<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useRepositoryStore } from '@/stores/repository'
import { fileApi } from '@/api'
import { useTimelinePlayer, detectLanguage, computeChangedLines } from '@/composables/useTimelinePlayer'
import { useChat, generateSessionId } from '@/composables/useChat'
import type { FileTimeline, TimelineCommit } from '@/types'
import { marked } from 'marked'
import hljs from 'highlight.js'

const route = useRoute()
const router = useRouter()
const repoStore = useRepositoryStore()

const repoId = computed(() => Number(route.params.repoId))
const filePath = computed(() => decodeURIComponent(route.params.filePath as string || ''))

const loading = ref(true)
const timeline = ref<FileTimeline | null>(null)
const showChat = ref(false)
const currentCode = ref('')
const previousCode = ref('')
const codeViewerRef = ref<HTMLElement | null>(null)
const previousCodeViewerRef = ref<HTMLElement | null>(null)
const changeKey = ref(0) // 用于触发动画重置
const suggestions = ref<string[]>([]) // 智能推荐问题
const contentLoading = new Set<number>()
let commitLoadToken = 0

// 分屏对比模式
const viewMode = ref<'single' | 'split'>('single')

// 演进故事
const showStoryDialog = ref(false)
const storyLoading = ref(false)
const evolutionStory = ref<{ story: string; keyMilestones: Array<{ commitHash: string; summary: string }> } | null>(null)

// 方法级追踪
const trackingMode = ref<'file' | 'method'>('file')
const methodList = ref<Array<{ name: string; signature: string; startLine: number; endLine: number; content: string }>>([])
const selectedMethod = ref<string | null>(null)
const methodTimelineData = ref<Array<any>>([])
const methodLoading = ref(false)

// 根据追踪模式切换数据源
const commits = computed(() => {
  if (trackingMode.value === 'method' && methodTimelineData.value.length > 0) {
    // 方法模式：使用方法时间线数据，转换为 TimelineCommit 格式
    return methodTimelineData.value.map((item: any, index: number) => ({
      id: item.commitId,
      commitHash: item.commitHash,
      shortHash: item.shortHash,
      commitMessage: item.commitMessage,
      authorName: item.authorName,
      commitTime: item.commitTime,
      commitOrder: index + 1,
      changeType: 'MODIFY' as const,
      additions: item.additions,
      deletions: item.deletions,
      content: item.content,
      aiSummary: undefined,
      changeCategory: undefined
    }))
  }
  // 文件模式：使用原始文件时间线
  return timeline.value?.commits || []
})
const player = useTimelinePlayer(() => commits.value, {
  defaultSpeed: 1500,
  loop: false,
  beforeAdvance: async (nextIndex) => {
    if (trackingMode.value !== 'file') return true
    const nextCommit = commits.value[nextIndex]
    await ensureCommitContent(nextCommit)
    return true
  }
})

const sessionId = generateSessionId()
const chat = useChat(sessionId)
const language = computed(() => detectLanguage(filePath.value))

async function ensureCommitContent(commit: TimelineCommit | null | undefined) {
  if (!commit || commit.content != null) return
  if (commit.changeType === 'DELETE') {
    commit.content = ''
    return
  }
  if (contentLoading.has(commit.id)) return

  contentLoading.add(commit.id)
  try {
    const { content } = await fileApi.getContent(repoId.value, commit.id, filePath.value)
    commit.content = content ?? ''
  } catch (e) {
    console.warn('Failed to load commit content:', e)
  } finally {
    contentLoading.delete(commit.id)
  }
}

onMounted(async () => {
  try {
    if (!repoStore.currentRepo || repoStore.currentRepo.id !== repoId.value) {
      await repoStore.fetchRepoDetail(repoId.value)
    }
    const data = await fileApi.getTimeline(repoId.value, filePath.value, false)
    timeline.value = data
    chat.setContext({ repoId: repoId.value, filePath: filePath.value })
  } catch (e) {
    console.error('Failed to load timeline:', e)
  } finally {
    loading.value = false
  }
})

watch(() => player.currentCommit.value, async (commit, oldCommit) => {
  if (commit) {
    const requestId = ++commitLoadToken
    if (trackingMode.value === 'file') {
      await Promise.all([
        ensureCommitContent(commit),
        ensureCommitContent(oldCommit)
      ])
      if (requestId !== commitLoadToken) return
    }
    const commitContent = commit.content ?? undefined
    if (trackingMode.value === 'file') {
      const nextCommit = commits.value[player.currentIndex.value + 1]
      void ensureCommitContent(nextCommit)
    }
    // 保存之前的代码用于对比
    previousCode.value = oldCommit?.content ?? ''
    
    // 根据追踪模式设置当前代码
    if (trackingMode.value === 'method' && selectedMethod.value && commitContent != null) {
      // 方法模式：从文件中提取方法代码
      const methodContent = extractMethodFromCode(commitContent, selectedMethod.value)
      currentCode.value = methodContent || commitContent
      // 也更新 previousCode 为方法代码
      if (previousCode.value) {
        previousCode.value = extractMethodFromCode(previousCode.value, selectedMethod.value) || previousCode.value
      }
    } else {
      // 文件模式：显示完整文件
      currentCode.value = commitContent == null ? '// 加载中...' : commitContent
    }
    changeKey.value++ // 触发动画重置
    
    // 构建富上下文：元信息 + diff + 代码片段
    let contextParts: string[] = []
    
    // 1. 元信息
    contextParts.push(`文件: ${filePath.value}`)
    contextParts.push(`提交: ${commit.commitMessage}`)
    contextParts.push(`作者: ${commit.authorName}`)
    contextParts.push(`变更: +${commit.additions ?? '?'} / -${commit.deletions ?? '?'} 行`)
    if (commit.aiSummary) {
      contextParts.push(`AI摘要: ${commit.aiSummary}`)
    }
    
    // 2. 获取 diff（如果有上一个版本）
    if (oldCommit?.commitHash) {
      try {
        const diffResult = await fileApi.getDiff(
          repoId.value, 
          oldCommit.commitHash, 
          commit.commitHash, 
          filePath.value
        )
        if (diffResult?.diff) {
          contextParts.push('\n=== Diff（变更内容）===')
          contextParts.push(diffResult.diff.slice(0, 2000)) // 限制 diff 长度
        }
      } catch (e) {
        console.warn('获取diff失败:', e)
      }
    }
    
    // 3. 当前代码片段（用剩余空间）
    const contextSoFar = contextParts.join('\n')
    const remainingSpace = 4500 - contextSoFar.length
    if (remainingSpace > 500 && commitContent) {
      contextParts.push('\n=== 当前代码 ===')
      contextParts.push(commitContent.slice(0, remainingSpace))
    }
    
    // 更新AI聊天上下文
    chat.setContext({
      repoId: repoId.value,
      filePath: filePath.value,
      codeSnippet: contextParts.join('\n')
    })
    
    // 自动滚动到第一个变化行
    await nextTick()
    scrollToFirstChange()
    
    // 加载智能推荐问题
    if (commit.id) {
      try {
        suggestions.value = await chat.getSuggestions(commit.id)
      } catch (e) {
        console.warn('获取推荐问题失败:', e)
        suggestions.value = []
      }
    }
  }
}, { immediate: true })

const highlightedCode = computed(() => {
  if (!currentCode.value) return ''
  try {
    return hljs.highlight(currentCode.value, { language: language.value }).value
  } catch {
    return currentCode.value
  }
})

// 高亮上一版本代码
const highlightedPreviousCode = computed(() => {
  if (!previousCode.value) return ''
  try {
    return hljs.highlight(previousCode.value, { language: language.value }).value
  } catch {
    return previousCode.value
  }
})

// 计算变更行
const changedLines = computed(() => {
  if (!previousCode.value || !currentCode.value) {
    return { added: new Set<number>(), deleted: [] as number[], firstChangedLine: null }
  }
  return computeChangedLines(previousCode.value, currentCode.value)
})

const codeLines = computed(() => {
  const addedLines = changedLines.value.added
  return highlightedCode.value.split('\n').map((content, index) => ({
    number: index + 1,
    content,
    type: addedLines.has(index + 1) ? 'added' as const : 'normal' as const
  }))
})

// 上一版本的代码行（标记删除行）
const previousCodeLines = computed(() => {
  const deletedLines = new Set(changedLines.value.deleted)
  return highlightedPreviousCode.value.split('\n').map((content, index) => ({
    number: index + 1,
    content,
    type: deletedLines.has(index + 1) ? 'deleted' as const : 'normal' as const
  }))
})

// 自动滚动到第一个变化行
function scrollToFirstChange() {
  const firstLine = changedLines.value.firstChangedLine
  if (firstLine && codeViewerRef.value) {
    const lineElement = codeViewerRef.value.querySelector(`[data-line="${firstLine}"]`)
    if (lineElement) {
      lineElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  }
}

// 同步滚动
function syncScroll(source: 'current' | 'previous') {
  if (viewMode.value !== 'split') return
  const sourceRef = source === 'current' ? codeViewerRef.value : previousCodeViewerRef.value
  const targetRef = source === 'current' ? previousCodeViewerRef.value : codeViewerRef.value
  if (sourceRef && targetRef) {
    targetRef.scrollTop = sourceRef.scrollTop
  }
}

// 切换视图模式
function toggleViewMode() {
  viewMode.value = viewMode.value === 'single' ? 'split' : 'single'
}

// 生成演进故事
async function generateEvolutionStory() {
  if (storyLoading.value) return
  storyLoading.value = true
  showStoryDialog.value = true
  try {
    const data = await fileApi.getEvolutionStory(repoId.value, filePath.value)
    evolutionStory.value = data
  } catch (e) {
    console.error('Failed to generate evolution story:', e)
    evolutionStory.value = { story: '生成故事失败，请稍后重试。', keyMilestones: [] }
  } finally {
    storyLoading.value = false
  }
}

// 加载方法列表
async function loadMethods() {
  const commit = player.currentCommit.value
  if (!commit) return
  
  methodLoading.value = true
  try {
    const methods = await fileApi.getMethods(repoId.value, commit.id, filePath.value)
    methodList.value = methods
  } catch (e) {
    console.error('Failed to load methods:', e)
    methodList.value = []
  } finally {
    methodLoading.value = false
  }
}

// 选择方法进行追踪
async function selectMethod(methodName: string | null) {
  selectedMethod.value = methodName
  
  if (!methodName) {
    // 切换回文件模式
    trackingMode.value = 'file'
    methodTimelineData.value = []
    player.goToFirst()
    return
  }
  
  trackingMode.value = 'method'
  methodLoading.value = true
  
  try {
    const timeline = await fileApi.getMethodTimeline(repoId.value, filePath.value, methodName)
    methodTimelineData.value = timeline
  } catch (e) {
    console.error('Failed to load method timeline:', e)
    methodTimelineData.value = []
  } finally {
    methodLoading.value = false
    // 重置播放器到第一帧
    player.goToFirst()
  }
}

// 切换追踪模式
function toggleTrackingMode() {
  if (trackingMode.value === 'method') {
    selectMethod(null)
  } else {
    loadMethods()
  }
}

// 从代码中提取方法内容
function extractMethodFromCode(code: string, methodName: string): string | null {
  if (!code || !methodName) return null
  
  const lines = code.split('\n')
  let startLine = -1
  let braceCount = 0
  let foundStart = false
  let endLine = -1
  
  // 查找方法开始位置
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] as string
    if (!line) continue
    
    // 匹配方法定义（支持 Java、JS、TS）
    if (!foundStart && (
      line.includes(`${methodName}(`) || 
      line.includes(`${methodName} (`) ||
      line.match(new RegExp(`\\b${methodName}\\s*\\(`))
    )) {
      // 检查是否是方法定义（不是调用）
      if (line.includes('function') || line.includes('public') || 
          line.includes('private') || line.includes('protected') ||
          line.includes('=>') || line.match(/^\s*(async\s+)?[a-zA-Z]+\s+\w+\s*\(/)) {
        startLine = i
        foundStart = true
      }
    }
    
    if (foundStart) {
      // 计算大括号
      for (const char of line) {
        if (char === '{') braceCount++
        if (char === '}') braceCount--
      }
      
      // 找到方法结束
      if (braceCount === 0 && line.includes('}')) {
        endLine = i
        break
      }
    }
  }
  
  if (startLine >= 0 && endLine >= startLine) {
    return lines.slice(startLine, endLine + 1).join('\n')
  }
  
  return null
}

const speedOptions = [
  { label: '0.5x', value: 3000 },
  { label: '1x', value: 1500 },
  { label: '2x', value: 750 },
]

function renderMarkdown(text: string) { return marked(text) }
function formatTime(dateStr: string) {
  return new Date(dateStr).toLocaleDateString('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' })
}

function handleKeydown(e: KeyboardEvent) { player.handleKeydown(e) }
onMounted(() => { window.addEventListener('keydown', handleKeydown) })
onUnmounted(() => { window.removeEventListener('keydown', handleKeydown) })

function goBack() { router.push(`/repo/${repoId.value}`) }

const questionInput = ref('')
async function sendQuestion() {
  if (!questionInput.value.trim()) return
  await chat.sendMessageStream(questionInput.value)
  questionInput.value = ''
}
</script>

<template>
  <div class="player-page" v-loading="loading">
    <header class="player-header">
      <div class="header-left">
        <el-button text @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
        <div class="file-info">
          <el-icon class="file-icon"><Document /></el-icon>
          <span class="file-path">{{ filePath }}</span>
        </div>
      </div>
      <div class="header-center">
        <div class="commit-info" v-if="player.currentCommit.value">
          <span class="commit-order">#{{ player.currentIndex.value + 1 }}/{{ player.totalFrames.value }}</span>
          <span class="commit-hash">{{ player.currentCommit.value.shortHash }}</span>
        </div>
      </div>
      <div class="header-right">
        <!-- 方法级追踪 -->
        <el-button-group class="tracking-mode-toggle">
          <el-button :type="trackingMode === 'file' ? 'primary' : 'default'" @click="selectMethod(null)" size="small">
            <el-icon><Document /></el-icon> 文件
          </el-button>
          <el-button :type="trackingMode === 'method' ? 'primary' : 'default'" @click="loadMethods" size="small">
            <el-icon><Operation /></el-icon> 方法
          </el-button>
        </el-button-group>
        
        <!-- 方法选择器 -->
        <el-select
          v-if="methodList.length > 0"
          v-model="selectedMethod"
          placeholder="选择方法"
          size="small"
          style="width: 180px; margin-right: 12px;"
          :loading="methodLoading"
          @change="selectMethod"
          clearable
        >
          <el-option
            v-for="method in methodList"
            :key="method.name"
            :label="method.name + '()'"
            :value="method.name"
          >
            <span>{{ method.name }}()</span>
            <span style="color: #999; font-size: 12px; margin-left: 8px;">
              L{{ method.startLine }}-{{ method.endLine }}
            </span>
          </el-option>
        </el-select>

        <el-button-group class="view-mode-toggle">
          <el-button :type="viewMode === 'single' ? 'primary' : 'default'" @click="viewMode = 'single'" size="small">
            <el-icon><Document /></el-icon> 单栏
          </el-button>
          <el-button :type="viewMode === 'split' ? 'primary' : 'default'" @click="viewMode = 'split'" size="small">
            <el-icon><CopyDocument /></el-icon> 对比
          </el-button>
        </el-button-group>
        <el-button @click="generateEvolutionStory" :loading="storyLoading">
          <el-icon><TrendCharts /></el-icon> 演进故事
        </el-button>
        <el-button :type="showChat ? 'primary' : 'default'" @click="showChat = !showChat">
          <el-icon><ChatDotRound /></el-icon> {{ showChat ? '关闭' : 'AI对话' }}
        </el-button>
      </div>
    </header>

    <main class="player-main">
      <div class="code-section">
        <div class="commit-panel" v-if="player.currentCommit.value">
          <div class="commit-message">{{ player.currentCommit.value.commitMessage }}</div>
          <div class="commit-meta">
            <span><el-icon><User /></el-icon> {{ player.currentCommit.value.authorName }}</span>
            <span><el-icon><Calendar /></el-icon> {{ formatTime(player.currentCommit.value.commitTime) }}</span>
            <span class="stats">
              <span class="additions">+{{ player.currentCommit.value.additions ?? 0 }}</span>
              <span class="deletions">-{{ player.currentCommit.value.deletions ?? 0 }}</span>
            </span>
          </div>
          <div class="ai-summary" v-if="player.currentCommit.value.aiSummary">
            <el-icon><MagicStick /></el-icon>
            <span>{{ player.currentCommit.value.aiSummary }}</span>
          </div>
        </div>
        <div class="code-viewers" :class="{ 'code-viewers--split': viewMode === 'split' }">
          <!-- 旧版本代码（分屏模式） -->
          <div class="code-viewer code-viewer--previous" v-if="viewMode === 'split'" ref="previousCodeViewerRef" @scroll="syncScroll('previous')">
            <div class="code-viewer-label">旧版本</div>
            <div class="code-content" :key="changeKey + '-prev'">
              <div 
                v-for="line in previousCodeLines" 
                :key="line.number" 
                :data-line="line.number"
                class="code-line"
                :class="{ 'code-line--deleted': line.type === 'deleted' }"
              >
                <span class="line-number">{{ line.number }}</span>
                <span class="line-content" v-html="line.content"></span>
              </div>
            </div>
          </div>
          <!-- 新版本代码 -->
          <div class="code-viewer" :class="{ 'code-viewer--current': viewMode === 'split' }" ref="codeViewerRef" @scroll="syncScroll('current')">
            <div class="code-viewer-label" v-if="viewMode === 'split'">新版本</div>
            <div class="code-content" :key="changeKey">
              <div 
                v-for="line in codeLines" 
                :key="line.number" 
                :data-line="line.number"
                class="code-line"
                :class="{ 'code-line--added': line.type === 'added' }"
              >
                <span class="line-number">{{ line.number }}</span>
                <span class="line-content" v-html="line.content"></span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <transition name="slide-right">
        <div class="chat-section" v-show="showChat">
          <div class="chat-header">
            <h3><el-icon><ChatDotRound /></el-icon> AI对话</h3>
            <el-button text size="small" @click="chat.clearMessages">清空</el-button>
          </div>
          <div class="chat-messages">
            <div v-if="!chat.hasMessages.value" class="chat-empty">
              <p>问我关于这段代码的任何问题</p>
              <div class="suggestions">
                <el-button v-for="q in (suggestions.length > 0 ? suggestions : chat.getDefaultSuggestions()).slice(0,3)" :key="q" size="small" @click="chat.sendMessageStream(q)">{{ q }}</el-button>
              </div>
            </div>
            <div v-for="msg in chat.messages.value" :key="msg.id" class="chat-message" :class="[`chat-message--${msg.role}`]">
              <div class="message-avatar">
                <el-icon v-if="msg.role === 'user'"><User /></el-icon>
                <el-icon v-else><MagicStick /></el-icon>
              </div>
              <div class="message-content">
                <div v-if="msg.isLoading && !msg.content" class="loading-dots"><span></span><span></span><span></span></div>
                <div v-else>
                  <span v-html="msg.role === 'assistant' ? renderMarkdown(msg.content) : msg.content"></span>
                  <span v-if="msg.isLoading" class="streaming-cursor">▌</span>
                </div>
              </div>
            </div>
          </div>
          <div class="chat-input">
            <el-input v-model="questionInput" placeholder="问AI任何问题..." @keyup.enter="sendQuestion" :disabled="chat.isLoading.value">
              <template #append><el-button :loading="chat.isLoading.value" @click="sendQuestion"><el-icon><Promotion /></el-icon></el-button></template>
            </el-input>
          </div>
        </div>
      </transition>
    </main>

    <footer class="player-footer">
      <div class="timeline-slider">
        <el-slider v-model="player.currentIndex.value" :max="Math.max(0, player.totalFrames.value - 1)" :show-tooltip="false" @input="player.goTo($event as number)" />
      </div>
      <div class="player-controls">
        <el-button-group>
          <el-button @click="player.goToFirst" :disabled="!player.hasPrev.value"><el-icon><DArrowLeft /></el-icon></el-button>
          <el-button @click="player.prev" :disabled="!player.hasPrev.value"><el-icon><ArrowLeft /></el-icon></el-button>
          <el-button type="primary" @click="player.togglePlay" class="play-button">
            <el-icon v-if="player.isPlaying.value"><VideoPause /></el-icon>
            <el-icon v-else><VideoPlay /></el-icon>
          </el-button>
          <el-button @click="player.next" :disabled="!player.hasNext.value"><el-icon><ArrowRight /></el-icon></el-button>
          <el-button @click="player.goToLast" :disabled="!player.hasNext.value"><el-icon><DArrowRight /></el-icon></el-button>
        </el-button-group>
        <el-select v-model="player.speed.value" size="small" style="width: 80px">
          <el-option v-for="opt in speedOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
      <div class="shortcuts-hint">
        <span><kbd>Space</kbd> 播放</span>
        <span><kbd>←</kbd><kbd>→</kbd> 切换</span>
      </div>
    </footer>

    <!-- 演进故事对话框 -->
    <el-dialog v-model="showStoryDialog" title="📜 文件演进故事" width="600px" :close-on-click-modal="false">
      <div v-loading="storyLoading" class="story-content">
        <template v-if="evolutionStory">
          <div class="story-text" v-html="renderMarkdown(evolutionStory.story)"></div>
          <div class="story-milestones" v-if="evolutionStory.keyMilestones?.length">
            <h4>🏆 关键里程碑</h4>
            <el-timeline>
              <el-timeline-item
                v-for="(milestone, idx) in evolutionStory.keyMilestones"
                :key="idx"
                :timestamp="milestone.commitHash"
                placement="top"
              >
                {{ milestone.summary }}
              </el-timeline-item>
            </el-timeline>
          </div>
        </template>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.player-page { height: 100vh; display: flex; flex-direction: column; background: var(--bg-main); }

.player-header { display: flex; align-items: center; justify-content: space-between; padding: var(--spacing-sm) var(--spacing-lg); background: var(--bg-card); border-bottom: 1px solid var(--border-color); height: 56px; }
.header-left, .header-right { display: flex; align-items: center; gap: var(--spacing-md); }
.file-info { display: flex; align-items: center; gap: var(--spacing-sm); }
.file-icon { color: var(--color-accent); }
.file-path { font-family: var(--font-mono); font-size: 0.9rem; color: var(--text-secondary); }
.commit-info { display: flex; align-items: center; gap: var(--spacing-md); }
.commit-order { background: var(--color-primary-glow); color: var(--color-primary); padding: 4px 12px; border-radius: var(--radius-full); font-family: var(--font-mono); font-size: 0.85rem; }
.commit-hash { font-family: var(--font-mono); color: var(--text-muted); }
.tracking-mode-toggle { margin-right: 8px; }

.player-main { flex: 1; display: flex; overflow: hidden; }
.code-section { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.commit-panel { padding: var(--spacing-md) var(--spacing-lg); background: var(--bg-card); border-bottom: 1px solid var(--border-color); }
.commit-message { font-size: 1rem; font-weight: 500; margin-bottom: var(--spacing-xs); }
.commit-meta { display: flex; gap: var(--spacing-lg); font-size: 0.85rem; color: var(--text-muted); }
.commit-meta > span { display: flex; align-items: center; gap: 4px; }
.additions { color: var(--color-success); }
.deletions { color: var(--color-danger); }
.stats-loading { color: var(--text-muted); font-style: italic; font-size: 0.85rem; }
.ai-summary { display: flex; align-items: flex-start; gap: var(--spacing-sm); padding: var(--spacing-sm) var(--spacing-md); background: var(--color-primary-glow); border-radius: var(--radius-md); font-size: 0.9rem; color: var(--color-primary); margin-top: var(--spacing-sm); }

.code-viewer { flex: 1; overflow: auto; background: var(--code-bg); }
.code-content { font-family: var(--font-mono); font-size: 13px; line-height: 1.6; padding: var(--spacing-md) 0; }
.code-line { display: flex; min-height: 22px; padding: 0 var(--spacing-lg); transition: background-color 0.3s ease; }
.code-line:hover { background: var(--bg-hover); }
.code-line--added { 
  background: rgba(46, 160, 67, 0.15); 
  border-left: 3px solid #2ea043;
  animation: highlight-pulse 2s ease-out;
}
.code-line--added .line-number { color: #2ea043; font-weight: 600; }
@keyframes highlight-pulse {
  0% { background: rgba(46, 160, 67, 0.4); }
  50% { background: rgba(46, 160, 67, 0.25); }
  100% { background: rgba(46, 160, 67, 0.15); }
}
.line-number { flex-shrink: 0; width: 50px; padding-right: var(--spacing-md); text-align: right; color: var(--text-muted); user-select: none; }
.line-content { flex: 1; white-space: pre; }

.chat-section { width: 380px; display: flex; flex-direction: column; background: var(--bg-card); border-left: 1px solid var(--border-color); }
.chat-header { display: flex; justify-content: space-between; align-items: center; padding: var(--spacing-md); border-bottom: 1px solid var(--border-color); }
.chat-header h3 { display: flex; align-items: center; gap: var(--spacing-sm); font-size: 1rem; margin: 0; }
.chat-messages { flex: 1; overflow-y: auto; padding: var(--spacing-md); }
.chat-empty { text-align: center; padding: var(--spacing-xl); color: var(--text-muted); }
.suggestions { display: flex; flex-direction: column; gap: var(--spacing-sm); margin-top: var(--spacing-md); }
.chat-message { display: flex; gap: var(--spacing-sm); margin-bottom: var(--spacing-md); }
.chat-message--user { flex-direction: row-reverse; }
.message-avatar { width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 14px; }
.chat-message--user .message-avatar { background: var(--color-accent); color: var(--bg-primary); }
.chat-message--assistant .message-avatar { background: var(--color-primary); color: var(--bg-primary); }
.message-content { max-width: 85%; padding: var(--spacing-sm) var(--spacing-md); border-radius: var(--radius-md); font-size: 0.9rem; line-height: 1.5; }
.chat-message--user .message-content { background: var(--color-accent); color: var(--bg-primary); }
.chat-message--assistant .message-content { background: var(--bg-secondary); }
.chat-input { padding: var(--spacing-md); border-top: 1px solid var(--border-color); }

.player-footer { padding: var(--spacing-md) var(--spacing-xl); background: var(--bg-card); border-top: 1px solid var(--border-color); }
.timeline-slider { margin-bottom: var(--spacing-md); }
.player-controls { display: flex; align-items: center; justify-content: center; gap: var(--spacing-lg); margin-bottom: var(--spacing-sm); }
.play-button { width: 44px; height: 44px; font-size: 18px; }
.shortcuts-hint { display: flex; justify-content: center; gap: var(--spacing-lg); font-size: 0.8rem; color: var(--text-muted); }
kbd { padding: 2px 6px; background: var(--bg-tertiary); border: 1px solid var(--border-color); border-radius: var(--radius-sm); font-family: var(--font-mono); font-size: 0.75rem; }

.slide-right-enter-active, .slide-right-leave-active { transition: all var(--transition-normal); }
.slide-right-enter-from, .slide-right-leave-to { transform: translateX(100%); opacity: 0; }

/* 分屏对比模式样式 */
.code-viewers { flex: 1; display: flex; overflow: hidden; }
.code-viewers--split { gap: 1px; background: var(--border-color); }
.code-viewers--split .code-viewer { flex: 1; position: relative; }
.code-viewer-label {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 4px 12px;
  background: var(--bg-tertiary);
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-color);
}
.code-viewer--previous .code-viewer-label { color: #f85149; }
.code-viewer--current .code-viewer-label { color: #2ea043; }

/* 删除行样式 */
.code-line--deleted {
  background: rgba(248, 81, 73, 0.15);
  border-left: 3px solid #f85149;
  animation: delete-pulse 2s ease-out;
}
.code-line--deleted .line-number { color: #f85149; font-weight: 600; }
@keyframes delete-pulse {
  0% { background: rgba(248, 81, 73, 0.4); }
  50% { background: rgba(248, 81, 73, 0.25); }
  100% { background: rgba(248, 81, 73, 0.15); }
}

/* 演进故事样式 */
.story-content { min-height: 200px; }
.story-text { line-height: 1.8; color: var(--text-primary); }
.story-text :deep(p) { margin-bottom: 1em; }
.story-milestones { margin-top: var(--spacing-lg); padding-top: var(--spacing-lg); border-top: 1px solid var(--border-color); }
.story-milestones h4 { margin-bottom: var(--spacing-md); font-size: 1rem; }

/* 视图模式切换按钮 */
.view-mode-toggle { margin-right: var(--spacing-sm); }

/* 流式输出光标动画 */
.streaming-cursor {
  display: inline-block;
  color: var(--color-primary);
  animation: blink 1s step-end infinite;
}
@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
</style>
