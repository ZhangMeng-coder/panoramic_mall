<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { clearAuth, getToken, getUser } from '../store/auth'
import { authApi } from '../api/auth'
import { showToast } from '../composables/useToast'
import {
  clear as clearCartBadge,
  count as cartCount,
  refresh as refreshCartBadge
} from '../store/cart'

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

/**
 * 购物车徽标数（**购物车行数**，口径见 store/cart.ts）。
 * >99 折叠成「99+」：三位数会把 34px 高的顶栏那枚胶囊撑宽，把「我的订单」挤走。
 */
const badgeText = computed(() => (cartCount.value > 99 ? '99+' : String(cartCount.value)))

/**
 * 挂载时对一次数。顶栏是**每页各挂一个**的（首页 / 列表 / 详情 / 账号页都各写了一个
 * `<TopBar />`），所以「进入任一页面」就等于「重新对一次数」——加购后从详情页走到任何
 * 一页、或购物车页改完回来，数字都不会停在旧值上。
 * 未登录时 `refresh()` 自己不发请求（见 store/cart.ts，那里也写了为什么）。
 */
onMounted(() => {
  void refreshCartBadge()
})

/**
 * 退出：先请服务端下线（删 Redis 快照），**接口失败也照样本地清**（token 失效时本来就没得清）。
 * ⚠ 徽标必须在本地一起清掉：它是「这个人的车里有多少项」，留着会让下一个人（或未登录态的
 * 顶栏）挂着上一个人的数字。
 */
async function logout(): Promise<void> {
  try {
    await authApi.logout()
  } catch {
    // 忽略：本地登出照样生效
  }
  clearAuth()
  clearCartBadge()
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
        <!-- 购物车：**真实路由 + 真实计数**（原来是死链 + 写死的 3）。
             徽标只在有东西时出现；计数归 store/cart.ts 一处管（加购 / 登出都要动它） -->
        <router-link class="topbar__link" to="/cart">
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
          <span v-if="cartCount > 0" class="topbar__count tnum">{{ badgeText }}</span>
        </router-link>
        <span class="topbar__divider"></span>
        <!-- 我的订单：**真实路由**（原来是 `href="#"` 死链）。与上面「购物车」同一写法：
             router-link + 同一个 `.topbar__link` 类名 -->
        <router-link class="topbar__link" to="/orders">
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
        </router-link>
      </nav>
    </div>
  </header>
</template>
