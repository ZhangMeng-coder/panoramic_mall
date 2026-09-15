/**
 * 登录态形状（store 身份空间，JWT `type=store`）。
 * 字段照 store-bff 的 `CurrentUserVO` / `LoginResultVO`；可空性照 `store_user` 的列定义。
 */

/** 当前登录店主（登录/注册返回的 user 与 `/store/auth/me` 同一个形状） */
export interface CurrentUser {
  id: number
  username: string
  /** 昵称（`store_user.nickname` 可空，页面自行回落 username） */
  nickname: string | null
  /** 手机号（`store_user.phone` 可空） */
  phone: string | null
  /** 权限串集合（店主端不接 RBAC，此处恒为空数组） */
  perms: string[]
}

/** 登录 / 注册结果 */
export interface LoginResult {
  token: string
  user: CurrentUser
}
