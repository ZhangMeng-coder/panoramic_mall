<template>
  <el-dialog
    :model-value="modelValue"
    :title="type === 'edit' ? '编辑角色' : '新增角色'"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="角色名称" prop="name">
        <el-input v-model="form.name" maxlength="50" placeholder="请输入角色名称（如：超级管理员）" />
      </el-form-item>
      <el-form-item label="角色标识" prop="code">
        <el-input v-model="form.code" maxlength="50" placeholder="请输入角色标识（如：admin）" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="255"
          show-word-limit
          placeholder="请输入角色描述"
        />
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
import { ref } from 'vue'

const props = defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** add | edit */
  type: { type: String, default: 'add' },
  /** 编辑时的角色数据 */
  role: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

const formRef = ref(null)
const form = ref({ name: '', code: '', description: '', sort: 0 })

const rules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色标识', trigger: 'blur' }]
}

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

function initForm() {
  if (props.type === 'edit' && props.role) {
    form.value = {
      name: props.role.name,
      code: props.role.code,
      description: props.role.description || '',
      sort: props.role.sort ?? 0
    }
  } else {
    form.value = { name: '', code: '', description: '', sort: 0 }
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
