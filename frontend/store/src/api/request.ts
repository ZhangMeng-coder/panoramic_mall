import axios, { type AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '../store/auth'
import type { RespData } from '../types/api'

/**
 * axios 实例：走 vite dev proxy，同源访问网关
 */
const instance = axios.create({
  baseURL: '/',
  timeout: 10000,
  // 数组型 query 参数发成重复键（`brandIds=1&brandIds=2`），不用 axios 默认的 `brandIds[]=1&brandIds[]=2`：
  // Spring 的 `List<Long>` 只认重复键；PHP 风格的 `[]` 后缀会被 BeanWrapper 当成下标解析（空下标 → NumberFormatException），请求直接失败。
  // 用对象形式只改数组键名，其余序列化（编码、嵌套对象）仍走 axios 默认，不接管。
  paramsSerializer: { indexes: null }
})

/**
 * 请求拦截：携带登录 token → Authorization: Bearer …
 */
instance.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

/** 登录失效统一处理：清本地登录态，回登录页（保留来源页便于回跳） */
function handleUnauthorized(): void {
  clearAuth()
  if (window.location.hash.replace(/^#/, '') !== '/login') {
    const redirect = encodeURIComponent(window.location.hash.replace(/^#/, '') || '/')
    window.location.hash = `#/login?redirect=${redirect}`
  }
}

/**
 * 响应拦截：
 * 后端统一返回 { code, msg, data }，code=200 表示成功。
 * - code=200 → **原样返回 response**（解包交给文件末尾的 `request` 封装，这样返回类型才与运行时一致）；
 * - code=401（登录态失效，HTTP 200 body 或真实 HTTP 401）→ 清态并跳登录页；
 * - code=403 → 提示“无权限”；
 * - 其余 → 弹后端 msg。
 *
 * ⚠ 与 `frontend/mall/src/api/request.ts` 的关系：**401 出口三端一致**——清本地登录态 + 带 `redirect` 跳登录页
 * （mall 另会先弹一句「请先登录」，本端不弹、直接跳）。
 * 本端是店主端，每个页面都要登录，因此**没有** mall 的 `silent401` 静默开关——那一个仅供路由守卫
 * 刷新重建登录态的 `authApi.me()`（mall 首页对游客开放，本端没有这种页面）。
 * **别去掉 mall 的 `silent401`、也别扩大这个静默面**（口径见 CLAUDE.md「mall 前台（用户端）」与 cross-cutting 第 11 条）。
 *
 * ⚠ 本文件与 `frontend/admin/src/api/request.ts` 的**代码部分逐字相同**（历史上就是两份复制，
 * 只有头部注释里各自指认「本端是哪一端」的两行不同）。本次拉平**只加类型、不做去重**
 * （去重需要 workspace / 共享包，属另一件事）；改这里时记得同步另一端，否则两份会开始漂移。
 */
instance.interceptors.response.use(
  (response) => {
    // axios 把 response.data 定成 any；这里收窄成后端统一外壳，免得下面一路 any 下去
    const res = response.data as Partial<RespData<unknown>> | undefined
    if (res && res.code === 200) {
      return response
    }
    if (res && res.code === 401) {
      handleUnauthorized()
      return Promise.reject(new Error('登录已失效，请重新登录'))
    }
    if (res && res.code === 403) {
      ElMessage.warning(res.msg || '无权限执行该操作')
      return Promise.reject(new Error(res.msg || '无权限执行该操作'))
    }
    const message = res && res.msg ? res.msg : '请求失败'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  (error: AxiosError<Partial<RespData<unknown>>>) => {
    if (error.response) {
      const status = error.response.status
      // 网关 / 服务安全入口返回的真实 HTTP 401
      if (status === 401) {
        handleUnauthorized()
        return Promise.reject(new Error('登录已失效，请重新登录'))
      }
      if (status === 403) {
        ElMessage.warning('无权限执行该操作')
        return Promise.reject(new Error('无权限执行该操作'))
      }
      const body = error.response.data
      const message = body && body.msg ? body.msg : `请求失败（HTTP ${status}）`
      ElMessage.error(message)
      return Promise.reject(error)
    }
    ElMessage.error(error.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

/**
 * 调用层封装：把 `RespData` 外壳解开，只把业务数据交给调用方。
 *
 * 为什么不直接导出 `instance`：拦截器已经把 `{code,msg,data}` 判过一遍，
 * 但 axios 的 `get<T>()` 返回类型是 `AxiosResponse<T>` —— 直接用会处处对不上。
 * 这层薄封装让「运行时拿到的值」与「类型说的值」一致，且**不用任何 as 断言**。
 */
export interface ApiClient {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const request: ApiClient = {
  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const res: AxiosResponse<RespData<T>> = await instance.get<RespData<T>>(url, config)
    return res.data.data
  },
  async post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const res: AxiosResponse<RespData<T>> = await instance.post<RespData<T>>(url, data, config)
    return res.data.data
  },
  async put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    const res: AxiosResponse<RespData<T>> = await instance.put<RespData<T>>(url, data, config)
    return res.data.data
  },
  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const res: AxiosResponse<RespData<T>> = await instance.delete<RespData<T>>(url, config)
    return res.data.data
  }
}

export default request
