<template>
  <el-card shadow="never" class="home-card">
    <!-- 主页占位：展示内容暂定为空，后续在此填充欢迎面板/业务概览等 -->
    <el-empty description="主页 · 内容待定" :image-size="120" />
  </el-card>
</template>

<script setup>
// 主页占位页：对任意已登录用户可见，无按钮级权限门槛
</script>

<style scoped>
.home-card {
  min-height: 100%;
}
</style>
