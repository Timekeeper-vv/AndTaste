<template>
  <view class="page">
    <view class="wash wash-one" /><view class="wash wash-two" />
    <view class="hero">
      <text class="eyebrow">SELECTION ATELIER</text>
      <text class="title">灵感选品</text>
      <text class="sub">从文化主题到可落地的商品方向，先选一个值得试做的起点。</text>
    </view>

    <view class="filter-card">
      <view class="filter-head"><view><text>先告诉我们你的方向</text><text>推荐会结合预算、受众和场景排序</text></view><text class="version">资料版次 {{ version }}</text></view>
      <input class="theme-input" :value="filters.theme" maxlength="40" placeholder="例如：青铜器、地方守护兽、青绿山水" @input="updateTheme" />
      <view class="filter-label"><text>预算上限</text><text>建议零售价参考</text></view>
      <scroll-view scroll-x class="chip-scroll" :show-scrollbar="false"><view class="chips"><text v-for="item in budgetOptions" :key="item.value" class="chip" :class="{ active: filters.budgetMax === item.value }" @tap="filters.budgetMax = item.value">{{ item.label }}</text></view></scroll-view>
      <view class="filter-label"><text>主要受众</text><text>可不选</text></view>
      <scroll-view scroll-x class="chip-scroll" :show-scrollbar="false"><view class="chips"><text v-for="item in audienceOptions" :key="item" class="chip" :class="{ active: filters.audience === item }" @tap="filters.audience = filters.audience === item ? '' : item">{{ item }}</text></view></scroll-view>
      <view class="filter-label"><text>使用场景</text><text>可不选</text></view>
      <scroll-view scroll-x class="chip-scroll" :show-scrollbar="false"><view class="chips"><text v-for="item in occasionOptions" :key="item" class="chip" :class="{ active: filters.occasion === item }" @tap="filters.occasion = filters.occasion === item ? '' : item">{{ item }}</text></view></scroll-view>
      <button class="recommend-button" :loading="loading" @tap="loadRecommendations">{{ loading ? '正在匹配' : '生成选品建议' }}<text>›</text></button>
    </view>

    <view class="category-strip"><text class="category-caption">品类</text><text class="category-chip" :class="{ active: !filters.category }" @tap="filters.category = ''">全部</text><text v-for="item in categories" :key="item.categoryKey" class="category-chip" :class="{ active: filters.category === item.categoryKey }" @tap="filters.category = item.categoryKey">{{ item.name }}</text></view>

    <view class="result-head"><view><text class="eyebrow">RECOMMENDED DIRECTIONS</text><text class="result-title">适合先做的方向</text></view><text>{{ options.length }} 个建议</text></view>
    <view v-if="loading && !options.length" class="empty">正在读取选品知识库…</view>
    <view v-else-if="!options.length" class="empty">暂时没有匹配结果，放宽预算或换一个主题试试。</view>
    <view v-else class="option-list">
      <view v-for="option in options" :key="option.optionKey" class="option-card">
        <image v-if="coverUrl(option)" class="cover" :src="coverUrl(option)" mode="aspectFill" />
        <view v-else class="cover fallback"><text>{{ option.name.slice(0, 1) }}</text><text>{{ option.categoryName }}</text></view>
        <view class="option-body">
          <view class="option-title-row"><view><text class="option-name">{{ option.name }}</text><text class="option-subtitle">{{ option.subtitle }}</text></view><text class="score">{{ option.matchScore || 0 }} 分</text></view>
          <text class="reason">{{ option.reason }}</text>
          <text class="description">{{ option.description }}</text>
          <view class="meta-grid"><view><text>工艺</text><text>{{ option.material }}</text></view><view><text>参考零售价</text><text>{{ option.retailDisplay }}</text></view><view><text>打样</text><text>{{ option.sampleLeadTime }}</text></view><view><text>大货</text><text>{{ option.bulkLeadTime }}</text></view></view>
          <text class="planning-note">{{ option.planningNote }}</text>
          <text class="source-note">手册案例图 · 版权状态：{{ option.imageRightsStatus === 'pending_review' ? '待核验' : '已审核' }}</text>
          <view class="action-row"><button size="mini" class="favorite" @tap="toggleFavorite(option)">{{ option.favorited ? '已收藏' : '收藏' }}</button><button size="mini" class="make" @tap="makeProduct(option)">带入创作</button><button size="mini" class="demand" @tap="openDemand(option)">提交需求</button></view>
        </view>
      </view>
    </view>

    <view class="disclaimer">{{ disclaimer }}</view>

    <view v-if="demandOption" class="demand-panel">
      <view class="demand-head"><view><text class="eyebrow">PRODUCT REQUEST</text><text>我想把它做成商品</text></view><text class="close" @tap="demandOption = null">×</text></view>
      <text class="demand-selected">{{ demandOption.name }} · {{ demandOption.subtitle }}</text>
      <textarea class="demand-input" :value="demandNote" maxlength="500" placeholder="补充文化主题、数量、期望时间或想保留的设计元素" @input="updateDemandNote" />
      <button class="demand-submit" :loading="demandLoading" @tap="submitDemand">{{ demandLoading ? '正在提交' : '提交商品化需求' }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import { reactive, ref } from 'vue'
import { createSelectionDemand, getSelectionCategories, getSelectionRecommendations, addSelectionFavorite, removeSelectionFavorite, type SelectionFilters, type SelectionOption } from '../../api/selection'
import { apiUrl } from '../../api/client'
import { requireSession } from '../../utils/session'

const categories = ref<any[]>([])
const options = ref<SelectionOption[]>([])
const loading = ref(false)
const demandLoading = ref(false)
const demandOption = ref<SelectionOption | null>(null)
const demandNote = ref('')
const assetId = ref('')
const version = ref('2023')
const disclaimer = ref('资料来源于 2023 年选品手册，仅用于方向筛选。价格、工期、资质和可生产性需在正式打样前重新确认。')
const filters = reactive<SelectionFilters>({ theme: '', audience: '', occasion: '', budgetMax: '', category: '', size: 6 })
const budgetOptions = [{ label: '不限', value: '' }, { label: '50 元内', value: 50 }, { label: '100 元内', value: 100 }, { label: '200 元内', value: 200 }, { label: '300 元内', value: 300 }]
const audienceOptions = ['年轻游客', '亲子家庭', '文博爱好者', '办公人群', '机构客户']
const occasionOptions = ['伴手礼', '节日礼赠', '旅行打卡', '日常使用', '展览纪念']

onLoad((query: any) => {
  if (!requireSession()) return
  assetId.value = String(query?.assetId || '')
  void loadKnowledge()
})

onPullDownRefresh(async () => {
  await loadRecommendations()
  uni.stopPullDownRefresh()
})

async function loadKnowledge() {
  try {
    categories.value = await getSelectionCategories()
    await loadRecommendations()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '选品知识库暂不可用', icon: 'none' })
  }
}

async function loadRecommendations() {
  if (loading.value) return
  loading.value = true
  try {
    const result = await getSelectionRecommendations({ ...filters, assetId: assetId.value || undefined })
    options.value = Array.isArray(result?.options) ? result.options : []
    version.value = result?.version || '2023'
    disclaimer.value = result?.disclaimer || disclaimer.value
  } catch (error: any) {
    uni.showToast({ title: error?.message || '推荐暂不可用，请稍后重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

function updateTheme(event: any) { filters.theme = String(event?.detail?.value || '') }
function updateDemandNote(event: any) { demandNote.value = String(event?.detail?.value || '') }

async function toggleFavorite(option: SelectionOption) {
  try {
    if (option.favorited) await removeSelectionFavorite(option.optionKey)
    else await addSelectionFavorite(option.optionKey)
    option.favorited = !option.favorited
    uni.showToast({ title: option.favorited ? '已收藏' : '已取消收藏', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '收藏操作失败', icon: 'none' })
  }
}

function coverUrl(option: SelectionOption) {
  const value = String(option.coverImageUrl || '')
  return value.startsWith('http') ? value : value ? apiUrl(value) : ''
}

function productKeyFor(option: SelectionOption) {
  if (option.name.includes('毛绒')) return 'plush'
  if (option.name.includes('公仔')) return 'pvc_figure'
  if (option.name.includes('钥匙扣') || option.name.includes('徽章') || option.name.includes('吊坠')) return 'keychain'
  if (option.name.includes('礼盒')) return 'gift_box'
  return 'magnet'
}

function materialFor(option: SelectionOption) {
  const text = `${option.material} ${option.process}`
  if (text.includes('陶瓷')) return { name: '陶瓷釉面', modelMaterial: '陶瓷釉面' }
  if (text.includes('毛绒') || text.includes('水晶超柔') || text.includes('布艺')) return { name: '全毛绒', modelMaterial: '全毛绒' }
  if (text.includes('PVC')) return { name: 'PVC', modelMaterial: 'PVC 潮玩' }
  if (text.includes('亚克力')) return { name: '亚克力', modelMaterial: '透明亚克力' }
  if (text.includes('木')) return { name: '木质', modelMaterial: '木质温润' }
  if (text.includes('纸')) return { name: '纸质', modelMaterial: '纸质礼盒' }
  if (text.includes('金属') || text.includes('合金')) return { name: '金属', modelMaterial: '金属质感' }
  return { name: 'PVC', modelMaterial: 'PVC 潮玩' }
}

function makeProduct(option: SelectionOption) {
  const material = materialFor(option)
  const prompt = `以${filters.theme || '地方文化与馆藏纹样'}为主题，设计一款${option.name}。${option.description}建议材质：${option.material}；建议工艺：${option.process}；规格参考：${option.specification}。请保留文化符号的识别度，形成适合${filters.audience || '年轻游客和文创爱好者'}的商品视觉。`
  uni.setStorageSync('miniapp_atelier_draft', { mode: 'image', title: `${filters.theme || '文化主题'} · ${option.name}`, prompt, productKey: productKeyFor(option), material: material.name, modelMaterial: material.modelMaterial })
  uni.navigateTo({ url: '/pages/create/index?mode=image' })
}

function openDemand(option: SelectionOption) {
  demandOption.value = option
  demandNote.value = ''
}

async function submitDemand() {
  if (!demandOption.value || demandLoading.value) return
  demandLoading.value = true
  try {
    const result = await createSelectionDemand({ optionKey: demandOption.value.optionKey, assetId: assetId.value || undefined, theme: filters.theme, budgetMax: filters.budgetMax, audience: filters.audience, occasion: filters.occasion, note: demandNote.value })
    demandOption.value = null
    uni.showModal({ title: '需求已提交', content: `${result?.message || '商品化需求已提交'}\n需求编号：${result?.requestNo || '-'}`, showCancel: false })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '提交失败，请稍后重试', icon: 'none' })
  } finally {
    demandLoading.value = false
  }
}
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;box-sizing:border-box;overflow:hidden;padding:30rpx 28rpx 70rpx;background:linear-gradient(160deg,#fbf8f2,#f0e8de 58%,#edf3ed);color:#302b26}.wash{position:absolute;pointer-events:none;border-radius:50%;filter:blur(12rpx)}.wash-one{top:120rpx;right:-160rpx;width:400rpx;height:210rpx;background:rgba(113,146,128,.14)}.wash-two{bottom:360rpx;left:-160rpx;width:380rpx;height:180rpx;background:rgba(183,102,78,.09)}.hero,.filter-card,.category-strip,.result-head,.option-list,.empty,.disclaimer,.demand-panel{position:relative;z-index:1}.hero{padding:14rpx 7rpx 25rpx}.eyebrow{display:block;color:#668575;font-size:16rpx;font-weight:900;letter-spacing:2.4rpx}.title{display:block;margin-top:12rpx;color:#3a342e;font-family:"Songti SC","STSong",serif;font-size:52rpx;font-weight:800;line-height:1.15}.sub{display:block;max-width:620rpx;margin-top:13rpx;color:#7c7167;font-size:20rpx;line-height:1.6}.filter-card{padding:19rpx;border:1rpx solid rgba(128,112,93,.16);border-radius:23rpx;background:rgba(255,253,249,.86);box-shadow:0 14rpx 30rpx rgba(67,53,37,.07)}.filter-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx}.filter-head view{display:flex;flex-direction:column;gap:5rpx}.filter-head view text:first-child{color:#4d463f;font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:800}.filter-head view text:last-child{color:#9a8e83;font-size:15rpx}.version{padding:6rpx 8rpx;border-radius:9rpx;background:#eef4ee;color:#638171;font-size:13rpx;font-weight:800}.theme-input{box-sizing:border-box;width:100%;height:78rpx;margin-top:16rpx;padding:0 14rpx;border:1rpx solid #e1d8cc;border-radius:13rpx;background:#fbf8f2;color:#4b443d;font-size:20rpx}.filter-label{display:flex;justify-content:space-between;gap:8rpx;margin-top:15rpx;color:#665e55;font-size:16rpx;font-weight:800}.filter-label text:last-child{color:#a1968a;font-size:13rpx;font-weight:500}.chip-scroll{margin-top:8rpx;white-space:nowrap}.chips{display:flex;gap:7rpx}.chip,.category-chip{display:inline-block;padding:8rpx 10rpx;border:1rpx solid #e5ddd3;border-radius:10rpx;background:#fffdfa;color:#82776c;font-size:15rpx;white-space:nowrap}.chip.active,.category-chip.active{border-color:#678878;background:#eaf2e9;color:#4e705e;font-weight:800}.recommend-button{display:flex;align-items:center;justify-content:center;gap:8rpx;width:100%;height:78rpx;margin:18rpx 0 0;border-radius:15rpx;background:linear-gradient(135deg,#423b34,#658274);color:#fff;font-size:22rpx;font-weight:850;box-shadow:0 10rpx 20rpx rgba(68,79,65,.16)}.recommend-button text{color:#e9c5a5;font-size:28rpx}.recommend-button[loading]{opacity:.66}.category-strip{display:flex;gap:7rpx;align-items:center;overflow-x:auto;margin-top:19rpx;padding-bottom:2rpx;white-space:nowrap}.category-caption{color:#9b8f84;font-size:15rpx}.category-chip{font-size:14rpx}.result-head{display:flex;align-items:flex-end;justify-content:space-between;gap:10rpx;margin:27rpx 5rpx 13rpx}.result-head view{display:flex;flex-direction:column;gap:5rpx}.result-title{color:#443c34;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.result-head>text:last-child{color:#9b8e82;font-size:15rpx}.empty{padding:64rpx 20rpx;border:1rpx dashed #d5cabb;border-radius:20rpx;background:rgba(255,253,249,.66);color:#8e8277;font-size:19rpx;text-align:center}.option-list{display:grid;gap:14rpx}.option-card{overflow:hidden;border:1rpx solid rgba(130,113,94,.15);border-radius:22rpx;background:rgba(255,254,250,.93);box-shadow:0 11rpx 25rpx rgba(69,54,39,.06)}.cover{display:block;width:100%;height:265rpx;background:#e8e1d7}.cover.fallback{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:7rpx;background:linear-gradient(145deg,#dce7dd,#efdfd0);color:#567566}.cover.fallback text:first-child{font-family:"Songti SC","STSong",serif;font-size:72rpx;font-weight:800}.cover.fallback text:last-child{font-size:16rpx}.option-body{padding:17rpx}.option-title-row{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx}.option-title-row view{display:flex;min-width:0;flex-direction:column;gap:5rpx}.option-name{color:#403931;font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:800}.option-subtitle{color:#95745f;font-size:15rpx;font-weight:800}.score{flex:0 0 auto;padding:6rpx 8rpx;border-radius:9rpx;background:#f6eee3;color:#ad674c;font-size:14rpx;font-weight:900}.reason{display:block;margin-top:10rpx;color:#587768;font-size:17rpx;font-weight:800;line-height:1.5}.description{display:block;margin-top:7rpx;color:#7b7167;font-size:17rpx;line-height:1.55}.meta-grid{display:grid;grid-template-columns:1fr 1fr;gap:8rpx;margin-top:14rpx;padding:10rpx;border-radius:14rpx;background:#f7f3ed}.meta-grid view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.meta-grid text:first-child{color:#a0968b;font-size:13rpx}.meta-grid text:last-child{overflow:hidden;color:#554d45;font-size:15rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.planning-note{display:block;margin-top:10rpx;padding:9rpx 10rpx;border-left:3rpx solid #b68068;border-radius:0 9rpx 9rpx 0;background:#fbf2eb;color:#806f62;font-size:14rpx;line-height:1.45}.source-note{display:block;margin-top:8rpx;color:#aa9d91;font-size:12rpx;line-height:1.4}.action-row{display:flex;gap:7rpx;margin-top:13rpx}.action-row button{flex:1;height:58rpx;margin:0;padding:0 6rpx;border-radius:10rpx;font-size:14rpx}.action-row button::after{border:0}.favorite{border:1rpx solid #d8e3d8;background:#f5faf4;color:#587765}.make{background:#5e7c6f;color:#fff}.demand{border:1rpx solid #e2cdbd;background:#fff8ef;color:#a5644a}.disclaimer{margin:18rpx 5rpx 0;color:#9a8f83;font-size:14rpx;line-height:1.55;text-align:center}.demand-panel{margin-top:20rpx;padding:18rpx;border:1rpx solid #d6c6b6;border-radius:20rpx;background:#fffaf2;box-shadow:0 10rpx 22rpx rgba(90,66,45,.08)}.demand-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx}.demand-head view{display:flex;flex-direction:column;gap:5rpx}.demand-head view text:last-child{color:#483e35;font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:800}.close{color:#9a7863;font-size:34rpx;line-height:1}.demand-selected{display:block;margin-top:12rpx;color:#8b654f;font-size:16rpx;font-weight:800}.demand-input{box-sizing:border-box;width:100%;height:150rpx;margin-top:12rpx;padding:12rpx;border:1rpx solid #e1d6c9;border-radius:13rpx;background:#fffdf9;color:#4b433b;font-size:17rpx;line-height:1.5}.demand-submit{height:72rpx;margin-top:12rpx;border-radius:13rpx;background:#a6644b;color:#fff;font-size:20rpx;font-weight:800}.demand-submit[loading]{opacity:.65}
</style>
