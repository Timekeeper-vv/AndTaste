<template>
  <view class="page">
    <view class="topbar">
      <view class="brand" @tap="changeContext">
        <text class="brand-seal">之</text>
        <view class="brand-copy"><text class="brand-name">之间智造</text><text class="brand-subtitle">创作工作台</text></view>
      </view>
      <view class="top-actions">
        <view v-if="loggedIn" class="credit-chip" @tap="go('/pages/recharge/index')"><text>积分</text><text>{{ credits }}</text></view>
        <view class="profile-entry" @tap="openProfile"><text>我</text></view>
      </view>
    </view>

    <scroll-view scroll-y class="page-scroll" :show-scrollbar="false">
      <view class="content">
        <view class="hero">
          <image v-if="heroVisualUrl" :src="heroVisualUrl" mode="aspectFill" class="hero-visual" />
          <view v-else class="hero-fallback" aria-hidden="true"><view class="artifact-card"><text>之</text><view /><view /></view></view>
          <view v-if="heroVisualUrl" class="hero-visual-shade" />
          <view class="hero-copy">
            <text class="hero-kicker">AI 对话创作</text>
            <text class="hero-title">从一个想法开始</text>
            <text class="hero-description">描述灵感，逐步完成一件作品。</text>
            <view class="hero-link" @tap="startConversation"><text>用对话开始</text><text>›</text></view>
          </view>
          <view v-if="heroVisualUrl" class="hero-caption"><view class="ai-dot" /><text>之间智造效果图</text></view>
        </view>

        <view class="section-head path-heading"><view><text>创作入口</text><text>常用工具</text></view></view>
        <view class="creation-entry primary-entry" @tap="startConversation">
          <view class="entry-icon conversation-icon"><text>◌</text></view>
          <view class="entry-copy"><text>对话式创作</text><text>说出想法，逐步完成一件作品</text></view>
          <view class="entry-arrow primary-arrow"><text>›</text></view>
        </view>

        <view class="secondary-entry-grid">
          <view class="creation-entry secondary-entry" @tap="openCommercial">
            <view class="entry-icon commercial-icon"><text>□</text></view>
            <view class="entry-copy"><text>商品化申请</text><text>报价 · 打样 · 渠道</text></view>
            <text class="secondary-arrow">›</text>
          </view>
          <view class="creation-entry secondary-entry works-entry" @tap="openWorks">
            <view class="entry-icon works-icon"><text>◇</text></view>
            <view class="entry-copy"><text>作品与灵感</text><text>{{ assetCount ? `${assetCount} 件作品待管理` : '查看并管理作品' }}</text></view>
            <text class="secondary-arrow">›</text>
          </view>
        </view>

        <view v-if="isProfessional" class="management-workspace">
          <view class="section-head management-heading"><view><text>管理工作台</text><text>集中处理</text></view></view>
          <view class="workspace-entry" @tap="openProfessional">
            <view class="workspace-icon"><text>▦</text></view>
            <view class="workspace-copy"><text>专业工作台</text><text>作品管理 · 生产流程 · 历史记录</text><view class="workspace-stats"><text>{{ assetCount }} 件作品</text><text>{{ commercialRequestCount }} 条申请</text></view></view>
            <text class="workspace-arrow">›</text>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-nav">
      <view class="nav-item active" @tap="refreshHome"><text class="nav-icon">⌂</text><text>首页</text></view>
      <view class="nav-item create-nav" @tap="startConversation"><text class="create-icon">＋</text><text>新建创作</text></view>
      <view class="nav-item" @tap="openWorks"><text class="nav-icon">□</text><text>作品</text></view>
      <view class="nav-item" @tap="openProfile"><text class="nav-icon">◯</text><text>我的</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getAssetPreviewAccess, getAssets, getCredits, getProductionRequests } from '../../api/creative'
import { apiUrl } from '../../api/client'
import { getCommercialRequests } from '../../api/commercial'
import { getSession, requireSession } from '../../utils/session'

const user = ref(getSession()?.user)
const credits = ref(0)
const assets = ref<any[]>([])
const productionRequests = ref<any[]>([])
const commercialRequests = ref({ quoteRequests: [] as any[], consignmentApplications: [] as any[], selectionDemands: [] as any[] })
const refreshing = ref(false)
const heroVisualUrl = ref('')
const creatorMode = ref<'amateur' | 'professional'>(readCreatorMode())

const loggedIn = computed(() => Boolean(user.value))
const isProfessional = computed(() => creatorMode.value === 'professional')
const assetCount = computed(() => assets.value.length)
const commercialRequestCount = computed(() => productionRequests.value.length
  + commercialRequests.value.quoteRequests.length
  + commercialRequests.value.consignmentApplications.length
  + commercialRequests.value.selectionDemands.length)
const latestVisualAsset = computed(() => [...assets.value]
  .filter(asset => asset?.id && ['image', 'model'].includes(String(asset?.assetType || '')))
  .sort((left, right) => requestTime(right) - requestTime(left))[0] || null)

function requestTime(item: any) {
  const value = item?.updatedAt || item?.createdAt || item?.submittedAt || item?.reviewedAt || ''
  const timestamp = Date.parse(String(value))
  return Number.isFinite(timestamp) ? timestamp : 0
}

function readCreatorMode(): 'amateur' | 'professional' {
  return (uni.getStorageSync('creation_context') || {}).creatorMode === 'professional' ? 'professional' : 'amateur'
}

function go(url: string) {
  uni.navigateTo({ url })
}

function startConversation() {
  if (!requireSession()) return
  go('/pages/conversation-create/index')
}

function openWorks() {
  go('/pages/works/index')
}

function openCommercial() {
  if (!requireSession()) return
  go('/pages/commercial/index')
}

function openProfessional() {
  if (!requireSession() || !isProfessional.value) return
  go('/pages/professional/index')
}

function openProfile() {
  go('/pages/profile/index')
}

function changeContext() {
  if (!requireSession()) return
  go('/pages/purpose/index')
}

function absoluteMediaUrl(value: string | undefined, assetId: string, accessToken?: string) {
  if (!value && !accessToken) return ''
  if (value && /^https:\/\//.test(value)) {
    if (!accessToken || value.includes('access_token=')) return value
    return `${value}${value.includes('?') ? '&' : '?'}access_token=${encodeURIComponent(accessToken)}`
  }
  if (value?.startsWith('/')) return apiUrl(value)
  return accessToken ? apiUrl(`/api/creative/ai/assets/${encodeURIComponent(assetId)}/content?access_token=${encodeURIComponent(accessToken)}`) : ''
}

async function hydrateHeroVisual() {
  heroVisualUrl.value = ''
  const asset = latestVisualAsset.value
  if (!asset?.id) return
  try {
    const access = await getAssetPreviewAccess(asset.id)
    const raw = asset.assetType === 'model' ? access?.previewUrl : (access?.previewUrl || access?.url)
    heroVisualUrl.value = absoluteMediaUrl(raw, String(asset.id), access?.accessToken)
  } catch {
    heroVisualUrl.value = ''
  }
}

function normalizeCommercialRequests(value: any) {
  return {
    quoteRequests: Array.isArray(value?.quoteRequests) ? value.quoteRequests : [],
    consignmentApplications: Array.isArray(value?.consignmentApplications) ? value.consignmentApplications : [],
    selectionDemands: Array.isArray(value?.selectionDemands) ? value.selectionDemands : [],
  }
}

async function refreshHome() {
  if (!getSession() || refreshing.value) return
  refreshing.value = true
  user.value = getSession()?.user
  const [creditResult, assetResult, requestResult, commercialResult] = await Promise.allSettled([
    getCredits(),
    getAssets(),
    getProductionRequests(),
    getCommercialRequests(),
  ])
  if (creditResult.status === 'fulfilled') credits.value = Number(creditResult.value?.balance) || 0
  if (assetResult.status === 'fulfilled') assets.value = Array.isArray(assetResult.value) ? assetResult.value : []
  if (requestResult.status === 'fulfilled') productionRequests.value = Array.isArray(requestResult.value) ? requestResult.value : []
  if (commercialResult.status === 'fulfilled') commercialRequests.value = normalizeCommercialRequests(commercialResult.value)
  await hydrateHeroVisual()
  refreshing.value = false
}

onShow(() => {
  user.value = getSession()?.user
  creatorMode.value = readCreatorMode()
  if (user.value) void refreshHome()
  else {
    heroVisualUrl.value = ''
    credits.value = 0
    assets.value = []
    productionRequests.value = []
    commercialRequests.value = normalizeCommercialRequests(null)
  }
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;background:#f7f7f3;color:#263d33}
.topbar{position:relative;z-index:3;display:flex;align-items:center;justify-content:space-between;gap:24rpx;height:calc(168rpx + env(safe-area-inset-top));padding:calc(76rpx + env(safe-area-inset-top)) 32rpx 12rpx;background:#f7f7f3;box-sizing:border-box}.brand{display:flex;align-items:center;min-width:0;gap:16rpx}.brand-seal{display:grid;place-items:center;width:56rpx;height:56rpx;flex:none;border-radius:16rpx;background:#264d3e;color:#f8f4e9;font-family:"Songti SC","STSong",serif;font-size:34rpx;font-weight:700}.brand-copy{display:flex;align-items:baseline;min-width:0;gap:12rpx}.brand-name{color:#263d33;font-size:32rpx;font-weight:750}.brand-subtitle{overflow:hidden;color:#7f8d85;font-size:18rpx;text-overflow:ellipsis;white-space:nowrap}.top-actions{display:flex;align-items:center;gap:16rpx}.credit-chip{display:flex;align-items:baseline;gap:8rpx;padding:10rpx 12rpx;border:1rpx solid #dce3dc;border-radius:16rpx;background:#fbfcf9;color:#718078;font-size:19rpx}.credit-chip text:last-child{color:#416b56;font-size:24rpx;font-weight:750}.profile-entry{display:grid;place-items:center;width:56rpx;height:56rpx;flex:none;border:1rpx solid #dce3dc;border-radius:50%;background:#edf2ed;color:#416b56;font-size:22rpx;font-weight:750}
.page-scroll{height:calc(100vh - 168rpx - env(safe-area-inset-top));box-sizing:border-box}.content{padding:8rpx 32rpx calc(180rpx + env(safe-area-inset-bottom));box-sizing:border-box}.section-head{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin:32rpx 0 16rpx}.section-head>view{display:flex;align-items:baseline;min-width:0;gap:12rpx}.section-head>view text:first-child{color:#293f35;font-size:28rpx;font-weight:750}.section-head>view text:last-child{overflow:hidden;color:#8a968f;font-size:18rpx;text-overflow:ellipsis;white-space:nowrap}
.hero{position:relative;display:flex;overflow:hidden;height:244rpx;border-radius:16rpx;background:#264d3e;color:#fffdf8}.hero-visual,.hero-fallback,.hero-visual-shade{position:absolute;top:0;right:0;bottom:0;width:40%;height:100%}.hero-visual{z-index:1}.hero-fallback{z-index:1;overflow:hidden;background:#315f4b}.hero-visual-shade{z-index:2;background:rgba(18,56,43,.32)}.hero-fallback::before{position:absolute;top:18rpx;right:14rpx;width:122rpx;height:190rpx;border:1rpx solid rgba(245,221,170,.48);border-radius:16rpx;content:"";transform:rotate(10deg)}.artifact-card{position:absolute;z-index:1;top:34rpx;right:30rpx;display:flex;width:104rpx;height:170rpx;align-items:center;justify-content:center;flex-direction:column;border:1rpx solid rgba(245,221,170,.72);border-radius:16rpx;background:#d3a65e;color:#244b3c;transform:rotate(9deg)}.artifact-card>text{font-family:"Songti SC","STSong",serif;font-size:58rpx;font-weight:700;line-height:1}.artifact-card>view{width:46rpx;height:3rpx;margin-top:12rpx;border-radius:3rpx;background:rgba(38,77,62,.58)}.artifact-card>view:last-child{width:28rpx;margin-top:7rpx}.hero-copy{position:relative;z-index:3;display:flex;width:65%;min-width:0;flex-direction:column;padding:30rpx 0 0 24rpx;box-sizing:border-box}.hero-kicker{color:#c9d8cd;font-size:18rpx;font-weight:650}.hero-title{margin-top:12rpx;color:#fffdf8;font-family:"Songti SC","STSong",serif;font-size:38rpx;font-weight:700;line-height:1.2;white-space:nowrap}.hero-description{margin-top:10rpx;color:#d7e2d9;font-size:19rpx;line-height:1.5;white-space:nowrap}.hero-link{display:flex;align-items:center;gap:6rpx;width:max-content;margin-top:15rpx;padding:7rpx 0;border-bottom:1rpx solid rgba(255,255,255,.45);color:#f6efdf;font-size:20rpx;font-weight:700}.hero-link text:last-child{font-size:28rpx;font-weight:400;line-height:1}.hero-caption{position:absolute;z-index:4;right:14rpx;bottom:12rpx;display:flex;align-items:center;gap:6rpx;color:#fffdf8;font-size:15rpx;font-weight:650;white-space:nowrap}.ai-dot{width:8rpx;height:8rpx;border-radius:50%;background:#d2a75d}
.path-heading{margin-top:34rpx}.creation-entry{box-sizing:border-box;border:1rpx solid #dfe4df;border-radius:16rpx;background:#fff;box-shadow:0 5rpx 14rpx rgba(33,56,46,.03)}.primary-entry{display:flex;min-height:176rpx;align-items:center;gap:20rpx;padding:24rpx;background:#f2f7f2;border-color:#d0dfd3}.entry-icon{display:grid;place-items:center;flex:none;border-radius:16rpx;font-family:"Songti SC","STSong",serif}.entry-icon text{line-height:1}.conversation-icon{width:80rpx;height:80rpx;background:#315f4b;color:#f4f7f2;font-size:48rpx}.entry-copy{display:flex;min-width:0;flex:1;flex-direction:column}.entry-copy text:first-child{overflow:hidden;color:#293f35;font-size:30rpx;font-weight:750;text-overflow:ellipsis;white-space:nowrap}.entry-copy text:last-child{overflow:hidden;margin-top:8rpx;color:#7d8a82;font-size:20rpx;text-overflow:ellipsis;white-space:nowrap}.entry-arrow{display:grid;place-items:center;flex:none;border-radius:50%}.primary-arrow{width:60rpx;height:60rpx;background:#dfece1;color:#315f4b;font-size:38rpx;line-height:1}.secondary-entry-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16rpx;margin-top:16rpx}.secondary-entry{position:relative;display:flex;min-width:0;height:188rpx;flex-direction:column;justify-content:space-between;padding:20rpx}.secondary-entry .entry-icon{width:56rpx;height:56rpx;border-radius:14rpx;background:#edf3ee;color:#47745d;font-size:30rpx}.secondary-entry .entry-copy{margin-top:12rpx}.secondary-entry .entry-copy text:first-child{font-size:24rpx}.secondary-entry .entry-copy text:last-child{margin-top:6rpx;font-size:18rpx}.works-entry{background:#fffdf9}.works-entry .works-icon{background:#f3eee2;color:#806b46}.secondary-arrow{position:absolute;right:18rpx;bottom:15rpx;color:#75867c;font-size:32rpx;line-height:1}
.management-heading{margin-top:34rpx}.workspace-entry{display:flex;align-items:center;gap:16rpx;min-height:164rpx;padding:20rpx;border:1rpx solid #dfe4df;border-radius:16rpx;background:#fff;box-shadow:0 5rpx 14rpx rgba(33,56,46,.03)}.workspace-icon{display:grid;place-items:center;width:68rpx;height:68rpx;align-self:flex-start;flex:none;border-radius:16rpx;background:#edf1ec;color:#587263;font-size:32rpx}.workspace-copy{display:flex;min-width:0;flex:1;align-self:stretch;flex-direction:column}.workspace-copy>text:first-child{color:#31493d;font-size:25rpx;font-weight:750}.workspace-copy>text:nth-child(2){overflow:hidden;margin-top:7rpx;color:#89948d;font-size:18rpx;text-overflow:ellipsis;white-space:nowrap}.workspace-stats{display:flex;align-items:center;gap:8rpx;margin-top:auto}.workspace-stats text{padding:5rpx 8rpx;border-radius:8rpx;background:#f0f4f0;color:#61756a;font-size:16rpx}.workspace-arrow{align-self:center;color:#73827a;font-size:36rpx}
.bottom-nav{position:fixed;z-index:10;right:0;bottom:0;left:0;display:grid;grid-template-columns:repeat(4,1fr);height:116rpx;padding:12rpx 20rpx calc(12rpx + env(safe-area-inset-bottom));box-sizing:content-box;border-top:1rpx solid #dfe4df;background:rgba(250,251,248,.97)}.nav-item{display:flex;align-items:center;justify-content:center;min-width:0;flex-direction:column;gap:6rpx;color:#8b958f;font-size:17rpx}.nav-icon{font-size:30rpx;line-height:1}.nav-item.active{color:#315f4b;font-weight:750}.create-icon{display:grid;place-items:center;width:76rpx;height:76rpx;margin-top:-36rpx;border:4rpx solid #f7f7f3;border-radius:50%;background:#264d3e;color:#fffdf8;font-size:42rpx;font-weight:400;line-height:1;box-shadow:0 10rpx 20rpx rgba(33,68,52,.2)}.create-nav{color:#365849;font-weight:700}
</style>
