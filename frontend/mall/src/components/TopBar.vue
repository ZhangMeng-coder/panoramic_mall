<script setup lang="ts">
import { loggedIn } from '../mock/session'
</script>

<template>
  <!-- ① 顶部用户条：左「登录信息」，右「购物车 / 我的订单」 -->
  <header class="topbar">
    <div class="container topbar__inner">
      <div class="topbar__account">
        <span class="topbar__greet">
          <template v-if="loggedIn">Hi，<b>张小明</b></template>
          <template v-else>你好，<a class="topbar__login" href="#">请登录</a></template>
        </span>
        <span class="topbar__divider"></span>
        <a v-if="loggedIn" class="topbar__logout" href="#">退出</a>
        <a v-else class="topbar__reg" href="#">免费注册</a>
      </div>

      <nav class="topbar__nav">
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
