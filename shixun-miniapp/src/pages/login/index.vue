<template>
  <view class="page">
    <view class="hero"><text class="eyebrow">AI CULTURAL CREATION</text><text class="title">之间智造</text><text class="sub">让文创灵感，成为可被看见的作品</text></view>
    <view class="card">
      <text class="card-title">欢迎回来</text>
      <text class="card-desc">登录后即可开始 AI 文创创作</text>
      <input v-model.trim="username" class="input" placeholder="用户名" placeholder-class="placeholder" />
      <input v-model="password" class="input" password placeholder="密码" placeholder-class="placeholder" />
      <button class="primary" :loading="loading" @tap="login">登录并开始创作</button>
      <text class="hint">使用现有平台账号登录。微信一键登录将在后续版本开放。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { request } from '../../api/client'
import { saveSession } from '../../utils/session'
const username = ref('')
const password = ref('')
const loading = ref(false)
async function login() {
  if (!username.value || !password.value) return uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
  loading.value = true
  try {
    const session = await request<any>('/api/users/login', { method: 'POST', data: { username: username.value, password: password.value }, header: { 'content-type': 'application/json' } })
    if (!session?.token || !session?.user) throw new Error('登录响应缺少令牌')
    if (session.user.role !== 'user') {
      throw new Error('该账号是管理端账号，请使用网页管理端登录')
    }
    saveSession(session)
    uni.reLaunch({ url: '/pages/purpose/index' })
  } catch (error: any) { uni.showToast({ title: error.message || '登录失败', icon: 'none' }) } finally { loading.value = false }
}
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:150rpx 48rpx 60rpx;background:radial-gradient(circle at 88% 8%,#f7d9b4 0,#fffaf5 36%,#f8eee5 100%);box-sizing:border-box}.hero{display:flex;flex-direction:column;margin-bottom:72rpx}.eyebrow{font-size:20rpx;letter-spacing:5rpx;color:#9d3f22;font-weight:700}.title{font-size:76rpx;line-height:1.2;margin-top:22rpx;font-weight:800;color:#2d1710;letter-spacing:4rpx}.sub{font-size:28rpx;color:#77584b;line-height:1.75;margin-top:18rpx}.card{background:#fff;padding:48rpx 38rpx;border-radius:34rpx;box-shadow:0 22rpx 56rpx rgba(99,46,23,.13)}.card-title{display:block;font-weight:700;font-size:42rpx}.card-desc{display:block;font-size:26rpx;color:#95796d;margin:12rpx 0 38rpx}.input{height:94rpx;background:#faf6f2;border-radius:16rpx;padding:0 28rpx;margin-bottom:22rpx;font-size:30rpx;box-sizing:border-box}.placeholder{color:#b9a79d}.primary{height:96rpx;line-height:96rpx;border-radius:48rpx;background:linear-gradient(135deg,#b95733,#7f2919);color:#fff;font-size:30rpx;margin-top:16rpx}.hint{display:block;margin-top:28rpx;color:#ae978a;text-align:center;font-size:22rpx;line-height:1.6}
</style>
