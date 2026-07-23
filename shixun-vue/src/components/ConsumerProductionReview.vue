<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const rows = ref<any[]>([])
const loading = ref(false)
const reviewingId = ref<number | null>(null)
const type = ref<'all' | 'sample' | 'bulk'>('all')
const status = ref<'all' | 'review' | 'approved' | 'rejected'>('review')
const userId = ref('')
const comment = ref('')
type ModelFormat = 'GLB' | 'OBJ' | 'STL'
const modelFormats: ModelFormat[] = ['GLB', 'OBJ', 'STL']
const selectedFormats = ref<Record<number, ModelFormat>>({})
const downloadingKeys = ref<Set<string>>(new Set())

const stats = computed(() => ({
  total: rows.value.length,
  sample: rows.value.filter(x => x.requestType === 'sample').length,
  bulk: rows.value.filter(x => x.requestType === 'bulk').length,
  review: rows.value.filter(x => x.status === 'review').length,
}))

function requestTypeText(v?: string) { return v === 'bulk' ? '批量生产' : '打样' }
function statusText(v?: string) { const map: Record<string,string> = { review:'待审核', approved:'已通过', rejected:'未通过' }; return map[String(v || 'review')] || String(v || '-') }
function statusClass(v?: string) { const s = String(v || 'review'); return s === 'approved' ? 'ok' : s === 'rejected' ? 'bad' : 'wait' }
function fmtTime(v?: string) { return v ? String(v).replace('T',' ').slice(0,19) : '-' }
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
    window.open(`/api/creative/ai/assets/${r.assetId}/model-content`, '_blank', 'noopener,noreferrer')
    return
  }
  const key = downloadKey(r, format)
  downloadingKeys.value = new Set([...downloadingKeys.value, key])
  emit('alert', `正在转换为 ${format} 格式，首次可能需要1-2分钟`, 'success')
  try {
    const response = await fetch(`/api/creative/ai/assets/${r.assetId}/download-model?format=${format}`, {
      cache: 'no-store',
      headers: { 'X-Current-Role': props.currentUser.role, 'X-Current-User': props.currentUser.username },
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
    const r = await fetch(`/api/creative/ai/consumer-production/admin/review?${qs}`, {
      cache: 'no-store',
      headers: { 'X-Current-Role': props.currentUser.role, 'X-Current-User': props.currentUser.username },
    })
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.message || `HTTP ${r.status}`)
    const data = await r.json()
    rows.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    emit('alert', '加载C端生产审核失败：' + (e?.message || e), 'error')
  } finally { loading.value = false }
}

async function review(row: any, next: 'approved' | 'rejected' | 'review') {
  reviewingId.value = row.id
  try {
    const r = await fetch(`/api/creative/ai/consumer-production/admin/${row.id}/review`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', 'X-Current-Role': props.currentUser.role, 'X-Current-User': props.currentUser.username },
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
          <div class="qty">
            <article><b>{{ r.quantity }}</b><span>总数量</span></article>
            <article><b>{{ r.selfShipQuantity }}</b><span>邮寄给用户</span></article>
            <article><b>{{ museumQty(r) }}</b><span>博物馆投放</span></article>
          </div>
          <div v-if="museumList(r).length" class="museums">
            <b>投放分配</b>
            <p v-for="m in museumList(r)" :key="m.museumId || m.museumName">{{ m.museumName }}：{{ m.quantity }}个 <small v-if="m.city">· {{ m.city }}</small></p>
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
.prod-review-page{padding:24px;display:flex;flex-direction:column;gap:18px;color:#0f172a}.hero-card{display:grid;grid-template-columns:1.1fr .9fr;gap:18px;padding:30px;border-radius:30px;background:linear-gradient(135deg,#fff,#fff7ed 48%,#ecfdf5);border:1px solid #e2e8f0;box-shadow:0 22px 60px rgba(15,23,42,.07)}.hero-card span{display:inline-flex;padding:7px 10px;border-radius:999px;background:#ffedd5;color:#b45309;font-size:11px;font-weight:950;letter-spacing:1.6px}.hero-card h1{margin:10px 0;font-size:32px}.hero-card p{margin:0;color:#64748b;line-height:1.7}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.stats article{padding:16px;border-radius:18px;background:rgba(255,255,255,.75);border:1px solid #e2e8f0}.stats b{display:block;font-size:26px}.stats em{font-style:normal;color:#64748b;font-size:12px;font-weight:900}.filters{display:grid;grid-template-columns:140px 140px 140px 1fr 90px;gap:10px;align-items:end;padding:16px;border-radius:20px;background:#fff;border:1px solid #e2e8f0}.filters span{display:block;margin-bottom:7px;color:#64748b;font-size:12px;font-weight:900}.filters input,.filters select{width:100%;height:40px;box-sizing:border-box;border:1px solid #cbd5e1;border-radius:12px;padding:0 12px;background:#f8fafc}.filters button{height:40px;border:0;border-radius:12px;background:#111827;color:#fff;font-weight:900}.request-list{display:grid;grid-template-columns:repeat(auto-fill,minmax(430px,1fr));gap:16px}.request-card{display:grid;grid-template-columns:170px 1fr;overflow:hidden;border-radius:24px;background:#fff;border:1px solid #e2e8f0;box-shadow:0 14px 38px rgba(15,23,42,.06)}.cover{position:relative;background:#111827;min-height:260px}.cover img,.cover div{width:100%;height:100%;object-fit:cover;display:flex;align-items:center;justify-content:center;color:#fff;font-size:38px;font-weight:950}.cover i,.cover strong{position:absolute;left:10px;padding:5px 8px;border-radius:999px;background:rgba(255,255,255,.92);font-size:11px;font-style:normal;font-weight:950}.cover i{top:10px;color:#334155}.cover strong{top:42px}.cover strong.wait{color:#b45309}.cover strong.ok{color:#047857}.cover strong.bad{color:#dc2626}.body{padding:16px}.body header{display:flex;justify-content:space-between;gap:12px}.body header b{font-size:17px}.body small,.meta,.times{color:#64748b;font-size:12px}.meta,.times{display:flex;justify-content:space-between;gap:10px;margin-top:8px}.qty{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:12px 0}.qty article{padding:10px;border-radius:14px;background:#f8fafc;border:1px solid #e2e8f0}.qty b{display:block;font-size:22px}.qty span{color:#64748b;font-size:12px;font-weight:900}.museums{padding:10px;border-radius:14px;background:#f0fdfa;border:1px solid #ccfbf1}.museums b{display:block;margin-bottom:5px}.museums p,.address,.note{margin:5px 0;color:#475569;font-size:13px;line-height:1.5}.note{padding:10px;border-radius:14px;background:#fff7ed;color:#7c2d12}.body footer{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.body footer a,.body footer button{height:36px;display:inline-flex;align-items:center;padding:0 12px;border:1px solid #e2e8f0;border-radius:12px;background:#fff;color:#334155;text-decoration:none;font-weight:900}.body footer .format-select{width:86px;height:36px;border:1px solid #e2e8f0;border-radius:12px;background:#fff;padding:0 10px;font-weight:900;color:#334155}.body footer .file-btn{background:#111827;color:#fff;border-color:#111827}.body footer .approve{background:#0f766e;color:#fff;border-color:#0f766e}.body footer .reject{background:#dc2626;color:#fff;border-color:#dc2626}.empty{padding:60px 20px;text-align:center;border-radius:24px;background:#fff;border:1px dashed #cbd5e1}.empty b,.empty span{display:block}.empty span{margin-top:8px;color:#64748b}@media(max-width:980px){.hero-card,.request-card{grid-template-columns:1fr}.stats{grid-template-columns:repeat(2,1fr)}.filters{grid-template-columns:1fr 1fr}.comment,.filters button{grid-column:1/-1}.cover{height:220px}}
</style>
