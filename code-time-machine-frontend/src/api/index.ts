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

const api: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

api.interceptors.request.use(
  (config) => {
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

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

export const repositoryApi = {
  getList(page = 1, pageSize = 10): Promise<PageResult<Repository>> {
    return api.get('/repository/list', { params: { page, pageSize } })
  },

  getById(id: number): Promise<Repository> {
    return api.get(`/repository/${id}`)
  },

  analyzeByUrl(url: string, options?: {
    depth?: number
    since?: string
    until?: string
    pathFilters?: string[]
    shallow?: boolean
  }): Promise<Repository> {
    return api.post('/repository/analyze', { url, ...options })
  },

  getAnalyzeProgress(id: number): Promise<{ progress: number; status: number }> {
    return api.get(`/repository/${id}/progress`)
  },

  delete(id: number): Promise<void> {
    return api.delete(`/repository/${id}`)
  },

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

  fetchMoreHistory(id: number, depth: number = 500): Promise<Repository> {
    return api.post(`/repository/${id}/fetch-more-history`, { depth })
  }
}

export const commitApi = {
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

  getById(id: number): Promise<CommitRecord> {
    return api.get(`/commit/${id}`)
  },

  getFileChanges(commitId: number): Promise<FileChange[]> {
    return api.get(`/commit/${commitId}/files`)
  },

  getAiAnalysis(commitId: number): Promise<AiAnalysis> {
    return api.get(`/commit/${commitId}/analysis`)
  },

  triggerAnalysis(commitId: number): Promise<AiAnalysis> {
    return api.post(`/commit/${commitId}/analyze`)
  },

  getDiff(fromCommitId: number, toCommitId: number, filePath: string): Promise<{
    oldContent: string
    newContent: string
    diffLines: Array<{ type: string; content: string }>
  }> {
    return api.get('/commit/diff', {
      params: { fromCommitId, toCommitId, filePath }
    })
  },

  getStats(commitId: number): Promise<{
    additions: number
    deletions: number
    filesChanged: number
    calculated: boolean
  }> {
    return api.get(`/commit/${commitId}/stats`)
  }
}

export const fileApi = {
  getFileTree(repoId: number, commitId?: number): Promise<Array<{
    path: string
    type: 'file' | 'folder'
    children?: Array<{ path: string; type: 'file' | 'folder' }>
  }>> {
    return api.get(`/file/tree/${repoId}`, { params: { commitId } })
  },

  getTimeline(repoId: number, filePath: string, includeContent = false): Promise<FileTimeline> {
    return api.get(`/file/timeline/${repoId}`, { params: { filePath, includeContent } })
  },

  getSnapshot(repoId: number, commitId: number, filePath: string): Promise<FileSnapshot> {
    return api.get(`/file/snapshot`, {
      params: { repoId, commitId, filePath }
    })
  },

  getContent(repoId: number, commitId: number, filePath: string): Promise<{
    content: string
    language: string
    lineCount: number
  }> {
    return api.get(`/file/content`, {
      params: { repoId, commitId, filePath }
    })
  },

  search(repoId: number, keyword: string): Promise<Array<{
    filePath: string
    matchCount: number
    commits: number
  }>> {
    return api.get(`/file/search/${repoId}`, { params: { keyword } })
  },

  getEvolutionStory(repoId: number, filePath: string): Promise<{
    story: string
    keyMilestones: Array<{ commitHash: string; summary: string }>
  }> {
    return api.get(`/file/evolution-story/${repoId}`, { params: { filePath } })
  },

  getDiff(repoId: number, fromCommit: string, toCommit: string, filePath: string): Promise<{ diff: string }> {
    return api.get(`/file/diff/${repoId}`, { params: { fromCommit, toCommit, filePath } })
  },

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

  getBatchContent(repoId: number, commitIds: number[], filePath: string): Promise<Record<number, {
    content: string
    language: string
    lineCount: number
  }>> {
    return api.post('/file/content/batch', { repoId, commitIds, filePath })
  }
}

export const chatApi = {
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
        const lines = text.split('\n')
        for (const line of lines) {
          if (line.startsWith('data:')) {
            const content = line.slice(5)
            if (content && content !== '[DONE]') {
              onChunk(content)
            }
          } else if (line.trim() && !line.startsWith(':')) {
            onChunk(line)
          }
        }
      }
    } catch (error) {
      console.error('Stream error:', error)
      onError?.(error as Error)
    }
  },

  getHistory(sessionId: string): Promise<ChatMessage[]> {
    return api.get(`/ai/history/${sessionId}`)
  },

  clearHistory(sessionId: string): Promise<void> {
    return api.delete(`/ai/history/${sessionId}`)
  },

  getSuggestions(commitId: number): Promise<string[]> {
    return api.get(`/ai/suggestions/${commitId}`)
  },

  getLearningPath(repoId: number): Promise<{ learningPath: string; metadata: string }> {
    return api.get(`/ai/learning-path/${repoId}`, { timeout: 120000 })
  }
}

export const statsApi = {
  getLinesTrend(repoId: number): Promise<{
    dates: string[]
    additions: number[]
    deletions: number[]
    total: number[]
  }> {
    return api.get(`/stats/lines-trend/${repoId}`)
  },

  getCommitFrequency(repoId: number): Promise<{
    dates: string[]
    counts: number[]
  }> {
    return api.get(`/stats/commit-frequency/${repoId}`)
  },

  getContributors(repoId: number): Promise<ContributorStats[]> {
    return api.get(`/stats/contributors/${repoId}`)
  },

  getFileTypes(repoId: number): Promise<Array<{
    extension: string
    count: number
    percentage: number
  }>> {
    return api.get(`/stats/file-types/${repoId}`)
  },

  getChangeTypes(repoId: number): Promise<Array<{
    category: string
    count: number
    percentage: number
  }>> {
    return api.get(`/stats/change-types/${repoId}`)
  },

  getHeatmap(repoId: number): Promise<Array<{
    day: number
    hour: number
    count: number
  }>> {
    return api.get(`/stats/heatmap/${repoId}`)
  },

  getFileHeatmap(repoId: number): Promise<Array<{
    filePath: string
    modifyCount: number
    heatLevel: number
  }>> {
    return api.get(`/stats/file-heatmap/${repoId}`)
  },

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

export default api
