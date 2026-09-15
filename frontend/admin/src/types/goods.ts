/**
 * 商品中台（goods-center）的共享形状。
 *
 * 放这里是因为被**多个 api 模块**共用（如品牌/分类，`api/brand.ts`、`api/category.ts`、
 * `api/shopGoods.ts` 三处都要用；各自抄一份必然漂移）。只被单个 api 模块用的形状仍就近声明在各自的 `api/*.ts`。
 *
 * ⚠ admin / store / mall 三端各自一份、不抽共享包（见拉平设计「非目标」）。
 * 字段照 common 的 `com.panoramic.common.goods.vo.*`，可空性照对应表的列定义。
 */

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

/** 品牌行，与 `BrandVO` 同构（logo/description 可空） */
export interface BrandItem {
  id: number
  name: string
  logo: string | null
  description: string | null
  sort: number
  createTime: string
}

/** 分类树节点，与 `CategoryTreeVO` 同构（level 为层级，从 1 起） */
export interface CategoryNode {
  id: number
  /** 父分类 ID，0 表示顶级 */
  parentId: number
  name: string
  /** 层级：1 / 2 / 3（最多三级） */
  level: number
  sort: number
  children: CategoryNode[]
}
