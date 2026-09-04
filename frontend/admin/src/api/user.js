import request from './request'

/**
 * 用户管理 API（admin 服务）
 */
export const userApi = {
  /** 用户分页查询（pageNum/pageSize/keyword/status） */
  page(params) {
    return request.get('/admin/users/page', { params })
  },

  /** 用户详情 */
  detail(id) {
    return request.get(`/admin/users/${id}`)
  },

  /** 新建用户 */
  add(data) {
    return request.post('/admin/users', data)
  },

  /** 更新用户（password 留空表示不修改） */
  update(id, data) {
    return request.put(`/admin/users/${id}`, data)
  },

  /** 删除用户 */
  remove(id) {
    return request.delete(`/admin/users/${id}`)
  },

  /** 查询用户已分配的角色ID */
  roleIds(id) {
    return request.get(`/admin/users/${id}/roles`)
  },

  /** 给用户分配角色（整体替换） */
  assignRoles(id, roleIds) {
    return request.put(`/admin/users/${id}/roles`, { roleIds })
  },

  /** 分页查询“不在指定角色内”的用户（角色下分配用户页面用） */
  unassignedUsersPage(roleId, params) {
    return request.get('/admin/users/unassigned/page', { params: { roleId, ...params } })
  }
}
