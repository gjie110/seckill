<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h1>🎫 演唱会门票秒杀系统</h1>
        <p>高并发场景下的票务抢购平台</p>
      </div>

      <el-form ref="loginFormRef" :model="loginForm" :rules="rules" class="login-form">
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名 / 手机号 / 邮箱"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-button"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-tips">
        <p>测试账号：</p>
        <p>管理员：admin / admin123</p>
        <p>普通用户：user001 / user123</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '../api/index.js'

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)

// 登录表单数据
const loginForm = reactive({
  username: '',
  password: ''
})

// 表单验证规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

// 处理登录
const handleLogin = async () => {
  if (!loginFormRef.value) return

  // 表单验证
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true

  try {
    // 调用后端登录接口 POST /auth/login
    const result = await authApi.login(loginForm.username, loginForm.password)
    console.log('登录响应:', result)

    // 判断登录是否成功：code === 200 表示成功
    if (result && result.code === 200 && result.data) {
      const userData = result.data

      // 保存用户信息到 localStorage
      localStorage.setItem('userId', userData.userId)
      localStorage.setItem('username', userData.username)
      localStorage.setItem('role', userData.role)
      localStorage.setItem('token', userData.token)
      localStorage.setItem('isLoggedIn', 'true')

      ElMessage.success(result.msg || '登录成功！')

      // 根据角色跳转不同页面
      // admin 角色 -> 管理员页面
      // user 角色 -> 普通用户首页（演出列表）
      setTimeout(() => {
        if (userData.role === 'admin') {
          router.push('/admin/product')
        } else {
          router.push('/')
        }
      }, 500)
    } else {
      // 登录失败：显示后端返回的错误信息
      ElMessage.error(result?.msg || '登录失败，请检查用户名和密码')
    }
  } catch (error) {
    console.error('登录请求异常:', error)
    ElMessage.error('网络异常，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  justify-content: center;
  align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-box {
  width: 420px;
  padding: 40px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h1 {
  font-size: 22px;
  color: #333;
  margin-bottom: 10px;
}

.login-header p {
  font-size: 14px;
  color: #999;
}

.login-form {
  margin-top: 20px;
}

.login-button {
  width: 100%;
}

.login-tips {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: #999;
  line-height: 1.8;
}
</style>
