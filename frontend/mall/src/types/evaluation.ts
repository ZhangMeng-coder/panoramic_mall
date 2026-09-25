/* ============================================================================
   商品评价 —— 与契约 docs/contracts/mall-bff.md 的 `/evaluations` 三行一致。
   字段定义以后端 `MallEvaluationItemVO` / `MallEvaluationPageQueryDTO` /
   `MallEvaluationSubmitDTO`（mall-bff 的 vo / dto 包）与域类型
   `StoreGoodsEvaluationStatVO` 为准，这里只是前端侧的镜像（不重复写业务规则）。
   ========================================================================== */

import type { SpecAttr } from './catalog'

/**
 * 评价里的一条 SKU 快照（下单那一刻的规格 / 单价 / 数量）。
 * ⚠ 它是**快照不是引用**：商品改价、SKU 被删都不改写历史评价的展示。
 * ⚠ **是数组**：一笔评价对应一个 SPU，但同单里该 SPU 可能有多个 SKU 行，全部带进来。
 */
export interface EvaluationSkuSnapshot {
  skuId: number
  /** 规格组合（下单当时的快照）；无规格商品为空数组 */
  specAttrs: SpecAttr[] | null
  /** 下单时单价（元） */
  unitPrice: number
  /** 下单数量 */
  quantity: number
}

/**
 * 评价列表项（对应后端 `MallEvaluationItemVO`）。
 * ⚠ **没有 `customerId`**：那是顾客账号 id（内部标识），后端已把它换成 `nickname` / `avatar`。
 * ⚠ `nickname` **非空**（后端兜底占位「用户」）；`avatar` 可空，空即不渲染头像图。
 */
export interface EvaluationItem {
  /** 评价 id */
  id: number
  /** 被评价的商品 SPU id */
  spuId: number
  skuSnapshot: EvaluationSkuSnapshot[] | null
  /** 评分：1 ~ 5 星 */
  score: number
  /** 评价文字；**可空**（只打星不写字） */
  content: string | null
  /** 评价人展示名（后端保证非空） */
  nickname: string
  /** 评价人头像 URL；可空 */
  avatar: string | null
  /** 评价时间（后端 `LocalDateTime` 的 ISO 串；展示走 `formatDateTime`） */
  createTime: string
  /** 商家回复内容；**`null` = 未回复**（未回复就整块不渲染） */
  replyContent: string | null
  /** 商家回复时间；未回复为 `null` */
  replyTime: string | null
}

/** 某一星级的评价条数 */
export interface EvaluationScoreCount {
  score: number
  count: number
}

/**
 * 评价星级分布（对应域类型 `StoreGoodsEvaluationStatVO`，后端原样下发）。
 * ⚠ `scores` **恒为 5 项、按 1 → 5 升序**，没有人打的星级也占位补 0 ——
 * 页面直接画五条即可，**不要**自己补缺项（补了反而会在「某星级恰好没人打」时错位）。
 */
export interface EvaluationStat {
  /** 总条数（= 各星级条数之和） */
  total: number
  scores: EvaluationScoreCount[]
}

/**
 * 评价分页查询（对应后端 `MallEvaluationPageQueryDTO`）。
 * ⚠ **没有星级筛选**：C 端要的是**分布**（见 {@link EvaluationStat}），不要筛选——
 * 星级筛选是**商户端**的需求（商户按星级翻差评）。两条看似不对称是刻意的：分布是展示、筛选是操作。
 * ⚠ 每页条数**由页面传**（域侧是通用分页，不写死 10）。
 */
export interface EvaluationPageQuery {
  spuId: number
  pageNum: number
  pageSize: number
}

/**
 * 提交评价（对应后端 `MallEvaluationSubmitDTO`）。
 * ⚠ **不含 `customerId` / `skuSnapshot`**：顾客身份由后端从登录态取；
 * SKU 快照由后端按 `spuId` 从**订单明细**归组（页面手上没有可信的快照）。
 * ⚠ 「订单已完成」是**业务前置条件**（后端经 trade-center 校验，不满足回 400）——页面不重判。
 */
export interface EvaluationSubmitPayload {
  orderNo: string
  spuId: number
  /** 1 ~ 5 星 */
  score: number
  /** 评价文字（可空；最长 500 字） */
  content?: string
}
