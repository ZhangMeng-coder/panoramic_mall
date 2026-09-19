/* ============================================================================
   收货地址 —— 与契约 docs/contracts/mall-bff.md 的 `/addresses` 五行一致。
   字段定义以后端 `AddressVO` / `AddressSaveDTO`（mall-bff 的 vo / dto 包）为准，
   这里只是前端侧的镜像。
   ========================================================================== */

/** 收货地址（对齐 `AddressVO`）——`GET /addresses` 的出参元素 */
export interface AddressVO {
  /** 主键 */
  id: number
  /** 收件人姓名，域侧 `@NotBlank` + ≤50 字符 */
  receiverName: string
  /** 收件人手机号，域侧 `@NotBlank` + ≤20 字符（**不限手机号形态**：固话 / 分机也合法） */
  receiverPhone: string
  /** 省市区（自由文本单列，如「广东省 深圳市 南山区」）；**可空**，≤100 字符 */
  region: string | null
  /** 详细地址，域侧 `@NotBlank` + ≤255 字符 */
  detailAddress: string
  /** 默认地址：0 否 / 1 是 */
  isDefault: number
}

/**
 * 地址保存请求体（对齐 `AddressSaveDTO`，`POST /addresses` 与 `PUT /addresses/{id}` 共用）。
 *
 * ⚠ **刻意不含 `id`、也不含 `isDefault`**：新增 / 编辑靠「调哪个端点」区分，
 * 默认位只有两条路径——「首条地址自动设为默认」与 `POST /addresses/{id}/default`。
 * 想改默认位就往 `addressApi.setDefault()` 送，不要在保存体里带一个 `isDefault`。
 */
export interface AddressPayload {
  /** 收件人姓名，≤50 字符 */
  receiverName: string
  /** 收件人手机号，≤20 字符 */
  receiverPhone: string
  /** 省市区，可空（≤100 字符）；未填送 `null`，不送空串 */
  region: string | null
  /** 详细地址，≤255 字符 */
  detailAddress: string
}
