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
export const createPaymentOrder = (packageCode: string) => request<any>('/api/payments/orders', { method: 'POST', data: { packageCode, channel: 'manual_wechat_qr' }, header: { 'content-type': 'application/json' } })
export const manualComplete = (orderNo: string) => request<any>(`/api/payments/orders/${encodeURIComponent(orderNo)}/manual-complete`, { method: 'POST' })
export const getPaymentOrders = () => request<any[]>('/api/payments/orders')
