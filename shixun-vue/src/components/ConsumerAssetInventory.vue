<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

interface InventoryAsset {
  id: number
  assetNo?: string
  title?: string
  assetType?: 'image' | 'model' | string
  sourceType?: string
  fileUrl?: string
  previewUrl?: string
  prompt?: string
  status?: string
  format?: string
  tags?: string
  createdBy?: number
  createdByName?: string
  createdAt?: string
  updatedAt?: string
}

const rows = ref<InventoryAsset[]>([])
const loading = ref(false)
const userId = ref('')
const keyword = ref('')
const type = ref<'all' | 'image' | 'model'>('all')
const active = ref<InventoryAsset | null>(null)

const stats = computed(() => {
  const image = rows.value.filter(x => x.assetType === 'image').length
  const model = rows.value.filter(x => x.assetType === 'model').length
  const users = new Set(rows.value.map(x => x.createdBy).filter(Boolean)).size
  return { total: rows.value.length, image, model, users }
})

function fmtTime(v?: string) {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 19)
}

function assetTypeText(v?: string) {
  return v === 'model' ? '3D模型' : '产品图片'
}

function previewUrl(a: InventoryAsset) {
  return a.previewUrl || a.fileUrl || ''
}

function fileUrl(a: InventoryAsset) {
  if (a.assetType === 'model' && a.id) return `/api/creative/ai/assets/${a.id}/model-content`
  return a.fileUrl || a.previewUrl || ''
}

function downloadUrl(a: InventoryAsset) {
  if (a.assetType === 'model' && a.id) return `/api/creative/ai/assets/${a.id}/download-model?format=GLB`
  return fileUrl(a)
}

async function load() {
  loading.value = true
  try {
    const qs = new URLSearchParams({ size: '500' })
    if (userId.value.trim()) qs.set('userId', userId.value.trim())
    if (keyword.value.trim()) qs.set('keyword', keyword.value.trim())
    if (type.value !== 'all') qs.set('type', type.value)
    const r = await fetch(`/api/creative/ai/consumer-assets/inventory?${qs}`, {
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
    rows.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    emit('alert', '加载C端库存失败：' + (e?.message || e), 'error')
  } finally {
    loading.value = false
  }
}

function open(a: InventoryAsset) {
  active.value = a
  document.body.style.overflow = 'hidden'
}
function close() {
  active.value = null
  document.body.style.overflow = ''
}

onMounted(load)
</script>

<template>
  <div class="inventory-page">
    <section class="inventory-hero">
      <div>
        <span>APPROVED ASSET LIBRARY</span>
        <h1>C端用户端库存</h1>
        <p>这里沉淀所有C端用户审核通过的图片与3D模型。用户提交审核后，超级管理员审核通过，作品会自动出现在这里。</p>
      </div>
      <div class="stats">
        <article><b>{{ stats.total }}</b><em>已入库作品</em></article>
        <article><b>{{ stats.image }}</b><em>图片</em></article>
        <article><b>{{ stats.model }}</b><em>3D模型</em></article>
        <article><b>{{ stats.users }}</b><em>用户数</em></article>
      </div>
    </section>

    <section class="filters">
      <label><span>用户ID</span><input v-model.trim="userId" type="number" placeholder="按用户ID查询" @keyup.enter="load" /></label>
      <label><span>类型</span><select v-model="type" @change="load"><option value="all">全部</option><option value="image">图片</option><option value="model">3D模型</option></select></label>
      <label class="keyword"><span>关键词</span><input v-model.trim="keyword" placeholder="标题 / 提示词 / 资产编号 / 用户名" @keyup.enter="load" /></label>
      <button type="button" :disabled="loading" @click="load">{{ loading ? '查询中…' : '查询库存' }}</button>
    </section>

    <section v-if="rows.length" class="asset-grid">
      <article v-for="a in rows" :key="a.id" class="asset-card">
        <div class="cover" @click="open(a)">
          <img v-if="a.assetType === 'image' && previewUrl(a)" :src="previewUrl(a)" alt="库存图片" />
          <img v-else-if="a.assetType === 'model' && a.previewUrl" :src="a.previewUrl" alt="3D预览" />
          <div v-else class="model-cover">3D</div>
          <i>{{ assetTypeText(a.assetType) }}</i>
          <strong>已入库</strong>
        </div>
        <div class="body">
          <header><b>{{ a.title || '未命名作品' }}</b><small>#{{ a.id }}</small></header>
          <p>{{ a.prompt || '暂无提示词' }}</p>
          <div class="meta"><span>用户：{{ a.createdByName || '-' }} / {{ a.createdBy || '-' }}</span><span>{{ (a.format || '-').toUpperCase() }}</span></div>
          <div class="meta"><span>入库时间：{{ fmtTime(a.updatedAt || a.createdAt) }}</span></div>
          <div class="actions">
            <button type="button" @click="open(a)">查看</button>
            <a v-if="fileUrl(a)" :href="fileUrl(a)" target="_blank" rel="noopener">打开文件</a>
            <a v-if="downloadUrl(a)" :href="downloadUrl(a)" target="_blank" rel="noopener">下载</a>
          </div>
        </div>
      </article>
    </section>

    <section v-else class="empty">
      <b>{{ loading ? '正在加载库存…' : '暂无已入库作品' }}</b>
      <span>审核页点击“通过”后，作品会自动进入这里。</span>
    </section>

    <Teleport to="body">
      <div v-if="active" class="modal" @click.self="close">
        <div class="modal-card">
          <header>
            <div><b>{{ active.title || '库存作品预览' }}</b><span>{{ assetTypeText(active.assetType) }} · 用户ID {{ active.createdBy }} · 已入库</span></div>
            <button type="button" @click="close">×</button>
          </header>
          <main>
            <img v-if="active.assetType === 'image' && previewUrl(active)" :src="previewUrl(active)" alt="作品预览" />
            <div v-else class="model-large"><b>3D模型文件</b><span>点击下方按钮打开或下载GLB模型。</span></div>
          </main>
          <footer>
            <a v-if="fileUrl(active)" :href="fileUrl(active)" target="_blank" rel="noopener">打开原文件</a>
            <a v-if="downloadUrl(active)" :href="downloadUrl(active)" target="_blank" rel="noopener">下载文件</a>
          </footer>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.inventory-page{padding:24px;display:flex;flex-direction:column;gap:18px;color:#0f172a}.inventory-hero{display:grid;grid-template-columns:minmax(0,1.1fr) minmax(420px,.9fr);gap:20px;padding:30px;border-radius:30px;background:linear-gradient(135deg,#fffaf4,#f7efe7 48%,#eefaf7);border:1px solid rgba(148,163,184,.18);box-shadow:0 22px 60px rgba(15,23,42,.08);overflow:hidden}.inventory-hero span{display:inline-flex;padding:7px 10px;border-radius:999px;background:#fff6ed;color:#b4532a;font-size:11px;font-weight:950;letter-spacing:1.6px}.inventory-hero h1{margin:12px 0 10px;font-size:32px;letter-spacing:-.05em}.inventory-hero p{margin:0;max-width:720px;color:#64748b;line-height:1.75}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}.stats article{padding:18px;border-radius:20px;background:rgba(255,255,255,.76);border:1px solid rgba(148,163,184,.18)}.stats b{display:block;font-size:30px}.stats em{font-style:normal;color:#64748b;font-size:12px;font-weight:900}.filters{display:grid;grid-template-columns:170px 150px minmax(260px,1fr) 120px;gap:12px;align-items:end;padding:16px;border-radius:22px;background:#fff;border:1px solid rgba(148,163,184,.18);box-shadow:0 12px 34px rgba(15,23,42,.05)}label span{display:block;margin-bottom:7px;color:#475569;font-size:12px;font-weight:900}input,select{width:100%;height:42px;box-sizing:border-box;border:1px solid #e2e8f0;border-radius:13px;background:#f8fafc;padding:0 12px;outline:none}input:focus,select:focus{border-color:#b4532a;box-shadow:0 0 0 3px rgba(180,83,42,.12)}.filters button{height:42px;border:0;border-radius:13px;background:#111827;color:#fff;font-weight:900;cursor:pointer}.filters button:disabled{opacity:.55}.asset-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(330px,1fr));gap:16px}.asset-card{overflow:hidden;border-radius:24px;background:#fff;border:1px solid rgba(148,163,184,.16);box-shadow:0 16px 42px rgba(15,23,42,.07)}.cover{position:relative;height:230px;background:#111827;cursor:pointer;overflow:hidden}.cover img{width:100%;height:100%;object-fit:cover;display:block;transition:.25s}.cover:hover img{transform:scale(1.03)}.model-cover{height:100%;display:flex;align-items:center;justify-content:center;color:#fff;font-size:48px;font-weight:950;background:radial-gradient(circle at 70% 20%,rgba(20,184,166,.35),transparent 35%),linear-gradient(135deg,#111827,#334155)}.cover i,.cover strong{position:absolute;top:12px;padding:7px 9px;border-radius:999px;background:rgba(255,255,255,.92);font-size:11px;font-style:normal;font-weight:950}.cover i{left:12px;color:#334155}.cover strong{right:12px;color:#047857;background:#ecfdf5}.body{padding:16px}.body header{display:flex;justify-content:space-between;gap:12px}.body b{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.body small{color:#94a3b8}.body p{min-height:44px;margin:12px 0;color:#475569;font-size:13px;line-height:1.55;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.meta{display:flex;justify-content:space-between;gap:10px;margin-top:8px;color:#64748b;font-size:12px}.actions{display:flex;flex-wrap:wrap;gap:8px;margin-top:14px}.actions button,.actions a,footer a{height:38px;display:inline-flex;align-items:center;padding:0 13px;border:1px solid #e2e8f0;border-radius:12px;background:#fff;color:#334155;text-decoration:none;font-weight:900;cursor:pointer}.actions a:last-child,footer a:last-child{background:#0f766e;color:#fff;border-color:#0f766e}.empty{padding:64px 20px;text-align:center;border-radius:24px;background:#fff;border:1px dashed #cbd5e1;color:#64748b}.empty b,.empty span{display:block}.empty b{margin-bottom:8px;color:#0f172a;font-size:18px}.modal{position:fixed;inset:0;z-index:220;background:rgba(15,23,42,.62);backdrop-filter:blur(8px);display:flex;align-items:center;justify-content:center;padding:24px}.modal-card{width:min(980px,96vw);max-height:92vh;display:flex;flex-direction:column;border-radius:26px;background:#fff;overflow:hidden;box-shadow:0 28px 90px rgba(0,0,0,.28)}.modal-card header,.modal-card footer{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:16px 18px;border-bottom:1px solid #e2e8f0}.modal-card footer{justify-content:flex-end;border-top:1px solid #e2e8f0;border-bottom:0}.modal-card header b,.modal-card header span{display:block}.modal-card header span{margin-top:4px;color:#64748b;font-size:12px}.modal-card header button{width:38px;height:38px;border:0;border-radius:12px;background:#f1f5f9;font-size:24px;color:#475569}.modal-card main{min-height:320px;overflow:auto;background:#f8fafc;display:flex;align-items:center;justify-content:center}.modal-card main img{max-width:100%;max-height:72vh;object-fit:contain}.model-large{display:flex;flex-direction:column;align-items:center;gap:10px;color:#64748b}.model-large b{font-size:28px;color:#0f172a}@media(max-width:980px){.inventory-page{padding:16px}.inventory-hero{grid-template-columns:1fr}.stats{grid-template-columns:repeat(2,1fr)}.filters{grid-template-columns:1fr 1fr}.keyword,.filters button{grid-column:1/-1}}@media(max-width:640px){.filters,.asset-grid{grid-template-columns:1fr}.cover{height:210px}}
</style>
