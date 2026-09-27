import request from './request'

/**
 * 时间窗口取值，与后端 `com.panoramic.common.stats.StatsWindow` 同枚举（两端的页面契约共用它）。
 *
 * ⚠ 这是**页面级**枚举，只到端 BFF 为止：BFF 把它解析成显式的 `start` / `end` 再传给两个域，
 * 域接口**不收**窗口枚举、**不收**粒度（cross-cutting 第 25 条）。故本文件的枚举值必须与后端
 * `com.panoramic.common.stats.StatsWindow` **逐字同名**，改一处要同步另一处。
 *
 * ⚠ 本端与 admin 端的 `api/stats.ts` **各自持一份**（两个独立工程，不共享代码，同 `request.ts`）：
 * 窗口 / 粒度 / 折线点三个类型的定义**逐字相同**，改一处要同步改另一处；两个看板的指标集合不同，
 * 那是刻意的不对称。
 */
export type StatsWindow =
  | 'THIS_MONTH'
  | 'LAST_MONTH'
  | 'THIS_QUARTER'
  | 'LAST_QUARTER'
  | 'THIS_YEAR'
  | 'LAST_YEAR'
  | 'CUSTOM'

/**
 * 折线粒度，与后端 `com.panoramic.common.stats.StatsGrain` 同枚举。
 *
 * ⚠ **别在页面里按窗口天数重算这条规则**（≤180 天按天，>180 天按月）：那会让规则有第二处实现、
 * 各自漂移。响应给了哪个粒度就按哪个格式化 x 轴标签。
 */
export type StatsGrain = 'DAY' | 'MONTH'

/**
 * 看板查询参数，与 store-bff 的 `StoreStatsQueryDTO` 同构。
 *
 * ⚠ **没有 `storeId`**：作用域由 store-bff 按登录店主（`type=store` 的登录 id）无条件写进域入参，
 * 页面无权选择看哪家店（cross-cutting 第 22 条）。
 * ⚠ `start` / `end` **只在 `window === 'CUSTOM'` 时该传**，且两者都必填；其余窗口传了会被 BFF 忽略。
 */
export interface StoreStatsQuery {
  /** 不传 = 本月（后端与页面默认值同口径） */
  window: StatsWindow
  /** 自定义窗口起点（`YYYY-MM-DD`）；仅 CUSTOM 用 */
  start?: string
  /** 自定义窗口终点（`YYYY-MM-DD`）；仅 CUSTOM 用 */
  end?: string
}

/**
 * 折线图的一个数据点，与后端 `com.panoramic.common.stats.StatsPointVO` 同构。
 *
 * ⚠ 窗口内**每个桶都有点**（没数据的桶是 0，不是缺项）——零填充在 BFF 做，页面不需要自己补；
 * 未开店 / 窗口内无单时是一串 0，**不是空数组**。
 * ⚠ `date` 的语义随 `grain` 变：按天是那一天，按月是**那个月的 1 号**。
 */
export interface StatsPoint {
  /** 桶标识（按天 = 当天；按月 = 当月 1 号） */
  date: string
  count: number
}

/** 评价星级分布的一行，与 store 域 `StoreGoodsEvaluationScoreCountVO` 同构 */
export interface EvaluationScoreCount {
  /** 星级（1 ~ 5） */
  score: number
  /** 该星级的评价条数 */
  count: number
}

/**
 * 店主端首页看板八个指标，与 store-bff 的 `StoreStatsVO` 同构。
 *
 * ⚠ 前三个是**当前累计快照**，不受时间窗口影响；其余五个按窗口算。
 * ⚠ 三个快照指标**粒度不同、不可相加不可比**：上架 / 下架数的是 **SPU**、库存异常数的是 **SKU 行**。
 */
export interface StoreStatsOverview {
  /** 上架商品数（快照 · SPU 口径） */
  onShelfCount: number
  /** 下架商品数（快照 · SPU 口径） */
  offShelfCount: number
  /** 库存异常数（快照 · SKU 行口径：库存归零、或已跌破 / 触及预警阈值；未设阈值且为 0 也算） */
  abnormalStockCount: number
  /** 总营业额（窗口 · 按支付时间归属 · 已扣退款；元） */
  revenue: number
  /** 成交订单数量（窗口 · 按支付时间落窗口、按当前状态判定为已完成） */
  dealOrderCount: number
  /**
   * 成交订单比例（**比例 0~1，不是百分数**；乘 100 与「%」是本页的展示格式）。
   *
   * ⚠ **`null` 与 `0` 含义不同**：`null` = 窗口内没有已支付订单（分母为 0，页面展示「—」）、
   * `0` = 有分母但一笔都没成交（真的 0%）。⚠ 不得把 `null` 显示成 0%。
   */
  dealOrderRatio: number | null
  /** 评价星级分布（窗口 · 固定 1~5 五行升序，无评价的星级占位 0） */
  evaluationScores: EvaluationScoreCount[]
  /** 折线粒度（x 轴标签按它格式化，别自己按天数重算） */
  grain: StatsGrain
  /** 订单数量折线（按**下单时刻**分桶；与营业额的时间基准刻意不同） */
  orderSeries: StatsPoint[]
}

/**
 * 首页看板 API（store-bff 编排，经网关 `/store/**` 前缀转发到 8084）。
 * 路径与方法**照契约表 docs/contracts/store-bff.md 写，不照后端代码写**。
 *
 * ⚠ **无权限串**：本端不接 RBAC（登录态是唯一门槛），没有权限串可挂（别照 admin 端补 `v-perm`）。
 * ⚠ 本接口**不套审核门禁**：首页不在「开店后业务入口」那组路由里，未开店 / 未过审也打得开，
 * 各计数回 0、折线回一串 0——页面**不要**为「无店」另做分支或错误提示。
 */
export const statsApi = {
  /** 首页看板（窗口缺省 = 本月；自定义窗口的起止都必填，非法由 BFF 回 400） */
  overview(params: StoreStatsQuery) {
    return request.get<StoreStatsOverview>('/store/stats/overview', { params })
  }
}
