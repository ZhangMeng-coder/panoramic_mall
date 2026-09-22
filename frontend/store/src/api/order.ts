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
 * 订单详情，与 trade 域 `TradeOrderVO` 同构（store-bff 直接下发域契约类型，本层不另造 VO）。
 *
 * ⚠ 状态文案取 `statusStoreAdminLabel`（商户端视角），**不要**用 `statusMallLabel`（那是顾客端视角，
 * 同一枚举两套词：`PAID` 顾客看到「已支付」、店主看到「待发货」）；前端不得自造文案。
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
 * 本店订单分页查询参数，与 store-bff 的 `StoreOrderPageQueryDTO` 同构。
 * ⚠ **没有 `storeId`**：作用域由 store-bff 按登录店主（`type=store` 的登录 id）无条件写入域入参，
 * 页面无权选择看哪家店（cross-cutting 第 22 条）。
 */
export interface OrderPageQuery extends PageQuery {
  /** 订单号**精确**匹配；null = 不筛 */
  orderNo: string | null
  /** 状态**枚举名**（如 PAID）；null = 不筛。取值非法由域侧回 400 */
  status: string | null
}

/** 发货请求体，与 store-bff 的 `StoreOrderShipDTO` 同构 */
export interface ShipPayload {
  trackingNo: string
}

/**
 * 订单 API（经网关 `/store/orders/**` 转发到 store-bff）。
 * ⚠ 本端不接 RBAC，故没有权限串可挂（别照 admin 端补 `v-perm`）。
 */
export const orderApi = {
  /** 本店订单分页（订单号精确 + 状态筛选；全状态可见，含「待支付」） */
  page(params: OrderPageQuery) {
    return request.get<PageResult<TradeOrderVO>>('/store/orders/page', { params })
  },

  /** 本店订单详情（不属本店的单后端回 404，拦截器提示） */
  detail(orderNo: string) {
    return request.get<TradeOrderVO>(`/store/orders/${orderNo}`)
  },

  /** 发货（记录快递单号；重复发货 / 跳级由域内状态机拒，400 由拦截器提示） */
  ship(orderNo: string, data: ShipPayload) {
    return request.post<void>(`/store/orders/${orderNo}/ship`, data)
  }
}
