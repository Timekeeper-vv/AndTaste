import { computed, ref, type Ref } from 'vue'
import {
  createModel as createModelApi,
  getTripoModelTask as getTripoModelTaskApi,
  type SeedreamMultiViewImage,
} from '../api/creative'
import { apiUrl } from '../api/client'
import type { CreativeProductLike } from '../utils/creativeEngine'

type ReadonlyValue<T> = { readonly value: T }

export type ModelInputMode = 'single' | 'multiview'

export interface ModelTask {
  jobId: number
  status: string
  progress: number
  assetId?: number | null
  previewUrl?: string
  errorMessage?: string
}

export interface ModelGenerationRequest extends Record<string, unknown> {
  title: string
  prompt: string
  rawPrompt: string
  mode: 'image_to_model' | 'multiview_to_model'
  inputAssetId: number
  productKey?: string
  productCategory?: string
  material: string
  productSize: string
  materialLabel: string
  materialPrompt: string
  multiviewAssetIds?: Record<string, number>
  exportFormats: 'GLB'
  texture: true
  pbr: true
  textureQuality: 'extreme'
  geometryQuality: 'detailed'
  textureAlignment: 'original_image'
  orientation: 'align_image'
  autoSize: true
  imageAutofix: true
  exportUv: true
  faceLimit: 2000000
}

export interface ModelGenerationContext {
  inputMode: ModelInputMode
  inputAssetId: number
  productType: string
  material: string
  productSize: string
  request: ModelGenerationRequest
}

export interface ModelGenerationOptions {
  selectedProduct: ReadonlyValue<CreativeProductLike | null>
  productKey: ReadonlyValue<string | undefined>
  productType: ReadonlyValue<string | undefined>
  material: ReadonlyValue<string>
  productSize: ReadonlyValue<string>
  prompt: ReadonlyValue<string>
  generatedAssetId: ReadonlyValue<number | null>
  multiviewImages: ReadonlyValue<SeedreamMultiViewImage[]>
  hasCompleteThreeViews: ReadonlyValue<boolean>
  multiviewBundleStatus: ReadonlyValue<string>
  useMultiViewInput: ReadonlyValue<boolean>
  busy: Ref<boolean>
  busyMessage: Ref<string>
  modelInputMode?: Ref<ModelInputMode>
  modelTask?: Ref<ModelTask | null>
  modelRefreshing?: Ref<boolean>
  ensureThreeDimensionalPolicy: () => Promise<boolean>
  saveEvent: (step: string, eventType: string, payload: Record<string, any>) => Promise<void>
  createModel?: typeof createModelApi
  getModelTask?: typeof getTripoModelTaskApi
  resolvePreviewUrl?: (payload: any) => string
  showError?: (message: string) => void
  pollIntervalMs?: number
  onSubmitted?: (task: ModelTask, context: ModelGenerationContext) => Promise<void> | void
  onSucceeded?: (task: ModelTask) => Promise<void> | void
  onFailed?: (task: ModelTask) => Promise<void> | void
}

function positiveId(value: unknown) {
  const id = Number(value)
  return Number.isFinite(id) && id > 0 ? id : null
}

function defaultPreviewUrl(payload: any) {
  const raw = String(payload?.previewUrl || payload?.imageUrl || payload?.fileUrl || payload?.url || payload?.accessUrl || '')
  if (/^https?:\/\//i.test(raw)) return raw
  return raw.startsWith('/') ? apiUrl(raw) : ''
}

export function useModelGeneration(options: ModelGenerationOptions) {
  const modelInputMode = options.modelInputMode || ref<ModelInputMode>('single')
  const modelTask = options.modelTask || ref<ModelTask | null>(null)
  const modelRefreshing = options.modelRefreshing || ref(false)
  const createModel = options.createModel || createModelApi
  const getModelTask = options.getModelTask || getTripoModelTaskApi
  const resolvePreviewUrl = options.resolvePreviewUrl || defaultPreviewUrl
  const pollIntervalMs = Math.max(1000, Number(options.pollIntervalMs) || 5000)
  let modelPollTimer: ReturnType<typeof setTimeout> | null = null
  let modelPollVersion = 0

  const normalizedModelProgress = computed(() => Math.max(0, Math.min(100, Number(modelTask.value?.progress) || 0)))
  const isModelTaskSucceeded = computed(() => modelTask.value?.status === 'succeeded')
  const isModelTaskFailed = computed(() => modelTask.value?.status === 'failed')
  const isModelTaskTerminal = computed(() => isModelTaskSucceeded.value || isModelTaskFailed.value)
  const modelTaskTitle = computed(() => isModelTaskSucceeded.value
    ? '3D 模型已经生成'
    : isModelTaskFailed.value
      ? '3D 建模未完成'
      : '3D 建模正在生成')
  const modelInputLabel = computed(() => modelInputMode.value === 'multiview' ? '三视图建模' : '单图建模')
  const modelTaskDescription = computed(() => isModelTaskSucceeded.value
    ? `${modelInputLabel.value}的 3D 原型已保存到作品库`
    : isModelTaskFailed.value
      ? `本次${modelInputLabel.value}失败，可回到产品图重新提交`
      : `正在进行${modelInputLabel.value}`)
  const modelTaskDetail = computed(() => isModelTaskSucceeded.value
    ? '可以在作品库查看模型、评审并申请打样。'
    : isModelTaskFailed.value
      ? '失败原因已保留。检查产品图或三视图后可以再次发起建模。'
      : '本页面会自动刷新进度，离开后也会继续在作品库保存。')

  function showError(message: string) {
    if (options.showError) options.showError(message)
    else uni.showToast({ title: message, icon: 'none' })
  }

  function setModelTask(payload: any) {
    const jobId = positiveId(payload?.jobId || payload?.modelJobId)
    if (!jobId) return null
    if (payload?.inputMode === 'multiview' || payload?.multiview === true) modelInputMode.value = 'multiview'
    else if (payload?.inputMode === 'single' || payload?.multiview === false) modelInputMode.value = 'single'
    modelTask.value = {
      jobId,
      status: String(payload?.status || 'running').toLowerCase(),
      progress: Number(payload?.progress) || 0,
      assetId: positiveId(payload?.assetId || payload?.modelAssetId),
      previewUrl: resolvePreviewUrl(payload),
      errorMessage: String(payload?.errorMessage || payload?.error || ''),
    }
    return modelTask.value
  }

  function stopModelPolling() {
    modelPollVersion += 1
    if (modelPollTimer) clearTimeout(modelPollTimer)
    modelPollTimer = null
  }

  async function refreshModelTask() {
    if (!modelTask.value || modelRefreshing.value) return modelTask.value
    modelRefreshing.value = true
    try {
      const previousStatus = modelTask.value.status
      const result = await getModelTask(modelTask.value.jobId)
      const task = setModelTask(result)
      if (!task) return modelTask.value
      if (task.status === 'succeeded' && previousStatus !== 'succeeded') {
        await options.saveEvent('model', 'model_completed', {
          modelJobId: task.jobId,
          assetId: task.assetId,
          status: 'succeeded',
          progress: 100,
          previewUrl: task.previewUrl,
        })
        await options.onSucceeded?.(task)
      } else if (task.status === 'failed' && previousStatus !== 'failed') {
        await options.saveEvent('model', 'model_failed', {
          modelJobId: task.jobId,
          status: 'failed',
          progress: task.progress,
          errorMessage: task.errorMessage,
        })
        await options.onFailed?.(task)
      }
      return task
    } catch (error: any) {
      if (modelTask.value && !isModelTaskTerminal.value) {
        modelTask.value.errorMessage = error?.message || '暂时无法读取建模进度，系统会自动重试'
      }
      return modelTask.value
    } finally {
      modelRefreshing.value = false
    }
  }

  async function scheduleModelPolling(immediate = false) {
    stopModelPolling()
    const version = modelPollVersion
    const poll = async () => {
      if (version !== modelPollVersion || !modelTask.value || isModelTaskTerminal.value) return
      await refreshModelTask()
      if (version !== modelPollVersion || !modelTask.value || isModelTaskTerminal.value) return
      modelPollTimer = setTimeout(poll, pollIntervalMs)
    }
    if (immediate) await poll()
    else modelPollTimer = setTimeout(poll, pollIntervalMs)
  }

  async function resumeModelTask(payload?: any) {
    if (payload) setModelTask(payload)
    if (!modelTask.value || isModelTaskTerminal.value) return modelTask.value
    await scheduleModelPolling(true)
    return modelTask.value
  }

  function buildModelRequest(useMultiview = options.useMultiViewInput.value): ModelGenerationRequest {
    const inputAssetId = positiveId(options.generatedAssetId.value)
    if (!inputAssetId) throw new Error('当前产品图未保存成功，请先重新生成产品图')
    const productType = options.productType.value
      || options.selectedProduct.value?.name
      || options.selectedProduct.value?.label
      || '文创产品'
    return {
      title: `${productType} · ${useMultiview ? '三视图' : '单图'} 3D 原型`,
      prompt: options.prompt.value,
      rawPrompt: options.prompt.value,
      mode: useMultiview ? 'multiview_to_model' : 'image_to_model',
      inputAssetId,
      productKey: options.productKey.value || options.selectedProduct.value?.key,
      productCategory: productType,
      material: options.material.value,
      productSize: options.productSize.value,
      materialLabel: options.material.value,
      materialPrompt: `manufacturing material: ${options.material.value}`,
      multiviewAssetIds: useMultiview
        ? Object.fromEntries(options.multiviewImages.value.map(item => [item.view, Number(item.assetId)]))
        : undefined,
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
    }
  }

  async function generateModel() {
    if (options.busy.value) return
    const inputAssetId = positiveId(options.generatedAssetId.value)
    if (!inputAssetId) {
      showError('当前产品图未保存成功，请先重新生成产品图')
      return
    }
    if (!await options.ensureThreeDimensionalPolicy()) return

    options.busy.value = true
    options.busyMessage.value = '正在提交 3D 建模任务，请稍候…'
    try {
      const useMultiview = options.useMultiViewInput.value
      if (useMultiview && !options.hasCompleteThreeViews.value) {
        throw new Error('请先生成完整的正面、侧面和背面，再提交多视图建模')
      }
      if (useMultiview && options.multiviewBundleStatus.value !== 'approved') {
        throw new Error('三视图作品包需先通过人工审核，再继续建模或申请打样')
      }
      modelInputMode.value = useMultiview ? 'multiview' : 'single'
      options.busyMessage.value = useMultiview
        ? '正在提交三视图 3D 建模任务，请稍候…'
        : '正在提交单图 3D 建模任务，请稍候…'
      const request = buildModelRequest(useMultiview)
      const context: ModelGenerationContext = {
        inputMode: modelInputMode.value,
        inputAssetId,
        productType: options.productType.value || options.selectedProduct.value?.name || '文创产品',
        material: options.material.value,
        productSize: options.productSize.value,
        request,
      }
      await options.saveEvent('model', 'model_started', {
        inputAssetId,
        multiview: useMultiview,
        inputMode: modelInputMode.value,
        productType: context.productType,
        material: context.material,
        productSize: context.productSize,
      })
      const result = await createModel(request)
      const jobId = positiveId(result?.jobId)
      if (!jobId) throw new Error('3D 服务没有返回任务编号，请稍后重试')
      const task = setModelTask({
        jobId,
        status: result?.status,
        progress: result?.progress,
        assetId: result?.assetId,
      })
      if (!task) throw new Error('3D 服务没有返回有效任务，请稍后重试')
      await options.saveEvent('model', 'model_submitted', {
        inputAssetId,
        multiview: useMultiview,
        inputMode: modelInputMode.value,
        modelJobId: jobId,
        modelAssetId: result?.assetId,
        status: task.status || 'running',
        progress: task.progress || 0,
        productType: context.productType,
        material: context.material,
        productSize: context.productSize,
      })
      await options.onSubmitted?.(task, context)
      void scheduleModelPolling(true)
      return task
    } catch (error: any) {
      showError(error?.message || '3D 任务提交失败')
      return null
    } finally {
      options.busy.value = false
      options.busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
    }
  }

  function clearModelGeneration() {
    stopModelPolling()
    modelTask.value = null
    modelInputMode.value = 'single'
    modelRefreshing.value = false
  }

  return {
    modelInputMode,
    modelTask,
    modelRefreshing,
    normalizedModelProgress,
    isModelTaskSucceeded,
    isModelTaskFailed,
    isModelTaskTerminal,
    modelTaskTitle,
    modelInputLabel,
    modelTaskDescription,
    modelTaskDetail,
    setModelTask,
    stopModelPolling,
    refreshModelTask,
    scheduleModelPolling,
    resumeModelTask,
    buildModelRequest,
    generateModel,
    clearModelGeneration,
  }
}
