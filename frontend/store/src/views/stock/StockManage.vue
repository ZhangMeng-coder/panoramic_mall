<template>
  <el-card shadow="never">
    <!-- 筛选区 -->
    <el-form inline class="filter-form">
      <el-form-item label="关键词">
        <el-input
          v-model="query.keyword"
          placeholder="商品名 / SKU 编码"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.shelfStatus" clearable placeholder="全部状态" style="width: 120px">
          <el-option label="上架中" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-checkbox v-model="query.lowStockOnly">仅看低库存</el-checkbox>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 批量工具栏：有勾选才出现 -->
    <div v-if="selectedIds.length" class="stock-toolbar">
      <span class="stock-toolbar__hint">已选 {{ selectedIds.length }} 项</span>
      <el-button type="primary" plain size="small" @click="handleBatchSet">批量设置库存</el-button>
    </div>

    <!-- 库存列表：库存 / 预警值可就地改，改完即时提交 -->
    <el-table
      v-loading="loading"
      :data="records"
      :row-class-name="rowClassName"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="46" />
      <el-table-column prop="spuName" label="商品名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="规格" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">{{ specText(row as StockRow) }}</template>
      </el-table-column>
      <el-table-column label="SKU 编码" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ (row as StockRow).skuCode || '—' }}</template>
      </el-table-column>
      <el-table-column label="价格（元）" width="100">
        <template #default="{ row }">{{ (row as StockRow).price }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="(row as StockRow).shelfStatus === 1 ? 'success' : 'info'">
            {{ (row as StockRow).shelfStatus === 1 ? '上架中' : '已下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="库存" width="120">
        <template #default="{ row }">
          <el-input-number
            v-model="row.stock"
            :min="0"
            :controls="false"
            :disabled="saving.has((row as StockRow).skuId)"
            style="width: 100%"
            @change="() => onStockChange(row as StockRow)"
          />
        </template>
      </el-table-column>
      <!-- 预警值：清空即取消预警（value-on-clear 传 null，与后端「warnStock 为空 = 清除预警」对齐） -->
      <el-table-column label="预警值" width="120">
        <template #default="{ row }">
          <el-input-number
            v-model="row.warnStock"
            :min="0"
            :controls="false"
            :value-on-clear="null"
            :disabled="saving.has((row as StockRow).skuId)"
            placeholder="不预警"
            style="width: 100%"
            @change="() => onStockChange(row as StockRow)"
          />
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      class="pager"
      layout="total, prev, pager, next, sizes"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      @current-change="loadPage"
      @size-change="loadPage"
    />
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { stockApi } from '../../api/stock'
import type { StockRow } from '../../api/stock'

/** 筛选条件：状态「全部」为 null（与不传等效，仅用于拼查询参数） */
interface StockQuery {
  pageNum: number
  pageSize: number
  keyword: string
  shelfStatus: number | null
  lowStockOnly: boolean
}

const loading = ref(false)
const records = ref<StockRow[]>([])
const total = ref(0)
/** 正在提交改动的 SKU（同一行请求在飞时禁用输入，避免重复提交） */
const saving = ref<Set<number>>(new Set())

const query = reactive<StockQuery>({
  pageNum: 1,
  pageSize: 10,
  keyword: '',
  shelfStatus: null,
  lowStockOnly: false
})

const selectedRows = ref<StockRow[]>([])
const selectedIds = computed(() => selectedRows.value.map((r) => r.skuId))

async function loadPage() {
  loading.value = true
  try {
    const data = await stockApi.page(query)
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadPage()
}

function handleReset() {
  query.keyword = ''
  query.shelfStatus = null
  query.lowStockOnly = false
  handleSearch()
}

function handleSelectionChange(rows: StockRow[]) {
  selectedRows.value = rows
}

/** 规格展示：`规格:取值` 用 `/` 连接；无规格组合显示占位符 */
function specText(row: StockRow) {
  const attrs = row.specAttrs || []
  if (!attrs.length) return '—'
  return attrs.map((a) => `${a.spec}:${a.value}`).join(' / ')
}

/** 低库存：设了预警阈值且总库存已降到阈值（含）以下 */
function isLowStock(row: StockRow) {
  const warn = row.warnStock
  return warn !== null && warn !== undefined && row.stock <= warn
}

function rowClassName(data: { row: StockRow }) {
  return isLowStock(data.row) ? 'stock-row--low' : ''
}

/**
 * 就地改库存 / 预警值：改动即时提交（`stock` 与 `warnStock` 一起提交，未改的那个原值回传）。
 * 失败时把该行两个值都回滚成提交前的快照（后端拒改时页面上不留假值）。
 */
async function onStockChange(row: StockRow) {
  if (saving.value.has(row.skuId)) return
  const snapshot = { stock: row.stock, warnStock: row.warnStock }
  saving.value.add(row.skuId)
  try {
    await stockApi.update(row.skuId, { stock: row.stock, warnStock: row.warnStock })
    ElMessage.success('已更新')
  } catch {
    row.stock = snapshot.stock
    row.warnStock = snapshot.warnStock // 拦截器已提示
  } finally {
    saving.value.delete(row.skuId)
  }
}

/** 批量设置：把选中 SKU 的总库存统一设为同一值（不动预警值），成功后刷新列表 */
async function handleBatchSet() {
  const skuIds = selectedIds.value
  if (!skuIds.length) return
  let stock: number
  try {
    const { value } = await ElMessageBox.prompt(
      `将选中的 ${skuIds.length} 个 SKU 的总库存统一设为：`,
      '批量设置库存',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /^\d+$/,
        inputErrorMessage: '请输入不小于 0 的整数',
        inputPlaceholder: '0'
      }
    )
    stock = Number(value)
  } catch {
    return // 用户取消
  }
  try {
    await stockApi.batchUpdate({ skuIds, stock })
    ElMessage.success('已更新')
    loadPage()
  } catch {
    // 拦截器已提示
  }
}

onMounted(loadPage)
</script>

<style scoped>
.filter-form {
  margin-bottom: 4px;
}

.filter-form .el-form-item {
  margin-bottom: 8px;
}

.stock-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-2);
}

.stock-toolbar__hint {
  font-size: var(--text-xs);
  color: var(--color-text-muted);
}

.pager {
  margin-top: var(--space-3);
  justify-content: flex-end;
}

/* 低库存行整行淡警示底（选择器需盖过 EP 自身的 td 底色规则） */
:deep(.el-table__body tr.stock-row--low > td.el-table__cell) {
  background-color: var(--color-warning-soft);
}
</style>
