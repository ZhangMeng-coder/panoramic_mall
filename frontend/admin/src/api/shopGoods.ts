import request from './request'
import type { PageQuery, PageResult } from '../types/api'
import type { BrandItem, CategoryNode, SpecAttr, SpecConfigItem } from '../types/goods'

/** 店铺商品列表行，与 `StoreGoodsSpuPlatformPageItemVO` 同构（可空列照 `store_goods_spu`） */
export interface ShopGoodsPageItem {
  id: number
  storeId: number
  /** 所属店铺名（store 域填充，`store_shop.shop_name` 非空） */
  storeName: string
  name: string
  mainImage: string | null
  categoryId: number
  categoryName: string | null
  /** 分类全路径（BFF 读时解析，解析失败为空串，前端回退 categoryName） */
  categoryPath: string
  brandId: number | null
  brandName: string | null
  /** 0 下架 / 1 上架（推导量：≥1 SKU 上架则上架） */
  shelfStatus: number
  skuCount: number
  /** 0 未锁定 / 1 已锁定 */
  lockStatus: number
  lockReason: string | null
  lockUser: string | null
  lockTime: string | null
  goodsSpuId: number | null
  updateTime: string
}

/** 店铺商品 SKU 行，与 `StoreGoodsSkuVO` 同构 */
export interface ShopGoodsSku {
  id: number
  spuId: number
  specAttrs: SpecAttr[]
  skuCode: string | null
  mainImage: string | null
  price: number
  shelfStatus: number
}

/**
 * 店铺商品详情，owner 侧 `StoreGoodsSpuDetailVO` + 平台侧追加的 `storeName` / `categoryPath`
 * （后端 `StoreGoodsSpuPlatformDetailVO extends StoreGoodsSpuDetailVO`）。
 */
export interface ShopGoodsDetail {
  id: number
  storeId: number
  /** 平台侧追加：所属店铺名 */
  storeName: string
  name: string
  categoryId: number
  categoryName: string | null
  /** 平台侧追加：分类全路径（解析失败为空串） */
  categoryPath: string
  brandId: number | null
  brandName: string | null
  mainImage: string | null
  imageList: string[]
  description: string | null
  specConfig: SpecConfigItem[]
  shelfStatus: number
  lockStatus: number
  lockReason: string | null
  lockUser: string | null
  lockTime: string | null
  goodsSpuId: number | null
  centerVersion: number | null
  skus: ShopGoodsSku[]
  createTime: string
  updateTime: string
}

/** 店铺商品分页查询参数，与后端 `ShopGoodsPageQueryDTO` 同构（分类为单值，子树展开由 BFF 做） */
export interface ShopGoodsPageQuery extends PageQuery {
  keyword?: string
  categoryId?: number
  brandId?: number
  storeId?: number
  shelfStatus?: number
  lockStatus?: number
}

/** 店铺下拉选项，与 `ShopOptionVO` 同构 */
export interface ShopOption {
  id: number
  shopName: string
}

/** 锁定请求体，与后端 `StoreGoodsLockDTO` 同构（原因必填） */
export interface ShopGoodsLockPayload {
  reason: string
}

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
  page(params: ShopGoodsPageQuery) {
    return request.get<PageResult<ShopGoodsPageItem>>('/admin/shop/goods/page', { params })
  },

  /** 店铺商品详情（跨店只读，含 SKU 列表与锁定信息） */
  detail(id: number) {
    return request.get<ShopGoodsDetail>(`/admin/shop/goods/${id}`)
  },

  /** 分类树（筛选下拉，可选任意层级） */
  categories() {
    return request.get<CategoryNode[]>('/admin/shop/goods/categories')
  },

  /** 品牌列表（筛选下拉） */
  brands() {
    return request.get<BrandItem[]>('/admin/shop/goods/brands')
  },

  /** 店铺下拉选项（按店铺筛选，不按审核状态过滤） */
  shops() {
    return request.get<ShopOption[]>('/admin/shop/goods/shops')
  },

  /** 锁定商品 { reason }（原因必填；锁定会级联下架其全部 SKU） */
  lock(id: number, data: ShopGoodsLockPayload) {
    return request.post<void>(`/admin/shop/goods/${id}/lock`, data)
  },

  /** 解锁商品（不自动恢复上架，需店主手动重新上架） */
  unlock(id: number) {
    return request.post<void>(`/admin/shop/goods/${id}/unlock`)
  }
}
