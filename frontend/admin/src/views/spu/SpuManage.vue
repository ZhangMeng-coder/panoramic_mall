<template>
  <el-card shadow="never">
    <!-- 筛选区 -->
    <el-form inline class="filter-form">
      <el-form-item label="分类">
        <el-tree-select
          v-model="query.categoryId"
          :data="categoryOptions"
          :props="{ label: 'name', children: 'children' }"
          node-key="id"
          check-strictly
          clearable
          default-expand-all
          placeholder="全部分类"
          style="width: 180px"
        />
      </el-form-item>
      <el-form-item label="品牌">
        <el-select v-model="query.brandId" clearable filterable placeholder="全部品牌" style="width: 160px">
          <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 120px">
          <el-option label="展示中" :value="1" />
          <el-option label="已隐藏" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="名称">
        <el-input
          v-model="query.keyword"
          placeholder="商品名称关键字"
          clearable
          style="width: 180px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button v-perm="'goods:spu:list'" type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <el-button v-perm="'goods:spu:add'" type="success" @click="openAdd">新增商品</el-button>
      </el-form-item>
    </el-form>

    <!-- 商品列表 -->
    <el-table v-loading="loading" :data="records" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="mainImage" label="主图" width="90">
        <template #default="{ row }">
          <el-image
            v-if="row.mainImage"
            :src="row.mainImage"
            :preview-src-list="[row.mainImage]"
            preview-teleported
            fit="contain"
            style="width: 56px; height: 40px"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="categoryName" label="分类" min-width="110" />
      <el-table-column prop="brandName" label="品牌" min-width="110" />
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? '展示中' : '已隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="330" fixed="right">
        <template #default="{ row }">
          <el-button v-perm="'goods:spu:list'" link type="info" @click="openPreview(row)">预览</el-button>
          <el-button v-perm="'goods:spu:edit'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-perm="'goods:spu:edit'" link type="success" @click="openSku(row)">规格</el-button>
          <el-button
            v-if="row.status === 0"
            v-perm="'goods:spu:edit'"
            link
            type="success"
            @click="handleToggleStatus(row)"
          >展示</el-button>
          <el-button
            v-else
            v-perm="'goods:spu:edit'"
            link
            type="warning"
            @click="handleToggleStatus(row)"
          >隐藏</el-button>
          <el-button v-perm="'goods:spu:delete'" link type="danger" :disabled="row.status === 1" @click="handleDelete(row)">删除</el-button>
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

    <SpuFormDialog
      v-model="dialogVisible"
      :type="dialogType"
      :spu="dialogSpu"
      @save="handleSave"
    />
    <SpuSkuManageDialog v-model="skuDialogVisible" :spu="dialogSpu" @saved="handleSkuSaved" />
    <SpuPreviewDialog v-model="previewDialogVisible" :spu="dialogSpu" />
  </el-card>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { spuApi } from '../../api/spu'
import { brandApi } from '../../api/brand'
import { categoryApi } from '../../api/category'
import SpuFormDialog from './SpuFormDialog.vue'
import SpuSkuManageDialog from './SpuSkuManageDialog.vue'
import SpuPreviewDialog from './SpuPreviewDialog.vue'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const brands = ref([])
const categoryOptions = ref([])

const query = reactive({ pageNum: 1, pageSize: 10, categoryId: null, brandId: null, status: null, keyword: '' })

// 弹窗状态：编辑（基本信息+规格配置） / 规格（SKU 管理） / 预览
const dialogVisible = ref(false)
const dialogType = ref('add') // add | edit
const dialogSpu = ref(null) // 共享的 SpuDetailVO（三个弹窗复用，打开前各自刷新拉取）
const skuDialogVisible = ref(false)
const previewDialogVisible = ref(false)

async function loadOptions() {
  const [brandList, tree] = await Promise.all([brandApi.list(), categoryApi.tree()])
  brands.value = brandList
  const walk = (nodes) =>
    nodes.map((n) => ({
      id: n.id,
      name: n.name,
      children: walk(n.children || [])
    }))
  categoryOptions.value = walk(tree)
}

async function loadPage() {
  loading.value = true
  try {
    const data = await spuApi.page({ ...query })
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
  query.categoryId = null
  query.brandId = null
  query.status = null
  query.keyword = ''
  handleSearch()
}

async function openAdd() {
  dialogType.value = 'add'
  dialogSpu.value = null
  dialogVisible.value = true
}

async function openEdit(row) {
  try {
    const detail = await spuApi.detail(row.id)
    dialogType.value = 'edit'
    dialogSpu.value = detail
    dialogVisible.value = true
  } catch {
    // 详情加载失败时拦截器已提示
  }
}

/** 打开 SKU/规格管理弹窗（每次拉最新详情，避免 SKU 与规格配置陈旧） */
async function openSku(row) {
  try {
    dialogSpu.value = await spuApi.detail(row.id)
    skuDialogVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

/** 打开只读预览弹窗 */
async function openPreview(row) {
  try {
    dialogSpu.value = await spuApi.detail(row.id)
    previewDialogVisible.value = true
  } catch {
    // 拦截器已提示
  }
}

/** 规格/SKU 保存成功：关闭弹窗并刷新列表 */
function handleSkuSaved() {
  skuDialogVisible.value = false
  loadPage()
}

async function handleSave(payload) {
  try {
    if (dialogType.value === 'add') {
      await spuApi.add(payload)
      ElMessage.success('商品创建成功')
    } else {
      await spuApi.update(dialogSpu.value.id, payload)
      ElMessage.success('商品更新成功')
    }
    dialogVisible.value = false
    loadPage()
  } catch {
    // 后端业务校验失败（如 SKU 组合重复）时拦截器已提示，弹窗保持打开供修改
  }
}

async function handleToggleStatus(row) {
  const target = row.status === 1 ? 0 : 1
  try {
    await spuApi.updateStatus(row.id, target)
    ElMessage.success(target === 1 ? '商品已设为展示' : '商品已设为隐藏')
    loadPage()
  } catch {
    // 拦截器已提示
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除商品「${row.name}」吗？展示中的商品需先隐藏`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await spuApi.remove(row.id)
    ElMessage.success('商品删除成功')
    loadPage()
  } catch {
    // 拦截器已提示
  }
}

onMounted(async () => {
  await loadOptions()
  loadPage()
})
</script>

<style scoped>
.filter-form {
  margin-bottom: 4px;
}

.filter-form .el-form-item {
  margin-bottom: 8px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
