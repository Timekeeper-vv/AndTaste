<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'
import type { DeathRecord, User, AlertType } from '../types'

const props = defineProps<{ currentUser: User | null }>()
const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface DeathForm {
  earTag: string
  eventTime: string
  cause: string
  operator: string
  notes: string
}

const records = ref<DeathRecord[]>([])
const search = ref<string>('')
const showModal = ref<boolean>(false)
const form = ref<DeathForm>({ earTag: '', eventTime: '', cause: '', operator: '', notes: '' })

const page = ref(1)
const pageSize = 10
const filtered = computed(() =>
  records.value.filter(r => r.earTag?.includes(search.value) || (r.cause || '').includes(search.value))
)
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize)))
const paginated = computed(() => filtered.value.slice((page.value - 1) * pageSize, page.value * pageSize))

async function load() {
  const res = await fetch('/api/events/death')
  records.value = await res.json()
}

function openAdd() {
  form.value = { earTag: '', eventTime: today(), cause: '', operator: props.currentUser?.username || '', notes: '' }
  showModal.value = true
}

async function save() {
  const res = await fetch('/api/events/death', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(form.value),
  })
  if (res.ok) {
    showModal.value = false
    load()
    emit('alert', '死亡登记完成，个体状态已更新为"死亡"')
  } else {
    const err = await res.json()
    emit('alert', err.error || '操作失败', 'error')
  }
}

async function deleteRecord(id: number) {
  if (!confirm('确定删除该死亡记录？')) return
  await fetch(`/api/events/death/${id}`, { method: 'DELETE' })
  load()
  emit('alert', '删除成功')
}

function today() { return new Date().toISOString().split('T')[0] }

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">死亡管理</h2>
        <p class="page-desc">登记死亡后个体状态自动变更为"死亡"，统计数据同步更新</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        登记死亡
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">累计死亡登记</div>
        <div class="stat-num dead">{{ records.length }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="search" class="search-input" placeholder="搜索耳标号或死亡原因..." />
        </div>
      </div>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>耳标号</th>
              <th>死亡日期</th>
              <th>死亡原因</th>
              <th>记录人</th>
              <th>备注</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in paginated" :key="r.id">
              <td><code>{{ r.earTag }}</code></td>
              <td>{{ r.eventTime }}</td>
              <td>{{ r.cause || '—' }}</td>
              <td>{{ r.operator || '—' }}</td>
              <td>{{ r.notes || '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-del" @click="deleteRecord(r.id)">删除</button>
                </div>
              </td>
            </tr>
            <tr v-if="paginated.length === 0">
              <td colspan="6"><div class="empty-state"><p>暂无死亡记录</p></div></td>
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

    <Modal :show="showModal" title="登记死亡" @close="showModal = false">
      <div class="form-grid">
        <div class="form-group">
          <label>耳标号 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.earTag" placeholder="输入待登记死亡个体耳标号" />
        </div>
        <div class="form-group">
          <label>死亡日期</label>
          <input v-model="form.eventTime" type="date" />
        </div>
        <div class="form-group">
          <label>死亡原因</label>
          <input v-model="form.cause" placeholder="如：疾病、意外等" />
        </div>
        <div class="form-group">
          <label>记录人</label>
          <div class="operator-display">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            {{ form.operator || '—' }}
          </div>
        </div>
        <div class="form-group" style="grid-column: span 2">
          <label>备注</label>
          <input v-model="form.notes" placeholder="可填写详细说明" />
        </div>
      </div>
      <div class="modal-footer">
        <button class="btn btn-secondary" @click="showModal = false">取消</button>
        <button class="btn btn-primary" @click="save">确认登记</button>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.stat-num.dead { color: #64748B; }

.operator-display {
  display: flex; align-items: center; gap: 6px;
  height: 36px; padding: 0 12px;
  background: var(--c-bg); border: 1px solid var(--c-border);
  border-radius: var(--r); font-size: 13px; color: var(--c-text-2);
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
