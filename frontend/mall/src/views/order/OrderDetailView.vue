<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import TopBar from '../../components/TopBar.vue'
import SiteFooter from '../../components/SiteFooter.vue'
import AddressPicker from '../../components/AddressPicker.vue'
import ModalShell from '../../components/ModalShell.vue'
import { orderApi } from '../../api/order'
import { showToast } from '../../composables/useToast'
import type { OrderVO } from '../../types/order'
import { ORDER_STATUS_PENDING_PAYMENT, ORDER_STATUS_SHIPPED } from '../../types/order'
import { formatDateTime, trimNum } from '../../utils/format'
import { grad } from '../../utils/gradient'

/**
 * 订单详情（`/orders/:orderNo`，**需登录态**——路由 meta.requiresAuth 拦）。
 * 骨架同商品详情页：顶栏 + 页脚 + 1280 容器 + 面包屑；**不带万能搜索框**（它不是商品浏览页）。
 *
 * 八条口径（都不是随手写的）：
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

/** 并发序号：连点两笔单时，后发的请求作废先发的响应（与列表页同一手法） */
let seq = 0

/**
 * 展开支付面板：金额**预填订单总额**——它必须与总额相等才算成功（域内比对），
 * 预填只是省去顾客手打一遍，不是把校验搬到前端（顾客照样可以改，改错了由域回 400）。
 */
function openPay(): void {
  const o = order.value
  if (!o || o.status !== ORDER_STATUS_PENDING_PAYMENT) return
  payAmount.value = trimNum(o.totalAmount)
  payOpen.value = true
}

/** 列表页「去支付」带 `?pay=1` 进来：详情到位后自动展开支付面板（状态不对则不开） */
function maybeAutoOpenPay(o: OrderVO): void {
  if (route.query.pay !== '1') return
  if (o.status !== ORDER_STATUS_PENDING_PAYMENT) return
  openPay()
}

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

/* ---- 改收货地址（见文件头 ⑧） ---- */

/**
 * 能不能改收货地址：**仅待支付**。域内是同一道闸门（非待支付回 400），这里判一次只为
 * **不摆出一个注定失败的入口**——判据与按钮渲染同源，不各写一遍。
 */
const canChangeAddress = computed(() => order.value?.status === ORDER_STATUS_PENDING_PAYMENT)

/** 开改地址弹窗（地址块旁与支付面板内两个入口共用） */
function openAddress(): void {
  if (!canChangeAddress.value || addressSaving.value) return
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

/** 换单号（同页复用组件实例）即重拉，并把上一个单的面板态清干净 */
watch(
  orderNo,
  (no) => {
    payOpen.value = false
    payAmount.value = ''
    confirmingReceive.value = false
    addressOpen.value = false
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
                v-if="canChangeAddress"
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

        <!-- 操作区：按状态显示（判的是**枚举名**，见文件头 ②） -->
        <div v-if="order.status === ORDER_STATUS_PENDING_PAYMENT || order.status === ORDER_STATUS_SHIPPED" class="order-detail__ops">
          <template v-if="order.status === ORDER_STATUS_PENDING_PAYMENT">
            <button
              class="order-detail__btn"
              type="button"
              :disabled="payOpen || paying"
              @click="openPay"
            >
              去支付
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
              <p class="field__hint">需与订单总额一致才能支付成功</p>
            </div>

            <!-- 支付前发现寄错地址：就地改（仅待支付才渲染，见文件头 ⑧）。
                 ⚠ 它与上面的「取消」不是一回事：取消只是收起支付面板，这里改的是那一单的快照 -->
            <p v-if="canChangeAddress" class="order-pay__addr">
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
              <button class="order-pay__submit" type="submit" :disabled="paying">
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
