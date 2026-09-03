<template>
  <el-dialog
    :model-value="modelValue"
    title="分配权限"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initData"
  >
    <div class="assign-tip">
      为角色「{{ role?.name }}」勾选权限，勾选父节点将自动全选其下所有子节点
    </div>

    <div v-loading="loading" class="perm-box">
      <el-tree
        ref="treeRef"
        :data="treeData"
        node-key="id"
        show-checkbox
        default-expand-all
        :props="{ label: 'name', children: 'children' }"
      >
        <template #default="{ data }">
          <span class="perm-node">
            <span class="perm-name">{{ data.name }}</span>
            <el-tag v-if="data.type === 1" size="small" type="warning" effect="plain">目录</el-tag>
            <el-tag v-else-if="data.type === 2" size="small" type="success" effect="plain">页面</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">按钮</el-tag>
            <span v-if="data.perms" class="perm-code">{{ data.perms }}</span>
          </span>
        </template>
      </el-tree>
      <el-empty v-if="!loading && !treeData.length" description="暂无权限，请先到权限管理页配置" :image-size="60" />
    </div>

    <template #footer>
      <el-button @click="onUpdateVisible(false)">取消</el-button>
      <el-button @click="clearChecked">清空勾选</el-button>
      <el-button type="primary" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { permissionApi } from '../../api/permission'
import { roleApi } from '../../api/role'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 待分配权限的角色 */
  role: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

const loading = ref(false)
const treeData = ref([])
const treeRef = ref(null)

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

async function initData() {
  treeData.value = []
  if (!props.role) return
  loading.value = true
  try {
    const [tree, ids] = await Promise.all([permissionApi.tree(), roleApi.permissionIds(props.role.id)])
    treeData.value = tree
    if (ids && ids.length) {
      treeRef.value?.setCheckedKeys(ids)
    }
  } finally {
    loading.value = false
  }
}

function clearChecked() {
  treeRef.value?.setCheckedKeys([])
}

async function handleSubmit() {
  if (!props.role) return
  // 仅保存“完全勾选”的节点（父勾选会级联子节点一并完全勾选），
  // 与后端整体替换语义一致；回显 setCheckedKeys 可精确还原。
  const checkedKeys = treeRef.value?.getCheckedKeys() || []
  emit('save', checkedKeys)
}
</script>

<style scoped>
.assign-tip {
  color: #909399;
  font-size: 12px;
  margin-bottom: 10px;
}

.perm-box {
  min-height: 120px;
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px;
}

.perm-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.perm-name {
  font-weight: 500;
}

.perm-code {
  color: #909399;
  font-size: 12px;
}
</style>
