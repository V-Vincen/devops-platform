<script setup lang="ts">
const cards = [
  { label: '项目数量', value: '—', note: '项目管理模块已接入' },
  { label: '运行环境', value: 'dev / test', note: 'Kubernetes 环境待接入' },
  { label: '构建状态', value: '待接入', note: 'Jenkins Pipeline' },
  { label: '基础设施', value: 'Nexus · Nacos · Redis', note: '按环境配置启用' },
]
</script>

<template>
  <section class="page-section">
    <div class="page-heading compact-page-heading">
      <div>
        <p class="eyebrow">OVERVIEW</p>
        <div class="page-title-row">
          <h1>工作台</h1>
          <span class="page-title-divider" />
          <p class="page-description">集中查看项目、环境和交付基础设施的准备情况。</p>
        </div>
      </div>
    </div>

    <div class="stat-grid">
      <el-card v-for="card in cards" :key="card.label" shadow="never" class="stat-card">
        <div class="stat-label">{{ card.label }}</div>
        <div class="stat-value">{{ card.value }}</div>
        <div class="stat-note">{{ card.note }}</div>
      </el-card>
    </div>

    <el-card shadow="never" class="welcome-card">
      <div class="welcome-content">
        <div>
          <p class="eyebrow">NEXT STEP</p>
          <h2>先创建一个项目，开始接入第一条流水线</h2>
          <p>项目管理接口已经具备基础分页和创建能力，后续将继续接入 Gitee、Jenkins 和 Kubernetes。</p>
        </div>
        <el-button type="primary" @click="$router.push('/projects')">进入项目管理</el-button>
      </div>
    </el-card>
  </section>
</template>
