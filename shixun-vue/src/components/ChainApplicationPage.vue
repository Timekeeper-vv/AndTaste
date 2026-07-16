<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { User } from '../types'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()

const props = defineProps<{ type: 'home' | 'marketing' | 'newProduct' | 'priceAdjust'; currentUser?: User }>()
const titles = {
  home: '之间连锁',
  marketing: '门店营销方案申请【连锁】',
  newProduct: '新商品上架申请【连锁】',
  priceAdjust: '商品售价调整申请【连锁】'
}
const desc = {
  home: '连锁门店业务申请入口，左侧菜单已按“之间连锁”统一归类。',
  marketing: '用于门店活动、节日促销、社群推广、陈列物料等营销方案申请。',
  newProduct: '用于连锁门店新品上架、资料提交、门店适配和铺货申请。',
  priceAdjust: '用于连锁商品售价调整、调价原因、影响门店和生效时间申请。'
}
const fields: Record<string,string[]> = {
  marketing: ['申请门店','活动名称','活动时间','预算金额','营销方案','预期效果'],
  newProduct: ['商品名称','商品类别','建议售价','适用门店','上架原因','铺货计划'],
  priceAdjust: ['商品名称','当前售价','调整后售价','生效时间','影响门店','调价原因']
}
const form = reactive<Record<string,string>>({})
const saved = ref<any[]>([])
const loading = ref(false)

async function loadRecords(){
  if (props.type === 'home') return
  loading.value = true
  try {
    const p = new URLSearchParams({ category: 'chain', applicant: props.currentUser?.username || '', page: '1', size: '20' })
    const res = await fetch(`/api/workflows/applications?${p}`)
    const data = await res.json()
    saved.value = Array.isArray(data) ? data : data.content || []
  } catch {
    saved.value = []
    emit('alert', '加载连锁申请记录失败', 'error')
  } finally {
    loading.value = false
  }
}

async function submit(){
  if (props.type === 'home') return
  const currentFields = Object.fromEntries([...(fields[props.type] || []), '补充说明'].map(k => [k, form[k] || '']))
  const res = await fetch('/api/workflows/applications', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      category: 'chain',
      typeKey: props.type,
      title: titles[props.type],
      applicant: props.currentUser?.username || '当前用户',
      applicantRole: props.currentUser?.role || 'feeder',
      fields: currentFields,
    })
  })
  if (!res.ok) throw new Error(await res.text())
  saved.value.unshift(await res.json())
  Object.keys(form).forEach(k => form[k] = '')
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
  <div class="chain-page">
    <section class="chain-hero">
      <span>CHAIN STORE WORKFLOW</span>
      <h2>{{ titles[props.type] }}</h2>
      <p>{{ desc[props.type] }}</p>
    </section>
    <section class="chain-card">
      <h3>{{ props.type === 'home' ? '功能说明' : '申请表单' }}</h3>
      <div v-if="props.type === 'home'" class="chain-links">
        <article><b>门店营销方案申请</b><p>管理连锁门店营销活动申请。</p></article>
        <article><b>新商品上架申请</b><p>管理新品在连锁门店的上架流程。</p></article>
        <article><b>商品售价调整申请</b><p>管理商品价格调整和审批记录。</p></article>
      </div>
      <div v-else class="chain-form">
        <div class="form-grid">
          <label v-for="f in fields[props.type]" :key="f">{{ f }}<input v-model="form[f]" :placeholder="'请输入'+f"></label>
        </div>
        <label>补充说明<textarea v-model="form['补充说明']" rows="4" placeholder="请输入附件、说明或审批备注"></textarea></label>
        <button class="submit" @click="submitWithGuard">提交申请到审批中心</button>
        <div v-if="loading" class="saved-list">
          <p>正在加载已提交记录...</p>
        </div>
        <div v-else-if="saved.length" class="saved-list">
          <h3>本页已提交记录</h3>
          <p v-for="x in saved" :key="x.id"><b>{{x.title}}</b><span>{{x.createdAt || x.created_at || x.updatedAt || '—'}}</span></p>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.chain-page{min-height:100vh;background:#f6f8fb}.chain-hero{padding:34px;border-radius:22px;background:linear-gradient(120deg,#0f172a,#164e63 65%,#0f766e);color:#fff;box-shadow:0 18px 45px rgba(15,23,42,.18)}.chain-hero span{font-size:10px;letter-spacing:2px;color:#99f6e4;font-weight:900}.chain-hero h2{margin:8px 0;font-size:32px}.chain-hero p{margin:0;color:#dbeafe}.chain-card{margin-top:18px;padding:24px;border:1px solid #e2e8f0;border-radius:20px;background:#fff}.chain-card h3{margin-top:0}.chain-links{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.chain-links article,.placeholder{padding:18px;border:1px solid #e2e8f0;border-radius:16px;background:#f8fafc}.chain-links p,.placeholder p{color:#64748b;line-height:1.7}.form-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.chain-form label{display:block;color:#475569;font-size:12px;font-weight:800}.chain-form input,.chain-form textarea{width:100%;box-sizing:border-box;margin-top:6px;border:1px solid #cbd5e1;border-radius:10px;padding:10px;font:inherit}.submit{margin-top:16px;border:0;border-radius:12px;padding:12px 18px;background:#0f766e;color:#fff;font-weight:900;cursor:pointer}.saved-list{margin-top:18px;padding:14px;border-radius:14px;background:#f8fafc}.saved-list p{display:flex;justify-content:space-between;border-bottom:1px solid #e2e8f0;padding:9px 0;margin:0}.saved-list span{color:#64748b}@media(max-width:900px){.chain-links,.form-grid{grid-template-columns:1fr}}
</style>
