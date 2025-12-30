<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRepositoryStore } from '@/stores/repository'
import { fileApi, commitApi } from '@/api'
import { ChangeTypeMap, ChangeCategoryMap, type ChangeType, type AiAnalysis, type ChangeCategory } from '@/types'
import { ElMessage } from 'element-plus'

const router = useRouter()
const repoStore = useRepositoryStore()

const selectedFile = ref('')
const fileList = ref<Array<{ path: string; modifyCount: number }>>([])
const loading = ref(false)
const loadingMore = ref(false)

// AI 分析相关状态
const analysisMap = ref<Map<number, AiAnalysis>>(new Map())
const analysisLoading = ref<Set<number>>(new Set())
const expandedAnalysis = ref<Set<number>>(new Set())

const commits = computed(() => repoStore.commitsByOrder)
const commitsTotal = computed(() => repoStore.commitsTotal)
const hasMoreCommits = computed(() => repoStore.hasMoreCommits)

onMounted(async () => {
  // 获取文件列表
  if (repoStore.currentRepo) {
    try {
      const tree = await fileApi.getFileTree(repoStore.currentRepo.id)
      fileList.value = flattenTree(tree)
    } catch (e) {
      console.error(e)
    }
  }
})

function flattenTree(nodes: any[], prefix = ''): Array<{ path: string; modifyCount: number }> {
  const result: Array<{ path: string; modifyCount: number }> = []
  for (const node of nodes) {
    const path = prefix ? `${prefix}/${node.path}` : node.path
    if (node.type === 'file') {
      result.push({ path, modifyCount: node.modifyCount || 0 })
    }
    if (node.children) {
      result.push(...flattenTree(node.children, path))
    }
  }
  // 按修改次数降序排列
  return result.sort((a, b) => b.modifyCount - a.modifyCount)
}

function goToPlayer() {
  if (selectedFile.value && repoStore.currentRepo) {
    router.push(`/player/${repoStore.currentRepo.id}/${encodeURIComponent(selectedFile.value)}`)
  }
}

function getChangeTypeInfo(type: ChangeType) {
  return ChangeTypeMap[type] || { label: type, color: '#6B7280' }
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  })
}

async function loadMore() {
  if (!repoStore.currentRepo || loadingMore.value) return
  loadingMore.value = true
  try {
    await repoStore.loadMoreCommits(repoStore.currentRepo.id)
  } finally {
    loadingMore.value = false
  }
}

// ========== AI 分析相关函数 ==========

// 获取或触发 AI 分析
async function toggleAnalysis(commitId: number) {
  // 如果已展开，则收起
  if (expandedAnalysis.value.has(commitId)) {
    expandedAnalysis.value.delete(commitId)
    expandedAnalysis.value = new Set(expandedAnalysis.value)
    return
  }

  // 如果已有分析结果，直接展开
  if (analysisMap.value.has(commitId)) {
    expandedAnalysis.value.add(commitId)
    expandedAnalysis.value = new Set(expandedAnalysis.value)
    return
  }

  // 先尝试获取已有分析
  await fetchOrTriggerAnalysis(commitId)
}

async function fetchOrTriggerAnalysis(commitId: number) {
  if (analysisLoading.value.has(commitId)) return

  analysisLoading.value.add(commitId)
  analysisLoading.value = new Set(analysisLoading.value)

  try {
    // 先尝试获取已有分析
    try {
      const analysis = await commitApi.getAiAnalysis(commitId)
      analysisMap.value.set(commitId, analysis)
      analysisMap.value = new Map(analysisMap.value)
      expandedAnalysis.value.add(commitId)
      expandedAnalysis.value = new Set(expandedAnalysis.value)
      return
    } catch (e: any) {
      // 404 表示没有分析，需要触发
      if (e?.response?.status !== 404 && !e?.message?.includes('404')) {
        console.log('No existing analysis, triggering new one...')
      }
    }

    // 触发新分析
    ElMessage.info('正在生成 AI 分析，请稍候...')
    const analysis = await commitApi.triggerAnalysis(commitId)
    analysisMap.value.set(commitId, analysis)
    analysisMap.value = new Map(analysisMap.value)
    expandedAnalysis.value.add(commitId)
    expandedAnalysis.value = new Set(expandedAnalysis.value)
    ElMessage.success('AI 分析完成')
  } catch (e: any) {
    console.error('AI analysis failed:', e)
    ElMessage.error('AI 分析失败: ' + (e?.message || '未知错误'))
  } finally {
    analysisLoading.value.delete(commitId)
    analysisLoading.value = new Set(analysisLoading.value)
  }
}

function isAnalysisLoading(commitId: number): boolean {
  return analysisLoading.value.has(commitId)
}

function isAnalysisExpanded(commitId: number): boolean {
  return expandedAnalysis.value.has(commitId)
}

function getAnalysis(commitId: number): AiAnalysis | undefined {
  return analysisMap.value.get(commitId)
}

function getCategoryInfo(category: string | undefined) {
  if (!category) return null
  return ChangeCategoryMap[category as ChangeCategory] || null
}

function renderStars(score: number | undefined, max: number = 10): string {
  if (!score) return ''
  const filled = Math.round((score / max) * 5)
  return '★'.repeat(filled) + '☆'.repeat(5 - filled)
}
</script>

<template>
  <div class="timeline-tab">
    <!-- 文件选择器 -->
    <div class="file-selector">
      <el-select 
        v-model="selectedFile" 
        placeholder="选择要查看的文件"
        filterable
        size="large"
        class="file-select"
      >
        <el-option
          v-for="file in fileList"
          :key="file.path"
          :label="file.path"
          :value="file.path"
        >
          <span>{{ file.path }}</span>
          <span class="modify-count-badge">{{ file.modifyCount }} 次修改</span>
        </el-option>
      </el-select>
      <el-button 
        type="primary" 
        size="large"
        :disabled="!selectedFile"
        @click="goToPlayer"
      >
        <el-icon><VideoPlay /></el-icon>
        进入时光机
      </el-button>
    </div>

    <!-- 提交时间轴 -->
    <div class="timeline-container">
      <div class="timeline-header">
        <h3>提交时间轴</h3>
        <span class="commit-count">共 {{ commitsTotal }} 次提交</span>
      </div>

      <div class="timeline">
        <div class="timeline-line"></div>
        
        <div 
          v-for="(commit, index) in commits" 
          :key="commit.id"
          class="timeline-item"
          :class="{ 'timeline-item--first': index === 0 }"
        >
          <div class="timeline-dot">
            <span class="dot-inner"></span>
          </div>
          
          <div class="timeline-content">
            <div class="commit-card">
              <div class="commit-header">
                <span class="commit-order">#{{ index + 1 }}</span>
                <span class="commit-hash">{{ commit.shortHash }}</span>
                <span class="commit-date">{{ formatDate(commit.commitTime) }}</span>
              </div>
              
              <p class="commit-message">{{ commit.commitMessage }}</p>
              
              <div class="commit-details">
                <div class="commit-author">
                  <div class="author-avatar">
                    {{ commit.authorName.charAt(0).toUpperCase() }}
                  </div>
                  <span>{{ commit.authorName }}</span>
                </div>
                
                <div class="commit-stats">
                  <span class="files-changed" v-if="commit.filesChanged !== null">
                    <el-icon><Document /></el-icon>
                    {{ commit.filesChanged }} 文件
                  </span>
                  <span class="stats-loading" v-else>
                    <el-icon class="is-loading"><Loading /></el-icon>
                  </span>
                  <template v-if="commit.additions !== null">
                    <span class="additions">+{{ commit.additions }}</span>
                    <span class="deletions">-{{ commit.deletions }}</span>
                  </template>
                  <span class="stats-loading-text" v-else>计算中...</span>
                </div>
              </div>

              <!-- 文件变更列表 -->
              <div class="file-changes" v-if="commit.fileChanges && commit.fileChanges.length > 0">
                <div 
                  v-for="file in commit.fileChanges.slice(0, 5)" 
                  :key="file.id"
                  class="file-change"
                >
                  <span 
                    class="change-type"
                    :style="{ color: getChangeTypeInfo(file.changeType).color }"
                  >
                    {{ getChangeTypeInfo(file.changeType).label }}
                  </span>
                  <span class="file-path">{{ file.filePath }}</span>
                </div>
                <div 
                  v-if="commit.fileChanges.length > 5" 
                  class="more-files"
                >
                  还有 {{ commit.fileChanges.length - 5 }} 个文件...
                </div>
              </div>

              <!-- AI 分析面板 -->
              <div class="ai-analysis-section">
                <div 
                  class="ai-analysis-trigger"
                  @click="toggleAnalysis(commit.id)"
                >
                  <span class="ai-trigger-icon">
                    <el-icon v-if="isAnalysisLoading(commit.id)" class="is-loading"><Loading /></el-icon>
                    <el-icon v-else><MagicStick /></el-icon>
                  </span>
                  <span class="ai-trigger-text">
                    {{ isAnalysisLoading(commit.id) ? '分析中...' : 'AI 分析' }}
                  </span>
                  <el-icon class="ai-trigger-arrow" :class="{ 'is-expanded': isAnalysisExpanded(commit.id) }">
                    <ArrowDown />
                  </el-icon>
                </div>

                <!-- 展开的分析内容 -->
                <transition name="slide-fade">
                  <div v-if="isAnalysisExpanded(commit.id) && getAnalysis(commit.id)" class="ai-analysis-content">
                    <div class="analysis-item" v-if="getAnalysis(commit.id)?.summary">
                      <span class="analysis-label">📝 摘要</span>
                      <p class="analysis-text">{{ getAnalysis(commit.id)?.summary }}</p>
                    </div>
                    
                    <div class="analysis-item" v-if="getAnalysis(commit.id)?.purpose">
                      <span class="analysis-label">🎯 目的</span>
                      <p class="analysis-text">{{ getAnalysis(commit.id)?.purpose }}</p>
                    </div>
                    
                    <div class="analysis-item" v-if="getAnalysis(commit.id)?.impact">
                      <span class="analysis-label">⚡ 影响</span>
                      <p class="analysis-text">{{ getAnalysis(commit.id)?.impact }}</p>
                    </div>

                    <div class="analysis-footer">
                      <!-- 分类标签 -->
                      <span 
                        v-if="getCategoryInfo(getAnalysis(commit.id)?.changeCategory)"
                        class="category-tag"
                        :style="{ 
                          background: getCategoryInfo(getAnalysis(commit.id)?.changeCategory)?.color + '20',
                          color: getCategoryInfo(getAnalysis(commit.id)?.changeCategory)?.color,
                          borderColor: getCategoryInfo(getAnalysis(commit.id)?.changeCategory)?.color
                        }"
                      >
                        {{ getCategoryInfo(getAnalysis(commit.id)?.changeCategory)?.label }}
                      </span>

                      <!-- 评分 -->
                      <div class="analysis-scores" v-if="getAnalysis(commit.id)?.complexityScore || getAnalysis(commit.id)?.importanceScore">
                        <span class="score-item" v-if="getAnalysis(commit.id)?.complexityScore">
                          <span class="score-label">复杂度</span>
                          <span class="score-stars">{{ renderStars(getAnalysis(commit.id)?.complexityScore) }}</span>
                        </span>
                        <span class="score-item" v-if="getAnalysis(commit.id)?.importanceScore">
                          <span class="score-label">重要性</span>
                          <span class="score-stars">{{ renderStars(getAnalysis(commit.id)?.importanceScore) }}</span>
                        </span>
                      </div>
                    </div>
                  </div>
                </transition>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 加载更多 -->
      <div class="load-more" v-if="hasMoreCommits">
        <el-button :loading="loadingMore" @click="loadMore">
          加载更多 ({{ commits.length }}/{{ commitsTotal }})
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.timeline-tab {
  min-height: 500px;
}

.file-selector {
  display: flex;
  gap: var(--spacing-md);
  margin-bottom: var(--spacing-xl);
  padding: var(--spacing-lg);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
}

.file-select {
  flex: 1;
}

.modify-count-badge {
  float: right;
  font-size: 12px;
  color: var(--color-primary);
  background: var(--color-primary-glow);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  margin-left: 8px;
}

.timeline-container {
  position: relative;
}

.timeline-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--spacing-lg);
}

.timeline-header h3 {
  font-size: 1.1rem;
}

.commit-count {
  color: var(--text-muted);
  font-size: 0.9rem;
}

.timeline {
  position: relative;
  padding-left: 40px;
}

.timeline-line {
  position: absolute;
  left: 15px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: linear-gradient(
    to bottom,
    var(--color-primary),
    var(--color-accent),
    var(--color-secondary)
  );
}

.timeline-item {
  position: relative;
  padding-bottom: var(--spacing-lg);
}

.timeline-dot {
  position: absolute;
  left: -33px;
  top: 8px;
  width: 16px;
  height: 16px;
  background: var(--bg-primary);
  border: 2px solid var(--color-primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.timeline-item--first .timeline-dot {
  width: 20px;
  height: 20px;
  left: -35px;
  border-color: var(--color-accent);
  box-shadow: 0 0 10px var(--color-accent);
}

.dot-inner {
  width: 6px;
  height: 6px;
  background: var(--color-primary);
  border-radius: 50%;
}

.timeline-item--first .dot-inner {
  background: var(--color-accent);
}

.commit-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  transition: all var(--transition-fast);
}

.commit-card:hover {
  border-color: var(--color-primary);
}

.commit-header {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.commit-order {
  background: var(--color-primary-glow);
  color: var(--color-primary);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  font-size: 0.75rem;
  font-family: var(--font-mono);
}

.commit-hash {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  color: var(--text-muted);
}

.commit-date {
  margin-left: auto;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.commit-message {
  font-size: 0.95rem;
  margin-bottom: var(--spacing-md);
  line-height: 1.5;
}

.commit-details {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--spacing-sm);
  border-top: 1px solid var(--border-color);
}

.commit-author {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 0.85rem;
}

.author-avatar {
  width: 24px;
  height: 24px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.7rem;
  font-weight: 600;
  color: var(--bg-primary);
}

.commit-stats {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  font-size: 0.8rem;
}

.files-changed {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--text-muted);
}

.additions {
  color: var(--color-success);
  font-family: var(--font-mono);
}

.deletions {
  color: var(--color-danger);
  font-family: var(--font-mono);
}

.stats-loading {
  color: var(--text-muted);
}

.stats-loading-text {
  color: var(--text-muted);
  font-size: 0.8rem;
  font-style: italic;
}

.file-changes {
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-sm);
  border-top: 1px dashed var(--border-color);
}

.file-change {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: 4px 0;
  font-size: 0.8rem;
}

.change-type {
  font-weight: 500;
  min-width: 40px;
}

.file-path {
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

.more-files {
  font-size: 0.8rem;
  color: var(--text-muted);
  padding-top: 4px;
}

.load-more {
  text-align: center;
  padding: var(--spacing-xl);
}

@media (max-width: 768px) {
  .file-selector {
    flex-direction: column;
  }
  
  .timeline {
    padding-left: 30px;
  }
  
  .timeline-dot {
    left: -23px;
  }
}

/* ========== AI 分析面板样式 ========== */
.ai-analysis-section {
  margin-top: var(--spacing-md);
  padding-top: var(--spacing-sm);
  border-top: 1px dashed var(--border-color);
}

.ai-analysis-trigger {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1), rgba(139, 92, 246, 0.1));
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  user-select: none;
}

.ai-analysis-trigger:hover {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.2), rgba(139, 92, 246, 0.2));
  border-color: rgba(99, 102, 241, 0.4);
}

.ai-trigger-icon {
  color: var(--color-primary);
  font-size: 1rem;
  display: flex;
  align-items: center;
}

.ai-trigger-text {
  flex: 1;
  font-size: 0.85rem;
  font-weight: 500;
  color: var(--color-primary);
}

.ai-trigger-arrow {
  color: var(--text-muted);
  transition: transform var(--transition-fast);
}

.ai-trigger-arrow.is-expanded {
  transform: rotate(180deg);
}

.ai-analysis-content {
  margin-top: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.analysis-item {
  margin-bottom: var(--spacing-md);
}

.analysis-item:last-of-type {
  margin-bottom: var(--spacing-sm);
}

.analysis-label {
  display: block;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
}

.analysis-text {
  font-size: 0.9rem;
  line-height: 1.6;
  color: var(--text-primary);
  margin: 0;
}

.analysis-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
  padding-top: var(--spacing-sm);
  border-top: 1px dashed var(--border-color);
}

.category-tag {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  font-size: 0.75rem;
  font-weight: 600;
  border-radius: var(--radius-full);
  border: 1px solid;
}

.analysis-scores {
  display: flex;
  gap: var(--spacing-md);
}

.score-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 0.75rem;
}

.score-label {
  color: var(--text-muted);
}

.score-stars {
  color: #F59E0B;
  font-size: 0.8rem;
  letter-spacing: 1px;
}

/* 过渡动画 */
.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.2s ease-in;
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
