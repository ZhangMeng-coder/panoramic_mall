/**
 * 登录态形状（admin 身份空间，JWT `type=admin`）。
 * 字段照后端 `CurrentUserVO` / `LoginResultVO` / `PermissionTreeVO`；
 * 可空性照 `sys_user` / `sys_permission` 的列定义（可空列一律 `| null`，不假装成非空）。
 */

/** 当前登录用户（登录返回的 user 与 `/admin/auth/me` 同一个形状） */
export interface CurrentUser {
  id: number
  username: string
  /** 昵称（`sys_user.nickname` 可空，页面自行回落 username） */
  nickname: string | null
  /** 头像 URL（`sys_user.avatar` 可空） */
  avatar: string | null
  /** 权限串集合（后端 `List.copyOf`，恒非空） */
  perms: string[]
}

/** 登录结果 */
export interface LoginResult {
  token: string
  user: CurrentUser
}

/**
 * 菜单 / 权限树节点（`/admin/permissions/tree` 与 `/admin/permissions/menus` 共用），
 * 照后端 `PermissionTreeVO`。
 */
export interface PermissionNode {
  id: number
  /** 父权限 ID，0 表示顶级 */
  parentId: number
  name: string
  /** 类型：1=目录，2=页面，3=按钮（与后端 `PermissionServiceImpl` 的层级规则一致） */
  type: number
  /** 权限串（目录可空） */
  perms: string | null
  /** 图标（可空） */
  icon: string | null
  /** 路由地址（仅页面 type=2 有） */
  route: string | null
  sort: number
  children: PermissionNode[]
}
