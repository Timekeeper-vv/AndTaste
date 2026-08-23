import { ref, type Ref } from 'vue'
import {
  DEFAULT_SEEDREAM_IMAGE_SIZE,
  createReferenceToImage,
  createTextToImage,
  getArkImageJob,
  isAuthenticationError,
  waitForArkImageJob,
  type ImageGenerationJobProgress,
} from '../api/client'
import { optimizeImageEditPrompt, optimizeImagePrompt } from '../api/creative'
import {
  buildReferenceRawPrompt,
  compileCreativeImageRequest,
  resolveCreativeProductProfile,
  type CreativeEngineInput,
  type CreativeImageRequest,
  type CreativeProductLike,
} from '../utils/creativeEngineRuntime'

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
  projectId?: ReadonlyValue<number | null>
  versionId?: ReadonlyValue<number | null>
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

/**
 * The WeChat developer tool may evaluate a page while its watched output is
 * between two rebuilds. Keep the generation path usable if that transient
 * module record is empty; the next clean build still uses the full shared
 * compiler from `creativeEngineRuntime.ts`.
 */
function fallbackProductForm(input: CreativeEngineInput) {
  const context = [
    input.product?.key,
    input.product?.name,
    input.product?.label,
    input.productKey,
    input.productCategory,
    input.productType,
  ].filter(Boolean).join(' ')
  if (/冰淇淋|冰激凌|ice\s*cream|gelato/i.test(context)) return 'ice_cream'
  if (/毛绒|布偶|plush|stuffed|soft\s*toy/i.test(context)) return 'plush'
  if (/抱枕|靠垫|cushion|pillow/i.test(context)) return 'cushion'
  if (/钥匙扣|key\s*chain|keychain/i.test(context)) return 'keychain'
  if (/冰箱贴|磁贴|magnet/i.test(context)) return 'magnet'
  if (/明信片|书签|贴纸|笔记本|notebook|postcard|bookmark|sticker/i.test(context)) return 'paper_stationery'
  return 'general'
}

function fallbackCompiledRequest(input: CreativeEngineInput): CreativeImageRequest {
  const purpose = input.purpose || (input.refinement ? 'refinement' : Number(input.inputAssetId) > 0 ? 'reference' : 'text')
  const productType = String(input.product?.name || input.product?.label || input.productType || input.productCategory || '文创产品').trim().slice(0, 160)
  const material = String(input.material || '适合该产品的制造材质').trim().slice(0, 160)
  const productSize = String(input.productSize || '按产品实际规格').trim().slice(0, 120)
  const source = String(input.optimizedPrompt || input.prompt || input.rawPrompt || `为${productType}设计一套适合量产打样的产品视觉`).trim().slice(0, 6000)
  const reference = purpose === 'reference' || purpose === 'refinement' || Number(input.inputAssetId) > 0
  const form = fallbackProductForm(input)
  const shape = form === 'ice_cream'
    ? '标准化 2.5D 浮雕冰淇淋冰棒，单个完整扁平轮廓，正面浅浮雕，底部有 100-120mm 天然实木棒。'
    : form === 'plush'
      ? '完整立体填充毛绒玩具，布料裁片、柔软填充体积、缝线和刺绣细节清晰可见。'
      : form === 'cushion'
        ? '完整可拥抱的异形抱枕，具有柔软填充体积、布料裁片、包边和缝线。'
        : '完整、可识别、可量产的真实实体文创产品，具有明确轮廓、合理厚度、圆角和实际材质结构。'
  const prompt = [
    '【角色】你是专业产品设计师 + AI 图像工程师，正在为电商平台制作真实、可量产、可打样的文创产品主图。',
    `【任务】将${reference ? '上传参考图中的主体和核心视觉元素' : '用户提供的灵感'}完全重构为一件真实的「${productType}」成品；不是原图复刻、滤镜或简单贴图。`,
    `【产品形态】${shape}`,
    `【制造参数】材质为「${material}」；成品规格为「${productSize}」。`,
    '【构图规则】单个完整产品居中，占画面约 75%，背景纯白或浅灰，边缘清晰，禁止手机截图、海报、平面标签稿和无关场景。',
    reference ? '【参考图转化原则】参考图只提供主体、轮廓、颜色、纹样和文化识别点；必须改变原始载体和场景，把元素重构到目标产品上，不得原图不变。' : '',
    `【用户方向】${source}`,
  ].filter(Boolean).join('\n')
  const numericAssetId = Number(input.inputAssetId)
  return {
    prompt,
    rawPrompt: String(input.rawPrompt || input.prompt || '').trim().slice(0, 6000),
    negativePrompt: 'phone screenshot, smartphone, mobile screen, app interface, status bar, unchanged reference image, near duplicate, flat poster, label-only artwork, tiny isolated motif, cropped product, incomplete product, unrelated object, external watermark',
    productKey: String(input.product?.key || input.productKey || '').trim().slice(0, 120),
    productCategory: String(input.product?.categoryName || input.productCategory || input.product?.categoryKey || '文创产品').trim().slice(0, 160),
    productType,
    material,
    productSize,
    inputAssetId: Number.isFinite(numericAssetId) && numericAssetId > 0 ? numericAssetId : null,
    refinement: input.refinement === true || purpose === 'refinement',
    refinementNote: String(input.refinementNote || '').trim().slice(0, 2400),
    seed: input.seed ?? null,
    productForm: form,
    creativeEngineVersion: 'miniapp-creative-engine-runtime-fallback-v2',
  }
}

function compileWithRuntimeFallback(input: CreativeEngineInput): CreativeImageRequest {
  try {
    if (typeof compileCreativeImageRequest === 'function') return compileCreativeImageRequest(input)
  } catch (error) {
    console.warn('[image-generation] creative engine compiler unavailable; using local fallback', error)
  }
  return fallbackCompiledRequest(input)
}

function productFormWithRuntimeFallback(input: CreativeEngineInput) {
  try {
    if (typeof resolveCreativeProductProfile === 'function') return resolveCreativeProductProfile(input).key
  } catch (error) {
    console.warn('[image-generation] creative engine profile unavailable; using local fallback', error)
  }
  return fallbackProductForm(input)
}

function buildReferencePromptWithRuntimeFallback(supplement?: string) {
  try {
    if (typeof buildReferenceRawPrompt === 'function') return buildReferenceRawPrompt(supplement)
  } catch (error) {
    console.warn('[image-generation] creative engine reference builder unavailable; using local fallback', error)
  }
  const detail = String(supplement || '').trim()
  return detail ? `上传参考图中的主体、轮廓、颜色和文化识别元素。用户补充方向：${detail}` : '上传参考图中的主体、轮廓、颜色和文化识别元素。'
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
    return compileWithRuntimeFallback(currentCreativeEngineInput(overrides))
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
    if (productFormWithRuntimeFallback(currentCreativeEngineInput()) === 'ice_cream') return original
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
    } catch (error) {
      if (isAuthenticationError(error)) throw error
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
    if (/cannot (?:read (?:property|properties)|access).*resolveCreativeProductProfile.*(?:undefined|null)/i.test(raw)) return '小程序创作组件加载异常，请重新进入创作页后再试。若仍失败，请把开发者工具控制台中的 image-generation 日志发给管理员。'
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
    let failureStage = 'saving_generation_brief'
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
        ? buildReferencePromptWithRuntimeFallback(options.inspirationText.value)
        : options.inspirationText.value.trim() || options.prompt.value
      const optimizedPrompt = await resolveSeedreamPrompt(referenceMode ? rawPrompt : options.prompt.value)
      failureStage = 'compiling_product_prompt'
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
        request.projectId = options.projectId?.value || undefined
        request.versionId = options.versionId?.value || undefined
        generationPrompt = request.prompt
        failureStage = 'submitting_reference_generation'
        imageGenerationStage.value = 'adapting_product'
        options.busyMessage.value = `正在依据参考图生成${product?.name || '产品'}，预计需要 1-3 分钟…`
        result = await createReferenceToImage({ title: `${product?.name || '产品'} · 对话创作`, ...request, imageSize: DEFAULT_SEEDREAM_IMAGE_SIZE }, handleQueue)
      } else {
        const request = compileCurrentCreativeImageRequest({ prompt: options.prompt.value, rawPrompt, optimizedPrompt, purpose: 'text', refinement: false })
        request.projectId = options.projectId?.value || undefined
        request.versionId = options.versionId?.value || undefined
        generationPrompt = request.prompt
        failureStage = 'submitting_text_generation'
        options.busyMessage.value = '正在提交 Seedream 5.0 生图任务…'
        result = await createTextToImage({ title: `${product?.name || '产品'} · 对话创作`, ...request, imageSize: DEFAULT_SEEDREAM_IMAGE_SIZE }, handleQueue)
      }
      if (queueEventPromise) await queueEventPromise
      pendingImageJobId.value = null
      pendingGenerationPrompt.value = ''
      failureStage = 'saving_generated_result'
      await options.onGenerated(result, generationPrompt)
    } catch (error: any) {
      console.error('[image-generation] failed', {
        stage: failureStage,
        name: String(error?.name || ''),
        message: String(error?.message || error?.errMsg || error || ''),
        stack: String(error?.stack || ''),
        statusCode: Number(error?.statusCode || error?.status || 0),
        code: String(error?.code || ''),
      })
      throw error
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
      if (isAuthenticationError(error)) throw error
      let failedJob: ImageGenerationJobProgress | null = null
      try {
        const latest = await getArkImageJob(jobId)
        if (latest.status === 'failed') failedJob = latest
      } catch (latestError) {
        if (isAuthenticationError(latestError)) throw latestError
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
      } catch (error) {
        if (isAuthenticationError(error)) throw error
        // The direct edit remains usable when optimization is unavailable.
      }
      const request = compileCurrentCreativeImageRequest({ prompt: refinementPrompt, rawPrompt: note, optimizedPrompt: refinementPrompt, inputAssetId: sourceAssetId, purpose: 'refinement', refinement: true, refinementNote: note })
      request.projectId = options.projectId?.value || undefined
      request.versionId = options.versionId?.value || undefined
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
