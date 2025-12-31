// =====================================================
// AI代码时光机 - API服务层
// =====================================================

import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import type {
  Repository,
  CommitRecord,
  FileChange,
  FileSnapshot,
  AiAnalysis,
  ChatMessage,
  FileTimeline,
  ApiResponse,
  PageResult,
  ContributorStats,
  SearchFilter
} from '@/types'

// 创建axios实例
const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
api.interceptors.request.use(
  (config) => {
    // 可以在这里添加token等
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
api.interceptors.response.use(
  (response: AxiosResponse<ApiResponse<unknown>>) => {
    const res = response.data
    if (res.code !== 200) {
      console.error('API Error:', res.message)
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data as unknown as AxiosResponse
  },
  (error) => {
    console.error('Request Error:', error)
    return Promise.reject(error)
  }
)

// =====================================================
// 仓库相关API
// =====================================================
export const repositoryApi = {
  // 获取仓库列表
  getList(page = 1, pageSize = 10): Promise<PageResult<Repository>> {
    return api.get('/repository/list', { params: { page, pageSize } })
  },

  // 获取单个仓库详情
  getById(id: number): Promise<Repository> {
    return api.get(`/repository/${id}`)
  },

  // 通过URL分析仓库（支持高级选项）
  analyzeByUrl(url: string, options?: {
    depth?: number
    since?: string
    until?: string
    pathFilters?: string[]
    shallow?: boolean
  }): Promise<Repository> {
    return api.post('/repository/analyze', { url, ...options })
  },

  // 获取分析进度
  getAnalyzeProgress(id: number): Promise<{ progress: number; status: number }> {
    return api.get(`/repository/${id}/progress`)
  },

  // 删除仓库
  delete(id: number): Promise<void> {
    return api.delete(`/repository/${id}`)
  },

  // 获取仓库统计概览
  getOverview(id: number): Promise<{
    totalCommits: number
    totalAuthors: number
    totalAdditions: number
    totalDeletions: number
    firstCommit: string
    lastCommit: string
    topContributors: ContributorStats[]
  }> {
    return api.get(`/repository/${id}/overview`)
  },

  // 增量获取更多历史
  fetchMoreHistory(id: number, depth: number = 500): Promise<Repository> {
    return api.post(`/repository/${id}/fetch-more-history`, { depth })
  }
}

// =====================================================
// 提交记录相关API
// =====================================================
export const commitApi = {
  // 获取仓库的提交列表
  getList(
    repoId: number,
    page = 1,
    pageSize = 50,
    filter?: SearchFilter
  ): Promise<PageResult<CommitRecord>> {
    return api.get(`/commit/list/${repoId}`, {
      params: { page, pageSize, ...filter }
    })
  },

  // 获取单个提交详情
  getById(id: number): Promise<CommitRecord> {
    return api.get(`/commit/${id}`)
  },

  // 获取提交的文件变更列表
  getFileChanges(commitId: number): Promise<FileChange[]> {
    return api.get(`/commit/${commitId}/files`)
  },

  // 获取提交的AI分析
  getAiAnalysis(commitId: number): Promise<AiAnalysis> {
    return api.get(`/commit/${commitId}/analysis`)
  },

  // 触发AI分析
  triggerAnalysis(commitId: number): Promise<AiAnalysis> {
    return api.post(`/commit/${commitId}/analyze`)
  },

  // 获取两个提交之间的diff
  getDiff(fromCommitId: number, toCommitId: number, filePath: string): Promise<{
    oldContent: string
    newContent: string
    diffLines: Array<{ type: string; content: string }>
  }> {
    return api.get('/commit/diff', {
      params: { fromCommitId, toCommitId, filePath }
    })
  },

  // 获取提交的统计信息（按需计算）
  getStats(commitId: number): Promise<{
    additions: number
    deletions: number
    filesChanged: number
    calculated: boolean
  }> {
    return api.get(`/commit/${commitId}/stats`)
  }
}

// =====================================================
// 文件相关API
// =====================================================
export const fileApi = {
  // 获取仓库的文件树
  getFileTree(repoId: number, commitId?: number): Promise<Array<{
    path: string
    type: 'file' | 'folder'
    children?: Array<{ path: string; type: 'file' | 'folder' }>
  }>> {
    return api.get(`/file/tree/${repoId}`, { params: { commitId } })
  },

  // 获取文件的演化时间线
  getTimeline(repoId: number, filePath: string, includeContent = false): Promise<FileTimeline> {
    return api.get(`/file/timeline/${repoId}`, { params: { filePath, includeContent } })
  },

  // 获取文件在特定提交时的快照
  getSnapshot(repoId: number, commitId: number, filePath: string): Promise<FileSnapshot> {
    return api.get(`/file/snapshot`, {
      params: { repoId, commitId, filePath }
    })
  },

  // 获取文件内容
  getContent(repoId: number, commitId: number, filePath: string): Promise<{
    content: string
    language: string
    lineCount: number
  }> {
    return api.get(`/file/content`, {
      params: { repoId, commitId, filePath }
    })
  },

  // 搜索文件
  search(repoId: number, keyword: string): Promise<Array<{
    filePath: string
    matchCount: number
    commits: number
  }>> {
    return api.get(`/file/search/${repoId}`, { params: { keyword } })
  },

  // 获取文件演进故事
  getEvolutionStory(repoId: number, filePath: string): Promise<{
    story: string
    keyMilestones: Array<{ commitHash: string; summary: string }>
  }> {
    return api.get(`/file/evolution-story/${repoId}`, { params: { filePath } })
  },

  // 获取文件diff
  getDiff(repoId: number, fromCommit: string, toCommit: string, filePath: string): Promise<{ diff: string }> {
    return api.get(`/file/diff/${repoId}`, { params: { fromCommit, toCommit, filePath } })
  },

  // 获取文件中的方法列表
  getMethods(repoId: number, commitId: number, filePath: string): Promise<Array<{
    name: string
    signature: string
    startLine: number
    endLine: number
    content: string
    className?: string
    parameterCount: number
  }>> {
    return api.get(`/file/methods/${repoId}`, { params: { commitId, filePath } })
  },

  // 获取方法演化时间线
  getMethodTimeline(repoId: number, filePath: string, methodName: string): Promise<Array<{
    commitId: number
    commitHash: string
    shortHash: string
    authorName: string
    commitMessage: string
    commitTime: string
    additions: number
    deletions: number
    content: string
  }>> {
    return api.get(`/file/method-timeline/${repoId}`, { params: { filePath, methodName } })
  },

  // 批量获取文件内容（用于预加载优化）
  getBatchContent(repoId: number, commitIds: number[], filePath: string): Promise<Record<number, {
    content: string
    language: string
    lineCount: number
  }>> {
    return api.post('/file/content/batch', { repoId, commitIds, filePath })
  }
}


// =====================================================
// AI聊天相关API
// =====================================================
export const chatApi = {
  // 发送问题
  ask(params: {
    sessionId: string
    repoId?: number
    commitId?: number
    filePath?: string
    question: string
    context?: string
  }): Promise<{ answer: string; tokensUsed: number }> {
    return api.post('/ai/ask', params)
  },

  // 发送问题（流式输出）
  async askStream(
    params: {
      sessionId: string
      repoId?: number
      commitId?: number
      filePath?: string
      question: string
      context?: string
    },
    onChunk: (chunk: string) => void,
    onComplete?: () => void,
    onError?: (error: Error) => void
  ): Promise<void> {
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api'

    try {
      const response = await fetch(`${baseUrl}/ai/ask/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify(params)
      })

      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`)
      }

      const reader = response.body?.getReader()
      if (!reader) {
        throw new Error('No reader available')
      }

      const decoder = new TextDecoder()

      while (true) {
        const { done, value } = await reader.read()
        if (done) {
          onComplete?.()
          break
        }

        const text = decoder.decode(value, { stream: true })
        // 解析 SSE 格式: data:content\n\n
        const lines = text.split('\n')
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const content = line.slice(5)
            if (content && content !== '[DONE]') {
              onChunk(content)
            }
          } else if (line.trim() && !line.startsWith(':')) {
            // 非 SSE 格式，直接作为内容
            onChunk(line)
          }
        }
      }
    } catch (error) {
      console.error('Stream error:', error)
      onError?.(error as Error)
    }
  },

  // 获取对话历史
  getHistory(sessionId: string): Promise<ChatMessage[]> {
    return api.get(`/ai/history/${sessionId}`)
  },

  // 清除对话历史
  clearHistory(sessionId: string): Promise<void> {
    return api.delete(`/ai/history/${sessionId}`)
  },

  // 获取推荐问题
  getSuggestions(commitId: number): Promise<string[]> {
    return api.get(`/ai/suggestions/${commitId}`)
  },

  // 生成学习路径（AI 调用可能比较慢，增加超时时间）
  getLearningPath(repoId: number): Promise<{ learningPath: string; metadata: string }> {
    return api.get(`/ai/learning-path/${repoId}`, { timeout: 120000 })
  }
}

// =====================================================
// 统计相关API
// =====================================================
export const statsApi = {
  // 获取代码行数变化趋势
  getLinesTrend(repoId: number): Promise<{
    dates: string[]
    additions: number[]
    deletions: number[]
    total: number[]
  }> {
    return api.get(`/stats/lines-trend/${repoId}`)
  },

  // 获取提交频率统计
  getCommitFrequency(repoId: number): Promise<{
    dates: string[]
    counts: number[]
  }> {
    return api.get(`/stats/commit-frequency/${repoId}`)
  },

  // 获取贡献者排行
  getContributors(repoId: number): Promise<ContributorStats[]> {
    return api.get(`/stats/contributors/${repoId}`)
  },

  // 获取文件类型分布
  getFileTypes(repoId: number): Promise<Array<{
    extension: string
    count: number
    percentage: number
  }>> {
    return api.get(`/stats/file-types/${repoId}`)
  },

  // 获取变更类型分布
  getChangeTypes(repoId: number): Promise<Array<{
    category: string
    count: number
    percentage: number
  }>> {
    return api.get(`/stats/change-types/${repoId}`)
  },

  // 获取热力图数据(按小时/星期)
  getHeatmap(repoId: number): Promise<Array<{
    day: number // 0-6 周日到周六
    hour: number // 0-23
    count: number
  }>> {
    return api.get(`/stats/heatmap/${repoId}`)
  },

  // 获取文件热力图数据
  getFileHeatmap(repoId: number): Promise<Array<{
    filePath: string
    modifyCount: number
    heatLevel: number
  }>> {
    return api.get(`/stats/file-heatmap/${repoId}`)
  },

  // 获取活动热力图数据
  getActivityHeatmap(repoId: number): Promise<{
    matrix: number[][]
    dayLabels: string[]
    hourLabels: string[]
    byDayOfWeek: Record<number, number>
    byHour: Record<number, number>
    totalCommits: number
  }> {
    return api.get(`/stats/activity-heatmap/${repoId}`)
  }
}

// 导出默认实例
export default api
