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

const complete = computed(() => !!stats.value?.verify?.complete)
const total = computed(() => Number(stats.value?.verify?.importedRows || items.value.length || 0))
const expected = computed(() => Number(stats.value?.verify?.expectedDataRows || stats.value?.verify?.expectedRows || 154))
const worksheetRows = computed(() => Number(stats.value?.verify?.expectedWorksheetRows || expected.value + 1))

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

function rawCells(row: WorkOrder | null) {
  if (!row?.rawJson) return {}
  try {
    const parsed = typeof row.rawJson === 'string' ? JSON.parse(row.rawJson) : row.rawJson
    return parsed.cells || {}
  } catch {
    return {}
  }
}

function val(v: any) {
  if (v === null || v === undefined || v === '') return '-'
  return String(v).replace('T', ' ')
}

function exportCsv() {
  const headers = ['Excel行号','申请编号','申请状态','发起时间','发起人','申请部门','项目名称','产品名称','订单类型','产品类型','二级类型','打样数量','规格/口味','附件','工单状态','开始时间','预计完成','实际完成','负责人','工厂','SourceID']
  const rows = items.value.map(x => [x.excelRowNo,x.applicationNo,x.approvalStatus,x.initiatedAt,x.initiator,x.applicationDepartment,x.projectName,x.productName,x.orderType,x.productType,x.productSubType,x.sampleQuantityText,x.specFlavor,x.attachmentSummary,x.workOrderStatus,x.startDate,x.estimatedCompleteDate,x.actualCompleteDate,x.owner,x.factory,x.sourceId])
  const csv = [headers, ...rows].map(r => r.map(v => `"${val(v).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '2026打样申请_系统导入校验.csv'
  a.click()
  URL.revokeObjectURL(url)
}

onMounted(load)
</script>

<template>
  <div class="sample-page">
    <section class="hero">
      <div>
        <p>SUPPLY CHAIN SAMPLE WORK ORDERS</p>
        <h2>供应链打样工单明细</h2>
        <span>已将 Excel 的每一条打样明细作为独立系统记录导入。原表 155 行含表头，系统保存 154 条业务明细，并保留原始 34 列和 SourceID 便于审计。</span>
      </div>
      <div class="hero-actions">
        <button class="btn btn-secondary" @click="load" :disabled="loading">刷新校验</button>
        <button class="btn btn-primary" @click="exportCsv">导出当前结果</button>
      </div>
    </section>

    <section class="verify" :class="{ ok: complete }">
      <article><small>导入完整性</small><b>{{ complete ? '完整' : '需检查' }}</b><em>{{ total }}/{{ expected }} 条明细，{{ worksheetRows }} 行含表头</em></article>
      <article><small>唯一 SourceID</small><b>{{ stats.verify?.distinctSourceId || 0 }}</b><em>重复组 {{ stats.verify?.duplicateSourceIdGroups || 0 }}</em></article>
      <article><small>原始字段保全</small><b>{{ stats.verify?.expectedColumns || 34 }}</b><em>列/行 raw_json 留痕</em></article>
      <article><small>来源</small><b>{{ stats.verify?.sourceSheet || '2026打样申请' }}</b><em>{{ stats.verify?.sourceFile || '' }}</em></article>
    </section>

    <section class="stat-grid">
      <article v-for="s in (stats.status || [])" :key="s.name"><small>工单状态</small><b>{{ s.name }}</b><em>{{ s.count }} 条</em></article>
      <article v-for="s in (stats.orderType || []).slice(0,3)" :key="'t'+s.name"><small>订单类型</small><b>{{ s.name }}</b><em>{{ s.count }} 条</em></article>
      <article v-for="s in (stats.owner || []).slice(0,3)" :key="'o'+s.name"><small>负责人</small><b>{{ s.name }}</b><em>{{ s.count }} 条</em></article>
    </section>

    <section class="toolbar-card">
      <input v-model="keyword" @keyup.enter="load" placeholder="搜索：申请编号 / 项目 / 产品 / 负责人 / SourceID" />
      <select v-model="status"><option value="">全部状态</option><option v-for="x in options.statuses" :key="x">{{ x }}</option></select>
      <select v-model="owner"><option value="">全部负责人</option><option v-for="x in options.owners" :key="x">{{ x }}</option></select>
      <select v-model="orderType"><option value="">全部订单类型</option><option v-for="x in options.orderTypes" :key="x">{{ x }}</option></select>
      <select v-model="productType"><option value="">全部产品类型</option><option v-for="x in options.productTypes" :key="x">{{ x }}</option></select>
      <button class="btn btn-primary" @click="load" :disabled="loading">查询</button>
    </section>

    <section class="table-card">
      <div class="table-head"><b>当前结果：{{ items.length }} 条</b><span>点击“查看原始列”可核对 Excel 34 列。</span></div>
      <div class="table-scroll">
        <table>
          <thead><tr><th>Excel行</th><th>申请编号</th><th>项目</th><th>产品名称</th><th>订单/产品类型</th><th>数量</th><th>规格/口味</th><th>状态</th><th>负责人</th><th>开始/预计/实际</th><th>附件</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="r in items" :key="r.id">
              <td>{{ r.excelRowNo }}</td>
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
              <td><button class="mini" @click="openDetail(r)">查看原始列</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!items.length" class="empty">暂无数据</div>
    </section>

    <div v-if="selected || detailLoading" class="modal-mask" @click.self="selected=null">
      <div class="modal">
        <div class="modal-head"><div><small>Excel 行 {{ selected?.excelRowNo }}</small><h3>{{ selected?.applicationNo }} · {{ selected?.productName }}</h3></div><button @click="selected=null">×</button></div>
        <div class="detail-grid">
          <div v-for="(v,k) in rawCells(selected)" :key="String(k)"><small>{{ k }}</small><b>{{ val(v) }}</b></div>
        </div>
        <div class="checksum"><b>SourceID</b><span>{{ selected?.sourceId }}</span><b>Row Checksum</b><span>{{ selected?.rowChecksum }}</span></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.sample-page{padding:24px;background:#f8fafc;min-height:100vh}.hero{display:flex;justify-content:space-between;gap:16px;padding:26px;border-radius:20px;background:linear-gradient(135deg,#0f172a,#0f766e);color:#fff}.hero p{font-size:11px;letter-spacing:1.6px;color:#ccfbf1}.hero h2{margin:6px 0}.hero span{opacity:.85}.hero-actions{display:flex;gap:10px;align-items:flex-start}.verify,.stat-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px;margin:16px 0}.verify article,.stat-grid article{background:#fff;border:1px solid #e2e8f0;border-radius:14px;padding:16px;box-shadow:0 1px 2px rgba(15,23,42,.04)}.verify.ok article:first-child{border-color:#86efac;background:#f0fdf4}.verify small,.stat-grid small{display:block;color:#64748b;font-size:12px}.verify b,.stat-grid b{display:block;margin:5px 0;font-size:20px;color:#0f172a}.verify em,.stat-grid em{font-style:normal;color:#64748b;font-size:12px}.toolbar-card{display:grid;grid-template-columns:2fr repeat(4,1fr) auto;gap:10px;background:#fff;border:1px solid #e2e8f0;border-radius:16px;padding:14px;margin-bottom:16px}.toolbar-card input,.toolbar-card select{height:38px;border:1px solid #cbd5e1;border-radius:9px;padding:0 10px}.table-card{background:#fff;border:1px solid #e2e8f0;border-radius:18px;overflow:hidden}.table-head{display:flex;justify-content:space-between;padding:14px 16px;border-bottom:1px solid #e2e8f0}.table-head span{color:#64748b}.table-scroll{overflow:auto}table{min-width:1280px;width:100%;border-collapse:collapse}th,td{padding:11px 12px;border-bottom:1px solid #eef2f7;text-align:left;font-size:13px;vertical-align:top}th{background:#f8fafc;color:#475569;white-space:nowrap}td small{display:block;color:#64748b;margin-top:3px}.product{font-weight:800;color:#0f766e}.pill{display:inline-flex;padding:4px 9px;border-radius:999px;font-size:12px;font-weight:800}.pill.done{background:#dcfce7;color:#166534}.pill.doing{background:#fff7ed;color:#c2410c}.mini{border:0;border-radius:999px;padding:7px 10px;background:#eef2ff;color:#4338ca;font-weight:800;cursor:pointer}.empty{text-align:center;color:#94a3b8;padding:30px}.modal-mask{position:fixed;inset:0;background:rgba(15,23,42,.62);z-index:1000;display:flex;align-items:center;justify-content:center;padding:24px}.modal{background:#fff;width:min(980px,96vw);max-height:90vh;overflow:auto;border-radius:18px;padding:20px}.modal-head{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #e2e8f0;padding-bottom:12px;margin-bottom:12px}.modal-head button{border:0;background:#f1f5f9;border-radius:50%;width:34px;height:34px;font-size:20px}.detail-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:10px}.detail-grid div{border:1px solid #eef2f7;border-radius:10px;padding:10px}.detail-grid small{display:block;color:#64748b}.detail-grid b{display:block;margin-top:4px;word-break:break-word}.checksum{margin-top:12px;padding:12px;border-radius:12px;background:#f8fafc;display:grid;grid-template-columns:120px 1fr;gap:8px;word-break:break-all}@media(max-width:1050px){.toolbar-card{grid-template-columns:1fr}.hero{flex-direction:column}.detail-grid{grid-template-columns:1fr}}
</style>
