import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Repository, CommitRecord, FileChange, AiAnalysis, ContributorStats } from '@/types'
import { repositoryApi, commitApi } from '@/api'

export const useRepositoryStore = defineStore('repository', () => {
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
  const commitsTotal = ref(0)
  const commitsPage = ref(1)
  const commitsPageSize = 100
  const hasRepo = computed(() => currentRepo.value !== null)
  const isAnalyzing = computed(() => currentRepo.value?.status === 1)
  const isAnalyzed = computed(() => currentRepo.value?.status === 2)

  const totalCommits = computed(() => currentRepo.value?.totalCommits || 0)
  const totalAuthors = computed(() => contributors.value.length)

  const commitsByOrder = computed(() => {
    return [...commits.value].sort((a, b) => a.commitOrder - b.commitOrder)
  })

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

      if (repo.status === 1) {
        await pollAnalyzeProgress(repo.id)
      }

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

  async function fetchMoreHistory(repoId: number, depth: number = 500) {
    loading.value = true
    error.value = null

    try {
      const repo = await repositoryApi.fetchMoreHistory(repoId, depth)
      currentRepo.value = repo

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

  async function fetchRepoDetail(id: number) {
    loading.value = true
    error.value = null

    try {
      const repo = await repositoryApi.getById(id)
      currentRepo.value = repo

      await fetchCommits(id)

      try {
        const overview = await repositoryApi.getOverview(id)
        contributors.value = overview.topContributors || []
      } catch {
      }

      return repo
    } catch (err) {
      error.value = (err as Error).message
      throw err
    } finally {
      loading.value = false
    }
  }

  async function fetchCommits(repoId: number, page = 1, pageSize = 100) {
    try {
      const result = await commitApi.getList(repoId, page, pageSize)
      commits.value = result.list
      commitsTotal.value = result.total
      commitsPage.value = page
      return result
    } catch (err) {
      error.value = (err as Error).message
      throw err
    }
  }

  async function loadMoreCommits(repoId: number) {
    try {
      const nextPage = commitsPage.value + 1
      const result = await commitApi.getList(repoId, nextPage, commitsPageSize)
      commits.value = [...commits.value, ...result.list]
      commitsPage.value = nextPage
      return result
    } catch (err) {
      error.value = (err as Error).message
      throw err
    }
  }

  const hasMoreCommits = computed(() => {
    return commits.value.length < commitsTotal.value
  })

  async function selectCommit(commit: CommitRecord) {
    currentCommit.value = commit

    try {
      const changes = await commitApi.getFileChanges(commit.id)
      fileChanges.value = changes

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

  function clearCurrentRepo() {
    currentRepo.value = null
    commits.value = []
    currentCommit.value = null
    fileChanges.value = []
    currentAnalysis.value = null
    contributors.value = []
    analyzeProgress.value = 0
  }

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
    hasRepo,
    isAnalyzing,
    isAnalyzed,
    totalCommits,
    totalAuthors,
    commitsByOrder,
    commitsTotal,
    hasMoreCommits,
    fetchRepositories,
    analyzeRepository,
    fetchRepoDetail,
    fetchCommits,
    loadMoreCommits,
    selectCommit,
    triggerAnalysis,
    clearCurrentRepo,
    deleteRepository,
    fetchMoreHistory
  }
})
