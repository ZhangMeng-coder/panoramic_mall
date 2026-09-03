import request from './request'

/**
 * 角色管理 API（admin 服务）
 */
export const roleApi = {
  /** 角色分页查询（pageNum/pageSize/keyword） */
  page(params) {
    return request.get('/admin/roles/page', { params })
  },

  /** 全量角色列表（下拉/分配用） */
  list() {
    return request.get('/admin/roles/list')
  },

  /** 新建角色 */
  add(data) {
    return request.post('/admin/roles', data)
  },

  /** 更新角色 */
  update(id, data) {
    return request.put(`/admin/roles/${id}`, data)
  },

  /** 删除角色 */
  remove(id) {
    return request.delete(`/admin/roles/${id}`)
  },

  /** 查询角色已分配的权限ID */
  permissionIds(id) {
    return request.get(`/admin/roles/${id}/permissions`)
  },

  /** 给角色分配权限（整体替换） */
  assignPermissions(id, permissionIds) {
    return request.put(`/admin/roles/${id}/permissions`, { permissionIds })
  },

  /** 查询角色下已分配的用户ID */
  userIds(id) {
    return request.get(`/admin/roles/${id}/user-ids`)
  },

  /** 分页查询“不在该角色内”的用户（分配用户页面用） */
  unassignedUsersPage(id, params) {
    return request.get(`/admin/roles/${id}/unassigned-users/page`, { params })
  },

  /** 给角色分配用户（整体替换） */
  assignUsers(id, userIds) {
    return request.put(`/admin/roles/${id}/users`, { userIds })
  }
}
