<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import TopBar from '../components/TopBar.vue'
import SiteFooter from '../components/SiteFooter.vue'
import { cartApi } from '../api/cart'
import { refresh as refreshBadge } from '../store/cart'
import { showToast } from '../composables/useToast'
import type { CartItem, CartResult } from '../types/cart'
import { grad } from '../utils/gradient'
import { trimNum } from '../utils/format'

/**
 * 购物车页（`/cart`，**需登录态**——路由 meta.requiresAuth 拦，见 router/index.ts）。
 * 骨架与账号页同款：顶栏 + 页脚 + 1280 容器；**不带万能搜索框**——它不是商品浏览页
 * （搜索框是首页 ② 区块，列表 / 详情两个浏览页才有）。
 *
 * 七条口径（都不是随手写的，前六条直接对应契约 mall-bff.md「形状与行为口径」）：
 * ① **汇总一律用服务端值**：`selectedQuantity` / `selectedAmount` 由服务端算好，
 *    前端**不自己乘加**（自己算就是第二份会漂移的金额口径）。行小计是例外——它是
 *    **单行展示**（单价 × 数量），契约里没有这个字段，只能前端算。
 * ② **参数化页面的写法选「服务端为准 + 重拉」**（另一条路是本地乐观更新）：
 *    改数量 / 改选中 / 删除 / 清空成功后**重拉整份购物车**（`load(false)`，不闪加载态），
 *    与收货地址页同一手法。好处是勾选态与汇总结算永远同源、无需自己实现「服务端行为」；
 *    代价是点击到画面更新要等一个来回，故期间**行内控件禁用并在按钮上显示进行中文案**。
 *    ⚠ 这条路**没有本地乐观态可回滚**：请求失败时拦截器已弹后端 msg，行内控件在
 *    `finally` 里复位，并**重拉一次**把页面拉回与服务端一致（失败多半意味着服务端状态
 *    与页面不一致，这正是最需要重拉的时刻）。
 * ③ **失效行不从列表里删掉**（顾客要看得见才敢删它）：整行灰显 + 「已失效」标记，
 *    勾选框禁用且**不显示勾选**、数量与小计显示 `—`、只保留「删除」。
 *    ⚠ 服务端可能仍把它标着 `selected=true`（全选是**整表**操作，含失效行），页面故意不显示——
 *    显示成勾选会让人以为它会算进金额；同理「删除选中」只删**页面上勾得到**的行（有效行），
 *    不顺手把看不见的失效行一起删掉（那是惊喜式删除）。
 * ④ **全选勾选态按有效行推导**（全是失效行时不可点），点击走 `PUT /cart/selected`
 *    整表接口——**不在前端拆成逐行改**（那是 N 次请求）。
 * ⑤ **数量步进器**：`+` 在 `!purchasable` 时禁用（`purchasable` 由服务端给：
 *    invalid 或 `quantity >= availableStock`）；`-` 到 1 时**禁用而不是转成删除**——
 *    删除是独立动作（还要两步确认），把「减一格」和「删掉这一行」绑在一起，误触代价不对等。
 *    另外 `+` 还夹了一道**前端镜像的 999 上限**（域侧单行上限，超限必然 400），
 *    免得点出一个注定失败的请求。
 * ⑥ **不摆「立即购买」，「去结算」是禁用按钮** + 一行「结算功能开发中」：后端没有下单 /
 *    结算接口，摆一个点了没反应的按钮比不摆更糟。
 * ⑦ **破坏性动作走行内两步确认**（本端没有模态层，也不用 `window.confirm`，同收货地址页）：
 *    行删除就地变成「确认删除？/ 确认删除 / 取消」；「删除选中」「清空购物车」共用一处
 *    待确认态（`pendingWipe`），同时只留一处确认，不并列弹两块。
 */

/** 单行数量上限（域侧口径的前端镜像，见文件头 ⑤）；只在 `+` 的禁用判据里用 */
const MAX_QUANTITY = 999

const cart = ref<CartResult | null>(null)
const loading = ref(false)
const loadFailed = ref(false)

/** 行内写操作进行中的行 id 集合（防连点；与收货地址页同一手法，用集合而非单一 id） */
const busyIds = ref<number[]>([])
/** 整批写操作（全选 / 删除选中 / 清空）进行中 —— 期间全表操作一起禁用 */
const busyAll = ref(false)

/** 正在确认删除的那一行（同一时刻至多一行） */
const confirmingId = ref<number | null>(null)
/** 待确认的整批动作：`'selected'` = 删除选中、`'all'` = 清空购物车；null = 没有待确认的 */
const pendingWipe = ref<'selected' | 'all' | null>(null)

/** 图片加载失败的行 id（回退 CSS 渐变占位；同一行只记一次，避免每次重渲染重试) */
const failedImgs = ref<number[]>([])

const shops = computed(() => cart.value?.shops ?? [])

/** 行数（不是件数）：用于空态判据与页头「共 N 项」。件数之和由服务端给（`totalQuantity`） */
const rowCount = computed(() => shops.value.reduce((n, s) => n + s.items.length, 0))

/** 有效行（服务端判定；页面不重判一遍可见性） */
const validItems = computed(() => shops.value.flatMap((s) => s.items.filter((i) => !i.invalid)))

/** 全选勾选态：按**有效行**推导（见文件头 ④）。没有有效行时不是「全选」 */
const allSelected = computed(
  () => validItems.value.length > 0 && validItems.value.every((i) => i.selected)
)

/** 页面上勾得到的行 id（「删除选中」的作用域，见文件头 ③） */
const selectedVisibleIds = computed(() => validItems.value.filter((i) => i.selected).map((i) => i.id))

/** 汇总：全部取服务端算好的值（见文件头 ①） */
const selectedQuantity = computed(() => cart.value?.selectedQuantity ?? 0)
const selectedAmount = computed(() => cart.value?.selectedAmount ?? 0)
const invalidCount = computed(() => cart.value?.invalidCount ?? 0)

/**
 * 拉列表。`hard = true`（首次进入 / 降级重试）显示加载态；写操作之后的刷新走 `hard = false`，
 * 列表就地换新、不闪一层「正在加载」。
 *
 * ⚠ **普通调用**，不传 `silent401`（那是路由守卫刷新重建用户态专用的）：会话真没了就走
 * 401 出口（清态 + 提示 + 跳登录页），而不是静默留在页面上。
 */
async function load(hard = true): Promise<void> {
  if (hard) loading.value = true
  try {
    cart.value = await cartApi.list()
    loadFailed.value = false
  } catch {
    // 拦截器已弹后端 msg（下游故障时是「购物车暂不可用，请稍后重试」）
    loadFailed.value = true
  } finally {
    if (hard) loading.value = false
  }
}

onMounted(() => {
  void load()
})

function isBusy(id: number): boolean {
  return busyIds.value.includes(id)
}

function busyStart(id: number): void {
  if (!busyIds.value.includes(id)) busyIds.value = [...busyIds.value, id]
}

function busyEnd(id: number): void {
  busyIds.value = busyIds.value.filter((v) => v !== id)
}

/**
 * 把勾选框拨回**服务端值**。
 *
 * ⚠ 为什么需要它：`:checked` 是**单向绑定**，Vue 只在「绑定的值变了」时才重写 DOM 属性。
 * 于是用户点出来的那一下会留在框上——写请求失败（或根本没发出去）时，页面就会显示一个
 * 服务端并不同意的新状态，且**重拉也不会纠正它**（重拉回来的值与绑定值相同，没有 patch）。
 * 故失败路径必须自己把框拨回来。传 `oldValue`（本次点击前的值）即可：写失败 ⇒ 服务端值没变。
 */
function restoreCheck(target: EventTarget | null, oldValue: boolean): void {
  if (target instanceof HTMLInputElement) target.checked = oldValue
}

/* ---- 展示辅助 ---- */

/** 规格文案，如「颜色：曜石黑 / 容量：256G」（按存储顺序，见 types/cart.ts 的 specAttrs 注释） */
function specText(item: CartItem): string {
  return (item.specAttrs ?? []).map((a) => `${a.spec}：${a.value}`).join(' / ')
}

/** 占位文字取商品名首字；名字都没有（SPU 已删）时兜一个「商」 */
function nameLabel(item: CartItem): string {
  return item.name?.trim().charAt(0) || '商'
}

/** 占位色相由 spuId 派生：同一商品每次渲染的占位色一致（与列表卡同一手法） */
function hue(item: CartItem): number {
  return item.spuId % 360
}

/** 有图且没失败过才给出地址；空串即「无图」→ 模板回退渐变占位 */
function imgSrc(item: CartItem): string {
  if (!item.mainImage || failedImgs.value.includes(item.id)) return ''
  return item.mainImage
}

function markImgFailed(id: number): void {
  if (!failedImgs.value.includes(id)) failedImgs.value = [...failedImgs.value, id]
}

/** 行小计（单价 × 数量）—— 唯一的本地乘法，只用于这一行的展示，见文件头 ① */
function subtotal(item: CartItem): number {
  return (item.price ?? 0) * item.quantity
}

/* ---- 写操作（成功后一律重拉，见文件头 ②）---- */

/** 改单行选中态（`selected` 是目标值；失效行不参与，模板已禁用） */
async function toggleSelect(item: CartItem, event: Event): Promise<void> {
  const box = event.target
  if (item.invalid || isBusy(item.id) || busyAll.value) {
    restoreCheck(box, item.selected)
    return
  }
  busyStart(item.id)
  try {
    await cartApi.updateSelected(item.id, !item.selected)
    await load(false)
  } catch {
    // 拦截器已弹后端 msg（下游故障时是「购物车暂不可用，请稍后重试」）；
    // 重拉一次把页面拉回与服务端一致 + 把勾选框拨回去（见 restoreCheck）
    await load(false)
    restoreCheck(box, item.selected)
  } finally {
    busyEnd(item.id)
  }
}

/** 全选 / 全不选：整表接口（含失效行），勾选态由 `allSelected` 推导出目标值 */
async function toggleAll(event: Event): Promise<void> {
  const box = event.target
  if (busyAll.value) {
    restoreCheck(box, allSelected.value)
    return
  }
  busyAll.value = true
  try {
    await cartApi.selectAll(!allSelected.value)
    await load(false)
  } catch {
    // 拦截器已弹后端 msg；同样要重拉 + 拨回勾选框
    await load(false)
    restoreCheck(box, allSelected.value)
  } finally {
    busyAll.value = false
  }
}

/** `-` 能不能点：到 1 即止（不转成删除，见文件头 ⑤） */
function canDecrease(item: CartItem): boolean {
  return !item.invalid && item.quantity > 1 && !isBusy(item.id) && !busyAll.value
}

/** `+` 能不能点：服务端的 `purchasable` + 前端镜像的 999 上限（见文件头 ⑤） */
function canIncrease(item: CartItem): boolean {
  return (
    !item.invalid &&
    item.purchasable &&
    item.quantity < MAX_QUANTITY &&
    !isBusy(item.id) &&
    !busyAll.value
  )
}

/** 步进：`delta` 只可能是 ±1，边界由 `canDecrease` / `canIncrease` 把住（这里再兜一层） */
async function step(item: CartItem, delta: number): Promise<void> {
  const next = item.quantity + delta
  if (next < 1 || next > MAX_QUANTITY) return
  if (delta > 0 ? !canIncrease(item) : !canDecrease(item)) return

  // ⚠ 改数量**不动徽标**：徽标口径是**行数**（`GET /cart/count`），数量变了行数没变——顺手
  // 刷一次只是白费一个请求（真会改变行数的是下面的「删除」与「清空」，那两处才刷）
  busyStart(item.id)
  try {
    await cartApi.updateQuantity(item.id, next)
    await load(false)
  } catch {
    // 拦截器已弹后端 msg（超上限 / 商品已不可买都是 400）
  } finally {
    busyEnd(item.id)
  }
}

/** 行删除的两步确认入口（同时收起整批确认态，见文件头 ⑦） */
function askRemove(id: number): void {
  pendingWipe.value = null
  confirmingId.value = id
}

/**
 * 删一行。⚠ 购物车**没有单行删除端点**，删一行也走批量端点（`ids: [id]`）。
 * 成功后顺手刷徽标：徽标口径是**行数**，与页面汇总口径不同（见 store/cart.ts）。
 */
async function removeItem(item: CartItem): Promise<void> {
  busyStart(item.id)
  try {
    await cartApi.removeItems([item.id])
    await load(false)
    void refreshBadge()
    showToast('已从购物车删除', 'success')
  } catch {
    // 拦截器已弹后端 msg。单行命中 0 行是幂等 no-op（契约口径），故「双击删除」不会报错；
    // 真失败时重拉一次，把页面拉回与服务端一致
    await load(false)
  } finally {
    confirmingId.value = null
    busyEnd(item.id)
  }
}

/** 整批破坏性动作的两步确认入口 */
function askWipe(scope: 'selected' | 'all'): void {
  confirmingId.value = null
  pendingWipe.value = scope
}

/**
 * 整批删除：`'selected'` = 删页面上勾选得到的有效行；`'all'` = 清空整个购物车。
 * 两者共用一个待确认态与一个 busy 标记，请求体形状不同（批量 id 数组 vs 空体 DELETE）。
 */
async function wipe(scope: 'selected' | 'all'): Promise<void> {
  const ids = selectedVisibleIds.value
  if (scope === 'selected' ? !ids.length : !rowCount.value) return

  busyAll.value = true
  try {
    if (scope === 'all') {
      await cartApi.clear()
    } else {
      await cartApi.removeItems(ids)
    }
    await load(false)
    void refreshBadge()
    showToast(scope === 'all' ? '购物车已清空' : `已删除 ${ids.length} 项商品`, 'success')
  } catch {
    // 拦截器已弹后端 msg；重拉把页面拉回与服务端一致
    await load(false)
  } finally {
    busyAll.value = false
    pendingWipe.value = null
  }
}
</script>

<template>
  <TopBar />

  <section class="cart">
    <div class="container">
      <div class="cart__head">
        <h1 class="cart__title">购物车</h1>
        <p v-if="rowCount" class="cart__desc">
          共 <b class="tnum">{{ rowCount }}</b> 项
          <template v-if="invalidCount">
            ，其中 <b class="tnum">{{ invalidCount }}</b> 项已失效（不计入合计）
          </template>
        </p>
      </div>

      <p v-if="loading" class="cart__state" role="status">正在加载购物车…</p>

      <!-- 降级态：读不到整块给「暂不可用」+ 重试，形状照收货地址页的那块 -->
      <div v-else-if="loadFailed" class="cart__fallback">
        <p class="cart__fallback-text">购物车暂不可用，请稍后重试</p>
        <button class="cart__retry" type="button" @click="load()">重试</button>
      </div>

      <!-- 空态：与上面的降级块**刻意不同形**（虚线框 / 无底 / 引导回首页），别把两者合成一块 -->
      <div v-else-if="!rowCount" class="cart__empty">
        <p class="cart__empty-text">购物车还是空的</p>
        <p class="cart__empty-hint">挑几件喜欢的商品放进来吧</p>
        <!-- 「首页」是全站唯一落点（顶栏那一处），空态这里也是回首页，不另造一个去处 -->
        <router-link class="cart__empty-link" to="/">去首页逛逛</router-link>
      </div>

      <template v-else>
        <div class="cart__panel">
          <!-- 顶部操作条：全选（勾选态按有效行推导）+ 删除选中 / 清空（各走行内两步确认） -->
          <div class="cart__ops">
            <label class="cart-check">
              <input
                type="checkbox"
                :checked="allSelected"
                :disabled="busyAll || !validItems.length"
                @change="toggleAll"
              />
              <span>全选</span>
            </label>

            <div class="cart__ops-right">
              <template v-if="pendingWipe === 'selected'">
                <span class="cart__confirm">确认删除选中的 {{ selectedVisibleIds.length }} 项？</span>
                <button
                  class="cart__link cart__link--danger"
                  type="button"
                  :disabled="busyAll"
                  @click="wipe('selected')"
                >
                  {{ busyAll ? '删除中…' : '确认删除' }}
                </button>
                <button class="cart__link" type="button" @click="pendingWipe = null">取消</button>
              </template>

              <template v-else-if="pendingWipe === 'all'">
                <span class="cart__confirm">确认清空购物车？</span>
                <button
                  class="cart__link cart__link--danger"
                  type="button"
                  :disabled="busyAll"
                  @click="wipe('all')"
                >
                  {{ busyAll ? '清空中…' : '确认清空' }}
                </button>
                <button class="cart__link" type="button" @click="pendingWipe = null">取消</button>
              </template>

              <template v-else>
                <button
                  class="cart__link"
                  type="button"
                  :disabled="!selectedVisibleIds.length || busyAll"
                  @click="askWipe('selected')"
                >
                  删除选中
                </button>
                <button class="cart__link" type="button" :disabled="busyAll" @click="askWipe('all')">
                  清空购物车
                </button>
              </template>
            </div>
          </div>

          <!-- 按店铺分组（`shopId === null` 的那组是「商品已删、无法归属店铺」的兜底组，同样渲染） -->
          <section v-for="shop in shops" :key="shop.shopId ?? 0" class="cart-shop">
            <h2 class="cart-shop__name">{{ shop.shopName }}</h2>

            <ul>
              <li
                v-for="item in shop.items"
                :key="item.id"
                class="cart-item"
                :class="{ 'is-invalid': item.invalid }"
              >
                <!-- 勾选框：失效行**禁用且不显示勾选**（服务端可能仍标着 selected，见文件头 ③） -->
                <input
                  class="cart-item__check"
                  type="checkbox"
                  :checked="!item.invalid && item.selected"
                  :disabled="item.invalid || isBusy(item.id) || busyAll"
                  :aria-label="`选择：${item.name ?? '该商品已删除'}`"
                  @change="toggleSelect(item, $event)"
                />

                <!-- 图位：有图用图（后端 URL），空 / 加载失败回退 CSS 渐变 + 名称首字 -->
                <div class="cart-item__thumb" :style="{ background: grad(hue(item), 60, 91, 82) }">
                  <img
                    v-if="imgSrc(item)"
                    class="cart-item__img"
                    :src="imgSrc(item)"
                    :alt="item.name ?? ''"
                    @error="markImgFailed(item.id)"
                  />
                  <span v-else class="cart-item__ph" :style="{ color: `hsl(${hue(item)} 42% 32%)` }">
                    {{ nameLabel(item) }}
                  </span>
                </div>

                <div class="cart-item__info">
                  <p class="cart-item__name clamp-2">
                    <!-- 有效行才给回详情页的链接：失效行点过去只会吃一个「商品不存在或已下架」 -->
                    <router-link
                      v-if="!item.invalid && item.name"
                      class="cart-item__link"
                      :to="`/goods/${item.spuId}`"
                    >
                      {{ item.name }}
                    </router-link>
                    <template v-else>{{ item.name ?? '该商品已删除' }}</template>
                    <span v-if="item.invalid" class="cart-item__tag">已失效</span>
                  </p>
                  <p v-if="specText(item)" class="cart-item__spec">{{ specText(item) }}</p>
                </div>

                <div class="cart-item__price tnum">
                  <template v-if="item.price !== null">¥{{ trimNum(item.price) }}</template>
                  <template v-else>—</template>
                </div>

                <!-- 数量：失效行只留一个「—」（不做步进器，它本来就不能买） -->
                <div v-if="item.invalid" class="cart-item__na">—</div>
                <div v-else class="cart-qty">
                  <button
                    class="cart-qty__btn"
                    type="button"
                    :disabled="!canDecrease(item)"
                    aria-label="减少数量"
                    @click="step(item, -1)"
                  >
                    −
                  </button>
                  <span class="cart-qty__num tnum" aria-live="polite">{{ item.quantity }}</span>
                  <button
                    class="cart-qty__btn"
                    type="button"
                    :disabled="!canIncrease(item)"
                    aria-label="增加数量"
                    @click="step(item, 1)"
                  >
                    +
                  </button>
                </div>

                <div class="cart-item__sub tnum">
                  <template v-if="item.invalid || item.price === null">—</template>
                  <template v-else>¥{{ trimNum(subtotal(item)) }}</template>
                </div>

                <!-- 操作：删除是行内两步确认（沿用收货地址页的手法，本端没有模态层） -->
                <div class="cart-item__ops">
                  <template v-if="confirmingId === item.id">
                    <span class="cart__confirm">确认删除？</span>
                    <button
                      class="cart__link cart__link--danger"
                      type="button"
                      :disabled="isBusy(item.id)"
                      @click="removeItem(item)"
                    >
                      {{ isBusy(item.id) ? '删除中…' : '确认删除' }}
                    </button>
                    <button class="cart__link" type="button" @click="confirmingId = null">
                      取消
                    </button>
                  </template>
                  <button v-else class="cart__link" type="button" @click="askRemove(item.id)">
                    删除
                  </button>
                </div>
              </li>
            </ul>
          </section>
        </div>

        <!-- 底部汇总栏：件数与金额都取服务端值（文件头 ①）；吸底，长列表滚动时也看得见。
             ⚠ 它已在 .container（1280）里面，不要再套一层 container —— 那是 1280 套 1280 的自我重复 -->
        <div class="cart-sum">
          <div class="cart-sum__inner">
            <span class="cart-sum__lead">已选 <b class="tnum">{{ selectedQuantity }}</b> 件</span>

            <div class="cart-sum__right">
              <span class="cart-sum__label">合计</span>
              <span class="cart-sum__amount tnum">
                <span class="cart-sum__sym">¥</span>{{ trimNum(selectedAmount) }}
              </span>
              <!-- 「去结算」**恒禁用** + 一行说明：后端没有下单 / 结算接口（文件头 ⑥），
                   别改成可点，也别在它旁边再摆一个「立即购买」 -->
              <button class="cart-sum__btn" type="button" disabled>去结算</button>
              <span class="cart-sum__hint">结算功能开发中</span>
            </div>
          </div>
        </div>
      </template>
    </div>
  </section>

  <SiteFooter />
</template>
