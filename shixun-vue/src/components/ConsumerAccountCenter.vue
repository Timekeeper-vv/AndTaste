<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { User } from '../types'
import { consumerApi as api, consumerJson as json, rows, money, dateText } from '../utils/consumerApi'
defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [message: string, type?: 'success' | 'error']; logout: [] }>()
const account = ref<any>(null), packages = ref<any[]>([]), orders = ref<any[]>([])
const loading = ref(false), error = ref(''), cancelOpen = ref(false), password = ref(''), confirmation = ref(''), cancelling = ref(false)
async function load() {
  loading.value = true; error.value = ''
  const results = await Promise.allSettled([api('/api/creative/ai/consumer-credits/account'), api('/api/payments/packages'), api('/api/payments/orders')])
  const balance = results[0], offers = results[1], history = results[2]
  if (balance.status === 'fulfilled') account.value = balance.value
  if (offers.status === 'fulfilled') packages.value = rows(offers.value.items)
  if (history.status === 'fulfilled') orders.value = rows(history.value.items)
  error.value = results.filter(result => result.status === 'rejected').map(result => (result as PromiseRejectedResult).reason?.message).join('；')
  loading.value = false
}
async function cancelAccount() {
  if (cancelling.value || confirmation.value !== '注销账号') return
  if (!window.confirm('确认申请注销当前账号？请先处理未完成的订单和支付。')) return
  cancelling.value = true
  try { const result = await api<any>('/api/users/me/cancellation', json('POST', { confirmation: confirmation.value, ...(password.value ? { password: password.value } : {}) })); emit('alert', result.message || '注销申请已提交', 'success'); emit('logout') }
  catch (failure: any) { emit('alert', failure.message, 'error') }
  finally { cancelling.value = false }
}
const labels: Record<string, string> = { pending: '待支付', paid: '已支付', closed: '已关闭', expired: '已过期', failed: '失败', manual_review: '支付核对中', payment_exception: '支付异常待处理', refund_exception: '退款核对中' }
defineExpose({ refresh: load })
onMounted(load)
</script>
<template><section class="account-center"><header><div><h2>我的账号</h2><p>{{ currentUser.username }}</p></div><button :disabled="loading" @click="load">刷新</button></header><p v-if="error" role="alert">{{ error }}</p><article class="balance"><span>创作积分</span><strong>{{ account ? Number(account.balance || 0) : '—' }}</strong><p>充值请前往微信小程序「我的 → 充值积分」。网页端可查看余额、套餐和支付记录。</p></article><div class="packages"><article v-for="item in packages" :key="item.code || item.id"><b>{{ item.name || item.packageName }}</b><p>到账 {{ item.credits || item.creditAmount || item.points }} 积分 · ¥{{ money(item.amountYuan ?? Number(item.amountFen || 0) / 100) }}</p><small>{{ item.baseCredits }} 基础 + {{ item.bonusCredits }} 赠送 · 约可生成 {{ item.estimatedFlatImages }} 张平面图</small></article></div><h3>支付记录</h3><p v-if="!orders.length && !loading">暂无支付记录</p><article v-for="item in orders" :key="item.orderNo" class="payment-row"><div><b>{{ item.packageName || item.subject || item.orderType || '支付订单' }}</b><small>{{ item.orderNo }} · {{ dateText(item.createdAt) }}</small></div><div>{{ labels[item.status] || item.status }}<p>¥{{ money(item.amountYuan ?? Number(item.amountFen || 0) / 100) }}</p></div></article><button class="cancel-toggle" @click="cancelOpen = !cancelOpen">账号注销</button><form v-if="cancelOpen" @submit.prevent="cancelAccount"><p>注销申请由服务端检查未完成业务；不符合条件时会返回具体原因。</p><label>当前登录密码（微信登录可留空）<input v-model="password" type="password" autocomplete="current-password" :disabled="cancelling" /></label><label>输入“注销账号”确认<input v-model="confirmation" :disabled="cancelling" /></label><button :disabled="cancelling || confirmation !== '注销账号'">{{ cancelling ? '正在处理…' : '确认申请注销' }}</button></form></section></template>
<style scoped>
.account-center{max-width:1000px;margin:auto;padding:28px;border-radius:24px;background:#f3faf7;color:#2f4539}.account-center header,.payment-row{display:flex;align-items:center;justify-content:space-between;gap:20px}.account-center button,.account-center input{padding:10px 14px;border:1px solid #cfe4db;border-radius:12px;background:white;color:#40755e}.account-center small{display:block;margin-top:7px;color:#8d9f95}.balance{padding:26px;border-radius:22px;background:linear-gradient(120deg,#6cebc6,#419b93);color:white}.balance strong{display:block;margin:12px 0;font-size:48px}.balance p{line-height:1.7}.packages{display:flex;flex-wrap:wrap;gap:12px;margin:20px 0}.packages article,.payment-row{padding:18px;border:1px solid #e0eee6;border-radius:16px;background:white}.packages article small{color:#718275}.payment-row{margin:10px 0;font-size:13px}.cancel-toggle{margin-top:26px}.account-center form{padding:20px;background:white;border-radius:16px;margin-top:16px}.account-center label{display:grid;gap:8px;margin:14px 0}.account-center button:disabled{opacity:.5}@media(max-width:600px){.account-center{padding:16px}.payment-row{align-items:flex-start;flex-direction:column}}
</style>
