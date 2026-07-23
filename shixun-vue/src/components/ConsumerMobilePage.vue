<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { User } from '../types'
import andTasteLogo from '../assets/and_taste.png'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error']; logout: [] }>()

type Tab = 'image' | 'model' | 'gallery'
type Phase = 'idle' | 'optimize' | 'generate' | 'save' | 'done'

const tab = ref<Tab>('image')
const busy = ref(false)
const stage = ref('')
const phase = ref<Phase>('idle')
type CreationPurpose = '' | 'personal' | 'museum_sale'
const purposeStorageKey = `consumerCreationPurpose:${props.currentUser.id}`
const creationPurpose = ref<CreationPurpose>((localStorage.getItem(purposeStorageKey) as CreationPurpose) || '')
const purposeOptions = [
  { value: 'personal' as const, title: '个人收藏 / 送礼', desc: '用于自己收藏、赠送亲友，不进入售卖渠道。', tag: '不可售卖' },
  { value: 'museum_sale' as const, title: '博物馆售卖', desc: '面向博物馆文创店、展陈空间或渠道售卖。', tag: '可提交生产' },
]
const imageConfig = ref<any>({})
const tripoConfig = ref<any>({})
const creditAccount = ref<any>(null)
const creditRules = ref<any>({})
const assets = ref<any[]>([])
const imageResult = ref<any>(null)
const modelResult = ref<any>(null)
const imagePreviewUrl = ref('')
const uploadPreviewUrl = ref('')
const modelProgress = ref(0)
const modelTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const imageAnchor = ref<HTMLElement | null>(null)
const modelAnchor = ref<HTMLElement | null>(null)
const previewAsset = ref<any | null>(null)
const previewReady = ref(false)
const previewLoadFailed = ref(false)
const modelViewerLoaded = ref(false)
const previewDownloadFormat = ref<'GLB' | 'OBJ' | 'STL'>('GLB')
const previewDownloading = ref(false)
const creditPanelOpen = ref(false)
const rechargePackages = [
  { points: 100, label: '体验包', desc: '适合少量图片生成和一次3D尝试' },
  { points: 500, label: '创作包', desc: '适合连续做系列文创方案' },
  { points: 1000, label: '生产预备包', desc: '适合博物馆售卖方向的批量创作' },
]
const submittedAssetIds = ref<Set<number>>(new Set())
const submittingAssetIds = ref<Set<number>>(new Set())
const productionRequests = ref<any[]>([])
const museums = ref<any[]>([])
const productionModal = ref<any | null>(null)
const submittingProduction = ref(false)
const productionForm = reactive({
  requestType: 'sample' as 'sample' | 'bulk',
  quantity: 1,
  selfShipQuantity: 1,
  recipientName: '',
  recipientPhone: '',
  recipientAddress: '',
  note: '',
  museumDistribution: [] as Array<{ museumId: string; museumName: string; quantity: number }>,
})
const CONSUMER_TRIPO_MODEL_VERSION = 'v3.1-20260211'

const imageForm = reactive({
  rawPrompt: '一款适合年轻游客的城市味道文创礼盒，温暖、精致、有官方文创质感',
  prompt: '',
  usageGuide: '',
  style: '官方文创',
  imagenAspectRatio: '1:1',
  imagenImageSize: '1K',
  imagenOutputFormat: 'png',
})

const modelForm = reactive({
  mode: 'image_to_model' as 'image_to_model' | 'text_to_model',
  rawPrompt: '山城街巷主题亚克力钥匙扣，立体浮雕层次，适合文创打样',
  prompt: '',
  inputAssetId: null as number | null,
})

const recentImages = computed(() => assets.value.filter(x => x.assetType === 'image').slice(0, 8))
const recentModels = computed(() => assets.value.filter(x => x.assetType === 'model').slice(0, 8))
const recentProductionRequests = computed(() => productionRequests.value.slice(0, 8))
const canGenerateModel = computed(() => modelForm.mode === 'image_to_model' ? !!modelForm.inputAssetId : !!modelForm.rawPrompt.trim())
const previewModelUrl = computed(() => previewAsset.value?.id ? `/api/creative/ai/assets/${previewAsset.value.id}/model-content` : previewAsset.value?.fileUrl || previewAsset.value?.modelUrl || '')
const previewDownloadUrl = computed(() => previewAsset.value?.id ? `/api/creative/ai/assets/${previewAsset.value.id}/download-model?format=${previewDownloadFormat.value}&currentUserId=${props.currentUser.id}` : previewAsset.value?.fileUrl || previewAsset.value?.modelUrl || previewModelUrl.value)

const creditBalance = computed(() => Number(creditAccount.value?.balance ?? 0))
const imageCost = computed(() => Number(creditRules.value?.image2d ?? 1))
const modelCost = computed(() => modelForm.mode === 'image_to_model' ? Number(creditRules.value?.imageTo3d ?? 10) : Number(creditRules.value?.textTo3d ?? 8))
const convertCost = computed(() => Number(creditRules.value?.modelConvert ?? 1))
const selectedPurposeLabel = computed(() => purposeOptions.find(x => x.value === creationPurpose.value)?.title || '')
const selectedPurposeFullText = computed(() => creationPurpose.value === 'personal' ? '个人收藏/送礼（不可售卖）' : creationPurpose.value === 'museum_sale' ? '博物馆售卖' : '未选择')

function selectCreationPurpose(value: 'personal' | 'museum_sale') {
  creationPurpose.value = value
  localStorage.setItem(purposeStorageKey, value)
  emit('alert', `已选择创作目的：${selectedPurposeFullText.value}`, 'success')
}

function changeCreationPurpose() {
  creationPurpose.value = ''
  localStorage.removeItem(purposeStorageKey)
}

function openCreditPanel() { creditPanelOpen.value = true }
function closeCreditPanel() { creditPanelOpen.value = false }
function contactAdminForRecharge(points?: number) {
  const text = points ? `我想给账号 ${props.currentUser.username} 充值 ${points} 点额度` : `我想给账号 ${props.currentUser.username} 充值额度`
  navigator.clipboard?.writeText(text).catch(() => {})
  emit('alert', '充值申请文案已复制，请联系平台管理员充值', 'success')
}

async function downloadPreviewModel() {
  const url = previewDownloadUrl.value
  const format = previewDownloadFormat.value
  const id = previewAsset.value?.id || Date.now()
  if (!url) {
    emit('alert', '模型文件暂不可下载', 'error')
    return
  }
  previewDownloading.value = true
  emit('alert', format === 'GLB' ? '正在准备模型文件…' : `正在转换为 ${format} 格式，首次可能需要1-2分钟`, 'success')
  try {
    const response = await fetch(url, {
      cache: 'no-store',
      headers: {
        'X-Current-Role': 'user',
        'X-Current-User-Id': String(props.currentUser.id),
        'X-Current-User': props.currentUser.username,
      },
    })
    if (!response.ok) {
      let message = ''
      try {
        const contentType = response.headers.get('content-type') || ''
        message = contentType.includes('application/json') ? (await response.json()).message : await response.text()
      } catch {}
      throw new Error(message || `HTTP ${response.status}`)
    }
    const blob = await response.blob()
    const disposition = response.headers.get('content-disposition') || ''
    const matched = /filename\*=UTF-8''([^;]+)|filename=\"?([^\";]+)\"?/i.exec(disposition)
    const filename = matched ? decodeURIComponent(matched[1] || matched[2]) : `and-taste-3d-${id}-${format.toLowerCase()}.${format.toLowerCase()}`
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1500)
    emit('alert', `已开始下载 ${format} 模型`, 'success')
    await load()
  } catch (error: any) {
    emit('alert', `下载失败：${error?.message || error}`, 'error')
  } finally {
    previewDownloading.value = false
  }
}

function displayAssetTitle(a: any): string {
  const title = String(a?.title || '')
  if (title.includes('参考图')) return '参考图'
  return a?.assetType === 'model' ? '3D作品' : '产品图作品'
}

function modelPreviewImage(a: any): string {
  return a?.previewUrl || ''
}

const workStatusText: Record<string, string> = { review: '待审核', approved: '已通过', rejected: '未通过', draft: '草稿' }
function workStatusLabel(a: any): string { return workStatusText[String(a?.status || 'draft')] || String(a?.status || '草稿') }
function workStatusClass(a: any): string { const s = String(a?.status || 'draft'); return s === 'approved' ? 'approved' : s === 'rejected' ? 'rejected' : s === 'review' ? 'review' : 'draft' }
function assetIdOf(a: any): number { return Number(a?.id || a?.assetId || 0) }
function isSubmittedForReview(a: any): boolean { const id = assetIdOf(a); return !!id && submittedAssetIds.value.has(id) }
function isSubmittingForReview(a: any): boolean { const id = assetIdOf(a); return !!id && submittingAssetIds.value.has(id) }
function isApprovedModel(a: any): boolean { return a?.assetType === 'model' && String(a?.assetStatus || a?.status || '') === 'approved' }
function canSubmitProduction(a: any): boolean { return isApprovedModel(a) }
function requestTypeText(v?: string) { return v === 'bulk' ? '批量生产' : '打样' }
function productionStatusText(v?: string) { const map: Record<string,string> = { review:'待审核', approved:'已通过', rejected:'未通过' }; return map[String(v || 'review')] || String(v || '-') }
function productionStatusClass(v?: string) { const st=String(v || 'review'); return st === 'approved' ? 'approved' : st === 'rejected' ? 'rejected' : 'review' }
function isMuseumSalePurpose() { return creationPurpose.value === 'museum_sale' }

function ensureSingleMuseumSelection() {
  if (!isMuseumSalePurpose()) return
  if (!productionForm.museumDistribution.length) {
    const firstMuseum = museums.value[0]
    if (firstMuseum) {
      productionForm.museumDistribution = [{ museumId: firstMuseum.id, museumName: firstMuseum.name, quantity: Number(productionForm.quantity || 0) }]
    }
  }
}

function currentMuseumDistribution() {
  if (!isMuseumSalePurpose()) return []
  ensureSingleMuseumSelection()
  const row = productionForm.museumDistribution[0]
  if (!row?.museumId) return []
  const known = museums.value.find(m => m.id === row.museumId)
  return [{
    museumId: row.museumId,
    museumName: known?.name || row.museumName || '博物馆',
    quantity: Number(productionForm.quantity || 0),
  }]
}

function canSubmitReview(a: any): boolean {
  const id = assetIdOf(a)
  const st = String(a?.assetStatus || a?.status || 'draft')
  return !!id && !isSubmittedForReview(a) && !isSubmittingForReview(a) && a?.sourceType !== 'upload' && st !== 'review' && st !== 'approved'
}

async function submitAssetForReview(a: any) {
  const id = assetIdOf(a)
  if (!id) {
    emit('alert', '作品ID不存在，无法提交博物馆审批', 'error')
    return
  }
  if (!props.currentUser?.id) {
    emit('alert', '当前登录信息缺少用户ID，请退出后重新登录 user 账号再提交', 'error')
    return
  }
  submittingAssetIds.value = new Set([...submittingAssetIds.value, id])
  try {
    const r = await fetch(`/api/creative/ai/consumer-assets/${id}/submit-review?currentUserId=${props.currentUser.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'X-Current-Role': 'user',
        'X-Current-User-Id': String(props.currentUser.id),
        'X-Current-User': props.currentUser.username,
      },
      body: JSON.stringify({
        note: `C端用户主动提交博物馆审批；创作目的：${selectedPurposeFullText.value}`,
        currentUserId: String(props.currentUser.id),
        currentUsername: props.currentUser.username,
      }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    submittedAssetIds.value = new Set([...submittedAssetIds.value, id])
    if (a) a.status = 'review'
    await load()
    emit('alert', '已提交博物馆审批，请等待审核结果', 'success')
  } catch (e: any) {
    emit('alert', '提交博物馆审批失败：' + (e?.message || e), 'error')
  } finally {
    const next = new Set(submittingAssetIds.value)
    next.delete(id)
    submittingAssetIds.value = next
  }
}

function setStage(text: string, nextPhase: Phase) {
  stage.value = text
  phase.value = nextPhase
}

onMounted(load)
onBeforeUnmount(() => {
  if (modelTimer.value) clearTimeout(modelTimer.value)
  if (imagePreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(imagePreviewUrl.value)
  if (uploadPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(uploadPreviewUrl.value)
  document.body.style.overflow = ''
})

async function json(url: string) {
  const r = await fetch(url, { cache: 'no-store' })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return await r.json()
}

async function load() {
  try {
    const assetParams = new URLSearchParams({ role: 'user', currentUserId: String(props.currentUser.id) })
    const [i, t, a, c, prs, ms] = await Promise.all([
      json('/api/creative/ai/jimeng/config'),
      json('/api/creative/ai/tripo/config'),
      json(`/api/creative/ai/assets?${assetParams}`),
      json(`/api/creative/ai/consumer-credits/account?currentUserId=${props.currentUser.id}`),
      json(`/api/creative/ai/consumer-production/my?currentUserId=${props.currentUser.id}`),
      json('/api/creative/ai/consumer-production/museums'),
    ])
    imageConfig.value = i
    tripoConfig.value = t
    assets.value = Array.isArray(a) ? a : []
    creditAccount.value = c
    creditRules.value = c?.rules || {}
    productionRequests.value = Array.isArray(prs) ? prs : []
    museums.value = Array.isArray(ms) ? ms : []
  } catch (e: any) {
    emit('alert', '加载移动创作页失败：' + (e?.message || e), 'error')
  }
}

async function prepareAssetPreview(assetId: number, target: 'image' | 'upload') {
  const response = await fetch(`/api/creative/ai/assets/${assetId}/content?v=${Date.now()}`, { cache: 'no-store' })
  if (!response.ok) throw new Error(`读取文件失败 HTTP ${response.status}`)
  const blob = await response.blob()
  if (!blob.type.startsWith('image/')) throw new Error(`文件类型异常：${blob.type || 'unknown'}`)
  const url = URL.createObjectURL(blob)
  if (target === 'image') {
    if (imagePreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(imagePreviewUrl.value)
    imagePreviewUrl.value = url
  } else {
    if (uploadPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(uploadPreviewUrl.value)
    uploadPreviewUrl.value = url
  }
}

async function optimizeImagePrompt() {
  const source = [imageForm.style, imageForm.rawPrompt].filter(Boolean).join('，')
  const r = await fetch('/api/creative/ai/prompt/tripo-optimize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt: source, provider: 'jimeng' }),
  })
  if (!r.ok) {
    const err = await r.json().catch(() => null)
    throw new Error(err?.message || `HTTP ${r.status}`)
  }
  const d = await r.json()
  imageForm.prompt = d.prompt || source
  imageForm.usageGuide = d.usageGuide || ''
}

async function generateImage() {
  if (!imageForm.rawPrompt.trim()) {
    emit('alert', '先写一句你想做什么产品', 'error')
    return
  }
  if (!imageConfig.value.configured) {
    emit('alert', '即梦AI签名鉴权未配置，请联系管理员配置火山AccessKeyId和SecretAccessKey', 'error')
    return
  }
  busy.value = true
  imageResult.value = null
  setStage('正在优化创意', 'optimize')
  try {
    await optimizeImagePrompt()
    setStage('正在生成图片', 'generate')
    const r = await fetch('/api/creative/ai/jimeng/text-to-image', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'jimeng',
        rawPrompt: imageForm.rawPrompt,
        prompt: imageForm.prompt,
        imagenAspectRatio: imageForm.imagenAspectRatio,
        imagenImageSize: imageForm.imagenImageSize,
        imagenOutputFormat: imageForm.imagenOutputFormat,
        currentUserId: props.currentUser.id,
      }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const d = await r.json()
    if (d.creditAccount) creditAccount.value = d.creditAccount
    imageResult.value = d
    setStage('正在保存作品', 'save')
    await prepareAssetPreview(d.assetId, 'image')
    await load()
    await nextTick()
    imageAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    phase.value = 'done'
    emit('alert', '图片已保存，可提交博物馆审批', 'success')
  } catch (e: any) {
    phase.value = 'idle'
    emit('alert', '生成图片失败：' + (e?.message || e), 'error')
  } finally {
    busy.value = false
    stage.value = ''
  }
}

async function uploadReference(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  busy.value = true
  setStage('正在上传参考图', 'save')
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('title', 'C端3D参考图')
    fd.append('tags', 'C端,3D参考图')
    fd.append('currentUserId', String(props.currentUser.id))
    const r = await fetch('/api/creative/ai/assets/upload', { method: 'POST', body: fd })
    if (!r.ok) throw new Error(await r.text())
    const d = await r.json()
    modelForm.inputAssetId = d.assetId
    await prepareAssetPreview(d.assetId, 'upload')
    emit('alert', '参考图已上传', 'success')
  } catch (e: any) {
    emit('alert', '上传失败：' + (e?.message || e), 'error')
  } finally {
    busy.value = false
    stage.value = ''
    phase.value = 'idle'
  }
}

async function optimizeModelPrompt() {
  if (modelForm.mode !== 'text_to_model') {
    modelForm.prompt = ''
    return
  }
  if (!modelForm.rawPrompt.trim()) return
  const r = await fetch('/api/creative/ai/prompt/tripo-3d-optimize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt: modelForm.rawPrompt, promptTemplate: 'universal' }),
  })
  if (!r.ok) {
    const err = await r.json().catch(() => null)
    throw new Error(err?.message || `HTTP ${r.status}`)
  }
  const d = await r.json()
  modelForm.prompt = d.prompt || modelForm.rawPrompt
}

async function generateModel() {
  if (!tripoConfig.value.configured || !tripoConfig.value.serviceReachable) {
    emit('alert', '3D生成服务暂不可用，请联系管理员', 'error')
    return
  }
  if (!canGenerateModel.value) {
    emit('alert', modelForm.mode === 'image_to_model' ? '先上传一张产品参考图' : '先写一句模型描述', 'error')
    return
  }
  busy.value = true
  modelResult.value = null
  modelProgress.value = 0
  setStage(modelForm.mode === 'text_to_model' ? '正在优化创意' : '正在提交图片', modelForm.mode === 'text_to_model' ? 'optimize' : 'generate')
  try {
    if (modelForm.mode === 'text_to_model') await optimizeModelPrompt()
    setStage('正在生成3D模型', 'generate')
    const isImageToModel = modelForm.mode === 'image_to_model'
    const body = {
      mode: modelForm.mode,
      modelVersion: CONSUMER_TRIPO_MODEL_VERSION,
      promptTemplate: isImageToModel ? '' : 'universal',
      rawPrompt: isImageToModel ? '' : modelForm.rawPrompt,
      prompt: isImageToModel ? '' : (modelForm.prompt || modelForm.rawPrompt),
      negativePrompt: isImageToModel ? '' : 'low poly, blurry, flat texture, deformed, asymmetric, noisy mesh',
      inputAssetId: modelForm.inputAssetId,
      multiviewAssetIds: { front: null, left: null, back: null, right: null },
      exportFormats: 'GLB',
      texture: true,
      pbr: true,
      textureQuality: 'extreme',
      geometryQuality: 'detailed',
      textureAlignment: 'original_image',
      orientation: 'align_image',
      autoSize: true,
      imageAutofix: true,
      quad: false,
      smartLowPoly: false,
      generateParts: false,
      exportUv: true,
      compress: false,
      faceLimit: 2000000,
      currentUserId: props.currentUser.id,
    }
    const r = await fetch('/api/creative/ai/tripo/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const d = await r.json()
    if (d.creditAccount) creditAccount.value = d.creditAccount
    emit('alert', '已开始生成3D模型', 'success')
    await pollModel(d.jobId)
  } catch (e: any) {
    busy.value = false
    stage.value = ''
    phase.value = 'idle'
    emit('alert', '3D生成失败：' + (e?.message || e), 'error')
  }
}

async function pollModel(jobId: number) {
  if (modelTimer.value) clearTimeout(modelTimer.value)
  try {
    const r = await fetch(`/api/creative/ai/tripo/tasks/${jobId}`, { cache: 'no-store' })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const d = await r.json()
    modelProgress.value = Number(d.progress || 0)
    setStage(d.status === 'succeeded' ? '3D模型已完成' : `正在生成3D模型 ${modelProgress.value || 0}%`, d.status === 'succeeded' ? 'save' : 'generate')
    if (d.status === 'succeeded') {
      if (d.creditAccount) creditAccount.value = d.creditAccount
      modelResult.value = d
      busy.value = false
      stage.value = ''
      await load()
      await nextTick()
      modelAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      phase.value = 'done'
      emit('alert', '3D模型已保存，可提交博物馆审批', 'success')
      return
    }
    if (d.status === 'failed') throw new Error(d.errorMessage || '3D生成失败')
    modelTimer.value = setTimeout(() => pollModel(jobId), 3000)
  } catch (e: any) {
    busy.value = false
    stage.value = ''
    phase.value = 'idle'
    emit('alert', '查询3D任务失败：' + (e?.message || e), 'error')
  }
}

function openProductionRequest(a: any, type: 'sample' | 'bulk') {
  if (type === 'bulk' && creationPurpose.value !== 'museum_sale') {
    emit('alert', '当前创作目的为个人收藏/送礼（不可售卖），不能提交博物馆售卖/批量生产申请', 'error')
    return
  }
  if (!canSubmitProduction(a)) {
    emit('alert', '作品需先通过审核，并且必须是3D模型，才能提交打样或生产申请', 'error')
    return
  }
  productionModal.value = a
  productionForm.requestType = type
  productionForm.quantity = type === 'sample' ? 1 : 1000
  productionForm.selfShipQuantity = isMuseumSalePurpose() ? 0 : productionForm.quantity
  productionForm.recipientName = props.currentUser.username
  productionForm.recipientPhone = ''
  productionForm.recipientAddress = ''
  productionForm.note = type === 'sample' ? `创作目的：${selectedPurposeFullText.value}。希望先打样确认材质、尺寸和包装效果` : `创作目的：${selectedPurposeFullText.value}。计划按所选用途执行，不做个人/博物馆拆分`
  const firstMuseum = museums.value[0]
  productionForm.museumDistribution = isMuseumSalePurpose() && firstMuseum ? [{ museumId: firstMuseum.id, museumName: firstMuseum.name, quantity: productionForm.quantity }] : []
  document.body.style.overflow = 'hidden'
}

function closeProductionRequest() {
  productionModal.value = null
  document.body.style.overflow = ''
}

function changeMuseum(row: any) {
  const found = museums.value.find(m => m.id === row.museumId)
  if (found) row.museumName = found.name
}

async function submitProductionRequest() {
  if (!productionModal.value?.id) return
  if (productionForm.requestType === 'bulk' && creationPurpose.value !== 'museum_sale') {
    emit('alert', '个人收藏/送礼（不可售卖）用途不能提交批量生产售卖申请', 'error')
    return
  }
  const quantity = Number(productionForm.quantity || 0)
  if (quantity <= 0) {
    emit('alert', '申请数量必须大于0', 'error')
    return
  }
  const museumRows = currentMuseumDistribution()
  if (isMuseumSalePurpose() && !museumRows.length) {
    emit('alert', '请选择一个博物馆，全部数量将进入该博物馆售卖，不支持拆分', 'error')
    return
  }
  submittingProduction.value = true
  try {
    const r = await fetch(`/api/creative/ai/consumer-production/submit?currentUserId=${props.currentUser.id}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Current-Role': 'user', 'X-Current-User-Id': String(props.currentUser.id), 'X-Current-User': props.currentUser.username },
      body: JSON.stringify({
        currentUserId: props.currentUser.id,
        assetId: productionModal.value.id,
        requestType: productionForm.requestType,
        purpose: creationPurpose.value,
        quantity,
        selfShipQuantity: isMuseumSalePurpose() ? 0 : quantity,
        recipientName: productionForm.recipientName,
        recipientPhone: productionForm.recipientPhone,
        recipientAddress: productionForm.recipientAddress,
        note: `创作目的：${selectedPurposeFullText.value}。${productionForm.note || ''}`,
        museumDistribution: museumRows,
      }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const d = await r.json()
    emit('alert', d.message || '申请已提交，请等待审批', 'success')
    closeProductionRequest()
    await load()
    tab.value = 'gallery'
  } catch (e: any) {
    emit('alert', '提交失败：' + (e?.message || e), 'error')
  } finally {
    submittingProduction.value = false
  }
}

function openUrl(url?: string) {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function ensureModelViewer() {
  if (modelViewerLoaded.value) return
  await import('@google/model-viewer')
  modelViewerLoaded.value = true
}

async function openModelPreview(a?: any) {
  const asset = a || modelResult.value
  if (!asset?.id && !asset?.assetId && !asset?.fileUrl && !asset?.modelUrl) {
    emit('alert', '模型文件暂不可预览', 'error')
    return
  }
  previewAsset.value = {
    ...asset,
    id: asset.id || asset.assetId,
    fileUrl: asset.fileUrl || asset.modelUrl,
  }
  previewReady.value = false
  previewLoadFailed.value = false
  previewDownloadFormat.value = 'GLB'
  document.body.style.overflow = 'hidden'
  try {
    await ensureModelViewer()
  } catch (e: any) {
    previewLoadFailed.value = true
    emit('alert', '3D预览组件加载失败，请稍后重试', 'error')
  }
}

function closeModelPreview() {
  previewAsset.value = null
  previewReady.value = false
  previewLoadFailed.value = false
  document.body.style.overflow = ''
}
</script>

<template>
  <main class="consumer-shell immersive-shell">
    <div class="ambient-layer"></div>
    <header class="consumer-top">
      <div class="brand">
        <img :src="andTasteLogo" alt="之间味道" />
        <div>
          <b>之间味道</b>
          <span>文创灵感工坊</span>
        </div>
      </div>
      <button type="button" class="icon-btn" title="退出登录" @click="emit('logout')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/></svg>
      </button>
    </header>

    <section v-if="!creationPurpose" class="purpose-gate">
      <div class="purpose-card">
        <div class="purpose-brand"><img :src="andTasteLogo" alt="之间味道" /><span>开始创作前</span></div>
        <h1>您创作是为了什么？</h1>
        <p>请选择本次创作目的。选定后后续按单一路径执行，不支持个人和博物馆数量拆分。</p>
        <div class="purpose-options">
          <button v-for="item in purposeOptions" :key="item.value" type="button" @click="selectCreationPurpose(item.value)">
            <i>{{ item.tag }}</i>
            <b>{{ item.title }}</b>
            <span>{{ item.desc }}</span>
          </button>
        </div>
      </div>
    </section>

    <section class="hero">
      <span>{{ props.currentUser.username }} · 剩余额度 {{ creditBalance }} 点 · {{ selectedPurposeFullText }}</span>
      <button type="button" class="purpose-change" @click="changeCreationPurpose">切换用途</button>
      <h1>把想法变成文创作品</h1>
      <p>输入一句话，选择图片或3D，系统会自动完成创作并保存。</p>
      <div class="hero-actions">
        <button type="button" @click="tab='image'">生成图片</button>
        <button type="button" @click="tab='model'">生成3D</button>
        <button type="button" class="recharge-hero" @click="openCreditPanel">充值额度</button>
      </div>
    </section>

    <nav class="quick-tabs">
      <button type="button" :class="{active:tab==='image'}" @click="tab='image'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m8 13 2.5-2.5L15 15l1-1 3 3"/><circle cx="8" cy="9" r="1"/></svg>
        图片
      </button>
      <button type="button" :class="{active:tab==='model'}" @click="tab='model'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m12 2 8 4.5v9L12 20l-8-4.5v-9L12 2Z"/><path d="M12 11 4.5 6.8"/><path d="M12 11v9"/><path d="m12 11 7.5-4.2"/></svg>
        3D
      </button>
      <button type="button" :class="{active:tab==='gallery'}" @click="tab='gallery'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
        作品
      </button>
    </nav>

    <section v-if="tab==='image'" class="panel creation-panel">
      <div class="section-head">
        <span>IMAGE</span>
        <b>产品图生成</b>
      </div>
      <label>
        <span>你想做什么</span>
        <textarea v-model="imageForm.rawPrompt" rows="5" placeholder="例如：江西博物馆主题冰箱贴，青铜器纹样，年轻人喜欢，精致伴手礼"></textarea>
      </label>
      <div class="chips">
        <button type="button" :class="{active:imageForm.style==='官方文创'}" @click="imageForm.style='官方文创'">官方文创</button>
        <button type="button" :class="{active:imageForm.style==='国潮精致'}" @click="imageForm.style='国潮精致'">国潮精致</button>
        <button type="button" :class="{active:imageForm.style==='可爱潮玩'}" @click="imageForm.style='可爱潮玩'">可爱潮玩</button>
      </div>
      <div class="chips compact">
        <button type="button" :class="{active:imageForm.imagenAspectRatio==='1:1'}" @click="imageForm.imagenAspectRatio='1:1'">方图</button>
        <button type="button" :class="{active:imageForm.imagenAspectRatio==='9:16'}" @click="imageForm.imagenAspectRatio='9:16'">手机海报</button>
        <button type="button" :class="{active:imageForm.imagenAspectRatio==='16:9'}" @click="imageForm.imagenAspectRatio='16:9'">横版</button>
      </div>
      <button type="button" class="primary" :disabled="busy" @click="generateImage">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2 3 14h9l-1 8 10-12h-9l1-8Z"/></svg>
        {{ busy && tab==='image' ? stage || '正在生成' : `一键生成并保存图片 · ${imageCost}点` }}
      </button>

      <article v-if="imageResult" ref="imageAnchor" class="result-card">
        <img v-if="imagePreviewUrl" :src="imagePreviewUrl" alt="生成图片" />
        <div class="result-info">
          <b>已保存到作品库</b>
          <p v-if="imageForm.usageGuide">{{ imageForm.usageGuide }}</p>
          <div class="result-actions">
            <a v-if="imageResult.imageUrl || imageResult.fileUrl" :href="imageResult.imageUrl || imageResult.fileUrl" target="_blank" rel="noopener">查看原图</a>
            <button v-if="canSubmitReview(imageResult)" type="button" @click.stop="submitAssetForReview(imageResult)">提交博物馆审批</button>
            <span v-else-if="isSubmittingForReview(imageResult)" class="submitted-tip">提交中...</span>
            <span v-else-if="isSubmittedForReview(imageResult) || imageResult.status === 'review'" class="submitted-tip">已提交博物馆审批</span>
          </div>
        </div>
      </article>
    </section>

    <section v-if="tab==='model'" class="panel creation-panel">
      <div class="section-head">
        <span>3D</span>
        <b>轻量3D建模</b>
      </div>
      <div class="mode-switch">
        <button type="button" :class="{active:modelForm.mode==='image_to_model'}" @click="modelForm.mode='image_to_model'">拍照/上传生成</button>
        <button type="button" :class="{active:modelForm.mode==='text_to_model'}" @click="modelForm.mode='text_to_model'">文字生成</button>
      </div>

      <label v-if="modelForm.mode==='image_to_model'" class="upload-box">
        <input type="file" accept="image/*" @change="uploadReference" />
        <img v-if="uploadPreviewUrl" :src="uploadPreviewUrl" alt="3D参考图" />
        <span v-else>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14"/><path d="M5 12h14"/></svg>
          上传产品图
        </span>
      </label>

      <p v-if="modelForm.mode==='image_to_model'" class="simple-note">
        上传清晰产品图后，系统会直接使用 Tripo 3.1 最高质量模型生成 3D，不需要再填写提示词。
      </p>

      <label v-if="modelForm.mode==='text_to_model'">
        <span>模型描述</span>
        <textarea v-model="modelForm.rawPrompt" rows="4" placeholder="例如：山城街巷主题亚克力钥匙扣，边缘圆润，有浮雕层次，适合打样"></textarea>
      </label>

      <button type="button" class="primary green" :disabled="busy || !canGenerateModel" @click="generateModel">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m12 2 8 4.5v9L12 20l-8-4.5v-9L12 2Z"/></svg>
        {{ busy && tab==='model' ? stage || '正在生成' : `一键生成并保存3D · ${modelCost}点` }}
      </button>
      <div v-if="busy && tab==='model'" class="progress">
        <span :style="{ width: `${Math.max(12, modelProgress)}%` }"></span>
      </div>

      <article v-if="modelResult" ref="modelAnchor" class="result-card">
        <img v-if="modelResult.previewUrl" :src="modelResult.previewUrl" alt="3D模型预览" />
        <div class="result-info">
          <b>3D模型已生成</b>
          <div class="result-actions">
            <button type="button" @click="openModelPreview(modelResult)">预览模型</button>
            <button v-if="canSubmitReview(modelResult)" type="button" @click.stop="submitAssetForReview(modelResult)">提交博物馆审批</button>
            <span v-else-if="isSubmittingForReview(modelResult)" class="submitted-tip">提交中...</span>
            <span v-else-if="isSubmittedForReview(modelResult) || modelResult.status === 'review'" class="submitted-tip">已提交博物馆审批</span>
          </div>
        </div>
      </article>
    </section>

    <section v-if="tab==='gallery'" class="panel creation-panel">
      <div class="section-head">
        <span>WORKS</span>
        <b>最近作品</b>
      </div>
      <div class="gallery">
        <article v-for="a in recentImages" :key="`img-${a.id}`">
          <img :src="a.previewUrl || a.fileUrl" alt="作品图片" />
          <span class="work-status" :class="workStatusClass(a)">{{ workStatusLabel(a) }}</span>
          <b>{{ displayAssetTitle(a) }}</b>
          <button v-if="canSubmitReview(a)" type="button" class="review-submit" @click.stop="submitAssetForReview(a)">提交博物馆审批</button>
          <span v-else-if="isSubmittingForReview(a)" class="submitted-tip">提交中...</span>
        </article>
        <article v-for="a in recentModels" :key="`model-${a.id}`">
          <img v-if="modelPreviewImage(a)" :src="modelPreviewImage(a)" alt="3D作品预览" />
          <div v-else class="model-tile">3D</div>
          <span class="work-status" :class="workStatusClass(a)">{{ workStatusLabel(a) }}</span>
          <b>{{ displayAssetTitle(a) }}</b>
          <button type="button" @click.stop="openModelPreview(a)">预览</button>
          <button v-if="canSubmitReview(a)" type="button" class="review-submit" @click.stop="submitAssetForReview(a)">提交博物馆审批</button>
          <span v-else-if="isSubmittingForReview(a)" class="submitted-tip">提交中...</span>
          <div v-if="canSubmitProduction(a)" class="production-actions">
            <button type="button" @click.stop="openProductionRequest(a, 'sample')">申请打样</button>
            <button v-if="creationPurpose === 'museum_sale'" type="button" class="prod" @click.stop="openProductionRequest(a, 'bulk')">批量生产</button>
          </div>
        </article>
      </div>
      <p v-if="!recentImages.length && !recentModels.length" class="empty">暂无作品</p>

      <div v-if="recentProductionRequests.length" class="production-list">
        <h3>我的生产申请</h3>
        <article v-for="r in recentProductionRequests" :key="r.id">
          <div><b>{{ requestTypeText(r.requestType) }} · {{ r.quantity }}个</b><span>{{ r.assetTitle || r.title }}</span></div>
          <em :class="productionStatusClass(r.status)">{{ productionStatusText(r.status) }}</em>
        </article>
      </div>
    </section>

    <Teleport to="body">
      <section v-if="creditPanelOpen" class="credit-modal" @click.self="closeCreditPanel">
        <div class="credit-card">
          <header>
            <div><b>我的额度</b><span>{{ props.currentUser.username }} · 当前余额 {{ creditBalance }} 点</span></div>
            <button type="button" @click="closeCreditPanel">×</button>
          </header>
          <main>
            <div class="balance-card">
              <span>可用额度</span>
              <b>{{ creditBalance }}</b>
              <em>点</em>
            </div>
            <div class="rules-card">
              <b>消耗规则</b>
              <p>2D生图：{{ imageCost }}点 / 次</p>
              <p>3D生成：{{ modelCost }}点 / 次</p>
              <p>OBJ/STL转换下载：{{ convertCost }}点 / 次</p>
            </div>
            <div class="packages">
              <button v-for="pkg in rechargePackages" :key="pkg.points" type="button" @click="contactAdminForRecharge(pkg.points)">
                <strong>{{ pkg.points }} 点</strong>
                <span>{{ pkg.label }}</span>
                <em>{{ pkg.desc }}</em>
              </button>
            </div>
            <p class="recharge-note">第一版暂不接入在线支付。点击套餐会复制充值申请文案，请发给平台管理员；管理员在后台「C端额度管理」为你充值后，刷新即可看到余额。</p>
          </main>
          <footer>
            <button type="button" @click="contactAdminForRecharge()">复制充值申请</button>
            <button type="button" class="done" @click="closeCreditPanel">完成</button>
          </footer>
        </div>
      </section>

      <section v-if="productionModal" class="production-modal" @click.self="closeProductionRequest">
        <div class="production-card">
          <header>
            <div><b>{{ productionForm.requestType === 'sample' ? '提交打样申请' : '提交批量生产申请' }}</b><span>作品：{{ productionModal.title || '3D模型作品' }}</span></div>
            <button type="button" @click="closeProductionRequest">×</button>
          </header>
          <main>
            <p class="purpose-in-form">创作目的：{{ selectedPurposeFullText }}</p>
            <label><span>总数量</span><input v-model.number="productionForm.quantity" type="number" min="1" /></label>
            <div v-if="creationPurpose === 'personal'" class="single-route">
              <b>个人收藏 / 送礼路径</b>
              <span>全部数量将按个人/送礼用途执行，不进入售卖渠道，不支持拆分到博物馆。</span>
            </div>
            <template v-else>
              <div class="single-route museum">
                <b>博物馆售卖路径</b>
                <span>全部数量将进入所选博物馆售卖，不支持拆分给个人或多个博物馆。</span>
              </div>
              <div class="dist-head"><b>选择博物馆</b><small>全部 {{ productionForm.quantity || 0 }} 个</small></div>
              <div v-if="productionForm.museumDistribution[0]" class="dist-row single">
                <select v-model="productionForm.museumDistribution[0].museumId" @change="changeMuseum(productionForm.museumDistribution[0])"><option v-for="m in museums" :key="m.id" :value="m.id">{{ m.name }}</option></select>
              </div>
              <p v-else class="alloc-tip bad">暂无可选博物馆，请联系平台管理员配置。</p>
            </template>
            <template v-if="creationPurpose === 'personal'">
              <label><span>收件人</span><input v-model.trim="productionForm.recipientName" placeholder="收件人姓名" /></label>
              <label><span>手机号</span><input v-model.trim="productionForm.recipientPhone" placeholder="用于寄送联系" /></label>
              <label><span>收货地址</span><textarea v-model.trim="productionForm.recipientAddress" rows="2" placeholder="收货地址"></textarea></label>
            </template>
            <label><span>申请说明</span><textarea v-model.trim="productionForm.note" rows="3"></textarea></label>
          </main>
          <footer>
            <button type="button" @click="closeProductionRequest">取消</button>
            <button type="button" class="submit" :disabled="submittingProduction" @click="submitProductionRequest">{{ submittingProduction ? '提交中' : '提交审批' }}</button>
          </footer>
        </div>
      </section>

      <section v-if="previewAsset" class="model-preview-modal" @click.self="closeModelPreview">
        <div class="model-preview-top">
          <button type="button" class="preview-back" @click="closeModelPreview">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18 9 12l6-6"/></svg>
            返回
          </button>
          <div>
            <b>3D作品预览</b>
            <span>拖动旋转 · 双指缩放</span>
          </div>
        </div>

        <div class="model-viewer-wrap">
          <model-viewer
            class="model-viewer"
            :src="previewModelUrl"
            :poster="previewAsset.previewUrl || ''"
            alt="3D模型预览"
            camera-controls
            auto-rotate
            interaction-prompt="auto"
            shadow-intensity="0.8"
            exposure="1"
            environment-image="neutral"
            ar
            @load="previewReady = true"
            @error="previewLoadFailed = true"
          >
          </model-viewer>
          <div v-if="!previewReady && !previewLoadFailed" class="model-loading">
            <i></i>
            <span>模型加载中</span>
          </div>
          <div v-if="previewLoadFailed" class="model-error">
            <b>暂时无法预览</b>
            <span>可以先下载模型文件，或稍后再试。</span>
          </div>
        </div>

        <div class="model-preview-bottom">
          <button type="button" @click="closeModelPreview">完成</button>
          <label class="format-select">
            <span>下载格式</span>
            <select v-model="previewDownloadFormat">
              <option value="GLB">GLB</option>
              <option value="OBJ">OBJ</option>
              <option value="STL">STL</option>
            </select>
          </label>
          <button type="button" class="download-action" :disabled="previewDownloading" @click="downloadPreviewModel">{{ previewDownloading ? '处理中' : `下载${previewDownloadFormat}${previewDownloadFormat==='GLB'?'':` · ${convertCost}点`}` }}</button>
        </div>
      </section>
    </Teleport>
  </main>
</template>

<style scoped>
.purpose-gate{position:fixed;inset:0;z-index:300;display:flex;align-items:center;justify-content:center;padding:20px;background:radial-gradient(circle at 80% 10%,rgba(255,255,255,.24),transparent 180px),linear-gradient(160deg,#2a1c16,#7c3f2b 58%,#e0a35d);color:#fff}.purpose-card{width:min(420px,100%);padding:24px;border-radius:28px;background:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.24);box-shadow:0 30px 80px rgba(37,22,14,.35);backdrop-filter:blur(18px)}.purpose-brand{display:flex;align-items:center;gap:10px;margin-bottom:18px}.purpose-brand img{width:38px;height:38px;border-radius:10px;background:#fff}.purpose-brand span{font-size:12px;font-weight:900;letter-spacing:1.4px}.purpose-card h1{margin:0 0 10px;font-size:30px;letter-spacing:-.04em}.purpose-card p{margin:0 0 16px;color:rgba(255,255,255,.78);line-height:1.7}.purpose-options{display:flex;flex-direction:column;gap:10px}.purpose-options button{position:relative;text-align:left;padding:16px;border:1px solid rgba(255,255,255,.24);border-radius:18px;background:rgba(255,255,255,.92);color:#201a17;box-shadow:0 12px 30px rgba(32,26,23,.12)}.purpose-options i{display:inline-flex;margin-bottom:8px;padding:4px 8px;border-radius:999px;background:#fff7ed;color:#b4532a;font-style:normal;font-size:11px;font-weight:950}.purpose-options b,.purpose-options span{display:block}.purpose-options b{font-size:18px}.purpose-options span{margin-top:5px;color:#6e5547;font-size:13px;line-height:1.5}.purpose-change{position:relative;z-index:1;align-self:flex-start;margin-top:8px;height:30px;border:1px solid rgba(255,255,255,.3);border-radius:999px;background:rgba(255,255,255,.12);color:#fff;font-size:11px;font-weight:900}.purpose-in-form{margin:0 0 10px;padding:9px 10px;border-radius:12px;background:#fff7ed;color:#9a3412;font-size:12px;font-weight:900}.credit-modal{position:fixed;inset:0;z-index:260;background:rgba(32,26,23,.58);backdrop-filter:blur(8px);display:flex;align-items:flex-end;justify-content:center}.credit-card{width:min(460px,100vw);max-height:88vh;display:flex;flex-direction:column;border-radius:24px 24px 0 0;background:#fff;overflow:hidden;color:#201a17}.credit-card header,.credit-card footer{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:14px;border-bottom:1px solid #eadfd4}.credit-card footer{border-top:1px solid #eadfd4;border-bottom:0}.credit-card header b,.credit-card header span{display:block}.credit-card header span{margin-top:3px;color:#8a7161;font-size:12px}.credit-card header button{width:34px;height:34px;border:0;border-radius:10px;background:#f6f2ea;font-size:22px}.credit-card main{padding:14px;overflow:auto}.balance-card{position:relative;padding:18px;border-radius:20px;background:linear-gradient(135deg,#201a17,#7c3f2b);color:#fff}.balance-card span,.balance-card em{font-style:normal;color:rgba(255,255,255,.72);font-size:12px;font-weight:900}.balance-card b{display:inline-block;margin:8px 6px 0 0;font-size:42px}.rules-card{margin-top:10px;padding:14px;border-radius:18px;background:#fffaf4;border:1px solid #eadfd4}.rules-card b{display:block;margin-bottom:8px}.rules-card p{margin:5px 0;color:#6e5547;font-size:13px}.packages{display:grid;grid-template-columns:1fr;gap:9px;margin-top:10px}.packages button{text-align:left;padding:13px;border:1px solid #eadfd4;border-radius:16px;background:#fff;color:#201a17}.packages strong,.packages span,.packages em{display:block}.packages strong{font-size:20px}.packages span{margin-top:3px;font-weight:900}.packages em{margin-top:4px;color:#8a7161;font-size:12px;font-style:normal}.recharge-note{margin:12px 0 0;color:#8a7161;font-size:12px;line-height:1.6}.credit-card footer button{height:38px;border:0;border-radius:10px;background:#201a17;color:#fff;padding:0 12px;font-weight:900}.credit-card footer .done{background:#b4532a}.hero-actions .recharge-hero{background:rgba(255,255,255,.92);color:#7c2d12;border-color:rgba(255,255,255,.92)}.consumer-shell{min-height:100vh;background:#f6f2ea;color:#201a17;padding:14px 14px 96px;font-family:Inter,"PingFang SC",system-ui,sans-serif}.consumer-top{position:sticky;top:0;z-index:10;display:flex;align-items:center;justify-content:space-between;margin:-14px -14px 10px;padding:12px 14px;background:rgba(246,242,234,.86);backdrop-filter:blur(18px);border-bottom:1px solid rgba(120,92,64,.12)}.brand{display:flex;align-items:center;gap:9px}.brand img{width:34px;height:34px;border-radius:8px;object-fit:cover}.brand b,.brand span{display:block}.brand b{font-size:15px}.brand span{font-size:11px;color:#8a7161}.icon-btn{width:38px;height:38px;border:0;border-radius:8px;background:#fff;color:#4b3327;box-shadow:0 6px 18px rgba(69,45,26,.08)}.icon-btn svg,.primary svg,.quick-tabs svg,.upload-box svg{width:18px;height:18px}.hero{position:relative;min-height:172px;padding:24px 18px;border-radius:8px;background:radial-gradient(circle at 84% 16%,rgba(255,255,255,.2),transparent 24%),linear-gradient(135deg,#2a1c16,#8e402b 62%,#c27643);color:#fff;display:flex;flex-direction:column;justify-content:flex-end;box-shadow:0 18px 42px rgba(90,54,31,.22);overflow:hidden}.hero:after{content:"";position:absolute;right:18px;top:16px;width:92px;height:92px;border-radius:50%;background:rgba(255,255,255,.12);box-shadow:-26px 46px 0 rgba(255,255,255,.08)}.hero>*{position:relative;z-index:1}.hero span{width:max-content;padding:5px 9px;border-radius:999px;background:rgba(255,255,255,.16);font-size:11px}.hero h1{margin:12px 0 15px;font-size:28px;line-height:1.08;letter-spacing:0}.hero-actions{display:flex;gap:9px}.hero-actions button{height:38px;padding:0 14px;border:1px solid rgba(255,255,255,.34);border-radius:8px;background:rgba(255,255,255,.14);color:#fff;font-weight:800}.quick-tabs{position:fixed;left:14px;right:14px;bottom:14px;z-index:20;display:grid;grid-template-columns:repeat(3,1fr);gap:6px;padding:7px;border:1px solid rgba(120,92,64,.14);border-radius:8px;background:rgba(255,255,255,.9);backdrop-filter:blur(18px);box-shadow:0 18px 50px rgba(57,38,26,.16)}.quick-tabs button{height:48px;border:0;border-radius:8px;background:transparent;color:#8a7161;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;font-size:11px;font-weight:800}.quick-tabs button.active{background:#201a17;color:#fff}.panel{margin-top:12px;padding:15px;border-radius:8px;background:#fff;box-shadow:0 12px 32px rgba(77,51,31,.08);border:1px solid rgba(120,92,64,.1)}.section-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:13px}.section-head span{font-size:10px;font-weight:900;letter-spacing:1.6px;color:#b4532a}.section-head b{font-size:18px}label{display:block;margin-top:12px}label>span{display:block;margin-bottom:7px;font-size:13px;font-weight:800;color:#4a3429}textarea{width:100%;box-sizing:border-box;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4;padding:12px;color:#241a16;font-size:15px;line-height:1.55;resize:vertical;outline:none}textarea:focus{border-color:#b4532a;box-shadow:0 0 0 3px rgba(180,83,42,.12)}.chips{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:10px}.chips.compact{grid-template-columns:repeat(3,1fr)}.chips button,.mode-switch button{min-height:38px;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4;color:#6e5547;font-weight:800}.chips button.active,.mode-switch button.active{border-color:#201a17;background:#201a17;color:#fff}.primary{width:100%;height:52px;margin-top:14px;border:0;border-radius:8px;background:#b4532a;color:#fff;font-size:16px;font-weight:900;display:flex;align-items:center;justify-content:center;gap:8px;box-shadow:0 12px 26px rgba(180,83,42,.24)}.primary.green{background:#0f766e;box-shadow:0 12px 26px rgba(15,118,110,.2)}.primary:disabled{opacity:.55}.result-card{overflow:hidden;margin-top:14px;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4}.result-card>img{display:block;width:100%;max-height:480px;object-fit:contain;background:#211814}.result-info{padding:12px}.result-info b{display:block;margin-bottom:5px}.result-info p{margin:0 0 10px;white-space:pre-wrap;color:#6e5547;font-size:13px;line-height:1.6}.result-actions{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.result-info a,.result-info button{display:inline-flex;height:34px;align-items:center;padding:0 12px;border:0;border-radius:8px;background:#201a17;color:#fff;text-decoration:none;font-weight:800}.submitted-tip{display:inline-flex;height:30px;align-items:center;padding:0 10px;border-radius:999px;background:#fff7ed;color:#b45309;font-size:12px;font-weight:900}.mode-switch{display:grid;grid-template-columns:1fr 1fr;gap:8px}.upload-box{position:relative;min-height:170px;border:1px dashed #c7a995;border-radius:8px;background:#fffaf4;display:flex;align-items:center;justify-content:center;overflow:hidden}.upload-box input{position:absolute;inset:0;opacity:0}.upload-box img{width:100%;height:220px;object-fit:cover}.upload-box span{display:flex;align-items:center;gap:8px;color:#8a7161;font-weight:900}.progress{height:8px;margin-top:12px;border-radius:999px;background:#e9ded2;overflow:hidden}.progress span{display:block;height:100%;border-radius:999px;background:#0f766e;transition:width .25s ease}.gallery{display:grid;grid-template-columns:1fr 1fr;gap:10px}.gallery article{position:relative;overflow:hidden;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4}.gallery img,.model-tile{width:100%;aspect-ratio:1/1;object-fit:cover;background:#201a17;color:#fff}.model-tile{display:flex;align-items:center;justify-content:center;font-size:28px;font-weight:950}.work-status{position:absolute;top:8px;right:8px;padding:4px 7px;border-radius:999px;background:rgba(255,255,255,.92);font-size:10px;font-weight:900}.work-status.draft{color:#64748b}.work-status.review{color:#b45309}.work-status.approved{color:#047857}.work-status.rejected{color:#dc2626}.gallery b{display:block;padding:9px;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.gallery button{margin:0 9px 9px;height:30px;border:0;border-radius:8px;background:#201a17;color:#fff;font-weight:800}.gallery .review-submit{background:#b4532a}.production-actions{display:flex;gap:6px;padding:0 9px 9px}.gallery .production-actions button{flex:1;margin:0;background:#0f766e}.gallery .production-actions .prod{background:#7c2d12}.production-list{margin-top:14px;display:flex;flex-direction:column;gap:8px}.production-list h3{margin:4px 0;font-size:15px}.production-list article{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px;border-radius:10px;background:#fffaf4;border:1px solid #eadfd4}.production-list b,.production-list span{display:block}.production-list span{margin-top:3px;color:#8a7161;font-size:12px}.production-list em{font-style:normal;padding:4px 8px;border-radius:999px;font-size:11px;font-weight:900}.production-list em.review{background:#fff7ed;color:#b45309}.production-list em.approved{background:#ecfdf5;color:#047857}.production-list em.rejected{background:#fef2f2;color:#dc2626}.production-modal{position:fixed;inset:0;z-index:220;background:rgba(32,26,23,.58);backdrop-filter:blur(8px);display:flex;align-items:flex-end;justify-content:center}.production-card{width:min(460px,100vw);max-height:88vh;display:flex;flex-direction:column;border-radius:24px 24px 0 0;background:#fff;overflow:hidden}.production-card header,.production-card footer{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:14px;border-bottom:1px solid #eadfd4}.production-card footer{border-top:1px solid #eadfd4;border-bottom:0}.production-card header b,.production-card header span{display:block}.production-card header span{margin-top:3px;color:#8a7161;font-size:12px}.production-card header button{width:34px;height:34px;border:0;border-radius:10px;background:#f6f2ea;font-size:22px}.production-card main{padding:14px;overflow:auto}.production-card input,.production-card select{width:100%;height:40px;box-sizing:border-box;border:1px solid #eadfd4;border-radius:10px;background:#fffaf4;padding:0 10px}.dist-head{display:flex;align-items:center;justify-content:space-between;margin-top:12px}.dist-head button,.production-card footer button{height:38px;border:0;border-radius:10px;background:#201a17;color:#fff;padding:0 12px;font-weight:900}.dist-row{display:grid;grid-template-columns:1fr 74px 52px;gap:7px;margin-top:8px}.dist-row button{border:0;border-radius:10px;background:#fef2f2;color:#dc2626;font-weight:900}.alloc-tip{margin:8px 0 0;color:#047857;font-size:12px;font-weight:900}.alloc-tip.bad{color:#dc2626}.production-card footer .submit{background:#b4532a}.empty{padding:40px 0;text-align:center;color:#8a7161}@media(min-width:720px){.consumer-shell{display:block;max-width:460px;margin:0 auto;box-shadow:0 0 0 1px rgba(120,92,64,.08),0 24px 80px rgba(40,28,22,.15)}.quick-tabs{left:50%;right:auto;width:432px;transform:translateX(-50%)}}
</style>

<style scoped>
.immersive-shell{
  background:linear-gradient(180deg,#f7f0e8 0%,#eee4da 100%);
  padding:0 14px 104px;
}
.ambient-layer{
  opacity:.45;
  mix-blend-mode:normal;
}
.immersive-shell .consumer-top{
  background:rgba(247,240,232,.86);
  color:#201a17;
  backdrop-filter:blur(18px);
  border-bottom:1px solid rgba(87,65,44,.08);
}
.immersive-shell .brand span{
  color:#8a7161;
}
.immersive-shell .icon-btn{
  background:#fffaf5;
  color:#201a17;
  box-shadow:0 8px 24px rgba(58,39,25,.08);
}
.immersive-shell .hero{
  min-height:248px;
  margin:10px 0 14px;
  padding:24px 18px;
  border-radius:28px;
  background:
    radial-gradient(circle at 82% 18%,rgba(255,255,255,.18),transparent 110px),
    linear-gradient(135deg,#221713,#6b3a29 58%,#ad6840);
  box-shadow:0 22px 54px rgba(77,48,29,.2);
}
.immersive-shell .hero:after{
  width:150px;
  height:150px;
  right:-24px;
  top:18px;
  opacity:.72;
}
.immersive-shell .hero h1{
  max-width:11ch;
  font-size:32px;
}
.immersive-shell .hero p{
  max-width:24ch;
  margin-bottom:18px;
  font-size:13px;
}
.immersive-shell .hero-actions button{
  min-width:104px;
  height:42px;
}
.immersive-shell .creation-panel{
  margin-top:12px;
  border-radius:24px;
  box-shadow:0 18px 44px rgba(58,39,25,.1);
}
.immersive-shell .section-head{
  margin-bottom:14px;
}
.immersive-shell .section-head span{
  font-size:10px;
  letter-spacing:1.8px;
}
.immersive-shell textarea{
  min-height:138px;
}
.immersive-shell .primary{
  min-height:56px;
}
</style>

<style scoped>
.immersive-shell{
  position:relative;
  min-height:100dvh;
  overflow-x:hidden;
  background:
    linear-gradient(180deg,#17100d 0,#2a1b15 34%,#f5eee5 34.5%,#eee3d8 100%);
  padding:0 14px 104px;
}
.ambient-layer{
  position:fixed;
  inset:0;
  pointer-events:none;
  background:
    radial-gradient(circle at 76px 108px,rgba(255,255,255,.16),transparent 80px),
    radial-gradient(circle at 86% 15%,rgba(194,118,67,.38),transparent 160px),
    radial-gradient(circle at 18% 42%,rgba(15,118,110,.18),transparent 150px);
  mix-blend-mode:screen;
  opacity:.92;
  z-index:0;
}
.immersive-shell>*:not(.ambient-layer){
  position:relative;
  z-index:1;
}
.immersive-shell .consumer-top{
  margin:0 -14px;
  padding:14px 16px 10px;
  border:0;
  background:linear-gradient(180deg,rgba(23,16,13,.94),rgba(23,16,13,.62));
  color:#fff;
}
.immersive-shell .brand span{
  color:rgba(255,255,255,.64);
}
.immersive-shell .icon-btn{
  background:rgba(255,255,255,.12);
  color:#fff;
  box-shadow:none;
  backdrop-filter:blur(12px);
}
.immersive-shell .hero{
  min-height:430px;
  margin:0 -14px;
  padding:92px 22px 28px;
  border-radius:0 0 34px 34px;
  background:
    linear-gradient(180deg,rgba(23,16,13,.15),rgba(23,16,13,.76)),
    radial-gradient(circle at 72% 18%,rgba(255,221,186,.24),transparent 130px),
    radial-gradient(circle at 16% 70%,rgba(20,184,166,.16),transparent 120px),
    linear-gradient(135deg,#211510 0%,#5a3024 48%,#b86b3b 100%);
  box-shadow:0 28px 64px rgba(48,29,19,.38);
  justify-content:flex-end;
}
.immersive-shell .hero:after{
  right:-34px;
  top:50px;
  width:220px;
  height:220px;
  background:
    linear-gradient(145deg,rgba(255,255,255,.18),rgba(255,255,255,.02)),
    radial-gradient(circle at 35% 30%,rgba(255,244,220,.16),transparent 46%);
  border:1px solid rgba(255,255,255,.12);
  box-shadow:-110px 138px 0 rgba(255,255,255,.055);
}
.hero-glass{
  position:absolute;
  top:18px;
  left:22px;
  right:22px;
  display:flex;
  align-items:center;
  gap:10px;
  padding:10px 12px;
  border:1px solid rgba(255,255,255,.14);
  border-radius:18px;
  background:rgba(255,255,255,.09);
  backdrop-filter:blur(18px);
}
.hero-glass img{
  width:34px;
  height:34px;
  border-radius:10px;
  object-fit:cover;
}
.hero-glass strong{
  color:#fff;
  letter-spacing:.02em;
}
.immersive-shell .hero span{
  margin-bottom:10px;
  background:rgba(255,255,255,.15);
}
.immersive-shell .hero h1{
  max-width:10.5ch;
  margin:0 0 14px;
  font-size:38px;
  line-height:1.04;
  text-wrap:balance;
}
.immersive-shell .hero p{
  max-width:29ch;
  margin-bottom:20px;
  color:rgba(255,255,255,.82);
}
.immersive-shell .hero-actions button{
  min-width:122px;
  height:44px;
  border-radius:14px;
  background:rgba(255,255,255,.13);
}
.immersive-shell .workflow-strip{
  margin:-28px 2px 12px;
  padding:10px;
  border-radius:20px;
  background:rgba(255,250,244,.92);
  backdrop-filter:blur(18px);
  box-shadow:0 18px 46px rgba(52,34,22,.18);
}
.immersive-shell .workflow-chip{
  justify-content:center;
  gap:6px;
}
.immersive-shell .workflow-chip b{
  width:28px;
  height:28px;
  background:#efe1d5;
}
.immersive-shell .workflow-chip.active b{
  background:#17100d;
}
.live-console{
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:14px;
  margin:0 2px 12px;
  padding:14px 16px;
  border-radius:20px;
  background:linear-gradient(135deg,#231813,#3a241b);
  color:#fff;
  box-shadow:0 18px 44px rgba(50,31,20,.22);
}
.live-console span{
  display:block;
  margin-bottom:4px;
  color:rgba(255,255,255,.58);
  font-size:11px;
}
.live-console b{
  display:block;
  color:#fff;
  font-size:13px;
  line-height:1.35;
}
.live-console i{
  width:14px;
  height:14px;
  border-radius:50%;
  background:#22c55e;
  box-shadow:0 0 0 7px rgba(34,197,94,.12);
  flex:0 0 auto;
}
.live-console i.active{
  animation:pulseLive 1s infinite ease-in-out;
}
@keyframes pulseLive{
  0%,100%{transform:scale(.86);opacity:.65}
  50%{transform:scale(1.08);opacity:1}
}
.immersive-shell .creation-panel{
  margin-top:12px;
  padding:18px;
  border-radius:28px;
  background:linear-gradient(180deg,rgba(255,255,255,.96),rgba(255,250,245,.9));
  border:1px solid rgba(122,92,68,.14);
  box-shadow:0 24px 60px rgba(58,39,25,.12);
}
.immersive-shell .section-head b{
  font-size:21px;
}
.immersive-shell .service-pill{
  border-radius:18px;
  background:#fff8ef;
}
.immersive-shell textarea{
  min-height:150px;
  border-radius:20px;
  background:#fffdf9;
  font-size:16px;
  box-shadow:inset 0 1px 0 rgba(255,255,255,.8);
}
.immersive-shell .chips button,
.immersive-shell .mode-switch button{
  border-radius:16px;
}
.immersive-shell .primary{
  border-radius:20px;
  min-height:60px;
  background:linear-gradient(135deg,#17100d,#0f766e 52%,#19a092);
  box-shadow:0 22px 42px rgba(15,118,110,.24);
}
.immersive-shell .primary.green{
  background:linear-gradient(135deg,#17211f,#0f766e 58%,#1ca18f);
}
.immersive-shell .upload-box{
  min-height:210px;
  border-radius:24px;
}
.immersive-shell .quick-tabs{
  left:16px;
  right:16px;
  bottom:14px;
  border-radius:24px;
  padding:8px;
  background:rgba(255,250,245,.9);
  border:1px solid rgba(93,70,52,.16);
  box-shadow:0 22px 64px rgba(45,30,20,.22);
}
.immersive-shell .quick-tabs button{
  border-radius:18px;
}
.immersive-shell .quick-tabs button.active{
  background:linear-gradient(180deg,#17100d,#31231d);
}
@media(min-width:720px){
  .immersive-shell{
    max-width:460px;
    margin:0 auto;
    box-shadow:0 0 0 1px rgba(120,92,64,.08),0 24px 80px rgba(40,28,22,.15);
  }
  .immersive-shell .hero{
    margin-left:-14px;
    margin-right:-14px;
  }
  .immersive-shell .quick-tabs{
    left:50%;
    right:auto;
    width:428px;
    transform:translateX(-50%);
  }
}
</style>

<style scoped>
.consumer-shell{
  background:
    radial-gradient(circle at top left, rgba(180,83,42,.12), transparent 28%),
    radial-gradient(circle at 92% 4%, rgba(15,118,110,.12), transparent 24%),
    linear-gradient(180deg, #f9f5ef 0%, #f3ede6 52%, #efe7dd 100%);
}
.consumer-top{
  margin:-14px -14px 12px;
  padding:14px 14px 12px;
  border-bottom:1px solid rgba(87,65,44,.08);
  background:rgba(248,241,232,.82);
}
.brand img{
  width:38px;
  height:38px;
  border-radius:10px;
  box-shadow:0 8px 20px rgba(39,28,20,.12);
}
.hero{
  min-height:212px;
  padding:24px 18px 20px;
  border-radius:22px;
  background:
    radial-gradient(circle at 82% 18%, rgba(255,255,255,.16), transparent 26%),
    radial-gradient(circle at 18% 22%, rgba(255,255,255,.08), transparent 22%),
    linear-gradient(135deg, #231813 0%, #5f3124 46%, #9d5a35 100%);
  box-shadow:0 24px 56px rgba(87,52,29,.24);
}
.hero:after{
  width:128px;
  height:128px;
  right:12px;
  top:-6px;
  background:
    radial-gradient(circle, rgba(255,255,255,.16) 0, rgba(255,255,255,.08) 38%, transparent 70%);
  box-shadow:-48px 78px 0 rgba(255,255,255,.06);
}
.hero span{
  padding:6px 10px;
  background:rgba(255,255,255,.14);
  letter-spacing:.8px;
}
.hero h1{
  max-width:12ch;
  font-size:30px;
  line-height:1.1;
}
.hero p{
  margin:0 0 16px;
  max-width:26ch;
  color:rgba(255,255,255,.88);
  font-size:13px;
  line-height:1.55;
}
.hero-actions button{
  min-width:108px;
  border-color:rgba(255,255,255,.2);
  background:rgba(255,255,255,.12);
  backdrop-filter:blur(10px);
}
.workflow-strip{
  display:grid;
  grid-template-columns:1fr 18px 1fr 18px 1fr;
  align-items:center;
  gap:8px;
  margin:12px 0 4px;
  padding:10px 12px;
  border-radius:16px;
  background:rgba(255,255,255,.7);
  border:1px solid rgba(87,65,44,.08);
  box-shadow:0 10px 26px rgba(76,53,33,.06);
}
.workflow-chip{
  display:flex;
  align-items:center;
  gap:8px;
  min-width:0;
  color:#7a6758;
}
.workflow-chip b{
  display:flex;
  align-items:center;
  justify-content:center;
  width:26px;
  height:26px;
  border-radius:999px;
  background:#f3e8de;
  color:#8b5a3c;
  font-size:10px;
  flex:0 0 auto;
}
.workflow-chip span{
  overflow:hidden;
  white-space:nowrap;
  text-overflow:ellipsis;
  font-size:12px;
  font-weight:800;
}
.workflow-chip.active b{
  background:#201a17;
  color:#fff;
}
.workflow-chip.active span{
  color:#201a17;
}
.workflow-arrow{
  color:#b49a87;
  text-align:center;
  font-weight:900;
}
.panel{
  margin-top:14px;
  padding:16px;
  border-radius:22px;
  background:rgba(255,255,255,.82);
  border:1px solid rgba(87,65,44,.09);
  box-shadow:0 18px 42px rgba(69,49,31,.08);
  backdrop-filter:blur(14px);
}
.section-head{
  margin-bottom:10px;
}
.section-head span{
  color:#b06539;
}
.section-head b{
  color:#221913;
}
.service-pill{
  display:flex;
  align-items:center;
  gap:8px;
  margin:2px 0 12px;
  padding:10px 12px;
  border-radius:14px;
  background:#fbf4ec;
  border:1px solid #ecd9c8;
}
.service-pill i{
  width:8px;
  height:8px;
  border-radius:50%;
  background:#0f766e;
  box-shadow:0 0 0 4px rgba(15,118,110,.12);
}
.service-pill span{
  color:#9a7c68;
  font-size:11px;
  font-weight:800;
}
.service-pill b{
  margin-left:auto;
  color:#221913;
  font-size:12px;
  font-weight:900;
}
.service-pill em{
  margin-left:8px;
  color:#8a7161;
  font-size:11px;
  font-style:normal;
}
.service-pill.teal i{ background:#0f766e; box-shadow:0 0 0 4px rgba(15,118,110,.12); }
.mini-note{
  margin-top:10px;
  padding:10px 12px;
  border-radius:12px;
  background:rgba(15,23,42,.04);
  color:#6f5a4d;
  font-size:12px;
  line-height:1.55;
}
.chips{
  grid-template-columns:repeat(3,minmax(0,1fr));
}
.chips button,
.mode-switch button{
  min-height:42px;
  background:#fffaf4;
  border-color:#e7d7c9;
  color:#745e4f;
  box-shadow:0 1px 0 rgba(255,255,255,.8) inset;
}
.chips button.active,
.mode-switch button.active{
  background:linear-gradient(180deg,#211a17,#34271f);
  border-color:#211a17;
  color:#fff;
}
.primary{
  min-height:56px;
  border-radius:16px;
  background:linear-gradient(135deg,#0f766e,#1d9b8f 54%,#0b5b56);
  box-shadow:0 18px 30px rgba(15,118,110,.22);
}
.primary.green{
  background:linear-gradient(135deg,#1e6f60,#0f766e 55%,#124b42);
}
.result-card{
  margin-top:16px;
  border-radius:18px;
  overflow:hidden;
  border-color:#ead8c9;
  background:linear-gradient(180deg,#fffdf9,#f8f0e6);
}
.result-card>img{
  border-bottom:1px solid rgba(87,65,44,.08);
  max-height:360px;
}
.result-info{
  padding:14px;
}
.result-info b{
  font-size:15px;
}
.result-info p{
  color:#6e584a;
}
.result-info a,
.result-info button{
  border-radius:999px;
  background:#201a17;
}
.upload-box{
  border-radius:18px;
  border-color:#dcc2ae;
  background:linear-gradient(180deg,#fffaf4,#f8efe5);
}
.upload-box span{
  color:#8c705e;
}
.progress{
  height:9px;
  border-radius:999px;
  background:#eadfd5;
}
.progress span{
  background:linear-gradient(90deg,#0f766e,#1d9b8f);
}
.gallery{
  grid-template-columns:repeat(2,minmax(0,1fr));
}
.gallery article{
  border-radius:16px;
  border-color:#ead8c9;
  background:#fffdf9;
  box-shadow:0 10px 24px rgba(74,50,31,.06);
}
.gallery img,.model-tile{
  border-bottom:1px solid rgba(87,65,44,.08);
}
.gallery button{
  border-radius:999px;
  background:#201a17;
}
.empty{
  color:#8a7161;
}
.quick-tabs{
  bottom:12px;
  border-radius:18px;
  padding:8px;
  background:rgba(255,251,246,.92);
  border:1px solid rgba(87,65,44,.12);
  box-shadow:0 18px 50px rgba(57,38,26,.18);
}
.quick-tabs button{
  min-height:48px;
  border-radius:12px;
  color:#927868;
}
.quick-tabs button.active{
  background:linear-gradient(180deg,#1e1714,#2d221d);
}
@media(min-width:720px){
  .consumer-shell{
    max-width:460px;
    margin:0 auto;
    box-shadow:0 0 0 1px rgba(120,92,64,.08),0 24px 80px rgba(40,28,22,.15);
  }
  .quick-tabs{
    left:50%;
    right:auto;
    width:432px;
    transform:translateX(-50%);
  }
}
</style>

<style scoped>
/* Must stay last: fixes the C-end mobile layout after older style blocks. */
.consumer-shell.immersive-shell{
  min-height:100dvh !important;
  padding:0 14px 28px !important;
  overflow-x:hidden !important;
  background:linear-gradient(180deg,#fbf6ef 0%,#f1e8df 52%,#ece0d5 100%) !important;
  color:#201a17 !important;
}
.consumer-shell.immersive-shell *,
.consumer-shell.immersive-shell *::before,
.consumer-shell.immersive-shell *::after{
  box-sizing:border-box !important;
}
.consumer-shell.immersive-shell .ambient-layer,
.consumer-shell.immersive-shell .hero-glass,
.consumer-shell.immersive-shell .workflow-strip,
.consumer-shell.immersive-shell .live-console,
.consumer-shell.immersive-shell .service-pill,
.consumer-shell.immersive-shell .mini-note{
  display:none !important;
}
.consumer-shell.immersive-shell .consumer-top{
  position:sticky !important;
  top:0 !important;
  z-index:20 !important;
  margin:0 -14px !important;
  padding:12px 16px !important;
  background:rgba(251,246,239,.94) !important;
  color:#201a17 !important;
  border-bottom:1px solid rgba(87,65,44,.08) !important;
}
.consumer-shell.immersive-shell .brand span{ color:#8a7161 !important; }
.consumer-shell.immersive-shell .icon-btn{
  width:38px !important;
  height:38px !important;
  flex:0 0 38px !important;
  border-radius:12px !important;
  background:#fffaf5 !important;
  color:#201a17 !important;
  box-shadow:0 8px 22px rgba(58,39,25,.08) !important;
}
.consumer-shell.immersive-shell .hero{
  min-height:188px !important;
  margin:14px 0 12px !important;
  padding:22px 18px 18px !important;
  border-radius:24px !important;
  background:radial-gradient(circle at 92% 18%,rgba(255,255,255,.18),transparent 94px),linear-gradient(135deg,#241814 0%,#713e2d 60%,#af6840 100%) !important;
  box-shadow:0 18px 44px rgba(78,48,29,.18) !important;
}
.consumer-shell.immersive-shell .hero:after{ display:none !important; }
.consumer-shell.immersive-shell .hero span{
  margin:0 0 11px !important;
  color:#fff !important;
}
.consumer-shell.immersive-shell .hero h1{
  max-width:10em !important;
  margin:0 0 8px !important;
  font-size:30px !important;
  line-height:1.08 !important;
}
.consumer-shell.immersive-shell .hero p{
  max-width:24em !important;
  margin:0 0 15px !important;
  color:rgba(255,255,255,.82) !important;
  font-size:13px !important;
  line-height:1.55 !important;
}
.consumer-shell.immersive-shell .hero-actions{
  display:grid !important;
  grid-template-columns:repeat(2,minmax(0,1fr)) !important;
  gap:10px !important;
}
.consumer-shell.immersive-shell .hero-actions button{
  width:100% !important;
  min-width:0 !important;
  height:42px !important;
  border-radius:14px !important;
}
.consumer-shell.immersive-shell .quick-tabs{
  position:static !important;
  left:auto !important;
  right:auto !important;
  bottom:auto !important;
  width:100% !important;
  transform:none !important;
  display:grid !important;
  grid-template-columns:repeat(3,minmax(0,1fr)) !important;
  gap:6px !important;
  margin:0 0 12px !important;
  padding:7px !important;
  border-radius:18px !important;
  background:rgba(255,250,245,.94) !important;
  border:1px solid rgba(87,65,44,.12) !important;
  box-shadow:0 10px 28px rgba(57,38,26,.08) !important;
}
.consumer-shell.immersive-shell .quick-tabs button{
  min-width:0 !important;
  height:48px !important;
  min-height:0 !important;
  padding:0 4px !important;
  border-radius:13px !important;
  font-size:11px !important;
  white-space:normal !important;
}
.consumer-shell.immersive-shell .quick-tabs button.active{
  background:#201a17 !important;
  color:#fff !important;
}
.consumer-shell.immersive-shell .creation-panel{
  width:100% !important;
  margin:0 !important;
  padding:17px !important;
  border-radius:24px !important;
  background:rgba(255,255,255,.95) !important;
  border:1px solid rgba(87,65,44,.1) !important;
  box-shadow:0 16px 38px rgba(58,39,25,.09) !important;
}
.consumer-shell.immersive-shell .section-head{
  margin:0 0 12px !important;
}
.consumer-shell.immersive-shell .section-head b{
  font-size:20px !important;
  line-height:1.2 !important;
}
.consumer-shell.immersive-shell textarea{
  width:100% !important;
  min-height:124px !important;
  max-height:210px !important;
  border-radius:18px !important;
  font-size:15px !important;
}
.consumer-shell.immersive-shell .chips,
.consumer-shell.immersive-shell .chips.compact{
  grid-template-columns:repeat(3,minmax(0,1fr)) !important;
}
.consumer-shell.immersive-shell .mode-switch{
  grid-template-columns:repeat(2,minmax(0,1fr)) !important;
}
.consumer-shell.immersive-shell .chips button,
.consumer-shell.immersive-shell .mode-switch button{
  min-width:0 !important;
  min-height:40px !important;
  padding:0 6px !important;
  border-radius:14px !important;
  font-size:13px !important;
  line-height:1.15 !important;
  white-space:normal !important;
}
.consumer-shell.immersive-shell .primary{
  min-height:54px !important;
  border-radius:18px !important;
  background:#b4532a !important;
  box-shadow:0 12px 26px rgba(180,83,42,.24) !important;
}
.consumer-shell.immersive-shell .primary.green{
  background:#0f766e !important;
  box-shadow:0 12px 26px rgba(15,118,110,.2) !important;
}
.consumer-shell.immersive-shell .upload-box{
  min-height:168px !important;
  border-radius:20px !important;
}
.consumer-shell.immersive-shell .upload-box img{
  width:100% !important;
  height:210px !important;
  object-fit:cover !important;
}
.consumer-shell.immersive-shell .gallery{
  grid-template-columns:repeat(2,minmax(0,1fr)) !important;
}
.consumer-shell.immersive-shell .gallery article{
  min-width:0 !important;
}
.consumer-shell.immersive-shell .gallery article > button{
  width:calc(100% - 18px) !important;
}
.single-route{
  display:flex;
  flex-direction:column;
  gap:6px;
  margin-top:12px;
  padding:12px;
  border-radius:16px;
  background:#fff7ed;
  border:1px solid #fed7aa;
  color:#7c2d12;
}
.single-route b{
  font-size:14px;
  line-height:1.25;
}
.single-route span{
  color:#9a5a2a;
  font-size:12px;
  line-height:1.55;
}
.single-route.museum{
  background:#ecfdf5;
  border-color:#bbf7d0;
  color:#065f46;
}
.single-route.museum span{
  color:#047857;
}
.dist-head small{
  color:#8a7161;
  font-size:12px;
  font-weight:900;
}
.dist-row.single{
  grid-template-columns:1fr !important;
}
.model-preview-modal{
  position:fixed !important;
  inset:0 !important;
  z-index:9999 !important;
  display:flex !important;
  flex-direction:column !important;
  padding:calc(env(safe-area-inset-top,0px) + 12px) 14px calc(env(safe-area-inset-bottom,0px) + 14px) !important;
  background:
    radial-gradient(circle at 70% 12%,rgba(180,83,42,.28),transparent 180px),
    linear-gradient(180deg,#17100d 0%,#2a1d18 100%) !important;
  color:#fff !important;
}
.model-preview-top{
  display:flex !important;
  align-items:center !important;
  gap:12px !important;
  flex:0 0 auto !important;
}
.model-preview-top b,
.model-preview-top span{
  display:block !important;
}
.model-preview-top b{
  font-size:17px !important;
  line-height:1.2 !important;
}
.model-preview-top span{
  margin-top:3px !important;
  color:rgba(255,255,255,.58) !important;
  font-size:12px !important;
}
.preview-back{
  display:inline-flex !important;
  align-items:center !important;
  gap:3px !important;
  height:40px !important;
  padding:0 12px 0 8px !important;
  border:1px solid rgba(255,255,255,.13) !important;
  border-radius:999px !important;
  background:rgba(255,255,255,.08) !important;
  color:#fff !important;
  font-weight:800 !important;
}
.preview-back svg{
  width:20px !important;
  height:20px !important;
}
.model-viewer-wrap{
  position:relative !important;
  flex:1 1 auto !important;
  min-height:0 !important;
  margin:14px 0 !important;
  overflow:hidden !important;
  border:1px solid rgba(255,255,255,.1) !important;
  border-radius:28px !important;
  background:
    radial-gradient(circle at 50% 45%,rgba(255,255,255,.08),transparent 210px),
    linear-gradient(180deg,#30231d,#15100d) !important;
  box-shadow:0 24px 80px rgba(0,0,0,.28) !important;
}
.model-viewer{
  display:block !important;
  width:100% !important;
  height:100% !important;
  min-height:420px !important;
  --poster-color:transparent;
  background:transparent !important;
}
.model-loading,
.model-error{
  position:absolute !important;
  left:50% !important;
  top:50% !important;
  transform:translate(-50%,-50%) !important;
  display:flex !important;
  flex-direction:column !important;
  align-items:center !important;
  justify-content:center !important;
  gap:10px !important;
  padding:18px !important;
  border-radius:20px !important;
  background:rgba(0,0,0,.24) !important;
  color:#fff !important;
  text-align:center !important;
  backdrop-filter:blur(14px) !important;
}
.model-loading i{
  width:28px !important;
  height:28px !important;
  border:3px solid rgba(255,255,255,.2) !important;
  border-top-color:#fff !important;
  border-radius:50% !important;
  animation:modelSpin .85s linear infinite !important;
}
.model-error span{
  max-width:14em !important;
  color:rgba(255,255,255,.68) !important;
  font-size:12px !important;
  line-height:1.5 !important;
}
.model-preview-bottom{
  display:grid !important;
  grid-template-columns:1fr 1fr 1fr !important;
  gap:10px !important;
  flex:0 0 auto !important;
}
.model-preview-bottom button,
.model-preview-bottom a,
.model-preview-bottom .download-action,
.format-select{
  display:flex !important;
  align-items:center !important;
  justify-content:center !important;
  min-height:50px !important;
  border:1px solid rgba(255,255,255,.14) !important;
  border-radius:18px !important;
  background:rgba(255,255,255,.1) !important;
  color:#fff !important;
  text-decoration:none !important;
  font-size:15px !important;
  font-weight:900 !important;
}
.format-select{
  flex-direction:column !important;
  gap:2px !important;
  padding:5px 8px !important;
}
.format-select span{
  color:rgba(255,255,255,.58) !important;
  font-size:10px !important;
  line-height:1 !important;
}
.format-select select{
  width:100% !important;
  border:0 !important;
  background:transparent !important;
  color:#fff !important;
  font-size:15px !important;
  font-weight:900 !important;
  text-align:center !important;
  outline:none !important;
}
.format-select option{
  color:#111827 !important;
}
.model-preview-bottom a,
.model-preview-bottom .download-action{
  border-color:#c27643 !important;
  background:#c27643 !important;
}
.simple-note{
  margin:10px 0 0 !important;
  padding:11px 12px !important;
  border-radius:10px !important;
  background:#f0fdfa !important;
  border:1px solid #ccfbf1 !important;
  color:#0f766e !important;
  font-size:12px !important;
  line-height:1.55 !important;
  font-weight:800 !important;
}
@keyframes modelSpin{
  to{ transform:rotate(360deg); }
}
@media(min-width:720px){
  .consumer-shell.immersive-shell{
    max-width:460px !important;
    margin:0 auto !important;
    box-shadow:0 0 0 1px rgba(120,92,64,.08),0 24px 80px rgba(40,28,22,.15) !important;
  }
}
</style>
