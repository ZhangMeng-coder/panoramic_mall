<template>
  <el-dialog
    :model-value="modelValue"
    :title="type === 'edit' ? '编辑用户' : '新增用户'"
    width="520px"
    :close-on-click-modal="false"
    @update:model-value="onUpdateVisible"
    @open="initForm"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" maxlength="50" placeholder="请输入用户名（登录账号）" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          maxlength="64"
          :placeholder="type === 'edit' ? '留空表示不修改密码' : '请输入密码（6-64位）'"
        />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="form.nickname" maxlength="50" placeholder="请输入昵称/姓名" />
      </el-form-item>
      <el-form-item label="手机号" prop="phone">
        <el-input v-model="form.phone" maxlength="20" placeholder="请输入手机号" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="form.email" maxlength="100" placeholder="请输入邮箱" />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">停用</el-radio>
        </el-radio-group>
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
  /** 编辑时的用户数据 */
  user: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue', 'save'])

const formRef = ref(null)
const form = ref({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  email: '',
  status: 1
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    {
      validator: (rule, value, callback) => {
        if (props.type === 'add' && !value) {
          callback(new Error('请输入密码'))
        } else if (value && value.length < 6) {
          callback(new Error('密码长度不能少于6位'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

function onUpdateVisible(visible) {
  emit('update:modelValue', visible)
}

function initForm() {
  if (props.type === 'edit' && props.user) {
    // 编辑不回显密码
    form.value = {
      username: props.user.username,
      password: '',
      nickname: props.user.nickname || '',
      phone: props.user.phone || '',
      email: props.user.email || '',
      status: props.user.status ?? 1
    }
  } else {
    form.value = { username: '', password: '', nickname: '', phone: '', email: '', status: 1 }
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
  const payload = { ...form.value }
  if (props.type === 'edit' && !payload.password) {
    delete payload.password
  }
  emit('save', payload)
}
</script>
