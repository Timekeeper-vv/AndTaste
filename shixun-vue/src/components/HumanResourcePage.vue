<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import type { User } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const props = defineProps<{ type: 'home'|'newProductIncentive'|'resignation'|'training'|'holidayOvertime'|'transfer'|'policyApproval'|'regularization'|'recruitment'; currentUser?: User }>()

type Cfg = { title:string; desc:string; fields:string[]; tips:string[] }
const category = 'humanResource'
const config: Record<string, Cfg> = {
  home:{title:'人力资源管理',desc:'统一管理员工激励、离职、培训、加班、调岗、制度方案、转正和招聘等人力资源申请。左侧为大菜单，子页面互相独立。',fields:[],tips:['从左侧子菜单进入对应申请','每个子页面独立字段和记录','提交后进入审批中心']},
  newProductIncentive:{title:'新产品开发激励',desc:'用于新产品开发贡献、激励金额、参与人员与成果说明申请。',fields:['激励项目/产品','申请部门','参与人员','贡献说明','成果产出','建议激励金额','完成时间','证明材料'],tips:['写清个人/团队贡献','补充产品成果或数据证明','激励金额需说明依据']},
  resignation:{title:'离职申请',desc:'用于员工离职原因、交接安排、最后工作日和审批留痕。',fields:['离职人','所属部门','岗位','入职日期','拟离职日期','离职原因','工作交接人','交接事项'],tips:['提前确认最后工作日','写清交接人和交接清单','涉及资产归还请备注']},
  training:{title:'培训申请',desc:'用于内部/外部培训、预算、参训人员和培训目标申请。',fields:['培训名称','培训类型','参训人员','培训机构/讲师','培训时间','培训地点','培训费用','培训目标'],tips:['明确培训目标和适用岗位','外部培训需说明费用明细','培训后可补充转训计划']},
  holidayOvertime:{title:'加班申请【法定节假日】',desc:'用于法定节假日加班原因、时间、人员和补偿方式申请。',fields:['加班人员','所属部门','加班日期','加班时段','加班地点','加班原因','工作内容','补偿方式'],tips:['仅用于法定节假日加班','加班原因需具体可核验','补偿方式按公司制度填写']},
  transfer:{title:'调岗申请',desc:'用于员工岗位调整、调入调出部门、调岗原因和生效时间申请。',fields:['员工姓名','当前部门','当前岗位','调入部门','调入岗位','调岗原因','生效日期','交接安排'],tips:['明确调岗前后岗位','说明调岗原因和业务必要性','同步填写交接安排']},
  policyApproval:{title:'制度&方案审批',desc:'用于人事制度、管理方案、激励方案等文件审批。',fields:['制度/方案名称','发起部门','适用范围','生效时间','核心内容','修订原因','影响说明','附件清单'],tips:['写清适用范围和生效时间','制度修订需说明变化点','上传或备注附件清单']},
  regularization:{title:'转正申请',desc:'用于试用期员工转正评价、绩效表现、转正日期和薪酬建议申请。',fields:['员工姓名','所属部门','岗位','入职日期','拟转正日期','试用期表现','直属上级评价','转正建议'],tips:['结合试用期目标评价','写清是否建议转正','如涉及薪酬调整请备注']},
  recruitment:{title:'招聘申请',desc:'用于新增/补缺岗位、招聘人数、任职要求和到岗时间申请。',fields:['招聘岗位','申请部门','招聘人数','招聘原因','岗位职责','任职要求','期望到岗时间','薪资范围'],tips:['明确新增或补缺原因','岗位职责和任职要求要具体','薪资范围建议提前确认']},
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
  }catch{ saved.value=[]; emit('alert','加载人力资源申请记录失败','error') } finally{ loading.value=false }
}
async function submit(){
  if(props.type==='home') return
  const fields=Object.fromEntries([...current.value.fields,'补充说明'].map(k=>[k,form[k]||'']))
  const res=await fetch('/api/workflows/applications',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({category,typeKey:props.type,title:current.value.title,applicant:props.currentUser?.username||'当前用户',applicantRole:props.currentUser?.role||'feeder',fields})})
  if(!res.ok) throw new Error(await res.text())
  Object.keys(form).forEach(k=>form[k]=''); await loadRecords(); emit('alert','人力资源申请已提交到审批中心','success')
}
async function submitWithGuard(){ try{ await submit() }catch(e:any){ emit('alert',`提交失败：${e.message||e}`,'error') } }
watch(()=>props.type, async()=>{ reset(); saved.value=[]; await loadRecords() })
onMounted(async()=>{ reset(); await loadRecords() })
</script>

<template>
  <div class="hr-page">
    <section class="hr-hero"><span>HUMAN RESOURCE WORKFLOW</span><h2>{{ current.title }}</h2><p>{{ current.desc }}</p></section>
    <section v-if="props.type==='home'" class="overview-grid"><article v-for="item in Object.entries(config).filter(([k])=>k!=='home')" :key="item[0]"><b>{{item[1].title}}</b><p>{{item[1].desc}}</p></article></section>
    <section v-else class="content-grid">
      <article class="form-card"><header><div><h3>申请信息</h3><p>该页面独立提交 <b>{{ current.title }}</b>，不会混入其他人资子页面记录。</p></div></header><div class="form-grid"><label v-for="f in current.fields" :key="f">{{f}}<input v-model="form[f]" :placeholder="'请输入'+f" /></label></div><label>补充说明<textarea v-model="form['补充说明']" rows="4" placeholder="请输入附件清单、审批备注或其他说明"></textarea></label><button class="submit" @click="submitWithGuard">提交申请到审批中心</button></article>
      <aside class="tips-card"><h3>填写提示</h3><p v-for="tip in current.tips" :key="tip">{{tip}}</p></aside>
    </section>
    <section v-if="props.type!=='home'" class="records-card"><h3>本页已提交记录</h3><div v-if="loading" class="empty">正在加载已提交记录...</div><div v-else-if="!saved.length" class="empty">暂无当前子页面提交记录</div><div v-else class="record-list"><p v-for="x in saved" :key="x.id"><b>{{x.title}}</b><span>{{x.status==='pending'?'待审批':x.status==='approved'?'已通过':'已驳回'}}</span><em>{{x.createdAt||x.created_at||x.updatedAt||'—'}}</em></p></div></section>
  </div>
</template>

<style scoped>
.hr-page{min-height:100vh;background:#f6f8fb}.hr-hero{padding:34px;border-radius:22px;background:linear-gradient(120deg,#111827,#7c3aed 58%,#db2777);color:#fff;box-shadow:0 18px 45px rgba(15,23,42,.18)}.hr-hero span{font-size:10px;letter-spacing:2px;color:#ddd6fe;font-weight:900}.hr-hero h2{margin:8px 0;font-size:32px}.hr-hero p{margin:0;color:#f5f3ff}.overview-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:18px}.overview-grid article,.form-card,.tips-card,.records-card{padding:20px;border:1px solid #e2e8f0;border-radius:18px;background:#fff}.overview-grid p,.form-card p,.tips-card p{color:#64748b;line-height:1.7}.content-grid{display:grid;grid-template-columns:minmax(0,1fr) 280px;gap:18px;margin-top:18px}.form-card h3,.tips-card h3,.records-card h3{margin:0 0 12px}.form-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}label{display:block;color:#475569;font-size:12px;font-weight:800}input,textarea{width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #cbd5e1;border-radius:10px;padding:10px;font:inherit}input{height:40px}.submit{margin-top:16px;border:0;border-radius:12px;padding:12px 18px;background:#7c3aed;color:#fff;font-weight:900;cursor:pointer}.submit:hover{background:#6d28d9}.tips-card p{padding:12px;border-radius:12px;background:#f5f3ff;border:1px solid #ddd6fe;margin:0 0 10px}.records-card{margin-top:18px}.empty{color:#94a3b8;padding:18px;border-radius:12px;background:#f8fafc}.record-list p{display:grid;grid-template-columns:1fr auto auto;gap:12px;align-items:center;border-bottom:1px solid #e2e8f0;padding:10px 0;margin:0}.record-list span{font-size:12px;font-weight:800;color:#7c3aed;background:#f5f3ff;border-radius:999px;padding:4px 10px}.record-list em{color:#64748b;font-style:normal}@media(max-width:1000px){.overview-grid,.content-grid,.form-grid{grid-template-columns:1fr}.record-list p{grid-template-columns:1fr}}
</style>
