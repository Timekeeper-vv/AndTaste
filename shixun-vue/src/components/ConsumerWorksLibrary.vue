<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { consumerApi as api, consumerJson as json, downloadConsumerFile, rows, dateText } from '../utils/consumerApi'
import { requestAssetPreviewAccess } from '../utils/assetAccess'
const emit = defineEmits<{ alert: [message: string, type?: 'success' | 'error']; sample: [asset: any]; commercial: [asset: any] }>()
const assets = ref<any[]>([])
const loading = ref(false)
const error = ref('')
const keyword = ref('')
const busyId = ref<number | null>(null)
const statuses: Record<string, string> = { draft: '待审核', review: '审核中', approved: '审核通过', rejected: '未通过' }
async function load() {
  loading.value = true; error.value = ''
  try {
    assets.value = rows(await api('/api/creative/ai/assets?size=500'))
    await Promise.all(assets.value.map(async item => {
      try { const access = await requestAssetPreviewAccess(item.id); item.previewUrl = access.previewUrl; item.contentUrl = access.url } catch { item.previewUrl = '' }
    }))
  } catch (failure: any) { error.value = failure.message }
  finally { loading.value = false }
}
async function review(item: any) {
  if (busyId.value) return
  busyId.value = item.id
  try { await api(`/api/creative/ai/consumer-assets/${item.id}/submit-review`, json('PUT', { purpose: 'personal' })); await load(); emit('alert', '作品已提交审核', 'success') }
  catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { busyId.value = null }
}
async function download(item: any) {
  if (busyId.value) return
  busyId.value = item.id
  try { const access = await requestAssetPreviewAccess(item.id); await downloadConsumerFile(access.url, `${item.title || '作品'}-${item.id}.${item.format || (item.assetType === 'model' ? 'glb' : 'png')}`) }
  catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { busyId.value = null }
}
async function preview(item: any, material = false) {
  const tab = window.open('about:blank', '_blank')
  if (tab) tab.opener = null
  try {
    const access = material
      ? await api<any>(`/api/creative/ai/assets/${item.id}/material-lab-access`, json('POST', {}))
      : await requestAssetPreviewAccess(item.id)
    const contentUrl = new URL(access.modelUrl || access.url, window.location.origin).href
    const query = new URLSearchParams({ assetId: String(item.id), title: item.title || '作品', modelUrl: contentUrl })
    if (material) query.set('labToken', access.accessToken)
    const url = item.assetType === 'model' ? `/${material ? 'material-lab' : 'model-preview'}.html?${query}` : contentUrl
    if (tab) tab.location.href = url
    else emit('alert', '浏览器拦截了预览窗口，请允许打开新窗口后重试', 'error')
  } catch (failure: any) { tab?.close(); emit('alert', failure.message, 'error') }
}
onMounted(load)
</script>
<template><section class="works-library"><header><h2>我的作品</h2><div><input v-model="keyword" placeholder="搜索作品名称或产品号" /><button :disabled="loading" @click="load">刷新作品</button></div></header><p v-if="loading">正在加载作品…</p><p v-if="error">{{ error }}</p><div class="works-grid"><article v-for="item in assets.filter(item => !keyword.trim() || String(item.title || '').includes(keyword.trim()) || String(item.productNo || '').includes(keyword.trim()))" :key="item.id"><img v-if="item.previewUrl" :src="item.previewUrl" :alt="item.title" @click="preview(item)" /><div v-else class="placeholder">{{ item.assetType === 'model' ? '3D' : '图' }}</div><h3>{{ item.title || `作品 ${item.id}` }}</h3><small>{{ item.productNo || '产品号待关联' }} · {{ dateText(item.createdAt) }}</small><p>{{ statuses[item.status] || item.status || '已保存' }}<template v-if="item.reviewComment"> · {{ item.reviewComment }}</template></p><div class="actions"><button @click="preview(item)">{{ item.assetType === 'model' ? '查看3D' : '查看原图' }}</button><button v-if="item.assetType === 'model'" @click="preview(item, true)">材质编辑</button><button :disabled="!!busyId" @click="download(item)">下载作品</button><button v-if="item.assetType === 'model' && !['review', 'approved'].includes(item.status)" :disabled="!!busyId" @click="review(item)">提交审核</button><button v-if="item.status === 'approved' && item.assetType === 'model'" @click="emit('sample', item)">申请打样</button><button @click="emit('commercial', item)">询价 / 渠道合作</button></div></article></div><p v-if="!loading && !assets.length && !error">暂无作品，从创意工坊开始创作吧。</p></section></template>
<style scoped>
.works-library{padding:26px;border-radius:24px;background:#f4fbf8;color:#263e31}.works-library header{display:flex;align-items:center;justify-content:space-between;gap:16px}.works-library header div{display:flex;gap:12px}.works-library input,.works-library button{padding:10px 14px;border:1px solid #d2e2d9;border-radius:12px;background:white;color:#42745a}.works-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:20px;margin-top:24px}.works-grid article{overflow:hidden;padding:18px;border-radius:22px;background:white;border:1px solid #dfede5}.works-grid img,.placeholder{width:100%;height:210px;object-fit:contain;background:#eff6f1;border-radius:16px;cursor:pointer}.placeholder{display:grid;place-items:center;font-size:40px;color:#50886c}.works-grid small{color:#8b9d90}.works-grid p{color:#698372;font-size:13px}.actions{display:flex;flex-wrap:wrap;gap:8px}.actions button{cursor:pointer;font-size:12px}.actions button:disabled{opacity:.5}@media(max-width:600px){.works-library{padding:16px}.works-library header{flex-direction:column;align-items:flex-start}.works-library header div{width:100%}.works-library input{min-width:0;flex:1}}
</style>
