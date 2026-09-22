<template>
  <view class="page">
    <view class="mini-top"><text class="back" @tap="leaveLogin">‹</text><text class="mini-dots">•••</text><text class="mini-circle">○</text></view>
    <view class="brand-lockup"><image class="brand-seal" src="/static/ui-design/brand-logo.png" mode="aspectFit" /><text class="brand-name">之间智造</text><text class="brand-en">AND SMART MFG</text><text class="brand-slogan">把创意变成好产品</text></view>
    <view class="card">
      <button class="wechat-button" :loading="wechatLoading" :disabled="wechatLoading || wechatPhoneRequired" @tap="wechatLogin"><text class="wechat-mark">●</text> 微信登录</button>
      <button class="phone-button" :loading="wechatLoading" @tap="wechatLogin"><text class="phone-mark">▯</text> 手机号登录</button>
      <view class="agreement" @tap="wechatTermsAccepted = !wechatTermsAccepted"><text class="checkbox">{{ wechatTermsAccepted ? '✓' : '' }}</text><text>我已阅读并同意《用户协议》与《隐私协议》</text></view>
      <text class="password-link" @tap="showPasswordLogin = !showPasswordLogin">账号密码登录</text><view v-if="showPasswordLogin" class="password-login"><input v-model.trim="username" class="input" placeholder="用户名或邮箱" placeholder-class="placeholder" /><input v-model="password" class="input" password placeholder="密码" placeholder-class="placeholder" /><button class="primary" :loading="loading" @tap="login">登录并开始创作</button></view>
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
      <text class="skip-login" @tap="leaveLogin">暂不登录，继续浏览首页</text>
    </view>
    <view v-if="displayedCampaigns.length || campaignLoading" class="campaign-board">
      <view class="campaign-board-head"><view><text class="campaign-kicker">馆方征集 · 投稿通道</text><text class="campaign-title">馆方征集 · 投稿通道</text></view><text>更多 ›</text></view>
      <scroll-view scroll-x class="campaign-scroll" :show-scrollbar="false">
        <view class="campaign-row">
          <view v-for="(campaign, index) in displayedCampaigns" :key="campaign.key" class="campaign-card" :class="{ selected: selectedCampaignKey === campaign.key }" @tap="selectCampaign(campaign)">
            <image class="campaign-art" :src="campaignImage(index)" mode="aspectFill" /><view class="campaign-info"><text class="campaign-target">{{ campaign.targetName }}｜{{ campaign.title }}</text><text class="campaign-style">{{ campaign.collectionStyle }} · 文创转化</text><text class="campaign-products">作品审核通过 · +{{ campaign.rewardAmount }} 创作积分</text></view><text class="campaign-points">+{{ campaign.rewardAmount }}</text>
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
import { computed, ref } from 'vue'
import { ApiError, request } from '../../api/client'
import { getPublicCreatorCampaigns, type CreatorCampaign } from '../../api/creative'
import { saveSession } from '../../utils/session'
const username = ref('')
const password = ref('')
const loading = ref(false)
const fromWebview = ref(false)
const miniWebLoginSession = ref('')
const redirectUrl = ref('')
const wechatLoading = ref(false)
const wechatPhoneRequired = ref(false)
const wechatTermsAccepted = ref(false)
const showPasswordLogin = ref(false)
const campaigns = ref<CreatorCampaign[]>([])
const campaignLoading = ref(false)
const selectedCampaignKey = ref('')
const campaignFallbacks: CreatorCampaign[] = [
  { key: 'design-bronze', title: '青铜器主题', targetName: '国家博物馆', channelCode: 'design-bronze', collectionStyle: '青铜纹样 · 器物元素', recommendedProducts: [], brief: '', promptHint: '', rewardAmount: 80 },
  { key: 'design-lacquer', title: '漆器纹样主题', targetName: '湖北省博物馆', channelCode: 'design-lacquer', collectionStyle: '漆器纹样 · 色彩元素', recommendedProducts: [], brief: '', promptHint: '', rewardAmount: 60 },
]
const displayedCampaigns = computed(() => campaigns.value.length ? campaigns.value : campaignFallbacks)
const campaignImage = (index: number) => index % 2 ? '/static/ui-design/campaign-drum.jpg' : '/static/ui-design/campaign-bronze.jpg'

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
  } else if (redirectUrl.value) {
    uni.reLaunch({ url: redirectUrl.value })
  } else {
    uni.reLaunch({ url: '/pages/purpose/index' })
  }
}

async function login() {
  if (!username.value || !password.value) return uni.showToast({ title: '请输入用户名或邮箱和密码', icon: 'none' })
  loading.value = true
  try {
    const session = await request<any>('/api/users/login', { method: 'POST', data: { username: username.value, password: password.value }, header: { 'content-type': 'application/json' } })
    // Keep the password-authenticated session before asking the server to bind
    // the current WeChat OpenID. A prior quick-login placeholder is safely
    // merged only after both identities have been verified.
    saveSession(session)
    try {
      const code = await miniProgramLoginCode()
      const binding = await request<any>('/api/users/wechat-bind-current', {
        method: 'POST', data: { code }, header: { 'content-type': 'application/json' },
      })
      if (binding?.merged) uni.showToast({ title: '已同步已有作品', icon: 'success' })
    } catch (bindingError: any) {
      // Password login remains available when a browser does not implement
      // uni.login. On a mini-program device, surface a short actionable notice.
      const message = String(bindingError?.message || '')
      if (message && !/当前运行在小程序中/.test(message)) {
        uni.showToast({ title: message.includes('已绑定其他创作账号') ? '微信已绑定其他账号' : '作品同步将在下次登录完成', icon: 'none' })
      }
    }
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
  if (!wechatTermsAccepted.value) {
    uni.showToast({ title: '请先同意用户协议与隐私协议', icon: 'none' })
    return
  }
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
  if (campaign.key.startsWith('design-')) {
    uni.showToast({ title: '征集活动数据加载中', icon: 'none' })
    return
  }
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

function safeMiniProgramRoute(value: unknown) {
  const raw = String(value || '').trim()
  if (!raw) return ''
  let route = raw
  try { route = decodeURIComponent(raw) } catch { return '' }
  return route.startsWith('/pages/') ? route : ''
}

onLoad((query: Record<string, string> = {}) => {
  fromWebview.value = query.from === 'webview'
  miniWebLoginSession.value = query.miniWebLoginSession || ''
  redirectUrl.value = safeMiniProgramRoute(query.redirect)
  readPendingCampaign()
  void loadCampaigns()
  // The web-view login button already represents an explicit user action.
  // Continue that action automatically after the native page is ready.
  if (fromWebview.value || miniWebLoginSession.value) setTimeout(() => void wechatLogin(), 80)
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;padding:38rpx 32rpx 70rpx;background:#fff;color:#2c2c2c;box-sizing:border-box}.mini-top{display:flex;align-items:center;height:58rpx;color:#252525}.back{font-size:57rpx;font-weight:200;line-height:1}.mini-dots{margin-left:auto;padding:11rpx 24rpx;border:1rpx solid #e6e6e6;border-radius:99rpx;font-size:28rpx;letter-spacing:6rpx;line-height:1}.mini-circle{padding:7rpx 13rpx;border:1rpx solid #e6e6e6;border-left:0;border-radius:0 99rpx 99rpx 0;font-size:31rpx;line-height:1}.brand-lockup{display:flex;align-items:center;flex-direction:column;margin:76rpx 0 62rpx}.brand-seal{display:grid;place-items:center;width:124rpx;height:124rpx;border-radius:42rpx;background:linear-gradient(145deg,#13d7bb,#00a994);color:#fff;font-family:"Songti SC","STSong",serif;font-size:75rpx;font-weight:800}.brand-name{margin-top:28rpx;color:#292929;font-family:"Songti SC","STSong",serif;font-size:55rpx;font-weight:800;letter-spacing:4rpx}.brand-en{margin-top:3rpx;color:#292929;font-size:23rpx;font-weight:700;letter-spacing:3rpx}.brand-slogan{margin-top:32rpx;color:#c5c5c5;font-family:"Songti SC","STSong",serif;font-size:32rpx;letter-spacing:6rpx}.card{position:relative;border:0;border-radius:28rpx;background:#fff;padding:0 24rpx;box-shadow:none}.wechat-button,.phone-button,.primary{width:100%;height:88rpx;line-height:88rpx;margin:0 0 22rpx;border-radius:50rpx;font-size:28rpx;font-weight:700}.wechat-button{background:#08c768;color:#fff}.phone-button{background:#d9d9d9;color:#fff}.wechat-mark,.phone-mark{margin-right:17rpx;font-size:30rpx}.phone-mark{font-size:31rpx}.agreement{display:flex;align-items:center;justify-content:center;gap:12rpx;margin:5rpx 0 24rpx;color:#343434;font-size:20rpx}.checkbox{display:grid;place-items:center;width:32rpx;height:32rpx;border:2rpx solid #aaa;border-radius:8rpx;color:#08b67e;font-size:24rpx}.password-link{display:block;margin:0 auto 14rpx;color:#999;text-align:center;font-size:20rpx}.password-login{margin-top:12rpx}.input{box-sizing:border-box;width:100%;height:82rpx;margin-bottom:14rpx;padding:0 24rpx;border:1rpx solid #e1e5e3;border-radius:42rpx;background:#f7f9f8;font-size:24rpx}.primary{background:#12c97a;color:#fff}.register-row{display:flex;justify-content:center;gap:8rpx;margin-top:24rpx;color:#a0a0a0;font-size:21rpx}.register-row text:last-child{color:#00ad83;font-weight:800}.skip-login{display:block;margin:25rpx auto 0;padding:14rpx;color:#00ad83;text-align:center;font-size:22rpx}.wechat-status{display:block;color:#688;text-align:center;font-size:20rpx}.wechat-phone-auth{margin-top:22rpx;padding:22rpx;border:1rpx solid #d9eee7;border-radius:18rpx;background:#f2fcf8}.profile-title{display:block;color:#0c9c77;font-size:24rpx;font-weight:800}.phone-copy{display:block;color:#718078;font-size:21rpx;line-height:1.55}.consent-row{display:flex;align-items:flex-start;gap:10rpx;margin-top:14rpx;color:#6f756d;font-size:20rpx;line-height:1.45}.check{display:grid;place-items:center;flex:none;width:28rpx;height:28rpx;border:1rpx solid #bfcfc0;border-radius:7rpx;background:#fff;color:#4f8364;font-weight:900}.phone-auth-button{height:84rpx;line-height:84rpx;margin-top:18rpx;border-radius:14rpx;background:#0ac77d;color:#fff;font-size:26rpx;font-weight:800}.campaign-board{position:relative;z-index:1;margin-top:44rpx;padding:0;border:0;border-radius:0;background:#fff;box-shadow:none}.campaign-board-head{display:flex;align-items:flex-end;justify-content:space-between;gap:12rpx}.campaign-board-head view{display:flex;flex-direction:column;gap:5rpx}.campaign-kicker{color:#333;font-size:27rpx;font-weight:800}.campaign-title{display:none}.campaign-board-head>text{border:0;background:none;color:#888;font-size:19rpx}.campaign-scroll{width:calc(100% + 12rpx);margin:18rpx -6rpx 0;white-space:nowrap}.campaign-row{display:flex;gap:13rpx;padding:1rpx 6rpx 8rpx}.campaign-card{display:flex;flex:0 0 100%;min-height:150rpx;box-sizing:border-box;flex-direction:row;align-items:center;padding:12rpx;border:1rpx solid #e4f0ec;border-left:9rpx solid #06bf9d;border-radius:22rpx;background:#fff;white-space:normal;box-shadow:0 7rpx 22rpx rgba(14,160,129,.11)}.campaign-card.selected{border-color:#a3e8d7;background:#f5fffc}.campaign-art{display:grid;place-items:center;width:150rpx;height:130rpx;flex:none;border-radius:18rpx;background:#e0f4ed;color:#168c70;font-family:"Songti SC","STSong",serif;font-size:62rpx}.campaign-info{display:flex;min-width:0;flex:1;margin-left:20rpx;flex-direction:column;gap:10rpx}.campaign-target{overflow:hidden;color:#2c3430;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.campaign-style{color:#9a9f9c;font-size:19rpx}.campaign-products{color:#9a9f9c;font-size:18rpx}.campaign-points{padding:10rpx 14rpx;border-radius:30rpx;background:#08c7a0;color:#fff;font-size:22rpx;font-weight:800}.campaign-loading{display:flex;align-items:center;justify-content:center;min-height:150rpx;border:1rpx dashed #d3e9e0;border-radius:20rpx;color:#8aa89d;font-size:19rpx}.campaign-notice{display:none}
</style>
