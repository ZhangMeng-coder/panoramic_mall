import request from './request'
import type { PermissionNode } from '../types/auth'

/** 权限新增请求体，与后端 `PermissionSaveDTO` 同构（perms/icon/route 可空：目录可不填） */
export interface PermissionPayload {
  /** 父权限 ID，0 表示顶级（仅目录） */
  parentId: number
  name: string
  /** 类型：1=目录，2=页面，3=按钮（子类型必须等于父类型 +1） */
  type: number
  perms?: string
  icon?: string
  route?: string
  sort?: number
}

/** 权限编辑请求体，与后端 `PermissionUpdateDTO` 同构（父级与类型不可变更） */
export type PermissionUpdatePayload = Omit<PermissionPayload, 'parentId' | 'type'>

/**
 * 权限管理 API（admin 服务）
 */
export const permissionApi = {
  /** 全量权限树（含按钮，权限管理页用） */
  tree() {
    return request.get<PermissionNode[]>('/admin/permissions/tree')
  },

  /**
   * 前端目录接口（仅目录+页面，页面含路由地址 route）。
   * 按当前登录用户角色过滤返回其可见菜单。
   */
  menus() {
    return request.get<PermissionNode[]>('/admin/permissions/menus')
  },

  /** 权限详情 */
  detail(id: number) {
    return request.get<PermissionNode>(`/admin/permissions/${id}`)
  },

  /** 新建权限 → 新权限 ID */
  add(data: PermissionPayload) {
    return request.post<number>('/admin/permissions', data)
  },

  /** 更新权限（仅名称/权限字符串/图标/路由地址/排序，父级与类型不可变更） */
  update(id: number, data: PermissionUpdatePayload) {
    return request.put<void>(`/admin/permissions/${id}`, data)
  },

  /** 删除权限 */
  remove(id: number) {
    return request.delete<void>(`/admin/permissions/${id}`)
  }
}
