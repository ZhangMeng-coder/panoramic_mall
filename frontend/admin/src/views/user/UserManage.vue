<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="用户名 / 昵称 / 手机号关键字"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button type="success" @click="openAdd">新增用户</el-button>
    </div>

    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120">
        <template #default="{ row }">{{ row.nickname || '-' }}</template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" min-width="130">
        <template #default="{ row }">{{ row.phone || '-' }}</template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.email || '-' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="openAssignRole(row)">分配角色</el-button>
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

    <UserFormDialog v-model="dialogVisible" :type="dialogType" :user="dialogUser" @save="handleSave" />
    <AssignRoleDialog v-model="assignVisible" :user="dialogUser" @save="handleAssignRole" />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '../../api/user'
import UserFormDialog from './UserFormDialog.vue'
import AssignRoleDialog from './AssignRoleDialog.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '', status: undefined })

// 表单弹窗状态
const dialogVisible = ref(false)
const dialogType = ref('add') // add | edit
const dialogUser = ref(null)

// 分配角色弹窗状态
const assignVisible = ref(false)

async function loadPage() {
  loading.value = true
  try {
    const data = await userApi.page({ ...query })
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
  dialogUser.value = null
  dialogVisible.value = true
}

function openEdit(user) {
  dialogType.value = 'edit'
  dialogUser.value = user
  dialogVisible.value = true
}

function openAssignRole(user) {
  dialogUser.value = user
  assignVisible.value = true
}

async function handleSave(form) {
  try {
    if (dialogType.value === 'add') {
      await userApi.add(form)
      ElMessage.success('用户创建成功')
    } else {
      await userApi.update(dialogUser.value.id, form)
      ElMessage.success('用户更新成功')
    }
    dialogVisible.value = false
    loadPage()
  } catch {
    // 后端业务校验失败（如用户名重复）时拦截器已提示，弹窗保持打开供修改
  }
}

async function handleAssignRole(roleIds) {
  try {
    await userApi.assignRoles(dialogUser.value.id, roleIds)
    ElMessage.success('角色分配成功')
    assignVisible.value = false
  } catch {
    // 后端校验失败时拦截器已提示
  }
}

async function handleDelete(user) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户「${user.username}」吗？其角色分配记录将一并清除`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await userApi.remove(user.id)
    ElMessage.success('用户删除成功')
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
