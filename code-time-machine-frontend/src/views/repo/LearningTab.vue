<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { learningApi } from '@/api'
import { useRepositoryStore } from '@/stores/repository'
import type { LearningDifficulty, LearningMethodUnit, LearningMission, LearningPlan } from '@/types'

const router = useRouter()
const repoStore = useRepositoryStore()

const loading = ref(false)
const plan = ref<LearningPlan | null>(null)
const activeMissionId = ref('')
const selectedAnswers = ref<Record<string, number>>({})
const unitResults = ref<Record<string, { correct: number; total: number; passed: boolean }>>({})

const activeMission = computed(() => {
  if (!plan.value || !activeMissionId.value) {
    return null
  }
  return plan.value.missions.find(m => m.missionId === activeMissionId.value) || null
})

const activeMethodUnits = computed(() => activeMission.value?.methodUnits || [])

const completedMethodUnitCount = computed(() =>
  activeMethodUnits.value.filter(unit => unitResults.value[unit.unitId]?.passed).length
)

const methodMasteryProgress = computed(() => {
  if (activeMethodUnits.value.length === 0) return 0
  return Math.round((completedMethodUnitCount.value / activeMethodUnits.value.length) * 100)
})

async function loadPlan() {
  const repoId = repoStore.currentRepo?.id
  if (!repoId) return

  loading.value = true
  try {
    const result = await learningApi.getPlan(repoId)
    plan.value = result
    selectedAnswers.value = {}
    unitResults.value = {}
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

function answerKey(unitId: string, questionId: string) {
  return `${unitId}::${questionId}`
}

function isUnitUnlocked(unitIndex: number) {
  if (unitIndex <= 0) return true
  for (let i = 0; i < unitIndex; i++) {
    const unitId = activeMethodUnits.value[i]?.unitId
    if (!unitId || !unitResults.value[unitId]?.passed) {
      return false
    }
  }
  return true
}

function requiredCorrect(unit: LearningMethodUnit) {
  const total = unit.quizQuestions.length
  return Math.max(1, Math.min(unit.passThreshold || total, total))
}

function submitUnitQuiz(unit: LearningMethodUnit, unitIndex: number) {
  if (!isUnitUnlocked(unitIndex)) {
    ElMessage.warning('请先通过前置方法单元')
    return
  }
  if (!unit.quizQuestions || unit.quizQuestions.length === 0) {
    ElMessage.warning('该方法单元暂无题目')
    return
  }

  for (const question of unit.quizQuestions) {
    const answer = selectedAnswers.value[answerKey(unit.unitId, question.questionId)]
    if (answer === undefined || answer === null) {
      ElMessage.warning('请先完成该单元所有题目')
      return
    }
  }

  let correct = 0
  for (const question of unit.quizQuestions) {
    const answer = selectedAnswers.value[answerKey(unit.unitId, question.questionId)]
    if (answer === question.correctOptionIndex) {
      correct++
    }
  }

  const total = unit.quizQuestions.length
  const passThreshold = requiredCorrect(unit)
  const passed = correct >= passThreshold
  unitResults.value[unit.unitId] = { correct, total, passed }

  if (passed) {
    if (unitIndex < activeMethodUnits.value.length - 1) {
      ElMessage.success(`通过 ${correct}/${total}，已解锁下一个方法单元`)
    } else {
      ElMessage.success(`通过 ${correct}/${total}，当前任务方法掌握完成`)
    }
    return
  }

  ElMessage.warning(`当前 ${correct}/${total}，需要至少答对 ${passThreshold} 题`)
}

function unitResult(unitId: string) {
  return unitResults.value[unitId]
}

function formatMethodMeta(unit: LearningMethodUnit) {
  const lineText = unit.startLine && unit.endLine
    ? `L${unit.startLine}-L${unit.endLine}`
    : '行号未知'
  const params = unit.parameterCount ?? 0
  return `${lineText} · 参数 ${params} 个 · ${unit.estimatedMinutes} 分钟`
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

          <div class="detail-block" v-if="activeMission.methodUnits && activeMission.methodUnits.length > 0">
            <div class="mastery-header">
              <h5>方法级掌握</h5>
              <span class="mastery-progress-text">
                {{ completedMethodUnitCount }}/{{ activeMission.methodUnits.length }} 已通过
              </span>
            </div>
            <el-progress :percentage="methodMasteryProgress" :stroke-width="10" />

            <div class="mastery-unit-list">
              <div
                v-for="(unit, index) in activeMission.methodUnits"
                :key="unit.unitId"
                class="mastery-unit"
                :class="{ locked: !isUnitUnlocked(index), passed: unitResult(unit.unitId)?.passed }"
              >
                <div class="mastery-unit-header">
                  <div class="mastery-unit-headline">
                    <p class="mastery-unit-title">{{ unit.methodName }}()</p>
                    <p class="mastery-unit-file">{{ unit.filePath }}</p>
                    <p class="mastery-unit-meta">{{ formatMethodMeta(unit) }}</p>
                  </div>
                  <el-tag
                    size="small"
                    :type="unitResult(unit.unitId)?.passed ? 'success' : (isUnitUnlocked(index) ? 'info' : 'warning')"
                  >
                    {{ unitResult(unit.unitId)?.passed ? '已通过' : (isUnitUnlocked(index) ? '待作答' : '已锁定') }}
                  </el-tag>
                </div>

                <p class="mastery-objective">{{ unit.objective }}</p>
                <p class="mastery-reason">{{ unit.importanceReason }}</p>
                <ul class="mastery-hints">
                  <li v-for="hint in unit.learningHints" :key="hint">{{ hint }}</li>
                </ul>

                <div v-if="isUnitUnlocked(index)" class="mastery-quiz">
                  <div v-for="question in unit.quizQuestions" :key="question.questionId" class="quiz-question">
                    <p class="quiz-title">{{ question.question }}</p>
                    <el-radio-group v-model="selectedAnswers[answerKey(unit.unitId, question.questionId)]">
                      <el-radio
                        v-for="(option, optionIndex) in question.options"
                        :key="`${question.questionId}-${option}`"
                        :value="optionIndex"
                      >
                        {{ option }}
                      </el-radio>
                    </el-radio-group>
                    <p v-if="unitResult(unit.unitId)" class="quiz-explain">{{ question.explanation }}</p>
                  </div>

                  <div class="quiz-footer">
                    <span v-if="unitResult(unit.unitId)" class="quiz-result">
                      得分 {{ unitResult(unit.unitId)?.correct }}/{{ unitResult(unit.unitId)?.total }}，
                      通过线 {{ requiredCorrect(unit) }}
                    </span>
                    <el-button size="small" type="primary" @click="submitUnitQuiz(unit, index)">
                      提交本单元测验
                    </el-button>
                  </div>
                </div>

                <p v-else class="unit-locked-tip">先通过上一个方法单元后再解锁。</p>
              </div>
            </div>
          </div>
          <div class="detail-block" v-else>
            <h5>方法级掌握</h5>
            <div class="method-empty">
              当前任务核心文件以文档/配置为主，暂未提取到可测验的方法单元。建议切换到源码文件更多的任务或仓库继续。
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

.mastery-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.mastery-progress-text {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.mastery-unit-list {
  display: grid;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-sm);
}

.mastery-unit {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  padding: var(--spacing-sm) var(--spacing-md);
}

.mastery-unit.locked {
  opacity: 0.78;
}

.mastery-unit.passed {
  border-color: var(--color-success);
}

.mastery-unit-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-xs);
}

.mastery-unit-title {
  font-weight: 600;
  margin-bottom: 2px;
}

.mastery-unit-file {
  font-family: var(--font-mono);
  font-size: 0.78rem;
  color: var(--text-muted);
  margin-bottom: 2px;
  word-break: break-all;
}

.mastery-unit-meta {
  font-size: 0.78rem;
  color: var(--text-muted);
}

.mastery-objective {
  font-size: 0.9rem;
  margin-bottom: 4px;
}

.mastery-reason {
  font-size: 0.82rem;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.mastery-hints {
  padding-left: 1.2rem;
  display: grid;
  gap: 2px;
  margin-bottom: var(--spacing-sm);
  color: var(--text-secondary);
}

.mastery-quiz {
  border-top: 1px dashed var(--border-color);
  padding-top: var(--spacing-sm);
}

.quiz-question {
  margin-bottom: var(--spacing-sm);
}

.quiz-title {
  font-weight: 500;
  margin-bottom: 6px;
}

.quiz-explain {
  margin-top: 6px;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.quiz-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-sm);
}

.quiz-result {
  font-size: 0.82rem;
  color: var(--text-secondary);
}

.unit-locked-tip {
  font-size: 0.82rem;
  color: var(--text-muted);
}

.method-empty {
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
  color: var(--text-secondary);
  padding: var(--spacing-sm) var(--spacing-md);
  font-size: 0.88rem;
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
