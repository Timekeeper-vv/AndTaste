import { clearSession, getSession } from '../utils/session'

// 小程序正式发布时，填写 .env 的 VITE_API_BASE_URL，例如 https://api.example.com
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'https://api.example.com').replace(/\/$/, '')

function messageOf(data: any, fallback: string) {
  return data?.message || data?.error || data?.detail || fallback
}

export function apiUrl(path: string) {
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
}

export type RequestOptions = Omit<UniApp.RequestOptions, 'url'>

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const session = getSession()
  const headers: Record<string, string> = { ...(options.header as Record<string, string> || {}) }
  if (session?.token && !headers.Authorization) headers.Authorization = `Bearer ${session.token}`
  const response = await uni.request({ url: apiUrl(path), ...options, header: headers })
  const data: any = response.data
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
  const response = await uni.uploadFile({
    url: apiUrl(path), filePath, name,
    header: session?.token ? { Authorization: `Bearer ${session.token}` } : {},
  })
  const data = JSON.parse(response.data || '{}')
  if (response.statusCode < 200 || response.statusCode >= 300) throw new Error(messageOf(data, `上传失败（${response.statusCode}）`))
  return data as T
}
