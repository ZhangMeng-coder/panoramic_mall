<template>
  <el-card shadow="never">
    <!-- 筛选区 -->
    <el-form inline class="filter-form">
      <el-form-item label="订单号">
        <el-input
          v-model="query.orderNo"
          placeholder="订单号（精确匹配）"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </el-form-item>
      <!-- 状态用文本输入枚举名：状态的权威清单在交易域，前端抄一份下拉就是第二个会漂移的地方 -->
      <el-form-item label="状态">
        <el-input
          v-model="query.status"
          placeholder="状态（枚举名，如 PAID）"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 本店订单列表：全状态可见（含「待支付」） -->
    <el-table v-loading="loading" :data="records">
      <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="storeName" label="店铺" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ (row as TradeOrderVO).storeName || '—' }}</template>
      </el-table-column>
      <el-table-column label="顾客姓名" width="110">
        <template #default="{ row }">{{ (row as TradeOrderVO).address.receiverName }}</template>
      </el-table-column>
      <!-- 状态文案由域下发（商户端视角），前端不自造 -->
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag effect="plain">{{ (row as TradeOrderVO).statusStoreAdminLabel }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="totalQuantity" label="件数" width="80" align="center" />
      <el-table-column label="金额（元）" width="120" align="right">
        <template #default="{ row }">{{ (row as TradeOrderVO).totalAmount }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="下单时间" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row as TradeOrderVO)">详情</el-button>
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

    <OrderDetailDialog v-model="detailVisible" :order-no="currentOrderNo" @shipped="loadPage" />
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { orderApi } from '../../api/order'
import type { TradeOrderVO } from '../../api/order'
import OrderDetailDialog from './OrderDetailDialog.vue'

/** 筛选条件：文本输入用空串表示「不填」，发请求时归一成 null（后端 null / 空串都视为不筛） */
interface OrderQuery {
  pageNum: number
  pageSize: number
  orderNo: string
  status: string
}

const loading = ref(false)
const records = ref<TradeOrderVO[]>([])
const total = ref(0)

const query = reactive<OrderQuery>({
  pageNum: 1,
  pageSize: 10,
  orderNo: '',
  status: ''
})

// 详情弹窗：store 的侧栏按 route.path 高亮（不认 meta.activeMenu），故详情不做成独立路由、放弹窗里
const detailVisible = ref(false)
const currentOrderNo = ref<string | null>(null)

async function loadPage() {
  loading.value = true
  try {
    const data = await orderApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      orderNo: query.orderNo.trim() || null,
      status: query.status.trim() || null
    })
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
  query.orderNo = ''
  query.status = ''
  handleSearch()
}

function openDetail(row: TradeOrderVO) {
  currentOrderNo.value = row.orderNo
  detailVisible.value = true
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

.pager {
  margin-top: var(--space-3);
  justify-content: flex-end;
}
</style>
