<template>
  <view class="page">
    <view class="cube">3D</view>
    <text class="title">{{ title }}</text>
    <text class="description">模型将在小程序内的安全 H5 预览页打开。访问链接只对当前作品短时有效，不会把登录令牌传给 H5。</text>
    <button class="open" :disabled="!canOpen" :loading="opening" @tap="openH5">打开 3D 预览</button>
    <view v-if="openError" class="open-error"><text>{{ openError }}</text></view>
    <view v-if="!canOpen" class="config-notice">
      <text class="notice-title">{{ configHintTitle }}</text>
      <text>{{ configHint }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { apiUrl } from '../../api/client'
import { getModelPreviewAccess } from '../../api/creative'

const id = ref('')
const title = ref('3D 模型')
const opening = ref(false)
const openError = ref('')
const previewBase = (import.meta.env.VITE_MODEL_PREVIEW_BASE_URL || '').replace(/\/$/, '')
const canOpen = computed(() => /^https:\/\//.test(previewBase) && /^\d+$/.test(id.value))
const configHintTitle = computed(() => previewBase ? 'H5 预览域名必须使用 HTTPS' : '尚未配置 H5 预览域名')
const configHint = computed(() => previewBase
  ? 'VITE_MODEL_PREVIEW_BASE_URL 当前不是 HTTPS 地址，微信小程序无法通过 web-view 打开。请改为已备案的 HTTPS 业务域名，并在微信公众平台添加为业务域名后重新构建。'
  : '请在构建环境中设置 VITE_MODEL_PREVIEW_BASE_URL，并将该 HTTPS 域名添加到微信小程序的业务域名。配置完成后重新构建即可在小程序内预览。')

function safelyDecode(value: unknown) {
  try { return decodeURIComponent(String(value || '')) } catch { return String(value || '') }
}

function appendQuery(base: string, values: Record<string, string>) {
  const hashIndex = base.indexOf('#')
  const path = hashIndex >= 0 ? base.slice(0, hashIndex) : base
  const hash = hashIndex >= 0 ? base.slice(hashIndex) : ''
  const query = Object.entries(values).map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`).join('&')
  return `${path}${path.includes('?') ? '&' : '?'}${query}${hash}`
}

function absolutePreviewUrl(value: string | undefined, assetId: string, accessToken: string | undefined) {
  const fallback = accessToken
    ? apiUrl(`/api/creative/ai/assets/${encodeURIComponent(assetId)}/model-content?access_token=${encodeURIComponent(accessToken)}`)
    : ''
  if (!value) return fallback
  if (/^https:\/\//.test(value)) {
    if (!accessToken || value.includes('access_token=')) return value
    return `${value}${value.includes('?') ? '&' : '?'}access_token=${encodeURIComponent(accessToken)}`
  }
  if (value.startsWith('/')) return apiUrl(value)
  return fallback
}

async function openH5() {
  if (!canOpen.value) return uni.showToast({ title: '请先配置 HTTPS 业务域名', icon: 'none' })
  opening.value = true
  openError.value = ''
  try {
    const access = await getModelPreviewAccess(id.value)
    const modelUrl = absolutePreviewUrl(access?.url, id.value, access?.accessToken)
    if (!/^https:\/\//.test(modelUrl)) throw new Error('模型预览地址必须是 HTTPS，请检查服务器域名配置')
    // 传给 H5 的仅是服务端签发的短期、单资源访问 URL，不包含用户登录 JWT。
    const h5Url = appendQuery(previewBase, { assetId: id.value, title: title.value, modelUrl })
    uni.setStorageSync('smart_pig_model_preview_url', h5Url)
    uni.navigateTo({ url: '/pages/model-webview/index' })
  } catch (error: any) {
    openError.value = error?.message || '暂时无法获取模型预览权限，请稍后重试。'
  } finally {
    opening.value = false
  }
}

onLoad((query: any) => {
  id.value = String(query?.id || '')
  title.value = safelyDecode(query?.title || '3D 模型')
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:130rpx 58rpx;text-align:center;box-sizing:border-box}.cube{width:210rpx;height:210rpx;margin:0 auto 48rpx;border-radius:32rpx;background:linear-gradient(145deg,#3a1d15,#b75632);color:#fff;display:flex;align-items:center;justify-content:center;font-size:56rpx;font-weight:800;box-shadow:26rpx 26rpx 0 #f0c5a6}.title{font-size:42rpx;font-weight:800;display:block}.description{display:block;color:#866c60;line-height:1.8;font-size:27rpx;margin:26rpx 0}.open{height:94rpx;line-height:94rpx;border-radius:48rpx;background:#953d24;color:#fff;font-size:29rpx}.open[disabled]{background:#c8afa1;color:#fff}.open-error{margin-top:22rpx;padding:20rpx;background:#fff0ec;border-radius:16rpx;color:#b04632;font-size:23rpx;line-height:1.6;text-align:left}.config-notice{margin-top:30rpx;padding:26rpx;background:#fff4e8;border-radius:20rpx;color:#9b7160;text-align:left;font-size:22rpx;line-height:1.7}.notice-title{display:block;color:#8e3e26;font-size:26rpx;font-weight:700;margin-bottom:8rpx}
</style>

<style scoped lang="scss">
.page{background:radial-gradient(ellipse at 10% 0%,rgba(151,177,163,.18),transparent 29%),linear-gradient(180deg,#faf8f3,#f0e9df)}.cube{background:linear-gradient(145deg,#5b7b6d,#9fb7a9);box-shadow:26rpx 26rpx 0 #dde7dd}.title{font-family:"Songti SC","STSong",serif;color:#302b26}.description{color:#81776c}.open{border-radius:17rpx;background:linear-gradient(135deg,#3e3933,#617e71)}.config-notice{background:#f4efe7;color:#7f756b}.notice-title{color:#a4644f}
</style>
