import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 静态路由表 + meta.perm 权限码过滤：
 * - 路由全量注册（不动态 addRoute），守卫按 /me 返回的 permissionCodes 裁剪
 * - 无权限码命中的页面 → /403（自定义角色新增权限无需改前端路由，天然支持）
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/index.vue'),
    meta: { guest: true, title: '登录' },
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'Odometer', perm: 'menu:dashboard' },
      },
      {
        path: 'products',
        name: 'products',
        component: () => import('@/views/products/index.vue'),
        meta: { title: '商品管理', icon: 'Goods', perm: 'menu:product' },
      },
      {
        path: 'inventory',
        name: 'inventory',
        component: () => import('@/views/inventory/index.vue'),
        meta: { title: '库存管理', icon: 'Box', perm: 'menu:inventory' },
      },
      {
        path: 'orders',
        name: 'orders',
        component: () => import('@/views/orders/index.vue'),
        meta: { title: '订单管理', icon: 'List', perm: 'menu:order' },
      },
      {
        path: 'refund',
        name: 'refund',
        component: () => import('@/views/refund/index.vue'),
        meta: { title: '退款售后', icon: 'RefreshLeft', perm: 'menu:refund' },
      },
      {
        path: 'settlement',
        name: 'settlement',
        component: () => import('@/views/settlement/index.vue'),
        meta: { title: '结算管理', icon: 'Money', perm: 'menu:settlement' },
      },
      {
        path: 'deposit',
        name: 'deposit',
        component: () => import('@/views/deposit/index.vue'),
        meta: { title: '保证金管理', icon: 'Wallet', perm: 'store:deposit:confirm' },
      },
      {
        path: 'stores',
        name: 'stores',
        component: () => import('@/views/stores/index.vue'),
        meta: { title: '我的门店', icon: 'Shop', perm: 'menu:store' },
      },
      {
        path: 'staff',
        name: 'staff',
        component: () => import('@/views/staff/index.vue'),
        meta: { title: '员工管理', icon: 'User', perm: 'store:staff:manage' },
      },
      {
        path: 'member',
        name: 'member',
        component: () => import('@/views/member/index.vue'),
        meta: { title: '会员管理', icon: 'UserFilled', perm: 'menu:member' },
      },
      {
        path: 'coupon',
        name: 'coupon',
        component: () => import('@/views/coupon/index.vue'),
        meta: { title: '优惠券管理', icon: 'Ticket', perm: 'menu:marketing' },
      },
      {
        path: 'roles',
        name: 'roles',
        component: () => import('@/views/roles/index.vue'),
        meta: { title: '角色权限', icon: 'Lock', perm: 'system:role:config' },
      },
    ],
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '无权限' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/403.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 未恢复会话先 bootstrap（有 refreshToken 则静默换新，页面刷新不丢登录态）
  if (!auth.bootstrapped) {
    await auth.bootstrap()
  }

  // 登录页：已登录则回工作台
  if (to.meta.guest) {
    return auth.isLoggedIn ? { path: '/dashboard' } : true
  }

  // 受保护路由：未登录 → 登录页
  if (!auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  // 权限过滤：meta.perm 未命中 → 403（403 页本身放行）
  const perm = to.meta.perm as string | undefined
  if (perm && !auth.hasPermission(perm)) {
    return { path: '/403' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · 养生茶管理后台` : '养生茶管理后台'
})

export default router
