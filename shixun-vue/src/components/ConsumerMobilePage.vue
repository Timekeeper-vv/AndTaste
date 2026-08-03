<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { User } from '../types'
import andTasteLogo from '../assets/and_taste.png'
import QRCode from 'qrcode'
import MaterialModelStudio from './MaterialModelStudio.vue'
import CustomerSupportWidget from './CustomerSupportWidget.vue'
import { requestAssetPreviewAccess, requestAssetPreviewUrl } from '../utils/assetAccess'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error']; logout: [] }>()

type Tab = 'image' | 'model' | 'gallery'
type Phase = 'idle' | 'optimize' | 'generate' | 'save' | 'done'

const tab = ref<Tab>('image')
const busy = ref(false)
const stage = ref('')
const phase = ref<Phase>('idle')
type CreationPurpose = '' | 'personal' | 'museum_sale'
const creationPurpose = ref<CreationPurpose>('')
const purposeGate = ref<HTMLElement | null>(null)
const purposeStep = ref<'purpose' | 'museum'>('purpose')
const selectedPurposeMuseum = ref<any | null>(null)
const purposeRegion = ref('')
function recommendationForMuseum(museum: any) {
  if (!museum) return null
  if (museum.recommendation) return museum.recommendation
  // 前端兜底：即使服务器尚未发布最新推荐接口，选中后也必须能显示策略卡片。
  const id = String(museum.id || '')
  const flagship = ['national-museum', 'shanghai-museum', 'nanjing-museum', 'shaanxi-history', 'qinshihuang-museum', 'hunan-museum', 'sanxingdui-museum', 'suzhou-museum'].includes(id)
  const emerging = ['ganzhou-museum', 'ningbo-museum', 'shenzhen-museum', 'qinhuangdao-museum', 'capital-museum', 'china-art-museum'].includes(id)
  if (flagship) return { badge: '高客流 · 高竞争', trafficLevel: '高', competitionLevel: '高', breakoutPotential: '中高', advantages: '曝光机会大、品牌背书强、游客消费场景成熟。', risks: '同类竞争强，需要更成熟的设计、供应链和差异化 IP 才容易突围。', disclaimer: '策略标签为系统测试建议，请以实际授权、客流、渠道规则和市场调研为准。' }
  if (emerging) return { badge: '竞争较低 · 更易试爆款', trafficLevel: '中低', competitionLevel: '较低', breakoutPotential: '高', advantages: '同质竞争较低，地域题材和新颖产品更容易被看见，适合小批量试爆款。', risks: '自然客流相对有限，需要更精准的定价、陈列和线上传播配合。', disclaimer: '策略标签为系统测试建议，请以实际授权、客流、渠道规则和市场调研为准。' }
  return { badge: '客流与竞争较均衡', trafficLevel: '中高', competitionLevel: '中', breakoutPotential: '中高', advantages: '客流与竞争相对均衡，既适合展示品牌，也适合稳定测试转化。', risks: '需要围绕当地文化符号、礼品属性和价格带做清晰差异化。', disclaimer: '策略标签为系统测试建议，请以实际授权、客流、渠道规则和市场调研为准。' }
}
const selectedMuseumRecommendation = computed(() => recommendationForMuseum(selectedPurposeMuseum.value))
const purposeOptions = [
  { value: 'personal' as const, title: '个人收藏 / 送礼', desc: '用于自己收藏、赠送亲友，不进入售卖渠道。', tag: '不可售卖' },
  { value: 'museum_sale' as const, title: '售卖（景区、博物馆）', desc: '面向景区文创店、博物馆文创店、展陈空间或文旅渠道售卖。', tag: '可提交生产' },
]
const imageConfig = ref<any>({})
const tripoConfig = ref<any>({})
const creditAccount = ref<any>(null)
const creditRules = ref<any>({})
const assets = ref<any[]>([])
const imageResult = ref<any>(null)
const doubaoMultiViewResult = ref<any[]>([])
const doubaoReferenceAssetId = ref<number | null>(null)
const doubaoReferencePreviewUrl = ref('')
const modelResult = ref<any>(null)
const imagePreviewUrl = ref('')
const imageEditPreviewUrl = ref('')
const uploadPreviewUrl = ref('')
const modelProgress = ref(0)
const modelTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const imageAnchor = ref<HTMLElement | null>(null)
const modelAnchor = ref<HTMLElement | null>(null)
const touchStartX = ref(0)
const touchStartY = ref(0)
const previewAsset = ref<any | null>(null)
const previewReady = ref(false)
const previewLoadFailed = ref(false)
const modelViewerLoaded = ref(false)
const previewDownloadFormat = ref<'GLB' | 'OBJ' | 'STL'>('GLB')
const previewDownloading = ref(false)
const creditPanelOpen = ref(false)
const rechargePackages = ref<any[]>([])
const paymentChannelEnabled = ref(false)
const wechatPaymentEnabled = ref(false)
const manualPaymentEnabled = ref(false)
const paymentOrder = ref<any | null>(null)
const paymentQrUrl = ref('')
const paymentLoading = ref(false)
const paymentError = ref('')
const paymentTimer = ref<ReturnType<typeof setInterval> | null>(null)
const submittedAssetIds = ref<Set<number>>(new Set())
const submittingAssetIds = ref<Set<number>>(new Set())
const productionRequests = ref<any[]>([])
const museums = ref<any[]>([])
const museumRegion = reactive({ province: '', city: '' })
const productionModal = ref<any | null>(null)
const submittingProduction = ref(false)
const productionForm = reactive({
  requestType: 'sample' as 'sample' | 'bulk',
  quantity: 1,
  selfShipQuantity: 1,
  recipientName: '',
  recipientPhone: '',
  recipientAddress: '',
  note: '',
  museumDistribution: [] as Array<{ museumId: string; museumName: string; quantity: number }>,
})
const CONSUMER_TRIPO_MODEL_VERSION = 'v3.1-20260211'

const imageForm = reactive({
  rawPrompt: '一款适合年轻游客的城市味道文创礼盒，温暖、精致、有官方文创质感',
  prompt: '',
  usageGuide: '',
  style: '官方文创',
  imagenAspectRatio: '1:1',
  imagenImageSize: '1K',
  imagenOutputFormat: 'png',
  generationMode: 'single' as 'single' | 'image_to_image' | 'multiview',
  inputAssetId: null as number | null,
})

const materialOptions = [
  { label: '陶瓷釉面', prompt: 'glazed ceramic, smooth glossy glaze, subtle kiln texture' },
  { label: '金属质感', prompt: 'brushed metal, premium metallic luster, refined engraved details' },
  { label: '木质温润', prompt: 'natural wood, warm grain, matte handcrafted finish' },
  { label: '亚克力透明', prompt: 'transparent acrylic, polished edges, clear glossy surface' },
  { label: '树脂潮玩', prompt: 'vinyl resin toy, soft matte finish, rounded premium surface' },
  { label: '织物布艺', prompt: 'woven textile, soft fabric texture, visible fine fibers' },
  { label: '全毛绒', prompt: 'soft premium plush toy fabric, dense short pile faux fur, fuzzy fibers, velvety surface, padded stuffed volume, subtle seams, embroidered details, no glossy plastic' },
  { label: 'PPC 高精硬塑', prompt: 'precision injection-molded PPC polymer, high-density satin engineering plastic, crisp parting lines, subtle micro orange-peel texture, clean tight seams, accurate small details, premium non-glossy polymer surface, 8k PBR material, no fabric, no fur, no metal' },
]
const productCategories = [
  { key: 'magnet', label: '冰箱贴', materials: ['PVC', '搪胶', '树脂', '金属'], image: '博物馆文创冰箱贴，背面预留平整磁铁位，图案清晰、边缘圆润，适合游客伴手礼', model: '文博主题冰箱贴，背面平整磁铁位，主体厚度适中，边缘圆角，浮雕不超过安全深度，适合量产打样' },
  { key: 'plush', label: '毛绒玩具', materials: ['全毛绒', '短毛绒', '超柔绒'], image: '原创文博守护兽毛绒玩具，短密绒毛，刺绣五官，拼色裁片清晰，温暖棚拍产品图', model: '原创文博守护兽全毛绒玩具，圆润填充体，短密绒毛，刺绣五官，独立布料裁片与可见缝线，适合打样', template: 'plush_toy', materialLabel: '全毛绒' },
  { key: 'pvc', label: 'PVC / 搪胶公仔', materials: ['PVC', '搪胶', '软胶'], image: '原创文博潮玩公仔，PVC 搪胶量产感，圆润安全，分件涂装清晰，商业产品渲染', model: '原创文博潮玩 PVC 搪胶公仔，合理分件，避免深倒扣，厚薄均匀，清楚分型线与喷涂区域，适合量产打样', template: 'collectible', materialLabel: '树脂潮玩' },
  { key: 'hardplastic', label: '硬塑摆件', materials: ['PPC', 'ABS', '树脂'], image: '原创文博精密硬塑摆件，低光泽注塑表面，分件紧密，浅浮雕纹样清楚，商品棚拍', model: '原创文博精密硬塑摆件，PPC 高精硬塑，分件合理、壁厚均匀、浅浮雕清晰、无悬空细杆，适合量产打样', template: 'ppc_precision', materialLabel: 'PPC 高精硬塑' },
  { key: 'keychain', label: '钥匙扣', materials: ['亚克力', 'PVC', '金属'], image: '原创城市文化钥匙扣，轮廓简洁，挂孔牢固，适合博物馆文创商店陈列', model: '原创城市文化钥匙扣，挂孔加厚且牢固，边缘圆润，避免细小悬空结构，适合生产' },
  { key: 'giftbox', label: '礼盒', materials: ['纸质', '木质', '金属'], image: '原创城市文化伴手礼礼盒，纸质包装，局部烫金，陈列清晰，适合游客送礼', model: '原创文创礼盒结构摆件，纸质礼盒比例合理，开合结构明确，适合打样展示' },
]
const selectedProductKey = ref('magnet')
const selectedMaterial = ref('PVC')
const productProfile = computed(() => productCategories.find(item => item.key === selectedProductKey.value) || productCategories[0])
const productionAssessment = ref<any | null>(null)
const rightsServiceOpen = ref(false)
const rightsService = ref('')
const hotChannels = [
  { name: '国家级综合馆', monogram: '国', kind: '高客流渠道', tags: ['客流高', '竞争高'], desc: '适合成熟系列、强叙事礼盒和高完成度潮玩。', strategy: '曝光与背书强；先用小批量测试差异化。' },
  { name: '城市历史博物馆', monogram: '城', kind: '城市文旅渠道', tags: ['客流稳', '送礼强'], desc: '地方文化记忆更强，适合城市限定与伴手礼。', strategy: '用地域符号做系列化，价格带更容易覆盖。' },
  { name: '小众景区文创店', monogram: '景', kind: '低竞争试验场', tags: ['竞争低', '易出爆款'], desc: '自然客流有限，但新品更容易获得完整陈列位。', strategy: '小景区更适合“小批量 + 快迭代”试爆款。' },
]
const successCases = [
  { title: '青铜纹样冰箱贴', creator: '设计师 · 乔木', note: '用简化纹样 + 低客单价完成首轮试销', metric: '测试转化 18%' },
  { title: '城市守护兽毛绒', creator: '设计师 · 初晴', note: '刺绣五官与短毛绒工艺，适合亲子客群', metric: '复购意向 72%' },
  { title: '馆藏色礼盒', creator: '设计师 · 南山', note: '把器物配色变成可送礼包装语言', metric: '礼赠场景 TOP 3' },
]
const rankingPeriod = ref<'month' | 'quarter' | 'year'>('month')
const salesRankings = { month: ['青铜纹样冰箱贴', '城市守护兽毛绒', '鎏金书签礼盒'], quarter: ['城市守护兽毛绒', '馆藏色礼盒', '山水亚克力钥匙扣'], year: ['青铜纹样冰箱贴', '馆藏色礼盒', '城市守护兽毛绒'] }

const modelForm = reactive({
  mode: 'image_to_model' as 'image_to_model' | 'multiview_to_model' | 'text_to_model',
  rawPrompt: '山城街巷主题亚克力钥匙扣，立体浮雕层次，适合文创打样',
  prompt: '',
  inputAssetId: null as number | null,
  promptTemplate: 'universal',
  materialLabel: '陶瓷釉面',
  materialPrompt: 'glazed ceramic, smooth glossy glaze, subtle kiln texture',
  multiviewAssetIds: { front: null as number | null, left: null as number | null, back: null as number | null, right: null as number | null },
})
const multiviewPreviewUrls = reactive<Record<'front' | 'left' | 'back' | 'right', string>>({ front: '', left: '', back: '', right: '' })

const recentImages = computed(() => assets.value.filter(x => x.assetType === 'image').slice(0, 8))
const recentModels = computed(() => assets.value.filter(x => x.assetType === 'model').slice(0, 8))
const recentProductionRequests = computed(() => productionRequests.value.slice(0, 8))
const canGenerateModel = computed(() => {
  if (modelForm.mode === 'image_to_model') return !!modelForm.inputAssetId
  if (modelForm.mode === 'multiview_to_model') return !!modelForm.multiviewAssetIds.front && Object.values(modelForm.multiviewAssetIds).filter(Boolean).length >= 2
  return !!modelForm.rawPrompt.trim()
})
const previewModelUrl = ref('')
const previewDownloadUrl = computed(() => previewAsset.value?.id
  ? previewDownloadFormat.value === 'GLB'
    ? previewModelUrl.value
    : `/api/creative/ai/assets/${encodeURIComponent(String(previewAsset.value.id))}/download-model?format=${encodeURIComponent(previewDownloadFormat.value)}`
  : previewAsset.value?.fileUrl || previewAsset.value?.modelUrl || previewModelUrl.value)

const creditBalance = computed(() => Number(creditAccount.value?.balance ?? 0))
const imageCost = computed(() => Number(creditRules.value?.image2d ?? 1))
const modelCost = computed(() => modelForm.mode === 'text_to_model' ? Number(creditRules.value?.textTo3d ?? 60) : Number(creditRules.value?.imageTo3d ?? 70))
const convertCost = computed(() => Number(creditRules.value?.modelConvert ?? 1))
const museumProvinces = computed(() => [...new Set(museums.value.map(m => m.province).filter(Boolean))])
const museumCities = computed(() => [...new Set(museums.value.filter(m => !museumRegion.province || m.province === museumRegion.province).map(m => m.city).filter(Boolean))])
const filteredMuseums = computed(() => museums.value.filter(m =>
  (!museumRegion.province || m.province === museumRegion.province) &&
  (!museumRegion.city || m.city === museumRegion.city)
))
const purposeRegions = computed(() => {
  const seen = new Set<string>()
  return museums.value.reduce<Array<{ value: string; label: string; province: string; city: string }>>((items, museum) => {
    const province = String(museum.province || '')
    const city = String(museum.city || '')
    const value = `${province}${city}`
    if (province && city && !seen.has(value)) { seen.add(value); items.push({ value, label: `${province} · ${city}`, province, city }) }
    return items
  }, [])
})
const purposeMuseums = computed(() => {
  const region = purposeRegions.value.find(item => item.value === purposeRegion.value)
  return region ? museums.value.filter(museum => museum.province === region.province && museum.city === region.city) : []
})
const selectedPurposeLabel = computed(() => purposeOptions.find(x => x.value === creationPurpose.value)?.title || '')
const selectedPurposeFullText = computed(() => creationPurpose.value === 'personal' ? '个人收藏/送礼（不可售卖）' : creationPurpose.value === 'museum_sale' ? '售卖（景区、博物馆）' : '未选择')
const reviewFlowTitle = computed(() => creationPurpose.value === 'museum_sale' ? '博物馆审批' : '作品审核')
const reviewSubmitText = computed(() => creationPurpose.value === 'museum_sale' ? '提交博物馆审批' : '提交作品审核')
const reviewSubmittedText = computed(() => creationPurpose.value === 'museum_sale' ? '已提交博物馆审批' : '已提交作品审核')
const totalWorks = computed(() => recentImages.value.length + recentModels.value.length)
const reviewCount = computed(() => assets.value.filter(x => String(x.status || x.assetStatus || '') === 'review').length)
const approvedCount = computed(() => assets.value.filter(x => String(x.status || x.assetStatus || '') === 'approved').length)
const flowActiveIndex = computed(() => {
  if (!creationPurpose.value) return 0
  if (busy.value) return 1
  if (reviewCount.value > 0) return 2
  if (approvedCount.value > 0 || recentProductionRequests.value.length > 0) return 3
  return 1
})
const flowSteps = computed(() => [
  { key: 'purpose', no: '01', title: '确定用途', desc: selectedPurposeLabel.value || '先选创作方向' },
  { key: 'create', no: '02', title: 'AI创作', desc: busy.value ? (stage.value || '正在处理') : '图片 / 3D一键生成' },
  { key: 'review', no: '03', title: reviewFlowTitle.value, desc: reviewCount.value ? `${reviewCount.value}件待审核` : '提交用途审核' },
  { key: 'deliver', no: '04', title: creationPurpose.value === 'museum_sale' ? '打样生产' : '作品交付', desc: recentProductionRequests.value.length ? `${recentProductionRequests.value.length}条申请` : '通过后继续推进' },
])
const currentStageText = computed(() => busy.value ? (stage.value || '正在处理') : phase.value === 'done' ? '作品已保存' : '准备就绪')
const imagePromptPresets = [
  '江西博物馆青铜器纹样冰箱贴，年轻人喜欢，官方文创质感',
  '城市味道伴手礼礼盒，温暖包装，适合送礼',
  '国潮可爱IP钥匙扣，精致包装，适合博物馆商店',
]
const imageShowcaseTemplates = [
  {
    id: 'museum-gift-box',
    title: '文博伴手礼礼盒',
    subtitle: '馆藏灵感 · 可售包装视觉',
    image: '/and-taste-sample-1.svg',
    prompt: '一款面向博物馆文创商店的高端文化伴手礼礼盒，以馆藏纹样和城市文化为灵感，温暖克制的东方配色，精致纸质包装与局部烫金工艺，产品正面展示，干净高级的商业产品摄影，适合游客送礼和陈列售卖',
    style: '官方文创',
    ratio: '1:1',
  },
]
const modelTemplateOptions = [
  { key: 'universal', label: '通用产品', desc: '适合普通文创和摆件' },
  { key: 'collectible', label: '潮玩手办', desc: '圆润比例、精致涂装' },
  { key: 'oriental', label: '国风器物', desc: '纹样、釉色与工艺感' },
  { key: 'plush_toy', label: '毛绒玩具', desc: '蓬松软体、短绒、刺绣' },
  { key: 'ppc_precision', label: 'PPC 精密硬塑', desc: '注塑分件、清晰细节' },
]
const modelShowcaseTemplates = [
  {
    id: 'precision-model-demo',
    title: '精密文创摆件 3D 范例',
    subtitle: '3D 成品预览 · 可继续换材质',
    image: '/and-taste-sample-3.svg',
    presetLabel: 'PPC 精密摆件',
  },
]
const modelPromptPresets = [
  { label: '亚克力钥匙扣', text: '山城街巷主题亚克力钥匙扣，边缘圆润，有浮雕层次，适合打样', template: 'universal', materialLabel: '亚克力透明' },
  { label: '青铜纹样冰箱贴', text: '青铜器纹样冰箱贴立体模型，金属质感，背面平整', template: 'oriental', materialLabel: '金属质感' },
  { label: '文博潮玩摆件', text: '可爱文博守护兽潮玩摆件，圆润安全，适合桌面陈列', template: 'collectible', materialLabel: '树脂潮玩' },
  { label: '毛绒守护兽', text: '一只可爱的文博守护兽毛绒玩具，圆润头身比例，短耳朵和小尾巴，奶油白与暖棕拼色，柔软填充感，短密绒毛表面，刺绣眼睛和鼻子，局部可见细腻缝线，适合博物馆文创商店销售', template: 'plush_toy', materialLabel: '全毛绒' },
  { label: 'PPC 精密摆件', text: '一款博物馆文创守护兽精密硬塑摆件，圆润但边界清晰的轮廓，分件结构合理，耳朵、徽章和底座细节锐利，细窄凹槽与浅浮雕纹样清楚可见，采用浅暖灰 PPC 高精硬塑视觉材质，低光泽注塑表面，局部可见细致分型线，适合量产打样与桌面陈列', template: 'ppc_precision', materialLabel: 'PPC 高精硬塑' },
]

function selectProductCategory(key: string) {
  selectedProductKey.value = key
  const profile = productProfile.value
  selectedMaterial.value = profile.materials[0]
  imageForm.rawPrompt = profile.image
  modelForm.rawPrompt = profile.model
  if (profile.template) selectModelTemplate(profile.template)
  if (profile.materialLabel) chooseModelMaterial(profile.materialLabel)
}
function selectProductMaterial(material: string) {
  selectedMaterial.value = material
  const mapping: Record<string, string> = { '全毛绒': '全毛绒', 'PPC': 'PPC 高精硬塑', '树脂': '树脂潮玩', '金属': '金属质感', '亚克力': '亚克力透明' }
  if (mapping[material]) chooseModelMaterial(mapping[material])
}
async function refreshProductionAssessment() {
  const response = await fetch('/api/creative/ai/production-feasibility', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ productCategory: productProfile.value.label, material: selectedMaterial.value, prompt: modelForm.rawPrompt || imageForm.rawPrompt }) })
  if (!response.ok) throw new Error('生产可行性初筛服务不可用')
  productionAssessment.value = await response.json()
}
async function submitRightsService(assetId?: number) {
  if (!rightsService.value) { emit('alert', '请选择需要咨询的版权服务', 'error'); return }
  const response = await fetch('/api/creative/ai/consumer/copyright-consultations', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ assetId, service: rightsService.value }) })
  const data = await response.json().catch(() => null)
  if (!response.ok) { emit('alert', data?.message || '服务登记失败', 'error'); return }
  rightsServiceOpen.value = false; rightsService.value = ''
  emit('alert', data?.message || '版权服务咨询已登记', 'success')
}

function selectCreationPurpose(value: 'personal' | 'museum_sale') {
  if (value === 'museum_sale') {
    purposeStep.value = 'museum'
    nextTick(() => purposeGate.value?.focus())
    return
  }
  creationPurpose.value = value
  document.body.style.overflow = ''
  emit('alert', `已选择创作目的：${selectedPurposeFullText.value}`, 'success')
}
function backToPurposeChoice() { purposeStep.value = 'purpose' }
function changePurposeRegion() {
  selectedPurposeMuseum.value = null
  museumRegion.province = ''
  museumRegion.city = ''
  productionForm.museumDistribution = []
}
function confirmMuseumPurpose() {
  const museum = selectedPurposeMuseum.value
  if (!museum) {
    emit('alert', '请先选择要合作的博物馆', 'error')
    return
  }
  selectMuseum(museum)
  creationPurpose.value = 'museum_sale'
  document.body.style.overflow = ''
  emit('alert', `已选择售卖去向：${museum.name}`, 'success')
}

function switchTab(next: Tab) {
  if (tab.value === next) return
  tab.value = next
  nextTick(() => document.querySelector('.mobile-page-wrap')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function handleTouchStart(e: TouchEvent) {
  touchStartX.value = e.touches[0]?.clientX || 0
  touchStartY.value = e.touches[0]?.clientY || 0
}

function handleTouchEnd(e: TouchEvent) {
  const end = e.changedTouches[0]
  if (!end || busy.value || previewAsset.value || productionModal.value || creditPanelOpen.value) return
  const dx = end.clientX - touchStartX.value
  const dy = end.clientY - touchStartY.value
  if (Math.abs(dx) < 72 || Math.abs(dx) < Math.abs(dy) * 1.25) return
  const tabs: Tab[] = ['image', 'model', 'gallery']
  const idx = tabs.indexOf(tab.value)
  const next = dx < 0 ? tabs[Math.min(tabs.length - 1, idx + 1)] : tabs[Math.max(0, idx - 1)]
  switchTab(next)
}

function applyImagePreset(text: string) {
  imageForm.rawPrompt = text
}
function applyImageShowcase(template: typeof imageShowcaseTemplates[number]) {
  imageForm.rawPrompt = template.prompt
  imageForm.style = template.style
  imageForm.imagenAspectRatio = template.ratio
  emit('alert', `已套用「${template.title}」视觉模板，可继续修改文字后生成`, 'success')
}

function chooseModelMaterial(label: string) {
  const material = materialOptions.find(item => item.label === label)
  if (!material) return
  modelForm.materialLabel = material.label
  modelForm.materialPrompt = material.prompt
}
function selectModelTemplate(template: string) {
  modelForm.promptTemplate = template
  if (template === 'plush_toy') chooseModelMaterial('全毛绒')
  if (template === 'ppc_precision') chooseModelMaterial('PPC 高精硬塑')
}
function applyModelPreset(preset: typeof modelPromptPresets[number]) {
  modelForm.rawPrompt = preset.text
  modelForm.mode = 'text_to_model'
  modelForm.promptTemplate = preset.template
  chooseModelMaterial(preset.materialLabel)
}
function applyModelShowcase(template: typeof modelShowcaseTemplates[number]) {
  const preset = modelPromptPresets.find(item => item.label === template.presetLabel)
  if (preset) applyModelPreset(preset)
  emit('alert', `已套用「${template.title}」建模方案，可继续修改描述后生成`, 'success')
}

function changeCreationPurpose() {
  creationPurpose.value = ''
  purposeStep.value = 'purpose'
  selectedPurposeMuseum.value = null
  document.body.style.overflow = 'hidden'
  window.scrollTo({ top: 0, behavior: 'smooth' })
  nextTick(() => purposeGate.value?.focus())
}

function openCreditPanel() { creditPanelOpen.value = true }
function stopPaymentPolling() {
  if (paymentTimer.value) clearInterval(paymentTimer.value)
  paymentTimer.value = null
}
function closePaymentOrder() {
  stopPaymentPolling()
  paymentOrder.value = null
  paymentQrUrl.value = ''
  paymentError.value = ''
}
function closeCreditPanel() {
  closePaymentOrder()
  creditPanelOpen.value = false
}
async function loadPaymentPackages() {
  try {
    const data = await json('/api/payments/packages')
    rechargePackages.value = Array.isArray(data?.items) ? data.items : []
    const channels = Array.isArray(data?.channels) ? data.channels : []
    wechatPaymentEnabled.value = !!channels.find((x: any) => x.code === 'wechat' && x.enabled)
    manualPaymentEnabled.value = !!channels.find((x: any) => x.code === 'manual_wechat_qr' && x.enabled)
    paymentChannelEnabled.value = wechatPaymentEnabled.value || manualPaymentEnabled.value
  } catch {
    rechargePackages.value = []
    paymentChannelEnabled.value = false
    wechatPaymentEnabled.value = false
    manualPaymentEnabled.value = false
  }
}
async function refreshPaymentOrder() {
  if (!paymentOrder.value?.orderNo) return
  const r = await fetch(`/api/payments/orders/${encodeURIComponent(paymentOrder.value.orderNo)}`, {
    cache: 'no-store',
  })
  if (!r.ok) return
  const latest = await r.json()
  paymentOrder.value = latest
  if (latest.status === 'paid') {
    stopPaymentPolling()
    await load()
    emit('alert', `充值成功，${latest.credits} 点已到账`, 'success')
  }
  if (['closed', 'failed'].includes(latest.status)) stopPaymentPolling()
}
async function createPaymentOrder(pkg: any) {
  paymentError.value = ''
  if (!paymentChannelEnabled.value) {
    paymentError.value = '支付通道当前不可用，请稍后再试。'
    return
  }
  paymentLoading.value = true
  try {
    const channel = wechatPaymentEnabled.value ? 'wechat' : 'manual_wechat_qr'
    const r = await fetch('/api/payments/orders', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ packageCode: pkg.code, channel }),
    })
    const data = await r.json().catch(() => null)
    if (!r.ok) throw new Error(data?.message || `HTTP ${r.status}`)
    paymentOrder.value = data
    // 人工收款码本身就是一张图片：直接展示图片地址，不能再把图片
    // 地址编码成另一张二维码，否则扫码后只会打开图片而不会完成收款。
    // 微信 Native 返回的 weixin:// 链接则仍需在页面上生成二维码。
    const codeUrl = String(data?.codeUrl || '').trim()
    const isManualQr = data?.channel === 'manual_wechat_qr'
    const isImageUrl = /^(?:https?:\/\/|\/|data:image\/|blob:)/i.test(codeUrl)
    if (isManualQr && isImageUrl) {
      paymentQrUrl.value = codeUrl
    } else if (isManualQr && codeUrl) {
      // 兼容管理员填写 `payment-collection-qr.jpg` 这类相对路径。
      paymentQrUrl.value = `/${codeUrl.replace(/^\/+/, '')}`
    } else if (codeUrl) {
      paymentQrUrl.value = await QRCode.toDataURL(codeUrl, { width: 360, margin: 1, color: { dark: '#1f1713', light: '#ffffff' } })
    } else {
      throw new Error('支付服务未返回收款码，请联系平台管理员')
    }
    stopPaymentPolling()
    if (data.channel === 'wechat') paymentTimer.value = setInterval(refreshPaymentOrder, 2000)
  } catch (e: any) {
    paymentError.value = e?.message || '创建支付订单失败'
  } finally {
    paymentLoading.value = false
  }
}
async function copyPaymentCode() {
  if (!paymentOrder.value?.codeUrl) return
  await navigator.clipboard?.writeText(paymentOrder.value.codeUrl).catch(() => {})
  emit('alert', '支付链接已复制，可在微信中打开', 'success')
}
async function completeManualPayment() {
  if (!paymentOrder.value?.orderNo) return
  paymentLoading.value = true
  try {
    const r = await fetch(`/api/payments/orders/${encodeURIComponent(paymentOrder.value.orderNo)}/manual-complete`, {
      method: 'POST',
    })
    const data = await r.json().catch(() => null)
    if (!r.ok) throw new Error(data?.message || `HTTP ${r.status}`)
    paymentOrder.value = data
    emit('alert', '已提交收款核验，管理员确认后额度自动到账', 'success')
  } catch (e: any) {
    paymentError.value = e?.message || '提交支付完成状态失败'
  } finally {
    paymentLoading.value = false
  }
}

async function downloadPreviewModel() {
  const url = previewDownloadUrl.value
  const format = previewDownloadFormat.value
  const id = previewAsset.value?.id || Date.now()
  if (!url) {
    emit('alert', '模型文件暂不可下载', 'error')
    return
  }
  previewDownloading.value = true
  emit('alert', format === 'GLB' ? '正在准备模型文件…' : `正在转换为 ${format} 格式，首次可能需要1-2分钟`, 'success')
  try {
    const response = await fetch(url, {
      cache: 'no-store',
    })
    if (!response.ok) {
      let message = ''
      try {
        const contentType = response.headers.get('content-type') || ''
        message = contentType.includes('application/json') ? (await response.json()).message : await response.text()
      } catch {}
      throw new Error(message || `HTTP ${response.status}`)
    }
    const blob = await response.blob()
    const disposition = response.headers.get('content-disposition') || ''
    const matched = /filename\*=UTF-8''([^;]+)|filename=\"?([^\";]+)\"?/i.exec(disposition)
    const filename = matched ? decodeURIComponent(matched[1] || matched[2]) : `and-taste-3d-${id}-${format.toLowerCase()}.${format.toLowerCase()}`
    const objectUrl = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1500)
    emit('alert', `已开始下载 ${format} 模型`, 'success')
    await load()
  } catch (error: any) {
    emit('alert', `下载失败：${error?.message || error}`, 'error')
  } finally {
    previewDownloading.value = false
  }
}

function displayAssetTitle(a: any): string {
  const title = String(a?.title || '')
  if (title.includes('参考图')) return '参考图'
  return a?.assetType === 'model' ? '3D作品' : '产品图作品'
}

function modelPreviewImage(a: any): string {
  return a?.previewUrl || ''
}

const workStatusText: Record<string, string> = { review: '待审核', approved: '已通过', rejected: '未通过', draft: '草稿' }
function workStatusLabel(a: any): string { return workStatusText[String(a?.status || 'draft')] || String(a?.status || '草稿') }
function workStatusClass(a: any): string { const s = String(a?.status || 'draft'); return s === 'approved' ? 'approved' : s === 'rejected' ? 'rejected' : s === 'review' ? 'review' : 'draft' }
function assetIdOf(a: any): number { return Number(a?.id || a?.assetId || 0) }
async function secureAssetResult(data: any, kind: 'image' | 'model') {
  const id = assetIdOf(data)
  if (!id) throw new Error('生成结果缺少作品编号')
  const access = await requestAssetPreviewAccess(id)
  return {
    ...data,
    id,
    assetId: id,
    fileUrl: access.url,
    previewUrl: access.previewUrl,
    ...(kind === 'model' ? { modelUrl: access.url } : { imageUrl: access.url }),
  }
}
function isSubmittedForReview(a: any): boolean { const id = assetIdOf(a); return !!id && submittedAssetIds.value.has(id) }
function isSubmittingForReview(a: any): boolean { const id = assetIdOf(a); return !!id && submittingAssetIds.value.has(id) }
function isApprovedModel(a: any): boolean { return a?.assetType === 'model' && String(a?.assetStatus || a?.status || '') === 'approved' }
function canSubmitProduction(a: any): boolean { return isApprovedModel(a) }
function requestTypeText(v?: string) { return v === 'bulk' ? '批量生产' : '打样' }
function productionStatusText(v?: string) { const map: Record<string,string> = { review:'待审核', approved:'已通过', rejected:'未通过' }; return map[String(v || 'review')] || String(v || '-') }
function productionStatusClass(v?: string) { const st=String(v || 'review'); return st === 'approved' ? 'approved' : st === 'rejected' ? 'rejected' : 'review' }
function isMuseumSalePurpose() { return creationPurpose.value === 'museum_sale' }

function ensureSingleMuseumSelection() {
  if (!isMuseumSalePurpose()) return
  if (!productionForm.museumDistribution.length) {
    const firstMuseum = filteredMuseums.value[0] || museums.value[0]
    if (firstMuseum) {
      selectMuseum(firstMuseum)
    }
  }
}

function currentMuseumDistribution() {
  if (!isMuseumSalePurpose()) return []
  ensureSingleMuseumSelection()
  const row = productionForm.museumDistribution[0]
  if (!row?.museumId) return []
  const known = museums.value.find(m => m.id === row.museumId)
  return [{
    museumId: row.museumId,
    museumName: known?.name || row.museumName || '博物馆',
    quantity: Number(productionForm.quantity || 0),
  }]
}

function canSubmitReview(a: any): boolean {
  const id = assetIdOf(a)
  const st = String(a?.assetStatus || a?.status || 'draft')
  return !!id && !isSubmittedForReview(a) && !isSubmittingForReview(a) && a?.sourceType !== 'upload' && st !== 'review' && st !== 'approved'
}

async function submitAssetForReview(a: any) {
  const id = assetIdOf(a)
  if (!id) {
    emit('alert', `作品ID不存在，无法${reviewSubmitText.value}`, 'error')
    return
  }
  if (!props.currentUser?.id) {
    emit('alert', '当前登录信息缺少用户ID，请退出后重新登录 user 账号再提交', 'error')
    return
  }
  submittingAssetIds.value = new Set([...submittingAssetIds.value, id])
  try {
    const r = await fetch(`/api/creative/ai/consumer-assets/${id}/submit-review`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        purpose: creationPurpose.value,
        museumId: creationPurpose.value === 'museum_sale' ? selectedPurposeMuseum.value?.id || '' : '',
        note: `C端用户主动提交${reviewFlowTitle.value}；创作目的：${selectedPurposeFullText.value}${creationPurpose.value === 'museum_sale' && selectedPurposeMuseum.value ? `；审批博物馆：${selectedPurposeMuseum.value.name}` : ''}`,
      }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    submittedAssetIds.value = new Set([...submittedAssetIds.value, id])
    if (a) a.status = 'review'
    await load()
    emit('alert', `${reviewSubmittedText.value}，请等待审核结果`, 'success')
  } catch (e: any) {
    emit('alert', `${reviewSubmitText.value}失败：` + (e?.message || e), 'error')
  } finally {
    const next = new Set(submittingAssetIds.value)
    next.delete(id)
    submittingAssetIds.value = next
  }
}

function setStage(text: string, nextPhase: Phase) {
  stage.value = text
  phase.value = nextPhase
}

onMounted(() => {
  // Every login starts with a deliberate destination choice; it is not persisted between sessions.
  document.body.style.overflow = 'hidden'
  load()
  nextTick(() => purposeGate.value?.focus())
})
onBeforeUnmount(() => {
  if (modelTimer.value) clearTimeout(modelTimer.value)
  stopPaymentPolling()
  if (imagePreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(imagePreviewUrl.value)
  if (imageEditPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(imageEditPreviewUrl.value)
  if (uploadPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(uploadPreviewUrl.value)
  document.body.style.overflow = ''
})

async function json(url: string) {
  const r = await fetch(url, { cache: 'no-store' })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return await r.json()
}

async function load() {
  try {
    const [i, t, a, c, prs, ms] = await Promise.all([
      json('/api/creative/ai/jimeng/config'),
      json('/api/creative/ai/tripo/config'),
      json('/api/creative/ai/assets'),
      json('/api/creative/ai/consumer-credits/account'),
      json('/api/creative/ai/consumer-production/my'),
      json('/api/creative/ai/consumer-production/museums'),
    ])
    await loadPaymentPackages()
    imageConfig.value = i
    tripoConfig.value = t
    assets.value = Array.isArray(a) ? a : []
    creditAccount.value = c
    creditRules.value = c?.rules || {}
    productionRequests.value = Array.isArray(prs) ? prs : []
    museums.value = Array.isArray(ms) ? ms : []
  } catch (e: any) {
    emit('alert', '加载移动创作页失败：' + (e?.message || e), 'error')
  }
}

async function prepareAssetPreview(assetId: number, target: 'image' | 'upload') {
  const response = await fetch(`/api/creative/ai/assets/${assetId}/content?v=${Date.now()}`, { cache: 'no-store' })
  if (!response.ok) throw new Error(`读取文件失败 HTTP ${response.status}`)
  const blob = await response.blob()
  if (!blob.type.startsWith('image/')) throw new Error(`文件类型异常：${blob.type || 'unknown'}`)
  const url = URL.createObjectURL(blob)
  if (target === 'image') {
    if (imagePreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(imagePreviewUrl.value)
  if (imageEditPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(imageEditPreviewUrl.value)
    imagePreviewUrl.value = url
  } else {
    if (uploadPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(uploadPreviewUrl.value)
    uploadPreviewUrl.value = url
  }
}

async function optimizeImagePrompt() {
  const source = [imageForm.style, imageForm.rawPrompt].filter(Boolean).join('，')
  const r = await fetch('/api/creative/ai/prompt/tripo-optimize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt: source, provider: 'jimeng', productCategory: productProfile.value.label, material: selectedMaterial.value }),
  })
  if (!r.ok) {
    const err = await r.json().catch(() => null)
    throw new Error(err?.message || `HTTP ${r.status}`)
  }
  const d = await r.json()
  imageForm.prompt = d.prompt || source
  imageForm.usageGuide = d.usageGuide || ''
}

async function generateDoubaoMultiView() {
  setStage('Doubao-Seedream-5.0-lite 正在生成正/左/后/右视图', 'generate')
  const r = await fetch('/api/creative/ai/volcengine/seedream/multiview', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt: imageForm.rawPrompt, inputAssetId: doubaoReferenceAssetId.value, size: '2K', watermark: true }),
  })
  if (!r.ok) { const err = await r.json().catch(() => null); throw new Error(err?.message || `HTTP ${r.status}`) }
  const data = await r.json()
  const rawImages = Array.isArray(data.images) ? data.images : []
  doubaoMultiViewResult.value = await Promise.all(rawImages.map(async (item: any) => {
    try { return await secureAssetResult(item, 'image') } catch { return { ...item, previewUrl: '', fileUrl: '' } }
  }))
  if (!doubaoMultiViewResult.value.length) throw new Error('Doubao 未返回多视图结果')
  await load(); phase.value = 'done'
  emit('alert', data.message || 'Doubao 多视图已保存，可直接用于 3D 建模', 'success')
}
function useDoubaoMultiViewFor3d() {
  const slots = ['front', 'left', 'back', 'right'] as const
  for (const view of slots) {
    const image = doubaoMultiViewResult.value.find(item => item.view === view)
    if (!image?.assetId) continue
    modelForm.multiviewAssetIds[view] = Number(image.assetId)
    multiviewPreviewUrls[view] = image.previewUrl || image.fileUrl || ''
  }
  modelForm.mode = 'multiview_to_model'; switchTab('model')
  emit('alert', '已把 Doubao 正/左/后/右图带入多视图 3D 建模', 'success')
}
async function generateImage() {
  if (!imageForm.rawPrompt.trim()) { emit('alert', '先写一句你想做什么产品', 'error'); return }
  if (imageForm.generationMode === 'multiview' && !doubaoReferenceAssetId.value) { emit('alert', '请先上传一张产品参考图，再生成正/左/后/右视图', 'error'); return }
  if (imageForm.generationMode === 'image_to_image' && !imageForm.inputAssetId) { emit('alert', '请先上传一张参考图，再用文字描述你想如何改造它', 'error'); return }
  busy.value = true; imageResult.value = null; doubaoMultiViewResult.value = []; setStage('正在优化创意', 'optimize')
  try {
    if (imageForm.generationMode === 'multiview') {
      await generateDoubaoMultiView(); await nextTick(); imageAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'center' }); return
    }
    if (imageForm.generationMode === 'single' && !imageConfig.value.configured) throw new Error('即梦AI签名鉴权未配置，请联系管理员配置火山AccessKeyId和SecretAccessKey')
    await optimizeImagePrompt(); setStage(imageForm.generationMode === 'image_to_image' ? '正在融合参考图与文字描述' : '正在生成图片', 'generate')
    const endpoint = imageForm.generationMode === 'image_to_image' ? '/api/creative/ai/image-to-image' : '/api/creative/ai/jimeng/text-to-image'
    const payload = imageForm.generationMode === 'image_to_image' ? { title: `图文结合 · ${productProfile.value.label}`, prompt: imageForm.prompt, inputAssetId: imageForm.inputAssetId, productCategory: productProfile.value.label, material: selectedMaterial.value } : { provider: 'jimeng', rawPrompt: imageForm.rawPrompt, prompt: imageForm.prompt, productCategory: productProfile.value.label, material: selectedMaterial.value, imagenAspectRatio: imageForm.imagenAspectRatio, imagenImageSize: imageForm.imagenImageSize, imagenOutputFormat: imageForm.imagenOutputFormat }
    const r = await fetch(endpoint, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
    if (!r.ok) { const err = await r.json().catch(() => null); throw new Error(err?.message || `HTTP ${r.status}`) }
    const d = await r.json(); if (d.creditAccount) creditAccount.value = d.creditAccount; imageResult.value = await secureAssetResult(d, 'image')
    setStage('正在保存作品', 'save'); await prepareAssetPreview(d.assetId, 'image'); await load(); await nextTick(); imageAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'center' }); phase.value = 'done'
    emit('alert', `图片已保存，可${reviewSubmitText.value}`, 'success')
  } catch (e: any) {
    phase.value = 'idle'; emit('alert', (imageForm.generationMode === 'multiview' ? 'Doubao 多视图生成失败：' : imageForm.generationMode === 'image_to_image' ? '图文结合生成失败：' : '生成图片失败：') + (e?.message || e), 'error')
  } finally { busy.value = false; stage.value = '' }
}
async function uploadImageEditReference(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  busy.value = true; setStage('正在上传图文结合参考图', 'save')
  try {
    const fd = new FormData(); fd.append('file', file); fd.append('title', 'C端图文结合参考图'); fd.append('tags', 'C端,2D,图生图,参考图')
    const r = await fetch('/api/creative/ai/assets/upload', { method: 'POST', body: fd })
    if (!r.ok) { const error = await r.json().catch(() => null); throw new Error(error?.message || `HTTP ${r.status}`) }
    const data = await r.json(); imageForm.inputAssetId = Number(data.assetId)
    if (imageEditPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(imageEditPreviewUrl.value)
    imageEditPreviewUrl.value = URL.createObjectURL(file)
    emit('alert', '参考图已上传，现在可用文字控制改造方向', 'success')
  } catch (error: any) { emit('alert', '图文结合参考图上传失败：' + (error?.message || error), 'error') }
  finally { busy.value = false; stage.value = ''; phase.value = 'idle' }
}

async function uploadDoubaoReference(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  busy.value = true; setStage('正在上传 Doubao 多视图参考图', 'save')
  try {
    const fd = new FormData(); fd.append('file', file); fd.append('title', 'Doubao 多视图产品参考图'); fd.append('tags', 'C端,Doubao,多视图,3D参考')
    const r = await fetch('/api/creative/ai/assets/upload', { method: 'POST', body: fd })
    if (!r.ok) { const error = await r.json().catch(() => null); throw new Error(error?.message || `HTTP ${r.status}`) }
    const data = await r.json(); doubaoReferenceAssetId.value = Number(data.assetId)
    if (doubaoReferencePreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(doubaoReferencePreviewUrl.value)
    doubaoReferencePreviewUrl.value = URL.createObjectURL(file)
    emit('alert', '参考图已上传，现可生成正/左/后/右多视图', 'success')
  } catch (error: any) { emit('alert', '多视图参考图上传失败：' + (error?.message || error), 'error') }
  finally { busy.value = false; stage.value = ''; phase.value = 'idle' }
}

async function uploadReference(e: Event, view?: 'front' | 'left' | 'back' | 'right') {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  busy.value = true
  setStage('正在上传参考图', 'save')
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('title', view ? `C端多视图3D参考图-${view}` : 'C端3D参考图')
    fd.append('tags', 'C端,3D参考图')
    const r = await fetch('/api/creative/ai/assets/upload', { method: 'POST', body: fd })
    if (!r.ok) throw new Error(await r.text())
    const d = await r.json()
    if (view) {
      modelForm.multiviewAssetIds[view] = d.assetId
      if (multiviewPreviewUrls[view].startsWith('blob:')) URL.revokeObjectURL(multiviewPreviewUrls[view])
      multiviewPreviewUrls[view] = URL.createObjectURL(file)
      emit('alert', `${({ front: '正面', left: '左侧', back: '背面', right: '右侧' } as const)[view]}图已上传`, 'success')
    } else {
      modelForm.inputAssetId = d.assetId
      await prepareAssetPreview(d.assetId, 'upload')
      emit('alert', '参考图已上传', 'success')
    }
  } catch (e: any) {
    emit('alert', '上传失败：' + (e?.message || e), 'error')
  } finally {
    busy.value = false
    stage.value = ''
    phase.value = 'idle'
  }
}

async function optimizeModelPrompt() {
  if (modelForm.mode !== 'text_to_model') {
    modelForm.prompt = ''
    return
  }
  if (!modelForm.rawPrompt.trim()) return
  const r = await fetch('/api/creative/ai/prompt/tripo-3d-optimize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt: `${modelForm.rawPrompt}；产品类别：${productProfile.value.label}；期望材质与表面质感：${modelForm.materialLabel}（${modelForm.materialPrompt}）`, promptTemplate: modelForm.promptTemplate, productCategory: productProfile.value.label, material: selectedMaterial.value }),
  })
  if (!r.ok) {
    const err = await r.json().catch(() => null)
    throw new Error(err?.message || `HTTP ${r.status}`)
  }
  const d = await r.json()
  modelForm.prompt = d.prompt || modelForm.rawPrompt
}

async function generateModel() {
  if (!tripoConfig.value.configured || !tripoConfig.value.serviceReachable) {
    emit('alert', '3D生成服务暂不可用，请联系管理员', 'error')
    return
  }
  if (!canGenerateModel.value) {
    emit('alert', modelForm.mode === 'image_to_model' ? '先上传一张产品参考图' : modelForm.mode === 'multiview_to_model' ? '请至少上传正面图和另一个视角' : '先写一句模型描述', 'error')
    return
  }
  busy.value = true
  modelResult.value = null
  modelProgress.value = 0
  setStage(modelForm.mode === 'text_to_model' ? '正在优化创意' : modelForm.mode === 'multiview_to_model' ? '正在整理多视图素材' : '正在提交图片', modelForm.mode === 'text_to_model' ? 'optimize' : 'generate')
  try {
    try { await refreshProductionAssessment() } catch { emit('alert', '生产可行性初筛暂不可用，已继续提交建模；请在打样前申请人工复核', 'error') }
    if (modelForm.mode === 'text_to_model') await optimizeModelPrompt()
    setStage('正在生成3D模型', 'generate')
    const isImageToModel = modelForm.mode === 'image_to_model'
    const isMultiviewToModel = modelForm.mode === 'multiview_to_model'
    const body = {
      mode: modelForm.mode,
      modelVersion: CONSUMER_TRIPO_MODEL_VERSION,
      promptTemplate: isImageToModel ? '' : modelForm.promptTemplate,
      rawPrompt: isImageToModel ? '' : modelForm.rawPrompt,
      prompt: isImageToModel ? '' : (modelForm.prompt || modelForm.rawPrompt),
      negativePrompt: isImageToModel ? '' : 'low poly, blurry, flat texture, deformed, asymmetric, noisy mesh',
      materialLabel: modelForm.materialLabel,
      materialPrompt: modelForm.materialPrompt,
      productCategory: productProfile.value.label,
      inputAssetId: isImageToModel ? modelForm.inputAssetId : null,
      multiviewAssetIds: isMultiviewToModel ? { ...modelForm.multiviewAssetIds } : { front: null, left: null, back: null, right: null },
      exportFormats: 'GLB',
      texture: true,
      pbr: true,
      textureQuality: 'extreme',
      geometryQuality: 'detailed',
      textureAlignment: 'original_image',
      orientation: 'align_image',
      autoSize: true,
      imageAutofix: true,
      quad: false,
      smartLowPoly: false,
      generateParts: false,
      exportUv: true,
      compress: false,
      faceLimit: 2000000,
    }
    const r = await fetch('/api/creative/ai/tripo/generate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const d = await r.json()
    if (d.creditAccount) creditAccount.value = d.creditAccount
    emit('alert', '已开始生成3D模型', 'success')
    await pollModel(d.jobId)
  } catch (e: any) {
    busy.value = false
    stage.value = ''
    phase.value = 'idle'
    emit('alert', '3D生成失败：' + (e?.message || e), 'error')
  }
}

async function pollModel(jobId: number) {
  if (modelTimer.value) clearTimeout(modelTimer.value)
  try {
    const r = await fetch(`/api/creative/ai/tripo/tasks/${jobId}`, { cache: 'no-store' })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const d = await r.json()
    modelProgress.value = Number(d.progress || 0)
    setStage(d.status === 'succeeded' ? '3D模型已完成' : `正在生成3D模型 ${modelProgress.value || 0}%`, d.status === 'succeeded' ? 'save' : 'generate')
    if (d.status === 'succeeded') {
      if (d.creditAccount) creditAccount.value = d.creditAccount
      modelResult.value = await secureAssetResult(d, 'model')
      busy.value = false
      stage.value = ''
      await load()
      await nextTick()
      modelAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      phase.value = 'done'
      emit('alert', `3D模型已保存，可${reviewSubmitText.value}`, 'success')
      return
    }
    if (d.status === 'failed') throw new Error(d.errorMessage || '3D生成失败')
    modelTimer.value = setTimeout(() => pollModel(jobId), 3000)
  } catch (e: any) {
    busy.value = false
    stage.value = ''
    phase.value = 'idle'
    emit('alert', '查询3D任务失败：' + (e?.message || e), 'error')
  }
}

function openProductionRequest(a: any, type: 'sample' | 'bulk') {
  if (type === 'bulk' && creationPurpose.value !== 'museum_sale') {
    emit('alert', '当前创作目的为个人收藏/送礼（不可售卖），不能提交售卖（景区、博物馆）/批量生产申请', 'error')
    return
  }
  if (!canSubmitProduction(a)) {
    emit('alert', '作品需先通过审核，并且必须是3D模型，才能提交打样或生产申请', 'error')
    return
  }
  productionModal.value = a
  productionForm.requestType = type
  productionForm.quantity = type === 'sample' ? 1 : 1000
  productionForm.selfShipQuantity = isMuseumSalePurpose() ? 0 : productionForm.quantity
  productionForm.recipientName = props.currentUser.username
  productionForm.recipientPhone = ''
  productionForm.recipientAddress = ''
  productionForm.note = type === 'sample' ? `创作目的：${selectedPurposeFullText.value}。希望先打样确认材质、尺寸和包装效果` : `创作目的：${selectedPurposeFullText.value}。计划按所选用途执行，不做个人/博物馆拆分`
  const firstMuseum = filteredMuseums.value[0] || museums.value[0]
  productionForm.museumDistribution = []
  if (isMuseumSalePurpose() && firstMuseum) selectMuseum(firstMuseum)
  document.body.style.overflow = 'hidden'
}

function closeProductionRequest() {
  productionModal.value = null
  document.body.style.overflow = ''
}

function selectMuseum(found: any) {
  if (!found) return
  selectedPurposeMuseum.value = found
  museumRegion.province = found.province || ''
  museumRegion.city = found.city || ''
  productionForm.museumDistribution = [{ museumId: found.id, museumName: found.name, quantity: Number(productionForm.quantity || 0) }]
}
function selectFirstMuseumInRegion() {
  const found = filteredMuseums.value[0]
  if (found) selectMuseum(found)
  else productionForm.museumDistribution = []
}
function changeMuseumProvince() {
  museumRegion.city = ''
  selectFirstMuseumInRegion()
}
function changeMuseumCity() {
  selectFirstMuseumInRegion()
}
function changeMuseum(row: any) {
  const found = museums.value.find(m => m.id === row.museumId)
  if (found) selectMuseum(found)
}

async function submitProductionRequest() {
  if (!productionModal.value?.id) return
  if (productionForm.requestType === 'bulk' && creationPurpose.value !== 'museum_sale') {
    emit('alert', '个人收藏/送礼（不可售卖）用途不能提交批量生产售卖申请', 'error')
    return
  }
  const quantity = Number(productionForm.quantity || 0)
  if (quantity <= 0) {
    emit('alert', '申请数量必须大于0', 'error')
    return
  }
  const museumRows = currentMuseumDistribution()
  if (isMuseumSalePurpose() && !museumRows.length) {
    emit('alert', '请选择一个博物馆，全部数量将进入该售卖去向，不支持拆分', 'error')
    return
  }
  submittingProduction.value = true
  try {
    const r = await fetch('/api/creative/ai/consumer-production/submit', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        assetId: productionModal.value.id,
        requestType: productionForm.requestType,
        purpose: creationPurpose.value,
        quantity,
        selfShipQuantity: isMuseumSalePurpose() ? 0 : quantity,
        recipientName: productionForm.recipientName,
        recipientPhone: productionForm.recipientPhone,
        recipientAddress: productionForm.recipientAddress,
        note: `创作目的：${selectedPurposeFullText.value}。${productionForm.note || ''}`,
        museumDistribution: museumRows,
      }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const d = await r.json()
    emit('alert', d.message || '申请已提交，请等待审批', 'success')
    closeProductionRequest()
    await load()
    tab.value = 'gallery'
  } catch (e: any) {
    emit('alert', '提交失败：' + (e?.message || e), 'error')
  } finally {
    submittingProduction.value = false
  }
}

function openUrl(url?: string) {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}

async function ensureModelViewer() {
  if (modelViewerLoaded.value) return
  await import('@google/model-viewer')
  modelViewerLoaded.value = true
}

async function openModelPreview(a?: any) {
  const asset = a || modelResult.value
  if (!asset?.id && !asset?.assetId && !asset?.fileUrl && !asset?.modelUrl) {
    emit('alert', '模型文件暂不可预览', 'error')
    return
  }
  previewAsset.value = {
    ...asset,
    id: asset.id || asset.assetId,
    fileUrl: asset.fileUrl || asset.modelUrl,
  }
  previewModelUrl.value = ''
  previewReady.value = false
  previewLoadFailed.value = false
  previewDownloadFormat.value = 'GLB'
  document.body.style.overflow = 'hidden'
  try {
    previewModelUrl.value = await requestAssetPreviewUrl(Number(previewAsset.value.id))
  } catch (e: any) {
    closeModelPreview()
    emit('alert', `模型预览失败：${e?.message || e}`, 'error')
  }
}

async function saveMaterialVariant(payload: { blob: Blob; materialLabel: string }) {
  const assetId = Number(previewAsset.value?.id || 0)
  if (!assetId) { emit('alert', '当前模型无法保存材质版本', 'error'); return }
  try {
    const maximumBytes = 100 * 1024 * 1024
    if (payload.blob.size > maximumBytes) {
      emit('alert', `材质版模型大小为 ${(payload.blob.size / 1024 / 1024).toFixed(1)}MB，超过 100MB 保存上限。请使用 2K 或更小贴图后重试。`, 'error')
      return
    }
    const fd = new FormData()
    fd.append('file', new File([payload.blob], `${String(previewAsset.value?.title || '3d-model')}-${payload.materialLabel}.glb`, { type: 'model/gltf-binary' }))
    fd.append('materialLabel', payload.materialLabel)
    const response = await fetch(`/api/creative/ai/assets/${assetId}/material-variants`, { method: 'POST', body: fd })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    await load()
    emit('alert', data?.message || '材质版模型已保存到作品库', 'success')
  } catch (error: any) { emit('alert', '保存材质版本失败：' + (error?.message || error), 'error') }
}

function closeModelPreview() {
  previewAsset.value = null
  previewModelUrl.value = ''
  previewReady.value = false
  previewLoadFailed.value = false
  document.body.style.overflow = ''
}
</script>

<template>
  <main class="consumer-shell immersive-shell" @touchstart.passive="handleTouchStart" @touchend.passive="handleTouchEnd">
    <div class="ambient-layer"></div>
    <header class="consumer-top">
      <div class="brand">
        <img :src="andTasteLogo" alt="之间味道" />
        <div>
          <b>之间味道</b>
          <span>文创灵感工坊</span>
        </div>
      </div>
      <button type="button" class="icon-btn" title="退出登录" @click="emit('logout')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="M16 17l5-5-5-5"/><path d="M21 12H9"/></svg>
      </button>
    </header>

    <section v-if="!creationPurpose" ref="purposeGate" class="purpose-gate" role="dialog" aria-modal="true" aria-labelledby="purpose-gate-title" tabindex="-1" @keydown.esc.prevent>
      <div class="purpose-card">
        <div class="purpose-aurora"></div>
        <div class="purpose-brand"><img :src="andTasteLogo" alt="之间味道" /><div><span>之间智造 · AI CULTURAL CREATION</span><small>从灵感到可售文创</small></div><em>2026</em></div>
        <template v-if="purposeStep === 'purpose'">
          <div class="purpose-step"><span>01</span><i></i><b>确定作品去向</b><small>两步完成创作配置</small></div>
          <h1 id="purpose-gate-title">让每一件作品，<br /><strong>找到更好的去向。</strong></h1>
          <p>选择创作目的后，系统将匹配后续审核、生产与渠道路径。</p>
          <div class="purpose-options">
            <button v-for="item in purposeOptions" :key="item.value" type="button" @click="selectCreationPurpose(item.value)">
              <i>{{ item.tag }}</i><b>{{ item.title }}</b><span>{{ item.desc }}</span><em>→</em>
            </button>
          </div>
          <div class="purpose-footnote"><span>✦</span> 用策略做选择，用创意创造价值</div>
        </template>
        <template v-else>
          <button type="button" class="purpose-back" @click="backToPurposeChoice">← 返回作品去向</button>
          <div class="purpose-step"><span>02</span><i></i><b>选择合作博物馆</b><small>获取渠道策略建议</small></div>
          <h1 id="purpose-gate-title">选对渠道，<strong>让好作品被看见。</strong></h1>
          <p>只需选择省 / 市与博物馆名称，即可查看客流、竞争、爆款潜力及优缺点建议。</p>
          <div class="purpose-museum-layout">
            <div class="purpose-museum-select purpose-museum-select-simple">
              <label><span>省 / 市</span><select v-model="purposeRegion" @change="changePurposeRegion"><option value="">请选择省 / 市</option><option v-for="region in purposeRegions" :key="region.value" :value="region.value">{{ region.label }}</option></select></label>
              <label><span>博物馆名称</span><select v-model="selectedPurposeMuseum" :disabled="!purposeMuseums.length" @change="selectMuseum(selectedPurposeMuseum)"><option :value="null">请选择博物馆</option><option v-for="museum in purposeMuseums" :key="museum.id" :value="museum">{{ museum.name }}</option></select></label>
            </div>
            <aside class="museum-recommendation" aria-live="polite">
              <template v-if="selectedMuseumRecommendation">
                <div class="museum-recommendation-head"><span>选址策略建议（测试）</span><b>{{ selectedMuseumRecommendation.badge }}</b></div>
                <div class="museum-recommendation-metrics"><span>客流潜力：<strong>{{ selectedMuseumRecommendation.trafficLevel }}</strong></span><span>竞争强度：<strong>{{ selectedMuseumRecommendation.competitionLevel }}</strong></span><span>爆款潜力：<strong>{{ selectedMuseumRecommendation.breakoutPotential }}</strong></span></div>
                <p><strong>优点：</strong>{{ selectedMuseumRecommendation.advantages }}</p>
                <p><strong>注意：</strong>{{ selectedMuseumRecommendation.risks }}</p>
                <small>{{ selectedMuseumRecommendation.disclaimer }}</small>
              </template>
              <template v-else><div class="museum-recommendation-head"><span>推荐博物馆卡片</span><b>待选择</b></div><p>先选择“省 / 市”和博物馆名称，系统会在这里显示该渠道的客流、竞争、爆款潜力，以及优缺点建议。</p></template>
            </aside>
          </div>
          <button type="button" class="purpose-confirm" :disabled="!selectedPurposeMuseum" @click="confirmMuseumPurpose">确认并进入创作</button>
        </template>
      </div>
    </section>

    <section class="studio-home">
      <div class="studio-hero">
        <div class="studio-hero-copy">
          <div class="studio-kicker"><span>✦</span> YOUR CREATIVE DESK <i>01</i></div>
          <button type="button" class="studio-purpose-pill" @click="changeCreationPurpose">{{ selectedPurposeFullText }} <b>切换</b></button>
          <h1>把文化灵感，<br /><strong>做成会被带走的作品。</strong></h1>
          <p>从一句想法开始，完成视觉创作、3D 建模、审核与生产。今天，做一件真正有机会被喜欢的文创。</p>
          <div class="studio-hero-actions">
            <button type="button" class="studio-main-action" @click="switchTab('image')"><span>✦</span> 立即开始创作</button>
            <button type="button" class="studio-sub-action" @click="switchTab('gallery')">查看我的作品 →</button>
          </div>
        </div>
        <div class="studio-hero-art" aria-hidden="true"><i class="art-ring ring-one"></i><i class="art-ring ring-two"></i><div class="art-tile tile-one"><span>AI</span><b>文化灵感</b></div><div class="art-tile tile-two"><span>3D</span><b>产品原型</b></div><em>✦</em></div>
      </div>

      <section class="studio-launcher">
        <div class="studio-section-title"><div><span>CREATE NOW</span><b>今天想先做什么？</b></div><small>选择一个创作入口，即刻开始</small></div>
        <div class="studio-launch-grid">
          <button type="button" class="launch-image" @click="switchTab('image')"><i>01</i><span class="launch-icon">✦</span><b>文字生成图片</b><small>把一句灵感，变成完整文创视觉</small><em>{{ imageCost }} 点 / 次</em><strong>开始生成 →</strong></button>
          <button type="button" class="launch-model" @click="switchTab('model')"><i>02</i><span class="launch-icon">◇</span><b>生成 3D 模型</b><small>从图片或文字，生成可预览原型</small><em>{{ modelCost }} 点 / 次</em><strong>开始建模 →</strong></button>
          <button type="button" class="launch-library" @click="switchTab('gallery')"><i>03</i><span class="launch-icon">▦</span><b>管理我的作品</b><small>查看审核、生产与可用资产</small><em>{{ totalWorks }} 件作品</em><strong>进入作品库 →</strong></button>
        </div>
      </section>

      <section class="market-discovery">
        <div class="discovery-heading"><span>CHANNEL RADAR · 测试数据</span><b>热门渠道与小景区爆款策略</b><small>不使用未经授权的机构 Logo；以下为平台测试策略卡，合作前请核验授权与真实渠道数据。</small></div>
        <div class="channel-card-row"><article v-for="channel in hotChannels" :key="channel.name" class="channel-card"><div class="channel-mark">{{ channel.monogram }}</div><div><span>{{ channel.kind }}</span><b>{{ channel.name }}</b><p>{{ channel.desc }}</p><em v-for="tag in channel.tags" :key="tag">{{ tag }}</em><small>{{ channel.strategy }}</small></div></article></div>
        <div class="proof-board"><div class="proof-cases"><span>DESIGNER STORIES · 测试案例</span><article v-for="item in successCases" :key="item.title"><i>✦</i><div><b>{{ item.title }}</b><small>{{ item.creator }} · {{ item.note }}</small></div><em>{{ item.metric }}</em></article></div><div class="proof-ranking"><div><span>SALES RANK · 模拟销量</span><button v-for="period in ['month','quarter','year']" :key="period" :class="{active:rankingPeriod===period}" @click="rankingPeriod=period as 'month' | 'quarter' | 'year'">{{ ({month:'月榜',quarter:'季榜',year:'年榜'} as any)[period] }}</button></div><ol><li v-for="(item,index) in salesRankings[rankingPeriod]" :key="item"><b>0{{ index + 1 }}</b><span>{{ item }}</span><em>{{ 98-index*17 }} 热度</em></li></ol></div></div>
      </section>

      <section class="studio-overview">
        <article class="overview-balance"><span>CREATIVE CREDIT</span><b>{{ creditBalance }}<small>点</small></b><p>创作额度充足，继续把灵感做出来。</p><button type="button" @click="openCreditPanel">充值额度 →</button></article>
        <article class="overview-route"><span>当前作品去向</span><b>{{ selectedPurposeFullText }}</b><p v-if="selectedPurposeMuseum">合作机构：{{ selectedPurposeMuseum.name }}</p><p v-else>个人创作路径，不进入售卖渠道。</p><button type="button" @click="changeCreationPurpose">调整去向</button></article>
        <article class="overview-pulse" :class="{live:busy}"><span>{{ busy ? 'AI IS WORKING' : 'CREATIVE PULSE' }}</span><b>{{ currentStageText }}</b><div><i></i><i></i><i></i><i></i><i></i></div><small>{{ busy ? '请保持页面打开，完成后自动保存到作品库。' : `已有 ${approvedCount} 件作品通过审核，可继续进入生产。` }}</small></article>
      </section>
    </section>

    <nav class="quick-tabs bottom-tabs">
      <button type="button" :class="{active:tab==='image'}" @click="switchTab('image')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m8 13 2.5-2.5L15 15l1-1 3 3"/><circle cx="8" cy="9" r="1"/></svg>
        图片 <small>{{ recentImages.length }}</small>
      </button>
      <button type="button" :class="{active:tab==='model'}" @click="switchTab('model')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m12 2 8 4.5v9L12 20l-8-4.5v-9L12 2Z"/><path d="M12 11 4.5 6.8"/><path d="M12 11v9"/><path d="m12 11 7.5-4.2"/></svg>
        3D <small>{{ recentModels.length }}</small>
      </button>
      <button type="button" :class="{active:tab==='gallery'}" @click="switchTab('gallery')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
        作品 <small>{{ totalWorks }}</small>
      </button>
    </nav>

    <div class="mobile-page-wrap">
    <Transition name="mobile-page" mode="out-in">
    <section v-if="tab==='image'" key="image" class="panel creation-panel creation-redesign image-redesign">
      <header class="creation-spotlight">
        <div><span>IMAGE LAB · 01</span><h2>用一句话，<strong>生成一张想被带走的产品图。</strong></h2><p>把文化、材质、图案和使用场景交给 AI，生成后自动进入你的作品库。</p></div>
        <aside><i>16</i><span>积分 / 次</span><small>生成成功才入库</small></aside>
      </header>
      <section class="product-brief" aria-label="产品与材质引导"><div class="product-brief-title"><span>STEP 01 · PRODUCT BLUEPRINT</span><b>先选要做什么，AI 才能按真实产品思路生成</b><small>类别、材质会联动案例、提示词、3D模板和生产初筛。</small></div><div class="brief-selectors"><div><span>产品类别</span><button v-for="item in productCategories" :key="item.key" :class="{active:selectedProductKey===item.key}" @click="selectProductCategory(item.key)">{{ item.label }}</button></div><div><span>具体材质</span><button v-for="item in productProfile.materials" :key="item" :class="{active:selectedMaterial===item}" @click="selectProductMaterial(item)">{{ item }}</button></div></div><aside><b>{{ productProfile.label }} · {{ selectedMaterial }}</b><p>{{ productProfile.image }}</p><small>AI 会自动加入可生产结构建议；原创设计、授权核验与最终打样仍由你确认。</small></aside></section>
      <div class="creation-workspace">
        <div class="prompt-studio">
          <div class="prompt-studio-head"><span>创作描述</span><small>{{ imageForm.rawPrompt.length }} / 800</small></div>
          <section class="image-template-showcase"><div class="image-template-title"><div><span>VISUAL TEMPLATE</span><b>先看效果，再开始创作</b></div><small>点击样例自动带入提示词</small></div><button v-for="item in imageShowcaseTemplates" :key="item.id" type="button" class="image-template-card" @click="applyImageShowcase(item)"><img :src="item.image" :alt="item.title" /><div><span>{{ item.subtitle }}</span><b>{{ item.title }}</b><small>使用这个视觉模板 <em>→</em></small></div></button></section>
          <textarea v-model="imageForm.rawPrompt" rows="7" placeholder="例如：为博物馆设计一款青铜器纹样冰箱贴，金属浮雕质感，年轻人喜欢，适合作为精致伴手礼。"></textarea>
          <div class="inspiration-row"><b>灵感快捷填充</b><div class="preset-scroll"><button v-for="p in imagePromptPresets" :key="p" type="button" @click="applyImagePreset(p)">{{ p }}</button></div></div>
        </div>
        <aside class="creation-controls">
          <div class="control-block"><span>生成方式</span><div class="choice-grid three"><button type="button" :class="{active:imageForm.generationMode==='single'}" @click="imageForm.generationMode='single'">文字生图<br/><small>即梦 AI</small></button><button type="button" :class="{active:imageForm.generationMode==='image_to_image'}" @click="imageForm.generationMode='image_to_image'">图文结合<br/><small>参考图 + 文字控制</small></button><button type="button" :class="['doubao-choice',{active:imageForm.generationMode==='multiview'}]" @click="imageForm.generationMode='multiview'">多视图 3D<br/><small>Doubao · 正/左/后/右</small></button></div></div><div v-if="imageForm.generationMode==='image_to_image'" class="image-edit-upload"><div><span>STEP 01 · 上传参考图</span><b>保留主体特征，用文字指定改造方向</b><small>例如：把普通摆件改成馆藏风礼盒、改成毛绒玩具、替换配色和包装场景。</small></div><label :class="{ ready: imageForm.inputAssetId }"><input type="file" accept="image/png,image/jpeg,image/webp" @change="uploadImageEditReference" /><img v-if="imageEditPreviewUrl" :src="imageEditPreviewUrl" alt="图文结合参考图" /><template v-else><i>＋</i><b>上传产品 / 草图 / 参考图</b><small>PNG / JPG / WEBP</small></template><em v-if="imageForm.inputAssetId">已就绪</em></label></div><div v-if="imageForm.generationMode==='multiview'" class="doubao-reference-upload"><div><span>STEP 01 · 上传产品参考图</span><b>上传一张正面或 3/4 角度的清晰产品图</b><small>Doubao 将以该图为一致性基准，生成正 / 左 / 后 / 右四个建模视角。</small></div><label :class="{ ready: doubaoReferenceAssetId }"><input type="file" accept="image/png,image/jpeg,image/webp" @change="uploadDoubaoReference" /><img v-if="doubaoReferencePreviewUrl" :src="doubaoReferencePreviewUrl" alt="多视图参考图" /><template v-else><i>＋</i><b>上传参考图</b><small>PNG / JPG / WEBP</small></template><em v-if="doubaoReferenceAssetId">已就绪</em></label></div><div class="control-block"><span>视觉方向</span><div class="choice-grid three"><button type="button" :class="{active:imageForm.style==='官方文创'}" @click="imageForm.style='官方文创'">馆藏感</button><button type="button" :class="{active:imageForm.style==='国潮精致'}" @click="imageForm.style='国潮精致'">新国潮</button><button type="button" :class="{active:imageForm.style==='可爱潮玩'}" @click="imageForm.style='可爱潮玩'">潮玩感</button></div></div>
          <div class="control-block"><span>画面比例</span><div class="choice-grid"><button type="button" :class="{active:imageForm.imagenAspectRatio==='1:1'}" @click="imageForm.imagenAspectRatio='1:1'">1:1<br/><small>商品主图</small></button><button type="button" :class="{active:imageForm.imagenAspectRatio==='9:16'}" @click="imageForm.imagenAspectRatio='9:16'">9:16<br/><small>手机海报</small></button><button type="button" :class="{active:imageForm.imagenAspectRatio==='16:9'}" @click="imageForm.imagenAspectRatio='16:9'">16:9<br/><small>横版展示</small></button></div></div>
          <div class="creation-tip"><i>✦</i><span>系统将自动优化提示词，并生成适合文创展示的视觉效果。</span></div>
        </aside>
      </div>
      <button type="button" class="creation-submit image-submit" :disabled="busy" @click="generateImage"><span>{{ imageForm.generationMode==='multiview' ? '◇' : '✦' }}</span><b>{{ busy && tab==='image' ? stage || '正在生成产品图' : imageForm.generationMode==='multiview' ? '生成多视图 3D 参考图' : imageForm.generationMode==='image_to_image' ? '生成图文结合产品图' : '生成产品图' }}</b><em>{{ imageForm.generationMode==='multiview' ? 'Doubao · 4视图' : imageForm.generationMode==='image_to_image' ? '参考图 + 文本' : `${imageCost} 点` }}</em></button>
      <div v-if="busy && tab==='image'" class="generation-stage image-stage"><div class="stage-orbit"><i></i><i></i><i></i></div><div><b>{{ stage || '正在构思视觉方案' }}</b><span>AI 正在将你的灵感转化为文创产品图，请稍候。</span></div></div>
      <article v-if="doubaoMultiViewResult.length" ref="imageAnchor" class="doubao-multiview-result"><div class="doubao-result-head"><div><span>DOUBAO-SEEDREAM-5.0-LITE</span><b>多视图 3D 参考图已生成</b><small>正 / 左 / 后 / 右四个视角已保存到作品库，可直接交给 Tripo 多视图建模。</small></div><button type="button" @click="useDoubaoMultiViewFor3d">用于多视图 3D 建模 →</button></div><div class="doubao-view-grid"><article v-for="item in doubaoMultiViewResult" :key="item.view"><img :src="item.previewUrl || item.fileUrl" :alt="`${item.label}图`" /><b>{{ item.label }}图</b></article></div></article>
      <article v-if="imageResult" ref="imageAnchor" class="result-card redesigned-result"><div class="result-image-wrap"><img v-if="imagePreviewUrl" :src="imagePreviewUrl" alt="生成图片" /><span>GENERATED</span></div><div class="result-info"><span>创作已完成</span><b>产品图已保存到作品库</b><p v-if="imageForm.usageGuide">{{ imageForm.usageGuide }}</p><div class="result-actions"><a v-if="imageResult.imageUrl || imageResult.fileUrl" :href="imageResult.imageUrl || imageResult.fileUrl" target="_blank" rel="noopener">查看原图</a><button v-if="canSubmitReview(imageResult)" type="button" @click.stop="submitAssetForReview(imageResult)">{{ reviewSubmitText }}</button><span v-else-if="isSubmittingForReview(imageResult)" class="submitted-tip">提交中...</span><span v-else-if="isSubmittedForReview(imageResult) || imageResult.status === 'review'" class="submitted-tip">{{ reviewSubmittedText }}</span></div></div></article>
    </section>

    <section v-else-if="tab==='model'" key="model" class="panel creation-panel creation-redesign model-redesign">
      <header class="creation-spotlight">
        <div><span>3D FORGE · 02</span><h2>从灵感到原型，<strong>让作品拥有立体形态。</strong></h2><p>上传产品图，或输入文字描述，生成可旋转预览、可继续打样的 3D 模型。</p></div>
        <aside class="green"><i>{{ modelCost }}</i><span>积分 / 次</span><small>支持图生 3D / 文生 3D</small></aside>
      </header>
            <section class="product-brief" aria-label="产品与材质引导"><div class="product-brief-title"><span>STEP 01 · PRODUCT BLUEPRINT</span><b>先选要做什么，AI 才能按真实产品思路生成</b><small>类别、材质会联动案例、提示词、3D模板和生产初筛。</small></div><div class="brief-selectors"><div><span>产品类别</span><button v-for="item in productCategories" :key="item.key" :class="{active:selectedProductKey===item.key}" @click="selectProductCategory(item.key)">{{ item.label }}</button></div><div><span>具体材质</span><button v-for="item in productProfile.materials" :key="item" :class="{active:selectedMaterial===item}" @click="selectProductMaterial(item)">{{ item }}</button></div></div><aside><b>{{ productProfile.label }} · {{ selectedMaterial }}</b><p>{{ productProfile.image }}</p><small>AI 会自动加入可生产结构建议；原创设计、授权核验与最终打样仍由你确认。</small></aside></section>
      <div class="model-mode-switch three-modes"><button type="button" :class="{active:modelForm.mode==='image_to_model'}" @click="modelForm.mode='image_to_model'"><b>图片生成 3D</b><span>上传产品图，快速建立立体原型</span></button><button type="button" :class="{active:modelForm.mode==='multiview_to_model'}" @click="modelForm.mode='multiview_to_model'"><b>多视图生成 3D</b><span>上传多个视角，模型更完整</span></button><button type="button" :class="{active:modelForm.mode==='text_to_model'}" @click="modelForm.mode='text_to_model'"><b>文字生成 3D</b><span>用描述直接构建产品模型</span></button></div>
      <section class="material-picker"><div><span>表面材质偏好</span><b>选择模型的视觉材质与 PBR 表面质感</b><small>文本生成会写入 Tripo 提示词；图片 / 多视图生成会作为任务材质记录，最终实物仍需打样确认。</small></div><div class="material-chips"><button v-for="item in materialOptions" :key="item.label" type="button" :class="{ active: modelForm.materialLabel===item.label }" @click="chooseModelMaterial(item.label)">{{ item.label }}</button></div></section>
      <div class="model-workspace">
        <div v-if="modelForm.mode==='image_to_model'" class="model-upload-pane"><label class="upload-box redesign-upload"><input type="file" accept="image/*" @change="uploadReference" /><img v-if="uploadPreviewUrl" :src="uploadPreviewUrl" alt="3D参考图" /><span v-else><i>＋</i><b>上传一张产品参考图</b><small>PNG / JPG / WEBP，主体越清晰，3D 效果越好</small></span></label><div class="model-note"><b>图生 3D 的优势</b><span>保留产品的主体轮廓与视觉特征，适合已有图片的文创快速建模。</span></div></div>
        <div v-else-if="modelForm.mode==='multiview_to_model'" class="multiview-pane"><div class="multiview-head"><div><span>MULTI-VIEW CAPTURE</span><b>上传同一产品的多个视角</b></div><small>至少上传正面图 + 任意一个侧面图</small></div><div class="multiview-grid"><label v-for="view in ['front','left','back','right']" :key="view" class="multiview-slot" :class="{ ready: modelForm.multiviewAssetIds[view as 'front' | 'left' | 'back' | 'right'] }"><input type="file" accept="image/*" @change="uploadReference($event, view as 'front' | 'left' | 'back' | 'right')" /><img v-if="multiviewPreviewUrls[view as 'front' | 'left' | 'back' | 'right']" :src="multiviewPreviewUrls[view as 'front' | 'left' | 'back' | 'right']" :alt="`${view}视图`" /><template v-else><i>{{ ({ front: '正', left: '左', back: '后', right: '右' } as any)[view] }}</i><b>{{ ({ front: '正面图', left: '左侧图', back: '背面图', right: '右侧图' } as any)[view] }}</b><small>{{ view==='front' ? '必传' : '可选，建议上传' }}</small></template><em v-if="modelForm.multiviewAssetIds[view as 'front' | 'left' | 'back' | 'right']">已上传</em></label></div><div class="model-note multiview-note"><b>多视图建模优势</b><span>多个角度能让 AI 更准确识别厚度、侧面结构和背部细节，生成结果通常比单图更完整。</span></div></div>
        <div v-else class="model-prompt-pane"><div class="prompt-studio-head"><span>模型描述</span><small>{{ modelForm.rawPrompt.length }} / 1024</small></div><section class="model-template-showcase"><div class="model-showcase-title"><div><span>3D EXAMPLE</span><b>先看 3D 成品，再开始建模</b></div><small>点击带入同类型建模方案</small></div><button v-for="item in modelShowcaseTemplates" :key="item.id" type="button" class="model-showcase-card" @click="applyModelShowcase(item)"><img :src="item.image" :alt="item.title" /><div><span>{{ item.subtitle }}</span><b>{{ item.title }}</b><small>使用这个建模方案 <em>→</em></small></div></button></section><div class="model-template-picker"><div><span>建模模板</span><small>模板会优化 Tripo 的结构、材质和反向提示词</small></div><div><button v-for="item in modelTemplateOptions" :key="item.key" type="button" :class="{ active:modelForm.promptTemplate===item.key }" @click="selectModelTemplate(item.key)"><b>{{ item.label }}</b><small>{{ item.desc }}</small></button></div></div><textarea v-model="modelForm.rawPrompt" rows="7" placeholder="例如：一只可爱的文博守护兽毛绒玩具，圆润软体，短密绒毛，刺绣五官，适合打样。"></textarea><div class="inspiration-row"><b>推荐描述</b><div class="preset-scroll"><button v-for="p in modelPromptPresets" :key="p.label" type="button" @click="applyModelPreset(p)">{{ p.label }}</button></div></div><div v-if="modelForm.promptTemplate==='plush_toy'" class="plush-prompt-tip"><b>毛绒建模已启用</b><span>会强调圆润填充体、短密绒毛、刺绣细节、缝线与可生产的封闭网格；生成后可继续在作品库用“全毛绒”材质增强预览。</span></div><div v-else-if="modelForm.promptTemplate==='ppc_precision'" class="ppc-prompt-tip"><b>PPC 精密硬塑已启用</b><span>会强调高密度注塑质感、清楚的分件与分型线、紧密拼缝、锐利小细节、微细表面颗粒与 UV/PBR 纹理；适合硬塑摆件和量产打样方案。</span></div></div>
        <aside class="model-guidance"><span>MODEL QUALITY</span><b>高质量建模建议</b><ul><li>描述清楚主体、材质和结构</li><li>优先选择轮廓完整、背景干净的图片</li><li>先打样确认，再进入批量生产</li></ul><div><i>3D</i><p>生成完成后可旋转预览，并提交审核。</p></div></aside>
      </div>
      <section v-if="productionAssessment" class="feasibility-card"><div><span>AI 生产可行性初筛</span><b>{{ productionAssessment.level }}</b><em>{{ productionAssessment.score }} 分</em></div><p>{{ productionAssessment.issues?.[0] }}</p><small v-for="tip in productionAssessment.suggestions" :key="tip">✦ {{ tip }}</small><button type="button" @click="refreshProductionAssessment">重新评估</button><i>{{ productionAssessment.disclaimer }}</i></section>
      <button type="button" class="creation-submit model-submit" :disabled="busy || !canGenerateModel" @click="generateModel"><span>◇</span><b>{{ busy && tab==='model' ? stage || '正在构建立体原型' : '生成 3D 模型' }}</b><em>{{ modelCost }} 点</em></button>
      <div v-if="busy && tab==='model'" class="generation-stage model-stage"><div class="stage-orbit"><i></i><i></i><i></i></div><div><b>{{ stage || '正在构建立体原型' }}</b><span>3D 生成时间稍长，完成后会自动保存并可旋转预览。</span></div><div class="model-progress-line"><span :style="{ width: `${Math.max(12, modelProgress)}%` }"></span></div></div>
      <article v-if="modelResult" ref="modelAnchor" class="result-card redesigned-result"><div class="result-image-wrap model-result"><img v-if="modelResult.previewUrl" :src="modelResult.previewUrl" alt="3D模型预览" /><span>3D READY</span></div><div class="result-info"><span>立体原型已完成</span><b>3D 模型已保存到作品库</b><p>现在可以旋转预览、提交审核，并在通过后申请打样或批量生产。</p><div class="result-actions"><button type="button" @click="openModelPreview(modelResult)">材质编辑</button><button v-if="canSubmitReview(modelResult)" type="button" @click.stop="submitAssetForReview(modelResult)">{{ reviewSubmitText }}</button><span v-else-if="isSubmittingForReview(modelResult)" class="submitted-tip">提交中...</span><span v-else-if="isSubmittedForReview(modelResult) || modelResult.status === 'review'" class="submitted-tip">{{ reviewSubmittedText }}</span></div></div></article>
    </section>

    <section v-else key="gallery" class="panel creation-panel">
      <div class="section-head">
        <span>WORKS</span>
        <b>最近作品</b>
      </div>
      <div class="gallery-summary">
        <article><b>{{ recentImages.length }}</b><span>产品图</span></article>
        <article><b>{{ recentModels.length }}</b><span>3D模型</span></article>
        <article><b>{{ reviewCount }}</b><span>审批中</span></article>
      </div>
      <div class="gallery">
        <article v-for="a in recentImages" :key="`img-${a.id}`">
          <img :src="a.previewUrl || a.fileUrl" alt="作品图片" />
          <span class="work-status" :class="workStatusClass(a)">{{ workStatusLabel(a) }}</span>
          <b>{{ displayAssetTitle(a) }}</b>
          <button v-if="canSubmitReview(a)" type="button" class="review-submit" @click.stop="submitAssetForReview(a)">{{ reviewSubmitText }}</button>
          <span v-else-if="isSubmittingForReview(a)" class="submitted-tip">提交中...</span>
        </article>
        <article v-for="a in recentModels" :key="`model-${a.id}`">
          <img v-if="modelPreviewImage(a)" :src="modelPreviewImage(a)" alt="3D作品预览" />
          <div v-else class="model-tile">3D</div>
          <span class="work-status" :class="workStatusClass(a)">{{ workStatusLabel(a) }}</span>
          <b>{{ displayAssetTitle(a) }}</b>
          <button type="button" @click.stop="openModelPreview(a)">材质编辑</button>
          <button type="button" class="rights-button" @click.stop="rightsServiceOpen=true; rightsService=''">版权确权</button>
          <button v-if="canSubmitReview(a)" type="button" class="review-submit" @click.stop="submitAssetForReview(a)">{{ reviewSubmitText }}</button>
          <span v-else-if="isSubmittingForReview(a)" class="submitted-tip">提交中...</span>
          <div v-if="canSubmitProduction(a)" class="production-actions">
            <button type="button" @click.stop="openProductionRequest(a, 'sample')">申请打样</button>
            <button v-if="creationPurpose === 'museum_sale'" type="button" class="prod" @click.stop="openProductionRequest(a, 'bulk')">批量生产</button>
          </div>
        </article>
      </div>
      <p v-if="!recentImages.length && !recentModels.length" class="empty">暂无作品</p>

      <div v-if="recentProductionRequests.length" class="production-list">
        <h3>我的生产申请</h3>
        <article v-for="r in recentProductionRequests" :key="r.id">
          <div><b>{{ requestTypeText(r.requestType) }} · {{ r.quantity }}个</b><span>{{ r.assetTitle || r.title }}</span></div>
          <em :class="productionStatusClass(r.status)">{{ productionStatusText(r.status) }}</em>
        </article>
      </div>
    </section>
    </Transition>
    </div>

    <Teleport to="body">
      <section v-if="creditPanelOpen" class="credit-modal" @click.self="closeCreditPanel">
        <div class="credit-card">
          <header>
            <div><b>我的额度</b><span>{{ props.currentUser.username }} · 当前余额 {{ creditBalance }} 点</span></div>
            <button type="button" @click="closeCreditPanel">×</button>
          </header>
          <main>
            <div class="balance-card">
              <span>可用额度</span>
              <b>{{ creditBalance }}</b>
              <em>点</em>
            </div>
            <div class="rules-card">
              <b>消耗规则</b>
              <p>2D生图：{{ imageCost }}点 / 次</p>
              <p>3D生成：{{ modelCost }}点 / 次</p>
              <p>OBJ/STL转换下载：{{ convertCost }}点 / 次</p>
            </div>
            <div v-if="paymentOrder" class="payment-order-card">
              <div class="payment-order-head"><span>{{ paymentOrder.channel === 'wechat' ? '微信支付' : '微信收款码' }}</span><b>{{ paymentOrder.channel === 'wechat' ? '支付成功后系统会自动到账，无需人工审批' : '扫码付款后点击“提交核验”，管理员确认后额度到账' }}</b></div>
              <img v-if="paymentQrUrl" :src="paymentQrUrl" alt="微信收款二维码" class="payment-qr" />
              <strong>¥ {{ paymentOrder.amountYuan }} · {{ paymentOrder.credits }} 点</strong>
              <small>订单 {{ paymentOrder.orderNo }} · {{ paymentOrder.status === 'paid' ? '支付成功，积分已到账' : paymentOrder.status === 'failed' ? '支付失败' : paymentOrder.channel === 'wechat' ? '等待微信支付结果回调，系统自动确认' : '扫码付款后请点击提交核验' }}</small>
              <button v-if="paymentOrder.status === 'pending' && paymentOrder.channel === 'manual_wechat_qr'" type="button" class="manual-complete" :disabled="paymentLoading" @click="completeManualPayment">{{ paymentLoading ? '提交中…' : '我已完成支付，提交核验' }}</button>
              <button v-else-if="paymentOrder.status === 'pending'" type="button" class="copy-payment" disabled>等待微信支付结果回调</button>
            </div>
            <div v-else class="packages">
              <button v-for="pkg in rechargePackages" :key="pkg.code" type="button" :disabled="paymentLoading || !paymentChannelEnabled" @click="createPaymentOrder(pkg)">
                <strong>{{ pkg.credits }} 点</strong>
                <span>{{ pkg.name }} · ¥{{ pkg.amountYuan }}</span>
                <em>{{ pkg.description }}</em>
              </button>
              <p v-if="!rechargePackages.length" class="recharge-note">充值套餐加载中，请稍后重试。</p><p v-else-if="!paymentChannelEnabled" class="recharge-note">当前暂无可用支付方式，请管理员配置真实微信支付或有效收款码后重试。</p>
            </div>
            <p v-if="paymentError" class="payment-error">{{ paymentError }}</p>
            <p class="recharge-note">{{ paymentOrder?.channel === 'wechat' ? '请使用微信完成付款。微信支付成功后由官方回调自动验签，系统会自动为账户增加积分，不需要人工审批。' : '请扫描收款码完成付款。付款后点击“提交核验”，管理员确认后额度到账。' }}</p>
          </main>
          <footer>
            <button v-if="paymentOrder" type="button" @click="closePaymentOrder">返回套餐</button>
            <button v-else type="button" @click="loadPaymentPackages">刷新套餐</button>
            <button type="button" class="done" @click="closeCreditPanel">完成</button>
          </footer>
        </div>
      </section>

      <section v-if="productionModal" class="production-modal" @click.self="closeProductionRequest">
        <div class="production-card">
          <header>
            <div><b>{{ productionForm.requestType === 'sample' ? '提交打样申请' : '提交批量生产申请' }}</b><span>作品：{{ productionModal.title || '3D模型作品' }}</span></div>
            <button type="button" @click="closeProductionRequest">×</button>
          </header>
          <main>
            <p class="purpose-in-form">创作目的：{{ selectedPurposeFullText }}</p>
            <label><span>总数量</span><input v-model.number="productionForm.quantity" type="number" min="1" /></label>
            <div v-if="creationPurpose === 'personal'" class="single-route">
              <b>个人收藏 / 送礼路径</b>
              <span>全部数量将按个人/送礼用途执行，不进入售卖渠道，不支持拆分到博物馆。</span>
            </div>
            <template v-else>
              <div class="single-route museum">
                <b>售卖（景区、博物馆）路径</b>
                <span>全部数量将进入所选博物馆售卖，不支持拆分给个人或多个博物馆。</span>
              </div>
              <div class="dist-head"><b>选择投放博物馆</b><small>全部 {{ productionForm.quantity || 0 }} 个</small></div>
              <div class="museum-location-select">
                <select v-model="museumRegion.province" @change="changeMuseumProvince"><option value="">省 / 直辖市</option><option v-for="province in museumProvinces" :key="province" :value="province">{{ province }}</option></select>
                <select v-model="museumRegion.city" :disabled="!museumRegion.province" @change="changeMuseumCity"><option value="">市</option><option v-for="city in museumCities" :key="city" :value="city">{{ city }}</option></select>
              </div>
              <div v-if="productionForm.museumDistribution[0]" class="dist-row single museum-final-select">
                <select v-model="productionForm.museumDistribution[0].museumId" @change="changeMuseum(productionForm.museumDistribution[0])"><option v-for="m in filteredMuseums" :key="m.id" :value="m.id">{{ m.name }} · {{ m.scene }}</option></select>
              </div>
              <p v-if="productionForm.museumDistribution[0]" class="museum-selection-tip">将投放至：{{ productionForm.museumDistribution[0].museumName }}（{{ museumRegion.province }} {{ museumRegion.city }}）</p>
              <p v-else class="alloc-tip bad">当前区域暂无可选博物馆，请调整省、市。</p>
            </template>
            <template v-if="creationPurpose === 'personal'">
              <label><span>收件人</span><input v-model.trim="productionForm.recipientName" placeholder="收件人姓名" /></label>
              <label><span>手机号</span><input v-model.trim="productionForm.recipientPhone" placeholder="用于寄送联系" /></label>
              <label><span>收货地址</span><textarea v-model.trim="productionForm.recipientAddress" rows="2" placeholder="收货地址"></textarea></label>
            </template>
            <label><span>申请说明</span><textarea v-model.trim="productionForm.note" rows="3"></textarea></label>
          </main>
          <footer>
            <button type="button" @click="closeProductionRequest">取消</button>
            <button type="button" class="submit" :disabled="submittingProduction" @click="submitProductionRequest">{{ submittingProduction ? '提交中' : '提交审批' }}</button>
          </footer>
        </div>
      </section>

      <section v-if="rightsServiceOpen" class="rights-modal" @click.self="rightsServiceOpen=false"><div><button class="rights-close" @click="rightsServiceOpen=false">×</button><span>RIGHTS & COMPLIANCE</span><h3>为作品提前做好确权准备</h3><p>作品著作权归设计师所有；平台商业运营权以双方后续协议授权范围为准。此处为咨询登记，不等同于已完成登记、专利申请或法律意见。</p><label v-for="item in ['暂不申请，仅保存创作证据','著作权登记咨询','外观设计专利咨询','商标 / IP 运营咨询']" :key="item"><input v-model="rightsService" type="radio" :value="item" /> {{ item }}</label><button class="rights-submit" @click="submitRightsService()">提交咨询登记</button><small>涉及明星、人物肖像、品牌商标或第三方 IP 的内容，请先取得有效授权；上传真人照片需获得当事人授权。</small></div></section>
      <section v-if="previewAsset" class="model-preview-modal" @click.self="closeModelPreview">
        <div class="model-preview-top">
          <button type="button" class="preview-back" @click="closeModelPreview">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 18 9 12l6-6"/></svg>
            返回
          </button>
          <div>
            <b>3D 材质工作台</b>
            <span>实时换材质 · 保存版本 · 导出 GLB</span>
          </div>
        </div>

        <div class="model-viewer-wrap material-viewer-wrap">
          <MaterialModelStudio :model-url="previewModelUrl" :model-name="previewAsset.title || 'and-taste-model'" @loaded="previewReady = true" @save-variant="saveMaterialVariant" @error="(message) => { previewLoadFailed = true; emit('alert', message, 'error') }" />
        </div>

        <div class="model-preview-bottom">
          <button type="button" @click="closeModelPreview">完成</button>
          <label class="format-select">
            <span>下载格式</span>
            <select v-model="previewDownloadFormat">
              <option value="GLB">GLB</option>
              <option value="OBJ">OBJ</option>
              <option value="STL">STL</option>
            </select>
          </label>
          <button type="button" class="download-action" :disabled="previewDownloading" @click="downloadPreviewModel">{{ previewDownloading ? '处理中' : `下载${previewDownloadFormat}${previewDownloadFormat==='GLB'?'':` · ${convertCost}点`}` }}</button>
        </div>
      </section>
    </Teleport>
      <CustomerSupportWidget :current-user="props.currentUser" @alert="(msg, type) => emit('alert', msg, type)" />
  </main>
</template>

<style scoped>
.purpose-gate{position:fixed;inset:0;z-index:300;display:flex;align-items:center;justify-content:center;padding:20px;background:radial-gradient(circle at 80% 10%,rgba(255,255,255,.24),transparent 180px),linear-gradient(160deg,#2a1c16,#7c3f2b 58%,#e0a35d);color:#fff}.purpose-card{width:min(420px,100%);padding:24px;border-radius:28px;background:rgba(255,255,255,.14);border:1px solid rgba(255,255,255,.24);box-shadow:0 30px 80px rgba(37,22,14,.35);backdrop-filter:blur(18px)}.purpose-brand{display:flex;align-items:center;gap:10px;margin-bottom:18px}.purpose-brand img{width:38px;height:38px;border-radius:10px;background:#fff}.purpose-brand span{font-size:12px;font-weight:900;letter-spacing:1.4px}.purpose-card h1{margin:0 0 10px;font-size:30px;letter-spacing:-.04em}.purpose-card p{margin:0 0 16px;color:rgba(255,255,255,.78);line-height:1.7}.purpose-options{display:flex;flex-direction:column;gap:10px}.purpose-options button{position:relative;text-align:left;padding:16px;border:1px solid rgba(255,255,255,.24);border-radius:18px;background:rgba(255,255,255,.92);color:#201a17;box-shadow:0 12px 30px rgba(32,26,23,.12)}.purpose-options i{display:inline-flex;margin-bottom:8px;padding:4px 8px;border-radius:999px;background:#fff7ed;color:#b4532a;font-style:normal;font-size:11px;font-weight:950}.purpose-options b,.purpose-options span{display:block}.purpose-options b{font-size:18px}.purpose-options span{margin-top:5px;color:#6e5547;font-size:13px;line-height:1.5}.purpose-change{position:relative;z-index:1;align-self:flex-start;margin-top:8px;height:30px;border:1px solid rgba(255,255,255,.3);border-radius:999px;background:rgba(255,255,255,.12);color:#fff;font-size:11px;font-weight:900}.purpose-in-form{margin:0 0 10px;padding:9px 10px;border-radius:12px;background:#fff7ed;color:#9a3412;font-size:12px;font-weight:900}.credit-modal{position:fixed;inset:0;z-index:260;background:rgba(32,26,23,.58);backdrop-filter:blur(8px);display:flex;align-items:flex-end;justify-content:center}.credit-card{width:min(460px,100vw);max-height:88vh;display:flex;flex-direction:column;border-radius:24px 24px 0 0;background:#fff;overflow:hidden;color:#201a17}.credit-card header,.credit-card footer{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:14px;border-bottom:1px solid #eadfd4}.credit-card footer{border-top:1px solid #eadfd4;border-bottom:0}.credit-card header b,.credit-card header span{display:block}.credit-card header span{margin-top:3px;color:#8a7161;font-size:12px}.credit-card header button{width:34px;height:34px;border:0;border-radius:10px;background:#f6f2ea;font-size:22px}.credit-card main{padding:14px;overflow:auto}.balance-card{position:relative;padding:18px;border-radius:20px;background:linear-gradient(135deg,#201a17,#7c3f2b);color:#fff}.balance-card span,.balance-card em{font-style:normal;color:rgba(255,255,255,.72);font-size:12px;font-weight:900}.balance-card b{display:inline-block;margin:8px 6px 0 0;font-size:42px}.rules-card{margin-top:10px;padding:14px;border-radius:18px;background:#fffaf4;border:1px solid #eadfd4}.rules-card b{display:block;margin-bottom:8px}.rules-card p{margin:5px 0;color:#6e5547;font-size:13px}.packages{display:grid;grid-template-columns:1fr;gap:9px;margin-top:10px}.packages button{text-align:left;padding:13px;border:1px solid #eadfd4;border-radius:16px;background:#fff;color:#201a17}.packages strong,.packages span,.packages em{display:block}.packages strong{font-size:20px}.packages span{margin-top:3px;font-weight:900}.packages em{margin-top:4px;color:#8a7161;font-size:12px;font-style:normal}.recharge-note{margin:12px 0 0;color:#8a7161;font-size:12px;line-height:1.6}.credit-card footer button{height:38px;border:0;border-radius:10px;background:#201a17;color:#fff;padding:0 12px;font-weight:900}.credit-card footer .done{background:#b4532a}.hero-actions .recharge-hero{background:rgba(255,255,255,.92);color:#7c2d12;border-color:rgba(255,255,255,.92)}.consumer-shell{min-height:100vh;background:#f6f2ea;color:#201a17;padding:14px 14px 96px;font-family:Inter,"PingFang SC",system-ui,sans-serif}.consumer-top{position:sticky;top:0;z-index:10;display:flex;align-items:center;justify-content:space-between;margin:-14px -14px 10px;padding:12px 14px;background:rgba(246,242,234,.86);backdrop-filter:blur(18px);border-bottom:1px solid rgba(120,92,64,.12)}.brand{display:flex;align-items:center;gap:9px}.brand img{width:34px;height:34px;border-radius:8px;object-fit:cover}.brand b,.brand span{display:block}.brand b{font-size:15px}.brand span{font-size:11px;color:#8a7161}.icon-btn{width:38px;height:38px;border:0;border-radius:8px;background:#fff;color:#4b3327;box-shadow:0 6px 18px rgba(69,45,26,.08)}.icon-btn svg,.primary svg,.quick-tabs svg,.upload-box svg{width:18px;height:18px}.hero{position:relative;min-height:172px;padding:24px 18px;border-radius:8px;background:radial-gradient(circle at 84% 16%,rgba(255,255,255,.2),transparent 24%),linear-gradient(135deg,#2a1c16,#8e402b 62%,#c27643);color:#fff;display:flex;flex-direction:column;justify-content:flex-end;box-shadow:0 18px 42px rgba(90,54,31,.22);overflow:hidden}.hero:after{content:"";position:absolute;right:18px;top:16px;width:92px;height:92px;border-radius:50%;background:rgba(255,255,255,.12);box-shadow:-26px 46px 0 rgba(255,255,255,.08)}.hero>*{position:relative;z-index:1}.hero span{width:max-content;padding:5px 9px;border-radius:999px;background:rgba(255,255,255,.16);font-size:11px}.hero h1{margin:12px 0 15px;font-size:28px;line-height:1.08;letter-spacing:0}.hero-actions{display:flex;gap:9px}.hero-actions button{height:38px;padding:0 14px;border:1px solid rgba(255,255,255,.34);border-radius:8px;background:rgba(255,255,255,.14);color:#fff;font-weight:800}.quick-tabs{position:fixed;left:14px;right:14px;bottom:14px;z-index:20;display:grid;grid-template-columns:repeat(3,1fr);gap:6px;padding:7px;border:1px solid rgba(120,92,64,.14);border-radius:8px;background:rgba(255,255,255,.9);backdrop-filter:blur(18px);box-shadow:0 18px 50px rgba(57,38,26,.16)}.quick-tabs button{height:48px;border:0;border-radius:8px;background:transparent;color:#8a7161;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;font-size:11px;font-weight:800}.quick-tabs button.active{background:#201a17;color:#fff}.panel{margin-top:12px;padding:15px;border-radius:8px;background:#fff;box-shadow:0 12px 32px rgba(77,51,31,.08);border:1px solid rgba(120,92,64,.1)}.section-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:13px}.section-head span{font-size:10px;font-weight:900;letter-spacing:1.6px;color:#b4532a}.section-head b{font-size:18px}label{display:block;margin-top:12px}label>span{display:block;margin-bottom:7px;font-size:13px;font-weight:800;color:#4a3429}textarea{width:100%;box-sizing:border-box;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4;padding:12px;color:#241a16;font-size:15px;line-height:1.55;resize:vertical;outline:none}textarea:focus{border-color:#b4532a;box-shadow:0 0 0 3px rgba(180,83,42,.12)}.chips{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:10px}.chips.compact{grid-template-columns:repeat(3,1fr)}.chips button,.mode-switch button{min-height:38px;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4;color:#6e5547;font-weight:800}.chips button.active,.mode-switch button.active{border-color:#201a17;background:#201a17;color:#fff}.primary{width:100%;height:52px;margin-top:14px;border:0;border-radius:8px;background:#b4532a;color:#fff;font-size:16px;font-weight:900;display:flex;align-items:center;justify-content:center;gap:8px;box-shadow:0 12px 26px rgba(180,83,42,.24)}.primary.green{background:#0f766e;box-shadow:0 12px 26px rgba(15,118,110,.2)}.primary:disabled{opacity:.55}.result-card{overflow:hidden;margin-top:14px;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4}.result-card>img{display:block;width:100%;max-height:480px;object-fit:contain;background:#211814}.result-info{padding:12px}.result-info b{display:block;margin-bottom:5px}.result-info p{margin:0 0 10px;white-space:pre-wrap;color:#6e5547;font-size:13px;line-height:1.6}.result-actions{display:flex;flex-wrap:wrap;align-items:center;gap:8px}.result-info a,.result-info button{display:inline-flex;height:34px;align-items:center;padding:0 12px;border:0;border-radius:8px;background:#201a17;color:#fff;text-decoration:none;font-weight:800}.submitted-tip{display:inline-flex;height:30px;align-items:center;padding:0 10px;border-radius:999px;background:#fff7ed;color:#b45309;font-size:12px;font-weight:900}.mode-switch{display:grid;grid-template-columns:1fr 1fr;gap:8px}.upload-box{position:relative;min-height:170px;border:1px dashed #c7a995;border-radius:8px;background:#fffaf4;display:flex;align-items:center;justify-content:center;overflow:hidden}.upload-box input{position:absolute;inset:0;opacity:0}.upload-box img{width:100%;height:220px;object-fit:cover}.upload-box span{display:flex;align-items:center;gap:8px;color:#8a7161;font-weight:900}.progress{height:8px;margin-top:12px;border-radius:999px;background:#e9ded2;overflow:hidden}.progress span{display:block;height:100%;border-radius:999px;background:#0f766e;transition:width .25s ease}.gallery{display:grid;grid-template-columns:1fr 1fr;gap:10px}.gallery article{position:relative;overflow:hidden;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4}.gallery img,.model-tile{width:100%;aspect-ratio:1/1;object-fit:cover;background:#201a17;color:#fff}.model-tile{display:flex;align-items:center;justify-content:center;font-size:28px;font-weight:950}.work-status{position:absolute;top:8px;right:8px;padding:4px 7px;border-radius:999px;background:rgba(255,255,255,.92);font-size:10px;font-weight:900}.work-status.draft{color:#64748b}.work-status.review{color:#b45309}.work-status.approved{color:#047857}.work-status.rejected{color:#dc2626}.gallery b{display:block;padding:9px;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.gallery button{margin:0 9px 9px;height:30px;border:0;border-radius:8px;background:#201a17;color:#fff;font-weight:800}.gallery .review-submit{background:#b4532a}.production-actions{display:flex;gap:6px;padding:0 9px 9px}.gallery .production-actions button{flex:1;margin:0;background:#0f766e}.gallery .production-actions .prod{background:#7c2d12}.production-list{margin-top:14px;display:flex;flex-direction:column;gap:8px}.production-list h3{margin:4px 0;font-size:15px}.production-list article{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:10px;border-radius:10px;background:#fffaf4;border:1px solid #eadfd4}.production-list b,.production-list span{display:block}.production-list span{margin-top:3px;color:#8a7161;font-size:12px}.production-list em{font-style:normal;padding:4px 8px;border-radius:999px;font-size:11px;font-weight:900}.production-list em.review{background:#fff7ed;color:#b45309}.production-list em.approved{background:#ecfdf5;color:#047857}.production-list em.rejected{background:#fef2f2;color:#dc2626}.production-modal{position:fixed;inset:0;z-index:220;background:rgba(32,26,23,.58);backdrop-filter:blur(8px);display:flex;align-items:flex-end;justify-content:center}.production-card{width:min(460px,100vw);max-height:88vh;display:flex;flex-direction:column;border-radius:24px 24px 0 0;background:#fff;overflow:hidden}.production-card header,.production-card footer{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:14px;border-bottom:1px solid #eadfd4}.production-card footer{border-top:1px solid #eadfd4;border-bottom:0}.production-card header b,.production-card header span{display:block}.production-card header span{margin-top:3px;color:#8a7161;font-size:12px}.production-card header button{width:34px;height:34px;border:0;border-radius:10px;background:#f6f2ea;font-size:22px}.production-card main{padding:14px;overflow:auto}.production-card input,.production-card select{width:100%;height:40px;box-sizing:border-box;border:1px solid #eadfd4;border-radius:10px;background:#fffaf4;padding:0 10px}.dist-head{display:flex;align-items:center;justify-content:space-between;margin-top:12px}.dist-head button,.production-card footer button{height:38px;border:0;border-radius:10px;background:#201a17;color:#fff;padding:0 12px;font-weight:900}.dist-row{display:grid;grid-template-columns:1fr 74px 52px;gap:7px;margin-top:8px}.dist-row button{border:0;border-radius:10px;background:#fef2f2;color:#dc2626;font-weight:900}.alloc-tip{margin:8px 0 0;color:#047857;font-size:12px;font-weight:900}.alloc-tip.bad{color:#dc2626}.production-card footer .submit{background:#b4532a}.empty{padding:40px 0;text-align:center;color:#8a7161}@media(min-width:720px){.consumer-shell{display:block;max-width:460px;margin:0 auto;box-shadow:0 0 0 1px rgba(120,92,64,.08),0 24px 80px rgba(40,28,22,.15)}.quick-tabs{left:50%;right:auto;width:432px;transform:translateX(-50%)}}
</style>

<style scoped>
.immersive-shell{
  background:linear-gradient(180deg,#f7f0e8 0%,#eee4da 100%);
  padding:0 14px 104px;
}
.ambient-layer{
  opacity:.45;
  mix-blend-mode:normal;
}
.immersive-shell .consumer-top{
  background:rgba(247,240,232,.86);
  color:#201a17;
  backdrop-filter:blur(18px);
  border-bottom:1px solid rgba(87,65,44,.08);
}
.immersive-shell .brand span{
  color:#8a7161;
}
.immersive-shell .icon-btn{
  background:#fffaf5;
  color:#201a17;
  box-shadow:0 8px 24px rgba(58,39,25,.08);
}
.immersive-shell .hero{
  min-height:248px;
  margin:10px 0 14px;
  padding:24px 18px;
  border-radius:28px;
  background:
    radial-gradient(circle at 82% 18%,rgba(255,255,255,.18),transparent 110px),
    linear-gradient(135deg,#221713,#6b3a29 58%,#ad6840);
  box-shadow:0 22px 54px rgba(77,48,29,.2);
}
.immersive-shell .hero:after{
  width:150px;
  height:150px;
  right:-24px;
  top:18px;
  opacity:.72;
}
.immersive-shell .hero h1{
  max-width:11ch;
  font-size:32px;
}
.immersive-shell .hero p{
  max-width:24ch;
  margin-bottom:18px;
  font-size:13px;
}
.immersive-shell .hero-actions button{
  min-width:104px;
  height:42px;
}
.immersive-shell .creation-panel{
  margin-top:12px;
  border-radius:24px;
  box-shadow:0 18px 44px rgba(58,39,25,.1);
}
.immersive-shell .section-head{
  margin-bottom:14px;
}
.immersive-shell .section-head span{
  font-size:10px;
  letter-spacing:1.8px;
}
.immersive-shell textarea{
  min-height:138px;
}
.immersive-shell .primary{
  min-height:56px;
}
</style>

<style scoped>
.immersive-shell{
  position:relative;
  min-height:100dvh;
  overflow-x:hidden;
  background:
    linear-gradient(180deg,#17100d 0,#2a1b15 34%,#f5eee5 34.5%,#eee3d8 100%);
  padding:0 14px 104px;
}
.ambient-layer{
  position:fixed;
  inset:0;
  pointer-events:none;
  background:
    radial-gradient(circle at 76px 108px,rgba(255,255,255,.16),transparent 80px),
    radial-gradient(circle at 86% 15%,rgba(194,118,67,.38),transparent 160px),
    radial-gradient(circle at 18% 42%,rgba(15,118,110,.18),transparent 150px);
  mix-blend-mode:screen;
  opacity:.92;
  z-index:0;
}
.immersive-shell>*:not(.ambient-layer){
  position:relative;
  z-index:1;
}
.immersive-shell .consumer-top{
  margin:0 -14px;
  padding:14px 16px 10px;
  border:0;
  background:linear-gradient(180deg,rgba(23,16,13,.94),rgba(23,16,13,.62));
  color:#fff;
}
.immersive-shell .brand span{
  color:rgba(255,255,255,.64);
}
.immersive-shell .icon-btn{
  background:rgba(255,255,255,.12);
  color:#fff;
  box-shadow:none;
  backdrop-filter:blur(12px);
}
.immersive-shell .hero{
  min-height:430px;
  margin:0 -14px;
  padding:92px 22px 28px;
  border-radius:0 0 34px 34px;
  background:
    linear-gradient(180deg,rgba(23,16,13,.15),rgba(23,16,13,.76)),
    radial-gradient(circle at 72% 18%,rgba(255,221,186,.24),transparent 130px),
    radial-gradient(circle at 16% 70%,rgba(20,184,166,.16),transparent 120px),
    linear-gradient(135deg,#211510 0%,#5a3024 48%,#b86b3b 100%);
  box-shadow:0 28px 64px rgba(48,29,19,.38);
  justify-content:flex-end;
}
.immersive-shell .hero:after{
  right:-34px;
  top:50px;
  width:220px;
  height:220px;
  background:
    linear-gradient(145deg,rgba(255,255,255,.18),rgba(255,255,255,.02)),
    radial-gradient(circle at 35% 30%,rgba(255,244,220,.16),transparent 46%);
  border:1px solid rgba(255,255,255,.12);
  box-shadow:-110px 138px 0 rgba(255,255,255,.055);
}
.hero-glass{
  position:absolute;
  top:18px;
  left:22px;
  right:22px;
  display:flex;
  align-items:center;
  gap:10px;
  padding:10px 12px;
  border:1px solid rgba(255,255,255,.14);
  border-radius:18px;
  background:rgba(255,255,255,.09);
  backdrop-filter:blur(18px);
}
.hero-glass img{
  width:34px;
  height:34px;
  border-radius:10px;
  object-fit:cover;
}
.hero-glass strong{
  color:#fff;
  letter-spacing:.02em;
}
.immersive-shell .hero span{
  margin-bottom:10px;
  background:rgba(255,255,255,.15);
}
.immersive-shell .hero h1{
  max-width:10.5ch;
  margin:0 0 14px;
  font-size:38px;
  line-height:1.04;
  text-wrap:balance;
}
.immersive-shell .hero p{
  max-width:29ch;
  margin-bottom:20px;
  color:rgba(255,255,255,.82);
}
.immersive-shell .hero-actions button{
  min-width:122px;
  height:44px;
  border-radius:14px;
  background:rgba(255,255,255,.13);
}
.immersive-shell .workflow-strip{
  margin:-28px 2px 12px;
  padding:10px;
  border-radius:20px;
  background:rgba(255,250,244,.92);
  backdrop-filter:blur(18px);
  box-shadow:0 18px 46px rgba(52,34,22,.18);
}
.immersive-shell .workflow-chip{
  justify-content:center;
  gap:6px;
}
.immersive-shell .workflow-chip b{
  width:28px;
  height:28px;
  background:#efe1d5;
}
.immersive-shell .workflow-chip.active b{
  background:#17100d;
}
.live-console{
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:14px;
  margin:0 2px 12px;
  padding:14px 16px;
  border-radius:20px;
  background:linear-gradient(135deg,#231813,#3a241b);
  color:#fff;
  box-shadow:0 18px 44px rgba(50,31,20,.22);
}
.live-console span{
  display:block;
  margin-bottom:4px;
  color:rgba(255,255,255,.58);
  font-size:11px;
}
.live-console b{
  display:block;
  color:#fff;
  font-size:13px;
  line-height:1.35;
}
.live-console i{
  width:14px;
  height:14px;
  border-radius:50%;
  background:#22c55e;
  box-shadow:0 0 0 7px rgba(34,197,94,.12);
  flex:0 0 auto;
}
.live-console i.active{
  animation:pulseLive 1s infinite ease-in-out;
}
@keyframes pulseLive{
  0%,100%{transform:scale(.86);opacity:.65}
  50%{transform:scale(1.08);opacity:1}
}
.immersive-shell .creation-panel{
  margin-top:12px;
  padding:18px;
  border-radius:28px;
  background:linear-gradient(180deg,rgba(255,255,255,.96),rgba(255,250,245,.9));
  border:1px solid rgba(122,92,68,.14);
  box-shadow:0 24px 60px rgba(58,39,25,.12);
}
.immersive-shell .section-head b{
  font-size:21px;
}
.immersive-shell .service-pill{
  border-radius:18px;
  background:#fff8ef;
}
.immersive-shell textarea{
  min-height:150px;
  border-radius:20px;
  background:#fffdf9;
  font-size:16px;
  box-shadow:inset 0 1px 0 rgba(255,255,255,.8);
}
.immersive-shell .chips button,
.immersive-shell .mode-switch button{
  border-radius:16px;
}
.immersive-shell .primary{
  border-radius:20px;
  min-height:60px;
  background:linear-gradient(135deg,#17100d,#0f766e 52%,#19a092);
  box-shadow:0 22px 42px rgba(15,118,110,.24);
}
.immersive-shell .primary.green{
  background:linear-gradient(135deg,#17211f,#0f766e 58%,#1ca18f);
}
.immersive-shell .upload-box{
  min-height:210px;
  border-radius:24px;
}
.immersive-shell .quick-tabs{
  left:16px;
  right:16px;
  bottom:14px;
  border-radius:24px;
  padding:8px;
  background:rgba(255,250,245,.9);
  border:1px solid rgba(93,70,52,.16);
  box-shadow:0 22px 64px rgba(45,30,20,.22);
}
.immersive-shell .quick-tabs button{
  border-radius:18px;
}
.immersive-shell .quick-tabs button.active{
  background:linear-gradient(180deg,#17100d,#31231d);
}
@media(min-width:720px){
  .immersive-shell{
    max-width:460px;
    margin:0 auto;
    box-shadow:0 0 0 1px rgba(120,92,64,.08),0 24px 80px rgba(40,28,22,.15);
  }
  .immersive-shell .hero{
    margin-left:-14px;
    margin-right:-14px;
  }
  .immersive-shell .quick-tabs{
    left:50%;
    right:auto;
    width:428px;
    transform:translateX(-50%);
  }
}
</style>

<style scoped>
.consumer-shell{
  background:
    radial-gradient(circle at top left, rgba(180,83,42,.12), transparent 28%),
    radial-gradient(circle at 92% 4%, rgba(15,118,110,.12), transparent 24%),
    linear-gradient(180deg, #f9f5ef 0%, #f3ede6 52%, #efe7dd 100%);
}
.consumer-top{
  margin:-14px -14px 12px;
  padding:14px 14px 12px;
  border-bottom:1px solid rgba(87,65,44,.08);
  background:rgba(248,241,232,.82);
}
.brand img{
  width:38px;
  height:38px;
  border-radius:10px;
  box-shadow:0 8px 20px rgba(39,28,20,.12);
}
.hero{
  min-height:212px;
  padding:24px 18px 20px;
  border-radius:22px;
  background:
    radial-gradient(circle at 82% 18%, rgba(255,255,255,.16), transparent 26%),
    radial-gradient(circle at 18% 22%, rgba(255,255,255,.08), transparent 22%),
    linear-gradient(135deg, #231813 0%, #5f3124 46%, #9d5a35 100%);
  box-shadow:0 24px 56px rgba(87,52,29,.24);
}
.hero:after{
  width:128px;
  height:128px;
  right:12px;
  top:-6px;
  background:
    radial-gradient(circle, rgba(255,255,255,.16) 0, rgba(255,255,255,.08) 38%, transparent 70%);
  box-shadow:-48px 78px 0 rgba(255,255,255,.06);
}
.hero span{
  padding:6px 10px;
  background:rgba(255,255,255,.14);
  letter-spacing:.8px;
}
.hero h1{
  max-width:12ch;
  font-size:30px;
  line-height:1.1;
}
.hero p{
  margin:0 0 16px;
  max-width:26ch;
  color:rgba(255,255,255,.88);
  font-size:13px;
  line-height:1.55;
}
.hero-actions button{
  min-width:108px;
  border-color:rgba(255,255,255,.2);
  background:rgba(255,255,255,.12);
  backdrop-filter:blur(10px);
}
.workflow-strip{
  display:grid;
  grid-template-columns:1fr 18px 1fr 18px 1fr;
  align-items:center;
  gap:8px;
  margin:12px 0 4px;
  padding:10px 12px;
  border-radius:16px;
  background:rgba(255,255,255,.7);
  border:1px solid rgba(87,65,44,.08);
  box-shadow:0 10px 26px rgba(76,53,33,.06);
}
.workflow-chip{
  display:flex;
  align-items:center;
  gap:8px;
  min-width:0;
  color:#7a6758;
}
.workflow-chip b{
  display:flex;
  align-items:center;
  justify-content:center;
  width:26px;
  height:26px;
  border-radius:999px;
  background:#f3e8de;
  color:#8b5a3c;
  font-size:10px;
  flex:0 0 auto;
}
.workflow-chip span{
  overflow:hidden;
  white-space:nowrap;
  text-overflow:ellipsis;
  font-size:12px;
  font-weight:800;
}
.workflow-chip.active b{
  background:#201a17;
  color:#fff;
}
.workflow-chip.active span{
  color:#201a17;
}
.workflow-arrow{
  color:#b49a87;
  text-align:center;
  font-weight:900;
}
.panel{
  margin-top:14px;
  padding:16px;
  border-radius:22px;
  background:rgba(255,255,255,.82);
  border:1px solid rgba(87,65,44,.09);
  box-shadow:0 18px 42px rgba(69,49,31,.08);
  backdrop-filter:blur(14px);
}
.section-head{
  margin-bottom:10px;
}
.section-head span{
  color:#b06539;
}
.section-head b{
  color:#221913;
}
.service-pill{
  display:flex;
  align-items:center;
  gap:8px;
  margin:2px 0 12px;
  padding:10px 12px;
  border-radius:14px;
  background:#fbf4ec;
  border:1px solid #ecd9c8;
}
.service-pill i{
  width:8px;
  height:8px;
  border-radius:50%;
  background:#0f766e;
  box-shadow:0 0 0 4px rgba(15,118,110,.12);
}
.service-pill span{
  color:#9a7c68;
  font-size:11px;
  font-weight:800;
}
.service-pill b{
  margin-left:auto;
  color:#221913;
  font-size:12px;
  font-weight:900;
}
.service-pill em{
  margin-left:8px;
  color:#8a7161;
  font-size:11px;
  font-style:normal;
}
.service-pill.teal i{ background:#0f766e; box-shadow:0 0 0 4px rgba(15,118,110,.12); }
.mini-note{
  margin-top:10px;
  padding:10px 12px;
  border-radius:12px;
  background:rgba(15,23,42,.04);
  color:#6f5a4d;
  font-size:12px;
  line-height:1.55;
}
.chips{
  grid-template-columns:repeat(3,minmax(0,1fr));
}
.chips button,
.mode-switch button{
  min-height:42px;
  background:#fffaf4;
  border-color:#e7d7c9;
  color:#745e4f;
  box-shadow:0 1px 0 rgba(255,255,255,.8) inset;
}
.chips button.active,
.mode-switch button.active{
  background:linear-gradient(180deg,#211a17,#34271f);
  border-color:#211a17;
  color:#fff;
}
.primary{
  min-height:56px;
  border-radius:16px;
  background:linear-gradient(135deg,#0f766e,#1d9b8f 54%,#0b5b56);
  box-shadow:0 18px 30px rgba(15,118,110,.22);
}
.primary.green{
  background:linear-gradient(135deg,#1e6f60,#0f766e 55%,#124b42);
}
.result-card{
  margin-top:16px;
  border-radius:18px;
  overflow:hidden;
  border-color:#ead8c9;
  background:linear-gradient(180deg,#fffdf9,#f8f0e6);
}
.result-card>img{
  border-bottom:1px solid rgba(87,65,44,.08);
  max-height:360px;
}
.result-info{
  padding:14px;
}
.result-info b{
  font-size:15px;
}
.result-info p{
  color:#6e584a;
}
.result-info a,
.result-info button{
  border-radius:999px;
  background:#201a17;
}
.upload-box{
  border-radius:18px;
  border-color:#dcc2ae;
  background:linear-gradient(180deg,#fffaf4,#f8efe5);
}
.upload-box span{
  color:#8c705e;
}
.progress{
  height:9px;
  border-radius:999px;
  background:#eadfd5;
}
.progress span{
  background:linear-gradient(90deg,#0f766e,#1d9b8f);
}
.gallery{
  grid-template-columns:repeat(2,minmax(0,1fr));
}
.gallery article{
  border-radius:16px;
  border-color:#ead8c9;
  background:#fffdf9;
  box-shadow:0 10px 24px rgba(74,50,31,.06);
}
.gallery img,.model-tile{
  border-bottom:1px solid rgba(87,65,44,.08);
}
.gallery button{
  border-radius:999px;
  background:#201a17;
}
.empty{
  color:#8a7161;
}
.quick-tabs{
  bottom:12px;
  border-radius:18px;
  padding:8px;
  background:rgba(255,251,246,.92);
  border:1px solid rgba(87,65,44,.12);
  box-shadow:0 18px 50px rgba(57,38,26,.18);
}
.quick-tabs button{
  min-height:48px;
  border-radius:12px;
  color:#927868;
}
.quick-tabs button.active{
  background:linear-gradient(180deg,#1e1714,#2d221d);
}
@media(min-width:720px){
  .consumer-shell{
    max-width:460px;
    margin:0 auto;
    box-shadow:0 0 0 1px rgba(120,92,64,.08),0 24px 80px rgba(40,28,22,.15);
  }
  .quick-tabs{
    left:50%;
    right:auto;
    width:432px;
    transform:translateX(-50%);
  }
}
</style>

<style scoped>
/* Must stay last: fixes the C-end mobile layout after older style blocks. */
.consumer-shell.immersive-shell{
  min-height:100dvh !important;
  padding:0 14px 28px !important;
  overflow-x:hidden !important;
  background:linear-gradient(180deg,#fbf6ef 0%,#f1e8df 52%,#ece0d5 100%) !important;
  color:#201a17 !important;
}
.consumer-shell.immersive-shell *,
.consumer-shell.immersive-shell *::before,
.consumer-shell.immersive-shell *::after{
  box-sizing:border-box !important;
}
.consumer-shell.immersive-shell .ambient-layer,
.consumer-shell.immersive-shell .hero-glass,
.consumer-shell.immersive-shell .workflow-strip,
.consumer-shell.immersive-shell .live-console,
.consumer-shell.immersive-shell .service-pill,
.consumer-shell.immersive-shell .mini-note{
  display:none !important;
}
.consumer-shell.immersive-shell .consumer-top{
  position:sticky !important;
  top:0 !important;
  z-index:20 !important;
  margin:0 -14px !important;
  padding:12px 16px !important;
  background:rgba(251,246,239,.94) !important;
  color:#201a17 !important;
  border-bottom:1px solid rgba(87,65,44,.08) !important;
}
.consumer-shell.immersive-shell .brand span{ color:#8a7161 !important; }
.consumer-shell.immersive-shell .icon-btn{
  width:38px !important;
  height:38px !important;
  flex:0 0 38px !important;
  border-radius:12px !important;
  background:#fffaf5 !important;
  color:#201a17 !important;
  box-shadow:0 8px 22px rgba(58,39,25,.08) !important;
}
.consumer-shell.immersive-shell .hero{
  min-height:188px !important;
  margin:14px 0 12px !important;
  padding:22px 18px 18px !important;
  border-radius:24px !important;
  background:radial-gradient(circle at 92% 18%,rgba(255,255,255,.18),transparent 94px),linear-gradient(135deg,#241814 0%,#713e2d 60%,#af6840 100%) !important;
  box-shadow:0 18px 44px rgba(78,48,29,.18) !important;
}
.consumer-shell.immersive-shell .hero:after{ display:none !important; }
.consumer-shell.immersive-shell .hero span{
  margin:0 0 11px !important;
  color:#fff !important;
}
.consumer-shell.immersive-shell .hero h1{
  max-width:10em !important;
  margin:0 0 8px !important;
  font-size:30px !important;
  line-height:1.08 !important;
}
.consumer-shell.immersive-shell .hero p{
  max-width:24em !important;
  margin:0 0 15px !important;
  color:rgba(255,255,255,.82) !important;
  font-size:13px !important;
  line-height:1.55 !important;
}
.consumer-shell.immersive-shell .hero-actions{
  display:grid !important;
  grid-template-columns:repeat(2,minmax(0,1fr)) !important;
  gap:10px !important;
}
.consumer-shell.immersive-shell .hero-actions button{
  width:100% !important;
  min-width:0 !important;
  height:42px !important;
  border-radius:14px !important;
}
.consumer-shell.immersive-shell .quick-tabs{
  position:static !important;
  left:auto !important;
  right:auto !important;
  bottom:auto !important;
  width:100% !important;
  transform:none !important;
  display:grid !important;
  grid-template-columns:repeat(3,minmax(0,1fr)) !important;
  gap:6px !important;
  margin:0 0 12px !important;
  padding:7px !important;
  border-radius:18px !important;
  background:rgba(255,250,245,.94) !important;
  border:1px solid rgba(87,65,44,.12) !important;
  box-shadow:0 10px 28px rgba(57,38,26,.08) !important;
}
.consumer-shell.immersive-shell .quick-tabs button{
  min-width:0 !important;
  height:48px !important;
  min-height:0 !important;
  padding:0 4px !important;
  border-radius:13px !important;
  font-size:11px !important;
  white-space:normal !important;
}
.consumer-shell.immersive-shell .quick-tabs button.active{
  background:#201a17 !important;
  color:#fff !important;
}
.consumer-shell.immersive-shell .creation-panel{
  width:100% !important;
  margin:0 !important;
  padding:17px !important;
  border-radius:24px !important;
  background:rgba(255,255,255,.95) !important;
  border:1px solid rgba(87,65,44,.1) !important;
  box-shadow:0 16px 38px rgba(58,39,25,.09) !important;
}
.consumer-shell.immersive-shell .section-head{
  margin:0 0 12px !important;
}
.consumer-shell.immersive-shell .section-head b{
  font-size:20px !important;
  line-height:1.2 !important;
}
.consumer-shell.immersive-shell textarea{
  width:100% !important;
  min-height:124px !important;
  max-height:210px !important;
  border-radius:18px !important;
  font-size:15px !important;
}
.consumer-shell.immersive-shell .chips,
.consumer-shell.immersive-shell .chips.compact{
  grid-template-columns:repeat(3,minmax(0,1fr)) !important;
}
.consumer-shell.immersive-shell .mode-switch{
  grid-template-columns:repeat(2,minmax(0,1fr)) !important;
}
.consumer-shell.immersive-shell .chips button,
.consumer-shell.immersive-shell .mode-switch button{
  min-width:0 !important;
  min-height:40px !important;
  padding:0 6px !important;
  border-radius:14px !important;
  font-size:13px !important;
  line-height:1.15 !important;
  white-space:normal !important;
}
.consumer-shell.immersive-shell .primary{
  min-height:54px !important;
  border-radius:18px !important;
  background:#b4532a !important;
  box-shadow:0 12px 26px rgba(180,83,42,.24) !important;
}
.consumer-shell.immersive-shell .primary.green{
  background:#0f766e !important;
  box-shadow:0 12px 26px rgba(15,118,110,.2) !important;
}
.consumer-shell.immersive-shell .upload-box{
  min-height:168px !important;
  border-radius:20px !important;
}
.consumer-shell.immersive-shell .upload-box img{
  width:100% !important;
  height:210px !important;
  object-fit:cover !important;
}
.consumer-shell.immersive-shell .gallery{
  grid-template-columns:repeat(2,minmax(0,1fr)) !important;
}
.consumer-shell.immersive-shell .gallery article{
  min-width:0 !important;
}
.consumer-shell.immersive-shell .gallery article > button{
  width:calc(100% - 18px) !important;
}
.single-route{
  display:flex;
  flex-direction:column;
  gap:6px;
  margin-top:12px;
  padding:12px;
  border-radius:16px;
  background:#fff7ed;
  border:1px solid #fed7aa;
  color:#7c2d12;
}
.single-route b{
  font-size:14px;
  line-height:1.25;
}
.single-route span{
  color:#9a5a2a;
  font-size:12px;
  line-height:1.55;
}
.single-route.museum{
  background:#ecfdf5;
  border-color:#bbf7d0;
  color:#065f46;
}
.single-route.museum span{
  color:#047857;
}
.dist-head small{
  color:#8a7161;
  font-size:12px;
  font-weight:900;
}
.dist-row.single{
  grid-template-columns:1fr !important;
}
.model-preview-modal{
  position:fixed !important;
  inset:0 !important;
  z-index:9999 !important;
  display:flex !important;
  flex-direction:column !important;
  padding:calc(env(safe-area-inset-top,0px) + 12px) 14px calc(env(safe-area-inset-bottom,0px) + 14px) !important;
  background:
    radial-gradient(circle at 70% 12%,rgba(180,83,42,.28),transparent 180px),
    linear-gradient(180deg,#17100d 0%,#2a1d18 100%) !important;
  color:#fff !important;
}
.model-preview-top{
  display:flex !important;
  align-items:center !important;
  gap:12px !important;
  flex:0 0 auto !important;
}
.model-preview-top b,
.model-preview-top span{
  display:block !important;
}
.model-preview-top b{
  font-size:17px !important;
  line-height:1.2 !important;
}
.model-preview-top span{
  margin-top:3px !important;
  color:rgba(255,255,255,.58) !important;
  font-size:12px !important;
}
.preview-back{
  display:inline-flex !important;
  align-items:center !important;
  gap:3px !important;
  height:40px !important;
  padding:0 12px 0 8px !important;
  border:1px solid rgba(255,255,255,.13) !important;
  border-radius:999px !important;
  background:rgba(255,255,255,.08) !important;
  color:#fff !important;
  font-weight:800 !important;
}
.preview-back svg{
  width:20px !important;
  height:20px !important;
}
.model-viewer-wrap{
  position:relative !important;
  flex:1 1 auto !important;
  min-height:0 !important;
  margin:14px 0 !important;
  overflow:hidden !important;
  border:1px solid rgba(255,255,255,.1) !important;
  border-radius:28px !important;
  background:
    radial-gradient(circle at 50% 45%,rgba(255,255,255,.08),transparent 210px),
    linear-gradient(180deg,#30231d,#15100d) !important;
  box-shadow:0 24px 80px rgba(0,0,0,.28) !important;
}
.model-viewer{
  display:block !important;
  width:100% !important;
  height:100% !important;
  min-height:420px !important;
  --poster-color:transparent;
  background:transparent !important;
}
.model-loading,
.model-error{
  position:absolute !important;
  left:50% !important;
  top:50% !important;
  transform:translate(-50%,-50%) !important;
  display:flex !important;
  flex-direction:column !important;
  align-items:center !important;
  justify-content:center !important;
  gap:10px !important;
  padding:18px !important;
  border-radius:20px !important;
  background:rgba(0,0,0,.24) !important;
  color:#fff !important;
  text-align:center !important;
  backdrop-filter:blur(14px) !important;
}
.model-loading i{
  width:28px !important;
  height:28px !important;
  border:3px solid rgba(255,255,255,.2) !important;
  border-top-color:#fff !important;
  border-radius:50% !important;
  animation:modelSpin .85s linear infinite !important;
}
.model-error span{
  max-width:14em !important;
  color:rgba(255,255,255,.68) !important;
  font-size:12px !important;
  line-height:1.5 !important;
}
.model-preview-bottom{
  display:grid !important;
  grid-template-columns:1fr 1fr 1fr !important;
  gap:10px !important;
  flex:0 0 auto !important;
}
.model-preview-bottom button,
.model-preview-bottom a,
.model-preview-bottom .download-action,
.format-select{
  display:flex !important;
  align-items:center !important;
  justify-content:center !important;
  min-height:50px !important;
  border:1px solid rgba(255,255,255,.14) !important;
  border-radius:18px !important;
  background:rgba(255,255,255,.1) !important;
  color:#fff !important;
  text-decoration:none !important;
  font-size:15px !important;
  font-weight:900 !important;
}
.format-select{
  flex-direction:column !important;
  gap:2px !important;
  padding:5px 8px !important;
}
.format-select span{
  color:rgba(255,255,255,.58) !important;
  font-size:10px !important;
  line-height:1 !important;
}
.format-select select{
  width:100% !important;
  border:0 !important;
  background:transparent !important;
  color:#fff !important;
  font-size:15px !important;
  font-weight:900 !important;
  text-align:center !important;
  outline:none !important;
}
.format-select option{
  color:#111827 !important;
}
.model-preview-bottom a,
.model-preview-bottom .download-action{
  border-color:#c27643 !important;
  background:#c27643 !important;
}
.simple-note{
  margin:10px 0 0 !important;
  padding:11px 12px !important;
  border-radius:10px !important;
  background:#f0fdfa !important;
  border:1px solid #ccfbf1 !important;
  color:#0f766e !important;
  font-size:12px !important;
  line-height:1.55 !important;
  font-weight:800 !important;
}
@keyframes modelSpin{
  to{ transform:rotate(360deg); }
}
@media(min-width:720px){
  .consumer-shell.immersive-shell{
    max-width:460px !important;
    margin:0 auto !important;
    box-shadow:0 0 0 1px rgba(120,92,64,.08),0 24px 80px rgba(40,28,22,.15) !important;
  }
}
</style>

<style scoped>
/* C端流程动效升级：作为最后样式块覆盖历史样式。 */
.consumer-shell.immersive-shell{
  padding:0 14px 32px !important;
  background:
    radial-gradient(circle at 8% 4%,rgba(194,118,67,.18),transparent 180px),
    radial-gradient(circle at 92% 18%,rgba(15,118,110,.14),transparent 160px),
    linear-gradient(180deg,#fbf6ef 0%,#f4ece4 48%,#eadfd4 100%) !important;
}
.consumer-shell.immersive-shell .ambient-layer{
  display:block !important;
  position:fixed !important;
  inset:0 !important;
  z-index:0 !important;
  pointer-events:none !important;
  opacity:.65 !important;
  mix-blend-mode:normal !important;
  background:
    radial-gradient(circle at 18% 16%,rgba(255,255,255,.62),transparent 96px),
    radial-gradient(circle at 84% 12%,rgba(194,118,67,.22),transparent 150px),
    radial-gradient(circle at 70% 78%,rgba(15,118,110,.12),transparent 180px) !important;
  animation:ambientFloat 8s ease-in-out infinite alternate !important;
}
@keyframes ambientFloat{to{transform:translate3d(0,-16px,0) scale(1.03)}}
.consumer-shell.immersive-shell .consumer-top{
  background:rgba(251,246,239,.72) !important;
  backdrop-filter:blur(22px) saturate(1.25) !important;
}
.consumer-shell.immersive-shell .hero{
  position:relative !important;
  min-height:248px !important;
  margin:12px 0 14px !important;
  padding:18px !important;
  overflow:hidden !important;
  border:1px solid rgba(255,255,255,.2) !important;
  border-radius:30px !important;
  background:
    linear-gradient(180deg,rgba(255,255,255,.08),rgba(255,255,255,0)),
    radial-gradient(circle at 88% 18%,rgba(255,232,202,.24),transparent 112px),
    linear-gradient(135deg,#201510 0%,#623628 52%,#b66b42 100%) !important;
  box-shadow:0 26px 66px rgba(70,43,25,.24) !important;
}
.hero-motion-orb{position:absolute;right:-44px;top:-34px;width:172px;height:172px;border-radius:50%;background:linear-gradient(145deg,rgba(255,255,255,.28),rgba(255,255,255,.04));border:1px solid rgba(255,255,255,.22);filter:blur(.1px);animation:orbDrift 5.5s ease-in-out infinite alternate}@keyframes orbDrift{to{transform:translate(-16px,18px) scale(1.08)}}
.hero-meta{position:relative;z-index:2;display:flex;flex-wrap:wrap;gap:7px;margin-bottom:13px}.hero-meta span{width:auto !important;margin:0 !important;padding:6px 10px !important;border:1px solid rgba(255,255,255,.18) !important;border-radius:999px !important;background:rgba(255,255,255,.14) !important;color:#fff !important;font-size:11px !important;font-weight:900 !important}.consumer-shell.immersive-shell .purpose-change{position:absolute !important;z-index:3 !important;right:16px !important;top:18px !important;margin:0 !important;background:rgba(255,255,255,.12) !important}.consumer-shell.immersive-shell .hero h1{font-size:34px !important;letter-spacing:-.04em !important}
.hero-stats-mobile{position:relative;z-index:2;display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:16px 0 12px}.hero-stats-mobile article{padding:10px 8px;border:1px solid rgba(255,255,255,.14);border-radius:18px;background:rgba(255,255,255,.1);color:#fff;backdrop-filter:blur(14px)}.hero-stats-mobile b,.hero-stats-mobile span{display:block}.hero-stats-mobile b{font-size:20px;line-height:1}.hero-stats-mobile span{margin-top:4px;color:rgba(255,255,255,.62);font-size:10px;font-weight:900}
.flow-card{position:relative;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin:0 0 12px;padding:13px 10px 10px;border:1px solid rgba(92,66,48,.1);border-radius:24px;background:rgba(255,250,245,.84);box-shadow:0 18px 44px rgba(60,40,25,.1);backdrop-filter:blur(18px)}.flow-line{position:absolute;left:22px;right:22px;top:29px;height:3px;border-radius:999px;background:#ead9c8;overflow:hidden}.flow-line i{display:block;height:100%;border-radius:999px;background:linear-gradient(90deg,#b4532a,#0f766e);transition:width .45s cubic-bezier(.2,.8,.2,1)}.flow-card article{position:relative;z-index:1;min-width:0;text-align:center;color:#8a7161;transition:transform .25s ease,color .25s ease}.flow-card b{display:inline-flex;align-items:center;justify-content:center;width:34px;height:34px;border-radius:50%;background:#f5eadf;border:2px solid #fff;color:#9a6a4e;font-size:10px;box-shadow:0 6px 16px rgba(65,42,28,.1);transition:all .25s}.flow-card span,.flow-card em{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.flow-card span{margin-top:7px;font-size:11px;font-weight:950;color:#4a3429}.flow-card em{margin-top:3px;font-size:9px;font-style:normal;color:#9a8474}.flow-card article.active{transform:translateY(-2px)}.flow-card article.active b{background:#201a17;color:#fff;box-shadow:0 10px 22px rgba(32,26,23,.22)}.flow-card article.done b{background:#0f766e;color:#fff}
.status-console{display:flex;align-items:center;gap:11px;margin:0 0 12px;padding:12px;border-radius:20px;background:linear-gradient(135deg,#fff,#fff8ef);border:1px solid rgba(92,66,48,.1);box-shadow:0 14px 32px rgba(60,40,25,.08)}.status-console i{width:12px;height:12px;border-radius:50%;background:#0f766e;box-shadow:0 0 0 7px rgba(15,118,110,.12)}.status-console.live i{animation:pulseLive 1s infinite ease-in-out}.status-console div{flex:1;min-width:0}.status-console span,.status-console b{display:block}.status-console span{font-size:10px;font-weight:900;color:#9a7c68;letter-spacing:.08em}.status-console b{margin-top:3px;color:#201a17;font-size:13px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.status-console button{height:34px;border:0;border-radius:999px;background:#201a17;color:#fff;padding:0 12px;font-size:12px;font-weight:900}
.consumer-shell.immersive-shell .quick-tabs{position:sticky !important;top:62px !important;z-index:12 !important;margin-bottom:12px !important;background:rgba(255,251,246,.82) !important;backdrop-filter:blur(18px) !important}.quick-tabs small{display:inline-flex;align-items:center;justify-content:center;min-width:16px;height:16px;margin-left:2px;border-radius:999px;background:rgba(180,83,42,.12);color:#b4532a;font-size:10px;font-weight:950}.quick-tabs button.active small{background:rgba(255,255,255,.18);color:#fff}
.creation-guide{display:grid;grid-template-columns:auto 1fr auto 1fr auto 1.25fr;align-items:center;gap:6px;margin:0 0 12px;padding:10px;border-radius:18px;background:#fff7ed;border:1px solid #fed7aa;color:#9a3412}.creation-guide.green{background:#ecfdf5;border-color:#bbf7d0;color:#047857}.creation-guide i{display:inline-flex;align-items:center;justify-content:center;width:22px;height:22px;border-radius:50%;background:#fff;font-size:10px;font-style:normal;font-weight:950}.creation-guide span{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:10px;font-weight:900}
.preset-scroll{display:flex;gap:8px;margin:10px -2px 2px;padding:0 2px 4px;overflow-x:auto;scroll-snap-type:x mandatory}.preset-scroll::-webkit-scrollbar{display:none}.preset-scroll button{flex:0 0 74%;scroll-snap-align:start;min-height:42px;padding:9px 11px;border:1px solid #ead8c9;border-radius:16px;background:#fffdf9;color:#6e5547;text-align:left;font-size:12px;font-weight:800;line-height:1.35;box-shadow:0 8px 18px rgba(68,45,29,.06)}
.ai-progress-card{display:flex;align-items:center;gap:12px;margin-top:12px;padding:13px;border-radius:18px;background:#fff7ed;border:1px solid #fed7aa;color:#7c2d12}.ai-progress-card.green{background:#ecfdf5;border-color:#bbf7d0;color:#065f46}.ai-progress-card b,.ai-progress-card span{display:block}.ai-progress-card b{font-size:13px}.ai-progress-card span{margin-top:3px;font-size:11px;line-height:1.45;color:#8a5b3a}.ai-progress-card.green span{color:#047857}.ai-spinner{width:28px;height:28px;border-radius:50%;border:3px solid rgba(180,83,42,.18);border-top-color:#b4532a;animation:modelSpin .85s linear infinite}.ai-progress-card.green .ai-spinner{border-color:rgba(15,118,110,.18);border-top-color:#0f766e}
.gallery-summary{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-bottom:12px}.gallery-summary article{padding:12px 8px;border-radius:18px;background:#fff7ed;border:1px solid #fed7aa;text-align:center}.gallery-summary b,.gallery-summary span{display:block}.gallery-summary b{font-size:22px;color:#7c2d12}.gallery-summary span{font-size:11px;font-weight:900;color:#9a5a2a}
.consumer-shell.immersive-shell .creation-panel{animation:panelIn .32s ease both}@keyframes panelIn{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:translateY(0)}}.consumer-shell.immersive-shell .primary:not(:disabled){position:relative;overflow:hidden}.consumer-shell.immersive-shell .primary:not(:disabled)::after{content:"";position:absolute;inset:0;background:linear-gradient(90deg,transparent,rgba(255,255,255,.22),transparent);transform:translateX(-110%);animation:buttonShine 2.8s ease-in-out infinite}@keyframes buttonShine{65%,100%{transform:translateX(120%)}}
.purpose-card{position:relative;overflow:hidden;animation:purposePop .38s cubic-bezier(.2,.8,.2,1) both}.purpose-aurora{position:absolute;right:-80px;top:-80px;width:220px;height:220px;border-radius:50%;background:radial-gradient(circle,rgba(255,255,255,.42),transparent 64%);animation:orbDrift 4.5s ease-in-out infinite alternate}.purpose-options button{transition:transform .18s ease,box-shadow .18s ease}.purpose-options button:active{transform:scale(.985)}@keyframes purposePop{from{opacity:0;transform:translateY(12px) scale(.98)}to{opacity:1;transform:translateY(0) scale(1)}}
@media(max-width:380px){.flow-card{gap:5px;padding-left:7px;padding-right:7px}.flow-card span{font-size:10px}.flow-card em{display:none}.hero-stats-mobile article{padding:9px 5px}.creation-guide{grid-template-columns:auto 1fr}.creation-guide i:nth-of-type(n+2),.creation-guide i:nth-of-type(n+2)+span{display:none}.preset-scroll button{flex-basis:84%}}
</style>

<style scoped>
/* C端 App 化布局升级：底部导航 + 页面切换 + 得物式轻卡片。 */
.consumer-shell.immersive-shell{padding:0 14px calc(104px + env(safe-area-inset-bottom,0px)) !important;background:#f5f3ef !important}.consumer-shell.immersive-shell .consumer-top{height:58px !important;margin:0 -14px !important;padding:10px 16px !important;background:rgba(245,243,239,.9) !important;border-bottom:0 !important;box-shadow:none !important}.consumer-shell.immersive-shell .brand b{font-size:16px !important;letter-spacing:.02em !important}.consumer-shell.immersive-shell .brand span{font-size:10px !important}.consumer-shell.immersive-shell .brand img{width:34px !important;height:34px !important;border-radius:12px !important}.consumer-shell.immersive-shell .hero{min-height:220px !important;margin:6px 0 10px !important;padding:18px 16px 16px !important;border-radius:26px !important;background:radial-gradient(circle at 86% 0%,rgba(255,255,255,.2),transparent 112px),linear-gradient(137deg,#121212 0%,#32231d 50%,#b86639 100%) !important;box-shadow:0 18px 44px rgba(25,20,17,.18) !important}.consumer-shell.immersive-shell .hero h1{max-width:9em !important;margin-top:2px !important;font-size:31px !important;letter-spacing:-.055em !important}.consumer-shell.immersive-shell .hero p{max-width:25em !important;margin-bottom:10px !important;color:rgba(255,255,255,.72) !important}.hero-stats-mobile{margin:12px 0 10px !important}.hero-stats-mobile article{border-radius:16px !important;background:rgba(255,255,255,.09) !important}.consumer-shell.immersive-shell .hero-actions{grid-template-columns:1fr 1fr 1fr !important;gap:7px !important}.consumer-shell.immersive-shell .hero-actions button{height:38px !important;border-radius:999px !important;font-size:12px !important;padding:0 8px !important}
.app-action-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:9px;margin:0 0 11px}.app-action-grid button{min-width:0;padding:12px 8px;border:0;border-radius:22px;background:#fff;box-shadow:0 10px 26px rgba(23,20,18,.06);text-align:left;transition:transform .18s ease,box-shadow .18s ease}.app-action-grid button:active{transform:scale(.97)}.app-action-grid i{display:inline-flex;align-items:center;justify-content:center;height:24px;min-width:30px;margin-bottom:10px;padding:0 7px;border-radius:999px;color:#fff;font-size:10px;font-style:normal;font-weight:950}.app-action-grid i.orange{background:#c25a2e}.app-action-grid i.green{background:#0f766e}.app-action-grid i.dark{background:#171717}.app-action-grid b,.app-action-grid span{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.app-action-grid b{color:#161412;font-size:13px}.app-action-grid span{margin-top:3px;color:#9a9289;font-size:10px;font-weight:800}.consumer-shell.immersive-shell .flow-card{margin-bottom:10px !important;border-radius:22px !important;background:#fff !important;box-shadow:0 10px 28px rgba(23,20,18,.055) !important}.consumer-shell.immersive-shell .status-console{display:none !important}
.consumer-shell.immersive-shell .bottom-tabs{position:fixed !important;left:12px !important;right:12px !important;bottom:calc(10px + env(safe-area-inset-bottom,0px)) !important;top:auto !important;z-index:90 !important;width:auto !important;max-width:436px !important;height:66px !important;margin:0 auto !important;padding:7px !important;border-radius:26px !important;background:rgba(255,255,255,.9) !important;border:1px solid rgba(23,20,18,.08) !important;box-shadow:0 18px 48px rgba(23,20,18,.18) !important;backdrop-filter:blur(24px) saturate(1.2) !important}.consumer-shell.immersive-shell .bottom-tabs button{height:52px !important;border-radius:20px !important;font-size:11px !important;gap:2px !important;transition:transform .18s ease,background .18s ease,color .18s ease !important}.consumer-shell.immersive-shell .bottom-tabs button:active{transform:scale(.96)}.consumer-shell.immersive-shell .bottom-tabs button.active{background:#111 !important;color:#fff !important}.mobile-page-wrap{position:relative;min-height:420px;overflow:visible}.mobile-page-enter-active,.mobile-page-leave-active{transition:opacity .22s ease,transform .26s cubic-bezier(.2,.8,.2,1),filter .26s ease}.mobile-page-enter-from{opacity:0;transform:translateX(18px) scale(.985);filter:blur(4px)}.mobile-page-leave-to{opacity:0;transform:translateX(-18px) scale(.985);filter:blur(4px)}
.consumer-shell.immersive-shell .creation-panel{border-radius:28px !important;background:#fff !important;border:0 !important;box-shadow:0 14px 38px rgba(23,20,18,.07) !important}.consumer-shell.immersive-shell .section-head{align-items:center !important;padding-bottom:10px !important;border-bottom:1px solid #f0ebe6 !important}.consumer-shell.immersive-shell .section-head span{color:#c25a2e !important}.consumer-shell.immersive-shell textarea{border-radius:22px !important;background:#f8f7f5 !important;border-color:#eee9e2 !important;box-shadow:none !important}.consumer-shell.immersive-shell .chips button,.consumer-shell.immersive-shell .mode-switch button{border-radius:18px !important;background:#f8f7f5 !important;border-color:#eee9e2 !important;color:#625a53 !important}.consumer-shell.immersive-shell .chips button.active,.consumer-shell.immersive-shell .mode-switch button.active{background:#111 !important;color:#fff !important;border-color:#111 !important}.consumer-shell.immersive-shell .primary{border-radius:24px !important;background:#111 !important;box-shadow:0 14px 30px rgba(0,0,0,.16) !important}.consumer-shell.immersive-shell .primary.green{background:#0f766e !important}.preset-scroll button{border:0 !important;background:#f8f7f5 !important;box-shadow:none !important;flex-basis:68% !important}.creation-guide{border:0 !important;background:#f8f7f5 !important;color:#6b5748 !important}.creation-guide.green{background:#f0fdfa !important;color:#0f766e !important}.gallery-summary article{border:0 !important;background:#f8f7f5 !important}.gallery-summary b{color:#111 !important}.gallery-summary span{color:#898078 !important}.consumer-shell.immersive-shell .gallery{gap:12px !important}.consumer-shell.immersive-shell .gallery article{border:0 !important;border-radius:22px !important;background:#fff !important;box-shadow:0 10px 28px rgba(23,20,18,.065) !important}.consumer-shell.immersive-shell .gallery img,.consumer-shell.immersive-shell .model-tile{border-radius:22px 22px 0 0 !important}.consumer-shell.immersive-shell .gallery button{border-radius:999px !important;background:#111 !important}.consumer-shell.immersive-shell .gallery .review-submit{background:#c25a2e !important}.production-actions{display:grid !important;grid-template-columns:1fr 1fr !important}.consumer-shell.immersive-shell .production-list article{border:0 !important;border-radius:18px !important;background:#f8f7f5 !important}@media(min-width:720px){.consumer-shell.immersive-shell .bottom-tabs{left:50% !important;right:auto !important;width:432px !important;transform:translateX(-50%) !important}}
</style>


<style scoped>
/* Consumer atelier refresh: premium museum-creative workbench with richer hierarchy on all screens. */
.consumer-shell.immersive-shell { isolation: isolate; color: #27211f; }
.consumer-shell.immersive-shell button,.consumer-shell.immersive-shell a { -webkit-tap-highlight-color: transparent; }
.consumer-shell.immersive-shell button:focus-visible,.consumer-shell.immersive-shell a:focus-visible,.consumer-shell.immersive-shell textarea:focus-visible,.consumer-shell.immersive-shell input:focus-visible,.consumer-shell.immersive-shell select:focus-visible { outline: 3px solid rgba(235,150,88,.52) !important; outline-offset: 3px; }
.consumer-shell.immersive-shell .consumer-top { min-height:72px; border-bottom:1px solid rgba(75,50,37,.08) !important; }
.consumer-shell.immersive-shell .brand img { border-radius:12px !important; box-shadow:0 8px 20px rgba(59,36,25,.18); }
.consumer-shell.immersive-shell .brand b { letter-spacing:-.025em; }
.consumer-shell.immersive-shell .hero { display:flex; flex-direction:column; justify-content:center; min-height:282px !important; padding:28px 22px !important; border-radius:32px !important; background:linear-gradient(115deg,rgba(16,11,10,.22),transparent 56%),radial-gradient(circle at 88% 18%,rgba(255,216,164,.42),transparent 12%),radial-gradient(circle at 87% 86%,rgba(90,204,185,.25),transparent 20%),linear-gradient(130deg,#211511 0%,#5c3328 54%,#be7147 100%) !important; }
.consumer-shell.immersive-shell .hero::after { content:''; position:absolute; inset:0; pointer-events:none; border-radius:inherit; background-image:linear-gradient(115deg,rgba(255,255,255,.1) 1px,transparent 1px),linear-gradient(25deg,rgba(255,255,255,.06) 1px,transparent 1px); background-size:32px 32px,44px 44px; mask-image:linear-gradient(90deg,#000,transparent 70%); opacity:.35; }
.hero-illustration { position:absolute; right:11px; bottom:8px; width:175px; height:185px; z-index:1; pointer-events:none; transform:rotate(-5deg); }
.hero-halo { position:absolute; inset:22px 10px 16px; border-radius:50%; background:radial-gradient(circle,rgba(255,244,219,.44),rgba(255,219,162,.14) 48%,transparent 70%); filter:blur(2px); animation:heroGlow 4s ease-in-out infinite alternate; }
.hero-card { position:absolute; display:block; width:122px; height:140px; right:18px; bottom:17px; border-radius:24px; transform:rotate(13deg); }
.hero-card-back { right:43px; bottom:31px; background:linear-gradient(145deg,rgba(255,255,255,.25),rgba(255,255,255,.03)); border:1px solid rgba(255,255,255,.28); box-shadow:0 24px 34px rgba(25,12,8,.22); }
.hero-card-front { overflow:hidden; padding:23px 18px; background:linear-gradient(145deg,#fbdec0,#e88351 60%,#913e2a); border:1px solid rgba(255,255,255,.45); box-shadow:0 28px 40px rgba(24,11,7,.38),inset 0 1px 0 rgba(255,255,255,.45); }
.hero-card-front::before,.hero-card-front::after { content:''; position:absolute; border-radius:999px; background:rgba(255,255,255,.25); }.hero-card-front::before { width:116px; height:116px; left:-46px; bottom:-50px; }.hero-card-front::after { width:70px; height:70px; right:-34px; top:-24px; }.hero-card-front i { position:relative; z-index:1; display:block; height:8px; margin-bottom:10px; border-radius:99px; background:rgba(86,35,23,.62); }.hero-card-front i:nth-child(1) { width:58px; background:rgba(255,255,255,.82); }.hero-card-front i:nth-child(2) { width:76px; }.hero-card-front i:nth-child(3) { width:40px; }
.hero-spark { position:absolute; z-index:3; color:#fff1d4; text-shadow:0 8px 16px rgba(65,25,15,.4); font-size:24px; animation:heroSpark 2.8s ease-in-out infinite alternate; }.hero-spark-one { top:18px; right:24px; }.hero-spark-two { bottom:18px; left:22px; font-size:18px; animation-delay:-1.1s; }
.consumer-shell.immersive-shell .hero-meta,.consumer-shell.immersive-shell .hero h1,.consumer-shell.immersive-shell .hero > p,.consumer-shell.immersive-shell .hero-stats-mobile,.consumer-shell.immersive-shell .hero-actions { max-width:calc(100% - 120px); }.consumer-shell.immersive-shell .hero h1 { position:relative; z-index:2; margin-top:0; line-height:1.08; text-wrap:balance; }.consumer-shell.immersive-shell .hero > p { position:relative; z-index:2; margin-top:10px; color:rgba(255,255,255,.77); line-height:1.58; }.consumer-shell.immersive-shell .hero-actions { position:relative; z-index:2; }
.consumer-shell.immersive-shell .app-action-grid button { position:relative; overflow:hidden; min-height:112px; }.consumer-shell.immersive-shell .app-action-grid button::after { content:'↗'; position:absolute; right:13px; top:12px; color:#c6bdb4; font-size:16px; transition:transform .2s ease,color .2s ease; }.consumer-shell.immersive-shell .app-action-grid button:hover { transform:translateY(-4px); box-shadow:0 20px 34px rgba(34,24,18,.12) !important; }.consumer-shell.immersive-shell .app-action-grid button:hover::after { transform:translate(2px,-2px); color:#be5f35; }
.consumer-shell.immersive-shell .creation-panel { position:relative; overflow:hidden; }.consumer-shell.immersive-shell .creation-panel::before { content:''; position:absolute; width:190px; height:190px; right:-110px; top:-120px; border-radius:50%; background:radial-gradient(circle,rgba(234,143,80,.12),transparent 68%); pointer-events:none; }.consumer-shell.immersive-shell .section-head b { font-size:18px; letter-spacing:-.025em; }.consumer-shell.immersive-shell .section-head span { font-weight:900; letter-spacing:.11em; }.consumer-shell.immersive-shell textarea { min-height:132px; padding:16px !important; font-size:14px; line-height:1.65; }.consumer-shell.immersive-shell .primary { min-height:54px; letter-spacing:.01em; transition:transform .2s ease,box-shadow .2s ease,filter .2s ease !important; }.consumer-shell.immersive-shell .primary:not(:disabled):hover { transform:translateY(-2px); filter:brightness(1.08); box-shadow:0 18px 34px rgba(0,0,0,.22) !important; }.consumer-shell.immersive-shell .primary:disabled { opacity:.58; cursor:wait; }
.consumer-shell.immersive-shell .purpose-card { border-radius:34px !important; background:linear-gradient(145deg,rgba(255,255,255,.22),rgba(255,255,255,.08)) !important; }.consumer-shell.immersive-shell .purpose-card h1 { text-wrap:balance; line-height:1.1; }.consumer-shell.immersive-shell .purpose-options button { overflow:hidden; transition:transform .2s ease,box-shadow .2s ease,background .2s ease !important; }.consumer-shell.immersive-shell .purpose-options button::after { content:'→'; position:absolute; right:18px; top:50%; transform:translateY(-50%); font-size:22px; color:#b4532a; opacity:.72; }.consumer-shell.immersive-shell .purpose-options button:hover { transform:translateY(-3px); background:#fff !important; box-shadow:0 18px 30px rgba(32,26,23,.2) !important; }
@keyframes heroGlow { to { transform:scale(1.08); opacity:.78; } } @keyframes heroSpark { to { transform:translateY(-7px) rotate(12deg) scale(1.08); opacity:.7; } }
@media (min-width:860px) { .consumer-shell.immersive-shell { width:min(1180px,calc(100% - 48px)) !important; max-width:1180px !important; min-height:100vh; margin:24px auto !important; padding:0 28px 116px !important; border:1px solid rgba(87,57,42,.09); border-radius:36px; box-shadow:0 32px 100px rgba(55,32,22,.18) !important; }.consumer-shell.immersive-shell .consumer-top { margin:0 -28px 18px !important; padding:16px 28px !important; border-radius:36px 36px 0 0; }.consumer-shell.immersive-shell .hero { min-height:370px !important; margin-bottom:20px !important; padding:52px 54px !important; }.consumer-shell.immersive-shell .hero h1 { font-size:clamp(42px,5vw,62px) !important; }.consumer-shell.immersive-shell .hero > p { max-width:550px !important; font-size:16px; }.consumer-shell.immersive-shell .hero-meta,.consumer-shell.immersive-shell .hero h1,.consumer-shell.immersive-shell .hero > p,.consumer-shell.immersive-shell .hero-stats-mobile,.consumer-shell.immersive-shell .hero-actions { max-width:62%; }.hero-illustration { right:76px; bottom:32px; width:285px; height:285px; transform:rotate(-6deg) scale(1.12); }.hero-card { width:176px; height:204px; border-radius:34px; }.hero-card-back { right:48px; bottom:36px; }.hero-card-front { padding:38px 26px; }.hero-card-front i { height:12px; margin-bottom:15px; }.hero-card-front i:nth-child(1) { width:88px; }.hero-card-front i:nth-child(2) { width:112px; }.hero-card-front i:nth-child(3) { width:62px; }.consumer-shell.immersive-shell .app-action-grid { grid-template-columns:repeat(3,1fr) !important; gap:16px !important; margin-bottom:18px !important; }.consumer-shell.immersive-shell .app-action-grid button { min-height:128px; padding:22px !important; }.consumer-shell.immersive-shell .app-action-grid b { font-size:16px; }.consumer-shell.immersive-shell .flow-card { padding:18px 18px 14px !important; margin-bottom:18px !important; }.consumer-shell.immersive-shell .mobile-page-wrap { max-width:1000px; margin:0 auto; }.consumer-shell.immersive-shell .creation-panel { padding:30px !important; }.consumer-shell.immersive-shell .bottom-tabs { width:560px !important; max-width:calc(100% - 80px) !important; }.consumer-shell.immersive-shell .gallery { grid-template-columns:repeat(3,minmax(0,1fr)) !important; } }
@media (max-width:420px) { .hero-illustration { right:-10px; opacity:.82; transform:rotate(-5deg) scale(.82); transform-origin:bottom right; }.consumer-shell.immersive-shell .hero-meta,.consumer-shell.immersive-shell .hero h1,.consumer-shell.immersive-shell .hero > p,.consumer-shell.immersive-shell .hero-stats-mobile,.consumer-shell.immersive-shell .hero-actions { max-width:calc(100% - 82px); }.consumer-shell.immersive-shell .hero h1 { font-size:30px !important; } }
@media (prefers-reduced-motion:reduce) { .consumer-shell.immersive-shell *,.consumer-shell.immersive-shell *::before,.consumer-shell.immersive-shell *::after { animation-duration:.01ms !important; animation-iteration-count:1 !important; transition-duration:.01ms !important; } }
</style>


<style scoped>
/* Gate is intentionally a session-entry screen: users cannot reach the workbench before choosing. */
.consumer-shell.immersive-shell:has(.purpose-gate) { height: 100dvh; overflow: hidden; }
.consumer-shell.immersive-shell .purpose-gate {
  position: fixed !important;
  inset: 0 !important;
  z-index: 1000 !important;
  min-height: 100dvh;
  padding: max(24px, env(safe-area-inset-top)) max(20px, env(safe-area-inset-right)) max(24px, env(safe-area-inset-bottom)) max(20px, env(safe-area-inset-left)) !important;
  background:
    radial-gradient(circle at 86% 14%, rgba(255, 195, 123, .35), transparent 22%),
    radial-gradient(circle at 12% 88%, rgba(35, 171, 150, .26), transparent 28%),
    linear-gradient(145deg, #1e120f 0%, #613527 56%, #bc7046 100%) !important;
}
.consumer-shell.immersive-shell .purpose-gate::before,
.consumer-shell.immersive-shell .purpose-gate::after {
  content: '';
  position: absolute;
  width: min(62vw, 540px);
  aspect-ratio: 1;
  border-radius: 50%;
  border: 1px solid rgba(255,255,255,.14);
  pointer-events: none;
}
.consumer-shell.immersive-shell .purpose-gate::before { right: -18vw; top: -24vw; box-shadow: inset 0 0 80px rgba(255,255,255,.08); }
.consumer-shell.immersive-shell .purpose-gate::after { left: -28vw; bottom: -36vw; background: radial-gradient(circle, rgba(255,255,255,.09), transparent 68%); }
.consumer-shell.immersive-shell .purpose-card { position: relative; z-index: 1; width: min(620px, 100%) !important; padding: clamp(28px, 6vw, 54px) !important; border-radius: 38px !important; }
.consumer-shell.immersive-shell .purpose-card > p { max-width: 480px; font-size: 15px; }
.consumer-shell.immersive-shell .purpose-options { gap: 14px !important; margin-top: 26px; }
.consumer-shell.immersive-shell .purpose-options button { min-height: 132px; padding: 23px 64px 23px 22px !important; border-radius: 22px !important; }
.consumer-shell.immersive-shell .purpose-options b { font-size: 20px; }
@media (max-width: 560px) {
  .consumer-shell.immersive-shell .purpose-card { padding: 28px 22px !important; }
  .consumer-shell.immersive-shell .purpose-card h1 { font-size: 31px !important; }
  .consumer-shell.immersive-shell .purpose-options button { min-height: 118px; }
}
</style>

<style scoped>
.payment-order-card{margin-top:14px;padding:18px;border-radius:22px;background:linear-gradient(145deg,#f8fffc,#effaf6);border:1px solid #bbf7d0;text-align:center}.payment-order-head{display:flex;flex-direction:column;gap:4px;align-items:center;color:#047857}.payment-order-head span{font-size:11px;font-weight:900;letter-spacing:.1em}.payment-order-head b{font-size:16px}.payment-qr{display:block;width:min(220px,74vw);margin:14px auto;border-radius:14px;background:#fff;padding:10px;box-shadow:0 10px 22px rgba(6,78,59,.12)}.payment-order-card strong,.payment-order-card small{display:block}.payment-order-card strong{color:#17342d;font-size:17px}.payment-order-card small{margin-top:5px;color:#5f766e;font-size:11px}.copy-payment{margin-top:12px;padding:9px 14px;border:1px solid #99f6e4;border-radius:999px;background:#fff;color:#047857;font-weight:800}.manual-complete{margin-top:12px;width:100%;height:44px;border:0;border-radius:14px;background:#0f766e;color:#fff;font-weight:900;box-shadow:0 10px 20px rgba(15,118,110,.2)}.manual-complete:disabled{opacity:.55;cursor:wait}.payment-error{margin:12px 0 0;padding:10px 12px;border-radius:14px;background:#fef2f2;color:#b91c1c;font-size:12px;line-height:1.55}.packages button:disabled{opacity:.58;cursor:wait}
</style>

<style scoped>
.museum-location-select{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin:10px 0}.museum-location-select select,.museum-final-select select{width:100%;min-width:0}.museum-selection-tip{margin:9px 0 0;padding:9px 11px;border-radius:12px;background:#f0fdfa;color:#047857;font-size:12px;line-height:1.45;font-weight:700}.museum-final-select{grid-template-columns:1fr !important}@media(max-width:420px){.museum-location-select{grid-template-columns:1fr}.museum-location-select select{height:39px}}
</style>

<style scoped>
.purpose-back{margin:0 0 16px;padding:0;border:0;background:transparent;color:rgba(255,255,255,.82);font-weight:800}.purpose-museum-layout{display:grid;grid-template-columns:minmax(0,.85fr) minmax(0,1.15fr);gap:14px;margin-top:22px;align-items:stretch}.purpose-museum-select{display:grid;gap:10px}.purpose-museum-select label{display:grid;gap:6px;color:rgba(255,255,255,.82);font-size:12px;font-weight:800}.purpose-museum-select select{width:100%;height:50px;padding:0 14px;border:1px solid rgba(255,255,255,.22);border-radius:14px;background:rgba(255,255,255,.96);color:#2e211b;font:inherit}.purpose-museum-select-simple select:last-child{height:56px}.museum-recommendation{margin-top:14px;padding:14px;border-radius:15px;background:linear-gradient(135deg,#fff3e5,#fff);color:#41251c;box-shadow:0 10px 24px rgba(39,18,9,.12)}.museum-recommendation-head{display:flex;justify-content:space-between;gap:8px;align-items:center;font-size:12px;font-weight:900}.museum-recommendation-head span{color:#9a4b2f}.museum-recommendation-head b{padding:4px 8px;border-radius:99px;background:#f5d3b7;color:#7e3019;font-size:11px}.museum-recommendation-metrics{display:flex;flex-wrap:wrap;gap:7px;margin-top:11px}.museum-recommendation-metrics span{padding:5px 7px;border-radius:8px;background:#fff;color:#77584a;font-size:11px}.museum-recommendation-metrics strong{color:#512114}.museum-recommendation p{margin:8px 0;font-size:12px;line-height:1.55;font-weight:700}.museum-recommendation p strong{color:#a13f21}.museum-recommendation small{display:block;color:#96796c;font-size:10px;line-height:1.45}.purpose-confirm{width:100%;height:52px;margin-top:16px;border:0;border-radius:16px;background:#1d1714;color:#fff;font-size:15px;font-weight:900;box-shadow:0 12px 26px rgba(25,13,8,.24)}.purpose-confirm:disabled{opacity:.45;cursor:not-allowed}@media(max-width:620px){.purpose-museum-layout{grid-template-columns:1fr}.museum-recommendation{margin-top:0}}
</style>

<style scoped>
/* Premium purpose-routing experience */
.consumer-shell.immersive-shell .purpose-gate {
  overflow: auto;
  align-items: center;
  padding: clamp(18px, 5vw, 72px);
  background:
    radial-gradient(circle at 9% 14%, rgba(255,197,128,.28), transparent 25%),
    radial-gradient(circle at 90% 82%, rgba(226,110,61,.22), transparent 30%),
    linear-gradient(125deg, #120f16 0%, #24151a 38%, #713520 100%);
}
.consumer-shell.immersive-shell .purpose-gate::before {
  content:""; position:absolute; width:42vw; height:42vw; right:-15vw; top:-20vw; border-radius:50%;
  background:conic-gradient(from 30deg, rgba(255,202,139,.28), transparent 30%, rgba(255,120,69,.14), transparent 68%);
  filter:blur(2px); animation: purpose-orbit 18s linear infinite;
}
.consumer-shell.immersive-shell .purpose-gate::after {
  content:""; position:absolute; inset:0; pointer-events:none; opacity:.3;
  background-image:linear-gradient(rgba(255,255,255,.06) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.06) 1px,transparent 1px);
  background-size:42px 42px; mask-image:linear-gradient(to bottom,transparent,black 20%,black 80%,transparent);
}
.consumer-shell.immersive-shell .purpose-card {
  width:min(980px, 100%) !important; min-height:500px; padding:clamp(28px, 5vw, 62px) !important;
  border:1px solid rgba(255,255,255,.16) !important; border-radius:38px !important;
  background:linear-gradient(135deg,rgba(255,255,255,.16),rgba(255,255,255,.055)) !important;
  box-shadow:0 34px 90px rgba(8,5,8,.48), inset 0 1px rgba(255,255,255,.18) !important;
  backdrop-filter:blur(28px) saturate(135%);
}
.consumer-shell.immersive-shell .purpose-brand { margin-bottom:34px; gap:12px; }
.consumer-shell.immersive-shell .purpose-brand img { width:44px; height:44px; border-radius:14px; box-shadow:0 9px 24px rgba(0,0,0,.22); }
.consumer-shell.immersive-shell .purpose-brand > div { display:grid; gap:3px; }
.consumer-shell.immersive-shell .purpose-brand span { font-size:11px; letter-spacing:1.5px; }
.consumer-shell.immersive-shell .purpose-brand small { color:rgba(255,255,255,.62); font-size:11px; }
.consumer-shell.immersive-shell .purpose-brand em { margin-left:auto; color:rgba(255,255,255,.38); font-size:11px; font-style:normal; letter-spacing:2px; }
.consumer-shell.immersive-shell .purpose-step { display:flex; align-items:center; gap:9px; margin-bottom:16px; color:#ffcf9c; }
.consumer-shell.immersive-shell .purpose-step > span { display:grid; place-items:center; width:29px; height:29px; border-radius:50%; background:#ffb66f; color:#3d1c13; font-size:11px; font-weight:950; }
.consumer-shell.immersive-shell .purpose-step i { width:24px; height:1px; background:rgba(255,255,255,.35); }
.consumer-shell.immersive-shell .purpose-step b { font-size:12px; letter-spacing:.8px; }
.consumer-shell.immersive-shell .purpose-step small { margin-left:auto; color:rgba(255,255,255,.58); font-size:11px; }
.consumer-shell.immersive-shell .purpose-card h1 { max-width:none; margin:0 0 14px; font-size:clamp(34px,4vw,56px) !important; line-height:1.06; letter-spacing:-.065em; }
.consumer-shell.immersive-shell .purpose-card h1 strong { color:#ffd0a1; font-weight:800; }
.consumer-shell.immersive-shell .purpose-card > p { max-width:600px; margin-bottom:30px; color:rgba(255,255,255,.76); font-size:15px; }
.consumer-shell.immersive-shell .purpose-options { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:15px !important; margin-top:0; }
.consumer-shell.immersive-shell .purpose-options button { min-height:188px; padding:25px 54px 24px 25px !important; overflow:hidden; border:1px solid rgba(255,255,255,.65) !important; border-radius:24px !important; background:linear-gradient(145deg,#fffdf9,#f5e7d9) !important; box-shadow:0 15px 36px rgba(17,7,8,.22) !important; transition:transform .22s ease, box-shadow .22s ease; }
.consumer-shell.immersive-shell .purpose-options button:nth-child(2) { background:linear-gradient(145deg,#ffefe0,#f6c997) !important; }
.consumer-shell.immersive-shell .purpose-options button:hover { transform:translateY(-6px); box-shadow:0 22px 42px rgba(17,7,8,.32) !important; }
.consumer-shell.immersive-shell .purpose-options button::after { content:""; position:absolute; width:150px; height:150px; right:-48px; bottom:-68px; border-radius:50%; background:radial-gradient(circle,rgba(190,84,38,.22),transparent 69%); }
.consumer-shell.immersive-shell .purpose-options i { margin-bottom:16px; padding:6px 10px; background:#fff; box-shadow:0 4px 13px rgba(102,47,21,.1); }
.consumer-shell.immersive-shell .purpose-options b { font-size:23px; letter-spacing:-.04em; }
.consumer-shell.immersive-shell .purpose-options span { max-width:26ch; margin-top:9px; font-size:13px; line-height:1.65; }
.consumer-shell.immersive-shell .purpose-options em { position:absolute; right:23px; top:50%; transform:translateY(-50%); color:#9b4225; font-size:28px; font-style:normal; font-weight:300; }
.consumer-shell.immersive-shell .purpose-footnote { display:flex; align-items:center; gap:8px; margin-top:24px; color:rgba(255,255,255,.58); font-size:12px; }
.consumer-shell.immersive-shell .purpose-footnote span { color:#ffbe7b; }
.consumer-shell.immersive-shell .purpose-back { padding:8px 12px; border:1px solid rgba(255,255,255,.2); border-radius:999px; background:rgba(255,255,255,.08); transition:background .2s; }
.consumer-shell.immersive-shell .purpose-back:hover { background:rgba(255,255,255,.16); }
.consumer-shell.immersive-shell .purpose-museum-layout { grid-template-columns:minmax(280px,.85fr) minmax(330px,1.15fr); gap:20px; margin-top:30px; }
.consumer-shell.immersive-shell .purpose-museum-select { padding:18px; border:1px solid rgba(255,255,255,.14); border-radius:21px; background:rgba(11,8,12,.18); }
.consumer-shell.immersive-shell .purpose-museum-select label { gap:9px; color:#ffd5b0; letter-spacing:.4px; }
.consumer-shell.immersive-shell .purpose-museum-select select { height:56px; border:1px solid rgba(255,255,255,.5); border-radius:14px; background:#fffaf6; box-shadow:inset 0 1px #fff; }
.consumer-shell.immersive-shell .museum-recommendation { display:flex; flex-direction:column; justify-content:center; margin-top:0; min-height:164px; padding:21px; border:1px solid rgba(255,219,176,.7); border-radius:21px; background:linear-gradient(135deg,#fffdf8,#ffe8cf); box-shadow:0 18px 34px rgba(11,5,7,.18); }
.consumer-shell.immersive-shell .museum-recommendation-head { font-size:13px; }
.consumer-shell.immersive-shell .museum-recommendation-head b { padding:6px 10px; background:#572619; color:#ffe3c2; }
.consumer-shell.immersive-shell .museum-recommendation-metrics { gap:8px; margin-top:15px; }
.consumer-shell.immersive-shell .museum-recommendation-metrics span { padding:7px 9px; border:1px solid #f0d9c1; font-size:11px; }
.consumer-shell.immersive-shell .museum-recommendation p { font-size:12px; line-height:1.65; }
.consumer-shell.immersive-shell .purpose-confirm { height:58px; margin-top:22px; border:1px solid rgba(255,241,224,.55); border-radius:17px; background:linear-gradient(135deg,#ffd0a1,#e98a55); color:#3a1a11; box-shadow:0 16px 30px rgba(18,6,7,.25); transition:transform .2s; }
.consumer-shell.immersive-shell .purpose-confirm:not(:disabled):hover { transform:translateY(-2px); }
@keyframes purpose-orbit { to { transform:rotate(360deg); } }
@media(max-width:680px){
  .consumer-shell.immersive-shell .purpose-gate{align-items:flex-start;padding:16px;}
  .consumer-shell.immersive-shell .purpose-card{min-height:calc(100vh - 32px);padding:27px 22px !important;border-radius:27px !important;}
  .consumer-shell.immersive-shell .purpose-brand{margin-bottom:28px;}.consumer-shell.immersive-shell .purpose-brand em{display:none;}
  .consumer-shell.immersive-shell .purpose-card h1{font-size:36px !important;}.consumer-shell.immersive-shell .purpose-step small{display:none;}
  .consumer-shell.immersive-shell .purpose-options{grid-template-columns:1fr;}.consumer-shell.immersive-shell .purpose-options button{min-height:132px;padding:19px 50px 19px 19px !important;}
  .consumer-shell.immersive-shell .purpose-options b{font-size:19px;}.consumer-shell.immersive-shell .purpose-museum-layout{grid-template-columns:1fr;gap:13px;}
}
</style>

<style scoped>
/* Creative workbench visual refresh */
.consumer-shell.immersive-shell{background:linear-gradient(180deg,#f8f1eb 0,#fcfaf7 42%,#f3ebe5 100%)}
.consumer-shell.immersive-shell .hero{min-height:365px;padding:34px 30px 28px;border-radius:30px;background:radial-gradient(circle at 78% 14%,rgba(255,229,192,.28),transparent 25%),linear-gradient(132deg,#1c1720 0%,#4c251e 46%,#a84c2e 100%);box-shadow:0 25px 55px rgba(73,32,20,.22)}
.consumer-shell.immersive-shell .hero::before{content:'AI CULTURAL STUDIO';position:absolute;right:26px;bottom:26px;color:rgba(255,234,211,.15);font-size:10px;font-weight:900;letter-spacing:2.8px;writing-mode:vertical-rl}
.consumer-shell.immersive-shell .hero h1{max-width:11ch;margin-top:20px;font-size:42px !important;line-height:1.05 !important}.consumer-shell.immersive-shell .hero p{max-width:31ch;font-size:14px;line-height:1.7}.consumer-shell.immersive-shell .hero-actions{position:relative;z-index:3;display:flex;flex-wrap:wrap;gap:9px;margin-top:20px}.consumer-shell.immersive-shell .hero-actions button{min-height:44px;padding:0 16px;border-radius:13px;font-size:12px;font-weight:900;transition:transform .2s}.consumer-shell.immersive-shell .hero-actions button:hover{transform:translateY(-2px)}
.consumer-shell.immersive-shell .app-action-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin:16px 0}.consumer-shell.immersive-shell .app-action-grid button{position:relative;overflow:hidden;min-height:116px;padding:17px 14px;border:1px solid #f0dfd4;border-radius:22px;background:#fff;color:#2d1b15;text-align:left;box-shadow:0 11px 25px rgba(81,44,28,.08);transition:transform .2s,box-shadow .2s}.consumer-shell.immersive-shell .app-action-grid button:hover{transform:translateY(-4px);box-shadow:0 17px 30px rgba(81,44,28,.14)}.consumer-shell.immersive-shell .app-action-grid button::after{content:'';position:absolute;width:80px;height:80px;border-radius:50%;right:-32px;bottom:-36px;background:radial-gradient(circle,rgba(226,117,67,.22),transparent 70%)}.consumer-shell.immersive-shell .app-action-grid i{display:grid;place-items:center;width:35px;height:28px;margin-bottom:12px;border-radius:9px;font-size:10px;font-style:normal;font-weight:950;letter-spacing:.4px}.consumer-shell.immersive-shell .app-action-grid .orange{background:#fff0df;color:#bd542d}.consumer-shell.immersive-shell .app-action-grid .green{background:#e5f5ef;color:#177a58}.consumer-shell.immersive-shell .app-action-grid .dark{background:#efe9ff;color:#5940a0}.consumer-shell.immersive-shell .app-action-grid b,.consumer-shell.immersive-shell .app-action-grid span{display:block}.consumer-shell.immersive-shell .app-action-grid b{font-size:14px}.consumer-shell.immersive-shell .app-action-grid span{margin-top:5px;color:#987c6e;font-size:11px}
.consumer-shell.immersive-shell .flow-card{margin:20px 0;padding:18px;border:1px solid #eadbd0;border-radius:23px;background:#fffcf9;box-shadow:0 10px 28px rgba(83,44,26,.06)}.consumer-shell.immersive-shell .status-console{margin:16px 0;border:1px solid #eadbd0;border-radius:18px;background:linear-gradient(135deg,#fff,#f8ede6);box-shadow:none}.consumer-shell.immersive-shell .status-console button{border-radius:11px;background:#48231a;color:#fff}
.consumer-shell.immersive-shell .quick-tabs{position:sticky;top:10px;z-index:12;margin:20px 0 16px;padding:7px;border:1px solid rgba(233,216,204,.9);border-radius:18px;background:rgba(255,253,250,.82);box-shadow:0 12px 28px rgba(72,35,21,.08);backdrop-filter:blur(15px)}.consumer-shell.immersive-shell .quick-tabs button{min-height:48px;border-radius:13px;font-size:13px;font-weight:900}.consumer-shell.immersive-shell .quick-tabs button.active{background:linear-gradient(135deg,#4b241a,#9f4328);color:#fff;box-shadow:0 8px 18px rgba(92,39,23,.23)}
.consumer-shell.immersive-shell .creation-panel{padding:24px;border:1px solid #ecdcd1;border-radius:27px;background:linear-gradient(150deg,#fffdfb,#fff8f3);box-shadow:0 18px 42px rgba(88,47,28,.08)}.consumer-shell.immersive-shell .section-head{display:flex;align-items:flex-end;gap:10px;margin-bottom:18px}.consumer-shell.immersive-shell .section-head span{padding:6px 9px;border-radius:8px;background:#f8e6d8;color:#a44929;font-size:10px;font-weight:950;letter-spacing:1px}.consumer-shell.immersive-shell .section-head b{font-size:24px;letter-spacing:-.04em}.consumer-shell.immersive-shell .creation-guide{padding:12px 14px;border:1px solid #f0dfd4;border-radius:14px;background:#fff;color:#806356}.consumer-shell.immersive-shell .creation-panel label>span{font-size:13px;font-weight:900}.consumer-shell.immersive-shell .creation-panel textarea{margin-top:8px;border:1px solid #ead8cc;border-radius:18px;background:#fffdfb;box-shadow:inset 0 1px 2px rgba(91,43,20,.03);font-size:14px;line-height:1.65}.consumer-shell.immersive-shell .creation-panel textarea:focus{border-color:#d86a40;box-shadow:0 0 0 4px rgba(216,106,64,.12);outline:none}.consumer-shell.immersive-shell .preset-scroll button{border-radius:999px;border-color:#eadbd0;background:#fff9f4;color:#80513e}.consumer-shell.immersive-shell .chips button{border-radius:999px}.consumer-shell.immersive-shell .primary{height:58px;border-radius:17px;background:linear-gradient(135deg,#4b241a,#af4f2f);box-shadow:0 15px 26px rgba(138,58,33,.23);font-size:14px}.consumer-shell.immersive-shell .primary.green{background:linear-gradient(135deg,#133d34,#17815d);box-shadow:0 15px 26px rgba(23,129,93,.2)}.consumer-shell.immersive-shell .upload-box{border:1.5px dashed #d8a487;border-radius:21px;background:linear-gradient(135deg,#fff7f1,#fde5d5)}.consumer-shell.immersive-shell .simple-note{border-radius:13px;background:#eefaf4;color:#31765c}.consumer-shell.immersive-shell .result-card{border:1px solid #f0d8c6;border-radius:21px;background:#fff;box-shadow:0 13px 28px rgba(85,39,19,.09)}
.consumer-shell.immersive-shell .gallery-summary article{border-radius:18px;background:linear-gradient(145deg,#fff,#f9e9dc);box-shadow:0 9px 20px rgba(87,42,21,.06)}.consumer-shell.immersive-shell .gallery article{border:1px solid #efded3;border-radius:19px;background:#fff;box-shadow:0 10px 20px rgba(85,39,19,.07);overflow:hidden}.consumer-shell.immersive-shell .gallery article:hover{transform:translateY(-3px);transition:transform .2s}.consumer-shell.immersive-shell .gallery .model-tile{background:linear-gradient(135deg,#2b1d21,#9e4529)}
@media(max-width:620px){.consumer-shell.immersive-shell .hero{min-height:350px;padding:27px 21px}.consumer-shell.immersive-shell .hero h1{font-size:36px !important}.consumer-shell.immersive-shell .app-action-grid{gap:8px}.consumer-shell.immersive-shell .app-action-grid button{min-height:102px;padding:13px 10px}.consumer-shell.immersive-shell .app-action-grid b{font-size:12px}.consumer-shell.immersive-shell .app-action-grid span{font-size:10px}.consumer-shell.immersive-shell .creation-panel{padding:17px;border-radius:22px}.consumer-shell.immersive-shell .section-head b{font-size:21px}.consumer-shell.immersive-shell .quick-tabs{top:6px}}
</style>

<style scoped>
/* Rebuilt consumer home: creator dashboard */
.consumer-shell.immersive-shell .studio-home{display:grid;gap:20px;margin-bottom:22px}.consumer-shell.immersive-shell .studio-hero{position:relative;isolation:isolate;overflow:hidden;min-height:440px;padding:52px 56px;border-radius:34px;background:radial-gradient(circle at 80% 18%,rgba(255,216,165,.28),transparent 20%),linear-gradient(135deg,#16131c,#34201e 43%,#9c4128);box-shadow:0 28px 70px rgba(65,28,19,.25)}.consumer-shell.immersive-shell .studio-hero::before{content:"";position:absolute;inset:0;z-index:-1;background:linear-gradient(120deg,rgba(255,255,255,.06) 1px,transparent 1px),linear-gradient(30deg,rgba(255,255,255,.035) 1px,transparent 1px);background-size:46px 46px;mask-image:linear-gradient(90deg,black,transparent 72%)}.studio-hero-copy{position:relative;z-index:2;max-width:600px;color:#fff}.studio-kicker{display:flex;align-items:center;gap:8px;color:#ffd2a0;font-size:10px;font-weight:950;letter-spacing:2px}.studio-kicker span{font-size:15px}.studio-kicker i{margin-left:5px;color:rgba(255,255,255,.4);font-style:normal}.studio-purpose-pill{display:flex;align-items:center;gap:9px;margin-top:20px;padding:8px 11px;border:1px solid rgba(255,255,255,.17);border-radius:999px;background:rgba(255,255,255,.1);color:#fff;font-size:11px;font-weight:800}.studio-purpose-pill b{padding:3px 7px;border-radius:999px;background:rgba(255,220,184,.16);color:#ffd7ae;font-size:10px}.studio-hero h1{margin:25px 0 14px;font-size:clamp(40px,5.2vw,66px);line-height:1.02;letter-spacing:-.075em}.studio-hero h1 strong{color:#ffc995}.studio-hero p{max-width:500px;margin:0;color:rgba(255,255,255,.76);font-size:15px;line-height:1.75}.studio-hero-actions{display:flex;gap:12px;margin-top:30px}.studio-hero-actions button{height:50px;border-radius:14px;font-weight:900}.studio-main-action{padding:0 19px;border:0;background:linear-gradient(135deg,#ffd2a1,#ef8b56);color:#3f1d14;box-shadow:0 12px 26px rgba(17,6,8,.25)}.studio-main-action span{font-size:16px}.studio-sub-action{padding:0 5px;border:0;background:transparent;color:#fff}.studio-hero-art{position:absolute;right:6%;bottom:6%;z-index:1;width:330px;height:300px;transform:rotate(-7deg)}.art-ring{position:absolute;border:1px solid rgba(255,231,201,.23);border-radius:50%}.ring-one{inset:20px;transform:rotateX(65deg)}.ring-two{inset:55px 25px;transform:rotateX(65deg) rotate(42deg)}.art-tile{position:absolute;display:flex;flex-direction:column;justify-content:flex-end;padding:20px;border:1px solid rgba(255,255,255,.42);border-radius:27px;box-shadow:0 24px 40px rgba(22,7,8,.28);backdrop-filter:blur(12px)}.art-tile span{font-size:12px;font-weight:950;letter-spacing:1px}.art-tile b{margin-top:5px;font-size:15px}.tile-one{left:31px;top:34px;width:128px;height:150px;background:linear-gradient(145deg,#ffe1bf,#d35a38);color:#552014;transform:rotate(-16deg)}.tile-two{right:22px;bottom:30px;width:144px;height:165px;background:linear-gradient(145deg,rgba(255,255,255,.25),rgba(158,66,41,.38));color:#fff;transform:rotate(15deg)}.studio-hero-art em{position:absolute;right:4px;top:10px;color:#ffdbaf;font-size:39px;font-style:normal;text-shadow:0 8px 20px rgba(18,7,8,.4)}
.consumer-shell.immersive-shell .studio-launcher{padding:28px;border:1px solid #eadbd0;border-radius:29px;background:#fffdfb;box-shadow:0 16px 38px rgba(78,39,21,.07)}.studio-section-title{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:19px}.studio-section-title div{display:grid;gap:4px}.studio-section-title span{color:#ba5631;font-size:10px;font-weight:950;letter-spacing:1.8px}.studio-section-title b{font-size:25px;letter-spacing:-.05em}.studio-section-title small{color:#987a6a;font-size:12px}.studio-launch-grid{display:grid;grid-template-columns:1.18fr 1fr 1fr;gap:13px}.studio-launch-grid button{position:relative;min-height:220px;padding:22px;overflow:hidden;border:1px solid transparent;border-radius:23px;text-align:left;transition:transform .22s,box-shadow .22s}.studio-launch-grid button:hover{transform:translateY(-5px);box-shadow:0 20px 34px rgba(77,36,19,.15)}.studio-launch-grid button>i{position:absolute;right:17px;top:16px;color:rgba(68,28,18,.35);font-size:11px;font-style:normal;font-weight:950}.studio-launch-grid b,.studio-launch-grid small,.studio-launch-grid em,.studio-launch-grid strong{display:block;position:relative;z-index:1}.launch-icon{display:grid;place-items:center;width:44px;height:44px;border-radius:14px;font-size:20px}.studio-launch-grid b{margin-top:24px;font-size:20px;letter-spacing:-.05em}.studio-launch-grid small{max-width:23ch;margin-top:8px;font-size:12px;line-height:1.6}.studio-launch-grid em{margin-top:19px;font-size:11px;font-style:normal;font-weight:900}.studio-launch-grid strong{margin-top:9px;font-size:12px}.launch-image{background:linear-gradient(135deg,#ffe7cd,#f2b278);color:#542414}.launch-image .launch-icon{background:#fff7ef;color:#b64e2c}.launch-model{background:linear-gradient(145deg,#dff5ec,#9ed9c1);color:#173f33}.launch-model .launch-icon{background:#f1fff8;color:#197052}.launch-library{background:linear-gradient(145deg,#eee8ff,#c6b9ef);color:#31245e}.launch-library .launch-icon{background:#faf8ff;color:#5b45a8}
.consumer-shell.immersive-shell .studio-overview{display:grid;grid-template-columns:1fr 1.1fr 1.35fr;gap:13px}.studio-overview article{min-height:160px;padding:20px;border-radius:23px}.studio-overview span{display:block;font-size:10px;font-weight:950;letter-spacing:1.2px}.overview-balance{background:linear-gradient(145deg,#251820,#623022);color:#fff}.overview-balance span{color:#f4c496}.overview-balance b{display:block;margin-top:13px;font-size:42px;line-height:1}.overview-balance b small{margin-left:4px;font-size:13px}.overview-balance p{margin:10px 0;color:rgba(255,255,255,.63);font-size:11px;line-height:1.5}.overview-balance button,.overview-route button{border:0;background:transparent;color:inherit;font-size:11px;font-weight:900}.overview-route{border:1px solid #ead9ce;background:#fffaf6;color:#352018}.overview-route span{color:#aa4d2e}.overview-route b{display:block;margin-top:13px;font-size:18px}.overview-route p{margin:8px 0 12px;color:#8f7161;font-size:11px;line-height:1.5}.overview-route button{color:#a44728}.overview-pulse{border:1px solid #d9e9e0;background:linear-gradient(135deg,#f6fffa,#e6f7ef);color:#204839}.overview-pulse.live{border-color:#aee7cb;background:linear-gradient(135deg,#e2fff0,#c5f4de)}.overview-pulse span{color:#328065}.overview-pulse b{display:block;margin-top:13px;font-size:17px}.overview-pulse div{display:flex;align-items:flex-end;gap:5px;height:28px;margin:13px 0}.overview-pulse i{width:7px;border-radius:8px;background:#43b787}.overview-pulse i:nth-child(1){height:35%}.overview-pulse i:nth-child(2){height:70%}.overview-pulse i:nth-child(3){height:100%}.overview-pulse i:nth-child(4){height:58%}.overview-pulse i:nth-child(5){height:84%}.overview-pulse small{display:block;color:#62867a;font-size:11px;line-height:1.5}
@media(max-width:780px){.consumer-shell.immersive-shell .studio-hero{min-height:430px;padding:31px 24px}.studio-hero h1{font-size:40px}.studio-hero-art{right:-22px;bottom:-15px;transform:rotate(-7deg) scale(.72);transform-origin:bottom right}.studio-hero-copy{max-width:75%}.studio-launcher{padding:20px !important}.studio-launch-grid{grid-template-columns:1fr 1fr}.studio-launch-grid button:first-child{grid-column:span 2}.studio-overview{grid-template-columns:1fr 1fr !important}.overview-pulse{grid-column:span 2}}
@media(max-width:480px){.studio-hero-copy{max-width:78%}.studio-hero h1{font-size:35px}.studio-hero p{font-size:12px}.studio-hero-actions{flex-direction:column;align-items:flex-start;gap:6px}.studio-hero-actions button{height:43px}.studio-section-title small{display:none}.studio-launch-grid{grid-template-columns:1fr}.studio-launch-grid button:first-child{grid-column:auto}.studio-launch-grid button{min-height:145px}.studio-launch-grid b{margin-top:15px}.studio-launch-grid em{margin-top:12px}.studio-overview{grid-template-columns:1fr !important}.overview-pulse{grid-column:auto}.consumer-shell.immersive-shell .quick-tabs{margin-top:2px}}
</style>

<style scoped>
/* Accessibility fix: high-contrast museum recommendation card */
.consumer-shell.immersive-shell .purpose-card .museum-recommendation{
  background:#fffaf5 !important;
  color:#2d1a13 !important;
  border:1px solid #f3c99f !important;
}
.consumer-shell.immersive-shell .purpose-card .museum-recommendation *{color:inherit;}
.consumer-shell.immersive-shell .purpose-card .museum-recommendation-head span{color:#9a3e22 !important;}
.consumer-shell.immersive-shell .purpose-card .museum-recommendation-head b{background:#542419 !important;color:#ffe8cf !important;}
.consumer-shell.immersive-shell .purpose-card .museum-recommendation-metrics span{background:#f7e7d8 !important;border:1px solid #ebc4a1 !important;color:#603224 !important;}
.consumer-shell.immersive-shell .purpose-card .museum-recommendation-metrics strong{color:#9e3e20 !important;}
.consumer-shell.immersive-shell .purpose-card .museum-recommendation p{color:#452419 !important;}
.consumer-shell.immersive-shell .purpose-card .museum-recommendation p strong{color:#b04423 !important;}
.consumer-shell.immersive-shell .purpose-card .museum-recommendation small{color:#795d50 !important;}
</style>

<style scoped>
/* Product image + 3D generation UI rebuild */
.consumer-shell.immersive-shell .creation-redesign{padding:0 !important;overflow:hidden;border:0 !important;background:#fffdfb !important;box-shadow:0 20px 48px rgba(78,39,21,.1) !important}.creation-spotlight{display:flex;justify-content:space-between;gap:28px;padding:34px 36px 28px;background:linear-gradient(125deg,#281a19,#633121 57%,#bd5a34);color:#fff}.model-redesign .creation-spotlight{background:linear-gradient(125deg,#102c28,#235b49 57%,#3b9b75)}.creation-spotlight>div{max-width:610px}.creation-spotlight span{color:#ffd2a0;font-size:10px;font-weight:950;letter-spacing:1.8px}.model-redesign .creation-spotlight>div>span{color:#bdeedb}.creation-spotlight h2{margin:12px 0 9px;font-size:31px;line-height:1.12;letter-spacing:-.06em}.creation-spotlight h2 strong{color:#ffcc98}.model-redesign .creation-spotlight h2 strong{color:#bcefd9}.creation-spotlight p{margin:0;color:rgba(255,255,255,.72);font-size:13px;line-height:1.65}.creation-spotlight aside{display:flex;flex-direction:column;align-items:center;justify-content:center;min-width:118px;padding:14px;border:1px solid rgba(255,255,255,.18);border-radius:19px;background:rgba(255,255,255,.1);text-align:center}.creation-spotlight aside i{font-size:34px;font-style:normal;font-weight:950;line-height:1}.creation-spotlight aside span{margin-top:6px;color:#fff;font-size:10px;letter-spacing:0}.creation-spotlight aside small{margin-top:4px;color:rgba(255,255,255,.62);font-size:9px}.creation-workspace{display:grid;grid-template-columns:minmax(0,1.4fr) minmax(250px,.8fr);gap:18px;padding:28px 36px}.prompt-studio{padding:18px;border:1px solid #eeded3;border-radius:20px;background:#fff}.prompt-studio-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:11px}.prompt-studio-head span{font-size:14px;font-weight:950}.prompt-studio-head small{color:#a28677;font-size:11px}.prompt-studio textarea,.model-prompt-pane textarea{box-sizing:border-box;width:100%;border:1px solid #ead8cc;border-radius:15px;background:#fffcfa;padding:15px;color:#382218;font:inherit;font-size:14px;line-height:1.7;resize:vertical}.prompt-studio textarea:focus,.model-prompt-pane textarea:focus{outline:0;border-color:#c75a33;box-shadow:0 0 0 4px rgba(199,90,51,.1)}.inspiration-row{margin-top:15px}.inspiration-row>b{display:block;margin-bottom:8px;color:#855c4b;font-size:11px}.inspiration-row .preset-scroll{margin:0}.inspiration-row .preset-scroll button{background:#fff5ec;color:#82492f}.creation-controls{display:grid;align-content:start;gap:13px}.control-block{padding:15px;border:1px solid #eeded3;border-radius:18px;background:#fff}.control-block>span{display:block;margin-bottom:10px;font-size:12px;font-weight:950}.choice-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.choice-grid button{min-height:48px;border:1px solid #ecd9cc;border-radius:12px;background:#fffaf6;color:#775746;font-size:11px;font-weight:800}.choice-grid button.active{border-color:#bd4d29;background:#4a2319;color:#fff;box-shadow:0 6px 14px rgba(105,43,24,.2)}.choice-grid small{font-size:9px;font-weight:600}.creation-tip{display:flex;gap:8px;padding:12px 13px;border-radius:15px;background:#fff0e2;color:#7d4d37;font-size:11px;line-height:1.55}.creation-tip i{color:#c9512c;font-style:normal;font-size:15px}.creation-submit{display:flex;align-items:center;justify-content:center;gap:9px;width:calc(100% - 72px);height:62px;margin:0 36px 30px;border:0;border-radius:17px;color:#fff;font-size:15px;box-shadow:0 15px 28px rgba(102,40,22,.24)}.creation-submit span{font-size:20px}.creation-submit em{margin-left:8px;padding:4px 8px;border-radius:99px;background:rgba(255,255,255,.15);font-size:11px;font-style:normal}.image-submit{background:linear-gradient(135deg,#4d241a,#b6512f)}.model-submit{background:linear-gradient(135deg,#153d34,#16805b);box-shadow:0 15px 28px rgba(15,111,78,.22)}.creation-submit:disabled{opacity:.5}.generation-stage{display:flex;align-items:center;gap:14px;margin:0 36px 28px;padding:16px;border:1px solid #f2ceb5;border-radius:18px;background:#fff8f2}.generation-stage b,.generation-stage span{display:block}.generation-stage b{font-size:13px}.generation-stage span{margin-top:4px;color:#896b5c;font-size:11px}.model-stage{border-color:#bde7d3;background:#f2fcf7}.stage-orbit{position:relative;width:42px;height:42px;flex:0 0 auto}.stage-orbit i{position:absolute;inset:0;border:2px solid transparent;border-top-color:#c3512d;border-radius:50%;animation:spin 1.15s linear infinite}.stage-orbit i:nth-child(2){inset:6px;border-top-color:#e7a16f;animation-direction:reverse}.stage-orbit i:nth-child(3){inset:12px;border-top-color:#432319}.model-stage .stage-orbit i{border-top-color:#278265}.model-progress-line{align-self:flex-end;flex:1;max-width:160px;height:5px;border-radius:99px;background:#dcefe7;overflow:hidden}.model-progress-line span{display:block;height:100%;margin:0;background:#24916d;border-radius:inherit}.model-mode-switch{display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:24px 36px 0}.model-mode-switch button{display:grid;gap:5px;padding:16px;border:1px solid #d9e9e1;border-radius:17px;background:#f7fcf9;text-align:left;color:#527267}.model-mode-switch button b{font-size:14px}.model-mode-switch button span{font-size:11px}.model-mode-switch button.active{border-color:#218260;background:linear-gradient(135deg,#19473b,#278564);color:#fff;box-shadow:0 10px 22px rgba(19,95,67,.2)}.model-workspace{display:grid;grid-template-columns:minmax(0,1.25fr) minmax(230px,.75fr);gap:18px;padding:20px 36px 28px}.model-upload-pane,.model-prompt-pane{min-width:0}.redesign-upload{display:flex;min-height:258px;border:1.5px dashed #7ec4a9 !important;border-radius:22px !important;background:linear-gradient(135deg,#f3fff9,#e1f5ec) !important}.redesign-upload>span{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:7px;color:#2c6b57}.redesign-upload>span i{display:grid;place-items:center;width:48px;height:48px;border-radius:15px;background:#1c7659;color:#fff;font-size:28px;font-style:normal}.redesign-upload>span b{font-size:15px}.redesign-upload>span small{font-size:11px}.redesign-upload img{width:100%;height:258px;object-fit:contain}.model-note{display:grid;gap:4px;margin-top:12px;padding:13px;border-radius:14px;background:#f3faf6;color:#4a7565}.model-note b{font-size:12px}.model-note span{font-size:11px;line-height:1.55}.model-prompt-pane{padding:18px;border:1px solid #dceae4;border-radius:20px;background:#fff}.model-guidance{display:flex;flex-direction:column;padding:18px;border-radius:20px;background:linear-gradient(160deg,#173a32,#2e7e61);color:#fff}.model-guidance>span{color:#bdebd9;font-size:10px;font-weight:950;letter-spacing:1.4px}.model-guidance>b{margin-top:10px;font-size:18px}.model-guidance ul{display:grid;gap:8px;margin:15px 0;padding:0;list-style:none}.model-guidance li{color:rgba(255,255,255,.75);font-size:11px;line-height:1.5}.model-guidance li::before{content:'✓';margin-right:6px;color:#9ae4c6}.model-guidance>div{display:flex;align-items:center;gap:10px;margin-top:auto;padding-top:13px;border-top:1px solid rgba(255,255,255,.15)}.model-guidance i{display:grid;place-items:center;width:35px;height:35px;border-radius:11px;background:rgba(255,255,255,.14);font-style:normal;font-size:11px;font-weight:950}.model-guidance p{margin:0;color:rgba(255,255,255,.65);font-size:10px;line-height:1.5}.redesigned-result{display:grid;grid-template-columns:200px 1fr;overflow:hidden;margin:0 36px 34px !important;border:1px solid #efd6c4 !important;border-radius:21px !important;background:#fff}.result-image-wrap{position:relative;background:#f5e6da}.result-image-wrap img{display:block;width:100%;height:100%;min-height:170px;object-fit:cover}.result-image-wrap span{position:absolute;left:12px;top:12px;padding:5px 7px;border-radius:7px;background:rgba(39,20,14,.68);color:#fff;font-size:9px;font-weight:950;letter-spacing:1px}.model-result{background:linear-gradient(135deg,#183c32,#398567)}.redesigned-result .result-info{padding:22px}.redesigned-result .result-info>span{display:block;color:#b64d2b;font-size:10px;font-weight:950;letter-spacing:1.2px}.redesigned-result .result-info>b{display:block;margin-top:7px;font-size:20px}.redesigned-result .result-info p{color:#826758;font-size:12px;line-height:1.6}.redesigned-result .result-actions{margin-top:15px}
@keyframes spin{to{transform:rotate(360deg)}}
@media(max-width:760px){.creation-spotlight{padding:25px 21px;gap:14px}.creation-spotlight h2{font-size:25px}.creation-spotlight aside{min-width:84px;padding:10px}.creation-spotlight aside i{font-size:27px}.creation-workspace,.model-workspace{grid-template-columns:1fr;padding:18px}.creation-controls{grid-template-columns:1fr 1fr}.creation-submit{width:calc(100% - 36px);margin:0 18px 22px}.generation-stage{margin:0 18px 20px}.model-mode-switch{padding:18px 18px 0}.model-guidance{min-height:175px}.redesigned-result{grid-template-columns:1fr;margin:0 18px 22px !important}.result-image-wrap img{max-height:240px}.redesigned-result .result-info{padding:18px}}
@media(max-width:460px){.creation-spotlight{display:block}.creation-spotlight aside{display:none}.creation-controls{grid-template-columns:1fr}.choice-grid{gap:5px}.choice-grid button{font-size:10px}.model-mode-switch{grid-template-columns:1fr}.creation-spotlight h2{font-size:23px}.redesign-upload{min-height:210px}.redesign-upload img{height:210px}}
</style>

<style scoped>
.consumer-shell.immersive-shell .model-mode-switch.three-modes{grid-template-columns:repeat(3,1fr)}
.multiview-pane{padding:18px;border:1px solid #dceae4;border-radius:20px;background:#fbfffd}.multiview-head{display:flex;align-items:flex-end;justify-content:space-between;gap:12px;margin-bottom:15px}.multiview-head div{display:grid;gap:4px}.multiview-head span{color:#2c8b68;font-size:10px;font-weight:950;letter-spacing:1.3px}.multiview-head b{font-size:17px;letter-spacing:-.04em}.multiview-head>small{color:#678478;font-size:10px}.multiview-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:9px}.multiview-slot{position:relative;display:flex;min-height:130px;flex-direction:column;align-items:center;justify-content:center;gap:6px;overflow:hidden;border:1.5px dashed #a8d8c4;border-radius:16px;background:linear-gradient(145deg,#f4fff9,#e6f7ef);color:#46806a;text-align:center;cursor:pointer}.multiview-slot:first-child{border-color:#2f9a73;background:linear-gradient(145deg,#e6fff3,#d0f1e0)}.multiview-slot.ready{border-style:solid;background:#173e34}.multiview-slot input{position:absolute;inset:0;z-index:4;opacity:0;cursor:pointer}.multiview-slot img{position:absolute;inset:0;width:100%;height:100%;object-fit:cover}.multiview-slot i{display:grid;place-items:center;width:32px;height:32px;border-radius:10px;background:#fff;color:#237a5c;font-size:12px;font-style:normal;font-weight:950}.multiview-slot b{font-size:12px}.multiview-slot small{font-size:9px}.multiview-slot em{position:absolute;right:7px;top:7px;z-index:3;padding:4px 6px;border-radius:7px;background:#157457;color:#fff;font-size:9px;font-style:normal;font-weight:900}.multiview-note{margin-top:13px}.model-redesign .model-guidance{min-height:258px}
@media(max-width:760px){.consumer-shell.immersive-shell .model-mode-switch.three-modes{grid-template-columns:1fr}.multiview-grid{grid-template-columns:repeat(2,1fr)}.multiview-slot{min-height:135px}.multiview-head{display:block}.multiview-head>small{display:block;margin-top:5px}}
</style>

<style scoped>
.doubao-reference-upload{display:grid;gap:9px;padding:11px;border:1px solid #cfd9f5;border-radius:15px;background:linear-gradient(135deg,#f5f7ff,#edf2ff)}.doubao-reference-upload>div{display:grid;gap:3px}.doubao-reference-upload>div span{color:#4b67b6;font-size:9px;font-weight:950;letter-spacing:.9px}.doubao-reference-upload>div b{color:#33466f;font-size:11px}.doubao-reference-upload>div small{color:#75839f;font-size:9px;line-height:1.45}.doubao-reference-upload label{position:relative;display:flex;min-height:112px;align-items:center;justify-content:center;overflow:hidden;border:1.5px dashed #9aafe5;border-radius:12px;background:#fff;color:#5b73ae;text-align:center}.doubao-reference-upload label.ready{border-style:solid;border-color:#4d6ec4}.doubao-reference-upload input{position:absolute;inset:0;z-index:3;opacity:0;cursor:pointer}.doubao-reference-upload img{position:absolute;inset:0;width:100%;height:100%;object-fit:contain;background:#f9fbff}.doubao-reference-upload label>template,.doubao-reference-upload label>i,.doubao-reference-upload label>b,.doubao-reference-upload label>small{position:relative;z-index:1}.doubao-reference-upload label i{display:grid;place-items:center;width:29px;height:29px;margin-right:7px;border-radius:9px;background:#5973bd;color:#fff;font-size:19px;font-style:normal}.doubao-reference-upload label b{font-size:10px}.doubao-reference-upload label small{margin-left:5px;font-size:8px}.doubao-reference-upload label em{position:absolute;right:7px;top:7px;z-index:4;padding:4px 6px;border-radius:7px;background:#4967b3;color:#fff;font-size:8px;font-style:normal;font-weight:900}
.choice-grid.two{grid-template-columns:1fr 1.55fr}.choice-grid .doubao-choice{line-height:1.3}.choice-grid .doubao-choice small{font-size:8px}.choice-grid .doubao-choice.active{background:linear-gradient(135deg,#253d78,#4469c5);border-color:#4469c5}.doubao-multiview-result{margin:0 36px 28px;padding:17px;border:1px solid #cfd9f5;border-radius:22px;background:linear-gradient(135deg,#f4f7ff,#fbfcff);box-shadow:0 14px 30px rgba(47,83,164,.1)}.doubao-result-head{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:14px}.doubao-result-head>div{display:grid;gap:4px}.doubao-result-head span{color:#4966b3;font-size:9px;font-weight:950;letter-spacing:1.1px}.doubao-result-head b{color:#293957;font-size:17px}.doubao-result-head small{color:#71809b;font-size:10px}.doubao-result-head button{padding:11px 13px;border:0;border-radius:12px;background:linear-gradient(135deg,#314c93,#5c78d4);color:#fff;font-size:10px;font-weight:900;white-space:nowrap}.doubao-view-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:9px}.doubao-view-grid article{overflow:hidden;border:1px solid #dce4f7;border-radius:13px;background:#fff}.doubao-view-grid img{display:block;width:100%;aspect-ratio:1;object-fit:cover}.doubao-view-grid b{display:block;padding:7px 8px;color:#405474;font-size:10px;text-align:center}@media(max-width:760px){.choice-grid.two{grid-template-columns:1fr}.doubao-multiview-result{margin:0 18px 22px;padding:13px}.doubao-result-head{align-items:flex-start;flex-direction:column}.doubao-result-head button{width:100%}.doubao-view-grid{grid-template-columns:repeat(2,1fr)}}
.image-template-showcase{margin:0 0 13px;padding:11px;border:1px solid #ead8c9;border-radius:18px;background:linear-gradient(135deg,#fffaf5,#fff2e8)}.image-template-title{display:flex;align-items:end;justify-content:space-between;gap:10px;margin-bottom:9px}.image-template-title>div{display:grid;gap:3px}.image-template-title span{color:#b55b38;font-size:9px;font-weight:950;letter-spacing:1.2px}.image-template-title b{color:#4a3025;font-size:13px}.image-template-title>small{color:#9a7c6e;font-size:9px}.image-template-card{display:grid;grid-template-columns:116px 1fr;gap:11px;width:100%;overflow:hidden;padding:0;border:1px solid #efd9c8;border-radius:13px;background:#fff;text-align:left;box-shadow:0 8px 20px rgba(108,68,43,.08);transition:.2s}.image-template-card:hover{transform:translateY(-2px);border-color:#d68a62;box-shadow:0 13px 25px rgba(108,68,43,.15)}.image-template-card img{display:block;width:116px;height:92px;object-fit:cover}.image-template-card>div{display:grid;align-content:center;gap:4px;padding:8px 9px 8px 0}.image-template-card span{color:#b55b38;font-size:9px;font-weight:850;letter-spacing:.4px}.image-template-card b{color:#42281e;font-size:14px}.image-template-card small{color:#916f60;font-size:10px}.image-template-card em{margin-left:4px;color:#ae4e2a;font-size:13px;font-style:normal;font-weight:950}@media(max-width:460px){.image-template-title>small{display:none}.image-template-card{grid-template-columns:100px 1fr}.image-template-card img{width:100px;height:88px}}
.model-template-showcase{margin:0 0 12px;padding:11px;border:1px solid #cde5db;border-radius:17px;background:linear-gradient(135deg,#f2fff8,#edf8f3)}.model-showcase-title{display:flex;align-items:end;justify-content:space-between;gap:9px;margin-bottom:9px}.model-showcase-title>div{display:grid;gap:3px}.model-showcase-title span{color:#258161;font-size:9px;font-weight:950;letter-spacing:1.2px}.model-showcase-title b{color:#264e40;font-size:13px}.model-showcase-title>small{color:#789287;font-size:9px}.model-showcase-card{display:grid;grid-template-columns:116px 1fr;gap:11px;width:100%;overflow:hidden;padding:0;border:1px solid #cce5d9;border-radius:13px;background:#fff;text-align:left;box-shadow:0 8px 20px rgba(27,105,75,.1);transition:.2s}.model-showcase-card:hover{transform:translateY(-2px);border-color:#2d9b71;box-shadow:0 13px 26px rgba(27,105,75,.16)}.model-showcase-card img{display:block;width:116px;height:92px;object-fit:cover;background:#143b31}.model-showcase-card>div{display:grid;align-content:center;gap:4px;padding:8px 9px 8px 0}.model-showcase-card span{color:#258161;font-size:9px;font-weight:850;letter-spacing:.4px}.model-showcase-card b{color:#24493c;font-size:14px}.model-showcase-card small{color:#6d8c80;font-size:10px}.model-showcase-card em{margin-left:4px;color:#167456;font-size:13px;font-style:normal;font-weight:950}@media(max-width:460px){.model-showcase-title>small{display:none}.model-showcase-card{grid-template-columns:100px 1fr}.model-showcase-card img{width:100px;height:88px}}
.model-template-picker{display:grid;gap:9px;margin:0 0 12px;padding:12px;border:1px solid #d7e6df;border-radius:16px;background:linear-gradient(135deg,#f8fffb,#eff8f3)}.model-template-picker>div:first-child{display:flex;align-items:baseline;justify-content:space-between;gap:10px}.model-template-picker span{color:#25775e;font-size:10px;font-weight:950;letter-spacing:1px}.model-template-picker small{color:#789087;font-size:9px}.model-template-picker>div:last-child{display:grid;grid-template-columns:repeat(5,1fr);gap:7px}.model-template-picker button{display:grid;gap:3px;padding:9px;border:1px solid #cfe4da;border-radius:11px;background:#fff;color:#416b5c;text-align:left}.model-template-picker button b{font-size:10px}.model-template-picker button small{font-size:8px;line-height:1.25}.model-template-picker button.active{border-color:#1e8060;background:#1d7458;color:#fff;box-shadow:0 8px 16px rgba(23,110,79,.18)}.model-template-picker button.active small{color:rgba(255,255,255,.72)}.plush-prompt-tip,.ppc-prompt-tip{display:grid;gap:4px;margin-top:11px;padding:10px 12px;border-radius:12px;background:linear-gradient(135deg,#fff0ea,#fff8ee);border:1px solid #f1d6c8}.plush-prompt-tip b{color:#a54d32;font-size:10px}.plush-prompt-tip span,.ppc-prompt-tip span{color:#89675a;font-size:9px;line-height:1.5}.ppc-prompt-tip{background:linear-gradient(135deg,#edf2f3,#f8fbfb);border-color:#d4e0e1}.ppc-prompt-tip b{color:#42646a;font-size:10px}@media(max-width:760px){.model-template-picker>div:last-child{grid-template-columns:1fr 1fr}}
.material-picker{display:flex;align-items:center;justify-content:space-between;gap:22px;padding:18px 36px;border-top:1px solid #dceae4;border-bottom:1px solid #dceae4;background:linear-gradient(90deg,#f7fffb,#eef8f3)}.material-picker>div:first-child{display:grid;gap:4px;max-width:340px}.material-picker span{color:#258161;font-size:10px;font-weight:950;letter-spacing:1.3px}.material-picker b{font-size:15px;color:#214d40}.material-picker small{color:#6d8a7e;font-size:10px;line-height:1.5}.material-chips{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:7px}.material-chips button{padding:8px 10px;border:1px solid #cfe5da;border-radius:999px;background:#fff;color:#4f7566;font-size:11px;font-weight:800}.material-chips button.active{border-color:#1f805e;background:#1d7458;color:#fff;box-shadow:0 7px 14px rgba(23,110,79,.18)}@media(max-width:760px){.material-picker{display:grid;padding:17px 18px;gap:13px}.material-chips{justify-content:flex-start}}
</style>

<style scoped>
.market-discovery{margin:18px 2px;padding:18px;border-radius:26px;background:linear-gradient(145deg,#181416,#2a211d 55%,#0f766e);color:#fff;box-shadow:0 18px 44px rgba(39,29,25,.22)}
.discovery-heading span,.product-brief-title span{display:block;font-size:10px;letter-spacing:.13em;color:#7ee9d9;font-weight:800}.discovery-heading b{display:block;font-size:20px;margin:6px 0}.discovery-heading small{display:block;line-height:1.55;color:rgba(255,255,255,.62);font-size:11px}
.channel-card-row{display:grid;gap:10px;margin-top:15px}.channel-card{display:flex;gap:12px;padding:13px;border-radius:18px;background:rgba(255,255,255,.09);border:1px solid rgba(255,255,255,.12)}.channel-mark{display:grid;place-items:center;width:43px;height:43px;border-radius:14px;background:linear-gradient(135deg,#f8d99e,#ca7c50);color:#28180f;font-size:19px;font-weight:900}.channel-card>div:last-child{min-width:0;flex:1}.channel-card span{font-size:10px;color:#a7f3d0}.channel-card b{display:block;font-size:14px;margin:2px 0}.channel-card p{margin:3px 0 7px;color:rgba(255,255,255,.77);font-size:11px;line-height:1.45}.channel-card em{font-style:normal;font-size:10px;padding:3px 6px;margin-right:5px;border-radius:99px;background:rgba(250,204,21,.16);color:#fde68a}.channel-card small{display:block;margin-top:7px;color:#b7efe5;font-size:10px;line-height:1.45}
.proof-board{display:grid;gap:10px;margin-top:12px}.proof-cases,.proof-ranking{padding:13px;border-radius:18px;background:#fff;color:#1c1917}.proof-cases>span,.proof-ranking span{display:block;margin-bottom:8px;color:#0f766e;font-size:10px;font-weight:800;letter-spacing:.09em}.proof-cases article{display:grid;grid-template-columns:23px 1fr auto;align-items:center;gap:7px;padding:8px 0;border-top:1px solid #eee7df}.proof-cases i{display:grid;place-items:center;width:22px;height:22px;border-radius:7px;background:#fdf1df;color:#d97706;font-size:11px}.proof-cases b{display:block;font-size:12px}.proof-cases small{display:block;margin-top:2px;color:#78716c;font-size:10px;line-height:1.35}.proof-cases em,.proof-ranking em{font-size:10px;color:#0f766e;font-style:normal;font-weight:800}.proof-ranking>div{display:flex;align-items:center;gap:4px;flex-wrap:wrap}.proof-ranking>div span{margin:0 auto 0 0}.proof-ranking button{border:0;border-radius:8px;padding:4px 7px;background:#f3f0eb;color:#78716c;font-size:10px}.proof-ranking button.active{background:#181416;color:#fff}.proof-ranking ol{list-style:none;padding:0;margin:8px 0 0}.proof-ranking li{display:grid;grid-template-columns:30px 1fr auto;gap:8px;align-items:center;padding:8px 0;border-top:1px solid #eee7df}.proof-ranking li>b{color:#d97706;font-size:12px}.proof-ranking li>span{font-size:12px}
.product-brief{display:grid;gap:13px;margin:14px 0;padding:15px;border:1px solid rgba(15,118,110,.18);border-radius:20px;background:linear-gradient(135deg,#f2fffb,#fffaf0)}.product-brief-title b{display:block;color:#1c1917;font-size:15px;margin:4px 0}.product-brief-title small{display:block;color:#78716c;font-size:11px;line-height:1.45}.brief-selectors{display:grid;gap:9px}.brief-selectors>div{display:flex;flex-wrap:wrap;gap:6px;align-items:center}.brief-selectors span{width:100%;font-size:11px;color:#57534e;font-weight:800}.brief-selectors button{border:1px solid #ded7ce;background:#fff;border-radius:10px;padding:7px 9px;color:#57534e;font-size:11px;font-weight:700}.brief-selectors button.active{background:#124e4a;color:#fff;border-color:#124e4a;box-shadow:0 5px 13px rgba(15,118,110,.2)}.product-brief aside{padding:11px;border-radius:14px;background:#173e3a;color:#fff}.product-brief aside b{font-size:12px}.product-brief aside p{margin:5px 0;color:#d2f5e8;font-size:11px;line-height:1.5}.product-brief aside small{color:#8dd9c7;font-size:10px;line-height:1.4}
.feasibility-card{display:grid;gap:7px;margin:14px 0;padding:14px;border:1px solid #b7e4db;border-radius:18px;background:#f0fdf8;color:#134e4a}.feasibility-card>div{display:flex;align-items:center;gap:8px}.feasibility-card span{font-size:10px;font-weight:800;letter-spacing:.08em}.feasibility-card b{font-size:13px}.feasibility-card em{margin-left:auto;color:#0f766e;font-style:normal;font-weight:900}.feasibility-card p{margin:0;font-size:11px;line-height:1.5}.feasibility-card small{font-size:10px;line-height:1.45;color:#17635a}.feasibility-card button{justify-self:start;border:0;border-radius:9px;padding:7px 10px;background:#0f766e;color:#fff;font-size:11px}.feasibility-card i{font-size:9px;font-style:normal;line-height:1.45;color:#6b7280}
.rights-button{background:#fff7ed!important;color:#9a3412!important;border-color:#fed7aa!important}.rights-modal{position:fixed;z-index:100;inset:0;display:grid;place-items:center;padding:20px;background:rgba(15,10,7,.62);backdrop-filter:blur(8px)}.rights-modal>div{position:relative;width:min(420px,100%);display:grid;gap:11px;padding:22px;border-radius:24px;background:#fffaf3;color:#292524;box-shadow:0 25px 80px rgba(0,0,0,.28)}.rights-modal span{font-size:10px;letter-spacing:.12em;font-weight:900;color:#0f766e}.rights-modal h3{margin:0;font-size:21px}.rights-modal p,.rights-modal small{margin:0;color:#78716c;font-size:12px;line-height:1.6}.rights-modal label{padding:9px 10px;border-radius:11px;background:#f5f1eb;font-size:12px}.rights-modal label input{accent-color:#0f766e}.rights-close{position:absolute;right:12px;top:10px;border:0;background:transparent;color:#78716c;font-size:24px}.rights-submit{border:0;border-radius:12px;padding:12px;background:linear-gradient(135deg,#0f766e,#0c9488);color:#fff;font-weight:800}
@media(min-width:720px){.channel-card-row{grid-template-columns:repeat(3,1fr)}.proof-board{grid-template-columns:1.15fr .85fr}.product-brief{grid-template-columns:1.05fr 1fr}.product-brief aside{align-self:stretch}}
</style>

<style scoped>.image-edit-upload{display:grid;gap:10px;margin:12px 0;padding:13px;border:1px solid #f1cfb4;border-radius:18px;background:linear-gradient(135deg,#fff7ef,#fffdf9)}.image-edit-upload>div span,.image-edit-upload>div small{display:block;color:#a05a34;font-size:10px;line-height:1.45}.image-edit-upload>div b{display:block;margin:3px 0;color:#5a2a18;font-size:13px}.image-edit-upload label{position:relative;display:grid;place-items:center;min-height:138px;overflow:hidden;border:1.5px dashed #d98a5c;border-radius:15px;background:#fff;text-align:center;color:#9a4d2a}.image-edit-upload label input{position:absolute;inset:0;opacity:0;cursor:pointer}.image-edit-upload label img{width:100%;height:160px;object-fit:contain}.image-edit-upload label>template,.image-edit-upload label>span{display:grid}.image-edit-upload label i{display:grid;place-items:center;width:34px;height:34px;margin:auto;border-radius:10px;background:#b4512d;color:#fff;font-size:21px;font-style:normal}.image-edit-upload label b,.image-edit-upload label small{display:block;margin-top:5px;font-size:11px}.image-edit-upload label em{position:absolute;right:8px;top:8px;padding:3px 6px;border-radius:99px;background:#0f766e;color:#fff;font-size:9px;font-style:normal}</style>
