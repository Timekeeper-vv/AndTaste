<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { User } from '../types'

defineProps<{ currentUser: User }>()
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const rows = ref<any[]>([])
const topProducts = ref<any[]>([])
const summary = ref<any>({})
const total = ref(0)
const page = ref(1)
const size = ref(50)
const loading = ref(false)
const form = reactive({ year: 2026, projectName: '', productType: '', keyword: '' })
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const money = (value: any) => Number(value || 0).toLocaleString('zh-CN')
const time = (value: any) => value ? String(value).replace('T', ' ').slice(0, 19) : '-'

async function load() {
  loading.value = true
  try {
    const query = new URLSearchParams({ page: String(page.value), size: String(size.value) })
    if (form.year) query.set('year', String(form.year))
    if (form.projectName.trim()) query.set('projectName', form.projectName.trim())
    if (form.productType.trim()) query.set('productType', form.productType.trim())
    if (form.keyword.trim()) query.set('keyword', form.keyword.trim())
    const response = await fetch(`/api/analytics/historical-sales?${query}`, { cache: 'no-store' })
    const data = await response.json().catch(() => null)
    if (!response.ok) throw new Error(data?.message || `HTTP ${response.status}`)
    rows.value = Array.isArray(data?.items) ? data.items : []
    topProducts.value = Array.isArray(data?.topProducts) ? data.topProducts : []
    summary.value = data?.summary || {}
    total.value = Number(data?.total || 0)
  } catch (error: any) { emit('alert', `销售数据加载失败：${error?.message || error}`, 'error') }
  finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function previous() { if (page.value > 1) { page.value--; void load() } }
function next() { if (page.value < totalPages.value) { page.value++; void load() } }
onMounted(load)
</script>

<template>
  <div class="sales-page">
    <section class="hero"><div><span>SALES INTELLIGENCE</span><h1>历史销售数据</h1><p>来源：2026年销售数量.xlsx。数据用于爆款方向分析，不会写入用户订单、库存或支付流水。</p></div><div class="stats"><article><b>{{ money(summary.sales) }}</b><small>累计销量</small></article><article><b>{{ money(summary.loss) }}</b><small>损耗数量</small></article><article><b>{{ money(summary.projects) }}</b><small>项目数</small></article><article><b>{{ money(summary.products) }}</b><small>产品数</small></article></div></section>
    <section class="panel toolbar"><label>年份<input v-model.number="form.year" type="number" min="2000" max="2100" /></label><label>项目/博物馆<input v-model.trim="form.projectName" placeholder="如 国家博物馆" /></label><label>产品类型<input v-model.trim="form.productType" placeholder="如 文具 / 冷冻食品" /></label><label class="wide">关键词<input v-model.trim="form.keyword" placeholder="产品名称、条码或项目" @keyup.enter="search" /></label><button :disabled="loading" @click="search">查询</button></section>
    <section class="grid"><div class="panel"><header><div><h2>销量领先产品</h2><p>当前筛选条件下销量最高的产品。</p></div></header><div class="top-list"><article v-for="(item,index) in topProducts" :key="`${item.productName}-${index}`"><strong>{{ index + 1 }}</strong><div><b>{{ item.productName }}</b><small>{{ item.projectName }} · {{ item.productType }} / {{ item.secondaryType || '综合' }}</small></div><em>{{ money(item.sales) }} 件</em></article></div><div v-if="!topProducts.length" class="empty">暂无销量数据</div></div><div class="panel"><header><div><h2>导入状态</h2><p>部署时会自动执行幂等迁移，重复部署不会重复插入。</p></div></header><div class="import-note"><b>当前数据源</b><span>2026年销售数量.xlsx</span><b>记录总数</b><span>{{ total }} 条</span><b>分析口径</b><span>销量、损耗、项目覆盖和产品类型</span></div></div></section>
    <section class="panel"><header><div><h2>销售明细</h2><p>支持按年份、项目、产品类型和关键词筛选。</p></div><small>第 {{ page }} / {{ totalPages }} 页，共 {{ total }} 条</small></header><div class="table-wrap"><table><thead><tr><th>年份</th><th>项目/博物馆</th><th>产品名称</th><th>条码</th><th>产品类型</th><th>子类型</th><th>1-7月销量</th><th>损耗</th><th>导入时间</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td>{{ item.reportYear }}</td><td>{{ item.projectName || '-' }}</td><td><b>{{ item.productName }}</b></td><td>{{ item.productCode || '-' }}</td><td>{{ item.productType || '-' }}</td><td>{{ item.secondaryType || '-' }}</td><td class="sales">{{ money(item.sales) }}</td><td>{{ money(item.loss) }}</td><td>{{ time(item.importedAt) }}</td></tr></tbody></table></div><div v-if="!rows.length" class="empty">暂无符合条件的记录</div><footer class="pager"><button :disabled="page <= 1 || loading" @click="previous">上一页</button><button :disabled="page >= totalPages || loading" @click="next">下一页</button></footer></section>
  </div>
</template>

<style scoped>
.sales-page{display:grid;gap:18px;padding:24px}.hero,.panel{background:#fff;border:1px solid #e2e8f0;border-radius:22px;box-shadow:0 12px 34px rgba(15,23,42,.05)}.hero{display:grid;grid-template-columns:1fr auto;gap:22px;padding:28px}.hero>div>span{color:#0f766e;font-size:11px;font-weight:900;letter-spacing:2px}.hero h1{margin:10px 0 6px;font-size:32px}.hero p,.panel header p{margin:0;color:#64748b;line-height:1.6}.stats{display:grid;grid-template-columns:repeat(4,120px);gap:10px}.stats article{padding:16px;border-radius:16px;background:#f8fafc}.stats b{display:block;font-size:25px}.stats small{color:#64748b}.panel{padding:18px}.toolbar{display:grid;grid-template-columns:130px 1.1fr 1fr 1.4fr 90px;gap:10px;align-items:end}.toolbar label{display:grid;gap:6px;color:#64748b;font-size:12px;font-weight:800}.toolbar input{height:40px;box-sizing:border-box;border:1px solid #cbd5e1;border-radius:10px;padding:0 11px}.toolbar button,.pager button{height:40px;border:0;border-radius:10px;background:#0f766e;color:#fff;font-weight:800;cursor:pointer}.toolbar button:disabled,.pager button:disabled{opacity:.55}.grid{display:grid;grid-template-columns:1.4fr 1fr;gap:18px}.panel header{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:14px}.panel h2{margin:0 0 4px;font-size:19px}.panel header>small{color:#64748b}.top-list{display:grid;gap:9px}.top-list article{display:grid;grid-template-columns:30px 1fr auto;align-items:center;gap:10px;padding:12px;border:1px solid #e2e8f0;border-radius:14px;background:#fbfdff}.top-list strong{font-size:20px;color:#b4532a;text-align:center}.top-list b,.top-list small{display:block}.top-list small{margin-top:4px;color:#64748b;font-size:12px}.top-list em{font-style:normal;color:#0f766e;font-weight:900}.import-note{display:grid;grid-template-columns:auto 1fr;gap:12px;padding:8px 2px;color:#475569}.import-note b{color:#64748b}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;min-width:1050px}th,td{padding:11px 10px;border-bottom:1px solid #edf2f7;text-align:left;font-size:13px;white-space:nowrap}th{color:#64748b;font-size:12px}.sales{color:#0f766e;font-weight:900}.empty{text-align:center;color:#94a3b8;padding:28px}.pager{display:flex;justify-content:flex-end;gap:8px;margin-top:14px}.pager button{padding:0 15px}@media(max-width:1000px){.hero,.grid{grid-template-columns:1fr}.stats{grid-template-columns:repeat(2,1fr)}.toolbar{grid-template-columns:1fr 1fr}.toolbar .wide{grid-column:1/-1}}
</style>
