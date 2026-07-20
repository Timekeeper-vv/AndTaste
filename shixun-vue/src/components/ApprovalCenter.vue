<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'

type ApprovalStatus = 'pending' | 'approved' | 'rejected' | 'withdrawn'
type ApprovalCategory = 'finance' | 'chain' | 'production' | 'marketDepartment' | 'projectDepartment' | 'humanResource' | 'attendance'
type StatusFilter = ApprovalStatus | 'all'
type CategoryFilter = ApprovalCategory | 'all'

interface WorkflowApplication {
  id: number
  appNo: string
  category: ApprovalCategory
  type: string
  title: string
  applicant: string
  applicantRole?: string
  fields: Record<string, string>
  status: ApprovalStatus
  createdAt: string
  updatedAt?: string
  approvedAt?: string | null
  rejectedAt?: string | null
  withdrawnAt?: string | null
  finishedAt?: string | null
  approver?: string | null
  comment?: string | null
  flowType?: string
  flowName?: string
  currentStep?: number
  currentStepName?: string
  currentHandler?: string
  currentApprovalCount?: number
  approvalRequiredCount?: number
  approvalPassedCount?: number
  approvalProgress?: Array<{ name: string; approved: boolean; time?: string | null; comment?: string | null }>
  resubmitCount?: number
  flowConfig?: Array<{ index: number; name: string; roles: string[]; mode: string; requiredCount: number; approvers?: string[] }>
  timeline?: Array<{ name: string; status: string; time?: string; operator?: string }>
  logs?: Array<{ id: number; action: string; operator: string; operatorRole: string; comment?: string; stepIndex?: number; stepName?: string; approvalRound?: number; createdAt: string }>
}

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const applications = ref<WorkflowApplication[]>([])
const summary = ref<any>({})
const statusFilter = ref<StatusFilter>('pending')
const categoryFilter = ref<CategoryFilter>('all')
const keyword = ref('')
const approvalComment = ref<Record<number, string>>({})
const transferTarget = ref<Record<number, string>>({})
const transferVisible = ref<Record<number, boolean>>({})
const expanded = ref<Record<number, boolean>>({})
const loading = ref(false)

const categories: { key: CategoryFilter; label: string }[] = [
  { key: 'all', label: '全部类型' },
  { key: 'finance', label: '财务申请' },
  { key: 'chain', label: '连锁申请' },
  { key: 'production', label: '生产申请' },
  { key: 'marketDepartment', label: '市场部需求' },
  { key: 'projectDepartment', label: '项目部需求' },
  { key: 'humanResource', label: '人力资源' },
  { key: 'attendance', label: '考勤申请' },
]

const statusTabs: { key: StatusFilter; label: string }[] = [
  { key: 'pending', label: '待审批' },
  { key: 'all', label: '全部记录' },
  { key: 'approved', label: '已通过' },
  { key: 'rejected', label: '已驳回' },
  { key: 'withdrawn', label: '已撤回' },
]

const filteredApplications = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return [...applications.value]
    .sort((a, b) => b.id - a.id)
    .filter(item => statusFilter.value === 'all' || item.status === statusFilter.value)
    .filter(item => categoryFilter.value === 'all' || item.category === categoryFilter.value)
    .filter(item => {
      if (!kw) return true
      return [item.appNo, item.title, item.applicant, categoryLabel(item.category), item.type, ...Object.values(item.fields || {})]
        .filter(Boolean)
        .some(v => String(v).toLowerCase().includes(kw))
    })
})

const pendingCount = computed(() => Number(summary.value.pendingCount || 0))
const approvedCount = computed(() => Number(summary.value.approvedCount || 0))
const rejectedCount = computed(() => Number(summary.value.rejectedCount || 0))
const withdrawnCount = computed(() => Number(summary.value.withdrawnCount || 0))
const totalCount = computed(() => pendingCount.value + approvedCount.value + rejectedCount.value + withdrawnCount.value)
const responseRate = computed(() => totalCount.value ? Math.round((approvedCount.value + rejectedCount.value) / totalCount.value * 100) : 0)
const categoryStats = computed(() => categories.filter(c => c.key !== 'all').map(c => ({
  ...c,
  count: applications.value.filter(i => i.category === c.key).length,
  pending: applications.value.filter(i => i.category === c.key && i.status === 'pending').length,
})).filter(i => i.count > 0 || i.pending > 0))

async function load() {
  loading.value = true
  try {
    const params: Record<string, string> = { page: '1', size: '300' }
    if (statusFilter.value !== 'all') params.status = statusFilter.value
    const [listRes, summaryRes] = await Promise.all([
      fetch(`/api/workflows/applications?${new URLSearchParams(params)}`),
      fetch('/api/workflows/summary'),
    ])
    if (!listRes.ok) throw new Error(await listRes.text())
    if (!summaryRes.ok) throw new Error(await summaryRes.text())
    const listData = await listRes.json()
    applications.value = Array.isArray(listData) ? listData : listData.content || []
    summary.value = await summaryRes.json()
  } catch (e: any) {
    applications.value = []
    emit('alert', `加载审批列表失败：${e.message || e}`, 'error')
  } finally {
    loading.value = false
  }
}

async function act(id: number, action: 'approve' | 'reject') {
  try {
    const url = `/api/workflows/applications/${id}/${action === 'approve' ? 'approve' : 'reject'}`
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        operator: props.currentUser.username,
        operatorRole: props.currentUser.role,
        comment: approvalComment.value[id] || '',
      }),
    })
    if (!res.ok) throw new Error(await res.text())
    const updated = await res.json().catch(() => null)
    approvalComment.value[id] = ''
    await load()
    if (action === 'approve' && updated?.status === 'pending') {
      emit('alert', `已记录你的审批，当前 ${updated.approvalPassedCount || 0}/${updated.approvalRequiredCount || 4} 人通过`, 'success')
    } else {
      emit('alert', action === 'approve' ? '四名审批员已全部通过，申请已批准' : '已驳回申请', 'success')
    }
  } catch (e: any) {
    emit('alert', `审批失败：${e.message || e}`, 'error')
  }
}

async function transfer(id: number) {
  const target = transferTarget.value[id]?.trim()
  if (!target) { emit('alert', '请填写转交对象用户名', 'error'); return }
  try {
    const res = await fetch(`/api/workflows/applications/${id}/transfer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        operator: props.currentUser.username,
        operatorRole: props.currentUser.role,
        target,
        comment: approvalComment.value[id] || '',
      }),
    })
    if (!res.ok) throw new Error(await res.text())
    transferTarget.value[id] = ''
    transferVisible.value[id] = false
    await load()
    emit('alert', '已转交申请', 'success')
  } catch (e: any) {
    emit('alert', `转交失败：${e.message || e}`, 'error')
  }
}

async function withdraw(id: number) {
  try {
    const res = await fetch(`/api/workflows/applications/${id}/withdraw`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ operator: props.currentUser.username, operatorRole: props.currentUser.role, comment: '申请人撤回' }),
    })
    if (!res.ok) throw new Error(await res.text())
    await load()
    emit('alert', '已撤回申请', 'success')
  } catch (e: any) {
    emit('alert', `撤回失败：${e.message || e}`, 'error')
  }
}

async function resubmit(id: number) {
  try {
    const res = await fetch(`/api/workflows/applications/${id}/resubmit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ operator: props.currentUser.username, operatorRole: props.currentUser.role, comment: '修改后重新提交' }),
    })
    if (!res.ok) throw new Error(await res.text())
    await load()
    emit('alert', '已重新提交申请', 'success')
  } catch (e: any) {
    emit('alert', `重新提交失败：${e.message || e}`, 'error')
  }
}

function setStatus(v: StatusFilter) {
  statusFilter.value = v
  load()
}

function statusLabel(status: ApprovalStatus) {
  return status === 'pending' ? '待审批' : status === 'approved' ? '已通过' : status === 'withdrawn' ? '已撤回' : '已驳回'
}

function categoryLabel(category: ApprovalCategory) {
  return category === 'finance' ? '财务申请' : category === 'production' ? '生产申请' : category === 'marketDepartment' ? '市场部需求' : category === 'projectDepartment' ? '项目部需求' : category === 'humanResource' ? '人力资源申请' : category === 'attendance' ? '考勤申请' : '连锁申请'
}

function formatTime(v?: string | null) {
  if (!v) return '—'
  return String(v).replace('T', ' ').slice(0, 16)
}

function canWithdraw(item: WorkflowApplication) {
  return item.status === 'pending' && (item.applicant === props.currentUser.username || props.currentUser.role === 'admin')
}

function canResubmit(item: WorkflowApplication) {
  return ['rejected', 'withdrawn'].includes(item.status) && (item.applicant === props.currentUser.username || props.currentUser.role === 'admin')
}

function flowModeLabel(mode?: string, required?: number) {
  if (mode === 'all') return `会签 · ${required || 1} 人`
  return '或签'
}

function isRequiredApprover(item: WorkflowApplication) {
  return (item.approvalProgress || []).some(p => p.name === props.currentUser.username)
}

function hasCurrentUserApproved(item: WorkflowApplication) {
  return (item.approvalProgress || []).some(p => p.name === props.currentUser.username && p.approved)
}

function canApprove(item: WorkflowApplication) {
  return item.status === 'pending' && isRequiredApprover(item) && !hasCurrentUserApproved(item)
}

function canReject(item: WorkflowApplication) {
  return item.status === 'pending' && (props.currentUser.role === 'admin' || isRequiredApprover(item))
}

function progressHint(item: WorkflowApplication) {
  if (!item.approvalProgress?.length) return ''
  if (hasCurrentUserApproved(item)) return '你已审批，等待其他审批员完成会签。'
  if (!isRequiredApprover(item)) return '当前流程仅审批员1-4可通过审批。'
  return '轮到你处理：通过后仍需其他审批员全部同意。'
}

onMounted(load)
</script>

<template>
  <div class="page approval-page">
    <section class="approval-hero">
      <div>
        <span>APPROVAL CENTER</span>
        <h2>审批中心</h2>
        <p>统一处理财务、连锁、市场、项目、人事、考勤和生产相关申请，审批过程长期留痕。</p>
      </div>
      <button :disabled="loading" @click="load">{{ loading ? '同步中…' : '刷新审批数据' }}</button>
    </section>

    <section class="approval-stats">
      <article class="stat-pending"><small>待审批</small><b>{{ pendingCount }}</b><span>需要及时处理</span></article>
      <article class="stat-approved"><small>已通过</small><b>{{ approvedCount }}</b><span>审批完成</span></article>
      <article class="stat-rejected"><small>已驳回</small><b>{{ rejectedCount }}</b><span>需申请人调整</span></article>
      <article class="stat-rate"><small>响应完成率</small><b>{{ responseRate }}<i>%</i></b><span>通过 + 驳回 / 全部，撤回 {{ withdrawnCount }}</span></article>
    </section>

    <section class="approval-layout">
      <aside class="approval-side">
        <div class="side-card">
          <h3>类型分布</h3>
          <button v-for="c in categories" :key="c.key" :class="{ active: categoryFilter === c.key }" @click="categoryFilter = c.key">
            <span>{{ c.label }}</span>
            <b v-if="c.key === 'all'">{{ applications.length }}</b>
            <b v-else>{{ applications.filter(i => i.category === c.key).length }}</b>
          </button>
        </div>
        <div class="side-card compact">
          <h3>待办提示</h3>
          <p v-if="pendingCount">当前共有 <b>{{ pendingCount }}</b> 条申请等待审批，建议优先处理提交时间较早的记录。</p>
          <p v-else>当前没有待审批申请，审批队列运行良好。</p>
          <div class="category-mini" v-if="categoryStats.length">
            <span v-for="s in categoryStats.slice(0, 5)" :key="s.key">{{ s.label }} · {{ s.pending }} 待办</span>
          </div>
        </div>
      </aside>

      <section class="approval-card">
        <header>
          <div>
            <small>APPLICATION QUEUE</small>
            <h3>申请队列</h3>
            <p>支持按状态、类型、申请人、单号和字段内容快速筛选。</p>
          </div>
          <div class="status-tabs">
            <button v-for="s in statusTabs" :key="s.key" :class="{ active: statusFilter === s.key }" @click="setStatus(s.key)">{{ s.label }}</button>
          </div>
        </header>

        <div class="approval-toolbar">
          <input v-model="keyword" placeholder="搜索单号 / 标题 / 申请人 / 内容" />
          <select v-model="categoryFilter">
            <option v-for="c in categories" :key="c.key" :value="c.key">{{ c.label }}</option>
          </select>
          <button :disabled="loading" @click="load">刷新</button>
        </div>

        <div v-if="!filteredApplications.length" class="empty">
          暂无符合条件的申请。可切换状态、类型或清空搜索条件后重试。
        </div>

        <article v-for="item in filteredApplications" :key="item.id" class="approval-item">
          <div class="item-head">
            <div>
              <span class="category">{{ categoryLabel(item.category) }}</span>
              <h4>{{ item.title }}</h4>
              <p>
                单号：{{ item.appNo || '—' }} · 申请人：{{ item.applicant }} · 提交：{{ formatTime(item.createdAt) }}
                <template v-if="item.flowName"> · 流程：{{ item.flowName }}</template>
                <template v-if="item.currentStepName && item.status === 'pending'"> · 当前：{{ item.currentStepName }}</template>
              </p>
            </div>
            <em :class="item.status">{{ statusLabel(item.status) }}</em>
          </div>

          <div v-if="item.flowConfig?.length" class="flow-strip">
            <span v-for="step in item.flowConfig" :key="step.index" :class="{ active: item.status === 'pending' && item.currentStep === step.index, done: (item.currentStep || 0) > step.index || item.status === 'approved' }">
              <b>{{ step.index + 1 }}</b>
              {{ step.name }}
              <small>{{ flowModeLabel(step.mode, step.requiredCount) }}</small>
            </span>
          </div>

          <div v-if="item.approvalProgress?.length" class="approver-panel">
            <div class="approver-title">
              <span>四人会签进度</span>
              <b>{{ item.approvalPassedCount || 0 }}/{{ item.approvalRequiredCount || item.approvalProgress.length }} 已通过</b>
            </div>
            <div class="approver-grid">
              <div v-for="p in item.approvalProgress" :key="p.name" :class="{ passed: p.approved, current: p.name === currentUser.username }">
                <i>{{ p.approved ? '✓' : '·' }}</i>
                <strong>{{ p.name }}</strong>
                <small>{{ p.approved ? `已通过 · ${formatTime(p.time)}` : '待审批' }}</small>
                <em v-if="p.comment">{{ p.comment }}</em>
              </div>
            </div>
          </div>

          <div class="timeline">
            <div
              v-for="(node, idx) in (item.timeline?.length ? item.timeline : [])"
              :key="idx"
              :class="[node.status, { active: node.status === 'active', done: node.status === 'done', rejected: node.status === 'rejected', withdrawn: node.status === 'withdrawn' }]"
            >
              <i></i><span>{{ node.name }}</span><small>{{ node.operator || node.time || (node.status === 'active' ? '等待处理' : '—') }}</small>
            </div>
          </div>

          <dl :class="{ collapsed: !expanded[item.id] }">
            <template v-for="(v, k) in item.fields" :key="k">
              <dt>{{ k }}</dt>
              <dd>{{ v || '—' }}</dd>
            </template>
          </dl>
          <button v-if="Object.keys(item.fields || {}).length > 4" class="expand-btn" @click="expanded[item.id] = !expanded[item.id]">
            {{ expanded[item.id] ? '收起详情' : '展开全部字段' }}
          </button>

          <div v-if="item.status === 'pending'" class="approve-box">
            <input v-model="approvalComment[item.id]" placeholder="审批意见（选填）" />
            <button class="approve" :disabled="!canApprove(item)" @click="act(item.id, 'approve')">{{ hasCurrentUserApproved(item) ? '已审批' : '批准' }}</button>
            <button class="reject" :disabled="!canReject(item)" @click="act(item.id, 'reject')">驳回</button>
          </div>
          <p v-if="item.status === 'pending' && item.approvalProgress?.length" class="approval-hint">{{ progressHint(item) }}</p>
          <div v-if="item.status === 'pending'" class="secondary-actions">
            <button v-if="!item.approvalProgress?.length" @click="transferVisible[item.id] = !transferVisible[item.id]">转交</button>
            <button v-if="canWithdraw(item)" @click="withdraw(item.id)">撤回</button>
            <span v-if="item.currentHandler">当前处理：{{ item.currentHandler }}</span>
          </div>
          <div v-if="transferVisible[item.id] && !item.approvalProgress?.length" class="transfer-box">
            <input v-model="transferTarget[item.id]" placeholder="输入转交对象用户名，例如 approver01" />
            <button @click="transfer(item.id)">确认转交</button>
          </div>
          <div v-if="canResubmit(item)" class="resubmit-box">
            <span>该申请可修改后重新提交。当前提供快捷重提，保留原表单内容。</span>
            <button @click="resubmit(item.id)">重新提交</button>
          </div>
          <p v-else class="result">
            审批人：{{ item.approver || '—' }} · 完成时间：{{ formatTime(item.finishedAt || item.approvedAt || item.rejectedAt || item.withdrawnAt || item.updatedAt) }}
            <span v-if="item.comment"> · 意见：{{ item.comment }}</span>
          </p>
          <details v-if="item.logs?.length" class="log-panel">
            <summary>查看审批日志（{{ item.logs.length }}）</summary>
            <div v-for="log in item.logs" :key="log.id">
              <b>{{ log.operator }}</b><span>{{ log.action }}</span><small>{{ log.createdAt }}</small><p v-if="log.comment">{{ log.comment }}</p>
            </div>
          </details>
        </article>
      </section>
    </section>
  </div>
</template>

<style scoped>
.approval-page{min-height:100vh}.approval-hero{position:relative;display:flex;justify-content:space-between;gap:24px;align-items:flex-start;padding:34px;border:1px solid rgba(15,23,42,.08);border-radius:30px;background:linear-gradient(125deg,rgba(255,255,255,.98),rgba(255,255,255,.72)),radial-gradient(circle at 92% 0,rgba(20,184,166,.18),transparent 35%);box-shadow:0 24px 68px rgba(15,23,42,.10);overflow:hidden}.approval-hero span,.approval-card header small{font-size:9px;letter-spacing:.18em;color:#0f766e;font-weight:950}.approval-hero h2{margin:8px 0;font-size:36px;font-weight:950;letter-spacing:-.05em;color:#0f172a}.approval-hero p{max-width:760px;margin:0;color:#64748b;line-height:1.75}.approval-hero button,.approval-toolbar button{height:36px;padding:0 14px;border:1px solid rgba(15,23,42,.08);border-radius:999px;background:#fff;color:#0f172a;font-weight:900;cursor:pointer}.approval-stats{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin:18px 0}.approval-stats article{--tone:#0f766e;position:relative;overflow:hidden;padding:20px;border:1px solid rgba(15,23,42,.08);border-radius:22px;background:linear-gradient(145deg,rgba(255,255,255,.96),rgba(255,255,255,.76));box-shadow:0 16px 42px rgba(15,23,42,.08)}.approval-stats article:after{content:"";position:absolute;right:-36px;bottom:-48px;width:120px;height:120px;border-radius:50%;background:color-mix(in srgb,var(--tone) 12%,transparent)}.stat-pending{--tone:#d97706!important}.stat-approved{--tone:#10b981!important}.stat-rejected{--tone:#e11d48!important}.stat-rate{--tone:#2563eb!important}.approval-stats small{display:block;color:#64748b;font-weight:900}.approval-stats b{display:block;margin:10px 0 6px;color:#0f172a;font-size:34px;font-weight:950;letter-spacing:-.06em}.approval-stats i{font-style:normal;font-size:14px;color:#64748b}.approval-stats span{color:#94a3b8;font-size:11px}.approval-layout{display:grid;grid-template-columns:280px minmax(0,1fr);gap:18px}.approval-side{display:grid;gap:14px;align-content:start}.side-card,.approval-card{border:1px solid rgba(15,23,42,.08);border-radius:24px;background:linear-gradient(145deg,rgba(255,255,255,.96),rgba(255,255,255,.76));box-shadow:0 20px 56px rgba(15,23,42,.08);backdrop-filter:blur(18px)}.side-card{padding:18px}.side-card h3{margin:0 0 14px;color:#0f172a;font-size:16px;font-weight:950}.side-card button{width:100%;height:38px;margin-top:7px;display:flex;justify-content:space-between;align-items:center;border:1px solid transparent;border-radius:13px;background:transparent;color:#475569;cursor:pointer;font-weight:800}.side-card button:hover,.side-card button.active{padding:0 10px;background:rgba(204,251,241,.42);border-color:rgba(20,184,166,.18);color:#0f766e}.side-card.compact p{margin:0;color:#64748b;line-height:1.7}.side-card.compact b{color:#0f766e}.category-mini{display:grid;gap:6px;margin-top:14px}.category-mini span{padding:7px 9px;border-radius:999px;background:rgba(248,250,252,.78);color:#64748b;font-size:11px;font-weight:800}.approval-card{padding:22px}.approval-card header{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.approval-card h3{margin:6px 0;color:#0f172a;font-size:22px;font-weight:950}.approval-card p{margin:0;color:#64748b}.status-tabs{display:flex;gap:7px;flex-wrap:wrap;justify-content:flex-end}.status-tabs button{height:34px;padding:0 12px;border:1px solid rgba(15,23,42,.08);border-radius:999px;background:#fff;color:#475569;cursor:pointer;font-size:12px;font-weight:900}.status-tabs button.active{background:linear-gradient(135deg,#0f766e,#14b8a6);color:#fff;border-color:transparent;box-shadow:0 12px 26px rgba(20,184,166,.22)}.approval-toolbar{display:grid;grid-template-columns:1fr 180px auto;gap:10px;margin:18px 0}.approval-toolbar input,.approval-toolbar select{height:38px;border:1px solid rgba(15,23,42,.10);border-radius:14px;background:rgba(255,255,255,.82);padding:0 12px}.empty{padding:32px;border:1px dashed rgba(15,23,42,.14);border-radius:18px;background:rgba(248,250,252,.74);color:#64748b;text-align:center}.approval-item{margin-top:14px;padding:18px;border:1px solid rgba(15,23,42,.08);border-radius:20px;background:rgba(255,255,255,.78);box-shadow:0 12px 34px rgba(15,23,42,.05)}.item-head{display:flex;justify-content:space-between;gap:14px}.item-head h4{margin:7px 0;font-size:18px;font-weight:950;color:#0f172a}.category{font-size:11px;font-weight:950;color:#0f766e}.item-head em{height:max-content;border-radius:999px;padding:6px 10px;font-size:12px;font-style:normal;font-weight:950}.item-head em.pending{background:#fef3c7;color:#b45309}.item-head em.approved{background:#dcfce7;color:#15803d}.item-head em.rejected{background:#fee2e2;color:#b91c1c}.item-head em.withdrawn{background:#e2e8f0;color:#475569}.flow-strip{display:flex;gap:8px;flex-wrap:wrap;margin:14px 0}.flow-strip span{display:inline-flex;align-items:center;gap:7px;padding:8px 10px;border:1px solid rgba(15,23,42,.08);border-radius:999px;background:#f8fafc;color:#64748b;font-size:11px;font-weight:900}.flow-strip b{display:grid;place-items:center;width:18px;height:18px;border-radius:50%;background:#e2e8f0;color:#475569;font-size:10px}.flow-strip small{color:#94a3b8}.flow-strip .active{background:#fef3c7;color:#b45309}.flow-strip .active b{background:#f59e0b;color:#fff}.flow-strip .done{background:#dcfce7;color:#15803d}.flow-strip .done b{background:#10b981;color:#fff}.approver-panel{margin:14px 0;padding:14px;border:1px solid rgba(15,23,42,.08);border-radius:18px;background:linear-gradient(145deg,#f8fafc,#fff)}.approver-title{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:10px}.approver-title span{color:#0f172a;font-size:13px;font-weight:950}.approver-title b{padding:5px 9px;border-radius:999px;background:#ecfdf5;color:#047857;font-size:11px}.approver-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px}.approver-grid div{position:relative;min-height:78px;padding:12px;border:1px solid rgba(15,23,42,.08);border-radius:16px;background:#fff;color:#64748b}.approver-grid div.passed{border-color:rgba(16,185,129,.24);background:linear-gradient(145deg,#ecfdf5,#fff)}.approver-grid div.current{box-shadow:0 0 0 3px rgba(20,184,166,.10)}.approver-grid i{display:grid;place-items:center;width:22px;height:22px;border-radius:50%;background:#e2e8f0;color:#64748b;font-style:normal;font-weight:950}.approver-grid .passed i{background:#10b981;color:#fff}.approver-grid strong{display:block;margin-top:8px;color:#0f172a;font-size:13px}.approver-grid small{display:block;margin-top:3px;font-size:10px}.approver-grid em{display:block;margin-top:6px;color:#0f766e;font-size:11px;font-style:normal;word-break:break-word}.timeline{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin:14px 0}.timeline div{position:relative;padding:12px 12px 12px 34px;border:1px solid rgba(15,23,42,.08);border-radius:16px;background:#f8fafc}.timeline i{position:absolute;left:13px;top:16px;width:9px;height:9px;border-radius:50%;background:#cbd5e1}.timeline .done i{background:#10b981;box-shadow:0 0 0 5px rgba(16,185,129,.10)}.timeline .active i{background:#f59e0b;box-shadow:0 0 0 5px rgba(245,158,11,.12)}.timeline .rejected i{background:#e11d48;box-shadow:0 0 0 5px rgba(225,29,72,.10)}.timeline .withdrawn i{background:#64748b;box-shadow:0 0 0 5px rgba(100,116,139,.10)}.timeline span{display:block;color:#0f172a;font-size:12px;font-weight:950}.timeline small{display:block;margin-top:5px;color:#94a3b8;font-size:10px}dl{display:grid;grid-template-columns:150px 1fr;gap:8px 12px;margin:14px 0 8px;padding:14px;border-radius:16px;background:#f8fafc;max-height:none;overflow:hidden}dl.collapsed{max-height:154px}dt{color:#475569;font-weight:950}dd{margin:0;color:#0f172a;word-break:break-word}.expand-btn{border:0;background:transparent;color:#0f766e;font-weight:900;cursor:pointer}.approve-box,.transfer-box{display:grid;grid-template-columns:1fr auto auto;gap:10px;margin-top:14px}.approve-box input,.transfer-box input{border:1px solid rgba(15,23,42,.10);border-radius:14px;padding:0 12px;height:38px}.approve-box button,.transfer-box button,.resubmit-box button{border:0;border-radius:999px;padding:0 16px;color:#fff;font-weight:950;cursor:pointer}.approve-box button:disabled{cursor:not-allowed;opacity:.48;filter:grayscale(.3)}.approve{background:#16a34a}.reject{background:#dc2626}.approval-hint{margin:8px 0 0!important;color:#64748b!important;font-size:12px}.secondary-actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-top:10px}.secondary-actions button{height:30px;padding:0 12px;border:1px solid rgba(15,23,42,.08);border-radius:999px;background:#fff;color:#475569;font-weight:900;cursor:pointer}.secondary-actions span{color:#94a3b8;font-size:12px}.transfer-box{grid-template-columns:1fr auto}.transfer-box button{background:#2563eb}.resubmit-box{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-top:12px;padding:12px;border-radius:16px;background:#fff7ed;color:#9a3412;font-size:12px}.resubmit-box button{height:32px;background:#d97706;white-space:nowrap}.result{margin-top:12px;font-size:13px;color:#64748b}.log-panel{margin-top:12px;padding:10px 12px;border-radius:16px;background:#f8fafc}.log-panel summary{cursor:pointer;color:#0f766e;font-size:12px;font-weight:950}.log-panel div{display:grid;grid-template-columns:90px 90px 130px 1fr;gap:8px;margin-top:9px;color:#64748b;font-size:12px}.log-panel b{color:#0f172a}.log-panel p{margin:0;color:#475569}@media(max-width:1100px){.approval-layout{grid-template-columns:1fr}.approval-stats{grid-template-columns:repeat(2,1fr)}.approver-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:760px){.approval-hero,.approval-card header,.item-head{display:block}.approval-stats,.timeline,.approver-grid{grid-template-columns:1fr}.approval-toolbar,.approve-box,.transfer-box{grid-template-columns:1fr}dl{grid-template-columns:1fr}.log-panel div{grid-template-columns:1fr}}
</style>
