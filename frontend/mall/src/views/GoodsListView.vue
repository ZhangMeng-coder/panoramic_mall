<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '../components/TopBar.vue'
import SearchBar from '../components/SearchBar.vue'
import FilterRow from '../components/FilterRow.vue'
import CatalogCard from '../components/CatalogCard.vue'
import Pager from '../components/Pager.vue'
import SiteFooter from '../components/SiteFooter.vue'
import { catalogApi } from '../api/catalog'
import type {
  CategoryNode,
  FacetQuery,
  FacetResult,
  GoodsListItem,
  SortKey
} from '../types/catalog'

/**
 * 搜索结果页 / 分类商品页**共用**的列表骨架（靠路由路径分流，不是两个组件）：
 *   /search?keyword=…              搜索模式：关键词来自 query
 *   /category/:categoryId?…        分类模式：锚点来自路由参数
 * ⚠ 两种模式的**渲染逻辑完全一致**，差异（搜索页出顶级分类 / 分类页出子分类）
 * 全部由 mall-bff 的 facets 决定，前端不再判一遍。
 *
 * 筛选状态全部住在 URL query 上（`categoryIds` / `brandIds` 逗号分隔 + `sort` + `page`），
 * 页面自身不持有可变的筛选副本 —— 刷新、分享、后退都不丢。
 */

/** 每页 49 = 7 列 × 7 行整行；后端 BasePageVO 有 @Max(100)，不得写更大的「一页塞满」值 */
const PAGE_SIZE = 49

/** 排序 tab（顺序即展示顺序） */
const SORTS: ReadonlyArray<{ key: SortKey; label: string }> = [
  { key: 'default', label: '综合' },
  { key: 'priceAsc', label: '价格 ↑' },
  { key: 'priceDesc', label: '价格 ↓' }
]

const route = useRoute()
const router = useRouter()

/** 分类模式（锚点在路由参数上）；否则搜索模式（关键词在 query 上） */
const isCategory = computed(() => route.path.startsWith('/category/'))

/**
 * 解析正整数。URL 与路由参数都是外部输入（手改、老链接、收藏夹），
 * 可能拿到 NaN / 0 / 负数 / 数组 —— 一律回退 fallback，
 * **绝不把 NaN 发给后端**（后端 @Min/@Max 会直接 400）。
 */
function positiveInt(raw: unknown, fallback: number): number {
  const n = Number(raw)
  return Number.isInteger(n) && n > 0 ? n : fallback
}

/**
 * 分类页锚点。`/category/:categoryId` 可以被塞进非数字值（`/category/abc`），
 * `Number('abc')` 得 NaN —— 解析不出正整数时按「无锚点」处理（退回全部商品），
 * 而不是把 NaN 送去后端。
 */
const anchorId = computed<number | null>(() => {
  if (!isCategory.value) return null
  const raw = route.params.categoryId
  const parsed = positiveInt(Array.isArray(raw) ? raw[0] : raw, 0)
  return parsed > 0 ? parsed : null
})

/** 关键词只在搜索模式生效：分类页 URL 里带 keyword 不参与查询，也不写回 query（避免死参数） */
const keyword = computed(() => {
  if (isCategory.value) return ''
  const raw = route.query.keyword
  return typeof raw === 'string' ? raw.trim() : ''
})

/** 逗号分隔的 id 列表 → 去重后的正整数数组（非法项剔除，`?brandIds=` 这类空值当未选） */
function parseIds(raw: unknown): number[] {
  if (typeof raw !== 'string' || raw === '') return []
  const ids: number[] = []
  for (const part of raw.split(',')) {
    const n = Number(part.trim())
    if (Number.isInteger(n) && n > 0 && !ids.includes(n)) ids.push(n)
  }
  return ids
}

const categoryIds = computed(() => parseIds(route.query.categoryIds))
const brandIds = computed(() => parseIds(route.query.brandIds))

/** 排序：非白名单值（手改 URL）退回默认，不把脏值透给后端 */
const sortKey = computed<SortKey>(() => {
  const raw = route.query.sort
  if (typeof raw !== 'string') return 'default'
  const hit = SORTS.find((s) => s.key === raw)
  return hit ? hit.key : 'default'
})

const pageNum = computed(() => positiveInt(route.query.page, 1))

const tree = ref<CategoryNode[]>([])
const items = ref<GoodsListItem[]>([])
const total = ref(0)
const facets = ref<FacetResult>({ categories: [], brands: [] })
const loading = ref(false)
const failed = ref(false)

/** 在分类树里递归找节点（层级数由 goods-center 决定，这里不假设只有两层） */
function findCategory(nodes: CategoryNode[], id: number): CategoryNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    const hit = node.children ? findCategory(node.children, id) : null
    if (hit) return hit
  }
  return null
}

/** 分类页标题：优先取分类树里的权威名；树里没有（id 过期 / 树不可用）退化为通用标题 */
const anchorName = computed(() => {
  const id = anchorId.value
  if (id === null) return '分类商品'
  return findCategory(tree.value, id)?.name ?? '分类商品'
})

/** 页面筛选状态 —— 即 URL query 的投影 */
interface Selection {
  categoryIds: number[]
  brandIds: number[]
  sort: SortKey
  page: number
}

/** 当前 URL 反映出的选择 */
function selection(): Selection {
  return {
    categoryIds: categoryIds.value,
    brandIds: brandIds.value,
    sort: sortKey.value,
    page: pageNum.value
  }
}

/** 选择 → URL query（默认值不写：链接短、可读、可分享） */
function toQuery(sel: Selection): Record<string, string> {
  const query: Record<string, string> = {}
  if (keyword.value) query.keyword = keyword.value
  if (sel.categoryIds.length) query.categoryIds = sel.categoryIds.join(',')
  if (sel.brandIds.length) query.brandIds = sel.brandIds.join(',')
  if (sel.sort !== 'default') query.sort = sel.sort
  if (sel.page > 1) query.page = String(sel.page)
  return query
}

/**
 * 写回 URL —— **URL 是唯一真相源**，这里不发请求：query 变了由下面的 watcher 统一拉数据。
 * 用户动作（筛选 / 排序 / 翻页）用 push：浏览器「后退」应该能撤销它。
 */
function navigate(sel: Selection): void {
  void router.push({ query: toQuery(sel) })
}

/** 选 / 取消选（幂等切换） */
function toggle(ids: number[], id: number): number[] {
  return ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]
}

/** 改筛选 / 排序一律回第 1 页：结果集变了，原来的页号没有意义 */
function toggleCategory(id: number): void {
  navigate({ ...selection(), categoryIds: toggle(categoryIds.value, id), page: 1 })
}

function toggleBrand(id: number): void {
  navigate({ ...selection(), brandIds: toggle(brandIds.value, id), page: 1 })
}

function changeSort(sort: SortKey): void {
  if (sort === sortKey.value) return
  navigate({ ...selection(), sort, page: 1 })
}

function changePage(page: number): void {
  navigate({ ...selection(), page })
}

/** 并发序号：后发的请求作废先发的响应（连点筛选 / 排序时不串数据） */
let seq = 0

/**
 * 钳「越界的当前页」—— ⚠ Pager 只钳它自己的高亮、**不 emit**，兜底责任在本页面。
 * 漏了就是：改筛选后结果从 5 页缩到 2 页 → 分页器正确高亮「2」，而这里仍发 pageNum=5
 * → 列表空白，且点已高亮的「2」按 Pager 的设计是 no-op，用户只能靠「上一页」自救。
 * 修正写回 URL（URL 是唯一真相源），watcher 会用钳后的页号重发一次。
 */
function normalizePage(asked: number, newTotal: number): void {
  const pageCount = Math.max(1, Math.ceil(newTotal / PAGE_SIZE))
  if (asked <= pageCount) return
  void router.replace({ query: toQuery({ ...selection(), page: pageCount }) })
}

async function load(page: number): Promise<void> {
  const current = ++seq
  loading.value = true
  failed.value = false

  // 分类锚点与已选筛选用同一份形状——两个接口的筛选部分口径一致，只是 facets 不带分页与排序
  const filter: FacetQuery = {
    keyword: keyword.value || undefined,
    categoryId: anchorId.value ?? undefined,
    categoryIds: categoryIds.value.length ? categoryIds.value : undefined,
    brandIds: brandIds.value.length ? brandIds.value : undefined
  }

  const [goodsRes, facetRes] = await Promise.allSettled([
    catalogApi.goods({ ...filter, sort: sortKey.value, pageNum: page, pageSize: PAGE_SIZE }),
    catalogApi.facets(filter)
  ])

  if (current !== seq) return // 已被后发的请求取代，本次结果丢弃
  loading.value = false

  if (goodsRes.status === 'rejected') {
    // 拦截器已弹后端提示；列表是主内容，拿不到就整块显示「暂不可用」
    items.value = []
    total.value = 0
    failed.value = true
    return
  }

  items.value = goodsRes.value.records
  total.value = goodsRes.value.total
  // facets 只是筛选面板（增强）：它失败不该让已经拿到的商品列表一起消失，保留上一次的 chips
  if (facetRes.status === 'fulfilled') facets.value = facetRes.value

  normalizePage(page, goodsRes.value.total)
}

/**
 * 查询签名：任一维度变化即重新拉取。
 * ⚠ 必须是 watch 而不是只 onMounted —— 搜索框在 /search 里换词时路由**复用同一个组件实例**，
 * 只在挂载时拉一次会一直显示上一次的关键词结果。
 */
const signature = computed(() =>
  [
    keyword.value,
    anchorId.value ?? '',
    categoryIds.value.join(','),
    brandIds.value.join(','),
    sortKey.value,
    pageNum.value
  ].join('|')
)

/** 分类树整页只拉一次：分类页标题与「全部分类」入口用它，跟筛选一起重拉没有意义 */
async function loadTree(): Promise<void> {
  try {
    tree.value = await catalogApi.categories()
  } catch {
    // 只影响分类页标题这一处展示：退化为通用标题，不打断列表（拦截器已弹提示）
    tree.value = []
  }
}

onMounted(() => {
  void loadTree()
  void load(pageNum.value)
})

watch(signature, () => {
  void load(pageNum.value)
})
</script>

<template>
  <TopBar />
  <SearchBar />

  <section class="catalog">
    <div class="container">
      <!-- 分类页标题行：分类名 + 「全部分类」入口（搜索页没有这一行） -->
      <div v-if="isCategory" class="catalog__head">
        <h1 class="catalog__title">{{ anchorName }}</h1>
        <router-link class="catalog__link" to="/">全部分类</router-link>
      </div>

      <!-- 筛选：分类 / 品牌各一行（该维度无可选项时 FilterRow 自己整行不渲染） -->
      <FilterRow
        title="分类"
        :items="facets.categories"
        :selected="categoryIds"
        @toggle="toggleCategory"
      />
      <FilterRow title="品牌" :items="facets.brands" :selected="brandIds" @toggle="toggleBrand" />

      <!-- 排序条：共 N 件 | 综合 / 价格 ↑ / 价格 ↓ -->
      <div class="catalog__bar">
        <span class="catalog__total">共 <b class="tnum">{{ total }}</b> 件商品</span>
        <div class="catalog__sorts">
          <button
            v-for="s in SORTS"
            :key="s.key"
            class="catalog__sort"
            :class="{ 'is-on': s.key === sortKey }"
            type="button"
            @click="changeSort(s.key)"
          >
            {{ s.label }}
          </button>
        </div>
      </div>

      <p v-if="loading" class="catalog__state" role="status">正在加载商品…</p>

      <p v-else-if="failed" class="catalog__state" role="status">商品暂不可用，请稍后重试</p>

      <div v-else-if="!items.length" class="catalog__state">
        <p>没有找到相关商品</p>
        <router-link class="catalog__link" to="/">返回首页</router-link>
      </div>

      <template v-else>
        <ul class="catalog__grid">
          <CatalogCard v-for="g in items" :key="g.id" :goods="g" />
        </ul>

        <Pager :total="total" :page-size="PAGE_SIZE" :page="pageNum" @change="changePage" />
      </template>
    </div>
  </section>

  <SiteFooter />
</template>
