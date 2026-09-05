<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-head">
        <div class="auth-logo">
          <span class="logo-badge">店主</span>
          <span class="logo-title">全景商城 · 店铺端</span>
        </div>
        <p class="auth-sub">注册店主账号（注册后请先维护店铺信息并提交审核）</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名（3-50位字母/数字/下划线）"
            :prefix-icon="User"
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item prop="nickname">
          <el-input
            v-model="form.nickname"
            placeholder="昵称（选填）"
            :prefix-icon="Avatar"
            autocomplete="nickname"
          />
        </el-form-item>
        <el-form-item prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="手机号（选填）"
            :prefix-icon="Iphone"
            autocomplete="tel"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码（6-64位）"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="new-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button
          class="auth-btn"
          type="primary"
          size="large"
          :loading="submitting"
          @click="handleSubmit"
        >注 册</el-button>
      </el-form>
      <div class="auth-foot">
        已有账号？
        <router-link class="auth-link" to="/login">去登录</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar, Iphone } from '@element-plus/icons-vue'
import { authApi } from '../../api/auth'
import { setToken, setUser, getDefaultPath } from '../../store/auth'

const router = useRouter()

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ username: '', nickname: '', phone: '', password: '', confirmPassword: '' })

const USERNAME_RE = /^[a-zA-Z0-9_]{3,50}$/
const PHONE_RE = /^1[3-9]\d{9}$/

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: USERNAME_RE, message: '用户名需为3-50位字母/数字/下划线', trigger: 'blur' }
  ],
  nickname: [{ max: 50, message: '昵称不能超过50个字符', trigger: 'blur' }],
  phone: [
    {
      validator: (rule, value, callback) => {
        if (value && !PHONE_RE.test(value)) {
          callback(new Error('手机号格式不正确'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function handleSubmit() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    const data = await authApi.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname,
      phone: form.phone
    })
    setToken(data.token)
    setUser(data.user)
    ElMessage.success('注册成功，欢迎开店')
    router.replace(getDefaultPath())
  } catch {
    // 用户名重复等由拦截器统一提示，停留注册页
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.auth-page {
  height: 100vh;
  width: 100vw;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(1200px 600px at 85% -10%, var(--color-primary-light-6, rgba(79, 70, 229, 0.18)), transparent 60%),
    radial-gradient(900px 500px at -10% 110%, var(--color-secondary-light-6, rgba(14, 165, 233, 0.14)), transparent 55%),
    var(--el-bg-color-page);
}

.auth-card {
  width: 400px;
  padding: var(--space-6, 24px) var(--space-5, 20px);
  background-color: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--radius-lg, 12px);
  box-shadow: var(--shadow-lg, 0 12px 32px rgba(15, 23, 42, 0.12));
}

.auth-head {
  margin-bottom: var(--space-5, 20px);
}

.auth-logo {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.logo-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 34px;
  font-size: 14px;
  font-weight: var(--weight-semibold);
  color: #fff;
  background-color: var(--el-color-primary);
  border-radius: var(--radius-base);
}

.logo-title {
  font-size: 17px;
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: 0.3px;
}

.auth-sub {
  margin: var(--space-3) 0 0;
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.auth-btn {
  width: 100%;
  margin-top: var(--space-1, 4px);
  letter-spacing: 4px;
}

.auth-foot {
  margin-top: var(--space-4);
  text-align: center;
  font-size: var(--text-sm);
  color: var(--el-text-color-secondary);
}

.auth-link {
  color: var(--el-color-primary);
  text-decoration: none;
}
</style>
