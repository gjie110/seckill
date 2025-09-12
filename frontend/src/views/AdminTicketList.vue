<template>
  <div class="admin-ticket-container">
    <div class="page-header">
      <div class="header-left">
        <h2>🎫 票种管理</h2>
        <p class="header-subtitle">为每个演出添加票种并设定抢购时间与数量</p>
      </div>
      <div class="header-right">
        <el-select
          v-model="selectedProductId"
          class="product-select"
          placeholder="选择演出"
          @change="loadTicketTypes"
          style="width: 240px; margin-right: 12px"
        >
          <el-option
            v-for="product in productList"
            :key="product.id"
            :label="product.name"
            :value="product.id"
          />
        </el-select>
        <el-button type="primary" @click="openAddDialog" :disabled="!selectedProductId">
          <el-icon><Plus /></el-icon>
          新增票种
        </el-button>
      </div>
    </div>

    <div v-if="!selectedProductId" class="empty-state">
      <p>👆 请先选择一场演出</p>
    </div>

    <div v-else class="ticket-list">
      <div v-for="ticket in ticketTypes" :key="ticket.id" class="ticket-card">
        <div class="ticket-head">
          <div class="ticket-info">
            <div class="ticket-name-row">
              <span class="ticket-name">{{ ticket.typeName }}</span>
              <span class="channel-tag" :class="getChannelClass(ticket.channel)">
                {{ getChannelLabel(ticket.channel) }}
              </span>
              <span class="ticket-status" :class="getStatusClass(ticket.status)">
                {{ ticket.status === 1 ? '已发布' : '未发布' }}
              </span>
            </div>
            <div class="ticket-meta">
              <span>📍 {{ ticket.seatArea || '无座位区域' }}</span>
              <span class="ticket-price">¥{{ ticket.price }}</span>
            </div>
          </div>
        </div>

        <div class="ticket-stats">
          <div class="stat">
            <span class="stat-label">总库存</span>
            <span class="stat-value">{{ ticket.totalStock }}</span>
          </div>
          <div class="stat">
            <span class="stat-label">可用库存</span>
            <span class="stat-value" :class="{ low: ticket.availableStock < 10 }">
              {{ ticket.availableStock }}
            </span>
          </div>
          <div class="stat">
            <span class="stat-label">已售</span>
            <span class="stat-value sold">{{ ticket.soldStock }}</span>
          </div>
          <div class="stat" v-if="ticket.channel !== 'regular'">
            <span class="stat-label">限购</span>
            <span class="stat-value">{{ getMaxPerUser(ticket.id) }} 张/人</span>
          </div>
        </div>

        <div class="ticket-schedule" v-if="getConfig(ticket.id)">
          <el-icon><Clock /></el-icon>
          <span>{{ formatDateTime(getConfig(ticket.id).startTime) }} ~ {{ formatDateTime(getConfig(ticket.id).endTime) }}</span>
        </div>

        <div class="ticket-actions">
          <el-button size="small" link type="primary" @click="openEditDialog(ticket)">编辑</el-button>
          <el-button size="small" link @click="openAdjustStock(ticket)">库存调整</el-button>
          <el-button v-if="ticket.status === 0" size="small" link type="success" @click="openPublishDialog(ticket)">发布</el-button>
          <el-button v-if="ticket.status === 1" size="small" link type="warning" @click="handleUnpublish(ticket)">下架</el-button>
          <el-button size="small" link type="info" @click="openConfigDialog(ticket)">活动配置</el-button>
          <el-button v-if="ticket.channel !== 'regular'" size="small" link type="success" @click="openLimitDialog(ticket)">修改限购</el-button>
          <el-button v-if="ticket.status === 0 && ticket.soldStock === 0" size="small" link type="danger" @click="handleDelete(ticket)">删除</el-button>
        </div>
      </div>

      <div v-if="ticketTypes.length === 0" class="empty-state">
        <p>该演出暂无票种，请点击"新增票种"添加</p>
      </div>
    </div>

    <!-- 新增/编辑票种对话框 -->
    <el-dialog
      v-model="showFormDialog"
      :title="isEdit ? '编辑票种' : '新增票种（并发布）'"
      width="640px"
      destroy-on-close
    >
      <el-form ref="ticketFormRef" :model="ticketForm" :rules="ticketRules" label-width="120px">
        <el-form-item label="票种名称" prop="typeName">
          <el-input v-model="ticketForm.typeName" placeholder="如：A区VIP、B区学生票" />
        </el-form-item>
        <el-form-item label="座位区域">
          <el-input v-model="ticketForm.seatArea" placeholder="如：内场前区 A1-A5区" />
        </el-form-item>
        <el-form-item label="票价(元)" prop="price">
          <el-input-number v-model="ticketForm.price" :min="0" :precision="2" :step="100" style="width: 200px" />
        </el-form-item>
        <el-form-item label="销售渠道" prop="channel">
          <el-radio-group v-model="ticketForm.channel">
            <el-radio value="seckill">秒杀票</el-radio>
            <el-radio value="special">特价票</el-radio>
            <el-radio value="regular">常规票</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!isEdit" label="总库存" prop="totalStock">
          <el-input-number v-model="ticketForm.totalStock" :min="1" :max="100000" style="width: 200px" />
          <span class="form-tip">本场演出的票种总库存</span>
        </el-form-item>
        <el-form-item v-if="!isEdit" label="抢购数量" prop="seckillStock">
          <el-input-number v-model="ticketForm.seckillStock" :min="1" :max="100000" style="width: 200px" />
          <span class="form-tip">实际可抢购数量，不能超过总库存</span>
        </el-form-item>
        <el-form-item v-if="!isEdit" label="是否立即发布">
          <el-switch v-model="autoPublish" :active-value="1" :inactive-value="0" />
          <span class="form-tip">开启后会同步设置抢购时间和发布</span>
        </el-form-item>
        <template v-if="!isEdit && autoPublish === 1">
          <el-form-item label="抢购开始时间" prop="startTime">
            <el-date-picker
              v-model="ticketForm.startTime"
              type="datetime"
              placeholder="请选择开始时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="抢购结束时间" prop="endTime">
            <el-date-picker
              v-model="ticketForm.endTime"
              type="datetime"
              placeholder="请选择结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="每人限购">
            <el-input-number v-model="ticketForm.maxPerUser" :min="1" :max="10" style="width: 150px" />
            <span class="form-tip">张/人</span>
          </el-form-item>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSaveTicket">确认</el-button>
      </template>
    </el-dialog>

    <!-- 库存调整 -->
    <el-dialog v-model="adjustStockVisible" title="库存调整（应急）" width="450px">
      <el-form>
        <el-form-item label="票种"><span>{{ currentTicket?.typeName }}</span></el-form-item>
        <el-form-item label="当前库存"><span>{{ currentTicket?.availableStock }}</span></el-form-item>
        <el-form-item label="调整数量">
          <el-input-number v-model="adjustAmount" :min="1" :max="10000" />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-radio-group v-model="adjustOperation">
            <el-radio value="increase">增加库存</el-radio>
            <el-radio value="decrease">扣减库存</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="调整后库存">
          <span :class="{ low: getAdjustedStock() < 0 }">{{ getAdjustedStock() }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustStockVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAdjustStock" :disabled="getAdjustedStock() < 0">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 发布票（选择发布数量） -->
    <el-dialog v-model="publishDialogVisible" title="发布票" width="480px">
      <el-form label-width="120px">
        <el-form-item label="票种"><span class="ticket-name-badge">{{ currentTicket?.typeName }}</span></el-form-item>
        <el-form-item label="可用库存">
          <span class="stock-value">{{ currentTicket?.availableStock }}</span> 张
        </el-form-item>
        <el-form-item label="本次发布数量">
          <el-input-number
            v-model="publishStock"
            :min="1"
            :max="currentTicket?.availableStock || 1"
            style="width: 200px"
          />
          <span class="form-tip">剩余库存将保留，稍后可继续发布或通过"库存调整"追加</span>
        </el-form-item>
        <el-form-item label="发布后">
          <span>可用库存 = <strong>{{ publishStock }}</strong> 张</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishDialogVisible = false">取消</el-button>
        <el-button type="success" :loading="publishing" @click="confirmPublish">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 抢购时间配置 -->
    <el-dialog v-model="configDialogVisible" title="活动配置（抢购时间+限购）" width="540px" destroy-on-close>
      <el-form ref="configFormRef" :model="configForm" :rules="configRules" label-width="120px">
        <el-form-item label="票种"><span>{{ currentTicket?.typeName }}</span></el-form-item>
        <el-form-item label="抢购开始时间" prop="startTime">
          <el-date-picker
            v-model="configForm.startTime"
            type="datetime"
            placeholder="请选择开始时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
          <span class="form-tip" v-if="configForm._existingStartTime">当前配置：{{ configForm._existingStartTime }}</span>
        </el-form-item>
        <el-form-item label="抢购结束时间" prop="endTime">
          <el-date-picker
            v-model="configForm.endTime"
            type="datetime"
            placeholder="请选择结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
          <span class="form-tip" v-if="configForm._existingEndTime">当前配置：{{ configForm._existingEndTime }}</span>
        </el-form-item>
        <el-form-item label="每人限购" prop="maxPerUser">
          <el-input-number v-model="configForm.maxPerUser" :min="1" :max="10" style="width: 150px" />
          <span class="form-tip">张/人</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="configDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmConfig">保存</el-button>
      </template>
    </el-dialog>

    <!-- 修改限购对话框 -->
    <el-dialog v-model="limitDialogVisible" title="修改每人限购数量" width="420px" destroy-on-close>
      <el-form ref="limitFormRef" :model="limitForm" :rules="limitRules" label-width="120px">
        <el-form-item label="票种"><span>{{ currentTicket?.typeName }}</span></el-form-item>
        <el-form-item label="当前限购">
          <el-tag type="info">{{ getMaxPerUser(currentTicket?.id) }} 张/人</el-tag>
        </el-form-item>
        <el-form-item label="新限购数" prop="maxPerUser">
          <el-input-number v-model="limitForm.maxPerUser" :min="1" :max="10" style="width: 150px" />
          <span class="form-tip">张/人</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="limitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingLimit" @click="confirmLimit">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Clock } from '@element-plus/icons-vue'
import { adminApi } from '../api/index.js'

const productList = ref([])
const selectedProductId = ref(null)
const ticketTypes = ref([])
const configMap = ref({})

const showFormDialog = ref(false)
const configDialogVisible = ref(false)
const limitDialogVisible = ref(false)
const adjustStockVisible = ref(false)
const publishDialogVisible = ref(false)
const isEdit = ref(false)
const autoPublish = ref(1)
const saving = ref(false)
const publishing = ref(false)
const savingLimit = ref(false)

const ticketFormRef = ref(null)
const configFormRef = ref(null)
const limitFormRef = ref(null)

const currentTicket = ref(null)
const adjustAmount = ref(1)
const adjustOperation = ref('increase')
const publishStock = ref(1)

const ticketForm = reactive({
  id: null,
  typeName: '',
  seatArea: '',
  price: 0,
  channel: 'seckill',
  totalStock: 100,
  seckillStock: 100,
  startTime: '',
  endTime: '',
  maxPerUser: 3
})

const configForm = reactive({
  startTime: '',
  endTime: '',
  maxPerUser: 3,
  _existingStartTime: '',
  _existingEndTime: ''
})

const limitForm = reactive({
  maxPerUser: 3
})

// 自定义校验：抢购数量不能超过总库存
const validateSeckillStock = (rule, value, callback) => {
  if (value == null || value === '') {
    callback(new Error('请输入抢购数量'))
  } else if (Number(value) > Number(ticketForm.totalStock)) {
    callback(new Error('抢购数量不能超过总库存'))
  } else {
    callback()
  }
}

const ticketRules = {
  typeName: [{ required: true, message: '请输入票种名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入票价', trigger: 'blur' }],
  channel: [{ required: true, message: '请选择渠道', trigger: 'change' }],
  totalStock: [{ required: true, message: '请输入总库存', trigger: 'blur' }],
  seckillStock: [
    { required: true, message: '请输入抢购数量', trigger: 'blur' },
    { validator: validateSeckillStock, trigger: 'blur' }
  ],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }]
}

const configRules = {
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  maxPerUser: [{ required: true, message: '请输入限购数', trigger: 'blur' }]
}

const limitRules = {
  maxPerUser: [{ required: true, message: '请输入限购数', trigger: 'blur' }]
}

const toPickerTime = (time) => {
  if (!time) return ''
  const d = time instanceof Date ? time : new Date(time)
  if (isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const formatDateTime = (date) => {
  const full = toPickerTime(date)
  if (!full) return '-'
  return full.substring(0, 16)
}

onMounted(async () => {
  try {
    const result = await adminApi.getProducts()
    if (result.code === 200) {
      productList.value = result.data?.products || []
      if (productList.value.length > 0) {
        selectedProductId.value = productList.value[0].id
        loadTicketTypes()
      }
    }
  } catch (error) {
    ElMessage.error('加载演出列表失败')
  }
})

const loadTicketTypes = async () => {
  if (!selectedProductId.value) {
    ticketTypes.value = []
    return
  }
  try {
    const result = await adminApi.getProductDetail(selectedProductId.value)
    if (result.code === 200) {
      ticketTypes.value = result.data?.ticketTypes || []
      const cfgs = result.data?.configMap || {}
      configMap.value = {}
      for (const key of Object.keys(cfgs)) {
        configMap.value[cfgs[key].ticketTypeId] = cfgs[key]
      }
    }
  } catch (error) {
    ElMessage.error('加载票种失败')
  }
}

const getConfig = (ticketTypeId) => configMap.value[ticketTypeId]
const getMaxPerUser = (ticketTypeId) => configMap.value[ticketTypeId]?.maxPerUser || 3

const getChannelLabel = (c) => ({ seckill: '秒杀', special: '特价', regular: '常规' }[c] || c)
const getChannelClass = (c) => `channel-${c}`
const getStatusClass = (s) => (s === 1 ? 'status-on' : 'status-off')


const resetForm = () => {
  ticketForm.id = null
  ticketForm.typeName = ''
  ticketForm.seatArea = ''
  ticketForm.price = 0
  ticketForm.channel = 'seckill'
  ticketForm.totalStock = 100
  ticketForm.seckillStock = 100
  ticketForm.startTime = ''
  ticketForm.endTime = ''
  ticketForm.maxPerUser = 3
  autoPublish.value = 1
}

const openAddDialog = () => {
  resetForm()
  isEdit.value = false
  showFormDialog.value = true
}

const openEditDialog = (ticket) => {
  ticketForm.id = ticket.id
  ticketForm.typeName = ticket.typeName
  ticketForm.seatArea = ticket.seatArea || ''
  ticketForm.price = Number(ticket.price) || 0
  ticketForm.channel = ticket.channel
  isEdit.value = true
  showFormDialog.value = true
}

const handleSaveTicket = async () => {
  if (!ticketFormRef.value) return
  const valid = await ticketFormRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEdit.value) {
      const result = await adminApi.updateTicket(ticketForm.id, {
        typeName: ticketForm.typeName,
        seatArea: ticketForm.seatArea,
        price: ticketForm.price,
        channel: ticketForm.channel
      })
      if (result.code === 200) {
        ElMessage.success('修改成功')
        showFormDialog.value = false
        loadTicketTypes()
      } else {
        ElMessage.error(result.msg || '修改失败')
      }
    } else {
      const result = await adminApi.addTicket({
        productId: selectedProductId.value,
        typeName: ticketForm.typeName,
        seatArea: ticketForm.seatArea,
        price: ticketForm.price,
        channel: ticketForm.channel,
        totalStock: ticketForm.totalStock,
        seckillStock: ticketForm.seckillStock
      })
      if (result.code === 200) {
        const ticketId = result.data
        if (autoPublish.value === 1 && ticketForm.startTime && ticketForm.endTime) {
          const cfgRes = await adminApi.setConfig(ticketId, {
            startTime: ticketForm.startTime,
            endTime: ticketForm.endTime,
            maxPerUser: ticketForm.maxPerUser
          })
          if (cfgRes.code !== 200) {
            ElMessage.warning('票种已添加，但活动配置失败：' + (cfgRes.msg || ''))
          } else {
            const pubRes = await adminApi.publishTicket(ticketId)
            if (pubRes.code !== 200) {
              ElMessage.warning('票种已添加并配置抢购时间，但发布失败：' + (pubRes.msg || ''))
            } else {
              ElMessage.success('新增并发布成功')
            }
          }
        } else {
          ElMessage.success('新增成功（未发布）')
        }
        showFormDialog.value = false
        loadTicketTypes()
      } else {
        ElMessage.error(result.msg || '新增失败')
      }
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

const handlePublish = async (ticket) => {
  if (!getConfig(ticket.id)) {
    try {
      await ElMessageBox.confirm('该票种尚未配置抢购时间，是否使用默认值发布？', '提示', {
        type: 'warning',
        confirmButtonText: '使用默认时间',
        cancelButtonText: '取消'
      })
    } catch {
      ElMessage.info('请先配置抢购时间')
      return
    }
  }
  try {
    const result = await adminApi.publishTicket(ticket.id)
    if (result.code === 200) {
      ElMessage.success('发布成功')
      loadTicketTypes()
    } else {
      ElMessage.error(result.msg || '发布失败')
    }
  } catch (error) {
    ElMessage.error('发布失败')
  }
}

const openPublishDialog = (ticket) => {
  currentTicket.value = ticket
  publishStock.value = ticket.availableStock || 1  // 默认发布全部
  publishDialogVisible.value = true
}

const confirmPublish = async () => {
  if (!currentTicket.value) return
  publishing.value = true
  try {
    const stockToPublish = publishStock.value
    const result = await adminApi.publishTicketWithStock(currentTicket.value.id, stockToPublish)
    if (result.code === 200) {
      ElMessage.success(result.data || '发布成功')
      publishDialogVisible.value = false
      loadTicketTypes()
    } else {
      ElMessage.error(result.msg || '发布失败')
    }
  } catch (error) {
    ElMessage.error('发布失败')
  } finally {
    publishing.value = false
  }
}

const handleUnpublish = async (ticket) => {
  try {
    const result = await adminApi.unpublishTicket(ticket.id)
    if (result.code === 200) {
      ElMessage.success('已下架')
      loadTicketTypes()
    } else {
      ElMessage.error(result.msg || '下架失败')
    }
  } catch (error) {
    ElMessage.error('下架失败')
  }
}

const handleDelete = async (ticket) => {
  try {
    await ElMessageBox.confirm(`确定删除票种【${ticket.typeName}】吗？此操作不可恢复！`, '警告', {
      type: 'error',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    const result = await adminApi.deleteTicket(ticket.id)
    if (result.code === 200) {
      ElMessage.success('删除成功')
      loadTicketTypes()
    } else {
      ElMessage.error(result.msg || '删除失败')
    }
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const openAdjustStock = (ticket) => {
  currentTicket.value = ticket
  adjustAmount.value = 1
  adjustOperation.value = 'increase'
  adjustStockVisible.value = true
}

const getAdjustedStock = () => {
  if (!currentTicket.value) return 0
  const current = currentTicket.value.availableStock || 0
  if (adjustOperation.value === 'increase') {
    return current + (adjustAmount.value || 0)
  }
  return current - (adjustAmount.value || 0)
}

const confirmAdjustStock = async () => {
  if (!currentTicket.value) return
  try {
    const result = await adminApi.adjustStock(
      currentTicket.value.id,
      adjustAmount.value,
      adjustOperation.value
    )
    if (result.code === 200) {
      ElMessage.success('调整成功')
      adjustStockVisible.value = false
      loadTicketTypes()
    } else {
      ElMessage.error(result.msg || '调整失败')
    }
  } catch (error) {
    ElMessage.error('调整失败')
  }
}

const openConfigDialog = (ticket) => {
  currentTicket.value = ticket
  const cfg = getConfig(ticket.id)
  // 用 toPickerTime 生成 el-date-picker 需要的精确格式
  configForm.startTime = cfg ? toPickerTime(cfg.startTime) : ''
  configForm.endTime = cfg ? toPickerTime(cfg.endTime) : ''
  configForm.maxPerUser = cfg?.maxPerUser || 3
  // 同时在下方显示"当前配置"作为参考
  configForm._existingStartTime = cfg ? formatDateTime(cfg.startTime) : '未配置'
  configForm._existingEndTime = cfg ? formatDateTime(cfg.endTime) : '未配置'
  configDialogVisible.value = true
}

const openLimitDialog = (ticket) => {
  currentTicket.value = ticket
  limitForm.maxPerUser = getMaxPerUser(ticket.id)
  limitDialogVisible.value = true
}

const confirmLimit = async () => {
  if (!limitFormRef.value || !currentTicket.value) return
  const valid = await limitFormRef.value.validate().catch(() => false)
  if (!valid) return
  savingLimit.value = true
  try {
    const result = await adminApi.setMaxPerUser(currentTicket.value.id, limitForm.maxPerUser)
    if (result.code === 200) {
      ElMessage.success('限购数量已更新')
      limitDialogVisible.value = false
      loadTicketTypes()
    } else {
      ElMessage.error(result.msg || '修改失败')
    }
  } catch (error) {
    ElMessage.error('修改失败')
  } finally {
    savingLimit.value = false
  }
}

const confirmConfig = async () => {
  if (!configFormRef.value || !currentTicket.value) return
  const valid = await configFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    const result = await adminApi.setConfig(currentTicket.value.id, {
      startTime: configForm.startTime,
      endTime: configForm.endTime,
      maxPerUser: configForm.maxPerUser
    })
    if (result.code === 200) {
      ElMessage.success('抢购时间已更新')
      configDialogVisible.value = false
      loadTicketTypes()
    } else {
      ElMessage.error(result.msg || '配置失败')
    }
  } catch (error) {
    ElMessage.error('配置失败')
  }
}
</script>

<style scoped>
.admin-ticket-container {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 10px;
}
.header-left h2 {
  margin: 0 0 5px 0;
  font-size: 22px;
}
.header-subtitle {
  margin: 0;
  font-size: 13px;
  color: #909399;
}
.header-right {
  display: flex;
  align-items: center;
}
.empty-state {
  text-align: center;
  padding: 80px 0;
  color: #909399;
  background: #fff;
  border-radius: 8px;
}
.ticket-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.ticket-card {
  background: #fff;
  padding: 18px 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.ticket-head {
  margin-bottom: 12px;
}
.ticket-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}
.ticket-name {
  font-size: 16px;
  font-weight: 600;
}
.channel-tag {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}
.channel-seckill {
  color: #f56c6c;
  background: #fef0f0;
}
.channel-special {
  color: #e6a23c;
  background: #fdf6ec;
}
.channel-regular {
  color: #67c23a;
  background: #f0f9eb;
}
.ticket-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}
.status-on {
  color: #67c23a;
  background: #f0f9eb;
}
.status-off {
  color: #909399;
  background: #f4f4f5;
}
.ticket-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: #606266;
}
.ticket-price {
  color: #f56c6c;
  font-weight: 600;
}
.ticket-stats {
  display: flex;
  gap: 30px;
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f8f9fb;
  border-radius: 6px;
}
.stat {
  display: flex;
  flex-direction: column;
}
.stat-label {
  font-size: 12px;
  color: #909399;
}
.stat-value {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.stat-value.low {
  color: #e6a23c;
}
.stat-value.sold {
  color: #909399;
}
.ticket-schedule {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 10px;
  padding: 6px 12px;
  background: #ecf5ff;
  border-radius: 4px;
}
.ticket-actions {
  display: flex;
  gap: 5px;
  border-top: 1px solid #f0f0f0;
  padding-top: 10px;
}
.ticket-name-badge {
  font-weight: 600;
  color: #409eff;
}
.stock-value {
  font-size: 16px;
  font-weight: 700;
  color: #67c23a;
}
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
