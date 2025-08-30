<template>
  <div class="home-page">
    <!-- 轮播Banner -->
    <div class="banner-section">
      <el-carousel :interval="4000" type="card" height="300px" v-if="banners.length > 0">
        <el-carousel-item v-for="banner in banners" :key="banner.id">
          <div class="banner-item" :style="{ backgroundImage: `url(${banner.image})` }">
            <div class="banner-content">
              <h2>{{ banner.title }}</h2>
              <p>{{ banner.subtitle }}</p>
              <el-button type="primary" @click="goToDetail(banner.productId)">立即抢购</el-button>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
      <el-carousel height="300px" v-else>
        <el-carousel-item>
          <div class="banner-item default-banner">
            <div class="banner-content">
              <h2>🎵 演唱会门票秒杀</h2>
              <p>热门演出 限时抢购</p>
              <el-button type="primary" @click="$router.push('/products')">查看全部演出</el-button>
            </div>
          </div>
        </el-carousel-item>
      </el-carousel>
    </div>

    <div class="main-content">
      <!-- 分类导航 -->
      <div class="category-nav">
        <div class="category-item" @click="goToProducts('seckill')">
          <span class="icon">🔥</span>
          <span>秒杀专场</span>
        </div>
        <div class="category-item" @click="goToProducts('special')">
          <span class="icon">🎉</span>
          <span>特价优惠</span>
        </div>
        <div class="category-item" @click="goToProducts('regular')">
          <span class="icon">🎫</span>
          <span>常规购票</span>
        </div>
        <div class="category-item" @click="goToProducts('')">
          <span class="icon">📅</span>
          <span>近期演出</span>
        </div>
      </div>

      <!-- 即将开售 / 进行中 -->
      <div class="section" v-if="upcomingSales.length > 0">
        <div class="section-header">
          <h2 class="section-title">🔥 抢购专场</h2>
          <el-button type="text" @click="goToProducts('seckill')">查看更多 →</el-button>
        </div>
        <div class="countdown-list">
          <div v-for="item in upcomingSales" :key="item.ticketTypeId" class="countdown-card" @click="goToTicket(item.ticketTypeId)">
            <div class="countdown-image">
              <img :src="(item.posterUrl && (String(item.posterUrl).startsWith('http') || String(item.posterUrl).startsWith('/'))) ? item.posterUrl : getDefaultImage(item.productId)" :alt="item.productName" />
              <div class="seckill-tag" v-if="item.channel === 'seckill'">秒杀</div>
              <div class="special-tag" v-if="item.channel === 'special'">特价</div>
              <div class="status-overlay" :class="getActivityStatusClass(item.activityStatus)">
                {{ getActivityStatusText(item.activityStatus) }}
              </div>
            </div>
            <div class="countdown-info">
              <h3>{{ item.productName }}</h3>
              <p class="ticket-type" v-if="item.typeName">{{ item.typeName }}</p>
              <p class="venue">{{ item.venue }}</p>
              <div class="countdown-timer">
                <span>{{ item.activityStatus === 1 ? '距结束' : '距开售' }}</span>
                <span class="time">{{ getCountdownDisplay(item) }}</span>
              </div>
              <p class="price">¥{{ item.price }} · {{ item.availableStock > 0 ? '剩余 '+item.availableStock+'张' : '已售罄' }}</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 全部演出 -->
      <div class="section">
        <div class="section-header">
          <h2 class="section-title">🎵 演出列表</h2>
        </div>
        <div class="product-list-compact">
          <div v-for="product in allProducts" :key="product.id" class="product-item" @click="goToDetail(product.id)">
            <div class="item-image">
              <img :src="(product.posterUrl && (String(product.posterUrl).startsWith('http') || String(product.posterUrl).startsWith('/'))) ? product.posterUrl : getDefaultImage(product.id)" :alt="product.name" />
            </div>
            <div class="item-info">
              <h3>{{ product.name }}</h3>
              <p class="venue">{{ product.venue }}</p>
              <p class="date">{{ formatDate(product.showTime) }}</p>
            </div>
            <div class="item-tags">
              <span v-if="product.hasSeckill" class="tag seckill">秒杀</span>
              <span v-if="product.hasSpecial" class="tag special">特价</span>
            </div>
            <div class="item-price">
              <span class="price">¥{{ product.lowestPrice }} 起</span>
              <el-button type="primary" size="small">选座购票</el-button>
            </div>
          </div>
        </div>
        <div class="load-more">
          <el-button @click="goToProducts('')">查看全部演出</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { productApi } from '../api/index.js'

const router = useRouter()
const banners = ref([])
const upcomingSales = ref([])
const allProducts = ref([])

// 默认Banner数据 - 按 productId 顺序：1=周杰伦 2=五月天 3=薛之谦
const defaultBanners = [
  {
    id: 1,
    title: '周杰伦 2026 嘉年华世界巡回演唱会',
    subtitle: '北京鸟巢 限时秒杀',
    image: '/poster-1.svg',
    productId: 1
  },
  {
    id: 2,
    title: '五月天 2026 好好好想见到你',
    subtitle: '上海梅赛德斯奔驰中心 即将开票',
    image: '/poster-2.svg',
    productId: 2
  },
  {
    id: 3,
    title: '薛之谦 天外来物 巡回演唱会',
    subtitle: '深圳春茧体育馆',
    image: '/poster-3.svg',
    productId: 3
  }
]

const loadData = async () => {
  try {
    // 加载轮播演出（管理员标记的）
    const bannerRes = await productApi.getBanners()
    if (bannerRes.code === 200 && bannerRes.data && bannerRes.data.length > 0) {
      banners.value = bannerRes.data.map((p, idx) => ({
      id: p.id,
      title: p.name,
      subtitle: `${p.venue} · ${formatShowTime(p.showTime)}`,
      image: (p.posterUrl && (String(p.posterUrl).startsWith('http') || String(p.posterUrl).startsWith('/')))
        ? p.posterUrl : getDefaultImage(p.id),
      color: getColorByIndex(idx),
      productId: p.id
    }))
    } else {
      banners.value = defaultBanners
    }

    // 加载即将开售/进行中的秒杀配置（真实倒计时数据）
    try {
      const upcomingRes = await productApi.getSeckillUpcoming()
      if (upcomingRes.code === 200 && upcomingRes.data && upcomingRes.data.length > 0) {
        upcomingSales.value = upcomingRes.data.slice(0, 6)
      } else {
        upcomingSales.value = []
      }
    } catch (e) {
      console.warn('获取秒杀配置失败，使用空数据', e)
      upcomingSales.value = []
    }

    // 加载全部已发布演出
    const result = await productApi.getList()
    if (result.code === 200) {
      const products = result.data || []
      allProducts.value = products.slice(0, 6)
    }
  } catch (error) {
    console.error('加载数据失败:', error)
    banners.value = defaultBanners
  }
}

// 默认图片列表 - 索引按 productId 稳定映射：1=周杰伦 2=五月天 3=薛之谦
const defaultImageList = [
  '/poster-1.svg', // index 0 备用
  '/poster-1.svg', // index 1 周杰伦演唱会
  '/poster-2.svg', // index 2 五月天演唱会
  '/poster-3.svg', // index 3 薛之谦演唱会
  '/poster-1.svg'  // index 4 备用
]

const getImageUrl = (url) => {
  if (!url) return defaultImageList[0]
  if (url.startsWith('http') || url.startsWith('/')) return url
  return defaultImageList[0]
}

const getDefaultImage = (id) => {
  const n = Number(id)
  if (!Number.isFinite(n) || n < 0) return defaultImageList[0]
  return defaultImageList[Math.floor(n) % defaultImageList.length]
}

const getColorByIndex = (index) => {
  const colors = [
    'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
    'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
    'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    'linear-gradient(135deg, #fa709a 0%, #fee140 100%)'
  ]
  const i = Number.isFinite(Number(index)) ? Math.floor(Number(index)) % colors.length : 0
  return colors[i]
}

const formatShowTime = (date) => {
  if (!date) return ''
  const d = new Date(date)
  if (isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const goToDetail = (productId) => {
  router.push(`/product/${productId}`)
}

// 点击"抢购专场"卡片：跳转到单张票详情页
const goToTicket = (ticketTypeId) => {
  router.push(`/ticket/${ticketTypeId}`)
}

const goToProducts = (channel) => {
  if (channel) {
    router.push({ path: '/products', query: { channel } })
  } else {
    router.push('/products')
  }
}

const formatDate = (date) => {
  if (!date) return '待定'
  const d = new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// 秒杀配置倒计时显示（实时更新）
const getCountdownDisplay = (item) => {
  const now = new Date().getTime()
  let targetTime

  if (item.activityStatus === 1) {
    // 进行中：倒计时到结束
    targetTime = new Date(item.endTime).getTime()
  } else if (item.activityStatus === 0) {
    // 未开始：倒计时到开始
    targetTime = new Date(item.startTime).getTime()
  } else {
    // 已结束
    return '已结束'
  }

  const diff = targetTime - now
  if (diff <= 0) return '00:00:00'

  const hours = Math.floor(diff / (1000 * 60 * 60))
  const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60))
  const seconds = Math.floor((diff % (1000 * 60)) / 1000)
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

const getActivityStatusText = (status) => {
  if (status === 0) return '即将开售'
  if (status === 1) return '热卖中'
  return '已结束'
}

const getActivityStatusClass = (status) => {
  if (status === 0) return 'status-upcoming'
  if (status === 1) return 'status-active'
  return 'status-ended'
}

// 定时刷新倒计时（每秒更新）
let countdownInterval = null
onMounted(() => {
  loadData()
  countdownInterval = setInterval(() => {
    upcomingSales.value = [...upcomingSales.value]
  }, 1000)
})

onUnmounted(() => {
  if (countdownInterval) {
    clearInterval(countdownInterval)
    countdownInterval = null
  }
})
</script>

<style scoped>
.home-page {
  background: #f5f7fa;
  min-height: 100vh;
}

.banner-section {
  margin-bottom: 30px;
}

.banner-item {
  height: 300px;
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
}

.banner-item.default-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.banner-content {
  text-align: center;
  color: white;
  background: rgba(0, 0, 0, 0.5);
  padding: 40px 60px;
  border-radius: 12px;
}

.banner-content h2 {
  font-size: 32px;
  margin-bottom: 10px;
}

.banner-content p {
  font-size: 18px;
  margin-bottom: 20px;
}

.main-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 20px;
}

.category-nav {
  display: flex;
  justify-content: center;
  gap: 40px;
  margin-bottom: 40px;
}

.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  transition: transform 0.2s;
}

.category-item:hover {
  transform: translateY(-5px);
}

.category-item .icon {
  font-size: 40px;
}

.category-item span:last-child {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.section {
  margin-bottom: 40px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.section-title {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.countdown-list {
  display: flex;
  gap: 20px;
  overflow-x: auto;
  padding-bottom: 10px;
}

.countdown-card {
  flex: 0 0 280px;
  background: white;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.countdown-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.countdown-image {
  position: relative;
  height: 150px;
  overflow: hidden;
}

.countdown-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.seckill-tag {
  position: absolute;
  top: 10px;
  left: 10px;
  background: linear-gradient(135deg, #ff6b6b, #ee5a5a);
  color: white;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
}

.special-tag {
  position: absolute;
  top: 10px;
  left: 10px;
  background: linear-gradient(135deg, #ff9f43, #f39c12);
  color: white;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 12px;
}

.status-overlay {
  position: absolute;
  top: 10px;
  right: 10px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}

.status-overlay.status-upcoming {
  background: rgba(64, 158, 255, 0.9);
  color: white;
}

.status-overlay.status-active {
  background: rgba(245, 108, 108, 0.9);
  color: white;
}

.status-overlay.status-ended {
  background: rgba(144, 147, 153, 0.9);
  color: white;
}

.countdown-info {
  padding: 15px;
}

.countdown-info h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.countdown-info .ticket-type {
  font-size: 14px;
  color: #1890ff;
  font-weight: 600;
  margin: 0 0 6px 0;
}

.countdown-info .venue {
  font-size: 13px;
  color: #666;
  margin: 0 0 10px 0;
}

.countdown-timer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.countdown-timer span:first-child {
  font-size: 12px;
  color: #999;
}

.countdown-timer .time {
  font-family: monospace;
  font-size: 16px;
  color: #ff6b6b;
  font-weight: 600;
}

.countdown-info .price {
  font-size: 18px;
  font-weight: 600;
  color: #ff6b6b;
  margin: 0;
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
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.product-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.product-image {
  position: relative;
  height: 180px;
  overflow: hidden;
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-image .seckill-tag,
.product-image .special-tag {
  position: absolute;
  top: 10px;
  right: 10px;
}

.product-info {
  padding: 15px;
}

.product-info h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-info .venue,
.product-info .date {
  font-size: 13px;
  color: #666;
  margin: 0 0 4px 0;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-top: 10px;
}

.price-row .price {
  font-size: 20px;
  font-weight: 600;
  color: #ff6b6b;
}

.price-row .price-tag {
  font-size: 12px;
  color: #999;
}

.product-list-compact {
  background: white;
  border-radius: 12px;
  overflow: hidden;
}

.product-item {
  display: flex;
  align-items: center;
  padding: 15px 20px;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.2s;
}

.product-item:last-child {
  border-bottom: none;
}

.product-item:hover {
  background: #f9f9f9;
}

.item-image {
  width: 80px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  margin-right: 15px;
}

.item-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.item-info {
  flex: 1;
}

.item-info h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 5px 0;
}

.item-info .venue,
.item-info .date {
  font-size: 13px;
  color: #666;
  margin: 0;
}

.item-tags {
  display: flex;
  gap: 8px;
  margin: 0 20px;
}

.item-tags .tag {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
}

.item-tags .tag.seckill {
  background: #fff1f0;
  color: #ff4d4f;
}

.item-tags .tag.special {
  background: #fff7e6;
  color: #faad14;
}

.item-price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.item-price .price {
  font-size: 18px;
  font-weight: 600;
  color: #ff6b6b;
}

.load-more {
  text-align: center;
  padding: 20px;
}
</style>
