import request from './request'

/**
 * 商品品牌 API
 */
export const brandApi = {
  /** 品牌分页查询（pageNum/pageSize/keyword） */
  page(params) {
    return request.get('/admin/goods/brands/page', { params })
  },

  /** 全量品牌列表（下拉用） */
  list() {
    return request.get('/admin/goods/brands/list')
  },

  /** 新建品牌 */
  add(data) {
    return request.post('/admin/goods/brands', data)
  },

  /** 更新品牌 */
  update(id, data) {
    return request.put(`/admin/goods/brands/${id}`, data)
  },

  /** 删除品牌 */
  remove(id) {
    return request.delete(`/admin/goods/brands/${id}`)
  }
}
