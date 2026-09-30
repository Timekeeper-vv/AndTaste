<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" aria-label="返回" @tap="goBack">‹</view>
      <text class="nav-title">产品智造</text>
      <view class="nav-menu" aria-label="更多操作" @tap="openMenu"><text>•••</text><view /><text>◎</text></view>
    </view>

    <view class="content">
      <view class="hero">
        <view class="hero-art"><image src="/static/ui-design/product-making.jpg" mode="aspectFill" /></view>
        <text class="hero-title">产品智造</text>
        <text class="hero-subtitle">让成熟创意，走向产品落地</text>
        <text class="hero-description">上传已有三视图作品包，</text>
        <text class="hero-description">一站式完成审核、打样、生产与渠道合作。</text>
      </view>

      <view class="action-area">
        <button class="primary-action" @tap="chooseZip">确认上传</button>
        <button class="secondary-action" @tap="goBack">再逛逛</button>
        <view class="upload-note">
          <text>仅支持ZIP格式 · 不超过100MB</text>
          <text @tap="showRequirements">查看作品包要求</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { getSession, requireSession } from '../../utils/session'

const PENDING_PRODUCT_PACKAGE_KEY = 'pending_product_package'
const MAX_ZIP_BYTES = 100 * 1024 * 1024

function goBack() {
  if (getCurrentPages().length > 1) {
    uni.navigateBack()
    return
  }
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

function openSubmissions() {
  uni.navigateTo({ url: '/pages/professional/index?entry=production' })
}

function openMenu() {
  uni.showActionSheet({
    itemList: ['选择ZIP作品包', '查看提交记录', '查看作品包要求'],
    success: result => {
      if (result.tapIndex === 0) chooseZip()
      else if (result.tapIndex === 1) openSubmissions()
      else if (result.tapIndex === 2) showRequirements()
    },
  })
}

function chooseZip() {
  if (!requireSession()) return
  const chooser = (uni as any).chooseMessageFile
  if (typeof chooser !== 'function') {
    uni.showToast({ title: '当前版本暂不支持选择ZIP文件，请更新微信后重试', icon: 'none' })
    return
  }
  chooser({
    count: 1,
    type: 'file',
    extension: ['zip'],
    success: (result: any) => {
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
        userName: String(getSession()?.user?.username || '').trim(),
        selectedAt: Date.now(),
      })
      uni.navigateTo({ url: '/pages/professional/index?entry=production' })
    },
    fail: (error: any) => {
      if (!/cancel/i.test(String(error?.errMsg || ''))) {
        uni.showToast({ title: '文件选择失败，请重新选择', icon: 'none' })
      }
    },
  })
}

onMounted(() => { requireSession() })
</script>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  box-sizing: border-box;
  overflow: hidden;
  background: #fff;
  color: rgba(0, 0, 0, .8);
}
.nav-bar {
  position: fixed;
  z-index: 10;
  top: 0;
  right: 0;
  left: 0;
  display: grid;
  grid-template-columns: 150rpx minmax(0, 1fr) 190rpx;
  align-items: end;
  height: calc(126rpx + env(safe-area-inset-top));
  box-sizing: border-box;
  padding: calc(36rpx + env(safe-area-inset-top)) 26rpx 18rpx;
  background: rgba(255, 255, 255, .97);
}
.nav-title {
  overflow: hidden;
  color: #070a09;
  font-size: 40rpx;
  font-weight: 700;
  line-height: 58rpx;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-back {
  justify-self: start;
  width: 64rpx;
  height: 58rpx;
  color: #202624;
  font-size: 64rpx;
  font-weight: 300;
  line-height: 48rpx;
}
.nav-menu {
  display: flex;
  align-items: center;
  justify-content: space-around;
  justify-self: end;
  width: 172rpx;
  height: 62rpx;
  box-sizing: border-box;
  padding: 0 20rpx;
  border: 1rpx solid rgba(151, 151, 151, .28);
  border-radius: 34rpx;
  background: rgba(255, 255, 255, .92);
  color: #050706;
  font-size: 28rpx;
}
.nav-menu view { width: 1rpx; height: 37rpx; background: rgba(151, 151, 151, .35); }
.nav-menu text:last-child { font-size: 39rpx; line-height: 1; }
.content {
  display: flex;
  min-height: 100vh;
  box-sizing: border-box;
  flex-direction: column;
  padding: calc(190rpx + env(safe-area-inset-top)) 80rpx calc(62rpx + env(safe-area-inset-bottom));
}
.hero { display: flex; align-items: center; flex-direction: column; text-align: center; }
.hero-art {
  width: 180rpx;
  height: 196rpx;
  overflow: hidden;
  border-radius: 24rpx;
  background: #f7f8f8;
}
.hero-art image { display: block; width: 100%; height: 100%; }
.hero-title { margin-top: 20rpx; color: rgba(0, 0, 0, .8); font-size: 50rpx; font-weight: 700; line-height: 62rpx; }
.hero-subtitle { margin: 8rpx 0 26rpx; color: rgba(0, 0, 0, .48); font-size: 34rpx; font-weight: 300; line-height: 52rpx; }
.hero-description { color: rgba(0, 0, 0, .8); font-size: 28rpx; font-weight: 600; line-height: 50rpx; }
.action-area { display: flex; margin-top: auto; flex-direction: column; }
.primary-action, .secondary-action {
  width: 100%;
  height: 96rpx;
  margin: 0;
  border-radius: 48rpx;
  font-size: 40rpx;
  font-weight: 500;
  line-height: 96rpx;
}
.primary-action { background: linear-gradient(148deg, #69ebc7 0%, #49a59c 100%); color: #fff; }
.secondary-action { margin-top: 32rpx; border: 2rpx solid #43aca1; background: #fff; color: #43aca1; }
.primary-action::after, .secondary-action::after { border: 0; }
.upload-note { display: flex; align-items: center; margin-top: 96rpx; flex-direction: column; }
.upload-note text:first-child { color: rgba(0, 0, 0, .8); font-size: 28rpx; font-weight: 600; line-height: 50rpx; }
.upload-note text:last-child { margin-top: 18rpx; color: rgba(0, 0, 0, .7); font-size: 24rpx; font-weight: 300; line-height: 50rpx; text-decoration: underline; }
@media (max-height: 720px) {
  .content { padding-top: calc(158rpx + env(safe-area-inset-top)); }
  .hero-art { width: 150rpx; height: 162rpx; }
  .hero-title { margin-top: 12rpx; }
  .hero-subtitle { margin-bottom: 14rpx; }
  .upload-note { margin-top: 50rpx; }
}
@media (max-width: 360px) {
  .nav-bar { grid-template-columns: 110rpx minmax(0, 1fr) 165rpx; padding-right: 18rpx; padding-left: 18rpx; }
  .nav-menu { width: 152rpx; }
  .content { padding-right: 46rpx; padding-left: 46rpx; }
}
</style>
