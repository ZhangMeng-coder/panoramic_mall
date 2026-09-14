import { createRouter, createWebHashHistory } from 'vue-router'

/**
 * 当前只有首页一条路由（页面内容全为静态写死）。
 * 引入路由是为后续页面留位：mall 前台的登录 / 商品列表 / 详情等页届时直接加在这里。
 */
const routes = [
  {
    path: '/',
    name: 'home',
    component: () => import('../views/HomeView.vue'),
    meta: { title: '首页' }
  },
  // 兜底：未匹配路径回首页
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
