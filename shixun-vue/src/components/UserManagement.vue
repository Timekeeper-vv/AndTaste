<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Modal from './Modal.vue'
import type { UserRecord, AlertType, Role } from '../types'

const props = defineProps<{ currentUser?: { username: string; role: Role } | null }>()
const emit = defineEmits<{ alert: [msg: string, type?: AlertType] }>()

interface UserForm { id: string; username: string; age: string; email: string; phone: string; password: string; role: Role }

const users = ref<UserRecord[]>([])
const loading = ref<boolean>(false)
const submitting = ref<boolean>(false)
const editingId = ref<number | null>(null)
const showModal = ref<boolean>(false)
const isEdit = ref<boolean>(false)
const form = ref<UserForm>({ id: '', username: '', age: '', email: '', phone: '', password: '', role: 'admin' })

const roleOptions: Array<{ value: Role; label: string }> = [
  { value: 'admin',      label: '超级管理员' },
  { value: 'technician', label: '审批主管' },
  { value: 'feeder',     label: '员工' },
  { value: 'designer',   label: '设计师' },
]
const roleLabelMap: Record<Role, string> = { admin: '超级管理员', technician: '审批主管', feeder: '员工', designer: '设计师' }
const roleBadgeClass: Record<Role, string> = { admin: 'badge-admin', technician: 'badge-approver', feeder: 'badge-employee', designer: 'badge-designer' }
const demoPasswordAccounts = new Set(['superadmin', 'approver01', 'employee01', 'testuser', '审批员1', '审批员2', '审批员3', '审批员4', 'designer'])
const searchQuery = ref<string>('')
const currentPage = ref<number>(1)
const pageSize = ref<number>(10)
const total = ref<number>(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const isAdmin = computed(() => props.currentUser?.role === 'admin')

onMounted(loadUsers)

async function loadUsers() {
  loading.value = true
  users.value = []
  try {
    const p = new URLSearchParams({ page: String(currentPage.value), size: String(pageSize.value) })
    if (searchQuery.value.trim()) p.set('search', searchQuery.value.trim())
    const res = await fetch(`/api/users?${p}`, { headers: { 'X-Current-Role': props.currentUser?.role || 'admin', 'X-Current-User': props.currentUser?.username || 'admin' } })
    const data = await res.json()
    users.value = data.content
    total.value = data.total
  } catch {
    emit('alert', '加载用户失败', 'error')
  } finally {
    loading.value = false
  }
}

function onSearch() { currentPage.value = 1; loadUsers() }

function openAdd() {
  form.value = { id: '', username: '', age: '', email: '', phone: '', password: '', role: 'admin' }
  isEdit.value = false
  showModal.value = true
}

async function openEdit(id: number): Promise<void> {
  if (editingId.value !== null) return
  editingId.value = id
  try {
    const res = await fetch(`/api/users/${id}`, { headers: { 'X-Current-Role': props.currentUser?.role || 'admin', 'X-Current-User': props.currentUser?.username || 'admin' } })
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
  const payload: Record<string, unknown> = {
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
      headers: {
        'Content-Type': 'application/json',
        'X-Current-Role': props.currentUser?.role || 'admin',
        'X-Current-User': props.currentUser?.username || 'admin',
      },
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

async function deleteUser(id: number, name: string) {
  if (!confirm(`确定要删除用户「${name}」？此操作不可恢复。`)) return
  try {
    const res = await fetch(`/api/users/${id}`, {
      method: 'DELETE',
      headers: {
        'X-Current-Role': props.currentUser?.role || 'admin',
        'X-Current-User': props.currentUser?.username || 'admin',
      },
    })
    if (!res.ok && res.status !== 204) { emit('alert', '删除失败', 'error'); return }
    emit('alert', '用户删除成功')
    loadUsers()
  } catch {
    emit('alert', '网络错误', 'error')
  }
}

async function resetPassword(u: UserRecord) {
  if (!isAdmin.value) { emit('alert', '仅超级管理员可重置密码', 'error'); return }
  if (!confirm(`确定将「${u.username}」的密码重置为 123456？`)) return
  try {
    const res = await fetch(`/api/users/${u.id}/reset-password`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Current-Role': props.currentUser?.role || 'admin',
        'X-Current-User': props.currentUser?.username || 'admin',
      },
      body: JSON.stringify({ password: '123456' }),
    })
    if (!res.ok) {
      const text = await res.text()
      emit('alert', `重置失败：${extractMsg(text)}`, 'error'); return
    }
    const data = await res.json()
    emit('alert', `已重置 ${data.username}，新密码：${data.password}`, 'success')
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

function passwordDisplay(u: UserRecord) {
  if (!isAdmin.value) return '仅超管可见'
  return demoPasswordAccounts.has(u.username) ? '123456' : '已加密存储'
}

</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-desc">管理超级管理员、审批主管与员工账号权限。密码采用加密存储，演示账号显示默认密码，其他账号可由超管重置。</p>
      </div>
      <button class="btn btn-primary" @click="openAdd">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新增用户
      </button>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">总用户数</div>
        <div class="stat-num primary">{{ total }}</div>
      </div>
    </div>

    <div class="table-card">
      <div class="toolbar">
        <div class="search-wrap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="searchQuery" class="search-input" placeholder="搜索用户名、邮箱或手机号..." @input="onSearch" />
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
              <th>登录密码</th>
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
                <td><div class="sk sk-md"></div></td>
              </tr>
            </template>

            <tr v-else-if="!users.length">
              <td colspan="8">
                <div class="empty-state">
                  <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" style="color:var(--c-border)"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                  <p>{{ searchQuery ? '没有找到匹配的用户' : '暂无用户数据' }}</p>
                </div>
              </td>
            </tr>

            <tr v-else v-for="u in users" :key="u.id">
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
                <span class="password-pill" :class="{ muted: !demoPasswordAccounts.has(u.username) }">
                  {{ passwordDisplay(u) }}
                </span>
              </td>
              <td>
                <div class="td-ops">
                  <button class="btn-edit" @click="openEdit(u.id)" :disabled="editingId !== null">
                    {{ editingId === u.id ? '加载中' : '编辑' }}
                  </button>
                  <button v-if="isAdmin" class="btn-reset" @click="resetPassword(u)">重置密码</button>
                  <button class="btn-del" @click="deleteUser(u.id, u.username)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <span class="pg-total">共 {{ total }} 条</span>
        <button class="pg-btn" :disabled="currentPage === 1" @click="currentPage--; loadUsers()">‹</button>
        <span class="pg-info">第 {{ currentPage }} / {{ totalPages }} 页</span>
        <button class="pg-btn" :disabled="currentPage === totalPages" @click="currentPage++; loadUsers()">›</button>
        <select v-model.number="pageSize" class="pg-size" @change="currentPage = 1; loadUsers()">
          <option :value="5">5条/页</option>
          <option :value="10">10条/页</option>
          <option :value="20">20条/页</option>
          <option :value="50">50条/页</option>
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
          <small class="role-help">超管可管理全部；审批主管负责审核申请；员工负责业务申请；设计师仅可使用创意设计三个功能。</small>
        </div>
        <div class="form-group" style="grid-column: 1 / -1">
          <label>密码 <span v-if="!isEdit" style="color:var(--c-error)">*</span></label>
          <input v-model="form.password" type="password" :placeholder="isEdit ? '不填则不修改密码' : '请设置密码'" />
          <small class="role-help">出于安全原因，系统不保存可反查的明文密码；如忘记密码，超级管理员可在列表中一键重置为 123456。</small>
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
.badge-admin    { background: rgba(239,68,68,.12); color: #ef4444; }
.badge-approver { background: rgba(124,58,237,.12); color: #7c3aed; }
.badge-employee { background: rgba(13,148,136,.12); color: #0d9488; }
.badge-designer { background: rgba(37,99,235,.12); color: #2563eb; }
.role-help { display:block; margin-top:6px; font-size:12px; color:var(--c-text-3); line-height:1.4; }
.password-pill {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  background: rgba(16,185,129,.12);
  color: #047857;
  font-size: 12px;
  font-weight: 800;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.password-pill.muted {
  background: rgba(100,116,139,.10);
  color: var(--c-text-3);
  font-family: inherit;
}
.btn-reset {
  height: 28px;
  padding: 0 10px;
  border: 1px solid rgba(37,99,235,.18);
  border-radius: 999px;
  background: rgba(37,99,235,.08);
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
  cursor: pointer;
}
.btn-reset:hover { background: #2563eb; color: #fff; border-color: #2563eb; }

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
.pg-total { color: var(--c-text-3); margin-right: auto; }
.pg-size {
  height: 28px; padding: 0 6px; border: 1px solid var(--c-border);
  border-radius: var(--r); font-size: 12px; color: var(--c-text);
  background: var(--c-surface); cursor: pointer;
}
</style>
