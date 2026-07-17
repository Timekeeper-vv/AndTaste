<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { User } from '../types'

type Row = Record<string, any>

const props = defineProps<{ currentUser?: User | null }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const loading = ref(false)
const saving = ref(false)
const rows = ref<Row[]>([])
const options = ref<any>({ statuses: [], owners: [], orderTypes: [], productTypes: [] })
const keyword = ref('')
const status = ref('')
const editingId = ref<number | null>(null)
const showForm = ref(false)
const detail = ref<Row | null>(null)

const form = reactive<any>({
  applicationDepartment: '项目服务部',
  applicant: '',
  projectName: '',
  productName: '',
  orderType: '分账',
  productType: '百货',
  productSubType: '',
  sampleQuantityText: '2',
  specFlavor: '常规',
  sampleFeeYuan: '',
  detailRemark: '',
  attachmentSummary: '',
  startDate: '',
  estimatedCompleteDate: '',
  actualCompleteDate: '',
  owner: '',
  factory: '',
  sampleCostYuan: '',
  sampleFileProvidedDate: '',
})

function resetForm() {
  editingId.value = null
  Object.assign(form, {
    applicationDepartment: '项目服务部', applicant: props.currentUser?.username || '', projectName: '', productName: '', orderType: '分账', productType: '百货', productSubType: '', sampleQuantityText: '2', specFlavor: '常规', sampleFeeYuan: '', detailRemark: '', attachmentSummary: '', startDate: '', estimatedCompleteDate: '', actualCompleteDate: '', owner: '', factory: '', sampleCostYuan: '', sampleFileProvidedDate: ''
  })
}

function openCreate() { resetForm(); showForm.value = true }
function editRow(r: Row) {
  editingId.value = r.id
  Object.assign(form, {
    applicationDepartment: r.applicationDepartment || '项目服务部', applicant: r.applicant || props.currentUser?.username || '', projectName: r.projectName || '', productName: r.productName || '', orderType: r.orderType || '分账', productType: r.productType || '百货', productSubType: r.productSubType || '', sampleQuantityText: r.sampleQuantityText || '', specFlavor: r.specFlavor || '', sampleFeeYuan: r.sampleFeeYuan || '', detailRemark: r.detailRemark || '', attachmentSummary: r.attachmentSummary || '', startDate: dateOnly(r.startDate), estimatedCompleteDate: dateOnly(r.estimatedCompleteDate), actualCompleteDate: dateOnly(r.actualCompleteDate), owner: r.owner || '', factory: r.factory || '', sampleCostYuan: r.sampleCostYuan || '', sampleFileProvidedDate: dateOnly(r.sampleFileProvidedDate)
  })
  showForm.value = true
}

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({ page: '1', size: '500' })
    if (keyword.value.trim()) params.set('keyword', keyword.value.trim())
    if (status.value) params.set('status', status.value)
    const [listRes, optRes] = await Promise.all([
      fetch(`/api/supply-chain/sample-work-orders?${params}`),
      fetch('/api/supply-chain/sample-work-orders/options')
    ])
    const list = await listRes.json()
    rows.value = Array.isArray(list.items) ? list.items : []
    options.value = await optRes.json()
  } catch (e: any) {
    emit('alert', '加载打样申请失败：' + (e.message || e), 'error')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!String(form.productName || '').trim()) return emit('alert', '请填写产品名称', 'error')
  saving.value = true
  try {
    const body = { ...form, applicant: form.applicant || props.currentUser?.username || '当前用户', updatedBy: props.currentUser?.username || '当前用户', initiator: props.currentUser?.username || form.applicant || '当前用户' }
    const url = editingId.value ? `/api/supply-chain/sample-work-orders/${editingId.value}` : '/api/supply-chain/sample-work-orders'
    const res = await fetch(url, { method: editingId.value ? 'PUT' : 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    if (!res.ok) throw new Error(await res.text())
    showForm.value = false
    await load()
    emit('alert', editingId.value ? '打样单已更新' : '打样单已新增', 'success')
  } catch (e: any) {
    emit('alert', '保存失败：' + (e.message || e), 'error')
  } finally {
    saving.value = false
  }
}

async function submitApproval(r: Row) {
  try {
    const res = await fetch(`/api/supply-chain/sample-work-orders/${r.id}/submit-approval`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ applicant: props.currentUser?.username || r.applicant || '当前用户', applicantRole: props.currentUser?.role || 'feeder' }) })
    if (!res.ok) throw new Error(await res.text())
    await load()
    emit('alert', '已提交审批中心，审批通过后工单状态会自动变为“待打样”', 'success')
  } catch (e: any) { emit('alert', '提交审批失败：' + (e.message || e), 'error') }
}

async function updateStatus(r: Row) {
  try {
    const res = await fetch(`/api/supply-chain/sample-work-orders/${r.id}/work-status`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...r, updatedBy: props.currentUser?.username || '当前用户' }) })
    if (!res.ok) throw new Error(await res.text())
    await load()
    emit('alert', '工单状态已更新', 'success')
  } catch (e: any) { emit('alert', '更新状态失败：' + (e.message || e), 'error') }
}

async function removeRow(r: Row) {
  if (!confirm(`确定删除打样单：${r.productName}？删除后列表不再显示，但后台仍保留审计痕迹。`)) return
  try {
    const res = await fetch(`/api/supply-chain/sample-work-orders/${r.id}`, { method: 'DELETE', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ operator: props.currentUser?.username || '当前用户' }) })
    if (!res.ok) throw new Error(await res.text())
    await load()
    emit('alert', '已删除打样单', 'success')
  } catch (e: any) { emit('alert', '删除失败：' + (e.message || e), 'error') }
}

async function openDetail(r: Row) {
  const res = await fetch(`/api/supply-chain/sample-work-orders/${r.id}`)
  detail.value = await res.json()
}

function approvalText(s: string) { return s === '审批中' ? '审批中' : s === '已通过' ? '已通过' : s === '已驳回' ? '已驳回' : s || '草稿' }
function dateOnly(v: any) { return v ? String(v).slice(0, 10) : '' }
function val(v: any) { return v === null || v === undefined || v === '' ? '-' : String(v).replace('T', ' ') }
function canSubmit(r: Row) { return !['审批中', '已通过'].includes(r.approvalStatus) }

onMounted(() => { resetForm(); load() })
</script>

<template>
  <div class="sample-apply-page">
    <section class="hero">
      <div>
        <p>PRODUCTION SAMPLE REQUEST</p>
        <h2>打样申请</h2>
        <span>新增、编辑、删除打样单；提交后进入审批中心，审批通过后自动进入打样工单流转。</span>
      </div>
      <button class="primary" @click="openCreate">新增打样申请</button>
    </section>

    <section class="toolbar">
      <input v-model="keyword" @keyup.enter="load" placeholder="搜索申请编号 / 项目 / 产品 / 负责人 / SourceID" />
      <select v-model="status"><option value="">全部工单状态</option><option v-for="x in options.statuses" :key="x">{{ x }}</option></select>
      <button @click="load" :disabled="loading">查询</button>
    </section>

    <section class="table-card">
      <header><b>打样申请与工单状态</b><small>共 {{ rows.length }} 条；状态可直接修改，审批结果由审批中心自动回写。</small></header>
      <div class="table-scroll">
        <table>
          <thead><tr><th>申请编号</th><th>项目/产品</th><th>类型</th><th>数量/规格</th><th>审批</th><th>工单状态</th><th>时间</th><th>负责人/工厂</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="r in rows" :key="r.id">
              <td><b>{{ r.applicationNo }}</b><small>{{ r.sourceKind === 'manual' ? '系统新增' : 'Excel导入' }}</small></td>
              <td><b>{{ val(r.projectName) }}</b><small>{{ r.productName }}</small></td>
              <td>{{ val(r.orderType) }} / {{ val(r.productType) }} / {{ val(r.productSubType) }}</td>
              <td>{{ val(r.sampleQuantityText) }}<small>{{ val(r.specFlavor) }}</small></td>
              <td><span class="approval" :class="r.approvalStatus">{{ approvalText(r.approvalStatus) }}</span></td>
              <td><select v-model="r.workOrderStatus" @change="updateStatus(r)"><option>草稿</option><option>待审批</option><option>待打样</option><option>进行中</option><option>已完成</option><option>延期完成</option><option>项目暂停</option><option>审批驳回</option></select></td>
              <td><small>开始 {{ val(r.startDate) }}</small><small>预计 {{ val(r.estimatedCompleteDate) }}</small><small>实际 {{ val(r.actualCompleteDate) }}</small></td>
              <td>{{ val(r.owner) }}<small>{{ val(r.factory) }}</small></td>
              <td class="ops">
                <button @click="editRow(r)">编辑</button>
                <button :disabled="!canSubmit(r)" @click="submitApproval(r)">提交审批</button>
                <button @click="openDetail(r)">详情</button>
                <button class="danger" @click="removeRow(r)">删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!rows.length" class="empty">暂无打样申请</div>
    </section>

    <div v-if="showForm" class="modal-mask" @click.self="showForm=false">
      <form class="modal" @submit.prevent="save">
        <div class="modal-head"><div><small>{{ editingId ? '编辑打样单' : '新增打样申请' }}</small><h3>{{ form.productName || '未命名打样申请' }}</h3></div><button type="button" @click="showForm=false">×</button></div>
        <div class="form-grid">
          <label>申请人<input v-model="form.applicant" /></label>
          <label>申请部门<input v-model="form.applicationDepartment" /></label>
          <label>项目名称<input v-model="form.projectName" /></label>
          <label>产品名称<input v-model="form.productName" required /></label>
          <label>订单类型<select v-model="form.orderType"><option>分账</option><option>采购</option></select></label>
          <label>产品类型<input v-model="form.productType" /></label>
          <label>二级类型<input v-model="form.productSubType" /></label>
          <label>打样数量<input v-model="form.sampleQuantityText" /></label>
          <label>规格/口味<input v-model="form.specFlavor" /></label>
          <label>打样费<input v-model="form.sampleFeeYuan" type="number" step="0.01" /></label>
          <label>附件说明<input v-model="form.attachmentSummary" placeholder="如 1 个附件" /></label>
          <label>负责人<input v-model="form.owner" /></label>
          <label>开始时间<input v-model="form.startDate" type="date" /></label>
          <label>预计完成<input v-model="form.estimatedCompleteDate" type="date" /></label>
          <label>实际完成<input v-model="form.actualCompleteDate" type="date" /></label>
          <label>工厂<input v-model="form.factory" /></label>
          <label>打样费用<input v-model="form.sampleCostYuan" type="number" step="0.01" /></label>
          <label>文件提供日期<input v-model="form.sampleFileProvidedDate" type="date" /></label>
          <label class="wide">备注<textarea v-model="form.detailRemark" rows="3" /></label>
        </div>
        <div class="modal-actions"><button type="button" @click="showForm=false">取消</button><button class="primary" :disabled="saving">保存</button></div>
      </form>
    </div>

    <div v-if="detail" class="modal-mask" @click.self="detail=null">
      <div class="modal detail-modal">
        <div class="modal-head"><div><small>打样单详情</small><h3>{{ detail.applicationNo }} · {{ detail.productName }}</h3></div><button @click="detail=null">×</button></div>
        <div class="detail-grid">
          <div v-for="(v,k) in detail" :key="String(k)"><small>{{ k }}</small><b>{{ val(v) }}</b></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sample-apply-page{padding:24px;background:#f6f8fb;min-height:100vh}.hero{display:flex;justify-content:space-between;gap:16px;align-items:center;padding:26px;border-radius:20px;background:linear-gradient(135deg,#111827,#7c2d12,#0f766e);color:#fff}.hero p{font-size:11px;letter-spacing:1.6px;color:#fed7aa}.hero h2{margin:6px 0}.hero span{opacity:.86}.primary{background:#0f766e!important;color:#fff!important}.toolbar{display:grid;grid-template-columns:1fr 180px auto;gap:10px;margin:16px 0;padding:14px;background:#fff;border:1px solid #e2e8f0;border-radius:16px}.toolbar input,.toolbar select,.toolbar button,.form-grid input,.form-grid select,.form-grid textarea,td select{border:1px solid #cbd5e1;border-radius:9px;padding:9px;background:#fff}.toolbar button,button{border:0;border-radius:9px;padding:9px 12px;cursor:pointer;font-weight:800}.table-card{background:#fff;border:1px solid #e2e8f0;border-radius:18px;overflow:hidden}.table-card header{display:flex;justify-content:space-between;padding:16px;border-bottom:1px solid #e2e8f0}.table-card small,td small{display:block;color:#64748b;font-size:12px;margin-top:3px}.table-scroll{overflow:auto}table{width:100%;border-collapse:collapse;min-width:1180px}th,td{padding:12px;border-bottom:1px solid #eef2f7;text-align:left;font-size:13px;vertical-align:top}th{background:#f8fafc;color:#475569}.approval{display:inline-block;padding:5px 9px;border-radius:999px;background:#f1f5f9;color:#475569;font-size:12px;font-weight:900}.approval.审批中{background:#fff7ed;color:#c2410c}.approval.已通过{background:#dcfce7;color:#15803d}.approval.已驳回{background:#fee2e2;color:#b91c1c}.ops{display:flex;gap:6px;flex-wrap:wrap}.ops button{background:#eef2ff;color:#4338ca}.ops button:disabled{opacity:.45;cursor:not-allowed}.ops .danger{background:#fef2f2;color:#dc2626}.empty{padding:32px;text-align:center;color:#94a3b8}.modal-mask{position:fixed;inset:0;background:rgba(15,23,42,.62);z-index:1000;display:flex;align-items:center;justify-content:center;padding:22px}.modal{width:min(980px,96vw);max-height:92vh;overflow:auto;background:#fff;border-radius:20px;padding:20px;box-shadow:0 25px 70px rgba(0,0,0,.3)}.modal-head{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #e2e8f0;padding-bottom:12px;margin-bottom:14px}.modal-head h3{margin:4px 0}.modal-head>button{width:34px;height:34px;border-radius:50%;background:#f1f5f9;font-size:20px}.form-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.form-grid label{font-size:12px;font-weight:800;color:#475569}.form-grid input,.form-grid select,.form-grid textarea{display:block;width:100%;box-sizing:border-box;margin-top:6px}.wide{grid-column:1/-1}.modal-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:16px}.modal-actions button{background:#f1f5f9}.detail-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.detail-grid div{background:#f8fafc;border:1px solid #e2e8f0;border-radius:10px;padding:10px;word-break:break-all}.detail-grid small{display:block;color:#64748b}.detail-grid b{font-size:12px;color:#0f172a}@media(max-width:900px){.toolbar,.form-grid,.detail-grid{grid-template-columns:1fr}.hero{flex-direction:column;align-items:flex-start}}
</style>
