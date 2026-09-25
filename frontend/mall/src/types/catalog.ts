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
  /**
   * 商品评分（**平均值**，1 ~ 5，两位小数）。⚠ **可空**：`null` = **还没有人评价** ——
   * 不渲染评分（不显示 0、不显示占位）：「没人评过」与「评了 0 分」不是一回事。
   */
  score: number | null
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
  /**
   * 可用库存（= `stock`；`locked_stock` 已于 2026-09-21 废弃、不参与口径）。**非可空**：后端恒返回 0 或正整数。
   * 0 表示该规格已售罄——⚠ 售罄**不影响商品可见性**（商品照常可打开），也不禁用规格值点选。
   * 商户端的低库存预警阈值不在本端（`warn_stock` 不下发）。
   */
  availableStock: number
}

/** 商品详情（对应后端 MallGoodsDetailVO） */
export interface GoodsDetail {
  id: number
  name: string
  mainImage: string | null
  imageList: string[] | null
  /**
   * 商品详情**HTML**（店主自由录入的富文本，两端录入框提示语即「支持 HTML」）。
   * ⚠ 这份是**已消毒**的：mall-bff 下发前按白名单清洗过（剥脚本 / 事件属性 / 样式），
   * 故按 HTML 渲染（`v-html`）是对的；别改回 `{{ }}` 插值（店主写的 `<p>` 会原样露出来）。
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
  /**
   * **商品**评分（平均值，1 ~ 5）。⚠ **可空**：`null` = 无人评价 → 不渲染评分
   * （与列表卡的 `score` 同一个值、同一个口径）。
   */
  score: number | null
  /**
   * **店铺**评分（平均值，1 ~ 5）——详情页同时展示商品评分与店铺评分，故这里要多一个。
   * ⚠ **可空**，语义同上。⚠ 后端**没有**为它新增一次跨域调用（取自判可见性时那次 `getShop` 的返回），
   * 故它与商品评分可能来自**两次不同时刻**的读数——页面原样展示即可，不在这里比对。
   */
  shopScore: number | null
}
