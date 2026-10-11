<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { consumerApi as api, consumerJson as json, rows, money, dateText } from '../utils/consumerApi'
const props = defineProps<{ asset?: any }>()
const emit = defineEmits<{ alert: [message: string, type?: 'success' | 'error']; pay: [request: any] }>()
const products = ref<any[]>([]), channels = ref<any[]>([]), records = ref<any[]>([])
const templateCode = ref(''), channelId = ref(''), channelSearch = ref(''), province = ref('')
const kind = ref('quote'), quantity = ref(1), purpose = ref('personal'), requestType = ref('sample')
const copyrightBasis = ref('original'), copyrightConfirmed = ref(false), authorizationNote = ref(''), note = ref('')
const busy = ref(false), error = ref(''), revision = ref<any>(null), revisionFile = ref<File | null>(null), revisionNote = ref('')
const product = computed(() => products.value.find(item => item.templateCode === templateCode.value))
const visibleChannels = computed(() => channels.value.filter(item => (!province.value || item.province === province.value) && (!channelSearch.value.trim() || `${item.name} ${item.city}`.includes(channelSearch.value.trim()))))
const provinces = computed(() => [...new Set(channels.value.map(item => String(item.province || '')).filter(Boolean))])
const labels: Record<string, string> = { submitted: '已提交', pending_review: '审核中', pending: '待处理', quoted: '已报价', accepted: '已确认报价', rejected: '未通过', need_materials: '待补充材料', approved: '已通过', requested: '已申请协助', processing: '处理中', completed: '已完成', cancelled: '已取消', unpaid: '待支付', pending_payment: '待支付', paid: '已支付', not_required: '无需支付' }
function normalize(item: any) { return new Proxy(item, { get(target, field) { if (typeof field !== 'string') return target[field]; const key = Object.keys(target).find(key => key.replace(/[_-]/g, '').toLowerCase() === field.toLowerCase()); return key ? target[key] : undefined } }) }
async function refresh() {
  const data = await api<any>('/api/commercial/consumer/requests')
  records.value = [['quote', data.quoteRequests], ['consignment', data.consignmentApplications], ['guidance', data.guidanceRequests], ['selection', data.selectionDemands]].flatMap(([type, items]) => rows(items).map(item => normalize({ ...item, type })))
}
async function load() {
  busy.value = true; error.value = ''
  try { const [catalog, directory] = await Promise.all([api('/api/commercial/consumer/products'), api('/api/commercial/consumer/channels')]); products.value = rows(catalog); channels.value = rows(directory); chooseProduct(); await refresh() }
  catch (failure: any) { error.value = failure.message }
  finally { busy.value = false }
}
function chooseProduct() { const match = products.value.find(item => item.productName === props.asset?.productName || item.productName === props.asset?.productType); templateCode.value = match?.templateCode || products.value[0]?.templateCode || '' }
watch(() => props.asset?.id, chooseProduct)
async function submit() {
  if (busy.value || !templateCode.value) return
  if (!copyrightConfirmed.value) { emit('alert', '请确认作品版权授权', 'error'); return }
  if (!Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > 100000) { emit('alert', '请输入1至100000之间的整数数量', 'error'); return }
  if (kind.value === 'consignment' && (!props.asset?.id || !channelId.value)) { emit('alert', '请先从我的作品选择作品，再选择意向渠道', 'error'); return }
  if (kind.value === 'consignment' && copyrightBasis.value === 'authorized' && !authorizationNote.value.trim()) { emit('alert', '请填写授权说明', 'error'); return }
  busy.value = true
  try {
    const body = { templateCode: templateCode.value, assetId: props.asset?.id || undefined, copyrightBasis: copyrightBasis.value, copyrightConfirmed: true, note: note.value, ...(kind.value === 'quote' ? { quantity: quantity.value, purpose: purpose.value, requestType: requestType.value } : { channelId: Number(channelId.value), authorizationNote: authorizationNote.value }) }
    await api(`/api/commercial/consumer/${kind.value === 'quote' ? 'quote-requests' : 'consignment-applications'}`, json('POST', body)); await refresh(); emit('alert', '申请已提交，后台处理后可在下方查看结果', 'success')
  } catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { busy.value = false }
}
async function act(item: any, action: 'accept' | 'guidance') {
  if (busy.value) return
  if (!window.confirm(action === 'accept' ? '确认接受此报价和交期？确认后可支付打样费用。' : '确认申请平台协助修改？费用由平台报价后另行确认支付。')) return
  busy.value = true
  try { if (action === 'accept') await api(`/api/commercial/consumer/quote-requests/${item.id}/accept`, json('POST', {})); else await api('/api/commercial/consumer/professional-guidance', json('POST', { applicationType: item.type, applicationId: item.id, note: note.value })); await refresh(); emit('alert', action === 'accept' ? '报价已确认' : '协助申请已提交', 'success') }
  catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { busy.value = false }
}
async function submitRevision() {
  if (!revisionFile.value || busy.value || !revision.value) return
  if (!/^image\/(png|jpeg|webp)$/.test(revisionFile.value.type) || revisionFile.value.size > 20 * 1024 * 1024) { emit('alert', '请选择20MB以内的PNG、JPEG或WebP作品图', 'error'); return }
  busy.value = true
  try { const form = new FormData(); form.append('file', revisionFile.value); const uploaded = await api<any>('/api/creative/ai/assets/upload', { method: 'POST', body: form }); await api('/api/commercial/consumer/application-revisions', json('POST', { applicationType: revision.value.type, applicationId: revision.value.id, assetId: uploaded.assetId, note: revisionNote.value })); revision.value = null; revisionFile.value = null; await refresh(); emit('alert', '修改后的作品已重新提交审核', 'success') }
  catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { busy.value = false }
}
function chooseFile(event: Event) { revisionFile.value = (event.target as HTMLInputElement).files?.[0] || null }
defineExpose({ refresh })
onMounted(load)
</script>
<template>
  <section class="commercial-workspace"><header><div><h2>询价与渠道合作</h2><p>产品报价、版权确认、平台协助和支付记录</p></div><button :disabled="busy" @click="load">刷新</button></header><p v-if="error" role="alert">{{ error }}</p>
    <form class="request-form" @submit.prevent="submit"><p v-if="asset">已选择：{{ asset.title || asset.productName || `作品 ${asset.id}` }} · {{ asset.productNo }}</p><p v-else>渠道合作需先从「我的作品」选择作品。</p><div class="form-grid"><label>申请类型<select v-model="kind"><option value="quote">产品询价</option><option value="consignment">渠道合作</option></select></label><label>产品<select v-model="templateCode"><option v-for="item in products" :key="item.templateCode" :value="item.templateCode">{{ item.productName }}</option></select></label><template v-if="kind === 'quote'"><label>合作方式<select v-model="requestType"><option value="sample">打样</option><option value="bulk">批量生产</option><option value="personal">个人定制</option></select></label><label>数量<input v-model.number="quantity" type="number" min="1" max="100000" /></label><label>用途<select v-model="purpose"><option value="personal">自用</option><option value="channel_sale">渠道售卖</option><option value="museum_sale">博物馆售卖</option></select></label></template><label>版权依据<select v-model="copyrightBasis"><option value="original">原创</option><option value="authorized">已取得授权</option><option value="public_domain">公共领域</option></select></label></div><p v-if="product">{{ product.material }} · {{ product.process }} · {{ product.specification }}<br />参考打样费：¥{{ money(product.sampleFeeYuan) }} · {{ product.sampleLeadTime || '以正式报价为准' }}</p>
      <template v-if="kind === 'consignment'"><h3>意向渠道</h3><div class="filters"><input v-model="channelSearch" placeholder="搜索博物馆或景区" /><select v-model="province"><option value="">全部省份</option><option v-for="item in provinces" :key="item">{{ item }}</option></select></div><select v-model="channelId" required><option value="">请选择渠道</option><option v-for="item in visibleChannels" :key="item.id" :value="String(item.id)">{{ item.name }} · {{ item.city || item.province }}</option></select><small>渠道信息供意向选择，合作资格与结果以实际审核为准。</small><label v-if="copyrightBasis === 'authorized'">授权说明<textarea v-model="authorizationNote" maxlength="1200" required /></label></template>
      <label>补充说明<textarea v-model="note" maxlength="1200" /></label><p v-if="product?.copyrightStatement" class="copyright">{{ product.copyrightStatement }}</p><label class="checkbox"><input v-model="copyrightConfirmed" type="checkbox" />我确认已合法取得作品与素材的使用授权，并配合平台审核。</label><button :disabled="busy || !templateCode">{{ busy ? '正在处理…' : '提交申请' }}</button></form>
    <h3>我的申请</h3><p v-if="!records.length && !busy">暂无申请</p><article v-for="item in records" :key="`${item.type}-${item.id}`" class="record"><header><div><b>{{ item.productName || item.guidanceNo || item.requestNo || item.applicationNo }}</b><small>{{ item.requestNo || item.applicationNo || item.guidanceNo }} · {{ dateText(item.createdAt) }}</small></div><span>{{ labels[item.status] || item.status }}</span></header><p v-if="item.quotedTotalPrice != null">报价：¥{{ money(item.quotedTotalPrice) }} · 交期：{{ item.quotedLeadTime || '待确认' }}</p><p v-if="item.quotedFeeYuan != null">协助费用：¥{{ money(item.quotedFeeYuan) }}</p><p v-if="item.operatorComment || item.guidanceResult">{{ item.operatorComment || item.guidanceResult }}</p><p v-if="item.channelName">意向渠道：{{ item.channelName }}</p><p v-if="item.samplePaymentStatus || item.paymentStatus">支付状态：{{ labels[item.samplePaymentStatus || item.paymentStatus] || item.samplePaymentStatus || item.paymentStatus }}</p><div class="actions"><button v-if="item.type === 'quote' && item.status === 'quoted'" :disabled="busy" @click="act(item, 'accept')">接受报价</button><button v-if="item.type === 'quote' && item.status === 'accepted' && ['unpaid', 'pending'].includes(item.samplePaymentStatus)" @click="emit('pay', { ...item, paymentKind: 'quote' })">支付打样费</button><button v-if="item.type === 'guidance' && ['unpaid', 'pending'].includes(item.paymentStatus) && Number(item.quotedFeeYuan) > 0" @click="emit('pay', { ...item, paymentKind: 'guidance' })">支付协助费用</button><template v-if="['quote', 'consignment'].includes(item.type) && ['rejected', 'need_materials'].includes(item.status)"><button :disabled="busy" @click="revision = item; revisionFile = null; revisionNote = ''">修改后重新提交</button><button :disabled="busy" @click="act(item, 'guidance')">申请平台协助</button></template></div></article>
    <div v-if="revision" class="mask" @click.self="!busy && (revision = null)"><form @submit.prevent="submitRevision"><h3>上传修改后的作品</h3><input type="file" accept="image/png,image/jpeg,image/webp" :disabled="busy" @change="chooseFile" /><label>修改说明<textarea v-model="revisionNote" maxlength="1200" /></label><button :disabled="busy || !revisionFile">重新提交审核</button><button type="button" :disabled="busy" @click="revision = null">取消</button></form></div>
  </section>
</template>
<style scoped>
.commercial-workspace{max-width:1100px;margin:auto;padding:28px;background:#f4faf7;border-radius:24px;color:#2c4738}.commercial-workspace header{display:flex;align-items:center;justify-content:space-between;gap:16px}.request-form,.record{padding:24px;margin:20px 0;border:1px solid #dfebe3;border-radius:20px;background:white}.commercial-workspace label{display:grid;gap:8px;margin:14px 0}.commercial-workspace input,.commercial-workspace select,.commercial-workspace textarea,.commercial-workspace button{font:inherit;padding:11px 14px;border:1px solid #d3e5da;border-radius:12px;color:#46765a;background:white;box-sizing:border-box;max-width:100%}.commercial-workspace textarea{min-height:85px;resize:vertical}.commercial-workspace button{cursor:pointer}.commercial-workspace button:disabled{opacity:.5}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 20px}.commercial-workspace .checkbox{display:flex;align-items:flex-start;line-height:1.7}.commercial-workspace .checkbox input{margin-top:6px}.commercial-workspace small{display:block;color:#8a9d90;font-size:12px;margin:8px 0}.actions,.filters{display:flex;gap:10px;flex-wrap:wrap}.record p,.request-form p{line-height:1.8;font-size:14px}.copyright{color:#8d7857}.mask{position:fixed;inset:0;z-index:100;background:#12362780;display:grid;place-items:center;padding:20px}.mask form{padding:26px;border-radius:20px;width:min(560px,100%);box-sizing:border-box;background:white}@media(max-width:600px){.commercial-workspace{padding:16px}.request-form,.record{padding:16px}.form-grid{grid-template-columns:1fr}}
</style>
