import request from './request'

/**
 * 权限管理 API（admin 服务）
 */
export const permissionApi = {
  /** 全量权限树（含按钮，权限管理页用） */
  tree() {
    return request.get('/admin/permissions/tree')
  },

  /**
   * 前端目录接口（仅目录+页面，页面含路由地址 route）。
   * 当前全量返回（临时）；预留鉴权后按当前登录用户角色过滤。
   */
  menus() {
    return request.get('/admin/permissions/menus')
  },

  /** 权限详情 */
  detail(id) {
    return request.get(`/admin/permissions/${id}`)
  },

  /** 新建权限 */
  add(data) {
    return request.post('/admin/permissions', data)
  },

  /** 更新权限（仅名称/权限字符串/图标/路由地址/排序，父级与类型不可变更） */
  update(id, data) {
    return request.put(`/admin/permissions/${id}`, data)
  },

  /** 删除权限 */
  remove(id) {
    return request.delete(`/admin/permissions/${id}`)
  }
}
