import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken, getUser, setUser, getDefaultPath } from '../store/auth'
import { isApproved, fetchMyShop } from '../store/shop'
import { authApi } from '../api/auth'

/**
 * 开店后业务入口路由（须审核通过才可见/可进入，本期均为假页面占位）
 */
const AFTER_APPROVED_PATHS = ['/goods', '/orders', '/stock']

const routes = [
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/register/RegisterView.vue'),
    meta: { title: '店主注册' }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/login/LoginView.vue'),
    meta: { title: '店主登录' }
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
        path: '/shop-info',
        name: 'shopInfo',
        component: () => import('../views/shop/ShopInfoView.vue'),
        meta: { title: '店铺信息' }
      },
      {
        path: '/goods',
        name: 'goods',
        component: () => import('../views/placeholder/PlaceholderView.vue'),
        meta: { title: '商品管理', description: '商品管理功能开发中，敬请期待。' }
      },
      {
        path: '/orders',
        name: 'orders',
        component: () => import('../views/placeholder/PlaceholderView.vue'),
        meta: { title: '订单管理', description: '订单管理功能开发中，敬请期待。' }
      },
      {
        path: '/stock',
        name: 'stock',
        component: () => import('../views/placeholder/PlaceholderView.vue'),
        meta: { title: '库存管理', description: '库存管理功能开发中，敬请期待。' }
      }
    ]
  },
  // 兜底：未匹配路径回默认落地页（守卫会处理登录态）
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 全局路由守卫：
 * 1) 未登录（无 token）→ 仅放行注册/登录，其余跳登录（带来源页）；
 * 2) 已登录访问注册/登录 → 回默认落地页；
 * 3) 已登录但内存无用户态（刷新场景）→ 先拉 /auth/me 重建，失败则登出；
 * 4) 访问 开店后业务入口(/goods|/orders|/stock) → 拉店铺态，未审核通过一律回店铺信息。
 */
router.beforeEach(async (to) => {
  const token = getToken()

  if (!token) {
    if (to.path === '/login' || to.path === '/register') return true
    return { path: '/login', query: to.path !== '/' ? { redirect: to.fullPath } : {} }
  }

  if (to.path === '/login' || to.path === '/register') {
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

  // 开店后业务入口：店铺必须审核通过，否则回到店铺信息
  if (AFTER_APPROVED_PATHS.includes(to.path)) {
    await fetchMyShop()
    if (!isApproved.value) {
      return '/shop-info'
    }
  }
  return true
})

export default router
