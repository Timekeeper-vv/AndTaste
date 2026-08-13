import { request } from './client'

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

export const getCommercialRequests = () => request<{ quoteRequests: any[]; consignmentApplications: any[] }>('/api/commercial/consumer/requests')

export const acceptCommercialQuote = (id: number) => request<any>(`/api/commercial/consumer/quote-requests/${id}/accept`, { method: 'POST' })
