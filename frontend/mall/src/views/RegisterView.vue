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
const nickname = ref('')
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
  const nick = nickname.value.trim()
  if (nick.length > 50) {
    showToast('昵称最长 50 个字')
    return
  }

  loading.value = true
  try {
    // 注册即登录：后端直接返回 token + user
    const res = await authApi.register({
      phone: value,
      code: smsCode,
      nickname: nick || undefined
    })
    setToken(res.token)
    setUser(res.user)
    showToast('注册成功，已自动登录', 'success')
    await router.replace(redirectTarget())
  } catch {
    // 拦截器已弹后端 msg（如「验证码错误」「手机号已注册」）
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <AccountShell>
    <h1 class="account__title">免费注册</h1>
    <p class="account__sub">手机号即账号，注册成功后自动登录</p>

    <form class="account__form" autocomplete="off" @submit.prevent="submit">
      <div class="field">
        <label class="field__label" for="regPhone">手机号</label>
        <div class="field__box">
          <input
            id="regPhone"
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
        <label class="field__label" for="regCode">验证码</label>
        <div class="field__box">
          <input
            id="regCode"
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

      <div class="field">
        <label class="field__label" for="regNickname">昵称（选填）</label>
        <div class="field__box">
          <input
            id="regNickname"
            v-model="nickname"
            class="field__input"
            type="text"
            maxlength="50"
            placeholder="不填则用手机号"
          />
        </div>
      </div>

      <p class="account__tip">演示环境：短信为模拟通道，验证码固定 <b>888888</b></p>

      <button class="btn-primary" type="submit" :disabled="loading">
        {{ loading ? '注册中…' : '注册并登录' }}
      </button>
    </form>

    <p class="account__switch">已有账号？<router-link to="/login">直接登录</router-link></p>
  </AccountShell>
</template>
