<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import Modal from './Modal.vue'

const emit = defineEmits(['alert'])

const users = ref([])
const loading = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const showModal = ref(false)
const isEdit = ref(false)
const form = ref({ id: '', username: '', age: '', email: '', phone: '', password: '' })
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

onMounted(loadUsers)

watch(searchQuery, () => { currentPage.value = 1 })
watch(pageSize, () => { currentPage.value = 1 })

async function loadUsers() {
  loading.value = true
  users.value = []
  try {
    const res = await fetch('/api/users')
    users.value = await res.json()
  } catch {
    emit('alert', '加载用户失败', 'error')
  } finally {
    loading.value = false
  }
}

const filteredUsers = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return users.value
  return users.value.filter(u =>
    (u.username ?? '').toLowerCase().includes(q) ||
    (u.email ?? '').toLowerCase().includes(q) ||
    (u.phone ?? '').includes(q)
  )
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredUsers.value.length / pageSize.value)))

const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredUsers.value.slice(start, start + pageSize.value)
})

const pageNumbers = computed(() => {
  const total = totalPages.value
  const cur = currentPage.value
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  if (cur <= 4) return [1, 2, 3, 4, 5, '...', total]
  if (cur >= total - 3) return [1, '...', total - 4, total - 3, total - 2, total - 1, total]
  return [1, '...', cur - 1, cur, cur + 1, '...', total]
})

function goToPage(p) {
  if (typeof p !== 'number') return
  if (p < 1 || p > totalPages.value) return
  currentPage.value = p
}

function openAdd() {
  form.value = { id: '', username: '', age: '', email: '', phone: '', password: '' }
  isEdit.value = false
  showModal.value = true
}

async function openEdit(id) {
  if (editingId.value !== null) return
  editingId.value = id
  try {
    const res = await fetch(`/api/users/${id}`)
    if (!res.ok) { emit('alert', '获取用户信息失败', 'error'); return }
    const u = await res.json()
    form.value = { id: u.id, username: u.username ?? '', age: u.age ?? '', email: u.email ?? '', phone: u.phone ?? '', password: '' }
    isEdit.value = true
    showModal.value = true
  } catch {
    emit('alert', '获取用户信息失败', 'error')
  } finally {
    editingId.value = null
  }
}

async function submitForm() {
  if (submitting.value) return
  if (!isEdit.value && !form.value.password) {
    emit('alert', '新增用户密码不能为空', 'error')
    return
  }
  const payload = {
    username: form.value.username,
    age: Number(form.value.age),
    email: form.value.email,
    phone: form.value.phone || undefined,
  }
  if (form.value.password) payload.password = form.value.password
  submitting.value = true
  try {
    const res = await fetch(isEdit.value ? `/api/users/${form.value.id}` : '/api/users', {
      method: isEdit.value ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (!res.ok) {
      const text = await res.text()
      emit('alert', `操作失败：${extractMsg(text)}`, 'error')
      return
    }
    emit('alert', isEdit.value ? '用户修改成功' : '用户新增成功')
    showModal.value = false
    loadUsers()
  } catch {
    emit('alert', '网络错误', 'error')
  } finally {
    submitting.value = false
  }
}

async function deleteUser(id, name) {
  if (!confirm(`确定要删除用户「${name}」（ID: ${id}）吗？此操作不可恢复。`)) return
  try {
    const res = await fetch(`/api/users/${id}`, { method: 'DELETE' })
    if (!res.ok && res.status !== 204) { emit('alert', '删除失败', 'error'); return }
    emit('alert', '用户删除成功')
    loadUsers()
  } catch {
    emit('alert', '网络错误', 'error')
  }
}

function extractMsg(text) {
  try { const o = JSON.parse(text); return o.detail || o.message || text } catch { return text }
}

function avatarBg(name) {
  const palette = ['#6366f1','#8b5cf6','#ec4899','#f59e0b','#10b981','#3b82f6','#ef4444','#06b6d4']
  let h = 0
  for (let i = 0; i < name.length; i++) h = name.charCodeAt(i) + ((h << 5) - h)
  return palette[Math.abs(h) % palette.length]
}

function initial(name) {
  return name ? name.charAt(0).toUpperCase() : '?'
}

const rangeStart = computed(() => filteredUsers.value.length ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const rangeEnd = computed(() => Math.min(currentPage.value * pageSize.value, filteredUsers.value.length))
</script>

<template>
  <div class="um-wrap">
    <!-- Page header -->
    <div class="um-header">
      <div class="um-title-group">
        <div class="um-icon-box">
          <svg width="20" height="20" fill="none" stroke="#fff" stroke-width="2" viewBox="0 0 24 24">
            <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>
            <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <div>
          <h2 class="um-title">用户管理</h2>
          <p class="um-desc">管理系统中的所有注册用户</p>
        </div>
      </div>
      <button class="um-add-btn" @click="openAdd">
        <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新增用户
      </button>
    </div>

    <!-- Stats -->
    <div class="um-stats">
      <div class="um-stat-card">
        <div class="um-stat-icon" style="background:#eff6ff">
          <svg width="18" height="18" fill="none" stroke="#3b82f6" stroke-width="2" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>
        </div>
        <div>
          <div class="um-stat-num">{{ users.length }}</div>
          <div class="um-stat-label">总用户数</div>
        </div>
      </div>
      <div class="um-stat-card">
        <div class="um-stat-icon" style="background:#f5f3ff">
          <svg width="18" height="18" fill="none" stroke="#8b5cf6" stroke-width="2" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        </div>
        <div>
          <div class="um-stat-num">{{ filteredUsers.length }}</div>
          <div class="um-stat-label">筛选结果</div>
        </div>
      </div>
      <div class="um-stat-card">
        <div class="um-stat-icon" style="background:#f0fdf4">
          <svg width="18" height="18" fill="none" stroke="#10b981" stroke-width="2" viewBox="0 0 24 24"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
        </div>
        <div>
          <div class="um-stat-num">{{ totalPages }}</div>
          <div class="um-stat-label">总页数</div>
        </div>
      </div>
    </div>

    <!-- Table card -->
    <div class="um-card">
      <!-- Toolbar -->
      <div class="um-toolbar">
        <div class="um-search-wrap">
          <svg class="um-search-icon" width="15" height="15" fill="none" stroke="#9ca3af" stroke-width="2" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="searchQuery" class="um-search" placeholder="搜索用户名、邮箱或手机号..." />
        </div>
        <button class="um-refresh-btn" @click="loadUsers" :disabled="loading">
          <svg :class="['um-spin-svg', { spinning: loading }]" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          刷新
        </button>
      </div>

      <!-- Table -->
      <div class="um-table-scroll">
        <table class="um-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>用户</th>
              <th>年龄</th>
              <th>邮箱</th>
              <th>手机号</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="loading">
              <tr v-for="i in 5" :key="i" class="um-sk-row">
                <td><div class="um-sk um-sk-s"></div></td>
                <td>
                  <div class="um-user-cell">
                    <div class="um-sk um-sk-av"></div>
                    <div class="um-sk um-sk-m"></div>
                  </div>
                </td>
                <td><div class="um-sk um-sk-s"></div></td>
                <td><div class="um-sk um-sk-l"></div></td>
                <td><div class="um-sk um-sk-m"></div></td>
                <td><div class="um-sk um-sk-m"></div></td>
              </tr>
            </template>
            <tr v-else-if="!pagedUsers.length" class="um-empty-row">
              <td colspan="6">
                <div class="um-empty">
                  <svg width="40" height="40" fill="none" stroke="#d1d5db" stroke-width="1.5" viewBox="0 0 24 24"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                  <span>{{ searchQuery ? '没有找到匹配的用户' : '暂无用户数据' }}</span>
                </div>
              </td>
            </tr>
            <tr v-else v-for="u in pagedUsers" :key="u.id" class="um-data-row">
              <td class="um-id">#{{ u.id }}</td>
              <td>
                <div class="um-user-cell">
                  <div class="um-avatar" :style="{ background: avatarBg(u.username) }">{{ initial(u.username) }}</div>
                  <span class="um-uname">{{ u.username }}</span>
                </div>
              </td>
              <td>{{ u.age ?? '—' }}</td>
              <td class="um-email">{{ u.email ?? '—' }}</td>
              <td>{{ u.phone ?? '—' }}</td>
              <td>
                <div class="um-ops">
                  <button class="um-op um-op-edit" @click="openEdit(u.id)" :disabled="editingId !== null">
                    {{ editingId === u.id ? '加载中' : '编辑' }}
                  </button>
                  <button class="um-op um-op-del" @click="deleteUser(u.id, u.username)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="um-pager" v-if="!loading">
        <span class="um-pager-info">显示第 {{ rangeStart }}–{{ rangeEnd }} 条，共 {{ filteredUsers.length }} 条</span>
        <div class="um-pager-pages">
          <button class="um-pager-btn" :disabled="currentPage === 1" @click="goToPage(1)" title="第一页">«</button>
          <button class="um-pager-btn" :disabled="currentPage === 1" @click="goToPage(currentPage - 1)" title="上一页">‹</button>
          <template v-for="p in pageNumbers" :key="p + '-' + Math.random()">
            <span v-if="p === '...'" class="um-pager-dots">···</span>
            <button v-else class="um-pager-btn" :class="{ 'um-pager-active': p === currentPage }" @click="goToPage(p)">{{ p }}</button>
          </template>
          <button class="um-pager-btn" :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)" title="下一页">›</button>
          <button class="um-pager-btn" :disabled="currentPage >= totalPages" @click="goToPage(totalPages)" title="最后一页">»</button>
        </div>
        <select v-model="pageSize" class="um-pager-size">
          <option :value="5">5 条/页</option>
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
      </div>
    </div>
  </div>

  <!-- Modal -->
  <Modal :show="showModal" :title="isEdit ? '编辑用户' : '新增用户'" @close="showModal = false">
    <form @submit.prevent="submitForm" class="um-form">
      <div class="um-form-row">
        <div class="um-fg">
          <label>用户名 <span class="um-req">*</span></label>
          <input v-model="form.username" placeholder="请输入用户名" required />
        </div>
        <div class="um-fg">
          <label>年龄 <span class="um-req">*</span></label>
          <input v-model="form.age" type="number" min="1" max="150" placeholder="请输入年龄" required />
        </div>
      </div>
      <div class="um-fg">
        <label>邮箱 <span class="um-req">*</span></label>
        <input v-model="form.email" type="email" placeholder="请输入邮箱" required />
      </div>
      <div class="um-form-row">
        <div class="um-fg">
          <label>手机号</label>
          <input v-model="form.phone" placeholder="请输入手机号" />
        </div>
        <div class="um-fg">
          <label>密码 <span v-if="!isEdit" class="um-req">*</span></label>
          <input v-model="form.password" type="password" :placeholder="isEdit ? '不填则不修改密码' : '请输入密码'" />
        </div>
      </div>
      <div class="um-modal-footer">
        <button type="button" class="um-btn-cancel" @click="showModal = false" :disabled="submitting">取消</button>
        <button type="submit" class="um-btn-submit" :disabled="submitting">
          {{ submitting ? '提交中...' : (isEdit ? '保存修改' : '确认新增') }}
        </button>
      </div>
    </form>
  </Modal>
</template>

<style scoped>
.um-wrap { padding: 4px 0; }

/* Header */
.um-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 22px; }
.um-title-group { display: flex; align-items: center; gap: 14px; }
.um-icon-box {
  width: 46px; height: 46px; border-radius: 13px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 14px rgba(99,102,241,0.4);
  flex-shrink: 0;
}
.um-title { font-size: 20px; font-weight: 700; color: #111827; margin: 0; }
.um-desc { font-size: 13px; color: #9ca3af; margin: 3px 0 0; }
.um-add-btn {
  display: flex; align-items: center; gap: 7px;
  padding: 10px 22px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff; border: none; border-radius: 10px;
  font-size: 14px; font-weight: 500; cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 4px 14px rgba(99,102,241,0.38);
  font-family: inherit;
}
.um-add-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(99,102,241,0.48); }

/* Stats */
.um-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 18px; }
.um-stat-card {
  background: #fff; border-radius: 14px; padding: 18px 20px;
  display: flex; align-items: center; gap: 14px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.05); border: 1px solid #f0f0f5;
}
.um-stat-icon {
  width: 44px; height: 44px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.um-stat-num { font-size: 24px; font-weight: 700; color: #111827; line-height: 1; }
.um-stat-label { font-size: 12px; color: #9ca3af; margin-top: 5px; }

/* Card */
.um-card {
  background: #fff; border-radius: 16px;
  box-shadow: 0 1px 8px rgba(0,0,0,0.06); border: 1px solid #f0f0f5; overflow: hidden;
}

/* Toolbar */
.um-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid #f5f5f8; gap: 12px;
}
.um-search-wrap { position: relative; flex: 1; max-width: 360px; }
.um-search-icon { position: absolute; left: 11px; top: 50%; transform: translateY(-50%); pointer-events: none; }
.um-search {
  width: 100%; padding: 9px 14px 9px 34px;
  border: 1.5px solid #e5e7eb; border-radius: 9px;
  font-size: 14px; background: #f9fafb;
  transition: all 0.2s; font-family: inherit;
}
.um-search:focus { outline: none; border-color: #6366f1; background: #fff; box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.um-refresh-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 9px 16px; border: 1.5px solid #e5e7eb;
  background: #fff; border-radius: 9px; font-size: 13px;
  cursor: pointer; color: #6b7280; transition: all 0.2s; font-family: inherit;
}
.um-refresh-btn:hover { border-color: #6366f1; color: #6366f1; background: #f5f3ff; }

/* Table */
.um-table-scroll { overflow-x: auto; }
.um-table { width: 100%; border-collapse: collapse; }
.um-table thead tr { background: #f8f9fd; }
.um-table th {
  padding: 12px 16px; font-size: 11px; font-weight: 700;
  color: #6b7280; text-transform: uppercase; letter-spacing: 0.7px;
  text-align: left; border-bottom: 1px solid #f0f0f5;
}
.um-data-row td {
  padding: 14px 16px; font-size: 14px; color: #374151;
  border-bottom: 1px solid #f5f5f8; transition: background 0.15s;
}
.um-data-row:hover td { background: #fafbff; }
.um-data-row:last-child td { border-bottom: none; }
.um-id { font-family: monospace; font-size: 12px; color: #9ca3af; }
.um-user-cell { display: flex; align-items: center; gap: 10px; }
.um-avatar {
  width: 34px; height: 34px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 13px; font-weight: 700; flex-shrink: 0;
}
.um-uname { font-weight: 600; color: #1f2937; }
.um-email { color: #6b7280; font-size: 13px; }
.um-ops { display: flex; gap: 6px; }
.um-op {
  padding: 5px 13px; border-radius: 7px; border: none;
  font-size: 12px; font-weight: 500; cursor: pointer;
  transition: all 0.15s; font-family: inherit;
}
.um-op-edit { background: #eff6ff; color: #3b82f6; }
.um-op-edit:hover { background: #3b82f6; color: #fff; }
.um-op-del { background: #fef2f2; color: #ef4444; }
.um-op-del:hover { background: #ef4444; color: #fff; }

/* Empty */
.um-empty-row td { padding: 60px 0; }
.um-empty { display: flex; flex-direction: column; align-items: center; gap: 10px; color: #9ca3af; font-size: 14px; }

/* Skeleton */
.um-sk-row td { padding: 14px 16px; border-bottom: 1px solid #f5f5f8; }
.um-sk {
  height: 13px; border-radius: 6px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%; animation: um-shimmer 1.4s infinite;
}
.um-sk-s { width: 36px; }
.um-sk-m { width: 80px; }
.um-sk-l { width: 150px; }
.um-sk-av { width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0; }
@keyframes um-shimmer { to { background-position: -200% 0; } }

/* Pagination */
.um-pager {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; border-top: 1px solid #f5f5f8; gap: 10px;
}
.um-pager-info { font-size: 13px; color: #9ca3af; white-space: nowrap; }
.um-pager-pages { display: flex; align-items: center; gap: 3px; }
.um-pager-btn {
  min-width: 32px; height: 32px; padding: 0 7px;
  border: 1.5px solid #e5e7eb; background: #fff;
  border-radius: 7px; font-size: 13px; cursor: pointer;
  transition: all 0.15s; color: #374151; font-family: inherit;
}
.um-pager-btn:hover:not(:disabled) { border-color: #6366f1; color: #6366f1; background: #f5f3ff; }
.um-pager-btn.um-pager-active { background: #6366f1; border-color: #6366f1; color: #fff; font-weight: 700; }
.um-pager-btn:disabled { opacity: 0.38; cursor: not-allowed; }
.um-pager-dots { padding: 0 4px; color: #9ca3af; font-size: 15px; line-height: 32px; user-select: none; }
.um-pager-size {
  padding: 6px 10px; border: 1.5px solid #e5e7eb; border-radius: 7px;
  font-size: 13px; color: #374151; background: #fff; cursor: pointer; font-family: inherit;
}
.um-pager-size:focus { outline: none; border-color: #6366f1; }

/* Spin */
.um-spin-svg { transition: none; }
.spinning { animation: um-spin 0.7s linear infinite; }
@keyframes um-spin { to { transform: rotate(360deg); } }

/* Modal form */
.um-form { padding: 2px 0; }
.um-form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.um-fg { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.um-fg label { font-size: 13px; font-weight: 600; color: #374151; }
.um-fg input {
  padding: 9px 12px; border: 1.5px solid #e5e7eb; border-radius: 9px;
  font-size: 14px; background: #f9fafb; transition: all 0.2s; font-family: inherit;
}
.um-fg input:focus { outline: none; border-color: #6366f1; background: #fff; box-shadow: 0 0 0 3px rgba(99,102,241,0.1); }
.um-req { color: #ef4444; }
.um-modal-footer {
  display: flex; gap: 10px; justify-content: flex-end;
  margin-top: 20px; padding-top: 16px; border-top: 1px solid #f0f0f0;
}
.um-btn-cancel {
  padding: 9px 22px; border: 1.5px solid #e5e7eb; background: #fff;
  border-radius: 9px; font-size: 14px; cursor: pointer; color: #6b7280;
  transition: all 0.2s; font-family: inherit;
}
.um-btn-cancel:hover { background: #f5f5f5; border-color: #d1d5db; }
.um-btn-submit {
  padding: 9px 22px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  color: #fff; border: none; border-radius: 9px;
  font-size: 14px; font-weight: 500; cursor: pointer;
  box-shadow: 0 3px 10px rgba(99,102,241,0.35);
  transition: all 0.2s; font-family: inherit;
}
.um-btn-submit:hover { transform: translateY(-1px); box-shadow: 0 5px 15px rgba(99,102,241,0.45); }
</style>
