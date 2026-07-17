<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'

type Supplier = {
  id?: number
  receiverNo: string
  supplier: string
  accountType: string
  accountName: string
  bankAccount: string
  bank: string
  branch: string
  location: string
  note?: string
}

const keyword = ref('')
const bankFilter = ref('全部银行')
const locationFilter = ref('全部地区')
const onlyWarning = ref(false)

const suppliers = ref<Supplier[]>([])
const loading = ref(false)
const showAddForm = ref(false)
const saveLoading = ref(false)
const addForm = ref<Supplier>({
  receiverNo: '',
  supplier: '',
  accountType: '对公账户',
  accountName: '',
  bankAccount: '',
  bank: '',
  branch: '',
  location: '',
  note: ''
})

async function loadSuppliers() {
  loading.value = true
  try {
    const res = await fetch('/api/suppliers')
    const data = await res.json()
    suppliers.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('加载供应商失败', e)
    suppliers.value = []
  } finally {
    loading.value = false
  }
}

function openAddForm() {
  addForm.value = {
    receiverNo: '',
    supplier: '',
    accountType: '对公账户',
    accountName: '',
    bankAccount: '',
    bank: '',
    branch: '',
    location: '',
    note: ''
  }
  showAddForm.value = true
}

async function submitSupplier() {
  if (!addForm.value.supplier.trim() || !addForm.value.accountName.trim() || !addForm.value.bankAccount.trim() || !addForm.value.bank.trim()) {
    alert('供应商、户名、银行账号、银行为必填项')
    return
  }
  saveLoading.value = true
  try {
    const res = await fetch('/api/suppliers', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(addForm.value)
    })
    const data = await res.json()
    if (!res.ok) throw new Error(data.error || '保存失败')
    showAddForm.value = false
    await loadSuppliers()
  } catch (e: any) {
    alert(e?.message || '保存失败')
  } finally {
    saveLoading.value = false
  }
}

async function deleteSupplier(item: Supplier) {
  if (!item.id) return
  if (!confirm(`确认删除供应商：${item.supplier}？`)) return
  await fetch(`/api/suppliers/${item.id}`, { method: 'DELETE' })
  await loadSuppliers()
}

onMounted(loadSuppliers)

function bankGroup(bank: string) {
  if (bank.includes('工商')) return '中国工商银行'
  if (bank.includes('建设')) return '中国建设银行'
  if (bank.includes('招商')) return '招商银行'
  if (bank.includes('农业')) return '中国农业银行'
  if (bank.includes('中国银行')) return '中国银行'
  if (bank.includes('中信')) return '中信银行'
  if (bank.includes('民生')) return '民生银行'
  if (bank.includes('交通')) return '交通银行'
  if (bank.includes('民泰')) return '浙江民泰商业银行'
  if (bank.includes('威海')) return '威海银行'
  return bank
}

function region(location: string) {
  return location.replace('省','').replace('市','').replace('自治区','').slice(0, 2)
}

const bankOptions = computed(() => ['全部银行', ...Array.from(new Set(suppliers.value.map(x => bankGroup(x.bank))))])
const locationOptions = computed(() => ['全部地区', ...Array.from(new Set(suppliers.value.map(x => region(x.location))))])

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return suppliers.value.filter(x => {
    const haystack = [x.receiverNo,x.supplier,x.accountType,x.accountName,x.bankAccount,x.bank,x.branch,x.location,x.note || '',bankGroup(x.bank)].join(' ').toLowerCase()
    return (!kw || haystack.includes(kw))
      && (bankFilter.value === '全部银行' || bankGroup(x.bank) === bankFilter.value)
      && (locationFilter.value === '全部地区' || region(x.location) === locationFilter.value)
      && (!onlyWarning.value || !!x.note)
  })
})

const bankStats = computed(() => {
  const map = new Map<string, number>()
  suppliers.value.forEach(x => map.set(bankGroup(x.bank), (map.get(bankGroup(x.bank)) || 0) + 1))
  return Array.from(map.entries()).sort((a,b) => b[1] - a[1]).slice(0, 5)
})
const warningCount = computed(() => suppliers.value.filter(x => x.note).length)

function maskedAccount(account: string) {
  if (account.length <= 8) return account
  return account.slice(0, 4) + ' **** **** ' + account.slice(-4)
}

function copyText(text: string) {
  navigator.clipboard?.writeText(text)
}

function exportCsv() {
  const headers = ['收方编号','供应商','账户类型','供应商户名','银行账号','银行','开户行','开户行所在地','备注']
  const rows = filtered.value.map(x => [x.receiverNo,x.supplier,x.accountType,x.accountName,x.bankAccount,x.bank,x.branch,x.location,x.note || ''])
  const csv = [headers, ...rows].map(row => row.map(v => `"${String(v).replace(/"/g, '""')}"`).join(',')).join('\n')
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = '供应商银行账户列表.csv'
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="supplier-page">
    <section class="supplier-hero">
      <div>
        <span>SUPPLIER BANK ACCOUNT DIRECTORY</span>
        <h2>供应商列表</h2>
        <p>集中维护供应商对公账户、开户行、所在地和核验备注；支持收方编号、供应商、银行、账号、地区等模糊查询。</p>
      </div>
      <div class="hero-actions">
        <button class="add-btn" @click="openAddForm">新增供应商</button>
        <button class="export-btn" @click="exportCsv">导出当前结果 CSV</button>
      </div>
    </section>

    <section class="stat-grid">
      <article><small>供应商总数</small><b>{{ suppliers.length }}</b><em>对公账户</em></article>
      <article><small>当前结果</small><b>{{ filtered.length }}</b><em>已筛选</em></article>
      <article><small>需核验</small><b>{{ warningCount }}</b><em>异常标注</em></article>
      <article><small>覆盖地区</small><b>{{ locationOptions.length - 1 }}</b><em>省市区域</em></article>
    </section>

    <section class="toolbar-card">
      <div class="search-box">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
        <input v-model="keyword" placeholder="模糊查询：收方编号 / 供应商 / 户名 / 银行账号 / 开户行 / 地区" />
      </div>
      <select v-model="bankFilter"><option v-for="b in bankOptions" :key="b">{{ b }}</option></select>
      <select v-model="locationFilter"><option v-for="l in locationOptions" :key="l">{{ l }}</option></select>
      <label class="warning-toggle"><input type="checkbox" v-model="onlyWarning" /> 只看需核验</label>
    </section>



    <section v-if="showAddForm" class="add-panel">
      <div class="add-panel-head">
        <div>
          <h3>新增供应商账户</h3>
          <p>保存后会进入数据库，右下角 AI 助手可立即实时查询。</p>
        </div>
        <button class="close-btn" @click="showAddForm = false">关闭</button>
      </div>
      <div class="form-grid">
        <label><span>收方编号</span><input v-model="addForm.receiverNo" placeholder="不填自动生成" /></label>
        <label><span>供应商 *</span><input v-model="addForm.supplier" placeholder="例如 tst" /></label>
        <label><span>账户类型</span><input v-model="addForm.accountType" /></label>
        <label><span>供应商户名 *</span><input v-model="addForm.accountName" placeholder="对公户名" /></label>
        <label><span>银行账号 *</span><input v-model="addForm.bankAccount" placeholder="自动去除空格" /></label>
        <label><span>银行 *</span><input v-model="addForm.bank" placeholder="例如 招商银行" /></label>
        <label><span>开户行</span><input v-model="addForm.branch" /></label>
        <label><span>开户行所在地</span><input v-model="addForm.location" /></label>
        <label class="full"><span>备注</span><textarea v-model="addForm.note" rows="2" placeholder="异常核验提示，可不填" /></label>
      </div>
      <div class="form-actions">
        <button class="cancel-btn" @click="showAddForm = false">取消</button>
        <button class="save-btn" @click="submitSupplier" :disabled="saveLoading">{{ saveLoading ? '保存中...' : '保存到数据库' }}</button>
      </div>
    </section>
    <section class="bank-cloud">
      <button v-for="[name,count] in bankStats" :key="name" @click="bankFilter = name" :class="{active: bankFilter === name}">
        {{ name }} <b>{{ count }}</b>
      </button>
    </section>

    <section class="table-card supplier-table-card">
      <div class="table-wrap">
        <table class="supplier-table">
          <thead>
            <tr>
              <th>收方编号</th>
              <th>供应商 / 户名</th>
              <th>银行账号</th>
              <th>银行</th>
              <th>开户行</th>
              <th>所在地</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in filtered" :key="item.receiverNo" :class="{warn: item.note}">
              <td><code>{{ item.receiverNo }}</code></td>
              <td class="supplier-cell"><b>{{ item.supplier }}</b><span>{{ item.accountType }} · {{ item.accountName }}</span></td>
              <td class="account-cell"><strong>{{ maskedAccount(item.bankAccount) }}</strong><small>{{ item.bankAccount }}</small></td>
              <td><span class="bank-pill">{{ bankGroup(item.bank) }}</span><small>{{ item.bank }}</small></td>
              <td class="branch-cell">{{ item.branch }}</td>
              <td>{{ item.location }}</td>
              <td><span class="status" :class="item.note ? 'need-check' : 'ok'">{{ item.note ? '需核验' : '正常' }}</span></td>
              <td class="ops"><button @click="copyText(item.bankAccount)">复制账号</button><button @click="copyText([item.accountName,item.bankAccount,item.branch].join('\n'))">复制付款信息</button><button class="danger" @click="deleteSupplier(item)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="loading" class="empty">正在从数据库加载供应商账户...</div>
      <div v-else-if="!filtered.length" class="empty">没有匹配的供应商账户，请调整关键词或筛选条件。</div>
    </section>

    <section v-if="filtered.some(x => x.note)" class="warning-panel">
      <h3>核验提醒</h3>
      <p v-for="x in filtered.filter(x => x.note)" :key="x.receiverNo"><b>{{ x.supplier }}</b>：{{ x.note }}</p>
    </section>
  </div>
</template>

<style scoped>
.supplier-page{min-height:100vh;background:#f6f8fb}.supplier-hero{display:flex;justify-content:space-between;gap:18px;align-items:flex-start;padding:34px;border-radius:24px;background:radial-gradient(circle at 12% 10%,rgba(255,255,255,.22),transparent 25%),linear-gradient(120deg,#0f172a,#155e75 58%,#0f766e);color:#fff;box-shadow:0 18px 45px rgba(15,23,42,.18)}.supplier-hero span{font-size:10px;letter-spacing:2px;color:#99f6e4;font-weight:900}.supplier-hero h2{margin:8px 0;font-size:32px}.supplier-hero p{max-width:760px;margin:0;color:#dbeafe}.export-btn{border:1px solid rgba(255,255,255,.35);background:rgba(255,255,255,.12);color:#fff;border-radius:12px;padding:11px 16px;font-weight:900;cursor:pointer}.stat-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-top:18px}.stat-grid article{padding:18px;border:1px solid #e2e8f0;border-radius:18px;background:#fff;box-shadow:0 1px 2px rgba(15,23,42,.04)}.stat-grid small{display:block;color:#64748b;font-weight:800}.stat-grid b{display:block;margin-top:6px;font-size:30px}.stat-grid em{font-style:normal;color:#0f766e;font-size:12px;font-weight:800}.toolbar-card{display:grid;grid-template-columns:minmax(260px,1fr) 180px 140px auto;gap:12px;margin-top:18px;padding:16px;border:1px solid #e2e8f0;border-radius:18px;background:#fff}.search-box{position:relative}.search-box svg{position:absolute;left:12px;top:50%;transform:translateY(-50%);color:#94a3b8}.search-box input,.toolbar-card select{width:100%;height:42px;border:1px solid #cbd5e1;border-radius:12px;padding:0 12px;font:inherit;background:#fff}.search-box input{padding-left:38px}.warning-toggle{display:flex;align-items:center;gap:8px;color:#475569;font-weight:800;white-space:nowrap}.warning-toggle input{width:auto}.bank-cloud{display:flex;gap:10px;flex-wrap:wrap;margin:14px 0}.bank-cloud button{border:1px solid #ccfbf1;background:#f0fdfa;color:#115e59;border-radius:999px;padding:8px 12px;cursor:pointer}.bank-cloud button.active{background:#0f766e;color:#fff}.bank-cloud b{margin-left:5px}.supplier-table-card{border-radius:20px;box-shadow:0 1px 2px rgba(15,23,42,.05)}.supplier-table{width:100%;min-width:1250px;border-collapse:separate;border-spacing:0}.supplier-table th{position:sticky;top:0;background:#f8fafc;color:#475569;text-align:left;font-size:12px;padding:14px;border-bottom:1px solid #e2e8f0;z-index:1}.supplier-table td{padding:14px;border-bottom:1px solid #edf2f7;vertical-align:middle}.supplier-table tr:hover{background:#fbfdff}.supplier-table tr.warn{background:#fffdf7}.supplier-table code{padding:5px 8px;border-radius:8px;background:#eef2ff;color:#3730a3;font-weight:900}.supplier-cell b{display:block;color:#0f172a}.supplier-cell span,.account-cell small,td small{display:block;margin-top:4px;color:#64748b;font-size:12px}.account-cell strong{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;color:#0f172a}.bank-pill{display:inline-block;padding:5px 9px;border-radius:999px;background:#ecfeff;color:#0e7490;font-size:12px;font-weight:900}.branch-cell{max-width:270px;line-height:1.55}.status{display:inline-block;border-radius:999px;padding:5px 10px;font-size:12px;font-weight:900}.status.ok{background:#dcfce7;color:#15803d}.status.need-check{background:#fef3c7;color:#b45309}.ops{display:flex;gap:8px;white-space:nowrap}.ops button{border:0;border-radius:9px;background:#f1f5f9;color:#334155;padding:8px 10px;cursor:pointer;font-weight:800}.ops button:hover{background:#0f766e;color:#fff}.empty{padding:32px;text-align:center;color:#94a3b8}.warning-panel{margin-top:18px;padding:18px;border-radius:18px;border:1px solid #fdba74;background:#fff7ed}.warning-panel h3{margin:0 0 10px;color:#9a3412}.warning-panel p{margin:8px 0;color:#7c2d12}.hero-actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap}.add-btn{border:0;background:#fff;color:#0f766e;border-radius:12px;padding:11px 16px;font-weight:900;cursor:pointer;box-shadow:0 8px 18px rgba(15,23,42,.16)}.add-panel{margin-top:18px;padding:18px;border:1px solid #bae6fd;border-radius:20px;background:linear-gradient(180deg,#f0f9ff,#fff);box-shadow:0 1px 2px rgba(15,23,42,.05)}.add-panel-head{display:flex;justify-content:space-between;gap:16px;align-items:flex-start;margin-bottom:14px}.add-panel h3{margin:0;color:#0f172a}.add-panel p{margin:4px 0 0;color:#64748b}.close-btn{border:1px solid #cbd5e1;background:#fff;border-radius:10px;padding:8px 12px;cursor:pointer}.form-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.form-grid label{display:flex;flex-direction:column;gap:6px;color:#475569;font-size:12px;font-weight:900}.form-grid input,.form-grid textarea{border:1px solid #cbd5e1;border-radius:11px;padding:10px 12px;font:inherit;background:#fff}.form-grid .full{grid-column:1/-1}.form-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:14px}.cancel-btn,.save-btn{border:0;border-radius:11px;padding:10px 16px;font-weight:900;cursor:pointer}.cancel-btn{background:#f1f5f9;color:#334155}.save-btn{background:#0f766e;color:#fff}.save-btn:disabled{opacity:.6;cursor:not-allowed}.ops button.danger{background:#fef2f2;color:#b91c1c}.ops button.danger:hover{background:#dc2626;color:#fff}@media(max-width:1100px){.supplier-hero{display:block}.export-btn{margin-top:16px}.stat-grid{grid-template-columns:repeat(2,1fr)}.toolbar-card{grid-template-columns:1fr}}@media(max-width:700px){.stat-grid{grid-template-columns:1fr}.form-grid{grid-template-columns:1fr}}
</style>
