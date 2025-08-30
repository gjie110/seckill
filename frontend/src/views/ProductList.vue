<template>
  <div class="product-list-page">
    <div class="page-header">
      <h1>
        🎵
        <span v-if="activeChannel">{{ channelLabel }}演出</span>
        <span v-else>全部演出</span>
      </h1>
      <p v-if="activeChannel" class="channel-subtitle">
        {{ channelSubtitle }}
      </p>
    </div>

    <div class="filter-section">
      <div class="search-bar">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索演出名称"
          class="search-input"
          clearable
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>

      <div class="channel-bar">
        <el-button
          :type="activeChannel === '' ? 'primary' : 'default'"
          size="small"
          @click="setChannel('')"
        >全部</el-button>
        <el-button
          :type="activeChannel === 'seckill' ? 'primary' : 'default'"
          size="small"
          @click="setChannel('seckill')"
        >秒杀</el-button>
        <el-button
          :type="activeChannel === 'special' ? 'primary' : 'default'"
          size="small"
          @click="setChannel('special')"
        >特价</el-button>
        <el-button
          :type="activeChannel === 'regular' ? 'primary' : 'default'"
          size="small"
          @click="setChannel('regular')"
        >常规</el-button>
      </div>
    </div>

    <div class="result-info">
      <span>共找到 <strong>{{ total }}</strong> 场演出</span>
    </div>

    <div class="product-grid" v-loading="loading">
      <div
        v-for="product in productList"
        :key="product.id"
        class="product-card"
        @click="goDetail(product)"
      >
        <div class="card-image">
          <img :src="(product.posterUrl && (String(product.posterUrl).startsWith('http') || String(product.posterUrl).startsWith('/'))) ? product.posterUrl : getDefaultImage(product.id)" :alt="product.name" />
          <div class="card-status hot" v-if="product.activityStatus === 1">
            <span>热卖中</span>
          </div>
          <div class="card-status coming" v-else-if="product.activityStatus === 0">
            <span>即将开售</span>
          </div>
          <div class="card-status ended" v-else-if="product.activityStatus === 2">
            <span>已结束</span>
          </div>
          <div class="card-status coming" v-else>
            <span>预售中</span>
          </div>
          <div class="channel-flags">
            <span v-if="activeChannel === 'seckill' || (!activeChannel && hasChannel(product, 'seckill'))" class="channel-flag seckill">秒杀</span>
            <span v-if="activeChannel === 'special' || (!activeChannel && hasChannel(product, 'special'))" class="channel-flag special">特价</span>
            <span v-if="activeChannel === 'regular' || (!activeChannel && hasChannel(product, 'regular'))" class="channel-flag regular">常规</span>
          </div>
        </div>
        <div class="card-content">
          <h3 class="product-name">{{ product.name }}</h3>
          <p class="ticket-type-line" v-if="product.ticketTypeName">{{ product.ticketTypeName }}</p>
          <div class="product-info">
            <div class="info-row">
              <el-icon class="icon"><Location /></el-icon>
              <span>{{ product.venue || '待定' }}</span>
            </div>
            <div class="info-row">
              <el-icon class="icon"><Clock /></el-icon>
              <span>{{ formatDateTime(product.showTime) }}</span>
            </div>
          </div>
          <div
            v-if="product.activityStatus === 1"
            class="activity-banner active"
          >
            <el-icon><Clock /></el-icon>
            <span>活动进行中，距结束 {{ countdowns[product.id] || getCountdownText(product) }}</span>
          </div>
          <div
            v-else-if="product.activityStatus === 0"
            class="activity-banner pending"
          >
            <el-icon><Clock /></el-icon>
            <span>尚未开售，距开始 {{ countdowns[product.id] || getCountdownText(product) }}</span>
          </div>
          <div class="card-footer">
            <span class="price-label">¥{{ product.lowestPrice || 0 }}</span>
            <el-button
              v-if="product.activityStatus === 2"
              type="info"
              size="small"
              disabled
            >
              已结束
            </el-button>
            <el-button v-else type="primary" size="small">查看详情</el-button>
          </div>
        </div>
      </div>

      <div v-if="productList.length === 0 && !loading" class="empty-state">
        <el-empty description="暂无相关演出" />
      </div>
    </div>

    <div class="pagination-wrapper" v-if="total > pageSize">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="prev, pager, next, jumper"
        @current-change="handlePageChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Search, Location, Clock } from '@element-plus/icons-vue'
import { productApi } from '../api/index.js'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const rawProducts = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(12)
const countdowns = reactive({})     // product.id -> HH:MM:SS
let countdownTimer = null

const searchKeyword = ref('')
const activeChannel = ref('')

const channelLabel = computed(() => {
  const map = { seckill: '秒杀专场', special: '特价优惠', regular: '常规购票' }
  return map[activeChannel.value] || ''
})
const channelSubtitle = computed(() => {
  const map = {
    seckill: '限时秒杀，手慢无！',
    special: '特价优惠，数量有限！',
    regular: '常规渠道长期在售'
  }
  return map[activeChannel.value] || ''
})

const hasChannel = (product, ch) => {
  if (!product) return false
  if (Array.isArray(product.channels) && product.channels.indexOf(ch) >= 0) return true
  if (ch === 'seckill' && product.hasSeckill) return true
  if (ch === 'special' && product.hasSpecial) return true
  if (ch === 'regular' && product.hasRegular) return true
  return false
}

/** 本地在 rawProducts 上做关键词筛选 + 分页；后端已按 channel 做筛选 */
const productList = computed(() => {
  let list = rawProducts.value || []
  if (searchKeyword.value && searchKeyword.value.trim() !== '') {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(p => (p.name || '').toLowerCase().indexOf(kw) >= 0)
  }
  total.value = list.length
  const start = (currentPage.value - 1) * pageSize.value
  return list.slice(start, start + pageSize.value)
})

watch(() => route.query.channel, (val) => {
  activeChannel.value = val || ''
  currentPage.value = 1
  loadProducts()
})

const setChannel = (ch) => {
  activeChannel.value = ch || ''
  currentPage.value = 1
  // 同步到路由（方便分享/收藏）
  router.push({ path: '/products', query: ch ? { channel: ch } : {} })
  loadProducts()
}

const loadProducts = async () => {
  loading.value = true
  try {
    const result = await productApi.getList(activeChannel.value || undefined)
    if (result.code === 200) {
      rawProducts.value = result.data || []
    }
  } catch (error) {
    console.error('加载演出列表失败:', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
}

const handlePageChange = (page) => {
  currentPage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

const getCountdownText = (product) => {
  if (!product) return ''
  const now = Date.now()
  let target = null
  if (product.activityStatus === 0 && product.startTime) {
    target = new Date(product.startTime).getTime()
  } else if (product.activityStatus === 1 && product.endTime) {
    target = new Date(product.endTime).getTime()
  }
  if (!target) return ''
  const diff = target - now
  if (diff <= 0) return '00:00:00'
  const h = Math.floor(diff / (1000 * 60 * 60))
  const m = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  const s = Math.floor((diff % (1000 * 60)) / 1000)
  if (h > 24 * 30) {
    const days = Math.floor(h / 24)
    return `${days} 天 ${String(h % 24).padStart(2, '0')} 小时`
  }
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

const tickCountdown = () => {
  const list = rawProducts.value || []
  list.forEach((p) => {
    if (p && p.id != null && (p.activityStatus === 0 || p.activityStatus === 1)) {
      countdowns[p.id] = getCountdownText(p)
    }
  })
}

onMounted(() => {
  activeChannel.value = route.query.channel || ''
  loadProducts()
  countdownTimer = setInterval(tickCountdown, 1000)
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
})

const goDetail = (product) => {
  // 如果有具体票种 ID：跳转到单张票详情页（只显示该票）
  if (product && product.ticketTypeId) {
    router.push(`/ticket/${product.ticketTypeId}`)
    return
  }
  // 否则跳到演出总览页（含 channel 筛选）
  const id = typeof product === 'object' ? product.id : product
  if (activeChannel.value) {
    router.push({ path: `/product/${id}`, query: { channel: activeChannel.value } })
  } else {
    router.push(`/product/${id}`)
  }
}

const getDefaultImage = (id) => {
  const images = [
    '/poster-1.svg', // 周杰伦演唱会
    '/poster-2.svg', // 五月天演唱会
    '/poster-3.svg', // 薛之谦演唱会
    '/poster-1.svg'  // 备用
  ]
  return images[((id || 1) - 1) % images.length]
}

const formatDateTime = (dateStr) => {
  if (!dateStr) return '待定'
  const date = new Date(dateStr)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.product-list-page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h1 {
  font-size: 28px;
  font-weight: 600;
  margin: 0;
}

.channel-subtitle {
  margin: 8px 0 0 0;
  color: #666;
  font-size: 14px;
}

.filter-section {
  background: white;
  padding: 20px;
  border-radius: 12px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.search-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
}

.search-input {
  flex: 1;
  max-width: 400px;
}

.channel-bar {
  display: flex;
  gap: 10px;
}

.result-info {
  margin-bottom: 15px;
  color: #666;
}

.result-info strong {
  color: #409eff;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
}

.product-card {
  background: white;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.product-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.card-image {
  position: relative;
  height: 200px;
  overflow: hidden;
}

.card-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-status {
  position: absolute;
  top: 12px;
  left: 12px;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
  color: white;
}

.card-status.hot {
  background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
}

.card-status.coming {
  background: rgba(0, 0, 0, 0.6);
}

.card-status.ended {
  background: linear-gradient(135deg, #888, #666);
  opacity: 0.7;
}

.channel-flags {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  gap: 6px;
}

.channel-flag {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  color: white;
}

.channel-flag.seckill {
  background: #ff4d4f;
}

.channel-flag.special {
  background: #fa8c16;
}

.channel-flag.regular {
  background: #595959;
}

.card-content {
  padding: 15px;
}

.product-name {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #333;
}

.ticket-type-line {
  font-size: 13px;
  color: #1890ff;
  font-weight: 600;
  margin: 0 0 8px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-info {
  margin-bottom: 15px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #666;
  margin-bottom: 5px;
}

.info-row .icon {
  font-size: 14px;
  color: #999;
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.price-label {
  font-size: 16px;
  color: #ff4d4f;
  font-weight: 700;
}

.empty-state {
  grid-column: 1 / -1;
  padding: 60px 0;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}

@media (max-width: 1024px) {
  .product-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

.activity-banner {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 10px;
  border-radius: 6px;
  margin-bottom: 12px;
  font-size: 12px;
  font-weight: 500;
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

@media (max-width: 768px) {
  .product-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
