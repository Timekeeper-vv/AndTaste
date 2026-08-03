<template>
  <view class="page">
    <view class="ink ink-one" /><view class="ink ink-two" />

    <view class="hero">
      <view>
        <text class="eyebrow">STYLE INTELLIGENCE · 01</text>
        <text class="title">把灵感，调成<br />一套自己的设计语言。</text>
        <text class="subtitle">先选择文化表达和产品方向，再把它们组合成可直接带入 AI 创作的提示词。</text>
      </view>
      <view class="seal"><text>调</text><text>风</text></view>
    </view>

    <view class="process-card">
      <view v-for="(step, index) in processSteps" :key="step.title" class="process-item" :class="{ active: index === 1 }">
        <text>{{ `0${index + 1}` }}</text><view><text>{{ step.title }}</text><text>{{ step.note }}</text></view>
      </view>
    </view>

    <view class="card idea-card">
      <view class="section-head">
        <view><text>CREATIVE BRIEF</text><text>先写下创作想法</text></view>
        <text>{{ concept.length }}/360</text>
      </view>
      <input v-model.trim="title" class="title-input" maxlength="40" placeholder="作品名称（可选）" />
      <textarea v-model="concept" class="idea-input" maxlength="360" placeholder="例如：把馆藏青铜器的饕餮纹转化为年轻人愿意随身携带的潮玩冰箱贴，保留威严感，但用圆润、温暖的当代方式表达。" />
      <view class="hint-row"><text>✦</text><text>写清楚文化元素、目标人群、使用场景或想要保留的情绪，组合效果会更准确。</text></view>
    </view>

    <view class="card style-card-shell">
      <view class="section-head">
        <view><text>BRAND STYLE LIBRARY</text><text>选择表达风格</text></view>
        <text>{{ styles.length ? `${styles.length} 套已上线` : '动态风格库' }}</text>
      </view>

      <view v-if="stylesLoading" class="state-card"><view class="seal-loader">印</view><text>正在读取可用风格…</text></view>
      <view v-else-if="stylesError" class="state-card error-state"><text class="state-symbol">!</text><text>{{ stylesError }}</text><button size="mini" @tap="loadStyles">重新加载</button></view>
      <view v-else-if="!styles.length" class="state-card"><text class="state-symbol">风</text><text>暂时没有可用风格，请稍后刷新。</text></view>
      <scroll-view v-else scroll-x class="style-scroll" :show-scrollbar="false">
        <view class="style-row">
          <view
            v-for="(style, index) in styles"
            :key="style.id"
            class="style-choice"
            :class="{ active: selectedStyleId === style.id }"
            :style="{ '--tone': styleTone(style, index) }"
            @tap="selectStyle(style.id)"
          >
            <view class="style-orb"><text>{{ styleMark(style.name) }}</text></view>
            <text class="style-name">{{ style.name }}</text>
            <text class="style-desc">{{ style.description || '以当代产品语言转译文化灵感。' }}</text>
            <text class="style-palette">{{ paletteLabel(style.palette) }}</text>
            <text class="style-check">{{ selectedStyleId === style.id ? '已选' : '选择' }}</text>
          </view>
        </view>
      </scroll-view>

      <view v-if="selectedStyle" class="style-note">
        <text>{{ selectedStyle.name }}</text>
        <text>{{ selectedStyle.description || '当前风格会与产品、场景和你的创作想法一起组合。' }}</text>
      </view>
    </view>

    <view class="card direction-card">
      <view class="section-head">
        <view><text>PRODUCT DIRECTION</text><text>补全产品语境</text></view>
        <text>可随时调整</text>
      </view>

      <text class="field-label">使用场景</text>
      <scroll-view scroll-x class="chip-scroll" :show-scrollbar="false"><view class="chip-row"><view v-for="scene in scenes" :key="scene" class="choice-chip" :class="{ active: selectedScene === scene }" @tap="selectedScene = scene"><text>{{ scene }}</text></view></view></scroll-view>

      <text class="field-label">产品类型</text>
      <scroll-view scroll-x class="product-scroll" :show-scrollbar="false"><view class="product-row"><view v-for="product in productCategories" :key="product.key" class="product-choice" :class="{ active: selectedProductKey === product.key }" @tap="chooseProduct(product.key)"><text>{{ product.mark }}</text><view><text>{{ product.label }}</text><text>{{ product.short }}</text></view></view></view></scroll-view>
      <text class="product-desc">{{ selectedProduct.description }}</text>

      <view class="material-head"><view><text class="field-label">材质方向</text><text>会作为创作和后续 3D 的工艺偏好带入</text></view><text>{{ selectedMaterial.name }}</text></view>
      <scroll-view scroll-x class="material-scroll" :show-scrollbar="false"><view class="material-row"><view v-for="material in recommendedMaterials" :key="material.key" class="material-choice" :class="{ active: selectedMaterialKey === material.key }" @tap="selectedMaterialKey = material.key"><text :style="{ background: material.swatch }" /><view><text>{{ material.name }}</text><text>{{ material.short }}</text></view><text v-if="selectedMaterialKey === material.key">✓</text></view></view></scroll-view>
    </view>

    <view class="compose-card">
      <view class="compose-top"><view><text>COMPOSED PROMPT</text><text>由文化约束守住表达边界</text></view><text>{{ composition ? '已准备好' : '等待组合' }}</text></view>
      <button class="compose-button" :loading="composing" :disabled="!canCompose" @tap="compose"><text class="brush" /><text class="button-mark">合</text><text>{{ composing ? '正在组合…' : composition ? '重新组合提示词' : '组合我的提示词' }}</text></button>
      <text v-if="!canCompose" class="compose-hint">{{ !concept.trim() ? '先写下你的创作想法。' : !selectedStyle ? '请选择一套表达风格。' : '请稍候。' }}</text>

      <view v-if="composition" class="result-card" :class="{ stale: compositionDirty }">
        <view class="result-head"><view><text>{{ composition.styleName || selectedStyle?.name || '当前风格' }}</text><text>可在带入创作前继续微调</text></view><text v-if="compositionDirty">参数已改动</text><text v-else>已同步</text></view>
        <text class="result-label">正向提示词</text>
        <textarea :value="composition.prompt" class="result-input" maxlength="1800" @input="updateComposedPrompt" />
        <view class="guardrail"><text>文化表达约束</text><text>{{ composition.guardrails || selectedStyle?.culturalGuardrails || '请确保对文化元素、馆藏图像与相关授权进行核验。' }}</text></view>
        <view class="negative"><text>避免出现</text><text>{{ composition.negativePrompt || selectedStyle?.negativePrompt || '低清晰度、文字水印、畸形结构、未经授权的标识。' }}</text></view>
        <button class="bring-button" :disabled="compositionDirty || composing" @tap="bringToCreate"><text>带入 AI 创作</text><text>→</text></button>
        <text v-if="compositionDirty" class="stale-hint">已修改风格、产品或灵感，请重新组合后再带入，避免提示词与参数不一致。</text>
      </view>
    </view>

    <view class="footer-note"><text>提示词组合不消耗创作积分。</text><text>AI 结果仍需在提交审核、打样或生产前进行版权与工艺确认。</text></view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  composeCreativePrompt,
  getCreativeStyleProfiles,
  type ComposedCreativePrompt,
  type CreativeStyleProfile,
} from '../../api/creative'
import {
  materialCatalog,
  productCategories,
  type MaterialKey,
  type ProductCategoryKey,
} from '../../config/materials'
import { requireSession } from '../../utils/session'

const processSteps = [
  { title: '写灵感', note: '文化与场景' },
  { title: '选风格', note: '表达与约束' },
  { title: '进创作', note: '生成产品视觉' },
]
const scenes = ['博物馆礼赠', '景区伴手礼', '城市文旅', '亲子潮玩', '节日送礼', '桌面收藏']
const toneFallbacks = ['#6d897a', '#b97861', '#a78b55', '#748d97', '#896d63', '#789373']

const styles = ref<CreativeStyleProfile[]>([])
const stylesLoading = ref(false)
const stylesError = ref('')
const selectedStyleId = ref<number | null>(null)
const title = ref('')
const concept = ref('')
const selectedScene = ref(scenes[0])
const selectedProductKey = ref<ProductCategoryKey>('magnet')
const selectedMaterialKey = ref<MaterialKey>('ceramic')
const composing = ref(false)
const composition = ref<ComposedCreativePrompt | null>(null)
const compositionDirty = ref(true)

const selectedStyle = computed(() => styles.value.find(style => style.id === selectedStyleId.value) || null)
const selectedProduct = computed(() => productCategories.find(product => product.key === selectedProductKey.value) || productCategories[0])
const recommendedMaterials = computed(() => selectedProduct.value.materialKeys.map(key => materialCatalog[key]))
const selectedMaterial = computed(() => materialCatalog[selectedMaterialKey.value] || recommendedMaterials.value[0])
const canCompose = computed(() => Boolean(selectedStyle.value && concept.value.trim().length >= 2 && !composing.value))

watch([title, concept, selectedStyleId, selectedScene, selectedProductKey, selectedMaterialKey], () => {
  if (composition.value) compositionDirty.value = true
})

function styleTone(style: CreativeStyleProfile, index: number) {
  const match = String(style.palette || '').match(/#[0-9a-fA-F]{3,8}/)
  return match?.[0] || toneFallbacks[index % toneFallbacks.length]
}

function styleMark(name?: string) {
  return String(name || '风').trim().slice(0, 1) || '风'
}

function paletteLabel(value?: string) {
  const label = String(value || '').trim()
  if (!label) return '文化色彩方向'
  return label.length > 30 ? `${label.slice(0, 30)}…` : label
}

function selectStyle(id: number) {
  selectedStyleId.value = id
}

function chooseProduct(key: ProductCategoryKey) {
  const product = productCategories.find(item => item.key === key)
  if (!product) return
  selectedProductKey.value = product.key
  selectedMaterialKey.value = product.materialKeys[0]
}

async function loadStyles() {
  if (!requireSession()) return
  stylesLoading.value = true
  stylesError.value = ''
  try {
    const result = await getCreativeStyleProfiles()
    styles.value = Array.isArray(result) ? result.filter(item => Number.isFinite(Number(item?.id))) : []
    if (!styles.value.length) {
      selectedStyleId.value = null
      return
    }
    if (!styles.value.some(item => item.id === selectedStyleId.value)) selectedStyleId.value = styles.value[0].id
  } catch (error: any) {
    styles.value = []
    selectedStyleId.value = null
    stylesError.value = error?.message || '风格库加载失败，请检查网络后重试。'
  } finally {
    stylesLoading.value = false
  }
}

async function compose() {
  if (!canCompose.value || !selectedStyle.value) return
  composing.value = true
  try {
    const result = await composeCreativePrompt({
      title: title.value.trim() || undefined,
      prompt: concept.value.trim(),
      styleId: selectedStyle.value.id,
      scene: selectedScene.value,
      productType: selectedProduct.value.label,
      productCategory: selectedProduct.value.key,
      material: selectedMaterial.value.name,
    })
    if (!String(result?.prompt || '').trim()) throw new Error('服务未返回可用提示词，请重试')
    composition.value = {
      prompt: String(result.prompt).trim(),
      negativePrompt: String(result.negativePrompt || '').trim(),
      styleName: String(result.styleName || selectedStyle.value.name).trim(),
      guardrails: String(result.guardrails || selectedStyle.value.culturalGuardrails || '').trim(),
    }
    compositionDirty.value = false
    uni.showToast({ title: '提示词已组合', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '提示词组合失败，请重试', icon: 'none' })
  } finally {
    composing.value = false
  }
}

function updateComposedPrompt(event: any) {
  if (!composition.value) return
  composition.value.prompt = String(event?.detail?.value || '')
}

function bringToCreate() {
  if (!composition.value || compositionDirty.value || composing.value) {
    uni.showToast({ title: '请先重新组合提示词', icon: 'none' })
    return
  }
  const product = selectedProduct.value
  const material = selectedMaterial.value
  uni.setStorageSync('miniapp_atelier_draft', {
    mode: 'image',
    title: title.value.trim() || `${composition.value.styleName || selectedStyle.value?.name || '文化创意'} · ${product.label}`,
    prompt: composition.value.prompt,
    negativePrompt: composition.value.negativePrompt,
    styleId: selectedStyle.value?.id,
    styleName: composition.value.styleName,
    guardrails: composition.value.guardrails,
    scene: selectedScene.value,
    productKey: product.key,
    material: material.name,
    modelMaterial: material.modelLabel,
  })
  uni.navigateTo({ url: '/pages/create/index?mode=image' })
}

onLoad(() => { void loadStyles() })
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;box-sizing:border-box;overflow:hidden;padding:36rpx 28rpx calc(72rpx + env(safe-area-inset-bottom));background:linear-gradient(160deg,#faf9f5 0%,#f4f0e9 48%,#edf3ed 100%);color:#383530}.ink{position:absolute;z-index:0;border-radius:50%;pointer-events:none;filter:blur(4rpx)}.ink-one{top:-165rpx;right:-170rpx;width:510rpx;height:420rpx;background:radial-gradient(ellipse,rgba(115,149,129,.17),transparent 68%)}.ink-two{bottom:260rpx;left:-175rpx;width:440rpx;height:300rpx;background:radial-gradient(ellipse,rgba(183,111,86,.11),transparent 67%)}.hero,.process-card,.card,.compose-card,.footer-note{position:relative;z-index:1}.hero{display:flex;align-items:flex-start;justify-content:space-between;gap:28rpx;padding:13rpx 8rpx 30rpx}.hero>view:first-child{display:flex;min-width:0;flex:1;flex-direction:column}.eyebrow,.section-head>view>text:first-child{color:#6b8879;font-size:17rpx;font-weight:900;letter-spacing:2.1rpx}.title{margin-top:10rpx;color:#343832;font-family:"Songti SC","STSong",serif;font-size:49rpx;font-weight:800;line-height:1.18;letter-spacing:-1.4rpx}.subtitle{max-width:545rpx;margin-top:15rpx;color:#7f8179;font-size:21rpx;line-height:1.65}.seal{display:grid;grid-template-columns:1fr 1fr;gap:1rpx;flex:none;width:82rpx;height:82rpx;padding:6rpx;border:2rpx solid rgba(160,83,63,.7);border-radius:13rpx;box-sizing:border-box;background:rgba(255,250,243,.72);color:#9d573f;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:900;line-height:1;transform:rotate(-7deg)}.seal text{display:grid;place-items:center}.process-card{display:grid;grid-template-columns:repeat(3,1fr);gap:7rpx;padding:11rpx;border:1rpx solid rgba(113,121,103,.13);border-radius:20rpx;background:rgba(255,254,250,.71);box-shadow:0 12rpx 30rpx rgba(62,58,47,.04)}.process-item{display:flex;align-items:center;gap:7rpx;min-width:0;padding:9rpx;border-radius:13rpx;color:#918a80}.process-item>text{color:#b2aaa0;font-family:"Songti SC","STSong",serif;font-size:19rpx;font-weight:800}.process-item>view{display:flex;min-width:0;flex-direction:column;gap:2rpx}.process-item>view text:first-child{overflow:hidden;color:#625e57;font-size:17rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.process-item>view text:last-child{overflow:hidden;font-size:13rpx;text-overflow:ellipsis;white-space:nowrap}.process-item.active{color:#547162;background:#edf4ee}.process-item.active>text{color:#527563}.process-item.active>view text:first-child{color:#476254}.card,.compose-card{margin-top:19rpx;border:1rpx solid rgba(115,107,93,.14);border-radius:25rpx;background:rgba(255,254,250,.84);box-shadow:0 14rpx 32rpx rgba(61,55,43,.055)}.card{padding:22rpx}.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx}.section-head>view{display:flex;min-width:0;flex-direction:column;gap:6rpx}.section-head>view>text:last-child{color:#46443e;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800;letter-spacing:-.7rpx}.section-head>text{flex:none;margin-top:5rpx;color:#9b9185;font-size:15rpx;text-align:right}.title-input,.idea-input,.result-input{display:block;box-sizing:border-box;width:100%;border:1rpx solid #e6ded3;border-radius:15rpx;background:#fcfbf7;color:#46433d;font-size:21rpx}.title-input{height:76rpx;margin-top:17rpx;padding:0 15rpx}.idea-input{min-height:197rpx;margin-top:10rpx;padding:15rpx;line-height:1.65}.hint-row{display:flex;gap:8rpx;align-items:flex-start;margin-top:12rpx;padding:10rpx 11rpx;border-left:3rpx solid #b67b63;border-radius:0 11rpx 11rpx 0;background:#faf2eb;color:#82766b;font-size:16rpx;line-height:1.55}.hint-row text:first-child{color:#a7644e;font-size:18rpx}.state-card{display:grid;justify-items:center;gap:10rpx;margin-top:16rpx;padding:38rpx 20rpx;border:1rpx dashed #d1c8bc;border-radius:18rpx;color:#888178;font-size:19rpx;text-align:center}.state-card button{height:52rpx;margin:1rpx 0 0;padding:0 16rpx;border:1rpx solid #c6d6c8;border-radius:10rpx;background:#eef5ee;color:#567564;font-size:16rpx;font-weight:850}.state-symbol,.seal-loader{display:grid;place-items:center;width:52rpx;height:52rpx;border:1rpx solid #aabdab;border-radius:12rpx;color:#60806d;font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:800}.seal-loader{animation:stamp 1.3s ease-in-out infinite}.error-state{color:#a05f4d}.error-state .state-symbol{border-color:#d9b8ad;color:#a15f4e;background:#fcf4f0}.style-scroll,.chip-scroll,.product-scroll,.material-scroll{margin:16rpx -3rpx -4rpx;white-space:nowrap}.style-row,.chip-row,.product-row,.material-row{display:flex;gap:10rpx}.style-choice{position:relative;display:flex;flex:0 0 245rpx;min-height:247rpx;overflow:hidden;box-sizing:border-box;flex-direction:column;padding:17rpx;border:1rpx solid #e7ded2;border-radius:19rpx;background:linear-gradient(145deg,#fffefa,#f8f4ed)}.style-choice::before{position:absolute;right:-50rpx;top:-50rpx;width:154rpx;height:154rpx;border-radius:50%;background:var(--tone);content:"";opacity:.16}.style-choice.active{border-color:var(--tone);box-shadow:0 11rpx 20rpx rgba(69,63,52,.10)}.style-orb{position:relative;z-index:1;display:grid;place-items:center;width:51rpx;height:51rpx;border-radius:15rpx;background:linear-gradient(145deg,#fff,#eee3d7);color:var(--tone);font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:900;box-shadow:0 5rpx 10rpx rgba(72,63,49,.08)}.style-name{position:relative;z-index:1;margin-top:15rpx;overflow:hidden;color:#464139;font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.style-desc{position:relative;z-index:1;display:-webkit-box;overflow:hidden;margin-top:8rpx;color:#827a70;font-size:16rpx;line-height:1.52;-webkit-box-orient:vertical;-webkit-line-clamp:3}.style-palette{position:relative;z-index:1;overflow:hidden;margin-top:auto;padding-top:12rpx;border-top:1rpx solid rgba(128,113,93,.12);color:#9b816e;font-size:14rpx;text-overflow:ellipsis;white-space:nowrap}.style-check{position:absolute;z-index:1;right:13rpx;top:14rpx;padding:5rpx 8rpx;border-radius:99rpx;color:#8f8376;background:rgba(255,253,249,.76);font-size:13rpx;font-weight:850}.style-choice.active .style-check{color:#547464;background:#e5f0e6}.style-note{display:flex;flex-direction:column;gap:5rpx;margin-top:15rpx;padding:11rpx 12rpx;border-left:3rpx solid #83a08c;border-radius:0 12rpx 12rpx 0;background:#f2f6ef}.style-note text:first-child{color:#577363;font-size:17rpx;font-weight:900}.style-note text:last-child{color:#7c7b72;font-size:16rpx;line-height:1.55}.field-label{display:block;margin-top:19rpx;color:#514e47;font-size:21rpx;font-weight:850}.chip-scroll{margin-top:11rpx}.choice-chip{flex:0 0 auto;padding:10rpx 13rpx;border:1rpx solid #e4dcd1;border-radius:12rpx;background:#fffefa;color:#83796d;font-size:17rpx}.choice-chip.active{border-color:#8ea996;background:#edf4ed;color:#4f705f;font-weight:850}.product-choice{display:flex;flex:0 0 auto;align-items:center;gap:8rpx;min-width:150rpx;padding:10rpx;border:1rpx solid #e5ddd2;border-radius:13rpx;background:#fffefa}.product-choice>text{display:grid;place-items:center;width:33rpx;height:33rpx;border-radius:10rpx;background:#eee7dc;color:#896f59;font-family:"Songti SC","STSong",serif;font-size:19rpx;font-weight:850}.product-choice>view{display:flex;flex-direction:column;gap:2rpx}.product-choice>view text:first-child{color:#4a453e;font-size:18rpx;font-weight:900}.product-choice>view text:last-child{color:#9a8f82;font-size:13rpx}.product-choice.active{border-color:#8fa99a;background:#edf4ed}.product-choice.active>text{background:#5e7d70;color:#fff}.product-desc{display:block;margin-top:11rpx;color:#7a7a71;font-size:16rpx;line-height:1.55}.material-head{display:flex;align-items:flex-end;justify-content:space-between;gap:10rpx;margin-top:18rpx}.material-head .field-label{margin:0}.material-head>view{display:flex;min-width:0;flex-direction:column;gap:5rpx}.material-head>view text:last-child{color:#958a7e;font-size:14rpx}.material-head>text{flex:none;padding:6rpx 8rpx;border-radius:99rpx;background:#edf4ed;color:#557564;font-size:14rpx;font-weight:850}.material-scroll{margin-top:11rpx}.material-choice{display:grid;grid-template-columns:25rpx 1fr auto;align-items:center;gap:8rpx;flex:0 0 177rpx;min-height:62rpx;box-sizing:border-box;padding:10rpx;border:1rpx solid #e5ddd1;border-radius:14rpx;background:#fffefa}.material-choice>text:first-child{width:23rpx;height:23rpx;border:1rpx solid rgba(91,78,62,.13);border-radius:7rpx}.material-choice>view{display:flex;min-width:0;flex-direction:column;gap:2rpx}.material-choice>view text:first-child{color:#4d4942;font-size:17rpx;font-weight:900}.material-choice>view text:last-child{color:#9a8f82;font-size:12rpx}.material-choice>text:last-child{color:#5c816f;font-size:17rpx;font-weight:850}.material-choice.active{border-color:#91aa99;background:#edf4ed}.compose-card{overflow:hidden;padding:21rpx;background:linear-gradient(145deg,#fcfaf4,#edf3ed)}.compose-top{display:flex;align-items:flex-start;justify-content:space-between;gap:12rpx}.compose-top>view{display:flex;flex-direction:column;gap:6rpx}.compose-top>view text:first-child{color:#668274;font-size:16rpx;font-weight:900;letter-spacing:1.9rpx}.compose-top>view text:last-child{color:#403f39;font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:800}.compose-top>text{margin-top:3rpx;padding:6rpx 8rpx;border-radius:99rpx;background:#fffefa;color:#8d8275;font-size:14rpx}.compose-button,.bring-button{position:relative;display:flex;align-items:center;justify-content:center;gap:9rpx;overflow:hidden;width:100%;border-radius:17rpx;color:#fff;font-size:23rpx;font-weight:850}.compose-button{height:88rpx;margin-top:18rpx;background:linear-gradient(135deg,#38352f,#5e7c6e);box-shadow:0 11rpx 21rpx rgba(57,65,55,.15)}.compose-button[disabled]{opacity:.56}.brush{position:absolute;left:6%;top:12rpx;width:61%;height:55rpx;border-radius:60% 50% 48% 56%;background:linear-gradient(90deg,transparent,rgba(184,101,78,.76),rgba(206,158,103,.65),transparent);transform:rotate(-8deg)}.button-mark{position:relative;z-index:1;display:grid;place-items:center;width:28rpx;height:28rpx;border:1rpx solid rgba(255,255,255,.35);border-radius:8rpx;color:#f3cfac;font-family:"Songti SC","STSong",serif;font-size:19rpx}.compose-button>text:last-child{position:relative;z-index:1}.compose-hint{display:block;margin-top:10rpx;color:#978b7e;font-size:15rpx;text-align:center}.result-card{margin-top:19rpx;padding:15rpx;border:1rpx solid #d8e1d8;border-radius:18rpx;background:rgba(255,254,250,.87)}.result-card.stale{border-color:#ebd2bd;background:#fffaf3}.result-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx}.result-head>view{display:flex;min-width:0;flex-direction:column;gap:3rpx}.result-head>view text:first-child{overflow:hidden;color:#4b6456;font-size:19rpx;font-weight:900;text-overflow:ellipsis;white-space:nowrap}.result-head>view text:last-child{color:#90867b;font-size:14rpx}.result-head>text{flex:none;padding:5rpx 7rpx;border-radius:99rpx;background:#e8f2e8;color:#547663;font-size:13rpx;font-weight:850}.result-card.stale .result-head>text{background:#faeadc;color:#a06e52}.result-label{display:block;margin-top:15rpx;color:#675e54;font-size:16rpx;font-weight:900}.result-input{min-height:195rpx;margin-top:8rpx;padding:12rpx;color:#5a554d;font-size:17rpx;line-height:1.58}.guardrail,.negative{display:flex;flex-direction:column;gap:5rpx;margin-top:12rpx;padding:11rpx;border-radius:12rpx}.guardrail{border-left:3rpx solid #819b89;background:#eff5ef}.negative{border-left:3rpx solid #c59075;background:#faf1ea}.guardrail text:first-child,.negative text:first-child{font-size:15rpx;font-weight:900}.guardrail text:first-child{color:#587365}.negative text:first-child{color:#a06e55}.guardrail text:last-child,.negative text:last-child{color:#77746c;font-size:15rpx;line-height:1.55}.bring-button{height:82rpx;margin-top:15rpx;background:linear-gradient(135deg,#a6634e,#c58468)}.bring-button[disabled]{opacity:.54}.bring-button text:last-child{font-size:26rpx}.stale-hint{display:block;margin-top:9rpx;color:#a07156;font-size:14rpx;line-height:1.5;text-align:center}.footer-note{display:flex;flex-direction:column;gap:4rpx;margin:19rpx 12rpx 0;color:#978e84;font-size:15rpx;line-height:1.55;text-align:center}@keyframes stamp{0%,100%{transform:rotate(-7deg) scale(.9);opacity:.7}50%{transform:rotate(-7deg) scale(1);opacity:1}}
</style>
