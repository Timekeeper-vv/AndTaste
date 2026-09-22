<template>
  <view class="page">
    <view class="mini-top"><text class="mini-title">我的</text><view class="mini-menu"><text>•••</text><text>◉</text></view></view>
    <view class="profile-hero">
      <image class="seal-avatar" src="/static/ui-design/brand-logo.png" mode="aspectFit" />
      <view class="identity"><text class="eyebrow">MY ATELIER</text><text class="name">{{ displayName }}</text><text class="role">{{ loggedIn ? '灵感与作品都在这里沉淀' : '登录后管理作品与订单' }}</text></view>
    </view>

    <view v-if="!loggedIn" class="guest-card"><text class="guest-kicker">GUEST MODE</text><text class="guest-title">登录后查看创作资产</text><text class="guest-copy">作品、审核进度和商城订单都会安全保存在你的账户中。</text><button class="guest-login" @tap="goLogin">登录</button><text class="guest-home" @tap="goHome">返回首页继续浏览</text></view>

    <template v-else>
      <view class="token-card"><view><text>Token　余额</text><text>{{ creditBalance }}</text><text>创作消耗 Token · 充值或任务获取</text></view><text class="token-button" @tap="go('/pages/recharge/index')">＋ 充值</text></view>
      <view class="order-card"><view class="order-head"><text>我的订单</text><text @tap="go('/pages/orders/index')">全部订单 ›</text></view><view class="order-grid"><view @tap="go('/pages/orders/index')"><image class="order-icon" src="/static/ui-design/order-pay.png" mode="aspectFit" /><text>{{ orderCounts.pendingPay }}</text><text>待付款</text></view><view @tap="go('/pages/orders/index')"><image class="order-icon" src="/static/ui-design/order-ship.png" mode="aspectFit" /><text>{{ orderCounts.pendingShip }}</text><text>待发货</text></view><view @tap="go('/pages/orders/index')"><image class="order-icon" src="/static/ui-design/order-receive.png" mode="aspectFit" /><text>{{ orderCounts.pendingReceive }}</text><text>待收货</text></view></view></view>
      <view class="section-heading"><text>ACCOUNT · 账户与服务</text><text @tap="go('/pages/conversation-create/index')">历史对话 ›</text></view>
      <view class="menu-card">
        <view class="menu-row" @tap="go('/pages/works/index')"><image class="menu-icon" src="/static/ui-design/service-works.png" mode="aspectFit" /><view class="menu-copy"><text>我的作品</text><text>查看创作成果与审核状态</text></view><text class="arrow">›</text></view>
        <view class="menu-row" @tap="openAddress"><image class="menu-icon" src="/static/ui-design/service-address.png" mode="aspectFit" /><view class="menu-copy"><text>收货地址</text><text>管理订单收货地址</text></view><text class="arrow">›</text></view>
        <view class="menu-row" @tap="go('/pages/support/index?tab=chat')"><image class="menu-icon" src="/static/ui-design/service-support.png" mode="aspectFit" /><view class="menu-copy"><text>在线客服</text><text>创作咨询 · 打样咨询 · 售后优化</text></view><view class="service-badge">8</view><text class="arrow">›</text></view>
      </view>
      <button class="logout" @tap="logout">退出当前账号</button>
    </template>

    <view class="bottom-nav"><view @tap="goHome"><text>⌂</text><text>首页</text></view><view @tap="go('/pages/works/index')"><text>▣</text><text>作品</text></view><view class="active"><text>◉</text><text>我的</text></view></view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCredits } from '../../api/creative'
import { getMarketplaceOrders, type MarketplaceOrder } from '../../api/marketplace'
import { clearSession, getSession } from '../../utils/session'

const user = ref(getSession()?.user)
const loggedIn = computed(() => Boolean(user.value))
const creatorMode = ref<'amateur' | 'professional'>((uni.getStorageSync('creation_context') || {}).creatorMode === 'professional' ? 'professional' : 'amateur')
const displayName = computed(() => user.value?.username || '创作用户')
const creatorModeLabel = computed(() => creatorMode.value === 'professional' ? '专业创作用户' : '业余创作用户')
const creditBalance = ref(0)
const orders = ref<MarketplaceOrder[]>([])
const orderCounts = computed(() => orders.value.reduce((counts, order) => {
  const status = String(order.orderStatus || '').toLowerCase()
  if (['pending_pay', 'pending_payment', 'created'].includes(status)) counts.pendingPay += 1
  if (['paid', 'pending_ship', 'pending_shipment', 'processing'].includes(status)) counts.pendingShip += 1
  if (['shipped', 'in_transit', 'pending_receive', 'pending_receipt'].includes(status)) counts.pendingReceive += 1
  return counts
}, { pendingPay: 0, pendingShip: 0, pendingReceive: 0 }))
const go = (url: string) => uni.navigateTo({ url })
const goHome = () => uni.reLaunch({ url: '/pages/home/index' })
const goLogin = () => uni.navigateTo({ url: '/pages/login/index?from=profile' })

function openAddress() {
  uni.showToast({ title: '收货地址在确认订单时维护', icon: 'none' })
}

onShow(() => {
  user.value = getSession()?.user
  if (!user.value) return
  void Promise.allSettled([getCredits(), getMarketplaceOrders()]).then(([creditResult, orderResult]) => {
    if (creditResult.status === 'fulfilled') creditBalance.value = Number(creditResult.value?.balance) || 0
    if (orderResult.status === 'fulfilled') orders.value = Array.isArray(orderResult.value) ? orderResult.value : []
  })
})

function logout() {
  uni.showModal({
    title: '退出登录',
    content: '退出后需要重新登录才能继续管理作品和服务咨询，确定退出吗？',
    success: result => {
      if (!result.confirm) return
      clearSession()
      uni.reLaunch({ url: '/pages/login/index' })
    },
  })
}
</script>

<style scoped lang="scss">
.page {
  position: relative;
  min-height: 100vh;
  box-sizing: border-box;
  overflow: hidden;
  padding: 58rpx 32rpx calc(156rpx + env(safe-area-inset-bottom));
  background:
    linear-gradient(145deg, rgba(255, 255, 255, .8), rgba(245, 240, 230, .92)),
    #f7f3ed;
}
.ink-wash { position: absolute; border-radius: 999rpx; pointer-events: none; filter: blur(2rpx); }
.ink-wash-one { top: -116rpx; right: -116rpx; width: 420rpx; height: 360rpx; opacity: .68; background: radial-gradient(ellipse, rgba(113, 143, 128, .2) 0%, rgba(113, 143, 128, .04) 48%, transparent 72%); transform: rotate(-24deg); }
.ink-wash-two { left: -220rpx; bottom: 130rpx; width: 460rpx; height: 280rpx; opacity: .6; background: radial-gradient(ellipse, rgba(177, 111, 84, .12), transparent 67%); transform: rotate(17deg); }
.profile-hero, .welcome-card, .guest-card, .section-heading, .menu-card, .logout { position: relative; z-index: 1; }
.profile-hero { display: flex; align-items: center; gap: 23rpx; padding: 18rpx 7rpx 42rpx; }
.seal-avatar { display: flex; align-items: center; justify-content: center; flex: none; width: 106rpx; height: 106rpx; border: 5rpx solid rgba(255, 255, 255, .74); border-radius: 36rpx 30rpx 38rpx 26rpx; color: #fffaf2; background: linear-gradient(145deg, #587669, #88a293); box-shadow: 0 15rpx 29rpx rgba(48, 78, 67, .23), inset 0 0 0 1rpx rgba(255, 255, 255, .28); font-family: "Songti SC", "STSong", serif; font-size: 47rpx; font-weight: 800; transform: rotate(-5deg); }
.identity { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.eyebrow { color: #81958b; font-size: 18rpx; font-weight: 800; letter-spacing: 2.1rpx; }
.name { overflow: hidden; margin-top: 7rpx; color: #292c28; font-family: "Songti SC", "STSong", serif; font-size: 40rpx; font-weight: 800; line-height: 1.2; text-overflow: ellipsis; white-space: nowrap; }
.role { margin-top: 10rpx; color: #7a8078; font-size: 22rpx; }
.guest-card { display:flex; flex-direction:column; margin-top:8rpx; padding:31rpx 28rpx; border:1rpx solid rgba(101,126,110,.2); border-radius:24rpx; background:linear-gradient(145deg,#f8fcf7,#edf3ea); box-shadow:0 13rpx 29rpx rgba(62,81,66,.08); }.guest-kicker{color:#6d8c7d;font-size:18rpx;font-weight:900;letter-spacing:2rpx}.guest-title{margin-top:12rpx;color:#354239;font-family:"Songti SC","STSong",serif;font-size:34rpx;font-weight:800}.guest-copy{margin-top:11rpx;color:#788078;font-size:21rpx;line-height:1.6}.guest-login{height:88rpx;line-height:88rpx;margin-top:25rpx;border-radius:15rpx;background:#527766;color:#fff;font-size:25rpx;font-weight:800}.guest-home{padding:22rpx 10rpx 0;color:#597766;text-align:center;font-size:22rpx;font-weight:800}
.welcome-card { display: flex; align-items: center; justify-content: space-between; gap: 24rpx; overflow: hidden; box-sizing: border-box; min-height: 170rpx; padding: 29rpx 28rpx 27rpx; border: 1rpx solid rgba(100, 112, 94, .15); border-radius: 30rpx; background: linear-gradient(125deg, rgba(249, 252, 247, .97), rgba(232, 239, 231, .89)); box-shadow: 0 17rpx 38rpx rgba(57, 63, 50, .08); }
.welcome-card::before { position: absolute; top: -35rpx; right: 32rpx; width: 188rpx; height: 156rpx; border: 1rpx solid rgba(107, 135, 116, .12); border-radius: 50%; content: ''; }
.welcome-kicker { display: block; color: #6e8c7d; font-size: 19rpx; font-weight: 800; letter-spacing: 1.6rpx; }
.welcome-title { display: block; max-width: 480rpx; margin-top: 11rpx; color: #31362f; font-family: "Songti SC", "STSong", serif; font-size: 31rpx; font-weight: 700; line-height: 1.45; }
.welcome-seal { position: relative; z-index: 1; display: flex; align-items: center; justify-content: center; width: 53rpx; height: 53rpx; border: 2rpx solid rgba(154, 77, 52, .72); border-radius: 7rpx; color: #a4553c; font-family: "Songti SC", "STSong", serif; font-size: 27rpx; font-weight: 800; transform: rotate(-9deg); }
.section-heading { display: flex; align-items: baseline; justify-content: space-between; margin: 43rpx 6rpx 17rpx; }
.section-heading text:first-child { color: #3f453e; font-family: "Songti SC", "STSong", serif; font-size: 29rpx; font-weight: 800; }
.section-heading text:last-child { color: #93a198; font-size: 17rpx; font-weight: 800; letter-spacing: 1.6rpx; }
.service-heading { margin-top: 37rpx; }
.market-heading { margin-top: 37rpx; }
.menu-card { overflow: hidden; border: 1rpx solid rgba(116, 103, 83, .14); border-radius: 28rpx; background: rgba(255, 253, 249, .9); box-shadow: 0 13rpx 32rpx rgba(67, 53, 37, .055); }
.menu-row { display: flex; align-items: center; min-height: 122rpx; padding: 0 24rpx; border-bottom: 1rpx solid #eee7de; }
.menu-row:last-child { border-bottom: 0; }
.menu-row:active { background: #f8f4ed; }
.menu-icon { display: flex; align-items: center; justify-content: center; flex: none; width: 58rpx; height: 58rpx; margin-right: 19rpx; border-radius: 18rpx; font-family: "Songti SC", "STSong", serif; font-size: 27rpx; font-weight: 800; }
.artwork { color: #55796a; background: #e8f0e9; }.credit { color: #b56b46; background: #f9ebdf; }.purpose { color: #8d7655; background: #f4efe2; }.professional { color: #a45f48; background: #f6e9e1; }.style { color: #667b95; background: #e8edf5; }.market { color: #5d7e70; background: #e6f0e8; }.orders { color: #a06249; background: #f8ebe2; }.service { color: #4f8374; background: #e4f2ed; }.rights { color: #a26047; background: #f6e9e2; }.production { color: #5d7c91; background: #e7eef4; }
.menu-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.menu-copy text:first-child { color: #33352f; font-size: 29rpx; font-weight: 700; }
.menu-copy text:last-child { overflow: hidden; margin-top: 7rpx; color: #909087; font-size: 20rpx; text-overflow: ellipsis; white-space: nowrap; }
.arrow { margin-left: 14rpx; color: #8ba094; font-size: 42rpx; font-weight: 300; line-height: 1; }
.service-badge { margin-left: 10rpx; padding: 4rpx 10rpx; border-radius: 99rpx; color: #4a8370; background: #e5f5ed; font-size: 18rpx; font-weight: 800; }
.logout { width: 100%; height: 91rpx; margin-top: 49rpx; border: 1rpx solid #ecdcd1; border-radius: 22rpx; color: #a3654d; background: rgba(250, 242, 236, .9); font-size: 27rpx; }
.bottom-nav { position: fixed; z-index: 5; right: 24rpx; bottom: calc(20rpx + env(safe-area-inset-bottom)); left: 24rpx; display: grid; grid-template-columns: repeat(3, 1fr); overflow: hidden; min-height: 96rpx; border: 1rpx solid rgba(116, 103, 83, .15); border-radius: 22rpx; background: rgba(255, 253, 249, .96); box-shadow: 0 13rpx 34rpx rgba(63, 50, 34, .16); }
.bottom-nav view { display: flex; align-items: center; justify-content: center; gap: 5rpx; min-width: 0; flex-direction: column; color: #8c877e; font-size: 18rpx; }
.bottom-nav view text:first-child { color: #849489; font-size: 27rpx; line-height: 1; }
.bottom-nav .active { color: #4f7563; background: #edf4ed; font-weight: 800; }
.bottom-nav .active text:first-child { color: #547b69; }
</style>

<style scoped lang="scss">
.page{overflow:visible;padding:calc(28rpx + env(safe-area-inset-top)) 32rpx calc(150rpx + env(safe-area-inset-bottom));background:#fff;color:#222}.mini-top{position:relative;display:flex;align-items:center;justify-content:center;height:68rpx;margin-bottom:14rpx}.mini-title{font-size:30rpx;font-weight:800}.mini-menu{position:absolute;right:0;display:flex;align-items:center;gap:14rpx;padding:8rpx 17rpx;border:1rpx solid #e4e4e4;border-radius:30rpx;color:#333;font-size:24rpx}.profile-hero{padding:16rpx 0 28rpx}.seal-avatar{width:72rpx;height:72rpx;border:0;border-radius:18rpx;box-shadow:none;transform:none}.eyebrow{display:none}.name{margin-top:4rpx;font-family:"PingFang SC",sans-serif;font-size:38rpx}.role{margin-top:6rpx;color:#8d9591;font-size:20rpx}.welcome-card{display:none}.token-card{display:flex;align-items:center;justify-content:space-between;margin:8rpx 0 27rpx;padding:22rpx 26rpx;border-radius:28rpx;background:linear-gradient(110deg,#0ccbb0,#003e35);color:#fff}.token-card view{display:flex;flex-direction:column}.token-card view text:first-child{font-size:19rpx}.token-card view text:nth-child(2){margin-top:3rpx;font-size:49rpx;font-weight:700}.token-card view text:last-child{margin-top:1rpx;color:#b8f6e8;font-size:17rpx}.token-button{padding:10rpx 19rpx;border-radius:30rpx;background:#0ce4c0;font-size:21rpx;font-weight:800}.order-card{padding:25rpx 23rpx;border:1rpx solid #e1edf7;border-radius:24rpx;background:#fff;box-shadow:0 8rpx 22rpx rgba(81,117,157,.1)}.order-head{display:flex;align-items:center;justify-content:space-between}.order-head text:first-child{font-family:"PingFang SC",sans-serif;font-size:29rpx;font-weight:800}.order-head text:last-child{color:#888;font-size:19rpx}.order-grid{display:grid;grid-template-columns:repeat(3,1fr);margin-top:26rpx}.order-grid view{display:flex;align-items:center;flex-direction:column;gap:6rpx}.order-icon{width:58rpx;height:48rpx}.order-grid view text:nth-child(2){font-size:29rpx;font-weight:800}.order-grid view text:last-child{color:#888;font-size:18rpx}.section-heading{margin:31rpx 5rpx 15rpx}.section-heading text:first-child{font-family:"PingFang SC",sans-serif;font-size:25rpx}.section-heading text:last-child{font-size:18rpx}.menu-card{border:0;background:transparent;box-shadow:none;overflow:visible}.menu-row{min-height:107rpx;margin-bottom:15rpx;padding:0 22rpx;border:1rpx solid #e6eef4;border-radius:23rpx;background:#fff;box-shadow:0 7rpx 20rpx rgba(60,91,117,.08)}.menu-icon{width:72rpx;height:72rpx;margin-right:17rpx;border-radius:17rpx}.menu-copy text:first-child{font-size:27rpx}.menu-copy text:last-child{font-size:19rpx}.logout{height:82rpx;margin-top:34rpx;border:0;background:#f1f5f3;color:#719083;font-size:24rpx}.bottom-nav{right:28rpx;bottom:20rpx;left:28rpx;grid-template-columns:repeat(3,1fr);height:84rpx;padding:10rpx 12rpx;border:0;border-radius:40rpx;background:linear-gradient(90deg,#12d8bd,#00b9a6);box-shadow:0 12rpx 28rpx rgba(0,161,141,.24)}.bottom-nav view{color:#dffff8;font-size:18rpx}.bottom-nav .active{color:#fff;background:none}.service-badge{display:grid;place-items:center;width:32rpx;height:32rpx;padding:0;border-radius:50%;background:#ff5c61;color:#fff;font-size:17rpx}
</style>
