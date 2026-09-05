<template>
  <el-card shadow="never">
    <el-result icon="info" :title="route.meta.title" :sub-title="description">
      <template #extra>
        <el-button type="primary" @click="router.push('/shop-info')">返回店铺信息</el-button>
      </template>
    </el-result>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

// 各占位页用路由 meta 提供差异化描述（见 router/index.js）
const description = computed(
  () => route.meta.description || '该功能为后续需求，本期仅作入口占位。'
)
</script>
