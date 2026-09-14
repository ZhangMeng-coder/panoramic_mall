import { ref } from 'vue'
import type { Ref } from 'vue'

export type ToastType = 'error' | 'success' | 'info'

export interface ToastItem {
  id: number
  msg: string
  type: ToastType
}

/** 提示条存活时长（只控制何时从队列摘掉，与 CSS 过渡时长无关） */
const DURATION = 2500

/**
 * 极简提示条队列 —— **模块级状态**，不依赖组件实例：
 * axios 拦截器是普通函数、没有组件上下文，那里也要能弹提示，
 * 所以队列不能挂在某个组件上。
 *
 * mall 前台禁用 Element Plus，因此不用 ElMessage，样式见 styles/account.css。
 */
const items: Ref<ToastItem[]> = ref([])
let seed = 0

export function showToast(msg: string, type: ToastType = 'error'): void {
  const id = ++seed
  items.value = [...items.value, { id, msg, type }]
  window.setTimeout(() => {
    dismissToast(id)
  }, DURATION)
}

export function dismissToast(id: number): void {
  items.value = items.value.filter((t) => t.id !== id)
}

export function useToast(): {
  items: Ref<ToastItem[]>
  showToast: typeof showToast
  dismissToast: typeof dismissToast
} {
  return { items, showToast, dismissToast }
}
