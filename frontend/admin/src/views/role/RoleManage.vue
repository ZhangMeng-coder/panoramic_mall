<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="角色名称 / 标识关键字"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="success" @click="openAdd">新增角色</el-button>
    </div>

    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="角色名称" min-width="140" />
      <el-table-column prop="code" label="角色标识" min-width="120">
        <template #default="{ row }">
          <el-tag effect="plain">{{ row.code }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openAssignPermission(row)">分配权限</el-button>
          <el-button link type="success" @click="openAssignUser(row)">分配用户</el-button>
          <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <RoleFormDialog v-model="dialogVisible" :type="dialogType" :role="dialogRole" @save="handleSave" />
    <AssignPermissionDialog v-model="permVisible" :role="dialogRole" @save="handleAssignPermission" />
    <AssignUserDialog v-model="userVisible" :role="dialogRole" @save="handleAssignUser" />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roleApi } from '../../api/role'
import RoleFormDialog from './RoleFormDialog.vue'
import AssignPermissionDialog from './AssignPermissionDialog.vue'
import AssignUserDialog from './AssignUserDialog.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

// 表单弹窗状态
const dialogVisible = ref(false)
const dialogType = ref('add') // add | edit
const dialogRole = ref(null)

// 分配弹窗状态
const permVisible = ref(false)
const userVisible = ref(false)

async function loadPage() {
  loading.value = true
  try {
    const data = await roleApi.page({ ...query })
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

function openAdd() {
  dialogType.value = 'add'
  dialogRole.value = null
  dialogVisible.value = true
}

function openEdit(role) {
  dialogType.value = 'edit'
  dialogRole.value = role
  dialogVisible.value = true
}

function openAssignPermission(role) {
  dialogRole.value = role
  permVisible.value = true
}

function openAssignUser(role) {
  dialogRole.value = role
  userVisible.value = true
}

async function handleSave(form) {
  try {
    if (dialogType.value === 'add') {
      await roleApi.add(form)
      ElMessage.success('角色创建成功')
    } else {
      await roleApi.update(dialogRole.value.id, form)
      ElMessage.success('角色更新成功')
    }
    dialogVisible.value = false
    loadPage()
  } catch {
    // 后端业务校验失败（如名称/标识重复）时拦截器已提示，弹窗保持打开供修改
  }
}

async function handleAssignPermission(permissionIds) {
  try {
    await roleApi.assignPermissions(dialogRole.value.id, permissionIds)
    ElMessage.success('权限分配成功')
    permVisible.value = false
  } catch {
    // 后端校验失败时拦截器已提示
  }
}

async function handleAssignUser(userIds) {
  try {
    await roleApi.assignUsers(dialogRole.value.id, userIds)
    ElMessage.success('用户分配成功')
    userVisible.value = false
  } catch {
    // 后端校验失败时拦截器已提示
  }
}

async function handleDelete(role) {
  try {
    await ElMessageBox.confirm(
      `确定删除角色「${role.name}」吗？已分配给用户的角色将无法删除`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await roleApi.remove(role.id)
    ElMessage.success('角色删除成功')
    loadPage()
  } catch {
    // 后端业务校验失败时拦截器已提示，此处无需处理
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
</style>
