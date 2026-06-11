<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import type { DashboardStats, AlertType, PageName } from '../types'

const emit = defineEmits<{
  'switch-page': [page: PageName]
  'alert': [msg: string, type?: AlertType]
}>()

const stats = ref<DashboardStats>({ animals: 0, activeAnimals: 0, soldAnimals: 0, deadAnimals: 0, pens: 0, activePens: 0, batches: 0, drugs: 0 })
const loading = ref<boolean>(true)

// 存栏率计算
const stockRate = computed(() => {
  if (!stats.value.animals) return 0
  return Math.round(stats.value.activeAnimals / stats.value.animals * 100)
})

const outRate = computed(() => {
  if (!stats.value.animals) return 0
  return Math.round(stats.value.soldAnimals / stats.value.animals * 100)
})

const deadRate = computed(() => {
  if (!stats.value.animals) return 0
  return Math.round(stats.value.deadAnimals / stats.value.animals * 100)
})

onMounted(async () => {
  try {
    const [aRes, pRes, bRes, dRes] = await Promise.all([
      fetch('/api/animals'),
      fetch('/api/pens'),
      fetch('/api/batches'),
      fetch('/api/drugs-vaccines'),
    ])
    const animals = await aRes.json()
    const pens    = await pRes.json()
    const batches = await bRes.json()
    const drugs   = await dRes.json()

    stats.value = {
      animals:       animals.length,
      activeAnimals: animals.filter(a => a.status === 'ACTIVE').length,
      soldAnimals:   animals.filter(a => a.status === 'SOLD').length,
      deadAnimals:   animals.filter(a => a.status === 'DEAD').length,
      pens:          pens.length,
      activePens:    pens.filter(p => p.status === 1).length,
      batches:       batches.length,
      drugs:         drugs.length,
    }
  } catch {
    /* silent - show zeros */
  } finally {
    loading.value = false
  }
})

const shortcuts: Array<{ page: PageName; label: string; desc: string; icon: string; color: string }> = [
  { page: 'animals',      label: '个体档案',  desc: '查看和管理所有牲畜个体记录',   icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>`, color: 'teal' },
  { page: 'batches',      label: '养殖批次',  desc: '管理牲畜养殖批次及分组',       icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>`, color: 'indigo' },
  { page: 'immunization', label: '免疫记录',  desc: '记录疫苗接种情况与计划',       icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`, color: 'blue' },
  { page: 'medication',   label: '用药记录',  desc: '跟踪用药和治疗过程',           icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M10.5 20H4a2 2 0 0 1-2-2V5c0-1.1.9-2 2-2h3.93a2 2 0 0 1 1.66.9l.82 1.2a2 2 0 0 0 1.66.9H20a2 2 0 0 1 2 2v3"/><circle cx="18" cy="18" r="3"/><path d="m22 22-1.5-1.5"/></svg>`, color: 'amber' },
  { page: 'traceability', label: '溯源查询',  desc: '全链路个体与批次溯源',         icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>`, color: 'purple' },
  { page: 'pens',         label: '圈舍管理',  desc: '查看圈舍容量与状态',           icon: `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`, color: 'green' },
]
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">概览仪表盘</h2>
        <p class="page-desc">欢迎回来，以下是养殖场今日运营概况</p>
      </div>
    </div>

    <!-- ── 库存流量核心指标 ── -->
    <div class="section-label">存栏流量总览</div>
    <div class="flow-grid">
      <!-- 累计入栏 -->
      <div class="flow-card flow-card--in">
        <div class="flow-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M12 2v20M2 12h20"/><path d="M17 7l5 5-5 5"/>
          </svg>
        </div>
        <div class="flow-body">
          <div class="flow-label">累计入栏</div>
          <div class="flow-value">{{ loading ? '—' : stats.animals }}</div>
          <div class="flow-unit">头</div>
        </div>
        <div class="flow-badge in">入</div>
      </div>

      <!-- 箭头 -->
      <div class="flow-arrow">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M5 12h14M13 6l6 6-6 6"/>
        </svg>
      </div>

      <!-- 当前存栏 -->
      <div class="flow-card flow-card--stock">
        <div class="flow-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
            <polyline points="9 22 9 12 15 12 15 22"/>
          </svg>
        </div>
        <div class="flow-body">
          <div class="flow-label">当前存栏</div>
          <div class="flow-value">{{ loading ? '—' : stats.activeAnimals }}</div>
          <div class="flow-unit">头在栏</div>
        </div>
        <div class="flow-rate">{{ loading ? '' : stockRate + '%' }}</div>
      </div>

      <!-- 箭头 -->
      <div class="flow-arrow">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M5 12h14M13 6l6 6-6 6"/>
        </svg>
      </div>

      <!-- 累计出栏 -->
      <div class="flow-card flow-card--out">
        <div class="flow-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
        </div>
        <div class="flow-body">
          <div class="flow-label">累计出栏</div>
          <div class="flow-value">{{ loading ? '—' : stats.soldAnimals }}</div>
          <div class="flow-unit">头</div>
        </div>
        <div class="flow-badge out">出</div>
      </div>

      <!-- 箭头 -->
      <div class="flow-arrow">
        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M5 12h14M13 6l6 6-6 6"/>
        </svg>
      </div>

      <!-- 累计死亡 -->
      <div class="flow-card flow-card--dead">
        <div class="flow-icon">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>
          </svg>
        </div>
        <div class="flow-body">
          <div class="flow-label">累计死亡</div>
          <div class="flow-value">{{ loading ? '—' : stats.deadAnimals }}</div>
          <div class="flow-unit">头</div>
        </div>
        <div class="flow-badge dead">亡</div>
      </div>
    </div>

    <!-- 存栏进度条 -->
    <div class="stock-bar-wrap" v-if="!loading && stats.animals > 0">
      <div class="stock-bar">
        <div class="stock-bar__fill" :style="{ width: stockRate + '%' }"></div>
        <div class="stock-bar__out" :style="{ width: outRate + '%', left: stockRate + '%' }"></div>
        <div class="stock-bar__dead" :style="{ width: deadRate + '%', left: (stockRate + outRate) + '%' }"></div>
      </div>
      <div class="stock-bar-legend">
        <span class="legend-dot in"></span>在栏 {{ stats.activeAnimals }} 头（{{ stockRate }}%）
        <span class="legend-dot out" style="margin-left:16px"></span>已出栏 {{ stats.soldAnimals }} 头（{{ outRate }}%）
        <span class="legend-dot dead" style="margin-left:16px"></span>死亡 {{ stats.deadAnimals }} 头（{{ deadRate }}%）
      </div>
    </div>

    <!-- ── KPI 次级指标 ── -->
    <div class="section-label" style="margin-top: 28px;">运营指标</div>
    <div class="kpi-grid">
      <div class="kpi-card" @click="emit('switch-page', 'pens')" style="cursor:pointer">
        <div class="kpi-header">
          <span class="kpi-label">圈舍总数</span>
          <div class="kpi-icon green">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
          </div>
        </div>
        <div class="kpi-value">{{ loading ? '—' : stats.pens }}</div>
        <div class="kpi-sub">启用中 <strong class="text-success">{{ loading ? '—' : stats.activePens }}</strong></div>
      </div>

      <div class="kpi-card" @click="emit('switch-page', 'batches')" style="cursor:pointer">
        <div class="kpi-header">
          <span class="kpi-label">养殖批次</span>
          <div class="kpi-icon indigo">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>
          </div>
        </div>
        <div class="kpi-value">{{ loading ? '—' : stats.batches }}</div>
        <div class="kpi-sub">当前活跃批次总数</div>
      </div>

      <div class="kpi-card" @click="emit('switch-page', 'drugs')" style="cursor:pointer">
        <div class="kpi-header">
          <span class="kpi-label">药品疫苗库</span>
          <div class="kpi-icon amber">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v18m0 0h10a2 2 0 0 0 2-2V9M9 21H5a2 2 0 0 1-2-2V9m0 0h18"/></svg>
          </div>
        </div>
        <div class="kpi-value">{{ loading ? '—' : stats.drugs }}</div>
        <div class="kpi-sub">标准化药品 / 疫苗条目</div>
      </div>

      <div class="kpi-card" @click="emit('switch-page', 'statistics')" style="cursor:pointer">
        <div class="kpi-header">
          <span class="kpi-label">出栏率</span>
          <div class="kpi-icon teal">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
          </div>
        </div>
        <div class="kpi-value" :style="{ color: outRate > 50 ? 'var(--c-success)' : 'var(--c-text)' }">
          {{ loading ? '—' : outRate + '%' }}
        </div>
        <div class="kpi-sub">查看统计分析 →</div>
      </div>
    </div>

    <!-- 快捷入口 -->
    <div class="section-header">
      <h3>快捷入口</h3>
      <p class="page-desc">常用功能快速访问</p>
    </div>
    <div class="shortcuts-grid">
      <button
        v-for="s in shortcuts"
        :key="s.page"
        class="shortcut-card"
        @click="emit('switch-page', s.page)"
      >
        <div :class="['shortcut-icon', s.color]" v-html="s.icon"></div>
        <div class="shortcut-body">
          <div class="shortcut-label">{{ s.label }}</div>
          <div class="shortcut-desc">{{ s.desc }}</div>
        </div>
        <svg class="shortcut-arrow" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
      </button>
    </div>

    <!-- Info banner -->
    <div class="info-banner">
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
      <span>全链路溯源系统支持通过耳标号或批次号追踪每只动物的完整生命周期记录，符合食品安全合规要求。</span>
      <button class="btn btn-primary btn-sm" @click="emit('switch-page', 'traceability')">开始溯源查询</button>
    </div>
  </div>
</template>

<style scoped>
.section-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .6px;
  text-transform: uppercase;
  color: var(--c-text-3);
  margin-bottom: 12px;
}

/* 流量卡片 */
.flow-grid {
  display: flex;
  align-items: center;
  gap: 0;
  margin-bottom: 16px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 20px 24px;
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.flow-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  border-radius: var(--r-md);
  position: relative;
}

.flow-card--in    { background: linear-gradient(135deg,#F0FDF4,#DCFCE7); }
.flow-card--stock { background: linear-gradient(135deg,#EFF6FF,#DBEAFE); }
.flow-card--out   { background: linear-gradient(135deg,#FFF7ED,#FED7AA); }
.flow-card--dead  { background: linear-gradient(135deg,#F8FAFC,#F1F5F9); }

.flow-icon {
  width: 44px; height: 44px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.flow-card--in    .flow-icon { background: #22C55E20; color: #16A34A; }
.flow-card--stock .flow-icon { background: #3B82F620; color: #2563EB; }
.flow-card--out   .flow-icon { background: #F9731620; color: #EA580C; }
.flow-card--dead  .flow-icon { background: #94A3B820; color: #64748B; }

.flow-body { flex: 1; }
.flow-label { font-size: 11px; font-weight: 600; color: var(--c-text-2); text-transform: uppercase; letter-spacing: .4px; margin-bottom: 4px; }
.flow-value { font-size: 36px; font-weight: 800; line-height: 1; color: var(--c-text); }
.flow-unit  { font-size: 12px; color: var(--c-text-2); margin-top: 2px; }

.flow-badge {
  position: absolute; top: 12px; right: 12px;
  width: 24px; height: 24px; border-radius: 6px;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 700;
}
.flow-badge.in   { background: #DCFCE7; color: #16A34A; }
.flow-badge.out  { background: #FED7AA; color: #EA580C; }
.flow-badge.dead { background: #E2E8F0; color: #64748B; }

.flow-rate {
  position: absolute; top: 12px; right: 12px;
  font-size: 18px; font-weight: 700; color: #2563EB;
}

.flow-arrow {
  width: 40px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  color: var(--c-text-3);
}

/* 存栏进度条 */
.stock-bar-wrap {
  margin-bottom: 8px;
}

.stock-bar {
  position: relative;
  height: 8px;
  background: var(--c-bg-2);
  border-radius: 4px;
  overflow: visible;
  margin-bottom: 8px;
}

.stock-bar__fill {
  position: absolute; left: 0; top: 0; height: 100%;
  background: #22C55E; border-radius: 4px 0 0 4px;
  transition: width .5s ease;
}

.stock-bar__out {
  position: absolute; top: 0; height: 100%;
  background: #FB923C;
  transition: width .5s ease, left .5s ease;
}

.stock-bar__dead {
  position: absolute; top: 0; height: 100%;
  background: #94A3B8;
  transition: width .5s ease, left .5s ease;
}

.stock-bar-legend {
  display: flex; align-items: center; gap: 4px;
  font-size: 12px; color: var(--c-text-2);
}

.legend-dot {
  width: 8px; height: 8px; border-radius: 50%; display: inline-block;
}
.legend-dot.in   { background: #22C55E; }
.legend-dot.out  { background: #FB923C; }
.legend-dot.dead { background: #94A3B8; }

/* KPI grid */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 32px;
}

.kpi-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
  transition: border-color .15s, box-shadow .15s;
}
.kpi-card:hover { border-color: var(--c-primary); box-shadow: 0 0 0 3px rgba(13,148,136,.06); }

.kpi-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
}
.kpi-label {
  font-size: 12px; font-weight: 600; color: var(--c-text-2);
  text-transform: uppercase; letter-spacing: 0.4px;
}
.kpi-icon {
  width: 32px; height: 32px; border-radius: var(--r);
  display: flex; align-items: center; justify-content: center;
}
.kpi-icon.teal   { background: var(--c-primary-light); color: var(--c-primary-dark); }
.kpi-icon.green  { background: var(--c-success-bg); color: var(--c-success); }
.kpi-icon.indigo { background: #EEF2FF; color: var(--c-accent); }
.kpi-icon.amber  { background: var(--c-warning-bg); color: var(--c-warning); }
.kpi-value { font-size: 32px; font-weight: 700; color: var(--c-text); line-height: 1; margin-bottom: 8px; }
.kpi-sub   { font-size: 12px; color: var(--c-text-2); }

/* Shortcuts */
.section-header { margin-bottom: 16px; }
.section-header h3 { margin-bottom: 2px; }

.shortcuts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px; margin-bottom: 24px;
}

.shortcut-card {
  display: flex; align-items: center; gap: 16px; padding: 16px;
  background: var(--c-surface); border: 1px solid var(--c-border);
  border-radius: var(--r-md); cursor: pointer; text-align: left;
  font-family: var(--font); transition: border-color .15s, box-shadow .15s;
}
.shortcut-card:hover { border-color: var(--c-primary); box-shadow: 0 0 0 3px rgba(13,148,136,.08); }

.shortcut-icon {
  width: 40px; height: 40px; border-radius: var(--r);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.shortcut-icon.teal   { background: var(--c-primary-light); color: var(--c-primary-dark); }
.shortcut-icon.indigo { background: #EEF2FF; color: var(--c-accent); }
.shortcut-icon.blue   { background: var(--c-info-bg); color: var(--c-info); }
.shortcut-icon.amber  { background: var(--c-warning-bg); color: var(--c-warning); }
.shortcut-icon.purple { background: var(--c-purple-bg); color: var(--c-purple); }
.shortcut-icon.green  { background: var(--c-success-bg); color: var(--c-success); }

.shortcut-body { flex: 1; min-width: 0; }
.shortcut-label { font-size: 14px; font-weight: 600; color: var(--c-text); margin-bottom: 2px; }
.shortcut-desc  { font-size: 12px; color: var(--c-text-2); }
.shortcut-arrow { color: var(--c-text-3); flex-shrink: 0; }

/* Info banner */
.info-banner {
  display: flex; align-items: center; gap: 10px; padding: 14px 16px;
  background: var(--c-primary-light); border: 1px solid rgba(13,148,136,.2);
  border-radius: var(--r-md); font-size: 13px; color: var(--c-primary-dark);
}
.info-banner svg { flex-shrink: 0; }
.info-banner span { flex: 1; }
.text-success { color: var(--c-success); }
</style>
