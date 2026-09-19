import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken, getUser, setUser } from '../store/auth'
import { authApi } from '../api/auth'
import { showToast } from '../composables/useToast'
import { LOGIN_REQUIRED_MSG } from '../api/request'
import { AUTH_PATHS } from './paths'

/**
 * 登录 / 注册页的路径。定义在叶子模块 `./paths`（断开 router ↔ request 的循环 import），
 * 这里原样转出，登录 / 注册页与请求层仍从各自熟悉的地方取同一份。
 */
export { AUTH_PATHS }

const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    // 首页**公开**：分类宫格接口 `/catalog/categories` 在免鉴权白名单里，游客可看
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
  // ⚠ 两条都 `requiresAuth`：首页免登录，**一涉及商品查询就要登录**（对应后端
  // `/catalog/goods`、`/catalog/facets` 不在免鉴权白名单里）。
  {
    path: '/search',
    name: 'search',
    component: () => import('../views/GoodsListView.vue'),
    meta: { title: '搜索结果', requiresAuth: true }
  },
  {
    path: '/category/:categoryId',
    name: 'category',
    component: () => import('../views/GoodsListView.vue'),
    meta: { title: '分类商品', requiresAuth: true }
  },
  // 兜底：未匹配路径回首页
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 全局守卫三条规则 —— 分级是「**首页公开，商品查询 / 详情需登录**」：
 *
 * 1) `requiresAuth` 页且本地无 token → 带去登录页并把原页塞进 `redirect`（登录后回原页，见
 *    `LoginView.redirectTarget()`）。这里**先拦**，比让页面发请求吃 401 更早、也不会白打一次接口。
 *    提示一并在这里弹：用户点了分类却被弹走，得知道为什么。
 * 2) 已登录（有 token）还去登录 / 注册页 → 回来源页，没有则回首页；
 * 3) 有 token 但内存里没有用户态（**刷新场景**）→ 拉 /auth/me 重建。
 *    这次调用带 `silent401`（见 `authApi.me()`），失败**只清本地态、不提示不跳转**——
 *    公开首页上的重建失败不该把游客弹去登录页，「未登录」对 mall 本就是合法状态。
 *
 * ⚠ 两条分支的先后不能换：先判 `requiresAuth` 才能让无 token 的游客**立刻**落到登录页；
 *    换成先重建用户态，会先白打一次 /auth/me 再跳。
 */
router.beforeEach(async (to) => {
  const token = getToken()

  if (to.meta.requiresAuth === true && !token) {
    showToast(LOGIN_REQUIRED_MSG, 'info')
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (token && AUTH_PATHS.includes(to.path)) {
    return typeof to.query.redirect === 'string' ? to.query.redirect : '/'
  }

  if (token && !getUser()) {
    try {
      setUser(await authApi.me())
    } catch {
      // 静默：本次按未登录继续（公开页照常打开，顶栏自己会显示「请登录」；
      // 需登录页的后续接口会再吃一次 401，由拦截器接管提示与跳转）
    }
  }

  return true
})

export default router
