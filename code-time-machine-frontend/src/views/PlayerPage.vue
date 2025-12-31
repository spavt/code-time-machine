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
const changeKey = ref(0) // 用于触发动画重置
const suggestions = ref<string[]>([]) // 智能推荐问题
const contentLoading = new Map<number, Promise<void>>()
let commitLoadToken = 0

// 代码高亮缓存：key = commitId, value = 高亮后的 HTML
const highlightCache = new Map<number, string>()

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

// AI 分析
const currentAnalysis = ref<AiAnalysis | null>(null)
const analysisLoading = ref(false)
const showAnalysisPopover = ref(false)

// 播放器界面风格切换
const playerStyle = ref<'classic' | 'cinematic'>(
  (localStorage.getItem('playerStyle') as 'classic' | 'cinematic') || 'cinematic'
)

function togglePlayerStyle() {
  playerStyle.value = playerStyle.value === 'cinematic' ? 'classic' : 'cinematic'
  localStorage.setItem('playerStyle', playerStyle.value)
}

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

// 基于 repoId + filePath 生成确定性的 sessionId，确保同一文件使用相同的会话
// 注意：使用 route.params 直接获取初始值，因为这些值在组件加载时即可用
const sessionId = generateDeterministicSessionId(
  Number(route.params.repoId),
  decodeURIComponent(route.params.filePath as string || '')
)
const chat = useChat(sessionId)
const language = computed(() => detectLanguage(filePath.value))

// 滑动窗口预加载配置
const PRELOAD_WINDOW_SIZE = 5 // 预加载前后各 5 帧

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

// 滑动窗口预加载：使用批量 API 预加载周围帧（不包括当前帧）
// 当前帧仍然使用 ensureCommitContent 单独加载，确保即时显示
// 标记是否正在批量加载，避免重复请求
let batchLoadingPromise: Promise<void> | null = null

function preloadWindow(centerIndex: number) {
  if (trackingMode.value !== 'file') return
  
  const commitList = commits.value
  const start = Math.max(0, centerIndex - PRELOAD_WINDOW_SIZE)
  const end = Math.min(commitList.length - 1, centerIndex + PRELOAD_WINDOW_SIZE)
  
  // 收集需要预加载的 commitIds（排除当前帧，当前帧单独加载）
  const toLoad: number[] = []
  for (let i = start; i <= end; i++) {
    // 跳过当前帧，当前帧由 watch 中的 ensureCommitContent 处理
    if (i === centerIndex) continue
    
    const commit = commitList[i]
    if (commit && commit.content == null && !contentLoading.has(commit.id)) {
      toLoad.push(commit.id)
    }
  }
  
  if (toLoad.length === 0) return
  
  // 如果已经在批量加载中，跳过（不影响当前帧）
  if (batchLoadingPromise) return
  
  // 批量请求（后台静默加载，不阻塞）
  batchLoadingPromise = (async () => {
    try {
      const results = await fileApi.getBatchContent(repoId.value, toLoad, filePath.value)
      
      // 将结果填充到对应的 commit 对象中
      for (const [commitIdStr, data] of Object.entries(results)) {
        const commitId = Number(commitIdStr)
        const commit = commitList.find(c => c.id === commitId)
        if (commit && commit.content == null) {
          commit.content = data.content
          // 预计算高亮
          if (data.content) {
            getCachedHighlight(commit.id, data.content)
          }
        }
      }
    } catch (e) {
      console.warn('Batch preload failed, falling back to individual requests:', e)
      // 降级：逐个加载
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


// 获取缓存的高亮代码，若未缓存则计算并缓存
function getCachedHighlight(commitId: number, content: string): string {
  if (!content) return ''
  
  // 检查缓存
  const cached = highlightCache.get(commitId)
  if (cached !== undefined) {
    return cached
  }
  
  // 计算高亮并缓存
  let highlighted: string
  try {
    highlighted = hljs.highlight(content, { language: language.value }).value
  } catch {
    highlighted = content
  }
  
  highlightCache.set(commitId, highlighted)
  return highlighted
}

// 补充历史消息的 commitOrder 和 shortHash（后端可能没有返回这些字段）
function enrichHistoryMessages() {
  const commitList = commits.value
  if (commitList.length === 0) return
  
  // 创建 commitId -> commit 的映射
  const commitMap = new Map<number, { order: number; shortHash: string; message: string }>()
  commitList.forEach((c, idx) => {
    commitMap.set(c.id, { order: idx + 1, shortHash: c.shortHash, message: c.commitMessage })
  })
  
  // 遍历消息，补充缺失的字段
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
    // 加载历史聊天记录
    await chat.loadHistory()
    // 补充历史消息的 commitOrder 和 shortHash（后端可能没有返回这些字段）
    enrichHistoryMessages()
    // 初始预加载滑动窗口
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
      // 滑动窗口预加载：预加载当前帧前后各 5 帧
      preloadWindow(player.currentIndex.value)
      await Promise.all([
        ensureCommitContent(commit),
        ensureCommitContent(oldCommit)
      ])
      if (requestId !== commitLoadToken) return
    }
    const commitContent = commit.content ?? undefined
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
    // 注：移除了 changeKey.value++ 以避免 DOM 重建导致滚动位置重置
    
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
          const { additions, deletions } = countDiffStats(diffResult.diff)
          commit.additions = additions
          commit.deletions = deletions
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
    
    // 更新AI聊天上下文（包含当前帧信息）
    chat.setContext({
      repoId: repoId.value,
      commitId: commit.id,
      commitOrder: player.currentIndex.value + 1,
      shortHash: commit.shortHash,
      filePath: filePath.value,
      codeSnippet: contextParts.join('\n')
    })
    
    // 自动滚动到第一个变化行（等待 DOM 完全渲染）
    await nextTick()
    // 再等待一个微任务周期，确保 computed 属性都计算完成
    await nextTick()
    // 添加小延迟确保浏览器完成渲染
    setTimeout(() => {
      scrollToFirstChange()
    }, 50)
    
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

// 使用缓存的高亮代码
const highlightedCode = computed(() => {
  const commit = player.currentCommit.value
  if (!commit || !currentCode.value) return ''
  return getCachedHighlight(commit.id, currentCode.value)
})

// 高亮上一版本代码（使用缓存）
const highlightedPreviousCode = computed(() => {
  const prevCommit = player.previousCommit.value
  if (!prevCommit || !previousCode.value) return ''
  return getCachedHighlight(prevCommit.id, previousCode.value)
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

// =========== 虚拟滚动（仅超过 800 行时启用）===========
const VIRTUAL_SCROLL_THRESHOLD = 800
const CODE_LINE_HEIGHT = 22 // 与 CSS .code-line min-height 保持一致

// 是否使用虚拟滚动
const useVirtualCodeViewer = computed(() => codeLines.value.length > VIRTUAL_SCROLL_THRESHOLD)
const useVirtualPreviousViewer = computed(() => previousCodeLines.value.length > VIRTUAL_SCROLL_THRESHOLD)

// 当前代码虚拟列表
const {
  list: virtualCodeLines,
  containerProps: codeContainerProps,
  wrapperProps: codeWrapperProps,
  scrollTo: scrollToCodeLine
} = useVirtualList(codeLines, { itemHeight: CODE_LINE_HEIGHT })

// 上一版本代码虚拟列表
const {
  list: virtualPreviousCodeLines,
  containerProps: previousContainerProps,
  wrapperProps: previousWrapperProps,
  scrollTo: scrollToPreviousLine
} = useVirtualList(previousCodeLines, { itemHeight: CODE_LINE_HEIGHT })


// 自动滚动到第一个变化行
function scrollToFirstChange() {
  const firstLine = changedLines.value.firstChangedLine
  
  if (!firstLine) {
    return
  }
  
  // 虚拟滚动模式：使用 scrollTo API
  if (useVirtualCodeViewer.value) {
    // 滚动到目标行，居中显示
    const targetIndex = Math.max(0, firstLine - 1)
    scrollToCodeLine(targetIndex)
    if (viewMode.value === 'split' && useVirtualPreviousViewer.value) {
      scrollToPreviousLine(targetIndex)
    }
    return
  }
  
  // 非虚拟滚动模式：使用原有 DOM 滚动逻辑
  if (viewMode.value === 'split') {
    // 双栏模式：查找实际元素位置，同步滚动两边
    // 播放时使用瞬间滚动（避免动画未完成就切帧），手动时用平滑滚动
    const scrollBehavior: ScrollBehavior = player.isPlaying.value ? 'auto' : 'smooth'
    
    const currentViewer = codeViewerRef.value
    const previousViewer = previousCodeViewerRef.value
    
    if (currentViewer) {
      // 在当前版本中查找目标行
      const lineElement = currentViewer.querySelector(`[data-line="${firstLine}"]`) as HTMLElement
      
      let targetScrollTop: number
      
      if (lineElement) {
        // 找到元素：计算实际位置
        const containerRect = currentViewer.getBoundingClientRect()
        const elementRect = lineElement.getBoundingClientRect()
        const relativeTop = elementRect.top - containerRect.top + currentViewer.scrollTop
        targetScrollTop = Math.max(0, relativeTop - containerRect.height / 2)
      } else {
        // 未找到元素：使用估算（备用方案）
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
    // 单栏模式：始终使用平滑滚动
    if (codeViewerRef.value) {
      const lineElement = codeViewerRef.value.querySelector(`[data-line="${firstLine}"]`)
      if (lineElement) {
        lineElement.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
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

// 跳转到指定帧（根据 commitOrder）
function jumpToCommitOrder(commitOrder: number | undefined) {
  if (!commitOrder) return
  const targetIndex = commitOrder - 1  // commitOrder 从 1 开始，index 从 0 开始
  if (targetIndex >= 0 && targetIndex < player.totalFrames.value) {
    player.goTo(targetIndex)
  }
}

// 按提交分组的消息（用于显示分隔线）
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
    // 只有用户消息带有 commitOrder，用它来判断分组
    const msgCommitOrder = msg.role === 'user' ? msg.commitOrder : undefined
    
    // 如果是新的提交组或第一条消息
    if (msg.role === 'user' && (
      !currentGroup || 
      currentGroup.commitOrder !== msgCommitOrder
    )) {
      // 查找对应 commit 获取 message
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
      // 如果第一条是 assistant 消息（理论上不应该），创建一个无分组
      currentGroup = { messages: [] }
      groups.push(currentGroup)
    }
    
    currentGroup.messages.push(msg)
  }
  
  return groups
})

// ========== AI 分析相关函数 ==========
async function fetchAnalysis() {
  const commit = player.currentCommit.value
  if (!commit) return

  // 如果已有分析，直接显示
  if (currentAnalysis.value?.commitId === commit.id) {
    showAnalysisPopover.value = !showAnalysisPopover.value
    return
  }

  analysisLoading.value = true
  try {
    // 先尝试获取已有分析
    try {
      const analysis = await commitApi.getAiAnalysis(commit.id)
      currentAnalysis.value = analysis
      showAnalysisPopover.value = true
      return
    } catch (e: any) {
      // 404 表示没有分析
      console.log('No existing analysis, triggering new one...')
    }

    // 触发新分析
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

// 当 commit 切换时，重置分析状态
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
        <el-tooltip :content="playerStyle === 'cinematic' ? '切换到经典风格' : '切换到电影风格'" placement="bottom">
          <el-button @click="togglePlayerStyle" class="style-toggle-btn">
            <el-icon><component :is="playerStyle === 'cinematic' ? 'Film' : 'Monitor'" /></el-icon>
            {{ playerStyle === 'cinematic' ? '电影' : '经典' }}
          </el-button>
        </el-tooltip>
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
          <!-- AI 分析 -->
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
              
              <!-- Popover 内容 -->
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
            
            <!-- 简短摘要（如果已有分析） -->
            <span v-if="currentAnalysis?.summary" class="quick-summary" @click="showAnalysisPopover = true">
              {{ currentAnalysis.summary.slice(0, 50) }}{{ currentAnalysis.summary.length > 50 ? '...' : '' }}
            </span>
          </div>
        </div>
        <div class="code-viewers" :class="{ 'code-viewers--split': viewMode === 'split' }">
          <!-- 旧版本代码（分屏模式） -->
          <div 
            class="code-viewer code-viewer--previous" 
            v-if="viewMode === 'split'" 
            ref="previousCodeViewerRef" 
            @scroll="syncScroll('previous')"
            v-bind="useVirtualPreviousViewer ? previousContainerProps : {}"
          >
            <div class="code-viewer-label">旧版本</div>
            <!-- 虚拟滚动模式 -->
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
            <!-- 普通渲染模式 -->
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
          <!-- 新版本代码 -->
          <div 
            class="code-viewer" 
            :class="{ 'code-viewer--current': viewMode === 'split' }" 
            ref="codeViewerRef" 
            @scroll="syncScroll('current')"
            v-bind="useVirtualCodeViewer ? codeContainerProps : {}"
          >
            <div class="code-viewer-label" v-if="viewMode === 'split'">新版本</div>
            <!-- 虚拟滚动模式 -->
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
            <!-- 普通渲染模式 -->
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
            <!-- 按提交分组显示消息 -->
            <template v-for="(group, groupIndex) in groupedMessages" :key="groupIndex">
              <!-- 提交分隔线 -->
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
              
              <!-- 该组的消息 -->
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
/* =====================================================
   CINEMATIC FILM REEL PLAYER
   Aesthetic: 1930s Movie Theater Control Room
   ===================================================== */

/* --- Base Layout --- */
.player-page { 
  height: 100vh; 
  display: flex; 
  flex-direction: column; 
  background: linear-gradient(180deg, var(--color-film-darker) 0%, var(--color-film-dark) 100%);
  position: relative;
  overflow: hidden;
}

/* Subtle film grain overlay */
.player-page::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E");
  opacity: 0.03;
  pointer-events: none;
  z-index: 0;
}

/* --- Header: Projection Room Status Bar --- */
.player-header { 
  display: flex; 
  align-items: center; 
  justify-content: space-between; 
  padding: var(--spacing-sm) var(--spacing-xl);
  background: linear-gradient(180deg, rgba(26, 26, 46, 0.98) 0%, rgba(15, 15, 26, 0.95) 100%);
  border-bottom: 2px solid var(--color-film-border);
  height: 64px;
  position: relative;
  z-index: 10;
}

/* Art Deco corner decorations */
.player-header::before,
.player-header::after {
  content: '◆';
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-amber);
  font-size: 10px;
  opacity: 0.6;
}
.player-header::before { left: 12px; }
.player-header::after { right: 12px; }

.header-left, .header-right { 
  display: flex; 
  align-items: center; 
  gap: var(--spacing-md); 
  z-index: 1;
}

.file-info { 
  display: flex; 
  align-items: center; 
  gap: var(--spacing-sm);
  padding: 6px 16px;
  background: rgba(246, 185, 59, 0.08);
  border: 1px solid rgba(246, 185, 59, 0.2);
  border-radius: var(--radius-sm);
}

.file-icon { 
  color: var(--color-amber); 
  font-size: 16px;
}

.file-path { 
  font-family: var(--font-display); 
  font-size: 0.95rem; 
  font-weight: 500;
  color: var(--color-projector-light);
  letter-spacing: 0.02em;
}

/* Frame Counter - Mechanical Style */
.header-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
}

.commit-info { 
  display: flex; 
  align-items: center; 
  gap: var(--spacing-md);
  padding: 8px 20px;
  background: linear-gradient(180deg, #2a2a3e 0%, #1a1a2e 100%);
  border: 1px solid var(--color-amber);
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-projector), inset 0 1px 0 rgba(255,255,255,0.05);
}

.commit-order { 
  font-family: var(--font-display);
  font-size: 1.1rem;
  font-weight: 600;
  color: var(--color-amber);
  letter-spacing: 0.05em;
  text-shadow: 0 0 20px var(--color-amber-glow);
}

.commit-hash { 
  font-family: var(--font-code); 
  font-size: 0.85rem;
  color: var(--text-muted);
  padding-left: var(--spacing-md);
  border-left: 1px solid var(--color-film-border);
}

.tracking-mode-toggle { margin-right: 8px; }

/* --- Main Content Area --- */
.player-main { 
  flex: 1; 
  display: flex; 
  overflow: hidden;
  position: relative;
  z-index: 1;
}

.code-section { 
  flex: 1; 
  display: flex; 
  flex-direction: column; 
  overflow: hidden;
  position: relative;
}

/* Commit Panel - Film Slate Style */
.commit-panel { 
  padding: var(--spacing-md) var(--spacing-xl);
  background: linear-gradient(90deg, rgba(26, 26, 46, 0.95) 0%, rgba(42, 42, 62, 0.9) 50%, rgba(26, 26, 46, 0.95) 100%);
  border-bottom: 1px solid var(--color-film-border);
  position: relative;
}

.commit-panel::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: var(--color-amber);
}

.commit-message { 
  font-family: var(--font-display);
  font-size: 1.15rem; 
  font-weight: 600;
  color: var(--color-projector-light);
  margin-bottom: var(--spacing-xs);
  letter-spacing: 0.01em;
}

.commit-meta { 
  display: flex; 
  gap: var(--spacing-xl); 
  font-size: 0.85rem; 
  color: var(--text-muted);
  font-family: var(--font-code);
}

.commit-meta > span { 
  display: flex; 
  align-items: center; 
  gap: 6px;
}

.additions { color: var(--color-amber) !important; font-weight: 600; }
.deletions { color: #f87171 !important; font-weight: 600; }

/* --- Code Viewer: The Screen --- */
.code-viewers { 
  flex: 1; 
  display: flex; 
  overflow: hidden;
  position: relative;
}

/* Vignette effect */
.code-viewers::after {
  content: '';
  position: absolute;
  inset: 0;
  box-shadow: var(--shadow-vignette);
  pointer-events: none;
  z-index: 5;
}

.code-viewers--split { 
  gap: 0;
}

/* Film perforation divider for split view */
.code-viewers--split::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 20px;
  transform: translateX(-50%);
  background: 
    repeating-linear-gradient(
      180deg,
      transparent 0px,
      transparent 20px,
      var(--color-film-border) 20px,
      var(--color-film-border) 24px,
      transparent 24px,
      transparent 44px
    );
  z-index: 10;
}

.code-viewers--split .code-viewer { 
  flex: 1;
  position: relative;
}

.code-viewer { 
  flex: 1; 
  overflow: auto; 
  background: var(--code-bg);
  position: relative;
}

.code-viewer-label {
  position: sticky;
  top: 0;
  z-index: 10;
  padding: 8px 16px;
  background: linear-gradient(180deg, var(--color-film-dark) 0%, rgba(26, 26, 46, 0.95) 100%);
  font-family: var(--font-display);
  font-size: 0.8rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--text-muted);
  border-bottom: 1px solid var(--color-film-border);
}

.code-viewer--previous .code-viewer-label { color: #f87171; }
.code-viewer--current .code-viewer-label { color: var(--color-amber); }

.code-content { 
  font-family: var(--font-code); 
  font-size: 13px; 
  line-height: 1.7; 
  padding: var(--spacing-md) 0;
}

.code-line { 
  display: flex; 
  min-height: 24px; 
  padding: 0 var(--spacing-xl);
  transition: background-color 0.2s ease;
}

.code-line:hover { 
  background: rgba(246, 185, 59, 0.05);
}

/* Changed lines with amber glow */
.code-line--added { 
  background: rgba(246, 185, 59, 0.12);
  border-left: 3px solid var(--color-amber);
  animation: amber-pulse 2s ease-out;
}

.code-line--added .line-number { 
  color: var(--color-amber); 
  font-weight: 600;
}

@keyframes amber-pulse {
  0% { background: rgba(246, 185, 59, 0.35); box-shadow: inset 0 0 30px rgba(246, 185, 59, 0.2); }
  50% { background: rgba(246, 185, 59, 0.2); }
  100% { background: rgba(246, 185, 59, 0.12); }
}

.code-line--deleted {
  background: rgba(248, 113, 113, 0.12);
  border-left: 3px solid #f87171;
  animation: delete-pulse 2s ease-out;
}

.code-line--deleted .line-number { 
  color: #f87171; 
  font-weight: 600;
}

@keyframes delete-pulse {
  0% { background: rgba(248, 113, 113, 0.35); }
  50% { background: rgba(248, 113, 113, 0.2); }
  100% { background: rgba(248, 113, 113, 0.12); }
}

.line-number { 
  flex-shrink: 0; 
  width: 55px; 
  padding-right: var(--spacing-md); 
  text-align: right; 
  color: var(--text-muted); 
  user-select: none;
  font-size: 12px;
  opacity: 0.7;
}

.line-content { 
  flex: 1; 
  white-space: pre;
}

/* --- AI Chat: Director's Notes --- */
.chat-section { 
  width: 420px; 
  display: flex; 
  flex-direction: column;
  background: linear-gradient(180deg, #0f0f1a 0%, #0a0a12 100%);
  border-left: 2px solid var(--color-amber);
  position: relative;
  box-shadow: -5px 0 30px rgba(0, 0, 0, 0.5);
}

/* Paper texture overlay */
.chat-section::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='100' height='100'%3E%3Crect fill='%23faf8f0' width='100' height='100'/%3E%3Crect fill='%23f5f3e8' x='0' y='0' width='1' height='100'/%3E%3C/svg%3E");
  opacity: 0.02;
  pointer-events: none;
}

.chat-header { 
  display: flex; 
  justify-content: space-between; 
  align-items: center; 
  padding: var(--spacing-md) var(--spacing-lg);
  background: linear-gradient(180deg, rgba(246, 185, 59, 0.1) 0%, transparent 100%);
  border-bottom: 1px solid var(--color-film-border);
}

.chat-header h3 { 
  display: flex; 
  align-items: center; 
  gap: var(--spacing-sm);
  font-family: var(--font-display);
  font-size: 1rem;
  font-weight: 600;
  color: var(--color-amber);
  margin: 0;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.chat-messages { 
  flex: 1; 
  overflow-y: auto; 
  padding: var(--spacing-md);
}

.chat-empty { 
  text-align: center; 
  padding: var(--spacing-2xl) var(--spacing-lg);
  color: var(--text-muted);
}

.chat-empty p {
  font-family: var(--font-display);
  font-style: italic;
  font-size: 0.95rem;
  margin-bottom: var(--spacing-lg);
}

.suggestions { 
  display: flex; 
  flex-direction: column; 
  gap: var(--spacing-sm);
}

.chat-message { 
  display: flex; 
  gap: var(--spacing-sm); 
  margin-bottom: var(--spacing-md);
}

.chat-message--user { flex-direction: row-reverse; }

.message-avatar { 
  width: 32px; 
  height: 32px; 
  border-radius: 50%; 
  display: flex; 
  align-items: center; 
  justify-content: center; 
  flex-shrink: 0;
  font-size: 14px;
  border: 2px solid transparent;
}

.chat-message--user .message-avatar { 
  background: var(--color-amber);
  border-color: rgba(246, 185, 59, 0.3);
  color: var(--color-film-dark);
}

.chat-message--assistant .message-avatar { 
  background: linear-gradient(135deg, #2a2a3e, #3a3a4e);
  border-color: var(--color-film-border);
  color: var(--color-amber);
}

.message-content { 
  max-width: 85%; 
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-md);
  font-size: 0.9rem;
  line-height: 1.6;
}

.chat-message--user .message-content { 
  background: var(--color-amber);
  color: var(--color-film-dark);
  font-weight: 500;
}

.chat-message--assistant .message-content { 
  background: linear-gradient(180deg, #1a1a28 0%, #12121a 100%);
  border: 1px solid rgba(246, 185, 59, 0.25);
  color: #e8e8f0;
  font-size: 0.88rem;
  line-height: 1.7;
}

.chat-message--assistant .message-content :deep(p) {
  margin-bottom: 0.8em;
  color: #e8e8f0;
}

.chat-message--assistant .message-content :deep(p:last-child) {
  margin-bottom: 0;
}

.chat-message--assistant .message-content :deep(code) {
  background: rgba(246, 185, 59, 0.15);
  color: var(--color-amber);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: var(--font-code);
  font-size: 0.85em;
}

.chat-message--assistant .message-content :deep(pre) {
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid var(--color-film-border);
  border-radius: var(--radius-sm);
  padding: var(--spacing-sm);
  overflow-x: auto;
  margin: 0.5em 0;
}

.chat-message--assistant .message-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: #e0e0e0;
}

.chat-input { 
  padding: var(--spacing-md);
  border-top: 1px solid var(--color-film-border);
  background: rgba(26, 26, 46, 0.5);
}

/* --- Footer: Film Strip Control Deck --- */
.player-footer { 
  padding: var(--spacing-md) var(--spacing-xl);
  background: linear-gradient(180deg, rgba(26, 26, 46, 0.98) 0%, rgba(15, 15, 26, 1) 100%);
  border-top: 2px solid var(--color-film-border);
  position: relative;
  z-index: 10;
}

.footer-content {
  display: flex;
  align-items: center;
  gap: var(--spacing-lg);
}

/* Progress Indicator */
.progress-info {
  display: flex;
  align-items: baseline;
  gap: 2px;
  font-family: var(--font-display);
  min-width: 70px;
}

.progress-current {
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--color-amber);
  text-shadow: 0 0 15px var(--color-amber-glow);
}

.progress-separator {
  font-size: 1rem;
  color: var(--text-muted);
  margin: 0 2px;
}

.progress-total {
  font-size: 0.9rem;
  color: var(--text-muted);
}

/* Film Strip Timeline */
.timeline-slider { 
  flex: 1;
  position: relative;
  padding: 8px 20px;
  background: linear-gradient(180deg, #0a0a12 0%, #151520 50%, #0a0a12 100%);
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-film-border);
}

/* Sprocket holes */
.timeline-slider::before,
.timeline-slider::after {
  content: '○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○ ○';
  position: absolute;
  left: 0;
  right: 0;
  font-size: 6px;
  letter-spacing: 8px;
  text-align: center;
  color: var(--color-film-border);
  pointer-events: none;
}

.timeline-slider::before { top: 0; }
.timeline-slider::after { bottom: 0; }

/* Slider styling */
.timeline-slider :deep(.el-slider__runway) {
  background: linear-gradient(90deg, 
    transparent 0%, 
    rgba(246, 185, 59, 0.2) 10%, 
    rgba(246, 185, 59, 0.2) 90%, 
    transparent 100%
  ) !important;
  height: 4px;
  border-radius: 2px;
}

.timeline-slider :deep(.el-slider__bar) {
  background: linear-gradient(90deg, var(--color-amber), #E8A838) !important;
  box-shadow: 0 0 10px var(--color-amber-glow);
}

.timeline-slider :deep(.el-slider__button) {
  width: 16px;
  height: 16px;
  background: radial-gradient(circle at 30% 30%, #fff 0%, var(--color-amber) 50%, #E8A838 100%);
  border: 2px solid var(--color-amber);
  box-shadow: 0 0 15px var(--color-amber-glow);
}

/* Player Controls - Compact Inline */
.player-controls { 
  display: flex; 
  align-items: center; 
  gap: var(--spacing-sm);
}

.player-controls :deep(.el-button-group) {
  display: flex;
  gap: 4px;
}

.player-controls :deep(.el-button) {
  background: linear-gradient(180deg, #2a2a3e 0%, #1a1a2e 100%) !important;
  border: 1px solid var(--color-film-border) !important;
  color: var(--text-secondary) !important;
  border-radius: var(--radius-sm) !important;
  transition: all 0.2s ease;
}

.player-controls :deep(.el-button:hover:not(:disabled)) {
  background: linear-gradient(180deg, #3a3a4e 0%, #2a2a3e 100%) !important;
  border-color: var(--color-amber) !important;
  color: var(--color-amber) !important;
  box-shadow: 0 0 15px rgba(246, 185, 59, 0.2);
}

.player-controls :deep(.el-button:disabled) {
  opacity: 0.3;
}

/* Play Button - Large Brass Style */
.play-button {
  width: 56px !important;
  height: 56px !important;
  border-radius: 50% !important;
  background: linear-gradient(145deg, var(--color-amber) 0%, #E8A838 50%, #D4941F 100%) !important;
  border: 3px solid rgba(255, 255, 255, 0.2) !important;
  box-shadow: 
    0 0 30px var(--color-amber-glow),
    var(--shadow-mechanical),
    inset 0 2px 0 rgba(255, 255, 255, 0.3) !important;
  color: var(--color-film-dark) !important;
  font-size: 22px !important;
  transition: all 0.15s ease !important;
}

.play-button:hover {
  transform: translateY(-2px) !important;
  box-shadow: 
    0 0 40px rgba(246, 185, 59, 0.4),
    0 8px 25px rgba(0, 0, 0, 0.4),
    inset 0 2px 0 rgba(255, 255, 255, 0.3) !important;
}

.play-button:active {
  transform: translateY(1px) !important;
  box-shadow: 
    0 0 20px var(--color-amber-glow),
    0 2px 10px rgba(0, 0, 0, 0.4),
    inset 0 2px 0 rgba(255, 255, 255, 0.3) !important;
}

/* Speed Selector - RPM Dial Style */
.player-controls :deep(.el-select) {
  margin-left: var(--spacing-md);
}

.player-controls :deep(.el-select .el-input__wrapper) {
  background: linear-gradient(180deg, #2a2a3e 0%, #1a1a2e 100%) !important;
  border: 1px solid var(--color-film-border) !important;
  border-radius: 20px !important;
}

.player-controls :deep(.el-select .el-input__inner) {
  font-family: var(--font-code) !important;
  color: var(--color-amber) !important;
  font-weight: 600;
}

/* Keyboard Shortcuts Hint */
.shortcuts-hint { 
  display: flex; 
  align-items: center;
  gap: var(--spacing-xs);
  font-size: 0.7rem;
  color: var(--text-muted);
  opacity: 0.6;
}

kbd { 
  padding: 3px 8px;
  background: linear-gradient(180deg, #2a2a3e 0%, #1a1a2e 100%);
  border: 1px solid var(--color-film-border);
  border-radius: 4px;
  font-family: var(--font-code);
  font-size: 0.7rem;
  color: var(--color-amber);
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.3);
}

/* --- Animations --- */
.slide-right-enter-active, 
.slide-right-leave-active { 
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.slide-right-enter-from, 
.slide-right-leave-to { 
  transform: translateX(100%); 
  opacity: 0;
}

/* Film strip rolling animation for playing state */
@keyframes sprocket-roll {
  0% { letter-spacing: 12px; }
  50% { letter-spacing: 14px; }
  100% { letter-spacing: 12px; }
}

.player-page.is-playing .timeline-slider::before,
.player-page.is-playing .timeline-slider::after {
  animation: sprocket-roll 0.3s ease-in-out infinite;
}

/* --- View Mode Toggle --- */
.view-mode-toggle { margin-right: var(--spacing-sm); }

/* --- Streaming Cursor --- */
.streaming-cursor {
  display: inline-block;
  color: var(--color-amber);
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* --- AI Analysis Styles --- */
.ai-analysis-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-md);
  margin-top: var(--spacing-sm);
  padding-top: var(--spacing-sm);
  border-top: 1px dashed var(--color-film-border);
}

.ai-analysis-btn {
  background: linear-gradient(135deg, rgba(246, 185, 59, 0.1), rgba(232, 168, 56, 0.1)) !important;
  border: 1px solid rgba(246, 185, 59, 0.3) !important;
  color: var(--color-amber) !important;
  font-family: var(--font-display);
}

.ai-analysis-btn:hover {
  background: linear-gradient(135deg, rgba(246, 185, 59, 0.2), rgba(232, 168, 56, 0.2)) !important;
  border-color: var(--color-amber) !important;
}

.quick-summary {
  flex: 1;
  font-size: 0.85rem;
  color: var(--text-secondary);
  cursor: pointer;
  line-height: 1.4;
  font-family: var(--font-code);
}

.quick-summary:hover {
  color: var(--color-amber);
}

.analysis-popover {
  margin: -12px;
}

.analysis-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-sm) var(--spacing-md);
  background: linear-gradient(180deg, rgba(246, 185, 59, 0.15) 0%, transparent 100%);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
}

.analysis-title {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 0.95rem;
  color: var(--color-amber);
}

.analysis-body {
  padding: var(--spacing-md);
}

.analysis-item {
  margin-bottom: var(--spacing-md);
}

.analysis-item:last-child {
  margin-bottom: 0;
}

.analysis-label {
  display: block;
  font-size: 0.7rem;
  font-weight: 600;
  font-family: var(--font-display);
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 4px;
}

.analysis-text {
  font-size: 0.85rem;
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
  padding: var(--spacing-sm) var(--spacing-md);
  background: rgba(42, 42, 62, 0.5);
  border-radius: 0 0 var(--radius-md) var(--radius-md);
}

.category-tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  font-size: 0.7rem;
  font-weight: 600;
  font-family: var(--font-display);
  border-radius: var(--radius-full);
  letter-spacing: 0.5px;
}

.analysis-scores {
  display: flex;
  gap: var(--spacing-md);
}

.score-item {
  font-size: 0.75rem;
  color: var(--text-muted);
  font-family: var(--font-code);
}

.score-stars {
  color: var(--color-amber);
  letter-spacing: 1px;
}

/* --- Commit Separator in Chat --- */
.commit-separator {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  margin: var(--spacing-md) 0;
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.commit-separator:hover {
  opacity: 0.8;
}

.commit-separator:hover .separator-content {
  background: rgba(246, 185, 59, 0.2);
  border-color: var(--color-amber);
}

.separator-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(to right, transparent, var(--color-film-border), transparent);
}

.separator-content {
  display: flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 4px 12px;
  background: rgba(246, 185, 59, 0.1);
  border: 1px solid var(--color-film-border);
  border-radius: var(--radius-full);
  font-size: 0.7rem;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.separator-badge {
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--color-amber);
}

.separator-hash {
  font-family: var(--font-code);
  color: var(--text-muted);
}

.separator-message {
  color: var(--text-secondary);
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* --- Story Dialog --- */
.story-content { 
  min-height: 200px;
}

.story-text { 
  line-height: 1.8; 
  color: var(--text-primary);
  font-family: var(--font-display);
}

.story-text :deep(p) { 
  margin-bottom: 1em;
}

.story-milestones { 
  margin-top: var(--spacing-lg);
  padding-top: var(--spacing-lg);
  border-top: 1px solid var(--color-film-border);
}

.story-milestones h4 { 
  font-family: var(--font-display);
  margin-bottom: var(--spacing-md);
  font-size: 1rem;
  color: var(--color-amber);
}

/* --- Loading Animation --- */
.loading-dots span {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin: 0 2px;
  background: var(--color-amber);
  border-radius: 50%;
  animation: loading-bounce 1.4s ease-in-out infinite both;
}

.loading-dots span:nth-child(1) { animation-delay: -0.32s; }
.loading-dots span:nth-child(2) { animation-delay: -0.16s; }

@keyframes loading-bounce {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

/* =====================================================
   CLASSIC STYLE - 经典简洁风格
   Original minimal design with standard accents
   ===================================================== */

/* Style Toggle Button */
.style-toggle-btn {
  transition: all 0.3s ease;
}

/* --- Classic Style Overrides --- */
.player-page--classic {
  background: var(--bg-main);
}

.player-page--classic::before {
  display: none; /* 移除胶片纹理 */
}

.player-page--classic .player-header {
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  height: 56px;
}

.player-page--classic .player-header::before,
.player-page--classic .player-header::after {
  display: none; /* 移除装饰性菱形 */
}

.player-page--classic .file-info {
  background: transparent;
  border: none;
  padding: 0;
}

.player-page--classic .file-icon {
  color: var(--color-accent);
}

.player-page--classic .file-path {
  font-family: var(--font-mono);
  font-size: 0.9rem;
  color: var(--text-secondary);
}

.player-page--classic .commit-order {
  background: var(--color-primary-glow);
  color: var(--color-primary);
  border: none;
  font-family: var(--font-mono);
}

.player-page--classic .commit-panel {
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  padding: var(--spacing-md) var(--spacing-lg);
}

.player-page--classic .commit-panel::before {
  display: none;
}

.player-page--classic .commit-message {
  font-family: inherit;
  font-size: 1rem;
  font-weight: 500;
  color: var(--text-primary);
  letter-spacing: normal;
}

.player-page--classic .commit-meta {
  font-family: inherit;
}

.player-page--classic .additions {
  color: var(--color-success) !important;
}

.player-page--classic .deletions {
  color: var(--color-danger) !important;
}

.player-page--classic .code-viewer {
  background: var(--code-bg);
}

.player-page--classic .code-viewers::after {
  display: none; /* 移除暗角效果 */
}

.player-page--classic .code-viewers--split::before {
  display: none; /* 移除胶片孔洞分隔线 */
}

.player-page--classic .code-viewers--split {
  gap: 1px;
  background: var(--border-color);
}

.player-page--classic .code-viewer-label {
  background: var(--bg-tertiary);
  font-family: inherit;
  letter-spacing: 0.5px;
}

.player-page--classic .code-viewer--previous .code-viewer-label {
  color: #f85149;
}

.player-page--classic .code-viewer--current .code-viewer-label {
  color: #2ea043;
}

.player-page--classic .code-line {
  transition: background-color 0.3s ease;
}

.player-page--classic .code-line:hover {
  background: var(--bg-hover);
}

.player-page--classic .code-line--added {
  background: rgba(46, 160, 67, 0.15);
  border-left: 3px solid #2ea043;
  animation: classic-highlight-pulse 2s ease-out;
}

.player-page--classic .code-line--added .line-number {
  color: #2ea043;
}

@keyframes classic-highlight-pulse {
  0% { background: rgba(46, 160, 67, 0.4); }
  50% { background: rgba(46, 160, 67, 0.25); }
  100% { background: rgba(46, 160, 67, 0.15); }
}

.player-page--classic .code-line--deleted {
  background: rgba(248, 81, 73, 0.15);
  border-left: 3px solid #f85149;
  animation: classic-delete-pulse 2s ease-out;
}

.player-page--classic .code-line--deleted .line-number {
  color: #f85149;
}

@keyframes classic-delete-pulse {
  0% { background: rgba(248, 81, 73, 0.4); }
  50% { background: rgba(248, 81, 73, 0.25); }
  100% { background: rgba(248, 81, 73, 0.15); }
}

/* Classic Chat Section */
.player-page--classic .chat-section {
  background: var(--bg-card);
  border-left: 1px solid var(--border-color);
  box-shadow: none;
}

.player-page--classic .chat-section::before {
  display: none;
}

.player-page--classic .chat-header {
  background: transparent;
  border-bottom: 1px solid var(--border-color);
}

.player-page--classic .chat-header h3 {
  font-family: inherit;
  color: var(--color-primary);
  letter-spacing: normal;
  text-transform: none;
}

.player-page--classic .chat-message--user .message-avatar {
  background: var(--color-accent);
}

.player-page--classic .chat-message--assistant .message-avatar {
  background: var(--color-primary);
}

.player-page--classic .chat-message--user .message-content {
  background: var(--color-accent);
}

.player-page--classic .chat-message--assistant .message-content {
  background: var(--bg-secondary);
  border: none;
}

.player-page--classic .chat-message--assistant .message-content :deep(code) {
  background: rgba(var(--color-primary-rgb), 0.15);
  color: var(--color-primary);
}

.player-page--classic .chat-input {
  background: transparent;
}

/* Classic Footer */
.player-page--classic .player-footer {
  background: var(--bg-card);
  border-top: 1px solid var(--border-color);
  padding: var(--spacing-md) var(--spacing-xl);
}

.player-page--classic .timeline-slider {
  background: transparent;
  border: none;
  padding: 0;
}

.player-page--classic .timeline-slider::before,
.player-page--classic .timeline-slider::after {
  display: none; /* 移除胶片孔洞 */
}

.player-page--classic .progress-current {
  color: var(--color-primary);
  text-shadow: none;
}

.player-page--classic .player-controls :deep(.el-button) {
  background: var(--bg-secondary) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-primary) !important;
}

.player-page--classic .player-controls :deep(.el-button:hover:not(:disabled)) {
  background: var(--bg-tertiary) !important;
  border-color: var(--color-primary) !important;
  color: var(--color-primary) !important;
  box-shadow: none;
}

.player-page--classic .play-button {
  width: 44px !important;
  height: 44px !important;
  border-radius: 50% !important;
  background: var(--color-primary) !important;
  border: none !important;
  box-shadow: none !important;
  color: var(--bg-primary) !important;
  font-size: 18px !important;
}

.player-page--classic .play-button:hover {
  transform: none !important;
  opacity: 0.9;
}

.player-page--classic .player-controls :deep(.el-select .el-input__wrapper) {
  background: var(--bg-secondary) !important;
  border: 1px solid var(--border-color) !important;
  border-radius: var(--radius-md) !important;
}

.player-page--classic .player-controls :deep(.el-select .el-input__inner) {
  color: var(--text-primary) !important;
  font-weight: normal;
}

.player-page--classic kbd {
  background: var(--bg-tertiary);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  box-shadow: 0 2px 0 rgba(0, 0, 0, 0.2);
}

/* Classic: 移除播放动画 */
.player-page--classic.is-playing .timeline-slider::before,
.player-page--classic.is-playing .timeline-slider::after {
  animation: none;
}

/* Classic AI Analysis */
.player-page--classic .ai-analysis-btn {
  background: rgba(var(--color-primary-rgb), 0.1) !important;
  border-color: rgba(var(--color-primary-rgb), 0.3) !important;
  color: var(--color-primary) !important;
}

.player-page--classic .ai-analysis-btn:hover {
  background: rgba(var(--color-primary-rgb), 0.2) !important;
  border-color: var(--color-primary) !important;
}

.player-page--classic .quick-summary:hover {
  color: var(--color-primary);
}

.player-page--classic .streaming-cursor {
  color: var(--color-primary);
}

/* Classic Story Dialog */
.player-page--classic .story-milestones {
  border-top-color: var(--border-color);
}

.player-page--classic .story-milestones h4 {
  color: var(--color-primary);
}
</style>


