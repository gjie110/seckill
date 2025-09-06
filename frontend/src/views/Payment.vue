<template>
  <div class="payment-page">
    <el-breadcrumb separator="/" class="breadcrumb">
      <el-breadcrumb-item @click="goHome">首页</el-breadcrumb-item>
      <el-breadcrumb-item>订单支付</el-breadcrumb-item>
    </el-breadcrumb>

    <div class="payment-container">
      <div class="payment-main">
        <!-- 订单信息 -->
        <div class="order-info-section" v-if="order">
          <div class="order-header">
            <h2>📋 订单信息</h2>
            <div class="order-status">
              <span class="status-tag pending" v-if="order.status === 0">待支付</span>
              <span class="status-tag paid" v-else-if="order.status === 1">已支付</span>
            </div>
          </div>

          <div class="order-details">
            <div class="detail-row">
              <span class="label">订单号</span>
              <span class="value">{{ order.orderNo }}</span>
            </div>
            <div class="detail-row">
              <span class="label">演出名称</span>
              <span class="value">{{ order.productName || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="label">票档</span>
              <span class="value">{{ order.ticketTypeName || '-' }}</span>
            </div>
            <div class="detail-row">
              <span class="label">数量</span>
              <span class="value">{{ order.quantity }} 张</span>
            </div>
            <div class="detail-row">
              <span class="label">单价</span>
              <span class="value">¥{{ order.unitPrice }}</span>
            </div>
          </div>

          <div class="total-row">
            <span class="label">应付金额</span>
            <span class="amount">¥{{ order.totalAmount }}</span>
          </div>
        </div>

        <!-- 支付倒计时 -->
        <div class="countdown-section" v-if="order && order.status === 0">
          <div class="countdown-info">
            <span class="icon">⏰</span>
            <span class="text">请在</span>
            <span class="time">{{ countdown }}</span>
            <span class="text">内完成支付，超时订单将自动取消</span>
          </div>
        </div>

        <!-- 支付方式 -->
        <div class="payment-methods" v-if="order && order.status === 0">
          <h3>💳 选择支付方式</h3>
          <div class="method-list">
            <div
              class="method-item"
              :class="{ selected: selectedMethod === 'alipay' }"
              @click="selectedMethod = 'alipay'"
            >
              <div class="method-icon">
                <img src="https://img.icons8.com/color/48/alipay.png" alt="支付宝" />
              </div>
              <div class="method-info">
                <span class="method-name">支付宝</span>
                <span class="method-desc">推荐有支付宝账户的用户使用</span>
              </div>
              <div class="method-check">
                <el-icon v-if="selectedMethod === 'alipay'"><Check /></el-icon>
              </div>
            </div>

            <div
              class="method-item"
              :class="{ selected: selectedMethod === 'wechat' }"
              @click="selectedMethod = 'wechat'"
            >
              <div class="method-icon">
                <img src="https://img.icons8.com/color/48/wechat.png" alt="微信支付" />
              </div>
              <div class="method-info">
                <span class="method-name">微信支付</span>
                <span class="method-desc">推荐有微信账户的用户使用</span>
              </div>
              <div class="method-check">
                <el-icon v-if="selectedMethod === 'wechat'"><Check /></el-icon>
              </div>
            </div>

            <div
              class="method-item"
              :class="{ selected: selectedMethod === 'bank' }"
              @click="selectedMethod = 'bank'"
            >
              <div class="method-icon">
                <img src="https://img.icons8.com/color/48/bank-card.png" alt="银行卡" />
              </div>
              <div class="method-info">
                <span class="method-name">银行卡支付</span>
                <span class="method-desc">支持各大银行借记卡及信用卡</span>
              </div>
              <div class="method-check">
                <el-icon v-if="selectedMethod === 'bank'"><Check /></el-icon>
              </div>
            </div>
          </div>
        </div>

        <!-- 已支付状态 -->
        <div class="paid-section" v-if="order && order.status === 1">
          <div class="paid-icon">✅</div>
          <h2>支付成功</h2>
          <p>您的订单已支付成功，请到"我的票"查看电子票</p>
          <div class="paid-actions">
            <el-button type="primary" @click="goToMyTickets">查看我的票</el-button>
            <el-button @click="goHome">返回首页</el-button>
          </div>
        </div>

        <!-- 支付按钮 -->
        <div class="pay-button-section" v-if="order && order.status === 0">
          <el-button
            type="primary"
            size="large"
            class="pay-button"
            @click="handlePay"
            :loading="paying"
          >
            确认支付 ¥{{ order?.totalAmount }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 支付成功对话框 -->
    <el-dialog v-model="showSuccessDialog" title="支付成功" width="400px" :close-on-click-modal="false">
      <div class="success-content">
        <div class="success-icon">🎉</div>
        <p>恭喜！您已成功购票</p>
        <p class="order-info">订单号：{{ order?.orderNo }}</p>
      </div>
      <template #footer>
        <el-button @click="goToMyTickets">查看我的票</el-button>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { orderApi } from '../api/index.js'

const route = useRoute()
const router = useRouter()

const order = ref(null)
const countdown = ref('14:59')
const selectedMethod = ref('alipay')
const paying = ref(false)
const showSuccessDialog = ref(false)

let countdownTimer = null
let timeoutCheckTimer = null

onMounted(() => {
  loadOrder()
})

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer)
  if (timeoutCheckTimer) clearInterval(timeoutCheckTimer)
})

const loadOrder = async () => {
  const orderNo = route.query.orderNo
  if (!orderNo) {
    ElMessage.error('订单号不存在')
    router.push('/')
    return
  }

  try {
    const result = await orderApi.getDetail(orderNo)
    if (result.code === 200) {
      order.value = result.data

      if (order.value.status === 0) {
        startCountdown()
        startTimeoutCheck()
      }
    }
  } catch (error) {
    console.error('加载订单失败:', error)
    ElMessage.error('加载失败')
  }
}

const startCountdown = () => {
  if (!order.value?.timeoutTime) return

  const updateCountdown = () => {
    const now = new Date().getTime()
    const timeout = new Date(order.value.timeoutTime).getTime()
    const diff = timeout - now

    if (diff <= 0) {
      countdown.value = '00:00'
      handleTimeout()
      return
    }

    const minutes = Math.floor(diff / (1000 * 60))
    const seconds = Math.floor((diff % (1000 * 60)) / 1000)
    countdown.value = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }

  updateCountdown()
  countdownTimer = setInterval(updateCountdown, 1000)
}

const startTimeoutCheck = () => {
  // 每10秒检查一次订单状态
  timeoutCheckTimer = setInterval(async () => {
    try {
      const result = await orderApi.getDetail(order.value.orderNo)
      if (result.code === 200) {
        order.value = result.data
        if (order.value.status !== 0) {
          clearInterval(countdownTimer)
          clearInterval(timeoutCheckTimer)
        }
      }
    } catch (error) {
      console.error('检查订单状态失败:', error)
    }
  }, 10000)
}

const handleTimeout = () => {
  clearInterval(countdownTimer)
  clearInterval(timeoutCheckTimer)
  ElMessage.warning('订单已超时，请重新下单')
  router.push('/products')
}

const handlePay = async () => {
  if (!order.value) return

  paying.value = true

  // 模拟支付过程
  setTimeout(async () => {
    try {
      const result = await orderApi.pay(order.value.orderNo, order.value.userId)
      if (result.code === 200) {
        clearInterval(countdownTimer)
        clearInterval(timeoutCheckTimer)
        order.value.status = 1
        showSuccessDialog.value = true
      } else {
        ElMessage.error(result.msg || '支付失败')
      }
    } catch (error) {
      ElMessage.error('支付失败')
    } finally {
      paying.value = false
    }
  }, 1500)
}

const goHome = () => {
  router.push('/')
}

const goToMyTickets = () => {
  showSuccessDialog.value = false
  router.push('/my-tickets')
}
</script>

<style scoped>
.payment-page {
  padding: 20px;
  max-width: 600px;
  margin: 0 auto;
}

.breadcrumb {
  margin-bottom: 20px;
}

.payment-container {
  background: white;
  border-radius: 12px;
  padding: 30px;
}

.order-info-section {
  margin-bottom: 25px;
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.order-header h2 {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.status-tag {
  padding: 6px 16px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 500;
}

.status-tag.pending {
  background: #fff7e6;
  color: #fa8c16;
}

.status-tag.paid {
  background: #f6ffed;
  color: #52c41a;
}

.order-details {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  overflow: hidden;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  padding: 12px 15px;
  border-bottom: 1px solid #f0f0f0;
}

.detail-row:last-child {
  border-bottom: none;
}

.detail-row .label {
  color: #666;
  font-size: 14px;
}

.detail-row .value {
  color: #333;
  font-size: 14px;
}

.total-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 2px dashed #f0f0f0;
}

.total-row .label {
  font-size: 16px;
  color: #333;
}

.total-row .amount {
  font-size: 32px;
  font-weight: 700;
  color: #ff4d4f;
}

.countdown-section {
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 8px;
  padding: 15px 20px;
  margin-bottom: 25px;
}

.countdown-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #666;
}

.countdown-info .icon {
  font-size: 20px;
}

.countdown-info .time {
  font-family: monospace;
  font-size: 18px;
  font-weight: 700;
  color: #ff4d4f;
  background: white;
  padding: 4px 12px;
  border-radius: 4px;
}

.payment-methods h3 {
  font-size: 16px;
  font-weight: 600;
  margin: 0 0 15px 0;
}

.method-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.method-item {
  display: flex;
  align-items: center;
  gap: 15px;
  padding: 15px;
  border: 2px solid #f0f0f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.method-item:hover {
  border-color: #409eff;
}

.method-item.selected {
  border-color: #409eff;
  background: #f0f7ff;
}

.method-icon {
  width: 40px;
  height: 40px;
}

.method-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.method-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.method-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.method-desc {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.method-check {
  width: 24px;
  height: 24px;
  color: #409eff;
  font-size: 20px;
}

.paid-section {
  text-align: center;
  padding: 40px 0;
}

.paid-icon {
  font-size: 64px;
  margin-bottom: 20px;
}

.paid-section h2 {
  font-size: 24px;
  font-weight: 600;
  margin: 0 0 15px 0;
  color: #52c41a;
}

.paid-section p {
  color: #666;
  margin: 0 0 25px 0;
}

.paid-actions {
  display: flex;
  justify-content: center;
  gap: 15px;
}

.pay-button-section {
  margin-top: 25px;
}

.pay-button {
  width: 100%;
  height: 50px;
  font-size: 18px;
}

.success-content {
  text-align: center;
  padding: 20px 0;
}

.success-icon {
  font-size: 64px;
  margin-bottom: 15px;
}

.success-content p {
  font-size: 16px;
  color: #333;
  margin: 0 0 10px 0;
}

.success-content .order-info {
  font-size: 14px;
  color: #666;
}
</style>
