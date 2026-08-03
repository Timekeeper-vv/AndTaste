// 身份归属由请求中的 Bearer Token 决定；role 只用于在登录时阻止后台账号误入小程序。
export interface MiniUser { username: string; role?: string }
export interface AuthSession { token: string; tokenType: 'Bearer'; expiresIn: number; user: MiniUser }

const KEY = 'smart_pig_auth'
/** The released miniapp uses the deployed consumer H5 as its single UI. */
export const CONSUMER_WEBVIEW_ROUTE = '/pages/webview/index'
export const getSession = (): AuthSession | null => uni.getStorageSync(KEY) || null
export const saveSession = (session: AuthSession) => uni.setStorageSync(KEY, session)
export const clearSession = () => uni.removeStorageSync(KEY)
export async function restoreSession() { return getSession() }
// Native pages remain in the project as a fallback while the web-view is the
// default entry. This keeps existing deep links/builds compatible without
// allowing a stale native session to bypass the canonical H5 user experience.
export const sessionStartRoute = () => CONSUMER_WEBVIEW_ROUTE
export const requireSession = () => {
  const session = getSession()
  if (!session) { uni.reLaunch({ url: '/pages/login/index' }); return null }
  return session
}
