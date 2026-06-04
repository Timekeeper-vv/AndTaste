<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import Modal from './Modal.vue'

const emit = defineEmits(['alert'])

const products = ref([])
const loading = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const showModal = ref(false)
const isEdit = ref(false)
const form = ref({ id: '', name: '', price: '', stock: '', category: '', description: '' })
const searchQuery = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

onMounted(loadProducts)

watch(searchQuery, () => { currentPage.value = 1 })
watch(pageSize, () => { currentPage.value = 1 })

async function loadProducts() {
  loading.value = true
  products.value = []
  try {
    const res = await fetch('/api/products')
    products.value = await res.json()
  } catch {
    emit('alert', '加载商品失败', 'error')
  } finally {
    loading.value = false
  }
}

const filteredProducts = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return products.value
  return products.value.filter(p =>
    (p.name ?? '').toLowerCase().includes(q) ||
    (p.category ?? '').toLowerCase().includes(q) ||
    (p.description ?? '').toLowerCase().includes(q)
  )
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredProducts.value.length / pageSize.value)))

const pagedProducts = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredProducts.value.slice(start, start + pageSize.value)
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
  form.value = { id: '', name: '', price: '', stock: '', category: '', description: '' }
  isEdit.value = false
  showModal.value = true
}

async function openEdit(id) {
  if (editingId.value !== null) return
  editingId.value = id
  try {
    const res = await fetch(`/api/products/${id}`)
    if (!res.ok) { emit('alert', '获取商品信息失败', 'error'); return }
    const p = await res.json()
    form.value = { id: p.id, name: p.name ?? '', price: p.price ?? '', stock: p.stock ?? '', category: p.category ?? '', description: p.description ?? '' }
    isEdit.value = true
    showModal.value = true
  } catch {
    emit('alert', '获取商品信息失败', 'error')
  } finally {
    editingId.value = null
  }
}

async function submitForm() {
  if (submitting.value) return
  const payload = {
    name: form.value.name,
    price: Number(form.value.price),
    stock: Number(form.value.stock),
    category: form.value.category || undefined,
    description: form.value.description || undefined,
  }
  submitting.value = true
  try {
    const res = await fetch(isEdit.value ? `/api/products/${form.value.id}` : '/api/products', {
      method: isEdit.value ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (!res.ok) {
      const text = await res.text()
      emit('alert', `操作失败：${extractMsg(text)}`, 'error')
      return
    }
    emit('alert', isEdit.value ? '商品修改成功' : '商品新增成功')
    showModal.value = false
    loadProducts()
  } catch {
    emit('alert', '网络错误', 'error')
  } finally {
    submitting.value = false
  }
}

async function deleteProduct(id, name) {
  if (!confirm(`确定要删除商品「${name}」（ID: ${id}）吗？此操作不可恢复。`)) return
  try {
    const res = await fetch(`/api/products/${id}`, { method: 'DELETE' })
    if (!res.ok && res.status !== 204) { emit('alert', '删除失败', 'error'); return }
    emit('alert', '商品删除成功')
    loadProducts()
  } catch {
    emit('alert', '网络错误', 'error')
  }
}

function extractMsg(text) {
  try { const o = JSON.parse(text); return o.detail || o.message || text } catch { return text }
}

function formatPrice(price) {
  return price != null ? `¥ ${Number(price).toFixed(2)}` : '—'
}

function stockStatus(stock) {
  if (stock == null) return null
  if (stock === 0) return { label: '缺货', cls: 'pm-stock-out' }
  if (stock < 10) return { label: '库存紧张', cls: 'pm-stock-low' }
  return { label: '库存充足', cls: 'pm-stock-ok' }
}

const rangeStart = computed(() => filteredProducts.value.length ? (currentPage.value - 1) * pageSize.value + 1 : 0)
const rangeEnd = computed(() => Math.min(currentPage.value * pageSize.value, filteredProducts.value.length))

const totalStock = computed(() => products.value.reduce((s, p) => s + (p.stock ?? 0), 0))
</script>

<template>
  <div class="pm-wrap">
    <!-- Header -->
    <div class="pm-header">
      <div class="pm-title-group">
        <div class="pm-icon-box">
          <svg width="20" height="20" fill="none" stroke="#fff" stroke-width="2" viewBox="0 0 24 24">
            <path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/>
            <path d="M16 10a4 4 0 0 1-8 0"/>
          </svg>
        </div>
        <div>
          <h2 class="pm-title">商品管理</h2>
          <p class="pm-desc">管理系统中的所有在售商品</p>
        </div>
      </div>
      <button class="pm-add-btn" @click="openAdd">
        <svg width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
        新增商品
      </button>
    </div>

    <!-- Stats -->
    <div class="pm-stats">
      <div class="pm-stat-card">
        <div class="pm-stat-icon" style="background:#ecfdf5">
          <svg width="18" height="18" fill="none" stroke="#10b981" stroke-width="2" viewBox="0 0 24 24"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
        </div>
        <div>
          <div class="pm-stat-num">{{ products.length }}</div>
          <div class="pm-stat-label">总商品数</div>
        </div>
      </div>
      <div class="pm-stat-card">
        <div class="pm-stat-icon" style="background:#fff7ed">
          <svg width="18" height="18" fill="none" stroke="#f59e0b" stroke-width="2" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        </div>
        <div>
          <div class="pm-stat-num">{{ filteredProducts.length }}</div>
          <div class="pm-stat-label">筛选结果</div>
        </div>
      </div>
      <div class="pm-stat-card">
        <div class="pm-stat-icon" style="background:#eff6ff">
          <svg width="18" height="18" fill="none" stroke="#3b82f6" stroke-width="2" viewBox="0 0 24 24"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>
        </div>
        <div>
          <div class="pm-stat-num">{{ totalStock.toLocaleString() }}</div>
          <div class="pm-stat-label">总库存量</div>
        </div>
      </div>
    </div>

    <!-- Table card -->
    <div class="pm-card">
      <!-- Toolbar -->
      <div class="pm-toolbar">
        <div class="pm-search-wrap">
          <svg class="pm-search-icon" width="15" height="15" fill="none" stroke="#9ca3af" stroke-width="2" viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <input v-model="searchQuery" class="pm-search" placeholder="搜索商品名称、分类或描述..." />
        </div>
        <button class="pm-refresh-btn" @click="loadProducts" :disabled="loading">
          <svg :class="['pm-spin-svg', { spinning: loading }]" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.5" viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10"/></svg>
          刷新
        </button>
      </div>

      <!-- Table -->
      <div class="pm-table-scroll">
        <table class="pm-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>商品名称</th>
              <th>价格</th>
              <th>库存</th>
              <th>分类</th>
              <th>描述</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-if="loading">
              <tr v-for="i in 5" :key="i" class="pm-sk-row">
                <td><div class="pm-sk pm-sk-s"></div></td>
                <td>
                  <div class="pm-prod-cell">
                    <div class="pm-sk pm-sk-icon"></div>
                    <div class="pm-sk pm-sk-m"></div>
                  </div>
                </td>
                <td><div class="pm-sk pm-sk-m"></div></td>
                <td><div class="pm-sk pm-sk-s"></div></td>
                <td><div class="pm-sk pm-sk-m"></div></td>
                <td><div class="pm-sk pm-sk-l"></div></td>
                <td><div class="pm-sk pm-sk-m"></div></td>
              </tr>
            </template>
            <tr v-else-if="!pagedProducts.length" class="pm-empty-row">
              <td colspan="7">
                <div class="pm-empty">
                  <svg width="40" height="40" fill="none" stroke="#d1d5db" stroke-width="1.5" viewBox="0 0 24 24"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
                  <span>{{ searchQuery ? '没有找到匹配的商品' : '暂无商品数据' }}</span>
                </div>
              </td>
            </tr>
            <tr v-else v-for="p in pagedProducts" :key="p.id" class="pm-data-row">
              <td class="pm-id">#{{ p.id }}</td>
              <td>
                <div class="pm-prod-cell">
                  <div class="pm-prod-icon">
                    <svg width="14" height="14" fill="none" stroke="#10b981" stroke-width="2" viewBox="0 0 24 24"><path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><line x1="3" y1="6" x2="21" y2="6"/><path d="M16 10a4 4 0 0 1-8 0"/></svg>
                  </div>
                  <span class="pm-pname">{{ p.name }}</span>
                </div>
              </td>
              <td class="pm-price">{{ formatPrice(p.price) }}</td>
              <td>
                <div v-if="p.stock != null" class="pm-stock-wrap">
                  <span :class="['pm-stock-badge', stockStatus(p.stock)?.cls]">{{ stockStatus(p.stock)?.label }}</span>
                  <span class="pm-stock-num">{{ p.stock }}</span>
                </div>
                <span v-else>—</span>
              </td>
              <td>
                <span v-if="p.category" class="pm-tag">{{ p.category }}</span>
                <span v-else class="pm-no-cat">—</span>
              </td>
              <td class="pm-desc" :title="p.description ?? ''">{{ p.description ?? '—' }}</td>
              <td>
                <div class="pm-ops">
                  <button class="pm-op pm-op-edit" @click="openEdit(p.id)" :disabled="editingId !== null">
                    {{ editingId === p.id ? '加载中' : '编辑' }}
                  </button>
                  <button class="pm-op pm-op-del" @click="deleteProduct(p.id, p.name)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Pagination -->
      <div class="pm-pager" v-if="!loading">
        <span class="pm-pager-info">显示第 {{ rangeStart }}–{{ rangeEnd }} 条，共 {{ filteredProducts.length }} 条</span>
        <div class="pm-pager-pages">
          <button class="pm-pager-btn" :disabled="currentPage === 1" @click="goToPage(1)" title="第一页">«</button>
          <button class="pm-pager-btn" :disabled="currentPage === 1" @click="goToPage(currentPage - 1)" title="上一页">‹</button>
          <template v-for="p in pageNumbers" :key="p + '-' + Math.random()">
            <span v-if="p === '...'" class="pm-pager-dots">···</span>
            <button v-else class="pm-pager-btn" :class="{ 'pm-pager-active': p === currentPage }" @click="goToPage(p)">{{ p }}</button>
          </template>
          <button class="pm-pager-btn" :disabled="currentPage >= totalPages" @click="goToPage(currentPage + 1)" title="下一页">›</button>
          <button class="pm-pager-btn" :disabled="currentPage >= totalPages" @click="goToPage(totalPages)" title="最后一页">»</button>
        </div>
        <select v-model="pageSize" class="pm-pager-size">
          <option :value="5">5 条/页</option>
          <option :value="10">10 条/页</option>
          <option :value="20">20 条/页</option>
          <option :value="50">50 条/页</option>
        </select>
      </div>
    </div>
  </div>

  <!-- Modal -->
  <Modal :show="showModal" :title="isEdit ? '编辑商品' : '新增商品'" @close="showModal = false">
    <form @submit.prevent="submitForm" class="pm-form">
      <div class="pm-form-row">
        <div class="pm-fg">
          <label>商品名称 <span class="pm-req">*</span></label>
          <input v-model="form.name" placeholder="请输入商品名称" required />
        </div>
        <div class="pm-fg">
          <label>分类</label>
          <input v-model="form.category" placeholder="请输入商品分类" />
        </div>
      </div>
      <div class="pm-form-row">
        <div class="pm-fg">
          <label>价格（元）<span class="pm-req">*</span></label>
          <input v-model="form.price" type="number" min="0.01" step="0.01" placeholder="请输入价格" required />
        </div>
        <div class="pm-fg">
          <label>库存 <span class="pm-req">*</span></label>
          <input v-model="form.stock" type="number" min="0" placeholder="请输入库存数量" required />
        </div>
      </div>
      <div class="pm-fg">
        <label>描述</label>
        <textarea v-model="form.description" placeholder="请输入商品描述" class="pm-textarea"></textarea>
      </div>
      <div class="pm-modal-footer">
        <button type="button" class="pm-btn-cancel" @click="showModal = false" :disabled="submitting">取消</button>
        <button type="submit" class="pm-btn-submit" :disabled="submitting">
          {{ submitting ? '提交中...' : (isEdit ? '保存修改' : '确认新增') }}
        </button>
      </div>
    </form>
  </Modal>
</template>

<style scoped>
.pm-wrap { padding: 4px 0; }

/* Header */
.pm-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 22px; }
.pm-title-group { display: flex; align-items: center; gap: 14px; }
.pm-icon-box {
  width: 46px; height: 46px; border-radius: 13px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 14px rgba(16,185,129,0.4); flex-shrink: 0;
}
.pm-title { font-size: 20px; font-weight: 700; color: #111827; margin: 0; }
.pm-desc { font-size: 13px; color: #9ca3af; margin: 3px 0 0; }
.pm-add-btn {
  display: flex; align-items: center; gap: 7px;
  padding: 10px 22px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: #fff; border: none; border-radius: 10px;
  font-size: 14px; font-weight: 500; cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
  box-shadow: 0 4px 14px rgba(16,185,129,0.38); font-family: inherit;
}
.pm-add-btn:hover { transform: translateY(-2px); box-shadow: 0 6px 20px rgba(16,185,129,0.48); }

/* Stats */
.pm-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; margin-bottom: 18px; }
.pm-stat-card {
  background: #fff; border-radius: 14px; padding: 18px 20px;
  display: flex; align-items: center; gap: 14px;
  box-shadow: 0 1px 6px rgba(0,0,0,0.05); border: 1px solid #f0f0f5;
}
.pm-stat-icon {
  width: 44px; height: 44px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.pm-stat-num { font-size: 24px; font-weight: 700; color: #111827; line-height: 1; }
.pm-stat-label { font-size: 12px; color: #9ca3af; margin-top: 5px; }

/* Card */
.pm-card {
  background: #fff; border-radius: 16px;
  box-shadow: 0 1px 8px rgba(0,0,0,0.06); border: 1px solid #f0f0f5; overflow: hidden;
}

/* Toolbar */
.pm-toolbar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 20px; border-bottom: 1px solid #f5f5f8; gap: 12px;
}
.pm-search-wrap { position: relative; flex: 1; max-width: 360px; }
.pm-search-icon { position: absolute; left: 11px; top: 50%; transform: translateY(-50%); pointer-events: none; }
.pm-search {
  width: 100%; padding: 9px 14px 9px 34px;
  border: 1.5px solid #e5e7eb; border-radius: 9px;
  font-size: 14px; background: #f9fafb;
  transition: all 0.2s; font-family: inherit;
}
.pm-search:focus { outline: none; border-color: #10b981; background: #fff; box-shadow: 0 0 0 3px rgba(16,185,129,0.1); }
.pm-refresh-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 9px 16px; border: 1.5px solid #e5e7eb;
  background: #fff; border-radius: 9px; font-size: 13px;
  cursor: pointer; color: #6b7280; transition: all 0.2s; font-family: inherit;
}
.pm-refresh-btn:hover { border-color: #10b981; color: #10b981; background: #ecfdf5; }

/* Table */
.pm-table-scroll { overflow-x: auto; }
.pm-table { width: 100%; border-collapse: collapse; }
.pm-table thead tr { background: #f8f9fd; }
.pm-table th {
  padding: 12px 16px; font-size: 11px; font-weight: 700;
  color: #6b7280; text-transform: uppercase; letter-spacing: 0.7px;
  text-align: left; border-bottom: 1px solid #f0f0f5;
}
.pm-data-row td {
  padding: 14px 16px; font-size: 14px; color: #374151;
  border-bottom: 1px solid #f5f5f8; transition: background 0.15s;
}
.pm-data-row:hover td { background: #f0fdf8; }
.pm-data-row:last-child td { border-bottom: none; }
.pm-id { font-family: monospace; font-size: 12px; color: #9ca3af; }

.pm-prod-cell { display: flex; align-items: center; gap: 10px; }
.pm-prod-icon {
  width: 32px; height: 32px; border-radius: 8px; background: #ecfdf5;
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.pm-pname { font-weight: 600; color: #1f2937; }

.pm-price { font-weight: 600; color: #059669; font-size: 14px; }

.pm-stock-wrap { display: flex; align-items: center; gap: 6px; }
.pm-stock-badge {
  display: inline-block; padding: 2px 7px; border-radius: 20px;
  font-size: 11px; font-weight: 600; white-space: nowrap;
}
.pm-stock-ok { background: #d1fae5; color: #065f46; }
.pm-stock-low { background: #fef3c7; color: #92400e; }
.pm-stock-out { background: #fee2e2; color: #991b1b; }
.pm-stock-num { font-size: 13px; color: #6b7280; }

.pm-tag {
  display: inline-block; padding: 3px 10px; border-radius: 20px;
  font-size: 12px; font-weight: 500; background: #ecfdf5; color: #059669;
}
.pm-no-cat { color: #9ca3af; }

.pm-desc {
  max-width: 160px; overflow: hidden;
  text-overflow: ellipsis; white-space: nowrap;
  color: #6b7280; font-size: 13px;
}

.pm-ops { display: flex; gap: 6px; }
.pm-op {
  padding: 5px 13px; border-radius: 7px; border: none;
  font-size: 12px; font-weight: 500; cursor: pointer;
  transition: all 0.15s; font-family: inherit;
}
.pm-op-edit { background: #ecfdf5; color: #059669; }
.pm-op-edit:hover { background: #059669; color: #fff; }
.pm-op-del { background: #fef2f2; color: #ef4444; }
.pm-op-del:hover { background: #ef4444; color: #fff; }

/* Empty */
.pm-empty-row td { padding: 60px 0; }
.pm-empty { display: flex; flex-direction: column; align-items: center; gap: 10px; color: #9ca3af; font-size: 14px; }

/* Skeleton */
.pm-sk-row td { padding: 14px 16px; border-bottom: 1px solid #f5f5f8; }
.pm-sk {
  height: 13px; border-radius: 6px;
  background: linear-gradient(90deg, #f0f0f0 25%, #e8e8e8 50%, #f0f0f0 75%);
  background-size: 200% 100%; animation: pm-shimmer 1.4s infinite;
}
.pm-sk-s { width: 36px; }
.pm-sk-m { width: 80px; }
.pm-sk-l { width: 150px; }
.pm-sk-icon { width: 32px; height: 32px; border-radius: 8px; flex-shrink: 0; }
@keyframes pm-shimmer { to { background-position: -200% 0; } }

/* Pagination */
.pm-pager {
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 20px; border-top: 1px solid #f5f5f8; gap: 10px;
}
.pm-pager-info { font-size: 13px; color: #9ca3af; white-space: nowrap; }
.pm-pager-pages { display: flex; align-items: center; gap: 3px; }
.pm-pager-btn {
  min-width: 32px; height: 32px; padding: 0 7px;
  border: 1.5px solid #e5e7eb; background: #fff;
  border-radius: 7px; font-size: 13px; cursor: pointer;
  transition: all 0.15s; color: #374151; font-family: inherit;
}
.pm-pager-btn:hover:not(:disabled) { border-color: #10b981; color: #10b981; background: #ecfdf5; }
.pm-pager-btn.pm-pager-active { background: #10b981; border-color: #10b981; color: #fff; font-weight: 700; }
.pm-pager-btn:disabled { opacity: 0.38; cursor: not-allowed; }
.pm-pager-dots { padding: 0 4px; color: #9ca3af; font-size: 15px; line-height: 32px; user-select: none; }
.pm-pager-size {
  padding: 6px 10px; border: 1.5px solid #e5e7eb; border-radius: 7px;
  font-size: 13px; color: #374151; background: #fff; cursor: pointer; font-family: inherit;
}
.pm-pager-size:focus { outline: none; border-color: #10b981; }

/* Spin */
.spinning { animation: pm-spin 0.7s linear infinite; }
@keyframes pm-spin { to { transform: rotate(360deg); } }

/* Modal form */
.pm-form { padding: 2px 0; }
.pm-form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.pm-fg { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.pm-fg label { font-size: 13px; font-weight: 600; color: #374151; }
.pm-fg input, .pm-textarea {
  padding: 9px 12px; border: 1.5px solid #e5e7eb; border-radius: 9px;
  font-size: 14px; background: #f9fafb; transition: all 0.2s; font-family: inherit;
  width: 100%; box-sizing: border-box;
}
.pm-fg input:focus, .pm-textarea:focus {
  outline: none; border-color: #10b981; background: #fff;
  box-shadow: 0 0 0 3px rgba(16,185,129,0.1);
}
.pm-textarea { resize: vertical; min-height: 72px; }
.pm-req { color: #ef4444; }
.pm-modal-footer {
  display: flex; gap: 10px; justify-content: flex-end;
  margin-top: 20px; padding-top: 16px; border-top: 1px solid #f0f0f0;
}
.pm-btn-cancel {
  padding: 9px 22px; border: 1.5px solid #e5e7eb; background: #fff;
  border-radius: 9px; font-size: 14px; cursor: pointer; color: #6b7280;
  transition: all 0.2s; font-family: inherit;
}
.pm-btn-cancel:hover { background: #f5f5f5; border-color: #d1d5db; }
.pm-btn-submit {
  padding: 9px 22px;
  background: linear-gradient(135deg, #10b981 0%, #059669 100%);
  color: #fff; border: none; border-radius: 9px;
  font-size: 14px; font-weight: 500; cursor: pointer;
  box-shadow: 0 3px 10px rgba(16,185,129,0.35);
  transition: all 0.2s; font-family: inherit;
}
.pm-btn-submit:hover { transform: translateY(-1px); box-shadow: 0 5px 15px rgba(16,185,129,0.45); }
</style>
