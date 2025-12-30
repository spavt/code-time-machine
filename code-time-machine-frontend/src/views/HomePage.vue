<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRepositoryStore } from '@/stores/repository'
import { useTheme } from '@/composables/useTheme'
import { repositoryApi } from '@/api'
import type { Repository } from '@/types'
import { Clock, VideoPlay, Monitor, Timer, DataLine, Folder, Setting, Delete } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'

const router = useRouter()
const repoStore = useRepositoryStore()
const { isDarkMode, toggleTheme } = useTheme()

const recentRepos = ref<Repository[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    await repoStore.fetchRepositories()
    recentRepos.value = repoStore.repositories.slice(0, 6)
  } finally {
    loading.value = false
  }
})

function goToAnalyze() {
  router.push('/analyze')
}

function goToRepo(id: number) {
  router.push(`/repo/${id}`)
}

async function deleteRepo(repo: Repository, event: Event) {
  event.stopPropagation() // 阻止卡片点击事件
  
  try {
    await ElMessageBox.confirm(
      `确定要删除仓库 "${repo.name}" 吗？删除后数据将无法恢复。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    
    await repositoryApi.delete(repo.id)
    ElMessage.success('仓库已删除')
    
    // 刷新列表
    await repoStore.fetchRepositories()
    recentRepos.value = repoStore.repositories.slice(0, 6)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}
</script>

<template>
  <div class="home-page">
    <!-- Navbar -->
    <nav class="navbar glass-panel">
      <div class="nav-content">
        <div class="brand">
          <div class="logo-box">
            <el-icon><Clock /></el-icon>
          </div>
          <span class="brand-text">代码时光机</span>
        </div>
        
        <div class="nav-links">
          <router-link to="/" class="nav-item active">首页</router-link>
          <router-link to="/analyze" class="nav-item">分析</router-link>
          <a href="#" class="nav-item">文档</a>
        </div>
        
        <div class="nav-actions">
           <el-button circle class="icon-btn" @click="toggleTheme" :title="isDarkMode ? '切换到浅色模式' : '切换到深色模式'">
              <el-icon v-if="isDarkMode">☀️</el-icon>
              <el-icon v-else>🌙</el-icon>
           </el-button>
           <el-button type="primary" round @click="goToAnalyze">
              开始分析
           </el-button>
        </div>
      </div>
    </nav>

    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-content">
        <div class="badge-pill">
          <span class="dot"></span>
          <span>AI 驱动的代码演进分析</span>
        </div>
        
        <h1 class="hero-title">
          可视化你的代码 <br/>
          <span class="text-gradient">穿越时光之旅</span>
        </h1>
        
        <p class="hero-subtitle">
          以前所未有的方式体验代码库的演进。
          识别模式、调试历史，理解每次变更背后的「为什么」。
        </p>
        
        <div class="hero-cta">
          <el-button type="primary" size="large" class="cta-btn" @click="goToAnalyze">
            <el-icon class="mr-2"><VideoPlay /></el-icon> 开始探索
          </el-button>
          <el-button class="glass-btn" size="large">
            观看演示
          </el-button>
        </div>
      </div>
    </section>

    <!-- Features Grid -->
    <section class="features-section">
      <div class="feature-grid">
        <div class="feature-box glass-panel">
          <div class="icon-sport">
            <el-icon><Timer /></el-icon>
          </div>
          <h3>时间轴回放</h3>
          <p>通过电影级的回放引擎，实时观看代码的验证和变更过程。</p>
        </div>
        
        <div class="feature-box glass-panel">
          <div class="icon-sport purple">
            <el-icon><DataLine /></el-icon>
          </div>
          <h3>深度洞察</h3>
          <p>基于提交历史的热力图和分析，即时发现代码瓶颈。</p>
        </div>
        
        <div class="feature-box glass-panel">
          <div class="icon-sport blue">
            <el-icon><Monitor /></el-icon>
          </div>
          <h3>AI 解读</h3>
          <p>询问任何历史版本的问题，获得上下文感知的智能回答。</p>
        </div>
      </div>
    </section>

    <!-- Projects Section -->
    <section class="projects-section" v-if="recentRepos.length > 0">
      <div class="section-head">
        <h2>最近项目</h2>
        <router-link to="/analyze" class="link-animate">查看全部</router-link>
      </div>
      
      <div class="projects-grid" v-if="!loading">
        <div 
          v-for="repo in recentRepos" 
          :key="repo.id" 
          class="project-card glass-panel hover-effect"
          @click="goToRepo(repo.id)"
        >
          <div class="card-top">
            <div class="repo-icon">
              <el-icon><Folder /></el-icon>
            </div>
            <div class="card-actions">
              <el-button 
                type="danger" 
                circle 
                size="small" 
                class="delete-btn"
                @click="deleteRepo(repo, $event)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
          
          <h3>{{ repo.name }}</h3>
          <p class="desc">{{ repo.description || '暂无描述' }}</p>
          
          <div class="card-footer">
            <span class="meta-tag">{{ repo.totalCommits }} 次提交</span>
            <span class="meta-tag">最近更新</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.home-page {
  padding-top: 80px;
  max-width: 1400px;
  margin: 0 auto;
  padding-bottom: 100px;
}

/* Navbar */
.navbar {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  width: 90%;
  max-width: 1200px;
  height: 64px;
  border-radius: 100px;
  z-index: 100;
  padding: 0 8px;
}

.nav-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  padding: 0 16px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-box {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-purple));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
}

.brand-text {
  font-weight: 700;
  font-size: 1.1rem;
  letter-spacing: -0.02em;
}

.nav-links {
  display: flex;
  gap: 32px;
}

.nav-item {
  color: var(--text-secondary);
  font-weight: 500;
  font-size: 0.95rem;
  transition: color 0.2s;
}

.nav-item:hover, .nav-item.active {
  color: var(--text-primary);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-btn {
  background: transparent;
  border: 1px solid var(--border-color);
  color: var(--text-secondary);
}

.icon-btn:hover {
  background: var(--bg-glass-hover);
  color: var(--text-primary);
}

/* Hero */
.hero-section {
  text-align: center;
  padding: 100px 20px 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.badge-pill {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  background: var(--color-primary-glow);
  border: 1px solid var(--border-highlight);
  border-radius: 100px;
  color: var(--color-primary);
  font-size: 0.85rem;
  font-weight: 500;
  margin-bottom: 32px;
}

.dot {
  width: 6px;
  height: 6px;
  background: var(--color-primary);
  border-radius: 50%;
  box-shadow: 0 0 10px var(--color-primary);
}

.hero-title {
  font-size: 4.5rem;
  line-height: 1.1;
  font-weight: 800;
  margin-bottom: 24px;
  letter-spacing: -0.03em;
}

.hero-subtitle {
  font-size: 1.25rem;
  color: var(--text-secondary);
  max-width: 600px;
  margin-bottom: 48px;
  line-height: 1.6;
}

.hero-cta {
  display: flex;
  gap: 16px;
}

.cta-btn {
  height: 48px;
  padding: 0 32px;
  font-size: 1rem;
}

.glass-btn {
  background: var(--bg-glass);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  height: 48px;
  padding: 0 32px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s;
}

.glass-btn:hover {
  background: var(--bg-glass-hover);
}

/* Features */
.features-section {
  padding: 40px 20px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
}

.feature-box {
  padding: 32px;
  border-radius: var(--radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  transition: transform 0.3s ease;
}

.feature-box:hover {
  transform: translateY(-5px);
}

.icon-sport {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  background: var(--color-primary-glow);
  color: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 20px;
}

.icon-sport.purple {
  background: color-mix(in srgb, var(--color-purple) 15%, transparent);
  color: var(--color-purple);
}

.icon-sport.blue {
  background: color-mix(in srgb, var(--color-accent) 15%, transparent);
  color: var(--color-accent);
}

.feature-box h3 {
  margin-bottom: 12px;
  font-size: 1.2rem;
}

.feature-box p {
  color: var(--text-muted);
  font-size: 0.95rem;
}

/* Projects */
.projects-section {
  padding: 80px 20px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 24px;
}

.project-card {
  padding: 24px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}

.repo-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: var(--bg-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
  font-size: 24px;
  border: 1px solid var(--border-color);
}

.status-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-success);
  box-shadow: 0 0 8px var(--color-success);
}

.card-actions {
  opacity: 0;
  transition: opacity 0.2s;
}

.project-card:hover .card-actions {
  opacity: 1;
}

.delete-btn {
  box-shadow: 0 2px 8px rgba(239, 68, 68, 0.3);
}

.project-card h3 {
  font-size: 1.1rem;
  margin-bottom: 8px;
}

.desc {
  color: var(--text-muted);
  font-size: 0.9rem;
  margin-bottom: 24px;
  flex-grow: 1;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  display: flex;
  gap: 12px;
  margin-top: auto;
}

.meta-tag {
  font-size: 0.8rem;
  padding: 4px 10px;
  border-radius: 100px;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
}

.mr-2 {
  margin-right: 8px;
}

@media (max-width: 768px) {
  .hero-title { font-size: 3rem; }
  .feature-grid { grid-template-columns: 1fr; }
  .navbar { width: 95%; top: 10px; }
  .nav-links { display: none; }
}
</style>
