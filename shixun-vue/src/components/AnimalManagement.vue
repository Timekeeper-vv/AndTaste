<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'
import type { Animal, Batch, Pen, AlertType } from '../types'
import { BREEDS } from '../constants'

const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface AnimalForm {
  earTag: string; gender: 'MALE' | 'FEMALE'; entryDate: string; breed: string
  batchId: number | null; currentPenId: number | null; birthWeight: number | null
}

const animals = ref<Animal[]>([])
const batches = ref<Batch[]>([])
const pens = ref<Pen[]>([])
const search = ref<string>('')
const statusFilter = ref<string>('')
const showModal = ref<boolean>(false)
const editingId = ref<number | null>(null)
const form = ref<AnimalForm>({ earTag: '', gender: 'MALE', entryDate: '', breed: '', batchId: null, currentPenId: null, birthWeight: null })

const page = ref(1)
const pageSize = 10
const filtered = computed(() =>
  animals.value.filter(a =>
    (!statusFilter.value || a.status === statusFilter.value) &&
    (a.earTag?.includes(search.value) ||
     a.breed?.includes(search.value) ||
     a.batchCode?.includes(search.value))
  )
)
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const paginated = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
function onSearch() { page.value = 1 }

async function load() {
  const [aRes, bRes, pRes] = await Promise.all([
    fetch('/api/animals'), fetch('/api/batches'), fetch('/api/pens/active')
  ])
  animals.value = await aRes.json()
  batches.value = await bRes.json()
  pens.value = await pRes.json()
}

async function openAdd() {
  editingId.value = null
  form.value = { earTag: '', gender: 'MALE', entryDate: today(), breed: '', batchId: null, currentPenId: null, birthWeight: null }
  showModal.value = true
  try {
    const res = await fetch('/api/animals/generate-ear-tag')
    const data = await res.json()
    form.value.earTag = data.earTag
  } catch {}
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

function today(): string { return new Date().toISOString().split('T')[0] }
function genderLabel(g: 'MALE' | 'FEMALE'): string { return g === 'MALE' ? '公' : '母' }
function genderClass(g: 'MALE' | 'FEMALE'): string { return g === 'MALE' ? 'badge-info' : 'badge-purple' }

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
      <div class="stat-card">
        <div class="stat-label">死亡</div>
        <div class="stat-num" style="color:#64748B">{{ animals.filter(a => a.status === 'DEAD').length }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索耳标号、品种或批次号..." @input="onSearch" />
        </div>
        <select v-model="statusFilter" class="select-filter">
          <option value="">全部状态</option>
          <option value="ACTIVE">在栏</option>
          <option value="SOLD">已出栏</option>
          <option value="DEAD">死亡</option>
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
            <tr v-for="a in paginated" :key="a.id">
              <td><code>{{ a.earTag }}</code></td>
              <td><span :class="['badge', genderClass(a.gender)]">{{ genderLabel(a.gender) }}</span></td>
              <td>{{ a.breed }}</td>
              <td>{{ a.entryDate }}</td>
              <td><code>{{ a.batchCode }}</code></td>
              <td>{{ a.currentPenName || '—' }}</td>
              <td>{{ a.birthWeight ? a.birthWeight + ' kg' : '—' }}</td>
              <td>
                <span :class="['status-badge', a.status === 'ACTIVE' ? 'active' : a.status === 'DEAD' ? 'dead' : 'sold']">
                  {{ a.status === 'ACTIVE' ? '在栏' : a.status === 'DEAD' ? '死亡' : '已出栏' }}
                </span>
              </td>
              <td>
                <div class="td-ops">
                  <button class="btn-edit" @click="openEdit(a)">编辑</button>
                  <button class="btn-del" @click="deleteAnimal(a.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="paginated.length === 0">
              <td colspan="9"><div class="empty-state"><p>暂无个体数据</p></div></td>
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

    <Modal :show="showModal" :title="editingId ? '编辑个体档案' : '新建个体档案'" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span> <small style="color:var(--c-text-3)">{{ editingId ? '(全局唯一)' : '(系统已自动分配，可修改)' }}</small></label>
          <input v-model="form.earTag" :disabled="!!editingId" :placeholder="editingId ? '' : '系统自动生成'" />
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
          <select v-model="form.breed">
            <option value="">— 请选择品种 —</option>
            <option v-for="b in BREEDS" :key="b" :value="b">{{ b }}</option>
          </select>
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

<style scoped>
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
.status-badge { padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 500; }
.status-badge.active { background: var(--c-success-bg); color: var(--c-success); }
.status-badge.sold   { background: var(--c-bg-2); color: var(--c-text-3); }
.status-badge.dead   { background: #F1F5F9; color: #64748B; }
</style>
