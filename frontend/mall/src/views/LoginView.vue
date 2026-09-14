<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AccountShell from '../components/AccountShell.vue'
import { authApi } from '../api/auth'
import { setToken, setUser } from '../store/auth'
import { showToast } from '../composables/useToast'
import { PHONE_RE, useSmsCode } from '../composables/useSmsCode'
import { AUTH_PATHS } from '../router'

const route = useRoute()
const router = useRouter()

const phone = ref('')
const code = ref('')
const loading = ref(false)

const { countdown, send: sendCode } = useSmsCode(phone)

/** 回跳目标：只认站内路径，且不能是登录 / 注册页本身（否则守卫会把自己弹给自己，成环） */
function redirectTarget(): string {
  const r = route.query.redirect
  if (typeof r === 'string' && r.startsWith('/') && !AUTH_PATHS.includes(r)) {
    return r
  }
  return '/'
}

async function submit(): Promise<void> {
  const value = phone.value.trim()
  if (!PHONE_RE.test(value)) {
    showToast('请输入正确的手机号')
    return
  }
  const smsCode = code.value.trim()
  if (!smsCode) {
    showToast('请输入验证码')
    return
  }

  loading.value = true
  try {
    const res = await authApi.login({ phone: value, code: smsCode })
    setToken(res.token)
    setUser(res.user)
    showToast('登录成功', 'success')
    await router.replace(redirectTarget())
  } catch {
    // 拦截器已弹后端 msg（如「验证码错误」「手机号未注册」）
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AccountShell>
    <h1 class="account__title">登录</h1>
    <p class="account__sub">手机号 + 短信验证码，登录后可享会员价、查看订单与优惠券</p>

    <form class="account__form" autocomplete="off" @submit.prevent="submit">
      <div class="field">
        <label class="field__label" for="loginPhone">手机号</label>
        <div class="field__box">
          <input
            id="loginPhone"
            v-model="phone"
            class="field__input"
            type="tel"
            maxlength="11"
            inputmode="numeric"
            placeholder="请输入 11 位手机号"
          />
        </div>
      </div>

      <div class="field">
        <label class="field__label" for="loginCode">验证码</label>
        <div class="field__box">
          <input
            id="loginCode"
            v-model="code"
            class="field__input"
            type="text"
            maxlength="6"
            inputmode="numeric"
            placeholder="6 位验证码"
          />
          <button class="field__code" type="button" :disabled="countdown > 0" @click="sendCode">
            {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
          </button>
        </div>
      </div>

      <p class="account__tip">演示环境：短信为模拟通道，验证码固定 <b>888888</b></p>

      <button class="btn-primary" type="submit" :disabled="loading">
        {{ loading ? '登录中…' : '登 录' }}
      </button>
    </form>

    <p class="account__switch">还没账号？<router-link to="/register">免费注册</router-link></p>
  </AccountShell>
</template>
