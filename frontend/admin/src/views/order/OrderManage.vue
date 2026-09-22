<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="query.orderNo"
        placeholder="订单号（精确匹配）"
        clearable
        style="width: 220px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <!-- 状态用文本输入枚举名：状态的权威清单在交易域，前端抄一份下拉就是第二个会漂移的地方 -->
      <el-input
        v-model="query.status"
        placeholder="状态（枚举名，如 PAID）"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button v-perm="'trade:order:list'" type="primary" @click="handleSearch">查询</el-button>
    </div>

    <!-- 全平台订单列表：全状态可见（含「待支付」） -->
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row as TradeOrderVO)">{{ row.orderNo }}</el-link>
        </template>
      </el-table-column>
      <el-table-column prop="storeName" label="店铺" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">{{ (row as TradeOrderVO).storeName || '-' }}</template>
      </el-table-column>
      <el-table-column label="顾客姓名" width="110">
        <template #default="{ row }">{{ (row as TradeOrderVO).address.receiverName }}</template>
      </el-table-column>
      <!-- 状态文案由域下发（商户端 / 管理端视角），前端不自造 -->
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
          <el-button v-perm="'trade:order:list'" link type="primary" @click="openDetail(row as TradeOrderVO)">
            详情
          </el-button>
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { orderApi } from '../../api/order'
import type { TradeOrderVO } from '../../api/order'

/** 筛选条件：文本输入用空串表示「不填」，发请求时归一成 null（后端 null / 空串都视为不筛） */
interface OrderQuery {
  pageNum: number
  pageSize: number
  orderNo: string
  status: string
}

const router = useRouter()

const loading = ref(false)
const records = ref<TradeOrderVO[]>([])
const total = ref(0)
const query = reactive<OrderQuery>({ pageNum: 1, pageSize: 10, orderNo: '', status: '' })

async function loadPage() {
  loading.value = true
  try {
    const data = await orderApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      orderNo: query.orderNo.trim() || null,
      status: query.status.trim() || null,
      // 平台侧筛选条件（storeId / customerId）契约里保留，本页不摆输入框，恒传 null = 不筛
      storeId: null,
      customerId: null
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

// ⚠ el-table 插槽把 row 声明成 EP 的 DefaultRow（表格组件非泛型，拿不到 :data 的行类型），
//   故在【模板调用处】断言一次（`row as TradeOrderVO`），函数本身保持强类型。
function openDetail(row: TradeOrderVO) {
  router.push(`/order/${row.orderNo}`)
}

onMounted(loadPage)
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
