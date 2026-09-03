<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button type="primary" @click="openAdd(null)">新增顶级目录</el-button>
      <span class="toolbar-tip">层级：目录 → 页面 → 按钮（逐级递减，按钮下不能再加子级）</span>
    </div>

    <el-tree
      v-loading="loading"
      :data="treeData"
      node-key="id"
      :props="{ label: 'name', children: 'children' }"
      default-expand-all
      class="perm-tree"
      @mouseleave="hoverId = null"
    >
      <template #default="{ data }">
        <div
          class="tree-node"
          :class="{ 'is-hover': hoverId === data.id }"
          @mouseenter="hoverId = data.id"
        >
          <span class="tree-node-label">
            <span class="node-name">{{ data.name }}</span>
            <el-tag v-if="data.type === 1" size="small" type="warning" effect="plain">目录</el-tag>
            <el-tag v-else-if="data.type === 2" size="small" type="success" effect="plain">页面</el-tag>
            <el-tag v-else size="small" type="info" effect="plain">按钮</el-tag>
            <span v-if="data.perms" class="node-perms">{{ data.perms }}</span>
          </span>
          <span v-if="hoverId === data.id" class="tree-node-actions" @click.stop>
            <el-button v-if="data.type < 3" link type="primary" size="small" @click="openAdd(data)">
              新增{{ data.type === 1 ? '页面' : '按钮' }}
            </el-button>
            <el-button link type="primary" size="small" @click="openEdit(data)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(data)">删除</el-button>
          </span>
        </div>
      </template>
    </el-tree>

    <el-empty v-if="!loading && !treeData.length" description="暂无权限，点击上方按钮新增顶级目录" />

    <PermissionFormDialog
      v-model="dialogVisible"
      :type="dialogType"
      :parent="dialogParent"
      :permission="dialogPermission"
      @save="handleSave"
    />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permissionApi } from '../../api/permission'
import PermissionFormDialog from './PermissionFormDialog.vue'

const loading = ref(false)
const treeData = ref([])
const hoverId = ref(null)

// 表单弹窗状态
const dialogVisible = ref(false)
const dialogType = ref('add') // add | edit
const dialogParent = ref(null)
const dialogPermission = ref(null)

async function loadTree() {
  loading.value = true
  try {
    treeData.value = await permissionApi.tree()
  } finally {
    loading.value = false
  }
}

function openAdd(parent) {
  dialogType.value = 'add'
  dialogParent.value = parent
  dialogPermission.value = null
  dialogVisible.value = true
}

function openEdit(permission) {
  dialogType.value = 'edit'
  dialogParent.value = null
  dialogPermission.value = permission
  dialogVisible.value = true
}

async function handleSave(form) {
  try {
    if (dialogType.value === 'add') {
      await permissionApi.add(form)
      ElMessage.success('权限创建成功')
    } else {
      await permissionApi.update(dialogPermission.value.id, form)
      ElMessage.success('权限更新成功')
    }
    dialogVisible.value = false
    loadTree()
  } catch {
    // 后端业务校验失败（如层级/重名）时拦截器已提示，弹窗保持打开供修改
  }
}

async function handleDelete(permission) {
  try {
    await ElMessageBox.confirm(
      `确定删除权限「${permission.name}」吗？存在子权限或已分配给角色时将无法删除`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await permissionApi.remove(permission.id)
    ElMessage.success('权限删除成功')
    loadTree()
  } catch {
    // 后端业务校验失败（存在子权限/被角色引用）时拦截器已提示，此处无需处理
  }
}

onMounted(loadTree)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.toolbar-tip {
  color: #909399;
  font-size: 12px;
}

.perm-tree {
  min-height: 60px;
}

.tree-node {
  display: inline-flex;
  align-items: center;
  width: 100%;
  padding-right: 8px;
  border-radius: 4px;
}

.tree-node.is-hover {
  background-color: #ecf5ff;
}

.tree-node-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.node-name {
  font-weight: 500;
}

.node-perms {
  color: #909399;
  font-size: 12px;
}

.tree-node-actions {
  flex-shrink: 0;
  padding-left: 12px;
}
</style>
