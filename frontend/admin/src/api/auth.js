import request from './request'

/**
 * 认证相关 API（admin 服务 /auth）
 * 登录接口在网关与服务两侧均为白名单；其余需带登录态。
 */
export const authApi = {
  /** 登录：{ username, password } → { token, user } */
  login(data) {
    return request.post('/admin/auth/login', data)
  },

  /** 登出：删除服务端 Redis 登录态（本地 token 由调用方清除） */
  logout() {
    return request.post('/admin/auth/logout')
  },

  /** 当前登录用户信息（含 perms），刷新页面恢复用户态用 */
  me() {
    return request.get('/admin/auth/me')
  },

  /** 修改当前登录用户密码：{ oldPassword, newPassword } */
  changePassword(data) {
    return request.put('/admin/auth/password', data)
  }
}
