<template>
  <view class="page">
    <view class="head"><text class="eyebrow">PROFESSIONAL GUIDANCE</text><text class="title">专业指导费支付</text><text class="sub">支付完成后，专业人员会根据当前作品给出可执行的修改建议。完成指导后可直接上传本地修改图重新提交。</text></view>
    <view v-if="loading" class="empty">正在加载专业指导单…</view>
    <view v-else-if="!guidance" class="empty">暂无待支付的专业指导单</view>
    <view v-else class="guidance-card">
      <view class="guidance-head"><view><text class="product">{{ guidance.productName || '商品化申请' }}</text><text class="meta">{{ guidance.guidanceNo || `指导单 #${guidance.id}` }} · {{ statusText(guidance.status) }}</text></view><text class="fee">¥{{ fee(guidance.quotedFeeYuan) }}</text></view>
      <text v-if="guidance.operatorComment" class="note">运营说明：{{ guidance.operatorComment }}</text>
      <text class="note">{{ guidance.paymentStatus === 'manual_review' ? '待人工核验收款' : guidance.paymentStatus === 'paid' ? '已支付，专业指导正在进行' : `预计完成：${guidance.quotedLeadTime || '待运营确认'}` }}</text>
      <button v-if="guidance.status === 'quoted' && ['unpaid', 'pending'].includes(String(guidance.paymentStatus || 'unpaid'))" class="pay" :loading="paying" @tap="pay">微信支付专业指导费</button>
      <button v-else-if="guidance.paymentStatus === 'manual_review'" class="disabled" disabled>等待人工核验</button>
    </view>
    <view v-if="paymentOrder" class="modal"><view class="sheet"><text class="sheet-title">{{ intent === 'paid' ? '支付成功' : intent === 'exception' ? '支付结果核对中' : '正在确认支付' }}</text><text class="sheet-hint">{{ hint }}</text><text class="order">订单号：{{ paymentOrder.orderNo }} · ¥{{ fee(paymentOrder.amountYuan) }}</text><button v-if="intent === 'exception'" class="query" @tap="refresh">查询订单状态</button><text class="close" @tap="close">关闭</text></view></view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getCommercialRequests } from '../../api/commercial'
import { bindWechatMiniapp, createCommercialGuidancePaymentOrder, getPaymentOrder, type PaymentOrder } from '../../api/creative'
import { requireSession } from '../../utils/session'

const guidance = ref<any>(null)
const guidanceId = ref('')
const loading = ref(false)
const paying = ref(false)
const paymentOrder = ref<PaymentOrder | null>(null)
const intent = ref<'awaiting' | 'paid' | 'exception'>('awaiting')
const hint = ref('微信支付已受理，正在等待官方回调确认，请勿重复支付。')
let timer: ReturnType<typeof setInterval> | null = null

const fee = (value: any) => Number(value || 0).toFixed(2).replace(/\.00$/, '')
function stop() { if (timer) clearInterval(timer); timer = null }
function close() { stop(); paymentOrder.value = null }
function statusText(status?: string) { return ({ requested: '等待运营报价', quoted: '待支付', in_progress: '指导进行中', completed: '指导完成', closed: '已关闭' } as Record<string, string>)[String(status || '')] || '处理中' }
function loginCode(): Promise<string> { return new Promise((resolve, reject) => { uni.login({ provider: 'weixin', success: (result) => result.code ? resolve(result.code) : reject(new Error('微信登录凭证获取失败')), fail: () => reject(new Error('微信登录失败')) }) }) }

async function load() {
  loading.value = true
  try {
    const data = await getCommercialRequests({ force: true })
    guidance.value = (data.guidanceRequests || []).find((item: any) => String(item.id) === guidanceId.value) || null
  } catch (error: any) {
    uni.showToast({ title: error?.message || '加载专业指导单失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

async function pay() {
  if (!guidance.value?.id || paying.value) return
  paying.value = true
  try {
    await bindWechatMiniapp(await loginCode())
    const order = await createCommercialGuidancePaymentOrder(guidance.value.id, 'wechat_jsapi')
    paymentOrder.value = order
    // #ifdef MP-WEIXIN
    const params: any = order.paymentParams
    if (!params?.timeStamp || !params?.nonceStr || !params?.package || !params?.paySign) throw new Error('支付凭证不完整，请稍后重试')
    try {
      await new Promise<void>((resolve, reject) => uni.requestPayment({ provider: 'wxpay', timeStamp: String(params.timeStamp), nonceStr: params.nonceStr, package: params.package, signType: params.signType || 'RSA', paySign: params.paySign, success: () => resolve(), fail: reject }))
    } catch (error: any) {
      if (!String(error?.errMsg || '').toLowerCase().includes('cancel')) throw error
    }
    // #endif
    startPolling()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '发起支付失败', icon: 'none' })
    paymentOrder.value = null
  } finally {
    paying.value = false
  }
}

function startPolling() { stop(); timer = setInterval(() => void refresh(), 2500); void refresh() }
async function refresh() {
  if (!paymentOrder.value?.orderNo) return
  try {
    const latest = await getPaymentOrder(paymentOrder.value.orderNo)
    paymentOrder.value = latest
    if (latest.status === 'paid') {
      stop()
      intent.value = 'paid'
      hint.value = '专业指导费已到账，专业人员将开始处理你的作品。'
      await load()
    } else if (['payment_exception', 'refund_exception'].includes(String(latest.status))) {
      stop()
      intent.value = 'exception'
      hint.value = '支付结果正在与微信官方核对，请勿重复支付。'
    }
  } catch { /* Keep polling through transient network failures. */ }
}

onLoad((query: any) => { guidanceId.value = String(query?.guidanceId || '') })
onShow(() => { if (requireSession() && guidanceId.value) void load() })
onUnload(stop)
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:38rpx 34rpx 80rpx;box-sizing:border-box;background:linear-gradient(180deg,#faf8f3,#f0e9df)}.head{padding:14rpx 4rpx 30rpx}.eyebrow{display:block;color:#5f7d70;font-size:20rpx;letter-spacing:3rpx}.title{display:block;margin-top:14rpx;color:#302b26;font:800 48rpx/1.2 "Songti SC","STSong",serif}.sub{display:block;margin-top:12rpx;color:#82786d;font-size:23rpx;line-height:1.65}.guidance-card{margin-top:17rpx;padding:25rpx;border:1rpx solid rgba(129,112,93,.13);border-radius:20rpx;background:#fff;box-shadow:0 9rpx 22rpx rgba(67,53,37,.055)}.guidance-head{display:flex;justify-content:space-between;gap:18rpx}.product{display:block;color:#302b26;font-size:28rpx;font-weight:750}.meta,.note{display:block;margin-top:8rpx;color:#8d8277;font-size:21rpx;line-height:1.55}.fee{color:#9f624b;font-size:34rpx;font-weight:800}.pay,.disabled,.query{height:82rpx;line-height:82rpx;margin-top:21rpx;border:0;border-radius:16rpx;background:linear-gradient(135deg,#3e3933,#617e71);color:#fff;font-size:26rpx}.disabled{background:#d6d0c9;color:#81776f}.empty{padding:100rpx 30rpx;text-align:center;color:#93877c;font-size:25rpx}.modal{position:fixed;inset:0;display:flex;align-items:flex-end;background:rgba(0,0,0,.48);z-index:10}.sheet{width:100%;padding:45rpx 36rpx 35rpx;box-sizing:border-box;text-align:center;border-radius:32rpx 32rpx 0 0;background:#fffdfa}.sheet-title{display:block;color:#302b26;font-size:34rpx;font-weight:800}.sheet-hint,.order{display:block;margin-top:16rpx;color:#8b8075;font-size:23rpx;line-height:1.6}.query{background:#fff;border:2rpx solid #698477;color:#5c796c}.close{display:block;padding:27rpx;color:#8e7469;font-size:26rpx}
</style>
