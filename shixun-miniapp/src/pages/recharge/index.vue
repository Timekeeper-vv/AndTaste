<template>
  <view class="page">
    <view class="head">
      <view>
        <text class="title">充值积分</text>
        <text class="sub">官方微信支付，支付成功后由服务端自动确认到账。</text>
      </view>
      <button class="refresh" size="mini" :loading="loading" @tap="loadData(true)">刷新</button>
    </view>

    <view class="card"><text class="balance">当前积分 <text>{{ balance }}</text></text><text class="rules">文生图 16 积分 · 文生3D 60 积分 · 图生3D 70 积分</text></view>

    <view class="section">
      <text class="label">选择充值套餐</text>
      <view v-for="pkg in packages" :key="pkg.code" class="pkg" :class="{ selected: selected?.code === pkg.code }" @tap="selected = pkg">
        <view><text class="pkg-name">{{ pkg.name }}</text><text class="pkg-credit">{{ pkg.credits }} 积分</text></view>
        <text class="price">¥{{ packageAmount(pkg) }}</text>
      </view>
    </view>

    <button class="pay" :disabled="!selected || !paymentEnabled" :loading="creatingOrder || requestingPayment" @tap="startPayment">{{ paymentButtonText }}</button>
    <text v-if="!paymentEnabled" class="payment-unavailable">当前暂无可用支付方式，请稍后重试或联系平台管理员。</text>
    <text v-else class="payment-note">将在微信内完成安全支付，积分以微信官方回调结果为准。</text>

    <view class="section history">
      <view class="history-head"><text class="label">充值订单</text><text>{{ orders.length }} 笔</text></view>
      <view v-if="!orders.length && !loading" class="no-order">还没有充值订单</view>
      <view v-for="item in orders" :key="item.orderNo" class="order-item">
        <view class="row"><text class="order-name">{{ item.packageName || '积分充值' }}</text><text class="status" :class="item.status">{{ paymentStatusText(item.status) }}</text></view>
        <text class="order-meta">{{ item.orderNo }} · {{ formatTime(item.createdAt) }}</text>
        <view class="order-bottom"><text>{{ item.credits }} 积分</text><text class="order-price">¥{{ orderAmount(item) }}</text></view>
      </view>
    </view>

    <view v-if="paymentOrder" class="modal">
      <view class="sheet">
        <text class="sheet-title">微信支付</text>

        <view class="jsapi-state" :class="paymentIntent">
          <text class="state-icon">{{ paymentStateIcon }}</text>
          <text class="state-title">{{ paymentStateTitle }}</text>
          <text class="state-hint">{{ paymentHint }}</text>
        </view>

        <text class="order">订单号：{{ paymentOrder.orderNo }}</text>
        <text class="order">金额：¥{{ orderAmount(paymentOrder) }} · {{ paymentOrder.credits }} 积分</text>
        <text class="order">当前状态：{{ paymentStatusText(paymentOrder.status) }}。支付是否到账以微信官方回调为准。</text>

        <button v-if="paymentOrder.status === 'pending'" class="query" :loading="paymentPolling" @tap="refreshPaymentStatus(true)">查询支付结果</button>
        <text class="close" @tap="closePaymentOrder">关闭</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import {
  bindWechatMiniapp,
  createPaymentOrder,
  getCredits,
  getPackages,
  getPaymentOrder,
  getPaymentOrders,
  getWechatPaymentParams,
  type PaymentOrder,
  type WechatJsapiPaymentParams,
} from '../../api/creative'
import { moneyText, statusText } from '../../utils/format'
import { requireSession } from '../../utils/session'

type PaymentIntent = 'idle' | 'launching' | 'awaiting_callback' | 'cancelled' | 'failed' | 'paid' | 'closed' | 'exception'

const MAX_PAYMENT_POLL_ATTEMPTS = 45
const PAYMENT_POLL_INTERVAL = 2000

const packages = ref<any[]>([])
const selected = ref<any>(null)
const balance = ref(0)
const orders = ref<any[]>([])
const loading = ref(false)
const creatingOrder = ref(false)
const requestingPayment = ref(false)
const paymentPolling = ref(false)
const paymentOrder = ref<PaymentOrder | null>(null)
const wechatJsapiEnabled = ref(false)
const paymentIntent = ref<PaymentIntent>('idle')
const paymentHint = ref('')
const paymentPollAttempts = ref(0)
let paymentTimer: ReturnType<typeof setInterval> | null = null
let paymentPollInFlight = false
let paymentPollingGeneration = 0
let paidNoticeShown = false

const paymentEnabled = computed(() => wechatJsapiEnabled.value)
const isWechatJsapiOrder = computed(() => paymentOrder.value?.channel === 'wechat_jsapi')
const paymentButtonText = computed(() => {
  if (!paymentEnabled.value) return '支付暂不可用'
  if (!selected.value) return '请选择套餐'
  const pending = pendingJsapiOrder()
  if (pending) return `继续支付 ¥${orderAmount(pending)}`
  return `微信支付 ¥${packageAmount(selected.value)}`
})
const paymentStateIcon = computed(() => ({
  idle: '⌛',
  launching: '…',
  awaiting_callback: '⌛',
  cancelled: '×',
  failed: '!',
  paid: '✓',
  closed: '×',
  exception: '!',
}[paymentIntent.value] || '⌛'))
const paymentStateTitle = computed(() => ({
  idle: '等待支付结果',
  launching: '正在唤起微信支付',
  awaiting_callback: '正在确认支付结果',
  cancelled: '你已取消本次支付',
  failed: '未能完成微信支付',
  paid: '积分已到账',
  closed: '订单已关闭',
  exception: '支付结果核对中',
}[paymentIntent.value] || '等待支付结果'))

const rows = (payload: any) => Array.isArray(payload) ? payload : (Array.isArray(payload?.items) ? payload.items : [])
const packageAmount = (pkg: any) => moneyText(pkg?.amountYuan, pkg?.amountFen)
const orderAmount = (order: any) => moneyText(order?.amountYuan, order?.amountFen)
const pendingJsapiOrder = () => orders.value.find((item: PaymentOrder) => (
  item.channel === 'wechat_jsapi' && item.status === 'pending'
)) as PaymentOrder | undefined
const paymentStatusText = (status?: string) => ({
  pending: '待支付',
  manual_review: '待人工核验',
  paid: '已到账',
  failed: '支付失败',
  closed: '已关闭',
  expired: '已过期',
  cancelled: '已取消',
  payment_exception: '支付结果核对中',
  refund_requested: '退款申请中',
  refund_processing: '退款处理中',
  refund_unknown: '退款待核对',
  refund_exception: '退款异常',
  refund_failed: '退款未完成',
  refunded: '已退款',
}[status || ''] || statusText(status))

function formatTime(value: any) {
  if (!value) return '刚刚创建'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  const two = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`
}

function stopPaymentPolling() {
  paymentPollingGeneration += 1
  if (paymentTimer) clearInterval(paymentTimer)
  paymentTimer = null
  paymentPolling.value = false
}

function closePaymentOrder() {
  stopPaymentPolling()
  paymentOrder.value = null
  paymentHint.value = ''
  paymentIntent.value = 'idle'
  paymentPollAttempts.value = 0
  paidNoticeShown = false
}

async function loadData(notify = false) {
  loading.value = true
  try {
    const [packageData, creditData, orderData] = await Promise.all([getPackages(), getCredits(), getPaymentOrders()])
    packages.value = rows(packageData)
    const channels = Array.isArray(packageData?.channels) ? packageData.channels : []
    wechatJsapiEnabled.value = channels.some((channel: any) => channel.code === 'wechat_jsapi' && channel.enabled)
    balance.value = Number(creditData?.balance) || 0
    orders.value = rows(orderData)
    if (notify) uni.showToast({ title: '订单已刷新', icon: 'success' })
  } catch (error: any) {
    wechatJsapiEnabled.value = false
    uni.showToast({ title: error.message || '加载充值信息失败', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

/**
 * A network timeout after creating a payment can mean WeChat has already
 * received the order. Recover the server-side order instead of allowing the
 * user to immediately create and pay another one.
 */
async function recoverUncertainWechatOrder() {
  try {
    const orderData = await getPaymentOrders()
    orders.value = rows(orderData)
    const latest = orders.value.find((item: PaymentOrder) => (
      item.channel === 'wechat_jsapi'
      && ['pending', 'payment_exception'].includes(item.status || '')
    )) as PaymentOrder | undefined
    if (!latest) return false
    if (latest.status === 'payment_exception') {
      paymentOrder.value = latest
      paymentIntent.value = 'exception'
      paymentHint.value = '支付结果正在由服务器核对，请勿重复付款。'
      return true
    }
    const resumable = await getWechatPaymentParams(latest.orderNo)
    if (resumable.status !== 'pending') return false
    await launchWechatPayment(resumable, true)
    return true
  } catch {
    return false
  }
}

function confirmResumePendingPayment(order: PaymentOrder): Promise<boolean> {
  if (!selected.value || order.packageCode === selected.value.code) return Promise.resolve(true)
  return new Promise(resolve => {
    uni.showModal({
      title: '存在待支付订单',
      content: `当前有一笔 ¥${orderAmount(order)} 的待支付订单。为避免重复扣款，本次将继续支付该订单。`,
      confirmText: '继续支付',
      cancelText: '暂不支付',
      success: result => resolve(result.confirm),
      fail: () => resolve(false),
    })
  })
}

async function resumePendingWechatPayment() {
  const pending = pendingJsapiOrder()
  if (!pending) return false
  if (!(await confirmResumePendingPayment(pending))) return true

  creatingOrder.value = true
  try {
    const latest = await getWechatPaymentParams(pending.orderNo)
    if (latest.status !== 'pending') {
      await loadData(false)
      uni.showToast({ title: '待支付订单状态已更新，请重新选择套餐', icon: 'none' })
      return false
    }
    await launchWechatPayment(latest, true)
    return true
  } catch (error: any) {
    uni.showToast({ title: error.message || '无法恢复待支付订单，请刷新后重试', icon: 'none' })
    return true
  } finally {
    creatingOrder.value = false
  }
}

function loginForWechatCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.login({
      provider: 'weixin',
      success: (result) => result.code ? resolve(result.code) : reject(new Error('未获取到微信登录凭证，请稍后重试')),
      fail: () => reject(new Error('微信登录失败，请检查网络后重试')),
    })
    // #endif
    // #ifndef MP-WEIXIN
    reject(new Error('请在微信小程序内完成微信支付'))
    // #endif
  })
}

function requirePaymentParams(order: PaymentOrder): WechatJsapiPaymentParams {
  const params = order.paymentParams
  if (!params?.timeStamp || !params?.nonceStr || !params?.package || !params?.signType || !params?.paySign) {
    throw new Error('支付服务未返回完整支付凭证，请稍后重试')
  }
  return params
}

function requestWechatPayment(params: WechatJsapiPaymentParams): Promise<void> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: String(params.timeStamp),
      nonceStr: params.nonceStr,
      package: params.package,
      signType: params.signType as 'RSA' | 'MD5' | 'HMAC-SHA256',
      paySign: params.paySign,
      success: () => resolve(),
      fail: (error) => reject(error),
    })
    // #endif
    // #ifndef MP-WEIXIN
    reject(new Error('请在微信小程序内完成微信支付'))
    // #endif
  })
}

async function launchWechatPayment(order: PaymentOrder, resuming = false) {
  paymentOrder.value = order
  paymentIntent.value = 'launching'
  paymentHint.value = resuming ? '正在重新唤起支付...' : '请在微信支付页面完成付款。'
  requestingPayment.value = true
  try {
    await requestWechatPayment(requirePaymentParams(order))
    // The client callback is not an accounting signal. Only the signed server
    // callback can mark credits as paid.
    paymentIntent.value = 'awaiting_callback'
    paymentHint.value = '支付已受理，正在等待结果确认，请勿重复支付。'
  } catch (error: any) {
    paymentIntent.value = paymentWasCancelled(error) ? 'cancelled' : 'failed'
    paymentHint.value = paymentIntent.value === 'cancelled'
      ? '本次支付已取消。可再次点击支付按钮继续完成当前订单。'
      : '未能调起或完成支付。若未发生扣款，可再次点击支付按钮继续当前订单。'
  } finally {
    requestingPayment.value = false
    // A callback may race with either a completed or cancelled client action.
    startPaymentPolling()
  }
}

function paymentWasCancelled(error: any) {
  const message = `${error?.errMsg || ''} ${error?.message || ''}`.toLowerCase()
  return message.includes('cancel') || message.includes('取消')
}

function startPaymentPolling() {
  if (!paymentOrder.value?.orderNo || !isWechatJsapiOrder.value) return
  stopPaymentPolling()
  const generation = paymentPollingGeneration
  paymentPollAttempts.value = 0
  paymentPolling.value = true
  void refreshPaymentStatus(false, generation)
  paymentTimer = setInterval(() => { void refreshPaymentStatus(false, generation) }, PAYMENT_POLL_INTERVAL)
}

async function refreshPaymentStatus(showLoading = false, generation = paymentPollingGeneration) {
  if (!paymentOrder.value?.orderNo || !isWechatJsapiOrder.value || paymentPollInFlight) return
  const orderNo = paymentOrder.value.orderNo
  paymentPollInFlight = true
  if (showLoading) paymentPolling.value = true
  try {
    const latest = await getPaymentOrder(orderNo)
    // Closing the sheet or creating another order must not allow a late polling
    // response to reopen or overwrite the current order.
    if (generation !== paymentPollingGeneration || paymentOrder.value?.orderNo !== orderNo) return
    paymentOrder.value = latest
    const status = latest.status || 'pending'
    if (status === 'paid') {
      stopPaymentPolling()
      paymentIntent.value = 'paid'
      paymentHint.value = '微信支付已由官方回调确认，积分已发放到你的账户。'
      await loadData(false)
      if (!paidNoticeShown) {
        paidNoticeShown = true
        uni.showModal({ title: '充值成功', content: `${latest.credits || ''} 积分已到账。`, showCancel: false })
      }
      return
    }
    if (status === 'payment_exception') {
      stopPaymentPolling()
      paymentIntent.value = 'exception'
      paymentHint.value = '支付结果正在由服务器与微信核对。若微信已扣款，请勿重复支付；平台会以官方对账结果为准。'
      await loadData(false)
      return
    }
    if (['refund_requested', 'refund_processing', 'refund_unknown', 'refund_exception', 'refund_failed', 'refunded'].includes(status)) {
      stopPaymentPolling()
      paymentIntent.value = 'exception'
      paymentHint.value = status === 'refunded'
        ? '该笔订单已原路退款，积分已相应撤销。'
        : '该笔订单正在进行退款或人工核对，请勿重复付款。'
      await loadData(false)
      return
    }
    if (['closed', 'failed', 'expired', 'cancelled'].includes(status)) {
      stopPaymentPolling()
      paymentIntent.value = 'closed'
      paymentHint.value = status === 'expired' ? '订单已过期，请重新选择套餐发起支付。' : '订单未完成支付，未产生积分。'
      await loadData(false)
      return
    }
    paymentPollAttempts.value += 1
    if (paymentPollAttempts.value >= MAX_PAYMENT_POLL_ATTEMPTS) {
      stopPaymentPolling()
      if (paymentIntent.value === 'awaiting_callback') paymentHint.value = '暂未收到支付结果。若微信已扣款，请稍后刷新订单，不要重复支付。'
    }
  } catch (error: any) {
    if (showLoading) uni.showToast({ title: error.message || '查询支付结果失败', icon: 'none' })
  } finally {
    paymentPollInFlight = false
    if (showLoading && !paymentTimer) paymentPolling.value = false
  }
}

async function createWechatPaymentOrder() {
  if (!selected.value) return
  creatingOrder.value = true
  let newlyCreatedOrder: PaymentOrder | null = null
  try {
    // A login code is one-time-use. Bind it immediately before creating the order.
    const code = await loginForWechatCode()
    const binding = await bindWechatMiniapp(code)
    if (!binding?.openIdBound) throw new Error('微信身份绑定失败，请稍后重试')

    const order = await createPaymentOrder(selected.value.code, 'wechat_jsapi')
    newlyCreatedOrder = order
    await launchWechatPayment(order)
  } catch (error: any) {
    if (newlyCreatedOrder) {
      paymentOrder.value = newlyCreatedOrder
      paymentIntent.value = 'failed'
      paymentHint.value = '支付订单已创建，但未能获取有效支付凭证。请不要重复支付，可稍后查询订单。'
      await loadData(false)
    } else if (!(await recoverUncertainWechatOrder())) {
      paymentOrder.value = null
    }
    uni.showToast({ title: error.message || '发起微信支付失败', icon: 'none' })
  } finally {
    creatingOrder.value = false
  }
}

async function startPayment() {
  if (!selected.value || !paymentEnabled.value || !requireSession()) return
  if (await resumePendingWechatPayment()) return
  await createWechatPaymentOrder()
}

onShow(() => {
  if (!requireSession()) return
  void loadData(false)
  if (paymentOrder.value?.status === 'pending' && isWechatJsapiOrder.value) startPaymentPolling()
})

onPullDownRefresh(() => {
  if (requireSession()) void loadData(false)
  else uni.stopPullDownRefresh()
})

onUnload(stopPaymentPolling)
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:34rpx;box-sizing:border-box}.head{display:flex;justify-content:space-between;gap:20rpx;padding:20rpx 4rpx 30rpx}.title{display:block;font-size:48rpx;font-weight:800}.sub{display:block;font-size:23rpx;color:#8d7469;margin-top:12rpx;line-height:1.6}.refresh{flex-shrink:0;margin:4rpx 0 0;background:#f4e5db;color:#873e26;font-size:21rpx}.card{background:linear-gradient(135deg,#472015,#a94c2b);border-radius:25rpx;color:#fff;padding:34rpx}.balance{font-size:24rpx;display:block}.balance text{font-size:62rpx;font-weight:800;margin-left:16rpx}.rules{font-size:21rpx;color:#f4d7c4;margin-top:20rpx;display:block}.section{margin-top:36rpx}.label{font-size:30rpx;font-weight:700}.pkg{display:flex;justify-content:space-between;align-items:center;margin-top:18rpx;background:#fff;border:2rpx solid transparent;border-radius:20rpx;padding:26rpx}.pkg.selected{border-color:#a64a2b;background:#fff8f2}.pkg-name,.pkg-credit{display:block}.pkg-name{font-size:29rpx;font-weight:700}.pkg-credit{font-size:22rpx;color:#936f5f;margin-top:8rpx}.price{font-size:38rpx;font-weight:800;color:#9e4325}.pay,.done,.query,.fallback{height:94rpx;line-height:94rpx;background:#963c23;color:#fff;border-radius:48rpx;font-size:29rpx;margin-top:40rpx}.pay[disabled],.fallback[disabled]{opacity:.4}.payment-unavailable,.payment-note{display:block;margin:18rpx 12rpx 0;color:#ad442c;font-size:22rpx;line-height:1.6;text-align:center}.payment-note{color:#8d7469}.history{padding-bottom:42rpx}.history-head{display:flex;align-items:center;justify-content:space-between}.history-head text:last-child{font-size:21rpx;color:#9a7d70}.no-order{padding:50rpx;text-align:center;color:#a48c80;font-size:24rpx}.order-item{margin-top:16rpx;background:#fff;border-radius:18rpx;padding:24rpx}.row{display:flex;align-items:center;gap:16rpx}.order-name{font-size:27rpx;font-weight:700;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.status{font-size:20rpx;border-radius:20rpx;padding:6rpx 12rpx;background:#f9e6d5;color:#a2492b;white-space:nowrap}.status.paid{background:#e4f5e9;color:#248653}.status.manual_review{background:#fff0d5;color:#aa681e}.status.closed,.status.failed,.status.expired,.status.cancelled{background:#ffe5e1;color:#ba3d2e}.order-meta{display:block;font-size:20rpx;color:#9b8175;margin-top:10rpx}.order-bottom{display:flex;justify-content:space-between;margin-top:15rpx;color:#765b4e;font-size:22rpx}.order-price{font-size:29rpx;font-weight:800;color:#9e4325}.modal{position:fixed;inset:0;background:rgba(0,0,0,.48);display:flex;align-items:flex-end;z-index:10}.sheet{width:100%;background:#fff;border-radius:34rpx 34rpx 0 0;padding:44rpx;box-sizing:border-box;text-align:center}.sheet-title{font-size:34rpx;font-weight:700}.qr{width:420rpx;height:420rpx;margin:24rpx auto}.qr-unavailable{display:block;margin:28rpx 0;padding:30rpx;background:#fff1e9;border-radius:18rpx;color:#ad442c;font-size:25rpx;line-height:1.6}.jsapi-state{margin:30rpx 0 24rpx;padding:34rpx 28rpx;border-radius:22rpx;background:#fff8f2}.jsapi-state.awaiting_callback,.jsapi-state.launching{background:#fff7de}.jsapi-state.paid{background:#e9f8ec}.jsapi-state.cancelled,.jsapi-state.failed,.jsapi-state.closed{background:#fff0ec}.state-icon{display:block;font-size:54rpx;font-weight:800;color:#9e4325;line-height:1}.paid .state-icon{color:#248653}.state-title{display:block;margin-top:16rpx;font-size:29rpx;font-weight:700}.state-hint{display:block;margin-top:12rpx;font-size:22rpx;line-height:1.65;color:#8f7062}.order{display:block;font-size:21rpx;color:#947a6d;line-height:1.7}.done,.query,.fallback{margin-top:25rpx}.query{background:#fff;border:2rpx solid #963c23;color:#963c23}.fallback{margin-top:16rpx;background:#b36e2e}.close{display:block;padding:25rpx;color:#8e7469;font-size:26rpx}
</style>

<style scoped lang="scss">
.page{background:radial-gradient(ellipse at 8% 0%,rgba(151,177,163,.17),transparent 28%),linear-gradient(180deg,#faf8f3,#f0e9df)}.title{font-family:"Songti SC","STSong",serif;color:#302b26}.sub{color:#82786d}.refresh{background:#edf3ed;color:#607b6e}.card{border:1rpx solid rgba(114,96,78,.12);background:linear-gradient(145deg,#eaf2eb,#d8e5dc);color:#385043;box-shadow:0 10rpx 23rpx rgba(63,82,69,.08)}.rules{color:#668075}.pkg,.order-item{border:1rpx solid rgba(129,112,93,.12);box-shadow:0 8rpx 18rpx rgba(67,53,37,.045)}.pkg.selected{border-color:#9caf9f;background:#eff5ef}.pkg-credit,.order-meta{color:#8d8277}.price,.order-price{color:#9f624b}.pay,.done{border-radius:17rpx;background:linear-gradient(135deg,#3e3933,#617e71);box-shadow:0 12rpx 22rpx rgba(52,58,52,.16)}.query{border-color:#698477;color:#5c796c}.fallback{background:#b68163}.status{background:#f5ece4;color:#9d5c48}.status.paid{background:#e7f1e8;color:#567a67}.status.manual_review{background:#f6f0df;color:#9b7540}.sheet{background:#fffdfa}.sheet-title{font-family:"Songti SC","STSong",serif}.close,.state-hint,.order{color:#8b8075}
</style>
