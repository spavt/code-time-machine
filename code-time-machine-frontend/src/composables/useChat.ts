// =====================================================
// AI代码时光机 - AI对话 Composable
// =====================================================

import { ref, computed } from 'vue'
import { chatApi } from '@/api'
import type { ChatMessage } from '@/types'

export function useChat(sessionId: string) {
  const messages = ref<ChatMessage[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // 当前上下文
  const context = ref<{
    repoId?: number
    commitId?: number
    filePath?: string
    codeSnippet?: string
  }>({})

  // 计算属性
  const hasMessages = computed(() => messages.value.length > 0)
  const lastMessage = computed(() =>
    messages.value.length > 0 ? messages.value[messages.value.length - 1] : null
  )

  // 发送消息
  async function sendMessage(question: string) {
    if (!question.trim() || isLoading.value) return

    // 添加用户消息
    const userMessage: ChatMessage = {
      id: `user-${Date.now()}`,
      sessionId,
      repoId: context.value.repoId,
      commitId: context.value.commitId,
      filePath: context.value.filePath,
      role: 'user',
      content: question,
      createdAt: new Date().toISOString()
    }
    messages.value.push(userMessage)

    // 添加AI加载状态消息
    const loadingMessage: ChatMessage = {
      id: `assistant-${Date.now()}`,
      sessionId,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
      isLoading: true
    }
    messages.value.push(loadingMessage)

    isLoading.value = true
    error.value = null

    try {
      const response = await chatApi.ask({
        sessionId,
        repoId: context.value.repoId,
        commitId: context.value.commitId,
        filePath: context.value.filePath,
        question,
        context: context.value.codeSnippet
      })

      // 更新AI消息
      const aiIndex = messages.value.findIndex(m => m.id === loadingMessage.id)
      if (aiIndex >= 0) {
        messages.value[aiIndex] = {
          ...loadingMessage,
          content: response.answer,
          tokensUsed: response.tokensUsed,
          isLoading: false
        }
      }
    } catch (err) {
      error.value = (err as Error).message
      // 移除加载消息
      messages.value = messages.value.filter(m => m.id !== loadingMessage.id)

      // 添加错误消息
      messages.value.push({
        id: `error-${Date.now()}`,
        sessionId,
        role: 'assistant',
        content: `抱歉，请求失败了：${error.value}`,
        createdAt: new Date().toISOString()
      })
    } finally {
      isLoading.value = false
    }
  }

  // 加载历史消息
  async function loadHistory() {
    try {
      const history = await chatApi.getHistory(sessionId)
      messages.value = history
    } catch (err) {
      console.error('Load history failed:', err)
    }
  }

  // 清除对话
  async function clearMessages() {
    try {
      await chatApi.clearHistory(sessionId)
      messages.value = []
    } catch (err) {
      error.value = (err as Error).message
    }
  }

  // 设置上下文
  function setContext(ctx: typeof context.value) {
    context.value = { ...ctx }
  }

  // 添加系统消息
  function addSystemMessage(content: string) {
    messages.value.push({
      id: `system-${Date.now()}`,
      sessionId,
      role: 'system',
      content,
      createdAt: new Date().toISOString()
    })
  }

  // 获取推荐问题
  async function getSuggestions(commitId: number): Promise<string[]> {
    try {
      return await chatApi.getSuggestions(commitId)
    } catch {
      return getDefaultSuggestions()
    }
  }

  // 默认推荐问题
  function getDefaultSuggestions(): string[] {
    return [
      '这段代码的主要功能是什么？',
      '为什么这样设计？有什么好处？',
      '这个改动可能带来什么影响？',
      '有没有更好的实现方式？',
      '这里用到了什么设计模式？'
    ]
  }

  // 发送消息（流式输出）
  async function sendMessageStream(question: string) {
    if (!question.trim() || isLoading.value) return

    // 添加用户消息
    const userMessage: ChatMessage = {
      id: `user-${Date.now()}`,
      sessionId,
      repoId: context.value.repoId,
      commitId: context.value.commitId,
      filePath: context.value.filePath,
      role: 'user',
      content: question,
      createdAt: new Date().toISOString()
    }
    messages.value.push(userMessage)

    // 添加AI流式消息
    const streamMessageId = `assistant-stream-${Date.now()}`
    const streamMessage: ChatMessage = {
      id: streamMessageId,
      sessionId,
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
      isLoading: true
    }
    messages.value.push(streamMessage)

    isLoading.value = true
    error.value = null

    try {
      await chatApi.askStream(
        {
          sessionId,
          repoId: context.value.repoId,
          commitId: context.value.commitId,
          filePath: context.value.filePath,
          question,
          context: context.value.codeSnippet
        },
        // onChunk
        (chunk: string) => {
          const msgIndex = messages.value.findIndex(m => m.id === streamMessageId)
          if (msgIndex >= 0) {
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
        // onComplete
        () => {
          const msgIndex = messages.value.findIndex(m => m.id === streamMessageId)
          if (msgIndex >= 0) {
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
          isLoading.value = false
        },
        // onError
        (err: Error) => {
          error.value = err.message
          const msgIndex = messages.value.findIndex(m => m.id === streamMessageId)
          if (msgIndex >= 0) {
            const currentMsg = messages.value[msgIndex]
            if (currentMsg) {
              messages.value[msgIndex] = {
                id: currentMsg.id,
                sessionId: currentMsg.sessionId,
                repoId: currentMsg.repoId,
                commitId: currentMsg.commitId,
                filePath: currentMsg.filePath,
                role: currentMsg.role,
                content: currentMsg.content || `抱歉，请求失败了：${err.message}`,
                createdAt: currentMsg.createdAt,
                isLoading: false
              }
            }
          }
          isLoading.value = false
        }
      )
    } catch (err) {
      error.value = (err as Error).message
      isLoading.value = false
    }
  }

  return {
    // 状态
    messages,
    isLoading,
    error,
    context,
    hasMessages,
    lastMessage,

    // 方法
    sendMessage,
    sendMessageStream,
    loadHistory,
    clearMessages,
    setContext,
    addSystemMessage,
    getSuggestions,
    getDefaultSuggestions
  }
}

// 生成随机会话ID（仅用于临时场景）
export function generateSessionId(): string {
  return `session-${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
}

// 生成确定性会话ID（基于 repoId + filePath，确保同一文件使用相同的会话）
export function generateDeterministicSessionId(repoId: number, filePath: string): string {
  // 简单哈希函数
  const str = `${repoId}-${filePath}`
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash // Convert to 32bit integer
  }
  // 转换为正数并返回
  const positiveHash = Math.abs(hash).toString(36)
  return `chat-${repoId}-${positiveHash}`
}
