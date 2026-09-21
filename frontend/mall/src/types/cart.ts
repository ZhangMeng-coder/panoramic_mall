/* ============================================================================
   购物车 —— 与契约 docs/contracts/mall-bff.md 的 `/cart` 八行 +「形状与行为口径」
   表里的购物车七条一致（对应后端 MallCartVO / MallCartShopVO / MallCartItemVO
   与 MallCartItemAddDTO）。
   字段定义以后端 VO / DTO 类为准，这里只是前端侧的镜像（不重复写业务规则）。
   ========================================================================== */

import type { SpecAttr } from './catalog'

/** 购物车行（对应后端 MallCartItemVO） */
export interface CartItem {
  /** 行主键 —— 改数量 / 改选中 / 删除都以它为准（不是 skuId） */
  id: number
  spuId: number
  skuId: number
  /** 商品名；null = 该 SPU 已被删除，连名字都拿不到（此时 mainImage/specAttrs/price 同样为 null） */
  name: string | null
  mainImage: string | null
  /** 规格属性。⚠ 购物车行**没有** specConfig 可作维度顺序基准（那是详情页才下发的 SPU 级配置），
   *  故页面按这里的存储顺序原样展示，不像详情页那样重排 */
  specAttrs: SpecAttr[] | null
  price: number | null
  /** 可用库存（= stock − locked_stock）。⚠ 加购不校验库存，库存只影响「还能不能再加」 */
  availableStock: number
  quantity: number
  /** 服务端持久化的选中态（页面上的「全选」见 CartView.vue 的作用域注释） */
  selected: boolean
  /** 商品本身不可买（店铺未审核 / SPU 已下架或锁定 / SPU 已删）→ 不计入件数与金额 */
  invalid: boolean
  /** 商品可见但这一行不能再加（invalid 或 quantity >= availableStock）→ 前端禁「+」 */
  purchasable: boolean
}

/**
 * 购物车按店铺分组（对应后端 MallCartShopVO）。
 * ⚠ `shopId === null` 的那一组是「商品已被删除、无法归属店铺」的**兜底组**，
 * 正常渲染 `shopName` 即可，不要为它另写一套版式。
 */
export interface CartShop {
  shopId: number | null
  shopName: string
  items: CartItem[]
}

/** 购物车（对应后端 MallCartVO）—— 分组与四个汇总值都由服务端算好，前端只渲染 */
export interface CartResult {
  shops: CartShop[]
  /** 有效行件数之和 */
  totalQuantity: number
  selectedQuantity: number
  /** 选中且有效的行金额之和（后端已算好，前端不要自己乘加一遍） */
  selectedAmount: number
  invalidCount: number
}

/**
 * 加购请求体（对应后端 `MallCartItemAddDTO`）。
 * ⚠ 只校验「该商品对 C 端可见」——**不校验库存、不锁库存**（购物车是购买意向不是占位）；
 * 数量上限（单行 999 / 单购物车 100 行）在域侧，超限 400 原样透传。
 */
export interface CartItemAddPayload {
  spuId: number
  skuId: number
  quantity: number
}
