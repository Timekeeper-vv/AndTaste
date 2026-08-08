import { request } from './client'

export interface SelectionFilters {
  category?: string
  theme?: string
  audience?: string
  occasion?: string
  budgetMax?: number | string
  assetId?: number | string
  size?: number
}

export interface SelectionOption {
  id: number
  optionKey: string
  categoryKey: string
  categoryName: string
  name: string
  subtitle: string
  description: string
  material: string
  process: string
  specification: string
  sampleLeadTime: string
  bulkLeadTime: string
  retailMin?: number | null
  retailMax?: number | null
  retailDisplay: string
  tags: string
  audienceTags: string
  occasionTags: string
  budgetBand: string
  coverImageUrl?: string | null
  imageSource?: string | null
  imageRightsStatus?: string
  sourceVersion?: string
  sourcePage?: number
  favorited?: boolean
  matchScore?: number
  reason?: string
  planningNote?: string
}

export const getSelectionCategories = () => request<any[]>('/api/selection/categories')

export const getSelectionOptions = (params: SelectionFilters = {}) => {
  const query: string[] = []
  if (params.category) query.push(`category=${encodeURIComponent(params.category)}`)
  if (params.theme) query.push(`keyword=${encodeURIComponent(params.theme)}`)
  if (params.size) query.push(`size=${encodeURIComponent(String(params.size))}`)
  return request<SelectionOption[]>(`/api/selection/options${query.length ? `?${query.join('&')}` : ''}`)
}

export const getSelectionRecommendations = (params: SelectionFilters = {}) => request<{
  recommendationNo: string
  version: string
  source: string
  filters: SelectionFilters
  options: SelectionOption[]
  disclaimer: string
}>('/api/selection/recommendations', {
  method: 'POST',
  data: params,
  header: { 'content-type': 'application/json' },
})

export const addSelectionFavorite = (optionKey: string) => request<any>(
  `/api/selection/favorites/${encodeURIComponent(optionKey)}`,
  { method: 'POST' },
)

export const removeSelectionFavorite = (optionKey: string) => request<any>(
  `/api/selection/favorites/${encodeURIComponent(optionKey)}`,
  { method: 'DELETE' },
)

export const createSelectionDemand = (body: {
  optionKey: string
  assetId?: number | string
  theme?: string
  budgetMax?: number | string
  audience?: string
  occasion?: string
  note?: string
}) => request<any>('/api/selection/demands', {
  method: 'POST',
  data: body,
  header: { 'content-type': 'application/json' },
})

export const getMySelectionDemands = () => request<any[]>('/api/selection/demands/mine')
