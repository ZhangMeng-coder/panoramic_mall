import { request } from './request'
import type { PageResult } from '../types/api'
import type {
  OrderAddressUpdatePayload,
  OrderCreatePayload,
  OrderPageQuery,
  OrderPayPayload,
  OrderVO
} from '../types/order'

/**
 * 订单接口（经网关 `/mall/**` 前缀转发到 mall-bff 8085）。
 * 路径与方法**照契约表 docs/contracts/mall-bff.md 写，不照后端代码写**。
 * 权限串一律为空：C 端不接 RBAC。
 *
 * ⚠ 三条**不体现在本文件**的服务端口径，写页面时要照它来：
 * ① **下单出参是数组**（`OrderVO[]`）——一次提交按 `storeId` 拆成多笔（一单一店），
 *    顺序 = `storeId` 升序（确定）。页面按「一笔一单」展示与支付，**不要把数组拍平成一笔**。
 * ② **状态文案只取 `statusMallLabel`**（域下发）：`status` 只用来判行为，别拿它拼文案。
 * ③ **假支付**：`pay` 的 `amount` 必须**等于订单总额**才算成功，比对在域内
 *    （不一致回 `400`「支付金额与订单总额不一致（应付 X 元，实付 Y 元）」）——
 *    本层与页面都**不做**这个校验，页面拿到什么 msg 就显示什么 msg。
 */
export const orderApi = {
  /** 下单（`DIRECT` 详情页直购 / `CART` 购物车结算），出参是**本次提交涉及的全部订单** */
  create(payload: OrderCreatePayload): Promise<OrderVO[]> {
    return request.post<OrderVO[]>('/mall/orders', payload)
  },

  /** 我的订单分页（作用域恒为登录顾客，页面无权选择看谁的单） */
  page(query: OrderPageQuery): Promise<PageResult<OrderVO>> {
    return request.post<PageResult<OrderVO>>('/mall/orders/page', query)
  },

  /** 订单详情（路径标识是**业务单号**，不是自增 id） */
  detail(orderNo: string): Promise<OrderVO> {
    return request.get<OrderVO>(`/mall/orders/${orderNo}`)
  },

  /** 支付（入参金额须与订单总额一致，见文件头 ③）；成功后**重新拉详情**，状态由域决定 */
  pay(orderNo: string, payload: OrderPayPayload): Promise<void> {
    return request.post<void>(`/mall/orders/${orderNo}/pay`, payload)
  },

  /** 确认收货（仅「已发货」可收，其余状态由域驳回落 400） */
  receive(orderNo: string): Promise<void> {
    return request.post<void>(`/mall/orders/${orderNo}/receive`)
  },

  /**
   * 改收货地址（**仅待支付可改**，闸门在域内 → 其余状态 400 原样透传，
   * 文案如「订单当前状态「已支付」不允许修改收货地址」）。
   *
   * ⚠ 它改的是**这一笔订单的快照**，不动顾客地址簿；出参 `void`，成功后**重新拉详情**
   * （地址快照由服务端下发，页面不本地改）。
   */
  updateAddress(orderNo: string, payload: OrderAddressUpdatePayload): Promise<void> {
    return request.put<void>(`/mall/orders/${orderNo}/address`, payload)
  }
}
