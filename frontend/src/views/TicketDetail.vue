<template>
  <div class="ticket-detail-page">
    <div class="back-nav">
      <el-button type="text" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
    </div>

    <div class="ticket-card" v-loading="loading">
      <!-- 左侧海报图 -->
      <div class="poster-section">
        <img :src="(product.posterUrl && (String(product.posterUrl).startsWith('http') || String(product.posterUrl).startsWith('/'))) ? product.posterUrl : getDefaultImage(product.id)" :alt="product.name" class="poster-img" />
        <div class="channel-badge" :class="ticket.channel">
          {{ channelName(ticket.channel) }}
        </div>
      </div>

      <!-- 右侧信息 -->
      <div class="info-section">
        <h1 class="product-name">{{ product.name }}</h1>
        <h2 class="ticket-type-name">{{ ticket.typeName }}</h2>

        <div class="meta-row">
          <el-icon><Location /></el-icon>
          <span>{{ product.venue }}</span>
        </div>
        <div class="meta-row">
          <el-icon><Calendar /></el-icon>
          <span>{{ formatDateTime(product.showTime) }}</span>
        </div>
        <div class="meta-row" v-if="ticket.seatArea">
          <el-icon><Guide /></el-icon>
          <span>座位区域：{{ ticket.seatArea }}</span>
        </div>

        <div class="price-row">
          <span class="price-label">票价</span>
          <span class="price-value">¥{{ ticket.price }}</span>
        </div>

        <div class="stock-row" v-if="!isShowEnded">
          <span class="stock-label">库存：</span>
          <span class="stock-value">{{ ticket.availableStock || 0 }} / {{ ticket.totalStock || 0 }} 张</span>
        </div>

        <!-- 活动状态 / 倒计时 -->
        <div v-if="isActivityOngoing" class="activity-banner active">
          <el-icon><Clock /></el-icon>
          <span>活动进行中，距结束 {{ countdownText }}</span>
        </div>
        <div v-else-if="isActivityPending" class="activity-banner pending">
          <el-icon><Clock /></el-icon>
          <span>尚未开售，距开售 {{ countdownText }}</span>
        </div>
        <div v-else-if="isActivityEnded" class="activity-banner ended">
          <el-icon><Warning /></el-icon>
          <span>{{ config.reason || '活动已结束' }}</span>
        </div>
        <div v-else-if="config && !config.canPurchase" class="activity-banner ended">
          <el-icon><Warning /></el-icon>
          <span>{{ config.reason || '暂不可购买' }}</span>
        </div>

        <!-- 购票操作按钮 -->
        <div class="action-row" v-if="!isShowEnded">
          <el-button
            v-if="canPurchase"
            type="primary"
            size="large"
            @click="goCheckout"
          >
            立即购买
          </el-button>
          <el-button
            v-else-if="isSoldOut"
            type="warning"
            size="large"
            @click="applyWaitlist"
          >
            候补登记
          </el-button>
          <el-button v-else size="large" disabled>
            {{ config.reason || '不可购买' }}
          </el-button>
        </div>

        <!-- 描述 -->
        <div v-if="product.description" class="description-section">
          <h3>演出介绍</h3>
          <p>{{ product.description }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Location, Calendar, Guide, Clock, Warning } from '@element-plus/icons-vue'
import { productApi } from '../api/index.js'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const ticket = ref({})       // 当前票种基本信息
const product = ref({})      // 所属演出信息
const config = ref({})       // 实时购买状态（包含 activityStatus / startTime / endTime / reason）
let countdownTimer = null
let countdownMs = 0

const channelName = (ch) => {
  const map = { seckill: '秒杀', special: '特价', regular: '常规' }
  return map[ch] || ch || '常规'
}

const isActivityOngoing = computed(() => config.value && config.value.activityStatus === 1)
const isActivityPending = computed(() => config.value && config.value.activityStatus === 0)
const isActivityEnded = computed(() => config.value && config.value.activityStatus === 2)
// 演出已结束：activityStatus === 2 或 reason 包含"结束"
const isShowEnded = computed(() => {
  if (config.value && config.value.activityStatus === 2) return true
  if (config.value && config.value.reason && config.value.reason.includes('结束')) return true
  return false
})
const isSoldOut = computed(() => {
  // 库存为 0 但票种本身是上架的
  if (!ticket.value || ticket.value.status !== 1) return false
  if ((ticket.value.availableStock || 0) > 0) return false
  return true
})
const canPurchase = computed(() => {
  if (!config.value) return false
  return !!config.value.canPurchase
})

const countdownText = ref('00:00:00')

const startCountdown = () => {
  if (countdownTimer) clearInterval(countdownTimer)
  const st = config.value.startTime ? new Date(config.value.startTime).getTime() : null
  const et = config.value.endTime ? new Date(config.value.endTime).getTime() : null
  let target = null
  if (config.value.activityStatus === 0 && st) {
    target = st
  } else if (config.value.activityStatus === 1 && et) {
    target = et
  } else {
    countdownText.value = '00:00:00'
    return
  }
  const update = () => {
    const diff = target - Date.now()
    if (diff <= 0) {
      countdownText.value = '00:00:00'
      clearInterval(countdownTimer)
      countdownTimer = null
      // 倒计时结束，自动重新加载以刷新状态
      setTimeout(loadTicket, 500)
      return
    }
    const h = Math.floor(diff / (1000 * 60 * 60))
    const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
    const s = Math.floor((diff % (1000 * 60)) / 1000)
    if (h > 24 * 30) {
      const days = Math.floor(h / 24)
      countdownText.value = `${days} 天 ${String(h % 24).padStart(2, '0')} 小时`
    } else {
      countdownText.value = `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
    }
  }
  update()
  countdownTimer = setInterval(update, 1000)
}

onMounted(() => {
  loadTicket()
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

const loadTicket = async () => {
  loading.value = true
  try {
    const result = await productApi.getTicketDetail(route.params.ticketTypeId)
    if (result.code === 200 && result.data) {
      ticket.value = {
        id: result.data.id,
        productId: result.data.productId,
        typeName: result.data.typeName,
        seatArea: result.data.seatArea,
        channel: result.data.channel,
        price: result.data.price,
        totalStock: result.data.totalStock,
        availableStock: result.data.availableStock,
        status: result.data.status
      }
      product.value = result.data.product || {}
      config.value = result.data.config || {}
      // 若没有 activityStatus（常规票通常没有），默认可购买
      if (config.value.activityStatus === undefined && config.value.canPurchase) {
        // 常规票不需要倒计时
      }
      startCountdown()
    } else {
      ElMessage.error(result.msg || '加载失败')
    }
  } catch (e) {
    console.error('加载票详情失败:', e)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

const getDefaultImage = (id) => {
  const list = [
    '/poster-1.svg', // 0 备用
    '/poster-1.svg', // 1 周杰伦演唱会
    '/poster-2.svg', // 2 五月天演唱会
    '/poster-3.svg', // 3 薛之谦演唱会
    '/poster-1.svg'  // 4 备用
  ]
  const n = Number(id)
  if (!Number.isFinite(n) || n < 0) return list[0]
  return list[Math.floor(n) % list.length]
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '待定'
  const date = new Date(dateStr)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const goCheckout = () => {
  const userId = localStorage.getItem('userId')
  if (!userId) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push({
    path: '/checkout',
    query: {
      productId: ticket.value.productId,
      ticketTypeId: ticket.value.id,
      channel: ticket.value.channel
    }
  })
}

const applyWaitlist = async () => {
  const userId = localStorage.getItem('userId')
  if (!userId) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    const result = await productApi.applyWaitlist({
      userId,
      ticketTypeId: ticket.value.id,
      productId: ticket.value.productId
    })
    if (result.code === 200) {
      ElMessage.success(result.data || '候补登记成功！有票时将通知您')
    } else {
      ElMessage.error(result.msg || '候补登记失败')
    }
  } catch (e) {
    console.error('候补登记失败:', e)
    ElMessage.error('候补登记失败，请稍后重试')
  }
}
</script>

<style scoped>
.ticket-detail-page {
  padding: 20px;
  max-width: 1100px;
  margin: 0 auto;
}

.back-nav {
  margin-bottom: 15px;
}

.ticket-card {
  background: white;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  display: flex;
  overflow: hidden;
}

.poster-section {
  position: relative;
  flex: 0 0 45%;
  min-height: 500px;
}

.poster-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.channel-badge {
  position: absolute;
  top: 20px;
  left: 20px;
  padding: 8px 20px;
  border-radius: 20px;
  font-size: 15px;
  font-weight: 600;
  color: white;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.channel-badge.seckill {
  background: linear-gradient(135deg, #ff4d4f, #ff7875);
}

.channel-badge.special {
  background: linear-gradient(135deg, #fa8c16, #ffa940);
}

.channel-badge.regular {
  background: linear-gradient(135deg, #1890ff, #40a9ff);
}

.info-section {
  flex: 1;
  padding: 40px;
  display: flex;
  flex-direction: column;
}

.product-name {
  font-size: 28px;
  font-weight: 700;
  margin: 0;
  color: #262626;
}

.ticket-type-name {
  font-size: 22px;
  font-weight: 600;
  margin: 10px 0 25px 0;
  color: #1890ff;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  color: #595959;
  font-size: 15px;
}

.meta-row .el-icon {
  color: #8c8c8c;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 15px;
  margin: 25px 0 10px 0;
  padding: 20px 25px;
  background: linear-gradient(135deg, #fff1f0, #ffe7e4);
  border-radius: 8px;
}

.price-label {
  color: #8c8c8c;
  font-size: 15px;
}

.price-value {
  color: #ff4d4f;
  font-size: 32px;
  font-weight: 700;
}

.stock-row {
  display: flex;
  gap: 10px;
  color: #595959;
  font-size: 15px;
  margin-bottom: 15px;
}

.stock-value {
  font-weight: 600;
}

.activity-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 18px;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 500;
  margin: 15px 0;
  font-family: 'SF Mono', Menlo, Consolas, monospace;
}

.activity-banner.active {
  background: linear-gradient(135deg, #fff1f0, #ffe7e4);
  color: #ff4d4f;
  border: 1px solid #ffccc7;
}

.activity-banner.pending {
  background: linear-gradient(135deg, #e6f7ff, #d0ebff);
  color: #1890ff;
  border: 1px solid #91d5ff;
}

.activity-banner.ended {
  background: #f5f5f5;
  color: #8c8c8c;
  border: 1px solid #d9d9d9;
}

.action-row {
  margin-top: 25px;
}

.action-row .el-button {
  min-width: 180px;
}

.description-section {
  margin-top: 40px;
  padding-top: 30px;
  border-top: 1px solid #f0f0f0;
}

.description-section h3 {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 15px 0;
  color: #262626;
}

.description-section p {
  color: #595959;
  line-height: 1.8;
  font-size: 14px;
}

@media (max-width: 768px) {
  .ticket-card {
    flex-direction: column;
  }
  .poster-section {
    flex: 0 0 auto;
    height: 300px;
    min-height: 300px;
  }
  .info-section {
    padding: 25px;
  }
  .product-name {
    font-size: 22px;
  }
  .ticket-type-name {
    font-size: 18px;
  }
}
</style>
