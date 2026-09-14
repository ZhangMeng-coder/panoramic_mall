import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios'
import { clearAuth, getToken } from '../store/auth'
import { showToast } from '../composables/useToast'

/** 对外接口统一返回体：与 common 的 RespData 同构（契约 README 规定页面级接口必包） */
interface RespData<T> {
  code: number
  msg: string
  data: T
}

const BUSINESS_OK = 200
const HTTP_UNAUTHORIZED = 401
const HTTP_FORBIDDEN = 403

/** 登录已失效时抛出的文案：调用方想提示就 `error.message`，也可选择静默 */
const SESSION_EXPIRED = '登录已失效，请重新登录'

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

/**
 * ⚠ 401 只清本地态 + 抛错，**不自己跳登录页** —— 这是与 store / admin 两端的**有意差异**。
 *
 * 另两端的页面全都要登录，所以它们 401 时直接改 hash 跳登录页是对的；
 * **mall 首页是公开页**：一个带过期 token 的游客逛首页不该被弹去登录页，
 * 页面照常按「未登录态」渲染（顶栏自己会变成「请登录」）。
 * 跳不跳由**路由守卫 / 调用方**决定，拦截器不擅自改 URL。
 *
 * 同理这里**不弹提示条**：公开页每次打开都弹一句「登录已失效」是噪音。
 * 需要登录才能做的动作，由调用方 catch 后自行用 error.message 提示。
 */
function sessionExpired(): Error {
  clearAuth()
  return new Error(SESSION_EXPIRED)
}

/** 业务码非 200：弹后端 msg 原文（后端已有中文提示，前端不再包一层）后抛错 */
function businessFail(code: number | undefined, msg: string | undefined): never {
  if (code === HTTP_UNAUTHORIZED) {
    throw sessionExpired()
  }
  const message = msg || '请求失败'
  showToast(message, code === HTTP_FORBIDDEN ? 'info' : 'error')
  throw new Error(message)
}

instance.interceptors.response.use(
  (response: AxiosResponse<Partial<RespData<unknown>>>) => {
    const res = response.data
    if (res && res.code === BUSINESS_OK) return response
    return businessFail(res?.code, res?.msg)
  },
  (error: AxiosError<Partial<RespData<unknown>>>) => {
    const status = error.response?.status
    if (status === HTTP_UNAUTHORIZED) {
      return Promise.reject(sessionExpired())
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
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
}

export const request: ApiClient = {
  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const res = await instance.get<RespData<T>>(url, config)
    return res.data.data
  },
  async post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const res = await instance.post<RespData<T>>(url, data, config)
    return res.data.data
  }
}
