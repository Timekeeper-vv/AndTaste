<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { consumerApi as api, consumerJson as json, rows, money, dateText } from '../utils/consumerApi'
const emit = defineEmits<{ alert: [message: string, type?: 'success' | 'error'] }>()
const artworks = ref<any[]>([]), categories = ref<any[]>([]), orders = ref<any[]>([]), keyword = ref(''), category = ref('')
const detail = ref<any>(null), skuId = ref(''), quantity = ref(1), busy = ref(false), error = ref(''), tab = ref('shop')
const skus = computed(() => rows(detail.value?.skus).filter(item => item.status === 'on_sale' && Number(item.stock || 0) > 0))
const sku = computed(() => skus.value.find(item => String(item.id) === skuId.value))
async function load() {
  busy.value = true; error.value = ''
  try { const results = await Promise.all([api(`/api/creative/artworks?keyword=${encodeURIComponent(keyword.value)}${category.value ? `&categoryId=${encodeURIComponent(category.value)}` : ''}`), api('/api/creative/categories'), api('/api/creative/orders')]); artworks.value = rows(results[0]); categories.value = rows(results[1]); orders.value = rows(results[2]) }
  catch (failure: any) { error.value = failure.message }
  finally { busy.value = false }
}
async function open(item: any) {
  busy.value = true
  try { detail.value = await api(`/api/creative/artworks/${item.id}`); skuId.value = ''; quantity.value = 1; if (!Array.isArray(detail.value.skus)) detail.value.skus = rows(await api('/api/creative/skus')).filter(row => Number(row.artworkId) === Number(item.id)); skuId.value = String(skus.value[0]?.id || '') }
  catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { busy.value = false }
}
async function checkout() {
  if (busy.value || !sku.value) return
  if (!Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > Number(sku.value.stock)) { emit('alert', '数量超出库存或格式不正确', 'error'); return }
  if (!window.confirm('当前商城仅支持创建待支付订单，商品支付与发货尚未开放。是否创建订单？')) return
  busy.value = true
  try { const result = await api<any>('/api/creative/orders', json('POST', { items: [{ skuId: Number(skuId.value), quantity: quantity.value }], paymentMethod: 'wechat', remark: '网页端商城订单' })); emit('alert', `订单 ${result.orderNo || ''} 已创建，状态为待支付。商品支付与发货尚未开放。`, 'success'); detail.value = null; tab.value = 'orders'; await load() }
  catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { busy.value = false }
}
const labels: Record<string, string> = { pending_pay: '待支付', paid: '已支付', shipped: '已发货', completed: '已完成', cancelled: '已取消' }
onMounted(load)
</script>
<template><section class="marketplace"><header><h2>文创商城</h2><div><button @click="tab = 'shop'">浏览作品</button><button @click="tab = 'orders'; load()">我的商城订单</button></div></header><p class="notice">商品支付与发货尚未开放；提交后仅创建待支付订单。</p><p v-if="error" role="alert">{{ error }}</p><template v-if="tab === 'shop'"><form class="filters" @submit.prevent="load"><input v-model="keyword" placeholder="搜索文创作品" /><select v-model="category"><option value="">全部分类</option><option v-for="item in categories" :key="item.id" :value="item.id">{{ item.name }}</option></select><button :disabled="busy">搜索</button></form><div class="artwork-grid"><button v-for="item in artworks" :key="item.id" class="artwork" :disabled="busy" @click="open(item)"><img v-if="item.imageUrl || item.thumbnailUrl || item.coverImageUrl || item.coverUrl" :src="item.imageUrl || item.thumbnailUrl || item.coverImageUrl || item.coverUrl" :alt="item.title" /><b>{{ item.title || item.name }}</b><small>{{ item.designerName || item.categoryName }}</small></button></div></template><template v-else><p v-if="!orders.length && !busy">暂无商城订单</p><article v-for="item in orders" :key="item.id" class="order"><b>{{ item.orderNo }}</b><p>{{ labels[item.orderStatus] || item.orderStatus }} · ¥{{ money(item.payAmount) }} · {{ dateText(item.createdAt) }}</p><p v-for="line in rows(item.items)" :key="line.id">{{ line.productName || line.artworkTitle || line.skuName }} × {{ line.quantity }}</p></article></template><p v-if="busy">正在加载…</p><div v-if="detail" class="mask" @click.self="detail = null"><section class="detail"><button class="close" @click="detail = null">关闭</button><h2>{{ detail.title }}</h2><img v-if="detail.imageUrl || detail.thumbnailUrl || detail.coverImageUrl || detail.coverUrl" :src="detail.imageUrl || detail.thumbnailUrl || detail.coverImageUrl || detail.coverUrl" :alt="detail.title" /><p>{{ detail.story || detail.description }}</p><label>产品规格<select v-model="skuId"><option v-for="item in skus" :key="item.id" :value="String(item.id)">{{ item.productName || item.skuName || item.name }} · ¥{{ money(item.price || item.salePrice) }} · 库存 {{ item.stock }}</option></select></label><label>数量<input v-model.number="quantity" type="number" min="1" :max="sku?.stock" /></label><button :disabled="busy || !sku" @click="checkout">创建待支付订单</button><p v-if="!skus.length">暂无可下单的产品规格</p></section></div></section></template>
<style scoped>
.marketplace{padding:26px;border-radius:24px;background:#f4faf7;color:#294338}.marketplace header,.filters{display:flex;align-items:center;justify-content:space-between;gap:14px}.marketplace button,.marketplace input,.marketplace select{padding:10px 14px;border:1px solid #cfe2d8;border-radius:12px;background:white;color:#3f735a}.marketplace button{cursor:pointer}.notice{color:#947443;font-size:13px}.filters input{flex:1;min-width:0}.artwork-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:20px;margin-top:24px}.artwork{text-align:left}.artwork img{width:100%;height:220px;object-fit:cover;border-radius:16px}.artwork b,.artwork small{display:block;margin-top:12px}.order{padding:20px;border:1px solid #d9e7df;border-radius:18px;background:white;margin-top:14px}.mask{position:fixed;inset:0;z-index:100;background:#102b237a;display:grid;place-items:center;padding:20px}.detail{max-height:85vh;overflow:auto;width:min(560px,100%);box-sizing:border-box;padding:24px;border-radius:24px;background:white}.detail>img{width:100%;max-height:320px;object-fit:contain}.detail label{display:grid;gap:8px;margin:14px 0}.close{float:right}@media(max-width:600px){.marketplace{padding:16px}.marketplace header{align-items:flex-start;flex-direction:column}.filters{flex-wrap:wrap}}
</style>
