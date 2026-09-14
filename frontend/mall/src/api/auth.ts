import { request } from './request'
import type { CurrentUser, LoginPayload, LoginResult, RegisterPayload } from '../types/auth'

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

  /** 当前登录顾客；未登录 / token 失效会被网关拦下 */
  me(): Promise<CurrentUser> {
    return request.get<CurrentUser>('/mall/auth/me')
  }
}
