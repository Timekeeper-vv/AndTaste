<script setup>
import { ref } from 'vue'

const emit = defineEmits(['alert'])

const mode = ref('animal')
const query = ref('')
const loading = ref(false)
const animalResult = ref(null)
const batchResult = ref(null)

const eventTypeMap = {
  ENTRY:        { label: '入栏登记', color: 'var(--c-success)',  bg: 'var(--c-success-bg)' },
  IMMUNIZATION: { label: '免疫接种', color: 'var(--c-info)',     bg: 'var(--c-info-bg)' },
  MEDICATION:   { label: '用药治疗', color: 'var(--c-warning)',  bg: 'var(--c-warning-bg)' },
  PEN_TRANSFER: { label: '转舍记录', color: 'var(--c-purple)',   bg: 'var(--c-purple-bg)' },
  SLAUGHTER:    { label: '出栏登记', color: 'var(--c-error)',    bg: 'var(--c-error-bg)' },
}

async function search() {
  if (!query.value.trim()) { emit('alert', '请输入查询内容', 'error'); return }
  loading.value = true
  animalResult.value = null
  batchResult.value = null
  try {
    if (mode.value === 'animal') {
      const res = await fetch(`/api/traceability/animal/${encodeURIComponent(query.value.trim())}`)
      if (res.ok) animalResult.value = await res.json()
      else emit('alert', `耳标号 "${query.value}" 不存在`, 'error')
    } else {
      const res = await fetch(`/api/traceability/batch/${encodeURIComponent(query.value.trim())}`)
      if (res.ok) batchResult.value = await res.json()
      else emit('alert', `批次号 "${query.value}" 不存在`, 'error')
    }
  } finally { loading.value = false }
}

function getEventMeta(type) {
  return eventTypeMap[type] || { label: type, color: 'var(--c-text-2)', bg: 'var(--c-bg)' }
}

function switchMode(m) {
  mode.value = m
  query.value = ''
  animalResult.value = null
  batchResult.value = null
}

function statusLabel(s) { return s === 'ACTIVE' ? '在栏' : '已出栏' }
function genderLabel(g) { return g === 'MALE' ? '公' : '母' }
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">全链路溯源查询</h2>
        <p class="page-desc">通过耳标号或批次号生成结构化生命周期时间线</p>
      </div>
    </div>

    <!-- Search panel -->
    <div class="search-panel">
      <div class="mode-toggle">
        <button :class="['mode-btn', { active: mode === 'animal' }]" @click="switchMode('animal')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>
          个体溯源（耳标号）
        </button>
        <button :class="['mode-btn', { active: mode === 'batch' }]" @click="switchMode('batch')">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>
          批次溯源（批次号）
        </button>
      </div>
      <div class="search-row">
        <div class="search-wrap" style="flex:1">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input
            v-model="query"
            class="search-input"
            :placeholder="mode === 'animal' ? '输入耳标号，如 ET-001' : '输入批次号，如 BATCH-2024-001'"
            @keyup.enter="search"
          />
        </div>
        <button class="btn btn-primary" :disabled="loading" @click="search">
          <svg v-if="loading" width="14" height="14" class="spinning" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          {{ loading ? '查询中...' : '开始溯源' }}
        </button>
      </div>
    </div>

    <!-- Animal result -->
    <template v-if="animalResult">
      <div class="result-section">
        <div class="animal-card">
          <div class="animal-avatar">{{ animalResult.animal?.earTag?.slice(-3) }}</div>
          <div class="animal-info">
            <div class="animal-ear-tag"><code style="font-size:18px;font-weight:700">{{ animalResult.animal?.earTag }}</code></div>
            <div class="animal-meta">
              <span class="badge badge-neutral">{{ genderLabel(animalResult.animal?.gender) }}</span>
              <span class="badge badge-neutral">{{ animalResult.animal?.breed }}</span>
              <span class="badge badge-neutral">批次: {{ animalResult.animal?.batchCode }}</span>
              <span class="badge badge-neutral">圈舍: {{ animalResult.animal?.currentPenName || '—' }}</span>
              <span class="badge badge-neutral">入栏: {{ animalResult.animal?.entryDate }}</span>
            </div>
          </div>
          <span :class="['status-badge', animalResult.animal?.status === 'ACTIVE' ? 'active' : 'sold']">
            {{ statusLabel(animalResult.animal?.status) }}
          </span>
        </div>

        <div class="tl-header">
          <h3>生命周期时间线</h3>
          <span class="badge badge-neutral">共 {{ animalResult.timeline?.length }} 条事件</span>
        </div>

        <div class="timeline">
          <div class="tl-track"></div>
          <div v-for="(evt, idx) in animalResult.timeline" :key="idx" class="tl-item">
            <div class="tl-dot" :style="{ background: getEventMeta(evt.eventType).color }">
              <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="3"><polyline points="20 6 9 17 4 12"/></svg>
            </div>
            <div class="tl-card">
              <div class="tl-card-header">
                <span class="badge" :style="{ background: getEventMeta(evt.eventType).bg, color: getEventMeta(evt.eventType).color }">
                  {{ getEventMeta(evt.eventType).label }}
                </span>
                <span class="text-xs text-muted">{{ evt.eventTime }}</span>
                <span v-if="evt.operator" class="text-xs text-muted">执行人: {{ evt.operator }}</span>
              </div>
              <div class="tl-card-body">
                <strong>{{ evt.itemName }}</strong>
                <p v-if="evt.detail" class="text-sm text-muted">{{ evt.detail }}</p>
                <p v-if="evt.dosage" class="text-xs text-muted">剂量/重量: {{ evt.dosage }}</p>
              </div>
            </div>
          </div>
          <div v-if="animalResult.timeline?.length === 0" class="empty-state">
            <p>该个体暂无事件记录</p>
          </div>
        </div>
      </div>
    </template>

    <!-- Batch result -->
    <template v-if="batchResult">
      <div class="result-section">
        <div class="batch-header-card">
          <div>
            <h3 class="batch-code">{{ batchResult.batch?.batch_code }}</h3>
            <p class="text-sm text-muted">{{ batchResult.batch?.breed }} · 入栏: {{ batchResult.batch?.entry_date }}</p>
          </div>
        </div>

        <div class="batch-kpis">
          <div class="batch-kpi">
            <div class="bkpi-num">{{ batchResult.batch?.total_count }}</div>
            <div class="bkpi-label">总存栏</div>
          </div>
          <div class="batch-kpi">
            <div class="bkpi-num" style="color:var(--c-error)">{{ batchResult.batch?.sold_count }}</div>
            <div class="bkpi-label">已出栏</div>
          </div>
          <div class="batch-kpi">
            <div class="bkpi-num" style="color:var(--c-info)">{{ batchResult.batch?.immunization_count }}</div>
            <div class="bkpi-label">免疫次数</div>
          </div>
          <div class="batch-kpi">
            <div class="bkpi-num" style="color:var(--c-warning)">{{ batchResult.batch?.medication_count }}</div>
            <div class="bkpi-label">用药次数</div>
          </div>
          <div class="batch-kpi">
            <div class="bkpi-num" style="color:var(--c-purple)">{{ batchResult.batch?.transfer_count }}</div>
            <div class="bkpi-label">转舍次数</div>
          </div>
        </div>

        <div class="table-card" style="margin-top:16px">
          <div style="padding:16px 16px 0">
            <h4>批次个体明细（{{ batchResult.animals?.length }} 条）</h4>
          </div>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>耳标号</th>
                  <th>性别</th>
                  <th>品种</th>
                  <th>当前圈舍</th>
                  <th>出生重</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="a in batchResult.animals" :key="a.id">
                  <td><code>{{ a.earTag }}</code></td>
                  <td>{{ genderLabel(a.gender) }}</td>
                  <td>{{ a.breed }}</td>
                  <td>{{ a.currentPenName || '—' }}</td>
                  <td>{{ a.birthWeight ? a.birthWeight + ' kg' : '—' }}</td>
                  <td>
                    <span :class="['status-badge', a.status === 'ACTIVE' ? 'active' : 'sold']">
                      {{ statusLabel(a.status) }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* Search panel */
.search-panel {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 20px;
  box-shadow: var(--shadow-sm);
  margin-bottom: 24px;
}

.mode-toggle {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.mode-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--c-border);
  border-radius: var(--r);
  background: var(--c-surface);
  color: var(--c-text-2);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font);
  cursor: pointer;
  transition: all .15s;
}

.mode-btn.active {
  background: var(--c-primary);
  border-color: var(--c-primary);
  color: #fff;
}

.search-row {
  display: flex;
  gap: 12px;
}

/* Animal result */
.animal-card {
  display: flex;
  align-items: center;
  gap: 20px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: var(--shadow-sm);
}

.animal-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--c-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
}

.animal-info { flex: 1; }
.animal-ear-tag { margin-bottom: 8px; }
.animal-meta { display: flex; flex-wrap: wrap; gap: 6px; }

/* Timeline */
.tl-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.timeline {
  position: relative;
  padding-left: 36px;
  margin-bottom: 8px;
}

.tl-track {
  position: absolute;
  left: 11px;
  top: 12px;
  bottom: 12px;
  width: 2px;
  background: var(--c-border);
  border-radius: 1px;
}

.tl-item {
  position: relative;
  margin-bottom: 16px;
}

.tl-dot {
  position: absolute;
  left: -34px;
  top: 10px;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid var(--c-surface);
  box-shadow: var(--shadow-sm);
}

.tl-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r);
  padding: 12px 14px;
  box-shadow: var(--shadow-sm);
}

.tl-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.tl-card-body strong {
  font-size: 14px;
  color: var(--c-text);
}

.tl-card-body p { margin-top: 4px; }

/* Batch result */
.batch-header-card {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-sm);
}

.batch-code {
  font-size: 18px;
  margin-bottom: 4px;
}

.batch-kpis {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 4px;
}

.batch-kpi {
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r);
  padding: 16px;
  text-align: center;
  box-shadow: var(--shadow-sm);
}

.bkpi-num {
  font-size: 24px;
  font-weight: 700;
  color: var(--c-text);
  line-height: 1;
  margin-bottom: 6px;
}

.bkpi-label {
  font-size: 12px;
  color: var(--c-text-2);
}

.spinning { animation: spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
