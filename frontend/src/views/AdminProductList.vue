<template>
  <div class="admin-product-container">
    <div class="page-header">
      <div class="header-left">
        <h2>🎵 演出管理</h2>
        <p class="header-subtitle">发布演唱会信息，管理演出图片与轮播</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon>
          新增演出
        </el-button>
      </div>
    </div>

    <div class="filter-bar">
      <div class="filter-left">
        <span class="filter-label">状态：</span>
        <el-select v-model="statusFilter" class="filter-select" @change="loadProducts">
          <el-option label="全部" :value="null" />
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
        </el-select>
      </div>
      <div class="filter-right">
        <el-input
          v-model="searchKeyword"
          placeholder="输入演出名称"
          class="search-input"
          @keyup.enter="handleSearch"
          clearable
        />
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </div>
    </div>

    <div class="product-list">
      <div v-for="product in productList" :key="product.id" class="product-card">
        <div class="product-poster">
          <el-image
            v-if="product.posterUrl"
            :src="getImageUrl(product.posterUrl)"
            fit="cover"
            class="poster-img"
          />
          <div v-else class="poster-placeholder">无图</div>
        </div>
        <div class="product-main">
          <div class="product-info">
            <div class="product-name-row">
              <span class="product-name">{{ product.name }}</span>
              <el-tag v-if="product.isBanner === 1" type="warning" size="small">轮播</el-tag>
              <span class="product-status" :class="getStatusClass(product.status)">
                {{ product.status === 1 ? '已发布' : '草稿' }}
              </span>
            </div>
            <div class="product-meta">
              <span class="meta-item">📍 {{ product.venue }}</span>
              <span class="meta-item">⏰ {{ formatDateTime(product.showTime) }}</span>
            </div>
          </div>
          <div class="product-stats">
            <span class="stat-item">{{ getTicketCount(product.id) }} 个票档</span>
            <span class="stat-item">库存：{{ getTotalStock(product.id) }}</span>
            <span class="stat-item">销量：{{ getTotalSold(product.id) }}</span>
          </div>
          <div class="product-actions">
            <el-button type="primary" size="small" link @click="openEditDialog(product)">编辑</el-button>
            <el-button v-if="product.status === 0" type="success" size="small" link @click="handlePublish(product)">发布</el-button>
            <el-button v-if="product.status === 1" type="warning" size="small" link @click="handleUnpublish(product)">下架</el-button>
            <el-button type="info" size="small" link @click="handleViewDetail(product)">详情</el-button>
            <el-button v-if="product.status === 0" type="danger" size="small" link @click="handleDelete(product)">删除</el-button>
          </div>
        </div>
      </div>
      <div v-if="productList.length === 0" class="empty-tip">暂无演出数据</div>
    </div>

    <el-dialog v-model="showFormDialog" :title="isEdit ? '编辑演出' : '新增演出'" width="640px" destroy-on-close>
      <el-form ref="productFormRef" :model="productForm" :rules="productRules" label-width="100px">
        <el-form-item label="演出名称" prop="name">
          <el-input v-model="productForm.name" placeholder="请输入演出名称" />
        </el-form-item>
        <el-form-item label="演出描述">
          <el-input v-model="productForm.description" type="textarea" :rows="3" placeholder="请输入演出描述" />
        </el-form-item>
        <el-form-item label="演出场馆" prop="venue">
          <el-input v-model="productForm.venue" placeholder="请输入演出场馆" />
        </el-form-item>
        <el-form-item label="演出时间" prop="showTime">
          <el-date-picker
            v-model="productForm.showTime"
            type="datetime"
            placeholder="请选择演出时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="海报图片">
          <el-upload
            class="poster-uploader"
            :action="uploadUrl"
            :show-file-list="false"
            :headers="uploadHeaders"
            :before-upload="beforeUpload"
            :on-success="onUploadSuccess"
            :on-error="onUploadError"
            accept="image/*"
            name="file"
          >
            <img v-if="productForm.posterUrl" :src="getImageUrl(productForm.posterUrl)" class="uploaded-image" />
            <el-icon v-else class="uploader-icon"><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">点击上传海报图片（支持 jpg/png/webp，≤5MB）</div>
        </el-form-item>
        <el-form-item label="是否轮播">
          <el-switch
            v-model="isBannerFlag"
            active-text="加入首页轮播"
            inline-prompt
            :active-value="1"
            :inactive-value="0"
          />
          <span class="form-tip">开启后该演出会在用户端首页 Banner 中展示</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFormDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetailDialog" title="演出详情" width="700px">
      <div v-if="selectedProduct" class="detail-content">
        <div class="detail-row">
          <span class="detail-label">演出名称：</span>
          <span class="detail-value">{{ selectedProduct.name }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">演出描述：</span>
          <span class="detail-value">{{ selectedProduct.description || '-' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">演出场馆：</span>
          <span class="detail-value">{{ selectedProduct.venue }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">演出时间：</span>
          <span class="detail-value">{{ formatDateTime(selectedProduct.showTime) }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">当前状态：</span>
          <span :class="getStatusClass(selectedProduct.status)" class="detail-value">
            {{ selectedProduct.status === 1 ? '已发布' : '草稿' }}
          </span>
        </div>
        <div class="detail-row">
          <span class="detail-label">是否轮播：</span>
          <span class="detail-value">{{ selectedProduct.isBanner === 1 ? '是' : '否' }}</span>
        </div>
        <div class="detail-row">
          <span class="detail-label">海报图片：</span>
          <div class="detail-value">
            <el-image
              v-if="selectedProduct.posterUrl"
              :src="getImageUrl(selectedProduct.posterUrl)"
              fit="contain"
              style="max-width: 300px; max-height: 200px"
            />
            <span v-else>-</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { adminApi } from '../api/index.js'

const productList = ref([])
const searchKeyword = ref('')
const statusFilter = ref(null)
const total = ref(0)

const showFormDialog = ref(false)
const showDetailDialog = ref(false)
const isEdit = ref(false)
const productFormRef = ref(null)
const selectedProduct = ref(null)
const saving = ref(false)

const ticketStats = ref({})

const productForm = reactive({
  id: null,
  name: '',
  description: '',
  venue: '',
  showTime: '',
  posterUrl: ''
})
const isBannerFlag = ref(0)

const productRules = {
  name: [{ required: true, message: '请输入演出名称', trigger: 'blur' }],
  venue: [{ required: true, message: '请输入演出场馆', trigger: 'blur' }],
  showTime: [{ required: true, message: '请选择演出时间', trigger: 'change' }]
}

const API_BASE = '/api'
const uploadUrl = `${API_BASE}/file/upload`
const uploadHeaders = computed(() => ({
  'X-Requested-With': 'XMLHttpRequest'
}))

const getImageUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http')) return url
  if (url.startsWith('/uploads/')) return API_BASE.replace('/api', '') + url
  return url
}

const beforeUpload = (file) => {
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 5MB')
    return false
  }
  return true
}

const onUploadSuccess = (response) => {
  if (response.code === 200 && response.data) {
    productForm.posterUrl = response.data.url
    ElMessage.success('上传成功')
  } else {
    ElMessage.error(response.msg || '上传失败')
  }
}

const onUploadError = () => {
  ElMessage.error('上传失败，请检查后端服务')
}

onMounted(() => {
  loadProducts()
})

const loadProducts = async () => {
  try {
    const result = await adminApi.searchProducts(searchKeyword.value, statusFilter.value)
    if (result.code === 200) {
      productList.value = result.data || []
      total.value = productList.value.length
      loadTicketStats()
    }
  } catch (error) {
    console.error('加载演出列表失败:', error)
    ElMessage.error('加载失败')
  }
}

const loadTicketStats = async () => {
  ticketStats.value = {}
  for (const product of productList.value) {
    try {
      const result = await adminApi.getProductDetail(product.id)
      if (result.code === 200 && result.data) {
        const tickets = result.data.ticketTypes || []
        let totalStock = 0
        let totalSold = 0
        for (const t of tickets) {
          totalStock += t.availableStock || 0
          totalSold += t.soldStock || 0
        }
        ticketStats.value[product.id] = {
          count: tickets.length,
          stock: totalStock,
          sold: totalSold
        }
      }
    } catch (e) {
      ticketStats.value[product.id] = { count: 0, stock: 0, sold: 0 }
    }
  }
}

const getTicketCount = (productId) => ticketStats.value[productId]?.count || 0
const getTotalStock = (productId) => ticketStats.value[productId]?.stock || 0
const getTotalSold = (productId) => ticketStats.value[productId]?.sold || 0

const handleSearch = () => loadProducts()

const getStatusClass = (status) => (status === 1 ? 'status-published' : 'status-draft')

const formatDateTime = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  if (isNaN(d.getTime())) return date
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const resetForm = () => {
  productForm.id = null
  productForm.name = ''
  productForm.description = ''
  productForm.venue = ''
  productForm.showTime = ''
  productForm.posterUrl = ''
  isBannerFlag.value = 0
}

const openAddDialog = () => {
  resetForm()
  isEdit.value = false
  showFormDialog.value = true
}

const openEditDialog = (product) => {
  productForm.id = product.id
  productForm.name = product.name
  productForm.description = product.description || ''
  productForm.venue = product.venue
  productForm.showTime = product.showTime ? formatDateTimeForApi(product.showTime) : ''
  productForm.posterUrl = product.posterUrl || ''
  isBannerFlag.value = product.isBanner || 0
  isEdit.value = true
  showFormDialog.value = true
}

const handleViewDetail = (product) => {
  selectedProduct.value = product
  showDetailDialog.value = true
}

const handlePublish = async (product) => {
  try {
    const result = await adminApi.publishProduct(product.id)
    if (result.code === 200) {
      ElMessage.success('发布成功')
      loadProducts()
    } else {
      ElMessage.error(result.msg || '发布失败')
    }
  } catch (error) {
    ElMessage.error('发布失败')
  }
}

const handleUnpublish = async (product) => {
  try {
    const result = await adminApi.unpublishProduct(product.id)
    if (result.code === 200) {
      ElMessage.success('已下架')
      loadProducts()
    } else {
      ElMessage.error(result.msg || '下架失败')
    }
  } catch (error) {
    ElMessage.error('下架失败')
  }
}

const handleDelete = async (product) => {
  try {
    await ElMessageBox.confirm(`确定要删除演出【${product.name}】吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    const result = await adminApi.deleteProduct(product.id)
    if (result.code === 200) {
      ElMessage.success('删除成功')
      loadProducts()
    } else {
      ElMessage.error(result.msg || '删除失败')
    }
  } catch (error) {
    ElMessage.error('删除失败')
  }
}

const handleSave = async () => {
  if (!productFormRef.value) return
  const valid = await productFormRef.value.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const params = {
      name: productForm.name,
      description: productForm.description,
      venue: productForm.venue,
      showTime: productForm.showTime,
      posterUrl: productForm.posterUrl,
      isBanner: isBannerFlag.value
    }

    let result
    if (isEdit.value) {
      result = await adminApi.updateProduct(productForm.id, params)
    } else {
      result = await adminApi.addProduct(params)
    }

    if (result.code === 200) {
      ElMessage.success(isEdit.value ? '修改成功' : '新增成功')
      showFormDialog.value = false
      loadProducts()
    } else {
      ElMessage.error(result.msg || '操作失败')
    }
  } catch (error) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

const formatDateTimeForApi = (date) => {
  const d = new Date(date)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}
</script>

<style scoped>
.admin-product-container {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
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
  width: 130px;
}
.search-input {
  width: 250px;
  margin-right: 10px;
}
.product-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.product-card {
  display: flex;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  transition: box-shadow 0.2s;
}
.product-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}
.product-poster {
  width: 180px;
  height: 130px;
  flex-shrink: 0;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
}
.poster-img {
  width: 100%;
  height: 100%;
}
.poster-placeholder {
  color: #c0c4cc;
  font-size: 14px;
}
.product-main {
  flex: 1;
  padding: 15px 20px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}
.product-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.product-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.product-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
}
.status-published {
  color: #67c23a;
  background: #f0f9eb;
}
.status-draft {
  color: #909399;
  background: #f4f4f5;
}
.product-meta {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}
.product-stats {
  display: flex;
  gap: 20px;
  font-size: 13px;
  color: #909399;
  margin-bottom: 10px;
}
.product-actions {
  display: flex;
  gap: 5px;
}
.empty-tip {
  text-align: center;
  color: #909399;
  padding: 60px 0;
  background: #fff;
  border-radius: 8px;
}
.poster-uploader {
  display: inline-block;
}
.poster-uploader :deep(.el-upload) {
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  cursor: pointer;
  width: 180px;
  height: 130px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  position: relative;
}
.poster-uploader :deep(.el-upload:hover) {
  border-color: #409eff;
}
.uploader-icon {
  font-size: 28px;
  color: #8c939d;
}
.uploaded-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.upload-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
.detail-row {
  display: flex;
  margin-bottom: 12px;
}
.detail-label {
  width: 100px;
  color: #909399;
}
</style>
