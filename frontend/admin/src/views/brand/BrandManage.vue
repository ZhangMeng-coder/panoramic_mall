<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="品牌名称关键字"
        clearable
        style="width: 240px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button v-perm="'goods:brand'" type="primary" @click="handleSearch">查询</el-button>
      <el-button v-perm="'goods:brand:add'" type="success" @click="openAdd">新增品牌</el-button>
    </div>

    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="品牌名称" min-width="140" />
      <el-table-column prop="logo" label="LOGO" min-width="120">
        <template #default="{ row }">
          <el-image
            v-if="row.logo"
            :src="row.logo"
            :preview-src-list="[row.logo]"
            preview-teleported
            fit="contain"
            style="width: 48px; height: 32px"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="简介" min-width="200" show-overflow-tooltip />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'goods:brand:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-perm="'goods:brand:delete'" link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <BrandFormDialog v-model="dialogVisible" :type="dialogType" :brand="dialogBrand" @save="handleSave" />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { brandApi } from '../../api/brand'
import BrandFormDialog from './BrandFormDialog.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, keyword: '' })

// 表单弹窗状态
const dialogVisible = ref(false)
const dialogType = ref('add') // add | edit
const dialogBrand = ref(null)

async function loadPage() {
  loading.value = true
  try {
    const data = await brandApi.page({ ...query })
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
  dialogBrand.value = null
  dialogVisible.value = true
}

function openEdit(brand) {
  dialogType.value = 'edit'
  dialogBrand.value = brand
  dialogVisible.value = true
}

async function handleSave(form) {
  try {
    if (dialogType.value === 'add') {
      await brandApi.add(form)
      ElMessage.success('品牌创建成功')
    } else {
      await brandApi.update(dialogBrand.value.id, form)
      ElMessage.success('品牌更新成功')
    }
    dialogVisible.value = false
    loadPage()
  } catch {
    // 后端业务校验失败（如重名）时拦截器已提示，弹窗保持打开供修改
  }
}

async function handleDelete(brand) {
  try {
    await ElMessageBox.confirm(
      `确定删除品牌「${brand.name}」吗？该品牌下存在商品时将无法删除`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await brandApi.remove(brand.id)
    ElMessage.success('品牌删除成功')
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
