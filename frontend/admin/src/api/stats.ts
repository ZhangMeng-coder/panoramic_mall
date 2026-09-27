import request from './request'

/**
 * 时间窗口取值，与后端 `com.panoramic.common.stats.StatsWindow` 同枚举（两端的页面契约共用它）。
 *
 * ⚠ 这是**页面级**枚举，只到端 BFF 为止：BFF 把它解析成显式的 `start` / `end` 再传给三个域，
 * 域接口**不收**窗口枚举、**不收**粒度（cross-cutting 第 25 条）。故本文件的枚举值必须与后端
 * `com.panoramic.common.stats.StatsWindow` **逐字同名**，改一处要同步另一处。
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
 * 看板查询参数，与 admin BFF 的 `AdminStatsQueryDTO` 同构。
 *
 * ⚠ `start` / `end` **只在 `window === 'CUSTOM'` 时该传**，且两者都必填；其余窗口传了会被 BFF 忽略
 * （后端把「非自定义窗口的起止」当无用参数丢弃，不是错误）。
 */
export interface StatsOverviewQuery {
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
 * ⚠ 窗口内**每个桶都有点**（没数据的桶是 0，不是缺项）——零填充在 BFF 做，页面不需要自己补。
 * ⚠ `date` 的语义随 `grain` 变：按天是那一天，按月是**那个月的 1 号**。
 */
export interface StatsPoint {
  /** 桶标识（按天 = 当天；按月 = 当月 1 号） */
  date: string
  count: number
}

/**
 * 首页看板八个指标，与 admin BFF 的 `AdminStatsVO` 同构。
 *
 * ⚠ 前三个是**当前累计快照**，不受时间窗口影响；其余五个按窗口算。
 * ⚠ `userCount` 口径是**近似**（数的是顾客资料行，不等于注册用户数），文案**不得**写成「注册用户数」。
 */
export interface StatsOverview {
  /** 用户数量（快照 · 顾客资料行计数 · **近似**） */
  userCount: number
  /** 商家数量（快照 · 审核已通过的店铺数） */
  shopCount: number
  /** 商家商品数量（快照 · 未删除的全部 SPU；与商家数互不对齐是正常的） */
  goodsCount: number
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
  /** 折线粒度（x 轴标签按它格式化，别自己按天数重算） */
  grain: StatsGrain
  /** 新增用户折线（按顾客资料行的创建时刻分桶） */
  userSeries: StatsPoint[]
  /** 新增订单折线（按**下单时刻**分桶；与营业额的时间基准刻意不同） */
  orderSeries: StatsPoint[]
}

/**
 * 首页看板 API（admin BFF 编排，经网关 `/admin/**` 前缀转发）。
 *
 * ⚠ **无权限串**：本接口与主页 `/home` 一样是「登录后必得」——主页刻意不入权限表，
 * 给本接口挂权限串会让没有该权限的管理员落到一个取不到数的首页。故本页**不挂 `v-perm`**
 * （与本端其它页面不同，别顺手补上）。
 */
export const statsApi = {
  /** 首页看板（窗口缺省 = 本月；自定义窗口的起止都必填，非法由 BFF 回 400） */
  overview(params: StatsOverviewQuery) {
    return request.get<StatsOverview>('/admin/stats/overview', { params })
  }
}
