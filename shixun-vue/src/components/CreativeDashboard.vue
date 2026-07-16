<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

const emit = defineEmits<{ 'switch-page': [page: string], alert: [msg: string, type?: 'success' | 'error'] }>()
const loading = ref(false)
const production = ref<any>({}), warehouse = ref<any>({}), logistics = ref<any>({})
const workflow = ref<any>({})
const workbench = ref<any>({ orders: [], sources: [] }), assets = ref<any[]>([])
const updatedAt = ref('等待同步')

const orders = computed<any[]>(() => workbench.value.orders || [])
const count = (status: string) => orders.value.filter(o => o.status === status).length
const amount = computed(() => orders.value.reduce((sum, o) => sum + Number(o.totalAmount || 0), 0))
const samples = computed(() => orders.value.filter(o => o.orderType === 'sample').length)
const bulk = computed(() => orders.value.filter(o => o.orderType === 'bulk').length)
const images = computed(() => assets.value.filter(a => a.assetType === 'image'))
const models = computed(() => assets.value.filter(a => a.assetType === 'model'))
const risks = computed(() => Number(warehouse.value.alertCount || 0) + Number(logistics.value.exceptionCount || 0))
const done = computed(() => count('completed') + count('shipped'))
const fulfillment = computed(() => orders.value.length ? Math.round(done.value / orders.value.length * 100) : 0)
const health = computed(() => Math.max(0, 100 - Number(warehouse.value.alertCount || 0) * 5 - Number(logistics.value.exceptionCount || 0) * 8 - count('pending_confirm') * 2))
const healthText = computed(() => health.value >= 90 ? '卓越' : health.value >= 75 ? '稳健' : health.value >= 60 ? '关注' : '预警')
const recentOrders = computed(() => orders.value.slice(0, 7))
const maxPipeline = computed(() => Math.max(1, ...pipeline.value.map(i => i.value)))
const statusText: Record<string, string> = { pending_confirm:'待确认', confirmed:'待下达', producing:'生产中', ready_to_ship:'待发货', shipped:'已发货', completed:'已完成' }
const pipeline = computed(() => [
  { label:'客户确认', value:count('pending_confirm'), color:'#f59e0b' },
  { label:'待下达', value:count('confirmed'), color:'#8b5cf6' },
  { label:'生产执行', value:count('producing'), color:'#3b82f6' },
  { label:'待发货', value:count('ready_to_ship'), color:'#14b8a6' },
  { label:'履约完成', value:done.value, color:'#10b981' }
])
const actions = computed(() => [
  { code:'DESIGN 01', title:'2D 创意生图', desc:'Qwen3 优化提示词与 Tripo 商业出图', page:'creative2d', tone:'indigo' },
  { code:'DESIGN 02', title:'3D 辅助建模', desc:`${models.value.length} 项模型资产 · P/H 系列生成`, page:'creative3d', tone:'violet' },
  { code:'REVIEW 03', title:'智能评估', desc:'设计、市场、生产多维可行性评审', page:'creativeReview', tone:'cyan' },
  { code:'COST 04', title:'智能成本核算', desc:'AI BOM、工艺路线、动态预算与报价', page:'production', tone:'amber' },
  { code:'SAMPLE 05', title:'产品打样', desc:`${samples.value} 张打样订单正在管理`, page:'sampleProduction', tone:'rose' },
  { code:'MASS 06', title:'大货生产', desc:`${bulk.value} 张大货订单全程追踪`, page:'bulkProduction', tone:'blue' },
  { code:'STOCK 07', title:'智能仓储', desc:`${warehouse.value.alertCount || 0} 项库存预警待处理`, page:'warehouseLogistics', tone:'green' },
  { code:'TRACK 08', title:'物流跟踪', desc:`${logistics.value.inTransitCount || 0} 单在途 · 订单号实时绑定`, page:'logistics', tone:'slate' },
  { code:'APPROVAL 09', title:'审批中心', desc:`${workflow.value.pendingCount || 0} 条待审批申请`, page:'approvalCenter', tone:'rose' }
])
function money(v: unknown) { return Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits:2, maximumFractionDigits:2 }) }
async function json(url:string) { const r=await fetch(url); if(!r.ok) throw new Error(await r.text()); return r.json() }
async function load() {
  if (loading.value) return
  loading.value=true
  try {
    const [p,w,l,wb,a,ws]=await Promise.all([json('/api/production/dashboard'),json('/api/warehouse/dashboard'),json('/api/logistics/dashboard'),json('/api/production/workbench'),json('/api/creative/ai/assets'),json('/api/workflows/summary')])
    production.value=p; warehouse.value=w; logistics.value=l; workbench.value=wb; assets.value=a; workflow.value=ws
    updatedAt.value=new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false})
  } catch(e:any) { emit('alert',`经营看板加载失败：${e.message}`,'error') } finally { loading.value=false }
}
function go(page:string){ emit('switch-page',page) }
function hideBroken(e:Event){ (e.target as HTMLImageElement).style.display='none' }
onMounted(load)
</script>

<template>
<div class="page dashboard-page">
  <section class="hero">
    <div class="grid-pattern"></div><div class="glow"></div>
    <div class="hero-copy">
      <div class="kicker"><i></i> AND TASTE · INTELLIGENT OPERATIONS</div>
      <h1>经营驾驶舱</h1>
      <p>从创意资产到生产交付，让每一笔订单、每一道工艺和每一次履约都有据可循。</p>
      <div class="hero-actions">
        <button class="primary" @click="go('creative2d')">启动创意项目 <span>→</span></button>
        <button class="ghost" :disabled="loading" @click="load"><span :class="{spin:loading}">↻</span>{{loading?'数据同步中':'刷新经营数据'}}</button>
      </div>
    </div>
    <div class="health-card">
      <header><div><small>BUSINESS HEALTH</small><strong>经营健康指数</strong></div><em><i></i> LIVE</em></header>
      <div class="health-main">
        <div class="ring" :style="{'--score': `${health * 3.6}deg`}"><div><b>{{health}}</b><span>/100</span></div></div>
        <div><small>当前状态</small><strong>{{healthText}}</strong><p>{{risks?`存在 ${risks} 项供应链风险需要关注`:'生产、库存与履约运行稳定'}}</p></div>
      </div>
      <footer><span>全链路实时监控</span><span>同步于 {{updatedAt}}</span></footer>
    </div>
  </section>

  <section class="metrics">
    <article class="m-indigo"><span>商业订单<small>ORDER VOLUME</small></span><b>{{orders.length}}</b><p>{{samples}} 打样 · {{bulk}} 大货</p></article>
    <article class="m-teal"><span>订单总额<small>ORDER VALUE</small></span><b class="money"><i>¥</i>{{money(amount)}}</b><p>统一商业订单口径</p></article>
    <article class="m-violet"><span>生产执行<small>IN PRODUCTION</small></span><b>{{count('producing')}}</b><p>{{count('confirmed')}} 待下达 · {{count('ready_to_ship')}} 待发货</p></article>
    <article class="m-blue"><span>履约完成率<small>FULFILLMENT</small></span><b>{{fulfillment}}<i>%</i></b><p>{{logistics.inTransitCount||0}} 在途 · {{logistics.signedCount||0}} 签收</p></article>
    <article :class="risks?'m-rose':'m-slate'"><span>供应链风险<small>RISK CONTROL</small></span><b>{{risks}}</b><p>{{warehouse.alertCount||0}} 库存 · {{logistics.exceptionCount||0}} 物流</p></article>
  </section>

  <section class="surface capabilities">
    <div class="section-head"><div><small>CORE CAPABILITIES</small><h2>核心业务能力</h2><p>围绕文创产品商业化全流程，快速进入关键工作台。</p></div><div class="asset-total"><b>{{assets.length}}</b><span>数字资产沉淀</span></div></div>
    <div class="action-grid">
      <button v-for="a in actions" :key="a.page" :class="`tone-${a.tone}`" @click="go(a.page)">
        <small>{{a.code}}</small><i></i><h3>{{a.title}}</h3><p>{{a.desc}}</p><span>进入工作台 →</span>
      </button>
    </div>
  </section>

  <div class="layout">
    <section class="surface orders-panel">
      <div class="section-head"><div><small>ORDER OPERATIONS</small><h2>订单执行中心</h2><p>统一订单号贯穿报价、打样、量产、仓储和物流。</p></div><button class="link-btn" @click="go('sampleProduction')">查看全部订单 →</button></div>
      <div class="pipeline">
        <div v-for="p in pipeline" :key="p.label"><span>{{p.label}}<b>{{p.value}}</b></span><i><em :style="{width:`${Math.max(p.value?12:0,p.value/maxPipeline*100)}%`,background:p.color}"></em></i></div>
      </div>
      <div class="table-wrap"><table><thead><tr><th>统一订单号</th><th>业务类型</th><th>产品 / 项目</th><th>订单金额</th><th>当前状态</th></tr></thead><tbody>
        <tr v-for="o in recentOrders" :key="o.id"><td><strong>{{o.orderNo||'—'}}</strong></td><td><span class="type" :class="o.orderType">{{o.orderType==='sample'?'产品打样':'大货生产'}}</span></td><td>{{o.productName||'未命名产品'}}</td><td><strong>¥{{money(o.totalAmount)}}</strong></td><td><em class="status" :class="o.status"><i></i>{{statusText[o.status]||o.status||'待处理'}}</em></td></tr>
      </tbody></table><div v-if="!recentOrders.length&&!loading" class="empty"><b>暂无商业订单</b><p>从智能成本核算创建第一张订单。</p><button @click="go('production')">创建生产方案</button></div></div>
    </section>

    <aside class="surface priorities">
      <div class="section-head"><div><small>PRIORITY QUEUE</small><h2>经营优先级</h2><p>按风险与交付节点排列</p></div><b class="todo">{{count('pending_confirm')+count('confirmed')+risks}}</b></div>
      <button @click="go('approvalCenter')"><i class="rose">✉</i><span><b>审批待办</b><small>连锁 / 财务申请待处理</small></span><strong>{{workflow.pendingCount||0}}</strong></button>
      <button @click="go('sampleProduction')"><i class="amber">◷</i><span><b>等待客户确认</b><small>确认详细报价与生产内容</small></span><strong>{{count('pending_confirm')}}</strong></button>
      <button @click="go('bulkProduction')"><i class="violet">◇</i><span><b>待下达生产</b><small>已确认订单等待执行</small></span><strong>{{count('confirmed')}}</strong></button>
      <button @click="go('warehouseLogistics')"><i class="rose">△</i><span><b>库存预警</b><small>缺货、低库存与超储风险</small></span><strong>{{warehouse.alertCount||0}}</strong></button>
      <button @click="go('logistics')"><i class="blue">◎</i><span><b>物流异常</b><small>运输停滞与异常节点</small></span><strong>{{logistics.exceptionCount||0}}</strong></button>
      <div class="insight" :class="{safe:!risks}"><i></i><span><b>{{risks?'建议优先处理供应链风险':'当前供应链运行稳定'}}</b><small>{{risks?`库存与物流共有 ${risks} 项异常需要跟进。`:'未发现库存或物流异常，建议关注待确认订单。'}}</small></span></div>
    </aside>
  </div>

  <div class="layout lower">
    <section class="surface assets-panel">
      <div class="section-head"><div><small>CREATIVE ASSET LIBRARY</small><h2>创意资产矩阵</h2><p>AI 图片、3D 模型与生产方案持续沉淀为企业数字资产。</p></div><button class="link-btn" @click="go('creative2d')">进入创意设计 →</button></div>
      <div class="asset-stats"><div><span>全部资产</span><b>{{assets.length}}</b><small>ALL ASSETS</small></div><div><span>图片资产</span><b>{{images.length}}</b><small>2D VISUALS</small></div><div><span>3D 模型</span><b>{{models.length}}</b><small>3D MODELS</small></div><div><span>BOM 方案</span><b>{{production.bomCount||0}}</b><small>PRODUCTION BOM</small></div><div><span>生产项目</span><b>{{workbench.sources?.length||0}}</b><small>ACTIVE PROJECTS</small></div></div>
      <div v-if="images.length" class="gallery"><button v-for="a in images.slice(0,6)" :key="a.id" @click="go('creative2d')"><img :src="a.previewUrl||a.fileUrl" :alt="a.title||'创意资产'" @error="hideBroken"><span>{{a.title||'创意资产'}}</span></button></div>
      <div v-else class="asset-empty">资产库等待第一份创意作品 <button @click="go('creative2d')">立即生成</button></div>
    </section>
    <aside class="surface operations">
      <div class="section-head"><div><small>OPERATIONS HEALTH</small><h2>运营基础盘</h2><p>生产资源与履约能力</p></div></div>
      <div class="op-grid"><div><span>BOM 方案</span><b>{{production.bomCount||0}}</b><small>物料库 {{production.materialCount||0}} 项</small></div><div><span>工艺路线</span><b>{{production.processCount||0}}</b><small>待采购 {{production.pendingPurchaseCount||0}} 项</small></div><div><span>可用库存</span><b>{{Number(warehouse.availableStock||0).toFixed(0)}}</b><small>{{warehouse.itemCount||0}} 个品类</small></div><div><span>物流履约</span><b>{{logistics.shipmentCount||0}}</b><small>{{logistics.inTransitCount||0}} 在途 · {{logistics.signedCount||0}} 签收</small></div></div>
      <button class="wide-btn" @click="go('warehouseLogistics')">查看仓储物流全景 <span>→</span></button>
    </aside>
  </div>
</div>
</template>

<style scoped>
.dashboard-page{--ink:#0b1220;--muted:#718096;min-height:100vh;padding:0;color:var(--ink);background:radial-gradient(circle at 0 0,#eef2ff 0,transparent 25%),linear-gradient(180deg,#f8fafc,#f3f6fa)}button{font:inherit}.hero{position:relative;isolation:isolate;min-height:285px;padding:38px 42px;display:grid;grid-template-columns:1.4fr minmax(330px,.6fr);gap:50px;align-items:center;overflow:hidden;border-radius:24px;color:#fff;background:linear-gradient(120deg,#07101f,#131d39 48%,#172554 76%,#0f766e 140%);box-shadow:0 24px 60px #0f172a2c}.grid-pattern{position:absolute;inset:0;z-index:-2;opacity:.13;background-image:linear-gradient(#ffffff40 1px,transparent 1px),linear-gradient(90deg,#ffffff40 1px,transparent 1px);background-size:48px 48px;mask-image:linear-gradient(90deg,#000,transparent 72%)}.glow{position:absolute;z-index:-1;width:430px;height:430px;right:-150px;top:-220px;border-radius:50%;background:radial-gradient(circle,#2dd4bf42,transparent 67%)}.kicker{display:flex;align-items:center;gap:9px;margin-bottom:15px;color:#a5b4fc;font-size:10px;font-weight:800;letter-spacing:.2em}.kicker i{width:7px;height:7px;border-radius:50%;background:#2dd4bf;box-shadow:0 0 0 5px #2dd4bf20,0 0 18px #2dd4bf}.hero h1{margin:0;font-size:clamp(34px,3.4vw,50px);letter-spacing:-.055em}.hero-copy>p{max-width:670px;margin:16px 0 0;color:#cbd5e1c9;font-size:14px;line-height:1.8}.hero-actions{display:flex;gap:10px;margin-top:27px}.hero-actions button{height:43px;padding:0 17px;display:flex;align-items:center;gap:9px;border-radius:10px;cursor:pointer;font-size:12px;font-weight:700}.hero-actions .primary{border:1px solid #fff;background:#fff;color:#111827;box-shadow:0 12px 28px #0003}.hero-actions .ghost{border:1px solid #ffffff35;background:#ffffff12;color:#fff}.spin{display:inline-block;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.health-card{padding:21px;border:1px solid #ffffff24;border-radius:18px;background:linear-gradient(145deg,#ffffff1c,#ffffff09);backdrop-filter:blur(16px)}.health-card header,.health-card footer{display:flex;justify-content:space-between;align-items:flex-start}.health-card header small{display:block;color:#94a3b8;font-size:8px;letter-spacing:.18em}.health-card header strong{display:block;margin-top:5px;font-size:14px}.health-card header em{display:flex;align-items:center;gap:5px;padding:5px 8px;border:1px solid #2dd4bf45;border-radius:999px;color:#5eead4;background:#0d948822;font-size:8px;font-style:normal;font-weight:800}.health-card header em i{width:5px;height:5px;border-radius:50%;background:#2dd4bf}.health-main{display:flex;align-items:center;gap:20px;margin:14px 0}.ring{--score:0deg;width:100px;height:100px;flex:0 0 auto;padding:8px;border-radius:50%;background:conic-gradient(#5eead4 var(--score),#ffffff17 0)}.ring>div{height:100%;display:flex;align-items:center;justify-content:center;border-radius:50%;background:#111b31}.ring b{font-size:30px;letter-spacing:-.06em}.ring span{margin-top:17px;color:#94a3b8;font-size:8px}.health-main>div:last-child small{color:#94a3b8;font-size:9px}.health-main>div:last-child strong{display:block;margin:3px 0 6px;color:#5eead4;font-size:20px}.health-main p{margin:0;color:#cbd5e1;font-size:10px;line-height:1.5}.health-card footer{padding-top:12px;border-top:1px solid #ffffff17;color:#94a3b8;font-size:9px}
.metrics{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin:16px 0 20px}.metrics article{--tone:#4f46e5;--soft:#eef2ff;position:relative;min-height:135px;padding:18px;overflow:hidden;border:1px solid #e5e9f0;border-radius:16px;background:#fffffff2;box-shadow:0 7px 24px #0f172a0b;transition:.2s}.metrics article:after{content:"";position:absolute;right:-30px;bottom:-48px;width:115px;height:115px;border-radius:50%;background:var(--soft)}.metrics article:hover{transform:translateY(-3px);border-color:var(--tone);box-shadow:0 14px 32px #0f172a14}.m-teal{--tone:#0d9488!important;--soft:#ccfbf1!important}.m-violet{--tone:#7c3aed!important;--soft:#f3e8ff!important}.m-blue{--tone:#2563eb!important;--soft:#dbeafe!important}.m-rose{--tone:#e11d48!important;--soft:#ffe4e6!important}.m-slate{--tone:#64748b!important;--soft:#f1f5f9!important}.metrics span{color:#475569;font-size:11px;font-weight:700}.metrics span small{display:block;margin-top:3px;color:#a1a8b5;font-size:7px;letter-spacing:.14em}.metrics b{position:relative;z-index:1;display:block;margin:15px 0 10px;font-size:29px;letter-spacing:-.05em}.metrics b i{font-size:13px;font-style:normal;color:#64748b}.metrics .money{font-size:clamp(19px,1.7vw,27px)}.metrics p{position:relative;z-index:1;margin:0;color:#7f8998;font-size:9px}
.surface{padding:24px;border:1px solid #e3e8ef;border-radius:20px;background:#fffffff4;box-shadow:0 10px 32px #0f172a0b}.capabilities{margin-bottom:16px}.section-head{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.section-head>div>small{display:block;margin-bottom:6px;color:#6366f1;font-size:8px;font-weight:900;letter-spacing:.18em}.section-head h2{margin:0;font-size:19px;letter-spacing:-.025em}.section-head p{margin:5px 0 0;color:#7b8494;font-size:10px;line-height:1.6}.asset-total{padding:8px 12px;display:flex;align-items:center;gap:9px;border:1px solid #e5e9ef;border-radius:11px;background:#fafbfc}.asset-total b{color:#4338ca;font-size:20px}.asset-total span{color:#7b8494;font-size:9px}.action-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:19px}.action-grid button{--tone:#4f46e5;--soft:#eef2ff;position:relative;min-height:155px;padding:16px;overflow:hidden;border:1px solid #e7ebf1;border-radius:14px;text-align:left;background:linear-gradient(145deg,#fff,#fbfcfe);cursor:pointer;transition:.22s}.action-grid button:hover{transform:translateY(-4px);border-color:var(--tone);box-shadow:0 14px 30px #0f172a17}.action-grid button>small{color:#9aa3b2;font-size:7px;font-weight:900;letter-spacing:.15em}.action-grid button>i{position:absolute;right:16px;top:16px;width:9px;height:9px;border-radius:3px;background:var(--tone);box-shadow:0 0 0 7px var(--soft);transform:rotate(45deg)}.action-grid h3{margin:22px 0 6px;font-size:13px}.action-grid p{margin:0;color:#778193;font-size:9px;line-height:1.65}.action-grid button>span{position:absolute;left:16px;bottom:14px;color:var(--tone);font-size:9px;font-weight:700}.tone-violet{--tone:#7c3aed!important;--soft:#f3e8ff!important}.tone-cyan{--tone:#0891b2!important;--soft:#cffafe!important}.tone-amber{--tone:#d97706!important;--soft:#fef3c7!important}.tone-rose{--tone:#e11d48!important;--soft:#ffe4e6!important}.tone-blue{--tone:#2563eb!important;--soft:#dbeafe!important}.tone-green{--tone:#059669!important;--soft:#d1fae5!important}.tone-slate{--tone:#475569!important;--soft:#e2e8f0!important}
.layout{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(290px,.68fr);gap:16px;margin-bottom:16px}.layout.lower{grid-template-columns:minmax(0,1.45fr) minmax(300px,.55fr)}.link-btn{padding:8px 11px;border:1px solid #e1e6ed;border-radius:9px;color:#4f46e5;background:#fff;cursor:pointer;font-size:9px;font-weight:700}.pipeline{display:grid;grid-template-columns:repeat(5,1fr);gap:11px;margin:18px 0 13px;padding:13px;border:1px solid #edf0f5;border-radius:13px;background:#fafbfc}.pipeline>div>span{display:flex;justify-content:space-between;color:#6f798a;font-size:8px}.pipeline span b{color:#172033;font-size:14px}.pipeline>div>i{display:block;height:4px;margin-top:7px;overflow:hidden;border-radius:99px;background:#e9edf3}.pipeline em{display:block;height:100%;border-radius:99px}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse}th,td{padding:11px;border-bottom:1px solid #edf0f4;text-align:left;white-space:nowrap;font-size:10px}th{color:#929aaa;font-size:8px;letter-spacing:.05em}td{color:#596477}.type,.status{display:inline-flex;align-items:center;padding:4px 7px;border-radius:999px;color:#7c3aed;background:#f3e8ff;font-size:8px}.type.bulk{color:#2563eb;background:#dbeafe}.status{gap:5px;color:#64748b;background:#f1f5f9;font-style:normal}.status i{width:4px;height:4px;border-radius:50%;background:currentColor}.status.pending_confirm{color:#b45309;background:#fef3c7}.status.confirmed{color:#7c3aed;background:#f3e8ff}.status.producing{color:#2563eb;background:#dbeafe}.status.ready_to_ship{color:#0f766e;background:#ccfbf1}.status.shipped,.status.completed{color:#047857;background:#d1fae5}.empty{padding:30px;text-align:center}.empty p{color:#8a94a4;font-size:9px}.empty button{padding:7px 10px;border:0;border-radius:8px;color:#fff;background:#4f46e5;font-size:9px;cursor:pointer}.todo{min-width:34px;height:34px;display:grid;place-items:center;border-radius:11px;color:#e11d48;background:#fff1f2}.priorities>.section-head{margin-bottom:7px}.priorities>button{width:100%;padding:12px 0;display:grid;grid-template-columns:36px 1fr auto;align-items:center;gap:10px;border:0;border-bottom:1px solid #edf0f4;text-align:left;background:transparent;cursor:pointer}.priorities>button>i{width:34px;height:34px;display:grid;place-items:center;border-radius:10px;font-style:normal}.amber{color:#b45309;background:#fef3c7}.violet{color:#7c3aed;background:#f3e8ff}.rose{color:#e11d48;background:#ffe4e6}.blue{color:#2563eb;background:#dbeafe}.priorities button span b,.priorities button span small{display:block}.priorities button span b{font-size:10px}.priorities button span small{margin-top:3px;color:#929baa;font-size:8px}.priorities button>strong{font-size:15px}.insight{display:flex;gap:9px;margin-top:15px;padding:12px;border:1px solid #fed7aa;border-radius:11px;background:#fffaf2}.insight.safe{border-color:#a7f3d0;background:#f0fdf4}.insight>i{width:7px;height:7px;flex:0 0 auto;margin-top:3px;border-radius:50%;background:#f97316;box-shadow:0 0 0 4px #ffedd5}.insight.safe>i{background:#10b981;box-shadow:0 0 0 4px #d1fae5}.insight b,.insight small{display:block}.insight b{font-size:9px}.insight small{margin-top:4px;color:#8d6b55;font-size:8px;line-height:1.5}
.asset-stats{display:grid;grid-template-columns:repeat(5,1fr);gap:8px;margin-top:18px}.asset-stats>div,.op-grid>div{padding:12px;border:1px solid #edf0f5;border-radius:11px;background:#fafbfc}.asset-stats span,.op-grid span{color:#657084;font-size:8px}.asset-stats b,.op-grid b{display:block;margin:6px 0 3px;font-size:21px}.asset-stats small,.op-grid small{color:#a1a9b6;font-size:6px;letter-spacing:.1em}.gallery{display:grid;grid-template-columns:repeat(6,1fr);gap:8px;margin-top:12px}.gallery button{position:relative;height:105px;padding:0;overflow:hidden;border:1px solid #e7eaf0;border-radius:10px;background:#eef1f5;cursor:pointer}.gallery img{width:100%;height:100%;object-fit:cover;transition:.3s}.gallery button:hover img{transform:scale(1.06)}.gallery span{position:absolute;inset:auto 0 0;padding:18px 7px 7px;overflow:hidden;color:#fff;background:linear-gradient(transparent,#030712d9);font-size:8px;text-overflow:ellipsis;white-space:nowrap}.asset-empty{margin-top:12px;padding:22px;text-align:center;border:1px dashed #d9deea;border-radius:11px;color:#8b95a6;background:#fafbfc;font-size:9px}.asset-empty button{margin-left:8px;padding:6px 9px;border:0;border-radius:7px;color:#fff;background:#4f46e5;cursor:pointer;font-size:8px}.op-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:17px}.wide-btn{width:100%;margin-top:11px;padding:10px 12px;display:flex;justify-content:space-between;border:1px solid #dfe4eb;border-radius:9px;color:#344054;background:#fff;cursor:pointer;font-size:9px;font-weight:700}.wide-btn:hover{color:#4f46e5;border-color:#a5b4fc}
@media(max-width:1360px){.metrics{grid-template-columns:repeat(3,1fr)}.gallery{grid-template-columns:repeat(3,1fr)}}@media(max-width:1120px){.hero{grid-template-columns:1fr}.layout,.layout.lower{grid-template-columns:1fr}.action-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:760px){.hero{padding:27px 21px;border-radius:18px}.hero-actions{flex-direction:column}.metrics{grid-template-columns:1fr 1fr}.surface{padding:18px}.pipeline{grid-template-columns:1fr}.asset-stats{grid-template-columns:repeat(2,1fr)}.gallery{grid-template-columns:repeat(2,1fr)}}@media(max-width:520px){.metrics,.action-grid,.op-grid{grid-template-columns:1fr}.section-head{flex-direction:column}.health-main{align-items:flex-start}}
</style>
