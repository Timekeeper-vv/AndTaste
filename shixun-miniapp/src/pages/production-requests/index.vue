<template>
  <view class="page">
    <view class="head">
      <view>
        <text class="eyebrow">MY PRODUCTION</text>
        <text class="title">我的生产申请</text>
        <text class="sub">从审核、支付到制作和样品验收，所有申请集中在这里。</text>
      </view>
      <button class="refresh" size="mini" :loading="loading" :disabled="loading" @tap="load">刷新</button>
    </view>

    <view v-if="!signedIn" class="empty-card">
      <text class="empty-title">登录后查看申请</text>
      <text class="empty-copy">你的打样和生产记录会保存在当前账号中。</text>
      <button class="primary" @tap="goLogin">登录</button>
    </view>
    <view v-else-if="loading && !requests.length" class="empty-card"><text>正在加载申请…</text></view>
    <view v-else-if="loadError && !requests.length" class="empty-card">
      <text class="empty-title">申请暂时无法打开</text>
      <text class="empty-copy">{{ loadError }}</text>
      <button class="primary" @tap="load">重新加载</button>
    </view>
    <view v-else-if="!requests.length" class="empty-card">
      <text class="empty-title">还没有生产申请</text>
      <text class="empty-copy">完成作品审核后，可以从作品库发起打样或生产。</text>
      <button class="primary" @tap="goWorks">去我的作品</button>
    </view>
    <view v-else class="request-list">
      <view v-if="loadError" class="load-warning" @tap="load"><text>显示的是上次数据，最新状态未能同步</text><text>点击重试 ›</text></view>
      <view v-for="item in requests" :key="item.id" class="request-card">
        <view class="request-head">
          <view class="request-title-wrap"><text class="product-no">产品号：{{ item.productNo || '未关联产品号' }}</text><text class="request-title">{{ item.sampleProductName || item.title || '生产申请' }}</text><text class="request-no">{{ item.requestNo || `申请 #${item.id}` }}</text></view>
          <text class="request-type">{{ item.requestType === 'bulk' ? '批量生产' : '打样' }}</text>
        </view>
        <view class="status-row"><text class="status" :class="statusClass(item)">{{ statusLabel(item) }}</text><text v-if="item.requestType === 'sample' && paymentLabel(item)" class="payment">{{ paymentLabel(item) }}</text></view>
        <view class="next-card" :class="{ blocked: workflowFor(item)?.flow?.blocked }">
          <view class="next-head"><text>下一步</text><text>{{ workflowFor(item)?.flow?.phaseLabel || statusLabel(item) }}</text></view>
          <text class="next-action">{{ workflowFor(item)?.flow?.nextAction || fallbackNextAction(item) }}</text>
          <view v-if="workflowBlockers(item).length" class="blockers"><text v-for="blocker in workflowBlockers(item).slice(0, 2)" :key="blocker.code">{{ blocker.label }}：{{ blocker.reason }}</text></view>
        </view>
        <view class="meta-row"><text>数量 {{ item.quantity || 0 }}</text><text>提交 {{ formatDate(item.createdAt) }}</text></view>
        <text v-if="item.reviewComment" class="comment">审核说明：{{ item.reviewComment }}</text>
        <view class="actions">
          <button v-if="sampleRoute(item)" class="primary small" size="mini" @tap="openSample(item)">查看样品完整流程</button>
          <button v-if="item.requestType === 'sample' && needsPayment(item)" class="pay small" size="mini" @tap="goPayment(item)">去支付打样费</button>
          <button v-if="canSubmitBulk(item)" class="pay small" size="mini" @tap="goBulkProduction(item)">申请批量生产</button>
          <button class="outline small" size="mini" :loading="detailBusyId === item.id" :disabled="Boolean(detailBusyId)" @tap="toggleWorkflow(item)">{{ workflowFor(item) ? '收起流程记录' : '查看流程记录' }}</button>
          <button v-if="!sampleRoute(item)" class="outline small" size="mini" @tap="goWorks">查看作品</button>
        </view>
        <view v-if="workflowFor(item)" class="detail-panel">
          <view v-if="workflowFor(item)?.payment" class="detail-row"><text>支付订单</text><text>{{ workflowFor(item)?.payment?.orderNo || '-' }} · {{ workflowFor(item)?.payment?.status || '-' }}</text></view>
          <view v-if="workflowFor(item)?.logistics" class="detail-row"><text>样品物流</text><text>{{ logisticsLabel(workflowFor(item)?.logistics) }}{{ workflowFor(item)?.logistics?.trackingNo ? ` · ${workflowFor(item)?.logistics?.trackingNo}` : '' }}</text></view>
          <view v-if="workflowTimeline(item).length" class="timeline"><text class="timeline-title">流程记录</text><view v-for="event in workflowTimeline(item).slice(-5).reverse()" :key="event.id" class="timeline-row"><text class="timeline-dot" /><view><text>{{ eventLabel(event) }}</text><text>{{ formatDate(event.createdAt) }}</text></view></view></view>
          <text v-else class="detail-empty">暂时还没有更多流程记录。</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getCreativeWorkflowDetail, getProductionRequests, type CreativeWorkflowDetail } from '../../api/creative'
import { getSession, requireSession } from '../../utils/session'

const requests = ref<any[]>([])
const loading = ref(false)
const loadError = ref('')
const workflows = ref<Record<string, CreativeWorkflowDetail>>({})
const detailBusyId = ref<number | string>('')
const signedIn = computed(() => Boolean(getSession()?.token))

function formatDate(value?: string) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function statusLabel(item: any) {
  const status = String(item?.status || '')
  const sample = String(item?.sampleWorkflowStatus || '')
  if (status === 'review') return '待审核'
  if (status === 'rejected' || sample === 'rejected') return sample === 'rejected' ? '样品未通过' : '未通过'
  if (String(item?.requestType || '') === 'sample' && status === 'approved'
      && ['unpaid', 'pending', 'manual_review'].includes(String(item?.samplePaymentStatus || ''))) return '审核通过，待支付'
  if (String(item?.requestType || '') === 'sample' && sample) {
    const sampleLabels: Record<string, string> = {
      not_started: '等待打样', in_production: '工厂打样中', ready_to_ship: '样品已出样',
      shipped: '样品已寄出', received: '待提交样品反馈', revision_required: '等待返修',
      revision_in_progress: '返修处理中', revision_completed: '返修完成，待出样',
      accepted: '样品已验收', rejected: '样品未通过', bulk_unlocked: '已解锁量产',
    }
    if (sampleLabels[sample]) return sampleLabels[sample]
  }
  return ({ approved: '审核通过', processing: '制作中', shipped: '样品已寄出', completed: '已完成' } as Record<string, string>)[status] || status || '待处理'
}

function statusClass(item: any) {
  const status = String(item?.status || '')
  const sample = String(item?.sampleWorkflowStatus || '')
  if (['rejected', 'sample_rejected'].includes(sample) || status === 'rejected') return 'bad'
  if (['accepted', 'bulk_unlocked'].includes(sample) || ['approved', 'processing', 'shipped', 'completed'].includes(status)) return 'good'
  return 'wait'
}

function paymentLabel(item: any) {
  return ({ unpaid: '待支付', pending: '支付处理中', manual_review: '待核验', paid: '已支付', not_required: '免支付' } as Record<string, string>)[String(item?.samplePaymentStatus || '')] || ''
}

function needsPayment(item: any) { return ['unpaid', 'pending'].includes(String(item?.samplePaymentStatus || '')) && String(item?.status || '') === 'approved' }
function canSubmitBulk(item: any) {
  return String(item?.requestType || '') === 'sample'
    && String(item?.sampleWorkflowStatus || '') === 'bulk_unlocked'
    && Number(item?.assetId) > 0
}

function workflowFor(item: any) { return workflows.value[String(item?.id)] || null }
function workflowBlockers(item: any) { const blockers = workflowFor(item)?.flow?.blockers; return Array.isArray(blockers) ? blockers : [] }
function workflowTimeline(item: any) { const timeline = workflowFor(item)?.timeline; return Array.isArray(timeline) ? timeline : [] }
function fallbackCode(item: any) {
  const sample = String(item?.sampleWorkflowStatus || '')
  if (sample === 'bulk_unlocked') return 'bulk_unlocked'
  if (sample === 'accepted') return 'sample_accepted'
  if (['revision_required', 'revision_in_progress', 'revision_completed'].includes(sample)) return sample
  if (sample === 'received') return 'sample_feedback'
  if (sample === 'shipped') return 'sample_shipped'
  if (sample === 'in_production') return 'sampling'
  if (item?.status === 'review') return 'human_review'
  if (item?.requestType === 'sample' && item?.status === 'approved' && ['unpaid', 'pending', 'manual_review'].includes(String(item?.samplePaymentStatus || ''))) return 'payment_pending'
  return String(item?.status || 'approved')
}
function fallbackNextAction(item: any) { return ({ human_review: '等待平台审核完成', payment_pending: '完成打样费支付', sampling: '等待工厂更新打样状态', sample_shipped: '收到样品后提交反馈', sample_feedback: '选择返修或确认验收', revision_required: '等待工厂开始返修', revision_in_progress: '等待返修完成并重新出样', revision_completed: '等待新样品寄出', sample_accepted: '解锁批量生产', bulk_unlocked: '提交批量生产申请', rejected: '根据审核意见修改后重新提交' } as Record<string, string>)[fallbackCode(item)] || '等待流程更新' }
function logisticsLabel(item: any) { return ({ pending: '等待发货', shipped: '已发货', in_transit: '运输中', delivering: '派送中', signed: '已签收', exception: '物流异常', returned: '已退回' } as Record<string, string>)[String(item?.status || '')] || '物流处理中' }
function eventLabel(event: any) { return ({ production_request_submitted: '提交生产申请', sampling_requested: '进入打样流程', production_review_approved: '生产申请审核通过', production_review_rejected: '生产申请被驳回', sample_feedback_accept: '提交样品验收反馈', sample_revision_requested: '提交返修反馈', sample_accepted: '样品验收通过', bulk_unlocked: '解锁批量生产' } as Record<string, string>)[String(event?.eventType || '')] || '流程状态更新' }

function sampleRoute(item: any) {
  if (String(item?.requestType || '') !== 'sample' || !item?.id || !item?.projectId || !item?.versionId) return ''
  const productQuery = item?.productNo ? `&productNo=${encodeURIComponent(String(item.productNo))}` : ''
  return `/pages/sample-lifecycle/index?projectId=${encodeURIComponent(String(item.projectId))}&versionId=${encodeURIComponent(String(item.versionId))}&requestId=${encodeURIComponent(String(item.id))}&title=${encodeURIComponent(item.sampleProductName || item.title || '样品申请')}${productQuery}`
}

function openSample(item: any) { const url = sampleRoute(item); if (url) uni.navigateTo({ url }) }
function goPayment(item: any) { uni.navigateTo({ url: `/pages/sample-payment/index?requestId=${encodeURIComponent(String(item?.id || ''))}` }) }
function goBulkProduction(item: any) {
  const assetId = Number(item?.assetId)
  if (!Number.isFinite(assetId) || assetId <= 0) return uni.showToast({ title: '缺少可量产的作品编号，请刷新作品库', icon: 'none' })
  const projectQuery = item?.projectId ? `&projectId=${encodeURIComponent(String(item.projectId))}` : ''
  const versionQuery = item?.versionId ? `&versionId=${encodeURIComponent(String(item.versionId))}` : ''
  const title = item?.sampleProductName || item?.title || '已验收样品'
  const productQuery = item?.productNo ? `&productNo=${encodeURIComponent(String(item.productNo))}` : ''
  uni.navigateTo({ url: `/pages/production/index?assetId=${encodeURIComponent(String(assetId))}&requestType=bulk&title=${encodeURIComponent(title)}${projectQuery}${versionQuery}${productQuery}` })
}
function goWorks() { uni.navigateTo({ url: '/pages/works/index' }) }
function goLogin() { uni.navigateTo({ url: '/pages/login/index?from=production-requests' }) }

async function load() {
  if (!signedIn.value || loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const rows = await getProductionRequests()
    requests.value = Array.isArray(rows) ? rows : []
    workflows.value = {}
  } catch (error: any) {
    loadError.value = error?.message || '申请暂时无法加载'
    uni.showToast({ title: loadError.value, icon: 'none' })
  } finally { loading.value = false; uni.stopPullDownRefresh() }
}

async function toggleWorkflow(item: any) {
  const key = String(item?.id || '')
  if (!key) return
  if (detailBusyId.value) return
  if (workflows.value[key]) {
    const next = { ...workflows.value }
    delete next[key]
    workflows.value = next
    return
  }
  detailBusyId.value = item.id
  try {
    workflows.value = { ...workflows.value, [key]: await getCreativeWorkflowDetail(item.id) }
  } catch (error: any) {
    uni.showToast({ title: error?.message || '流程记录加载失败', icon: 'none' })
  } finally { detailBusyId.value = '' }
}

onShow(() => { if (requireSession()) void load() })
onPullDownRefresh(() => { void load() })
</script>

<style scoped lang="scss">
.page{min-height:100vh;box-sizing:border-box;padding:42rpx 32rpx 70rpx;background:linear-gradient(180deg,#faf8f3,#f0e9df);color:#3b342d}.head{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;padding:8rpx 4rpx 24rpx}.eyebrow{display:block;color:#718a7b;font-size:19rpx;letter-spacing:3rpx}.title{display:block;margin-top:12rpx;color:#302b26;font:800 44rpx/1.2 "Songti SC","STSong",serif}.sub{display:block;margin-top:11rpx;color:#82786d;font-size:21rpx;line-height:1.55}.refresh{flex:none;margin:5rpx 0 0;padding:0 22rpx;height:62rpx;line-height:62rpx;border:1rpx solid #c9d9cc;border-radius:12rpx;background:#fff;color:#5a7c6a;font-size:20rpx}.request-list{display:flex;flex-direction:column;gap:14rpx}.request-card,.empty-card{padding:24rpx;border:1rpx solid rgba(129,112,93,.14);border-radius:18rpx;background:#fffdfa;box-shadow:0 8rpx 20rpx rgba(67,53,37,.05)}.request-head{display:flex;justify-content:space-between;gap:12rpx}.request-title-wrap{min-width:0;display:flex;flex:1;flex-direction:column}.product-no{display:block;color:#365e4a;font-size:24rpx;font-weight:900}.request-title{overflow:hidden;color:#443c34;font:800 27rpx "Songti SC","STSong",serif;text-overflow:ellipsis;white-space:nowrap}.request-no{margin-top:7rpx;color:#a09286;font-size:16rpx}.request-type{flex:none;padding:7rpx 10rpx;border-radius:9rpx;background:#edf4ed;color:#5f806e;font-size:17rpx}.status-row{display:flex;align-items:center;gap:9rpx;margin-top:16rpx}.status{padding:6rpx 10rpx;border-radius:9rpx;background:#fff0d5;color:#9a713d;font-size:17rpx;font-weight:800}.status.good{background:#e5f1e7;color:#5f806b}.status.bad{background:#fbe5e0;color:#a04e43}.payment{color:#9c7957;font-size:16rpx}.meta-row{display:flex;justify-content:space-between;gap:10rpx;margin-top:13rpx;color:#978a7d;font-size:16rpx}.comment{display:block;margin-top:12rpx;padding:10rpx;border-radius:9rpx;background:#fff7ed;color:#856a56;font-size:16rpx;line-height:1.5}.actions{display:flex;flex-wrap:wrap;gap:9rpx;margin-top:17rpx}.actions button{margin:0}.small{height:60rpx;line-height:60rpx;padding:0 17rpx;border-radius:10rpx;font-size:18rpx}.primary{border:0;background:#5e7c6f;color:#fff}.pay{border:0;background:#b7794d;color:#fff}.outline{border:1rpx solid #cbd9cf;background:#fff;color:#5e7c6f}.empty-card{display:flex;align-items:center;flex-direction:column;padding:85rpx 28rpx;text-align:center}.empty-title{color:#443c34;font:800 29rpx "Songti SC","STSong",serif}.empty-copy{margin-top:10rpx;color:#96897d;font-size:18rpx;line-height:1.5}.empty-card .primary{margin-top:24rpx;padding:0 32rpx;height:68rpx;line-height:68rpx;border-radius:11rpx;font-size:21rpx}.next-card{margin-top:16rpx;padding:14rpx 15rpx;border:1rpx solid #d9e5dc;border-radius:15rpx;background:#f7fbf7}.next-card.blocked{border-color:#ead7c7;background:#fff9f1}.next-head{display:flex;justify-content:space-between;gap:8rpx;color:#718178;font-size:17rpx}.next-head text:last-child{color:#557465;font-weight:800}.next-action{display:block;margin-top:7rpx;color:#3f594b;font-size:23rpx;font-weight:800;line-height:1.45}.blockers{display:flex;flex-direction:column;gap:4rpx;margin-top:7rpx;padding-top:7rpx;border-top:1rpx solid rgba(151,119,91,.15)}.blockers text{color:#a05e48;font-size:17rpx;line-height:1.45}.detail-panel{margin-top:13rpx;padding:13rpx;border-top:1rpx solid #eee6dc}.detail-row{display:flex;justify-content:space-between;gap:12rpx;padding:7rpx 0;color:#8a8176;font-size:17rpx}.detail-row text:last-child{overflow:hidden;color:#5f7568;text-align:right;text-overflow:ellipsis;white-space:nowrap}.timeline{margin-top:7rpx}.timeline-title{display:block;color:#6b7d72;font-size:18rpx;font-weight:900}.timeline-row{display:flex;gap:9rpx;margin-top:10rpx}.timeline-dot{flex:none;width:11rpx;height:11rpx;margin-top:6rpx;border-radius:50%;background:#88a594}.timeline-row view{display:flex;min-width:0;flex-direction:column;gap:3rpx}.timeline-row view text:first-child{color:#5d695f;font-size:18rpx}.timeline-row view text:last-child{color:#a0988f;font-size:16rpx}.detail-empty{display:block;padding:10rpx 0;color:#a0988f;font-size:17rpx}
</style>

<style scoped lang="scss">
.load-warning{display:flex;align-items:center;justify-content:space-between;gap:10rpx;padding:14rpx 16rpx;border:1rpx solid #ead7c7;border-radius:13rpx;background:#fff9f1;color:#9a6a52;font-size:17rpx}.load-warning text:last-child{color:#6a8977;font-weight:800}
</style>
