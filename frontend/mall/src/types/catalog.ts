/* ============================================================================
   C 端商品浏览（分类树 / 商品分页 / 筛选聚合）的类型
   ----------------------------------------------------------------------------
   与契约 docs/contracts/mall-bff.md 登记的 CategoryTreeVO / MallGoodsItemVO /
   MallFacetVO 及两个查询 DTO 的字段口径一致。
   字段定义以后端 VO / DTO 类为准，这里只是前端侧的镜像（不重复写业务规则）。
   ========================================================================== */

/** 分类树节点（对应后端 CategoryTreeVO） */
export interface CategoryNode {
  id: number
  parentId: number
  name: string
  level: number
  sort: number
  /** 图标图片 URL，可空 —— 空或加载失败时前端回退 CSS 渐变占位 */
  icon: string | null
  children: CategoryNode[] | null
}

/** 列表页商品项（对应后端 MallGoodsItemVO） */
export interface GoodsListItem {
  id: number
  name: string
  mainImage: string | null
  minPrice: number | null
  storeId: number
  storeName: string
  categoryId: number | null
  categoryName: string | null
  brandId: number | null
  brandName: string | null
}

/** 筛选维度的可选项 */
export interface FacetItem {
  id: number
  name: string
  count: number
}

export interface FacetResult {
  categories: FacetItem[]
  brands: FacetItem[]
}

export type SortKey = 'default' | 'priceAsc' | 'priceDesc'

export interface GoodsPageQuery {
  keyword?: string
  categoryId?: number
  categoryIds?: number[]
  brandIds?: number[]
  sort?: SortKey
  pageNum: number
  pageSize: number
}

export interface FacetQuery {
  keyword?: string
  categoryId?: number
  categoryIds?: number[]
  brandIds?: number[]
}

/* ============================================================================
   商品详情（对应后端 MallGoodsDetailVO / MallGoodsSkuVO 及 common 的
   SpecConfigItem / SpecAttr）—— 与列表是**两个接口**：列表出的是摘要，
   详情出的是完整可售信息（规格配置 + 上架 SKU）
   ========================================================================== */

/** 单个规格属性（对应后端 SpecAttr）—— 一个 SKU 上的一组「规格名 → 规格值」 */
export interface SpecAttr {
  spec: string
  value: string
}

/** 规格维度配置（对应后端 SpecConfigItem）—— 页面按它渲染规格选择器 */
export interface SpecConfigItem {
  spec: string
  values: string[]
}

/** 详情页的 SKU 项（对应后端 MallGoodsSkuVO）—— **只含上架 SKU**，下架的不下发 */
export interface GoodsDetailSku {
  id: number
  specAttrs: SpecAttr[]
  price: number
  mainImage: string | null
}

/** 商品详情（对应后端 MallGoodsDetailVO） */
export interface GoodsDetail {
  id: number
  name: string
  mainImage: string | null
  imageList: string[] | null
  /**
   * 商品详情**原文**（店主在 store 端用 textarea 录入的自由文本，可能含 HTML 标签）。
   * ⚠ 前端**按纯文本渲染**，不得 v-html —— 那等于让店主往 C 端页面注入脚本。
   */
  description: string | null
  specConfig: SpecConfigItem[] | null
  storeId: number
  storeName: string
  categoryId: number | null
  categoryName: string | null
  brandId: number | null
  brandName: string | null
  /** 上架 SKU。契约保证非空（SPU 上架 ⟺ 至少一个 SKU 上架），价格区间由它算出（后端不另下发 min/max） */
  skus: GoodsDetailSku[]
}
