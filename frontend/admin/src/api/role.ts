import request from './request'
import type { PageQuery, PageResult } from '../types/api'

/** 角色行，与后端 `RoleVO` 同构（`sys_role.description` 可空） */
export interface RoleItem {
  id: number
  name: string
  code: string
  description: string | null
  sort: number
  createTime: string
}

/** 角色分页查询参数，与后端 `RolePageQueryDTO` 同构 */
export interface RolePageQuery extends PageQuery {
  keyword?: string
}

/** 角色新增 / 编辑请求体，与后端 `RoleSaveDTO` / `RoleUpdateDTO` 同构 */
export interface RolePayload {
  name: string
  code: string
  description?: string
  sort?: number
}

/**
 * 角色管理 API（admin 服务）
 */
export const roleApi = {
  /** 角色分页查询（pageNum/pageSize/keyword） */
  page(params: RolePageQuery) {
    return request.get<PageResult<RoleItem>>('/admin/roles/page', { params })
  },

  /** 全量角色列表（下拉/分配用） */
  list() {
    return request.get<RoleItem[]>('/admin/roles/list')
  },

  /** 新建角色 → 新角色 ID */
  add(data: RolePayload) {
    return request.post<number>('/admin/roles', data)
  },

  /** 更新角色 */
  update(id: number, data: RolePayload) {
    return request.put<void>(`/admin/roles/${id}`, data)
  },

  /** 删除角色 */
  remove(id: number) {
    return request.delete<void>(`/admin/roles/${id}`)
  },

  /** 查询角色已分配的权限ID */
  permissionIds(id: number) {
    return request.get<number[]>(`/admin/roles/${id}/permissions`)
  },

  /** 给角色分配权限（整体替换） */
  assignPermissions(id: number, permissionIds: number[]) {
    return request.put<void>(`/admin/roles/${id}/permissions`, { permissionIds })
  },

  /** 查询角色下已分配的用户ID */
  userIds(id: number) {
    return request.get<number[]>(`/admin/roles/${id}/user-ids`)
  },

  /** 给角色分配用户（整体替换） */
  assignUsers(id: number, userIds: number[]) {
    return request.put<void>(`/admin/roles/${id}/users`, { userIds })
  }
}
