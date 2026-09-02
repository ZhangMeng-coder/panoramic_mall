import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/category' },
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
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
