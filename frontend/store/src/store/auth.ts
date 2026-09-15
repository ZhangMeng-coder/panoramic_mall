import { ref } from 'vue'
import type { CurrentUser } from '../types/auth'

/**
 * 店主登录态存储（轻量模块，未引入 pinia）：
 * - token 持久化到 localStorage（刷新保留）；
 * - 用户信息驻留内存，刷新后由路由守卫拉 /auth/me 重建。
 */
const TOKEN_KEY = 'pm-store-token'

const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
const user = ref<CurrentUser | null>(null)
// 「/」落地页兜底：登录后默认进主页（置顶且恒可见）
const defaultPath = ref<string>('/home')

export function getToken(): string {
  return token.value
}

export function setToken(value: string): void {
  token.value = value || ''
  if (value) {
    localStorage.setItem(TOKEN_KEY, value)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function getUser(): CurrentUser | null {
  return user.value
}

export function setUser(value: CurrentUser | null): void {
  user.value = value || null
}

export function getDefaultPath(): string {
  return defaultPath.value || '/home'
}

export function setDefaultPath(path: string): void {
  defaultPath.value = path || '/home'
}

export function clearAuth(): void {
  setToken('')
  user.value = null
}
