/* ============================================================================
   C 端顾客的身份类型 —— 与契约 docs/contracts/mall-bff.md 登记的
   CurrentUserVO / LoginResultVO、以及 DTO 的字段口径一致。
   字段定义以后端 VO / DTO 类为准，这里只是前端侧的镜像（不重复写业务规则）。
   ========================================================================== */

/** 当前登录顾客（对齐 CurrentUserVO） */
export interface CurrentUser {
  id: number
  /** 账号即手机号：后端把手机号同时填进 username 与 phone，两者同值 */
  username: string
  nickname: string
  phone: string
  /** C 端不接 RBAC，恒为空数组（字段保留是为了与另两端同构） */
  perms: string[]
}

/** 登录 / 注册的返回（对齐 LoginResultVO）：注册即登录，所以两者形状相同 */
export interface LoginResult {
  token: string
  user: CurrentUser
}

/** 登录请求体（对齐 LoginDTO） */
export interface LoginPayload {
  phone: string
  code: string
}

/** 注册请求体（对齐 RegisterDTO）：nickname 选填、最长 50 */
export interface RegisterPayload {
  phone: string
  code: string
  nickname?: string
}
