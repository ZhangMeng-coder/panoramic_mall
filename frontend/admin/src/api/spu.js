import request from './request'

/**
 * 商品（SPU）API
 */
export const spuApi = {
  /** 商品分页查询（pageNum/pageSize/categoryId/brandId/status/keyword） */
  page(params) {
    return request.get('/goods/spu/page', { params })
  },

  /** 商品详情（含 SKU 列表） */
  detail(id) {
    return request.get(`/goods/spu/${id}`)
  },

  /** 新建商品（SPU + SKU） */
  add(data) {
    return request.post('/goods/spu', data)
  },

  /** 更新商品（SKU diff） */
  update(id, data) {
    return request.put(`/goods/spu/${id}`, data)
  },

  /** 商品上下架 */
  updateStatus(id, status) {
    return request.put(`/goods/spu/${id}/status`, { status })
  },

  /** 删除商品 */
  remove(id) {
    return request.delete(`/goods/spu/${id}`)
  }
}
