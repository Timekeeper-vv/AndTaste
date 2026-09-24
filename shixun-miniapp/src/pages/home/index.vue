<template>
  <view class="page">
    <view class="topbar"><view class="brand" @tap="changeContext"><image class="brand-seal" src="/static/ui-design/brand-logo.png" mode="scaleToFill" /></view><view class="top-actions"><view v-if="loggedIn" class="credit-chip" @tap="go('/pages/recharge/index')"><text>⚡</text><text>{{ credits }}</text></view><view class="notice-entry"><text>♧</text><text v-if="loggedIn" class="notice-count">8</text></view><view class="profile-entry" @tap="openProfile"><text>◯</text></view></view></view>

    <scroll-view scroll-y class="page-scroll" :show-scrollbar="false">
      <view class="content">
        <view class="hero">
          <image src="/static/ui-design/home-gift.jpg" mode="aspectFit" class="hero-gift" />
          <view class="hero-copy">
            <text class="hero-kicker">完成首次 AI 创作</text>
            <text class="hero-title">创作有礼</text>
            <text class="hero-description">即可获得 30 创作积分</text>
            <view class="hero-link" @tap="startConversation"><text>用对话开始</text><text>›</text></view>
          </view>
        </view>

        <view class="section-head path-heading"><view><text>创意工坊</text></view><text class="history-link" @tap="openConversationHistory">历史对话 ›</text></view>
        <view class="creation-entry primary-entry" @tap="startConversation"><view class="entry-copy"><text>说出你的想法，让<span>创意</span>从这里开始…</text><view class="prompt-input"><text>说说你想做什么...</text><text>→</text></view><scroll-view scroll-x class="chips"><text v-for="chip in ['食品饮品','文房器物','日用生活','创意工坊']" :key="chip">● {{ chip }}</text></scroll-view></view></view>

        <view class="secondary-entry-grid">
          <view class="creation-entry secondary-entry product-entry" @tap="openCommercial">
            <image class="secondary-art" src="/static/ui-design/product-making.jpg" mode="aspectFill" />
            <view class="entry-copy"><text>产品智造</text><text>快速打样 · 小批量起订</text><text>成熟工艺 · 产品落地</text></view>
            <text class="secondary-arrow">›</text>
          </view>
          <view class="creation-entry secondary-entry works-entry" @tap="openCommercial">
            <image class="secondary-art" src="/static/ui-design/channel-cooperation.jpg" mode="aspectFill" />
            <view class="entry-copy"><text>渠道合作</text><text>博物馆 · 景区 · 商业空间</text><text>征集合​​作 · 产品上架</text></view>
            <text class="secondary-arrow">›</text>
          </view>
        </view>

        <view class="section-head campaign-heading"><view><text>馆方征集 · 投稿通道</text></view><text>全部征集 ›</text></view>
        <view class="home-campaign" v-for="(campaign,index) in campaignsPreview" :key="campaign.key" @tap="selectHomeCampaign(campaign)"><image class="home-campaign-art" :src="campaignImage(index)" mode="aspectFill" /><view class="home-campaign-copy"><text>{{ campaign.targetName }}｜{{ campaign.title }}</text><text>{{ campaign.collectionStyle }} · 文创转化</text><text>作品审核通过 · +{{ campaign.rewardAmount }} 创作积分</text></view><text class="home-campaign-points">+{{ campaign.rewardAmount }}</text></view>
        <view class="inspiration-head"><text>灵感库</text><text @tap="openWorks">寻找灵感 ›</text></view><scroll-view scroll-x class="inspiration-scroll"><view v-for="(asset,index) in inspirationAssets" :key="asset.id || index" class="inspiration-card" @tap="openWorks"><image v-if="inspirationSrc(asset)" :src="inspirationSrc(asset)" mode="aspectFill"/><view v-else class="inspiration-placeholder">灵感</view><text>{{ asset.title || ['藏戏面具摆件','猫猫公交吊坠','双尾虎擦手巾'][index % 3] }}</text></view></scroll-view>
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

    <view class="bottom-nav"><view class="nav-item active" @tap="refreshHome"><text class="nav-icon">⌂</text><text>首页</text></view><view class="nav-item" @tap="openWorks"><text class="nav-icon">▣</text><text>作品</text></view><view class="nav-item" @tap="openProfile"><text class="nav-icon">♙</text><text>我的</text></view></view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { deleteConversation, getAssetPreviewAccess, getAssets, getConversations, getCredits, getProductionRequests, getPublicCreatorCampaigns, type ConversationSession, type CreatorCampaign } from '../../api/creative'
import { apiUrl } from '../../api/client'
import { getCommercialRequests } from '../../api/commercial'
import { getSession, requireSession } from '../../utils/session'

const user = ref(getSession()?.user)
const credits = ref(0)
const assets = ref<any[]>([])
const conversations = ref<ConversationSession[]>([])
const campaigns = ref<CreatorCampaign[]>([])
const deletingConversationId = ref<number | null>(null)
const productionRequests = ref<any[]>([])
const commercialRequests = ref({ quoteRequests: [] as any[], consignmentApplications: [] as any[], selectionDemands: [] as any[] })
const refreshing = ref(false)
const heroVisualUrl = ref('')
const inspirationPreviewUrls = ref<Record<string, string>>({})
const creatorMode = ref<'amateur' | 'professional'>(readCreatorMode())
const campaignFallbacks: CreatorCampaign[] = [
  { key: 'design-bronze', title: '青铜器主题', targetName: '国家博物馆', channelCode: 'design-bronze', collectionStyle: '青铜纹样 · 器物元素', recommendedProducts: [], brief: '', promptHint: '', rewardAmount: 80 },
  { key: 'design-lacquer', title: '漆器纹样主题', targetName: '湖北省博物馆', channelCode: 'design-lacquer', collectionStyle: '漆器纹样 · 色彩元素', recommendedProducts: [], brief: '', promptHint: '', rewardAmount: 60 },
]
const inspirationFallbacks = [
  { id: 'design-mask', title: '藏戏面具摆件', localUrl: '/static/ui-design/inspiration-mask.jpg' },
  { id: 'design-cat', title: '猫猫公交吊坠', localUrl: '/static/ui-design/inspiration-cat.jpg' },
  { id: 'design-tiger', title: '双尾虎擦手巾', localUrl: '/static/ui-design/work-tiger.jpg' },
]

const loggedIn = computed(() => Boolean(user.value))
const isProfessional = computed(() => creatorMode.value === 'professional')
const campaignsPreview = computed(() => (campaigns.value.length ? campaigns.value : campaignFallbacks).slice(0, 2))
const inspirationAssets = computed(() => {
  const live = assets.value.filter(asset => asset?.assetType === 'image').slice(0, 6)
  return live.length ? live : inspirationFallbacks
})
const campaignImage = (index: number) => index % 2 ? '/static/ui-design/campaign-drum.jpg' : '/static/ui-design/campaign-bronze.jpg'
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

function startNewConversation() {
  if (!requireSession()) return
  go('/pages/conversation-create/index?new=1')
}

function openConversation(session: ConversationSession) {
  if (!requireSession() || !session?.id) return
  go(`/pages/conversation-create/index?sessionId=${encodeURIComponent(String(session.id))}`)
}

function openConversationHistory() {
  if (!requireSession()) return
  if (conversations.value[0]) openConversation(conversations.value[0])
  else startNewConversation()
}

function selectHomeCampaign(campaign: CreatorCampaign) {
  if (campaign.key.startsWith('design-')) {
    uni.showToast({ title: '征集活动数据加载中', icon: 'none' })
    return
  }
  uni.setStorageSync('pending_creator_campaign', { ...campaign, selectedAt: Date.now() })
  uni.showToast({ title: '征集方向已选择', icon: 'none' })
  if (!getSession()) go('/pages/login/index')
}

function removeConversation(session: ConversationSession) {
  if (!session?.id || deletingConversationId.value) return
  uni.showModal({
    title: '删除历史对话',
    content: '只删除这段对话记录，不会删除作品、生产模拟图或 3D 模型。',
    confirmText: '删除',
    confirmColor: '#b45f4a',
    success: async result => {
      if (!result.confirm) return
      deletingConversationId.value = Number(session.id)
      try {
        await deleteConversation(session.id)
        conversations.value = conversations.value.filter(item => Number(item.id) !== Number(session.id))
        uni.showToast({ title: '历史对话已删除', icon: 'none' })
      } catch (error: any) {
        uni.showToast({ title: error?.message || '删除失败，请稍后重试', icon: 'none' })
      } finally {
        deletingConversationId.value = null
      }
    },
  })
}

function conversationTitle(session: ConversationSession) {
  return String(session.productType || '').trim() || '未命名创作'
}

function conversationStage(session: ConversationSession) {
  if (String(session.status || '') === 'completed') return '已完成'
  if (session.material) return `材质：${session.material}`
  if (session.mode === 'image') return '图片灵感'
  if (session.mode === 'text') return '文字灵感'
  return '创作进行中'
}

function formatConversationTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const diff = Math.max(0, Date.now() - date.getTime())
  if (diff < 60 * 1000) return '刚刚更新'
  if (diff < 24 * 60 * 60 * 1000) return `${Math.max(1, Math.floor(diff / (60 * 60 * 1000)))} 小时前`
  return `${date.getMonth() + 1}月${date.getDate()}日`
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

function inspirationSrc(asset: any) {
  return asset?.localUrl || inspirationPreviewUrls.value[String(asset?.id || '')] || ''
}

async function hydrateInspirationVisuals() {
  const pairs = await Promise.all(inspirationAssets.value.filter(asset => !asset.localUrl).map(async asset => {
    try {
      const access = await getAssetPreviewAccess(asset.id)
      const url = absoluteMediaUrl(access?.previewUrl || access?.url, String(asset.id), access?.accessToken)
      return url ? [String(asset.id), url] as const : null
    } catch {
      return null
    }
  }))
  inspirationPreviewUrls.value = Object.fromEntries(pairs.filter(Boolean) as Array<readonly [string, string]>)
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
  const [creditResult, assetResult, requestResult, commercialResult, conversationResult] = await Promise.allSettled([
    getCredits(),
    getAssets(),
    getProductionRequests(),
    getCommercialRequests(),
    getConversations(),
  ])
  const campaignResult = await Promise.allSettled([getPublicCreatorCampaigns()])
  if (creditResult.status === 'fulfilled') credits.value = Number(creditResult.value?.balance) || 0
  if (assetResult.status === 'fulfilled') assets.value = Array.isArray(assetResult.value) ? assetResult.value : []
  if (requestResult.status === 'fulfilled') productionRequests.value = Array.isArray(requestResult.value) ? requestResult.value : []
  if (commercialResult.status === 'fulfilled') commercialRequests.value = normalizeCommercialRequests(commercialResult.value)
  if (conversationResult.status === 'fulfilled') conversations.value = Array.isArray(conversationResult.value) ? conversationResult.value : []
  if (campaignResult[0].status === 'fulfilled') campaigns.value = Array.isArray(campaignResult[0].value) ? campaignResult[0].value : []
  await Promise.all([hydrateHeroVisual(), hydrateInspirationVisuals()])
  refreshing.value = false
}

onShow(() => {
  user.value = getSession()?.user
  creatorMode.value = readCreatorMode()
  if (user.value) void refreshHome()
  else {
    heroVisualUrl.value = ''
    inspirationPreviewUrls.value = {}
    credits.value = 0
    assets.value = []
    productionRequests.value = []
    commercialRequests.value = normalizeCommercialRequests(null)
    conversations.value = []
    void getPublicCreatorCampaigns().then(value => { campaigns.value = Array.isArray(value) ? value : [] }).catch(() => { campaigns.value = [] })
  }
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;background:#f7f7f3;color:#263d33}
.topbar{position:relative;z-index:3;display:flex;align-items:center;justify-content:space-between;gap:24rpx;height:calc(168rpx + env(safe-area-inset-top));padding:calc(76rpx + env(safe-area-inset-top)) 32rpx 12rpx;background:#f7f7f3;box-sizing:border-box}.brand{display:flex;align-items:center;min-width:0;gap:16rpx}.brand-seal{display:grid;place-items:center;width:56rpx;height:56rpx;flex:none;border-radius:16rpx;background:#264d3e;color:#f8f4e9;font-family:"Songti SC","STSong",serif;font-size:34rpx;font-weight:700}.brand-copy{display:flex;align-items:baseline;min-width:0;gap:12rpx}.brand-name{color:#263d33;font-size:32rpx;font-weight:750}.brand-subtitle{display:flex;overflow:hidden;align-items:center;gap:3rpx;color:#577466;font-size:18rpx;font-weight:700;white-space:nowrap}.brand-subtitle text:first-child{overflow:hidden;text-overflow:ellipsis}.brand-subtitle text:last-child{flex:none;font-size:25rpx;line-height:1}.top-actions{display:flex;align-items:center;gap:16rpx}.credit-chip{display:flex;align-items:baseline;gap:8rpx;padding:10rpx 12rpx;border:1rpx solid #dce3dc;border-radius:16rpx;background:#fbfcf9;color:#718078;font-size:19rpx}.credit-chip text:last-child{color:#416b56;font-size:24rpx;font-weight:750}.profile-entry{display:grid;place-items:center;width:56rpx;height:56rpx;flex:none;border:1rpx solid #dce3dc;border-radius:50%;background:#edf2ed;color:#416b56;font-size:22rpx;font-weight:750}
.page-scroll{height:calc(100vh - 168rpx - env(safe-area-inset-top));box-sizing:border-box}.content{padding:8rpx 32rpx calc(180rpx + env(safe-area-inset-bottom));box-sizing:border-box}.section-head{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin:32rpx 0 16rpx}.section-head>view{display:flex;align-items:baseline;min-width:0;gap:12rpx}.section-head>view text:first-child{color:#293f35;font-size:28rpx;font-weight:750}.section-head>view text:last-child{overflow:hidden;color:#8a968f;font-size:18rpx;text-overflow:ellipsis;white-space:nowrap}
.hero{position:relative;display:flex;overflow:hidden;height:244rpx;border-radius:16rpx;background:#264d3e;color:#fffdf8}.hero-visual,.hero-fallback,.hero-visual-shade{position:absolute;top:0;right:0;bottom:0;width:40%;height:100%}.hero-visual{z-index:1}.hero-fallback{z-index:1;overflow:hidden;background:#315f4b}.hero-visual-shade{z-index:2;background:rgba(18,56,43,.32)}.hero-fallback::before{position:absolute;top:18rpx;right:14rpx;width:122rpx;height:190rpx;border:1rpx solid rgba(245,221,170,.48);border-radius:16rpx;content:"";transform:rotate(10deg)}.artifact-card{position:absolute;z-index:1;top:34rpx;right:30rpx;display:flex;width:104rpx;height:170rpx;align-items:center;justify-content:center;flex-direction:column;border:1rpx solid rgba(245,221,170,.72);border-radius:16rpx;background:#d3a65e;color:#244b3c;transform:rotate(9deg)}.artifact-card>text{font-family:"Songti SC","STSong",serif;font-size:58rpx;font-weight:700;line-height:1}.artifact-card>view{width:46rpx;height:3rpx;margin-top:12rpx;border-radius:3rpx;background:rgba(38,77,62,.58)}.artifact-card>view:last-child{width:28rpx;margin-top:7rpx}.hero-copy{position:relative;z-index:3;display:flex;width:65%;min-width:0;flex-direction:column;padding:30rpx 0 0 24rpx;box-sizing:border-box}.hero-kicker{color:#c9d8cd;font-size:18rpx;font-weight:650}.hero-title{margin-top:12rpx;color:#fffdf8;font-family:"Songti SC","STSong",serif;font-size:38rpx;font-weight:700;line-height:1.2;white-space:nowrap}.hero-description{margin-top:10rpx;color:#d7e2d9;font-size:19rpx;line-height:1.5;white-space:nowrap}.hero-link{display:flex;align-items:center;gap:6rpx;width:max-content;margin-top:15rpx;padding:7rpx 0;border-bottom:1rpx solid rgba(255,255,255,.45);color:#f6efdf;font-size:20rpx;font-weight:700}.hero-link text:last-child{font-size:28rpx;font-weight:400;line-height:1}.hero-caption{position:absolute;z-index:4;right:14rpx;bottom:12rpx;display:flex;align-items:center;gap:6rpx;color:#fffdf8;font-size:15rpx;font-weight:650;white-space:nowrap}.ai-dot{width:8rpx;height:8rpx;border-radius:50%;background:#d2a75d}
.path-heading{margin-top:34rpx}.creation-entry{box-sizing:border-box;border:1rpx solid #dfe4df;border-radius:16rpx;background:#fff;box-shadow:0 5rpx 14rpx rgba(33,56,46,.03)}.primary-entry{display:flex;min-height:176rpx;align-items:center;gap:20rpx;padding:24rpx;background:#f2f7f2;border-color:#d0dfd3}.entry-icon{display:grid;place-items:center;flex:none;border-radius:16rpx;font-family:"Songti SC","STSong",serif}.entry-icon text{line-height:1}.conversation-icon{width:80rpx;height:80rpx;background:#315f4b;color:#f4f7f2;font-size:48rpx}.entry-copy{display:flex;min-width:0;flex:1;flex-direction:column}.entry-copy text:first-child{overflow:hidden;color:#293f35;font-size:30rpx;font-weight:750;text-overflow:ellipsis;white-space:nowrap}.entry-copy text:last-child{overflow:hidden;margin-top:8rpx;color:#7d8a82;font-size:20rpx;text-overflow:ellipsis;white-space:nowrap}.entry-arrow{display:grid;place-items:center;flex:none;border-radius:50%}.primary-arrow{width:60rpx;height:60rpx;background:#dfece1;color:#315f4b;font-size:38rpx;line-height:1}.secondary-entry-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16rpx;margin-top:16rpx}.secondary-entry{position:relative;display:flex;min-width:0;height:188rpx;flex-direction:column;justify-content:space-between;padding:20rpx}.secondary-entry .entry-icon{width:56rpx;height:56rpx;border-radius:14rpx;background:#edf3ee;color:#47745d;font-size:30rpx}.secondary-entry .entry-copy{margin-top:12rpx}.secondary-entry .entry-copy text:first-child{font-size:24rpx}.secondary-entry .entry-copy text:last-child{margin-top:6rpx;font-size:18rpx}.works-entry{background:#fffdf9}.works-entry .works-icon{background:#f3eee2;color:#806b46}.secondary-arrow{position:absolute;right:18rpx;bottom:15rpx;color:#75867c;font-size:32rpx;line-height:1}
.management-heading{margin-top:34rpx}.workspace-entry{display:flex;align-items:center;gap:16rpx;min-height:164rpx;padding:20rpx;border:1rpx solid #dfe4df;border-radius:16rpx;background:#fff;box-shadow:0 5rpx 14rpx rgba(33,56,46,.03)}.workspace-icon{display:grid;place-items:center;width:68rpx;height:68rpx;align-self:flex-start;flex:none;border-radius:16rpx;background:#edf1ec;color:#587263;font-size:32rpx}.workspace-copy{display:flex;min-width:0;flex:1;align-self:stretch;flex-direction:column}.workspace-copy>text:first-child{color:#31493d;font-size:25rpx;font-weight:750}.workspace-copy>text:nth-child(2){overflow:hidden;margin-top:7rpx;color:#89948d;font-size:18rpx;text-overflow:ellipsis;white-space:nowrap}.workspace-stats{display:flex;align-items:center;gap:8rpx;margin-top:auto}.workspace-stats text{padding:5rpx 8rpx;border-radius:8rpx;background:#f0f4f0;color:#61756a;font-size:16rpx}.workspace-arrow{align-self:center;color:#73827a;font-size:36rpx}
.bottom-nav{position:fixed;z-index:10;right:0;bottom:0;left:0;display:grid;grid-template-columns:repeat(4,1fr);height:116rpx;padding:12rpx 20rpx calc(12rpx + env(safe-area-inset-bottom));box-sizing:content-box;border-top:1rpx solid #dfe4df;background:rgba(250,251,248,.97)}.nav-item{display:flex;align-items:center;justify-content:center;min-width:0;flex-direction:column;gap:6rpx;color:#8b958f;font-size:17rpx}.nav-icon{font-size:30rpx;line-height:1}.nav-item.active{color:#315f4b;font-weight:750}.create-icon{display:grid;place-items:center;width:76rpx;height:76rpx;margin-top:-36rpx;border:4rpx solid #f7f7f3;border-radius:50%;background:#264d3e;color:#fffdf8;font-size:42rpx;font-weight:400;line-height:1;box-shadow:0 10rpx 20rpx rgba(33,68,52,.2)}.create-nav{color:#365849;font-weight:700}
.history-section{margin-top:28rpx}.history-heading{margin:0 0 14rpx}.new-conversation{display:flex;align-items:center;gap:4rpx;padding:8rpx 12rpx;border:1rpx solid #cbdccf;border-radius:10rpx;background:#f0f7f1;color:#47715a;font-size:17rpx;font-weight:700}.new-conversation text:first-child{font-size:24rpx;line-height:1}.conversation-history-list{display:flex;flex-direction:column;gap:9rpx}.conversation-history-item,.history-empty{display:flex;align-items:center;gap:13rpx;min-height:86rpx;padding:13rpx 14rpx;box-sizing:border-box;border:1rpx solid #e0e7e1;border-radius:14rpx;background:#fff;box-shadow:0 4rpx 12rpx rgba(33,56,46,.025)}.conversation-history-item:active,.history-empty:active{background:#f1f7f1}.history-mark,.history-empty-mark{display:grid;place-items:center;flex:none;width:48rpx;height:48rpx;border-radius:13rpx;background:#edf4ee;color:#527963;font-family:"Songti SC","STSong",serif;font-size:23rpx;font-weight:800}.history-copy{display:flex;min-width:0;flex:1;flex-direction:column;gap:7rpx}.history-title-row{display:flex;align-items:baseline;justify-content:space-between;gap:10rpx;min-width:0}.history-title-row text:first-child{overflow:hidden;color:#344b3f;font-size:20rpx;font-weight:750;text-overflow:ellipsis;white-space:nowrap}.history-title-row text:last-child{flex:none;color:#9aa59e;font-size:14rpx}.history-detail{overflow:hidden;color:#819088;font-size:15rpx;text-overflow:ellipsis;white-space:nowrap}.history-arrow{flex:none;color:#84948b;font-size:30rpx;line-height:1}.history-empty{border-style:dashed;background:#fbfcfa}.history-empty-mark{background:#f5f7f3;color:#769081;font-family:inherit;font-size:27rpx}.history-empty view{display:flex;min-width:0;flex:1;flex-direction:column;gap:5rpx}.history-empty view text:first-child{color:#52695b;font-size:18rpx;font-weight:750}.history-empty view text:last-child{overflow:hidden;color:#9aa59e;font-size:14rpx;text-overflow:ellipsis;white-space:nowrap}
.history-item-actions{display:flex;align-items:center;gap:9rpx;flex:none}.history-delete{display:flex;align-items:center;justify-content:center;min-width:64rpx;height:46rpx;padding:0 7rpx;border:1rpx solid #efd8d0;border-radius:9rpx;background:#fff8f5;color:#b45f4a;font-size:14rpx}.history-delete:active{background:#fbe9e3}.history-delete.loading{opacity:.6}
</style>

<style scoped lang="scss">
.page{background:#fff;color:#202d29}.topbar{height:calc(118rpx + env(safe-area-inset-top));padding:calc(57rpx + env(safe-area-inset-top)) 32rpx 12rpx;background:#fff}.brand-seal{width:56rpx;height:56rpx;border-radius:16rpx}.brand-copy{display:none}.top-actions{gap:17rpx}.credit-chip{padding:10rpx 16rpx;border:0;border-radius:30rpx;background:#101615;color:#fff;font-size:20rpx}.credit-chip text:last-child{color:#fff;font-size:23rpx}.notice-entry{position:relative;display:grid;place-items:center;width:58rpx;height:58rpx;border:2rpx solid #0bc6a5;border-radius:50%;color:#08b99a;font-size:27rpx}.notice-count{position:absolute;right:-7rpx;top:-9rpx;display:grid;place-items:center;width:30rpx;height:30rpx;border-radius:50%;background:#ff5861;color:#fff;font-size:17rpx}.profile-entry{width:58rpx;height:58rpx;border:1rpx solid #dedede;border-radius:50%;background:#fff;color:#333;font-size:24rpx}.page-scroll{height:calc(100vh - 118rpx - env(safe-area-inset-top))}.content{padding:10rpx 32rpx calc(160rpx + env(safe-area-inset-bottom))}.hero{height:284rpx;border-radius:23rpx;background:linear-gradient(110deg,#eef7ff,#f4f7fb 58%,#fff0e4)}.hero-gift{position:absolute;z-index:1;right:0;top:0;width:53%;height:100%}.hero-copy{padding:52rpx 0 0 27rpx}.hero-kicker{order:2;margin-top:24rpx;color:#172c43;font-size:19rpx}.hero-title{order:1;font-size:49rpx;color:#143259}.hero-description{order:3;color:#506278;font-size:22rpx}.hero-link{order:4;margin-top:17rpx;border:0;color:#1e6bd6;font-size:20rpx}.path-heading{margin-top:39rpx}.section-head>view text:first-child{font-family:"PingFang SC",sans-serif;color:#1f2523;font-size:31rpx}.history-link{color:#888;font-size:19rpx}.primary-entry{min-height:250rpx;padding:28rpx 28rpx;border:0;border-radius:24rpx;background:#fff;box-shadow:0 9rpx 25rpx rgba(66,94,87,.08)}.primary-entry .entry-copy>text:first-child{color:#252525;font-size:30rpx;font-weight:800}.primary-entry .entry-copy>text:first-child span{color:#256ee3}.prompt-input{display:flex;align-items:center;justify-content:space-between;margin-top:25rpx;padding:12rpx 13rpx 12rpx 27rpx;border-radius:50rpx;background:#f1f4f8;color:#a0a5aa;font-size:23rpx}.prompt-input text:last-child{display:grid;place-items:center;width:58rpx;height:58rpx;border-radius:50%;background:#2b65d5;color:#fff;font-size:38rpx;line-height:1}.chips{width:calc(100% + 12rpx);margin-top:20rpx;white-space:nowrap}.chips text{display:inline-block;margin-right:12rpx;padding:9rpx 15rpx;border:2rpx solid #7ca8ff;border-radius:30rpx;color:#376dd7;font-size:18rpx}.secondary-entry-grid{gap:17rpx;margin-top:18rpx}.secondary-entry{position:relative;overflow:hidden;height:188rpx;border:0;border-radius:23rpx;padding:23rpx;background:#f7f7f7;box-shadow:none}.secondary-art{position:absolute;right:0;top:0;width:54%;height:48%;border-radius:0 23rpx 0 0}.product-entry{background:linear-gradient(180deg,#fff0dd,#f4f4f4)}.works-entry{background:linear-gradient(180deg,#dffaf4,#f4f4f4)}.secondary-entry .entry-copy{position:relative;z-index:2;margin-top:auto}.secondary-entry .entry-copy text:first-child{color:#efa441;font-size:27rpx}.works-entry .entry-copy text:first-child{color:#05aa96}.secondary-entry .entry-copy text:last-child{color:#555;font-size:18rpx}.secondary-entry .entry-copy text:nth-child(3){margin-top:5rpx;color:#888;font-size:17rpx}.secondary-arrow{display:none}.campaign-heading{margin-top:39rpx}.campaign-heading>text{color:#888;font-size:19rpx}.home-campaign{display:flex;align-items:center;gap:17rpx;margin-bottom:15rpx;padding:0;border-left:8rpx solid #07bfa0;border-radius:21rpx;background:#fff;box-shadow:0 8rpx 22rpx rgba(14,160,129,.1);overflow:hidden}.home-campaign-art{width:135rpx;height:135rpx;flex:none;border-radius:17rpx}.home-campaign-copy{display:flex;min-width:0;flex:1;flex-direction:column;gap:8rpx}.home-campaign-copy text:first-child{overflow:hidden;color:#2a332f;font-size:23rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.home-campaign-copy text:nth-child(2),.home-campaign-copy text:nth-child(3){color:#999;font-size:17rpx}.home-campaign-points{margin-right:12rpx;padding:8rpx 12rpx;border-radius:30rpx;background:#07bea0;color:#fff;font-size:19rpx;font-weight:800}.inspiration-head{display:flex;align-items:center;justify-content:space-between;margin:31rpx 0 16rpx;color:#222;font-size:29rpx;font-weight:800}.inspiration-head text:last-child{color:#888;font-size:19rpx;font-weight:400}.inspiration-scroll{width:calc(100% + 12rpx);margin-left:-6rpx;white-space:nowrap}.inspiration-card{display:inline-flex;overflow:hidden;width:185rpx;height:225rpx;margin:0 7rpx;border-radius:17rpx;background:#f6f6f6;vertical-align:top;flex-direction:column}.inspiration-card image,.inspiration-placeholder{display:block;width:100%;height:225rpx;background:#e9ecea}.inspiration-placeholder{display:grid;place-items:center;color:#9aac9f;font-size:25rpx}.inspiration-card>text{padding:9rpx 10rpx;color:#fff;font-size:18rpx;background:rgba(28,28,28,.55);transform:translateY(-38rpx)}.bottom-nav{right:28rpx;bottom:20rpx;left:28rpx;grid-template-columns:repeat(3,1fr);height:84rpx;padding:10rpx 12rpx;border:0;border-radius:40rpx;background:linear-gradient(90deg,#12d8bd,#00b9a6);box-shadow:0 12rpx 28rpx rgba(0,161,141,.24)}.nav-item{color:#dffff8;font-size:18rpx}.nav-icon{font-size:30rpx}.nav-item.active{color:#fff;background:none}.create-nav{display:none}
</style>

<style scoped lang="scss">
.brand-seal{display:block;border-radius:0;background:transparent}
</style>
