<template>
  <div class="notifications-page">
    <div class="page-header">
      <h1>🔔 消息通知</h1>
      <div class="header-actions">
        <el-badge v-if="unreadCount > 0" :value="unreadCount" class="header-badge" />
        <el-button v-if="unreadCount > 0" type="primary" size="small" @click="markAllRead">
          全部标为已读
        </el-button>
      </div>
    </div>

    <div class="notification-list" v-loading="loading">
      <div
        v-for="msg in notifications"
        :key="msg.id"
        class="notification-card"
        :class="{ unread: msg.status === 0 }"
        @click="openDetail(msg)"
      >
        <div class="notification-header">
          <span class="notification-type" :class="getTypeClass(msg.type)">
            {{ getTypeName(msg.type) }}
          </span>
          <span class="notification-time">{{ formatTime(msg.createTime) }}</span>
        </div>
        <div class="notification-title" v-if="msg.title">
          {{ msg.title }}
        </div>
        <div class="notification-content">
          {{ msg.content }}
        </div>
        <div class="notification-footer">
          <span v-if="msg.status === 0" class="unread-tag">
            <span class="unread-dot"></span> 未读
          </span>
          <span v-else class="read-tag">已读</span>
        </div>
      </div>

      <div v-if="notifications.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无消息" />
      </div>
    </div>

    <!-- 消息详情弹窗 -->
    <el-dialog v-model="detailVisible" :title="detailMsg?.title || '消息详情'" width="520px" destroy-on-close>
      <div v-if="detailMsg" class="detail-body">
        <div class="detail-meta">
          <span class="detail-type" :class="getTypeClass(detailMsg.type)">{{ getTypeName(detailMsg.type) }}</span>
          <span class="detail-time">{{ formatTime(detailMsg.createTime) }}</span>
        </div>
        <div class="detail-content">{{ detailMsg.content }}</div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button v-if="detailMsg && detailMsg.status === 0" type="primary" @click="markOneRead(detailMsg)">
          标记已读
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { messageApi } from '../api/index.js'

const loading = ref(false)
const notifications = ref([])
const detailVisible = ref(false)
const detailMsg = ref(null)

const unreadCount = computed(() => {
  return notifications.value.filter(m => m.status === 0).length
})

onMounted(() => {
  loadNotifications()
})

const loadNotifications = async () => {
  loading.value = true
  try {
    const userId = localStorage.getItem('userId')
    if (!userId) {
      ElMessage.warning('请先登录')
      return
    }
    const result = await messageApi.getList(userId)
    if (result.code === 200) {
      notifications.value = result.data || []
    }
  } catch (error) {
    console.error('加载消息失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const openDetail = async (msg) => {
  detailMsg.value = msg
  detailVisible.value = true
  if (msg.status === 0) {
    await markOneRead(msg)
  }
}

const markOneRead = async (msg) => {
  try {
    await messageApi.readOne(msg.id)
    msg.status = 1
    // 触发父级刷新未读徽章
    fetchParentUnread()
  } catch (e) {
    // silent
  }
}

const markAllRead = async () => {
  try {
    const userId = localStorage.getItem('userId')
    if (!userId) return
    const result = await messageApi.readAll(userId)
    if (result.code === 200) {
      notifications.value.forEach(m => (m.status = 1))
      fetchParentUnread()
      ElMessage.success('已全部标记为已读')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

const fetchParentUnread = () => {
  // 通过事件通知 App.vue 刷新未读徽章（使用 provide/inject 或自定义事件）
  // 这里通过 location 事件或直接触发 API 都可
  // 简化：手动调用 fetch（通过 window 事件）
  window.dispatchEvent(new CustomEvent('refresh-unread'))
}

const getTypeName = (type) => {
  const map = {
    'waitlist_joined': '候补确认',
    'waitlist_notify': '候补通知',
    'order_paid': '购票成功',
    'order_cancelled': '订单取消',
    'order_timeout': '订单超时',
    'ticket_refund': '退票通知',
    'system': '系统消息'
  }
  return map[type] || '通知'
}

const getTypeClass = (type) => {
  const map = {
    'waitlist_joined': 'waitlist',
    'waitlist_notify': 'waitlist',
    'order_paid': 'paid',
    'order_cancelled': 'cancelled',
    'order_timeout': 'timeout',
    'ticket_refund': 'refund',
    'system': 'system'
  }
  return map[type] || 'default'
}

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days}天前`
  return `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
}
</script>

<style scoped>
.notifications-page {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-badge {
  margin-right: 4px;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notification-card {
  background: white;
  padding: 18px 20px;
  border-radius: 10px;
  border-left: 4px solid transparent;
  cursor: pointer;
  transition: all 0.15s;
}

.notification-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
}

.notification-card.unread {
  border-left-color: #409eff;
  background: #f6faff;
}

.notification-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.notification-type {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.notification-type.waitlist { background: #d1ecf1; color: #0c5460; }
.notification-type.paid { background: #d4edda; color: #155724; }
.notification-type.cancelled { background: #f8d7da; color: #721c24; }
.notification-type.timeout { background: #fff3cd; color: #856404; }
.notification-type.refund { background: #f8d7da; color: #721c24; }
.notification-type.system { background: #fff3cd; color: #856404; }
.notification-type.default { background: #f0f0f0; color: #666; }

.notification-time {
  font-size: 12px;
  color: #aaa;
}

.notification-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 6px;
}

.notification-content {
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notification-footer {
  margin-top: 10px;
}

.unread-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #409eff;
}

.read-tag {
  font-size: 12px;
  color: #bbb;
}

.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #409eff;
}

.empty-state {
  padding: 60px 0;
}

/* detail dialog */
.detail-body {
  padding: 10px 0;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-type {
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.detail-time {
  font-size: 13px;
  color: #999;
}

.detail-content {
  font-size: 15px;
  color: #333;
  line-height: 1.8;
  white-space: pre-wrap;
}
</style>
