<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'
import type { SlaughterRecord, AlertType } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface SlaughterForm {
  earTag: string; eventTime: string; type: 'SALE' | 'SLAUGHTER' | 'TRANSFER'
  destination: string; weight: number | null; price: number | null
}

const records = ref<SlaughterRecord[]>([])
const search = ref<string>('')
const showModal = ref<boolean>(false)
const form = ref<SlaughterForm>({ earTag: '', eventTime: '', type: 'SALE', destination: '', weight: null, price: null })

const page = ref(1)
const pageSize = 10
const filtered = computed(() =>
  records.value.filter(r => r.earTag?.includes(search.value) || r.destination?.includes(search.value))
)
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const paginated = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

async function load() {
  const res = await fetch('/api/events/slaughter')
  records.value = await res.json()
}

function openAdd() {
  form.value = { earTag: '', eventTime: today(), type: 'SALE', destination: '', weight: null, price: null }
  showModal.value = true
}

async function save() {
  const res = await fetch('/api/events/slaughter', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(form.value)
  })
  if (res.ok) { showModal.value = false; load(); emit('alert', '出栏登记完成，个体状态已更新') }
  else { const err = await res.json(); emit('alert', err.error || '操作失败', 'error') }
}

async function deleteRecord(id) {
  if (!confirm('确定删除该出栏记录？')) return
  await fetch(`/api/events/slaughter/${id}`, { method: 'DELETE' })
  load(); emit('alert', '删除成功')
}

const typeLabel: Record<string, string> = { SALE: '销售', SLAUGHTER: '屠宰', TRANSFER: '转移' }
const typeClass: Record<string, string>  = { SALE: 'badge-success', SLAUGHTER: 'badge-error', TRANSFER: 'badge-info' }

function today() { return new Date().toISOString().split('T')[0] }

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">出栏管理</h2>
        <p class="page-desc">登记出栏后个体状态自动变更为"已出栏"</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
        登记出栏
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">销售出栏</div>
        <div class="stat-num success">{{ records.filter(r => r.type === 'SALE').length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">屠宰出栏</div>
        <div class="stat-num error">{{ records.filter(r => r.type === 'SLAUGHTER').length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">转移出栏</div>
        <div class="stat-num info">{{ records.filter(r => r.type === 'TRANSFER').length }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索耳标号或目的地..." />
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>耳标号</th>
              <th>出栏类型</th>
              <th>出栏日期</th>
              <th>目的地</th>
              <th>重量（kg）</th>
              <th>价格（元）</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in paginated" :key="r.id">
              <td><code>{{ r.earTag }}</code></td>
              <td><span :class="['badge', typeClass[r.type]]">{{ typeLabel[r.type] }}</span></td>
              <td>{{ r.eventTime }}</td>
              <td>{{ r.destination || '—' }}</td>
              <td>{{ r.weight ?? '—' }}</td>
              <td>{{ r.price != null ? '¥' + r.price : '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-del" @click="deleteRecord(r.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="paginated.length === 0">
              <td colspan="7"><div class="empty-state"><p>暂无出栏记录</p></div></td>
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

    <Modal :show="showModal" title="登记出栏" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.earTag" placeholder="输入待出栏个体耳标号" />
        </div>
        <div class="form-group">
          <label>出栏类型 <span style="color:var(--c-error)">*</span></label>
          <select v-model="form.type">
            <option value="SALE">销售</option>
            <option value="SLAUGHTER">屠宰</option>
            <option value="TRANSFER">转移</option>
          </select>
        </div>
        <div class="form-group">
          <label>出栏日期</label>
          <input v-model="form.eventTime" type="date" />
        </div>
        <div class="form-group">
          <label>目的地</label>
          <input v-model="form.destination" placeholder="买家/屠宰场名称" />
        </div>
        <div class="form-group">
          <label>重量（kg）</label>
          <input v-model.number="form.weight" type="number" step="0.1" placeholder="0.0" />
        </div>
        <div class="form-group">
          <label>价格（元）</label>
          <input v-model.number="form.price" type="number" step="0.01" placeholder="0.00" />
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showModal = false">取消</button>
        <button class="btn btn-primary" @click="save">确认出栏</button>
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
</style>
