<script setup>
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'

const emit = defineEmits(['alert'])

const records = ref([])
const vaccines = ref([])
const search = ref('')
const showModal = ref(false)
const form = ref({ earTag: '', vaccineId: null, eventTime: '', dosage: '', operator: '', notes: '' })

const filtered = computed(() =>
  records.value.filter(r => r.earTag?.includes(search.value) || r.vaccineName?.includes(search.value))
)

async function load() {
  const [rRes, vRes] = await Promise.all([fetch('/api/events/immunization'), fetch('/api/drugs-vaccines?category=VACCINE')])
  records.value = await rRes.json()
  vaccines.value = await vRes.json()
}

function openAdd() {
  form.value = { earTag: '', vaccineId: null, eventTime: today(), dosage: '', operator: '', notes: '' }
  showModal.value = true
}

async function save() {
  const res = await fetch('/api/events/immunization', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value)
  })
  if (res.ok) { showModal.value = false; load(); emit('alert', '免疫记录已录入') }
  else { const err = await res.json(); emit('alert', err.error || '录入失败', 'error') }
}

async function deleteRecord(id) {
  if (!confirm('确定删除该记录？')) return
  await fetch(`/api/events/immunization/${id}`, { method: 'DELETE' })
  load(); emit('alert', '删除成功')
}

function today() { return new Date().toISOString().split('T')[0] }

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">免疫记录管理</h2>
        <p class="page-desc">录入个体免疫事件，自动挂载至个体时间线</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        录入免疫
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">免疫记录总数</div>
        <div class="stat-num info">{{ records.length }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索耳标号或疫苗名称..." />
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>耳标号</th>
              <th>疫苗名称</th>
              <th>免疫日期</th>
              <th>剂量</th>
              <th>执行人</th>
              <th>备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in filtered" :key="r.id">
              <td><code>{{ r.earTag }}</code></td>
              <td><span class="badge badge-info">{{ r.vaccineName }}</span></td>
              <td>{{ r.eventTime }}</td>
              <td>{{ r.dosage || '—' }}</td>
              <td>{{ r.operator || '—' }}</td>
              <td class="cell-truncate">{{ r.notes || '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-del" @click="deleteRecord(r.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="filtered.length === 0">
              <td colspan="7"><div class="empty-state"><p>暂无免疫记录</p></div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Modal :show="showModal" title="录入免疫记录" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.earTag" placeholder="如 ET-001" />
        </div>
        <div class="form-group">
          <label>疫苗 <span style="color:var(--c-error)">*</span></label>
          <select v-model.number="form.vaccineId">
            <option :value="null">— 从标准库选择 —</option>
            <option v-for="v in vaccines" :key="v.id" :value="v.id">{{ v.genericName }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>免疫日期</label>
          <input v-model="form.eventTime" type="date" />
        </div>
        <div class="form-group">
          <label>剂量</label>
          <input v-model="form.dosage" placeholder="如 1头份" />
        </div>
        <div class="form-group">
          <label>执行人</label>
          <input v-model="form.operator" placeholder="操作员姓名" />
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>备注</label>
          <textarea v-model="form.notes" rows="2"></textarea>
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
  color: var(--c-text-2);
  font-size: 13px;
}
</style>
