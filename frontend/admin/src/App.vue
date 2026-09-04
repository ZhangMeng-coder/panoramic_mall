<template>
  <el-container class="app-layout">
    <el-aside width="224px" class="app-aside">
      <div class="app-logo">
        <span class="logo-badge">全景</span>
        <span class="logo-title">全景商城 · 后台</span>
      </div>
      <!-- 菜单由后台 /admin/permissions/menus 动态生成（目录→页面两层，先全量返回，预留按权限过滤） -->
      <el-menu
        v-if="menuReady"
        :default-active="activeMenu"
        :default-openeds="openedKeys"
        router
        class="app-menu"
      >
        <el-sub-menu v-for="dir in menuTree" :key="dir.id" :index="`m-${dir.id}`">
          <template #title>
            <el-icon><component :is="resolveIcon(dir.icon)" /></el-icon>
            <span>{{ dir.name }}</span>
          </template>
          <el-menu-item
            v-for="page in dir.children || []"
            :key="page.id"
            :index="page.route || `m-${page.id}`"
          >
            <el-icon><component :is="resolveIcon(page.icon)" /></el-icon>
            <span>{{ page.name }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
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
import { useRoute } from 'vue-router'
import { permissionApi } from './api/permission'
import {
  Menu,
  Goods,
  Box,
  Setting,
  User,
  Avatar,
  Lock,
  FolderOpened,
  Sunny,
  Moon
} from '@element-plus/icons-vue'

const THEME_KEY = 'pm-admin-theme'

const route = useRoute()
const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title ? String(route.meta.title) : ''))

// —— 侧边菜单：后台 /admin/permissions/menus 动态生成（目录→页面两层）——
// 用 v-if="menuReady" 保证 el-menu 首帧渲染即拿到完整数据，default-openeds 生效
const menuReady = ref(false)
const menuTree = ref([])
const openedKeys = computed(() => menuTree.value.map((dir) => `m-${dir.id}`))

// 图标：数据库存小写标识 → EP 图标组件；兼容别名，未知统一兜底 Menu
const ICON_MAP = {
  menu: Menu,
  goods: Goods,
  box: Box,
  setting: Setting,
  user: User,
  avatar: Avatar,
  team: Avatar,
  role: Avatar,
  lock: Lock,
  folder: FolderOpened,
  'folder-opened': FolderOpened
}
function resolveIcon(name) {
  const key = (name || '').toLowerCase()
  return ICON_MAP[key] || Menu
}

async function loadMenu() {
  try {
    menuTree.value = await permissionApi.menus()
  } catch {
    // 目录接口失败时侧栏留空（路由仍可直接访问），不阻断页面
    console.warn('菜单加载失败，侧边栏暂空')
    menuTree.value = []
  } finally {
    menuReady.value = true
  }
}
onMounted(loadMenu)

// 主题：.dark 挂到 <html>，联动 EP dark css-vars + 令牌暗色层；默认跟随系统，可切换并持久化
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
    document.title = title ? `${title} · 全景商城后台` : '全景商城后台'
  },
  { immediate: true }
)
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
  width: 28px;
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

.app-menu .el-menu-item,
.app-menu .el-sub-menu__title {
  color: var(--el-text-color-regular);
  border-radius: var(--radius-base);
  margin-bottom: 2px;
}

.app-menu .el-menu-item:hover,
.app-menu .el-sub-menu__title:hover {
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

.app-main {
  padding: var(--space-4);
  overflow-y: auto;
}
</style>
