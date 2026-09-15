import request from './request'
import type { LoginResult } from '../types/auth'

/** 登录请求体，与 store-bff 的 `LoginDTO` 同构 */
export interface LoginPayload {
  username: string
  password: string
}

/** 注册请求体，与 store-bff 的 `RegisterDTO` 同构（注册即登录） */
export interface RegisterPayload {
  username: string
  password: string
  nickname?: string
  phone?: string
}

/**
 * 店主认证 API（经网关 /store/** 前缀转发到 store-bff 店铺端 BFF）
 */
export const authApi = {
  /** 注册（注册即登录） */
  register(data: RegisterPayload) {
    return request.post<LoginResult>('/store/auth/register', data)
  },

  /** 登录 */
  login(data: LoginPayload) {
    return request.post<LoginResult>('/store/auth/login', data)
  },

  /** 登出 */
  logout() {
    return request.post<void>('/store/auth/logout')
  },

  /** 当前登录店主信息 */
  me() {
    return request.get<LoginResult['user']>('/store/auth/me')
  }
}
