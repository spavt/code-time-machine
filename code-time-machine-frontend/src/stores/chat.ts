
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage } from '@/types'
import { chatApi } from '@/api'

export const useChatStore = defineStore('chat', () => {
  const sessionId = ref<string>(generateSessionId())
  const messages = ref<ChatMessage[]>([])
  const suggestions = ref<string[]>([])

  const contextRepoId = ref<number | null>(null)
  const contextCommitId = ref<number | null>(null)
  const contextFilePath = ref<string | null>(null)

  const loading = ref(false)
  const error = ref<string | null>(null)
  const isExpanded = ref(false)

  const hasMessages = computed(() => messages.value.length > 0)
  const lastMessage = computed(() => {
    if (messages.value.length === 0) return null
    return messages.value[messages.value.length - 1]
  })
  const isLastMessageFromUser = computed(() => {
    return lastMessage.value?.role === 'user'
  })


  function generateSessionId(): string {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9)
  }

  function setContext(repoId?: number, commitId?: number, filePath?: string) {
    if (repoId !== undefined) contextRepoId.value = repoId
    if (commitId !== undefined) contextCommitId.value = commitId
    if (filePath !== undefined) contextFilePath.value = filePath
  }

  function clearContext() {
    contextRepoId.value = null
    contextCommitId.value = null
    contextFilePath.value = null
  }

  async function sendMessage(question: string, context?: string) {
    if (!question.trim()) return

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

      messages.value = messages.value.filter(m => !m.isLoading)

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

  async function sendMessageStream(question: string, context?: string) {
    if (!question.trim()) return

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

  async function fetchSuggestions(commitId: number) {
    try {
      const result = await chatApi.getSuggestions(commitId)
      suggestions.value = result
      return result
    } catch (err) {
      console.error('Fetch suggestions error:', err)
      suggestions.value = [
        '这次改动的主要目的是什么？',
        '这段代码为什么要这样写？',
        '这个改动可能有什么潜在问题？',
        '能解释一下这里的设计思路吗？'
      ]
      return suggestions.value
    }
  }

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

  function newSession() {
    sessionId.value = generateSessionId()
    messages.value = []
    suggestions.value = []
    error.value = null
  }

  function toggleExpanded() {
    isExpanded.value = !isExpanded.value
  }

  function askQuickQuestion(question: string) {
    return sendMessage(question)
  }

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
    sessionId,
    messages,
    suggestions,
    contextRepoId,
    contextCommitId,
    contextFilePath,
    loading,
    error,
    isExpanded,

    hasMessages,
    lastMessage,
    isLastMessageFromUser,

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
