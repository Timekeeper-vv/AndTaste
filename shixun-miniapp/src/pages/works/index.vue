<template>
  <view class="page">
    <view class="intro">
      <view>
        <text class="title">我的作品</text>
        <text class="sub">生成进度、审核结果和打样/生产申请都会在这里同步</text>
      </view>
      <button class="refresh" size="mini" :loading="loading" @tap="refresh(true)">刷新</button>
    </view>
    <AiGeneratedNotice class="ai-disclosure" compact description="带有“AI生成”标识的图片、四视图和 3D 原型由人工智能生成。展示、商业使用、打样和生产前请完成人工复核与权利核验。" />

    <view v-if="loading && !assets.length && !jobs.length" class="empty">正在同步作品状态…</view>

    <view v-else>
      <view v-if="activeJobs.length" class="section">
        <view class="section-head"><text>生成任务</text><text>{{ activeJobs.length }} 条</text></view>
        <view v-for="job in activeJobs" :key="`job-${job.id}`" class="job-card" :class="job.status">
          <view class="job-icon">{{ jobIcon(job.jobType) }}</view>
          <view class="job-body">
            <view class="row"><text class="name">{{ jobTitle(job) }}</text><text class="status" :class="job.status">{{ statusText(job.status) }}</text></view>
            <text class="meta">{{ jobNo(job) }} · {{ jobTypeText(job.jobType) }}</text>
            <view v-if="isGenerating(job.status)" class="progress-line"><view class="progress-value" :style="{ width: `${jobProgress(job)}%` }" /></view>
            <text v-if="isGenerating(job.status)" class="progress-text">生成进度 {{ jobProgress(job) }}%</text>
            <text v-if="job.status === 'failed'" class="failure">失败原因：{{ job.errorMessage || '生成服务未返回具体原因，请稍后重试。' }}</text>
          </view>
        </view>
      </view>

      <view v-if="!assets.length && !activeJobs.length" class="empty">
        <text>还没有作品，去开始第一件创作吧。</text>
        <button class="create-first" @tap="goCreate">开始创作</button>
      </view>

      <view v-else-if="assets.length" class="section">
        <view class="section-head"><text>作品库</text><text>{{ assets.length }} 件</text></view>
        <view v-for="item in assets" :key="item.id" class="asset">
          <view class="asset-media">
            <image v-if="previewSrc(item)" :src="previewSrc(item)" mode="aspectFill" class="cover" />
            <view v-else class="model">{{ item.assetType === 'model' ? '3D' : 'AI' }}</view>
            <text v-if="isAiGenerated(item)" class="ai-output-badge">AI生成</text>
          </view>
          <view class="body">
            <view class="row"><text class="name">{{ item.title || '未命名作品' }}</text><text class="status" :class="assetDisplayStatus(item)">{{ statusText(assetDisplayStatus(item)) }}</text></view>
            <text class="meta">{{ item.assetType === 'model' ? '3D 模型' : 'AI 图片' }} · {{ item.format?.toUpperCase() || '文件' }}</text>
            <view v-if="materialFor(item)" class="material-summary">
              <text>本次工艺</text><text>{{ materialFor(item)?.name }}</text><text>{{ materialFor(item)?.hint }}</text>
            </view>
            <text v-if="generationText(item)" class="generation">{{ generationText(item) }}</text>
            <view v-if="isGenerating(assetDisplayStatus(item))" class="progress-line"><view class="progress-value" :style="{ width: `${assetProgress(item)}%` }" /></view>
            <text v-if="assetFailure(item)" class="failure">失败原因：{{ assetFailure(item) }}</text>
            <text v-if="source(item)" class="source">审批出处：{{ source(item) }}</text>
            <text v-if="requestFor(item)" class="request-state">{{ requestTypeText(requestFor(item)?.requestType) }}：{{ statusText(requestFor(item)?.status) }}{{ requestFor(item)?.reviewComment ? ` · ${requestFor(item).reviewComment}` : '' }}</text>
            <view class="actions">
              <button v-if="item.assetType === 'model' && !isGenerating(assetDisplayStatus(item))" size="mini" @tap="preview(item)">查看 3D</button>
              <button v-if="item.assetType === 'model' && !isGenerating(assetDisplayStatus(item))" size="mini" class="material" @tap="openMaterialLab(item)">换材质（PPC / 搪胶 / 毛绒）</button>
              <button v-if="item.assetType === 'model' && !isGenerating(assetDisplayStatus(item))" size="mini" class="export" :loading="downloadingModelId === String(item.id)" @tap="chooseModelExport(item)">导出模型</button>
              <button v-if="canRunDesignReview(item)" size="mini" class="design-review" @tap="openDesignReview(item)">AI 深度评审</button>
              <button v-if="canSubmitReview(item)" size="mini" :loading="submittingId === item.id" @tap="submitReview(item)">提交审核</button>
              <button v-if="canApplyProduction(item)" size="mini" class="production" @tap="applyProduction(item)">打样 / 生产</button>
              <button size="mini" @tap="copy(item)">复制编号</button>
            </view>
          </view>
        </view>
      </view>

      <view v-if="productionRequests.length" class="section request-section">
        <view class="section-head"><text>我的打样 / 生产申请</text><text>{{ productionRequests.length }} 条</text></view>
        <view v-for="request in productionRequests" :key="request.id" class="request-card">
          <view class="row"><text class="request-title">{{ request.title || request.assetTitle }}</text><text class="status" :class="request.status">{{ statusText(request.status) }}</text></view>
          <text class="meta">{{ request.requestNo }} · {{ requestTypeText(request.requestType) }} · {{ request.quantity }} 件</text>
          <text v-if="request.reviewComment" class="failure">审核说明：{{ request.reviewComment }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import AiGeneratedNotice from '../../components/AiGeneratedNotice.vue'
import { getAssetPreviewAccess, getAssets, getJobs, getProductionRequests, submitAssetReview } from '../../api/creative'
import { apiUrl } from '../../api/client'
import { confirmCreativePolicy } from '../../utils/compliance'
import { getSession, requireSession } from '../../utils/session'
import { statusText } from '../../utils/format'

const assets = ref<any[]>([])
const jobs = ref<any[]>([])
const productionRequests = ref<any[]>([])
const securedPreviews = ref<Record<string, string>>({})
const loading = ref(true)
const submittingId = ref<number | null>(null)
const downloadingModelId = ref('')
const DESKTOP_MODEL_URL = 'https://www.zhijiansk.com/'
const threeDimensionalPolicyConfirmed = ref(false)

const assetJobMap = computed(() => {
  const result: Record<string, any> = {}
  jobs.value.forEach((job) => {
    if (job.outputAssetId !== undefined && job.outputAssetId !== null) result[String(job.outputAssetId)] = job
  })
  return result
})

const activeJobs = computed(() => {
  const knownAssetIds = new Set(assets.value.map((asset) => String(asset.id)))
  return jobs.value.filter((job) => {
    const outputAssetId = job.outputAssetId
    return !outputAssetId || !knownAssetIds.has(String(outputAssetId)) || job.status === 'failed'
  })
})

const productionByAsset = computed(() => {
  const result: Record<string, any> = {}
  productionRequests.value.forEach((request) => {
    if (!result[String(request.assetId)]) result[String(request.assetId)] = request
  })
  return result
})

const jobFor = (asset: any) => assetJobMap.value[String(asset.id)]
const isAiGenerated = (asset: any) => Boolean(jobFor(asset))
const requestFor = (asset: any) => productionByAsset.value[String(asset.id)]
const previewSrc = (asset: any) => {
  const secured = securedPreviews.value[String(asset.id)]
  if (secured) return secured
  return /^https:\/\//.test(String(asset.previewUrl || '')) ? asset.previewUrl : ''
}
const isGenerating = (status?: string) => ['queued', 'pending', 'running', 'processing'].includes(String(status || ''))
const jobProgress = (job: any) => Math.max(0, Math.min(100, Number(job?.progress) || 0))
const assetProgress = (asset: any) => jobProgress(jobFor(asset))
const assetDisplayStatus = (asset: any) => {
  const job = jobFor(asset)
  if (job && (isGenerating(job.status) || job.status === 'failed')) return job.status
  return String(asset.status || 'draft')
}
const assetFailure = (asset: any) => {
  const job = jobFor(asset)
  return job?.status === 'failed' ? job.errorMessage || '生成服务未返回具体原因，请稍后重试。' : ''
}
const generationText = (asset: any) => {
  const job = jobFor(asset)
  if (!job) return ''
  if (isGenerating(job.status)) return `生成任务 ${jobNo(job)} 正在处理`
  if (job.status === 'succeeded') return `生成任务 ${jobNo(job)} 已完成`
  return ''
}
const source = (asset: any) => {
  const tags = String(asset.tags || '')
  const match = tags.match(/审批出处=([^,，;；]+)/)
  return match?.[1] || ''
}
const jobNo = (job: any) => job?.jobNo || `任务 #${job?.id || '-'}`
const jobTypeText = (type?: string) => ({ text_to_image: '文生图', image_to_3d: '图生 3D', text_to_3d: '文生 3D' }[String(type || '')] || 'AI 创作')
const jobIcon = (type?: string) => String(type || '').includes('3d') ? '3D' : 'AI'
const jobTitle = (job: any) => job?.title || job?.productName || `${jobTypeText(job?.jobType)}任务`
const requestTypeText = (type?: string) => type === 'bulk' ? '批量生产申请' : '打样申请'
const canSubmitReview = (asset: any) => {
  if (!['image', 'model'].includes(asset.assetType) || isGenerating(assetDisplayStatus(asset))) return false
  return !['review', 'approved'].includes(String(asset.status || 'draft'))
}
const canApplyProduction = (asset: any) => asset.assetType === 'model' && asset.status === 'approved' && !requestFor(asset)
const canRunDesignReview = (asset: any) => ['image', 'model'].includes(String(asset?.assetType || '')) && !isGenerating(assetDisplayStatus(asset))

const materialDefinitions = [
  { tokens: ['PPC', 'PPC 高精硬塑'], name: 'PPC 高精硬塑', hint: '精密注塑 · 细节稳定' },
  { tokens: ['ABS', 'ABS 工程硬塑'], name: 'ABS 工程硬塑', hint: '工程硬塑 · 稳定量产' },
  { tokens: ['搪胶', '糖胶'], name: '搪胶（糖胶）', hint: '软触潮玩 · 圆润中空' },
  { tokens: ['软胶', 'soft vinyl'], name: '软胶', hint: '柔韧包胶 · 低反射' },
  { tokens: ['超柔绒'], name: '超柔绒', hint: '亲肤细绒 · 柔光触感' },
  { tokens: ['短毛绒'], name: '短毛绒', hint: '短密绒面 · 轮廓清晰' },
  { tokens: ['全毛绒', '毛绒'], name: '全毛绒', hint: '填充玩偶 · 刺绣细节' },
  { tokens: ['PVC'], name: 'PVC 潮玩', hint: '量产塑胶 · 易还原' },
  { tokens: ['树脂'], name: '树脂潮玩', hint: '细腻半哑 · 收藏感' },
  { tokens: ['陶瓷'], name: '陶瓷釉面', hint: '温润釉色 · 器物感' },
  { tokens: ['金属'], name: '金属', hint: '五金/徽章 · 细节浮雕' },
  { tokens: ['亚克力'], name: '透明亚克力', hint: '通透挂件 · 边缘高光' },
  { tokens: ['纸质'], name: '纸质礼盒', hint: '礼赠包装 · 低反射纸感' },
  { tokens: ['木质'], name: '木质温润', hint: '自然木作 · 细腻纹理' },
]

function materialFor(asset: any) {
  const text = [asset?.tags, asset?.prompt, asset?.title, asset?.metadataJson].filter(Boolean).join(' ').toLowerCase()
  return materialDefinitions.find(item => item.tokens.some(token => text.includes(token.toLowerCase())))
}

function absoluteMediaUrl(value: string | undefined, assetId: string, accessToken?: string) {
  if (!value && !accessToken) return ''
  if (value && /^https:\/\//.test(value)) {
    if (!accessToken || value.includes('access_token=')) return value
    return `${value}${value.includes('?') ? '&' : '?'}access_token=${encodeURIComponent(accessToken)}`
  }
  if (value?.startsWith('/')) return apiUrl(value)
  return accessToken ? apiUrl(`/api/creative/ai/assets/${encodeURIComponent(assetId)}/content?access_token=${encodeURIComponent(accessToken)}`) : ''
}

async function hydratePreviews(rows: any[]) {
  const candidates = rows.filter((asset) => ['image', 'model'].includes(asset.assetType) && asset.id).slice(0, 12)
  const pairs = await Promise.all(candidates.map(async (asset) => {
    try {
      const access = await getAssetPreviewAccess(asset.id)
      // 模型封面使用受控的 preview-content，模型本体在点“查看3D”时单独获取。
      const raw = asset.assetType === 'model' ? access?.previewUrl : (access?.previewUrl || access?.url)
      const url = absoluteMediaUrl(raw, String(asset.id), access?.accessToken)
      return url ? [String(asset.id), url] as const : null
    } catch {
      return null
    }
  }))
  const next: Record<string, string> = {}
  pairs.forEach((pair) => { if (pair) next[pair[0]] = pair[1] })
  securedPreviews.value = next
}

async function refresh(notify = false) {
  loading.value = true
  try {
    const [assetRows, jobRows, requestRows] = await Promise.all([getAssets(), getJobs(), getProductionRequests()])
    assets.value = Array.isArray(assetRows) ? assetRows : []
    jobs.value = Array.isArray(jobRows) ? jobRows : []
    productionRequests.value = Array.isArray(requestRows) ? requestRows : []
    // 静态 /generated 与 /uploads 在生产环境受保护；只为当前列表前 12 个作品签发短期封面地址。
    void hydratePreviews(assets.value)
    if (notify) uni.showToast({ title: '状态已更新', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error.message || '加载作品失败', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

async function preview(asset: any) {
  if (!threeDimensionalPolicyConfirmed.value && !(await confirmCreativePolicy('three-dimensional'))) return
  threeDimensionalPolicyConfirmed.value = true
  showDesktopModelNotice('preview')
}

async function openMaterialLab(_asset?: any) {
  if (!threeDimensionalPolicyConfirmed.value && !(await confirmCreativePolicy('three-dimensional'))) return
  threeDimensionalPolicyConfirmed.value = true
  showDesktopModelNotice('material')
}

function showDesktopModelNotice(action: 'preview' | 'material') {
  const feature = action === 'preview' ? '3D 模型预览' : '模型材质编辑'
  uni.showModal({
    title: '请使用电脑端',
    content: `小程序端暂不支持${feature}，请在电脑浏览器打开：${DESKTOP_MODEL_URL}`,
    cancelText: '知道了',
    confirmText: '复制网址',
    success: (result) => {
      if (!result.confirm) return
      uni.setClipboardData({
        data: DESKTOP_MODEL_URL,
        success: () => uni.showToast({ title: '电脑端网址已复制', icon: 'success' }),
      })
    },
  })
}

type ModelExportFormat = 'GLB' | 'OBJ' | 'STL'

const modelExportOptions: Array<{ format: ModelExportFormat; label: string }> = [
  { format: 'GLB', label: 'GLB · 通用 3D 模型' },
  { format: 'OBJ', label: 'OBJ · ZIP 压缩包' },
  { format: 'STL', label: 'STL · 3D 打印模型' },
]

function chooseModelExport(asset: any) {
  const assetId = String(asset?.id || '')
  if (!/^\d+$/.test(assetId)) {
    uni.showToast({ title: '作品编号无效，无法导出模型', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: modelExportOptions.map(item => item.label),
    success: (result) => {
      const selected = modelExportOptions[result.tapIndex]
      if (!selected) return
      if (selected.format === 'OBJ') {
        uni.showModal({
          title: '导出 OBJ（ZIP）',
          content: 'OBJ 会以 ZIP 压缩包下载。服务端可能需要转换，请耐心等待。',
          confirmText: '开始导出',
          success: (confirmation) => { if (confirmation.confirm) void downloadModel(asset, selected.format) },
        })
        return
      }
      void downloadModel(asset, selected.format)
    },
  })
}

async function downloadModel(asset: any, format: ModelExportFormat) {
  const assetId = String(asset?.id || '')
  const session = getSession()
  if (!session?.token) {
    requireSession()
    return
  }
  if (downloadingModelId.value) return
  downloadingModelId.value = assetId
  const outputName = format === 'OBJ' ? 'OBJ（ZIP）' : format
  uni.showLoading({ title: format === 'OBJ' ? '正在转换 OBJ（ZIP）…' : `正在导出 ${outputName}…`, mask: true })
  try {
    const result = await uni.downloadFile({
      url: apiUrl(`/api/creative/ai/assets/${encodeURIComponent(assetId)}/download-model?format=${format}`),
      header: { Authorization: `Bearer ${session.token}` },
    })
    if (result.statusCode < 200 || result.statusCode >= 300 || !result.tempFilePath) {
      throw new Error(`导出失败（${result.statusCode || '网络异常'}）`)
    }
    const saved = await uni.saveFile({ tempFilePath: result.tempFilePath })
    try {
      await uni.openDocument({ filePath: saved.savedFilePath, showMenu: true })
    } catch {
      uni.showModal({
        title: '模型已保存',
        content: `${outputName} 已保存到微信文件。若当前设备不能直接打开，请在“微信文件”中发送到电脑或专业建模软件。`,
        showCancel: false,
      })
    }
  } catch (error: any) {
    uni.showToast({ title: error?.message || `${outputName} 导出失败，请稍后重试`, icon: 'none' })
  } finally {
    uni.hideLoading()
    downloadingModelId.value = ''
  }
}

function copy(asset: any) {
  uni.setClipboardData({ data: String(asset.assetNo || asset.id) })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/create/index' })
}

function applyProduction(asset: any) {
  uni.navigateTo({ url: `/pages/production/index?assetId=${encodeURIComponent(String(asset.id))}&title=${encodeURIComponent(asset.title || '3D模型')}` })
}

function openDesignReview(asset: any) {
  uni.navigateTo({ url: `/pages/review/index?assetId=${encodeURIComponent(String(asset.id))}&title=${encodeURIComponent(asset.title || '作品')}` })
}

function submitReview(asset: any) {
  const context = uni.getStorageSync('creation_context') || {}
  const purpose = context.purpose === 'museum_sale' ? 'museum_sale' : 'personal'
  const museumId = context.museum?.id
  if (purpose === 'museum_sale' && !museumId) {
    uni.showToast({ title: '请先选择服务博物馆后再提交审核', icon: 'none' })
    return
  }
  const destination = purpose === 'museum_sale' ? `博物馆售卖 · ${context.museum?.name || ''}` : '个人创作'
  uni.showModal({
    title: '提交作品审核',
    content: `将按「${destination}」提交。审核通过后，3D 作品可申请打样或生产。`,
    success: async (result) => {
      if (!result.confirm) return
      submittingId.value = asset.id
      try {
        const response = await submitAssetReview(asset.id, { purpose, museumId })
        uni.showToast({ title: response?.message || '已提交审核', icon: 'success' })
        await refresh(false)
      } catch (error: any) {
        uni.showToast({ title: error.message || '提交审核失败', icon: 'none' })
      } finally {
        submittingId.value = null
      }
    },
  })
}

onShow(() => {
  if (requireSession()) void refresh(false)
})

onPullDownRefresh(() => {
  if (requireSession()) refresh(false)
  else uni.stopPullDownRefresh()
})
</script>

<style scoped lang="scss">
.ai-disclosure{margin:-18rpx 0 10rpx}.asset-media{position:relative;flex:0 0 180rpx;width:180rpx;height:180rpx}.asset-media .cover,.asset-media .model{display:block;width:180rpx;height:180rpx}.asset-media .model{display:flex}.ai-output-badge{position:absolute;left:10rpx;top:10rpx;padding:5rpx 8rpx;border-radius:6rpx;background:rgba(107,67,49,.88);color:#fff;font-size:16rpx;font-weight:900;line-height:1.2}
.page{min-height:100vh;padding:34rpx}.intro{display:flex;align-items:flex-start;justify-content:space-between;gap:20rpx;padding:20rpx 4rpx 34rpx}.title{font-size:48rpx;font-weight:800;display:block}.sub{font-size:24rpx;color:#8e7469;display:block;margin-top:12rpx;line-height:1.55}.refresh{margin:4rpx 0 0;background:#f4e5db;color:#873e26;font-size:21rpx}.section{margin-top:10rpx}.section-head{display:flex;justify-content:space-between;align-items:center;margin:24rpx 4rpx 18rpx;font-size:30rpx;font-weight:750}.section-head text:last-child{font-size:21rpx;color:#9a7d70;font-weight:400}.empty{padding:120rpx 34rpx;text-align:center;color:#9c8479;line-height:1.8}.create-first{width:300rpx;height:82rpx;line-height:82rpx;margin:28rpx auto 0;border-radius:42rpx;background:#963c23;color:#fff;font-size:27rpx}.asset,.job-card,.request-card{display:flex;background:#fff;border-radius:22rpx;margin-bottom:22rpx;overflow:hidden;box-shadow:0 8rpx 22rpx rgba(65,34,20,.07)}.cover,.model{width:180rpx;height:180rpx;flex-shrink:0}.cover{background:#f4e7df}.model,.job-icon{background:linear-gradient(145deg,#4b2518,#bc5a34);color:#fff;font-size:38rpx;font-weight:800;display:flex;align-items:center;justify-content:center}.body,.job-body{padding:20rpx;min-width:0;flex:1}.row{display:flex;align-items:center;gap:12rpx}.name,.request-title{font-weight:700;font-size:29rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1}.status{font-size:20rpx;border-radius:20rpx;padding:6rpx 12rpx;background:#f9e6d5;color:#a2492b;white-space:nowrap}.status.approved,.status.succeeded,.status.paid{background:#e4f5e9;color:#248653}.status.rejected,.status.failed{background:#ffe5e1;color:#ba3d2e}.status.running,.status.queued,.status.processing{background:#fff0d5;color:#aa681e}.meta,.source,.generation,.request-state,.failure,.progress-text{display:block;font-size:21rpx;color:#967c70;margin-top:10rpx;line-height:1.5}.generation{color:#8b5a42}.source{color:#9d4e30}.request-state{color:#7b5c4e}.failure{color:#ba3d2e;white-space:normal}.progress-line{height:10rpx;border-radius:8rpx;background:#f2e4da;margin-top:14rpx;overflow:hidden}.progress-value{height:100%;background:linear-gradient(90deg,#c86a40,#8b351f);border-radius:inherit}.progress-text{margin-top:7rpx;font-size:19rpx}.actions{display:flex;flex-wrap:wrap;gap:10rpx;margin-top:14rpx}.actions button{margin:0;background:#f8ede5;color:#843b23;font-size:20rpx}.actions .production{background:#f8d9c0;color:#74301d}.job-card{padding:0}.job-icon{width:130rpx;min-height:150rpx;flex-shrink:0;font-size:29rpx}.job-card.failed .job-icon{background:linear-gradient(145deg,#7f2920,#c64d3d)}.request-section{padding-bottom:40rpx}.request-card{display:block;padding:24rpx;box-sizing:border-box}.request-title{display:block}
</style>

<style scoped lang="scss">
.page{background:radial-gradient(ellipse at 10% 0%,rgba(151,177,163,.17),transparent 29%),linear-gradient(180deg,#faf8f3,#f0e9df)}.title{font-family:"Songti SC","STSong",serif;color:#302b26}.sub{color:#82786d}.refresh{background:#edf3ed;color:#607b6e}.section-head{font-family:"Songti SC","STSong",serif}.asset,.job-card,.request-card{border:1rpx solid rgba(129,112,93,.13);box-shadow:0 9rpx 21rpx rgba(67,53,37,.055)}.cover{background:#eef2eb}.model,.job-icon{background:linear-gradient(145deg,#5f7f71,#9eb5a8)}.job-card.failed .job-icon{background:linear-gradient(145deg,#865346,#bf765f)}.status{background:#f5ece4;color:#9d5c48}.status.approved,.status.succeeded,.status.paid{background:#e7f1e8;color:#567a67}.status.running,.status.queued,.status.processing{background:#f6f0df;color:#9b7540}.meta,.source,.generation,.request-state,.progress-text{color:#8c8176}.source{color:#7d9587}.progress-line{background:#ebe5dc}.progress-value{background:linear-gradient(90deg,#a56e58,#6e8b7c)}.actions button{background:#f2f5ef;color:#59776a}.actions .material{background:#dcece2;color:#426d5a}.actions .export{background:#e8edf5;color:#526b85}.actions .production{background:#efe1d5;color:#8c5947}.actions .design-review{background:#eeeaf5;color:#6b5b8b}.create-first{border-radius:17rpx;background:linear-gradient(135deg,#3e3933,#617e71)}.material-summary{display:flex;align-items:center;flex-wrap:wrap;gap:7rpx;margin-top:11rpx}.material-summary text:first-child{padding:3rpx 8rpx;border-radius:8rpx;background:#eef2ec;color:#728578;font-size:16rpx;font-weight:800}.material-summary text:nth-child(2){color:#476c5b;font-size:20rpx;font-weight:850}.material-summary text:last-child{color:#978c80;font-size:17rpx}
</style>
