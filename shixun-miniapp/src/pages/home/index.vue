<template>
  <view class="page">
    <view class="topbar">
      <view class="brand-lockup" @tap="go('/pages/home/index')">
        <text class="brand-seal">之</text>
        <view><text>之间智造</text><text>AI 文创制作平台</text></view>
      </view>
      <view class="account-chip" @tap="go('/pages/profile/index')">
        <text>{{ user?.username || '浏览创作' }}</text>
        <text>{{ user ? '›' : '登录 ›' }}</text>
      </view>
    </view>

    <view class="hero">
      <view class="hero-copy">
        <text class="hero-label">AI 辅助创作 · 可继续打样生产</text>
        <text class="hero-title">把灵感，做成<br /><text>真的产品。</text></text>
        <text class="hero-desc">从一句想法或一张图片开始，完成产品设计、四视图、3D 原型和生产报价。</text>
        <button class="hero-action" @tap="openConversation(false)"><text class="action-mark">✦</text>开始对话式创作<text>›</text></button>
        <view class="trust-row"><text>AI 生成标识</text><text>工艺方向校验</text><text>人工报价跟进</text></view>
      </view>
      <view class="product-scene" aria-label="文创产品创作示意">
        <view class="scene-label"><text>文创产品工作流</text><text>设计 · 打样 · 生产</text></view>
        <view class="mock-product magnet"><text>纹</text></view>
        <view class="mock-product tag"><text>之</text></view>
        <view class="mock-product card"><text>云</text></view>
        <view class="scene-shadow" />
      </view>
    </view>

    <view v-if="activeConversation" class="resume-card" @tap="openConversation(false)">
      <view class="resume-icon">续</view>
      <view><text>继续上次创作</text><text>{{ conversationStatus }}</text></view>
      <text class="resume-arrow">›</text>
    </view>

    <view class="workflow-section">
      <view class="section-head"><view><text>创作流程</text><text>不需要懂提示词或生产工艺，按步骤完成即可。</text></view><text>自动保存</text></view>
      <view class="workflow-list">
        <view v-for="item in workflow" :key="item.no" class="workflow-item"><text>{{ item.no }}</text><view><text>{{ item.title }}</text><text>{{ item.desc }}</text></view></view>
      </view>
    </view>

    <view class="product-section">
      <view class="section-head"><view><text>想做什么产品？</text><text>先选方向，具体商品和材质会在创作中继续选择。</text></view><text>全部选品</text></view>
      <view class="product-grid">
        <view v-for="item in productGroups" :key="item.title" class="product-card" :class="item.tone" @tap="openConversation(false)">
          <text class="product-mark">{{ item.mark }}</text>
          <view><text>{{ item.title }}</text><text>{{ item.examples }}</text></view>
          <text>›</text>
        </view>
      </view>
    </view>

    <view v-if="featuredReward" class="reward-card" @tap="handleRewardAction(featuredReward)">
      <view class="reward-copy"><text>新手创作任务</text><text>{{ featuredReward.title }}</text><text>{{ featuredReward.description }}</text></view>
      <view class="reward-points"><text>+{{ featuredReward.rewardAmount }}</text><text>积分</text></view>
      <text class="reward-action">{{ rewardActionLabel(featuredReward) }} ›</text>
    </view>

    <view v-if="isProfessional" class="professional-card" @tap="go('/pages/professional/index')">
      <text>专</text><view><text>专业创作工作台</text><text>上传作品包，进入 AI 评审、版权核验与生产对接。</text></view><text>›</text>
    </view>

    <view class="service-section">
      <view class="section-head"><view><text>我的服务</text><text>作品、订单和生产进度都在这里查看。</text></view></view>
      <view class="service-grid">
        <view v-for="item in services" :key="item.title" class="service-item" @tap="item.action"><text>{{ item.mark }}</text><view><text>{{ item.title }}</text><text>{{ item.desc }}</text></view><text>›</text></view>
      </view>
    </view>

    <view class="credit-strip" @tap="go('/pages/recharge/index')"><view><text>可用创作积分</text><text>{{ user ? '用于 AI 生成和 3D 建模' : '登录后查看积分和作品' }}</text></view><text v-if="user" class="credit-value">{{ credits }}</text><text class="credit-action">{{ user ? '充值 ›' : '去登录 ›' }}</text></view>

    <view class="bottom-nav">
      <view class="active" @tap="go('/pages/home/index')"><text>⌂</text><text>首页</text></view>
      <view class="create-nav" @tap="openConversation(false)"><text>✦</text><text>开始创作</text></view>
      <view @tap="go('/pages/works/index')"><text>▣</text><text>作品</text></view>
      <view @tap="go('/pages/profile/index')"><text>◉</text><text>我的</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onShow } from '@dcloudio/uni-app'
import { computed, onMounted, ref } from 'vue'
import { claimRewardMission, getConversations, getCredits, getRewardOverview, type ConversationSession } from '../../api/creative'
import { getSession } from '../../utils/session'

const user = ref(getSession()?.user)
const credits = ref(0)
const rewardOverview = ref<any>({ missions: [] })
const rewardBusy = ref('')
const activeConversation = ref<ConversationSession | null>(null)

const workflow = [
  { no: '01', title: '选择产品方向', desc: '按品类选择商品，也可以让系统推荐。' },
  { no: '02', title: '描述灵感或上传图片', desc: 'AI 把灵感整理成适合生产的产品方案。' },
  { no: '03', title: '生成并继续落地', desc: '不满意可修改，再生成四视图、3D 与报价。' },
]
const productGroups = [
  { mark: '礼', title: '文创纪念', examples: '冰箱贴 · 徽章 · 钥匙扣', tone: 'jade' },
  { mark: '文', title: '文具纸品', examples: '书签 · 明信片 · 贴纸', tone: 'paper' },
  { mark: '用', title: '日用礼赠', examples: '帆布袋 · 马克杯 · 抱枕', tone: 'coral' },
  { mark: '食', title: '食品礼赠', examples: '曲奇 · 巧克力 · 冰淇淋', tone: 'gold' },
  { mark: '玩', title: '潮玩玩具', examples: '毛绒 · 摆件 · 盲盒', tone: 'lavender' },
  { mark: '艺', title: '更多选品', examples: '从选品手册中继续挑选', tone: 'ink' },
]
const services = [
  { mark: '作', title: '我的作品', desc: '查看创作和建模进度', action: () => go('/pages/works/index') },
  { mark: '样', title: '打样与报价', desc: '跟进报价、支付和生产', action: () => go('/pages/works/index') },
  { mark: '单', title: '商城订单', desc: '查看购买订单与状态', action: () => go('/pages/orders/index') },
  { mark: '问', title: '服务咨询', desc: '咨询版权、创作与合作', action: () => go('/pages/support/index?tab=chat') },
]

const rewardMissions = computed<any[]>(() => Array.isArray(rewardOverview.value?.missions) ? rewardOverview.value.missions : [])
const featuredReward = computed(() => rewardMissions.value.find(item => item.key === 'first_image_success') || rewardMissions.value[0] || null)
const isProfessional = computed(() => uni.getStorageSync('creation_context')?.creatorMode === 'professional')
const conversationStatus = computed(() => {
  const events = Array.isArray(activeConversation.value?.events) ? activeConversation.value!.events! : []
  const latest = events[events.length - 1]?.eventType
  const labels: Record<string, string> = {
    mode_selected: '已选择创作方式，等待选择产品', product_selected: '已选择产品，继续补充灵感',
    text_inspiration_submitted: '灵感已保存，继续选择材质', image_inspiration_uploaded: '图片已保存，继续选择材质',
    material_selected: '材质已保存，继续选择风格', creative_direction_confirmed: '方案已整理，可以生成产品图',
    image_generated: '产品图已生成，可继续四视图或 3D', image_refined: '已生成新版本产品图',
    multiview_generated: '四视图已生成，可以继续 3D 建模', model_submitted: '3D 建模任务进行中', model_completed: '3D 模型已完成',
  }
  return labels[String(latest || '')] || '创作进度已自动保存，点击继续'
})

function go(url: string) { uni.navigateTo({ url }) }
function openConversation(forceNew: boolean) { uni.navigateTo({ url: `/pages/conversation-create/index${forceNew ? '?new=1' : ''}` }) }
function rewardActionLabel(mission: any) {
  return mission?.status === 'claimed' ? '已完成' : mission?.status === 'claimable' ? '领取积分' : mission?.key === 'first_image_success' ? '去创作' : '查看任务'
}
async function handleRewardAction(mission: any) {
  if (!mission || mission.status === 'claimed' || rewardBusy.value) return
  if (mission.status === 'claimable') {
    rewardBusy.value = mission.key
    try {
      const result = await claimRewardMission(String(mission.key))
      if (result?.creditAccount?.balance != null) credits.value = Number(result.creditAccount.balance) || 0
      rewardOverview.value = await getRewardOverview()
      uni.showToast({ title: '任务积分已到账', icon: 'success' })
    } catch (error: any) { uni.showToast({ title: error?.message || '领取失败，请稍后重试', icon: 'none' }) }
    finally { rewardBusy.value = '' }
    return
  }
  openConversation(false)
}
async function refreshHome() {
  user.value = getSession()?.user
  if (!user.value) {
    credits.value = 0
    rewardOverview.value = { missions: [] }
    activeConversation.value = null
    return
  }
  try {
    const [credit, rewards, conversations] = await Promise.all([getCredits(), getRewardOverview(), getConversations()])
    credits.value = Number(credit?.balance) || 0
    rewardOverview.value = rewards || { missions: [] }
    activeConversation.value = (Array.isArray(conversations) ? conversations : []).find(item => String(item.status || 'draft') !== 'archived') || null
  } catch {
    // Visitors can still use the page; account data will retry next time it is shown.
  }
}

onMounted(() => { void refreshHome() })
onShow(() => { void refreshHome() })
</script>

<style scoped lang="scss">
.page{min-height:100vh;box-sizing:border-box;padding:0 24rpx 156rpx;background:#f4f6f4;color:#18201d}.topbar{display:flex;align-items:center;justify-content:space-between;padding:22rpx 0 18rpx}.brand-lockup{display:flex;align-items:center;gap:11rpx}.brand-seal{display:grid;place-items:center;width:43rpx;height:43rpx;border:2rpx solid #b44935;border-radius:7rpx;color:#b44935;font-family:"Songti SC","STSong",serif;font-size:28rpx;font-weight:800}.brand-lockup view{display:flex;flex-direction:column;gap:3rpx}.brand-lockup text:first-child{color:#17231e;font-size:26rpx;font-weight:850}.brand-lockup text:last-child{color:#738078;font-size:13rpx}.account-chip{display:flex;align-items:center;gap:7rpx;max-width:260rpx;padding:9rpx 11rpx;border:1rpx solid #d7dfd9;border-radius:9rpx;background:#fff;color:#526158;font-size:16rpx}.account-chip text:first-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.account-chip text:last-child{color:#a64d3c;white-space:nowrap}.hero{display:grid;grid-template-columns:minmax(0,1fr) 218rpx;min-height:422rpx;overflow:hidden;border-radius:10rpx;background:#1e2a25;box-shadow:0 18rpx 36rpx rgba(29,43,36,.18)}.hero-copy{position:relative;z-index:2;display:flex;flex-direction:column;padding:35rpx 0 30rpx 28rpx}.hero-label{color:#b7cbb9;font-size:14rpx;font-weight:750}.hero-title{margin-top:17rpx;color:#fff;font-family:"Songti SC","STSong",serif;font-size:47rpx;font-weight:800;line-height:1.18}.hero-title text{color:#df8c76}.hero-desc{margin-top:14rpx;padding-right:8rpx;color:#c1cbc4;font-size:17rpx;line-height:1.6}.hero-action{display:flex;align-items:center;justify-content:center;gap:9rpx;height:76rpx;margin:22rpx 0 0;border-radius:7rpx;background:#c55540;color:#fff;font-size:21rpx;font-weight:850}.hero-action::after{border:0}.hero-action text:last-child{margin-left:auto;padding-right:5rpx;font-size:33rpx;line-height:1}.action-mark{font-size:24rpx}.trust-row{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:auto;padding-top:15rpx}.trust-row text{padding:6rpx 7rpx;border:1rpx solid rgba(202,218,205,.24);border-radius:5rpx;color:#b8c5bb;font-size:12rpx}.product-scene{position:relative;overflow:hidden;background:linear-gradient(155deg,#345349,#24362f 55%,#1c2924)}.product-scene::before{content:"";position:absolute;right:-72rpx;top:-72rpx;width:290rpx;height:290rpx;border:1rpx solid rgba(255,255,255,.13);border-radius:50%}.product-scene::after{content:"";position:absolute;right:-126rpx;top:-122rpx;width:390rpx;height:390rpx;border:1rpx solid rgba(255,255,255,.09);border-radius:50%}.scene-label{position:absolute;z-index:2;top:31rpx;right:20rpx;display:flex;flex-direction:column;align-items:flex-end;gap:6rpx}.scene-label text:first-child{color:#ebf0e8;font-size:16rpx;font-weight:800}.scene-label text:last-child{color:#b7c9bc;font-size:12rpx}.mock-product{position:absolute;z-index:1;display:grid;place-items:center;box-sizing:border-box;box-shadow:0 14rpx 23rpx rgba(5,15,11,.25)}.mock-product text{font-family:"Songti SC","STSong",serif;font-weight:800}.magnet{right:25rpx;bottom:81rpx;width:111rpx;height:111rpx;border:10rpx solid #bd715b;border-radius:31rpx;background:#d7ddcd;color:#b5513c;font-size:44rpx;transform:rotate(-9deg)}.tag{right:117rpx;bottom:37rpx;width:83rpx;height:127rpx;border:5rpx solid #d8af68;border-radius:43rpx 43rpx 17rpx 17rpx;background:#f0e8d3;color:#526d5d;font-size:37rpx;transform:rotate(17deg)}.card{right:-11rpx;bottom:5rpx;width:121rpx;height:90rpx;border:4rpx solid rgba(255,255,255,.42);border-radius:7rpx;background:linear-gradient(145deg,#bccfbd,#698676);color:#fff;font-size:42rpx;transform:rotate(10deg)}.scene-shadow{position:absolute;right:0;bottom:24rpx;width:244rpx;height:44rpx;border-radius:50%;background:rgba(7,17,13,.4);filter:blur(12rpx)}.resume-card{display:grid;grid-template-columns:56rpx minmax(0,1fr) auto;align-items:center;gap:13rpx;margin-top:18rpx;padding:15rpx;border:1rpx solid #b6cbc0;border-radius:9rpx;background:#e9f1eb}.resume-icon{display:grid;place-items:center;width:53rpx;height:53rpx;border-radius:7rpx;background:#365d4a;color:#fff;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:850}.resume-card view:nth-child(2){display:flex;min-width:0;flex-direction:column;gap:5rpx}.resume-card view:nth-child(2) text:first-child{color:#1f382c;font-size:21rpx;font-weight:850}.resume-card view:nth-child(2) text:last-child{overflow:hidden;color:#62766b;font-size:15rpx;text-overflow:ellipsis;white-space:nowrap}.resume-arrow{color:#b34c38;font-size:34rpx}.workflow-section,.product-section,.service-section{margin-top:28rpx}.section-head{display:flex;align-items:flex-end;justify-content:space-between;gap:12rpx}.section-head>view{display:flex;min-width:0;flex-direction:column;gap:7rpx}.section-head>view text:first-child{color:#1e2b25;font-family:"Songti SC","STSong",serif;font-size:30rpx;font-weight:800}.section-head>view text:last-child{color:#738078;font-size:15rpx;line-height:1.48}.section-head>text{padding-bottom:3rpx;color:#a14d3b;font-size:14rpx;font-weight:800;white-space:nowrap}.workflow-list{display:grid;gap:1rpx;overflow:hidden;margin-top:15rpx;border:1rpx solid #dce3de;border-radius:8rpx;background:#dce3de}.workflow-item{display:grid;grid-template-columns:54rpx minmax(0,1fr);gap:12rpx;align-items:center;min-height:89rpx;padding:13rpx;background:#fff}.workflow-item>text{display:grid;place-items:center;width:43rpx;height:43rpx;border-radius:50%;background:#eef3ef;color:#3c6b53;font-size:16rpx;font-weight:900}.workflow-item view{display:flex;flex-direction:column;gap:4rpx}.workflow-item view text:first-child{color:#26342d;font-size:20rpx;font-weight:850}.workflow-item view text:last-child{color:#7b877f;font-size:14rpx;line-height:1.4}.product-grid{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:15rpx}.product-card{display:grid;grid-template-columns:43rpx minmax(0,1fr) 15rpx;align-items:center;gap:8rpx;min-height:107rpx;padding:13rpx;border:1rpx solid #dce3de;border-radius:8rpx;background:#fff}.product-mark{display:grid;place-items:center;width:41rpx;height:41rpx;border-radius:6rpx;font-family:"Songti SC","STSong",serif;font-size:23rpx;font-weight:850}.product-card view{display:flex;min-width:0;flex-direction:column;gap:5rpx}.product-card view text:first-child{overflow:hidden;color:#26342d;font-size:18rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.product-card view text:last-child{display:-webkit-box;overflow:hidden;color:#7a867e;font-size:12rpx;line-height:1.35;-webkit-box-orient:vertical;-webkit-line-clamp:2}.product-card>text:last-child{color:#a7503e;font-size:28rpx}.product-card.jade .product-mark{background:#e5f0e8;color:#42735a}.product-card.paper .product-mark{background:#f2eadc;color:#8a6a40}.product-card.coral .product-mark{background:#f7e5df;color:#a5503e}.product-card.gold .product-mark{background:#f9efcf;color:#9a6a1f}.product-card.lavender .product-mark{background:#ece8f5;color:#665691}.product-card.ink .product-mark{background:#e5e9e7;color:#3b4a42}.reward-card{position:relative;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:12rpx;min-height:146rpx;margin-top:28rpx;padding:18rpx;border:1rpx solid #e1c9a7;border-radius:9rpx;background:#fff8e9}.reward-copy{display:flex;min-width:0;flex-direction:column;gap:5rpx}.reward-copy text:first-child{color:#a7613e;font-size:14rpx;font-weight:850}.reward-copy text:nth-child(2){overflow:hidden;color:#392f25;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.reward-copy text:last-child{display:-webkit-box;overflow:hidden;color:#826f60;font-size:15rpx;line-height:1.42;-webkit-box-orient:vertical;-webkit-line-clamp:2}.reward-points{display:flex;align-items:center;justify-content:center;align-self:start;min-width:89rpx;min-height:89rpx;flex-direction:column;border-radius:50%;background:#f4dfb8;color:#984426}.reward-points text:first-child{font-family:"Songti SC","STSong",serif;font-size:32rpx;font-weight:900}.reward-points text:last-child{font-size:14rpx;font-weight:850}.reward-action{position:absolute;right:19rpx;bottom:14rpx;color:#a34e3d;font-size:14rpx;font-weight:850}.professional-card{display:grid;grid-template-columns:46rpx minmax(0,1fr) auto;align-items:center;gap:12rpx;margin-top:17rpx;padding:15rpx;border:1rpx solid #cfdcd3;border-radius:8rpx;background:#fff}.professional-card>text:first-child{display:grid;place-items:center;width:44rpx;height:44rpx;border-radius:7rpx;background:#dfece3;color:#376c50;font-family:"Songti SC","STSong",serif;font-size:23rpx;font-weight:850}.professional-card view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.professional-card view text:first-child{color:#22352a;font-size:20rpx;font-weight:850}.professional-card view text:last-child{color:#748078;font-size:14rpx;line-height:1.4}.professional-card>text:last-child{color:#a34e3d;font-size:29rpx}.service-grid{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:15rpx}.service-item{display:grid;grid-template-columns:34rpx minmax(0,1fr) auto;align-items:center;gap:8rpx;min-height:92rpx;padding:12rpx;border:1rpx solid #dce3de;border-radius:8rpx;background:#fff}.service-item>text:first-child{display:grid;place-items:center;width:33rpx;height:33rpx;border-radius:6rpx;background:#edf2ee;color:#456d58;font-family:"Songti SC","STSong",serif;font-size:19rpx;font-weight:850}.service-item view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.service-item view text:first-child{overflow:hidden;color:#2c3a32;font-size:17rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.service-item view text:last-child{display:-webkit-box;overflow:hidden;color:#7c887f;font-size:12rpx;line-height:1.35;-webkit-box-orient:vertical;-webkit-line-clamp:2}.service-item>text:last-child{color:#a24f3d;font-size:25rpx}.credit-strip{display:flex;align-items:center;gap:11rpx;margin-top:28rpx;padding:17rpx;border-top:1rpx solid #d5ddd7;border-bottom:1rpx solid #d5ddd7}.credit-strip view{display:flex;min-width:0;flex:1;flex-direction:column;gap:4rpx}.credit-strip view text:first-child{color:#27362e;font-size:18rpx;font-weight:850}.credit-strip view text:last-child{overflow:hidden;color:#7d8981;font-size:13rpx;text-overflow:ellipsis;white-space:nowrap}.credit-value{color:#1e2c25;font-family:"Songti SC","STSong",serif;font-size:36rpx;font-weight:900}.credit-action{color:#a74e3b;font-size:15rpx;font-weight:850;white-space:nowrap}.bottom-nav{position:fixed;z-index:10;right:0;bottom:0;left:0;display:flex;align-items:flex-end;justify-content:space-around;padding:12rpx 8rpx calc(12rpx + env(safe-area-inset-bottom));border-top:1rpx solid #d6dfd8;background:rgba(255,255,255,.97);box-shadow:0 -8rpx 19rpx rgba(22,35,28,.07)}.bottom-nav view{display:flex;min-width:74rpx;align-items:center;flex-direction:column;gap:4rpx;color:#76837b;font-size:25rpx}.bottom-nav view text:last-child{font-size:14rpx}.bottom-nav .active{color:#376b51}.bottom-nav .create-nav{margin-top:-33rpx;padding:11rpx 15rpx 9rpx;border:4rpx solid #f4f6f4;border-radius:10rpx;background:#c6533f;color:#fff;box-shadow:0 6rpx 13rpx rgba(166,69,51,.24)}.bottom-nav .create-nav text:first-child{font-size:23rpx}.bottom-nav .create-nav text:last-child{font-size:13rpx;font-weight:850}.hero-action:active,.resume-card:active,.product-card:active,.service-item:active,.professional-card:active,.reward-card:active{opacity:.8;transform:translateY(1rpx)}
</style>
