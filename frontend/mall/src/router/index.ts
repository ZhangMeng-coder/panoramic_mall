import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken, getUser, setUser } from '../store/auth'
import { authApi } from '../api/auth'

/**
 * 登录 / 注册页的路径。
 * 导出给两个页面用（回跳目标不能落在这些路径上，否则守卫会把自己弹给自己、成环）。
 */
export const AUTH_PATHS = ['/login', '/register']

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { title: '注册' }
  },
  // 商品列表页：搜索结果与分类商品**共用**同一个组件（靠路径分流模式）。
  // 两条都是公开页 —— 游客可以随便逛商品，不需要 meta.requiresAuth。
  {
    path: '/search',
    name: 'search',
    component: () => import('../views/GoodsListView.vue'),
    meta: { title: '搜索结果' }
  },
  {
    path: '/category/:categoryId',
    name: 'category',
    component: () => import('../views/GoodsListView.vue'),
    meta: { title: '分类商品' }
  },
  // 兜底：未匹配路径回首页
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 全局守卫只有两条规则 —— **mall 首页是公开页，不拦游客**：
 *
 * 1) 已登录（有 token）还去登录 / 注册页 → 回来源页，没有则回首页；
 * 2) 有 token 但内存里没有用户态（**刷新场景**）→ 拉 /auth/me 重建。
 *    失败**不跳转**（拦截器已清本地态），照常进入目标公开页 ——
 *    对 mall 来说「未登录」本就是合法状态，不是需要被纠正的异常。
 *
 * ⚠ 现在没有**任何**「必须登录才能进」的页面，所以守卫里没有 `meta.requiresAuth` 分支。
 * 将来真出现这种页时再加：`to.meta.requiresAuth && !getToken()` → 跳 `/login?redirect=`。
 */
router.beforeEach(async (to) => {
  const token = getToken()

  if (token && AUTH_PATHS.includes(to.path)) {
    return typeof to.query.redirect === 'string' ? to.query.redirect : '/'
  }

  if (token && !getUser()) {
    try {
      setUser(await authApi.me())
    } catch {
      // 静默：本次按未登录继续（首页等公开页照常打开，顶栏自己会显示「请登录」）
    }
  }

  return true
})

export default router
