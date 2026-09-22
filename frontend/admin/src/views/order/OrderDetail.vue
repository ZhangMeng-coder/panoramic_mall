<template>
  <el-card v-loading="loading" shadow="never">
    <template #header>
      <div class="detail-header">
        <div class="detail-header-left">
          <el-button link type="primary" @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <span class="detail-title">{{ order?.orderNo || '订单详情' }}</span>
          <el-tag v-if="order" effect="plain">{{ order.statusStoreAdminLabel }}</el-tag>
        </div>
      </div>
    </template>

    <template v-if="order">
      <!-- 基础信息 -->
      <div class="section-title">基础信息</div>
      <el-descriptions :column="3" border class="section-body">
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ order.statusStoreAdminLabel }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ order.createTime }}</el-descriptions-item>
        <el-descriptions-item label="店铺">{{ order.storeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="件数">{{ order.totalQuantity }}</el-descriptions-item>
        <el-descriptions-item label="金额（元）">{{ order.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="快递单号" :span="3">{{ order.shipNo || '-' }}</el-descriptions-item>
      </el-descriptions>

      <!-- 收货地址（下单当时的快照） -->
      <div class="section-title">收货地址</div>
      <el-descriptions :column="2" border class="section-body">
        <el-descriptions-item label="收件人">{{ order.address.receiverName }}</el-descriptions-item>
        <el-descriptions-item label="电话">{{ order.address.receiverPhone }}</el-descriptions-item>
        <el-descriptions-item label="所在地区" :span="2">{{ order.address.region }}</el-descriptions-item>
        <el-descriptions-item label="详细地址" :span="2">{{ order.address.detail }}</el-descriptions-item>
      </el-descriptions>

      <!-- 商品明细 -->
      <div class="section-title">商品明细</div>
      <div class="section-body">
        <el-table :data="order.items" border size="small">
          <el-table-column label="主图" width="90">
            <template #default="{ row }">
              <el-image
                v-if="itemImage(row as TradeOrderItem)"
                :src="itemImage(row as TradeOrderItem)"
                :preview-src-list="[itemImage(row as TradeOrderItem)]"
                preview-teleported
                fit="cover"
                class="item-img"
              />
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="goodsName" label="商品名称" min-width="180" show-overflow-tooltip />
          <el-table-column label="规格" min-width="170" show-overflow-tooltip>
            <template #default="{ row }">{{ specText(row as TradeOrderItem) }}</template>
          </el-table-column>
          <el-table-column label="单价（元）" width="110" align="right">
            <template #default="{ row }">{{ (row as TradeOrderItem).unitPrice }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" align="center" />
          <el-table-column label="小计（元）" width="110" align="right">
            <template #default="{ row }">{{ (row as TradeOrderItem).subtotal }}</template>
          </el-table-column>
        </el-table>
      </div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { orderApi } from '../../api/order'
import type { TradeOrderItem, TradeOrderVO } from '../../api/order'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
// 详情未加载完成前为 null（模板里用 v-if="order" 收窄）
const order = ref<TradeOrderVO | null>(null)

async function loadDetail() {
  loading.value = true
  try {
    order.value = await orderApi.detail(String(route.params.orderNo))
  } catch {
    // 拦截器已提示（如订单不存在）
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/order')
}

/** 明细主图：域侧可能下发 null、也可能是空串，统一收成「无图」的 falsy 值 */
function itemImage(row: TradeOrderItem) {
  return row.mainImage || ''
}

/** 规格展示：`规格名:取值` 用 `/` 连接；无规格组合（空表）显示占位符 */
function specText(row: TradeOrderItem) {
  const attrs = row.specAttrs || {}
  const parts = Object.entries(attrs).map(([name, value]) => `${name}:${value}`)
  return parts.length ? parts.join(' / ') : '—'
}

onMounted(loadDetail)
</script>

<style scoped>
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.detail-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.detail-title {
  font-size: 16px;
  font-weight: 600;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 18px 0 8px;
}

.section-title:first-of-type {
  margin-top: 0;
}

.item-img {
  width: 48px;
  height: 36px;
  border-radius: 3px;
}

.muted {
  font-size: 13px;
  color: var(--el-text-color-disabled);
}
</style>
