<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import type { ECharts } from 'echarts'
import type { MonthlyData, AnimalStatusData, PenUsageData } from '../types'

interface ChartsData {
  monthlySlaughter: MonthlyData[]
  monthlyEntry: MonthlyData[]
  monthlyImmunization: MonthlyData[]
  animalStatus: AnimalStatusData[]
  penUsage: PenUsageData[]
}

const loading = ref<boolean>(true)
const chartsData = ref<ChartsData>({
  monthlySlaughter: [],
  monthlyEntry: [],
  monthlyImmunization: [],
  animalStatus: [],
  penUsage: [],
})

// 累计入栏/出栏/存栏/死亡汇总
const totalIn    = ref(0)
const totalOut   = ref(0)
const totalStock = ref(0)
const totalDead  = ref(0)

// Template refs — populated by Vue when v-else section is mounted
const chartInoutEl        = ref<HTMLElement | null>(null)
const chartImmunizationEl = ref<HTMLElement | null>(null)
const chartStatusEl       = ref<HTMLElement | null>(null)
const chartPenEl          = ref<HTMLElement | null>(null)

let chartInstances: ECharts[] = []

function disposeCharts(): void {
  chartInstances.forEach(c => c?.dispose())
  chartInstances = []
}

function padMonths(data: MonthlyData[]): { months: string[]; values: number[] } {
  const months: string[] = []
  for (let i = 5; i >= 0; i--) {
    const d = new Date()
    d.setDate(1)
    d.setMonth(d.getMonth() - i)
    months.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`)
  }
  const map = Object.fromEntries(data.map(r => [r.month, Number(r.count)]))
  return { months, values: months.map(m => map[m] ?? 0) }
}

function initCharts() {
  disposeCharts()

  // Chart 1: 入栏 vs 出栏 对比（双柱）
  if (chartInoutEl.value) {
    const { months, values: entryVals } = padMonths(chartsData.value.monthlyEntry)
    const { values: slaughterVals } = padMonths(chartsData.value.monthlySlaughter)
    const c = echarts.init(chartInoutEl.value)
    c.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { top: 0, right: 0, textStyle: { fontSize: 12 } },
      grid: { top: 32, right: 20, bottom: 36, left: 46 },
      xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
      series: [
        {
          name: '入栏头数',
          type: 'bar',
          data: entryVals,
          barMaxWidth: 28,
          itemStyle: { color: '#22C55E', borderRadius: [4, 4, 0, 0] },
        },
        {
          name: '出栏头数',
          type: 'bar',
          data: slaughterVals,
          barMaxWidth: 28,
          itemStyle: { color: '#FB923C', borderRadius: [4, 4, 0, 0] },
        },
      ],
    })
    chartInstances.push(c)
  }

  // Chart 2: Monthly immunization trend (line)
  if (chartImmunizationEl.value) {
    const { months, values } = padMonths(chartsData.value.monthlyImmunization)
    const c = echarts.init(chartImmunizationEl.value)
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { top: 20, right: 20, bottom: 36, left: 46 },
      xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
      series: [{
        name: '免疫次数',
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'circle', symbolSize: 6,
        lineStyle: { color: '#0d9488', width: 2 },
        itemStyle: { color: '#0d9488' },
        areaStyle: {
          color: {
            type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(13,148,136,.25)' },
              { offset: 1, color: 'rgba(13,148,136,0)' },
            ],
          },
        },
      }],
    })
    chartInstances.push(c)
  }

  // Chart 3: Animal status pie
  if (chartStatusEl.value) {
    const statusLabels: Record<string, string> = { ACTIVE: '在栏', SOLD: '已出栏', DEAD: '死亡' }
    const statusColors: Record<string, string> = { ACTIVE: '#22C55E', SOLD: '#FB923C', DEAD: '#94A3B8' }
    const pieData = chartsData.value.animalStatus.map(r => ({
      name: statusLabels[r.status] || r.status,
      value: Number(r.count),
      itemStyle: { color: statusColors[r.status] || '#6366f1' },
    }))
    const c = echarts.init(chartStatusEl.value)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} 头 ({d}%)' },
      legend: { bottom: 0, left: 'center', textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['50%', '45%'],
        data: pieData.length ? pieData : [{ name: '暂无数据', value: 1, itemStyle: { color: '#e2e8f0' } }],
        label: { show: true, formatter: '{b}\n{c} 头', fontSize: 12 },
      }],
    })
    chartInstances.push(c)
  }

  // Chart 4: Pen capacity vs usage (horizontal bar)
  if (chartPenEl.value) {
    const pens = chartsData.value.penUsage
    const names = pens.map(p => p.penName)
    const caps  = pens.map(p => Number(p.capacity))
    const counts = pens.map(p => Number(p.currentCount))
    const c = echarts.init(chartPenEl.value)
    c.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { top: 0, right: 0, textStyle: { fontSize: 12 } },
      grid: { top: 28, right: 20, bottom: 36, left: 70 },
      xAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'category', data: names, axisLabel: { fontSize: 11 } },
      series: [
        { name: '设计容量', type: 'bar', data: caps, barMaxWidth: 20, itemStyle: { color: '#E2E8F0', borderRadius: [0, 4, 4, 0] } },
        { name: '当前存栏', type: 'bar', data: counts, barMaxWidth: 20, itemStyle: { color: '#0d9488', borderRadius: [0, 4, 4, 0] } },
      ],
    })
    chartInstances.push(c)
  }
}

function handleResize() {
  chartInstances.forEach(c => c?.resize())
}

// Trigger chart init after loading flips to false and Vue has updated the DOM
watch(loading, (val) => {
  if (val === false) {
    requestAnimationFrame(() => {
      initCharts()
      window.addEventListener('resize', handleResize)
    })
  }
}, { flush: 'post' })

async function safeJson<T>(res: Response, fallback: T): Promise<T> {
  if (!res.ok) { console.warn(`Stats API ${res.url} returned ${res.status}`); return fallback }
  return res.json()
}

onMounted(async () => {
  try {
    const [slRes, entryRes, immRes, statusRes, penRes] = await Promise.all([
      fetch('/api/stats/monthly-slaughter'),
      fetch('/api/stats/monthly-entry'),
      fetch('/api/stats/monthly-immunization'),
      fetch('/api/stats/animal-status'),
      fetch('/api/stats/pen-usage'),
    ])
    const statusData: AnimalStatusData[] = await safeJson(statusRes, [])
    const activeCount = statusData.find(r => r.status === 'ACTIVE')
    const soldCount   = statusData.find(r => r.status === 'SOLD')
    const deadCount   = statusData.find(r => r.status === 'DEAD')
    totalStock.value = activeCount ? Number(activeCount.count) : 0
    totalOut.value   = soldCount   ? Number(soldCount.count)   : 0
    totalDead.value  = deadCount   ? Number(deadCount.count)   : 0
    totalIn.value    = totalStock.value + totalOut.value + totalDead.value

    chartsData.value = {
      monthlySlaughter:    await safeJson(slRes,   []),
      monthlyEntry:        await safeJson(entryRes, []),
      monthlyImmunization: await safeJson(immRes,  []),
      animalStatus:        statusData,
      penUsage:            await safeJson(penRes,  []),
    }
  } catch (err) {
    console.error('Stats fetch failed:', err)
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">数据统计分析</h2>
        <p class="page-desc">可视化呈现养殖场运营关键指标</p>
      </div>
    </div>

    <!-- 入出存死汇总 -->
    <div v-if="!loading" class="summary-row">
      <div class="summary-card in">
        <div class="summary-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v20M2 12h20"/><path d="M17 7l5 5-5 5"/></svg>
        </div>
        <div>
          <div class="summary-label">累计入栏</div>
          <div class="summary-value">{{ totalIn }}</div>
        </div>
        <div class="summary-unit">头</div>
      </div>
      <div class="summary-sep">=</div>
      <div class="summary-card stock">
        <div class="summary-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
        </div>
        <div>
          <div class="summary-label">当前存栏</div>
          <div class="summary-value">{{ totalStock }}</div>
        </div>
        <div class="summary-unit">头在栏</div>
      </div>
      <div class="summary-sep">+</div>
      <div class="summary-card out">
        <div class="summary-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
        </div>
        <div>
          <div class="summary-label">累计出栏</div>
          <div class="summary-value">{{ totalOut }}</div>
        </div>
        <div class="summary-unit">头</div>
      </div>
      <div class="summary-sep">+</div>
      <div class="summary-card dead">
        <div class="summary-icon">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
        </div>
        <div>
          <div class="summary-label">累计死亡</div>
          <div class="summary-value">{{ totalDead }}</div>
        </div>
        <div class="summary-unit">头</div>
      </div>
      <div class="summary-eq">
        <span class="eq-formula">入栏 {{ totalIn }} = 存栏 {{ totalStock }} + 出栏 {{ totalOut }} + 死亡 {{ totalDead }}</span>
      </div>
    </div>

    <div v-if="loading" class="loading-state">加载中…</div>

    <div v-else class="charts-grid">
      <!-- 入栏 vs 出栏 月度对比 -->
      <div class="chart-card chart-card--wide">
        <div class="chart-title">
          <span class="dot green"></span>入栏 vs 出栏月度对比（近6个月）
        </div>
        <div ref="chartInoutEl" class="chart-body"></div>
      </div>

      <!-- 免疫覆盖率趋势 -->
      <div class="chart-card">
        <div class="chart-title">
          <span class="dot teal"></span>免疫覆盖趋势（近6个月）
        </div>
        <div ref="chartImmunizationEl" class="chart-body"></div>
      </div>

      <!-- 个体状态分布 -->
      <div class="chart-card">
        <div class="chart-title">
          <span class="dot blue"></span>个体状态分布（在栏 / 出栏 / 死亡）
        </div>
        <div ref="chartStatusEl" class="chart-body"></div>
      </div>

      <!-- 圈舍容量利用率 -->
      <div class="chart-card chart-card--wide">
        <div class="chart-title">
          <span class="dot orange"></span>圈舍容量利用率
        </div>
        <div ref="chartPenEl" class="chart-body"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 汇总行 */
.summary-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  margin-bottom: 24px;
  box-shadow: var(--shadow-sm);
  flex-wrap: wrap;
}

.summary-card {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px; border-radius: var(--r-md); flex: 1; min-width: 140px;
}
.summary-card.in    { background: #F0FDF4; }
.summary-card.stock { background: #EFF6FF; }
.summary-card.out   { background: #FFF7ED; }
.summary-card.dead  { background: #F8FAFC; }

.summary-icon {
  width: 36px; height: 36px; border-radius: 8px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.summary-card.in    .summary-icon { background: #22C55E20; color: #16A34A; }
.summary-card.stock .summary-icon { background: #3B82F620; color: #2563EB; }
.summary-card.out   .summary-icon { background: #F9731620; color: #EA580C; }
.summary-card.dead  .summary-icon { background: #94A3B820; color: #64748B; }

.summary-label { font-size: 11px; color: var(--c-text-2); font-weight: 500; text-transform: uppercase; letter-spacing: .4px; }
.summary-value { font-size: 28px; font-weight: 800; color: var(--c-text); line-height: 1.1; }
.summary-unit  { font-size: 12px; color: var(--c-text-2); align-self: flex-end; padding-bottom: 3px; }

.summary-sep { font-size: 20px; color: var(--c-border); font-weight: 300; }

.summary-eq {
  flex: 2; min-width: 200px;
  background: var(--c-bg-2); border-radius: var(--r);
  padding: 8px 14px; text-align: center;
}
.eq-formula { font-size: 13px; color: var(--c-text-2); font-family: 'Courier New', monospace; }

/* Loading */
.loading-state {
  text-align: center; padding: 60px; color: var(--c-text-3); font-size: 14px;
}

/* Charts grid */
.charts-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.chart-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
}

.chart-card--wide { grid-column: span 2; }

@media (max-width: 900px) {
  .charts-grid { grid-template-columns: 1fr; }
  .chart-card--wide { grid-column: span 1; }
}

.chart-title {
  display: flex; align-items: center; gap: 6px;
  font-size: 13px; font-weight: 600; color: var(--c-text); margin-bottom: 16px;
}

.dot {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
}
.dot.green  { background: #22C55E; }
.dot.teal   { background: #0d9488; }
.dot.blue   { background: #3B82F6; }
.dot.orange { background: #FB923C; }

.chart-body { height: 260px; width: 100%; }
</style>
