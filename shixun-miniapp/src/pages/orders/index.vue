<template>
  <view class="page">
    <view class="head">
      <view><text class="eyebrow">MY MARKET ORDERS</text><text class="title">我的商城订单</text><text class="sub">仅展示当前登录账号创建的订单。订单归属、金额和商品信息均由服务端返回。</text></view>
      <button size="mini" :loading="loading" @tap="loadOrders">刷新</button>
    </view>

    <view class="notice"><text>支付即将接入</text><text>当前创建的订单会保持“待支付”；未发生真实支付前，不会展示为已付款、已发货或已完成。</text></view>

    <view v-if="loading && !orders.length" class="loading"><text>印</text><text>正在查找你的订单…</text></view>
    <view v-else-if="errorMessage" class="error-card"><text>{{ errorMessage }}</text><button size="mini" @tap="loadOrders">重新加载</button></view>
    <view v-else-if="!orders.length" class="empty-card"><text>还没有商城订单</text><text>去看看已经上架的文化创意作品吧。</text><button @tap="goMarket">逛商城</button></view>

    <view v-else class="order-list">
      <view v-for="order in orders" :key="order.orderNo" class="order-card">
        <view class="order-head"><view><text>{{ order.orderNo }}</text><text>{{ formatTime(order.createdAt) }}</text></view><view class="status" :class="statusClass(order.orderStatus)">{{ orderStatusText(order.orderStatus) }}</view></view>
        <view v-for="item in order.items || []" :key="item.id || `${order.orderNo}-${item.skuId}`" class="order-item">
          <image v-if="item.coverUrl" :src="imageUrl(item.coverUrl)" class="cover" mode="aspectFill" />
          <view v-else class="cover fallback">{{ (item.productName || '物').slice(0, 1) }}</view>
          <view class="item-info"><text>{{ item.productName || '文化创意商品' }}</text><text>{{ item.artworkTitle || '文化创意作品' }}</text><text>¥{{ money(item.unitPrice) }} × {{ item.quantity || 0 }}</text></view>
          <text class="subtotal">¥{{ money(item.subtotal) }}</text>
        </view>
        <view class="order-total"><text>共 {{ totalQuantity(order) }} 件商品</text><view><text>待支付金额</text><text>¥{{ money(order.payAmount ?? order.totalAmount) }}</text></view></view>
        <view v-if="isPendingPayment(order.orderStatus)" class="pending-note"><text>当前仅完成订单创建</text><text>支付、收货地址、履约和售后流程正在接入，订单暂不会进入发货状态。</text></view>
        <view v-else class="server-note"><text>订单状态以服务端为准</text><text>{{ order.paymentMethod ? `结算方式：${paymentMethodText(order.paymentMethod)}` : '暂无结算方式信息' }}</text></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { getMarketplaceOrders, type MarketplaceOrder } from '../../api/marketplace'
import { imageUrl, moneyText } from '../../utils/format'
import { requireSession } from '../../utils/session'

const orders = ref<MarketplaceOrder[]>([])
const loading = ref(false)
const errorMessage = ref('')
const money = (value: unknown) => moneyText(value)

const orderStatusText = (status?: string | null) => ({
  pending_pay: '待支付（接入中）',
  paid: '已付款',
  producing: '生产中',
  shipped: '已发货',
  completed: '已完成',
  cancelled: '已取消',
  closed: '已关闭',
}[status || ''] || '状态待确认')

const statusClass = (status?: string | null) => ({
  pending: status === 'pending_pay',
  complete: ['paid', 'producing', 'shipped', 'completed'].includes(status || ''),
  closed: ['cancelled', 'closed'].includes(status || ''),
})

const isPendingPayment = (status?: string | null) => status === 'pending_pay'
const paymentMethodText = (method?: string | null) => ({ wechat: '微信支付（待接入）', manual_wechat_qr: '微信人工收款码（待接入）' }[method || ''] || method || '待确认')

function totalQuantity(order: MarketplaceOrder) {
  return (order.items || []).reduce((sum, item) => sum + Math.max(0, Number(item.quantity || 0)), 0)
}

function formatTime(value?: string | null) {
  if (!value) return '刚刚创建'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  const two = (part: number) => String(part).padStart(2, '0')
  return `${date.getFullYear()}-${two(date.getMonth() + 1)}-${two(date.getDate())} ${two(date.getHours())}:${two(date.getMinutes())}`
}

async function loadOrders() {
  if (!requireSession()) return
  loading.value = true
  errorMessage.value = ''
  try {
    const rows = await getMarketplaceOrders()
    orders.value = Array.isArray(rows) ? rows : []
  } catch (error: any) {
    errorMessage.value = error?.message || '订单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function goMarket() {
  uni.navigateTo({ url: '/pages/market/index' })
}

onShow(() => {
  if (requireSession()) void loadOrders()
})

onPullDownRefresh(async () => {
  await loadOrders()
  uni.stopPullDownRefresh()
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;box-sizing:border-box;padding:38rpx 29rpx calc(70rpx + env(safe-area-inset-bottom));background:radial-gradient(ellipse at 95% 0%,rgba(182,108,80,.1),transparent 28%),linear-gradient(180deg,#faf8f3,#f0e9df);color:#352f28}.head{display:flex;align-items:flex-start;justify-content:space-between;gap:15rpx;padding:5rpx 6rpx 25rpx}.head>view{display:flex;flex:1;flex-direction:column}.eyebrow{color:#698478;font-size:17rpx;font-weight:900;letter-spacing:2.3rpx}.title{margin-top:9rpx;color:#302b25;font-family:"Songti SC","STSong",serif;font-size:44rpx;font-weight:800}.sub{margin-top:10rpx;color:#887c70;font-size:20rpx;line-height:1.55}.head button{flex:none;margin-top:5rpx;border:1rpx solid #cfe0d2;border-radius:99rpx;background:#eef5ef;color:#58786a;font-size:19rpx}.notice{display:flex;flex-direction:column;gap:6rpx;padding:19rpx;border:1rpx solid #ead6c9;border-radius:22rpx;background:rgba(251,241,235,.85)}.notice text:first-child{color:#a55e45;font-size:22rpx;font-weight:900}.notice text:last-child{color:#927767;font-size:19rpx;line-height:1.65}.loading,.error-card,.empty-card{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:12rpx;min-height:215rpx;margin-top:23rpx;padding:24rpx;border:1rpx dashed #d7c8b7;border-radius:23rpx;background:rgba(255,253,249,.76);color:#938579;font-size:22rpx;text-align:center}.loading text:first-child{display:grid;place-items:center;width:49rpx;height:49rpx;border:2rpx solid #a45d43;border-radius:7rpx;color:#a45d43;font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:800;animation:stamp 1.3s ease-in-out infinite}.error-card button{margin:4rpx 0 0;background:#607f71;color:#fff;font-size:20rpx}.empty-card text:first-child{color:#514a42;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.empty-card text:last-child{color:#978a7e;font-size:20rpx}.empty-card button{height:73rpx;line-height:73rpx;margin:9rpx 0 0;padding:0 30rpx;border-radius:15rpx;background:#607f71;color:#fff;font-size:22rpx}.order-list{display:flex;flex-direction:column;gap:19rpx;margin-top:24rpx}.order-card{overflow:hidden;border:1rpx solid rgba(127,109,88,.14);border-radius:24rpx;background:rgba(255,253,249,.9);box-shadow:0 11rpx 27rpx rgba(67,53,37,.06)}.order-head{display:flex;align-items:center;justify-content:space-between;gap:10rpx;padding:19rpx 20rpx;border-bottom:1rpx solid #eee5da}.order-head>view:first-child{display:flex;min-width:0;flex-direction:column;gap:5rpx}.order-head>view:first-child text:first-child{overflow:hidden;color:#474039;font-size:23rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.order-head>view:first-child text:last-child{color:#a3998e;font-size:17rpx}.status{flex:none;padding:7rpx 11rpx;border-radius:99rpx;background:#eef4ee;color:#628174;font-size:17rpx;font-weight:800}.status.complete{background:#e6f1ec;color:#447961}.status.closed{background:#f3ede8;color:#8f7f72}.order-item{display:grid;grid-template-columns:81rpx minmax(0,1fr) auto;align-items:center;gap:12rpx;padding:16rpx 20rpx;border-bottom:1rpx solid #f0e8de}.cover{width:81rpx;height:81rpx;border-radius:14rpx;background:#e4ddd3}.cover.fallback{display:grid;place-items:center;color:#648074;background:#dfe9df;font-family:"Songti SC","STSong",serif;font-size:32rpx}.item-info{display:flex;min-width:0;flex-direction:column;gap:4rpx}.item-info text:first-child{overflow:hidden;color:#484139;font-size:22rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.item-info text:nth-child(2){overflow:hidden;color:#8c8075;font-size:17rpx;text-overflow:ellipsis;white-space:nowrap}.item-info text:last-child{color:#a1988e;font-size:17rpx}.subtotal{align-self:flex-end;margin-bottom:4rpx;color:#555048;font-size:20rpx;font-weight:800}.order-total{display:flex;align-items:center;justify-content:space-between;padding:18rpx 20rpx}.order-total>text{color:#978b7f;font-size:19rpx}.order-total>view{display:flex;align-items:baseline;gap:8rpx}.order-total>view text:first-child{color:#918579;font-size:18rpx}.order-total>view text:last-child{color:#a55138;font-size:29rpx;font-weight:900}.pending-note,.server-note{display:flex;flex-direction:column;gap:4rpx;margin:0 15rpx 16rpx;padding:14rpx 15rpx;border-radius:15rpx;background:#fbf1eb}.pending-note text:first-child,.server-note text:first-child{color:#a25e45;font-size:18rpx;font-weight:900}.pending-note text:last-child,.server-note text:last-child{color:#947b6c;font-size:17rpx;line-height:1.55}.server-note{background:#f1f5ef}.server-note text:first-child{color:#628174}.server-note text:last-child{color:#849284}@keyframes stamp{50%{transform:rotate(-8deg) scale(1.08);opacity:.65}}
</style>
