<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const formRef = ref()

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value.validate()
  try {
    await authStore.signIn(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/dashboard'
    await router.replace(redirect)
  } catch {
    // 请求错误已经由统一 Axios 拦截器提示，这里只负责保持登录页状态。
  }
}
</script>

<template>
  <main class="login-page">
    <el-card shadow="never" class="login-card">
      <div class="login-brand">
        <div class="brand-mark">D</div>
        <div>
          <div class="brand-title">DevOps</div>
          <div class="brand-subtitle">Test Platform</div>
        </div>
      </div>
      <p class="eyebrow">SIGN IN</p>
      <h1>登录平台</h1>
      <p class="page-description">请输入平台账号，进入项目和测试环境管理工作台。</p>

      <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" size="large" placeholder="用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            placeholder="密码"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="login-button" :loading="authStore.loading" @click="submit">
          登录
        </el-button>
      </el-form>

      <p class="login-hint">首次启动请通过 DEVOPS_BOOTSTRAP_ADMIN_PASSWORD 初始化管理员。</p>
    </el-card>
  </main>
</template>
