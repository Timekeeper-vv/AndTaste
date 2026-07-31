import { request, uploadFile } from './client'
import { getSession } from '../utils/session'

const userId = () => getSession()?.user.id || 0
export const getMuseums = () => request<any[]>('/api/creative/ai/consumer-production/museums')
export const getCredits = () => request<any>(`/api/creative/ai/consumer-credits/account?currentUserId=${userId()}`)
export const getCreditRules = () => request<any>('/api/creative/ai/consumer-credits/rules')
export const getAssets = () => request<any[]>(`/api/creative/ai/assets?currentUserId=${userId()}&limit=100`)
export const createImage = (body: any) => request<any>('/api/creative/ai/jimeng/text-to-image', { method: 'POST', data: { ...body, currentUserId: userId() }, header: { 'content-type': 'application/json' } })
export const createModel = (body: any) => request<any>('/api/creative/ai/tripo/generate', { method: 'POST', data: { ...body, currentUserId: userId() }, header: { 'content-type': 'application/json' } })
export const uploadReference = (filePath: string) => uploadFile<any>(`/api/creative/ai/assets/upload?currentUserId=${userId()}`, filePath)
export const getPackages = () => request<any>('/api/payments/packages')
export const createPaymentOrder = (packageCode: string) => request<any>('/api/payments/orders', { method: 'POST', data: { packageCode, channel: 'manual_wechat_qr' }, header: { 'content-type': 'application/json' } })
export const manualComplete = (orderNo: string) => request<any>(`/api/payments/orders/${encodeURIComponent(orderNo)}/manual-complete`, { method: 'POST' })
