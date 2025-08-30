<template>
  <!-- 登录页：无侧边栏，全屏展示 -->
  <div v-if="isLoginPage" class="login-layout">
    <router-view />
  </div>

  <!-- 管理员布局 -->
  <div v-else-if="isAdmin" class="app-container">
    <aside class="sidebar admin-sidebar">
      <div class="logo">
        <h2>⚙️ 管理后台</h2>
        <div class="user-info">
          <span>管理员：{{ username }}</span>
        </div>
      </div>
      <nav>
        <el-menu :default-active="activeMenu" mode="vertical" class="admin-menu" @select="onAdminMenuSelect">
          <el-menu-item index="/admin/product">
            <el-icon><VideoPlay /></el-icon>
            <span>演出管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/ticket">
            <el-icon><Tickets /></el-icon>
            <span>票种管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/order">
            <el-icon><Document /></el-icon>
            <span>订单列表</span>
          </el-menu-item>
          <el-menu-item index="/admin/user">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
        </el-menu>
      </nav>
      <div class="logout-section">
        <el-button type="danger" @click="handleLogout" class="logout-button">退出登录</el-button>
      </div>
    </aside>
    <main class="main-content">
      <router-view />
    </main>
  </div>

  <!-- 普通用户布局 -->
  <div v-else class="app-container">
    <aside class="sidebar">
      <div class="logo">
        <h2>🎫 秒杀系统</h2>
        <div class="user-info">
          <span>用户：{{ username }}</span>
        </div>
      </div>
      <nav>
        <el-menu
          :default-active="activeMenu"
          mode="vertical"
          @select="onUserMenuSelect"
        >
          <el-menu-item index="/">
            <el-icon><House /></el-icon>
            <span>首页 / 演出列表</span>
          </el-menu-item>
          <el-sub-menu index="purchase">
            <template #title>
              <el-icon><ShoppingCart /></el-icon>
              <span>演出购票</span>
            </template>
            <el-menu-item index="/products?channel=seckill">
              <el-icon><DataLine /></el-icon>
              <span>秒杀票</span>
            </el-menu-item>
            <el-menu-item index="/products?channel=special">
              <el-icon><Present /></el-icon>
              <span>特价票</span>
            </el-menu-item>
            <el-menu-item index="/products?channel=regular">
              <el-icon><Tickets /></el-icon>
              <span>常规票</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item index="/orders">
            <el-icon><Document /></el-icon>
            <span>我的订单</span>
          </el-menu-item>
          <el-menu-item index="/my-tickets">
            <el-icon><CreditCard /></el-icon>
            <span>我的票</span>
          </el-menu-item>
          <el-menu-item index="/waiting">
            <el-icon><Clock /></el-icon>
            <span>候补记录</span>
          </el-menu-item>
          <el-menu-item index="/notifications" class="notification-menu-item">
            <el-icon><Bell /></el-icon>
            <span>消息通知</span>
            <el-badge v-if="unreadCount > 0" :value="unreadCount" :max="99" class="msg-badge" />
          </el-menu-item>
        </el-menu>
      </nav>
      <div class="logout-section">
        <el-button type="danger" @click="handleLogout" class="logout-button">退出登录</el-button>
      </div>
    </aside>
    <main class="main-content">
      <router-view @readMsg="fetchUnread" />
    </main>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { VideoPlay, Tickets, Document, ShoppingCart, CreditCard, Clock, Bell, DataLine, Present, House, User } from '@element-plus/icons-vue'
import { messageApi } from './api/index.js'

const router = useRouter()
const route = useRoute()
const activeMenu = ref('/')
const username = ref('')
const unreadCount = ref(0)
let pollTimer = null

const isLoginPage = computed(() => route.path === '/login')
const isAdmin = computed(() => {
  const role = localStorage.getItem('role')
  const loggedIn = localStorage.getItem('isLoggedIn') === 'true'
  console.log('[App] role=', role, 'isLoggedIn=', loggedIn, 'path=', route.path)
  return loggedIn && role === 'admin'
})

const computeActiveMenu = () => {
  // 优先用全路径（含 query），确保 /products?channel=seckill 可精确匹配
  const full = route.fullPath
  if (full.startsWith('/products?')) return full
  return route.path
}

const fetchUnread = async () => {
  if (isAdmin.value) return
  try {
    const userId = localStorage.getItem('userId')
    if (!userId) return
    const result = await messageApi.getUnreadCount(userId)
    if (result.code === 200) {
      unreadCount.value = Number(result.data) || 0
    }
  } catch (e) {
    // silent
  }
}

onMounted(() => {
  username.value = localStorage.getItem('username') || '未登录'
  activeMenu.value = computeActiveMenu()
  fetchUnread()
  pollTimer = setInterval(fetchUnread, 30000)
  window.addEventListener('refresh-unread', fetchUnread)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  window.removeEventListener('refresh-unread', fetchUnread)
})

// 监听路由变化（path + query 都可），刷新菜单高亮
watch(
  () => [route.path, JSON.stringify(route.query)],
  () => {
    activeMenu.value = computeActiveMenu()
    username.value = localStorage.getItem('username') || '未登录'
  }
)

const handleLogout = () => {
  localStorage.clear()
  if (pollTimer) clearInterval(pollTimer)
  router.push('/login')
}

const onUserMenuSelect = (index) => {
  router.push(index)
}

const onAdminMenuSelect = (index) => {
  router.push(index)
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.login-layout {
  min-height: 100vh;
}

.app-container {
  display: flex;
  min-height: 100vh;
  background: #f5f7fa;
}

.sidebar {
  width: 220px;
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 100%);
  color: white;
  padding: 20px 0;
  display: flex;
  flex-direction: column;
}

.sidebar.admin-sidebar {
  background: linear-gradient(180deg, #2c3e50 0%, #34495e 100%);
}

.logo {
  text-align: center;
  padding: 20px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
  margin-bottom: 20px;
}

.logo h2 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 10px;
}

.user-info {
  font-size: 12px;
  color: rgba(255,255,255,0.7);
}

nav {
  flex: 1;
  padding: 0 10px;
}

.notification-menu-item {
  position: relative;
}

.msg-badge {
  position: absolute;
  right: 20px;
  top: 50%;
  transform: translateY(-50%);
}

.logout-section {
  padding: 20px 10px;
  border-top: 1px solid rgba(255,255,255,0.1);
}

.logout-button {
  width: 100%;
}

.main-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}
</style>
