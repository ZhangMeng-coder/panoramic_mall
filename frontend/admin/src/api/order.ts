import request from './request'
import type { PageQuery, PageResult } from '../types/api'

/**
 * 收货地址快照，与 trade 域的 `TradeOrderAddressDTO` 同构（下单当时的值，此后顾客改地址不影响已下的单）。
 * ⚠ 详细地址字段名是 `detail`，不是 `detailAddress`。
 */
export interface TradeOrderAddress {
  receiverName: string
  receiverPhone: string
  /** 省市区（由 mall-bff 拼好传来） */
  region: string
  /** 详细地址 */
  detail: string
}

/** 订单明细行，与 trade 域 `TradeOrderVO.Item` 同构（下单当时的快照，金额不由页面计算） */
export interface TradeOrderItem {
  skuId: number
  spuId: number
  goodsName: string
  /** 主图快照；无图时可能是 null **也可能是空串**，判无图请用 falsy */
  mainImage: string | null
  /** 规格名 → 取值；无规格为**空表**（不是 null） */
  specAttrs: Record<string, string>
  unitPrice: number
  quantity: number
  subtotal: number
}

/**
 * 订单详情，与 trade 域 `TradeOrderVO` 同构（admin BFF 直接下发域契约类型，本层不另造 VO）。
 *
 * ⚠ 状态文案取 `statusStoreAdminLabel`（商户端 / 管理端视角），**不要**用 `statusMallLabel`
 * （那是顾客端视角，同一枚举两套词：`PAID` 顾客看到「已支付」、店主看到「待发货」）；前端不得自造文案。
 * ⚠ `customerId` 是域 VO 的透传字段，本端展示用不上，不渲染。
 */
export interface TradeOrderVO {
  /** 业务可读单号（一切订单操作都以它标识） */
  orderNo: string
  /** 下单顾客 id（= mall_user.id），本端不展示 */
  customerId: number
  storeId: number
  /** 店铺名（下单当时的快照） */
  storeName: string
  /** 订单来源：DIRECT / CART */
  source: string
  /** 状态枚举名，如 PAID */
  status: string
  /** 商城端（顾客）文案 */
  statusMallLabel: string
  /** 商户端 / 管理端文案（本端展示这个） */
  statusStoreAdminLabel: string
  /** 件数合计（各行 quantity 之和） */
  totalQuantity: number
  /** 金额合计（元） */
  totalAmount: number
  /** 快递单号；未发货为 null */
  shipNo: string | null
  createTime: string
  address: TradeOrderAddress
  items: TradeOrderItem[]
}

/**
 * 订单分页查询参数，与 admin BFF 的 `OrderPageQueryDTO` 同构。
 *
 * ⚠ `storeId` / `customerId` 在管理端是**平台筛选条件**（在全量里过滤「某一店 / 某一顾客」的单），
 * 不是数据作用域——平台侧没有锚点、管理员登录 id 既不是店铺 id 也不是顾客 id，故它们原样来自页面。
 * 本端页面当前只摆订单号与状态两个输入框，这两个字段保留在契约里（cross-cutting 第 22 条）。
 */
export interface OrderPageQuery extends PageQuery {
  /** 订单号**精确**匹配；null = 不筛 */
  orderNo: string | null
  /** 状态**枚举名**（如 PAID）；null = 不筛。取值非法由域侧回 400 */
  status: string | null
  /** 店铺筛选（= 店主账号 id）；null = 全部店铺 */
  storeId: number | null
  /** 顾客筛选（= mall_user.id）；null = 全部顾客 */
  customerId: number | null
}

/**
 * 订单 API（平台侧只读；admin BFF 编排，经网关 /admin/** 前缀转发，trade-center 已下沉为纯域）。
 *
 * ⚠ 两条都挂 `trade:order:list`（后端 `@PreAuthorize` 把关），页面上的按钮同挂该串。
 * ⚠ 管理端对订单**只读**：没有改状态 / 改单 / 删单的端点，别照别处补写动作。
 */
export const orderApi = {
  /** 订单分页查询（订单号精确 / 状态枚举名 / 店铺 / 顾客） */
  page(params: OrderPageQuery) {
    return request.get<PageResult<TradeOrderVO>>('/admin/orders/page', { params })
  },

  /** 订单详情（路径标识用 orderNo，不是自增 id） */
  detail(orderNo: string) {
    return request.get<TradeOrderVO>(`/admin/orders/${orderNo}`)
  }
}
