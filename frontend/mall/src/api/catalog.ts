import { request } from './request'
import type { PageResult } from '../types/api'
import type { CategoryNode, FacetQuery, FacetResult, GoodsListItem, GoodsPageQuery } from '../types/catalog'

// ⚠ 路径必须带 /mall 前缀：mall 前端是直连网关的（与 auth.ts 里 '/mall/auth/login' 同一口径）。
// gateway 对该前缀做 StripPrefix=1，落到 mall-bff 时是 /catalog/...（服务本地白名单不带前缀）。

/** 全量分类树（首页宫格 / 分类页标题 / 筛选名解析共用） */
function categories(): Promise<CategoryNode[]> {
  return request.get<CategoryNode[]>('/mall/catalog/categories')
}

/** 商品分页 */
function goods(body: GoodsPageQuery): Promise<PageResult<GoodsListItem>> {
  return request.post<PageResult<GoodsListItem>>('/mall/catalog/goods', body)
}

/** 筛选维度聚合 */
function facets(body: FacetQuery): Promise<FacetResult> {
  return request.post<FacetResult>('/mall/catalog/facets', body)
}

// 导出形态对齐同目录的 auth.ts（`export const authApi = { ... }`）
export const catalogApi = { categories, goods, facets }
