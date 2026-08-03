import { request, uploadFile } from './client'

/**
 * 这里的接口全部通过 client.ts 自动携带的 Bearer Token 识别当前用户。
 * 不要再从小程序提交 currentUserId、role 之类可以被篡改的身份参数。
 */
export const getMuseums = () => request<any[]>('/api/creative/ai/consumer-production/museums')
export const getCredits = () => request<any>('/api/creative/ai/consumer-credits/account')
export const getCreditRules = () => request<any>('/api/creative/ai/consumer-credits/rules')
export const getAssets = (type?: string) => request<any[]>(`/api/creative/ai/assets${type ? `?type=${encodeURIComponent(type)}` : ''}`)
export const getJobs = () => request<any[]>('/api/creative/ai/jobs')

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
export const submitProductionRequest = (body: ProductionSubmission) => request<any>('/api/creative/ai/consumer-production/submit', {
  method: 'POST', data: body, header: { 'content-type': 'application/json' },
})

/** 申请只读、5 分钟有效且绑定单个作品的媒体访问地址。 */
export const getAssetPreviewAccess = (assetId: number | string) => request<{ url?: string; previewUrl?: string; accessToken?: string; expiresIn?: number }>(
  `/api/creative/ai/assets/${encodeURIComponent(String(assetId))}/preview-access`,
  { method: 'POST' },
)
export const getModelPreviewAccess = getAssetPreviewAccess

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
