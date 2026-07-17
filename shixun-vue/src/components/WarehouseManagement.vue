<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

type WarehouseView = 'products' | 'inventory' | 'inbound' | 'outbound' | 'pick' | 'alerts'
const props = withDefaults(defineProps<{ initialView?: WarehouseView }>(), { initialView: 'inventory' })
const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const active = ref<WarehouseView>(props.initialView)
const loading = ref(false)
const dashboard = ref<any>({})
const locations = ref<any[]>([])
const inventory = ref<any[]>([])
const products = ref<any[]>([])
const productTotal = ref(0)
const primaryCategories = ref<any[]>([])
const secondaryCategories = ref<any[]>([])
const productFilter = ref({ keyword: '', primaryCategory: '', secondaryCategory: '', page: 1, pageSize: 100 })
const inboundList = ref<any[]>([])
const outboundList = ref<any[]>([])
const pickTasks = ref<any[]>([])
const alerts = ref<any[]>([])
const aiReport = ref<any>(null)
const inboundForm = ref<any>({ itemType: 'SKU', itemCode: '', itemName: '', spec: '', unit: '件', qty: 100, unitCost: 0, locationCode: 'A-01-01', safetyStock: 20, maxStock: 9999, sourceType: 'production', supplier: '产品表/生产入库', operator: '仓库员', remark: '按产品主数据入库' })
const outboundForm = ref<any>({ inventoryId: '', qty: 10, orderNo: '', purpose: '订单发货', receiver: '客户', operator: '仓库员' })

const selectedInventory = computed(() => inventory.value.find(i => Number(i.id) === Number(outboundForm.value.inventoryId)))
const totalPages = computed(() => Math.max(1, Math.ceil(productTotal.value / productFilter.value.pageSize)))

async function getJson(url: string) { const r = await fetch(url); if (!r.ok) throw new Error(await r.text()); return r.json() }
async function postJson(url: string, body: any = {}) { const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }); if (!r.ok) throw new Error(await r.text()); return r.json() }

async function loadProducts() {
  const q = new URLSearchParams()
  q.set('page', String(productFilter.value.page))
  q.set('pageSize', String(productFilter.value.pageSize))
  if (productFilter.value.keyword) q.set('keyword', productFilter.value.keyword)
  if (productFilter.value.primaryCategory) q.set('primaryCategory', productFilter.value.primaryCategory)
  if (productFilter.value.secondaryCategory) q.set('secondaryCategory', productFilter.value.secondaryCategory)
  const data = await getJson(`/api/warehouse/products?${q.toString()}`)
  products.value = data.items || []
  productTotal.value = Number(data.total || 0)
  primaryCategories.value = data.primaryCategories || []
  secondaryCategories.value = data.secondaryCategories || []
}
async function load() {
  loading.value = true
  try {
    const [d, l, i, ib, ob, p, a] = await Promise.all([getJson('/api/warehouse/dashboard'), getJson('/api/warehouse/locations'), getJson('/api/warehouse/inventory'), getJson('/api/warehouse/inbound'), getJson('/api/warehouse/outbound'), getJson('/api/warehouse/pick-tasks'), getJson('/api/warehouse/alerts')])
    dashboard.value = d; locations.value = l; inventory.value = i; inboundList.value = ib; outboundList.value = ob; pickTasks.value = p; alerts.value = a
    if (!outboundForm.value.inventoryId && i.length) outboundForm.value.inventoryId = i[0].id
    await loadProducts()
  } catch (e: any) { emit('alert', `加载仓储失败：${e.message || e}`, 'error') }
  finally { loading.value = false }
}
function applyProductFilter() { productFilter.value.page = 1; loadProducts().catch((e: any) => emit('alert', `加载产品失败：${e.message || e}`, 'error')) }
function changeProductPage(delta: number) { productFilter.value.page = Math.min(totalPages.value, Math.max(1, productFilter.value.page + delta)); loadProducts().catch((e: any) => emit('alert', `加载产品失败：${e.message || e}`, 'error')) }
function useProduct(p: any) {
  inboundForm.value.itemType = 'SKU'
  inboundForm.value.itemCode = p.productCode
  inboundForm.value.itemName = p.productName
  inboundForm.value.spec = p.specDescription || p.coldCategory || p.secondaryCategory || ''
  inboundForm.value.unit = '件'
  inboundForm.value.unitCost = Number(p.productCostUnitPrice || p.companyCostPrice || 0)
  inboundForm.value.locationCode = p.locationName || inboundForm.value.locationCode || 'A-01-01'
  inboundForm.value.supplier = '产品表主数据'
  active.value = 'inbound'
}
async function inbound() { loading.value = true; try { const r = await postJson('/api/warehouse/inbound', inboundForm.value); emit('alert', `${r.message}：${r.inboundNo}`, 'success'); await load(); active.value = 'inventory' } catch (e: any) { emit('alert', `入库失败：${e.message || e}`, 'error') } finally { loading.value = false } }
async function outbound() { loading.value = true; try { const r = await postJson('/api/warehouse/outbound', { ...outboundForm.value, inventoryId: Number(outboundForm.value.inventoryId) }); emit('alert', `${r.message}：${r.pickNo}`, 'success'); await load(); active.value = 'pick' } catch (e: any) { emit('alert', `出库失败：${e.message || e}`, 'error') } finally { loading.value = false } }
async function completePick(id: number) { loading.value = true; try { const r = await postJson(`/api/warehouse/pick-tasks/${id}/complete`); emit('alert', r.message, 'success'); await load() } catch (e: any) { emit('alert', `拣货失败：${e.message || e}`, 'error') } finally { loading.value = false } }
async function refreshAlerts() { loading.value = true; try { const r = await postJson('/api/warehouse/alerts/refresh'); emit('alert', `${r.message}：${r.alertCount}条`, 'success'); await load(); active.value = 'alerts' } catch (e: any) { emit('alert', `刷新预警失败：${e.message || e}`, 'error') } finally { loading.value = false } }
async function runAiReport() { loading.value = true; try { aiReport.value = await postJson('/api/warehouse/alerts/ai-report'); emit('alert', 'AI仓储报告已生成', 'success') } catch (e: any) { emit('alert', `AI报告失败：${e.message || e}`, 'error') } finally { loading.value = false } }
function money(v: any) { return Number(v || 0).toFixed(2) }
function statusText(s: string) { return ({ pending: '待拣货', picking: '拣货中', done: '已完成', shipped: '已出库', cancelled: '已取消' } as any)[s] || s }
function levelText(s: string) { return ({ critical: '严重', warning: '预警', info: '提示' } as any)[s] || s }
watch(() => props.initialView, v => { if (v) active.value = v })
onMounted(() => { active.value = props.initialView; load() })
</script>

<template>
  <div class="page warehouse-page">
    <div class="page-header warehouse-hero">
      <div>
        <p class="eyebrow">SMART WAREHOUSE</p>
        <h2 class="page-title">产品主数据 · 智能库存与出入库</h2>
        <p class="page-desc">已接入供应链产品表，入库可直接从 2026 产品主数据选择 SKU；库存保留出入库、拣货和智能预警闭环。</p>
      </div>
      <button class="btn btn-secondary" :disabled="loading" @click="load">刷新</button>
    </div>

    <div class="stats-row">
      <div class="stat-card"><div class="stat-label">产品主数据</div><div class="stat-num primary">{{ dashboard.productCount ?? '-' }}</div></div>
      <div class="stat-card"><div class="stat-label">库存品类</div><div class="stat-num success">{{ dashboard.itemCount ?? '-' }}</div></div>
      <div class="stat-card"><div class="stat-label">总库存</div><div class="stat-num warning">{{ money(dashboard.totalStock) }}</div></div>
      <div class="stat-card"><div class="stat-label">可用库存</div><div class="stat-num purple">{{ money(dashboard.availableStock) }}</div></div>
      <div class="stat-card"><div class="stat-label">预警</div><div class="stat-num info">{{ dashboard.alertCount ?? '-' }}</div></div>
    </div>

    <div class="tabs">
      <button :class="{ active: active === 'products' }" @click="active = 'products'">产品主数据</button>
      <button :class="{ active: active === 'inventory' }" @click="active = 'inventory'">库存台账</button>
      <button :class="{ active: active === 'inbound' }" @click="active = 'inbound'">入库</button>
      <button :class="{ active: active === 'outbound' }" @click="active = 'outbound'">出库</button>
      <button :class="{ active: active === 'pick' }" @click="active = 'pick'">拣货任务</button>
      <button :class="{ active: active === 'alerts' }" @click="active = 'alerts'">智能预警</button>
    </div>

    <section v-if="active === 'products'" class="panel-card">
      <div class="panel-head">
        <div><h3>产品主数据</h3><p>来自“产品表（供应链和项目维护）”，用于出入库选品、分类、箱码、成本价和结算价维护。</p></div>
        <button class="mini primary" @click="applyProductFilter">查询</button>
      </div>
      <div class="filter-row">
        <input v-model="productFilter.keyword" placeholder="搜产品名称 / 产品编码 / 箱码 / 冷藏分类" @keyup.enter="applyProductFilter">
        <select v-model="productFilter.primaryCategory" @change="applyProductFilter"><option value="">全部一级分类</option><option v-for="c in primaryCategories" :key="c.value" :value="c.value">{{ c.value }}（{{ c.count }}）</option></select>
        <select v-model="productFilter.secondaryCategory" @change="applyProductFilter"><option value="">全部二级分类</option><option v-for="c in secondaryCategories" :key="c.value" :value="c.value">{{ c.value }}（{{ c.count }}）</option></select>
      </div>
      <table>
        <thead><tr><th>产品编码</th><th>产品名称</th><th>分类</th><th>箱码</th><th>价格/成本</th><th>表内数量/位置</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="p in products" :key="p.id">
            <td><b>{{ p.productCode }}</b><br><small>{{ p.productRefCode || '-' }}</small></td>
            <td><b>{{ p.productName }}</b><br><small>{{ p.specDescription || p.coldCategory || '-' }}</small></td>
            <td>{{ p.primaryCategory || '-' }} / {{ p.secondaryCategory || '-' }}</td>
            <td>{{ p.boxCode || '-' }}</td>
            <td>结算 {{ money(p.settlementUnitPrice) }}<br><small>成本 {{ money(p.productCostUnitPrice || p.companyCostPrice) }}</small></td>
            <td>{{ money(p.initialQty) }}<br><small>{{ p.locationName || '未填库位' }}</small></td>
            <td><button class="mini" @click="useProduct(p)">用于入库</button></td>
          </tr>
        </tbody>
      </table>
      <div class="pager"><span>共 {{ productTotal }} 条，第 {{ productFilter.page }} / {{ totalPages }} 页</span><button class="mini" :disabled="productFilter.page <= 1" @click="changeProductPage(-1)">上一页</button><button class="mini" :disabled="productFilter.page >= totalPages" @click="changeProductPage(1)">下一页</button></div>
    </section>

    <section v-if="active === 'inventory'" class="panel-card">
      <div class="panel-head"><div><h3>库存台账</h3><p>可用库存 = 总库存 - 已锁定待拣货数量；库存行会关联产品主数据分类与成本。</p></div><button class="mini" @click="refreshAlerts">刷新预警</button></div>
      <table><thead><tr><th>编码</th><th>名称</th><th>分类/库位</th><th>总库存</th><th>锁定</th><th>可用</th><th>安全库存</th><th>上次入库</th></tr></thead><tbody><tr v-for="i in inventory" :key="i.id"><td>{{ i.itemCode }}</td><td><b>{{ i.itemName }}</b><br><small>{{ i.spec }}</small></td><td>{{ i.primaryCategory || i.itemType }} / {{ i.secondaryCategory || '-' }}<br><small>{{ i.locationCode }}</small></td><td>{{ money(i.stockQty) }} {{ i.unit }}</td><td>{{ money(i.lockedQty) }}</td><td :class="{ danger: Number(i.availableQty) <= Number(i.safetyStock) }">{{ money(i.availableQty) }}</td><td>{{ money(i.safetyStock) }}</td><td>{{ i.lastInAt || '-' }}</td></tr></tbody></table>
    </section>

    <section v-if="active === 'inbound'" class="grid-2">
      <div class="panel-card"><div class="section-title"><span>入</span><div><h3>新增入库</h3><p>可先在“产品主数据”点击“用于入库”，系统会自动带出编码、名称、分类规格和成本。</p></div></div><div class="form-row"><div><label>类型</label><select v-model="inboundForm.itemType"><option>SKU</option><option>MATERIAL</option><option>PACKAGE</option></select></div><div><label>编码</label><input v-model="inboundForm.itemCode" placeholder="产品编码"></div></div><label>名称</label><input v-model="inboundForm.itemName"><label>规格</label><input v-model="inboundForm.spec"><div class="form-row"><div><label>数量</label><input v-model.number="inboundForm.qty" type="number"></div><div><label>单位</label><input v-model="inboundForm.unit"></div><div><label>单价</label><input v-model.number="inboundForm.unitCost" type="number"></div></div><div class="form-row"><div><label>库位</label><select v-model="inboundForm.locationCode"><option v-for="l in locations" :key="l.locationCode">{{ l.locationCode }}</option><option v-if="inboundForm.locationCode && !locations.some(l => l.locationCode === inboundForm.locationCode)">{{ inboundForm.locationCode }}</option></select></div><div><label>安全库存</label><input v-model.number="inboundForm.safetyStock" type="number"></div><div><label>库存上限</label><input v-model.number="inboundForm.maxStock" type="number"></div></div><label>供应商/来源</label><input v-model="inboundForm.supplier"><button class="btn btn-primary full" :disabled="loading" @click="inbound">确认入库</button></div>
      <div class="panel-card"><h3>最近入库单</h3><div class="doc" v-for="r in inboundList" :key="r.id"><b>{{ r.inboundNo }}</b><span>{{ r.sourceType }} · {{ r.supplier }} · {{ r.createdAt }}</span><small v-for="it in r.items" :key="it.id">{{ it.itemName }} × {{ money(it.qty) }} → {{ it.locationCode }}</small></div></div>
    </section>

    <section v-if="active === 'outbound'" class="grid-2"><div class="panel-card"><div class="section-title"><span>出</span><div><h3>创建出库单</h3><p>出库后先锁定库存并生成拣货任务，拣货完成才真正扣库存。</p></div></div><label>选择库存</label><select v-model="outboundForm.inventoryId"><option v-for="i in inventory" :key="i.id" :value="i.id">{{ i.itemCode }} · {{ i.itemName }} · 可用{{ money(i.availableQty) }}{{ i.unit }} · {{ i.locationCode }}</option></select><div v-if="selectedInventory" class="stock-tip">当前可用：{{ money(selectedInventory.availableQty) }} {{ selectedInventory.unit }}，库位：{{ selectedInventory.locationCode }}</div><div class="form-row"><div><label>出库数量</label><input v-model.number="outboundForm.qty" type="number"></div><div><label>订单号</label><input v-model="outboundForm.orderNo"></div></div><label>用途</label><input v-model="outboundForm.purpose"><label>收货/领用人</label><input v-model="outboundForm.receiver"><button class="btn btn-primary full" :disabled="loading" @click="outbound">创建出库单并生成拣货任务</button></div><div class="panel-card"><h3>最近出库单</h3><div class="doc" v-for="r in outboundList" :key="r.id"><b>{{ r.outboundNo }} · {{ statusText(r.status) }}</b><span>{{ r.orderNo || '无订单' }} · {{ r.purpose }} · {{ r.createdAt }}</span><small v-for="it in r.items" :key="it.id">{{ it.itemName }} × {{ money(it.qty) }} ← {{ it.locationCode }}</small></div></div></section>

    <section v-if="active === 'pick'" class="panel-card"><h3>拣货任务</h3><div class="pick-grid"><div class="pick-card" v-for="p in pickTasks" :key="p.id"><div><b>{{ p.pickNo }}</b><em :class="p.status">{{ statusText(p.status) }}</em></div><span>{{ p.itemName }} × {{ money(p.qty) }}</span><small>库位：{{ p.locationCode }} · 出库单：{{ p.outboundNo }} · 订单：{{ p.orderNo || '-' }}</small><button class="mini primary" :disabled="p.status === 'done' || loading" @click="completePick(p.id)">确认拣货完成并出库</button></div></div></section>

    <section v-if="active === 'alerts'" class="grid-2"><div class="panel-card"><div class="panel-head"><div><h3>智能预警</h3><p>自动识别缺货、低库存、超储。</p></div><div class="actions"><button class="mini" @click="refreshAlerts">刷新预警</button><button class="mini primary" @click="runAiReport">AI仓储报告</button></div></div><div class="alert" v-for="a in alerts" :key="a.id" :class="a.level"><b>{{ levelText(a.level) }} · {{ a.itemName }}</b><span>{{ a.message }}</span><small>{{ a.suggestion }}</small></div><div v-if="!alerts.length" class="empty">暂无打开的预警。</div></div><div class="panel-card"><h3>AI仓储报告</h3><div v-if="aiReport" class="ai-draft"><b>{{ aiReport.reportNo }}</b><pre>{{ aiReport.report }}</pre></div><div v-else class="empty">点击“AI仓储报告”，让AI基于库存和预警给出补货/出库建议。</div></div></section>
  </div>
</template>

<style scoped>
.warehouse-page{background:#f8fafc;min-height:calc(100vh - var(--header-h))}.warehouse-hero{padding:28px;border-radius:20px;color:#fff;background:linear-gradient(135deg,#111827,#166534 45%,#0f766e);box-shadow:var(--shadow-md)}.warehouse-hero .page-title{color:#fff;font-size:28px}.warehouse-hero .page-desc{color:rgba(255,255,255,.82)}.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:2px;color:#bbf7d0;font-weight:800}.tabs{display:flex;gap:10px;flex-wrap:wrap;margin:18px 0}.tabs button{border:1px solid var(--c-border);background:#fff;border-radius:999px;padding:10px 16px;cursor:pointer;font-weight:800;color:#475569}.tabs button.active{background:#166534;color:#fff;border-color:#166534}.grid-2{display:grid;grid-template-columns:1fr 1fr;gap:18px}.panel-card{background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:20px;box-shadow:var(--shadow-sm);overflow:auto}.panel-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:14px}.panel-head h3,.panel-card h3{margin:0 0 4px}.panel-head p{margin:0;color:#64748b;font-size:13px}.filter-row{display:grid;grid-template-columns:2fr 1fr 1fr;gap:10px;margin-bottom:14px}.pager{display:flex;justify-content:flex-end;gap:10px;align-items:center;margin-top:14px;color:#64748b;font-size:13px}.section-title{display:flex;gap:12px;align-items:flex-start;margin-bottom:14px}.section-title>span{background:#166534;color:#fff;border-radius:12px;min-width:38px;height:32px;display:flex;align-items:center;justify-content:center;font-weight:900}.section-title h3{margin:0 0 4px}.section-title p{margin:0;color:#64748b;font-size:13px}.panel-card label{display:block;font-size:12px;font-weight:800;color:#64748b;margin:10px 0 6px}input,select{width:100%;height:40px;border:1px solid var(--c-border);border-radius:10px;padding:0 10px;background:#fff}.form-row{display:grid;grid-template-columns:repeat(3,1fr);gap:10px}.full{width:100%;margin-top:16px}.mini{border:0;background:#f1f5f9;border-radius:999px;padding:8px 12px;cursor:pointer;font-weight:800;white-space:nowrap}.mini.primary{background:#166534;color:#fff}.actions{display:flex;gap:8px}table{width:100%;border-collapse:collapse}th,td{padding:10px 12px;border-bottom:1px solid #eef2f7;text-align:left;font-size:13px;vertical-align:top}th{background:#f8fafc;color:#64748b}td small{color:#64748b}.danger{color:#dc2626;font-weight:900}.doc{border:1px solid #eef2f7;border-radius:12px;padding:12px;margin-bottom:10px;display:flex;flex-direction:column;gap:5px}.doc span,.doc small{color:#64748b;font-size:13px}.stock-tip{margin:10px 0;padding:12px;border-radius:12px;background:#f0fdf4;border:1px solid #bbf7d0;color:#166534;font-weight:800}.pick-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:12px}.pick-card{border:1px solid #e2e8f0;border-radius:14px;padding:14px;display:flex;flex-direction:column;gap:8px}.pick-card div{display:flex;justify-content:space-between;gap:8px}.pick-card span{font-weight:800}.pick-card small{color:#64748b}.pick-card em{font-style:normal;border-radius:999px;padding:3px 8px;font-size:12px;font-weight:900;background:#e2e8f0}.pick-card em.done{background:#dcfce7;color:#166534}.pick-card em.pending{background:#fff7ed;color:#c2410c}.alert{border-radius:14px;padding:14px;margin-bottom:10px;border:1px solid #e2e8f0;display:flex;flex-direction:column;gap:6px}.alert.critical{background:#fef2f2;border-color:#fecaca}.alert.warning{background:#fff7ed;border-color:#fed7aa}.alert.info{background:#eff6ff;border-color:#bfdbfe}.alert span{color:#334155}.alert small{color:#64748b}.empty{text-align:center;color:#64748b;padding:36px;border:1px dashed #cbd5e1;border-radius:14px}.ai-draft{border:1px solid #c4b5fd;background:#faf5ff;border-radius:14px;padding:14px}.ai-draft pre{white-space:pre-wrap;word-break:break-word;line-height:1.7;margin:10px 0 0;color:#334155;font-family:inherit}@media(max-width:1100px){.grid-2,.form-row,.filter-row{grid-template-columns:1fr}.panel-head{flex-direction:column}}
</style>
