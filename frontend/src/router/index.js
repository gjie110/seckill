import { createRouter, createWebHistory } from 'vue-router'

// 路由配置
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/HomePage.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  {
    path: '/products',
    name: 'ProductList',
    component: () => import('../views/ProductList.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  {
    path: '/product/:id',
    name: 'ProductDetail',
    component: () => import('../views/ProductDetail.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  {
    path: '/ticket/:ticketTypeId',
    name: 'TicketDetail',
    component: () => import('../views/TicketDetail.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  {
    path: '/checkout',
    name: 'Checkout',
    component: () => import('../views/Checkout.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  {
    path: '/payment',
    name: 'Payment',
    component: () => import('../views/Payment.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  {
    path: '/orders',
    name: 'OrderList',
    component: () => import('../views/OrderList.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  {
    path: '/my-tickets',
    name: 'MyTickets',
    component: () => import('../views/MyTickets.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  {
    path: '/waiting',
    name: 'WaitingList',
    component: () => import('../views/WaitingList.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  {
    path: '/notifications',
    name: 'Notifications',
    component: () => import('../views/Notifications.vue'),
    meta: { requiresAuth: true, role: 'user' }
  },
  // 管理员页面路由 - 仅 admin 角色可访问
  {
    path: '/admin/product',
    name: 'AdminProductList',
    component: () => import('../views/AdminProductList.vue'),
    meta: { requiresAuth: true, role: 'admin' }
  },
  {
    path: '/admin/ticket',
    name: 'AdminTicketList',
    component: () => import('../views/AdminTicketList.vue'),
    meta: { requiresAuth: true, role: 'admin' }
  },
  {
    path: '/admin/order',
    name: 'AdminOrderList',
    component: () => import('../views/AdminOrderList.vue'),
    meta: { requiresAuth: true, role: 'admin' }
  },
  {
    path: '/admin/user',
    name: 'AdminUserList',
    component: () => import('../views/AdminUserList.vue'),
    meta: { requiresAuth: true, role: 'admin' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫 - 检查登录状态和角色权限
router.beforeEach((to, from, next) => {
  const isLoggedIn = localStorage.getItem('isLoggedIn') === 'true'
  const userRole = localStorage.getItem('role')

  // 1. 如果页面需要登录但未登录 -> 跳转到登录页
  if (to.meta.requiresAuth && !isLoggedIn) {
    next('/login')
    return
  }

  // 2. 如果已登录且访问登录页 -> 跳转
  if (to.path === '/login' && isLoggedIn) {
    if (userRole === 'admin') {
      next('/admin/product')
    } else {
      next('/')
    }
    return
  }

  // 3. 角色权限检查（双向拦截）
  if (to.meta.role === 'admin' && userRole !== 'admin') {
    next('/')
    return
  }
  if (to.meta.role === 'user' && userRole === 'admin') {
    next('/admin/product')
    return
  }

  next()
})

export default router
