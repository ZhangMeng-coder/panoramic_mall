<template>
  <el-container class="app-layout">
    <el-aside width="224px" class="app-aside">
      <div class="app-logo">
        <span class="logo-badge">店主</span>
        <span class="logo-title">全景商城 · 店铺端</span>
      </div>
      <!-- 静态菜单：主页 置顶恒可见；店铺信息恒可见；商品/订单/库存为开店后业务入口，仅审核通过后显示 -->
      <el-menu :default-active="activeMenu" router class="app-menu">
        <el-menu-item index="/home">
          <el-icon><HomeFilled /></el-icon>
          <span>主页</span>
        </el-menu-item>
        <el-menu-item index="/shop-info">
          <el-icon><component :is="Shop" /></el-icon>
          <span>店铺信息</span>
        </el-menu-item>
        <template v-if="isApproved">
          <el-menu-item index="/goods">
            <el-icon><component :is="Goods" /></el-icon>
            <span>商品管理</span>
          </el-menu-item>
          <el-menu-item index="/orders">
            <el-icon><component :is="Tickets" /></el-icon>
            <span>订单管理</span>
          </el-menu-item>
          <el-menu-item index="/stock">
            <el-icon><component :is="Box" /></el-icon>
            <span>库存管理</span>
          </el-menu-item>
        </template>
      </el-menu>
      <div v-if="!isApproved" class="aside-tip">店铺审核通过后，开放 商品 / 订单 / 库存 管理入口。</div>
    </el-aside>

    <el-container class="app-body" direction="vertical">
      <header class="app-topbar">
        <div class="app-topbar-title">{{ pageTitle }}</div>
        <div class="app-topbar-actions">
          <button
            class="app-theme-btn"
            type="button"
            :title="isDark ? '切换到浅色模式' : '切换到深色模式'"
            @click="toggleTheme"
          >
            <el-icon :size="16">
              <Sunny v-if="isDark" />
              <Moon v-else />
            </el-icon>
          </button>

          <el-dropdown trigger="click" @command="handleUserCommand">
            <div class="app-user">
              <el-avatar :size="28"><el-icon><User /></el-icon></el-avatar>
              <span class="app-user-name">{{ displayName }}</span>
              <el-icon :size="12" class="app-user-caret"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout" divided>
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authApi } from '../api/auth'
import { getUser, clearAuth } from '../store/auth'
import { isApproved, clearShop, fetchMyShop } from '../store/shop'
import { HomeFilled, Shop, Goods, Tickets, Box, User, Sunny, Moon, ArrowDown, SwitchButton } from '@element-plus/icons-vue'

const THEME_KEY = 'pm-store-theme'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title ? String(route.meta.title) : ''))
const user = computed(() => getUser())
const displayName = computed(() => {
  const u = user.value
  if (!u) return ''
  return u.nickname || u.username || '未登录'
})

async function handleUserCommand(command) {
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定退出登录吗？', '退出登录', {
        type: 'warning',
        confirmButtonText: '退出',
        cancelButtonText: '取消'
      })
    } catch {
      return // 用户取消
    }
    try {
      // 尽力通知服务端删 Redis 登录态；失败不阻断本地登出
      await authApi.logout()
    } catch {
      /* 忽略：本地清态仍执行 */
    }
    clearAuth()
    clearShop()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}

// —— 主题：.dark 挂到 <html>，联动 EP dark css-vars + 令牌暗色层；默认跟随系统，可切换并持久化 ——
const isDark = ref(false)
function resolveInitialTheme() {
  try {
    const saved = localStorage.getItem(THEME_KEY)
    if (saved) return saved === 'dark'
  } catch {
    /* localStorage 不可用时忽略 */
  }
  return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
}
function applyTheme(dark) {
  document.documentElement.classList.toggle('dark', dark)
  try {
    localStorage.setItem(THEME_KEY, dark ? 'dark' : 'light')
  } catch {
    /* ignore */
  }
}
function toggleTheme() {
  isDark.value = !isDark.value
}

isDark.value = resolveInitialTheme()
watch(isDark, applyTheme, { immediate: true })

watch(
  pageTitle,
  (title) => {
    document.title = title ? `${title} · 全景商城店铺端` : '全景商城店铺端'
  },
  { immediate: true }
)

onMounted(() => {
  // 进入任意店铺态页面即拉一次“我的店铺”，驱动侧栏开店后业务入口的显隐
  void fetchMyShop()
})
</script>

<style>
/* 布局与界面皮肤均以 EP 主题变量 + Design Token 驱动，浅/暗自动适配 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

.app-layout {
  height: 100%;
}

/* ---------- 侧边栏 ---------- */
.app-aside {
  display: flex;
  flex-direction: column;
  background-color: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);
}

.app-logo {
  height: 56px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--el-border-color-light);
}

.logo-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 28px;
  font-size: 12px;
  font-weight: var(--weight-semibold);
  color: #fff;
  background-color: var(--el-color-primary);
  border-radius: var(--radius-base);
}

.logo-title {
  font-size: 15px;
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
  letter-spacing: 0.3px;
}

.app-menu {
  flex: 1;
  overflow-y: auto;
  border-right: none;
  padding: var(--space-2);
}

.app-menu:not(.el-menu--collapse) {
  width: 100%;
}

.app-menu .el-menu-item {
  color: var(--el-text-color-regular);
  border-radius: var(--radius-base);
  margin-bottom: 2px;
}

.app-menu .el-menu-item:hover {
  color: var(--el-color-primary);
  background-color: var(--el-fill-color-light);
}

.app-menu .el-menu-item.is-active {
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
  font-weight: var(--weight-medium);
}

/* 深色下选中态用实心主题色，保证文字对比度 */
html.dark .app-menu .el-menu-item.is-active {
  color: #fff;
  background-color: var(--el-color-primary);
}

.aside-tip {
  margin: 0 var(--space-3) var(--space-3);
  padding: var(--space-3);
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
  background-color: var(--el-fill-color-light);
  border-radius: var(--radius-base);
}

/* ---------- 右侧内容区（顶栏 + 页面） ---------- */
.app-body {
  background-color: var(--el-bg-color-page);
}

.app-topbar {
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-4);
  background-color: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
}

.app-topbar-title {
  font-size: var(--text-base);
  font-weight: var(--weight-semibold);
  color: var(--el-text-color-primary);
}

.app-topbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.app-theme-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--radius-base);
  background-color: transparent;
  color: var(--el-text-color-regular);
  cursor: pointer;
  transition: color var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.app-theme-btn:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  background-color: var(--el-color-primary-light-9);
}

.app-user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: 0 var(--space-2);
  height: 34px;
  border-radius: var(--radius-base);
  cursor: pointer;
  outline: none;
  transition: background-color var(--duration-fast) var(--ease-standard);
}

.app-user:hover {
  background-color: var(--el-fill-color-light);
}

.app-user-name {
  font-size: var(--text-sm);
  color: var(--el-text-color-primary);
  max-width: 120px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.app-user-caret {
  color: var(--el-text-color-secondary);
}

.app-main {
  padding: var(--space-4);
  overflow-y: auto;
}
</style>
