import { DEFAULT_SEEDREAM_IMAGE_SIZE, buildCreativeGenerationRequest, request, uploadFile, waitForImageGenerationJob, type CreativeGenerationRequest, type ImageGenerationJobProgress, type SeedreamImageSize } from './client'

export interface ConversationSession {
  id: number
  projectId?: number
  versionId?: number
  sessionNo?: string
  mode?: 'template' | 'text' | 'image' | string
  productType?: string
  material?: string
  productSize?: string
  status?: string
  events?: ConversationEvent[]
  createdAt?: string
  updatedAt?: string
}

export interface ConversationEvent {
  id: number
  step: string
  eventType: string
  payload?: Record<string, any>
  createdAt?: string
}

export const createConversation = (mode?: 'template' | 'text' | 'image') => request<ConversationSession>('/api/creative/ai/conversations', {
  method: 'POST', data: mode ? { mode } : {}, header: { 'content-type': 'application/json' },
})
export const getConversations = () => request<ConversationSession[]>('/api/creative/ai/conversations')
export const getConversation = (id: number | string) => request<ConversationSession>(`/api/creative/ai/conversations/${encodeURIComponent(String(id))}`)
export const deleteConversation = (id: number | string) => request<{ deleted?: boolean; id?: number }>(
  `/api/creative/ai/conversations/${encodeURIComponent(String(id))}`,
  { method: 'DELETE' },
)
export const saveConversationEvent = (id: number | string, body: { step: string; eventType: string; payload?: Record<string, any>; idempotencyKey?: string }) => request<ConversationSession>(
  `/api/creative/ai/conversations/${encodeURIComponent(String(id))}/events`,
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

export interface ConversationQuickReply {
  label: string
  type: 'product' | 'category' | 'material' | 'template' | 'upload' | 'text' | 'confirm_generate' | 'add_detail' | 'edit' | 'adopt_direction' | string
  value?: string
}

export interface ConversationChatResult {
  assistantText: string
  quickReplies?: ConversationQuickReply[]
  readyToGenerate?: boolean
  generationConfirmationRequired?: boolean
  stage?: string
  chatModel?: string
  brief?: Record<string, any>
  session?: ConversationSession
}

export const sendConversationChat = (id: number | string, body: {
  message?: string
  action?: { type: string; value?: string; label?: string }
}) => request<ConversationChatResult>(
  `/api/creative/ai/conversations/${encodeURIComponent(String(id))}/chat`,
  { method: 'POST', data: body, timeout: 45000, header: { 'content-type': 'application/json' } },
)

/**
 * 这里的接口全部通过 client.ts 自动携带的 Bearer Token 识别当前用户。
 * 不要再从小程序提交 currentUserId、role 之类可以被篡改的身份参数。
 */
export const getMuseums = () => request<any[]>('/api/creative/ai/consumer-production/museums')
export const getCredits = () => request<any>('/api/creative/ai/consumer-credits/account')
export const getCreditRules = () => request<any>('/api/creative/ai/consumer-credits/rules')
export const getRewardOverview = () => request<any>('/api/creative/ai/consumer-rewards/overview')

export interface CreatorCampaign {
  key: string
  title: string
  targetName: string
  channelCode: string
  collectionStyle: string
  recommendedProducts: string[]
  recommendedProductKey?: string
  brief: string
  promptHint: string
  rewardAmount: number
  deadline?: string
  reviewNotice?: string
  cooperationNotice?: string
}

/** Login may display campaign briefs before an account is authenticated. */
export const getPublicCreatorCampaigns = () => request<CreatorCampaign[]>(
  '/api/creative/ai/consumer-rewards/campaigns/public',
  { header: { Authorization: '' } },
)

export const claimRewardMission = (missionKey: string) => request<any>(
  `/api/creative/ai/consumer-rewards/missions/${encodeURIComponent(missionKey)}/claim`,
  { method: 'POST', header: { 'content-type': 'application/json' } },
)
export const getAssets = (type?: string) => request<any[]>(`/api/creative/ai/assets${type ? `?type=${encodeURIComponent(type)}` : ''}`)
export const getJobs = () => request<any[]>('/api/creative/ai/jobs')

export type DesignReviewRecommendation = 'go' | 'adjust' | 'reject' | string

export interface DesignReviewAgent {
  agentKey: string
  agentName: string
  score: number
  verdict?: string
  comments?: string
  suggestions?: string[] | string
  suggestionsJson?: string
}

export interface DesignReviewReport {
  id?: number
  reviewId?: number
  reviewNo?: string
  assetId: number | string
  assetTitle?: string
  overallScore?: number
  recommendation?: DesignReviewRecommendation
  summary?: string
  createdAt?: string
  agents?: DesignReviewAgent[]
  matrix?: Record<string, unknown>
  roadmap?: unknown
}

/**
 * 发起一份真正的多角色创作评审。服务端会再次校验作品归属，返回设计、市场、
 * 成本及消费者四个视角的报告；它不等同于提交生产审核。
 */
export const createDesignReview = (body: { assetId: number | string; context?: string }) => request<DesignReviewReport>(
  '/api/creative/ai/reviews',
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

/** 仅查询当前登录用户自己的 AI 创作评审记录。 */
export const getDesignReviews = (assetId?: number | string) => request<DesignReviewReport[]>(
  `/api/creative/ai/reviews${assetId === undefined ? '' : `?assetId=${encodeURIComponent(String(assetId))}`}`,
)

export interface CustomerServiceConversation {
  id: number
  userId?: number
  userName?: string
  status?: string
  humanTakeover?: boolean | number
  takenByName?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface CustomerServiceMessage {
  id: number
  senderType: 'user' | 'assistant' | 'staff' | string
  senderName?: string | null
  content: string
  createdAt?: string
}

export interface CustomerServiceConversationDetail {
  conversation: CustomerServiceConversation | null
  messages: CustomerServiceMessage[]
}

/** 打开（或恢复）当前 C 端用户唯一的客服会话。 */
export const openCustomerServiceConversation = () => request<CustomerServiceConversationDetail>(
  '/api/customer-service/conversations/open',
  { method: 'POST' },
)

/** 仅返回当前登录用户的客服历史，不会创建演示数据。 */
export const getMyCustomerServiceConversation = () => request<CustomerServiceConversationDetail>(
  '/api/customer-service/conversations/mine',
)

export const sendCustomerServiceMessage = (conversationId: number | string, content: string) => request<CustomerServiceConversationDetail>(
  `/api/customer-service/conversations/${encodeURIComponent(String(conversationId))}/messages`,
  { method: 'POST', data: { content }, header: { 'content-type': 'application/json' } },
)

export interface CopyrightConsultationPayload {
  service: string
  note?: string
  assetId?: number | string
}

/** 提交版权服务咨询；服务端会校验当前用户与关联作品的归属。 */
export const createCopyrightConsultation = (body: CopyrightConsultationPayload) => request<{ message?: string; status?: string }>(
  '/api/creative/ai/consumer/copyright-consultations',
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

export interface ImagePromptOptimizeRequest {
  prompt: string
  provider?: string
  productCategory?: string
  material?: string
  /** Finished product dimensions, never the image output resolution. */
  productSize?: string
}

export interface ImagePromptOptimizeResult {
  prompt?: string
  usageGuide?: string
}

/** 将灵感描述优化为真实图片生成服务可执行的商业产品提示词。 */
export const optimizeImagePrompt = (body: ImagePromptOptimizeRequest) => request<ImagePromptOptimizeResult>(
  '/api/creative/ai/prompt/tripo-optimize',
  // 提示词优化只是增强步骤，失败时创作页会立即使用原始提示词继续生图。
  { method: 'POST', data: body, timeout: 30000, header: { 'content-type': 'application/json' } },
)

export interface ImageEditPromptOptimizeRequest {
  prompt?: string
  refinementNote: string
  productCategory?: string
  material?: string
  /** Keep the revision compatible with the selected finished product dimensions. */
  productSize?: string
}

/** 将用户的补充修改转为平衡的图改图指令，保留主题连续性并明确执行修改。 */
export const optimizeImageEditPrompt = (body: ImageEditPromptOptimizeRequest) => request<{ prompt?: string }>(
  '/api/creative/ai/prompt/image-edit-optimize',
  { method: 'POST', data: body, timeout: 30000, header: { 'content-type': 'application/json' } },
)

export interface SeedreamMultiViewRequest extends CreativeGenerationRequest {
  /** 用户上传并已归属到当前账号的单张参考图资产。 */
  inputAssetId: number | string
  /** 产品/角色描述。服务端会生成一张横向生产模拟图，并保存视角切片。 */
  prompt: string
  productKey?: string
  productCategory?: string
  material?: string
  /** Finished product dimensions, retained with every generated view. */
  productSize?: string
  /** Conversational route uses front/side/back; professional route defaults to four. */
  viewCount?: 3 | 4
  /** Seedream 多视图接口当前只接受 1K 或 2K。 */
  size?: SeedreamImageSize
  watermark?: boolean
}

export interface SeedreamMultiViewImage {
  view: 'front' | 'left' | 'back' | 'right'
  label: string
  assetId: number
  /** 服务端签发的短时受控图片地址，可能是相对 API 路径。 */
  previewUrl?: string
  imageUrl?: string
  fileUrl?: string
}

/**
 * The complete horizontal triptych returned by the production-simulation
 * pipeline. It intentionally has no `view` field: it is one image containing
 * front, left and back panels. Older servers simply omit this object.
 */
export interface SeedreamProductionSimulationImage {
  assetId: number
  label?: string
  previewUrl?: string
  imageUrl?: string
  fileUrl?: string
  width?: number
  height?: number
}

export interface SeedreamMultiViewResult {
  provider?: string
  model?: string
  message?: string
  images: SeedreamMultiViewImage[]
  /** Complete one-image triptych; optional for old queued jobs/servers. */
  simulationAssetId?: number
  simulationImage?: SeedreamProductionSimulationImage
}

/**
 * 用一张已上传的参考图调用 Doubao Seedream，服务端生成一张横向生产模拟图，
 * 同时保存正面、左侧和背面的视角切片。它不会伪造本地预览结果。
 */
export async function createSeedreamMultiView(body: SeedreamMultiViewRequest, onProgress?: (job: ImageGenerationJobProgress) => void) {
  const normalized = buildCreativeGenerationRequest(body)
  const queued = await request<ImageGenerationJobProgress>('/api/creative/ai/volcengine/seedream/multiview', {
    method: 'POST', data: { ...normalized, size: body?.size || DEFAULT_SEEDREAM_IMAGE_SIZE, provider: 'ark', queue: true }, timeout: 30000, header: { 'content-type': 'application/json' },
  })
  return waitForImageGenerationJob(queued, onProgress) as Promise<SeedreamMultiViewResult>
}

export const createModel = (body: any) => request<any>('/api/creative/ai/tripo/generate', { method: 'POST', data: body, header: { 'content-type': 'application/json' } })
export const getTripoModelTask = (jobId: number | string) => request<any>(
  `/api/creative/ai/tripo/tasks/${encodeURIComponent(String(jobId))}`,
)
export const uploadReference = (filePath: string, projectId?: number | string | null, versionId?: number | string | null) => uploadFile<any>(
  '/api/creative/ai/assets/upload', filePath, 'file', {
    ...(projectId ? { projectId: String(projectId) } : {}),
    ...(versionId ? { versionId: String(versionId) } : {}),
  },
)

export interface ProfessionalSubmission {
  id?: number
  submissionNo?: string
  title?: string
  originalName?: string
  fileSize?: number
  purpose?: 'personal' | 'museum_sale' | string
  museumName?: string
  note?: string
  status?: 'review' | 'approved' | 'rejected' | string
  reviewComment?: string
  createdAt?: string
}

export const uploadProfessionalSubmission = (filePath: string, formData: Record<string, string>) => uploadFile<any>(
  '/api/creative/ai/consumer-professional-submissions', filePath, 'file', formData,
)
export const getMyProfessionalSubmissions = () => request<ProfessionalSubmission[]>('/api/creative/ai/consumer-professional-submissions/my')

export type Tripo3dPromptTemplate = 'universal' | 'collectible' | 'oriental' | 'plush_toy' | 'ppc_precision'

export interface Tripo3dPromptOptimizeRequest {
  prompt: string
  promptTemplate: Tripo3dPromptTemplate
  productCategory: string
  /** 制造材质，用于让服务端按实际工艺方向优化文案。 */
  material?: string
}

export interface Tripo3dPromptOptimizeResult {
  prompt?: string
  negativePrompt?: string
  template?: Tripo3dPromptTemplate | string
  templateName?: string
  usageTips?: string
}

/**
 * 将文字创意转换为 Tripo 可直接使用的英文 3D 提示词，并返回对应反向提示词。
 * 只在“文字 3D”路线使用；图生 / 多视图建模仍以用户上传的真实图片为输入。
 */
export const optimizeTripo3dPrompt = (body: Tripo3dPromptOptimizeRequest) => request<Tripo3dPromptOptimizeResult>(
  '/api/creative/ai/prompt/tripo-3d-optimize',
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

export interface ProductionFeasibilityRequest {
  productCategory: string
  material: string
  prompt: string
}

export interface ProductionFeasibilityResult {
  score: number
  level: string
  issues: string[]
  suggestions: string[]
  productPolicy?: string
  productRules?: string
  disclaimer?: string
}

export interface CreativePreflightCheck {
  key: string
  label: string
  status: 'passed' | 'needs_review' | 'blocked' | string
  detail: string
  blocking?: boolean
}

export interface CreativePreflightReport {
  reportId?: number
  projectId: number | string
  versionId: number | string
  status: 'passed' | 'needs_review' | 'blocked' | 'not_run' | string
  score?: number
  versionFreezeHash?: string
  checks?: CreativePreflightCheck[]
  issues?: string[]
  suggestions?: string[]
  context?: Record<string, any>
  createdAt?: string
  updatedAt?: string
}

/** 对指定项目版本运行可追溯的生产预检，结果会写入项目时间线。 */
export const runCreativePreflight = (projectId: number | string, versionId: number | string, body?: { assetId?: number | string; bundleId?: number | string }) => request<CreativePreflightReport>(
  `/api/creative/projects/${encodeURIComponent(String(projectId))}/versions/${encodeURIComponent(String(versionId))}/preflight`,
  { method: 'POST', data: body || {}, header: { 'content-type': 'application/json' } },
)

export const getLatestCreativePreflight = (projectId: number | string, versionId: number | string) => request<CreativePreflightReport>(
  `/api/creative/projects/${encodeURIComponent(String(projectId))}/versions/${encodeURIComponent(String(versionId))}/preflight/latest`,
)

/** 在提交 3D 前做非阻断的生产可行性初筛，最终仍以打样和工艺人员复核为准。 */
export const assessProductionFeasibility = (body: ProductionFeasibilityRequest) => request<ProductionFeasibilityResult>(
  '/api/creative/ai/production-feasibility',
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

export interface ReviewSubmission {
  purpose: 'personal' | 'museum_sale'
  museumId?: string
  note?: string
  /** Selected from the public creator-task board; server verifies its channel. */
  campaignKey?: string
  projectId?: number | string
  versionId?: number | string
}

export const submitAssetReview = (assetId: number | string, body: ReviewSubmission) => request<any>(
  `/api/creative/ai/consumer-assets/${encodeURIComponent(String(assetId))}/submit-review`,
  { method: 'PUT', data: body, header: { 'content-type': 'application/json' } },
)

export interface MultiViewBundleImage extends SeedreamMultiViewImage {
  assetTitle?: string
  assetStatus?: string
}

export interface MultiViewBundleSimulationImage extends SeedreamProductionSimulationImage {
  assetTitle?: string
  assetStatus?: string
}

export interface MultiViewBundle {
  id: number
  bundleId?: number
  projectId?: number
  versionId?: number
  bundleNo?: string
  inputAssetId?: number
  productKey?: string
  productName?: string
  material?: string
  productSize?: string
  viewCount?: number
  status?: 'draft' | 'review' | 'approved' | 'rejected' | string
  purpose?: 'personal' | 'museum_sale' | string
  museumId?: string
  museumName?: string
  campaignKey?: string
  note?: string
  reviewComment?: string
  reviewedBy?: string
  reviewedAt?: string
  createdAt?: string
  updatedAt?: string
  images: MultiViewBundleImage[]
  /** Complete horizontal production-simulation image, optional on old bundles. */
  simulationAssetId?: number
  simulationImage?: MultiViewBundleSimulationImage
}

export const createMultiViewBundle = (body: {
  inputAssetId: number | string
  projectId?: number | string
  versionId?: number | string
  productKey?: string
  productName?: string
  material?: string
  productSize?: string
  viewCount: 3 | 4
  images: Array<{ view: string; assetId: number | string; label?: string }>
  simulationAssetId?: number | string
}) => request<MultiViewBundle>('/api/creative/ai/consumer-multiview-bundles', {
  method: 'POST', data: body, header: { 'content-type': 'application/json' },
})

export const getMyMultiViewBundles = () => request<MultiViewBundle[]>('/api/creative/ai/consumer-multiview-bundles/my')

export const submitMultiViewBundleReview = (bundleId: number | string, body: ReviewSubmission) => request<MultiViewBundle & { success?: boolean; message?: string }>(
  `/api/creative/ai/consumer-multiview-bundles/${encodeURIComponent(String(bundleId))}/submit-review`,
  { method: 'PUT', data: body, header: { 'content-type': 'application/json' } },
)

export interface ProductionSubmission {
  assetId?: number
  bundleId?: number
  projectId?: number | string
  versionId?: number | string
  /** Stable key kept by the submit page so a retry cannot create another request. */
  idempotencyKey?: string
  requestType: 'sample' | 'bulk'
  title?: string
  quantity: number
  purpose: 'personal' | 'museum_sale'
  selfShipQuantity?: number
  museumDistribution?: Array<{ museumId: string; museumName: string; quantity: number }>
  recipientName?: string
  recipientPhone?: string
  recipientAddress?: string
  note?: string
}

export const getProductionRequests = () => request<any[]>('/api/creative/ai/consumer-production/my')
export const createSamplePaymentOrder = (requestId: number | string, channel: PaymentChannel = 'wechat_jsapi') => request<PaymentOrder>('/api/payments/sample-orders', {
  method: 'POST', data: { requestId: String(requestId), channel }, header: { 'content-type': 'application/json' },
})
export const createCommercialQuoteSamplePaymentOrder = (requestId: number | string, channel: PaymentChannel = 'wechat_jsapi') => request<PaymentOrder>('/api/payments/commercial-quote-sample-orders', {
  method: 'POST', data: { requestId: String(requestId), channel }, header: { 'content-type': 'application/json' },
})
export const createCommercialGuidancePaymentOrder = (guidanceId: number | string, channel: PaymentChannel = 'wechat_jsapi') => request<PaymentOrder>('/api/payments/commercial-guidance-orders', {
  method: 'POST', data: { guidanceId: String(guidanceId), channel }, header: { 'content-type': 'application/json' },
})
export const submitProductionRequest = (body: ProductionSubmission) => request<any>('/api/creative/ai/consumer-production/submit', {
  method: 'POST', data: body, header: { 'content-type': 'application/json' },
})

export interface CreativeWorkflowBlocker {
  code: string
  label: string
  reason?: string
  action?: string
}

export interface CreativeWorkflowFlow {
  code?: string
  label?: string
  phase?: string
  phaseLabel?: string
  nextAction?: string
  nextActionCode?: string
  blocked?: boolean
  blockers?: CreativeWorkflowBlocker[]
  availableActions?: string[]
  progressPercent?: number
  updatedAt?: string
}

/** One read model for review, payment, sampling, feedback, logistics and bulk handoff. */
export interface CreativeWorkflowDetail {
  request?: Record<string, any> | null
  project?: Record<string, any> | null
  version?: Record<string, any> | null
  snapshot?: Record<string, any> | null
  preflight?: Record<string, any> | null
  review?: Record<string, any> | null
  payment?: Record<string, any> | null
  sample?: { events?: SampleLifecycleEvent[]; count?: number } | null
  logistics?: SampleLogistics | null
  timeline?: Array<Record<string, any>>
  flow?: CreativeWorkflowFlow
}

export const getCreativeWorkflowDetail = (requestId: number | string) => request<CreativeWorkflowDetail>(
  `/api/creative/workflow/requests/${encodeURIComponent(String(requestId))}`,
)

export interface SampleLifecycleEvent {
  id: number | string
  requestId?: number | string
  eventType?: string
  decision?: 'accept' | 'revision_required' | 'reject' | string
  rating?: number | null
  comment?: string
  issueTagsJson?: string | string[] | null
  evidenceAssetIdsJson?: string | string[] | null
  payloadJson?: string | Record<string, any> | null
  createdBy?: number | string
  createdAt?: string
}

export interface SampleLifecycle {
  id: number | string
  requestNo?: string
  requestType?: 'sample' | 'bulk' | string
  title?: string
  assetId?: number | string
  bundleId?: number | string
  status?: string
  samplePaymentStatus?: string
  projectId?: number | string
  versionId?: number | string
  sampleWorkflowStatus?: 'not_started' | 'received' | 'revision_required' | 'rejected' | 'accepted' | 'bulk_unlocked' | string
  sampleReceivedAt?: string
  sampleAcceptedAt?: string
  sampleRevisionCount?: number
  bulkUnlockedAt?: string
  bulkUnlockedBy?: number | string
  events?: SampleLifecycleEvent[]
}

/** Request-scoped sample shipment projection shared by factory and C端. */
export interface SampleLogisticsTrace {
  id: number | string
  logisticsId?: number | string
  requestId?: number | string
  eventType?: string
  status?: string
  alertLevel?: 'normal' | 'warning' | 'exception' | string
  location?: string
  content?: string
  payloadJson?: string | Record<string, any> | null
  createdBy?: number | string
  createdAt?: string
}

export interface SampleLogistics {
  id?: number | string
  logisticsId?: number | string
  requestId?: number | string
  requestNo?: string
  userId?: number | string
  carrierCode?: string
  carrierName?: string
  trackingNo?: string
  status?: 'pending' | 'shipped' | 'in_transit' | 'delivering' | 'signed' | 'exception' | 'returned' | string
  latestTrace?: string
  alertLevel?: 'normal' | 'warning' | 'exception' | string
  alertStatus?: 'open' | 'acknowledged' | 'resolved' | string
  exceptionNote?: string
  shippedAt?: string
  signedAt?: string
  estimatedArrival?: string
  lastSyncedAt?: string
  createdAt?: string
  updatedAt?: string
  traces?: SampleLogisticsTrace[]
}

function sampleLifecyclePath(projectId: number | string, versionId: number | string, requestId: number | string, action = '') {
  const base = `/api/creative/projects/${encodeURIComponent(String(projectId))}/versions/${encodeURIComponent(String(versionId))}/sample-lifecycle/${encodeURIComponent(String(requestId))}`
  return action ? `${base}/${action}` : base
}

/** 查询样品打样后的收货、反馈、返修、验收与量产解锁状态。 */
export const getSampleLifecycle = (projectId: number | string, versionId: number | string, requestId: number | string) => request<SampleLifecycle>(
  sampleLifecyclePath(projectId, versionId, requestId),
)

/** Query shipment and exception state for the current user's sample request. */
export const getSampleLogistics = (projectId: number | string, versionId: number | string, requestId: number | string) => request<SampleLogistics>(
  sampleLifecyclePath(projectId, versionId, requestId, 'logistics'),
)

export interface SampleFeedbackPayload {
  decision: 'accept' | 'revision_required' | 'reject'
  rating?: number
  comment?: string
  issueTags?: string[]
  evidenceAssetIds?: Array<number | string>
}

/** 提交样品反馈；accept 建议使用 acceptSample，以便记录正式验收时间。 */
export const submitSampleFeedback = (projectId: number | string, versionId: number | string, requestId: number | string, body: SampleFeedbackPayload) => request<SampleLifecycle>(
  sampleLifecyclePath(projectId, versionId, requestId, 'feedback'),
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

export const requestSampleRevision = (projectId: number | string, versionId: number | string, requestId: number | string, body: Omit<SampleFeedbackPayload, 'decision' | 'rating'>) => request<SampleLifecycle>(
  sampleLifecyclePath(projectId, versionId, requestId, 'revision'),
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

export const acceptSample = (projectId: number | string, versionId: number | string, requestId: number | string, body: Pick<SampleFeedbackPayload, 'rating' | 'comment' | 'evidenceAssetIds'> = {}) => request<SampleLifecycle>(
  sampleLifecyclePath(projectId, versionId, requestId, 'accept'),
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

export const unlockSampleBulkProduction = (projectId: number | string, versionId: number | string, requestId: number | string, body: Pick<SampleFeedbackPayload, 'comment' | 'evidenceAssetIds'> = {}) => request<SampleLifecycle>(
  sampleLifecyclePath(projectId, versionId, requestId, 'bulk-unlock'),
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

/** 申请只读、5 分钟有效且绑定单个作品的媒体访问地址。 */
export const getAssetPreviewAccess = (assetId: number | string) => request<{ url?: string; previewUrl?: string; accessToken?: string; expiresIn?: number }>(
  `/api/creative/ai/assets/${encodeURIComponent(String(assetId))}/preview-access`,
  { method: 'POST' },
)
export const getModelPreviewAccess = getAssetPreviewAccess

export interface MaterialLabAccess {
  assetId: number | string
  /** 仅供同源 HTTPS 材质实验室读取当前模型的短时地址。 */
  modelUrl?: string
  /** 仅绑定当前作品、5 分钟有效；不可作为普通登录令牌。 */
  accessToken?: string
  expiresIn?: number
}

/**
 * 为当前用户自己的 3D 作品签发一次短时、单作品的材质实验室会话。
 * 小程序不会将自己的登录 JWT 传给 H5；H5 仅能读取该模型并保存其材质版本。
 */
export const getMaterialLabAccess = (assetId: number | string) => request<MaterialLabAccess>(
  `/api/creative/ai/assets/${encodeURIComponent(String(assetId))}/material-lab-access`,
  { method: 'POST' },
)

export const getPackages = () => request<any>('/api/payments/packages')

/**
 * Only the temporary code returned by `uni.login` is sent to the server.
 * The server exchanges it with WeChat and stores the resulting OpenID; neither
 * an AppSecret nor any merchant key may ever be bundled into the mini program.
 */
export const bindWechatMiniapp = (code: string) => request<{ bound: boolean; openIdBound: boolean }>('/api/payments/wechat/bind', {
  method: 'POST', data: { code }, header: { 'content-type': 'application/json' },
})

export type PaymentChannel = 'manual_wechat_qr' | 'wechat_jsapi' | 'wechat_virtual_payment'

export interface WechatJsapiPaymentParams {
  timeStamp: string | number
  nonceStr: string
  package: string
  signType: 'RSA' | 'MD5' | 'HMAC-SHA256' | string
  paySign: string
}

export interface WechatVirtualPaymentParams {
  mode: 'short_series_coin'
  signData: string
  paySig: string
  signature: string
}

export interface PaymentOrder {
  orderNo: string
  packageCode?: string
  channel?: PaymentChannel | string
  status?: string
  packageName?: string
  credits?: number
  amountYuan?: number | string
  amountFen?: number | string
  codeUrl?: string
  createdAt?: string
  refundStatus?: string
  paymentParams?: WechatJsapiPaymentParams
  virtualPayment?: WechatVirtualPaymentParams
}

export const createPaymentOrder = (packageCode: string, channel: PaymentChannel = 'wechat_virtual_payment') => request<PaymentOrder>('/api/payments/orders', {
  method: 'POST', data: { packageCode, channel }, header: { 'content-type': 'application/json' },
})
export const manualComplete = (orderNo: string) => request<any>(`/api/payments/orders/${encodeURIComponent(orderNo)}/manual-complete`, { method: 'POST' })
/** Close an unpaid order before switching payment methods; never call this after a confirmed payment. */
export const closePaymentOrderOnServer = (orderNo: string) => request<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}/close`, { method: 'POST' })
export const getPaymentOrders = () => request<any[]>('/api/payments/orders')
export const getPaymentOrder = (orderNo: string) => request<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}`)
export const getWechatPaymentParams = (orderNo: string) => request<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}/payment-params`, { method: 'POST' })
export const cancelVirtualPaymentOrder = (orderNo: string) => request<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}/virtual-cancel`, { method: 'POST' })

/**
 * 面向普通创作者开放的品牌风格档案。风格内容由后台维护，避免把一套固定的
 * 文案和文化约束硬编码在小程序里。
 */
export interface CreativeStyleProfile {
  id: number
  name: string
  description?: string
  basePrompt?: string
  negativePrompt?: string
  palette?: string
  culturalGuardrails?: string
}

export const getCreativeStyleProfiles = () => request<CreativeStyleProfile[]>('/api/creative/ai/styles')

export interface ComposeCreativePromptRequest {
  title?: string
  prompt: string
  negativePrompt?: string
  styleId?: number
  scene?: string
  productType?: string
  productCategory?: string
  material?: string
}

export interface ComposedCreativePrompt {
  prompt: string
  negativePrompt: string
  styleName: string
  guardrails: string
}

/**
 * 使用当前风格档案组合一份可带入创作页的提示词。这是服务端规则组合，不会
 * 触发外部生图服务，也不会消耗用户创作积分。
 */
export const composeCreativePrompt = (body: ComposeCreativePromptRequest) => request<ComposedCreativePrompt>(
  '/api/creative/ai/prompt/compose',
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)
