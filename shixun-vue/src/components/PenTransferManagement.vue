<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'
import type { TransferRecord, Pen, Animal, AlertType } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface TransferForm { earTag: string; toPenId: number | null; eventTime: string; reason: string }

const records = ref<TransferRecord[]>([])
const pens = ref<Pen[]>([])
const search = ref<string>('')
const showModal = ref<boolean>(false)
const form = ref<TransferForm>({ earTag: '', toPenId: null, eventTime: '', reason: '' })
const currentAnimal = ref<Animal | null>(null)
const lookingUp = ref<boolean>(false)

const page = ref(1)
const pageSize = 10
const filtered = computed(() =>
  records.value.filter(r =>
    r.earTag?.includes(search.value) ||
    r.fromPenName?.includes(search.value) ||
    r.toPenName?.includes(search.value)
  )
)
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const paginated = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

// 排除当前圈舍，避免原地转舍
const availablePens = computed(() =>
  currentAnimal.value?.currentPenId
    ? pens.value.filter(p => p.id !== currentAnimal.value!.currentPenId)
    : pens.value
)

async function load() {
  const [rRes, pRes] = await Promise.all([fetch('/api/events/transfer'), fetch('/api/pens/active')])
  records.value = await rRes.json()
  pens.value = await pRes.json()
}

function openAdd() {
  form.value = { earTag: '', toPenId: null, eventTime: today(), reason: '' }
  currentAnimal.value = null
  showModal.value = true
}

async function lookupAnimal() {
  const tag = form.value.earTag.trim()
  if (!tag) { currentAnimal.value = null; return }
  lookingUp.value = true
  try {
    const res = await fetch(`/api/animals/ear-tag/${encodeURIComponent(tag)}`)
    if (res.ok) {
      currentAnimal.value = await res.json()
      form.value.toPenId = null
    } else {
      currentAnimal.value = null
    }
  } catch {
    currentAnimal.value = null
  } finally {
    lookingUp.value = false
  }
}

async function save() {
  const res = await fetch('/api/events/transfer', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value)
  })
  if (res.ok) { showModal.value = false; load(); emit('alert', '转舍完成，存栏数已同步更新') }
  else { const err = await res.json(); emit('alert', err.error || '转舍失败', 'error') }
}

async function deleteRecord(id) {
  if (!confirm('确定删除该转舍记录？')) return
  await fetch(`/api/events/transfer/${id}`, { method: 'DELETE' })
  load(); emit('alert', '删除成功')
}

function today() { return new Date().toISOString().split('T')[0] }

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">转舍管理</h2>
        <p class="page-desc">事务保障数据一致性：记录、圈舍更新、存栏计数原子操作</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M17 1l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
        执行转舍
      </button>
    </div>

    <div class="tx-banner">
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
      <span>执行转舍时，系统采用数据库事务机制，将"插入转舍记录"、"更新个体当前圈舍"、"调整存栏计数"封装为原子操作，任一失败则整体回滚。</span>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">转舍记录总数</div>
        <div class="stat-num purple">{{ records.length }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索耳标号或圈舍名称..." />
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>耳标号</th>
              <th>原圈舍</th>
              <th>目标圈舍</th>
              <th>转舍日期</th>
              <th>转舍原因</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in paginated" :key="r.id">
              <td><code>{{ r.earTag }}</code></td>
              <td>
                <span class="badge badge-neutral">{{ r.fromPenName || '初始' }}</span>
              </td>
              <td>
                <span class="badge badge-primary">{{ r.toPenName }}</span>
              </td>
              <td>{{ r.eventTime }}</td>
              <td class="cell-truncate">{{ r.reason || '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-del" @click="deleteRecord(r.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="paginated.length === 0">
              <td colspan="6"><div class="empty-state"><p>暂无转舍记录</p></div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination" v-if="totalPages > 1">
        <button class="pg-btn" :disabled="page === 1" @click="page--">‹</button>
        <span class="pg-info">第 {{ page }} / {{ totalPages }} 页 &nbsp;共 {{ filtered.length }} 条</span>
        <button class="pg-btn" :disabled="page === totalPages" @click="page++">›</button>
      </div>
    </div>

    <Modal :show="showModal" title="执行转舍" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group" style="grid-column: 1 / -1">
          <label>耳标号 <span style="color:var(--c-error)">*</span></label>
          <input
            v-model="form.earTag"
            placeholder="输入待转舍个体耳标号后离开此框自动查询"
            @blur="lookupAnimal"
          />
        </div>

        <!-- 当前圈舍展示区 -->
        <div class="form-group" style="grid-column: 1 / -1">
          <label>当前所在圈舍</label>
          <div v-if="lookingUp" class="pen-info pen-info--loading">查询中…</div>
          <div v-else-if="currentAnimal" class="pen-info pen-info--found">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>
            <span>{{ currentAnimal.currentPenName || '暂未入圈' }}</span>
            <span v-if="currentAnimal.status === 'SOLD'" class="sold-tag">已出栏</span>
          </div>
          <div v-else-if="form.earTag.trim()" class="pen-info pen-info--notfound">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>
            未找到该耳标号对应的个体
          </div>
          <div v-else class="pen-info pen-info--empty">请先输入耳标号</div>
        </div>

        <div class="form-group">
          <label>目标圈舍 <span style="color:var(--c-error)">*</span></label>
          <select v-model.number="form.toPenId" :disabled="!currentAnimal || currentAnimal.status === 'SOLD'">
            <option :value="null">— 请选择目标圈舍 —</option>
            <option v-for="p in availablePens" :key="p.id" :value="p.id">
              {{ p.penName }}（存栏 {{ p.currentCount }}/{{ p.capacity }}）
            </option>
          </select>
          <small v-if="currentAnimal?.currentPenId" style="color:var(--c-text-3);margin-top:4px;display:block">
            已自动排除当前圈舍
          </small>
        </div>

        <div class="form-group">
          <label>转舍日期</label>
          <input v-model="form.eventTime" type="date" />
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>转舍原因</label>
          <textarea v-model="form.reason" rows="2" placeholder="如 扩栏分群、疾病隔离等"></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showModal = false">取消</button>
        <button class="btn btn-primary" @click="save">确认转舍</button>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.tx-banner {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 12px 14px;
  background: var(--c-info-bg);
  border: 1px solid #BFDBFE;
  border-radius: var(--r);
  font-size: 13px;
  color: var(--c-info);
  margin-bottom: 20px;
}

.tx-banner svg { flex-shrink: 0; margin-top: 1px; }

.pagination {
  display: flex; align-items: center; justify-content: flex-end;
  gap: 12px; padding: 12px 16px; border-top: 1px solid var(--c-border);
  font-size: 13px; color: var(--c-text-2);
}
.pg-btn {
  width: 28px; height: 28px; border: 1px solid var(--c-border);
  border-radius: var(--r); background: var(--c-surface); cursor: pointer;
  font-size: 16px; display: flex; align-items: center; justify-content: center; color: var(--c-text);
}
.pg-btn:disabled { opacity: .4; cursor: not-allowed; }
.pg-btn:not(:disabled):hover { border-color: var(--c-primary); color: var(--c-primary); }

.cell-truncate {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text-2);
}

.pen-info {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: var(--r);
  font-size: 13px;
  min-height: 36px;
}

.pen-info--found {
  background: #F0FDF4;
  border: 1px solid #BBF7D0;
  color: #166534;
  font-weight: 500;
}

.pen-info--notfound {
  background: #FEF2F2;
  border: 1px solid #FECACA;
  color: var(--c-error);
}

.pen-info--loading,
.pen-info--empty {
  background: var(--c-bg-2);
  border: 1px solid var(--c-border);
  color: var(--c-text-3);
}

.sold-tag {
  margin-left: 6px;
  padding: 1px 6px;
  background: #FEF2F2;
  color: var(--c-error);
  border-radius: 4px;
  font-size: 11px;
}
</style>
