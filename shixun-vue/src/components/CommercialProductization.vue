<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'

type Tab = 'quotes' | 'sampleRequests' | 'consignments' | 'guidance'

const props = defineProps<{ currentUser: User; initialTab?: Tab; guidanceOnly?: boolean }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const tab = ref<Tab>(props.initialTab === 'guidance' || props.initialTab === 'consignments' ? props.initialTab : 'quotes')
const status = ref(defaultStatus(tab.value))
const loading = ref(false)
const rows = ref<any[]>([])
const comment = ref('')
const quoteUnit = ref('')
const quoteTotal = ref('')
const quoteLead = ref('')
const sampleDrafts = ref<Record<number, { fee: string; lead: string; material: string; note: string }>>({})
const guidanceDrafts = ref<Record<number, { fee: string; lead: string; result: string; comment: string }>>({})
const busyId = ref<number | null>(null)

const statuses = computed(() => {
  if (tab.value === 'quotes') return [
    { value: 'new', label: '待处理' }, { value: 'processing', label: '处理中' },
    { value: 'quoted', label: '已报价' }, { value: 'rejected', label: '已驳回' }, { value: 'all', label: '全部' },
  ]
  if (tab.value === 'sampleRequests') return [
    { value: 'review', label: '待报价' }, { value: 'approved', label: '待用户支付' }, { value: 'processing', label: '待生产' }, { value: 'all', label: '全部' },
  ]
  if (tab.value === 'consignments') return [
    { value: 'pending_review', label: '待审核' }, { value: 'need_materials', label: '待补材料' },
    { value: 'approved', label: '已通过' }, { value: 'rejected', label: '已驳回' }, { value: 'all', label: '全部' },
  ]
  return [
    { value: 'requested', label: '待报价' }, { value: 'quoted', label: '待付款' },
    { value: 'in_progress', label: '指导中' }, { value: 'completed', label: '已完成' },
    { value: 'closed', label: '已关闭' }, { value: 'all', label: '全部' },
  ]
})

const statusLabel: Record<string, string> = {
  new: '待处理', processing: '处理中', quoted: '已报价', accepted: '已接受', rejected: '已驳回', closed: '已关闭',
  pending_review: '待审核', need_materials: '待补材料', approved: '已通过', withdrawn: '已撤回',
  requested: '待指导报价', in_progress: '指导进行中', completed: '指导已完成',
  unpaid: '待付款', pending: '支付处理中', manual_review: '人工核验中', paid: '已支付', not_required: '未报价',
}

function endpointFor(value: Tab) {
  return value === 'quotes' ? 'quote-requests' : value === 'sampleRequests' ? 'sample-requests' : value === 'consignments' ? 'consignment-applications' : 'professional-guidance'
}
function tabLabel(value: Tab) { return value === 'quotes' ? '报价' : value === 'sampleRequests' ? 'C端打样' : value === 'consignments' ? '代销' : '专业指导' }
function defaultStatus(value: Tab) { return value === 'quotes' ? 'new' : value === 'sampleRequests' ? 'review' : value === 'consignments' ? 'pending_review' : 'requested' }

async function load() {
  loading.value = true
  try {
    const response = await fetch(`/api/commercial/admin/${endpointFor(tab.value)}?status=${encodeURIComponent(status.value)}`, { cache: 'no-store' })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    rows.value = Array.isArray(data) ? data : []
  } catch (error: any) {
    emit('alert', `加载${tabLabel(tab.value)}申请失败：${error?.message || error}`, 'error')
  } finally {
    loading.value = false
  }
}

function switchTab(next: Tab) {
  tab.value = next
  status.value = defaultStatus(next)
  resetForm()
  void load()
}
function resetForm() {
  comment.value = ''
  quoteUnit.value = ''
  quoteTotal.value = ''
  quoteLead.value = ''
  sampleDrafts.value = {}
  guidanceDrafts.value = {}
}
function formatTime(value?: string) { return value ? String(value).replace('T', ' ').slice(0, 19) : '-' }
function sampleDraft(row: any) {
  const id = Number(row.id)
  if (!sampleDrafts.value[id]) {
    sampleDrafts.value[id] = {
      fee: row.sampleFeeYuan == null ? '' : String(row.sampleFeeYuan),
      lead: row.sampleLeadTime || '',
      material: row.sampleMaterial || '',
      note: row.sampleQuoteNote || row.reviewComment || '',
    }
  }
  return sampleDrafts.value[id]
}
function openQuote(row: any) {
  if (tab.value === 'sampleRequests') {
    const draft = sampleDraft(row)
    quoteUnit.value = draft.material
    quoteTotal.value = draft.fee
    quoteLead.value = draft.lead
    comment.value = draft.note
  } else {
    quoteUnit.value = row.quotedUnitPrice || ''
    quoteTotal.value = row.quotedTotalPrice || ''
    quoteLead.value = row.quotedLeadTime || ''
    comment.value = row.operatorComment || ''
  }
}
function guidanceDraft(row: any) {
  const id = Number(row.id)
  if (!guidanceDrafts.value[id]) {
    guidanceDrafts.value[id] = {
      fee: row.quotedFeeYuan || '',
      lead: row.quotedLeadTime || '',
      result: row.guidanceResult || '',
      comment: row.operatorComment || '',
    }
  }
  return guidanceDrafts.value[id]
}
function canQuoteGuidance(row: any) { return ['requested', 'quoted'].includes(String(row.status)) && ['not_required', 'unpaid'].includes(String(row.paymentStatus || 'not_required')) }
function canCompleteGuidance(row: any) { return row.status === 'in_progress' && row.paymentStatus === 'paid' }

async function update(row: any, nextStatus: string) {
  busyId.value = row.id
  try {
    let body: Record<string, unknown>
    if (tab.value === 'quotes') {
      body = { status: nextStatus, quotedUnitPrice: quoteUnit.value || null, quotedTotalPrice: quoteTotal.value || null, quotedLeadTime: quoteLead.value, operatorComment: comment.value }
    } else if (tab.value === 'sampleRequests') {
      const draft = sampleDraft(row)
      const fee = Number(draft.fee)
      if (!draft.material.trim() || !draft.lead.trim() || !draft.fee.trim()) {
        emit('alert', '请填写打样价格、材质和打样时间', 'error')
        return
      }
      if (!Number.isFinite(fee) || fee <= 0) {
        emit('alert', '打样价格必须是大于0的数字', 'error')
        return
      }
      body = { sampleFeeYuan: draft.fee, sampleLeadTime: draft.lead, sampleMaterial: draft.material, sampleQuoteNote: draft.note }
    } else if (tab.value === 'consignments') {
      body = { status: nextStatus, operatorComment: comment.value }
    } else {
      const draft = guidanceDraft(row)
      body = { status: nextStatus, operatorComment: draft.comment }
      if (nextStatus === 'quoted') Object.assign(body, { quotedFeeYuan: draft.fee, quotedLeadTime: draft.lead })
      if (nextStatus === 'completed') Object.assign(body, { guidanceResult: draft.result })
    }
    const response = await fetch(`/api/commercial/admin/${endpointFor(tab.value)}/${row.id}`, {
      method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    emit('alert', tab.value === 'guidance' ? '专业指导单已更新' : tab.value === 'sampleRequests' ? '打样报价单已保存' : '申请状态已更新', 'success')
    await load()
  } catch (error: any) {
    emit('alert', `更新失败：${error?.message || error}`, 'error')
  } finally {
    busyId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="commercial-page">
    <section class="hero">
      <div><span>COMMERCIAL WORKFLOW</span><h1>{{ props.guidanceOnly ? '专业指导工单' : '商品化与代销审核' }}</h1><p>{{ props.guidanceOnly ? '处理审核不通过后的专业指导申请：先报价，用户付款后自动进入指导，完成后用户可上传本地修改图重新提交原申请。' : '处理用户报价、打样、渠道代销和驳回后的专业指导。专业指导完成后，用户会上传本地修改图并重新进入原申请的审核流程。' }}</p></div>
      <div class="rule"><b>专业指导规则</b><strong>报价后付款</strong><small>付款后才进入指导执行</small><small>完成指导不替代作品审核或打样报价</small></div>
    </section>
    <section class="toolbar">
      <template v-if="!props.guidanceOnly">
        <button :class="{ active: tab === 'quotes' }" @click="switchTab('quotes')">报价 / 打样申请</button>
        <button :class="{ active: tab === 'sampleRequests' }" @click="switchTab('sampleRequests')">C端打样申请</button>
        <button :class="{ active: tab === 'consignments' }" @click="switchTab('consignments')">渠道代销申请</button>
        <button :class="{ active: tab === 'guidance' }" @click="switchTab('guidance')">专业指导</button>
      </template>
      <strong v-else class="guidance-toolbar-title">专业指导</strong>
      <select v-model="status" @change="load"><option v-for="item in statuses" :key="item.value" :value="item.value">{{ item.label }}</option></select>
      <button class="refresh" @click="load">{{ loading ? '加载中…' : '刷新' }}</button>
    </section>
    <section v-if="rows.length" class="list">
      <article v-for="row in rows" :key="row.id" class="row">
        <div class="row-head"><div><b>{{ row.displayProductName || row.productName || '商品化申请' }}</b><small>{{ tab === 'guidance' ? row.guidanceNo : (row.requestNo || row.applicationNo) }} · {{ row.productNo || '' }} · 用户 {{ row.username }} / ID {{ row.userId }}</small></div><em :class="row.status">{{ tab === 'sampleRequests' && row.samplePaymentStatus === 'paid' ? '已支付打样费 · 待生产' : tab === 'sampleRequests' && row.status === 'approved' ? '待用户支付打样费' : statusLabel[row.status] || row.status }}</em></div>
        <div class="meta">
          <span>作品 ID：{{ row.assetId || '未关联' }}</span>
          <span v-if="tab === 'quotes'">数量：{{ row.quantity }} · {{ row.requestType }}</span>
          <span v-else-if="tab === 'sampleRequests'">数量：{{ row.quantity }} · 多视图/3D打样</span>
          <span v-else-if="tab === 'consignments'">渠道：{{ row.channelName || '未指定' }}</span>
          <span v-else>原申请：{{ row.applicationNo || `${row.applicationType} #${row.applicationId}` }} · {{ statusLabel[row.applicationStatus] || row.applicationStatus }}</span>
          <span>提交：{{ formatTime(row.createdAt) }}</span>
        </div>
        <p v-if="tab === 'sampleRequests'">用户打样申请：{{ row.note || '未填写说明' }}</p>
        <p v-else-if="tab === 'guidance' && row.requestNote">用户诉求：{{ row.requestNote }}</p>
        <p v-else-if="row.note">用户说明：{{ row.note }}</p>
        <p v-if="row.copyrightBasis" class="rights">权利依据：{{ row.copyrightBasis }} · 已确认声明：{{ row.copyrightConfirmed ? '是' : '否' }}<span v-if="row.authorizationNote"> · {{ row.authorizationNote }}</span></p>
        <div v-if="tab === 'quotes'" class="quote-form"><label>单价 <input v-model="quoteUnit" inputmode="decimal" placeholder="待确认" @focus="openQuote(row)" /></label><label>总价 <input v-model="quoteTotal" inputmode="decimal" placeholder="待确认" /></label><label>打样时间 <input v-model="quoteLead" placeholder="例如：10-15 个工作日" /></label></div>
        <div v-else-if="tab === 'sampleRequests'" class="quote-form"><label>材质 <input v-model="sampleDraft(row).material" placeholder="例如：锌合金、亚克力、树脂" /></label><label>打样价格 <input v-model="sampleDraft(row).fee" inputmode="decimal" placeholder="例如：199" /></label><label>打样时间 <input v-model="sampleDraft(row).lead" placeholder="例如：10-15 个工作日" /></label></div>
        <div v-if="tab === 'guidance'" class="guidance-form">
          <label>指导费 <input v-model="guidanceDraft(row).fee" inputmode="decimal" placeholder="例如：199" /></label>
          <label>预计完成 <input v-model="guidanceDraft(row).lead" placeholder="例如：1-2 个工作日" /></label>
          <label class="wide">完成建议 <textarea v-model="guidanceDraft(row).result" placeholder="写明作品问题、可执行修改方式、材质/工艺/视觉建议" /></label>
          <p class="payment-state">支付状态：{{ statusLabel[row.paymentStatus] || row.paymentStatus || '未报价' }}</p>
        </div>
        <label v-if="tab === 'guidance'" class="comment"><span>运营说明</span><input v-model="guidanceDraft(row).comment" placeholder="报价范围、服务边界或交付说明" /></label>
        <label v-else-if="tab === 'sampleRequests'" class="comment"><span>报价备注</span><input v-model="sampleDraft(row).note" placeholder="材质工艺、费用包含范围、生产说明" /></label>
        <label v-else class="comment"><span>运营备注</span><input v-model="comment" placeholder="通过说明、补材料要求或驳回原因" @focus="tab === 'quotes' ? openQuote(row) : null" /></label>
        <footer v-if="tab === 'quotes'"><button class="processing" :disabled="busyId === row.id" @click="update(row, 'processing')">接单处理中</button><button class="quoted" :disabled="busyId === row.id" @click="update(row, 'quoted')">保存报价</button><button class="approve" :disabled="busyId === row.id" @click="update(row, 'accepted')">确认可执行</button><button class="reject" :disabled="busyId === row.id" @click="update(row, 'rejected')">驳回</button></footer>
        <footer v-else-if="tab === 'sampleRequests'"><button class="quoted" :disabled="busyId === row.id || row.samplePaymentStatus === 'paid'" @click="update(row, 'approved')">保存报价并通知支付</button></footer>
        <footer v-else-if="tab === 'consignments'"><button class="materials" :disabled="busyId === row.id" @click="update(row, 'need_materials')">要求补材料</button><button class="approve" :disabled="busyId === row.id" @click="update(row, 'approved')">通过代销审核</button><button class="reject" :disabled="busyId === row.id" @click="update(row, 'rejected')">驳回</button></footer>
        <footer v-else><button class="quoted" :disabled="busyId === row.id || !canQuoteGuidance(row)" @click="update(row, 'quoted')">保存指导报价</button><button class="approve" :disabled="busyId === row.id || !canCompleteGuidance(row)" @click="update(row, 'completed')">完成指导</button><button class="reject" :disabled="busyId === row.id || row.paymentStatus === 'paid' || row.status === 'in_progress'" @click="update(row, 'closed')">关闭指导单</button></footer>
      </article>
    </section>
    <section v-else class="empty"><b>{{ loading ? '正在加载申请…' : '暂无匹配申请' }}</b><span>{{ tab === 'guidance' ? '用户申请专业指导后会显示在这里。' : '用户提交报价或代销申请后会显示在这里。' }}</span></section>
  </div>
</template>

<style scoped>
.commercial-page{padding:24px;color:#0f172a;display:flex;flex-direction:column;gap:18px}.hero{display:grid;grid-template-columns:1fr 300px;gap:20px;padding:28px;border:1px solid #e2e8f0;border-radius:18px;background:linear-gradient(135deg,#fff,#fff7ed 52%,#ecfdf5);box-shadow:0 16px 45px rgba(15,23,42,.06)}.hero span{display:inline-block;padding:6px 9px;border-radius:999px;background:#ffedd5;color:#9a3412;font-size:11px;font-weight:950;letter-spacing:1.5px}.hero h1{margin:12px 0 8px;font-size:30px}.hero p{max-width:760px;margin:0;color:#64748b;line-height:1.7}.rule{display:flex;flex-direction:column;gap:8px;padding:16px;border:1px solid #99f6e4;border-radius:14px;background:#f0fdfa}.rule b{color:#0f766e}.rule strong{font-size:20px;color:#134e4a}.rule small{color:#475569}.toolbar{display:flex;align-items:center;gap:9px;padding:13px;border:1px solid #e2e8f0;border-radius:14px;background:#fff}.guidance-toolbar-title{color:#0f766e;font-size:14px}.toolbar button,.toolbar select,.row button{height:38px;border:1px solid #cbd5e1;border-radius:8px;background:#fff;padding:0 13px;color:#334155;font-weight:850;cursor:pointer}.toolbar button.active{background:#111827;color:#fff;border-color:#111827}.toolbar select{margin-left:auto;padding:0 10px}.toolbar .refresh{background:#f8fafc}.list{display:grid;grid-template-columns:repeat(auto-fill,minmax(480px,1fr));gap:15px}.row{padding:17px;border:1px solid #e2e8f0;border-radius:12px;background:#fff;box-shadow:0 10px 26px rgba(15,23,42,.05)}.row-head{display:flex;justify-content:space-between;gap:12px}.row-head b{display:block;font-size:17px}.row-head small{display:block;margin-top:6px;color:#64748b;font-size:12px}.row-head em{height:max-content;padding:5px 8px;border-radius:999px;background:#f1f5f9;color:#475569;font-style:normal;font-size:11px;font-weight:900;white-space:nowrap}.row-head em.approved,.row-head em.accepted,.row-head em.completed{background:#dcfce7;color:#15803d}.row-head em.rejected{background:#fee2e2;color:#b91c1c}.row-head em.need_materials,.row-head em.quoted{background:#fff7ed;color:#c2410c}.row-head em.in_progress{background:#e0f2fe;color:#0369a1}.meta{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px;color:#64748b;font-size:12px}.row p{margin:12px 0;padding:10px;border-radius:9px;background:#f8fafc;color:#475569;font-size:13px;line-height:1.55}.row .rights{background:#fff7ed;color:#7c2d12}.quote-form,.guidance-form{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:12px}.guidance-form{grid-template-columns:repeat(2,1fr)}.quote-form label,.guidance-form label,.comment span{display:block;color:#64748b;font-size:12px;font-weight:850}.quote-form input,.guidance-form input,.guidance-form textarea,.comment input{box-sizing:border-box;width:100%;height:36px;margin-top:5px;border:1px solid #cbd5e1;border-radius:8px;padding:0 9px;background:#fff;color:#334155}.guidance-form .wide{grid-column:1/-1}.guidance-form textarea{height:80px;padding:8px;resize:vertical}.guidance-form .payment-state{grid-column:1/-1;margin:0;padding:9px;background:#f8fafc;color:#475569}.comment{display:block;margin-top:12px}.row footer{display:flex;flex-wrap:wrap;gap:8px;margin-top:13px}.row footer button.processing{background:#eef2ff;color:#4338ca}.row footer button.quoted{background:#fffbeb;color:#a16207}.row footer button.materials{background:#fff7ed;color:#c2410c}.row footer button.approve{background:#0f766e;color:#fff;border-color:#0f766e}.row footer button.reject{background:#dc2626;color:#fff;border-color:#dc2626}.row button:disabled{opacity:.45;cursor:not-allowed}.empty{padding:60px 20px;border:1px dashed #cbd5e1;border-radius:14px;background:#fff;text-align:center}.empty b,.empty span{display:block}.empty span{margin-top:8px;color:#64748b}@media(max-width:850px){.hero{grid-template-columns:1fr}.toolbar{flex-wrap:wrap}.toolbar select{margin-left:0}.list{grid-template-columns:1fr}.quote-form,.guidance-form{grid-template-columns:1fr}.guidance-form .wide,.guidance-form .payment-state{grid-column:auto}}
</style>
