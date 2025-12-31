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

    <!-- Product Preview (Linear-style embedded) -->
    <section class="product-preview-section">
      <div class="product-preview-container">
        <div class="product-preview-wrapper">
          <!-- 模拟的播放器界面 -->
          <div class="mock-player">
            <!-- 顶部控制栏 -->
            <div class="mock-header">
              <div class="mock-header-left">
                <div class="mock-btn mock-btn-back">←</div>
                <div class="mock-file-path">
                  <span class="mock-icon">📄</span>
                  <span>src/composables/useTimelinePlayer.ts</span>
                </div>
              </div>
              <div class="mock-header-right">
                <div class="mock-badge">TypeScript</div>
                <div class="mock-btn">⚙️</div>
              </div>
            </div>

            <!-- 主体区域 -->
            <div class="mock-body">
              <!-- 左侧时间线 -->
              <div class="mock-timeline">
                <div class="mock-timeline-header">时间轴</div>
                <div class="mock-timeline-list">
                  <div class="mock-commit" v-for="i in 6" :key="i" :class="{ active: i === 3 }">
                    <div class="mock-commit-dot"></div>
                    <div class="mock-commit-content">
                      <div class="mock-commit-msg">{{ ['feat: 添加虚拟滚动', 'fix: 修复切换bug', 'refactor: 优化性能', 'style: 调整样式', 'docs: 更新文档', 'test: 添加测试'][i-1] }}</div>
                      <div class="mock-commit-meta">{{ ['张三', '李四', '王五', '赵六', '钱七', '孙八'][i-1] }} · {{ i }}天前</div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 中间代码区 -->
              <div class="mock-code-area">
                <div class="mock-code-header">
                  <span>代码变更</span>
                  <div class="mock-stats">
                    <span class="mock-stat-add">+42</span>
                    <span class="mock-stat-del">-18</span>
                  </div>
                </div>
                <div class="mock-code-viewer">
                  <div class="mock-code-line" v-for="i in 12" :key="i" :class="{ 
                    'line-added': [3, 4, 5, 8, 9].includes(i),
                    'line-deleted': [6].includes(i)
                  }">
                    <span class="mock-line-num">{{ i + 45 }}</span>
                    <span class="mock-line-content">
                      {{ ['  const player = useTimelinePlayer()', '  ', '  // 新增: 虚拟滚动支持', '  const { list, containerProps } = useVirtualList(', '    codeLines, { itemHeight: 22 }', '  // 旧代码: 直接渲染所有行', '  )', '  ', '  // 性能优化: 预加载窗口', '  function preloadWindow(centerIndex: number) {', '    const start = Math.max(0, centerIndex - 5)', '    const end = Math.min(length - 1, centerIndex + 5)'][i-1] }}
                    </span>
                  </div>
                </div>
              </div>

              <!-- 右侧 AI 面板 -->
              <div class="mock-ai-panel">
                <div class="mock-ai-header">
                  <span class="mock-ai-icon">🤖</span>
                  <span>AI 助手</span>
                </div>
                <div class="mock-ai-chat">
                  <div class="mock-ai-msg mock-ai-user">
                    这次提交做了什么优化？
                  </div>
                  <div class="mock-ai-msg mock-ai-assistant">
                    <div class="mock-typing">
                      这次提交引入了虚拟滚动技术，使用 <code>useVirtualList</code> 来优化大文件的渲染性能。主要变更：
                      <br/><br/>
                      1. 仅渲染可视区域内的代码行<br/>
                      2. 添加了预加载窗口机制<br/>
                      3. 预计可提升 10x 渲染速度
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 底部播放控制 -->
            <div class="mock-controls">
              <div class="mock-play-btn">▶</div>
              <div class="mock-progress">
                <div class="mock-progress-bar">
                  <div class="mock-progress-fill"></div>
                </div>
                <span class="mock-progress-text">3 / 156</span>
              </div>
              <div class="mock-speed">1x</div>
            </div>
          </div>
        </div>
        <!-- 底部渐变遮罩 -->
        <div class="product-preview-fade"></div>
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
  .product-preview-wrapper { transform: perspective(1000px) rotateX(5deg) scale(0.9); }
  .mock-ai-panel { display: none; }
  .mock-timeline { display: none; }
}

/* ========== Product Preview Section (Linear-style) ========== */
.product-preview-section {
  position: relative;
  padding: 0 20px 40px;
  margin-top: -20px;
  overflow: visible;
}

.product-preview-container {
  position: relative;
  max-width: 1200px;
  margin: 0 auto;
}

.product-preview-wrapper {
  transform: perspective(2000px) rotateX(8deg) scale(0.95);
  transform-origin: center top;
  transition: transform 0.5s ease;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 
    0 50px 100px -20px rgba(0, 0, 0, 0.5),
    0 30px 60px -30px rgba(0, 0, 0, 0.4),
    0 0 0 1px rgba(255, 255, 255, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.1);
}

.product-preview-wrapper:hover {
  transform: perspective(2000px) rotateX(3deg) scale(0.98);
}

.product-preview-fade {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 200px;
  background: linear-gradient(to top, var(--bg-primary) 0%, transparent 100%);
  pointer-events: none;
  z-index: 10;
}

/* ========== Mock Player Styles ========== */
.mock-player {
  background: linear-gradient(135deg, #0d1117 0%, #161b22 100%);
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  color: #e6edf3;
  font-size: 13px;
}

/* Mock Header */
.mock-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.mock-header-left, .mock-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mock-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.05);
  cursor: pointer;
  transition: background 0.2s;
}

.mock-btn:hover {
  background: rgba(255, 255, 255, 0.1);
}

.mock-file-path {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #8b949e;
}

.mock-file-path span:last-child {
  color: #e6edf3;
  font-weight: 500;
}

.mock-badge {
  padding: 4px 10px;
  background: linear-gradient(135deg, #3178c6 0%, #235a9a 100%);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

/* Mock Body */
.mock-body {
  display: grid;
  grid-template-columns: 200px 1fr 260px;
  min-height: 320px;
}

/* Mock Timeline */
.mock-timeline {
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  padding: 12px;
  background: rgba(0, 0, 0, 0.15);
}

.mock-timeline-header {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: #8b949e;
  margin-bottom: 12px;
  letter-spacing: 0.5px;
}

.mock-timeline-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mock-commit {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.mock-commit:hover {
  background: rgba(255, 255, 255, 0.05);
}

.mock-commit.active {
  background: rgba(88, 166, 255, 0.15);
}

.mock-commit.active .mock-commit-dot {
  background: #58a6ff;
  box-shadow: 0 0 8px rgba(88, 166, 255, 0.6);
}

.mock-commit-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #484f58;
  margin-top: 5px;
  flex-shrink: 0;
}

.mock-commit-content {
  min-width: 0;
  flex: 1;
}

.mock-commit-msg {
  font-size: 12px;
  color: #e6edf3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
}

.mock-commit-meta {
  font-size: 11px;
  color: #6e7681;
}

/* Mock Code Area */
.mock-code-area {
  display: flex;
  flex-direction: column;
  background: #0d1117;
}

.mock-code-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: rgba(0, 0, 0, 0.3);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  font-weight: 500;
}

.mock-stats {
  display: flex;
  gap: 12px;
}

.mock-stat-add {
  color: #3fb950;
  font-weight: 600;
}

.mock-stat-del {
  color: #f85149;
  font-weight: 600;
}

.mock-code-viewer {
  flex: 1;
  padding: 8px 0;
  font-family: 'JetBrains Mono', 'Fira Code', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.6;
  overflow: hidden;
}

.mock-code-line {
  display: flex;
  padding: 1px 16px;
  transition: background 0.15s;
}

.mock-code-line.line-added {
  background: rgba(63, 185, 80, 0.15);
  border-left: 3px solid #3fb950;
}

.mock-code-line.line-deleted {
  background: rgba(248, 81, 73, 0.15);
  border-left: 3px solid #f85149;
  text-decoration: line-through;
  opacity: 0.7;
}

.mock-line-num {
  width: 40px;
  color: #484f58;
  text-align: right;
  margin-right: 16px;
  user-select: none;
  flex-shrink: 0;
}

.mock-line-content {
  color: #c9d1d9;
  white-space: pre;
}

/* Mock AI Panel */
.mock-ai-panel {
  border-left: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  background: rgba(0, 0, 0, 0.15);
}

.mock-ai-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  font-weight: 600;
}

.mock-ai-icon {
  font-size: 16px;
}

.mock-ai-chat {
  flex: 1;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}

.mock-ai-msg {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 12px;
  line-height: 1.5;
  max-width: 95%;
}

.mock-ai-user {
  background: linear-gradient(135deg, #238636 0%, #1a7f37 100%);
  color: white;
  align-self: flex-end;
  border-bottom-right-radius: 4px;
}

.mock-ai-assistant {
  background: rgba(255, 255, 255, 0.08);
  color: #e6edf3;
  align-self: flex-start;
  border-bottom-left-radius: 4px;
}

.mock-ai-assistant code {
  background: rgba(110, 118, 129, 0.4);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
}

/* Mock Controls */
.mock-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 20px;
  background: rgba(0, 0, 0, 0.4);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.mock-play-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--color-primary, #58a6ff) 0%, #3178c6 100%);
  border-radius: 50%;
  font-size: 12px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(88, 166, 255, 0.3);
  transition: transform 0.2s, box-shadow 0.2s;
}

.mock-play-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 16px rgba(88, 166, 255, 0.4);
}

.mock-progress {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
}

.mock-progress-bar {
  flex: 1;
  height: 4px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
  overflow: hidden;
}

.mock-progress-fill {
  width: 35%;
  height: 100%;
  background: linear-gradient(90deg, var(--color-primary, #58a6ff), #a371f7);
  border-radius: 2px;
  animation: pulse-glow 2s ease-in-out infinite;
}

@keyframes pulse-glow {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.mock-progress-text {
  font-size: 12px;
  color: #8b949e;
  min-width: 60px;
}

.mock-speed {
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
</style>
