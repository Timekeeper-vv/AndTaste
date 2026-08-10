<template>
  <view v-if="privacyVisible" class="privacy-mask">
    <view class="privacy-dialog">
      <text class="privacy-title">请先阅读隐私保护指引</text>
      <text class="privacy-copy">为了完成手机号快捷登录、账号安全和订单服务，我们会在你同意后处理必要的登录凭证和你主动授权的手机号。SessionKey 只在服务端使用，不会返回给小程序。</text>
      <text class="privacy-link" @tap="openPrivacyContract">查看隐私保护指引</text>
      <view class="privacy-actions">
        <button class="privacy-secondary" @tap="rejectPrivacy">暂不使用</button>
        <button class="privacy-primary" @tap="agreePrivacy">同意并继续</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { restoreSession } from './utils/session'

const privacyVisible = ref(false)
const privacyResolver = ref<((result: { event: 'agree' | 'disagree' }) => void) | null>(null)

function openPrivacyContract() {
  const wxApi = (globalThis as any).wx
  if (typeof wxApi?.openPrivacyContract === 'function') {
    wxApi.openPrivacyContract({ fail: () => uni.showToast({ title: '隐私协议暂时无法打开', icon: 'none' }) })
    return
  }
  uni.showToast({ title: '请在小程序中查看', icon: 'none' })
}

function agreePrivacy() {
  privacyVisible.value = false
  const resolve = privacyResolver.value
  privacyResolver.value = null
  resolve?.({ event: 'agree' })
}

function rejectPrivacy() {
  privacyVisible.value = false
  const resolve = privacyResolver.value
  privacyResolver.value = null
  resolve?.({ event: 'disagree' })
  uni.showToast({ title: '同意隐私指引后才能使用手机号快捷登录', icon: 'none' })
}

onLaunch(() => {
  // WeChat calls this resolver when a protected API needs user privacy
  // authorization. Keep the decision in the official callback and never
  // call getPhoneNumber before the user has agreed.
  // #ifdef MP-WEIXIN
  const wxApi = (globalThis as any).wx
  if (typeof wxApi?.onNeedPrivacyAuthorization === 'function') {
    wxApi.onNeedPrivacyAuthorization((resolve: (result: { event: 'agree' | 'disagree' }) => void) => {
      privacyResolver.value = resolve
      privacyVisible.value = true
    })
  }
  // #endif
  // Visitors must be able to explore the home page before deciding whether to
  // log in. Privacy authorization is requested only by a protected action,
  // such as the user actively choosing phone-number quick login.
  restoreSession()
    .finally(() => setTimeout(() => uni.reLaunch({ url: '/pages/home/index' }), 0))
})
</script>

<style lang="scss">
page {
  background: #f7f3ed;
  color: #292622;
  font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif;
}
button::after { border: none; }
.privacy-mask { position: fixed; inset: 0; z-index: 9999; display: flex; align-items: center; justify-content: center; padding: 40rpx; background: rgba(35, 29, 24, .58); }
.privacy-dialog { width: 100%; max-width: 620rpx; box-sizing: border-box; padding: 38rpx 32rpx 28rpx; border-radius: 22rpx; background: #fffdf8; box-shadow: 0 18rpx 60rpx rgba(35, 29, 24, .22); }
.privacy-title { display: block; color: #302c27; font-size: 34rpx; font-weight: 800; }
.privacy-copy { display: block; margin-top: 20rpx; color: #6f675d; font-size: 25rpx; line-height: 1.7; }
.privacy-link { display: block; margin-top: 18rpx; color: #4c8065; font-size: 24rpx; font-weight: 700; }
.privacy-actions { display: flex; gap: 18rpx; margin-top: 30rpx; }
.privacy-actions button { flex: 1; height: 78rpx; line-height: 78rpx; border-radius: 14rpx; font-size: 25rpx; font-weight: 700; }
.privacy-secondary { color: #6f675d; background: #f2eee7; }
.privacy-primary { color: #fff; background: #4c8065; }
</style>
