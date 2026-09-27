<template>
  <div v-loading="loading" class="home">
    <!-- ① 快照三项：当前累计，不受时间窗口影响 -->
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">上架商品数量</div>
          <div class="stat-value">{{ formatCount(stats?.onShelfCount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">下架商品数量</div>
          <div class="stat-value">{{ formatCount(stats?.offShelfCount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <!-- ⚠ 它与库存页「仅看低库存」不是一个判据（多了「未设阈值且库存为 0」那一支） -->
          <div class="stat-label">
            <el-tooltip content="库存归零、或已跌破 / 触及预警阈值的 SKU 数（与库存页「仅看低库存」口径不同）" placement="top">
              <span>库存异常数量</span>
            </el-tooltip>
          </div>
          <div class="stat-value">{{ formatCount(stats?.abnormalStockCount) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ② 时间窗口：从总营业额开始的所有指标都按它统计 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter">
        <span class="filter-label">时间窗口</span>
        <el-select v-model="timeWindow" style="width: 130px" @change="load">
          <el-option v-for="option in WINDOW_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <!-- 自定义窗口：选完一整个区间才发请求（后端要求起止同时给出） -->
        <el-date-picker
          v-if="timeWindow === 'CUSTOM'"
          v-model="customRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          unlink-panels
          @change="onCustomRangeChange"
        />
        <span class="filter-hint">上方三项为当前累计快照，不受此窗口影响</span>
      </div>
    </el-card>

    <!-- ③ 窗口内指标三项 -->
    <el-row :gutter="16">
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">总营业额</div>
          <div class="stat-value">{{ formatAmount(stats?.revenue) }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <div class="stat-label">成交订单数量</div>
          <div class="stat-value">{{ formatCount(stats?.dealOrderCount) }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <!-- ⚠ null（窗口内没有已支付订单）展示「—」，不是 0% —— 两者含义不同 -->
          <div class="stat-label">
            <el-tooltip content="成交订单数 ÷ 窗口内已支付订单数" placement="top">
              <span>成交订单比例</span>
            </el-tooltip>
          </div>
          <div class="stat-value">{{ formatRatio(stats?.dealOrderRatio) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ④ 评价分布 + 订单折线；折线 x 轴标签按后端回的 grain 格式化，本页不按天数重算粒度 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <div class="chart-title">评价分布</div>
          <div ref="scoreChartRef" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <div class="chart-title">订单数量</div>
          <div ref="orderChartRef" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption, EChartsType } from 'echarts'
import { statsApi } from '../../api/stats'
import type {
  EvaluationScoreCount,
  StatsGrain,
  StatsPoint,
  StatsWindow,
  StoreStatsOverview,
  StoreStatsQuery
} from '../../api/stats'

// 按需注册（不引整包）：本页要柱状图 + 折线图 + 直角坐标系 + 悬浮提示 + Canvas 渲染
echarts.use([BarChart, LineChart, GridComponent, TooltipComponent, CanvasRenderer])

/** 窗口下拉选项；值必须与后端 `common` 的 `StatsWindow` 逐字同名（文案是展示层的事，只在这） */
const WINDOW_OPTIONS: { value: StatsWindow; label: string }[] = [
  { value: 'THIS_MONTH', label: '本月' },
  { value: 'LAST_MONTH', label: '上月' },
  { value: 'THIS_QUARTER', label: '本季' },
  { value: 'LAST_QUARTER', label: '上季' },
  { value: 'THIS_YEAR', label: '今年' },
  { value: 'LAST_YEAR', label: '去年' },
  { value: 'CUSTOM', label: '自定义' }
]

/** 星级柱与订单折线的配色 */
const SCORE_BAR_COLOR = '#e6a23c'
const ORDER_SERIES_COLOR = '#409eff'

const loading = ref(false)
const stats = ref<StoreStatsOverview | null>(null)
/** ⚠ 不叫 `window`：那会遮蔽全局 `window`（本组件底部要拿它挂 resize） */
const timeWindow = ref<StatsWindow>('THIS_MONTH')
/** 自定义窗口的起止（`YYYY-MM-DD`）；只在 `timeWindow === 'CUSTOM'` 时发出去 */
const customRange = ref<string[] | null>(null)

const scoreChartRef = ref<HTMLDivElement | null>(null)
const orderChartRef = ref<HTMLDivElement | null>(null)
let scoreChart: EChartsType | null = null
let orderChart: EChartsType | null = null

/**
 * 组装查询参数；**自定义窗口只选了一端时返回 null**（不发请求）。
 *
 * ⚠ 起止是**日期串**不是时刻：后端（域侧）自己把上界取成 `end` 次日 00:00，页面**不**给 `23:59:59`。
 */
function buildQuery(): StoreStatsQuery | null {
  if (timeWindow.value === 'CUSTOM') {
    const range = customRange.value
    if (!range || range.length !== 2) {
      return null
    }
    return { window: timeWindow.value, start: range[0], end: range[1] }
  }
  return { window: timeWindow.value }
}

async function load(): Promise<void> {
  const query = buildQuery()
  if (!query) {
    return
  }
  loading.value = true
  try {
    stats.value = await statsApi.overview(query)
    // 图表容器此刻可能还是空的（首次加载），等渲染完再 init
    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

/** 自定义区间选完（或清空）时触发；只选了一端就不发请求，等用户选全 */
function onCustomRangeChange(): void {
  if (customRange.value && customRange.value.length === 2) {
    load()
  }
}

function renderCharts(): void {
  const data = stats.value
  if (!data || !scoreChartRef.value || !orderChartRef.value) {
    return
  }
  // 实例只建一次：每次 setOption 覆盖数据，不重建（重建会丢动画状态并泄漏旧实例）
  scoreChart = scoreChart ?? echarts.init(scoreChartRef.value)
  orderChart = orderChart ?? echarts.init(orderChartRef.value)
  scoreChart.setOption(buildScoreOption(data.evaluationScores))
  orderChart.setOption(buildOrderOption(data.orderSeries, data.grain))
}

/**
 * 评价分布：**固定五根柱**（星级 1~5）。
 *
 * ⚠ 域侧恒回 5 行、无评价的星级补 0，故这里**不补缺失星级**——照序画就行；
 * 也不按 `score` 排序（域侧口径就是升序，页面重排等于有第二处排序规则）。
 */
function buildScoreOption(scores: EvaluationScoreCount[]): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 24, bottom: 4, containLabel: true },
    xAxis: { type: 'category', data: scores.map((item) => `${item.score} 星`) },
    // minInterval: 1 —— 计数是整数，别让坐标轴出现 0.5 这种刻度
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '评价条数',
        type: 'bar',
        barMaxWidth: 48,
        itemStyle: { color: SCORE_BAR_COLOR },
        data: scores.map((item) => item.count)
      }
    ]
  }
}

/** 订单数量折线：每个桶都有点（窗口零填充在 BFF 做），按 grain 格式化 x 轴 */
function buildOrderOption(points: StatsPoint[], grain: StatsGrain): EChartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 16, top: 24, bottom: 4, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((point) => formatBucket(point.date, grain))
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '订单数量',
        type: 'line',
        smooth: true,
        // 按天出点时最长 180 个点，圆点会糊成一条带子，故点多时隐掉
        showSymbol: points.length <= 40,
        areaStyle: { opacity: 0.12 },
        itemStyle: { color: ORDER_SERIES_COLOR },
        lineStyle: { color: ORDER_SERIES_COLOR },
        data: points.map((point) => point.count)
      }
    ]
  }
}

/** x 轴标签：按天取 `MM-DD`、按月取 `YYYY-MM`（后端给的是 ISO 日期串，取前缀即可） */
function formatBucket(date: string, grain: StatsGrain): string {
  return grain === 'DAY' ? date.slice(5) : date.slice(0, 7)
}

function formatCount(value: number | undefined): string {
  return value === undefined ? '—' : value.toLocaleString('zh-CN')
}

function formatAmount(value: number | undefined): string {
  if (value === undefined) {
    return '—'
  }
  return `¥ ${value.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
}

/** 比例（0~1）→ 百分数；**null 回「—」**（分母为 0，与「真的 0%」不同） */
function formatRatio(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—'
  }
  return `${(value * 100).toFixed(1)}%`
}

/** 侧栏折叠 / 窗口缩放都会改变容器宽度，图表要跟着重算尺寸 */
function handleResize(): void {
  scoreChart?.resize()
  orderChart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  scoreChart?.dispose()
  orderChart?.dispose()
  scoreChart = null
  orderChart = null
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card {
  --el-card-padding: 12px 20px;
}

.filter {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.filter-label {
  color: var(--el-text-color-regular);
}

.filter-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
}

.chart {
  height: 300px;
}
</style>
