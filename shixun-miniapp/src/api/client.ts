import { clearSession, getSession } from '../utils/session'

// 小程序正式发布时，填写 .env 的 VITE_API_BASE_URL，例如 https://api.example.com
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'https://api.example.com').replace(/\/$/, '')

function messageOf(data: any, fallback: string) {
  // uni.request may leave an error response as a JSON string when the server
  // does not send an explicit JSON content type. Parse it before falling back
  // to a generic status message so the miniapp shows the real validation error.
  if (typeof data === 'string') {
    try { data = JSON.parse(data) } catch { return data.trim() || fallback }
  }
  return data?.message || data?.error || data?.detail || fallback
}

function parsePayload(data: any) {
  if (typeof data !== 'string') return data
  try { return JSON.parse(data) } catch { return data }
}

function uploadFailureMessage(error: any) {
  const raw = String(error?.errMsg || error?.message || error || '')
  if (/url not in domain list|合法域名|not in domain/i.test(raw)) return '上传域名未配置，请在微信公众平台将 https://zhijiansk.com 添加到 uploadFile 合法域名'
  if (/ssl|certificate|cert/i.test(raw)) return '上传服务的 HTTPS 证书校验失败，请检查域名证书配置'
  if (/timeout|timed out/i.test(raw)) return '图片上传超时，请检查网络后重试'
  if (/401|unauthorized|请先登录|登录已过期/i.test(raw)) return '登录已过期，请重新登录后再上传'
  if (/413|request entity too large|file too large/i.test(raw)) return '图片文件过大，请选择 100MB 以内的 JPG、PNG 或 WEBP 图片'
  if (/dns|network|fail|connect|refused/i.test(raw)) return '无法连接上传服务，请确认微信后台已将 https://zhijiansk.com 同时添加到 request、uploadFile 和 downloadFile 合法域名'
  return `图片上传失败：${raw || '微信未返回具体原因'}`
}

export class ApiError extends Error {
  statusCode: number
  code?: string

  constructor(message: string, statusCode: number, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.statusCode = statusCode
    this.code = code
  }
}

export function apiUrl(path: string) {
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
}

export type RequestOptions = Omit<UniApp.RequestOptions, 'url'>

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const session = getSession()
  const headers: Record<string, string> = { ...(options.header as Record<string, string> || {}) }
  // Some endpoints (such as public consumer registration) must deliberately
  // remain anonymous even when the device still has a previous session. An
  // explicit empty Authorization header is therefore an opt-out, rather than
  // being overwritten with the stored token.
  if (session?.token && !Object.prototype.hasOwnProperty.call(headers, 'Authorization')) headers.Authorization = `Bearer ${session.token}`
  let response: UniApp.RequestSuccessCallbackResult
  try {
    response = await uni.request({ url: apiUrl(path), ...options, header: headers })
  } catch (error: any) {
    throw new Error(messageOf(error, '网络请求失败，请检查服务地址和网络连接'))
  }
  const data: any = parsePayload(response.data)
  if (response.statusCode === 401) {
    clearSession()
    uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
    setTimeout(() => uni.reLaunch({ url: '/pages/login/index' }), 500)
    throw new Error('登录已过期')
  }
  if (response.statusCode < 200 || response.statusCode >= 300) {
    throw new ApiError(messageOf(data, `请求失败（${response.statusCode}）`), response.statusCode, data?.code)
  }
  return data as T
}

export async function uploadFile<T>(path: string, filePath: string, name = 'file', formData?: Record<string, string>): Promise<T> {
  const session = getSession()
  let response: UniApp.UploadFileSuccessCallbackResult
  try {
    response = await uni.uploadFile({
      url: apiUrl(path), filePath, name,
      timeout: 120000,
      formData,
      header: session?.token ? { Authorization: `Bearer ${session.token}` } : {},
    })
  } catch (error: any) {
    throw new Error(uploadFailureMessage(error))
  }
  const data: any = parsePayload(response.data || '{}')
  if (response.statusCode === 401) {
    clearSession()
    uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
    setTimeout(() => uni.reLaunch({ url: '/pages/login/index' }), 500)
    throw new Error('登录已过期')
  }
  if (response.statusCode < 200 || response.statusCode >= 300) throw new Error(messageOf(data, `上传失败（${response.statusCode}）`))
  return data as T
}

/**
 * 生图接口单独保留在基础客户端上，避免页面依赖较大的 creative API 模块。
 * 即梦同步任务可能持续数分钟，小程序端必须显式设置足够的等待时间。
 */
export const createTextToImage = (body: any) => request<any>('/api/creative/ai/jimeng/text-to-image', {
  method: 'POST', data: body, timeout: 240000, header: { 'content-type': 'application/json' },
})

export const createReferenceToImage = (body: any) => request<any>('/api/creative/ai/image-to-image', {
  method: 'POST', data: body, timeout: 240000, header: { 'content-type': 'application/json' },
})
