<template>
  <div class="waiting-list-page">
    <div class="page-header">
      <h1>⏳ 候补记录</h1>
    </div>

    <el-table :data="waitingList" border v-loading="loading" class="waiting-table">
      <el-table-column prop="ticketTypeId" label="票种ID" width="120" />
      <el-table-column prop="productId" label="演出ID" width="120" />
      <el-table-column prop="quantity" label="数量" width="80" />
      <el-table-column label="状态" width="120">
        <template #default="scope">
          <span class="status-tag" :class="getStatusClass(scope.row.status)">
            {{ getStatusName(scope.row.status) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="申请时间" />
    </el-table>

    <div v-if="waitingList.length === 0 && !loading" class="empty-state">
      <el-empty description="暂无候补记录" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { orderApi } from '../api/index.js'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const waitingList = ref([])

onMounted(() => {
  loadWaiting()
})

const loadWaiting = async () => {
  loading.value = true
  try {
    // 调用 /order/waitlist 接口（后端 OrderController）
    const result = await orderApi.getWaitlist(1001)
    if (result.code === 200) {
      waitingList.value = result.data || []
    }
  } catch (error) {
    console.error('加载候补记录失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const getStatusName = (status) => {
  const map = { 0: '等待中', 1: '已通知', 2: '已购票', 3: '已失效' }
  return map[status] || status
}

const getStatusClass = (status) => {
  const map = { 0: 'waiting', 1: 'notified', 2: 'success', 3: 'expired' }
  return map[status] || ''
}
</script>

<style scoped>
.waiting-list-page {
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

.waiting-table {
  background: white;
  border-radius: 12px;
  overflow: hidden;
}

.status-tag {
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}

.status-tag.waiting {
  background: #d1ecf1;
  color: #0c5460;
}

.status-tag.notified {
  background: #fff3cd;
  color: #856404;
}

.status-tag.success {
  background: #d4edda;
  color: #155724;
}

.status-tag.expired {
  background: #f8d7da;
  color: #721c24;
}

.empty-state {
  padding: 40px 0;
}
</style>
