<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TopBar from '../../components/TopBar.vue'
import SiteFooter from '../../components/SiteFooter.vue'
import AddressPicker from '../../components/AddressPicker.vue'
import ModalShell from '../../components/ModalShell.vue'
import StarRating from '../../components/StarRating.vue'
import { evaluationApi } from '../../api/evaluation'
import { orderApi } from '../../api/order'
import { countdownText, remainingMs, useNowTick } from '../../composables/useCountdown'
import { showToast } from '../../composables/useToast'
import type { OrderVO } from '../../types/order'
import {
  ORDER_STATUS_PAID,
  ORDER_STATUS_PENDING_PAYMENT,
  ORDER_STATUS_RECEIVED,
  ORDER_STATUS_SHIPPED
} from '../../types/order'
import { formatDateTime, trimNum } from '../../utils/format'
import { grad } from '../../utils/gradient'

/**
 * 订单详情（`/orders/:orderNo`，**需登录态**——路由 meta.requiresAuth 拦）。
 * 骨架同商品详情页：顶栏 + 页脚 + 1280 容器 + 面包屑；**不带万能搜索框**（它不是商品浏览页）。
 *
 * 十条口径（都不是随手写的）：
 * ① **一切以服务端为准 + 成功后重拉**：状态、状态文案、金额、小计**全部**用服务端下发的值，
 *    写操作（支付 / 确认收货）成功后**重新拉详情**——状态迁移由域决定，前端不自造、不本地改。
 * ② **状态文案只取 `statusMallLabel`**；`status` 枚举名只用来判「显示哪个操作」，
 *    也**不拿文案当分支条件**（领域可改措辞，改了就会静默改掉按钮）。
 * ③ **支付金额的比对在域内**（金额是领域规则）：页面只负责把顾客填的数送过去，
 *    不一致时域侧回 400「支付金额与订单总额不一致（应付 X 元，实付 Y 元）」——
 *    拦截器已统一弹后端 msg，本页**不再包一层自己的文案**。
 * ④ **支付面板行内展开**：金额输入与确认按钮就地出现，`?pay=1` 进入时自动展开（列表页「去支付」的落点）。
 *    ⚠ 本端虽已有模态基座（`ModalShell`），但它在**本页**只装改地址那一个弹窗（见 ⑧）——
 *    支付面板仍旧就地展开，别顺手把它也搬进弹窗。
 * ⑤ **确认收货走行内两步确认**（同购物车页的破坏性动作），不用 `window.confirm`。
 * ⑥ **`shipNo` 为 null 时不渲染「快递单号」那一行**（别显示 "null"）。
 * ⑦ **`address` 为空快照时不渲染地址块**：后端映射处显式判空（形态上可为 null），
 *    这里不臆造一块空地址出来。
 * ⑧ **改收货地址只摆给「待支付」看**（地址块旁与支付面板内各一个入口，同一个弹窗）：
 *    闸门在**域内**（非待支付一律 400「订单当前状态「…」不允许修改收货地址」），
 *    这里不摆一个注定失败的按钮。⚠ 它与下单**同一个口径**：页面只给 `addressId`，
 *    地址内容由服务端取回并校验归属；改的是**本单的快照**，**不动顾客地址簿**。
 *    改完**重拉详情**（快照由服务端下发，见文件头 ①）。
 * ⑨ **待支付倒计时**取的截止时刻是域下发的 `expireTime`——**不要**用 `createTime` 加一个前端写死的
 *    分钟数再算一遍：那会开出第二个说了算的地方，与域侧判定必然漂移。走秒与文案在
 *    `composables/useCountdown.ts`（两个订单页共用一份，不各写一遍）。
 *    ⚠ 倒计时是**提示**、不是闸门：到 0 只说明「该重新拉一次了」，**能不能付款仍由域侧说了算**
 *    （过期后支付接口回 400「该订单已过期（超过支付时限），请重新下单」，文案原样展示）。
 *    ⚠ 归零后**自动拉一次**详情：把那一笔被**域内定时任务**改成「已取消」的结果拿回来
 *    （任务最坏晚一个扫描周期，拉早了拿回的仍是待支付，故只拉一次、不无脑重拉）。
 * ⑩ **取消订单（仅待支付）/ 仅退款（仅已支付未发货）**：闸门同样在**域内**状态机，
 *    这里判一次只为不摆出注定失败的按钮（判据与按钮渲染同源）；两者都走行内两步确认（同 ⑤）。
 *    ⚠ 它们是**两个动作**，不是同一个：取消是「没付过钱的单不买了」，仅退款是「付过的钱退回去」——
 *    域侧是两个状态、两条迁移边，故这里也是两个按钮（合并就得在本层猜状态，而状态是域的事实）。
 *    ⚠ 倒计时归零后掐掉的是**支付**入口，**不是取消**入口：域侧取消不看超时，仍是「待支付 → 已取消」。
 * ⑪ **商品评价入口只摆给「已收货」**（`RECEIVED`）——这是**唯一的评价门禁**，判据是**枚举名**（同 ②），
 *    闸门在**服务端**（它经 trade-center 校验订单属本人且已收货，不满足回 400 中文提示），
 *    这里判一次只为不摆出注定失败的按钮。
 *    ⚠ **按商品（SPU）评，不按明细行**：同单里同一 SPU 的多个 SKU 行是**一条**评价
 *    （`evaluated` 在该 SPU 的所有行上同值），故入口按 `spuId` 归组后给一次。
 *    ⚠ `evaluated` **三态**（见 `types/order.ts`）：`null` = 后端没取到该标记（store 域不可用的
 *    **静默降级**）→ **入口照常给**，重复提交由服务端拒（域侧 `(order_no, spu_id)` 唯一键回 400）——
 *    页面**不猜**，猜错就会藏掉一个本来能用的入口。
 *    ⚠ 提交面板**行内展开**（同 ④ 的支付面板；本端模态层只装选地址那一个）；提交成功后**重拉详情**，
 *    已评价态由服务端下发（本页另外记一份「刚提交成功」只用于重拉回来之前那一小段，见 `justEvaluated`）。
 *    ⚠ 评价内容**最长 500 字**（与后端约束镜像，超了在这里就被拦下，不必等一个 400）。
 */

const route = useRoute()

/**
 * 路由参数是外部输入：`orderNo` 与后端下发的单号形态一致（`yyyyMMddHHmmss` + 4 位序列），
 * 空串就直接按「订单不存在」渲染，不发请求（`/orders/` 会落到列表页，这里只管带参的情形）。
 */
const orderNo = computed(() => {
  const raw = route.params.orderNo
  return (Array.isArray(raw) ? raw[0] : raw) ?? ''
})

const order = ref<OrderVO | null>(null)
const loading = ref(false)
/** 非空即错误态：文案直接用后端 msg（404 → 「订单不存在」；下游故障 → 「…暂不可用」） */
const errorMsg = ref('')

/** 支付面板是否展开；`payAmount` 是输入框里的字符串（提交时再转数） */
const payOpen = ref(false)
const payAmount = ref('')
const paying = ref(false)

/** 确认收货的行内两步确认态 */
const confirmingReceive = ref(false)
const receiving = ref(false)

/** 改收货地址的弹窗与在途态（见文件头 ⑧） */
const addressOpen = ref(false)
const addressSaving = ref(false)

/**
 * 待支付的门（去支付 / 改地址 / 取消订单三个入口**共用一个判据**，不各写一遍）。
 * ⚠ 判的是**枚举名**（不是文案），理由同文件头 ②。
 */
const isPendingPayment = computed(() => order.value?.status === ORDER_STATUS_PENDING_PAYMENT)

/** 仅退款的门：**仅「已支付、未发货」**（= 域侧仅退款动作声明的来源状态 `PAID`；「未发货」由主链顺序保证） */
const canRefund = computed(() => order.value?.status === ORDER_STATUS_PAID)

/**
 * 秒级时钟：只在「待支付**且**这一笔有截止时刻」时才走（没有倒计时要显示时就不该有定时器在跳）。
 * 挂 / 停在 `useCountdown` 内由 `watch` 负责。
 */
const now = useNowTick(() => isPendingPayment.value && !!order.value?.expireTime)

/**
 * 距支付截止时刻的剩余毫秒；`null` = 这一笔没有截止时刻（老单，语义是**无超时**：
 * 不倒计时、也不判过期）。非待支付一律为 `null`——那个时刻在别的状态下不承载任何含义。
 */
const remaining = computed(() =>
  isPendingPayment.value ? remainingMs(order.value?.expireTime ?? null, now.value) : null
)

/** 已过支付截止时刻（**到点即过期**，与域侧 `isTimedOut` / 关单取数同一句闭区间） */
const expired = computed(() => {
  const ms = remaining.value
  return ms !== null && ms <= 0
})

/** 倒计时文案；无截止时刻 / 已过期 → 空串（模板据此不渲染那一行） */
const countdown = computed(() => {
  const ms = remaining.value
  return ms === null || ms <= 0 ? '' : countdownText(ms)
})

/** 过期后是否已自动重拉过（见文件头 ⑨：只拉一次，不无脑重拉） */
const expiredReloaded = ref(false)

/** 取消 / 仅退款的行内两步确认态与在途态（同时至多一个动作） */
const confirming = ref<'cancel' | 'refund' | null>(null)
const acting = ref(false)

/** 并发序号：连点两笔单时，后发的请求作废先发的响应（与列表页同一手法） */
let seq = 0

/**
 * 展开支付面板：金额**预填订单总额**——它必须与总额相等才算成功（域内比对），
 * 预填只是省去顾客手打一遍，不是把校验搬到前端（顾客照样可以改，改错了由域回 400）。
 *
 * ⚠ 已过期就不开（见文件头 ⑨）：域侧支付会回 400，展开一个注定失败的输入框只会误导顾客。
 */
function openPay(): void {
  const o = order.value
  if (!o || o.status !== ORDER_STATUS_PENDING_PAYMENT || expired.value) return
  payAmount.value = trimNum(o.totalAmount)
  payOpen.value = true
}

/** 列表页「去支付」带 `?pay=1` 进来：详情到位后自动展开支付面板（状态不对 / 已过期则不开） */
function maybeAutoOpenPay(o: OrderVO): void {
  if (route.query.pay !== '1') return
  if (o.status !== ORDER_STATUS_PENDING_PAYMENT) return
  openPay()
}

/**
 * 状态一旦离开「待支付」就收起支付面板（见文件头 ④）。
 *
 * ⚠ 支付面板不在下面那块操作区里（它是独立的 `v-if="payOpen"`），状态被别处推进——顾客在另一页
 * 操作、或**域内定时任务把超时单关掉**——操作区整块消失，面板却还开着，且 `expired` 会跟着变回
 * `false`（非待支付的剩余时间恒为 `null`）：提示话术退回「需与订单总额一致才能支付成功」、
 * 「确认支付」重新可点，按下去只能拿一个 400。故由状态驱动收起，而不是靠顾客自己点「取消」。
 */
watch(isPendingPayment, (on) => {
  if (!on) payOpen.value = false
})

async function load(no: string): Promise<void> {
  const current = ++seq
  // 空单号不发请求：`GET /orders/` 是另一条路径（列表），发给它只会拿回一个与本页无关的错
  if (!no) {
    order.value = null
    errorMsg.value = '订单不存在'
    loading.value = false
    return
  }
  loading.value = true
  try {
    const data = await orderApi.detail(no)
    if (current !== seq) return
    order.value = data
    errorMsg.value = ''
    maybeAutoOpenPay(data)
  } catch (e) {
    if (current !== seq) return
    order.value = null
    errorMsg.value = e instanceof Error ? e.message : '订单暂不可用，请稍后重试'
  } finally {
    if (current === seq) loading.value = false
  }
}

/** 支付（面板里的「确认支付」）。成功后**重拉详情**：状态与文案都由域下发 */
async function submitPay(): Promise<void> {
  const o = order.value
  if (!o || paying.value) return

  const amount = Number(payAmount.value)
  // 客户端只拦「填不成数」这一种（免得发一个注定 400 的空值下去）；
  // 「金额对不对」是域内规则，**不在这里再写一遍**
  if (!Number.isFinite(amount) || amount <= 0) {
    showToast('请输入正确的支付金额', 'info')
    return
  }

  paying.value = true
  try {
    await orderApi.pay(o.orderNo, { amount })
    payOpen.value = false
    showToast('支付成功', 'success')
    await load(o.orderNo)
  } catch {
    // 拦截器已弹后端 msg（金额不符 → 域内 400 的中文文案原样展示，见文件头 ③）
  } finally {
    paying.value = false
  }
}

/** 确认收货（两步确认的第二步）。成功后同样重拉 */
async function receive(): Promise<void> {
  const o = order.value
  if (!o || receiving.value) return

  receiving.value = true
  try {
    await orderApi.receive(o.orderNo)
    showToast('已确认收货', 'success')
    await load(o.orderNo)
  } catch {
    // 拦截器已弹后端 msg；重拉把页面拉回与服务端一致（状态可能已被别处推进）
    await load(o.orderNo)
  } finally {
    receiving.value = false
    confirmingReceive.value = false
  }
}

/* ---- 取消订单 / 仅退款（见文件头 ⑩） ---- */

/**
 * 取消 / 仅退款（两步确认的第二步）。
 *
 * ⚠ 两者合用一个提交函数：形状**完全一致**——无请求体、出参 `void`、成功后重拉详情；
 * `kind` 只用来选调哪个接口与弹哪句 toast（文案不同：「已取消」≠「已退款」）。
 * ⚠ 失败**照旧重拉**：状态可能已被别处推进（顾客在两个页面同时操作、或定时任务刚把它关了），
 * 重拉让页面立刻回到与服务端一致的样子；中文 msg 由拦截器弹出。
 */
async function submitAction(kind: 'cancel' | 'refund'): Promise<void> {
  const o = order.value
  if (!o || acting.value) return

  acting.value = true
  try {
    if (kind === 'cancel') await orderApi.cancel(o.orderNo)
    else await orderApi.refund(o.orderNo)
    showToast(kind === 'cancel' ? '订单已取消' : '已退款', 'success')
    await load(o.orderNo)
  } catch {
    // 拦截器已弹后端 msg（状态不允许 → 域内的中文文案原样展示）
    await load(o.orderNo)
  } finally {
    acting.value = false
    confirming.value = null
  }
}

/* ---- 改收货地址（见文件头 ⑧） ---- */

/** 开改地址弹窗（地址块旁与支付面板内两个入口共用）；闸门见 {@link isPendingPayment} */
function openAddress(): void {
  if (!isPendingPayment.value || addressSaving.value) return
  addressOpen.value = true
}

/** 关弹窗（取消 / Esc / 点遮罩）—— 提交在途不关，与 `ModalShell` 的 `closable` 同一口径 */
function closeAddress(): void {
  if (addressSaving.value) return
  addressOpen.value = false
}

/**
 * 提交新地址（弹窗里的主按钮）。成功后**重拉详情**：地址快照由服务端下发，页面不本地改（同文件头 ①）。
 *
 * ⚠ 失败**不关弹窗**：非待支付（状态在别处被推进）/ 地址不存在（别人删了）的中文文案由拦截器弹出，
 * 顾客可以换一条——关掉弹窗只会让他再点一次入口。
 */
async function submitAddress(addressId: number): Promise<void> {
  const o = order.value
  if (!o || addressSaving.value) return

  addressSaving.value = true
  try {
    await orderApi.updateAddress(o.orderNo, { addressId })
    addressOpen.value = false
    showToast('收货地址已修改', 'success')
    await load(o.orderNo)
  } catch {
    // 拦截器已弹后端 msg
  } finally {
    addressSaving.value = false
  }
}

/* ---- 商品评价（见文件头 ⑪）---- */

/** 评价内容上限（与后端 `MallEvaluationSubmitDTO` 的 `@Size(max = 500)` 镜像；`maxlength` 也用它） */
const EVAL_CONTENT_MAX = 500

/** 已收货 = 评价入口的唯一闸门（判的是**枚举名**，不是文案，见文件头 ②） */
const isReceived = computed(() => order.value?.status === ORDER_STATUS_RECEIVED)

/** 评价目标行（页面形状，不外移）：**按 SPU 归组**，一个商品一行 */
interface EvalTarget {
  spuId: number
  /** 商品名（下单当时的快照）——同 SPU 多行的名字相同，取第一行的即可 */
  goodsName: string
  /** 服务端下发的已评价态；**`null` = 不知道**（静默降级，见 `types/order.ts`） */
  evaluated: boolean | null
}

/**
 * 评价目标：按 `spuId` 归组后的商品列表（同 SPU 的多个 SKU 行合成一条 —— 评价是按商品一条）。
 * ⚠ 取**第一行**的 `evaluated`：同 SPU 各行该值由后端保证同值，逐行判会得到同一个答案。
 */
const evalTargets = computed<EvalTarget[]>(() => {
  const seen = new Set<number>()
  const rows: EvalTarget[] = []
  for (const line of order.value?.items ?? []) {
    if (seen.has(line.spuId)) continue
    seen.add(line.spuId)
    rows.push({ spuId: line.spuId, goodsName: line.goodsName, evaluated: line.evaluated })
  }
  return rows
})

/** 提交面板当前评的是哪个商品（`null` = 面板收起）；评分默认 5 星 */
const evalOpenSpuId = ref<number | null>(null)
const evalScore = ref(5)
const evalContent = ref('')
const evalSubmitting = ref(false)

/**
 * 本页**刚提交成功**的商品（只用于「重拉回来之前」那一小段：提交成功后重拉要走一个往返，
 * 期间若后端把 `evaluated` 静默降级成 `null`，入口就会又冒出来）。
 * ⚠ 它**不是权威态**，只是本页刚刚亲历的事实；服务端下发的 `evaluated === true` 优先。
 */
const justEvaluated = ref<number[]>([])

/** 面板标题用的那一行（点了「评价」之后按 spuId 找回来） */
const evalOpenTarget = computed(
  () => evalTargets.value.find((t) => t.spuId === evalOpenSpuId.value) ?? null
)

/** 是否已评价：服务端说「是」或本页刚提交过（`false` / `null` 都按「还没评」处理，见文件头 ⑪） */
function isEvaluated(target: EvalTarget): boolean {
  return target.evaluated === true || justEvaluated.value.includes(target.spuId)
}

/** 开评价面板（换一个商品就是换一个 spuId，评分与文字一并重置，免得带上一条的内容） */
function openEval(spuId: number): void {
  if (evalSubmitting.value) return
  evalOpenSpuId.value = spuId
  evalScore.value = 5
  evalContent.value = ''
}

/** 关面板（提交在途不关，与支付面板同一口径） */
function closeEval(): void {
  if (evalSubmitting.value) return
  evalOpenSpuId.value = null
}

/**
 * 提交评价。成功后**重拉详情**（已评价标记由服务端下发，见文件头 ⑪）。
 *
 * ⚠ 失败**不关面板**：订单未完成 / 该商品已评价的中文 msg 由拦截器弹出，
 * 顾客可以改一改重试；关掉它只会让人再点一次入口。
 */
async function submitEval(): Promise<void> {
  const spuId = evalOpenSpuId.value
  const o = order.value
  if (spuId === null || !o || evalSubmitting.value) return

  evalSubmitting.value = true
  try {
    await evaluationApi.submit({
      orderNo: o.orderNo,
      spuId,
      score: evalScore.value,
      // 空内容不下发空串：不写字就是没有（后端也只做长度校验，不做非空要求）
      content: evalContent.value.trim() || undefined
    })
    if (!justEvaluated.value.includes(spuId)) {
      justEvaluated.value = [...justEvaluated.value, spuId]
    }
    showToast('评价已提交', 'success')
    evalOpenSpuId.value = null
    await load(o.orderNo)
  } catch {
    // 拦截器已弹后端 msg
  } finally {
    evalSubmitting.value = false
  }
}

/* ---- 展示辅助 ---- */

/** 规格文案，如「颜色：曜石黑 / 容量：256G」；无规格时是空对象 → 空串，模板不渲染这一行 */
function specText(line: OrderVO['items'][number]): string {
  return Object.entries(line.specAttrs ?? {})
    .map(([k, v]) => `${k}：${v}`)
    .join(' / ')
}

/** 收货地址整行文案（省市区可空，空就不占位） */
const addressText = computed(() => {
  const a = order.value?.address
  if (!a) return ''
  return [a.region, a.detailAddress].filter(Boolean).join(' ')
})

/** 商品占位色相由 spuId 派生（与列表页 / 购物车同一手法） */
function hue(spuId: number): number {
  return spuId % 360
}

const failedImgs = ref<number[]>([])

function imgSrc(line: OrderVO['items'][number]): string {
  if (!line.mainImage || failedImgs.value.includes(line.skuId)) return ''
  return line.mainImage
}

function markImgFailed(skuId: number): void {
  if (!failedImgs.value.includes(skuId)) failedImgs.value = [...failedImgs.value, skuId]
}

function nameLabel(line: OrderVO['items'][number]): string {
  return line.goodsName?.trim().charAt(0) || '商'
}

/**
 * 倒计时归零 → **自动重拉一次**详情（见文件头 ⑨）。
 *
 * ⚠ 只拉一次：真正把订单改成「已取消」的是**域内定时任务**（最坏晚一个扫描周期），
 * 任务还没跑到时重拉拿回来的仍是「待支付」——无脑重拉就是死循环。
 * 之后要再拉一次，由页面上的「刷新」交给顾客点。
 */
watch(expired, (isExpired) => {
  if (!isExpired || expiredReloaded.value) return
  expiredReloaded.value = true
  void load(orderNo.value)
})

/** 换单号（同页复用组件实例）即重拉，并把上一个单的面板态清干净 */
watch(
  orderNo,
  (no) => {
    payOpen.value = false
    payAmount.value = ''
    confirmingReceive.value = false
    addressOpen.value = false
    confirming.value = null
    // 评价面板与「刚提交成功」的本地记录都是**这一笔单**的态，换单必须清（见文件头 ⑪）
    evalOpenSpuId.value = null
    evalContent.value = ''
    justEvaluated.value = []
    // ⚠ 必须清：不然换到另一笔也过期的单时，`expired` 的 watch 会被上一次的「已拉过」挡住
    expiredReloaded.value = false
    failedImgs.value = []
    void load(no)
  },
  { immediate: true }
)
</script>

<template>
  <TopBar />

  <section class="order-detail">
    <div class="container">
      <!-- 面包屑：我的订单 → 本单（单号是当前页，不给链接） -->
      <nav class="order-detail__crumbs">
        <router-link class="order-detail__crumb-link" to="/orders">我的订单</router-link>
        <span class="order-detail__crumb-sep">›</span>
        <span class="order-detail__crumb-cur tnum">{{ orderNo }}</span>
      </nav>

      <p v-if="loading" class="order__state" role="status">正在加载订单…</p>

      <!-- 错误态：文案是后端 msg（不存在 / 下游故障都以它为准） -->
      <div v-else-if="errorMsg" class="order__fallback">
        <p class="order__fallback-text">{{ errorMsg }}</p>
        <button class="order__retry" type="button" @click="load(orderNo)">重试</button>
        <router-link class="order__fallback-link" to="/orders">返回我的订单</router-link>
      </div>

      <template v-else-if="order">
        <!-- 状态条：文案取域下发的 statusMallLabel（见文件头 ②） -->
        <div class="order-detail__status">
          <span class="order-detail__status-text">{{ order.statusMallLabel }}</span>
          <span class="order-detail__status-sub">
            下单时间 {{ formatDateTime(order.createTime) }}
          </span>
          <!-- 待支付倒计时（见文件头 ⑨）：截止时刻为 null 的老单没有这一行 -->
          <span v-if="countdown" class="order-detail__countdown">
            剩余支付时间 <b class="tnum">{{ countdown }}</b>
          </span>
          <!-- 已过期：提示 + 一个手动刷新（自动拉过一次了，任务可能还没跑到） -->
          <span v-else-if="expired" class="order-detail__countdown order-detail__countdown--over">
            支付已超时，订单将自动取消
            <button class="order-detail__link" type="button" @click="load(orderNo)">刷新</button>
          </span>
        </div>

        <div class="order-detail__panel">
          <dl class="order-detail__meta">
            <div class="order-detail__meta-row">
              <dt class="order-detail__meta-key">订单编号</dt>
              <dd class="order-detail__meta-val tnum">{{ order.orderNo }}</dd>
            </div>
            <div class="order-detail__meta-row">
              <dt class="order-detail__meta-key">店铺</dt>
              <dd class="order-detail__meta-val">{{ order.storeName }}</dd>
            </div>
            <!-- 快递单号：未发货时 shipNo 是 null，整行不渲染（见文件头 ⑥） -->
            <div v-if="order.shipNo" class="order-detail__meta-row">
              <dt class="order-detail__meta-key">快递单号</dt>
              <dd class="order-detail__meta-val tnum">{{ order.shipNo }}</dd>
            </div>
          </dl>

          <!-- 收货地址快照：下单当时的值（此后去地址簿改 / 删那条都不影响本单）；空快照不渲染（见文件头 ⑦） -->
          <div v-if="order.address" class="order-detail__addr">
            <div class="order-detail__addr-head">
              <h2 class="order-detail__block-title">收货地址</h2>
              <!-- 改地址入口（仅待支付；与支付面板内那个是同一个弹窗，见文件头 ⑧） -->
              <button
                v-if="isPendingPayment"
                class="order-detail__link"
                type="button"
                @click="openAddress"
              >
                修改地址
              </button>
            </div>
            <p class="order-detail__addr-line">
              <b class="order-detail__addr-name">{{ order.address.receiverName }}</b>
              <span class="order-detail__addr-phone tnum">{{ order.address.receiverPhone }}</span>
            </p>
            <p class="order-detail__addr-text">{{ addressText }}</p>
          </div>
        </div>

        <!-- 商品行表：单价 × 数量 = 小计；⚠ 小计是**下单当时算好落库的值**，页面不乘（见文件头 ①） -->
        <div class="order-detail__panel">
          <h2 class="order-detail__block-title">商品</h2>

          <ul class="order-detail__items">
            <li v-for="line in order.items" :key="line.skuId" class="order-line">
              <div class="order-line__thumb" :style="{ background: grad(hue(line.spuId), 60, 91, 82) }">
                <img
                  v-if="imgSrc(line)"
                  class="order-line__img"
                  :src="imgSrc(line)"
                  :alt="line.goodsName"
                  @error="markImgFailed(line.skuId)"
                />
                <span
                  v-else
                  class="order-line__ph"
                  :style="{ color: `hsl(${hue(line.spuId)} 42% 32%)` }"
                >
                  {{ nameLabel(line) }}
                </span>
              </div>

              <div class="order-line__info">
                <p class="order-line__name clamp-2">{{ line.goodsName }}</p>
                <p v-if="specText(line)" class="order-line__spec">{{ specText(line) }}</p>
              </div>

              <div class="order-line__price tnum">¥{{ trimNum(line.unitPrice) }}</div>
              <div class="order-line__qty tnum">× {{ line.quantity }}</div>
              <div class="order-line__sub tnum">¥{{ trimNum(line.subtotal) }}</div>
            </li>
          </ul>

          <div class="order-detail__total">
            <span class="order-detail__total-qty">
              共 <b class="tnum">{{ order.totalQuantity }}</b> 件
            </span>
            <span class="order-detail__total-label">合计</span>
            <span class="order-detail__total-amount tnum">
              <span class="order-detail__total-sym">¥</span>{{ trimNum(order.totalAmount) }}
            </span>
          </div>
        </div>

        <!-- 操作区：按状态显示（判的是**枚举名**，见文件头 ②）。
             待支付 → 去支付 + 取消订单；已支付 → 仅退款；已发货 → 确认收货。
             其余状态（含两个结束过程的落点「已取消」/「已退款」）没有可做的动作，整块不渲染 -->
        <div
          v-if="isPendingPayment || canRefund || order.status === ORDER_STATUS_SHIPPED"
          class="order-detail__ops"
        >
          <template v-if="isPendingPayment">
            <button
              class="order-detail__btn"
              type="button"
              :disabled="payOpen || paying || expired"
              @click="openPay"
            >
              去支付
            </button>

            <!-- 取消订单：行内两步确认（见文件头 ⑤ / ⑩）。
                 ⚠ 超时后它**照旧可用**：域侧取消不看超时 -->
            <template v-if="confirming === 'cancel'">
              <span class="order-detail__confirm">确认取消这一笔订单？</span>
              <button
                class="order-detail__btn"
                type="button"
                :disabled="acting"
                @click="submitAction('cancel')"
              >
                {{ acting ? '提交中…' : '确认取消' }}
              </button>
              <button
                class="order-detail__link"
                type="button"
                :disabled="acting"
                @click="confirming = null"
              >
                再想想
              </button>
            </template>
            <button
              v-else
              class="order-detail__link"
              type="button"
              :disabled="acting"
              @click="confirming = 'cancel'"
            >
              取消订单
            </button>
          </template>

          <!-- 仅退款：只摆给「已支付、未发货」（见文件头 ⑩） -->
          <template v-else-if="canRefund">
            <template v-if="confirming === 'refund'">
              <span class="order-detail__confirm">
                确认退款 ¥{{ trimNum(order.totalAmount) }}？退款后商品库存将回补
              </span>
              <button
                class="order-detail__btn"
                type="button"
                :disabled="acting"
                @click="submitAction('refund')"
              >
                {{ acting ? '提交中…' : '确认退款' }}
              </button>
              <button
                class="order-detail__link"
                type="button"
                :disabled="acting"
                @click="confirming = null"
              >
                再想想
              </button>
            </template>
            <button
              v-else
              class="order-detail__btn"
              type="button"
              :disabled="acting"
              @click="confirming = 'refund'"
            >
              仅退款
            </button>
          </template>

          <template v-else>
            <!-- 确认收货：行内两步确认（见文件头 ⑤） -->
            <template v-if="confirmingReceive">
              <span class="order-detail__confirm">确认已收到货？</span>
              <button
                class="order-detail__btn"
                type="button"
                :disabled="receiving"
                @click="receive"
              >
                {{ receiving ? '提交中…' : '确认收货' }}
              </button>
              <button
                class="order-detail__link"
                type="button"
                :disabled="receiving"
                @click="confirmingReceive = false"
              >
                取消
              </button>
            </template>
            <button v-else class="order-detail__btn" type="button" @click="confirmingReceive = true">
              确认收货
            </button>
          </template>
        </div>

        <!-- 商品评价：**只摆给「已收货」**（见文件头 ⑪）。一个商品一行——评价按 SPU 一条，
             同单里同一 SPU 的多个 SKU 行合成一条，故这里的行数可能少于上面商品行数 -->
        <div v-if="isReceived && evalTargets.length" class="order-detail__panel">
          <h2 class="order-detail__block-title">商品评价</h2>

          <ul class="order-eval">
            <li v-for="t in evalTargets" :key="t.spuId" class="order-eval__row">
              <span class="order-eval__name clamp-2">{{ t.goodsName }}</span>
              <!-- 已评价：`evaluated === true` 或本页刚提交过（见 isEvaluated） -->
              <span v-if="isEvaluated(t)" class="order-eval__done">已评价</span>
              <!-- `evaluated` 为 null（后端没取到该标记）时**照常给入口**，重复提交由服务端拒 -->
              <button
                v-else
                class="order-eval__open"
                type="button"
                @click="openEval(t.spuId)"
              >
                评价
              </button>
            </li>
          </ul>

          <p class="order-eval__hint">同一商品只能评价一次，提交后不可修改</p>
        </div>

        <!-- 评价提交面板：行内展开（见文件头 ⑪ / ④）。
             ⚠ 评的是**商品**，规格 / 单价 / 数量由服务端从本单明细归组，页面不传 -->
        <section v-if="evalOpenTarget" class="order-eval-panel">
          <h2 class="order-eval-panel__title">评价商品</h2>
          <p class="order-eval-panel__goods">{{ evalOpenTarget.goodsName }}</p>

          <div class="order-eval-panel__score">
            <span class="order-eval-panel__score-label">评分</span>
            <StarRating
              :score="evalScore"
              interactive
              :disabled="evalSubmitting"
              @pick="evalScore = $event"
            />
            <span class="order-eval-panel__score-num tnum">{{ evalScore }} 星</span>
          </div>

          <form class="order-eval-panel__form" autocomplete="off" @submit.prevent="submitEval">
            <div class="field">
              <label class="field__label" for="evalContent">评价内容（选填）</label>
              <div class="field__box order-eval-panel__box">
                <textarea
                  id="evalContent"
                  v-model="evalContent"
                  class="field__input order-eval-panel__text"
                  rows="4"
                  :maxlength="EVAL_CONTENT_MAX"
                  :disabled="evalSubmitting"
                  placeholder="说说这件商品怎么样"
                ></textarea>
              </div>
              <p class="field__hint">最多 {{ EVAL_CONTENT_MAX }} 字</p>
            </div>

            <div class="order-eval-panel__ops">
              <button class="order-eval-panel__submit" type="submit" :disabled="evalSubmitting">
                {{ evalSubmitting ? '提交中…' : '提交评价' }}
              </button>
              <button
                class="order-eval-panel__cancel"
                type="button"
                :disabled="evalSubmitting"
                @click="closeEval"
              >
                取消
              </button>
            </div>
          </form>
        </section>

        <!-- 支付面板：行内展开（见文件头 ④）。金额必须与订单总额一致，比对在域内 -->
        <section v-if="payOpen" class="order-pay">
          <h2 class="order-pay__title">支付订单</h2>

          <form class="order-pay__form" autocomplete="off" @submit.prevent="submitPay">
            <div class="field">
              <label class="field__label" for="payAmount">支付金额（元）</label>
              <div class="field__box">
                <input
                  id="payAmount"
                  v-model="payAmount"
                  class="field__input"
                  type="text"
                  inputmode="decimal"
                  :disabled="paying"
                />
              </div>
              <p class="field__hint">
                {{ expired ? '支付已超时，该订单将自动取消' : '需与订单总额一致才能支付成功' }}
              </p>
            </div>

            <!-- 支付前发现寄错地址：就地改（仅待支付才渲染，见文件头 ⑧）。
                 ⚠ 它与上面的「取消」不是一回事：取消只是收起支付面板，这里改的是那一单的快照 -->
            <p v-if="isPendingPayment" class="order-pay__addr">
              收货地址有误？
              <button
                class="order-detail__link"
                type="button"
                :disabled="paying"
                @click="openAddress"
              >
                修改地址
              </button>
            </p>

            <div class="order-pay__ops">
              <!-- 已过期就禁掉提交（域侧必回 400，放它按下去只是白跑一趟）；面板本身留着，
                   顾客仍能从上面那块改地址或取消订单 -->
              <button class="order-pay__submit" type="submit" :disabled="paying || expired">
                {{ paying ? '支付中…' : '确认支付' }}
              </button>
              <button class="order-pay__cancel" type="button" :disabled="paying" @click="payOpen = false">
                取消
              </button>
            </div>
          </form>
        </section>

        <!-- 改地址弹窗：两个入口（地址块旁 / 支付面板内）共用这一个。
             ⚠ 无障碍名与弹窗里那个可见标题（AddressPicker 的「选择收货地址」）**逐字一致**：
             读屏读到的名字和眼睛看到的标题不该是两个说法。
             ⚠ 提交在途时 `closable=false`——那时关掉它，顾客会以为没改，而地址可能已经改了 -->
        <ModalShell
          v-if="addressOpen"
          label="选择收货地址"
          :closable="!addressSaving"
          @close="closeAddress"
        >
          <AddressPicker
            :submitting="addressSaving"
            confirm-text="确认修改"
            @confirm="submitAddress"
            @cancel="closeAddress"
          />
        </ModalShell>
      </template>
    </div>
  </section>

  <SiteFooter />
</template>
