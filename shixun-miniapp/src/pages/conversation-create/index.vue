<template>
  <view class="page chat-experience">
    <scroll-view class="chat" scroll-y :scroll-into-view="scrollIntoView" scroll-with-animation>
      <view class="workspace-intro">
        <view class="workspace-intro-top"><view class="online-mark"><view class="online-dot" /><text>AI 工作台</text></view><text class="workspace-ref">{{ projectId ? `项目 ${projectId}` : '新项目' }}</text></view>
        <text class="workspace-title">把灵感说出来，剩下的交给我</text>
        <text class="workspace-subtitle">我会帮你整理产品方向、生成视觉，并继续推进生产模拟图、3D 和打样。</text>
        <view v-if="selectedProduct || material" class="brief-strip">
          <view v-if="selectedProduct" class="brief-chip"><text>产品</text><text>{{ selectedProduct.name }}</text></view>
          <view v-if="material" class="brief-chip"><text>材质</text><text>{{ material }}</text></view>
          <view v-if="productSize" class="brief-chip"><text>尺寸</text><text>{{ productSize }}</text></view>
          <view v-if="mode" class="brief-chip muted"><text>{{ mode === 'image' ? '参考图' : '文字灵感' }}</text></view>
        </view>
        <view v-if="campaignContext" class="campaign-strip">
          <view><text>优先征集</text><text>{{ campaignContext.title }}</text><text>面向 {{ campaignContext.targetName }} · {{ campaignContext.collectionStyle }}</text></view>
          <text>通过 +{{ campaignContext.rewardAmount }} 积分</text>
        </view>
      </view>

      <AiGeneratedNotice class="ai-disclosure" compact description="对话建议、提示词和后续生成的图片、生产模拟图、3D 原型均可能由人工智能生成，仅供创作参考，商业使用前请人工复核。" />

      <view v-for="item in messages" :id="`message-${item.id}`" :key="item.id" class="message-row" :class="item.role">
        <view v-if="item.role === 'assistant'" class="message-avatar assistant-avatar">之</view>
        <view class="message-content">
          <view class="message-meta"><text>{{ item.role === 'assistant' ? '之间智造' : '我' }}</text><text v-if="item.role === 'assistant'">AI 助手</text></view>
          <view class="bubble" :class="{ 'image-bubble': item.imageUrl || item.imageAssetId }">
            <image
              v-if="item.imageUrl"
              class="message-image"
              :src="item.imageUrl"
              mode="aspectFit"
              @tap="previewMessageImage(item)"
            />
            <view v-else-if="item.imageAssetId" class="message-image-loading" @tap="previewMessageImage(item)">
              <text>图片加载中</text>
            </view>
            <!-- selectable uses WeChat's native text-selection menu, which
                 remains available even when the JS clipboard scope is not
                 declared in the mini-program privacy guide. -->
            <text v-if="item.text" class="message-text" selectable>{{ item.text }}</text>
            <view v-if="item.text && !item.imageUrl && !item.imageAssetId" class="message-actions">
              <text class="message-copy" aria-label="复制这段文字" @tap.stop="copyMessageText(item)">复制</text>
            </view>
            <view v-if="item.imageUrl || item.imageAssetId" class="message-image-footer">
              <text :class="{ failed: item.imageState === 'failed' }">{{ item.imageState === 'uploading' ? '正在上传灵感图片…' : item.imageState === 'failed' ? '上传失败，请重新选择' : '已上传灵感图片 · 点击查看大图' }}</text>
              <text v-if="item.role === 'user'" class="message-image-reselect" @tap.stop="pickInspirationImage">重新选择</text>
            </view>
          </view>
        </view>
        <view v-if="item.role === 'user'" class="message-avatar user-avatar">我</view>
      </view>

      <view v-if="chatThinking" id="chat-thinking" class="thinking-row" aria-label="之间正在思考">
        <view class="message-avatar assistant-avatar thinking-avatar">之</view>
        <view class="thinking-content">
          <view class="thinking-bubble">
            <view class="thinking-title-row"><text class="thinking-title">之间正在思考</text><view class="thinking-dots" aria-hidden="true"><view class="thinking-dot" /><view class="thinking-dot" /><view class="thinking-dot" /></view></view>
            <text class="thinking-detail">{{ thinkingLabel }}</text>
          </view>
        </view>
      </view>

      <view v-if="phase === 'result'" id="result-output" class="output-surface">
        <view class="output-header"><view><text class="surface-kicker">IMAGE OUTPUT</text><text class="surface-title">产品视觉已完成</text></view><view class="output-status"><view class="status-check">✓</view><text>已保存</text></view></view>
        <view class="visual-frame"><image v-if="previewUrl" class="result-image" :src="previewUrl" mode="aspectFit" @tap="previewImage" /><view v-else class="result-placeholder"><text>{{ selectedProduct?.mark || '作' }}</text><text>作品已保存到作品库</text></view><view class="visual-badge">AI 生成</view></view>
        <view class="output-info"><view><text>{{ selectedProduct?.name || '文创产品' }}</text><text>{{ material || '材质待定' }} · {{ productSize || '尺寸待定' }} · {{ mode === 'image' ? '参考图改造' : '文字生图' }}</text></view><text class="output-open" @tap="previewImage">查看大图 ›</text></view>
        <view v-if="refiningImage" class="refinement-panel"><view class="refinement-heading"><view><text class="surface-kicker">REFINE THIS IMAGE</text><text>告诉我哪里不满意</text></view><text class="refinement-close" @tap="cancelRefinement">×</text></view><textarea v-model="refinementNote" maxlength="500" auto-height class="text-input refinement-input" placeholder="例如：保留主体和构图，把边缘改得更简洁，去掉文字。" /><view class="input-foot"><text>{{ refinementNote.length }}/500</text><button class="dark-button" :disabled="!refinementNote.trim() || busy" :loading="busy" @tap="regenerateWithRefinement">基于当前图重新生成</button></view></view>
        <view v-else class="output-actions"><view class="output-action primary" @tap="generateMultiView"><view class="action-icon">观</view><view><text>生成生产模拟图</text><text>一张图包含三个标准视角</text></view><text class="action-arrow">›</text></view><view class="output-action" @tap="startRefinement"><view class="action-icon warm">改</view><view><text>不满意，继续修改</text><text>基于当前图再生成</text></view><text class="action-arrow">›</text></view><view class="output-action" @tap="generateModel"><view class="action-icon dark">3D</view><view><text>单图生成 3D</text><text>直接创建产品原型</text></view><text class="action-arrow">›</text></view><view class="output-action disabled"><view class="action-icon gold">样</view><view><text>完成生产模拟图或 3D 原型后打样</text><text>当前产品图仅用于继续创作</text></view></view></view>
      </view>

      <view v-if="phase === 'multiview'" id="multiview-output" class="output-surface">
        <view class="output-header"><view><text class="surface-kicker">PRODUCTION SIMULATION</text><text class="surface-title">生产模拟图已完成</text></view><view class="output-status"><view class="status-check">✓</view><text>已保存</text></view></view>
        <text class="surface-note">一张横向图包含正面、侧面和背面，三个面板保持同一产品比例和基线；系统同时保存三张视角切片用于 3D 建模和审核。</text>
        <view v-if="simulationImageUrl" class="simulation-frame" @tap="previewSimulationImage"><image :src="simulationImageUrl" mode="aspectFit" /><view class="visual-badge">生产模拟图</view></view>
        <view v-else class="simulation-placeholder"><text>生产模拟图已保存</text><text>完整预览加载中，可先查看下方视角切片</text></view>
        <view class="view-grid-heading"><text>视角切片</text><text>用于建模与审核</text></view>
        <view class="view-grid"><view v-for="item in multiviewImages" :key="item.assetId" class="view-card" @tap="previewMultiViewImage(item)"><image v-if="imageUrl(item)" :src="imageUrl(item)" mode="aspectFit" /><view v-else class="view-placeholder"><text>{{ item.label }}</text><text>已保存</text></view><view class="view-label"><text>{{ item.label }}</text><text>查看大图 ›</text></view></view></view>
        <view class="bundle-review-state" :class="`bundle-${multiviewBundleStatus || 'draft'}`">
          <view><text class="bundle-review-label">作品包状态</text><text class="bundle-review-title">{{ multiviewBundleStatusText }}</text></view>
          <text v-if="multiviewBundleNo" class="bundle-review-no">{{ multiviewBundleNo }}</text>
        </view>
        <text v-if="multiviewBundleStatus === 'rejected' && multiviewBundleComment" class="bundle-review-comment">未通过原因：{{ multiviewBundleComment }}</text>
        <button v-if="canSubmitMultiViewReview" class="dark-button full-button" :loading="multiviewBundleSubmitting" @tap="submitMultiViewReview">提交生产模拟图审核 <text>›</text></button>
        <button v-else-if="multiviewBundleStatus === 'review'" class="outline-button full-button" disabled>审核中，请等待平台反馈</button>
        <template v-else-if="multiviewBundleStatus === 'approved'">
          <button class="dark-button full-button" @tap="applyMultiViewProduction">申请打样 <text>›</text></button>
          <button class="outline-button full-button" :loading="busy" @tap="generateModel">继续生成 3D 原型</button>
        </template>
      </view>

      <view v-if="phase === 'model'" id="model-output" class="output-surface">
        <view class="output-header"><view><text class="surface-kicker">3D PROTOTYPE</text><text class="surface-title">{{ modelTaskTitle }}</text></view><view class="model-state" :class="{ done: isModelTaskSucceeded, failed: isModelTaskFailed }">{{ isModelTaskSucceeded ? '完成' : isModelTaskFailed ? '失败' : '处理中' }}</view></view>
        <view class="model-summary"><view class="model-mark">3D</view><view><text>{{ modelTaskDescription }}</text><text>{{ modelTaskDetail }}</text></view></view>
        <view v-if="modelTask" class="model-progress"><view class="progress-row"><text>建模进度</text><text>{{ normalizedModelProgress }}%</text></view><view class="model-progress-track"><view class="model-progress-value" :style="{ width: `${normalizedModelProgress}%` }" /></view></view>
        <text v-if="modelTask?.errorMessage" class="model-error">{{ modelTask.errorMessage }}</text>
        <button v-if="modelTask && !isModelTaskTerminal" class="outline-button full-button" :loading="modelRefreshing" @tap="refreshModelTask">刷新进度</button>
        <button v-if="isModelTaskFailed" class="dark-button full-button" :loading="busy" @tap="generateModel">重新提交 3D 建模</button>
        <button class="dark-button full-button" @tap="goWorks">{{ isModelTaskSucceeded ? '查看已完成的 3D 作品' : '查看我的作品' }}</button>
        <button v-if="isModelTaskSucceeded" class="outline-button full-button" @tap="openCommercial">申请打样 / 商品化</button>
      </view>

      <view id="bottom-anchor" class="bottom-anchor" />
    </scroll-view>

    <view v-if="busy" class="loading-bar"><view class="loading-spinner" aria-hidden="true" /><view><text class="loading-title">之间正在处理</text><text>{{ busyMessage }}</text></view></view>

    <view class="composer-dock">
      <view class="composer-context"><view class="context-live" /><text>{{ chatStageLabel }}</text><text v-if="selectedProduct" class="context-product">· {{ selectedProduct.name }}</text><text v-if="chatSending" class="context-working">处理中</text></view>
      <scroll-view v-if="chatQuickReplies.length" scroll-x class="quick-reply-list" :show-scrollbar="false"><view class="quick-reply-track"><view v-for="item in chatQuickReplies" :key="`${item.type}-${item.value}-${item.label}`" class="quick-reply" :class="{ confirm: item.type === 'confirm_generate', secondary: item.type === 'add_detail', disabled: busy || chatSending || quickReplySubmitting }" :aria-label="item.label" @tap="handleQuickReply(item)"><text class="quick-reply-mark">{{ quickReplyMark(item.type) }}</text><text>{{ item.label }}</text></view></view></scroll-view>
      <view class="chat-input-row"><button class="chat-upload-button" :disabled="busy || chatSending || quickReplySubmitting" aria-label="上传灵感图片" @tap="pickInspirationImage">＋</button><input v-model="chatInput" class="chat-input" maxlength="1200" confirm-type="send" placeholder="描述你的灵感，或直接回答上面的问题" @confirm="submitChatInput" /><button class="chat-send-button" :class="{ ready: chatInput.trim() }" :disabled="!chatInput.trim() || busy || chatSending || quickReplySubmitting" aria-label="发送" @tap="submitChatInput">↑</button></view>
      <view class="composer-footer"><text>AI 生成内容 · 请在商业使用前人工复核</text><text>{{ chatInput.length }}/1200</text></view>
    </view>

    <view class="bottom-actions"><button v-if="canGoPrevious" :disabled="busy || saving || chatSending" @tap="goPreviousStep"><text>‹</text>{{ previousActionLabel }}</button><button @tap="goWorks"><text>▣</text>作品库</button><button class="restart-action" @tap="restart"><text>＋</text>重新开始</button></view>

    <view v-if="policyDialog" class="policy-mask" @tap="resolvePolicyDialog(false)">
      <view class="policy-dialog" @tap.stop>
        <view class="policy-dialog-head"><view><text class="surface-kicker">BEFORE YOU CREATE</text><text>AI生成提示</text></view><text>提交前确认</text></view>
        <text class="policy-dialog-title">{{ activePolicy.title }}</text>
        <scroll-view class="policy-dialog-copy" scroll-y><text>{{ activePolicy.content }}</text></scroll-view>
        <view class="policy-dialog-actions"><button class="policy-cancel" @tap="resolvePolicyDialog(false)">暂不继续</button><button class="policy-confirm" @tap="resolvePolicyDialog(true)">我已阅读并继续</button></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { onHide, onLoad, onUnload } from '@dcloudio/uni-app'
import AiGeneratedNotice from '../../components/AiGeneratedNotice.vue'
import {
  getAssetPreviewAccess,
  uploadReference,
  type ConversationQuickReply,
  type CreatorCampaign,
  type SeedreamMultiViewImage,
  type SeedreamProductionSimulationImage,
} from '../../api/creative'
import { apiUrl, isAuthenticationError } from '../../api/client'
import {
  resolveCreativeProductProfile,
  type CreativeProductLike,
  type CreativeProductProfile,
} from '../../utils/creativeEngineRuntime'
import { requireSession } from '../../utils/session'
import { useImageGeneration } from '../../composables/useImageGeneration'
import { useMultiViewGeneration } from '../../composables/useMultiViewGeneration'
import { useModelGeneration, type ModelTask } from '../../composables/useModelGeneration'
import { useConversationSession } from '../../composables/useConversationSession'
import { useConversationRestoration } from '../../composables/useConversationRestoration'
import { useChatDraft } from '../../composables/useChatDraft'
import { useConversationChat } from '../../composables/useConversationChat'
import { useCreativePolicy } from '../../composables/useCreativePolicy'
import {
  catalogSpecificationHint,
  localRecommendedProductSize,
  type MaterialOption,
  type ProductOption,
  useProductCatalog,
} from '../../composables/useProductCatalog'

type Phase = 'mode' | 'product' | 'inspiration' | 'image' | 'material' | 'size' | 'result' | 'multiview' | 'model'
type Mode = 'template' | 'text' | 'image'
type EditableBriefField = 'product' | 'inspiration' | 'material' | 'size'
interface Message {
  id: number
  role: 'assistant' | 'user'
  text: string
  imageUrl?: string
  imageAssetId?: number
  imageState?: 'uploading' | 'ready' | 'failed'
}
type CampaignContext = CreatorCampaign & { sessionId?: number }

function sharedProductFormProfile(product: ProductOption | null): CreativeProductProfile {
  return resolveCreativeProductProfile({
    product: product as CreativeProductLike | null,
    productKey: product?.key,
    productCategory: product?.categoryName || product?.categoryKey,
    productType: product?.name,
    material: material.value,
    productSize: productSize.value,
  })
}

const modeOptions = [
  { key: 'template' as Mode, mark: '例', title: '没有灵感（看看示例）', desc: '浏览示例并了解创作方式' },
  { key: 'text' as Mode, mark: '字', title: '已有灵感（文字）', desc: '把你的想法、故事或需求告诉我' },
  { key: 'image' as Mode, mark: '图', title: '已有灵感（图片）', desc: '上传草图、照片或有权使用的参考图' },
]
const phase = ref<Phase>('mode')
const mode = ref<Mode | ''>('')
const selectedProduct = ref<ProductOption | null>(null)
const material = ref('')
const materialChoice = ref<'recommend' | string>('recommend')
const productSize = ref('')
const productSizeRecommended = ref(false)
const inspirationText = ref('')
const referencePath = ref('')
const referenceAssetId = ref<number | null>(null)
const sessionId = ref<number | null>(null)
const projectId = ref<number | null>(null)
const versionId = ref<number | null>(null)
const generatedAssetId = ref<number | null>(null)
const pendingImageJobId = ref<number | null>(null)
const pendingGenerationPrompt = ref('')
const pendingMultiViewJobId = ref<number | null>(null)
const pendingMultiViewInputAssetId = ref<number | null>(null)
const pendingMultiViewPrompt = ref('')
const previewUrl = ref('')
const referenceAnalysis = ref('')
const multiviewImages = ref<SeedreamMultiViewImage[]>([])
const simulationAssetId = ref<number | null>(null)
const simulationImage = ref<SeedreamProductionSimulationImage | null>(null)
const multiviewBundleId = ref<number | null>(null)
const multiviewBundleNo = ref('')
const multiviewBundleStatus = ref('')
const multiviewBundleComment = ref('')
const multiviewBundleSubmitting = ref(false)
const modelInputMode = ref<'single' | 'multiview'>('single')
const refiningImage = ref(false)
const refinementNote = ref('')
const modelTask = ref<ModelTask | null>(null)
const messages = ref<Message[]>([])
const busy = ref(false)
const busyMessage = ref('正在保存创作过程并调用 AI，请稍候…')
const saving = ref(false)
const sessionReady = ref(false)
const scrollIntoView = ref('bottom-anchor')
let messageId = 0
const forceNewSession = ref(false)
const chatExperience = true
const chatInput = ref('')
const chatQuickReplies = ref<ConversationQuickReply[]>([])
const chatSending = ref(false)
const quickReplySubmitting = ref(false)
const chatThinking = ref(false)
const imageGenerationStage = ref<'adapting_product' | ''>('')
const thinkingLabel = ref('正在理解你的想法')
const awaitingGenerationConfirmation = ref(false)
const chatStage = ref('need_product')
const autoGenerationInFlight = ref(false)
const modelRefreshing = ref(false)
const campaignContext = ref<CampaignContext | null>(null)
const campaignAttached = ref(false)
const productCatalog = useProductCatalog({
  onError: (error: any) => uni.showToast({ title: error?.message || '选品目录暂不可用，请稍后重试', icon: 'none' }),
})
const {
  productOptions,
  productKeyword,
  productCategory,
  catalogLoading,
  productCatalogCategories,
  filteredProductOptions,
  loadProductCatalog,
  updateProductKeyword,
  setProductCategory,
  productCountForCategory,
  productByValue,
} = productCatalog
const chatDraft = useChatDraft({ sessionId, chatInput })
const {
  persist: persistChatDraft,
  schedule: scheduleChatDraftSave,
  restore: restoreChatDraft,
  clear: clearChatDraft,
} = chatDraft

const previousEditTarget = computed<EditableBriefField | null>(() => {
  if (phase.value === 'result') return selectedProduct.value ? 'size' : null
  if (phase.value === 'multiview' || phase.value === 'model') return null
  if (!selectedProduct.value || chatStage.value === 'need_product') return null
  if (chatStage.value === 'need_inspiration') return 'product'
  if (chatStage.value === 'need_material') return 'inspiration'
  if (chatStage.value === 'need_size') return 'material'
  if (['confirm_before_image', 'need_additional_detail', 'ready_for_image', 'image_ready'].includes(chatStage.value)) return 'size'
  if (productSize.value) return 'size'
  if (material.value) return 'material'
  if (inspirationText.value || referenceAssetId.value) return 'inspiration'
  return 'product'
})
const previousActionLabel = computed(() => {
  if (phase.value === 'model') return multiviewImages.value.length >= 3 ? '返回生产模拟图' : '返回产品图'
  if (phase.value === 'multiview') return '返回产品图'
  return ({ product: '修改产品', inspiration: '修改灵感', material: '修改材质', size: '修改尺寸' } as Record<EditableBriefField, string>)[previousEditTarget.value || 'product']
})
const canGoPrevious = computed(() => !busy.value && !saving.value && !chatSending.value
  && (phase.value === 'multiview' || phase.value === 'model' || Boolean(previousEditTarget.value)))

const currentMaterials = computed(() => selectedProduct.value?.materials || [])
const isFoodProduct = computed(() => selectedProduct.value?.categoryKey === 'food'
  || /食品|食用|曲奇|饼干|糕点|月饼|咖啡|饮品|茶|巧克力|糖果/.test(`${selectedProduct.value?.name || ''} ${material.value}`))
const prompt = computed(() => {
  const product = selectedProduct.value?.name || '文创产品'
  const source = inspirationText.value.trim() || `为${product}设计一套具有文化辨识度、适合量产打样的产品视觉`
  const campaignDirection = campaignContext.value
    ? `本作品参加平台优先征集「${campaignContext.value.title}」，面向${campaignContext.value.targetName}候选渠道；请重点遵循：${campaignContext.value.promptHint}。`
    : ''
  const size = productSize.value || '待确认'
  const sizeSource = productSizeRecommended.value ? '（已按推荐规格确定）' : ''
  const catalogHint = productSizeRecommended.value
    ? catalogSpecificationHint(selectedProduct.value, productSize.value)
    : ''
  const catalogSpecification = catalogHint
    ? `目录推荐规格参考（仅保留容量/件数信息，不覆盖已选成品规格）：${catalogHint}。`
    : ''
  // This is the user's creative brief only. The shared creative engine adds
  // product-form, material, size and reference-image rules exactly once when
  // the request is submitted.
  return `${source}。产品：${product}；材质：${material.value}；成品尺寸：${size}${sizeSource}。${catalogSpecification}视觉气质与配色只依据用户灵感和产品形态协调，不强行套用固定风格或用途。${campaignDirection}`
})
const multiviewBundleStatusText = computed(() => ({
  draft: '待提交审核',
  review: '生产模拟图审核中',
  approved: '审核已通过',
  rejected: '审核未通过',
}[multiviewBundleStatus.value] || '待创建审核包'))
const simulationImageUrl = computed(() => imageUrl(simulationImage.value))
const canSubmitMultiViewReview = computed(() => ['draft', 'rejected'].includes(multiviewBundleStatus.value))
const chatStageLabel = computed(() => ({
  need_product: '先告诉我想做什么产品',
  need_inspiration: '再说说你的灵感，或上传参考图',
  need_material: '最后确认材质，不确定可以让我推荐',
  need_size: '再确认成品尺寸，不确定可以按推荐规格',
  understanding: '我正在整理你的创作方向',
  confirm_before_image: '生成前确认一下，还有需要补充的吗？',
  need_additional_detail: '请补充你想保留、加强或避免的内容',
  ready_for_image: '信息已足够，准备生成产品图',
  template_unavailable: '没有灵感示例功能正在开发中',
  image_ready: '产品图已完成，可以继续落地',
  multiview_ready: '生产模拟图已完成，请先整包提交审核',
  multiview_review: '生产模拟图正在人工审核',
  multiview_approved: '生产模拟图审核已通过，可以申请打样',
  multiview_rejected: '生产模拟图未通过，可根据原因修改后重提',
  model_running: '3D 原型正在生成',
  model_ready: '3D 原型已完成，可以申请打样',
}[chatStage.value] || '告诉我你的创作想法'))
function quickReplyMark(type: string) {
  return ({
    category: '类',
    product: '选',
    material: '材',
    size: '尺',
    upload: '图',
    text: '写',
    template: '例',
    confirm_generate: '出',
    add_detail: '改',
    multiview: '观',
    bundle_review: '审',
    bundle_production: '样',
    model: '3D',
    refine: '改',
    commercial: '样',
    works: '作',
  } as Record<string, string>)[type] || '→'
}

function addMessage(role: Message['role'], text: string) {
  const id = ++messageId
  messages.value.push({ id, role, text })
  void nextTick(() => { scrollIntoView.value = `message-${id}` })
  return id
}
function addImageMessage(imageUrl: string, text = '已上传灵感图片', imageState: Message['imageState'] = 'ready', imageAssetId?: number) {
  const id = ++messageId
  messages.value.push({ id, role: 'user', text, imageUrl, imageAssetId, imageState })
  void nextTick(() => { scrollIntoView.value = `message-${id}` })
  return id
}
function updateImageMessage(id: number, values: Partial<Pick<Message, 'text' | 'imageUrl' | 'imageAssetId' | 'imageState'>>) {
  const message = messages.value.find(item => item.id === id)
  if (message) Object.assign(message, values)
}
function addAssistantMessage(text: string) {
  const value = text.trim()
  if (!value) return null
  const recentAssistant = [...messages.value].reverse().find(item => item.role === 'assistant')
  // Keep a broken planner from filling the transcript with the same template
  // while the persisted brief still advances locally.
  if (recentAssistant?.text === value) return null
  return addMessage('assistant', value)
}
function addRestoredMessage(role: Message['role'], text: string) {
  const value = text.trim()
  if (!value) return null
  const isSizeQuestion = role === 'assistant' && /这件产品想做多大[？?]/.test(value)
  const isRecommendedSizeReply = role === 'user' && value === '按推荐规格'
  // Old sessions can contain several copies of the same size turn from the
  // previous client. Once a size is already in the brief, those questions are
  // stale and should not be shown again when the transcript is restored.
  if (isSizeQuestion && productSize.value) return null
  if (isRecommendedSizeReply && messages.value.some(item => item.role === 'user' && item.text === value)) return null
  if (messages.value.some(item => item.role === role && item.text === value)) return null
  return addMessage(role, value)
}
function addRestoredImageMessage(assetId: number, text = '已上传灵感图片') {
  if (!Number.isFinite(assetId) || assetId <= 0) return null
  if (messages.value.some(item => item.imageAssetId === assetId)) return null
  const id = ++messageId
  messages.value.push({ id, role: 'user', text, imageAssetId: assetId, imageState: 'ready' })
  return id
}
async function scrollToSection(id: 'result-output' | 'multiview-output' | 'model-output' | 'bottom-anchor') {
  scrollIntoView.value = ''
  await nextTick()
  scrollIntoView.value = id
}
function setChatThinking(active: boolean, label = '正在理解你的想法') {
  chatThinking.value = active
  if (active) {
    thinkingLabel.value = label
    void nextTick(() => { if (chatThinking.value) scrollIntoView.value = 'chat-thinking' })
  }
}
function setGenerationConfirmationReplies() {
  chatQuickReplies.value = [
    { label: '没有补充，开始生成', type: 'confirm_generate', value: 'confirm' },
    { label: '我还要补充', type: 'add_detail', value: '' },
  ]
}
function hasCompleteLocalGenerationBrief() {
  if (!selectedProduct.value || !material.value || !productSize.value) return false
  if (isReferenceImageMode()) return Boolean(referenceAssetId.value)
  if (!mode.value) return false
  return mode.value === 'text' && Boolean(inspirationText.value.trim())
}

function hasReferenceImage() {
  const assetId = Number(referenceAssetId.value)
  return Number.isFinite(assetId) && assetId > 0
}

/**
 * A saved reference image is the authoritative source for image generation.
 * Chat planning can return a stale text mode, so every generation decision
 * uses this derived state instead of trusting the planner's mode field alone.
 */
function isReferenceImageMode() {
  return mode.value === 'image' || hasReferenceImage()
}

function activateReferenceImageMode() {
  mode.value = 'image'
  // The uploaded image is the primary source, but text already entered remains
  // a useful supplement. Clearing it here made the same image produce a
  // different brief depending on when the upload happened.
}

function preserveReferenceImageMode() {
  if (!hasReferenceImage()) return false
  mode.value = 'image'
  return true
}
function setInitialChatReplies() {
  if (productOptions.value.length) {
    const seen = new Set<string>()
    const categories = productOptions.value
      .filter(item => seen.has(item.categoryKey) ? false : (seen.add(item.categoryKey), true))
      .slice(0, 7)
      .map(item => ({ label: item.categoryName, type: 'category', value: item.categoryKey }))
    chatQuickReplies.value = [
      ...categories,
      { label: '没有灵感（看看示例）', type: 'template', value: '' },
    ]
  } else {
    chatQuickReplies.value = [
      { label: '我有一个想法', type: 'text', value: '' },
      { label: '上传灵感图片', type: 'upload', value: '' },
      { label: '没有灵感（看看示例）', type: 'template', value: '' },
    ]
  }
}

function applyChatBrief(brief: Record<string, any> | undefined, preserveExisting = false, preserveRecommendedSize = false) {
  if (!brief) return
  const previousSize = productSize.value
  const previousSizeWasRecommended = productSizeRecommended.value
  const localReferenceAssetId = hasReferenceImage() ? Number(referenceAssetId.value) : null
  const resolvedReferenceAssetId = Number(brief.referenceAssetId) > 0 ? Number(brief.referenceAssetId) : null
  const product = productByValue(brief.productName, brief.productKey)
  const localProduct = selectedProduct.value
  const localMaterial = material.value
  const localProductSize = productSize.value
  const briefMatchesLocalProduct = !localProduct || !product || product.key === localProduct.key
  // Once an uploaded asset exists, the local brief is authoritative for
  // fields the planner did not return. This keeps product/material/size stable
  // across the early-upload and prompted-upload paths.
  if (product && (!localReferenceAssetId || !localProduct)) selectedProduct.value = product
  else if (!preserveExisting && !localReferenceAssetId) selectedProduct.value = null
  const resolvedMode = ['template', 'text', 'image'].includes(String(brief.mode || '')) ? String(brief.mode) as Mode : ''
  if (!localReferenceAssetId && (resolvedMode || !preserveExisting)) mode.value = resolvedMode
  if (brief.inspiration && brief.inspirationSource !== 'image') {
    const nextInspiration = String(brief.inspiration).trim()
    // Do not persist planner acknowledgements as user creative direction.
    // They otherwise become an order-dependent supplement after an upload.
    if (!/^(?:没有(?:具体)?灵感(?:（?看看示例）?)?|无(?:具体)?灵感|没有补充|无补充|不用补充|我已上传(?:一张)?(?:灵感)?图片|已上传(?:一张)?灵感图片|上传灵感图片)[。.!！?？\s]*$/i.test(nextInspiration)) {
      inspirationText.value = nextInspiration
    }
  }
  else if (!preserveExisting && !localReferenceAssetId && !resolvedReferenceAssetId) inspirationText.value = ''
  if (localReferenceAssetId) referenceAssetId.value = localReferenceAssetId
  else if (resolvedReferenceAssetId || !preserveExisting) referenceAssetId.value = resolvedReferenceAssetId
  // Never let a planner response downgrade an uploaded reference into text
  // generation or discard the asset ID that was just uploaded locally.
  preserveReferenceImageMode()
  if (!referenceAssetId.value) referencePath.value = ''
  if (brief.material && briefMatchesLocalProduct && (!localReferenceAssetId || !localMaterial)) {
    material.value = String(brief.material)
    materialChoice.value = brief.materialRecommended ? 'recommend' : material.value
  } else if (!preserveExisting && !localReferenceAssetId) {
    material.value = ''
    materialChoice.value = 'recommend'
  }
  if (brief.productSize && briefMatchesLocalProduct && (!localReferenceAssetId || !localProductSize)) {
    const resolvedSize = String(brief.productSize)
    productSize.value = resolvedSize
    productSizeRecommended.value = Boolean(brief.sizeRecommended)
      || (preserveRecommendedSize && previousSizeWasRecommended && resolvedSize === previousSize)
  } else if (!preserveExisting && !preserveRecommendedSize && !localReferenceAssetId) {
    productSize.value = ''
    productSizeRecommended.value = false
  }
}

async function chooseRecommendedSizeLocally(label = '按推荐规格') {
  if (!selectedProduct.value) {
    uni.showToast({ title: '请先选择产品，再推荐成品规格', icon: 'none' })
    return
  }
  const recommended = localRecommendedProductSize(selectedProduct.value)
  productSize.value = recommended
  productSizeRecommended.value = true
  addMessage('user', label)
  await saveCreativeEventBestEffort('size', 'size_selected', {
    productKey: selectedProduct.value.key,
    productType: selectedProduct.value.name,
    productSize: recommended,
    recommended: true,
    source: 'miniapp_catalog',
  })
  chatStage.value = 'confirm_before_image'
  awaitingGenerationConfirmation.value = true
  setGenerationConfirmationReplies()
  addAssistantMessage(`根据${selectedProduct.value.name}的常用打样规格，我推荐 ${recommended}，已为你设置并写入生成提示词。生成前还有需要补充的吗？`)
  phase.value = 'size'
}

async function goPreviousStep() {
  if (!canGoPrevious.value) return
  const from = phase.value
  if (from === 'multiview' || from === 'model') {
    const to = previousPhase(from)
    if (!to) return
    phase.value = to
    addMessage('assistant', '已返回上一步，现有作品和生成记录不会删除。')
    await saveEvent('navigation', 'previous_step', { from, to })
    await scrollToSection(to === 'multiview' ? 'multiview-output' : 'result-output')
    return
  }
  const target = previousEditTarget.value
  if (!target) return
  const label = ({ product: '修改产品', inspiration: '修改灵感', material: '修改材质', size: '修改尺寸' } as Record<EditableBriefField, string>)[target]
  const edited = await sendChatTurn('', { type: 'edit', value: target, label })
  if (!edited) return
  if (generatedAssetId.value) clearGeneratedOutputForNewDirection()
  phase.value = 'mode'
  await saveEvent('navigation', 'previous_step', { from, to: target })
}

function previousPhase(current: Phase): Phase | null {
  const transitions: Partial<Record<Phase, Phase>> = {
    product: 'mode',
    inspiration: 'product',
    image: 'product',
    material: mode.value === 'image' ? 'image' : 'inspiration',
    result: 'material',
    multiview: 'result',
    model: multiviewImages.value.length >= 3 ? 'multiview' : 'result',
  }
  return transitions[current] || null
}
function goWorks() { uni.navigateTo({ url: '/pages/works/index' }) }
function openCommercial() {
  if (phase.value === 'result') {
    uni.showToast({ title: '请先生成生产模拟图或 3D 原型', icon: 'none' })
    return
  }
  if (phase.value === 'model' && !isModelTaskSucceeded.value) {
    uni.showToast({ title: '请等待 3D 原型生成完成', icon: 'none' })
    return
  }
  const params: string[] = []
  const commercialAssetId = phase.value === 'model' && isModelTaskSucceeded.value ? modelTask.value?.assetId : generatedAssetId.value
  if (!commercialAssetId) {
    uni.showToast({ title: '3D 原型尚未保存完成，请稍后再试', icon: 'none' })
    return
  }
  if (commercialAssetId) params.push('assetId=' + encodeURIComponent(String(commercialAssetId)))
  if (selectedProduct.value?.key) params.push(`productKey=${encodeURIComponent(selectedProduct.value.key)}`)
  if (selectedProduct.value?.name) params.push(`productName=${encodeURIComponent(selectedProduct.value.name)}`)
  if (material.value) params.push(`material=${encodeURIComponent(material.value)}`)
  if (productSize.value) params.push(`productSize=${encodeURIComponent(productSize.value)}`)
  const query = params.join('&')
  uni.navigateTo({ url: `/pages/commercial/index${query ? `?${query}` : ''}` })
}
function selectedModeTitle() { return modeOptions.find(item => item.key === mode.value)?.title || '' }
function showTemplateDeveloping() {
  uni.showModal({
    title: '功能开发中',
    content: '没有灵感示例功能正在开发，敬请期待。你也可以先使用文字或图片灵感开始创作。',
    showCancel: false,
  })
}

function isNotFound(error: any) { return Number(error?.statusCode) === 404 || /not found|不存在|找不到/i.test(String(error?.message || '')) }

function campaignFromStorage(): CampaignContext | null {
  const context = uni.getStorageSync('creation_context') || {}
  const value = context?.campaign
  if (!value || typeof value !== 'object' || typeof value.key !== 'string' || typeof value.channelCode !== 'string') return null
  return value as CampaignContext
}

function bindCampaignSession() {
  if (!campaignContext.value || !sessionId.value || campaignContext.value.sessionId === sessionId.value) return
  const context = uni.getStorageSync('creation_context') || {}
  campaignContext.value = { ...campaignContext.value, sessionId: sessionId.value }
  uni.setStorageSync('creation_context', { ...context, campaign: campaignContext.value })
}

async function attachCampaignToConversation() {
  const campaign = campaignContext.value
  if (!campaign || campaignAttached.value) return
  campaignAttached.value = true
  bindCampaignSession()
  addMessage('assistant', `已带入「${campaign.title}」。我会把${campaign.collectionStyle}和${campaign.recommendedProducts.join('、')}方向带进后续生成；作品提交审核通过后，${campaign.rewardAmount} 积分会自动到账。`)
  await saveEvent('campaign', 'campaign_selected', {
    campaignKey: campaign.key,
    campaignTitle: campaign.title,
    channelCode: campaign.channelCode,
    targetName: campaign.targetName,
    rewardAmount: campaign.rewardAmount,
  })
}

function clearGeneratedOutputForNewDirection() {
  clearModelGeneration()
  generatedAssetId.value = null
  pendingImageJobId.value = null
  pendingGenerationPrompt.value = ''
  pendingMultiViewJobId.value = null
  pendingMultiViewInputAssetId.value = null
  pendingMultiViewPrompt.value = ''
  previewUrl.value = ''
  referenceAnalysis.value = ''
  multiviewImages.value = []
  simulationAssetId.value = null
  simulationImage.value = null
  multiviewBundleId.value = null
  multiviewBundleNo.value = ''
  multiviewBundleStatus.value = ''
  multiviewBundleComment.value = ''
  refiningImage.value = false
  refinementNote.value = ''
}

// Assigned after the session composable exposes saveEvent. Keeping a no-op
// until then lets session restoration use resetViewState during initialization.
let resetCreativePolicy: () => void = () => {}

function editableTarget(value: unknown): EditableBriefField | null {
  const target = String(value || '')
  return ['product', 'inspiration', 'material', 'size'].includes(target) ? target as EditableBriefField : null
}

async function freshAssetPreview(assetId: number) {
  if (!Number.isFinite(assetId) || assetId <= 0) return ''
  try { return imageUrl(await getAssetPreviewAccess(assetId)) } catch { return '' }
}

async function refreshRestoredPreviews() {
  if (generatedAssetId.value) {
    const fresh = await freshAssetPreview(generatedAssetId.value)
    if (fresh) previewUrl.value = fresh
  }
  if (multiviewImages.value.length) {
    multiviewImages.value = await Promise.all(multiviewImages.value.map(async item => {
      const fresh = await freshAssetPreview(Number(item.assetId))
      return fresh ? { ...item, previewUrl: fresh } : item
    }))
  }
  if (simulationImage.value || simulationAssetId.value) {
    const current = simulationImage.value || { assetId: simulationAssetId.value as number, label: '生产模拟图' }
    const fresh = await freshAssetPreview(Number(current.assetId))
    if (fresh) simulationImage.value = { ...current, previewUrl: fresh }
  }
  const imageMessages = messages.value.filter(item => item.imageAssetId && !item.imageUrl)
  await Promise.all(imageMessages.map(async item => {
    const fresh = await freshAssetPreview(Number(item.imageAssetId))
    if (fresh) updateImageMessage(item.id, { imageUrl: fresh, imageState: 'ready' })
  }))
}

function resetViewState() {
  clearModelGeneration()
  phase.value = 'mode'
  mode.value = ''
  selectedProduct.value = null
  material.value = ''
  materialChoice.value = 'recommend'
  productSize.value = ''
  productSizeRecommended.value = false
  inspirationText.value = ''
  referencePath.value = ''
  referenceAssetId.value = null
  generatedAssetId.value = null
  pendingImageJobId.value = null
  pendingGenerationPrompt.value = ''
  pendingMultiViewJobId.value = null
  pendingMultiViewInputAssetId.value = null
  pendingMultiViewPrompt.value = ''
  previewUrl.value = ''
  referenceAnalysis.value = ''
  multiviewImages.value = []
  simulationAssetId.value = null
  simulationImage.value = null
  multiviewBundleId.value = null
  multiviewBundleNo.value = ''
  multiviewBundleStatus.value = ''
  multiviewBundleComment.value = ''
  refiningImage.value = false
  refinementNote.value = ''
  resetCreativePolicy()
  messages.value = []
  messageId = 0
  chatQuickReplies.value = []
  chatStage.value = 'need_product'
  chatInput.value = ''
  awaitingGenerationConfirmation.value = false
  campaignAttached.value = false
  setChatThinking(false)
  autoGenerationInFlight.value = false
}

const conversationRestoration = useConversationRestoration({
  phase,
  mode,
  selectedProduct,
  material,
  materialChoice,
  productSize,
  productSizeRecommended,
  inspirationText,
  referenceAssetId,
  generatedAssetId,
  pendingImageJobId,
  pendingGenerationPrompt,
  pendingMultiViewJobId,
  pendingMultiViewInputAssetId,
  pendingMultiViewPrompt,
  previewUrl,
  referenceAnalysis,
  multiviewImages,
  simulationAssetId,
  simulationImage,
  multiviewBundleId,
  multiviewBundleNo,
  multiviewBundleStatus,
  multiviewBundleComment,
  campaignAttached,
  modelInputMode,
  refinementNote,
  chatQuickReplies,
  chatStage,
  awaitingGenerationConfirmation,
  modeOptions,
  productByValue,
  imageUrl: item => imageUrl(item),
  setModelTask: payload => setModelTask(payload),
  applyChatBrief,
  clearGeneratedOutputForNewDirection,
  editableTarget,
  resetTranscript: () => { messages.value = []; messageId = 0 },
  addInitialMessage: () => { addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。') },
  addRestoredMessage,
  addRestoredImageMessage,
  setGenerationConfirmationReplies,
})
const {
  restoreEvent,
  restoreMessages,
  restorePhase,
} = conversationRestoration

const conversationSession = useConversationSession({
  sessionId,
  sessionReady,
  saving,
  forceNewSession,
  onSessionLoaded: session => {
    projectId.value = Number(session.projectId) > 0 ? Number(session.projectId) : null
    versionId.value = Number(session.versionId) > 0 ? Number(session.versionId) : null
  },
  campaignSessionId: () => Number(campaignContext.value?.sessionId) || 0,
  requireSession: () => Boolean(requireSession()),
  isNotFound,
  resetViewState,
  restoreEvent,
  restoreMessages,
  restorePhase,
  refreshRestoredPreviews,
  onCreateError: (error: any) => {
    uni.showToast({
      title: isNotFound(error) ? '创作服务暂时不可用，请稍后再试' : (error?.message || '无法建立创作会话'),
      icon: 'none',
    })
  },
})
const {
  ensureSession,
  saveEvent,
} = conversationSession

const creativePolicy = useCreativePolicy({ saveEvent })
const {
  policyDialog,
  activePolicy,
  resolvePolicyDialog,
  ensureReferencePolicy,
  ensureAiPolicyForImage,
  ensureThreeDimensionalPolicy,
  reset: resetCreativePolicyState,
} = creativePolicy
resetCreativePolicy = resetCreativePolicyState

async function chooseMode(value: Mode) {
  if (busy.value) return
  if (value === 'template') {
    showTemplateDeveloping()
    return
  }
  mode.value = value
  // Image mode keeps any text already entered as an optional supplement;
  // upload order must not change the final reference-image brief.
  if (value !== 'image') {
    // Choosing a new non-image source starts a new direction. Do not let an
    // earlier reference image silently force this turn back to image-to-image.
    referencePath.value = ''
    referenceAssetId.value = null
  }
  addMessage('user', selectedModeTitle())
  addMessage('assistant', '好，我们先确定产品方向。你想把它做成什么？')
  await saveEvent('mode', 'mode_selected', { mode: value, modeName: selectedModeTitle() })
  phase.value = 'product'
}
async function chooseProduct(value: ProductOption) {
  selectedProduct.value = value
  material.value = ''
  materialChoice.value = 'recommend'
  productSize.value = ''
  productSizeRecommended.value = false
  addMessage('user', value.name)
  await saveEvent('product', 'product_selected', { productKey: value.key, productType: value.name, process: value.process })
  if (mode.value === 'template') {
    addMessage('assistant', `${value.name}很适合先做一版。现在选材质，我会把工艺约束一起带进提示词。`)
    phase.value = 'material'
  } else if (mode.value === 'text') {
    addMessage('assistant', '收到。把你已有的文字灵感告诉我，不用写成复杂提示词。')
    phase.value = 'inspiration'
  } else if (hasReferenceImage()) {
    addMessage('assistant', '图片已收到。现在确认材质，我会继续保留图片主体并把产品规格写入生成提示词。')
    phase.value = 'material'
  } else {
    addMessage('assistant', '收到。请上传一张你有权使用的灵感图片，我会保留主体并优化成产品视觉。')
    phase.value = 'image'
  }
}
async function submitTextInspiration() {
  if (!inspirationText.value.trim()) return
  addMessage('user', inspirationText.value.trim())
  await saveEvent('inspiration', 'text_inspiration_submitted', { productType: selectedProduct.value?.name, inspirationText: inspirationText.value.trim() })
  addMessage('assistant', '灵感已记录。请再确认材质，随后我会询问成品尺寸。')
  phase.value = 'material'
}
async function pickInspirationImage() {
  if (busy.value) {
    uni.showToast({ title: '图片正在上传或生成中，请稍候', icon: 'none' })
    return
  }
  if (!(await ensureReferencePolicy())) return
  // Establish the conversation before choosing the file. Otherwise a fast
  // upload can finish while session restoration is still resetting the page,
  // which makes the same image follow a different brief on the next step.
  if (!(await ensureSession())) return
  // chooseImage is the protected API itself. Calling it directly lets WeChat
  // invoke the app-level privacy resolver and then resume this exact action.
  // A separate requirePrivacyAuthorize call can consume the tap without
  // opening the album on some base-library versions.
  // Keep the original pixels; the server creates a bounded generation copy
  // after upload, so WeChat-side compression does not erase small markings.
  uni.chooseImage({ count: 1, sizeType: ['original'], sourceType: ['album'], success: (result) => {
    const path = result.tempFilePaths?.[0]
    if (!path) {
      uni.showToast({ title: '没有读取到图片，请重新选择', icon: 'none' })
      return
    }
    activateReferenceImageMode()
    referencePath.value = path
    referenceAssetId.value = null
    void uploadInspirationImage(path)
  }, fail: (error: any) => {
    const message = String(error?.errMsg || '')
    if (/cancel/i.test(message)) return
    const hint = /privacy/i.test(message)
      ? '请先同意小程序隐私保护指引后重试'
      : /auth|permission|deny/i.test(message)
        ? '微信没有读取相册的权限，请在系统设置中检查微信的照片权限'
        : '微信未能选取这张图片'
    uni.showModal({ title: '选择图片失败', content: `${hint}\n\n微信返回：${message || '未提供错误信息'}`, showCancel: false })
  } })
}
async function uploadInspirationImage(path: string) {
  const imageMessageId = addImageMessage(path, '正在上传灵感图片…', 'uploading')
  busy.value = true
  try {
    const result = await uploadReference(path, projectId.value, versionId.value)
    const id = Number(result?.assetId)
    if (!Number.isFinite(id) || id <= 0) throw new Error('图片上传成功但没有返回作品编号')
    activateReferenceImageMode()
    referenceAssetId.value = id
    // Show the local image immediately. Replace it with the server-controlled
    // preview in the background so a slow media-token request cannot stall the
    // conversation turn.
    updateImageMessage(imageMessageId, { text: '已上传灵感图片', imageUrl: path, imageAssetId: id, imageState: 'ready' })
    void freshAssetPreview(id).then(storedPreview => {
      if (storedPreview) updateImageMessage(imageMessageId, { imageUrl: storedPreview, imageState: 'ready' })
    })
    await saveCreativeEventBestEffort('inspiration', 'image_inspiration_uploaded', {
      productType: selectedProduct.value?.name,
      inputAssetId: id,
      fileType: 'image',
      // Persist the optional text supplement with the upload event so session
      // restoration has the same brief regardless of upload order.
      inspirationText: inspirationText.value.trim(),
    })
    uni.showToast({ title: '图片已留存', icon: 'success' })
    // Keep the interaction lock through the synthetic image chat turn. If we
    // release it here, a user can select the product/material while this turn
    // is still writing the server brief; the two requests then race and the
    // same uploaded image can receive different product context.
    await sendChatTurn('我已上传灵感图片', { type: 'image', value: String(id), label: '已上传灵感图片' }, { skipUserMessage: true })
  } catch (error: any) {
    updateImageMessage(imageMessageId, { imageState: 'failed', text: '这张灵感图片上传失败' })
    const message = error?.message || '图片上传失败'
    // Toast 文案长度有限，网络上传错误改用弹窗，避免关键的微信错误被截断。
    uni.showModal({ title: '图片上传失败', content: message, showCancel: false })
  }
  finally { busy.value = false }
}
async function submitImageInspiration() {
  if (!referenceAssetId.value) return
  addMessage('user', '已上传一张灵感图片')
  await saveEvent('inspiration', 'image_inspiration_confirmed', { productType: selectedProduct.value?.name, inputAssetId: referenceAssetId.value })
  addMessage('assistant', '图片已收到。你希望它用什么材质？')
  phase.value = 'material'
}
async function chooseMaterial(value: MaterialOption) {
  material.value = value.name
  materialChoice.value = value.name
  addMessage('user', value.name)
  await saveEvent('material', 'material_selected', { productType: selectedProduct.value?.name, material: value.name, materialNote: value.note })
  await generateImageAfterMaterialSelection()
}
function recommendedMaterial() {
  return currentMaterials.value[0] || null
}
async function chooseRecommendedMaterial() {
  const recommendation = recommendedMaterial()
  if (!recommendation) {
    uni.showToast({ title: '暂时无法推荐材质，请手动选择', icon: 'none' })
    return
  }
  material.value = recommendation.name
  materialChoice.value = 'recommend'
  addMessage('user', '你帮我推荐材质')
  await saveEvent('material', 'material_selected', { productType: selectedProduct.value?.name, material: recommendation.name, materialNote: recommendation.note, recommended: true })
  addMessage('assistant', `根据${selectedProduct.value?.name || '当前产品'}的结构和工艺，我推荐${recommendation.name}。${recommendation.note}`)
  await generateImageAfterMaterialSelection()
}

async function generateImageAfterMaterialSelection() {
  if (!productSize.value) {
    addMessage('assistant', '材质已确认。这件产品想做多大？例如 60×60×3mm、直径 80mm 或 A5；不确定时可以按推荐规格。')
    phase.value = 'size'
    return
  }
  addMessage('assistant', '材质和尺寸已确认，现在生成产品图。')
  await generateProductImage()
}

async function saveCreativeEventBestEffort(step: string, eventType: string, payload: Record<string, any> = {}) {
  if (!sessionId.value) return
  try {
    await saveEvent(step, eventType, payload)
  } catch (error) {
    console.warn('[conversation-create] event persistence skipped', { step, eventType, error })
  }
}

async function completeGeneratedProductImage(result: any, generationPrompt: string) {
  if (!selectedProduct.value) throw new Error('当前产品信息已失效，请重新选择产品')
  const assetId = Number(result?.assetId || result?.id)
  if (!Number.isFinite(assetId) || assetId <= 0) throw new Error('产品图没有保存成功，请重新生成')
  pendingImageJobId.value = null
  pendingGenerationPrompt.value = ''
  generatedAssetId.value = assetId
  previewUrl.value = imageUrl(result) || await freshAssetPreview(assetId)
  referenceAnalysis.value = String(result?.referenceAnalysis || '')
  let productForm = selectedProduct.value.key || 'general'
  try {
    productForm = sharedProductFormProfile(selectedProduct.value).key || productForm
  } catch (error) {
    // Event metadata must never turn a successfully saved image into a
    // misleading generation failure. Keep the catalog key as a safe fallback.
    console.warn('[conversation-create] product profile metadata fallback', error)
  }
  await saveCreativeEventBestEffort('image', 'image_generated', { jobId: result?.jobId, productType: selectedProduct.value.name, material: material.value, productSize: productSize.value, prompt: generationPrompt, sourcePrompt: prompt.value, generatedAssetId: generatedAssetId.value, previewUrl: previewUrl.value, mode: mode.value, referenceAssetId: referenceAssetId.value, inspirationText: inspirationText.value, referenceStrategy: isReferenceImageMode() ? 'direct_single_pass' : 'text_to_image', productForm, referenceAnalysis: result?.referenceAnalysis || '', referenceAnalysisSource: result?.referenceAnalysisSource || '' })
  addMessage('assistant', '产品视觉已经生成并保存。下一步请生成生产模拟图或 3D 原型，完成后才能提交审核和申请打样。')
  chatStage.value = 'image_ready'
  chatQuickReplies.value = [
    { label: '满意，生成生产模拟图', type: 'multiview', value: '' },
    { label: '不满意，告诉我怎么改', type: 'refine', value: '' },
    { label: '生成 3D 原型', type: 'model', value: '' },
  ]
  phase.value = 'result'
  await scrollToSection('result-output')
}

async function completeRefinedProductImage(result: any, generationPrompt: string, note: string) {
  const sourceAssetId = generatedAssetId.value
  if (!selectedProduct.value || !sourceAssetId) throw new Error('当前产品图已失效，请重新生成')
  const newAssetId = Number(result?.assetId || result?.id)
  if (!Number.isFinite(newAssetId) || newAssetId <= 0) throw new Error('修改后的产品图没有保存成功，请重试')
  generatedAssetId.value = newAssetId
  previewUrl.value = imageUrl(result) || await freshAssetPreview(newAssetId)
  referenceAnalysis.value = String(result?.referenceAnalysis || referenceAnalysis.value || '')
  multiviewImages.value = []
  simulationAssetId.value = null
  simulationImage.value = null
  multiviewBundleId.value = null
  multiviewBundleNo.value = ''
  multiviewBundleStatus.value = ''
  multiviewBundleComment.value = ''
  clearModelGeneration()
  await saveCreativeEventBestEffort('image', 'image_refined', {
    previousAssetId: sourceAssetId,
    generatedAssetId: newAssetId,
    previewUrl: previewUrl.value,
    refinementNote: note,
    optimizedPrompt: generationPrompt,
    productType: selectedProduct.value.name,
    material: material.value,
    productSize: productSize.value,
    referenceAnalysis: result?.referenceAnalysis || '',
    referenceAnalysisSource: result?.referenceAnalysisSource || '',
  })
  addMessage('user', `补充修改：${note}`)
  addMessage('assistant', '新的产品视觉已经生成，旧版本仍保留在作品库。你可以继续修改，或生成生产模拟图和 3D。')
  cancelRefinement()
  await scrollToSection('result-output')
}

const imageGeneration = useImageGeneration({
  selectedProduct,
  productKey: computed(() => selectedProduct.value?.key),
  productCategory: computed(() => selectedProduct.value?.categoryName || selectedProduct.value?.categoryKey),
  productType: computed(() => selectedProduct.value?.name),
  material,
  productSize,
  prompt,
  inspirationText,
  mode,
  referenceAssetId,
  projectId,
  versionId,
  generatedAssetId,
  previewUrl,
  referenceAnalysis,
  busy,
  busyMessage,
  imageGenerationStage,
  pendingImageJobId,
  pendingGenerationPrompt,
  ensureAiPolicy: ensureAiPolicyForImage,
  requireSession: () => Boolean(requireSession()),
  saveEvent,
  saveEventBestEffort: saveCreativeEventBestEffort,
  freshAssetPreview,
  onGenerated: completeGeneratedProductImage,
  onRefined: completeRefinedProductImage,
})

async function generateProductImage() {
  try {
    await imageGeneration.generateProductImage()
  } catch (error: any) {
    if (isAuthenticationError(error)) return
    uni.showModal({ title: '产品图未生成', content: imageGeneration.generationFailureMessage(error), showCancel: false })
  }
}

async function resumePendingImageGeneration() {
  try {
    await imageGeneration.resumePendingImageGeneration()
  } catch (error: any) {
    if (isAuthenticationError(error)) return
    uni.showModal({ title: '生成进度暂时无法读取', content: imageGeneration.generationFailureMessage(error), showCancel: false })
  }
}

function updateMultiViewChatState() {
  if (multiviewBundleStatus.value === 'approved') {
    chatStage.value = 'multiview_approved'
    chatQuickReplies.value = [
      { label: '申请打样', type: 'bundle_production', value: '' },
      { label: '继续生成 3D', type: 'model', value: '' },
    ]
  } else if (multiviewBundleStatus.value === 'review') {
    chatStage.value = 'multiview_review'
    chatQuickReplies.value = [{ label: '查看我的作品', type: 'works', value: '' }]
  } else if (multiviewBundleStatus.value === 'rejected') {
    chatStage.value = 'multiview_rejected'
    chatQuickReplies.value = [{ label: '重新提交审核', type: 'bundle_review', value: '' }]
  } else {
    chatStage.value = 'multiview_ready'
    chatQuickReplies.value = [{ label: '提交生产模拟图审核', type: 'bundle_review', value: '' }]
  }
}

const multiViewGeneration = useMultiViewGeneration({
  selectedProduct,
  productKey: computed(() => selectedProduct.value?.key),
  productCategory: computed(() => selectedProduct.value?.categoryName || selectedProduct.value?.categoryKey),
  productType: computed(() => selectedProduct.value?.name),
  material,
  productSize,
  prompt,
  generatedAssetId,
  projectId,
  versionId,
  busy,
  busyMessage,
  multiviewImages,
  simulationAssetId,
  simulationImage,
  multiviewBundleId,
  multiviewBundleNo,
  multiviewBundleStatus,
  multiviewBundleComment,
  multiviewBundleSubmitting,
  pendingMultiViewJobId,
  pendingMultiViewInputAssetId,
  pendingMultiViewPrompt,
  ensureAiPolicy: ensureAiPolicyForImage,
  saveEvent,
  saveEventBestEffort: saveCreativeEventBestEffort,
  freshAssetPreview,
  updateMultiViewChatState,
  onGenerated: async () => {
    addMessage('assistant', '生产模拟图已保存为一个作品包：完整横向图用于查看，正面、侧面和背面切片用于建模。先提交整包审核，审核通过后就可以申请打样；如果审核未通过，我会把原因保留在这里。')
    phase.value = 'multiview'
    await scrollToSection('multiview-output')
  },
})

const { hasCompleteThreeViews } = multiViewGeneration

async function generateMultiView() {
  try {
    await multiViewGeneration.generateMultiView()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '生产模拟图生成失败', icon: 'none' })
  }
}

async function resumePendingMultiViewGeneration() {
  try {
    await multiViewGeneration.resumePendingMultiViewGeneration()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '生产模拟图生成进度暂时无法读取', icon: 'none' })
  }
}

async function restoreCurrentMultiViewBundle() {
  try {
    await multiViewGeneration.restoreCurrentMultiViewBundle()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '生产模拟图审核状态暂时无法读取', icon: 'none' })
  }
}

async function submitMultiViewReview() {
  try {
    await multiViewGeneration.submitMultiViewReview()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '提交生产模拟图审核失败', icon: 'none' })
  }
}

function applyMultiViewProduction() {
  multiViewGeneration.applyMultiViewProduction()
}

const modelGeneration = useModelGeneration({
  selectedProduct,
  productKey: computed(() => selectedProduct.value?.key),
  productType: computed(() => selectedProduct.value?.name),
  material,
  productSize,
  prompt,
  generatedAssetId,
  projectId,
  versionId,
  multiviewImages,
  hasCompleteThreeViews,
  multiviewBundleStatus,
  // Keep a failed multi-view task in multi-view mode when the user retries
  // from the model result panel (the phase has already moved to `model`).
  useMultiViewInput: computed(() => phase.value === 'multiview'
    || (phase.value === 'model' && modelInputMode.value === 'multiview')),
  busy,
  busyMessage,
  modelInputMode,
  modelTask,
  modelRefreshing,
  ensureThreeDimensionalPolicy,
  saveEvent,
  onSubmitted: async (_task, context) => {
    const inputLabel = context.inputMode === 'multiview' ? '生产模拟图建模' : '单图建模'
    addMessage('assistant', `${inputLabel}任务已提交，完成后会出现在作品库。你可以在那里预览、评审并申请打样。`)
    chatStage.value = 'model_running'
    chatQuickReplies.value = [{ label: '查看我的作品', type: 'works', value: '' }]
    phase.value = 'model'
    await scrollToSection('model-output')
  },
  onSucceeded: async () => {
    addMessage('assistant', '3D 模型已经生成并保存到作品库，可以继续评审、申请打样或提交商品化报价。')
    chatStage.value = 'model_ready'
    chatQuickReplies.value = [
      { label: '申请打样 / 商品化', type: 'commercial', value: '' },
      { label: '查看我的作品', type: 'works', value: '' },
    ]
    if (phase.value === 'model') await scrollToSection('model-output')
  },
  onFailed: () => {
    addMessage('assistant', '3D 建模没有完成，失败原因已保存。可以检查产品图后重新提交。')
  },
})

const {
  normalizedModelProgress,
  isModelTaskSucceeded,
  isModelTaskFailed,
  isModelTaskTerminal,
  modelTaskTitle,
  modelInputLabel,
  modelTaskDescription,
  modelTaskDetail,
  setModelTask,
  stopModelPolling,
  refreshModelTask,
  scheduleModelPolling,
  generateModel,
  clearModelGeneration,
} = modelGeneration

const conversationChat = useConversationChat({
  sessionId,
  busy,
  chatSending,
  quickReplySubmitting,
  chatInput,
  chatQuickReplies,
  awaitingGenerationConfirmation,
  chatStage,
  autoGenerationInFlight,
  productSize,
  productSizeRecommended,
  generatedAssetId,
  phase,
  selectedProduct,
  inspirationText,
  referencePath,
  referenceAssetId,
  ensureSession,
  applyChatBrief,
  addMessage,
  addAssistantMessage,
  setChatThinking,
  setGenerationConfirmationReplies,
  hasReferenceImage,
  activateReferenceImageMode,
  preserveReferenceImageMode,
  hasCompleteLocalGenerationBrief,
  chooseRecommendedSizeLocally,
  recommendedProductSize: () => localRecommendedProductSize(selectedProduct.value),
  saveCreativeEventBestEffort,
  generateProductImage,
  pickInspirationImage,
  generateMultiView,
  submitMultiViewReview,
  applyMultiViewProduction,
  generateModel: async () => { await generateModel() },
  openCommercial,
  goWorks,
  startRefinement,
  showTemplateDeveloping,
  removeOptimisticMessage: id => { messages.value = messages.value.filter(item => item.id !== id) },
  clearChatDraft,
  onMissingText: () => { uni.showToast({ title: '请在下方输入框告诉我你的想法', icon: 'none' }) },
  onChatError: (message, error) => {
    if (isAuthenticationError(error)) return
    uni.showModal({ title: '对话暂时中断', content: message, showCancel: false })
  },
})
const {
  sendChatTurn,
  handleQuickReply,
  submitChatInput,
} = conversationChat

async function regenerateWithRefinement() {
  try {
    await imageGeneration.regenerateWithRefinement(refinementNote.value)
  } catch (error: any) {
    if (isAuthenticationError(error)) return
    uni.showToast({ title: error?.message || '重新生成失败，请稍后重试', icon: 'none' })
  }
}

function imageUrl(item: any) {
  const raw = String(item?.previewUrl || item?.imageUrl || item?.fileUrl || item?.url || item?.accessUrl || '')
  if (/^https?:\/\//i.test(raw)) return raw
  return raw.startsWith('/') ? apiUrl(raw) : ''
}
async function previewMessageImage(item: Message) {
  let url = item.imageUrl || ''
  if (!url && item.imageAssetId) url = await freshAssetPreview(item.imageAssetId)
  if (!url) {
    uni.showToast({ title: item.imageState === 'failed' ? '图片上传失败，请重新选择' : '图片还在加载，请稍候', icon: 'none' })
    return
  }
  if (!item.imageUrl) updateImageMessage(item.id, { imageUrl: url, imageState: 'ready' })
  uni.previewImage({ current: url, urls: [url] })
}
let copyingMessageText = false

function clipboardText(value: unknown) {
  // Keep line breaks/tabs, but remove control characters that can make the
  // native clipboard reject an otherwise valid string.
  return String(value ?? '').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, '').trim()
}

function invokeClipboardApi(api: { setClipboardData: (options: WechatClipboardOptions) => void }, data: string) {
  return new Promise<void>((resolve, reject) => {
    try {
      const options: WechatClipboardOptions = {
        data,
        success: () => resolve(),
        fail: (error) => reject(error),
      }
      api.setClipboardData(options)
    } catch (error) {
      reject(error)
    }
  })
}

async function writeClipboardText(data: string) {
  let firstError: unknown
  try {
    const nativeWx = typeof wx !== 'undefined' ? wx : undefined
    if (nativeWx && typeof nativeWx.setClipboardData === 'function') {
      await invokeClipboardApi({ setClipboardData: nativeWx.setClipboardData.bind(nativeWx) }, data)
      return true
    }
  } catch (error) {
    firstError = error
    if (isClipboardPrivacyError(error)) throw error
  }

  try {
    await new Promise<void>((resolve, reject) => {
      try {
        uni.setClipboardData({
          data,
          showToast: false,
          success: () => resolve(),
          fail: (error) => reject(error),
        })
      } catch (error) {
        reject(error)
      }
    })
    return false
  } catch (error) {
    throw firstError || error
  }
}

function isClipboardPrivacyError(error: unknown) {
  const raw = String((error as any)?.errMsg || (error as any)?.message || '')
  return /privacy agreement|privacy policy|scope is not declared|隐私协议|隐私指引/i.test(raw)
}

function clipboardFailureMessage(error: any) {
  const raw = String(error?.errMsg || error?.message || '').trim()
  console.warn('[clipboard] setClipboardData failed', { errMsg: raw, errCode: error?.errCode })
  if (isClipboardPrivacyError(error)) return '小程序隐私指引尚未声明剪贴板，请联系管理员配置；也可长按文字复制'
  if (/not support|not available|undefined|not a function|navigator\.clipboard/i.test(raw)) return '当前环境不支持复制，请在微信小程序中操作'
  if (/auth|permission|denied|forbidden|拒绝/i.test(raw)) return '剪贴板权限被系统拒绝，请允许后重试'
  return '复制失败，可长按文字复制'
}

async function copyMessageText(item: Message) {
  const value = clipboardText(item.text)
  if (!value || copyingMessageText) return
  copyingMessageText = true
  try {
    const nativeToastShown = await writeClipboardText(value)
    if (!nativeToastShown) uni.showToast({ title: '已复制', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: clipboardFailureMessage(error), icon: 'none' })
  } finally {
    copyingMessageText = false
  }
}

async function previewMultiViewImage(item: SeedreamMultiViewImage) {
  let current = imageUrl(item)
  if (!current && Number(item.assetId) > 0) current = await freshAssetPreview(Number(item.assetId))
  if (!current) {
    uni.showToast({ title: '视图还在加载，请稍候', icon: 'none' })
    return
  }
  const urls = multiviewImages.value.map(imageUrl).filter(Boolean)
  uni.previewImage({ current, urls: urls.length ? urls : [current] })
}

async function previewSimulationImage() {
  let current = simulationImageUrl.value
  if (!current && simulationAssetId.value) current = await freshAssetPreview(simulationAssetId.value)
  if (!current) {
    uni.showToast({ title: '生产模拟图还在加载，请稍候', icon: 'none' })
    return
  }
  if (simulationImage.value && !simulationImage.value.previewUrl) simulationImage.value = { ...simulationImage.value, previewUrl: current }
  uni.previewImage({ current, urls: [current] })
}

function previewImage() { if (previewUrl.value) uni.previewImage({ current: previewUrl.value, urls: [previewUrl.value] }) }
function startRefinement() {
  if (!generatedAssetId.value) {
    uni.showToast({ title: '当前产品图未保存成功，请先重新生成', icon: 'none' })
    return
  }
  refinementNote.value = ''
  refiningImage.value = true
}
function cancelRefinement() {
  refiningImage.value = false
  refinementNote.value = ''
}

function restart() {
  if (busy.value || saving.value) {
    uni.showToast({ title: '当前正在保存或生成，请稍候', icon: 'none' })
    return
  }
  uni.showModal({
    title: '重新开始创作',
    content: '当前进度会保留在创作记录中，并为你新建一份空白创作。',
    confirmText: '重新开始',
    success: result => {
      if (!result.confirm) return
      persistChatDraft()
      uni.redirectTo({ url: '/pages/conversation-create/index?new=1' })
    },
  })
}

watch(chatInput, scheduleChatDraftSave)
onLoad(options => {
  campaignContext.value = campaignFromStorage()
  const campaignNeedsSession = Boolean(campaignContext.value && !Number(campaignContext.value.sessionId))
  forceNewSession.value = String(options?.new || '') === '1' || campaignNeedsSession
})
onMounted(async () => {
  if (!requireSession()) return
  if (!messages.value.length) addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  await loadProductCatalog()
  if (!(await ensureSession())) return
  restoreChatDraft()
  if (!messages.value.length) addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  await attachCampaignToConversation()
  if (awaitingGenerationConfirmation.value && !chatQuickReplies.value.length) setGenerationConfirmationReplies()
  if (!chatQuickReplies.value.length && chatStage.value !== 'need_additional_detail') setInitialChatReplies()
  if (pendingImageJobId.value && !generatedAssetId.value) void resumePendingImageGeneration()
  else if (pendingMultiViewJobId.value && !hasCompleteThreeViews.value) void resumePendingMultiViewGeneration()
  if (phase.value === 'multiview' && hasCompleteThreeViews.value) await restoreCurrentMultiViewBundle()
  if (phase.value === 'model' && modelTask.value && !isModelTaskTerminal.value) void scheduleModelPolling(true)
  if (phase.value === 'result') await scrollToSection('result-output')
  else if (phase.value === 'multiview') await scrollToSection('multiview-output')
  else if (phase.value === 'model') await scrollToSection('model-output')
})
onHide(persistChatDraft)
onUnload(persistChatDraft)
onUnmounted(() => { persistChatDraft(); resolvePolicyDialog(false); stopModelPolling() })
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding-bottom:116rpx;background:linear-gradient(180deg,#f7f3ed 0%,#f1ece4 100%);color:#332d28}.topbar{position:fixed;z-index:5;top:0;left:0;right:0;display:flex;align-items:center;gap:12rpx;padding:18rpx 26rpx calc(16rpx + env(safe-area-inset-top));border-bottom:1rpx solid rgba(116,96,75,.12);background:rgba(247,243,237,.96);backdrop-filter:blur(14rpx)}.back{width:48rpx;height:48rpx;color:#6d5f52;font-size:58rpx;line-height:38rpx;text-align:center}.topbar>view:nth-child(2){display:flex;flex:1;flex-direction:column;gap:4rpx}.eyebrow{color:#668071;font-size:14rpx;font-weight:900;letter-spacing:2rpx}.top-title{font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.save-state{color:#88988b;font-size:15rpx}.chat{height:calc(100vh - 132rpx);box-sizing:border-box;padding:126rpx 24rpx 26rpx}.intro-line{margin:0 2rpx 20rpx;padding:12rpx 14rpx;border-left:3rpx solid #b58b69;background:#f3eee6;color:#84786c;font-size:15rpx;line-height:1.5}.message-row{display:flex;align-items:flex-start;gap:9rpx;margin:17rpx 0}.message-row.user{justify-content:flex-end}.avatar{display:grid;place-items:center;flex:0 0 48rpx;width:48rpx;height:48rpx;border-radius:15rpx;background:#5e7c6d;color:#fff;font-family:"Songti SC","STSong",serif;font-size:25rpx}.bubble{max-width:78%;padding:14rpx 16rpx;border:1rpx solid #e2d8cb;border-radius:17rpx;background:#fffdfa;box-shadow:0 6rpx 15rpx rgba(80,61,42,.045)}.bubble text{color:#534940;font-size:20rpx;line-height:1.55}.user .bubble{border-color:#a9bdae;background:#e5efe7}.user .bubble text{color:#4f685b}.choice-panel,.input-panel,.summary-panel,.result-panel{margin:22rpx 0 26rpx;padding:19rpx;border:1rpx solid #e2d9ce;border-radius:22rpx;background:rgba(255,253,249,.9);box-shadow:0 10rpx 25rpx rgba(79,60,41,.06)}.choice-title{display:block;color:#403831;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.choice-note{display:block;margin-top:7rpx;color:#8c8075;font-size:16rpx;line-height:1.5}.choice-grid,.product-grid,.material-grid{display:grid;gap:10rpx;margin-top:15rpx}.choice-card{display:grid;grid-template-columns:50rpx minmax(0,1fr) 20rpx;align-items:center;gap:10rpx;padding:13rpx;border:1rpx solid #e4dbd0;border-radius:16rpx;background:#fffefa}.choice-mark,.product-mark{display:grid;place-items:center;width:47rpx;height:47rpx;border-radius:14rpx;background:#e8f0e8;color:#5e806e;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:800}.choice-card view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.choice-card view text:first-child{color:#463d35;font-size:21rpx;font-weight:800}.choice-card view text:last-child{color:#92867a;font-size:15rpx;line-height:1.4}.choice-arrow{color:#a16f59;font-size:33rpx}.product-grid{grid-template-columns:1fr 1fr}.product-card{display:flex;min-height:177rpx;flex-direction:column;padding:14rpx;border:1rpx solid #e5dbce;border-radius:17rpx;background:#fffefa}.product-card:active,.choice-card:active,.next-card:active{background:#f4efe7}.product-card:nth-child(2n) .product-mark{background:#f7e8df;color:#a96750}.product-card:nth-child(3n) .product-mark{background:#f5edd9;color:#947144}.product-name{margin-top:10rpx;color:#443a32;font-size:20rpx;font-weight:850}.product-desc{margin-top:5rpx;color:#8b7f73;font-size:14rpx;line-height:1.4}.product-process{margin-top:auto;color:#8c6e59;font-size:14rpx;font-weight:800}.text-input{width:100%;min-height:190rpx;box-sizing:border-box;margin-top:16rpx;padding:14rpx;border:1rpx solid #ddd2c5;border-radius:15rpx;background:#fbf9f5;color:#443b33;font-size:20rpx;line-height:1.6}.input-foot{display:flex;align-items:center;justify-content:space-between;margin-top:12rpx;color:#a09387;font-size:14rpx}.dark-button,.outline-button{height:76rpx;margin-top:15rpx;border-radius:14rpx;font-size:21rpx;font-weight:800}.dark-button{background:#3f3933;color:#fff}.dark-button::after,.outline-button::after,.link-button::after{border:0}.dark-button[disabled]{opacity:.48}.full-button{width:100%}.image-picker{display:flex;align-items:center;justify-content:center;height:300rpx;margin-top:16rpx;overflow:hidden;border:1rpx dashed #b5a796;border-radius:17rpx;background:#faf7f1}.image-picker>view{display:flex;align-items:center;flex-direction:column;gap:8rpx;color:#96897b}.image-picker>view text:first-child{font-size:62rpx;line-height:1}.image-picker image{width:100%;height:100%}.material-grid{grid-template-columns:1fr 1fr}.material-card{display:grid;grid-template-columns:36rpx minmax(0,1fr) 22rpx;align-items:center;gap:9rpx;min-height:74rpx;padding:11rpx;border:1rpx solid #e2d8cc;border-radius:15rpx;background:#fffefa}.material-card.active{border-color:#80a28f;background:#eef5ee}.swatch{width:32rpx;height:32rpx;border:1rpx solid rgba(100,80,58,.16);border-radius:10rpx}.material-card view:nth-child(2){display:flex;min-width:0;flex-direction:column;gap:4rpx}.material-card view text:first-child{color:#493f36;font-size:18rpx;font-weight:800}.material-card view text:last-child{color:#94877b;font-size:13rpx;line-height:1.3}.check{color:#56816c;font-size:21rpx;font-weight:900}.style-section{margin-top:17rpx}.style-section>text{color:#72675c;font-size:16rpx;font-weight:800}.pill-row{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:9rpx}.pill{padding:9rpx 12rpx;border:1rpx solid #e1d7cb;border-radius:999rpx;background:#fffefa;color:#897d71;font-size:15rpx}.pill.active{border-color:#6e907e;background:#e7f0e8;color:#4d715f;font-weight:800}.summary-card{display:grid;gap:0;margin-top:15rpx;border-top:1rpx solid #e6ddd2}.summary-card>view{display:grid;grid-template-columns:110rpx 1fr;gap:10rpx;padding:12rpx 0;border-bottom:1rpx solid #eee7df}.summary-card text:first-child{color:#9c8b7d;font-size:15rpx}.summary-card text:last-child{color:#4c4239;font-size:17rpx;line-height:1.45}.summary-note,.result-tip{display:block;margin-top:14rpx;color:#82766a;font-size:16rpx;line-height:1.55}.link-button{display:block;margin:13rpx auto 0;padding:0;background:transparent;color:#93705d;font-size:16rpx}.result-kicker{display:block;color:#9d7a5e;font-size:14rpx;font-weight:900;letter-spacing:2rpx}.result-image{width:100%;height:430rpx;margin-top:15rpx;border-radius:17rpx;background:#eee7dc}.result-placeholder{display:flex;align-items:center;justify-content:center;height:260rpx;margin-top:15rpx;flex-direction:column;gap:9rpx;border-radius:17rpx;background:linear-gradient(145deg,#d9e7dc,#ead9cc);color:#557365}.result-placeholder text:first-child{font-family:"Songti SC","STSong",serif;font-size:62rpx}.result-placeholder text:last-child{font-size:16rpx}.next-grid{display:grid;gap:10rpx;margin-top:17rpx}.next-card{display:grid;grid-template-columns:48rpx minmax(0,1fr) 18rpx;align-items:center;gap:10rpx;padding:13rpx;border:1rpx solid #e2d8cd;border-radius:15rpx;background:#fffefa}.next-card>text:first-child{display:grid;place-items:center;width:44rpx;height:44rpx;border-radius:13rpx;background:#edf3eb;color:#5e806e;font-family:"Songti SC","STSong",serif;font-size:24rpx;font-weight:800}.next-card view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.next-card view text:first-child{color:#473d35;font-size:19rpx;font-weight:800}.next-card view text:last-child{color:#92867a;font-size:14rpx}.next-card>text:last-child{color:#a16f59;font-size:31rpx}.view-grid{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:15rpx}.view-card{overflow:hidden;border:1rpx solid #e2d8cd;border-radius:14rpx;background:#fffefa}.view-card image,.view-placeholder{display:block;width:100%;height:190rpx;background:#eee8df}.view-placeholder{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:5rpx;color:#817367;font-size:15rpx}.view-card>text:last-child{display:block;padding:8rpx 10rpx;color:#6f6257;font-size:15rpx;font-weight:800}.model-success{display:flex;align-items:center;gap:14rpx;margin-top:18rpx;padding:16rpx;border-radius:16rpx;background:#e8f0e9}.model-success>text{display:grid;place-items:center;width:74rpx;height:74rpx;border-radius:22rpx;background:#5f7d6e;color:#fff;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.model-success view{display:flex;flex:1;flex-direction:column;gap:6rpx}.model-success view text:first-child{color:#4c6e5c;font-size:20rpx;font-weight:800}.model-success view text:last-child{color:#789082;font-size:14rpx;line-height:1.4}.model-progress{margin-top:15rpx;padding:13rpx;border:1rpx solid #dbe7dc;border-radius:14rpx;background:#f7fbf6}.model-progress>view:first-child{display:flex;justify-content:space-between;color:#54715f;font-size:15rpx;font-weight:800}.model-progress-track{height:12rpx;margin-top:10rpx;overflow:hidden;border-radius:999rpx;background:#dbe8dd}.model-progress-value{height:100%;border-radius:inherit;background:#648875;transition:width .35s ease}.model-error{display:block;margin-top:12rpx;padding:11rpx;border-radius:12rpx;background:#fff0ec;color:#a05543;font-size:14rpx;line-height:1.45}.outline-button{border:1rpx solid #9ab4a2;background:#f7fbf6;color:#557564}.loading-bar{position:fixed;z-index:7;right:20rpx;bottom:115rpx;left:20rpx;padding:12rpx 14rpx;border:1rpx solid #d9c8b5;border-radius:13rpx;background:#fff7eb;color:#96704f;font-size:15rpx;text-align:center;box-shadow:0 8rpx 20rpx rgba(81,58,35,.12)}.bottom-actions{position:fixed;z-index:6;right:0;bottom:0;left:0;display:flex;justify-content:space-around;padding:13rpx 20rpx calc(13rpx + env(safe-area-inset-bottom));border-top:1rpx solid rgba(110,91,70,.14);background:rgba(247,243,237,.96);backdrop-filter:blur(13rpx)}.bottom-actions button{margin:0;background:transparent;color:#6f6256;font-size:16rpx}.bottom-actions button::after{border:0}
.catalog-tools{margin-top:15rpx;padding:12rpx;border:1rpx solid #e6ddd2;border-radius:15rpx;background:#f8f4ed}.catalog-search{box-sizing:border-box;width:100%;height:66rpx;padding:0 13rpx;border:1rpx solid #ded4c7;border-radius:11rpx;background:#fffefa;color:#4c433a;font-size:18rpx}.catalog-categories{margin-top:10rpx;white-space:nowrap}.catalog-categories>view{display:flex;gap:7rpx}.catalog-category{display:inline-block;padding:7rpx 10rpx;border:1rpx solid #ded5c9;border-radius:9rpx;background:#fffefa;color:#897d72;font-size:14rpx}.catalog-category.active{border-color:#72917f;background:#e7f0e7;color:#4e705e;font-weight:800}.catalog-count{display:block;margin-top:9rpx;color:#907d6f;font-size:13rpx}.catalog-empty{margin-top:15rpx;padding:34rpx 16rpx;border:1rpx dashed #d8cbbd;border-radius:15rpx;background:#faf7f1;color:#8f8276;font-size:17rpx;text-align:center}.product-card{min-height:187rpx}.product-category-name{margin-top:9rpx;color:#9c8879;font-size:12rpx}.product-name{margin-top:4rpx;line-height:1.35}.product-desc,.product-process{display:-webkit-box;overflow:hidden;-webkit-box-orient:vertical}.product-desc{-webkit-line-clamp:2}.product-process{font-size:13rpx;line-height:1.35;-webkit-line-clamp:2}
.category-entry-grid{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:15rpx}.category-entry{display:grid;grid-template-columns:44rpx minmax(0,1fr) 15rpx;align-items:center;gap:8rpx;min-height:112rpx;padding:12rpx;border:1rpx solid #dfd6ca;border-radius:15rpx;background:#fffefa}.category-entry:active{background:#f1f5ef}.category-entry-mark{display:grid;place-items:center;width:42rpx;height:42rpx;border-radius:12rpx;background:#e7f0e8;color:#567665;font-family:"Songti SC","STSong",serif;font-size:23rpx;font-weight:800}.category-entry view{display:flex;min-width:0;flex-direction:column;gap:5rpx}.category-entry view text:first-child{overflow:hidden;color:#4d433b;font-size:17rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.category-entry view text:last-child{color:#97887b;font-size:12rpx}.category-entry>text:last-child{color:#aa7a61;font-size:27rpx}.catalog-result-title{display:block;margin:16rpx 2rpx 0;color:#6a5c4e;font-size:18rpx;font-weight:850}
.recommendation-card{border-color:#a8beab;background:#f0f7ef}.recommendation-mark{display:grid;place-items:center;width:32rpx;height:32rpx;border-radius:10rpx;background:#5d806b;color:#fff;font-size:18rpx;font-weight:850}.recommendation-pill{border-color:#8cad98;background:#edf5ed;color:#4f715d;font-weight:850}
.refinement-panel{margin-top:16rpx;padding:15rpx;border:1rpx solid #d8c9b7;border-radius:15rpx;background:#f8f3eb}.refinement-panel>text:first-child{display:block;color:#5c5044;font-size:19rpx;font-weight:850}.refinement-input{min-height:130rpx;margin-top:10rpx;font-size:18rpx}.refinement-panel .dark-button{height:64rpx;margin:0;font-size:17rpx}
.food-direction-note{display:block;margin-top:14rpx;padding:12rpx;border-left:4rpx solid #b37b4d;border-radius:0 10rpx 10rpx 0;background:#fbf2e5;color:#795b42;font-size:16rpx;line-height:1.55}
.policy-mask{position:fixed;z-index:20;inset:0;display:flex;align-items:center;justify-content:center;padding:38rpx;background:rgba(24,29,26,.58);box-sizing:border-box}.policy-dialog{width:100%;max-height:80vh;overflow:hidden;border-radius:18rpx;background:#fffdfa;box-shadow:0 20rpx 50rpx rgba(25,31,27,.3)}.policy-dialog-head{display:flex;align-items:center;justify-content:space-between;padding:22rpx 22rpx 13rpx;border-bottom:1rpx solid #ece4d9}.policy-dialog-head text:first-child{color:#3d3831;font-size:24rpx;font-weight:850}.policy-dialog-head text:last-child{color:#a36e57;font-size:14rpx}.policy-dialog-title{display:block;padding:18rpx 22rpx 7rpx;color:#332e29;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:850}.policy-dialog-copy{box-sizing:border-box;width:100%;height:270rpx;padding:0 22rpx 18rpx}.policy-dialog-copy text{color:#6f665c;font-size:17rpx;line-height:1.7}.policy-dialog-actions{display:flex;gap:10rpx;padding:14rpx 22rpx calc(18rpx + env(safe-area-inset-bottom));border-top:1rpx solid #eee7de;background:#fffdfa}.policy-dialog-actions button{flex:1;height:78rpx;margin:0;border-radius:10rpx;font-size:18rpx;font-weight:850}.policy-dialog-actions button::after{border:0}.policy-cancel{border:1rpx solid #ded5c9;background:#f7f3ed;color:#827568}.policy-confirm{background:#3f3933;color:#fff}
.topbar-actions{display:flex;align-items:center;gap:10rpx}.previous-button{height:46rpx;margin:0;padding:0 12rpx;border:1rpx solid #bfd0c1;border-radius:9rpx;background:#f3f8f3;color:#527463;font-size:14rpx;line-height:46rpx}.previous-button::after{border:0}.previous-button[disabled],.bottom-actions button[disabled]{opacity:.55}
.chat-experience .choice-panel,.chat-experience .input-panel{display:none}.chat-command-panel{margin:18rpx 0 24rpx;padding:15rpx;border:1rpx solid #dfd5c9;border-radius:18rpx;background:rgba(255,253,249,.94);box-shadow:0 8rpx 20rpx rgba(79,60,41,.05)}.chat-stage-label{display:block;color:#837568;font-size:15rpx;line-height:1.4}.quick-reply-list{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:12rpx}.quick-reply{height:62rpx;margin:0;padding:0 14rpx;border:1rpx solid #a9c1ad;border-radius:13rpx;background:#eff6ef;color:#4f705e;font-size:16rpx;line-height:62rpx}.quick-reply::after{border:0}.quick-reply[disabled]{opacity:.5}.chat-input-row{display:flex;align-items:center;gap:8rpx;margin-top:12rpx}.chat-upload-button,.chat-send-button{flex:0 0 auto;height:66rpx;margin:0;border-radius:12rpx;font-size:17rpx;line-height:66rpx}.chat-upload-button{width:66rpx;padding:0;border:1rpx solid #d5c9bc;background:#faf6ef;color:#806f61;font-size:30rpx}.chat-send-button{padding:0 15rpx;background:#3f3933;color:#fff}.chat-input{flex:1;box-sizing:border-box;height:66rpx;padding:0 13rpx;border:1rpx solid #d9cec1;border-radius:12rpx;background:#fbf9f5;color:#443b33;font-size:18rpx}.chat-send-button::after,.chat-upload-button::after{border:0}.chat-send-button[disabled]{opacity:.45}
.thinking-row{display:flex;align-items:flex-start;gap:9rpx;margin:17rpx 0 18rpx;animation:thinking-enter .24s ease-out}.thinking-avatar{animation:thinking-breathe 1.8s ease-in-out infinite;box-shadow:0 0 0 6rpx rgba(94,124,109,.08)}.thinking-bubble{max-width:78%;padding:13rpx 16rpx;border:1rpx solid #d4e0d5;border-radius:17rpx 17rpx 17rpx 7rpx;background:#f8fcf8;box-shadow:0 7rpx 17rpx rgba(73,102,81,.07)}.thinking-title-row{display:flex;align-items:center;gap:9rpx}.thinking-title{color:#4d705c;font-size:19rpx;font-weight:850}.thinking-detail{display:block;margin-top:5rpx;color:#8a9b8d;font-size:15rpx;line-height:1.4}.thinking-dots{display:flex;align-items:center;gap:4rpx;height:22rpx}.thinking-dot{width:7rpx;height:7rpx;border-radius:50%;background:#6e967c;animation:thinking-dot-bounce 1.25s ease-in-out infinite}.thinking-dot:nth-child(2){animation-delay:.16s}.thinking-dot:nth-child(3){animation-delay:.32s}.quick-reply[disabled],.chat-upload-button[disabled],.chat-send-button[disabled]{opacity:.72;filter:none}.quick-reply[disabled]{border-color:#c4d5c6;background:#f1f7f1;color:#779180}.chat-upload-button[disabled]{border-color:#d9d5cc;background:#f6f4ef;color:#998f83}.chat-send-button[disabled]{background:#7b877f;color:#fff}.dark-button[disabled]{opacity:.72;background:#68746d;color:#fff}.loading-bar{display:flex;align-items:center;justify-content:center;gap:9rpx}.loading-spinner{width:22rpx;height:22rpx;border:3rpx solid #e8d8c7;border-top-color:#ad7e5d;border-radius:50%;animation:loading-spin .8s linear infinite}@keyframes thinking-enter{from{opacity:0;transform:translateY(8rpx)}to{opacity:1;transform:translateY(0)}}@keyframes thinking-breathe{0%,100%{transform:translateY(0);box-shadow:0 0 0 6rpx rgba(94,124,109,.08)}50%{transform:translateY(-2rpx);box-shadow:0 0 0 10rpx rgba(94,124,109,.03)}}@keyframes thinking-dot-bounce{0%,60%,100%{opacity:.35;transform:translateY(0) scale(.85)}30%{opacity:1;transform:translateY(-4rpx) scale(1)}}@keyframes loading-spin{to{transform:rotate(360deg)}}
</style>

<style scoped lang="scss">
/* The conversation page is a focused workspace: the transcript stays clear,
 * while the composer and project state remain available at the edges. */
.page.chat-experience {
  --ink: #26332d;
  --ink-soft: #738079;
  --line: #e2e8e3;
  --paper: #f5f7f5;
  --surface: #ffffff;
  --green: #3f6958;
  --green-soft: #e9f2ec;
  --orange: #c76f53;
  --orange-soft: #fff0e9;
  min-height: 100vh;
  box-sizing: border-box;
  padding-bottom: calc(118rpx + env(safe-area-inset-bottom));
  background: var(--paper);
  color: var(--ink);
}

.workspace-intro-top,
.output-header,
.output-info,
.composer-context,
.composer-footer,
.refinement-heading,
.progress-row {
  display: flex;
  align-items: center;
}

.surface-kicker {
  display: block;
  color: #84938b;
  font-size: 11rpx;
  font-weight: 900;
  letter-spacing: 1.8rpx;
}

.chat {
  height: 100vh;
  box-sizing: border-box;
  padding: 24rpx 28rpx calc(332rpx + env(safe-area-inset-bottom));
}
.workspace-intro { margin: 6rpx 0 18rpx; }
.workspace-intro-top { justify-content: space-between; gap: 10rpx; }
.online-mark { display: flex; align-items: center; gap: 7rpx; color: var(--green); font-size: 13rpx; font-weight: 850; }
.online-dot { width: 10rpx; height: 10rpx; border-radius: 50%; background: #6faa82; box-shadow: 0 0 0 5rpx rgba(111, 170, 130, .12); }
.workspace-ref { overflow: hidden; color: #a0aaa4; font-size: 12rpx; text-overflow: ellipsis; white-space: nowrap; }
.workspace-title { display: block; margin-top: 13rpx; color: var(--ink); font-family: "Songti SC", "STSong", serif; font-size: 35rpx; font-weight: 800; line-height: 1.25; }
.workspace-subtitle { display: block; max-width: 630rpx; margin-top: 7rpx; color: var(--ink-soft); font-size: 15rpx; line-height: 1.5; }
.brief-strip { display: flex; flex-wrap: wrap; gap: 7rpx; margin-top: 13rpx; }
.brief-chip { display: inline-flex; align-items: center; gap: 6rpx; padding: 6rpx 9rpx; border: 1rpx solid #cdded2; border-radius: 8rpx; background: #edf5ef; color: var(--green); font-size: 12rpx; }
.brief-chip text:first-child { color: #8ca296; }
.brief-chip.muted { border-color: #e2e7e3; background: #fff; color: #84918a; }
.campaign-strip { display: flex; align-items: flex-start; justify-content: space-between; gap: 10rpx; margin-top: 13rpx; padding: 10rpx 11rpx; border: 1rpx solid #c7dccb; border-radius: 11rpx; background: #f2f8f2; }
.campaign-strip>view { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3rpx; }
.campaign-strip>view text:first-child { color: #66806e; font-size: 11rpx; font-weight: 900; letter-spacing: 1.2rpx; }
.campaign-strip>view text:nth-child(2) { overflow: hidden; color: #3e5949; font-size: 15rpx; font-weight: 850; text-overflow: ellipsis; white-space: nowrap; }
.campaign-strip>view text:last-child { overflow: hidden; color: #789081; font-size: 11rpx; text-overflow: ellipsis; white-space: nowrap; }
.campaign-strip>text { flex: 0 0 auto; padding: 5rpx 6rpx; border-radius: 7rpx; background: #dcecdf; color: #4e745c; font-size: 11rpx; font-weight: 850; white-space: nowrap; }
.ai-disclosure { margin: 0 0 22rpx; }

.message-row { display: flex; align-items: flex-start; gap: 10rpx; margin: 20rpx 0; }
.message-row.user { justify-content: flex-end; }
.message-avatar { display: grid; place-items: center; flex: 0 0 46rpx; width: 46rpx; height: 46rpx; border-radius: 14rpx; font-family: "Songti SC", "STSong", serif; font-size: 22rpx; font-weight: 850; }
.assistant-avatar { background: var(--green); color: #fff; box-shadow: 0 5rpx 12rpx rgba(54, 93, 74, .18); }
.user-avatar { background: #f7e5dc; color: #a45d48; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; font-size: 16rpx; }
.message-content { display: flex; min-width: 0; max-width: 82%; flex-direction: column; align-items: flex-start; }
.user .message-content { align-items: flex-end; }
.message-meta { display: flex; align-items: center; gap: 8rpx; margin: 0 4rpx 8rpx; color: #849089; font-size: 19rpx; }
.message-meta text:last-child { color: #a5afa9; }
.bubble { max-width: 100%; box-sizing: border-box; padding: 18rpx 20rpx; border: 1rpx solid #e1e8e2; border-radius: 7rpx 16rpx 16rpx 16rpx; background: var(--surface); box-shadow: 0 6rpx 17rpx rgba(51, 72, 60, .045); }
.bubble text { color: #3f4d45; font-size: 28rpx; line-height: 1.65; }
.user .bubble { border-color: #c3d5c8; border-radius: 16rpx 7rpx 16rpx 16rpx; background: #e8f2eb; }
.user .bubble text { color: #436052; }
.image-bubble { width: 100%; max-width: 540rpx; padding: 10rpx; }
.message-image, .message-image-loading { display: block; width: 100%; height: 360rpx; overflow: hidden; border-radius: 10rpx; background: #dfe9e1; }
.message-image-loading { display: flex; align-items: center; justify-content: center; color: #759080; font-size: 20rpx; }
.message-image-footer { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; padding: 9rpx 3rpx 1rpx; }
.message-image-footer text:first-child { overflow: hidden; color: #6f8176; font-size: 18rpx; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.message-image-footer text:first-child.failed { color: #b46350; }
.message-image-reselect { flex: 0 0 auto; color: #a16f59 !important; font-size: 18rpx !important; font-weight: 800; }
.message-actions { display: flex; justify-content: flex-end; margin-top: 8rpx; }
.message-copy { padding: 3rpx 2rpx; color: #789184 !important; font-size: 18rpx !important; line-height: 1.3 !important; }
.message-copy:active { color: #4f7561 !important; opacity: .72; }

.thinking-row { display: flex; align-items: flex-start; gap: 10rpx; margin: 20rpx 0; animation: thinking-enter .24s ease-out; }
.thinking-content { display: flex; min-width: 0; max-width: 82%; flex-direction: column; }
.thinking-bubble { padding: 13rpx 16rpx; border: 1rpx solid #d7e5da; border-radius: 7rpx 16rpx 16rpx 16rpx; background: #f9fcf9; box-shadow: 0 7rpx 17rpx rgba(62, 103, 76, .07); }
.thinking-title-row { display: flex; align-items: center; gap: 10rpx; }
.thinking-title { color: var(--green); font-size: 25rpx; font-weight: 850; }
.thinking-detail { display: block; margin-top: 7rpx; color: #84958b; font-size: 22rpx; line-height: 1.5; }
.thinking-dots { display: flex; align-items: center; gap: 4rpx; height: 22rpx; }
.thinking-dot { width: 7rpx; height: 7rpx; border-radius: 50%; background: #78a58a; animation: thinking-dot-bounce 1.25s ease-in-out infinite; }
.thinking-dot:nth-child(2) { animation-delay: .16s; }
.thinking-dot:nth-child(3) { animation-delay: .32s; }

.output-surface { margin: 24rpx 0 20rpx; padding: 18rpx; border: 1rpx solid #dce6df; border-radius: 18rpx; background: #fff; box-shadow: 0 12rpx 28rpx rgba(42, 67, 53, .07); }
.output-header { justify-content: space-between; gap: 12rpx; }
.surface-title { display: block; margin-top: 5rpx; color: var(--ink); font-size: 25rpx; font-weight: 850; }
.output-status { display: flex; align-items: center; gap: 5rpx; color: #6f8d7b; font-size: 12rpx; }
.status-check { display: grid; place-items: center; width: 25rpx; height: 25rpx; border-radius: 50%; background: #e6f1e9; color: var(--green); font-size: 15rpx; font-weight: 900; }
.visual-frame { position: relative; overflow: hidden; margin-top: 16rpx; border-radius: 13rpx; background: #edf0ed; }
.simulation-frame { position: relative; overflow: hidden; margin-top: 15rpx; border: 1rpx solid #d3e1d6; border-radius: 13rpx; background: #edf3ee; }
.simulation-frame image { display: block; width: 100%; height: 310rpx; background: #edf3ee; }
.simulation-placeholder { display: flex; align-items: center; justify-content: center; min-height: 150rpx; margin-top: 15rpx; padding: 18rpx; flex-direction: column; gap: 6rpx; border: 1rpx dashed #b9cfbe; border-radius: 13rpx; background: #f4f8f4; color: #62806d; text-align: center; }
.simulation-placeholder text:first-child { font-size: 17rpx; font-weight: 850; }
.simulation-placeholder text:last-child { color: #8c9c91; font-size: 13rpx; }
.result-image { display: block; width: 100%; height: 420rpx; background: #edf0ed; }
.visual-badge { position: absolute; top: 12rpx; left: 12rpx; padding: 5rpx 8rpx; border: 1rpx solid rgba(255, 255, 255, .7); border-radius: 6rpx; background: rgba(35, 53, 43, .7); color: #fff; font-size: 11rpx; font-weight: 800; }
.result-placeholder { display: flex; align-items: center; justify-content: center; height: 420rpx; flex-direction: column; gap: 8rpx; background: #e9f0eb; color: #5c7a68; }
.result-placeholder text:first-child { font-family: "Songti SC", "STSong", serif; font-size: 58rpx; }
.result-placeholder text:last-child { font-size: 14rpx; }
.output-info { justify-content: space-between; gap: 10rpx; padding: 13rpx 2rpx 3rpx; }
.output-info view { display: flex; min-width: 0; flex-direction: column; gap: 4rpx; }
.output-info view text:first-child { overflow: hidden; color: var(--ink); font-size: 18rpx; font-weight: 850; text-overflow: ellipsis; white-space: nowrap; }
.output-info view text:last-child { color: #8b9890; font-size: 13rpx; }
.output-open { flex: 0 0 auto; color: var(--orange); font-size: 13rpx; font-weight: 800; }
.output-actions { display: grid; gap: 8rpx; margin-top: 13rpx; }
.output-action { display: grid; grid-template-columns: 44rpx minmax(0, 1fr) 18rpx; align-items: center; gap: 10rpx; min-height: 66rpx; padding: 10rpx 11rpx; border: 1rpx solid #e2e9e3; border-radius: 12rpx; background: #fbfcfb; }
.output-action.primary { border-color: #b7d0be; background: #f0f7f1; }
.output-action:active { background: #edf3ee; }
.action-icon { display: grid; place-items: center; width: 42rpx; height: 42rpx; border-radius: 12rpx; background: #dcebe0; color: var(--green); font-family: "Songti SC", "STSong", serif; font-size: 21rpx; font-weight: 850; }
.action-icon.warm { background: var(--orange-soft); color: var(--orange); }
.action-icon.dark { background: #e9ecea; color: #44534b; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; font-size: 14rpx; }
.action-icon.gold { background: #f9f0dd; color: #a17a3e; }
.output-action view:nth-child(2) { display: flex; min-width: 0; flex-direction: column; gap: 3rpx; }
.output-action view:nth-child(2) text:first-child { color: #3d4c43; font-size: 17rpx; font-weight: 850; }
.output-action view:nth-child(2) text:last-child { color: #8b9890; font-size: 13rpx; }
.action-arrow { color: #a8b3ac; font-size: 28rpx; }
.surface-note { display: block; margin-top: 10rpx; color: #7f8d84; font-size: 14rpx; line-height: 1.5; }
.view-grid-heading { display: flex; align-items: baseline; justify-content: space-between; gap: 8rpx; margin-top: 15rpx; }
.view-grid-heading text:first-child { color: #4c6152; font-size: 16rpx; font-weight: 850; }
.view-grid-heading text:last-child { color: #93a097; font-size: 12rpx; }

.view-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 9rpx; margin-top: 15rpx; }
.view-card { overflow: hidden; border: 1rpx solid #e0e8e1; border-radius: 12rpx; background: #fbfcfb; }
.view-card image, .view-placeholder { display: block; width: 100%; height: 184rpx; background: #edf1ed; }
.view-placeholder { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 5rpx; color: #829189; font-size: 13rpx; }
.view-label { display: flex; align-items: center; justify-content: space-between; gap: 5rpx; padding: 8rpx 9rpx; }
.view-label text:first-child { color: #4d5e53; font-size: 14rpx; font-weight: 850; }
.view-label text:last-child { color: #8aa493; font-size: 11rpx; }
.full-button { width: 100%; }
.dark-button, .outline-button { height: 68rpx; margin-top: 12rpx; border-radius: 11rpx; font-size: 17rpx; font-weight: 850; line-height: 68rpx; }
.dark-button { background: #354b40; color: #fff; }
.outline-button { border: 1rpx solid #b9cec0; background: #f8fbf8; color: #557464; }
.dark-button::after, .outline-button::after, .link-button::after { border: 0; }
.dark-button[disabled] { opacity: .55; }
.dark-button text { margin-left: 5rpx; font-size: 25rpx; line-height: 1; }

.model-summary { display: flex; align-items: center; gap: 13rpx; margin-top: 17rpx; padding: 14rpx; border-radius: 13rpx; background: #edf5ef; }
.model-mark { display: grid; place-items: center; flex: 0 0 62rpx; width: 62rpx; height: 62rpx; border-radius: 17rpx; background: var(--green); color: #fff; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; font-size: 18rpx; font-weight: 900; }
.model-summary view:last-child { display: flex; min-width: 0; flex-direction: column; gap: 5rpx; }
.model-summary view:last-child text:first-child { color: #4d6b59; font-size: 16rpx; font-weight: 850; }
.model-summary view:last-child text:last-child { color: #83958a; font-size: 13rpx; line-height: 1.4; }
.model-state { padding: 5rpx 8rpx; border-radius: 7rpx; background: #edf2ed; color: #6a8273; font-size: 12rpx; font-weight: 850; }
.model-state.done { background: #e5f2e8; color: #4f8463; }
.model-state.failed { background: #fff0ec; color: #ad5d4a; }
.model-progress { margin-top: 13rpx; padding: 13rpx; border: 1rpx solid #e0e9e1; border-radius: 12rpx; background: #fbfdfb; }
.progress-row { justify-content: space-between; color: #587161; font-size: 13rpx; font-weight: 850; }
.model-progress-track { height: 9rpx; margin-top: 10rpx; overflow: hidden; border-radius: 99rpx; background: #e1ebe3; }
.model-progress-value { height: 100%; border-radius: inherit; background: #67947a; transition: width .35s ease; }
.model-error { display: block; margin-top: 11rpx; padding: 10rpx; border-radius: 10rpx; background: #fff0ec; color: #a75948; font-size: 13rpx; line-height: 1.45; }

.bundle-review-state { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; margin-top: 14rpx; padding: 12rpx 13rpx; border: 1rpx solid #dfe7e1; border-radius: 11rpx; background: #f8faf8; }
.bundle-review-state>view { display: flex; min-width: 0; flex-direction: column; gap: 4rpx; }
.bundle-review-label { color: #929e97; font-size: 11rpx; }
.bundle-review-title { color: #55665c; font-size: 16rpx; font-weight: 850; }
.bundle-review-no { overflow: hidden; max-width: 190rpx; color: #9ba59f; font-size: 10rpx; text-overflow: ellipsis; white-space: nowrap; }
.bundle-review-state.bundle-review { border-color: #ead9ad; background: #fffaf0; }
.bundle-review-state.bundle-approved { border-color: #bbd8c3; background: #eef7f0; }
.bundle-review-state.bundle-approved .bundle-review-title { color: #47745a; }
.bundle-review-state.bundle-rejected { border-color: #edc8bd; background: #fff3ef; }
.bundle-review-state.bundle-rejected .bundle-review-title { color: #a65d49; }
.bundle-review-comment { display: block; margin-top: 9rpx; padding: 10rpx 11rpx; border-left: 4rpx solid #bd6d55; border-radius: 0 9rpx 9rpx 0; background: #fff4f0; color: #965542; font-size: 13rpx; line-height: 1.55; }

.refinement-panel { margin-top: 14rpx; padding: 14rpx; border: 1rpx solid #ead7ce; border-radius: 13rpx; background: #fff9f6; }
.refinement-heading { justify-content: space-between; gap: 10rpx; }
.refinement-heading view { display: flex; flex-direction: column; gap: 5rpx; }
.refinement-heading view text:last-child { color: #684e45; font-size: 17rpx; font-weight: 850; }
.refinement-close { color: #aa806e; font-size: 28rpx; }
.text-input { width: 100%; min-height: 132rpx; box-sizing: border-box; margin-top: 12rpx; padding: 12rpx; border: 1rpx solid #e5d4cc; border-radius: 10rpx; background: #fff; color: #493f3b; font-size: 17rpx; line-height: 1.55; }
.input-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 9rpx; color: #a08f86; font-size: 12rpx; }
.refinement-panel .dark-button { height: 58rpx; margin: 0; padding: 0 13rpx; font-size: 15rpx; line-height: 58rpx; }

.composer-dock { position: fixed; z-index: 25; right: 0; bottom: calc(88rpx + env(safe-area-inset-bottom)); left: 0; box-sizing: border-box; padding: 13rpx 22rpx 9rpx; border-top: 1rpx solid #dfe7e1; background: rgba(255, 255, 255, .97); box-shadow: 0 -10rpx 24rpx rgba(44, 62, 51, .07); backdrop-filter: blur(18rpx); }
.composer-context { min-width: 0; gap: 7rpx; color: #64776c; font-size: 20rpx; }
.context-live { width: 9rpx; height: 9rpx; border-radius: 50%; background: #70a481; }
.context-product { overflow: hidden; max-width: 260rpx; color: #96a29b; text-overflow: ellipsis; white-space: nowrap; }
.context-working { margin-left: auto; color: var(--orange); font-size: 19rpx; }
.quick-reply-list { display: block; width: 100%; height: 64rpx; min-height: 64rpx; margin-top: 10rpx; overflow: hidden; white-space: nowrap; }
.quick-reply-track { display: flex; align-items: center; height: 64rpx; gap: 8rpx; white-space: nowrap; }
.quick-reply { display: inline-flex; align-items: center; gap: 7rpx; flex: 0 0 auto; min-height: 64rpx; padding: 0 15rpx; border: 1rpx solid #d8e5db; border-radius: 10rpx; background: #f6faf7; color: #456655; font-size: 24rpx; line-height: 1.35; }
.quick-reply.confirm { border-color: #9fc3a9; background: #eaf5ed; color: #3f7052; font-weight: 850; }
.quick-reply.secondary { border-color: #e6d5ca; background: #fff9f5; color: #9b6b57; }
.quick-reply.disabled { opacity: .55; }
.quick-reply:active { opacity: .75; }
.quick-reply-mark { display: inline-grid; place-items: center; flex: 0 0 32rpx; width: 32rpx; height: 32rpx; border-radius: 7rpx; background: #dcebe0; color: #4e7860; font-size: 18rpx; font-weight: 900; line-height: 32rpx; }
.quick-reply.confirm .quick-reply-mark { background: #4f8563; color: #fff; }
.quick-reply.secondary .quick-reply-mark { background: #f3dfd3; color: #a66751; }
.chat-input-row { display: flex; align-items: center; gap: 8rpx; margin-top: 10rpx; }
.chat-upload-button, .chat-send-button { flex: 0 0 auto; height: 76rpx; margin: 0; border-radius: 12rpx; line-height: 76rpx; }
.chat-upload-button { width: 76rpx; padding: 0; border: 1rpx solid #d9e2db; background: #f8faf8; color: #658073; font-size: 34rpx; }
.chat-send-button { width: 76rpx; padding: 0; background: #dfe7e1; color: #91a099; font-size: 32rpx; font-weight: 900; }
.chat-send-button.ready { background: var(--green); color: #fff; }
.chat-input { flex: 1; min-width: 0; height: 76rpx; box-sizing: border-box; padding: 0 17rpx; border: 1rpx solid #d9e2db; border-radius: 12rpx; background: #f8faf8; color: #33463b; font-size: 26rpx; }
.chat-input:focus { border-color: #9cbea7; background: #fff; }
.chat-send-button::after, .chat-upload-button::after { border: 0; }
.chat-send-button[disabled], .chat-upload-button[disabled] { opacity: .65; }
.composer-footer { justify-content: space-between; gap: 8rpx; margin-top: 7rpx; color: #a2ada6; font-size: 10rpx; }
.composer-footer text:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.loading-bar { position: fixed; z-index: 27; right: 22rpx; bottom: calc(238rpx + env(safe-area-inset-bottom)); left: 22rpx; display: flex; align-items: center; justify-content: center; gap: 9rpx; box-sizing: border-box; min-height: 52rpx; padding: 8rpx 13rpx; border: 1rpx solid #efd6c8; border-radius: 11rpx; background: #fff8f4; color: #9e6b58; font-size: 12rpx; box-shadow: 0 8rpx 19rpx rgba(111, 71, 54, .1); }
.loading-bar view:last-child { display: flex; min-width: 0; flex-direction: column; gap: 2rpx; }
.loading-title { color: #875541; font-size: 13rpx; font-weight: 850; }
.loading-spinner { width: 19rpx; height: 19rpx; border: 3rpx solid #f1dcd2; border-top-color: var(--orange); border-radius: 50%; animation: loading-spin .8s linear infinite; }

.bottom-actions { position: fixed; z-index: 28; right: 0; bottom: 0; left: 0; display: flex; align-items: center; justify-content: space-around; box-sizing: border-box; min-height: 88rpx; padding: 9rpx 24rpx calc(9rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #dfe7e1; background: rgba(248, 250, 248, .98); backdrop-filter: blur(18rpx); }
.bottom-actions button { display: flex; align-items: center; justify-content: center; gap: 5rpx; min-width: 132rpx; height: 52rpx; margin: 0; padding: 0 11rpx; border: 1rpx solid transparent; border-radius: 10rpx; background: transparent; color: #74827a; font-size: 14rpx; line-height: 52rpx; }
.bottom-actions button text { font-size: 21rpx; line-height: 1; }
.bottom-actions button::after { border: 0; }
.bottom-actions button:active { background: #edf3ee; }
.bottom-actions button[disabled] { opacity: .45; }
.bottom-actions .restart-action { border-color: #ead8ce; color: #a16b56; background: #fffaf7; }

.policy-mask { position: fixed; z-index: 50; inset: 0; display: flex; align-items: center; justify-content: center; padding: 32rpx; box-sizing: border-box; background: rgba(31, 44, 36, .62); }
.policy-dialog { width: 100%; max-height: 80vh; overflow: hidden; border: 1rpx solid #dce6df; border-radius: 17rpx; background: #fff; box-shadow: 0 22rpx 55rpx rgba(22, 38, 29, .3); }
.policy-dialog-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10rpx; padding: 18rpx 20rpx 14rpx; border-bottom: 1rpx solid #e8eee9; }
.policy-dialog-head view { display: flex; flex-direction: column; gap: 5rpx; }
.policy-dialog-head view text:last-child { color: var(--ink); font-size: 22rpx; font-weight: 850; }
.policy-dialog-head>text { color: var(--orange); font-size: 12rpx; }
.policy-dialog-title { display: block; padding: 16rpx 20rpx 7rpx; color: var(--ink); font-size: 25rpx; font-weight: 850; }
.policy-dialog-copy { width: 100%; height: 270rpx; box-sizing: border-box; padding: 0 20rpx 16rpx; }
.policy-dialog-copy text { color: #69776f; font-size: 15rpx; line-height: 1.7; }
.policy-dialog-actions { display: flex; gap: 9rpx; padding: 13rpx 20rpx calc(16rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #e8eee9; background: #fbfcfb; }
.policy-dialog-actions button { flex: 1; height: 66rpx; margin: 0; border-radius: 10rpx; font-size: 16rpx; font-weight: 850; line-height: 66rpx; }
.policy-dialog-actions button::after { border: 0; }
.policy-cancel { border: 1rpx solid #dce5de; background: #fff; color: #7c8982; }
.policy-confirm { background: #354b40; color: #fff; }

@keyframes thinking-enter { from { opacity: 0; transform: translateY(8rpx); } to { opacity: 1; transform: translateY(0); } }
@keyframes thinking-dot-bounce { 0%, 60%, 100% { opacity: .35; transform: translateY(0) scale(.85); } 30% { opacity: 1; transform: translateY(-4rpx) scale(1); } }
@keyframes loading-spin { to { transform: rotate(360deg); } }
</style>
