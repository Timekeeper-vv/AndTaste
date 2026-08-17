<template>
  <view class="page">
    <view class="login-nav">
      <text class="back" @tap="leaveLogin">‹ 返回</text>
      <text class="browse" @tap="leaveLogin">暂不登录</text>
    </view>
    <view class="hero"><text class="eyebrow">AI CULTURAL CREATION</text><text class="title">之间智造</text><text class="sub">让文创灵感，成为可被看见的作品</text></view>
    <view class="card">
      <text class="card-title">欢迎回来</text>
      <text class="card-desc">登录后即可开始 AI 文创创作</text>
      <input v-model.trim="username" class="input" placeholder="用户名或邮箱" placeholder-class="placeholder" />
      <input v-model="password" class="input" password placeholder="密码" placeholder-class="placeholder" />
      <button class="primary" :loading="loading" @tap="login">登录并开始创作</button>
      <button class="wechat-button" :loading="wechatLoading" :disabled="wechatLoading || wechatPhoneRequired" @tap="wechatLogin">手机号快捷登录</button>
      <text v-if="fromWebview && wechatLoading" class="wechat-status">正在验证登录状态...</text>
      <view v-if="wechatPhoneRequired" class="wechat-phone-auth">
        <text class="profile-title">完成手机号验证</text>
        <text class="phone-copy">首次登录需完成手机号快捷验证，请点击下方按钮授权并继续。</text>
        <view class="consent-row" @tap="wechatTermsAccepted = !wechatTermsAccepted">
          <text class="check">{{ wechatTermsAccepted ? '✓' : '' }}</text><text>我已阅读并同意用户服务、隐私说明与内容规范，并确认后续合作按要求完成实名认证</text>
        </view>
        <button class="phone-auth-button" open-type="getPhoneNumber" phone-number-no-quota-toast="false" :loading="wechatLoading" :disabled="wechatLoading || !wechatTermsAccepted" @getphonenumber="authorizeWechatPhone">手机号快捷登录</button>
      </view>
      <view class="register-row"><text>还没有账号？</text><text @tap="goRegister">创建创作账号 ›</text></view>
      <text class="hint">支持用户名或邮箱登录。手机号快捷登录首次使用时需要补充必要资料并完成合规确认。</text>
      <text class="skip-login" @tap="leaveLogin">暂不登录，继续浏览首页</text>
    </view>
    <view v-if="campaigns.length || campaignLoading" class="campaign-board">
      <view class="campaign-board-head"><view><text class="campaign-kicker">PRIORITY BRIEFS</text><text class="campaign-title">馆方优先征集</text></view><text>选中后登录即带入</text></view>
      <text class="campaign-board-copy">选择平台正在征集的创作方向；作品审核通过后，积分自动到账。</text>
      <scroll-view scroll-x class="campaign-scroll" :show-scrollbar="false">
        <view class="campaign-row">
          <view v-for="campaign in campaigns" :key="campaign.key" class="campaign-card" :class="{ selected: selectedCampaignKey === campaign.key }" @tap="selectCampaign(campaign)">
            <view class="campaign-card-top"><text class="campaign-badge">优先征集</text><text class="campaign-points">+{{ campaign.rewardAmount }} 积分</text></view>
            <text class="campaign-target">面向 {{ campaign.targetName }}</text>
            <text class="campaign-style">{{ campaign.collectionStyle }}</text>
            <text class="campaign-products">推荐：{{ campaign.recommendedProducts.join(' / ') }}</text>
            <text class="campaign-action">{{ selectedCampaignKey === campaign.key ? '已选择，登录后开始' : '选择这个方向 ›' }}</text>
          </view>
          <view v-if="campaignLoading && !campaigns.length" class="campaign-loading"><text>正在加载征集任务…</text></view>
        </view>
      </scroll-view>
      <text class="campaign-notice">平台优先征集方向不代表目标机构已采购、合作、授权或认可具体作品。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { ApiError, request } from '../../api/client'
import { getPublicCreatorCampaigns, type CreatorCampaign } from '../../api/creative'
import { saveSession } from '../../utils/session'
const username = ref('')
const password = ref('')
const loading = ref(false)
const fromWebview = ref(false)
const miniWebLoginSession = ref('')
const wechatLoading = ref(false)
const wechatPhoneRequired = ref(false)
const wechatTermsAccepted = ref(false)
const campaigns = ref<CreatorCampaign[]>([])
const campaignLoading = ref(false)
const selectedCampaignKey = ref('')

function handleOfficialPrivacyAuthorization() {
  // The coupled WeChat button has already synchronized the official privacy
  // consent. The phone callback below remains the only place that accepts the
  // one-time phone code.
}

function finishLogin(session: any) {
  if (!session?.token || !session?.user) throw new Error('登录响应缺少令牌')
  if (session.user.role !== 'user') throw new Error('该账号是管理端账号，请使用网页管理端登录')
  saveSession(session)
  if (miniWebLoginSession.value) {
    uni.showToast({ title: '网页已登录', icon: 'success' })
    setTimeout(() => uni.reLaunch({ url: '/pages/webview/index' }), 700)
  } else if (fromWebview.value) {
    uni.setStorageSync('smart_pig_auth_updated', String(Date.now()))
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: '/pages/purpose/index' })
  }
}

async function login() {
  if (!username.value || !password.value) return uni.showToast({ title: '请输入用户名或邮箱和密码', icon: 'none' })
  loading.value = true
  try {
    const session = await request<any>('/api/users/login', { method: 'POST', data: { username: username.value, password: password.value }, header: { 'content-type': 'application/json' } })
    finishLogin(session)
  } catch (error: any) { uni.showToast({ title: error.message || '登录失败', icon: 'none' }) } finally { loading.value = false }
}

function miniProgramLoginCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: result => result.code ? resolve(result.code) : reject(new Error('登录凭证获取失败')),
      fail: () => reject(new Error('快捷登录失败，请确认当前运行在小程序中')),
    })
  })
}

async function wechatLogin() {
  if (wechatLoading.value) return
  wechatLoading.value = true
  try {
    const code = await miniProgramLoginCode()
    const data: Record<string, any> = { code }
    if (miniWebLoginSession.value) data.miniWebLoginSession = miniWebLoginSession.value
    const session = await request<any>('/api/users/wechat-login', { method: 'POST', data, header: { 'content-type': 'application/json', Authorization: '' } })
    finishLogin(session)
  } catch (error: any) {
    if (error instanceof ApiError && error.code === 'WECHAT_PROFILE_REQUIRED') {
      wechatPhoneRequired.value = true
    } else {
      uni.showToast({ title: error?.message || '手机号快捷登录失败', icon: 'none' })
    }
  } finally { wechatLoading.value = false }
}

async function authorizeWechatPhone(event: any) {
  const phoneCode = String(event?.detail?.code || '').trim()
  const errorMessage = String(event?.detail?.errMsg || '').trim()
  const errorNumber = event?.detail?.errno == null ? '' : String(event.detail.errno)
  if (!phoneCode) {
    const isDevtools = /devtools|simulator|mock/i.test(errorMessage)
    const isOutOfQuota = errorNumber === '1400001'
    const diagnostic = [errorMessage, errorNumber ? `errno=${errorNumber}` : ''].filter(Boolean).join('；')
    uni.showModal({
      title: isDevtools ? '请使用真机授权' : isOutOfQuota ? '手机号验证额度不足' : '手机号授权未完成',
      content: isDevtools
        ? '开发者工具模拟器不支持真实手机号授权。请点击工具栏“预览”，用真实设备扫码打开小程序后再授权。'
        : isOutOfQuota
          ? '本小程序的手机号验证体验额度已用完，请补充“手机号快速验证组件”用量后再试。'
        : `请在系统授权弹窗中选择“允许”。如果没有弹窗，请确认小程序已认证并在隐私指引中声明手机号后再试。${diagnostic ? `\n\n平台返回：${diagnostic}` : ''}`,
      showCancel: false,
    })
    return
  }
  if (!wechatTermsAccepted.value) {
    uni.showToast({ title: '请先同意用户服务与隐私说明', icon: 'none' })
    return
  }
  if (wechatLoading.value) return
  wechatLoading.value = true
  try {
    const loginCode = await miniProgramLoginCode()
    const session = await request<any>('/api/users/wechat-phone-login', {
      method: 'POST',
      data: {
        loginCode,
        phoneCode,
        agreeTerms: true,
        ...(miniWebLoginSession.value ? { miniWebLoginSession: miniWebLoginSession.value } : {}),
      },
      header: { 'content-type': 'application/json', Authorization: '' },
    })
    finishLogin(session)
  } catch (error: any) {
    uni.showToast({ title: error?.message || '手机号授权登录失败，请重试', icon: 'none' })
  } finally { wechatLoading.value = false }
}

function goRegister() { uni.navigateTo({ url: '/pages/register/index' }) }

function readPendingCampaign() {
  const value = uni.getStorageSync('pending_creator_campaign')
  if (value && typeof value === 'object' && typeof value.key === 'string' && typeof value.channelCode === 'string') {
    selectedCampaignKey.value = value.key
  }
}

function selectCampaign(campaign: CreatorCampaign) {
  selectedCampaignKey.value = campaign.key
  uni.setStorageSync('pending_creator_campaign', { ...campaign, selectedAt: Date.now() })
  uni.showToast({ title: '任务已选，登录后自动带入创作', icon: 'none' })
}

async function loadCampaigns() {
  campaignLoading.value = true
  try {
    const rows = await getPublicCreatorCampaigns()
    campaigns.value = Array.isArray(rows) ? rows : []
  } catch {
    // Login must remain usable when an optional public task board is offline.
    campaigns.value = []
  } finally {
    campaignLoading.value = false
  }
}

function leaveLogin() {
  // Web login is an explicit handoff. Do not interrupt it with a route change.
  if (miniWebLoginSession.value) return
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: '/pages/home/index' })
}

onLoad((query: Record<string, string> = {}) => {
  fromWebview.value = query.from === 'webview'
  miniWebLoginSession.value = query.miniWebLoginSession || ''
  readPendingCampaign()
  void loadCampaigns()
  // The web-view login button already represents an explicit user action.
  // Continue that action automatically after the native page is ready.
  if (fromWebview.value || miniWebLoginSession.value) setTimeout(() => void wechatLogin(), 80)
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;padding:46rpx 48rpx 60rpx;background:radial-gradient(ellipse at 10% 4%,rgba(143,165,154,.24),transparent 29%),radial-gradient(circle at 90% 19%,rgba(185,102,79,.12),transparent 23%),linear-gradient(180deg,#faf8f3,#f1ebe2);box-sizing:border-box}.page::before{content:"";position:absolute;right:-90rpx;top:78rpx;width:320rpx;height:104rpx;border-radius:50%;background:rgba(102,132,118,.1);filter:blur(17rpx);transform:rotate(-14deg)}.login-nav{position:relative;z-index:2;display:flex;align-items:center;justify-content:space-between;min-height:58rpx;margin:0 -8rpx 47rpx;color:#5f796c;font-size:24rpx;font-weight:800}.back,.browse{padding:12rpx}.browse{border:1rpx solid #d9e5da;border-radius:999rpx;background:rgba(255,253,249,.72);font-size:21rpx}.hero,.card{position:relative;z-index:1}.hero{display:flex;flex-direction:column;margin-bottom:50rpx}.eyebrow{font-size:17rpx;letter-spacing:4rpx;color:#5f7d70;font-weight:800}.title{margin-top:20rpx;color:#2e2a25;font-family:"Songti SC","STSong",serif;font-size:76rpx;font-weight:700;letter-spacing:5rpx;line-height:1.2}.sub{margin-top:17rpx;color:#776e64;font-family:"Songti SC","STSong",serif;font-size:29rpx;line-height:1.7}.card{border:1rpx solid rgba(120,103,84,.14);border-radius:32rpx;background:rgba(255,253,249,.88);padding:43rpx 35rpx;box-shadow:0 22rpx 52rpx rgba(76,59,41,.10)}.card-title{display:block;color:#37312b;font-family:"Songti SC","STSong",serif;font-size:42rpx;font-weight:700}.card-desc{display:block;margin:12rpx 0 34rpx;color:#8a8075;font-size:24rpx}.input{box-sizing:border-box;width:100%;height:92rpx;margin-bottom:18rpx;padding:0 24rpx;border:1rpx solid #e4dcd1;border-radius:15rpx;background:#fbf9f4;color:#403a34;font-size:28rpx}.placeholder{color:#b5aa9e}.primary{height:94rpx;line-height:94rpx;margin-top:13rpx;border-radius:17rpx;background:linear-gradient(135deg,#3d3933,#627f72);color:#fff;font-size:28rpx;font-weight:800;box-shadow:0 12rpx 22rpx rgba(52,58,52,.16)}.register-row{display:flex;justify-content:center;gap:9rpx;margin-top:22rpx;color:#9c9185;font-size:21rpx}.register-row text:last-child{color:#5d7d6e;font-weight:850}.hint{display:block;margin-top:17rpx;color:#a09387;text-align:center;font-size:20rpx;line-height:1.65}.skip-login{display:block;margin:25rpx auto 0;padding:14rpx;color:#597768;text-align:center;font-size:23rpx;font-weight:800}
.wechat-button{height:84rpx;line-height:84rpx;margin-top:18rpx;border:1rpx solid #6b907b;border-radius:17rpx;background:#f4faf4;color:#4d755f;font-size:27rpx;font-weight:800}.wechat-button[disabled]{color:#9aa99f;background:#eef2ee;border-color:#d7e0d8}.wechat-status{display:block;margin-top:12rpx;color:#6f8977;text-align:center;font-size:21rpx}.wechat-phone-auth{margin-top:23rpx;padding:22rpx;border:1rpx solid #d9e5d9;border-radius:18rpx;background:#f5f9f3}.profile-title{display:block;margin-bottom:5rpx;color:#4f745e;font-size:24rpx;font-weight:800}.phone-copy{display:block;color:#718078;font-size:21rpx;line-height:1.55}.consent-row{display:flex;align-items:flex-start;gap:10rpx;margin-top:14rpx;color:#6f756d;font-size:20rpx;line-height:1.45}.check{display:grid;place-items:center;flex:none;width:28rpx;height:28rpx;border:1rpx solid #bfcfc0;border-radius:7rpx;background:#fff;color:#4f8364;font-weight:900}.phone-auth-button{height:84rpx;line-height:84rpx;margin-top:18rpx;border-radius:14rpx;background:#4d8064;color:#fff;font-size:26rpx;font-weight:800}.phone-auth-button[disabled]{color:#b7c6ba;background:#cfddd1}
.page{overflow:visible;padding-bottom:80rpx}.campaign-board{position:relative;z-index:1;margin-top:28rpx;padding:25rpx 23rpx 21rpx;border:1rpx solid rgba(104,126,112,.22);border-radius:22rpx;background:rgba(255,253,249,.82);box-shadow:0 13rpx 30rpx rgba(68,57,43,.07)}.campaign-board-head{display:flex;align-items:flex-end;justify-content:space-between;gap:12rpx}.campaign-board-head view{display:flex;flex-direction:column;gap:4rpx}.campaign-kicker{color:#708b7c;font-size:16rpx;font-weight:850;letter-spacing:2rpx}.campaign-title{color:#39342e;font-family:"Songti SC","STSong",serif;font-size:34rpx;font-weight:700}.campaign-board-head>text{padding:6rpx 8rpx;border:1rpx solid #d9e4da;border-radius:8rpx;background:#f4f8f4;color:#63806e;font-size:17rpx;font-weight:750}.campaign-board-copy{display:block;margin-top:11rpx;color:#7c7369;font-size:20rpx;line-height:1.55}.campaign-scroll{width:calc(100% + 8rpx);margin:18rpx -4rpx 0;white-space:nowrap}.campaign-row{display:flex;gap:12rpx;padding:1rpx 4rpx 7rpx}.campaign-card{display:flex;flex:0 0 402rpx;min-height:214rpx;box-sizing:border-box;flex-direction:column;padding:17rpx;border:1rpx solid #e3d9ce;border-radius:16rpx;background:#fffefa;white-space:normal}.campaign-card.selected{border-color:#6f947e;background:#eff6ef;box-shadow:0 8rpx 18rpx rgba(76,108,88,.12)}.campaign-card-top{display:flex;align-items:center;justify-content:space-between;gap:10rpx}.campaign-badge{padding:5rpx 8rpx;border-radius:7rpx;background:#e7f0e8;color:#577865;font-size:16rpx;font-weight:850}.campaign-points{color:#b25e45;font-size:18rpx;font-weight:900}.campaign-target{display:block;margin-top:14rpx;overflow:hidden;color:#3d3832;font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.campaign-style{display:block;margin-top:7rpx;color:#627c6d;font-size:19rpx;font-weight:800}.campaign-products{display:block;margin-top:8rpx;overflow:hidden;color:#8b8075;font-size:17rpx;text-overflow:ellipsis;white-space:nowrap}.campaign-action{display:block;margin-top:auto;padding-top:12rpx;color:#a55f49;font-size:18rpx;font-weight:850}.campaign-loading{display:flex;flex:0 0 260rpx;align-items:center;justify-content:center;min-height:214rpx;border:1rpx dashed #d8cfc3;border-radius:16rpx;background:#fbf9f5;color:#978b7f;font-size:19rpx}.campaign-notice{display:block;margin-top:4rpx;color:#9a9085;font-size:16rpx;line-height:1.5}
</style>
