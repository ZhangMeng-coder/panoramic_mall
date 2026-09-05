import axios from 'axios'
import { ElMessage } from 'element-plus'
import { getToken, clearAuth } from '../store/auth'

/**
 * axios 实例：走 vite dev proxy，同源访问网关
 */
const request = axios.create({
  baseURL: '/',
  timeout: 10000
})

/**
 * 请求拦截：携带登录 token → Authorization: Bearer …
 */
request.interceptors.request.use(
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
function handleUnauthorized() {
  clearAuth()
  if (window.location.hash.replace(/^#/, '') !== '/login') {
    const redirect = encodeURIComponent(window.location.hash.replace(/^#/, '') || '/')
    window.location.hash = `#/login?redirect=${redirect}`
  }
}

/**
 * 响应拦截：
 * 后端统一返回 { code, msg, data }，code=200 表示成功。
 * - code=200 → 返回业务 data；
 * - code=401（登录态失效，HTTP 200 body 或真实 HTTP 401）→ 清态并跳登录页；
 * - code=403 → 提示“无权限”；
 * - 其余 → 弹后端 msg。
 */
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && res.code === 200) {
      return res.data
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
  (error) => {
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

export default request
