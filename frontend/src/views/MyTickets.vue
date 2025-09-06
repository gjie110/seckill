<template>
  <div class="my-tickets">
    <div class="page-header">
      <h1>🎫 我的票</h1>
    </div>

    <div class="ticket-list" v-loading="loading">
      <div v-for="ticket in tickets" :key="ticket.id" class="ticket-card">
        <div class="ticket-header">
          <div class="ticket-info">
            <p class="product-name" v-if="ticket.productName">{{ ticket.productName }}</p>
            <h3>{{ ticket.ticketTypeName }}</h3>
            <p class="seat-info" v-if="ticket.seatInfo">{{ ticket.seatInfo }}</p>
            <p class="ticket-price">¥{{ ticket.price }}</p>
          </div>
          <div class="ticket-qrcode">
            <div class="qrcode-placeholder">
              <el-icon><Search /></el-icon>
              <span>扫码入场</span>
            </div>
          </div>
        </div>

        <div class="ticket-body">
          <div class="info-row">
            <span class="label">票号：</span>
            <span class="value">{{ ticket.ticketNo }}</span>
          </div>
          <div class="info-row" v-if="ticket.productName">
            <span class="label">演出：</span>
            <span class="value">{{ ticket.productName }}</span>
          </div>
          <div class="info-row">
            <span class="label">票种：</span>
            <span class="value">{{ ticket.ticketTypeName }}</span>
          </div>
        </div>

        <div class="ticket-footer">
          <span class="status-tag" :class="getStatusClass(ticket.status)">
            {{ getStatusName(ticket.status) }}
          </span>
          <el-button
            v-if="ticket.status === 0"
            type="danger"
            size="small"
            @click="refundTicket(ticket.id)"
          >
            申请退票
          </el-button>
        </div>
      </div>

      <div v-if="tickets.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无门票">
          <el-button type="primary" @click="goProducts">去购票</el-button>
        </el-empty>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { orderApi } from '../api/index.js'

const router = useRouter()
const loading = ref(false)
const tickets = ref([])

onMounted(() => {
  loadTickets()
})

const loadTickets = async () => {
  loading.value = true
  try {
    const userId = localStorage.getItem('userId')
    if (!userId) {
      ElMessage.warning('请先登录')
      router.push('/login')
      return
    }
    const result = await orderApi.getMyTickets(userId)
    if (result.code === 200) {
      tickets.value = result.data || []
    } else {
      ElMessage.error(result.msg || '加载失败')
    }
  } catch (error) {
    console.error('加载我的票失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const getStatusName = (status) => {
  const map = { 0: '未使用', 1: '已使用', 2: '已退票' }
  return map[status] || status
}

const getStatusClass = (status) => {
  const map = { 0: 'valid', 1: 'used', 2: 'refunded' }
  return map[status] || ''
}

const refundTicket = async (ticketId) => {
  const ticket = tickets.value.find(t => t.id === ticketId)
  if (!ticket) {
    ElMessage.error('票不存在')
    return
  }
  try {
    await ElMessageBox.confirm(
      '确定要退票吗？退票后库存将被释放且不可撤销。',
      '退票确认',
      {
        confirmButtonText: '确定退票',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
  } catch {
    return
  }
  try {
    const userId = localStorage.getItem('userId')
    if (!userId) {
      ElMessage.warning('请先登录')
      return
    }
    const result = await orderApi.refundTicket(ticket.ticketNo, userId)
    if (result.code === 200) {
      ElMessage.success(result.msg || '退票成功')
      loadTickets()
    } else {
      ElMessage.error(result.msg || '退票失败')
    }
  } catch (e) {
    console.error('退票失败:', e)
    ElMessage.error('退票失败，请稍后重试')
  }
}

const goProducts = () => {
  router.push('/products')
}
</script>

<style scoped>
.my-tickets {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  font-size: 24px;
  font-weight: 600;
  margin: 0;
}

.ticket-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
}

.ticket-card {
  background: linear-gradient(135deg, #fff8f0 0%, #fff 100%);
  border: 2px dashed #f39c12;
  border-radius: 12px;
  padding: 20px;
}

.ticket-header {
  display: flex;
  justify-content: space-between;
  padding-bottom: 15px;
  border-bottom: 1px dashed #ddd;
  margin-bottom: 15px;
}

.ticket-info .product-name {
  font-size: 13px;
  color: #409eff;
  font-weight: 500;
  margin: 0 0 4px 0;
}

.ticket-info h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.ticket-info .seat-info {
  color: #666;
  font-size: 14px;
  margin: 0 0 4px 0;
}

.ticket-info .ticket-price {
  font-size: 18px;
  font-weight: 600;
  color: #ff4d4f;
  margin: 0;
}

.qrcode-placeholder {
  width: 80px;
  height: 80px;
  border: 1px dashed #ddd;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #999;
  gap: 4px;
}

.qrcode-placeholder span {
  font-size: 12px;
}

.ticket-body {
  padding-bottom: 15px;
}

.info-row {
  display: flex;
  gap: 5px;
  font-size: 13px;
  margin-bottom: 4px;
}

.info-row .label {
  color: #999;
}

.info-row .value {
  color: #333;
}

.ticket-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 15px;
  border-top: 1px dashed #ddd;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-tag.valid {
  background: #d4edda;
  color: #155724;
}

.status-tag.used {
  background: #fff3cd;
  color: #856404;
}

.status-tag.refunded {
  background: #f8d7da;
  color: #721c24;
}

.empty-state {
  grid-column: 1 / -1;
  padding: 60px 0;
}
</style>
