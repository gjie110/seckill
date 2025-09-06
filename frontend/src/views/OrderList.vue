<template>
  <div class="order-list-page">
    <div class="page-header">
      <h1>📦 我的订单</h1>
    </div>

    <div class="filter-section">
      <el-radio-group v-model="statusFilter" @change="handleFilter">
        <el-radio-button label="">全部</el-radio-button>
        <el-radio-button label="0">待支付</el-radio-button>
        <el-radio-button label="1">已支付</el-radio-button>
        <el-radio-button label="2">已取消</el-radio-button>
        <el-radio-button label="3">已超时</el-radio-button>
      </el-radio-group>
    </div>

    <div class="order-list" v-loading="loading">
      <div v-for="order in orderList" :key="order.orderNo" class="order-card">
        <div class="order-header">
          <span class="order-no">订单号：{{ order.orderNo }}</span>
          <span class="order-status" :class="getStatusClass(order.status)">
            {{ getStatusLabel(order.status) }}
          </span>
        </div>

        <div class="order-body">
          <div class="order-info">
            <div class="info-row">
              <span class="label">演出ID：</span>
              <span class="value">{{ order.productId }}</span>
            </div>
            <div class="info-row">
              <span class="label">票种ID：</span>
              <span class="value">{{ order.ticketTypeId }}</span>
            </div>
            <div class="info-row">
              <span class="label">数量：</span>
              <span class="value">{{ order.quantity }}张</span>
            </div>
          </div>
          <div class="order-amount">
            <span class="amount">¥{{ order.totalAmount }}</span>
          </div>
        </div>

        <div class="order-footer">
          <span class="order-time">{{ formatDateTime(order.createTime) }}</span>
          <div class="order-actions">
            <el-button type="text" @click="viewDetail(order.orderNo)">查看详情</el-button>
            <el-button
              v-if="order.status === 0"
              type="primary"
              size="small"
              @click="goPay(order.orderNo)"
            >
              去支付
            </el-button>
            <el-button
              v-if="order.status === 0"
              type="text"
              @click="handleCancel(order.orderNo)"
            >
              取消订单
            </el-button>
          </div>
        </div>
      </div>

      <div v-if="orderList.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无订单">
          <el-button type="primary" @click="goShopping">去购票</el-button>
        </el-empty>
      </div>
    </div>

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="订单详情" width="600px">
      <div v-if="orderDetail" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号" :span="2">{{ orderDetail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <span :class="getStatusClass(orderDetail.status)">
              {{ getStatusLabel(orderDetail.status) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="销售渠道">
            {{ getChannelLabel(orderDetail.channel) }}
          </el-descriptions-item>
          <el-descriptions-item label="演出ID">{{ orderDetail.productId }}</el-descriptions-item>
          <el-descriptions-item label="票种ID">{{ orderDetail.ticketTypeId }}</el-descriptions-item>
          <el-descriptions-item label="单价">¥{{ orderDetail.unitPrice }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ orderDetail.quantity }}张</el-descriptions-item>
          <el-descriptions-item label="订单金额" :span="2">
            <span class="amount">¥{{ orderDetail.totalAmount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatDateTime(orderDetail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">
            {{ orderDetail.payTime ? formatDateTime(orderDetail.payTime) : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="超时时间" :span="2">
            {{ orderDetail.timeoutTime ? formatDateTime(orderDetail.timeoutTime) : '-' }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button
          v-if="orderDetail && orderDetail.status === 0"
          type="primary"
          @click="goPay(orderDetail.orderNo)"
        >
          去支付
        </el-button>
      </template>
    </el-dialog>

    <!-- 取消确认弹窗 -->
    <el-dialog v-model="cancelDialogVisible" title="取消订单" width="400px">
      <p>确定要取消该订单吗？取消后不可恢复。</p>
      <template #footer>
        <el-button @click="cancelDialogVisible = false">不取消</el-button>
        <el-button type="danger" @click="confirmCancel">确认取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '../api/index.js'

const router = useRouter()

const loading = ref(false)
const orderList = ref([])
const statusFilter = ref('')

const detailDialogVisible = ref(false)
const cancelDialogVisible = ref(false)
const orderDetail = ref(null)
const currentCancelOrderNo = ref('')

onMounted(() => {
  loadOrders()
})

const loadOrders = async () => {
  loading.value = true
  try {
    const userId = localStorage.getItem('userId')
    if (!userId) {
      ElMessage.warning('请先登录')
      router.push('/login')
      return
    }
    const result = await orderApi.getList(userId)
    if (result.code === 200) {
      let orders = result.data || []

      if (statusFilter.value !== '') {
        orders = orders.filter(o => String(o.status) === String(statusFilter.value))
      }

      orderList.value = orders
    } else {
      ElMessage.error(result.msg || '加载失败')
    }
  } catch (error) {
    console.error('加载订单列表失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleFilter = () => {
  loadOrders()
}

const getStatusClass = (status) => {
  const classes = {
    '0': 'status-pending',
    '1': 'status-paid',
    '2': 'status-cancelled',
    '3': 'status-timeout'
  }
  return classes[status] || ''
}

const getStatusLabel = (status) => {
  const labels = {
    '0': '待支付',
    '1': '已支付',
    '2': '已取消',
    '3': '已超时'
  }
  return labels[status] || status
}

const getChannelLabel = (channel) => {
  const labels = { seckill: '秒杀', special: '特价', regular: '常规' }
  return labels[channel] || channel || '-'
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}:${String(date.getSeconds()).padStart(2, '0')}`
}

const viewDetail = async (orderNo) => {
  try {
    const result = await orderApi.getDetail(orderNo)
    if (result.code === 200) {
      orderDetail.value = result.data
      detailDialogVisible.value = true
    }
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

const goPay = (orderNo) => {
  router.push({ path: '/payment', query: { orderNo } })
}

const handleCancel = (orderNo) => {
  currentCancelOrderNo.value = orderNo
  cancelDialogVisible.value = true
}

const confirmCancel = async () => {
  try {
    const userId = localStorage.getItem('userId')
    if (!userId) {
      ElMessage.warning('请先登录')
      router.push('/login')
      return
    }
    const result = await orderApi.cancel(currentCancelOrderNo.value, userId)
    if (result.code === 200) {
      ElMessage.success('订单已取消')
      cancelDialogVisible.value = false
      loadOrders()
    } else {
      ElMessage.error(result.msg || '取消失败')
    }
  } catch (error) {
    ElMessage.error('取消失败')
  }
}

const goShopping = () => {
  router.push('/products')
}
</script>

<style scoped>
.order-list-page {
  padding: 20px;
  max-width: 900px;
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

.filter-section {
  background: white;
  padding: 15px 20px;
  border-radius: 12px;
  margin-bottom: 20px;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.order-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 15px;
}

.order-no {
  font-size: 14px;
  color: #666;
}

.order-status {
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
}

.status-pending {
  background: #fff7e6;
  color: #fa8c16;
}

.status-paid {
  background: #f6ffed;
  color: #52c41a;
}

.status-cancelled,
.status-timeout {
  background: #f5f5f5;
  color: #999;
}

.order-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.order-info {
  display: flex;
  gap: 30px;
}

.info-row {
  display: flex;
  gap: 5px;
  font-size: 14px;
}

.info-row .label {
  color: #999;
}

.info-row .value {
  color: #333;
}

.order-amount .amount {
  font-size: 22px;
  font-weight: 700;
  color: #ff4d4f;
}

.order-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 15px;
  border-top: 1px solid #f0f0f0;
}

.order-time {
  font-size: 13px;
  color: #999;
}

.order-actions {
  display: flex;
  gap: 10px;
}

.empty-state {
  padding: 60px 0;
  text-align: center;
}

.detail-content {
  padding: 10px 0;
}

.amount {
  font-weight: 600;
  color: #ff4d4f;
}
</style>
