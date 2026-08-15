<template>
  <view class="page">
    <view class="hero" :class="{ 'has-visual': Boolean(heroVisualUrl) }">
      <image v-if="heroVisualUrl" :src="heroVisualUrl" mode="aspectFill" class="hero-visual" />
      <view v-else class="hero-fallback" aria-hidden="true"><view class="artifact-form"><text>之</text><view /><view /></view></view>
      <view class="hero-film" />
      <view class="hero-shade" />

      <view class="topbar">
        <view class="brand" @tap="changeContext"><text class="brand-seal">之</text><view><text>创作工作台</text><text>{{ contextLabel }}</text></view></view>
        <view class="top-actions">
          <view v-if="loggedIn" class="credit-chip" @tap="go('/pages/recharge/index')"><text>积分</text><text>{{ credits }}</text></view>
          <view class="profile-entry" @tap="openProfile"><text>我</text></view>
        </view>
      </view>

      <view class="hero-copy">
        <view class="hero-meta"><text>CULTURAL CREATION</text><text>{{ heroVisualUrl ? 'LATEST WORK' : 'IDEA TO OBJECT' }}</text></view>
        <text class="hero-title">把灵感，<br />做成真实的作品。</text>
        <text class="hero-description">从一句描述开始，完成产品视觉、原型与生产。</text>
      </view>

      <view class="hero-caption">
        <view><text>{{ heroVisualLabel }}</text><text>{{ heroVisualTitle }}</text></view>
        <text>{{ heroVisualUrl ? 'AI生成' : '01 / 03' }}</text>
      </view>
    </view>

    <view class="journey-card" @tap="openProgress">
      <view class="journey-mark"><text>{{ progressNumber }}</text><text>现在</text></view>
      <view class="journey-copy"><text>创作正在发生</text><text>{{ progressState.title }}</text><text>{{ progressState.description }}</text></view>
      <view class="journey-state"><text :class="progressState.tone">{{ progressState.label }}</text><text>›</text></view>
    </view>

    <view class="section-heading"><view><text>CREATIVE PATHS</text><text>从这里，继续向前。</text></view><text>三个入口</text></view>
    <view class="entry-field">
      <view class="entry-card conversation-entry" @tap="startConversation">
        <view class="entry-card-top"><text>01</text><text>新建创作</text></view>
        <view class="entry-card-bottom"><view><text>对话式创作</text><text>说出一个想法，开始一件作品。</text></view><text class="entry-arrow">›</text></view>
      </view>
      <view class="minor-entry-row">
        <view class="entry-card progress-entry" @tap="openProgress"><text>02</text><view><text>产品进度</text><text>{{ progressShortLabel }}</text></view><text class="entry-arrow">›</text></view>
        <view class="entry-card works-entry" @tap="openWorks"><text>03</text><view><text>作品与灵感</text><text>{{ assetCount }} 件作品</text></view><text class="entry-arrow">›</text></view>
      </view>
    </view>

    <view v-if="isProfessional" class="professional-link" @tap="go('/pages/professional/index')"><view><text>专业创作工作台</text><text>作品包、评审与生产对接</text></view><text>›</text></view>
    <text v-else class="context-link" @tap="changeContext">当前为{{ contextLabel }} · 切换创作方式或售卖渠道 ›</text>

    <view class="bottom-nav">
      <view class="active" @tap="refreshHome"><text>⌂</text><text>首页</text></view>
      <view @tap="startConversation"><text>✦</text><text>创作</text></view>
      <view @tap="openWorks"><text>▣</text><text>作品</text></view>
      <view @tap="openProfile"><text>◉</text><text>我的</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getAssetPreviewAccess, getAssets, getCredits, getProductionRequests } from '../../api/creative'
import { apiUrl } from '../../api/client'
import { getSession, requireSession } from '../../utils/session'

const user = ref(getSession()?.user)
const credits = ref(0)
const assets = ref<any[]>([])
const productionRequests = ref<any[]>([])
const refreshing = ref(false)
const heroVisualUrl = ref('')
const context = ref<any>(uni.getStorageSync('creation_context') || {})

const loggedIn = computed(() => Boolean(user.value))
const isProfessional = computed(() => context.value?.creatorMode === 'professional')
const contextLabel = computed(() => {
  if (!loggedIn.value) return '先浏览，随时开始创作'
  if (context.value?.purpose === 'museum_sale') return `${isProfessional.value ? '专业' : '业余'} · 文旅售卖`
  return `${isProfessional.value ? '专业' : '业余'} · 个人创作`
})
const assetCount = computed(() => assets.value.length)
const latestVisualAsset = computed(() => [...assets.value]
  .filter(asset => asset?.id && ['image', 'model'].includes(String(asset?.assetType || '')))
  .sort((left, right) => requestTime(right) - requestTime(left))[0] || null)
const heroVisualLabel = computed(() => {
  if (!heroVisualUrl.value) return '创作的起点'
  return latestVisualAsset.value?.assetType === 'model' ? '最新 3D 原型' : '最新效果图'
})
const heroVisualTitle = computed(() => String(latestVisualAsset.value?.title || '让一个想法，成为可被看见的作品'))
const latestRequest = computed(() => [...productionRequests.value].sort((left, right) => requestTime(right) - requestTime(left))[0] || null)
const latestTrackedAsset = computed(() => assets.value
  .filter(asset => ['review', 'approved', 'rejected'].includes(String(asset?.status || '').toLowerCase()))
  .sort((left, right) => requestTime(right) - requestTime(left))[0] || null)
const latestProgressItem = computed(() => {
  const request = latestRequest.value
  const asset = latestTrackedAsset.value
  if (!request) return asset ? { type: 'asset' as const, value: asset } : null
  if (!asset) return { type: 'request' as const, value: request }
  return requestTime(request) >= requestTime(asset)
    ? { type: 'request' as const, value: request }
    : { type: 'asset' as const, value: asset }
})
const progressState = computed(() => {
  if (!loggedIn.value) return { index: 0, tone: 'neutral', label: '登录后同步', title: '你的第一件产品，从这里开始', description: '登录后可以同步作品审核、打样和生产状态。' }
  const item = latestProgressItem.value
  if (!item) return { index: 0, tone: 'neutral', label: '待开始', title: '还没有产品项目', description: '完成创作后，提交审核或创建打样申请即可在这里继续推进。' }

  if (item.type === 'asset') {
    const asset = item.value
    const status = String(asset.status || '').toLowerCase()
    const title = asset.title || '未命名作品'
    if (status === 'rejected') return { index: 1, tone: 'warning', label: '需要调整', title, description: '审核未通过，请在作品与灵感中调整后重新提交。' }
    if (status === 'approved' && asset.assetType !== 'model') return { index: 1, tone: 'active', label: '审核已通过', title, description: '图片审核已通过，继续生成 3D 模型后可创建打样或生产项目。' }
    if (status === 'approved') return { index: 2, tone: 'active', label: '可创建项目', title, description: '3D 模型审核已通过，可以创建打样或生产项目。' }
    return { index: 1, tone: 'active', label: '审核中', title, description: '平台正在核对作品内容、版权材料和生产可行性。' }
  }

  const request = item.value
  const status = String(request.status || '').toLowerCase()
  const paymentStatus = String(request.samplePaymentStatus || '').toLowerCase()
  const title = request.title || request.assetTitle || request.sampleProductName || '未命名产品'
  if (['rejected', 'returned', 'need_materials'].includes(status)) return { index: 1, tone: 'warning', label: '需要调整', title, description: request.reviewComment || '请在作品页查看审核说明并补充后重新提交。' }
  if (['completed', 'shipped'].includes(status)) return { index: 3, tone: 'complete', label: '已完成', title, description: '该产品流程已完成，详情以产品进度页为准。' }
  if (['producing', 'production', 'in_progress'].includes(status)) return { index: 3, tone: 'active', label: '生产中', title, description: '供应链正在推进生产，详情以产品进度页为准。' }
  if (status === 'approved' && ['unpaid', 'pending', 'manual_review'].includes(paymentStatus)) return { index: 2, tone: 'warning', label: '待支付打样费', title, description: '申请已通过，请完成打样费支付后进入生产安排。' }
  if (status === 'approved' && paymentStatus === 'paid') return { index: 2, tone: 'active', label: '打样安排中', title, description: '已完成打样费支付，供应链正在安排后续流程。' }
  if (status === 'approved') return { index: 2, tone: 'active', label: '审核已通过', title, description: '产品已通过审核，正在进入打样或生产对接。' }
  return { index: 1, tone: 'active', label: '审核中', title, description: '平台正在核对作品、工艺和生产信息。' }
})
const progressShortLabel = computed(() => ({
  '登录后同步': '登录后同步',
  '待开始': '从创作开始',
  '需要调整': '需要调整',
  '可创建项目': '创建项目',
  '待支付打样费': '待支付',
  '打样安排中': '打样中',
  '审核已通过': '已通过',
  '审核中': '审核中',
  '生产中': '生产中',
  '已完成': '已完成',
}[progressState.value.label] || '查看进度'))
const progressNumber = computed(() => String(Math.min(Math.max(Number(progressState.value.index) + 1, 1), 3)).padStart(2, '0'))

function requestTime(item: any) {
  const value = item?.updatedAt || item?.createdAt || item?.submittedAt || item?.reviewedAt || ''
  const timestamp = Date.parse(String(value))
  return Number.isFinite(timestamp) ? timestamp : 0
}

function go(url: string) {
  uni.navigateTo({ url })
}

function startConversation() {
  if (!requireSession()) return
  go('/pages/conversation-create/index')
}

function openWorks() {
  if (!requireSession()) return
  go('/pages/works/index')
}

function openProgress() {
  if (!requireSession()) return
  go('/pages/product-progress/index')
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

async function refreshHome() {
  if (!getSession() || refreshing.value) return
  refreshing.value = true
  user.value = getSession()?.user
  context.value = uni.getStorageSync('creation_context') || {}
  const [creditResult, assetResult, requestResult] = await Promise.allSettled([
    getCredits(),
    getAssets(),
    getProductionRequests(),
  ])
  if (creditResult.status === 'fulfilled') credits.value = Number(creditResult.value?.balance) || 0
  if (assetResult.status === 'fulfilled') assets.value = Array.isArray(assetResult.value) ? assetResult.value : []
  if (requestResult.status === 'fulfilled') productionRequests.value = Array.isArray(requestResult.value) ? requestResult.value : []
  void hydrateHeroVisual()
  refreshing.value = false
}

onShow(() => {
  user.value = getSession()?.user
  context.value = uni.getStorageSync('creation_context') || {}
  if (user.value) void refreshHome()
  else heroVisualUrl.value = ''
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;box-sizing:border-box;padding:0 0 calc(150rpx + env(safe-area-inset-bottom));background:#edf1ec;color:#27362f}
.hero{position:relative;display:flex;min-height:588rpx;overflow:hidden;flex-direction:column;background:#153d33;color:#fffdf7;border-radius:0 0 28rpx 28rpx;box-shadow:0 18rpx 36rpx rgba(33,58,46,.15)}.hero-visual,.hero-fallback,.hero-film,.hero-shade{position:absolute;inset:0;width:100%;height:100%}.hero-visual{z-index:1}.hero-fallback{z-index:1;overflow:hidden;background:#163f35}.hero-film{z-index:2;opacity:.22;background-image:repeating-linear-gradient(0deg,rgba(255,255,255,.18) 0,rgba(255,255,255,.18) 1rpx,transparent 1rpx,transparent 6rpx);mix-blend-mode:soft-light;pointer-events:none}.hero-shade{z-index:3;background:linear-gradient(180deg,rgba(10,30,24,.38) 0%,rgba(12,42,33,.17) 28%,rgba(11,35,28,.84) 100%)}.hero.has-visual .hero-shade{background:linear-gradient(180deg,rgba(9,27,22,.48) 0%,rgba(12,39,31,.18) 32%,rgba(10,32,26,.86) 100%)}
.artifact-form{position:absolute;right:-15rpx;bottom:54rpx;width:286rpx;height:372rpx;border:2rpx solid rgba(242,204,138,.78);border-radius:54% 46% 56% 44% / 42% 59% 41% 58%;background:#ca965b;box-shadow:inset 0 0 0 11rpx rgba(246,226,183,.18);transform:rotate(13deg)}.artifact-form>text{position:absolute;top:42rpx;left:44rpx;color:#153d33;font-family:"Songti SC","STSong",serif;font-size:122rpx;font-weight:850;line-height:1}.artifact-form>view:first-of-type{position:absolute;right:37rpx;bottom:58rpx;width:100rpx;height:100rpx;border:2rpx solid rgba(21,61,51,.68);border-radius:42% 58% 54% 46% / 44% 37% 63% 56%}.artifact-form>view:last-of-type{position:absolute;right:64rpx;bottom:88rpx;width:43rpx;height:43rpx;border:2rpx solid rgba(21,61,51,.52);border-radius:52% 48% 44% 56% / 42% 57% 43% 58%}.hero-fallback::before{position:absolute;top:144rpx;right:194rpx;width:208rpx;height:208rpx;border:1rpx solid rgba(249,235,204,.2);border-radius:56% 44% 42% 58% / 47% 56% 44% 53%;content:"";transform:rotate(-27deg)}.hero-fallback::after{position:absolute;right:0;bottom:44rpx;left:0;height:105rpx;border-top:1rpx solid rgba(255,255,255,.16);content:"";transform:skewY(-5deg)}
.topbar{position:relative;z-index:5;display:flex;align-items:center;justify-content:space-between;gap:18rpx;padding:27rpx 32rpx 0}.brand{display:flex;align-items:center;min-width:0;gap:13rpx}.brand-seal{display:grid;place-items:center;width:48rpx;height:48rpx;flex:none;border:1rpx solid rgba(255,255,255,.32);border-radius:10rpx;background:rgba(255,255,255,.12);color:#fffdf7;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:850}.brand view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.brand view text:first-child{color:#fffdf7;font-family:"Songti SC","STSong",serif;font-size:30rpx;font-weight:850}.brand view text:last-child{overflow:hidden;color:rgba(240,248,240,.73);font-size:16rpx;text-overflow:ellipsis;white-space:nowrap}.top-actions{display:flex;align-items:center;gap:11rpx}.credit-chip{display:flex;align-items:baseline;gap:5rpx;padding:9rpx 10rpx;border:1rpx solid rgba(255,255,255,.2);border-radius:9rpx;background:rgba(10,39,30,.24);color:#d5e3d8;font-size:16rpx}.credit-chip text:last-child{color:#f6cb7a;font-size:22rpx;font-weight:850}.profile-entry{display:grid;place-items:center;width:48rpx;height:48rpx;border:1rpx solid rgba(255,255,255,.22);border-radius:50%;background:rgba(255,255,255,.12);color:#fffdf7;font-family:"Songti SC","STSong",serif;font-size:23rpx;font-weight:850}
.hero-copy{position:relative;z-index:5;display:flex;max-width:610rpx;flex:1;flex-direction:column;padding:66rpx 32rpx 25rpx}.hero-meta{display:flex;align-items:center;justify-content:space-between;gap:12rpx;color:#cadbcf;font-size:16rpx;font-weight:850;letter-spacing:1.4rpx}.hero-meta text:last-child{color:#f2cc85;font-size:15rpx;letter-spacing:0}.hero-title{margin-top:21rpx;color:#fffdf8;font-family:"Songti SC","STSong",serif;font-size:54rpx;font-weight:850;line-height:1.17}.hero-description{max-width:460rpx;margin-top:16rpx;color:#d4e0d6;font-size:21rpx;line-height:1.62}.hero-caption{position:relative;z-index:5;display:flex;align-items:flex-end;justify-content:space-between;gap:18rpx;padding:0 32rpx 25rpx}.hero-caption>view{display:flex;min-width:0;flex:1;flex-direction:column;gap:4rpx}.hero-caption text:first-child{color:#ebd2a2;font-size:16rpx;font-weight:850}.hero-caption text:last-child{overflow:hidden;color:rgba(255,255,255,.78);font-size:18rpx;text-overflow:ellipsis;white-space:nowrap}.hero-caption>text{flex:none;color:#f4d19b;font-size:16rpx;font-weight:850}

.journey-card{position:relative;z-index:6;display:flex;align-items:center;gap:15rpx;min-height:136rpx;margin:-24rpx 24rpx 0;padding:17rpx 17rpx 17rpx 18rpx;border:1rpx solid #d5dfd8;border-radius:12rpx;background:#fbfcf9;box-shadow:0 14rpx 26rpx rgba(46,64,54,.1)}.journey-mark{display:flex;align-items:center;justify-content:center;width:72rpx;height:72rpx;flex:none;flex-direction:column;border-radius:45% 55% 46% 54% / 44% 48% 52% 56%;background:#deece0;color:#315f4b}.journey-mark text:first-child{font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:850;line-height:1}.journey-mark text:last-child{margin-top:4rpx;font-size:14rpx;font-weight:850}.journey-copy{display:flex;min-width:0;flex:1;flex-direction:column}.journey-copy text:first-child{color:#829087;font-size:16rpx;font-weight:750}.journey-copy text:nth-child(2){overflow:hidden;margin-top:4rpx;color:#304339;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.journey-copy text:last-child{display:-webkit-box;overflow:hidden;margin-top:4rpx;color:#879188;font-size:16rpx;line-height:1.4;-webkit-box-orient:vertical;-webkit-line-clamp:1}.journey-state{display:flex;align-items:center;gap:8rpx;flex:none}.journey-state text:first-child{padding:6rpx 8rpx;border-radius:6rpx;background:#e4efe7;color:#4f7761;font-size:16rpx;font-weight:850}.journey-state text:first-child.warning{background:#f8e9d7;color:#9b6631}.journey-state text:first-child.complete{background:#dcefe2;color:#3d7751}.journey-state text:last-child{color:#708278;font-size:32rpx;line-height:1}

.section-heading{display:flex;align-items:flex-end;justify-content:space-between;gap:17rpx;margin:37rpx 27rpx 16rpx}.section-heading>view{display:flex;flex-direction:column}.section-heading>view text:first-child{color:#70907d;font-size:16rpx;font-weight:900;letter-spacing:1.7rpx}.section-heading>view text:last-child{margin-top:4rpx;color:#2b3f35;font-family:"Songti SC","STSong",serif;font-size:31rpx;font-weight:850}.section-heading>text{padding-bottom:3rpx;color:#969e97;font-size:17rpx}.entry-field{display:flex;flex-direction:column;gap:12rpx;margin:0 24rpx}.entry-card{box-sizing:border-box;overflow:hidden;border:1rpx solid #d8e0d9;border-radius:12rpx;background:#fbfcf9}.conversation-entry{position:relative;display:flex;min-height:177rpx;flex-direction:column;justify-content:space-between;padding:20rpx;background:#234d40;color:#fffdf7;box-shadow:0 12rpx 22rpx rgba(38,74,60,.13)}.conversation-entry::after{position:absolute;right:-37rpx;bottom:-54rpx;width:188rpx;height:188rpx;border:1rpx solid rgba(248,209,145,.53);border-radius:53% 47% 45% 55% / 55% 43% 57% 45%;content:"";transform:rotate(-18deg)}.entry-card-top,.entry-card-bottom{position:relative;z-index:1;display:flex;align-items:center;justify-content:space-between;gap:15rpx}.entry-card-top text:first-child{color:#edcb8d;font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:850}.entry-card-top text:last-child{padding:6rpx 9rpx;border:1rpx solid rgba(255,255,255,.21);border-radius:7rpx;color:#c7dacc;font-size:16rpx}.entry-card-bottom>view{display:flex;min-width:0;flex-direction:column;gap:5rpx}.entry-card-bottom text:first-child{color:#fffdf7;font-family:"Songti SC","STSong",serif;font-size:30rpx;font-weight:850}.entry-card-bottom text:last-child{color:#c7d8cc;font-size:18rpx}.entry-arrow{display:grid;place-items:center;width:40rpx;height:40rpx;flex:none;border-radius:50%;background:#ebc481;color:#234d40;font-size:34rpx;font-weight:400;line-height:1}.minor-entry-row{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12rpx}.minor-entry-row .entry-card{position:relative;display:flex;min-width:0;min-height:172rpx;flex-direction:column;justify-content:space-between;padding:18rpx}.minor-entry-row .entry-card>text:first-child{color:#7e9688;font-family:"Songti SC","STSong",serif;font-size:22rpx;font-weight:850}.minor-entry-row .entry-card>view{display:flex;flex-direction:column;gap:5rpx}.minor-entry-row .entry-card>view text:first-child{overflow:hidden;color:#32483c;font-family:"Songti SC","STSong",serif;font-size:24rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.minor-entry-row .entry-card>view text:last-child{overflow:hidden;color:#8a968d;font-size:16rpx;text-overflow:ellipsis;white-space:nowrap}.minor-entry-row .entry-arrow{position:absolute;right:16rpx;bottom:17rpx;width:34rpx;height:34rpx;background:#dfece1;color:#3d735b;font-size:29rpx}.progress-entry{background:#f7f9f5}.works-entry{background:#fff8f2}.works-entry>text:first-child{color:#b67d60!important}.works-entry .entry-arrow{background:#f5e2d5;color:#a26249}

.professional-link{display:flex;align-items:center;justify-content:space-between;gap:17rpx;margin:20rpx 24rpx 0;padding:17rpx 4rpx 17rpx 3rpx;border-top:1rpx solid #dce4dd;color:#466c58}.professional-link>view{display:flex;flex-direction:column;gap:5rpx}.professional-link>view text:first-child{font-size:20rpx;font-weight:850}.professional-link>view text:last-child{color:#87928a;font-size:17rpx}.professional-link>text{font-size:32rpx}.context-link{display:block;margin:23rpx 24rpx 0;color:#74827a;font-size:18rpx;text-align:center}

.bottom-nav{position:fixed;z-index:15;right:0;bottom:0;left:0;display:grid;grid-template-columns:repeat(4,1fr);align-items:center;height:112rpx;padding-bottom:env(safe-area-inset-bottom);box-sizing:content-box;border-top:1rpx solid #dae1dc;background:rgba(250,252,249,.94);backdrop-filter:blur(15rpx)}.bottom-nav view{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:5rpx;color:#929d94;font-size:18rpx}.bottom-nav view text:first-child{font-size:29rpx;line-height:1}.bottom-nav view.active{color:#285d48;font-weight:850}
</style>
