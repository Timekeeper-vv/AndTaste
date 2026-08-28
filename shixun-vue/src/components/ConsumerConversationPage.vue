<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { User } from '../types'
import andTasteLogo from '../assets/and_taste.png'
import QRCode from 'qrcode'
import { buildCreativeGenerationPayload } from '../utils/creativeGeneration'
import { requestAssetPreviewAccess } from '../utils/assetAccess'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [message: string, type?: 'success' | 'error']; logout: [] }>()

type CreatorProfile = 'amateur' | 'professional'
type ChatRole = 'assistant' | 'user'
type QuickReply = { label: string; type: string; value?: string }
type ProductIdentity = { productNo?: string; productId?: number }
type Product = {
  key: string
  optionKey?: string
  name: string
  categoryKey?: string
  categoryName?: string
  material?: string
  process?: string
  specification?: string
  subtitle?: string
  description?: string
}
type ChatMessage = {
  id: string
  role: ChatRole
  text: string
  imageUrl?: string
  imageAssetId?: number
}
type Conversation = {
  id: number
  productNo?: string
  productId?: number
  projectId?: number
  versionId?: number
  sessionNo?: string
  productType?: string
  material?: string
  productSize?: string
  status?: string
  updatedAt?: string
  createdAt?: string
}
type MultiViewImage = ProductIdentity & {
  view: string
  label: string
  assetId: number
  previewUrl?: string
}

const api = async <T = any>(url: string, init: RequestInit = {}): Promise<T> => {
  const response = await fetch(url, { cache: 'no-store', ...init })
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new Error(data?.message || data?.error || `请求失败（HTTP ${response.status}）`)
  return data as T
}

const creatorProfile = ref<CreatorProfile>((localStorage.getItem('smart_pig_creator_profile') as CreatorProfile) || 'amateur')
const roleChanged = (profile: CreatorProfile) => {
  creatorProfile.value = profile
  localStorage.setItem('smart_pig_creator_profile', profile)
  if (profile === 'professional') void loadProfessionalSubmissions()
}

const conversations = ref<Conversation[]>([])
const activeConversationId = ref<number | null>(null)
const projectId = ref<number | null>(null)
const versionId = ref<number | null>(null)
const activeConversation = computed(() => conversations.value.find(item => item.id === activeConversationId.value) || null)
const historyLoading = ref(false)
const historyPanelOpen = ref(true)
const HISTORY_PAGE_SIZE = 5
const historyPage = ref(1)
const historyTotalPages = computed(() => Math.max(1, Math.ceil(conversations.value.length / HISTORY_PAGE_SIZE)))
const visibleConversations = computed(() => {
  const page = Math.min(Math.max(1, historyPage.value), historyTotalPages.value)
  const start = (page - 1) * HISTORY_PAGE_SIZE
  return conversations.value.slice(start, start + HISTORY_PAGE_SIZE)
})
const messages = ref<ChatMessage[]>([])
const messageCounter = ref(0)
const products = ref<Product[]>([])
const productLoading = ref(false)
const brief = ref<Record<string, any>>({})
const chatInput = ref('')
const quickReplies = ref<QuickReply[]>([])
const chatStage = ref('need_product')
const sending = ref(false)
const busy = ref(false)
const busyText = ref('之间正在处理…')
const thinking = ref(false)
const chatContent = ref<HTMLElement | null>(null)
const activeView = ref<'chat' | 'result' | 'orders'>('chat')
const selectedReferenceFile = ref<File | null>(null)
const selectedReferenceUrl = ref('')
const replacementPrompt = ref('')
const replacementOpen = ref(false)
const generatedAssetId = ref<number | null>(null)
const generatedPreviewUrl = ref('')
const simulationAssetId = ref<number | null>(null)
const simulationPreviewUrl = ref('')
const multiviewImages = ref<MultiViewImage[]>([])
const bundleId = ref<number | null>(null)
const bundleStatus = ref('')
const bundleComment = ref('')
const bundleRefreshing = ref(false)
const bundleRefreshSequence = ref(0)
const modelTask = ref<any | null>(null)
const modelReviewStatus = ref('')
const modelReviewComment = ref('')
// Browser timers return numeric handles; avoid Node.js Timeout types in the client build.
const modelPollTimer = ref<number | null>(null)
const productionRequests = ref<any[]>([])
const museums = ref<any[]>([])
const selectedRequest = ref<any | null>(null)
const productionDialogOpen = ref(false)
const productionSubmitting = ref(false)
const productionForm = ref({ requestType: 'sample', purpose: 'personal', quantity: 1, recipientName: '', recipientPhone: '', recipientAddress: '', museumId: '', museumName: '', note: '' })
const paymentOrder = ref<any | null>(null)
const paymentQrUrl = ref('')
const paymentLoading = ref(false)
const paymentTimer = ref<number | null>(null)
const paymentDialogOpen = ref(false)
const paymentRequest = ref<any | null>(null)
const paymentIntent = ref<'awaiting' | 'paid' | 'exception' | 'failed'>('awaiting')
const paymentHint = ref('请使用微信扫码支付。支付回调确认后，申请会自动进入生产中。')
const paymentError = ref('')
const paymentClosing = ref(false)
const paymentReturnView = ref<'chat' | 'result' | 'orders'>('orders')
const professionalSubmissions = ref<any[]>([])
const professionalSubmissionFile = ref<File | null>(null)
const professionalSubmissionTitle = ref('')
const professionalSubmissionNote = ref('')
const professionalSubmissionBusy = ref(false)
const submissionInput = ref<HTMLInputElement | null>(null)
const referenceInput = ref<HTMLInputElement | null>(null)
const inputLocked = computed(() => Boolean(replacementOpen.value || (activeView.value === 'result' && quickReplies.value.length)))
const inputPlaceholder = computed(() => inputLocked.value ? '请先选择上方选项，切勿直接对话' : '描述你的想法，或回答我上面的问题')
const selectedProduct = computed(() => {
  const key = String(brief.value.productKey || '')
  const name = String(brief.value.productName || brief.value.productType || '')
  return products.value.find(item => item.key === key || item.name === name) || null
})
const productName = computed(() => String(brief.value.productName || brief.value.productType || selectedProduct.value?.name || '文创产品'))
const canonicalProductNo = computed(() => String(brief.value.productNo || '').trim())
const canonicalProductId = computed(() => {
  const value = Number(brief.value.productId)
  return Number.isFinite(value) && value > 0 ? value : null
})
const material = computed(() => String(brief.value.material || ''))
const productSize = computed(() => String(brief.value.productSize || ''))
const hasCompleteBrief = computed(() => Boolean(productName.value && material.value && productSize.value && (brief.value.inspiration || brief.value.referenceAssetId)))
const canGenerateImage = computed(() => Boolean(activeConversationId.value && hasCompleteBrief.value))
const generationActionReplies = computed(() => quickReplies.value.filter(item => {
  if (!['multiview', 'model', 'replace_image', 'refine', 'bundle_review', 'bundle_production', 'commercial', 'model_review', 'works'].includes(item.type)) return false
  if (item.type === 'model_review') return Boolean(modelTask.value?.assetId) && !['review', 'approved'].includes(modelReviewStatus.value)
  if (['bundle_production', 'commercial'].includes(item.type)) {
    const bundleReady = Boolean(bundleId.value && bundleStatus.value === 'approved')
    const modelReady = modelTask.value?.status === 'succeeded' && modelReviewStatus.value === 'approved'
    return bundleReady || modelReady
  }
  return true
}))
const museumOptions = computed(() => museums.value.filter(item => item.id && item.name))

function changeHistoryPage(page: number) {
  historyPage.value = Math.min(Math.max(1, page), historyTotalPages.value)
}
watch(historyTotalPages, total => {
  if (historyPage.value > total) historyPage.value = total
})

function notify(message: string, type: 'success' | 'error' = 'success') { emit('alert', message, type) }
function messageId() { messageCounter.value += 1; return `message-${messageCounter.value}` }
function addMessage(role: ChatRole, text: string, imageUrl = '', imageAssetId?: number) {
  const value = String(text || '').trim()
  if (!value && !imageUrl && !imageAssetId) return
  messages.value.push({ id: messageId(), role, text: value, imageUrl: imageUrl || undefined, imageAssetId })
  // Scroll only the chat viewport. scrollIntoView() can scroll the page that
  // contains the chat panel, which makes every sent message jump to the top.
  void nextTick(() => {
    const container = chatContent.value
    if (!container) return
    container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' })
  })
}
function assistant(text: string) { if (text && messages.value.at(-1)?.text !== text) addMessage('assistant', text) }
function clearReferencePreview() {
  if (selectedReferenceUrl.value.startsWith('blob:')) URL.revokeObjectURL(selectedReferenceUrl.value)
  selectedReferenceFile.value = null
  selectedReferenceUrl.value = ''
}
function formatTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}
function statusText(status?: string) {
  return ({ review: '审核中', approved: '已通过', rejected: '未通过', processing: '生产中', unpaid: '待支付打样费', paid: '已支付，生产中', pending: '支付确认中' } as Record<string, string>)[String(status || '')] || String(status || '待处理')
}
function modelReviewLabel(status?: string) {
  return ({ draft: '待提交审核', review: '审核中', approved: '审核已通过', rejected: '审核未通过' } as Record<string, string>)[String(status || '')] || '待提交审核'
}
function statusClass(status?: string) {
  const value = String(status || '')
  return ['approved', 'paid', 'processing'].includes(value) ? 'success' : ['rejected'].includes(value) ? 'danger' : 'pending'
}
function imageUrl(value: any) {
  const raw = String(value?.previewUrl || value?.imageUrl || value?.fileUrl || value?.url || '')
  return /^https?:\/\//i.test(raw) ? raw : raw.startsWith('/') ? raw : ''
}
async function previewForAsset(assetId: number) {
  if (!assetId) return ''
  try {
    const access = await requestAssetPreviewAccess(assetId)
    return access.previewUrl || access.url || ''
  } catch { return '' }
}
async function loadProducts() {
  productLoading.value = true
  try {
    const rows = await api<any[]>('/api/selection/options?size=300') || []
    // The selection endpoint calls the canonical identifier `optionKey`; the
    // conversation UI historically used `key`. Normalize once at the edge so
    // product matching and every downstream generation request use the same ID.
    products.value = rows.map(row => ({
      ...row,
      key: String(row?.key || row?.optionKey || ''),
      optionKey: String(row?.optionKey || row?.key || ''),
      name: String(row?.name || row?.productName || ''),
    })).filter(row => row.key && row.name)
  } catch { products.value = [] } finally { productLoading.value = false }
}
async function loadHistory() {
  historyLoading.value = true
  try {
    conversations.value = await api<Conversation[]>('/api/creative/ai/conversations') || []
    historyPage.value = 1
  } catch (error: any) { notify(error.message || '读取历史对话失败', 'error') } finally { historyLoading.value = false }
}
function resetConversationView() {
  clearReferencePreview()
  messages.value = []
  messageCounter.value = 0
  projectId.value = null
  versionId.value = null
  brief.value = {}
  quickReplies.value = []
  chatStage.value = 'need_product'
  generatedAssetId.value = null
  generatedPreviewUrl.value = ''
  simulationAssetId.value = null
  simulationPreviewUrl.value = ''
  multiviewImages.value = []
  bundleId.value = null
  bundleStatus.value = ''
  bundleComment.value = ''
  bundleRefreshSequence.value += 1
  bundleRefreshing.value = false
  modelTask.value = null
  modelReviewStatus.value = ''
  modelReviewComment.value = ''
  replacementOpen.value = false
  replacementPrompt.value = ''
  activeView.value = 'chat'
}
function initialReplies() {
  const categories = [...new Map(products.value.filter(item => item.categoryKey).map(item => [item.categoryKey, item.categoryName || item.categoryKey])).entries()]
    .slice(0, 7).map(([value, label]) => ({ type: 'category', value: String(value), label: String(label) }))
  quickReplies.value = [...categories, { type: 'text', label: '我有一个具体想法', value: '' }, { type: 'upload', label: '上传参考图片', value: '' }]
}
function applyBrief(next: Record<string, any> | undefined) {
  if (!next) return
  const merged = { ...brief.value, ...next }
  if (next.productType && !next.productName) merged.productName = next.productType
  // Product identity is assigned by the server and must survive later chat
  // turns whose brief payload does not include these canonical fields.
  const nextProductNo = String(next.productNo || '').trim()
  const nextProductId = Number(next.productId)
  if (nextProductNo) merged.productNo = nextProductNo
  else if (brief.value.productNo) merged.productNo = brief.value.productNo
  if (Number.isFinite(nextProductId) && nextProductId > 0) merged.productId = nextProductId
  else if (brief.value.productId) merged.productId = brief.value.productId
  brief.value = merged
  if (next.referenceAssetId) {
    const referenceAssetId = Number(next.referenceAssetId)
    void previewForAsset(referenceAssetId).then(url => {
      // A preview request can finish after the user switches conversations.
      // Only apply it while the same reference is still active.
      if (url && Number(brief.value.referenceAssetId) === referenceAssetId) selectedReferenceUrl.value = url
    })
  }
}
function productIdentityFrom(...sources: any[]): ProductIdentity {
  let productNo = ''
  let productId: number | undefined
  for (const source of sources.flatMap(value => Array.isArray(value) ? value : [value])) {
    if (!source || typeof source !== 'object') continue
    if (!productNo) productNo = String(source.productNo || '').trim()
    if (!productId) {
      const value = Number(source.productId)
      if (Number.isFinite(value) && value > 0) productId = value
    }
    if (productNo && productId) break
  }
  return { ...(productNo ? { productNo } : {}), ...(productId ? { productId } : {}) }
}
function applyProductIdentity(...sources: any[]): ProductIdentity {
  const identity = productIdentityFrom(...sources)
  if (identity.productNo || identity.productId) {
    applyBrief(identity)
    const conversation = conversations.value.find(item => item.id === activeConversationId.value)
    if (conversation) Object.assign(conversation, identity)
  }
  return identity
}
function currentProductIdentity(): ProductIdentity {
  return {
    ...(canonicalProductNo.value ? { productNo: canonicalProductNo.value } : {}),
    ...(canonicalProductId.value ? { productId: canonicalProductId.value } : {}),
  }
}
function eventPayload(event: any) { return event?.payload && typeof event.payload === 'object' ? event.payload : {} }
function updateBundleActionReplies() {
  if (!bundleId.value) return
  if (bundleStatus.value === 'approved') {
    quickReplies.value = [{ type: 'bundle_production', label: '申请打样' }, { type: 'model', label: '继续生成 3D' }]
    chatStage.value = 'multiview_approved'
  } else if (bundleStatus.value === 'review') {
    quickReplies.value = [{ type: 'works', label: '查看订单和作品' }]
    chatStage.value = 'multiview_review'
  } else if (bundleStatus.value === 'rejected') {
    quickReplies.value = [{ type: 'bundle_review', label: '重新提交审核' }]
    chatStage.value = 'multiview_rejected'
  } else {
    quickReplies.value = [{ type: 'bundle_review', label: '提交生产模拟图审核' }]
    chatStage.value = 'multiview_ready'
  }
}
async function refreshBundleReviewStatus(options: { notify?: boolean } = {}) {
  if (bundleRefreshing.value) return
  const refreshSequence = ++bundleRefreshSequence.value
  const conversationAtStart = activeConversationId.value
  const currentId = Number(bundleId.value || 0)
  const currentInputAssetId = Number(generatedAssetId.value || 0)
  if (!currentId && !currentInputAssetId) return
  bundleRefreshing.value = true
  try {
    const rows = await api<any[]>('/api/creative/ai/consumer-multiview-bundles/my') || []
    const bundle = rows.find(item => {
      const id = Number(item?.id || item?.bundleId || 0)
      return (currentId > 0 && id === currentId)
        || (!currentId && currentInputAssetId > 0 && Number(item?.inputAssetId || 0) === currentInputAssetId)
    })
    if (!bundle) return
    // A user can switch history while the request is in flight. Never apply
    // the previous conversation's review result to the newly selected one.
    if (refreshSequence !== bundleRefreshSequence.value || conversationAtStart !== activeConversationId.value) return
    const resolvedBundleId = Number(bundle.id || bundle.bundleId) || currentId
    const resolvedBundleStatus = String(bundle.status || 'draft')
    const resolvedBundleComment = String(bundle.reviewComment || '')
    let nextImages: MultiViewImage[] | null = null
    if (Array.isArray(bundle.images) && bundle.images.length) {
      nextImages = bundle.images
        .filter((item: any) => Number(item?.assetId) > 0)
        .map((item: any) => ({ ...item, assetId: Number(item.assetId), previewUrl: item.previewUrl || '' }))
      await Promise.all(nextImages.map(async item => {
        if (!item.previewUrl) item.previewUrl = await previewForAsset(item.assetId)
      }))
    }
    const simulation = bundle.simulationImage
    let nextSimulationAssetId: number | null = null
    let nextSimulationPreviewUrl = ''
    if (simulation?.assetId) {
      nextSimulationAssetId = Number(simulation.assetId)
      nextSimulationPreviewUrl = imageUrl(simulation) || await previewForAsset(nextSimulationAssetId)
    }
    if (refreshSequence !== bundleRefreshSequence.value || conversationAtStart !== activeConversationId.value) return
    applyProductIdentity(bundle, simulation, nextImages || [])
    bundleId.value = resolvedBundleId || bundleId.value
    bundleStatus.value = resolvedBundleStatus
    bundleComment.value = resolvedBundleComment
    if (nextImages) multiviewImages.value = nextImages
    if (nextSimulationAssetId) {
      simulationAssetId.value = nextSimulationAssetId
      simulationPreviewUrl.value = nextSimulationPreviewUrl
    }
    updateBundleActionReplies()
    if (options.notify !== false) notify(resolvedBundleStatus === 'approved' ? '审核已通过，可以申请打样' : `生产模拟图审核状态：${statusText(resolvedBundleStatus)}`)
  } catch (error: any) {
    if (refreshSequence === bundleRefreshSequence.value && options.notify !== false) {
      notify(error.message || '生产模拟图审核状态暂时无法读取', 'error')
    }
  } finally {
    if (refreshSequence === bundleRefreshSequence.value) bundleRefreshing.value = false
  }
}
async function restoreDetail(detail: any) {
  resetConversationView()
  activeConversationId.value = Number(detail?.id) || null
  projectId.value = Number(detail?.projectId) || null
  versionId.value = Number(detail?.versionId) || null
  applyProductIdentity(detail, detail?.brief)
  const events = Array.isArray(detail?.events) ? detail.events : []
  for (const event of events) {
    const payload = eventPayload(event)
    applyProductIdentity(payload, payload.brief, payload.result, payload.simulationImage, payload.images)
    if (event.eventType === 'chat_state') applyBrief(payload)
    if (event.eventType === 'chat_user_message' && (payload.message || payload.action?.label)) addMessage('user', payload.message || payload.action.label)
    if (event.eventType === 'chat_assistant_message') {
      applyBrief(payload.brief)
      if (payload.text) assistant(payload.text)
      if (Array.isArray(payload.quickReplies)) quickReplies.value = payload.quickReplies
      if (payload.stage) chatStage.value = payload.stage
    }
    if (event.eventType === 'image_generated' || event.eventType === 'image_refined') {
      const id = Number(payload.generatedAssetId || payload.assetId)
      if (id) { generatedAssetId.value = id; generatedPreviewUrl.value = String(payload.previewUrl || '') || await previewForAsset(id); activeView.value = 'result' }
    }
    if (event.eventType === 'multiview_generated') {
      const images = Array.isArray(payload.images) ? payload.images : []
      multiviewImages.value = images.filter((item: any) => item.assetId).map((item: any) => ({ ...item, assetId: Number(item.assetId), previewUrl: item.previewUrl || '' }))
      await Promise.all(multiviewImages.value.map(async item => { if (!item.previewUrl) item.previewUrl = await previewForAsset(item.assetId) }))
      simulationAssetId.value = Number(payload.simulationAssetId || payload.simulationImage?.assetId) || null
      if (simulationAssetId.value) simulationPreviewUrl.value = await previewForAsset(simulationAssetId.value)
      bundleId.value = Number(payload.bundleId) || null
      bundleStatus.value = String(payload.bundleStatus || '')
      activeView.value = 'result'
    }
    if (event.eventType === 'model_submitted' || event.eventType === 'model_completed' || event.eventType === 'model_failed') {
      modelReviewStatus.value = String(payload.assetStatus || modelReviewStatus.value || '')
      modelReviewComment.value = String(payload.reviewComment || modelReviewComment.value || '')
      modelTask.value = { jobId: Number(payload.modelJobId), status: event.eventType === 'model_completed' ? 'succeeded' : event.eventType === 'model_failed' ? 'failed' : String(payload.status || 'running'), progress: Number(payload.progress || 0), assetId: Number(payload.assetId || payload.modelAssetId) || null, errorMessage: payload.errorMessage || '', ...productIdentityFrom(payload) }
      activeView.value = 'result'
    }
    if (event.eventType === 'model_review_submitted') {
      modelReviewStatus.value = String(payload.assetStatus || 'review')
      modelReviewComment.value = String(payload.reviewComment || '')
      activeView.value = 'result'
    }
  }
  if (!messages.value.length) addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  if (!quickReplies.value.length && activeView.value === 'chat') initialReplies()
  if (bundleId.value || generatedAssetId.value || multiviewImages.value.length >= 3) {
    await refreshBundleReviewStatus({ notify: false })
  }
  if (modelTask.value && ['running', 'queued'].includes(modelTask.value.status)) scheduleModelPolling()
  if (modelTask.value?.assetId && modelTask.value.status === 'succeeded') void refreshModelReviewStatus(modelTask.value.assetId)
}
async function selectConversation(id: number) {
  if (!id || sending.value || busy.value) return
  try { await restoreDetail(await api(`/api/creative/ai/conversations/${id}`)) } catch (error: any) { notify(error.message || '打开历史对话失败', 'error') }
}
async function createConversation() {
  if (sending.value || busy.value) return
  try {
    const created = await api<Conversation>('/api/creative/ai/conversations', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}) })
    conversations.value = [created, ...conversations.value.filter(item => item.id !== created.id)]
    historyPage.value = 1
    await selectConversation(created.id)
  } catch (error: any) { notify(error.message || '新建对话失败', 'error') }
}
async function ensureActiveConversation() {
  if (activeConversationId.value) return true
  await createConversation()
  return Boolean(activeConversationId.value)
}
async function removeConversation(item: Conversation, event?: Event) {
  event?.stopPropagation()
  if (!window.confirm('只删除这条历史对话？生成的作品、模型和订单不会删除。')) return
  try {
    await api(`/api/creative/ai/conversations/${item.id}`, { method: 'DELETE' })
    conversations.value = conversations.value.filter(row => row.id !== item.id)
    if (activeConversationId.value === item.id) { activeConversationId.value = null; resetConversationView(); addMessage('assistant', '已删除这条历史对话。作品和订单仍保留在你的账户中。'); initialReplies() }
    notify('历史对话已删除，生成作品未受影响')
  } catch (error: any) { notify(error.message || '删除历史对话失败', 'error') }
}
async function saveEvent(step: string, eventType: string, payload: Record<string, any> = {}) {
  if (!activeConversationId.value) return
  const identity = productIdentityFrom(payload, currentProductIdentity())
  if (identity.productNo || identity.productId) applyProductIdentity(identity)
  const persistedPayload = { ...payload, ...currentProductIdentity() }
  try { await api(`/api/creative/ai/conversations/${activeConversationId.value}/events`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ step, eventType, payload: persistedPayload }) }) } catch { /* persistence must not interrupt the creation turn */ }
}
function thinkingLabel(action?: QuickReply, message = '') {
  if (['product', 'category'].includes(String(action?.type))) return '正在整理产品方向'
  if (String(action?.type) === 'material') return '正在匹配材质与工艺'
  if (String(action?.type) === 'size') return '正在核对成品尺寸与比例'
  if (action?.type === 'image' || /图片|照片|草图|参考图/.test(message)) return '正在读取参考图片'
  return '正在理解你的想法'
}
async function sendChat(message = chatInput.value, action?: QuickReply, options: { skipUser?: boolean } = {}) {
  const text = String(message || '').trim()
  if (sending.value || busy.value || inputLocked.value && !action) return
  if (!await ensureActiveConversation()) return
  if (!options.skipUser && (text || action?.label)) addMessage('user', text || action?.label || '')
  chatInput.value = ''
  sending.value = true
  thinking.value = true
  try {
    const result = await api<any>(`/api/creative/ai/conversations/${activeConversationId.value}/chat`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ message: text, action: action ? { type: action.type, value: action.value, label: action.label } : undefined }) })
    if (result.session) {
      projectId.value = Number(result.session.projectId) || projectId.value
      versionId.value = Number(result.session.versionId) || versionId.value
    }
    applyBrief(result.brief)
    chatStage.value = String(result.stage || 'understanding')
    quickReplies.value = Array.isArray(result.quickReplies) ? result.quickReplies : []
    if (result.assistantText) assistant(String(result.assistantText))
    const explicit = action?.type === 'confirm_generate' || /没有.*补充|直接.*生成|开始.*生成/.test(text)
    if (result.readyToGenerate && explicit && !generatedAssetId.value) await generateProductImage()
    else if (result.readyToGenerate && !result.generationConfirmationRequired && !generatedAssetId.value) {
      quickReplies.value = [{ type: 'confirm_generate', label: '没有补充，开始生成', value: 'confirm' }, { type: 'add_detail', label: '我还要补充', value: '' }]
      chatStage.value = 'confirm_before_image'
      assistant('生成前确认一下，还有需要补充的吗？没有的话点击“没有补充，开始生成”。')
    }
  } catch (error: any) {
    notify(error.message || '对话服务暂时不可用，当前输入已保留', 'error')
  } finally { thinking.value = false; sending.value = false }
}
async function handleReply(item: QuickReply) {
  if (sending.value || busy.value) return
  if (item.type === 'upload' || item.type === 'replace_image') { referenceInput.value?.click(); return }
  if (item.type === 'multiview') { await generateMultiView(); return }
  if (item.type === 'model') { await generateModel(); return }
  if (item.type === 'model_review') { await submitModelReview(); return }
  if (item.type === 'bundle_review') { await submitBundleReview(); return }
  if (item.type === 'bundle_production' || item.type === 'commercial') { openProductionDialog(); return }
  if (item.type === 'works') { activeView.value = 'orders'; await loadProductionRequests(); return }
  if (item.type === 'refine') { replacementOpen.value = false; quickReplies.value = []; activeView.value = 'chat'; assistant('请告诉我想保留、加强或避免的内容。'); return }
  const message = item.type === 'text' ? String(item.label || item.value || '') : ''
  await sendChat(message, item)
}
async function handleReferenceFile(file: File, replacement = false) {
  if (!file.type.startsWith('image/')) { notify('请选择 JPG、PNG 或 WEBP 图片', 'error'); return }
  if (selectedReferenceUrl.value.startsWith('blob:')) URL.revokeObjectURL(selectedReferenceUrl.value)
  selectedReferenceFile.value = file
  selectedReferenceUrl.value = URL.createObjectURL(file)
  busy.value = true
  busyText.value = '正在上传参考图片…'
  try {
    const form = new FormData()
    form.append('file', file)
    form.append('title', replacement ? '对话创作替换参考图' : '对话创作参考图')
    form.append('tags', 'C端,对话式创作,参考图')
    if (projectId.value) form.append('projectId', String(projectId.value))
    if (versionId.value) form.append('versionId', String(versionId.value))
    const result = await api<any>('/api/creative/ai/assets/upload', { method: 'POST', body: form })
    const id = Number(result?.assetId)
    if (!id) throw new Error('上传成功但没有返回作品编号')
    brief.value = { ...brief.value, mode: 'image', referenceAssetId: id, inspiration: brief.value.inspiration || '以用户上传的参考图主体、轮廓和细节为创作依据。' }
    addMessage('user', '已上传参考图片', selectedReferenceUrl.value, id)
    await saveEvent('inspiration', 'image_inspiration_uploaded', { inputAssetId: id, referenceAssetId: id })
    if (replacement) {
      replacementOpen.value = true
      activeView.value = 'result'
      quickReplies.value = []
      assistant('新参考图已上传，产品方向保持不变。请补充这次生成要求，我会调用图生图。')
    } else {
      await sendChat('', { type: 'image', value: String(id), label: '已上传参考图片' }, { skipUser: true })
    }
  } catch (error: any) { notify(error.message || '参考图片上传失败', 'error') } finally { busy.value = false; busyText.value = '之间正在处理…' }
}
function onReferenceChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) void handleReferenceFile(file, replacementOpen.value)
  ;(event.target as HTMLInputElement).value = ''
}
async function generateReplacement() {
  const note = replacementPrompt.value.trim()
  if (!note) { notify('请先输入这次改图要求', 'error'); return }
  brief.value = { ...brief.value, inspiration: note, mode: 'image' }
  replacementOpen.value = false
  quickReplies.value = []
  addMessage('user', `新参考图生成要求：${note}`)
  await saveEvent('inspiration', 'image_reference_replacement_prompt', { prompt: note, inputAssetId: brief.value.referenceAssetId, previousGeneratedAssetId: generatedAssetId.value })
  await generateProductImage(true)
}
async function pollImageJob(job: any) {
  let current = job
  const deadline = Date.now() + 45 * 60 * 1000
  while (['queued', 'running'].includes(String(current?.status || ''))) {
    if (Date.now() > deadline) throw new Error('任务仍在后台排队，请稍后到作品库查看')
    await new Promise(resolve => window.setTimeout(resolve, 2200))
    current = await api(`/api/creative/ai/image-jobs/${current.jobId}`)
    busyText.value = current.status === 'queued' ? `已进入生成队列${current.queuePosition ? `，前面还有 ${Math.max(0, current.queuePosition - 1)} 项` : ''}` : `生成中 ${Math.round(Number(current.progress || 0))}%`
  }
  if (current.status === 'failed') throw new Error(current.errorMessage || current.message || '生成失败')
  if (current.status !== 'succeeded') throw new Error(current.message || '生成状态异常')
  return current
}
async function generateProductImage(replacement = false) {
  if (!activeConversationId.value) { notify('请先新建一条创作对话', 'error'); return }
  if (!hasCompleteBrief.value || (brief.value.mode === 'image' && !brief.value.referenceAssetId)) { notify('请先完成产品、灵感、材质和成品尺寸', 'error'); return }
  if (busy.value) return
  busy.value = true
  busyText.value = replacement ? '正在依据新参考图重新生成…' : '正在提交 Seedream 产品图任务…'
  try {
    const reference = brief.value.mode === 'image' && Number(brief.value.referenceAssetId) > 0
    const payload = buildCreativeGenerationPayload({
      provider: 'ark', title: `${productName.value} · 对话创作`, rawPrompt: String(brief.value.inspiration || ''),
      prompt: String(brief.value.inspiration || `为${productName.value}设计一件适合量产打样的文创产品`), productKey: brief.value.productKey,
      productType: productName.value, productCategory: brief.value.categoryName || brief.value.categoryKey || '文创产品', material: material.value,
      productSize: productSize.value, inputAssetId: reference ? Number(brief.value.referenceAssetId) : null, refinement: replacement, refinementNote: replacement ? String(brief.value.inspiration || '') : '', projectId: projectId.value || undefined, versionId: versionId.value || undefined, queue: true,
    })
    const queued = await api<any>(reference ? '/api/creative/ai/image-to-image' : '/api/creative/ai/ark/text-to-image', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ...payload, queue: true }) })
    const result = await pollImageJob(queued)
    const identity = applyProductIdentity(result)
    const id = Number(result.assetId || result.id)
    if (!id) throw new Error('产品图没有返回作品编号')
    generatedAssetId.value = id
    generatedPreviewUrl.value = imageUrl(result) || await previewForAsset(id)
    simulationAssetId.value = null; simulationPreviewUrl.value = ''; multiviewImages.value = []; bundleId.value = null; bundleStatus.value = ''; modelTask.value = null; modelReviewStatus.value = ''; modelReviewComment.value = ''
    await saveEvent('image', replacement ? 'image_refined' : 'image_generated', { generatedAssetId: id, previewUrl: generatedPreviewUrl.value, productType: productName.value, material: material.value, productSize: productSize.value, referenceAssetId: brief.value.referenceAssetId || null, ...identity })
    activeView.value = 'result'
    quickReplies.value = [{ type: 'multiview', label: '满意，生成生产模拟图' }, { type: 'refine', label: '不满意，告诉我怎么改' }, { type: 'replace_image', label: '重新上传图片生成' }, { type: 'model', label: '生成 3D 原型' }]
    chatStage.value = 'image_ready'
    assistant('产品视觉已经生成并保存。你可以生成生产模拟图或 3D 原型，完成后即可提交审核和申请打样。')
  } catch (error: any) { notify(error.message || '产品图生成失败', 'error') } finally { busy.value = false; busyText.value = '之间正在处理…' }
}
async function generateMultiView() {
  if (!generatedAssetId.value || busy.value) { if (!generatedAssetId.value) notify('请先生成产品图', 'error'); return }
  busy.value = true; busyText.value = '正在生成一张包含正面、侧面和背面的生产模拟图…'
  try {
    const queued = await api<any>('/api/creative/ai/volcengine/seedream/multiview', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(buildCreativeGenerationPayload({ provider: 'ark', queue: true, inputAssetId: generatedAssetId.value, projectId: projectId.value || undefined, versionId: versionId.value || undefined, prompt: `将${productName.value}转为包含正面、侧面和背面的标准生产模拟图。材质：${material.value}；尺寸：${productSize.value}`, rawPrompt: String(brief.value.inspiration || ''), productKey: brief.value.productKey, productType: productName.value, productCategory: brief.value.categoryName || brief.value.categoryKey, material: material.value, productSize: productSize.value, viewCount: 3, size: '2K', watermark: true })) })
    const result = await pollImageJob(queued)
    const resultIdentity = applyProductIdentity(result, result.simulationImage, result.images)
    const images: MultiViewImage[] = (Array.isArray(result.images) ? result.images : []).filter((item: any) => item.assetId).map((item: any) => ({ ...item, assetId: Number(item.assetId), label: item.label || ({ front: '正面', left: '侧面', back: '背面' } as Record<string, string>)[item.view] || '视图' }))
    if (images.length < 3) throw new Error('生产模拟图没有完整返回三个视角')
    await Promise.all(images.map(async (item: any) => { item.previewUrl = imageUrl(item) || await previewForAsset(item.assetId) }))
    multiviewImages.value = images
    simulationAssetId.value = Number(result.simulationAssetId || result.simulationImage?.assetId) || null
    simulationPreviewUrl.value = simulationAssetId.value ? await previewForAsset(simulationAssetId.value) : imageUrl(result.simulationImage)
    const bundle = await api<any>('/api/creative/ai/consumer-multiview-bundles', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ inputAssetId: generatedAssetId.value, projectId: projectId.value || undefined, versionId: versionId.value || undefined, productKey: brief.value.productKey, productName: productName.value, material: material.value, productSize: productSize.value, viewCount: 3, images: images.map((item: any) => ({ view: item.view, assetId: item.assetId, label: item.label })), ...(simulationAssetId.value ? { simulationAssetId: simulationAssetId.value } : {}) }) })
    const identity = applyProductIdentity(bundle, resultIdentity, images)
    bundleId.value = Number(bundle.id || bundle.bundleId); bundleStatus.value = String(bundle.status || 'draft'); bundleComment.value = String(bundle.reviewComment || '')
    await saveEvent('multiview', 'multiview_generated', { inputAssetId: generatedAssetId.value, bundleId: bundleId.value, bundleNo: bundle.bundleNo, bundleStatus: bundleStatus.value, simulationAssetId: simulationAssetId.value, images: images.map((item: any) => ({ view: item.view, assetId: item.assetId, label: item.label, ...productIdentityFrom(item) })), ...identity })
    updateBundleActionReplies(); activeView.value = 'result'
    assistant('生产模拟图已保存为一个作品包：完整横向图用于查看，三个视角切片用于建模和审核。请先提交整包审核。')
  } catch (error: any) { notify(error.message || '生产模拟图生成失败', 'error') } finally { busy.value = false; busyText.value = '之间正在处理…' }
}
async function submitBundleReview() {
  if (!bundleId.value || busy.value) return
  busy.value = true; busyText.value = '正在提交生产模拟图审核…'
  try {
    const result = await api<any>(`/api/creative/ai/consumer-multiview-bundles/${bundleId.value}/submit-review`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ purpose: productionForm.value.purpose, museumId: productionForm.value.museumId || undefined, projectId: projectId.value || undefined, versionId: versionId.value || undefined, note: '由网站对话式创作提交的生产模拟图作品包' }) })
    bundleStatus.value = String(result.status || 'review'); bundleComment.value = String(result.reviewComment || '')
    await saveEvent('multiview', 'multiview_review_submitted', { bundleId: bundleId.value, status: bundleStatus.value, ...currentProductIdentity() })
    updateBundleActionReplies()
    notify(result.message || '生产模拟图已提交审核')
  } catch (error: any) { notify(error.message || '提交审核失败', 'error') } finally { busy.value = false; busyText.value = '之间正在处理…' }
}
function setModelActionReplies() {
  if (modelTask.value?.status !== 'succeeded') return
  if (modelReviewStatus.value === 'approved') {
    quickReplies.value = [{ type: 'bundle_production', label: '申请打样' }, { type: 'works', label: '查看订单和作品' }]
  } else if (modelReviewStatus.value === 'review') {
    quickReplies.value = [{ type: 'works', label: '查看订单和作品' }]
  } else {
    quickReplies.value = [{ type: 'model_review', label: '提交 3D 建模审核' }, { type: 'works', label: '查看订单和作品' }]
  }
}
async function refreshModelReviewStatus(assetId: number) {
  if (!assetId) return
  try {
    const rows = await api<any[]>('/api/creative/ai/assets?type=model&size=300') || []
    const asset = rows.find(row => Number(row?.id) === Number(assetId))
    if (!asset) return
    modelReviewStatus.value = String(asset.status || modelReviewStatus.value || 'draft')
    modelReviewComment.value = String(asset.reviewComment || modelReviewComment.value || '')
    setModelActionReplies()
  } catch { /* review status can be refreshed from the works page */ }
}
async function submitModelReview() {
  const assetId = Number(modelTask.value?.assetId || 0)
  if (!assetId) { notify('3D 模型尚未保存完成，请稍后再试', 'error'); return }
  if (['review', 'approved'].includes(modelReviewStatus.value)) {
    notify(modelReviewStatus.value === 'approved' ? '该 3D 模型已审核通过' : '该 3D 模型正在审核中')
    return
  }
  if (busy.value) return
  busy.value = true
  busyText.value = '正在提交 3D 建模审核…'
  try {
    const result = await api<any>(`/api/creative/ai/consumer-assets/${assetId}/submit-review`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ purpose: 'personal', projectId: projectId.value || undefined, versionId: versionId.value || undefined, note: '由网站对话式创作提交的 3D 建模审核' }),
    })
    modelReviewStatus.value = String(result.status || 'review')
    modelReviewComment.value = String(result.reviewComment || '')
    await saveEvent('model', 'model_review_submitted', { assetId, assetStatus: modelReviewStatus.value, projectId: projectId.value, versionId: versionId.value, ...currentProductIdentity() })
    chatStage.value = 'model_review'
    setModelActionReplies()
    notify(result.message || '3D 模型已提交审核')
  } catch (error: any) { notify(error.message || '提交 3D 建模审核失败', 'error') } finally { busy.value = false; busyText.value = '之间正在处理…' }
}
async function generateModel() {
  if (!generatedAssetId.value || busy.value) { if (!generatedAssetId.value) notify('请先生成产品图', 'error'); return }
  if (multiviewImages.value.length >= 3 && bundleStatus.value && bundleStatus.value !== 'approved') { notify('三视图作品包需先通过审核，再继续建模', 'error'); return }
  busy.value = true; busyText.value = '正在提交 GLB 3D 建模任务…'
  try {
    const useMulti = multiviewImages.value.length >= 3 && bundleStatus.value === 'approved'
    const result = await api<any>('/api/creative/ai/tripo/generate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title: `${productName.value} · 3D 原型`, prompt: String(brief.value.inspiration || productName.value), rawPrompt: String(brief.value.inspiration || productName.value), mode: useMulti ? 'multiview_to_model' : 'image_to_model', inputAssetId: generatedAssetId.value, projectId: projectId.value || undefined, versionId: versionId.value || undefined, productKey: brief.value.productKey, productCategory: brief.value.categoryName || brief.value.categoryKey, material: material.value, productSize: productSize.value, materialLabel: material.value, materialPrompt: `manufacturing material: ${material.value}`, multiviewAssetIds: useMulti ? Object.fromEntries(multiviewImages.value.map(item => [item.view, item.assetId])) : undefined, exportFormats: 'GLB', texture: true, pbr: true, textureQuality: 'extreme', geometryQuality: 'detailed', textureAlignment: 'original_image', orientation: 'align_image', autoSize: true, imageAutofix: true, exportUv: true, faceLimit: 2000000 }) })
    const jobId = Number(result.jobId)
    if (!jobId) throw new Error('3D 服务没有返回任务编号')
    modelReviewStatus.value = ''
    modelReviewComment.value = ''
    const identity = applyProductIdentity(result)
    modelTask.value = { jobId, status: String(result.status || 'running'), progress: Number(result.progress || 0), assetId: Number(result.assetId) || null, ...identity }
    await saveEvent('model', 'model_submitted', { modelJobId: jobId, modelAssetId: result.assetId, inputAssetId: generatedAssetId.value, status: modelTask.value.status, ...identity })
    activeView.value = 'result'; quickReplies.value = [{ type: 'works', label: '查看订单和作品' }]; chatStage.value = 'model_running'; assistant('3D 建模任务已提交，完成后会出现在作品与订单状态中。')
    scheduleModelPolling()
    if (modelTask.value.status === 'succeeded') {
      await refreshModelReviewStatus(modelTask.value.assetId || 0)
      chatStage.value = 'model_ready'
      setModelActionReplies()
    }
  } catch (error: any) { notify(error.message || '3D 建模提交失败', 'error') } finally { busy.value = false; busyText.value = '之间正在处理…' }
}
function stopModelPolling() { if (modelPollTimer.value) window.clearTimeout(modelPollTimer.value); modelPollTimer.value = null }
function scheduleModelPolling() {
  stopModelPolling()
  const poll = async () => {
    if (!modelTask.value || ['succeeded', 'failed'].includes(String(modelTask.value.status))) return
    try {
      const latest = await api<any>(`/api/creative/ai/tripo/tasks/${modelTask.value.jobId}`)
      const identity = applyProductIdentity(latest)
      modelTask.value = { ...modelTask.value, ...latest, progress: Number(latest.progress || 0), assetId: Number(latest.assetId || latest.modelAssetId || modelTask.value.assetId) || null, ...identity }
      modelReviewStatus.value = String(latest.assetStatus || modelReviewStatus.value || 'draft')
      modelReviewComment.value = String(latest.reviewComment || modelReviewComment.value || '')
      if (modelTask.value.status === 'succeeded') {
        if (modelTask.value.assetId) await saveEvent('model', 'model_completed', { modelJobId: modelTask.value.jobId, assetId: modelTask.value.assetId, assetStatus: modelReviewStatus.value, progress: 100, ...currentProductIdentity() })
        chatStage.value = 'model_ready'; setModelActionReplies(); assistant(modelReviewStatus.value === 'approved' ? '3D 模型已经生成并通过审核，可以申请打样。' : '3D 模型已经生成并保存，请先提交 3D 建模审核，审核通过后才能申请打样。')
      } else if (modelTask.value.status === 'failed') {
        await saveEvent('model', 'model_failed', { modelJobId: modelTask.value.jobId, errorMessage: modelTask.value.errorMessage || modelTask.value.error, ...currentProductIdentity() })
        assistant('3D 建模没有完成，失败原因已保存，可以检查产品图后重新提交。')
      } else modelPollTimer.value = window.setTimeout(poll, 5000)
    } catch { modelPollTimer.value = window.setTimeout(poll, 7000) }
  }
  void poll()
}
function openProductionDialog(request?: any) {
  selectedRequest.value = request || { id: null, bundleId: bundleId.value, assetId: modelTask.value?.assetId || generatedAssetId.value, title: productName.value }
  productionForm.value = { requestType: 'sample', purpose: 'personal', quantity: 1, recipientName: '', recipientPhone: '', recipientAddress: '', museumId: '', museumName: '', note: '' }
  productionDialogOpen.value = true
}
function selectMuseum() {
  const museum = museums.value.find(item => String(item.id) === String(productionForm.value.museumId))
  productionForm.value.museumName = museum?.name || ''
}
async function submitProduction() {
  const target = selectedRequest.value
  if (!target || productionSubmitting.value) return
  if (productionForm.value.purpose === 'museum_sale' && !productionForm.value.museumId) { notify('售卖路径请选择博物馆或景区', 'error'); return }
  productionSubmitting.value = true
  try {
    const body: Record<string, any> = { ...currentProductIdentity(), assetId: target.assetId || undefined, bundleId: target.bundleId || bundleId.value || undefined, projectId: projectId.value || undefined, versionId: versionId.value || undefined, requestType: 'sample', title: target.title || productName.value, quantity: Math.max(1, Number(productionForm.value.quantity || 1)), purpose: productionForm.value.purpose, recipientName: productionForm.value.recipientName, recipientPhone: productionForm.value.recipientPhone, recipientAddress: productionForm.value.recipientAddress, note: productionForm.value.note, ...(productionForm.value.museumId ? { museumDistribution: [{ museumId: productionForm.value.museumId, museumName: productionForm.value.museumName, quantity: Math.max(1, Number(productionForm.value.quantity || 1)) }] } : {}) }
    const data = await api<any>('/api/creative/ai/consumer-production/submit', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    productionDialogOpen.value = false; notify(data.message || '打样申请已提交，等待后台报价'); await loadProductionRequests(); activeView.value = 'orders'
  } catch (error: any) { notify(error.message || '打样申请提交失败', 'error') } finally { productionSubmitting.value = false }
}
async function loadProductionRequests() { try { productionRequests.value = await api<any[]>('/api/creative/ai/consumer-production/my') || [] } catch { productionRequests.value = [] } }
async function loadMuseums() { try { museums.value = await api<any[]>('/api/creative/ai/consumer-production/museums') || [] } catch { museums.value = [] } }
function stopPaymentPolling() { if (paymentTimer.value) window.clearInterval(paymentTimer.value); paymentTimer.value = null }
function paymentTargetMatches(request: any) {
  return Boolean(paymentRequest.value && request?.id
    && String(paymentRequest.value.id) === String(request.id)
    && Boolean(paymentRequest.value.professionalPayment) === Boolean(request.professionalPayment))
}
function paymentIsTerminal(status?: string) {
  return ['paid', 'closed', 'cancelled', 'failed', 'expired', 'manual_review', 'payment_exception', 'refund_exception'].includes(String(status || ''))
}
const paymentLocked = computed(() => Boolean(paymentDialogOpen.value && (paymentLoading.value || paymentClosing.value || (paymentOrder.value && !paymentIsTerminal(paymentOrder.value.status)))))
function leavePaymentDialog(options: { keepOrder?: boolean } = {}) {
  stopPaymentPolling()
  paymentDialogOpen.value = false
  activeView.value = paymentReturnView.value
  if (!options.keepOrder) {
    paymentOrder.value = null
    paymentQrUrl.value = ''
    paymentRequest.value = null
    paymentError.value = ''
  }
}
async function closePaymentDialog() {
  if (paymentLoading.value || paymentClosing.value) {
    notify('正在准备支付订单，请稍候', 'error')
    return
  }
  const order = paymentOrder.value
  if (!order?.orderNo) {
    leavePaymentDialog()
    return
  }
  const status = String(order.status || '')
  if (status === 'pending') {
    if (order.canClose === false) {
      notify('当前支付结果正在核对，请稍后刷新订单状态', 'error')
      return
    }
    if (!window.confirm('尚未支付，确定取消并关闭这笔支付订单吗？打样申请和作品不会删除。')) return
    paymentClosing.value = true
    try {
      const closed = await api<any>(`/api/payments/orders/${encodeURIComponent(order.orderNo)}/close`, { method: 'POST' })
      paymentOrder.value = closed
      notify('未支付订单已关闭，打样申请仍保留')
      leavePaymentDialog()
    } catch (error: any) {
      // A payment can complete while the close request is in flight. Query the
      // authoritative state before deciding whether it is safe to leave.
      await refreshPayment()
      const latestStatus = String(paymentOrder.value?.status || '')
      if (latestStatus === 'paid') {
        notify('打样费已支付，订单进入生产中')
        leavePaymentDialog()
      } else if (['closed', 'cancelled', 'failed', 'expired'].includes(latestStatus)) {
        leavePaymentDialog()
      } else {
        notify(error.message || '支付订单暂时无法关闭，请稍后重试', 'error')
      }
    } finally {
      paymentClosing.value = false
    }
    return
  }
  if (status === 'paid') {
    await Promise.all([loadProductionRequests(), loadProfessionalSubmissions()])
    leavePaymentDialog()
    return
  }
  // An exception/manual-review result must remain visible in the order list;
  // it cannot be locally closed because the official payment result is still
  // authoritative.
  leavePaymentDialog({ keepOrder: ['manual_review', 'payment_exception', 'refund_exception'].includes(status) })
}
function reopenPaymentDialog() {
  if (!paymentOrder.value) return
  paymentDialogOpen.value = true
  if (!paymentIsTerminal(paymentOrder.value.status)) {
    stopPaymentPolling()
    paymentTimer.value = window.setInterval(refreshPayment, 2200)
    void refreshPayment()
  }
}
function handlePaymentBeforeUnload(event: BeforeUnloadEvent) {
  if (!paymentLocked.value) return
  event.preventDefault()
  event.returnValue = ''
}
async function refreshPayment() {
  if (!paymentOrder.value?.orderNo) return
  try {
    const latest = await api<any>(`/api/payments/orders/${encodeURIComponent(paymentOrder.value.orderNo)}`)
    const previousIntent = paymentIntent.value
    paymentOrder.value = latest
    if (latest.status === 'paid') {
      stopPaymentPolling()
      paymentIntent.value = 'paid'
      paymentHint.value = '打样费已到账，申请已进入生产流程。现在可以返回订单查看进度。'
      if (previousIntent !== 'paid') {
        await Promise.all([loadProductionRequests(), loadProfessionalSubmissions()])
        notify('打样费已支付，订单进入生产中')
      }
    }
    if (['payment_exception', 'refund_exception', 'manual_review'].includes(String(latest.status))) {
      stopPaymentPolling()
      paymentIntent.value = 'exception'
      paymentHint.value = '支付结果正在与微信官方核对，请勿重复支付。稍后可重新打开此订单查询。'
    }
    if (['closed', 'cancelled', 'failed', 'expired'].includes(String(latest.status))) {
      stopPaymentPolling()
      paymentIntent.value = 'failed'
      paymentHint.value = latest.status === 'expired' ? '支付订单已过期，请重新发起支付。' : latest.status === 'cancelled' ? '支付订单已取消，请重新发起支付。' : '支付订单未完成，请重新发起支付。'
    }
  } catch { /* polling is best effort */ }
}
async function paySample(request: any) {
  if (!request?.id || !['unpaid', 'pending'].includes(String(request.samplePaymentStatus || 'unpaid'))) return
  if (paymentDialogOpen.value && paymentLocked.value) {
    if (paymentTargetMatches(request) && paymentIntent.value === 'awaiting') return
    notify('请先完成当前打样费支付，再选择其他订单', 'error')
    return
  }
  if (paymentOrder.value && paymentTargetMatches(request) && !paymentDialogOpen.value && !paymentIsTerminal(paymentOrder.value.status)) {
    reopenPaymentDialog()
    return
  }
  paymentRequest.value = request
  paymentReturnView.value = activeView.value
  paymentOrder.value = null
  paymentQrUrl.value = ''
  paymentError.value = ''
  paymentIntent.value = 'awaiting'
  paymentHint.value = '正在创建支付订单，请稍候…'
  paymentDialogOpen.value = true
  paymentLoading.value = true
  try {
    const endpoint = request.professionalPayment ? '/api/payments/professional-submission-sample-orders' : '/api/payments/sample-orders'
    // H5 displays a native WeChat QR code. JSAPI returns only paymentParams
    // for wx.requestPayment and therefore has no codeUrl to render here.
    const channel = 'wechat'
    paymentOrder.value = await api<any>(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(request.professionalPayment ? { submissionId: String(request.id), channel } : { requestId: String(request.id), channel }) })
    const codeUrl = String(paymentOrder.value?.codeUrl || '')
    if (!codeUrl) throw new Error('微信支付未返回二维码，请检查支付商户配置')
    paymentQrUrl.value = await QRCode.toDataURL(codeUrl, { width: 300, margin: 1 })
    paymentIntent.value = paymentOrder.value.status === 'paid' ? 'paid' : 'awaiting'
    paymentHint.value = paymentOrder.value.status === 'paid' ? '打样费已到账，申请已进入生产流程。' : '请使用微信扫码支付。支付回调确认后，申请会自动进入生产中。'
    stopPaymentPolling()
    if (!paymentIsTerminal(paymentOrder.value.status)) paymentTimer.value = window.setInterval(refreshPayment, 2200)
    await refreshPayment()
  } catch (error: any) {
    paymentError.value = error.message || '创建支付订单失败'
    paymentIntent.value = 'failed'
    paymentHint.value = paymentError.value
    stopPaymentPolling()
    notify(paymentError.value, 'error')
  } finally { paymentLoading.value = false }
}
async function loadProfessionalSubmissions() { try { professionalSubmissions.value = await api<any[]>('/api/creative/ai/consumer-professional-submissions/my') || [] } catch { professionalSubmissions.value = [] } }
function chooseProfessionalFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0] || null
  if (file && !/\.zip$/i.test(file.name)) {
    notify('专业审核仅支持 ZIP 作品包', 'error')
    ;(event.target as HTMLInputElement).value = ''
    professionalSubmissionFile.value = null
    return
  }
  if (file && file.size > 100 * 1024 * 1024) {
    notify('ZIP 作品包不能超过 100MB', 'error')
    ;(event.target as HTMLInputElement).value = ''
    professionalSubmissionFile.value = null
    return
  }
  professionalSubmissionFile.value = file
  if (file && !professionalSubmissionTitle.value) professionalSubmissionTitle.value = file.name.replace(/\.zip$/i, '')
}
async function submitProfessionalSubmission() {
  const file = professionalSubmissionFile.value
  if (!file) { notify('请先选择 ZIP 作品包', 'error'); return }
  if (!/\.zip$/i.test(file.name)) { notify('专业审核仅支持 ZIP 作品包', 'error'); return }
  if (file.size > 100 * 1024 * 1024) { notify('ZIP 作品包不能超过 100MB', 'error'); return }
  professionalSubmissionBusy.value = true
  try {
    const form = new FormData()
    form.append('file', file)
    form.append('title', professionalSubmissionTitle.value.trim() || file.name.replace(/\.zip$/i, ''))
    form.append('note', professionalSubmissionNote.value.trim())
    form.append('purpose', 'personal')
    // Keep a professional ZIP attached to the product created in this
    // conversation. The server derives the canonical product number from
    // assetId and validates ownership; never derive it from a file name.
    const currentAssetId = Number(generatedAssetId.value || 0)
    if (Number.isFinite(currentAssetId) && currentAssetId > 0) form.append('assetId', String(currentAssetId))
    const currentProductNo = String(brief.value.productNo || '').trim()
    if (currentProductNo) form.append('productNo', currentProductNo)
    if (canonicalProductId.value) form.append('productId', String(canonicalProductId.value))
    await api('/api/creative/ai/consumer-professional-submissions', { method: 'POST', body: form })
    professionalSubmissionFile.value = null; professionalSubmissionTitle.value = ''; professionalSubmissionNote.value = ''; await loadProfessionalSubmissions(); notify('专业作品包已提交审核')
  } catch (error: any) { notify(error.message || '提交专业作品包失败', 'error') } finally { professionalSubmissionBusy.value = false }
}
async function loadInitial() {
  await Promise.all([loadProducts(), loadHistory(), loadProductionRequests(), loadMuseums(), creatorProfile.value === 'professional' ? loadProfessionalSubmissions() : Promise.resolve()])
  if (conversations.value.length) await selectConversation(conversations.value[0].id)
  else {
    // Match the mini-program entry: the first visit is immediately usable,
    // while the sidebar still records a normal resumable session.
    await createConversation()
    if (!activeConversationId.value) {
      resetConversationView()
      addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
      initialReplies()
    }
  }
}
onMounted(() => {
  window.addEventListener('beforeunload', handlePaymentBeforeUnload)
  void loadInitial()
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handlePaymentBeforeUnload)
  stopPaymentPolling(); stopModelPolling(); clearReferencePreview()
})
</script>

<template>
  <main class="conversation-workspace">
    <header class="workspace-header">
      <div class="brand"><img :src="andTasteLogo" alt="之间味道" /><div><strong>之间味道</strong><span>对话式创作</span></div></div>
      <div class="header-copy"><span>CONVERSATIONAL CREATIVE STUDIO</span><h1>把一句灵感，推进成一件可生产的作品。</h1><p>网站端与小程序使用同一套对话、作品、审核、打样和生产流程。</p></div>
      <div class="header-actions"><div class="creator-switch" role="group" aria-label="创作者身份"><button :class="{ active: creatorProfile === 'amateur' }" @click="roleChanged('amateur')">业余创作者</button><button :class="{ active: creatorProfile === 'professional' }" @click="roleChanged('professional')">专业创作者</button></div><button class="icon-action" title="退出登录" @click="emit('logout')">退出</button></div>
    </header>

    <section v-if="creatorProfile === 'professional'" class="professional-top-panel">
      <div class="professional-top-copy"><span>PROFESSIONAL CREATOR · ZIP REVIEW</span><h2>先提交专业作品包</h2><p>上传包含效果图、3D 文件、尺寸、材质、工艺说明和版权材料的 ZIP，后台审核通过后会返回报价单。</p></div>
      <div class="professional-form"><label class="zip-picker"><input ref="submissionInput" type="file" accept=".zip,application/zip" @change="chooseProfessionalFile" /><strong>{{ professionalSubmissionFile?.name || '选择 ZIP 作品包' }}</strong><small>{{ professionalSubmissionFile ? `${(professionalSubmissionFile.size / 1024 / 1024).toFixed(1)} MB` : '最大 100MB' }}</small></label><input v-model="professionalSubmissionTitle" maxlength="200" placeholder="作品包名称" /><textarea v-model="professionalSubmissionNote" rows="2" maxlength="1000" placeholder="给审核员的说明（亮点、材质、工艺和审核重点）" /><button :disabled="professionalSubmissionBusy || !professionalSubmissionFile" @click="submitProfessionalSubmission">{{ professionalSubmissionBusy ? '正在提交…' : '提交专业作品包审核' }}</button></div>
      <div v-if="professionalSubmissions.length" class="professional-history"><strong>最近提交</strong><article v-for="item in professionalSubmissions" :key="item.id"><div><b>{{ item.title }}</b><span>{{ statusText(item.status) }} · {{ formatTime(item.createdAt) }}</span><small v-if="item.quotedSampleFeeYuan">报价：¥{{ item.quotedSampleFeeYuan }} · {{ item.quotedSampleLeadTime || '交期待确认' }}</small></div><button v-if="item.status === 'approved' && ['unpaid', 'pending'].includes(String(item.samplePaymentStatus || 'unpaid'))" :disabled="paymentLoading" @click="paySample({ ...item, professionalPayment: true })">{{ paymentTargetMatches({ ...item, professionalPayment: true }) && paymentOrder && !paymentIsTerminal(paymentOrder.status) ? '继续支付' : '支付打样费' }}</button></article></div>
    </section>

    <section class="workspace-grid">
      <aside class="history-sidebar" :class="{ collapsed: !historyPanelOpen }">
        <div class="sidebar-heading"><div><span>YOUR SESSIONS</span><h2>历史对话</h2></div><button title="收起历史对话" @click="historyPanelOpen = !historyPanelOpen">{{ historyPanelOpen ? '‹' : '›' }}</button></div>
        <template v-if="historyPanelOpen"><button class="new-session" @click="createConversation">＋ 新建对话</button><div v-if="historyLoading" class="sidebar-empty">正在读取…</div><div v-else-if="!conversations.length" class="sidebar-empty">还没有历史对话<br /><small>新建一条对话开始创作</small></div><template v-else><div class="session-list"><button v-for="item in visibleConversations" :key="item.id" :class="['session-item', { active: item.id === activeConversationId }]" @click="selectConversation(item.id)"><span class="session-mark">对</span><span class="session-copy"><strong>{{ item.productType || '未命名创作' }}</strong><small>{{ item.material || '尚未确定材质' }} · {{ formatTime(item.updatedAt) }}</small></span><i title="删除历史对话" @click="removeConversation(item, $event)">×</i></button></div><nav v-if="historyTotalPages > 1" class="history-pagination" aria-label="历史对话分页"><button :disabled="historyPage <= 1" aria-label="上一页" @click="changeHistoryPage(historyPage - 1)">‹</button><span>{{ historyPage }} / {{ historyTotalPages }}</span><button :disabled="historyPage >= historyTotalPages" aria-label="下一页" @click="changeHistoryPage(historyPage + 1)">›</button></nav></template><div class="sidebar-note">删除历史对话不会删除已生成的作品、3D 模型、审核记录或订单。</div></template>
        <div class="sidebar-links"><button :class="{ active: activeView === 'chat' }" @click="activeView = 'chat'">对话创作</button><button :class="{ active: activeView === 'result' }" @click="activeView = 'result'">当前作品</button><button :class="{ active: activeView === 'orders' }" @click="activeView = 'orders'; loadProductionRequests()">订单与进度 <em v-if="productionRequests.length">{{ productionRequests.length }}</em></button></div>
      </aside>

      <section class="chat-panel">
        <header class="chat-header"><div class="assistant-avatar">之</div><div><span>BETWEEN TASTE AI · 在线</span><h2>你的创作顾问</h2><small>{{ activeConversation ? `会话 ${activeConversation.sessionNo || activeConversation.id}` : '先新建一条创作对话' }}</small></div><div class="brief-chip"><b>{{ productName }}</b><span>{{ material || '材质待定' }} · {{ productSize || '尺寸待定' }}</span></div></header>
        <div v-if="activeView === 'chat'" ref="chatContent" class="chat-content"><div class="conversation-intro">我会先听懂你想做什么，自动绑定产品方向，再逐步确认灵感、材质和成品尺寸。确认后才开始生成，不会把每句闲聊直接塞进提示词。</div><div class="message-stream"><article v-for="item in messages" :key="item.id" :class="['message', item.role]"><div v-if="item.role === 'assistant'" class="message-avatar">之</div><div class="message-body"><div v-if="item.imageUrl" class="message-image"><img :src="item.imageUrl" alt="用户上传的参考图" /></div><p v-if="item.text">{{ item.text }}</p><small>{{ item.role === 'assistant' ? '之间 AI' : '我' }}</small></div><div v-if="item.role === 'user'" class="message-avatar user">我</div></article><article v-if="thinking" class="message assistant"><div class="message-avatar">之</div><div class="message-body thinking"><span></span><span></span><span></span><small>正在理解你的想法</small></div></article></div></div>
        <div v-else-if="activeView === 'result'" class="result-content"><div class="result-toolbar"><div><div class="result-product-identity"><span>产品号</span><strong :class="{ missing: !canonicalProductNo }">{{ canonicalProductNo || '未关联产品号' }}</strong><small v-if="canonicalProductId">内部产品 ID：{{ canonicalProductId }}</small></div><span>CREATIVE OUTPUT</span><h2>当前创作进度</h2></div><div class="result-toolbar-actions"><button v-if="bundleId" :disabled="bundleRefreshing" @click="refreshBundleReviewStatus()">{{ bundleRefreshing ? '刷新中…' : '刷新审核状态' }}</button><button @click="activeView = 'chat'">返回对话</button></div></div><section v-if="generatedPreviewUrl" class="output-card"><div class="output-image"><img :src="generatedPreviewUrl" alt="生成的产品图" /><b>产品视觉</b></div><div class="output-copy"><strong>{{ productName }}</strong><span>{{ material || '材质待定' }} · {{ productSize || '尺寸待定' }}</span></div></section><section v-if="replacementOpen" class="replacement-box"><strong>新参考图已上传，请补充改图要求</strong><textarea v-model="replacementPrompt" rows="3" placeholder="例如：保留新图主体和配色，转成当前产品的量产外观，背景简洁，不要文字。" /><button :disabled="busy || !replacementPrompt.trim()" @click="generateReplacement">根据新参考图生成</button></section><section v-if="simulationPreviewUrl || multiviewImages.length" class="output-card simulation-card"><div v-if="simulationPreviewUrl" class="output-image"><img :src="simulationPreviewUrl" alt="生产模拟图" /><b>生产模拟图</b></div><div class="view-strip"><div v-for="item in multiviewImages" :key="item.assetId"><img v-if="item.previewUrl" :src="item.previewUrl" :alt="item.label" /><span>{{ item.label }}</span></div></div><div class="review-state" :class="statusClass(bundleStatus)"><strong>生产模拟图：{{ bundleStatus === 'approved' ? '审核已通过' : bundleStatus === 'review' ? '审核中' : bundleStatus === 'rejected' ? '审核未通过' : '待提交审核' }}</strong><small v-if="bundleComment">{{ bundleComment }}</small></div></section><section v-if="modelTask" class="model-status"><div><strong>3D 原型</strong><span>{{ modelTask.status === 'succeeded' ? '已完成' : modelTask.status === 'failed' ? '生成失败' : '生成中' }}</span></div><div class="progress"><i :style="{ width: `${Math.max(4, Math.min(100, Number(modelTask.progress || 0)))}%` }"></i></div><small v-if="modelTask.status === 'succeeded'">审核状态：{{ modelReviewLabel(modelReviewStatus) }}<template v-if="modelReviewComment"> · {{ modelReviewComment }}</template></small><small>{{ modelTask.errorMessage || (modelTask.status === 'succeeded' ? 'GLB 文件已保存到作品库，请先提交 3D 建模审核，审核通过后才能申请打样。' : `当前进度 ${Math.round(Number(modelTask.progress || 0))}%`) }}</small></section><div class="result-actions"><button v-for="item in generationActionReplies" :key="item.type" :class="{ primary: item.type === 'multiview' || item.type === 'bundle_production' }" :disabled="busy" @click="handleReply(item)">{{ item.label }}</button></div></div>
        <div v-else class="orders-content"><div class="result-toolbar"><div><span>WORKFLOW ORDERS</span><h2>订单与生产进度</h2></div><button @click="loadProductionRequests">刷新</button></div><div v-if="!productionRequests.length" class="orders-empty">暂时没有打样订单<br /><small>作品审核通过后，可以在对话中申请打样。</small></div><article v-for="item in productionRequests" :key="item.id" class="order-card"><div><span>{{ item.productNo || item.requestNo || `订单 ${item.id}` }}</span><strong>{{ item.title || item.assetTitle || item.sampleProductName || '文创产品' }}</strong><small>{{ item.requestType === 'bulk' ? '批量生产' : '打样' }} · {{ item.quantity || 1 }} 件 · {{ formatTime(item.createdAt) }}</small></div><div class="order-status"><b :class="statusClass(item.status)">{{ statusText(item.status) }}</b><span v-if="item.samplePaymentStatus">{{ statusText(item.samplePaymentStatus) }}</span><button v-if="item.status === 'approved' && ['unpaid', 'pending'].includes(String(item.samplePaymentStatus || 'unpaid'))" :disabled="paymentLoading" @click="paySample(item)">{{ paymentTargetMatches(item) && paymentOrder && !paymentIsTerminal(paymentOrder.status) ? '继续支付' : '支付打样费' }}</button></div></article><div v-if="paymentOrder && !paymentDialogOpen" class="payment-card"><div><strong>打样费支付</strong><span>{{ paymentOrder.orderNo }} · {{ paymentOrder.status === 'paid' ? '支付成功，已进入生产' : paymentIntent === 'exception' ? '支付结果核对中' : '待支付' }}</span></div><img v-if="paymentQrUrl" :src="paymentQrUrl" alt="微信支付二维码" /><small>{{ paymentHint }}</small><button v-if="!paymentIsTerminal(paymentOrder.status)" @click="reopenPaymentDialog">继续支付</button></div></div>

        <footer v-if="activeView === 'chat'" class="chat-composer"><div class="quick-replies"><button v-for="item in quickReplies" :key="`${item.type}-${item.label}`" :class="{ confirm: item.type === 'confirm_generate', secondary: ['replace_image', 'add_detail'].includes(item.type) }" :disabled="busy || sending" @click="handleReply(item)">{{ item.label }}</button></div><div v-if="busy" class="busy-line"><i></i>{{ busyText }}</div><div class="composer-row"><button class="upload-button" title="上传参考图片" :disabled="busy || sending || inputLocked" @click="referenceInput?.click()">＋</button><textarea v-model="chatInput" :disabled="busy || sending || inputLocked" :placeholder="inputPlaceholder" rows="2" maxlength="1200" @keydown.enter.exact.prevent="sendChat()" /><button class="send-button" :disabled="busy || sending || inputLocked || !chatInput.trim()" @click="sendChat()">发送</button></div><div class="composer-foot"><span>AI 生成内容 · 请在商业使用前人工复核</span><span>{{ chatInput.length }}/1200</span></div></footer>
      </section>
    </section>

    <input ref="referenceInput" class="visually-hidden" type="file" accept="image/*" @change="onReferenceChange" />
    <div v-if="productionDialogOpen" class="dialog-mask" @click.self="productionDialogOpen = false"><section class="production-dialog"><header><div><span>PRODUCTION REQUEST</span><h2>申请打样</h2><small>{{ selectedRequest?.title || productName }}</small></div><button @click="productionDialogOpen = false">×</button></header><main><label><span>创作去向</span><select v-model="productionForm.purpose"><option value="personal">个人收藏 / 送礼</option><option value="museum_sale">售卖（景区、博物馆）</option></select></label><label v-if="productionForm.purpose === 'museum_sale'"><span>合作渠道</span><select v-model="productionForm.museumId" @change="selectMuseum"><option value="">请选择博物馆或景区</option><option v-for="museum in museumOptions" :key="museum.id" :value="museum.id">{{ museum.name }}</option></select></label><label><span>数量</span><input v-model.number="productionForm.quantity" type="number" min="1" /></label><label v-if="productionForm.purpose === 'personal'"><span>收件人</span><input v-model="productionForm.recipientName" placeholder="姓名" /></label><label v-if="productionForm.purpose === 'personal'"><span>手机号</span><input v-model="productionForm.recipientPhone" placeholder="用于寄送联系" /></label><label v-if="productionForm.purpose === 'personal'"><span>收货地址</span><textarea v-model="productionForm.recipientAddress" rows="2" placeholder="收货地址" /></label><label><span>申请说明</span><textarea v-model="productionForm.note" rows="3" placeholder="补充打样要求" /></label></main><footer><button @click="productionDialogOpen = false">取消</button><button class="primary" :disabled="productionSubmitting" @click="submitProduction">{{ productionSubmitting ? '提交中…' : '提交打样申请' }}</button></footer></section></div>

    <div v-if="paymentDialogOpen" class="payment-dialog-mask" role="dialog" aria-modal="true" aria-labelledby="payment-dialog-title">
      <section class="payment-dialog" @click.stop>
        <header>
          <div><span>SAMPLE PAYMENT</span><h2 id="payment-dialog-title">支付打样费</h2><small>{{ paymentRequest?.title || paymentRequest?.sampleProductName || productName }}</small></div>
          <button type="button" :disabled="paymentLoading || paymentClosing" title="关闭支付窗口" @click="closePaymentDialog">×</button>
        </header>
        <main>
          <div v-if="paymentLoading" class="payment-loading"><i></i><strong>正在创建微信支付订单</strong><small>请稍候，正在准备二维码…</small></div>
          <template v-else>
            <div class="payment-summary"><span>本次打样费用</span><strong>¥{{ paymentOrder?.amountYuan || paymentRequest?.sampleFeeYuan || paymentRequest?.quotedSampleFeeYuan || '-' }}</strong></div>
            <img v-if="paymentQrUrl" :src="paymentQrUrl" alt="微信支付二维码" class="payment-qr-large" />
            <div v-else class="payment-qr-missing">{{ paymentError || '暂未生成二维码，请重试。' }}</div>
            <p class="payment-hint" :class="{ error: paymentIntent === 'failed' }">{{ paymentHint }}</p>
            <small v-if="paymentOrder?.orderNo" class="payment-order-no">订单号：{{ paymentOrder.orderNo }} · {{ paymentOrder.status === 'paid' ? '已支付' : paymentOrder.status === 'manual_review' ? '待人工核验' : paymentOrder.status === 'payment_exception' ? '结果核对中' : paymentOrder.status === 'expired' ? '已过期' : '待支付' }}</small>
          </template>
        </main>
        <footer>
          <span v-if="paymentLocked">支付完成前请勿离开；如需稍后处理，请取消并关闭这笔未支付订单。</span>
          <span v-else-if="paymentIntent === 'paid'">支付成功后订单已进入生产中。</span>
          <button v-if="paymentIntent === 'failed'" type="button" class="primary" :disabled="paymentLoading" @click="paymentRequest && paySample(paymentRequest)">重新发起支付</button>
          <button type="button" :class="{ primary: !paymentLocked }" :disabled="paymentClosing" @click="closePaymentDialog">{{ paymentClosing ? '正在关闭…' : paymentLocked ? '取消支付并关闭订单' : '返回订单' }}</button>
        </footer>
      </section>
    </div>
  </main>
</template>

<style scoped>
:global(*) { box-sizing: border-box; }
.conversation-workspace { min-height: 100vh; min-height: 100dvh; padding: 26px 32px 40px; color: #25372d; background: #f4f7f3; font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif; }
.workspace-header { max-width: 1480px; margin: 0 auto 22px; display: grid; grid-template-columns: 240px minmax(0, 1fr) auto; align-items: center; gap: 26px; }
.brand { display: flex; align-items: center; gap: 11px; min-width: 0; }
.brand img { width: 42px; height: 42px; object-fit: contain; }
.brand strong, .brand span { display: block; }
.brand strong { font-family: "Songti SC", serif; font-size: 18px; }
.brand span { margin-top: 3px; color: #87988d; font-size: 11px; }
.header-copy span, .professional-top-copy > span, .chat-header span, .result-toolbar span { color: #73917e; font-size: 10px; font-weight: 900; letter-spacing: .16em; }
.header-copy h1 { margin: 6px 0 4px; color: #263e31; font-size: clamp(23px, 2.5vw, 36px); line-height: 1.2; }
.header-copy p { margin: 0; color: #829188; font-size: 13px; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.creator-switch { display: flex; padding: 3px; border: 1px solid #d7e2d9; border-radius: 10px; background: #fff; }
.creator-switch button, .icon-action, .sidebar-heading button, .result-toolbar button { border: 0; background: transparent; color: #6d7f73; cursor: pointer; }
.creator-switch button { padding: 8px 11px; border-radius: 7px; font-size: 12px; }
.creator-switch button.active { background: #3f7054; color: #fff; font-weight: 800; }
.icon-action { padding: 8px 0 8px 8px; font-size: 12px; }
.professional-top-panel { max-width: 1480px; margin: 0 auto 22px; display: grid; grid-template-columns: minmax(220px, .8fr) minmax(390px, 1.25fr) minmax(260px, .9fr); gap: 20px; padding: 22px; border: 1px solid #cfe1d3; border-radius: 12px; background: #fff; box-shadow: 0 10px 28px rgba(55, 88, 66, .07); }
.professional-top-copy h2 { margin: 7px 0 7px; font-size: 22px; }
.professional-top-copy p { margin: 0; color: #718276; font-size: 13px; line-height: 1.65; }
.professional-form { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; align-items: start; }
.professional-form .zip-picker, .professional-form textarea, .professional-form button { grid-column: 1 / -1; }
.zip-picker { display: flex; align-items: center; gap: 10px; min-height: 52px; padding: 10px 13px; border: 1px dashed #a9c4ae; border-radius: 9px; background: #f5faf6; cursor: pointer; }
.zip-picker input { display: none; }
.zip-picker strong { min-width: 0; overflow: hidden; color: #4d6756; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.zip-picker small { margin-left: auto; color: #92a197; font-size: 11px; white-space: nowrap; }
.professional-form input, .professional-form textarea, .production-dialog input, .production-dialog textarea, .production-dialog select { width: 100%; border: 1px solid #dce7de; border-radius: 8px; padding: 9px 11px; color: #34473b; background: #fbfdfb; font: inherit; font-size: 12px; outline: none; }
.professional-form textarea { resize: vertical; }
.professional-form button, .new-session, .chat-composer .send-button, .replacement-box button, .production-dialog .primary, .professional-history button, .order-status button { border: 0; border-radius: 8px; padding: 10px 13px; background: #3e7053; color: #fff; font-weight: 800; cursor: pointer; }
.professional-form button:disabled, .chat-composer button:disabled, .production-dialog button:disabled { opacity: .5; cursor: not-allowed; }
.professional-history { display: grid; gap: 7px; align-content: start; }
.professional-history > strong { color: #718579; font-size: 12px; }
.professional-history article { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 9px 10px; border: 1px solid #e5ece6; border-radius: 8px; background: #fafcf9; }
.professional-history article div { min-width: 0; }
.professional-history b, .professional-history span, .professional-history small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.professional-history b { color: #42594a; font-size: 12px; }
.professional-history span, .professional-history small { margin-top: 3px; color: #8b9a8f; font-size: 10px; }
.professional-history button { flex: 0 0 auto; padding: 7px 9px; font-size: 11px; }
.workspace-grid { max-width: 1480px; width: 100%; height: clamp(520px, calc(100vh - 250px), 760px); height: clamp(520px, calc(100dvh - 250px), 760px); min-height: 0; margin: 0 auto; display: grid; grid-template-columns: 270px minmax(0, 1fr); gap: 18px; }
.history-sidebar, .chat-panel { height: 100%; min-height: 0; border: 1px solid #dce7de; border-radius: 12px; background: #fff; box-shadow: 0 16px 42px rgba(48, 78, 57, .08); }
.history-sidebar { display: flex; flex-direction: column; overflow: hidden; }
.history-sidebar.collapsed { width: 58px; }
.history-sidebar.collapsed .sidebar-heading h2, .history-sidebar.collapsed .sidebar-heading span, .history-sidebar.collapsed .sidebar-links button:not(.active), .history-sidebar.collapsed .new-session, .history-sidebar.collapsed .session-list, .history-sidebar.collapsed .history-pagination, .history-sidebar.collapsed .sidebar-note { display: none; }
.sidebar-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 18px 16px 13px; border-bottom: 1px solid #edf2ed; }
.sidebar-heading span { color: #91a297; font-size: 9px; letter-spacing: .14em; }
.sidebar-heading h2 { margin: 5px 0 0; font-size: 18px; }
.sidebar-heading button { font-size: 24px; line-height: 1; }
.result-toolbar-actions { display: flex; align-items: center; gap: 8px; }
.result-toolbar-actions button:disabled { opacity: .55; cursor: wait; }
.new-session { margin: 14px 14px 10px; text-align: left; }
.sidebar-empty { padding: 38px 15px; color: #9aa79f; font-size: 12px; line-height: 1.8; text-align: center; }
.sidebar-empty small { color: #b0bbb3; }
.session-list { display: grid; flex: 1 1 auto; gap: 4px; min-height: 0; padding: 0 9px; overflow: auto; }
.session-item { display: flex; align-items: center; gap: 8px; width: 100%; padding: 9px 7px; border: 1px solid transparent; border-radius: 8px; background: transparent; color: #567061; text-align: left; cursor: pointer; }
.session-item:hover, .session-item.active { border-color: #c9dece; background: #f1f8f2; }
.session-mark { display: grid; place-items: center; flex: 0 0 29px; width: 29px; height: 29px; border-radius: 8px; background: #e4f0e6; color: #538064; font-size: 12px; font-weight: 900; }
.session-copy { min-width: 0; flex: 1; }
.session-copy strong, .session-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.session-copy strong { color: #445b4c; font-size: 12px; }
.session-copy small { margin-top: 4px; color: #9aa79e; font-size: 10px; }
.session-item i { flex: 0 0 auto; padding: 3px; color: #a6b3aa; font-size: 17px; font-style: normal; opacity: .2; }
.session-item:hover i, .session-item.active i { opacity: 1; }
.history-pagination { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 9px 12px 5px; color: #8a9b8f; font-size: 10px; }
.history-pagination button { display: grid; place-items: center; width: 24px; height: 24px; padding: 0; border: 1px solid #d9e6db; border-radius: 6px; background: #f8fbf8; color: #5c8066; font-size: 17px; line-height: 1; cursor: pointer; }
.history-pagination button:disabled { opacity: .35; cursor: not-allowed; }
.sidebar-note { margin: auto 14px 14px; padding: 10px; border-top: 1px solid #edf2ed; color: #9ba8a0; font-size: 10px; line-height: 1.5; }
.sidebar-links { display: grid; gap: 3px; margin-top: auto; padding: 10px; border-top: 1px solid #edf2ed; }
.sidebar-links button { display: flex; align-items: center; justify-content: space-between; padding: 9px 10px; border: 0; border-radius: 7px; background: transparent; color: #76897c; text-align: left; font-size: 12px; cursor: pointer; }
.sidebar-links button.active, .sidebar-links button:hover { background: #eff7f0; color: #467255; font-weight: 800; }
.sidebar-links em { padding: 1px 5px; border-radius: 10px; background: #deefe0; color: #4c7c5c; font-size: 10px; font-style: normal; }
.chat-panel { display: flex; flex-direction: column; overflow: hidden; }
.chat-header { display: flex; align-items: center; gap: 12px; min-height: 86px; padding: 15px 20px; border-bottom: 1px solid #edf2ed; }
.assistant-avatar, .message-avatar { display: grid; place-items: center; border-radius: 11px; background: #4c7d60; color: #fff; font-family: "Songti SC", serif; font-weight: 900; }
.assistant-avatar { flex: 0 0 43px; width: 43px; height: 43px; font-size: 20px; }
.chat-header h2 { margin: 4px 0 1px; font-size: 17px; }
.chat-header small { color: #9aa89f; font-size: 10px; }
.brief-chip { min-width: 150px; margin-left: auto; padding: 8px 10px; border-left: 1px solid #e7eee8; }
.brief-chip b, .brief-chip span { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.brief-chip b { color: #4b6755; font-size: 12px; }
.brief-chip span { margin-top: 4px; color: #9aa89f; font-size: 10px; }
.chat-content, .result-content, .orders-content { flex: 1 1 auto; min-height: 0; overflow: auto; overscroll-behavior: contain; scrollbar-gutter: stable; }
.chat-content { overflow-anchor: none; }
.conversation-intro { margin: 18px 22px 2px; padding: 10px 12px; border-left: 3px solid #9bbb9e; background: #f4f9f4; color: #718579; font-size: 12px; line-height: 1.6; }
.message-stream { min-height: 100%; padding: 14px 24px 22px; }
.message { display: flex; align-items: flex-start; gap: 9px; margin: 14px 0; }
.message.user { justify-content: flex-end; }
.message-avatar { flex: 0 0 30px; width: 30px; height: 30px; border-radius: 9px; font-size: 13px; }
.message-avatar.user { background: #e6f0e8; color: #547660; font-family: inherit; font-size: 10px; }
.message-body { max-width: min(76%, 620px); }
.message-body p { margin: 0; padding: 11px 13px; border: 1px solid #e1eae2; border-radius: 5px 13px 13px 13px; background: #fbfdfb; color: #45594b; font-size: 13px; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
.message.user .message-body p { border-color: #bdd6c2; border-radius: 13px 5px 13px 13px; background: #e9f4eb; color: #446451; }
.message-body small { display: block; margin: 5px 4px 0; color: #a4afa8; font-size: 9px; }
.message.user .message-body small { text-align: right; }
.message-image { margin-bottom: 5px; overflow: hidden; border: 1px solid #dbe7dd; border-radius: 10px; background: #eff5f0; }
.message-image img { display: block; max-width: 260px; max-height: 220px; object-fit: contain; }
.thinking { display: inline-flex; align-items: center; gap: 5px; padding: 12px 14px; border: 1px solid #e1eae2; border-radius: 5px 13px 13px 13px; background: #fbfdfb; }
.thinking span { width: 6px; height: 6px; border-radius: 50%; background: #74a17f; animation: pulse 1.2s infinite; }
.thinking span:nth-child(2) { animation-delay: .15s; }.thinking span:nth-child(3) { animation-delay: .3s; }
.thinking small { margin-left: 5px; }
@keyframes pulse { 0%, 100% { opacity: .3; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-3px); } }
.chat-composer { padding: 11px 18px 13px; border-top: 1px solid #e5ede6; background: #fcfefc; }
.quick-replies { display: flex; flex-wrap: wrap; gap: 7px; max-height: 82px; overflow: auto; }
.quick-replies button { padding: 7px 10px; border: 1px solid #c8ddcd; border-radius: 7px; background: #f2f8f3; color: #4e765d; font-size: 11px; cursor: pointer; }
.quick-replies button.confirm { border-color: #7eb28b; background: #e7f5ea; font-weight: 800; }.quick-replies button.secondary { border-color: #e6d0c5; background: #fff8f5; color: #9a6754; }
.busy-line { display: flex; align-items: center; gap: 7px; margin: 8px 2px 0; color: #ad765c; font-size: 11px; }.busy-line i { width: 10px; height: 10px; border: 2px solid #f2ddd4; border-top-color: #b8785d; border-radius: 50%; animation: spin .8s linear infinite; }@keyframes spin { to { transform: rotate(360deg); } }
.composer-row { display: grid; grid-template-columns: 38px minmax(0, 1fr) 64px; gap: 8px; margin-top: 9px; }
.composer-row textarea { min-height: 52px; resize: none; border: 1px solid #d9e5db; border-radius: 9px; padding: 9px 11px; color: #405748; background: #f7faf7; font: inherit; font-size: 13px; line-height: 1.5; outline: none; }.composer-row textarea:focus { border-color: #9fc2a5; background: #fff; }.composer-row textarea::placeholder { color: #a1afa5; }
.upload-button, .send-button { border: 1px solid #d4e3d7; border-radius: 9px; background: #f6faf6; color: #5c8268; cursor: pointer; }.upload-button { font-size: 22px; }.send-button { border: 0; background: #3f7053; color: #fff; font-size: 12px; font-weight: 800; }
.composer-foot { display: flex; justify-content: space-between; gap: 8px; margin-top: 6px; color: #a8b2ab; font-size: 9px; }.composer-foot span:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.result-content, .orders-content { padding: 21px 24px; }.result-toolbar { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; margin-bottom: 14px; }.result-toolbar h2 { margin: 5px 0 0; font-size: 21px; }.result-toolbar button { padding: 6px 0 6px 8px; color: #5d8669; font-size: 12px; }.result-product-identity { display: flex; align-items: baseline; flex-wrap: wrap; gap: 6px 9px; margin-bottom: 9px; padding-bottom: 8px; border-bottom: 1px solid #e6eee7; }.result-product-identity span { color: #668875; letter-spacing: .1em; }.result-product-identity strong { color: #315e43; font-size: 15px; letter-spacing: .04em; }.result-product-identity strong.missing { color: #9aa89f; font-weight: 600; }.result-product-identity small { color: #9aa89f; font-size: 10px; }
.output-card { overflow: hidden; margin-bottom: 14px; border: 1px solid #dfe9e0; border-radius: 10px; background: #fbfdfb; }.output-image { position: relative; overflow: hidden; background: #edf4ee; }.output-image img { display: block; width: 100%; max-height: 420px; object-fit: contain; }.output-image b { position: absolute; top: 9px; left: 9px; padding: 4px 7px; border-radius: 5px; background: rgba(46, 74, 57, .75); color: #fff; font-size: 10px; }.output-copy { display: flex; justify-content: space-between; gap: 8px; padding: 10px 12px; }.output-copy strong { color: #476151; font-size: 13px; }.output-copy span { color: #91a198; font-size: 11px; }
.replacement-box { margin-bottom: 14px; padding: 13px; border: 1px solid #ead8cf; border-radius: 10px; background: #fff9f6; }.replacement-box strong { color: #805a4b; font-size: 13px; }.replacement-box textarea { display: block; width: 100%; margin: 9px 0; padding: 9px; resize: vertical; border: 1px solid #eadbd4; border-radius: 7px; font: inherit; font-size: 12px; }.replacement-box button { font-size: 12px; }
.simulation-card { padding: 12px; }.view-strip { display: grid; grid-template-columns: repeat(3, 1fr); gap: 7px; padding-top: 9px; }.view-strip div { overflow: hidden; border: 1px solid #e1e9e2; border-radius: 7px; background: #f1f5f1; }.view-strip img { display: block; width: 100%; height: 105px; object-fit: contain; }.view-strip span { display: block; padding: 5px; color: #6a7d70; font-size: 10px; text-align: center; }.review-state { display: grid; gap: 4px; margin-top: 10px; padding: 9px 10px; border-radius: 7px; background: #f4f7f4; color: #687a6d; font-size: 12px; }.review-state.success { background: #eef8f0; color: #4b7b58; }.review-state.danger { background: #fff3ef; color: #a35d4a; }.review-state small { line-height: 1.5; }
.model-status { margin-bottom: 14px; padding: 12px; border: 1px solid #dce9df; border-radius: 9px; background: #f8fbf8; }.model-status > div:first-child { display: flex; justify-content: space-between; color: #52705d; font-size: 13px; }.model-status > div:first-child span { color: #83968a; font-size: 11px; }.progress { height: 8px; margin: 10px 0 7px; overflow: hidden; border-radius: 8px; background: #e3ece4; }.progress i { display: block; height: 100%; border-radius: inherit; background: #6a9a77; transition: width .3s; }.model-status small { color: #8a9b90; font-size: 10px; line-height: 1.5; }.result-actions { display: flex; flex-wrap: wrap; gap: 8px; }.result-actions button { padding: 9px 12px; border: 1px solid #c8ddcd; border-radius: 8px; background: #f5faf5; color: #4b775a; font-size: 11px; cursor: pointer; }.result-actions button.primary { border-color: #8dbb98; background: #e8f5ea; font-weight: 800; }
.orders-empty { padding: 70px 20px; color: #9aa89f; font-size: 13px; line-height: 1.8; text-align: center; }.orders-empty small { color: #b0bbb3; }.order-card { display: flex; justify-content: space-between; gap: 14px; padding: 13px 0; border-bottom: 1px solid #edf2ed; }.order-card > div:first-child { min-width: 0; }.order-card span, .order-card strong, .order-card small { display: block; }.order-card span { color: #9aaa9f; font-size: 10px; }.order-card strong { margin-top: 4px; color: #4b6352; font-size: 13px; }.order-card small { margin-top: 4px; color: #8a9b90; font-size: 10px; }.order-status { display: flex; align-items: flex-end; flex: 0 0 auto; flex-direction: column; gap: 5px; }.order-status b, .order-status span { padding: 4px 7px; border-radius: 5px; background: #f1f5f1; color: #788a7d; font-size: 10px; font-weight: 700; }.order-status b.success, .order-status span.success { background: #eaf6ec; color: #4c7b58; }.order-status b.danger { background: #fff0ec; color: #a35c4a; }.order-status button { padding: 6px 8px; font-size: 10px; }.payment-card { display: flex; align-items: center; gap: 13px; margin-top: 17px; padding: 13px; border: 1px solid #d4e5d7; border-radius: 9px; background: #f4faf5; }.payment-card > div { display: grid; gap: 5px; flex: 1; }.payment-card strong { color: #476e52; font-size: 13px; }.payment-card span, .payment-card small { color: #819487; font-size: 10px; line-height: 1.5; }.payment-card img { width: 120px; height: 120px; }
.dialog-mask { position: fixed; z-index: 50; inset: 0; display: grid; place-items: center; padding: 20px; background: rgba(32, 48, 38, .45); }.production-dialog { width: min(520px, 100%); overflow: hidden; border-radius: 12px; background: #fff; box-shadow: 0 24px 70px rgba(22, 45, 29, .25); }.production-dialog header { display: flex; justify-content: space-between; padding: 18px 20px 13px; border-bottom: 1px solid #edf2ed; }.production-dialog header span { color: #7e9b86; font-size: 9px; letter-spacing: .14em; }.production-dialog h2 { margin: 5px 0 3px; font-size: 20px; }.production-dialog header small { color: #8b9b90; font-size: 11px; }.production-dialog header button { border: 0; background: transparent; color: #8b9b90; font-size: 24px; cursor: pointer; }.production-dialog main { display: grid; gap: 11px; padding: 18px 20px; }.production-dialog label { display: grid; grid-template-columns: 88px minmax(0, 1fr); align-items: center; gap: 9px; }.production-dialog label > span { color: #6c8173; font-size: 12px; }.production-dialog textarea { resize: vertical; }.production-dialog footer { display: flex; justify-content: flex-end; gap: 8px; padding: 13px 20px 17px; border-top: 1px solid #edf2ed; }.production-dialog footer button { min-width: 80px; padding: 9px 13px; border: 1px solid #d8e4da; border-radius: 8px; background: #fff; color: #698071; cursor: pointer; }.production-dialog footer .primary { border: 0; }
.payment-dialog-mask { position: fixed; z-index: 80; inset: 0; display: grid; place-items: center; padding: 18px; background: rgba(22, 38, 28, .58); overscroll-behavior: contain; }
.payment-dialog { display: flex; flex-direction: column; width: min(430px, 100%); max-height: min(720px, calc(100dvh - 32px)); overflow: hidden; border: 1px solid #d3e4d6; border-radius: 14px; background: #fff; box-shadow: 0 28px 80px rgba(19, 42, 26, .3); }
.payment-dialog header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; padding: 19px 21px 14px; border-bottom: 1px solid #edf2ed; }
.payment-dialog header span { color: #72917c; font-size: 9px; font-weight: 900; letter-spacing: .16em; }
.payment-dialog header h2 { margin: 6px 0 4px; color: #2f4b39; font-size: 21px; }
.payment-dialog header small { display: block; max-width: 320px; overflow: hidden; color: #83948a; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.payment-dialog header button { flex: 0 0 auto; width: 30px; height: 30px; border: 1px solid transparent; border-radius: 7px; background: transparent; color: #819287; font-size: 24px; line-height: 1; cursor: pointer; }
.payment-dialog header button:hover { border-color: #dce9de; background: #f5faf5; }
.payment-dialog header button:disabled { opacity: .4; cursor: wait; }
.payment-dialog main { display: grid; min-height: 0; gap: 12px; overflow: auto; padding: 20px 21px 18px; text-align: center; }
.payment-loading { display: grid; place-items: center; gap: 9px; min-height: 230px; color: #526d5c; }
.payment-loading i { width: 28px; height: 28px; border: 3px solid #dbeade; border-top-color: #4c7d60; border-radius: 50%; animation: spin .8s linear infinite; }
.payment-loading strong { font-size: 14px; }
.payment-loading small { color: #91a197; font-size: 11px; }
.payment-summary { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: #7e9184; font-size: 12px; }
.payment-summary strong { color: #3e7150; font-size: 24px; }
.payment-qr-large { display: block; width: min(300px, 72vw); height: auto; aspect-ratio: 1; margin: 2px auto 1px; border: 8px solid #fff; border-radius: 6px; box-shadow: 0 8px 24px rgba(43, 78, 53, .13); image-rendering: pixelated; }
.payment-qr-missing { display: grid; place-items: center; min-height: 210px; padding: 18px; border: 1px dashed #e1cfc6; border-radius: 9px; background: #fff9f6; color: #a26755; font-size: 12px; line-height: 1.6; }
.payment-hint { margin: 0; color: #687d70; font-size: 12px; line-height: 1.65; }
.payment-hint.error { color: #a45f4d; }
.payment-order-no { color: #a0ada5; font-size: 10px; }
.payment-dialog footer { display: flex; align-items: center; justify-content: flex-end; flex-wrap: wrap; gap: 9px; padding: 13px 21px 17px; border-top: 1px solid #edf2ed; }
.payment-dialog footer span { flex: 1 1 100%; color: #98a69d; font-size: 10px; line-height: 1.5; }
.payment-dialog footer button { min-width: 108px; padding: 9px 12px; border: 1px solid #d8e5da; border-radius: 8px; background: #fff; color: #688071; font: inherit; font-size: 11px; cursor: pointer; }
.payment-dialog footer button.primary { border-color: #3f7053; background: #3f7053; color: #fff; font-weight: 800; }
.payment-dialog footer button:disabled { opacity: .5; cursor: not-allowed; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; clip-path: inset(50%); }
@media (max-width: 1050px) { .workspace-header { grid-template-columns: 1fr auto; }.brand { grid-column: 1 / -1; }.professional-top-panel { grid-template-columns: 1fr 1.2fr; }.professional-history { grid-column: 1 / -1; }.workspace-grid { grid-template-columns: 220px minmax(0, 1fr); } }
@media (max-width: 760px) { .conversation-workspace { padding: 17px 12px 28px; }.workspace-header { display: flex; flex-wrap: wrap; gap: 12px; }.header-copy { order: 3; width: 100%; }.header-copy h1 { font-size: 25px; }.header-actions { margin-left: auto; flex-wrap: wrap; justify-content: flex-end; }.professional-top-panel { grid-template-columns: 1fr; padding: 15px; }.professional-history { grid-column: auto; }.workspace-grid { display: block; height: auto; min-height: 0; }.history-sidebar { height: auto; min-height: auto; margin-bottom: 12px; }.history-sidebar.collapsed { width: auto; }.history-sidebar.collapsed .sidebar-heading h2, .history-sidebar.collapsed .sidebar-heading span, .history-sidebar.collapsed .sidebar-links button:not(.active), .history-sidebar.collapsed .new-session, .history-sidebar.collapsed .session-list, .history-sidebar.collapsed .sidebar-note { display: block; }.history-sidebar.collapsed .sidebar-heading button { transform: rotate(-90deg); }.session-list { max-height: 180px; }.chat-panel { height: clamp(500px, calc(100vh - 200px), 680px); height: clamp(500px, calc(100dvh - 200px), 680px); min-height: 0; }.chat-header { padding: 12px; }.brief-chip { display: none; }.message-stream { padding: 10px 13px 18px; }.message-body { max-width: 84%; }.result-content, .orders-content { padding: 15px; }.composer-row { grid-template-columns: 34px minmax(0, 1fr) 56px; }.composer-foot { font-size: 8px; }.professional-form { grid-template-columns: 1fr; }.professional-form input { grid-column: 1 / -1; } }
</style>
