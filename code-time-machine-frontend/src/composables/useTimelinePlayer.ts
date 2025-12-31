import { ref, computed, watch, onUnmounted } from 'vue'
import type { TimelineCommit, DiffLine } from '@/types'
import { diffArrays } from 'diff'

export interface PlayerOptions {
  autoPlay?: boolean
  defaultSpeed?: number
  loop?: boolean
  beforeAdvance?: (nextIndex: number, currentIndex: number) => Promise<boolean> | boolean
}

export function useTimelinePlayer(
  commits: () => TimelineCommit[],
  options: PlayerOptions = {}
) {
  const { autoPlay = false, defaultSpeed = 1000, loop = false, beforeAdvance } = options

  const isPlaying = ref(false)
  const currentIndex = ref(0)
  const speed = ref(defaultSpeed)
  const playDirection = ref<'forward' | 'backward'>('forward')

  let playTimer: ReturnType<typeof setInterval> | null = null
  let pendingAdvance = false
  let advanceToken = 0

  const totalFrames = computed(() => commits().length)
  const hasNext = computed(() => currentIndex.value < totalFrames.value - 1)
  const hasPrev = computed(() => currentIndex.value > 0)
  const progress = computed(() =>
    totalFrames.value > 0 ? (currentIndex.value / (totalFrames.value - 1)) * 100 : 0
  )

  const currentCommit = computed(() => {
    const list = commits()
    return list[currentIndex.value] || null
  })

  const previousCommit = computed(() => {
    const list = commits()
    return currentIndex.value > 0 ? list[currentIndex.value - 1] : null
  })
  function play() {
    if (isPlaying.value) return
    if (!hasNext.value && playDirection.value === 'forward') {
      if (loop) {
        currentIndex.value = 0
      } else {
        return
      }
    }

    isPlaying.value = true
    pendingAdvance = false
    advanceToken += 1
    const token = advanceToken
    playTimer = setInterval(() => {
      let nextIndex: number | null = null
      if (playDirection.value === 'forward') {
        if (hasNext.value) {
          nextIndex = currentIndex.value + 1
        } else if (loop && totalFrames.value > 0) {
          nextIndex = 0
        } else {
          pause()
          return
        }
      } else {
        if (hasPrev.value) {
          nextIndex = currentIndex.value - 1
        } else if (loop && totalFrames.value > 0) {
          nextIndex = totalFrames.value - 1
        } else {
          pause()
          return
        }
      }

      if (nextIndex == null) {
        return
      }

      if (!beforeAdvance) {
        currentIndex.value = nextIndex
        return
      }

      if (pendingAdvance) return
      pendingAdvance = true

      Promise.resolve(beforeAdvance(nextIndex, currentIndex.value))
        .then((ready) => {
          if (!isPlaying.value || token !== advanceToken) return
          if (ready !== false) {
            currentIndex.value = nextIndex
          }
        })
        .catch(() => {
        })
        .finally(() => {
          pendingAdvance = false
        })
    }, speed.value)
  }

  function pause() {
    isPlaying.value = false
    if (playTimer) {
      clearInterval(playTimer)
      playTimer = null
    }
    advanceToken += 1
    pendingAdvance = false
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

  function toggleDirection() {
    playDirection.value = playDirection.value === 'forward' ? 'backward' : 'forward'
  }

  function handleKeydown(e: KeyboardEvent) {
    switch (e.key) {
      case ' ':
        e.preventDefault()
        togglePlay()
        break
      case 'ArrowRight':
        e.preventDefault()
        next()
        break
      case 'ArrowLeft':
        e.preventDefault()
        prev()
        break
      case 'Home':
        e.preventDefault()
        goToFirst()
        break
      case 'End':
        e.preventDefault()
        goToLast()
        break
    }
  }

  watch(speed, () => {
    if (isPlaying.value) {
      pause()
      play()
    }
  })

  onUnmounted(() => {
    pause()
  })

  if (autoPlay && totalFrames.value > 0) {
    play()
  }

  return {
    isPlaying,
    currentIndex,
    speed,
    playDirection,
    totalFrames,
    progress,
    hasNext,
    hasPrev,
    currentCommit,
    previousCommit,
    play,
    pause,
    togglePlay,
    next,
    prev,
    goTo,
    goToFirst,
    goToLast,
    setSpeed,
    toggleDirection,
    handleKeydown
  }
}

export function parseDiff(diffText: string): DiffLine[] {
  if (!diffText) return []

  const lines = diffText.split('\n')
  const result: DiffLine[] = []
  let oldLine = 0
  let newLine = 0

  for (const line of lines) {
    if (line.startsWith('diff ') || line.startsWith('index ') ||
      line.startsWith('---') || line.startsWith('+++')) {
      continue
    }

    const hunkMatch = line.match(/^@@ -(\d+),?\d* \+(\d+),?\d* @@/)
    if (hunkMatch) {
      oldLine = parseInt(hunkMatch[1] ?? '0') - 1
      newLine = parseInt(hunkMatch[2] ?? '0') - 1
      result.push({
        lineNumber: result.length,
        type: 'context',
        content: line
      })
      continue
    }

    if (line.startsWith('+')) {
      newLine++
      result.push({
        lineNumber: result.length,
        newLineNumber: newLine,
        type: 'add',
        content: line.slice(1)
      })
    } else if (line.startsWith('-')) {
      oldLine++
      result.push({
        lineNumber: result.length,
        oldLineNumber: oldLine,
        type: 'delete',
        content: line.slice(1)
      })
    } else {
      oldLine++
      newLine++
      result.push({
        lineNumber: result.length,
        oldLineNumber: oldLine,
        newLineNumber: newLine,
        type: 'normal',
        content: line.startsWith(' ') ? line.slice(1) : line
      })
    }
  }

  return result
}

export function detectLanguage(filePath: string): string {
  const ext = filePath.split('.').pop()?.toLowerCase() || ''

  const langMap: Record<string, string> = {
    'js': 'javascript',
    'jsx': 'jsx',
    'ts': 'typescript',
    'tsx': 'tsx',
    'vue': 'vue',
    'py': 'python',
    'java': 'java',
    'kt': 'kotlin',
    'go': 'go',
    'rs': 'rust',
    'c': 'c',
    'cpp': 'cpp',
    'cc': 'cpp',
    'h': 'c',
    'hpp': 'cpp',
    'cs': 'csharp',
    'rb': 'ruby',
    'php': 'php',
    'swift': 'swift',
    'scala': 'scala',
    'sql': 'sql',
    'sh': 'bash',
    'bash': 'bash',
    'zsh': 'bash',
    'yaml': 'yaml',
    'yml': 'yaml',
    'json': 'json',
    'xml': 'xml',
    'html': 'html',
    'css': 'css',
    'scss': 'scss',
    'less': 'less',
    'md': 'markdown',
    'dockerfile': 'dockerfile'
  }

  return langMap[ext] || 'plaintext'
}

export interface LineChange {
  lineNumber: number
  type: 'added' | 'deleted' | 'modified'
}

export function computeChangedLines(
  oldContent: string,
  newContent: string
): { added: Set<number>; deleted: number[]; firstChangedLine: number | null } {
  const added = new Set<number>()
  const deleted: number[] = []
  let firstChangedLine: number | null = null

  if (oldContent === newContent) {
    return { added, deleted, firstChangedLine }
  }

  const oldLines = oldContent.split('\n')
  const newLines = newContent.split('\n')

  const changes = diffArrays(oldLines, newLines)

  let oldLineNum = 1
  let newLineNum = 1

  for (const change of changes) {
    const count = change.value.length

    if (change.removed) {
      for (let i = 0; i < count; i++) {
        deleted.push(oldLineNum + i)
        if (firstChangedLine === null) {
          firstChangedLine = oldLineNum + i
        }
      }
      oldLineNum += count
    } else if (change.added) {
      for (let i = 0; i < count; i++) {
        const lineNum = newLineNum + i
        added.add(lineNum)
        if (firstChangedLine === null || lineNum < firstChangedLine) {
          firstChangedLine = lineNum
        }
      }
      newLineNum += count
    } else {
      oldLineNum += count
      newLineNum += count
    }
  }

  return { added, deleted, firstChangedLine }
}
