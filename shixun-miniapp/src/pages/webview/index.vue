<template>
  <view class="webview-shell">
    <web-view
      v-if="source"
      class="consumer-webview"
      :src="source"
      @load="handleLoad"
      @error="handleError"
      @message="handleMessage"
    />

    <view v-if="failed" class="fallback" role="alert">
      <view class="fallback-mark">之</view>
      <text class="fallback-title">暂时无法打开创作空间</text>
      <text class="fallback-copy">网页工具暂时无法打开。日常创作、作品、订单和服务请使用小程序原生页面；请确认 3D 工具所需的 HTTPS 业务域名已配置。</text>
      <button class="fallback-action" @tap="reload">重新打开网页工具</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { onLoad, onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'

/**
 * The consumer H5 is the source of truth for the user experience.  Keeping
 * the URL in an env variable lets each deployment use its own registered
 * WeChat business domain without changing the app code.
 */
const DEFAULT_CONSUMER_WEB_URL = 'https://zhijiansk.com/'
const source = ref('')
const failed = ref(false)
const nativeTokenPending = ref(false)
const lastAuthUpdate = ref('')
let requested = false

function consumerWebUrl(): string {
  const configured = String(import.meta.env.VITE_CONSUMER_WEB_URL || DEFAULT_CONSUMER_WEB_URL).trim()
  if (!configured) return ''

  // Do not use the browser URL constructor here. It is not guaranteed to be
  // available in every WeChat Mini Program runtime; an unavailable global
  // would make this page fall back before <web-view> is even created.
  const baseUrl = configured.split('#', 1)[0]
  if (!/^https:\/\/[a-z0-9.-]+(?::\d+)?(?:[/?]|$)/i.test(baseUrl)) return ''

  // Keep the client marker in the query (it is not a secret), while the
  // bearer token is put in the fragment below. Fragments are not sent to the
  // H5 server and are removed by the H5 bootstrap bridge immediately.
  const hasClientMarker = /(?:[?&])client=wechat-miniapp(?:[&#]|$)/.test(baseUrl)
  const pageUrl = hasClientMarker
    ? baseUrl
    : `${baseUrl}${baseUrl.includes('?') ? '&' : '?'}client=wechat-miniapp`
  const session = uni.getStorageSync('smart_pig_auth') as { token?: string } | null
  const token = String(session?.token || '').trim()
  nativeTokenPending.value = !!token
  return token
    ? `${pageUrl}#miniapp=1&access_token=${encodeURIComponent(token)}`
    : `${pageUrl}#miniapp=1`
}

function loadConsumerPage() {
  failed.value = false
  source.value = consumerWebUrl()
  if (!source.value) failed.value = true
}

function reload() {
  loadConsumerPage()
}

function handleLoad() {
  if (!nativeTokenPending.value) return
  // The token has now been handed to H5 in the URL fragment.  Remove the
  // native copy so a delayed AUTH_LOGOUT/AUTH_EXPIRED message cannot cause an
  // expired credential to be injected again on the next web-view open.
  uni.removeStorageSync('smart_pig_auth')
  nativeTokenPending.value = false
}

function handleError(event: any) {
  // Keep the detailed reason in the developer-tool console without exposing
  // implementation details to end users on the fallback screen.
  console.error('[Smart Pig] web-view failed to load', event?.detail || event)
  failed.value = true
}

/**
 * H5 may post a close/logout message when it is embedded in a miniapp.  The
 * event is intentionally narrow: arbitrary payloads are ignored so a page
 * cannot navigate the native shell unexpectedly.
 */
function handleMessage(event: any) {
  const messages = event?.detail?.data
  const payload = Array.isArray(messages) ? messages[messages.length - 1] : messages
  if (payload === 'close' || payload?.action === 'close') uni.navigateBack()
  const eventName = payload?.event || payload?.action
  if (payload === 'logout' || payload?.action === 'logout' || eventName === 'AUTH_LOGOUT' || eventName === 'AUTH_EXPIRED') {
    // H5 sessionStorage and mini-program storage are separate.  Remove the
    // native copy before re-opening the web-view, otherwise it would inject
    // the same expired token again and trap the user in a login loop.
    uni.removeStorageSync('smart_pig_auth')
    uni.reLaunch({ url: '/pages/webview/index' })
  }
}

onLoad(() => {
  if (!requested) {
    requested = true
    loadConsumerPage()
  }
})

onShow(() => {
  const authUpdate = String(uni.getStorageSync('smart_pig_auth_updated') || '')
  if (authUpdate && authUpdate !== lastAuthUpdate.value) {
    lastAuthUpdate.value = authUpdate
    uni.removeStorageSync('smart_pig_auth_updated')
    loadConsumerPage()
    return
  }
  // If WeChat has recreated the page after a failed navigation, try once more
  // when it becomes visible. Do not reload a healthy web-view on every show.
  if (!source.value && !failed.value) loadConsumerPage()
})
</script>

<style scoped lang="scss">
.webview-shell{position:relative;width:100%;height:100vh;min-height:100vh;background:#f7f3ed;overflow:hidden}
.consumer-webview{display:block;width:100%;height:100vh;min-height:100vh}
.fallback{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:64rpx;background:linear-gradient(180deg,#faf8f3,#f1ebe2);text-align:center;box-sizing:border-box}
.fallback-mark{display:grid;place-items:center;width:112rpx;height:112rpx;border:1rpx solid rgba(93,125,110,.26);border-radius:34rpx;background:linear-gradient(145deg,#e8f0e8,#bdcfc2);color:#456757;font-family:"Songti SC","STSong",serif;font-size:64rpx;box-shadow:0 16rpx 30rpx rgba(68,85,71,.14)}
.fallback-title{margin-top:34rpx;color:#332e29;font-family:"Songti SC","STSong",serif;font-size:38rpx;font-weight:700}
.fallback-copy{max-width:560rpx;margin-top:18rpx;color:#81776c;font-size:25rpx;line-height:1.7}
.fallback-action,.fallback-secondary{width:430rpx;height:86rpx;line-height:86rpx;border-radius:16rpx;font-size:27rpx;font-weight:800;box-sizing:border-box}
.fallback-action{margin-top:40rpx;background:#557968;color:#fff;box-shadow:0 12rpx 22rpx rgba(63,91,74,.18)}
.fallback-secondary{margin-top:18rpx;border:1rpx solid #d9cec1;background:rgba(255,253,249,.8);color:#6f6256}
.fallback-action::after,.fallback-secondary::after{border:0}
</style>
