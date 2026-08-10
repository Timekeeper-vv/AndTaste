<template>
  <view class="empty">
    <text class="cube">3D</text>
    <text class="title">请使用电脑端查看</text>
    <text class="desc">小程序端暂不支持 3D 模型预览和材质编辑，请在电脑浏览器打开：</text>
    <text class="url">https://www.zhijiansk.com/</text>
    <button class="copy" @tap="copyDesktopUrl">复制电脑端网址</button>
    <button class="back" @tap="back">返回作品页</button>
  </view>
</template>

<script setup lang="ts">
const desktopUrl = 'https://www.zhijiansk.com/'

function copyDesktopUrl() {
  uni.setClipboardData({
    data: desktopUrl,
    success: () => uni.showToast({ title: '电脑端网址已复制', icon: 'success' }),
  })
}

function back() {
  uni.navigateBack()
}
</script>

<style scoped lang="scss">
.empty{min-height:100vh;padding:150rpx 55rpx;text-align:center;box-sizing:border-box;background:linear-gradient(180deg,#faf8f3,#f0e9df)}.cube{display:grid;place-items:center;width:180rpx;height:180rpx;margin:0 auto 42rpx;border-radius:28rpx;background:linear-gradient(145deg,#5b7b6d,#9fb7a9);box-shadow:20rpx 20rpx 0 #dde7dd;color:#fff;font-size:50rpx;font-weight:800}.title{display:block;color:#302b26;font-family:"Songti SC","STSong",serif;font-size:42rpx;font-weight:800}.desc{display:block;margin-top:22rpx;color:#81776c;font-size:26rpx;line-height:1.7}.url{display:block;margin-top:18rpx;padding:18rpx 12rpx;border:1rpx solid #c8d9cc;border-radius:14rpx;background:#f4f8f1;color:#557565;font-size:24rpx;word-break:break-all}.copy,.back{width:100%;height:86rpx;line-height:86rpx;border-radius:17rpx;font-size:27rpx}.copy{margin-top:28rpx;background:linear-gradient(135deg,#3e3933,#617e71);color:#fff}.back{margin-top:14rpx;background:#eee8df;color:#74685e}
</style>
