import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import type { TimelineCommit, FileTimeline, DiffLine } from '@/types'
import { fileApi } from '@/api'

export const useTimelineStore = defineStore('timeline', () => {
  const currentFile = ref<string>('')
  const currentRepoId = ref<number | null>(null)
  const timeline = ref<FileTimeline | null>(null)
  const commits = ref<TimelineCommit[]>([])
  const contentLoading = new Set<number>()
  const currentIndex = ref(0)
  const isPlaying = ref(false)
  const playSpeed = ref(1000)
  const playTimer = ref<ReturnType<typeof setInterval> | null>(null)
  const showDiff = ref(true)
  const autoScroll = ref(true)
  const highlightChanges = ref(true)
  const loading = ref(false)
  const error = ref<string | null>(null)
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

  const speedOptions = [
    { label: '0.5x', value: 2000 },
    { label: '1x', value: 1000 },
    { label: '2x', value: 500 },
    { label: '3x', value: 333 },
    { label: '5x', value: 200 }
  ]

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

  async function loadCommitContent(index: number) {
    if (index < 0 || index >= commits.value.length) return

    const commit = commits.value[index]
    if (!commit) return
    if (commit.content != null) return
    if (commit.changeType === 'DELETE') {
      commit.content = ''
      return
    }
    if (!currentRepoId.value || !currentFile.value) return
    if (contentLoading.has(commit.id)) return

    contentLoading.add(commit.id)

    try {
      const { content } = await fileApi.getContent(currentRepoId.value, commit.id, currentFile.value)
      if (commits.value[index]) {
        commits.value[index].content = content ?? ''
      }
    } catch (err) {
      console.error('Load content error:', err)
    } finally {
      contentLoading.delete(commit.id)
    }
  }

  function play() {
    if (isPlaying.value) return
    if (currentIndex.value >= totalFrames.value - 1) {
      currentIndex.value = 0
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

  function setSpeed(speed: number) {
    playSpeed.value = speed

    if (isPlaying.value) {
      pause()
      play()
    }
  }

  function toggleDiff() {
    showDiff.value = !showDiff.value
  }

  function parseDiff(diffText: string): DiffLine[] {
    if (!diffText) return []

    const lines = diffText.split('\n')
    const result: DiffLine[] = []

    let oldLine = 0
    let newLine = 0

    for (const line of lines) {
      if (line.startsWith('@@')) {
        const match = line.match(/@@ -(\d+),?\d* \+(\d+),?\d* @@/)
        if (match) {
          oldLine = parseInt(match[1] ?? '0') - 1
          newLine = parseInt(match[2] ?? '0') - 1
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

  function clear() {
    pause()
    currentFile.value = ''
    currentRepoId.value = null
    timeline.value = null
    commits.value = []
    currentIndex.value = 0
    error.value = null
  }

  watch(playSpeed, () => {
    if (isPlaying.value) {
      pause()
      play()
    }
  })

  return {
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
    totalFrames,
    hasTimeline,
    progress,
    currentCommit,
    previousCommit,
    nextCommit,
    currentContent,
    currentDiffLines,
    speedOptions,
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
