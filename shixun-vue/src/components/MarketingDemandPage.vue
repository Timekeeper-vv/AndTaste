<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { User } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const props = defineProps<{
  type: 'home' | 'promotion' | 'ecommerceNewProduct' | 'shooting'
  currentUser?: User
}>()

type MarketConfig = {
  title: string
  desc: string
  fields: string[]
  tips: string[]
}

const category = 'marketDepartment'

const config: Record<string, MarketConfig> = {
  home: {
    title: '市场部需求管理',
    desc: '统一管理市场宣传、电商上新、拍摄排期和产品文案需求。左侧为大菜单，四个子页面彼此独立。',
    fields: [],
    tips: ['从左侧子菜单进入对应页面', '不同页面使用不同申请类型与表单字段', '提交后进入审批中心统一处理']
  },
  promotion: {
    title: '营销宣传申请',
    desc: '用于活动推广、节日营销、渠道投放、物料制作等宣传需求申请。',
    fields: ['需求名称', '宣传主题', '投放渠道', '目标人群', '上线时间', '预算金额', '所需物料', '预期效果'],
    tips: ['明确宣传目标与渠道', '补充参考案例或素材附件', '涉及预算需写清费用拆分']
  },
  ecommerceNewProduct: {
    title: '电商新品上架申请',
    desc: '用于电商平台新品上架资料、价格、库存、详情页和活动资源位申请。',
    fields: ['商品名称', '商品编码/SKU', '上架平台', '商品类目', '建议售价', '首批库存', '卖点摘要', '计划上架时间'],
    tips: ['商品标题、主图、详情资料要同步准备', '价格与库存需和供应链确认', '若参加活动请写清活动档期']
  },
  shooting: {
    title: '拍摄需求申请',
    desc: '用于产品图、场景图、短视频、直播素材等拍摄需求和排期申请。',
    fields: ['拍摄项目', '产品/主题', '拍摄类型', '使用渠道', '交付时间', '拍摄地点', '参与人员', '参考风格'],
    tips: ['尽量提供参考图或脚本方向', '明确横版/竖版、尺寸和数量', '需要模特/场地/道具请提前说明']
  }
}

const current = computed(() => config[props.type])
const form = reactive<Record<string, string>>({})
const saved = ref<any[]>([])
const loading = ref(false)

function resetFormForType() {
  Object.keys(form).forEach(k => delete form[k])
  ;[...current.value.fields, '补充说明'].forEach(k => { form[k] = '' })
}

async function loadRecords() {
  if (props.type === 'home') return
  loading.value = true
  try {
    const p = new URLSearchParams({
      category,
      applicant: props.currentUser?.username || '',
      page: '1',
      size: '100'
    })
    const res = await fetch(`/api/workflows/applications?${p}`)
    if (!res.ok) throw new Error(await res.text())
    const data = await res.json()
    const list = Array.isArray(data) ? data : data.content || []
    saved.value = list.filter((x: any) => (x.type || x.typeKey) === props.type)
  } catch {
    saved.value = []
    emit('alert', '加载市场部申请记录失败', 'error')
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (props.type === 'home') return
  const fields = Object.fromEntries([...current.value.fields, '补充说明'].map(k => [k, form[k] || '']))
  const res = await fetch('/api/workflows/applications', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      category,
      typeKey: props.type,
      title: current.value.title,
      applicant: props.currentUser?.username || '当前用户',
      applicantRole: props.currentUser?.role || 'feeder',
      fields
    })
  })
  if (!res.ok) throw new Error(await res.text())
  Object.keys(form).forEach(k => { form[k] = '' })
  await loadRecords()
  emit('alert', '市场部需求已提交到审批中心', 'success')
}

async function submitWithGuard() {
  try {
    await submit()
  } catch (e: any) {
    emit('alert', `提交失败：${e.message || e}`, 'error')
  }
}

watch(() => props.type, async () => {
  resetFormForType()
  saved.value = []
  await loadRecords()
})

onMounted(async () => {
  resetFormForType()
  await loadRecords()
})
</script>

<template>
  <div class="market-demand-page">
    <section class="market-hero">
      <span>MARKETING DEPARTMENT WORKFLOW</span>
      <h2>{{ current.title }}</h2>
      <p>{{ current.desc }}</p>
    </section>

    <section v-if="props.type === 'home'" class="overview-grid">
      <article v-for="item in Object.entries(config).filter(([k]) => k !== 'home')" :key="item[0]">
        <b>{{ item[1].title }}</b>
        <p>{{ item[1].desc }}</p>
      </article>
    </section>

    <section v-else class="content-grid">
      <article class="form-card">
        <header>
          <div>
            <h3>申请信息</h3>
            <p>该页面独立提交 <b>{{ current.title }}</b>，不会混入其他市场部子页面记录。</p>
          </div>
        </header>
        <div class="form-grid">
          <label v-for="f in current.fields" :key="f">
            {{ f }}
            <input v-model="form[f]" :placeholder="'请输入' + f" />
          </label>
        </div>
        <label>
          补充说明
          <textarea v-model="form['补充说明']" rows="4" placeholder="请输入附件清单、参考链接、审批备注或其他要求"></textarea>
        </label>
        <button class="submit" @click="submitWithGuard">提交申请到审批中心</button>
      </article>

      <aside class="tips-card">
        <h3>填写提示</h3>
        <p v-for="tip in current.tips" :key="tip">{{ tip }}</p>
      </aside>
    </section>

    <section v-if="props.type !== 'home'" class="records-card">
      <h3>本页已提交记录</h3>
      <div v-if="loading" class="empty">正在加载已提交记录...</div>
      <div v-else-if="!saved.length" class="empty">暂无当前子页面提交记录</div>
      <div v-else class="record-list">
        <p v-for="x in saved" :key="x.id">
          <b>{{ x.title }}</b>
          <span>{{ x.status === 'pending' ? '待审批' : x.status === 'approved' ? '已通过' : '已驳回' }}</span>
          <em>{{ x.createdAt || x.created_at || x.updatedAt || '—' }}</em>
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.market-demand-page{min-height:100vh;background:#f6f8fb}.market-hero{padding:34px;border-radius:22px;background:linear-gradient(120deg,#111827,#9f1239 58%,#f97316);color:#fff;box-shadow:0 18px 45px rgba(15,23,42,.18)}.market-hero span{font-size:10px;letter-spacing:2px;color:#fed7aa;font-weight:900}.market-hero h2{margin:8px 0;font-size:32px}.market-hero p{margin:0;color:#ffe4e6}.overview-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px;margin-top:18px}.overview-grid article,.form-card,.tips-card,.records-card{padding:20px;border:1px solid #e2e8f0;border-radius:18px;background:#fff}.overview-grid p,.form-card p,.tips-card p{color:#64748b;line-height:1.7}.content-grid{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:18px;margin-top:18px}.form-card header{display:flex;justify-content:space-between;gap:12px;margin-bottom:14px}.form-card h3,.tips-card h3,.records-card h3{margin:0 0 12px}.form-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}label{display:block;color:#475569;font-size:12px;font-weight:800}input,textarea{width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #cbd5e1;border-radius:10px;padding:10px;font:inherit}input{height:40px}.submit{margin-top:16px;border:0;border-radius:12px;padding:12px 18px;background:#be123c;color:#fff;font-weight:900;cursor:pointer}.submit:hover{background:#9f1239}.tips-card p{padding:12px;border-radius:12px;background:#fff7ed;border:1px solid #fed7aa;margin:0 0 10px}.records-card{margin-top:18px}.empty{color:#94a3b8;padding:18px;border-radius:12px;background:#f8fafc}.record-list p{display:grid;grid-template-columns:1fr auto auto;gap:12px;align-items:center;border-bottom:1px solid #e2e8f0;padding:10px 0;margin:0}.record-list span{font-size:12px;font-weight:800;color:#be123c;background:#fff1f2;border-radius:999px;padding:4px 10px}.record-list em{color:#64748b;font-style:normal}@media(max-width:1000px){.overview-grid,.content-grid,.form-grid{grid-template-columns:1fr}.record-list p{grid-template-columns:1fr}}
</style>
