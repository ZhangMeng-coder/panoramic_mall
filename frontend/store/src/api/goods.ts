import request from './request'
import type { PageQuery, PageResult } from '../types/api'

/** 一条规格取值，如 `{ spec: '颜色', value: '红' }` */
export interface SpecAttr {
  spec: string
  value: string
}

/** 规格属性配置项：一个规格维度 + 该维度的全部可选值 */
export interface SpecConfigItem {
  spec: string
  values: string[]
}

/** 分类树节点（中台），与 goods-center 的 `CategoryTreeVO` 同构 */
export interface CategoryNode {
  id: number
  parentId: number
  name: string
  /** 层级：1 / 2 / 3（最多三级） */
  level: number
  sort: number
  children: CategoryNode[]
}

/** 品牌行（中台），与 goods-center 的 `BrandVO` 同构 */
export interface BrandItem {
  id: number
  name: string
  logo: string | null
  description: string | null
  sort: number
  createTime: string
}

/** 在售商品 SKU 行，与 store 域的 `StoreGoodsSkuVO` 同构 */
export interface StoreGoodsSku {
  id: number
  spuId: number
  specAttrs: SpecAttr[]
  skuCode: string | null
  mainImage: string | null
  /** 店主自填售价 */
  price: number
  /** 0 下架 / 1 上架 */
  shelfStatus: number
}

/** SKU 整单替换的入参，与 store 域的 `StoreGoodsSkuDTO` 同构（id 为空表示新增） */
export interface StoreGoodsSkuPayload {
  id?: number
  specAttrs?: SpecAttr[]
  skuCode?: string
  mainImage?: string
  price?: number
}

/** 在售商品列表行，与 store 域的 `StoreGoodsSpuPageItemVO` 同构 */
export interface StoreGoodsSpuPageItem {
  id: number
  name: string
  mainImage: string | null
  categoryId: number
  categoryName: string | null
  /** 分类全路径（BFF 读时解析，解析失败为空串，前端回退 categoryName） */
  categoryPath: string
  brandName: string | null
  /** 0 下架 / 1 上架（推导量：≥1 SKU 上架则上架，前端只读） */
  shelfStatus: number
  /** 0 未锁定 / 1 已锁定（平台锁定，锁定期整行只读） */
  lockStatus: number
  lockReason: string | null
  /** 锁定人（`UserType:UserId` 格式；⚠ 商户端**不展示**，仅管理端展示） */
  lockUser: string | null
  lockTime: string | null
  skuCount: number
  /** 关联的中台模板 SPU ID（未关联为 null） */
  goodsSpuId: number | null
  updateTime: string
}

/** 中台模板的 SKU，与 goods-center 的 `SkuVO` 同构 */
export interface CenterSku {
  id: number
  spuId: number
  specAttrs: SpecAttr[]
  skuCode: string | null
  mainImage: string | null
}

/** 中台标准模板详情，与 goods-center 的 `SpuDetailVO` 同构 */
export interface CenterSpuDetail {
  id: number
  name: string
  categoryId: number
  categoryName: string
  brandId: number
  brandName: string
  mainImage: string | null
  imageList: string[]
  description: string | null
  status: number
  /** 版本戳（任何修改即刷新），店主端比对它决定「更新提示」 */
  version: number
  specConfig: SpecConfigItem[]
  skus: CenterSku[]
  categoryPath: string
  createTime: string
}

/**
 * 在售商品详情。
 *
 * 后端是 store 域 `StoreGoodsSpuDetailVO` + store-bff `StoreGoodsSpuDetailBffVO` 追加的四项
 * （centerOutdated / centerMissing / centerSpu / categoryPath），这里摊平成一张表，
 * 免得为一个「只是多几个字段」的继承关系在前端造两层类型。
 */
export interface StoreGoodsSpuDetail {
  id: number
  storeId: number
  name: string
  categoryId: number
  categoryName: string | null
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
  /** 关联的中台模板 SPU ID（未关联为 null） */
  goodsSpuId: number | null
  /** 关联时记录的模板版本（未关联为 null） */
  centerVersion: number | null
  skus: StoreGoodsSku[]
  createTime: string
  updateTime: string
  /** BFF 读时解析的分类全路径（解析失败为空串） */
  categoryPath: string
  /** 中台模板已更新（本地 centerVersion 落后） */
  centerOutdated: boolean
  /** 关联的中台模板已不存在（被删） */
  centerMissing: boolean
  /** 中台模板详情（关联且存在时非空） */
  centerSpu: CenterSpuDetail | null
}

/** 在售商品分页查询参数，与 store 域的 `StoreGoodsSpuPageQueryDTO` 同构 */
export interface StoreGoodsSpuPageQuery extends PageQuery {
  keyword?: string
  categoryId?: number
  brandId?: number
  shelfStatus?: number
}

/** 新增在售商品请求体，与 store 域的 `StoreGoodsSpuSaveDTO` 同构 */
export interface StoreGoodsSpuSavePayload {
  name: string
  categoryId: number
  categoryName?: string
  brandId?: number
  brandName?: string
  mainImage?: string
  imageList?: string[]
  description?: string
  specConfig?: SpecConfigItem[]
  /** 关联的中台模板 SPU ID（可选） */
  goodsSpuId?: number
  /** 关联时记录的模板版本 */
  centerVersion?: number
  skus?: StoreGoodsSkuPayload[]
}

/** 修改在售商品请求体（不含 SKU），与 store 域的 `StoreGoodsSpuUpdateDTO` 同构 */
export type StoreGoodsSpuUpdatePayload = Omit<StoreGoodsSpuSavePayload, 'goodsSpuId' | 'skus'>

/** 按 SKU 编码反查中台模板的结果，与 goods-center 的 `SpuBySkuCodeVO` 同构 */
export interface SpuBySkuCode {
  /** 未命中时为 null（成功响应，非错误） */
  spu: CenterSpuDetail | null
  /** 命中多条时的数量（>1 表示中台编码重复，已取第一条） */
  matchedSkuCount: number
}

/**
 * 店铺在售商品 API（经网关 /store/goods/** 转发到 store-bff）。
 * 商品数据由 store-bff 编排落 store 域；分类/品牌下拉与「按 SKU 编码反查中台模板」
 * 由 store-bff 调商品中台（goods-center）取。
 * 全部接口在服务端先校验「我的店铺已审核通过」，未通过返回 code=403。
 */
export const goodsApi = {
  /** 我的商品分页（pageNum/pageSize/keyword/categoryId/brandId/shelfStatus） */
  page(params: StoreGoodsSpuPageQuery) {
    return request.get<PageResult<StoreGoodsSpuPageItem>>('/store/goods/spu/page', { params })
  },

  /** 商品详情（含 SKU 列表；关联中台时含 centerOutdated/centerMissing/centerSpu） */
  detail(id: number) {
    return request.get<StoreGoodsSpuDetail>(`/store/goods/spu/${id}`)
  },

  /** 新增商品（可一并提交 SKU；SPU 与 SKU 一律以下架态落库）→ 新商品 ID */
  add(data: StoreGoodsSpuSavePayload) {
    return request.post<number>('/store/goods/spu', data)
  },

  /** 修改商品（基础信息 + 规格属性配置；存在上架 SKU 时规格配置只读） */
  update(id: number, data: StoreGoodsSpuUpdatePayload) {
    return request.put<void>(`/store/goods/spu/${id}`, data)
  },

  /** 删除商品（存在上架 SKU 时拒绝；否则级联软删其全部 SKU） */
  remove(id: number) {
    return request.delete<void>(`/store/goods/spu/${id}`)
  },

  /** SKU 整单替换（未上架可增/改/删；已上架须原样保留且不得缺失） */
  replaceSkus(id: number, skus: StoreGoodsSkuPayload[]) {
    return request.put<void>(`/store/goods/spu/${id}/skus`, { skus })
  },

  /** SKU 上下架（shelfStatus：0 下架 / 1 上架；上架任一 SKU 联动 SPU 上架，全下架联动 SPU 下架） */
  updateSkuShelf(spuId: number, skuId: number, shelfStatus: number) {
    return request.put<void>(`/store/goods/spu/${spuId}/skus/${skuId}/shelf`, { shelfStatus })
  }
}

/**
 * 商品中台基础数据 API（分类/品牌下拉 + 按 SKU 编码预填新增表单）
 */
export const goodsMetaApi = {
  /** 分类树（中台） */
  categories() {
    return request.get<CategoryNode[]>('/store/goods/categories/tree')
  },

  /** 品牌列表（中台，非必填） */
  brands() {
    return request.get<BrandItem[]>('/store/goods/brands')
  },

  /**
   * 按 SKU 编码反查中台标准模板（新增商品时整单预填）。
   * 未命中返回 { spu: null }（成功响应，非错误），前端提示后允许继续自建；
   * matchedSkuCount > 1 表示中台编码重复、已取第一条。
   */
  spuBySkuCode(skuCode: string) {
    return request.get<SpuBySkuCode>('/store/goods/center/spu-by-sku-code', {
      params: { skuCode }
    })
  }
}
