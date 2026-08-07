<template>
  <view class="page">
    <view class="hero"><text class="eyebrow">AI CULTURAL CREATION</text><text class="title">之间智造</text><text class="sub">让文创灵感，成为可被看见的作品</text></view>
    <view class="card">
      <text class="card-title">欢迎回来</text>
      <text class="card-desc">登录后即可开始 AI 文创创作</text>
      <input v-model.trim="username" class="input" placeholder="用户名" placeholder-class="placeholder" />
      <input v-model="password" class="input" password placeholder="密码" placeholder-class="placeholder" />
      <button class="primary" :loading="loading" @tap="login">登录并开始创作</button>
      <button class="wechat-button" :loading="wechatLoading" @tap="wechatLogin">微信登录</button>
      <view v-if="wechatProfileRequired" class="wechat-profile">
        <text class="profile-title">首次微信登录，请补充账号资料</text>
        <input v-model.trim="wechatForm.username" class="input" maxlength="40" placeholder="用户名" placeholder-class="placeholder" />
        <input v-model.trim="wechatForm.phone" class="input" maxlength="30" type="number" placeholder="手机号" placeholder-class="placeholder" />
        <input v-model.trim="wechatForm.age" class="input" maxlength="3" type="number" placeholder="年龄" placeholder-class="placeholder" />
        <input v-model.trim="wechatForm.email" class="input" maxlength="100" placeholder="邮箱" placeholder-class="placeholder" />
        <input v-model.trim="wechatForm.signature" class="input" maxlength="100" placeholder="合规签署名" placeholder-class="placeholder" />
        <view v-for="item in wechatConsents" :key="item.key" class="consent-row" @tap="toggleWechatConsent(item.key)">
          <text class="check">{{ wechatForm[item.key] ? '✓' : '' }}</text><text>{{ item.label }}</text>
        </view>
        <view class="consent-row" @tap="wechatForm.realNameAcknowledged = !wechatForm.realNameAcknowledged">
          <text class="check">{{ wechatForm.realNameAcknowledged ? '✓' : '' }}</text><text>我确认后续合作或生产时按要求完成实名认证</text>
        </view>
      </view>
      <view class="register-row"><text>还没有账号？</text><text @tap="goRegister">创建创作账号 ›</text></view>
      <text class="hint">支持平台账号登录。微信登录首次使用时需要补充必要资料并完成合规确认。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { ApiError, request } from '../../api/client'
import { saveSession } from '../../utils/session'
const username = ref('')
const password = ref('')
const loading = ref(false)
const fromWebview = ref(false)
const miniWebLoginSession = ref('')
const wechatLoading = ref(false)
const wechatProfileRequired = ref(false)
const wechatForm = reactive({
  username: '', phone: '', age: '', email: '', signature: '',
  agreeDisclaimer: false, agreeConfidentiality: false, agreeContentPolicy: false,
  realNameAcknowledged: false,
})
const wechatConsents = [
  { key: 'agreeDisclaimer' as const, label: '我已阅读并同意用户服务与隐私说明' },
  { key: 'agreeConfidentiality' as const, label: '我已阅读并同意保密与知识产权约定' },
  { key: 'agreeContentPolicy' as const, label: '我已阅读并同意内容创作规范' },
]

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

function validEmail(value: string) { return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) }
function validPhone(value: string) { return /^[0-9+()\-\s]{6,30}$/.test(value) }
function toggleWechatConsent(key: 'agreeDisclaimer' | 'agreeConfidentiality' | 'agreeContentPolicy') { wechatForm[key] = !wechatForm[key] }

function validateWechatProfile() {
  if (!wechatForm.username || !wechatForm.phone || !wechatForm.age || !wechatForm.email || !wechatForm.signature) throw new Error('请完整填写首次登录资料')
  const age = Number(wechatForm.age)
  if (!Number.isInteger(age) || age <= 0 || age > 120) throw new Error('请填写有效年龄')
  if (!validPhone(wechatForm.phone)) throw new Error('手机号格式不正确')
  if (!validEmail(wechatForm.email)) throw new Error('邮箱格式不正确')
  if (!wechatForm.agreeDisclaimer || !wechatForm.agreeConfidentiality || !wechatForm.agreeContentPolicy || !wechatForm.realNameAcknowledged) throw new Error('请先完成全部使用确认')
  return age
}

async function wechatLogin() {
  if (wechatLoading.value) return
  wechatLoading.value = true
  try {
    const code = await miniProgramLoginCode()
    const data: Record<string, any> = { code }
    if (miniWebLoginSession.value) data.miniWebLoginSession = miniWebLoginSession.value
    if (wechatProfileRequired.value) {
      const age = validateWechatProfile()
      Object.assign(data, wechatForm, { age })
    }
    const session = await request<any>('/api/users/wechat-login', { method: 'POST', data, header: { 'content-type': 'application/json', Authorization: '' } })
    finishLogin(session)
  } catch (error: any) {
    if (error instanceof ApiError && error.code === 'WECHAT_PROFILE_REQUIRED') {
      wechatProfileRequired.value = true
      uni.showToast({ title: '请补充资料后再次点击微信登录', icon: 'none' })
    } else {
      uni.showToast({ title: error?.message || '微信登录失败', icon: 'none' })
    }
  } finally { wechatLoading.value = false }
}

function goRegister() { uni.navigateTo({ url: '/pages/register/index' }) }

onLoad((query: Record<string, string> = {}) => {
  fromWebview.value = query.from === 'webview'
  miniWebLoginSession.value = query.miniWebLoginSession || ''
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;padding:132rpx 48rpx 60rpx;background:radial-gradient(ellipse at 10% 4%,rgba(143,165,154,.24),transparent 29%),radial-gradient(circle at 90% 19%,rgba(185,102,79,.12),transparent 23%),linear-gradient(180deg,#faf8f3,#f1ebe2);box-sizing:border-box}.page::before{content:"";position:absolute;right:-90rpx;top:78rpx;width:320rpx;height:104rpx;border-radius:50%;background:rgba(102,132,118,.1);filter:blur(17rpx);transform:rotate(-14deg)}.hero,.card{position:relative;z-index:1}.hero{display:flex;flex-direction:column;margin-bottom:61rpx}.eyebrow{font-size:17rpx;letter-spacing:4rpx;color:#5f7d70;font-weight:800}.title{margin-top:20rpx;color:#2e2a25;font-family:"Songti SC","STSong",serif;font-size:76rpx;font-weight:700;letter-spacing:5rpx;line-height:1.2}.sub{margin-top:17rpx;color:#776e64;font-family:"Songti SC","STSong",serif;font-size:29rpx;line-height:1.7}.card{border:1rpx solid rgba(120,103,84,.14);border-radius:32rpx;background:rgba(255,253,249,.88);padding:43rpx 35rpx;box-shadow:0 22rpx 52rpx rgba(76,59,41,.10)}.card-title{display:block;color:#37312b;font-family:"Songti SC","STSong",serif;font-size:42rpx;font-weight:700}.card-desc{display:block;margin:12rpx 0 34rpx;color:#8a8075;font-size:24rpx}.input{box-sizing:border-box;width:100%;height:92rpx;margin-bottom:18rpx;padding:0 24rpx;border:1rpx solid #e4dcd1;border-radius:15rpx;background:#fbf9f4;color:#403a34;font-size:28rpx}.placeholder{color:#b5aa9e}.primary{height:94rpx;line-height:94rpx;margin-top:13rpx;border-radius:17rpx;background:linear-gradient(135deg,#3d3933,#627f72);color:#fff;font-size:28rpx;font-weight:800;box-shadow:0 12rpx 22rpx rgba(52,58,52,.16)}.register-row{display:flex;justify-content:center;gap:9rpx;margin-top:22rpx;color:#9c9185;font-size:21rpx}.register-row text:last-child{color:#5d7d6e;font-weight:850}.hint{display:block;margin-top:17rpx;color:#a09387;text-align:center;font-size:20rpx;line-height:1.65}
.wechat-button{height:84rpx;line-height:84rpx;margin-top:18rpx;border:1rpx solid #6b907b;border-radius:17rpx;background:#f4faf4;color:#4d755f;font-size:27rpx;font-weight:800}.wechat-profile{margin-top:23rpx;padding:22rpx;border:1rpx solid #d9e5d9;border-radius:18rpx;background:#f5f9f3}.profile-title{display:block;margin-bottom:5rpx;color:#4f745e;font-size:24rpx;font-weight:800}.consent-row{display:flex;align-items:center;gap:10rpx;margin-top:10rpx;color:#6f756d;font-size:20rpx;line-height:1.45}.check{display:grid;place-items:center;flex:none;width:28rpx;height:28rpx;border:1rpx solid #bfcfc0;border-radius:7rpx;background:#fff;color:#4f8364;font-weight:900}
</style>
