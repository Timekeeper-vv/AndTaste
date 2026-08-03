import { request } from './client'

/**
 * C 端文创商城接口。
 *
 * 所有读取接口都来自已审核、可公开浏览的商城数据；创建订单仍由 client.ts
 * 自动附带当前用户的 Bearer Token，服务端会重新校验 SKU、库存和用户归属。
 */

export interface MarketplaceCategory {
  id: number
  name: string
  description?: string | null
  sortOrder?: number
}

export interface MarketplaceTag {
  id: number
  name: string
}

export interface MarketplaceArtwork {
  id: number
  title: string
  subtitle?: string | null
  imageUrl?: string | null
  thumbnailUrl?: string | null
  story?: string | null
  licenseType?: string | null
  saleStatus?: string | null
  viewCount?: number
  favoriteCount?: number
  categoryId?: number | null
  categoryName?: string | null
  designerId?: number | null
  designerName?: string | null
  designerBio?: string | null
  minPrice?: number | string | null
  skuCount?: number
  tags?: MarketplaceTag[]
  skus?: MarketplaceSku[]
}

export interface MarketplaceSku {
  id: number
  artworkId: number
  skuCode?: string | null
  productName: string
  productType?: string | null
  coverUrl?: string | null
  price: number | string
  originalPrice?: number | string | null
  stock?: number | null
  material?: string | null
  size?: string | null
  status?: string | null
  artworkTitle?: string | null
  designerName?: string | null
}

export interface MarketplaceDesigner {
  id: number
  brandName?: string | null
  bio?: string | null
  revenueShare?: number | string | null
  auditStatus?: string | null
  displayName?: string | null
  avatarUrl?: string | null
  artworkCount?: number
}

export interface MarketplaceOrderItem {
  id?: number
  skuId?: number
  artworkId?: number
  productName?: string | null
  artworkTitle?: string | null
  coverUrl?: string | null
  unitPrice?: number | string | null
  quantity?: number | null
  subtotal?: number | string | null
}

export interface MarketplaceOrder {
  id: number
  orderNo: string
  userId?: number
  buyerName?: string | null
  totalAmount?: number | string | null
  payAmount?: number | string | null
  paymentMethod?: string | null
  orderStatus?: string | null
  remark?: string | null
  createdAt?: string | null
  items?: MarketplaceOrderItem[]
}

export interface CreateMarketplaceOrderRequest {
  /** 服务端始终使用 JWT 当前用户；不要传递 userId。 */
  items: Array<{ skuId: number; quantity: number }>
  /** 仅作为待支付订单的未来结算方式标记，并不会在小程序端伪造支付结果。 */
  paymentMethod: 'wechat' | 'manual_wechat_qr'
  remark?: string
}

export interface CreateMarketplaceOrderResult {
  orderId: number
  orderNo: string
  payAmount: number | string
  orderStatus: 'pending_pay' | string
  paymentRequired: boolean
}

export interface MarketplaceArtworkFilters {
  keyword?: string
  categoryId?: number | string
}

function queryString(filters: MarketplaceArtworkFilters = {}) {
  const params: string[] = []
  const keyword = filters.keyword?.trim()
  if (keyword) params.push(`keyword=${encodeURIComponent(keyword)}`)
  if (filters.categoryId !== undefined && filters.categoryId !== null && String(filters.categoryId).trim()) {
    params.push(`categoryId=${encodeURIComponent(String(filters.categoryId))}`)
  }
  return params.length ? `?${params.join('&')}` : ''
}

export const getMarketplaceCategories = () => request<MarketplaceCategory[]>('/api/creative/categories')
export const getMarketplaceTags = () => request<MarketplaceTag[]>('/api/creative/tags')
export const getMarketplaceArtworks = (filters?: MarketplaceArtworkFilters) => request<MarketplaceArtwork[]>(`/api/creative/artworks${queryString(filters)}`)
export const getMarketplaceArtwork = (artworkId: number | string) => request<MarketplaceArtwork>(`/api/creative/artworks/${encodeURIComponent(String(artworkId))}`)
export const getMarketplaceSkus = (artworkId?: number | string) => request<MarketplaceSku[]>(`/api/creative/skus${artworkId === undefined || artworkId === null ? '' : `?artworkId=${encodeURIComponent(String(artworkId))}`}`)
export const getMarketplaceDesigners = () => request<MarketplaceDesigner[]>('/api/creative/designers')
export const getMarketplaceOrders = () => request<MarketplaceOrder[]>('/api/creative/orders')
export const createMarketplaceOrder = (payload: CreateMarketplaceOrderRequest) => request<CreateMarketplaceOrderResult>('/api/creative/orders', {
  method: 'POST',
  data: payload,
  header: { 'content-type': 'application/json' },
})
