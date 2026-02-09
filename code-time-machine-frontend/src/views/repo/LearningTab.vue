<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { learningApi } from '@/api'
import { useRepositoryStore } from '@/stores/repository'
import type { LearningDifficulty, LearningMission, LearningPlan } from '@/types'

const router = useRouter()
const repoStore = useRepositoryStore()

const loading = ref(false)
const plan = ref<LearningPlan | null>(null)
const activeMissionId = ref('')

const activeMission = computed(() => {
  if (!plan.value || !activeMissionId.value) {
    return null
  }
  return plan.value.missions.find(m => m.missionId === activeMissionId.value) || null
})

async function loadPlan() {
  const repoId = repoStore.currentRepo?.id
  if (!repoId) return

  loading.value = true
  try {
    const result = await learningApi.getPlan(repoId)
    plan.value = result
    if (!activeMissionId.value || !result.missions.some(m => m.missionId === activeMissionId.value)) {
      activeMissionId.value = result.missions[0]?.missionId || ''
    }
  } catch (error) {
    console.error('Load learning plan failed:', error)
    ElMessage.error('学习任务加载失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

function selectMission(mission: LearningMission) {
  activeMissionId.value = mission.missionId
}

function openPlayer(filePath: string) {
  const repoId = repoStore.currentRepo?.id
  if (!repoId) return
  router.push(`/player/${repoId}/${encodeURIComponent(filePath)}`)
}

function openTimeline(filePath: string) {
  const repoId = repoStore.currentRepo?.id
  if (!repoId) return
  router.push({
    path: `/repo/${repoId}/timeline`,
    query: { file: filePath }
  })
}

function openCommit(shortHash: string) {
  const repoId = repoStore.currentRepo?.id
  if (!repoId) return
  router.push({
    path: `/repo/${repoId}/commits`,
    query: { keyword: shortHash }
  })
}

function difficultyLabel(level: LearningDifficulty) {
  if (level === 'advanced') return '进阶'
  if (level === 'intermediate') return '中级'
  return '入门'
}

function difficultyType(level: LearningDifficulty) {
  if (level === 'advanced') return 'danger'
  if (level === 'intermediate') return 'warning'
  return 'success'
}

function formatChangeTypes(types?: string[]) {
  if (!types || types.length === 0) return '类型未知'
  const labelMap: Record<string, string> = {
    ADD: '新增',
    MODIFY: '修改',
    DELETE: '删除',
    RENAME: '重命名',
    COPY: '复制'
  }
  return types.map(type => labelMap[type] || type).join(' / ')
}

onMounted(loadPlan)
watch(() => repoStore.currentRepo?.id, () => {
  loadPlan()
})
</script>

<template>
  <div class="learning-tab" v-loading="loading">
    <div v-if="plan" class="learning-layout">
      <section class="learning-summary">
        <h3>学习任务模式</h3>
        <p>从“文件时间线”升级到“功能理解任务”。每个任务都包含跨文件阅读顺序和关键提交解剖。</p>
        <div class="summary-metrics">
          <div class="metric-card">
            <span class="metric-value">{{ plan.totalMissions }}</span>
            <span class="metric-label">任务数</span>
          </div>
          <div class="metric-card">
            <span class="metric-value">{{ plan.estimatedTotalMinutes }}</span>
            <span class="metric-label">预计总分钟</span>
          </div>
        </div>
      </section>

      <section class="mission-panel">
        <div class="mission-list">
          <h4>任务清单</h4>
          <div
            v-for="mission in plan.missions"
            :key="mission.missionId"
            class="mission-item"
            :class="{ active: mission.missionId === activeMissionId }"
            @click="selectMission(mission)"
          >
            <div class="mission-item-main">
              <p class="mission-item-title">{{ mission.title }}</p>
              <p class="mission-item-meta">{{ mission.estimatedMinutes }} 分钟 · 热度 {{ mission.hotScore }}</p>
            </div>
            <el-tag size="small" :type="difficultyType(mission.difficulty)">
              {{ difficultyLabel(mission.difficulty) }}
            </el-tag>
          </div>
        </div>

        <div class="mission-detail" v-if="activeMission">
          <div class="detail-header">
            <h4>{{ activeMission.title }}</h4>
            <el-tag :type="difficultyType(activeMission.difficulty)">
              {{ difficultyLabel(activeMission.difficulty) }}
            </el-tag>
          </div>
          <p class="detail-objective">{{ activeMission.objective }}</p>
          <p class="detail-reason">{{ activeMission.importanceReason }}</p>

          <div class="detail-block" v-if="activeMission.recommendationEvidence.length > 0">
            <h5>推荐证据</h5>
            <ul class="evidence-list">
              <li v-for="evidence in activeMission.recommendationEvidence" :key="evidence">
                {{ evidence }}
              </li>
            </ul>
          </div>

          <div class="detail-block">
            <h5>推荐先看文件</h5>
            <div class="file-list">
              <div v-for="filePath in activeMission.filePaths" :key="filePath" class="file-item">
                <span class="file-path">{{ filePath }}</span>
                <div class="file-actions">
                  <el-button size="small" text @click="openTimeline(filePath)">看时间线</el-button>
                  <el-button size="small" type="primary" text @click="openPlayer(filePath)">进时光机</el-button>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-block" v-if="activeMission.keyCommits.length > 0">
            <h5>关键提交</h5>
            <div class="commit-list">
              <div v-for="commit in activeMission.keyCommits" :key="commit.commitId" class="commit-item">
                <div class="commit-main">
                  <el-tag size="small" type="info">{{ commit.phase || '关键提交' }}</el-tag>
                  <span class="commit-hash">{{ commit.shortHash }}</span>
                  <span class="commit-message">{{ commit.commitMessage }}</span>
                </div>
                <p v-if="commit.focusReason" class="commit-focus">{{ commit.focusReason }}</p>
                <div class="commit-extra">
                  <span class="commit-lines">+{{ commit.additions || 0 }} / -{{ commit.deletions || 0 }}</span>
                  <span class="commit-types">{{ formatChangeTypes(commit.changeTypes) }}</span>
                </div>
                <div class="commit-hit-files" v-if="commit.hitFiles && commit.hitFiles.length > 0">
                  <span v-for="file in commit.hitFiles" :key="file" class="hit-file">{{ file }}</span>
                </div>
                <div class="commit-meta">
                  <span>{{ commit.authorName }}</span>
                  <span>{{ commit.commitTime }}</span>
                  <span>覆盖 {{ commit.touchedFiles }} 文件</span>
                  <el-button size="small" text @click="openCommit(commit.shortHash)">去提交页</el-button>
                </div>
              </div>
            </div>
          </div>

          <div class="detail-block">
            <h5>学习步骤</h5>
            <ol class="step-list">
              <li v-for="step in activeMission.learningSteps" :key="step">{{ step }}</li>
            </ol>
          </div>

          <div class="detail-block">
            <h5>通过标准</h5>
            <ul class="checkpoint-list">
              <li v-for="checkpoint in activeMission.checkpoints" :key="checkpoint">{{ checkpoint }}</li>
            </ul>
          </div>
        </div>
      </section>

      <section class="learning-suggestions">
        <h4>全局学习建议</h4>
        <ul>
          <li v-for="tip in plan.globalSuggestions" :key="tip">{{ tip }}</li>
        </ul>
      </section>
    </div>

    <el-empty v-else description="暂无学习任务，请先完成仓库分析" />
  </div>
</template>

<style scoped>
.learning-tab {
  min-height: 500px;
}

.learning-layout {
  display: grid;
  gap: var(--spacing-lg);
}

.learning-summary {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg);
}

.learning-summary h3 {
  margin-bottom: var(--spacing-sm);
}

.learning-summary p {
  color: var(--text-secondary);
  margin-bottom: var(--spacing-md);
}

.summary-metrics {
  display: flex;
  gap: var(--spacing-md);
}

.metric-card {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  min-width: 120px;
}

.metric-value {
  display: block;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--color-primary);
  font-family: var(--font-mono);
}

.metric-label {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.mission-panel {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--spacing-lg);
}

.mission-list,
.mission-detail,
.learning-suggestions {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg);
}

.mission-list h4,
.mission-detail h4,
.learning-suggestions h4 {
  margin-bottom: var(--spacing-md);
}

.mission-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-sm);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: var(--spacing-sm) var(--spacing-md);
  margin-bottom: var(--spacing-sm);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.mission-item:hover,
.mission-item.active {
  border-color: var(--color-primary);
  background: var(--bg-hover);
}

.mission-item-title {
  margin-bottom: 4px;
  font-weight: 600;
}

.mission-item-meta {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.detail-objective {
  font-weight: 500;
  margin-bottom: var(--spacing-xs);
}

.detail-reason {
  color: var(--text-secondary);
  margin-bottom: var(--spacing-md);
}

.detail-block {
  margin-bottom: var(--spacing-lg);
}

.detail-block h5 {
  margin-bottom: var(--spacing-sm);
}

.file-list,
.commit-list {
  display: grid;
  gap: var(--spacing-sm);
}

.file-item,
.commit-item {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  padding: var(--spacing-sm) var(--spacing-md);
}

.file-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-md);
}

.file-path {
  font-family: var(--font-mono);
  font-size: 0.85rem;
  word-break: break-all;
}

.file-actions {
  flex-shrink: 0;
  display: flex;
  gap: var(--spacing-xs);
}

.commit-main {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.commit-hash {
  font-family: var(--font-mono);
  color: var(--color-accent);
  font-size: 0.85rem;
}

.commit-message {
  font-size: 0.9rem;
}

.commit-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--text-muted);
  font-size: 0.8rem;
}

.commit-focus {
  margin: 0 0 var(--spacing-xs);
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.commit-extra {
  display: flex;
  gap: var(--spacing-sm);
  font-size: 0.8rem;
  color: var(--text-muted);
  margin-bottom: var(--spacing-xs);
}

.commit-lines {
  font-family: var(--font-mono);
}

.commit-hit-files {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: var(--spacing-xs);
}

.hit-file {
  border: 1px solid var(--border-color);
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 0.75rem;
  color: var(--text-secondary);
  font-family: var(--font-mono);
}

.evidence-list {
  padding-left: 1.2rem;
  display: grid;
  gap: var(--spacing-xs);
}

.step-list,
.checkpoint-list,
.learning-suggestions ul {
  padding-left: 1.2rem;
  display: grid;
  gap: var(--spacing-xs);
}

@media (max-width: 1024px) {
  .mission-panel {
    grid-template-columns: 1fr;
  }
}
</style>
