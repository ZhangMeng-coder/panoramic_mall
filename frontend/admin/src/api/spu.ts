import request from './request'
import type { PageQuery, PageResult } from '../types/api'
import type { SpecAttr, SpecConfigItem } from '../types/goods'

/** 商品列表行，与 `SpuPageItemVO` 同构（`categoryPath` 由 BFF 读时解析，可能为空） */
export interface SpuPageItem {
  id: number
  name: string
  categoryId: number
  categoryName: string
  categoryPath: string
  brandId: number
  brandName: string
  mainImage: string | null
  /** 1 展示 / 0 隐藏（表示对商城是否可见） */
  status: number
  createTime: string
}

/** SKU 行，与 `SkuVO` 同构 */
export interface SkuItem {
  id: number
  spuId: number
  specAttrs: SpecAttr[]
  skuCode: string | null
  mainImage: string | null
}

/** SKU 整单替换的入参，与后端 `SkuDTO` 同构（id 为空表示新增） */
export interface SkuPayload {
  id?: number
  specAttrs?: SpecAttr[]
  skuCode?: string
  mainImage?: string
}

/** 商品详情，与 `SpuDetailVO` 同构 */
export interface SpuDetail {
  id: number
  name: string
  categoryId: number
  categoryName: string
  brandId: number
  brandName: string
  mainImage: string | null
  /** 轮播图（后端把空的 JSON 列回落成 `[]`，恒非空） */
  imageList: string[]
  description: string | null
  status: number
  /** 版本戳（任何修改即刷新） */
  version: number
  /** 规格属性配置（同上，恒非空） */
  specConfig: SpecConfigItem[]
  skus: SkuItem[]
  categoryPath: string
  createTime: string
}

/** 商品分页查询参数，与后端 `SpuPageQueryDTO` 同构 */
export interface SpuPageQuery extends PageQuery {
  categoryId?: number
  brandId?: number
  status?: number
  keyword?: string
}

/** 商品新增 / 编辑请求体，与后端 `SpuSaveDTO` / `SpuUpdateDTO` 同构（不含 SKU） */
export interface SpuPayload {
  name: string
  categoryId: number
  brandId: number
  mainImage?: string
  imageList?: string[]
  description?: string
  status?: number
  specConfig?: SpecConfigItem[]
}

/**
 * 商品（SPU）API
 */
export const spuApi = {
  /** 商品分页查询（pageNum/pageSize/categoryId/brandId/status/keyword） */
  page(params: SpuPageQuery) {
    return request.get<PageResult<SpuPageItem>>('/admin/goods/spu/page', { params })
  },

  /** 商品详情（含 SKU 列表、规格属性配置、分类完整链条） */
  detail(id: number) {
    return request.get<SpuDetail>(`/admin/goods/spu/${id}`)
  },

  /** 新建商品（基础信息 + 规格属性配置，不含 SKU）→ 新商品 ID */
  add(data: SpuPayload) {
    return request.post<number>('/admin/goods/spu', data)
  },

  /** 更新商品（仅基础信息 + 规格属性配置，不含 SKU） */
  update(id: number, data: SpuPayload) {
    return request.put<void>(`/admin/goods/spu/${id}`, data)
  },

  /** 全量替换商品 SKU（规格管理弹窗保存；空 skus = 清空该商品全部 SKU） */
  updateSkus(id: number, skus: SkuPayload[]) {
    return request.put<void>(`/admin/goods/spu/${id}/skus`, { skus })
  },

  /** 商品展示/隐藏切换（1 展示，0 隐藏） */
  updateStatus(id: number, status: number) {
    return request.put<void>(`/admin/goods/spu/${id}/status`, { status })
  },

  /** 删除商品 */
  remove(id: number) {
    return request.delete<void>(`/admin/goods/spu/${id}`)
  }
}
