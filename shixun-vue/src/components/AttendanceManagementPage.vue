<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { User } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const props = defineProps<{ type: 'home'|'cardRepair'|'leave'|'businessTrip'|'outgoing'; currentUser?: User }>()

type Cfg = { title:string; desc:string; fields:string[]; tips:string[] }
const category = 'attendance'
const config: Record<string, Cfg> = {
  home:{title:'考勤管理',desc:'统一管理员工补卡、请假、出差和外出申请。左侧为大菜单，子页面互相独立。',fields:[],tips:['从左侧子菜单进入对应申请','不同考勤申请使用不同字段','提交后进入审批中心统一处理']},
  cardRepair:{title:'补卡申请',desc:'用于漏打卡、忘打卡、设备异常等补卡申请。',fields:['申请人','所属部门','补卡日期','补卡时间','补卡类型','异常原因','证明人','备注'],tips:['写清补卡日期和时间','补卡原因需真实可核验','如有证明人请填写']},
  leave:{title:'请假申请',desc:'用于事假、病假、年假、调休等请假申请。',fields:['请假人','所属部门','请假类型','开始时间','结束时间','请假天数/小时','请假原因','工作交接人'],tips:['请假时间需精确到日期/小时','写清工作交接安排','病假等按制度补充证明']},
  businessTrip:{title:'出差申请',desc:'用于因公出差目的、行程、预算和审批记录。',fields:['出差人','所属部门','出差地点','出差事由','出发时间','返回时间','预计费用','同行人员'],tips:['明确出差目的和行程','预计费用请提前测算','多人同行请补充名单']},
  outgoing:{title:'外出申请',desc:'用于工作时间临时外出、拜访客户、办事等外出申请。',fields:['外出人','所属部门','外出事由','外出地点','开始时间','结束时间','联系人/客户','交通方式'],tips:['外出事由需具体','填写预计返回时间','客户拜访可备注联系人']},
}
const current = computed(()=>config[props.type])
const form = reactive<Record<string,string>>({})
const saved = ref<any[]>([])
const loading = ref(false)
function reset(){ Object.keys(form).forEach(k=>delete form[k]); [...current.value.fields,'补充说明'].forEach(k=>form[k]='') }
async function loadRecords(){
  if(props.type==='home') return
  loading.value=true
  try{
    const p=new URLSearchParams({category, applicant: props.currentUser?.username || '', page:'1', size:'100'})
    const res=await fetch(`/api/workflows/applications?${p}`); if(!res.ok) throw new Error(await res.text())
    const data=await res.json(); const list=Array.isArray(data)?data:data.content||[]
    saved.value=list.filter((x:any)=>(x.type||x.typeKey)===props.type)
  }catch{ saved.value=[]; emit('alert','加载考勤申请记录失败','error') } finally{ loading.value=false }
}
async function submit(){
  if(props.type==='home') return
  const fields=Object.fromEntries([...current.value.fields,'补充说明'].map(k=>[k,form[k]||'']))
  const res=await fetch('/api/workflows/applications',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({category,typeKey:props.type,title:current.value.title,applicant:props.currentUser?.username||'当前用户',applicantRole:props.currentUser?.role||'feeder',fields})})
  if(!res.ok) throw new Error(await res.text())
  Object.keys(form).forEach(k=>form[k]=''); await loadRecords(); emit('alert','考勤申请已提交到审批中心','success')
}
async function submitWithGuard(){ try{ await submit() }catch(e:any){ emit('alert',`提交失败：${e.message||e}`,'error') } }
watch(()=>props.type, async()=>{ reset(); saved.value=[]; await loadRecords() })
onMounted(async()=>{ reset(); await loadRecords() })
</script>

<template>
  <div class="attendance-page">
    <section class="attendance-hero"><span>ATTENDANCE WORKFLOW</span><h2>{{ current.title }}</h2><p>{{ current.desc }}</p></section>
    <section v-if="props.type==='home'" class="overview-grid"><article v-for="item in Object.entries(config).filter(([k])=>k!=='home')" :key="item[0]"><b>{{item[1].title}}</b><p>{{item[1].desc}}</p></article></section>
    <section v-else class="content-grid">
      <article class="form-card"><header><div><h3>申请信息</h3><p>该页面独立提交 <b>{{ current.title }}</b>，不会混入其他考勤子页面记录。</p></div></header><div class="form-grid"><label v-for="f in current.fields" :key="f">{{f}}<input v-model="form[f]" :placeholder="'请输入'+f" /></label></div><label>补充说明<textarea v-model="form['补充说明']" rows="4" placeholder="请输入附件清单、审批备注或其他说明"></textarea></label><button class="submit" @click="submitWithGuard">提交申请到审批中心</button></article>
      <aside class="tips-card"><h3>填写提示</h3><p v-for="tip in current.tips" :key="tip">{{tip}}</p></aside>
    </section>
    <section v-if="props.type!=='home'" class="records-card"><h3>本页已提交记录</h3><div v-if="loading" class="empty">正在加载已提交记录...</div><div v-else-if="!saved.length" class="empty">暂无当前子页面提交记录</div><div v-else class="record-list"><p v-for="x in saved" :key="x.id"><b>{{x.title}}</b><span>{{x.status==='pending'?'待审批':x.status==='approved'?'已通过':'已驳回'}}</span><em>{{x.createdAt||x.created_at||x.updatedAt||'—'}}</em></p></div></section>
  </div>
</template>

<style scoped>
.attendance-page{min-height:100vh;background:#f6f8fb}.attendance-hero{padding:34px;border-radius:22px;background:linear-gradient(120deg,#0f172a,#0f766e 58%,#65a30d);color:#fff;box-shadow:0 18px 45px rgba(15,23,42,.18)}.attendance-hero span{font-size:10px;letter-spacing:2px;color:#ccfbf1;font-weight:900}.attendance-hero h2{margin:8px 0;font-size:32px}.attendance-hero p{margin:0;color:#ecfdf5}.overview-grid{display:grid;grid-template-columns:repeat(2,1fr);gap:14px;margin-top:18px}.overview-grid article,.form-card,.tips-card,.records-card{padding:20px;border:1px solid #e2e8f0;border-radius:18px;background:#fff}.overview-grid p,.form-card p,.tips-card p{color:#64748b;line-height:1.7}.content-grid{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:18px;margin-top:18px}.form-card h3,.tips-card h3,.records-card h3{margin:0 0 12px}.form-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}label{display:block;color:#475569;font-size:12px;font-weight:800}input,textarea{width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #cbd5e1;border-radius:10px;padding:10px;font:inherit}input{height:40px}.submit{margin-top:16px;border:0;border-radius:12px;padding:12px 18px;background:#0f766e;color:#fff;font-weight:900;cursor:pointer}.submit:hover{background:#115e59}.tips-card p{padding:12px;border-radius:12px;background:#ecfdf5;border:1px solid #99f6e4;margin:0 0 10px}.records-card{margin-top:18px}.empty{color:#94a3b8;padding:18px;border-radius:12px;background:#f8fafc}.record-list p{display:grid;grid-template-columns:1fr auto auto;gap:12px;align-items:center;border-bottom:1px solid #e2e8f0;padding:10px 0;margin:0}.record-list span{font-size:12px;font-weight:800;color:#0f766e;background:#ecfdf5;border-radius:999px;padding:4px 10px}.record-list em{color:#64748b;font-style:normal}@media(max-width:1000px){.overview-grid,.content-grid,.form-grid{grid-template-columns:1fr}.record-list p{grid-template-columns:1fr}}
</style>
