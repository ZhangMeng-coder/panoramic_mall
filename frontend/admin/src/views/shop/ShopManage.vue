<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="店铺名 / 联系人 / 营业执照关键字"
        clearable
        style="width: 260px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select v-model="query.status" placeholder="审核状态" clearable style="width: 140px" @change="handleSearch">
        <el-option label="草稿" :value="0" />
        <el-option label="待审核" :value="1" />
        <el-option label="已通过" :value="2" />
        <el-option label="已驳回" :value="3" />
      </el-select>
      <el-button v-perm="'store:shop:list'" type="primary" @click="handleSearch">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="shopName" label="店铺名称" min-width="160">
        <template #default="{ row }">
          <el-link type="primary" @click="openDetail(row)">{{ row.shopName }}</el-link>
        </template>
      </el-table-column>
      <el-table-column label="联系人 / 电话" min-width="170">
        <template #default="{ row }">
          <span v-if="row.contactName">{{ row.contactName }}</span>
          <span v-if="row.contactName && row.contactPhone"> · </span>
          <span v-if="row.contactPhone">{{ row.contactPhone }}</span>
          <span v-if="!row.contactName && !row.contactPhone">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="licenseNo" label="统一社会信用代码" min-width="190" show-overflow-tooltip>
        <template #default="{ row }">{{ row.licenseNo || '-' }}</template>
      </el-table-column>
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusMeta(row.status).type">{{ statusMeta(row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ row.submitTime || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 1"
            v-perm="'store:shop:audit'"
            link
            type="success"
            @click="openAudit(row)"
          >
            审核
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

    <!-- 店铺详情抽屉 -->
    <el-drawer v-model="detailVisible" title="店铺详情" size="520px" destroy-on-close>
      <template v-if="detail">
        <div class="detail-head">
          <el-image v-if="detail.logo" :src="detail.logo" fit="cover" class="shop-logo" :preview-src-list="[detail.logo]" />
          <div class="detail-title">
            <div class="shop-name">{{ detail.shopName }}</div>
            <el-tag :type="statusMeta(detail.status).type" size="small">{{ statusMeta(detail.status).label }}</el-tag>
          </div>
        </div>
        <el-descriptions :column="1" border class="detail-desc">
          <el-descriptions-item label="店铺ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="店铺简介">{{ detail.intro || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.contactName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.contactPhone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="所在地区">{{ detail.region || '-' }}</el-descriptions-item>
          <el-descriptions-item label="详细地址">{{ detail.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="企业名称">{{ detail.licenseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="统一社会信用代码">{{ detail.licenseNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ detail.submitTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核时间">{{ detail.auditTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审核备注">{{ detail.auditRemark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="detail.licenseImg" class="detail-img-label">营业执照照片</div>
        <el-image
          v-if="detail.licenseImg"
          :src="detail.licenseImg"
          fit="contain"
          class="license-img"
          :preview-src-list="[detail.licenseImg]"
        />
      </template>
    </el-drawer>

    <!-- 审核弹窗：通过 / 驳回（驳回原因必填） -->
    <el-dialog v-model="auditVisible" title="店铺审核" width="460px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="店铺">
          <span>{{ auditShop?.shopName }}</span>
        </el-form-item>
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.approved">
            <el-radio :value="true">通过</el-radio>
            <el-radio :value="false">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!auditForm.approved" label="驳回原因">
          <el-input
            v-model="auditForm.auditRemark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="请填写驳回原因（店主可见，必填）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditVisible = false">取消</el-button>
        <el-button v-perm="'store:shop:audit'" type="primary" :loading="auditing" @click="submitAudit">
          确定{{ auditForm.approved ? '通过' : '驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { storeApi } from '../../api/store'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: undefined })

// —— 店铺详情抽屉 ——
const detailVisible = ref(false)
const detail = ref(null)

// —— 审核弹窗 ——
const auditVisible = ref(false)
const auditShop = ref(null)
const auditForm = reactive({ approved: true, auditRemark: '' })
const auditing = ref(false)

/** 审核状态展示映射 */
function statusMeta(status) {
  const map = {
    0: { label: '草稿', type: 'info' },
    1: { label: '待审核', type: 'warning' },
    2: { label: '已通过', type: 'success' },
    3: { label: '已驳回', type: 'danger' }
  }
  return map[status] || { label: '-', type: 'info' }
}

async function loadPage() {
  loading.value = true
  try {
    const data = await storeApi.page({ ...query })
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

async function openDetail(row) {
  detail.value = null
  detailVisible.value = true
  try {
    detail.value = await storeApi.detail(row.id)
  } catch {
    // 失败时详情抽屉置空，拦截器已提示
  }
}

function openAudit(row) {
  auditShop.value = row
  auditForm.approved = true
  auditForm.auditRemark = ''
  auditVisible.value = true
}

async function submitAudit() {
  if (!auditForm.approved && !auditForm.auditRemark.trim()) {
    ElMessage.warning('请填写驳回原因')
    return
  }
  auditing.value = true
  try {
    await storeApi.audit(auditShop.value.id, {
      approved: auditForm.approved,
      auditRemark: auditForm.approved ? undefined : auditForm.auditRemark.trim()
    })
    ElMessage.success(auditForm.approved ? '已通过该店铺审核' : '已驳回该店铺')
    auditVisible.value = false
    loadPage()
  } catch {
    // 后端业务校验失败（如重复审核）时拦截器已提示
  } finally {
    auditing.value = false
  }
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

.detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.shop-logo {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-light);
  flex-shrink: 0;
}

.detail-title .shop-name {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 6px;
}

.detail-img-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin: 16px 0 8px;
}

.license-img {
  width: 100%;
  border-radius: 8px;
}
</style>
