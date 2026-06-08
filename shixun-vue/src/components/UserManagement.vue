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
const form = ref({ id: '', username: '', age: '', email: '', phone: '', password: '', role: 'admin' })

const roleOptions = [
  { value: 'admin',      label: '管理员' },
  { value: 'technician', label: '技术员' },
  { value: 'feeder',     label: '饲养员' },
]
const roleLabelMap = { admin: '管理员', technician: '技术员', feeder: '饲养员' }
const roleBadgeClass = { admin: 'badge-admin', technician: 'badge-tech', feeder: 'badge-feeder' }
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
  form.value = { id: '', username: '', age: '', email: '', phone: '', password: '', role: 'admin' }
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
    form.value = { id: u.id, username: u.username ?? '', age: u.age ?? '', email: u.email ?? '', phone: u.phone ?? '', password: '', role: u.role || 'admin' }
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
  if (!isEdit.value && !form.value.password) { emit('alert', '新增用户密码不能为空', 'error'); return }
  const payload = {
    username: form.value.username,
    age: Number(form.value.age),
    email: form.value.email,
    phone: form.value.phone || undefined,
    role: form.value.role || 'admin',
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
      emit('alert', `操作失败：${extractMsg(text)}`, 'error'); return
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
  if (!confirm(`确定要删除用户「${name}」？此操作不可恢复。`)) return
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
  const palette = ['#0D9488', '#4F46E5', '#EC4899', '#D97706', '#059669', '#2563EB', '#DC2626', '#06B6D4']
  let h = 0
  for (let i = 0; i < name.length; i++) h = name.charCodeAt(i) + ((h << 5) - h)
  return palette[Math.abs(h) % palette.length]
}

function initial(name) { return name ? name.charAt(0).toUpperCase() : '?' }

const rangeStart = computed(() => filteredUsers.value.length ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const rangeEnd = computed(() => Math.min(currentPage.value * pageSize.value, filteredUsers.value.length))
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">管理系统中的所有注册用户账号</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新增用户
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">总用户数</div>
        <div class="stat-num primary">{{ users.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">筛选结果</div>
        <div class="stat-num">{{ filteredUsers.length }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">总页数</div>
        <div class="stat-num">{{ totalPages }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="searchQuery" class="search-input" placeholder="搜索用户名、邮箱或手机号..." />
        </div>
        <button class="btn btn-secondary btn-sm" @click="loadUsers" :disabled="loading">
          <svg width="13" height="13" :class="{ spinning: loading }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          刷新
        </button>
      </div>

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>用户</th>
              <th>角色</th>
              <th>年龄</th>
              <th>邮箱</th>
              <th>手机号</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <!-- skeleton -->
            <template v-if="loading">
              <tr v-for="i in 5" :key="i">
                <td><div class="sk sk-sm"></div></td>
                <td>
                  <div style="display:flex;gap:8px;align-items:center">
                    <div class="sk sk-avatar"></div>
                    <div class="sk sk-md"></div>
                  </div>
                </td>
                <td><div class="sk sk-sm"></div></td>
                <td><div class="sk sk-sm"></div></td>
                <td><div class="sk sk-lg"></div></td>
                <td><div class="sk sk-md"></div></td>
                <td><div class="sk sk-md"></div></td>
              </tr>
            </template>

            <tr v-else-if="!pagedUsers.length">
              <td colspan="7">
                <div class="empty-state">
                  <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="color:var(--c-border)"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                  <p>{{ searchQuery ? '没有找到匹配的用户' : '暂无用户数据' }}</p>
                </div>
              </td>
            </tr>

            <tr v-else v-for="u in pagedUsers" :key="u.id">
              <td><span class="text-xs text-muted font-mono">#{{ u.id }}</span></td>
              <td>
                <div style="display:flex;align-items:center;gap:10px">
                  <div class="user-avatar" :style="{ background: avatarBg(u.username) }">{{ initial(u.username) }}</div>
                  <span class="font-medium">{{ u.username }}</span>
                </div>
              </td>
              <td><span class="role-badge" :class="roleBadgeClass[u.role || 'admin']">{{ roleLabelMap[u.role || 'admin'] }}</span></td>
              <td>{{ u.age ?? '—' }}</td>
              <td class="text-sm text-muted">{{ u.email ?? '—' }}</td>
              <td>{{ u.phone ?? '—' }}</td>
              <td>
                <div class="td-ops">
                  <button class="btn-edit" @click="openEdit(u.id)" :disabled="editingId !== null">
                    {{ editingId === u.id ? '加载中' : '编辑' }}
                  </button>
                  <button class="btn-del" @click="deleteUser(u.id, u.username)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination" v-if="!loading">
        <span class="pagination-info">显示 {{ rangeStart }}–{{ rangeEnd }} 条，共 {{ filteredUsers.length }} 条</span>
        <div class="pagination-btns">
          <button class="page-btn" :disabled="currentPage === 1" @click="goToPage(1)">«</button>
          <button class="page-btn" :disabled="currentPage === 1" @click="goToPage(currentPage - 1)">‹</button>
          <template v-for="p in pageNumbers" :key="String(p) + Math.random()">
            <span v-if="p === '...'" style="padding:0 4px;color:var(--c-text-3)">···</span>
            <button v-else class="page-btn" :class="{ active: p === currentPage }" @click="goToPage(p)">{{ p }}</button>
          </template>
          <button class="page-btn" :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)">›</button>
          <button class="page-btn" :disabled="currentPage >= totalPages" @click="goToPage(totalPages)">»</button>
        </div>
        <select v-model="pageSize" class="per-page-select">
          <option :value="5">5 条/页</option>
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
      </div>
    </div>
  </div>

  <Modal :show="showModal" :title="isEdit ? '编辑用户' : '新增用户'" @close="showModal = false">
    <form @submit.prevent="submitForm">
      <div class="form-grid">
        <div class="form-group">
          <label>用户名 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.username" placeholder="用户名" required />
        </div>
        <div class="form-group">
          <label>年龄 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.age" type="number" min="1" max="150" placeholder="年龄" required />
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>邮箱 <span style="color:var(--c-error)">*</span></label>
          <input v-model="form.email" type="email" placeholder="电子邮箱" required />
        </div>
        <div class="form-group">
          <label>手机号</label>
          <input v-model="form.phone" placeholder="手机号（选填）" />
        </div>
        <div class="form-group">
          <label>角色 <span style="color:var(--c-error)">*</span></label>
          <select v-model="form.role" required>
            <option v-for="opt in roleOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>密码 <span v-if="!isEdit" style="color:var(--c-error)">*</span></label>
          <input v-model="form.password" type="password" :placeholder="isEdit ? '不填则不修改密码' : '请设置密码'" />
        </div>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn btn-secondary" @click="showModal = false" :disabled="submitting">取消</button>
        <button type="submit" class="btn btn-primary" :disabled="submitting">
          {{ submitting ? '提交中...' : (isEdit ? '保存修改' : '确认新增') }}
        </button>
      </div>
    </form>
  </Modal>
</template>

<style scoped>
.role-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 20px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.badge-admin   { background: rgba(13,148,136,.12); color: #0d9488; }
.badge-tech    { background: rgba(37,99,235,.12);  color: #2563eb; }
.badge-feeder  { background: rgba(217,119,6,.12);  color: #d97706; }

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

/* Skeleton */
.sk {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, var(--c-bg) 25%, var(--c-border-light) 50%, var(--c-bg) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}
.sk-sm { width: 40px; }
.sk-md { width: 90px; }
.sk-lg { width: 160px; }
.sk-avatar { width: 32px; height: 32px; border-radius: 50%; flex-shrink: 0; }
@keyframes shimmer { to { background-position: -200% 0; } }

.spinning { animation: spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
