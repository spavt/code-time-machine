// =====================================================
// AI代码时光机 - 时间线播放器状态管理
// =====================================================

import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { TimelineCommit, FileTimeline, DiffLine } from '@/types'
import { fileApi } from '@/api'

export const useTimelineStore = defineStore('timeline', () => {
  // 状态
  const currentFile = ref<string>('')
  const currentRepoId = ref<number | null>(null)
  const timeline = ref<FileTimeline | null>(null)
  const commits = ref<TimelineCommit[]>([])
  const contentLoading = new Set<number>()
  
  // 播放器状态
  const currentIndex = ref(0)
  const isPlaying = ref(false)
  const playSpeed = ref(1000) // 毫秒
  const playTimer = ref<ReturnType<typeof setInterval> | null>(null)
  
  // UI状态
  const showDiff = ref(true)
  const autoScroll = ref(true)
  const highlightChanges = ref(true)
  
  // 加载状态
  const loading = ref(false)
  const error = ref<string | null>(null)

  // 计算属性
  const totalFrames = computed(() => commits.value.length)
  const hasTimeline = computed(() => commits.value.length > 0)
  const progress = computed(() => {
    if (totalFrames.value === 0) return 0
    return (currentIndex.value / (totalFrames.value - 1)) * 100
  })
  
  const currentCommit = computed(() => {
    if (currentIndex.value < 0 || currentIndex.value >= commits.value.length) {
      return null
    }
    return commits.value[currentIndex.value]
  })
  
  const previousCommit = computed(() => {
    if (currentIndex.value <= 0) return null
    return commits.value[currentIndex.value - 1]
  })
  
  const nextCommit = computed(() => {
    if (currentIndex.value >= commits.value.length - 1) return null
    return commits.value[currentIndex.value + 1]
  })
  
  const currentContent = computed(() => {
    return currentCommit.value?.content || ''
  })
  
  const currentDiffLines = computed(() => {
    return currentCommit.value?.diffLines || []
  })
  
  // 速度选项
  const speedOptions = [
    { label: '0.5x', value: 2000 },
    { label: '1x', value: 1000 },
    { label: '2x', value: 500 },
    { label: '3x', value: 333 },
    { label: '5x', value: 200 }
  ]

  // Actions
  
  // 加载文件时间线
  async function loadTimeline(repoId: number, filePath: string) {
    loading.value = true
    error.value = null
    currentFile.value = filePath
    currentRepoId.value = repoId
    
    try {
      const data = await fileApi.getTimeline(repoId, filePath, false)
      timeline.value = data
      commits.value = data.commits || []
      currentIndex.value = 0
      
      // 加载第一个提交的内容
      if (commits.value.length > 0) {
        await loadCommitContent(0)
      }
      
      return data
    } catch (err) {
      error.value = (err as Error).message
      throw err
    } finally {
      loading.value = false
    }
  }
  
  // 加载特定提交的内容
  async function loadCommitContent(index: number) {
    if (index < 0 || index >= commits.value.length) return
    
    const commit = commits.value[index]
    if (commit.content != null) return
    if (commit.changeType === 'DELETE') {
      commits.value[index].content = ''
      return
    }
    if (!currentRepoId.value || !currentFile.value) return
    if (contentLoading.has(commit.id)) return

    contentLoading.add(commit.id)
    
    try {
      const { content } = await fileApi.getContent(currentRepoId.value, commit.id, currentFile.value)
      commits.value[index].content = content ?? ''
    } catch (err) {
      console.error('Load content error:', err)
    } finally {
      contentLoading.delete(commit.id)
    }
  }

  // 播放控制
  function play() {
    if (isPlaying.value) return
    if (currentIndex.value >= totalFrames.value - 1) {
      currentIndex.value = 0 // 从头开始
    }
    
    isPlaying.value = true
    
    playTimer.value = setInterval(() => {
      if (currentIndex.value < totalFrames.value - 1) {
        currentIndex.value++
        loadCommitContent(currentIndex.value)
      } else {
        pause()
      }
    }, playSpeed.value)
  }
  
  function pause() {
    isPlaying.value = false
    if (playTimer.value) {
      clearInterval(playTimer.value)
      playTimer.value = null
    }
  }
  
  function togglePlay() {
    if (isPlaying.value) {
      pause()
    } else {
      play()
    }
  }
  
  function stop() {
    pause()
    currentIndex.value = 0
  }
  
  function prev() {
    pause()
    if (currentIndex.value > 0) {
      currentIndex.value--
      loadCommitContent(currentIndex.value)
    }
  }
  
  function next() {
    pause()
    if (currentIndex.value < totalFrames.value - 1) {
      currentIndex.value++
      loadCommitContent(currentIndex.value)
    }
  }
  
  function goTo(index: number) {
    pause()
    if (index >= 0 && index < totalFrames.value) {
      currentIndex.value = index
      loadCommitContent(currentIndex.value)
    }
  }
  
  function goToFirst() {
    goTo(0)
  }
  
  function goToLast() {
    goTo(totalFrames.value - 1)
  }
  
  // 设置播放速度
  function setSpeed(speed: number) {
    playSpeed.value = speed
    
    // 如果正在播放，需要重新设置定时器
    if (isPlaying.value) {
      pause()
      play()
    }
  }
  
  // 切换Diff显示
  function toggleDiff() {
    showDiff.value = !showDiff.value
  }
  
  // 解析Diff文本为行数组
  function parseDiff(diffText: string): DiffLine[] {
    if (!diffText) return []
    
    const lines = diffText.split('\n')
    const result: DiffLine[] = []
    
    let oldLine = 0
    let newLine = 0
    
    for (const line of lines) {
      if (line.startsWith('@@')) {
        // 解析行号信息
        const match = line.match(/@@ -(\d+),?\d* \+(\d+),?\d* @@/)
        if (match) {
          oldLine = parseInt(match[1]) - 1
          newLine = parseInt(match[2]) - 1
        }
        continue
      }
      
      if (line.startsWith('+') && !line.startsWith('+++')) {
        newLine++
        result.push({
          lineNumber: result.length + 1,
          newLineNumber: newLine,
          type: 'add',
          content: line.substring(1)
        })
      } else if (line.startsWith('-') && !line.startsWith('---')) {
        oldLine++
        result.push({
          lineNumber: result.length + 1,
          oldLineNumber: oldLine,
          type: 'delete',
          content: line.substring(1)
        })
      } else if (line.startsWith(' ')) {
        oldLine++
        newLine++
        result.push({
          lineNumber: result.length + 1,
          oldLineNumber: oldLine,
          newLineNumber: newLine,
          type: 'normal',
          content: line.substring(1)
        })
      }
    }
    
    return result
  }
  
  // 清理状态
  function clear() {
    pause()
    currentFile.value = ''
    currentRepoId.value = null
    timeline.value = null
    commits.value = []
    currentIndex.value = 0
    error.value = null
  }

  // 监听速度变化，更新播放
  watch(playSpeed, () => {
    if (isPlaying.value) {
      pause()
      play()
    }
  })

  return {
    // 状态
    currentFile,
    timeline,
    commits,
    currentIndex,
    isPlaying,
    playSpeed,
    showDiff,
    autoScroll,
    highlightChanges,
    loading,
    error,
    
    // 计算属性
    totalFrames,
    hasTimeline,
    progress,
    currentCommit,
    previousCommit,
    nextCommit,
    currentContent,
    currentDiffLines,
    speedOptions,
    
    // Actions
    loadTimeline,
    loadCommitContent,
    play,
    pause,
    togglePlay,
    stop,
    prev,
    next,
    goTo,
    goToFirst,
    goToLast,
    setSpeed,
    toggleDiff,
    parseDiff,
    clear
  }
})
