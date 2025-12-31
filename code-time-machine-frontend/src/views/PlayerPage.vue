<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useVirtualList } from '@vueuse/core'
import { useRoute, useRouter } from 'vue-router'
import { useRepositoryStore } from '@/stores/repository'
import { fileApi, commitApi } from '@/api'
import { useTimelinePlayer, detectLanguage, computeChangedLines } from '@/composables/useTimelinePlayer'
import { useChat, generateDeterministicSessionId } from '@/composables/useChat'
import type { FileTimeline, TimelineCommit, AiAnalysis, ChangeCategory } from '@/types'
import { ChangeCategoryMap } from '@/types'
import { ElMessage } from 'element-plus'
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
const changeKey = ref(0)
const suggestions = ref<string[]>([])
const contentLoading = new Map<number, Promise<void>>()
let commitLoadToken = 0

const highlightCache = new Map<string, string>()

const viewMode = ref<'single' | 'split'>('single')

const showStoryDialog = ref(false)
const storyLoading = ref(false)
const evolutionStory = ref<{ story: string; keyMilestones: Array<{ commitHash: string; summary: string }> } | null>(null)

const trackingMode = ref<'file' | 'method'>('file')
const methodList = ref<Array<{ name: string; signature: string; startLine: number; endLine: number; content: string }>>([])
const selectedMethod = ref<string | null>(null)
const methodTimelineData = ref<Array<any>>([])
const methodLoading = ref(false)

const currentAnalysis = ref<AiAnalysis | null>(null)
const analysisLoading = ref(false)
const showAnalysisPopover = ref(false)

type PlayerStyleType = 'cinematic' | 'classic' | 'neon' | 'glassmorphism' | 'softui' | 'softui-dark'
const playerStyle = ref<PlayerStyleType>(
  (localStorage.getItem('playerStyle') as PlayerStyleType) || 'cinematic'
)

const styleOptions = [
  { value: 'cinematic', label: '🎬 电影', desc: 'Art Deco 风格' },
  { value: 'classic', label: '💻 经典', desc: '简洁现代' },
  { value: 'neon', label: '🌈 霓虹', desc: '赛博朋克' },
  { value: 'glassmorphism', label: '🪟 玻璃', desc: '毛玻璃效果' },
  { value: 'softui', label: '🧸 柔和', desc: 'Soft UI 浅色' },
  { value: 'softui-dark', label: '🌙 柔夜', desc: 'Soft UI 深色' }
]

function onStyleChange(value: PlayerStyleType) {
  playerStyle.value = value
  localStorage.setItem('playerStyle', value)
}

const commits = computed(() => {
  if (trackingMode.value === 'method' && methodTimelineData.value.length > 0) {
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

const sessionId = generateDeterministicSessionId(
  Number(route.params.repoId),
  decodeURIComponent(route.params.filePath as string || '')
)
const chat = useChat(sessionId)
const language = computed(() => detectLanguage(filePath.value))

const PRELOAD_WINDOW_SIZE = 5

function ensureCommitContent(commit: TimelineCommit | null | undefined): Promise<void> {
  if (!commit || commit.content != null) return Promise.resolve()
  if (commit.changeType === 'DELETE') {
    commit.content = ''
    return Promise.resolve()
  }
  const existing = contentLoading.get(commit.id)
  if (existing) return existing

  const loadingPromise = (async () => {
    try {
      const { content } = await fileApi.getContent(repoId.value, commit.id, filePath.value)
      commit.content = content ?? ''
    } catch (e) {
      console.warn('Failed to load commit content:', e)
    } finally {
      contentLoading.delete(commit.id)
    }
  })()
  contentLoading.set(commit.id, loadingPromise)
  return loadingPromise
}

let batchLoadingPromise: Promise<void> | null = null

function preloadWindow(centerIndex: number) {
  if (trackingMode.value !== 'file') return

  const commitList = commits.value
  const start = Math.max(0, centerIndex - PRELOAD_WINDOW_SIZE)
  const end = Math.min(commitList.length - 1, centerIndex + PRELOAD_WINDOW_SIZE)

  const toLoad: number[] = []
  for (let i = start; i <= end; i++) {
    if (i === centerIndex) continue

    const commit = commitList[i]
    if (commit && commit.content == null && !contentLoading.has(commit.id)) {
      toLoad.push(commit.id)
    }
  }

  if (toLoad.length === 0) return

  if (batchLoadingPromise) return

  batchLoadingPromise = (async () => {
    try {
      const results = await fileApi.getBatchContent(repoId.value, toLoad, filePath.value)

      for (const [commitIdStr, data] of Object.entries(results)) {
        const commitId = Number(commitIdStr)
        const commit = commitList.find(c => c.id === commitId)
        if (commit && commit.content == null) {
          commit.content = data.content
          if (data.content) {
            getCachedHighlight(commit.id, data.content)
          }
        }
      }
    } catch (e) {
      console.warn('Batch preload failed, falling back to individual requests:', e)
      for (const commitId of toLoad) {
        const commit = commitList.find(c => c.id === commitId)
        if (commit && commit.content == null) {
          void ensureCommitContent(commit)
        }
      }
    } finally {
      batchLoadingPromise = null
    }
  })()
}


function getCachedHighlight(commitId: number, content: string): string {
  if (!content) return ''

  const cacheKey = `${commitId}_${content.length}`
  
  const cached = highlightCache.get(cacheKey)
  if (cached !== undefined) {
    return cached
  }

  let highlighted: string
  try {
    highlighted = hljs.highlight(content, { language: language.value }).value
  } catch {
    highlighted = content
  }

  highlightCache.set(cacheKey, highlighted)
  return highlighted
}

function enrichHistoryMessages() {
  const commitList = commits.value
  if (commitList.length === 0) return

  const commitMap = new Map<number, { order: number; shortHash: string; message: string }>()
  commitList.forEach((c, idx) => {
    commitMap.set(c.id, { order: idx + 1, shortHash: c.shortHash, message: c.commitMessage })
  })

  for (const msg of chat.messages.value) {
    if (msg.role === 'user' && msg.commitId && !msg.commitOrder) {
      const info = commitMap.get(msg.commitId)
      if (info) {
        msg.commitOrder = info.order
        msg.shortHash = info.shortHash
      }
    }
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
    await chat.loadHistory()
    enrichHistoryMessages()
    preloadWindow(0)
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
      preloadWindow(player.currentIndex.value)
      await Promise.all([
        ensureCommitContent(commit),
        ensureCommitContent(oldCommit)
      ])
      if (requestId !== commitLoadToken) return
    }
    const commitContent = commit.content ?? undefined
    previousCode.value = oldCommit?.content ?? ''

    console.log('[Watch] trackingMode:', trackingMode.value)
    console.log('[Watch] selectedMethod:', selectedMethod.value)
    console.log('[Watch] commitContent 行数:', commitContent?.split('\n').length)

    if (trackingMode.value === 'method' && selectedMethod.value && commitContent != null) {
      const isFullFile = commitContent.split('\n').length > 100 || commitContent.includes('import ')
      console.log('[Watch] isFullFile:', isFullFile, '(>100行 或 包含import)')
      
      if (isFullFile) {
        const methodContent = extractMethodFromCode(commitContent, selectedMethod.value)
        console.log('[Watch] 前端提取方法行数:', methodContent?.split('\n').length)
        currentCode.value = methodContent || commitContent
        if (previousCode.value) {
          previousCode.value = extractMethodFromCode(previousCode.value, selectedMethod.value) || previousCode.value
        }
      } else {
        console.log('[Watch] 直接使用后端返回的方法内容')
        currentCode.value = commitContent
      }
      console.log('[Watch] 最终 currentCode 行数:', currentCode.value.split('\n').length)
    } else {
      currentCode.value = commitContent == null ? '// 加载中...' : commitContent
    }

    await nextTick()
    await nextTick()
    if (requestId !== commitLoadToken) return
    scrollToFirstChange()

    let contextParts: string[] = []

    contextParts.push(`文件: ${filePath.value}`)
    contextParts.push(`提交: ${commit.commitMessage}`)
    contextParts.push(`作者: ${commit.authorName}`)
    contextParts.push(`变更: +${commit.additions ?? '?'} / -${commit.deletions ?? '?'} 行`)
    if (commit.aiSummary) {
      contextParts.push(`AI摘要: ${commit.aiSummary}`)
    }

    if (oldCommit?.commitHash) {
      fileApi.getDiff(
        repoId.value,
        oldCommit.commitHash,
        commit.commitHash,
        filePath.value
      ).then((diffResult) => {
        if (requestId !== commitLoadToken) return
        if (diffResult?.diff) {
          const { additions, deletions } = countDiffStats(diffResult.diff)
          commit.additions = additions
          commit.deletions = deletions
        }
      }).catch((e) => {
        console.warn('获取diff失败:', e)
      })
    }

    const contextSoFar = contextParts.join('\n')
    const remainingSpace = 4500 - contextSoFar.length
    if (remainingSpace > 500 && commitContent) {
      contextParts.push('\n=== 当前代码 ===')
      contextParts.push(commitContent.slice(0, remainingSpace))
    }

    chat.setContext({
      repoId: repoId.value,
      commitId: commit.id,
      commitOrder: player.currentIndex.value + 1,
      shortHash: commit.shortHash,
      filePath: filePath.value,
      codeSnippet: contextParts.join('\n')
    })

    if (commit.id) {
      chat.getSuggestions(commit.id).then((result) => {
        if (requestId !== commitLoadToken) return
        suggestions.value = result
      }).catch((e) => {
        console.warn('获取推荐问题失败:', e)
        suggestions.value = []
      })
    }
  }
}, { immediate: true })

const highlightedCode = computed(() => {
  const commit = player.currentCommit.value
  if (!commit || !currentCode.value) return ''
  return getCachedHighlight(commit.id, currentCode.value)
})

const highlightedPreviousCode = computed(() => {
  const prevCommit = player.previousCommit.value
  if (!prevCommit || !previousCode.value) return ''
  return getCachedHighlight(prevCommit.id, previousCode.value)
})

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

const previousCodeLines = computed(() => {
  const deletedLines = new Set(changedLines.value.deleted)
  return highlightedPreviousCode.value.split('\n').map((content, index) => ({
    number: index + 1,
    content,
    type: deletedLines.has(index + 1) ? 'deleted' as const : 'normal' as const
  }))
})

const VIRTUAL_SCROLL_THRESHOLD = 800
const CODE_LINE_HEIGHT = 22

const useVirtualCodeViewer = computed(() => codeLines.value.length > VIRTUAL_SCROLL_THRESHOLD)
const useVirtualPreviousViewer = computed(() => previousCodeLines.value.length > VIRTUAL_SCROLL_THRESHOLD)

const {
  list: virtualCodeLines,
  containerProps: codeContainerProps,
  wrapperProps: codeWrapperProps,
  scrollTo: scrollToCodeLine
} = useVirtualList(codeLines, { itemHeight: CODE_LINE_HEIGHT })

const {
  list: virtualPreviousCodeLines,
  containerProps: previousContainerProps,
  wrapperProps: previousWrapperProps,
  scrollTo: scrollToPreviousLine
} = useVirtualList(previousCodeLines, { itemHeight: CODE_LINE_HEIGHT })


function scrollToFirstChange() {
  const firstLine = changedLines.value.firstChangedLine

  if (!firstLine) {
    return
  }

  if (useVirtualCodeViewer.value) {
    const targetIndex = Math.max(0, firstLine - 1)
    scrollToCodeLine(targetIndex)
    if (viewMode.value === 'split' && useVirtualPreviousViewer.value) {
      scrollToPreviousLine(targetIndex)
    }
    return
  }

  if (viewMode.value === 'split') {
    const scrollBehavior: ScrollBehavior = player.isPlaying.value ? 'auto' : 'smooth'

    const currentViewer = codeViewerRef.value
    const previousViewer = previousCodeViewerRef.value

    if (currentViewer) {
      const lineElement = currentViewer.querySelector(`[data-line="${firstLine}"]`) as HTMLElement

      let targetScrollTop: number

      if (lineElement) {
        const containerRect = currentViewer.getBoundingClientRect()
        const elementRect = lineElement.getBoundingClientRect()
        const relativeTop = elementRect.top - containerRect.top + currentViewer.scrollTop
        targetScrollTop = Math.max(0, relativeTop - containerRect.height / 2)
      } else {
        const firstLineEl = currentViewer.querySelector('[data-line="1"]') as HTMLElement
        const lineHeight = firstLineEl?.offsetHeight || 22
        const containerHeight = currentViewer.clientHeight
        targetScrollTop = Math.max(0, (firstLine - 1) * lineHeight - containerHeight / 2)
      }

      currentViewer.scrollTo({ top: targetScrollTop, behavior: scrollBehavior })
      if (previousViewer) {
        previousViewer.scrollTo({ top: targetScrollTop, behavior: scrollBehavior })
      }
    }
  } else {
    if (codeViewerRef.value) {
      const lineElement = codeViewerRef.value.querySelector(`[data-line="${firstLine}"]`)
      if (lineElement) {
        lineElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    }
  }
}

function syncScroll(source: 'current' | 'previous') {
  if (viewMode.value !== 'split') return
  const sourceRef = source === 'current' ? codeViewerRef.value : previousCodeViewerRef.value
  const targetRef = source === 'current' ? previousCodeViewerRef.value : codeViewerRef.value
  if (sourceRef && targetRef) {
    targetRef.scrollTop = sourceRef.scrollTop
  }
}

function toggleViewMode() {
  viewMode.value = viewMode.value === 'single' ? 'split' : 'single'
}

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

async function selectMethod(methodName: string | null) {
  selectedMethod.value = methodName

  if (!methodName) {
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
    player.goToFirst()
  }
}

function toggleTrackingMode() {
  if (trackingMode.value === 'method') {
    selectMethod(null)
  } else {
    loadMethods()
  }
}

function extractMethodFromCode(code: string, methodName: string): string | null {
  if (!code || !methodName) return null

  const lines = code.split('\n')
  let startLine = -1
  let braceCount = 0
  let foundStart = false
  let endLine = -1

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] as string
    if (!line) continue

    if (!foundStart && (
      line.includes(`${methodName}(`) ||
      line.includes(`${methodName} (`) ||
      line.match(new RegExp(`\\b${methodName}\\s*\\(`))
    )) {
      const trimmed = line.trim()
      const isDefinition = 
        trimmed.startsWith('function ') ||
        trimmed.startsWith('async function ') ||
        trimmed.startsWith('public ') ||
        trimmed.startsWith('private ') ||
        trimmed.startsWith('protected ') ||
        trimmed.startsWith('static ') ||
        line.includes('=>') ||
        trimmed.match(new RegExp(`^(async\\s+)?${methodName}\\s*[(<]`)) ||
        line.match(/^\s*(public|private|protected|static|final|\w+)\s+\w+\s+\w+\s*\(/) ||
        trimmed.startsWith('def ') ||
        trimmed.startsWith('async def ') ||
        trimmed.startsWith('func ') ||
        line.match(/^\s*(const|let|var)\s+\w+\s*=/)

      if (isDefinition) {
        startLine = i
        foundStart = true
      }
    }

    if (foundStart) {
      for (const char of line) {
        if (char === '{') braceCount++
        if (char === '}') braceCount--
      }

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

function countDiffStats(diffText: string): { additions: number; deletions: number } {
  let additions = 0
  let deletions = 0
  for (const line of diffText.split('\n')) {
    if (!line) continue
    if (line.startsWith('+++') || line.startsWith('---') || line.startsWith('@@')) continue
    if (line.startsWith('+')) {
      additions++
    } else if (line.startsWith('-')) {
      deletions++
    }
  }
  return { additions, deletions }
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

function jumpToCommitOrder(commitOrder: number | undefined) {
  if (!commitOrder) return
  const targetIndex = commitOrder - 1
  if (targetIndex >= 0 && targetIndex < player.totalFrames.value) {
    player.goTo(targetIndex)
  }
}

interface MessageGroup {
  commitOrder?: number
  shortHash?: string
  commitMessage?: string
  messages: typeof chat.messages.value
}

const groupedMessages = computed((): MessageGroup[] => {
  const messages = chat.messages.value
  if (messages.length === 0) return []

  const groups: MessageGroup[] = []
  let currentGroup: MessageGroup | null = null

  for (const msg of messages) {
    const msgCommitOrder = msg.role === 'user' ? msg.commitOrder : undefined

    if (msg.role === 'user' && (
      !currentGroup ||
      currentGroup.commitOrder !== msgCommitOrder
    )) {
      const commitInfo = commits.value.find(c => c.shortHash === msg.shortHash)
      currentGroup = {
        commitOrder: msgCommitOrder,
        shortHash: msg.shortHash,
        commitMessage: commitInfo?.commitMessage,
        messages: []
      }
      groups.push(currentGroup)
    }

    if (!currentGroup) {
      currentGroup = { messages: [] }
      groups.push(currentGroup)
    }

    currentGroup.messages.push(msg)
  }

  return groups
})

async function fetchAnalysis() {
  const commit = player.currentCommit.value
  if (!commit) return

  if (currentAnalysis.value?.commitId === commit.id) {
    showAnalysisPopover.value = !showAnalysisPopover.value
    return
  }

  analysisLoading.value = true
  try {
    try {
      const analysis = await commitApi.getAiAnalysis(commit.id)
      currentAnalysis.value = analysis
      showAnalysisPopover.value = true
      return
    } catch (e: any) {
      console.log('No existing analysis, triggering new one...')
    }

    ElMessage.info('正在生成 AI 分析...')
    const analysis = await commitApi.triggerAnalysis(commit.id)
    currentAnalysis.value = analysis
    showAnalysisPopover.value = true
    ElMessage.success('AI 分析完成')
  } catch (e: any) {
    console.error('AI analysis failed:', e)
    ElMessage.error('AI 分析失败')
  } finally {
    analysisLoading.value = false
  }
}

function getCategoryInfo(category: string | undefined) {
  if (!category) return null
  return ChangeCategoryMap[category as ChangeCategory] || null
}

function renderStars(score: number | undefined): string {
  if (!score) return ''
  const filled = Math.round((score / 10) * 5)
  return '★'.repeat(filled) + '☆'.repeat(5 - filled)
}

watch(() => player.currentCommit.value?.id, () => {
  currentAnalysis.value = null
  showAnalysisPopover.value = false
})
</script>

<template>
  <div class="player-page" :class="[`player-page--${playerStyle}`, { 'is-playing': player.isPlaying.value }]" v-loading="loading">
    <header class="player-header">
      <div class="header-left">
        <el-button text @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
        <div class="file-info">
          <el-icon class="file-icon"><Document /></el-icon>
          <span class="file-path">{{ filePath }}</span>
        </div>
        <div class="commit-info" v-if="player.currentCommit.value">
          <span class="commit-order">#{{ player.currentIndex.value + 1 }}/{{ player.totalFrames.value }}</span>
          <span class="commit-hash">{{ player.currentCommit.value.shortHash }}</span>
        </div>
      </div>
      <div class="header-right">
        <div class="tracking-controls">
          <el-button-group class="tracking-mode-toggle">
            <el-button :type="trackingMode === 'file' ? 'primary' : 'default'" @click="selectMethod(null)" size="small">
              <el-icon><Document /></el-icon> 文件
            </el-button>
            <el-button :type="trackingMode === 'method' ? 'primary' : 'default'" @click="loadMethods" size="small">
              <el-icon><Operation /></el-icon> 方法
            </el-button>
          </el-button-group>

          <el-select
            v-if="methodList.length > 0"
            v-model="selectedMethod"
            placeholder="选择方法"
            size="small"
            style="width: 160px;"
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
        </div>

        <span class="header-divider"></span>

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
        <el-select
          v-model="playerStyle"
          @change="onStyleChange"
          size="small"
          class="style-selector"
          style="width: 120px;"
        >
          <el-option
            v-for="opt in styleOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          >
            <span>{{ opt.label }}</span>
            <span class="style-option-desc">{{ opt.desc }}</span>
          </el-option>
        </el-select>
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
              <span class="additions">+{{ player.currentCommit.value.additions ?? '--' }}</span>
              <span class="deletions">-{{ player.currentCommit.value.deletions ?? '--' }}</span>
            </span>
          </div>
          <div class="ai-analysis-row">
            <el-popover
              :visible="showAnalysisPopover"
              placement="bottom-start"
              :width="400"
              trigger="click"
            >
              <template #reference>
                <el-button
                  size="small"
                  :loading="analysisLoading"
                  @click="fetchAnalysis"
                  class="ai-analysis-btn"
                >
                  <el-icon><MagicStick /></el-icon>
                  {{ analysisLoading ? '分析中...' : 'AI 分析' }}
                </el-button>
              </template>

              <div class="analysis-popover" v-if="currentAnalysis">
                <div class="analysis-header">
                  <span class="analysis-title">🤖 AI 分析结果</span>
                  <el-button text size="small" @click="showAnalysisPopover = false">
                    <el-icon><Close /></el-icon>
                  </el-button>
                </div>

                <div class="analysis-body">
                  <div class="analysis-item" v-if="currentAnalysis.summary">
                    <span class="analysis-label">📝 摘要</span>
                    <p class="analysis-text">{{ currentAnalysis.summary }}</p>
                  </div>

                  <div class="analysis-item" v-if="currentAnalysis.purpose">
                    <span class="analysis-label">🎯 目的</span>
                    <p class="analysis-text">{{ currentAnalysis.purpose }}</p>
                  </div>

                  <div class="analysis-item" v-if="currentAnalysis.impact">
                    <span class="analysis-label">⚡ 影响</span>
                    <p class="analysis-text">{{ currentAnalysis.impact }}</p>
                  </div>
                </div>

                <div class="analysis-footer">
                  <span
                    v-if="getCategoryInfo(currentAnalysis.changeCategory)"
                    class="category-tag"
                    :style="{
                      background: getCategoryInfo(currentAnalysis.changeCategory)?.color + '20',
                      color: getCategoryInfo(currentAnalysis.changeCategory)?.color
                    }"
                  >
                    {{ getCategoryInfo(currentAnalysis.changeCategory)?.label }}
                  </span>

                  <div class="analysis-scores" v-if="currentAnalysis.complexityScore || currentAnalysis.importanceScore">
                    <span v-if="currentAnalysis.complexityScore" class="score-item">
                      复杂度: <span class="score-stars">{{ renderStars(currentAnalysis.complexityScore) }}</span>
                    </span>
                    <span v-if="currentAnalysis.importanceScore" class="score-item">
                      重要性: <span class="score-stars">{{ renderStars(currentAnalysis.importanceScore) }}</span>
                    </span>
                  </div>
                </div>
              </div>
            </el-popover>

            <span v-if="currentAnalysis?.summary" class="quick-summary" @click="showAnalysisPopover = true">
              {{ currentAnalysis.summary.slice(0, 50) }}{{ currentAnalysis.summary.length > 50 ? '...' : '' }}
            </span>
          </div>
        </div>
        <div class="code-viewers" :class="{ 'code-viewers--split': viewMode === 'split' }">
          <div
            class="code-viewer code-viewer--previous"
            v-if="viewMode === 'split'"
            ref="previousCodeViewerRef"
            @scroll="syncScroll('previous')"
            v-bind="useVirtualPreviousViewer ? previousContainerProps : {}"
          >
            <div class="code-viewer-label">旧版本</div>
            <div v-if="useVirtualPreviousViewer" class="code-content" v-bind="previousWrapperProps">
              <div
                v-for="{ data: line, index } in virtualPreviousCodeLines"
                :key="index"
                :data-line="line.number"
                class="code-line"
                :class="{ 'code-line--deleted': line.type === 'deleted' }"
              >
                <span class="line-number">{{ line.number }}</span>
                <span class="line-content" v-html="line.content"></span>
              </div>
            </div>
            <div v-else class="code-content">
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
          <div
            class="code-viewer"
            :class="{ 'code-viewer--current': viewMode === 'split' }"
            ref="codeViewerRef"
            @scroll="syncScroll('current')"
            v-bind="useVirtualCodeViewer ? codeContainerProps : {}"
          >
            <div class="code-viewer-label" v-if="viewMode === 'split'">新版本</div>
            <div v-if="useVirtualCodeViewer" class="code-content" v-bind="codeWrapperProps">
              <div
                v-for="{ data: line, index } in virtualCodeLines"
                :key="index"
                :data-line="line.number"
                class="code-line"
                :class="{ 'code-line--added': line.type === 'added' }"
              >
                <span class="line-number">{{ line.number }}</span>
                <span class="line-content" v-html="line.content"></span>
              </div>
            </div>
            <div v-else class="code-content">
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
            <template v-for="(group, groupIndex) in groupedMessages" :key="groupIndex">
              <div
                v-if="group.commitOrder"
                class="commit-separator"
                @click="jumpToCommitOrder(group.commitOrder)"
                :title="`跳转到第 ${group.commitOrder} 帧`"
              >
                <span class="separator-line"></span>
                <span class="separator-content">
                  <span class="separator-badge">#{{ group.commitOrder }}</span>
                  <span class="separator-hash">{{ group.shortHash }}</span>
                  <span class="separator-message" v-if="group.commitMessage">{{ group.commitMessage.slice(0, 30) }}{{ group.commitMessage.length > 30 ? '...' : '' }}</span>
                </span>
                <span class="separator-line"></span>
              </div>

              <div
                v-for="msg in group.messages"
                :key="msg.id"
                class="chat-message"
                :class="[`chat-message--${msg.role}`]"
              >
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
            </template>
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
      <div class="footer-content">
        <div class="progress-info">
          <span class="progress-current">{{ player.currentIndex.value + 1 }}</span>
          <span class="progress-separator">/</span>
          <span class="progress-total">{{ player.totalFrames.value }}</span>
        </div>
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
          <el-select v-model="player.speed.value" size="small" class="speed-select">
            <el-option v-for="opt in speedOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
        <div class="shortcuts-hint">
          <kbd>Space</kbd> <kbd>←</kbd> <kbd>→</kbd>
        </div>
      </div>
    </footer>

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
@import "./PlayerPage.css";
</style>








