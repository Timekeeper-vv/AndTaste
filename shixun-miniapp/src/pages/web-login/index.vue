<template>
  <view class="page">
    <view class="panel">
      <text class="eyebrow">WECHAT WEB LOGIN</text>
      <text class="title">确认登录网页端</text>
      <text class="copy">确认后，当前网页会自动进入你的创作空间。</text>
      <button class="confirm" :loading="loading" @tap="confirmLogin">确认微信登录</button>
      <text v-if="message" class="message">{{ message }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { ApiError, request } from '../../api/client'
import { saveSession } from '../../utils/session'

const sessionId = ref('')
const loading = ref(false)
const message = ref('')

function loginCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: result => result.code ? resolve(result.code) : reject(new Error('微信登录凭证获取失败')),
      fail: () => reject(new Error('微信登录失败，请检查网络后重试')),
    })
  })
}

async function confirmLogin() {
  if (!sessionId.value || loading.value) return
  loading.value = true
  message.value = ''
  try {
    const code = await loginCode()
    const result = await request<any>('/api/users/wechat-mini-web/confirm', {
      method: 'POST',
      data: { sessionId: sessionId.value, code },
      header: { 'content-type': 'application/json', Authorization: '' },
    })
    if (result?.token && result?.user) saveSession(result)
    uni.showToast({ title: '网页已登录', icon: 'success' })
    setTimeout(() => uni.reLaunch({ url: '/pages/webview/index' }), 700)
  } catch (error: any) {
    if (error instanceof ApiError && error.code === 'WECHAT_PROFILE_REQUIRED') {
      uni.redirectTo({ url: `/pages/login/index?miniWebLoginSession=${encodeURIComponent(sessionId.value)}` })
      return
    }
    message.value = error?.message || '确认失败，请重新扫码'
  } finally {
    loading.value = false
  }
}

onLoad((query: Record<string, string> = {}) => {
  try { sessionId.value = decodeURIComponent(query.scene || query.sessionId || '') } catch { sessionId.value = '' }
  if (!sessionId.value) message.value = '登录码已失效，请回到网页重新扫码'
})
</script>

<style scoped lang="scss">
.page{display:grid;place-items:center;min-height:100vh;padding:48rpx;background:#f4f7f1;box-sizing:border-box}.panel{display:flex;flex-direction:column;width:100%;max-width:590rpx;padding:56rpx 42rpx;border:1rpx solid #d4e1d4;border-radius:18rpx;background:#fff;box-shadow:0 18rpx 44rpx rgba(42,71,48,.1);box-sizing:border-box}.eyebrow{color:#5c8868;font-size:20rpx;font-weight:800}.title{margin-top:18rpx;color:#25342a;font-size:46rpx;font-weight:800}.copy{margin-top:16rpx;color:#748076;font-size:26rpx;line-height:1.65}.confirm{height:92rpx;line-height:92rpx;margin-top:46rpx;border-radius:12rpx;background:#44765a;color:#fff;font-size:29rpx;font-weight:800}.message{margin-top:20rpx;color:#a24b4b;font-size:24rpx;line-height:1.55;text-align:center}
</style>
