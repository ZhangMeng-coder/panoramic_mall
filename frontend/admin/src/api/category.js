import request from './request'

/**
 * 商品分类 API
 */
export const categoryApi = {
  /** 新建分类 */
  add(data) {
    return request.post('/goods/categories', data)
  },

  /** 查询全量分类树 */
  tree() {
    return request.get('/goods/categories/tree')
  },

  /** 更新分类（仅名称与排序） */
  update(id, data) {
    return request.put(`/goods/categories/${id}`, data)
  },

  /** 删除分类 */
  remove(id) {
    return request.delete(`/goods/categories/${id}`)
  }
}
