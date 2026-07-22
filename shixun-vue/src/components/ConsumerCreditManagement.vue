<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { User } from '../types'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const accounts = ref<any[]>([])
const transactions = ref<any[]>([])
const loading = ref(false)
const search = ref('')
const txUserId = ref('')
const recharge = reactive({ userId: '', amount: 100, remark: '管理员充值' })
const setBalance = reactive({ userId: '', balance: 0, remark: '管理员直接设置额度余额' })

const stats = computed(() => {
  const totalBalance = accounts.value.reduce((s, x) => s + Number(x.balance || 0), 0)
  const consumed = accounts.value.reduce((s, x) => s + Number(x.totalConsumed || 0), 0)
  const users = accounts.value.length
  const frozen = accounts.value.reduce((s, x) => s + Number(x.frozenBalance || 0), 0)
  return { users, totalBalance, consumed, frozen }
})

function money(v: any) { return Number(v || 0).toFixed(2).replace(/\.00$/, '') }
function time(v?: string) { return v ? String(v).replace('T',' ').slice(0,19) : '-' }
function bizText(v?: string) {
  const map: Record<string,string> = { image2d:'2D生图', image_to_3d:'图生3D', text_to_3d:'文生3D', model_convert:'模型转换', admin_recharge:'管理员充值', admin_set_balance:'直接设置额度', initial:'初始化额度' }
  return map[String(v || '')] || String(v || '-')
}
function statusText(v?: string) {
  const map: Record<string,string> = { pending:'预扣中', completed:'已完成', refunded:'已退回' }
  return map[String(v || '')] || String(v || '-')
}
function txAmountText(t: any) {
  if (t.direction === 'recharge') return `+${money(t.amount)}`
  if (t.direction === 'consume') return `-${money(t.amount)}`
  const diff = Number(t.balanceAfter || 0) - Number(t.balanceBefore || 0)
  return `${diff >= 0 ? '+' : '-'}${money(Math.abs(diff))}`
}
function txDirectionClass(t: any) {
  if (t.direction === 'recharge') return 'recharge'
  if (t.direction === 'consume') return 'consume'
  return Number(t.balanceAfter || 0) >= Number(t.balanceBefore || 0) ? 'recharge' : 'consume'
}

async function loadAccounts() {
  loading.value = true
  try {
    const qs = new URLSearchParams({ size: '500' })
    if (search.value.trim()) qs.set('search', search.value.trim())
    const r = await fetch(`/api/creative/ai/consumer-credits/admin/accounts?${qs}`, { cache: 'no-store', headers: { 'X-Current-Role': props.currentUser.role, 'X-Current-User': props.currentUser.username } })
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.message || `HTTP ${r.status}`)
    accounts.value = await r.json()
  } catch (e: any) {
    emit('alert', '加载额度账户失败：' + (e?.message || e), 'error')
  } finally { loading.value = false }
}

async function loadTransactions(userId?: number) {
  try {
    const qs = new URLSearchParams({ size: '300' })
    const uid = userId ? String(userId) : txUserId.value.trim()
    if (uid) qs.set('userId', uid)
    const r = await fetch(`/api/creative/ai/consumer-credits/admin/transactions?${qs}`, { cache: 'no-store', headers: { 'X-Current-Role': props.currentUser.role, 'X-Current-User': props.currentUser.username } })
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.message || `HTTP ${r.status}`)
    transactions.value = await r.json()
  } catch (e: any) {
    emit('alert', '加载额度流水失败：' + (e?.message || e), 'error')
  }
}

function pickUser(a: any) {
  recharge.userId = String(a.userId)
  setBalance.userId = String(a.userId)
  setBalance.balance = Number(a.balance || 0)
  txUserId.value = String(a.userId)
  loadTransactions(a.userId)
}

async function submitRecharge() {
  if (!recharge.userId || Number(recharge.amount) <= 0) {
    emit('alert', '请填写用户ID和大于0的充值额度', 'error')
    return
  }
  try {
    const r = await fetch('/api/creative/ai/consumer-credits/admin/recharge', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Current-Role': props.currentUser.role, 'X-Current-User': props.currentUser.username },
      body: JSON.stringify({ userId: recharge.userId, amount: String(recharge.amount), remark: recharge.remark }),
    })
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.message || `HTTP ${r.status}`)
    emit('alert', '充值成功', 'success')
    await Promise.all([loadAccounts(), loadTransactions(Number(recharge.userId))])
  } catch (e: any) {
    emit('alert', '充值失败：' + (e?.message || e), 'error')
  }
}

async function submitSetBalance() {
  if (!setBalance.userId || Number(setBalance.balance) < 0) {
    emit('alert', '请填写用户ID和不小于0的目标余额', 'error')
    return
  }
  try {
    const r = await fetch('/api/creative/ai/consumer-credits/admin/set-balance', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Current-Role': props.currentUser.role, 'X-Current-User': props.currentUser.username },
      body: JSON.stringify({ userId: setBalance.userId, balance: String(setBalance.balance), remark: setBalance.remark }),
    })
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.message || `HTTP ${r.status}`)
    emit('alert', '额度设置成功', 'success')
    await Promise.all([loadAccounts(), loadTransactions(Number(setBalance.userId))])
  } catch (e: any) {
    emit('alert', '设置额度失败：' + (e?.message || e), 'error')
  }
}

onMounted(() => { loadAccounts(); loadTransactions() })
</script>

<template>
  <div class="credit-page">
    <section class="hero-card">
      <div><span>CREDIT CENTER</span><h1>C端额度管理</h1><p>超级管理员可查看C端用户余额、冻结额度、消费流水，并支持充值或直接设置用户可用额度。</p></div>
      <div class="stats">
        <article><b>{{ stats.users }}</b><em>C端用户</em></article>
        <article><b>{{ money(stats.totalBalance) }}</b><em>剩余额度</em></article>
        <article><b>{{ money(stats.frozen) }}</b><em>预扣中</em></article>
        <article><b>{{ money(stats.consumed) }}</b><em>已消耗</em></article>
      </div>
    </section>

    <section class="toolbar">
      <label><span>搜索用户</span><input v-model.trim="search" placeholder="用户ID / 账号 / 手机" @keyup.enter="loadAccounts" /></label>
      <button @click="loadAccounts">查询账户</button>
      <label><span>充值用户ID</span><input v-model.trim="recharge.userId" type="number" placeholder="点击左侧用户自动填入" /></label>
      <label><span>充值点数</span><input v-model.number="recharge.amount" type="number" min="1" /></label>
      <label class="remark"><span>充值备注</span><input v-model.trim="recharge.remark" /></label>
      <button class="recharge" @click="submitRecharge">确认充值</button>
    </section>

    <section class="toolbar set-toolbar">
      <label><span>设置用户ID</span><input v-model.trim="setBalance.userId" type="number" placeholder="点击左侧用户自动填入" /></label>
      <label><span>目标可用余额</span><input v-model.number="setBalance.balance" type="number" min="0" /></label>
      <label class="remark"><span>设置备注</span><input v-model.trim="setBalance.remark" /></label>
      <button class="set-balance" @click="submitSetBalance">直接设置额度</button>
      <p>说明：直接设置会把该用户“可用余额”改为目标值，冻结中的生成任务不受影响，并会记录额度流水。</p>
    </section>

    <section class="grid-layout">
      <div class="panel">
        <header><b>额度账户</b><small>点击账户可联动查看流水</small></header>
        <div class="account-list">
          <button v-for="a in accounts" :key="a.userId" @click="pickUser(a)">
            <div><b>{{ a.username }}</b><small>ID {{ a.userId }} · {{ a.phone || '-' }}</small></div>
            <strong>{{ money(a.balance) }} 点</strong>
            <em>冻结 {{ money(a.frozenBalance) }} / 已用 {{ money(a.totalConsumed) }}</em>
          </button>
        </div>
        <p v-if="!accounts.length" class="empty">暂无C端用户额度账户</p>
      </div>

      <div class="panel">
        <header>
          <div><b>额度流水</b><small>预扣 / 完成 / 退回 / 充值</small></div>
          <label><input v-model.trim="txUserId" placeholder="用户ID" @keyup.enter="loadTransactions()" /></label>
          <button @click="loadTransactions()">查询</button>
        </header>
        <div class="tx-list">
          <article v-for="t in transactions" :key="t.id">
            <div><b>{{ bizText(t.bizType) }}</b><small>{{ t.username || '-' }} / ID {{ t.userId }} · {{ time(t.createdAt) }}</small></div>
            <strong :class="txDirectionClass(t)">{{ txAmountText(t) }}</strong>
            <em :class="t.status">{{ statusText(t.status) }}</em>
            <p>{{ t.remark || '-' }}</p>
          </article>
        </div>
        <p v-if="!transactions.length" class="empty">暂无额度流水</p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.credit-page{padding:24px;display:flex;flex-direction:column;gap:18px}.hero-card{display:grid;grid-template-columns:1.1fr .9fr;gap:18px;padding:28px;border-radius:28px;background:linear-gradient(135deg,#fff,#fff7ed 48%,#ecfdf5);border:1px solid #e2e8f0;box-shadow:0 20px 55px rgba(15,23,42,.07)}.hero-card span{display:inline-flex;padding:7px 10px;border-radius:999px;background:#ffedd5;color:#b45309;font-size:11px;font-weight:950;letter-spacing:1.6px}.hero-card h1{margin:10px 0;font-size:32px}.hero-card p{margin:0;color:#64748b;line-height:1.7}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.stats article{padding:16px;border-radius:18px;background:rgba(255,255,255,.75);border:1px solid #e2e8f0}.stats b{display:block;font-size:26px}.stats em{font-style:normal;color:#64748b;font-size:12px;font-weight:900}.toolbar{display:grid;grid-template-columns:1.2fr 110px 120px 120px 1fr 110px;gap:10px;align-items:end;padding:16px;border-radius:20px;background:#fff;border:1px solid #e2e8f0}.toolbar span,.panel header small{display:block;color:#64748b;font-size:12px;font-weight:800}.toolbar input,.panel header input{width:100%;height:40px;box-sizing:border-box;border:1px solid #cbd5e1;border-radius:12px;padding:0 12px}.toolbar button,.panel header button{height:40px;border:0;border-radius:12px;background:#111827;color:#fff;font-weight:900;cursor:pointer}.toolbar .recharge{background:#0f766e}.toolbar .set-balance{background:#7c2d12}.set-toolbar{grid-template-columns:150px 150px 1fr 130px 1.1fr}.set-toolbar p{margin:0;color:#64748b;font-size:12px;line-height:1.5}.grid-layout{display:grid;grid-template-columns:minmax(320px,.8fr) minmax(480px,1.2fr);gap:18px}.panel{padding:18px;border-radius:22px;background:#fff;border:1px solid #e2e8f0;box-shadow:0 12px 34px rgba(15,23,42,.05)}.panel>header{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:14px}.panel header b{font-size:18px}.panel header label{margin-left:auto}.account-list,.tx-list{display:flex;flex-direction:column;gap:10px;max-height:680px;overflow:auto}.account-list button{display:grid;grid-template-columns:1fr auto;gap:6px;padding:14px;border:1px solid #e2e8f0;border-radius:16px;background:#f8fafc;text-align:left;cursor:pointer}.account-list button:hover{border-color:#0f766e;background:#f0fdfa}.account-list b,.tx-list b{display:block;color:#0f172a}.account-list small,.tx-list small{display:block;color:#64748b;margin-top:3px}.account-list strong{font-size:20px;color:#0f766e}.account-list em{grid-column:1/-1;color:#64748b;font-size:12px;font-style:normal}.tx-list article{display:grid;grid-template-columns:1fr auto auto;gap:8px;align-items:center;padding:13px;border:1px solid #e2e8f0;border-radius:16px;background:#fbfdff}.tx-list strong{font-size:18px}.tx-list strong.recharge{color:#0f766e}.tx-list strong.consume{color:#b91c1c}.tx-list em{padding:4px 8px;border-radius:999px;font-style:normal;font-size:12px;font-weight:900}.tx-list em.pending{background:#fff7ed;color:#b45309}.tx-list em.completed{background:#ecfdf5;color:#047857}.tx-list em.refunded{background:#eff6ff;color:#1d4ed8}.tx-list p{grid-column:1/-1;margin:0;color:#64748b;font-size:12px}.empty{text-align:center;color:#94a3b8;padding:28px}@media(max-width:1100px){.hero-card,.grid-layout{grid-template-columns:1fr}.toolbar{grid-template-columns:1fr 1fr}.remark{grid-column:1/-1}.stats{grid-template-columns:repeat(2,1fr)}}
</style>
