// 身份归属由请求中的 Bearer Token 决定；role 只用于在登录时阻止后台账号误入小程序。
export interface MiniUser { username: string; role?: string }
export interface AuthSession { token: string; tokenType: 'Bearer'; expiresIn: number; user: MiniUser }

const KEY = 'smart_pig_auth'
/**
 * Native pages are the primary consumer experience. H5 is retained only for
 * browser handoff and model tools that require a web runtime.
 */
export const CONSUMER_NATIVE_HOME_ROUTE = '/pages/home/index'
export const CONSUMER_WEBVIEW_ROUTE = '/pages/webview/index'
export const CONSUMER_LOGIN_ROUTE = '/pages/login/index'
export const getSession = (): AuthSession | null => uni.getStorageSync(KEY) || null
export const saveSession = (session: AuthSession) => uni.setStorageSync(KEY, session)
export const clearSession = () => uni.removeStorageSync(KEY)
export async function restoreSession() { return getSession() }
// Authenticate natively first so a new miniapp user never lands on an H5
// username/password page. Native pages remain the canonical post-login experience.
export const sessionStartRoute = () => getSession() ? CONSUMER_NATIVE_HOME_ROUTE : CONSUMER_LOGIN_ROUTE
export const requireSession = () => {
  const session = getSession()
  if (!session) { uni.reLaunch({ url: '/pages/login/index' }); return null }
  return session
}
