/**
 * Resolve a private creative asset into a short-lived, asset-bound URL.
 *
 * The regular login JWT must stay in the Authorization header.  It must not be
 * copied into an image/model URL because URLs are commonly retained by browser
 * history, proxy logs, analytics and referrer headers.
 */
export interface AssetPreviewAccess {
  /** Signed URL for the original asset (model binary or image content). */
  url: string
  /** Signed URL for the image used by cards and model thumbnails. */
  previewUrl: string
  expiresIn: number
}

function signedAssetUrl(value: unknown, assetId: string): string {
  if (typeof value !== 'string' || !value) return ''
  try {
    const parsed = new URL(value, window.location.origin)
    const expectedPrefix = `/api/creative/ai/assets/${assetId}/`
    const allowedEndpoint = /^(?:content|model-content|preview-content)$/
    const endpoint = parsed.pathname.startsWith(expectedPrefix)
      ? parsed.pathname.slice(expectedPrefix.length)
      : ''
    // The backend returns same-origin, asset-bound media URLs. Reject legacy
    // /generated and /uploads paths (and arbitrary remote URLs) at the edge.
    if (parsed.origin !== window.location.origin || !allowedEndpoint.test(endpoint)) return ''
    if (!parsed.searchParams.get('access_token')) return ''
    return `${parsed.pathname}${parsed.search}`
  } catch {
    return ''
  }
}

export async function requestAssetPreviewAccess(assetId: number | string): Promise<AssetPreviewAccess> {
  const id = encodeURIComponent(String(assetId))
  const response = await fetch(`/api/creative/ai/assets/${id}/preview-access`, {
    method: 'POST',
    headers: { Accept: 'application/json' },
    cache: 'no-store',
  })
  const data = await response.json().catch(() => null) as {
    url?: unknown
    previewUrl?: unknown
    expiresIn?: unknown
    message?: unknown
  } | null
  if (!response.ok) throw new Error(String(data?.message || `HTTP ${response.status}`))
  const url = signedAssetUrl(data?.url, id)
  const previewUrl = signedAssetUrl(data?.previewUrl, id)
  if (!url || !previewUrl) throw new Error('服务端未返回有效的作品预览地址')
  const expiresIn = Number(data?.expiresIn)
  return { url, previewUrl, expiresIn: Number.isFinite(expiresIn) && expiresIn > 0 ? expiresIn : 300 }
}

export async function requestAssetPreviewUrl(assetId: number | string): Promise<string> {
  const access = await requestAssetPreviewAccess(assetId)
  return access.url
}
