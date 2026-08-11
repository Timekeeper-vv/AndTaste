<template>
  <view class="page">
    <view class="hero">
      <text class="eyebrow">FROM IDEA TO PRODUCT</text>
      <text class="title">商品化申请</text>
      <text class="sub">先选适合试做的产品，再提交报价或渠道代销申请。真实价格、工期和合作关系以运营审核结果及正式协议为准。</text>
    </view>

    <view class="notice"><text class="notice-title">首期试运行规则</text><text>预售 / 按单生产 · 平台暂不承诺备货 · 创作者 70% / 平台服务 30% 为试运行规则，结算口径以正式协议为准。</text></view>

    <view class="section-head"><view><text class="eyebrow">PRODUCT TEMPLATES</text><text class="section-title">选择商品方向</text></view><text>{{ filteredProducts.length }} / {{ products.length }}</text></view>
    <view v-if="loading" class="empty">正在读取产品模板…</view>
    <view v-else-if="!products.length" class="empty">暂时没有可申请的产品方向。</view>
    <view v-else>
      <view class="catalog-tools"><input class="catalog-search" :value="keyword" maxlength="30" placeholder="搜索：冰箱贴、书签、冰淇淋、马克杯…" @input="updateKeyword" /><scroll-view scroll-x class="catalog-categories" :show-scrollbar="false"><view><text class="catalog-category" :class="{ active: !category }" @tap="category = ''">全部</text><text v-for="item in categories" :key="item.key" class="catalog-category" :class="{ active: category === item.key }" @tap="category = item.key">{{ item.name }}</text></view></scroll-view></view>
      <view v-if="!filteredProducts.length" class="empty compact">没有匹配的商品方向，换个分类或关键词试试。</view>
      <view v-else class="product-list">
      <view v-for="product in filteredProducts" :key="product.templateCode" class="product-card" :class="{ active: selected?.templateCode === product.templateCode }" @tap="selected = product">
        <image v-if="coverUrl(product)" :src="coverUrl(product)" class="product-cover" mode="aspectFill" />
        <view v-else class="product-cover fallback"><text>{{ product.productName.slice(0, 1) }}</text></view>
        <view class="product-body"><view class="product-title-row"><text class="product-name">{{ product.productName }}</text><text class="status">待确认</text></view><text class="product-meta">材质：{{ product.material }}</text><text class="product-meta">规格：{{ product.specification }}</text><text class="product-meta">参考零售价：{{ product.indicativeRetailDisplay }}</text><text class="product-tip">打样 {{ product.sampleLeadTime }} · 大货 {{ product.bulkLeadTime }}</text><button class="select-btn" size="mini" @tap.stop="selected = product">{{ selected?.templateCode === product.templateCode ? '已选择' : '选择方向' }}</button></view>
      </view>
      </view>
    </view>

    <view v-if="selected" class="apply-panel">
      <view class="panel-head"><view><text class="eyebrow">REQUEST</text><text class="section-title">{{ selected.productName }}</text></view><text class="close" @tap="selected = null">×</text></view>
      <view class="mode-tabs"><text :class="{ active: mode === 'quote' }" @tap="mode = 'quote'">申请报价 / 打样</text><text :class="{ active: mode === 'consignment' }" @tap="mode = 'consignment'">申请渠道代销</text></view>
      <view v-if="mode === 'quote'" class="form">
        <text class="label">申请类型</text><view class="chips"><text v-for="item in quoteTypes" :key="item.value" :class="{ active: quoteType === item.value }" @tap="quoteType = item.value">{{ item.label }}</text></view>
        <text class="label">预计数量</text><input v-model="quantity" class="input" type="number" placeholder="例如：1 或 100" />
        <text class="label">补充说明</text><textarea v-model="note" class="textarea" maxlength="800" placeholder="文化主题、尺寸、期望时间或希望保留的元素" />
      </view>
      <view v-else class="form">
        <text class="label">目标渠道（可不选）</text><view class="channel-picker" @tap="openChannelPicker"><view><text>{{ selectedChannel?.name || '选择博物馆、景区或文旅门店' }}</text><text v-if="selectedChannel">{{ channelLocation(selectedChannel) }} · {{ channelStatus(selectedChannel) }}</text></view><text>›</text></view><text v-if="selectedChannel?.cooperationNotice" class="channel-notice">{{ selectedChannel.cooperationNotice }}</text>
        <text class="label">作品编号</text><input v-model="assetId" class="input" type="number" placeholder="请填写已生成作品的编号" />
        <text class="label">补充说明</text><textarea v-model="note" class="textarea" maxlength="800" placeholder="目标客群、预计数量、渠道想法等" />
      </view>
      <view class="copyright-box"><text class="label">版权依据</text><view class="chips"><text v-for="item in copyrightBases" :key="item.value" :class="{ active: copyrightBasis === item.value }" @tap="copyrightBasis = item.value">{{ item.label }}</text></view><text class="copyright-statement">{{ copyrightStatement }}</text><input v-if="mode === 'consignment' && copyrightBasis === 'authorized'" v-model="authorizationNote" class="input" placeholder="授权来源和允许的商业使用范围" /><label class="check-row"><checkbox :checked="copyrightConfirmed" color="#5e7c6f" @tap="copyrightConfirmed = !copyrightConfirmed" /><text>我已阅读并确认版权声明，平台审核不等于权利授予。</text></label><text class="copyright-note">{{ selected.copyrightRequirement }}</text></view>
      <button class="submit" :loading="submitting" @tap="submit">{{ submitting ? '正在提交' : mode === 'quote' ? '提交报价申请' : '提交代销申请' }}</button>
    </view>

    <view v-if="channelPickerVisible" class="channel-mask" @tap="closeChannelPicker">
      <view class="channel-modal" @tap.stop>
        <view class="channel-modal-head"><view><text>选择售卖渠道</text><text>按地区筛选或搜索名称，目录不代表已合作</text></view><text class="channel-close" @tap="closeChannelPicker">×</text></view>
        <view class="channel-search-row"><input v-model="channelKeyword" confirm-type="search" maxlength="40" placeholder="搜索：故宫、黄鹤楼、南京博物院…" @confirm="searchChannels" /><button size="mini" @tap="searchChannels">搜索</button></view>
        <scroll-view scroll-x class="channel-filter-scroll" :show-scrollbar="false"><view class="channel-filter-row"><text v-for="item in channelTypes" :key="item.key" :class="{ active: channelType === item.key }" @tap="setChannelType(item.key)">{{ item.name }}</text></view></scroll-view>
        <scroll-view scroll-x class="channel-filter-scroll" :show-scrollbar="false"><view class="channel-filter-row"><text v-for="item in channelRegions" :key="item.key" :class="{ active: channelRegion === item.key }" @tap="setChannelRegion(item.key)">{{ item.name }}</text></view></scroll-view>
        <scroll-view scroll-x class="channel-filter-scroll" :show-scrollbar="false"><view class="channel-filter-row"><text :class="{ active: !channelProvince }" @tap="setChannelProvince('')">全部省份</text><text v-for="item in channelProvinces" :key="item.province" :class="{ active: channelProvince === item.province }" @tap="setChannelProvince(item.province)">{{ item.province }} {{ item.count }}</text></view></scroll-view>
        <text class="channel-result-note">{{ channelDirectory.total }} 条可选渠道 · 候选渠道须经运营、版权和授权核验</text>
        <scroll-view scroll-y class="channel-results">
          <view v-if="channelLoading && !channelDirectory.items.length" class="channel-empty">正在读取渠道目录…</view>
          <view v-else-if="!channelDirectory.items.length" class="channel-empty">没有找到匹配渠道，换个地区或名称试试。</view>
          <view v-for="item in channelDirectory.items" :key="item.id" class="channel-option" :class="{ selected: selectedChannel?.id === item.id }" @tap="selectChannel(item)"><view><view class="channel-name-row"><text>{{ item.name }}</text><text class="channel-type-tag">{{ channelTypeLabel(item.channelType) }}</text></view><text>{{ channelLocation(item) || '地区待核验' }}</text><text>{{ channelStatus(item) }}</text></view><text>{{ selectedChannel?.id === item.id ? '✓' : '›' }}</text></view>
          <button v-if="channelDirectory.items.length < channelDirectory.total" class="load-more" :loading="channelLoading" @tap="loadMoreChannels">加载更多</button>
        </scroll-view>
        <view class="channel-modal-actions"><button class="channel-clear" @tap="clearChannel">暂不指定</button><button class="channel-confirm" @tap="closeChannelPicker">完成</button></view>
      </view>
    </view>

    <view v-if="requests.quoteRequests.length || requests.consignmentApplications.length" class="request-history"><view class="section-head"><view><text class="eyebrow">MY REQUESTS</text><text class="section-title">申请记录</text></view></view><view v-for="item in [...requests.quoteRequests, ...requests.consignmentApplications]" :key="item.requestNo || item.applicationNo" class="history-row"><view class="history-main"><view class="history-head"><view><text class="history-name">{{ item.productName }}</text><text class="history-no">{{ item.requestNo || item.applicationNo }} · {{ item.channelName || '报价/打样' }}</text></view><text class="history-status">{{ statusText(item.status) }}</text></view><view v-if="item.requestNo && ['quoted', 'accepted'].includes(item.status)" class="quote-detail"><text>单价：{{ displayMoney(item.quotedUnitPrice) }}</text><text>总价：{{ displayMoney(item.quotedTotalPrice) }}</text><text>交期：{{ item.quotedLeadTime || '待确认' }}</text><text v-if="item.operatorComment">说明：{{ item.operatorComment }}</text></view><button v-if="item.requestNo && item.status === 'quoted'" class="accept-quote" size="mini" :loading="acceptingId === item.id" @tap.stop="acceptQuote(item)">{{ acceptingId === item.id ? '提交中' : '接受报价' }}</button><button v-if="item.requestNo && item.status === 'accepted' && item.requestType === 'sample' && ['unpaid', 'pending'].includes(String(item.samplePaymentStatus || 'unpaid'))" class="pay-sample" size="mini" @tap.stop="paySampleFee(item)">支付打样费</button></view></view></view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { apiUrl } from '../../api/client'
import { acceptCommercialQuote, createConsignmentApplication, createQuoteRequest, getCommercialChannelDirectory, getCommercialProducts, getCommercialRequests, type CommercialChannel, type CommercialChannelDirectory, type CommercialProduct } from '../../api/commercial'
import { requireSession } from '../../utils/session'

const products = ref<CommercialProduct[]>([])
const category = ref('')
const keyword = ref('')
const selected = ref<CommercialProduct | null>(null)
const selectedChannel = ref<CommercialChannel | null>(null)
const channelPickerVisible = ref(false)
const channelLoading = ref(false)
const channelKeyword = ref('')
const channelType = ref('')
const channelRegion = ref('')
const channelProvince = ref('')
const channelDirectory = ref<CommercialChannelDirectory>({ items: [], total: 0, page: 1, size: 30, provinces: [] })
const loading = ref(false)
const submitting = ref(false)
const acceptingId = ref<number | null>(null)
const mode = ref<'quote' | 'consignment'>('quote')
const quoteType = ref('sample')
const quantity = ref('1')
const note = ref('')
const assetId = ref('')
const copyrightBasis = ref('original')
const copyrightConfirmed = ref(false)
const authorizationNote = ref('')
const copyrightStatement = ref('我确认提交的作品为本人原创、已取得有效商业授权，或属于可依法商业使用的公有领域内容；我不会在未获授权的情况下使用博物馆、景区、品牌、字体、人物肖像或他人作品。平台审核不等于权利授予，正式上架前仍需补充权利证明并签署相关协议。')
const requests = ref<{ quoteRequests: any[]; consignmentApplications: any[] }>({ quoteRequests: [], consignmentApplications: [] })
const quoteTypes = [{ value: 'sample', label: '先做打样' }, { value: 'bulk', label: '批量生产' }, { value: 'personal', label: '个人定制' }]
const copyrightBases = [{ value: 'original', label: '本人原创' }, { value: 'authorized', label: '已取得授权' }, { value: 'public_domain', label: '公有领域' }]
const channelTypes = [{ key: '', name: '全部渠道' }, { key: 'museum', name: '博物馆' }, { key: 'scenic_spot', name: '景区/场馆' }, { key: 'cultural_store', name: '文旅门店' }]
const channelRegions = [{ key: '', name: '全国' }, { key: 'north', name: '华北' }, { key: 'northeast', name: '东北' }, { key: 'east', name: '华东' }, { key: 'central', name: '华中' }, { key: 'south', name: '华南' }, { key: 'southwest', name: '西南' }, { key: 'northwest', name: '西北' }]
const categoryLabels: Record<string, string> = { food: '食品饮品', stationery: '文具纸品', souvenir: '景区文创', accessory: '饰品挂件', craft: '工艺收藏', daily: '日用生活', tableware: '餐饮器物', toy: '潮玩玩具', apparel: '服饰配件', precious: '贵金属' }
const categories = computed(() => {
  const seen = new Set<string>()
  return products.value.filter((product) => {
    const key = String(product.categoryKey || product.productType || 'other')
    if (seen.has(key)) return false
    seen.add(key)
    return true
  }).map((product) => {
    const key = String(product.categoryKey || product.productType || 'other')
    return { key, name: categoryLabels[key] || product.categoryName || '其他' }
  })
})
const filteredProducts = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return products.value.filter((product) => {
    const key = String(product.categoryKey || product.productType || 'other')
    if (category.value && key !== category.value) return false
    if (!query) return true
    return `${product.productName} ${product.categoryName || ''} ${product.material} ${product.process}`.toLowerCase().includes(query)
  })
})

onLoad(async (query: any) => {
  if (!requireSession()) return
  assetId.value = String(query?.assetId || '')
  await load()
})

async function load() {
  loading.value = true
  try {
    products.value = await getCommercialProducts()
    if (products.value[0]?.copyrightStatement) copyrightStatement.value = String(products.value[0].copyrightStatement)
    requests.value = await getCommercialRequests()
  } catch (error: any) { uni.showToast({ title: error?.message || '商品化服务暂不可用', icon: 'none' }) }
  finally { loading.value = false }
}

function coverUrl(product: CommercialProduct) { const value = String(product.coverImageUrl || ''); return value.startsWith('http') ? value : value ? apiUrl(value) : '' }
function updateKeyword(event: any) { keyword.value = String(event?.detail?.value || '') }
function channelTypeLabel(value?: string) { return ({ museum: '博物馆', scenic_spot: '景区/场馆', cultural_store: '文旅门店', other: '其他' } as Record<string, string>)[String(value || '')] || '渠道' }
function channelLocation(channel: CommercialChannel) { return [channel.province, channel.city, channel.district].filter(Boolean).join(' ') }
function channelStatus(channel: CommercialChannel) { return channel.cooperationStatus === 'cooperating' ? '合作状态待按实际点位确认' : channel.cooperationStatus === 'pending_verification' ? '运营候选，待核验' : '公开目录，待联系核验' }
const channelProvinces = computed(() => channelDirectory.value.provinces)

async function loadChannels(reset = true) {
  if (channelLoading.value) return
  channelLoading.value = true
  try {
    const page = reset ? 1 : channelDirectory.value.page + 1
    const response = await getCommercialChannelDirectory({ keyword: channelKeyword.value.trim(), type: channelType.value, region: channelRegion.value, province: channelProvince.value, page, size: 30 })
    channelDirectory.value = { ...response, items: reset ? response.items : [...channelDirectory.value.items, ...response.items] }
  } catch (error: any) { uni.showToast({ title: error?.message || '渠道目录加载失败', icon: 'none' }) }
  finally { channelLoading.value = false }
}
function openChannelPicker() { channelPickerVisible.value = true; void loadChannels(true) }
function closeChannelPicker() { channelPickerVisible.value = false }
function searchChannels() { void loadChannels(true) }
function loadMoreChannels() { void loadChannels(false) }
function setChannelType(value: string) { channelType.value = value; channelProvince.value = ''; void loadChannels(true) }
function setChannelRegion(value: string) { channelRegion.value = value; channelProvince.value = ''; void loadChannels(true) }
function setChannelProvince(value: string) { channelProvince.value = value; void loadChannels(true) }
function selectChannel(channel: CommercialChannel) { selectedChannel.value = channel; channelPickerVisible.value = false }
function clearChannel() { selectedChannel.value = null; channelPickerVisible.value = false }

async function submit() {
  if (!selected.value || submitting.value) return
  if (!copyrightConfirmed.value) return uni.showToast({ title: '请先确认版权声明', icon: 'none' })
  if (mode.value === 'consignment' && !assetId.value) return uni.showToast({ title: '代销申请需要填写作品编号', icon: 'none' })
  submitting.value = true
  try {
    const body: Record<string, unknown> = { templateCode: selected.value.templateCode, note: note.value, copyrightBasis: copyrightBasis.value, copyrightConfirmed: true }
    const result = mode.value === 'quote'
      ? await createQuoteRequest({ ...body, requestType: quoteType.value, purpose: 'personal', quantity: Number(quantity.value) || 1, assetId: assetId.value || undefined })
      : await createConsignmentApplication({ ...body, assetId: Number(assetId.value), channelId: selectedChannel.value?.id || undefined, authorizationNote: authorizationNote.value })
    requests.value = await getCommercialRequests()
    uni.showModal({ title: '已提交', content: `${result?.message || '申请已提交'}\n编号：${result?.requestNo || '-'}`, showCancel: false })
    selected.value = null; note.value = ''; authorizationNote.value = ''; copyrightConfirmed.value = false
  } catch (error: any) { uni.showToast({ title: error?.message || '提交失败，请稍后重试', icon: 'none' }) }
  finally { submitting.value = false }
}

async function acceptQuote(item: any) {
  if (acceptingId.value || item.status !== 'quoted') return
  acceptingId.value = Number(item.id)
  try {
    const result = await acceptCommercialQuote(Number(item.id))
    requests.value = await getCommercialRequests()
    uni.showModal({ title: '报价已接受', content: result?.message || '运营会联系你确认打样或生产细节', showCancel: false })
  } catch (error: any) { uni.showToast({ title: error?.message || '接受报价失败，请稍后重试', icon: 'none' }) }
  finally { acceptingId.value = null }
}

function paySampleFee(item: any) {
  uni.navigateTo({ url: `/pages/sample-payment/index?quoteId=${encodeURIComponent(String(item.id))}` })
}

function displayMoney(value: unknown) { return value === null || value === undefined || value === '' ? '待确认' : `¥${Number(value).toFixed(2)}` }
function statusText(status: string) { const map: Record<string, string> = { new: '待处理', processing: '处理中', quoted: '已报价', pending_review: '待审核', need_materials: '待补材料', approved: '已通过', rejected: '未通过', accepted: '已接受', closed: '已关闭' }; return map[status] || status }
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:30rpx 28rpx 80rpx;box-sizing:border-box;background:linear-gradient(160deg,#fbf8f2,#f1e9df 58%,#edf3ed);color:#3b342d}.hero{padding:10rpx 6rpx 25rpx}.eyebrow{display:block;color:#668575;font-size:16rpx;font-weight:900;letter-spacing:2.2rpx}.title{display:block;margin-top:12rpx;font-family:"Songti SC","STSong",serif;font-size:52rpx;font-weight:800}.sub{display:block;margin-top:12rpx;color:#7c7167;font-size:20rpx;line-height:1.6}.notice{display:flex;flex-direction:column;gap:7rpx;padding:17rpx;border:1rpx solid #e5d1bd;border-radius:18rpx;background:#fff8ef;color:#866653;font-size:16rpx;line-height:1.55}.notice-title{font-size:21rpx;color:#a45f43;font-weight:900}.section-head{display:flex;align-items:flex-end;justify-content:space-between;gap:10rpx;margin:28rpx 5rpx 13rpx}.section-head>view{display:flex;flex-direction:column;gap:5rpx}.section-title{color:#443c34;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.section-head>text:last-child{color:#9b8e82;font-size:15rpx}.catalog-tools{margin:0 0 15rpx;padding:12rpx;border:1rpx solid #e3d8cb;border-radius:15rpx;background:#f8f3eb}.catalog-search{box-sizing:border-box;width:100%;height:66rpx;padding:0 13rpx;border:1rpx solid #ded4c7;border-radius:11rpx;background:#fffefa;color:#4c433a;font-size:18rpx}.catalog-categories{margin-top:10rpx;white-space:nowrap}.catalog-categories>view{display:flex;gap:7rpx}.catalog-category{display:inline-block;padding:8rpx 10rpx;border:1rpx solid #ded5c9;border-radius:9rpx;background:#fffefa;color:#897d72;font-size:14rpx}.catalog-category.active{border-color:#72917f;background:#e7f0e7;color:#4e705e;font-weight:800}.product-list{display:grid;gap:13rpx}.product-card{display:flex;overflow:hidden;border:1rpx solid rgba(130,113,94,.15);border-radius:20rpx;background:rgba(255,254,250,.94);box-shadow:0 10rpx 23rpx rgba(69,54,39,.06)}.product-card.active{border-color:#779886;box-shadow:0 0 0 3rpx rgba(119,152,134,.13)}.product-cover{flex:0 0 190rpx;width:190rpx;height:240rpx;background:#e7dfd3}.fallback{display:flex;align-items:center;justify-content:center;background:linear-gradient(145deg,#dce7dd,#efdfd0);color:#5b7b6d}.fallback text{font-family:"Songti SC","STSong",serif;font-size:70rpx;font-weight:800}.product-body{min-width:0;flex:1;padding:16rpx}.product-title-row{display:flex;align-items:flex-start;justify-content:space-between;gap:6rpx}.product-name{color:#403931;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:800}.status{padding:5rpx 7rpx;border-radius:8rpx;background:#f5eee3;color:#aa765a;font-size:12rpx;white-space:nowrap}.product-meta{display:block;margin-top:8rpx;overflow:hidden;color:#81766b;font-size:15rpx;text-overflow:ellipsis;white-space:nowrap}.product-tip{display:block;margin-top:10rpx;color:#5d7b6b;font-size:14rpx;line-height:1.4}.select-btn{height:52rpx;margin:14rpx 0 0;padding:0 14rpx;border:1rpx solid #6b8d7b;border-radius:10rpx;background:#eef5ee;color:#527462;font-size:14rpx}.select-btn::after{border:0}.empty{padding:58rpx 20rpx;border:1rpx dashed #d5cabb;border-radius:19rpx;color:#8e8277;text-align:center}.empty.compact{padding:34rpx 18rpx}.apply-panel,.request-history{margin-top:22rpx;padding:18rpx;border:1rpx solid #d7c9b9;border-radius:21rpx;background:#fffaf3;box-shadow:0 10rpx 22rpx rgba(90,66,45,.07)}.panel-head{display:flex;justify-content:space-between;gap:12rpx}.panel-head>view{display:flex;flex-direction:column;gap:5rpx}.close{font-size:35rpx;color:#9a7863}.mode-tabs{display:flex;gap:9rpx;margin-top:18rpx}.mode-tabs text,.chips text{padding:9rpx 11rpx;border:1rpx solid #e2d6ca;border-radius:10rpx;background:#fffdf9;color:#8b7b6c;font-size:15rpx}.mode-tabs text.active,.chips text.active{border-color:#6e907f;background:#eaf3ea;color:#527363;font-weight:800}.form{display:flex;flex-direction:column;gap:8rpx;margin-top:17rpx}.label{display:block;margin-top:6rpx;color:#6c5c4f;font-size:16rpx;font-weight:800}.chips{display:flex;flex-wrap:wrap;gap:7rpx}.input,.textarea{box-sizing:border-box;width:100%;border:1rpx solid #dfd4c8;border-radius:12rpx;background:#fffdf9;color:#4a423a;font-size:17rpx}.input{height:68rpx;padding:0 12rpx}.textarea{height:130rpx;padding:11rpx;line-height:1.5}.channel-picker{display:flex;align-items:center;justify-content:space-between;gap:8rpx;padding:15rpx 12rpx;border:1rpx solid #dfd4c8;border-radius:12rpx;background:#fffdf9;color:#796d62;font-size:16rpx}.copyright-box{margin-top:17rpx;padding:13rpx;border-radius:14rpx;background:#f7f2e9}.copyright-statement{display:block;margin-top:10rpx;color:#6d6258;font-size:14rpx;line-height:1.6}.check-row{display:flex;align-items:flex-start;gap:6rpx;margin-top:12rpx;color:#6d6258;font-size:14rpx;line-height:1.45}.check-row checkbox{transform:scale(.75);transform-origin:top left}.copyright-note{display:block;margin-top:10rpx;color:#947f70;font-size:14rpx;line-height:1.5}.submit{height:74rpx;margin-top:17rpx;border-radius:13rpx;background:#5e7c6f;color:#fff;font-size:21rpx;font-weight:850}.submit[loading]{opacity:.65}.history-row{padding:13rpx 0;border-top:1rpx solid #eee2d6}.history-main{display:flex;min-width:0;flex-direction:column;gap:9rpx}.history-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx}.history-head>view{display:flex;min-width:0;flex-direction:column;gap:5rpx}.history-name{color:#4b4036;font-size:18rpx;font-weight:800}.history-no{color:#97897d;font-size:13rpx}.history-status{padding:5rpx 7rpx;border-radius:8rpx;background:#edf4ed;color:#5f7b6e;font-size:13rpx;white-space:nowrap}.quote-detail{display:flex;flex-direction:column;gap:5rpx;padding:11rpx;border-radius:11rpx;background:#f3f7f2;color:#62796d;font-size:14rpx;line-height:1.45}.accept-quote{align-self:flex-start;height:52rpx;margin:0;padding:0 15rpx;border-radius:10rpx;background:#5e7c6f;color:#fff;font-size:15rpx}.accept-quote::after{border:0}
.pay-sample{align-self:flex-start;height:52rpx;margin:0;padding:0 15rpx;border-radius:10rpx;background:#b9664f;color:#fff;font-size:15rpx}.pay-sample::after{border:0}
.channel-picker>view{display:flex;min-width:0;flex:1;flex-direction:column;gap:4rpx}.channel-picker>view text:first-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.channel-picker>view text:last-child{color:#a08d7e;font-size:13rpx}.channel-notice{display:block;margin-top:3rpx;color:#9b7968;font-size:13rpx;line-height:1.45}.channel-mask{position:fixed;z-index:90;inset:0;display:flex;align-items:flex-end;background:rgba(36,31,27,.58)}.channel-modal{width:100%;max-height:89vh;box-sizing:border-box;padding:22rpx 22rpx calc(20rpx + env(safe-area-inset-bottom));border-radius:24rpx 24rpx 0 0;background:#fffaf4;box-shadow:0 -12rpx 36rpx rgba(44,31,21,.16)}.channel-modal-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16rpx}.channel-modal-head>view{display:flex;flex-direction:column;gap:6rpx}.channel-modal-head text:first-child{color:#413931;font-family:"Songti SC","STSong",serif;font-size:30rpx;font-weight:800}.channel-modal-head text:last-child{color:#938276;font-size:14rpx;line-height:1.45}.channel-close{width:44rpx;height:44rpx;color:#937460;font-size:36rpx;line-height:38rpx;text-align:center}.channel-search-row{display:grid;grid-template-columns:minmax(0,1fr) 100rpx;gap:9rpx;margin-top:16rpx}.channel-search-row input{box-sizing:border-box;width:100%;height:66rpx;padding:0 13rpx;border:1rpx solid #ded3c6;border-radius:12rpx;background:#fffefa;color:#4b4239;font-size:17rpx}.channel-search-row button{height:66rpx;margin:0;border-radius:12rpx;background:#587766;color:#fff;font-size:16rpx;line-height:66rpx}.channel-search-row button::after,.channel-modal-actions button::after,.load-more::after{border:0}.channel-filter-scroll{margin-top:11rpx;white-space:nowrap}.channel-filter-row{display:flex;gap:8rpx}.channel-filter-row text{display:inline-block;padding:8rpx 11rpx;border:1rpx solid #e1d6ca;border-radius:10rpx;background:#fffefa;color:#877a6d;font-size:14rpx}.channel-filter-row text.active{border-color:#779884;background:#e9f2e9;color:#50725f;font-weight:800}.channel-result-note{display:block;margin:13rpx 2rpx 8rpx;color:#957c6a;font-size:13rpx}.channel-results{height:56vh;min-height:480rpx;border-top:1rpx solid #eadfd3;border-bottom:1rpx solid #eadfd3}.channel-empty{padding:58rpx 20rpx;color:#968679;font-size:17rpx;text-align:center}.channel-option{display:flex;align-items:center;justify-content:space-between;gap:12rpx;padding:15rpx 4rpx;border-bottom:1rpx solid #f0e7de}.channel-option.selected{background:#eff5ef}.channel-option>view{display:flex;min-width:0;flex:1;flex-direction:column;gap:5rpx}.channel-name-row{display:flex;align-items:center;gap:7rpx}.channel-name-row text:first-child{overflow:hidden;color:#493f36;font-size:19rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.channel-name-row .channel-type-tag{flex:0 0 auto;padding:3rpx 6rpx;border-radius:6rpx;background:#f1eadf;color:#9a725c;font-size:11rpx}.channel-option>view>text{color:#97897d;font-size:13rpx;line-height:1.35}.channel-option>text{color:#668472;font-size:26rpx}.load-more{display:block;height:62rpx;margin:14rpx auto;padding:0 25rpx;border:1rpx solid #a9c0ad;border-radius:11rpx;background:#f1f7f1;color:#537664;font-size:16rpx;line-height:62rpx}.channel-modal-actions{display:grid;grid-template-columns:1fr 1.4fr;gap:10rpx;margin-top:15rpx}.channel-modal-actions button{height:70rpx;margin:0;border-radius:13rpx;font-size:18rpx;font-weight:800;line-height:70rpx}.channel-clear{border:1rpx solid #d8cbbd;background:#fffefa;color:#806f61}.channel-confirm{background:#587766;color:#fff}
</style>
