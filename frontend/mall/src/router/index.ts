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
  // 商品详情：同属「涉及商品查询」→ 也要登录态（后端 `/catalog/goods/{id}` 不在免鉴权白名单里，
  // 路由不拦的话会先渲染出页面、再由接口 401 把用户弹走，白闪一屏）
  {
    path: '/goods/:id',
    name: 'goods',
    component: () => import('../views/GoodsDetailView.vue'),
    meta: { title: '商品详情', requiresAuth: true }
  },
  // 购物车：顾客自己的数据（车里的东西就是他的），与商品查询同级——**要登录态**。
  // 不挂在 /account 下：它是独立一级页（顶栏直达），不是「个人中心」的一个子页
  {
    path: '/cart',
    name: 'cart',
    component: () => import('../views/CartView.vue'),
    meta: { title: '购物车', requiresAuth: true }
  },
  // 个人中心：**二级结构**——父路由挂「左菜单 + 右内容」的外壳，内容各页是它的 children。
  // 顾客自己的数据一律要登录态（与「一涉及顾客数据就要登录」一致），故父子两级都标 requiresAuth。
  {
    path: '/account',
    component: () => import('../views/account/AccountLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      // 只写 `/account` 时落到默认页，免得右栏空着（菜单里没有指向父路径的项）
      { path: '', redirect: '/account/profile' },
      {
        path: 'profile',
        name: 'account-profile',
        component: () => import('../views/account/ProfileView.vue'),
        meta: { title: '个人资料', requiresAuth: true }
      },
      {
        path: 'addresses',
        name: 'account-addresses',
        component: () => import('../views/account/AddressView.vue'),
        meta: { title: '收货地址', requiresAuth: true }
      }
    ]
  },
  // 兜底：未匹配路径回首页
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 去登录页，并把原页塞进 `redirect`（登录后由 `LoginView.redirectTarget()` 回跳）。
 * 两处分支共用同一形状：本地根本没 token、以及有 token 但服务端不认了（重建失败）。
 */
function loginLocation(fullPath: string) {
  showToast(LOGIN_REQUIRED_MSG, 'info')
  return { path: '/login', query: { redirect: fullPath } }
}

/**
 * 全局守卫三条规则 —— 分级是「**首页公开，商品查询 / 详情需登录**」：
 *
 * 1) `requiresAuth` 页且本地无 token → 带去登录页并把原页塞进 `redirect`（登录后回原页，见
 *    `LoginView.redirectTarget()`）。这里**先拦**，比让页面发请求吃 401 更早、也不会白打一次接口。
 *    提示一并在这里弹：用户点了分类却被弹走，得知道为什么。
 * 2) 已登录（有 token）还去登录 / 注册页 → 回来源页，没有则回首页；
 * 3) 有 token 但内存里没有用户态（**刷新场景**）→ 拉 /auth/me 重建。
 *    这次调用带 `silent401`（见下方调用处），失败**只清本地态、不提示不跳转**——
 *    公开首页上的重建失败不该把游客弹去登录页，「未登录」对 mall 本就是合法状态；
 *    **需登录页上「会话真没了」时由这里自己跳登录页**（见下方 catch 的两类分述）。
 *
 * ⚠ 两条分支的先后不能换：先判 `requiresAuth` 才能让无 token 的游客**立刻**落到登录页；
 *    换成先重建用户态，会先白打一次 /auth/me 再跳。
 */
router.beforeEach(async (to) => {
  const token = getToken()

  if (to.meta.requiresAuth === true && !token) {
    return loginLocation(to.fullPath)
  }

  if (token && AUTH_PATHS.includes(to.path)) {
    return typeof to.query.redirect === 'string' ? to.query.redirect : '/'
  }

  if (token && !getUser()) {
    try {
      // ⚠ `silent401` 只在**这一处**传：这里是「刷新时顺手重建」，失败不该有副作用。
      // 别处（资料保存后刷新 store 等）走不带 silent401 的普通调用。
      setUser(await authApi.me({ silent401: true }))
    } catch {
      // 重建失败分两类，**处理必须分开**：
      //
      // ① **会话真的没了**（`/auth/me` 401）——`me()` 带 `silent401`：本地态已清、不提示不跳转。
      //    **需登录页必须在这里拦下**，不能指望「后续接口再吃一次 401，由拦截器接管」：到那时
      //    本地 token 已被 401 分支清掉，拦截器判不出「本来有登录态」（`sessionExpired` 靠
      //    `getToken()` 分辨），于是既不提示也不跳转，页面只会渲染成「商品暂不可用」
      //    —— 把「未登录」报成了「下游故障」。判据用 `!getToken()` 认这件事：**清态只有 401 分支会做**。
      // ② **网络 / 5xx / 超时等**（token 未被动过）——**不跳**，落回页面自己的降级（「…暂不可用」）。
      //    ⚠ 这里若也跳登录页会成环：token 还在 → 登录页命中下面规则 2 又被弹回本页 → 再 `me()`…
      //    直到 vue-router 的无限重定向保护中止导航，期间连打数次 `/auth/me`、连弹两个提示。
      //
      // 公开页两类都直接放行（游客是合法状态，顶栏自己会变「请登录」）。
      if (to.meta.requiresAuth === true && !getToken()) {
        return loginLocation(to.fullPath)
      }
    }
  }

  return true
})

export default router
