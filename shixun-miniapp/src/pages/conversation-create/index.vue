<template>
  <view class="page">
    <view class="topbar"><view @tap="goBack" class="back">‹</view><view><text class="eyebrow">CONVERSATIONAL STUDIO</text><text class="top-title">对话式创作</text></view><text class="save-state">{{ saving ? '保存中' : '已留存' }}</text></view>

    <scroll-view class="chat" scroll-y :scroll-into-view="scrollIntoView" scroll-with-animation>
      <view class="intro-line"><text>每一步都会变成你的创作档案，后面可继续生图、四视图、3D 和商品化。</text></view>
      <AiGeneratedNotice class="ai-disclosure" compact description="对话建议、提示词和后续生成的图片、四视图、3D 原型均可能由人工智能生成，仅供创作参考，需经人工复核后再用于商业场景。" />
      <view v-for="item in messages" :id="`message-${item.id}`" :key="item.id" class="message-row" :class="item.role">
        <view v-if="item.role === 'assistant'" class="avatar">之</view>
        <view class="bubble"><text>{{ item.text }}</text></view>
      </view>

      <view v-if="phase === 'mode'" class="choice-panel"><text class="choice-title">你想从哪种方式开始？</text><view class="choice-grid"><view v-for="item in modeOptions" :key="item.key" class="choice-card" @tap="chooseMode(item.key)"><text class="choice-mark">{{ item.mark }}</text><view><text>{{ item.title }}</text><text>{{ item.desc }}</text></view><text class="choice-arrow">›</text></view></view></view>

      <view v-if="phase === 'product'" class="choice-panel"><text class="choice-title">先选一个要落地的产品</text><text class="choice-note">先从简单、容易打样的产品开始，后面还可以更换方向。</text><view class="product-grid"><view v-for="item in productOptions" :key="item.key" class="product-card" @tap="chooseProduct(item)"><text class="product-mark">{{ item.mark }}</text><text class="product-name">{{ item.name }}</text><text class="product-desc">{{ item.desc }}</text><text class="product-process">{{ item.process }}</text></view></view></view>

      <view v-if="phase === 'inspiration'" class="input-panel"><text class="choice-title">说说你的已有灵感</text><text class="choice-note">可以写文化主题、故事、想做的造型、使用场景，越具体越容易落地。</text><textarea v-model="inspirationText" maxlength="1200" auto-height class="text-input" placeholder="例如：把家乡古城的城墙和祥云结合，做成适合游客带走的合金冰箱贴。" /><view class="input-foot"><text>{{ inspirationText.length }}/1200</text><button class="dark-button" :disabled="!inspirationText.trim() || busy" @tap="submitTextInspiration">继续</button></view></view>

      <view v-if="phase === 'image'" class="input-panel"><text class="choice-title">上传你的灵感图片</text><text class="choice-note">可以是草图、照片、纹样或你有权使用的参考图。系统会保留主体，再优化为产品视觉。</text><view class="image-picker" :class="{ ready: referencePath }" @tap="pickInspirationImage"><image v-if="referencePath" :src="referencePath" mode="aspectFill" /><view v-else><text>+</text><text>选择一张图片</text></view></view><button class="dark-button full-button" :disabled="!referenceAssetId || busy" @tap="submitImageInspiration">{{ referenceAssetId ? '继续选择工艺' : '先上传图片' }}</button></view>

      <view v-if="phase === 'material'" class="choice-panel"><text class="choice-title">你希望它用什么材质？</text><text class="choice-note">材质会同步进入生图、三视图、3D 和后续生产提示词。</text><view class="material-grid"><view v-for="item in currentMaterials" :key="item.name" class="material-card" :class="{ active: material === item.name }" @tap="chooseMaterial(item)"><view class="swatch" :style="{ background: item.color }" /><view><text>{{ item.name }}</text><text>{{ item.note }}</text></view><text v-if="material === item.name" class="check">✓</text></view></view></view>

      <view v-if="phase === 'style'" class="choice-panel"><text class="choice-title">最后确定创作气质</text><view class="style-section"><text>视觉风格</text><view class="pill-row"><text v-for="item in styles" :key="item" class="pill" :class="{ active: style === item }" @tap="chooseStyle(item)">{{ item }}</text></view></view><view class="style-section"><text>主色方向</text><view class="pill-row"><text v-for="item in palettes" :key="item" class="pill" :class="{ active: palette === item }" @tap="choosePalette(item)">{{ item }}</text></view></view><view class="style-section"><text>主要用途</text><view class="pill-row"><text v-for="item in purposes" :key="item" class="pill" :class="{ active: purpose === item }" @tap="choosePurpose(item)">{{ item }}</text></view></view><button class="dark-button full-button" :disabled="busy" @tap="confirmDirection">查看 AI 方案</button></view>

      <view v-if="phase === 'summary'" class="summary-panel"><text class="choice-title">这是我为你整理的创作方案</text><view class="summary-card"><view><text>产品</text><text>{{ selectedProduct?.name }}</text></view><view><text>材质</text><text>{{ material }}</text></view><view><text>风格</text><text>{{ style }} · {{ palette }}</text></view><view><text>用途</text><text>{{ purpose }}</text></view><view><text>灵感</text><text>{{ inspirationText || '使用产品模板自动生成方向' }}</text></view></view><text class="summary-note">确认后会生成第一张产品视觉，生成结果会自动进入作品库；之后可以继续四视图和 3D 建模。</text><button class="dark-button full-button" :loading="busy" @tap="generateProductImage">确认并生成产品图</button><button class="link-button" @tap="editDirection">返回修改</button></view>

      <view v-if="phase === 'result'" class="result-panel"><text class="result-kicker">PRODUCT VISUAL READY</text><text class="choice-title">第一张产品视觉已经完成</text><image v-if="previewUrl" class="result-image" :src="previewUrl" mode="aspectFill" @tap="previewImage" /><view v-else class="result-placeholder"><text>{{ selectedProduct?.mark || '作' }}</text><text>作品已保存到作品库</text></view><text class="result-tip">这不是流程终点。你可以继续补全四个角度，或直接进入 3D 原型。</text><view class="next-grid"><view class="next-card" @tap="generateMultiView"><text>观</text><view><text>生成三视图 / 四视图</text><text>补全正、左、背、右四个角度</text></view><text>›</text></view><view class="next-card" @tap="generateModel"><text>形</text><view><text>直接生成 3D 模型</text><text>从当前产品图创建立体原型</text></view><text>›</text></view><view class="next-card" @tap="openCommercial"><text>做</text><view><text>申请打样 / 商品化</text><text>把创作提交给运营报价</text></view><text>›</text></view></view></view>

      <view v-if="phase === 'multiview'" class="result-panel"><text class="result-kicker">TURNAROUND VIEW</text><text class="choice-title">四视图已保存</text><text class="result-tip">系统已把产品的正面、左侧、背面和右侧都留在作品库，可以继续交给 3D 建模。</text><view class="view-grid"><view v-for="item in multiviewImages" :key="item.assetId" class="view-card"><image v-if="imageUrl(item)" :src="imageUrl(item)" mode="aspectFill" /><view v-else class="view-placeholder"><text>{{ item.label }}</text><text>已保存</text></view><text>{{ item.label }}</text></view></view><button class="dark-button full-button" :loading="busy" @tap="generateModel">继续生成 3D 模型</button><button class="outline-button full-button" @tap="openCommercial">先申请打样 / 商品化</button></view>

      <view v-if="phase === 'model'" class="result-panel"><text class="result-kicker">3D PROTOTYPE</text><text class="choice-title">3D 建模任务已提交</text><view class="model-success"><text>3D</text><view><text>模型正在作品库生成</text><text>完成后可以预览、评审、申请打样或提交商品化报价。</text></view></view><button class="dark-button full-button" @tap="goWorks">查看我的作品</button><button class="outline-button full-button" @tap="openCommercial">申请打样 / 商品化</button></view>
    </scroll-view>

    <view v-if="busy" class="loading-bar"><text>正在保存创作过程并调用 AI，请稍候…</text></view>
    <view class="bottom-actions"><button @tap="goWorks">作品库</button><button @tap="restart">重新开始</button></view>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AiGeneratedNotice from '../../components/AiGeneratedNotice.vue'
import {
  createConversation,
  createImage,
  createImageWithReference,
  createModel,
  createSeedreamMultiView,
  getConversation,
  getConversations,
  optimizeImagePrompt,
  saveConversationEvent,
  uploadReference,
  type ConversationSession,
  type SeedreamMultiViewImage,
} from '../../api/creative'
import { apiUrl } from '../../api/client'
import { requireSession } from '../../utils/session'

type Phase = 'mode' | 'product' | 'inspiration' | 'image' | 'material' | 'style' | 'summary' | 'result' | 'multiview' | 'model'
type Mode = 'template' | 'text' | 'image'
interface Message { id: number; role: 'assistant' | 'user'; text: string }
interface ProductOption { key: string; name: string; mark: string; desc: string; process: string; materials: MaterialOption[] }
interface MaterialOption { name: string; note: string; color: string }

const modeOptions = [
  { key: 'template' as Mode, mark: '模', title: '带模板开始', desc: '选择产品，自动生成一套可落地提示词' },
  { key: 'text' as Mode, mark: '字', title: '已有灵感（文字）', desc: '把你的想法、故事或需求告诉我' },
  { key: 'image' as Mode, mark: '图', title: '已有灵感（图片）', desc: '上传草图、照片或有权使用的参考图' },
]
const productOptions: ProductOption[] = [
  { key: 'alloy_magnet', name: '合金冰箱贴', mark: '贴', desc: '轮廓清晰，最适合首件打样', process: '压铸 · 浅浮雕 · 背磁', materials: [{ name: '锌合金', note: '适合金属浮雕与电镀', color: 'linear-gradient(145deg,#e5c989,#8e7554)' }, { name: '合金', note: '适合做复古器物质感', color: 'linear-gradient(145deg,#d9b780,#77624d)' }, { name: '亚克力', note: '适合轻量透明方案', color: 'linear-gradient(145deg,#fff,#9fc3c6)' }] },
  { key: 'badge', name: '锌合金徽章', mark: '章', desc: '小尺寸、低复杂度、易做系列', process: '冲压 · 烤漆 · 蝴蝶扣', materials: [{ name: '锌合金', note: '适合浮雕和精细轮廓', color: 'linear-gradient(145deg,#e8d19e,#917451)' }, { name: '金属', note: '适合复古电镀效果', color: 'linear-gradient(145deg,#d8b978,#6d5845)' }] },
  { key: 'keychain', name: '合金钥匙扣', mark: '扣', desc: '有明确挂孔，方便快速商品化', process: '压铸 · 电镀 · 挂环', materials: [{ name: '锌合金', note: '适合立体造型和金属边框', color: 'linear-gradient(145deg,#ecd49c,#947b55)' }, { name: '亚克力', note: '适合彩色平面图案', color: 'linear-gradient(145deg,#fff,#a4ced1)' }] },
  { key: 'canvas_bag', name: '帆布袋', mark: '袋', desc: '适合系列纹样和实用型礼赠', process: '丝印 · 热转印 · 缝制', materials: [{ name: '帆布', note: '自然布面，适合日常使用', color: 'linear-gradient(145deg,#f5ead6,#b39a78)' }, { name: '棉帆布', note: '适合柔和国风插画', color: 'linear-gradient(145deg,#fff6e7,#c9ad8b)' }] },
  { key: 'ceramic_mug', name: '陶瓷马克杯', mark: '杯', desc: '适合图案延展和礼赠场景', process: '釉面 · 热转印 · 烧制', materials: [{ name: '陶瓷', note: '适合釉面和器物感', color: 'linear-gradient(145deg,#fff,#bfd3c5)' }, { name: '陶瓷釉面', note: '适合温润高光效果', color: 'linear-gradient(145deg,#f8f2df,#9ebdb0)' }] },
]
const styles = ['国潮', '敦煌', '青绿山水', '现代极简', '亲子卡通']
const palettes = ['青绿金', '朱砂米白', '蓝白', '黑金', '明快多彩']
const purposes = ['景区伴手礼', '博物馆文创', '企业礼赠', '个人收藏', '亲子纪念']

const phase = ref<Phase>('mode')
const mode = ref<Mode | ''>('')
const selectedProduct = ref<ProductOption | null>(null)
const material = ref('')
const style = ref('国潮')
const palette = ref('青绿金')
const purpose = ref('景区伴手礼')
const inspirationText = ref('')
const referencePath = ref('')
const referenceAssetId = ref<number | null>(null)
const sessionId = ref<number | null>(null)
const generatedAssetId = ref<number | null>(null)
const previewUrl = ref('')
const multiviewImages = ref<SeedreamMultiViewImage[]>([])
const messages = ref<Message[]>([])
const busy = ref(false)
const saving = ref(false)
const sessionReady = ref(false)
const scrollIntoView = ref('bottom-anchor')
let messageId = 0
let sessionPromise: Promise<boolean> | null = null
const forceNewSession = ref(false)

const currentMaterials = computed(() => selectedProduct.value?.materials || [])
const prompt = computed(() => {
  const product = selectedProduct.value?.name || '文创产品'
  const source = inspirationText.value.trim() || `为${product}设计一套具有文化辨识度、适合量产打样的产品视觉`
  return `${source}。产品：${product}；材质：${material.value}；风格：${style.value}；主色：${palette.value}；用途：${purpose.value}。请考虑清晰轮廓、可生产结构、合理尺寸和适合商品展示的构图。`
})

function addMessage(role: Message['role'], text: string) {
  messages.value.push({ id: ++messageId, role, text })
  void nextTick(() => { scrollIntoView.value = `message-${messageId}` })
}
function goBack() { uni.navigateBack() }
function goWorks() { uni.navigateTo({ url: '/pages/works/index' }) }
function openCommercial() { uni.navigateTo({ url: `/pages/commercial/index${generatedAssetId.value ? `?assetId=${generatedAssetId.value}` : ''}` }) }
function selectedModeTitle() { return modeOptions.find(item => item.key === mode.value)?.title || '' }

function isNotFound(error: any) { return Number(error?.statusCode) === 404 || /not found|不存在|找不到/i.test(String(error?.message || '')) }

function productByValue(productType?: string, productKey?: string) {
  return productOptions.find(item => item.key === productKey || item.name === productType) || null
}

function resetViewState() {
  phase.value = 'mode'
  mode.value = ''
  selectedProduct.value = null
  material.value = ''
  style.value = '国潮'
  palette.value = '青绿金'
  purpose.value = '景区伴手礼'
  inspirationText.value = ''
  referencePath.value = ''
  referenceAssetId.value = null
  generatedAssetId.value = null
  previewUrl.value = ''
  multiviewImages.value = []
  messages.value = []
  messageId = 0
}

function restoreEvent(event: any) {
  const payload = event?.payload || {}
  switch (String(event?.eventType || '')) {
    case 'mode_selected':
      mode.value = payload.mode || mode.value
      break
    case 'product_selected':
      selectedProduct.value = productByValue(payload.productType, payload.productKey) || selectedProduct.value
      if (!material.value && selectedProduct.value) material.value = selectedProduct.value.materials[0].name
      break
    case 'text_inspiration_submitted':
      inspirationText.value = String(payload.inspirationText || '')
      break
    case 'image_inspiration_uploaded':
      referenceAssetId.value = Number(payload.inputAssetId) || null
      break
    case 'material_selected':
      material.value = String(payload.material || payload.materialName || material.value)
      break
    case 'style_selected':
      if (payload.style) style.value = String(payload.style)
      if (payload.palette) palette.value = String(payload.palette)
      if (payload.purpose) purpose.value = String(payload.purpose)
      break
    case 'palette_selected':
      palette.value = String(payload.palette || palette.value)
      break
    case 'purpose_selected':
      purpose.value = String(payload.purpose || purpose.value)
      break
    case 'creative_direction_confirmed':
      if (payload.style) style.value = String(payload.style)
      if (payload.palette) palette.value = String(payload.palette)
      if (payload.purpose) purpose.value = String(payload.purpose)
      if (payload.inspirationText) inspirationText.value = String(payload.inspirationText)
      break
    case 'image_generated':
      generatedAssetId.value = Number(payload.generatedAssetId) || generatedAssetId.value
      previewUrl.value = imageUrl({ previewUrl: payload.previewUrl })
      break
    case 'multiview_generated':
      multiviewImages.value = Array.isArray(payload.images) ? payload.images : []
      break
    case 'model_submitted':
      break
    default:
      break
  }
}

function restoreMessages(events: any[]) {
  messages.value = []
  messageId = 0
  addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  for (const event of events) {
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
        addMessage('assistant', '最后确认视觉风格、颜色和用途，我就可以为你整理完整方案。')
        break
      case 'creative_direction_confirmed':
        addMessage('user', `${payload.style || style.value} · ${payload.palette || palette.value} · ${payload.purpose || purpose.value}`)
        addMessage('assistant', '方案整理好了。确认后我会调用现有生图服务，并把成图与完整参数绑定到这次会话。')
        break
      case 'image_generated':
        addMessage('assistant', '产品视觉已经生成并保存。下一步可以补全四视图、生成 3D，或直接提交商品化申请。')
        break
      case 'multiview_generated':
        addMessage('assistant', '四个角度都已保存。现在可以把它们一起交给 3D 建模，结构会比单张图更完整。')
        break
      case 'model_submitted':
        addMessage('assistant', '3D 建模任务已提交，完成后会出现在作品库。你可以在那里预览、评审并申请打样。')
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
      case 'material_selected': phase.value = 'style'; break
      case 'creative_direction_confirmed': phase.value = 'summary'; break
      case 'image_generated': phase.value = 'result'; break
      case 'multiview_generated': phase.value = 'multiview'; break
      case 'model_submitted': phase.value = 'model'; break
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
  catch (error: any) { uni.showToast({ title: error?.message || '步骤保存失败', icon: 'none' }) }
  finally { saving.value = false }
}
async function chooseMode(value: Mode) {
  if (busy.value) return
  mode.value = value
  addMessage('user', selectedModeTitle())
  addMessage('assistant', '好，我们先确定产品方向。你想把它做成什么？')
  await saveEvent('mode', 'mode_selected', { mode: value, modeName: selectedModeTitle() })
  phase.value = 'product'
}
async function chooseProduct(value: ProductOption) {
  selectedProduct.value = value
  material.value = value.materials[0].name
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
  addMessage('assistant', '我记下了这段灵感。接下来选择材质，我会把材质、结构和生产限制一起考虑。')
  phase.value = 'material'
}
function pickInspirationImage() {
  if (busy.value) return
  uni.chooseImage({ count: 1, sizeType: ['compressed'], success: (result) => {
    const path = result.tempFilePaths?.[0]
    if (path) { referencePath.value = path; referenceAssetId.value = null; void uploadInspirationImage(path) }
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
  } catch (error: any) { referencePath.value = ''; uni.showToast({ title: error?.message || '图片上传失败', icon: 'none' }) }
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
  addMessage('user', value.name)
  await saveEvent('material', 'material_selected', { productType: selectedProduct.value?.name, material: value.name, materialNote: value.note })
  addMessage('assistant', '最后确认视觉风格、颜色和用途，我就可以为你整理完整方案。')
  phase.value = 'style'
}
function chooseStyle(value: string) { style.value = value; void saveEvent('style', 'style_selected', { style: value, productType: selectedProduct.value?.name }) }
function choosePalette(value: string) { palette.value = value; void saveEvent('style', 'palette_selected', { palette: value, productType: selectedProduct.value?.name }) }
function choosePurpose(value: string) { purpose.value = value; void saveEvent('style', 'purpose_selected', { purpose: value, productType: selectedProduct.value?.name }) }
async function confirmDirection() {
  addMessage('user', `${style.value} · ${palette.value} · ${purpose.value}`)
  await saveEvent('summary', 'creative_direction_confirmed', { productType: selectedProduct.value?.name, material: material.value, style: style.value, palette: palette.value, purpose: purpose.value, inspirationText: inspirationText.value.trim() })
  addMessage('assistant', '方案整理好了。确认后我会调用现有生图服务，并把成图与完整参数绑定到这次会话。')
  phase.value = 'summary'
}
function editDirection() { phase.value = 'style' }
async function generateProductImage() {
  if (busy.value || !selectedProduct.value || !material.value) return
  busy.value = true
  try {
    await saveEvent('summary', 'generation_started', { productType: selectedProduct.value.name, material: material.value, prompt: prompt.value })
    let generationPrompt = prompt.value
    try {
      const optimized = await optimizeImagePrompt({ prompt: prompt.value, provider: 'tripo', productCategory: selectedProduct.value.name, material: material.value })
      if (String(optimized?.prompt || '').trim()) {
        generationPrompt = String(optimized.prompt).trim()
        await saveEvent('summary', 'prompt_optimized', { productType: selectedProduct.value.name, material: material.value, sourcePrompt: prompt.value, optimizedPrompt: generationPrompt })
      }
    } catch {
      await saveEvent('summary', 'prompt_optimization_fallback', { productType: selectedProduct.value.name, material: material.value, sourcePrompt: prompt.value, reason: 'optimization_unavailable' })
    }
    let result: any
    if (mode.value === 'image') {
      result = await createImageWithReference({ title: `${selectedProduct.value.name} · 对话创作`, prompt: generationPrompt, inputAssetId: referenceAssetId.value, productCategory: selectedProduct.value.name, material: material.value })
    } else {
      result = await createImage({ title: `${selectedProduct.value.name} · 对话创作`, prompt: generationPrompt, rawPrompt: inspirationText.value || prompt.value, scene: purpose.value, productType: selectedProduct.value.name, productCategory: selectedProduct.value.name, material: material.value })
    }
    generatedAssetId.value = Number(result?.assetId || result?.id)
    previewUrl.value = imageUrl(result)
    await saveEvent('image', 'image_generated', { productType: selectedProduct.value.name, material: material.value, prompt: generationPrompt, sourcePrompt: prompt.value, generatedAssetId: generatedAssetId.value, previewUrl: previewUrl.value })
    addMessage('assistant', '产品视觉已经生成并保存。下一步可以补全四视图、生成 3D，或直接提交商品化申请。')
    phase.value = 'result'
  } catch (error: any) { uni.showToast({ title: error?.message || '生成失败，请稍后重试', icon: 'none' }) }
  finally { busy.value = false }
}
function imageUrl(item: any) {
  const raw = String(item?.previewUrl || item?.imageUrl || item?.fileUrl || '')
  if (/^https?:\/\//i.test(raw)) return raw
  return raw.startsWith('/') ? apiUrl(raw) : ''
}
function previewImage() { if (previewUrl.value) uni.previewImage({ current: previewUrl.value, urls: [previewUrl.value] }) }
async function generateMultiView() {
  if (busy.value || !generatedAssetId.value) return
  busy.value = true
  try {
    await saveEvent('multiview', 'multiview_started', { inputAssetId: generatedAssetId.value, productType: selectedProduct.value?.name, material: material.value })
    const result = await createSeedreamMultiView({ inputAssetId: generatedAssetId.value, prompt: prompt.value, size: '2K', watermark: true })
    multiviewImages.value = (Array.isArray(result?.images) ? result.images : []).filter(item => Number(item?.assetId) > 0)
    if (multiviewImages.value.length < 4) throw new Error('四视图没有完整返回，请稍后重试')
    await saveEvent('multiview', 'multiview_generated', { inputAssetId: generatedAssetId.value, images: multiviewImages.value.map(item => ({ view: item.view, assetId: item.assetId, label: item.label })) })
    addMessage('assistant', '四个角度都已保存。现在可以把它们一起交给 3D 建模，结构会比单张图更完整。')
    phase.value = 'multiview'
  } catch (error: any) { uni.showToast({ title: error?.message || '四视图生成失败', icon: 'none' }) }
  finally { busy.value = false }
}
async function generateModel() {
  if (busy.value || !generatedAssetId.value) return
  busy.value = true
  try {
    const useMultiview = multiviewImages.value.length >= 4
    const payload: any = { title: `${selectedProduct.value?.name || '文创产品'} · 对话 3D 原型`, prompt: prompt.value, rawPrompt: prompt.value, mode: useMultiview ? 'multiview_to_model' : 'image_to_model', inputAssetId: generatedAssetId.value, productCategory: selectedProduct.value?.name, material: material.value, materialLabel: material.value, materialPrompt: `manufacturing material: ${material.value}`, multiviewAssetIds: useMultiview ? Object.fromEntries(multiviewImages.value.map(item => [item.view, Number(item.assetId)])) : undefined, exportFormats: 'GLB', texture: true, pbr: true, textureQuality: 'extreme', geometryQuality: 'detailed', textureAlignment: 'original_image', orientation: 'align_image', autoSize: true, imageAutofix: true, exportUv: true, faceLimit: 2000000 }
    await saveEvent('model', 'model_started', { inputAssetId: generatedAssetId.value, multiview: useMultiview, productType: selectedProduct.value?.name, material: material.value })
    const result = await createModel(payload)
    await saveEvent('model', 'model_submitted', { inputAssetId: generatedAssetId.value, modelJobId: result?.jobId, modelAssetId: result?.assetId, productType: selectedProduct.value?.name, material: material.value })
    addMessage('assistant', '3D 建模任务已提交，完成后会出现在作品库。你可以在那里预览、评审并申请打样。')
    phase.value = 'model'
  } catch (error: any) { uni.showToast({ title: error?.message || '3D 任务提交失败', icon: 'none' }) }
  finally { busy.value = false }
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
  if (!(await ensureSession())) return
  if (!messages.value.length) addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding-bottom:116rpx;background:linear-gradient(180deg,#f7f3ed 0%,#f1ece4 100%);color:#332d28}.topbar{position:fixed;z-index:5;top:0;left:0;right:0;display:flex;align-items:center;gap:12rpx;padding:18rpx 26rpx calc(16rpx + env(safe-area-inset-top));border-bottom:1rpx solid rgba(116,96,75,.12);background:rgba(247,243,237,.96);backdrop-filter:blur(14rpx)}.back{width:48rpx;height:48rpx;color:#6d5f52;font-size:58rpx;line-height:38rpx;text-align:center}.topbar>view:nth-child(2){display:flex;flex:1;flex-direction:column;gap:4rpx}.eyebrow{color:#668071;font-size:14rpx;font-weight:900;letter-spacing:2rpx}.top-title{font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.save-state{color:#88988b;font-size:15rpx}.chat{height:calc(100vh - 132rpx);box-sizing:border-box;padding:126rpx 24rpx 26rpx}.intro-line{margin:0 2rpx 20rpx;padding:12rpx 14rpx;border-left:3rpx solid #b58b69;background:#f3eee6;color:#84786c;font-size:15rpx;line-height:1.5}.message-row{display:flex;align-items:flex-start;gap:9rpx;margin:17rpx 0}.message-row.user{justify-content:flex-end}.avatar{display:grid;place-items:center;flex:0 0 48rpx;width:48rpx;height:48rpx;border-radius:15rpx;background:#5e7c6d;color:#fff;font-family:"Songti SC","STSong",serif;font-size:25rpx}.bubble{max-width:78%;padding:14rpx 16rpx;border:1rpx solid #e2d8cb;border-radius:17rpx;background:#fffdfa;box-shadow:0 6rpx 15rpx rgba(80,61,42,.045)}.bubble text{color:#534940;font-size:20rpx;line-height:1.55}.user .bubble{border-color:#a9bdae;background:#e5efe7}.user .bubble text{color:#4f685b}.choice-panel,.input-panel,.summary-panel,.result-panel{margin:22rpx 0 26rpx;padding:19rpx;border:1rpx solid #e2d9ce;border-radius:22rpx;background:rgba(255,253,249,.9);box-shadow:0 10rpx 25rpx rgba(79,60,41,.06)}.choice-title{display:block;color:#403831;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.choice-note{display:block;margin-top:7rpx;color:#8c8075;font-size:16rpx;line-height:1.5}.choice-grid,.product-grid,.material-grid{display:grid;gap:10rpx;margin-top:15rpx}.choice-card{display:grid;grid-template-columns:50rpx minmax(0,1fr) 20rpx;align-items:center;gap:10rpx;padding:13rpx;border:1rpx solid #e4dbd0;border-radius:16rpx;background:#fffefa}.choice-mark,.product-mark{display:grid;place-items:center;width:47rpx;height:47rpx;border-radius:14rpx;background:#e8f0e8;color:#5e806e;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:800}.choice-card view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.choice-card view text:first-child{color:#463d35;font-size:21rpx;font-weight:800}.choice-card view text:last-child{color:#92867a;font-size:15rpx;line-height:1.4}.choice-arrow{color:#a16f59;font-size:33rpx}.product-grid{grid-template-columns:1fr 1fr}.product-card{display:flex;min-height:177rpx;flex-direction:column;padding:14rpx;border:1rpx solid #e5dbce;border-radius:17rpx;background:#fffefa}.product-card:active,.choice-card:active,.next-card:active{background:#f4efe7}.product-card:nth-child(2n) .product-mark{background:#f7e8df;color:#a96750}.product-card:nth-child(3n) .product-mark{background:#f5edd9;color:#947144}.product-name{margin-top:10rpx;color:#443a32;font-size:20rpx;font-weight:850}.product-desc{margin-top:5rpx;color:#8b7f73;font-size:14rpx;line-height:1.4}.product-process{margin-top:auto;color:#8c6e59;font-size:14rpx;font-weight:800}.text-input{width:100%;min-height:190rpx;box-sizing:border-box;margin-top:16rpx;padding:14rpx;border:1rpx solid #ddd2c5;border-radius:15rpx;background:#fbf9f5;color:#443b33;font-size:20rpx;line-height:1.6}.input-foot{display:flex;align-items:center;justify-content:space-between;margin-top:12rpx;color:#a09387;font-size:14rpx}.dark-button,.outline-button{height:76rpx;margin-top:15rpx;border-radius:14rpx;font-size:21rpx;font-weight:800}.dark-button{background:#3f3933;color:#fff}.dark-button::after,.outline-button::after,.link-button::after{border:0}.dark-button[disabled]{opacity:.48}.full-button{width:100%}.image-picker{display:flex;align-items:center;justify-content:center;height:300rpx;margin-top:16rpx;overflow:hidden;border:1rpx dashed #b5a796;border-radius:17rpx;background:#faf7f1}.image-picker>view{display:flex;align-items:center;flex-direction:column;gap:8rpx;color:#96897b}.image-picker>view text:first-child{font-size:62rpx;line-height:1}.image-picker image{width:100%;height:100%}.material-grid{grid-template-columns:1fr 1fr}.material-card{display:grid;grid-template-columns:36rpx minmax(0,1fr) 22rpx;align-items:center;gap:9rpx;min-height:74rpx;padding:11rpx;border:1rpx solid #e2d8cc;border-radius:15rpx;background:#fffefa}.material-card.active{border-color:#80a28f;background:#eef5ee}.swatch{width:32rpx;height:32rpx;border:1rpx solid rgba(100,80,58,.16);border-radius:10rpx}.material-card view:nth-child(2){display:flex;min-width:0;flex-direction:column;gap:4rpx}.material-card view text:first-child{color:#493f36;font-size:18rpx;font-weight:800}.material-card view text:last-child{color:#94877b;font-size:13rpx;line-height:1.3}.check{color:#56816c;font-size:21rpx;font-weight:900}.style-section{margin-top:17rpx}.style-section>text{color:#72675c;font-size:16rpx;font-weight:800}.pill-row{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:9rpx}.pill{padding:9rpx 12rpx;border:1rpx solid #e1d7cb;border-radius:999rpx;background:#fffefa;color:#897d71;font-size:15rpx}.pill.active{border-color:#6e907e;background:#e7f0e8;color:#4d715f;font-weight:800}.summary-card{display:grid;gap:0;margin-top:15rpx;border-top:1rpx solid #e6ddd2}.summary-card>view{display:grid;grid-template-columns:110rpx 1fr;gap:10rpx;padding:12rpx 0;border-bottom:1rpx solid #eee7df}.summary-card text:first-child{color:#9c8b7d;font-size:15rpx}.summary-card text:last-child{color:#4c4239;font-size:17rpx;line-height:1.45}.summary-note,.result-tip{display:block;margin-top:14rpx;color:#82766a;font-size:16rpx;line-height:1.55}.link-button{display:block;margin:13rpx auto 0;padding:0;background:transparent;color:#93705d;font-size:16rpx}.result-kicker{display:block;color:#9d7a5e;font-size:14rpx;font-weight:900;letter-spacing:2rpx}.result-image{width:100%;height:430rpx;margin-top:15rpx;border-radius:17rpx;background:#eee7dc}.result-placeholder{display:flex;align-items:center;justify-content:center;height:260rpx;margin-top:15rpx;flex-direction:column;gap:9rpx;border-radius:17rpx;background:linear-gradient(145deg,#d9e7dc,#ead9cc);color:#557365}.result-placeholder text:first-child{font-family:"Songti SC","STSong",serif;font-size:62rpx}.result-placeholder text:last-child{font-size:16rpx}.next-grid{display:grid;gap:10rpx;margin-top:17rpx}.next-card{display:grid;grid-template-columns:48rpx minmax(0,1fr) 18rpx;align-items:center;gap:10rpx;padding:13rpx;border:1rpx solid #e2d8cd;border-radius:15rpx;background:#fffefa}.next-card>text:first-child{display:grid;place-items:center;width:44rpx;height:44rpx;border-radius:13rpx;background:#edf3eb;color:#5e806e;font-family:"Songti SC","STSong",serif;font-size:24rpx;font-weight:800}.next-card view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.next-card view text:first-child{color:#473d35;font-size:19rpx;font-weight:800}.next-card view text:last-child{color:#92867a;font-size:14rpx}.next-card>text:last-child{color:#a16f59;font-size:31rpx}.view-grid{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:15rpx}.view-card{overflow:hidden;border:1rpx solid #e2d8cd;border-radius:14rpx;background:#fffefa}.view-card image,.view-placeholder{display:block;width:100%;height:190rpx;background:#eee8df}.view-placeholder{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:5rpx;color:#817367;font-size:15rpx}.view-card>text:last-child{display:block;padding:8rpx 10rpx;color:#6f6257;font-size:15rpx;font-weight:800}.model-success{display:flex;align-items:center;gap:14rpx;margin-top:18rpx;padding:16rpx;border-radius:16rpx;background:#e8f0e9}.model-success>text{display:grid;place-items:center;width:74rpx;height:74rpx;border-radius:22rpx;background:#5f7d6e;color:#fff;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.model-success view{display:flex;flex:1;flex-direction:column;gap:6rpx}.model-success view text:first-child{color:#4c6e5c;font-size:20rpx;font-weight:800}.model-success view text:last-child{color:#789082;font-size:14rpx;line-height:1.4}.outline-button{border:1rpx solid #9ab4a2;background:#f7fbf6;color:#557564}.loading-bar{position:fixed;z-index:7;right:20rpx;bottom:115rpx;left:20rpx;padding:12rpx 14rpx;border:1rpx solid #d9c8b5;border-radius:13rpx;background:#fff7eb;color:#96704f;font-size:15rpx;text-align:center;box-shadow:0 8rpx 20rpx rgba(81,58,35,.12)}.bottom-actions{position:fixed;z-index:6;right:0;bottom:0;left:0;display:flex;justify-content:space-around;padding:13rpx 20rpx calc(13rpx + env(safe-area-inset-bottom));border-top:1rpx solid rgba(110,91,70,.14);background:rgba(247,243,237,.96);backdrop-filter:blur(13rpx)}.bottom-actions button{margin:0;background:transparent;color:#6f6256;font-size:16rpx}.bottom-actions button::after{border:0}
</style>
