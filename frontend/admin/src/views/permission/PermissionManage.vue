<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button v-perm="'system:permission:add'" type="primary" @click="openAdd(null)">新增顶级目录</el-button>
      <span class="toolbar-tip">层级：目录 → 页面 → 按钮（逐级递减，按钮下不能再加子级）</span>
    </div>

    <!-- 树形数据用 el-table 的树类型展示（整棵一次加载，非懒加载） -->
    <el-table
      v-loading="loading"
      class="permission-table"
      :data="tableData"
      row-key="id"
      default-expand-all
    >
      <el-table-column label="权限名称" min-width="300">
        <template #default="{ row }">
          <span class="perm-name">{{ row.name }}</span>
          <el-tag v-if="row.type === 1" size="small" type="warning" effect="plain">目录</el-tag>
          <el-tag v-else-if="row.type === 2" size="small" type="success" effect="plain">页面</el-tag>
          <el-tag v-else size="small" type="info" effect="plain">按钮</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="perms" label="权限字符串" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <code v-if="row.perms" class="perm-perms">{{ row.perms }}</code>
          <span v-else class="perm-no-perms">—</span>
        </template>
      </el-table-column>
      <!-- 页面级权限才需要路由地址（供前端菜单导航） -->
      <el-table-column label="路由地址" min-width="150">
        <template #default="{ row }">
          <code v-if="row.type === 2 && row.route" class="perm-route">{{ row.route }}</code>
          <span v-else class="perm-no-perms">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="90" align="center" />
      <!-- el-table 插槽行的类型由 EP 定成 DefaultRow（Record<PropertyKey, any>），与业务行类型不互认，
           而运行时它就是树节点行，故调用处理函数时显式断言成 PermissionNode -->
      <el-table-column label="操作" width="250" align="center">
        <template #default="{ row }">
          <span class="row-actions">
            <el-button
              v-if="row.type < 3"
              v-perm="'system:permission:add'"
              link
              type="primary"
              size="small"
              @click="openAdd(row as PermissionNode)"
            >新增{{ row.type === 1 ? '页面' : '按钮' }}</el-button>
            <el-button v-perm="'system:permission:edit'" link type="primary" size="small" @click="openEdit(row as PermissionNode)">编辑</el-button>
            <el-button v-perm="'system:permission:delete'" link type="danger" size="small" @click="handleDelete(row as PermissionNode)">删除</el-button>
          </span>
        </template>
      </el-table-column>
      <template #empty>暂无权限，点击上方按钮新增顶级目录</template>
    </el-table>

    <PermissionFormDialog
      v-model="dialogVisible"
      :type="dialogType"
      :parent="dialogParent"
      :permission="dialogPermission"
      @save="handleSave"
    />
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { permissionApi } from '../../api/permission'
import type { PermissionPayload } from '../../api/permission'
import type { PermissionNode } from '../../types/auth'
import PermissionFormDialog from './PermissionFormDialog.vue'

const loading = ref(false)
const tableData = ref<PermissionNode[]>([])

// 表单弹窗状态
const dialogVisible = ref(false)
const dialogType = ref('add') // add | edit
/** 新增时的上级节点（null 表示顶级目录） */
const dialogParent = ref<PermissionNode | null>(null)
/** 编辑时的权限节点 */
const dialogPermission = ref<PermissionNode | null>(null)

async function loadTree() {
  loading.value = true
  try {
    tableData.value = await permissionApi.tree()
  } finally {
    loading.value = false
  }
}

function openAdd(parent: PermissionNode | null) {
  dialogType.value = 'add'
  dialogParent.value = parent
  dialogPermission.value = null
  dialogVisible.value = true
}

function openEdit(permission: PermissionNode) {
  dialogType.value = 'edit'
  dialogParent.value = null
  dialogPermission.value = permission
  dialogVisible.value = true
}

async function handleSave(form: PermissionPayload) {
  try {
    if (dialogType.value === 'add') {
      await permissionApi.add(form)
      ElMessage.success('权限创建成功')
    } else {
      // 编辑态由 openEdit 赋值；提前取出仅为把 `PermissionNode | null` 收窄
      const permission = dialogPermission.value
      if (!permission) return
      await permissionApi.update(permission.id, form)
      ElMessage.success('权限更新成功')
    }
    dialogVisible.value = false
    loadTree()
  } catch {
    // 后端业务校验失败（如层级/重名）时拦截器已提示，弹窗保持打开供修改
  }
}

async function handleDelete(permission: PermissionNode) {
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
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.permission-table {
  min-height: 60px;
}

.perm-name {
  font-weight: 500;
  margin-right: 6px;
}

.perm-perms {
  font-size: 12px;
  color: var(--el-text-color-regular);
  background-color: var(--el-fill-color-light);
  border-radius: 3px;
  padding: 1px 6px;
}

.perm-no-perms {
  color: var(--el-text-color-disabled);
}

.perm-route {
  font-size: 12px;
  color: var(--el-text-color-regular);
  background-color: var(--el-fill-color-light);
  border-radius: 3px;
  padding: 1px 6px;
}
</style>

<!-- 行操作仅在鼠标悬停当前行时显示（slot 内容带 scoped 属性，需放开作用域） -->
<style>
.permission-table .row-actions {
  visibility: hidden;
}

.permission-table .el-table__row:hover .row-actions {
  visibility: visible;
}
</style>
