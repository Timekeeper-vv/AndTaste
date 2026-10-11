<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { consumerApi as api, consumerJson as json, dateText, money, rows, validateZip } from '../utils/consumerApi'
import productArt from '../../../shixun-miniapp/src/static/ui-design/product-making.jpg'
import channelArt from '../../../shixun-miniapp/src/static/ui-design/channel-cooperation.jpg'

const props = defineProps<{ mode: 'personal' | 'museum_sale' }>()
const emit = defineEmits<{ alert: [message: string, type?: 'success' | 'error']; pay: [record: any]; transfer: []; revise: [text: string] }>()
const isChannel = computed(() => props.mode === 'museum_sale')
const heading = computed(() => isChannel.value ? '渠道合作' : '产品智造')
const records = ref<any[]>([])
const channels = ref<any[]>([])
const loading = ref(false)
const loadError = ref('')
const busy = ref(false)
const page = ref<'intro' | 'upload' | 'detail'>('intro')
const selectedId = ref<number | null>(null)
const record = computed(() => records.value.find(item => Number(item.id) === selectedId.value) || null)
const stage = ref(1)
const steps = ['上传作品', '平台审核', '博物馆审核', '打样', '上架销售']
const file = ref<File | null>(null)
const form = ref({ title: '', note: '', productInfo: '', museumId: '', copyright: false })
const province = ref('')
const channelKeyword = ref('')
const provinces = computed(() => [...new Set(channels.value.map(item => String(item.province || '')).filter(Boolean))])
const availableChannels = computed(() => channels.value.filter(item => (!province.value || item.province === province.value)
  && (!channelKeyword.value.trim() || String(item.name).includes(channelKeyword.value.trim()))))
const selectedChannel = computed(() => channels.value.find(item => String(item.id) === form.value.museumId))
const revisionFile = ref<File | null>(null)
const replacementChannelId = ref('')
const sampleOpen = ref(false)
const sample = ref({ quantity: 1, recipientName: '', recipientPhone: '', recipientAddress: '' })
const sampleAddressLocked = computed(() => record.value?.samplePaymentStatus === 'pending' && !!record.value?.recipientName)
const canSample = computed(() => record.value?.status === 'approved' && (!isChannel.value || record.value.museumReviewStatus === 'approved')
  && ['unpaid', 'pending'].includes(String(record.value.samplePaymentStatus)))
const quoteTotal = computed(() => money(Number(record.value?.quotedSampleFeeYuan || 0) * Number(sample.value.quantity || 1)))
const currentStage = computed(() => {
  if (!record.value) return 0
  if (record.value.samplePaymentStatus === 'paid' || record.value.status === 'processing') return 3
  if (record.value.channelRequestStatus === 'requested' || ['review', 'approved', 'rejected'].includes(record.value.museumReviewStatus)) return 2
  return 1
})
function label(item: any) {
  if (item.status === 'processing') return '生产中'
  if (item.samplePaymentStatus === 'paid') return '打样已支付'
  if (item.status === 'rejected') return '平台未通过'
  if (item.museumReviewStatus === 'rejected') return '馆方未通过'
  if (item.museumReviewStatus === 'review') return '馆方审核中'
  if (item.status === 'approved') return item.purpose !== 'museum_sale' || item.museumReviewStatus === 'approved' ? '待打样' : '平台通过'
  return '审核中'
}
function stateClass(item: any) { return item.status === 'rejected' || item.museumReviewStatus === 'rejected' ? 'danger' : item.status === 'review' ? 'pending' : 'success' }
const platformPassed = computed(() => ['approved', 'processing'].includes(String(record.value?.status)))
const stageTitle = computed(() => {
  if (!isChannel.value) {
    if (record.value?.samplePaymentStatus === 'paid') return '打样申请已支付'
    if (record.value?.status === 'processing') return '打样进行中'
    return record.value?.status === 'rejected' ? '平台审核未通过' : platformPassed.value ? '平台审核通过' : '平台审核中'
  }
  if (stage.value === 1) return record.value?.status === 'rejected' ? '平台审核未通过' : platformPassed.value ? '平台审核通过' : '平台审核中'
  if (stage.value === 0) return '作品已上传'
  if (stage.value === 2) return ({ approved: '博物馆审核通过', rejected: '博物馆审核未通过', review: `${record.value?.museumName || '馆方'}审核中` } as Record<string, string>)[record.value?.museumReviewStatus] || '提交渠道方审核'
  if (stage.value === 3) return record.value?.samplePaymentStatus === 'paid' ? '打样申请已支付' : record.value?.status === 'processing' ? '打样进行中' : '等待申请打样'
  return '等待上架销售'
})
const reasons = computed(() => String(isChannel.value && stage.value === 2 ? record.value?.museumReviewComment || '' : record.value?.reviewComment || '')
  .split(/[\n；;]+/).map(text => text.replace(/^[-×xX•\s]+/, '').trim()).filter(Boolean))
const rejectedAtStage = computed(() => record.value && (isChannel.value && stage.value === 2 ? record.value.museumReviewStatus === 'rejected' : stage.value === 1 && record.value.status === 'rejected'))

async function load() {
  loading.value = true; loadError.value = ''
  try {
    records.value = rows(await api('/api/creative/ai/consumer-professional-submissions/my')).filter(item => props.mode === 'museum_sale' ? item.purpose === 'museum_sale' : item.purpose !== 'museum_sale')
  } catch (error: any) { loadError.value = error.message }
  finally { loading.value = false }
}
async function loadChannels() {
  try { channels.value = rows(await api('/api/creative/ai/consumer-production/museums')) }
  catch (error: any) { emit('alert', error.message, 'error') }
}
function openRecord(item: any) {
  selectedId.value = Number(item.id); page.value = 'detail'; sampleOpen.value = false; revisionFile.value = null
  stage.value = currentStage.value
  sample.value = { quantity: Number(item.sampleQuantity || 1), recipientName: item.recipientName || '', recipientPhone: item.recipientPhone || '', recipientAddress: item.recipientAddress || '' }
}
function chooseFile(event: Event, revision = false) {
  const input = event.target as HTMLInputElement
  const chosen = input.files?.[0] || null
  if (!chosen) return
  try {
    validateZip(chosen)
    if (revision) revisionFile.value = chosen
    else { file.value = chosen; if (!form.value.title) form.value.title = chosen.name.replace(/\.zip$/i, '') }
  } catch (error: any) { emit('alert', error.message, 'error'); input.value = '' }
}
async function submit() {
  if (busy.value) return
  try {
    validateZip(file.value)
    if (!form.value.title.trim() || !form.value.note.trim() || !form.value.productInfo.trim()) throw new Error('请完整填写作品名称、介绍和产品信息')
    if (!form.value.copyright) throw new Error('请先确认作品及素材的使用授权')
    if (isChannel.value && !selectedChannel.value) throw new Error('请选择意向渠道')
    busy.value = true
    const data = new FormData()
    data.append('file', file.value); data.append('title', form.value.title.trim())
    data.append('note', `${form.value.note.trim()}\n\n产品信息：${form.value.productInfo.trim()}`)
    data.append('purpose', props.mode)
    if (isChannel.value) { data.append('museumId', String(selectedChannel.value.id)); data.append('museumName', selectedChannel.value.name) }
    const result = await api('/api/creative/ai/consumer-professional-submissions', { method: 'POST', body: data })
    file.value = null; form.value = { title: '', note: '', productInfo: '', museumId: '', copyright: false }
    await load()
    const submitted = records.value.find(item => item.submissionNo === result.submissionNo)
    if (submitted) openRecord(submitted); else page.value = 'intro'
    emit('alert', '作品包已提交审核', 'success')
  } catch (error: any) { emit('alert', error.message, 'error') }
  finally { busy.value = false }
}
async function action(suffix: string, method = 'POST', body?: unknown) {
  if (!record.value?.id || busy.value) return
  busy.value = true
  try {
    await api(`/api/creative/ai/consumer-professional-submissions/${record.value.id}/${suffix}`, json(method, body))
    await load(); sampleOpen.value = false; emit('alert', '操作成功，进度已更新', 'success')
    if (suffix === 'transfer-product-making') emit('transfer')
  } catch (error: any) { emit('alert', error.message, 'error') }
  finally { busy.value = false }
}
async function resubmit() {
  if (!record.value?.id || busy.value) return
  try {
    validateZip(revisionFile.value); busy.value = true
    const data = new FormData(); data.append('file', revisionFile.value)
    const suffix = isChannel.value && stage.value === 2 ? 'museum-resubmit' : 'resubmit'
    await api(`/api/creative/ai/consumer-professional-submissions/${record.value.id}/${suffix}`, { method: 'POST', body: data })
    await load(); revisionFile.value = null; emit('alert', '修改后的作品包已重新提交审核', 'success')
  } catch (error: any) { emit('alert', error.message, 'error') }
  finally { busy.value = false }
}
async function changeChannel() {
  const chosen = channels.value.find(item => String(item.id) === replacementChannelId.value)
  if (!chosen || chosen.name === record.value?.museumName) return emit('alert', '请选择其他博物馆或景区', 'error')
  await action('channel', 'PUT', { museumId: String(chosen.id), museumName: chosen.name })
}
async function download() {
  if (!record.value?.id || busy.value) return
  busy.value = true
  try {
    const access = await api(`/api/creative/ai/consumer-professional-submissions/${record.value.id}/download-access`, json('POST'))
    const url = new URL(access.url, window.location.origin)
    if (url.origin !== window.location.origin || url.pathname !== `/api/creative/ai/consumer-professional-submissions/${record.value.id}/download` || !url.searchParams.get('access_token')) throw new Error('下载地址无效')
    const link = document.createElement('a'); link.href = url.href; link.download = record.value.originalName || '作品包.zip'; link.click()
  } catch (error: any) { emit('alert', error.message, 'error') }
  finally { busy.value = false }
}
async function saveAndPay() {
  if (!record.value?.id || busy.value) return
  try {
    if (!sample.value.recipientName.trim() || !sample.value.recipientAddress.trim()) throw new Error('请填写收件人和收件地址')
    if (!/^[0-9+()\- ]{6,40}$/.test(sample.value.recipientPhone)) throw new Error('请填写正确的收件电话')
    if (!Number.isInteger(sample.value.quantity) || sample.value.quantity < 1 || sample.value.quantity > 10) throw new Error('打样数量须为 1–10 件')
    busy.value = true
    // Pending orders have immutable prices; only an older order with no address can be completed.
    if (record.value.samplePaymentStatus === 'unpaid' || !record.value.recipientName) {
      await api(`/api/creative/ai/consumer-professional-submissions/${record.value.id}/sample-application`, json('PUT', sample.value))
      await load()
    }
    emit('pay', { ...record.value, professionalPayment: true })
  } catch (error: any) { emit('alert', error.message, 'error') }
  finally { busy.value = false }
}
watch(() => props.mode, () => { page.value = 'intro'; selectedId.value = null; stage.value = 1; sampleOpen.value = false; void load() })
onMounted(() => { void load(); void loadChannels() })
defineExpose({ refresh: load })
</script>

<template>
  <section class="submission-workspace">
    <header class="section-toolbar"><h2>{{ heading }}</h2><div><button :disabled="busy" @click="page = 'intro'">我的提交</button><button :disabled="loading || busy" @click="load">刷新进度</button><button :disabled="busy" @click="page = 'upload'; stage = 0">＋ 提交新作品</button></div></header>
    <div v-if="page === 'intro'" class="submission-hero"><img :src="isChannel ? channelArt : productArt" :alt="heading" /><h1>{{ heading }}</h1><p>让成熟创意，走向产品落地</p><strong>{{ isChannel ? '上传已有作品，平台审核后提报博物馆；通过后打样、上架渠道售卖。' : '上传已有三视图作品包，一站式完成审核、打样、生产与渠道合作。' }}</strong><button class="primary" @click="page = 'upload'; stage = 0">确认上传</button><small>仅支持 ZIP 格式 · 不超过 100MB · 包含三视图、尺寸、材质工艺说明与版权材料</small></div>
    <nav v-if="isChannel && page !== 'intro'" class="submission-steps" aria-label="渠道合作五个阶段"><button v-for="(step, index) in steps" :key="step" :disabled="busy || (page === 'upload' && index !== 0)" :class="{ selected: stage === index, done: record && index <= currentStage }" @click="stage = index"><i>{{ index + 1 }}</i>{{ step }}</button></nav>
    <p v-if="loadError" class="error">{{ loadError }} <button @click="load">重新加载</button></p>
    <template v-if="page === 'upload'">
      <form class="submission-form" @submit.prevent="submit">
        <h2>上传作品</h2><p>审核通过后即可申请打样、生产或进入渠道合作。</p>
        <fieldset :disabled="busy"><h3>01 上传作品包 <small>必填</small></h3><label class="file-picker"><input type="file" accept=".zip,application/zip" @change="chooseFile($event)" /><b>{{ file?.name || '选择作品包文件' }}</b><span>{{ file ? `${(file.size / 1024 / 1024).toFixed(1)} MB` : 'ZIP 格式 · 不超过 100MB' }}</span></label><p>✓ 效果图（三视角）　✓ 尺寸规格　✓ 材质与工艺说明　✓ 版权材料</p>
        <h3>02 作品信息</h3><label>作品名称 *<input v-model.trim="form.title" maxlength="100" required placeholder="如：青铜纹样冰箱贴" /></label><label>作品介绍 *<textarea v-model.trim="form.note" maxlength="1200" required placeholder="创作理念、文化来源、设计思路……" /></label><label>产品信息 *<input v-model.trim="form.productInfo" maxlength="300" required placeholder="如：60×60×3mm · 锌合金 · 珐琅工艺 · 首批500个" /></label>
        <template v-if="isChannel"><h3>03 意向渠道 <small>单选</small></h3><div class="channel-filter"><select v-model="province"><option value="">全部地区</option><option v-for="item in provinces" :key="item">{{ item }}</option></select><input v-model="channelKeyword" placeholder="搜索博物馆或景区" /></div><div class="channel-choices"><label v-for="item in availableChannels" :key="item.id" :class="{ chosen: String(item.id) === form.museumId }"><input v-model="form.museumId" type="radio" :value="String(item.id)" /><b>{{ item.name }}</b><small>{{ [item.province, item.city].filter(Boolean).join(' · ') }} · {{ item.channelType === 'scenic_spot' ? '景区' : '博物馆' }}</small></label><p v-if="!availableChannels.length">暂无匹配渠道，请修改筛选条件</p></div></template>
        <label class="copyright"><input v-model="form.copyright" type="checkbox" />我确认已合法取得作品及素材的使用授权，并同意配合平台与馆方审核。</label><button type="submit" class="primary">{{ busy ? '提交中…' : '提交审核' }}</button></fieldset>
      </form>
    </template>
    <template v-if="page === 'detail' && record">
      <div class="stage-banner" :class="stateClass(record)"><h2>{{ stageTitle }}</h2><p>{{ record.title }} · {{ record.museumName || '产品打样' }}</p></div>
      <article class="submission-card"><div><h3>{{ record.title || record.originalName }}</h3><small>{{ record.submissionNo }} · {{ record.productNo }} · {{ dateText(record.createdAt) }}</small></div><b :class="stateClass(record)">{{ label(record) }}</b><button :disabled="busy" @click="download">下载作品包</button></article>
      <section v-if="rejectedAtStage" class="detail-card"><h3 class="danger">审核未通过原因</h3><p v-for="reason in reasons" :key="reason">× {{ reason }}</p><p v-if="!reasons.length">请根据审核意见完善作品包。</p><h3>自行修改后重新提交</h3><label class="file-picker"><input type="file" accept=".zip" @change="chooseFile($event, true)" /><b>{{ revisionFile?.name || '选择修改后的 ZIP 作品包' }}</b></label><button class="primary" :disabled="busy || !revisionFile" @click="resubmit">重新提交审核</button>
        <template v-if="isChannel && stage === 2"><h3>改报其他渠道</h3><select v-model="replacementChannelId"><option value="">请选择其他渠道</option><option v-for="item in channels.filter(item => item.name !== record.museumName)" :key="item.id" :value="String(item.id)">{{ item.name }} · {{ item.province }}</option></select><button :disabled="busy || !replacementChannelId" @click="changeChannel">确认更换渠道</button><p>改报其他渠道不消耗积分，平台审核结果仍有效。</p><button :disabled="busy" @click="action('transfer-product-making')">转入产品智造</button></template>
        <button v-else :disabled="busy" @click="emit('revise', `请根据以下审核意见，帮我整理这件产品的修改方案：\n${reasons.join('\n')}`)">让创意工坊帮我整理修改方案</button>
      </section>
      <section v-else class="detail-card">
        <template v-if="isChannel && stage === 0"><h3>作品已上传</h3><p>{{ record.originalName }} · {{ record.note }}</p></template>
        <template v-else-if="isChannel && stage === 2"><p>{{ record.museumReviewStatus === 'approved' ? '馆方审核已通过，可以申请打样。' : record.channelRequestStatus === 'requested' ? '馆方正在评估作品主题与版权，请耐心等待。' : '平台审核通过后，请确认提报所选渠道。' }}</p><button v-if="platformPassed && record.channelRequestStatus !== 'requested'" class="primary" :disabled="busy" @click="action('channel-request')">提交「{{ record.museumName }}」审核</button></template>
        <template v-else-if="isChannel && stage === 4"><h3>等待上架销售</h3><p>当前尚无上架结果，馆方审核通过及支付打样费不代表商品已上架。</p></template>
        <template v-else-if="isChannel && stage === 1 && platformPassed"><p>{{ record.channelRequestStatus === 'requested' ? '平台审核已通过，作品已提报馆方，可在博物馆审核阶段查看结果。' : '平台审核已通过，请确认提报所选渠道。' }}</p><button v-if="record.channelRequestStatus !== 'requested'" class="primary" :disabled="busy" @click="action('channel-request'); stage = 2">提交「{{ record.museumName }}」审核</button></template>
        <p v-else>{{ record.samplePaymentStatus === 'paid' ? '打样费已支付，申请已进入生产流程，请等待后续安排。' : platformPassed ? '平台审核已通过，请确认报价和收件信息后申请打样。' : '作品正在平台审核中，请耐心等待。' }}</p>
        <template v-if="canSample && (!isChannel || stage === 2 || stage === 3)"><p>单件打样费 ¥{{ money(record.quotedSampleFeeYuan) }} · {{ record.quotedSampleLeadTime }}</p><p>{{ record.quotedSampleNote }}</p><button class="primary" @click="sampleOpen = !sampleOpen">{{ sampleOpen ? '收起打样申请' : '申请打样' }}</button></template>
      </section>
      <form v-if="sampleOpen && canSample" class="detail-card" @submit.prevent="saveAndPay"><h3>打样申请</h3><fieldset :disabled="busy"><label>数量（1–10件）<input v-model.number="sample.quantity" type="number" min="1" max="10" step="1" :disabled="record.samplePaymentStatus !== 'unpaid'" /></label><label>收件人 *<input v-model.trim="sample.recipientName" :disabled="sampleAddressLocked" maxlength="120" required /></label><label>收件地址 *<textarea v-model.trim="sample.recipientAddress" :disabled="sampleAddressLocked" maxlength="500" required /></label><label>收件电话 *<input v-model.trim="sample.recipientPhone" :disabled="sampleAddressLocked" maxlength="40" required /></label><button class="primary" type="submit">支付 ¥{{ quoteTotal }}，提交打样申请</button></fieldset></form>
    </template>
    <section v-if="page === 'intro' || page === 'detail'" class="submission-history"><h3>我的提交</h3><p v-if="loading">正在加载…</p><p v-else-if="!records.length && !loadError">暂无提交，上传作品包后可在这里查看审核结果。</p><button v-for="item in records" :key="item.id" class="submission-card"  :disabled="busy" @click="openRecord(item)"><span><strong>{{ item.title || item.originalName }}</strong><small>{{ item.submissionNo }} · {{ dateText(item.createdAt) }}</small></span><b :class="stateClass(item)">{{ label(item) }} ›</b></button></section>
    <p class="review-tip">平台将在 1–2 个工作日内审核作品完整性、版权信息与生产可行性，审核结果会同步到我的提交。</p>
  </section>
</template>

<style scoped>
.submission-workspace{padding:26px;background:linear-gradient(150deg,#fff,#effaf7);border-radius:24px;color:#23342e}.section-toolbar{display:flex;justify-content:space-between;gap:16px;align-items:center}.section-toolbar div{display:flex;flex-wrap:wrap;gap:8px}button{padding:10px 16px;border:1px solid #cfe4dc;border-radius:14px;background:#fff;color:#2e7968;cursor:pointer}button:disabled,fieldset:disabled{opacity:.6;cursor:wait}.submission-hero{max-width:640px;margin:30px auto 48px;text-align:center;display:flex;flex-direction:column;align-items:center;gap:18px}.submission-hero img{width:124px;height:136px;object-fit:cover;border-radius:24px}.submission-hero h1{margin:0;font-size:36px}.submission-hero p{font-size:24px;font-weight:300;color:#9ba8a2;margin:0}.submission-hero strong{line-height:1.9;font-size:18px}.submission-hero small{color:#85958c}.primary{background:linear-gradient(120deg,#62eac3,#429a93);color:white;border:0;min-height:48px;font-size:18px}.submission-hero .primary{width:300px;margin-top:22px}.submission-steps{display:grid;grid-template-columns:repeat(5,1fr);margin:30px 0;gap:8px}.submission-steps button{display:flex;flex-direction:column;align-items:center;gap:9px;border:0;background:transparent;color:#7f8e88}.submission-steps i{display:grid;place-items:center;width:40px;height:40px;border-radius:50%;background:#edf1ef;font-style:normal}.submission-steps .done{color:#2baf93}.submission-steps .done i{background:#d8f2e9}.submission-steps .selected i{outline:2px solid #35b59a;background:#51caae;color:#fff}.submission-form,.detail-card{max-width:790px;margin:24px auto;padding:28px;border-radius:24px;background:#fff;border:1px solid #e4f5ee;box-shadow:0 5px 20px #299e8e09}fieldset{border:0;padding:0;margin:0;min-width:0}h3 small{float:right;color:#95a39c;font-size:13px;font-weight:400}label:not(.copyright){display:flex;flex-direction:column;gap:8px;margin:18px 0;font-weight:600}input:not([type=radio]):not([type=checkbox]),textarea,select{width:100%;box-sizing:border-box;padding:12px;border:1px solid #d8e3dd;border-radius:14px;background:#f7f9f8;color:#263d32;font:inherit}textarea{min-height:100px;resize:vertical}.file-picker{text-align:center;min-height:120px;align-items:center;justify-content:center;padding:20px;border:1px dashed #bacfc4;border-radius:22px;background:#f5f7f6;font-weight:400!important}.file-picker input{max-width:280px}.copyright{display:flex;gap:10px;margin:24px 0;line-height:1.6}.submission-form .primary,.detail-card .primary{display:block;width:100%;margin:22px 0 0}.channel-filter{display:flex;gap:12px}.channel-choices{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;max-height:300px;overflow:auto}.channel-choices label{padding:18px;background:#f5f7f6;border:1px solid transparent;border-radius:16px;margin:0!important;font-weight:400!important}.channel-choices label.chosen{background:#e9f9f3;border-color:#5bcbae}.channel-choices input{align-self:flex-start}.channel-choices small{color:#929e98}.submission-card{width:100%;display:flex;align-items:center;justify-content:space-between;gap:18px;box-sizing:border-box;padding:24px;margin:18px 0;background:#fff;border:1px solid #e4f5ee;border-radius:24px;text-align:left}.submission-card strong,.submission-card small{display:block}.submission-card h3{margin:0 0 8px}.submission-card small{margin-top:8px;color:#8a9a91}.submission-card b{white-space:nowrap;border-radius:24px;padding:10px 18px;background:#eef6f1;font-size:14px}.danger{color:#df4845}.success{color:#28a78b}.pending{color:#d19333}.stage-banner{margin:26px 0 36px}.stage-banner p,.detail-card p{color:#6b7f74;line-height:1.8}.detail-card>button:not(.primary){margin:12px 10px 0 0}.review-tip{max-width:750px;margin:42px auto 12px;font-size:13px;line-height:1.8;color:#84968a}.error{color:#c3443e}@media(max-width:640px){.submission-workspace{padding:16px}.section-toolbar{align-items:flex-start;flex-direction:column}.submission-steps{gap:0}.submission-steps button{font-size:11px;padding:8px 0}.submission-card{padding:16px;flex-wrap:wrap}.submission-form,.detail-card{padding:18px}.submission-hero p{font-size:20px}.channel-filter{flex-direction:column}.channel-choices{grid-template-columns:1fr}.submission-hero .primary{width:100%}}
</style>
