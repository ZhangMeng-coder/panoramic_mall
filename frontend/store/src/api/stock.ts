import request from './request'
import type { PageResult } from '../types/api'
import type { SpecAttr } from './goods'

/**
 * SKU 库存分页查询参数，与 store 域的 `StoreGoodsStockPageQueryDTO` 同构。
 * 作用域由服务端按登录店主名下（store_id）固定，前端不传。
 */
export interface StockPageQuery {
  pageNum: number
  pageSize: number
  /** 模糊匹配 SKU 编码 或 商品名称 */
  keyword: string | null
  /** 0 下架 / 1 上架，null = 全部 */
  shelfStatus: number | null
  /** true = 只看 stock <= warnStock（未设预警阈值的行为天然不满足） */
  lowStockOnly: boolean
}

/** 库存列表行，与 store 域的 `StoreGoodsStockPageItemVO` 同构 */
export interface StockRow {
  skuId: number
  spuId: number
  spuName: string | null
  mainImage: string | null
  specAttrs: SpecAttr[] | null
  skuCode: string | null
  price: number
  /** 0 下架 / 1 上架 */
  shelfStatus: number
  /** 总库存（商户维护） */
  stock: number
  /** 低库存预警阈值，null = 不预警 */
  warnStock: number | null
}

/** 单行改库存请求体，与 store 域的 `StoreGoodsStockUpdateDTO` 同构（warnStock 传 null = 清除预警） */
export interface StockUpdatePayload {
  stock: number
  warnStock: number | null
}

/** 批量设库存请求体，与 store 域的 `StoreGoodsStockBatchUpdateDTO` 同构（统一设为同一值，不动预警值） */
export interface StockBatchPayload {
  skuIds: number[]
  stock: number
}

/**
 * SKU 库存 API（经网关 /store/goods/stock/** 转发到 store-bff）。
 * 与商品接口同样先校验「我的店铺已审核通过」，未通过返回 code=403；
 * 平台锁定的商品其库存同样只读（服务端拒绝）。
 */
export const stockApi = {
  /** 我的 SKU 库存分页（按 SKU 平铺一行一条，支持关键字 / 上下架 / 仅看低库存） */
  page(params: StockPageQuery) {
    return request.get<PageResult<StockRow>>('/store/goods/stock/page', { params })
  },

  /** 改单行 SKU 库存（直接赋值，不是增减） */
  update(skuId: number, data: StockUpdatePayload) {
    return request.put<void>(`/store/goods/stock/${skuId}`, data)
  },

  /** 批量把选中 SKU 的总库存统一设为同一值（不动预警值） */
  batchUpdate(data: StockBatchPayload) {
    return request.put<void>('/store/goods/stock/batch', data)
  }
}
