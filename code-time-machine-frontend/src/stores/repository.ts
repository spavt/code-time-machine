// =====================================================
// AI代码时光机 - 仓库状态管理
// =====================================================

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Repository, CommitRecord, FileChange, AiAnalysis, ContributorStats } from '@/types'
import { repositoryApi, commitApi } from '@/api'

export const useRepositoryStore = defineStore('repository', () => {
  // 状态
  const repositories = ref<Repository[]>([])
  const currentRepo = ref<Repository | null>(null)
  const commits = ref<CommitRecord[]>([])
  const currentCommit = ref<CommitRecord | null>(null)
  const fileChanges = ref<FileChange[]>([])
  const currentAnalysis = ref<AiAnalysis | null>(null)
  const contributors = ref<ContributorStats[]>([])

  const loading = ref(false)
  const analyzing = ref(false)
  const analyzeProgress = ref(0)
  const error = ref<string | null>(null)
  const pollIntervalMs = 2000

  // 计算属性
  const hasRepo = computed(() => currentRepo.value !== null)
  const isAnalyzing = computed(() => currentRepo.value?.status === 1)
  const isAnalyzed = computed(() => currentRepo.value?.status === 2)

  const totalCommits = computed(() => currentRepo.value?.totalCommits || 0)
  const totalAuthors = computed(() => contributors.value.length)

  const commitsByOrder = computed(() => {
    return [...commits.value].sort((a, b) => a.commitOrder - b.commitOrder)
  })

  // Actions

  // 获取仓库列表
  async function fetchRepositories() {
    loading.value = true
    error.value = null
    try {
      const result = await repositoryApi.getList(1, 100)
      repositories.value = result.list
    } catch (err) {
      error.value = (err as Error).message
    } finally {
      loading.value = false
    }
  }

  // 分析新仓库（支持高级选项）
  async function analyzeRepository(url: string, options?: {
    depth?: number
    since?: string
    until?: string
    pathFilters?: string[]
    shallow?: boolean
  }) {
    analyzing.value = true
    analyzeProgress.value = 0
    error.value = null

    try {
      const repo = await repositoryApi.analyzeByUrl(url, options)
      currentRepo.value = repo

      // 轮询分析进度
      if (repo.status === 1) {
        await pollAnalyzeProgress(repo.id)
      }

      // 添加到列表
      const existingIndex = repositories.value.findIndex(r => r.id === repo.id)
      if (existingIndex >= 0) {
        repositories.value[existingIndex] = repo
      } else {
        repositories.value.unshift(repo)
      }

      return repo
    } catch (err) {
      error.value = (err as Error).message
      throw err
    } finally {
      analyzing.value = false
    }
  }

  // 增量获取更多历史
  async function fetchMoreHistory(repoId: number, depth: number = 500) {
    loading.value = true
    error.value = null

    try {
      const repo = await repositoryApi.fetchMoreHistory(repoId, depth)
      currentRepo.value = repo

      // 更新列表中的仓库
      const existingIndex = repositories.value.findIndex(r => r.id === repoId)
      if (existingIndex >= 0) {
        repositories.value[existingIndex] = repo
      }

      return repo
    } catch (err) {
      error.value = (err as Error).message
      throw err
    } finally {
      loading.value = false
    }
  }

  // 轮询分析进度
  async function pollAnalyzeProgress(repoId: number) {
    while (true) {
      try {
        const { progress, status } = await repositoryApi.getAnalyzeProgress(repoId)
        analyzeProgress.value = progress

        if (currentRepo.value && currentRepo.value.id === repoId) {
          currentRepo.value.analyzeProgress = progress
          currentRepo.value.status = status as 0 | 1 | 2 | 3
        }

        if (status === 2) {
          await fetchRepoDetail(repoId)
          return
        }

        if (status === 3) {
          throw new Error('分析失败')
        }

        if (status !== 1 || progress >= 100) {
          return
        }
      } catch (err) {
        console.error('Poll progress error:', err)
        throw err
      }

      await new Promise((resolve) => setTimeout(resolve, pollIntervalMs))
    }
  }

  // 获取仓库详情
  async function fetchRepoDetail(id: number) {
    loading.value = true
    error.value = null

    try {
      const repo = await repositoryApi.getById(id)
      currentRepo.value = repo

      // 同时获取提交列表
      await fetchCommits(id)

      // 获取概览信息
      try {
        const overview = await repositoryApi.getOverview(id)
        contributors.value = overview.topContributors || []
      } catch {
        // 概览信息非必需
      }

      return repo
    } catch (err) {
      error.value = (err as Error).message
      throw err
    } finally {
      loading.value = false
    }
  }

  // 获取提交列表
  async function fetchCommits(repoId: number, page = 1, pageSize = 100) {
    try {
      const result = await commitApi.getList(repoId, page, pageSize)
      commits.value = result.list
      return result
    } catch (err) {
      error.value = (err as Error).message
      throw err
    }
  }

  // 选择提交
  async function selectCommit(commit: CommitRecord) {
    currentCommit.value = commit

    try {
      // 获取文件变更
      const changes = await commitApi.getFileChanges(commit.id)
      fileChanges.value = changes

      // 获取AI分析
      try {
        const analysis = await commitApi.getAiAnalysis(commit.id)
        currentAnalysis.value = analysis
      } catch {
        currentAnalysis.value = null
      }
    } catch (err) {
      error.value = (err as Error).message
    }
  }

  // 触发AI分析
  async function triggerAnalysis(commitId: number) {
    try {
      const analysis = await commitApi.triggerAnalysis(commitId)
      currentAnalysis.value = analysis
      return analysis
    } catch (err) {
      error.value = (err as Error).message
      throw err
    }
  }

  // 清理状态
  function clearCurrentRepo() {
    currentRepo.value = null
    commits.value = []
    currentCommit.value = null
    fileChanges.value = []
    currentAnalysis.value = null
    contributors.value = []
    analyzeProgress.value = 0
  }

  // 删除仓库
  async function deleteRepository(id: number) {
    try {
      await repositoryApi.delete(id)
      repositories.value = repositories.value.filter(r => r.id !== id)

      if (currentRepo.value?.id === id) {
        clearCurrentRepo()
      }
    } catch (err) {
      error.value = (err as Error).message
      throw err
    }
  }

  return {
    // 状态
    repositories,
    currentRepo,
    commits,
    currentCommit,
    fileChanges,
    currentAnalysis,
    contributors,
    loading,
    analyzing,
    analyzeProgress,
    error,

    // 计算属性
    hasRepo,
    isAnalyzing,
    isAnalyzed,
    totalCommits,
    totalAuthors,
    commitsByOrder,

    // Actions
    fetchRepositories,
    analyzeRepository,
    fetchRepoDetail,
    fetchCommits,
    selectCommit,
    triggerAnalysis,
    clearCurrentRepo,
    deleteRepository,
    fetchMoreHistory
  }
})
