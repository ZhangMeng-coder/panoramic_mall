/* ============================================================================
   订单 —— 与契约 docs/contracts/mall-bff.md 的 `/orders` 五行一致。
   字段定义以后端 `MallOrderVO` / `MallOrderCreateDTO` / `MallOrderPageQueryDTO` /
   `MallOrderPayDTO`（mall-bff 的 vo / dto 包）为准，这里只是前端侧的镜像。
   ========================================================================== */

/** 订单明细行（对应后端 `MallOrderVO.Item`） */
export interface OrderItem {
  /** 店铺商品 SKU id（store 域） */
  skuId: number
  /** 店铺商品 SPU id（store 域） */
  spuId: number
  /** 商品名（**下单当时的快照**） */
  goodsName: string
  /** 商品主图（下单当时的快照）。⚠ **空串也可能出现**，判无图请用 falsy（`!mainImage`） */
  mainImage: string | null
  /** 规格（规格名 → 取值）。⚠ 无规格时是**空对象不是 null** */
  specAttrs: Record<string, string>
  /** 单价（元，下单当时的快照） */
  unitPrice: number
  /** 数量 */
  quantity: number
  /** 小计（= 单价 × 数量，**下单当时算好落库的值**，不由页面乘） */
  subtotal: number
}

/** 收货地址快照（对应后端 `MallOrderVO.Address`）——下单当时的值，此后改 / 删地址都不影响已下的单 */
export interface OrderAddress {
  receiverName: string
  receiverPhone: string
  /** 省市区（自由文本单列）；**可空** */
  region: string | null
  detailAddress: string
}

/**
 * 订单（对应后端 `MallOrderVO`）—— 列表 / 详情的出参元素。
 *
 * ⚠ **本类型里没有「未发货」之外的快递态**：`shipNo` 未发货时是 `null`，
 * 页面据此决定「快递单号」那一行**渲不渲染**（不是显示 "null"）。
 */
export interface OrderVO {
  /** 业务可读单号 —— 详情跳转与两个写接口的路径标识都以它为准（不是自增 id） */
  orderNo: string
  /** 店铺名（下单当时的快照） */
  storeName: string
  /** 订单来源：`DIRECT` / `CART` */
  source: string
  /** 状态**枚举名**（如 `PENDING_PAYMENT` / `SHIPPED`）——只用于判「该显示哪个操作」 */
  status: string
  /**
   * 商城端（顾客）可读状态文案（如「已支付」）——**原样取域下发的**。
   * ⚠ 页面**不得**自己按 `status` 重写一份文案：两端各写一份必漂移
   */
  statusMallLabel: string
  /** 件数合计（各行 `quantity` 之和，不是行数） */
  totalQuantity: number
  /** 金额合计（元） */
  totalAmount: number
  /** 快递单号；**未发货为 `null`** */
  shipNo: string | null
  /** 下单时间（后端 `LocalDateTime`，ISO 串；展示走 `formatDateTime`） */
  createTime: string
  /** 收货地址快照；后端形态上可为 `null`（映射处显式判空），页面据此不渲染地址块 */
  address: OrderAddress | null
  /** 订单明细（至少一行；顺序 = 下单时的 `skuId` 升序） */
  items: OrderItem[]
}

/**
 * 订单状态枚举名里**本端要判行为**的两个（其余状态不需要分支）。
 *
 * ⚠ 这里判的是**枚举名**而不是 `statusMallLabel`：文案是给人读的措辞（领域可改、端侧还可能再翻），
 * 拿它当分支条件，改一个错别字就会静默改掉按钮；枚举名是契约里稳定的那一个。
 * ⚠ 但**展示**一律用域下发的 `statusMallLabel`，不要用这两个常量去写文案。
 */
export const ORDER_STATUS_PENDING_PAYMENT = 'PENDING_PAYMENT'
export const ORDER_STATUS_SHIPPED = 'SHIPPED'

/** 下单商品行（对应 `MallOrderCreateDTO.Item`）；同款多行由域内合并，前端不必先去重 */
export interface OrderCreateItem {
  skuId: number
  quantity: number
}

/**
 * 下单请求体（对应后端 `MallOrderCreateDTO`）。
 *
 * ⚠ **不含 `customerId`**：顾客身份只来自登录态（服务端取），页面传来的锚点等于把数据权限交出去。
 * ⚠ `cartItemIds` 是**购物车行 id**（不是 skuId），仅 `CART` 结算时带——服务端在**下单成功后**
 * 用它清车，页面**不要**自己再调一次删除接口。
 */
export interface OrderCreatePayload {
  source: 'DIRECT' | 'CART'
  /** 请求级幂等键：**每次确认下单生成一个**（`newRequestId()`） */
  requestId: string
  addressId: number
  items: OrderCreateItem[]
  cartItemIds?: number[]
}

/** 我的订单分页查询参数（对应 `MallOrderPageQueryDTO`）；两个筛选项不填即不筛 */
export interface OrderPageQuery {
  pageNum: number
  pageSize: number
  /** 订单号**精确**匹配 */
  orderNo?: string
  /** 状态枚举名（如 `PAID`） */
  status?: string
}

/** 支付请求体（对应 `MallOrderPayDTO`）—— `amount` 必须**等于订单总额**，比对在域内，不一致回 400 */
export interface OrderPayPayload {
  amount: number
}

/**
 * 改订单收货地址的请求体（对应 `MallOrderAddressUpdateDTO`）。
 *
 * ⚠ 与下单**同一个口径**：页面只给 `addressId`，地址内容由服务端取回并校验归属
 * （不属本人 → 404「地址不存在」，不区分「不存在」与「不是你的」）。
 * ⚠ 它改的是**这一笔订单的快照**，**不动顾客地址簿**（改完地址簿还是原样）。
 */
export interface OrderAddressUpdatePayload {
  addressId: number
}
