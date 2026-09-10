import request from './request'

/**
 * 店主认证 API（经网关 /store/** 前缀转发到 store-bff 店铺端 BFF）
 */
export const authApi = {
  /** 注册（注册即登录） */
  register(data) {
    return request.post('/store/auth/register', data)
  },

  /** 登录 */
  login(data) {
    return request.post('/store/auth/login', data)
  },

  /** 登出 */
  logout() {
    return request.post('/store/auth/logout')
  },

  /** 当前登录店主信息 */
  me() {
    return request.get('/store/auth/me')
  }
}
