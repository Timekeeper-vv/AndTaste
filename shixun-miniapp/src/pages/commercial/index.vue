<template>
  <view class="page">
    <view class="hero">
      <text class="eyebrow">FROM IDEA TO PRODUCT</text>
      <text class="title">商品化申请</text>
      <text class="sub">先选适合试做的产品，再提交报价或渠道代销申请。真实价格、工期和合作关系以运营审核结果及正式协议为准。</text>
    </view>

    <view class="notice"><text class="notice-title">首期试运行规则</text><text>预售 / 按单生产 · 平台暂不承诺备货 · 创作者 70% / 平台服务 30% 为试运行规则，结算口径以正式协议为准。</text></view>

    <view class="section-head"><view><text class="eyebrow">PRODUCT TEMPLATES</text><text class="section-title">先从简单产品开始</text></view><text>{{ products.length }} 个方向</text></view>
    <view v-if="loading" class="empty">正在读取产品模板…</view>
    <view v-else-if="!products.length" class="empty">暂时没有可申请的产品方向。</view>
    <view v-else class="product-list">
      <view v-for="product in products" :key="product.templateCode" class="product-card" :class="{ active: selected?.templateCode === product.templateCode }" @tap="selected = product">
        <image v-if="coverUrl(product)" :src="coverUrl(product)" class="product-cover" mode="aspectFill" />
        <view v-else class="product-cover fallback"><text>{{ product.productName.slice(0, 1) }}</text></view>
        <view class="product-body"><view class="product-title-row"><text class="product-name">{{ product.productName }}</text><text class="status">待确认</text></view><text class="product-meta">材质：{{ product.material }}</text><text class="product-meta">规格：{{ product.specification }}</text><text class="product-meta">参考零售价：{{ product.indicativeRetailDisplay }}</text><text class="product-tip">打样 {{ product.sampleLeadTime }} · 大货 {{ product.bulkLeadTime }}</text><button class="select-btn" size="mini" @tap.stop="selected = product">{{ selected?.templateCode === product.templateCode ? '已选择' : '选择方向' }}</button></view>
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
        <text class="label">目标渠道（可不选）</text><view class="channel-picker" @tap="chooseChannel"><text>{{ selectedChannel?.name || '从公开目录中选择，不能视为已合作' }}</text><text>›</text></view>
        <text class="label">作品编号</text><input v-model="assetId" class="input" type="number" placeholder="请填写已生成作品的编号" />
        <text class="label">补充说明</text><textarea v-model="note" class="textarea" maxlength="800" placeholder="目标客群、预计数量、渠道想法等" />
      </view>
      <view class="copyright-box"><text class="label">版权依据</text><view class="chips"><text v-for="item in copyrightBases" :key="item.value" :class="{ active: copyrightBasis === item.value }" @tap="copyrightBasis = item.value">{{ item.label }}</text></view><text class="copyright-statement">{{ copyrightStatement }}</text><input v-if="mode === 'consignment' && copyrightBasis === 'authorized'" v-model="authorizationNote" class="input" placeholder="授权来源和允许的商业使用范围" /><label class="check-row"><checkbox :checked="copyrightConfirmed" color="#5e7c6f" @tap="copyrightConfirmed = !copyrightConfirmed" /><text>我已阅读并确认版权声明，平台审核不等于权利授予。</text></label><text class="copyright-note">{{ selected.copyrightRequirement }}</text></view>
      <button class="submit" :loading="submitting" @tap="submit">{{ submitting ? '正在提交' : mode === 'quote' ? '提交报价申请' : '提交代销申请' }}</button>
    </view>

    <view v-if="requests.quoteRequests.length || requests.consignmentApplications.length" class="request-history"><view class="section-head"><view><text class="eyebrow">MY REQUESTS</text><text class="section-title">申请记录</text></view></view><view v-for="item in [...requests.quoteRequests, ...requests.consignmentApplications]" :key="item.requestNo || item.applicationNo" class="history-row"><view><text>{{ item.productName }}</text><text>{{ item.requestNo || item.applicationNo }} · {{ item.channelName || '报价/打样' }}</text></view><text class="history-status">{{ statusText(item.status) }}</text></view></view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { apiUrl } from '../../api/client'
import { createConsignmentApplication, createQuoteRequest, getCommercialChannels, getCommercialProducts, getCommercialRequests, type CommercialChannel, type CommercialProduct } from '../../api/commercial'
import { requireSession } from '../../utils/session'

const products = ref<CommercialProduct[]>([])
const channels = ref<CommercialChannel[]>([])
const selected = ref<CommercialProduct | null>(null)
const selectedChannel = ref<CommercialChannel | null>(null)
const loading = ref(false)
const submitting = ref(false)
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

async function chooseChannel() {
  try {
    channels.value = await getCommercialChannels()
    const names = channels.value.slice(0, 100).map(item => item.name)
    uni.showActionSheet({ itemList: names.length ? names : ['暂无目录记录'], success: (result) => { selectedChannel.value = channels.value[result.tapIndex] || null } })
  } catch (error: any) { uni.showToast({ title: error?.message || '渠道目录加载失败', icon: 'none' }) }
}

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

function statusText(status: string) { const map: Record<string, string> = { new: '待处理', processing: '处理中', quoted: '已报价', pending_review: '待审核', need_materials: '待补材料', approved: '已通过', rejected: '未通过', accepted: '已接受', closed: '已关闭' }; return map[status] || status }
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:30rpx 28rpx 80rpx;box-sizing:border-box;background:linear-gradient(160deg,#fbf8f2,#f1e9df 58%,#edf3ed);color:#3b342d}.hero{padding:10rpx 6rpx 25rpx}.eyebrow{display:block;color:#668575;font-size:16rpx;font-weight:900;letter-spacing:2.2rpx}.title{display:block;margin-top:12rpx;font-family:"Songti SC","STSong",serif;font-size:52rpx;font-weight:800}.sub{display:block;margin-top:12rpx;color:#7c7167;font-size:20rpx;line-height:1.6}.notice{display:flex;flex-direction:column;gap:7rpx;padding:17rpx;border:1rpx solid #e5d1bd;border-radius:18rpx;background:#fff8ef;color:#866653;font-size:16rpx;line-height:1.55}.notice-title{font-size:21rpx;color:#a45f43;font-weight:900}.section-head{display:flex;align-items:flex-end;justify-content:space-between;gap:10rpx;margin:28rpx 5rpx 13rpx}.section-head>view{display:flex;flex-direction:column;gap:5rpx}.section-title{color:#443c34;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.section-head>text:last-child{color:#9b8e82;font-size:15rpx}.product-list{display:grid;gap:13rpx}.product-card{display:flex;overflow:hidden;border:1rpx solid rgba(130,113,94,.15);border-radius:20rpx;background:rgba(255,254,250,.94);box-shadow:0 10rpx 23rpx rgba(69,54,39,.06)}.product-card.active{border-color:#779886;box-shadow:0 0 0 3rpx rgba(119,152,134,.13)}.product-cover{flex:0 0 190rpx;width:190rpx;height:240rpx;background:#e7dfd3}.fallback{display:flex;align-items:center;justify-content:center;background:linear-gradient(145deg,#dce7dd,#efdfd0);color:#5b7b6d}.fallback text{font-family:"Songti SC","STSong",serif;font-size:70rpx;font-weight:800}.product-body{min-width:0;flex:1;padding:16rpx}.product-title-row{display:flex;align-items:flex-start;justify-content:space-between;gap:6rpx}.product-name{color:#403931;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:800}.status{padding:5rpx 7rpx;border-radius:8rpx;background:#f5eee3;color:#aa765a;font-size:12rpx;white-space:nowrap}.product-meta{display:block;margin-top:8rpx;overflow:hidden;color:#81766b;font-size:15rpx;text-overflow:ellipsis;white-space:nowrap}.product-tip{display:block;margin-top:10rpx;color:#5d7b6b;font-size:14rpx;line-height:1.4}.select-btn{height:52rpx;margin:14rpx 0 0;padding:0 14rpx;border:1rpx solid #6b8d7b;border-radius:10rpx;background:#eef5ee;color:#527462;font-size:14rpx}.select-btn::after{border:0}.empty{padding:58rpx 20rpx;border:1rpx dashed #d5cabb;border-radius:19rpx;color:#8e8277;text-align:center}.apply-panel,.request-history{margin-top:22rpx;padding:18rpx;border:1rpx solid #d7c9b9;border-radius:21rpx;background:#fffaf3;box-shadow:0 10rpx 22rpx rgba(90,66,45,.07)}.panel-head{display:flex;justify-content:space-between;gap:12rpx}.panel-head>view{display:flex;flex-direction:column;gap:5rpx}.close{font-size:35rpx;color:#9a7863}.mode-tabs{display:flex;gap:9rpx;margin-top:18rpx}.mode-tabs text,.chips text{padding:9rpx 11rpx;border:1rpx solid #e2d6ca;border-radius:10rpx;background:#fffdf9;color:#8b7b6c;font-size:15rpx}.mode-tabs text.active,.chips text.active{border-color:#6e907f;background:#eaf3ea;color:#527363;font-weight:800}.form{display:flex;flex-direction:column;gap:8rpx;margin-top:17rpx}.label{display:block;margin-top:6rpx;color:#6c5c4f;font-size:16rpx;font-weight:800}.chips{display:flex;flex-wrap:wrap;gap:7rpx}.input,.textarea{box-sizing:border-box;width:100%;border:1rpx solid #dfd4c8;border-radius:12rpx;background:#fffdf9;color:#4a423a;font-size:17rpx}.input{height:68rpx;padding:0 12rpx}.textarea{height:130rpx;padding:11rpx;line-height:1.5}.channel-picker{display:flex;align-items:center;justify-content:space-between;gap:8rpx;padding:15rpx 12rpx;border:1rpx solid #dfd4c8;border-radius:12rpx;background:#fffdf9;color:#796d62;font-size:16rpx}.copyright-box{margin-top:17rpx;padding:13rpx;border-radius:14rpx;background:#f7f2e9}.copyright-statement{display:block;margin-top:10rpx;color:#6d6258;font-size:14rpx;line-height:1.6}.check-row{display:flex;align-items:flex-start;gap:6rpx;margin-top:12rpx;color:#6d6258;font-size:14rpx;line-height:1.45}.check-row checkbox{transform:scale(.75);transform-origin:top left}.copyright-note{display:block;margin-top:10rpx;color:#947f70;font-size:14rpx;line-height:1.5}.submit{height:74rpx;margin-top:17rpx;border-radius:13rpx;background:#5e7c6f;color:#fff;font-size:21rpx;font-weight:850}.submit[loading]{opacity:.65}.history-row{display:flex;align-items:center;justify-content:space-between;gap:10rpx;padding:13rpx 0;border-top:1rpx solid #eee2d6}.history-row view{display:flex;min-width:0;flex-direction:column;gap:5rpx}.history-row view text:first-child{color:#4b4036;font-size:18rpx;font-weight:800}.history-row view text:last-child{color:#97897d;font-size:13rpx}.history-status{padding:5rpx 7rpx;border-radius:8rpx;background:#edf4ed;color:#5f7b6e;font-size:13rpx;white-space:nowrap}
</style>
