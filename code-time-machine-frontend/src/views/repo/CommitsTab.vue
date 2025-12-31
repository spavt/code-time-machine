<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRepositoryStore } from '@/stores/repository'
import { ChangeCategoryMap, ChangeTypeMap, type ChangeCategory, type ChangeType } from '@/types'

const repoStore = useRepositoryStore()

const searchKeyword = ref('')
const selectedCategory = ref<ChangeCategory | ''>('')
const currentPage = ref(1)
const pageSize = ref(20)

const categories: { label: string; value: ChangeCategory | '' }[] = [
  { label: '全部类型', value: '' },
  { label: '新功能', value: 'feature' },
  { label: '修复Bug', value: 'bugfix' },
  { label: '重构', value: 'refactor' },
  { label: '文档', value: 'docs' },
  { label: '性能', value: 'perf' },
]

const filteredCommits = computed(() => {
  let result = repoStore.commits

  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    result = result.filter(c => 
      c.commitMessage.toLowerCase().includes(keyword) ||
      c.authorName.toLowerCase().includes(keyword) ||
      c.shortHash.includes(keyword)
    )
  }

  if (selectedCategory.value) {
    result = result.filter(c => 
      c.aiAnalysis?.changeCategory === selectedCategory.value
    )
  }

  return result
})

const paginatedCommits = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredCommits.value.slice(start, start + pageSize.value)
})

const totalPages = computed(() => Math.ceil(filteredCommits.value.length / pageSize.value))

const selectedCommit = ref<number | null>(null)

function selectCommit(commitId: number) {
  selectedCommit.value = selectedCommit.value === commitId ? null : commitId
  const commit = repoStore.commits.find(c => c.id === commitId)
  if (commit) {
    repoStore.selectCommit(commit)
  }
}

function getCategoryInfo(category?: ChangeCategory) {
  if (!category) return { label: '其他', color: '#6B7280' }
  return ChangeCategoryMap[category] || { label: category, color: '#6B7280' }
}

function getChangeTypeInfo(type: ChangeType) {
  return ChangeTypeMap[type] || { label: type, color: '#6B7280' }
}

function formatTime(dateStr: string) {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}
</script>

<template>
  <div class="commits-tab">
    <!-- 搜索和过滤 -->
    <div class="filter-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索提交信息、作者、哈希值..."
        clearable
        class="search-input"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>

      <el-select v-model="selectedCategory" placeholder="变更类型" clearable>
        <el-option
          v-for="cat in categories"
          :key="cat.value"
          :label="cat.label"
          :value="cat.value"
        />
      </el-select>

      <div class="result-count">
        共 {{ filteredCommits.length }} 条记录
      </div>
    </div>

    <!-- 提交列表 -->
    <div class="commits-list">
      <div 
        v-for="commit in paginatedCommits" 
        :key="commit.id"
        class="commit-row"
        :class="{ 'commit-row--expanded': selectedCommit === commit.id }"
        @click="selectCommit(commit.id)"
      >
        <div class="commit-main">
          <div class="commit-info">
            <div class="commit-header">
              <span 
                class="commit-category"
                :style="{ 
                  backgroundColor: getCategoryInfo(commit.aiAnalysis?.changeCategory).color + '20', 
                  color: getCategoryInfo(commit.aiAnalysis?.changeCategory).color 
                }"
              >
                {{ getCategoryInfo(commit.aiAnalysis?.changeCategory).label }}
              </span>
              <span class="commit-hash">{{ commit.shortHash }}</span>
              <span class="commit-time">{{ formatTime(commit.commitTime) }}</span>
            </div>
            
            <p class="commit-message">{{ commit.commitMessage }}</p>
            
            <div class="commit-meta">
              <span class="commit-author">
                <el-icon><User /></el-icon>
                {{ commit.authorName }}
              </span>
            </div>
          </div>

          <div class="commit-stats">
            <el-icon class="expand-icon" :class="{ rotated: selectedCommit === commit.id }">
              <ArrowDown />
            </el-icon>
          </div>
        </div>

        <!-- 展开详情 -->
        <transition name="slide">
          <div v-if="selectedCommit === commit.id" class="commit-details">
            <!-- AI分析 -->
            <div class="analysis-section" v-if="repoStore.currentAnalysis">
              <h4><el-icon><MagicStick /></el-icon> AI分析</h4>
              <div class="analysis-content">
                <p v-if="repoStore.currentAnalysis.summary">
                  <strong>摘要：</strong>{{ repoStore.currentAnalysis.summary }}
                </p>
                <p v-if="repoStore.currentAnalysis.purpose">
                  <strong>目的：</strong>{{ repoStore.currentAnalysis.purpose }}
                </p>
                <p v-if="repoStore.currentAnalysis.impact">
                  <strong>影响：</strong>{{ repoStore.currentAnalysis.impact }}
                </p>
              </div>
            </div>

            <!-- 文件变更 -->
            <div class="files-section" v-if="repoStore.fileChanges.length > 0">
              <h4><el-icon><Folder /></el-icon> 变更文件</h4>
              <div class="files-list">
                <div 
                  v-for="file in repoStore.fileChanges" 
                  :key="file.id"
                  class="file-row"
                >
                  <span 
                    class="file-type"
                    :style="{ color: getChangeTypeInfo(file.changeType).color }"
                  >
                    {{ getChangeTypeInfo(file.changeType).label }}
                  </span>
                  <span class="file-path">{{ file.filePath }}</span>
                </div>
              </div>
            </div>
          </div>
        </transition>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="totalPages > 1">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="filteredCommits.length"
        layout="prev, pager, next"
        background
      />
    </div>
  </div>
</template>

<style scoped>
.commits-tab {
  min-height: 500px;
}

.filter-bar {
  display: flex;
  gap: var(--spacing-md);
  align-items: center;
  margin-bottom: var(--spacing-lg);
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
}

.search-input {
  flex: 1;
  max-width: 400px;
}

.result-count {
  margin-left: auto;
  font-size: 0.9rem;
  color: var(--text-muted);
}

.commits-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.commit-row {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.commit-row:hover {
  border-color: var(--color-primary);
}

.commit-row--expanded {
  border-color: var(--color-primary);
}

.commit-main {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-md);
}

.commit-info {
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
  font-size: 0.8rem;
  color: var(--text-muted);
}

.commit-time {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.commit-message {
  font-size: 0.95rem;
  margin-bottom: var(--spacing-xs);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.commit-meta {
  display: flex;
  gap: var(--spacing-md);
  font-size: 0.8rem;
  color: var(--text-muted);
}

.commit-author,
.commit-files {
  display: flex;
  align-items: center;
  gap: 4px;
}

.commit-stats {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.stat-value {
  font-family: var(--font-mono);
  font-size: 0.9rem;
  font-weight: 600;
}

.additions {
  color: var(--color-success);
}

.deletions {
  color: var(--color-danger);
}

.expand-icon {
  color: var(--text-muted);
  transition: transform var(--transition-fast);
}

.expand-icon.rotated {
  transform: rotate(180deg);
}

.commit-details {
  padding: var(--spacing-lg);
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-color);
}

.commit-details h4 {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 0.9rem;
  margin-bottom: var(--spacing-md);
}

.commit-details h4 .el-icon {
  color: var(--color-primary);
}

.analysis-section {
  margin-bottom: var(--spacing-lg);
}

.analysis-content p {
  font-size: 0.9rem;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-sm);
  line-height: 1.6;
}

.analysis-content strong {
  color: var(--text-primary);
}

.files-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-xs);
}

.file-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  padding: var(--spacing-sm);
  background: var(--bg-card);
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
}

.file-type {
  min-width: 40px;
  font-weight: 500;
}

.file-path {
  flex: 1;
  font-family: var(--font-mono);
  color: var(--text-secondary);
}

.file-stats {
  display: flex;
  gap: var(--spacing-sm);
  font-family: var(--font-mono);
  font-size: 0.8rem;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: var(--spacing-xl);
}

.slide-enter-active,
.slide-leave-active {
  transition: all var(--transition-normal);
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
}

@media (max-width: 768px) {
  .filter-bar {
    flex-wrap: wrap;
  }
  
  .search-input {
    width: 100%;
    max-width: none;
  }
  
  .commit-main {
    flex-direction: column;
    align-items: flex-start;
    gap: var(--spacing-sm);
  }
  
  .commit-stats {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
