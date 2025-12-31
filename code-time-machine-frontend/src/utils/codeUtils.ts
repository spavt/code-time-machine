
export function extractMethodFromCode(code: string, methodName: string): string | null {
  if (!code || !methodName) return null

  const lines = code.split('\n')
  let startLine = -1
  let braceCount = 0
  let foundStart = false
  let endLine = -1

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i] as string
    if (!line) continue

    if (!foundStart && (
      line.includes(`${methodName}(`) ||
      line.includes(`${methodName} (`) ||
      line.match(new RegExp(`\\b${methodName}\\s*\\(`))
    )) {
      const trimmed = line.trim()
      const isDefinition = 
        trimmed.startsWith('function ') ||
        trimmed.startsWith('async function ') ||
        trimmed.startsWith('public ') ||
        trimmed.startsWith('private ') ||
        trimmed.startsWith('protected ') ||
        trimmed.startsWith('static ') ||
        line.includes('=>') ||
        trimmed.match(new RegExp(`^(async\\s+)?${methodName}\\s*[(<]`)) ||
        line.match(/^\s*(public|private|protected|static|final|\w+)\s+\w+\s+\w+\s*\(/) ||
        trimmed.startsWith('def ') ||
        trimmed.startsWith('async def ') ||
        trimmed.startsWith('func ') ||
        line.match(/^\s*(const|let|var)\s+\w+\s*=/)

      if (isDefinition) {
        startLine = i
        foundStart = true
      }
    }

    if (foundStart) {
      for (const char of line) {
        if (char === '{') braceCount++
        if (char === '}') braceCount--
      }

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

export function countDiffStats(diffText: string): { additions: number; deletions: number } {
  let additions = 0
  let deletions = 0
  
  for (const line of diffText.split('\n')) {
    if (!line) continue
    if (line.startsWith('+++') || line.startsWith('---') || line.startsWith('@@')) continue
    if (line.startsWith('+')) {
      additions++
    } else if (line.startsWith('-')) {
      deletions++
    }
  }
  
  return { additions, deletions }
}

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
