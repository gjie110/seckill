<template>
  <div class="checkout-page">
    <!-- 加载中 -->
    <el-skeleton v-if="loading" :rows="10" animated />

    <!-- 加载失败或数据为空 -->
    <div v-else-if="!product || !ticketType" class="empty-state">
      <el-empty description="订单数据加载失败">
        <el-button type="primary" @click="goBack">返回</el-button>
      </el-empty>
    </div>

    <!-- 正常内容 -->
    <template v-else>
      <el-breadcrumb separator="/" class="breadcrumb">
        <el-breadcrumb-item @click="goBack">演出详情</el-breadcrumb-item>
        <el-breadcrumb-item>确认订单</el-breadcrumb-item>
      </el-breadcrumb>

      <div class="checkout-container">
        <!-- 左侧：订单信息 -->
        <div class="order-section">
        <h2 class="section-title">📋 订单信息</h2>

        <div class="product-info" v-if="product">
          <div class="product-image">
            <img :src="(product.posterUrl && (String(product.posterUrl).startsWith('http') || String(product.posterUrl).startsWith('/'))) ? product.posterUrl : getDefaultImage(product.id)" :alt="product.name" />
          </div>
          <div class="product-detail">
            <h3>{{ product.name }}</h3>
            <p>{{ product.venue }}</p>
            <p>{{ formatDateTime(product.showTime) }}</p>
          </div>
        </div>

        <div class="ticket-info" v-if="ticketType">
          <div class="info-row">
            <span class="label">票档</span>
            <span class="value">
              <span class="channel-badge" :class="ticketType.channel">
                {{ getChannelName(ticketType.channel) }}
              </span>
              {{ ticketType.typeName }}
            </span>
          </div>
          <div class="info-row" v-if="ticketType.seatArea">
            <span class="label">座位</span>
            <span class="value">{{ ticketType.seatArea }}</span>
          </div>
          <div class="info-row">
            <span class="label">单价</span>
            <span class="value price">¥{{ ticketType.price }}</span>
          </div>
        </div>

        <div class="quantity-section">
          <span class="label">购买数量</span>
          <el-input-number v-model="quantity" :min="1" :max="maxQuantity" @change="handleQuantityChange" />
          <span class="hint">每单最多购买 {{ maxQuantity }} 张</span>
        </div>

        <div class="total-section">
          <span class="label">订单总额</span>
          <span class="total-price">¥{{ totalAmount }}</span>
        </div>
        </div>

        <!-- 右侧：购票人信息 -->
        <div class="buyer-section">
        <h2 class="section-title">👤 购票人信息</h2>

        <el-form ref="formRef" :model="buyerForm" :rules="rules" label-width="100px">
          <el-form-item label="真实姓名" prop="realName">
            <el-input v-model="buyerForm.realName" placeholder="请输入真实姓名" />
          </el-form-item>
          <el-form-item label="身份证号" prop="idCard">
            <el-input v-model="buyerForm.idCard" placeholder="请输入身份证号码" />
          </el-form-item>
          <el-form-item label="手机号码" prop="phone">
            <el-input v-model="buyerForm.phone" placeholder="请输入手机号码" />
          </el-form-item>
        </el-form>

        <div class="agreement">
          <el-checkbox v-model="agreed">
            我已阅读并同意
            <a href="#" @click.prevent>《购票协议》</a>
            和
            <a href="#" @click.prevent>《用户须知》</a>
          </el-checkbox>
        </div>

        <div class="action-buttons">
          <el-button @click="goBack">返回</el-button>
          <el-button type="primary" :disabled="!canSubmit" :loading="submitting" @click="handleSubmit">
            {{ submitting ? '提交中...' : '提交订单' }}
          </el-button>
        </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { productApi, orderApi } from '../api/index.js'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const product = ref(null)
const ticketType = ref(null)
const quantity = ref(1)
const maxQuantity = ref(5)
const agreed = ref(false)
const formRef = ref(null)

const buyerForm = reactive({
  realName: '',
  idCard: '',
  phone: ''
})

const rules = {
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  idCard: [
    { required: true, message: '请输入身份证号码', trigger: 'blur' },
    { pattern: /(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/, message: '身份证格式不正确', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号码', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

const totalAmount = computed(() => {
  if (!ticketType.value) return 0
  return (ticketType.value.price * quantity.value).toFixed(2)
})

const submitting = ref(false)

const canSubmit = computed(() => {
  return !submitting.value &&
         !loading.value &&
         product.value &&
         ticketType.value &&
         agreed.value &&
         buyerForm.realName &&
         buyerForm.idCard &&
         buyerForm.phone
})

onMounted(() => {
  loadData()
})

const loadData = async () => {
  const productId = route.query.productId
  const ticketTypeId = route.query.ticketTypeId

  if (!productId || !ticketTypeId) {
    ElMessage.error('参数错误')
    router.back()
    return
  }

  loading.value = true
  try {
    const result = await productApi.getDetail(productId)
    if (result.code === 200) {
      product.value = result.data.product
      ticketType.value = result.data.ticketTypes.find(t => t.id == ticketTypeId)

      if (!ticketType.value) {
        ElMessage.error('票档不存在')
        router.back()
      }

      // 设置最大购买数量
      const config = ticketType.value?.config
      if (config) {
        maxQuantity.value = config.maxPerUser || 5
      }
      maxQuantity.value = Math.min(maxQuantity.value, ticketType.value?.availableStock || 1)
    }
  } catch (error) {
    console.error('加载数据失败:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleQuantityChange = (val) => {
  quantity.value = val
}

const handleSubmit = async () => {
  if (!canSubmit.value) return

  // 先进行表单验证
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }

  submitting.value = true
  try {
    const userId = localStorage.getItem('userId') || 1001
    const result = await orderApi.create({
      productId: product.value.id,
      ticketTypeId: ticketType.value.id,
      userId: userId,
      quantity: quantity.value,
      channel: ticketType.value.channel,
      realName: buyerForm.realName,
      idCard: buyerForm.idCard,
      phone: buyerForm.phone
    })

    if (result.code === 200) {
      const orderNo = result.data
      router.push({ path: '/payment', query: { orderNo } })
    } else {
      ElMessage.error(result.msg || '下单失败')
      submitting.value = false
    }
  } catch (error) {
    console.error('下单失败:', error)
    ElMessage.error('下单失败，请稍后重试')
    submitting.value = false
  }
}

const goBack = () => {
  router.back()
}

const getChannelName = (channel) => {
  const names = { seckill: '秒杀', special: '特价', regular: '常规' }
  return names[channel] || channel
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
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
</script>

<style scoped>
.checkout-page {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}

.breadcrumb {
  margin-bottom: 20px;
}

.checkout-container {
  display: flex;
  gap: 30px;
}

.order-section {
  flex: 1;
  background: white;
  padding: 25px;
  border-radius: 12px;
}

.section-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 20px 0;
  color: #333;
}

.product-info {
  display: flex;
  gap: 15px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 20px;
}

.product-image {
  width: 100px;
  height: 100px;
  border-radius: 8px;
  overflow: hidden;
}

.product-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.product-detail h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 8px 0;
}

.product-detail p {
  font-size: 13px;
  color: #666;
  margin: 0;
}

.ticket-info {
  margin-bottom: 20px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f5f5f5;
}

.info-row .label {
  color: #666;
  font-size: 14px;
}

.info-row .value {
  font-size: 14px;
  color: #333;
}

.info-row .value.price {
  font-size: 18px;
  font-weight: 600;
  color: #ff4d4f;
}

.channel-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  margin-right: 8px;
}

.channel-badge.seckill {
  background: #fff1f0;
  color: #ff4d4f;
}

.channel-badge.special {
  background: #fff7e6;
  color: #fa8c16;
}

.channel-badge.regular {
  background: #f5f5f5;
  color: #666;
}

.quantity-section {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 15px;
}

.quantity-section .hint {
  font-size: 12px;
  color: #999;
}

.total-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px 0;
}

.total-section .label {
  font-size: 16px;
  color: #333;
}

.total-price {
  font-size: 28px;
  font-weight: 700;
  color: #ff4d4f;
}

.buyer-section {
  flex: 1;
  background: white;
  padding: 25px;
  border-radius: 12px;
}

.agreement {
  margin: 20px 0;
  font-size: 13px;
}

.agreement a {
  color: #409eff;
}

.action-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 15px;
  margin-top: 20px;
}
</style>
