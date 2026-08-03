<template>
  <view class="page">
    <view class="hero"><text class="eyebrow">AI CULTURAL CREATION</text><text class="title">之间智造</text><text class="sub">让文创灵感，成为可被看见的作品</text></view>
    <view class="card">
      <text class="card-title">欢迎回来</text>
      <text class="card-desc">登录后即可开始 AI 文创创作</text>
      <input v-model.trim="username" class="input" placeholder="用户名" placeholder-class="placeholder" />
      <input v-model="password" class="input" password placeholder="密码" placeholder-class="placeholder" />
      <button class="primary" :loading="loading" @tap="login">登录并开始创作</button>
      <view class="register-row"><text>还没有账号？</text><text @tap="goRegister">创建创作账号 ›</text></view>
      <text class="hint">支持使用平台账号登录。注册时会完成必要的合规确认；微信一键登录将在后续版本开放。</text>
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
function goRegister() { uni.navigateTo({ url: '/pages/register/index' }) }
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;padding:132rpx 48rpx 60rpx;background:radial-gradient(ellipse at 10% 4%,rgba(143,165,154,.24),transparent 29%),radial-gradient(circle at 90% 19%,rgba(185,102,79,.12),transparent 23%),linear-gradient(180deg,#faf8f3,#f1ebe2);box-sizing:border-box}.page::before{content:"";position:absolute;right:-90rpx;top:78rpx;width:320rpx;height:104rpx;border-radius:50%;background:rgba(102,132,118,.1);filter:blur(17rpx);transform:rotate(-14deg)}.hero,.card{position:relative;z-index:1}.hero{display:flex;flex-direction:column;margin-bottom:61rpx}.eyebrow{font-size:17rpx;letter-spacing:4rpx;color:#5f7d70;font-weight:800}.title{margin-top:20rpx;color:#2e2a25;font-family:"Songti SC","STSong",serif;font-size:76rpx;font-weight:700;letter-spacing:5rpx;line-height:1.2}.sub{margin-top:17rpx;color:#776e64;font-family:"Songti SC","STSong",serif;font-size:29rpx;line-height:1.7}.card{border:1rpx solid rgba(120,103,84,.14);border-radius:32rpx;background:rgba(255,253,249,.88);padding:43rpx 35rpx;box-shadow:0 22rpx 52rpx rgba(76,59,41,.10)}.card-title{display:block;color:#37312b;font-family:"Songti SC","STSong",serif;font-size:42rpx;font-weight:700}.card-desc{display:block;margin:12rpx 0 34rpx;color:#8a8075;font-size:24rpx}.input{box-sizing:border-box;width:100%;height:92rpx;margin-bottom:18rpx;padding:0 24rpx;border:1rpx solid #e4dcd1;border-radius:15rpx;background:#fbf9f4;color:#403a34;font-size:28rpx}.placeholder{color:#b5aa9e}.primary{height:94rpx;line-height:94rpx;margin-top:13rpx;border-radius:17rpx;background:linear-gradient(135deg,#3d3933,#627f72);color:#fff;font-size:28rpx;font-weight:800;box-shadow:0 12rpx 22rpx rgba(52,58,52,.16)}.register-row{display:flex;justify-content:center;gap:9rpx;margin-top:22rpx;color:#9c9185;font-size:21rpx}.register-row text:last-child{color:#5d7d6e;font-weight:850}.hint{display:block;margin-top:17rpx;color:#a09387;text-align:center;font-size:20rpx;line-height:1.65}
</style>
