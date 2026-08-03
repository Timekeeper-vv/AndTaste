// 身份归属由请求中的 Bearer Token 决定；role 只用于在登录时阻止后台账号误入小程序。
export interface MiniUser { username: string; role?: string }
export interface AuthSession { token: string; tokenType: 'Bearer'; expiresIn: number; user: MiniUser }

const KEY = 'smart_pig_auth'
export const getSession = (): AuthSession | null => uni.getStorageSync(KEY) || null
export const saveSession = (session: AuthSession) => uni.setStorageSync(KEY, session)
export const clearSession = () => uni.removeStorageSync(KEY)
export async function restoreSession() { return getSession() }
export const sessionStartRoute = () => {
  if (!getSession()) return '/pages/login/index'
  return uni.getStorageSync('creation_context') ? '/pages/home/index' : '/pages/purpose/index'
}
export const requireSession = () => {
  const session = getSession()
  if (!session) { uni.reLaunch({ url: '/pages/login/index' }); return null }
  return session
}
