<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useRepositoryStore } from '@/stores/repository'
import { fileApi, chatApi } from '@/api'
import { ChangeCategoryMap, type ChangeCategory } from '@/types'
import { marked } from 'marked'

const router = useRouter()
const repoStore = useRepositoryStore()

const recentCommits = computed(() => repoStore.commits.slice(0, 10))
const fileSuggestions = ref<string[]>([])
const loadingFiles = ref(false)

// 学习路径
const showLearningPath = ref(false)
const learningPathLoading = ref(false)
const learningPath = ref('')

function goToPlayer(filePath: string) {
  router.push(`/player/${repoStore.currentRepo?.id}/${encodeURIComponent(filePath)}`)
}

function goToTimeline() {
  if (repoStore.currentRepo?.id) {
    router.push(`/repo/${repoStore.currentRepo.id}/timeline`)
  }
}

async function loadFileSuggestions() {
  if (!repoStore.currentRepo?.id) {
    fileSuggestions.value = []
    return
  }

  loadingFiles.value = true
  try {
    const tree = await fileApi.getFileTree(repoStore.currentRepo.id)
    const files = flattenTree(tree)
    fileSuggestions.value = files.sort((a, b) => a.localeCompare(b)).slice(0, 3)
  } catch (error) {
    console.error('Failed to load file suggestions:', error)
    fileSuggestions.value = []
  } finally {
    loadingFiles.value = false
  }
}

function flattenTree(nodes: any[], prefix = ''): string[] {
  const result: string[] = []
  for (const node of nodes) {
    const path = prefix ? `${prefix}/${node.path}` : node.path
    if (node.type === 'file') {
      result.push(path)
    }
    if (node.children) {
      result.push(...flattenTree(node.children, path))
    }
  }
  return result
}

function getCategoryInfo(category?: ChangeCategory) {
  if (!category) return { label: '其他', color: '#6B7280' }
  return ChangeCategoryMap[category] || { label: category, color: '#6B7280' }
}

function formatTime(dateStr: string) {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  if (days < 7) return `${days}天前`
  if (days < 30) return `${Math.floor(days / 7)}周前`
  return date.toLocaleDateString()
}

onMounted(loadFileSuggestions)
watch(() => repoStore.currentRepo?.id, loadFileSuggestions)

async function generateLearningPath() {
  if (!repoStore.currentRepo?.id) return
  
  showLearningPath.value = true
  learningPathLoading.value = true
  
  try {
    const result = await chatApi.getLearningPath(repoStore.currentRepo.id)
    learningPath.value = result.learningPath
  } catch (e) {
    console.error('Failed to generate learning path:', e)
    learningPath.value = '生成学习路径失败，请稍后重试。'
  } finally {
    learningPathLoading.value = false
  }
}

function renderMarkdown(text: string) {
  return marked(text)
}
</script>

<template>
  <div class="overview-tab">
    <div class="overview-grid">
      <!-- 左侧：快速开始 -->
      <div class="quick-start-section">
        <h3>
          <el-icon><VideoPlay /></el-icon>
          快速开始
        </h3>
        <p class="section-desc">选择一个文件，开始时光之旅</p>

        <div class="file-suggestions">
          <div v-if="loadingFiles" class="file-empty">正在加载文件...</div>
          <div v-else-if="fileSuggestions.length === 0" class="file-empty">暂无可用文件</div>
          <div
            v-else
            class="file-item"
            v-for="file in fileSuggestions"
            :key="file"
            @click="goToPlayer(file)"
          >
            <el-icon class="file-icon"><Document /></el-icon>
            <span class="file-name">{{ file }}</span>
            <el-icon class="arrow-icon"><ArrowRight /></el-icon>
          </div>
        </div>

        <el-button class="view-all-btn" text @click="goToTimeline">
          查看所有文件<el-icon><ArrowRight /></el-icon>
        </el-button>
        
        <el-divider />
        
        <!-- 学习路径 -->
        <h3 style="margin-top: var(--spacing-md);">
          <el-icon><Reading /></el-icon>
          AI 学习路径
        </h3>
        <p class="section-desc">让 AI 帮你规划学习顺序</p>
        <el-button type="primary" @click="generateLearningPath" :loading="learningPathLoading">
          <el-icon><MagicStick /></el-icon>
          生成学习路径
        </el-button>
      </div>

      <!-- 右侧：最近提交 -->
      <div class="recent-commits-section">
        <h3>
          <el-icon><Clock /></el-icon>
          最近提交
        </h3>

        <div class="commits-list">
          <div
            v-for="commit in recentCommits"
            :key="commit.id"
            class="commit-item"
            @click="repoStore.selectCommit(commit)"
          >
            <div class="commit-main">
              <div class="commit-header">
                <span
                  class="commit-category"
                  :style="{ backgroundColor: getCategoryInfo(commit.aiAnalysis?.changeCategory).color + '20', color: getCategoryInfo(commit.aiAnalysis?.changeCategory).color }"
                >
                  {{ getCategoryInfo(commit.aiAnalysis?.changeCategory).label }}
                </span>
                <span class="commit-hash">{{ commit.shortHash }}</span>
              </div>
              <p class="commit-message">{{ commit.commitMessage }}</p>
              <div class="commit-meta">
                <span class="commit-author">
                  <el-icon><User /></el-icon>
                  {{ commit.authorName }}
                </span>
                <span class="commit-time">{{ formatTime(commit.commitTime) }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 主要贡献者 -->
    <div class="contributors-section" v-if="repoStore.contributors.length > 0">
      <h3>
        <el-icon><User /></el-icon>
        主要贡献者
      </h3>
      <div class="contributors-grid">
        <div
          v-for="contributor in repoStore.contributors.slice(0, 6)"
          :key="contributor.authorName"
          class="contributor-card"
        >
          <div class="contributor-avatar">
            {{ contributor.authorName.charAt(0).toUpperCase() }}
          </div>
          <div class="contributor-info">
            <span class="contributor-name">{{ contributor.authorName }}</span>
            <span class="contributor-stats">
              {{ contributor.commitCount }} 次提交
            </span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 学习路径对话框 -->
    <el-dialog 
      v-model="showLearningPath" 
      title="📚 AI 学习路径" 
      width="700px"
      destroy-on-close
    >
      <div v-if="learningPathLoading" class="loading-container">
        <el-icon class="is-loading" :size="40"><Loading /></el-icon>
        <p>AI 正在分析项目结构...</p>
      </div>
      <div v-else class="learning-path-content" v-html="renderMarkdown(learningPath)"></div>
    </el-dialog>
  </div>
</template>

<style scoped>
.overview-tab {
  min-height: 400px;
}

.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1.5fr;
  gap: var(--spacing-xl);
  margin-bottom: var(--spacing-xl);
}

h3 {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 1rem;
  margin-bottom: var(--spacing-md);
}

h3 .el-icon {
  color: var(--color-primary);
}

.section-desc {
  color: var(--text-muted);
  font-size: 0.9rem;
  margin-bottom: var(--spacing-lg);
}

.quick-start-section {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg);
}

.file-suggestions {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-md);
}

.file-item {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.file-item:hover {
  border-color: var(--color-primary);
  background: var(--bg-hover);
}

.file-icon {
  color: var(--color-accent);
}

.file-name {
  flex: 1;
  font-family: var(--font-mono);
  font-size: 0.9rem;
}

.arrow-icon {
  color: var(--text-muted);
  font-size: 12px;
}

.file-empty {
  padding: var(--spacing-md);
  background: var(--bg-card);
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-muted);
  font-size: 0.9rem;
}

.view-all-btn {
  color: var(--color-accent) !important;
}

.recent-commits-section {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg);
}

.commits-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
  max-height: 400px;
  overflow-y: auto;
}

.commit-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: var(--spacing-md);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.commit-item:hover {
  border-color: var(--color-primary);
}

.commit-main {
  flex: 1;
  min-width: 0;
}

.commit-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.commit-category {
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: 0.7rem;
  font-weight: 500;
}

.commit-hash {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  color: var(--text-muted);
}

.commit-message {
  font-size: 0.9rem;
  margin-bottom: var(--spacing-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.commit-meta {
  display: flex;
  gap: var(--spacing-md);
  font-size: 0.75rem;
  color: var(--text-muted);
}

.commit-author {
  display: flex;
  align-items: center;
  gap: 4px;
}

.commit-stats {
  display: flex;
  gap: var(--spacing-sm);
  font-family: var(--font-mono);
  font-size: 0.8rem;
}

.stat-add {
  color: var(--color-success);
}

.stat-del {
  color: var(--color-danger);
}

.contributors-section {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg);
}

.contributors-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: var(--spacing-md);
}

.contributor-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
}

.contributor-avatar {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: var(--bg-primary);
}

.contributor-info {
  display: flex;
  flex-direction: column;
}

.contributor-name {
  font-weight: 500;
}

.contributor-stats {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.text-success {
  color: var(--color-success);
}

.text-danger {
  color: var(--color-danger);
}

@media (max-width: 1024px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}

.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--spacing-xl);
  gap: var(--spacing-md);
  color: var(--text-muted);
}

.learning-path-content {
  line-height: 1.8;
  max-height: 60vh;
  overflow-y: auto;
}

.learning-path-content h1,
.learning-path-content h2,
.learning-path-content h3 {
  margin-top: var(--spacing-lg);
  margin-bottom: var(--spacing-sm);
  color: var(--text-primary);
}

.learning-path-content ul,
.learning-path-content ol {
  padding-left: var(--spacing-lg);
}

.learning-path-content li {
  margin-bottom: var(--spacing-xs);
}

.learning-path-content code {
  background: var(--bg-secondary);
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-family: var(--font-mono);
}

.learning-path-content blockquote {
  border-left: 3px solid var(--color-primary);
  margin: var(--spacing-md) 0;
  padding-left: var(--spacing-md);
  color: var(--text-secondary);
}
</style>
