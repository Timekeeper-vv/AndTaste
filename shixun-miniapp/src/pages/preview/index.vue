<template>
  <view class="page">
    <view class="cube">3D</view>
    <text class="title">{{ title }}</text>
    <text class="description">小程序端暂不支持 3D 模型预览，请在电脑浏览器打开以下地址查看和编辑模型。</text>
    <view class="desktop-url">https://www.zhijiansk.com/</view>
    <button class="open" @tap="copyDesktopUrl">复制电脑端网址</button>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

const title = ref('3D 模型')
const desktopUrl = 'https://www.zhijiansk.com/'

function safelyDecode(value: unknown) {
  try { return decodeURIComponent(String(value || '')) } catch { return String(value || '') }
}

function copyDesktopUrl() {
  uni.setClipboardData({
    data: desktopUrl,
    success: () => uni.showToast({ title: '电脑端网址已复制', icon: 'success' }),
  })
}

onLoad((query: any) => {
  title.value = safelyDecode(query?.title || '3D 模型')
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:130rpx 58rpx;text-align:center;box-sizing:border-box}.cube{width:210rpx;height:210rpx;margin:0 auto 48rpx;border-radius:32rpx;background:linear-gradient(145deg,#3a1d15,#b75632);color:#fff;display:flex;align-items:center;justify-content:center;font-size:56rpx;font-weight:800;box-shadow:26rpx 26rpx 0 #f0c5a6}.title{font-size:42rpx;font-weight:800;display:block}.description{display:block;color:#866c60;line-height:1.8;font-size:27rpx;margin:26rpx 0}.desktop-url{padding:22rpx 16rpx;border:1rpx solid #d8c9bb;border-radius:16rpx;background:#fffaf4;color:#88644f;font-size:25rpx;word-break:break-all}.open{height:94rpx;line-height:94rpx;margin-top:28rpx;border-radius:17rpx;background:#953d24;color:#fff;font-size:29rpx}
</style>

<style scoped lang="scss">
.page{background:radial-gradient(ellipse at 10% 0%,rgba(151,177,163,.18),transparent 29%),linear-gradient(180deg,#faf8f3,#f0e9df)}.cube{background:linear-gradient(145deg,#5b7b6d,#9fb7a9);box-shadow:26rpx 26rpx 0 #dde7dd}.title{font-family:"Songti SC","STSong",serif;color:#302b26}.description{color:#81776c}.desktop-url{border-color:#c8d9cc;background:#f4f8f1;color:#557565}.open{background:linear-gradient(135deg,#3e3933,#617e71)}
</style>
