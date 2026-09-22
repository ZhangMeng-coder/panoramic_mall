<template>
  <el-dialog
    :model-value="modelValue"
    title="订单详情"
    width="820px"
    @update:model-value="onUpdateVisible"
    @open="loadDetail"
  >
    <div v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.statusStoreAdminLabel }}</el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ detail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="店铺">{{ detail.storeName }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">收货地址</div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="收件人">{{ detail.address.receiverName }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ detail.address.receiverPhone }}</el-descriptions-item>
          <el-descriptions-item label="所在地区" :span="2">{{ detail.address.region }}</el-descriptions-item>
          <el-descriptions-item label="详细地址" :span="2">{{ detail.address.detail }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">商品明细</div>
        <el-table :data="detail.items" border size="small">
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
        <div class="amount-line">
          <span class="amount-line__label">合计</span>
          <span class="amount-line__count">{{ detail.totalQuantity }} 件</span>
          <span class="amount-line__value">¥{{ detail.totalAmount }}</span>
        </div>

        <div class="section-title">发货</div>
        <!-- ⚠ PAID（商户侧文案「待发货」）是域内状态机唯一允许发货的状态；此判断只是按钮的可见性提示，
             真正的门禁在域内（跳级 / 重复发货由域回 400，拦截器提示）。状态取值以域为准，前端不自造枚举清单。 -->
        <div v-if="detail.status === 'PAID'" class="ship-row">
          <el-input
            v-model="trackingNo"
            placeholder="快递单号"
            clearable
            maxlength="64"
            style="width: 260px"
            @keyup.enter="submitShip"
          />
          <el-button type="primary" :loading="shipping" @click="submitShip">发货</el-button>
        </div>
        <div v-else class="ship-row">
          <span class="ship-row__label">快递单号：</span>
          <span>{{ detail.shipNo || '—' }}</span>
        </div>
      </template>
    </div>

    <template #footer>
      <el-button @click="onUpdateVisible(false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { orderApi } from '../../api/order'
import type { TradeOrderItem, TradeOrderVO } from '../../api/order'

const props = defineProps<{
  /** 弹窗显隐（v-model） */
  modelValue: boolean
  /** 要查看的订单号；关闭时为 null */
  orderNo: string | null
}>()

const emit = defineEmits(['update:modelValue', 'shipped'])

const loading = ref(false)
const detail = ref<TradeOrderVO | null>(null)
const trackingNo = ref('')
const shipping = ref(false)

function onUpdateVisible(visible: boolean) {
  emit('update:modelValue', visible)
}

async function loadDetail() {
  // 弹窗每次由列表行带入订单号；为空只可能是「已关闭又被 @open 触发」，直接跳过
  const orderNo = props.orderNo
  if (!orderNo) return
  trackingNo.value = ''
  // 先清空：本弹窗是复用的，避免上一单的内容在新单加载完成前短暂显示
  detail.value = null
  loading.value = true
  try {
    detail.value = await orderApi.detail(orderNo)
  } catch {
    // 拦截器已提示（如订单不存在）；detail 保持为 null，页面显示空态
  } finally {
    loading.value = false
  }
}

/** 规格展示：`规格名:取值` 用 `/` 连接；无规格组合（空表）显示占位符 */
function specText(row: TradeOrderItem) {
  const attrs = row.specAttrs || {}
  const parts = Object.entries(attrs).map(([name, value]) => `${name}:${value}`)
  return parts.length ? parts.join(' / ') : '—'
}

async function submitShip() {
  const orderNo = props.orderNo
  if (!orderNo) return
  const no = trackingNo.value.trim()
  if (!no) {
    ElMessage.warning('请填写快递单号')
    return
  }
  shipping.value = true
  try {
    await orderApi.ship(orderNo, { trackingNo: no })
    ElMessage.success('已发货')
    // 状态由域决定（本端不自造），重拉详情取新状态，并让父组件重拉列表
    await loadDetail()
    emit('shipped')
  } catch {
    // 域内业务校验失败（重复发货 / 跳级 / 单号非法）由拦截器提示，保持表单值供修改
  } finally {
    shipping.value = false
  }
}
</script>

<style scoped>
.section-title {
  font-size: 13px;
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  margin: var(--space-4) 0 var(--space-2);
}

.amount-line {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-2);
}

.amount-line__label {
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
}

.amount-line__count {
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.amount-line__value {
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  color: var(--el-color-danger);
}

.ship-row {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.ship-row__label {
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
}
</style>
