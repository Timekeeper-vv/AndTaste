<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'
import { requestAssetPreviewAccess, requestAssetPreviewUrl } from '../utils/assetAccess'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const rows = ref<any[]>([])
const loading = ref(false)
const reviewingId = ref<number | null>(null)
const type = ref<'all' | 'sample' | 'bulk'>('all')
const status = ref<'all' | 'review' | 'approved' | 'rejected'>('all')
const userId = ref('')
const comment = ref('')
type ModelFormat = 'GLB' | 'OBJ' | 'STL'
const modelFormats: ModelFormat[] = ['GLB', 'OBJ', 'STL']
const selectedFormats = ref<Record<number, ModelFormat>>({})
const downloadingKeys = ref<Set<string>>(new Set())
const lifecycleByRequest = ref<Record<number, any>>({})
const lifecycleSelections = ref<Record<number, string>>({})
const lifecycleComments = ref<Record<number, string>>({})
const lifecycleEvidence = ref<Record<number, Array<{ assetId: number; previewUrl: string }>>>({})
const lifecycleBusyId = ref<number | null>(null)
const lifecycleUploadBusyId = ref<number | null>(null)
const logisticsByRequest = ref<Record<number, any>>({})
const logisticsForms = ref<Record<number, { carrierCode: string; trackingNo: string; exceptionNote: string }>>({})
const logisticsCarrierOptions = ref<Array<{ code: string; name: string }>>([])
const logisticsBusyId = ref<number | null>(null)
const logisticsExceptionBusyId = ref<number | null>(null)
const workflowDetails = ref<Record<number, any>>({})
const workflowDetailBusyId = ref<number | null>(null)
const logisticsAlerts = ref<any[]>([])
const lifecycleOptions = [
  { value: 'in_production', label: '生产中' },
  { value: 'ready_to_ship', label: '已出样，待发货' },
  { value: 'shipped', label: '已发货' },
  { value: 'revision_in_progress', label: '返修处理中' },
  { value: 'revision_completed', label: '返修完成' },
]
const lifecycleReadOnlyOptions = [
  { value: 'revision_required', label: '待返修' },
  { value: 'received', label: '用户待反馈' },
  { value: 'accepted', label: '用户已验收' },
  { value: 'rejected', label: '用户未通过' },
  { value: 'bulk_unlocked', label: '已解锁量产' },
]

const stats = computed(() => ({
  total: rows.value.length,
  sample: rows.value.filter(x => x.requestType === 'sample').length,
  bulk: rows.value.filter(x => x.requestType === 'bulk').length,
  review: rows.value.filter(x => x.status === 'review').length,
  payment: rows.value.filter(x => x.requestType === 'sample' && x.samplePaymentStatus === 'manual_review').length,
  revision: rows.value.filter(x => x.requestType === 'sample' && x.sampleWorkflowStatus === 'revision_required').length,
  logistics: logisticsAlerts.value.length,
  todo: rows.value.filter(x => x.status === 'review' || (x.requestType === 'sample' && ['manual_review'].includes(String(x.samplePaymentStatus || '')))
    || (x.requestType === 'sample' && x.sampleWorkflowStatus === 'revision_required')).length + logisticsAlerts.value.length,
}))

function requestTypeText(v?: string) { return v === 'bulk' ? '批量生产' : '打样' }
function statusText(v?: string) { const map: Record<string,string> = { review:'待审核', approved:'已通过', processing:'生产中', rejected:'未通过' }; return map[String(v || 'review')] || String(v || '-') }
function statusClass(v?: string) { const s = String(v || 'review'); return s === 'approved' || s === 'processing' ? 'ok' : s === 'rejected' ? 'bad' : 'wait' }
function samplePaymentText(v?: string) { const map: Record<string,string> = { unpaid:'待用户支付', pending:'支付处理中', manual_review:'待人工核验', paid:'已支付并进入生产' }; return map[String(v || '')] || '' }
function lifecycleText(v?: string) { const map: Record<string, string> = { not_started: '等待工厂处理', in_production: '生产中', ready_to_ship: '已出样，待发货', shipped: '已发货，等待用户反馈', revision_required: '待返修', revision_in_progress: '返修处理中', revision_completed: '返修完成', received: '用户待反馈', accepted: '用户已验收', rejected: '用户未通过', bulk_unlocked: '已解锁量产' }; return map[String(v || 'not_started')] || String(v || '等待工厂处理') }
function lifecycleValue(row: any) { return lifecycleSelections.value[row.id] || lifecycleByRequest.value[row.id]?.sampleWorkflowStatus || 'not_started' }
function lifecycleChange(row: any, event: Event) { lifecycleSelections.value = { ...lifecycleSelections.value, [row.id]: (event.target as HTMLSelectElement).value } }
function lifecycleEvidenceFor(row: any) { return lifecycleEvidence.value[row.id] || [] }
function logisticsFor(row: any) { return logisticsByRequest.value[row.id] || row.sampleLifecycle?.logistics || null }
function logisticsStatusText(v?: string) { const map: Record<string, string> = { pending: '等待发货', shipped: '已发货，等待揽收', in_transit: '运输中', delivering: '派送中', signed: '已签收', exception: '物流异常', returned: '已退回' }; return map[String(v || 'pending')] || String(v || '等待物流信息') }
function logisticsAlertText(item: any) { return item?.alertLevel === 'exception' || item?.status === 'exception' ? '异常提醒' : item?.alertLevel === 'warning' ? '物流预警' : '' }
function logisticsFormFor(row: any) {
  const current = logisticsFor(row) || {}
  return logisticsForms.value[row.id] || { carrierCode: String(current.carrierCode || ''), trackingNo: String(current.trackingNo || ''), exceptionNote: String(current.exceptionNote || '') }
}
function logisticsFieldChange(row: any, field: 'carrierCode' | 'trackingNo' | 'exceptionNote', event: Event) {
  const current = logisticsFormFor(row)
  logisticsForms.value = { ...logisticsForms.value, [row.id]: { ...current, [field]: (event.target as HTMLInputElement | HTMLSelectElement).value } }
}
function logisticsTraces(item: any) { return Array.isArray(item?.traces) ? item.traces.slice(0, 3) : [] }
function logisticsBusy(row: any) { return logisticsBusyId.value === row.id || logisticsExceptionBusyId.value === row.id }
function fmtTime(v?: string) { return v ? String(v).replace('T',' ').slice(0,19) : '-' }
function workflowDetailFor(row: any) { return workflowDetails.value[row.id] || null }
function workflowDetailFlow(row: any) { return workflowDetailFor(row)?.flow || null }
function workflowDetailBlockers(row: any) { const items = workflowDetailFlow(row)?.blockers; return Array.isArray(items) ? items : [] }
function museumList(r: any) { return Array.isArray(r.museumDistribution) ? r.museumDistribution : [] }
function museumQty(r: any) { return museumList(r).reduce((s: number, x: any) => s + Number(x.quantity || 0), 0) }
function previewUrl(r: any) { return r.previewUrl || r.fileUrl || '' }
function formatOf(r: any): ModelFormat { return selectedFormats.value[r.id] || 'GLB' }
function setFormat(r: any, e: Event) { selectedFormats.value = { ...selectedFormats.value, [r.id]: (e.target as HTMLSelectElement).value as ModelFormat } }
function downloadKey(r: any, format: ModelFormat) { return `${r.id}-${format}` }
function isDownloading(r: any, format: ModelFormat) { return downloadingKeys.value.has(downloadKey(r, format)) }
function safeName(s?: string) { return String(s || 'and-taste-3d').replace(/[\/:*?"<>|\s]+/g, '-').replace(/-+/g, '-').slice(0, 80) }
function filenameFromDisposition(disposition: string | null, fallback: string) {
  if (!disposition) return fallback
  const matched = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition)
  if (!matched) return fallback
  try { return decodeURIComponent(matched[1] || matched[2]) } catch { return matched[1] || matched[2] || fallback }
}

async function openOrDownloadModel(r: any) {
  const format = formatOf(r)
  if (!r.assetId) return
  if (format === 'GLB') {
    try {
      const url = await requestAssetPreviewUrl(r.assetId)
      window.open(url, '_blank', 'noopener,noreferrer')
    } catch (e: any) {
      emit('alert', `模型预览失败：${e?.message || e}`, 'error')
    }
    return
  }
  const key = downloadKey(r, format)
  downloadingKeys.value = new Set([...downloadingKeys.value, key])
  emit('alert', `正在转换为 ${format} 格式，首次可能需要1-2分钟`, 'success')
  try {
    const response = await fetch(`/api/creative/ai/assets/${r.assetId}/download-model?format=${format}`, {
      cache: 'no-store',
    })
    if (!response.ok) {
      let message = ''
      try {
        const ct = response.headers.get('content-type') || ''
        message = ct.includes('application/json') ? (await response.json()).message : await response.text()
      } catch {}
      throw new Error(message || `HTTP ${response.status}`)
    }
    const blob = await response.blob()
    const ext = format === 'OBJ' ? 'zip' : format.toLowerCase()
    const filename = filenameFromDisposition(response.headers.get('content-disposition'), `${safeName(r.assetTitle || r.title)}-${r.assetId}.${ext}`)
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1500)
    emit('alert', `${format} 文件已开始下载`, 'success')
  } catch (e: any) {
    emit('alert', `${format} 文件处理失败：` + (e?.message || e), 'error')
  } finally {
    const next = new Set(downloadingKeys.value)
    next.delete(key)
    downloadingKeys.value = next
  }
}

async function load() {
  loading.value = true
  try {
    const qs = new URLSearchParams({ size: '300' })
    if (type.value !== 'all') qs.set('type', type.value)
    if (status.value !== 'all') qs.set('status', status.value)
    if (userId.value.trim()) qs.set('userId', userId.value.trim())
    const [r, lifecycleResponse, carrierResponse, logisticsAlertResponse] = await Promise.all([
      fetch(`/api/creative/ai/consumer-production/admin/review?${qs}`, { cache: 'no-store' }),
      fetch('/api/production/sample-lifecycle?size=300', { cache: 'no-store' }),
      fetch('/api/logistics/carriers', { cache: 'no-store' }),
      fetch('/api/production/sample-logistics/alerts?size=200', { cache: 'no-store' }),
    ])
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.message || `HTTP ${r.status}`)
    const data = await r.json()
    const lifecycleRows = lifecycleResponse.ok ? await lifecycleResponse.json().catch(() => []) : []
    const carrierRows = carrierResponse.ok ? await carrierResponse.json().catch(() => []) : []
    logisticsAlerts.value = logisticsAlertResponse.ok ? (await logisticsAlertResponse.json().catch(() => [])) : []
    logisticsCarrierOptions.value = (Array.isArray(carrierRows) ? carrierRows : [])
      .map((item: any) => ({ code: String(item.code || ''), name: String(item.name || item.code || '') }))
      .filter((item: { code: string; name: string }) => item.code)
    const byRequest: Record<number, any> = {}
    for (const item of Array.isArray(lifecycleRows) ? lifecycleRows : []) byRequest[Number(item.id)] = item
    lifecycleByRequest.value = byRequest
    const nextRows = Array.isArray(data) ? data : []
    rows.value = nextRows.map((item: any) => ({ ...item, sampleLifecycle: byRequest[Number(item.id)] || null }))
    await loadSampleLogistics(rows.value)
  } catch (e: any) {
    emit('alert', '加载C端生产审核失败：' + (e?.message || e), 'error')
  } finally { loading.value = false }
}

async function loadWorkflowDetail(row: any) {
  if (workflowDetailBusyId.value === row.id) return
  if (workflowDetailFor(row)) {
    const next = { ...workflowDetails.value }
    delete next[row.id]
    workflowDetails.value = next
    return
  }
  workflowDetailBusyId.value = row.id
  try {
    const response = await fetch(`/api/production/workflow/${row.id}`, { cache: 'no-store' })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    workflowDetails.value = { ...workflowDetails.value, [row.id]: data }
  } catch (e: any) {
    emit('alert', '加载完整流程失败：' + (e?.message || e), 'error')
  } finally { workflowDetailBusyId.value = null }
}

async function loadSampleLogistics(items: any[]) {
  const samples = items.filter(item => item.requestType === 'sample')
  const result: Record<number, any> = {}
  // The lifecycle list already includes a safe logistics projection. Avoid a
  // request-per-card fan-out; fetch the full trace only after an operator saves
  // or opens a specific request.
  for (const row of samples) {
    const item = row.sampleLifecycle?.logistics
    if (!item || typeof item !== 'object') continue
    result[Number(row.id)] = item
    logisticsForms.value = {
      ...logisticsForms.value,
      [row.id]: {
        carrierCode: String(item.carrierCode || ''),
        trackingNo: String(item.trackingNo || ''),
        exceptionNote: String(item.exceptionNote || ''),
      },
    }
  }
  logisticsByRequest.value = result
}

async function updateSampleLogistics(row: any) {
  const form = logisticsFormFor(row)
  if (!form.carrierCode && form.trackingNo) return emit('alert', '填写快递单号时请选择承运商', 'error')
  if (form.trackingNo && form.trackingNo.trim().length < 4) return emit('alert', '快递单号至少需要 4 位', 'error')
  logisticsBusyId.value = row.id
  try {
    const response = await fetch(`/api/production/sample-lifecycle/${row.id}/logistics`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ carrierCode: form.carrierCode, trackingNo: form.trackingNo.trim() }),
    })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    const item = data?.logistics || data?.shipment || data
    logisticsByRequest.value = { ...logisticsByRequest.value, [row.id]: item }
    logisticsForms.value = { ...logisticsForms.value, [row.id]: { carrierCode: String(item.carrierCode || ''), trackingNo: String(item.trackingNo || ''), exceptionNote: String(item.exceptionNote || '') } }
    if (item.status === 'shipped' && row.sampleLifecycle) {
      row.sampleLifecycle = { ...row.sampleLifecycle, sampleWorkflowStatus: 'shipped' }
      lifecycleByRequest.value = { ...lifecycleByRequest.value, [row.id]: row.sampleLifecycle }
    }
    emit('alert', '样品物流信息已保存', 'success')
  } catch (e: any) { emit('alert', '保存物流信息失败：' + (e?.message || e), 'error') } finally { logisticsBusyId.value = null }
}

async function markSampleLogisticsException(row: any) {
  const form = logisticsFormFor(row)
  if (!form.exceptionNote.trim()) return emit('alert', '请先填写异常说明', 'error')
  logisticsExceptionBusyId.value = row.id
  try {
    const response = await fetch(`/api/production/sample-lifecycle/${row.id}/logistics/exception`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ carrierCode: form.carrierCode, trackingNo: form.trackingNo.trim(), exceptionNote: form.exceptionNote.trim() }),
    })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    const item = data?.logistics || data?.shipment || data
    logisticsByRequest.value = { ...logisticsByRequest.value, [row.id]: item }
    emit('alert', '已标记物流异常，C端会显示提醒', 'success')
  } catch (e: any) { emit('alert', '标记物流异常失败：' + (e?.message || e), 'error') } finally { logisticsExceptionBusyId.value = null }
}

async function resolveSampleLogisticsException(row: any) {
  logisticsExceptionBusyId.value = row.id
  try {
    const response = await fetch(`/api/production/sample-lifecycle/${row.id}/logistics/resolve`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}) })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    const item = data?.logistics || data?.shipment || data
    logisticsByRequest.value = { ...logisticsByRequest.value, [row.id]: item }
    logisticsForms.value = { ...logisticsForms.value, [row.id]: { ...logisticsFormFor(row), exceptionNote: '' } }
    emit('alert', '物流异常已标记为已处理', 'success')
  } catch (e: any) { emit('alert', '处理物流异常失败：' + (e?.message || e), 'error') } finally { logisticsExceptionBusyId.value = null }
}

async function updateSampleLifecycle(row: any) {
  const status = lifecycleValue(row)
  if (!status || status === 'not_started') return emit('alert', '请选择工厂处理状态', 'error')
  lifecycleBusyId.value = row.id
  try {
    const r = await fetch(`/api/production/sample-lifecycle/${row.id}/status`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status, comment: lifecycleComments.value[row.id] || '', evidenceAssetIds: lifecycleEvidenceFor(row).map(item => item.assetId) }),
    })
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.message || `HTTP ${r.status}`)
    const updated = await r.json()
    lifecycleByRequest.value = { ...lifecycleByRequest.value, [row.id]: updated }
    Object.assign(row, { status: updated.status, sampleWorkflowStatus: updated.sampleWorkflowStatus, sampleLifecycle: updated })
    lifecycleSelections.value = { ...lifecycleSelections.value, [row.id]: updated.sampleWorkflowStatus }
    lifecycleComments.value = { ...lifecycleComments.value, [row.id]: '' }
    lifecycleEvidence.value = { ...lifecycleEvidence.value, [row.id]: [] }
    emit('alert', `样品状态已更新为：${lifecycleText(updated.sampleWorkflowStatus)}`, 'success')
  } catch (e: any) {
    emit('alert', '更新样品状态失败：' + (e?.message || e), 'error')
  } finally { lifecycleBusyId.value = null }
}

async function uploadLifecycleEvidence(row: any, event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || []).slice(0, 3 - lifecycleEvidenceFor(row).length)
  input.value = ''
  if (!files.length) return
  lifecycleUploadBusyId.value = row.id
  try {
    const uploaded: Array<{ assetId: number; previewUrl: string }> = []
    for (const file of files) {
      const form = new FormData()
      form.append('file', file)
      form.append('title', `${row.title || row.sampleProductName || '样品'}出样照片`)
      form.append('tags', '样品证据,工厂出样')
      const response = await fetch('/api/creative/ai/assets/upload', { method: 'POST', body: form })
      const data = await response.json().catch(() => null)
      if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
      const assetId = Number(data?.assetId)
      if (!Number.isFinite(assetId) || assetId <= 0) throw new Error('图片上传后未返回素材编号')
      const previewUrl = String(data?.previewUrl || data?.imageUrl || '') || (await requestAssetPreviewAccess(assetId)).previewUrl
      uploaded.push({ assetId, previewUrl })
    }
    lifecycleEvidence.value = { ...lifecycleEvidence.value, [row.id]: [...lifecycleEvidenceFor(row), ...uploaded] }
    emit('alert', `已上传 ${uploaded.length} 张样品证据图，提交状态时会一并记录`, 'success')
  } catch (e: any) {
    emit('alert', '样品照片上传失败：' + (e?.message || e), 'error')
  } finally { lifecycleUploadBusyId.value = null }
}

function removeLifecycleEvidence(row: any, assetId: number) {
  lifecycleEvidence.value = { ...lifecycleEvidence.value, [row.id]: lifecycleEvidenceFor(row).filter(item => item.assetId !== assetId) }
}

async function review(row: any, next: 'approved' | 'rejected' | 'review') {
  reviewingId.value = row.id
  try {
    const r = await fetch(`/api/creative/ai/consumer-production/admin/${row.id}/review`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: next, operator: props.currentUser.username, comment: comment.value.trim() }),
    })
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.message || `HTTP ${r.status}`)
    emit('alert', next === 'approved' ? '生产申请已通过' : next === 'rejected' ? '生产申请已驳回' : '已退回待审核', 'success')
    await load()
  } catch (e: any) {
    emit('alert', '审核失败：' + (e?.message || e), 'error')
  } finally { reviewingId.value = null }
}

onMounted(load)
</script>

<template>
  <div class="prod-review-page">
    <section class="hero-card">
      <div>
        <span>CONSUMER PRODUCTION</span>
        <h1>C端用户作品生产审核</h1>
        <p>审核用户基于已入库 3D 作品提交的打样和批量生产申请。批量生产支持查看自收数量与博物馆投放分配。</p>
      </div>
      <div class="stats">
        <article><b>{{ stats.total }}</b><em>当前列表</em></article>
        <article><b>{{ stats.review }}</b><em>待审核</em></article>
        <article><b>{{ stats.sample }}</b><em>打样</em></article>
        <article><b>{{ stats.bulk }}</b><em>批量生产</em></article>
        <article class="todo-stat"><b>{{ stats.todo }}</b><em>待处理事项</em><small v-if="stats.payment || stats.revision || stats.logistics">支付 {{ stats.payment }} · 返修 {{ stats.revision }} · 物流 {{ stats.logistics }}</small></article>
      </div>
    </section>

    <section class="filters">
      <label><span>申请类型</span><select v-model="type" @change="load"><option value="all">全部</option><option value="sample">打样</option><option value="bulk">批量生产</option></select></label>
      <label><span>状态</span><select v-model="status" @change="load"><option value="all">全部</option><option value="review">待审核</option><option value="approved">已通过</option><option value="rejected">未通过</option></select></label>
      <label><span>用户ID</span><input v-model.trim="userId" type="number" placeholder="按用户查询" @keyup.enter="load" /></label>
      <label class="comment"><span>审核意见</span><input v-model.trim="comment" placeholder="通过说明或驳回原因" /></label>
      <button type="button" :disabled="loading" @click="load">{{ loading ? '查询中…' : '查询' }}</button>
    </section>

    <section v-if="rows.length" class="request-list">
      <article v-for="r in rows" :key="r.id" class="request-card">
        <div class="cover">
          <img v-if="previewUrl(r)" :src="previewUrl(r)" alt="3D作品预览" />
          <div v-else>3D</div>
          <i>{{ requestTypeText(r.requestType) }}</i>
          <strong :class="statusClass(r.status)">{{ statusText(r.status) }}</strong>
        </div>
        <div class="body">
          <header><b>{{ r.title || r.assetTitle || '生产申请' }}</b><small>{{ r.requestNo }}</small></header>
          <div class="meta"><span>用户：{{ r.username }} / ID {{ r.userId }}</span><span>作品ID：{{ r.assetId }}</span></div>
          <div class="workflow-summary">
            <div class="workflow-summary-head"><span>统一流程</span><b v-if="workflowDetailFlow(r)">{{ workflowDetailFlow(r).label }}</b><b v-else>未展开</b><button type="button" :disabled="workflowDetailBusyId === r.id" @click="loadWorkflowDetail(r)">{{ workflowDetailBusyId === r.id ? '加载中…' : workflowDetailFor(r) ? '收起详情' : '查看完整流程' }}</button></div>
            <template v-if="workflowDetailFlow(r)">
              <div class="workflow-progress"><i :style="{ width: `${Number(workflowDetailFlow(r).progressPercent || 0)}%` }" /></div>
              <small class="workflow-next">下一步：{{ workflowDetailFlow(r).nextAction || '等待更新' }}</small>
              <small v-for="item in workflowDetailBlockers(r).slice(0, 2)" :key="item.code" class="workflow-blocker">{{ item.label }}：{{ item.reason }}</small>
              <div v-if="workflowDetailFor(r)?.preflight || workflowDetailFor(r)?.review || workflowDetailFor(r)?.payment" class="workflow-checks"><span v-if="workflowDetailFor(r)?.preflight">预检：{{ workflowDetailFor(r).preflight.status }}</span><span v-if="workflowDetailFor(r)?.review">AI评审：{{ workflowDetailFor(r).review.recommendation || '已记录' }}</span><span v-if="workflowDetailFor(r)?.payment">支付：{{ workflowDetailFor(r).payment.status }}</span></div>
            </template>
          </div>
          <p v-if="r.requestType === 'sample'" class="sample-fee-meta">打样产品：<b>{{ r.sampleProductName || '未选择' }}</b> · 费用：<strong>¥{{ r.sampleFeeYuan || '-' }}</strong><em v-if="samplePaymentText(r.samplePaymentStatus)">{{ samplePaymentText(r.samplePaymentStatus) }}</em></p>
          <div v-if="r.requestType === 'sample'" class="sample-lifecycle-panel">
            <div class="sample-lifecycle-head"><b>样品生命周期</b><span>{{ lifecycleText(lifecycleValue(r)) }}</span></div>
            <div class="sample-lifecycle-actions">
              <select :value="lifecycleValue(r)" :disabled="lifecycleBusyId === r.id || ['accepted','bulk_unlocked','rejected'].includes(lifecycleValue(r))" @change="lifecycleChange(r, $event)">
                <option value="not_started">选择工厂状态</option>
                <option v-for="option in lifecycleOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
                <template v-if="lifecycleReadOnlyOptions.some(item => item.value === lifecycleValue(r))">
                  <option v-for="option in lifecycleReadOnlyOptions" :key="`readonly-${option.value}`" :value="option.value" disabled>{{ option.label }}</option>
                </template>
              </select>
              <input v-model="lifecycleComments[r.id]" placeholder="可选：出样/返修/物流备注" :disabled="lifecycleBusyId === r.id" />
              <button type="button" class="lifecycle-submit" :disabled="lifecycleBusyId === r.id || ['review','rejected'].includes(r.status)" @click="updateSampleLifecycle(r)">{{ lifecycleBusyId === r.id ? '更新中…' : '更新样品状态' }}</button>
            </div>
            <div class="sample-evidence">
              <div class="sample-evidence-head"><span>出样/返修照片（最多 3 张）</span><label class="sample-evidence-upload"><input type="file" accept="image/png,image/jpeg,image/webp" multiple :disabled="lifecycleUploadBusyId === r.id || lifecycleBusyId === r.id" @change="uploadLifecycleEvidence(r, $event)" /><span>{{ lifecycleUploadBusyId === r.id ? '上传中…' : '上传照片' }}</span></label></div>
              <div v-if="lifecycleEvidenceFor(r).length" class="sample-evidence-grid"><div v-for="item in lifecycleEvidenceFor(r)" :key="item.assetId" class="sample-evidence-item"><img :src="item.previewUrl" alt="样品证据" /><button type="button" @click="removeLifecycleEvidence(r, item.assetId)">×</button></div></div>
            </div>
            <div class="sample-logistics">
              <div class="sample-logistics-head"><span>样品物流</span><span v-if="logisticsFor(r)" :class="{ 'logistics-alert-badge': logisticsAlertText(logisticsFor(r)) }">{{ logisticsStatusText(logisticsFor(r)?.status) }}{{ logisticsAlertText(logisticsFor(r)) ? ` · ${logisticsAlertText(logisticsFor(r))}` : '' }}</span><span v-else>尚未录入</span></div>
              <div class="sample-logistics-actions">
                <select :value="logisticsFormFor(r).carrierCode" :disabled="logisticsBusy(r)" @change="logisticsFieldChange(r, 'carrierCode', $event)">
                  <option value="">选择快递公司</option>
                  <option v-for="carrier in logisticsCarrierOptions" :key="carrier.code" :value="carrier.code">{{ carrier.name }}</option>
                </select>
                <input :value="logisticsFormFor(r).trackingNo" placeholder="快递单号" :disabled="logisticsBusy(r)" @input="logisticsFieldChange(r, 'trackingNo', $event)" />
                <button type="button" :disabled="logisticsBusy(r)" @click="updateSampleLogistics(r)">{{ logisticsBusyId === r.id ? '保存中…' : '保存物流' }}</button>
              </div>
              <div class="sample-logistics-exception">
                <input :value="logisticsFormFor(r).exceptionNote" placeholder="异常备注（填写后可标记异常）" :disabled="logisticsBusy(r)" @input="logisticsFieldChange(r, 'exceptionNote', $event)" />
                <button v-if="logisticsFor(r)?.alertLevel !== 'exception'" type="button" class="exception-button" :disabled="logisticsBusy(r)" @click="markSampleLogisticsException(r)">{{ logisticsExceptionBusyId === r.id ? '处理中…' : '标记异常' }}</button>
                <button v-else type="button" class="resolve-button" :disabled="logisticsBusy(r)" @click="resolveSampleLogisticsException(r)">{{ logisticsExceptionBusyId === r.id ? '处理中…' : '标记已处理' }}</button>
              </div>
              <small v-if="logisticsFor(r)?.latestTrace" class="sample-logistics-trace">最新轨迹：{{ logisticsFor(r).latestTrace }}</small>
              <small v-if="logisticsFor(r)?.exceptionNote" class="sample-logistics-note">异常说明：{{ logisticsFor(r).exceptionNote }}</small>
              <div v-if="logisticsTraces(logisticsFor(r)).length" class="sample-logistics-traces"><span v-for="trace in logisticsTraces(logisticsFor(r))" :key="trace.id">{{ trace.content }} · {{ fmtTime(trace.createdAt) }}</span></div>
            </div>
            <small v-if="r.sampleLifecycle?.latestEvent">最近记录：{{ r.sampleLifecycle.latestEvent.comment || lifecycleText(r.sampleLifecycle.sampleWorkflowStatus) }} · {{ fmtTime(r.sampleLifecycle.latestEvent.createdAt) }}</small>
          </div>
          <div class="qty">
            <article><b>{{ r.quantity }}</b><span>总数量</span></article>
            <article><b>{{ r.selfShipQuantity }}</b><span>邮寄给用户</span></article>
            <article><b>{{ museumQty(r) }}</b><span>博物馆投放</span></article>
          </div>
          <div v-if="museumList(r).length" class="museums">
            <b>投放分配</b>
            <p v-for="m in museumList(r)" :key="m.museumId || m.museumName"><b>审批出处：</b>{{ m.approvalSource || `${m.province || ''}${m.city || ''}${m.district || ''} · ${m.museumName}` }} · 投放 {{ m.quantity }} 个</p>
          </div>
          <p class="address" v-if="r.recipientAddress || r.recipientName">自收信息：{{ r.recipientName || '-' }} / {{ r.recipientPhone || '-' }} / {{ r.recipientAddress || '-' }}</p>
          <p class="note">{{ r.note || '暂无申请说明' }}</p>
          <div class="times"><span>提交：{{ fmtTime(r.createdAt) }}</span><span v-if="r.reviewedAt">审核：{{ fmtTime(r.reviewedAt) }}</span></div>
          <footer>
            <template v-if="r.assetId">
              <select class="format-select" :value="formatOf(r)" @change="setFormat(r, $event)">
                <option v-for="f in modelFormats" :key="f" :value="f">{{ f }}</option>
              </select>
              <button type="button" class="file-btn" :disabled="isDownloading(r, formatOf(r))" @click="openOrDownloadModel(r)">
                {{ isDownloading(r, formatOf(r)) ? '处理中' : formatOf(r) === 'GLB' ? '打开文件' : '转换下载' }}
              </button>
            </template>
            <button type="button" class="approve" :disabled="reviewingId === r.id" @click="review(r, 'approved')">通过</button>
            <button type="button" class="reject" :disabled="reviewingId === r.id" @click="review(r, 'rejected')">不通过</button>
            <button v-if="r.status !== 'review'" type="button" :disabled="reviewingId === r.id" @click="review(r, 'review')">退回待审</button>
          </footer>
        </div>
      </article>
    </section>

    <section v-else class="empty"><b>{{ loading ? '正在加载…' : '暂无生产申请' }}</b><span>用户在C端作品审核通过后，可提交打样或批量生产申请。</span></section>
  </div>
</template>

<style scoped>
.prod-review-page{padding:24px;display:flex;flex-direction:column;gap:18px;color:#0f172a}.hero-card{display:grid;grid-template-columns:1.1fr .9fr;gap:18px;padding:30px;border-radius:30px;background:linear-gradient(135deg,#fff,#fff7ed 48%,#ecfdf5);border:1px solid #e2e8f0;box-shadow:0 22px 60px rgba(15,23,42,.07)}.hero-card span{display:inline-flex;padding:7px 10px;border-radius:999px;background:#ffedd5;color:#b45309;font-size:11px;font-weight:950;letter-spacing:1.6px}.hero-card h1{margin:10px 0;font-size:32px}.hero-card p{margin:0;color:#64748b;line-height:1.7}.stats{display:grid;grid-template-columns:repeat(5,1fr);gap:10px}.stats article{padding:16px;border-radius:18px;background:rgba(255,255,255,.75);border:1px solid #e2e8f0}.stats b{display:block;font-size:26px}.stats em{font-style:normal;color:#64748b;font-size:12px;font-weight:900}.stats small{display:block;margin-top:7px;color:#94a3b8;font-size:10px;line-height:1.35}.todo-stat{border-color:#fed7aa!important;background:#fffaf3!important}.filters{display:grid;grid-template-columns:140px 140px 140px 1fr 90px;gap:10px;align-items:end;padding:16px;border-radius:20px;background:#fff;border:1px solid #e2e8f0}.filters span{display:block;margin-bottom:7px;color:#64748b;font-size:12px;font-weight:900}.filters input,.filters select{width:100%;height:40px;box-sizing:border-box;border:1px solid #cbd5e1;border-radius:12px;padding:0 12px;background:#f8fafc}.filters button{height:40px;border:0;border-radius:12px;background:#111827;color:#fff;font-weight:900}.request-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(430px,1fr));gap:16px}.request-card{display:grid;grid-template-columns:170px 1fr;overflow:hidden;border-radius:24px;background:#fff;border:1px solid #e2e8f0;box-shadow:0 14px 38px rgba(15,23,42,.06)}.cover{position:relative;background:#111827;min-height:260px}.cover img,.cover div{width:100%;height:100%;object-fit:cover;display:flex;align-items:center;justify-content:center;color:#fff;font-size:38px;font-weight:950}.cover i,.cover strong{position:absolute;left:10px;padding:5px 8px;border-radius:999px;background:rgba(255,255,255,.92);font-size:11px;font-style:normal;font-weight:950}.cover i{top:10px;color:#334155}.cover strong{top:42px}.cover strong.wait{color:#b45309}.cover strong.ok{color:#047857}.cover strong.bad{color:#dc2626}.body{padding:16px}.body header{display:flex;justify-content:space-between;gap:12px}.body header b{font-size:17px}.body small,.meta,.times{color:#64748b;font-size:12px}.meta,.times{display:flex;justify-content:space-between;gap:10px;margin-top:8px}.qty{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:12px 0}.qty article{padding:10px;border-radius:14px;background:#f8fafc;border:1px solid #e2e8f0}.qty b{display:block;font-size:22px}.qty span{color:#64748b;font-size:12px;font-weight:900}.museums{padding:10px;border-radius:14px;background:#f0fdfa;border:1px solid #ccfbf1}.museums b{display:block;margin-bottom:5px}.museums p,.address,.note{margin:5px 0;color:#475569;font-size:13px;line-height:1.5}.note{padding:10px;border-radius:14px;background:#fff7ed;color:#7c2d12}.body footer{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.body footer a,.body footer button{height:36px;display:inline-flex;align-items:center;padding:0 12px;border:1px solid #e2e8f0;border-radius:12px;background:#fff;color:#334155;text-decoration:none;font-weight:900}.body footer .format-select{width:86px;height:36px;border:1px solid #e2e8f0;border-radius:12px;background:#fff;padding:0 10px;font-weight:900;color:#334155}.body footer .file-btn{background:#111827;color:#fff;border-color:#111827}.body footer .approve{background:#0f766e;color:#fff;border-color:#0f766e}.body footer .reject{background:#dc2626;color:#fff;border-color:#dc2626}.empty{padding:60px 20px;text-align:center;border-radius:24px;background:#fff;border:1px dashed #cbd5e1}.empty b,.empty span{display:block}.empty span{margin-top:8px;color:#64748b}@media(max-width:980px){.hero-card,.request-card{grid-template-columns:1fr}.stats{grid-template-columns:repeat(2,1fr)}.filters{grid-template-columns:1fr 1fr}.comment,.filters button{grid-column:1/-1}.cover{height:220px}}
.workflow-summary{margin-top:10px;padding:10px 11px;border:1px solid #d8e7de;border-radius:13px;background:#f7fbf7}.workflow-summary-head{display:flex;align-items:center;gap:7px;color:#557465;font-size:11px}.workflow-summary-head b{color:#365748}.workflow-summary-head button{margin-left:auto;height:28px;padding:0 8px;border:0;border-radius:8px;background:#e3f0e6;color:#527363;font-size:10px;font-weight:900;cursor:pointer}.workflow-summary-head button:disabled{opacity:.55}.workflow-progress{height:6px;margin-top:8px;overflow:hidden;border-radius:5px;background:#e2ebe4}.workflow-progress i{display:block;height:100%;border-radius:inherit;background:#638c70}.workflow-next,.workflow-blocker{display:block;margin-top:7px;font-size:10px;line-height:1.45}.workflow-next{color:#557465}.workflow-blocker{color:#a04e43}.workflow-checks{display:flex;flex-wrap:wrap;gap:5px;margin-top:8px}.workflow-checks span{padding:3px 6px;border-radius:6px;background:#fff;color:#718277;font-size:9px}
 </style>
<style scoped>
.sample-fee-meta{margin:8px 0;padding:9px 10px;border:1px solid #eadfd2;border-radius:12px;background:#fff8ef;color:#7c5b48;font-size:12px;line-height:1.5}.sample-fee-meta b{color:#4b6659}.sample-fee-meta strong{color:#b4532a}.sample-fee-meta em{margin-left:8px;padding:3px 6px;border-radius:999px;background:#ecfdf5;color:#047857;font-style:normal;font-weight:900;font-size:10px}
.sample-lifecycle-panel{margin:10px 0;padding:11px;border:1px solid #d8e7de;border-radius:14px;background:#f7fbf7}.sample-lifecycle-head{display:flex;justify-content:space-between;gap:10px;align-items:center;color:#365748;font-size:12px}.sample-lifecycle-head span{padding:4px 7px;border-radius:999px;background:#e4f1e7;color:#527363;font-weight:900}.sample-lifecycle-actions{display:grid;grid-template-columns:145px 1fr auto;gap:7px;margin-top:9px}.sample-lifecycle-actions select,.sample-lifecycle-actions input{min-width:0;height:34px;box-sizing:border-box;border:1px solid #cbded2;border-radius:9px;padding:0 9px;background:#fff;color:#365748}.sample-lifecycle-actions button{height:34px;border:0;border-radius:9px;padding:0 10px;background:#527b66;color:#fff;font-size:12px;font-weight:900}.sample-lifecycle-actions button:disabled,.sample-lifecycle-actions select:disabled,.sample-lifecycle-actions input:disabled{opacity:.55;cursor:not-allowed}.sample-lifecycle-panel>small{display:block;margin-top:7px;color:#708277;font-size:11px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}@media(max-width:620px){.sample-lifecycle-actions{grid-template-columns:1fr}.sample-lifecycle-actions button{width:100%}}
.sample-evidence{margin-top:9px;padding-top:9px;border-top:1px solid #dfece3}.sample-evidence-head{display:flex;align-items:center;justify-content:space-between;gap:8px;color:#5f7667;font-size:11px}.sample-evidence-upload{position:relative;display:inline-flex;align-items:center;height:30px;padding:0 9px;border-radius:8px;background:#e5f1e8;color:#527363;font-weight:900;cursor:pointer}.sample-evidence-upload input{position:absolute;inset:0;width:100%;height:100%;opacity:0;cursor:pointer}.sample-evidence-upload input:disabled{cursor:not-allowed}.sample-evidence-grid{display:flex;gap:7px;margin-top:8px}.sample-evidence-item{position:relative;width:54px;height:54px;overflow:hidden;border-radius:8px;background:#e9f0eb}.sample-evidence-item img{display:block;width:100%;height:100%;object-fit:cover}.sample-evidence-item button{position:absolute;top:2px;right:2px;width:17px;height:17px;padding:0;border:0;border-radius:50%;background:rgba(15,23,42,.72);color:#fff;line-height:17px;font-size:14px;cursor:pointer}
</style>

<style scoped>
.sample-logistics{margin-top:10px;padding-top:10px;border-top:1px solid #dfece3}.sample-logistics-head{display:flex;justify-content:space-between;align-items:center;gap:8px;color:#557465;font-size:11px;font-weight:900}.sample-logistics-head>span:last-child{padding:4px 7px;border-radius:999px;background:#e8f3ea;color:#527363}.sample-logistics-head .logistics-alert-badge{background:#fbe4de;color:#a04e43}.sample-logistics-actions,.sample-logistics-exception{display:grid;grid-template-columns:125px 1fr auto;gap:7px;margin-top:8px}.sample-logistics-exception{grid-template-columns:1fr auto}.sample-logistics select,.sample-logistics input{min-width:0;height:32px;box-sizing:border-box;border:1px solid #cbded2;border-radius:9px;padding:0 8px;background:#fff;color:#365748;font-size:11px}.sample-logistics button{height:32px;padding:0 10px;border:0;border-radius:9px;background:#527b66;color:#fff;font-size:11px;font-weight:900}.sample-logistics button.exception-button{background:#b86557}.sample-logistics button.resolve-button{background:#718d7c}.sample-logistics button:disabled,.sample-logistics input:disabled,.sample-logistics select:disabled{opacity:.55;cursor:not-allowed}.sample-logistics-trace,.sample-logistics-note{display:block;margin-top:7px;color:#708277;font-size:10px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.sample-logistics-note{color:#a04e43}.sample-logistics-traces{display:flex;flex-direction:column;gap:4px;margin-top:7px;color:#798b80;font-size:10px}.sample-logistics-traces span{overflow:hidden;white-space:nowrap;text-overflow:ellipsis}@media(max-width:620px){.sample-logistics-actions,.sample-logistics-exception{grid-template-columns:1fr}.sample-logistics button{width:100%}}
</style>
