import request from './request'
import type { PageQuery, PageResult } from '../types/api'

/**
 * 店铺行，与 store 域的 `ShopVO` 同构。
 * `store_shop` 表除 shop_name / status 外几乎全列可空（草稿态只填了一部分），故一律 `| null`。
 */
export interface ShopItem {
  id: number
  shopName: string
  logo: string | null
  intro: string | null
  contactName: string | null
  contactPhone: string | null
  /** 省市区（如「浙江省/杭州市/西湖区」） */
  region: string | null
  address: string | null
  licenseName: string | null
  licenseNo: string | null
  licenseImg: string | null
  /** 0 草稿 / 1 待审核 / 2 已通过 / 3 已驳回 */
  status: number
  submitTime: string | null
  /** 审核人（存审计格式 `UserType:UserId`，管理端可见） */
  auditBy: number | null
  auditTime: string | null
  auditRemark: string | null
  createTime: string
  updateTime: string
}

/** 店铺分页查询参数，与后端 `ShopPageQueryDTO` 同构 */
export interface ShopPageQuery extends PageQuery {
  status?: number
  keyword?: string
}

/** 店铺审核请求体，与后端 `ShopAuditDTO` 同构（驳回时 auditRemark 必填） */
export interface ShopAuditPayload {
  approved: boolean
  auditRemark?: string
}

/**
 * 店铺管理 API（平台侧；admin 端 BFF 编排，经网关 /admin/** 前缀转发，store 域已下沉为纯域）
 */
export const storeApi = {
  /** 店铺分页查询（pageNum/pageSize/status/keyword） */
  page(params: ShopPageQuery) {
    return request.get<PageResult<ShopItem>>('/admin/shop/shops', { params })
  },

  /** 店铺详情 */
  detail(id: number) {
    return request.get<ShopItem>(`/admin/shop/shops/${id}`)
  },

  /** 店铺审核 { approved, auditRemark } */
  audit(id: number, data: ShopAuditPayload) {
    return request.post<void>(`/admin/shop/shops/${id}/audit`, data)
  }
}
