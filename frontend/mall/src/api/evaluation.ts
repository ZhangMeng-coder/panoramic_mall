import { request } from './request'
import type { PageResult } from '../types/api'
import type {
  EvaluationItem,
  EvaluationPageQuery,
  EvaluationStat,
  EvaluationSubmitPayload
} from '../types/evaluation'

/**
 * 商品评价接口（经网关 `/mall/**` 前缀转发到 mall-bff 8085）。
 * 路径与方法**照契约表 docs/contracts/mall-bff.md 写，不照后端代码写**。
 * 权限串一律为空：C 端不接 RBAC。
 *
 * ⚠ 四条**不体现在方法签名里**的服务端口径，写页面时要照它来：
 * ① **C 端只读 + 只写自己的评价**：**没有回复入口**（回复是商户端的事），也没有改 / 删评价。
 * ② **分页固定时间倒序**（`create_time desc, id desc`）——页面**不提供排序**、也不传排序字段。
 * ③ **星级分布由 `stat` 单独取**：列表接口的出参里**没有**分布，别指望从分页结果里算出来
 *    （那只能算出「本页」的，而分布要的是该商品**全部**评价的）。
 * ④ **写评价的门禁在服务端**（订单必须是本人且已收货），不满足回 400 中文提示——
 *    页面只用 `evaluated` 标记不摆注定失败的按钮，**不重判**一遍状态。
 */
export const evaluationApi = {
  /**
   * 提交评价（一笔订单里的一个商品一条）。
   * ⚠ 重复提交同单同商品 → 后端 400「该商品已评价」（幂等由域侧唯一键保证），
   * 文案由拦截器统一弹出，页面不另写一份。
   */
  submit(payload: EvaluationSubmitPayload): Promise<void> {
    return request.post<void>('/mall/evaluations', payload)
  },

  /** 某商品的评价分页（时间倒序；每页条数由页面传，本端用 10） */
  page(query: EvaluationPageQuery): Promise<PageResult<EvaluationItem>> {
    return request.post<PageResult<EvaluationItem>>('/mall/evaluations/page', query)
  },

  /** 某商品的评价星级分布（1 ~ 5 星各多少人 + 总条数） */
  stat(spuId: number): Promise<EvaluationStat> {
    return request.get<EvaluationStat>(`/mall/evaluations/stat/${spuId}`)
  }
}
