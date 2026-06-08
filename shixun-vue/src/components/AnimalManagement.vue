<script setup>
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'

const emit = defineEmits(['alert'])

const animals = ref([])
const batches = ref([])
const pens = ref([])
const search = ref('')
const statusFilter = ref('')
const showModal = ref(false)
const editingId = ref(null)
const form = ref({ earTag: '', gender: 'MALE', entryDate: '', breed: '', batchId: null, currentPenId: null, birthWeight: null })

const filtered = computed(() =>
  animals.value.filter(a =>
    (!statusFilter.value || a.status === statusFilter.value) &&
    (a.earTag?.includes(search.value) ||
     a.breed?.includes(search.value) ||
     a.batchCode?.includes(search.value))
  )
)

async function load() {
  const [aRes, bRes, pRes] = await Promise.all([
    fetch('/api/animals'), fetch('/api/batches'), fetch('/api/pens/active')
  ])
  animals.value = await aRes.json()
  batches.value = await bRes.json()
  pens.value = await pRes.json()
}

function openAdd() {
  editingId.value = null
  form.value = { earTag: '', gender: 'MALE', entryDate: today(), breed: '', batchId: null, currentPenId: null, birthWeight: null }
  showModal.value = true
}

function openEdit(a) {
  editingId.value = a.id
  form.value = { earTag: a.earTag, gender: a.gender, entryDate: a.entryDate, breed: a.breed, batchId: a.batchId, currentPenId: a.currentPenId, birthWeight: a.birthWeight }
  showModal.value = true
}

async function save() {
  const url = editingId.value ? `/api/animals/${editingId.value}` : '/api/animals'
  const method = editingId.value ? 'PUT' : 'POST'
  const res = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value) })
  if (res.ok) { showModal.value = false; load(); emit('alert', editingId.value ? '更新成功' : '建档成功') }
  else { const err = await res.json(); emit('alert', err.error || '操作失败', 'error') }
}

async function deleteAnimal(id) {
  if (!confirm('确定删除该个体档案？')) return
  const res = await fetch(`/api/animals/${id}`, { method: 'DELETE' })
  if (res.ok) { load(); emit('alert', '删除成功') }
  else emit('alert', '删除失败', 'error')
}

function today() { return new Date().toISOString().split('T')[0] }
function genderLabel(g) { return g === 'MALE' ? '公' : '母' }
function genderClass(g) { return g === 'MALE' ? 'badge-info' : 'badge-purple' }

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">个体数字档案</h2>
        <p class="page-desc">以耳标号为唯一标识，构建牲畜终身数字身份</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新建档案
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">总个体数</div>
        <div class="stat-num">{{ animals.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">在栏中</div>
        <div class="stat-num success">{{ animals.filter(a => a.status === 'ACTIVE').length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已出栏</div>
        <div class="stat-num" style="color:var(--c-text-2)">{{ animals.filter(a => a.status === 'SOLD').length }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索耳标号、品种或批次号..." />
        </div>
        <select v-model="statusFilter" class="select-filter">
          <option value="">全部状态</option>
          <option value="ACTIVE">在栏</option>
          <option value="SOLD">已出栏</option>
        </select>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>耳标号</th>
              <th>性别</th>
              <th>品种</th>
              <th>入栏日期</th>
              <th>所属批次</th>
              <th>当前圈舍</th>
              <th>出生重</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="a in filtered" :key="a.id">
              <td><code>{{ a.earTag }}</code></td>
              <td><span :class="['badge', genderClass(a.gender)]">{{ genderLabel(a.gender) }}</span></td>
              <td>{{ a.breed }}</td>
              <td>{{ a.entryDate }}</td>
              <td><code>{{ a.batchCode }}</code></td>
              <td>{{ a.currentPenName || '—' }}</td>
              <td>{{ a.birthWeight ? a.birthWeight + ' kg' : '—' }}</td>
              <td>
                <span :class="['status-badge', a.status === 'ACTIVE' ? 'active' : 'sold']">
                  {{ a.status === 'ACTIVE' ? '在栏' : '已出栏' }}
                </span>
              </td>
              <td>
                <div class="td-ops">
                  <button class="btn-edit" @click="openEdit(a)">编辑</button>
                  <button class="btn-del" @click="deleteAnimal(a.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="filtered.length === 0">
              <td colspan="9"><div class="empty-state"><p>暂无个体数据</p></div></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <Modal :show="showModal" :title="editingId ? '编辑个体档案' : '新建个体档案'" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span> <small style="color:var(--c-text-3)">(全局唯一)</small></label>
          <input v-model="form.earTag" :disabled="!!editingId" placeholder="如 ET-004" />
        </div>
        <div class="form-group">
          <label>性别 <span style="color:var(--c-error)">*</span></label>
          <select v-model="form.gender">
            <option value="MALE">公</option>
            <option value="FEMALE">母</option>
          </select>
        </div>
        <div class="form-group">
          <label>品种 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.breed" placeholder="如 杜洛克猪" />
        </div>
        <div class="form-group">
          <label>入栏日期</label>
          <input v-model="form.entryDate" type="date" />
        </div>
        <div class="form-group">
          <label>所属批次 <span style="color:var(--c-error)">*</span></label>
          <select v-model.number="form.batchId">
            <option :value="null">— 请选择 —</option>
            <option v-for="b in batches" :key="b.id" :value="b.id">{{ b.batchCode }} - {{ b.breed }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>当前圈舍</label>
          <select v-model.number="form.currentPenId">
            <option :value="null">— 请选择 —</option>
            <option v-for="p in pens" :key="p.id" :value="p.id">{{ p.penName }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>出生重量（kg）</label>
          <input v-model.number="form.birthWeight" type="number" step="0.1" placeholder="0.0" />
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showModal = false">取消</button>
        <button class="btn btn-primary" @click="save">保存</button>
      </div>
    </Modal>
  </div>
</template>
