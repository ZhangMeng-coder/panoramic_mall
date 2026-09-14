import { ref } from 'vue'
import type { CurrentUser } from '../types/auth'

/**
 * C 端顾客登录态（轻量模块，未引入 pinia —— 与 store / admin 两端同构）：
 * - token 持久化到 localStorage（刷新保留）；
 * - 用户信息只驻留内存，刷新后由路由守卫拉 /auth/me 重建。
 *
 * 不存 defaultPath（store 端有是因为它登录后要进主页）：mall 登录后回来源页或首页，
 * 逻辑在页面里就够了。
 */
const TOKEN_KEY = 'pm-mall-token'

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const user = ref<CurrentUser | null>(null)

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

/** 清本地登录态（退出、或拦截器判定登录已失效时调用） */
export function clearAuth(): void {
  setToken('')
  user.value = null
}
