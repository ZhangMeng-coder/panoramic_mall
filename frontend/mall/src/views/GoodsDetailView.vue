<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TopBar from '../components/TopBar.vue'
import SearchBar from '../components/SearchBar.vue'
import SiteFooter from '../components/SiteFooter.vue'
import { catalogApi } from '../api/catalog'
import type { GoodsDetail, GoodsDetailSku } from '../types/catalog'
import { grad } from '../utils/gradient'
import { priceParts } from '../utils/format'

/**
 * 商品详情页（`/goods/:id`，**需登录态**——路由 meta.requiresAuth 拦，见 router/index.ts）。
 *
 * 三个口径（都不是随手写的）：
 * ① **可见性由后端一处判定**：不存在 / 已下架 / 被平台锁定 / 店铺未过审，BFF 一律回
 *    404「商品不存在或已下架」，**不区分原因**。本页拿到什么 msg 就显示什么 msg，
 *    不在前端重判一遍可见性（那会造出第二份会漂移的口径）。
 * ② **商品详情按 HTML 渲染**：`description` 是店主自由录入的**富文本**（store / admin 两端的
 *    录入框提示语就是「支持 HTML」，库列注释也是「商品详情（富文本）」），故这里 `v-html` 渲染。
 *    安全性由**后端出口**兜住 —— mall-bff 下发前已用 common 的 `HtmlSanitizer` 按白名单清洗
 *    （剥脚本 / 事件属性 / 样式；白名单只此一份，admin 端同字段共用），本页**不需要、也不得**
 *    自己再拼一遍 HTML。
 *    ⚠ 别改回 `{{ }}` 插值：那样店主写的 `<p>` 会原样露在页面上。
 * ③ **不做「加入购物车 / 立即购买」**：后端没有购物车与下单接口（顶栏那两个入口也还是死链），
 *    摆一个点了没反应的按钮比不摆更糟，故只在信息区写一行说明。
 */

const route = useRoute()

/**
 * 路由参数是外部输入（手改地址栏、老链接）：解析不出正整数就**不发请求**，
 * 直接按「商品不存在」渲染。`/goods/abc` 若照发会给后端一个 400，白打一次还弹错。
 */
const goodsId = computed<number | null>(() => {
  const raw = route.params.id
  const n = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(n) && n > 0 ? n : null
})

const goods = ref<GoodsDetail | null>(null)
const loading = ref(false)
/** 非空即错误态：文案直接用后端 msg（详见文件头 ①），拿不到就当「暂不可用」 */
const errorMsg = ref('')

/** 当前大图（缩略图点击切换）；无图时为空串 */
const activeImage = ref('')
const stageFailed = ref(false)

/** 已选规格：规格名 → 规格值（未选的维度没有键） */
const picked = ref<Record<string, string>>({})

/** 并发序号：连点两个详情链接时，后发的请求作废先发的响应（与列表页同一手法） */
let seq = 0

async function load(id: number | null): Promise<void> {
  const current = ++seq
  // 换了商品：规格选择、大图、错误态一律重置，避免上一个商品的态粘过来
  goods.value = null
  errorMsg.value = ''
  picked.value = {}
  activeImage.value = ''
  stageFailed.value = false

  if (id === null) {
    errorMsg.value = '商品不存在或已下架'
    loading.value = false
    return
  }

  loading.value = true
  try {
    const data = await catalogApi.detail(id)
    if (current !== seq) return
    // 契约保证至少一个上架 SKU；真为空（数据异常）按不可见处理，别渲染出没有价格的详情
    if (!data.skus.length) {
      errorMsg.value = '商品不存在或已下架'
      return
    }
    goods.value = data
    activeImage.value = images.value[0] ?? ''
  } catch (e) {
    if (current !== seq) return
    errorMsg.value = e instanceof Error ? e.message : '商品暂不可用，请稍后重试'
  } finally {
    if (current === seq) loading.value = false
  }
}

/** 轮播图（剔空串），为空则退回主图；都没有 → 空数组，模板走 CSS 渐变占位 */
const images = computed<string[]>(() => {
  const list = (goods.value?.imageList ?? []).filter((u) => u.trim() !== '')
  if (list.length) return list
  const main = goods.value?.mainImage
  return main && main.trim() !== '' ? [main] : []
})

/** 大图地址：加载失败时置空 → 模板回退渐变占位 */
const stageSrc = computed(() => (stageFailed.value ? '' : activeImage.value))

/** 占位文字取商品名首字；色相由 id 派生（同一商品每次渲染的占位色一致） */
const label = computed(() => goods.value?.name.trim().charAt(0) || '商')
const hue = computed(() => (goods.value?.id ?? 0) % 360)

/** 规格维度（空数组 = 无规格商品，不渲染选择器） */
const specs = computed(() => goods.value?.specConfig ?? [])

const prices = computed(() => goods.value?.skus.map((s) => s.price) ?? [])
const minPrice = computed(() => (prices.value.length ? Math.min(...prices.value) : null))
const maxPrice = computed(() => (prices.value.length ? Math.max(...prices.value) : null))

/**
 * 选中规格组合对应的 SKU：**所有维度都选了**且存在匹配项时才有值，否则 null。
 * 无规格商品（单 SKU）直接就是那一个。
 */
const activeSku = computed<GoodsDetailSku | null>(() => {
  const g = goods.value
  if (!g) return null
  const dims = specs.value
  if (!dims.length) return g.skus.length === 1 ? g.skus[0] : null
  if (dims.some((d) => !picked.value[d.spec])) return null
  return (
    g.skus.find((sku) =>
      dims.every((d) =>
        sku.specAttrs.some((a) => a.spec === d.spec && a.value === picked.value[d.spec])
      )
    ) ?? null
  )
})

/** 规格都选齐了却配不出 SKU（店主删过规格组合）——提示一句，别让价格区静默停在区间价 */
const comboMissing = computed(
  () => specs.value.length > 0 && specs.value.every((d) => picked.value[d.spec]) && !activeSku.value
)

/**
 * 库存文案：**只在选中 SKU 后出现**——未选定时价格区给的是区间/起价，此时不臆造库存。
 * >0 给具体件数，0 即售罄（无规格单 SKU 商品同样走这里，`activeSku` 直通那一个）。
 */
const stockText = computed<string | null>(() => {
  const sku = activeSku.value
  if (!sku) return null
  return sku.availableStock > 0 ? `库存 ${sku.availableStock} 件` : '已售罄'
})

/** 售罄态（只用于文案上色）；未选中 SKU 时为 false */
const soldOut = computed(() => activeSku.value?.availableStock === 0)

/** 展示价：选中 SKU 用它的价，否则用区间最低价 */
const shownPrice = computed<number | null>(() => activeSku.value?.price ?? minPrice.value)

/** 价格三层字号拆解（未选中且有高低价差时带「起」，与列表卡同一读法） */
const parts = computed(() =>
  shownPrice.value === null ? null : priceParts(shownPrice.value)
)

const priceSuffix = computed(() =>
  activeSku.value || minPrice.value === null || minPrice.value === maxPrice.value ? '' : ' 起'
)

/** 点规格值：再点一次同一个值 = 取消该维度（价格回到区间展示） */
function pick(spec: string, value: string): void {
  const next: Record<string, string> = { ...picked.value }
  if (next[spec] === value) delete next[spec]
  else next[spec] = value
  picked.value = next
}

/** 换图重试一次：上一张图的加载失败态不该粘到新图上（与 CatalogCard 同款处理） */
watch(activeImage, () => {
  stageFailed.value = false
})

/**
 * 路由参数变即重拉：从详情页点进另一个详情页会**复用同一个组件实例**（onMounted 不会再跑），
 * 只在挂载时拉一次会让页面停在上一个商品上。
 * ⚠ 这一句放文件末尾：`immediate` 会**同步**触发一次 `load`，而 load 里读 `images` 等
 * 在其后声明的 computed —— 挪到前面去就会踩 TDZ。
 */
watch(goodsId, (id) => void load(id), { immediate: true })
</script>

<template>
  <TopBar />
  <SearchBar />

  <section class="detail">
    <div class="container">
      <!-- 面包屑：首页 → 所属分类 → 商品（分类锚点可点，回该分类的商品列表） -->
      <nav class="detail__crumbs">
        <router-link class="detail__crumb-link" to="/">首页</router-link>
        <template v-if="goods?.categoryId">
          <span class="detail__crumb-sep">›</span>
          <router-link class="detail__crumb-link" :to="`/category/${goods.categoryId}`">
            {{ goods.categoryName || '分类商品' }}
          </router-link>
        </template>
        <template v-if="goods">
          <span class="detail__crumb-sep">›</span>
          <span class="detail__crumb-cur">{{ goods.name }}</span>
        </template>
      </nav>

      <p v-if="loading" class="catalog__state" role="status">正在加载商品…</p>

      <!-- 错误态：文案是后端 msg（不可见 → 「商品不存在或已下架」；下游故障 → 「…暂不可用」）-->
      <div v-else-if="errorMsg" class="catalog__state">
        <p>{{ errorMsg }}</p>
        <router-link class="catalog__link" to="/">返回首页</router-link>
      </div>

      <template v-else-if="goods">
        <div class="detail__main">
          <!-- 左：大图 + 缩略图 -->
          <div class="detail__gallery">
            <div class="detail__stage" :style="{ background: grad(hue, 60, 91, 82) }">
              <img
                v-if="stageSrc"
                class="detail__stage-img"
                :src="stageSrc"
                :alt="goods.name"
                @error="stageFailed = true"
              />
              <span v-else class="detail__stage-ph" :style="{ color: `hsl(${hue} 42% 32%)` }">
                {{ label }}
              </span>
            </div>

            <!-- 缩略图：多于一张才出（只有主图时它没有意义）。用 button 而非 li 承担点击，
                 键盘也能切；alt 留空——商品名就在右侧，重复念一遍只是噪音 -->
            <ul v-if="images.length > 1" class="detail__thumbs">
              <li v-for="url in images" :key="url">
                <button
                  class="detail__thumb"
                  :class="{ 'is-on': url === activeImage }"
                  type="button"
                  @click="activeImage = url"
                >
                  <img class="detail__thumb-img" :src="url" alt="" />
                </button>
              </li>
            </ul>
          </div>

          <!-- 右：名称 / 价格 / 归属 / 规格 -->
          <div class="detail__info">
            <h1 class="detail__name">{{ goods.name }}</h1>

            <div class="detail__price-band">
              <div v-if="parts" class="detail__price tnum">
                <span class="detail__price-sym">¥</span><span class="detail__price-int">{{
                  parts.int
                }}</span><span class="detail__price-dec">{{ parts.dec }}</span>
              </div>
              <span v-else class="detail__price-tbd">价格待定</span>
              <span v-if="priceSuffix" class="detail__price-suffix">{{ priceSuffix }}</span>
            </div>

            <!-- 库存行：选中 SKU 后才出现（未选中不臆造库存）。售罄只改文案与颜色，
                 规格值**照样可点选**——顾客仍能逐个切过去比较，售罄不是禁用理由 -->
            <p v-if="stockText" class="detail__stock" :class="{ 'is-sold-out': soldOut }">
              {{ stockText }}
            </p>

            <dl class="detail__meta">
              <div class="detail__meta-row">
                <dt class="detail__meta-key">分类</dt>
                <dd class="detail__meta-val">{{ goods.categoryName || '-' }}</dd>
              </div>
              <div class="detail__meta-row">
                <dt class="detail__meta-key">品牌</dt>
                <dd class="detail__meta-val">{{ goods.brandName || '-' }}</dd>
              </div>
              <div class="detail__meta-row">
                <dt class="detail__meta-key">店铺</dt>
                <dd class="detail__meta-val">{{ goods.storeName || '-' }}</dd>
              </div>
            </dl>

            <!-- 规格选择：无规格配置的商品没有这一块 -->
            <div v-if="specs.length" class="detail__specs">
              <div v-for="d in specs" :key="d.spec" class="detail__spec">
                <span class="detail__spec-name">{{ d.spec }}</span>
                <div class="detail__spec-values">
                  <button
                    v-for="v in d.values"
                    :key="v"
                    class="detail__spec-value"
                    :class="{ 'is-on': picked[d.spec] === v }"
                    type="button"
                    @click="pick(d.spec, v)"
                  >
                    {{ v }}
                  </button>
                </div>
              </div>
            </div>

            <p v-if="comboMissing" class="detail__warn">该规格组合暂未上架，换一个组合看看</p>

            <p class="detail__note">演示环境：购物车与下单接口尚未开放，本页只展示商品信息。</p>
          </div>
        </div>

        <!-- 下：商品详情正文（HTML 渲染；内容已由 mall-bff 出口消毒，见文件头 ②） -->
        <section class="detail__desc">
          <h2 class="detail__desc-title">商品详情</h2>
          <div v-if="goods.description" class="detail__desc-body" v-html="goods.description"></div>
          <p v-else class="detail__desc-empty">店主未填写商品详情</p>
        </section>
      </template>
    </div>
  </section>

  <SiteFooter />
</template>

<!--
  本页唯一的 SFC 内样式块。整站的样式基准仍在 src/styles/catalog.css，
  这两条规则留在这里只是因为本次改动只允许动本文件（catalog.css 不在改动清单内）。
  ⚠ 色值 / 间距仍**只消费 tokens.css 的令牌**，不硬编码；不写媒体查询（本工程只做宽屏）。
-->
<style scoped>
.detail__stock {
  margin-top: var(--s-3);
  color: var(--n600);
  font-size: var(--t-sm);
}

.detail__stock.is-sold-out {
  color: var(--danger);
  font-weight: var(--w-medium);
}
</style>
