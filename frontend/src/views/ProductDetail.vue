<template>
  <div class="product-detail-page">
    <!-- 加载中显示骨架屏 -->
    <el-skeleton v-if="loading" :rows="10" animated />

    <!-- 加载失败或商品不存在 -->
    <div v-else-if="!product" class="loading-error">
      <el-empty description="演出不存在或已下架">
        <el-button type="primary" @click="goBack">返回列表</el-button>
      </el-empty>
    </div>

    <!-- 正常内容 -->
    <template v-else>
      <!-- 面包屑 -->
      <el-breadcrumb separator="/" class="breadcrumb">
        <el-breadcrumb-item @click="goBack">演出列表</el-breadcrumb-item>
        <el-breadcrumb-item>{{ product.name }}</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="detail-container">
        <!-- 左侧海报 -->
        <div class="poster-section">
          <div class="poster-image">
            <img :src="posterSrc" :alt="product.name" />
            <div class="poster-overlay" v-if="product.status === 1">
              <span class="status-tag published">热卖中</span>
            </div>
            <div class="poster-overlay" v-else>
              <span class="status-tag draft">即将开售</span>
            </div>
          </div>
        </div>

        <!-- 右侧信息 -->
        <div class="info-section">
          <h1 class="product-name">{{ product.name }}</h1>
          <p class="product-desc" v-if="product.description">{{ product.description }}</p>

          <div class="info-cards">
            <div class="info-card">
              <div class="card-icon">📍</div>
              <div class="card-content">
                <span class="card-label">演出场馆</span>
                <span class="card-value">{{ product.venue || '待定' }}</span>
              </div>
            </div>
            <div class="info-card">
              <div class="card-icon">📅</div>
              <div class="card-content">
                <span class="card-label">演出时间</span>
                <span class="card-value">{{ formatDateTime(product.showTime) }}</span>
              </div>
            </div>
            <div class="info-card">
              <div class="card-icon">💰</div>
              <div class="card-content">
                <span class="card-label">最低票价</span>
                <span class="card-value price">¥{{ lowestPrice }} 起</span>
              </div>
            </div>
          </div>

          <div class="channel-tags" v-if="channels && channels.length > 0">
            <span class="channel-hint">购票渠道：</span>
            <span v-for="ch in channels" :key="ch" class="channel-tag" :class="ch" @click="setChannel(ch)">
              {{ channelName(ch) }}
            </span>
          </div>
        </div>
      </div>

      <!-- 票档选择 -->
      <div class="ticket-section">
        <h2 class="section-title">🎫 选择票档
          <span v-if="channel" class="channel-filter-tag">
            (仅显示 {{ channelName(channel) }} 渠道)
            <el-button size="small" type="text" @click="clearChannel">清除</el-button>
          </span>
        </h2>

        <div v-for="ch in displayChannels" :key="ch" class="channel-group">
          <div class="channel-group-title">
            <span class="channel-badge" :class="ch">{{ channelName(ch) }}</span>
            <span class="channel-group-count">共 {{ groupedTickets[ch].length }} 个票档</span>
          </div>
          <div class="ticket-list">
            <div
              v-for="ticket in groupedTickets[ch]"
              :key="ticket.id"
              class="ticket-card"
              :class="{ disabled: !canPurchase(ticket) }"
            >
              <div class="ticket-main">
                <div class="ticket-left">
                  <div class="ticket-name">{{ ticket.typeName }}</div>
                  <div class="seat-area" v-if="ticket.seatArea">{{ ticket.seatArea }}</div>

                  <!-- 活动状态 / 倒计时 -->
                  <div class="countdown-mini" v-if="isActivityOpen(ticket)">
                    <el-icon><Clock /></el-icon>
                    <span>活动进行中，距结束 <span class="countdown-time">{{ countdowns[ticket.id] || getCountdownText(ticket) }}</span></span>
                  </div>
                  <div class="countdown-mini not-started" v-else-if="isActivityNotStarted(ticket)">
                    <el-icon><Clock /></el-icon>
                    <span>尚未开售，距开售 <span class="countdown-time">{{ countdowns[ticket.id] || getCountdownText(ticket) }}</span></span>
                  </div>
                  <div class="ticket-hint" v-if="!canPurchase(ticket) && !isOutOfStock(ticket) && !isShowEnded(ticket) && ticket.config && ticket.config.reason">
                    <el-icon><Warning /></el-icon>
                    <span>{{ ticket.config.reason }}</span>
                  </div>
                </div>
                <div class="ticket-right">
                  <div class="ticket-price">¥{{ ticket.price }}</div>
                  <!-- 库存展示逻辑：
                       已结束 / 未开售 → 不显示库存
                       有库存 → 显示剩余量
                       售罄 → 显示已售罄 -->
                  <div v-if="(ticket.availableStock || 0) > 0 && !isShowEnded(ticket) && !isActivityNotStarted(ticket)">
                    <div class="ticket-stock-info">
                      <span class="stock-label available">剩余 {{ ticket.availableStock }} / 总 {{ ticket.totalStock }} 张</span>
                    </div>
                  </div>
                  <div class="ticket-stock empty" v-else-if="!isShowEnded(ticket) && !isActivityNotStarted(ticket) && (ticket.availableStock || 0) <= 0">已售罄</div>
                </div>
              </div>

              <div class="ticket-actions">
                <!-- 已结束的票种不能有购买按钮 -->
                <el-button
                  v-if="isShowEnded(ticket)"
                  disabled
                >
                  已结束
                </el-button>
                <el-button
                  v-else-if="canPurchase(ticket)"
                  type="primary"
                  @click="goCheckout(ticket)"
                >
                  立即购买
                </el-button>
                <el-button
                  v-else-if="isOutOfStock(ticket)"
                  type="warning"
                  @click="applyWaitlist(ticket)"
                >
                  候补登记
                </el-button>
                <el-button
                  v-else
                  disabled
                >
                  {{ ticket.config && ticket.config.reason ? ticket.config.reason : '不可购买' }}
                </el-button>
              </div>
            </div>
          </div>
        </div>

        <div v-if="displayChannels.length === 0" class="empty-tickets">
          <el-empty :description="channel ? '当前渠道暂无票档，可切换其他渠道查看' : '暂无票档'" />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Warning, Clock } from '@element-plus/icons-vue'
import { productApi } from '../api/index.js'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const product = ref(null)

const posterSrc = computed(() => {
  const url = product.value?.posterUrl
  if (url && (String(url).startsWith('http') || String(url).startsWith('/'))) {
    return url + '?t=' + (product.value?.updateTime || Date.now())
  }
  const images = [
    '/poster-1.svg', '/poster-1.svg', '/poster-2.svg', '/poster-3.svg', '/poster-1.svg'
  ]
  const n = Number(product.value?.id)
  return images[Number.isFinite(n) && n >= 0 ? Math.floor(n) % images.length : 0]
})
const allTickets = ref([])   // 后端返回的全部票种（全渠道）
const lowestPrice = ref(0)
const channels = ref([])
const countdowns = reactive({}) // ticketTypeId -> 显示文本
let countdownTimer = null
let refreshTimer = null

const channelName = (ch) => ({ seckill: '秒杀', special: '特价', regular: '常规' }[ch] || '常规')

// 从路由 query 获取 channel，用于仅显示当前渠道的票
const channel = computed(() => route.query.channel || '')

// 过滤后的票种列表（渲染用）
const ticketTypes = computed(() => {
  if (!channel.value) return allTickets.value
  return allTickets.value.filter((t) => t.channel === channel.value)
})

// 按渠道分组
const groupedTickets = computed(() => {
  const groups = {}
  for (const t of ticketTypes.value) {
    const ch = t.channel || 'regular'
    if (!groups[ch]) groups[ch] = []
    groups[ch].push(t)
  }
  return groups
})

// 要展示的渠道顺序：seckill -> special -> regular
const displayChannels = computed(() => {
  const order = ['seckill', 'special', 'regular']
  return order.filter(ch => groupedTickets.value[ch] && groupedTickets.value[ch].length > 0)
})

/** 票种是否已结束（活动已结束 / 演出已结束 / 票种已下架）。已售罄不算已结束，仍可候补。 */
const isShowEnded = (ticket) => {
  if (!ticket) return false
  if (ticket.config && ticket.config.activityStatus === 2) return true
  if (ticket.config && ticket.config.reason && ticket.config.reason.includes('结束')) return true
  if (ticket.status === 0) return true   // 票种已下架视为结束
  // 额外兜底：如果后端给的是"票种未开售"，按用户的语义也视为结束
  if (ticket.config && ticket.config.reason && ticket.config.reason.includes('未开售')) return true
  return false
}

/** 某票种是否可以立即购买：满足 (1) 演出上架；(2) 票种上架；(3) 有库存；(4) 如为秒杀/特价则在活动时间内 */
const canPurchase = (ticket) => {
  if (!ticket) return false
  if (product.value && product.value.status !== 1) return false
  if (ticket.status !== 1) return false
  if (!(ticket.availableStock > 0)) return false
  // 后端提供的实时判断作为最终依据
  if (ticket.config && typeof ticket.config.canPurchase !== 'undefined') {
    return !!ticket.config.canPurchase
  }
  return true
}

const isOutOfStock = (ticket) => {
  if (!ticket) return false
  if (ticket.config && ticket.config.reason && ticket.config.reason.indexOf('售罄') >= 0) return true
  if (ticket.status === 1 && !(ticket.availableStock > 0)) return true
  return false
}

const isNotOpen = (ticket) => {
  if (!ticket) return false
  if (product.value && product.value.status !== 1) return true
  if (ticket.status !== 1) return true
  if (ticket.config && !ticket.config.canPurchase) return true
  return false
}

const isActivityOpen = (ticket) => {
  if (!ticket || !ticket.config) return false
  if (ticket.config.activityStatus !== 1) return false
  // 数据兜底：如果 reason 显示"结束"或"未开售"，但 activityStatus=1，以 reason 为准
  if (ticket.config.reason && (ticket.config.reason.includes('结束') || ticket.config.reason.includes('未开售'))) return false
  return true
}

const isActivityNotStarted = (ticket) => {
  if (!ticket || !ticket.config) return false
  if (ticket.config.activityStatus === 0) return true
  // 数据兜底：activityStatus=1 但 reason 显示"未开售"
  if (ticket.config.activityStatus === 1 && ticket.config.reason && ticket.config.reason.includes('未开售')) return true
  return false
}

/** 对有配置的票种展示倒计时（到结束/到开售），返回 HH:MM:SS 文本 */
const getCountdownText = (ticket) => {
  if (!ticket || !ticket.config) return ''
  const now = Date.now()
  const start = ticket.config.startTime ? new Date(ticket.config.startTime).getTime() : null
  // 优先用 endTime；无则用 showTime 兜底（常规票/未配置特价）
  const end = (ticket.config.endTime ? new Date(ticket.config.endTime).getTime() : null)
    || (ticket.config.showTime ? new Date(ticket.config.showTime).getTime() : null)
  let target = null
  if (ticket.config.activityStatus === 0 && start) {
    target = start
  } else if (ticket.config.activityStatus === 1 && end) {
    target = end
  } else {
    return ''
  }
  if (!target) return ''
  const diff = target - now
  if (diff <= 0) return '00:00:00'
  const totalHours = Math.floor(diff / (1000 * 60 * 60))
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  const seconds = Math.floor((diff % (1000 * 60)) / 1000)
  // 超过 24 小时显示 "X 天 HH:MM:SS"
  if (totalHours >= 24) {
    const days = Math.floor(totalHours / 24)
    const hours = totalHours % 24
    return `${days}天 ${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }
  return `${String(totalHours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

const tickCountdown = () => {
  const list = allTickets.value || []
  list.forEach((t) => {
    if (t.id != null) countdowns[t.id] = getCountdownText(t)
  })
}

onMounted(() => {
  loadProduct()
  countdownTimer = setInterval(tickCountdown, 1000)
  // 每 30 秒刷新一次后端实时状态（库存/活动状态）
  refreshTimer = setInterval(loadProduct, 30000)
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (refreshTimer) clearInterval(refreshTimer)
})

const loadProduct = async () => {
  try {
    const result = await productApi.getDetail(route.params.id)
    if (result.code === 200) {
      product.value = result.data.product
      allTickets.value = result.data.ticketTypes || []
      lowestPrice.value = result.data.lowestPrice || 0
      channels.value = result.data.channels || []
      tickCountdown()
    }
  } catch (error) {
    console.error('加载演出详情失败:', error)
    if (!loading.value) {
      ElMessage.error('加载失败')
    }
  } finally {
    loading.value = false
  }
}

const clearChannel = () => {
  // 清除当前 channel filter，回到无过滤视图
  router.replace({ path: `/product/${route.params.id}` })
}

const setChannel = (ch) => {
  router.replace({ path: `/product/${route.params.id}`, query: { channel: ch } })
}

const goCheckout = (ticket) => {
  const userId = localStorage.getItem('userId')
  if (!userId) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  router.push({
    path: '/checkout',
    query: {
      productId: product.value.id,
      ticketTypeId: ticket.id,
      channel: ticket.channel
    }
  })
}

const applyWaitlist = async (ticket) => {
  const userId = localStorage.getItem('userId')
  if (!userId) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  try {
    const result = await productApi.applyWaitlist({
      userId,
      ticketTypeId: ticket.id,
      productId: product.value.id
    })
    if (result.code === 200) {
      ElMessage.success(result.data || '候补登记成功！有票时会通知您')
    } else {
      ElMessage.error(result.msg || '候补登记失败')
    }
  } catch (error) {
    console.error('候补登记失败:', error)
    ElMessage.error('候补登记失败，请稍后重试')
  }
}

const goBack = () => {
  router.push('/products')
}

// 索引按 productId 稳定映射：1=周杰伦 2=五月天 3=薛之谦
const getDefaultImage = (id) => {
  const images = [
    '/poster-1.svg', // 0 备用
    '/poster-1.svg', // 1 周杰伦演唱会
    '/poster-2.svg', // 2 五月天演唱会
    '/poster-3.svg', // 3 薛之谦演唱会
    '/poster-1.svg'  // 4 备用
  ]
  const n = Number(id)
  if (!Number.isFinite(n) || n < 0) return images[0]
  return images[Math.floor(n) % images.length]
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '待定'
  const date = new Date(dateStr)
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.product-detail-page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.breadcrumb {
  margin-bottom: 20px;
}

.detail-container {
  display: flex;
  gap: 30px;
  background: white;
  padding: 30px;
  border-radius: 12px;
  margin-bottom: 20px;
}

.poster-section {
  flex: 0 0 350px;
}

.poster-image {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}

.poster-image img {
  width: 100%;
  height: 400px;
  object-fit: cover;
}

.poster-overlay {
  position: absolute;
  top: 15px;
  left: 15px;
}

.status-tag {
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

.status-tag.published {
  background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
  color: white;
}

.status-tag.draft {
  background: rgba(0, 0, 0, 0.7);
  color: white;
}

.info-section {
  flex: 1;
}

.product-name {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 15px 0;
  color: #333;
}

.product-desc {
  font-size: 15px;
  color: #666;
  line-height: 1.6;
  margin-bottom: 25px;
}

.info-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 15px;
}

.info-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 8px;
}

.card-icon {
  font-size: 28px;
}

.card-content {
  display: flex;
  flex-direction: column;
}

.card-label {
  font-size: 12px;
  color: #999;
  margin-bottom: 4px;
}

.card-value {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.card-value.price {
  color: #ff4d4f;
  font-size: 18px;
}

.channel-tags {
  margin-top: 20px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.channel-hint {
  color: #666;
  font-size: 14px;
  margin-right: 6px;
}

.channel-tag {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}
.channel-tag:hover {
  opacity: 0.75;
}

.channel-tag.seckill {
  background: #fff1f0;
  color: #ff4d4f;
}

.channel-tag.special {
  background: #fff7e6;
  color: #fa8c16;
}

.channel-tag.regular {
  background: #f0f0f0;
  color: #666;
}

.ticket-section {
  background: white;
  padding: 30px;
  border-radius: 12px;
  margin-bottom: 20px;
}

.section-title {
  font-size: 22px;
  font-weight: 600;
  margin: 0 0 20px 0;
  color: #333;
}

.channel-group {
  margin-bottom: 24px;
}

.channel-group-title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e8e8e8;
}

.channel-group-count {
  font-size: 13px;
  color: #999;
}

.countdown-time {
  font-family: 'SF Mono', Menlo, Consolas, monospace;
  font-weight: 700;
  font-size: 14px;
  background: linear-gradient(135deg, #fff1f0, #ffe7e4);
  color: #ff4d4f;
  padding: 1px 8px;
  border-radius: 4px;
  margin-left: 4px;
  border: 1px solid #ffccc7;
}

.countdown-mini.not-started .countdown-time {
  background: linear-gradient(135deg, #e6f7ff, #d0ebff);
  color: #1890ff;
  border-color: #91d5ff;
}

.channel-group .ticket-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ticket-card {
  border: 2px solid #f0f0f0;
  border-radius: 12px;
  padding: 20px;
  transition: all 0.2s;
}

.ticket-card:hover:not(.disabled) {
  border-color: #409eff;
  box-shadow: 0 2px 12px rgba(64, 158, 255, 0.15);
}

.ticket-card.disabled {
  background: #fafafa;
  opacity: 0.85;
}

.ticket-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.ticket-left {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.channel-badge {
  display: inline-block;
  width: fit-content;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}

.channel-badge.seckill {
  background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
  color: white;
}

.channel-badge.special {
  background: linear-gradient(135deg, #ff9f43, #f39c12);
  color: white;
}

.channel-badge.regular {
  background: #6c757d;
  color: white;
}

.ticket-name {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.seat-area {
  font-size: 14px;
  color: #666;
}

.ticket-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #fa8c16;
}

.countdown-mini {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #ff4d4f;
}

.countdown-mini.not-started {
  color: #fa8c16;
}

.ticket-right {
  text-align: right;
}

.ticket-price {
  font-size: 28px;
  font-weight: 700;
  color: #ff4d4f;
}

.ticket-stock-info {
  margin-top: 8px;
}

.stock-label {
  display: inline-block;
  font-size: 13px;
  padding: 3px 10px;
  border-radius: 4px;
}

.stock-label.available {
  color: #52c41a;
  background: #f6ffed;
}

.stock-label.soldout {
  color: #999;
  background: #f5f5f5;
}

.stock-label.ended {
  color: #999;
  background: #f5f5f5;
}

.ticket-actions {
  display: flex;
  justify-content: flex-end;
}

.loading-error {
  padding: 60px 0;
}

.empty-tickets {
  padding: 40px 0;
}

@media (max-width: 768px) {
  .detail-container {
    flex-direction: column;
  }
  .poster-section {
    flex: none;
  }
  .info-cards {
    grid-template-columns: 1fr;
  }
}
</style>
