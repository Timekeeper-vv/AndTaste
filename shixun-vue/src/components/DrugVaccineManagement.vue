<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'
import type { DrugVaccine, AlertType } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface DrugForm { category: 'DRUG' | 'VACCINE'; genericName: string; specification: string; manufacturer: string; description: string }

const items = ref<DrugVaccine[]>([])
const search = ref<string>('')
const categoryFilter = ref<string>('')
const showModal = ref<boolean>(false)
const editingId = ref<number | null>(null)
const form = ref<DrugForm>({ category: 'VACCINE', genericName: '', specification: '', manufacturer: '', description: '' })

const page = ref(1)
const pageSize = 10
const filtered = computed(() =>
  items.value.filter(d =>
    (!categoryFilter.value || d.category === categoryFilter.value) &&
    (d.genericName?.includes(search.value) || d.manufacturer?.includes(search.value))
  )
)
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const paginated = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

async function load() {
  const res = await fetch('/api/drugs-vaccines')
  items.value = await res.json()
}

function openAdd() {
  editingId.value = null
  form.value = { category: 'VACCINE', genericName: '', specification: '', manufacturer: '', description: '' }
  showModal.value = true
}

function openEdit(d) {
  editingId.value = d.id
  form.value = { ...d }
  showModal.value = true
}

async function save() {
  const url = editingId.value ? `/api/drugs-vaccines/${editingId.value}` : '/api/drugs-vaccines'
  const method = editingId.value ? 'PUT' : 'POST'
  const res = await fetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value) })
  if (res.ok) { showModal.value = false; load(); emit('alert', '保存成功') }
  else emit('alert', '操作失败', 'error')
}

async function deleteItem(id) {
  if (!confirm('确定删除？')) return
  const res = await fetch(`/api/drugs-vaccines/${id}`, { method: 'DELETE' })
  if (res.ok) { load(); emit('alert', '删除成功') }
  else emit('alert', '删除失败', 'error')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">兽药疫苗标准库</h2>
        <p class="page-desc">建立统一投入品主数据，防止事件录入时随意输入</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新增品目
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">疫苗品目</div>
        <div class="stat-num info">{{ items.filter(d => d.category === 'VACCINE').length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">药品品目</div>
        <div class="stat-num warning">{{ items.filter(d => d.category === 'DRUG').length }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索名称或生产厂家..." />
        </div>
        <select v-model="categoryFilter" class="select-filter">
          <option value="">全部分类</option>
          <option value="VACCINE">疫苗</option>
          <option value="DRUG">药品</option>
        </select>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>分类</th>
              <th>通用名</th>
              <th>规格</th>
              <th>生产厂家</th>
              <th>用途说明</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in paginated" :key="d.id">
              <td>
                <span :class="['badge', d.category === 'VACCINE' ? 'badge-info' : 'badge-warning']">
                  {{ d.category === 'VACCINE' ? '疫苗' : '药品' }}
                </span>
              </td>
              <td><strong>{{ d.genericName }}</strong></td>
              <td>{{ d.specification || '—' }}</td>
              <td>{{ d.manufacturer || '—' }}</td>
              <td class="cell-desc">{{ d.description || '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-edit" @click="openEdit(d)">编辑</button>
                  <button class="btn-del" @click="deleteItem(d.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="paginated.length === 0">
              <td colspan="6"><div class="empty-state"><p>暂无数据</p></div></td>
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

    <Modal :show="showModal" :title="editingId ? '编辑品目' : '新增品目'" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>分类 <span style="color:var(--c-error)">*</span></label>
          <select v-model="form.category">
            <option value="VACCINE">疫苗</option>
            <option value="DRUG">药品</option>
          </select>
        </div>
        <div class="form-group">
          <label>通用名 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.genericName" placeholder="如 猪瘟活疫苗" />
        </div>
        <div class="form-group">
          <label>规格</label>
          <input v-model="form.specification" placeholder="如 1头份/瓶" />
        </div>
        <div class="form-group">
          <label>生产厂家</label>
          <input v-model="form.manufacturer" placeholder="生产企业名称" />
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>用途说明</label>
          <textarea v-model="form.description" rows="3" placeholder="说明该药品/疫苗的主要用途、适应症或注意事项"></textarea>
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
.cell-desc {
  max-width: 240px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text-2);
  font-size: 13px;
}
</style>
