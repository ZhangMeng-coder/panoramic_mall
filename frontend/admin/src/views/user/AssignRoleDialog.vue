<template>
  <el-dialog
    :model-value="modelValue"
    title="分配角色"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initData"
  >
    <div class="assign-head">
      <span>为「{{ user?.username }}」分配角色（多选，可清空）</span>
      <el-input
        v-model="filter"
        placeholder="角色名称/标识关键字"
        clearable
        size="small"
        style="width: 200px"
      />
    </div>

    <div v-loading="loading" class="role-box">
      <el-checkbox-group v-model="checkedRoleIds" class="role-group">
        <el-checkbox
          v-for="role in filteredRoles"
          :key="role.id"
          :value="role.id"
          class="role-item"
        >
          {{ role.name }}<span class="role-code">（{{ role.code }}）</span>
        </el-checkbox>
      </el-checkbox-group>
      <el-empty v-if="!loading && !filteredRoles.length" description="无可分配角色" :image-size="60" />
    </div>

    <template #footer>
      <el-button @click="onUpdateVisible(false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { roleApi } from '../../api/role'
import type { RoleItem } from '../../api/role'
import { userApi } from '../../api/user'
import type { UserItem } from '../../api/user'

// 用类型式声明 + withDefaults，保留原运行时的两个 default（缺省即 false / null）
const props = withDefaults(
  defineProps<{
    /** 弹窗显隐（v-model） */
    modelValue?: boolean
    /** 待分配角色的用户 */
    user?: UserItem | null
  }>(),
  { modelValue: false, user: null }
)

const emit = defineEmits<{
  'update:modelValue': [visible: boolean]
  /** 保存勾选结果：整体替换，空数组表示清空该用户角色 */
  save: [roleIds: number[]]
}>()

const loading = ref(false)
const submitting = ref(false)
const allRoles = ref<RoleItem[]>([])
const checkedRoleIds = ref<number[]>([])
const filter = ref('')

const filteredRoles = computed<RoleItem[]>(() => {
  const kw = filter.value.trim().toLowerCase()
  if (!kw) return allRoles.value
  return allRoles.value.filter(
    (r) => r.name.toLowerCase().includes(kw) || r.code.toLowerCase().includes(kw)
  )
})

function onUpdateVisible(visible: boolean): void {
  emit('update:modelValue', visible)
}

async function initData(): Promise<void> {
  filter.value = ''
  checkedRoleIds.value = []
  if (!props.user) return
  loading.value = true
  try {
    const [roles, ids] = await Promise.all([roleApi.list(), userApi.roleIds(props.user.id)])
    allRoles.value = roles
    checkedRoleIds.value = ids
  } finally {
    loading.value = false
  }
}

async function handleSubmit(): Promise<void> {
  if (!props.user) return
  submitting.value = true
  try {
    // 整体替换；空数组表示清空该用户角色
    emit('save', [...checkedRoleIds.value])
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.assign-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.role-box {
  min-height: 120px;
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  padding: 8px 12px;
}

.role-group {
  display: flex;
  flex-direction: column;
}

.role-item {
  margin-right: 0;
  padding: 6px 0;
}

.role-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
