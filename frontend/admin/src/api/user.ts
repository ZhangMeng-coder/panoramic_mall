import request from './request'
import type { PageQuery, PageResult } from '../types/api'
import type { RoleItem } from './role'

/** 用户行，与后端 `UserVO` 同构（nickname/phone/email/avatar 四列可空） */
export interface UserItem {
  id: number
  username: string
  nickname: string | null
  phone: string | null
  email: string | null
  avatar: string | null
  /** 1 启用 / 0 禁用 */
  status: number
  createTime: string
  /** 已分配的角色（详情/列表带出） */
  roles: RoleItem[]
}

/** 用户分页查询参数，与后端 `UserPageQueryDTO` 同构 */
export interface UserPageQuery extends PageQuery {
  keyword?: string
  status?: number
}

/** 用户新增 / 编辑请求体，与后端 `UserSaveDTO` / `UserUpdateDTO` 同构 */
export interface UserPayload {
  username: string
  /** 新增必填；编辑时留空（不传）表示不修改密码 */
  password?: string
  nickname?: string
  phone?: string
  email?: string
  avatar?: string
  status?: number
}

/**
 * 用户管理 API（admin 服务）
 */
export const userApi = {
  /** 用户分页查询（pageNum/pageSize/keyword/status） */
  page(params: UserPageQuery) {
    return request.get<PageResult<UserItem>>('/admin/users/page', { params })
  },

  /** 用户详情 */
  detail(id: number) {
    return request.get<UserItem>(`/admin/users/${id}`)
  },

  /** 新建用户 → 新用户 ID */
  add(data: UserPayload) {
    return request.post<number>('/admin/users', data)
  },

  /** 更新用户（password 留空表示不修改） */
  update(id: number, data: UserPayload) {
    return request.put<void>(`/admin/users/${id}`, data)
  },

  /** 删除用户 */
  remove(id: number) {
    return request.delete<void>(`/admin/users/${id}`)
  },

  /** 查询用户已分配的角色ID */
  roleIds(id: number) {
    return request.get<number[]>(`/admin/users/${id}/roles`)
  },

  /** 给用户分配角色（整体替换） */
  assignRoles(id: number, roleIds: number[]) {
    return request.put<void>(`/admin/users/${id}/roles`, { roleIds })
  },

  /** 分页查询“不在指定角色内”的用户（角色下分配用户页面用） */
  unassignedUsersPage(roleId: number, params: UserPageQuery) {
    return request.get<PageResult<UserItem>>('/admin/users/unassigned/page', {
      params: { roleId, ...params }
    })
  }
}
