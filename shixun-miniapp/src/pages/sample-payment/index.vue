<template>
  <view class="page">
    <view class="head"><text class="eyebrow">SAMPLE PAYMENT</text><text class="title">打样费支付</text><text class="sub">只有平台审核通过的打样申请才能支付，支付成功后自动进入生产安排。</text></view>
    <view v-if="loading" class="empty">正在加载申请…</view>
    <view v-else-if="!requests.length" class="empty">暂无待支付的打样申请</view>
    <view v-for="item in requests" :key="item.id" class="request-card">
      <view class="request-head"><view><text class="product">{{ item.sampleProductName || item.title || '未命名作品' }}</text><text class="meta">申请 #{{ item.id }} · {{ item.status === 'approved' ? '审核通过' : item.status }}</text></view><text class="fee">¥{{ fee(item.sampleFeeYuan) }}</text></view>
      <text class="note">{{ item.samplePaymentStatus === 'paid' ? '已支付，申请已进入生产' : item.samplePaymentStatus === 'manual_review' ? '待管理员核验收款' : '请完成打样费用支付' }}</text>
      <button v-if="item.status === 'approved' && item.samplePaymentStatus !== 'paid' && item.samplePaymentStatus !== 'manual_review'" class="pay" :loading="payingId === item.id" @tap="pay(item)">微信支付打样费</button>
      <button v-else-if="item.samplePaymentStatus === 'manual_review'" class="disabled" disabled>等待人工核验</button>
    </view>
    <view v-if="paymentOrder" class="modal"><view class="sheet"><text class="sheet-title">{{ intent === 'paid' ? '支付成功' : intent === 'exception' ? '支付结果核对中' : '正在确认支付' }}</text><text class="sheet-hint">{{ hint }}</text><text class="order">订单号：{{ paymentOrder.orderNo }} · ¥{{ fee(paymentOrder.amountYuan) }}</text><button v-if="intent === 'exception'" class="query" @tap="refresh">查询订单状态</button><text class="close" @tap="close">关闭</text></view></view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getCommercialRequests } from '../../api/commercial'
import { bindWechatMiniapp, createCommercialQuoteSamplePaymentOrder, createSamplePaymentOrder, getPaymentOrder, getProductionRequests, type PaymentOrder } from '../../api/creative'
import { requireSession } from '../../utils/session'

const requests = ref<any[]>([])
const loading = ref(false)
const payingId = ref<number | string>('')
const paymentOrder = ref<PaymentOrder | null>(null)
const intent = ref<'awaiting' | 'paid' | 'exception'>('awaiting')
const hint = ref('微信支付已受理，正在等待官方回调确认，请勿重复支付。')
const quoteId = ref('')
let timer: ReturnType<typeof setInterval> | null = null
const fee = (value: any) => Number(value || 0).toFixed(2).replace(/\.00$/, '')

function stop() { if (timer) clearInterval(timer); timer = null }
function close() { stop(); paymentOrder.value = null }
function loginCode(): Promise<string> { return new Promise((resolve, reject) => { uni.login({ provider: 'weixin', success: (r) => r.code ? resolve(r.code) : reject(new Error('微信登录凭证获取失败')), fail: () => reject(new Error('微信登录失败')) }) }) }
async function load() { loading.value = true; try { if (quoteId.value) { const data = await getCommercialRequests(); const item = (data?.quoteRequests || []).find((x: any) => String(x.id) === quoteId.value); const paymentStatus = String(item?.samplePaymentStatus || 'unpaid'); requests.value = item && item.status === 'accepted' && ['unpaid', 'pending', 'manual_review'].includes(paymentStatus) ? [{ ...item, requestType: 'sample', status: 'approved', sampleProductName: item.productName, sampleFeeYuan: item.quotedTotalPrice, quotePayment: true }] : [] } else { const rows = await getProductionRequests(); requests.value = (Array.isArray(rows) ? rows : []).filter((x: any) => x.requestType === 'sample' && x.status === 'approved' && ['unpaid', 'pending'].includes(String(x.samplePaymentStatus || 'unpaid'))) } } catch (e: any) { uni.showToast({ title: e.message || '加载失败', icon: 'none' }) } finally { loading.value = false } }
async function pay(item: any) {
  if (payingId.value) return
  payingId.value = item.id
  try {
    await bindWechatMiniapp(await loginCode())
    const order = item.quotePayment ? await createCommercialQuoteSamplePaymentOrder(item.id, 'wechat_jsapi') : await createSamplePaymentOrder(item.id, 'wechat_jsapi')
    paymentOrder.value = order
    // #ifdef MP-WEIXIN
    const p: any = order.paymentParams
    if (!p?.timeStamp || !p?.nonceStr || !p?.package || !p?.paySign) throw new Error('支付凭证不完整，请稍后重试')
    try { await new Promise<void>((resolve, reject) => uni.requestPayment({ provider: 'wxpay', timeStamp: String(p.timeStamp), nonceStr: p.nonceStr, package: p.package, signType: p.signType || 'RSA', paySign: p.paySign, success: () => resolve(), fail: reject })) } catch (error: any) { if (!String(error?.errMsg || '').toLowerCase().includes('cancel')) throw error }
    // #endif
    startPolling()
  } catch (e: any) { uni.showToast({ title: e.message || '发起支付失败', icon: 'none' }); paymentOrder.value = null } finally { payingId.value = '' }
}
function startPolling() { stop(); timer = setInterval(() => void refresh(), 2500); void refresh() }
async function refresh() { if (!paymentOrder.value?.orderNo) return; try { const latest = await getPaymentOrder(paymentOrder.value.orderNo); paymentOrder.value = latest; if (latest.status === 'paid') { stop(); intent.value = 'paid'; hint.value = '打样费已到账，申请已进入生产流程。'; await load() } else if (['payment_exception', 'refund_exception'].includes(String(latest.status))) { stop(); intent.value = 'exception'; hint.value = '支付结果正在与微信官方核对，请勿重复支付。' } } catch {} }
onShow(() => { if (requireSession()) void load() })
onLoad((query: any) => { quoteId.value = String(query?.quoteId || '') })
onUnload(stop)
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:38rpx 34rpx 80rpx;box-sizing:border-box;background:linear-gradient(180deg,#faf8f3,#f0e9df)}.head{padding:14rpx 4rpx 30rpx}.eyebrow{display:block;color:#5f7d70;font-size:20rpx;letter-spacing:3rpx}.title{display:block;margin-top:14rpx;color:#302b26;font:800 48rpx/1.2 "Songti SC","STSong",serif}.sub{display:block;margin-top:12rpx;color:#82786d;font-size:23rpx;line-height:1.65}.request-card{margin-top:17rpx;padding:25rpx;border:1rpx solid rgba(129,112,93,.13);border-radius:20rpx;background:#fff;box-shadow:0 9rpx 22rpx rgba(67,53,37,.055)}.request-head{display:flex;justify-content:space-between;gap:18rpx}.product{display:block;color:#302b26;font-size:28rpx;font-weight:750}.meta,.note{display:block;margin-top:8rpx;color:#8d8277;font-size:21rpx}.fee{color:#9f624b;font-size:34rpx;font-weight:800}.pay,.disabled,.query{height:82rpx;line-height:82rpx;margin-top:21rpx;border:0;border-radius:16rpx;background:linear-gradient(135deg,#3e3933,#617e71);color:#fff;font-size:26rpx}.disabled{background:#d6d0c9;color:#81776f}.empty{padding:100rpx 30rpx;text-align:center;color:#93877c;font-size:25rpx}.modal{position:fixed;inset:0;display:flex;align-items:flex-end;background:rgba(0,0,0,.48);z-index:10}.sheet{width:100%;padding:45rpx 36rpx 35rpx;box-sizing:border-box;text-align:center;border-radius:32rpx 32rpx 0 0;background:#fffdfa}.sheet-title{display:block;color:#302b26;font-size:34rpx;font-weight:800}.sheet-hint,.order{display:block;margin-top:16rpx;color:#8b8075;font-size:23rpx;line-height:1.6}.query{background:#fff;border:2rpx solid #698477;color:#5c796c}.close{display:block;padding:27rpx;color:#8e7469;font-size:26rpx}
</style>
