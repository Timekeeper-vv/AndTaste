<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'

const props = defineProps<{ currentUser: User }>()
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

const works = ref<ConsumerAsset[]>([])
const loading = ref(false)
const reviewingId = ref<number | null>(null)
const keywordUserId = ref('')
const status = ref<'all' | ReviewStatus>('review')
const comment = ref('')
const activeWork = ref<ConsumerAsset | null>(null)

const stats = computed(() => {
  const total = works.value.length
  const review = works.value.filter(x => x.status === 'review').length
  const approved = works.value.filter(x => x.status === 'approved').length
  const rejected = works.value.filter(x => x.status === 'rejected').length
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
const fileViewUrl = (w: ConsumerAsset) => w.assetType === 'model' && w.id ? `/api/creative/ai/assets/${w.id}/model-content` : (w.fileUrl || w.previewUrl || '')

function formatTime(v?: string) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 19)
}

async function load() {
  loading.value = true
  try {
    const qs = new URLSearchParams({ size: '200' })
    if (keywordUserId.value.trim()) qs.set('userId', keywordUserId.value.trim())
    if (status.value !== 'all') qs.set('status', status.value)
    const r = await fetch(`/api/creative/ai/consumer-assets/review?${qs}`, {
      cache: 'no-store',
      headers: {
        'X-Current-Role': props.currentUser.role,
        'X-Current-User': props.currentUser.username,
      },
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    const data = await r.json()
    works.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    emit('alert', '加载C端作品失败：' + (e?.message || e), 'error')
  } finally {
    loading.value = false
  }
}

async function reviewWork(w: ConsumerAsset, nextStatus: ReviewStatus) {
  reviewingId.value = w.id
  try {
    const r = await fetch(`/api/creative/ai/consumer-assets/${w.id}/review`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        'X-Current-Role': props.currentUser.role,
        'X-Current-User': props.currentUser.username,
      },
      body: JSON.stringify({ status: nextStatus, operator: props.currentUser.username, comment: comment.value.trim() }),
    })
    if (!r.ok) {
      const err = await r.json().catch(() => null)
      throw new Error(err?.message || `HTTP ${r.status}`)
    }
    emit('alert', nextStatus === 'approved' ? '作品已审核通过，已进入C端用户端库存' : nextStatus === 'rejected' ? '作品已标记不通过' : '作品已退回待审核', 'success')
    await load()
  } catch (e: any) {
    emit('alert', '审核失败：' + (e?.message || e), 'error')
  } finally {
    reviewingId.value = null
  }
}

function openPreview(w: ConsumerAsset) {
  activeWork.value = w
  document.body.style.overflow = 'hidden'
}

function closePreview() {
  activeWork.value = null
  document.body.style.overflow = ''
}

onMounted(load)
</script>

<template>
  <div class="review-page">
    <section class="hero-card">
      <div>
        <span class="eyebrow">CONSUMER REVIEW</span>
        <h1>C端用户作品审核</h1>
        <p>集中查看 C 端用户生成的产品图和 3D 模型，支持按用户 ID 查询，并对作品做通过/不通过审核。</p>
      </div>
      <div class="hero-stats">
        <article><b>{{ stats.total }}</b><span>当前列表</span></article>
        <article><b>{{ stats.review }}</b><span>待审核</span></article>
        <article><b>{{ stats.approved }}</b><span>已通过</span></article>
        <article><b>{{ stats.rejected }}</b><span>未通过</span></article>
      </div>
    </section>

    <section class="filter-card">
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
      <label class="comment-field">
        <span>审核意见</span>
        <input v-model="comment" placeholder="选填：不通过原因或内部备注" />
      </label>
      <button type="button" :disabled="loading" @click="load">{{ loading ? '查询中…' : '查询作品' }}</button>
    </section>

    <section class="work-grid" v-if="works.length">
      <article v-for="w in works" :key="w.id" class="work-card">
        <div class="preview" @click="openPreview(w)">
          <img v-if="w.assetType === 'image' && previewUrl(w)" :src="previewUrl(w)" alt="C端作品" />
          <img v-else-if="w.assetType === 'model' && w.previewUrl" :src="w.previewUrl" alt="3D模型预览" />
          <div v-else class="model-placeholder">3D</div>
          <span class="type-pill">{{ assetTypeText(w.assetType) }}</span>
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
          <div class="meta-row">
            <span>格式：{{ (w.format || '-').toUpperCase() }}</span>
            <span>{{ formatTime(w.createdAt) }}</span>
          </div>
          <p class="prompt" :title="w.prompt">{{ w.prompt || '暂无提示词' }}</p>
          <div class="actions">
            <button type="button" class="outline" @click="openPreview(w)">查看</button>
            <button type="button" class="approve" :disabled="reviewingId === w.id" @click="reviewWork(w, 'approved')">通过</button>
            <button type="button" class="reject" :disabled="reviewingId === w.id" @click="reviewWork(w, 'rejected')">不通过</button>
            <button v-if="w.status !== 'review'" type="button" class="outline" :disabled="reviewingId === w.id" @click="reviewWork(w, 'review')">退回待审</button>
          </div>
        </div>
      </article>
    </section>

    <section v-else class="empty-card">
      <b>{{ loading ? '正在加载作品…' : '暂无匹配作品' }}</b>
      <span>可以切换状态或输入其他用户 ID 再查询。</span>
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
            <img v-if="activeWork.assetType === 'image' && previewUrl(activeWork)" :src="previewUrl(activeWork)" alt="作品预览" />
            <div v-else class="model-large">
              <b>3D模型文件</b>
              <span>可打开模型文件进行预览或下载。</span>
              <a v-if="fileViewUrl(activeWork)" :href="fileViewUrl(activeWork)" target="_blank" rel="noopener">打开模型文件</a>
            </div>
          </div>
          <footer>
            <a v-if="fileViewUrl(activeWork)" :href="fileViewUrl(activeWork)" target="_blank" rel="noopener">打开原文件</a>
            <button type="button" class="approve" @click="reviewWork(activeWork, 'approved')">审核通过</button>
            <button type="button" class="reject" @click="reviewWork(activeWork, 'rejected')">审核不通过</button>
          </footer>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.review-page{padding:24px;display:flex;flex-direction:column;gap:18px}.hero-card{position:relative;overflow:hidden;display:grid;grid-template-columns:minmax(0,1.2fr) minmax(360px,.8fr);gap:20px;padding:28px;border-radius:28px;color:#1f2937;background:linear-gradient(135deg,#fff 0%,#f8efe7 48%,#eefaf7 100%);border:1px solid rgba(148,163,184,.18);box-shadow:0 22px 60px rgba(15,23,42,.08)}.hero-card:after{content:"";position:absolute;right:-80px;top:-90px;width:260px;height:260px;border-radius:50%;background:rgba(180,83,42,.12)}.eyebrow{display:inline-flex;margin-bottom:10px;padding:7px 10px;border-radius:999px;background:#fff6ed;color:#b4532a;font-size:11px;font-weight:900;letter-spacing:1.7px}.hero-card h1{margin:0 0 10px;font-size:30px;letter-spacing:-.04em}.hero-card p{max-width:720px;margin:0;color:#64748b;line-height:1.7}.hero-stats{position:relative;z-index:1;display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.hero-stats article{padding:18px;border-radius:20px;background:rgba(255,255,255,.75);border:1px solid rgba(148,163,184,.16);box-shadow:0 12px 30px rgba(15,23,42,.05)}.hero-stats b{display:block;font-size:28px;color:#111827}.hero-stats span{font-size:12px;color:#64748b;font-weight:800}.filter-card{display:grid;grid-template-columns:180px 160px minmax(240px,1fr) 120px;gap:12px;align-items:end;padding:16px;border-radius:22px;background:#fff;border:1px solid rgba(148,163,184,.18);box-shadow:0 12px 34px rgba(15,23,42,.05)}label span{display:block;margin-bottom:7px;color:#475569;font-size:12px;font-weight:900}input,select{width:100%;height:42px;box-sizing:border-box;border:1px solid #e2e8f0;border-radius:13px;background:#f8fafc;padding:0 12px;color:#0f172a;outline:none}input:focus,select:focus{border-color:#b4532a;box-shadow:0 0 0 3px rgba(180,83,42,.12)}.filter-card button,.actions button,footer button{height:42px;border:0;border-radius:13px;font-weight:900;cursor:pointer}.filter-card button{background:#111827;color:#fff}.filter-card button:disabled,.actions button:disabled{opacity:.55;cursor:not-allowed}.work-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:16px}.work-card{overflow:hidden;border-radius:24px;background:#fff;border:1px solid rgba(148,163,184,.16);box-shadow:0 16px 42px rgba(15,23,42,.07)}.preview{position:relative;height:230px;background:#111827;cursor:pointer;overflow:hidden}.preview img{width:100%;height:100%;object-fit:cover;display:block;transition:transform .25s}.preview:hover img{transform:scale(1.03)}.model-placeholder{height:100%;display:flex;align-items:center;justify-content:center;color:#fff;font-size:46px;font-weight:950;background:radial-gradient(circle at 70% 20%,rgba(20,184,166,.35),transparent 35%),linear-gradient(135deg,#111827,#334155)}.type-pill,.status-pill{position:absolute;top:12px;padding:7px 9px;border-radius:999px;background:rgba(255,255,255,.92);font-size:11px;font-weight:900}.type-pill{left:12px;color:#334155}.status-pill{right:12px}.status-pill.wait{color:#b45309;background:#fff7ed}.status-pill.ok{color:#047857;background:#ecfdf5}.status-pill.bad{color:#dc2626;background:#fef2f2}.work-body{padding:16px}.title-line{display:flex;align-items:center;justify-content:space-between;gap:12px}.title-line b{font-size:16px;color:#0f172a;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.title-line small{color:#94a3b8;font-weight:900}.meta-row{display:flex;justify-content:space-between;gap:10px;margin-top:9px;color:#64748b;font-size:12px}.prompt{min-height:44px;margin:12px 0 14px;color:#475569;font-size:13px;line-height:1.55;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.actions{display:flex;flex-wrap:wrap;gap:8px}.actions button{padding:0 13px}.outline{border:1px solid #e2e8f0!important;background:#fff!important;color:#334155!important}.approve{background:#0f766e!important;color:#fff!important}.reject{background:#b91c1c!important;color:#fff!important}.empty-card{padding:60px 20px;text-align:center;border-radius:24px;background:#fff;border:1px dashed #cbd5e1;color:#64748b}.empty-card b,.empty-card span{display:block}.empty-card b{margin-bottom:8px;color:#0f172a;font-size:18px}.preview-modal{position:fixed;inset:0;z-index:200;background:rgba(15,23,42,.62);backdrop-filter:blur(8px);display:flex;align-items:center;justify-content:center;padding:24px}.modal-card{width:min(980px,96vw);max-height:92vh;display:flex;flex-direction:column;border-radius:26px;background:#fff;overflow:hidden;box-shadow:0 28px 90px rgba(0,0,0,.28)}.modal-card header,.modal-card footer{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}.modal-card footer{border-top:1px solid #e2e8f0;border-bottom:0;justify-content:flex-end}.modal-card header b,.modal-card header span{display:block}.modal-card header span{margin-top:4px;color:#64748b;font-size:12px}.modal-card header button{width:38px;height:38px;border:0;border-radius:12px;background:#f1f5f9;font-size:24px;color:#475569}.modal-body{min-height:320px;overflow:auto;background:#f8fafc;display:flex;align-items:center;justify-content:center}.modal-body img{max-width:100%;max-height:72vh;object-fit:contain}.model-large{display:flex;flex-direction:column;align-items:center;gap:10px;color:#64748b}.model-large b{font-size:28px;color:#0f172a}.model-large a,.modal-card footer a{height:40px;display:inline-flex;align-items:center;padding:0 14px;border-radius:12px;background:#111827;color:#fff;text-decoration:none;font-weight:900}@media(max-width:980px){.review-page{padding:16px}.hero-card{grid-template-columns:1fr}.filter-card{grid-template-columns:1fr 1fr}.comment-field{grid-column:1/-1}.filter-card button{grid-column:1/-1}}@media(max-width:640px){.filter-card,.work-grid{grid-template-columns:1fr}.hero-stats{grid-template-columns:repeat(2,1fr)}.preview{height:210px}}
</style>
