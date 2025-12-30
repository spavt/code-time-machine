// =====================================================
// AI代码时光机 - TypeScript类型定义
// =====================================================

// 仓库信息
export interface Repository {
  id: number
  name: string
  url: string
  localPath?: string
  description?: string
  defaultBranch: string
  language?: string
  stars: number
  totalCommits: number
  totalFiles: number
  repoSize: number
  status: RepoStatus
  analyzeProgress: number
  analyzeDepth?: number         // 分析深度
  analyzeSince?: string         // 分析起始时间
  analyzePathFilters?: string   // 路径过滤（JSON）
  canLoadMore?: boolean         // 是否可以加载更多历史
  lastAnalyzedAt?: string
  createdAt: string
  updatedAt: string
}

export type RepoStatus = 0 | 1 | 2 | 3 // 待分析 | 分析中 | 完成 | 失败

export const RepoStatusMap: Record<RepoStatus, { label: string; color: string }> = {
  0: { label: '待分析', color: 'info' },
  1: { label: '分析中', color: 'warning' },
  2: { label: '已完成', color: 'success' },
  3: { label: '失败', color: 'danger' }
}

// 分析选项
export interface AnalyzeOptions {
  depth?: number           // 分析深度: 20(极速), 100(快速), 500(推荐), 2000(深度), -1(全部)
  since?: string           // ISO日期字符串，只分析此时间之后的提交
  until?: string           // ISO日期字符串，只分析此时间之前的提交
  pathFilters?: string[]   // 路径过滤器
  shallow?: boolean        // 是否使用浅克隆
}

// 深度选项预设
export const DepthPresets = [
  { value: 20, label: '极速 (20次提交)', desc: '超快速预览', time: '~10秒' },
  { value: 100, label: '快速 (100次提交)', desc: '适合快速浏览', time: '~1分钟' },
  { value: 500, label: '推荐 (500次提交)', desc: '平衡速度和深度', time: '~3分钟' },
  { value: 2000, label: '深度 (2000次提交)', desc: '深入分析历史', time: '~10分钟' },
  { value: -1, label: '全部历史', desc: '完整项目历史', time: '取决于项目大小' },
  { value: 0, label: '自定义', desc: '输入任意次数', time: '取决于数量' }
]

// 提交记录
export interface CommitRecord {
  id: number
  repoId: number
  commitHash: string
  shortHash: string
  parentHash?: string
  authorName: string
  authorEmail?: string
  commitMessage: string
  commitTime: string
  additions: number
  deletions: number
  filesChanged: number
  isMerge: boolean
  commitOrder: number
  createdAt: string
  // 关联数据
  fileChanges?: FileChange[]
  aiAnalysis?: AiAnalysis
}

// 文件变更
export interface FileChange {
  id: number
  commitId: number
  repoId: number
  filePath: string
  fileName: string
  fileExtension?: string
  changeType: ChangeType
  oldPath?: string
  additions: number
  deletions: number
  diffText?: string
  fileContent?: string
  contentHash?: string
}

export type ChangeType = 'ADD' | 'MODIFY' | 'DELETE' | 'RENAME' | 'COPY'

export const ChangeTypeMap: Record<ChangeType, { label: string; color: string; icon: string }> = {
  'ADD': { label: '新增', color: '#67C23A', icon: 'Plus' },
  'MODIFY': { label: '修改', color: '#E6A23C', icon: 'Edit' },
  'DELETE': { label: '删除', color: '#F56C6C', icon: 'Delete' },
  'RENAME': { label: '重命名', color: '#909399', icon: 'Switch' },
  'COPY': { label: '复制', color: '#409EFF', icon: 'CopyDocument' }
}

// 文件快照
export interface FileSnapshot {
  id: number
  repoId: number
  commitId: number
  filePath: string
  content?: string
  lineCount: number
  charCount: number
  language?: string
}

// AI分析结果
export interface AiAnalysis {
  id: number
  commitId: number
  repoId: number
  analysisType: 'COMMIT' | 'FILE' | 'QUESTION'
  summary?: string
  purpose?: string
  impact?: string
  technicalDetails?: string
  suggestions?: string
  changeCategory?: ChangeCategory
  complexityScore?: number
  importanceScore?: number
  promptHash?: string
  modelUsed?: string
  tokensUsed?: number
  responseTime?: number
  createdAt: string
}

export type ChangeCategory = 'feature' | 'bugfix' | 'refactor' | 'docs' | 'style' | 'test' | 'chore' | 'perf'

export const ChangeCategoryMap: Record<ChangeCategory, { label: string; color: string; icon: string }> = {
  'feature': { label: '新功能', color: '#67C23A', icon: 'Star' },
  'bugfix': { label: '修复Bug', color: '#F56C6C', icon: 'WarnTriangleFilled' },
  'refactor': { label: '重构', color: '#E6A23C', icon: 'Refresh' },
  'docs': { label: '文档', color: '#909399', icon: 'Document' },
  'style': { label: '样式', color: '#A855F7', icon: 'Brush' },
  'test': { label: '测试', color: '#06B6D4', icon: 'Checked' },
  'chore': { label: '杂项', color: '#6B7280', icon: 'Setting' },
  'perf': { label: '性能', color: '#F59E0B', icon: 'TrendCharts' }
}

// 对话历史
export interface ChatMessage {
  id: string
  sessionId: string
  repoId?: number
  commitId?: number
  commitOrder?: number     // 提交序号（第几帧）
  shortHash?: string       // 提交短哈希
  filePath?: string
  role: 'user' | 'assistant' | 'system'
  content: string
  tokensUsed?: number
  createdAt: string
  isLoading?: boolean
}

// 统计数据
export interface Statistics {
  id: number
  repoId: number
  statDate?: string
  statType: string
  statKey: string
  statValue: number
  extraData?: Record<string, unknown>
}

// 文件演化时间线
export interface FileTimeline {
  repoId: number
  filePath: string
  fileName: string
  commits: TimelineCommit[]
}

export interface TimelineCommit {
  id: number
  commitHash: string
  shortHash: string
  commitMessage: string
  authorName: string
  commitTime: string
  commitOrder: number
  changeType: ChangeType
  additions: number
  deletions: number
  aiSummary?: string
  changeCategory?: ChangeCategory
  content?: string
  diffLines?: DiffLine[]
}

// Diff行信息
export interface DiffLine {
  lineNumber: number
  oldLineNumber?: number
  newLineNumber?: number
  type: 'normal' | 'add' | 'delete' | 'context'
  content: string
}

// 播放器状态
export interface PlayerState {
  isPlaying: boolean
  currentIndex: number
  speed: number
  totalFrames: number
}

// API响应格式
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

// 图表数据
export interface ChartData {
  labels: string[]
  datasets: {
    label: string
    data: number[]
    color?: string
  }[]
}

// 贡献者统计
export interface ContributorStats {
  authorName: string
  authorEmail?: string
  commitCount: number
  additions: number
  deletions: number
  firstCommit: string
  lastCommit: string
}

// 文件树节点
export interface FileTreeNode {
  id: string
  label: string
  path: string
  type: 'file' | 'folder'
  extension?: string
  children?: FileTreeNode[]
  changeType?: ChangeType
  commitCount?: number
}

// 搜索过滤器
export interface SearchFilter {
  keyword?: string
  authorName?: string
  startDate?: string
  endDate?: string
  changeTypes?: ChangeType[]
  changeCategories?: ChangeCategory[]
  filePath?: string
}
