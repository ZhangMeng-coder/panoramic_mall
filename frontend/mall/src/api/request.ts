import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios'
import { clearAuth, getToken } from '../store/auth'
import { showToast } from '../composables/useToast'
import { AUTH_PATHS } from '../router/paths'

/** 对外接口统一返回体：与 common 的 RespData 同构（契约 README 规定页面级接口必包） */
interface RespData<T> {
  code: number
  msg: string
  data: T
}

const BUSINESS_OK = 200
const HTTP_UNAUTHORIZED = 401
const HTTP_FORBIDDEN = 403

/** 未登录 / 登录态失效的统一提示文案（路由守卫 401 兜底两处共用同一句） */
export const LOGIN_REQUIRED_MSG = '请先登录'

/**
 * 调用面允许的额外配置：`silent401` 让**指定的**请求在 401 时既不提示也不跳登录页。
 * 不用 axios 的模块增强（那要写 `<D = any>`），而是自家 interface 继承 + 拦截器里一次收窄转换。
 */
interface ApiRequestConfig extends AxiosRequestConfig {
  /** 401 静默：只清本地登录态，不弹提示、不跳登录页（唯一使用者见 sessionExpired 注释） */
  silent401?: boolean
}

const instance = axios.create({
  baseURL: '/', // 走 vite dev proxy：/mall/** → 网关 8080（StripPrefix=1）→ mall-bff 8085
  timeout: 10000
})

/** 请求拦截：携带登录 token → Authorization: Bearer … */
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/** 取该请求是否要求 401 静默（`config` 在 axios 里是 any，此处收窄一次，不外泄） */
function isSilent401(config: unknown): boolean {
  return Boolean((config as ApiRequestConfig | undefined)?.silent401)
}

/**
 * 401 统一出口：清本地登录态 →（非静默时）提示「请先登录」并跳登录页带回跳参数。
 *
 * ⚠ **与「首页公开」共存的关键在 `silent`**：路由守卫里刷新重建登录态的 `authApi.me()` 带
 * `silent401`——一个带过期 token 的游客逛**公开首页**时，后台那次重建失败不该把他弹去登录页，
 * 页面照常按未登录态渲染（顶栏自己会变成「请登录」）。除这一处外一律**不静默**：
 * 「一涉及商品查询 / 详情就要登录」这条分级，用户得看见理由与去处。
 *
 * ⚠ 只在**本地确实有登录态**时才提示与跳转：
 * - 并发的多个请求同时 401（列表页 goods + facets 是一起发的）只提示一次、只跳一次；
 * - 清态后 token 为空，后续 401 不再重复；下次登录后 token 重新存在，提示能力自动恢复；
 * - 从没登录过的游客直接命中需登录接口时（正常会被路由守卫先拦下）只抛错，不弹不跳。
 *
 * ⚠ 跳转用 `window.location.hash` 而不 import router：`router/index.ts → api/auth.ts → api/request.ts`
 * 会成环（与 admin 端同一处理），故登录 / 注册页路径也从叶子模块 `router/paths.ts` 取。
 */
function sessionExpired(silent: boolean): Error {
  const hadSession = Boolean(getToken())
  clearAuth()
  if (!silent && hadSession) {
    showToast(LOGIN_REQUIRED_MSG, 'info')
    redirectToLogin()
  }
  return new Error(LOGIN_REQUIRED_MSG)
}

/** 当前页 → `/login?redirect=<原页>`；已在登录 / 注册页则不跳（否则登录页自己 401 时会成环） */
function redirectToLogin(): void {
  const current = window.location.hash.replace(/^#/, '') || '/'
  if (AUTH_PATHS.includes(current.split('?')[0])) return
  window.location.hash = `#/login?redirect=${encodeURIComponent(current)}`
}

/** 业务码非 200：401 走登录态出口；其余弹后端 msg 原文（后端已有中文提示，前端不再包一层）后抛错 */
function businessFail(code: number | undefined, msg: string | undefined, silent: boolean): never {
  if (code === HTTP_UNAUTHORIZED) {
    throw sessionExpired(silent)
  }
  const message = msg || '请求失败'
  showToast(message, code === HTTP_FORBIDDEN ? 'info' : 'error')
  throw new Error(message)
}

instance.interceptors.response.use(
  (response: AxiosResponse<Partial<RespData<unknown>>>) => {
    const res = response.data
    if (res && res.code === BUSINESS_OK) return response
    return businessFail(res?.code, res?.msg, isSilent401(response.config))
  },
  (error: AxiosError<Partial<RespData<unknown>>>) => {
    const silent = isSilent401(error.config)
    const status = error.response?.status
    if (status === HTTP_UNAUTHORIZED) {
      return Promise.reject(sessionExpired(silent))
    }
    if (status === HTTP_FORBIDDEN) {
      const message = error.response?.data?.msg || '无权限执行该操作'
      showToast(message, 'info')
      return Promise.reject(new Error(message))
    }
    if (status) {
      const message = error.response?.data?.msg || `请求失败（HTTP ${status}）`
      showToast(message)
      return Promise.reject(new Error(message))
    }
    showToast('网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

/**
 * 解包后的调用面：`request.get<T>()` 直接拿到业务数据 `T`，`RespData` 外壳由拦截器校验、这里剥掉。
 * 当前 5 条账号接口只用到 get / post；将来加 put / delete 时在这里补一个方法即可。
 */
interface ApiClient {
  get<T>(url: string, config?: ApiRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: ApiRequestConfig): Promise<T>
}

export const request: ApiClient = {
  async get<T>(url: string, config?: ApiRequestConfig): Promise<T> {
    const res = await instance.get<RespData<T>>(url, config)
    return res.data.data
  },
  async post<T>(url: string, data?: unknown, config?: ApiRequestConfig): Promise<T> {
    const res = await instance.post<RespData<T>>(url, data, config)
    return res.data.data
  }
}
