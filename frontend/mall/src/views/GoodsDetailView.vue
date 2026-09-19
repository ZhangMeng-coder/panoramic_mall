<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TopBar from '../components/TopBar.vue'
import SearchBar from '../components/SearchBar.vue'
import SiteFooter from '../components/SiteFooter.vue'
import { catalogApi } from '../api/catalog'
import type { GoodsDetail, GoodsDetailSku } from '../types/catalog'
import { grad } from '../utils/gradient'
import { priceParts, trimNum } from '../utils/format'

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

/** 选中的 SKU id（未选中为 null → 价格区回落区间价、库存行不出现） */
const selectedSkuId = ref<number | null>(null)

/** 并发序号：连点两个详情链接时，后发的请求作废先发的响应（与列表页同一手法） */
let seq = 0

async function load(id: number | null): Promise<void> {
  const current = ++seq
  // 换了商品：规格选择、大图、错误态一律重置，避免上一个商品的态粘过来
  goods.value = null
  errorMsg.value = ''
  selectedSkuId.value = null
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

/**
 * 规格维度配置（空数组 = 无规格商品，不渲染规格块）。
 * ⚠ 它**不再用来拼规格按钮**（那样能拼出上架 SKU 里不存在的组合，见 `skuRows`），
 * 只为**维度顺序**提供基准——库里 `spec_attrs` 是按提交顺序原样存的，各 SKU 未必一致。
 */
const specs = computed(() => goods.value?.specConfig ?? [])

const prices = computed(() => goods.value?.skus.map((s) => s.price) ?? [])
const minPrice = computed(() => (prices.value.length ? Math.min(...prices.value) : null))
const maxPrice = computed(() => (prices.value.length ? Math.max(...prices.value) : null))

/**
 * 当前选中的 SKU：未选中时为 null（价格区回落区间价、库存行不出现）。
 * 无规格商品（单 SKU）直接就是那一个——没有行可选，也就没有「未选中」态。
 */
const activeSku = computed<GoodsDetailSku | null>(() => {
  const g = goods.value
  if (!g) return null
  if (!specs.value.length) return g.skus.length === 1 ? g.skus[0] : null
  return g.skus.find((sku) => sku.id === selectedSkuId.value) ?? null
})

/** 规格行的展示形状（页面私有，不外移） */
interface SkuRow {
  /** SKU 主键：选中态与点击都以它为准 */
  id: number
  /** 规格文案，如「颜色：曜石黑 / 容量：256G」 */
  text: string
  /** 该 SKU 单价 */
  price: number
  /** 售罄（availableStock ≤ 0）：整行置灰且不可选 */
  soldOut: boolean
}

/**
 * 规格行列表：**一行一个上架 SKU**（下架的由 mall-bff 出口滤掉，这里不重判）。
 * ⚠ 之所以不按 `specConfig` 拼维度矩阵：那是 **SPU 级**配置，含只在**已下架 SKU** 上存在的值，
 * 矩阵能拼出「配置里有、上架 SKU 里没有」的组合，就得再补一句「该组合暂未上架」兜底。
 * 行列表让这类死路结构上不存在——每行必然对应一个真实在卖的规格。
 * ⚠ 维度顺序按 `specConfig` 重排（见 `specs` 注释），否则会出现一行「颜色/容量」、
 * 另一行「容量/颜色」。`specConfig` 里没有的维度排最后且保持原序（sort 稳定，同键不乱序）。
 */
const skuRows = computed<SkuRow[]>(() => {
  const g = goods.value
  if (!g) return []
  const order = new Map(specs.value.map((d, i) => [d.spec, i]))
  const rank = (spec: string): number => order.get(spec) ?? Number.MAX_SAFE_INTEGER
  return g.skus.map((sku) => ({
    id: sku.id,
    text: [...sku.specAttrs]
      .sort((a, b) => rank(a.spec) - rank(b.spec))
      .map((a) => `${a.spec}：${a.value}`)
      .join(' / '),
    price: sku.price,
    soldOut: sku.availableStock <= 0
  }))
})

/**
 * 库存文案：**只在选中 SKU 后出现**——未选定时价格区给的是区间/起价，此时不臆造库存。
 * >0 给具体件数，≤0 即售罄（无规格单 SKU 商品同样走这里，`activeSku` 直通那一个）。
 */
const stockText = computed<string | null>(() => {
  const sku = activeSku.value
  if (!sku) return null
  return sku.availableStock > 0 ? `库存 ${sku.availableStock} 件` : '已售罄'
})

/**
 * 售罄态（只用于文案上色）；未选中 SKU 时为 false。
 * ⚠ 判据必须与 `stockText` 同为「≤0」：写成 `=== 0` 时，若出现 `locked_stock > stock`
 * （本期 locked 恒 0，交易域接入后可能），文案会显示「已售罄」却不上色。
 */
const soldOut = computed(() => {
  const sku = activeSku.value
  return sku != null && sku.availableStock <= 0
})

/** 展示价：选中 SKU 用它的价，否则用区间最低价 */
const shownPrice = computed<number | null>(() => activeSku.value?.price ?? minPrice.value)

/** 价格三层字号拆解（未选中且有高低价差时带「起」，与列表卡同一读法） */
const parts = computed(() =>
  shownPrice.value === null ? null : priceParts(shownPrice.value)
)

const priceSuffix = computed(() =>
  activeSku.value || minPrice.value === null || minPrice.value === maxPrice.value ? '' : ' 起'
)

/**
 * 点规格行：选中它（价格区换成该行单价、库存行显示件数）。
 * 再点一次已选中的行 = 取消选择，价格落回区间价——这是回到区间展示的唯一途径。
 * 售罄行在模板上 `disabled`，压根进不来，此处不再判一次。
 */
function selectSku(id: number): void {
  selectedSkuId.value = selectedSkuId.value === id ? null : id
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

            <!-- 库存行：选中 SKU 后才出现（未选中不臆造库存）。件数只在这里给，不逐行印——
                 行里印了也只是让列表变吵，售罄行更是永远没机会显示它 -->
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

            <!-- 规格：一行一个上架 SKU（构造与「为什么不拼维度矩阵」见 skuRows 注释）。
                 售罄行置灰且不可选——顾客不会选中一个买不了的规格，价格区也就不会停在售罄价上。
                 无规格配置的商品没有这一块（那种商品的单价由价格区直接给） -->
            <div v-if="specs.length" class="detail__specs">
              <button
                v-for="row in skuRows"
                :key="row.id"
                class="detail__spec-row"
                :class="{ 'is-on': selectedSkuId === row.id }"
                type="button"
                :disabled="row.soldOut"
                @click="selectSku(row.id)"
              >
                <span class="detail__spec-text">{{ row.text }}</span>
                <span class="detail__spec-price tnum">¥{{ trimNum(row.price) }}</span>
                <span v-if="row.soldOut" class="detail__spec-out">已售罄</span>
              </button>
            </div>

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
