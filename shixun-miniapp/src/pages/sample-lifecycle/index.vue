<template>
  <view class="page">
    <view class="head">
      <text class="eyebrow">SAMPLE TO BULK</text>
      <text class="title">样品进度</text>
      <text class="sub">收到实物后，在这里记录反馈、发起返修或确认验收。验收通过后才能解锁批量生产。</text>
      <button class="refresh-button" :loading="loading" :disabled="loading" @tap="load">刷新进度</button>
    </view>

    <view v-if="loading" class="empty">正在加载样品进度…</view>
    <view v-else-if="loadError" class="empty error-state">
      <text>{{ loadError }}</text>
      <button class="outline-button" @tap="load">重新加载</button>
    </view>
    <template v-else-if="lifecycle">
      <view class="summary-card">
        <view class="summary-head">
          <view>
            <text class="summary-kicker">{{ lifecycle.requestNo || `申请 #${requestId}` }}</text>
            <text class="summary-title">{{ productTitle }}</text>
          </view>
          <text class="status" :class="flowCode">{{ displayWorkflowLabel }}</text>
        </view>
        <view class="progress-track"><view class="progress-value" :style="{ width: `${displayProgressPercent}%` }" /></view>
        <view class="progress-labels"><text>打样</text><text>样品反馈</text><text>验收</text><text>量产解锁</text></view>
        <view class="summary-meta">
          <text v-if="lifecycle.sampleReceivedAt">收到样品：{{ formatDate(lifecycle.sampleReceivedAt) }}</text>
          <text v-if="lifecycle.sampleRevisionCount">已返修 {{ lifecycle.sampleRevisionCount }} 次</text>
          <text v-if="lifecycle.sampleAcceptedAt">验收时间：{{ formatDate(lifecycle.sampleAcceptedAt) }}</text>
          <text v-if="lifecycle.bulkUnlockedAt">量产已解锁：{{ formatDate(lifecycle.bulkUnlockedAt) }}</text>
        </view>
      </view>

      <view v-if="workflowDetail?.flow" class="next-action-card" :class="{ blocked: workflowDetail.flow.blocked }">
        <view class="next-action-head"><text>当前流程</text><text>{{ workflowDetail.flow.phaseLabel || workflowDetail.flow.label }}</text></view>
        <text class="next-action-title">{{ workflowDetail.flow.nextAction || '等待流程更新' }}</text>
        <view v-if="workflowDetail.flow.blockers?.length" class="blocker-list">
          <text v-for="item in workflowDetail.flow.blockers" :key="item.code">{{ item.label }}：{{ item.reason }}</text>
        </view>
        <text v-else class="next-action-tip">平台、工厂和物流会围绕同一版本记录进度，状态变化后这里会自动刷新。</text>
      </view>

      <view v-if="!sampleReady" class="waiting-card"><text class="waiting-title">等待样品进入制作</text><text>当前申请状态为“{{ requestStatusLabel }}”，平台审核、支付或生产安排完成后才能提交样品反馈。</text></view>

      <view v-if="logistics" class="logistics-card" :class="{ exception: logisticsException }">
        <view class="logistics-head">
          <view>
            <text class="logistics-kicker">样品物流</text>
            <text class="logistics-title">{{ logisticsStatusLabel }}</text>
          </view>
          <text v-if="logisticsException" class="logistics-alert">异常提醒</text>
          <text v-else-if="logisticsAlertLabel" class="logistics-alert warning">{{ logisticsAlertLabel }}</text>
        </view>
        <view v-if="logistics.trackingNo" class="logistics-number">
          <text>{{ logistics.carrierName || logistics.carrierCode || '承运商待确认' }}</text>
          <text selectable>运单号：{{ logistics.trackingNo }}</text>
        </view>
        <text v-else class="logistics-empty">工厂录入快递单号后，这里会显示承运商和物流轨迹。</text>
        <view v-if="logisticsException && logistics.exceptionNote" class="exception-note"><text>物流异常：</text><text>{{ logistics.exceptionNote }}</text></view>
        <view v-if="logistics.latestTrace" class="latest-trace"><text>最新轨迹</text><text>{{ logistics.latestTrace }}</text></view>
        <view class="logistics-times">
          <text v-if="logistics.shippedAt">发货：{{ formatDate(logistics.shippedAt) }}</text>
          <text v-if="logistics.estimatedArrival">预计到达：{{ formatDate(logistics.estimatedArrival) }}</text>
          <text v-if="logistics.signedAt">签收：{{ formatDate(logistics.signedAt) }}</text>
        </view>
        <view v-if="logisticsTraces.length" class="logistics-traces">
          <text class="trace-heading">物流记录</text>
          <view v-for="trace in logisticsTraces" :key="trace.id" class="trace-row" :class="{ exception: trace.alertLevel === 'exception' }">
            <view class="trace-dot" />
            <view class="trace-body"><text>{{ trace.content || '物流状态更新' }}</text><text>{{ formatDate(trace.createdAt) }}</text></view>
          </view>
        </view>
      </view>

      <view v-if="canInteract" class="feedback-card">
        <view class="section-title"><text>样品反馈</text><text>记录后平台与工厂可按同一版本继续处理</text></view>
        <text class="label">满意度（可选）</text>
        <view class="rating-row">
          <text v-for="score in ratingOptions" :key="score" class="star" :class="{ active: rating >= score }" @tap="rating = score">★</text>
          <text class="rating-value">{{ rating ? `${rating} / 5` : '暂不评分' }}</text>
          <text v-if="rating" class="clear-rating" @tap="rating = 0">清除</text>
        </view>
        <text class="label">问题标签（返修时至少选择一项或填写说明）</text>
        <view class="tag-list">
          <text v-for="tag in issueTagOptions" :key="tag" class="tag" :class="{ active: issueTags.includes(tag) }" @tap="toggleIssueTag(tag)">{{ tag }}</text>
        </view>
        <textarea v-model.trim="comment" class="textarea" maxlength="2000" placeholder="例如：尺寸偏小 2mm，凤凰尾部颜色偏暗，请按当前版本重新调整。" />
        <view class="evidence-head"><text class="label">样品照片（可选，最多 3 张）</text><text>{{ evidence.length }}/3</text></view>
        <view class="evidence-grid">
          <view v-for="item in evidence" :key="item.assetId" class="evidence-item"><image :src="item.path" mode="aspectFill" /><text>已上传</text></view>
          <view v-if="evidence.length < 3" class="evidence-add" :class="{ uploading }" @tap="chooseEvidence"><text>{{ uploading ? '上传中' : '+' }}</text><text>{{ uploading ? '请稍候' : '添加照片' }}</text></view>
        </view>
        <view class="action-grid">
          <button class="revision-button" :loading="submitting === 'revision'" :disabled="Boolean(submitting) || uploading" @tap="confirmRevision">提交返修</button>
          <button class="reject-button" :loading="submitting === 'reject'" :disabled="Boolean(submitting) || uploading" @tap="confirmReject">不符合预期</button>
          <button class="accept-button" :loading="submitting === 'accept'" :disabled="Boolean(submitting) || uploading" @tap="confirmAccept">确认验收</button>
        </view>
        <text class="feedback-tip">提交后会保留本次反馈记录；如需继续修改，可再次上传照片并提交返修。</text>
      </view>

      <view v-if="canUnlock" class="unlock-card">
        <view><text class="unlock-title">样品已验收</text><text class="unlock-copy">当前版本可以进入批量生产申请，解锁后平台会按冻结版本继续核价与排产。</text></view>
        <textarea v-model.trim="unlockComment" class="textarea compact" maxlength="2000" placeholder="量产备注（可选）" />
        <button class="unlock-button" :loading="submitting === 'unlock'" :disabled="Boolean(submitting) || uploading" @tap="confirmUnlock">解锁批量生产</button>
      </view>

      <view class="timeline-card">
        <view class="section-title"><text>处理记录</text><text>{{ lifecycle.events?.length || 0 }} 条</text></view>
        <view v-if="!lifecycle.events?.length" class="timeline-empty">暂无反馈记录，收到样品后可以开始填写。</view>
        <view v-for="event in orderedEvents" :key="event.id" class="event-row">
          <view class="event-dot" :class="eventClass(event)" />
          <view class="event-body"><view class="event-head"><text>{{ eventLabel(event) }}</text><text>{{ formatDate(event.createdAt) }}</text></view><text v-if="event.comment" class="event-comment">{{ event.comment }}</text><view v-if="eventTags(event).length" class="event-tags"><text v-for="tag in eventTags(event)" :key="tag">{{ tag }}</text></view><view v-if="eventEvidence(event).length" class="event-evidence"><image v-for="url in eventEvidence(event)" :key="url" :src="url" mode="aspectFill" /></view><text v-if="event.rating" class="event-rating">评分 {{ event.rating }} / 5</text></view>
        </view>
      </view>
    </template>
    <view v-else class="empty error-state">
      <text>暂时找不到这条样品申请，请返回“我的生产申请”重新打开。</text>
      <button class="outline-button" @tap="load">重新加载</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { acceptSample, getAssetPreviewAccess, getCreativeWorkflowDetail, getSampleLifecycle, getSampleLogistics, requestSampleRevision, submitSampleFeedback, unlockSampleBulkProduction, uploadReference, type CreativeWorkflowDetail, type SampleLifecycle, type SampleLifecycleEvent, type SampleLogistics, type SampleLogisticsTrace } from '../../api/creative'
import { requireSession } from '../../utils/session'

const projectId = ref('')
const versionId = ref('')
const requestId = ref('')
const productTitle = ref('样品申请')
const lifecycle = ref<SampleLifecycle | null>(null)
const logistics = ref<SampleLogistics | null>(null)
const workflowDetail = ref<CreativeWorkflowDetail | null>(null)
const loading = ref(false)
const loadError = ref('')
const submitting = ref<'revision' | 'reject' | 'accept' | 'unlock' | ''>('')
const uploading = ref(false)
const comment = ref('')
const unlockComment = ref('')
const rating = ref(0)
const issueTags = ref<string[]>([])
const evidence = ref<Array<{ assetId: number; path: string }>>([])
const persistedEvidence = ref<Record<string, string[]>>({})
const ratingOptions = [1, 2, 3, 4, 5]
const issueTagOptions = ['尺寸不符', '颜色偏差', '材质不符', '结构问题', '细节缺失', '包装 / 配件']

const workflowStatus = computed(() => String(lifecycle.value?.sampleWorkflowStatus || 'not_started'))
const flowCode = computed(() => String(workflowDetail.value?.flow?.code || workflowStatus.value))
const displayWorkflowLabel = computed(() => String(workflowDetail.value?.flow?.label || workflowLabel.value))
const displayProgressPercent = computed(() => {
  const value = Number(workflowDetail.value?.flow?.progressPercent ?? progressPercent.value)
  return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : 20
})
const logisticsStatusLabel = computed(() => ({
  pending: '等待发货', shipped: '已发货，等待揽收', in_transit: '运输中', delivering: '派送中', signed: '已签收', exception: '物流异常', returned: '已退回',
}[String(logistics.value?.status || 'pending')] || String(logistics.value?.status || '等待物流信息')))
const logisticsException = computed(() => String(logistics.value?.alertLevel || '') === 'exception' || String(logistics.value?.status || '') === 'exception')
const logisticsAlertLabel = computed(() => String(logistics.value?.alertLevel || '') === 'warning' ? '物流预警' : '')
const logisticsTraces = computed<SampleLogisticsTrace[]>(() => Array.isArray(logistics.value?.traces) ? logistics.value!.traces!.slice(0, 8) : [])
const sampleReady = computed(() => {
  const workflow = workflowStatus.value
  const status = String(lifecycle.value?.status || '')
  const payment = String(lifecycle.value?.samplePaymentStatus || '')
  // The backend only accepts feedback after the factory has shipped the
  // sample (or recorded it as received). Mirror that gate in the UI so users
  // do not fill a form that is guaranteed to be rejected.
  const deliveredToWorkflow = ['shipped', 'received'].includes(workflow)
  const executionStarted = ['processing', 'shipped', 'completed'].includes(status) && ['paid', 'not_required'].includes(payment)
    || status === 'approved' && payment === 'not_required'
  return deliveredToWorkflow && executionStarted
})
const requestStatusLabel = computed(() => ({ review: '待审核', approved: '审核通过，待支付', processing: '生产中', shipped: '样品已寄出', completed: '已完成' }[String(lifecycle.value?.status || '')] || String(lifecycle.value?.status || '待处理')))
const workflowLabel = computed(() => ({
  not_started: '等待样品', in_production: '生产中', ready_to_ship: '已出样，待发货', shipped: '已发货，待收货反馈', revision_required: '待返修', revision_in_progress: '返修处理中', revision_completed: '返修完成，待出样', received: '待反馈', rejected: '已拒绝', accepted: '已验收', bulk_unlocked: '量产已解锁',
}[workflowStatus.value] || workflowStatus.value))
const progressPercent = computed(() => ({ not_started: 12, in_production: 28, ready_to_ship: 42, shipped: 50, received: 58, revision_required: 45, revision_in_progress: 52, revision_completed: 58, rejected: 45, accepted: 72, bulk_unlocked: 100 }[workflowStatus.value] || 20))
const canInteract = computed(() => sampleReady.value && !['accepted', 'bulk_unlocked', 'rejected'].includes(workflowStatus.value))
const canUnlock = computed(() => workflowStatus.value === 'accepted')
const orderedEvents = computed(() => [...(lifecycle.value?.events || [])].reverse())

function formatDate(value?: string) {
  if (!value) return '刚刚'
  const normalized = String(value).replace('T', ' ').replace(/\.\d+$/, '')
  return normalized.length > 16 ? normalized.slice(0, 16) : normalized
}

function eventLabel(event: SampleLifecycleEvent) {
  const map: Record<string, string> = { production_started: '工厂开始制作样品', sample_ready: '样品已出样，等待发货', sample_shipped: '工厂已寄出样品', revision_started: '工厂开始返修', revision_completed: '返修完成，等待重新出样', received: '收到样品并待反馈', feedback: event.decision === 'revision_required' ? '提交返修反馈' : event.decision === 'reject' ? '反馈不符合预期' : '提交样品反馈', revision_requested: '发起返修', accepted: '确认验收', rejected: '样品反馈未通过', bulk_unlocked: '解锁批量生产' }
  return map[String(event.eventType || '')] || '更新样品进度'
}

function eventClass(event: SampleLifecycleEvent) {
  return event.eventType === 'accepted' || event.eventType === 'bulk_unlocked' ? 'positive' : event.eventType === 'rejected' ? 'negative' : ''
}

function parseJsonList(value: unknown): string[] {
  if (Array.isArray(value)) return value.map(item => String(item)).filter(Boolean)
  if (typeof value !== 'string' || !value.trim()) return []
  try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed.map(item => String(item)).filter(Boolean) : [] } catch { return [] }
}

function eventTags(event: SampleLifecycleEvent) { return parseJsonList(event.issueTagsJson) }
function eventEvidence(event: SampleLifecycleEvent) { return persistedEvidence.value[String(event.id)] || [] }

async function loadPersistedEvidence(value: SampleLifecycle) {
  const next: Record<string, string[]> = {}
  for (const event of Array.isArray(value.events) ? value.events : []) {
    const ids = parseJsonList(event.evidenceAssetIdsJson).map(Number).filter(id => Number.isFinite(id) && id > 0)
    if (!ids.length) continue
    const previews: string[] = []
    for (const id of ids) {
      try {
        const access = await getAssetPreviewAccess(id)
        const preview = access?.previewUrl || access?.url
        if (preview) previews.push(String(preview))
      } catch { /* keep the timeline usable if an old image is unavailable */ }
    }
    if (previews.length) next[String(event.id)] = previews
  }
  persistedEvidence.value = next
}

async function load() {
  if (!projectId.value || !versionId.value || !requestId.value) {
    loadError.value = '缺少项目版本或申请编号，无法打开样品进度。'
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const [nextLifecycle, nextLogistics, nextWorkflow] = await Promise.all([
      getSampleLifecycle(projectId.value, versionId.value, requestId.value),
      // Logistics was added after the lifecycle endpoint. Keep the progress
      // page usable while an older rolling deployment has no such endpoint.
      getSampleLogistics(projectId.value, versionId.value, requestId.value).catch(() => null),
      getCreativeWorkflowDetail(requestId.value).catch(() => null),
    ])
    if (!nextLifecycle || !nextLifecycle.id) throw new Error('样品申请不存在或已无法访问')
    lifecycle.value = nextLifecycle
    logistics.value = nextLogistics
    workflowDetail.value = nextWorkflow
    await loadPersistedEvidence(nextLifecycle)
  } catch (error: any) { loadError.value = error?.message || '样品进度暂时无法加载' } finally { loading.value = false }
}

async function refreshDerivedData() {
  const [nextLogistics, nextWorkflow] = await Promise.all([
    getSampleLogistics(projectId.value, versionId.value, requestId.value).catch(() => null),
    getCreativeWorkflowDetail(requestId.value).catch(() => null),
  ])
  // Keep the last known projection if an optional read model is temporarily
  // unavailable. The action result itself remains authoritative.
  if (nextLogistics) logistics.value = nextLogistics
  if (nextWorkflow) workflowDetail.value = nextWorkflow
  if (lifecycle.value) await loadPersistedEvidence(lifecycle.value)
}

function toggleIssueTag(tag: string) { issueTags.value = issueTags.value.includes(tag) ? issueTags.value.filter(item => item !== tag) : [...issueTags.value, tag] }

function chooseImage(): Promise<string[]> {
  return new Promise((resolve, reject) => uni.chooseImage({ count: Math.max(1, 3 - evidence.value.length), sizeType: ['compressed'], sourceType: ['album', 'camera'], success: result => {
    const paths = result.tempFilePaths
    resolve(Array.isArray(paths) ? paths : paths ? [paths] : [])
  }, fail: error => { if (/cancel/i.test(String(error?.errMsg || ''))) reject(new Error('已取消选择照片')); else reject(new Error('无法选择样品照片')) } }))
}

async function chooseEvidence() {
  if (uploading.value || evidence.value.length >= 3) return
  uploading.value = true
  try {
    const paths = await chooseImage()
    for (const path of paths.slice(0, 3 - evidence.value.length)) {
      const uploaded = await uploadReference(path, projectId.value, versionId.value)
      const assetId = Number(uploaded?.assetId)
      if (!Number.isFinite(assetId) || assetId <= 0) throw new Error('样品照片上传后未返回作品编号')
      evidence.value = [...evidence.value, { assetId, path }]
    }
    if (paths.length) uni.showToast({ title: '样品照片已上传', icon: 'success' })
  } catch (error: any) {
    if (error?.message !== '已取消选择照片') uni.showToast({ title: error?.message || '样品照片上传失败', icon: 'none' })
  } finally { uploading.value = false }
}

function body(extra: Record<string, any> = {}) { return { ...extra, rating: rating.value || 0, comment: comment.value, issueTags: issueTags.value, evidenceAssetIds: evidence.value.map(item => item.assetId) } }

function confirmRevision() {
  if (!comment.value && !issueTags.value.length) return uni.showToast({ title: '请填写返修说明或选择问题标签', icon: 'none' })
  uni.showModal({ title: '提交返修反馈', content: '提交后工厂会按当前冻结版本处理返修，确认继续吗？', confirmText: '提交', success: result => { if (result.confirm) void performRevision() } })
}

function confirmReject() {
  if (!comment.value && !issueTags.value.length) return uni.showToast({ title: '请填写问题说明或选择问题标签', icon: 'none' })
  uni.showModal({ title: '标记为不符合预期', content: '该样品会记录为未通过，确认提交吗？', confirmText: '确认提交', success: result => { if (result.confirm) void performReject() } })
}

function confirmAccept() {
  uni.showModal({ title: '确认验收样品', content: '验收后可以继续解锁批量生产，确认样品符合要求吗？', confirmText: '确认验收', success: result => { if (result.confirm) void performAccept() } })
}

function confirmUnlock() {
  uni.showModal({ title: '解锁批量生产', content: '解锁后可提交批量生产申请，确认继续吗？', confirmText: '解锁', success: result => { if (result.confirm) void performUnlock() } })
}

async function performRevision() { await runAction('revision', () => requestSampleRevision(projectId.value, versionId.value, requestId.value, body())) }
async function performReject() { await runAction('reject', () => submitSampleFeedback(projectId.value, versionId.value, requestId.value, { ...body(), decision: 'reject' })) }
async function performAccept() { await runAction('accept', () => acceptSample(projectId.value, versionId.value, requestId.value, body())) }
async function performUnlock() { await runAction('unlock', () => unlockSampleBulkProduction(projectId.value, versionId.value, requestId.value, { comment: unlockComment.value, evidenceAssetIds: evidence.value.map(item => item.assetId) })) }

async function runAction(kind: typeof submitting.value, action: () => Promise<SampleLifecycle>) {
  if (!kind || submitting.value) return
  submitting.value = kind
  try {
    lifecycle.value = await action()
    await refreshDerivedData()
    comment.value = ''
    unlockComment.value = ''
    rating.value = 0
    issueTags.value = []
    evidence.value = []
    uni.showToast({ title: kind === 'unlock' ? '量产已解锁' : kind === 'accept' ? '样品已验收' : '反馈已提交', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '操作失败，请稍后重试', icon: 'none' })
    // A concurrent factory update can make the current buttons stale. Reload
    // the authoritative lifecycle after a failed mutation before retrying.
    if (projectId.value && versionId.value && requestId.value) void load()
  } finally { submitting.value = '' }
}

onLoad((query: any) => {
  if (!requireSession()) return
  projectId.value = String(query?.projectId || '')
  versionId.value = String(query?.versionId || '')
  requestId.value = String(query?.requestId || '')
  try { productTitle.value = decodeURIComponent(String(query?.title || '样品申请')) } catch { productTitle.value = String(query?.title || '样品申请') }
  void load()
})
onShow(() => { if (projectId.value && versionId.value && requestId.value && !loading.value) void load() })
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:38rpx 34rpx 80rpx;box-sizing:border-box;background:linear-gradient(180deg,#faf8f3,#f0e9df);color:#3b342d}.head{padding:14rpx 4rpx 28rpx}.eyebrow{display:block;color:#5f7d70;font-size:20rpx;letter-spacing:3rpx}.title{display:block;margin-top:14rpx;color:#302b26;font:800 48rpx/1.2 "Songti SC","STSong",serif}.sub{display:block;margin-top:12rpx;color:#82786d;font-size:23rpx;line-height:1.65}.empty{padding:100rpx 30rpx;text-align:center;color:#93877c;font-size:25rpx}.error-state{display:flex;flex-direction:column;align-items:center;gap:20rpx}.outline-button{height:70rpx;line-height:70rpx;margin:0;padding:0 28rpx;border:1rpx solid #698477;border-radius:13rpx;background:#fff;color:#5c796c;font-size:23rpx}.summary-card,.feedback-card,.timeline-card,.unlock-card,.next-action-card{margin-top:17rpx;padding:24rpx;border:1rpx solid rgba(129,112,93,.14);border-radius:20rpx;background:#fffdfa;box-shadow:0 9rpx 22rpx rgba(67,53,37,.055)}.summary-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx}.summary-kicker{display:block;color:#9b8d81;font-size:19rpx}.summary-title{display:block;margin-top:8rpx;color:#403931;font:800 28rpx "Songti SC","STSong",serif}.status{padding:7rpx 10rpx;border-radius:9rpx;background:#f4eee5;color:#997052;font-size:18rpx;white-space:nowrap}.status.accepted,.status.bulk_unlocked{background:#e6f1e7;color:#5f806b}.status.revision_required{background:#fff1d7;color:#9a7440}.status.rejected{background:#fce6e1;color:#a85448}.progress-track{height:12rpx;margin-top:24rpx;overflow:hidden;border-radius:8rpx;background:#eee6dc}.progress-value{height:100%;border-radius:inherit;background:linear-gradient(90deg,#a56e58,#6e8b7c);transition:width .25s}.progress-labels{display:flex;justify-content:space-between;margin-top:9rpx;color:#9b8e82;font-size:15rpx}.summary-meta{display:flex;flex-direction:column;gap:6rpx;margin-top:18rpx;color:#8c8176;font-size:17rpx}.next-action-card{border-color:#d7e4d8;background:#f7faf6}.next-action-card.blocked{border-color:#ead1c6;background:#fff7f3}.next-action-head{display:flex;justify-content:space-between;gap:10rpx;color:#688070;font-size:16rpx;font-weight:800}.next-action-head text:last-child{color:#9a7157}.next-action-title{display:block;margin-top:10rpx;color:#3f5147;font-size:24rpx;font-weight:800}.next-action-card.blocked .next-action-title{color:#9d5b4c}.next-action-tip,.blocker-list text{display:block;margin-top:8rpx;color:#80766e;font-size:16rpx;line-height:1.5}.blocker-list text{color:#9b5b4d}.section-title{display:flex;align-items:flex-end;justify-content:space-between;gap:10rpx}.section-title>text:first-child{color:#443c34;font:800 29rpx "Songti SC","STSong",serif}.section-title>text:last-child{color:#9b8e82;font-size:15rpx}.label{display:block;margin:18rpx 0 10rpx;color:#6c5c4f;font-size:17rpx;font-weight:800}.rating-row{display:flex;align-items:center;gap:6rpx}.star{color:#d6cfc6;font-size:42rpx;line-height:1}.star.active{color:#c28651}.rating-value{margin-left:8rpx;color:#82766c;font-size:17rpx}.clear-rating{margin-left:auto;color:#7d9587;font-size:16rpx}.tag-list{display:flex;flex-wrap:wrap;gap:8rpx}.tag{padding:9rpx 12rpx;border:1rpx solid #e1d6c9;border-radius:10rpx;background:#fff;color:#8a7c70;font-size:16rpx}.tag.active{border-color:#779886;background:#eaf3ea;color:#527363;font-weight:800}.textarea{box-sizing:border-box;width:100%;height:145rpx;margin-top:16rpx;padding:13rpx;border:1rpx solid #dfd4c8;border-radius:12rpx;background:#fff;color:#4a423a;font-size:18rpx;line-height:1.5}.textarea.compact{height:100rpx;margin-top:17rpx}.evidence-head{display:flex;align-items:center;justify-content:space-between}.evidence-head .label{margin-bottom:0}.evidence-head>text:last-child{color:#9b8e82;font-size:15rpx}.evidence-grid{display:flex;flex-wrap:wrap;gap:10rpx;margin-top:12rpx}.evidence-item,.evidence-add{width:150rpx;height:150rpx;overflow:hidden;border-radius:12rpx}.evidence-item{position:relative;background:#edf2ec}.evidence-item image{width:100%;height:100%;display:block}.evidence-item text{position:absolute;bottom:0;left:0;right:0;padding:5rpx;background:rgba(40,52,44,.64);color:#fff;text-align:center;font-size:14rpx}.evidence-add{display:flex;flex-direction:column;align-items:center;justify-content:center;border:1rpx dashed #abc0b0;background:#f3f7f2;color:#668273}.evidence-add>text:first-child{font-size:42rpx;line-height:1}.evidence-add>text:last-child{margin-top:8rpx;font-size:15rpx}.evidence-add.uploading{opacity:.6}.action-grid{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:18rpx}.action-grid button,.unlock-button{height:76rpx;line-height:76rpx;margin:0;border:0;border-radius:13rpx;font-size:21rpx}.revision-button{background:#eaf3ea;color:#527363}.reject-button{background:#f9e8e3;color:#9b594b}.accept-button{grid-column:1 / -1;background:#5e7c6f;color:#fff}.feedback-tip{display:block;margin-top:13rpx;color:#a08f82;font-size:15rpx;line-height:1.5}.unlock-card{border-color:#b9cfbc;background:#f5faf4}.unlock-title{display:block;color:#426d59;font:800 28rpx "Songti SC","STSong",serif}.unlock-copy{display:block;margin-top:9rpx;color:#718277;font-size:17rpx;line-height:1.55}.unlock-button{margin-top:16rpx;background:#557967;color:#fff}.timeline-empty{padding:34rpx 0 9rpx;color:#9b8e82;text-align:center;font-size:17rpx}.event-row{display:flex;gap:12rpx;padding:17rpx 0 0}.event-dot{width:18rpx;height:18rpx;margin-top:5rpx;flex-shrink:0;border:4rpx solid #a7bdad;border-radius:50%;background:#f5faf4}.event-dot.positive{border-color:#638c70;background:#e4f1e6}.event-dot.negative{border-color:#b86557;background:#f9e6e2}.event-body{min-width:0;flex:1;padding-bottom:15rpx;border-bottom:1rpx solid #eee4d9}.event-head{display:flex;justify-content:space-between;gap:10rpx;color:#5e7567;font-size:18rpx;font-weight:800}.event-head>text:last-child{color:#a29488;font-size:14rpx;font-weight:400;white-space:nowrap}.event-comment{display:block;margin-top:8rpx;color:#75695f;font-size:17rpx;line-height:1.55}.event-tags{display:flex;flex-wrap:wrap;gap:6rpx;margin-top:8rpx}.event-tags text{padding:5rpx 8rpx;border-radius:7rpx;background:#f4eee6;color:#977d68;font-size:14rpx}.event-evidence{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:10rpx}.event-evidence image{width:120rpx;height:120rpx;border-radius:10rpx;background:#edf2ec}.event-rating{display:block;margin-top:7rpx;color:#b27b4a;font-size:15rpx}
</style>

<style scoped lang="scss">
.logistics-card{margin-top:17rpx;padding:24rpx;border:1rpx solid #d5e4d9;border-radius:20rpx;background:#f8fcf8;box-shadow:0 9rpx 22rpx rgba(67,53,37,.045)}.logistics-card.exception{border-color:#e8b8ad;background:#fff8f5}.logistics-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx}.logistics-kicker{display:block;color:#718a7b;font-size:17rpx}.logistics-title{display:block;margin-top:7rpx;color:#3f6251;font:800 28rpx "Songti SC","STSong",serif}.logistics-card.exception .logistics-title{color:#a04e43}.logistics-alert{padding:7rpx 10rpx;border-radius:9rpx;background:#f7ddd8;color:#a04e43;font-size:17rpx;font-weight:800;white-space:nowrap}.logistics-alert.warning{background:#fff0d5;color:#9a713d}.logistics-number{display:flex;flex-wrap:wrap;gap:10rpx;margin-top:17rpx;color:#4d6e5d;font-size:19rpx;font-weight:800}.logistics-number text+text{color:#6e6258;font-weight:400}.logistics-empty{display:block;margin-top:17rpx;color:#97897c;font-size:17rpx;line-height:1.5}.exception-note{display:flex;gap:4rpx;margin-top:14rpx;padding:12rpx;border-radius:11rpx;background:#fbe5e0;color:#984d42;font-size:17rpx;line-height:1.5}.latest-trace{display:flex;gap:8rpx;margin-top:14rpx;padding:12rpx;border-radius:11rpx;background:rgba(255,255,255,.72);color:#776d63;font-size:17rpx;line-height:1.5}.latest-trace text:first-child{flex-shrink:0;color:#6c8175;font-weight:800}.logistics-times{display:flex;flex-direction:column;gap:5rpx;margin-top:13rpx;color:#93877c;font-size:15rpx}.logistics-traces{margin-top:17rpx;padding-top:13rpx;border-top:1rpx solid rgba(129,112,93,.14)}.trace-heading{display:block;color:#6c8175;font-size:17rpx;font-weight:800}.trace-row{display:flex;gap:10rpx;padding-top:13rpx}.trace-dot{width:13rpx;height:13rpx;margin-top:7rpx;flex-shrink:0;border:3rpx solid #9bb8a5;border-radius:50%;background:#f8fcf8}.trace-row.exception .trace-dot{border-color:#ba665a;background:#fff0ec}.trace-body{display:flex;flex:1;justify-content:space-between;gap:8rpx;color:#75695f;font-size:16rpx;line-height:1.5}.trace-body text:last-child{color:#a29488;font-size:14rpx;white-space:nowrap}
</style>

<style scoped lang="scss">
.head{position:relative}.sub{padding-right:150rpx}.refresh-button{position:absolute;top:12rpx;right:0;height:58rpx;margin:0;padding:0 17rpx;border:1rpx solid #c9d9cc;border-radius:11rpx;background:#fff;color:#5a7c6a;font-size:19rpx;line-height:58rpx}
</style>
