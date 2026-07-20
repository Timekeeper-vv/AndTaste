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
const orderProgress = computed(() => orders.value.length ? Math.round((count('confirmed') + count('producing') + count('ready_to_ship') + done.value) / orders.value.length * 100) : 0)
const riskControl = computed(() => Math.max(0, Math.min(100, 100 - risks.value * 12)))
const approvalResponse = computed(() => Math.max(0, Math.min(100, 100 - Number(workflow.value.pendingCount || 0) * 8)))
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
const gaugeCards = computed(() => [
  { label:'履约完成率', value:fulfillment.value, tone:'#0f766e', desc:`${done.value} 单已完成 / 已发货`, foot:`${logistics.value.inTransitCount || 0} 单在途` },
  { label:'订单推进率', value:orderProgress.value, tone:'#2563eb', desc:`${count('pending_confirm')} 单待客户确认`, foot:`${count('producing')} 单生产中` },
  { label:'风险控制指数', value:riskControl.value, tone:risks.value ? '#e11d48' : '#10b981', desc:`${risks.value} 项供应链风险`, foot:`库存 ${warehouse.value.alertCount || 0} · 物流 ${logistics.value.exceptionCount || 0}` },
  { label:'审批响应指数', value:approvalResponse.value, tone:'#d97706', desc:`${workflow.value.pendingCount || 0} 条审批待办`, foot:'连锁 / 财务 / 业务流转' }
])
const todoItems = computed(() => [
  { title:'审批待处理', value:Number(workflow.value.pendingCount || 0), desc:'财务、连锁与业务申请', page:'approvalCenter', tone:'amber' },
  { title:'客户待确认', value:count('pending_confirm'), desc:'报价与生产内容确认', page:'sampleProduction', tone:'blue' },
  { title:'待下达生产', value:count('confirmed'), desc:'已确认订单等待执行', page:'bulkProduction', tone:'violet' },
  { title:'风险待跟进', value:risks.value, desc:'库存预警与物流异常', page:'warehouseLogistics', tone:risks.value ? 'rose' : 'green' }
])
const alertItems = computed(() => [
  { label:'库存预警', value:Number(warehouse.value.alertCount || 0), page:'warehouseLogistics', desc:'缺货、低库存、超储风险' },
  { label:'物流异常', value:Number(logistics.value.exceptionCount || 0), page:'logistics', desc:'运输停滞、签收异常' },
  { label:'审批积压', value:Number(workflow.value.pendingCount || 0), page:'approvalCenter', desc:'超过正常响应时间需关注' },
].filter(i => i.value > 0))
const recentActivities = computed(() => recentOrders.value.slice(0, 5).map(o => ({
  id: o.id,
  title: o.productName || '未命名产品',
  meta: `${o.orderNo || '—'} · ${o.orderType === 'sample' ? '产品打样' : '大货生产'}`,
  status: statusText[o.status] || o.status || '待处理',
  amount: money(o.totalAmount)
})))
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
      <div class="hero-meta">
        <span><b>AI</b> 创意引擎在线</span>
        <span><b>{{orders.length}}</b> 商业订单接入</span>
        <span><b>{{assets.length}}</b> 数字资产沉淀</span>
      </div>
      <div class="hero-actions">
        <button class="primary" @click="go('creative2d')">启动创意项目 <span>→</span></button>
        <button class="ghost" :disabled="loading" @click="load"><span :class="{spin:loading}">↻</span>{{loading?'数据同步中':'刷新经营数据'}}</button>
      </div>
      <div class="release-flow" aria-label="发布会式业务流程">
        <div><i>01</i><span>创意生成</span></div>
        <em></em>
        <div><i>02</i><span>智能评审</span></div>
        <em></em>
        <div><i>03</i><span>生产核算</span></div>
        <em></em>
        <div><i>04</i><span>交付履约</span></div>
      </div>
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
    <div class="section-head"><div><small>CORE CAPABILITIES</small><h2>核心业务能力</h2><p>围绕文创产品商业化全流程，快速进入关键工作台。</p></div></div>
    <div class="action-grid">
      <button v-for="a in actions" :key="a.page" :class="`tone-${a.tone}`" @click="go(a.page)">
        <small>{{a.code}}</small><i></i><h3>{{a.title}}</h3><p>{{a.desc}}</p><span>进入工作台 →</span>
      </button>
    </div>
  </section>

  <section class="surface gauge-section">
    <div class="section-head">
      <div><small>EXECUTIVE DASHBOARD</small><h2>实时运营仪表盘</h2><p>关键指标自动汇总，辅助判断订单执行、审批响应和供应链风险。</p></div>
      <span class="sync-time">同步于 {{ updatedAt }}</span>
    </div>
    <div class="gauge-grid">
      <article v-for="g in gaugeCards" :key="g.label" class="gauge-card" :style="{ '--tone': g.tone, '--gauge': `${g.value * 3.6}deg` }">
        <div class="gauge-ring"><div><b>{{ g.value }}</b><i>%</i></div></div>
        <div class="gauge-info">
          <span>{{ g.label }}</span>
          <strong>{{ g.desc }}</strong>
          <small>{{ g.foot }}</small>
        </div>
      </article>
    </div>
  </section>

  <section class="ops-overview">
    <article class="overview-card todo-card">
      <div class="overview-head"><div><small>TODAY TODO</small><h2>今日经营待办</h2></div><button @click="load" :disabled="loading">{{ loading ? '同步中' : '刷新' }}</button></div>
      <div class="todo-grid">
        <button v-for="t in todoItems" :key="t.title" :class="`todo-${t.tone}`" @click="go(t.page)">
          <span>{{ t.title }}</span>
          <b>{{ t.value }}</b>
          <small>{{ t.desc }}</small>
        </button>
      </div>
    </article>
    <article class="overview-card alert-card">
      <div class="overview-head"><div><small>RISK WATCH</small><h2>异常提醒</h2></div></div>
      <div v-if="alertItems.length" class="alert-list">
        <button v-for="a in alertItems" :key="a.label" @click="go(a.page)">
          <i></i><span><b>{{ a.label }} · {{ a.value }}</b><small>{{ a.desc }}</small></span><em>处理 →</em>
        </button>
      </div>
      <div v-else class="safe-panel"><b>当前暂无异常</b><span>供应链、审批和履约状态稳定。</span></div>
    </article>
    <article class="overview-card activity-card">
      <div class="overview-head"><div><small>RECENT ACTIVITY</small><h2>最近订单动态</h2></div><button @click="go('sampleProduction')">全部</button></div>
      <div v-if="recentActivities.length" class="activity-list">
        <button v-for="a in recentActivities" :key="a.id" @click="go('sampleProduction')">
          <span><b>{{ a.title }}</b><small>{{ a.meta }}</small></span>
          <em>{{ a.status }}</em>
        </button>
      </div>
      <div v-else class="safe-panel"><b>暂无订单动态</b><span>创建第一张生产订单后将显示在这里。</span></div>
    </article>
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
</div>
</template>

<style scoped>
.dashboard-page{--ink:#0b1220;--muted:#718096;min-height:100vh;padding:0;color:var(--ink);background:radial-gradient(circle at 0 0,#eef2ff 0,transparent 25%),linear-gradient(180deg,#f8fafc,#f3f6fa)}button{font:inherit}.hero{position:relative;isolation:isolate;min-height:285px;padding:38px 42px;display:grid;grid-template-columns:1.4fr minmax(330px,.6fr);gap:50px;align-items:center;overflow:hidden;border-radius:24px;color:#fff;background:linear-gradient(120deg,#07101f,#131d39 48%,#172554 76%,#0f766e 140%);box-shadow:0 24px 60px #0f172a2c}.grid-pattern{position:absolute;inset:0;z-index:-2;opacity:.13;background-image:linear-gradient(#ffffff40 1px,transparent 1px),linear-gradient(90deg,#ffffff40 1px,transparent 1px);background-size:48px 48px;mask-image:linear-gradient(90deg,#000,transparent 72%)}.glow{position:absolute;z-index:-1;width:430px;height:430px;right:-150px;top:-220px;border-radius:50%;background:radial-gradient(circle,#2dd4bf42,transparent 67%)}.kicker{display:flex;align-items:center;gap:9px;margin-bottom:15px;color:#a5b4fc;font-size:10px;font-weight:800;letter-spacing:.2em}.kicker i{width:7px;height:7px;border-radius:50%;background:#2dd4bf;box-shadow:0 0 0 5px #2dd4bf20,0 0 18px #2dd4bf}.hero h1{margin:0;font-size:clamp(34px,3.4vw,50px);letter-spacing:-.055em}.hero-copy>p{max-width:670px;margin:16px 0 0;color:#cbd5e1c9;font-size:14px;line-height:1.8}.hero-actions{display:flex;gap:10px;margin-top:27px}.hero-actions button{height:43px;padding:0 17px;display:flex;align-items:center;gap:9px;border-radius:10px;cursor:pointer;font-size:12px;font-weight:700}.hero-actions .primary{border:1px solid #fff;background:#fff;color:#111827;box-shadow:0 12px 28px #0003}.hero-actions .ghost{border:1px solid #ffffff35;background:#ffffff12;color:#fff}.spin{display:inline-block;animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}.health-card{padding:21px;border:1px solid #ffffff24;border-radius:18px;background:linear-gradient(145deg,#ffffff1c,#ffffff09);backdrop-filter:blur(16px)}.health-card header,.health-card footer{display:flex;justify-content:space-between;align-items:flex-start}.health-card header small{display:block;color:#94a3b8;font-size:8px;letter-spacing:.18em}.health-card header strong{display:block;margin-top:5px;font-size:14px}.health-card header em{display:flex;align-items:center;gap:5px;padding:5px 8px;border:1px solid #2dd4bf45;border-radius:999px;color:#5eead4;background:#0d948822;font-size:8px;font-style:normal;font-weight:800}.health-card header em i{width:5px;height:5px;border-radius:50%;background:#2dd4bf}.health-main{display:flex;align-items:center;gap:20px;margin:14px 0}.ring{--score:0deg;width:100px;height:100px;flex:0 0 auto;padding:8px;border-radius:50%;background:conic-gradient(#5eead4 var(--score),#ffffff17 0)}.ring>div{height:100%;display:flex;align-items:center;justify-content:center;border-radius:50%;background:#111b31}.ring b{font-size:30px;letter-spacing:-.06em}.ring span{margin-top:17px;color:#94a3b8;font-size:8px}.health-main>div:last-child small{color:#94a3b8;font-size:9px}.health-main>div:last-child strong{display:block;margin:3px 0 6px;color:#5eead4;font-size:20px}.health-main p{margin:0;color:#cbd5e1;font-size:10px;line-height:1.5}.health-card footer{padding-top:12px;border-top:1px solid #ffffff17;color:#94a3b8;font-size:9px}
.metrics{display:grid;grid-template-columns:repeat(5,1fr);gap:12px;margin:16px 0 20px}.metrics article{--tone:#4f46e5;--soft:#eef2ff;position:relative;min-height:135px;padding:18px;overflow:hidden;border:1px solid #e5e9f0;border-radius:16px;background:#fffffff2;box-shadow:0 7px 24px #0f172a0b;transition:.2s}.metrics article:after{content:"";position:absolute;right:-30px;bottom:-48px;width:115px;height:115px;border-radius:50%;background:var(--soft)}.metrics article:hover{transform:translateY(-3px);border-color:var(--tone);box-shadow:0 14px 32px #0f172a14}.m-teal{--tone:#0d9488!important;--soft:#ccfbf1!important}.m-violet{--tone:#7c3aed!important;--soft:#f3e8ff!important}.m-blue{--tone:#2563eb!important;--soft:#dbeafe!important}.m-rose{--tone:#e11d48!important;--soft:#ffe4e6!important}.m-slate{--tone:#64748b!important;--soft:#f1f5f9!important}.metrics span{color:#475569;font-size:11px;font-weight:700}.metrics span small{display:block;margin-top:3px;color:#a1a8b5;font-size:7px;letter-spacing:.14em}.metrics b{position:relative;z-index:1;display:block;margin:15px 0 10px;font-size:29px;letter-spacing:-.05em}.metrics b i{font-size:13px;font-style:normal;color:#64748b}.metrics .money{font-size:clamp(19px,1.7vw,27px)}.metrics p{position:relative;z-index:1;margin:0;color:#7f8998;font-size:9px}
.surface{padding:24px;border:1px solid #e3e8ef;border-radius:20px;background:#fffffff4;box-shadow:0 10px 32px #0f172a0b}.capabilities{margin-bottom:16px}.section-head{display:flex;justify-content:space-between;align-items:flex-start;gap:20px}.section-head>div>small{display:block;margin-bottom:6px;color:#6366f1;font-size:8px;font-weight:900;letter-spacing:.18em}.section-head h2{margin:0;font-size:19px;letter-spacing:-.025em}.section-head p{margin:5px 0 0;color:#7b8494;font-size:10px;line-height:1.6}.asset-total{padding:8px 12px;display:flex;align-items:center;gap:9px;border:1px solid #e5e9ef;border-radius:11px;background:#fafbfc}.asset-total b{color:#4338ca;font-size:20px}.asset-total span{color:#7b8494;font-size:9px}.action-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px;margin-top:19px}.action-grid button{--tone:#4f46e5;--soft:#eef2ff;position:relative;min-height:155px;padding:16px;overflow:hidden;border:1px solid #e7ebf1;border-radius:14px;text-align:left;background:linear-gradient(145deg,#fff,#fbfcfe);cursor:pointer;transition:.22s}.action-grid button:hover{transform:translateY(-4px);border-color:var(--tone);box-shadow:0 14px 30px #0f172a17}.action-grid button>small{color:#9aa3b2;font-size:7px;font-weight:900;letter-spacing:.15em}.action-grid button>i{position:absolute;right:16px;top:16px;width:9px;height:9px;border-radius:3px;background:var(--tone);box-shadow:0 0 0 7px var(--soft);transform:rotate(45deg)}.action-grid h3{margin:22px 0 6px;font-size:13px}.action-grid p{margin:0;color:#778193;font-size:9px;line-height:1.65}.action-grid button>span{position:absolute;left:16px;bottom:14px;color:var(--tone);font-size:9px;font-weight:700}.tone-violet{--tone:#7c3aed!important;--soft:#f3e8ff!important}.tone-cyan{--tone:#0891b2!important;--soft:#cffafe!important}.tone-amber{--tone:#d97706!important;--soft:#fef3c7!important}.tone-rose{--tone:#e11d48!important;--soft:#ffe4e6!important}.tone-blue{--tone:#2563eb!important;--soft:#dbeafe!important}.tone-green{--tone:#059669!important;--soft:#d1fae5!important}.tone-slate{--tone:#475569!important;--soft:#e2e8f0!important}
.layout{display:grid;grid-template-columns:minmax(0,1.65fr) minmax(290px,.68fr);gap:16px;margin-bottom:16px}.layout.lower{grid-template-columns:minmax(0,1.45fr) minmax(300px,.55fr)}.link-btn{padding:8px 11px;border:1px solid #e1e6ed;border-radius:9px;color:#4f46e5;background:#fff;cursor:pointer;font-size:9px;font-weight:700}.pipeline{display:grid;grid-template-columns:repeat(5,1fr);gap:11px;margin:18px 0 13px;padding:13px;border:1px solid #edf0f5;border-radius:13px;background:#fafbfc}.pipeline>div>span{display:flex;justify-content:space-between;color:#6f798a;font-size:8px}.pipeline span b{color:#172033;font-size:14px}.pipeline>div>i{display:block;height:4px;margin-top:7px;overflow:hidden;border-radius:99px;background:#e9edf3}.pipeline em{display:block;height:100%;border-radius:99px}.table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse}th,td{padding:11px;border-bottom:1px solid #edf0f4;text-align:left;white-space:nowrap;font-size:10px}th{color:#929aaa;font-size:8px;letter-spacing:.05em}td{color:#596477}.type,.status{display:inline-flex;align-items:center;padding:4px 7px;border-radius:999px;color:#7c3aed;background:#f3e8ff;font-size:8px}.type.bulk{color:#2563eb;background:#dbeafe}.status{gap:5px;color:#64748b;background:#f1f5f9;font-style:normal}.status i{width:4px;height:4px;border-radius:50%;background:currentColor}.status.pending_confirm{color:#b45309;background:#fef3c7}.status.confirmed{color:#7c3aed;background:#f3e8ff}.status.producing{color:#2563eb;background:#dbeafe}.status.ready_to_ship{color:#0f766e;background:#ccfbf1}.status.shipped,.status.completed{color:#047857;background:#d1fae5}.empty{padding:30px;text-align:center}.empty p{color:#8a94a4;font-size:9px}.empty button{padding:7px 10px;border:0;border-radius:8px;color:#fff;background:#4f46e5;font-size:9px;cursor:pointer}.todo{min-width:34px;height:34px;display:grid;place-items:center;border-radius:11px;color:#e11d48;background:#fff1f2}.priorities>.section-head{margin-bottom:7px}.priorities>button{width:100%;padding:12px 0;display:grid;grid-template-columns:36px 1fr auto;align-items:center;gap:10px;border:0;border-bottom:1px solid #edf0f4;text-align:left;background:transparent;cursor:pointer}.priorities>button>i{width:34px;height:34px;display:grid;place-items:center;border-radius:10px;font-style:normal}.amber{color:#b45309;background:#fef3c7}.violet{color:#7c3aed;background:#f3e8ff}.rose{color:#e11d48;background:#ffe4e6}.blue{color:#2563eb;background:#dbeafe}.priorities button span b,.priorities button span small{display:block}.priorities button span b{font-size:10px}.priorities button span small{margin-top:3px;color:#929baa;font-size:8px}.priorities button>strong{font-size:15px}.insight{display:flex;gap:9px;margin-top:15px;padding:12px;border:1px solid #fed7aa;border-radius:11px;background:#fffaf2}.insight.safe{border-color:#a7f3d0;background:#f0fdf4}.insight>i{width:7px;height:7px;flex:0 0 auto;margin-top:3px;border-radius:50%;background:#f97316;box-shadow:0 0 0 4px #ffedd5}.insight.safe>i{background:#10b981;box-shadow:0 0 0 4px #d1fae5}.insight b,.insight small{display:block}.insight b{font-size:9px}.insight small{margin-top:4px;color:#8d6b55;font-size:8px;line-height:1.5}
.asset-stats{display:grid;grid-template-columns:repeat(5,1fr);gap:8px;margin-top:18px}.asset-stats>div,.op-grid>div{padding:12px;border:1px solid #edf0f5;border-radius:11px;background:#fafbfc}.asset-stats span,.op-grid span{color:#657084;font-size:8px}.asset-stats b,.op-grid b{display:block;margin:6px 0 3px;font-size:21px}.asset-stats small,.op-grid small{color:#a1a9b6;font-size:6px;letter-spacing:.1em}.gallery{display:grid;grid-template-columns:repeat(6,1fr);gap:8px;margin-top:12px}.gallery button{position:relative;height:105px;padding:0;overflow:hidden;border:1px solid #e7eaf0;border-radius:10px;background:#eef1f5;cursor:pointer}.gallery img{width:100%;height:100%;object-fit:cover;transition:.3s}.gallery button:hover img{transform:scale(1.06)}.gallery span{position:absolute;inset:auto 0 0;padding:18px 7px 7px;overflow:hidden;color:#fff;background:linear-gradient(transparent,#030712d9);font-size:8px;text-overflow:ellipsis;white-space:nowrap}.asset-empty{margin-top:12px;padding:22px;text-align:center;border:1px dashed #d9deea;border-radius:11px;color:#8b95a6;background:#fafbfc;font-size:9px}.asset-empty button{margin-left:8px;padding:6px 9px;border:0;border-radius:7px;color:#fff;background:#4f46e5;cursor:pointer;font-size:8px}.op-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:17px}.wide-btn{width:100%;margin-top:11px;padding:10px 12px;display:flex;justify-content:space-between;border:1px solid #dfe4eb;border-radius:9px;color:#344054;background:#fff;cursor:pointer;font-size:9px;font-weight:700}.wide-btn:hover{color:#4f46e5;border-color:#a5b4fc}
@media(max-width:1360px){.metrics{grid-template-columns:repeat(3,1fr)}.gallery{grid-template-columns:repeat(3,1fr)}}@media(max-width:1120px){.hero{grid-template-columns:1fr}.layout,.layout.lower{grid-template-columns:1fr}.action-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:760px){.hero{padding:27px 21px;border-radius:18px}.hero-actions{flex-direction:column}.metrics{grid-template-columns:1fr 1fr}.surface{padding:18px}.pipeline{grid-template-columns:1fr}.asset-stats{grid-template-columns:repeat(2,1fr)}.gallery{grid-template-columns:repeat(2,1fr)}}@media(max-width:520px){.metrics,.action-grid,.op-grid{grid-template-columns:1fr}.section-head{flex-direction:column}.health-main{align-items:flex-start}}

/* Cinematic dashboard skin */
.dashboard-page {
  position: relative;
  padding: 0;
  color: #0b1220;
  background:
    radial-gradient(circle at 4% 2%, rgba(20,184,166,.20), transparent 28%),
    radial-gradient(circle at 98% 8%, rgba(124,58,237,.16), transparent 32%),
    linear-gradient(180deg, rgba(248,251,255,.92), rgba(238,244,255,.86));
}
.dashboard-page::before {
  content: "";
  position: fixed;
  inset: 56px 0 0 0;
  pointer-events: none;
  background:
    linear-gradient(rgba(15,23,42,.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15,23,42,.035) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(circle at 50% 20%, #000, transparent 70%);
}
.hero {
  min-height: 360px;
  border-radius: 32px;
  padding: 48px;
  background:
    linear-gradient(120deg, rgba(2,6,23,.98), rgba(15,23,42,.95) 43%, rgba(30,41,59,.92)),
    radial-gradient(circle at 86% 18%, rgba(20,184,166,.46), transparent 34%),
    radial-gradient(circle at 50% 110%, rgba(124,58,237,.40), transparent 42%);
  box-shadow: 0 34px 90px rgba(2,6,23,.28), inset 0 1px 0 rgba(255,255,255,.12);
}
.hero::before {
  content: "";
  position: absolute;
  inset: 1px;
  border-radius: 31px;
  pointer-events: none;
  background:
    linear-gradient(115deg, transparent 0 46%, rgba(255,255,255,.10) 47%, transparent 48% 70%, rgba(94,234,212,.12) 71%, transparent 72%),
    radial-gradient(circle at 18% 20%, rgba(255,255,255,.10), transparent 26%);
}
.hero::after {
  content: "";
  position: absolute;
  right: 28%;
  bottom: -90px;
  width: 440px;
  height: 170px;
  border: 1px solid rgba(148,163,184,.20);
  border-radius: 999px;
  transform: rotate(-12deg);
  box-shadow: 0 0 70px rgba(56,189,248,.13), inset 0 0 40px rgba(20,184,166,.10);
}
.grid-pattern { opacity: .22; background-size: 40px 40px; }
.glow {
  width: 560px;
  height: 560px;
  right: -190px;
  top: -250px;
  background: radial-gradient(circle, rgba(45,212,191,.42), rgba(124,58,237,.16) 36%, transparent 70%);
  filter: blur(2px);
}
.kicker {
  color: #a5f3fc;
  letter-spacing: .24em;
}
.hero h1 {
  font-size: clamp(44px, 5vw, 76px);
  font-weight: 950;
  letter-spacing: -.075em;
  line-height: .98;
  text-shadow: 0 18px 50px rgba(0,0,0,.35);
}
.hero-copy>p {
  max-width: 720px;
  color: rgba(226,232,240,.78);
  font-size: 15px;
}
.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}
.hero-meta span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(255,255,255,.16);
  border-radius: 999px;
  color: rgba(226,232,240,.78);
  background: rgba(255,255,255,.075);
  backdrop-filter: blur(16px);
  font-size: 11px;
  font-weight: 700;
}
.hero-meta b { color: #5eead4; }
.hero-actions .primary {
  border: 0;
  background: linear-gradient(135deg, #ffffff, #dffcff);
  color: #07101f;
  box-shadow: 0 18px 42px rgba(94,234,212,.20);
}
.hero-actions .ghost {
  border-color: rgba(255,255,255,.18);
  background: rgba(255,255,255,.08);
  backdrop-filter: blur(14px);
}
.health-card {
  border-radius: 24px;
  background: linear-gradient(145deg, rgba(255,255,255,.18), rgba(255,255,255,.055));
  border: 1px solid rgba(255,255,255,.18);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.14), 0 24px 70px rgba(0,0,0,.18);
}
.ring {
  width: 122px;
  height: 122px;
  background: conic-gradient(#5eead4 var(--score), rgba(255,255,255,.12) 0);
  box-shadow: 0 0 42px rgba(45,212,191,.22);
}
.ring>div {
  background: radial-gradient(circle at 50% 0%, #1e293b, #07101f);
}
.metrics { gap: 16px; margin: 18px 0 22px; }
.metrics article {
  min-height: 146px;
  border-radius: 24px;
  background: linear-gradient(145deg, rgba(255,255,255,.94), rgba(255,255,255,.70));
  border-color: rgba(148,163,184,.18);
  box-shadow: 0 18px 50px rgba(15,23,42,.08);
  backdrop-filter: blur(18px) saturate(145%);
}
.metrics article::before {
  content: "";
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(135deg, color-mix(in srgb, var(--tone) 16%, transparent), transparent 48%);
  opacity: .55;
}
.metrics article:hover {
  transform: translateY(-6px);
  box-shadow: 0 28px 70px rgba(15,23,42,.14);
}
.metrics b { font-size: 34px; font-weight: 900; }
.surface {
  border-radius: 28px;
  background: linear-gradient(145deg, rgba(255,255,255,.92), rgba(255,255,255,.72));
  border: 1px solid rgba(148,163,184,.20);
  box-shadow: 0 22px 58px rgba(15,23,42,.08);
  backdrop-filter: blur(20px) saturate(150%);
}
.section-head h2 { font-size: 22px; font-weight: 900; }
.section-head>div>small { color: #0d9488; }
.action-grid { gap: 14px; }
.action-grid button {
  min-height: 172px;
  border-radius: 22px;
  background:
    linear-gradient(145deg, rgba(255,255,255,.96), rgba(255,255,255,.74)),
    radial-gradient(circle at 90% 6%, var(--soft), transparent 36%);
  box-shadow: 0 14px 34px rgba(15,23,42,.06);
}
.action-grid button:hover {
  transform: translateY(-7px) scale(1.01);
  box-shadow: 0 28px 58px rgba(15,23,42,.13);
}
.pipeline, .asset-stats>div, .op-grid>div {
  border-color: rgba(148,163,184,.18);
  background: rgba(248,250,252,.74);
}
@media(max-width:760px){.hero{padding:30px 22px;border-radius:24px}.hero-meta{display:none}}

/* Brand launch dashboard preview — official presentation style */
.dashboard-page {
  background:
    radial-gradient(circle at 14% -5%, rgba(20,184,166,.14), transparent 26%),
    radial-gradient(circle at 100% 6%, rgba(245,158,11,.14), transparent 30%),
    linear-gradient(180deg, #fbfaf7 0%, #f2f7f5 48%, #eef4ff 100%);
}
.dashboard-page::before {
  opacity: .6;
  background:
    linear-gradient(rgba(15,23,42,.028) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15,23,42,.028) 1px, transparent 1px);
}
.hero {
  min-height: 420px;
  grid-template-columns: minmax(0,1.2fr) minmax(360px,.68fr);
  border-radius: 34px;
  color: #0b1220;
  background:
    linear-gradient(115deg, rgba(255,255,255,.98) 0 52%, rgba(255,255,255,.74) 53% 66%, transparent 67%),
    radial-gradient(circle at 88% 20%, rgba(20,184,166,.22), transparent 30%),
    radial-gradient(circle at 88% 84%, rgba(245,158,11,.18), transparent 34%),
    linear-gradient(135deg, #fffaf0 0%, #edf8f5 48%, #eaf1ff 100%);
  box-shadow: 0 34px 90px rgba(15,23,42,.13);
}
.hero::before {
  background:
    linear-gradient(115deg, transparent 0 50%, rgba(15,23,42,.06) 51%, transparent 52% 72%, rgba(20,184,166,.13) 73%, transparent 74%),
    radial-gradient(circle at 18% 16%, rgba(20,184,166,.10), transparent 28%);
}
.hero::after {
  border-color: rgba(15,23,42,.08);
  box-shadow: 0 0 70px rgba(20,184,166,.10), inset 0 0 40px rgba(255,255,255,.46);
}
.grid-pattern {
  opacity: .16;
  background-image:
    linear-gradient(rgba(15,23,42,.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(15,23,42,.07) 1px, transparent 1px);
  mask-image: linear-gradient(90deg, transparent 0 42%, #000 68%, transparent 100%);
}
.glow {
  background: radial-gradient(circle, rgba(20,184,166,.22), rgba(245,158,11,.12) 42%, transparent 70%);
}
.kicker {
  color: #0f766e;
}
.kicker i {
  background: #0f766e;
  box-shadow: 0 0 0 5px rgba(20,184,166,.12), 0 0 18px rgba(20,184,166,.35);
}
.hero h1 {
  max-width: 760px;
  color: #0b1220;
  text-shadow: none;
  font-size: clamp(48px, 6vw, 86px);
}
.hero-copy>p {
  color: #475569;
  font-size: 16px;
}
.hero-meta span {
  color: #475569;
  border-color: rgba(15,23,42,.08);
  background: rgba(255,255,255,.72);
  box-shadow: 0 12px 32px rgba(15,23,42,.06);
}
.hero-meta b { color: #0f766e; }
.hero-actions .primary {
  color: #fff;
  background: linear-gradient(135deg, #0f766e, #14b8a6);
  box-shadow: 0 18px 38px rgba(20,184,166,.24);
}
.hero-actions .ghost {
  color: #0f172a;
  border-color: rgba(15,23,42,.10);
  background: rgba(255,255,255,.72);
}
.release-flow {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 26px;
  max-width: 760px;
}
.release-flow div {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 13px;
  border: 1px solid rgba(15,23,42,.08);
  border-radius: 999px;
  background: rgba(255,255,255,.78);
  box-shadow: 0 12px 28px rgba(15,23,42,.06);
  animation: releaseStep .55s ease both;
}
.release-flow div:nth-of-type(2) { animation-delay: .08s; }
.release-flow div:nth-of-type(3) { animation-delay: .16s; }
.release-flow div:nth-of-type(4) { animation-delay: .24s; }
.release-flow i {
  color: #0f766e;
  font-style: normal;
  font-size: 10px;
  font-weight: 950;
  letter-spacing: .08em;
}
.release-flow span {
  color: #334155;
  font-size: 12px;
  font-weight: 800;
}
.release-flow em {
  width: 32px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(20,184,166,.10), rgba(20,184,166,.70));
  animation: releaseLine 2.8s ease-in-out infinite;
}
.gauge-section {
  margin-bottom: 16px;
}
.sync-time {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 12px;
  border: 1px solid rgba(15,23,42,.08);
  border-radius: 999px;
  color: #64748b;
  background: rgba(255,255,255,.72);
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}
.gauge-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 20px;
}
.gauge-card {
  --tone: #0f766e;
  --gauge: 0deg;
  position: relative;
  min-height: 170px;
  display: grid;
  grid-template-columns: 96px 1fr;
  gap: 16px;
  align-items: center;
  padding: 18px;
  overflow: hidden;
  border: 1px solid rgba(15,23,42,.08);
  border-radius: 24px;
  background:
    linear-gradient(145deg, rgba(255,255,255,.94), rgba(255,255,255,.74)),
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tone) 14%, transparent), transparent 42%);
  box-shadow: 0 18px 46px rgba(15,23,42,.08);
  animation: launchCardIn .45s ease both;
}
.gauge-card:nth-child(2) { animation-delay: .06s; }
.gauge-card:nth-child(3) { animation-delay: .12s; }
.gauge-card:nth-child(4) { animation-delay: .18s; }
.gauge-card::after {
  content: "";
  position: absolute;
  right: -44px;
  bottom: -54px;
  width: 130px;
  height: 130px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--tone) 10%, transparent);
  pointer-events: none;
}
.gauge-ring {
  width: 96px;
  height: 96px;
  padding: 8px;
  border-radius: 50%;
  background: conic-gradient(var(--tone) var(--gauge), rgba(15,23,42,.08) 0);
  box-shadow: inset 0 0 0 1px rgba(15,23,42,.04), 0 14px 30px color-mix(in srgb, var(--tone) 18%, transparent);
}
.gauge-ring > div {
  height: 100%;
  display: flex;
  align-items: baseline;
  justify-content: center;
  border-radius: 50%;
  background: #fff;
  padding-top: 30px;
}
.gauge-ring b {
  color: #0f172a;
  font-size: 28px;
  font-weight: 950;
  letter-spacing: -.07em;
  line-height: 1;
}
.gauge-ring i {
  margin-left: 3px;
  color: var(--tone);
  font-size: 11px;
  font-style: normal;
  font-weight: 900;
}
.gauge-info {
  position: relative;
  z-index: 1;
  min-width: 0;
}
.gauge-info span {
  color: #64748b;
  font-size: 11px;
  font-weight: 900;
  letter-spacing: .03em;
}
.gauge-info strong {
  display: block;
  margin-top: 10px;
  color: #0f172a;
  font-size: 14px;
  font-weight: 950;
  line-height: 1.45;
}
.gauge-info small {
  display: block;
  margin-top: 8px;
  color: #94a3b8;
  font-size: 10px;
  line-height: 1.5;
}
.ops-overview {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, .8fr) minmax(300px, .9fr);
  gap: 16px;
  margin-bottom: 16px;
}
.overview-card {
  position: relative;
  overflow: hidden;
  padding: 22px;
  border: 1px solid rgba(15,23,42,.08);
  border-radius: 28px;
  background: linear-gradient(145deg, rgba(255,255,255,.96), rgba(255,255,255,.76));
  box-shadow: 0 22px 58px rgba(15,23,42,.08);
  backdrop-filter: blur(18px) saturate(145%);
  animation: launchCardIn .45s ease both;
}
.overview-card::after {
  content: "";
  position: absolute;
  right: -42px;
  top: -52px;
  width: 132px;
  height: 132px;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(20,184,166,.10), transparent 70%);
  pointer-events: none;
}
.overview-head {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 16px;
}
.overview-head small {
  display: block;
  color: #0f766e;
  font-size: 8px;
  font-weight: 950;
  letter-spacing: .18em;
}
.overview-head h2 {
  margin: 6px 0 0;
  color: #0f172a;
  font-size: 18px;
  font-weight: 950;
  letter-spacing: -.03em;
}
.overview-head button {
  height: 30px;
  padding: 0 11px;
  border: 1px solid rgba(15,23,42,.08);
  border-radius: 999px;
  color: #334155;
  background: rgba(255,255,255,.74);
  cursor: pointer;
  font-size: 10px;
  font-weight: 900;
}
.todo-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0,1fr));
  gap: 10px;
}
.todo-grid button {
  --tone:#0f766e;
  min-height: 110px;
  padding: 14px;
  border: 1px solid rgba(15,23,42,.07);
  border-radius: 18px;
  text-align: left;
  background:
    linear-gradient(145deg, rgba(255,255,255,.94), rgba(248,250,252,.72)),
    radial-gradient(circle at 100% 0%, color-mix(in srgb, var(--tone) 13%, transparent), transparent 48%);
  cursor: pointer;
  transition: .2s ease;
}
.todo-grid button:hover {
  transform: translateY(-4px);
  box-shadow: 0 16px 34px rgba(15,23,42,.10);
}
.todo-grid span {
  display: block;
  color: #64748b;
  font-size: 10px;
  font-weight: 900;
}
.todo-grid b {
  display: block;
  margin: 12px 0 7px;
  color: #0f172a;
  font-size: 26px;
  font-weight: 950;
  line-height: 1;
}
.todo-grid small {
  color: #94a3b8;
  font-size: 9px;
  line-height: 1.45;
}
.todo-amber{--tone:#d97706!important}.todo-blue{--tone:#2563eb!important}.todo-violet{--tone:#7c3aed!important}.todo-rose{--tone:#e11d48!important}.todo-green{--tone:#10b981!important}
.alert-list,
.activity-list {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 9px;
}
.alert-list button,
.activity-list button {
  width: 100%;
  min-height: 56px;
  display: grid;
  align-items: center;
  gap: 10px;
  border: 1px solid rgba(15,23,42,.07);
  border-radius: 16px;
  background: rgba(248,250,252,.72);
  cursor: pointer;
}
.alert-list button {
  grid-template-columns: 10px 1fr auto;
  padding: 10px 12px;
  text-align: left;
}
.alert-list i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e11d48;
  box-shadow: 0 0 0 5px rgba(225,29,72,.10);
}
.alert-list b,
.activity-list b {
  display: block;
  color: #0f172a;
  font-size: 12px;
  font-weight: 950;
}
.alert-list small,
.activity-list small {
  display: block;
  margin-top: 4px;
  color: #94a3b8;
  font-size: 9px;
}
.alert-list em {
  color: #0f766e;
  font-size: 10px;
  font-style: normal;
  font-weight: 900;
}
.activity-list button {
  grid-template-columns: 1fr auto;
  padding: 11px 12px;
  text-align: left;
}
.activity-list em {
  padding: 5px 8px;
  border-radius: 999px;
  color: #0f766e;
  background: rgba(204,251,241,.52);
  font-size: 10px;
  font-style: normal;
  font-weight: 900;
}
.safe-panel {
  position: relative;
  z-index: 1;
  min-height: 128px;
  display: grid;
  place-content: center;
  text-align: center;
  border: 1px dashed rgba(15,23,42,.10);
  border-radius: 18px;
  background: rgba(248,250,252,.62);
}
.safe-panel b {
  color: #0f766e;
  font-size: 15px;
  font-weight: 950;
}
.safe-panel span {
  margin-top: 6px;
  color: #94a3b8;
  font-size: 11px;
}
.executive-board {
  position: relative;
  z-index: 1;
  padding: 22px;
  border: 1px solid rgba(15,23,42,.08);
  border-radius: 28px;
  background: rgba(255,255,255,.78);
  box-shadow: 0 30px 76px rgba(15,23,42,.12);
  backdrop-filter: blur(20px) saturate(145%);
  animation: launchCardIn .52s ease both;
}
.executive-board::before {
  content: "";
  position: absolute;
  right: -36px;
  top: -42px;
  width: 150px;
  height: 150px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(20,184,166,.14), transparent 68%);
  pointer-events: none;
}
.executive-board header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid rgba(15,23,42,.08);
}
.executive-board header small {
  display: block;
  color: #0f766e;
  font-size: 8px;
  font-weight: 950;
  letter-spacing: .18em;
}
.executive-board header strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 17px;
  font-weight: 950;
  letter-spacing: -.03em;
}
.executive-board header > span {
  padding: 6px 9px;
  border-radius: 999px;
  color: #64748b;
  background: rgba(248,250,252,.86);
  font-size: 10px;
  font-weight: 800;
}
.board-main {
  display: grid;
  grid-template-columns: 132px 1fr;
  gap: 18px;
  align-items: center;
  margin-top: 18px;
}
.board-gauge {
  --gauge: 0deg;
  width: 132px;
  height: 132px;
  padding: 10px;
  border-radius: 50%;
  background:
    conic-gradient(#0f766e var(--gauge), rgba(15,23,42,.08) 0),
    #fff;
  box-shadow: inset 0 0 0 1px rgba(15,23,42,.06), 0 18px 42px rgba(20,184,166,.16);
}
.board-gauge > div {
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  border-radius: 50%;
  background: #fff;
}
.board-gauge b {
  color: #0f172a;
  font-size: 35px;
  font-weight: 950;
  line-height: 1;
  letter-spacing: -.07em;
}
.board-gauge i {
  margin-left: 4px;
  color: #0f766e;
  font-size: 12px;
  font-style: normal;
  font-weight: 900;
}
.board-gauge span {
  margin-top: 6px;
  color: #64748b;
  font-size: 10px;
  font-weight: 800;
}
.board-kpis {
  display: grid;
  gap: 9px;
}
.board-kpis > div {
  padding: 11px 12px;
  border: 1px solid rgba(15,23,42,.07);
  border-radius: 16px;
  background: rgba(248,250,252,.74);
}
.board-kpis span,
.board-flow span {
  color: #64748b;
  font-size: 10px;
  font-weight: 800;
}
.board-kpis b {
  display: block;
  margin-top: 3px;
  color: #0f172a;
  font-size: 21px;
  font-weight: 950;
  line-height: 1;
}
.board-kpis small {
  display: block;
  margin-top: 5px;
  color: #94a3b8;
  font-size: 9px;
}
.board-kpis .warn b { color: #e11d48; }
.board-flow {
  display: grid;
  gap: 9px;
  margin-top: 18px;
}
.board-flow > div {
  display: grid;
  grid-template-columns: 64px 1fr 24px;
  gap: 10px;
  align-items: center;
}
.board-flow i {
  display: block;
  height: 7px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(15,23,42,.07);
}
.board-flow em {
  display: block;
  height: 100%;
  min-width: 0;
  border-radius: inherit;
  transition: width .5s ease;
}
.board-flow b {
  color: #334155;
  text-align: right;
  font-size: 12px;
  font-weight: 950;
}
.health-card {
  color: #0b1220;
  background: rgba(255,255,255,.78);
  border-color: rgba(15,23,42,.08);
  box-shadow: 0 28px 72px rgba(15,23,42,.12);
}
.health-card header small,
.health-main>div:last-child small,
.health-card footer {
  color: #64748b;
}
.health-card header strong,
.health-main>div:last-child strong {
  color: #0f172a;
}
.health-card header em {
  color: #0f766e;
  border-color: rgba(20,184,166,.26);
  background: rgba(204,251,241,.52);
}
.ring {
  background: conic-gradient(#0f766e var(--score), rgba(15,23,42,.08) 0);
}
.ring>div {
  background: #fff;
}
.ring b { color: #0f172a; }
.ring span { color: #64748b; }
.health-main p { color: #64748b; }
.health-card footer {
  border-top-color: rgba(15,23,42,.08);
}
.metrics article,
.surface {
  animation: launchCardIn .5s ease both;
}
.metrics article:nth-child(2) { animation-delay: .04s; }
.metrics article:nth-child(3) { animation-delay: .08s; }
.metrics article:nth-child(4) { animation-delay: .12s; }
.metrics article:nth-child(5) { animation-delay: .16s; }
@keyframes releaseStep {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}
@keyframes releaseLine {
  0%,100% { transform: scaleX(.5); opacity: .35; }
  50% { transform: scaleX(1); opacity: 1; }
}
@keyframes launchCardIn {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: translateY(0); }
}
@media(max-width:1280px){.ops-overview{grid-template-columns:1fr}.todo-grid{grid-template-columns:repeat(2,1fr)}}
@media(max-width:1120px){.hero{grid-template-columns:1fr}.release-flow em{display:none}.executive-board{max-width:620px}.gauge-grid{grid-template-columns:repeat(2,1fr)}}
@media(max-width:620px){.board-main{grid-template-columns:1fr}.board-gauge{margin:auto}.board-flow>div{grid-template-columns:58px 1fr 22px}.gauge-grid{grid-template-columns:1fr}.gauge-card{grid-template-columns:86px 1fr}.gauge-ring{width:86px;height:86px}.todo-grid{grid-template-columns:1fr}}

/* The hero no longer hosts gauges; keep it clean and balanced. */
.hero {
  grid-template-columns: 1fr;
}
.hero-copy {
  max-width: 920px;
}
</style>
