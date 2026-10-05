<script setup lang="ts">
import { computed } from 'vue'
import {
  DataBoard,
  FolderOpened,
  Monitor,
  Setting,
  Tickets,
  UserFilled,
} from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isAdministrator = computed(() => authStore.user?.role === 'ADMIN')

async function signOut() {
  await authStore.signOut()
  await router.replace('/login')
}
</script>

<template>
  <router-view v-if="route.name === 'login'" />

  <el-container v-else class="app-shell">
    <el-aside width="216px" class="app-sidebar">
      <div class="brand">
        <div class="brand-mark">D</div>
        <div>
          <div class="brand-title">DevOps</div>
          <div class="brand-subtitle">Test Platform</div>
        </div>
      </div>

      <el-menu router :default-active="route.path" :default-openeds="['project-management']" class="app-menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataBoard /></el-icon>
          <span>工作台</span>
        </el-menu-item>
        <el-sub-menu index="project-management">
          <template #title>
            <el-icon><FolderOpened /></el-icon>
            <span>项目管理</span>
          </template>
          <el-menu-item index="/projects">项目列表</el-menu-item>
          <el-menu-item index="/environments">环境管理</el-menu-item>
          <el-menu-item index="/microservices">微服务管理</el-menu-item>
        </el-sub-menu>
        <el-menu-item index="/pipelines" disabled>
          <el-icon><Tickets /></el-icon>
          <span>持续集成</span>
        </el-menu-item>
        <el-menu-item index="/deployments" disabled>
          <el-icon><Monitor /></el-icon>
          <span>持续部署</span>
        </el-menu-item>
        <el-sub-menu v-if="isAdministrator" index="system-settings">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统设置</span>
          </template>
          <el-menu-item index="/settings/users">
            <el-icon><UserFilled /></el-icon>
            用户与权限
          </el-menu-item>
        </el-sub-menu>
        <el-menu-item v-else index="/settings" disabled>
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header">
        <div class="header-context">
          <div class="header-title">DevOps 控制台</div>
          <span class="header-divider" />
          <div class="header-caption">内部测试环境</div>
        </div>
        <div class="header-user">
          <el-tag type="success" effect="light">开发环境</el-tag>
          <span>{{ authStore.user?.displayName || authStore.user?.username || '未登录' }}</span>
          <el-button link type="primary" @click="signOut">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
