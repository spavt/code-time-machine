// =====================================================
// AI代码时光机 - 播放器逻辑 Composable
// =====================================================

import { ref, computed, watch, onUnmounted } from 'vue'
import type { TimelineCommit, DiffLine } from '@/types'

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

// 计算变更行 - 使用简化的逐行对比算法
export interface LineChange {
  lineNumber: number
  type: 'added' | 'deleted' | 'modified'
}

export function computeChangedLines(
  oldContent: string,
  newContent: string
): { added: Set<number>; deleted: number[]; firstChangedLine: number | null } {
  const oldLines = oldContent.split('\n')
  const newLines = newContent.split('\n')
  
  const added = new Set<number>()
  const deleted: number[] = []
  let firstChangedLine: number | null = null
  
  // 使用简单的 LCS 思路进行对比
  // 创建旧行内容到行号的映射
  const oldLineMap = new Map<string, number[]>()
  oldLines.forEach((line, idx) => {
    const existing = oldLineMap.get(line) || []
    existing.push(idx)
    oldLineMap.set(line, existing)
  })
  
  // 追踪已匹配的旧行
  const matchedOldLines = new Set<number>()
  
  // 第一遍：找出完全匹配的行
  const newLineMatches: (number | null)[] = new Array(newLines.length).fill(null)
  
  newLines.forEach((line, newIdx) => {
    const candidates = oldLineMap.get(line) || []
    for (const oldIdx of candidates) {
      if (!matchedOldLines.has(oldIdx)) {
        newLineMatches[newIdx] = oldIdx
        matchedOldLines.add(oldIdx)
        break
      }
    }
  })
  
  // 标记新增行（在新内容中存在但未匹配到旧内容的行）
  newLines.forEach((_, newIdx) => {
    if (newLineMatches[newIdx] === null) {
      const lineNum = newIdx + 1
      added.add(lineNum)
      if (firstChangedLine === null || lineNum < firstChangedLine) {
        firstChangedLine = lineNum
      }
    }
  })
  
  // 标记删除行（在旧内容中存在但未被匹配的行）
  oldLines.forEach((_, oldIdx) => {
    if (!matchedOldLines.has(oldIdx)) {
      deleted.push(oldIdx + 1)
      const approxNewLine = oldIdx + 1
      if (firstChangedLine === null || approxNewLine < firstChangedLine) {
        firstChangedLine = approxNewLine
      }
    }
  })
  
  return { added, deleted, firstChangedLine }
}
