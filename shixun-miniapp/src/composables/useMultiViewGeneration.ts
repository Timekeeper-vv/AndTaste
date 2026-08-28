import { computed, ref, type Ref } from 'vue'
import {
  DEFAULT_SEEDREAM_IMAGE_SIZE,
  getArkImageJob,
  waitForArkImageJob,
  type ImageGenerationJobProgress,
} from '../api/client'
import {
  createMultiViewBundle as createMultiViewBundleApi,
  createSeedreamMultiView as createSeedreamMultiViewApi,
  getMyMultiViewBundles as getMyMultiViewBundlesApi,
  submitMultiViewBundleReview,
  type MultiViewBundle,
  type SeedreamMultiViewImage,
  type SeedreamProductionSimulationImage,
} from '../api/creative'
import {
  compileCreativeImageRequest,
  type CreativeEngineInput,
  type CreativeImageRequest,
  type CreativeProductLike,
} from '../utils/creativeEngineRuntime'

type ReadonlyValue<T> = { readonly value: T }

export interface MultiViewGenerationOptions {
  selectedProduct: ReadonlyValue<CreativeProductLike | null>
  productKey: ReadonlyValue<string | undefined>
  productCategory: ReadonlyValue<string | undefined>
  productType: ReadonlyValue<string | undefined>
  material: ReadonlyValue<string>
  productSize: ReadonlyValue<string>
  prompt: ReadonlyValue<string>
  generatedAssetId: Ref<number | null>
  productNo?: Ref<string>
  projectId?: ReadonlyValue<number | null>
  versionId?: ReadonlyValue<number | null>
  busy: Ref<boolean>
  busyMessage: Ref<string>
  multiviewImages: Ref<SeedreamMultiViewImage[]>
  /** Complete horizontal production-simulation triptych, when supported. */
  simulationAssetId?: Ref<number | null>
  simulationImage?: Ref<SeedreamProductionSimulationImage | null>
  multiviewBundleId: Ref<number | null>
  multiviewBundleNo: Ref<string>
  multiviewBundleStatus: Ref<string>
  multiviewBundleComment: Ref<string>
  multiviewBundleSubmitting: Ref<boolean>
  pendingMultiViewJobId?: Ref<number | null>
  pendingMultiViewInputAssetId?: Ref<number | null>
  pendingMultiViewPrompt?: Ref<string>
  ensureAiPolicy: () => Promise<boolean>
  saveEvent: (step: string, eventType: string, payload: Record<string, any>) => Promise<void>
  saveEventBestEffort: (step: string, eventType: string, payload?: Record<string, any>) => Promise<void>
  freshAssetPreview: (assetId: number) => Promise<string>
  updateImageQueueMessage?: (job: ImageGenerationJobProgress) => void
  updateMultiViewChatState?: () => void
  createMultiViewBundle?: typeof createMultiViewBundleApi
  createSeedreamMultiView?: typeof createSeedreamMultiViewApi
  getMyMultiViewBundles?: typeof getMyMultiViewBundlesApi
  getImageJob?: typeof getArkImageJob
  waitForImageJob?: typeof waitForArkImageJob
  /** Called after a complete three-view bundle is persisted. */
  onGenerated?: (result: any, inputAssetId: number, bundle: MultiViewBundle, prompt?: string) => Promise<void> | void
  /** Called when a restored bundle has been applied, allowing the page to scroll/update UI. */
  onRestored?: (bundle: MultiViewBundle) => Promise<void> | void
}

export interface MultiViewRequestContext {
  productKey?: string
  productCategory?: string
  productType?: string
  material?: string
  productSize?: string
  prompt?: string
  rawPrompt?: string
  inputAssetId?: number | string | null
}

const VIEW_ORDER = ['front', 'left', 'back'] as const
const VIEW_LABELS: Record<string, string> = { front: '正面', left: '侧面', back: '背面' }

function positiveId(value: unknown) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

export function useMultiViewGeneration(options: MultiViewGenerationOptions) {
  const pendingMultiViewJobId = options.pendingMultiViewJobId || ref<number | null>(null)
  const pendingMultiViewInputAssetId = options.pendingMultiViewInputAssetId || ref<number | null>(null)
  const pendingMultiViewPrompt = options.pendingMultiViewPrompt || ref('')
  const createBundle = options.createMultiViewBundle || createMultiViewBundleApi
  const createViews = options.createSeedreamMultiView || createSeedreamMultiViewApi
  const listBundles = options.getMyMultiViewBundles || getMyMultiViewBundlesApi
  const getImageJob = options.getImageJob || getArkImageJob
  const waitForImageJob = options.waitForImageJob || waitForArkImageJob
  // A page can resume the same completed job from two lifecycle callbacks.
  // Serialize bundle lookup/creation so both paths cannot create duplicates.
  let bundleResolutionPromise: Promise<MultiViewBundle> | null = null

  const hasCompleteThreeViews = computed(() => {
    const available = new Set(options.multiviewImages.value.map(item => String(item?.view || '').toLowerCase()))
    return VIEW_ORDER.every(view => available.has(view))
  })

  function currentCreativeEngineInput(overrides: Partial<CreativeEngineInput> = {}): CreativeEngineInput {
    return {
      product: options.selectedProduct.value,
      productKey: options.productKey.value,
      productCategory: options.productCategory.value || options.productType.value,
      productType: options.productType.value,
      material: options.material.value,
      productSize: options.productSize.value,
      ...overrides,
    }
  }

  function compileMultiViewRequest(overrides: MultiViewRequestContext = {}): CreativeImageRequest {
    const inputAssetId = positiveId(overrides.inputAssetId)
    const requestOverrides: Partial<CreativeEngineInput> = {
      prompt: overrides.prompt ?? options.prompt.value,
      rawPrompt: overrides.rawPrompt ?? overrides.prompt ?? options.prompt.value,
      inputAssetId,
      purpose: 'multiview',
      refinement: false,
    }
    if (overrides.productKey !== undefined) requestOverrides.productKey = overrides.productKey
    if (overrides.productCategory !== undefined) requestOverrides.productCategory = overrides.productCategory
    if (overrides.productType !== undefined) requestOverrides.productType = overrides.productType
    if (overrides.material !== undefined) requestOverrides.material = overrides.material
    if (overrides.productSize !== undefined) requestOverrides.productSize = overrides.productSize
    return compileCreativeImageRequest(currentCreativeEngineInput(requestOverrides))
  }

  function updateImageQueueMessage(job: ImageGenerationJobProgress) {
    if (options.updateImageQueueMessage) {
      options.updateImageQueueMessage(job)
      return
    }
    if (job.status === 'queued') {
      options.busyMessage.value = job.queuePosition && job.queuePosition > 0
        ? `已进入生成队列，前面还有 ${job.queuePosition - 1} 项任务…`
        : '已进入生成队列，马上开始…'
    } else if (job.status === 'running') {
      options.busyMessage.value = job.jobType === 'multi_view'
        ? '正在生成标准化生产模拟图，请稍候…'
        : 'Seedream 5.0 正在生成生产模拟图，请稍候…'
    }
  }

  function normalizeImages(result: any): SeedreamMultiViewImage[] {
    const images = (Array.isArray(result?.images) ? result.images : []) as SeedreamMultiViewImage[]
    return images
      .filter(item => VIEW_ORDER.includes(String(item?.view || '').toLowerCase() as typeof VIEW_ORDER[number]) && positiveId(item?.assetId))
      .map(item => {
        const view = String(item.view).toLowerCase() as SeedreamMultiViewImage['view']
        return {
          ...item,
          view,
          label: item.label || VIEW_LABELS[view] || '视图',
          assetId: positiveId(item.assetId) as number,
        }
      })
      .sort((left, right) => VIEW_ORDER.indexOf(left.view as typeof VIEW_ORDER[number]) - VIEW_ORDER.indexOf(right.view as typeof VIEW_ORDER[number]))
  }

  function normalizeSimulationImage(value: any, fallbackAssetId?: unknown): SeedreamProductionSimulationImage | null {
    const assetId = positiveId(value?.assetId || value?.id || fallbackAssetId)
    if (!assetId) return null
    const raw = value && typeof value === 'object' ? value : {}
    return {
      ...raw,
      assetId,
      label: raw.label || '生产模拟图',
    }
  }

  async function hydrateImages(images: SeedreamMultiViewImage[]) {
    return Promise.all(images.map(async item => {
      const fresh = await options.freshAssetPreview(Number(item.assetId))
      return fresh ? { ...item, previewUrl: fresh } : item
    }))
  }

  async function hydrateSimulationImage(image: SeedreamProductionSimulationImage | null | undefined) {
    if (!image || !positiveId(image.assetId)) return image || null
    const fresh = await options.freshAssetPreview(Number(image.assetId))
    return fresh ? { ...image, previewUrl: fresh } : image
  }

  function applyMultiViewBundle(bundle: MultiViewBundle | null | undefined) {
    if (!bundle) return
    if (options.productNo && bundle.productNo) options.productNo.value = String(bundle.productNo)
    const bundleId = positiveId(bundle.id || bundle.bundleId)
    if (bundleId) options.multiviewBundleId.value = bundleId
    const inputAssetId = positiveId(bundle.inputAssetId)
    if (!options.generatedAssetId.value && inputAssetId) options.generatedAssetId.value = inputAssetId
    options.multiviewBundleNo.value = String(bundle.bundleNo || options.multiviewBundleNo.value || '')
    options.multiviewBundleStatus.value = String(bundle.status || options.multiviewBundleStatus.value || 'draft')
    options.multiviewBundleComment.value = String(bundle.reviewComment || '')
    const simulationAssetId = positiveId(bundle.simulationAssetId || bundle.simulationImage?.assetId)
    if (options.simulationAssetId && simulationAssetId) options.simulationAssetId.value = simulationAssetId
    const simulationImage = normalizeSimulationImage(bundle.simulationImage, simulationAssetId)
    if (options.simulationImage && simulationImage) options.simulationImage.value = simulationImage
    if (Array.isArray(bundle.images) && bundle.images.length) {
      options.multiviewImages.value = bundle.images
        .filter(item => positiveId(item?.assetId))
        .map(item => ({ ...item, assetId: positiveId(item.assetId) as number })) as SeedreamMultiViewImage[]
    }
  }

  async function completeGeneratedMultiView(result: any, inputAssetId: number, generationPrompt = '') {
    const images = normalizeImages(result)
    const returnedViews = new Set(images.map(item => item.view))
    if (!VIEW_ORDER.every(view => returnedViews.has(view))) {
      throw new Error('生产模拟图没有完整返回正面、侧面和背面切片，请稍后重试')
    }
    const hydratedImages = await hydrateImages(images)
    const simulationAssetId = positiveId(result?.simulationAssetId || result?.simulationImage?.assetId)
    const simulationImage = await hydrateSimulationImage(normalizeSimulationImage(result?.simulationImage, simulationAssetId))
    if (options.simulationAssetId) options.simulationAssetId.value = simulationAssetId
    if (options.simulationImage) options.simulationImage.value = simulationImage
    options.multiviewImages.value = hydratedImages
    const bundle = await resolveBundle(inputAssetId, hydratedImages, simulationAssetId)
    if (options.productNo && result?.productNo) options.productNo.value = String(result.productNo)
    const finalPrompt = generationPrompt || pendingMultiViewPrompt.value || compileMultiViewRequest({ inputAssetId }).prompt
    applyMultiViewBundle(bundle)
    pendingMultiViewJobId.value = null
    pendingMultiViewInputAssetId.value = null
    pendingMultiViewPrompt.value = ''
    await options.saveEventBestEffort('multiview', 'multiview_generated', {
      jobId: result?.jobId,
      inputAssetId,
      productSize: options.productSize.value,
      prompt: finalPrompt,
      bundleId: bundle.id,
      bundleNo: bundle.bundleNo,
      bundleStatus: bundle.status,
      bundleComment: bundle.reviewComment,
      simulationAssetId: simulationAssetId || bundle.simulationAssetId,
      simulationImage: simulationImage || bundle.simulationImage,
      images: hydratedImages.map(item => ({ view: item.view, assetId: item.assetId, label: item.label })),
    })
    options.updateMultiViewChatState?.()
    await options.onGenerated?.(result, inputAssetId, bundle, finalPrompt)
    return bundle
  }

  async function generateMultiView() {
    if (options.busy.value) return
    const inputAssetId = positiveId(options.generatedAssetId.value)
    if (!inputAssetId) throw new Error('当前产品图未保存成功，请先重新生成产品图')
    if (!await options.ensureAiPolicy()) return

    options.busy.value = true
    options.busyMessage.value = '正在生成一张包含正面、侧面和背面的生产模拟图，请稍候…'
    let queueEventPromise: Promise<void> | null = null
    try {
      const request = compileMultiViewRequest({ inputAssetId, prompt: options.prompt.value, rawPrompt: options.prompt.value })
      request.projectId = options.projectId?.value || undefined
      request.versionId = options.versionId?.value || undefined
      const generationPrompt = request.prompt
      await options.saveEventBestEffort('multiview', 'multiview_started', {
        inputAssetId,
        productType: options.productType.value,
        material: options.material.value,
        productSize: options.productSize.value,
        prompt: generationPrompt,
      })
      const handleQueue = (job: ImageGenerationJobProgress) => {
        updateImageQueueMessage(job)
        const jobId = positiveId(job.jobId)
        if (!jobId || pendingMultiViewJobId.value === jobId) return
        pendingMultiViewJobId.value = jobId
        pendingMultiViewInputAssetId.value = inputAssetId
        pendingMultiViewPrompt.value = generationPrompt
        queueEventPromise = options.saveEventBestEffort('multiview', 'multiview_queued', {
          jobId,
          inputAssetId,
          prompt: generationPrompt,
          productType: options.productType.value,
          material: options.material.value,
          productSize: options.productSize.value,
        })
      }
      const result = await createViews({
        ...request,
        inputAssetId,
        purpose: 'multiview',
        viewCount: 3,
        size: DEFAULT_SEEDREAM_IMAGE_SIZE,
        watermark: true,
      }, handleQueue)
      if (queueEventPromise) await queueEventPromise
      return await completeGeneratedMultiView(result, inputAssetId, generationPrompt)
    } finally {
      options.busy.value = false
      options.busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
    }
  }

  async function resumePendingMultiViewGeneration() {
    const jobId = positiveId(pendingMultiViewJobId.value)
    const inputAssetId = positiveId(pendingMultiViewInputAssetId.value || options.generatedAssetId.value)
    if (!jobId || !inputAssetId || hasCompleteThreeViews.value || options.busy.value) return

    options.busy.value = true
    options.busyMessage.value = '正在恢复上次的生产模拟图生成进度…'
    const generationPrompt = pendingMultiViewPrompt.value || compileMultiViewRequest({ inputAssetId }).prompt
    try {
      let job = await getImageJob(jobId)
      updateImageQueueMessage(job)
      if (job.status === 'queued' || job.status === 'running') job = await waitForImageJob(job, updateImageQueueMessage)
      if (job.status === 'failed') throw new Error(job.errorMessage || job.message || '生产模拟图生成失败')
      return await completeGeneratedMultiView(job, inputAssetId, generationPrompt)
    } catch (error) {
      let failedJob: ImageGenerationJobProgress | null = null
      try {
        const latest = await getImageJob(jobId)
        if (latest.status === 'failed') failedJob = latest
      } catch {
        // Keep the pending job attached when the progress endpoint is unavailable.
      }
      if (failedJob) {
        pendingMultiViewJobId.value = null
        pendingMultiViewInputAssetId.value = null
        pendingMultiViewPrompt.value = ''
        await options.saveEvent('multiview', 'multiview_failed', {
          jobId,
          inputAssetId,
          prompt: generationPrompt,
          errorMessage: failedJob.errorMessage || failedJob.message || '生产模拟图生成失败',
        })
      }
      throw failedJob || error
    } finally {
      options.busy.value = false
      options.busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
    }
  }

  async function restoreCurrentMultiViewBundle() {
    const inputAssetId = positiveId(options.generatedAssetId.value || pendingMultiViewInputAssetId.value)
    if (!inputAssetId || !hasCompleteThreeViews.value) return null

    // Route restoration through the same serialized lookup/create path as a
    // just-completed job. This keeps onLoad/onShow from creating two bundles.
    const bundle = await resolveBundle(inputAssetId, options.multiviewImages.value, options.simulationAssetId?.value || undefined)
    applyMultiViewBundle(bundle)
    options.multiviewImages.value = await hydrateImages(options.multiviewImages.value)
    if (options.simulationImage?.value) options.simulationImage.value = await hydrateSimulationImage(options.simulationImage.value)
    options.updateMultiViewChatState?.()
    await options.onRestored?.(bundle)
    return bundle
  }

  async function resolveBundle(inputAssetId: number, images: SeedreamMultiViewImage[], simulationAssetId?: number | null) {
    if (bundleResolutionPromise) return bundleResolutionPromise
    bundleResolutionPromise = (async () => {
      const bundles = await listBundles()
      const currentBundleId = positiveId(options.multiviewBundleId.value)
      const current = currentBundleId
        ? bundles.find(item => positiveId(item.id || item.bundleId) === currentBundleId)
        : undefined
      if (current) return current
      const imageIds = new Map(images.map(item => [String(item.view).toLowerCase(), positiveId(item.assetId)]))
      const existing = bundles.find(item => positiveId(item.inputAssetId) === inputAssetId
        && Array.isArray(item.images)
        && VIEW_ORDER.every(view => item.images.some(image => String(image.view).toLowerCase() === view && positiveId(image.assetId) === imageIds.get(view))))
      if (existing) return existing
      return createBundle({
        inputAssetId,
        projectId: options.projectId?.value || undefined,
        versionId: options.versionId?.value || undefined,
        productKey: options.productKey.value,
        productName: options.productType.value,
        material: options.material.value,
        productSize: options.productSize.value,
        viewCount: 3,
        images: images.map(item => ({ view: item.view, assetId: item.assetId, label: item.label })),
        ...(simulationAssetId ? { simulationAssetId } : {}),
      })
    })()
    try {
      return await bundleResolutionPromise
    } finally {
      bundleResolutionPromise = null
    }
  }

  async function submitMultiViewReview() {
    if (options.multiviewBundleSubmitting.value || !options.multiviewBundleId.value) return
    const context = uni.getStorageSync('creation_context') || {}
    const purpose = context.purpose === 'museum_sale' ? 'museum_sale' : 'personal'
    const museumId = purpose === 'museum_sale' ? String(context.museum?.id || '') : undefined
    const campaign = context.campaign && typeof context.campaign === 'object' ? context.campaign : null
    if (purpose === 'museum_sale' && !museumId) {
      uni.showToast({ title: '请先选择服务博物馆，再提交生产模拟图审核', icon: 'none' })
      return
    }
    if (campaign?.key && (purpose !== 'museum_sale' || campaign.channelCode !== context.museum?.channelCode)) {
      uni.showToast({ title: '优先征集任务与当前渠道不一致，请重新选择方向', icon: 'none' })
      return
    }
    const confirmed = await new Promise<boolean>(resolve => {
      uni.showModal({
        title: '提交生产模拟图审核',
        content: purpose === 'museum_sale'
          ? `将把正面、侧面和背面作为一个作品包提交给${context.museum?.name || '目标渠道'}审核。`
          : '三张图会作为一个完整作品包提交审核，审核通过后才能申请打样。',
        confirmText: '提交审核',
        success: result => resolve(Boolean(result.confirm)),
        fail: () => resolve(false),
      })
    })
    if (!confirmed) return

    options.multiviewBundleSubmitting.value = true
    try {
      const response = await submitMultiViewBundleReview(options.multiviewBundleId.value, {
        purpose,
        museumId,
        projectId: options.projectId?.value || undefined,
        versionId: options.versionId?.value || undefined,
        note: '由对话式创作提交的生产模拟图作品包',
        ...(campaign?.key ? { campaignKey: campaign.key } : {}),
      })
      applyMultiViewBundle(response)
      options.updateMultiViewChatState?.()
      await options.saveEvent('multiview', 'multiview_review_submitted', {
        bundleId: options.multiviewBundleId.value,
        status: response.status,
        purpose,
      })
      uni.showToast({ title: response.message || '生产模拟图已提交审核', icon: 'success' })
      return response
    } finally {
      options.multiviewBundleSubmitting.value = false
    }
  }

  function applyMultiViewProduction() {
    if (!options.multiviewBundleId.value || options.multiviewBundleStatus.value !== 'approved') return
    const title = options.productType.value || '生产模拟图作品'
    const projectQuery = options.projectId?.value ? `&projectId=${encodeURIComponent(String(options.projectId.value))}` : ''
    const versionQuery = options.versionId?.value ? `&versionId=${encodeURIComponent(String(options.versionId.value))}` : ''
    const productQuery = options.productNo?.value ? `&productNo=${encodeURIComponent(String(options.productNo.value))}` : ''
    uni.navigateTo({ url: `/pages/production/index?bundleId=${encodeURIComponent(String(options.multiviewBundleId.value))}&title=${encodeURIComponent(title)}${projectQuery}${versionQuery}${productQuery}` })
  }

  function clearPendingMultiView() {
    pendingMultiViewJobId.value = null
    pendingMultiViewInputAssetId.value = null
    pendingMultiViewPrompt.value = ''
  }

  return {
    pendingMultiViewJobId,
    pendingMultiViewInputAssetId,
    pendingMultiViewPrompt,
    hasCompleteThreeViews,
    currentCreativeEngineInput,
    compileMultiViewRequest,
    updateImageQueueMessage,
    applyMultiViewBundle,
    completeGeneratedMultiView,
    generateMultiView,
    resumePendingMultiViewGeneration,
    restoreCurrentMultiViewBundle,
    submitMultiViewReview,
    applyMultiViewProduction,
    clearPendingMultiView,
  }
}
