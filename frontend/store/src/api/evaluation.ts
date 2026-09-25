import request from './request'
import type { PageQuery, PageResult } from '../types/api'
import type { SpecAttr } from './goods'

/** 评价的 SKU 快照条目，与 store 域 `StoreGoodsEvaluationSkuVO` 同构（下单当时的事实，此后改价 / 删 SKU 都不改写） */
export interface EvaluationSkuSnapshot {
  /** SKU id（历史快照，不保证该 SKU 仍在） */
  skuId: number
  /** 规格组合；无规格为 **null 也可能是空数组**，判空请用 falsy */
  specAttrs: SpecAttr[] | null
  unitPrice: number
  quantity: number
}

/**
 * 本店评价列表行，与 store-bff 的 `StoreEvaluationItemVO` 同构。
 *
 * ⚠ **商品名与评价人展示名非空**：两者由 store-bff 兜底后下发——商品已软删 → 「商品已删除」、
 * 评价人资料取不到 → 「用户」；页面照常渲染，**不要**再判空造第二份兜底文案。
 * ⚠ **没有星级分布字段**：商户端要的是**筛选**（入参 `score`），不是分布（分布是 C 端商品详情页的）。
 * ⚠ **`replyContent` 为 null 即未回复**：回复**一条评价至多一个**，且**不可改、不可删**
 * （页面对已回复的行只给「查看」、不给编辑入口）。
 */
export interface StoreEvaluationItem {
  /** 评价 id（回复入口的路径标识） */
  id: number
  /** 被评价的商品 SPU id */
  spuId: number
  /** 商品名称（已软删时为 store-bff 的兜底文案） */
  spuName: string
  /** 下单时的 SKU 快照（该 SPU 在本单里的全部 SKU 行组） */
  skuSnapshot: EvaluationSkuSnapshot[]
  /** 评分：1 ~ 5 星 */
  score: number
  /** 评价文字（可空） */
  content: string | null
  /** 评价人展示名（取不到资料时为占位「用户」） */
  nickname: string
  /** 评价人头像 URL；无头像 / 取不到资料为 null，前端不渲染 */
  avatar: string | null
  createTime: string
  /** 商家回复内容；null = 未回复 */
  replyContent: string | null
  /** 商家回复时间；null = 未回复 */
  replyTime: string | null
}

/**
 * 本店评价分页查询参数，与 store-bff 的 `StoreEvaluationPageQueryDTO` 同构。
 *
 * ⚠ **没有 `storeId`**：作用域由 store-bff 按登录店主（`type=store` 的登录 id）无条件写入域入参，
 * 页面无权选择看哪家店（cross-cutting 第 22 条）。
 * ⚠ **星级是单个值**（页面是「全部 / 5 星 / …」单选），域侧入参才是集合 `scores`——
 * 由 store-bff 转成单元素集合，页面**不传数组**。
 */
export interface EvaluationPageQuery extends PageQuery {
  /** 商品 SPU id；null = 不筛 */
  spuId: number | null
  /** 星级筛选（1 ~ 5）；null = 全部。取值越界由 store-bff 回 400 */
  score: number | null
}

/** 回复请求体，与 store-bff 的 `StoreEvaluationReplyDTO` 同构（`storeId` 由服务端从登录态取） */
export interface EvaluationReplyPayload {
  replyContent: string
}

/**
 * 本店评价 API（经网关 `/store/evaluations/**` 转发到 store-bff 8084）。
 * 路径与方法**照契约表 docs/contracts/store-bff.md 写，不照后端代码写**。
 * ⚠ 本端不接 RBAC，故没有权限串可挂（别照 admin 端补 `v-perm`）。
 */
export const evaluationApi = {
  /** 本店评价分页（可按商品 + 星级筛选；固定时间倒序，页面不提供排序） */
  page(params: EvaluationPageQuery) {
    return request.get<PageResult<StoreEvaluationItem>>('/store/evaluations/page', { params })
  },

  /**
   * 回复评价（一条评价至多一个回复）。
   * ⚠ **回复不可改、不可删**：重复回复由域侧回 400「该评价已回复」，文案由拦截器统一弹出，页面不另写一份。
   */
  reply(id: number, data: EvaluationReplyPayload) {
    return request.post<void>(`/store/evaluations/${id}/reply`, data)
  }
}
