<script setup>
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'

const emit = defineEmits(['alert'])

const batches = ref([])
const pens = ref([])
const search = ref('')
const showModal = ref(false)
const editingId = ref(null)
const form = ref({ batchCode: '', entryDate: '', breed: '', source: '', initialPenId: null, notes: '' })

const filtered = computed(() =>
  batches.value.filter(b =>
    b.batchCode?.includes(search.value) ||
    b.breed?.includes(search.value) ||
    b.source?.includes(search.value)
  )
)

async function load() {
  const [bRes, pRes] = await Promise.all([fetch('/api/batches'), fetch('/api/pens/active')])
  batches.value = await bRes.json()
  pens.value = await pRes.json()
}

function openAdd() {
  editingId.value = null
  form.value = { batchCode: '', entryDate: today(), breed: '', source: '', initialPenId: null, notes: '' }
  showModal.value = true
}

function openEdit(b) {
  editingId.value = b.id
  form.value = { batchCode: b.batchCode, entryDate: b.entryDate, breed: b.breed, source: b.source, initialPenId: b.initialPenId, notes: b.notes }
  showModal.value = true
}

async function save() {
  const url = editingId.value ? `/api/batches/${editingId.value}` : '/api/batches'
  const method = editingId.value ? 'PUT' : 'POST'
  const res = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value) })
  if (res.ok) { showModal.value = false; load(); emit('alert', '保存成功') }
  else { const err = await res.json(); emit('alert', err.error || '操作失败', 'error') }
}

async function deleteBatch(id) {
  if (!confirm('确定删除该批次？')) return
  const res = await fetch(`/api/batches/${id}`, { method: 'DELETE' })
  if (res.ok) { load(); emit('alert', '删除成功') }
  else emit('alert', '删除失败', 'error')
}

function today() { return new Date().toISOString().split('T')[0] }

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">养殖批次管理</h2>
        <p class="page-desc">按"同进同出"原则对牲畜进行逻辑分组管理</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建批次
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">批次总数</div>
        <div class="stat-num primary">{{ batches.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总存栏数</div>
        <div class="stat-num">{{ batches.reduce((s, b) => s + (b.animalCount || 0), 0) }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索批次号、品种或来源地..." />
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>批次号</th>
              <th>入栏日期</th>
              <th>品种</th>
              <th>来源地</th>
              <th>初始圈舍</th>
              <th>存栏数</th>
              <th>备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="b in filtered" :key="b.id">
              <td><code>{{ b.batchCode }}</code></td>
              <td>{{ b.entryDate }}</td>
              <td>{{ b.breed }}</td>
              <td>{{ b.source || '—' }}</td>
              <td>{{ b.initialPenName || '—' }}</td>
              <td><span class="badge badge-primary">{{ b.animalCount || 0 }} 头</span></td>
              <td class="cell-truncate">{{ b.notes || '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-edit" @click="openEdit(b)">编辑</button>
                  <button class="btn-del" @click="deleteBatch(b.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="filtered.length === 0">
              <td colspan="8"><div class="empty-state"><p>暂无批次数据</p></div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Modal :show="showModal" :title="editingId ? '编辑批次' : '新建批次'" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>批次号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.batchCode" :disabled="!!editingId" placeholder="如 BATCH-2024-002" />
        </div>
        <div class="form-group">
          <label>入栏日期 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.entryDate" type="date" />
        </div>
        <div class="form-group">
          <label>品种 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.breed" placeholder="如 杜洛克猪" />
        </div>
        <div class="form-group">
          <label>来源地</label>
          <input v-model="form.source" placeholder="省市区" />
        </div>
        <div class="form-group">
          <label>初始圈舍</label>
          <select v-model.number="form.initialPenId">
            <option :value="null">— 请选择 —</option>
            <option v-for="p in pens" :key="p.id" :value="p.id">{{ p.penName }}</option>
          </select>
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>备注</label>
          <textarea v-model="form.notes" rows="2" placeholder="可选备注信息"></textarea>
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
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text-2);
  font-size: 13px;
}
</style>
