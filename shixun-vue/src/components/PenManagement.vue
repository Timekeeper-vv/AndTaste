<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'
import type { Pen, UserRecord, AlertType } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface PenForm { penCode: string; penName: string; capacity: number; responsiblePerson: string; status: number }

const pens = ref<Pen[]>([])
const users = ref<UserRecord[]>([])
const employees = computed(() => users.value.filter(u => u.role === 'feeder'))
const search = ref<string>('')
const showModal = ref<boolean>(false)
const editingId = ref<number | null>(null)
const form = ref<PenForm>({ penCode: '', penName: '', capacity: 50, responsiblePerson: '', status: 1 })

const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

async function load() {
  const p = new URLSearchParams({ page: String(page.value), size: String(pageSize.value) })
  if (search.value) p.set('search', search.value)
  const [pRes, uRes] = await Promise.all([fetch(`/api/pens?${p}`), fetch('/api/users', { headers: { 'X-Current-Role': 'admin', 'X-Current-User': 'admin' } })])
  const pageData = await pRes.json()
  pens.value = pageData.content
  total.value = pageData.total
  users.value = await uRes.json()
}

function onSearch() { page.value = 1; load() }

function openAdd() {
  editingId.value = null
  form.value = { penCode: '', penName: '', capacity: 50, responsiblePerson: '', status: 1 }
  showModal.value = true
}

function openEdit(p) {
  editingId.value = p.id
  form.value = { ...p }
  showModal.value = true
}

async function save() {
  if (form.value.capacity < 1) { emit('alert', '容量必须大于 0', 'error'); return }
  if (editingId.value) {
    const pen = pens.value.find(p => p.id === editingId.value)
    if (pen && form.value.capacity < (pen as any).currentCount) {
      emit('alert', `容量不能小于当前存栏数（${(pen as any).currentCount} 头）`, 'error'); return
    }
  }
  const url = editingId.value ? `/api/pens/${editingId.value}` : '/api/pens'
  const method = editingId.value ? 'PUT' : 'POST'
  const res = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value) })
  if (res.ok) {
    showModal.value = false; load()
    emit('alert', editingId.value ? '更新成功' : '创建成功')
  } else {
    const err = await res.json()
    emit('alert', err.error || '操作失败', 'error')
  }
}

async function deletePen(id) {
  if (!confirm('确定删除该圈舍？')) return
  const res = await fetch(`/api/pens/${id}`, { method: 'DELETE' })
  if (res.ok) { load(); emit('alert', '删除成功') }
  else emit('alert', '删除失败', 'error')
}

function capacityClass(p: Pen): string {
  const ratio = p.currentCount / p.capacity
  if (ratio >= 1) return 'badge-error'
  if (ratio >= 0.8) return 'badge-warning'
  return 'badge-success'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">圈舍资产管理</h2>
        <p class="page-desc">对养殖场物理空间进行数字化建模与状态管理</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新增圈舍
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">圈舍总数</div>
        <div class="stat-num">{{ total }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本页已启用</div>
        <div class="stat-num success">{{ pens.filter(p => p.status === 1).length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">本页存栏数</div>
        <div class="stat-num primary">{{ pens.reduce((s, p) => s + (p as any).currentCount, 0) }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索编号、名称或责任人..." @input="onSearch" />
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>圈舍编号</th>
              <th>圈舍名称</th>
              <th>设计容量</th>
              <th>当前存栏</th>
              <th>责任人</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in pens" :key="p.id">
              <td><code>{{ p.penCode }}</code></td>
              <td>{{ p.penName }}</td>
              <td>{{ p.capacity }} 头</td>
              <td><span :class="['badge', capacityClass(p)]">{{ p.currentCount }} / {{ p.capacity }}</span></td>
              <td>{{ p.responsiblePerson || '—' }}</td>
              <td><span :class="['status-badge', p.status === 1 ? 'active' : 'inactive']">{{ p.status === 1 ? '启用' : '停用' }}</span></td>
              <td>
                <div class="td-ops">
                  <button class="btn-edit" @click="openEdit(p)">编辑</button>
                  <button class="btn-del" @click="deletePen(p.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="pens.length === 0">
              <td colspan="7">
                <div class="empty-state"><p>暂无圈舍数据</p></div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span class="pg-total">共 {{ total }} 条</span>
        <button class="pg-btn" :disabled="page === 1" @click="page--; load()">‹</button>
        <span class="pg-info">第 {{ page }} / {{ totalPages }} 页</span>
        <button class="pg-btn" :disabled="page === totalPages" @click="page++; load()">›</button>
        <select v-model.number="pageSize" class="pg-size" @change="page = 1; load()">
          <option :value="5">5条/页</option>
          <option :value="10">10条/页</option>
          <option :value="20">20条/页</option>
          <option :value="50">50条/页</option>
        </select>
      </div>
    </div>

    <Modal :show="showModal" :title="editingId ? '编辑圈舍' : '新增圈舍'" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>圈舍编号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.penCode" :disabled="!!editingId" placeholder="如 PEN-D" />
        </div>
        <div class="form-group">
          <label>圈舍名称 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.penName" placeholder="如 D号圈舍" />
        </div>
        <div class="form-group">
          <label>设计容量（头）</label>
          <input v-model.number="form.capacity" type="number" min="1" step="1" />
        </div>
        <div class="form-group">
          <label>责任人（员工）</label>
          <select v-model="form.responsiblePerson">
            <option value="">— 请选择员工 —</option>
            <option v-for="u in employees" :key="u.id" :value="u.username">{{ u.username }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>状态</label>
          <select v-model.number="form.status">
            <option :value="1">启用</option>
            <option :value="0">停用</option>
          </select>
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
.pg-total { color: var(--c-text-3); margin-right: auto; }
.pg-btn {
  width: 28px; height: 28px; border: 1px solid var(--c-border);
  border-radius: var(--r); background: var(--c-surface); cursor: pointer;
  font-size: 16px; display: flex; align-items: center; justify-content: center; color: var(--c-text);
}
.pg-btn:disabled { opacity: .4; cursor: not-allowed; }
.pg-btn:not(:disabled):hover { border-color: var(--c-primary); color: var(--c-primary); }
.pg-size {
  height: 28px; padding: 0 6px; border: 1px solid var(--c-border);
  border-radius: var(--r); font-size: 12px; color: var(--c-text);
  background: var(--c-surface); cursor: pointer;
}
</style>
