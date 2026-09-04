<template>
  <el-dialog
    :model-value="modelValue"
    :title="`分配用户：${role?.name || ''}`"
    width="820px"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initData"
  >
    <div class="split">
      <!-- 左：未分配用户（可添加到该角色） -->
      <div class="panel">
        <div class="panel-head">
          <span>未分配用户</span>
          <div class="panel-ops">
            <el-input
              v-model="candidateQuery.keyword"
              placeholder="用户名/昵称/手机号"
              clearable
              size="small"
              style="width: 180px"
              @keyup.enter="searchCandidates"
              @clear="searchCandidates"
            />
            <el-button size="small" type="primary" @click="searchCandidates">查询</el-button>
          </div>
        </div>
        <el-table
          v-loading="loadingCandidates"
          :data="visibleCandidates"
          size="small"
          :max-height="320"
          empty-text="暂无可分配用户"
        >
          <el-table-column prop="username" label="用户名" min-width="90" />
          <el-table-column prop="nickname" label="昵称" min-width="90">
            <template #default="{ row }">{{ row.nickname || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                size="small"
                :disabled="assignedIds.has(row.id)"
                @click="addUser(row)"
              >
                {{ assignedIds.has(row.id) ? '已添加' : '添加' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="candidateQuery.pageNum"
          v-model:page-size="candidateQuery.pageSize"
          class="mini-pager"
          layout="total, prev, pager, next"
          :total="candidateTotal"
          :page-sizes="[8]"
          @current-change="loadCandidates"
        />
      </div>

      <!-- 右：已分配用户 -->
      <div class="panel">
        <div class="panel-head">
          <span>已分配用户（{{ assignedUsers.length }}）</span>
        </div>
        <div v-loading="loadingAssigned" class="assigned-box">
          <div v-for="u in assignedUsers" :key="u.id" class="assigned-row">
            <div class="assigned-info">
              <span class="assigned-name">{{ u.username }}</span>
              <span class="assigned-sub">{{ u.nickname || u.phone || '—' }}</span>
            </div>
            <el-button link type="danger" size="small" @click="removeUser(u.id)">移除</el-button>
          </div>
          <el-empty v-if="!loadingAssigned && !assignedUsers.length" description="暂未分配用户" :image-size="60" />
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="onUpdateVisible(false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { roleApi } from '../../api/role'
import { userApi } from '../../api/user'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 待分配用户的角色 */
  role: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

// —— 左：候选（未分配用户）分页 ——
const loadingCandidates = ref(false)
const candidates = ref([])
const candidateTotal = ref(0)
const candidateQuery = ref({ pageNum: 1, pageSize: 8, keyword: '' })

// —— 右：本会话已分配用户 ——
const loadingAssigned = ref(false)
const assignedUsers = ref([])
/** 打开时角色原本已分配的用户ID（用于统计会话内新增的隐藏候选数） */
const originalAssignedIds = ref(new Set())
/** 会话内（含原始）已分配用户ID集合 */
const assignedIds = computed(() => new Set(assignedUsers.value.map((u) => u.id)))

/** 候选列表剔除“本会话已添加”，避免重复添加 */
const visibleCandidates = computed(() =>
  candidates.value.filter((u) => !assignedIds.value.has(u.id))
)

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

async function initData() {
  assignedUsers.value = []
  originalAssignedIds.value = new Set()
  candidateQuery.value = { pageNum: 1, pageSize: 8, keyword: '' }
  if (!props.role) return

  loadingAssigned.value = true
  try {
    const ids = await roleApi.userIds(props.role.id)
    originalAssignedIds.value = new Set(ids)
    // 逐个取详情以展示用户名/昵称；失败（如已被删除）则忽略
    const settled = await Promise.allSettled(ids.map((id) => userApi.detail(id)))
    assignedUsers.value = settled
      .filter((s) => s.status === 'fulfilled')
      .map((s) => s.value)
  } finally {
    loadingAssigned.value = false
  }

  await loadCandidates()
}

async function loadCandidates() {
  if (!props.role) return
  loadingCandidates.value = true
  try {
    const data = await roleApi.unassignedUsersPage(props.role.id, { ...candidateQuery.value })
    candidates.value = data.records
    // 会话内新增了 x 个用户：总条数中它们仍被后端计入（保存前），减去保持分页一致
    const addedNew = assignedUsers.value.filter((u) => !originalAssignedIds.value.has(u.id)).length
    candidateTotal.value = data.total - addedNew
  } finally {
    loadingCandidates.value = false
  }
}

function searchCandidates() {
  candidateQuery.value.pageNum = 1
  loadCandidates()
}

function addUser(user) {
  if (assignedIds.value.has(user.id)) return
  assignedUsers.value.push({ ...user })
}

function removeUser(id) {
  assignedUsers.value = assignedUsers.value.filter((u) => u.id !== id)
}

async function handleSubmit() {
  if (!props.role) return
  // 整体替换：空数组表示清空该角色下所有用户
  emit('save', assignedUsers.value.map((u) => u.id))
}
</script>

<style scoped>
.split {
  display: flex;
  gap: 12px;
}

.panel {
  flex: 1;
  min-width: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  padding: 10px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 500;
}

.panel-ops {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mini-pager {
  margin-top: 8px;
  justify-content: center;
}

.assigned-box {
  min-height: 120px;
  max-height: 320px;
  overflow-y: auto;
}

.assigned-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 4px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}

.assigned-row:last-child {
  border-bottom: none;
}

.assigned-info {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
}

.assigned-name {
  font-weight: 500;
}

.assigned-sub {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
