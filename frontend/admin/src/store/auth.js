import { ref } from 'vue'

/**
 * 登录态存储（轻量模块，未引入 pinia）：
 * - token 持久化到 localStorage（刷新保留）；
 * - 用户信息（含 perms）驻留内存，刷新后由路由守卫拉 /auth/me 重建。
 */
const TOKEN_KEY = 'pm-admin-token'

const token = ref(localStorage.getItem(TOKEN_KEY) || '')
const user = ref(null)
// 登录后第一个可见菜单页的路由地址（/ 落地页兜底用）
const defaultPath = ref('')

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

/** 计算菜单树中第一个可见页面路由（登录/刷新后作为落地页） */
export function resolveFirstRoute(menus) {
  const list = Array.isArray(menus) ? menus : []
  for (const dir of list) {
    const children = Array.isArray(dir.children) ? dir.children : []
    const page = children.find((c) => c && c.route)
    if (page && page.route) return page.route
  }
  return ''
}

export function getDefaultPath() {
  // 主页对任意已登录用户可见，作为“/”落地页兜底（刷新/直达首页时用）
  return defaultPath.value || '/home'
}

export function setDefaultPath(path) {
  defaultPath.value = path || ''
}

export function clearAuth() {
  setToken('')
  user.value = null
}
