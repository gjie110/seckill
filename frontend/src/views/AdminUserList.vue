<template>
  <div class="admin-user-container">
    <div class="page-header">
      <div class="header-left">
        <h2>👥 用户管理</h2>
        <p class="header-subtitle">管理用户账号，支持新增、编辑、启用/禁用、修改角色、删除</p>
      </div>
      <div class="header-right">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名/手机号"
          clearable
          style="width: 200px; margin-right: 10px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon>
          新增用户
        </el-button>
      </div>
    </div>

    <div class="user-table" v-loading="loading">
      <el-table :data="filteredList" stripe>
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="username" label="用户名" min-width="140">
          <template #default="{ row }">
            <span class="username-cell">{{ row.username }}</span>
            <el-tag v-if="row.role === 'admin'" type="warning" size="small" class="ml-8">管理员</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="150">
          <template #default="{ row }">
            {{ row.phone || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180">
          <template #default="{ row }">
            {{ row.email || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="(row.status ?? 0) === 0 ? 'success' : 'danger'" size="small">
              {{ (row.status ?? 0) === 0 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="注册时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginTime" label="最后登录" width="170">
          <template #default="{ row }">
            {{ row.lastLoginTime ? formatDateTime(row.lastLoginTime) : '从未登录' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button size="small" link type="warning" @click="openRoleDialog(row)">角色</el-button>
            <el-button
              v-if="(row.status ?? 0) === 0"
              size="small"
              link
              type="info"
              @click="handleToggleStatus(row)"
            >
              禁用
            </el-button>
            <el-button
              v-else
              size="small"
              link
              type="success"
              @click="handleToggleStatus(row)"
            >
              启用
            </el-button>
            <el-button size="small" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="filteredList.length === 0 && !loading" class="empty-tip">
        暂无用户数据
      </div>

      <div v-if="total > 0" class="pagination-bar">
        <span>共 {{ total }} 条</span>
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="total"
          layout="prev, pager, next"
          @current-change="loadUsers"
        />
      </div>
    </div>

    <!-- 新增用户对话框 -->
    <el-dialog v-model="addDialogVisible" title="新增用户" width="520px" destroy-on-close>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="addForm.username" placeholder="登录用户名" maxlength="50" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="addForm.password" type="password" placeholder="登录密码" show-password />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="addForm.phone" placeholder="选填" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="addForm.email" placeholder="选填" maxlength="100" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-radio-group v-model="addForm.role">
            <el-radio value="user">普通用户</el-radio>
            <el-radio value="admin">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmAdd">确认注册</el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户对话框 -->
    <el-dialog v-model="editDialogVisible" title="编辑用户信息" width="520px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
        <el-form-item label="用户ID">
          <el-tag>{{ currentUser?.userId }}</el-tag>
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="editForm.username" placeholder="登录用户名" maxlength="50" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="editForm.password" type="password" placeholder="不修改请留空" show-password />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="editForm.phone" placeholder="选填" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" placeholder="选填" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="confirmEdit">保存修改</el-button>
      </template>
    </el-dialog>

    <!-- 修改角色对话框 -->
    <el-dialog v-model="roleDialogVisible" title="修改用户角色" width="420px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <strong>{{ currentUser?.username }}</strong>
        </el-form-item>
        <el-form-item label="当前角色">
          <el-tag :type="currentUser?.role === 'admin' ? 'warning' : ''">
            {{ currentUser?.role === 'admin' ? '管理员' : '普通用户' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="新角色">
          <el-radio-group v-model="newRole">
            <el-radio value="user">普通用户</el-radio>
            <el-radio value="admin">管理员</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmRole">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { adminApi } from '../api/index.js'

const loading = ref(false)
const saving = ref(false)
const userList = ref([])
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const addDialogVisible = ref(false)
const editDialogVisible = ref(false)
const roleDialogVisible = ref(false)

const currentUser = ref(null)
const newRole = ref('user')

const addFormRef = ref(null)
const editFormRef = ref(null)

const addForm = reactive({
  username: '',
  password: '',
  phone: '',
  email: '',
  role: 'user'
})

const editForm = reactive({
  username: '',
  password: '',
  phone: '',
  email: ''
})

const addRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 50, message: '用户名长度 2-50 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const editRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 50, message: '用户名长度 2-50 个字符', trigger: 'blur' }
  ]
}

// 后端已支持搜索，直接用userList
const filteredList = computed(() => userList.value)

onMounted(() => {
  loadUsers()
})

const handleSearch = () => {
  currentPage.value = 1
  loadUsers()
}

const loadUsers = async () => {
  loading.value = true
  try {
    const result = await adminApi.getUsers(keyword.value, currentPage.value, pageSize.value)
    if (result.code === 200 && result.data) {
      userList.value = result.data.users || []
      total.value = result.data.total || 0
    } else {
      ElMessage.error(result.msg || '加载失败')
    }
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const formatDateTime = (date) => {
  if (!date) return '-'
  const d = new Date(date)
  if (isNaN(d.getTime())) return date
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const openAddDialog = () => {
  addForm.username = ''
  addForm.password = ''
  addForm.phone = ''
  addForm.email = ''
  addForm.role = 'user'
  addDialogVisible.value = true
}

const confirmAdd = async () => {
  if (!addFormRef.value) return
  const valid = await addFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const result = await adminApi.addUser({
      username: addForm.username,
      password: addForm.password,
      phone: addForm.phone || undefined,
      email: addForm.email || undefined,
      role: addForm.role
    })
    if (result.code === 200) {
      ElMessage.success(result.data || '注册成功')
      addDialogVisible.value = false
      loadUsers()
    } else {
      ElMessage.error(result.msg || '注册失败')
    }
  } catch (error) {
    ElMessage.error('注册失败')
  } finally {
    saving.value = false
  }
}

const openEditDialog = (user) => {
  currentUser.value = user
  editForm.username = user.username || ''
  editForm.password = ''
  editForm.phone = user.phone || ''
  editForm.email = user.email || ''
  editDialogVisible.value = true
}

const confirmEdit = async () => {
  if (!editFormRef.value || !currentUser.value) return
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const result = await adminApi.editUser(currentUser.value.userId, {
      username: editForm.username,
      phone: editForm.phone || undefined,
      email: editForm.email || undefined,
      password: editForm.password || undefined
    })
    if (result.code === 200) {
      ElMessage.success('保存成功')
      editDialogVisible.value = false
      loadUsers()
    } else {
      ElMessage.error(result.msg || '保存失败')
    }
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const openRoleDialog = (user) => {
  currentUser.value = user
  newRole.value = user.role || 'user'
  roleDialogVisible.value = true
}

const confirmRole = async () => {
  if (!currentUser.value) return
  if (currentUser.value.role === newRole.value) {
    ElMessage.info('角色未变更')
    roleDialogVisible.value = false
    return
  }
  try {
    const result = await adminApi.setUserRole(currentUser.value.userId, newRole.value)
    if (result.code === 200) {
      ElMessage.success(result.data || '角色已更新')
      roleDialogVisible.value = false
      loadUsers()
    } else {
      ElMessage.error(result.msg || '修改失败')
    }
  } catch (error) {
    ElMessage.error('修改失败')
  }
}

const handleToggleStatus = async (user) => {
  const currentStatus = user.status ?? 0
  const newStatus = currentStatus === 0 ? 1 : 0
  const action = newStatus === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}用户【${user.username}】吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    const result = await adminApi.setUserStatus(user.userId, newStatus)
    if (result.code === 200) {
      ElMessage.success(result.data || `${action}成功`)
      loadUsers()
    } else {
      ElMessage.error(result.msg || `${action}失败`)
    }
  } catch (error) {
    ElMessage.error(`${action}失败`)
  }
}

const handleDelete = async (user) => {
  try {
    await ElMessageBox.confirm(`确定删除用户【${user.username}】吗？此操作不可恢复！`, '警告', {
      type: 'error',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    const result = await adminApi.deleteUser(user.userId)
    if (result.code === 200) {
      ElMessage.success('用户已删除')
      loadUsers()
    } else {
      ElMessage.error(result.msg || '删除失败')
    }
  } catch (error) {
    ElMessage.error('删除失败')
  }
}
</script>

<style scoped>
.admin-user-container {
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
  gap: 10px;
}
.user-table {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 5px rgba(0,0,0,0.05);
}
.username-cell {
  font-weight: 600;
}
.ml-8 {
  margin-left: 8px;
}
.empty-tip {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}

.pagination-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding: 15px 0;
  border-top: 1px solid #ebeef5;
}
</style>
