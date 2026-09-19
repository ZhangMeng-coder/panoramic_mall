import { request } from './request'
import type { PageResult } from '../types/api'
import type {
  CategoryNode,
  FacetQuery,
  FacetResult,
  GoodsDetail,
  GoodsListItem,
  GoodsPageQuery
} from '../types/catalog'

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

/**
 * 商品详情（对应 `GET /catalog/goods/{id}`）
 * ⚠ 需登录态：不在免鉴权白名单里（白名单只有 `/catalog/categories`），业务上不可见的商品
 * 一律回业务码 404「商品不存在或已下架」——拦截器会弹这条 msg 并抛错，调用方按错误渲染即可。
 */
function detail(id: number): Promise<GoodsDetail> {
  return request.get<GoodsDetail>(`/mall/catalog/goods/${id}`)
}

// 导出形态对齐同目录的 auth.ts（`export const authApi = { ... }`）
export const catalogApi = { categories, goods, facets, detail }
