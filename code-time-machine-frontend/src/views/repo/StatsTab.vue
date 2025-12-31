<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { useRepositoryStore } from '@/stores/repository'
import { statsApi } from '@/api'

use([CanvasRenderer, BarChart, LineChart, PieChart, GridComponent, LegendComponent, TitleComponent, TooltipComponent])
import { HeatmapChart } from 'echarts/charts'
import { VisualMapComponent } from 'echarts/components'
use([HeatmapChart, VisualMapComponent])

const repoStore = useRepositoryStore()
const loading = ref(false)

const linesTrend = ref({ dates: [] as string[], additions: [] as number[], deletions: [] as number[], total: [] as number[] })
const commitFrequency = ref({ dates: [] as string[], counts: [] as number[] })
const fileTypes = ref([] as Array<{ extension: string; count: number; percentage: number }>)
const changeTypes = ref([] as Array<{ category: string; count: number; percentage: number }>)
const activityHeatmap = ref({ byHour: {} as Record<number, number>, totalCommits: 0 })
const fileHeatmap = ref([] as Array<{ filePath: string; modifyCount: number; heatLevel: number }>)

const repoId = computed(() => repoStore.currentRepo?.id ?? null)

async function loadStats() {
  if (!repoId.value) {
    return
  }

  loading.value = true
  try {
    const [lines, frequency, types, changes] = await Promise.all([
      statsApi.getLinesTrend(repoId.value),
      statsApi.getCommitFrequency(repoId.value),
      statsApi.getFileTypes(repoId.value),
      statsApi.getChangeTypes(repoId.value)
    ])

    linesTrend.value = lines
    commitFrequency.value = frequency
    fileTypes.value = types
    changeTypes.value = changes
    
    // 加载热力图数据
    try {
      const [activity, files] = await Promise.all([
        statsApi.getActivityHeatmap(repoId.value),
        statsApi.getFileHeatmap(repoId.value)
      ])
      activityHeatmap.value = activity
      fileHeatmap.value = files
    } catch (e) {
      console.error('Failed to load heatmap:', e)
    }
  } catch (error) {
    console.error('Failed to load stats:', error)
    linesTrend.value = { dates: [], additions: [], deletions: [], total: [] }
    commitFrequency.value = { dates: [], counts: [] }
    fileTypes.value = []
    changeTypes.value = []
  } finally {
    loading.value = false
  }
}

watch(repoId, loadStats, { immediate: true })

const linesTrendOption = computed(() => ({
  title: { text: '代码行趋势', left: 'center', textStyle: { fontSize: 12, color: '#94a3b8' } },
  tooltip: { trigger: 'axis' },
  legend: { top: 28, data: ['新增', '删除', '总计'] },
  grid: { left: 40, right: 20, top: 60, bottom: 40 },
  xAxis: { type: 'category', data: linesTrend.value.dates, axisLabel: { rotate: 45 } },
  yAxis: { type: 'value' },
  series: [
    { name: '新增', type: 'line', data: linesTrend.value.additions, smooth: true },
    { name: '删除', type: 'line', data: linesTrend.value.deletions, smooth: true },
    { name: '总计', type: 'line', data: linesTrend.value.total, smooth: true }
  ]
}))

const commitFrequencyOption = computed(() => ({
  title: { text: '提交频率', left: 'center', textStyle: { fontSize: 12, color: '#94a3b8' } },
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 20, top: 50, bottom: 40 },
  xAxis: { type: 'category', data: commitFrequency.value.dates, axisLabel: { rotate: 45 } },
  yAxis: { type: 'value' },
  series: [
    { type: 'bar', data: commitFrequency.value.counts, itemStyle: { color: '#38bdf8' } }
  ]
}))

const fileTypesOption = computed(() => ({
  title: { text: '文件类型分布', left: 'center', textStyle: { fontSize: 12, color: '#94a3b8' } },
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, left: 'center' },
  series: [
    {
      type: 'pie',
      radius: ['35%', '60%'],
      data: fileTypes.value.map((item) => ({
        name: item.extension || 'unknown',
        value: item.count
      }))
    }
  ]
}))

const changeTypesOption = computed(() => ({
  title: { text: '变更类型分布', left: 'center', textStyle: { fontSize: 12, color: '#94a3b8' } },
  tooltip: { trigger: 'item' },
  legend: { bottom: 0, left: 'center' },
  series: [
    {
      type: 'pie',
      radius: ['35%', '60%'],
      data: changeTypes.value.map((item) => ({
        name: item.category,
        value: item.count
      }))
    }
  ]
}))

// 活跃时间热力图配置
const activityHeatmapOption = computed(() => {
  const hours = Array.from({ length: 24 }, (_, i) => `${i}:00`)
  const data = hours.map((_, i) => [i, 0, activityHeatmap.value.byHour[i] || 0])
  
  return {
    title: { text: '提交时段分布', left: 'center', textStyle: { fontSize: 12, color: '#94a3b8' } },
    tooltip: {
      formatter: (params: any) => `${params.data[0]}:00 - ${params.data[2]} 次提交`
    },
    grid: { left: 40, right: 20, top: 50, bottom: 60 },
    xAxis: {
      type: 'category',
      data: hours,
      axisLabel: { rotate: 45 }
    },
    yAxis: { type: 'category', data: ['提交'] },
    visualMap: {
      min: 0,
      max: Math.max(...Object.values(activityHeatmap.value.byHour || { 0: 1 }), 1),
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: { color: ['#1a2233', '#00ff88'] }
    },
    series: [{
      type: 'heatmap',
      data: data,
      label: { show: true, formatter: (p: any) => p.data[2] || '' },
      emphasis: { itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.5)' } }
    }]
  }
})
</script>

<template>
  <div class="stats-tab" v-loading="loading">
    <div class="stats-grid">
      <div class="chart-card">
        <v-chart :option="commitFrequencyOption" class="chart" autoresize />
      </div>

      <div class="chart-card">
        <v-chart :option="fileTypesOption" class="chart" autoresize />
      </div>

      <div class="chart-card">
        <v-chart :option="changeTypesOption" class="chart" autoresize />
      </div>

      <!-- 活跃时间热力图 -->
      <div class="chart-card chart-card--wide">
        <v-chart :option="activityHeatmapOption" class="chart" autoresize />
      </div>
      
      <!-- 文件热力图 -->
      <div class="chart-card chart-card--wide" v-if="fileHeatmap.length > 0">
        <h3>
          <el-icon><Document /></el-icon>
          文件修改热力图
        </h3>
        <div class="file-heatmap">
          <div 
            v-for="file in fileHeatmap.slice(0, 20)" 
            :key="file.filePath" 
            class="heatmap-item"
            :class="`heat-level-${file.heatLevel}`"
          >
            <span class="file-name">{{ file.filePath.split('/').pop() }}</span>
            <span class="file-count">{{ file.modifyCount }}</span>
          </div>
        </div>
      </div>

      <div class="chart-card chart-card--wide" v-if="repoStore.contributors.length > 0">
        <h3>
          <el-icon><User /></el-icon>
          贡献者统计
        </h3>
        <div class="contributors-table">
          <div class="table-header">
            <span class="col-rank">#</span>
            <span class="col-author">贡献者</span>
            <span class="col-commits">提交数</span>
          </div>
          <div
            v-for="(contributor, index) in repoStore.contributors"
            :key="contributor.authorName"
            class="table-row"
          >
            <span class="col-rank">{{ index + 1 }}</span>
            <span class="col-author">
              <div class="author-avatar">{{ contributor.authorName.charAt(0).toUpperCase() }}</div>
              {{ contributor.authorName }}
            </span>
            <span class="col-commits">{{ contributor.commitCount }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stats-tab {
  min-height: 500px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--spacing-lg);
}

.chart-card {
  background: var(--bg-secondary);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg);
}

.chart-card--wide {
  grid-column: span 2;
}

.chart-card h3 {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
  font-size: 1rem;
  margin-bottom: var(--spacing-md);
}

.chart-card h3 .el-icon {
  color: var(--color-primary);
}

.chart {
  width: 100%;
  height: 300px;
}


.contributors-table {
  width: 100%;
}

.table-header,
.table-row {
  display: grid;
  grid-template-columns: 40px 1fr 100px;
  gap: var(--spacing-md);
  padding: var(--spacing-sm) var(--spacing-md);
  align-items: center;
}

.table-header {
  font-size: 0.8rem;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border-color);
}

.table-row {
  font-size: 0.9rem;
  border-bottom: 1px solid var(--border-color);
}

.table-row:last-child {
  border-bottom: none;
}

.col-author {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.author-avatar {
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, var(--color-primary), var(--color-accent));
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-inverse);
}

.col-commits {
  text-align: center;
  font-family: var(--font-mono);
}

@media (max-width: 1024px) {
  .stats-grid {
    grid-template-columns: 1fr;
  }

  .chart-card--wide {
    grid-column: span 1;
  }
}

@media (max-width: 768px) {
  .table-header,
  .table-row {
    grid-template-columns: 30px 1fr 60px;
    font-size: 0.8rem;
  }
}

/* 文件热力图样式 */
.file-heatmap {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-sm);
}

.heatmap-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--spacing-sm) var(--spacing-md);
  border-radius: var(--radius-md);
  min-width: 150px;
  flex: 1 1 200px;
  font-size: 0.85rem;
  transition: transform var(--transition-fast);
}

.heatmap-item:hover {
  transform: scale(1.02);
}

.heatmap-item .file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 150px;
}

.heatmap-item .file-count {
  font-family: var(--font-mono);
  font-weight: 600;
  margin-left: var(--spacing-sm);
}

/* 热度等级颜色 */
.heat-level-1 {
  background: rgba(0, 255, 136, 0.1);
  border: 1px solid rgba(0, 255, 136, 0.2);
}

.heat-level-2 {
  background: rgba(0, 255, 136, 0.2);
  border: 1px solid rgba(0, 255, 136, 0.3);
}

.heat-level-3 {
  background: rgba(0, 255, 136, 0.35);
  border: 1px solid rgba(0, 255, 136, 0.5);
  color: var(--text-inverse);
}

.heat-level-4 {
  background: rgba(0, 255, 136, 0.6);
  border: 1px solid rgba(0, 255, 136, 0.8);
  color: var(--text-inverse);
}
</style>
