<template>
  <view class="page">
    <view class="hero"><text class="eyebrow">AI CULTURAL CREATION</text><text class="title">之间智造</text><text class="sub">让文创灵感，成为可被看见的作品</text></view>
    <view class="card">
      <text class="card-title">欢迎回来</text>
      <text class="card-desc">登录后即可开始 AI 文创创作</text>
      <input v-model.trim="username" class="input" placeholder="用户名" placeholder-class="placeholder" />
      <input v-model="password" class="input" password placeholder="密码" placeholder-class="placeholder" />
      <button class="primary" :loading="loading" @tap="login">登录并开始创作</button>
      <button class="wechat-button" :loading="wechatLoading" :disabled="wechatLoading || wechatPhoneRequired" @tap="wechatLogin">微信登录</button>
      <text v-if="fromWebview && wechatLoading" class="wechat-status">正在使用当前微信账号登录...</text>
      <view v-if="wechatPhoneRequired" class="wechat-phone-auth">
        <text class="profile-title">微信账号已识别</text>
        <text class="phone-copy">首次登录需要绑定当前微信的手机号，请点击下方按钮完成微信官方授权。</text>
        <view class="consent-row" @tap="wechatTermsAccepted = !wechatTermsAccepted">
          <text class="check">{{ wechatTermsAccepted ? '✓' : '' }}</text><text>我已阅读并同意用户服务、隐私说明与内容规范，并确认后续合作按要求完成实名认证</text>
        </view>
        <button class="phone-auth-button" open-type="getPhoneNumber" :loading="wechatLoading" :disabled="wechatLoading || !wechatTermsAccepted" @getphonenumber="authorizeWechatPhone">授权微信手机号并登录</button>
      </view>
      <view class="register-row"><text>还没有账号？</text><text @tap="goRegister">创建创作账号 ›</text></view>
      <text class="hint">支持平台账号登录。微信登录首次使用时需要补充必要资料并完成合规确认。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { ApiError, request } from '../../api/client'
import { saveSession } from '../../utils/session'
const username = ref('')
const password = ref('')
const loading = ref(false)
const fromWebview = ref(false)
const fromMiniapp = ref(false)
const miniWebLoginSession = ref('')
const wechatLoading = ref(false)
const wechatPhoneRequired = ref(false)
const wechatTermsAccepted = ref(false)

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
  } else if (fromMiniapp.value) {
    uni.reLaunch({ url: '/pages/webview/index' })
  } else {
    uni.reLaunch({ url: '/pages/purpose/index' })
  }
}

async function login() {
  if (!username.value || !password.value) return uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
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
      success: result => result.code ? resolve(result.code) : reject(new Error('微信登录凭证获取失败')),
      fail: () => reject(new Error('微信登录失败，请确认当前运行在微信小程序中')),
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
      uni.showToast({ title: error?.message || '微信登录失败', icon: 'none' })
    }
  } finally { wechatLoading.value = false }
}

async function authorizeWechatPhone(event: any) {
  const phoneCode = String(event?.detail?.code || '').trim()
  const errorMessage = String(event?.detail?.errMsg || '').trim()
  if (!phoneCode) {
    const isDevtools = /devtools|simulator|mock/i.test(errorMessage)
    uni.showModal({
      title: isDevtools ? '请使用真机授权' : '手机号授权未完成',
      content: isDevtools
        ? '微信开发者工具模拟器不支持真实手机号授权。请点击工具栏“预览”，用真实微信扫码打开小程序后再授权。'
        : '请在微信官方弹窗中选择“允许”。如果没有弹窗，请退出小程序后重新进入再试。',
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

onLoad((query: Record<string, string> = {}) => {
  fromWebview.value = query.from === 'webview'
  fromMiniapp.value = query.from === 'miniapp'
  miniWebLoginSession.value = query.miniWebLoginSession || ''
  // The web-view login button already represents an explicit user action.
  // Continue that action automatically after the native page is ready.
  if (fromWebview.value || fromMiniapp.value || miniWebLoginSession.value) setTimeout(() => void wechatLogin(), 80)
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;padding:132rpx 48rpx 60rpx;background:radial-gradient(ellipse at 10% 4%,rgba(143,165,154,.24),transparent 29%),radial-gradient(circle at 90% 19%,rgba(185,102,79,.12),transparent 23%),linear-gradient(180deg,#faf8f3,#f1ebe2);box-sizing:border-box}.page::before{content:"";position:absolute;right:-90rpx;top:78rpx;width:320rpx;height:104rpx;border-radius:50%;background:rgba(102,132,118,.1);filter:blur(17rpx);transform:rotate(-14deg)}.hero,.card{position:relative;z-index:1}.hero{display:flex;flex-direction:column;margin-bottom:61rpx}.eyebrow{font-size:17rpx;letter-spacing:4rpx;color:#5f7d70;font-weight:800}.title{margin-top:20rpx;color:#2e2a25;font-family:"Songti SC","STSong",serif;font-size:76rpx;font-weight:700;letter-spacing:5rpx;line-height:1.2}.sub{margin-top:17rpx;color:#776e64;font-family:"Songti SC","STSong",serif;font-size:29rpx;line-height:1.7}.card{border:1rpx solid rgba(120,103,84,.14);border-radius:32rpx;background:rgba(255,253,249,.88);padding:43rpx 35rpx;box-shadow:0 22rpx 52rpx rgba(76,59,41,.10)}.card-title{display:block;color:#37312b;font-family:"Songti SC","STSong",serif;font-size:42rpx;font-weight:700}.card-desc{display:block;margin:12rpx 0 34rpx;color:#8a8075;font-size:24rpx}.input{box-sizing:border-box;width:100%;height:92rpx;margin-bottom:18rpx;padding:0 24rpx;border:1rpx solid #e4dcd1;border-radius:15rpx;background:#fbf9f4;color:#403a34;font-size:28rpx}.placeholder{color:#b5aa9e}.primary{height:94rpx;line-height:94rpx;margin-top:13rpx;border-radius:17rpx;background:linear-gradient(135deg,#3d3933,#627f72);color:#fff;font-size:28rpx;font-weight:800;box-shadow:0 12rpx 22rpx rgba(52,58,52,.16)}.register-row{display:flex;justify-content:center;gap:9rpx;margin-top:22rpx;color:#9c9185;font-size:21rpx}.register-row text:last-child{color:#5d7d6e;font-weight:850}.hint{display:block;margin-top:17rpx;color:#a09387;text-align:center;font-size:20rpx;line-height:1.65}
.wechat-button{height:84rpx;line-height:84rpx;margin-top:18rpx;border:1rpx solid #6b907b;border-radius:17rpx;background:#f4faf4;color:#4d755f;font-size:27rpx;font-weight:800}.wechat-button[disabled]{color:#9aa99f;background:#eef2ee;border-color:#d7e0d8}.wechat-status{display:block;margin-top:12rpx;color:#6f8977;text-align:center;font-size:21rpx}.wechat-phone-auth{margin-top:23rpx;padding:22rpx;border:1rpx solid #d9e5d9;border-radius:18rpx;background:#f5f9f3}.profile-title{display:block;margin-bottom:5rpx;color:#4f745e;font-size:24rpx;font-weight:800}.phone-copy{display:block;color:#718078;font-size:21rpx;line-height:1.55}.consent-row{display:flex;align-items:flex-start;gap:10rpx;margin-top:14rpx;color:#6f756d;font-size:20rpx;line-height:1.45}.check{display:grid;place-items:center;flex:none;width:28rpx;height:28rpx;border:1rpx solid #bfcfc0;border-radius:7rpx;background:#fff;color:#4f8364;font-weight:900}.phone-auth-button{height:84rpx;line-height:84rpx;margin-top:18rpx;border-radius:14rpx;background:#4d8064;color:#fff;font-size:26rpx;font-weight:800}.phone-auth-button[disabled]{color:#b7c6ba;background:#cfddd1}
</style>
