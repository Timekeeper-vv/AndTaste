<template>
  <view class="page">
    <view class="ink-wash wash-one" /><view class="ink-wash wash-two" />
    <view class="head">
      <text class="eyebrow">AI CULTURAL CREATION</text>
      <text class="title">{{ activeMode.title }}</text>
      <text class="sub">{{ activeMode.description }}</text>
      <view class="mode-rail"><view v-for="item in modeOptions" :key="item.key" class="mode-tab" :class="{ active: mode === item.key }" @tap="selectMode(item.key)"><text>{{ item.mark }}</text><text>{{ item.short }}</text></view></view>
    </view>
    <AiGeneratedNotice class="ai-disclosure" description="提交后生成的图片、四视图和 3D 原型均为人工智能生成内容。请在展示、销售、打样或生产前完成人工复核、版权核验和工艺确认。" />

    <view class="intention-card">
      <view class="intention-top"><view><text>CREATIVE INTENTION</text><text>{{ selectedProductCategory.label }} · {{ form.material }}</text></view><text>创作参数已同步</text></view>
      <view class="object-preview" :style="{ '--tone': selectedPattern.tone }"><view><text>{{ selectedPattern.mark }}</text></view><text>{{ selectedPattern.name }}</text><text>{{ form.material }} · {{ finishSummary }}</text></view>
      <text>这是创意意向预览；真实图片和 3D 结果将在生成完成后保存到作品库。</text>
    </view>

    <view class="card creation-card">
      <view class="field-group"><text class="label">作品名称</text><input v-model.trim="form.title" placeholder="给作品起一个名字（可选）" class="input" /></view>
      <view class="field-group"><view class="prompt-label-row"><text class="label">创作描述</text><button v-if="canOptimizeImagePrompt" class="prompt-optimize" :loading="imagePromptOptimizing" :disabled="imagePromptOptimizing" @tap="optimizeCurrentImagePrompt">{{ imagePromptOptimizing ? '优化中' : 'AI 优化描述' }}</button></view><textarea v-model="form.prompt" placeholder="描述主题、材质、纹样、色彩与使用场景…" class="textarea" maxlength="800" /><view class="word-count"><text>{{ imagePromptGuide || '会自动带入纹样与材质方向' }}</text><text>{{ form.prompt.length }}/800</text></view></view>

      <view class="direction-panel">
        <view class="panel-head"><view><text>HERITAGE PATTERN</text><text>传统纹样灵感</text></view><text>点击加入描述</text></view>
        <scroll-view scroll-x class="pattern-scroll" :show-scrollbar="false"><view class="pattern-row"><view v-for="pattern in patterns" :key="pattern.id" class="pattern-chip" :class="{ active: selectedPatternId === pattern.id }" :style="{ '--tone': pattern.tone }" @tap="applyPattern(pattern)"><text>{{ pattern.mark }}</text><view><text>{{ pattern.name }}</text><text>{{ pattern.category }}</text></view></view></view></scroll-view>
      </view>

      <view class="direction-panel product-panel">
        <view class="panel-head"><view><text>PRODUCT BLUEPRINT</text><text>选择产品类别</text></view><text>先定品类，再定工艺</text></view>
        <scroll-view scroll-x class="product-scroll" :show-scrollbar="false"><view class="product-row"><view v-for="product in productCategories" :key="product.key" class="product-chip" :class="{ active: selectedProductKey === product.key }" @tap="chooseProductCategory(product.key)"><text>{{ product.mark }}</text><view><text>{{ product.label }}</text><text>{{ product.short }}</text></view></view></view></scroll-view>
        <view class="product-brief"><text>{{ selectedProductCategory.description }}</text><text>{{ selectedTemplateLabel }}</text></view>
      </view>

      <view class="direction-panel finish-panel">
        <view class="panel-head"><view><text>MATERIAL & FINISH</text><text>材质与表面效果</text></view><text>{{ materialPanelHint }}</text></view>
        <view class="material-meta"><view><text>{{ selectedProductCategory.label }} · {{ showAllMaterials ? '全部制造材质' : '量产推荐材质' }}</text><text>材质会同步到图片、3D 提示词和生产可行性初筛</text></view><button class="material-toggle" size="mini" @tap="showAllMaterials = !showAllMaterials">{{ showAllMaterials ? '只看推荐' : `全部 ${materialList.length} 种` }}</button></view>
        <text v-if="showAllMaterials" class="material-scope-tip">全部材质均可选择；“推荐”表示与当前 {{ selectedProductCategory.label }} 的量产工艺更匹配，其他材质仍可用于概念验证与进一步工艺确认。</text>
        <view class="material-row"><view v-for="material in visibleMaterials" :key="material.key" class="material-chip" :class="{ active: form.material === material.name }" @tap="chooseMaterial(material)"><text class="material-swatch" :style="{ background: material.swatch }" /><view><text>{{ material.name }}</text><text>{{ material.short }}</text></view><text v-if="isRecommendedForSelectedProduct(material)" class="material-recommended">推荐</text><text v-else class="material-cross">可选</text></view></view>
        <view class="slider-group"><view v-for="control in finishControls" :key="control.key" class="slider-row"><view><text>{{ control.label }}</text><text>{{ finish[control.key] }}%</text></view><slider :value="finish[control.key]" min="0" max="100" activeColor="#6e8b7c" backgroundColor="#e6dfd4" block-color="#6e8b7c" block-size="16" @change="changeFinish(control.key, $event)" /></view></view>
      </view>

      <view v-if="is3dMode" class="feasibility-panel">
        <view class="feasibility-head"><view><text>PRODUCTION READINESS</text><text>生产可行性初筛</text></view><button class="assessment-action" :loading="assessmentLoading" :disabled="assessmentLoading || !form.prompt.trim()" @tap="refreshProductionAssessment()">{{ assessmentLoading ? '评估中' : '更新初筛' }}</button></view>
        <text class="feasibility-intro">依据 {{ selectedProductCategory.label }} · {{ form.material }} 和创作描述，提前识别打样风险；不会替代工厂的正式工艺确认。</text>
        <view v-if="productionAssessment" class="assessment-result" :class="assessmentLevelClass"><view class="assessment-score"><view><text>{{ productionAssessment.level }}</text><text>本次工艺方向</text></view><text>{{ productionAssessment.score }}</text></view><text v-if="productionAssessment.productRules" class="product-rule-tip">已锁定{{ selectedProductCategory.label }}生产规则：{{ productionAssessment.productRules }}</text><view v-if="assessmentIssues.length" class="assessment-list"><text>需要关注</text><text v-for="(issue, index) in assessmentIssues" :key="`issue-${index}`">{{ issue }}</text></view><view v-if="assessmentSuggestions.length" class="assessment-list suggestions"><text>建议处理</text><text v-for="(suggestion, index) in assessmentSuggestions" :key="`suggestion-${index}`">{{ suggestion }}</text></view><text v-if="optimized3dTip" class="assessment-tip">3D 建模建议：{{ optimized3dTip }}</text></view>
        <view v-else class="assessment-empty"><text>先写好创作描述，即可查看当前产品与材质的量产风险提示。</text><text>系统会在提交 3D 前自动再做一次初筛。</text></view>
      </view>

      <view v-if="needsReference" class="upload-panel" :class="{ selected: referencePath }">
        <view><text>{{ referencePanel.eyebrow }}</text><text>{{ referencePanel.description }}</text></view>
        <button class="secondary" @tap="pickImage"><text>{{ referencePath ? '已选择参考图片' : '选择一张参考图片' }}</text><text>{{ referencePath ? '更换 ›' : '上传 ›' }}</text></button>
      </view>

      <view v-if="isMultiViewMode" class="multiview-workbench">
        <view class="multiview-head">
          <view><text>{{ multiViewSource === 'seedream' ? 'SEEDREAM · TURNAROUND' : 'MULTI-VIEW · CAPTURE' }}</text><text>{{ multiViewSource === 'seedream' ? '四个一致视角' : '上传你的多角度图片' }}</text></view>
          <text class="multiview-state" :class="{ ready: canSubmitMultiView }">{{ multiViewSource === 'seedream' ? (hasCompleteMultiView ? '4 / 4 已就绪' : '等待生成') : manualMultiViewProgress }}</text>
        </view>
        <view class="multiview-source-switch">
          <button :class="{ active: multiViewSource === 'seedream' }" :disabled="loading" @tap="setMultiViewSource('seedream')"><text>AI 补全四视图</text><text>1 张图 → 4 个一致视角</text></button>
          <button :class="{ active: multiViewSource === 'manual' }" :disabled="loading" @tap="setMultiViewSource('manual')"><text>手动上传多视图</text><text>正面必传，至少再传 1 个角度</text></button>
        </view>

        <template v-if="multiViewSource === 'seedream'">
          <text class="multiview-intro">由同一张参考图派生正、左、背、右四视图；只有服务端真实返回并存入作品库的图片，才能进入下一步建模。</text>
          <view class="turnaround-flow">
            <view v-for="slot in multiViewSlots" :key="slot.key" class="turnaround-step" :class="{ ready: multiViewAssetIdByView[slot.key] }"><text>{{ slot.short }}</text><text>{{ slot.label }}</text></view>
          </view>
          <view class="quality-row"><text>输出精度</text><view><button v-for="size in multiViewSizes" :key="size" class="quality-choice" :class="{ active: multiViewSize === size }" :disabled="loading" @tap="setMultiViewSize(size)">{{ size }}</button></view></view>

          <view v-if="multiViewError" class="multiview-error"><text>本次请求未完成</text><text>{{ multiViewError }}</text></view>

          <view v-if="orderedMultiViewImages.length" class="multiview-result">
            <view class="view-grid">
              <view v-for="item in orderedMultiViewImages" :key="`${item.view}-${item.assetId}`" class="view-card" @tap="previewMultiView(item)">
                <image v-if="multiViewImageSrc(item)" :src="multiViewImageSrc(item)" mode="aspectFill" />
                <view v-else class="view-image-fallback"><text>{{ item.label || viewLabel(item.view) }}</text><text>已存入作品库</text></view>
                <view class="view-card-meta"><text>{{ item.label || viewLabel(item.view) }}</text><text>已保存</text></view>
              </view>
            </view>
            <view class="multiview-delivery"><view><text>四视图已保存</text><text>{{ multiViewMessage || '可直接提交给 Tripo 创建 3D 原型。' }}</text></view><text>✓</text></view>
            <view class="multiview-credit-note"><text>积分说明</text><text>生成四视图阶段不预扣平台积分；提交 3D 时后端会按当前规则预扣 {{ imageTo3dCreditLabel }}，若提交失败会自动退回。</text></view>
            <button class="model-submit" :loading="loading && loadingAction === 'model'" :disabled="loading || !hasCompleteMultiView" @tap="submitMultiViewModel"><text>3D</text>{{ multiViewModelButtonLabel }}</button>
          </view>
          <view v-else class="multiview-empty"><view class="turntable-orb"><text>3D</text></view><view><text>先生成可用的四视图</text><text>选择一张主体清晰的参考图，再提交真实的 Seedream 多视图生成请求。</text></view></view>
        </template>

        <template v-else>
          <text class="multiview-intro">已有正面、侧面或背面产品图时，可直接上传给 Tripo。正面图必传，至少再上传一个角度；四个角度都会进一步提升厚度与背部结构的完整度。</text>
          <view class="manual-view-grid">
            <view v-for="slot in multiViewSlots" :key="slot.key" class="manual-view-slot" :class="{ ready: manualMultiViewAssets[slot.key].assetId, uploading: manualMultiViewUploading === slot.key }" @tap="pickManualMultiView(slot.key)">
              <image v-if="manualMultiViewAssets[slot.key].localPath" :src="manualMultiViewAssets[slot.key].localPath" mode="aspectFill" />
              <view v-else class="manual-view-empty"><text>{{ slot.short }}</text><text>{{ slot.label }}图</text><text>{{ slot.key === 'front' ? '必传' : '建议上传' }}</text></view>
              <view class="manual-view-meta"><text>{{ slot.label }}</text><text>{{ manualMultiViewUploading === slot.key ? '上传中…' : manualMultiViewAssets[slot.key].assetId ? '已存入作品库' : '点击上传' }}</text></view>
            </view>
          </view>
          <view class="multiview-credit-note"><text>积分说明</text><text>上传图片不会扣积分；提交多视图 3D 时才会按当前规则预扣 {{ imageTo3dCreditLabel }}，若提交失败会自动退回。</text></view>
          <button class="model-submit" :loading="loading && loadingAction === 'model'" :disabled="loading || !canSubmitMultiView" @tap="submitMultiViewModel"><text>3D</text>{{ manualMultiViewModelButtonLabel }}</button>
        </template>
      </view>

      <view class="notice"><text>创作提醒</text><text>{{ activeMode.notice }}</text></view>
      <button class="generate" :loading="loading" :disabled="loading" @tap="generate"><text v-if="loading" class="seal-loader">制</text><text v-else>✦</text>{{ generateButtonLabel }}</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import AiGeneratedNotice from '../../components/AiGeneratedNotice.vue'
import {
  assessProductionFeasibility,
  createModel,
  createSeedreamMultiView,
  getCreditRules,
  optimizeImagePrompt,
  optimizeTripo3dPrompt,
  type ProductionFeasibilityResult,
  type SeedreamMultiViewImage,
  type Tripo3dPromptTemplate,
  uploadReference,
} from '../../api/creative'
import { apiUrl, createReferenceToImage, createTextToImage } from '../../api/client'
import { confirmCreativePolicy } from '../../utils/compliance'
import {
  findMaterialDefinition,
  isRecommendedMaterial,
  materialCatalog,
  materialList,
  productCategories,
  type MaterialDefinition,
  type ProductCategoryKey,
} from '../../config/materials'
import { requireSession } from '../../utils/session'

type CreateMode = 'image' | 'reference' | 'text3d' | 'image3d' | 'multiview'
type FinishKey = 'glaze' | 'texture' | 'relief'
type MultiViewKey = 'front' | 'left' | 'back' | 'right'
type MultiViewSource = 'seedream' | 'manual'
type LoadingAction = 'creation' | 'multiview' | 'model'

interface MultiViewSlot {
  key: MultiViewKey
  label: string
  short: string
}

interface ManualMultiViewAsset {
  assetId: number | null
  localPath: string
}

const mode = ref<CreateMode>('image')
const loading = ref(false)
const loadingAction = ref<LoadingAction>('creation')
const referencePath = ref('')
const referenceAssetId = ref<number | null>(null)
const selectedPatternId = ref('taotie')
const selectedProductKey = ref<ProductCategoryKey>('magnet')
const showAllMaterials = ref(false)
const form = reactive({ title: '', prompt: '', material: 'PVC', modelMaterial: 'PVC 搪胶软胶' })
const finish = reactive({ glaze: 72, texture: 42, relief: 36 })
const multiViewSource = ref<MultiViewSource>('seedream')
const multiViewSize = ref<'1K' | '2K'>('2K')
const multiViewImages = ref<SeedreamMultiViewImage[]>([])
const manualMultiViewAssets = reactive<Record<MultiViewKey, ManualMultiViewAsset>>({
  front: { assetId: null, localPath: '' },
  left: { assetId: null, localPath: '' },
  back: { assetId: null, localPath: '' },
  right: { assetId: null, localPath: '' },
})
const manualMultiViewUploading = ref<MultiViewKey | ''>('')
const multiViewMessage = ref('')
const multiViewError = ref('')
const imageTo3dCredit = ref(70)
const productionAssessment = ref<ProductionFeasibilityResult | null>(null)
const assessmentLoading = ref(false)
const optimized3dTip = ref('')
const imagePromptOptimizing = ref(false)
const imagePromptGuide = ref('')
const referencePolicyConfirmed = ref(false)
const aiPolicyConfirmed = ref(false)
const threeDimensionalPolicyConfirmed = ref(false)

const multiViewSlots: MultiViewSlot[] = [
  { key: 'front', label: '正面', short: '正' },
  { key: 'left', label: '左侧', short: '左' },
  { key: 'back', label: '背面', short: '背' },
  { key: 'right', label: '右侧', short: '右' },
]
const multiViewSizes: Array<'1K' | '2K'> = ['1K', '2K']

const modeOptions: Array<{ key: CreateMode; mark: string; short: string; title: string; description: string; notice: string; cost: number }> = [
  { key: 'image', mark: '墨', short: '灵感生图', title: '让灵感，先成为一张产品图。', description: '把文化、材质、纹样和使用场景交给 AI，生成后会自动进入作品库。', notice: '文字生成图片会把你的纹样、材质和表面效果方向一并交给 AI。', cost: 16 },
  { key: 'reference', mark: '鉴', short: '参考图改造', title: '保留原有特征，重构文化语言。', description: '上传草图、普通产品图或灵感图，用文创设计重新组织它的材质与场景。', notice: '请使用你拥有版权或已获得授权的参考图片；生成结果会保留在你的作品库。', cost: 16 },
  { key: 'text3d', mark: '形', short: '文字 3D', title: '把一段描述，推向立体原型。', description: '清楚描述主体、材质和结构，生成后可在作品库发起 3D 安全预览。', notice: '3D 生成完成后，请先提交审核；审核通过的模型才能申请打样或生产。', cost: 60 },
  { key: 'image3d', mark: '立', short: '图片 3D', title: '从参考图，生成可预览的原型。', description: '上传主体清晰的图像，系统会生成可进入作品库继续推进的三维模型。', notice: '请尽量使用主体完整、背景干净的图片，以便获得更准确的 3D 结构；材质偏好会随本次作品工艺方向一并记录。', cost: 70 },
  { key: 'multiview', mark: '观', short: '多视图 3D', title: '从一张图，补全一件作品的四面。', description: '先由 Doubao Seedream 生成一致的正、左、背、右视图，再一键交给 Tripo 创建 3D 原型。', notice: '四视图阶段会真实调用火山 Seedream 并保存图片；平台积分只会在下一步提交 Tripo 3D 任务时按当前规则预扣。材质偏好会同步为本次工艺方向记录。', cost: 0 },
]
const patterns = [
  { id: 'taotie', name: '饕餮回纹', category: '青铜纹样', prompt: '简化饕餮回纹，适合文创产品边缘与局部浮雕装饰', tone: '#6d8476', mark: '饕' },
  { id: 'cloud', name: '如意云纹', category: '吉祥纹样', prompt: '灵动如意云纹，以留白和连续曲线构成现代东方装饰', tone: '#b66f59', mark: '云' },
  { id: 'brocade', name: '团花锦纹', category: '织绣纹样', prompt: '精简团花锦纹，以现代比例呈现精致织锦节奏', tone: '#b89557', mark: '锦' },
  { id: 'mountain', name: '青绿山水', category: '山水意境', prompt: '青绿山水的层叠远近关系，保留宣纸般呼吸感', tone: '#789993', mark: '山' },
  { id: 'window', name: '花窗几何', category: '建筑纹样', prompt: '传统花窗几何结构，以现代简化比例呈现秩序感', tone: '#887567', mark: '窗' },
]
const finishControls: Array<{ key: FinishKey; label: string }> = [{ key: 'glaze', label: '釉面光泽' }, { key: 'texture', label: '肌理颗粒' }, { key: 'relief', label: '浮雕层次' }]
const modelTemplateLabels: Record<Tripo3dPromptTemplate, string> = {
  universal: '通用产品建模',
  collectible: '潮玩收藏建模',
  oriental: '国风器物建模',
  plush_toy: '毛绒玩具建模',
  ppc_precision: 'PPC 精密硬塑建模',
}

const activeMode = computed(() => modeOptions.find(item => item.key === mode.value) || modeOptions[0])
const selectedPattern = computed(() => patterns.find(item => item.id === selectedPatternId.value) || patterns[0])
const selectedProductCategory = computed(() => productCategories.find(item => item.key === selectedProductKey.value) || productCategories[0])
const recommendedMaterials = computed<MaterialDefinition[]>(() => selectedProductCategory.value.materialKeys.map(key => materialCatalog[key]))
const visibleMaterials = computed<MaterialDefinition[]>(() => showAllMaterials.value ? materialList : recommendedMaterials.value)
const selectedMaterial = computed<MaterialDefinition>(() => materialList.find(item => item.name === form.material || item.modelLabel === form.modelMaterial)
  || materialCatalog.pvc)
const needsReference = computed(() => mode.value === 'reference'
  || mode.value === 'image3d'
  || (mode.value === 'multiview' && multiViewSource.value === 'seedream'))
const isMultiViewMode = computed(() => mode.value === 'multiview')
const is3dMode = computed(() => mode.value === 'text3d' || mode.value === 'image3d' || mode.value === 'multiview')
const canOptimizeImagePrompt = computed(() => mode.value === 'image' || mode.value === 'reference')
const finishSummary = computed(() => `${finish.glaze}% 光泽`)
const selectedModelTemplate = computed<Tripo3dPromptTemplate>(() => {
  const material = selectedMaterial.value.key
  if (material === 'ppc' || material === 'abs') return 'ppc_precision'
  if (material === 'plush' || material === 'short_plush' || material === 'ultra_plush') return 'plush_toy'
  if (material === 'pvc' || material === 'tangjiao' || material === 'soft_vinyl' || material === 'resin') return 'collectible'
  return selectedProductCategory.value.defaultTemplate || 'oriental'
})
const selectedTemplateLabel = computed(() => modelTemplateLabels[selectedModelTemplate.value])
const materialPanelHint = computed(() => {
  if (showAllMaterials.value) return `全部 ${materialList.length} 种可选`
  if (mode.value === 'text3d') return '带入 3D 提示词'
  if (mode.value === 'image' || mode.value === 'reference') return '带入生成描述'
  return '记录工艺方向'
})
const assessmentIssues = computed(() => Array.isArray(productionAssessment.value?.issues) ? productionAssessment.value!.issues : [])
const assessmentSuggestions = computed(() => Array.isArray(productionAssessment.value?.suggestions) ? productionAssessment.value!.suggestions : [])
const assessmentLevelClass = computed(() => {
  const level = String(productionAssessment.value?.level || '')
  if (level.includes('可进入')) return 'ready'
  if (level.includes('复核')) return 'review'
  return 'revise'
})
const referencePanel = computed(() => {
  if (mode.value === 'reference') return { eyebrow: 'REFERENCE REMIX', description: '上传产品、草图或灵感图，AI 会保留主体特征后进行文创改造。' }
  if (mode.value === 'multiview') return { eyebrow: 'MULTIVIEW SOURCE', description: '上传一张主体完整的产品图。Seedream 会以它为唯一依据，补全正、左、背、右四个一致视角。' }
  return { eyebrow: 'IMAGE TO 3D', description: '上传主体清晰的产品图，生成可继续预览与打样的 3D 原型。' }
})
const multiViewAssetIdByView = computed<Record<MultiViewKey, number | null>>(() => {
  const ids: Record<MultiViewKey, number | null> = { front: null, left: null, back: null, right: null }
  multiViewSlots.forEach((slot) => {
    const item = multiViewImages.value.find(image => image.view === slot.key)
    const assetId = Number(item?.assetId)
    if (Number.isFinite(assetId) && assetId > 0) ids[slot.key] = assetId
  })
  return ids
})
const orderedMultiViewImages = computed<SeedreamMultiViewImage[]>(() => multiViewSlots.reduce<SeedreamMultiViewImage[]>((images, slot) => {
  const image = multiViewImages.value.find(item => item.view === slot.key)
  if (image) images.push(image)
  return images
}, []))
const hasCompleteMultiView = computed(() => multiViewSlots.every(slot => Boolean(multiViewAssetIdByView.value[slot.key])))
const manualMultiViewAssetIdByView = computed<Record<MultiViewKey, number | null>>(() => ({
  front: manualMultiViewAssets.front.assetId,
  left: manualMultiViewAssets.left.assetId,
  back: manualMultiViewAssets.back.assetId,
  right: manualMultiViewAssets.right.assetId,
}))
const manualMultiViewCount = computed(() => multiViewSlots.filter(slot => Boolean(manualMultiViewAssetIdByView.value[slot.key])).length)
const hasMinimumManualMultiView = computed(() => Boolean(manualMultiViewAssetIdByView.value.front) && manualMultiViewCount.value >= 2)
const canSubmitMultiView = computed(() => multiViewSource.value === 'seedream' ? hasCompleteMultiView.value : hasMinimumManualMultiView.value)
const manualMultiViewProgress = computed(() => `${manualMultiViewCount.value} / 4 已上传`)
const imageTo3dCreditLabel = computed(() => `${formatCredit(imageTo3dCredit.value)} 积分`)
const multiViewModelButtonLabel = computed(() => `用 4 视图创建 3D（预扣 ${imageTo3dCreditLabel.value}）`)
const manualMultiViewModelButtonLabel = computed(() => `用已上传视图创建 3D（预扣 ${imageTo3dCreditLabel.value}）`)
const generateButtonLabel = computed(() => {
  if (loading.value) {
    if (loadingAction.value === 'multiview') return '正在调用 Seedream 生成四个视图…'
    if (loadingAction.value === 'model') return '正在提交多视图 3D 任务…'
    return '正在生成，请稍候…'
  }
  if (isMultiViewMode.value) {
    if (multiViewSource.value === 'manual') return canSubmitMultiView.value ? manualMultiViewModelButtonLabel.value : '先上传正面图与另一个角度'
    return hasCompleteMultiView.value ? '重新生成四个一致视图' : '生成四个一致视图'
  }
  return `开始生成（${activeMode.value.cost} 积分）`
})

function selectMode(next: CreateMode) { mode.value = next; imagePromptGuide.value = '' }
function applyPattern(pattern: typeof patterns[number]) {
  selectedPatternId.value = pattern.id
  if (!form.prompt.includes(pattern.prompt)) form.prompt = `${form.prompt.replace(/[，,。；;\s]+$/, '')}，${pattern.prompt}`
}
function chooseProductCategory(key: ProductCategoryKey) {
  if (selectedProductKey.value === key) return
  selectedProductKey.value = key
  const firstMaterial = materialCatalog[selectedProductCategory.value.materialKeys[0]]
  form.material = firstMaterial.name
  form.modelMaterial = firstMaterial.modelLabel
  productionAssessment.value = null
  optimized3dTip.value = ''
}

function resolveMaterialDefinition(materialName?: string, modelMaterial?: string) {
  const legacyNames: Record<string, string> = {
    '青铜金属': '金属',
    '木质温润': '木质',
    '玉感树脂': '树脂',
  }
  return findMaterialDefinition(materialName, modelMaterial)
    || findMaterialDefinition(materialName ? legacyNames[materialName] : '', modelMaterial)
}

function restoreProductMaterial(materialName?: string, modelMaterial?: string) {
  const material = resolveMaterialDefinition(materialName, modelMaterial)
  if (!material) return
  if (!selectedProductCategory.value.materialKeys.includes(material.key)) {
    const matchingProduct = productCategories.find(item => item.materialKeys.includes(material.key))
    if (matchingProduct) selectedProductKey.value = matchingProduct.key
    else showAllMaterials.value = true
  }
  form.material = material.name
  form.modelMaterial = material.modelLabel
}

function chooseMaterial(material: MaterialDefinition) {
  form.material = material.name
  form.modelMaterial = material.modelLabel
  productionAssessment.value = null
  optimized3dTip.value = ''
}
function isRecommendedForSelectedProduct(material: MaterialDefinition) {
  return isRecommendedMaterial(selectedProductCategory.value, material)
}
function changeFinish(key: FinishKey, event: any) { finish[key] = Number(event.detail.value) || 0 }
function clearMultiViewResult() {
  multiViewImages.value = []
  multiViewMessage.value = ''
  multiViewError.value = ''
}
function clearManualMultiViewAssets() {
  multiViewSlots.forEach((slot) => {
    manualMultiViewAssets[slot.key].assetId = null
    manualMultiViewAssets[slot.key].localPath = ''
  })
}
function setMultiViewSource(source: MultiViewSource) {
  if (multiViewSource.value === source || loading.value) return
  multiViewSource.value = source
  if (source === 'seedream') {
    clearManualMultiViewAssets()
  } else {
    clearMultiViewResult()
  }
}
async function pickImage() {
  if (!referencePolicyConfirmed.value && !(await confirmCreativePolicy('reference-materials'))) return
  referencePolicyConfirmed.value = true
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: (result) => {
      const selectedPath = result.tempFilePaths?.[0]
      if (!selectedPath) return
      const replacesCompleteViews = hasCompleteMultiView.value
      referencePath.value = selectedPath
      referenceAssetId.value = null
      clearMultiViewResult()
      if (replacesCompleteViews) uni.showToast({ title: '参考图已更新，请重新生成四视图', icon: 'none' })
    },
  })
}
async function pickManualMultiView(view: MultiViewKey) {
  if (loading.value || manualMultiViewUploading.value) return
  if (!referencePolicyConfirmed.value && !(await confirmCreativePolicy('reference-materials'))) return
  referencePolicyConfirmed.value = true
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: (result) => {
      const localPath = result.tempFilePaths?.[0]
      if (!localPath) return
      void uploadManualMultiView(view, localPath)
    },
  })
}
async function uploadManualMultiView(view: MultiViewKey, localPath: string) {
  if (!requireSession()) return
  manualMultiViewUploading.value = view
  try {
    const result = await uploadReference(localPath)
    const assetId = Number(result?.assetId)
    if (!Number.isFinite(assetId) || assetId <= 0) throw new Error('图片上传成功但未返回作品编号，请重试')
    manualMultiViewAssets[view].assetId = assetId
    manualMultiViewAssets[view].localPath = localPath
    uni.showToast({ title: `${viewLabel(view)}图已上传`, icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || `${viewLabel(view)}图上传失败`, icon: 'none' })
  } finally {
    manualMultiViewUploading.value = ''
  }
}
function setMultiViewSize(size: '1K' | '2K') {
  if (multiViewSize.value === size) return
  const replacesCompleteViews = hasCompleteMultiView.value
  multiViewSize.value = size
  clearMultiViewResult()
  if (replacesCompleteViews) uni.showToast({ title: '已更新精度，请重新生成四视图', icon: 'none' })
}
function buildPrompt() {
  const direction = `产品类别：${selectedProductCategory.value.label}，制造材质：${form.material}，视觉表面：${selectedMaterial.value.modelLabel}，釉面光泽 ${finish.glaze}%，肌理颗粒 ${finish.texture}%，浮雕层次 ${finish.relief}%`
  const source = form.prompt
    .replace(/(?:，|,)?材质表现：[^。；;]*(?:[。；;]|$)/g, '')
    .replace(/(?:，|,)?产品类别：[^。；;]*(?:[。；;]|$)/g, '')
    .replace(/[，,。；;\s]+$/, '')
  return `${source}，${direction}`
}
function modelMaterialPrompt() {
  return `${selectedMaterial.value.modelPrompt}; manufacturing material: ${form.material}; finish direction: ${finish.glaze}% gloss, ${finish.texture}% texture grain, ${finish.relief}% relief depth`
}
function formatCredit(value: number) { return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(2))) }
function viewLabel(view: string) { return multiViewSlots.find(slot => slot.key === view)?.label || '视图' }
function multiViewImageSrc(item: SeedreamMultiViewImage) {
  const raw = String(item.previewUrl || item.imageUrl || item.fileUrl || '')
  if (/^https?:\/\//i.test(raw)) return raw
  return raw.startsWith('/') ? apiUrl(raw) : ''
}
function previewMultiView(item: SeedreamMultiViewImage) {
  const current = multiViewImageSrc(item)
  const urls = orderedMultiViewImages.value.map(multiViewImageSrc).filter(Boolean)
  if (current && urls.length) uni.previewImage({ current, urls })
}
async function ensureReferenceAsset() {
  if (referenceAssetId.value) return referenceAssetId.value
  if (!referencePath.value) throw new Error('请先选择一张参考图片')
  const uploaded = await uploadReference(referencePath.value)
  const assetId = Number(uploaded?.assetId)
  if (!Number.isFinite(assetId) || assetId <= 0) throw new Error('参考图片上传成功但未返回作品编号，请重试')
  referenceAssetId.value = assetId
  return assetId
}
function normalizeMultiViewImages(result: { images?: SeedreamMultiViewImage[] }) {
  const source = Array.isArray(result?.images) ? result.images : []
  const normalized: SeedreamMultiViewImage[] = []
  const missing: string[] = []
  multiViewSlots.forEach((slot) => {
    const candidate = source.find(item => item?.view === slot.key && Number(item?.assetId) > 0)
    if (!candidate) {
      missing.push(slot.label)
      return
    }
    normalized.push({ ...candidate, view: slot.key, label: candidate.label || slot.label, assetId: Number(candidate.assetId) })
  })
  if (missing.length) throw new Error(`多视图服务没有完整返回${missing.join('、')}图，请不要提交 3D，并稍后重试`)
  return normalized
}
async function loadCreditRules() {
  try {
    const rules = await getCreditRules()
    const nextCost = Number(rules?.imageTo3d)
    if (Number.isFinite(nextCost) && nextCost >= 0) imageTo3dCredit.value = nextCost
  } catch {
    // 价格接口暂不可用时保留服务端默认的 70 积分文案；最终仍由提交接口校验并扣减。
  }
}

const defaultNegativePrompt = 'low poly, blurry, flat texture, deformed, asymmetric, noisy mesh, broken topology, floating parts, melted details, plastic look'

async function refreshProductionAssessment(options: { prompt?: string; quiet?: boolean } = {}) {
  const prompt = String(options.prompt || buildPrompt()).trim()
  if (!prompt || !form.prompt.trim()) {
    if (!options.quiet) uni.showToast({ title: '请先填写创作描述', icon: 'none' })
    return null
  }
  assessmentLoading.value = true
  try {
    const result = await assessProductionFeasibility({
      productCategory: selectedProductCategory.value.label,
      material: form.material,
      prompt,
    })
    productionAssessment.value = result
    return result
  } catch (error: any) {
    if (!options.quiet) uni.showToast({ title: error?.message || '生产可行性初筛暂不可用', icon: 'none' })
    return null
  } finally {
    assessmentLoading.value = false
  }
}

async function optimizeText3dPrompt(prompt: string) {
  try {
    const result = await optimizeTripo3dPrompt({
      prompt: `${prompt}；产品类别：${selectedProductCategory.value.label}；制造材质：${form.material}；期望材质与表面质感：${selectedMaterial.value.modelLabel}（${modelMaterialPrompt()}）`,
      promptTemplate: selectedModelTemplate.value,
      productCategory: selectedProductCategory.value.label,
      material: form.material,
    })
    optimized3dTip.value = String(result?.usageTips || '')
    return {
      prompt: String(result?.prompt || prompt).trim(),
      negativePrompt: String(result?.negativePrompt || defaultNegativePrompt).trim(),
    }
  } catch {
    // 提示词服务短暂不可用时，不应让已填写的真实工艺方案无法生成；后端仍会
    // 校验本次产品类别/材质，并将 modelMaterialPrompt 追加给 Tripo。
    optimized3dTip.value = '提示词优化服务暂不可用，已按当前产品和材质方向直接提交高质量 3D 任务。'
    return {
      prompt,
      negativePrompt: defaultNegativePrompt,
    }
  }
}

async function optimizeCurrentImagePrompt() {
  if (!form.prompt.trim()) return uni.showToast({ title: '请先填写创作描述', icon: 'none' })
  imagePromptOptimizing.value = true
  try {
    const result = await optimizeImagePrompt({
      prompt: buildPrompt(),
      provider: 'jimeng',
      productCategory: selectedProductCategory.value.label,
      material: form.material,
    })
    const prompt = String(result?.prompt || '').trim()
    if (!prompt) throw new Error('优化服务未返回有效描述')
    form.prompt = prompt
    imagePromptGuide.value = result?.usageGuide ? '已同步产品结构、材质与使用场景' : '已优化为商业产品生成描述'
    uni.showToast({ title: '描述已优化', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '描述优化暂不可用', icon: 'none' })
  } finally {
    imagePromptOptimizing.value = false
  }
}

function modelQualityOptions() {
  return {
    modelVersion: 'v3.1-20260211',
    promptTemplate: selectedModelTemplate.value,
    productCategory: selectedProductCategory.value.label,
    materialLabel: selectedMaterial.value.modelLabel,
    materialPrompt: modelMaterialPrompt(),
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
}

async function generate() {
  if (!requireSession()) return
  if (!form.prompt.trim()) return uni.showToast({ title: '请填写创作描述', icon: 'none' })
  if (needsReference.value && !referencePath.value) return uni.showToast({ title: '请先选择一张参考图片', icon: 'none' })
  if (!aiPolicyConfirmed.value && !(await confirmCreativePolicy('ai-output'))) return
  aiPolicyConfirmed.value = true
  if (is3dMode.value && !threeDimensionalPolicyConfirmed.value && !(await confirmCreativePolicy('three-dimensional'))) return
  if (is3dMode.value) threeDimensionalPolicyConfirmed.value = true
  const prompt = buildPrompt()
  if (isMultiViewMode.value) return multiViewSource.value === 'manual' ? submitMultiViewModel() : generateMultiView(prompt)
  loading.value = true
  loadingAction.value = 'creation'
  try {
    let result: any
    if (mode.value === 'image') {
      result = await createTextToImage({
        title: form.title || `${selectedProductCategory.value.label} · ${form.material}`,
        prompt,
        rawPrompt: prompt,
        scene: '文创产品',
        productType: selectedProductCategory.value.label,
        productCategory: selectedProductCategory.value.label,
        material: form.material,
      })
    } else if (mode.value === 'reference') {
      const inputAssetId = await ensureReferenceAsset()
      result = await createReferenceToImage({
        title: form.title || `图文结合 · ${selectedProductCategory.value.label}`,
        prompt,
        inputAssetId,
        productCategory: selectedProductCategory.value.label,
        material: form.material,
      })
    } else {
      let inputAssetId: number | undefined
      if (mode.value === 'image3d') inputAssetId = await ensureReferenceAsset()
      await refreshProductionAssessment({ prompt, quiet: true })
      const optimized = mode.value === 'text3d'
        ? await optimizeText3dPrompt(prompt)
        : { prompt, negativePrompt: defaultNegativePrompt }
      result = await createModel({
        title: form.title || `${selectedProductCategory.value.label} · ${form.material} 3D 原型`,
        prompt: optimized.prompt,
        rawPrompt: prompt,
        mode: mode.value === 'image3d' ? 'image_to_model' : 'text_to_model',
        inputAssetId,
        negativePrompt: optimized.negativePrompt,
        ...modelQualityOptions(),
      })
    }
    uni.removeStorageSync('miniapp_atelier_draft')
    const generatedText = mode.value === 'image' || mode.value === 'reference' ? '作品已生成' : '3D 生成任务已创建'
    const assetId = Number(result?.assetId || result?.id)
    if (mode.value === 'image' || mode.value === 'reference') {
      uni.showModal({ title: '作品已生成', content: '可以先去作品库查看，也可以基于这张图继续做商品化选品方案。', cancelText: '去作品库', confirmText: '做成商品', success: (modal) => {
        if (modal.confirm) uni.navigateTo({ url: `/pages/commercial/index${Number.isFinite(assetId) && assetId > 0 ? `?assetId=${assetId}` : ''}` })
        else uni.navigateTo({ url: '/pages/works/index' })
      } })
    } else {
      uni.showModal({ title: '已提交创作', content: `${generatedText}，可在“我的作品”中查看进度。`, showCancel: false, success: () => uni.navigateTo({ url: '/pages/works/index' }) })
    }
    return result
  } catch (error: any) {
    uni.showToast({ title: error.message || '生成失败', icon: 'none' })
  } finally {
    loading.value = false
    loadingAction.value = 'creation'
  }
}

async function generateMultiView(prompt: string) {
  if (!requireSession()) return
  if (!referencePath.value) return uni.showToast({ title: '请先选择一张参考图片', icon: 'none' })
  if (!aiPolicyConfirmed.value && !(await confirmCreativePolicy('ai-output'))) return
  aiPolicyConfirmed.value = true
  loading.value = true
  loadingAction.value = 'multiview'
  clearMultiViewResult()
  try {
    const inputAssetId = await ensureReferenceAsset()
    const result = await createSeedreamMultiView({
      inputAssetId,
      prompt,
      size: multiViewSize.value,
      watermark: true,
    })
    multiViewImages.value = normalizeMultiViewImages(result)
    multiViewMessage.value = result.message || 'Doubao Seedream 已完成四个一致视角，可提交给 Tripo 创建 3D。'
    uni.showToast({ title: '四视图已生成', icon: 'success' })
  } catch (error: any) {
    const message = error?.message || '多视图生成失败，请稍后重试'
    multiViewError.value = message
    uni.showToast({ title: message, icon: 'none' })
  } finally {
    loading.value = false
    loadingAction.value = 'creation'
  }
}

async function submitMultiViewModel() {
  if (!requireSession()) return
  if (!form.prompt.trim()) return uni.showToast({ title: '请填写创作描述', icon: 'none' })
  const assetIds = multiViewSource.value === 'seedream' ? multiViewAssetIdByView.value : manualMultiViewAssetIdByView.value
  const front = assetIds.front
  const left = assetIds.left
  const back = assetIds.back
  const right = assetIds.right
  const viewCount = [front, left, back, right].filter(Boolean).length
  if (multiViewSource.value === 'seedream' && (!front || !left || !back || !right)) {
    return uni.showToast({ title: '请先生成完整的正、左、背、右四视图', icon: 'none' })
  }
  if (multiViewSource.value === 'manual' && (!front || viewCount < 2)) {
    return uni.showToast({ title: '请先上传正面图与至少一个其他角度', icon: 'none' })
  }
  if (!threeDimensionalPolicyConfirmed.value && !(await confirmCreativePolicy('three-dimensional'))) return
  threeDimensionalPolicyConfirmed.value = true
  loading.value = true
  loadingAction.value = 'model'
  try {
    const prompt = buildPrompt()
    await refreshProductionAssessment({ prompt, quiet: true })
    const result = await createModel({
      title: form.title || `${multiViewSource.value === 'seedream' ? 'Seedream' : '手动'} 多视图 · ${selectedProductCategory.value.label} 3D 原型`,
      prompt,
      rawPrompt: prompt,
      mode: 'multiview_to_model',
      multiviewAssetIds: { front, left, back, right },
      negativePrompt: defaultNegativePrompt,
      ...modelQualityOptions(),
    })
    uni.removeStorageSync('miniapp_atelier_draft')
    const remaining = Number(result?.creditAccount?.balance)
    const balanceHint = Number.isFinite(remaining) ? `当前可用积分 ${formatCredit(remaining)}。` : ''
    const serverMessage = result?.message || '多视图 3D 生成任务已创建'
    uni.showModal({
      title: '3D 任务已提交',
      content: `${serverMessage}，可在“我的作品”中查看进度。${balanceHint}`,
      showCancel: false,
      success: () => uni.navigateTo({ url: '/pages/works/index' }),
    })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '3D 任务提交失败', icon: 'none' })
  } finally {
    loading.value = false
    loadingAction.value = 'creation'
  }
}

onLoad((query: any) => {
  const nextMode = String(query?.mode || '') as CreateMode
  if (modeOptions.some(item => item.key === nextMode)) mode.value = nextMode
  const draft = uni.getStorageSync('miniapp_atelier_draft') || {}
  if (draft.mode && modeOptions.some(item => item.key === draft.mode)) mode.value = draft.mode
  if (draft.title) form.title = String(draft.title)
  if (draft.prompt) form.prompt = String(draft.prompt)
  const draftProductKey = String(draft.productKey || '') as ProductCategoryKey
  if (productCategories.some(item => item.key === draftProductKey)) selectedProductKey.value = draftProductKey
  if (draft.material || draft.modelMaterial) restoreProductMaterial(String(draft.material || ''), String(draft.modelMaterial || ''))
  if (draft.pattern?.id && patterns.some(item => item.id === draft.pattern.id)) selectedPatternId.value = draft.pattern.id
  ;(['glaze', 'texture', 'relief'] as FinishKey[]).forEach((key) => { if (Number.isFinite(Number(draft[key]))) finish[key] = Number(draft[key]) })
  void loadCreditRules()
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;padding:26rpx 26rpx 62rpx;background:radial-gradient(ellipse at 4% 3%,rgba(151,177,163,.18),transparent 31%),linear-gradient(180deg,#faf8f3,#f0e9df);box-sizing:border-box;color:#2d2924}.ink-wash{position:absolute;z-index:0;border-radius:50%;filter:blur(12rpx);pointer-events:none}.wash-one{right:-88rpx;top:104rpx;width:280rpx;height:100rpx;background:rgba(119,148,136,.11);transform:rotate(-16deg)}.wash-two{left:-90rpx;top:460rpx;width:260rpx;height:72rpx;background:rgba(184,104,80,.08);transform:rotate(14deg)}.head,.intention-card,.card{position:relative;z-index:1}.head{padding:20rpx 10rpx 28rpx}.eyebrow{display:block;color:#628174;font-size:16rpx;font-weight:900;letter-spacing:2.6rpx}.title{display:block;margin-top:13rpx;font-family:"Songti SC","STSong",serif;font-size:43rpx;font-weight:700;line-height:1.2;letter-spacing:-1.5rpx}.sub{display:block;margin-top:11rpx;color:#82786d;font-size:20rpx;line-height:1.6}.mode-rail{display:flex;gap:7rpx;overflow-x:auto;margin-top:19rpx;padding-bottom:2rpx;white-space:nowrap}.mode-tab{display:flex;flex:0 0 auto;align-items:center;gap:5rpx;padding:8rpx 10rpx;border:1rpx solid #e4dcd1;border-radius:12rpx;background:rgba(255,253,249,.72);color:#82786e;font-size:17rpx}.mode-tab text:first-child{display:grid;place-items:center;width:23rpx;height:23rpx;border-radius:7rpx;background:#f1ede6;color:#96755e;font-family:"Songti SC","STSong",serif;font-size:15rpx}.mode-tab.active{border-color:#97ad9e;background:#eef4ee;color:#4e6d60}.mode-tab.active text:first-child{background:#5e7e71;color:#fff}.intention-card{overflow:hidden;padding:17rpx;border:1rpx solid rgba(132,117,99,.15);border-radius:23rpx;background:rgba(255,253,249,.76);box-shadow:0 12rpx 28rpx rgba(67,53,37,.055)}.intention-top{display:flex;justify-content:space-between;gap:8rpx}.intention-top view{display:flex;flex-direction:column;gap:4rpx}.intention-top view text:first-child{color:#9b816a;font-size:14rpx;letter-spacing:1.8rpx;font-weight:900}.intention-top view text:last-child{font-family:"Songti SC","STSong",serif;font-size:23rpx;font-weight:700}.intention-top>text{align-self:flex-start;padding:5rpx 7rpx;border-radius:99rpx;background:#eef4ee;color:#638073;font-size:14rpx}.object-preview{position:relative;display:flex;align-items:center;gap:13rpx;min-height:116rpx;margin-top:13rpx;padding:13rpx;border:1rpx solid rgba(136,151,134,.15);border-radius:17rpx;background:radial-gradient(circle at 26% 22%,rgba(255,255,255,.88),transparent 26%),linear-gradient(145deg,#dce5dc,#ede1d3);box-sizing:border-box;overflow:hidden}.object-preview::after{content:"";position:absolute;right:-31rpx;bottom:-42rpx;width:180rpx;height:70rpx;border-radius:50%;background:var(--tone);filter:blur(18rpx);opacity:.14}.object-preview>view{position:relative;z-index:1;display:grid;place-items:center;width:84rpx;height:84rpx;border:1rpx solid rgba(255,255,255,.7);border-radius:50%;background:linear-gradient(145deg,var(--tone),#e7cfbd);box-shadow:inset 0 3rpx 9rpx rgba(255,255,255,.52),0 10rpx 15rpx rgba(47,61,53,.17)}.object-preview>view text{color:#fff;font-family:"Songti SC","STSong",serif;font-size:43rpx;text-shadow:0 2rpx 7rpx rgba(35,50,42,.23)}.object-preview>text{position:relative;z-index:1}.object-preview>text:nth-child(2){font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:700}.object-preview>text:nth-child(3){margin-left:auto;color:#726a60;font-size:16rpx}.intention-card>text{display:block;margin-top:11rpx;color:#91867b;font-size:16rpx;line-height:1.5}.card{margin-top:18rpx;padding:23rpx;border:1rpx solid rgba(132,117,99,.13);border-radius:25rpx;background:rgba(255,254,251,.86);box-shadow:0 12rpx 30rpx rgba(67,53,37,.055)}.field-group+.field-group{margin-top:18rpx}.label{display:block;color:#534c44;font-size:22rpx;font-weight:800}.input,.textarea{display:block;box-sizing:border-box;width:100%;margin-top:10rpx;border:1rpx solid #e4dcd1;border-radius:15rpx;background:#fbf9f4;color:#403a33;font-size:23rpx}.input{height:82rpx;padding:0 16rpx}.textarea{min-height:198rpx;padding:15rpx;line-height:1.65}.word-count{display:flex;justify-content:space-between;gap:10rpx;margin-top:8rpx;color:#93887d;font-size:15rpx}.word-count text:first-child{color:#6e8378}.direction-panel{margin-top:21rpx;padding-top:18rpx;border-top:1rpx solid #ece4d9}.panel-head{display:flex;justify-content:space-between;align-items:flex-end;gap:9rpx}.panel-head view{display:flex;flex-direction:column;gap:4rpx}.panel-head view text:first-child{color:#618074;font-size:14rpx;font-weight:900;letter-spacing:1.9rpx}.panel-head view text:last-child{font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:700}.panel-head>text{color:#968b80;font-size:14rpx}.pattern-scroll{white-space:nowrap;margin:13rpx -3rpx -2rpx}.pattern-row{display:flex;gap:9rpx}.pattern-chip{display:flex;flex:0 0 auto;align-items:center;gap:8rpx;padding:9rpx 11rpx;border:1rpx solid #e6ded3;border-radius:14rpx;background:#fffefa}.pattern-chip>text{display:grid;place-items:center;width:30rpx;height:30rpx;border-radius:9rpx;background:rgba(255,255,255,.65);color:var(--tone);font-family:"Songti SC","STSong",serif;font-size:20rpx}.pattern-chip view{display:flex;flex-direction:column;gap:2rpx}.pattern-chip view text:first-child{color:#494139;font-size:18rpx;font-weight:800}.pattern-chip view text:last-child{color:#998e83;font-size:13rpx}.pattern-chip.active{border-color:var(--tone);background:#f7f7f1;box-shadow:0 6rpx 14rpx rgba(83,69,51,.08)}.material-row{display:grid;grid-template-columns:1fr 1fr;gap:8rpx;margin-top:13rpx}.material-chip{display:flex;align-items:center;gap:7rpx;padding:10rpx;border:1rpx solid #e6ded3;border-radius:13rpx;background:#fffefa;color:#6e655b}.material-chip>text:first-child{width:22rpx;height:22rpx;border-radius:7rpx;background:linear-gradient(140deg,#e8e0d4,#a9b9a7 48%,#f8f4ec 51%,#ac6d58)}.material-chip>text.bronze{background:linear-gradient(145deg,#d8caab,#776a59 44%,#b5a98f 47%,#473f36)}.material-chip>text.wood{background:repeating-linear-gradient(65deg,#c19466 0 2rpx,#e6cba8 2rpx 5rpx,#977047 5rpx 7rpx)}.material-chip>text.jade{background:radial-gradient(circle at 32% 25%,#fff 0 16%,transparent 17%),linear-gradient(145deg,#e4e8d6,#9eb6aa)}.material-chip>text:last-child{font-size:18rpx;font-weight:800}.material-chip.active{border-color:#9caf9f;background:#eef4ee;color:#47655a}.slider-group{display:grid;gap:7rpx;margin-top:12rpx;padding:12rpx;border:1rpx solid #e7dfd5;border-radius:16rpx;background:linear-gradient(145deg,#fbfaf6,#f1f4ef)}.slider-row>view{display:flex;justify-content:space-between;color:#6e665d;font-size:17rpx;font-weight:800}.slider-row>view text:last-child{color:#53776a}.slider-row slider{height:32rpx;margin:0 -11rpx}.upload-panel{margin-top:22rpx;padding:16rpx;border:1rpx dashed #c7b5a6;border-radius:17rpx;background:linear-gradient(145deg,#fffaf4,#f8f2ea)}.upload-panel.selected{border-style:solid;border-color:#8ea99a;background:#eff5ef}.upload-panel>view text{display:block}.upload-panel>view text:first-child{color:#a05e48;font-size:14rpx;font-weight:900;letter-spacing:1.6rpx}.upload-panel>view text:last-child{margin-top:6rpx;color:#7d7267;font-size:18rpx;line-height:1.55}.secondary{display:flex;justify-content:space-between;align-items:center;height:74rpx;margin-top:12rpx;padding:0 16rpx;border:1rpx solid #dde8de;border-radius:13rpx;background:#fffdfa;color:#5e796c;font-size:19rpx}.notice{display:flex;flex-direction:column;gap:5rpx;margin-top:21rpx;padding:13rpx;border-left:3rpx solid #bd8067;border-radius:0 13rpx 13rpx 0;background:#f6efe8}.notice text:first-child{color:#9d604a;font-size:16rpx;font-weight:900;letter-spacing:1rpx}.notice text:last-child{color:#7a6e63;font-size:17rpx;line-height:1.55}.generate{display:flex;align-items:center;justify-content:center;gap:9rpx;height:90rpx;margin-top:22rpx;border-radius:18rpx;background:linear-gradient(135deg,#3e3933,#5d7c6f);color:#fff;font-size:24rpx;font-weight:800;box-shadow:0 12rpx 23rpx rgba(53,59,52,.17)}.generate[disabled]{opacity:.62}.seal-loader{display:grid;place-items:center;width:28rpx;height:28rpx;border:1rpx solid rgba(255,255,255,.62);border-radius:4rpx;color:#f5d2b7;font-family:"Songti SC","STSong",serif;font-size:19rpx;animation:sealPress 1.2s ease-in-out infinite}@keyframes sealPress{0%,100%{transform:rotate(-7deg) scale(.88);opacity:.7}50%{transform:rotate(-7deg) scale(1);opacity:1}}

.product-scroll{margin:13rpx -3rpx -2rpx;white-space:nowrap}.product-row{display:flex;gap:9rpx}.product-chip{display:flex;flex:0 0 auto;align-items:center;gap:8rpx;min-width:148rpx;padding:10rpx 11rpx;border:1rpx solid #e6ded3;border-radius:14rpx;background:#fffefa;color:#766c61}.product-chip>text{display:grid;place-items:center;width:33rpx;height:33rpx;border-radius:10rpx;background:linear-gradient(145deg,#f2ebe1,#dfcfbc);color:#856d58;font-family:"Songti SC","STSong",serif;font-size:20rpx;font-weight:800}.product-chip>view{display:flex;flex-direction:column;gap:3rpx}.product-chip>view text:first-child{color:#494139;font-size:18rpx;font-weight:900}.product-chip>view text:last-child{color:#998e83;font-size:13rpx}.product-chip.active{border-color:#8da899;background:#edf4ec;box-shadow:0 6rpx 14rpx rgba(83,69,51,.08)}.product-chip.active>text{background:#5d7d70;color:#fff}.product-brief{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx;margin-top:12rpx;padding:11rpx 12rpx;border-left:3rpx solid #8ba798;border-radius:0 12rpx 12rpx 0;background:#f3f6f0}.product-brief text:first-child{flex:1;color:#737a70;font-size:16rpx;line-height:1.55}.product-brief text:last-child{flex:0 0 auto;max-width:176rpx;color:#547260;font-size:14rpx;font-weight:900;text-align:right}
.material-meta{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx;margin-top:13rpx;padding:10rpx 11rpx;border:1rpx solid #e8e1d7;border-radius:13rpx;background:#faf8f3}.material-meta>view{display:flex;flex-direction:column;gap:3rpx}.material-meta>view text:first-child{color:#5a5148;font-size:17rpx;font-weight:900}.material-meta>view text:last-child{color:#978c80;font-size:14rpx;line-height:1.45}.material-meta>text{flex:0 0 auto;padding:5rpx 7rpx;border-radius:99rpx;background:#eaf2e9;color:#557464;font-size:13rpx;font-weight:800}.material-row{margin-top:10rpx}.material-chip{min-height:68rpx;box-sizing:border-box}.material-chip>.material-swatch{flex:0 0 auto;width:25rpx;height:25rpx;border:1rpx solid rgba(91,78,62,.13);border-radius:8rpx;box-shadow:inset 0 1rpx 3rpx rgba(255,255,255,.56)}.material-chip>view{display:flex;min-width:0;flex-direction:column;gap:2rpx}.material-chip>view text:first-child{color:inherit;font-size:18rpx;font-weight:900}.material-chip>view text:last-child{color:#9a8f83;font-size:13rpx}.material-chip.active>view text:last-child{color:#708776}
.feasibility-panel{margin-top:22rpx;padding:16rpx;border:1rpx solid #d8e1d7;border-radius:19rpx;background:linear-gradient(145deg,#f5f8f3,#fffaf5);box-shadow:inset 0 1rpx 0 rgba(255,255,255,.85)}.feasibility-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx}.feasibility-head>view{display:flex;flex-direction:column;gap:4rpx}.feasibility-head>view text:first-child{color:#668272;font-size:14rpx;font-weight:900;letter-spacing:1.6rpx}.feasibility-head>view text:last-child{color:#403a33;font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:700}.assessment-action{flex:0 0 auto;height:52rpx;margin:0;padding:0 13rpx;border:1rpx solid #b9cdbc;border-radius:10rpx;background:#fffdf9;color:#537363;font-size:14rpx;font-weight:900}.assessment-action::after{border:0}.assessment-action[disabled]{opacity:.58}.feasibility-intro{display:block;margin-top:10rpx;color:#7f766d;font-size:16rpx;line-height:1.58}.assessment-empty{display:flex;flex-direction:column;gap:4rpx;margin-top:12rpx;padding:11rpx 12rpx;border:1rpx dashed #cfc9be;border-radius:13rpx;background:rgba(255,253,249,.7)}.assessment-empty text:first-child{color:#777066;font-size:16rpx;line-height:1.55}.assessment-empty text:last-child{color:#9a8d7e;font-size:14rpx}.assessment-result{margin-top:12rpx;padding:12rpx;border:1rpx solid #d9ded3;border-radius:14rpx;background:#fffdf9}.assessment-result.ready{border-color:#c5d8c8;background:#f5faf3}.assessment-result.review{border-color:#ead6b7;background:#fffaf1}.assessment-result.revise{border-color:#e4c9bd;background:#fff7f3}.assessment-score{display:flex;align-items:center;justify-content:space-between;gap:12rpx}.assessment-score>view{display:flex;flex-direction:column;gap:3rpx}.assessment-score>view text:first-child{color:#4a6858;font-size:19rpx;font-weight:900}.assessment-result.review .assessment-score>view text:first-child{color:#9b7541}.assessment-result.revise .assessment-score>view text:first-child{color:#9b5e4d}.assessment-score>view text:last-child{color:#93887c;font-size:14rpx}.assessment-score>text{display:grid;place-items:center;width:50rpx;height:50rpx;border-radius:50%;background:#dcebdc;color:#4d725c;font-size:25rpx;font-weight:900}.assessment-result.review .assessment-score>text{background:#f2e3c8;color:#9c7744}.assessment-result.revise .assessment-score>text{background:#f0d8ce;color:#a05f4c}.assessment-list{display:flex;flex-direction:column;gap:4rpx;margin-top:11rpx;padding-top:10rpx;border-top:1rpx solid rgba(133,119,101,.14)}.assessment-list text:first-child{color:#7a6755;font-size:14rpx;font-weight:900}.assessment-list text:not(:first-child){color:#766c62;font-size:15rpx;line-height:1.52}.assessment-list.suggestions text:first-child{color:#517262}.assessment-tip{display:block;margin-top:10rpx;padding:9rpx 10rpx;border-radius:10rpx;background:#edf4ec;color:#5d796a;font-size:14rpx;line-height:1.52}

.multiview-workbench{margin-top:22rpx;padding:17rpx;border:1rpx solid #cfddd2;border-radius:20rpx;background:linear-gradient(145deg,rgba(242,247,241,.98),rgba(255,251,245,.96));box-shadow:inset 0 1rpx 0 rgba(255,255,255,.88)}
.multiview-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx}.multiview-head>view{display:flex;flex-direction:column;gap:4rpx}.multiview-head>view text:first-child{color:#668272;font-size:14rpx;font-weight:900;letter-spacing:1.7rpx}.multiview-head>view text:last-child{color:#403a33;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:700}.multiview-state{flex:0 0 auto;padding:6rpx 9rpx;border-radius:99rpx;background:#ece7df;color:#8b8075;font-size:14rpx;font-weight:800}.multiview-state.ready{background:#dcecdf;color:#4e7463}
.multiview-intro{display:block;margin-top:10rpx;color:#80766b;font-size:17rpx;line-height:1.65}.turnaround-flow{display:grid;grid-template-columns:repeat(4,1fr);gap:7rpx;margin-top:15rpx}.turnaround-step{position:relative;display:flex;min-height:77rpx;flex-direction:column;align-items:center;justify-content:center;gap:5rpx;border:1rpx solid #ddd7ce;border-radius:13rpx;background:rgba(255,253,249,.78);color:#8a8177}.turnaround-step text:first-child{display:grid;place-items:center;width:31rpx;height:31rpx;border-radius:50%;background:#ebe6de;color:#82776c;font-family:"Songti SC","STSong",serif;font-size:19rpx}.turnaround-step text:last-child{font-size:14rpx;font-weight:800}.turnaround-step.ready{border-color:#a5bdac;background:#f5faf4;color:#527362}.turnaround-step.ready text:first-child{background:#668574;color:#fff;box-shadow:0 5rpx 10rpx rgba(69,100,82,.18)}
.quality-row{display:flex;align-items:center;justify-content:space-between;gap:12rpx;margin-top:13rpx;padding:10rpx 11rpx;border-radius:13rpx;background:rgba(255,255,255,.58);color:#756c62;font-size:16rpx;font-weight:800}.quality-row>view{display:flex;gap:6rpx}.quality-choice{min-width:68rpx;height:48rpx;margin:0;padding:0 12rpx;border:1rpx solid #ded7cd;border-radius:10rpx;background:#fffdf9;color:#82786d;font-size:15rpx;font-weight:900}.quality-choice.active{border-color:#638172;background:#638172;color:#fff;box-shadow:0 5rpx 11rpx rgba(77,111,94,.18)}.quality-choice[disabled]{opacity:.56}
.multiview-error{display:flex;flex-direction:column;gap:4rpx;margin-top:12rpx;padding:11rpx 12rpx;border:1rpx solid #e7c4b7;border-radius:13rpx;background:#fff5f0;color:#96604c}.multiview-error text:first-child{font-size:16rpx;font-weight:900}.multiview-error text:last-child{font-size:15rpx;line-height:1.55}
.multiview-result{margin-top:14rpx}.view-grid{display:grid;grid-template-columns:1fr 1fr;gap:9rpx}.view-card{overflow:hidden;border:1rpx solid #d9ded6;border-radius:15rpx;background:#fffdf9;box-shadow:0 6rpx 14rpx rgba(62,54,45,.055)}.view-card image,.view-image-fallback{display:block;width:100%;height:186rpx;background:linear-gradient(145deg,#e1e8df,#f0e4d8)}.view-image-fallback{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4rpx;color:#5c7566}.view-image-fallback text:first-child{font-family:"Songti SC","STSong",serif;font-size:22rpx;font-weight:700}.view-image-fallback text:last-child{color:#8d8378;font-size:14rpx}.view-card-meta{display:flex;align-items:center;justify-content:space-between;padding:9rpx 10rpx}.view-card-meta text:first-child{color:#4b443c;font-size:17rpx;font-weight:800}.view-card-meta text:last-child{color:#668272;font-size:13rpx}
.multiview-delivery{display:flex;align-items:flex-start;justify-content:space-between;gap:11rpx;margin-top:13rpx;padding:12rpx;border:1rpx solid #d7e3d7;border-radius:14rpx;background:#f4f8f2}.multiview-delivery>view{display:flex;flex-direction:column;gap:4rpx}.multiview-delivery>view text:first-child{color:#4b6c5a;font-size:18rpx;font-weight:900}.multiview-delivery>view text:last-child{color:#788176;font-size:15rpx;line-height:1.48}.multiview-delivery>text{display:grid;place-items:center;flex:0 0 auto;width:28rpx;height:28rpx;border-radius:50%;background:#668574;color:#fff;font-size:17rpx}.multiview-credit-note{display:flex;flex-direction:column;gap:4rpx;margin-top:10rpx;padding:11rpx 12rpx;border-left:3rpx solid #b88267;border-radius:0 12rpx 12rpx 0;background:#faf2eb}.multiview-credit-note text:first-child{color:#9a604a;font-size:15rpx;font-weight:900}.multiview-credit-note text:last-child{color:#7c7065;font-size:15rpx;line-height:1.55}.model-submit{display:flex;align-items:center;justify-content:center;gap:8rpx;width:100%;height:84rpx;margin-top:14rpx;border-radius:16rpx;background:linear-gradient(135deg,#4a6d5c,#718f7f);color:#fff;font-size:21rpx;font-weight:900;box-shadow:0 11rpx 21rpx rgba(68,98,80,.18)}.model-submit>text{display:grid;place-items:center;width:31rpx;height:31rpx;border:1rpx solid rgba(255,255,255,.55);border-radius:8rpx;font-size:15rpx}.model-submit[disabled]{opacity:.58}
.multiview-empty{display:flex;align-items:center;gap:13rpx;margin-top:14rpx;padding:15rpx;border:1rpx dashed #c9c6bd;border-radius:15rpx;background:rgba(255,253,249,.57)}.turntable-orb{display:grid;place-items:center;flex:0 0 auto;width:66rpx;height:66rpx;border:1rpx solid #aac0ae;border-radius:50%;background:radial-gradient(circle at 33% 28%,#fff 0 17%,#dbe7dc 18% 52%,#aec2b2 53%);box-shadow:0 0 0 7rpx rgba(220,232,221,.55)}.turntable-orb text{color:#526f60;font-family:"Songti SC","STSong",serif;font-size:18rpx;font-weight:800}.multiview-empty>view:last-child{display:flex;flex-direction:column;gap:5rpx}.multiview-empty>view:last-child text:first-child{color:#554d44;font-family:"Songti SC","STSong",serif;font-size:20rpx;font-weight:700}.multiview-empty>view:last-child text:last-child{color:#867b70;font-size:15rpx;line-height:1.55}
.prompt-label-row{display:flex;align-items:center;justify-content:space-between;gap:12rpx}.prompt-optimize{height:48rpx;margin:0;padding:0 12rpx;border:1rpx solid #b9cdbc;border-radius:10rpx;background:#f1f6ef;color:#537363;font-size:14rpx;font-weight:900}.prompt-optimize::after{border:0}.prompt-optimize[disabled]{opacity:.58}
.material-meta{align-items:center}.material-meta>view{min-width:0;flex:1}.material-toggle{flex:0 0 auto;height:50rpx;margin:0;padding:0 11rpx;border:1rpx solid #b9cdbc;border-radius:10rpx;background:#f1f6ef;color:#537363;font-size:14rpx;font-weight:900}.material-toggle::after{border:0}.material-scope-tip{display:block;margin-top:10rpx;padding:10rpx 11rpx;border-left:3rpx solid #b98a6b;border-radius:0 10rpx 10rpx 0;background:#faf3eb;color:#796d62;font-size:14rpx;line-height:1.52}.material-chip{position:relative;padding-right:52rpx}.material-chip .material-recommended,.material-chip .material-cross{position:absolute;right:9rpx;top:9rpx;margin:0!important;padding:4rpx 6rpx;border-radius:99rpx;font-size:11rpx!important;font-weight:900!important;line-height:1.1}.material-chip .material-recommended{color:#557867!important;background:#e4f1e5}.material-chip .material-cross{color:#93735f!important;background:#f4ece4}
.multiview-source-switch{display:grid;grid-template-columns:1fr 1fr;gap:8rpx;margin-top:13rpx}.multiview-source-switch button{display:flex;min-height:84rpx;flex-direction:column;align-items:flex-start;justify-content:center;gap:4rpx;margin:0;padding:12rpx;border:1rpx solid #ddd7ce;border-radius:13rpx;background:#fffdf9;color:#746b61;text-align:left}.multiview-source-switch button::after{border:0}.multiview-source-switch button text:first-child{font-size:17rpx;font-weight:900}.multiview-source-switch button text:last-child{color:#978c81;font-size:13rpx;line-height:1.4}.multiview-source-switch button.active{border-color:#86a894;background:#eef5ed;color:#4b6c5a;box-shadow:0 6rpx 14rpx rgba(78,109,89,.09)}.multiview-source-switch button.active text:last-child{color:#6d8677}.manual-view-grid{display:grid;grid-template-columns:1fr 1fr;gap:9rpx;margin-top:14rpx}.manual-view-slot{position:relative;min-height:180rpx;overflow:hidden;border:1rpx dashed #cfc8bd;border-radius:15rpx;background:rgba(255,253,249,.74)}.manual-view-slot.ready{border-style:solid;border-color:#9db8a5;background:#f4faf3}.manual-view-slot.uploading{opacity:.68}.manual-view-slot image{display:block;width:100%;height:180rpx;background:#e9ece5}.manual-view-empty{display:flex;height:180rpx;flex-direction:column;align-items:center;justify-content:center;gap:5rpx;color:#7a756c}.manual-view-empty text:first-child{display:grid;place-items:center;width:45rpx;height:45rpx;border-radius:50%;background:#ece7df;color:#627a6d;font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:800}.manual-view-empty text:nth-child(2){color:#544c43;font-size:18rpx;font-weight:900}.manual-view-empty text:last-child{color:#978c81;font-size:13rpx}.manual-view-meta{position:absolute;right:0;bottom:0;left:0;display:flex;align-items:center;justify-content:space-between;gap:6rpx;padding:8rpx 9rpx;background:rgba(38,46,40,.68);color:#fff}.manual-view-meta text:first-child{font-size:15rpx;font-weight:900}.manual-view-meta text:last-child{color:rgba(255,255,255,.76);font-size:12rpx}
.product-rule-tip{display:block;margin-top:11rpx;padding:10rpx 11rpx;border-left:3rpx solid #799887;border-radius:0 10rpx 10rpx 0;background:#f1f6ef;color:#5f7668;font-size:14rpx;line-height:1.55}
</style>
