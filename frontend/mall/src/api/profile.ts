import { request } from './request'
import type { ProfilePayload } from '../types/profile'

/**
 * 顾客资料接口（经网关 /mall/** 前缀转发到 mall-bff 8085）。
 * 路径与方法**照契约表 docs/contracts/mall-bff.md 写，不照后端代码写**。
 * 权限串一律为空：C 端不接 RBAC。
 *
 * ⚠ 资料**没有** `GET`：读走 `/auth/me`（出参含完整资料，并入 `authApi.me()`）；
 * 换绑手机号在 `authApi`（它的路径前缀也是 `/auth/**`，属顾客账号接口）——本模块只写 `PUT /profile`。
 */
export const profileApi = {
  /** 保存资料：**整份替换**（四项全传，未传即写为 NULL），详情见 `ProfilePayload` 注释 */
  save(payload: ProfilePayload): Promise<void> {
    return request.put<void>('/mall/profile', payload)
  }
}
