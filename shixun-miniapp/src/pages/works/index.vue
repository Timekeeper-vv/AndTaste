<template>
  <view class="page">
    <view class="intro">
      <view>
        <text class="title">我的作品</text>
        <text class="sub">查看和管理已生成的图片、模型与灵感素材</text>
      </view>
      <button v-if="signedIn" class="refresh" size="mini" :loading="loading" @tap="refresh(true)">刷新</button>
      <button v-else class="back-home" size="mini" @tap="goHome">返回首页</button>
    </view>

    <view v-if="!signedIn" class="guest-state">
      <view class="guest-mark">作</view>
      <text class="guest-title">登录后查看我的作品</text>
      <text class="guest-copy">你的效果图、审核进度和打样记录会安全保存在账号中。</text>
      <button class="guest-login" @tap="goLogin">登录查看我的作品</button>
      <button class="guest-browse" @tap="goHome">暂不登录，继续浏览</button>
    </view>

    <template v-else>
      <AiGeneratedNotice class="ai-disclosure" compact description="带有“AI生成”标识的图片、生产模拟图和 3D 原型由人工智能生成。展示、商业使用、打样和生产前请完成人工复核与权利核验。" />

      <view v-if="loadError && !assets.length && !multiviewBundles.length" class="load-error">
        <text class="load-error-title">作品暂时未能打开</text>
        <text class="load-error-copy">{{ loadError }}</text>
        <button class="load-retry" @tap="refresh(true)">重新加载</button>
      </view>

      <view v-else-if="!visibleAssets.length && !multiviewBundles.length" class="empty">
        <text>还没有作品，去开始第一件创作吧。</text>
        <button class="create-first" @tap="goCreate">开始创作</button>
      </view>

      <view v-else class="section">
        <view v-if="loadError" class="cache-warning" @tap="refresh(true)">
          <text>暂时无法更新最新作品</text><text>重新加载 ›</text>
        </view>
        <view class="section-head"><text>作品库</text><text>{{ totalWorkCount }} 件</text></view>

        <view v-for="bundle in multiviewBundles" :key="`bundle-${bundle.id}`" class="multiview-bundle-card">
          <view class="bundle-card-head"><view><text class="product-no">产品号：{{ bundle.productNo || '未关联产品号' }}</text><text>生产模拟图作品包 · {{ bundle.bundleNo || `#${bundle.id}` }}</text></view><text class="status" :class="String(bundle.status || 'draft')">{{ statusText(bundle.status || 'draft') }}</text></view>
          <view v-if="bundleSimulationSrc(bundle)" class="bundle-simulation-preview" @tap="previewBundleSimulation(bundle)"><image :src="bundleSimulationSrc(bundle)" mode="aspectFit" /><text>完整生产模拟图</text></view>
          <view v-else-if="bundle.simulationAssetId" class="bundle-simulation-placeholder"><text>完整生产模拟图已保存</text><text>预览加载中</text></view>
          <view class="bundle-view-heading"><text>视角切片</text><text>用于建模与审核</text></view>
          <view class="bundle-image-grid"><view v-for="image in bundle.images" :key="image.assetId"><image v-if="bundlePreviewSrc(image)" :src="bundlePreviewSrc(image)" mode="aspectFit" /><view v-else class="bundle-image-placeholder">{{ image.label }}</view><text>{{ image.label }}切片</text></view></view>
          <view class="bundle-card-body"><text class="bundle-title">{{ bundle.productName || '生产模拟图文创作品' }}</text><text class="meta">{{ bundle.material || '材质待定' }} · {{ bundle.productSize || '尺寸待定' }} · {{ bundle.viewCount || 3 }} 张视角切片</text><text v-if="bundle.status === 'rejected' && bundle.reviewComment" class="bundle-reject-reason">未通过原因：{{ bundle.reviewComment }}</text><view class="actions"><button v-if="['draft','rejected'].includes(String(bundle.status || 'draft'))" size="mini" :loading="submittingBundleId === bundle.id" @tap="submitBundleReview(bundle)">{{ bundle.status === 'rejected' ? '重新提交生产模拟图审核' : '提交生产模拟图审核' }}</button><button v-if="bundle.status === 'approved'" size="mini" class="production" @tap="applyBundleProduction(bundle)">申请打样</button><button v-if="sampleLifecycleRoute(bundle, 'bundle')" size="mini" class="sample-progress" @tap="openSampleLifecycle(bundle, 'bundle')">样品进度</button><button size="mini" @tap="copyBundle(bundle)">复制作品包编号</button></view></view>
        </view>

        <view v-for="item in visibleAssets" :key="item.id" class="asset">
          <view class="asset-media">
            <image v-if="previewSrc(item)" :src="previewSrc(item)" mode="aspectFill" class="cover" @error="handlePreviewError(item)" />
            <view v-else class="model">{{ item.assetType === 'model' ? '3D' : 'AI' }}</view>
            <text v-if="isAiGenerated(item)" class="ai-output-badge">AI生成</text>
          </view>
          <view class="body">
            <text class="product-no asset-product-no">产品号：{{ item.productNo || '未关联产品号' }}</text>
            <view class="row"><text class="name">{{ item.title || '未命名作品' }}</text><text class="status" :class="assetDisplayStatus(item)">{{ statusText(assetDisplayStatus(item)) }}</text></view>
            <text class="meta">{{ item.assetType === 'model' ? '3D 模型' : 'AI 图片' }} · {{ item.format?.toUpperCase() || '文件' }}</text>
            <view v-if="materialFor(item)" class="material-summary">
              <text>本次工艺</text><text>{{ materialFor(item)?.name }}</text><text>{{ materialFor(item)?.hint }}</text>
            </view>
            <text v-if="source(item)" class="source">审批出处：{{ source(item) }}</text>
            <view class="actions">
              <button v-if="item.assetType === 'model' && !isGenerating(assetDisplayStatus(item))" size="mini" @tap="preview(item)">查看 3D</button>
              <button v-if="item.assetType === 'model' && !isGenerating(assetDisplayStatus(item))" size="mini" class="material" @tap="openMaterialLab(item)">换材质（PPC / 搪胶 / 毛绒）</button>
              <button v-if="item.assetType === 'model' && !isGenerating(assetDisplayStatus(item))" size="mini" class="export" :loading="downloadingModelId === String(item.id)" @tap="chooseModelExport(item)">导出模型</button>
              <button v-if="canRunDesignReview(item)" size="mini" class="design-review" @tap="openDesignReview(item)">AI 深度评审</button>
              <button v-if="canSubmitReview(item)" size="mini" :loading="submittingId === item.id" @tap="submitReview(item)">提交审核</button>
              <text v-else-if="item.assetType === 'image'" class="review-gate-tip">完成三视图或 3D 原型后可提交审核</text>
              <button v-if="canApplyProduction(item)" size="mini" class="production" @tap="applyProduction(item)">打样 / 生产</button>
              <button v-if="sampleLifecycleRoute(item, 'asset')" size="mini" class="sample-progress" @tap="openSampleLifecycle(item, 'asset')">样品进度</button>
              <button size="mini" @tap="copy(item)">复制编号</button>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import AiGeneratedNotice from '../../components/AiGeneratedNotice.vue'
import { getAssetPreviewAccess, getAssets, getMyMultiViewBundles, getProductionRequests, submitAssetReview, submitMultiViewBundleReview } from '../../api/creative'
import { apiUrl } from '../../api/client'
import { confirmCreativePolicy } from '../../utils/compliance'
import { statusText } from '../../utils/format'

// Read the same persisted login record without importing the session module.
// This keeps the work library usable when DevTools is serving a partially
// rebuilt module graph during an incremental compile.
const MINI_SESSION_KEY = 'smart_pig_auth'
function readMiniSession(): any | null {
  try {
    const value = uni.getStorageSync(MINI_SESSION_KEY)
    return value && typeof value === 'object' ? value : null
  } catch {
    return null
  }
}

const assets = ref<any[]>([])
const multiviewBundles = ref<any[]>([])
const productionRequests = ref<any[]>([])
const securedPreviews = ref<Record<string, string>>({})
const securedBundlePreviews = ref<Record<string, string>>({})
const localPreviews = ref<Record<string, { path: string; savedAt: number }>>({})
const loading = ref(false)
const loadError = ref('')
const submittingId = ref<number | null>(null)
const submittingBundleId = ref<number | null>(null)
const downloadingModelId = ref('')
const signedIn = ref(Boolean(readMiniSession()?.token))
const DESKTOP_MODEL_URL = 'https://www.zhijiansk.com/'
const threeDimensionalPolicyConfirmed = ref(false)

const bundleAssetIds = computed(() => new Set(multiviewBundles.value.flatMap(bundle => [
  ...(Array.isArray(bundle.images) ? bundle.images.map((item: any) => String(item.assetId)) : []),
  bundle.simulationAssetId ? String(bundle.simulationAssetId) : '',
].filter(Boolean))))
// The image-inspiration flow creates a short-lived, model-generated
// intermediate asset while it extracts the subject from the uploaded image.
// Keep that asset available for the second Seedream pass, but do not present
// it as a user-facing work. The tag is deliberately explicit so ordinary
// uploads and final products remain visible.
const isInternalReferenceAsset = (asset: any) => {
  const tags = Array.isArray(asset?.tags)
    ? asset.tags.map((tag: any) => typeof tag === 'string' ? tag : tag?.name).join(',')
    : String(asset?.tags || '')
  return /内部图片元素提炼|参考图预处理/.test(tags)
}
const visibleAssets = computed(() => assets.value.filter(asset => !isInternalReferenceAsset(asset) && !bundleAssetIds.value.has(String(asset.id))))
const totalWorkCount = computed(() => visibleAssets.value.length + multiviewBundles.value.length)

const isAiGenerated = (asset: any) => String(asset?.sourceType || '') === 'ai_generated'
const previewSrc = (asset: any) => {
  const local = localPreviews.value[String(asset.id)]?.path
  if (local) return local
  const secured = securedPreviews.value[String(asset.id)]
  if (secured) return secured
  return /^https:\/\//.test(String(asset.previewUrl || '')) ? asset.previewUrl : ''
}
const bundlePreviewSrc = (item: any) => {
  const raw = String(item?.previewUrl || item?.imageUrl || item?.fileUrl || '')
  if (!raw) return ''
  return /^https:\/\//.test(raw) ? raw : apiUrl(raw)
}
const bundleSimulationImage = (bundle: any) => bundle?.simulationImage || bundle?.productionSimulationImage || (bundle?.simulationAssetId ? { assetId: bundle.simulationAssetId } : null)
const bundleSimulationSrc = (bundle: any) => {
  const image = bundleSimulationImage(bundle)
  const assetId = String(image?.assetId || bundle?.simulationAssetId || '')
  const secured = assetId ? securedBundlePreviews.value[assetId] : ''
  return secured || bundlePreviewSrc(image)
}
const isGenerating = (status?: string) => ['queued', 'pending', 'running', 'processing'].includes(String(status || ''))
const assetDisplayStatus = (asset: any) => String(asset.status || 'draft')
const source = (asset: any) => {
  const tags = String(asset.tags || '')
  const match = tags.match(/审批出处=([^,，;；]+)/)
  return match?.[1] || ''
}
const canSubmitReview = (asset: any) => {
  if (asset?.assetType !== 'model' || isGenerating(assetDisplayStatus(asset))) return false
  return !['review', 'approved'].includes(String(asset.status || 'draft'))
}
const canApplyProduction = (asset: any) => asset.assetType === 'model' && asset.status === 'approved'
const canRunDesignReview = (asset: any) => ['image', 'model'].includes(String(asset?.assetType || '')) && !isGenerating(assetDisplayStatus(asset))

const materialDefinitions = [
  { tokens: ['PPC', 'PPC 高精硬塑'], name: 'PPC 高精硬塑', hint: '精密注塑 · 细节稳定' },
  { tokens: ['ABS', 'ABS 工程硬塑'], name: 'ABS 工程硬塑', hint: '工程硬塑 · 稳定量产' },
  { tokens: ['搪胶', '糖胶'], name: '搪胶（糖胶）', hint: '软触潮玩 · 圆润中空' },
  { tokens: ['软胶', 'soft vinyl'], name: '软胶', hint: '柔韧包胶 · 低反射' },
  { tokens: ['超柔绒'], name: '超柔绒', hint: '亲肤细绒 · 柔光触感' },
  { tokens: ['短毛绒'], name: '短毛绒', hint: '短密绒面 · 轮廓清晰' },
  { tokens: ['全毛绒', '毛绒'], name: '全毛绒', hint: '填充玩偶 · 刺绣细节' },
  { tokens: ['PVC'], name: 'PVC 潮玩', hint: '量产塑胶 · 易还原' },
  { tokens: ['树脂'], name: '树脂潮玩', hint: '细腻半哑 · 收藏感' },
  { tokens: ['陶瓷'], name: '陶瓷釉面', hint: '温润釉色 · 器物感' },
  { tokens: ['金属'], name: '金属', hint: '五金/徽章 · 细节浮雕' },
  { tokens: ['亚克力'], name: '透明亚克力', hint: '通透挂件 · 边缘高光' },
  { tokens: ['纸质'], name: '纸质礼盒', hint: '礼赠包装 · 低反射纸感' },
  { tokens: ['木质'], name: '木质温润', hint: '自然木作 · 细腻纹理' },
]

function materialFor(asset: any) {
  const text = [asset?.tags, asset?.prompt, asset?.title, asset?.metadataJson].filter(Boolean).join(' ').toLowerCase()
  return materialDefinitions.find(item => item.tokens.some(token => text.includes(token.toLowerCase())))
}

function absoluteMediaUrl(value: string | undefined, assetId: string, accessToken?: string) {
  if (!value && !accessToken) return ''
  if (value && /^https:\/\//.test(value)) {
    if (!accessToken || value.includes('access_token=')) return value
    return `${value}${value.includes('?') ? '&' : '?'}access_token=${encodeURIComponent(accessToken)}`
  }
  if (value?.startsWith('/')) return apiUrl(value)
  return accessToken ? apiUrl(`/api/creative/ai/assets/${encodeURIComponent(assetId)}/content?access_token=${encodeURIComponent(accessToken)}`) : ''
}

async function hydratePreviews(rows: any[]) {
  const candidates = rows.filter((asset) => !isInternalReferenceAsset(asset) && ['image', 'model'].includes(asset.assetType) && asset.id).slice(0, 12)
  const pairs = await Promise.all(candidates.map(async (asset) => {
    try {
      const access = await getAssetPreviewAccess(asset.id)
      // 模型封面使用受控的 preview-content，模型本体在点“查看3D”时单独获取。
      const raw = asset.assetType === 'model' ? access?.previewUrl : (access?.previewUrl || access?.url)
      const url = absoluteMediaUrl(raw, String(asset.id), access?.accessToken)
      return url ? [String(asset.id), url] as const : null
    } catch {
      return null
    }
  }))
  const next: Record<string, string> = {}
  pairs.forEach((pair) => { if (pair) next[pair[0]] = pair[1] })
  securedPreviews.value = next
  // Save a small, account-scoped offline preview set. Remote signed URLs still
  // refresh in the background, while saved files keep recent works visible on
  // a slow or temporarily unavailable network.
  pairs.forEach((pair) => { if (pair) void cachePreview(pair[0], pair[1]) })
}

async function hydrateBundlePreviews(rows: any[]) {
  const pairs = await Promise.all(rows.map(async bundle => {
    const image = bundleSimulationImage(bundle)
    const assetId = String(image?.assetId || bundle?.simulationAssetId || '')
    if (!assetId || bundlePreviewSrc(image)) return null
    try {
      const access = await getAssetPreviewAccess(assetId)
      const raw = access?.previewUrl || access?.url
      const url = absoluteMediaUrl(raw, assetId, access?.accessToken)
      return url ? [assetId, url] as const : null
    } catch {
      return null
    }
  }))
  const next: Record<string, string> = {}
  pairs.forEach(pair => { if (pair) next[pair[0]] = pair[1] })
  securedBundlePreviews.value = next
  // Keep the signed URL on the row too, so a copied bundle remains previewable
  // during this page lifetime without another access request.
  if (Object.keys(next).length) {
    multiviewBundles.value = multiviewBundles.value.map(bundle => {
      const image = bundleSimulationImage(bundle)
      const assetId = String(image?.assetId || bundle?.simulationAssetId || '')
      const url = assetId ? next[assetId] : ''
      return url ? { ...bundle, simulationImage: { ...(image || {}), assetId: Number(assetId), previewUrl: url } } : bundle
    })
  }
}

function worksCacheKey() {
  // The session shape intentionally exposes only the public mini-user fields;
  // username is unique and prevents cached previews crossing accounts.
  const username = String(readMiniSession()?.user?.username || '').trim()
  return username ? `smart_pig_works_${username}` : ''
}

function saveWorksCache(rows: any[]) {
  const key = worksCacheKey()
  if (!key) return
  const cacheRows = rows.slice(0, 100).map((asset) => {
    const copy = { ...asset }
    delete copy.previewUrl
    delete copy.fileUrl
    delete copy.signedPreviewUrl
    delete copy.signedFileUrl
    return copy
  })
  try { uni.setStorageSync(key, { rows: cacheRows, previews: localPreviews.value, savedAt: Date.now() }) } catch { /* Cache is optional. */ }
}

function restoreWorksCache() {
  const key = worksCacheKey()
  if (!key) return
  try {
    const cached = uni.getStorageSync(key)
    const rows = Array.isArray(cached?.rows) ? cached.rows : []
    localPreviews.value = cached?.previews && typeof cached.previews === 'object' ? cached.previews : {}
    if (!rows.length) return
    assets.value = rows
    void hydratePreviews(rows)
  } catch { /* A missing or stale cache must never block the work library. */ }
}

async function cachePreview(assetId: string, url: string) {
  if (localPreviews.value[assetId]?.path || !url) return
  try {
    const downloaded = await uni.downloadFile({ url, timeout: 30000 })
    if (downloaded.statusCode < 200 || downloaded.statusCode >= 300 || !downloaded.tempFilePath) return
    const saved = await uni.saveFile({ tempFilePath: downloaded.tempFilePath })
    const next = { ...localPreviews.value, [assetId]: { path: saved.savedFilePath, savedAt: Date.now() } }
    const entries = Object.entries(next).sort((a, b) => b[1].savedAt - a[1].savedAt)
    const retained = Object.fromEntries(entries.slice(0, 20)) as Record<string, { path: string; savedAt: number }>
    entries.slice(20).forEach(([, preview]) => { void uni.removeSavedFile({ filePath: preview.path }).catch(() => undefined) })
    localPreviews.value = retained
    saveWorksCache(assets.value)
  } catch {
    // Preview caching is a resilience layer and must never block the library.
  }
}

function handlePreviewError(asset: any) {
  const assetId = String(asset?.id || '')
  if (!assetId || !localPreviews.value[assetId]) return
  const next = { ...localPreviews.value }
  delete next[assetId]
  localPreviews.value = next
  saveWorksCache(assets.value)
}

function readableLoadError(error: any) {
  const message = String(error?.message || error || '')
  if (/timeout|timed out/i.test(message)) return '网络响应较慢，请稍后重新加载。'
  if (/network|fail|connect|domain|dns/i.test(message)) return '暂时无法连接作品库，请检查网络后重试。'
  return '作品暂时无法加载，请稍后重新加载。'
}

async function refresh(notify = false) {
  if (!signedIn.value || !readMiniSession()?.token) {
    loading.value = false
    uni.stopPullDownRefresh()
    return
  }
  if (loading.value) {
    uni.stopPullDownRefresh()
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const [rows, bundles, requests] = await Promise.all([
      getAssets(),
      getMyMultiViewBundles().catch(() => []),
      getProductionRequests().catch(() => []),
    ])
    assets.value = Array.isArray(rows) ? rows : []
    multiviewBundles.value = Array.isArray(bundles) ? bundles : []
    productionRequests.value = Array.isArray(requests) ? requests : []
    saveWorksCache(assets.value)
    // 图片签名地址在后台补齐，作品列表本身不等待这些预览请求。
    void hydratePreviews(assets.value)
    void hydrateBundlePreviews(multiviewBundles.value)
    if (notify) uni.showToast({ title: '作品已更新', icon: 'success' })
  } catch (error: any) {
    loadError.value = readableLoadError(error)
    if (notify) uni.showToast({ title: '暂时无法加载作品', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

async function preview(asset: any) {
  if (!threeDimensionalPolicyConfirmed.value && !(await confirmCreativePolicy('three-dimensional'))) return
  threeDimensionalPolicyConfirmed.value = true
  showDesktopModelNotice('preview')
}

async function openMaterialLab(_asset?: any) {
  if (!threeDimensionalPolicyConfirmed.value && !(await confirmCreativePolicy('three-dimensional'))) return
  threeDimensionalPolicyConfirmed.value = true
  showDesktopModelNotice('material')
}

function showDesktopModelNotice(action: 'preview' | 'material') {
  const feature = action === 'preview' ? '3D 模型预览' : '模型材质编辑'
  uni.showModal({
    title: '请使用电脑端',
    content: `小程序端暂不支持${feature}，请在电脑浏览器打开：${DESKTOP_MODEL_URL}`,
    cancelText: '知道了',
    confirmText: '复制网址',
    success: (result) => {
      if (!result.confirm) return
      uni.setClipboardData({
        data: DESKTOP_MODEL_URL,
        success: () => uni.showToast({ title: '电脑端网址已复制', icon: 'success' }),
      })
    },
  })
}

type ModelExportFormat = 'GLB' | 'OBJ' | 'STL'

const modelExportOptions: Array<{ format: ModelExportFormat; label: string }> = [
  { format: 'GLB', label: 'GLB · 通用 3D 模型' },
  { format: 'OBJ', label: 'OBJ · ZIP 压缩包' },
  { format: 'STL', label: 'STL · 3D 打印模型' },
]

function chooseModelExport(asset: any) {
  const assetId = String(asset?.id || '')
  if (!/^\d+$/.test(assetId)) {
    uni.showToast({ title: '作品编号无效，无法导出模型', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: modelExportOptions.map(item => item.label),
    success: (result) => {
      const selected = modelExportOptions[result.tapIndex]
      if (!selected) return
      if (selected.format === 'OBJ') {
        uni.showModal({
          title: '导出 OBJ（ZIP）',
          content: 'OBJ 会以 ZIP 压缩包下载。服务端可能需要转换，请耐心等待。',
          confirmText: '开始导出',
          success: (confirmation) => { if (confirmation.confirm) void downloadModel(asset, selected.format) },
        })
        return
      }
      void downloadModel(asset, selected.format)
    },
  })
}

async function downloadModel(asset: any, format: ModelExportFormat) {
  const assetId = String(asset?.id || '')
  const session = readMiniSession()
  if (!session?.token) {
    promptLogin('导出模型')
    return
  }
  if (downloadingModelId.value) return
  downloadingModelId.value = assetId
  const outputName = format === 'OBJ' ? 'OBJ（ZIP）' : format
  uni.showLoading({ title: format === 'OBJ' ? '正在转换 OBJ（ZIP）…' : `正在导出 ${outputName}…`, mask: true })
  try {
    const result = await uni.downloadFile({
      url: apiUrl(`/api/creative/ai/assets/${encodeURIComponent(assetId)}/download-model?format=${format}`),
      header: { Authorization: `Bearer ${session.token}` },
    })
    if (result.statusCode < 200 || result.statusCode >= 300 || !result.tempFilePath) {
      throw new Error(`导出失败（${result.statusCode || '网络异常'}）`)
    }
    const saved = await uni.saveFile({ tempFilePath: result.tempFilePath })
    try {
      await uni.openDocument({ filePath: saved.savedFilePath, showMenu: true })
    } catch {
      uni.showModal({
        title: '模型已保存',
        content: `${outputName} 已保存到微信文件。若当前设备不能直接打开，请在“微信文件”中发送到电脑或专业建模软件。`,
        showCancel: false,
      })
    }
  } catch (error: any) {
    uni.showToast({ title: error?.message || `${outputName} 导出失败，请稍后重试`, icon: 'none' })
  } finally {
    uni.hideLoading()
    downloadingModelId.value = ''
  }
}

function copy(asset: any) {
  uni.setClipboardData({ data: String(asset.assetNo || asset.id) })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/conversation-create/index' })
}

function goLogin() {
  const redirect = encodeURIComponent('/pages/works/index')
  uni.navigateTo({ url: `/pages/login/index?from=works&redirect=${redirect}` })
}

function goHome() {
  const pages = getCurrentPages()
  const previousPage = pages[pages.length - 2] as any
  if (previousPage?.route === 'pages/home/index') {
    uni.navigateBack()
    return
  }
  uni.reLaunch({ url: '/pages/home/index' })
}

function promptLogin(action: string) {
  uni.showModal({
    title: `登录后可${action}`,
    content: '登录后可使用个人作品与文件服务。您也可以暂不登录，继续浏览小程序。',
    cancelText: '暂不登录',
    confirmText: '去登录',
    success: (result) => { if (result.confirm) goLogin() },
  })
}

function resetGuestState() {
  assets.value = []
  multiviewBundles.value = []
  productionRequests.value = []
  securedPreviews.value = {}
  securedBundlePreviews.value = {}
  localPreviews.value = {}
  loadError.value = ''
  loading.value = false
}

function copyBundle(bundle: any) {
  uni.setClipboardData({ data: String(bundle?.bundleNo || bundle?.id || '') })
}

function previewBundleSimulation(bundle: any) {
  const current = bundleSimulationSrc(bundle)
  if (!current) {
    uni.showToast({ title: '生产模拟图还在加载，请稍候', icon: 'none' })
    return
  }
  uni.previewImage({ current, urls: [current] })
}

function sampleLifecycleRequest(item: any, kind: 'asset' | 'bundle') {
  return productionRequests.value.find(request => {
    if (String(request?.requestType || '') !== 'sample') return false
    return kind === 'bundle'
      ? String(request?.multiviewBundleId || '') === String(item?.id || '')
      : String(request?.assetId || '') === String(item?.id || '')
  }) || null
}

function sampleLifecycleRoute(item: any, kind: 'asset' | 'bundle') {
  const request = sampleLifecycleRequest(item, kind)
  const projectId = request?.projectId || item?.projectId
  const versionId = request?.versionId || item?.versionId
  if (!request?.id || !projectId || !versionId) return ''
  const productNo = request?.productNo || item?.productNo || ''
  const productQuery = productNo ? `&productNo=${encodeURIComponent(String(productNo))}` : ''
  return `/pages/sample-lifecycle/index?projectId=${encodeURIComponent(String(projectId))}&versionId=${encodeURIComponent(String(versionId))}&requestId=${encodeURIComponent(String(request.id))}&title=${encodeURIComponent(item?.title || item?.productName || '样品申请')}${productQuery}`
}

function openSampleLifecycle(item: any, kind: 'asset' | 'bundle') {
  const url = sampleLifecycleRoute(item, kind)
  if (url) uni.navigateTo({ url })
  else uni.showToast({ title: '该申请缺少项目版本信息，请刷新作品库', icon: 'none' })
}

function submitBundleReview(bundle: any) {
  const context = uni.getStorageSync('creation_context') || {}
  const purpose = context.purpose === 'museum_sale' ? 'museum_sale' : 'personal'
  const museumId = purpose === 'museum_sale' ? String(context.museum?.id || '') : undefined
  const campaign = context.campaign && typeof context.campaign === 'object' ? context.campaign : null
  if (purpose === 'museum_sale' && !museumId) {
    uni.showToast({ title: '请先选择服务博物馆后再提交审核', icon: 'none' })
    return
  }
  if (campaign?.key && (purpose !== 'museum_sale' || campaign.channelCode !== context.museum?.channelCode)) {
    uni.showToast({ title: '优先征集任务与当前渠道不一致', icon: 'none' })
    return
  }
  uni.showModal({
    title: bundle.status === 'rejected' ? '重新提交生产模拟图审核' : '提交生产模拟图审核',
    content: purpose === 'museum_sale' ? `完整生产模拟图及正面、侧面和背面切片将作为一个作品包提交至${context.museum?.name || '目标渠道'}。` : '完整生产模拟图及三张视角切片将作为一个作品包提交审核。',
    confirmText: '提交审核',
    success: async result => {
      if (!result.confirm) return
      submittingBundleId.value = bundle.id
      try {
        const response = await submitMultiViewBundleReview(bundle.id, {
          purpose,
          museumId,
          ...(campaign?.key ? { campaignKey: campaign.key } : {}),
        })
          uni.showToast({ title: response.message || '生产模拟图已提交审核', icon: 'success' })
        await refresh(false)
      } catch (error: any) {
        uni.showToast({ title: error?.message || '提交生产模拟图审核失败', icon: 'none' })
      } finally {
        submittingBundleId.value = null
      }
    },
  })
}

async function applyBundleProduction(bundle: any) {
  uni.showLoading({ title: '正在检查申请', mask: true })
  try {
    const requests = await getProductionRequests()
    const existing = Array.isArray(requests) && requests.some(request => String(request?.multiviewBundleId) === String(bundle.id) && String(request?.status || '') !== 'rejected')
    if (existing) {
      uni.showModal({
        title: '已有打样申请',
        content: '该生产模拟图作品包已经提交过打样申请，请在商品化申请中继续处理。',
        cancelText: '取消', confirmText: '查看申请',
        success: result => { if (result.confirm) uni.navigateTo({ url: '/pages/production-requests/index' }) },
      })
      return
    }
    const projectQuery = bundle.projectId ? `&projectId=${encodeURIComponent(String(bundle.projectId))}` : ''
    const versionQuery = bundle.versionId ? `&versionId=${encodeURIComponent(String(bundle.versionId))}` : ''
    const productQuery = bundle.productNo ? `&productNo=${encodeURIComponent(String(bundle.productNo))}` : ''
    uni.navigateTo({ url: `/pages/production/index?bundleId=${encodeURIComponent(String(bundle.id))}&title=${encodeURIComponent(bundle.productName || '生产模拟图作品')}${projectQuery}${versionQuery}${productQuery}` })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '暂时无法检查申请', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function applyProduction(asset: any) {
  uni.showLoading({ title: '正在检查申请', mask: true })
  try {
    const requests = await getProductionRequests()
    const existing = Array.isArray(requests) && requests.some((request) => String(request?.assetId) === String(asset.id) && String(request?.status || '') !== 'rejected')
    if (existing) {
      uni.showModal({
        title: '已有商品化申请',
        content: '该作品已有打样或生产申请，请在商品化申请中继续处理。',
        cancelText: '取消',
        confirmText: '查看申请',
        success: (result) => { if (result.confirm) uni.navigateTo({ url: '/pages/production-requests/index' }) },
      })
      return
    }
    const projectQuery = asset.projectId ? `&projectId=${encodeURIComponent(String(asset.projectId))}` : ''
    const versionQuery = asset.versionId ? `&versionId=${encodeURIComponent(String(asset.versionId))}` : ''
    const productQuery = asset.productNo ? `&productNo=${encodeURIComponent(String(asset.productNo))}` : ''
    uni.navigateTo({ url: `/pages/production/index?assetId=${encodeURIComponent(String(asset.id))}&title=${encodeURIComponent(asset.title || '3D模型')}${projectQuery}${versionQuery}${productQuery}` })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '暂时无法检查申请，请稍后重试', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

function openDesignReview(asset: any) {
  uni.navigateTo({ url: `/pages/review/index?assetId=${encodeURIComponent(String(asset.id))}&title=${encodeURIComponent(asset.title || '作品')}` })
}

function submitReview(asset: any) {
  const context = uni.getStorageSync('creation_context') || {}
  const purpose = context.purpose === 'museum_sale' ? 'museum_sale' : 'personal'
  const museumId = context.museum?.id
  const campaign = context.campaign && typeof context.campaign === 'object' ? context.campaign : null
  const campaignKey = typeof campaign?.key === 'string' ? campaign.key : ''
  if (purpose === 'museum_sale' && !museumId) {
    uni.showToast({ title: '请先选择服务博物馆后再提交审核', icon: 'none' })
    return
  }
  if (campaignKey && (purpose !== 'museum_sale' || campaign.channelCode !== context.museum?.channelCode)) {
    uni.showToast({ title: '优先征集任务与当前渠道不一致，请重新选择任务方向', icon: 'none' })
    return
  }
  const destination = purpose === 'museum_sale' ? `博物馆售卖 · ${context.museum?.name || ''}` : '个人创作'
  uni.showModal({
    title: '提交作品审核',
    content: campaignKey
      ? `将按「${destination}」投稿「${campaign.title || '优先征集'}」。审核通过后自动获得 ${campaign.rewardAmount || ''} 积分。`
      : `将按「${destination}」提交。审核通过后，3D 作品可申请打样或生产。`,
    success: async (result) => {
      if (!result.confirm) return
      submittingId.value = asset.id
      try {
        const response = await submitAssetReview(asset.id, { purpose, museumId, projectId: asset.projectId, versionId: asset.versionId, ...(campaignKey ? { campaignKey } : {}) })
        if (campaignKey) {
          const nextContext = { ...context }
          delete nextContext.campaign
          uni.setStorageSync('creation_context', nextContext)
        }
        uni.showToast({ title: response?.message || '已提交审核', icon: 'success' })
        await refresh(false)
      } catch (error: any) {
        uni.showToast({ title: error.message || '提交审核失败', icon: 'none' })
      } finally {
        submittingId.value = null
      }
    },
  })
}

onShow(() => {
  signedIn.value = Boolean(readMiniSession()?.token)
  if (signedIn.value) {
    restoreWorksCache()
    void refresh(false)
  }
  else resetGuestState()
})

onPullDownRefresh(() => {
  signedIn.value = Boolean(readMiniSession()?.token)
  if (signedIn.value) refresh(false)
  else uni.stopPullDownRefresh()
})
</script>

<style scoped lang="scss">
.ai-disclosure{margin:-18rpx 0 10rpx}.asset-media{position:relative;flex:0 0 180rpx;width:180rpx;height:180rpx}.asset-media .cover,.asset-media .model{display:block;width:180rpx;height:180rpx}.asset-media .model{display:flex}.ai-output-badge{position:absolute;left:10rpx;top:10rpx;padding:5rpx 8rpx;border-radius:6rpx;background:rgba(107,67,49,.88);color:#fff;font-size:16rpx;font-weight:900;line-height:1.2}
.page{min-height:100vh;padding:34rpx}.intro{display:flex;align-items:flex-start;justify-content:space-between;gap:20rpx;padding:20rpx 4rpx 34rpx}.title{font-size:48rpx;font-weight:800;display:block}.sub{font-size:24rpx;color:#8e7469;display:block;margin-top:12rpx;line-height:1.55}.refresh{margin:4rpx 0 0;background:#f4e5db;color:#873e26;font-size:21rpx}.section{margin-top:10rpx}.section-head{display:flex;justify-content:space-between;align-items:center;margin:24rpx 4rpx 18rpx;font-size:30rpx;font-weight:750}.section-head text:last-child{font-size:21rpx;color:#9a7d70;font-weight:400}.empty{padding:120rpx 34rpx;text-align:center;color:#9c8479;line-height:1.8}.create-first{width:300rpx;height:82rpx;line-height:82rpx;margin:28rpx auto 0;border-radius:42rpx;background:#963c23;color:#fff;font-size:27rpx}.asset,.job-card{display:flex;background:#fff;border-radius:22rpx;margin-bottom:22rpx;overflow:hidden;box-shadow:0 8rpx 22rpx rgba(65,34,20,.07)}.cover,.model{width:180rpx;height:180rpx;flex-shrink:0}.cover{background:#f4e7df}.model,.job-icon{background:linear-gradient(145deg,#4b2518,#bc5a34);color:#fff;font-size:38rpx;font-weight:800;display:flex;align-items:center;justify-content:center}.body,.job-body{padding:20rpx;min-width:0;flex:1}.row{display:flex;align-items:center;gap:12rpx}.name{font-weight:700;font-size:29rpx;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1}.status{font-size:20rpx;border-radius:20rpx;padding:6rpx 12rpx;background:#f9e6d5;color:#a2492b;white-space:nowrap}.status.approved,.status.succeeded,.status.paid{background:#e4f5e9;color:#248653}.status.rejected,.status.failed{background:#ffe5e1;color:#ba3d2e}.status.running,.status.queued,.status.processing{background:#fff0d5;color:#aa681e}.meta,.source,.generation,.failure,.progress-text{display:block;font-size:21rpx;color:#967c70;margin-top:10rpx;line-height:1.5}.generation{color:#8b5a42}.source{color:#9d4e30}.failure{color:#ba3d2e;white-space:normal}.project-entry{display:flex;align-items:center;justify-content:space-between;gap:12rpx;margin-top:11rpx;padding:10rpx 12rpx;border-radius:10rpx;background:#edf3ed;color:#5d7969;font-size:19rpx;font-weight:750}.project-entry text:last-child{color:#789081;font-size:18rpx}.progress-line{height:10rpx;border-radius:8rpx;background:#f2e4da;margin-top:14rpx;overflow:hidden}.progress-value{height:100%;background:linear-gradient(90deg,#c86a40,#8b351f);border-radius:inherit}.progress-text{margin-top:7rpx;font-size:19rpx}.actions{display:flex;flex-wrap:wrap;gap:10rpx;margin-top:14rpx}.actions button{margin:0;background:#f8ede5;color:#843b23;font-size:20rpx}.actions .production{background:#f8d9c0;color:#74301d}.job-card{padding:0}.job-icon{width:130rpx;min-height:150rpx;flex-shrink:0;font-size:29rpx}.job-card.failed .job-icon{background:linear-gradient(145deg,#7f2920,#c64d3d)}
.multiview-bundle-card{margin-bottom:22rpx;padding:19rpx;border:1rpx solid rgba(105,135,113,.24);border-radius:18rpx;background:#fff;box-shadow:0 9rpx 21rpx rgba(67,53,37,.055)}.bundle-card-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx}.bundle-card-head view{display:flex;min-width:0;flex-direction:column;gap:5rpx}.bundle-card-head view text:first-child{color:#3f5548;font-size:27rpx;font-weight:800}.bundle-card-head view text:last-child{color:#9aa79d;font-size:18rpx}.bundle-card-head view .product-no{color:#365e4a;font-size:25rpx;font-weight:900}.bundle-card-head .status{background:#edf3ed;color:#5d7969}.bundle-card-head .status.review{background:#fff4dc;color:#9c743c}.bundle-card-head .status.approved{background:#e5f2e8;color:#4f8463}.bundle-card-head .status.rejected{background:#fff0ec;color:#ad5d4a}.bundle-image-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8rpx;margin-top:14rpx}.bundle-image-grid>view{overflow:hidden;border:1rpx solid #e1e9e2;border-radius:10rpx;background:#f8faf8}.bundle-image-grid image,.bundle-image-placeholder{display:block;width:100%;height:170rpx;background:#edf1ed}.bundle-image-placeholder{display:flex;align-items:center;justify-content:center;color:#809087;font-size:18rpx}.bundle-image-grid>view>text{display:block;padding:7rpx;color:#6d7f72;font-size:17rpx;font-weight:800;text-align:center}.bundle-card-body{display:flex;min-width:0;flex-direction:column}.bundle-title{display:block;margin-top:13rpx;color:#3d4c43;font-size:25rpx;font-weight:800}.bundle-reject-reason{display:block;margin-top:10rpx;padding:9rpx 10rpx;border-left:3rpx solid #bb6b55;border-radius:0 8rpx 8rpx 0;background:#fff3ef;color:#9c5946;font-size:19rpx;line-height:1.5}.asset-product-no{display:block;margin-bottom:7rpx;color:#365e4a;font-size:24rpx;font-weight:900}
.bundle-simulation-preview{overflow:hidden;margin-top:14rpx;border:1rpx solid #dbe6dc;border-radius:10rpx;background:#f7faf7}.bundle-simulation-preview image{display:block;width:100%;height:250rpx;background:#edf1ed}.bundle-simulation-preview text,.bundle-simulation-placeholder text{display:block;padding:7rpx;color:#5f796a;font-size:18rpx;font-weight:800;text-align:center}.bundle-simulation-placeholder{display:flex;align-items:center;justify-content:center;flex-direction:column;min-height:120rpx;margin-top:14rpx;border:1rpx dashed #cddbd0;border-radius:10rpx;background:#f7faf7;color:#6b8173}.bundle-simulation-placeholder text+text{padding-top:0;font-size:16rpx;font-weight:400;color:#93a197}
.bundle-view-heading{display:flex;align-items:baseline;justify-content:space-between;gap:8rpx;margin-top:14rpx;color:#6d8173;font-size:17rpx;font-weight:800}.bundle-view-heading text:last-child{color:#9ba79f;font-size:15rpx;font-weight:400}
.review-gate-tip{display:flex;align-items:center;min-height:48rpx;padding:0 12rpx;border-radius:9rpx;background:#f3f1ec;color:#8a8177;font-size:18rpx;line-height:1.35}.actions .sample-progress{background:#e7f2e9;color:#527363}
</style>

<style scoped lang="scss">
.page{background:radial-gradient(ellipse at 10% 0%,rgba(151,177,163,.17),transparent 29%),linear-gradient(180deg,#faf8f3,#f0e9df)}.title{font-family:"Songti SC","STSong",serif;color:#302b26}.sub{color:#82786d}.refresh{background:#edf3ed;color:#607b6e}.section-head{font-family:"Songti SC","STSong",serif}.asset,.job-card{border:1rpx solid rgba(129,112,93,.13);box-shadow:0 9rpx 21rpx rgba(67,53,37,.055)}.cover{background:#eef2eb}.model,.job-icon{background:linear-gradient(145deg,#5f7f71,#9eb5a8)}.job-card.failed .job-icon{background:linear-gradient(145deg,#865346,#bf765f)}.status{background:#f5ece4;color:#9d5c48}.status.approved,.status.succeeded,.status.paid{background:#e7f1e8;color:#567a67}.status.running,.status.queued,.status.processing{background:#f6f0df;color:#9b7540}.meta,.source,.generation,.progress-text{color:#8c8176}.source{color:#7d9587}.project-entry{background:#edf3ed;color:#5f7a69}.progress-line{background:#ebe5dc}.progress-value{background:linear-gradient(90deg,#a56e58,#6e8b7c)}.actions button{background:#f2f5ef;color:#59776a}.actions .material{background:#dcece2;color:#426d5a}.actions .export{background:#e8edf5;color:#526b85}.actions .production{background:#efe1d5;color:#8c5947}.actions .design-review{background:#eeeaf5;color:#6b5b8b}.create-first{border-radius:17rpx;background:linear-gradient(135deg,#3e3933,#617e71)}.material-summary{display:flex;align-items:center;flex-wrap:wrap;gap:7rpx;margin-top:11rpx}.material-summary text:first-child{padding:3rpx 8rpx;border-radius:8rpx;background:#eef2ec;color:#728578;font-size:16rpx;font-weight:800}.material-summary text:nth-child(2){color:#476c5b;font-size:20rpx;font-weight:850}.material-summary text:last-child{color:#978c80;font-size:17rpx}
.back-home{margin:4rpx 0 0;border:1rpx solid #d5e0d6;background:#fffdf9;color:#587666;font-size:21rpx}.guest-state{display:flex;align-items:center;flex-direction:column;margin:16rpx 0 36rpx;padding:64rpx 38rpx 48rpx;border:1rpx solid rgba(113,136,120,.22);border-radius:16rpx;background:rgba(255,253,249,.9);box-shadow:0 12rpx 30rpx rgba(67,53,37,.06);text-align:center}.guest-mark{display:grid;place-items:center;width:88rpx;height:88rpx;border-radius:16rpx;background:#edf3ed;color:#567765;font-family:"Songti SC","STSong",serif;font-size:42rpx;font-weight:700}.guest-title{display:block;margin-top:27rpx;color:#37332d;font-family:"Songti SC","STSong",serif;font-size:34rpx;font-weight:700}.guest-copy{display:block;margin-top:13rpx;color:#877d72;font-size:23rpx;line-height:1.7}.guest-login,.guest-browse{width:100%;height:84rpx;line-height:84rpx;margin:32rpx 0 0;border-radius:12rpx;font-size:26rpx;font-weight:800}.guest-login{background:#456a59;color:#fffdf8}.guest-browse{margin-top:16rpx;border:1rpx solid #d7dfd7;background:#fffdfa;color:#557364}
.load-error{display:flex;align-items:center;flex-direction:column;margin:20rpx 0;padding:90rpx 42rpx 70rpx;border:1rpx solid #e6d9c9;border-radius:16rpx;background:#fffdf9;text-align:center}.load-error-title{color:#4c4137;font-family:"Songti SC","STSong",serif;font-size:34rpx;font-weight:700}.load-error-copy{display:block;margin-top:16rpx;color:#897c70;font-size:23rpx;line-height:1.7}.load-retry{width:280rpx;height:82rpx;line-height:82rpx;margin-top:30rpx;border-radius:12rpx;background:#456a59;color:#fffdf8;font-size:25rpx;font-weight:800}.cache-warning{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin:8rpx 0 22rpx;padding:17rpx 18rpx;border:1rpx solid #eadfc9;border-radius:12rpx;background:#fff9ed;color:#88704f;font-size:20rpx;line-height:1.5}.cache-warning text:first-child{min-width:0;flex:1}.cache-warning text:last-child{flex:none;color:#617967;font-weight:800;white-space:nowrap}
</style>
