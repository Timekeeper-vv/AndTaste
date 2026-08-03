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

export function apiUrl(path: string) {
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
}

export type RequestOptions = Omit<UniApp.RequestOptions, 'url'>

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const session = getSession()
  const headers: Record<string, string> = { ...(options.header as Record<string, string> || {}) }
  if (session?.token && !headers.Authorization) headers.Authorization = `Bearer ${session.token}`
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
  if (response.statusCode < 200 || response.statusCode >= 300) throw new Error(messageOf(data, `请求失败（${response.statusCode}）`))
  return data as T
}

export async function uploadFile<T>(path: string, filePath: string, name = 'file'): Promise<T> {
  const session = getSession()
  let response: UniApp.UploadFileSuccessCallbackResult
  try {
    response = await uni.uploadFile({
      url: apiUrl(path), filePath, name,
      header: session?.token ? { Authorization: `Bearer ${session.token}` } : {},
    })
  } catch (error: any) {
    throw new Error(messageOf(error, '上传失败，请检查服务地址和网络连接'))
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
