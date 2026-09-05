<template>
  <el-dialog
    :model-value="modelValue"
    title="修改密码"
    width="420px"
    :close-on-click-modal="false"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @open="handleOpen"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" @submit.prevent>
      <el-form-item label="原密码" prop="oldPassword">
        <el-input
          v-model="form.oldPassword"
          type="password"
          show-password
          placeholder="请输入原密码"
          autocomplete="current-password"
        />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          placeholder="6-64 位新密码"
          autocomplete="new-password"
        />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          placeholder="再次输入新密码"
          autocomplete="new-password"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '../api/auth'

defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'success'])

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const rules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function handleOpen() {
  form.oldPassword = ''
  form.newPassword = ''
  form.confirmPassword = ''
  formRef.value && formRef.value.clearValidate()
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await authApi.changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功')
    emit('update:modelValue', false)
    emit('success')
  } catch {
    // 原密码错误等由拦截器统一提示，弹窗保持打开
  } finally {
    submitting.value = false
  }
}
</script>
