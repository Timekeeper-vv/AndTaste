import { request, uploadFile } from './client'

/**
 * 这里的接口全部通过 client.ts 自动携带的 Bearer Token 识别当前用户。
 * 不要再从小程序提交 currentUserId、role 之类可以被篡改的身份参数。
 */
export const getMuseums = () => request<any[]>('/api/creative/ai/consumer-production/museums')
export const getCredits = () => request<any>('/api/creative/ai/consumer-credits/account')
export const getCreditRules = () => request<any>('/api/creative/ai/consumer-credits/rules')
export const getRewardOverview = () => request<any>('/api/creative/ai/consumer-rewards/overview')
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

export const createImage = (body: any) => request<any>('/api/creative/ai/jimeng/text-to-image', { method: 'POST', data: body, header: { 'content-type': 'application/json' } })

export interface ImagePromptOptimizeRequest {
  prompt: string
  provider?: string
  productCategory?: string
  material?: string
}

export interface ImagePromptOptimizeResult {
  prompt?: string
  usageGuide?: string
}

/** 将灵感描述优化为真实图片生成服务可执行的商业产品提示词。 */
export const optimizeImagePrompt = (body: ImagePromptOptimizeRequest) => request<ImagePromptOptimizeResult>(
  '/api/creative/ai/prompt/tripo-optimize',
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

/**
 * 参考图改造沿用 C 端已经验证的图文结合接口；小程序只上传用户选择的
 * 原图并传回资产编号，服务端仍负责权限校验、生成和作品入库。
 */
export const createImageWithReference = (body: any) => request<any>('/api/creative/ai/image-to-image', { method: 'POST', data: body, header: { 'content-type': 'application/json' } })

export interface SeedreamMultiViewRequest {
  /** 用户上传并已归属到当前账号的单张参考图资产。 */
  inputAssetId: number | string
  /** 产品/角色描述。服务端会为正、左、背、右四个真实请求补齐视角约束。 */
  prompt: string
  /** Seedream 多视图接口当前只接受 1K 或 2K。 */
  size?: '1K' | '2K'
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

export interface SeedreamMultiViewResult {
  provider?: string
  model?: string
  message?: string
  images: SeedreamMultiViewImage[]
}

/**
 * 用一张已上传的参考图真实调用 Doubao Seedream，服务端顺序生成正、左、背、右
 * 四张图，并把每张图都保存为当前用户可访问的资产。它不会伪造本地预览结果。
 */
export const createSeedreamMultiView = (body: SeedreamMultiViewRequest) => request<SeedreamMultiViewResult>(
  '/api/creative/ai/volcengine/seedream/multiview',
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

export const createModel = (body: any) => request<any>('/api/creative/ai/tripo/generate', { method: 'POST', data: body, header: { 'content-type': 'application/json' } })
export const uploadReference = (filePath: string) => uploadFile<any>('/api/creative/ai/assets/upload', filePath)

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
  disclaimer?: string
}

/** 在提交 3D 前做非阻断的生产可行性初筛，最终仍以打样和工艺人员复核为准。 */
export const assessProductionFeasibility = (body: ProductionFeasibilityRequest) => request<ProductionFeasibilityResult>(
  '/api/creative/ai/production-feasibility',
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)

export interface ReviewSubmission {
  purpose: 'personal' | 'museum_sale'
  museumId?: string
  note?: string
}

export const submitAssetReview = (assetId: number | string, body: ReviewSubmission) => request<any>(
  `/api/creative/ai/consumer-assets/${encodeURIComponent(String(assetId))}/submit-review`,
  { method: 'PUT', data: body, header: { 'content-type': 'application/json' } },
)

export interface ProductionSubmission {
  assetId: number
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
export const submitProductionRequest = (body: ProductionSubmission) => request<any>('/api/creative/ai/consumer-production/submit', {
  method: 'POST', data: body, header: { 'content-type': 'application/json' },
})

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

export type PaymentChannel = 'manual_wechat_qr' | 'wechat_jsapi'

export interface WechatJsapiPaymentParams {
  timeStamp: string | number
  nonceStr: string
  package: string
  signType: 'RSA' | 'MD5' | 'HMAC-SHA256' | string
  paySign: string
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
}

export const createPaymentOrder = (packageCode: string, channel: PaymentChannel = 'manual_wechat_qr') => request<PaymentOrder>('/api/payments/orders', {
  method: 'POST', data: { packageCode, channel }, header: { 'content-type': 'application/json' },
})
export const manualComplete = (orderNo: string) => request<any>(`/api/payments/orders/${encodeURIComponent(orderNo)}/manual-complete`, { method: 'POST' })
/** Close an unpaid order before switching payment methods; never call this after a confirmed payment. */
export const closePaymentOrderOnServer = (orderNo: string) => request<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}/close`, { method: 'POST' })
export const getPaymentOrders = () => request<any[]>('/api/payments/orders')
export const getPaymentOrder = (orderNo: string) => request<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}`)
export const getWechatPaymentParams = (orderNo: string) => request<PaymentOrder>(`/api/payments/orders/${encodeURIComponent(orderNo)}/payment-params`, { method: 'POST' })

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
