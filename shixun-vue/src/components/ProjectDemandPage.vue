<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { User } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const props = defineProps<{
  type: 'home' | 'initiation' | 'inquiry'
  currentUser?: User
}>()

type ProjectConfig = {
  title: string
  desc: string
  fields: string[]
  tips: string[]
}

const category = 'projectDepartment'

const config: Record<string, ProjectConfig> = {
  home: {
    title: '项目部需求管理',
    desc: '统一管理项目立项、项目询价等项目部需求。左侧为大菜单，子页面彼此独立。',
    fields: [],
    tips: ['从左侧子菜单进入对应申请页面', '立项与询价使用不同申请类型和字段', '提交后进入审批中心统一处理']
  },
  initiation: {
    title: '项目立项申请',
    desc: '用于新项目启动、背景说明、目标范围、预算周期和资源需求申请。',
    fields: ['项目名称', '项目编号', '项目负责人', '所属客户/部门', '项目背景', '项目目标', '计划周期', '预算金额', '资源需求'],
    tips: ['写清项目目标、范围和交付物', '涉及预算请补充测算依据', '需要跨部门配合请注明资源需求']
  },
  inquiry: {
    title: '项目询价申请',
    desc: '用于项目物料、工艺、服务、供应商报价等询价需求申请。',
    fields: ['询价项目', '需求品类', '规格参数', '预计数量', '期望交期', '目标价格', '候选供应商', '报价用途', '联系人'],
    tips: ['规格、数量、交期越明确，报价越准确', '如已有供应商请填写候选名单', '询价结果可作为立项预算或采购依据']
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
    emit('alert', '加载项目部申请记录失败', 'error')
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
  emit('alert', '项目部需求已提交到审批中心', 'success')
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
  <div class="project-demand-page">
    <section class="project-hero">
      <span>PROJECT DEPARTMENT WORKFLOW</span>
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
            <p>该页面独立提交 <b>{{ current.title }}</b>，不会混入其他项目部子页面记录。</p>
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
          <textarea v-model="form['补充说明']" rows="4" placeholder="请输入附件清单、参考链接、审批备注或其他项目要求"></textarea>
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
.project-demand-page{min-height:100vh;background:#f6f8fb}.project-hero{padding:34px;border-radius:22px;background:linear-gradient(120deg,#0f172a,#1d4ed8 58%,#0891b2);color:#fff;box-shadow:0 18px 45px rgba(15,23,42,.18)}.project-hero span{font-size:10px;letter-spacing:2px;color:#bfdbfe;font-weight:900}.project-hero h2{margin:8px 0;font-size:32px}.project-hero p{margin:0;color:#dbeafe}.overview-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px;margin-top:18px}.overview-grid article,.form-card,.tips-card,.records-card{padding:20px;border:1px solid #e2e8f0;border-radius:18px;background:#fff}.overview-grid p,.form-card p,.tips-card p{color:#64748b;line-height:1.7}.content-grid{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:18px;margin-top:18px}.form-card header{display:flex;justify-content:space-between;gap:12px;margin-bottom:14px}.form-card h3,.tips-card h3,.records-card h3{margin:0 0 12px}.form-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}label{display:block;color:#475569;font-size:12px;font-weight:800}input,textarea{width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #cbd5e1;border-radius:10px;padding:10px;font:inherit}input{height:40px}.submit{margin-top:16px;border:0;border-radius:12px;padding:12px 18px;background:#1d4ed8;color:#fff;font-weight:900;cursor:pointer}.submit:hover{background:#1e40af}.tips-card p{padding:12px;border-radius:12px;background:#eff6ff;border:1px solid #bfdbfe;margin:0 0 10px}.records-card{margin-top:18px}.empty{color:#94a3b8;padding:18px;border-radius:12px;background:#f8fafc}.record-list p{display:grid;grid-template-columns:1fr auto auto;gap:12px;align-items:center;border-bottom:1px solid #e2e8f0;padding:10px 0;margin:0}.record-list span{font-size:12px;font-weight:800;color:#1d4ed8;background:#eff6ff;border-radius:999px;padding:4px 10px}.record-list em{color:#64748b;font-style:normal}@media(max-width:1000px){.overview-grid,.content-grid,.form-grid{grid-template-columns:1fr}.record-list p{grid-template-columns:1fr}}
</style>
