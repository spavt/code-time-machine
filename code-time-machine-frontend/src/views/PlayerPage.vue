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

// 代码高亮缓存：key = commitId_contentLength, value = 高亮后的 HTML
const highlightCache = new Map<string, string>()

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

// 播放器界面风格切换 - 支持多种风格
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
// 使用 commitId + 内容长度作为缓存 key，区分文件模式和方法模式
function getCachedHighlight(commitId: number, content: string): string {
  if (!content) return ''

  // 使用 commitId + 内容长度 作为缓存 key（方法模式下内容长度不同）
  const cacheKey = `${commitId}_${content.length}`
  
  // 检查缓存
  const cached = highlightCache.get(cacheKey)
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

  highlightCache.set(cacheKey, highlighted)
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

    // DEBUG: 追踪数据流
    console.log('[Watch] trackingMode:', trackingMode.value)
    console.log('[Watch] selectedMethod:', selectedMethod.value)
    console.log('[Watch] commitContent 行数:', commitContent?.split('\n').length)

    // 根据追踪模式设置当前代码
    if (trackingMode.value === 'method' && selectedMethod.value && commitContent != null) {
      // 方法模式：后端已返回提取的方法代码，直接使用
      // 如果后端返回的是完整文件（向后兼容），则在前端提取
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
        // previousCode 也应该是方法代码
      }
      console.log('[Watch] 最终 currentCode 行数:', currentCode.value.split('\n').length)
    } else {
      // 文件模式：显示完整文件
      currentCode.value = commitContent == null ? '// 加载中...' : commitContent
    }
    // 注：移除了 changeKey.value++ 以避免 DOM 重建导致滚动位置重置

    // ========== 立即执行滚动（不等待 diff API） ==========
    // 自动滚动到第一个变化行（等待 DOM 完全渲染）
    await nextTick()
    // 再等待一个微任务周期，确保 computed 属性都计算完成
    await nextTick()
    // 检查是否被取消（帧已切换）
    if (requestId !== commitLoadToken) return
    // 立即执行滚动，不再使用 setTimeout 延迟
    scrollToFirstChange()

    // ========== 以下为后台任务，不阻塞滚动 ==========
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

    // 2. 获取 diff（如果有上一个版本）- 后台获取，不阻塞 UI
    if (oldCommit?.commitHash) {
      fileApi.getDiff(
        repoId.value,
        oldCommit.commitHash,
        commit.commitHash,
        filePath.value
      ).then((diffResult) => {
        if (requestId !== commitLoadToken) return // 帧已切换，忽略结果
        if (diffResult?.diff) {
          const { additions, deletions } = countDiffStats(diffResult.diff)
          commit.additions = additions
          commit.deletions = deletions
          // 更新上下文（可选，如果需要的话）
        }
      }).catch((e) => {
        console.warn('获取diff失败:', e)
      })
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

    // 加载智能推荐问题 - 后台获取，不阻塞 UI
    if (commit.id) {
      chat.getSuggestions(commit.id).then((result) => {
        if (requestId !== commitLoadToken) return // 帧已切换，忽略结果
        suggestions.value = result
      }).catch((e) => {
        console.warn('获取推荐问题失败:', e)
        suggestions.value = []
      })
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

    // 匹配方法定义（支持 Java、JS、TS、Python、Go）
    if (!foundStart && (
      line.includes(`${methodName}(`) ||
      line.includes(`${methodName} (`) ||
      line.match(new RegExp(`\\b${methodName}\\s*\\(`))
    )) {
      const trimmed = line.trim()
      // 检查是否是方法定义（不是调用）
      const isDefinition = 
        // 传统 function 关键字
        trimmed.startsWith('function ') ||
        trimmed.startsWith('async function ') ||
        // 访问修饰符
        trimmed.startsWith('public ') ||
        trimmed.startsWith('private ') ||
        trimmed.startsWith('protected ') ||
        trimmed.startsWith('static ') ||
        // 箭头函数赋值
        line.includes('=>') ||
        // TypeScript/JavaScript 类方法 (无修饰符): methodName(params) { 或 async methodName(
        trimmed.match(new RegExp(`^(async\\s+)?${methodName}\\s*[(<]`)) ||
        // Java/C#: returnType methodName(
        line.match(/^\s*(public|private|protected|static|final|\w+)\s+\w+\s+\w+\s*\(/) ||
        // Python: def methodName(
        trimmed.startsWith('def ') ||
        trimmed.startsWith('async def ') ||
        // Go: func methodName(
        trimmed.startsWith('func ') ||
        // 变量赋值: const/let/var name = function/arrow
        line.match(/^\s*(const|let|var)\s+\w+\s*=/)

      if (isDefinition) {
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
        <!-- 版本信息移到左边 -->
        <div class="commit-info" v-if="player.currentCommit.value">
          <span class="commit-order">#{{ player.currentIndex.value + 1 }}/{{ player.totalFrames.value }}</span>
          <span class="commit-hash">{{ player.currentCommit.value.shortHash }}</span>
        </div>
      </div>
      <div class="header-right">
        <!-- 方法级追踪 -->
        <div class="tracking-controls">
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
@import "./PlayerPage.css";
</style>









