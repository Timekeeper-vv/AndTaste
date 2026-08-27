<template>
  <view class="page">
    <view class="hero">
      <text class="eyebrow">PROFESSIONAL CREATOR</text>
      <text class="title">专业创作工作台</text>
      <text class="sub">提交完整作品包，让设计、工艺、版权和渠道信息进入同一条审核流程。</text>
    </view>

    <view class="notice"><text>专业作品审核</text><text>仅接受 ZIP 作品包，最大 100MB。建议包含效果图、尺寸、材质、工艺说明和版权材料。</text><text class="web-hint">专业作品包建议用电脑端访问 https://zhijiansk.com/，体验更佳哦。</text></view>

    <view class="card">
      <text class="section-title">1 · 上传作品包</text>
      <view class="file-picker" :class="{ selected: filePath }" @tap="chooseZip">
        <text class="file-mark">{{ filePath ? 'ZIP' : '+' }}</text>
        <view><text>{{ fileName || '选择专业作品 ZIP' }}</text><text>{{ filePath ? formatSize(fileSize) : '点击选择文件，不能直接上传文件夹' }}</text></view>
        <text class="arrow">›</text>
      </view>
      <text v-if="fileError" class="error">{{ fileError }}</text>

      <text class="section-title">2 · 填写作品信息</text>
      <input v-model.trim="title" class="input" maxlength="100" placeholder="作品名称，例如：青铜纹样系列冰箱贴" />
      <textarea v-model.trim="note" class="textarea" maxlength="1200" placeholder="补充尺寸、材质、工艺、预计数量、版权来源等信息（可选）" />

      <text class="section-title">3 · 选择创作去向</text>
      <view class="purpose-row">
        <view class="purpose" :class="{ active: purpose === 'personal' }" @tap="purpose = 'personal'"><text>个人创作</text><text>自用、收藏或后续自行对接生产</text></view>
        <view class="purpose" :class="{ active: purpose === 'museum_sale' }" @tap="purpose = 'museum_sale'"><text>渠道售卖</text><text>提交景区或博物馆审核</text></view>
      </view>
      <template v-if="purpose === 'museum_sale'">
        <picker :range="provinces" :value="provinceIndex" @change="chooseProvince"><view class="picker">{{ province || '选择省 / 直辖市' }}<text>›</text></view></picker>
        <picker :range="museumNames" :value="museumIndex" :disabled="!province || !museumNames.length" @change="chooseMuseum"><view class="picker">{{ museum?.name || '选择博物馆或景区' }}<text>›</text></view></picker>
        <text v-if="!museum" class="field-tip">渠道售卖作品必须选择一个目标博物馆或景区。</text>
      </template>

      <view class="check-row" @tap="copyrightConfirmed = !copyrightConfirmed"><text class="checkbox">{{ copyrightConfirmed ? '✓' : '' }}</text><text>我确认已获得作品中图片、字体、肖像、商标、文物或景区元素的使用授权，并愿意配合平台审核。</text></view>
      <button class="submit" :loading="loading" :disabled="loading || !canSubmit" @tap="submit">提交专业作品包</button>
      <text class="footer">平台仅提供创作与审核流转服务；专业作品的版权、真实性、尺寸和生产可行性仍需提交者负责并接受人工复核。</text>
    </view>

    <view class="card records-card">
      <view class="records-head"><text class="section-title">我的专业提交</text><text @tap="loadRecords">刷新</text></view>
      <view v-if="!records.length" class="empty">还没有专业作品提交记录</view>
      <view v-for="record in records" :key="record.id || record.submissionNo" class="record">
        <view class="record-top"><view><text class="record-title">{{ record.title || record.originalName }}</text><text class="record-no">{{ record.submissionNo }} · {{ formatDate(record.createdAt) }}</text></view><text class="status" :class="`status-${record.status}`">{{ statusLabel(record.status) }}</text></view>
        <text class="record-meta">{{ record.purpose === 'museum_sale' ? `渠道：${record.museumName || '待选择'}` : '个人创作' }}</text>
        <view v-if="['approved', 'processing'].includes(String(record.status)) && record.quotedSampleFeeYuan" class="quote-box">
          <text class="quote-title">打样报价单</text>
          <text class="quote-line">费用：¥{{ fee(record.quotedSampleFeeYuan) }} · 预计交期：{{ record.quotedSampleLeadTime || '待确认' }}</text>
          <text v-if="record.quotedSampleNote" class="quote-line">说明：{{ record.quotedSampleNote }}</text>
          <text class="quote-status">{{ paymentStatusLabel(record.samplePaymentStatus) }}</text>
          <button v-if="record.samplePaymentStatus === 'unpaid'" class="quote-pay" size="mini" @tap.stop="payQuote(record)">支付打样费</button>
        </view>
        <text v-if="record.reviewComment" class="record-comment">审核意见：{{ record.reviewComment }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getMuseums, getMyProfessionalSubmissions, uploadProfessionalSubmission, type ProfessionalSubmission } from '../../api/creative'
import { requireSession } from '../../utils/session'

const filePath = ref('')
const fileName = ref('')
const fileSize = ref(0)
const fileError = ref('')
const title = ref('')
const note = ref('')
const purpose = ref<'personal' | 'museum_sale'>('personal')
const copyrightConfirmed = ref(false)
const loading = ref(false)
const records = ref<ProfessionalSubmission[]>([])
const museums = ref<any[]>([])
const province = ref('')
const museum = ref<any>(null)
const provinceIndex = ref(0)
const museumIndex = ref(0)
const provinces = computed(() => [...new Set(museums.value.map(item => item.province).filter(Boolean))])
const filteredMuseums = computed(() => museums.value.filter(item => item.province === province.value))
const museumNames = computed(() => filteredMuseums.value.map(item => `${item.name} · ${item.channelType === 'scenic_spot' ? '景区' : '博物馆'}`))
const canSubmit = computed(() => Boolean(filePath.value && copyrightConfirmed.value && (purpose.value === 'personal' || museum.value)))

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
  }, fail: (error: any) => {
    if (!/cancel/i.test(String(error?.errMsg || ''))) fileError.value = '文件选择失败，请重新点击选择 ZIP 作品包'
  } })
}
function chooseProvince(event: any) { provinceIndex.value = Number(event.detail.value); province.value = provinces.value[provinceIndex.value] || ''; museum.value = null; museumIndex.value = 0 }
function chooseMuseum(event: any) { museumIndex.value = Number(event.detail.value); museum.value = filteredMuseums.value[museumIndex.value] || null }
async function loadRecords() { try { records.value = await getMyProfessionalSubmissions() } catch (error: any) { uni.showToast({ title: error?.message || '提交记录加载失败', icon: 'none' }) } }
async function submit() {
  if (!canSubmit.value || loading.value) return
  loading.value = true
  try {
    const result = await uploadProfessionalSubmission(filePath.value, { title: title.value, note: note.value, purpose: purpose.value, museumId: museum.value?.id == null ? '' : String(museum.value.id), museumName: museum.value?.name || '' })
    await loadRecords()
    filePath.value = ''; fileName.value = ''; fileSize.value = 0; title.value = ''; note.value = ''; copyrightConfirmed.value = false
    uni.showModal({ title: '提交成功', content: `${result?.submissionNo || '作品包'}已进入专业审核，审核结果会显示在本页。`, showCancel: false })
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
  await Promise.all([loadRecords(), getMuseums().then(data => { museums.value = data }).catch(() => {})])
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:34rpx 28rpx 70rpx;box-sizing:border-box;background:linear-gradient(150deg,#faf8f3,#eee7dc);color:#332e29}.hero{display:flex;flex-direction:column;padding:20rpx 6rpx 24rpx}.eyebrow{color:#638174;font-size:17rpx;font-weight:800;letter-spacing:2.5rpx}.title{margin-top:12rpx;font-family:"Songti SC","STSong",serif;font-size:49rpx;font-weight:800}.sub{margin-top:12rpx;color:#7e756b;font-size:23rpx;line-height:1.65}.notice{display:flex;flex-direction:column;gap:8rpx;padding:21rpx;border:1rpx solid #d9e7dc;border-radius:17rpx;background:#eff6f0;color:#567565;font-size:21rpx;line-height:1.55}.notice text:first-child{font-size:25rpx;font-weight:800}.card{margin-top:20rpx;padding:25rpx;border:1rpx solid rgba(115,98,78,.14);border-radius:22rpx;background:rgba(255,253,249,.9);box-shadow:0 10rpx 25rpx rgba(72,57,41,.06)}.section-title{display:block;margin-bottom:15rpx;color:#3c3832;font-size:27rpx;font-weight:800}.file-picker{display:flex;align-items:center;gap:15rpx;padding:20rpx;border:1rpx dashed #cdbfb0;border-radius:16rpx;background:#fbf7f0}.file-picker.selected{border-style:solid;border-color:#8fa99a;background:#eff5ef}.file-mark{display:grid;place-items:center;flex:none;width:64rpx;height:64rpx;border-radius:15rpx;background:#e9f0e9;color:#5a806e;font-size:20rpx;font-weight:800}.file-picker view{display:flex;min-width:0;flex:1;flex-direction:column}.file-picker view text:first-child{overflow:hidden;color:#3d3933;font-size:23rpx;text-overflow:ellipsis;white-space:nowrap}.file-picker view text:last-child{margin-top:6rpx;color:#958a7e;font-size:18rpx}.arrow{color:#9a6a53;font-size:37rpx}.error{display:block;margin-top:9rpx;color:#b34f3d;font-size:19rpx}.input,.textarea,.picker{width:100%;box-sizing:border-box;margin-bottom:14rpx;border:1rpx solid #e4dbcf;border-radius:13rpx;background:#fbf8f2;color:#403a34;font-size:23rpx}.input{height:80rpx;padding:0 18rpx}.textarea{height:160rpx;padding:17rpx;line-height:1.55}.purpose-row{display:flex;gap:12rpx;margin-bottom:15rpx}.purpose{flex:1;padding:17rpx 14rpx;border:1rpx solid #e4dbcf;border-radius:14rpx;background:#fbf8f2}.purpose.active{border-color:#8fa99a;background:#eef5ef}.purpose text{display:block}.purpose text:first-child{font-size:23rpx;font-weight:800}.purpose text:last-child{margin-top:6rpx;color:#8a8075;font-size:18rpx;line-height:1.45}.picker{display:flex;justify-content:space-between;align-items:center;height:78rpx;padding:0 18rpx}.picker text{color:#a56b52;font-size:32rpx}.field-tip{display:block;margin:-5rpx 0 13rpx;color:#ae7058;font-size:18rpx}.check-row{display:flex;gap:10rpx;align-items:flex-start;margin-top:7rpx;color:#766c61;font-size:19rpx;line-height:1.55}.checkbox{display:grid;place-items:center;flex:none;width:33rpx;height:33rpx;border:1rpx solid #9caf9f;border-radius:7rpx;color:#fff;background:#fff}.check-row .checkbox:not(:empty){background:#638174}.submit{height:88rpx;line-height:88rpx;margin-top:22rpx;border-radius:16rpx;background:linear-gradient(135deg,#3c3934,#648173);color:#fff;font-size:27rpx;font-weight:800}.submit[disabled]{opacity:.45}.footer{display:block;margin-top:17rpx;color:#978d82;font-size:17rpx;line-height:1.55;text-align:center}.records-card{padding-bottom:12rpx}.records-head{display:flex;justify-content:space-between;align-items:center}.records-head .section-title{margin-bottom:4rpx}.records-head>text:last-child{color:#638174;font-size:20rpx}.empty{padding:25rpx 0;color:#978d82;font-size:20rpx;text-align:center}.record{padding:17rpx 0;border-top:1rpx solid #eee6dc}.record-top{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx}.record-title,.record-no,.record-meta,.record-comment{display:block}.record-title{color:#403a34;font-size:23rpx;font-weight:800}.record-no{margin-top:6rpx;color:#9a8e82;font-size:17rpx}.status{padding:5rpx 10rpx;border-radius:99rpx;font-size:17rpx}.status-review{color:#8a6a43;background:#f7ecd9}.status-approved{color:#4d7a65;background:#e5f2e8}.status-rejected{color:#a25243;background:#fae8e1}.record-meta{margin-top:10rpx;color:#756b61;font-size:19rpx}.record-comment{margin-top:8rpx;color:#a25243;font-size:19rpx;line-height:1.5}
.quote-box{margin-top:14rpx;padding:15rpx;border:1rpx solid #d5e5d8;border-radius:13rpx;background:#eff7f0}.quote-title,.quote-line,.quote-status{display:block}.quote-title{color:#47735b;font-size:21rpx;font-weight:850}.quote-line{margin-top:6rpx;color:#607b6a;font-size:19rpx;line-height:1.45}.quote-status{margin-top:8rpx;color:#7e6e5e;font-size:18rpx}.quote-pay{height:58rpx;line-height:58rpx;margin-top:11rpx;padding:0 20rpx;border:0;border-radius:10rpx;background:#557a66;color:#fff;font-size:20rpx}
.status-processing{color:#4d7a65;background:#e5f2e8}
.notice .web-hint{color:#9a6a53;font-weight:700}
</style>
