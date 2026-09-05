import request from './request'

/**
 * 店铺管理 API（平台侧；业务收敛在 store-center，经网关 /store/** 前缀转发）
 */
export const storeApi = {
  /** 店铺分页查询（pageNum/pageSize/status/keyword） */
  page(params) {
    return request.get('/store/admin/shops', { params })
  },

  /** 店铺详情 */
  detail(id) {
    return request.get(`/store/admin/shops/${id}`)
  },

  /** 店铺审核 { approved, auditRemark } */
  audit(id, data) {
    return request.post(`/store/admin/shops/${id}/audit`, data)
  }
}
