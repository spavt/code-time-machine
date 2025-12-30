// =====================================================
// AI代码时光机 - 播放器逻辑 Composable
// =====================================================

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

  // 播放状态
  const isPlaying = ref(false)
  const currentIndex = ref(0)
  const speed = ref(defaultSpeed) // 毫秒
  const playDirection = ref<'forward' | 'backward'>('forward')

  let playTimer: ReturnType<typeof setInterval> | null = null
  let pendingAdvance = false
  let advanceToken = 0

  // 计算属性
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

  // 播放控制
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
          // ignore guard errors to avoid stopping playback
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

  // 键盘快捷键
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

  // 速度变化时重启播放
  watch(speed, () => {
    if (isPlaying.value) {
      pause()
      play()
    }
  })

  // 清理
  onUnmounted(() => {
    pause()
  })

  // 自动播放
  if (autoPlay && totalFrames.value > 0) {
    play()
  }

  return {
    // 状态
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

    // 方法
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

// Diff解析工具
export function parseDiff(diffText: string): DiffLine[] {
  if (!diffText) return []

  const lines = diffText.split('\n')
  const result: DiffLine[] = []
  let oldLine = 0
  let newLine = 0

  for (const line of lines) {
    // 跳过diff头信息
    if (line.startsWith('diff ') || line.startsWith('index ') ||
      line.startsWith('---') || line.startsWith('+++')) {
      continue
    }

    // 解析hunk头
    const hunkMatch = line.match(/^@@ -(\d+),?\d* \+(\d+),?\d* @@/)
    if (hunkMatch) {
      oldLine = parseInt(hunkMatch[1]) - 1
      newLine = parseInt(hunkMatch[2]) - 1
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

// 代码语言检测
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

// 计算变更行 - 使用 diff 库的 Myers 算法，时间复杂度 O(n*d)
export interface LineChange {
  lineNumber: number
  type: 'added' | 'deleted' | 'modified'
}

/**
 * 使用 Myers diff 算法计算变更行
 * 时间复杂度: O(n*d)，其中 n 是行数，d 是差异大小
 * 相比原来的 O(n²) 实现，对于大文件和小改动有显著提升
 */
export function computeChangedLines(
  oldContent: string,
  newContent: string
): { added: Set<number>; deleted: number[]; firstChangedLine: number | null } {
  const added = new Set<number>()
  const deleted: number[] = []
  let firstChangedLine: number | null = null

  // 快速路径：完全相同
  if (oldContent === newContent) {
    return { added, deleted, firstChangedLine }
  }

  const oldLines = oldContent.split('\n')
  const newLines = newContent.split('\n')

  // 使用 diff 库的 diffArrays（基于 Myers 算法）
  const changes = diffArrays(oldLines, newLines)

  let oldLineNum = 1
  let newLineNum = 1

  for (const change of changes) {
    const count = change.value.length

    if (change.removed) {
      // 删除的行
      for (let i = 0; i < count; i++) {
        deleted.push(oldLineNum + i)
        if (firstChangedLine === null) {
          firstChangedLine = oldLineNum + i
        }
      }
      oldLineNum += count
    } else if (change.added) {
      // 新增的行
      for (let i = 0; i < count; i++) {
        const lineNum = newLineNum + i
        added.add(lineNum)
        if (firstChangedLine === null || lineNum < firstChangedLine) {
          firstChangedLine = lineNum
        }
      }
      newLineNum += count
    } else {
      // 未改变的行
      oldLineNum += count
      newLineNum += count
    }
  }

  return { added, deleted, firstChangedLine }
}
