import request from './request'
import type { ShopInfo, ShopPayload } from '../types/shop'

/**
 * 店主店铺 API（经网关 /store/** 前缀转发到 store-bff，店铺数据由 store-bff 编排落 store 域）
 */
export const shopApi = {
  /** 我的店铺（未创建返回 null） */
  mine() {
    return request.get<ShopInfo | null>('/store/shops/mine')
  },

  /** 保存草稿 */
  save(data: ShopPayload) {
    return request.post<void>('/store/shops/save', data)
  },

  /** 提交审核 */
  submit(data: ShopPayload) {
    return request.post<void>('/store/shops/submit', data)
  }
}
