
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { FileTimeline, TimelineCommit } from '@/types'
import { fileApi } from '@/api'

export const usePlayerStore = defineStore('player', () => {
  const timeline = ref<FileTimeline | null>(null)
  const currentIndex = ref(0)
  const isPlaying = ref(false)
  const speed = ref(1500)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const commits = computed(() => timeline.value?.commits || [])
  const totalFrames = computed(() => commits.value.length)
  const currentCommit = computed(() => commits.value[currentIndex.value] || null)
  const previousCommit = computed(() => 
    currentIndex.value > 0 ? commits.value[currentIndex.value - 1] : null
  )
  const hasNext = computed(() => currentIndex.value < totalFrames.value - 1)
  const hasPrev = computed(() => currentIndex.value > 0)
  const progress = computed(() => 
    totalFrames.value > 0 ? (currentIndex.value / (totalFrames.value - 1)) * 100 : 0
  )

  let playTimer: ReturnType<typeof setInterval> | null = null

  async function loadTimeline(repoId: number, filePath: string) {
    loading.value = true
    error.value = null
    
    try {
      const data = await fileApi.getTimeline(repoId, filePath, false)
      timeline.value = data
      currentIndex.value = 0
    } catch (err) {
      error.value = (err as Error).message
      throw err
    } finally {
      loading.value = false
    }
  }

  function play() {
    if (isPlaying.value || !hasNext.value) return
    
    isPlaying.value = true
    playTimer = setInterval(() => {
      if (hasNext.value) {
        currentIndex.value++
      } else {
        pause()
      }
    }, speed.value)
  }

  function pause() {
    isPlaying.value = false
    if (playTimer) {
      clearInterval(playTimer)
      playTimer = null
    }
  }

  function togglePlay() {
    if (isPlaying.value) {
      pause()
    } else {
      play()
    }
  }

  function next() {
    pause()
    if (hasNext.value) {
      currentIndex.value++
    }
  }

  function prev() {
    pause()
    if (hasPrev.value) {
      currentIndex.value--
    }
  }

  function goTo(index: number) {
    pause()
    if (index >= 0 && index < totalFrames.value) {
      currentIndex.value = index
    }
  }

  function goToFirst() {
    goTo(0)
  }

  function goToLast() {
    goTo(totalFrames.value - 1)
  }

  function setSpeed(ms: number) {
    speed.value = ms
    if (isPlaying.value) {
      pause()
      play()
    }
  }

  function reset() {
    pause()
    timeline.value = null
    currentIndex.value = 0
    error.value = null
  }

  return {
    timeline,
    currentIndex,
    isPlaying,
    speed,
    loading,
    error,
    
    commits,
    totalFrames,
    currentCommit,
    previousCommit,
    hasNext,
    hasPrev,
    progress,
    
    loadTimeline,
    play,
    pause,
    togglePlay,
    next,
    prev,
    goTo,
    goToFirst,
    goToLast,
    setSpeed,
    reset
  }
})
