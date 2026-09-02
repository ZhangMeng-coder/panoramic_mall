<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="420px"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item v-if="dialogTitleTip" label="上级分类">
        <el-input :model-value="dialogTitleTip" disabled />
      </el-form-item>
      <el-form-item label="分类名称" prop="name">
        <el-input v-model="form.name" maxlength="64" placeholder="请输入分类名称" />
      </el-form-item>
      <el-form-item label="排序" prop="sort">
        <el-input-number v-model="form.sort" :min="0" :max="99999" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="onUpdateVisible(false)">取消</el-button>
      <el-button type="primary" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** add | edit */
  type: { type: String, default: 'add' },
  /** 新增时作为上级的分类节点（null 表示顶级） */
  parent: { type: Object, default: null },
  /** 编辑时的分类节点 */
  category: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

const formRef = ref(null)
const form = ref({ name: '', sort: 0 })

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }]
}

const title = computed(() => {
  if (props.type === 'edit') return '编辑分类'
  return props.parent ? '新增子分类' : '新增顶级分类'
})

// 新增子分类时展示上级路径信息
const dialogTitleTip = computed(() => {
  if (props.type === 'add' && props.parent) {
    return props.parent.name
  }
  return null
})

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

function initForm() {
  if (props.type === 'edit' && props.category) {
    form.value = { name: props.category.name, sort: props.category.sort ?? 0 }
  } else {
    form.value = { name: '', sort: 0 }
  }
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  // 实际保存由父组件响应 save 事件完成，成功后父组件关闭弹窗
  emit('save', { ...form.value })
}
</script>
