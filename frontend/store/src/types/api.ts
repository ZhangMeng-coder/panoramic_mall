/**
 * 接口层公共形状：只放「被 ≥2 处共用」的通用外壳。
 * 单个接口私有的请求体 / 返回体，就近声明在各自的 `api/*.ts` 里并 export（供页面 import）。
 *
 * ⚠ 这里只写外壳，不抄业务字段 —— 业务字段以后端 VO/DTO 为准，抄一份就是制造第二个会漂移的地方。
 */

/** 后端统一响应外壳，与 common 的 `RespData` 同构；`code === 200` 为成功 */
export interface RespData<T> {
  code: number
  msg: string
  data: T
}

/** 分页结果，与 common 的 `PageResult` 同构 */
export interface PageResult<T> {
  /** 总记录数 */
  total: number
  /** 当前页记录 */
  records: T[]
}

/** 分页查询基参，与 common 的 `BasePageVO` 同构 */
export interface PageQuery {
  pageNum: number
  pageSize: number
}
