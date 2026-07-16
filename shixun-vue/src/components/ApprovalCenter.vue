<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'

type ApprovalStatus = 'pending' | 'approved' | 'rejected'
type ApprovalCategory = 'finance' | 'chain' | 'production' | 'marketDepartment' | 'projectDepartment' | 'humanResource' | 'attendance'

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
  approver?: string | null
  comment?: string | null
}

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const applications = ref<WorkflowApplication[]>([])
const summary = ref<any>({})
const filter = ref<'pending' | 'all'>('pending')
const approvalComment = ref<Record<number, string>>({})
const loading = ref(false)

const visibleApplications = computed(() => {
  const list = [...applications.value].sort((a, b) => b.id - a.id)
  return filter.value === 'pending' ? list.filter(x => x.status === 'pending') : list
})
const pendingCount = computed(() => Number(summary.value.pendingCount || 0))
const approvedCount = computed(() => Number(summary.value.approvedCount || 0))
const rejectedCount = computed(() => Number(summary.value.rejectedCount || 0))

async function load() {
  loading.value = true
  try {
    const [listRes, summaryRes] = await Promise.all([
      fetch(`/api/workflows/applications?${new URLSearchParams({ status: filter.value === 'pending' ? 'pending' : '', page: '1', size: '200' })}`),
      fetch('/api/workflows/summary'),
    ])
    const listData = await listRes.json()
    applications.value = Array.isArray(listData) ? listData : listData.content || []
    summary.value = await summaryRes.json()
  } catch {
    applications.value = []
    emit('alert', '加载审批列表失败', 'error')
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
    await load()
    emit('alert', action === 'approve' ? '已批准申请' : '已驳回申请', 'success')
  } catch (e: any) {
    emit('alert', `审批失败：${e.message || e}`, 'error')
  }
}

function statusLabel(status: ApprovalStatus) {
  return status === 'pending' ? '待审批' : status === 'approved' ? '已通过' : '已驳回'
}

function categoryLabel(category: ApprovalCategory) {
  return category === 'finance' ? '财务申请' : category === 'production' ? '生产申请' : category === 'marketDepartment' ? '市场部需求' : category === 'projectDepartment' ? '项目部需求' : category === 'humanResource' ? '人力资源申请' : category === 'attendance' ? '考勤申请' : '连锁申请'
}

onMounted(load)
</script>

<template>
  <div class="approval-page">
    <section class="approval-hero">
      <span>APPROVAL CENTER</span>
      <h2>审批中心</h2>
      <p>超级管理员和审批主管在这里统一处理员工提交的财务、连锁、市场部、项目部、人力资源、考勤和业务申请。</p>
    </section>

    <section class="approval-stats">
      <article><small>待审批</small><b>{{ pendingCount }}</b></article>
      <article><small>已通过</small><b>{{ approvedCount }}</b></article>
      <article><small>已驳回</small><b>{{ rejectedCount }}</b></article>
    </section>

    <section class="approval-card">
      <header>
        <div>
          <h3>申请列表</h3>
          <p>员工提交申请后，会自动进入待审批列表。</p>
        </div>
        <div class="actions">
          <button :class="{active:filter==='pending'}" @click="filter='pending'; load()">只看待审批</button>
          <button :class="{active:filter==='all'}" @click="filter='all'; load()">全部记录</button>
          <button :disabled="loading" @click="load">刷新</button>
        </div>
      </header>

      <div v-if="!visibleApplications.length" class="empty">
        暂无{{ filter === 'pending' ? '待审批' : '' }}申请。请先到“财务管理”“之间连锁”“市场部需求管理”“项目部需求管理”“人力资源管理”或“考勤管理”里提交一条申请。
      </div>

      <article v-for="item in visibleApplications" :key="item.id" class="approval-item">
        <div class="item-head">
          <div>
            <span class="category">{{ categoryLabel(item.category) }}</span>
            <h4>{{ item.title }}</h4>
            <p>申请人：{{ item.applicant }} · 提交时间：{{ item.createdAt }}</p>
          </div>
          <em :class="item.status">{{ statusLabel(item.status) }}</em>
        </div>

        <dl>
          <template v-for="(v, k) in item.fields" :key="k">
            <dt>{{ k }}</dt>
            <dd>{{ v || '—' }}</dd>
          </template>
        </dl>

        <div v-if="item.status === 'pending'" class="approve-box">
          <input v-model="approvalComment[item.id]" placeholder="审批意见（选填）" />
          <button class="approve" @click="act(item.id, 'approve')">批准申请</button>
          <button class="reject" @click="act(item.id, 'reject')">驳回申请</button>
        </div>
        <p v-else class="result">
          审批人：{{ item.approver || '—' }} · 审批时间：{{ item.approvedAt || item.rejectedAt || item.updatedAt || '—' }}
          <span v-if="item.comment"> · 意见：{{ item.comment }}</span>
        </p>
      </article>
    </section>
  </div>
</template>

<style scoped>
.approval-page{min-height:100vh;background:#f6f8fb}.approval-hero{padding:34px;border-radius:22px;background:linear-gradient(120deg,#111827,#581c87 62%,#991b1b);color:#fff;box-shadow:0 18px 45px rgba(15,23,42,.18)}.approval-hero span{font-size:10px;letter-spacing:2px;color:#fecaca;font-weight:900}.approval-hero h2{margin:8px 0;font-size:32px}.approval-hero p{margin:0;color:#f8fafc}.approval-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:18px}.approval-stats article{padding:18px;border:1px solid #e2e8f0;border-radius:16px;background:#fff}.approval-stats small{display:block;color:#64748b;font-weight:800}.approval-stats b{font-size:30px;color:#0f172a}.approval-card{margin-top:18px;padding:22px;border:1px solid #e2e8f0;border-radius:20px;background:#fff}.approval-card header{display:flex;justify-content:space-between;gap:16px;align-items:center}.approval-card h3{margin:0}.approval-card p{color:#64748b}.actions{display:flex;gap:8px;flex-wrap:wrap}.actions button{border:1px solid #cbd5e1;border-radius:10px;background:#fff;padding:9px 12px;cursor:pointer;font-weight:800}.actions button.active{background:#0f172a;color:#fff;border-color:#0f172a}.actions button:disabled{opacity:.5;cursor:not-allowed}.empty{padding:28px;border:1px dashed #cbd5e1;border-radius:16px;background:#f8fafc;color:#64748b;text-align:center}.approval-item{margin-top:14px;padding:18px;border:1px solid #e2e8f0;border-radius:16px;background:#fbfdff}.item-head{display:flex;justify-content:space-between;gap:12px}.item-head h4{margin:6px 0;font-size:18px}.category{font-size:11px;font-weight:900;color:#7c3aed}.item-head em{height:max-content;border-radius:999px;padding:5px 10px;font-size:12px;font-style:normal;font-weight:900}.item-head em.pending{background:#fef3c7;color:#b45309}.item-head em.approved{background:#dcfce7;color:#15803d}.item-head em.rejected{background:#fee2e2;color:#b91c1c}dl{display:grid;grid-template-columns:140px 1fr;gap:8px 12px;margin:14px 0;padding:14px;border-radius:12px;background:#f8fafc}dt{color:#475569;font-weight:900}dd{margin:0;color:#0f172a}.approve-box{display:grid;grid-template-columns:1fr auto auto;gap:10px}.approve-box input{border:1px solid #cbd5e1;border-radius:10px;padding:10px}.approve-box button{border:0;border-radius:10px;padding:10px 14px;color:#fff;font-weight:900;cursor:pointer}.approve{background:#16a34a}.reject{background:#dc2626}.result{margin-bottom:0;font-size:13px}@media(max-width:900px){.approval-stats{grid-template-columns:1fr}.approval-card header,.item-head{display:block}.approve-box{grid-template-columns:1fr}dl{grid-template-columns:1fr}}
</style>
