<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '../../components/TopBar.vue'
import SiteFooter from '../../components/SiteFooter.vue'
import Pager from '../../components/Pager.vue'
import { orderApi } from '../../api/order'
import { countdownText, remainingMs, useNowTick } from '../../composables/useCountdown'
import { showToast } from '../../composables/useToast'
import type { OrderVO } from '../../types/order'
import {
  ORDER_STATUS_PAID,
  ORDER_STATUS_PENDING_PAYMENT,
  ORDER_STATUS_SHIPPED
} from '../../types/order'
import { formatDateTime, trimNum } from '../../utils/format'
import { grad } from '../../utils/gradient'

/**
 * 我的订单（`/orders`，**需登录态**——路由 meta.requiresAuth 拦，见 router/index.ts）。
 * 骨架同购物车页：顶栏 + 页脚 + 1280 容器，**不带万能搜索框**（它不是商品浏览页）。
 *
 * 八条口径（都不是随手写的）：
 * ① **分页状态住在 URL query 上**（`?page=`），URL 是唯一真相源——刷新 / 后退都不丢，
 *    与商品列表页同一手法。每页 10 条（订单列表不受「商品网格 7 列 / 49 条」那条约束）。
 * ② **状态文案只取 `statusMallLabel`**（域下发）；`status` 枚举名**只用来判**
 *    「该显示哪个操作」——拿文案当分支条件，改一个错别字就会静默改掉按钮。
 * ③ **金额与件数一律用服务端值**（`totalAmount` / `totalQuantity` / 行 `subtotal`），
 *    页面不自己乘加一遍（自己算就是第二份会漂移的金额口径）。
 * ④ **`shipNo` 为 null 时不渲染快递单号那一行**（别显示 "null"）。
 * ⑤ **破坏性动作走行内两步确认**（不用 `window.confirm`；模态层只装下单流程的选地址）：确认收货就地
 *    变成「确认收货？/ 确认收货 / 取消」，同时只留一处确认。取消订单 / 仅退款同此（见 ⑧）。
 * ⑥ **整卡可点进详情**（`.order-card__hit` 铺满卡片的透明链接，同列表页 `.cat-card__hit`），
 *    行内操作按钮压在它**上面**（z-index）——否则点「确认收货」会顺带跳走。
 * ⑦ **待支付倒计时**取的截止时刻是域下发的 `expireTime`（**不要**按 `createTime` 自己再算一遍），
 *    走秒与文案在 `composables/useCountdown.ts`（与详情页共用一份）。截止时刻为 `null` 的老单
 *    没有倒计时那一格，也**不**算过期。⚠ 倒计时是**提示**不是闸门——能不能付款由域侧说了算。
 * ⑧ **超时后盯住服务端关单**：倒计时归零 → 掐掉「去支付」（改成一句「支付已超时」）
 *    并按 `EXPIRED_POLL_MS`（5 秒）静默重拉列表，直到那一笔不再是「待支付」——它是被**域内定时任务**
 *    取消的（最坏晚一个扫描周期），只拉一次的话顾客会一直盯着一笔实际已取消的单。
 *    ⚠ 轮询有次数上限（`EXPIRED_POLL_MAX`，12 次）：下游故障时不能把它变成一个永不停止的请求源。
 *    ⚠ 轮询**给用户触发的加载让路**（`loadingInFlight`）：两者共用同一个并发序号，轮询插进去会把
 *    用户那次响应作废，且两边的收尾都不复位 `loading` —— 整页会永停在「正在加载订单…」。
 *    ⚠ 「取消订单」在超时后**照旧可用**（域侧取消不看超时），它是顾客不等任务的即时出口。
 */

/** 每页 10 条（后端 `BasePageVO` 有 @Max(100)，这里取一个列表可读值） */
const PAGE_SIZE = 10

/** 缩略图最多摆几张：多出来的由「共 N 件」表达（摆满一行没有信息增益） */
const MAX_THUMBS = 4

const route = useRoute()
const router = useRouter()

/**
 * 解析正整数。URL 是外部输入（手改、老链接、收藏夹），可能是 `?page=abc` / `?page=0` /
 * 数组——一律回退 1，**绝不把 NaN 发给后端**（后端 @Min 会直接 400）。
 */
function positiveInt(raw: unknown, fallback: number): number {
  const n = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isInteger(n) && n > 0 ? n : fallback
}

const pageNum = computed(() => positiveInt(route.query.page, 1))

/**
 * 卡上能**就地提交**的三个动作（各自的闸门在模板里，与域侧动作方法声明的来源状态一致——取消只有待支付、仅退款只有已支付）。
 * ⚠ 「去支付」不在这里：它只是跳详情页，本页不提交——故它不是一个 action。
 */
type OrderAction = 'receive' | 'cancel' | 'refund'

/** 动作成功后的 toast 文案（与域侧的状态文案是两个东西：这里说的是「刚才发生了什么」） */
const ACTION_DONE: Record<OrderAction, string> = {
  receive: '已确认收货',
  cancel: '订单已取消',
  refund: '已退款'
}

/** 超时后盯住服务端关单的轮询：每 5 秒静默重拉一次，最多 12 次（见文件头 ⑧） */
const EXPIRED_POLL_MS = 5000
const EXPIRED_POLL_MAX = 12

const orders = ref<OrderVO[]>([])
const total = ref(0)
const loading = ref(false)
const failed = ref(false)

/** 正在行内两步确认的那一格（同一时刻至多一格）：哪一笔单的哪个动作 */
const confirming = ref<{ orderNo: string; action: OrderAction } | null>(null)
/** 动作在途的单号集合（防连点：两笔单各有一个请求在途是正常场景，故用集合而非单一 id） */
const busyNos = ref<string[]>([])

/** 图片加载失败的行（回退 CSS 渐变占位）；键是 `单号:skuId`，稳定且行内唯一 */
const failedImgs = ref<string[]>([])

/** 并发序号：连着翻页时，后发的请求作废先发的响应（与商品列表页同一手法） */
let seq = 0

/**
 * 非静默加载的**在途份数**（见文件头 ⑧）。存在的理由是静默轮询也会 `++seq`：若用户刚点的
 * 「取消订单 / 翻页」那次加载还在路上，轮询发出去就把它的响应作废了，而两边的收尾**都不复位**
 * `loading`（用户那次因 `current !== seq` 被丢弃，轮询那次又因 `silent` 不动它）→
 * 整页永停在「正在加载订单…」。故轮询前先看这里。
 */
let loadingInFlight = 0

/**
 * 拉一页。
 *
 * @param page   页码（1 起）
 * @param silent **静默**：超时后的轮询用（见文件头 ⑧）——不亮整页的加载态，
 *               否则每 5 秒整个列表会闪成「正在加载订单…」
 */
async function load(page: number, silent = false): Promise<void> {
  const current = ++seq
  if (!silent) {
    loadingInFlight += 1
    loading.value = true
    // 顾客触发的加载重新给轮询计数归零（见文件头 ⑧）：他刚动过手，值得再盯一会儿
    expiredPolls = 0
  }
  // 越界页要改 URL 重拉：那一帧**不摘加载态**（见下面钳位处）
  let clamping = false
  try {
    const res = await orderApi.page({ pageNum: page, pageSize: PAGE_SIZE })
    if (current !== seq) return

    // 钳「越界的当前页」—— Pager 只钳它自己的高亮、**不 emit**，兜底责任在本页面。
    // ⚠ 必须**先判后写**：若先把空结果落地，模板会立刻命中「还没有订单」渲染出一句假文案。
    // ⚠ 且**保持 loading**（`clamping`）：重拉是异步导航，要等守卫跑完才发；这一帧摘了加载态
    //    就会把「正在跳回有效页」闪成空态（同商品列表页那条口径）。
    const pageCount = Math.max(1, Math.ceil(res.total / PAGE_SIZE))
    if (page > pageCount) {
      clamping = true
      void router.replace({ query: { page: String(pageCount) } })
      return
    }

    orders.value = res.records
    total.value = res.total
    failed.value = false
  } catch {
    if (current !== seq) return
    // ⚠ 静默轮询失败**不整块降级**：列表还是好的，只是没拿到最新状态；把整页换成「暂不可用」
    //    反而把顾客手里这一页信息弄没了（见文件头 ⑧）。轮询本身有次数上限，不会一直打。
    if (silent) return
    // 拦截器已弹后端 msg（下游故障时是「订单暂不可用，请稍后重试」）；列表是主内容，拿不到就整块降级
    orders.value = []
    total.value = 0
    failed.value = true
  } finally {
    if (!silent) loadingInFlight -= 1
    if (current === seq && !clamping && !silent) loading.value = false
  }
}

function isBusy(orderNo: string): boolean {
  return busyNos.value.includes(orderNo)
}

function busyStart(orderNo: string): void {
  if (!busyNos.value.includes(orderNo)) busyNos.value = [...busyNos.value, orderNo]
}

function busyEnd(orderNo: string): void {
  busyNos.value = busyNos.value.filter((v) => v !== orderNo)
}

/** 翻页：写回 URL（**URL 是唯一真相源**），query 变了由下面的 watcher 统一拉数据 */
function changePage(page: number): void {
  // 行内确认是针对**某一格**的（见文件头 ⑤）：翻页后那一格已不在屏幕上，留着它等列表回来时
  // 可能又命中（同一笔单仍在新的这一页）——顾客从没在新的一页上点过它
  confirming.value = null
  void router.push({ query: { page: String(page) } })
}

/**
 * 卡上的动作（行内两步确认的第二步）：确认收货 / 取消订单 / 仅退款。
 * 成功后**重新拉列表**——状态由域决定（`statusMallLabel` 也随之下发），前端不自造。
 *
 * ⚠ 三个动作合用一个提交函数：形状完全一致（无请求体、出参 `void`、成功后重拉），
 * `action` 只用来选调哪个接口与弹哪句 toast。
 * ⚠ 失败**照旧重拉**：状态可能已被别处推进（另一个页面同时操作、或定时任务刚把单关了），
 * 重拉让页面立刻回到与服务端一致的样子；中文 msg 由拦截器弹出。
 */
async function submit(order: OrderVO, action: OrderAction): Promise<void> {
  busyStart(order.orderNo)
  try {
    if (action === 'receive') await orderApi.receive(order.orderNo)
    else if (action === 'cancel') await orderApi.cancel(order.orderNo)
    else await orderApi.refund(order.orderNo)
    showToast(ACTION_DONE[action], 'success')
    await load(pageNum.value)
  } catch {
    // 拦截器已弹后端 msg（状态已变 / 下游故障都是可展示的中文）；重拉把页面拉回与服务端一致
    await load(pageNum.value)
  } finally {
    confirming.value = null
    busyEnd(order.orderNo)
  }
}

/* ---- 待支付倒计时与「超时后盯住关单」（见文件头 ⑦ ⑧） ---- */

/**
 * 秒级时钟：页面上只要还有「待支付**且**带截止时刻」的单就走（一张都没有就不该有定时器在跳）。
 */
const now = useNowTick(() =>
  orders.value.some((o) => o.status === ORDER_STATUS_PENDING_PAYMENT && !!o.expireTime)
)

/** 剩余毫秒：只对「待支付」且带截止时刻的那一笔有意义；其余一律 `null`（不倒计时、不判过期） */
function remainingOf(order: OrderVO): number | null {
  if (order.status !== ORDER_STATUS_PENDING_PAYMENT) return null
  return remainingMs(order.expireTime, now.value)
}

/** 卡上的倒计时文案；无截止时刻的老单 / 已过期 → 空串（模板据此不渲染那一格） */
function countdownOf(order: OrderVO): string {
  const ms = remainingOf(order)
  return ms === null || ms <= 0 ? '' : countdownText(ms)
}

/** 这一笔是否已过支付截止时刻（**到点即过期**，与域侧同一句闭区间） */
function isExpired(order: OrderVO): boolean {
  const ms = remainingOf(order)
  return ms !== null && ms <= 0
}

/** 已重拉的次数与上次时刻（节流用，见文件头 ⑧）；每次**顾客触发**的加载都会重置 */
let expiredPolls = 0
let lastExpiredPoll = 0

/**
 * 盯住服务端把超时单关掉：列表里只要还有「已过期但仍是待支付」的一笔，就按 `EXPIRED_POLL_MS`
 * 静默重拉一次，直到它变成「已取消」或轮询次数用尽。
 *
 * ⚠ 挂在走秒的 `now` 上而不是挂个新定时器：这个 tick 本来就只为待支付单在跑，
 * 超时那一刻它一定还活着（那一笔仍是待支付），够用且不引入第二个时钟。
 * ⚠ 节流 + 上限缺一不可：不节流就是每秒一个请求，不设上限就是下游故障时永不停止的请求源。
 */
watch(now, () => {
  if (expiredPolls >= EXPIRED_POLL_MAX) return
  // 用户触发的加载/提交在途时让路：轮询这次 `++seq` 会把它的响应作废，而 `loading` 就没人复位了
  if (loadingInFlight > 0) return
  if (!orders.value.some(isExpired)) return
  const t = now.value
  if (t - lastExpiredPoll < EXPIRED_POLL_MS) return
  lastExpiredPoll = t
  expiredPolls += 1
  void load(pageNum.value, true)
})

/* ---- 展示辅助 ---- */

/** 规格文案，如「颜色：曜石黑 / 容量：256G」（按域下发的对象键顺序） */
function specText(item: OrderVO['items'][number]): string {
  return Object.entries(item.specAttrs ?? {})
    .map(([k, v]) => `${k}：${v}`)
    .join(' / ')
}

/** 占位文字取商品名首字；名字都没有时兜一个「商」 */
function nameLabel(item: OrderVO['items'][number]): string {
  return item.goodsName?.trim().charAt(0) || '商'
}

/** 占位色相由 spuId 派生：同一商品每次渲染的占位色一致（与购物车 / 列表卡同一手法） */
function hue(spuId: number): number {
  return spuId % 360
}

function imgKey(order: OrderVO, skuId: number): string {
  return `${order.orderNo}:${skuId}`
}

/** 有图且没失败过才给出地址；空串即「无图」→ 模板回退渐变占位（⚠ 判无图用 falsy） */
function imgSrc(order: OrderVO, item: OrderVO['items'][number]): string {
  if (!item.mainImage || failedImgs.value.includes(imgKey(order, item.skuId))) return ''
  return item.mainImage
}

function markImgFailed(order: OrderVO, skuId: number): void {
  const key = imgKey(order, skuId)
  if (!failedImgs.value.includes(key)) failedImgs.value = [...failedImgs.value, key]
}

/** 「去支付」：进详情页并自动展开支付面板（支付要填金额，列表里做不了） */
function toPay(order: OrderVO): void {
  void router.push({ path: `/orders/${order.orderNo}`, query: { pay: '1' } })
}

onMounted(() => {
  void load(pageNum.value)
})

// 翻页（含浏览器前进 / 后退）统一在这里拉数据
watch(pageNum, (page) => {
  void load(page)
})
</script>

<template>
  <TopBar />

  <section class="order-page">
    <div class="container">
      <div class="order__head">
        <h1 class="order__title">我的订单</h1>
        <p v-if="!loading && !failed && total" class="order__desc">
          共 <b class="tnum">{{ total }}</b> 笔
        </p>
      </div>

      <p v-if="loading" class="order__state" role="status">正在加载订单…</p>

      <!-- 降级态：读不到整块给「暂不可用」+ 重试（形状照购物车页的那块） -->
      <div v-else-if="failed" class="order__fallback">
        <p class="order__fallback-text">订单暂不可用，请稍后重试</p>
        <button class="order__retry" type="button" @click="load(pageNum)">重试</button>
      </div>

      <!-- 空态：与上面的降级块**刻意不同形**（虚线框 / 实心引导块），别把两者合成一块 -->
      <div v-else-if="!orders.length" class="order__empty">
        <p class="order__empty-text">还没有订单</p>
        <p class="order__empty-hint">挑几件喜欢的商品下单吧</p>
        <!-- 「首页」是全站唯一落点（顶栏那一处），空态这里也是回首页，不另造一个去处 -->
        <router-link class="order__empty-link" to="/">去首页逛逛</router-link>
      </div>

      <template v-else>
        <ul class="order-list">
          <li v-for="o in orders" :key="o.orderNo" class="order-card">
            <!-- 整卡链接：铺满卡片的透明层（同列表页 .cat-card__hit），行内操作压在它上面 -->
            <router-link
              class="order-card__hit"
              :to="`/orders/${o.orderNo}`"
              :aria-label="`查看订单 ${o.orderNo}`"
            />

            <div class="order-card__head">
              <span class="order-card__no tnum">{{ o.orderNo }}</span>
              <span class="order-card__store">{{ o.storeName }}</span>
              <!-- 状态文案原样取域下发的，别按 status 自己拼一份 -->
              <span class="order-card__status">{{ o.statusMallLabel }}</span>
            </div>

            <div class="order-card__body">
              <ul class="order-card__thumbs">
                <li v-for="item in o.items.slice(0, MAX_THUMBS)" :key="item.skuId">
                  <div
                    class="order-card__thumb"
                    :style="{ background: grad(hue(item.spuId), 60, 91, 82) }"
                  >
                    <img
                      v-if="imgSrc(o, item)"
                      class="order-card__img"
                      :src="imgSrc(o, item)"
                      :alt="item.goodsName"
                      @error="markImgFailed(o, item.skuId)"
                    />
                    <span
                      v-else
                      class="order-card__ph"
                      :style="{ color: `hsl(${hue(item.spuId)} 42% 32%)` }"
                    >
                      {{ nameLabel(item) }}
                    </span>
                  </div>
                  <p class="order-card__goods clamp-2">{{ item.goodsName }}</p>
                  <p v-if="specText(item)" class="order-card__spec">{{ specText(item) }}</p>
                </li>
              </ul>

              <div class="order-card__sum">
                <p class="order-card__qty">共 <b class="tnum">{{ o.totalQuantity }}</b> 件</p>
                <p class="order-card__amount tnum">
                  <span class="order-card__sym">¥</span>{{ trimNum(o.totalAmount) }}
                </p>
              </div>
            </div>

            <div class="order-card__foot">
              <span class="order-card__time">{{ formatDateTime(o.createTime) }}</span>

              <!-- ⚠ 有快递单号才渲染这一行：未发货时它是 null，别印出「null」 -->
              <span v-if="o.shipNo" class="order-card__ship tnum">快递单号 {{ o.shipNo }}</span>

              <!-- 待支付倒计时（见文件头 ⑦）：截止时刻为 null 的老单没有这一格，
                   已过期则换成一句既成事实（它不再是催促，主色也收掉） -->
              <span v-if="countdownOf(o)" class="order-card__countdown">
                剩余 <b class="tnum">{{ countdownOf(o) }}</b>
              </span>
              <span v-else-if="isExpired(o)" class="order-card__countdown order-card__countdown--over">
                支付已超时
              </span>

              <div class="order-card__ops">
                <!-- ⚠ 两步确认这一支**必须排最前**：它下面那支的入口按钮（含「取消订单」）
                     就是把 `confirming` 写上的动作——若被它挡住，点下去只是改了状态、渲染不变，
                     表现是按钮完全没反应、请求永不发出。见文件头 ⑤ / ⑧。
                     文案按动作不同：共用一个「确认」会读不出后果（取消与退款不是同一件事） -->
                <template v-if="confirming?.orderNo === o.orderNo">
                  <span class="order-card__confirm">
                    {{
                      confirming.action === 'cancel'
                        ? '确认取消这一笔？'
                        : confirming.action === 'refund'
                          ? `确认退款 ¥${trimNum(o.totalAmount)}？`
                          : '确认收货？'
                    }}
                  </span>
                  <button
                    class="order-card__btn order-card__btn--main"
                    type="button"
                    :disabled="isBusy(o.orderNo)"
                    @click="submit(o, confirming.action)"
                  >
                    {{ isBusy(o.orderNo) ? '提交中…' : '确认' }}
                  </button>
                  <button
                    class="order-card__link"
                    type="button"
                    :disabled="isBusy(o.orderNo)"
                    @click="confirming = null"
                  >
                    再想想
                  </button>
                </template>

                <!-- 已过期：只留「取消订单」与「查看详情」——去支付已经没意义（域侧必拒） -->
                <template v-else-if="isExpired(o)">
                  <button
                    class="order-card__btn"
                    type="button"
                    :disabled="isBusy(o.orderNo)"
                    @click="confirming = { orderNo: o.orderNo, action: 'cancel' }"
                  >
                    取消订单
                  </button>
                  <router-link class="order-card__link" :to="`/orders/${o.orderNo}`">
                    查看详情
                  </router-link>
                </template>

                <template v-else>
                  <button
                    v-if="o.status === ORDER_STATUS_PENDING_PAYMENT"
                    class="order-card__btn order-card__btn--main"
                    type="button"
                    @click="toPay(o)"
                  >
                    去支付
                  </button>
                  <!-- 取消订单：仅待支付（域侧同一道闸门） -->
                  <button
                    v-if="o.status === ORDER_STATUS_PENDING_PAYMENT"
                    class="order-card__btn"
                    type="button"
                    @click="confirming = { orderNo: o.orderNo, action: 'cancel' }"
                  >
                    取消订单
                  </button>
                  <!-- 仅退款：仅「已支付、未发货」 -->
                  <button
                    v-else-if="o.status === ORDER_STATUS_PAID"
                    class="order-card__btn"
                    type="button"
                    @click="confirming = { orderNo: o.orderNo, action: 'refund' }"
                  >
                    仅退款
                  </button>
                  <button
                    v-else-if="o.status === ORDER_STATUS_SHIPPED"
                    class="order-card__btn"
                    type="button"
                    @click="confirming = { orderNo: o.orderNo, action: 'receive' }"
                  >
                    确认收货
                  </button>
                  <router-link class="order-card__link" :to="`/orders/${o.orderNo}`">
                    查看详情
                  </router-link>
                </template>
              </div>
            </div>
          </li>
        </ul>

        <Pager :total="total" :page-size="PAGE_SIZE" :page="pageNum" @change="changePage" />
      </template>
    </div>
  </section>

  <SiteFooter />
</template>
