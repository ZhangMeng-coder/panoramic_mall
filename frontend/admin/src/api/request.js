import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * axios 实例：走 vite dev proxy，同源访问网关
 */
const request = axios.create({
  baseURL: '/',
  timeout: 10000
})

/**
 * 响应拦截器：
 * 后端统一返回 { code, msg, data }，code=200 表示成功。
 * 成功时直接返回业务数据 data；失败时弹出后端 msg 并 reject。
 */
request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && res.code === 200) {
      return res.data
    }
    const message = res && res.msg ? res.msg : '请求失败'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  },
  (error) => {
    ElMessage.error(error.message || '网络异常，请稍后重试')
    return Promise.reject(error)
  }
)

export default request
