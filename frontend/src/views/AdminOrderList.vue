<template>
  <div class="admin-order-container">
    <div class="page-header">
      <h2>📋 订单列表</h2>
    </div>

    <div class="filter-bar">
      <div class="filter-left">
        <span class="filter-label">状态筛选：</span>
        <el-select v-model="statusFilter" class="filter-select" @change="loadOrders">
          <el-option label="全部" :value="null" />
          <el-option label="待支付" :value="0" />
          <el-option label="已支付" :value="1" />
          <el-option label="已取消" :value="2" />
          <el-option label="已超时" :value="3" />
        </el-select>
      </div>
      <div class="filter-right">
        <el-input
          v-model="searchKeyword"
          placeholder="输入订单号/用户ID"
          class="search-input"
          @keyup.enter="handleSearch"
          clearable
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
    </div>

    <div class="order-table">
      <el-table :data="orderList" v-loading="loading" border stripe>
        <el-table-column prop="orderNo" label="订单号" width="200" />
        <el-table-column label="用户信息" width="120">
          <template #default="scope">
            <div>
              <div>ID: {{ scope.row.userId }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="演出信息" min-width="150">
          <template #default="scope">
            <div>演出ID: {{ scope.row.productId }}</div>
            <div style="color: #999; font-size: 12px">票种ID: {{ scope.row.ticketTypeId }}</div>
          </template>
        </el-table-column>
        <el-table-column label="票档/座位" width="120">
          <template #default="scope">
            <div>¥{{ scope.row.unitPrice }}</div>
            <div style="color: #999; font-size: 12px">x{{ scope.row.quantity }}张</div>
          </template>
        </el-table-column>
        <el-table-column label="订单金额" width="100">
          <template #default="scope">
            <span class="amount">¥{{ scope.row.totalAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="支付状态" width="100">
          <template #default="scope">
            <span class="status-tag" :class="getStatusClass(scope.row.status)">
              {{ getStatusLabel(scope.row.status) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="160">
          <template #default="scope">
            {{ formatDateTime(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button type="text" @click="showOrderDetail(scope.row)">查看详情</el-button>
            <el-button
              v-if="scope.row.status !== 2 && scope.row.status !== 3"
              type="text"
              @click="handleForceCancel(scope.row)"
            >强制取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-bar">
      <span>共 {{ total }} 条</span>
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadOrders"
      />
    </div>

    <el-dialog v-model="detailDialogVisible" title="订单详情" width="650px">
      <div v-if="orderDetail" class="detail-content">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ orderDetail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="订单状态">
            <span class="status-tag" :class="getStatusClass(orderDetail.status)">
              {{ getStatusLabel(orderDetail.status) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ orderDetail.userId }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ orderDetail.username || '-' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ orderDetail.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ orderDetail.userId }}</el-descriptions-item>
          <el-descriptions-item label="演出名称" :span="2">{{ orderDetail.productName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="演出场馆" :span="2">{{ orderDetail.venue || '-' }}</el-descriptions-item>
          <el-descriptions-item label="演出时间" :span="2">{{ formatDateTime(orderDetail.showTime) }}</el-descriptions-item>
          <el-descriptions-item label="票种名称">{{ orderDetail.ticketTypeName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="座位区域">{{ orderDetail.seatArea || '-' }}</el-descriptions-item>
          <el-descriptions-item label="单价">¥{{ orderDetail.unitPrice }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ orderDetail.quantity }}张</el-descriptions-item>
          <el-descriptions-item label="订单金额" :span="2">
            <span class="amount">¥{{ orderDetail.totalAmount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="销售渠道">
            {{ getChannelLabel(orderDetail.channel) }}
          </el-descriptions-item>
          <el-descriptions-item label="下单时间">{{ formatDateTime(orderDetail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ orderDetail.payTime ? formatDateTime(orderDetail.payTime) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="超时时间">{{ formatDateTime(orderDetail.timeoutTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelDialogVisible" title="强制取消订单" width="450px">
      <el-form>
        <el-form-item label="订单号">
          <span>{{ currentOrder?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="当前状态">
          <span class="status-tag" :class="getStatusClass(currentOrder?.status)">
            {{ getStatusLabel(currentOrder?.status) }}
          </span>
        </el-form-item>
        <el-form-item label="取消原因">
          <el-input v-model="cancelReason" type="textarea" placeholder="请输入取消原因" rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelDialogVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmCancel">确认强制取消</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../api/index.js'

const orderList = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const statusFilter = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const detailDialogVisible = ref(false)
const cancelDialogVisible = ref(false)
const orderDetail = ref(null)
const currentOrder = ref(null)
const cancelReason = ref('')

onMounted(() => {
  loadOrders()
})

const loadOrders = async () => {
  loading.value = true
  try {
    const result = await adminApi.getOrders(searchKeyword.value, statusFilter.value, currentPage.value, pageSize.value)
    if (result.code === 200 && result.data) {
      orderList.value = result.data.orders || []
      total.value = result.data.total || 0
    }
  } catch (error) {
    console.error('加载订单列表失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  loadOrders()
}

const getStatusClass = (status) => {
  switch (status) {
    case 0: return 'status-pending'
    case 1: return 'status-paid'
    case 2: return 'status-cancelled'
    case 3: return 'status-timeout'
    default: return ''
  }
}

const getStatusLabel = (status) => {
  switch (status) {
    case 0: return '待支付'
    case 1: return '已支付'
    case 2: return '已取消'
    case 3: return '已超时'
    default: return '-'
  }
}

const getChannelLabel = (channel) => {
  switch (channel) {
    case 'seckill': return '秒杀'
    case 'special': return '特价'
    case 'regular': return '常规'
    default: return channel || '-'
  }
}

const formatDateTime = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  return d.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

const showOrderDetail = async (order) => {
  try {
    const result = await adminApi.getOrderDetail(order.orderNo)
    if (result.code === 200) {
      orderDetail.value = result.data
      detailDialogVisible.value = true
    } else {
      ElMessage.error(result.msg || '加载详情失败')
    }
  } catch (error) {
    ElMessage.error('加载详情失败')
  }
}

const handleForceCancel = (order) => {
  currentOrder.value = order
  cancelReason.value = ''
  cancelDialogVisible.value = true
}

const confirmCancel = async () => {
  if (!currentOrder.value) return
  try {
    const result = await adminApi.forceCancelOrder(currentOrder.value.orderNo, cancelReason.value)
    if (result.code === 200) {
      ElMessage.success('强制取消成功')
      cancelDialogVisible.value = false
      loadOrders()
    } else {
      ElMessage.error(result.msg || '取消失败')
    }
  } catch (error) {
    ElMessage.error('取消失败')
  }
}
</script>

<style scoped>
.admin-order-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  font-size: 22px;
  margin: 0;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 15px 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 1px 5px rgba(0,0,0,0.05);
}

.filter-label {
  margin-right: 10px;
}

.filter-select {
  width: 120px;
}

.search-input {
  width: 250px;
  margin-right: 10px;
}

.order-table {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 5px rgba(0,0,0,0.05);
}

.amount {
  font-weight: 600;
  color: #e74c3c;
}

.status-tag {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
}

.status-pending {
  background: #fff7e6;
  color: #faad14;
}

.status-paid {
  background: #e6f7ff;
  color: #1890ff;
}

.status-cancelled {
  background: #f5f5f5;
  color: #999;
}

.status-timeout {
  background: #fff1f0;
  color: #ff4d4f;
}

.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding: 15px 0;
}

.detail-content {
  padding: 10px 0;
}
</style>
