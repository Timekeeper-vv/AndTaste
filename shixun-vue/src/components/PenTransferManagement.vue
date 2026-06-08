<script setup>
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'

const emit = defineEmits(['alert'])

const records = ref([])
const pens = ref([])
const search = ref('')
const showModal = ref(false)
const form = ref({ earTag: '', toPenId: null, eventTime: '', reason: '' })

const filtered = computed(() =>
  records.value.filter(r =>
    r.earTag?.includes(search.value) ||
    r.fromPenName?.includes(search.value) ||
    r.toPenName?.includes(search.value)
  )
)

async function load() {
  const [rRes, pRes] = await Promise.all([fetch('/api/events/transfer'), fetch('/api/pens/active')])
  records.value = await rRes.json()
  pens.value = await pRes.json()
}

function openAdd() {
  form.value = { earTag: '', toPenId: null, eventTime: today(), reason: '' }
  showModal.value = true
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
            <tr v-for="r in filtered" :key="r.id">
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
            <tr v-if="filtered.length === 0">
              <td colspan="6"><div class="empty-state"><p>暂无转舍记录</p></div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Modal :show="showModal" title="执行转舍" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.earTag" placeholder="输入待转舍个体耳标号" />
        </div>
        <div class="form-group">
          <label>目标圈舍 <span style="color:var(--c-error)">*</span></label>
          <select v-model.number="form.toPenId">
            <option :value="null">— 请选择目标圈舍 —</option>
            <option v-for="p in pens" :key="p.id" :value="p.id">{{ p.penName }}（存栏 {{ p.currentCount }}/{{ p.capacity }}）</option>
          </select>
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

.cell-truncate {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text-2);
}
</style>
