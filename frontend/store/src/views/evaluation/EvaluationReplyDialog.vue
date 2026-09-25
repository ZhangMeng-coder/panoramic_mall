<template>
  <el-dialog
    :model-value="modelValue"
    title="评价详情"
    width="640px"
    @update:model-value="onUpdateVisible"
    @open="resetForm"
  >
    <template v-if="evaluation">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="商品" :span="2">{{ evaluation.spuName }}</el-descriptions-item>
        <el-descriptions-item label="评价人">{{ evaluation.nickname }}</el-descriptions-item>
        <el-descriptions-item label="评价时间">{{ evaluation.createTime }}</el-descriptions-item>
        <el-descriptions-item label="星级" :span="2">
          <el-rate :model-value="evaluation.score" disabled />
        </el-descriptions-item>
      </el-descriptions>

      <div class="section-title">评价内容</div>
      <!-- 评价文字按纯文本插值（后端也按纯文本落库），**不用 v-html** -->
      <div class="text-block">{{ evaluation.content || '（未填写评价文字，仅评分）' }}</div>

      <div class="section-title">规格快照</div>
      <!-- 快照是**下单那一刻**的规格 / 单价 / 数量，此后改价、删 SKU 都不改写；空快照照实说空 -->
      <ul v-if="evaluation.skuSnapshot.length" class="snapshot">
        <li v-for="(sku, index) in evaluation.skuSnapshot" :key="index" class="snapshot__row">
          <span class="snapshot__spec">{{ specText(sku) }}</span>
          <span class="snapshot__price">¥{{ sku.unitPrice }} × {{ sku.quantity }}</span>
        </li>
      </ul>
      <div v-else class="text-block">—</div>

      <div class="section-title">商家回复</div>
      <!-- ⚠ 一条评价**至多一个回复**，且**不可改、不可删**：已回复的行只读展示，不给任何编辑入口 -->
      <template v-if="evaluation.replyContent">
        <div class="text-block">{{ evaluation.replyContent }}</div>
        <div class="reply-time">回复于 {{ evaluation.replyTime }}</div>
      </template>
      <template v-else>
        <el-input
          v-model="replyContent"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
          placeholder="回复内容（提交后不可修改、不可删除）"
        />
        <div class="reply-ops">
          <el-button type="primary" :loading="submitting" @click="submitReply">提交回复</el-button>
          <el-button :disabled="submitting" @click="replyContent = ''">清空</el-button>
        </div>
      </template>
    </template>

    <template #footer>
      <el-button @click="onUpdateVisible(false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { evaluationApi } from '../../api/evaluation'
import type { EvaluationSkuSnapshot, StoreEvaluationItem } from '../../api/evaluation'

const props = defineProps<{
  /** 弹窗显隐（v-model） */
  modelValue: boolean
  /** 要查看 / 回复的评价行；关闭时为 null */
  evaluation: StoreEvaluationItem | null
}>()

const emit = defineEmits(['update:modelValue', 'replied'])

const replyContent = ref('')
const submitting = ref(false)

function onUpdateVisible(visible: boolean) {
  emit('update:modelValue', visible)
}

function resetForm() {
  // 本弹窗是复用的：每次打开都清掉上一次的草稿，避免误把上一单的回复发到这一条上
  replyContent.value = ''
}

/** 规格 / 单价 / 数量合成一行：`颜色:红 / 尺码:M`，无规格组合时只留数量 */
function specText(sku: EvaluationSkuSnapshot) {
  const parts = (sku.specAttrs || []).map((a) => `${a.spec}:${a.value}`)
  return parts.length ? parts.join(' / ') : '（无规格）'
}

async function submitReply() {
  const evaluation = props.evaluation
  if (!evaluation) return
  const content = replyContent.value.trim()
  if (!content) {
    // 与域侧 / BFF 的 @NotBlank 同口径，先挡一次免得白跑一次请求
    ElMessage.warning('请填写回复内容')
    return
  }
  submitting.value = true
  try {
    await evaluationApi.reply(evaluation.id, { replyContent: content })
    ElMessage.success('已回复')
    emit('replied')
    onUpdateVisible(false)
  } catch {
    // 并发下已被回复 / 内容超长等由拦截器提示，保持表单值供修改
  } finally {
    submitting.value = false
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

.text-block {
  font-size: var(--text-sm);
  line-height: 1.7;
  color: var(--el-text-color-regular);
  white-space: pre-wrap;
  word-break: break-word;
}

.snapshot {
  list-style: none;
  margin: 0;
  padding: 0;
}

.snapshot__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  font-size: var(--text-sm);
  color: var(--el-text-color-regular);
  padding: var(--space-1) 0;
}

.snapshot__spec {
  word-break: break-word;
}

.snapshot__price {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}

.reply-time {
  margin-top: var(--space-2);
  font-size: var(--text-xs);
  color: var(--el-text-color-secondary);
}

.reply-ops {
  margin-top: var(--space-3);
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
