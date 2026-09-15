/**
 * 店铺形状（`store_shop` → store-bff → 店主端）。
 *
 * 放这里是因为被 `store/shop.ts`（响应式状态）、`ShopInfoView.vue`、`api/shop.ts` 三处共用。
 * 可空性照 `store_shop` 的列定义：除 shop_name / status 外几乎全列可空（草稿态只填一部分）。
 */

/** 店铺审核状态：0 草稿 / 1 待审核 / 2 已通过 / 3 已驳回 */
export type ShopStatus = 0 | 1 | 2 | 3

/** 我的店铺（`/store/shops/mine`；未创建时接口返回 null） */
export interface ShopInfo {
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
  status: ShopStatus
  submitTime: string | null
  auditBy: number | null
  auditTime: string | null
  auditRemark: string | null
  createTime: string
  updateTime: string
}

/** 店铺保存 / 提交审核请求体，与后端 `ShopSaveDTO` 同构 */
export interface ShopPayload {
  shopName: string
  logo?: string
  intro?: string
  contactName?: string
  contactPhone?: string
  region?: string
  address?: string
  licenseName?: string
  licenseNo?: string
  licenseImg?: string
}
