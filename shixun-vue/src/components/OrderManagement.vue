<script setup lang="ts">
import { onMounted, ref } from 'vue'
const rows = ref<any[]>([]), loading = ref(false)
function orderTypeText(r: any) { return r.orderType === 'professional' ? '专业作品打样' : r.orderType === 'multiview' ? '多视图打样' : r.orderType === 'model' ? '3D模型打样' : '生产申请' }
function textStatus(r: any) {
  if (r.samplePaymentStatus === 'paid' || (r.orderType === 'professional' && r.status === 'processing')) return '已支付打样费 · 生产中'
  if (r.samplePaymentStatus === 'unpaid') return '待用户支付打样费'
  if (r.samplePaymentStatus === 'pending') return '支付处理中'
  if (r.samplePaymentStatus === 'manual_review') return '待管理员核验'
  return r.status === 'review' ? '待审核' : r.status === 'processing' ? '生产中' : r.status || '-'
}
async function load() { loading.value = true; try { const r = await fetch('/api/creative/ai/admin/orders?size=500', { cache: 'no-store' }); rows.value = r.ok ? await r.json() : [] } finally { loading.value = false } }
onMounted(load)
</script>
<template><div class="orders-page"><header><div><span>ORDER CENTER</span><h1>订单管理</h1><p>统一查看图片、3D 模型、多视图和专业作品的打样与生产阶段。</p></div><button @click="load" :disabled="loading">{{ loading ? '加载中…' : '刷新' }}</button></header><div class="table-wrap"><table><thead><tr><th>订单类型</th><th>用户</th><th>产品号</th><th>产品</th><th>状态</th><th>打样信息</th><th>提交时间</th></tr></thead><tbody><tr v-for="r in rows" :key="`${r.orderType}-${r.id}`"><td>{{ orderTypeText(r) }}</td><td>{{ r.username || r.userId }}</td><td>{{ r.productNo || '-' }}</td><td>{{ r.productName || r.title || r.assetTitle || '-' }}</td><td>{{ textStatus(r) }}</td><td>{{ r.sampleMaterial || '-' }} · ¥{{ r.sampleFeeYuan || '-' }} · {{ r.sampleLeadTime || '待定' }}</td><td>{{ String(r.createdAt || '').replace('T',' ').slice(0,19) }}</td></tr></tbody></table><div v-if="!loading && !rows.length" class="empty">暂无订单</div></div></div></template>
<style scoped>.orders-page{padding:28px;color:#25372d}.orders-page header{display:flex;justify-content:space-between;align-items:flex-start;margin-bottom:18px}.orders-page header span{font-size:11px;letter-spacing:2px;color:#72907b}.orders-page h1{margin:5px 0;font-size:28px}.orders-page p{margin:0;color:#718078}.orders-page button{border:0;border-radius:8px;background:#527b66;color:#fff;padding:9px 15px;font-weight:700}.table-wrap{overflow:auto;background:#fff;border:1px solid #e1e9e3;border-radius:10px}.table-wrap table{width:100%;min-width:950px;border-collapse:collapse;font-size:13px}.table-wrap th,.table-wrap td{padding:13px 12px;border-bottom:1px solid #edf1ed;text-align:left;white-space:nowrap}.table-wrap th{background:#f6faf6;color:#567060}.empty{text-align:center;padding:50px;color:#819187}</style>
