<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRepositoryStore } from '@/stores/repository'

const route = useRoute()
const router = useRouter()
const repoStore = useRepositoryStore()

const loading = ref(true)
const activeTab = ref('overview')

const repoId = computed(() => Number(route.params.id))

onMounted(async () => {
  try {
    await repoStore.fetchRepoDetail(repoId.value)
  } finally {
    loading.value = false
  }
})

function goToTimeline() {
  router.push(`/repo/${repoId.value}/timeline`)
}

function goBack() {
  router.push('/')
}

function handleTabChange(tab: string) {
  activeTab.value = tab
  if (tab === 'overview') {
    router.push(`/repo/${repoId.value}`)
  } else {
    router.push(`/repo/${repoId.value}/${tab}`)
  }
}
</script>

<template>
  <div class="repository-page" v-loading="loading">
    <!-- 导航栏 -->
    <header class="header">
      <div class="header-content">
        <div class="header-left">
          <el-button text @click="goBack">
            <el-icon><ArrowLeft /></el-icon>
            返回
          </el-button>
          <div class="repo-title" v-if="repoStore.currentRepo">
            <el-icon class="repo-icon"><Folder /></el-icon>
            <span>{{ repoStore.currentRepo.name }}</span>
          </div>
        </div>
        <div class="header-right">
          <el-button type="primary" @click="goToTimeline">
            <el-icon><VideoPlay /></el-icon>
            进入时光机
          </el-button>
        </div>
      </div>
    </header>

    <main class="main-content" v-if="repoStore.currentRepo">
      <!-- 仓库信息卡片 -->
      <div class="repo-info-card">
        <div class="repo-header">
          <div class="repo-main">
            <h1>{{ repoStore.currentRepo.name }}</h1>
            <p class="repo-desc">{{ repoStore.currentRepo.description || '暂无描述' }}</p>
            <div class="repo-url">
              <el-icon><Link /></el-icon>
              <a :href="repoStore.currentRepo.url" target="_blank">
                {{ repoStore.currentRepo.url }}
              </a>
            </div>
          </div>
          <div class="repo-stats">
            <div class="stat-item">
              <span class="stat-value">{{ repoStore.currentRepo.totalCommits }}</span>
              <span class="stat-label">Commits</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ repoStore.totalAuthors }}</span>
              <span class="stat-label">贡献者</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ repoStore.currentRepo.totalFiles }}</span>
              <span class="stat-label">文件</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 标签页 -->
      <div class="tabs-section">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="概览" name="overview">
            <template #label>
              <span class="tab-label"><el-icon><House /></el-icon> 概览</span>
            </template>
          </el-tab-pane>
          <el-tab-pane label="时间轴" name="timeline">
            <template #label>
              <span class="tab-label"><el-icon><Clock /></el-icon> 时间轴</span>
            </template>
          </el-tab-pane>
          <el-tab-pane label="提交" name="commits">
            <template #label>
              <span class="tab-label"><el-icon><List /></el-icon> 提交</span>
            </template>
          </el-tab-pane>
          <el-tab-pane label="统计" name="stats">
            <template #label>
              <span class="tab-label"><el-icon><TrendCharts /></el-icon> 统计</span>
            </template>
          </el-tab-pane>
        </el-tabs>

        <!-- 子路由内容 -->
        <div class="tab-content">
          <router-view />
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.repository-page {
  min-height: 100vh;
  padding-top: 64px;
}

.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 100;
  background: var(--bg-card);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid var(--border-color);
}

.header-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 var(--spacing-xl);
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.repo-title {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-weight: 600;
  font-size: 1.1rem;
}

.repo-icon {
  color: var(--color-warning);
}

.main-content {
  max-width: 1400px;
  margin: 0 auto;
  padding: var(--spacing-xl);
}

.repo-info-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: var(--spacing-xl);
  margin-bottom: var(--spacing-xl);
}

.repo-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-xl);
}

.repo-main h1 {
  font-size: 1.75rem;
  margin-bottom: var(--spacing-sm);
}

.repo-desc {
  color: var(--text-secondary);
  margin-bottom: var(--spacing-md);
  max-width: 600px;
}

.repo-url {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 0.9rem;
  color: var(--text-muted);
}

.repo-url a {
  color: var(--color-accent);
}

.repo-stats {
  display: flex;
  gap: var(--spacing-xl);
}

.stat-item {
  text-align: center;
}

.stat-value {
  display: block;
  font-size: 2rem;
  font-weight: 700;
  color: var(--color-primary);
  font-family: var(--font-mono);
}

.stat-label {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.tabs-section {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.tab-label {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
}

.tab-content {
  padding: var(--spacing-xl);
}

:deep(.el-tabs__header) {
  margin: 0;
  padding: 0 var(--spacing-lg);
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}

:deep(.el-tabs__nav-wrap::after) {
  display: none;
}

:deep(.el-tabs__item) {
  height: 50px;
  line-height: 50px;
}

@media (max-width: 768px) {
  .repo-header {
    flex-direction: column;
  }
  
  .repo-stats {
    width: 100%;
    justify-content: space-around;
  }
}
</style>
