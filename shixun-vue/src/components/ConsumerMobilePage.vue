<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import type { User } from '../types'
import andTasteLogo from '../assets/and_taste.png'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error']; logout: [] }>()

type Tab = 'image' | 'model' | 'gallery'
type Phase = 'idle' | 'optimize' | 'generate' | 'save' | 'done'

const tab = ref<Tab>('image')
const busy = ref(false)
const stage = ref('')
const phase = ref<Phase>('idle')
const imageConfig = ref<any>({})
const tripoConfig = ref<any>({})
const assets = ref<any[]>([])
const imageResult = ref<any>(null)
const modelResult = ref<any>(null)
const imagePreviewUrl = ref('')
const uploadPreviewUrl = ref('')
const modelProgress = ref(0)
const modelTimer = ref<ReturnType<typeof setTimeout> | null>(null)
const imageAnchor = ref<HTMLElement | null>(null)
const modelAnchor = ref<HTMLElement | null>(null)

const imageForm = reactive({
  rawPrompt: '一款适合年轻游客的城市味道文创礼盒，温暖、精致、有官方文创质感',
  prompt: '',
  usageGuide: '',
  style: '官方文创',
  imagenAspectRatio: '1:1',
  imagenImageSize: '1K',
  imagenOutputFormat: 'png',
})

const modelForm = reactive({
  mode: 'image_to_model' as 'image_to_model' | 'text_to_model',
  rawPrompt: '山城街巷主题亚克力钥匙扣，立体浮雕层次，适合文创打样',
  prompt: '',
  inputAssetId: null as number | null,
})

const recentImages = computed(() => assets.value.filter(x => x.assetType === 'image').slice(0, 8))
const recentModels = computed(() => assets.value.filter(x => x.assetType === 'model').slice(0, 8))
const canGenerateModel = computed(() => modelForm.mode === 'image_to_model' ? !!modelForm.inputAssetId : !!modelForm.rawPrompt.trim())
const flowLabel = computed(() => tab.value === 'image' ? 'Qwen优化 -> Google Imagen 4 -> 自动保存' : 'Qwen优化 -> Tripo建模 -> 自动保存')
const serviceReadyText = computed(() => tab.value === 'image'
  ? (imageConfig.value.configured ? 'Google Imagen 4 已就绪' : 'Google Imagen 4 未配置')
  : (tripoConfig.value.configured && tripoConfig.value.serviceReachable ? 'Tripo 已就绪' : 'Tripo 未就绪'))

function setStage(text: string, nextPhase: Phase) {
  stage.value = text
  phase.value = nextPhase
}

onMounted(load)
onBeforeUnmount(() => {
  if (modelTimer.value) clearTimeout(modelTimer.value)
  if (imagePreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(imagePreviewUrl.value)
  if (uploadPreviewUrl.value.startsWith('blob:')) URL.revokeObjectURL(uploadPreviewUrl.value)
})

async function json(url: string) {
  const r = await fetch(url, { cache: 'no-store' })
  if (!r.ok) throw new Error(`HTTP ${r.status}`)
  return await r.json()
}

async function load() {
  try {
    const [i, t, a] = await Promise.all([
      json('/api/creative/ai/imagen/config'),
      json('/api/creative/ai/tripo/config'),
      json('/api/creative/ai/assets'),
    ])
    imageConfig.value = i
    tripoConfig.value = t
    assets.value = Array.isArray(a) ? a : []
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
    body: JSON.stringify({ prompt: source, provider: 'imagen' }),
  })
  if (!r.ok) {
    const err = await r.json().catch(() => null)
    throw new Error(err?.message || `HTTP ${r.status}`)
  }
  const d = await r.json()
  imageForm.prompt = d.prompt || source
  imageForm.usageGuide = d.usageGuide || ''
}

async function generateImage() {
  if (!imageForm.rawPrompt.trim()) {
    emit('alert', '先写一句你想做什么产品', 'error')
    return
  }
  if (!imageConfig.value.configured) {
    emit('alert', 'Google Imagen 4 未配置，先检查服务器 REPLICATE_API_KEY', 'error')
    return
  }
  busy.value = true
  imageResult.value = null
  setStage('Qwen正在优化图片提示词', 'optimize')
  try {
    await optimizeImagePrompt()
    setStage('Google Imagen 4 正在生成产品图', 'generate')
    const r = await fetch('/api/creative/ai/imagen/text-to-image', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'imagen',
        rawPrompt: imageForm.rawPrompt,
        prompt: imageForm.prompt,
        imagenAspectRatio: imageForm.imagenAspectRatio,
        imagenImageSize: imageForm.imagenImageSize,
        imagenOutputFormat: imageForm.imagenOutputFormat,
      }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const d = await r.json()
    imageResult.value = d
    setStage('正在保存并回传图片', 'save')
    await prepareAssetPreview(d.assetId, 'image')
    await load()
    await nextTick()
    imageAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    phase.value = 'done'
    emit('alert', '图片已生成并保存到资产库', 'success')
  } catch (e: any) {
    phase.value = 'idle'
    emit('alert', '生成图片失败：' + (e?.message || e), 'error')
  } finally {
    busy.value = false
    stage.value = ''
  }
}

async function uploadReference(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  busy.value = true
  setStage('正在上传参考图', 'save')
  try {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('title', 'C端3D参考图')
    fd.append('tags', 'C端,3D参考图')
    const r = await fetch('/api/creative/ai/assets/upload', { method: 'POST', body: fd })
    if (!r.ok) throw new Error(await r.text())
    const d = await r.json()
    modelForm.inputAssetId = d.assetId
    await prepareAssetPreview(d.assetId, 'upload')
    emit('alert', '参考图已上传', 'success')
  } catch (e: any) {
    emit('alert', '上传失败：' + (e?.message || e), 'error')
  } finally {
    busy.value = false
    stage.value = ''
    phase.value = 'idle'
  }
}

async function optimizeModelPrompt() {
  if (!modelForm.rawPrompt.trim()) return
  const r = await fetch('/api/creative/ai/prompt/tripo-3d-optimize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt: modelForm.rawPrompt, promptTemplate: 'universal' }),
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
    emit('alert', 'Tripo 未配置或暂不可用', 'error')
    return
  }
  if (!canGenerateModel.value) {
    emit('alert', modelForm.mode === 'image_to_model' ? '先上传一张产品参考图' : '先写一句模型描述', 'error')
    return
  }
  busy.value = true
  modelResult.value = null
  modelProgress.value = 0
  setStage('Qwen正在优化3D提示词', 'optimize')
  try {
    await optimizeModelPrompt()
    setStage('正在自动提交 Tripo 任务', 'generate')
    const body = {
      mode: modelForm.mode,
      modelVersion: tripoConfig.value.modelVersion || 'v3.1-20260211',
      promptTemplate: 'universal',
      rawPrompt: modelForm.rawPrompt,
      prompt: modelForm.prompt || modelForm.rawPrompt,
      negativePrompt: 'low poly, blurry, flat texture, deformed, asymmetric, noisy mesh',
      inputAssetId: modelForm.inputAssetId,
      multiviewAssetIds: { front: null, left: null, back: null, right: null },
      exportFormats: 'GLB',
      texture: true,
      pbr: true,
      textureQuality: 'detailed',
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
    emit('alert', '3D任务已提交', 'success')
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
    setStage(d.status === 'succeeded' ? '3D模型已完成' : `Tripo 正在建模 ${modelProgress.value || 0}%`, d.status === 'succeeded' ? 'save' : 'generate')
    if (d.status === 'succeeded') {
      modelResult.value = d
      busy.value = false
      stage.value = ''
      await load()
      await nextTick()
      modelAnchor.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      phase.value = 'done'
      emit('alert', '3D模型已生成并保存', 'success')
      return
    }
    if (d.status === 'failed') throw new Error(d.errorMessage || 'Tripo任务失败')
    modelTimer.value = setTimeout(() => pollModel(jobId), 3000)
  } catch (e: any) {
    busy.value = false
    stage.value = ''
    phase.value = 'idle'
    emit('alert', '查询3D任务失败：' + (e?.message || e), 'error')
  }
}

function openUrl(url?: string) {
  if (!url) return
  window.open(url, '_blank', 'noopener,noreferrer')
}
</script>

<template>
  <main class="consumer-shell">
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

    <section class="hero">
      <span>{{ props.currentUser.username }}</span>
      <h1>把一个想法变成官方感文创产品</h1>
      <p>输入一句话，系统会先自动优化提示词，再直接交给 Google Imagen 4 或 Tripo 生成，并回传到作品库。</p>
      <div class="hero-actions">
        <button type="button" @click="tab='image'">生成图片</button>
        <button type="button" @click="tab='model'">生成3D</button>
      </div>
    </section>

    <section class="workflow-strip">
      <div class="workflow-chip" :class="{ active: phase === 'optimize' }"><b>01</b><span>Qwen</span></div>
      <div class="workflow-arrow">→</div>
      <div class="workflow-chip" :class="{ active: phase === 'generate' }"><b>02</b><span>{{ tab === 'image' ? 'Google' : 'Tripo' }}</span></div>
      <div class="workflow-arrow">→</div>
      <div class="workflow-chip" :class="{ active: phase === 'save' || phase === 'done' }"><b>03</b><span>保存</span></div>
    </section>

    <nav class="quick-tabs">
      <button type="button" :class="{active:tab==='image'}" @click="tab='image'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m8 13 2.5-2.5L15 15l1-1 3 3"/><circle cx="8" cy="9" r="1"/></svg>
        图片
      </button>
      <button type="button" :class="{active:tab==='model'}" @click="tab='model'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m12 2 8 4.5v9L12 20l-8-4.5v-9L12 2Z"/><path d="M12 11 4.5 6.8"/><path d="M12 11v9"/><path d="m12 11 7.5-4.2"/></svg>
        3D
      </button>
      <button type="button" :class="{active:tab==='gallery'}" @click="tab='gallery'">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
        作品
      </button>
    </nav>

    <section v-if="tab==='image'" class="panel">
      <div class="section-head">
        <span>AI IMAGE</span>
        <b>产品图生成</b>
      </div>
      <div class="service-pill">
        <i></i>
        <span>服务商</span>
        <b>Google Imagen 4</b>
        <em>{{ serviceReadyText }}</em>
      </div>
      <label>
        <span>你想做什么</span>
        <textarea v-model="imageForm.rawPrompt" rows="5" placeholder="例如：江西博物馆主题冰箱贴，青铜器纹样，年轻人喜欢，精致伴手礼"></textarea>
      </label>
      <div class="chips">
        <button type="button" :class="{active:imageForm.style==='官方文创'}" @click="imageForm.style='官方文创'">官方文创</button>
        <button type="button" :class="{active:imageForm.style==='国潮精致'}" @click="imageForm.style='国潮精致'">国潮精致</button>
        <button type="button" :class="{active:imageForm.style==='可爱潮玩'}" @click="imageForm.style='可爱潮玩'">可爱潮玩</button>
      </div>
      <div class="chips compact">
        <button type="button" :class="{active:imageForm.imagenAspectRatio==='1:1'}" @click="imageForm.imagenAspectRatio='1:1'">方图</button>
        <button type="button" :class="{active:imageForm.imagenAspectRatio==='9:16'}" @click="imageForm.imagenAspectRatio='9:16'">手机海报</button>
        <button type="button" :class="{active:imageForm.imagenAspectRatio==='16:9'}" @click="imageForm.imagenAspectRatio='16:9'">横版</button>
      </div>
      <div class="mini-note">{{ flowLabel }}</div>
      <button type="button" class="primary" :disabled="busy" @click="generateImage">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M13 2 3 14h9l-1 8 10-12h-9l1-8Z"/></svg>
        {{ busy && tab==='image' ? stage || '正在生成' : '一键生成并保存图片' }}
      </button>

      <article v-if="imageResult" ref="imageAnchor" class="result-card">
        <img v-if="imagePreviewUrl" :src="imagePreviewUrl" alt="生成图片" />
        <div class="result-info">
          <b>已保存到作品库</b>
          <p v-if="imageForm.usageGuide">{{ imageForm.usageGuide }}</p>
          <a v-if="imageResult.imageUrl || imageResult.fileUrl" :href="imageResult.imageUrl || imageResult.fileUrl" target="_blank" rel="noopener">查看原图</a>
        </div>
      </article>
    </section>

    <section v-if="tab==='model'" class="panel">
      <div class="section-head">
        <span>AI MODEL</span>
        <b>轻量3D建模</b>
      </div>
      <div class="service-pill teal">
        <i></i>
        <span>服务商</span>
        <b>Tripo</b>
        <em>{{ serviceReadyText }}</em>
      </div>
      <div class="mode-switch">
        <button type="button" :class="{active:modelForm.mode==='image_to_model'}" @click="modelForm.mode='image_to_model'">拍照/上传生成</button>
        <button type="button" :class="{active:modelForm.mode==='text_to_model'}" @click="modelForm.mode='text_to_model'">文字生成</button>
      </div>
      <div class="mini-note">只保留简单输入，系统自动先优化提示词，再提交 Tripo。</div>

      <label v-if="modelForm.mode==='image_to_model'" class="upload-box">
        <input type="file" accept="image/*" @change="uploadReference" />
        <img v-if="uploadPreviewUrl" :src="uploadPreviewUrl" alt="3D参考图" />
        <span v-else>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14"/><path d="M5 12h14"/></svg>
          上传产品图
        </span>
      </label>

      <label>
        <span>{{ modelForm.mode === 'text_to_model' ? '模型描述' : '补充要求' }}</span>
        <textarea v-model="modelForm.rawPrompt" rows="4" placeholder="例如：做成钥匙扣，边缘圆润，有浮雕层次，适合打样"></textarea>
      </label>

      <button type="button" class="primary green" :disabled="busy || !canGenerateModel" @click="generateModel">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m12 2 8 4.5v9L12 20l-8-4.5v-9L12 2Z"/></svg>
        {{ busy && tab==='model' ? stage || '正在生成' : '一键生成并保存3D' }}
      </button>
      <div v-if="busy && tab==='model'" class="progress">
        <span :style="{ width: `${Math.max(12, modelProgress)}%` }"></span>
      </div>

      <article v-if="modelResult" ref="modelAnchor" class="result-card">
        <img v-if="modelResult.previewUrl" :src="modelResult.previewUrl" alt="3D模型预览" />
        <div class="result-info">
          <b>3D模型已生成</b>
          <button type="button" @click="openUrl(modelResult.modelUrl)">打开模型文件</button>
        </div>
      </article>
    </section>

    <section v-if="tab==='gallery'" class="panel">
      <div class="section-head">
        <span>MY WORKS</span>
        <b>最近作品</b>
      </div>
      <div class="mini-note">图片和3D结果都会自动回到作品库，方便手机直接查看。</div>
      <div class="gallery">
        <article v-for="a in recentImages" :key="`img-${a.id}`">
          <img :src="a.previewUrl || a.fileUrl" alt="作品图片" />
          <b>{{ a.title || 'AI图片' }}</b>
        </article>
        <article v-for="a in recentModels" :key="`model-${a.id}`">
          <div class="model-tile">3D</div>
          <b>{{ a.title || 'AI模型' }}</b>
          <button type="button" @click="openUrl(a.fileUrl)">打开</button>
        </article>
      </div>
      <p v-if="!recentImages.length && !recentModels.length" class="empty">暂无作品</p>
    </section>
  </main>
</template>

<style scoped>
.consumer-shell{min-height:100vh;background:#f6f2ea;color:#201a17;padding:14px 14px 96px;font-family:Inter,"PingFang SC",system-ui,sans-serif}.consumer-top{position:sticky;top:0;z-index:10;display:flex;align-items:center;justify-content:space-between;margin:-14px -14px 10px;padding:12px 14px;background:rgba(246,242,234,.86);backdrop-filter:blur(18px);border-bottom:1px solid rgba(120,92,64,.12)}.brand{display:flex;align-items:center;gap:9px}.brand img{width:34px;height:34px;border-radius:8px;object-fit:cover}.brand b,.brand span{display:block}.brand b{font-size:15px}.brand span{font-size:11px;color:#8a7161}.icon-btn{width:38px;height:38px;border:0;border-radius:8px;background:#fff;color:#4b3327;box-shadow:0 6px 18px rgba(69,45,26,.08)}.icon-btn svg,.primary svg,.quick-tabs svg,.upload-box svg{width:18px;height:18px}.hero{position:relative;min-height:172px;padding:24px 18px;border-radius:8px;background:radial-gradient(circle at 84% 16%,rgba(255,255,255,.2),transparent 24%),linear-gradient(135deg,#2a1c16,#8e402b 62%,#c27643);color:#fff;display:flex;flex-direction:column;justify-content:flex-end;box-shadow:0 18px 42px rgba(90,54,31,.22);overflow:hidden}.hero:after{content:"";position:absolute;right:18px;top:16px;width:92px;height:92px;border-radius:50%;background:rgba(255,255,255,.12);box-shadow:-26px 46px 0 rgba(255,255,255,.08)}.hero>*{position:relative;z-index:1}.hero span{width:max-content;padding:5px 9px;border-radius:999px;background:rgba(255,255,255,.16);font-size:11px}.hero h1{margin:12px 0 15px;font-size:28px;line-height:1.08;letter-spacing:0}.hero-actions{display:flex;gap:9px}.hero-actions button{height:38px;padding:0 14px;border:1px solid rgba(255,255,255,.34);border-radius:8px;background:rgba(255,255,255,.14);color:#fff;font-weight:800}.quick-tabs{position:fixed;left:14px;right:14px;bottom:14px;z-index:20;display:grid;grid-template-columns:repeat(3,1fr);gap:6px;padding:7px;border:1px solid rgba(120,92,64,.14);border-radius:8px;background:rgba(255,255,255,.9);backdrop-filter:blur(18px);box-shadow:0 18px 50px rgba(57,38,26,.16)}.quick-tabs button{height:48px;border:0;border-radius:8px;background:transparent;color:#8a7161;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:2px;font-size:11px;font-weight:800}.quick-tabs button.active{background:#201a17;color:#fff}.panel{margin-top:12px;padding:15px;border-radius:8px;background:#fff;box-shadow:0 12px 32px rgba(77,51,31,.08);border:1px solid rgba(120,92,64,.1)}.section-head{display:flex;align-items:flex-end;justify-content:space-between;margin-bottom:13px}.section-head span{font-size:10px;font-weight:900;letter-spacing:1.6px;color:#b4532a}.section-head b{font-size:18px}label{display:block;margin-top:12px}label>span{display:block;margin-bottom:7px;font-size:13px;font-weight:800;color:#4a3429}textarea{width:100%;box-sizing:border-box;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4;padding:12px;color:#241a16;font-size:15px;line-height:1.55;resize:vertical;outline:none}textarea:focus{border-color:#b4532a;box-shadow:0 0 0 3px rgba(180,83,42,.12)}.chips{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:10px}.chips.compact{grid-template-columns:repeat(3,1fr)}.chips button,.mode-switch button{min-height:38px;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4;color:#6e5547;font-weight:800}.chips button.active,.mode-switch button.active{border-color:#201a17;background:#201a17;color:#fff}.primary{width:100%;height:52px;margin-top:14px;border:0;border-radius:8px;background:#b4532a;color:#fff;font-size:16px;font-weight:900;display:flex;align-items:center;justify-content:center;gap:8px;box-shadow:0 12px 26px rgba(180,83,42,.24)}.primary.green{background:#0f766e;box-shadow:0 12px 26px rgba(15,118,110,.2)}.primary:disabled{opacity:.55}.result-card{overflow:hidden;margin-top:14px;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4}.result-card>img{display:block;width:100%;max-height:480px;object-fit:contain;background:#211814}.result-info{padding:12px}.result-info b{display:block;margin-bottom:5px}.result-info p{margin:0 0 10px;white-space:pre-wrap;color:#6e5547;font-size:13px;line-height:1.6}.result-info a,.result-info button{display:inline-flex;height:34px;align-items:center;padding:0 12px;border:0;border-radius:8px;background:#201a17;color:#fff;text-decoration:none;font-weight:800}.mode-switch{display:grid;grid-template-columns:1fr 1fr;gap:8px}.upload-box{position:relative;min-height:170px;border:1px dashed #c7a995;border-radius:8px;background:#fffaf4;display:flex;align-items:center;justify-content:center;overflow:hidden}.upload-box input{position:absolute;inset:0;opacity:0}.upload-box img{width:100%;height:220px;object-fit:cover}.upload-box span{display:flex;align-items:center;gap:8px;color:#8a7161;font-weight:900}.progress{height:8px;margin-top:12px;border-radius:999px;background:#e9ded2;overflow:hidden}.progress span{display:block;height:100%;border-radius:999px;background:#0f766e;transition:width .25s ease}.gallery{display:grid;grid-template-columns:1fr 1fr;gap:10px}.gallery article{overflow:hidden;border:1px solid #eadfd4;border-radius:8px;background:#fffaf4}.gallery img,.model-tile{width:100%;aspect-ratio:1/1;object-fit:cover;background:#201a17;color:#fff}.model-tile{display:flex;align-items:center;justify-content:center;font-size:28px;font-weight:950}.gallery b{display:block;padding:9px;font-size:12px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.gallery button{margin:0 9px 9px;height:30px;border:0;border-radius:8px;background:#201a17;color:#fff;font-weight:800}.empty{padding:40px 0;text-align:center;color:#8a7161}@media(min-width:720px){.consumer-shell{display:block;max-width:460px;margin:0 auto;box-shadow:0 0 0 1px rgba(120,92,64,.08),0 24px 80px rgba(40,28,22,.15)}.quick-tabs{left:50%;right:auto;width:432px;transform:translateX(-50%)}}
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
