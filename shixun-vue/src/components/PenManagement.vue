<script setup>
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'

const emit = defineEmits(['alert'])

const pens = ref([])
const search = ref('')
const showModal = ref(false)
const editingId = ref(null)
const form = ref({ penCode: '', penName: '', capacity: 50, responsiblePerson: '', status: 1 })

const filtered = computed(() =>
  pens.value.filter(p =>
    p.penCode?.includes(search.value) ||
    p.penName?.includes(search.value) ||
    p.responsiblePerson?.includes(search.value)
  )
)

async function load() {
  const res = await fetch('/api/pens')
  pens.value = await res.json()
}

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

function capacityClass(p) {
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
        <div class="stat-num">{{ pens.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">已启用</div>
        <div class="stat-num success">{{ pens.filter(p => p.status === 1).length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总存栏数</div>
        <div class="stat-num primary">{{ pens.reduce((s, p) => s + p.currentCount, 0) }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索编号、名称或责任人..." />
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
            <tr v-for="p in filtered" :key="p.id">
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
            <tr v-if="filtered.length === 0">
              <td colspan="7">
                <div class="empty-state"><p>暂无圈舍数据</p></div>
              </td>
            </tr>
          </tbody>
        </table>
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
          <input v-model.number="form.capacity" type="number" min="1" />
        </div>
        <div class="form-group">
          <label>责任人</label>
          <input v-model="form.responsiblePerson" placeholder="管理员姓名" />
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
