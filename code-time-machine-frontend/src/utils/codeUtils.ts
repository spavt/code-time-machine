/**
 * 代码处理工具函数
 * 从 PlayerPage.vue 抽离的可复用工具
 */

/**
 * 从代码字符串中提取指定方法的内容
 * 支持 Java, JavaScript, TypeScript, Python, Go 等语言
 * 
 * @param code - 完整的代码字符串
 * @param methodName - 要提取的方法名
 * @returns 提取的方法代码，如果未找到则返回 null
 */
export function extractMethodFromCode(code: string, methodName: string): string | null {
  if (!code || !methodName) return null

  const lines = code.split('\n')
  let startLine = -1
  let braceCount = 0
  let foundStart = false
  let endLine = -1

  // 查找方法开始位置
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] as string
    if (!line) continue

    // 匹配方法定义（支持 Java、JS、TS、Python、Go）
    if (!foundStart && (
      line.includes(`${methodName}(`) ||
      line.includes(`${methodName} (`) ||
      line.match(new RegExp(`\\b${methodName}\\s*\\(`))
    )) {
      const trimmed = line.trim()
      // 检查是否是方法定义（不是调用）
      const isDefinition = 
        // 传统 function 关键字
        trimmed.startsWith('function ') ||
        trimmed.startsWith('async function ') ||
        // 访问修饰符
        trimmed.startsWith('public ') ||
        trimmed.startsWith('private ') ||
        trimmed.startsWith('protected ') ||
        trimmed.startsWith('static ') ||
        // 箭头函数赋值
        line.includes('=>') ||
        // TypeScript/JavaScript 类方法 (无修饰符): methodName(params) { 或 async methodName(
        trimmed.match(new RegExp(`^(async\\s+)?${methodName}\\s*[(<]`)) ||
        // Java/C#: returnType methodName(
        line.match(/^\s*(public|private|protected|static|final|\w+)\s+\w+\s+\w+\s*\(/) ||
        // Python: def methodName(
        trimmed.startsWith('def ') ||
        trimmed.startsWith('async def ') ||
        // Go: func methodName(
        trimmed.startsWith('func ') ||
        // 变量赋值: const/let/var name = function/arrow
        line.match(/^\s*(const|let|var)\s+\w+\s*=/)

      if (isDefinition) {
        startLine = i
        foundStart = true
      }
    }

    if (foundStart) {
      // 计算大括号
      for (const char of line) {
        if (char === '{') braceCount++
        if (char === '}') braceCount--
      }

      // 找到方法结束
      if (braceCount === 0 && line.includes('}')) {
        endLine = i
        break
      }
    }
  }

  if (startLine >= 0 && endLine >= startLine) {
    return lines.slice(startLine, endLine + 1).join('\n')
  }

  return null
}

/**
 * 统计 diff 文本中的新增和删除行数
 * 
 * @param diffText - git diff 格式的文本
 * @returns 新增行数和删除行数
 */
export function countDiffStats(diffText: string): { additions: number; deletions: number } {
  let additions = 0
  let deletions = 0
  
  for (const line of diffText.split('\n')) {
    if (!line) continue
    // 跳过 diff 头信息
    if (line.startsWith('+++') || line.startsWith('---') || line.startsWith('@@')) continue
    if (line.startsWith('+')) {
      additions++
    } else if (line.startsWith('-')) {
      deletions++
    }
  }
  
  return { additions, deletions }
}

/**
 * 检测文件语言类型
 * 
 * @param filePath - 文件路径
 * @returns 语言标识符
 */
export function detectLanguage(filePath: string): string {
  if (!filePath) return 'plaintext'
  
  const ext = filePath.includes('.')
    ? filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase()
    : ''

  const languageMap: Record<string, string> = {
    'js': 'javascript',
    'ts': 'typescript',
    'jsx': 'jsx',
    'tsx': 'tsx',
    'vue': 'vue',
    'py': 'python',
    'java': 'java',
    'go': 'go',
    'rs': 'rust',
    'c': 'c',
    'h': 'c',
    'cpp': 'cpp',
    'cc': 'cpp',
    'hpp': 'cpp',
    'cs': 'csharp',
    'rb': 'ruby',
    'php': 'php',
    'sql': 'sql',
    'sh': 'bash',
    'bash': 'bash',
    'yaml': 'yaml',
    'yml': 'yaml',
    'json': 'json',
    'xml': 'xml',
    'html': 'html',
    'css': 'css',
    'scss': 'scss',
    'less': 'scss',
    'md': 'markdown'
  }

  return languageMap[ext] || 'plaintext'
}
