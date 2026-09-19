import { request } from './request'
import type { ApiRequestConfig } from './request'
import type {
  ChangePhonePayload,
  CurrentUser,
  LoginPayload,
  LoginResult,
  RegisterPayload
} from '../types/auth'

/**
 * C 端顾客账号接口（经网关 /mall/** 前缀转发到 mall-bff 8085）。
 * 路径与方法**照契约表 docs/contracts/mall-bff.md 写，不照后端代码写**。
 * 权限串一律为空：C 端不接 RBAC。
 */
export const authApi = {
  /** 取短信验证码。⚠ 演示实现：后端只打一行日志、不发真实短信；校验与固定码 888888 比对 */
  sendSmsCode(phone: string): Promise<void> {
    return request.post<void>('/mall/auth/sms-code', { phone })
  },

  /** 注册（**注册即登录**：直接返回 token + user，无需再调登录） */
  register(payload: RegisterPayload): Promise<LoginResult> {
    return request.post<LoginResult>('/mall/auth/register', payload)
  },

  /** 登录 */
  login(payload: LoginPayload): Promise<LoginResult> {
    return request.post<LoginResult>('/mall/auth/login', payload)
  },

  /** 登出：服务端删 Redis 会话快照即下线；本地 token 由调用方 clearAuth() 清 */
  logout(): Promise<void> {
    return request.post<void>('/mall/auth/logout')
  },

  /**
   * 当前登录顾客（出参含完整顾客资料：昵称 / 头像 / 性别 / 生日）；未登录 / token 失效会被网关拦下。
   *
   * ⚠ `silent401` 由**调用方**决定，方法里不写死——两种用法都需要：
   * - 路由守卫刷新重建用户态（`router/index.ts`）传 `{ silent401: true }`：公开首页上的失败不该把游客弹去登录页；
   * - 资料 / 换绑保存后刷新 store 走**普通调用**（不传，即 `silent401: false`）：会话真没了就该走 401 出口
   *   （清态 + 提示 + 跳登录页），而不是静默留在页面上继续显示旧资料。
   * 见 `api/request.ts` 的 `sessionExpired`。
   */
  me(config?: ApiRequestConfig): Promise<CurrentUser> {
    return request.get<CurrentUser>('/mall/auth/me', config)
  },

  /**
   * 换绑手机号（**双验证**：旧号验证码 + 新号验证码）。
   * 成功后服务端登录态快照即时更新、**不重签 token**——前端无需换 token、也不重新登录。
   * ⚠ 两个验证码都经 `sendSmsCode` 分别对旧号、新号取（本接口不负责发码）。
   */
  changePhone(payload: ChangePhonePayload): Promise<void> {
    return request.post<void>('/mall/auth/phone', payload)
  }
}
