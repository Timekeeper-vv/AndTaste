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
export const createImage = (body: any) => request<any>('/api/creative/ai/jimeng/text-to-image', { method: 'POST', data: body, header: { 'content-type': 'application/json' } })
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
