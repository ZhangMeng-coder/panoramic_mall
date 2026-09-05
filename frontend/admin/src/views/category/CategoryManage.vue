<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button v-perm="'goods:category:add'" type="primary" @click="openAdd(null)">新增顶级分类</el-button>
    </div>

    <!-- 树形数据用 el-table 的树类型展示（整棵一次加载，非懒加载） -->
    <el-table
      v-loading="loading"
      class="category-table"
      :data="tableData"
      row-key="id"
      default-expand-all
    >
      <el-table-column label="分类名称" min-width="360">
        <template #default="{ row }">
          <span class="cat-name">{{ row.name }}</span>
          <el-tag v-if="row.level === 3" size="small" type="info" effect="plain">末级</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="120" align="center" />
      <el-table-column label="操作" width="250" align="center">
        <template #default="{ row }">
          <span class="row-actions">
            <el-button
              v-if="row.level < 3"
              v-perm="'goods:category:add'"
              link
              type="primary"
              size="small"
              @click="openAdd(row)"
            >新增子分类</el-button>
            <el-button v-perm="'goods:category:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-perm="'goods:category:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </span>
        </template>
      </el-table-column>
      <template #empty>暂无分类，点击上方按钮新增</template>
    </el-table>

    <CategoryFormDialog
      v-model="dialogVisible"
      :type="dialogType"
      :parent="dialogParent"
      :category="dialogCategory"
      @save="handleSave"
    />
  </el-card>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi } from '../../api/category'
import CategoryFormDialog from './CategoryFormDialog.vue'

const loading = ref(false)
const tableData = ref([])

// 表单弹窗状态
const dialogVisible = ref(false)
const dialogType = ref('add') // add | edit
const dialogParent = ref(null)
const dialogCategory = ref(null)

async function loadTree() {
  loading.value = true
  try {
    tableData.value = await categoryApi.tree()
  } finally {
    loading.value = false
  }
}

function openAdd(parent) {
  dialogType.value = 'add'
  dialogParent.value = parent
  dialogCategory.value = null
  dialogVisible.value = true
}

function openEdit(category) {
  dialogType.value = 'edit'
  dialogParent.value = null
  dialogCategory.value = category
  dialogVisible.value = true
}

async function handleSave(form) {
  try {
    if (dialogType.value === 'add') {
      const payload = {
        parentId: dialogParent.value ? dialogParent.value.id : 0,
        name: form.name,
        sort: form.sort
      }
      await categoryApi.add(payload)
      ElMessage.success('分类创建成功')
    } else {
      const payload = { name: form.name, sort: form.sort }
      await categoryApi.update(dialogCategory.value.id, payload)
      ElMessage.success('分类更新成功')
    }
    dialogVisible.value = false
    loadTree()
  } catch {
    // 后端业务校验失败（如重名）时拦截器已提示，弹窗保持打开供修改
  }
}

async function handleDelete(category) {
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${category.name}」吗？存在子分类或商品时将无法删除`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return // 用户取消
  }
  try {
    await categoryApi.remove(category.id)
    ElMessage.success('分类删除成功')
    loadTree()
  } catch {
    // 后端业务校验失败（存在子分类/商品）时拦截器已提示，此处无需处理
  }
}

onMounted(loadTree)
</script>

<style scoped>
.toolbar {
  margin-bottom: 12px;
}

.category-table {
  min-height: 60px;
}

.cat-name {
  font-weight: 500;
  margin-right: 6px;
}
</style>

<!-- 行操作仅在鼠标悬停当前行时显示（slot 内容带 scoped 属性，需放开作用域） -->
<style>
.category-table .row-actions {
  visibility: hidden;
}

.category-table .el-table__row:hover .row-actions {
  visibility: visible;
}
</style>
