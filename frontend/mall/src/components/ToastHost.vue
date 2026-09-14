<script setup lang="ts">
import { useToast } from '../composables/useToast'

/**
 * 顶部居中的轻提示容器（挂 App.vue，全局一个）。
 * 样式在 src/styles/account.css，只消费 tokens.css 的令牌 —— 零 Element Plus。
 */
const { items, dismissToast } = useToast()
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <TransitionGroup name="toast">
      <div
        v-for="t in items"
        :key="t.id"
        class="toast"
        :class="`toast--${t.type}`"
        role="status"
        @click="dismissToast(t.id)"
      >
        {{ t.msg }}
      </div>
    </TransitionGroup>
  </div>
</template>
