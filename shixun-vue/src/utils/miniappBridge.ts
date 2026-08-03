/**
 * Small, dependency-free bridge for the consumer H5 surface when it is opened
 * inside a WeChat mini-program `<web-view>`.
 *
 * A web-view has its own origin storage; it cannot read the mini-program's
 * `uni` storage.  The mini-program therefore passes a short-lived access
 * token in the URL fragment (`#miniapp=1&access_token=...`).  Fragments are
 * not sent in HTTP requests (and are consequently absent from nginx access
 * logs and normal referrer headers).  `bootstrapEmbeddedSession` consumes the
 * token once and immediately removes it from the address bar.
 */

export const MINIAPP_MODE_KEY = 'smart_pig_miniapp_mode'
const ACCESS_TOKEN_KEYS = ['access_token', 'token'] as const
const MINIAPP_FLAG_KEYS = ['miniapp', 'mini_program', 'from', 'client'] as const

type MiniProgramBridge = {
  navigateBack?: (options?: { delta?: number; success?: () => void; fail?: () => void }) => void
  navigateTo?: (options: { url: string; success?: () => void; fail?: (error: unknown) => void }) => void
  redirectTo?: (options: { url: string; success?: () => void; fail?: (error: unknown) => void }) => void
  postMessage?: (options: { data: unknown }) => void
  getEnv?: (callback: (result: { miniprogram?: boolean }) => void) => void
}

let miniProgramSdkPromise: Promise<MiniProgramBridge | null> | null = null

function win(): (Window & { wx?: { miniProgram?: MiniProgramBridge }; __wxjs_environment?: string }) | null {
  return typeof window === 'undefined' ? null : window as Window & { wx?: { miniProgram?: MiniProgramBridge }; __wxjs_environment?: string }
}

function readParams(): { query: URLSearchParams; hash: URLSearchParams } {
  const current = win()
  if (!current) return { query: new URLSearchParams(), hash: new URLSearchParams() }
  const url = new URL(current.location.href)
  const rawHash = url.hash.replace(/^#/, '')
  // Support both `#miniapp=1&access_token=...` and the less common
  // `#/?miniapp=1&access_token=...` shape emitted by some routers.
  const hashText = rawHash.replace(/^\/?\??/, '')
  return { query: url.searchParams, hash: new URLSearchParams(hashText) }
}

function firstValue(params: URLSearchParams, keys: readonly string[]): string {
  for (const key of keys) {
    const value = params.get(key)
    if (value) return value.trim()
  }
  return ''
}

function isMiniappFlag(value: string): boolean {
  return ['1', 'true', 'yes', 'miniprogram', 'mini-program', 'wechat-miniapp', 'wechat-miniprogram'].includes(value.toLowerCase())
}

/**
 * Consume a web-view bootstrap token before Vue mounts.  Query parameters are
 * accepted for backwards compatibility, but new callers should use the hash
 * form so the bearer token never travels to the server.
 */
export function bootstrapEmbeddedSession(): boolean {
  const current = win()
  if (!current) return false
  const { query, hash } = readParams()
  const token = firstValue(hash, ACCESS_TOKEN_KEYS) || firstValue(query, ACCESS_TOKEN_KEYS)
  const mode = isMiniappFlag(firstValue(hash, MINIAPP_FLAG_KEYS) || firstValue(query, MINIAPP_FLAG_KEYS))
    || current.__wxjs_environment === 'miniprogram'
    || Boolean(current.wx?.miniProgram)

  if (mode) {
    try { sessionStorage.setItem(MINIAPP_MODE_KEY, '1') } catch { /* private mode / blocked storage */ }
  }
  // Do not put arbitrary data from the URL into storage.  The server remains
  // the authority; the length guard only prevents accidental giant values.
  if (token && token.length <= 4096) {
    try { sessionStorage.setItem('accessToken', token) } catch { /* handled by normal login flow */ }
    if (mode) {
      // A web-view can be recreated while the mini-program is backgrounded.
      // Keep a same-origin copy so the next bootstrap can restore the session;
      // every use is still verified by `/api/auth/me`, and logout/401 clears it.
      try { localStorage.setItem('accessToken', token) } catch { /* storage may be disabled */ }
    }
  } else if (mode) {
    // Native storage is consumed after the first successful hand-off. Keep a
    // browser-local copy so a later web-view recreation does not force a
    // second login; it is still verified by `/api/auth/me` before use.
    try {
      const persisted = localStorage.getItem('accessToken')
      if (persisted && persisted.length <= 4096) sessionStorage.setItem('accessToken', persisted)
    } catch { /* storage may be disabled */ }
  }

  const sensitiveKeys: Set<string> = new Set([...ACCESS_TOKEN_KEYS, ...MINIAPP_FLAG_KEYS])
  const hasSensitiveUrlData = [...query.keys(), ...hash.keys()].some(key => sensitiveKeys.has(key))
  if (hasSensitiveUrlData) {
    // Preserve an ordinary H5 route/query, but remove the bootstrap token and
    // mini-app marker.  Fragments are never needed after bootstrap.
    const clean = new URL(current.location.href)
    for (const key of sensitiveKeys) clean.searchParams.delete(key)
    clean.hash = ''
    try { current.history.replaceState(null, '', `${clean.pathname}${clean.search}`) } catch { /* old webview */ }
  }
  // `wx.miniProgram` is provided by the WeChat JS SDK rather than by the
  // browser runtime.  Start loading it in the background while Vue mounts;
  // desktop H5 never pays this network cost because `mode` is false.
  if (mode) void ensureMiniProgramSdk()
  return mode
}

export function isEmbeddedMiniapp(): boolean {
  const current = win()
  if (!current) return false
  try {
    if (sessionStorage.getItem(MINIAPP_MODE_KEY) === '1') return true
  } catch { /* continue with environment detection */ }
  return current.__wxjs_environment === 'miniprogram' || Boolean(current.wx?.miniProgram)
}

function bridge(): MiniProgramBridge | null {
  return win()?.wx?.miniProgram || null
}

/** Lazily load the official WeChat web-view SDK only inside a mini-program. */
export function ensureMiniProgramSdk(): Promise<MiniProgramBridge | null> {
  if (!isEmbeddedMiniapp()) return Promise.resolve(null)
  const existing = bridge()
  if (existing) return Promise.resolve(existing)
  if (miniProgramSdkPromise) return miniProgramSdkPromise
  const current = win()
  if (!current?.document) return Promise.resolve(null)
  miniProgramSdkPromise = new Promise(resolve => {
    const finish = () => resolve(bridge())
    const prior = current.document.querySelector('script[data-smart-pig-miniapp-sdk]') as HTMLScriptElement | null
    if (prior) {
      prior.addEventListener('load', finish, { once: true })
      prior.addEventListener('error', finish, { once: true })
      // A script may already have completed before listeners were attached.
      setTimeout(finish, 0)
      return
    }
    const script = current.document.createElement('script')
    script.async = true
    script.src = 'https://res.wx.qq.com/open/js/jweixin-1.6.0.js'
    script.dataset.smartPigMiniappSdk = '1'
    script.addEventListener('load', finish, { once: true })
    script.addEventListener('error', finish, { once: true })
    current.document.head.appendChild(script)
    // Do not hold a logout/navigation action forever on a blocked CDN.
    setTimeout(finish, 2500)
  }).finally(() => { miniProgramSdkPromise = null })
  return miniProgramSdkPromise
}

/** Notify the native shell of logout/expiry without exposing the JWT. */
export async function notifyMiniapp(event: string, payload: Record<string, unknown> = {}): Promise<void> {
  if (!isEmbeddedMiniapp()) return
  const nativeBridge = await ensureMiniProgramSdk()
  nativeBridge?.postMessage?.({ data: { source: 'smart-pig-h5', event, ...payload } })
}

/** Return to the native shell when the H5 user surface is embedded. */
export function navigateBackToMiniapp(): boolean {
  if (!isEmbeddedMiniapp()) return false
  const nativeBridge = bridge()
  if (!nativeBridge?.navigateBack) return false
  nativeBridge.navigateBack({ delta: 1 })
  return true
}

/** Open a native mini-program page (for example native JSAPI payment). */
export function navigateToMiniappPage(url: string): boolean {
  if (!isEmbeddedMiniapp() || !url || !url.startsWith('/')) return false
  const nativeBridge = bridge()
  if (!nativeBridge?.navigateTo) return false
  nativeBridge.navigateTo({ url })
  return true
}
