import request from './request'

/**
 * 商品分类 API
 */
export const categoryApi = {
  /** 新建分类 */
  add(data) {
    return request.post('/admin/goods/categories', data)
  },

  /** 查询全量分类树 */
  tree() {
    return request.get('/admin/goods/categories/tree')
  },

  /** 更新分类（仅名称与排序） */
  update(id, data) {
    return request.put(`/admin/goods/categories/${id}`, data)
  },

  /** 删除分类 */
  remove(id) {
    return request.delete(`/admin/goods/categories/${id}`)
  }
}
