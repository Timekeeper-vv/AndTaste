<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import Modal from './Modal.vue'
import * as XLSX from 'xlsx'
import jsPDF from 'jspdf'
import autoTable from 'jspdf-autotable'
import type { ImmunizationRecord, DrugVaccine, User, AlertType } from '../types'

const props = defineProps<{ currentUser: User | null }>()
const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface ImmunizationForm {
  earTag: string; vaccineId: number | null; eventTime: string
  dosage: string; operator: string; notes: string
}

const records = ref<ImmunizationRecord[]>([])
const vaccines = ref<DrugVaccine[]>([])
const search = ref<string>('')
const showModal = ref<boolean>(false)
const form = ref<ImmunizationForm>({ earTag: '', vaccineId: null, eventTime: '', dosage: '', operator: '', notes: '' })

// 分页
const page = ref(1)
const pageSize = 10
const filtered = computed(() =>
  records.value.filter(r => r.earTag?.includes(search.value) || r.vaccineName?.includes(search.value))
)
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const paginated = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))
function onSearch() { page.value = 1 }

async function load() {
  const [rRes, vRes] = await Promise.all([fetch('/api/events/immunization'), fetch('/api/drugs-vaccines?category=VACCINE')])
  records.value = await rRes.json()
  vaccines.value = await vRes.json()
}

function openAdd() {
  form.value = {
    earTag: '', vaccineId: null, eventTime: today(), dosage: '',
    operator: props.currentUser?.username || '', notes: ''
  }
  showModal.value = true
}

// 当登录用户变化时同步操作员字段
watch(() => props.currentUser, (u) => {
  if (showModal.value) form.value.operator = u?.username || ''
})

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

function exportExcel() {
  const rows = filtered.value.map(r => ({
    耳标号: r.earTag, 疫苗名称: r.vaccineName, 免疫日期: r.eventTime,
    剂量: r.dosage || '', 执行人: r.operator || '', 备注: r.notes || '',
  }))
  const ws = XLSX.utils.json_to_sheet(rows)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '免疫记录')
  XLSX.writeFile(wb, `免疫记录_${today()}.xlsx`)
}

function exportPDF() {
  const doc = new jsPDF({ orientation: 'landscape' })
  doc.setFont('helvetica')
  doc.text('免疫记录台账', 14, 14)
  autoTable(doc, {
    startY: 22,
    head: [['耳标号', '疫苗名称', '免疫日期', '剂量', '执行人', '备注']],
    body: filtered.value.map(r => [r.earTag, r.vaccineName, r.eventTime, r.dosage || '', r.operator || '', r.notes || '']),
    styles: { fontSize: 9, cellPadding: 3 },
    headStyles: { fillColor: [13, 148, 136] },
  })
  doc.save(`免疫记录_${today()}.pdf`)
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">免疫记录管理</h2>
        <p class="page-desc">录入个体免疫事件，自动挂载至个体时间线</p>
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
          录入免疫
        </button>
      </div>
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
          <input v-model="search" class="search-input" placeholder="搜索耳标号或疫苗名称..." @input="onSearch" />
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
            <tr v-for="r in paginated" :key="r.id">
              <td><code>{{ r.earTag }}</code></td>
              <td><span class="badge badge-info">{{ r.vaccineName }}</span></td>
              <td>{{ r.eventTime }}</td>
              <td>{{ r.dosage || '—' }}</td>
              <td>
                <span class="operator-tag">
                  <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/></svg>
                  {{ r.operator || '—' }}
                </span>
              </td>
              <td class="cell-truncate">{{ r.notes || '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-del" @click="deleteRecord(r.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="paginated.length === 0">
              <td colspan="7"><div class="empty-state"><p>暂无免疫记录</p></div></td>
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

    <Modal :show="showModal" title="录入免疫记录" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.earTag" placeholder="如 ET202606100010" />
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
          <div class="operator-display">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="8" r="4"/><path d="M20 21a8 8 0 1 0-16 0"/></svg>
            <span>{{ form.operator || '未登录' }}</span>
            <span class="op-badge">当前账户</span>
          </div>
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
.header-actions { display: flex; align-items: center; gap: 8px; }
.cell-truncate {
  max-width: 150px; overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap; color: var(--c-text-2); font-size: 13px;
}
.operator-tag {
  display: inline-flex; align-items: center; gap: 4px;
  font-size: 12px; color: var(--c-text-2);
}
.operator-display {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px; background: var(--c-bg-2);
  border: 1px solid var(--c-border); border-radius: var(--r);
  font-size: 13px; color: var(--c-text); font-weight: 500;
}
.op-badge {
  margin-left: auto; font-size: 11px; padding: 1px 6px;
  background: var(--c-primary-light); color: var(--c-primary-dark);
  border-radius: 4px;
}
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
</style>
