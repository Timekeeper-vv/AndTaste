// Keep the request layer self-contained. WeChat DevTools can briefly expose an
// incomplete imported module while it is rebuilding the watched output tree;
// authentication must still work during that window. This is the same storage
// key used by utils/session.ts, so login/logout semantics remain unchanged.
const MINI_SESSION_KEY = 'smart_pig_auth'

function readStoredSession(): any | null {
  try {
    const value = uni.getStorageSync(MINI_SESSION_KEY)
    return value && typeof value === 'object' ? value : null
  } catch {
    return null
  }
}

function clearStoredSession() {
  try { uni.removeStorageSync(MINI_SESSION_KEY) } catch { /* Storage is optional during bootstrap. */ }
}

// 小程序正式发布时，填写 .env 的 VITE_API_BASE_URL，例如 https://api.example.com
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'https://api.example.com').replace(/\/$/, '')

function stringValue(value: any) {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value.trim()
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return ''
}

function looksUnreadable(value: string) {
  const text = value.trim()
  if (!text || /^\[object Object\]$/i.test(text)) return true
  if (text.includes('\uFFFD')) return true
  const replacementCount = (text.match(/[?？]/g) || []).length
  return replacementCount >= 3 && replacementCount >= Math.ceil(text.length * 0.45)
}

/**
 * uni.request and the WeChat network layer do not always preserve the
 * server's error body. In particular, an undecodable Chinese response can
 * arrive as a string of question marks. Never expose that raw value to users.
 */
export function readableErrorMessage(error: any, fallback = '网络或服务暂时不可用，请稍后重试') {
  const errMsg = stringValue(error?.errMsg)
  const message = stringValue(error?.message)
  const raw = message || errMsg || stringValue(error)
  const context = `${errMsg} ${message}`.toLowerCase()
  const statusCode = Number(error?.statusCode || error?.status || 0)

  if (/url not in domain list|合法域名|not in domain/i.test(context)) return '当前接口域名未加入微信合法域名，请检查小程序 request 合法域名配置'
  if (/ssl|certificate|cert/i.test(context)) return '服务 HTTPS 证书校验失败，请检查本地或线上域名证书'
  if (/timeout|timed out/i.test(context)) return '请求超时了，当前输入已保留，请稍后重试'
  if (/network|request:fail|connect|refused|dns/i.test(context)) return '暂时连接不上创作服务，请检查网络后重试'
  if (statusCode === 401 || /unauthorized|请先登录|登录已过期/i.test(context)) return '登录状态已失效，请重新登录后继续'
  if (statusCode === 403 || /forbidden|无权访问/i.test(context)) return '当前账号暂时没有使用该功能的权限'
  if (statusCode === 429 || /too many|频繁|rate limit/i.test(context)) return '请求较多，创作服务正在排队，请稍后重试'
  if (statusCode >= 500) return '创作服务暂时不可用，当前输入已保留，请稍后重试'
  if (!looksUnreadable(raw)) return raw || fallback
  return fallback
}

function messageOf(data: any, fallback: string) {
  // uni.request may leave an error response as a JSON string when the server
  // does not send an explicit JSON content type. Parse it before falling back
  // to a generic status message so the miniapp shows the real validation error.
  if (typeof data === 'string') {
    try { data = JSON.parse(data) } catch { return readableErrorMessage(data, fallback) }
  }
  return readableErrorMessage(data?.message || data?.error || data?.detail, fallback)
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

export const AUTH_SESSION_EXPIRED_CODE = 'AUTH_SESSION_EXPIRED'

export function isAuthenticationError(error: any) {
  const statusCode = Number(error?.statusCode || error?.status || 0)
  const code = String(error?.code || '')
  const message = String(error?.message || error?.errMsg || '')
  return statusCode === 401
    || code === AUTH_SESSION_EXPIRED_CODE
    || /unauthorized|登录已过期|登录状态已失效|请先登录/i.test(message)
}

export function apiUrl(path: string) {
  return `${API_BASE_URL}${path.startsWith('/') ? path : `/${path}`}`
}

export type RequestOptions = Omit<UniApp.RequestOptions, 'url'>

const DEFAULT_REQUEST_TIMEOUT = 20000
let authenticationRedirectBlockedUntil = 0

function currentReturnRoute() {
  try {
    const pages = getCurrentPages()
    const current = pages[pages.length - 1] as any
    const route = String(current?.route || '').replace(/^\//, '')
    if (!route || route === 'pages/login/index') return ''
    const query = Object.entries(current?.options || {})
      // Reopening a conversation with new=1 would discard the project the user
      // was working on when the token expired. Let the page restore the latest
      // persisted conversation after login instead.
      .filter(([key, value]) => !(route === 'pages/conversation-create/index' && key === 'new') && value !== undefined && value !== null && String(value) !== '')
      .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
      .join('&')
    return `/${route}${query ? `?${query}` : ''}`
  } catch {
    return ''
  }
}

function expireSessionAndRedirectToLogin() {
  clearStoredSession()
  const now = Date.now()
  if (now < authenticationRedirectBlockedUntil) return
  authenticationRedirectBlockedUntil = now + 3000
  const returnRoute = currentReturnRoute()
  const loginUrl = returnRoute
    ? `/pages/login/index?redirect=${encodeURIComponent(returnRoute)}`
    : '/pages/login/index'
  uni.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
  setTimeout(() => uni.reLaunch({ url: loginUrl }), 500)
}

function authenticationError(data: any, fallback = '登录状态已失效，请重新登录后继续') {
  return new ApiError(fallback, 401, stringValue(data?.code) || AUTH_SESSION_EXPIRED_CODE)
}

function isCredentialAuthenticationRequest(path: string) {
  const endpoint = path.split('?')[0]
  return [
    '/api/users/login',
    '/api/users/wechat-login',
    '/api/users/wechat-phone-login',
    '/api/users/email-register',
    '/api/users/email-verification',
  ].includes(endpoint)
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const session = readStoredSession()
  const { timeout = DEFAULT_REQUEST_TIMEOUT, ...requestOptions } = options
  const headers: Record<string, string> = { ...(requestOptions.header as Record<string, string> || {}) }
  // Some endpoints (such as public consumer registration) must deliberately
  // remain anonymous even when the device still has a previous session. An
  // explicit empty Authorization header is therefore an opt-out, rather than
  // being overwritten with the stored token.
  if (session?.token && !Object.prototype.hasOwnProperty.call(headers, 'Authorization')) headers.Authorization = `Bearer ${session.token}`
  let response: UniApp.RequestSuccessCallbackResult
  try {
    response = await uni.request({ url: apiUrl(path), ...requestOptions, timeout, header: headers })
  } catch (error: any) {
    const message = readableErrorMessage(error, '网络请求失败，请检查服务地址和网络连接')
    console.warn('[smart-pig api] request failed', { path, message })
    throw new Error(message)
  }
  const data: any = parsePayload(response.data)
  if (response.statusCode === 401) {
    const sentBearerToken = /^Bearer\s+\S+/i.test(String(headers.Authorization || ''))
    // A rejected username/password or WeChat credential is a login validation
    // error, not proof that an existing protected session expired.
    if (sentBearerToken && !isCredentialAuthenticationRequest(path)) {
      expireSessionAndRedirectToLogin()
      throw authenticationError(data)
    }
    throw new ApiError(messageOf(data, '请先登录后继续'), 401, data?.code)
  }
  if (response.statusCode < 200 || response.statusCode >= 300) {
    const requestId = (response as any).header?.['X-Request-Id'] || (response as any).header?.['x-request-id'] || ''
    const message = messageOf(data, `请求失败（${response.statusCode}）`)
    console.warn('[smart-pig api] response failed', { path, statusCode: response.statusCode, requestId, message })
    throw new ApiError(message, response.statusCode, data?.code)
  }
  return data as T
}

export async function uploadFile<T>(path: string, filePath: string, name = 'file', formData?: Record<string, string>): Promise<T> {
  const session = readStoredSession()
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
  if (!response) throw new Error('微信未返回上传结果，请重新选择图片后重试')
  const data: any = parsePayload(response.data || '{}')
  if (response.statusCode === 401) {
    if (session?.token) {
      expireSessionAndRedirectToLogin()
      throw authenticationError(data)
    }
    throw new ApiError(messageOf(data, '请先登录后继续'), 401, data?.code)
  }
  if (response.statusCode < 200 || response.statusCode >= 300) throw new Error(messageOf(data, `上传失败（${response.statusCode}）`))
  return data as T
}

export type ImageGenerationJobProgress = {
  jobId?: number
  jobType?: 'text_to_image' | 'image_to_image' | 'multi_view' | string
  status?: 'queued' | 'running' | 'succeeded' | 'failed' | string
  progress?: number
  queuePosition?: number
  message?: string
  errorMessage?: string
  [key: string]: any
}

export type ArkImageJobProgress = ImageGenerationJobProgress

/** The miniapp keeps one image provider and one predictable output size. */
export type SeedreamImageSize = '1K' | '2K'
export const DEFAULT_SEEDREAM_IMAGE_SIZE: SeedreamImageSize = '2K'

function seedreamRequestBody(body: any) {
  const normalized: any = buildCreativeGenerationRequest(body || {})
  const imageSize = normalized?.imageSize || DEFAULT_SEEDREAM_IMAGE_SIZE
  return {
    ...normalized,
    provider: 'ark',
    imageSize,
    // Older server DTOs exposed this field with the Imagen-era spelling.
    // Sending both keeps the selected Seedream resolution explicit across
    // compatible deployments.
    imagenImageSize: normalized?.imagenImageSize || imageSize,
  }
}

const wait = (milliseconds: number) => new Promise<void>(resolve => setTimeout(resolve, milliseconds))

export const getImageGenerationJob = (jobId: number | string) => request<ImageGenerationJobProgress>(
  `/api/creative/ai/image-jobs/${encodeURIComponent(String(jobId))}`,
  { timeout: 30000 },
)

export const getArkImageJob = getImageGenerationJob

/**
 * Ark/Seedream is account-limited. The API returns a durable job immediately;
 * the client follows that job instead of holding one long HTTP request open.
 */
export async function waitForImageGenerationJob(initial: ImageGenerationJobProgress, onProgress?: (job: ImageGenerationJobProgress) => void) {
  let job = initial
  let transientFailures = 0
  const deadline = Date.now() + 45 * 60 * 1000
  onProgress?.(job)
  while (job.status === 'queued' || job.status === 'running') {
    if (!job.jobId) throw new Error('生图任务编号缺失，请重新提交')
    if (Date.now() >= deadline) throw new Error('任务仍在后台排队，已停止等待；请稍后到作品库查看生成结果')
    await wait(job.status === 'queued' ? 1800 : 2200)
    try {
      job = await getImageGenerationJob(job.jobId)
      transientFailures = 0
      onProgress?.(job)
    } catch (error) {
      transientFailures += 1
      if (transientFailures >= 3) throw error
      await wait(1200 * transientFailures)
    }
  }
  if (job.status === 'failed') throw new Error(job.errorMessage || job.message || '图片生成失败')
  if (job.status !== 'succeeded') throw new Error(job.message || '图片生成状态异常，请稍后到作品库查看')
  return job
}

export const waitForArkImageJob = waitForImageGenerationJob

/**
 * Canonical client-side adapter for the creative generation contract.
 *
 * The server is the source of truth for prompt policy, but every entry point
 * must send the same structured brief so a missing field cannot silently
 * change an image-to-image request into a generic product request.
 */
export type CreativeGenerationRequest = Record<string, any> & {
  prompt?: string
  rawPrompt?: string
  productKey?: string
  productCategory?: string
  productType?: string
  material?: string
  productSize?: string
  negativePrompt?: string
  inputAssetId?: number | string | null
  refinement?: boolean
  refinementNote?: string
  /** Optional Seedream seed used to reproduce a comparison run. */
  seed?: number | string | null
}

function normalizedBriefText(value: any, maxLength = 2400) {
  return String(value ?? '').trim().replace(/\s+/g, ' ').slice(0, maxLength)
}

function normalizedPromptText(value: any, maxLength = 6000) {
  return String(value ?? '')
    .replace(/\r\n?/g, '\n')
    .split('\n')
    .map(line => line.trim().replace(/[ \t]+/g, ' '))
    .join('\n')
    .trim()
    .slice(0, maxLength)
}

export function buildCreativeGenerationRequest(body: CreativeGenerationRequest = {}) {
  const prompt = normalizedPromptText(body.prompt, 6000)
  const suppliedRawPrompt = normalizedPromptText(body.rawPrompt, 6000)
  const rawPrompt = suppliedRawPrompt || prompt
  const suppliedCategory = normalizedBriefText(body.productCategory, 160)
  const suppliedType = normalizedBriefText(body.productType, 160)
  const productCategory = suppliedCategory || suppliedType || '文创产品'
  const productType = suppliedType || productCategory
  const inputAssetId = Number(body.inputAssetId)
  return {
    ...body,
    prompt,
    rawPrompt,
    productKey: normalizedBriefText(body.productKey, 120),
    productCategory,
    productType,
    material: normalizedBriefText(body.material, 160),
    productSize: normalizedBriefText(body.productSize, 120),
    negativePrompt: normalizedPromptText(body.negativePrompt, 4000),
    inputAssetId: Number.isFinite(inputAssetId) && inputAssetId > 0 ? inputAssetId : null,
    refinement: body.refinement === true,
    refinementNote: normalizedPromptText(body.refinementNote, 2400),
  }
}

export async function createTextToImage(body: any, onProgress?: (job: ArkImageJobProgress) => void) {
  const queued = await request<ArkImageJobProgress>('/api/creative/ai/ark/text-to-image', {
    method: 'POST', data: seedreamRequestBody(body), timeout: 30000, header: { 'content-type': 'application/json' },
  })
  return waitForArkImageJob(queued, onProgress)
}

export async function createReferenceToImage(body: any, onProgress?: (job: ImageGenerationJobProgress) => void) {
  const queued = await request<ImageGenerationJobProgress>('/api/creative/ai/image-to-image', {
    method: 'POST', data: { ...seedreamRequestBody(body), queue: true }, timeout: 30000, header: { 'content-type': 'application/json' },
  })
  return waitForImageGenerationJob(queued, onProgress)
}
