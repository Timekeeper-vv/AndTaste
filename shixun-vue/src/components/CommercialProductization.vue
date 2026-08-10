<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { User } from '../types'

const props = defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const tab = ref<'quotes' | 'consignments'>('quotes')
const status = ref('new')
const loading = ref(false)
const rows = ref<any[]>([])
const comment = ref('')
const quoteUnit = ref('')
const quoteTotal = ref('')
const quoteLead = ref('')
const busyId = ref<number | null>(null)

const statuses = computed(() => tab.value === 'quotes'
  ? [{ value: 'new', label: '待处理' }, { value: 'processing', label: '处理中' }, { value: 'quoted', label: '已报价' }, { value: 'rejected', label: '已驳回' }, { value: 'all', label: '全部' }]
  : [{ value: 'pending_review', label: '待审核' }, { value: 'need_materials', label: '待补材料' }, { value: 'approved', label: '已通过' }, { value: 'rejected', label: '已驳回' }, { value: 'all', label: '全部' }])

const statusLabel: Record<string, string> = { new: '待处理', processing: '处理中', quoted: '已报价', accepted: '已接受', rejected: '已驳回', closed: '已关闭', pending_review: '待审核', need_materials: '待补材料', approved: '已通过', withdrawn: '已撤回' }

async function load() {
  loading.value = true
  try {
    const endpoint = tab.value === 'quotes' ? 'quote-requests' : 'consignment-applications'
    const response = await fetch(`/api/commercial/admin/${endpoint}?status=${encodeURIComponent(status.value)}`, { cache: 'no-store' })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    rows.value = Array.isArray(data) ? data : []
  } catch (error: any) {
    emit('alert', `加载${tab.value === 'quotes' ? '报价' : '代销'}申请失败：${error?.message || error}`, 'error')
  } finally { loading.value = false }
}

function switchTab(next: 'quotes' | 'consignments') { tab.value = next; status.value = next === 'quotes' ? 'new' : 'pending_review'; void load() }
function formatTime(value?: string) { return value ? String(value).replace('T', ' ').slice(0, 19) : '-' }
function openQuote(row: any) { quoteUnit.value = row.quotedUnitPrice || ''; quoteTotal.value = row.quotedTotalPrice || ''; quoteLead.value = row.quotedLeadTime || ''; comment.value = row.operatorComment || '' }

async function update(row: any, nextStatus: string) {
  busyId.value = row.id
  try {
    const endpoint = tab.value === 'quotes' ? 'quote-requests' : 'consignment-applications'
    const body = tab.value === 'quotes'
      ? { status: nextStatus, quotedUnitPrice: quoteUnit.value || null, quotedTotalPrice: quoteTotal.value || null, quotedLeadTime: quoteLead.value, operatorComment: comment.value }
      : { status: nextStatus, operatorComment: comment.value }
    const response = await fetch(`/api/commercial/admin/${endpoint}/${row.id}`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    emit('alert', '申请状态已更新', 'success')
    await load()
  } catch (error: any) { emit('alert', `更新失败：${error?.message || error}`, 'error') }
  finally { busyId.value = null }
}

onMounted(load)
</script>

<template>
  <div class="commercial-page">
    <section class="hero"><div><span>COMMERCIAL MVP</span><h1>商品化与代销审核</h1><p>处理 C 端用户的报价、打样和渠道代销申请。样例参数只用于方向判断，正式价格、授权和交期必须经过人工确认。</p></div><div class="rule"><b>试运行代销规则</b><strong>预售 / 按单生产</strong><small>创作者 70% · 平台服务 30%</small><small>不承诺平台备货，正式以协议为准</small></div></section>
    <section class="toolbar"><button :class="{ active: tab === 'quotes' }" @click="switchTab('quotes')">报价 / 打样申请</button><button :class="{ active: tab === 'consignments' }" @click="switchTab('consignments')">渠道代销申请</button><select v-model="status" @change="load"><option v-for="item in statuses" :key="item.value" :value="item.value">{{ item.label }}</option></select><button class="refresh" @click="load">{{ loading ? '加载中…' : '刷新' }}</button></section>
    <section v-if="rows.length" class="list"><article v-for="row in rows" :key="row.id" class="row"><div class="row-head"><div><b>{{ row.productName }}</b><small>{{ row.requestNo || row.applicationNo }} · 用户 {{ row.username }} / ID {{ row.userId }}</small></div><em :class="row.status">{{ statusLabel[row.status] || row.status }}</em></div><div class="meta"><span>作品 ID：{{ row.assetId || '未关联' }}</span><span v-if="tab === 'quotes'">数量：{{ row.quantity }} · {{ row.requestType }}</span><span v-else>渠道：{{ row.channelName || '未指定' }}</span><span>提交：{{ formatTime(row.createdAt) }}</span></div><p v-if="row.note">用户说明：{{ row.note }}</p><p v-if="row.copyrightBasis" class="rights">权利依据：{{ row.copyrightBasis }} · 已确认声明：{{ row.copyrightConfirmed ? '是' : '否' }}<span v-if="row.authorizationNote"> · {{ row.authorizationNote }}</span></p><div v-if="tab === 'quotes'" class="quote-form"><label>单价 <input v-model="quoteUnit" inputmode="decimal" placeholder="待确认" @focus="openQuote(row)" /></label><label>总价 <input v-model="quoteTotal" inputmode="decimal" placeholder="待确认" /></label><label>交期 <input v-model="quoteLead" placeholder="例如：打样 10-15 天" /></label></div><label class="comment"><span>运营备注</span><input v-model="comment" placeholder="通过说明、补材料要求或驳回原因" /></label><footer><button v-if="tab === 'quotes'" class="processing" :disabled="busyId === row.id" @click="update(row, 'processing')">接单处理中</button><button v-if="tab === 'quotes'" class="quoted" :disabled="busyId === row.id" @click="update(row, 'quoted')">保存报价</button><button v-if="tab === 'consignments'" class="materials" :disabled="busyId === row.id" @click="update(row, 'need_materials')">要求补材料</button><button class="approve" :disabled="busyId === row.id" @click="update(row, tab === 'quotes' ? 'accepted' : 'approved')">{{ tab === 'quotes' ? '确认可执行' : '通过代销审核' }}</button><button class="reject" :disabled="busyId === row.id" @click="update(row, 'rejected')">驳回</button></footer></article></section><section v-else class="empty"><b>{{ loading ? '正在加载申请…' : '暂无匹配申请' }}</b><span>用户提交报价或代销申请后会显示在这里。</span></section>
  </div>
</template>

<style scoped>
.commercial-page{padding:24px;color:#0f172a;display:flex;flex-direction:column;gap:18px}.hero{display:grid;grid-template-columns:1fr 300px;gap:20px;padding:28px;border:1px solid #e2e8f0;border-radius:22px;background:linear-gradient(135deg,#fff,#fff7ed 52%,#ecfdf5);box-shadow:0 16px 45px rgba(15,23,42,.06)}.hero span{display:inline-block;padding:6px 9px;border-radius:999px;background:#ffedd5;color:#9a3412;font-size:11px;font-weight:950;letter-spacing:1.5px}.hero h1{margin:12px 0 8px;font-size:30px}.hero p{max-width:760px;margin:0;color:#64748b;line-height:1.7}.rule{display:flex;flex-direction:column;gap:8px;padding:16px;border:1px solid #99f6e4;border-radius:16px;background:#f0fdfa}.rule b{color:#0f766e}.rule strong{font-size:20px;color:#134e4a}.rule small{color:#475569}.toolbar{display:flex;align-items:center;gap:9px;padding:13px;border:1px solid #e2e8f0;border-radius:16px;background:#fff}.toolbar button,.toolbar select,.row button{height:38px;border:1px solid #cbd5e1;border-radius:10px;background:#fff;padding:0 13px;color:#334155;font-weight:850;cursor:pointer}.toolbar button.active{background:#111827;color:#fff;border-color:#111827}.toolbar select{margin-left:auto;padding:0 10px}.toolbar .refresh{background:#f8fafc}.list{display:grid;grid-template-columns:repeat(auto-fill,minmax(480px,1fr));gap:15px}.row{padding:17px;border:1px solid #e2e8f0;border-radius:18px;background:#fff;box-shadow:0 10px 26px rgba(15,23,42,.05)}.row-head{display:flex;justify-content:space-between;gap:12px}.row-head b{display:block;font-size:17px}.row-head small{display:block;margin-top:6px;color:#64748b;font-size:12px}.row-head em{height:max-content;padding:5px 8px;border-radius:999px;background:#f1f5f9;color:#475569;font-style:normal;font-size:11px;font-weight:900;white-space:nowrap}.row-head em.approved,.row-head em.accepted{background:#dcfce7;color:#15803d}.row-head em.rejected{background:#fee2e2;color:#b91c1c}.row-head em.need_materials{background:#fff7ed;color:#c2410c}.meta{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px;color:#64748b;font-size:12px}.row p{margin:12px 0;padding:10px;border-radius:11px;background:#f8fafc;color:#475569;font-size:13px;line-height:1.55}.row .rights{background:#fff7ed;color:#7c2d12}.quote-form{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:12px}.quote-form label,.comment span{display:block;color:#64748b;font-size:12px;font-weight:850}.quote-form input,.comment input{box-sizing:border-box;width:100%;height:36px;margin-top:5px;border:1px solid #cbd5e1;border-radius:9px;padding:0 9px;background:#fff;color:#334155}.comment{display:block;margin-top:12px}.row footer{display:flex;flex-wrap:wrap;gap:8px;margin-top:13px}.row footer button.processing{background:#eef2ff;color:#4338ca}.row footer button.quoted{background:#fffbeb;color:#a16207}.row footer button.materials{background:#fff7ed;color:#c2410c}.row footer button.approve{background:#0f766e;color:#fff;border-color:#0f766e}.row footer button.reject{background:#dc2626;color:#fff;border-color:#dc2626}.row button:disabled{opacity:.55;cursor:not-allowed}.empty{padding:60px 20px;border:1px dashed #cbd5e1;border-radius:18px;background:#fff;text-align:center}.empty b,.empty span{display:block}.empty span{margin-top:8px;color:#64748b}@media(max-width:850px){.hero{grid-template-columns:1fr}.toolbar{flex-wrap:wrap}.toolbar select{margin-left:0}.list{grid-template-columns:1fr}.quote-form{grid-template-columns:1fr}}
</style>
