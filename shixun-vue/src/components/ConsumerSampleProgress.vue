<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { consumerApi as api, consumerJson as json, dateText, rows } from '../utils/consumerApi'
import { requestAssetPreviewAccess } from '../utils/assetAccess'
const props = defineProps<{ request: any }>()
const emit = defineEmits<{ alert: [message: string, type?: 'success' | 'error']; updated: []; bulk: [request: any] }>()
const lifecycle = ref<any>(null)
const logistics = ref<any>(null)
const workflow = ref<any>(null)
const loading = ref(false)
const error = ref('')
const busy = ref(false)
const form = ref({ rating: 0, comment: '', issueTags: [] as string[] })
const evidence = ref<Array<{ assetId: number; previewUrl: string }>>([])
const issueOptions = ['尺寸不符', '颜色偏差', '材质不符', '结构问题', '细节缺失', '包装 / 配件']
const state = computed(() => String(lifecycle.value?.sampleWorkflowStatus || 'not_started'))
const stateLabels: Record<string, string> = { not_started: '等待样品', in_production: '生产中', ready_to_ship: '已出样，待发货', shipped: '已发货，待反馈', received: '待反馈', revision_required: '待返修', revision_in_progress: '返修处理中', revision_completed: '返修完成，待出样', rejected: '已拒绝', accepted: '已验收', bulk_unlocked: '量产已解锁' }
const canFeedback = computed(() => ['shipped', 'received'].includes(state.value)
  && (['processing', 'shipped', 'completed'].includes(String(lifecycle.value?.status)) && ['paid', 'not_required'].includes(String(lifecycle.value?.samplePaymentStatus))
    || lifecycle.value?.status === 'approved' && lifecycle.value?.samplePaymentStatus === 'not_required'))
function base() {
  if (!props.request.projectId || !props.request.versionId || !props.request.id) throw new Error('当前申请未关联项目版本，无法打开样品进度')
  return `/api/creative/projects/${encodeURIComponent(props.request.projectId)}/versions/${encodeURIComponent(props.request.versionId)}/sample-lifecycle/${encodeURIComponent(props.request.id)}`
}
async function load() {
  loading.value = true; error.value = ''
  try {
    const path = base()
    const [sampleData, shipping, detail] = await Promise.all([api(path), api(`${path}/logistics`).catch(() => null), api(`/api/creative/workflow/requests/${props.request.id}`).catch(() => null)])
    lifecycle.value = sampleData; logistics.value = shipping; workflow.value = detail
  } catch (failure: any) { error.value = failure.message }
  finally { loading.value = false }
}
async function uploadEvidence(event: Event) {
  const input = event.target as HTMLInputElement
  if (busy.value) return
  busy.value = true
  try {
    for (const file of Array.from(input.files || []).slice(0, 3 - evidence.value.length)) {
      if (!/^image\/(png|jpeg|webp)$/.test(file.type) || !file.size || file.size > 20 * 1024 * 1024) throw new Error('请选择20MB以内的PNG、JPEG或WebP样品照片')
      const data = new FormData(); data.append('file', file); data.append('projectId', String(props.request.projectId)); data.append('versionId', String(props.request.versionId))
      const uploaded = await api('/api/creative/ai/assets/upload', { method: 'POST', body: data })
      const assetId = Number(uploaded.assetId)
      if (!assetId) throw new Error('照片上传后未返回作品编号')
      const access = await requestAssetPreviewAccess(assetId)
      evidence.value.push({ assetId, previewUrl: access.previewUrl })
    }
  } catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { busy.value = false; input.value = '' }
}
async function perform(action: 'revision' | 'feedback' | 'accept' | 'bulk-unlock') {
  if (busy.value) return
  if ((action === 'revision' || action === 'feedback') && !form.value.comment.trim() && !form.value.issueTags.length) return emit('alert', '请填写问题说明或选择问题标签', 'error')
  const confirmation = ({ revision: '提交返修反馈', feedback: '反馈不符合预期', accept: '确认样品验收', 'bulk-unlock': '解锁批量生产' })[action]
  if (!window.confirm(`${confirmation}？提交后会保留处理记录。`)) return
  busy.value = true
  try {
    await api(`${base()}/${action}`, json('POST', {
      ...form.value, evidenceAssetIds: evidence.value.map(item => item.assetId), ...(action === 'feedback' ? { decision: 'reject' } : {}),
    }))
    await load(); emit('updated'); emit('alert', `${confirmation}成功`, 'success')
    form.value = { rating: 0, comment: '', issueTags: [] }; evidence.value = []
  } catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { busy.value = false }
}
watch(() => props.request.id, () => { lifecycle.value = null; form.value = { rating: 0, comment: '', issueTags: [] }; evidence.value = []; void load() })
onMounted(load)
</script>
<template>
  <section class="sample-progress"><header><div><small>{{ request.productNo || request.requestNo }}</small><h2>{{ request.title || request.assetTitle || '样品进度' }}</h2></div><button :disabled="loading || busy" @click="load">刷新进度</button></header>
    <p v-if="loading">正在加载样品进度…</p><p v-if="error" class="error">{{ error }}</p>
    <template v-if="lifecycle"><article><h3>{{ workflow?.flow?.label || stateLabels[state] || state }}</h3><p>{{ workflow?.flow?.nextAction || '收到样品后可记录反馈、申请返修或确认验收。' }}</p><p v-for="blocker in rows(workflow?.flow?.blockers)" :key="blocker.code">{{ blocker.label }}：{{ blocker.reason }}</p><small v-if="lifecycle.sampleRevisionCount">已返修 {{ lifecycle.sampleRevisionCount }} 次</small></article>
    <article v-if="logistics"><h3>样品物流</h3><p>{{ logistics.carrierName || '承运商待确认' }} · {{ logistics.trackingNo || '等待发货' }}</p><p>{{ logistics.latestTrace || '录入快递单号后显示物流轨迹' }}</p><p v-if="logistics.exceptionNote" class="error">{{ logistics.exceptionNote }}</p><p v-for="trace in rows(logistics.traces)" :key="trace.id"><small>{{ dateText(trace.createdAt) }}</small> {{ trace.content }}</p></article>
    <form v-if="canFeedback || state === 'accepted'" @submit.prevent="perform('accept')"><fieldset :disabled="busy"><h3>{{ state === 'accepted' ? '样品已验收' : '样品反馈' }}</h3><label v-if="canFeedback">满意度<select v-model.number="form.rating"><option :value="0">暂不评分</option><option v-for="score in 5" :key="score" :value="score">{{ score }} / 5</option></select></label><div v-if="canFeedback" class="tags"><label v-for="tag in issueOptions" :key="tag"><input v-model="form.issueTags" type="checkbox" :value="tag" />{{ tag }}</label></div><textarea v-model.trim="form.comment" maxlength="2000" placeholder="问题说明或量产备注（可选）" />
    <label v-if="evidence.length < 3">样品照片（最多3张）<input type="file" accept="image/png,image/jpeg,image/webp" multiple @change="uploadEvidence" /></label><div class="evidence"><img v-for="item in evidence" :key="item.assetId" :src="item.previewUrl" alt="样品照片" /></div><div class="actions"><template v-if="canFeedback"><button type="button" @click="perform('revision')">提交返修</button><button type="button" @click="perform('feedback')">不符合预期</button><button class="primary" type="submit">确认验收</button></template><button v-if="state === 'accepted'" class="primary" type="button" @click="perform('bulk-unlock')">解锁批量生产</button></div></fieldset></form>
    <article v-if="state === 'bulk_unlocked'"><h3>量产已解锁</h3><p>当前验收版本可进入批量生产申请。</p><button class="primary" :disabled="busy" @click="emit('bulk', { ...request, ...lifecycle })">申请批量生产</button></article>
    <article><h3>处理记录</h3><p v-if="!lifecycle.events?.length">暂无处理记录</p><div v-for="event in [...rows(lifecycle.events)].reverse()" :key="event.id" class="event"><strong>{{ ({ received: '样品已收到', revision_requested: '发起返修', feedback: '提交反馈', accepted: '确认验收', bulk_unlocked: '解锁量产', sample_shipped: '样品已寄出', production_started: '开始制作' } as Record<string, string>)[event.eventType] || event.eventType }}</strong><small>{{ dateText(event.createdAt) }}</small><p>{{ event.comment }}</p></div></article></template>
  </section>
</template>
<style scoped>
.sample-progress{padding:24px;color:#263c30;background:#f0faf6;border-radius:24px}.sample-progress header{display:flex;align-items:center;justify-content:space-between;gap:18px}.sample-progress h2{margin:8px 0 20px}.sample-progress article,.sample-progress form{margin:18px 0;padding:24px;border:1px solid #d9efe3;border-radius:22px;background:#fff}.sample-progress p{line-height:1.7;color:#6d8074}.sample-progress small{color:#8b9a91}.sample-progress button{padding:10px 18px;border:1px solid #c8dfd1;border-radius:12px;background:#fff;color:#43745a;cursor:pointer}.sample-progress button:disabled{opacity:.5}.sample-progress .primary{background:#3dac88;color:#fff;border:0}.sample-progress label{display:flex;gap:8px;align-items:center;margin:14px 0}.sample-progress select,.sample-progress textarea{padding:12px;border:1px solid #d1e3d9;border-radius:12px;background:#f8fbf9;font:inherit}.sample-progress textarea{width:100%;box-sizing:border-box;min-height:100px}.sample-progress fieldset{border:0;padding:0;min-width:0}.tags,.actions,.evidence{display:flex;flex-wrap:wrap;gap:12px}.evidence img{width:90px;height:90px;border-radius:12px;object-fit:cover}.event{border-top:1px solid #e5eee8;padding-top:16px;margin-top:18px}.event small{margin-left:12px}.error{color:#d24943!important}@media(max-width:640px){.sample-progress{padding:16px}.sample-progress article,.sample-progress form{padding:16px}}
</style>
