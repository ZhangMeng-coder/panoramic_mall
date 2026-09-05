import { ref } from 'vue'

/**
 * 店主登录态存储（轻量模块，未引入 pinia）：
 * - token 持久化到 localStorage（刷新保留）；
 * - 用户信息驻留内存，刷新后由路由守卫拉 /auth/me 重建。
 */
const TOKEN_KEY = 'pm-store-token'

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const user = ref(null)
// 「/」落地页兜底：登录后默认进主页（置顶且恒可见）
const defaultPath = ref('/home')

export function getToken() {
  return token.value
}

export function setToken(value) {
  token.value = value || ''
  if (value) {
    localStorage.setItem(TOKEN_KEY, value)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function getUser() {
  return user.value
}

export function setUser(value) {
  user.value = value || null
}

export function getDefaultPath() {
  return defaultPath.value || '/home'
}

export function setDefaultPath(path) {
  defaultPath.value = path || '/home'
}

export function clearAuth() {
  setToken('')
  user.value = null
}
