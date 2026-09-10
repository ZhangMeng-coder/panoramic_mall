import request from './request'

/**
 * 店铺管理 API（平台侧；admin 端 BFF 编排，经网关 /admin/** 前缀转发，store 域已下沉为纯域）
 */
export const storeApi = {
  /** 店铺分页查询（pageNum/pageSize/status/keyword） */
  page(params) {
    return request.get('/admin/shop/shops', { params })
  },

  /** 店铺详情 */
  detail(id) {
    return request.get(`/admin/shop/shops/${id}`)
  },

  /** 店铺审核 { approved, auditRemark } */
  audit(id, data) {
    return request.post(`/admin/shop/shops/${id}/audit`, data)
  }
}
