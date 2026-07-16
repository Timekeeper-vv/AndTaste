<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { User } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const props = defineProps<{ type:
  'home'|'assetScrap'|'publicPayment'|'pettyCash'|'personalExpense'|'promotionApproval'|'seal'|'pettyCashRepay'|'travel'|'invoice'|'specialExpense'|'pettyCashWriteoff';
  currentUser?: User
}>()

const config: Record<string,{title:string,desc:string,fields:string[]}> = {
  home:{title:'财务管理',desc:'统一管理财务类申请、报销、付款、开票、用章和备用金流程。',fields:[]},
  assetScrap:{title:'固定资产报废申请',desc:'用于固定资产报废、处置原因、残值和审批留痕。',fields:['资产名称','资产编号','购置日期','原值/净值','报废原因','处理方式']},
  publicPayment:{title:'对公付款申请(供应链)',desc:'用于供应链采购、加工、物流等对公付款申请。',fields:['收款单位','开户行','银行账号','付款金额','付款事由','关联订单/合同']},
  pettyCash:{title:'备用金申请',desc:'用于业务人员、门店或项目临时备用金申请。',fields:['申请金额','用途说明','使用部门','预计归还/核销日期','收款人','备注']},
  personalExpense:{title:'个人费用报销',desc:'用于员工个人办公、交通、招待等费用报销。',fields:['报销人','费用类型','发生日期','报销金额','费用说明','票据张数']},
  promotionApproval:{title:'促销活动审批',desc:'用于促销活动预算、活动周期、门店范围和财务审批。',fields:['活动名称','活动门店','活动时间','预算金额','促销方式','预期效果']},
  seal:{title:'用章用印申请',desc:'用于合同、付款、授权文件等用章用印申请。',fields:['文件名称','用章类型','使用部门','使用原因','份数','是否外带']},
  pettyCashRepay:{title:'备用金还款',desc:'用于备用金归还、还款方式和对应申请单核销。',fields:['原备用金单号','还款金额','还款方式','还款日期','经办人','备注']},
  travel:{title:'差旅报销',desc:'用于差旅交通、住宿、补贴等费用报销。',fields:['出差人','出差地点','出差日期','交通费','住宿费','差旅说明']},
  invoice:{title:'开票申请',desc:'用于客户开票、税号、金额、项目和寄送信息登记。',fields:['客户名称','纳税人识别号','开票金额','发票类型','开票内容','接收邮箱/地址']},
  specialExpense:{title:'费用报销(特殊事项)',desc:'用于特殊费用、非常规事项和专项审批报销。',fields:['事项名称','费用金额','特殊原因','所属项目','审批依据','附件说明']},
  pettyCashWriteoff:{title:'备用金核销',desc:'用于备用金使用后按票据和明细进行核销。',fields:['备用金单号','核销金额','票据金额','未用退回','费用明细','核销说明']},
}
const current = computed(()=>config[props.type])
const form = reactive<Record<string,string>>({})
const saved = ref<any[]>([])
const loading = ref(false)

async function loadRecords(){
  if (props.type === 'home') return
  loading.value = true
  try {
    const p = new URLSearchParams({ category: 'finance', applicant: props.currentUser?.username || '', page: '1', size: '20' })
    const res = await fetch(`/api/workflows/applications?${p}`)
    const data = await res.json()
    saved.value = Array.isArray(data) ? data : data.content || []
  } catch {
    saved.value = []
    emit('alert', '加载财务申请记录失败', 'error')
  } finally {
    loading.value = false
  }
}

async function submit(){
  if (props.type === 'home') return
  const fields = Object.fromEntries([...current.value.fields, '补充说明'].map(k => [k, form[k] || '']))
  const res = await fetch('/api/workflows/applications', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      category: 'finance',
      typeKey: props.type,
      title: current.value.title,
      applicant: props.currentUser?.username || '当前用户',
      applicantRole: props.currentUser?.role || 'feeder',
      fields,
    })
  })
  if (!res.ok) throw new Error(await res.text())
  saved.value.unshift(await res.json())
  Object.keys(form).forEach(k=>form[k]='')
  await loadRecords()
  emit('alert', '申请已提交到审批中心', 'success')
}

async function submitWithGuard() {
  try {
    await submit()
  } catch (e: any) {
    emit('alert', `提交失败：${e.message || e}`, 'error')
  }
}

onMounted(loadRecords)
</script>

<template>
  <div class="finance-page">
    <section class="finance-hero">
      <span>FINANCE WORKFLOW</span>
      <h2>{{ current.title }}</h2>
      <p>{{ current.desc }}</p>
    </section>

    <section v-if="props.type==='home'" class="finance-grid">
      <article v-for="item in Object.entries(config).filter(([k])=>k!=='home')" :key="item[0]">
        <b>{{ item[1].title }}</b><p>{{ item[1].desc }}</p>
      </article>
    </section>

    <section v-else class="finance-card">
      <header><h3>申请信息</h3><em>本地表单可填写提交，后续可接入审批流与数据库。</em></header>
      <div class="form-grid">
        <label v-for="f in current.fields" :key="f">{{ f }}<input v-model="form[f]" :placeholder="'请输入'+f"></label>
      </div>
      <label>补充说明<textarea v-model="form['补充说明']" rows="4" placeholder="请输入补充说明、附件清单或审批备注"></textarea></label>
      <button class="submit" @click="submitWithGuard">提交申请到审批中心</button>
      <div v-if="loading" class="saved-list"><p>正在加载已提交记录...</p></div>
      <div v-else-if="saved.length" class="saved-list"><h3>本页已提交记录</h3><p v-for="x in saved" :key="x.id"><b>{{x.title}}</b><span>{{x.createdAt || x.created_at || x.updatedAt || '—'}}</span></p></div>
    </section>
  </div>
</template>

<style scoped>
.finance-page{min-height:100vh;background:#f6f8fb}.finance-hero{padding:34px;border-radius:22px;background:linear-gradient(120deg,#111827,#1e3a8a 62%,#7c2d12);color:#fff;box-shadow:0 18px 45px rgba(15,23,42,.18)}.finance-hero span{font-size:10px;letter-spacing:2px;color:#fde68a;font-weight:900}.finance-hero h2{margin:8px 0;font-size:32px}.finance-hero p{margin:0;color:#dbeafe}.finance-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:14px;margin-top:18px}.finance-grid article,.finance-card{padding:20px;border:1px solid #e2e8f0;border-radius:18px;background:#fff}.finance-grid p,.finance-card em{color:#64748b;line-height:1.7;font-style:normal}.finance-card{margin-top:18px}.finance-card header{display:flex;justify-content:space-between;gap:12px}.form-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:12px}label{display:block;color:#475569;font-size:12px;font-weight:800}input,textarea{width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #cbd5e1;border-radius:10px;padding:10px;font:inherit}.submit{margin-top:16px;border:0;border-radius:12px;padding:12px 18px;background:#1d4ed8;color:#fff;font-weight:900;cursor:pointer}.saved-list{margin-top:18px;padding:14px;border-radius:14px;background:#f8fafc}.saved-list p{display:flex;justify-content:space-between;border-bottom:1px solid #e2e8f0;padding:9px 0;margin:0}.saved-list span{color:#64748b}@media(max-width:1000px){.finance-grid,.form-grid{grid-template-columns:1fr}}
</style>
