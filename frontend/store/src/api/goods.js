import request from './request'

/**
 * 店铺在售商品 API（经网关 /store/goods/** 转发到 store-bff）。
 * 商品数据由 store-bff 编排落 store 域；分类/品牌下拉与「按 SKU 编码反查中台模板」
 * 由 store-bff 调商品中台（goods-center）取。
 * 全部接口在服务端先校验「我的店铺已审核通过」，未通过返回 code=403。
 */
export const goodsApi = {
  /** 我的商品分页（pageNum/pageSize/keyword/categoryId/brandId/shelfStatus） */
  page(params) {
    return request.get('/store/goods/spu/page', { params })
  },

  /** 商品详情（含 SKU 列表；关联中台时含 centerOutdated/centerMissing/centerSpu） */
  detail(id) {
    return request.get(`/store/goods/spu/${id}`)
  },

  /** 新增商品（可一并提交 SKU；SPU 与 SKU 一律以下架态落库） */
  add(data) {
    return request.post('/store/goods/spu', data)
  },

  /** 修改商品（基础信息 + 规格属性配置；存在上架 SKU 时规格配置只读） */
  update(id, data) {
    return request.put(`/store/goods/spu/${id}`, data)
  },

  /** 删除商品（存在上架 SKU 时拒绝；否则级联软删其全部 SKU） */
  remove(id) {
    return request.delete(`/store/goods/spu/${id}`)
  },

  /** SKU 整单替换（未上架可增/改/删；已上架须原样保留且不得缺失） */
  replaceSkus(id, skus) {
    return request.put(`/store/goods/spu/${id}/skus`, { skus })
  },

  /** SKU 上下架（shelfStatus：0 下架 / 1 上架；上架任一 SKU 联动 SPU 上架，全下架联动 SPU 下架） */
  updateSkuShelf(spuId, skuId, shelfStatus) {
    return request.put(`/store/goods/spu/${spuId}/skus/${skuId}/shelf`, { shelfStatus })
  }
}

/**
 * 商品中台基础数据 API（分类/品牌下拉 + 按 SKU 编码预填新增表单）
 */
export const goodsMetaApi = {
  /** 分类树（中台） */
  categories() {
    return request.get('/store/goods/categories/tree')
  },

  /** 品牌列表（中台，非必填） */
  brands() {
    return request.get('/store/goods/brands')
  },

  /**
   * 按 SKU 编码反查中台标准模板（新增商品时整单预填）。
   * 未命中返回 { spu: null }（成功响应，非错误），前端提示后允许继续自建；
   * matchedSkuCount > 1 表示中台编码重复、已取第一条。
   */
  spuBySkuCode(skuCode) {
    return request.get('/store/goods/center/spu-by-sku-code', { params: { skuCode } })
  }
}
