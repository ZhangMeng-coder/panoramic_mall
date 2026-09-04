import request from './request'

/**
 * 商品（SPU）API
 */
export const spuApi = {
  /** 商品分页查询（pageNum/pageSize/categoryId/brandId/status/keyword） */
  page(params) {
    return request.get('/goods/spu/page', { params })
  },

  /** 商品详情（含 SKU 列表、规格属性配置、分类完整链条） */
  detail(id) {
    return request.get(`/goods/spu/${id}`)
  },

  /** 新建商品（基础信息 + 规格属性配置，不含 SKU） */
  add(data) {
    return request.post('/goods/spu', data)
  },

  /** 更新商品（仅基础信息 + 规格属性配置，不含 SKU） */
  update(id, data) {
    return request.put(`/goods/spu/${id}`, data)
  },

  /** 全量替换商品 SKU（规格管理弹窗保存；空 skus = 清空该商品全部 SKU） */
  updateSkus(id, skus) {
    return request.put(`/goods/spu/${id}/skus`, { skus })
  },

  /** 商品展示/隐藏切换（1 展示，0 隐藏） */
  updateStatus(id, status) {
    return request.put(`/goods/spu/${id}/status`, { status })
  },

  /** 删除商品 */
  remove(id) {
    return request.delete(`/goods/spu/${id}`)
  }
}
