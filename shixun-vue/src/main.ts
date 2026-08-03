import { createApp } from 'vue'
import './style.css'
import './shared.css'
import App from './App.vue'
import { bootstrapEmbeddedSession } from './utils/miniappBridge'

// Must run before App.restoreSession() so a mini-program web-view can reuse
// the same consumer H5 interface and authenticated API flow.  The bootstrap
// token is consumed from the URL fragment and removed immediately.
bootstrapEmbeddedSession()

const nativeFetch = window.fetch.bind(window)
const publicApiPaths = ['/api/users/login']

// Centralized Bearer injection. Business components no longer need to trust or manage user-id headers.
window.fetch = (async (input: RequestInfo | URL, init: RequestInit = {}) => {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
  const isApi = url.startsWith('/api/') || url.includes('/api/')
  const token = sessionStorage.getItem('accessToken')
  // A signup without a session is public. The same endpoint is also used by
  // super-admins to create staff accounts, which must carry their Bearer token.
  const isPublic = publicApiPaths.some(path => url.includes(path))
    || (url.endsWith('/api/users') && (init.method || 'GET').toUpperCase() === 'POST' && !token)
  const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined))
  if (isApi && token && !isPublic) headers.set('Authorization', `Bearer ${token}`)
  const response = await nativeFetch(input, { ...init, headers })
  if (isApi && response.status === 401 && !isPublic) window.dispatchEvent(new Event('auth-expired'))
  return response
}) as typeof window.fetch

createApp(App).mount('#app')
