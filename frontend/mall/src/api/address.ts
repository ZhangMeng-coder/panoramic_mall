import { request } from './request'
import type { AddressPayload, AddressStatus, AddressVO } from '../types/address'

/**
 * 顾客收货地址接口（经网关 `/mall/**` 前缀转发到 mall-bff 8085）。
 * 路径与方法**照契约表 docs/contracts/mall-bff.md 写，不照后端代码写**。
 * 权限串一律为空：C 端不接 RBAC。
 *
 * ⚠ 契约里还有两条**不体现在本文件**的服务端行为，写页面时要照它来：
 * ① `GET /addresses` 的**默认地址排在最前**（顺序以服务端为准，前端不再自己排）；
 * ② **首条地址自动设为默认**，而**删掉默认地址后不自动递补**（spec D7）——
 *    故「删完之后谁来当默认」不能由前端替服务端决定。
 */
export const addressApi = {
  /** 我的收货地址列表：默认地址排最前；**没有地址时是空数组，不是 null** */
  list(): Promise<AddressVO[]> {
    return request.get<AddressVO[]>('/mall/addresses')
  },

  /** 新增地址，出参是**新地址的 id**（不是整条地址，别照着返回值拼列表） */
  create(payload: AddressPayload): Promise<number> {
    return request.post<number>('/mall/addresses', payload)
  },

  /** 编辑地址（入参形状与新增同一份 `AddressPayload`） */
  update(id: number, payload: AddressPayload): Promise<void> {
    return request.put<void>(`/mall/addresses/${id}`, payload)
  },

  /** 删除地址（不存在 / 不属于本人 → 404「地址不存在」） */
  remove(id: number): Promise<void> {
    return request.delete<void>(`/mall/addresses/${id}`)
  },

  /** 设为默认：**改默认位的唯一路径**（入参只有 id、没有请求体，出参 void） */
  setDefault(id: number): Promise<void> {
    return request.post<void>(`/mall/addresses/${id}/default`)
  },

  /**
   * 我的地址状态（有没有地址 + 默认地址 id）—— **下单前的分支依据**（见 `useAddressGate`）。
   *
   * ⚠ 它**不回地址列表**：要给人看的地址照旧走 `list()`。服务端把它当派生态**缓存**，
   * 故「有默认地址」这条主路径上页面不必再拉一次列表。
   * ⚠ 缓存会陈旧（TTL 30 分钟 + 四个写路径主动失效）：拿它给的 `defaultAddressId` 下单，
   * 可能撞上域侧 404「地址不存在」——**页面必须能退回重选**，见 `useAddressGate`。
   */
  status(): Promise<AddressStatus> {
    return request.get<AddressStatus>('/mall/addresses/status')
  }
}
