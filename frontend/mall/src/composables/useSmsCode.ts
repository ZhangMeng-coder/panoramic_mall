import { onUnmounted, ref } from 'vue'
import type { Ref } from 'vue'
import { authApi } from '../api/auth'
import { showToast } from './useToast'

/**
 * 手机号格式 —— 与后端 SmsCodeDTO / RegisterDTO / LoginDTO 的 `@Pattern("^1[3-9]\\d{9}$")` 同口径。
 * ⚠ 前端校验只是**快速反馈，不是防线**：后端照旧全量校验。
 */
export const PHONE_RE = /^1[3-9]\d{9}$/

/** 取码后的本地倒计时秒数（后端无频控，这里纯防连点） */
const COUNTDOWN_SECONDS = 60

/**
 * 「获取验证码」的共用逻辑（登录页与注册页共用一份）：
 * 校验手机号 → 调取码接口 → 成功则起 60s 本地倒计时（倒计时中按钮禁用）。
 */
export function useSmsCode(phone: Ref<string>): {
  countdown: Ref<number>
  send: () => Promise<void>
} {
  const countdown = ref(0)
  let timer: number | undefined

  function stop(): void {
    if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
    countdown.value = 0
  }

  async function send(): Promise<void> {
    if (countdown.value > 0) return

    const value = phone.value.trim()
    if (!PHONE_RE.test(value)) {
      showToast('请输入正确的手机号')
      return
    }

    try {
      await authApi.sendSmsCode(value)
      showToast('验证码已发送', 'success')
      countdown.value = COUNTDOWN_SECONDS
      timer = window.setInterval(() => {
        countdown.value -= 1
        if (countdown.value <= 0) stop()
      }, 1000)
    } catch {
      // 拦截器已弹提示，这里不重复
    }
  }

  onUnmounted(stop)

  return { countdown, send }
}
