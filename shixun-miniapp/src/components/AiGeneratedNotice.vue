<template>
  <view class="ai-notice" :class="{ compact }">
    <view class="notice-head"><text class="badge">AI生成</text><text class="title">{{ title }}</text></view>
    <text class="copy">{{ description }}</text>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  title?: string
  description?: string
  compact?: boolean
}>(), {
  title: '人工智能生成内容',
  description: '本页面包含人工智能生成或分析内容，结果仅供参考，不替代人工审核、版权核验、报价、质检或生产确认。',
  compact: false,
})
</script>

<style scoped lang="scss">
.ai-notice{display:flex;flex-direction:column;gap:8rpx;padding:16rpx 18rpx;border:1rpx solid #d4b993;border-radius:8rpx;background:#fff8ec;color:#725e49}.notice-head{display:flex;align-items:center;gap:9rpx}.badge{padding:4rpx 8rpx;border-radius:6rpx;background:#9a624b;color:#fff;font-size:15rpx;font-weight:900;line-height:1.2}.title{color:#594737;font-size:18rpx;font-weight:850}.copy{color:#806e5c;font-size:15rpx;line-height:1.55}.compact{padding:12rpx 14rpx;gap:6rpx}.compact .title{font-size:16rpx}.compact .copy{font-size:13rpx}
</style>
