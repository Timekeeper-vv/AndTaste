<template>
  <view class="page">
    <view class="top"><text class="tag">CHOOSE YOUR STUDIO</text><text class="title">先选你的<text>创作方式</text></text><text class="desc">业余模式适合快速做出第一版；专业模式适合提交完整作品包、做评审和生产对接。</text></view>
    <view v-if="activeCampaign" class="campaign-context">
      <view class="campaign-context-head"><view><text>PRIORITY CREATOR TASK</text><text>已选择优先征集</text></view><text>审核通过 +{{ activeCampaign.rewardAmount }} 积分</text></view>
      <text class="campaign-context-title">{{ activeCampaign.title }}</text>
      <text class="campaign-context-copy">面向 {{ activeCampaign.targetName }} · {{ activeCampaign.collectionStyle }}</text>
      <text class="campaign-context-products">推荐：{{ activeCampaign.recommendedProducts.join(' / ') }}</text>
      <text v-if="campaignError" class="campaign-context-error">{{ campaignError }}</text>
    </view>
    <view class="mode-label"><text>第一步 · 创作方式</text><text>后续可在“我的”里随时切换</text></view>
    <view class="mode-grid">
      <view class="mode-choice amateur" :class="{active: creatorMode==='amateur'}" @tap="chooseCreatorMode('amateur')"><text class="mode-icon">聊</text><view><text class="name">业余创作</text><text class="intro">对话式引导、模板选品、快速生图，适合第一次创作</text><text class="mode-features">选产品 · 说灵感 · 自动推荐 · 申请打样</text></view><text class="check">{{ creatorMode==='amateur' ? '✓' : '' }}</text></view>
      <view class="mode-choice professional" :class="{active: creatorMode==='professional'}" @tap="chooseCreatorMode('professional')"><text class="mode-icon">专</text><view><text class="name">专业创作</text><text class="intro">上传专业作品包，补充规格工艺，进入评审与生产流程</text><text class="mode-features">作品包 · 四视图/3D · 版权材料 · 渠道投稿</text></view><text class="check">{{ creatorMode==='professional' ? '✓' : '' }}</text></view>
    </view>
    <view class="mode-label purpose-label"><text>第二步 · 创作去向</text><text>用于确定后续报价和审核流程</text></view>
    <view class="choice" :class="{active: purpose==='personal'}" @tap="choosePurpose('personal')"><text class="icon">✦</text><view><text class="name">个人创作</text><text class="intro">为自己的灵感、作品与生活方式而创作</text></view><text class="check">{{ purpose==='personal' ? '✓' : '' }}</text></view>
    <view class="choice" :class="{active: purpose==='museum_sale'}" @tap="choosePurpose('museum_sale')"><text class="icon">⌘</text><view><text class="name">售卖（景区、博物馆）</text><text class="intro">面向景区文创店、博物馆文创店与文旅渠道售卖</text></view><text class="check">{{ purpose==='museum_sale' ? '✓' : '' }}</text></view>
    <view v-if="purpose==='museum_sale'" class="museum">
      <text class="museum-title">选择售卖渠道</text>
      <picker :range="provinces" :value="provinceIndex" @change="chooseProvince"><view class="picker">{{ province || '选择省 / 直辖市' }}<text>›</text></view></picker>
      <picker :range="museumNames" :value="museumIndex" @change="chooseMuseum" :disabled="!province || !museumNames.length"><view class="picker">{{ museum?.name || '选择该省博物馆或景区' }}<text>›</text></view></picker>
      <text v-if="museum" class="museum-location">{{ museum.city }} · {{ museum.district }} · {{ museum.scene }}</text>
      <view v-if="museum?.recommendation" class="recommendation">
        <view class="recommendation-head"><text>选址策略建议（测试）</text><text>{{ museum.recommendation.badge }}</text></view>
        <view class="metrics"><text>客流潜力：{{ museum.recommendation.trafficLevel }}</text><text>竞争强度：{{ museum.recommendation.competitionLevel }}</text><text>爆款潜力：{{ museum.recommendation.breakoutPotential }}</text></view>
        <text class="advice">优点：{{ museum.recommendation.advantages }}</text>
        <text class="advice">注意：{{ museum.recommendation.risks }}</text>
        <text class="disclaimer">{{ museum.recommendation.disclaimer }}</text>
      </view>
      <text v-if="museum" class="source">审批出处将标注：{{ province }} · {{ museum.name }}</text>
    </view>
    <button class="enter" :disabled="!canEnter" @tap="enter">{{ enterLabel }}</button>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getMuseums, type CreatorCampaign } from '../../api/creative'
import { requireSession } from '../../utils/session'
const creatorMode = ref<'amateur'|'professional'>('amateur')
const purpose = ref<'personal'|'museum_sale'|''>('')
const museums = ref<any[]>([]); const province=ref(''); const museum=ref<any>(null)
const provinceIndex=ref(0); const museumIndex=ref(0)
const activeCampaign = ref<CreatorCampaign | null>(null)
const campaignError = ref('')
const provinces=computed(()=>[...new Set(museums.value.map(x=>x.province))])
const filteredMuseums=computed(()=>museums.value.filter(x=>x.province===province.value))
const museumNames=computed(()=>filteredMuseums.value.map(x=>`${x.name} · ${x.channelType === 'scenic_spot' ? '景区' : '博物馆'}`))
const canEnter=computed(()=>{
  if (activeCampaign.value) return creatorMode.value==='amateur' && purpose.value==='museum_sale' && museum.value?.channelCode===activeCampaign.value.channelCode
  return purpose.value==='personal'||!!museum.value
})
const enterLabel=computed(()=>activeCampaign.value ? '开始优先征集创作' : creatorMode.value === 'professional' ? '进入专业创作' : '进入业余创作')

function campaignFrom(value: any): CreatorCampaign | null {
  if (!value || typeof value !== 'object' || typeof value.key !== 'string' || typeof value.channelCode !== 'string') return null
  return value as CreatorCampaign
}
function setMuseum(next: any) {
  museum.value=next || null
  province.value=next?.province || ''
  provinceIndex.value=Math.max(0, provinces.value.indexOf(province.value))
  museumIndex.value=Math.max(0, filteredMuseums.value.findIndex(item=>String(item.id)===String(next?.id)))
}
function clearCampaign() {
  activeCampaign.value=null
  campaignError.value=''
  uni.removeStorageSync('pending_creator_campaign')
}
function applyCampaignMuseum() {
  const campaign=activeCampaign.value
  if (!campaign) return
  const target=museums.value.find(item=>item.channelCode===campaign.channelCode)
  if (!target) {
    museum.value=null
    campaignError.value='该任务对应渠道目录正在同步，请稍后重试或联系运营。'
    return
  }
  campaignError.value=''
  creatorMode.value='amateur'
  purpose.value='museum_sale'
  setMuseum(target)
}
function chooseCreatorMode(next: 'amateur'|'professional') {
  creatorMode.value=next
  if (next==='professional' && activeCampaign.value) clearCampaign()
}
function choosePurpose(next: 'personal'|'museum_sale') {
  purpose.value=next
  if (next==='personal' && activeCampaign.value) clearCampaign()
}
function chooseProvince(e:any) {
  province.value=provinces.value[e.detail.value]
  provinceIndex.value=e.detail.value
  museum.value=null
  museumIndex.value=0
  if (activeCampaign.value) clearCampaign()
}
function chooseMuseum(e:any) {
  const next=filteredMuseums.value[e.detail.value]
  museum.value=next
  museumIndex.value=e.detail.value
  if (activeCampaign.value && next?.channelCode!==activeCampaign.value.channelCode) clearCampaign()
}
function enter() {
  if(!canEnter.value)return
  uni.setStorageSync('creation_context',{creatorMode:creatorMode.value,purpose:purpose.value,museum:museum.value,campaign:activeCampaign.value})
  uni.removeStorageSync('pending_creator_campaign')
  uni.reLaunch({url:creatorMode.value==='professional'?'/pages/professional/index':'/pages/home/index'})
}
onMounted(async()=>{
  if(!requireSession())return
  const saved=uni.getStorageSync('creation_context')||{}
  const pending=campaignFrom(uni.getStorageSync('pending_creator_campaign'))
  activeCampaign.value=pending||campaignFrom(saved.campaign)
  if(activeCampaign.value){creatorMode.value='amateur';purpose.value='museum_sale'}
  else {
    if(saved.creatorMode==='professional'||saved.creatorMode==='amateur')creatorMode.value=saved.creatorMode
    if(saved.purpose==='personal'||saved.purpose==='museum_sale')purpose.value=saved.purpose
    if(saved.museum)setMuseum(saved.museum)
  }
  try{
    museums.value=await getMuseums()
    if(activeCampaign.value)applyCampaignMuseum()
    else if(museum.value)setMuseum(museums.value.find(item=>String(item.id)===String(museum.value?.id))||museum.value)
  }catch(e:any){uni.showToast({title:e.message||'博物馆目录加载失败',icon:'none'})}
})
</script>
<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;padding:100rpx 34rpx 80rpx;box-sizing:border-box;background:radial-gradient(circle at 10% 5%,rgba(145,163,151,.27),transparent 26%),radial-gradient(circle at 91% 89%,rgba(170,98,76,.15),transparent 29%),linear-gradient(145deg,#f8f4ed,#e9e1d6)}.page::before{content:"";position:absolute;right:-100rpx;top:150rpx;width:390rpx;height:116rpx;border-radius:50%;background:rgba(104,132,118,.1);filter:blur(19rpx);transform:rotate(-13deg)}.top,.choice,.museum,.enter{position:relative;z-index:1}.top{display:flex;flex-direction:column}.tag{color:#5e7d70;font-size:17rpx;font-weight:800;letter-spacing:3rpx}.title{margin-top:21rpx;color:#2d2924;font-family:"Songti SC","STSong",serif;font-size:58rpx;font-weight:700;line-height:1.3}.title text{color:#b9664f}.desc{margin:18rpx 0 33rpx;color:#766d63;font-size:24rpx;line-height:1.7}.choice{display:flex;align-items:center;margin-bottom:14rpx;padding:25rpx;border:1rpx solid rgba(114,96,78,.14);border-radius:21rpx;background:rgba(255,253,249,.77);color:#37312b;box-shadow:0 9rpx 20rpx rgba(73,55,37,.06)}.choice.active{border-color:#9caf9f;background:#eef4ee;color:#405f53}.icon{display:grid;place-items:center;width:58rpx;height:58rpx;border-radius:15rpx;background:#f2eee7;color:#a0604b;font-family:"Songti SC","STSong",serif;font-size:30rpx}.choice.active .icon{background:#607e71;color:#fff}.name,.intro{display:block}.name{font-size:28rpx;font-weight:800}.intro{margin-top:7rpx;color:#7d7369;font-size:20rpx;line-height:1.5}.check{margin-left:auto;color:#5b7a6d;font-size:31rpx}.museum{margin-top:21rpx;padding:25rpx;border:1rpx solid rgba(114,96,78,.14);border-radius:24rpx;background:rgba(255,253,249,.9);box-shadow:0 12rpx 28rpx rgba(73,55,37,.065)}.museum-title{display:block;margin-bottom:13rpx;color:#38322c;font-family:"Songti SC","STSong",serif;font-size:30rpx;font-weight:700}.picker{display:flex;justify-content:space-between;align-items:center;height:82rpx;margin:12rpx 0;padding:0 19rpx;border:1rpx solid #e5ddd2;border-radius:14rpx;background:#faf7f1;color:#554d45;font-size:24rpx}.picker text{color:#ad674f;font-size:33rpx}.recommendation{margin-top:17rpx;padding:17rpx;border:1rpx solid #dce8dd;border-radius:16rpx;background:#eff5ef}.recommendation-head{display:flex;justify-content:space-between;gap:12rpx;color:#58776a;font-size:20rpx;font-weight:800}.recommendation-head text:last-child{padding:4rpx 10rpx;border-radius:99rpx;background:#dceade;font-size:16rpx}.metrics{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:13rpx}.metrics text{padding:6rpx 8rpx;border-radius:8rpx;background:#fffdfa;color:#6c776e;font-size:17rpx}.advice,.disclaimer{display:block;line-height:1.6}.advice{margin-top:12rpx;color:#52675d;font-size:19rpx;font-weight:600}.disclaimer{margin-top:7rpx;color:#8e887f;font-size:16rpx}.source{display:block;margin-top:13rpx;color:#9a6653;font-size:19rpx;line-height:1.6}.enter{height:92rpx;line-height:92rpx;margin-top:30rpx;border-radius:17rpx;background:linear-gradient(135deg,#3d3933,#627f72);color:#fff;font-size:28rpx;font-weight:800;box-shadow:0 12rpx 22rpx rgba(52,58,52,.16)}.enter[disabled]{opacity:.45}
.museum-location{display:block;margin:-3rpx 4rpx 13rpx;color:#81766b;font-size:19rpx;line-height:1.5}
.page{padding-top:74rpx}
.top,.choice,.museum,.enter,.mode-label,.mode-grid{position:relative;z-index:1}
.desc{margin-bottom:25rpx}
.mode-label{display:flex;justify-content:space-between;align-items:baseline;margin:0 4rpx 13rpx;color:#3d3933;font-size:25rpx;font-weight:800}
.mode-label text:last-child{color:#8a8177;font-size:18rpx;font-weight:400}
.mode-grid{display:flex;flex-direction:column;gap:14rpx}
.mode-choice{display:flex;align-items:flex-start;gap:15rpx;padding:23rpx 21rpx;border:1rpx solid rgba(114,96,78,.14);border-radius:21rpx;background:rgba(255,253,249,.8);box-shadow:0 9rpx 20rpx rgba(73,55,37,.06)}
.mode-choice.active{border-color:#88a394;background:#eef5ef;box-shadow:0 9rpx 22rpx rgba(73,105,87,.12)}
.mode-icon{display:grid;place-items:center;flex:none;width:62rpx;height:62rpx;border-radius:17rpx;background:#e8f0e9;color:#537362;font-family:"Songti SC","STSong",serif;font-size:28rpx;font-weight:800}
.professional .mode-icon{background:#f6e9e1;color:#a45f48}
.mode-choice view{flex:1;min-width:0}
.mode-choice .name{display:block;color:#37312b;font-size:29rpx;font-weight:800}
.mode-choice .intro{display:block;margin-top:7rpx;color:#776e64;font-size:20rpx;line-height:1.5}
.mode-features{display:block;margin-top:10rpx;color:#668276;font-size:18rpx;line-height:1.45}
.mode-choice .check{margin-left:auto;color:#5b7a6d;font-size:31rpx}
.purpose-label{margin-top:29rpx}
.campaign-context{position:relative;z-index:1;margin:-2rpx 0 25rpx;padding:19rpx;border:1rpx solid #b9d0bd;border-radius:18rpx;background:#f0f7f0;box-shadow:0 9rpx 19rpx rgba(72,103,82,.08)}.campaign-context-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx}.campaign-context-head view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.campaign-context-head view text:first-child{color:#668170;font-size:15rpx;font-weight:850;letter-spacing:1.6rpx}.campaign-context-head view text:last-child{color:#3e5d4d;font-size:25rpx;font-weight:850}.campaign-context-head>text{flex:none;padding:6rpx 8rpx;border-radius:8rpx;background:#dcebdd;color:#4f765d;font-size:16rpx;font-weight:850}.campaign-context-title{display:block;margin-top:14rpx;color:#393d35;font-family:"Songti SC","STSong",serif;font-size:30rpx;font-weight:700}.campaign-context-copy{display:block;margin-top:7rpx;color:#607368;font-size:20rpx;line-height:1.5}.campaign-context-products{display:block;margin-top:8rpx;color:#8b7463;font-size:18rpx}.campaign-context-error{display:block;margin-top:10rpx;padding:9rpx 10rpx;border-radius:9rpx;background:#fff1ea;color:#a25943;font-size:17rpx;line-height:1.45}
</style>
