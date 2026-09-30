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
      <view v-if="loading" class="loading">正在读取我的提交…</view>
      <view v-else-if="records.length" class="submissions">
        <view class="section-head"><text>我的提交</text><button @tap="chooseZip">+ 提交新作品</button></view>
        <view v-for="(record, index) in records" :key="record.id || record.submissionNo" class="submission" @tap="openDetail(record)">
          <image :src="recordImage(record, index)" mode="aspectFill" />
          <view class="submission-main"><text>{{ record.title || record.originalName || '未命名作品' }}</text><text>{{ record.submissionNo || record.productNo }}</text></view>
          <text class="status" :class="statusClass(record)">{{ statusLabel(record) }}</text>
        </view>
      </view>
      <view v-else class="action-area">
        <button class="primary-action" @tap="chooseZip">确认上传</button>
        <button class="secondary-action" @tap="goBack">再逛逛</button>
        <view class="upload-note"><text>仅支持ZIP格式 · 不超过100MB</text><text @tap="showRequirements">查看作品包要求</text></view>
      </view>
      <text v-if="records.length" class="footer-note">平台将在1-2个工作日内审核作品的完整性、版权信息与生产可行性，结果可在“我的提交”里查询。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMyProfessionalSubmissions, type ProfessionalSubmission } from '../../api/creative'
import { getSession, requireSession } from '../../utils/session'

const PENDING_PRODUCT_PACKAGE_KEY = 'pending_product_package'
const MAX_ZIP_BYTES = 100 * 1024 * 1024
const records = ref<ProfessionalSubmission[]>([])
const loading = ref(false)

function goBack() { if (getCurrentPages().length > 1) { uni.navigateBack(); return }; uni.reLaunch({ url: '/pages/home/index' }) }
function showRequirements() { uni.showModal({ title: '作品包要求', content: '请上传一个不超过100MB的ZIP文件。建议包含正面、侧面、背面三视图，以及尺寸、材质、工艺、版权来源等说明；请勿直接上传文件夹。', showCancel: false, confirmText: '我知道了' }) }
function statusLabel(record: ProfessionalSubmission) {
  if (record.samplePaymentStatus === 'paid' || record.status === 'processing') return '生产中'
  if (record.status === 'approved') return record.samplePaymentStatus === 'pending' ? '待支付' : '待打样'
  return ({ review: '审核中', rejected: '未通过' } as Record<string, string>)[String(record.status)] || '处理中'
}
function statusClass(record: ProfessionalSubmission) {
  if (record.samplePaymentStatus === 'paid' || record.status === 'processing') return 'processing'
  return String(record.status || 'review')
}
function recordImage(record: ProfessionalSubmission, index: number) {
  const images = ['/static/ui-design/work-tiger.jpg', '/static/ui-design/inspiration-mask.jpg', '/static/ui-design/inspiration-cat.jpg']
  return images[Math.abs(Number(record.id || index) || index) % images.length]
}
function openDetail(record: ProfessionalSubmission) { if (record.id) uni.navigateTo({ url: `/pages/product-making-detail/index?id=${encodeURIComponent(String(record.id))}` }) }
async function loadRecords() {
  if (!requireSession()) return
  loading.value = true
  try { records.value = await getMyProfessionalSubmissions() }
  catch (error: any) { uni.showToast({ title: error?.message || '提交记录加载失败', icon: 'none' }) }
  finally { loading.value = false }
}
function openMenu() {
  uni.showActionSheet({ itemList: ['选择ZIP作品包', '刷新提交状态', '查看作品包要求'], success: result => {
    if (result.tapIndex === 0) chooseZip()
    else if (result.tapIndex === 1) loadRecords()
    else if (result.tapIndex === 2) showRequirements()
  } })
}
function chooseZip() {
  if (!requireSession()) return
  const chooser = (uni as any).chooseMessageFile
  if (typeof chooser !== 'function') return uni.showToast({ title: '当前版本暂不支持选择ZIP文件，请更新微信后重试', icon: 'none' })
  chooser({ count: 1, type: 'file', extension: ['zip'], success: (result: any) => {
    const file = result?.tempFiles?.[0]
    const path = String(file?.path || '').trim()
    const name = String(file?.name || path).trim()
    const size = Number(file?.size || 0)
    if (!path || !/\.zip$/i.test(name)) return uni.showToast({ title: '请选择ZIP格式的作品包', icon: 'none' })
    if (size > MAX_ZIP_BYTES) return uni.showToast({ title: 'ZIP作品包不能超过100MB', icon: 'none' })
    uni.setStorageSync(PENDING_PRODUCT_PACKAGE_KEY, { path, name, size, userName: String(getSession()?.user?.username || '').trim(), selectedAt: Date.now() })
    uni.navigateTo({ url: '/pages/professional/index?entry=production' })
  }, fail: (error: any) => { if (!/cancel/i.test(String(error?.errMsg || ''))) uni.showToast({ title: '文件选择失败，请重新选择', icon: 'none' }) } })
}

onShow(loadRecords)
</script>

<style scoped lang="scss">
.page { min-height: 100vh; background: linear-gradient(180deg,#fff 0%,#fff 53%,#f1fbfa 100%); color: #111; }
.nav-bar { position: fixed; z-index: 10; top: 0; right: 0; left: 0; display: grid; grid-template-columns: 150rpx minmax(0,1fr) 190rpx; align-items: end; height: calc(126rpx + env(safe-area-inset-top)); box-sizing: border-box; padding: calc(36rpx + env(safe-area-inset-top)) 26rpx 18rpx; background: rgba(255,255,255,.97); }
.nav-title { overflow: hidden; font-size: 40rpx; font-weight: 700; line-height: 58rpx; text-align: center; text-overflow: ellipsis; white-space: nowrap; }
.nav-back { justify-self: start; width: 64rpx; height: 58rpx; color: #202624; font-size: 64rpx; font-weight: 300; line-height: 48rpx; }
.nav-menu { display: flex; align-items: center; justify-content: space-around; justify-self: end; width: 172rpx; height: 62rpx; box-sizing: border-box; padding: 0 20rpx; border: 1rpx solid rgba(151,151,151,.28); border-radius: 34rpx; background: #fff; font-size: 28rpx; }
.nav-menu view { width: 1rpx; height: 37rpx; background: rgba(151,151,151,.35); }
.nav-menu text:last-child { font-size: 39rpx; line-height: 1; }
.content { display: flex; min-height: 100vh; box-sizing: border-box; flex-direction: column; padding: calc(176rpx + env(safe-area-inset-top)) 38rpx calc(64rpx + env(safe-area-inset-bottom)); }
.hero { display: flex; align-items: center; flex-direction: column; text-align: center; }
.hero-art { width: 166rpx; height: 182rpx; overflow: hidden; border-radius: 22rpx; background: #f7f8f8; }
.hero-art image { display: block; width: 100%; height: 100%; }
.hero-title { margin-top: 16rpx; color: rgba(0,0,0,.8); font-size: 48rpx; font-weight: 700; line-height: 62rpx; }
.hero-subtitle { margin: 6rpx 0 20rpx; color: rgba(0,0,0,.45); font-size: 31rpx; font-weight: 300; line-height: 48rpx; }
.hero-description { color: rgba(0,0,0,.8); font-size: 27rpx; font-weight: 600; line-height: 46rpx; }
.loading { padding: 100rpx 0; color: #7c8582; text-align: center; }
.submissions { margin-top: 154rpx; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 30rpx; }
.section-head > text { font-size: 34rpx; font-weight: 700; }
.section-head button { width: 230rpx; height: 68rpx; margin: 0; border: 3rpx solid #171b1a; border-radius: 36rpx; background: #fff; font-size: 27rpx; font-weight: 650; line-height: 62rpx; }
.section-head button::after { border: 0; }
.submission { display: flex; align-items: center; min-height: 136rpx; box-sizing: border-box; margin-bottom: 24rpx; padding: 18rpx 24rpx; border: 2rpx solid #e4faf7; border-radius: 34rpx; background: #fff; box-shadow: 0 8rpx 26rpx rgba(53,158,146,.06); }
.submission image { width: 94rpx; height: 94rpx; flex: 0 0 auto; border-radius: 25rpx; background: #f1f2f2; }
.submission-main { display: flex; min-width: 0; margin-left: 26rpx; flex: 1; flex-direction: column; }
.submission-main text:first-child { overflow: hidden; font-size: 31rpx; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.submission-main text:last-child { overflow: hidden; margin-top: 8rpx; color: #999; font-size: 20rpx; text-overflow: ellipsis; white-space: nowrap; }
.status { min-width: 138rpx; box-sizing: border-box; padding: 15rpx 22rpx; border-radius: 32rpx; background: #f7eddd; color: #ef9025; font-size: 25rpx; text-align: center; }
.status.rejected { background: #f9e5e6; color: #ee483f; }
.status.approved { background: #e3f3ef; color: #55bda7; }
.status.processing { background: #e7eef8; color: #507db8; }
.action-area { display: flex; margin-top: auto; padding: 72rpx 44rpx 0; flex-direction: column; }
.primary-action,.secondary-action { width: 100%; height: 92rpx; margin: 0; border-radius: 46rpx; font-size: 36rpx; font-weight: 500; line-height: 92rpx; }
.primary-action { background: linear-gradient(148deg,#69ebc7,#49a59c); color: #fff; }
.secondary-action { margin-top: 28rpx; border: 2rpx solid #43aca1; background: #fff; color: #43aca1; }
.primary-action::after,.secondary-action::after { border: 0; }
.upload-note { display: flex; align-items: center; margin-top: 60rpx; flex-direction: column; font-size: 24rpx; }
.upload-note text:last-child { margin-top: 16rpx; color: #666; text-decoration: underline; }
.footer-note { display: block; margin: 80rpx 50rpx 0; color: #666; font-size: 23rpx; line-height: 39rpx; text-align: center; }
@media (max-width: 360px) { .nav-bar { grid-template-columns: 110rpx minmax(0,1fr) 165rpx; padding-right: 18rpx; padding-left: 18rpx; } .nav-menu { width: 152rpx; } .content { padding-right: 28rpx; padding-left: 28rpx; } }
</style>
