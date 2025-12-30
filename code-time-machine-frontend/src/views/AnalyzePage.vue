<script setup lang="ts">
import { ref, computed } from 'vue'
import { storeToRefs } from 'pinia'
import { useRouter } from 'vue-router'
import { useRepositoryStore } from '@/stores/repository'
import { DepthPresets } from '@/types'
import { ElMessage } from 'element-plus'
import { Setting, QuestionFilled, ArrowDown, InfoFilled } from '@element-plus/icons-vue'

const router = useRouter()
const repoStore = useRepositoryStore()
const { analyzing, analyzeProgress } = storeToRefs(repoStore)

const repoUrl = ref('')

// 高级选项
const showAdvancedOptions = ref(false)
const analyzeDepth = ref(500) // 默认推荐深度
const customDepth = ref(1000) // 自定义深度默认值
const dateRange = ref<[Date, Date] | null>(null)
const pathFilter = ref('')

// 示例仓库
const exampleRepos = [
  { name: 'Vue.js', url: 'https://github.com/vuejs/vue', icon: 'V', commits: '~3500' },
  { name: 'React', url: 'https://github.com/facebook/react', icon: 'R', commits: '~18000' },
  { name: 'Spring Boot', url: 'https://github.com/spring-projects/spring-boot', icon: 'S', commits: '~50000', warning: '大型项目，建议使用深度限制' }
]

const isValidUrl = computed(() => {
  const pattern = /^https?:\/\/(github\.com|gitlab\.com|gitee\.com)\/[\w-]+\/[\w-]+/
  return pattern.test(repoUrl.value)
})

// 获取当前选中的深度预设信息
const selectedDepthPreset = computed(() => {
  const found = DepthPresets.find(p => p.value === analyzeDepth.value)
  return found ?? DepthPresets[1] // 默认返回推荐选项
})

// 构建分析选项
function buildAnalyzeOptions() {
  const options: {
    depth?: number
    since?: string
    until?: string
    pathFilters?: string[]
    shallow?: boolean
  } = {
    // 如果选择自定义(0)，使用 customDepth 的值
    depth: analyzeDepth.value === 0 ? customDepth.value : analyzeDepth.value,
    shallow: true
  }
  
  // 时间范围
  if (dateRange.value && dateRange.value[0] && dateRange.value[1]) {
    options.since = dateRange.value[0].toISOString().slice(0, 19)
    options.until = dateRange.value[1].toISOString().slice(0, 19)
  }
  
  // 路径过滤
  if (pathFilter.value.trim()) {
    options.pathFilters = pathFilter.value.split(',').map(p => p.trim()).filter(p => p)
  }
  
  return options
}

async function startAnalyze() {
  if (!repoUrl.value.trim()) {
    ElMessage.warning('请输入仓库地址')
    return
  }
  
  if (!isValidUrl.value) {
    ElMessage.warning('请输入有效的Git仓库地址')
    return
  }

  try {
    const options = buildAnalyzeOptions()
    const repo = await repoStore.analyzeRepository(repoUrl.value, options)
    ElMessage.success('分析完成')
    
    setTimeout(() => {
      router.push(`/repo/${repo.id}`)
    }, 500)
  } catch (error) {
    const message = (error as Error).message
    ElMessage.error(`分析失败: ${message}`)
  }
}

function selectExample(url: string) {
  repoUrl.value = url
  // 对于大型项目，自动打开高级选项提示
  const repo = exampleRepos.find(r => r.url === url)
  if (repo?.warning) {
    showAdvancedOptions.value = true
    ElMessage.info(repo.warning)
  }
}

function goBack() {
  router.push('/')
}
</script>

<template>
  <div class="analyze-page">
    <!-- 导航栏 -->
    <header class="header">
      <div class="header-content">
        <div class="logo" @click="goBack" style="cursor: pointer;">
          <el-icon class="logo-icon"><Clock /></el-icon>
          <span class="logo-text">Code Time Machine</span>
        </div>
      </div>
    </header>

    <main class="main-content">
      <div class="analyze-container">
        <!-- 标题 -->
        <div class="page-header">
          <h1>
            <el-icon><Search /></el-icon>
            分析Git仓库
          </h1>
          <p>输入仓库地址，开启代码考古之旅</p>
        </div>

        <!-- 输入区域 -->
        <div class="input-section">
          <div class="url-input-wrapper">
            <el-input
              v-model="repoUrl"
              size="large"
              placeholder="https://github.com/username/repository"
              :disabled="analyzing"
              @keyup.enter="startAnalyze"
            >
              <template #prefix>
                <el-icon><Link /></el-icon>
              </template>
            </el-input>
            <el-button 
              type="primary" 
              size="large"
              :loading="analyzing"
              :disabled="!repoUrl.trim()"
              @click="startAnalyze"
            >
              {{ analyzing ? '分析中...' : '开始分析' }}
            </el-button>
          </div>
          
          <!-- 高级选项切换 -->
          <div class="advanced-toggle" @click="showAdvancedOptions = !showAdvancedOptions">
            <el-icon><Setting /></el-icon>
            <span>{{ showAdvancedOptions ? '隐藏高级选项' : '显示高级选项' }}</span>
            <el-icon class="toggle-arrow" :class="{ 'is-open': showAdvancedOptions }">
              <ArrowDown />
            </el-icon>
          </div>
          
          <!-- 高级选项面板 -->
          <transition name="slide-down">
            <div v-show="showAdvancedOptions" class="advanced-options">
              <!-- 分析深度 -->
              <div class="option-group">
                <div class="option-label">
                  <span>分析深度</span>
                  <el-tooltip content="限制分析的提交数量，可以大幅减少克隆时间和磁盘占用" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
                <div class="depth-options">
                  <div 
                    v-for="preset in DepthPresets" 
                    :key="preset.value"
                    class="depth-card"
                    :class="{ active: analyzeDepth === preset.value }"
                    @click="analyzeDepth = preset.value"
                  >
                    <div class="depth-label">{{ preset.label }}</div>
                    <div class="depth-desc">{{ preset.desc }}</div>
                    <div class="depth-time">{{ preset.time }}</div>
                  </div>
                </div>
                
                <!-- 自定义次数输入框 -->
                <div v-if="analyzeDepth === 0" class="custom-depth-input">
                  <el-input-number
                    v-model="customDepth"
                    :min="1"
                    :max="100000"
                    :step="100"
                    size="large"
                    placeholder="输入提交次数"
                  />
                  <span class="custom-hint">次提交</span>
                </div>
              </div>
              
              <!-- 时间范围 -->
              <div class="option-group">
                <div class="option-label">
                  <span>时间范围（可选）</span>
                  <el-tooltip content="只分析指定时间段内的提交" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
                <el-date-picker
                  v-model="dateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  style="width: 100%"
                />
              </div>
              
              <!-- 路径过滤 -->
              <div class="option-group">
                <div class="option-label">
                  <span>路径过滤（可选）</span>
                  <el-tooltip content="只分析指定目录或文件，多个用逗号分隔" placement="top">
                    <el-icon class="help-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </div>
                <el-input
                  v-model="pathFilter"
                  placeholder="如: src/main/java/, *.py"
                />
              </div>
              
              <!-- 当前配置摘要 -->
              <div class="config-summary">
                <el-icon><InfoFilled /></el-icon>
                <span>
                  当前配置: 分析最近 <strong>{{ analyzeDepth === -1 ? '全部' : (analyzeDepth === 0 ? customDepth : analyzeDepth) }}</strong> 次提交
                  <template v-if="dateRange">，限制时间范围</template>
                  <template v-if="pathFilter.trim()">，过滤路径</template>
                  ，预计耗时 <strong>{{ analyzeDepth === 0 ? '取决于数量' : selectedDepthPreset.time }}</strong>
                </span>
              </div>
            </div>
          </transition>

          <!-- 进度条 -->
          <div v-if="analyzing" class="progress-section">
            <el-progress 
              :percentage="Math.max(0, Math.min(analyzeProgress, 100))" 
              :stroke-width="8"
              :show-text="false"
            />
            <div class="progress-status">
              <span class="status-text">
                <span class="loading-dots">
                  <span></span><span></span><span></span>
                </span>
                正在克隆仓库并分析提交历史...
              </span>
              <span class="progress-percent">{{ Math.max(0, Math.floor(analyzeProgress)) }}%</span>
            </div>
          </div>
        </div>

        <!-- 示例仓库 -->
        <div class="example-section">
          <h3>试试这些热门项目</h3>
          <div class="example-list">
            <div 
              v-for="repo in exampleRepos" 
              :key="repo.url"
              class="example-card"
              @click="selectExample(repo.url)"
            >
              <span class="example-icon">{{ repo.icon }}</span>
              <span class="example-name">{{ repo.name }}</span>
              <el-icon class="example-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>

        <!-- 已分析的仓库列表 -->
        <div class="history-section" v-if="repoStore.repositories.length > 0">
          <h3>
            <el-icon><Clock /></el-icon>
            已分析的仓库
          </h3>
          <div class="history-list">
            <div 
              v-for="repo in repoStore.repositories" 
              :key="repo.id"
              class="history-card"
              @click="router.push(`/repo/${repo.id}`)"
            >
              <div class="history-info">
                <el-icon class="history-icon"><Folder /></el-icon>
                <div class="history-details">
                  <span class="history-name">{{ repo.name }}</span>
                  <span class="history-meta">{{ repo.totalCommits }} commits</span>
                </div>
              </div>
              <el-tag 
                :type="repo.status === 2 ? 'success' : repo.status === 1 ? 'warning' : 'info'"
                size="small"
              >
                {{ repo.status === 2 ? '已完成' : repo.status === 1 ? '分析中' : '待分析' }}
              </el-tag>
            </div>
          </div>
        </div>

        <!-- 功能说明 -->
        <div class="features-section">
          <div class="feature-item">
            <el-icon><DocumentCopy /></el-icon>
            <div>
              <h4>支持的平台</h4>
              <p>GitHub、GitLab、Gitee</p>
            </div>
          </div>
          <div class="feature-item">
            <el-icon><Lock /></el-icon>
            <div>
              <h4>隐私保护</h4>
              <p>仅分析公开仓库，代码本地处理</p>
            </div>
          </div>
          <div class="feature-item">
            <el-icon><Timer /></el-icon>
            <div>
              <h4>分析速度</h4>
              <p>中型项目约需1-3分钟</p>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.analyze-page {
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--spacing-xl);
  height: 64px;
  display: flex;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.logo-icon {
  font-size: 24px;
  color: var(--color-primary);
}

.logo-text {
  font-size: 1.1rem;
  font-weight: 600;
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.main-content {
  padding: var(--spacing-2xl) var(--spacing-xl);
}

.analyze-container {
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: var(--spacing-2xl);
}

.page-header h1 {
  font-size: 2rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-sm);
  margin-bottom: var(--spacing-sm);
}

.page-header h1 .el-icon {
  color: var(--color-primary);
}

.page-header p {
  color: var(--text-secondary);
}

.input-section {
  margin-bottom: var(--spacing-2xl);
}

.url-input-wrapper {
  display: flex;
  gap: var(--spacing-md);
}

.url-input-wrapper .el-input {
  flex: 1;
}

.url-input-wrapper .el-button {
  padding: 0 32px;
}

.progress-section {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
}

.progress-status {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--spacing-md);
}

.status-text {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.progress-percent {
  color: var(--color-primary);
  font-family: var(--font-mono);
  font-weight: 600;
}

.example-section {
  margin-bottom: var(--spacing-2xl);
}

.example-section h3 {
  font-size: 1rem;
  color: var(--text-secondary);
  margin-bottom: var(--spacing-md);
  text-align: center;
}

.example-list {
  display: flex;
  gap: var(--spacing-md);
  justify-content: center;
  flex-wrap: wrap;
}

.example-card {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-sm) var(--spacing-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.example-card:hover {
  border-color: var(--color-primary);
  background: var(--bg-hover);
}

.example-icon {
  font-size: 1.2rem;
}

.example-name {
  font-weight: 500;
}

.example-arrow {
  color: var(--text-muted);
  font-size: 12px;
}

.history-section {
  margin-bottom: var(--spacing-2xl);
}

.history-section h3 {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 1.1rem;
  margin-bottom: var(--spacing-md);
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.history-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-md) var(--spacing-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.history-card:hover {
  border-color: var(--color-primary);
  background: var(--bg-hover);
}

.history-info {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
}

.history-icon {
  color: var(--color-warning);
  font-size: 20px;
}

.history-details {
  display: flex;
  flex-direction: column;
}

.history-name {
  font-weight: 500;
}

.history-meta {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.features-section {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--spacing-lg);
  padding: var(--spacing-xl);
  background: var(--bg-card);
  border-radius: var(--radius-lg);
  border: 1px solid var(--border-color);
}

.feature-item {
  display: flex;
  gap: var(--spacing-md);
  align-items: flex-start;
}

.feature-item .el-icon {
  font-size: 24px;
  color: var(--color-accent);
  flex-shrink: 0;
}

.feature-item h4 {
  font-size: 0.9rem;
  margin-bottom: 4px;
}

.feature-item p {
  font-size: 0.8rem;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .url-input-wrapper {
    flex-direction: column;
  }
  
  .features-section {
    grid-template-columns: 1fr;
  }
  
  .depth-options {
    grid-template-columns: 1fr !important;
  }
}

/* 高级选项样式 */
.advanced-toggle {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin-top: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 0.9rem;
  transition: color var(--transition-fast);
}

.advanced-toggle:hover {
  color: var(--color-primary);
}

.toggle-arrow {
  margin-left: auto;
  transition: transform var(--transition-fast);
}

.toggle-arrow.is-open {
  transform: rotate(180deg);
}

.advanced-options {
  margin-top: var(--spacing-lg);
  padding: var(--spacing-lg);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
}

.option-group {
  margin-bottom: var(--spacing-lg);
}

.option-group:last-child {
  margin-bottom: 0;
}

.option-label {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  margin-bottom: var(--spacing-sm);
  font-weight: 500;
  font-size: 0.9rem;
}

.help-icon {
  color: var(--text-muted);
  font-size: 14px;
  cursor: help;
}

.depth-options {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--spacing-sm);
}

.depth-card {
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  text-align: center;
}

.depth-card:hover {
  border-color: var(--color-primary);
  background: var(--bg-hover);
}

.depth-card.active {
  border-color: var(--color-primary);
  background: var(--color-primary-glow);
}

.depth-label {
  font-weight: 600;
  font-size: 0.85rem;
  margin-bottom: 4px;
}

.depth-desc {
  font-size: 0.75rem;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.depth-time {
  font-size: 0.7rem;
  color: var(--text-muted);
  font-family: var(--font-mono);
}

.custom-depth-input {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-md);
  padding: var(--spacing-md);
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  border: 2px solid var(--color-primary);
}

.custom-hint {
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.config-summary {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  padding: var(--spacing-md);
  background: var(--color-primary-glow);
  border-radius: var(--radius-md);
  font-size: 0.85rem;
  color: var(--text-secondary);
  margin-top: var(--spacing-lg);
}

.config-summary strong {
  color: var(--color-primary);
}

/* 动画 */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s ease;
  overflow: hidden;
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  max-height: 0;
  margin-top: 0;
  padding-top: 0;
  padding-bottom: 0;
}

.slide-down-enter-to,
.slide-down-leave-from {
  opacity: 1;
  max-height: 500px;
}
</style>
