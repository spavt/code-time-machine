/**
 * 推荐仓库配置
 * 根据用户技能级别推荐适合学习的开源项目
 */

export type SkillLevel = 'beginner' | 'intermediate' | 'advanced'

export interface RecommendedRepo {
    name: string
    url: string
    description: string
    level: SkillLevel
    tags: string[]
    stars?: string
    language?: string
}

// 技能级别配置
export const skillLevelConfig = {
    beginner: {
        icon: '🌱',
        title: '我是新手',
        subtitle: '刚开始学习编程',
        description: '推荐结构简单、注释清晰的入门级项目',
        color: '#10b981'
    },
    intermediate: {
        icon: '📚',
        title: '我有基础',
        subtitle: '有一定开发经验',
        description: '推荐设计模式清晰、架构合理的实战项目',
        color: '#3b82f6'
    },
    advanced: {
        icon: '🚀',
        title: '我是高手',
        subtitle: '追求深度理解',
        description: '推荐复杂框架源码、底层实现原理项目',
        color: '#8b5cf6'
    }
} as const

// 推荐仓库列表
export const recommendedRepos: RecommendedRepo[] = [
    // ========== 新手级别 ==========
    {
        name: 'javascript-algorithms',
        url: 'https://github.com/trekhleb/javascript-algorithms',
        description: '用 JavaScript 实现的算法和数据结构，配有详细解释和示例',
        level: 'beginner',
        tags: ['算法', 'JavaScript', '入门'],
        stars: '180k+',
        language: 'JavaScript'
    },
    {
        name: 'build-your-own-x',
        url: 'https://github.com/codecrafters-io/build-your-own-x',
        description: '从零构建各种技术的教程合集，边做边学',
        level: 'beginner',
        tags: ['教程', '实践', '入门'],
        stars: '250k+',
        language: '多语言'
    },
    {
        name: 'realworld',
        url: 'https://github.com/gothinkster/realworld',
        description: '使用各种前后端框架实现的真实应用，学习最佳实践',
        level: 'beginner',
        tags: ['全栈', '最佳实践', 'Demo'],
        stars: '77k+',
        language: '多语言'
    },
    {
        name: '30-seconds-of-code',
        url: 'https://github.com/Chalarangelo/30-seconds-of-code',
        description: '短小精悍的代码片段集合，每个都能快速理解',
        level: 'beginner',
        tags: ['代码片段', 'JavaScript', '速学'],
        stars: '118k+',
        language: 'JavaScript'
    },

    // ========== 有基础级别 ==========
    {
        name: 'axios',
        url: 'https://github.com/axios/axios',
        description: '最流行的 HTTP 客户端库，代码结构清晰',
        level: 'intermediate',
        tags: ['HTTP', '网络请求', '工具库'],
        stars: '103k+',
        language: 'JavaScript'
    },
    {
        name: 'dayjs',
        url: 'https://github.com/iamkun/dayjs',
        description: '轻量级日期处理库，代码仅 2KB，适合学习',
        level: 'intermediate',
        tags: ['日期', '轻量', '插件系统'],
        stars: '45k+',
        language: 'JavaScript'
    },
    {
        name: 'zustand',
        url: 'https://github.com/pmndrs/zustand',
        description: '极简状态管理库，核心代码不到 100 行',
        level: 'intermediate',
        tags: ['状态管理', 'React', '极简'],
        stars: '40k+',
        language: 'TypeScript'
    },
    {
        name: 'express',
        url: 'https://github.com/expressjs/express',
        description: 'Node.js 经典 Web 框架，中间件模式范例',
        level: 'intermediate',
        tags: ['Node.js', 'Web框架', '中间件'],
        stars: '62k+',
        language: 'JavaScript'
    },
    {
        name: 'lodash',
        url: 'https://github.com/lodash/lodash',
        description: '工具函数库经典，学习函数式编程思想',
        level: 'intermediate',
        tags: ['工具库', '函数式', '最佳实践'],
        stars: '58k+',
        language: 'JavaScript'
    },

    // ========== 高手级别 ==========
    {
        name: 'vue-core',
        url: 'https://github.com/vuejs/core',
        description: 'Vue 3 核心源码，响应式系统和编译器实现',
        level: 'advanced',
        tags: ['Vue', '框架源码', '响应式'],
        stars: '44k+',
        language: 'TypeScript'
    },
    {
        name: 'react',
        url: 'https://github.com/facebook/react',
        description: 'React 源码，Fiber 架构和调度器',
        level: 'advanced',
        tags: ['React', '框架源码', 'Fiber'],
        stars: '218k+',
        language: 'JavaScript'
    },
    {
        name: 'vite',
        url: 'https://github.com/vitejs/vite',
        description: '下一代前端构建工具，ESM 和 Rollup 集成',
        level: 'advanced',
        tags: ['构建工具', 'ESM', 'Rollup'],
        stars: '63k+',
        language: 'TypeScript'
    },
    {
        name: 'node',
        url: 'https://github.com/nodejs/node',
        description: 'Node.js 运行时源码，libuv 事件循环',
        level: 'advanced',
        tags: ['Node.js', '运行时', 'C++'],
        stars: '102k+',
        language: 'C++'
    },
    {
        name: 'typescript',
        url: 'https://github.com/microsoft/TypeScript',
        description: 'TypeScript 编译器源码，类型系统实现',
        level: 'advanced',
        tags: ['编译器', '类型系统', '语言设计'],
        stars: '96k+',
        language: 'TypeScript'
    }
]

// 根据级别获取推荐仓库
export function getReposByLevel(level: SkillLevel): RecommendedRepo[] {
    return recommendedRepos.filter(repo => repo.level === level)
}

// 获取级别显示配置
export function getLevelConfig(level: SkillLevel) {
    return skillLevelConfig[level]
}
