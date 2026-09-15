import request from './request'
import type { PageQuery, PageResult } from '../types/api'
import type { BrandItem } from '../types/goods'

/** 品牌分页查询参数，与后端 `BrandPageQueryDTO` 同构 */
export interface BrandPageQuery extends PageQuery {
  keyword?: string
}

/** 品牌新增 / 编辑请求体，与后端 `BrandSaveDTO` / `BrandUpdateDTO` 同构 */
export interface BrandPayload {
  name: string
  logo?: string
  description?: string
  sort?: number
}

/**
 * 商品品牌 API
 */
export const brandApi = {
  /** 品牌分页查询（pageNum/pageSize/keyword） */
  page(params: BrandPageQuery) {
    return request.get<PageResult<BrandItem>>('/admin/goods/brands/page', { params })
  },

  /** 全量品牌列表（下拉用） */
  list() {
    return request.get<BrandItem[]>('/admin/goods/brands/list')
  },

  /** 新建品牌 → 新品牌 ID */
  add(data: BrandPayload) {
    return request.post<number>('/admin/goods/brands', data)
  },

  /** 更新品牌 */
  update(id: number, data: BrandPayload) {
    return request.put<void>(`/admin/goods/brands/${id}`, data)
  },

  /** 删除品牌 */
  remove(id: number) {
    return request.delete<void>(`/admin/goods/brands/${id}`)
  }
}
