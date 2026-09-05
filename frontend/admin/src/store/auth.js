import { ref } from 'vue'

/**
 * 登录态存储（轻量模块，未引入 pinia）：
 * - token 持久化到 localStorage（刷新保留）；
 * - 用户信息（含 perms）驻留内存，刷新后由路由守卫拉 /auth/me 重建。
 */
const TOKEN_KEY = 'pm-admin-token'

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const user = ref(null)

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

/** 当前用户权限集合（数组，可能为空） */
export function getUserPerms() {
  const u = user.value
  if (!u || !Array.isArray(u.perms)) return []
  return u.perms
}

/**
 * 是否拥有指定权限：
 * - 传单个字符串：perms 需包含它；
 * - 传数组：拥有其中任意一个即可（or 语义）。
 * 兼容 * 通配管理员（预留）。
 */
export function hasPerm(perm) {
  if (perm === undefined || perm === null || perm === '') return true
  const perms = getUserPerms()
  if (perms.includes('*')) return true
  if (Array.isArray(perm)) {
    if (!perm.length) return true
    return perm.some((p) => perms.includes(p))
  }
  return perms.includes(perm)
}

export function getDefaultPath() {
  // “/”落地页：主页对任意登录用户恒可见且置顶，固定进 /home
  return '/home'
}

export function clearAuth() {
  setToken('')
  user.value = null
}
