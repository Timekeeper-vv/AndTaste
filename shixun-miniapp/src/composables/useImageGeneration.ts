import { ref, type Ref } from 'vue'
import {
  DEFAULT_SEEDREAM_IMAGE_SIZE,
  createReferenceToImage,
  createTextToImage,
  getArkImageJob,
  waitForArkImageJob,
  type ImageGenerationJobProgress,
} from '../api/client'
import { optimizeImageEditPrompt, optimizeImagePrompt } from '../api/creative'
import {
  compileCreativeImageRequest,
  buildReferenceRawPrompt,
  resolveCreativeProductProfile,
  type CreativeEngineInput,
  type CreativeImageRequest,
  type CreativeProductLike,
} from '../utils/creativeEngine'

export type ImageGenerationMode = 'text' | 'reference'
type ReadonlyValue<T> = { readonly value: T }

export interface ImageGenerationOptions {
  selectedProduct: ReadonlyValue<CreativeProductLike | null>
  productKey: ReadonlyValue<string | undefined>
  productCategory: ReadonlyValue<string | undefined>
  productType: ReadonlyValue<string | undefined>
  material: ReadonlyValue<string>
  productSize: ReadonlyValue<string>
  prompt: ReadonlyValue<string>
  inspirationText: ReadonlyValue<string>
  mode: ReadonlyValue<ImageGenerationMode | string>
  referenceAssetId: ReadonlyValue<number | null>
  generatedAssetId: ReadonlyValue<number | null>
  previewUrl: Ref<string>
  referenceAnalysis: Ref<string>
  busy: Ref<boolean>
  busyMessage: Ref<string>
  imageGenerationStage: Ref<'adapting_product' | ''>
  pendingImageJobId?: Ref<number | null>
  pendingGenerationPrompt?: Ref<string>
  ensureAiPolicy: () => Promise<boolean>
  requireSession: () => boolean
  saveEvent: (step: string, eventType: string, payload: Record<string, any>) => Promise<void>
  saveEventBestEffort: (step: string, eventType: string, payload?: Record<string, any>) => Promise<void>
  freshAssetPreview: (assetId: number) => Promise<string>
  updateImageQueueMessage?: (job: ImageGenerationJobProgress) => void
  onGenerated: (result: any, generationPrompt: string) => Promise<void>
  onRefined?: (result: any, generationPrompt: string, refinementNote: string) => Promise<void>
}

export function useImageGeneration(options: ImageGenerationOptions) {
  const pendingImageJobId = options.pendingImageJobId || ref<number | null>(null)
  const pendingGenerationPrompt = options.pendingGenerationPrompt || ref('')
  const imageGenerationStage = options.imageGenerationStage

  function currentCreativeEngineInput(overrides: Partial<CreativeEngineInput> = {}): CreativeEngineInput {
    return {
      product: options.selectedProduct.value,
      productKey: options.productKey.value,
      productCategory: options.productCategory.value,
      productType: options.productType.value,
      material: options.material.value,
      productSize: options.productSize.value,
      ...overrides,
    }
  }

  function compileCurrentCreativeImageRequest(overrides: Partial<CreativeEngineInput>): CreativeImageRequest {
    return compileCreativeImageRequest(currentCreativeEngineInput(overrides))
  }

  function updateImageQueueMessage(job: ImageGenerationJobProgress) {
    if (options.updateImageQueueMessage) return options.updateImageQueueMessage(job)
    if (job.status === 'queued') {
      options.busyMessage.value = imageGenerationStage.value === 'adapting_product'
        ? '产品化生成任务已排队，正在准备最终成品…'
        : job.queuePosition && job.queuePosition > 0
          ? `已进入生成队列，前面还有 ${job.queuePosition - 1} 项任务…`
          : '已进入生成队列，马上开始…'
    } else if (job.status === 'running') {
      options.busyMessage.value = imageGenerationStage.value === 'adapting_product'
        ? '正在把参考图元素转译为目标文创产品，请稍候…'
        : job.jobType === 'image_to_image'
          ? '正在依据参考图生成产品视觉，请稍候…'
          : 'Seedream 5.0 正在生成产品视觉，请稍候…'
    }
  }

  async function resolveSeedreamPrompt(sourcePrompt: string, purpose: 'initial' | 'multiview' = 'initial') {
    const original = sourcePrompt.trim()
    if (!original || !options.selectedProduct.value) return original
    // Ice-cream generation has a deterministic 2.5D carrier template. Do not
    // spend an optimizer call or feed a second expanded prompt into it.
    if (resolveCreativeProductProfile(currentCreativeEngineInput()).key === 'ice_cream') return original
    const shouldOptimize = purpose === 'initial' && options.mode.value !== 'image' && options.referenceAssetId.value === null
    if (!shouldOptimize) return original
    options.busyMessage.value = '正在整理产品提示词…'
    try {
      const result = await optimizeImagePrompt({
        prompt: original,
        provider: 'ark',
        productCategory: options.productCategory.value || options.productType.value,
        material: options.material.value,
        productSize: options.productSize.value,
      })
      const candidate = String(result?.prompt || '').trim()
      const optimized = candidate ? `${candidate}\nCore user requirements that must remain: ${original}`.slice(0, 1800) : original
      await options.saveEventBestEffort('summary', 'prompt_optimized', {
        purpose,
        productType: options.productType.value,
        material: options.material.value,
        productSize: options.productSize.value,
        sourcePrompt: original,
        optimizedPrompt: optimized,
        optimizer: 'siliconflow_qwen',
        imageProvider: 'volcengine_ark_seedream_5',
      })
      return optimized
    } catch {
      await options.saveEventBestEffort('summary', 'prompt_optimization_fallback', {
        purpose,
        productType: options.productType.value,
        material: options.material.value,
        productSize: options.productSize.value,
        sourcePrompt: original,
        reason: 'optimization_unavailable',
        imageProvider: 'volcengine_ark_seedream_5',
      })
      return original
    }
  }

  function generationFailureMessage(error: any) {
    const raw = String(error?.message || error?.errMsg || '').trim()
    if (/timeout|timed out|超时/i.test(raw)) return '生成请求等待超时。之间大模型生成通常需要 1-3 分钟，请检查网络后重新提交；本次失败不会扣除未成功生成的积分。'
    if (/登录已过期|请先登录|401/i.test(raw)) return '登录状态已失效，请重新登录后再生成。'
    if (/安全体验模式|SetLimitExceeded|模型.*暂停/i.test(raw)) return '方舟模型的安全体验额度已用尽，服务已暂停。请联系平台管理员在火山方舟控制台提高额度或关闭安全体验模式后重试。'
    if (/ark api key|火山方舟|服务尚未配置|未配置/i.test(raw)) return 'AI 生图服务没有完成配置。请检查服务器上的 VOLCENGINE_ARK_API_KEY 和模型开通状态，配置后重启 smart-pig 服务。'
    if (/网络|network|fail|connect|refused|域名/i.test(raw)) return '无法连接 AI 生图服务。请检查微信公众平台 request 合法域名、网络连接和服务器运行状态。'
    return raw || '生成服务暂时不可用，请稍后重试。'
  }

  async function generateProductImage() {
    if (!options.requireSession()) return
    if (options.busy.value) return
    if (!options.selectedProduct.value || !options.material.value || !options.productSize.value) {
      throw new Error('请先完成产品、材质和成品尺寸的确认')
    }
    if (!await options.ensureAiPolicy()) return
    options.busy.value = true
    options.busyMessage.value = '正在保存创作参数…'
    let queueEventPromise: Promise<void> | null = null
    try {
      const product = options.selectedProduct.value
      await options.saveEventBestEffort('summary', 'generation_started', {
        productType: options.productType.value,
        material: options.material.value,
        productSize: options.productSize.value,
        prompt: options.prompt.value,
        mode: options.mode.value,
        referenceAssetId: options.referenceAssetId.value,
      })
      const hasReferenceAsset = Number(options.referenceAssetId.value) > 0
      const referenceMode = options.mode.value === 'image' || options.mode.value === 'reference' || hasReferenceAsset
      // With no written brief, describe what must be extracted from the
      // reference image instead of falling back to "design a product". This
      // gives Seedream a stable subject slot and keeps all products on the same
      // image-to-image contract, including the fixed ice-cream template.
      const rawPrompt = referenceMode
        ? buildReferenceRawPrompt(options.inspirationText.value)
        : options.inspirationText.value.trim() || options.prompt.value
      const optimizedPrompt = await resolveSeedreamPrompt(referenceMode ? rawPrompt : options.prompt.value)
      let generationPrompt = ''
      let result: any
      const handleQueue = (job: ImageGenerationJobProgress) => {
        updateImageQueueMessage(job)
        const jobId = Number(job.jobId)
        if (Number.isFinite(jobId) && jobId > 0 && pendingImageJobId.value !== jobId) {
          pendingImageJobId.value = jobId
          pendingGenerationPrompt.value = generationPrompt
          queueEventPromise = options.saveEventBestEffort('image', 'image_generation_queued', {
            jobId,
            productType: product?.name,
            material: options.material.value,
            productSize: options.productSize.value,
            referenceAssetId: options.referenceAssetId.value,
            prompt: generationPrompt,
          })
        }
      }
      if (referenceMode) {
        const inputAssetId = Number(options.referenceAssetId.value)
        if (!Number.isFinite(inputAssetId) || inputAssetId <= 0) throw new Error('参考图片还没有保存完成，请重新上传后再生成')
        const request = compileCurrentCreativeImageRequest({ prompt: options.prompt.value, rawPrompt, optimizedPrompt, inputAssetId, purpose: 'reference', refinement: false })
        generationPrompt = request.prompt
        imageGenerationStage.value = 'adapting_product'
        options.busyMessage.value = `正在依据参考图生成${product?.name || '产品'}，预计需要 1-3 分钟…`
        result = await createReferenceToImage({ title: `${product?.name || '产品'} · 对话创作`, ...request, imageSize: DEFAULT_SEEDREAM_IMAGE_SIZE }, handleQueue)
      } else {
        const request = compileCurrentCreativeImageRequest({ prompt: options.prompt.value, rawPrompt, optimizedPrompt, purpose: 'text', refinement: false })
        generationPrompt = request.prompt
        options.busyMessage.value = '正在提交 Seedream 5.0 生图任务…'
        result = await createTextToImage({ title: `${product?.name || '产品'} · 对话创作`, ...request, imageSize: DEFAULT_SEEDREAM_IMAGE_SIZE }, handleQueue)
      }
      if (queueEventPromise) await queueEventPromise
      pendingImageJobId.value = null
      pendingGenerationPrompt.value = ''
      await options.onGenerated(result, generationPrompt)
    } finally {
      options.busy.value = false
      imageGenerationStage.value = ''
      options.busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
    }
  }

  async function resumePendingImageGeneration() {
    const jobId = pendingImageJobId.value
    if (!jobId || options.generatedAssetId.value || options.busy.value) return
    options.busy.value = true
    options.busyMessage.value = '正在恢复上次的图片生成进度…'
    try {
      let job = await getArkImageJob(jobId)
      updateImageQueueMessage(job)
      if (job.status === 'queued' || job.status === 'running') job = await waitForArkImageJob(job, updateImageQueueMessage)
      if (job.status === 'failed') throw new Error(job.errorMessage || job.message || '图片生成失败')
      const resumePrompt = pendingGenerationPrompt.value || options.prompt.value
      pendingImageJobId.value = null
      pendingGenerationPrompt.value = ''
      await options.onGenerated(job, resumePrompt)
    } catch (error) {
      let failedJob: ImageGenerationJobProgress | null = null
      try {
        const latest = await getArkImageJob(jobId)
        if (latest.status === 'failed') failedJob = latest
      } catch {
        // Keep the job attached when only the progress request is unavailable.
      }
      if (failedJob) {
        pendingImageJobId.value = null
        pendingGenerationPrompt.value = ''
        await options.saveEventBestEffort('image', 'image_generation_failed', {
          jobId,
          errorMessage: failedJob.errorMessage || failedJob.message || '图片生成失败',
        })
      }
      throw failedJob || error
    } finally {
      options.busy.value = false
      options.busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
    }
  }

  async function regenerateWithRefinement(note: string) {
    const sourceAssetId = options.generatedAssetId.value
    if (options.busy.value || !sourceAssetId || !note.trim() || !options.selectedProduct.value) return
    options.busy.value = true
    options.busyMessage.value = '正在理解修改要求并生成新方案，请稍候…'
    try {
      let refinementPrompt = note.trim()
      try {
        const optimized = await optimizeImageEditPrompt({ prompt: options.prompt.value, refinementNote: refinementPrompt, productCategory: options.productType.value, material: options.material.value, productSize: options.productSize.value })
        if (String(optimized?.prompt || '').trim()) refinementPrompt = String(optimized.prompt).trim()
      } catch {
        // The direct edit remains usable when optimization is unavailable.
      }
      const request = compileCurrentCreativeImageRequest({ prompt: refinementPrompt, rawPrompt: note, optimizedPrompt: refinementPrompt, inputAssetId: sourceAssetId, purpose: 'refinement', refinement: true, refinementNote: note })
      options.busyMessage.value = '正在基于当前产品图生成新方案，请稍候…'
      await options.saveEventBestEffort('image', 'image_refinement_started', { inputAssetId: sourceAssetId, refinementNote: note, optimizedPrompt: request.prompt, productType: options.productType.value, material: options.material.value, productSize: options.productSize.value })
      const result = await createReferenceToImage({ title: `${options.productType.value || '产品'} · 修改方案`, ...request, imageSize: DEFAULT_SEEDREAM_IMAGE_SIZE }, updateImageQueueMessage)
      if (options.onRefined) await options.onRefined(result, request.prompt, note.trim())
      else await options.onGenerated(result, request.prompt)
    } finally {
      options.busy.value = false
      options.busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
    }
  }

  function clearPendingImage() {
    pendingImageJobId.value = null
    pendingGenerationPrompt.value = ''
    imageGenerationStage.value = ''
  }

  return {
    pendingImageJobId,
    pendingGenerationPrompt,
    imageGenerationStage,
    updateImageQueueMessage,
    resolveSeedreamPrompt,
    compileCurrentCreativeImageRequest,
    generationFailureMessage,
    generateProductImage,
    resumePendingImageGeneration,
    regenerateWithRefinement,
    clearPendingImage,
  }
}
