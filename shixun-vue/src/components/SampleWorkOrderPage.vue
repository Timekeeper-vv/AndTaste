<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type WorkOrder = Record<string, any>

const loading = ref(false)
const items = ref<WorkOrder[]>([])
const stats = ref<any>({ verify: {} })
const options = ref<any>({ statuses: [], owners: [], orderTypes: [], productTypes: [] })
const keyword = ref('')
const status = ref('')
const owner = ref('')
const orderType = ref('')
const productType = ref('')
const selected = ref<WorkOrder | null>(null)
const detailLoading = ref(false)

const total = computed(() => Number(stats.value?.verify?.activeRows || stats.value?.verify?.importedRows || items.value.length || 0))
const statusCount = (name: string) => Number((stats.value?.status || []).find((x: any) => x.name === name)?.count || 0)
const doingCount = computed(() => statusCount('进行中'))
const doneCount = computed(() => statusCount('已完成'))
const pendingCount = computed(() => statusCount('待审批') + statusCount('待打样') + statusCount('草稿'))
const delayedCount = computed(() => statusCount('延期完成'))

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({ page: '1', size: '500' })
    if (keyword.value.trim()) params.set('keyword', keyword.value.trim())
    if (status.value) params.set('status', status.value)
    if (owner.value) params.set('owner', owner.value)
    if (orderType.value) params.set('orderType', orderType.value)
    if (productType.value) params.set('productType', productType.value)
    const [listRes, statsRes, optRes] = await Promise.all([
      fetch(`/api/supply-chain/sample-work-orders?${params}`),
      fetch('/api/supply-chain/sample-work-orders/stats'),
      fetch('/api/supply-chain/sample-work-orders/options')
    ])
    const list = await listRes.json()
    items.value = Array.isArray(list.items) ? list.items : []
    stats.value = await statsRes.json()
    options.value = await optRes.json()
  } finally {
    loading.value = false
  }
}

async function openDetail(row: WorkOrder) {
  detailLoading.value = true
  try {
    const res = await fetch(`/api/supply-chain/sample-work-orders/${row.id}`)
    selected.value = await res.json()
  } finally {
    detailLoading.value = false
  }
}

function val(v: any) {
  if (v === null || v === undefined || v === '') return '-'
  return String(v).replace('T', ' ')
}

function exportCsv() {
  const headers = ['申请编号','申请状态','发起时间','发起人','申请部门','项目名称','产品名称','订单类型','产品类型','二级类型','打样数量','规格/口味','附件','工单状态','开始时间','预计完成','实际完成','负责人','工厂']
  const rows = items.value.map(x => [x.applicationNo,x.approvalStatus,x.initiatedAt,x.initiator,x.applicationDepartment,x.projectName,x.productName,x.orderType,x.productType,x.productSubType,x.sampleQuantityText,x.specFlavor,x.attachmentSummary,x.workOrderStatus,x.startDate,x.estimatedCompleteDate,x.actualCompleteDate,x.owner,x.factory])
  const csv = [headers, ...rows].map(r => r.map(v => `"${val(v).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '打样工单明细.csv'
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<template>
  <div class="sample-page">
    <section class="hero">
      <div>
        <p>SAMPLE WORK ORDER BOARD</p>
        <h2>打样工单明细</h2>
        <span>统一查看所有打样任务的进度、负责人、计划时间和完成情况，支持按项目、产品、负责人快速查询。</span>
      </div>
      <div class="hero-actions">
        <button class="btn btn-secondary" @click="load" :disabled="loading">刷新数据</button>
        <button class="btn btn-primary" @click="exportCsv">导出明细</button>
      </div>
    </section>

    <section class="verify">
      <article><small>全部打样任务</small><b>{{ total }}</b><em>当前系统有效工单</em></article>
      <article><small>进行中</small><b>{{ doingCount }}</b><em>需要持续跟进</em></article>
      <article><small>已完成</small><b>{{ doneCount }}</b><em>已完成打样</em></article>
      <article><small>待处理</small><b>{{ pendingCount }}</b><em>草稿、待审批或待打样</em></article>
      <article><small>延期完成</small><b>{{ delayedCount }}</b><em>需复盘原因</em></article>
    </section>

    <section class="stat-grid">
      <article v-for="s in (stats.status || [])" :key="s.name"><small>工单状态</small><b>{{ s.name }}</b><em>{{ s.count }} 条</em></article>
      <article v-for="s in (stats.orderType || []).slice(0,3)" :key="'t'+s.name"><small>订单类型</small><b>{{ s.name }}</b><em>{{ s.count }} 条</em></article>
      <article v-for="s in (stats.owner || []).slice(0,3)" :key="'o'+s.name"><small>负责人</small><b>{{ s.name }}</b><em>{{ s.count }} 条</em></article>
    </section>

    <section class="toolbar-card">
      <input v-model="keyword" @keyup.enter="load" placeholder="搜索：申请编号 / 项目 / 产品 / 负责人" />
      <select v-model="status"><option value="">全部状态</option><option v-for="x in options.statuses" :key="x">{{ x }}</option></select>
      <select v-model="owner"><option value="">全部负责人</option><option v-for="x in options.owners" :key="x">{{ x }}</option></select>
      <select v-model="orderType"><option value="">全部订单类型</option><option v-for="x in options.orderTypes" :key="x">{{ x }}</option></select>
      <select v-model="productType"><option value="">全部产品类型</option><option v-for="x in options.productTypes" :key="x">{{ x }}</option></select>
      <button class="btn btn-primary" @click="load" :disabled="loading">查询</button>
    </section>

    <section class="table-card">
      <div class="table-head"><b>当前结果：{{ items.length }} 条</b><span>点击详情查看完整打样信息。</span></div>
      <div class="table-scroll">
        <table>
          <thead><tr><th>申请编号</th><th>项目</th><th>产品名称</th><th>订单/产品类型</th><th>数量</th><th>规格/口味</th><th>状态</th><th>负责人</th><th>开始/预计/实际</th><th>附件</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="r in items" :key="r.id">
              <td><b>{{ r.applicationNo }}</b><small>{{ val(r.initiatedAt) }}</small></td>
              <td>{{ val(r.projectName || r.detailProjectName) }}</td>
              <td class="product">{{ r.productName }}</td>
              <td>{{ r.orderType }} / {{ r.productType }} / {{ r.productSubType }}</td>
              <td>{{ r.sampleQuantityText }}</td>
              <td>{{ r.specFlavor }}</td>
              <td><span class="pill" :class="r.workOrderStatus==='已完成'?'done':'doing'">{{ val(r.workOrderStatus) }}</span></td>
              <td>{{ val(r.owner) }}</td>
              <td><small>{{ val(r.startDate) }} / {{ val(r.estimatedCompleteDate) }} / {{ val(r.actualCompleteDate) }}</small></td>
              <td>{{ r.attachmentSummary }}</td>
              <td><button class="mini" @click="openDetail(r)">查看详情</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!items.length" class="empty">暂无数据</div>
    </section>

    <div v-if="selected || detailLoading" class="modal-mask" @click.self="selected=null">
      <div class="modal">
        <div class="modal-head"><div><small>打样工单详情</small><h3>{{ selected?.applicationNo }} · {{ selected?.productName }}</h3></div><button @click="selected=null">×</button></div>
        <div class="detail-grid">
          <div><small>申请状态</small><b>{{ val(selected?.approvalStatus) }}</b></div>
          <div><small>工单状态</small><b>{{ val(selected?.workOrderStatus) }}</b></div>
          <div><small>申请部门</small><b>{{ val(selected?.applicationDepartment) }}</b></div>
          <div><small>申请人</small><b>{{ val(selected?.applicant) }}</b></div>
          <div><small>项目名称</small><b>{{ val(selected?.projectName || selected?.detailProjectName) }}</b></div>
          <div><small>产品名称</small><b>{{ val(selected?.productName) }}</b></div>
          <div><small>订单类型</small><b>{{ val(selected?.orderType) }}</b></div>
          <div><small>产品类型</small><b>{{ val(selected?.productType) }} / {{ val(selected?.productSubType) }}</b></div>
          <div><small>打样数量</small><b>{{ val(selected?.sampleQuantityText) }}</b></div>
          <div><small>规格/口味</small><b>{{ val(selected?.specFlavor) }}</b></div>
          <div><small>打样费</small><b>{{ val(selected?.sampleFeeYuan) }}</b></div>
          <div><small>负责人</small><b>{{ val(selected?.owner) }}</b></div>
          <div><small>工厂</small><b>{{ val(selected?.factory) }}</b></div>
          <div><small>开始时间</small><b>{{ val(selected?.startDate) }}</b></div>
          <div><small>预计完成</small><b>{{ val(selected?.estimatedCompleteDate) }}</b></div>
          <div><small>实际完成</small><b>{{ val(selected?.actualCompleteDate) }}</b></div>
          <div class="wide"><small>备注</small><b>{{ val(selected?.detailRemark || selected?.factory) }}</b></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sample-page{padding:24px;background:#f8fafc;min-height:100vh}.hero{display:flex;justify-content:space-between;gap:16px;padding:26px;border-radius:20px;background:linear-gradient(135deg,#0f172a,#0f766e);color:#fff}.hero p{font-size:11px;letter-spacing:1.6px;color:#ccfbf1}.hero h2{margin:6px 0}.hero span{opacity:.85}.hero-actions{display:flex;gap:10px;align-items:flex-start}.verify,.stat-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px;margin:16px 0}.verify article,.stat-grid article{background:#fff;border:1px solid #e2e8f0;border-radius:14px;padding:16px;box-shadow:0 1px 2px rgba(15,23,42,.04)}.verify small,.stat-grid small{display:block;color:#64748b;font-size:12px}.verify b,.stat-grid b{display:block;margin:5px 0;font-size:20px;color:#0f172a}.verify em,.stat-grid em{font-style:normal;color:#64748b;font-size:12px}.toolbar-card{display:grid;grid-template-columns:2fr repeat(4,1fr) auto;gap:10px;background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:14px;margin-bottom:16px}.toolbar-card input,.toolbar-card select{height:38px;border:1px solid #cbd5e1;border-radius:9px;padding:0 10px}.table-card{background:#fff;border:1px solid #e2e8f0;border-radius:18px;overflow:hidden}.table-head{display:flex;justify-content:space-between;padding:14px 16px;border-bottom:1px solid #e2e8f0}.table-head span{color:#64748b}.table-scroll{overflow:auto}table{min-width:1280px;width:100%;border-collapse:collapse}th,td{padding:11px 12px;border-bottom:1px solid #eef2f7;text-align:left;font-size:13px;vertical-align:top}th{background:#f8fafc;color:#475569;white-space:nowrap}td small{display:block;color:#64748b;margin-top:3px}.product{font-weight:800;color:#0f766e}.pill{display:inline-flex;padding:4px 9px;border-radius:999px;font-size:12px;font-weight:800}.pill.done{background:#dcfce7;color:#166534}.pill.doing{background:#fff7ed;color:#c2410c}.mini{border:0;border-radius:999px;padding:7px 10px;background:#eef2ff;color:#4338ca;font-weight:800;cursor:pointer}.empty{text-align:center;color:#94a3b8;padding:30px}.modal-mask{position:fixed;inset:0;background:rgba(15,23,42,.62);z-index:1000;display:flex;align-items:center;justify-content:center;padding:24px}.modal{background:#fff;width:min(980px,96vw);max-height:90vh;overflow:auto;border-radius:18px;padding:20px}.modal-head{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #e2e8f0;padding-bottom:12px;margin-bottom:12px}.modal-head button{border:0;background:#f1f5f9;border-radius:50%;width:34px;height:34px;font-size:20px}.detail-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.detail-grid div{border:1px solid #eef2f7;border-radius:10px;padding:10px}.detail-grid small{display:block;color:#64748b}.detail-grid b{display:block;margin-top:4px;word-break:break-word}.detail-grid .wide{grid-column:1/-1}@media(max-width:1050px){.toolbar-card{grid-template-columns:1fr}.hero{flex-direction:column}.detail-grid{grid-template-columns:1fr}}
</style>
