<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { clearAuth, getToken, getUser } from '../store/auth'
import { authApi } from '../api/auth'
import { showToast } from '../composables/useToast'

const route = useRoute()

/**
 * 是否就在首页。
 * ⚠ 不能用 vue-router 的 `router-link-active`：目标是 `/`，它是所有路径的前缀，
 * 高亮会跟着跑到每一页（`router-link-exact-active` 又拿不到类名钩子），故显式判 path。
 */
const isHome = computed(() => route.path === '/')

/** 有 token 即视为已登录；用户信息可能还在后台重建（昵称先留空） */
const loggedIn = computed(() => Boolean(getToken()))
const nickname = computed(() => getUser()?.nickname || '')

/** 退出：先请服务端下线（删 Redis 快照），**接口失败也照样本地清**（token 失效时本来就没得清） */
async function logout(): Promise<void> {
  try {
    await authApi.logout()
  } catch {
    // 忽略：本地登出照样生效
  }
  clearAuth()
  showToast('已退出登录', 'success')
}
</script>

<template>
  <!-- ① 顶部用户条：左「登录信息」，右「首页 / 购物车 / 我的订单」 -->
  <header class="topbar">
    <div class="container topbar__inner">
      <div class="topbar__account">
        <span class="topbar__greet">
          <template v-if="loggedIn">Hi，<b>{{ nickname }}</b></template>
          <template v-else>你好，<router-link class="topbar__login" to="/login">请登录</router-link></template>
        </span>
        <span class="topbar__divider"></span>
        <!-- 个人中心：登录态下才有的入口（未登录态形态不动：请登录 / 免费注册） -->
        <router-link v-if="loggedIn" class="topbar__center" to="/account/profile">
          个人中心
        </router-link>
        <a v-if="loggedIn" class="topbar__logout" href="#" @click.prevent="logout">退出</a>
        <router-link v-else class="topbar__reg" to="/register">免费注册</router-link>
      </div>

      <nav class="topbar__nav">
        <!-- 首页入口：全站每一页的顶栏都有它（首页 / 列表页 / 账号页 / 商品详情页），
             这是「任何地方都能回首页」的唯一落点，不各页各写一份 -->
        <router-link class="topbar__link" :class="{ 'is-on': isHome }" to="/">
          <svg class="topbar__icon" viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M4 10.5L12 4l8 6.5V20H4z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linejoin="round"
            />
          </svg>
          首页
        </router-link>
        <span class="topbar__divider"></span>
        <a class="topbar__link" href="#">
          <svg class="topbar__icon" viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M3 4h2l2.4 11.2A2 2 0 0 0 9.36 17h8.5a2 2 0 0 0 1.96-1.6L21.5 7H6"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <circle cx="10" cy="20" r="1.3" fill="currentColor" />
            <circle cx="18" cy="20" r="1.3" fill="currentColor" />
          </svg>
          购物车
          <span class="topbar__count tnum">3</span>
        </a>
        <span class="topbar__divider"></span>
        <a class="topbar__link" href="#">
          <svg class="topbar__icon" viewBox="0 0 24 24" aria-hidden="true">
            <path
              d="M6 3h12a1 1 0 0 1 1 1v16l-3.5-2.2L12 20l-3.5-2.2L5 20V4a1 1 0 0 1 1-1z"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linejoin="round"
            />
            <path
              d="M9 8h6M9 12h6"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
          </svg>
          我的订单
        </a>
      </nav>
    </div>
  </header>
</template>
