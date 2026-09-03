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

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

export default router
