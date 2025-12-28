<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useRepositoryStore } from '@/stores/repository'
import { fileApi } from '@/api'
import { ChangeTypeMap, type ChangeType } from '@/types'

const router = useRouter()
const repoStore = useRepositoryStore()

const selectedFile = ref('')
const fileList = ref<Array<{ path: string; modifyCount: number }>>([])
const loading = ref(false)

const commits = computed(() => repoStore.commitsByOrder)

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
        <span class="commit-count">共 {{ commits.length }} 次提交</span>
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
            </div>
          </div>
        </div>
      </div>

      <!-- 加载更多 -->
      <div class="load-more" v-if="commits.length >= 100">
        <el-button :loading="loading">加载更多</el-button>
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
</style>
