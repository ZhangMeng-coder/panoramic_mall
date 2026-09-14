import request from './request'

/**
 * 店铺商品管理 API（平台侧；admin 端 BFF 编排，经网关 /admin/** 前缀转发，store 域已下沉为纯域）
 *
 * 说明：
 * - 列表/详情/三个筛选下拉均挂 `store:goods:list`，锁定/解锁挂 `store:goods:lock`（后端 @PreAuthorize 把关）；
 * - 分类筛选传**单个** categoryId（可选任意层级），「含全部子分类」的子树展开由 admin BFF 用分类树完成；
 * - 每行/详情的 `categoryPath`（分类全路径）由 BFF 读时解析，解析失败时为空，前端回退 `categoryName`。
 */
export const shopGoodsApi = {
  /** 店铺商品分页查询（pageNum/pageSize/keyword/categoryId/brandId/storeId/shelfStatus/lockStatus） */
  page(params) {
    return request.get('/admin/shop/goods/page', { params })
  },

  /** 店铺商品详情（跨店只读，含 SKU 列表与锁定信息） */
  detail(id) {
    return request.get(`/admin/shop/goods/${id}`)
  },

  /** 分类树（筛选下拉，可选任意层级） */
  categories() {
    return request.get('/admin/shop/goods/categories')
  },

  /** 品牌列表（筛选下拉） */
  brands() {
    return request.get('/admin/shop/goods/brands')
  },

  /** 店铺下拉选项（按店铺筛选，不按审核状态过滤） */
  shops() {
    return request.get('/admin/shop/goods/shops')
  },

  /** 锁定商品 { reason }（原因必填；锁定会级联下架其全部 SKU） */
  lock(id, data) {
    return request.post(`/admin/shop/goods/${id}/lock`, data)
  },

  /** 解锁商品（不自动恢复上架，需店主手动重新上架） */
  unlock(id) {
    return request.post(`/admin/shop/goods/${id}/unlock`)
  }
}
