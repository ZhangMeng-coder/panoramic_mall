<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-button type="primary" @click="openAdd(null)">新增顶级分类</el-button>
    </div>

    <el-tree
      v-loading="loading"
      :data="treeData"
      node-key="id"
      :props="{ label: 'name', children: 'children' }"
      default-expand-all
      class="category-tree"
      @mouseleave="hoverId = null"
    >
      <template #default="{ node, data }">
        <div
          class="tree-node"
          :class="{ 'is-hover': hoverId === data.id }"
          @mouseenter="hoverId = data.id"
        >
          <span class="tree-node-label">
            {{ node.label }}
            <el-tag v-if="data.level === 3" size="small" type="info" effect="plain">末级</el-tag>
          </span>
          <span v-if="hoverId === data.id" class="tree-node-actions" @click.stop>
            <el-button v-if="data.level < 3" link type="primary" size="small" @click="openAdd(data)">新增子分类</el-button>
            <el-button link type="primary" size="small" @click="openEdit(data)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(data)">删除</el-button>
          </span>
        </div>
      </template>
    </el-tree>

    <el-empty v-if="!loading && !treeData.length" description="暂无分类，点击上方按钮新增" />

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
const treeData = ref([])
const hoverId = ref(null)

// 表单弹窗状态
const dialogVisible = ref(false)
const dialogType = ref('add') // add | edit
const dialogParent = ref(null)
const dialogCategory = ref(null)

async function loadTree() {
  loading.value = true
  try {
    treeData.value = await categoryApi.tree()
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

.category-tree {
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
}

.tree-node-actions {
  flex-shrink: 0;
  padding-left: 12px;
}
</style>
