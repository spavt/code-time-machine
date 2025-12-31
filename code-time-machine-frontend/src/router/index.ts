
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/HomePage.vue'),
    meta: {
      title: 'AI代码时光机',
      keepAlive: true
    }
  },
  {
    path: '/analyze',
    name: 'Analyze',
    component: () => import('@/views/AnalyzePage.vue'),
    meta: {
      title: '分析仓库'
    }
  },
  {
    path: '/repo/:id',
    name: 'Repository',
    component: () => import('@/views/RepositoryPage.vue'),
    meta: {
      title: '仓库详情'
    },
    children: [
      {
        path: '',
        name: 'RepoOverview',
        component: () => import('@/views/repo/OverviewTab.vue'),
        meta: {
          title: '概览'
        }
      },
      {
        path: 'timeline',
        name: 'RepoTimeline',
        component: () => import('@/views/repo/TimelineTab.vue'),
        meta: {
          title: '时间线'
        }
      },
      {
        path: 'commits',
        name: 'RepoCommits',
        component: () => import('@/views/repo/CommitsTab.vue'),
        meta: {
          title: '提交历史'
        }
      },
      {
        path: 'files',
        name: 'RepoFiles',
        component: () => import('@/views/repo/FilesTab.vue'),
        meta: {
          title: '文件浏览'
        }
      },
      {
        path: 'stats',
        name: 'RepoStats',
        component: () => import('@/views/repo/StatsTab.vue'),
        meta: {
          title: '统计分析'
        }
      }
    ]
  },
  {
    path: '/player/:repoId/:filePath+',
    name: 'Player',
    component: () => import('@/views/PlayerPage.vue'),
    meta: {
      title: '代码播放器'
    }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundPage.vue'),
    meta: {
      title: '页面不存在'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  }
})

router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || 'AI代码时光机'} - Code Time Machine`
  next()
})

export default router
