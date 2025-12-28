// =====================================================
// AI代码时光机 - 聊天状态管理
// =====================================================

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage } from '@/types'
import { chatApi } from '@/api'

export const useChatStore = defineStore('chat', () => {
  // 状态
  const sessionId = ref<string>(generateSessionId())
  const messages = ref<ChatMessage[]>([])
  const suggestions = ref<string[]>([])

  // 当前上下文
  const contextRepoId = ref<number | null>(null)
  const contextCommitId = ref<number | null>(null)
  const contextFilePath = ref<string | null>(null)

  // UI状态
  const loading = ref(false)
  const error = ref<string | null>(null)
  const isExpanded = ref(false)

  // 计算属性
  const hasMessages = computed(() => messages.value.length > 0)
  const lastMessage = computed(() => {
    if (messages.value.length === 0) return null
    return messages.value[messages.value.length - 1]
  })
  const isLastMessageFromUser = computed(() => {
    return lastMessage.value?.role === 'user'
  })

  // Actions

  // 生成会话ID
  function generateSessionId(): string {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
  }

  // 设置上下文
  function setContext(repoId?: number, commitId?: number, filePath?: string) {
    if (repoId !== undefined) contextRepoId.value = repoId
    if (commitId !== undefined) contextCommitId.value = commitId
    if (filePath !== undefined) contextFilePath.value = filePath
  }

  // 清除上下文
  function clearContext() {
    contextRepoId.value = null
    contextCommitId.value = null
    contextFilePath.value = null
  }

  // 发送消息
  async function sendMessage(question: string, context?: string) {
    if (!question.trim()) return

    // 添加用户消息
    const userMessage: ChatMessage = {
      id: 'msg_' + Date.now(),
      sessionId: sessionId.value,
      repoId: contextRepoId.value || undefined,
      commitId: contextCommitId.value || undefined,
      filePath: contextFilePath.value || undefined,
      role: 'user',
      content: question,
      createdAt: new Date().toISOString()
    }
    messages.value.push(userMessage)

    // 添加加载状态的AI消息
    const loadingMessage: ChatMessage = {
      id: 'msg_loading_' + Date.now(),
      sessionId: sessionId.value,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
      isLoading: true
    }
    messages.value.push(loadingMessage)

    loading.value = true
    error.value = null

    try {
      const response = await chatApi.ask({
        sessionId: sessionId.value,
        repoId: contextRepoId.value || undefined,
        commitId: contextCommitId.value || undefined,
        filePath: contextFilePath.value || undefined,
        question,
        context
      })

      // 更新AI回答
      const loadingIndex = messages.value.findIndex(m => m.isLoading)
      if (loadingIndex !== -1) {
        messages.value[loadingIndex] = {
          id: 'msg_' + Date.now() + '_ai',
          sessionId: sessionId.value,
          repoId: contextRepoId.value || undefined,
          commitId: contextCommitId.value || undefined,
          filePath: contextFilePath.value || undefined,
          role: 'assistant',
          content: response.answer,
          tokensUsed: response.tokensUsed,
          createdAt: new Date().toISOString(),
          isLoading: false
        }
      }

      return response
    } catch (err) {
      error.value = (err as Error).message

      // 移除加载消息
      messages.value = messages.value.filter(m => !m.isLoading)

      // 添加错误消息
      messages.value.push({
        id: 'msg_error_' + Date.now(),
        sessionId: sessionId.value,
        role: 'assistant',
        content: '抱歉，我遇到了一些问题。请稍后再试。',
        createdAt: new Date().toISOString()
      })

      throw err
    } finally {
      loading.value = false
    }
  }

  // 发送消息（流式输出）
  async function sendMessageStream(question: string, context?: string) {
    if (!question.trim()) return

    // 添加用户消息
    const userMessage: ChatMessage = {
      id: 'msg_' + Date.now(),
      sessionId: sessionId.value,
      repoId: contextRepoId.value || undefined,
      commitId: contextCommitId.value || undefined,
      filePath: contextFilePath.value || undefined,
      role: 'user',
      content: question,
      createdAt: new Date().toISOString()
    }
    messages.value.push(userMessage)

    // 添加流式AI消息（初始为空）
    const streamMessageId = 'msg_stream_' + Date.now()
    const streamMessage: ChatMessage = {
      id: streamMessageId,
      sessionId: sessionId.value,
      repoId: contextRepoId.value || undefined,
      commitId: contextCommitId.value || undefined,
      filePath: contextFilePath.value || undefined,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
      isLoading: true
    }
    messages.value.push(streamMessage)

    loading.value = true
    error.value = null

    try {
      await chatApi.askStream(
        {
          sessionId: sessionId.value,
          repoId: contextRepoId.value || undefined,
          commitId: contextCommitId.value || undefined,
          filePath: contextFilePath.value || undefined,
          question,
          context
        },
        // onChunk: 每次收到内容时更新消息
        (chunk: string) => {
          const msgIndex = messages.value.findIndex(m => m.id === streamMessageId)
          if (msgIndex !== -1) {
            const currentMsg = messages.value[msgIndex]
            if (currentMsg) {
              messages.value[msgIndex] = {
                id: currentMsg.id,
                sessionId: currentMsg.sessionId,
                repoId: currentMsg.repoId,
                commitId: currentMsg.commitId,
                filePath: currentMsg.filePath,
                role: currentMsg.role,
                content: currentMsg.content + chunk,
                createdAt: currentMsg.createdAt,
                isLoading: true
              }
            }
          }
        },
        // onComplete: 流式输出完成
        () => {
          const msgIndex = messages.value.findIndex(m => m.id === streamMessageId)
          if (msgIndex !== -1) {
            const currentMsg = messages.value[msgIndex]
            if (currentMsg) {
              messages.value[msgIndex] = {
                id: currentMsg.id,
                sessionId: currentMsg.sessionId,
                repoId: currentMsg.repoId,
                commitId: currentMsg.commitId,
                filePath: currentMsg.filePath,
                role: currentMsg.role,
                content: currentMsg.content,
                createdAt: currentMsg.createdAt,
                isLoading: false
              }
            }
          }
          loading.value = false
        },
        // onError: 出错时处理
        (err: Error) => {
          error.value = err.message
          const msgIndex = messages.value.findIndex(m => m.id === streamMessageId)
          if (msgIndex !== -1) {
            const currentMsg = messages.value[msgIndex]
            if (currentMsg) {
              messages.value[msgIndex] = {
                id: currentMsg.id,
                sessionId: currentMsg.sessionId,
                repoId: currentMsg.repoId,
                commitId: currentMsg.commitId,
                filePath: currentMsg.filePath,
                role: currentMsg.role,
                content: currentMsg.content || '抱歉，我遇到了一些问题。请稍后再试。',
                createdAt: currentMsg.createdAt,
                isLoading: false
              }
            }
          }
          loading.value = false
        }
      )
    } catch (err) {
      error.value = (err as Error).message
      loading.value = false
    }
  }

  // 获取推荐问题
  async function fetchSuggestions(commitId: number) {
    try {
      const result = await chatApi.getSuggestions(commitId)
      suggestions.value = result
      return result
    } catch (err) {
      console.error('Fetch suggestions error:', err)
      // 提供默认推荐问题
      suggestions.value = [
        '这次改动的主要目的是什么？',
        '这段代码为什么要这样写？',
        '这个改动可能有什么潜在问题？',
        '能解释一下这里的设计思路吗？'
      ]
      return suggestions.value
    }
  }

  // 加载历史消息
  async function loadHistory() {
    try {
      const history = await chatApi.getHistory(sessionId.value)
      messages.value = history
      return history
    } catch (err) {
      console.error('Load history error:', err)
      return []
    }
  }

  // 清除消息
  async function clearMessages() {
    try {
      await chatApi.clearHistory(sessionId.value)
    } catch (err) {
      console.error('Clear history error:', err)
    } finally {
      messages.value = []
      suggestions.value = []
    }
  }

  // 开始新会话
  function newSession() {
    sessionId.value = generateSessionId()
    messages.value = []
    suggestions.value = []
    error.value = null
  }

  // 切换展开状态
  function toggleExpanded() {
    isExpanded.value = !isExpanded.value
  }

  // 添加快捷问题
  function askQuickQuestion(question: string) {
    return sendMessage(question)
  }

  // 根据当前上下文构建系统消息
  function buildContextPrompt(): string {
    const parts: string[] = []

    if (contextRepoId.value) {
      parts.push(`当前仓库ID: ${contextRepoId.value}`)
    }
    if (contextCommitId.value) {
      parts.push(`当前提交ID: ${contextCommitId.value}`)
    }
    if (contextFilePath.value) {
      parts.push(`当前文件: ${contextFilePath.value}`)
    }

    return parts.join('\n')
  }

  return {
    // 状态
    sessionId,
    messages,
    suggestions,
    contextRepoId,
    contextCommitId,
    contextFilePath,
    loading,
    error,
    isExpanded,

    // 计算属性
    hasMessages,
    lastMessage,
    isLastMessageFromUser,

    // Actions
    setContext,
    clearContext,
    sendMessage,
    sendMessageStream,
    fetchSuggestions,
    loadHistory,
    clearMessages,
    newSession,
    toggleExpanded,
    askQuickQuestion,
    buildContextPrompt
  }
})
