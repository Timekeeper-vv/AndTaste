<template>
  <view class="page">
    <view class="nav-bar"><view class="nav-back" @tap="goBack">‹</view><text>产品智造</text><view class="nav-menu"><text>•••</text><view /><text>◎</text></view></view>
    <view v-if="loading" class="loading">正在读取审核结果…</view>
    <view v-else-if="!record" class="loading">没有找到这条提交记录</view>
    <view v-else class="content">
      <template v-if="record.status === 'rejected'">
        <view class="result-title"><view class="result-icon rejected">!</view><text>平台审核未通过</text></view>
        <text class="result-copy">很抱歉，您的作品未通过审核，请根据审核意见修改后重新提交。</text>
        <product-card :record="record" label="未通过" tone="rejected" />
        <view class="reason-card"><text class="card-title rejected-text">审核未通过原因</text><view v-for="reason in rejectionReasons" :key="reason" class="reason"><text>×</text><text>{{ reason }}</text></view></view>
        <text class="section-title">选择修改方式</text>
        <view class="choice-card">
          <view class="choice" :class="{ active: revisionMode === 'self' }" @tap="revisionMode = 'self'"><view class="radio"><view /></view><view><text>自行修改</text><text>{{ selectedFileName || '选择修改后的ZIP作品包，重新上传' }}</text></view></view>
          <view class="choice" :class="{ active: revisionMode === 'assistant' }" @tap="revisionMode = 'assistant'"><view class="radio"><view /></view><view><text>平台协助修改</text><text>AI助手按审核意见提供调整方案</text></view><text class="credit">按实际积分</text></view>
        </view>
        <button class="main-action" :loading="submitting" @tap="handleRevision">{{ revisionMode === 'self' ? (selectedFileName ? '重新提交审核' : '选择ZIP并重新提交') : '进入AI助手修改' }}</button>
      </template>

      <template v-else-if="record.status === 'approved'">
        <view class="result-title"><view class="result-icon approved">✓</view><text>平台审核通过</text></view>
        <text class="result-copy">你的作品已通过审核，现在可以申请打样了。</text>
        <product-card :record="record" :label="record.samplePaymentStatus === 'pending' ? '待支付' : '待打样'" tone="approved" />
        <view class="sample-card">
          <text class="card-title">打样申请</text>
          <view class="quantity-row"><button @tap="changeQuantity(-1)">−</button><text>{{ quantity }}</text><button @tap="changeQuantity(1)">+</button><view><text>单位：件</text><text>建议先打样1-2件，确认效果后再批量生产。</text></view></view>
          <view class="price-row"><text>打样费 ¥{{ money(unitFee) }}/件 × {{ quantity }}</text><text>¥{{ money(totalFee) }}</text></view>
          <text v-if="record.quotedSampleLeadTime" class="quote-note">预计交期：{{ record.quotedSampleLeadTime }}</text>
        </view>
        <text class="section-title">收件信息</text>
        <view class="address-card">
          <label>收件人<text>*</text></label><input v-model.trim="recipientName" maxlength="120" placeholder="请输入收件人姓名" />
          <label>收件地址<text>*</text></label><textarea v-model.trim="recipientAddress" maxlength="500" placeholder="请输入省、市、区和详细地址" />
          <label>收件电话<text>*</text></label><input v-model.trim="recipientPhone" type="text" maxlength="40" placeholder="请输入联系电话" />
        </view>
        <button class="main-action" :loading="submitting" @tap="saveAndPay">支付¥{{ money(totalFee) }}，提交打样申请</button>
      </template>

      <template v-else>
        <view class="state-center"><view class="result-icon" :class="record.status === 'processing' ? 'approved' : 'review'">{{ record.status === 'processing' ? '✓' : '…' }}</view><text>{{ record.status === 'processing' ? '打样申请已进入生产' : '平台审核中' }}</text><text>{{ record.status === 'processing' ? '打样费已确认，生产人员正在安排后续流程。' : '平台将在1-2个工作日内完成作品完整性、版权信息与生产可行性审核。' }}</text></view>
        <product-card :record="record" :label="record.status === 'processing' ? '生产中' : '审核中'" :tone="record.status === 'processing' ? 'approved' : 'review'" />
      </template>
      <text class="footer-note">审核和支付状态会同步到“产品智造 · 我的提交”，请以页面实时状态为准。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getMyProfessionalSubmissions, resubmitProfessionalSubmission, saveProfessionalSampleApplication, type ProfessionalSubmission } from '../../api/creative'
import { requireSession } from '../../utils/session'

const submissionId = ref('')
const record = ref<ProfessionalSubmission | null>(null)
const loading = ref(true)
const submitting = ref(false)
const revisionMode = ref<'self' | 'assistant'>('self')
const selectedFilePath = ref('')
const selectedFileName = ref('')
const quantity = ref(1)
const recipientName = ref('')
const recipientPhone = ref('')
const recipientAddress = ref('')
const unitFee = computed(() => Number(record.value?.quotedSampleFeeYuan || 0))
const totalFee = computed(() => unitFee.value * quantity.value)
const rejectionReasons = computed(() => String(record.value?.reviewComment || '请根据平台审核意见完善作品包后重新提交。').split(/[\n；;]+/).map(item => item.replace(/^[-×xX•\d.、\s]+/, '').trim()).filter(Boolean))

const ProductCard = defineComponent({
  props: { record: { type: Object, required: true }, label: String, tone: String },
  setup(props) {
    return () => h('view', { class: 'product-card' }, [
      h('image', { src: '/static/ui-design/inspiration-mask.jpg', mode: 'aspectFill' }),
      h('view', { class: 'product-info' }, [h('text', String((props.record as any).title || '未命名作品')), h('text', `编号：${String((props.record as any).submissionNo || (props.record as any).productNo || '-')}`)]),
      h('text', { class: `product-status ${props.tone || ''}` }, props.label || ''),
    ])
  },
})

function goBack() { if (getCurrentPages().length > 1) uni.navigateBack(); else uni.reLaunch({ url: '/pages/product-making/index' }) }
function money(value: number) { return Number(value || 0).toFixed(2) }
function changeQuantity(delta: number) { if (record.value?.samplePaymentStatus !== 'unpaid') return; quantity.value = Math.min(10, Math.max(1, quantity.value + delta)) }
async function loadRecord() {
  if (!submissionId.value || !requireSession()) return
  loading.value = true
  try {
    const rows = await getMyProfessionalSubmissions()
    record.value = rows.find(item => String(item.id) === submissionId.value) || null
    if (record.value) {
      quantity.value = Math.max(1, Number(record.value.sampleQuantity || 1))
      recipientName.value = String(record.value.recipientName || '')
      recipientPhone.value = String(record.value.recipientPhone || '')
      recipientAddress.value = String(record.value.recipientAddress || '')
      if (record.value.status === 'approved' && record.value.samplePaymentStatus === 'pending' && record.value.recipientName && record.value.recipientPhone && record.value.recipientAddress) {
        uni.redirectTo({ url: `/pages/sample-payment/index?professionalSubmissionId=${encodeURIComponent(submissionId.value)}` })
      }
    }
  } catch (error: any) { uni.showToast({ title: error?.message || '审核结果加载失败', icon: 'none' }) }
  finally { loading.value = false }
}
function chooseRevisionZip(): Promise<void> {
  return new Promise(resolve => {
    const chooser = (uni as any).chooseMessageFile
    if (typeof chooser !== 'function') { uni.showToast({ title: '当前版本不支持选择ZIP，请更新微信', icon: 'none' }); resolve(); return }
    chooser({ count: 1, type: 'file', extension: ['zip'], success: (result: any) => {
      const file = result?.tempFiles?.[0]
      const name = String(file?.name || file?.path || '')
      if (!file?.path || !/\.zip$/i.test(name)) { uni.showToast({ title: '请选择ZIP格式作品包', icon: 'none' }); resolve(); return }
      if (Number(file?.size || 0) > 100 * 1024 * 1024) { uni.showToast({ title: 'ZIP作品包不能超过100MB', icon: 'none' }); resolve(); return }
      selectedFilePath.value = String(file.path); selectedFileName.value = name; resolve()
    }, fail: () => resolve() })
  })
}
async function handleRevision() {
  if (submitting.value || !record.value?.id) return
  if (revisionMode.value === 'assistant') {
    uni.setStorageSync('product_revision_prompt', { submissionId: record.value.id, text: `请根据以下审核意见，帮我整理这件产品的修改方案：\n${rejectionReasons.value.join('\n')}`, createdAt: Date.now() })
    uni.navigateTo({ url: '/pages/conversation-create/index?new=1&revision=1' })
    return
  }
  if (!selectedFilePath.value) { await chooseRevisionZip(); if (!selectedFilePath.value) return }
  submitting.value = true
  try {
    await resubmitProfessionalSubmission(record.value.id, selectedFilePath.value)
    uni.showToast({ title: '已重新提交审核', icon: 'success' })
    setTimeout(() => uni.redirectTo({ url: '/pages/product-making/index' }), 600)
  } catch (error: any) { uni.showToast({ title: error?.message || '重新提交失败', icon: 'none' }) }
  finally { submitting.value = false }
}
async function saveAndPay() {
  if (submitting.value || !record.value?.id) return
  if (!recipientName.value) return uni.showToast({ title: '请填写收件人', icon: 'none' })
  if (!/^[0-9+()\- ]{6,40}$/.test(recipientPhone.value)) return uni.showToast({ title: '请填写正确的收件电话', icon: 'none' })
  if (!recipientAddress.value) return uni.showToast({ title: '请填写收件地址', icon: 'none' })
  submitting.value = true
  try {
    await saveProfessionalSampleApplication(record.value.id, { quantity: quantity.value, recipientName: recipientName.value, recipientPhone: recipientPhone.value, recipientAddress: recipientAddress.value })
    uni.navigateTo({ url: `/pages/sample-payment/index?professionalSubmissionId=${encodeURIComponent(String(record.value.id))}` })
  } catch (error: any) { uni.showToast({ title: error?.message || '打样申请保存失败', icon: 'none' }) }
  finally { submitting.value = false }
}

onLoad(options => { submissionId.value = String(options?.id || '') })
onShow(loadRecord)
</script>

<style scoped lang="scss">
.page { min-height: 100vh; background: linear-gradient(180deg,#fff 0%,#effafa 100%); color: #151817; }
.nav-bar { position: fixed; z-index: 10; top: 0; right: 0; left: 0; display: grid; grid-template-columns: 150rpx minmax(0,1fr) 190rpx; align-items: end; height: calc(126rpx + env(safe-area-inset-top)); box-sizing: border-box; padding: calc(36rpx + env(safe-area-inset-top)) 26rpx 18rpx; background: rgba(255,255,255,.97); }
.nav-bar>text { font-size: 40rpx; font-weight: 700; text-align: center; }
.nav-back { font-size: 64rpx; font-weight: 300; line-height: 48rpx; }
.nav-menu { display: flex; align-items: center; justify-content: space-around; width: 172rpx; height: 62rpx; padding: 0 20rpx; border: 1rpx solid #ddd; border-radius: 34rpx; background: #fff; box-sizing: border-box; }
.nav-menu view { width: 1rpx; height: 36rpx; background: #ddd; }
.loading { padding: calc(250rpx + env(safe-area-inset-top)) 40rpx; color: #79817f; text-align: center; }
.content { padding: calc(172rpx + env(safe-area-inset-top)) 30rpx calc(72rpx + env(safe-area-inset-bottom)); }
.result-title { display: flex; align-items: center; gap: 28rpx; margin: 14rpx 38rpx 0; }
.result-title>text { font-size: 38rpx; font-weight: 750; }
.result-icon { display: flex; align-items: center; justify-content: center; width: 62rpx; height: 62rpx; flex: 0 0 auto; border-radius: 50%; background: #eba13a; color: #fff; font-size: 43rpx; font-weight: 700; }
.result-icon.rejected { background: #df4439; }.result-icon.approved { background: #59aa73; }.result-icon.review { background: #eda849; font-size: 30rpx; }
.result-copy { display: block; margin: 18rpx 38rpx 70rpx; color: #555d5a; font-size: 24rpx; line-height: 40rpx; }
:deep(.product-card) { display: flex; align-items: center; min-height: 132rpx; box-sizing: border-box; padding: 20rpx 26rpx; border: 2rpx solid #e2f7f4; border-radius: 34rpx; background: #fff; box-shadow: 0 8rpx 24rpx rgba(54,157,145,.06); }
:deep(.product-card image) { width: 92rpx; height: 92rpx; flex: 0 0 auto; border-radius: 24rpx; }
:deep(.product-info) { display: flex; min-width: 0; margin-left: 28rpx; flex: 1; flex-direction: column; }
:deep(.product-info text:first-child) { overflow: hidden; font-size: 31rpx; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
:deep(.product-info text:last-child) { margin-top: 8rpx; color: #898e8c; font-size: 21rpx; }
:deep(.product-status) { min-width: 140rpx; padding: 16rpx 20rpx; border-radius: 32rpx; color: #df463d; background: #fae8e9; font-size: 25rpx; text-align: center; box-sizing: border-box; }
:deep(.product-status.approved) { color: #50b69f; background: #e3f3ef; }:deep(.product-status.review) { color: #e8912a; background: #faefdf; }
.reason-card,.choice-card,.sample-card,.address-card { margin-top: 30rpx; padding: 28rpx 26rpx; border: 2rpx solid #e5f8f6; border-radius: 34rpx; background: #fff; box-shadow: 0 8rpx 24rpx rgba(54,157,145,.05); }
.card-title { display: block; margin-bottom: 24rpx; font-size: 29rpx; font-weight: 700; }.rejected-text { color: #de4239; }
.reason { display: grid; grid-template-columns: 32rpx 1fr; gap: 8rpx; margin: 9rpx 0; color: #424846; font-size: 23rpx; line-height: 38rpx; }.reason>text:first-child { color: #e83c34; font-size: 30rpx; }
.section-title { display: block; margin: 34rpx 4rpx 20rpx; font-size: 32rpx; font-weight: 700; }
.choice { display: grid; grid-template-columns: 46rpx minmax(0,1fr) auto; align-items: center; gap: 12rpx; min-height: 112rpx; box-sizing: border-box; margin-bottom: 20rpx; padding: 18rpx 20rpx; border: 2rpx solid transparent; border-radius: 30rpx; background: #f5f5f5; }.choice:last-child { margin-bottom: 0; }.choice.active { border-color: #55d2bb; background: #e7f8f4; }
.radio { display: flex; align-items: center; justify-content: center; width: 34rpx; height: 34rpx; border: 3rpx solid #fff; border-radius: 50%; }.choice.active .radio { border-color: #55d2bb; }.choice.active .radio view { width: 19rpx; height: 19rpx; border-radius: 50%; background: #55d2bb; }
.choice>view:nth-child(2) { display: flex; min-width: 0; flex-direction: column; }.choice>view:nth-child(2) text:first-child { font-size: 28rpx; font-weight: 700; }.choice>view:nth-child(2) text:last-child { overflow: hidden; margin-top: 8rpx; color: #a0a4a2; font-size: 21rpx; text-overflow: ellipsis; white-space: nowrap; }.credit { padding: 10rpx 16rpx; border-radius: 24rpx; background: #f5ead8; color: #ee921f; font-size: 20rpx; }
.main-action { width: calc(100% - 100rpx); height: 94rpx; margin: 72rpx auto 0; border-radius: 26rpx; background: linear-gradient(100deg,#68ebc7,#49a49c); color: #fff; font-size: 34rpx; font-weight: 600; line-height: 94rpx; }.main-action::after { border: 0; }
.quantity-row { display: flex; align-items: center; }.quantity-row button { width: 54rpx; height: 54rpx; margin: 0; padding: 0; border-radius: 50%; background: #57d3ba; color: #fff; line-height: 54rpx; }.quantity-row button::after { border: 0; }.quantity-row>text { min-width: 66rpx; margin: 0 10rpx; padding: 10rpx 0; border: 2rpx solid #ddd; border-radius: 12rpx; font-size: 27rpx; text-align: center; }.quantity-row>view { display: flex; min-width: 0; margin-left: 20rpx; flex: 1; flex-direction: column; }.quantity-row>view text:first-child { font-size: 25rpx; font-weight: 650; }.quantity-row>view text:last-child { margin-top: 7rpx; color: #7c8280; font-size: 20rpx; }
.price-row { display: flex; align-items: center; justify-content: space-between; margin-top: 30rpx; padding-top: 28rpx; border-top: 2rpx dashed #ddd; color: #666; font-size: 22rpx; }.price-row text:last-child { color: #e58b25; font-size: 34rpx; font-weight: 650; }.quote-note { display: block; margin-top: 16rpx; color: #7c8582; font-size: 21rpx; }
.address-card label { display: block; margin: 18rpx 8rpx 10rpx; font-size: 26rpx; font-weight: 650; }.address-card label:first-child { margin-top: 0; }.address-card label text { color: #e7423a; }.address-card input,.address-card textarea { width: 100%; box-sizing: border-box; padding: 0 20rpx; border: 2rpx dashed #c9cecc; border-radius: 25rpx; background: #fafafa; font-size: 23rpx; }.address-card input { height: 70rpx; }.address-card textarea { height: 150rpx; padding-top: 18rpx; }
.state-center { display: flex; align-items: center; padding: 72rpx 30rpx 58rpx; flex-direction: column; text-align: center; }.state-center>text:nth-child(2) { margin-top: 24rpx; font-size: 38rpx; font-weight: 700; }.state-center>text:last-child { margin-top: 16rpx; color: #66706d; font-size: 23rpx; line-height: 40rpx; }
.footer-note { display: block; margin: 76rpx 50rpx 0; color: #666; font-size: 22rpx; line-height: 38rpx; text-align: center; }
@media (max-width:360px) { .nav-bar { grid-template-columns: 110rpx minmax(0,1fr) 165rpx; padding-right: 18rpx; padding-left: 18rpx; }.nav-menu { width: 152rpx; }.content { padding-right: 22rpx; padding-left: 22rpx; }.result-copy { margin-bottom: 48rpx; }.main-action { width: calc(100% - 40rpx); } }
</style>
