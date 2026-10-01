<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-back" aria-label="返回" @tap="goBack">‹</view>
      <text class="nav-title">渠道合作</text>
      <view class="nav-menu" aria-label="更多操作"><text>•••</text><view /><text>◎</text></view>
    </view>

    <view class="content">
      <view class="progress" aria-label="渠道合作流程">
        <view v-for="(step, index) in steps" :key="step" class="progress-item" @tap="selectStage(index)">
          <view class="progress-dot" :class="{ active: index <= currentStage, selected: index === selectedStage }">{{ index + 1 }}</view>
          <text :class="{ active: index <= currentStage, selected: index === selectedStage }">{{ step }}</text>
          <view v-if="index < steps.length - 1" class="progress-line" />
        </view>
      </view>

      <view v-if="viewStatus && !statusRecord" class="progress-empty">
        <text>暂无渠道合作作品</text>
        <text>提交作品包后，可在这里查看平台审核与后续进度。</text>
        <button class="submit" @tap="viewStatus = false; selectedStage = 0">上传作品</button>
      </view>
      <view v-else-if="viewStatus && statusRecord" class="status-view">
        <view class="status-banner" :class="`status-${displayStatusKind}`">
          <view class="status-mark">{{ displayStatusKind === 'rejected' ? '!' : displayStatusKind === 'approved' ? '✓' : '•' }}</view>
          <view><text class="status-title">{{ statusTitle }}</text><text class="status-copy">{{ statusCopy }}</text></view>
        </view>
        <view class="record-card">
          <view class="record-main"><view class="record-thumb">之</view><view><text class="record-title">{{ statusRecord.title || statusRecord.originalName || '我的作品' }}</text><text class="record-no">编号：{{ statusRecord.submissionNo || '待生成' }}</text></view></view>
          <text class="record-state" :class="`record-${displayStatusKind}`">{{ statusLabel }}</text>
        </view>

        <view v-if="selectedStage === 1 && statusKind === 'rejected'" class="reason-card">
          <text class="reason-title">审核未通过原因</text>
          <view v-for="(reason, index) in reviewReasons" :key="`${reason}-${index}`" class="reason-line"><text>×</text><text>{{ reason }}</text></view>
          <text class="repair-title">选择修改方式</text>
          <view class="repair-option selected" @tap="retryRejected"><view class="repair-radio" /><view><text>自行修改</text><text>修改作品包后重新上传</text></view></view>
          <view class="repair-option unavailable"><view class="repair-radio" /><view><text>平台协助修改</text><text>暂未开放，不会扣除积分</text></view><text class="repair-price">30积分/次</text></view>
          <button class="submit" @tap="retryRejected">重新提交审核</button>
        </view>
        <view v-else-if="selectedStage === 2 && museumReviewStatus === 'rejected'" class="reason-card museum-rejected-card">
          <text class="reason-title">馆方审核未通过原因</text>
          <view v-for="(reason, index) in museumReasons" :key="`museum-${reason}-${index}`" class="reason-line"><text>×</text><text>{{ reason }}</text></view>
          <text class="repair-title">选择下一步</text>
          <button class="submit" :loading="resubmittingMuseum" @tap="retryMuseumRejected">修改后再提交本馆</button>
          <button class="outline-action" @tap="changeMuseum">改报其他渠道</button>
          <button class="outline-action" @tap="transferToProductMaking">转入产品智造</button>
        </view>
        <view v-else class="stage-card">
          <text class="stage-title">{{ stagePanelTitle }}</text>
          <text class="stage-copy">{{ stagePanelCopy }}</text>
          <button v-if="selectedStage === 2 && canRequestChannel" class="submit" :loading="requestingChannel" @tap="requestChannelReview">申请平台提报</button>
          <button v-if="selectedStage === 2 && museumReviewStatus === 'approved'" class="submit" @tap="openSampleApplication">申请打样</button>
          <view v-if="selectedStage === 2 && statusRecord.channelRequestStatus === 'requested' && museumReviewStatus !== 'approved' && museumReviewStatus !== 'rejected'" class="requested-tip">已提交「{{ statusRecord.museumName }}」馆方审核，结果会同步到这里。</view>
        </view>

        <text class="section-title-small">审核列表</text>
        <view class="history-list">
          <view v-for="record in records" :key="record.id || record.submissionNo" class="history-item" @tap="selectRecord(record)">
            <view class="history-thumb">之</view><view class="history-copy"><text>{{ record.title || record.originalName || '我的作品' }}</text><text>编号：{{ record.submissionNo || '待生成' }}</text></view><text class="history-status">{{ recordStatusLabel(record) }}</text>
          </view>
        </view>
        <text class="footer">平台将在1-2个工作日内审核作品的完整性、版权信息与生产可行性，结果可在“消息通知”和“我的作品”里进行查询。</text>
      </view>

      <view v-else class="intro">
        <text class="intro-title">上传作品</text>
        <text class="intro-copy">提交已有作品，审核通过后即可打样、生产或进入渠道合作。</text>
      </view>

      <view v-if="!viewStatus" class="section-heading"><text><b>01</b> 上传作品包</text><text>必填</text></view>
      <view v-if="!viewStatus" class="upload-card">
        <view class="file-picker" :class="{ selected: filePath }" @tap="chooseZip">
          <view class="upload-icon">↥</view>
          <text class="file-title">{{ fileName || '选择作品包文件' }}</text>
          <text class="file-subtitle">{{ filePath ? `${formatSize(fileSize)} · 点击可重新选择` : 'ZIP格式 · 不超过100MB' }}</text>
        </view>
        <view class="package-checklist"><text>✓ 效果图(三视角)</text><text>✓ 尺寸规格</text><text>✓ 材质与工艺说明</text><text>✓ 版权材料</text></view>
        <text v-if="fileError" class="error">{{ fileError }}</text>
      </view>

      <view v-if="!viewStatus" class="section-heading"><text><b>02</b> 作品信息</text><text>名称必填</text></view>
      <view v-if="!viewStatus" class="form-card">
        <label class="field-label">作品名称<text>*</text></label>
        <input v-model.trim="title" class="input" maxlength="100" placeholder="请输入作品名称，如：青铜纹样冰箱贴" />
        <label class="field-label">作品介绍<text>*</text></label>
        <textarea v-model.trim="note" class="textarea" maxlength="1200" placeholder="创作理念、文化来源、设计思路……" />
        <label class="field-label">产品信息<text>*</text></label>
        <input v-model.trim="productInfo" class="input last" maxlength="300" placeholder="如：60x60x3mm·锌合金·珐琅工艺·首批500个等" />
      </view>

      <view v-if="!viewStatus" class="section-heading channel-heading"><text><b>03</b> 意向渠道<text class="heading-hint">（审核通过后将提报所选渠道）</text></text><text>单选</text></view>
      <view v-if="!viewStatus" class="channel-card">
        <view v-if="loadingMuseums" class="loading">正在加载渠道目录...</view>
        <view v-else-if="popularMuseums.length" class="channel-grid">
          <view v-for="item in popularMuseums" :key="item.channelCode || item.id" class="channel-option" :class="{ selected: sameMuseum(item, museum) }" @tap="selectMuseum(item)">
            <view class="channel-radio"><view /></view>
            <text class="channel-name">{{ item.name }}</text>
            <text class="channel-region">{{ channelRegion(item) }}</text>
            <text class="channel-type">{{ channelTypeLabel(item) }}</text>
          </view>
        </view>
        <text v-else class="empty">暂无可选渠道，请稍后重试</text>
        <text class="more-channels" @tap="openChannelPicker">按地区查找更多&gt;&gt;</text>
      </view>

      <view v-if="!viewStatus" class="check-row" @tap="copyrightConfirmed = !copyrightConfirmed"><text class="checkbox">{{ copyrightConfirmed ? '✓' : '' }}</text><text>我确认已合法取得作品及素材的使用授权，并同意配合平台与馆方审核。</text></view>
      <button v-if="!viewStatus" class="submit" :loading="loading" :disabled="loading" @tap="submit">重新提交审核</button>
      <text v-if="!viewStatus" class="footer">平台将在1-2个工作日内审核作品的完整性、版权信息与生产可行性，结果可在“消息通知”和“我的作品”里进行查询。</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { changeProfessionalSubmissionChannel, getMuseums, getMyProfessionalSubmissions, requestProfessionalChannelReview, resubmitProfessionalSubmission, resubmitProfessionalSubmissionForMuseum, transferProfessionalSubmissionToProductMaking, uploadProfessionalSubmission, type ProfessionalSubmission } from '../../api/creative'
import { getSession, requireSession } from '../../utils/session'

const steps = ['上传作品', '平台审核', '博物馆审核', '打样', '上架销售']
const pendingKey = 'pending_product_package'
const maxZipBytes = 100 * 1024 * 1024
const preferredCodes = ['museum-national', 'museum-palace', 'museum-shaanxi-history', 'catalog-west-lake']
const filePath = ref('')
const fileName = ref('')
const fileSize = ref(0)
const fileError = ref('')
const title = ref('')
const note = ref('')
const productInfo = ref('')
const museums = ref<any[]>([])
const museum = ref<any>(null)
const loadingMuseums = ref(true)
const copyrightConfirmed = ref(false)
const loading = ref(false)
const requestingChannel = ref(false)
const resubmittingMuseum = ref(false)
const records = ref<ProfessionalSubmission[]>([])
const statusRecord = ref<ProfessionalSubmission | null>(null)
const selectedStage = ref(0)
const viewStatus = ref(false)
const provinces = computed(() => [...new Set(museums.value.map(item => String(item.province || '').trim()).filter(Boolean))])
const popularMuseums = computed(() => {
  const byCode = new Map(museums.value.map(item => [item.channelCode, item]))
  const preferred = preferredCodes.map(code => byCode.get(code)).filter(Boolean)
  const used = new Set(preferred.map(item => item.channelCode))
  return [...preferred, ...museums.value.filter(item => !used.has(item.channelCode))].slice(0, 4)
})
const currentStage = computed(() => {
  const record = statusRecord.value
  if (!record) return 0
  if (record.status === 'processing' || record.samplePaymentStatus === 'paid') return 3
  if (record.museumReviewStatus === 'approved' || record.museumReviewStatus === 'rejected' || record.museumReviewStatus === 'review') return 2
  if (record.channelRequestStatus === 'requested') return 2
  return 1
})
const statusKind = computed(() => statusRecord.value?.status === 'rejected' ? 'rejected' : statusRecord.value?.status === 'approved' || statusRecord.value?.status === 'processing' ? 'approved' : 'review')
const museumReviewStatus = computed(() => String(statusRecord.value?.museumReviewStatus || 'not_started'))
const displayStatusKind = computed(() => selectedStage.value === 2 && museumReviewStatus.value === 'rejected' ? 'rejected' : selectedStage.value === 2 && museumReviewStatus.value === 'approved' ? 'approved' : selectedStage.value === 2 && museumReviewStatus.value === 'review' ? 'review' : statusKind.value)
const museumReasons = computed(() => String(statusRecord.value?.museumReviewComment || '请根据馆方审核意见完善作品后重新提交。').split(/\n|；|;/).map(item => item.replace(/^[-*×\s]+/, '').trim()).filter(Boolean))
const statusTitle = computed(() => selectedStage.value === 2 && museumReviewStatus.value === 'rejected' ? `${statusRecord.value?.museumName || '博物馆'}审核未通过` : selectedStage.value === 2 && museumReviewStatus.value === 'approved' ? '博物馆审核通过' : selectedStage.value === 2 && museumReviewStatus.value === 'review' ? `${statusRecord.value?.museumName || '博物馆'}审核中` : statusKind.value === 'rejected' ? '平台审核未通过' : statusKind.value === 'approved' ? '平台审核通过' : '平台审核中')
const statusCopy = computed(() => selectedStage.value === 2 && museumReviewStatus.value === 'rejected' ? '很抱歉，作品未通过馆方审核，请根据审核意见修改或改报其他渠道。' : selectedStage.value === 2 && museumReviewStatus.value === 'approved' ? '你的作品已通过馆方审核，现在可以申请打样。' : selectedStage.value === 2 && museumReviewStatus.value === 'review' ? '馆方正在评估作品主题与版权，请耐心等待审核结果。' : statusKind.value === 'rejected' ? '很抱歉，您的作品未通过审核，请根据审核意见修改后重新提交。' : statusKind.value === 'approved' ? '你的作品已通过平台审核，可以申请平台向意向渠道提报。' : '作品正在平台审核中，审核结果将通过消息通知，请耐心等待。')
const statusLabel = computed(() => selectedStage.value === 2 && museumReviewStatus.value === 'rejected' ? '馆方未通过' : selectedStage.value === 2 && museumReviewStatus.value === 'approved' ? '待打样' : selectedStage.value === 2 && museumReviewStatus.value === 'review' ? '馆方审核中' : statusKind.value === 'rejected' ? '未通过' : statusKind.value === 'approved' ? '平台通过' : '审核中')
const canRequestChannel = computed(() => !!statusRecord.value && ['approved', 'processing'].includes(String(statusRecord.value.status)) && statusRecord.value.channelRequestStatus !== 'requested')
const stagePanelTitle = computed(() => {
  if (selectedStage.value === 0) return '作品已上传'
  if (selectedStage.value === 1) return statusKind.value === 'rejected' ? '平台审核未通过' : statusKind.value === 'approved' ? '平台审核通过' : '平台审核中'
  if (selectedStage.value === 2) return museumReviewStatus.value === 'approved' ? '博物馆审核通过' : museumReviewStatus.value === 'rejected' ? '博物馆审核未通过' : museumReviewStatus.value === 'review' ? '博物馆审核中' : statusRecord.value?.channelRequestStatus === 'requested' ? '渠道提报待处理' : '提交渠道方审核'
  if (selectedStage.value === 3) return statusRecord.value?.status === 'processing' ? '打样进行中' : '等待打样'
  return '等待上架销售'
})
const stagePanelCopy = computed(() => {
  if (selectedStage.value === 0) return '作品包已上传，平台会先审核作品完整性、版权信息和生产可行性。'
  if (selectedStage.value === 1) return statusCopy.value
  if (selectedStage.value === 2) return museumReviewStatus.value === 'approved' ? '馆方已确认合作，下一步可提交打样申请。' : museumReviewStatus.value === 'rejected' ? '可修改作品后重新提交本馆，也可以改报其他渠道。' : museumReviewStatus.value === 'review' ? `${statusRecord.value?.museumName || '目标渠道'}正在审核作品主题与版权。` : statusRecord.value?.channelRequestStatus === 'requested' ? `已向平台申请提报「${statusRecord.value?.museumName || '目标渠道'}」，待馆方审核。` : '平台审核通过后，可向平台申请提报所选博物馆或景区。'
  if (selectedStage.value === 3) return statusRecord.value?.status === 'processing' ? '打样申请已进入生产流程，请留意样品进度；渠道审核状态仍需平台确认。' : '尚无打样进度，请等待平台后续通知。'
  return '目前尚无渠道上架结果，请等待平台后续通知。'
})
const reviewReasons = computed(() => {
  const raw = String(statusRecord.value?.reviewComment || '').trim()
  if (!raw) return ['请根据平台审核意见补充完整作品包后重新提交。']
  return raw.split(/\n|；|;/).map(item => item.replace(/^[-*×\s]+/, '').trim()).filter(Boolean)
})

function goBack() {
  if (getCurrentPages().length > 1) { uni.navigateBack(); return }
  uni.reLaunch({ url: '/pages/home/index' })
}

function selectStage(index: number) {
  selectedStage.value = index
  if (statusRecord.value) viewStatus.value = true
}

function selectRecord(record: ProfessionalSubmission) {
  statusRecord.value = record
  selectedStage.value = Math.min(currentStage.value, 4)
  viewStatus.value = true
}

function recordStatusLabel(record: ProfessionalSubmission) {
  if (record.status === 'rejected') return '未通过'
  if (record.museumReviewStatus === 'approved') return '待打样'
  if (record.museumReviewStatus === 'rejected') return '馆方未通过'
  if (record.museumReviewStatus === 'review' || record.channelRequestStatus === 'requested') return '馆方审核中'
  if (record.status === 'processing') return '打样中'
  if (record.status === 'approved') return '平台通过'
  return '审核中'
}

async function refreshRecords() {
  try {
    records.value = (await getMyProfessionalSubmissions()).filter(item => item.purpose === 'museum_sale')
    if (!statusRecord.value && records.value.length) statusRecord.value = records.value[0]
    else if (statusRecord.value?.id) statusRecord.value = records.value.find(item => item.id === statusRecord.value?.id) || statusRecord.value
  } catch { records.value = [] }
}

async function requestChannelReview() {
  if (!statusRecord.value?.id || requestingChannel.value) return
  requestingChannel.value = true
  try {
    await requestProfessionalChannelReview(statusRecord.value.id)
    await refreshRecords()
    selectedStage.value = 2
    uni.showToast({ title: '提报申请已提交', icon: 'success' })
  } catch (error: any) { uni.showToast({ title: error?.message || '提交失败，请稍后重试', icon: 'none' }) }
  finally { requestingChannel.value = false }
}

function chooseZipFile(): Promise<{ path: string; name: string } | null> {
  return new Promise(resolve => {
    const chooser = (uni as any).chooseMessageFile
    if (typeof chooser !== 'function') { uni.showToast({ title: '当前版本暂不支持选择ZIP文件', icon: 'none' }); resolve(null); return }
    chooser({ count: 1, type: 'file', extension: ['zip'], success: (result: any) => {
      const file = result?.tempFiles?.[0]
      const path = String(file?.path || '')
      const name = String(file?.name || path)
      if (!path || !/\.zip$/i.test(name)) { uni.showToast({ title: '请选择ZIP格式作品包', icon: 'none' }); resolve(null); return }
      if (Number(file?.size || 0) > maxZipBytes) { uni.showToast({ title: 'ZIP作品包不能超过100MB', icon: 'none' }); resolve(null); return }
      resolve({ path, name })
    }, fail: () => resolve(null) })
  })
}

async function retryMuseumRejected() {
  if (!statusRecord.value?.id || resubmittingMuseum.value) return
  const selected = await chooseZipFile()
  if (!selected) return
  resubmittingMuseum.value = true
  try {
    await resubmitProfessionalSubmissionForMuseum(statusRecord.value.id, selected.path)
    await refreshRecords()
    selectedStage.value = 2
    uni.showToast({ title: '已重新提交馆方审核', icon: 'success' })
  } catch (error: any) { uni.showToast({ title: error?.message || '重新提交失败', icon: 'none' }) }
  finally { resubmittingMuseum.value = false }
}

function changeMuseum() {
  if (!statusRecord.value?.id || !museums.value.length) return
  const candidates = museums.value.filter(item => String(item.name || '') !== String(statusRecord.value?.museumName || ''))
  if (!candidates.length) return uni.showToast({ title: '暂无其他可选渠道', icon: 'none' })
  uni.showActionSheet({ itemList: candidates.slice(0, 6).map(item => `${item.name} · ${channelRegion(item)}`), success: async result => {
    const chosen = candidates[Number(result.tapIndex)]
    if (!chosen || !statusRecord.value?.id) return
    try {
      await changeProfessionalSubmissionChannel(statusRecord.value.id, { museumId: chosen.id == null ? '' : String(chosen.id), museumName: String(chosen.name || '') })
      await refreshRecords()
      uni.showToast({ title: '已更换渠道，请重新提报', icon: 'success' })
    } catch (error: any) { uni.showToast({ title: error?.message || '更换渠道失败', icon: 'none' }) }
  } })
}

async function transferToProductMaking() {
  if (!statusRecord.value?.id) return
  try {
    await transferProfessionalSubmissionToProductMaking(statusRecord.value.id)
    uni.showToast({ title: '已转入产品智造', icon: 'success' })
    setTimeout(() => uni.redirectTo({ url: '/pages/product-making/index' }), 500)
  } catch (error: any) { uni.showToast({ title: error?.message || '转入失败', icon: 'none' }) }
}

function openSampleApplication() {
  if (!statusRecord.value?.id) return
  uni.navigateTo({ url: `/pages/product-making-detail/index?id=${encodeURIComponent(String(statusRecord.value.id))}` })
}

function retryRejected() {
  if (!statusRecord.value?.id) return
  const chooser = (uni as any).chooseMessageFile
  if (typeof chooser !== 'function') return uni.showToast({ title: '当前版本暂不支持选择ZIP文件', icon: 'none' })
  chooser({ count: 1, type: 'file', extension: ['zip'], success: async (result: any) => {
    const file = result?.tempFiles?.[0]
    const path = String(file?.path || '')
    const name = String(file?.name || path)
    if (!/\.zip$/i.test(name)) return uni.showToast({ title: '请选择ZIP格式作品包', icon: 'none' })
    if (Number(file?.size || 0) > maxZipBytes) return uni.showToast({ title: 'ZIP作品包不能超过100MB', icon: 'none' })
    try {
      await resubmitProfessionalSubmission(statusRecord.value!.id!, path)
      await refreshRecords()
      selectedStage.value = 1
      uni.showToast({ title: '已重新提交审核', icon: 'success' })
    } catch (error: any) { uni.showToast({ title: error?.message || '重新提交失败，请稍后重试', icon: 'none' }) }
  } })
}

function restorePendingPackage() {
  const raw = uni.getStorageSync(pendingKey)
  const currentUser = String(getSession()?.user?.username || '').trim()
  const selectedAt = Number(raw?.selectedAt || 0)
  const valid = raw && typeof raw === 'object'
    && String(raw.userName || '').trim() === currentUser
    && String(raw.path || '').trim()
    && /\.zip$/i.test(String(raw.name || ''))
    && Number(raw.size || 0) <= maxZipBytes
    && Date.now() - selectedAt < 30 * 60 * 1000
  if (!valid) {
    if (raw) uni.removeStorageSync(pendingKey)
    return
  }
  filePath.value = String(raw.path)
  fileName.value = String(raw.name)
  fileSize.value = Number(raw.size || 0)
  if (!title.value) title.value = fileName.value.replace(/\.zip$/i, '')
}

function rememberPendingPackage() {
  uni.setStorageSync(pendingKey, {
    path: filePath.value,
    name: fileName.value,
    size: fileSize.value,
    userName: String(getSession()?.user?.username || '').trim(),
    selectedAt: Date.now(),
  })
}

function chooseZip() {
  fileError.value = ''
  const chooser = (uni as any).chooseMessageFile
  if (typeof chooser !== 'function') {
    uni.showToast({ title: '当前版本暂不支持选择ZIP文件，请更新微信后重试', icon: 'none' })
    return
  }
  chooser({ count: 1, type: 'file', extension: ['zip'], success: (result: any) => {
    const file = result?.tempFiles?.[0]
    const path = String(file?.path || '').trim()
    const name = String(file?.name || path).trim()
    const size = Number(file?.size || 0)
    if (!path || !/\.zip$/i.test(name)) { fileError.value = '请选择 ZIP 格式的专业作品包'; return }
    if (size > maxZipBytes) { fileError.value = 'ZIP 作品包不能超过 100MB'; return }
    filePath.value = path; fileName.value = name; fileSize.value = size
    if (!title.value) title.value = name.replace(/\.zip$/i, '')
    rememberPendingPackage()
  }, fail: (error: any) => {
    if (!/cancel/i.test(String(error?.errMsg || ''))) fileError.value = '文件选择失败，请重新点击选择 ZIP 作品包'
  } })
}

function sameMuseum(left: any, right: any) {
  if (!left || !right) return false
  if (left.channelCode && right.channelCode) return left.channelCode === right.channelCode
  return left.id != null && right.id != null && String(left.id) === String(right.id)
}

function selectMuseum(item: any) { museum.value = item }
function channelTypeLabel(item: any) { return item?.channelType === 'scenic_spot' ? '景区' : '博物馆' }
function channelRegion(item: any) { return [item?.province, item?.city].filter(Boolean).join(' · ') || '全国渠道目录' }
function formatSize(size: number) { return size ? `${(size / 1024 / 1024).toFixed(2)} MB` : '文件已选择' }

function openChannelPicker() {
  if (!provinces.value.length) return
  uni.showActionSheet({ itemList: provinces.value, success: result => {
    const province = provinces.value[Number(result.tapIndex)]
    const candidates = museums.value.filter(item => item.province === province)
    if (candidates.length) {
      uni.showActionSheet({ itemList: candidates.map(item => `${item.name} · ${channelTypeLabel(item)}`), success: selected => selectMuseum(candidates[Number(selected.tapIndex)]) })
    }
  } })
}

async function submit() {
  if (loading.value) return
  if (!filePath.value) return uni.showToast({ title: '请先选择ZIP作品包', icon: 'none' })
  if (!title.value.trim()) return uni.showToast({ title: '请填写作品名称', icon: 'none' })
  if (!note.value.trim()) return uni.showToast({ title: '请填写作品介绍', icon: 'none' })
  if (!productInfo.value.trim()) return uni.showToast({ title: '请填写产品信息', icon: 'none' })
  if (!museum.value) return uni.showToast({ title: '请选择意向渠道', icon: 'none' })
  if (!copyrightConfirmed.value) return uni.showToast({ title: '请先确认作品及素材授权', icon: 'none' })
  loading.value = true
  try {
    const result = await uploadProfessionalSubmission(filePath.value, {
      title: title.value.trim(),
      note: `${note.value.trim()}\n\n产品信息：${productInfo.value.trim()}`,
      purpose: 'museum_sale',
      museumId: museum.value?.id == null ? '' : String(museum.value.id),
      museumName: String(museum.value?.name || ''),
    })
    uni.removeStorageSync(pendingKey)
    await refreshRecords()
    statusRecord.value = records.value.find(item => item.submissionNo === result?.submissionNo) || records.value[0] || null
    selectedStage.value = 1
    viewStatus.value = !!statusRecord.value
    if (!statusRecord.value) uni.showToast({ title: '提交成功', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '提交失败，请稍后重试', icon: 'none' })
  } finally { loading.value = false }
}

onMounted(async () => {
  if (!requireSession()) return
  restorePendingPackage()
  try {
    museums.value = await getMuseums()
    museum.value = popularMuseums.value[0] || null
  } catch { museums.value = [] } finally { loadingMuseums.value = false }
  await refreshRecords()
  if (!filePath.value && statusRecord.value) {
    viewStatus.value = true
    selectedStage.value = Math.min(currentStage.value, 4)
  }
})

onLoad(query => {
  if (String(query?.view || '') === 'progress') viewStatus.value = true
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;box-sizing:border-box;background:linear-gradient(180deg,#fff 0%,#f7fcfb 42%,#eefaf8 100%);color:#242a28}.nav-bar{position:fixed;z-index:10;top:0;right:0;left:0;display:grid;grid-template-columns:130rpx minmax(0,1fr) 172rpx;align-items:end;height:calc(112rpx + env(safe-area-inset-top));box-sizing:border-box;padding:calc(30rpx + env(safe-area-inset-top)) 24rpx 15rpx;background:rgba(255,255,255,.97)}.nav-title{overflow:hidden;color:#0c0f0e;font-size:36rpx;font-weight:700;line-height:54rpx;text-align:center;text-overflow:ellipsis;white-space:nowrap}.nav-back{justify-self:start;width:55rpx;height:52rpx;color:#161c19;font-size:56rpx;font-weight:300;line-height:44rpx}.nav-menu{display:flex;align-items:center;justify-content:space-around;justify-self:end;width:154rpx;height:56rpx;box-sizing:border-box;padding:0 16rpx;border:1rpx solid #e2e6e4;border-radius:31rpx;background:#fff;font-size:24rpx}.nav-menu view{width:1rpx;height:32rpx;background:#d8ddda}.nav-menu text:last-child{font-size:34rpx}.content{box-sizing:border-box;padding:calc(128rpx + env(safe-area-inset-top)) 28rpx calc(50rpx + env(safe-area-inset-bottom))}.progress{display:flex;align-items:flex-start;margin:8rpx 0 46rpx}.progress-item{display:flex;min-width:0;align-items:center;flex:1;flex-direction:column;position:relative}.progress-dot{display:grid;place-items:center;width:47rpx;height:47rpx;border-radius:50%;background:#f1f2f2;color:#959c99;font-size:20rpx}.progress-dot.active{background:#57cfb2;color:#fff}.progress-item>text{margin-top:8rpx;color:#5c6460;font-size:17rpx;white-space:nowrap}.progress-item>text.active{color:#4ec5ab}.progress-line{position:absolute;top:23rpx;left:calc(50% + 25rpx);width:calc(100% - 50rpx);height:2rpx;background:#e1e5e3}.intro{display:flex;flex-direction:column}.intro-title{color:#111513;font-size:36rpx;font-weight:700;line-height:54rpx}.intro-copy{margin-top:3rpx;color:#616966;font-size:20rpx;line-height:35rpx}.section-heading{display:flex;align-items:center;justify-content:space-between;margin:13rpx 0 16rpx;color:#171c1a}.section-heading>text:first-child{font-size:27rpx;font-weight:700}.section-heading b{margin-right:12rpx;font-size:28rpx}.section-heading>text:last-child{color:#7e8582;font-size:19rpx}.heading-hint{color:#7a8581;font-size:17rpx;font-weight:400}.upload-card,.form-card,.channel-card{padding:18rpx 20rpx;border:1rpx solid #d9f3ed;border-radius:24rpx;background:#fff;box-shadow:0 0 12rpx rgba(49,205,185,.10)}.file-picker{display:flex;align-items:center;justify-content:center;min-height:180rpx;box-sizing:border-box;flex-direction:column;padding:22rpx;border:1rpx dashed #adb6b2;border-radius:22rpx;background:#f7f8f8}.file-picker.selected{border-color:#58cbb4;background:#f0fbf8}.upload-icon{display:grid;place-items:center;width:50rpx;height:50rpx;border:3rpx solid #5fbaa9;border-radius:9rpx;color:#55b7a5;font-size:40rpx;font-weight:700;line-height:1}.file-title{overflow:hidden;max-width:100%;margin-top:14rpx;color:#303633;font-size:24rpx;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.file-subtitle{margin-top:3rpx;color:#737b78;font-size:18rpx}.package-checklist{display:flex;flex-wrap:wrap;gap:8rpx 22rpx;padding:18rpx 18rpx 0;color:#5e6763;font-size:17rpx;line-height:31rpx}.error{display:block;margin:8rpx 8rpx 0;color:#b74d3f;font-size:17rpx}.field-label{display:block;margin:1rpx 10rpx 9rpx;color:#363c39;font-size:23rpx;font-weight:600}.field-label text{color:#ed4740}.input,.textarea{width:100%;box-sizing:border-box;border:1rpx dashed #bec6c2;border-radius:22rpx;background:#f7f8f8;color:#313734;font-size:18rpx}.input{height:51rpx;margin-bottom:15rpx;padding:0 23rpx}.input.last{margin-bottom:0}.textarea{height:108rpx;margin-bottom:15rpx;padding:14rpx 23rpx;line-height:1.5}.channel-heading{margin-top:15rpx}.channel-card{padding:18rpx 16rpx 15rpx}.channel-grid{display:grid;grid-template-columns:1fr 1fr;gap:12rpx}.channel-option{position:relative;min-height:99rpx;box-sizing:border-box;padding:18rpx 12rpx 10rpx 36rpx;border:1rpx solid transparent;border-radius:17rpx;background:#f5f5f5}.channel-option.selected{border-color:#61d7bd;background:#e9faf6}.channel-radio{position:absolute;top:18rpx;left:13rpx;display:grid;place-items:center;width:19rpx;height:19rpx;border:2rpx solid #fff;border-radius:50%;background:#fff;box-shadow:0 0 0 1rpx #cad1ce}.channel-option.selected .channel-radio{background:#5fd3b9;box-shadow:0 0 0 1rpx #5fd3b9}.channel-radio view{width:7rpx;height:7rpx;border-radius:50%;background:#fff}.channel-name{display:block;overflow:hidden;color:#3e4542;font-size:20rpx;font-weight:600;text-overflow:ellipsis;white-space:nowrap}.channel-region{display:block;margin-top:5rpx;color:#7e8984;font-size:16rpx}.channel-type{display:inline-block;margin-top:7rpx;padding:3rpx 9rpx;border-radius:14rpx;background:#5bc6ae;color:#fff;font-size:14rpx}.channel-option:nth-child(2n) .channel-type{background:#ef952e}.loading,.empty{display:block;padding:40rpx 0;color:#7c8681;text-align:center;font-size:19rpx}.more-channels{display:block;margin-top:13rpx;color:#4fc5ac;text-align:center;font-size:17rpx}.check-row{display:flex;align-items:flex-start;gap:10rpx;margin:18rpx 14rpx 0;color:#4d5652;font-size:17rpx;line-height:28rpx}.checkbox{display:grid;place-items:center;flex:none;width:25rpx;height:25rpx;box-sizing:border-box;border:1rpx solid #909995;border-radius:6rpx;background:#fff;color:#54c9ad;font-size:19rpx;line-height:1}.submit{width:84%;height:70rpx;margin:23rpx auto 0;border-radius:36rpx;background:linear-gradient(110deg,#64eec7,#48a69e);color:#fff;font-size:27rpx;line-height:70rpx}.submit::after{border:0}.footer{display:block;margin:48rpx 18rpx 0;color:#606966;font-size:17rpx;line-height:30rpx;text-align:center}@media (max-width:360px){.nav-bar{grid-template-columns:105rpx minmax(0,1fr) 150rpx;padding-right:16rpx;padding-left:16rpx}.nav-menu{width:140rpx}.content{padding-right:20rpx;padding-left:20rpx}.progress-item>text{font-size:15rpx}.heading-hint{font-size:15rpx}.submit{width:86%}}
.progress-item{min-height:82rpx}.progress-dot.selected{box-shadow:0 0 0 4rpx rgba(87,207,178,.22)}.progress-item>text.selected{font-weight:700}.status-banner{display:flex;align-items:flex-start;gap:22rpx;margin:44rpx 8rpx 0}.status-mark{display:grid;place-items:center;flex:none;width:54rpx;height:54rpx;border-radius:50%;background:#5acaaa;color:#fff;font-size:36rpx;font-weight:700}.status-rejected .status-mark{background:#eb5148}.status-review .status-mark{background:#ebaa4a}.status-banner>view:last-child{display:flex;min-width:0;flex-direction:column}.status-title{color:#141917;font-size:38rpx;font-weight:700;line-height:58rpx}.status-copy{margin-top:8rpx;color:#5e6863;font-size:22rpx;line-height:35rpx}.record-card,.stage-card,.reason-card{box-sizing:border-box;margin-top:48rpx;padding:24rpx;border:1rpx solid #e0f3ee;border-radius:24rpx;background:#fff;box-shadow:0 2rpx 12rpx rgba(33,173,153,.08)}.record-card{display:flex;align-items:center;justify-content:space-between;gap:14rpx}.record-main{display:flex;min-width:0;align-items:center;gap:16rpx}.record-thumb,.history-thumb{display:grid;place-items:center;flex:none;width:82rpx;height:82rpx;border-radius:15rpx;background:linear-gradient(135deg,#70d9ba,#439c96);color:#fff;font-size:36rpx;font-weight:700}.record-main>view:last-child{display:flex;min-width:0;flex-direction:column;gap:8rpx}.record-title{overflow:hidden;font-size:27rpx;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.record-no{overflow:hidden;color:#8a9590;font-size:19rpx;text-overflow:ellipsis;white-space:nowrap}.record-state{flex:none;padding:10rpx 16rpx;border-radius:25rpx;background:#eaf8f3;color:#379e83;font-size:20rpx}.record-rejected{background:#faecea;color:#db493c}.record-review{background:#fbf0df;color:#bb852e}.reason-title,.stage-title,.section-title-small{display:block;color:#222a25;font-size:29rpx;font-weight:700}.reason-title{color:#dc493f}.reason-line{display:flex;align-items:flex-start;gap:13rpx;margin-top:17rpx;color:#4a534e;font-size:22rpx;line-height:35rpx}.reason-line text:first-child{flex:none;color:#e24a41;font-size:30rpx;line-height:32rpx}.reason-card .submit{margin-top:30rpx}.stage-copy{display:block;margin-top:16rpx;color:#62706a;font-size:22rpx;line-height:36rpx}.requested-tip{margin-top:20rpx;color:#3b9d86;font-size:20rpx}.section-title-small{margin:54rpx 4rpx 18rpx}.history-list{display:flex;flex-direction:column;gap:14rpx}.history-item{display:flex;align-items:center;gap:15rpx;min-height:112rpx;box-sizing:border-box;padding:15rpx;border:1rpx solid #e0f3ee;border-radius:20rpx;background:#fff}.history-thumb{width:74rpx;height:74rpx;font-size:29rpx}.history-copy{display:flex;min-width:0;flex:1;flex-direction:column;gap:5rpx}.history-copy text:first-child{overflow:hidden;font-size:23rpx;font-weight:600;text-overflow:ellipsis;white-space:nowrap}.history-copy text:last-child{overflow:hidden;color:#8a948f;font-size:17rpx;text-overflow:ellipsis;white-space:nowrap}.history-status{flex:none;color:#4aab95;font-size:19rpx}
.outline-action{width:84%;height:70rpx;margin:18rpx auto 0;border:2rpx solid #50b9a6;border-radius:36rpx;background:#fff;color:#419f8f;font-size:25rpx;line-height:66rpx}.outline-action::after{border:0}
.repair-title{display:block;margin:30rpx 0 18rpx;font-size:27rpx;font-weight:700}.repair-option{display:flex;align-items:center;gap:14rpx;min-height:86rpx;box-sizing:border-box;margin-top:13rpx;padding:12rpx 18rpx;border:1rpx solid transparent;border-radius:16rpx;background:#f5f6f5}.repair-option.selected{border-color:#63d2b8;background:#eaf9f5}.repair-option.unavailable{opacity:.6}.repair-radio{flex:none;width:22rpx;height:22rpx;border:2rpx solid #65cbb4;border-radius:50%;background:#fff}.repair-option.selected .repair-radio{background:#65cbb4;box-shadow:inset 0 0 0 5rpx #fff}.repair-option>view:nth-child(2){display:flex;min-width:0;flex:1;flex-direction:column;gap:5rpx}.repair-option>view:nth-child(2) text:first-child{font-size:23rpx;font-weight:600}.repair-option>view:nth-child(2) text:last-child{color:#8a9590;font-size:18rpx}.repair-price{flex:none;padding:7rpx 12rpx;border-radius:18rpx;background:#f8e9d6;color:#c98125;font-size:17rpx}
</style>
