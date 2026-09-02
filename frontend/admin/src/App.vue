<template>
  <el-container class="app-layout">
    <el-aside width="200px" class="app-aside">
      <div class="app-logo">全景商城 · 商品中台</div>
      <el-menu
        :default-active="activeMenu"
        router
        class="app-menu"
      >
        <el-menu-item index="/category">
          <el-icon><Menu /></el-icon>
          <span>分类管理</span>
        </el-menu-item>
        <el-menu-item index="/brand">
          <el-icon><Goods /></el-icon>
          <span>品牌管理</span>
        </el-menu-item>
        <el-menu-item index="/spu">
          <el-icon><Box /></el-icon>
          <span>商品管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Menu, Goods, Box } from '@element-plus/icons-vue'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
}

.app-layout {
  height: 100%;
}

.app-aside {
  background-color: #001529;
  color: #fff;
}

.app-logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.app-menu {
  border-right: none;
  background-color: #001529;
}

.app-menu .el-menu-item {
  color: rgba(255, 255, 255, 0.75);
}

.app-menu .el-menu-item.is-active {
  color: #fff;
  background-color: #409eff;
}

.app-menu .el-menu-item:hover {
  background-color: rgba(255, 255, 255, 0.1);
}

.app-main {
  background-color: #f5f7fa;
  padding: 16px;
  overflow-y: auto;
}
</style>
