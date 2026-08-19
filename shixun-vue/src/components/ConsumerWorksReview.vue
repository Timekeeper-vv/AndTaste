<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'
import { requestAssetPreviewUrl } from '../utils/assetAccess'

const props = defineProps<{ currentUser: User; mode?: 'standard' | 'professional' | 'multiview' }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

type ReviewStatus = 'review' | 'approved' | 'rejected'

interface ConsumerAsset {
  id: number
  assetNo?: string
  title?: string
  assetType?: 'image' | 'model' | string
  sourceType?: string
  fileUrl?: string
  previewUrl?: string
  prompt?: string
  status?: ReviewStatus | string
  format?: string
  tags?: string
  createdBy?: number
  createdByName?: string
  createdAt?: string
}

interface MultiViewBundle {
  id: number
  bundleNo?: string
  userId?: number
  username?: string
  productName?: string
  material?: string
  productSize?: string
  viewCount?: number
  status?: string
  purpose?: string
  museumName?: string
  campaignKey?: string
  reviewComment?: string
  createdAt?: string
  images?: Array<{ view?: string; label?: string; assetId?: number; previewUrl?: string; imageUrl?: string }>
}

const works = ref<ConsumerAsset[]>([])
const multiviewBundles = ref<MultiViewBundle[]>([])
const loading = ref(false)
const reviewingId = ref<number | null>(null)
const reviewingBundleId = ref<number | null>(null)
const keywordUserId = ref('')
const status = ref<'all' | ReviewStatus>('review')
const activeWork = ref<ConsumerAsset | null>(null)
const activeMediaUrl = ref('')
const professionalSubmissions = ref<any[]>([])
const reviewingSubmissionId = ref<number | null>(null)
const rejectionTarget = ref<{ kind: 'work' | 'bundle' | 'professional'; item: any } | null>(null)
const rejectionReason = ref('')
const activeBundleImage = ref<{ url: string; label: string } | null>(null)

const reviewMode = computed(() => props.mode || 'standard')
const isProfessionalMode = computed(() => reviewMode.value === 'professional')
const isMultiviewMode = computed(() => reviewMode.value === 'multiview')
const isStandardMode = computed(() => reviewMode.value === 'standard')

const activeMultiViewBundles = computed(() => multiviewBundles.value.filter(bundle => bundle.status !== 'archived'))
const visibleWorks = computed(() => {
  const bundleAssets = new Set(activeMultiViewBundles.value.flatMap(bundle => (bundle.images || []).map(item => String(item.assetId))))
  return works.value.filter(work => work.assetType === 'model' && !bundleAssets.has(String(work.id)))
})
const visibleMultiViewBundles = computed(() => status.value === 'all'
  ? activeMultiViewBundles.value
  : activeMultiViewBundles.value.filter(bundle => bundle.status === status.value))
const visibleProfessionalSubmissions = computed(() => status.value === 'all'
  ? professionalSubmissions.value
  : professionalSubmissions.value.filter(item => item.status === status.value))
const stats = computed(() => {
  const source = isProfessionalMode.value ? visibleProfessionalSubmissions.value : isMultiviewMode.value ? visibleMultiViewBundles.value : visibleWorks.value
  const total = source.length
  const review = source.filter(x => x.status === 'review').length
  const approved = source.filter(x => x.status === 'approved').length
  const rejected = source.filter(x => x.status === 'rejected').length
  return { total, review, approved, rejected }
})

const statusText: Record<string, string> = {
  review: '待审核',
  approved: '已通过',
  rejected: '未通过',
  draft: '草稿',
}

const statusClass = (s?: string) => s === 'approved' ? 'ok' : s === 'rejected' ? 'bad' : 'wait'
const assetTypeText = (t?: string) => t === 'model' ? '3D模型' : '产品图片'
const previewUrl = (w: ConsumerAsset) => w.previewUrl || w.fileUrl || ''
function purposeOf(w: ConsumerAsset): 'museum_sale' | 'personal' | 'unknown' {
  const t = `${w.tags || ''} ${w.prompt || ''}`
  if (t.includes('用途=museum_sale') || t.includes('博物馆售卖') || t.includes('博物馆审批')) return 'museum_sale'
  if (t.includes('用途=personal') || t.includes('个人收藏') || t.includes('送礼') || t.includes('作品审核')) return 'personal'
  return 'unknown'
}
function purposeText(w: ConsumerAsset) {
  const p = purposeOf(w)
  return p === 'museum_sale' ? '博物馆售卖' : p === 'personal' ? '个人收藏/送礼' : '未标明用途'
}
function approvalSource(w: ConsumerAsset) {
  const matched = /审批出处=([^;]+)/.exec(String(w.tags || ''))
  return matched?.[1]?.trim() || ''
}
function campaignOf(w: ConsumerAsset) {
  const matched = /活动投稿=([^;]+)/.exec(String(w.tags || ''))
  return matched?.[1] === 'museum_summer_gift_2026' ? '夏日伴手礼活动' : ''
}
function purposeClass(w: ConsumerAsset) { return purposeOf(w) === 'museum_sale' ? 'museum' : purposeOf(w) === 'personal' ? 'personal' : 'unknown' }

function formatTime(v?: string) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  try {
    if (isProfessionalMode.value) {
      await loadProfessionalSubmissions()
    } else {
      const qs = new URLSearchParams({ size: '200' })
      if (keywordUserId.value.trim()) qs.set('userId', keywordUserId.value.trim())
      if (status.value !== 'all') qs.set('status', status.value)
      const r = await fetch(`/api/creative/ai/consumer-assets/review?${qs}`, { cache: 'no-store' })
      if (!r.ok) {
        const err = await r.json().catch(() => null)
        throw new Error(err?.message || `HTTP ${r.status}`)
      }
      const data = await r.json()
      works.value = Array.isArray(data) ? data : []
    }
    if (isMultiviewMode.value) {
      await loadMultiViewBundles()
    }
  } catch (e: any) {
    emit('alert', `加载${isProfessionalMode.value ? '专业作品包' : isMultiviewMode.value ? '多视图作品包' : 'C端作品'}失败：` + (e?.message || e), 'error')
  } finally {
    loading.value = false
  }
}

async function loadMultiViewBundles() {
  try {
    const qs = new URLSearchParams({ size: '200' })
    if (keywordUserId.value.trim()) qs.set('userId', keywordUserId.value.trim())
    // Load every bundle so its child assets can be hidden from the legacy
    // single-asset list regardless of the selected status filter. The card
    // display itself is filtered by visibleMultiViewBundles below.
    const r = await fetch(`/api/creative/ai/consumer-multiview-bundles/review?${qs}`, { cache: 'no-store' })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const data = await r.json()
    multiviewBundles.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    // Keep the legacy single-asset review list usable while an older server
    // is being upgraded; the bundle endpoint is required after deployment.
    multiviewBundles.value = []
    emit('alert', '加载三视图作品包失败：' + (e?.message || e), 'error')
  }
}

function bundleImageUrl(item: NonNullable<MultiViewBundle['images']>[number]) {
  return item?.previewUrl || item?.imageUrl || ''
}

async function openBundleImage(image: NonNullable<MultiViewBundle['images']>[number]) {
  const fallback = bundleImageUrl(image)
  if (!fallback && !image.assetId) return
  activeBundleImage.value = { url: fallback, label: image.label || image.view || '视图' }
  document.body.style.overflow = 'hidden'
  if (!image.assetId) return
  try {
    const secured = await requestAssetPreviewUrl(image.assetId)
    if (secured && activeBundleImage.value) activeBundleImage.value.url = secured
  } catch {
    // Keep the signed URL returned by the bundle endpoint as a fallback.
  }
}

function closeBundleImage() {
  activeBundleImage.value = null
  if (!activeWork.value && !rejectionTarget.value) document.body.style.overflow = ''
}

function bundlePurpose(bundle: MultiViewBundle) {
  return bundle.purpose === 'museum_sale' ? `博物馆售卖${bundle.museumName ? ` · ${bundle.museumName}` : ''}` : '个人创作'
}

async function reviewBundle(bundle: MultiViewBundle, nextStatus: ReviewStatus, reviewComment = '') {
  reviewingBundleId.value = bundle.id
  try {
    const r = await fetch(`/api/creative/ai/consumer-multiview-bundles/${bundle.id}/review`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: nextStatus, comment: reviewComment.trim() }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    emit('alert', nextStatus === 'approved' ? '三视图作品包已审核通过，可进入打样流程' : nextStatus === 'rejected' ? '三视图作品包已驳回并记录原因' : '三视图作品包已退回待审核', 'success')
    await load()
  } catch (e: any) {
    emit('alert', '三视图作品包审核失败：' + (e?.message || e), 'error')
  } finally {
    reviewingBundleId.value = null
  }
}

async function loadProfessionalSubmissions() {
  try {
    const r = await fetch('/api/creative/ai/consumer-professional-submissions/review', { cache: 'no-store' })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const data = await r.json()
    professionalSubmissions.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    emit('alert', '加载专业作品包失败：' + (e?.message || e), 'error')
  }
}

async function reviewProfessionalSubmission(item: any, nextStatus: ReviewStatus, reviewComment = '') {
  reviewingSubmissionId.value = item.id
  try {
    const r = await fetch(`/api/creative/ai/consumer-professional-submissions/${item.id}/review`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status: nextStatus, comment: reviewComment.trim() }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    emit('alert', nextStatus === 'approved' ? '专业作品包已审核通过' : nextStatus === 'rejected' ? '专业作品包已标记不通过' : '专业作品包已退回待审核', 'success')
    await loadProfessionalSubmissions()
  } catch (e: any) {
    emit('alert', '专业作品包审核失败：' + (e?.message || e), 'error')
  } finally {
    reviewingSubmissionId.value = null
  }
}

async function downloadProfessionalSubmission(item: any) {
  try {
    const r = await fetch(`/api/creative/ai/consumer-professional-submissions/${item.id}/download`)
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const blob = await r.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = item.originalName || 'professional-submission.zip'
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } catch (e: any) {
    emit('alert', '下载专业作品包失败：' + (e?.message || e), 'error')
  }
}

async function reviewWork(w: ConsumerAsset, nextStatus: ReviewStatus, reviewComment = '') {
  reviewingId.value = w.id
  try {
    const r = await fetch(`/api/creative/ai/consumer-assets/${w.id}/review`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ status: nextStatus, operator: props.currentUser.username, comment: reviewComment.trim() }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const data = await r.json().catch(() => null)
    emit('alert', data?.message || (nextStatus === 'approved' ? '作品已审核通过，已进入C端用户端库存' : nextStatus === 'rejected' ? '作品已标记不通过' : '作品已退回待审核'), 'success')
    await load()
  } catch (e: any) {
    emit('alert', '审核失败：' + (e?.message || e), 'error')
  } finally {
    reviewingId.value = null
  }
}

async function openPreview(w: ConsumerAsset) {
  activeWork.value = w
  activeMediaUrl.value = ''
  document.body.style.overflow = 'hidden'
  try {
    activeMediaUrl.value = await requestAssetPreviewUrl(w.id)
  } catch (e: any) {
    emit('alert', `作品预览失败：${e?.message || e}`, 'error')
  }
}

function closePreview() {
  activeWork.value = null
  activeMediaUrl.value = ''
  document.body.style.overflow = ''
}

function openRejectForm(kind: 'work' | 'bundle' | 'professional', item: any) {
  rejectionTarget.value = { kind, item }
  rejectionReason.value = ''
  document.body.style.overflow = 'hidden'
}

function closeRejectForm() {
  rejectionTarget.value = null
  rejectionReason.value = ''
  if (!activeWork.value && !activeBundleImage.value) document.body.style.overflow = ''
}

async function confirmReject() {
  const target = rejectionTarget.value
  const reason = rejectionReason.value.trim()
  if (!target) return
  if (reason.length < 2) {
    emit('alert', '请填写具体的不通过原因', 'error')
    return
  }
  closeRejectForm()
  if (target.kind === 'work') await reviewWork(target.item, 'rejected', reason)
  else if (target.kind === 'bundle') await reviewBundle(target.item, 'rejected', reason)
  else await reviewProfessionalSubmission(target.item, 'rejected', reason)
}

onMounted(load)
</script>

<template>
  <div class="review-page">
    <section class="hero-card">
      <div>
        <span class="eyebrow">{{ isProfessionalMode ? 'PROFESSIONAL REVIEW' : isMultiviewMode ? 'MULTI-VIEW REVIEW' : 'CONSUMER REVIEW' }}</span>
        <h1>{{ isProfessionalMode ? '专业作品审核' : isMultiviewMode ? '多视图审核' : 'C端作品审核' }}</h1>
        <p v-if="isProfessionalMode">审核专业用户提交的 ZIP 作品包。驳回时必须填写具体原因，用户会在作品流程中看到并可重新提交。</p>
        <p v-else-if="isMultiviewMode">正面、侧面和背面作为一个完整作品包统一审核。每个视角都可以放大查看，通过后用户才能申请打样。</p>
        <p v-else>这里只处理已完成的 3D 原型。单张产品图不能单独进入审核，用户需要先生成三视图或 3D 模型。</p>
      </div>
      <div class="hero-stats">
        <article><b>{{ stats.total }}</b><span>当前列表</span></article>
        <article><b>{{ stats.review }}</b><span>待审核</span></article>
        <article><b>{{ stats.approved }}</b><span>已通过</span></article>
        <article><b>{{ stats.rejected }}</b><span>未通过</span></article>
      </div>
    </section>

    <section class="filter-card" v-if="!isProfessionalMode || professionalSubmissions.length">
      <label>
        <span>用户ID</span>
        <input v-model.trim="keywordUserId" type="number" placeholder="输入C端用户ID查询" @keyup.enter="load" />
      </label>
      <label>
        <span>审核状态</span>
        <select v-model="status" @change="load">
          <option value="all">全部</option>
          <option value="review">待审核</option>
          <option value="approved">已通过</option>
          <option value="rejected">未通过</option>
        </select>
      </label>
      <button type="button" :disabled="loading" @click="load">{{ loading ? '查询中…' : '查询作品' }}</button>
    </section>

    <section v-if="isMultiviewMode && visibleMultiViewBundles.length" class="multiview-review-panel">
      <header class="multiview-review-header"><div><span>COMPLETE PRODUCT REVIEW</span><h2>三视图作品包审核</h2><p>正面、侧面和背面作为一个完整产品统一审核。通过后用户才能申请打样。</p></div><b>{{ visibleMultiViewBundles.length }} <small>个作品包</small></b></header>
      <div class="multiview-review-grid">
        <article v-for="bundle in visibleMultiViewBundles" :key="bundle.id" class="multiview-review-card">
          <div class="bundle-review-top"><div><strong>{{ bundle.productName || '三视图文创作品' }}</strong><small>{{ bundle.bundleNo || `#${bundle.id}` }} · 用户 {{ bundle.userId || '-' }} · {{ bundle.username || '-' }}</small></div><span class="status-pill" :class="statusClass(bundle.status)">{{ statusText[bundle.status || 'review'] || bundle.status }}</span></div>
          <div class="bundle-review-images"><div v-for="image in bundle.images || []" :key="image.assetId" role="button" tabindex="0" @click="openBundleImage(image)"><img v-if="bundleImageUrl(image)" :src="bundleImageUrl(image)" :alt="image.label || '视图'" /><span v-else>{{ image.label || '视图' }}</span><small>{{ image.label }} · 点击查看大图</small></div></div>
          <div class="bundle-review-meta"><span>{{ bundle.material || '材质待定' }}</span><span>{{ bundle.productSize || '尺寸待定' }}</span><span>{{ bundlePurpose(bundle) }}</span></div>
          <p v-if="bundle.reviewComment" class="bundle-review-note">审核意见：{{ bundle.reviewComment }}</p>
          <div class="actions"><template v-if="bundle.status === 'review'"><button class="approve" :disabled="reviewingBundleId === bundle.id" @click="reviewBundle(bundle, 'approved')">通过整包</button><button class="reject" :disabled="reviewingBundleId === bundle.id" @click="openRejectForm('bundle', bundle)">不通过</button></template><button v-if="['approved', 'rejected'].includes(String(bundle.status))" class="outline" :disabled="reviewingBundleId === bundle.id" @click="reviewBundle(bundle, 'review')">退回待审</button></div>
        </article>
      </div>
    </section>

    <section class="work-grid" v-if="isStandardMode && visibleWorks.length">
      <article v-for="w in visibleWorks" :key="w.id" class="work-card">
        <div class="preview" @click="openPreview(w)">
          <img v-if="w.assetType === 'image' && previewUrl(w)" :src="previewUrl(w)" alt="C端作品" />
          <img v-else-if="w.assetType === 'model' && w.previewUrl" :src="w.previewUrl" alt="3D模型预览" />
          <div v-else class="model-placeholder">3D</div>
          <span class="type-pill">{{ assetTypeText(w.assetType) }}</span>
          <span class="purpose-pill" :class="purposeClass(w)">{{ purposeText(w) }}</span>
          <span class="status-pill" :class="statusClass(w.status)">{{ statusText[w.status || 'review'] || w.status }}</span>
        </div>
        <div class="work-body">
          <div class="title-line">
            <b>{{ w.title || '未命名作品' }}</b>
            <small>#{{ w.id }}</small>
          </div>
          <div class="meta-row">
            <span>用户ID：{{ w.createdBy || '-' }}</span>
            <span>账号：{{ w.createdByName || '-' }}</span>
          </div>
          <div class="meta-row purpose-row">
            <span>提交用途：{{ purposeText(w) }}</span>
            <span>{{ purposeOf(w) === 'museum_sale' ? '博物馆准入审批' : '普通作品审核' }}</span>
          </div>
          <div v-if="approvalSource(w)" class="approval-source">审批出处：{{ approvalSource(w) }}</div>
          <div v-if="campaignOf(w)" class="campaign-source">活动投稿：{{ campaignOf(w) }} · 通过时由系统自动结算积分</div>
          <div class="meta-row">
            <span>格式：{{ (w.format || '-').toUpperCase() }}</span>
            <span>{{ formatTime(w.createdAt) }}</span>
          </div>
          <p class="prompt" :title="w.prompt">{{ w.prompt || '暂无提示词' }}</p>
          <div class="actions">
            <button type="button" class="outline" @click="openPreview(w)">查看</button>
            <button type="button" class="approve" :disabled="reviewingId === w.id" @click="reviewWork(w, 'approved')">通过</button>
            <button type="button" class="reject" :disabled="reviewingId === w.id" @click="openRejectForm('work', w)">不通过</button>
            <button v-if="w.status !== 'review'" type="button" class="outline" :disabled="reviewingId === w.id" @click="reviewWork(w, 'review')">退回待审</button>
          </div>
        </div>
      </article>
    </section>

    <section v-if="((isStandardMode && !visibleWorks.length) || (isMultiviewMode && !visibleMultiViewBundles.length))" class="empty-card">
      <b>{{ loading ? '正在加载审核数据…' : isProfessionalMode ? '暂无专业作品包' : isMultiviewMode ? '暂无多视图作品包' : '暂无可审核的3D作品' }}</b>
      <span>可以切换状态或输入其他用户 ID 再查询。</span>
    </section>

    <section v-if="isProfessionalMode" class="professional-review-panel">
      <header>
        <div><span>PROFESSIONAL SUBMISSIONS</span><h2>专业作品包审核</h2><p>这里的 ZIP 文件由专业设计师真实提交，只有审核管理员可下载查看和给出审核结论。</p></div>
        <b>{{ visibleProfessionalSubmissions.length }} <small>份作品包</small></b>
      </header>
      <div v-if="visibleProfessionalSubmissions.length" class="submission-table-wrap">
        <table>
          <thead><tr><th>作品包</th><th>提交人 / 用途</th><th>文件与时间</th><th>审核状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="item in visibleProfessionalSubmissions" :key="item.id">
              <td><strong>{{ item.title }}</strong><small>{{ item.submissionNo }}</small><p v-if="item.note">{{ item.note }}</p></td>
              <td><strong>{{ item.createdByName || `用户 #${item.userId}` }}</strong><small>{{ item.purpose === 'museum_sale' ? `博物馆售卖${item.museumName ? ` · ${item.museumName}` : ''}` : '个人创作' }}</small></td>
              <td><strong>{{ item.originalName }}</strong><small>{{ item.fileSize ? `${(item.fileSize / 1024 / 1024).toFixed(1)} MB` : '-' }} · {{ formatTime(item.createdAt) }}</small></td>
              <td><span class="submission-status" :class="statusClass(item.status)">{{ statusText[item.status || 'review'] || item.status }}</span><small v-if="item.reviewComment" class="review-note">{{ item.reviewComment }}</small></td>
              <td><div class="submission-actions"><button type="button" class="outline" @click="downloadProfessionalSubmission(item)">下载 ZIP</button><button type="button" class="approve" :disabled="reviewingSubmissionId === item.id" @click="reviewProfessionalSubmission(item, 'approved')">通过</button><button type="button" class="reject" :disabled="reviewingSubmissionId === item.id" @click="openRejectForm('professional', item)">不通过</button><button v-if="item.status !== 'review'" type="button" class="outline" :disabled="reviewingSubmissionId === item.id" @click="reviewProfessionalSubmission(item, 'review')">退回待审</button></div></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="submission-empty">暂无专业设计师提交的 ZIP 作品包。</div>
    </section>

    <Teleport to="body">
      <div v-if="activeWork" class="preview-modal" @click.self="closePreview">
        <div class="modal-card">
          <header>
            <div>
              <b>{{ activeWork.title || '作品预览' }}</b>
              <span>用户ID：{{ activeWork.createdBy }} · {{ statusText[activeWork.status || 'review'] || activeWork.status }}</span>
            </div>
            <button type="button" @click="closePreview">×</button>
          </header>
          <div class="modal-body">
            <img v-if="activeWork.assetType === 'image' && (activeMediaUrl || previewUrl(activeWork))" :src="activeMediaUrl || previewUrl(activeWork)" alt="作品预览" />
            <div v-else class="model-large">
              <b>3D模型文件</b>
              <span>可打开模型文件进行预览或下载。</span>
              <a v-if="activeMediaUrl" :href="activeMediaUrl" target="_blank" rel="noopener">打开模型文件</a>
            </div>
          </div>
          <footer>
            <a v-if="activeMediaUrl" :href="activeMediaUrl" target="_blank" rel="noopener">打开原文件</a>
            <button type="button" class="approve" @click="reviewWork(activeWork, 'approved')">审核通过</button>
            <button type="button" class="reject" @click="openRejectForm('work', activeWork)">审核不通过</button>
          </footer>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="activeBundleImage" class="image-preview-modal" @click.self="closeBundleImage">
        <div class="image-preview-card">
          <header><div><b>{{ activeBundleImage.label }}</b><span>多视图审核 · 原图预览</span></div><button type="button" @click="closeBundleImage">×</button></header>
          <div class="image-preview-body"><img :src="activeBundleImage.url" :alt="activeBundleImage.label" /></div>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div v-if="rejectionTarget" class="reject-modal" @click.self="closeRejectForm">
        <form class="reject-dialog" @submit.prevent="confirmReject">
          <header><div><span class="eyebrow">REVIEW FEEDBACK</span><h2>填写不通过原因</h2><p>{{ rejectionTarget.kind === 'professional' ? rejectionTarget.item.title : rejectionTarget.kind === 'bundle' ? rejectionTarget.item.productName || '三视图作品包' : rejectionTarget.item.title || '3D作品' }}</p></div><button type="button" @click="closeRejectForm">×</button></header>
          <label><span>原因说明 <b>必填</b></span><textarea v-model.trim="rejectionReason" maxlength="500" autofocus placeholder="请写清楚需要修改的内容，例如：背面结构缺少闭合细节，请补充完整后重新提交。" /></label>
          <div class="reject-dialog-foot"><span>{{ rejectionReason.length }}/500</span><div><button type="button" class="outline" @click="closeRejectForm">取消</button><button type="submit" class="reject" :disabled="rejectionReason.trim().length < 2">确认不通过</button></div></div>
        </form>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.review-page{padding:24px;display:flex;flex-direction:column;gap:18px}.hero-card{position:relative;overflow:hidden;display:grid;grid-template-columns:minmax(0,1.2fr) minmax(360px,.8fr);gap:20px;padding:28px;border-radius:28px;color:#1f2937;background:linear-gradient(135deg,#fff 0%,#f8efe7 48%,#eefaf7 100%);border:1px solid rgba(148,163,184,.18);box-shadow:0 22px 60px rgba(15,23,42,.08)}.hero-card:after{content:"";position:absolute;right:-80px;top:-90px;width:260px;height:260px;border-radius:50%;background:rgba(180,83,42,.12)}.eyebrow{display:inline-flex;margin-bottom:10px;padding:7px 10px;border-radius:999px;background:#fff6ed;color:#b4532a;font-size:11px;font-weight:900;letter-spacing:1.7px}.hero-card h1{margin:0 0 10px;font-size:30px;letter-spacing:-.04em}.hero-card p{max-width:720px;margin:0;color:#64748b;line-height:1.7}.hero-stats{position:relative;z-index:1;display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.hero-stats article{padding:18px;border-radius:20px;background:rgba(255,255,255,.75);border:1px solid rgba(148,163,184,.16);box-shadow:0 12px 30px rgba(15,23,42,.05)}.hero-stats b{display:block;font-size:28px;color:#111827}.hero-stats span{font-size:12px;color:#64748b;font-weight:800}.filter-card{display:grid;grid-template-columns:180px 160px minmax(240px,1fr) 120px;gap:12px;align-items:end;padding:16px;border-radius:22px;background:#fff;border:1px solid rgba(148,163,184,.18);box-shadow:0 12px 34px rgba(15,23,42,.05)}label span{display:block;margin-bottom:7px;color:#475569;font-size:12px;font-weight:900}input,select{width:100%;height:42px;box-sizing:border-box;border:1px solid #e2e8f0;border-radius:13px;background:#f8fafc;padding:0 12px;color:#0f172a;outline:none}input:focus,select:focus{border-color:#b4532a;box-shadow:0 0 0 3px rgba(180,83,42,.12)}.filter-card button,.actions button,footer button{height:42px;border:0;border-radius:13px;font-weight:900;cursor:pointer}.filter-card button{background:#111827;color:#fff}.filter-card button:disabled,.actions button:disabled{opacity:.55;cursor:not-allowed}.work-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:16px}.work-card{overflow:hidden;border-radius:24px;background:#fff;border:1px solid rgba(148,163,184,.16);box-shadow:0 16px 42px rgba(15,23,42,.07)}.preview{position:relative;height:230px;background:#111827;cursor:pointer;overflow:hidden}.preview img{width:100%;height:100%;object-fit:cover;display:block;transition:transform .25s}.preview:hover img{transform:scale(1.03)}.model-placeholder{height:100%;display:flex;align-items:center;justify-content:center;color:#fff;font-size:46px;font-weight:950;background:radial-gradient(circle at 70% 20%,rgba(20,184,166,.35),transparent 35%),linear-gradient(135deg,#111827,#334155)}.type-pill,.status-pill{position:absolute;top:12px;padding:7px 9px;border-radius:999px;background:rgba(255,255,255,.92);font-size:11px;font-weight:900}.type-pill{left:12px;color:#334155}.status-pill{right:12px}.status-pill.wait{color:#b45309;background:#fff7ed}.status-pill.ok{color:#047857;background:#ecfdf5}.status-pill.bad{color:#dc2626;background:#fef2f2}.work-body{padding:16px}.title-line{display:flex;align-items:center;justify-content:space-between;gap:12px}.title-line b{font-size:16px;color:#0f172a;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.title-line small{color:#94a3b8;font-weight:900}.meta-row{display:flex;justify-content:space-between;gap:10px;margin-top:9px;color:#64748b;font-size:12px}.prompt{min-height:44px;margin:12px 0 14px;color:#475569;font-size:13px;line-height:1.55;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.actions{display:flex;flex-wrap:wrap;gap:8px}.actions button{padding:0 13px}.outline{border:1px solid #e2e8f0!important;background:#fff!important;color:#334155!important}.approve{background:#0f766e!important;color:#fff!important}.reject{background:#b91c1c!important;color:#fff!important}.empty-card{padding:60px 20px;text-align:center;border-radius:24px;background:#fff;border:1px dashed #cbd5e1;color:#64748b}.empty-card b,.empty-card span{display:block}.empty-card b{margin-bottom:8px;color:#0f172a;font-size:18px}.preview-modal{position:fixed;inset:0;z-index:200;background:rgba(15,23,42,.62);backdrop-filter:blur(8px);display:flex;align-items:center;justify-content:center;padding:24px}.modal-card{width:min(980px,96vw);max-height:92vh;display:flex;flex-direction:column;border-radius:26px;background:#fff;overflow:hidden;box-shadow:0 28px 90px rgba(0,0,0,.28)}.modal-card header,.modal-card footer{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}.modal-card footer{border-top:1px solid #e2e8f0;border-bottom:0;justify-content:flex-end}.modal-card header b,.modal-card header span{display:block}.modal-card header span{margin-top:4px;color:#64748b;font-size:12px}.modal-card header button{width:38px;height:38px;border:0;border-radius:12px;background:#f1f5f9;font-size:24px;color:#475569}.modal-body{min-height:320px;overflow:auto;background:#f8fafc;display:flex;align-items:center;justify-content:center}.modal-body img{max-width:100%;max-height:72vh;object-fit:contain}.model-large{display:flex;flex-direction:column;align-items:center;gap:10px;color:#64748b}.model-large b{font-size:28px;color:#0f172a}.model-large a,.modal-card footer a{height:40px;display:inline-flex;align-items:center;padding:0 14px;border-radius:12px;background:#111827;color:#fff;text-decoration:none;font-weight:900}@media(max-width:980px){.review-page{padding:16px}.hero-card{grid-template-columns:1fr}.filter-card{grid-template-columns:1fr 1fr}.comment-field{grid-column:1/-1}.filter-card button{grid-column:1/-1}}@media(max-width:640px){.filter-card,.work-grid{grid-template-columns:1fr}.hero-stats{grid-template-columns:repeat(2,1fr)}.preview{height:210px}}
</style>

<style scoped>
.bundle-review-images>div{cursor:zoom-in}.bundle-review-images>div:focus-visible{outline:3px solid rgba(15,118,110,.35);outline-offset:2px}.image-preview-modal,.reject-modal{position:fixed;inset:0;z-index:220;display:flex;align-items:center;justify-content:center;padding:24px;background:rgba(15,23,42,.72);backdrop-filter:blur(10px)}.image-preview-card{width:min(1080px,96vw);max-height:94vh;display:flex;flex-direction:column;overflow:hidden;border-radius:22px;background:#fff;box-shadow:0 30px 100px rgba(0,0,0,.34)}.image-preview-card header,.reject-dialog header{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding:18px 20px;border-bottom:1px solid #e2e8f0}.image-preview-card header b,.image-preview-card header span{display:block}.image-preview-card header span{margin-top:5px;color:#64748b;font-size:12px}.image-preview-card header button,.reject-dialog header button{width:38px;height:38px;border:0;border-radius:11px;background:#f1f5f9;color:#475569;font-size:24px;cursor:pointer}.image-preview-body{min-height:440px;display:flex;align-items:center;justify-content:center;overflow:auto;background:#101827}.image-preview-body img{max-width:100%;max-height:78vh;object-fit:contain}.reject-dialog{width:min(600px,94vw);padding:0;overflow:hidden;border:0;border-radius:22px;background:#fff;box-shadow:0 30px 100px rgba(0,0,0,.3)}.reject-dialog header{border-bottom:0;padding-bottom:10px}.reject-dialog h2{margin:3px 0 0;color:#0f172a;font-size:22px}.reject-dialog header p{margin:8px 0 0;color:#64748b;font-size:13px}.reject-dialog>label{display:block;padding:0 20px}.reject-dialog>label>span{display:flex;align-items:center;gap:7px;margin-bottom:8px;color:#334155;font-size:13px;font-weight:900}.reject-dialog>label>span b{padding:3px 6px;border-radius:6px;background:#fef2f2;color:#b91c1c;font-size:10px}.reject-dialog textarea{display:block;width:100%;min-height:150px;box-sizing:border-box;resize:vertical;border:1px solid #dbe3ea;border-radius:13px;background:#f8fafc;padding:12px;color:#0f172a;font:inherit;line-height:1.6;outline:none}.reject-dialog textarea:focus{border-color:#0f766e;box-shadow:0 0 0 3px rgba(15,118,110,.12)}.reject-dialog-foot{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 20px 20px;color:#94a3b8;font-size:12px}.reject-dialog-foot>div{display:flex;gap:8px}.reject-dialog-foot button{height:40px;padding:0 14px;border:0;border-radius:11px;font-weight:900;cursor:pointer}.reject-dialog-foot button:disabled{opacity:.5;cursor:not-allowed}@media(max-width:640px){.image-preview-modal,.reject-modal{padding:12px}.image-preview-body{min-height:300px}.image-preview-card{width:100%}.reject-dialog-foot{align-items:flex-end;flex-direction:column}.reject-dialog-foot>div{width:100%}.reject-dialog-foot button{flex:1}}
</style>

<style scoped>
.purpose-pill{position:absolute;left:92px;top:12px;padding:7px 9px;border-radius:999px;background:rgba(255,255,255,.92);font-size:11px;font-weight:900;color:#64748b}.purpose-pill.museum{color:#7c2d12;background:#fff7ed}.purpose-pill.personal{color:#047857;background:#ecfdf5}.purpose-pill.unknown{color:#64748b;background:#f8fafc}.purpose-row{padding:8px 10px;border-radius:12px;background:#f8fafc;color:#334155;font-weight:800}@media(max-width:640px){.purpose-pill{left:12px;top:48px}}
</style>

<style scoped>
.approval-source{margin:9px 0;padding:8px 10px;border-radius:10px;background:#ecfdf5;border:1px solid #a7f3d0;color:#047857;font-size:12px;font-weight:800;line-height:1.45}
.campaign-source{margin:9px 0;padding:8px 10px;border-radius:10px;background:#fff7ed;border:1px solid #fed7aa;color:#9a4f20;font-size:12px;font-weight:800;line-height:1.45}
</style>

<style scoped>
.professional-review-panel{overflow:hidden;border:1px solid rgba(112,139,119,.24);border-radius:24px;background:linear-gradient(145deg,#f8fbf7,#edf4ed);box-shadow:0 15px 37px rgba(67,92,72,.07)}.professional-review-panel>header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding:21px 23px;border-bottom:1px solid rgba(126,151,130,.17)}.professional-review-panel header span{display:block;color:#5f7b69;font-size:10px;font-weight:950;letter-spacing:.14em}.professional-review-panel h2{margin:6px 0;color:#34483b;font-size:22px}.professional-review-panel header p{max-width:620px;margin:0;color:#718072;font-size:12px;line-height:1.6}.professional-review-panel>header>b{display:grid;place-items:center;min-width:82px;min-height:65px;border:1px solid #d4e2d4;border-radius:16px;background:#fffefa;color:#476958;font-size:25px}.professional-review-panel>header>b small{color:#839185;font-size:10px}.submission-table-wrap{overflow:auto}.submission-table-wrap table{width:100%;min-width:980px;border-collapse:collapse}.submission-table-wrap th,.submission-table-wrap td{padding:15px 17px;border-bottom:1px solid rgba(126,151,130,.16);text-align:left;vertical-align:top}.submission-table-wrap th{background:rgba(255,255,255,.38);color:#617366;font-size:11px}.submission-table-wrap td strong,.submission-table-wrap td small{display:block}.submission-table-wrap td strong{max-width:260px;overflow:hidden;color:#33483b;font-size:13px;text-overflow:ellipsis;white-space:nowrap}.submission-table-wrap td small{margin-top:5px;color:#7a887d;font-size:11px;line-height:1.45}.submission-table-wrap td p{max-width:270px;margin:8px 0 0;color:#6e786e;font-size:11px;line-height:1.5}.submission-status{display:inline-flex;padding:6px 8px;border-radius:999px;font-size:11px;font-weight:900}.submission-status.wait{color:#9a6700;background:#fff4d8}.submission-status.ok{color:#23734e;background:#e7f7ed}.submission-status.bad{color:#b42318;background:#ffeded}.review-note{max-width:190px}.submission-actions{display:flex;flex-wrap:wrap;gap:7px}.submission-actions button{height:35px;padding:0 10px;border-radius:10px;font-size:11px}.submission-empty{padding:38px 20px;color:#748174;text-align:center;font-size:13px}@media(max-width:640px){.professional-review-panel>header{padding:18px}.professional-review-panel h2{font-size:19px}.professional-review-panel>header>b{min-width:64px;min-height:54px;font-size:21px}}
.multiview-review-panel{overflow:hidden;border:1px solid rgba(80,118,92,.24);border-radius:20px;background:#f7fbf7;box-shadow:0 12px 30px rgba(35,70,44,.06)}.multiview-review-header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding:20px 22px;border-bottom:1px solid #dce9de}.multiview-review-header span{color:#62806a;font-size:10px;font-weight:900;letter-spacing:.14em}.multiview-review-header h2{margin:6px 0;color:#334b3b;font-size:21px}.multiview-review-header p{margin:0;color:#718275;font-size:12px;line-height:1.55}.multiview-review-header>b{display:grid;place-items:center;min-width:80px;min-height:62px;border:1px solid #d3e2d5;border-radius:14px;background:#fff;color:#477058;font-size:23px}.multiview-review-header>b small{color:#829286;font-size:10px}.multiview-review-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(350px,1fr));gap:14px;padding:16px}.multiview-review-card{padding:15px;border:1px solid #dce8dd;border-radius:15px;background:#fff}.bundle-review-top{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.bundle-review-top>div{display:flex;min-width:0;flex-direction:column;gap:5px}.bundle-review-top strong{overflow:hidden;color:#304a38;font-size:15px;text-overflow:ellipsis;white-space:nowrap}.bundle-review-top small{color:#819087;font-size:11px}.bundle-review-images{display:grid;grid-template-columns:repeat(3,1fr);gap:7px;margin-top:12px}.bundle-review-images>div{overflow:hidden;border:1px solid #e1eae2;border-radius:9px;background:#f5f8f5}.bundle-review-images img,.bundle-review-images span{display:block;width:100%;height:145px;object-fit:contain;background:#edf2ed}.bundle-review-images span{display:grid;place-items:center;color:#839287;font-size:12px}.bundle-review-images small{display:block;padding:6px;color:#708174;font-size:11px;text-align:center}.bundle-review-meta{display:flex;flex-wrap:wrap;gap:6px;margin-top:11px}.bundle-review-meta span{padding:5px 7px;border-radius:7px;background:#f0f5f0;color:#607768;font-size:11px}.bundle-review-note{margin:10px 0 0;padding:9px 10px;border-left:3px solid #bd6c53;border-radius:0 8px 8px 0;background:#fff4ef;color:#925542;font-size:12px;line-height:1.5}.multiview-review-card .actions{margin-top:12px}.multiview-review-card .actions button{height:35px;padding:0 10px;border-radius:9px;font-size:11px}@media(max-width:640px){.multiview-review-header{padding:17px}.multiview-review-grid{grid-template-columns:1fr;padding:12px}.bundle-review-images img,.bundle-review-images span{height:112px}}
</style>
