<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" aria-label="返回" @tap="goBack">‹</view>
      <text class="nav-title">渠道合作</text>
      <view class="nav-menu" aria-label="更多操作"><text>•••</text><view /><text>◎</text></view>
    </view>

    <view class="content">
      <view class="hero">
        <view class="hero-art"><image src="/static/ui-design/channel-cooperation.jpg" mode="aspectFill" /></view>
        <text class="hero-title">渠道合作</text>
        <text class="hero-subtitle">让成熟创意，走向产品落地</text>
        <text class="hero-description">上传已有作品，平台审核确认可落地后，</text>
        <text class="hero-description">提报博物馆审核；</text>
        <text class="hero-description">通过后打样、上架渠道售卖。</text>
      </view>

      <view class="steps" aria-label="渠道合作流程">
        <view v-for="(step, index) in steps" :key="step.title" class="step-wrap">
          <view class="step"><view class="step-icon">{{ step.icon }}</view><text>{{ step.title }}</text></view>
          <view v-if="index < steps.length - 1" class="step-line" />
        </view>
      </view>

      <view class="action-area">
        <button class="primary-action" @tap="chooseZip">确认上传</button>
        <button class="secondary-action" @tap="goBack">再逛逛</button>
        <view class="upload-note"><text>仅支持ZIP格式 · 不超过100MB</text><text @tap="showRequirements">查看作品包要求</text></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { requireSession } from '../../utils/session'

const PENDING_PRODUCT_PACKAGE_KEY = 'pending_product_package'
const MAX_ZIP_BYTES = 100 * 1024 * 1024
const steps = [
  { title: '上传作品', icon: '↑' },
  { title: '平台审核', icon: '▣' },
  { title: '博物馆审核', icon: '♜' },
  { title: '打样', icon: '▤' },
  { title: '上架销售', icon: '↥' },
]

function goBack() {
  if (getCurrentPages().length > 1) { uni.navigateBack(); return }
  uni.reLaunch({ url: '/pages/home/index' })
}

function showRequirements() {
  uni.showModal({
    title: '作品包要求',
    content: '请上传一个不超过100MB的ZIP文件。建议包含正面、侧面、背面三视图，以及尺寸、材质、工艺、版权来源等说明；请勿直接上传文件夹。',
    showCancel: false,
    confirmText: '我知道了',
  })
}

function chooseZip() {
  if (!requireSession()) return
  const chooser = (uni as any).chooseMessageFile
  if (typeof chooser !== 'function') {
    uni.showToast({ title: '当前版本暂不支持选择ZIP文件，请更新微信后重试', icon: 'none' })
    return
  }
  chooser({ count: 1, type: 'file', extension: ['zip'], success: (result: any) => {
    const file = result?.tempFiles?.[0]
    const path = String(file?.path || '').trim()
    const name = String(file?.name || path).trim()
    const size = Number(file?.size || 0)
    if (!path || !/\.zip$/i.test(name)) {
      uni.showToast({ title: '请选择ZIP格式的作品包', icon: 'none' })
      return
    }
    if (size > MAX_ZIP_BYTES) {
      uni.showToast({ title: 'ZIP作品包不能超过100MB', icon: 'none' })
      return
    }
    uni.setStorageSync(PENDING_PRODUCT_PACKAGE_KEY, {
      path,
      name,
      size,
      userName: String((uni.getStorageSync('smart_pig_auth') || {}).user?.username || '').trim(),
      selectedAt: Date.now(),
    })
    uni.navigateTo({ url: '/pages/professional/index?entry=channel' })
  }, fail: (error: any) => {
    if (!/cancel/i.test(String(error?.errMsg || ''))) uni.showToast({ title: '文件选择失败，请重新选择', icon: 'none' })
  } })
}
</script>

<style scoped lang="scss">
.page { min-height: 100vh; background: linear-gradient(180deg,#fff 0%,#fff 58%,#f4fbfa 100%); color: #111; }
.nav-bar { position: fixed; z-index: 10; top: 0; right: 0; left: 0; display: grid; grid-template-columns: 150rpx minmax(0,1fr) 190rpx; align-items: end; height: calc(126rpx + env(safe-area-inset-top)); box-sizing: border-box; padding: calc(36rpx + env(safe-area-inset-top)) 26rpx 18rpx; background: rgba(255,255,255,.97); }
.nav-title { overflow: hidden; font-size: 40rpx; font-weight: 700; line-height: 58rpx; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
.nav-back { justify-self: start; width: 64rpx; height: 58rpx; color: #202624; font-size: 64rpx; font-weight: 300; line-height: 48rpx; }
.nav-menu { display: flex; align-items: center; justify-content: space-around; justify-self: end; width: 172rpx; height: 62rpx; box-sizing: border-box; padding: 0 20rpx; border: 1rpx solid rgba(151,151,151,.28); border-radius: 34rpx; background: #fff; font-size: 28rpx; }
.nav-menu view { width: 1rpx; height: 37rpx; background: rgba(151,151,151,.35); }.nav-menu text:last-child { font-size: 39rpx; line-height: 1; }
.content { display: flex; min-height: 100vh; box-sizing: border-box; flex-direction: column; padding: calc(175rpx + env(safe-area-inset-top)) 38rpx calc(64rpx + env(safe-area-inset-bottom)); }
.hero { display: flex; align-items: center; flex-direction: column; text-align: center; }.hero-art { width: 166rpx; height: 182rpx; overflow: hidden; border-radius: 22rpx; background: #f7f8f8; }.hero-art image { display: block; width: 100%; height: 100%; }
.hero-title { margin-top: 16rpx; color: rgba(0,0,0,.8); font-size: 48rpx; font-weight: 700; line-height: 62rpx; }.hero-subtitle { margin: 6rpx 0 20rpx; color: rgba(0,0,0,.45); font-size: 31rpx; font-weight: 300; line-height: 48rpx; }.hero-description { color: rgba(0,0,0,.8); font-size: 27rpx; font-weight: 600; line-height: 46rpx; }
.steps { display: flex; align-items: flex-start; justify-content: center; margin: 74rpx 0 0; }.step-wrap { display: flex; align-items: center; flex: 1; }.step { display: flex; min-width: 82rpx; align-items: center; flex-direction: column; color: #55cbb1; }.step-icon { display: grid; place-items: center; width: 70rpx; height: 70rpx; border-radius: 50%; background: #5bd1b6; color: #fff; font-size: 40rpx; font-weight: 700; line-height: 1; }.step text { margin-top: 8rpx; font-size: 18rpx; white-space: nowrap; }.step-line { height: 3rpx; flex: 1; margin: 0 5rpx 32rpx; background: #9caaa7; }
.action-area { display: flex; margin-top: auto; padding: 148rpx 44rpx 0; flex-direction: column; }.primary-action,.secondary-action { width: 100%; height: 92rpx; margin: 0; border-radius: 46rpx; font-size: 36rpx; font-weight: 500; line-height: 92rpx; }.primary-action { background: linear-gradient(148deg,#69ebc7,#49a59c); color: #fff; }.secondary-action { margin-top: 28rpx; border: 2rpx solid #43aca1; background: #fff; color: #43aca1; }.primary-action::after,.secondary-action::after { border: 0; }
.upload-note { display: flex; align-items: center; margin-top: 88rpx; flex-direction: column; color: #333; font-size: 24rpx; }.upload-note text:last-child { margin-top: 17rpx; color: #555; text-decoration: underline; }
@media (max-width: 360px) { .nav-bar { grid-template-columns: 110rpx minmax(0,1fr) 165rpx; padding-right: 18rpx; padding-left: 18rpx; }.nav-menu { width: 152rpx; }.content { padding-right: 28rpx; padding-left: 28rpx; }.steps { margin-right: -10rpx; margin-left: -10rpx; }.step-icon { width: 62rpx; height: 62rpx; }.step text { font-size: 16rpx; } }
</style>
