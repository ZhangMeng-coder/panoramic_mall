<script setup lang="ts">
import ToastHost from './components/ToastHost.vue'
</script>

<template>
  <router-view />
  <!-- 全局唯一的提示条容器（表单错误 / 会话失效共用） -->
  <ToastHost />
</template>
