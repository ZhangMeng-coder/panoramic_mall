<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TopBar from '../../components/TopBar.vue'
import SiteFooter from '../../components/SiteFooter.vue'
import Pager from '../../components/Pager.vue'
import { orderApi } from '../../api/order'
import { showToast } from '../../composables/useToast'
import type { OrderVO } from '../../types/order'
import { ORDER_STATUS_PENDING_PAYMENT, ORDER_STATUS_SHIPPED } from '../../types/order'
import { formatDateTime, trimNum } from '../../utils/format'
import { grad } from '../../utils/gradient'

/**
 * 我的订单（`/orders`，**需登录态**——路由 meta.requiresAuth 拦，见 router/index.ts）。
 * 骨架同购物车页：顶栏 + 页脚 + 1280 容器，**不带万能搜索框**（它不是商品浏览页）。
 *
 * 六条口径（都不是随手写的）：
 * ① **分页状态住在 URL query 上**（`?page=`），URL 是唯一真相源——刷新 / 后退都不丢，
 *    与商品列表页同一手法。每页 10 条（订单列表不受「商品网格 7 列 / 49 条」那条约束）。
 * ② **状态文案只取 `statusMallLabel`**（域下发）；`status` 枚举名**只用来判**
 *    「该显示哪个操作」——拿文案当分支条件，改一个错别字就会静默改掉按钮。
 * ③ **金额与件数一律用服务端值**（`totalAmount` / `totalQuantity` / 行 `subtotal`），
 *    页面不自己乘加一遍（自己算就是第二份会漂移的金额口径）。
 * ④ **`shipNo` 为 null 时不渲染快递单号那一行**（别显示 "null"）。
 * ⑤ **破坏性动作走行内两步确认**（本端没有模态层，也不用 `window.confirm`）：确认收货就地
 *    变成「确认收货？/ 确认收货 / 取消」，同时只留一处确认。
 * ⑥ **整卡可点进详情**（`.order-card__hit` 铺满卡片的透明链接，同列表页 `.cat-card__hit`），
 *    行内操作按钮压在它**上面**（z-index）——否则点「确认收货」会顺带跳走。
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

const orders = ref<OrderVO[]>([])
const total = ref(0)
const loading = ref(false)
const failed = ref(false)

/** 正在确认收货的那一笔（同一时刻至多一笔） */
const confirmingNo = ref<string | null>(null)
/** 确认收货在途的单号集合（防连点：两笔单各有一个请求在途是正常场景，故用集合而非单一 id） */
const busyNos = ref<string[]>([])

/** 图片加载失败的行（回退 CSS 渐变占位）；键是 `单号:skuId`，稳定且行内唯一 */
const failedImgs = ref<string[]>([])

/** 并发序号：连着翻页时，后发的请求作废先发的响应（与商品列表页同一手法） */
let seq = 0

async function load(page: number): Promise<void> {
  const current = ++seq
  loading.value = true
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
    // 拦截器已弹后端 msg（下游故障时是「订单暂不可用，请稍后重试」）；列表是主内容，拿不到就整块降级
    orders.value = []
    total.value = 0
    failed.value = true
  } finally {
    if (current === seq && !clamping) loading.value = false
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
  void router.push({ query: { page: String(page) } })
}

/**
 * 确认收货（行内两步确认的第二步）。
 * 成功后**重新拉列表**——状态由域决定（`statusMallLabel` 也随之下发），前端不自造。
 */
async function receive(order: OrderVO): Promise<void> {
  busyStart(order.orderNo)
  try {
    await orderApi.receive(order.orderNo)
    showToast('已确认收货', 'success')
    await load(pageNum.value)
  } catch {
    // 拦截器已弹后端 msg（状态已变 / 下游故障都是可展示的中文）；重拉把页面拉回与服务端一致
    await load(pageNum.value)
  } finally {
    confirmingNo.value = null
    busyEnd(order.orderNo)
  }
}

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

              <div class="order-card__ops">
                <!-- 行内两步确认（见文件头 ⑤）；确认按钮带在途文案 -->
                <template v-if="confirmingNo === o.orderNo">
                  <span class="order-card__confirm">确认收货？</span>
                  <button
                    class="order-card__btn order-card__btn--main"
                    type="button"
                    :disabled="isBusy(o.orderNo)"
                    @click="receive(o)"
                  >
                    {{ isBusy(o.orderNo) ? '提交中…' : '确认收货' }}
                  </button>
                  <button
                    class="order-card__link"
                    type="button"
                    :disabled="isBusy(o.orderNo)"
                    @click="confirmingNo = null"
                  >
                    取消
                  </button>
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
                  <button
                    v-else-if="o.status === ORDER_STATUS_SHIPPED"
                    class="order-card__btn"
                    type="button"
                    @click="confirmingNo = o.orderNo"
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
