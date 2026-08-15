import { request } from './client'
import { getSession } from '../utils/session'

export interface CommercialProduct {
  id: number
  templateCode: string
  optionKey?: string
  productName: string
  productType: string
  categoryKey?: string
  categoryName?: string
  material: string
  process: string
  specification: string
  sampleMoq: number
  bulkMoq: number
  sampleFeeYuan?: number | null
  indicativeRetailDisplay: string
  sampleLeadTime: string
  bulkLeadTime: string
  supplyStatus: string
  fulfillmentMode: string
  copyrightRequirement: string
  copyrightStatementVersion?: string
  copyrightStatement?: string
  coverImageUrl?: string
}

export interface CommercialChannel {
  id: number
  channelCode: string
  name: string
  province?: string
  city?: string
  district?: string
  channelType: string
  sourceType: string
  cooperationStatus: string
  notes?: string
  cooperationNotice?: string
}

export interface CommercialChannelDirectory {
  items: CommercialChannel[]
  total: number
  page: number
  size: number
  provinces: Array<{ province: string; count: number }>
}

export interface CommercialRequests {
  quoteRequests: any[]
  consignmentApplications: any[]
  summary?: {
    quoteRequestCount?: number
    consignmentApplicationCount?: number
  }
  syncedAt?: string
}

export interface CachedCommercialRequests {
  data: CommercialRequests
  savedAt: number
}

const COMMERCIAL_REQUEST_CACHE_PREFIX = 'smart_pig_commercial_requests:'
const COMMERCIAL_REQUEST_CACHE_MAX_AGE = 30 * 24 * 60 * 60 * 1000

function commercialRequestCacheKey() {
  const username = getSession()?.user?.username
  return username ? `${COMMERCIAL_REQUEST_CACHE_PREFIX}${encodeURIComponent(username)}` : ''
}

function normalizeCommercialRequests(value: any): CommercialRequests {
  const payload = value && typeof value === 'object' && !Array.isArray(value)
    && !Array.isArray(value.quoteRequests) && value.data && typeof value.data === 'object'
    ? value.data
    : value
  return {
    quoteRequests: Array.isArray(payload?.quoteRequests) ? payload.quoteRequests : [],
    consignmentApplications: Array.isArray(payload?.consignmentApplications) ? payload.consignmentApplications : [],
    summary: payload?.summary && typeof payload.summary === 'object' ? payload.summary : undefined,
    syncedAt: typeof payload?.syncedAt === 'string' ? payload.syncedAt : undefined,
  }
}

function cacheCommercialRequests(data: CommercialRequests) {
  const key = commercialRequestCacheKey()
  if (!key) return data
  uni.setStorageSync(key, { data, savedAt: Date.now() } satisfies CachedCommercialRequests)
  return data
}

/** Returns only a record saved for the currently authenticated mini-program account. */
export function getCachedCommercialRequests(): CachedCommercialRequests | null {
  const key = commercialRequestCacheKey()
  if (!key) return null
  const cached = uni.getStorageSync(key) as CachedCommercialRequests | undefined
  if (!cached || typeof cached.savedAt !== 'number' || Date.now() - cached.savedAt > COMMERCIAL_REQUEST_CACHE_MAX_AGE) {
    if (cached) uni.removeStorageSync(key)
    return null
  }
  return { data: normalizeCommercialRequests(cached.data), savedAt: cached.savedAt }
}

export const getCommercialProducts = () => request<CommercialProduct[]>('/api/commercial/consumer/products')

export const getCommercialChannels = (keyword?: string) => request<CommercialChannel[]>(`/api/commercial/consumer/channels${keyword ? `?keyword=${encodeURIComponent(keyword)}` : ''}`)

export const getCommercialChannelDirectory = (params: { keyword?: string; province?: string; region?: string; type?: string; page?: number; size?: number } = {}) => {
  const query = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && String(value).trim() !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&')
  return request<CommercialChannelDirectory>(`/api/commercial/consumer/channel-directory${query ? `?${query}` : ''}`)
}

export const createQuoteRequest = (body: Record<string, unknown>) => request<any>('/api/commercial/consumer/quote-requests', { method: 'POST', data: body, header: { 'content-type': 'application/json' } })

export const createConsignmentApplication = (body: Record<string, unknown>) => request<any>('/api/commercial/consumer/consignment-applications', { method: 'POST', data: body, header: { 'content-type': 'application/json' } })

export const getCommercialRequests = async () => cacheCommercialRequests(normalizeCommercialRequests(
  await request<unknown>('/api/commercial/consumer/requests'),
))

export const acceptCommercialQuote = (id: number) => request<any>(`/api/commercial/consumer/quote-requests/${id}/accept`, { method: 'POST' })
