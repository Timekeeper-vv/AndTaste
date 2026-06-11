<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'
import * as XLSX from 'xlsx'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import type { Batch, Pen, AlertType } from '../types'
import { BREEDS, SOURCE_PROVINCES } from '../constants'

const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface BatchForm {
  batchCode: string; entryDate: string; breed: string
  source: string; initialPenId: number | null; notes: string
}

const batches = ref<Batch[]>([])
const pens = ref<Pen[]>([])
const search = ref<string>('')
const showModal = ref<boolean>(false)
const editingId = ref<number | null>(null)
const form = ref<BatchForm>({ batchCode: '', entryDate: '', breed: '', source: '', initialPenId: null, notes: '' })

// 分页
const page = ref(1)
const pageSize = 10
const filtered = computed(() =>
  batches.value.filter(b =>
    b.batchCode?.includes(search.value) ||
    b.breed?.includes(search.value) ||
    b.source?.includes(search.value)
  )
)
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const paginated = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
function onSearch() { page.value = 1 }

async function load() {
  const [bRes, pRes] = await Promise.all([fetch('/api/batches'), fetch('/api/pens/active')])
  batches.value = await bRes.json()
  pens.value = await pRes.json()
}

async function openAdd() {
  editingId.value = null
  form.value = { batchCode: '', entryDate: today(), breed: '', source: '', initialPenId: null, notes: '' }
  showModal.value = true
  try {
    const res = await fetch('/api/batches/generate-batch-code')
    const data = await res.json()
    form.value.batchCode = data.batchCode
  } catch {}
}

function openEdit(b) {
  editingId.value = b.id
  form.value = { batchCode: b.batchCode, entryDate: b.entryDate, breed: b.breed, source: b.source || '', initialPenId: b.initialPenId, notes: b.notes || '' }
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

function exportExcel() {
  const rows = filtered.value.map(b => ({
    批次号: b.batchCode, 入栏日期: b.entryDate, 品种: b.breed,
    来源地: b.source || '', 初始圈舍: b.initialPenName || '', 存栏数: b.animalCount || 0, 备注: b.notes || '',
  }))
  const ws = XLSX.utils.json_to_sheet(rows)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '批次台账')
  XLSX.writeFile(wb, `批次台账_${today()}.xlsx`)
}

function exportPDF() {
  const doc = new jsPDF({ orientation: 'landscape' })
  doc.setFont('helvetica')
  doc.text('养殖批次台账', 14, 14)
  autoTable(doc, {
    startY: 22,
    head: [['批次号', '入栏日期', '品种', '来源地', '初始圈舍', '存栏数', '备注']],
    body: filtered.value.map(b => [b.batchCode, b.entryDate, b.breed, b.source || '', b.initialPenName || '', b.animalCount || 0, b.notes || '']),
    styles: { fontSize: 9, cellPadding: 3 },
    headStyles: { fillColor: [13, 148, 136] },
  })
  doc.save(`批次台账_${today()}.pdf`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">养殖批次管理</h2>
        <p class="page-desc">按"同进同出"原则对牲畜进行逻辑分组管理</p>
      </div>
      <div class="header-actions">
        <button class="btn btn-secondary" @click="exportExcel">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
          导出 Excel
        </button>
        <button class="btn btn-secondary" @click="exportPDF">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          导出 PDF
        </button>
        <button class="btn btn-primary" @click="openAdd">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
          新建批次
        </button>
      </div>
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
          <input v-model="search" class="search-input" placeholder="搜索批次号、品种或来源地..." @input="onSearch" />
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
            <tr v-for="b in paginated" :key="b.id">
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
            <tr v-if="paginated.length === 0">
              <td colspan="8"><div class="empty-state"><p>暂无批次数据</p></div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <!-- 分页 -->
      <div class="pagination" v-if="totalPages > 1">
        <button class="pg-btn" :disabled="page === 1" @click="page--">‹</button>
        <span class="pg-info">第 {{ page }} / {{ totalPages }} 页 &nbsp;共 {{ filtered.length }} 条</span>
        <button class="pg-btn" :disabled="page === totalPages" @click="page++">›</button>
      </div>
    </div>

    <Modal :show="showModal" :title="editingId ? '编辑批次' : '新建批次'" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>批次号 <span style="color:var(--c-error)">*</span> <small style="color:var(--c-text-3)">{{ editingId ? '' : '(系统已自动分配，可修改)' }}</small></label>
          <input v-model="form.batchCode" :disabled="!!editingId" :placeholder="editingId ? '' : '系统自动生成'" />
        </div>
        <div class="form-group">
          <label>入栏日期 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.entryDate" type="date" />
        </div>
        <div class="form-group">
          <label>品种 <span style="color:var(--c-error)">*</span></label>
          <select v-model="form.breed">
            <option value="">— 请选择品种 —</option>
            <option v-for="b in BREEDS" :key="b" :value="b">{{ b }}</option>
          </select>
        </div>
        <div class="form-group">
          <label>来源地</label>
          <select v-model="form.source">
            <option value="">— 请选择省份 —</option>
            <option v-for="p in SOURCE_PROVINCES" :key="p" :value="p">{{ p }}</option>
          </select>
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
.header-actions { display: flex; align-items: center; gap: 8px; }
.cell-truncate {
  max-width: 140px; overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap; color: var(--c-text-2); font-size: 13px;
}
.pagination {
  display: flex; align-items: center; justify-content: flex-end;
  gap: 12px; padding: 12px 16px; border-top: 1px solid var(--c-border);
  font-size: 13px; color: var(--c-text-2);
}
.pg-btn {
  width: 28px; height: 28px; border: 1px solid var(--c-border);
  border-radius: var(--r); background: var(--c-surface); cursor: pointer;
  font-size: 16px; line-height: 1; color: var(--c-text);
  display: flex; align-items: center; justify-content: center;
}
.pg-btn:disabled { opacity: .4; cursor: not-allowed; }
.pg-btn:not(:disabled):hover { border-color: var(--c-primary); color: var(--c-primary); }
</style>
