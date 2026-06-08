<script setup>
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'

const emit = defineEmits(['alert'])

const records = ref([])
const drugs = ref([])
const search = ref('')
const showModal = ref(false)
const form = ref({ earTag: '', drugId: null, reason: '', eventTime: '', dosage: '', operator: '' })

const filtered = computed(() =>
  records.value.filter(r => r.earTag?.includes(search.value) || r.drugName?.includes(search.value))
)

async function load() {
  const [rRes, dRes] = await Promise.all([fetch('/api/events/medication'), fetch('/api/drugs-vaccines?category=DRUG')])
  records.value = await rRes.json()
  drugs.value = await dRes.json()
}

function openAdd() {
  form.value = { earTag: '', drugId: null, reason: '', eventTime: today(), dosage: '', operator: '' }
  showModal.value = true
}

async function save() {
  const res = await fetch('/api/events/medication', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value)
  })
  if (res.ok) { showModal.value = false; load(); emit('alert', '用药记录已录入') }
  else { const err = await res.json(); emit('alert', err.error || '录入失败', 'error') }
}

async function deleteRecord(id) {
  if (!confirm('确定删除该记录？')) return
  await fetch(`/api/events/medication/${id}`, { method: 'DELETE' })
  load(); emit('alert', '删除成功')
}

function today() { return new Date().toISOString().split('T')[0] }

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">用药记录管理</h2>
        <p class="page-desc">记录用药事件，支持原因描述便于后续疫病分析</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        录入用药
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">用药记录总数</div>
        <div class="stat-num warning">{{ records.length }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索耳标号或药品名称..." />
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>耳标号</th>
              <th>药品名称</th>
              <th>用药原因</th>
              <th>用药日期</th>
              <th>剂量</th>
              <th>执行人</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in filtered" :key="r.id">
              <td><code>{{ r.earTag }}</code></td>
              <td><span class="badge badge-warning">{{ r.drugName }}</span></td>
              <td class="cell-truncate">{{ r.reason || '—' }}</td>
              <td>{{ r.eventTime }}</td>
              <td>{{ r.dosage || '—' }}</td>
              <td>{{ r.operator || '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-del" @click="deleteRecord(r.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="filtered.length === 0">
              <td colspan="7"><div class="empty-state"><p>暂无用药记录</p></div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Modal :show="showModal" title="录入用药记录" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.earTag" placeholder="如 ET-001" />
        </div>
        <div class="form-group">
          <label>药品 <span style="color:var(--c-error)">*</span></label>
          <select v-model.number="form.drugId">
            <option :value="null">— 从标准库选择 —</option>
            <option v-for="d in drugs" :key="d.id" :value="d.id">{{ d.genericName }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>用药日期</label>
          <input v-model="form.eventTime" type="date" />
        </div>
        <div class="form-group">
          <label>剂量</label>
          <input v-model="form.dosage" placeholder="如 5mL" />
        </div>
        <div class="form-group">
          <label>执行人</label>
          <input v-model="form.operator" placeholder="操作员姓名" />
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>用药原因 <span style="color:var(--c-error)">*</span></label>
          <textarea v-model="form.reason" rows="2" placeholder="描述发病症状或预防目的"></textarea>
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showModal = false">取消</button>
        <button class="btn btn-primary" @click="save">保存</button>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.cell-truncate {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
