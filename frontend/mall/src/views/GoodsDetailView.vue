<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '../components/TopBar.vue'
import SearchBar from '../components/SearchBar.vue'
import SiteFooter from '../components/SiteFooter.vue'
import AddressPicker from '../components/AddressPicker.vue'
import ModalShell from '../components/ModalShell.vue'
import Pager from '../components/Pager.vue'
import StarRating from '../components/StarRating.vue'
import { catalogApi } from '../api/catalog'
import { cartApi } from '../api/cart'
import { evaluationApi } from '../api/evaluation'
import { orderApi } from '../api/order'
import { LOGIN_REQUIRED_MSG } from '../api/request'
import { getToken } from '../store/auth'
import { refresh as refreshCartBadge } from '../store/cart'
import { showToast } from '../composables/useToast'
import { useAddressGate } from '../composables/useAddressGate'
import type { GoodsDetail, GoodsDetailSku } from '../types/catalog'
import type { EvaluationItem, EvaluationStat } from '../types/evaluation'
import type { OrderVO } from '../types/order'
import { grad } from '../utils/gradient'
import { formatDateTime, priceParts, trimNum } from '../utils/format'
import { newRequestId } from '../utils/requestId'

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
 * ③ **「加入购物车」与「立即下单」都已接入**：购物车走 `/cart` 八行、下单走 `/orders` 六行
 *    （契约 docs/contracts/mall-bff.md），本页规格区下方摆数量 + 两个动作。
 *    ⚠ 「立即下单」不是「立即购买」：它**不跳支付页**（C 端没有收银台），而是先过一道**地址分支**
 *    （`useAddressGate` 的三支路：没地址 → 去添加；有地址没默认 → 弹窗选一条；有默认 → 直接用），
 *    选定后按「确认下单」才真的下单；下单成功按「一笔 / 多笔」分流（一单一店，一次提交可能拆成多笔）。
 *    ⚠ 选地址那一步走的是**模态层**（`ModalShell` + `AddressPicker`）——本端唯一的模态层就是它。
 * ④ **评分与评价区**：评分有两个读数——**商品评分**（名称下方，与列表卡同一个值）与**店铺评分**，
 *    都在名称下方那条信息带里；**都可空**，为 `null` 时**不渲染**（「没人评过」不是「0 分」）。
 *    页面底部是**评价区**（分布 + 列表 + 分页，**只读**）：C 端**没有回复入口**（回复是商户端的事），
 *    也不能改 / 删自己的评价。⚠ **不提供星级筛选**——分布是展示、筛选是操作，筛选是**商户端**的需求。
 *    ⚠ 评价文字**按纯文本插值渲染**（`{{ }}`），**不许 `v-html`**：商品详情正文那条 HTML 链路的
 *    消毒前提是「店主自由录入 HTML」，评价没有这个前提（见文件头 ② 与 mall-bff 契约）。
 *    ⚠ 评价区**自己一份加载 / 错误态**（区块内提示 + 重试），取不到只让这一块空着，
 *    **不拖垮商品详情**——与「分类树拿不到不拖垮商品列表」同一条口径。
 */

const route = useRoute()
const router = useRouter()

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
  resetEvaluations()

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
    // 评价区独立取数（分布 + 第一页），失败只影响这一块（见文件头 ④）
    void loadEvaluations(id, 1)
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
 * ⚠ 判据必须与 `stockText` 同为「≤0」：两处口径必须一致，别一处写 `=== 0`、一处写 `<= 0` 而分家
 * （历史上 `availableStock` 曾是 `stock − locked_stock` 时才需要 `=== 0` 的判据，现已统一为 `stock`）。
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

/* ---- 购买区：数量 + 加入购物车 ---- */

/**
 * 单行加购上限的**前端镜像**（域侧上限 999，超限必然 400）。
 * 前端夹一道只是免得点出一个注定失败的请求，真正的上限判定在域侧，不在这里。
 */
const MAX_BUY_QUANTITY = 999

const buyQty = ref(1)
const adding = ref(false)

/** 加购上限 = min(999, 所选 SKU 的可用库存)；未选中规格时为 0（步进器与按钮都不可用） */
const maxBuyQty = computed(() => {
  const sku = activeSku.value
  return sku ? Math.min(MAX_BUY_QUANTITY, sku.availableStock) : 0
})

/**
 * 「加入购物车」可点：选中了规格（无规格商品由 `activeSku` 自动选中那一个）、
 * 该 SKU 未售罄、且没有请求在途。**不看登录态**——未登录点它也能进（提示后带去登录页），
 * 把按钮灰掉而不说理由才是更差的处理。
 */
const canAdd = computed(() => {
  const sku = activeSku.value
  return sku !== null && sku.availableStock > 0 && !adding.value
})

/** 按钮不可用时的理由（别让用户猜为什么点不动） */
const buyHint = computed<string>(() => {
  if (!activeSku.value) return '请先选择规格'
  return activeSku.value.availableStock <= 0 ? '该规格已售罄' : ''
})

/** 步进：`-` 到 1、`+` 到上限即止（模板已把按钮禁用，这里再兜一层） */
function stepBuyQty(delta: number): void {
  const next = buyQty.value + delta
  if (next < 1 || next > maxBuyQty.value) return
  buyQty.value = next
}

/**
 * 加入购物车。未登录 → 提示并去登录页（带回跳参数，登录后回本页）；
 * 加购**不校验库存**（契约口径）：库存只影响这行之后还能不能再加（`purchasable`），
 * 真正拦库存的是**下单**（本页的「立即下单」与购物车结算），由域侧判定。
 *
 * ⚠ 成功后刷的是**行数徽标**（`GET /cart/count` 口径），故重拉而不是本地 `buyQty` 相加——
 * 那会把「件数」当「行数」记进徽标（见 store/cart.ts）。
 */
async function addToCart(): Promise<void> {
  const sku = activeSku.value
  const spu = goods.value
  if (!sku || !spu) return

  if (!getToken()) {
    showToast(LOGIN_REQUIRED_MSG, 'info')
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  adding.value = true
  try {
    await cartApi.addItem({ spuId: spu.id, skuId: sku.id, quantity: buyQty.value })
    showToast('已加入购物车', 'success')
    void refreshCartBadge()
  } catch {
    // 拦截器已弹后端 msg（商品对 C 端不可见 → 400 中文提示原样透传）
  } finally {
    adding.value = false
  }
}

/* ---- 立即下单（source=DIRECT）---- */

/**
 * 下单前的**地址分支**（三分支与判定全在 `useAddressGate`——与购物车结算共用那一处）。
 * 本页只提供「怎么下单」：建单参数与成功后的去向。
 *
 * ⚠ **防连点**由 gate 的 `busy` / `submitting` 承担：`requestId` 在「确认」那一下才生成，
 * 若两次点击各生成一个键，域侧的第一级幂等（按 `(customer_id, request_id)`）就**失效**，
 * 连点两下就是两笔真实订单。故读状态与提交期间，入口按钮、弹窗整块禁用。
 */
const { busy, picking, submitting, start, confirm, close } = useAddressGate({
  submit: placeOrder
})

/** 「立即下单」可点：与加购同一判据（选中规格 + 未售罄 + 没有请求在途） */
const canBuyNow = computed(() => {
  const sku = activeSku.value
  return sku !== null && sku.availableStock > 0 && !busy.value
})

/** 点「立即下单」：未登录先提示去登录（同加购）；已登录则过那道地址分支（见 `useAddressGate`） */
async function openBuyNow(): Promise<void> {
  if (!canBuyNow.value) return

  if (!getToken()) {
    showToast(LOGIN_REQUIRED_MSG, 'info')
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }

  await start()
}

/**
 * 下单成功后的分流：**一笔 → 该单详情；多笔 → 订单列表 + 提示**
 * （一次提交按 `storeId` 拆成多笔，顺序 = `storeId` 升序，契约口径）。
 */
async function afterOrdered(orders: OrderVO[]): Promise<void> {
  if (orders.length === 1) {
    await router.push(`/orders/${orders[0].orderNo}`)
    return
  }
  // 0 笔理论上不会出现（域侧至少回一笔）；真出现时也别装没事，把人送到订单列表去看实情
  if (orders.length > 1) showToast(`已按店铺拆成 ${orders.length} 笔订单`, 'info')
  await router.push('/orders')
}

/**
 * 真的建单（gate 的 `submit`）：**这一刻**生成 requestId（每次提交一个）。
 *
 * ⚠ 失败**不关弹窗**（弹窗的开关归 gate：只有提交成功它才关）——域侧 400（商品已下架 /
 * 库存不足）的中文文案由拦截器弹出，顾客可以换一条地址或直接重试；关掉弹窗等于逼他再点一次。
 * ⚠ 本函数**不设自己的在途标记**：在途是 gate 的 `submitting`（同一个动作不该有两份状态）。
 */
async function placeOrder(addressId: number): Promise<void> {
  const sku = activeSku.value
  // 规格没了（理论上到不了：入口按钮按 `activeSku` 禁用、规格一变弹窗也会关）——按**失败**处理，
  // 别发一个没有商品的单。⚠ gate 只认「抛没抛」，故这里必须抛；提示也自己给一句：
  // 它不走拦截器（那是给后端 msg 用的），不提示的话弹窗会停在那里不说明理由
  if (!sku) {
    showToast('请先选择规格', 'info')
    throw new Error('请先选择规格')
  }

  const orders = await orderApi.create({
    source: 'DIRECT',
    requestId: newRequestId(),
    addressId,
    items: [{ skuId: sku.id, quantity: buyQty.value }]
  })
  await afterOrdered(orders)
}

/* ---- 商品评价区（只读：星级分布 + 评价列表 + 分页；见文件头 ④）---- */

/** 每页条数由**页面**传（契约：域侧是通用分页，不写死 10） */
const EVAL_PAGE_SIZE = 10

/** 星级分布（含 0 人的星级，域侧保证恒 5 行升序）；未取到时为 null */
const evalStat = ref<EvaluationStat | null>(null)
const evalItems = ref<EvaluationItem[]>([])
/**
 * 分页用的总数。⚠ 它与 `evalStat.total` 同源、但**来自两次调用**（并发新增评价时可能差一条）：
 * 各自用在自己那一处（分页用分页的、分布用分布的），不互相覆盖。
 */
const evalTotal = ref(0)
const evalPage = ref(1)
const evalLoading = ref(false)
/** 非空即评价区错误态：文案取后端 msg（**只影响这一块**，上面的商品详情照常展示） */
const evalErrorMsg = ref('')

/** 并发序号：连点两个商品 / 连翻两页时，后发的请求作废先发的响应（与详情同一手法） */
let evalSeq = 0

/** 换商品时把评价区清空（在途响应一并作废，免得旧商品的评价贴到新商品上） */
function resetEvaluations(): void {
  evalSeq++
  evalStat.value = null
  evalItems.value = []
  evalTotal.value = 0
  evalPage.value = 1
  evalErrorMsg.value = ''
  evalLoading.value = false
  failedAvatars.value = []
}

/**
 * 取评价区数据：**分布与列表一起发**（互不依赖，两趟串行只是白等一个往返）。
 * ⚠ 分布是「该商品全部评价」的统计，**不能从当前页算**——那只会算出本页的分布
 * （契约里它就是**另一个接口**）。
 */
async function loadEvaluations(id: number | null, page: number): Promise<void> {
  if (id === null) return
  const current = ++evalSeq
  evalLoading.value = true
  try {
    const [stat, data] = await Promise.all([
      evaluationApi.stat(id),
      evaluationApi.page({ spuId: id, pageNum: page, pageSize: EVAL_PAGE_SIZE })
    ])
    if (current !== evalSeq) return
    evalStat.value = stat
    evalItems.value = data.records
    evalTotal.value = data.total
    evalPage.value = page
    evalErrorMsg.value = ''
  } catch (e) {
    if (current !== evalSeq) return
    evalErrorMsg.value = e instanceof Error ? e.message : '评价暂不可用，请稍后重试'
  } finally {
    if (current === evalSeq) evalLoading.value = false
  }
}

/** 重试：重拉**当前页**（不把顾客甩回第一页） */
function reloadEvaluations(): void {
  void loadEvaluations(goodsId.value, evalPage.value)
}

/** 翻页（Pager 的 `change`）—— ⚠ 页码是它给的，别用 `evalPage` 顶替（那样点第 2 页只会重拉第 1 页） */
function changeEvalPage(page: number): void {
  void loadEvaluations(goodsId.value, page)
}

/** 分布行（页面形状，不外移）：**5 星在上**（统计接口给的是 1 → 5 升序，这里重排），条数占比画柱 */
interface DistRow {
  score: number
  count: number
  /** 条数占总数百分比（0 ~ 100，取整）——柱长。总数为 0 时全为 0 */
  percent: number
}

const distRows = computed<DistRow[]>(() => {
  const stat = evalStat.value
  const total = stat?.total ?? 0
  return [...(stat?.scores ?? [])]
    .sort((a, b) => b.score - a.score)
    .map((row) => ({
      score: row.score,
      count: row.count,
      percent: total > 0 ? Math.round((row.count / total) * 100) : 0
    }))
})

/**
 * 快照行文案，如「颜色：曜石黑 / 容量：256G × 2」；同单多个 SKU 行用「；」连起来。
 * ⚠ 无规格商品（`specAttrs` 为空）只给数量，不硬凑一个规格名出来。
 */
function snapshotText(item: EvaluationItem): string {
  return (item.skuSnapshot ?? [])
    .map((row) => {
      const spec = (row.specAttrs ?? []).map((a) => `${a.spec}：${a.value}`).join(' / ')
      return spec ? `${spec} × ${row.quantity}` : `× ${row.quantity}`
    })
    .join('；')
}

/** 头像占位字：取昵称首字（昵称由后端兜底，非空）。头像加载失败时同样回落到它 */
function nicknameInitial(name: string): string {
  return name.trim().charAt(0) || '用'
}

/** 头像图片加载失败态（按评价 id 记；头像 URL 为空时压根不渲染 img） */
const failedAvatars = ref<number[]>([])

function avatarSrc(item: EvaluationItem): string {
  if (!item.avatar || failedAvatars.value.includes(item.id)) return ''
  return item.avatar
}

function markAvatarFailed(id: number): void {
  if (!failedAvatars.value.includes(id)) failedAvatars.value = [...failedAvatars.value, id]
}

/** 换图重试一次：上一张图的加载失败态不该粘到新图上（与 CatalogCard 同款处理） */
watch(activeImage, () => {
  stageFailed.value = false
})

/** 换规格：数量重置为 1（不同 SKU 库存不同，继承上一个规格的数量会得到「一选就超上限」的怪状态） */
watch(activeSku, () => {
  buyQty.value = 1
  // 弹窗里正要下的是**上一个规格**，规格一换就把它收起（留着会让顾客对着新规格下旧规格的单）
  close()
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

            <!-- 评分带：商品评分（左）+ 店铺评分（右）。⚠ 两个都是**可空**的，`null` = 无人评价 →
                 各自不渲染（不显示 0、不显示占位）；两个都空时整条不渲染。
                 ⚠ 「条数」取自星级分布的总数（与下面的评价区同一份读数），未取到时不出这一截 -->
            <div v-if="goods.score !== null || goods.shopScore !== null" class="detail__scores">
              <span v-if="goods.score !== null" class="detail__score">
                <StarRating :score="goods.score" />
                <b class="detail__score-num tnum">{{ goods.score }}</b>
                <span v-if="evalStat" class="detail__score-count">
                  {{ evalStat.total }} 条评价
                </span>
              </span>
              <span v-if="goods.shopScore !== null" class="detail__shop-score">
                店铺评分 <b class="tnum">{{ goods.shopScore }}</b>
              </span>
            </div>

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

            <!-- 购买区：数量步进器 + 加入购物车 / 立即下单（见文件头 ③）。
                 未选规格 / 已售罄时按钮禁用，理由写在下面那行，别让用户猜 -->
            <div class="detail__buy">
              <div class="detail__qty" role="group" aria-label="购买数量">
                <button
                  class="detail__qty-btn"
                  type="button"
                  :disabled="!activeSku || buyQty <= 1"
                  aria-label="减少数量"
                  @click="stepBuyQty(-1)"
                >
                  −
                </button>
                <span class="detail__qty-num tnum" aria-live="polite">{{ buyQty }}</span>
                <button
                  class="detail__qty-btn"
                  type="button"
                  :disabled="!activeSku || buyQty >= maxBuyQty"
                  aria-label="增加数量"
                  @click="stepBuyQty(1)"
                >
                  +
                </button>
              </div>

              <button class="detail__add" type="button" :disabled="!canAdd" @click="addToCart">
                {{ adding ? '加入中…' : '加入购物车' }}
              </button>

              <!-- 立即下单：先过地址分支，不是「立即购买」（C 端没有收银台，见文件头 ③） -->
              <button class="detail__buy-now" type="button" :disabled="!canBuyNow" @click="openBuyNow">
                {{ busy ? '处理中…' : '立即下单' }}
              </button>
            </div>
            <p v-if="buyHint" class="detail__buy-hint">{{ buyHint }}</p>

            <!-- 选收货地址弹窗：**有地址但没有默认**时才出现（有默认就静默下单了，见 useAddressGate）。
                 ⚠ 提交在途时 `closable=false`——那时关掉它，顾客会以为没提交，而单可能已经建了 -->
            <ModalShell
              v-if="picking"
              label="选择收货地址"
              :closable="!submitting"
              @close="close"
            >
              <AddressPicker
                :submitting="submitting"
                confirm-text="确认下单"
                @confirm="confirm"
                @cancel="close"
              />
            </ModalShell>
          </div>
        </div>

        <!-- 下：商品详情正文（HTML 渲染；内容已由 mall-bff 出口消毒，见文件头 ②） -->
        <section class="detail__desc">
          <h2 class="detail__desc-title">商品详情</h2>
          <div v-if="goods.description" class="detail__desc-body" v-html="goods.description"></div>
          <p v-else class="detail__desc-empty">店主未填写商品详情</p>
        </section>

        <!-- 评价区：只读（分布 + 列表 + 分页，见文件头 ④）。⚠ 它自己一份加载 / 错误态，
             取不到只让这一块空着，不拖垮上面的商品详情 -->
        <section class="eval">
          <h2 class="eval__title">商品评价</h2>

          <!-- 分布：**恒定 5 行**（含 0 人的星级，由域侧保证）——页面不补缺项、也不按当前页算 -->
          <div class="eval__summary">
            <div class="eval__avg">
              <span class="eval__avg-num tnum">{{ goods.score ?? '—' }}</span>
              <span class="eval__avg-label">商品评分</span>
              <span class="eval__avg-total tnum">共 {{ evalStat?.total ?? 0 }} 条评价</span>
            </div>
            <ul class="eval__dist">
              <li v-for="row in distRows" :key="row.score" class="eval__dist-row">
                <span class="eval__dist-star">{{ row.score }} 星</span>
                <span class="eval__dist-bar">
                  <i class="eval__dist-fill" :style="{ width: `${row.percent}%` }"></i>
                </span>
                <span class="eval__dist-count tnum">{{ row.count }}</span>
              </li>
            </ul>
          </div>

          <p v-if="evalLoading" class="eval__state" role="status">正在加载评价…</p>

          <div v-else-if="evalErrorMsg" class="eval__state">
            <span>{{ evalErrorMsg }}</span>
            <button class="eval__retry" type="button" @click="reloadEvaluations">重试</button>
          </div>

          <template v-else>
            <p v-if="!evalItems.length" class="eval__state">还没有人评价这件商品</p>

            <ul v-else class="eval__list">
              <li v-for="item in evalItems" :key="item.id" class="eval-item">
                <div class="eval-item__head">
                  <span
                    class="eval-item__avatar"
                    :style="{ background: grad(item.id % 360, 60, 91, 82) }"
                  >
                    <img
                      v-if="avatarSrc(item)"
                      class="eval-item__avatar-img"
                      :src="avatarSrc(item)"
                      :alt="item.nickname"
                      @error="markAvatarFailed(item.id)"
                    />
                    <span v-else class="eval-item__avatar-ph">
                      {{ nicknameInitial(item.nickname) }}
                    </span>
                  </span>
                  <span class="eval-item__name">{{ item.nickname }}</span>
                  <StarRating :score="item.score" />
                  <span class="eval-item__time tnum">{{ formatDateTime(item.createTime) }}</span>
                </div>

                <!-- 评价文字：**纯文本插值**（不许 v-html，见文件头 ④）；只打星不写字时整行不渲染 -->
                <p v-if="item.content" class="eval-item__text">{{ item.content }}</p>
                <!-- 下单时的规格快照（评价挂在商品上，但成交的是某个规格） -->
                <p v-if="snapshotText(item)" class="eval-item__snapshot">
                  {{ snapshotText(item) }}
                </p>

                <!-- 商家回复：未回复时整块不渲染（`replyContent` 为 null） -->
                <div v-if="item.replyContent" class="eval-item__reply">
                  <p class="eval-item__reply-head">
                    商家回复
                    <span v-if="item.replyTime" class="tnum">
                      {{ formatDateTime(item.replyTime) }}
                    </span>
                  </p>
                  <p class="eval-item__reply-text">{{ item.replyContent }}</p>
                </div>
              </li>
            </ul>

            <!-- 分页：与列表页共用 Pager（本端不注册 Element Plus）。整块不渲染的条件由 Pager 自己判 -->
            <Pager
              :total="evalTotal"
              :page-size="EVAL_PAGE_SIZE"
              :page="evalPage"
              @change="changeEvalPage"
            />
          </template>
        </section>
      </template>
    </div>
  </section>

  <SiteFooter />
</template>
