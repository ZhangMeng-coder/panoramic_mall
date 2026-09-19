/* ============================================================================
   顾客资料 —— 与契约 docs/contracts/mall-bff.md 的 `PUT /profile` 一致。
   字段定义以后端 `ProfileSaveDTO` 为准，这里只是前端侧的镜像。
   ========================================================================== */

/**
 * 资料保存请求体（对齐 ProfileSaveDTO）。
 *
 * ⚠ 写口径是**整份替换**、不是增量更新：四项每次都全传，**未传的字段会被写成 NULL**。
 * 故前端**不要**做「非 null 才传」的过滤——那会让「清空头像 / 清空生日」这类操作静默失效
 * （值仍在库里，页面却以为清掉了）。要「只改一个字段」，得先读 `/auth/me` 拿到完整资料再整体回传。
 */
export interface ProfilePayload {
  /** 昵称，≤50 字符 */
  nickname: string | null
  /** 头像 URL，≤255 字符 */
  avatar: string | null
  /** 性别：0 未知 / 1 男 / 2 女（越界后端回 400） */
  gender: number | null
  /** 生日，JSON 里即 `YYYY-MM-DD` 字符串（后端 LocalDate，已关时间戳序列化） */
  birthday: string | null
}
