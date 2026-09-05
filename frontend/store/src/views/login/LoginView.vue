<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-head">
        <div class="auth-logo">
          <span class="logo-badge">店主</span>
          <span class="logo-title">全景商城 · 店铺端</span>
        </div>
        <p class="auth-sub">请使用店主账号登录</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            :prefix-icon="User"
            autocomplete="username"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-button
          class="auth-btn"
          type="primary"
          size="large"
          :loading="submitting"
          @click="handleSubmit"
        >登 录</el-button>
      </el-form>
      <div class="auth-foot">
        还没有账号？
        <router-link class="auth-link" to="/register">注册店主</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '../../api/auth'
import { setToken, setUser, getDefaultPath } from '../../store/auth'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const submitting = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 64, message: '密码长度 6-64 位', trigger: 'blur' }
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
    const data = await authApi.login({ username: form.username, password: form.password })
    setToken(data.token)
    setUser(data.user)
    ElMessage.success(`欢迎回来，${(data.user && (data.user.nickname || data.user.username)) || '店主'}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    router.replace(redirect && redirect.startsWith('/') ? redirect : getDefaultPath())
  } catch {
    // 账号/密码错误等由拦截器统一提示，停留登录页
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
  width: 380px;
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
