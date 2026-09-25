<template>
  <el-card shadow="never">
    <!-- 筛选区：⚠ 商户端**只有筛选、没有星级分布**（分布是 C 端商品详情页要的），两端刻意不对称 -->
    <el-form inline class="filter-form">
      <el-form-item label="商品">
        <!-- 商品选择器**复用既有在售商品分页**（GET /goods/spu/page，远程搜索），不为它新造一套接口 -->
        <el-select
          v-model="query.spuId"
          filterable
          remote
          clearable
          :remote-method="searchGoods"
          :loading="goodsLoading"
          placeholder="全部商品（可搜索名称）"
          style="width: 240px"
          @change="onGoodsChange"
        >
          <el-option v-for="g in goodsOptions" :key="g.id" :label="g.name" :value="g.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="星级">
        <el-select v-model="query.score" clearable placeholder="全部星级" style="width: 130px">
          <el-option v-for="s in STAR_OPTIONS" :key="s" :label="`${s} 星`" :value="s" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 本店评价列表：时间倒序由服务端固定（页面不提供排序） -->
    <el-table v-loading="loading" :data="records">
      <!-- 商品名由 store-bff 兜底下发（商品已软删 → 「商品已删除」），页面不再造第二份文案 -->
      <el-table-column prop="spuName" label="商品" min-width="170" show-overflow-tooltip />
      <el-table-column label="评价人" width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="buyer">
            <!-- 无头像时落回首字（不请求、不伪造图） -->
            <el-avatar :size="24" :src="(row as StoreEvaluationItem).avatar || ''">
              {{ (row as StoreEvaluationItem).nickname.charAt(0) }}
            </el-avatar>
            <span class="buyer__name">{{ (row as StoreEvaluationItem).nickname }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="星级" width="140">
        <template #default="{ row }">
          <el-rate :model-value="(row as StoreEvaluationItem).score" disabled />
        </template>
      </el-table-column>
      <el-table-column label="评价内容" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">{{ (row as StoreEvaluationItem).content || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createTime" label="评价时间" width="170" />
      <!-- 回复态：null = 未回复。回复一条评价至多一个，且不可改 / 不可删 -->
      <el-table-column label="商家回复" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="(row as StoreEvaluationItem).replyContent">
            {{ (row as StoreEvaluationItem).replyContent }}
          </span>
          <el-tag v-else type="info" effect="plain">未回复</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="(row as StoreEvaluationItem).replyContent" link type="primary" @click="openReply(row as StoreEvaluationItem)">查看</el-button>
          <el-button v-else link type="primary" @click="openReply(row as StoreEvaluationItem)">回复</el-button>
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

    <EvaluationReplyDialog v-model="replyVisible" :evaluation="currentEvaluation" @replied="loadPage" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue'
import { evaluationApi } from '../../api/evaluation'
import type { EvaluationPageQuery, StoreEvaluationItem } from '../../api/evaluation'
import { goodsApi } from '../../api/goods'
import type { StoreGoodsSpuPageItem } from '../../api/goods'
import EvaluationReplyDialog from './EvaluationReplyDialog.vue'

/** 星级筛选项：5 → 1 星（与 C 端分布同序，看得最重的排前面） */
const STAR_OPTIONS = [5, 4, 3, 2, 1]

const loading = ref(false)
const records = ref<StoreEvaluationItem[]>([])
const total = ref(0)

// 「全部」用 null：axios 序列化时 null 与 undefined 都会被丢弃，等价于不筛
const query = reactive<EvaluationPageQuery>({
  pageNum: 1,
  pageSize: 10,
  spuId: null,
  score: null
})

// 回复弹窗：数据直接取列表行（列表已带全部展示字段），打开时不再请求详情
const replyVisible = ref(false)
const currentEvaluation = ref<StoreEvaluationItem | null>(null)

// —— 商品选择器：远程搜索复用既有在售商品分页 ——

/** 远程搜索的候选（首次进页面按无关键字拉一页，「全部商品」不是一个接口） */
const goodsSearchResult = ref<StoreGoodsSpuPageItem[]>([])
/** 已选中的商品行：选中后再搜别的会把选中项挤出结果集，故单独留一份用于回显 */
const selectedGoods = ref<StoreGoodsSpuPageItem | null>(null)
const goodsLoading = ref(false)
/** 远程搜索的迟到响应守卫：慢的那次回来时不能覆盖新一次的结果 */
let goodsSeq = 0

/** 下拉项 = 本次搜索结果 + 已选中项（少了后者，回显会变成一个裸 id） */
const goodsOptions = computed<StoreGoodsSpuPageItem[]>(() => {
  const list = goodsSearchResult.value
  const picked = selectedGoods.value
  if (!picked || list.some((g) => g.id === picked.id)) return list
  return [picked, ...list]
})

async function searchGoods(keyword: string) {
  const seq = ++goodsSeq
  goodsLoading.value = true
  try {
    const data = await goodsApi.page({ pageNum: 1, pageSize: 20, keyword: keyword.trim() || undefined })
    if (seq !== goodsSeq) return
    goodsSearchResult.value = data.records
  } catch {
    // 拦截器已提示；候选保持上一次的结果，不清空（清空会把已选中项的回显也一起抹掉）
  } finally {
    if (seq === goodsSeq) goodsLoading.value = false
  }
}

/** 选中项变化：记住那一行用于回显；清空（clearable）时只清回显，筛选值本身由 el-select 置空 */
function onGoodsChange(id: number | null) {
  selectedGoods.value = id === null ? null : (goodsOptions.value.find((g) => g.id === id) ?? null)
}

async function loadPage() {
  loading.value = true
  try {
    const data = await evaluationApi.page({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      spuId: query.spuId ?? null,
      score: query.score ?? null
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
  query.spuId = null
  query.score = null
  selectedGoods.value = null
  handleSearch()
}

function openReply(row: StoreEvaluationItem) {
  currentEvaluation.value = row
  replyVisible.value = true
}

onMounted(() => {
  loadPage()
  // 首次进页面先把候选拉一页，免得点开下拉是空的（搜索行为本身由 remote-method 接管）
  void searchGoods('')
})
</script>

<style scoped>
.filter-form {
  margin-bottom: 4px;
}

.filter-form .el-form-item {
  margin-bottom: 8px;
}

.buyer {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.buyer__name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.pager {
  margin-top: var(--space-3);
  justify-content: flex-end;
}
</style>
