<template>
  <web-view v-if="source" :src="source" />
  <view v-else class="empty">
    <text class="title">无法打开 3D 预览</text>
    <text class="desc">预览链接不存在或已失效，请返回作品页后重新打开。</text>
    <button class="back" @tap="back">返回作品页</button>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const source = ref('')

onLoad((query: any) => {
  const candidate = String(uni.getStorageSync('smart_pig_model_preview_url') || '')
  // 链接只需交给当前 web-view 一次，避免短期媒体令牌长期保存在小程序本地存储。
  uni.removeStorageSync('smart_pig_model_preview_url')
  // 微信 web-view 只允许已配置的 HTTPS 业务域名；本地/HTTP 地址直接走可理解的降级页。
  source.value = /^https:\/\//.test(candidate) ? candidate : ''
})

function back() {
  uni.navigateBack()
}
</script>

<style scoped lang="scss">
.empty{min-height:100vh;padding:180rpx 60rpx;text-align:center;box-sizing:border-box}.title{display:block;font-size:42rpx;font-weight:800}.desc{display:block;font-size:26rpx;line-height:1.7;color:#8c7164;margin-top:22rpx}.back{height:90rpx;line-height:90rpx;margin-top:44rpx;border-radius:45rpx;background:#963c23;color:#fff;font-size:28rpx}
</style>
