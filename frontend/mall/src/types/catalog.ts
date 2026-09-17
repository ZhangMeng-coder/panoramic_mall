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
