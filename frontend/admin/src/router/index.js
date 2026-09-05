import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken, getUser, setUser, getDefaultPath } from '../store/auth'
import { authApi } from '../api/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/login/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layout/Layout.vue'),
    redirect: () => getDefaultPath(),
    children: [
      {
        path: '/home',
        name: 'home',
        component: () => import('../views/home/HomeView.vue'),
        meta: { title: '主页' }
      },
      {
        path: '/category',
        name: 'category',
        component: () => import('../views/category/CategoryManage.vue'),
        meta: { title: '分类管理' }
      },
      {
        path: '/brand',
        name: 'brand',
        component: () => import('../views/brand/BrandManage.vue'),
        meta: { title: '品牌管理' }
      },
      {
        path: '/spu',
        name: 'spu',
        component: () => import('../views/spu/SpuManage.vue'),
        meta: { title: '商品管理' }
      },
      {
        path: '/user',
        name: 'user',
        component: () => import('../views/user/UserManage.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: '/role',
        name: 'role',
        component: () => import('../views/role/RoleManage.vue'),
        meta: { title: '角色管理' }
      },
      {
        path: '/permission',
        name: 'permission',
        component: () => import('../views/permission/PermissionManage.vue'),
        meta: { title: '权限管理' }
      }
    ]
  },
  // 兜底：未匹配路径回首页（守卫会处理登录态）
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 全局路由守卫：
 * 1) 未登录（无 token）且目标非 /login → 跳 /login（带来源页）；
 * 2) 已登录访问 /login → 回默认落地页；
 * 3) 已登录但内存无用户态（刷新场景）→ 先拉 /auth/me 重建（含 perms），失败则登出。
 */
router.beforeEach(async (to) => {
  const token = getToken()

  if (!token) {
    if (to.path === '/login') return true
    return { path: '/login', query: to.path !== '/' ? { redirect: to.fullPath } : {} }
  }

  if (to.path === '/login') {
    return getDefaultPath()
  }

  if (!getUser()) {
    try {
      setUser(await authApi.me())
    } catch {
      // 拦截器已清态并跳登录页；此处确保不进入受保护页
      return { path: '/login' }
    }
  }
  return true
})

export default router
