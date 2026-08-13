<template>
  <view class="page">
    <view class="head">
      <view>
        <text class="title">充值积分</text>
        <text class="sub">通过微信小程序虚拟支付购买积分，到账由微信官方余额核验。</text>
      </view>
      <button class="refresh" size="mini" :loading="loading" @tap="loadData(true)">刷新</button>
    </view>

    <view class="card"><text class="balance">当前积分 <text>{{ balance }}</text></text><text class="rules">文生图 16 积分 · 文生3D 60 积分 · 图生3D 70 积分</text></view>

    <view class="section">
      <text class="label">选择充值套餐</text>
      <view v-for="pkg in packages" :key="pkg.code" class="pkg" :class="{ selected: selected?.code === pkg.code }" @tap="selectPackage(pkg)">
        <view><text class="pkg-name">{{ pkg.name }}</text><text class="pkg-credit">{{ pkg.credits }} 积分</text></view>
        <text class="price">¥{{ packageAmount(pkg) }}</text>
      </view>
    </view>

    <button class="pay" :disabled="creatingOrder || requestingPayment || loading" :loading="creatingOrder || requestingPayment" @tap="startPayment">{{ paymentButtonText }}</button>
    <text v-if="!paymentEnabled" class="payment-unavailable">微信虚拟支付正在配置中，请稍后再试。</text>
    <text v-else class="payment-note">支付由微信小程序虚拟支付完成，积分到账以微信官方代币余额核验结果为准。</text>
    <view v-if="paymentError" class="payment-error">
      <text class="payment-error-title">本次未调起支付</text>
      <text class="payment-error-detail">{{ paymentError }}</text>
      <text v-if="paymentErrorCode" class="payment-error-code">微信错误码：{{ paymentErrorCode }}</text>
    </view>

    <view class="section history">
      <view class="history-head"><text class="label">充值订单</text><text>{{ orders.length }} 笔</text></view>
      <view v-if="!orders.length && !loading" class="no-order">还没有充值订单</view>
      <view v-for="item in orders" :key="item.orderNo" class="order-item">
        <view class="row"><text class="order-name">{{ item.packageName || '积分充值' }}</text><text class="status" :class="item.status">{{ paymentStatusText(item.status) }}</text></view>
        <text class="order-meta">{{ item.orderNo }} · {{ formatTime(item.createdAt) }}</text>
        <view class="order-bottom"><text>{{ item.credits }} 积分</text><text class="order-price">¥{{ orderAmount(item) }}</text></view>
        <button v-if="isPendingVirtualOrder(item)" class="order-action" size="mini" @tap="openPendingOrder(item)">处理此订单</button>
      </view>
    </view>

    <view v-if="paymentSheetVisible" class="modal">
      <view class="sheet">
        <text class="sheet-title">微信虚拟支付</text>
        <view class="payment-state" :class="paymentIntent">
          <text class="state-icon">{{ paymentStateIcon }}</text>
          <text class="state-title">{{ paymentStateTitle }}</text>
          <text class="state-hint">{{ paymentHint }}</text>
        </view>
        <template v-if="paymentOrder">
          <text class="order">订单号：{{ paymentOrder.orderNo }}</text>
          <text class="order">金额：¥{{ orderAmount(paymentOrder) }} · {{ paymentOrder.credits }} 积分</text>
          <text class="order">支付结果由微信官方确认，请勿重复发起。</text>
          <button v-if="paymentOrder.status === 'pending'" class="query" :loading="paymentPolling" @tap="refreshPaymentStatus(true)">查询到账结果</button>
          <button v-if="canRestartPendingPayment" class="query" :loading="paymentPolling || creatingOrder" @tap="restartPendingPayment">本次未支付，重新发起</button>
        </template>
        <text v-else class="order">正在准备微信支付，请勿重复点击。</text>
        <button v-if="canRetryPayment" class="query" :loading="creatingOrder || requestingPayment" @tap="startPayment">重新尝试</button>
        <text class="close" @tap="closePaymentSheet">关闭</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import {
  bindWechatMiniapp,
  cancelVirtualPaymentOrder,
  createPaymentOrder,
  getCredits,
  getPackages,
  getPaymentOrder,
  getPaymentOrders,
  type PaymentOrder,
  type WechatVirtualPaymentParams,
} from '../../api/creative'
import { moneyText, statusText } from '../../utils/format'
import { requireSession } from '../../utils/session'
import { ApiError } from '../../api/client'

type PaymentIntent = 'idle' | 'launching' | 'awaiting_confirmation' | 'cancelled' | 'failed' | 'paid' | 'exception'
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
const paymentSheetVisible = ref(false)
const virtualPaymentEnabled = ref(false)
const paymentIntent = ref<PaymentIntent>('idle')
const paymentHint = ref('')
const paymentError = ref('')
const paymentErrorCode = ref<number | null>(null)
const paymentPollAttempts = ref(0)
let paymentTimer: ReturnType<typeof setInterval> | null = null
let paymentPollInFlight = false
let paidNoticeShown = false

const paymentEnabled = computed(() => virtualPaymentEnabled.value)
const paymentButtonText = computed(() => {
  if (loading.value) return '正在加载充值套餐'
  if (!selected.value) return packages.value.length ? '选择充值套餐' : '暂无法加载套餐'
  return paymentEnabled.value ? `微信虚拟支付 ¥${packageAmount(selected.value)}` : '检查微信虚拟支付'
})
const canRestartPendingPayment = computed(() => paymentOrder.value?.status === 'pending' && ['idle', 'cancelled', 'failed'].includes(paymentIntent.value))
const canRetryPayment = computed(() => paymentIntent.value === 'failed' && !paymentOrder.value)
const paymentStateIcon = computed(() => ({ idle: '⌛', launching: '…', awaiting_confirmation: '⌛', cancelled: '×', failed: '!', paid: '✓', exception: '!' }[paymentIntent.value] || '⌛'))
const paymentStateTitle = computed(() => ({ idle: '等待支付结果', launching: '正在唤起微信虚拟支付', awaiting_confirmation: '正在确认积分到账', cancelled: '你已取消本次支付', failed: '未能完成微信虚拟支付', paid: '积分已到账', exception: '支付结果核验中' }[paymentIntent.value] || '等待支付结果'))

const rows = (payload: any) => Array.isArray(payload) ? payload : (Array.isArray(payload?.items) ? payload.items : [])
const packageAmount = (pkg: any) => moneyText(pkg?.amountYuan, pkg?.amountFen)
const orderAmount = (order: any) => moneyText(order?.amountYuan, order?.amountFen)
const paymentStatusText = (status?: string) => ({ pending: '待确认', paid: '已到账', failed: '支付失败', closed: '已关闭', expired: '已过期', payment_exception: '核验异常', refunded: '已退款' }[status || ''] || statusText(status))

function formatTime(value: any) {
  if (!value) return '刚刚创建'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  const two = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`
}

function stopPaymentPolling() {
  if (paymentTimer) clearInterval(paymentTimer)
  paymentTimer = null
  paymentPolling.value = false
}

function closePaymentSheet() {
  stopPaymentPolling()
  paymentSheetVisible.value = false
  paymentOrder.value = null
  paymentHint.value = ''
  paymentIntent.value = 'idle'
  paymentPollAttempts.value = 0
  paidNoticeShown = false
}

function selectPackage(pkg: any) {
  selected.value = pkg
  paymentError.value = ''
  paymentErrorCode.value = null
}

function isPendingVirtualOrder(order: PaymentOrder) {
  return order.channel === 'wechat_virtual_payment' && order.status === 'pending'
}

function pendingVirtualOrder() {
  return orders.value.find((order: PaymentOrder) => isPendingVirtualOrder(order)) as PaymentOrder | undefined
}

function openPaymentSheet(intent: PaymentIntent, hint: string, order: PaymentOrder | null = null) {
  stopPaymentPolling()
  paymentSheetVisible.value = true
  paymentOrder.value = order
  paymentIntent.value = intent
  paymentHint.value = hint
  paymentPollAttempts.value = 0
}

function recordPaymentError(error: any, fallback: string) {
  const code = Number(error?.errCode)
  paymentErrorCode.value = Number.isFinite(code) ? code : null
  paymentError.value = virtualPaymentFailureMessage(error) || fallback
}

async function loadData(notify = false) {
  loading.value = true
  const [packageResult, creditResult, orderResult] = await Promise.allSettled([getPackages(), getCredits(), getPaymentOrders()])
  let packageLoaded = false
  try {
    if (packageResult.status === 'fulfilled') {
      const packageData = packageResult.value
      const availablePackages = rows(packageData)
      packages.value = availablePackages
      if (!selected.value || !availablePackages.some((item: any) => item.code === selected.value.code)) {
        selected.value = availablePackages[0] || null
      }
      virtualPaymentEnabled.value = (packageData?.channels || []).some((channel: any) => channel.code === 'wechat_virtual_payment' && channel.enabled)
      packageLoaded = true
    } else {
      packages.value = []
      selected.value = null
      virtualPaymentEnabled.value = false
    }
    if (creditResult.status === 'fulfilled') balance.value = Number(creditResult.value?.balance) || 0
    if (orderResult.status === 'fulfilled') orders.value = rows(orderResult.value)
    const errors = [packageResult, creditResult, orderResult]
      .filter((result): result is PromiseRejectedResult => result.status === 'rejected')
      .map(result => result.reason?.message || '部分充值信息加载失败')
    if (errors.length) {
      const message = packageLoaded ? '部分信息加载失败，可继续充值' : (errors[0] || '加载充值信息失败')
      if (notify || !packages.value.length) uni.showToast({ title: message, icon: 'none' })
    } else if (notify) {
      uni.showToast({ title: '订单已刷新', icon: 'success' })
    }
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function requestVirtualPayment(params: WechatVirtualPaymentParams): Promise<void> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    try {
      if (typeof wx === 'undefined' || typeof wx.requestVirtualPayment !== 'function') {
        reject(new Error('当前微信版本不支持虚拟支付，请升级微信后重试'))
        return
      }
      wx.requestVirtualPayment({
        mode: params.mode,
        signData: params.signData,
        paySig: params.paySig,
        signature: params.signature,
        success: () => resolve(),
        fail: (error: any) => reject(error),
      })
    } catch (error) {
      reject(error)
    }
    // #endif
    // #ifndef MP-WEIXIN
    reject(new Error('请在微信小程序内完成微信虚拟支付'))
    // #endif
  })
}

function paymentWasCancelled(error: any) {
  const message = `${error?.errMsg || ''} ${error?.message || ''} ${error?.errCode || ''}`.toLowerCase()
  return message.includes('cancel') || message.includes('取消') || message.includes('-2')
}

function virtualPaymentFailureMessage(error: any) {
  const code = Number(error?.errCode)
  const providerMessages: Record<number, string> = {
    '-15001': '支付参数无效，请刷新后重试。',
    '-15002': '这笔订单已失效，请重新发起支付。',
    '-15003': '微信支付服务暂时繁忙，请稍后重试。',
    '-15004': '支付币种配置错误，平台正在处理。',
    '-15005': '微信支付身份签名失效，请退出小程序后重新进入再试。',
    '-15006': '支付签名校验失败，请联系平台客服处理。',
    '-15007': '微信支付会话已过期，请重新进入小程序后重试。',
    '-15009': '虚拟货币尚未在微信后台发布，暂时无法支付。',
    '-15011': '当前小程序版本只能使用现网虚拟支付配置。',
    '-15014': '虚拟货币刚发布，微信后台需要约 10 分钟生效。',
    '-15017': '微信支付已限制该商户收款，请在微信商户平台查看原因。',
    '-15018': '虚拟货币审核未通过，请在微信虚拟支付后台处理。',
    '-15020': '操作过快，请稍后再试。',
    '-15021': '该小程序交易过于频繁，请稍后再试。',
    '-4': '微信风控暂时拦截了本次支付，请稍后再试或更换网络。',
    '-5': '微信正在确认签约状态，请稍后再试。',
    '1001': '支付参数错误，平台正在检查虚拟支付配置。',
  }
  if (providerMessages[code]) return providerMessages[code]
  const detail = String(error?.errMsg || error?.message || '').trim()
  return detail || '未能调起微信虚拟支付，请稍后重试。'
}

function showPaymentFailure(message: string) {
  uni.showModal({
    title: '暂未调起微信支付',
    content: message,
    showCancel: false,
  })
}

function loginForWechatCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.login({ provider: 'weixin', success: result => result.code ? resolve(result.code) : reject(new Error('未获取到微信登录凭证，请稍后重试')), fail: () => reject(new Error('微信登录失败，请检查网络后重试')) })
    // #endif
    // #ifndef MP-WEIXIN
    reject(new Error('请在微信小程序内完成微信虚拟支付'))
    // #endif
  })
}

function requireVirtualParams(order: PaymentOrder): WechatVirtualPaymentParams {
  const params = order.virtualPayment
  if (!params?.signData || !params?.paySig || !params?.signature || params.mode !== 'short_series_coin') throw new Error('支付服务未返回完整虚拟支付凭证，请稍后重试')
  return params
}

async function launchVirtualPayment(order: PaymentOrder) {
  paymentSheetVisible.value = true
  paymentOrder.value = order
  paymentIntent.value = 'launching'
  paymentHint.value = '正在打开微信支付页面，请稍候。'
  requestingPayment.value = true
  try {
    await requestVirtualPayment(requireVirtualParams(order))
    paymentIntent.value = 'awaiting_confirmation'
    paymentHint.value = '微信已受理，正在核验积分到账，请勿重复支付。'
  } catch (error: any) {
    paymentIntent.value = paymentWasCancelled(error) ? 'cancelled' : 'failed'
    paymentHint.value = paymentIntent.value === 'cancelled' ? '本次支付已取消，未增加积分。' : virtualPaymentFailureMessage(error)
    if (paymentIntent.value === 'cancelled') await confirmCancellation()
    else {
      recordPaymentError(error, paymentHint.value)
      showPaymentFailure(paymentHint.value)
    }
  } finally {
    requestingPayment.value = false
    if (paymentOrder.value?.status === 'pending') startPaymentPolling()
  }
}

async function openPendingOrder(order: PaymentOrder) {
  openPaymentSheet('idle', '检测到一笔待确认订单。请先查询到账结果；若确认未支付，可在此重新发起。', order)
  await refreshPaymentStatus(false)
}

async function restartPendingPayment() {
  if (!paymentOrder.value?.orderNo) return
  paymentPolling.value = true
  try {
    const latest = await cancelVirtualPaymentOrder(paymentOrder.value.orderNo)
    paymentOrder.value = latest
    if (latest.status === 'paid') {
      paymentIntent.value = 'paid'
      paymentHint.value = '微信支付已完成，积分已到账。'
      await loadData(false)
      return
    }
    if (latest.status !== 'cancelled') {
      paymentIntent.value = 'exception'
      paymentHint.value = '支付结果需要人工核验，请不要重复付款。'
      return
    }
    await loadData(false)
    paymentOrder.value = null
    paymentIntent.value = 'idle'
    paymentHint.value = '上一笔订单已核验为未支付，正在重新发起。'
    await createAndLaunchPayment()
  } catch (error: any) {
    paymentIntent.value = 'failed'
    paymentHint.value = error.message || '订单核验失败，请稍后重试。'
    recordPaymentError(error, paymentHint.value)
    showPaymentFailure(paymentHint.value)
  } finally {
    paymentPolling.value = false
  }
}

async function createAndLaunchPayment() {
  if (!selected.value) throw new Error('请先选择充值套餐')
  creatingOrder.value = true
  try {
    const code = await loginForWechatCode()
    const binding = await bindWechatMiniapp(code)
    if (!binding?.openIdBound) throw new Error('微信身份绑定失败，请稍后重试')
    const order = await createPaymentOrder(selected.value.code, 'wechat_virtual_payment')
    await launchVirtualPayment(order)
  } finally {
    creatingOrder.value = false
  }
}

async function startPayment() {
  if (!requireSession()) return
  paymentError.value = ''
  paymentErrorCode.value = null
  if (!selected.value) {
    if (packages.value.length) selectPackage(packages.value[0])
    else {
      const message = '充值套餐尚未加载完成，请点击右上角刷新后再试。'
      openPaymentSheet('failed', message)
      paymentError.value = message
      showPaymentFailure(message)
      return
    }
  }
  if (!paymentEnabled.value) {
    const message = '微信虚拟支付尚未完成服务器配置，暂时不能发起付款。请联系平台管理员检查支付开关、OfferId、现网 AppKey 和会话加密密钥。'
    openPaymentSheet('failed', message)
    paymentError.value = message
    showPaymentFailure(message)
    return
  }
  const pending = pendingVirtualOrder()
  if (pending) {
    await openPendingOrder(pending)
    return
  }
  openPaymentSheet('launching', '正在校验微信身份并创建支付订单，请稍候。')
  try {
    await createAndLaunchPayment()
  } catch (error: any) {
    if (error instanceof ApiError && error.statusCode === 409) {
      await loadData(false)
      const pendingOrder = pendingVirtualOrder()
      if (pendingOrder) {
        await openPendingOrder(pendingOrder)
        return
      }
    }
    recordPaymentError(error, '发起微信虚拟支付失败')
    paymentIntent.value = 'failed'
    paymentHint.value = paymentError.value
    showPaymentFailure(paymentError.value)
  }
}

async function confirmCancellation() {
  if (!paymentOrder.value?.orderNo) return
  paymentPolling.value = true
  try {
    const latest = await cancelVirtualPaymentOrder(paymentOrder.value.orderNo)
    paymentOrder.value = latest
    if (latest.status === 'paid') {
      paymentIntent.value = 'paid'
      paymentHint.value = '微信支付已完成，积分已到账。'
      await loadData(false)
    } else if (latest.status === 'cancelled') {
      stopPaymentPolling()
      paymentHint.value = '本次支付已取消。你可以重新选择套餐发起支付。'
      await loadData(false)
    } else {
      paymentIntent.value = 'exception'
      paymentHint.value = '支付结果需要人工核验，请不要重复付款。'
    }
  } catch (error: any) {
    paymentHint.value = error.message || '取消结果核验失败，请稍后刷新订单。'
  } finally {
    paymentPolling.value = false
  }
}

function startPaymentPolling() {
  if (!paymentOrder.value?.orderNo || paymentOrder.value.channel !== 'wechat_virtual_payment') return
  stopPaymentPolling()
  paymentPollAttempts.value = 0
  paymentPolling.value = true
  void refreshPaymentStatus(false)
  paymentTimer = setInterval(() => { void refreshPaymentStatus(false) }, PAYMENT_POLL_INTERVAL)
}

async function refreshPaymentStatus(showLoading = false) {
  if (!paymentOrder.value?.orderNo || paymentPollInFlight) return
  const orderNo = paymentOrder.value.orderNo
  paymentPollInFlight = true
  if (showLoading) paymentPolling.value = true
  try {
    const latest = await getPaymentOrder(orderNo)
    if (paymentOrder.value?.orderNo !== orderNo) return
    paymentOrder.value = latest
    if (latest.status === 'paid') {
      stopPaymentPolling()
      paymentIntent.value = 'paid'
      paymentHint.value = '微信虚拟支付已由官方余额核验确认，积分已发放。'
      await loadData(false)
      if (!paidNoticeShown) {
        paidNoticeShown = true
        uni.showModal({ title: '充值成功', content: `${latest.credits || ''} 积分已到账。`, showCancel: false })
      }
      return
    }
    if (latest.status === 'payment_exception') {
      stopPaymentPolling()
      paymentIntent.value = 'exception'
      paymentHint.value = '支付结果正在人工核验，请不要重复付款。'
      return
    }
    paymentPollAttempts.value += 1
    if (paymentPollAttempts.value >= MAX_PAYMENT_POLL_ATTEMPTS) {
      stopPaymentPolling()
      if (paymentIntent.value === 'awaiting_confirmation') paymentHint.value = '暂未确认到账。若已扣款，请稍后刷新订单，勿重复支付。'
    }
  } catch (error: any) {
    if (showLoading) uni.showToast({ title: error.message || '查询支付结果失败', icon: 'none' })
  } finally {
    paymentPollInFlight = false
    if (showLoading && !paymentTimer) paymentPolling.value = false
  }
}

onShow(() => { if (requireSession()) void loadData(false) })
onPullDownRefresh(() => { if (requireSession()) void loadData(false); else uni.stopPullDownRefresh() })
onUnload(stopPaymentPolling)
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:34rpx;box-sizing:border-box;background:linear-gradient(180deg,#faf8f3,#f0e9df)}.head{display:flex;justify-content:space-between;gap:20rpx;padding:20rpx 4rpx 30rpx}.title{display:block;font-size:48rpx;font-weight:800;color:#302b26}.sub{display:block;font-size:23rpx;color:#82786d;margin-top:12rpx;line-height:1.6}.refresh{flex-shrink:0;margin:4rpx 0 0;background:#edf3ed;color:#607b6e;font-size:21rpx}.card{border:1rpx solid rgba(114,96,78,.12);background:linear-gradient(145deg,#eaf2eb,#d8e5dc);border-radius:25rpx;color:#385043;padding:34rpx;box-shadow:0 10rpx 23rpx rgba(63,82,69,.08)}.balance{font-size:24rpx;display:block}.balance text{font-size:62rpx;font-weight:800;margin-left:16rpx}.rules{font-size:21rpx;color:#668075;margin-top:20rpx;display:block}.section{margin-top:36rpx}.label{font-size:30rpx;font-weight:700}.pkg{display:flex;justify-content:space-between;align-items:center;margin-top:18rpx;background:#fff;border:1rpx solid rgba(129,112,93,.12);border-radius:18rpx;padding:26rpx;box-shadow:0 8rpx 18rpx rgba(67,53,37,.045)}.pkg.selected{border-color:#9caf9f;background:#eff5ef}.pkg-name,.pkg-credit{display:block}.pkg-name{font-size:29rpx;font-weight:700}.pkg-credit{font-size:22rpx;color:#8d8277;margin-top:8rpx}.price{font-size:38rpx;font-weight:800;color:#9f624b}.pay,.query{height:94rpx;line-height:94rpx;border-radius:17rpx;font-size:29rpx;margin-top:40rpx}.pay{background:linear-gradient(135deg,#3e3933,#617e71);box-shadow:0 12rpx 22rpx rgba(52,58,52,.16);color:#fff}.pay[disabled]{opacity:.4}.payment-unavailable,.payment-note{display:block;margin:18rpx 12rpx 0;color:#ad442c;font-size:22rpx;line-height:1.6;text-align:center}.payment-note{color:#8d7469}.payment-error{margin:18rpx 0 0;padding:22rpx 24rpx;border:1rpx solid #f0b7a7;border-radius:14rpx;background:#fff1ed;color:#963d28}.payment-error-title,.payment-error-detail,.payment-error-code{display:block}.payment-error-title{font-size:25rpx;font-weight:700}.payment-error-detail{margin-top:8rpx;font-size:22rpx;line-height:1.55}.payment-error-code{margin-top:8rpx;font-size:20rpx;color:#af624c}.history{padding-bottom:42rpx}.history-head,.row,.order-bottom{display:flex;align-items:center;justify-content:space-between}.history-head text:last-child,.order-meta{font-size:21rpx;color:#9b8175}.no-order{padding:50rpx;text-align:center;color:#a48c80;font-size:24rpx}.order-item{margin-top:16rpx;background:#fff;border:1rpx solid rgba(129,112,93,.12);border-radius:18rpx;padding:24rpx;box-shadow:0 8rpx 18rpx rgba(67,53,37,.045)}.row{gap:16rpx}.order-name{font-size:27rpx;font-weight:700;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.status{font-size:20rpx;border-radius:20rpx;padding:6rpx 12rpx;background:#f9e6d5;color:#a2492b;white-space:nowrap}.status.paid{background:#e4f5e9;color:#248653}.status.payment_exception{background:#ffe5e1;color:#ba3d2e}.order-meta{display:block;margin-top:10rpx}.order-bottom{margin-top:15rpx;color:#765b4e;font-size:22rpx}.order-price{font-size:29rpx;font-weight:800;color:#9f624b}.order-action{margin:18rpx 0 0;background:#eff5ef;color:#45695b;border:1rpx solid #9caf9f;font-size:21rpx}.modal{position:fixed;inset:0;background:rgba(0,0,0,.48);display:flex;align-items:flex-end;z-index:10}.sheet{width:100%;background:#fffdfa;border-radius:34rpx 34rpx 0 0;padding:44rpx;box-sizing:border-box;text-align:center}.sheet-title{font-size:34rpx;font-weight:700}.payment-state{margin:30rpx 0 24rpx;padding:34rpx 28rpx;border-radius:22rpx;background:#fff8f2}.payment-state.awaiting_confirmation,.payment-state.launching{background:#fff7de}.payment-state.paid{background:#e9f8ec}.payment-state.cancelled,.payment-state.failed{background:#fff0ec}.state-icon{display:block;font-size:54rpx;font-weight:800;color:#9e4325;line-height:1}.paid .state-icon{color:#248653}.state-title{display:block;margin-top:16rpx;font-size:29rpx;font-weight:700}.state-hint,.order{display:block;margin-top:12rpx;font-size:22rpx;line-height:1.65;color:#8f7062}.order{font-size:21rpx;margin-top:4rpx}.query{margin-top:25rpx;background:#fff;border:2rpx solid #698477;color:#5c796c}.close{display:block;padding:25rpx;color:#8e7469;font-size:26rpx}
</style>
