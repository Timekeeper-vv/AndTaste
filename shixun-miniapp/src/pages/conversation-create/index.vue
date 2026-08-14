<template>
  <view class="page chat-experience">
    <scroll-view class="chat" scroll-y :scroll-into-view="scrollIntoView" scroll-with-animation>
      <view class="workspace-intro">
        <view class="workspace-intro-top"><view class="online-mark"><view class="online-dot" /><text>AI 工作台</text></view><text class="workspace-ref">{{ sessionId ? `项目 ${sessionId}` : '新项目' }}</text></view>
        <text class="workspace-title">把灵感说出来，剩下的交给我</text>
        <text class="workspace-subtitle">我会帮你整理产品方向、生成视觉，并继续推进三视图、3D 和打样。</text>
        <view v-if="selectedProduct || material" class="brief-strip">
          <view v-if="selectedProduct" class="brief-chip"><text>产品</text><text>{{ selectedProduct.name }}</text></view>
          <view v-if="material" class="brief-chip"><text>材质</text><text>{{ material }}</text></view>
          <view v-if="mode" class="brief-chip muted"><text>{{ mode === 'image' ? '参考图' : '文字灵感' }}</text></view>
        </view>
      </view>

      <AiGeneratedNotice class="ai-disclosure" compact description="对话建议、提示词和后续生成的图片、三视图、3D 原型均可能由人工智能生成，仅供创作参考，商业使用前请人工复核。" />

      <view v-for="item in messages" :id="`message-${item.id}`" :key="item.id" class="message-row" :class="item.role">
        <view v-if="item.role === 'assistant'" class="message-avatar assistant-avatar">之</view>
        <view class="message-content">
          <view class="message-meta"><text>{{ item.role === 'assistant' ? '之间智造' : '我' }}</text><text v-if="item.role === 'assistant'">AI 助手</text></view>
          <view class="bubble"><text>{{ item.text }}</text></view>
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

      <view v-if="phase === 'result'" class="output-surface">
        <view class="output-header"><view><text class="surface-kicker">IMAGE OUTPUT</text><text class="surface-title">产品视觉已完成</text></view><view class="output-status"><view class="status-check">✓</view><text>已保存</text></view></view>
        <view class="visual-frame"><image v-if="previewUrl" class="result-image" :src="previewUrl" mode="aspectFill" @tap="previewImage" /><view v-else class="result-placeholder"><text>{{ selectedProduct?.mark || '作' }}</text><text>作品已保存到作品库</text></view><view class="visual-badge">AI 生成</view></view>
        <view class="output-info"><view><text>{{ selectedProduct?.name || '文创产品' }}</text><text>{{ material || '材质待定' }} · {{ mode === 'image' ? '参考图改造' : '文字生图' }}</text></view><text class="output-open" @tap="previewImage">查看大图 ›</text></view>
        <view v-if="refiningImage" class="refinement-panel"><view class="refinement-heading"><view><text class="surface-kicker">REFINE THIS IMAGE</text><text>告诉我哪里不满意</text></view><text class="refinement-close" @tap="cancelRefinement">×</text></view><textarea v-model="refinementNote" maxlength="500" auto-height class="text-input refinement-input" placeholder="例如：保留主体和构图，把边缘改得更简洁，去掉文字。" /><view class="input-foot"><text>{{ refinementNote.length }}/500</text><button class="dark-button" :disabled="!refinementNote.trim() || busy" :loading="busy" @tap="regenerateWithRefinement">基于当前图重新生成</button></view></view>
        <view v-else class="output-actions"><view class="output-action primary" @tap="generateMultiView"><view class="action-icon">观</view><view><text>生成三视图</text><text>补全结构视角</text></view><text class="action-arrow">›</text></view><view class="output-action" @tap="startRefinement"><view class="action-icon warm">改</view><view><text>不满意，继续修改</text><text>基于当前图再生成</text></view><text class="action-arrow">›</text></view><view class="output-action" @tap="generateModel"><view class="action-icon dark">3D</view><view><text>单图生成 3D</text><text>直接创建产品原型</text></view><text class="action-arrow">›</text></view><view class="output-action" @tap="openCommercial"><view class="action-icon gold">样</view><view><text>申请打样 / 商品化</text><text>提交给运营报价</text></view><text class="action-arrow">›</text></view></view>
      </view>

      <view v-if="phase === 'multiview'" class="output-surface">
        <view class="output-header"><view><text class="surface-kicker">MULTI-VIEW OUTPUT</text><text class="surface-title">三视图已完成</text></view><view class="output-status"><view class="status-check">✓</view><text>3 张已保存</text></view></view>
        <text class="surface-note">正面、侧面和背面将保持同一产品主体，可直接交给 3D 建模。</text>
        <view class="view-grid"><view v-for="item in multiviewImages" :key="item.assetId" class="view-card"><image v-if="imageUrl(item)" :src="imageUrl(item)" mode="aspectFill" /><view v-else class="view-placeholder"><text>{{ item.label }}</text><text>已保存</text></view><view class="view-label"><text>{{ item.label }}</text><text>已生成</text></view></view></view>
        <button class="dark-button full-button" :loading="busy" @tap="generateModel">用三视图生成 3D 模型 <text>›</text></button>
        <button class="outline-button full-button" @tap="openCommercial">先申请打样 / 商品化</button>
      </view>

      <view v-if="phase === 'model'" class="output-surface">
        <view class="output-header"><view><text class="surface-kicker">3D PROTOTYPE</text><text class="surface-title">{{ modelTaskTitle }}</text></view><view class="model-state" :class="{ done: isModelTaskSucceeded, failed: isModelTaskFailed }">{{ isModelTaskSucceeded ? '完成' : isModelTaskFailed ? '失败' : '处理中' }}</view></view>
        <view class="model-summary"><view class="model-mark">3D</view><view><text>{{ modelTaskDescription }}</text><text>{{ modelTaskDetail }}</text></view></view>
        <view v-if="modelTask" class="model-progress"><view class="progress-row"><text>建模进度</text><text>{{ normalizedModelProgress }}%</text></view><view class="model-progress-track"><view class="model-progress-value" :style="{ width: `${normalizedModelProgress}%` }" /></view></view>
        <text v-if="modelTask?.errorMessage" class="model-error">{{ modelTask.errorMessage }}</text>
        <button v-if="modelTask && !isModelTaskTerminal" class="outline-button full-button" :loading="modelRefreshing" @tap="refreshModelTask">刷新进度</button>
        <button v-if="isModelTaskFailed" class="dark-button full-button" :loading="busy" @tap="generateModel">重新提交 3D 建模</button>
        <button class="dark-button full-button" @tap="goWorks">{{ isModelTaskSucceeded ? '查看已完成的 3D 作品' : '查看我的作品' }}</button>
        <button class="outline-button full-button" @tap="openCommercial">申请打样 / 商品化</button>
      </view>

      <view id="bottom-anchor" class="bottom-anchor" />
    </scroll-view>

    <view v-if="busy" class="loading-bar"><view class="loading-spinner" aria-hidden="true" /><view><text class="loading-title">之间正在处理</text><text>{{ busyMessage }}</text></view></view>

    <view class="composer-dock">
      <view class="composer-context"><view class="context-live" /><text>{{ chatStageLabel }}</text><text v-if="selectedProduct" class="context-product">· {{ selectedProduct.name }}</text><text v-if="chatSending" class="context-working">处理中</text></view>
      <scroll-view v-if="chatQuickReplies.length" scroll-x class="quick-reply-list" :show-scrollbar="false"><view class="quick-reply-track"><view v-for="item in chatQuickReplies" :key="`${item.type}-${item.value}-${item.label}`" class="quick-reply" :class="{ confirm: item.type === 'confirm_generate', secondary: item.type === 'add_detail', disabled: busy || chatSending }" :aria-label="item.label" @tap="handleQuickReply(item)"><text class="quick-reply-mark">{{ quickReplyMark(item.type) }}</text><text>{{ item.label }}</text></view></view></scroll-view>
      <view class="chat-input-row"><button class="chat-upload-button" :disabled="busy || chatSending" aria-label="上传灵感图片" @tap="pickInspirationImage">＋</button><input v-model="chatInput" class="chat-input" maxlength="1200" confirm-type="send" placeholder="描述你的灵感，或直接回答上面的问题" @confirm="submitChatInput" /><button class="chat-send-button" :class="{ ready: chatInput.trim() }" :disabled="!chatInput.trim() || busy || chatSending" aria-label="发送" @tap="submitChatInput">↑</button></view>
      <view class="composer-footer"><text>AI 生成内容 · 请在商业使用前人工复核</text><text>{{ chatInput.length }}/1200</text></view>
    </view>

    <view class="bottom-actions"><button v-if="canGoPrevious" :disabled="busy || saving" @tap="goPreviousStep"><text>‹</text>上一步</button><button @tap="goWorks"><text>▣</text>作品库</button><button class="restart-action" @tap="restart"><text>＋</text>重新开始</button></view>

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
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AiGeneratedNotice from '../../components/AiGeneratedNotice.vue'
import { getSelectionOptions, type SelectionOption } from '../../api/selection'
import {
  createConversation,
  createModel,
  createSeedreamMultiView,
  getConversation,
  getConversations,
  getTripoModelTask,
  optimizeImageEditPrompt,
  optimizeImagePrompt,
  saveConversationEvent,
  sendConversationChat,
  uploadReference,
  type ConversationSession,
  type ConversationQuickReply,
  type SeedreamMultiViewImage,
} from '../../api/creative'
import { apiUrl, createReferenceToImage, createTextToImage } from '../../api/client'
import { CREATIVE_POLICY_VERSION, getCreativePolicy, type CreativePolicyKey } from '../../utils/compliance'
import { requireSession } from '../../utils/session'

type Phase = 'mode' | 'product' | 'inspiration' | 'image' | 'material' | 'result' | 'multiview' | 'model'
type Mode = 'template' | 'text' | 'image'
interface Message { id: number; role: 'assistant' | 'user'; text: string }
interface ProductOption { key: string; name: string; mark: string; desc: string; process: string; categoryKey: string; categoryName: string; materials: MaterialOption[] }
interface MaterialOption { name: string; note: string; color: string }
interface ModelTask { jobId: number; status: string; progress: number; assetId?: number | null; previewUrl?: string; errorMessage?: string }

const modeOptions = [
  { key: 'template' as Mode, mark: '例', title: '没有灵感（看看示例）', desc: '浏览示例并了解创作方式' },
  { key: 'text' as Mode, mark: '字', title: '已有灵感（文字）', desc: '把你的想法、故事或需求告诉我' },
  { key: 'image' as Mode, mark: '图', title: '已有灵感（图片）', desc: '上传草图、照片或有权使用的参考图' },
]
const productOptions = ref<ProductOption[]>([])
const productKeyword = ref('')
const productCategory = ref('')
const catalogLoading = ref(false)

const phase = ref<Phase>('mode')
const canGoPrevious = computed(() => phase.value !== 'mode' && !busy.value && !saving.value)
const mode = ref<Mode | ''>('')
const selectedProduct = ref<ProductOption | null>(null)
const material = ref('')
const materialChoice = ref<'recommend' | string>('recommend')
const style = ref('国潮')
const purpose = ref('景区伴手礼')
const inspirationText = ref('')
const referencePath = ref('')
const referenceAssetId = ref<number | null>(null)
const sessionId = ref<number | null>(null)
const generatedAssetId = ref<number | null>(null)
const previewUrl = ref('')
const multiviewImages = ref<SeedreamMultiViewImage[]>([])
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
let sessionPromise: Promise<boolean> | null = null
const forceNewSession = ref(false)
const chatExperience = true
const chatInput = ref('')
const chatQuickReplies = ref<ConversationQuickReply[]>([])
const chatSending = ref(false)
const chatThinking = ref(false)
const thinkingLabel = ref('正在理解你的想法')
const awaitingGenerationConfirmation = ref(false)
const chatStage = ref('need_product')
const autoGenerationInFlight = ref(false)
const modelRefreshing = ref(false)
const referencePolicyConfirmed = ref(false)
const aiPolicyConfirmed = ref(false)
const threeDimensionalPolicyConfirmed = ref(false)
const policyDialog = ref<{ key: CreativePolicyKey; resolve: (confirmed: boolean) => void } | null>(null)
let modelPollTimer: ReturnType<typeof setTimeout> | null = null
let modelPollVersion = 0

const currentMaterials = computed(() => selectedProduct.value?.materials || [])
const categoryLabels: Record<string, string> = { food: '食品饮品', stationery: '文具纸品', souvenir: '景区文创', accessory: '饰品挂件', craft: '工艺收藏', daily: '日用生活', tableware: '餐饮器物', toy: '潮玩玩具', apparel: '服饰配件', precious: '贵金属' }
const categoryOrder = ['food', 'stationery', 'souvenir', 'accessory', 'craft', 'daily', 'tableware', 'toy', 'apparel', 'precious']
const productCatalogCategories = computed(() => {
  const names = new Map<string, string>()
  productOptions.value.forEach(item => names.set(item.categoryKey, categoryLabels[item.categoryKey] || item.categoryName || '其他'))
  return Array.from(names.entries())
    .map(([key, name]) => ({ key, name }))
    .sort((left, right) => {
      const leftIndex = categoryOrder.indexOf(left.key)
      const rightIndex = categoryOrder.indexOf(right.key)
      return (leftIndex < 0 ? 999 : leftIndex) - (rightIndex < 0 ? 999 : rightIndex) || left.name.localeCompare(right.name, 'zh-CN')
    })
})
const filteredProductOptions = computed(() => {
  const keyword = productKeyword.value.trim().toLowerCase()
  return productOptions.value.filter(item => {
    if (productCategory.value && item.categoryKey !== productCategory.value) return false
    if (!keyword) return true
    return `${item.name} ${item.desc} ${item.process} ${item.materials.map(material => material.name).join(' ')}`.toLowerCase().includes(keyword)
  })
})
const productCategoryName = computed(() => productCatalogCategories.value.find(item => item.key === productCategory.value)?.name || '')
const isFoodProduct = computed(() => selectedProduct.value?.categoryKey === 'food'
  || /食品|食用|曲奇|饼干|糕点|月饼|咖啡|饮品|茶|巧克力|糖果/.test(`${selectedProduct.value?.name || ''} ${material.value}`))
const prompt = computed(() => {
  const product = selectedProduct.value?.name || '文创产品'
  const source = inspirationText.value.trim() || `为${product}设计一套具有文化辨识度、适合量产打样的产品视觉`
  return `${source}。产品：${product}；材质：${material.value}；风格：${style.value}；配色由系统按产品和风格自动协调；用途：${purpose.value}。请考虑清晰轮廓、可生产结构、合理尺寸和适合商品展示的构图。`
})
const normalizedModelProgress = computed(() => Math.max(0, Math.min(100, Number(modelTask.value?.progress) || 0)))
const isModelTaskSucceeded = computed(() => modelTask.value?.status === 'succeeded')
const isModelTaskFailed = computed(() => modelTask.value?.status === 'failed')
const isModelTaskTerminal = computed(() => isModelTaskSucceeded.value || isModelTaskFailed.value)
const modelTaskTitle = computed(() => isModelTaskSucceeded.value ? '3D 模型已经生成' : isModelTaskFailed.value ? '3D 建模未完成' : '3D 建模正在生成')
const hasCompleteThreeViews = computed(() => {
  const available = new Set(multiviewImages.value.map(item => String(item?.view || '').toLowerCase()))
  return ['front', 'left', 'back'].every(view => available.has(view))
})
const modelInputLabel = computed(() => modelInputMode.value === 'multiview' ? '三视图建模' : '单图建模')
const modelTaskDescription = computed(() => isModelTaskSucceeded.value ? `${modelInputLabel.value}的 3D 原型已保存到作品库` : isModelTaskFailed.value ? `本次${modelInputLabel.value}失败，可回到产品图重新提交` : `正在进行${modelInputLabel.value}`)
const modelTaskDetail = computed(() => isModelTaskSucceeded.value ? '可以在作品库查看模型、评审并申请打样。' : isModelTaskFailed.value ? '失败原因已保留。检查产品图或三视图后可以再次发起建模。' : '本页面会自动刷新进度，离开后也会继续在作品库保存。')
const activePolicy = computed(() => getCreativePolicy(policyDialog.value?.key || 'ai-output'))
const chatStageLabel = computed(() => ({
  need_product: '先告诉我想做什么产品',
  need_inspiration: '再说说你的灵感，或上传参考图',
  need_material: '最后确认材质，不确定可以让我推荐',
  understanding: '我正在整理你的创作方向',
  confirm_before_image: '生成前确认一下，还有需要补充的吗？',
  need_additional_detail: '请补充你想保留、加强或避免的内容',
  ready_for_image: '信息已足够，准备生成产品图',
  template_unavailable: '没有灵感示例功能正在开发中',
  image_ready: '产品图已完成，可以继续落地',
  multiview_ready: '三视图已完成，可以生成 3D',
  model_running: '3D 原型正在生成',
  model_ready: '3D 原型已完成，可以申请打样',
}[chatStage.value] || '告诉我你的创作想法'))
function quickReplyMark(type: string) {
  return ({
    category: '类',
    product: '选',
    material: '材',
    upload: '图',
    text: '写',
    template: '例',
    confirm_generate: '出',
    add_detail: '改',
    multiview: '观',
    model: '3D',
    refine: '改',
    commercial: '样',
    works: '作',
  } as Record<string, string>)[type] || '→'
}

function addMessage(role: Message['role'], text: string) {
  messages.value.push({ id: ++messageId, role, text })
  void nextTick(() => { scrollIntoView.value = `message-${messageId}` })
}
function setChatThinking(active: boolean, label = '正在理解你的想法') {
  chatThinking.value = active
  if (active) {
    thinkingLabel.value = label
    void nextTick(() => { if (chatThinking.value) scrollIntoView.value = 'chat-thinking' })
  }
}
function thinkingLabelFor(action?: { type: string; value?: string; label?: string }, message = '') {
  const type = String(action?.type || '')
  if (type === 'category' || type === 'product') return '正在整理产品方向'
  if (type === 'material' || type === 'recommend_material') return '正在匹配材质与生产工艺'
  if (type === 'upload' || /图片|照片|草图|参考图/.test(message)) return '正在读取参考图片和主体特征'
  return '正在理解你的想法'
}
function setGenerationConfirmationReplies() {
  chatQuickReplies.value = [
    { label: '没有补充，开始生成', type: 'confirm_generate', value: 'confirm' },
    { label: '我还要补充', type: 'add_detail', value: '' },
  ]
}
function isGenerationConfirmationText(message: string) {
  const value = message.trim()
  return Boolean(value) && (/.*(没有|无|不需要|不用).*(补充|修改|添加|意见).*/.test(value)
    || /.*(直接|开始|确认).*(生成|出图).*/.test(value)
    || /^(没有|无|就这样)$/.test(value))
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

function applyChatBrief(brief: Record<string, any> | undefined) {
  if (!brief) return
  const product = productByValue(brief.productName, brief.productKey)
  if (product) selectedProduct.value = product
  if (brief.mode) mode.value = String(brief.mode) as Mode
  if (brief.inspiration && brief.inspirationSource !== 'image') inspirationText.value = String(brief.inspiration)
  if (brief.referenceAssetId) referenceAssetId.value = Number(brief.referenceAssetId) || referenceAssetId.value
  if (brief.material) {
    material.value = String(brief.material)
    materialChoice.value = brief.materialRecommended ? 'recommend' : material.value
  }
}

async function handleQuickReply(item: ConversationQuickReply) {
  if (busy.value || chatSending.value) return
  const type = String(item.type || '')
  if (type === 'upload') {
    await pickInspirationImage()
    return
  }
  if (type === 'multiview') {
    await generateMultiView()
    return
  }
  if (type === 'model') {
    await generateModel()
    return
  }
  if (type === 'commercial') {
    openCommercial()
    return
  }
  if (type === 'works') {
    goWorks()
    return
  }
  if (type === 'refine') {
    startRefinement()
    return
  }
  if (type === 'template') {
    showTemplateDeveloping()
    return
  }
  if (type === 'confirm_generate' || type === 'add_detail') {
    await sendChatTurn('', { type, value: String(item.value || ''), label: String(item.label || '') })
    return
  }
  if (type === 'text' && !String(item.value || '').trim()) {
    uni.showToast({ title: '请在下方输入框告诉我你的想法', icon: 'none' })
    return
  }
  const label = String(item.label || item.value || '').trim()
  // Keep structured selections out of the free-text slot. Otherwise a
  // product/material button can be misread as the user's inspiration.
  const message = type === 'text' ? label : ''
  await sendChatTurn(message, { type, value: String(item.value || ''), label })
}

async function submitChatInput() {
  const value = chatInput.value.trim()
  if (!value || busy.value || chatSending.value) return
  chatInput.value = ''
  await sendChatTurn(value)
}

async function sendChatTurn(message: string, action?: { type: string; value?: string; label?: string }) {
  if (!(await ensureSession()) || !sessionId.value || chatSending.value) return
  const visibleMessage = message.trim()
  const displayMessage = visibleMessage || String(action?.label || '').trim()
  if (displayMessage) addMessage('user', displayMessage)
  chatSending.value = true
  setChatThinking(true, thinkingLabelFor(action, visibleMessage))
  try {
    const result = await sendConversationChat(sessionId.value, { message: visibleMessage, action })
    applyChatBrief(result.brief)
    chatStage.value = String(result.stage || 'understanding')
    chatQuickReplies.value = Array.isArray(result.quickReplies) ? result.quickReplies : []
    if (result.assistantText) addMessage('assistant', result.assistantText)
    // Let the assistant reply settle in the transcript before showing the
    // separate, longer-running image-generation status.
    setChatThinking(false)
    const explicitlyConfirmed = action?.type === 'confirm_generate' || isGenerationConfirmationText(visibleMessage)
    const additionalDetailRequired = result.stage === 'need_additional_detail'
    const confirmationRequired = !additionalDetailRequired && (Boolean(result.generationConfirmationRequired) || result.stage === 'confirm_before_image')
    awaitingGenerationConfirmation.value = confirmationRequired
    if (confirmationRequired && !chatQuickReplies.value.length) setGenerationConfirmationReplies()
    if (result.readyToGenerate && explicitlyConfirmed && !generatedAssetId.value && phase.value !== 'result' && !autoGenerationInFlight.value) {
      awaitingGenerationConfirmation.value = false
      autoGenerationInFlight.value = true
      try {
        await generateProductImage()
      } finally {
        autoGenerationInFlight.value = false
      }
    } else if (result.readyToGenerate && !explicitlyConfirmed && !generatedAssetId.value && phase.value !== 'result') {
      // Keep a hard client-side guard when an older server response still
      // reports ready=true without a user confirmation action.
      awaitingGenerationConfirmation.value = true
      chatStage.value = 'confirm_before_image'
      setGenerationConfirmationReplies()
      addMessage('assistant', '生成前确认一下，还有需要补充的吗？没有的话点击“没有补充，开始生成”。')
    }
  } catch (error: any) {
    setChatThinking(false)
    uni.showModal({ title: '对话暂时中断', content: error?.message || '请稍后重试，当前已输入内容会保留。', showCancel: false })
  } finally {
    setChatThinking(false)
    chatSending.value = false
  }
}

async function goPreviousStep() {
  if (!canGoPrevious.value) return
  const from = phase.value
  const to = previousPhase(from)
  if (!to) return
  phase.value = to
  addMessage('assistant', '已回到上一步，之前填写和上传的内容都已保留，可以继续修改。')
  try {
    await saveEvent('navigation', 'previous_step', { from, to })
  } catch {
    // Local progress remains available even if the audit event cannot be saved.
  }
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
  const params: string[] = []
  if (generatedAssetId.value) params.push(`assetId=${encodeURIComponent(String(generatedAssetId.value))}`)
  if (selectedProduct.value?.key) params.push(`productKey=${encodeURIComponent(selectedProduct.value.key)}`)
  if (selectedProduct.value?.name) params.push(`productName=${encodeURIComponent(selectedProduct.value.name)}`)
  if (material.value) params.push(`material=${encodeURIComponent(material.value)}`)
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

function confirmCreativePolicyInPage(key: CreativePolicyKey): Promise<boolean> {
  // Some iOS/DevTools combinations do not render uni.showModal after a long
  // scroll interaction. Use a page-owned layer for the creation flow so the
  // user always sees the required consent action.
  if (policyDialog.value) return Promise.resolve(false)
  return new Promise(resolve => { policyDialog.value = { key, resolve } })
}
function resolvePolicyDialog(confirmed: boolean) {
  const dialog = policyDialog.value
  policyDialog.value = null
  dialog?.resolve(confirmed)
}

function productMark(name: string, category: string) {
  if (name.includes('冰箱贴')) return '贴'
  if (name.includes('徽章')) return '章'
  if (name.includes('钥匙扣')) return '扣'
  if (name.includes('书签')) return '签'
  if (name.includes('杯')) return '杯'
  if (name.includes('包') || name.includes('袋')) return '包'
  if (name.includes('公仔')) return '偶'
  if (name.includes('首饰') || name.includes('项链') || name.includes('耳')) return '饰'
  return ({ food: '食', stationery: '文', daily: '用', toy: '玩', tableware: '器', souvenir: '礼', accessory: '饰', apparel: '衣', craft: '艺', precious: '金' } as Record<string, string>)[category] || '作'
}

function materialColor(material: string) {
  if (/金属|合金|贵金属|马口铁|金箔|溅射金/.test(material)) return 'linear-gradient(145deg,#ead29d,#8a6a45)'
  if (/陶瓷|骨瓷|琉璃|玻璃|搪瓷/.test(material)) return 'linear-gradient(145deg,#fffdf3,#a7c8ba)'
  if (/亚克力|PC|PVC|ABS|硅胶|塑胶|树脂|搪胶/.test(material)) return 'linear-gradient(145deg,#f4fbfc,#97c2c7)'
  if (/毛绒|布艺|帆布|棉|毛毡|纤维|涤纶/.test(material)) return 'linear-gradient(145deg,#f4e7d5,#bc9776)'
  if (/木|竹|纸|杜邦/.test(material)) return 'linear-gradient(145deg,#f1e2c8,#a9835b)'
  return 'linear-gradient(145deg,#e7ece4,#91aa9a)'
}

function productFromSelection(option: SelectionOption): ProductOption {
  return {
    key: option.optionKey,
    name: option.name,
    mark: productMark(option.name, option.categoryKey),
    desc: option.subtitle || option.description,
    process: option.process,
    categoryKey: option.categoryKey,
    categoryName: categoryLabels[option.categoryKey] || option.categoryName || '其他',
    materials: [{ name: option.material, note: `${option.process} · ${option.specification}`, color: materialColor(option.material) }],
  }
}

async function loadProductCatalog() {
  if (catalogLoading.value) return
  catalogLoading.value = true
  try {
    const options = await getSelectionOptions({ size: 300 })
    productOptions.value = (Array.isArray(options) ? options : []).map(productFromSelection)
    if (!messages.value.length) setInitialChatReplies()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '选品目录暂不可用，请稍后重试', icon: 'none' })
  } finally {
    catalogLoading.value = false
  }
}

function updateProductKeyword(event: any) { productKeyword.value = String(event?.detail?.value || '') }
function productCountForCategory(categoryKey: string) { return productOptions.value.filter(item => item.categoryKey === categoryKey).length }
function categoryMark(categoryKey: string) {
  return ({ food: '食', stationery: '文', souvenir: '礼', accessory: '饰', craft: '艺', daily: '用', tableware: '器', toy: '玩', apparel: '衣', precious: '金' } as Record<string, string>)[categoryKey] || '作'
}

function isNotFound(error: any) { return Number(error?.statusCode) === 404 || /not found|不存在|找不到/i.test(String(error?.message || '')) }

function productByValue(productType?: string, productKey?: string) {
  return productOptions.value.find(item => item.key === productKey || item.name === productType) || null
}

function resetViewState() {
  stopModelPolling()
  phase.value = 'mode'
  mode.value = ''
  selectedProduct.value = null
  material.value = ''
  materialChoice.value = 'recommend'
  style.value = '国潮'
  purpose.value = '景区伴手礼'
  inspirationText.value = ''
  referencePath.value = ''
  referenceAssetId.value = null
  generatedAssetId.value = null
  previewUrl.value = ''
  multiviewImages.value = []
  modelInputMode.value = 'single'
  refiningImage.value = false
  refinementNote.value = ''
  modelTask.value = null
  referencePolicyConfirmed.value = false
  aiPolicyConfirmed.value = false
  threeDimensionalPolicyConfirmed.value = false
  messages.value = []
  messageId = 0
  chatQuickReplies.value = []
  chatStage.value = 'need_product'
  chatInput.value = ''
  awaitingGenerationConfirmation.value = false
  setChatThinking(false)
  autoGenerationInFlight.value = false
}

function restoreEvent(event: any) {
  const payload = event?.payload || {}
  switch (String(event?.eventType || '')) {
    case 'mode_selected':
      mode.value = payload.mode || mode.value
      break
    case 'product_selected':
      selectedProduct.value = productByValue(payload.productType, payload.productKey) || selectedProduct.value
      material.value = ''
      materialChoice.value = 'recommend'
      break
    case 'text_inspiration_submitted':
      inspirationText.value = String(payload.inspirationText || '')
      break
    case 'image_inspiration_uploaded':
      referenceAssetId.value = Number(payload.inputAssetId) || null
      break
    case 'material_selected':
      material.value = String(payload.material || payload.materialName || material.value)
      materialChoice.value = payload.recommended ? 'recommend' : material.value
      break
    case 'style_selected':
    case 'purpose_selected':
    case 'creative_direction_confirmed':
    case 'creative_direction_auto_confirmed':
      if (payload.style) style.value = String(payload.style)
      if (payload.purpose) purpose.value = String(payload.purpose)
      if (payload.inspirationText) inspirationText.value = String(payload.inspirationText)
      break
    case 'image_generated':
      generatedAssetId.value = Number(payload.generatedAssetId) || generatedAssetId.value
      previewUrl.value = imageUrl({ previewUrl: payload.previewUrl })
      break
    case 'image_refined':
      generatedAssetId.value = Number(payload.generatedAssetId) || generatedAssetId.value
      previewUrl.value = imageUrl({ previewUrl: payload.previewUrl })
      refinementNote.value = ''
      break
    case 'multiview_generated':
      multiviewImages.value = Array.isArray(payload.images) ? payload.images : []
      break
    case 'model_submitted':
      modelInputMode.value = payload.multiview ? 'multiview' : 'single'
      setModelTask(payload)
      break
    case 'model_completed':
      setModelTask({ ...payload, status: 'succeeded', progress: 100 })
      break
    case 'model_failed':
      setModelTask({ ...payload, status: 'failed' })
      break
    case 'chat_state':
      applyChatBrief(payload)
      break
    default:
      break
  }
}

function restoreMessages(events: any[]) {
  messages.value = []
  messageId = 0
  addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  const hasChatTranscript = events.some(event => ['chat_user_message', 'chat_assistant_message'].includes(String(event?.eventType || '')))
  const legacyConversationEvents = new Set([
    'mode_selected', 'product_selected', 'text_inspiration_submitted',
    'image_inspiration_uploaded', 'image_inspiration_confirmed',
    'material_selected', 'creative_direction_confirmed', 'creative_direction_auto_confirmed',
  ])
  for (const event of events) {
    if (hasChatTranscript && legacyConversationEvents.has(String(event?.eventType || ''))) continue
    const payload = event?.payload || {}
    switch (String(event?.eventType || '')) {
      case 'mode_selected':
        addMessage('user', modeOptions.find(item => item.key === payload.mode)?.title || String(payload.modeName || '已选择创作方式'))
        addMessage('assistant', '好，我们先确定产品方向。你想把它做成什么？')
        break
      case 'product_selected': {
        const product = productByValue(payload.productType, payload.productKey)
        if (product) addMessage('user', product.name)
        if (mode.value === 'template') addMessage('assistant', `${product?.name || '这个产品'}很适合先做一版。现在选材质，我会把工艺约束一起带进提示词。`)
        else if (mode.value === 'text') addMessage('assistant', '收到。把你已有的文字灵感告诉我，不用写成复杂提示词。')
        else addMessage('assistant', '收到。请上传一张你有权使用的灵感图片，我会保留主体并优化成产品视觉。')
        break
      }
      case 'text_inspiration_submitted':
        if (payload.inspirationText) addMessage('user', String(payload.inspirationText))
        addMessage('assistant', '我记下了这段灵感。接下来选择材质，我会把材质、结构和生产限制一起考虑。')
        break
      case 'image_inspiration_uploaded':
        addMessage('user', '已上传一张灵感图片')
        addMessage('assistant', '图片已收到。你希望它用什么材质？')
        break
      case 'material_selected':
        addMessage('user', String(payload.material || payload.materialName || material.value))
        addMessage('assistant', '材质已确认，我会直接按产品和灵感生成产品图。')
        break
      case 'creative_direction_auto_confirmed':
        addMessage('assistant', `我会根据你的灵感自动匹配${payload.material || material.value}，现在直接生成产品图。`)
        break
      case 'image_generated':
        addMessage('assistant', '产品视觉已经生成并保存。下一步可以补全四视图、生成 3D，或直接提交商品化申请。')
        break
      case 'image_refined':
        addMessage('user', `补充修改：${payload.refinementNote || '基于当前图重新生成'}`)
        addMessage('assistant', '新的产品视觉已经生成，旧版本仍保留在作品库。你可以继续修改，或进入四视图和 3D。')
        break
      case 'multiview_generated':
        addMessage('assistant', '四个角度都已保存。现在可以把它们一起交给 3D 建模，结构会比单张图更完整。')
        break
      case 'model_submitted':
        addMessage('assistant', '3D 建模任务已提交，完成后会出现在作品库。你可以在那里预览、评审并申请打样。')
        break
      case 'model_completed':
        addMessage('assistant', '3D 模型已经生成并保存到作品库，可以继续评审、申请打样或提交商品化报价。')
        break
      case 'model_failed':
        addMessage('assistant', '3D 建模没有完成，失败原因已保存。可以检查产品图后重新提交。')
        break
      case 'chat_user_message':
        if (payload.message) addMessage('user', String(payload.message))
        else if (payload.action?.label) addMessage('user', String(payload.action.label))
        break
      case 'chat_assistant_message':
        if (payload.text) addMessage('assistant', String(payload.text))
        if (Array.isArray(payload.quickReplies)) chatQuickReplies.value = payload.quickReplies
        if (payload.stage) chatStage.value = String(payload.stage)
        if (!generatedAssetId.value && (payload.generationConfirmationRequired || (payload.readyToGenerate && payload.generationConfirmed !== true))) {
          awaitingGenerationConfirmation.value = true
          chatStage.value = 'confirm_before_image'
          if (!chatQuickReplies.value.length) setGenerationConfirmationReplies()
        }
        break
      default:
        break
    }
  }
}

function restorePhase(events: any[]) {
  phase.value = 'mode'
  for (const event of events) {
    switch (String(event?.eventType || '')) {
      case 'mode_selected': phase.value = 'product'; break
      case 'product_selected': phase.value = mode.value === 'template' ? 'material' : mode.value === 'text' ? 'inspiration' : 'image'; break
      case 'text_inspiration_submitted': phase.value = 'material'; break
      case 'image_inspiration_uploaded': phase.value = 'image'; break
      case 'image_inspiration_confirmed': phase.value = 'material'; break
      case 'material_selected': phase.value = 'material'; break
      case 'creative_direction_confirmed':
      case 'creative_direction_auto_confirmed': phase.value = 'material'; break
      case 'image_generated': phase.value = 'result'; break
      case 'image_refined': phase.value = 'result'; break
      case 'multiview_generated': phase.value = 'multiview'; break
      case 'model_submitted': phase.value = 'model'; break
      case 'model_completed': phase.value = 'model'; break
      case 'model_failed': phase.value = 'model'; break
      case 'chat_assistant_message':
        if (event?.payload?.stage) chatStage.value = String(event.payload.stage)
        if (!generatedAssetId.value && (event?.payload?.generationConfirmationRequired || (event?.payload?.readyToGenerate && event?.payload?.generationConfirmed !== true))) {
          chatStage.value = 'confirm_before_image'
          awaitingGenerationConfirmation.value = true
        } else if (event?.payload?.readyToGenerate) {
          chatStage.value = 'ready_for_image'
        }
        break
      default: break
    }
  }
}

async function restoreLatestSession() {
  const sessions = await getConversations()
  const latest = sessions.find(item => String(item.status || 'draft') !== 'archived')
  if (!latest?.id) return false
  try {
    const detail = await getConversation(latest.id)
    const events = Array.isArray(detail.events) ? detail.events : []
    resetViewState()
    sessionId.value = Number(detail.id)
    for (const event of events) restoreEvent(event)
    restoreMessages(events)
    restorePhase(events)
    return Boolean(sessionId.value)
  } catch (error) {
    // A session may belong to an old deployment or have been removed. Do not
    // expose a raw 404 toast; start a fresh draft instead.
    if (isNotFound(error)) return false
    throw error
  }
}

async function ensureSession() {
  if (sessionPromise) return sessionPromise
  sessionPromise = (async () => {
    if (!requireSession()) return false
    if (sessionId.value) return true
    try {
      if (!forceNewSession.value) {
        try { if (await restoreLatestSession()) return true } catch (error: any) { if (!isNotFound(error)) throw error }
      }
      const session = await createConversation()
      sessionId.value = Number(session.id)
      resetViewState()
      return Boolean(sessionId.value)
    } catch (error: any) {
      uni.showToast({ title: isNotFound(error) ? '创作服务暂时不可用，请稍后再试' : (error?.message || '无法建立创作会话'), icon: 'none' })
      return false
    }
  })()
  const result = await sessionPromise
  sessionReady.value = result
  return result
}
async function saveEvent(step: string, eventType: string, payload: Record<string, any>) {
  if (!(await ensureSession()) || !sessionId.value) return
  saving.value = true
  try { await saveConversationEvent(sessionId.value, { step, eventType, payload }) }
  catch {
    // Conversation history improves continuity, but must never interrupt a
    // user's product selection or AI generation when a background save fails.
  }
  finally { saving.value = false }
}
async function chooseMode(value: Mode) {
  if (busy.value) return
  if (value === 'template') {
    showTemplateDeveloping()
    return
  }
  mode.value = value
  addMessage('user', selectedModeTitle())
  addMessage('assistant', '好，我们先确定产品方向。你想把它做成什么？')
  await saveEvent('mode', 'mode_selected', { mode: value, modeName: selectedModeTitle() })
  phase.value = 'product'
}
async function chooseProduct(value: ProductOption) {
  selectedProduct.value = value
  material.value = ''
  materialChoice.value = 'recommend'
  addMessage('user', value.name)
  await saveEvent('product', 'product_selected', { productKey: value.key, productType: value.name, process: value.process })
  if (mode.value === 'template') {
    addMessage('assistant', `${value.name}很适合先做一版。现在选材质，我会把工艺约束一起带进提示词。`)
    phase.value = 'material'
  } else if (mode.value === 'text') {
    addMessage('assistant', '收到。把你已有的文字灵感告诉我，不用写成复杂提示词。')
    phase.value = 'inspiration'
  } else {
    addMessage('assistant', '收到。请上传一张你有权使用的灵感图片，我会保留主体并优化成产品视觉。')
    phase.value = 'image'
  }
}
async function submitTextInspiration() {
  if (!inspirationText.value.trim()) return
  addMessage('user', inspirationText.value.trim())
  await saveEvent('inspiration', 'text_inspiration_submitted', { productType: selectedProduct.value?.name, inspirationText: inspirationText.value.trim() })
  const recommendation = recommendedMaterial()
  if (!recommendation) {
    uni.showToast({ title: '当前产品暂时没有可用材质，请稍后重试', icon: 'none' })
    return
  }
  material.value = recommendation.name
  materialChoice.value = 'recommend'
  style.value = recommendedStyle()
  purpose.value = recommendedPurpose()
  await saveEvent('material', 'material_selected', { productType: selectedProduct.value?.name, material: recommendation.name, materialNote: recommendation.note, recommended: true, autoSelected: true })
  addMessage('assistant', `我会根据你的灵感自动匹配${recommendation.name}，现在直接生成产品图。`)
  await generateProductImage()
}
async function pickInspirationImage() {
  if (busy.value) {
    uni.showToast({ title: '图片正在上传或生成中，请稍候', icon: 'none' })
    return
  }
  if (!referencePolicyConfirmed.value) {
    const confirmed = await confirmCreativePolicyInPage('reference-materials')
    if (!confirmed) return
    referencePolicyConfirmed.value = true
    await saveEvent('compliance', 'policy_notice_confirmed', { policyKey: 'reference-materials', policyVersion: CREATIVE_POLICY_VERSION })
  }
  // chooseImage is the protected API itself. Calling it directly lets WeChat
  // invoke the app-level privacy resolver and then resume this exact action.
  // A separate requirePrivacyAuthorize call can consume the tap without
  // opening the album on some base-library versions.
  uni.chooseImage({ count: 1, sizeType: ['compressed'], sourceType: ['album'], success: (result) => {
    const path = result.tempFilePaths?.[0]
    if (!path) {
      uni.showToast({ title: '没有读取到图片，请重新选择', icon: 'none' })
      return
    }
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
  busy.value = true
  try {
    const result = await uploadReference(path)
    const id = Number(result?.assetId)
    if (!Number.isFinite(id) || id <= 0) throw new Error('图片上传成功但没有返回作品编号')
    referenceAssetId.value = id
    await saveEvent('inspiration', 'image_inspiration_uploaded', { productType: selectedProduct.value?.name, inputAssetId: id, fileType: 'image' })
    uni.showToast({ title: '图片已留存', icon: 'success' })
    // The upload is complete before the chat turn starts. Release the upload
    // lock so a ready image conversation can enter the normal generation path.
    busy.value = false
    await sendChatTurn('我已上传灵感图片', { type: 'image', value: String(id), label: '已上传灵感图片' })
  } catch (error: any) {
    referencePath.value = ''
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
  style.value = recommendedStyle()
  purpose.value = recommendedPurpose()
  addMessage('assistant', '材质已确认，现在直接生成产品图。')
  await generateProductImage()
}
function recommendedStyle() {
  const context = `${selectedProduct.value?.name || ''} ${inspirationText.value}`
  if (/亲子|儿童|宝宝|卡通/.test(context)) return '亲子卡通'
  if (/敦煌|飞天|壁画/.test(context)) return '敦煌'
  if (/山水|江南|水墨|园林/.test(context)) return '青绿山水'
  if (/简约|极简|现代|科技/.test(context)) return '现代极简'
  return '国潮'
}
function recommendedPurpose() {
  const context = `${selectedProduct.value?.name || ''} ${inspirationText.value}`
  if (/博物馆|文物|展馆/.test(context)) return '博物馆文创'
  if (/企业|品牌|活动|客户/.test(context)) return '企业礼赠'
  if (/亲子|儿童|宝宝/.test(context)) return '亲子纪念'
  if (/收藏|纪念/.test(context)) return '个人收藏'
  return '景区伴手礼'
}
async function generateProductImage() {
  if (busy.value) {
    uni.showToast({ title: '图片正在生成，请不要重复提交', icon: 'none' })
    return
  }
  if (!selectedProduct.value || !material.value) {
    uni.showModal({ title: '暂时不能生成', content: '请先完成产品和材质选择，再生成产品图。', showCancel: false })
    return
  }
  if (!aiPolicyConfirmed.value) {
    const confirmed = await confirmCreativePolicyInPage('ai-output')
    if (!confirmed) {
      uni.showToast({ title: '已取消本次 AI 生成', icon: 'none' })
      return
    }
    aiPolicyConfirmed.value = true
    await saveEvent('compliance', 'policy_notice_confirmed', { policyKey: 'ai-output', policyVersion: CREATIVE_POLICY_VERSION })
  }
  busy.value = true
  busyMessage.value = '正在保存创作参数…'
  try {
    await saveEvent('summary', 'generation_started', { productType: selectedProduct.value.name, material: material.value, prompt: prompt.value })
    let generationPrompt = prompt.value
    if (mode.value !== 'image') {
      busyMessage.value = '正在整理产品提示词…'
      try {
        const optimized = await optimizeImagePrompt({ prompt: prompt.value, provider: 'tripo', productCategory: selectedProduct.value.name, material: material.value })
        if (String(optimized?.prompt || '').trim()) {
        generationPrompt = String(optimized.prompt).trim()
        await saveEvent('summary', 'prompt_optimized', { productType: selectedProduct.value.name, material: material.value, sourcePrompt: prompt.value, optimizedPrompt: generationPrompt })
        }
      } catch {
        await saveEvent('summary', 'prompt_optimization_fallback', { productType: selectedProduct.value.name, material: material.value, sourcePrompt: prompt.value, reason: 'optimization_unavailable' })
      }
    }
    let result: any
    if (mode.value === 'image') {
      if (!referenceAssetId.value) throw new Error('参考图片还没有保存完成，请重新上传后再生成')
      busyMessage.value = '正在依据参考图生成产品视觉，预计需要 1-3 分钟…'
      result = await createReferenceToImage({ title: `${selectedProduct.value.name} · 对话创作`, prompt: generationPrompt, inputAssetId: referenceAssetId.value, productKey: selectedProduct.value.key, productCategory: selectedProduct.value.name, material: material.value })
    } else {
      busyMessage.value = '正在调用之间大模型生成产品视觉，预计需要 1-3 分钟…'
      result = await createTextToImage({ title: `${selectedProduct.value.name} · 对话创作`, prompt: generationPrompt, rawPrompt: inspirationText.value || prompt.value, scene: purpose.value, productType: selectedProduct.value.name, productKey: selectedProduct.value.key, productCategory: selectedProduct.value.name, material: material.value })
    }
    const assetId = Number(result?.assetId || result?.id)
    if (!Number.isFinite(assetId) || assetId <= 0) throw new Error('产品图没有保存成功，请重新生成')
    generatedAssetId.value = assetId
    previewUrl.value = imageUrl(result)
    await saveEvent('image', 'image_generated', { productType: selectedProduct.value.name, material: material.value, prompt: generationPrompt, sourcePrompt: prompt.value, generatedAssetId: generatedAssetId.value, previewUrl: previewUrl.value, referenceAnalysis: result?.referenceAnalysis || '', referenceAnalysisSource: result?.referenceAnalysisSource || '' })
    addMessage('assistant', '产品视觉已经生成并保存。下一步可以补全四视图、生成 3D，或直接提交商品化申请。')
    chatStage.value = 'image_ready'
    chatQuickReplies.value = [
      { label: '满意，生成三视图', type: 'multiview', value: '' },
      { label: '不满意，告诉我怎么改', type: 'refine', value: '' },
      { label: '直接申请打样 / 商品化', type: 'commercial', value: '' },
    ]
    phase.value = 'result'
  } catch (error: any) {
    const message = generationFailureMessage(error)
    uni.showModal({ title: '产品图未生成', content: message, showCancel: false })
  }
  finally { busy.value = false; busyMessage.value = '正在保存创作过程并调用 AI，请稍候…' }
}

function generationFailureMessage(error: any) {
  const raw = String(error?.message || error?.errMsg || '').trim()
  if (/timeout|timed out|超时/i.test(raw)) return '生成请求等待超时。之间大模型生成通常需要 1-3 分钟，请检查网络后重新提交；本次失败不会扣除未成功生成的积分。'
  if (/登录已过期|请先登录|401/i.test(raw)) return '登录状态已失效，请重新登录后再生成。'
  if (/安全体验模式|SetLimitExceeded|模型.*暂停/i.test(raw)) return '方舟模型的安全体验额度已用尽，服务已暂停。请联系平台管理员在火山方舟控制台提高额度或关闭安全体验模式后重试。'
  if (/ark api key|火山方舟|服务尚未配置|未配置/i.test(raw)) return 'AI 生图服务没有完成配置。请检查服务器上的 VOLCENGINE_ARK_API_KEY 和模型开通状态，配置后重启 smart-pig 服务。'
  if (/网络|network|fail|connect|refused|域名/i.test(raw)) return '无法连接 AI 生图服务。请检查微信公众平台 request 合法域名、网络连接和服务器运行状态。'
  return raw || '生成服务暂时不可用，请稍后重试。'
}
function imageUrl(item: any) {
  const raw = String(item?.previewUrl || item?.imageUrl || item?.fileUrl || '')
  if (/^https?:\/\//i.test(raw)) return raw
  return raw.startsWith('/') ? apiUrl(raw) : ''
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
async function regenerateWithRefinement() {
  const sourceAssetId = generatedAssetId.value
  const note = refinementNote.value.trim()
  if (busy.value || !sourceAssetId || !note || !selectedProduct.value) return
  busy.value = true
  busyMessage.value = '正在理解修改要求并生成新方案，请稍候…'
  try {
    let refinementPrompt = note
    try {
      busyMessage.value = '正在由之间大模型优化修改要求…'
      const optimized = await optimizeImageEditPrompt({
        prompt: prompt.value,
        refinementNote: note,
        productCategory: selectedProduct.value.name,
        material: material.value,
      })
      if (String(optimized?.prompt || '').trim()) refinementPrompt = String(optimized.prompt).trim()
    } catch {
      // The edit request remains usable when prompt optimization is temporarily unavailable.
    }
    busyMessage.value = '正在基于当前产品图生成新方案，请稍候…'
    await saveEvent('image', 'image_refinement_started', { inputAssetId: sourceAssetId, refinementNote: note, optimizedPrompt: refinementPrompt, productType: selectedProduct.value.name, material: material.value })
    const result = await createReferenceToImage({ title: `${selectedProduct.value.name} · 修改方案`, prompt: refinementPrompt, inputAssetId: sourceAssetId, productKey: selectedProduct.value.key, productCategory: selectedProduct.value.name, material: material.value, refinement: true, refinementNote: note })
    const newAssetId = Number(result?.assetId || result?.id)
    if (!Number.isFinite(newAssetId) || newAssetId <= 0) throw new Error('修改后的产品图没有保存成功，请重试')
    generatedAssetId.value = newAssetId
    previewUrl.value = imageUrl(result)
    multiviewImages.value = []
    await saveEvent('image', 'image_refined', { previousAssetId: sourceAssetId, generatedAssetId: newAssetId, previewUrl: previewUrl.value, refinementNote: note, optimizedPrompt: refinementPrompt, productType: selectedProduct.value.name, material: material.value })
    addMessage('user', `补充修改：${note}`)
    addMessage('assistant', '新的产品视觉已经生成，旧版本仍保留在作品库。你可以继续修改，或进入四视图和 3D。')
    cancelRefinement()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '重新生成失败，请稍后重试', icon: 'none' })
  } finally {
    busy.value = false
    busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
  }
}

function setModelTask(payload: any) {
  const jobId = Number(payload?.jobId || payload?.modelJobId)
  if (!Number.isFinite(jobId) || jobId <= 0) return
  modelTask.value = {
    jobId,
    status: String(payload?.status || 'running').toLowerCase(),
    progress: Number(payload?.progress) || 0,
    assetId: Number(payload?.assetId || payload?.modelAssetId) || null,
    previewUrl: imageUrl(payload),
    errorMessage: String(payload?.errorMessage || payload?.error || ''),
  }
}

function stopModelPolling() {
  modelPollVersion += 1
  if (modelPollTimer) clearTimeout(modelPollTimer)
  modelPollTimer = null
}

async function refreshModelTask() {
  if (!modelTask.value || modelRefreshing.value) return
  modelRefreshing.value = true
  try {
    const result = await getTripoModelTask(modelTask.value.jobId)
    const previousStatus = modelTask.value.status
    setModelTask(result)
    if (!modelTask.value) return
    if (modelTask.value.status === 'succeeded' && previousStatus !== 'succeeded') {
      await saveEvent('model', 'model_completed', { modelJobId: modelTask.value.jobId, assetId: modelTask.value.assetId, status: 'succeeded', progress: 100, previewUrl: modelTask.value.previewUrl })
      addMessage('assistant', '3D 模型已经生成并保存到作品库，可以继续评审、申请打样或提交商品化报价。')
      chatStage.value = 'model_ready'
      chatQuickReplies.value = [
        { label: '申请打样 / 商品化', type: 'commercial', value: '' },
        { label: '查看我的作品', type: 'works', value: '' },
      ]
    } else if (modelTask.value.status === 'failed' && previousStatus !== 'failed') {
      await saveEvent('model', 'model_failed', { modelJobId: modelTask.value.jobId, status: 'failed', progress: modelTask.value.progress, errorMessage: modelTask.value.errorMessage })
      addMessage('assistant', '3D 建模没有完成，失败原因已保存。可以检查产品图后重新提交。')
    }
  } catch (error: any) {
    if (modelTask.value && !isModelTaskTerminal.value) modelTask.value.errorMessage = error?.message || '暂时无法读取建模进度，系统会自动重试'
  } finally {
    modelRefreshing.value = false
  }
}

async function scheduleModelPolling(immediate = false) {
  stopModelPolling()
  const version = modelPollVersion
  const poll = async () => {
    if (version !== modelPollVersion || !modelTask.value || isModelTaskTerminal.value) return
    await refreshModelTask()
    if (version !== modelPollVersion || !modelTask.value || isModelTaskTerminal.value) return
    modelPollTimer = setTimeout(poll, 5000)
  }
  if (immediate) await poll()
  else modelPollTimer = setTimeout(poll, 5000)
}

async function generateMultiView() {
  if (busy.value) return
  if (!generatedAssetId.value) {
    uni.showToast({ title: '当前产品图未保存成功，请先重新生成产品图', icon: 'none' })
    return
  }
  if (!aiPolicyConfirmed.value) {
    const confirmed = await confirmCreativePolicyInPage('ai-output')
    if (!confirmed) return
    aiPolicyConfirmed.value = true
    await saveEvent('compliance', 'policy_notice_confirmed', { policyKey: 'ai-output', policyVersion: CREATIVE_POLICY_VERSION })
  }
  busy.value = true
  busyMessage.value = '正在基于当前产品图生成正面、侧面和背面，请稍候…'
  try {
    await saveEvent('multiview', 'multiview_started', { inputAssetId: generatedAssetId.value, productType: selectedProduct.value?.name, material: material.value })
    const result = await createSeedreamMultiView({ inputAssetId: generatedAssetId.value, prompt: prompt.value, productKey: selectedProduct.value?.key, productCategory: selectedProduct.value?.name, material: material.value, viewCount: 3, size: '2K', watermark: true })
    multiviewImages.value = (Array.isArray(result?.images) ? result.images : []).filter(item => Number(item?.assetId) > 0)
    if (!hasCompleteThreeViews.value) throw new Error('三视图没有完整返回正面、侧面和背面，请稍后重试')
    await saveEvent('multiview', 'multiview_generated', { inputAssetId: generatedAssetId.value, images: multiviewImages.value.map(item => ({ view: item.view, assetId: item.assetId, label: item.label })) })
    addMessage('assistant', '正面、侧面和背面都已保存。现在可以把三张图一起交给 Tripo 多视图建模，结构会比单张图更完整。')
    chatStage.value = 'multiview_ready'
    chatQuickReplies.value = [
      { label: '用三视图生成 3D', type: 'model', value: '' },
      { label: '先申请打样 / 商品化', type: 'commercial', value: '' },
    ]
    phase.value = 'multiview'
  } catch (error: any) { uni.showToast({ title: error?.message || '四视图生成失败', icon: 'none' }) }
  finally { busy.value = false; busyMessage.value = '正在保存创作过程并调用 AI，请稍候…' }
}
async function generateModel() {
  if (busy.value) return
  if (!generatedAssetId.value) {
    uni.showToast({ title: '当前产品图未保存成功，请先重新生成产品图', icon: 'none' })
    return
  }
  if (!threeDimensionalPolicyConfirmed.value) {
    const confirmed = await confirmCreativePolicyInPage('three-dimensional')
    if (!confirmed) return
    threeDimensionalPolicyConfirmed.value = true
    await saveEvent('compliance', 'policy_notice_confirmed', { policyKey: 'three-dimensional', policyVersion: CREATIVE_POLICY_VERSION })
  }
  busy.value = true
  busyMessage.value = '正在提交 3D 建模任务，请稍候…'
  try {
    const useMultiview = phase.value === 'multiview'
    if (useMultiview && !hasCompleteThreeViews.value) throw new Error('请先生成完整的正面、侧面和背面，再提交多视图建模')
    modelInputMode.value = useMultiview ? 'multiview' : 'single'
    busyMessage.value = useMultiview ? '正在提交三视图 3D 建模任务，请稍候…' : '正在提交单图 3D 建模任务，请稍候…'
    const payload: any = { title: `${selectedProduct.value?.name || '文创产品'} · ${useMultiview ? '三视图' : '单图'} 3D 原型`, prompt: prompt.value, rawPrompt: prompt.value, mode: useMultiview ? 'multiview_to_model' : 'image_to_model', inputAssetId: generatedAssetId.value, productKey: selectedProduct.value?.key, productCategory: selectedProduct.value?.name, material: material.value, materialLabel: material.value, materialPrompt: `manufacturing material: ${material.value}`, multiviewAssetIds: useMultiview ? Object.fromEntries(multiviewImages.value.map(item => [item.view, Number(item.assetId)])) : undefined, exportFormats: 'GLB', texture: true, pbr: true, textureQuality: 'extreme', geometryQuality: 'detailed', textureAlignment: 'original_image', orientation: 'align_image', autoSize: true, imageAutofix: true, exportUv: true, faceLimit: 2000000 }
    await saveEvent('model', 'model_started', { inputAssetId: generatedAssetId.value, multiview: useMultiview, inputMode: modelInputMode.value, productType: selectedProduct.value?.name, material: material.value })
    const result = await createModel(payload)
    const jobId = Number(result?.jobId)
    if (!Number.isFinite(jobId) || jobId <= 0) throw new Error('3D 服务没有返回任务编号，请稍后重试')
    setModelTask({ jobId, status: result?.status, progress: result?.progress, assetId: result?.assetId })
    await saveEvent('model', 'model_submitted', { inputAssetId: generatedAssetId.value, multiview: useMultiview, inputMode: modelInputMode.value, modelJobId: jobId, modelAssetId: result?.assetId, status: modelTask.value?.status || 'running', progress: modelTask.value?.progress || 0, productType: selectedProduct.value?.name, material: material.value })
    addMessage('assistant', `${modelInputLabel.value}任务已提交，完成后会出现在作品库。你可以在那里预览、评审并申请打样。`)
    chatStage.value = 'model_running'
    chatQuickReplies.value = [{ label: '查看我的作品', type: 'works', value: '' }]
    phase.value = 'model'
    void scheduleModelPolling(true)
  } catch (error: any) { uni.showToast({ title: error?.message || '3D 任务提交失败', icon: 'none' }) }
  finally { busy.value = false; busyMessage.value = '正在保存创作过程并调用 AI，请稍候…' }
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
    success: result => { if (result.confirm) uni.redirectTo({ url: '/pages/conversation-create/index?new=1' }) },
  })
}
onLoad(options => { forceNewSession.value = String(options?.new || '') === '1' })
onMounted(async () => {
  if (!requireSession()) return
  if (!messages.value.length) addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  if (!chatQuickReplies.value.length) setInitialChatReplies()
  await loadProductCatalog()
  if (!(await ensureSession())) return
  if (!messages.value.length) addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  if (awaitingGenerationConfirmation.value && !chatQuickReplies.value.length) setGenerationConfirmationReplies()
  if (!chatQuickReplies.value.length && chatStage.value !== 'need_additional_detail') setInitialChatReplies()
  if (phase.value === 'model' && modelTask.value && !isModelTaskTerminal.value) void scheduleModelPolling(true)
})
onUnmounted(() => { resolvePolicyDialog(false); stopModelPolling() })
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
  padding: 24rpx 28rpx calc(296rpx + env(safe-area-inset-bottom));
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
.ai-disclosure { margin: 0 0 22rpx; }

.message-row { display: flex; align-items: flex-start; gap: 10rpx; margin: 20rpx 0; }
.message-row.user { justify-content: flex-end; }
.message-avatar { display: grid; place-items: center; flex: 0 0 46rpx; width: 46rpx; height: 46rpx; border-radius: 14rpx; font-family: "Songti SC", "STSong", serif; font-size: 22rpx; font-weight: 850; }
.assistant-avatar { background: var(--green); color: #fff; box-shadow: 0 5rpx 12rpx rgba(54, 93, 74, .18); }
.user-avatar { background: #f7e5dc; color: #a45d48; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; font-size: 16rpx; }
.message-content { display: flex; min-width: 0; max-width: 78%; flex-direction: column; align-items: flex-start; }
.user .message-content { align-items: flex-end; }
.message-meta { display: flex; align-items: center; gap: 7rpx; margin: 0 4rpx 6rpx; color: #849089; font-size: 12rpx; }
.message-meta text:last-child { color: #a5afa9; }
.bubble { max-width: 100%; box-sizing: border-box; padding: 14rpx 16rpx; border: 1rpx solid #e1e8e2; border-radius: 7rpx 16rpx 16rpx 16rpx; background: var(--surface); box-shadow: 0 6rpx 17rpx rgba(51, 72, 60, .045); }
.bubble text { color: #46534b; font-size: 18rpx; line-height: 1.65; }
.user .bubble { border-color: #c3d5c8; border-radius: 16rpx 7rpx 16rpx 16rpx; background: #e8f2eb; }
.user .bubble text { color: #436052; }

.thinking-row { display: flex; align-items: flex-start; gap: 10rpx; margin: 20rpx 0; animation: thinking-enter .24s ease-out; }
.thinking-content { display: flex; min-width: 0; max-width: 78%; flex-direction: column; }
.thinking-bubble { padding: 13rpx 16rpx; border: 1rpx solid #d7e5da; border-radius: 7rpx 16rpx 16rpx 16rpx; background: #f9fcf9; box-shadow: 0 7rpx 17rpx rgba(62, 103, 76, .07); }
.thinking-title-row { display: flex; align-items: center; gap: 10rpx; }
.thinking-title { color: var(--green); font-size: 17rpx; font-weight: 850; }
.thinking-detail { display: block; margin-top: 5rpx; color: #91a097; font-size: 14rpx; line-height: 1.4; }
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

.refinement-panel { margin-top: 14rpx; padding: 14rpx; border: 1rpx solid #ead7ce; border-radius: 13rpx; background: #fff9f6; }
.refinement-heading { justify-content: space-between; gap: 10rpx; }
.refinement-heading view { display: flex; flex-direction: column; gap: 5rpx; }
.refinement-heading view text:last-child { color: #684e45; font-size: 17rpx; font-weight: 850; }
.refinement-close { color: #aa806e; font-size: 28rpx; }
.text-input { width: 100%; min-height: 132rpx; box-sizing: border-box; margin-top: 12rpx; padding: 12rpx; border: 1rpx solid #e5d4cc; border-radius: 10rpx; background: #fff; color: #493f3b; font-size: 17rpx; line-height: 1.55; }
.input-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 9rpx; color: #a08f86; font-size: 12rpx; }
.refinement-panel .dark-button { height: 58rpx; margin: 0; padding: 0 13rpx; font-size: 15rpx; line-height: 58rpx; }

.composer-dock { position: fixed; z-index: 25; right: 0; bottom: calc(88rpx + env(safe-area-inset-bottom)); left: 0; box-sizing: border-box; padding: 13rpx 22rpx 9rpx; border-top: 1rpx solid #dfe7e1; background: rgba(255, 255, 255, .97); box-shadow: 0 -10rpx 24rpx rgba(44, 62, 51, .07); backdrop-filter: blur(18rpx); }
.composer-context { min-width: 0; gap: 7rpx; color: #6f8076; font-size: 13rpx; }
.context-live { width: 9rpx; height: 9rpx; border-radius: 50%; background: #70a481; }
.context-product { overflow: hidden; max-width: 260rpx; color: #96a29b; text-overflow: ellipsis; white-space: nowrap; }
.context-working { margin-left: auto; color: var(--orange); font-size: 12rpx; }
.quick-reply-list { width: 100%; margin-top: 10rpx; white-space: nowrap; }
.quick-reply-track { display: flex; gap: 8rpx; }
.quick-reply { display: inline-flex; align-items: center; gap: 6rpx; flex: 0 0 auto; height: 50rpx; padding: 0 11rpx; border: 1rpx solid #d8e5db; border-radius: 10rpx; background: #f6faf7; color: #527062; font-size: 14rpx; line-height: 50rpx; }
.quick-reply.confirm { border-color: #9fc3a9; background: #eaf5ed; color: #3f7052; font-weight: 850; }
.quick-reply.secondary { border-color: #e6d5ca; background: #fff9f5; color: #9b6b57; }
.quick-reply.disabled { opacity: .55; }
.quick-reply:active { opacity: .75; }
.quick-reply-mark { display: inline-grid; place-items: center; width: 25rpx; height: 25rpx; border-radius: 7rpx; background: #dcebe0; color: #4e7860; font-size: 11rpx; font-weight: 900; line-height: 25rpx; }
.quick-reply.confirm .quick-reply-mark { background: #4f8563; color: #fff; }
.quick-reply.secondary .quick-reply-mark { background: #f3dfd3; color: #a66751; }
.chat-input-row { display: flex; align-items: center; gap: 8rpx; margin-top: 10rpx; }
.chat-upload-button, .chat-send-button { flex: 0 0 auto; height: 62rpx; margin: 0; border-radius: 12rpx; line-height: 62rpx; }
.chat-upload-button { width: 62rpx; padding: 0; border: 1rpx solid #d9e2db; background: #f8faf8; color: #658073; font-size: 28rpx; }
.chat-send-button { width: 62rpx; padding: 0; background: #dfe7e1; color: #91a099; font-size: 27rpx; font-weight: 900; }
.chat-send-button.ready { background: var(--green); color: #fff; }
.chat-input { flex: 1; min-width: 0; height: 62rpx; box-sizing: border-box; padding: 0 15rpx; border: 1rpx solid #d9e2db; border-radius: 12rpx; background: #f8faf8; color: #3e4c44; font-size: 16rpx; }
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
