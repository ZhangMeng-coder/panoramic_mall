import request from './request'
import type { CurrentUser, LoginResult } from '../types/auth'

/** 登录请求体，与后端 `LoginDTO` 同构 */
export interface LoginPayload {
  username: string
  password: string
}

/** 修改密码请求体，与后端 `ChangePasswordDTO` 同构 */
export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

/**
 * 认证相关 API（admin 服务 /auth）
 * 登录接口在网关与服务两侧均为白名单；其余需带登录态。
 */
export const authApi = {
  /** 登录：{ username, password } → { token, user } */
  login(data: LoginPayload) {
    return request.post<LoginResult>('/admin/auth/login', data)
  },

  /** 登出：删除服务端 Redis 登录态（本地 token 由调用方清除） */
  logout() {
    return request.post<void>('/admin/auth/logout')
  },

  /** 当前登录用户信息（含 perms），刷新页面恢复用户态用 */
  me() {
    return request.get<CurrentUser>('/admin/auth/me')
  },

  /** 修改当前登录用户密码：{ oldPassword, newPassword } */
  changePassword(data: ChangePasswordPayload) {
    return request.put<void>('/admin/auth/password', data)
  }
}
