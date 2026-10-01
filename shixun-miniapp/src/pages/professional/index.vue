<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" aria-label="返回" @tap="goBack">‹</view>
      <text class="nav-title">{{ channelEntry ? '渠道合作' : '产品智造' }}</text>
      <view class="nav-menu" aria-label="更多操作" @tap="openPageMenu"><text>•••</text><view /><text>◎</text></view>
    </view>

    <view class="content">
      <view class="intro">
        <text class="intro-title">上传作品</text>
        <text class="intro-copy">{{ channelEntry ? '上传已有作品，平台审核确认可落地后，将提报博物馆审核；通过后打样、上架渠道售卖。' : '提交已有作品，审核通过后即可打样、生产或进入渠道合作。' }}</text>
      </view>

      <view class="section-heading"><text><b>01</b> 上传作品包</text><text>必填</text></view>
      <view class="upload-card">
        <view class="file-picker" :class="{ selected: filePath }" @tap="chooseZip">
          <view class="upload-icon">↥</view>
          <text class="file-title">{{ fileName || '选择作品包文件' }}</text>
          <text class="file-subtitle">{{ filePath ? `${formatSize(fileSize)} · 点击可重新选择` : 'ZIP格式 · 不超过100MB' }}</text>
        </view>
        <view class="package-checklist"><text>✓ 效果图(三视角)</text><text>✓ 尺寸规格</text><text>✓ 材质与工艺说明</text><text>✓ 版权材料</text></view>
        <text v-if="fileError" class="error">{{ fileError }}</text>
      </view>

      <view v-if="linkedContext" class="linked-context"><text>已关联产品号：{{ linkedContext.productNo || '待生成' }}</text><text>{{ linkedContext.productName || '对话式创作作品' }} · 作品 #{{ linkedContext.assetId }}</text></view>

      <view class="section-heading"><text><b>02</b> 作品信息</text><text>名称必填</text></view>
      <view class="form-card">
        <label class="field-label">作品名称<text>*</text></label>
        <input v-model.trim="title" class="input" maxlength="100" placeholder="请输入作品名称，如：青铜纹样冰箱贴" />
        <label class="field-label">作品介绍<text>*</text></label>
        <textarea v-model.trim="note" class="textarea" maxlength="1200" placeholder="创作理念、文化来源、设计思路……" />
        <label class="field-label">产品信息<text>*</text></label>
        <input v-model.trim="productInfo" class="input" maxlength="300" placeholder="如：60×60×3mm · 锌合金 · 珐琅工艺 · 首批500个" />
      </view>

      <view class="section-heading"><text><b>03</b> 合作方向</text><text>单选</text></view>
      <view class="direction-card">
        <view v-if="!channelEntry" class="direction-option" :class="{ active: purpose === 'personal' }" @tap="purpose = 'personal'">
          <view class="radio"><view /></view><view><text>产品打样</text><text>先把作品做成实物样品</text></view>
        </view>
        <view class="direction-option" :class="{ active: purpose === 'museum_sale' }" @tap="purpose = 'museum_sale'">
          <view class="radio"><view /></view><view><text>渠道合作</text><text>提交馆方或景区进行合作审核</text></view>
        </view>
        <view v-if="purpose === 'museum_sale'" class="channel-fields">
          <picker :range="provinces" :value="provinceIndex" @change="chooseProvince"><view class="picker">{{ province || '选择省 / 直辖市' }}<text>›</text></view></picker>
          <picker :range="museumNames" :value="museumIndex" :disabled="!province || !museumNames.length" @change="chooseMuseum"><view class="picker">{{ museum?.name || '选择合作博物馆或景区' }}<text>›</text></view></picker>
          <text v-if="!museum" class="field-tip">渠道合作必须选择一个目标博物馆或景区。</text>
        </view>
      </view>

      <view class="check-row" @tap="copyrightConfirmed = !copyrightConfirmed"><text class="checkbox">{{ copyrightConfirmed ? '✓' : '' }}</text><text>我确认已合法取得作品及素材的使用授权，并同意配合平台与馆方审核。</text></view>
      <button class="submit" :loading="loading" :disabled="loading" @tap="submit">提交审核</button>
      <text class="footer">平台将在1-2个工作日内审核作品的完整性、版权信息与生产可行性，结果可在“消息通知”和“我的作品”里进行查询。</text>

      <view class="record-toggle" @tap="showRecords = !showRecords"><text>{{ showRecords ? '收起提交记录' : '查看我的提交记录' }}</text><text>{{ showRecords ? '⌃' : '⌄' }}</text></view>

      <view v-if="showRecords" class="records-card">
        <view class="records-head"><text>我的提交</text><text @tap="loadRecords">刷新</text></view>
        <view v-if="!records.length" class="empty">还没有作品包提交记录</view>
        <view v-for="record in records" :key="record.id || record.submissionNo" class="record">
          <view class="record-top"><view><text class="product-no">产品号：{{ record.productNo || '未关联产品号' }}</text><text class="record-title">{{ record.title || record.originalName }}</text><text class="record-no">{{ record.submissionNo }} · {{ formatDate(record.createdAt) }}</text></view><text class="status" :class="`status-${record.status}`">{{ statusLabel(record.status) }}</text></view>
          <text class="record-meta">{{ record.purpose === 'museum_sale' ? `渠道合作：${record.museumName || '待选择'}` : '产品打样' }}</text>
          <view v-if="['approved', 'processing'].includes(String(record.status)) && record.quotedSampleFeeYuan" class="quote-box">
            <text class="quote-title">打样报价单</text><text class="quote-line">费用：¥{{ fee(record.quotedSampleFeeYuan) }} · 预计交期：{{ record.quotedSampleLeadTime || '待确认' }}</text>
            <text v-if="record.quotedSampleNote" class="quote-line">说明：{{ record.quotedSampleNote }}</text><text class="quote-status">{{ paymentStatusLabel(record.samplePaymentStatus) }}</text>
            <button v-if="record.samplePaymentStatus === 'unpaid'" class="quote-pay" size="mini" @tap.stop="payQuote(record)">支付打样费</button>
          </view>
          <text v-if="record.reviewComment" class="record-comment">审核意见：{{ record.reviewComment }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getMuseums, getMyProfessionalSubmissions, uploadProfessionalSubmission, type ProfessionalSubmission } from '../../api/creative'
import { getSession, requireSession } from '../../utils/session'

const filePath = ref('')
const fileName = ref('')
const fileSize = ref(0)
const fileError = ref('')
const title = ref('')
const note = ref('')
const productInfo = ref('')
const purpose = ref<'personal' | 'museum_sale'>('personal')
const channelEntry = ref(false)
const copyrightConfirmed = ref(false)
const loading = ref(false)
const records = ref<ProfessionalSubmission[]>([])
const museums = ref<any[]>([])
const province = ref('')
const museum = ref<any>(null)
const provinceIndex = ref(0)
const museumIndex = ref(0)
const PROFESSIONAL_SUBMISSION_CONTEXT_KEY = 'professional_submission_context'
const PENDING_PRODUCT_PACKAGE_KEY = 'pending_product_package'
const linkedContext = ref<Record<string, any> | null>(null)
const showRecords = ref(false)
const provinces = computed(() => [...new Set(museums.value.map(item => item.province).filter(Boolean))])
const filteredMuseums = computed(() => museums.value.filter(item => item.province === province.value))
const museumNames = computed(() => filteredMuseums.value.map(item => `${item.name} · ${item.channelType === 'scenic_spot' ? '景区' : '博物馆'}`))

function goBack() {
  if (getCurrentPages().length > 1) { uni.navigateBack(); return }
  uni.reLaunch({ url: '/pages/home/index' })
}

function showRequirements() {
  uni.showModal({ title: '作品包要求', content: '请上传不超过100MB的ZIP文件，并包含效果图（三视角）、尺寸规格、材质与工艺说明以及版权材料。', showCancel: false, confirmText: '我知道了' })
}

function openPageMenu() {
  uni.showActionSheet({ itemList: ['更换ZIP作品包', '查看提交记录', '查看作品包要求'], success: result => {
    if (result.tapIndex === 0) chooseZip()
    else if (result.tapIndex === 1) showRecords.value = true
    else if (result.tapIndex === 2) showRequirements()
  } })
}

function restoreLinkedContext() {
  const raw = uni.getStorageSync(PROFESSIONAL_SUBMISSION_CONTEXT_KEY)
  if (!raw || typeof raw !== 'object' || Number(raw.assetId) <= 0) {
    linkedContext.value = null
    return
  }
  // Storage is shared by accounts on the same device. Ignore a context left
  // by another account instead of sending its asset id with this upload.
  const owner = String(raw.userName || '').trim()
  const current = String(getSession()?.user?.username || '').trim()
  if (!owner || !current || owner !== current) {
    uni.removeStorageSync(PROFESSIONAL_SUBMISSION_CONTEXT_KEY)
    linkedContext.value = null
    return
  }
  linkedContext.value = { ...raw, assetId: Number(raw.assetId) }
}

function rememberPendingPackage() {
  if (!filePath.value) return
  uni.setStorageSync(PENDING_PRODUCT_PACKAGE_KEY, {
    path: filePath.value,
    name: fileName.value,
    size: fileSize.value,
    userName: String(getSession()?.user?.username || '').trim(),
    selectedAt: Date.now(),
  })
}

function restorePendingPackage() {
  if (filePath.value) return
  const raw = uni.getStorageSync(PENDING_PRODUCT_PACKAGE_KEY)
  const currentUser = String(getSession()?.user?.username || '').trim()
  const selectedAt = Number(raw?.selectedAt || 0)
  const valid = raw && typeof raw === 'object'
    && String(raw.userName || '').trim() === currentUser
    && /\.zip$/i.test(String(raw.name || ''))
    && String(raw.path || '').trim()
    && Number(raw.size || 0) <= 100 * 1024 * 1024
    && Date.now() - selectedAt < 30 * 60 * 1000
  if (!valid) {
    if (raw) uni.removeStorageSync(PENDING_PRODUCT_PACKAGE_KEY)
    return
  }
  filePath.value = String(raw.path)
  fileName.value = String(raw.name)
  fileSize.value = Number(raw.size || 0)
  if (!title.value) title.value = fileName.value.replace(/\.zip$/i, '')
}

function chooseZip() {
  fileError.value = ''
  const chooser = (uni as any).chooseMessageFile
  if (typeof chooser !== 'function') {
    uni.showToast({ title: '当前版本暂不支持选择 ZIP 文件，请更新微信后重试', icon: 'none' })
    return
  }
  chooser({ count: 1, type: 'file', extension: ['zip'], success: (result: any) => {
    const file = result?.tempFiles?.[0]
    if (!file?.path) return
    const name = String(file.name || file.path).trim()
    if (!/\.zip$/i.test(name)) { fileError.value = '请选择 ZIP 格式的专业作品包'; return }
    if (Number(file.size || 0) > 100 * 1024 * 1024) { fileError.value = 'ZIP 作品包不能超过 100MB'; return }
    filePath.value = file.path
    fileName.value = name
    fileSize.value = Number(file.size || 0)
    rememberPendingPackage()
  }, fail: (error: any) => {
    if (!/cancel/i.test(String(error?.errMsg || ''))) fileError.value = '文件选择失败，请重新点击选择 ZIP 作品包'
  } })
}
function chooseProvince(event: any) { provinceIndex.value = Number(event.detail.value); province.value = provinces.value[provinceIndex.value] || ''; museum.value = null; museumIndex.value = 0 }
function chooseMuseum(event: any) { museumIndex.value = Number(event.detail.value); museum.value = filteredMuseums.value[museumIndex.value] || null }
async function loadRecords() { try { records.value = await getMyProfessionalSubmissions() } catch (error: any) { uni.showToast({ title: error?.message || '提交记录加载失败', icon: 'none' }) } }
async function submit() {
  if (loading.value) return
  if (!filePath.value) return uni.showToast({ title: '请先选择ZIP作品包', icon: 'none' })
  if (!title.value.trim()) return uni.showToast({ title: '请填写作品名称', icon: 'none' })
  if (!note.value.trim()) return uni.showToast({ title: '请填写作品介绍', icon: 'none' })
  if (!productInfo.value.trim()) return uni.showToast({ title: '请填写产品信息', icon: 'none' })
  if (purpose.value === 'museum_sale' && !museum.value) return uni.showToast({ title: '请选择合作博物馆或景区', icon: 'none' })
  if (!copyrightConfirmed.value) return uni.showToast({ title: '请先确认作品及素材授权', icon: 'none' })
  loading.value = true
  try {
    const formData: Record<string, string> = {
      title: title.value,
      note: `${note.value.trim()}\n\n产品信息：${productInfo.value.trim()}`,
      purpose: purpose.value,
      museumId: museum.value?.id == null ? '' : String(museum.value.id),
      museumName: museum.value?.name || '',
    }
    const assetId = Number(linkedContext.value?.assetId || 0)
    if (Number.isFinite(assetId) && assetId > 0) formData.assetId = String(assetId)
    const productNo = String(linkedContext.value?.productNo || '').trim()
    if (productNo) formData.productNo = productNo
    const productId = String(linkedContext.value?.productId || '').trim()
    if (productId) formData.productId = productId
    const result = await uploadProfessionalSubmission(filePath.value, formData)
    await loadRecords()
    filePath.value = ''; fileName.value = ''; fileSize.value = 0; title.value = ''; note.value = ''; productInfo.value = ''; copyrightConfirmed.value = false
    // A successful package starts its own review record. Do not accidentally
    // attach a later, unrelated ZIP to the previous conversation's product.
    uni.removeStorageSync(PROFESSIONAL_SUBMISSION_CONTEXT_KEY)
    uni.removeStorageSync(PENDING_PRODUCT_PACKAGE_KEY)
    linkedContext.value = null
    showRecords.value = true
    uni.showModal({ title: '提交成功', content: `${result?.submissionNo || '作品包'}已进入审核，结果会显示在本页和“我的作品”中。`, showCancel: false })
  } catch (error: any) { uni.showToast({ title: error?.message || '提交失败，请稍后重试', icon: 'none' }) } finally { loading.value = false }
}
function formatSize(size: number) { return size ? `${(size / 1024 / 1024).toFixed(2)} MB` : '文件已选择' }
function formatDate(value?: string) { return value ? String(value).slice(0, 16).replace('T', ' ') : '' }
function statusLabel(status?: string) { return status === 'processing' ? '生产中' : status === 'approved' ? '已通过' : status === 'rejected' ? '需修改' : '审核中' }
function fee(value: any) { return Number(value || 0).toFixed(2).replace(/\.00$/, '') }
function paymentStatusLabel(status?: string) { return ({ unpaid: '待支付打样费', pending: '支付处理中', manual_review: '待管理员核验', paid: '已支付，进入生产' } as Record<string, string>)[String(status || '')] || '报价待确认' }
function payQuote(record: ProfessionalSubmission) {
  if (!record.id || record.samplePaymentStatus !== 'unpaid') return
  uni.navigateTo({ url: `/pages/sample-payment/index?professionalSubmissionId=${encodeURIComponent(String(record.id))}` })
}
onMounted(async () => {
  if (!requireSession()) return
  restoreLinkedContext()
  restorePendingPackage()
  await Promise.all([loadRecords(), getMuseums().then(data => { museums.value = data }).catch(() => {})])
})
onShow(() => {
  if (getSession()) { restoreLinkedContext(); restorePendingPackage() }
  else linkedContext.value = null
})
onLoad(query => {
  channelEntry.value = String(query?.entry || '') === 'channel'
  if (channelEntry.value) purpose.value = 'museum_sale'
  showRecords.value = String(query?.entry || '') !== 'production'
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;box-sizing:border-box;background:linear-gradient(150deg,#fff 0%,#f5fcfa 42%,#eaf8f5 100%);color:#252a28}.nav-bar{position:fixed;z-index:10;top:0;right:0;left:0;display:grid;grid-template-columns:150rpx minmax(0,1fr) 190rpx;align-items:end;height:calc(126rpx + env(safe-area-inset-top));box-sizing:border-box;padding:calc(36rpx + env(safe-area-inset-top)) 26rpx 18rpx;background:rgba(255,255,255,.97)}.nav-title{color:#080a09;font-size:40rpx;font-weight:700;line-height:58rpx;text-align:center}.nav-back{justify-self:start;width:64rpx;height:58rpx;color:#202624;font-size:64rpx;font-weight:300;line-height:48rpx}.nav-menu{display:flex;align-items:center;justify-content:space-around;justify-self:end;width:172rpx;height:62rpx;box-sizing:border-box;padding:0 20rpx;border:1rpx solid rgba(151,151,151,.28);border-radius:34rpx;background:#fff;color:#050706;font-size:28rpx}.nav-menu view{width:1rpx;height:37rpx;background:rgba(151,151,151,.35)}.nav-menu text:last-child{font-size:39rpx}.content{padding:calc(155rpx + env(safe-area-inset-top)) 34rpx calc(70rpx + env(safe-area-inset-bottom))}.intro{display:flex;flex-direction:column}.intro-title{color:#111513;font-size:40rpx;font-weight:700;line-height:58rpx}.intro-copy{margin-top:5rpx;color:#555f5b;font-size:22rpx;line-height:42rpx}.section-heading{display:flex;align-items:center;justify-content:space-between;margin:18rpx 0 24rpx;color:#141816}.section-heading>text:first-child{font-size:32rpx;font-weight:700}.section-heading b{margin-right:16rpx;font-size:32rpx}.section-heading>text:last-child{color:#777f7c;font-size:24rpx}.upload-card,.form-card,.direction-card,.records-card{padding:22rpx 24rpx;border:1rpx solid #dbf3ee;border-radius:32rpx;background:#fff;box-shadow:0 0 12rpx rgba(18,237,222,.12)}.file-picker{display:flex;align-items:center;justify-content:center;min-height:250rpx;box-sizing:border-box;flex-direction:column;padding:28rpx;border:1rpx dashed #aeb6b3;border-radius:32rpx;background:#f6f7f7}.file-picker.selected{border-color:#55c8b2;background:#f2fcf9}.upload-icon{display:grid;place-items:center;width:60rpx;height:60rpx;border:3rpx solid #69b6a8;border-radius:12rpx;color:#52b7a3;font-size:48rpx;font-weight:700;line-height:1}.file-title{overflow:hidden;max-width:100%;margin-top:22rpx;color:#323735;font-size:30rpx;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.file-subtitle{margin-top:2rpx;color:#6e7774;font-size:22rpx}.package-checklist{display:flex;flex-wrap:wrap;gap:12rpx 26rpx;padding:30rpx 35rpx 2rpx;color:#555e5b;font-size:21rpx;line-height:38rpx}.error{display:block;margin:12rpx 12rpx 0;color:#b34f3d;font-size:20rpx}.linked-context{display:flex;flex-direction:column;gap:6rpx;margin-top:15rpx;padding:16rpx 20rpx;border:1rpx solid #cce9e2;border-radius:15rpx;background:#f1fbf8;color:#55776d;font-size:19rpx}.linked-context text:first-child{font-size:22rpx;font-weight:700}.form-card{padding-bottom:28rpx}.field-label{display:block;margin:4rpx 14rpx 12rpx;color:#333936;font-size:28rpx;font-weight:600}.field-label text{color:#ef2929}.input,.textarea,.picker{width:100%;box-sizing:border-box;border:1rpx dashed #bfc5c3;border-radius:30rpx;background:#f7f8f8;color:#333936;font-size:22rpx}.input{height:60rpx;margin-bottom:20rpx;padding:0 32rpx}.textarea{height:145rpx;margin-bottom:20rpx;padding:20rpx 32rpx;line-height:1.55}.direction-card{display:flex;gap:28rpx;flex-direction:column;padding:30rpx 24rpx}.direction-option{display:flex;align-items:center;gap:18rpx;min-height:110rpx;box-sizing:border-box;padding:20rpx;border:1rpx solid transparent;border-radius:32rpx;background:#f5f5f5}.direction-option.active{border-color:#63d5bf;background:#eafaf6}.direction-option>view:last-child{display:flex;min-width:0;flex:1;flex-wrap:wrap;align-items:baseline;gap:6rpx 12rpx}.direction-option>view:last-child text:first-child{color:#303633;font-size:28rpx;font-weight:700}.direction-option>view:last-child text:last-child{color:#a0a7a4;font-size:21rpx}.radio{display:grid;place-items:center;flex:none;width:34rpx;height:34rpx;border:2rpx solid #fff;border-radius:50%;background:#fff}.direction-option.active .radio{border-color:#62d5bf}.direction-option.active .radio view{width:18rpx;height:18rpx;border-radius:50%;background:#62d5bf}.channel-fields{display:flex;gap:14rpx;flex-direction:column}.picker{display:flex;align-items:center;justify-content:space-between;height:72rpx;padding:0 25rpx;background:#fff}.picker text{color:#52aa9b;font-size:30rpx}.field-tip{color:#a46755;font-size:19rpx}.check-row{display:flex;align-items:flex-start;gap:16rpx;margin:30rpx 28rpx 0;color:#303735;font-size:21rpx;line-height:1.55}.checkbox{display:grid;place-items:center;flex:none;width:34rpx;height:34rpx;border:2rpx solid #9ba4a0;border-radius:8rpx;background:#fff;color:#fff}.check-row .checkbox:not(:empty){border-color:#53b8a4;background:#53b8a4}.submit{height:88rpx;margin:54rpx 52rpx 0;border-radius:44rpx;background:linear-gradient(148deg,#69ebc7,#49a59c);color:#fff;font-size:34rpx;font-weight:600;line-height:88rpx}.submit::after,.quote-pay::after{border:0}.submit[disabled]{opacity:.6}.footer{display:block;margin:76rpx 50rpx 0;color:#666f6b;font-size:20rpx;line-height:1.6;text-align:center}.record-toggle{display:flex;align-items:center;justify-content:center;gap:10rpx;margin-top:35rpx;color:#3d9d8c;font-size:22rpx}.records-card{margin-top:20rpx}.records-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8rpx}.records-head text:first-child{font-size:28rpx;font-weight:700}.records-head text:last-child{color:#3d9d8c;font-size:20rpx}.empty{padding:28rpx 0;color:#89928e;font-size:20rpx;text-align:center}.record{padding:20rpx 0;border-top:1rpx solid #e5efec}.record-top{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx}.record-title,.record-no,.record-meta,.record-comment,.product-no{display:block}.product-no{color:#53776b;font-size:18rpx}.record-title{margin-top:4rpx;font-size:23rpx;font-weight:700}.record-no{margin-top:5rpx;color:#8d9692;font-size:17rpx}.record-meta{margin-top:9rpx;color:#65716c;font-size:19rpx}.record-comment{margin-top:8rpx;color:#a25243;font-size:19rpx}.status{padding:5rpx 10rpx;border-radius:99rpx;font-size:17rpx}.status-review{color:#8a6a43;background:#f7ecd9}.status-approved,.status-processing{color:#4d7a65;background:#e5f2e8}.status-rejected{color:#a25243;background:#fae8e1}.quote-box{margin-top:14rpx;padding:15rpx;border:1rpx solid #d5e5d8;border-radius:13rpx;background:#eff7f0}.quote-title,.quote-line,.quote-status{display:block}.quote-title{color:#47735b;font-size:21rpx;font-weight:700}.quote-line{margin-top:6rpx;color:#607b6a;font-size:19rpx}.quote-status{margin-top:8rpx;color:#7e6e5e;font-size:18rpx}.quote-pay{height:58rpx;margin-top:11rpx;padding:0 20rpx;border:0;border-radius:10rpx;background:#557a66;color:#fff;font-size:20rpx;line-height:58rpx}@media(max-width:360px){.nav-bar{grid-template-columns:110rpx minmax(0,1fr) 165rpx;padding-right:18rpx;padding-left:18rpx}.nav-menu{width:152rpx}.content{padding-right:24rpx;padding-left:24rpx}.submit{margin-right:30rpx;margin-left:30rpx}}
</style>
