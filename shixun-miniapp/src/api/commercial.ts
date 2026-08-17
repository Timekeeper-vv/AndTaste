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
  selectionDemands: any[]
  summary?: {
    quoteRequestCount?: number
    consignmentApplicationCount?: number
    selectionDemandCount?: number
  }
  syncedAt?: string
}

export interface CachedCommercialRequests {
  data: CommercialRequests
  savedAt: number
}

const COMMERCIAL_REQUEST_CACHE_PREFIX = 'smart_pig_commercial_requests:'
const COMMERCIAL_REQUEST_CACHE_MAX_AGE = 30 * 24 * 60 * 60 * 1000
const COMMERCIAL_PENDING_REQUEST_MAX_AGE = 10 * 60 * 1000
let commercialRequestFetchSerial = 0
let commercialRequestInFlight: Promise<CommercialRequests> | null = null

const commercialRequestFields = [
  'id', 'requestNo', 'applicationNo', 'assetId', 'requestType', 'quantity', 'purpose', 'status',
  'quotedUnitPrice', 'quotedTotalPrice', 'quotedLeadTime', 'operatorComment',
  'samplePaymentStatus', 'samplePaymentOrderNo', 'samplePaidAt', 'templateCode', 'productName',
  'channelId', 'channelName', 'salesMode', 'creatorSharePercent', 'platformServicePercent',
  'optionId', 'optionKey', 'optionName', 'theme', 'budgetMax', 'audience', 'occasion', 'note',
  'createdAt', 'updatedAt',
]

function normalizedFieldName(value: string) {
  return value.replace(/[_-]/g, '').toLowerCase()
}

/**
 * Preserve one field shape for both pages even when a proxy, older server, or
 * JDBC driver changes the casing of SQL column labels. Product progress must
 * never discard an otherwise valid commercial request because `ID` arrived
 * instead of `id`.
 */
function normalizeCommercialRequestRow(value: any) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return value
  const byNormalizedName = new Map(Object.entries(value).map(([key, fieldValue]) => [normalizedFieldName(key), fieldValue]))
  const normalized: Record<string, any> = { ...value }
  commercialRequestFields.forEach(field => {
    const fieldValue = byNormalizedName.get(normalizedFieldName(field))
    if (fieldValue !== undefined) normalized[field] = fieldValue
  })
  return normalized
}

function normalizeCommercialRequestRows(value: unknown) {
  return Array.isArray(value) ? value.map(normalizeCommercialRequestRow) : []
}

function commercialRequestCacheKey() {
  const session = getSession()
  const username = session?.user?.username
  if (!username) return ''
  // Keep cached records separate when the same device changes accounts.
  const tokenTail = String(session?.token || '').slice(-16)
  return `${COMMERCIAL_REQUEST_CACHE_PREFIX}${encodeURIComponent(`${username}:${tokenTail}`)}`
}

function normalizeCommercialRequests(value: any): CommercialRequests {
  const payload = value && typeof value === 'object' && !Array.isArray(value)
    && !Array.isArray(value.quoteRequests) && value.data && typeof value.data === 'object'
    ? value.data
    : value
  return {
    quoteRequests: normalizeCommercialRequestRows(payload?.quoteRequests),
    consignmentApplications: normalizeCommercialRequestRows(payload?.consignmentApplications),
    selectionDemands: normalizeCommercialRequestRows(payload?.selectionDemands),
    summary: payload?.summary && typeof payload.summary === 'object' ? payload.summary : undefined,
    syncedAt: typeof payload?.syncedAt === 'string' ? payload.syncedAt : undefined,
  }
}

function commercialPendingKey() {
  const key = commercialRequestCacheKey()
  return key ? `${key}:pending` : ''
}

function requestIdentity(value: any) {
  return String(value?.id || value?.requestNo || value?.applicationNo || '')
}

function sameCommercialRequest(left: any, right: any) {
  const keys = (value: any) => [value?.id, value?.requestNo, value?.applicationNo]
    .map(item => String(item || ''))
    .filter(Boolean)
  const leftKeys = keys(left)
  const rightKeys = new Set(keys(right))
  return leftKeys.some(key => rightKeys.has(key))
}

function readPendingCommercialRequests(): Array<{ kind: 'quote' | 'consignment'; request: any; savedAt: number }> {
  const key = commercialPendingKey()
  if (!key) return []
  const raw = uni.getStorageSync(key)
  if (!Array.isArray(raw)) return []
  const valid = raw.filter(item => item && ['quote', 'consignment'].includes(item.kind)
    && item.request && typeof item.savedAt === 'number'
    && Date.now() - item.savedAt <= COMMERCIAL_PENDING_REQUEST_MAX_AGE)
  if (valid.length !== raw.length) uni.setStorageSync(key, valid)
  return valid
}

function mergePendingCommercialRequests(data: CommercialRequests): CommercialRequests {
  const pending = readPendingCommercialRequests()
  if (!pending.length) return data
  const quoteRequests = [...data.quoteRequests]
  const consignmentApplications = [...data.consignmentApplications]
  const remaining: typeof pending = []
  pending.forEach(item => {
    const target = item.kind === 'quote' ? quoteRequests : consignmentApplications
    const request = normalizeCommercialRequestRow(item.request)
    const identity = requestIdentity(request)
    const index = identity ? target.findIndex(row => sameCommercialRequest(row, request)) : -1
    if (index >= 0) {
      target[index] = { ...request, ...target[index] }
    } else {
      target.unshift(request)
      remaining.push({ ...item, request })
    }
  })
  const key = commercialPendingKey()
  if (key) {
    if (remaining.length) uni.setStorageSync(key, remaining)
    else uni.removeStorageSync(key)
  }
  return { ...data, quoteRequests, consignmentApplications }
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
  return { data: mergePendingCommercialRequests(normalizeCommercialRequests(cached.data)), savedAt: cached.savedAt }
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

export interface CommercialRequestFetchOptions {
  /** Bypass an older in-flight request and read the current server state. */
  force?: boolean
}

/**
 * Reads the current account's applications. The sequence guard prevents a
 * slower, older response from overwriting a newer response after navigation
 * back from the submission page.
 */
export function getCommercialRequests(options: CommercialRequestFetchOptions = {}): Promise<CommercialRequests> {
  if (!options.force && commercialRequestInFlight) return commercialRequestInFlight

  const serial = ++commercialRequestFetchSerial
  const path = `/api/commercial/consumer/requests?_refresh=${Date.now()}_${serial}`
  const promise = request<unknown>(path, {
    timeout: 30000,
    header: { 'Cache-Control': 'no-cache', Pragma: 'no-cache' },
  }).then(value => {
    const data = mergePendingCommercialRequests(normalizeCommercialRequests(value))
    if (serial === commercialRequestFetchSerial) cacheCommercialRequests(data)
    return data
  })
  commercialRequestInFlight = promise
  void promise.then(
    () => { if (commercialRequestInFlight === promise) commercialRequestInFlight = null },
    () => { if (commercialRequestInFlight === promise) commercialRequestInFlight = null },
  )
  return promise
}

/** Merge a successful submission into local state if the follow-up read is delayed. */
export function rememberCommercialRequest(kind: 'quote' | 'consignment', request: any): CommercialRequests {
  const cached = getCachedCommercialRequests()?.data || { quoteRequests: [], consignmentApplications: [], selectionDemands: [] }
  const key = kind === 'quote' ? 'quoteRequests' : 'consignmentApplications'
  const rows = [...cached[key]]
  const normalizedRequest = normalizeCommercialRequestRow(request)
  const identity = requestIdentity(normalizedRequest)
  const existingIndex = identity ? rows.findIndex(row => sameCommercialRequest(row, normalizedRequest)) : -1
  if (existingIndex >= 0) rows[existingIndex] = { ...rows[existingIndex], ...normalizedRequest }
  else rows.unshift(normalizedRequest)
  const merged: CommercialRequests = {
    ...cached,
    [key]: rows,
  }
  cacheCommercialRequests(merged)
  const pendingKey = commercialPendingKey()
  if (pendingKey && identity) {
    const pending = readPendingCommercialRequests()
      .filter(item => !(item.kind === kind && sameCommercialRequest(item.request, normalizedRequest)))
    pending.unshift({ kind, request: normalizedRequest, savedAt: Date.now() })
    uni.setStorageSync(pendingKey, pending)
  }
  return merged
}

export const acceptCommercialQuote = (id: number) => request<any>(`/api/commercial/consumer/quote-requests/${id}/accept`, { method: 'POST' })
