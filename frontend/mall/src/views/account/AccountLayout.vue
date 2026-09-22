<script setup lang="ts">
import { RouterView } from 'vue-router'
import TopBar from '../../components/TopBar.vue'
import SiteFooter from '../../components/SiteFooter.vue'

/**
 * 个人中心外壳：**左菜单 + 右内容**的二级结构（新增视觉区块，形态在本工程里先定稿）。
 *
 * 顶栏 / 页脚沿用首页同一套（仓库约定：新增页面的顶栏 / 页脚沿用同一套），
 * 中间那块由本组件提供一次，各内容页（个人资料 / 收货地址）只写右侧内容——
 * 别让每页各搭一套左右分栏，那会立刻漂移成两个样子。
 * 样式全在 styles/account.css 的 `.acct*` 里（只消费 tokens.css 令牌、1280px、无媒体查询）。
 */

/**
 * 菜单项。两项都已有对应的 child 路由（`/account/profile`、`/account/addresses`），
 * 路径即入口、不再有占位项——新增内容页时这里加一项 + 路由加一个 child 即可，
 * 本组件不需要别的改动（左右分栏只在这里搭一次）。
 */
const menus = [
  { to: '/account/profile', label: '个人资料' },
  { to: '/account/addresses', label: '收货地址' }
]
</script>

<template>
  <TopBar />
  <main class="acct">
    <div class="container">
      <div class="acct__grid">
        <aside class="acct__side">
          <nav>
            <!-- active-class 用前缀语义：将来 `/account/profile/xxx` 这类子页仍应点亮「个人资料」 -->
            <router-link
              v-for="m in menus"
              :key="m.to"
              class="acct__menu-item"
              active-class="is-on"
              :to="m.to"
            >
              {{ m.label }}
            </router-link>
          </nav>
        </aside>

        <section class="acct__main">
          <RouterView />
        </section>
      </div>
    </div>
  </main>
  <SiteFooter />
</template>
