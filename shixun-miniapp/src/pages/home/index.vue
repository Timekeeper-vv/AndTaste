<template>
  <view class="page">
    <view class="paper-grain" />

    <view class="hero-card">
      <view class="hero-top">
        <view class="brand-lockup"><text class="brand-seal">之</text><view><text>之间智造</text><text>ORIENTAL CREATIVE ATELIER</text></view></view>
        <view class="purpose-pill" @tap="go('/pages/purpose/index')"><text>{{ purposeBadge }}</text><text>切换</text></view>
      </view>
      <text class="hero-kicker">CULTURE · DESIGN · MAKE</text>
      <text class="hello">你好，{{ user?.username || '创作者' }}</text>
      <text class="hero-copy">把一段文化记忆，做成一件<br /><text>会被带走的作品。</text></text>
      <view class="credit-card" @tap="go('/pages/recharge/index')"><view><text>可用创作积分</text><text>{{ contextText }}</text></view><text class="points">{{ credits }}</text><text class="credit-arrow">充值 ›</text></view>
      <view class="ink-sun" /><view class="ink-mountain mountain-one" /><view class="ink-mountain mountain-two" />
    </view>

    <view class="atelier-card">
      <view class="section-head">
        <view><text class="eyebrow">AI CREATIVE DESK</text><text class="title">从一笔灵感，开始做产品。</text></view>
        <text class="connected">作品库已连接</text>
      </view>

      <scroll-view scroll-x class="mode-scroll" :show-scrollbar="false">
        <view class="mode-row">
          <view v-for="item in atelierModes" :key="item.key" class="mode-chip" :class="{ active: atelierMode === item.key }" @tap="chooseMode(item.key)"><text>{{ item.mark }}</text><view><text>{{ item.label }}</text><text>{{ item.short }}</text></view></view>
        </view>
      </scroll-view>

      <view class="studio-canvas">
        <view class="compose-panel">
          <view class="mode-note"><text>{{ activeMode.eyebrow }}</text><text>{{ activeMode.description }}</text></view>
          <text class="field-label">此刻的创作想法</text>
          <textarea v-model="atelier.prompt" class="prompt-input" maxlength="800" auto-height placeholder="例如：以青绿山水和馆藏纹样为灵感，做一款适合年轻人的博物馆冰箱贴。" />
          <view class="prompt-meta"><text>AI 正在理解：{{ selectedPattern.name }} · {{ atelier.material }} · 文创产品</text><text>{{ atelier.prompt.length }}/800</text></view>
          <button class="primary-creation" @tap="startAtelier"><text class="brush-stroke" /><text class="button-symbol">{{ activeMode.mark }}</text>{{ activeMode.action }}<text>{{ activeMode.cost ? `${activeMode.cost} 积分` : '四视图阶段不扣平台积分' }}</text></button>
        </view>

        <view class="preview-panel">
          <view class="preview-top"><text>CREATIVE INTENTION PREVIEW</text><text>意向预览</text></view>
          <view class="preview-stage" :style="{ '--tone': selectedPattern.tone }"><view class="preview-halo" /><view class="preview-product"><text>{{ selectedPattern.mark }}</text></view><view class="preview-label"><text>{{ selectedPattern.name }}</text><text>{{ selectedProduct.label }} · {{ atelier.material }} · {{ materialFinishText }}</text></view></view>
          <view class="preview-foot"><text><text class="dot" />灵感参数将带入下一步创作</text><text>{{ previewWords }}</text></view>
        </view>
      </view>

      <view class="library-block">
        <view class="library-title"><view><text class="eyebrow">HERITAGE PATTERN LIBRARY</text><text>从传统纹样中，挑一笔自己的当代语言。</text></view><text>点击加入</text></view>
        <scroll-view scroll-x class="pattern-scroll" :show-scrollbar="false"><view class="pattern-row"><view v-for="pattern in patterns" :key="pattern.id" class="pattern-card" :class="{ active: atelier.patternId === pattern.id }" :style="{ '--tone': pattern.tone }" @tap="applyPattern(pattern)"><text class="pattern-mark">{{ pattern.mark }}</text><text>{{ pattern.category }}</text><text>{{ pattern.name }}</text><text>{{ pattern.en }}</text><text>加入灵感 →</text></view></view></scroll-view>
      </view>

      <view class="finish-block">
        <view class="library-title"><view><text class="eyebrow">MATERIAL & FINISH LAB</text><text>决定它的温度、光泽与触感。</text></view><text>带入创作方向</text></view>
        <view class="product-picker">
          <text>先选产品类别</text>
          <scroll-view scroll-x class="product-scroll" :show-scrollbar="false"><view class="product-row"><view v-for="product in productCategories" :key="product.key" class="product-choice" :class="{ active: atelier.productKey === product.key }" @tap="chooseProduct(product.key)"><text>{{ product.mark }}</text><view><text>{{ product.label }}</text><text>{{ product.short }}</text></view></view></view></scroll-view>
          <text class="product-note">{{ selectedProduct.description }}</text>
        </view>
        <view class="material-caption"><view><text>{{ selectedProduct.label }} · {{ showAllMaterials ? '全部制造材质' : '量产推荐材质' }}</text><text>{{ selectedMaterial.modelLabel }}</text></view><button class="material-toggle" size="mini" @tap="showAllMaterials = !showAllMaterials">{{ showAllMaterials ? '只看推荐' : `查看全部 ${materialList.length} 种` }}</button></view>
        <text v-if="showAllMaterials" class="material-scope-tip">所有材质都可带入创作；标注“推荐”的是当前 {{ selectedProduct.label }} 更适合优先打样的工艺方向。</text>
        <view class="material-grid"><view v-for="material in visibleMaterials" :key="material.key" class="material-choice" :class="{ active: atelier.material === material.name }" @tap="chooseMaterial(material)"><text class="material-swatch" :style="{ background: material.swatch }" /><view><text>{{ material.name }}</text><text>{{ material.short }}</text></view><text v-if="isRecommendedForSelectedProduct(material)" class="material-recommended">推荐</text><text v-else class="material-cross">可选</text></view></view>
        <view class="finish-controls"><view v-for="control in finishControls" :key="control.key" class="finish-row"><view><text>{{ control.label }}</text><text>{{ atelier[control.key] }}%</text></view><slider :value="atelier[control.key]" min="0" max="100" activeColor="#6e8b7c" backgroundColor="#e6dfd4" block-color="#6e8b7c" block-size="16" @change="changeFinish(control.key, $event)" /></view></view>
        <view class="glaze-preview" :style="{ '--tone': selectedPattern.tone, '--glaze': `${atelier.glaze}%`, '--texture': `${atelier.texture}%`, '--relief': `${atelier.relief}%` }"><view><text>{{ selectedPattern.mark }}</text></view><text>{{ atelier.material }}</text><text>釉面与浮雕创意预览</text></view>
      </view>
    </view>

    <view class="entry-section">
      <view class="section-head compact"><view><text class="eyebrow">NEXT MOVE</text><text class="title">继续把作品做完整</text></view><text>全部业务入口</text></view>
      <view class="entry-grid"><view v-for="entry in quickEntries" :key="entry.title" class="entry-card" :class="entry.tone" @tap="entry.action"><text>{{ entry.no }}</text><text>{{ entry.icon }}</text><text>{{ entry.title }}</text><text>{{ entry.desc }}</text><text>{{ entry.tail }}</text></view></view>
    </view>

    <view class="market-section">
      <view class="market-heading"><text class="eyebrow">CULTURAL MARKET NOTE</text><text>从热门渠道找到信心，<text>从小景区找到机会。</text></text><text>以下为创作策略参考，合作前请核验授权与真实渠道数据。</text></view>
      <scroll-view scroll-x class="channel-scroll" :show-scrollbar="false"><view class="channel-row"><view v-for="channel in channels" :key="channel.name" class="channel-card"><text>{{ channel.mark }}</text><view><text>{{ channel.kind }}</text><text>{{ channel.name }}</text><text>{{ channel.desc }}</text><text>{{ channel.tag }}</text></view></view></view></scroll-view>
      <view class="case-head"><view><text class="eyebrow">CREATOR REFERENCE</text><text>把成功逻辑，拆成可用的创作方法。</text></view><text>创作示例</text></view>
      <scroll-view scroll-x class="case-scroll" :show-scrollbar="false"><view class="case-row"><view v-for="item in caseStudies" :key="item.title" class="case-card" :style="{ '--tone': item.tone }"><view class="case-cover"><text>{{ item.mark }}</text><text>{{ item.category }}</text></view><view><text>{{ item.title }}</text><text>{{ item.story }}</text><text>{{ item.method }}</text></view></view></view></scroll-view>
      <text class="case-note">以上均为平台创作方向示例，用于拆解产品思路；并非真实设计师、销量或授权背书。</text>
      <view class="rank-card"><view class="rank-head"><view><text>MARKET PULSE</text><text>销量情报榜</text><text>{{ rankMeta[rankingPeriod].caption }}</text></view><view class="rank-tabs"><text v-for="period in rankingPeriods" :key="period.key" :class="{ active: rankingPeriod === period.key }" @tap="rankingPeriod = period.key">{{ period.label }}</text></view></view><view v-for="(item, index) in rankItems" :key="item.name" class="rank-row"><text>0{{ index + 1 }}</text><view><text>{{ item.name }}</text><text>{{ item.note }}</text><view><text :style="{ width: `${item.share}%` }" /></view></view><view><text>{{ item.sales }}</text><text>{{ item.trend }}</text></view></view><text class="rank-note">{{ rankMeta[rankingPeriod].insight }}</text></view>
    </view>

    <view class="bottom-nav"><view class="active" @tap="go('/pages/home/index')"><text>⌂</text><text>首页</text></view><view @tap="startAtelier"><text>✦</text><text>创作</text></view><view @tap="go('/pages/works/index')"><text>▣</text><text>作品</text></view><view @tap="go('/pages/profile/index')"><text>◉</text><text>我的</text></view></view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { getCredits } from '../../api/creative'
import { materialCatalog, materialList, productCategories, isRecommendedMaterial, type MaterialDefinition } from '../../config/materials'
import { getSession, requireSession } from '../../utils/session'

type AtelierMode = 'concept' | 'reference' | 'prototype' | 'multiview'
type FinishKey = 'glaze' | 'texture' | 'relief'

const user = ref(getSession()?.user)
const credits = ref(0)
const context = ref<any>(uni.getStorageSync('creation_context'))
const atelierMode = ref<AtelierMode>('concept')
const rankingPeriod = ref<'month' | 'quarter' | 'year'>('month')
const showAllMaterials = ref(false)
const atelier = reactive({
  prompt: '以青绿山水和馆藏纹样为灵感，做一款适合年轻人的博物馆冰箱贴',
  patternId: 'taotie',
  productKey: 'magnet',
  material: '陶瓷釉面',
  modelMaterial: '陶瓷釉面',
  glaze: 72,
  texture: 42,
  relief: 36,
})

const atelierModes: Array<{ key: AtelierMode; label: string; short: string; mark: string; eyebrow: string; description: string; action: string; cost: number }> = [
  { key: 'concept', label: '灵感生图', short: '一句话成图', mark: '墨', eyebrow: '01 · IDEA TO IMAGE', description: '从一句文化灵感，生成可继续打样的产品视觉。', action: '生成产品视觉', cost: 16 },
  { key: 'reference', label: '参考图改造', short: '保留主体特征', mark: '鉴', eyebrow: '02 · REFERENCE REMIX', description: '上传一张参考图，用纹样、材质和场景重构产品语言。', action: '上传参考图', cost: 16 },
  { key: 'prototype', label: '3D 原型', short: '从构思到原型', mark: '形', eyebrow: '03 · FORM TO OBJECT', description: '把构思推进为可预览、可换材质的三维原型。', action: '生成 3D 原型', cost: 60 },
  { key: 'multiview', label: '多视图 3D', short: '四个一致视角', mark: '观', eyebrow: '04 · MULTIVIEW TO MODEL', description: '用一张产品图生成正、左、背、右四视图，再提交为更完整的 3D 原型。', action: '生成四视图', cost: 0 },
]
const patterns = [
  { id: 'taotie', name: '饕餮回纹', category: '青铜纹样', en: 'TAOTIE RHYTHM', prompt: '简化饕餮回纹，适合文创产品边缘与局部浮雕装饰', tone: '#6d8476', mark: '饕' },
  { id: 'cloud', name: '如意云纹', category: '吉祥纹样', en: 'AUSPICIOUS CLOUD', prompt: '灵动如意云纹，以留白和连续曲线构成现代东方装饰', tone: '#b66f59', mark: '云' },
  { id: 'brocade', name: '团花锦纹', category: '织绣纹样', en: 'BROCADE BLOOM', prompt: '精简团花锦纹，以现代比例呈现精致织锦节奏', tone: '#b89557', mark: '锦' },
  { id: 'mountain', name: '青绿山水', category: '山水意境', en: 'GREEN LANDSCAPE', prompt: '青绿山水的层叠远近关系，保留宣纸般呼吸感', tone: '#789993', mark: '山' },
  { id: 'window', name: '花窗几何', category: '建筑纹样', en: 'LATTICE GEOMETRY', prompt: '传统花窗几何结构，以现代简化比例呈现秩序感', tone: '#887567', mark: '窗' },
]
const finishControls: Array<{ key: FinishKey; label: string }> = [
  { key: 'glaze', label: '釉面光泽' },
  { key: 'texture', label: '肌理颗粒' },
  { key: 'relief', label: '浮雕层次' },
]
const channels = [
  { mark: '国', kind: '高客流渠道', name: '国家级综合馆', desc: '适合成熟系列与高完成度潮玩。', tag: '背书强 · 竞争高' },
  { mark: '城', kind: '城市文旅渠道', name: '城市历史博物馆', desc: '地域故事更强，礼赠转化稳定。', tag: '客流稳 · 送礼强' },
  { mark: '景', kind: '低竞争试验场', name: '小众景区文创店', desc: '新品更容易获得完整陈列位。', tag: '小批量 · 易试爆款' },
]
const caseStudies = [
  { mark: '纹', category: '馆藏符号转译', title: '青铜回纹冰箱贴', story: '把器物局部纹样缩为掌心尺度，先建立一眼可识别的轮廓。', method: '方法：一个核心符号 + 低门槛单品', tone: '#6f887a' },
  { mark: '山', category: '地方故事系列化', title: '青绿山水礼赠套装', story: '把地域色彩延伸到包装、卡片与产品细节，形成完整的送礼语境。', method: '方法：一组色彩 + 多个触点', tone: '#829b91' },
  { mark: '景', category: '小景区试爆款', title: '守护兽随身挂件', story: '从地方传说中提取一个可爱主角，用小批量陈列验证反馈。', method: '方法：小批量 + 强记忆点', tone: '#bd8067' },
]
const rankingPeriods = [{ key: 'month' as const, label: '月榜' }, { key: 'quarter' as const, label: '季榜' }, { key: 'year' as const, label: '年榜' }]
const rankMeta = {
  month: { caption: '本月试销表现 · 测试数据', insight: '低客单、高辨识的轻量纪念品，仍是新系列首发试爆款的优先选择。' },
  quarter: { caption: '近 90 天复购信号 · 测试数据', insight: '可互动、可收藏的角色型产品，更容易沉淀复购与系列化购买。' },
  year: { caption: '年度稳定成交 · 测试数据', insight: '文化符号与礼赠场景结合，更容易形成长期销售势能。' },
}
const rankings = {
  month: [{ name: '青铜纹样冰箱贴', note: '低门槛 · 高辨识', sales: '9.8K', trend: '+28%', share: 96 }, { name: '城市守护兽毛绒', note: '亲子潮玩 · 互动强', sales: '7.4K', trend: '+19%', share: 77 }, { name: '鎏金书签礼盒', note: '礼赠文具 · 节日送礼', sales: '5.9K', trend: '+13%', share: 62 }],
  quarter: [{ name: '城市守护兽毛绒', note: '系列化复购', sales: '23.6K', trend: '+34%', share: 94 }, { name: '馆藏色礼盒', note: '客单提升', sales: '18.2K', trend: '+22%', share: 75 }, { name: '山水亚克力钥匙扣', note: '陈列友好', sales: '14.6K', trend: '+17%', share: 60 }],
  year: [{ name: '青铜纹样冰箱贴', note: '全年稳定爆发', sales: '81.4K', trend: '+42%', share: 98 }, { name: '馆藏色礼盒', note: '礼赠刚需', sales: '64.9K', trend: '+28%', share: 78 }, { name: '城市守护兽毛绒', note: '口碑延续', sales: '58.7K', trend: '+24%', share: 69 }],
}

const activeMode = computed(() => atelierModes.find(item => item.key === atelierMode.value) || atelierModes[0])
const selectedPattern = computed(() => patterns.find(item => item.id === atelier.patternId) || patterns[0])
const selectedProduct = computed(() => productCategories.find(item => item.key === atelier.productKey) || productCategories[0])
const recommendedMaterials = computed<MaterialDefinition[]>(() => selectedProduct.value.materialKeys.map(key => materialCatalog[key]))
const visibleMaterials = computed<MaterialDefinition[]>(() => showAllMaterials.value ? materialList : recommendedMaterials.value)
const selectedMaterial = computed<MaterialDefinition>(() => materialList.find(item => item.name === atelier.material || item.modelLabel === atelier.modelMaterial) || materialCatalog.ceramic)
const contextText = computed(() => context.value?.purpose === 'museum_sale' ? `正在为「${context.value.museum?.name || '博物馆'}」创作` : '个人创作工作台')
const purposeBadge = computed(() => context.value?.purpose === 'museum_sale' ? '博物馆售卖' : '个人创作')
const materialFinishText = computed(() => `${atelier.glaze}% 光泽`)
const previewWords = computed(() => `${atelier.prompt.length}/800`)
const rankItems = computed(() => rankings[rankingPeriod.value])
const quickEntries = computed(() => [
  { no: '01', icon: '✦', title: '灵感生图', desc: '一句文化灵感，生成产品视觉', tail: '16 积分 / 次', tone: 'celadon', action: () => beginCreation('concept') },
  { no: '02', icon: '鉴', title: '参考图改造', desc: '上传草图或产品图，重构文创语言', tail: '16 积分 / 次', tone: 'terracotta', action: () => beginCreation('reference') },
  { no: '03', icon: '形', title: '文字生成 3D', desc: '从文字构思推进为立体模型', tail: '60 积分 / 次', tone: 'ink', action: () => beginCreation('prototype') },
  { no: '04', icon: '立', title: '图片生成 3D', desc: '上传清晰产品图，生成原型', tail: '70 积分 / 次', tone: 'terracotta', action: openImageTo3d },
  { no: '05', icon: '观', title: '多视图 3D', desc: 'Seedream 四视图，让模型更完整', tail: '先生成 4 视图 →', tone: 'celadon', action: () => beginCreation('multiview') },
  { no: '06', icon: '▣', title: '我的作品', desc: '查看生成、审核和生产申请进度', tail: '进入作品库 →', tone: 'gold', action: () => go('/pages/works/index') },
])

function go(url: string) { uni.navigateTo({ url }) }
function chooseMode(mode: AtelierMode) { atelierMode.value = mode }
function applyPattern(pattern: typeof patterns[number]) {
  atelier.patternId = pattern.id
  if (!atelier.prompt.includes(pattern.prompt)) atelier.prompt = `${atelier.prompt.replace(/[，,。；;\s]+$/, '')}，${pattern.prompt}`
}
function chooseProduct(key: string) {
  const product = productCategories.find(item => item.key === key)
  if (!product) return
  atelier.productKey = product.key
  const material = materialCatalog[product.materialKeys[0]]
  atelier.material = material.name
  atelier.modelMaterial = material.modelLabel
}
function chooseMaterial(material: MaterialDefinition) { atelier.material = material.name; atelier.modelMaterial = material.modelLabel }
function isRecommendedForSelectedProduct(material: MaterialDefinition) { return isRecommendedMaterial(selectedProduct.value, material) }
function changeFinish(key: FinishKey, event: any) { atelier[key] = Number(event.detail.value) || 0 }
function buildPrompt() {
  const direction = `产品类别：${selectedProduct.value.label}，制造材质：${atelier.material}，视觉表面：${atelier.modelMaterial}，釉面光泽 ${atelier.glaze}%，肌理颗粒 ${atelier.texture}%，浮雕层次 ${atelier.relief}%`
  const source = atelier.prompt.replace(/(?:，|,)?材质表现：[^。；;]*(?:[。；;]|$)/g, '').replace(/(?:，|,)?产品类别：[^。；;]*(?:[。；;]|$)/g, '').replace(/[，,。；;\s]+$/, '')
  return `${source}，${direction}`
}
function creationMode(mode: AtelierMode) { return mode === 'concept' ? 'image' : mode === 'reference' ? 'reference' : mode === 'multiview' ? 'multiview' : 'text3d' }
function beginCreation(mode = atelierMode.value) {
  uni.setStorageSync('miniapp_atelier_draft', { mode: creationMode(mode), title: `${selectedPattern.value.name} · ${atelier.material}`, prompt: buildPrompt(), pattern: selectedPattern.value, productKey: atelier.productKey, material: atelier.material, modelMaterial: atelier.modelMaterial, glaze: atelier.glaze, texture: atelier.texture, relief: atelier.relief })
  go(`/pages/create/index?mode=${creationMode(mode)}`)
}
function openImageTo3d() {
  uni.setStorageSync('miniapp_atelier_draft', { mode: 'image3d', title: `${selectedPattern.value.name} · ${atelier.material}`, prompt: buildPrompt(), pattern: selectedPattern.value, productKey: atelier.productKey, material: atelier.material, modelMaterial: atelier.modelMaterial, glaze: atelier.glaze, texture: atelier.texture, relief: atelier.relief })
  go('/pages/create/index?mode=image3d')
}
function startAtelier() { beginCreation() }

onMounted(async () => {
  if (!requireSession()) return
  try { credits.value = Number((await getCredits()).balance) || 0 } catch { /* 余额加载失败不影响创作入口 */ }
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;overflow:hidden;padding:0 24rpx 156rpx;background:radial-gradient(ellipse at 12% 0%,rgba(157,181,169,.22),transparent 30%),radial-gradient(ellipse at 95% 13%,rgba(191,111,84,.12),transparent 24%),linear-gradient(180deg,#fbf9f4 0%,#f5f0e8 52%,#eee6db 100%);box-sizing:border-box;color:#292622}.paper-grain{position:fixed;z-index:0;inset:0;pointer-events:none;opacity:.34;background-image:radial-gradient(circle at 1rpx 1rpx,rgba(74,63,51,.12) .65rpx,transparent 1rpx);background-size:8rpx 8rpx}.hero-card,.atelier-card,.entry-section,.market-section,.bottom-nav{position:relative;z-index:1}.hero-card{overflow:hidden;margin:20rpx -24rpx 0;padding:44rpx 38rpx 40rpx;border:1rpx solid rgba(123,106,89,.14);border-radius:0 0 44rpx 44rpx;background:linear-gradient(135deg,rgba(255,255,255,.93),rgba(247,244,236,.91) 54%,rgba(215,227,216,.84));box-shadow:0 22rpx 48rpx rgba(64,53,39,.09)}.hero-top{display:flex;justify-content:space-between;align-items:flex-start;gap:15rpx}.brand-lockup{display:flex;align-items:center;gap:12rpx}.brand-seal{display:grid;place-items:center;width:42rpx;height:42rpx;border:2rpx solid #a35e4a;border-radius:8rpx;color:#a35e4a;font-family:"Songti SC","STSong",serif;font-size:28rpx;font-weight:700;transform:rotate(-5deg)}.brand-lockup view text{display:block}.brand-lockup view text:first-child{font-family:"Songti SC","STSong",serif;font-size:28rpx;letter-spacing:2rpx}.brand-lockup view text:last-child{margin-top:4rpx;color:#8d8479;font-size:13rpx;letter-spacing:1.7rpx}.purpose-pill{display:flex;gap:7rpx;align-items:center;padding:9rpx 10rpx;border:1rpx solid #dbe6dc;border-radius:999rpx;background:rgba(255,253,249,.8);color:#5a776b;font-size:18rpx}.purpose-pill text:last-child{padding-left:7rpx;border-left:1rpx solid #d8e2d9;color:#8b796c}.hero-kicker{display:block;margin-top:50rpx;color:#628074;font-size:17rpx;font-weight:800;letter-spacing:3rpx}.hello{display:block;margin-top:14rpx;font-family:"Songti SC","STSong",serif;font-size:52rpx;line-height:1.1;font-weight:700;letter-spacing:-1rpx}.hero-copy{display:block;position:relative;z-index:2;margin-top:15rpx;color:#71695f;font-family:"Songti SC","STSong",serif;font-size:34rpx;line-height:1.45}.hero-copy text{color:#b9664f}.credit-card{position:relative;z-index:2;display:flex;align-items:center;gap:10rpx;margin-top:31rpx;padding:20rpx;border:1rpx solid rgba(255,255,255,.8);border-radius:20rpx;background:rgba(255,253,249,.72);box-shadow:0 8rpx 18rpx rgba(76,61,43,.05)}.credit-card view text{display:block;font-size:20rpx;color:#80776d}.credit-card view text:last-child{margin-top:5rpx;color:#627b70;font-size:17rpx}.points{margin-left:auto;color:#3d3a35;font-family:"Songti SC","STSong",serif;font-size:48rpx;font-weight:700}.credit-arrow{color:#a15f4a;font-size:19rpx}.ink-sun{position:absolute;right:72rpx;top:105rpx;width:142rpx;height:142rpx;border-radius:50%;background:radial-gradient(circle at 37% 30%,rgba(255,255,255,.74),transparent 23%),linear-gradient(145deg,rgba(221,189,151,.65),rgba(190,111,82,.28));opacity:.78}.ink-mountain{position:absolute;z-index:1;border-radius:50%;filter:blur(1rpx);opacity:.56}.mountain-one{right:-54rpx;bottom:45rpx;width:346rpx;height:128rpx;background:rgba(107,139,124,.23);transform:rotate(-13deg)}.mountain-two{right:54rpx;bottom:-52rpx;width:384rpx;height:138rpx;background:rgba(109,88,69,.14);transform:rotate(8deg)}
.atelier-card{margin-top:24rpx;padding:24rpx;border:1rpx solid rgba(132,117,99,.14);border-radius:30rpx;background:rgba(255,253,249,.8);box-shadow:0 14rpx 34rpx rgba(69,55,39,.06)}.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:14rpx}.section-head>view{display:flex;flex-direction:column;gap:7rpx}.eyebrow{color:#5e7d70;font-size:16rpx;font-weight:900;letter-spacing:2.4rpx}.title{font-family:"Songti SC","STSong",serif;font-size:33rpx;font-weight:700;letter-spacing:-1rpx}.connected{margin-top:4rpx;padding:7rpx 9rpx;border-radius:999rpx;background:#edf4ed;color:#678174;font-size:16rpx}.mode-scroll{white-space:nowrap;margin:21rpx -4rpx 0}.mode-row,.pattern-row,.channel-row{display:flex;gap:12rpx}.mode-chip{display:flex;align-items:center;gap:9rpx;min-width:184rpx;padding:13rpx;border:1rpx solid #e6ddd1;border-radius:17rpx;background:#fffefa;color:#847b70;box-sizing:border-box}.mode-chip>text{display:grid;place-items:center;width:38rpx;height:38rpx;border-radius:11rpx;background:#f2eee7;color:#927662;font-family:"Songti SC","STSong",serif;font-size:23rpx}.mode-chip view text{display:block}.mode-chip view text:first-child{font-size:21rpx;font-weight:800}.mode-chip view text:last-child{margin-top:3rpx;font-size:15rpx}.mode-chip.active{border-color:#9ab0a2;background:#edf4ed;color:#49685c;box-shadow:0 7rpx 14rpx rgba(84,119,103,.1)}.mode-chip.active>text{background:#607e71;color:#fff}.studio-canvas{display:grid;gap:16rpx;margin-top:19rpx}.compose-panel,.preview-panel{overflow:hidden;border:1rpx solid #e7ded3;border-radius:22rpx;background:#fffefa}.compose-panel{padding:20rpx}.mode-note text{display:block}.mode-note text:first-child{color:#987f69;font-size:15rpx;font-weight:900;letter-spacing:1.7rpx}.mode-note text:last-child{margin-top:7rpx;color:#81776c;font-size:20rpx;line-height:1.55}.field-label{display:block;margin-top:16rpx;color:#554e46;font-size:21rpx;font-weight:800}.prompt-input{display:block;box-sizing:border-box;width:100%;min-height:148rpx;margin-top:10rpx;padding:16rpx;border:1rpx solid #e5ddd2;border-radius:16rpx;background:linear-gradient(145deg,#fffefa,#faf8f3);color:#413a33;font-size:23rpx;line-height:1.65}.prompt-meta{display:flex;justify-content:space-between;gap:10rpx;margin-top:9rpx;color:#91867a;font-size:16rpx;line-height:1.4}.prompt-meta text:first-child{max-width:78%;color:#697e73}.primary-creation{position:relative;isolation:isolate;display:flex;align-items:center;justify-content:center;gap:8rpx;height:84rpx;margin-top:18rpx;overflow:hidden;border-radius:17rpx;background:#39342f;color:#fff;font-size:24rpx;font-weight:800}.primary-creation>text:not(.brush-stroke){position:relative;z-index:2}.primary-creation>text:last-child{color:#d9d1c5;font-size:16rpx}.button-symbol{display:grid;place-items:center;width:28rpx;height:28rpx;border:1rpx solid rgba(255,255,255,.26);border-radius:8rpx;color:#f2cda9;font-family:"Songti SC","STSong",serif;font-size:19rpx}.brush-stroke{position:absolute;z-index:1;left:5%;top:12rpx;width:58%;height:54rpx;border-radius:60% 50% 48% 56%;background:linear-gradient(90deg,transparent,rgba(185,102,79,.82),rgba(205,158,104,.68),transparent);transform:rotate(-9deg)}.preview-panel{padding:15rpx;background:linear-gradient(145deg,#f4f1e9,#e7eee9)}.preview-top{display:flex;justify-content:space-between;color:#99836e;font-size:15rpx;font-weight:900;letter-spacing:1.5rpx}.preview-top text:last-child{padding:4rpx 7rpx;border-radius:99rpx;background:rgba(255,255,255,.64);color:#678075;font-size:14rpx;letter-spacing:0}.preview-stage{position:relative;display:grid;place-items:center;min-height:242rpx;overflow:hidden;margin-top:12rpx;border:1rpx solid rgba(122,137,121,.18);border-radius:18rpx;background:radial-gradient(circle at 50% 19%,rgba(255,255,255,.88),transparent 36%),linear-gradient(145deg,#dbe5dc,#ede1d3)}.preview-stage::after{content:"";position:absolute;bottom:-32rpx;width:70%;height:70rpx;border-radius:50%;background:rgba(65,83,70,.18);filter:blur(16rpx)}.preview-halo{position:absolute;width:186rpx;height:56rpx;border-radius:50%;background:var(--tone);opacity:.17;filter:blur(22rpx);transform:rotate(-14deg)}.preview-product{position:relative;z-index:2;display:grid;place-items:center;width:136rpx;height:136rpx;border:1rpx solid rgba(255,255,255,.76);border-radius:50%;background:radial-gradient(circle at 35% 24%,rgba(255,255,255,.88),transparent 27%),linear-gradient(145deg,var(--tone),#e5d0bf);box-shadow:inset 0 3rpx 13rpx rgba(255,255,255,.64),0 18rpx 24rpx rgba(47,61,53,.18)}.preview-product text{color:#fff;font-family:"Songti SC","STSong",serif;font-size:69rpx;text-shadow:0 3rpx 8rpx rgba(35,50,42,.25)}.preview-label{position:absolute;z-index:3;left:12rpx;bottom:12rpx;display:flex;flex-direction:column;gap:3rpx;padding:9rpx 11rpx;border:1rpx solid rgba(255,255,255,.72);border-radius:12rpx;background:rgba(255,253,249,.74);backdrop-filter:blur(8rpx)}.preview-label text:first-child{color:#4d453c;font-size:20rpx;font-weight:800}.preview-label text:last-child{color:#83786d;font-size:15rpx}.preview-foot{display:flex;justify-content:space-between;gap:10rpx;margin-top:10rpx;color:#82786d;font-size:14rpx}.dot{display:inline-block;width:8rpx;height:8rpx;margin-right:6rpx;border-radius:50%;background:#7ea28d}
.library-block,.finish-block{margin-top:20rpx;padding-top:19rpx;border-top:1rpx solid #ece4d9}.library-title{display:flex;justify-content:space-between;align-items:flex-end;gap:12rpx}.library-title view{display:flex;flex-direction:column;gap:6rpx}.library-title view text:last-child{font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:700;letter-spacing:-1rpx}.library-title>text{color:#958a7f;font-size:16rpx}.pattern-scroll,.channel-scroll{white-space:nowrap;margin:15rpx -4rpx -4rpx}.pattern-card{position:relative;isolation:isolate;display:flex;flex:0 0 174rpx;flex-direction:column;min-height:195rpx;overflow:hidden;padding:17rpx;border:1rpx solid #e6ddd1;border-radius:18rpx;background:linear-gradient(145deg,#fffefa,#f7f2e9);box-sizing:border-box}.pattern-card::before{content:"";position:absolute;z-index:-1;right:-30rpx;top:-30rpx;width:116rpx;height:116rpx;border-radius:50%;background:var(--tone);opacity:.17}.pattern-card.active{border-color:var(--tone);box-shadow:0 10rpx 18rpx rgba(89,70,50,.11)}.pattern-mark{display:grid;place-items:center;width:46rpx;height:46rpx;margin-bottom:9rpx;border-radius:13rpx;background:rgba(255,255,255,.65);color:var(--tone);font-family:"Songti SC","STSong",serif;font-size:28rpx}.pattern-card text:nth-child(2){color:#95897c;font-size:14rpx}.pattern-card text:nth-child(3){margin-top:6rpx;color:#403931;font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:700}.pattern-card text:nth-child(4){margin-top:3rpx;color:#a09589;font-size:13rpx;letter-spacing:.7rpx}.pattern-card text:last-child{margin-top:auto;color:#876c5d;font-size:15rpx;font-weight:800}
.product-picker{margin-top:15rpx;padding:13rpx;border:1rpx solid #e5ddd2;border-radius:17rpx;background:linear-gradient(145deg,#fbfaf6,#f2f5ef)}.product-picker>text:first-child{display:block;color:#5d7568;font-size:15rpx;font-weight:900;letter-spacing:1.3rpx}.product-scroll{margin:10rpx -2rpx 0;white-space:nowrap}.product-row{display:flex;gap:8rpx}.product-choice{display:flex;flex:0 0 auto;align-items:center;gap:7rpx;min-width:135rpx;padding:8rpx 9rpx;border:1rpx solid #e5ddd2;border-radius:12rpx;background:#fffefa;color:#7a7065}.product-choice>text{display:grid;place-items:center;width:28rpx;height:28rpx;border-radius:8rpx;background:#eee7dc;color:#8a725e;font-family:"Songti SC","STSong",serif;font-size:17rpx;font-weight:800}.product-choice>view{display:flex;flex-direction:column;gap:2rpx}.product-choice>view text:first-child{color:#4a443c;font-size:16rpx;font-weight:900}.product-choice>view text:last-child{color:#978c80;font-size:12rpx}.product-choice.active{border-color:#8da899;background:#edf4ec;box-shadow:0 5rpx 11rpx rgba(83,69,51,.07)}.product-choice.active>text{background:#5d7d70;color:#fff}.product-note{display:block;margin-top:10rpx;color:#777c72;font-size:15rpx;line-height:1.55}.material-caption{display:flex;justify-content:space-between;gap:10rpx;margin-top:13rpx;padding:9rpx 10rpx;border-left:3rpx solid #8ba798;border-radius:0 11rpx 11rpx 0;background:#f3f6f0}.material-caption text:first-child{color:#59645b;font-size:16rpx;font-weight:800}.material-caption text:last-child{color:#668274;font-size:14rpx;font-weight:900;text-align:right}.material-grid{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:12rpx}.material-choice{display:grid;grid-template-columns:26rpx 1fr 16rpx;align-items:center;gap:9rpx;min-height:67rpx;box-sizing:border-box;padding:12rpx;border:1rpx solid #e6ddd2;border-radius:14rpx;background:#fffefa}.material-choice.active{border-color:#9ab0a2;background:#f0f5f0}.material-swatch{width:23rpx;height:23rpx;border:1rpx solid rgba(91,78,62,.13);border-radius:7rpx;box-shadow:inset 0 1rpx 3rpx rgba(255,255,255,.56)}.material-choice view text{display:block}.material-choice view text:first-child{font-size:19rpx;font-weight:800}.material-choice view text:last-child{margin-top:3rpx;color:#958b81;font-size:13rpx;line-height:1.3}.material-choice>text:last-child{color:transparent;font-size:17rpx}.material-choice.active>text:last-child{color:#5c8170}.finish-controls{display:grid;gap:7rpx;margin-top:13rpx;padding:14rpx;border:1rpx solid #e7dfd5;border-radius:17rpx;background:linear-gradient(145deg,#fbfaf6,#f1f4ef)}.finish-row>view{display:flex;justify-content:space-between;color:#6f665c;font-size:17rpx;font-weight:800}.finish-row>view text:last-child{color:#52776a}.finish-row slider{margin:0 -12rpx;height:34rpx}.glaze-preview{position:relative;display:grid;place-items:center;align-content:center;min-height:162rpx;overflow:hidden;margin-top:13rpx;border:1rpx solid rgba(93,117,104,.24);border-radius:19rpx;background:radial-gradient(circle at 36% 24%,rgba(255,255,255,.88),transparent calc(10% + var(--glaze) / 8)),radial-gradient(circle at 63% 69%,var(--tone),transparent calc(17% + var(--relief) / 10)),linear-gradient(145deg,#d2ddd4,#8fa79a 52%,#d9c5b4);box-shadow:inset 0 0 12rpx rgba(40,63,54,.18)}.glaze-preview::before{content:"";position:absolute;inset:16rpx 22%;border:1rpx solid rgba(255,255,255,.44);border-radius:50%;transform:rotate(-17deg) scaleY(.72)}.glaze-preview>view{display:grid;place-items:center;width:76rpx;height:76rpx;border:1rpx solid rgba(255,255,255,.72);border-radius:50%;background:rgba(255,255,255,.16);box-shadow:inset 0 3rpx 9rpx rgba(255,255,255,.55),0 10rpx 17rpx rgba(46,65,55,.2)}.glaze-preview>view text{color:#fff;font-family:"Songti SC","STSong",serif;font-size:42rpx;text-shadow:0 2rpx 7rpx rgba(34,53,46,.26)}.glaze-preview>text{position:relative;color:#fff;text-shadow:0 1rpx 4rpx rgba(36,49,43,.4)}.glaze-preview>text:nth-child(2){margin-top:10rpx;font-size:20rpx;font-weight:800}.glaze-preview>text:last-child{margin-top:4rpx;font-size:14rpx}
.entry-section,.market-section{margin-top:24rpx;padding:24rpx;border:1rpx solid rgba(133,120,102,.14);border-radius:29rpx;background:rgba(255,253,249,.74);box-shadow:0 12rpx 30rpx rgba(70,56,39,.045)}.section-head.compact{align-items:flex-end}.section-head.compact>text{color:#958a7f;font-size:16rpx}.entry-grid{display:grid;grid-template-columns:1fr 1fr;gap:12rpx;margin-top:17rpx}.entry-card{position:relative;display:flex;flex-direction:column;min-height:177rpx;overflow:hidden;padding:16rpx;border:1rpx solid #e5ddd2;border-radius:18rpx;background:#fffefa;box-sizing:border-box}.entry-card:last-child{grid-column:span 2;min-height:144rpx}.entry-card::before{content:"";position:absolute;right:-24rpx;top:-24rpx;width:92rpx;height:92rpx;border-radius:50%;background:rgba(143,165,154,.13)}.entry-card.terracotta::before{background:rgba(185,102,79,.14)}.entry-card.ink::before{background:rgba(75,68,59,.11)}.entry-card.gold::before{background:rgba(198,163,109,.16)}.entry-card text:first-child{color:#a09080;font-size:15rpx}.entry-card text:nth-child(2){margin-top:15rpx;color:#668176;font-family:"Songti SC","STSong",serif;font-size:34rpx}.entry-card text:nth-child(3){margin-top:9rpx;color:#403a33;font-size:23rpx;font-weight:800}.entry-card text:nth-child(4){margin-top:6rpx;color:#887e73;font-size:15rpx;line-height:1.4}.entry-card text:last-child{margin-top:auto;color:#ad664e;font-size:15rpx;font-weight:800}.market-heading{display:flex;flex-direction:column;gap:8rpx}.market-heading>text:nth-child(2){font-family:"Songti SC","STSong",serif;font-size:32rpx;font-weight:700;line-height:1.28}.market-heading>text:nth-child(2) text{color:#b9664f}.market-heading>text:last-child{color:#90857a;font-size:17rpx;line-height:1.5}.channel-card{display:flex;flex:0 0 300rpx;gap:13rpx;padding:15rpx;border:1rpx solid #e7ded3;border-radius:18rpx;background:#fffefa;box-sizing:border-box}.channel-card>text{display:grid;place-items:center;width:46rpx;height:46rpx;border-radius:13rpx;background:linear-gradient(145deg,#e9f0e9,#a8bdaf);color:#4c675c;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:700}.channel-card:nth-child(2)>text{background:linear-gradient(145deg,#f1e2d7,#c98c76);color:#714839}.channel-card:nth-child(3)>text{background:linear-gradient(145deg,#f1ead8,#c9ae78);color:#6f5935}.channel-card view{display:flex;flex:1;flex-direction:column;min-width:0}.channel-card view text:first-child{color:#789488;font-size:14rpx}.channel-card view text:nth-child(2){margin-top:3rpx;color:#3e3933;font-size:22rpx;font-weight:800}.channel-card view text:nth-child(3){margin-top:5rpx;color:#80766b;font-size:15rpx;line-height:1.4}.channel-card view text:last-child{margin-top:7rpx;color:#9b705e;font-size:14rpx}.case-head{display:flex;align-items:flex-end;justify-content:space-between;gap:12rpx;margin-top:24rpx}.case-head>view{display:flex;flex-direction:column;gap:6rpx}.case-head>view text:last-child{font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:700;letter-spacing:-1rpx}.case-head>text{padding:5rpx 8rpx;border:1rpx solid #e5ddd2;border-radius:99rpx;background:#fffdfa;color:#93877c;font-size:14rpx}.case-scroll{margin:14rpx -4rpx -3rpx;white-space:nowrap}.case-row{display:flex;gap:11rpx}.case-card{display:flex;flex:0 0 408rpx;gap:13rpx;overflow:hidden;padding:14rpx;border:1rpx solid #e6ddd2;border-radius:18rpx;background:#fffefa;box-sizing:border-box}.case-cover{position:relative;display:flex;flex:0 0 98rpx;flex-direction:column;align-items:center;justify-content:center;gap:8rpx;overflow:hidden;border-radius:14rpx;background:radial-gradient(circle at 35% 22%,rgba(255,255,255,.8),transparent 25%),linear-gradient(145deg,var(--tone),#e5cfbe)}.case-cover::after{content:"";position:absolute;bottom:-22rpx;left:-12rpx;width:126rpx;height:36rpx;border-radius:50%;background:rgba(40,55,48,.19);filter:blur(8rpx)}.case-cover text{position:relative;z-index:1;color:#fff;font-family:"Songti SC","STSong",serif;font-size:42rpx;text-shadow:0 2rpx 7rpx rgba(36,53,44,.24)}.case-cover text:last-child{font-family:-apple-system,BlinkMacSystemFont,"PingFang SC","Microsoft YaHei",sans-serif;font-size:12rpx;letter-spacing:.5rpx}.case-card>view:last-child{display:flex;min-width:0;flex-direction:column;justify-content:center}.case-card>view:last-child text:first-child{overflow:hidden;color:#403a33;font-family:"Songti SC","STSong",serif;font-size:24rpx;font-weight:700;text-overflow:ellipsis;white-space:nowrap}.case-card>view:last-child text:nth-child(2){display:-webkit-box;overflow:hidden;margin-top:7rpx;color:#81776c;font-size:15rpx;line-height:1.45;-webkit-box-orient:vertical;-webkit-line-clamp:2}.case-card>view:last-child text:last-child{margin-top:8rpx;color:#8d6554;font-size:14rpx;font-weight:800}.case-note{display:block;margin-top:11rpx;color:#978c81;font-size:14rpx;line-height:1.5}.rank-card{margin-top:17rpx;padding:16rpx;border:1rpx solid #e6ddd2;border-radius:20rpx;background:#fbf9f5}.rank-head{display:flex;justify-content:space-between;gap:10rpx;padding-bottom:12rpx;border-bottom:1rpx solid #ebe3d9}.rank-head>view:first-child{display:flex;flex-direction:column;gap:4rpx}.rank-head>view:first-child text:first-child{color:#9b8269;font-size:14rpx;font-weight:900;letter-spacing:1.7rpx}.rank-head>view:first-child text:nth-child(2){font-family:"Songti SC","STSong",serif;font-size:28rpx;font-weight:700}.rank-head>view:first-child text:last-child{color:#91877c;font-size:14rpx}.rank-tabs{display:flex;align-items:flex-start;gap:4rpx}.rank-tabs text{padding:6rpx 8rpx;border:1rpx solid #e4dcd2;border-radius:9rpx;background:#fffdfa;color:#8b8177;font-size:15rpx}.rank-tabs text.active{border-color:#4c4038;background:#4c4038;color:#fff}.rank-row{display:grid;grid-template-columns:30rpx 1fr auto;align-items:center;gap:9rpx;padding:13rpx 2rpx;border-bottom:1rpx solid #eee7df}.rank-row>text{color:#a88869;font-size:17rpx;font-weight:900}.rank-row>view:nth-child(2){min-width:0}.rank-row>view:nth-child(2)>text:first-child{display:block;overflow:hidden;color:#413a33;font-size:19rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.rank-row>view:nth-child(2)>text:nth-child(2){display:block;margin-top:3rpx;color:#91877d;font-size:14rpx}.rank-row>view:nth-child(2)>view{height:5rpx;overflow:hidden;margin-top:7rpx;border-radius:99rpx;background:#ebe5dd}.rank-row>view:nth-child(2)>view text{display:block;height:100%;border-radius:inherit;background:#b89570}.rank-row>view:last-child{display:flex;flex-direction:column;align-items:flex-end;gap:3rpx}.rank-row>view:last-child text:first-child{color:#4e433b;font-size:18rpx;font-weight:800}.rank-row>view:last-child text:last-child{color:#937256;font-size:14rpx;font-weight:800}.rank-note{display:block;margin-top:12rpx;padding:11rpx;border-left:3rpx solid #b89570;border-radius:0 10rpx 10rpx 0;background:#f4efe9;color:#766b61;font-size:16rpx;line-height:1.55}.bottom-nav{position:fixed;z-index:10;bottom:0;left:0;right:0;display:flex;justify-content:space-around;padding:17rpx 8rpx calc(17rpx + env(safe-area-inset-bottom));border-top:1rpx solid rgba(99,87,71,.12);background:rgba(255,253,249,.95);box-shadow:0 -9rpx 25rpx rgba(61,50,37,.11);backdrop-filter:blur(13rpx)}.bottom-nav view{display:flex;flex-direction:column;align-items:center;gap:4rpx;min-width:82rpx;color:#8d8277;font-size:27rpx}.bottom-nav view text:last-child{font-size:15rpx}.bottom-nav .active{color:#5f796d}.bottom-nav .active text:first-child{font-weight:900}
.material-caption{align-items:center}.material-caption>view{display:flex;min-width:0;flex:1;flex-direction:column;gap:3rpx}.material-caption>view text:first-child{color:#59645b;font-size:16rpx;font-weight:800}.material-caption>view text:last-child{color:#668274;font-size:14rpx;font-weight:900}.material-toggle{flex:0 0 auto;height:48rpx;margin:0;padding:0 11rpx;border:1rpx solid #cddccf;border-radius:10rpx;background:#fffefa;color:#567565;font-size:14rpx;font-weight:900}.material-toggle::after{border:0}.material-scope-tip{display:block;margin-top:10rpx;color:#7e766c;font-size:14rpx;line-height:1.52}.material-choice{grid-template-columns:26rpx minmax(0,1fr) auto}.material-choice .material-recommended,.material-choice .material-cross{justify-self:end;align-self:start;margin:0!important;padding:4rpx 6rpx;border-radius:99rpx;font-size:12rpx!important;font-weight:900!important;line-height:1.1}.material-choice .material-recommended{color:#557867!important;background:#e4f1e5}.material-choice .material-cross{color:#93735f!important;background:#f4ece4}
</style>
