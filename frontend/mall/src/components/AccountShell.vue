<script setup lang="ts">
import TopBar from './TopBar.vue'
import SiteFooter from './SiteFooter.vue'

/**
 * 账号页外壳：顶栏与页脚**沿用首页同一套**（根 CLAUDE.md：新增页面的顶栏 / 页脚沿用同一套），
 * 两页（登录 / 注册）只写中间那张卡片，避免顶栏页脚各抄一份。
 */
</script>

<template>
  <TopBar />
  <main class="account">
    <div class="container">
      <div class="account__card">
        <slot />
      </div>
    </div>
  </main>
  <SiteFooter />
</template>
