<template>
  <view class="page">
    <view class="page-backdrop">
      <view class="brand-lockup"><text class="brand-seal">之</text><view><text>之间智造</text><text>CREATIVE SETTINGS</text></view></view>
      <view class="backdrop-copy"><text>为这次创作，选一条合适的路径。</text><text>设置完成后会自动带入创作、报价和投稿流程。</text></view>
      <view v-if="savedSummary" class="saved-context"><text>已保存的创作设置</text><text>{{ savedSummary }}</text></view>
    </view>

    <view class="mask" />
    <view class="sheet" :class="{ 'channel-sheet': step === 'channel' }" @tap.stop>
      <view class="sheet-handle" />
      <view class="sheet-header">
        <view>
          <text class="step-label">{{ stepLabel }}</text>
          <text class="sheet-title">{{ stepTitle }}</text>
        </view>
        <text class="later" @tap="leaveLater">稍后设置</text>
      </view>

      <view v-if="step === 'mode'" class="step-content mode-content">
        <text class="sheet-copy">不同模式会匹配对应的创作工具和后续服务。</text>
        <view class="option-list">
          <view class="option-card" :class="{ selected: creatorMode === 'amateur' }" @tap="chooseCreatorMode('amateur')">
            <view class="option-mark amateur-mark">聊</view>
            <view class="option-copy"><text>业余创作</text><text>对话引导，快速把灵感做成第一版作品</text><text>适合初次创作与小批量打样</text></view>
            <text class="option-arrow">›</text>
          </view>
          <view class="option-card" :class="{ selected: creatorMode === 'professional' }" @tap="chooseCreatorMode('professional')">
            <view class="option-mark professional-mark">专</view>
            <view class="option-copy"><text>专业创作</text><text>上传作品包，补充工艺规格并进入评审</text><text>适合设计师、机构与完整项目</text></view>
            <text class="option-arrow">›</text>
          </view>
        </view>
      </view>

      <view v-else-if="step === 'purpose'" class="step-content purpose-content">
        <text class="sheet-copy">你可以先为自己创作，也可以直接面向文旅渠道准备作品。</text>
        <view class="option-list">
          <view class="option-card" :class="{ selected: purpose === 'personal' }" @tap="choosePurpose('personal')">
            <view class="option-mark personal-mark">个</view>
            <view class="option-copy"><text>个人创作</text><text>为自己的灵感、作品和生活方式而创作</text><text>可随时申请打样或补充售卖渠道</text></view>
            <text class="option-arrow">›</text>
          </view>
          <view class="option-card" :class="{ selected: purpose === 'museum_sale' }" @tap="choosePurpose('museum_sale')">
            <view class="option-mark sale-mark">售</view>
            <view class="option-copy"><text>售卖（景区、博物馆）</text><text>按目标渠道准备产品方向、报价与审核材料</text><text>先选一个意向渠道即可开始</text></view>
            <text class="option-arrow">›</text>
          </view>
        </view>
        <view class="sheet-footer"><text class="back-link" @tap="goBack">‹ 返回上一步</text></view>
      </view>

      <view v-else class="channel-step">
        <scroll-view scroll-y class="channel-scroll" :show-scrollbar="false">
          <view class="channel-content">
            <text class="sheet-copy">先从热门渠道中快速选择，或按省份查找目标博物馆、景区。</text>

            <view class="section-title"><text>热门博物馆和景区</text><text>快捷选择</text></view>
            <view v-if="loadingChannels" class="loading-box"><text>正在加载渠道目录...</text></view>
            <view v-else-if="popularMuseums.length" class="popular-grid">
              <view v-for="item in popularMuseums" :key="item.channelCode || item.id" class="popular-card" :class="{ selected: sameMuseum(item, museum) }" @tap="selectMuseum(item)">
                <view class="popular-card-top"><text>{{ channelTypeLabel(item) }}</text><text v-if="sameMuseum(item, museum)">已选</text></view>
                <text class="popular-name">{{ item.name }}</text>
                <text class="popular-region">{{ channelRegion(item) }}</text>
              </view>
            </view>
            <text v-else class="directory-empty">暂时没有可选渠道，请稍后重试。</text>

            <view class="section-title lookup-title"><text>按地区查找</text><text>省份 → 渠道</text></view>
            <picker :range="provinces" :value="provinceIndex" @change="chooseProvince" :disabled="!provinces.length">
              <view class="picker"><view><text>省份</text><text>{{ province || '请选择省份' }}</text></view><text>›</text></view>
            </picker>
            <picker :range="museumNames" :value="museumIndex" @change="chooseMuseum" :disabled="!province || !museumNames.length">
              <view class="picker" :class="{ disabled: !province || !museumNames.length }"><view><text>博物馆或景区</text><text>{{ museum?.name || '请选择渠道' }}</text></view><text>›</text></view>
            </picker>

            <view v-if="museum" class="selected-channel">
              <view><text>当前意向渠道</text><text>{{ museum.name }}</text></view>
              <text>{{ channelTypeLabel(museum) }} · {{ channelRegion(museum) }}</text>
            </view>
            <view v-if="museum?.recommendation" class="recommendation">
              <view><text>渠道建议</text><text>{{ museum.recommendation.badge }}</text></view>
              <text>客流潜力：{{ museum.recommendation.trafficLevel }} · 竞争强度：{{ museum.recommendation.competitionLevel }} · 爆款潜力：{{ museum.recommendation.breakoutPotential }}</text>
              <text>优点：{{ museum.recommendation.advantages }}</text>
              <text>注意：{{ museum.recommendation.risks }}</text>
            </view>
          </view>
        </scroll-view>
        <view class="channel-footer">
          <text class="back-link" @tap="goBack">‹ 返回上一步</text>
          <button class="confirm-button" :disabled="!museum || loadingChannels" @tap="completeChannelChoice">确认并开始创作 <text>›</text></button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getMuseums, type CreatorCampaign } from '../../api/creative'
import { requireSession } from '../../utils/session'

type CreatorMode = 'amateur' | 'professional'
type Purpose = 'personal' | 'museum_sale' | ''
type Step = 'mode' | 'purpose' | 'channel'

const POPULAR_CHANNEL_CODES = [
  'museum-palace',
  'museum-national',
  'museum-shanghai',
  'museum-suzhou',
  'museum-shaanxi-history',
  'museum-hunan',
  'catalog-west-lake',
  'catalog-huangshan-mountain',
  'catalog-zhangjiajie',
  'catalog-jiuzhaigou',
]

const step = ref<Step>('mode')
const creatorMode = ref<CreatorMode>('amateur')
const purpose = ref<Purpose>('')
const museums = ref<any[]>([])
const province = ref('')
const museum = ref<any>(null)
const provinceIndex = ref(0)
const museumIndex = ref(0)
const loadingChannels = ref(true)
const preferredCampaign = ref<CreatorCampaign | null>(null)

const provinces = computed(() => [...new Set(museums.value.map(item => String(item.province || '').trim()).filter(Boolean))])
const filteredMuseums = computed(() => museums.value.filter(item => item.province === province.value))
const museumNames = computed(() => filteredMuseums.value.map(item => `${item.name} · ${channelTypeLabel(item)}`))
const popularMuseums = computed(() => {
  const byCode = new Map(museums.value.map(item => [item.channelCode, item]))
  const listed = POPULAR_CHANNEL_CODES.map(code => byCode.get(code)).filter(Boolean)
  const listedCodes = new Set(listed.map((item: any) => item.channelCode))
  const fallback = museums.value.filter(item => !listedCodes.has(item.channelCode)).slice(0, Math.max(0, 10 - listed.length))
  return [...listed, ...fallback].slice(0, 10)
})
const stepLabel = computed(() => step.value === 'mode' ? '第 1 步 / 3' : step.value === 'purpose' ? '第 2 步 / 3' : '第 3 步 / 3')
const stepTitle = computed(() => step.value === 'mode' ? '你想怎样创作？' : step.value === 'purpose' ? '这次创作准备做什么？' : '选择意向售卖渠道')
const savedSummary = computed(() => {
  const saved = uni.getStorageSync('creation_context') || {}
  if (!saved.creatorMode && !saved.purpose) return ''
  const mode = saved.creatorMode === 'professional' ? '专业创作' : '业余创作'
  if (saved.purpose === 'museum_sale' && saved.museum?.name) return `${mode} · 售卖至 ${saved.museum.name}`
  return `${mode} · 个人创作`
})

function campaignFrom(value: any): CreatorCampaign | null {
  if (!value || typeof value !== 'object' || typeof value.key !== 'string' || typeof value.channelCode !== 'string') return null
  return value as CreatorCampaign
}

function channelTypeLabel(item: any) {
  return item?.channelType === 'scenic_spot' ? '景区' : '博物馆'
}

function channelRegion(item: any) {
  return [item?.province, item?.city].filter(Boolean).join(' · ') || '全国渠道目录'
}

function sameMuseum(left: any, right: any) {
  if (!left || !right) return false
  if (left.channelCode && right.channelCode) return left.channelCode === right.channelCode
  if (left.id == null || right.id == null) return false
  return String(left.id) === String(right.id)
}

function setMuseum(next: any) {
  museum.value = next || null
  province.value = next?.province || ''
  provinceIndex.value = Math.max(0, provinces.value.indexOf(province.value))
  museumIndex.value = Math.max(0, filteredMuseums.value.findIndex(item => sameMuseum(item, next)))
}

function chooseCreatorMode(next: CreatorMode) {
  creatorMode.value = next
  if (next === 'professional') preferredCampaign.value = null
  step.value = 'purpose'
}

function choosePurpose(next: Exclude<Purpose, ''>) {
  purpose.value = next
  if (next === 'personal') {
    museum.value = null
    preferredCampaign.value = null
    completeSelection()
    return
  }
  step.value = 'channel'
}

function chooseProvince(event: any) {
  province.value = provinces.value[Number(event.detail.value)] || ''
  provinceIndex.value = Number(event.detail.value) || 0
  museum.value = null
  museumIndex.value = 0
}

function chooseMuseum(event: any) {
  const next = filteredMuseums.value[Number(event.detail.value)]
  if (next) setMuseum(next)
}

function selectMuseum(next: any) {
  setMuseum(next)
}

function goBack() {
  step.value = step.value === 'channel' ? 'purpose' : 'mode'
}

function leaveLater() {
  uni.reLaunch({ url: '/pages/home/index' })
}

function completeChannelChoice() {
  if (!museum.value) return
  completeSelection()
}

function completeSelection() {
  const campaign = preferredCampaign.value && museum.value?.channelCode === preferredCampaign.value.channelCode
    ? preferredCampaign.value
    : null
  uni.setStorageSync('creation_context', {
    creatorMode: creatorMode.value,
    purpose: purpose.value,
    museum: museum.value,
    ...(campaign ? { campaign } : {}),
  })
  uni.removeStorageSync('pending_creator_campaign')
  uni.reLaunch({ url: creatorMode.value === 'professional' ? '/pages/professional/index' : '/pages/home/index' })
}

onMounted(async () => {
  if (!requireSession()) return
  const saved = uni.getStorageSync('creation_context') || {}
  const pending = campaignFrom(uni.getStorageSync('pending_creator_campaign'))
  preferredCampaign.value = pending
  if (saved.creatorMode === 'professional' || saved.creatorMode === 'amateur') creatorMode.value = saved.creatorMode
  if (saved.purpose === 'personal' || saved.purpose === 'museum_sale') purpose.value = saved.purpose

  try {
    museums.value = await getMuseums()
    const target = pending
      ? museums.value.find(item => item.channelCode === pending.channelCode)
      : saved.museum && museums.value.find(item => sameMuseum(item, saved.museum))
    if (target) setMuseum(target)
  } catch (error: any) {
    uni.showToast({ title: error?.message || '渠道目录加载失败', icon: 'none' })
  } finally {
    loadingChannels.value = false
  }
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;background:#f4f0e9;color:#302d28}
.page-backdrop{display:flex;min-height:100vh;box-sizing:border-box;flex-direction:column;padding:90rpx 42rpx;background:radial-gradient(ellipse at 10% 4%,rgba(127,157,141,.23),transparent 30%),radial-gradient(ellipse at 90% 87%,rgba(188,105,77,.15),transparent 31%),linear-gradient(150deg,#faf7f1,#eae2d7)}
.brand-lockup{display:flex;align-items:center;gap:15rpx;color:#365548}.brand-seal{display:grid;place-items:center;width:54rpx;height:54rpx;border-radius:15rpx;background:#416a59;color:#fffaf4;font-family:"Songti SC","STSong",serif;font-size:31rpx;font-weight:800}.brand-lockup view{display:flex;flex-direction:column;gap:4rpx}.brand-lockup view text:first-child{font-family:"Songti SC","STSong",serif;font-size:31rpx;font-weight:800}.brand-lockup view text:last-child{color:#799184;font-size:15rpx;font-weight:800;letter-spacing:2rpx}
.backdrop-copy{display:flex;flex-direction:column;gap:13rpx;margin-top:78rpx}.backdrop-copy text:first-child{color:#3a3934;font-family:"Songti SC","STSong",serif;font-size:47rpx;font-weight:700;line-height:1.35}.backdrop-copy text:last-child{color:#81786e;font-size:23rpx;line-height:1.65}.saved-context{display:flex;flex-direction:column;gap:8rpx;margin-top:42rpx;padding:20rpx;border-left:4rpx solid #b26a52;background:rgba(255,253,249,.49);color:#927467}.saved-context text:first-child{font-size:18rpx;font-weight:800}.saved-context text:last-child{color:#5f625a;font-size:23rpx}
.mask{position:absolute;inset:0;background:rgba(41,42,37,.30);backdrop-filter:blur(2rpx)}
.sheet{position:absolute;right:0;bottom:0;left:0;display:flex;max-height:calc(100vh - 88rpx);box-sizing:border-box;flex-direction:column;border-radius:30rpx 30rpx 0 0;background:#fffdfa;box-shadow:0 -18rpx 45rpx rgba(43,39,33,.16)}.sheet.channel-sheet{height:calc(100vh - 88rpx)}
.sheet-handle{width:64rpx;height:7rpx;flex:none;margin:16rpx auto 0;border-radius:99rpx;background:#dbd4ca}.sheet-header{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;padding:24rpx 32rpx 20rpx}.sheet-header view{display:flex;min-width:0;flex-direction:column;gap:7rpx}.step-label{color:#789184;font-size:18rpx;font-weight:850;letter-spacing:1.3rpx}.sheet-title{color:#312f2a;font-family:"Songti SC","STSong",serif;font-size:38rpx;font-weight:800;line-height:1.26}.later{flex:none;padding:9rpx 3rpx;color:#72867b;font-size:21rpx;font-weight:750}
.step-content{padding:0 32rpx 42rpx}.sheet-copy{display:block;color:#7d756c;font-size:22rpx;line-height:1.6}.option-list{display:flex;flex-direction:column;gap:16rpx;margin-top:28rpx}.option-card{display:flex;align-items:center;gap:17rpx;padding:22rpx 19rpx;border:1rpx solid #e4ddd3;border-radius:16rpx;background:#fffefa}.option-card.selected{border-color:#88a493;background:#f1f7f1;box-shadow:0 8rpx 18rpx rgba(82,110,92,.10)}.option-mark{display:grid;place-items:center;flex:none;width:60rpx;height:60rpx;border-radius:14rpx;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:850}.amateur-mark{background:#e4f0e7;color:#47745e}.professional-mark{background:#f8e9e1;color:#a35e49}.personal-mark{background:#f3eee7;color:#956650}.sale-mark{background:#e6eee8;color:#4d7361}.option-copy{display:flex;min-width:0;flex:1;flex-direction:column;gap:5rpx}.option-copy text:first-child{color:#3d3933;font-size:29rpx;font-weight:850}.option-copy text:nth-child(2){color:#766f67;font-size:20rpx;line-height:1.45}.option-copy text:last-child{color:#829487;font-size:18rpx;line-height:1.35}.option-arrow{flex:none;color:#a96751;font-size:38rpx;line-height:1}.sheet-footer{padding-top:26rpx}.back-link{display:inline-block;padding:12rpx 0;color:#668074;font-size:22rpx;font-weight:800}
.channel-step{display:flex;min-height:0;flex:1;flex-direction:column}.channel-scroll{min-height:0;flex:1}.channel-content{padding:0 32rpx 22rpx}.section-title{display:flex;align-items:baseline;justify-content:space-between;gap:16rpx;margin-top:27rpx;color:#3d3933;font-family:"Songti SC","STSong",serif;font-size:28rpx;font-weight:800}.section-title text:last-child{color:#8a978e;font-family:inherit;font-size:18rpx;font-weight:700}.popular-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12rpx;margin-top:16rpx}.popular-card{display:flex;min-width:0;min-height:140rpx;box-sizing:border-box;flex-direction:column;padding:15rpx;border:1rpx solid #e3dcd3;border-radius:13rpx;background:#fffefa}.popular-card.selected{border-color:#759584;background:#eff6ef;box-shadow:0 6rpx 15rpx rgba(76,111,88,.10)}.popular-card-top{display:flex;justify-content:space-between;gap:8rpx}.popular-card-top text{padding:4rpx 7rpx;border-radius:6rpx;background:#f0eee7;color:#7a756d;font-size:16rpx;font-weight:800}.popular-card.selected .popular-card-top text:first-child{background:#dcebdd;color:#527763}.popular-card-top text:last-child{background:#668977;color:#fff}.popular-name{display:-webkit-box;overflow:hidden;margin-top:12rpx;color:#423d36;font-size:23rpx;font-weight:800;line-height:1.35;-webkit-box-orient:vertical;-webkit-line-clamp:2}.popular-region{display:block;margin-top:auto;padding-top:8rpx;color:#91877b;font-size:17rpx;line-height:1.35}.loading-box,.directory-empty{display:block;margin-top:16rpx;padding:28rpx 16rpx;border:1rpx dashed #d8d1c8;border-radius:13rpx;background:#faf8f3;color:#8e857b;text-align:center;font-size:20rpx}.lookup-title{margin-top:31rpx}.picker{display:flex;align-items:center;justify-content:space-between;min-height:88rpx;box-sizing:border-box;margin-top:13rpx;padding:14rpx 18rpx;border:1rpx solid #e3dcd3;border-radius:13rpx;background:#fffefa}.picker.disabled{opacity:.5}.picker view{display:flex;min-width:0;flex:1;flex-direction:column;gap:5rpx}.picker view text:first-child{color:#9a9086;font-size:17rpx}.picker view text:last-child{overflow:hidden;color:#4b463f;font-size:23rpx;text-overflow:ellipsis;white-space:nowrap}.picker>text{margin-left:14rpx;color:#a96751;font-size:35rpx}.selected-channel{display:flex;align-items:flex-end;justify-content:space-between;gap:15rpx;margin-top:20rpx;padding:17rpx;border:1rpx solid #cadccf;border-radius:13rpx;background:#f0f7f0}.selected-channel view{display:flex;min-width:0;flex:1;flex-direction:column;gap:5rpx}.selected-channel view text:first-child{color:#668271;font-size:17rpx;font-weight:800}.selected-channel view text:last-child{overflow:hidden;color:#3e5c4d;font-size:25rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.selected-channel>text{flex:none;max-width:42%;color:#71847a;font-size:17rpx;text-align:right;line-height:1.45}.recommendation{display:flex;flex-direction:column;gap:8rpx;margin-top:14rpx;padding:16rpx;border-radius:13rpx;background:#f5f6f1;color:#68756d;font-size:18rpx;line-height:1.5}.recommendation view{display:flex;align-items:center;justify-content:space-between;gap:10rpx;color:#5c7869;font-size:20rpx;font-weight:850}.recommendation view text:last-child{padding:4rpx 8rpx;border-radius:6rpx;background:#e1ece1;font-size:16rpx}.channel-footer{display:flex;align-items:center;gap:18rpx;padding:14rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));border-top:1rpx solid #eee8df;background:#fffdfa}.channel-footer .back-link{flex:none}.confirm-button{flex:1;height:82rpx;line-height:82rpx;margin:0;border-radius:13rpx;background:#4e7765;color:#fff;font-size:23rpx;font-weight:850}.confirm-button text{margin-left:6rpx;font-size:29rpx}.confirm-button[disabled]{background:#c8d2ca;color:#f4f6f2}
</style>
