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
  /** 昵称；资料为空时后端**回退为手机号**（兜底只在 /auth/me 一处，前端不再判一次） */
  nickname: string
  /** 头像 URL；资料不可用时为空 */
  avatar: string | null
  /** 性别：0 未知 / 1 男 / 2 女；资料不可用时为空 */
  gender: number | null
  /** 生日；资料不可用时为空。后端是 LocalDate，本端 JSON 里就是 `YYYY-MM-DD` 或 null */
  birthday: string | null
  /**
   * **本次资料是否真的从 customer-center 读到了**（后端恒返回 true / false，非 null）。
   * `true` = 域读成功，此时上面四项为 null 就是「顾客没填」；`false` = 读失败已降级，
   * `nickname` 是手机号兜底、其余三项是「拿不到」而非「空」——**四项都不可信**。
   * ⚠ 为 `false` 时**不得渲染资料表单**：`PUT /profile` 是整份替换，拿降级值提交会静默清空真实资料。
   * ⚠ 别用「昵称 == 手机号」去推断这件事（契约「资料可用性」），只认这个标记。
   */
  profileLoaded: boolean
  phone: string
  /** 顾客账号无 RBAC 权限维度，恒为空数组（C 端不接 RBAC） */
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

/**
 * 换绑手机号请求体（对齐 ChangePhoneDTO）—— **双验证**：旧号码与新号码各一个验证码。
 * 字段名逐字如此（`oldCode` / `newPhone` / `newCode`），别改名也别加「旧手机号」入参：
 * 旧号由服务端从登录态取，前端传不了、也不需要传。
 */
export interface ChangePhonePayload {
  /** 当前手机号收到的验证码 */
  oldCode: string
  /** 要换绑到的新手机号 */
  newPhone: string
  /** 新手机号收到的验证码 */
  newCode: string
}
