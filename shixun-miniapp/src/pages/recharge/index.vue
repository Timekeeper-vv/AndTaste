<template>
  <view class="page">
    <view class="head">
      <view>
        <text class="title">充值积分</text>
        <text class="sub">支付完成后提交核验，管理员确认后积分自动到账。</text>
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

    <button class="pay" :disabled="!selected || !manualPaymentEnabled" :loading="creatingOrder" @tap="order">{{ !manualPaymentEnabled ? '收款码暂不可用' : selected ? `生成 ${selected.name} 收款码` : '请选择套餐' }}</button>
    <text v-if="!manualPaymentEnabled" class="payment-unavailable">收款码尚未配置或暂时不可用，请联系平台管理员后重试。</text>

    <view class="section history">
      <view class="history-head"><text class="label">充值订单</text><text>{{ orders.length }} 笔</text></view>
      <view v-if="!orders.length && !loading" class="no-order">还没有充值订单</view>
      <view v-for="item in orders" :key="item.orderNo" class="order-item">
        <view class="row"><text class="order-name">{{ item.packageName || '积分充值' }}</text><text class="status" :class="item.status">{{ statusText(item.status) }}</text></view>
        <text class="order-meta">{{ item.orderNo }} · {{ formatTime(item.createdAt) }}</text>
        <view class="order-bottom"><text>{{ item.credits }} 积分</text><text class="order-price">¥{{ orderAmount(item) }}</text></view>
      </view>
    </view>

    <view v-if="paymentOrder" class="modal">
      <view class="sheet">
        <text class="sheet-title">请使用微信扫码付款</text>
        <image v-if="qrUrl" :src="qrUrl" class="qr" mode="aspectFit" />
        <text v-else class="qr-unavailable">当前收款码不可用，请稍后重试或联系平台管理员。</text>
        <text class="order">订单号：{{ paymentOrder.orderNo }}</text>
        <text class="order">金额：¥{{ orderAmount(paymentOrder) }} · {{ paymentOrder.credits }} 积分</text>
        <text v-if="paymentOrder.status === 'pending'" class="order">支付后请点击下方按钮提交人工核验。</text>
        <text v-else class="order">当前状态：{{ statusText(paymentOrder.status) }}</text>
        <button v-if="paymentOrder.status === 'pending'" class="done" :loading="completing" @tap="complete">我已完成支付</button>
        <text class="close" @tap="paymentOrder = null">关闭</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { createPaymentOrder, getCredits, getPackages, getPaymentOrders, manualComplete } from '../../api/creative'
import { imageUrl, moneyText, statusText } from '../../utils/format'
import { requireSession } from '../../utils/session'

const packages = ref<any[]>([])
const selected = ref<any>(null)
const balance = ref(0)
const orders = ref<any[]>([])
const loading = ref(false)
const creatingOrder = ref(false)
const completing = ref(false)
const paymentOrder = ref<any>(null)
const qrUrl = ref('')
const manualPaymentEnabled = ref(false)

const rows = (payload: any) => Array.isArray(payload) ? payload : (Array.isArray(payload?.items) ? payload.items : [])
const packageAmount = (pkg: any) => moneyText(pkg?.amountYuan, pkg?.amountFen)
const orderAmount = (order: any) => moneyText(order?.amountYuan, order?.amountFen)

function formatTime(value: any) {
  if (!value) return '刚刚创建'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  const two = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`
}

async function loadData(notify = false) {
  loading.value = true
  try {
    const [packageData, creditData, orderData] = await Promise.all([getPackages(), getCredits(), getPaymentOrders()])
    packages.value = rows(packageData)
    const channels = Array.isArray(packageData?.channels) ? packageData.channels : []
    manualPaymentEnabled.value = !!channels.find((channel: any) => channel.code === 'manual_wechat_qr' && channel.enabled)
    balance.value = Number(creditData?.balance) || 0
    orders.value = rows(orderData)
    if (notify) uni.showToast({ title: '订单已刷新', icon: 'success' })
  } catch (error: any) {
    manualPaymentEnabled.value = false
    uni.showToast({ title: error.message || '加载充值信息失败', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

async function order() {
  if (!selected.value) return
  creatingOrder.value = true
  try {
    paymentOrder.value = await createPaymentOrder(selected.value.code)
    qrUrl.value = imageUrl(paymentOrder.value.codeUrl || '/payment-collection-qr.jpg')
    await loadData(false)
  } catch (error: any) {
    paymentOrder.value = null
    uni.showToast({ title: error.message || '创建订单失败', icon: 'none' })
  } finally {
    creatingOrder.value = false
  }
}

async function complete() {
  if (!paymentOrder.value?.orderNo) return
  completing.value = true
  try {
    paymentOrder.value = await manualComplete(paymentOrder.value.orderNo)
    await loadData(false)
    uni.showModal({
      title: '已提交核验',
      content: '管理员确认收款后，积分将自动发放到你的账户。',
      showCancel: false,
      success: () => { paymentOrder.value = null },
    })
  } catch (error: any) {
    uni.showToast({ title: error.message || '提交失败', icon: 'none' })
  } finally {
    completing.value = false
  }
}

onShow(() => {
  if (requireSession()) void loadData(false)
})

onPullDownRefresh(() => {
  if (requireSession()) loadData(false)
  else uni.stopPullDownRefresh()
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:34rpx;box-sizing:border-box}.head{display:flex;justify-content:space-between;gap:20rpx;padding:20rpx 4rpx 30rpx}.title{display:block;font-size:48rpx;font-weight:800}.sub{display:block;font-size:23rpx;color:#8d7469;margin-top:12rpx;line-height:1.6}.refresh{flex-shrink:0;margin:4rpx 0 0;background:#f4e5db;color:#873e26;font-size:21rpx}.card{background:linear-gradient(135deg,#472015,#a94c2b);border-radius:25rpx;color:#fff;padding:34rpx}.balance{font-size:24rpx;display:block}.balance text{font-size:62rpx;font-weight:800;margin-left:16rpx}.rules{font-size:21rpx;color:#f4d7c4;margin-top:20rpx;display:block}.section{margin-top:36rpx}.label{font-size:30rpx;font-weight:700}.pkg{display:flex;justify-content:space-between;align-items:center;margin-top:18rpx;background:#fff;border:2rpx solid transparent;border-radius:20rpx;padding:26rpx}.pkg.selected{border-color:#a64a2b;background:#fff8f2}.pkg-name,.pkg-credit{display:block}.pkg-name{font-size:29rpx;font-weight:700}.pkg-credit{font-size:22rpx;color:#936f5f;margin-top:8rpx}.price{font-size:38rpx;font-weight:800;color:#9e4325}.pay,.done{height:94rpx;line-height:94rpx;background:#963c23;color:#fff;border-radius:48rpx;font-size:29rpx;margin-top:40rpx}.pay[disabled]{opacity:.4}.payment-unavailable{display:block;margin:18rpx 12rpx 0;color:#ad442c;font-size:22rpx;line-height:1.6;text-align:center}.history{padding-bottom:42rpx}.history-head{display:flex;align-items:center;justify-content:space-between}.history-head text:last-child{font-size:21rpx;color:#9a7d70}.no-order{padding:50rpx;text-align:center;color:#a48c80;font-size:24rpx}.order-item{margin-top:16rpx;background:#fff;border-radius:18rpx;padding:24rpx}.row{display:flex;align-items:center;gap:16rpx}.order-name{font-size:27rpx;font-weight:700;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.status{font-size:20rpx;border-radius:20rpx;padding:6rpx 12rpx;background:#f9e6d5;color:#a2492b;white-space:nowrap}.status.paid{background:#e4f5e9;color:#248653}.status.manual_review{background:#fff0d5;color:#aa681e}.status.closed,.status.failed,.status.expired{background:#ffe5e1;color:#ba3d2e}.order-meta{display:block;font-size:20rpx;color:#9b8175;margin-top:10rpx}.order-bottom{display:flex;justify-content:space-between;margin-top:15rpx;color:#765b4e;font-size:22rpx}.order-price{font-size:29rpx;font-weight:800;color:#9e4325}.modal{position:fixed;inset:0;background:rgba(0,0,0,.48);display:flex;align-items:flex-end;z-index:10}.sheet{width:100%;background:#fff;border-radius:34rpx 34rpx 0 0;padding:44rpx;box-sizing:border-box;text-align:center}.sheet-title{font-size:34rpx;font-weight:700}.qr{width:420rpx;height:420rpx;margin:24rpx auto}.qr-unavailable{display:block;margin:28rpx 0;padding:30rpx;background:#fff1e9;border-radius:18rpx;color:#ad442c;font-size:25rpx;line-height:1.6}.order{display:block;font-size:21rpx;color:#947a6d;line-height:1.7}.done{margin-top:25rpx}.close{display:block;padding:25rpx;color:#8e7469;font-size:26rpx}
</style>
