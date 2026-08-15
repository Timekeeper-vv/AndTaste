<template>
  <view class="page">
    <view class="page-head">
      <view>
        <text class="eyebrow">PRODUCT WORKSPACE</text>
        <text class="page-title">产品进度</text>
        <text class="page-subtitle">已提交审核、打样或生产的项目会在这里持续更新。</text>
      </view>
      <button class="refresh-button" size="mini" :loading="loading" @tap="loadProjects(true)">刷新</button>
    </view>

    <view class="workspace-band">
      <view class="band-top"><text>CREATIVE DELIVERY</text><text>实时同步</text></view>
      <view class="band-copy"><text>从作品到产品，<br />每一步都有回应。</text><text>审核、原型、打样与生产，按项目持续推进。</text></view>
      <view class="band-stats">
        <view><text>{{ actionProjectCount }}</text><text>待我处理</text></view>
        <view><text>{{ progressingProjectCount }}</text><text>进行中</text></view>
        <view><text>{{ completedProjectCount }}</text><text>已完成</text></view>
      </view>
      <text class="band-seal">之</text>
    </view>

    <view class="section-bar">
      <view><text class="section-kicker">MY PROJECTS</text><text class="section-title">项目列表</text></view>
      <text>{{ projects.length }} 个项目</text>
    </view>
    <scroll-view class="filter-scroll" scroll-x :show-scrollbar="false">
      <view class="filter-row">
        <view v-for="filter in filters" :key="filter.key" class="filter-chip" :class="{ active: activeFilter === filter.key }" @tap="activeFilter = filter.key">
          <text>{{ filter.label }}</text><text>{{ filterCount(filter.key) }}</text>
        </view>
      </view>
    </scroll-view>

    <view v-if="loading && !projects.length" class="loading-state">
      <view class="loading-seal"><text>之</text></view><text>正在同步项目进度</text>
    </view>

    <view v-else-if="!projects.length" class="empty-state">
      <view class="empty-seal"><text>进</text></view>
      <text class="empty-title">还没有产品项目</text>
      <text class="empty-copy">提交作品审核，或创建打样、生产申请后，就能在这里继续完成每一步。</text>
      <button class="empty-action" @tap="goWorks">去作品库提交审核</button>
    </view>

    <view v-else-if="!filteredProjects.length" class="empty-state compact">
      <view class="empty-seal"><text>筛</text></view>
      <text class="empty-title">这个分类暂时没有项目</text>
      <text class="empty-copy">切换上方分类，查看其他项目状态。</text>
    </view>

    <view v-else class="project-list">
      <view v-for="project in filteredProjects" :key="project.key" class="project-card" :class="[project.state.tone, { attention: needsUserAction(project) }]">
        <view class="card-main">
          <view class="project-cover" :class="previewAssetFor(project)?.assetType || 'image'">
            <image v-if="projectPreview(project)" :src="projectPreview(project)" mode="aspectFill" class="cover-image" />
            <view v-else class="cover-fallback"><text>{{ previewAssetFor(project)?.assetType === 'model' ? '3D' : '图' }}</text></view>
            <view class="cover-shade" />
            <text class="cover-label">{{ previewAssetFor(project)?.assetType === 'model' ? '3D 原型' : '效果图' }}</text>
          </view>

          <view class="project-info">
            <view class="project-meta"><text>{{ project.request ? requestTypeText(project.request.requestType) : assetProjectText(project.asset) }}</text><text class="status" :class="project.state.tone">{{ project.state.label }}</text></view>
            <text class="project-title">{{ project.title }}</text>
            <text class="project-no">{{ project.no }} · {{ formatDate(project.updatedAt) }}</text>
            <view class="current-stage"><text>当前节点</text><text>{{ currentStageLabel(project) }}</text></view>
          </view>
        </view>

        <text class="project-note">{{ project.state.description }}</text>

        <view v-if="project.state.notice" class="project-notice" :class="project.state.tone">
          <text>{{ project.state.noticeTitle || '项目通知' }}</text><text>{{ project.state.notice }}</text>
        </view>

        <view class="timeline" :aria-label="`当前进度：${currentStageLabel(project)}`">
          <view v-for="(step, index) in steps" :key="step" class="timeline-step" :class="{ done: index < project.state.index, active: index === project.state.index }">
            <view class="timeline-mark"><text>{{ index + 1 }}</text></view><text>{{ step }}</text>
          </view>
        </view>

        <view class="card-bottom">
          <view class="project-scope"><text>{{ projectScopeText(project) }}</text><text>{{ project.state.hint }}</text></view>
          <button v-if="project.state.action" class="project-action" :class="actionClass(project.state.action)" :loading="actionBusyKey === project.key" @tap="handleProjectAction(project)">{{ actionLabel(project.state.action) }}</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { createModel, getAssetPreviewAccess, getAssets, getJobs, getProductionRequests, submitAssetReview } from '../../api/creative'
import { apiUrl } from '../../api/client'
import { confirmCreativePolicy } from '../../utils/compliance'
import { getSession, requireSession } from '../../utils/session'

const assets = ref<any[]>([])
const requests = ref<any[]>([])
const jobs = ref<any[]>([])
const loading = ref(false)
const actionBusyKey = ref('')
const securedPreviews = ref<Record<string, string>>({})
const activeFilter = ref<ProjectFilter>('all')
const steps = ['创作', '审核', '3D 原型', '打样', '生产']
const filters: Array<{ key: ProjectFilter; label: string }> = [
  { key: 'all', label: '全部' },
  { key: 'action', label: '待我处理' },
  { key: 'progressing', label: '进行中' },
  { key: 'completed', label: '已完成' },
]

type ProductAction = 'pay' | 'apply' | 'adjust' | 'submit_image_review' | 'generate_model' | 'retry_model' | 'submit_model_review' | 'resubmit_request' | 'contact' | 'refresh' | ''
type ProjectFilter = 'all' | 'action' | 'progressing' | 'completed'

interface ProjectState {
  index: number
  tone: string
  label: string
  description: string
  hint: string
  action: ProductAction
  notice?: string
  noticeTitle?: string
}

interface ProductProject {
  key: string
  asset: any
  sourceAsset?: any | null
  request: any | null
  relatedModelJob?: any | null
  relatedModelAsset?: any | null
  title: string
  no: string
  updatedAt: string
  state: ProjectState
}

const projects = computed(() => {
  const assetById = new Map(assets.value.map(asset => [String(asset.id), asset]))
  const newestModelJobByInput = new Map<string, any>()
  ;[...jobs.value]
    .filter(job => ['image_to_model', 'multiview_to_model'].includes(String(job?.jobType || '')) && job?.inputAssetId != null)
    .sort((left, right) => timestamp(right) - timestamp(left))
    .forEach(job => {
      const key = String(job.inputAssetId)
      if (!newestModelJobByInput.has(key)) newestModelJobByInput.set(key, job)
    })

  const newestRequestByAsset = new Map<string, any>()
  const sortedRequests = [...requests.value].sort((left, right) => timestamp(right) - timestamp(left))
  sortedRequests.forEach(request => {
    const key = request.assetId == null ? `request-${request.id}` : String(request.assetId)
    if (!newestRequestByAsset.has(key)) newestRequestByAsset.set(key, request)
  })

  const rows: ProductProject[] = []
  newestRequestByAsset.forEach((request, key) => {
    const asset = assetById.get(String(request.assetId)) || { id: request.assetId, title: request.assetTitle, status: 'approved', assetNo: '', assetType: request.assetType || 'model' }
    rows.push({
      key: `project-${key}`,
      asset,
      request,
      title: String(request.title || request.assetTitle || asset.title || '未命名产品'),
      no: String(request.requestNo || asset.assetNo || `项目 #${request.id || '-'}`),
      updatedAt: String(request.updatedAt || request.createdAt || asset.createdAt || ''),
      state: projectState(asset, request),
    })
  })

  assets.value
    .filter(isProjectCandidate)
    .filter(asset => {
      if (asset?.assetType !== 'image') return true
      const relatedJob = newestModelJobByInput.get(String(asset.id))
      const relatedAsset = relatedJob ? assetById.get(String(relatedJob.outputAssetId || '')) : null
      return !relatedAsset || !isProjectCandidate(relatedAsset)
    })
    .forEach(asset => {
      const key = String(asset.id)
      if (newestRequestByAsset.has(key)) return
      const relatedModelJob = asset.assetType === 'image' ? newestModelJobByInput.get(key) || null : null
      const relatedModelAsset = relatedModelJob ? assetById.get(String(relatedModelJob.outputAssetId || '')) || null : null
      rows.push({
        key: `review-${key}`,
        asset,
        sourceAsset: asset.assetType === 'model' ? assetById.get(String(asset.parentAssetId || '')) || null : null,
        request: null,
        relatedModelJob,
        relatedModelAsset,
        title: String(asset.title || '未命名作品'),
        no: String(asset.assetNo || `作品 #${asset.id || '-'}`),
        updatedAt: String(relatedModelJob?.finishedAt || relatedModelJob?.startedAt || relatedModelJob?.createdAt || asset.updatedAt || asset.createdAt || ''),
        state: projectState(asset, null, relatedModelJob, relatedModelAsset, asset.assetType === 'model' ? assetById.get(String(asset.parentAssetId || '')) || null : null),
      })
    })

  return rows.sort((left, right) => timestamp({ updatedAt: right.updatedAt }) - timestamp({ updatedAt: left.updatedAt }))
})

const actionProjectCount = computed(() => projects.value.filter(needsUserAction).length)
const progressingProjectCount = computed(() => projects.value.filter(isProgressing).length)
const completedProjectCount = computed(() => projects.value.filter(project => project.state.tone === 'complete').length)
const filteredProjects = computed(() => projects.value.filter(project => {
  if (activeFilter.value === 'action') return needsUserAction(project)
  if (activeFilter.value === 'progressing') return isProgressing(project)
  if (activeFilter.value === 'completed') return project.state.tone === 'complete'
  return true
}))

const trackedAssetStatuses = ['review', 'approved', 'rejected']
const directActions: ProductAction[] = ['pay', 'apply', 'adjust', 'submit_image_review', 'generate_model', 'retry_model', 'submit_model_review', 'resubmit_request']

function timestamp(value: any) {
  const raw = value?.updatedAt || value?.createdAt || value?.reviewedAt || ''
  const parsed = Date.parse(String(raw))
  return Number.isFinite(parsed) ? parsed : 0
}

function isMultiViewReference(asset: any) {
  const tags = String(asset?.tags || '')
  return tags.includes('多视图') || tags.includes('3D参考')
}

function isProjectCandidate(asset: any) {
  if (!asset || !['image', 'model'].includes(String(asset.assetType || ''))) return false
  if (isMultiViewReference(asset)) return false
  const status = String(asset.status || '').toLowerCase()
  if (trackedAssetStatuses.includes(status)) return true
  return status === 'draft' && String(asset.sourceType || '') === 'ai_generated'
}

function needsUserAction(project: ProductProject) {
  return directActions.includes(project.state.action)
}

function isProgressing(project: ProductProject) {
  if (project.state.tone === 'complete') return false
  return project.state.tone === 'active' || project.state.action === 'refresh' || project.state.action === 'contact'
}

function filterCount(filter: ProjectFilter) {
  if (filter === 'action') return actionProjectCount.value
  if (filter === 'progressing') return progressingProjectCount.value
  if (filter === 'completed') return completedProjectCount.value
  return projects.value.length
}

function previewAssetFor(project: ProductProject) {
  return project.relatedModelAsset || project.asset || project.sourceAsset || null
}

function projectPreview(project: ProductProject) {
  const asset = previewAssetFor(project)
  return asset?.id ? securedPreviews.value[String(asset.id)] || '' : ''
}

function currentStageLabel(project: ProductProject) {
  return steps[Math.max(0, Math.min(project.state.index, steps.length - 1))]
}

function projectState(asset: any, request: any | null, relatedModelJob?: any | null, relatedModelAsset?: any | null, sourceAsset?: any | null): ProjectState {
  const assetStatus = String(asset?.status || '').toLowerCase()
  if (!request) {
    if (assetStatus === 'draft') {
      if (asset?.assetType === 'model') {
        return { index: 2, tone: 'ready', label: '待提交 3D 模型审核', description: '3D 原型已生成。提交审核通过后，即可创建打样或生产项目。', hint: '等待提交模型审核', action: 'submit_model_review' }
      }
      return { index: 0, tone: 'ready', label: '待提交效果图审核', description: '效果图已生成。提交审核后，可继续生成 3D 原型并进入打样或生产。', hint: '等待提交审核', action: 'submit_image_review' }
    }
    if (assetStatus === 'rejected') {
      return {
        index: 1,
        tone: 'warning',
        label: '需要调整',
        description: '审核未通过，请根据审核反馈调整作品后重新提交。',
        hint: '等待作品调整',
        action: asset?.assetType === 'model' && sourceAsset?.assetType === 'image' ? 'retry_model' : 'adjust',
        noticeTitle: '审核未通过',
        notice: assetReviewNotice(asset),
      }
    }
    if (assetStatus === 'approved' && asset?.assetType !== 'model') {
      const modelStatus = String(relatedModelJob?.status || '').toLowerCase()
      if (['queued', 'running', 'processing', 'pending'].includes(modelStatus)) {
        return { index: 2, tone: 'active', label: '3D 建模中', description: '正在以审核通过的产品图生成 3D 原型，完成后可继续提交模型审核。', hint: `建模进度 ${Number(relatedModelJob?.progress) || 0}%`, action: 'refresh' }
      }
      if (modelStatus === 'failed') {
        return { index: 2, tone: 'warning', label: '3D 建模未完成', description: '本次 3D 原型没有生成成功，可基于同一张审核通过的产品图重新提交。', hint: '等待重新提交', action: 'retry_model', noticeTitle: '3D 建模通知', notice: String(relatedModelJob?.errorMessage || '建模服务未返回具体原因，请重新提交或联系项目顾问。') }
      }
      if (modelStatus === 'succeeded' && !relatedModelAsset) {
        return { index: 2, tone: 'active', label: '3D 结果同步中', description: '3D 原型已完成，正在同步到作品库。', hint: '请稍后刷新', action: 'refresh' }
      }
      if (relatedModelAsset && String(relatedModelAsset.status || '').toLowerCase() === 'draft') {
        return { index: 2, tone: 'ready', label: '3D 原型已完成', description: '3D 原型已保存。提交模型审核通过后，即可创建打样或生产项目。', hint: '等待提交模型审核', action: 'submit_model_review' }
      }
      return { index: 1, tone: 'ready', label: '审核已通过', description: '图片审核已通过。现在可直接基于当前图生成 3D 原型，随后进入打样或生产。', hint: '等待生成 3D 模型', action: 'generate_model' }
    }
    if (assetStatus === 'approved') return { index: 3, tone: 'ready', label: '可创建项目', description: '3D 模型审核已通过，可以申请打样或批量生产。', hint: '等待创建项目', action: 'apply' }
    return { index: 1, tone: 'active', label: '审核中', description: '平台正在核对作品内容、版权材料和后续生产可行性。', hint: '等待审核结果', action: 'contact' }
  }

  const status = String(request.status || '').toLowerCase()
  const paymentStatus = String(request.samplePaymentStatus || '').toLowerCase()
  if (['rejected', 'returned', 'need_materials'].includes(status)) return { index: 3, tone: 'warning', label: '项目需调整', description: '申请资料需要补充或调整，处理后可重新提交。', hint: '等待项目调整', action: 'resubmit_request', noticeTitle: '项目审核反馈', notice: String(request.reviewComment || '项目资料尚不完整，请根据平台审核意见补充或调整后重新提交。') }
  if (['completed', 'shipped'].includes(status)) return { index: 4, tone: 'complete', label: '已完成', description: '产品项目已完成。需要查询交付或售后信息，可联系项目顾问。', hint: '项目已完成', action: 'contact' }
  if (['producing', 'production', 'in_progress'].includes(status)) return { index: 4, tone: 'active', label: '生产中', description: '供应链正在推进生产。需要核对交付信息时可联系项目顾问。', hint: '等待生产完成', action: 'contact' }
  if (status === 'approved' && ['unpaid', 'pending', 'manual_review'].includes(paymentStatus)) return { index: 3, tone: 'warning', label: '待支付打样费', description: '项目已通过，完成打样费支付后会进入打样安排。', hint: '等待打样费支付', action: 'pay' }
  if (status === 'approved' && paymentStatus === 'paid') return { index: 3, tone: 'active', label: '打样安排中', description: '已完成打样费支付，供应链正在安排打样。', hint: '等待打样结果', action: 'contact' }
  if (status === 'approved') return { index: 3, tone: 'ready', label: '项目已通过', description: '项目审核已通过，正在进入打样或生产对接。', hint: '等待供应链安排', action: 'contact' }
  return { index: 3, tone: 'active', label: '项目审核中', description: '平台正在核对打样或生产资料。需要补充说明时可联系项目顾问。', hint: '等待项目审核', action: 'contact' }
}

function requestTypeText(type?: string) {
  return type === 'bulk' ? '批量生产项目' : '打样项目'
}

function distributionText(request: any) {
  return request?.requestType === 'bulk' ? '批量生产' : '打样申请'
}

function assetProjectText(asset: any) {
  const status = String(asset?.status || '').toLowerCase()
  if (status === 'draft' && asset?.assetType === 'model') return '待审核 3D 原型'
  if (status === 'draft') return '待审核效果图'
  if (status === 'approved' && asset?.assetType !== 'model') return '图片审核项目'
  if (asset?.assetType === 'model') return '3D 模型审核项目'
  return '作品审核项目'
}

function projectScopeText(project: ProductProject) {
  if (project.request) return `${Number(project.request.quantity || 0)} 件 · ${distributionText(project.request)}`
  if (project.state.label === '待提交效果图审核') return '效果图已生成 · 提交审核后进入产品流程'
  if (project.state.label === '待提交 3D 模型审核') return '3D 原型已生成 · 审核通过后可申请打样或生产'
  if (project.state.label === '3D 建模中') return '审核通过的产品图 · 正在生成 3D 原型'
  if (project.state.label === '3D 建模未完成') return '审核通过的产品图 · 可重新提交 3D 建模'
  if (project.state.label === '3D 结果同步中') return '3D 原型已完成 · 正在同步到作品库'
  if (project.state.label === '3D 原型已完成') return '3D 原型已保存 · 待提交模型审核'
  return assetProjectText(project.asset)
}

function assetReviewNotice(asset: any) {
  const tags = String(asset?.tags || '')
  const match = tags.match(/(?:^|;)审核:rejected(?:-([^;]+))?/)
  return match?.[1]?.trim() || '审核未通过，请根据作品的版权、内容或生产可行性要求调整后重新提交。'
}

function formatDate(value?: string) {
  if (!value) return '刚刚更新'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  const part = (number: number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}.${part(date.getMonth() + 1)}.${part(date.getDate())}`
}

function goWorks() {
  uni.navigateTo({ url: '/pages/works/index' })
}

function goSamplePayment(request?: any) {
  const requestId = String(request?.id || '')
  uni.navigateTo({ url: requestId ? `/pages/sample-payment/index?requestId=${encodeURIComponent(requestId)}` : '/pages/sample-payment/index' })
}

function applyProduction(asset: any) {
  if (!asset?.id) return goWorks()
  if (asset.assetType !== 'model') {
    uni.showToast({ title: '请先生成并审核通过 3D 模型', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/production/index?assetId=${encodeURIComponent(String(asset.id))}&title=${encodeURIComponent(String(asset.title || '3D模型'))}` })
}

function goSupport() {
  uni.navigateTo({ url: '/pages/support/index' })
}

function actionLabel(action: ProductAction) {
  return ({
    pay: '支付打样费',
    apply: '申请打样 / 生产',
    adjust: '查看审核反馈并调整作品',
    submit_image_review: '提交效果图审核',
    generate_model: '基于当前图生成 3D 原型',
    retry_model: '重新提交 3D 建模',
    submit_model_review: '提交 3D 模型审核',
    resubmit_request: '补充资料并重新提交申请',
    contact: '联系项目顾问',
    refresh: '刷新最新进度',
  } as Record<ProductAction, string>)[action] || ''
}

function actionClass(action: ProductAction) {
  return {
    pay: action === 'pay',
    secondary: ['adjust', 'contact', 'refresh'].includes(action),
  }
}

function confirmAction(title: string, content: string, confirmText: string) {
  return new Promise<boolean>((resolve) => {
    uni.showModal({ title, content, confirmText, cancelText: '暂不继续', success: result => resolve(Boolean(result.confirm)), fail: () => resolve(false) })
  })
}

async function startModel(project: ProductProject) {
  const asset = project.asset?.assetType === 'image' ? project.asset : project.sourceAsset
  if (!asset?.id || asset.assetType !== 'image') {
    uni.showToast({ title: '当前作品不能用于单图 3D 建模', icon: 'none' })
    return
  }
  if (!(await confirmCreativePolicy('three-dimensional'))) return
  if (!(await confirmAction('开始生成 3D 原型', '将以当前审核通过的产品图为唯一参考生成 3D 原型。模型完成后仍需提交审核，审核通过后才能创建打样或生产项目。', '开始建模'))) return

  actionBusyKey.value = project.key
  try {
    const title = String(asset.title || '文创产品')
    const prompt = String(asset.prompt || `${title}，基于当前审核通过的产品图生成一致的可生产 3D 原型。`)
    const response = await createModel({
      title: `${title} · 单图 3D 原型`,
      prompt,
      rawPrompt: prompt,
      mode: 'image_to_model',
      inputAssetId: Number(asset.id),
      productCategory: title,
      exportFormats: 'GLB',
      texture: true,
      pbr: true,
      textureQuality: 'extreme',
      geometryQuality: 'detailed',
      textureAlignment: 'original_image',
      orientation: 'align_image',
      autoSize: true,
      imageAutofix: true,
      exportUv: true,
      faceLimit: 2000000,
    })
    if (!Number(response?.jobId)) throw new Error('3D 服务没有返回任务编号，请稍后重试')
    uni.showToast({ title: '3D 建模已提交', icon: 'success' })
    await loadProjects(false)
  } catch (error: any) {
    uni.showToast({ title: error?.message || '3D 建模提交失败', icon: 'none' })
  } finally {
    actionBusyKey.value = ''
  }
}

async function submitImageReview(project: ProductProject) {
  const asset = project.asset
  if (!asset?.id || asset.assetType !== 'image') {
    uni.showToast({ title: '当前效果图无法提交审核', icon: 'none' })
    return
  }
  const context = uni.getStorageSync('creation_context') || {}
  const purpose = context.purpose === 'museum_sale' ? 'museum_sale' : 'personal'
  const museumId = context.museum?.id
  const campaign = context.campaign && typeof context.campaign === 'object' ? context.campaign : null
  const campaignKey = typeof campaign?.key === 'string' ? campaign.key : ''
  if (purpose === 'museum_sale' && !museumId) {
    const goToPurpose = await confirmAction('请先选择售卖渠道', '博物馆或景区售卖的效果图需要先确定投放渠道，才能提交审核。', '去选择')
    if (goToPurpose) uni.navigateTo({ url: '/pages/purpose/index' })
    return
  }
  if (campaignKey && (purpose !== 'museum_sale' || campaign.channelCode !== context.museum?.channelCode)) {
    uni.showToast({ title: '优先征集任务与当前渠道不一致，请重新选择任务方向', icon: 'none' })
    return
  }
  const destination = purpose === 'museum_sale' ? `博物馆售卖 · ${context.museum?.name || ''}` : '个人创作'
  const content = campaignKey
    ? `将按「${destination}」投稿「${campaign.title || '优先征集'}」。审核通过后自动获得 ${campaign.rewardAmount || ''} 积分。`
    : `将按「${destination}」提交效果图审核。通过后可继续生成 3D 原型。`
  if (!(await confirmAction('提交效果图审核', content, '提交审核'))) return

  actionBusyKey.value = project.key
  try {
    const response = await submitAssetReview(asset.id, { purpose, museumId, ...(campaignKey ? { campaignKey } : {}) })
    if (campaignKey) {
      const nextContext = { ...context }
      delete nextContext.campaign
      uni.setStorageSync('creation_context', nextContext)
    }
    uni.showToast({ title: response?.message || '效果图已提交审核', icon: 'success' })
    await loadProjects(false)
  } catch (error: any) {
    uni.showToast({ title: error?.message || '提交审核失败', icon: 'none' })
  } finally {
    actionBusyKey.value = ''
  }
}

async function submitModelReview(project: ProductProject) {
  const model = project.relatedModelAsset || (project.asset?.assetType === 'model' ? project.asset : null)
  if (!model?.id) {
    uni.showToast({ title: '3D 模型尚未同步完成，请刷新后重试', icon: 'none' })
    return
  }
  const context = uni.getStorageSync('creation_context') || {}
  const purpose = context.purpose === 'museum_sale' ? 'museum_sale' : 'personal'
  const museumId = context.museum?.id
  const campaign = context.campaign && typeof context.campaign === 'object' ? context.campaign : null
  const campaignKey = typeof campaign?.key === 'string' ? campaign.key : ''
  if (purpose === 'museum_sale' && !museumId) {
    const goToPurpose = await confirmAction('请先选择售卖渠道', '博物馆或景区售卖的 3D 模型需要先确定投放渠道，才能提交审核。', '去选择')
    if (goToPurpose) uni.navigateTo({ url: '/pages/purpose/index' })
    return
  }
  if (campaignKey && (purpose !== 'museum_sale' || campaign.channelCode !== context.museum?.channelCode)) {
    uni.showToast({ title: '优先征集任务与当前渠道不一致，请重新选择任务方向', icon: 'none' })
    return
  }
  const destination = purpose === 'museum_sale' ? `博物馆售卖 · ${context.museum?.name || ''}` : '个人创作'
  if (!(await confirmAction('提交 3D 模型审核', `将按「${destination}」提交 3D 模型。审核通过后可创建打样或生产项目。`, '提交审核'))) return

  actionBusyKey.value = project.key
  try {
    const response = await submitAssetReview(model.id, { purpose, museumId, ...(campaignKey ? { campaignKey } : {}) })
    if (campaignKey) {
      const nextContext = { ...context }
      delete nextContext.campaign
      uni.setStorageSync('creation_context', nextContext)
    }
    uni.showToast({ title: response?.message || '3D 模型已提交审核', icon: 'success' })
    await loadProjects(false)
  } catch (error: any) {
    uni.showToast({ title: error?.message || '提交审核失败', icon: 'none' })
  } finally {
    actionBusyKey.value = ''
  }
}

async function handleProjectAction(project: ProductProject) {
  if (actionBusyKey.value) return
  const action = project.state.action
  if (action === 'pay') return goSamplePayment(project.request)
  if (action === 'apply' || action === 'resubmit_request') return applyProduction(project.asset)
  if (action === 'adjust') return goWorks()
  if (action === 'submit_image_review') return submitImageReview(project)
  if (action === 'generate_model' || action === 'retry_model') return startModel(project)
  if (action === 'submit_model_review') return submitModelReview(project)
  if (action === 'contact') return goSupport()
  if (action === 'refresh') return loadProjects(true)
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

async function hydratePreviews(rows: ProductProject[]) {
  const visualAssets = rows
    .map(previewAssetFor)
    .filter((asset, index, source) => asset?.id && ['image', 'model'].includes(asset.assetType) && source.findIndex(item => String(item?.id) === String(asset.id)) === index)
    .slice(0, 16)
  const pairs = await Promise.all(visualAssets.map(async (asset) => {
    try {
      const access = await getAssetPreviewAccess(asset.id)
      const raw = asset.assetType === 'model' ? access?.previewUrl : (access?.previewUrl || access?.url)
      const url = absoluteMediaUrl(raw, String(asset.id), access?.accessToken)
      return url ? [String(asset.id), url] as const : null
    } catch {
      return null
    }
  }))
  const next: Record<string, string> = {}
  pairs.forEach(pair => { if (pair) next[pair[0]] = pair[1] })
  securedPreviews.value = next
}

async function loadProjects(notify = false) {
  if (!requireSession()) return
  loading.value = true
  try {
    const [assetResult, requestResult, jobResult] = await Promise.allSettled([getAssets(), getProductionRequests(), getJobs()])
    if (assetResult.status === 'fulfilled') assets.value = Array.isArray(assetResult.value) ? assetResult.value : []
    if (requestResult.status === 'fulfilled') requests.value = Array.isArray(requestResult.value) ? requestResult.value : []
    if (jobResult.status === 'fulfilled') jobs.value = Array.isArray(jobResult.value) ? jobResult.value : []
    void hydratePreviews(projects.value)
    const partialFailure = [assetResult, requestResult, jobResult].some(result => result.status === 'rejected')
    if (partialFailure) uni.showToast({ title: '部分状态暂未同步，已展示可用数据', icon: 'none' })
    else if (notify) uni.showToast({ title: '项目进度已更新', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '项目进度加载失败', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

onShow(() => { if (getSession()) void loadProjects() })
onPullDownRefresh(() => { if (getSession()) void loadProjects(true); else uni.stopPullDownRefresh() })
</script>

<style scoped lang="scss">
.page{min-height:100vh;box-sizing:border-box;padding:34rpx 30rpx calc(64rpx + env(safe-area-inset-bottom));background:#f3f5f1;color:#25332d}
.page-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20rpx;padding:4rpx 3rpx 25rpx}.page-head>view{display:flex;min-width:0;flex:1;flex-direction:column}.eyebrow,.section-kicker{color:#6f897a;font-size:16rpx;font-weight:900;letter-spacing:2.1rpx}.page-title{margin-top:8rpx;color:#20342c;font-family:"Songti SC","STSong",serif;font-size:47rpx;font-weight:850;line-height:1.16}.page-subtitle{margin-top:10rpx;color:#808a82;font-size:20rpx;line-height:1.5}.refresh-button{flex:none;height:56rpx;line-height:54rpx;margin:7rpx 0 0;padding:0 17rpx;border:1rpx solid #d7e0d8;border-radius:10rpx;background:#fbfcf9;color:#4c705e;font-size:19rpx;font-weight:800}.refresh-button::after,.project-action::after,.empty-action::after{border:0}

.workspace-band{position:relative;display:flex;min-height:294rpx;box-sizing:border-box;overflow:hidden;flex-direction:column;padding:25rpx 26rpx 0;border-radius:12rpx;background:#153e34;color:#fffdf8;box-shadow:0 16rpx 35rpx rgba(31,61,51,.14)}.workspace-band::before{position:absolute;top:-92rpx;right:62rpx;width:242rpx;height:458rpx;border-right:1rpx solid rgba(245,221,175,.26);border-left:1rpx solid rgba(245,221,175,.14);content:"";transform:rotate(19deg)}.workspace-band::after{position:absolute;right:0;bottom:0;left:0;height:1rpx;background:rgba(255,255,255,.18);content:""}.band-top,.band-copy,.band-stats{position:relative;z-index:2}.band-top{display:flex;align-items:center;justify-content:space-between;color:#bcd1c4;font-size:16rpx;font-weight:850;letter-spacing:1.5rpx}.band-top text:last-child{padding:5rpx 9rpx;border:1rpx solid rgba(255,255,255,.18);border-radius:7rpx;color:#e9c98a;font-size:16rpx;letter-spacing:0}.band-copy{display:flex;max-width:75%;flex-direction:column;margin-top:28rpx}.band-copy text:first-child{font-family:"Songti SC","STSong",serif;font-size:38rpx;font-weight:850;line-height:1.3}.band-copy text:last-child{margin-top:12rpx;color:#c3d2c8;font-size:19rpx;line-height:1.55}.band-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:0;margin-top:auto}.band-stats view{display:flex;min-height:80rpx;flex-direction:column;justify-content:center;padding:0 14rpx;border-top:1rpx solid rgba(255,255,255,.14)}.band-stats view:not(:last-child){border-right:1rpx solid rgba(255,255,255,.14)}.band-stats text:first-child{color:#fffdf8;font-family:"Songti SC","STSong",serif;font-size:31rpx;font-weight:850}.band-stats text:last-child{margin-top:4rpx;color:#b9cabf;font-size:17rpx}.band-seal{position:absolute;z-index:1;right:28rpx;bottom:79rpx;color:rgba(232,198,130,.93);font-family:"Songti SC","STSong",serif;font-size:128rpx;font-weight:850;line-height:1;transform:rotate(10deg)}

.section-bar{display:flex;align-items:flex-end;justify-content:space-between;gap:20rpx;margin:32rpx 4rpx 14rpx}.section-bar>view{display:flex;flex-direction:column}.section-title{margin-top:4rpx;color:#263a31;font-family:"Songti SC","STSong",serif;font-size:31rpx;font-weight:850}.section-bar>text{padding-bottom:3rpx;color:#8c968e;font-size:18rpx}.filter-scroll{width:calc(100% + 60rpx);margin:0 -30rpx;white-space:nowrap}.filter-row{display:flex;gap:11rpx;padding:2rpx 30rpx 8rpx}.filter-chip{display:flex;align-items:center;gap:8rpx;flex:none;min-height:56rpx;padding:0 15rpx;border:1rpx solid #dce3dc;border-radius:9rpx;background:#fbfcfa;color:#77847c;font-size:19rpx}.filter-chip text:last-child{display:grid;place-items:center;min-width:25rpx;height:25rpx;padding:0 4rpx;border-radius:5rpx;background:#edf1ed;color:#829087;font-size:16rpx;font-weight:850}.filter-chip.active{border-color:#19483b;background:#19483b;color:#fffdf8;font-weight:850}.filter-chip.active text:last-child{background:#e3b46e;color:#153e34}

.loading-state,.empty-state{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:13rpx;min-height:374rpx;margin-top:15rpx;padding:30rpx;box-sizing:border-box;border:1rpx dashed #cbd8cf;border-radius:12rpx;background:#fbfcfa;color:#7f8a81;font-size:22rpx;text-align:center;line-height:1.6}.loading-seal,.empty-seal{display:grid;place-items:center;width:70rpx;height:70rpx;border:1rpx solid #a1bbab;border-radius:11rpx;background:#e9f0e9;color:#416b56;font-family:"Songti SC","STSong",serif;font-size:36rpx;font-weight:850}.loading-seal{animation:seal-breathe 1.5s ease-in-out infinite}.empty-title{color:#34463b;font-family:"Songti SC","STSong",serif;font-size:31rpx;font-weight:850}.empty-copy{max-width:510rpx;color:#879087;font-size:20rpx}.empty-state.compact{min-height:244rpx}.empty-action{height:76rpx;line-height:76rpx;margin-top:8rpx;padding:0 24rpx;border-radius:10rpx;background:#1c503f;color:#fffdf8;font-size:21rpx;font-weight:850}

.project-list{display:flex;flex-direction:column;gap:18rpx;margin-top:14rpx}.project-card{position:relative;overflow:hidden;padding:20rpx;border:1rpx solid #dce3dc;border-radius:12rpx;background:#fbfcfa;box-shadow:0 10rpx 24rpx rgba(38,59,49,.055)}.project-card::before{position:absolute;top:0;bottom:0;left:0;width:5rpx;background:#8ba898;content:""}.project-card.warning::before{background:#d58e4f}.project-card.complete::before{background:#609a72}.project-card.attention{border-color:#cddbd0;box-shadow:0 13rpx 27rpx rgba(35,72,58,.09)}.card-main{display:flex;align-items:stretch;gap:17rpx}.project-cover{position:relative;display:flex;align-items:flex-end;justify-content:flex-start;flex:none;width:174rpx;height:174rpx;overflow:hidden;border-radius:9rpx;background:#7e9b8c}.project-cover.model{background:#7a9195}.cover-image,.cover-shade,.cover-fallback{position:absolute;inset:0;width:100%;height:100%}.cover-image{z-index:1}.cover-shade{z-index:2;background:linear-gradient(180deg,rgba(16,34,27,.03) 38%,rgba(15,42,34,.72) 100%)}.cover-fallback{display:grid;place-items:center;background:#527566;color:#f8f5ec;font-family:"Songti SC","STSong",serif;font-size:39rpx;font-weight:850}.project-cover.model .cover-fallback{background:#657b80}.cover-label{position:relative;z-index:3;margin:0 12rpx 11rpx;color:#fffdf8;font-size:16rpx;font-weight:850}.project-info{display:flex;min-width:0;flex:1;flex-direction:column}.project-meta{display:flex;align-items:center;justify-content:space-between;gap:10rpx}.project-meta>text:first-child{overflow:hidden;min-width:0;flex:1;color:#778f81;font-size:16rpx;font-weight:850;letter-spacing:.5rpx;text-overflow:ellipsis;white-space:nowrap}.status{flex:none;padding:6rpx 8rpx;border-radius:6rpx;background:#e8eee9;color:#587964;font-size:16rpx;font-weight:850}.status.warning{background:#f8ead8;color:#996832}.status.ready{background:#e5f0e6;color:#397057}.status.complete{background:#dceee1;color:#397051}.project-title{overflow:hidden;margin-top:11rpx;color:#2e4036;font-family:"Songti SC","STSong",serif;font-size:28rpx;font-weight:850;line-height:1.3;text-overflow:ellipsis;white-space:nowrap}.project-no{overflow:hidden;margin-top:6rpx;color:#929b93;font-size:16rpx;text-overflow:ellipsis;white-space:nowrap}.current-stage{display:flex;align-items:center;justify-content:space-between;gap:8rpx;margin-top:auto;padding-top:11rpx;border-top:1rpx solid #e5eae5;color:#87928a;font-size:16rpx}.current-stage text:last-child{overflow:hidden;color:#3e715a;font-size:18rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.project-note{display:block;min-height:51rpx;margin-top:17rpx;color:#6f7e74;font-size:20rpx;line-height:1.56}

.project-notice{display:flex;flex-direction:column;gap:6rpx;margin-top:13rpx;padding:12rpx 13rpx;border-left:3rpx solid #d8904e;background:#fff7eb;color:#855f32}.project-notice text:first-child{font-size:17rpx;font-weight:900}.project-notice text:last-child{font-size:19rpx;line-height:1.55}.project-notice.active{border-color:#80a58d;background:#f0f7f1;color:#537563}.project-notice.ready{border-color:#80a58d;background:#f0f7f1;color:#537563}.project-notice.complete{border-color:#6c9d77;background:#edf8ef;color:#467052}

.timeline{display:flex;align-items:flex-start;margin-top:19rpx;padding:17rpx 0 15rpx;border-top:1rpx solid #e2e8e2;border-bottom:1rpx solid #e2e8e2}.timeline-step{position:relative;display:flex;min-width:0;flex:1;flex-direction:column;gap:7rpx;color:#9ba49d;font-size:15rpx;text-align:center}.timeline-step:not(:last-child)::after{position:absolute;top:10rpx;right:-50%;left:50%;height:2rpx;background:#dce4dc;content:""}.timeline-mark{position:relative;z-index:1;display:grid;place-items:center;width:22rpx;height:22rpx;margin:0 auto;border-radius:50%;background:#dce4dc;color:#89948c;font-size:13rpx;font-weight:850}.timeline-step.done{color:#5f866f}.timeline-step.done .timeline-mark{background:#7ca38b;color:#fff}.timeline-step.done:not(:last-child)::after{background:#9fbaa6}.timeline-step.active{color:#315f4a;font-weight:850}.timeline-step.active .timeline-mark{box-shadow:0 0 0 6rpx #dcecdf;background:#285f4b;color:#fff}.timeline-step.active:not(:last-child)::after{background:#dce4dc}

.card-bottom{display:flex;align-items:flex-end;justify-content:space-between;gap:18rpx;margin-top:15rpx}.project-scope{display:flex;min-width:0;flex:1;flex-direction:column;gap:4rpx;color:#879188;font-size:16rpx;line-height:1.45}.project-scope text:first-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.project-scope text:last-child{color:#537a65;font-weight:750}.project-action{flex:none;max-width:330rpx;height:64rpx;line-height:64rpx;margin:0;padding:0 18rpx;border-radius:9rpx;background:#1c503f;color:#fffdf8;font-size:19rpx;font-weight:850}.project-action.pay{background:#c96e4e}.project-action.secondary{border:1rpx solid #d7e1d8;background:#f1f5f1;color:#567364}

@keyframes seal-breathe{0%,100%{transform:scale(.95);opacity:.72}50%{transform:scale(1);opacity:1}}
</style>
