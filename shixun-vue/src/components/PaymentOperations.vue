<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'

defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const orders = ref<any[]>([])
const exceptions = ref<any[]>([])
const reconciliation = ref<any[]>([])
const configuration = ref<any>({ missing: [] })
const loading = ref(false)
const actionOrder = ref('')
const billDate = ref(new Date(Date.now() - 86400000).toISOString().slice(0, 10))

const statusLabels: Record<string, string> = {
  pending: '待支付', manual_review: '待人工核验', paid: '已到账', failed: '下单失败',
  closed: '已关闭', expired: '已过期', payment_exception: '支付异常待核对',
  refund_requested: '退款申请中', refund_processing: '退款处理中', refund_unknown: '退款待核对',
  refund_exception: '退款异常', refund_failed: '退款未完成', refunded: '已退款',
}
const statusText = (value: string) => statusLabels[value] || value || '-'
const money = (fen: any) => (Number(fen || 0) / 100).toFixed(2)
const time = (value: any) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'
const hasManual = computed(() => orders.value.some(o => o.status === 'manual_review'))
const hasExceptions = computed(() => exceptions.value.length > 0)

async function api(path: string, init?: RequestInit) {
  const response = await fetch(path, init)
  const body = await response.json().catch(() => null)
  if (!response.ok) throw new Error(body?.message || `HTTP ${response.status}`)
  return body
}

async function load() {
  loading.value = true
  try {
    const [orderRows, exceptionRows, billRows, configurationResult] = await Promise.all([
      api('/api/payments/admin/orders'),
      api('/api/payments/admin/exceptions'),
      api('/api/payments/admin/reconciliation/daily'),
      api('/api/payments/admin/configuration'),
    ])
    orders.value = Array.isArray(orderRows) ? orderRows : []
    exceptions.value = Array.isArray(exceptionRows) ? exceptionRows : []
    reconciliation.value = Array.isArray(billRows) ? billRows : []
    configuration.value = configurationResult || { missing: [] }
  } catch (error: any) {
    emit('alert', `加载支付运营数据失败：${error?.message || error}`, 'error')
  } finally { loading.value = false }
}

async function runAction(order: any, action: 'confirm' | 'reconcile' | 'refund' | 'exception-refund') {
  if (!order?.orderNo || actionOrder.value) return
  const labels = { confirm: '确认这笔人工收款已经到账吗？', reconcile: '现在向微信官方查询这笔订单吗？', refund: '确认发起原路退款吗？', 'exception-refund': '确认对未入账支付异常发起退款吗？' }
  if (!window.confirm(labels[action])) return
  actionOrder.value = order.orderNo
  try {
    const result = await api(`/api/payments/admin/orders/${encodeURIComponent(order.orderNo)}/${action}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: action.includes('refund') ? JSON.stringify({ reason: '后台支付运营处理' }) : undefined,
    })
    emit('alert', result?.message || '操作已提交', 'success')
    await load()
  } catch (error: any) {
    emit('alert', `操作失败：${error?.message || error}`, 'error')
  } finally { actionOrder.value = '' }
}

async function reconcileBills() {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(billDate.value)) {
    emit('alert', '请输入正确的账单日期', 'error'); return
  }
  loading.value = true
  try {
    reconciliation.value = await api(`/api/payments/admin/reconciliation/daily?billDate=${encodeURIComponent(billDate.value)}`, { method: 'POST' })
    emit('alert', '微信账单已下载并完成本地比对', 'success')
    await load()
  } catch (error: any) {
    emit('alert', `账单对账失败：${error?.message || error}`, 'error')
  } finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <div class="payment-page">
    <section class="payment-hero">
      <div><span>PAYMENT OPERATIONS</span><h1>支付运营</h1><p>管理充值与打样费订单、退款和微信账单对账。到账状态只以微信官方回调或后台对账为准。</p></div>
      <div class="payment-stats"><article><b>{{ orders.length }}</b><small>订单总数</small></article><article><b>{{ orders.filter(o => o.status === 'paid').length }}</b><small>已到账</small></article><article :class="{ warning: hasManual }"><b>{{ orders.filter(o => o.status === 'manual_review').length }}</b><small>待人工核验</small></article><article :class="{ danger: hasExceptions }"><b>{{ exceptions.length }}</b><small>异常待处理</small></article></div>
    </section>

    <section class="payment-panel payment-readiness" :class="{ ready: configuration.officialPaymentReady }">
      <header><div><h2>官方微信支付状态</h2><p>{{ configuration.officialPaymentReady ? '充值和打样费将由微信官方回调自动确认到账。' : '尚未开放用户支付入口，请先补齐以下配置。' }}</p></div><span>{{ configuration.officialPaymentReady ? '已就绪' : '待配置' }}</span></header>
      <p v-if="!configuration.officialPaymentReady" class="missing">{{ (configuration.missing || []).join('、') || '请检查支付配置' }}</p>
      <p v-else class="ready-note">私钥、公钥和 HTTPS 回调均已通过服务器配置检查。支付成功后无需用户提交核验或管理员手动加积分。</p>
    </section>

    <section class="payment-panel">
      <header><div><h2>支付订单</h2><p>支持充值积分和审核通过后的打样费支付。</p></div><button :disabled="loading" @click="load">刷新</button></header>
      <div class="table-wrap"><table><thead><tr><th>订单号</th><th>用户</th><th>业务</th><th>金额</th><th>渠道</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody><tr v-for="order in orders" :key="order.orderNo"><td><b>{{ order.orderNo }}</b></td><td>{{ order.username || `用户 ${order.userId}` }}</td><td>{{ order.packageName }}</td><td>¥{{ money(order.amountFen) }}</td><td>{{ order.channel === 'manual_wechat_qr' ? '收款码' : order.channel === 'wechat_jsapi' ? '小程序支付' : '微信扫码' }}</td><td><span class="status" :class="order.status">{{ statusText(order.status) }}</span></td><td>{{ time(order.createdAt) }}</td><td class="actions"><button v-if="order.status === 'manual_review'" :disabled="actionOrder === order.orderNo" @click="runAction(order, 'confirm')">确认到账</button><button v-if="['pending','payment_exception'].includes(order.status) && order.channel !== 'manual_wechat_qr'" :disabled="actionOrder === order.orderNo" @click="runAction(order, 'reconcile')">官方对账</button><button v-if="order.status === 'paid'" :disabled="actionOrder === order.orderNo" @click="runAction(order, 'refund')">原路退款</button><button v-if="order.status === 'payment_exception'" :disabled="actionOrder === order.orderNo" @click="runAction(order, 'exception-refund')">异常退款</button><span v-if="!['manual_review','pending','payment_exception','paid'].includes(order.status)">-</span></td></tr></tbody></table></div>
      <div v-if="!orders.length" class="empty">暂无支付订单</div>
    </section>

    <section class="payment-grid">
      <div class="payment-panel"><header><div><h2>异常订单</h2><p>异常订单不得直接补积分，先官方对账或退款。</p></div></header><div class="exception-list"><article v-for="item in exceptions" :key="`${item.orderNo}-${item.refundNo || ''}`"><b>{{ item.orderNo }}</b><span>{{ statusText(item.status) }}<template v-if="item.refundStatus"> / {{ statusText(item.refundStatus) }}</template></span><small>用户 {{ item.userId }} · ¥{{ money(item.amountFen) }} · {{ time(item.updatedAt) }}</small><div><button @click="runAction(item, 'reconcile')">官方对账</button><button v-if="item.status === 'payment_exception'" @click="runAction(item, 'exception-refund')">异常退款</button></div></article></div><div v-if="!exceptions.length" class="empty">暂无异常订单</div></div>
      <div class="payment-panel"><header><div><h2>微信账单对账</h2><p>下载交易、退款和资金账单并保留比对结果。</p></div></header><div class="bill-form"><label>账单日期<input v-model="billDate" type="date" /></label><button :disabled="loading" @click="reconcileBills">下载并对账</button></div><div class="bill-list"><article v-for="item in reconciliation" :key="`${item.billDate}-${item.billType}`"><b>{{ item.billDate }} · {{ item.billType }}</b><span :class="item.status">{{ item.status }}</span><small>{{ item.resultSummary || '暂无结果' }}</small></article></div></div>
    </section>
  </div>
</template>

<style scoped>
.payment-page{display:grid;gap:18px;padding:24px}.payment-hero,.payment-panel{background:#fff;border:1px solid #e2e8f0;border-radius:22px;box-shadow:0 12px 34px rgba(15,23,42,.05)}.payment-hero{display:grid;grid-template-columns:1fr auto;gap:22px;padding:28px}.payment-hero span{color:#0f766e;font-size:11px;font-weight:900;letter-spacing:2px}.payment-hero h1{margin:10px 0 6px;font-size:32px}.payment-hero p,.payment-panel header p{margin:0;color:#64748b;line-height:1.6}.payment-stats{display:grid;grid-template-columns:repeat(4,120px);gap:10px}.payment-stats article{padding:16px;border-radius:16px;background:#f8fafc}.payment-stats article.warning{background:#fff7ed}.payment-stats article.danger{background:#fef2f2}.payment-stats b{display:block;font-size:28px}.payment-stats small{color:#64748b}.payment-panel{padding:18px}.payment-panel header{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:14px}.payment-panel h2{margin:0 0 4px;font-size:19px}.payment-panel header button,.bill-form button,.actions button,.exception-list button{border:0;border-radius:10px;background:#0f766e;color:#fff;padding:9px 13px;font-weight:800;cursor:pointer}.payment-panel header button:disabled,.actions button:disabled,.bill-form button:disabled{opacity:.55}.payment-readiness{border-color:#f0c987;background:#fffcf6}.payment-readiness.ready{border-color:#8fceae;background:#f4fcf7}.payment-readiness header>span{padding:6px 10px;border-radius:999px;background:#fff1d6;color:#a8600d;font-size:12px;font-weight:800}.payment-readiness.ready header>span{background:#ddf6e6;color:#147344}.missing,.ready-note{margin:0;color:#9a5c16;line-height:1.65;font-size:13px}.ready-note{color:#27734b}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;min-width:980px}th,td{padding:12px 10px;border-bottom:1px solid #edf2f7;text-align:left;font-size:13px;white-space:nowrap}th{color:#64748b;font-size:12px}.status{display:inline-flex;padding:5px 8px;border-radius:999px;background:#f1f5f9;color:#475569;font-size:12px;font-weight:800}.status.paid{background:#ecfdf5;color:#047857}.status.manual_review,.status.pending{background:#fff7ed;color:#b45309}.status.payment_exception,.status.refund_exception,.status.refund_unknown{background:#fef2f2;color:#b91c1c}.status.refunded{background:#eff6ff;color:#1d4ed8}.actions{display:flex;gap:6px}.actions button{padding:6px 9px;font-size:12px}.payment-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.exception-list,.bill-list{display:grid;gap:10px}.exception-list article,.bill-list article{display:grid;gap:5px;padding:13px;border:1px solid #e2e8f0;border-radius:14px;background:#fbfdff}.exception-list article>span{color:#b45309;font-size:12px;font-weight:800}.exception-list small,.bill-list small{color:#64748b}.exception-list article>div{display:flex;gap:7px;margin-top:4px}.exception-list button{padding:6px 9px;font-size:12px}.bill-form{display:flex;align-items:end;gap:10px;margin-bottom:14px}.bill-form label{display:grid;gap:5px;color:#64748b;font-size:12px;font-weight:800}.bill-form input{height:38px;border:1px solid #cbd5e1;border-radius:10px;padding:0 10px}.empty{text-align:center;color:#94a3b8;padding:26px}@media(max-width:1000px){.payment-hero,.payment-grid{grid-template-columns:1fr}.payment-stats{grid-template-columns:repeat(2,1fr)}table{min-width:900px}}
</style>
