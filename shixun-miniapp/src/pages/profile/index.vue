<template>
  <view class="page">
    <view class="ink-wash ink-wash-one" />
    <view class="ink-wash ink-wash-two" />

    <view class="profile-hero">
      <view class="seal-avatar">{{ displayName.slice(0, 1).toUpperCase() }}</view>
      <view class="identity">
        <text class="eyebrow">MY ATELIER</text>
        <text class="name">{{ displayName }}</text>
        <text class="role">{{ loggedIn ? `${creatorModeLabel} · 灵感与作品都在这里沉淀` : '先浏览首页，登录后再管理作品与订单' }}</text>
      </view>
    </view>

    <view v-if="!loggedIn" class="guest-card">
      <text class="guest-kicker">GUEST MODE</text>
      <text class="guest-title">先随意看看，再决定是否登录。</text>
      <text class="guest-copy">首页、选品方向和公开内容均可浏览；创作、保存、下单时再由你主动登录。</text>
      <button class="guest-login" @tap="goLogin">登录后管理我的创作</button>
      <text class="guest-home" @tap="goHome">暂不登录，返回首页继续浏览</text>
    </view>

    <view v-else class="welcome-card">
      <view>
        <text class="welcome-kicker">之间智造 · 创作服务</text>
        <text class="welcome-title">把一个灵感，慢慢做成一件好作品。</text>
      </view>
      <text class="welcome-seal">印</text>
    </view>

    <view v-if="loggedIn" class="section-heading">
      <text>创作与账户</text>
      <text>ACCOUNT</text>
    </view>
    <view v-if="loggedIn" class="menu-card">
      <view class="menu-row" @tap="go('/pages/works/index')">
        <view class="menu-icon artwork">作</view>
        <view class="menu-copy"><text>我的作品</text><text>查看创作成果与审核状态</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-row" @tap="go('/pages/commercial/index')">
        <view class="menu-icon market">做</view>
        <view class="menu-copy"><text>商品化申请</text><text>申请报价、打样或渠道代销</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-row" @tap="go('/pages/recharge/index')">
        <view class="menu-icon credit">点</view>
        <view class="menu-copy"><text>积分充值</text><text>管理创作所需积分</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-row" @tap="go('/pages/sample-payment/index')">
        <view class="menu-icon">¥</view>
        <view class="menu-copy"><text>打样费支付</text><text>审核通过后在这里完成支付</text></view>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-row" @tap="go('/pages/production-requests/index')">
        <view class="menu-icon production">流</view>
        <view class="menu-copy"><text>我的生产申请</text><text>审核、支付、制作和样品反馈统一查看</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-row" @tap="go('/pages/purpose/index')">
        <view class="menu-icon purpose">向</view>
        <view class="menu-copy"><text>切换创作模式与用途</text><text>{{ creatorModeLabel }} · 个人创作或景区、博物馆售卖</text></view>
        <text class="arrow">›</text>
      </view>
      <view v-if="creatorMode === 'professional'" class="menu-row" @tap="go('/pages/professional/index')">
        <view class="menu-icon professional">专</view>
        <view class="menu-copy"><text>专业作品提交</text><text>上传 ZIP 作品包，查看评审进度</text></view>
        <text class="arrow">›</text>
      </view>
    </view>

    <view v-if="loggedIn" class="section-heading market-heading">
      <text>文创商城</text>
      <text>MARKET &amp; ORDERS</text>
    </view>
    <view v-if="loggedIn" class="menu-card market-card">
      <view class="menu-row" @tap="go('/pages/market/index')">
        <view class="menu-icon market">集</view>
        <view class="menu-copy"><text>文创商城</text><text>浏览已审核的文化作品与实体衍生品</text></view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-row" @tap="go('/pages/orders/index')">
        <view class="menu-icon orders">单</view>
        <view class="menu-copy"><text>我的商城订单</text><text>查看已创建订单和当前处理状态</text></view>
        <text class="arrow">›</text>
      </view>
    </view>

    <view v-if="loggedIn" class="section-heading service-heading">
      <text>服务与保障</text>
      <text>CARE &amp; RIGHTS</text>
    </view>
    <view v-if="loggedIn" class="menu-card service-card">
      <view class="menu-row" @tap="go('/pages/support/index?tab=chat')">
        <view class="menu-icon service">问</view>
        <view class="menu-copy"><text>在线客服</text><text>查看历史消息，咨询创作、生产与订单</text></view>
        <view class="service-badge">在线</view>
        <text class="arrow">›</text>
      </view>
      <view class="menu-row" @tap="go('/pages/support/index?tab=rights')">
        <view class="menu-icon rights">权</view>
        <view class="menu-copy"><text>版权咨询</text><text>为作品登记著作权、专利或 IP 咨询</text></view>
        <text class="arrow">›</text>
      </view>
    </view>

    <button v-if="loggedIn" class="logout" @tap="logout">退出当前账号</button>
    <view class="bottom-nav">
      <view @tap="goHome"><text>⌂</text><text>首页</text></view>
      <view @tap="go('/pages/create/index?mode=text')"><text>✦</text><text>创作</text></view>
      <view @tap="go('/pages/works/index')"><text>▣</text><text>作品</text></view>
      <view class="active"><text>◉</text><text>我的</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { clearSession, getSession } from '../../utils/session'

const user = ref(getSession()?.user)
const loggedIn = computed(() => Boolean(user.value))
const creatorMode = ref<'amateur' | 'professional'>((uni.getStorageSync('creation_context') || {}).creatorMode === 'professional' ? 'professional' : 'amateur')
const displayName = computed(() => user.value?.username || '创作用户')
const creatorModeLabel = computed(() => creatorMode.value === 'professional' ? '专业创作用户' : '业余创作用户')
const go = (url: string) => uni.navigateTo({ url })
const goHome = () => uni.reLaunch({ url: '/pages/home/index' })
const goLogin = () => uni.navigateTo({ url: '/pages/login/index?from=profile' })

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
.bottom-nav { position: fixed; z-index: 5; right: 24rpx; bottom: calc(20rpx + env(safe-area-inset-bottom)); left: 24rpx; display: grid; grid-template-columns: repeat(4, 1fr); overflow: hidden; min-height: 96rpx; border: 1rpx solid rgba(116, 103, 83, .15); border-radius: 22rpx; background: rgba(255, 253, 249, .96); box-shadow: 0 13rpx 34rpx rgba(63, 50, 34, .16); }
.bottom-nav view { display: flex; align-items: center; justify-content: center; gap: 5rpx; min-width: 0; flex-direction: column; color: #8c877e; font-size: 18rpx; }
.bottom-nav view text:first-child { color: #849489; font-size: 27rpx; line-height: 1; }
.bottom-nav .active { color: #4f7563; background: #edf4ed; font-weight: 800; }
.bottom-nav .active text:first-child { color: #547b69; }
</style>
