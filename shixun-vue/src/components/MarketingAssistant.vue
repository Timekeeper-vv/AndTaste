<script setup lang="ts">
import { computed, ref } from 'vue'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const loading = ref(false)
const result = ref<any>(null)

const form = ref({
  brandName: '之间味道',
  productName: '山城晚风明信片套装',
  productType: '明信片/装饰画/伴手礼',
  audience: '年轻游客、城市礼物购买者、文创爱好者',
  sellingPoints: '地域文化、原创插画、艺术纸、适合送礼、可收藏',
  scenario: '旅行纪念、节日赠礼、办公桌装饰、伴手礼',
  priceBand: '29-99元',
  channel: '小红书/抖音/朋友圈/商品详情页',
  tone: '温暖、有记忆点、有东方生活美学',
  useAi: true
})

const aiAvailable = computed(() => result.value?.aiEnabled)

async function generate() {
  loading.value = true
  result.value = null
  try {
    const res = await fetch('/api/creative/marketing/copy', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form.value)
    })
    if (!res.ok) throw new Error(await res.text())
    result.value = await res.json()
    emit('alert', aiAvailable.value ? 'AI营销文案已生成' : '本地营销文案已生成', 'success')
  } catch (e: any) {
    emit('alert', `生成文案失败：${e.message || e}`, 'error')
  } finally {
    loading.value = false
  }
}

function copyText(text: string) {
  navigator.clipboard?.writeText(text || '')
  emit('alert', '已复制到剪贴板', 'success')
}

function joinList(list?: string[]) {
  return Array.isArray(list) ? list.join('\n') : ''
}
</script>

<template>
  <div class="page marketing-page">
    <div class="page-header marketing-hero">
      <div>
        <p class="eyebrow">MARKETING DEPARTMENT · COPYWRITING</p>
        <h2 class="page-title">产品宣传文案</h2>
        <p class="page-desc">独立的产品宣传文案页面，可生成标题、卖点、详情页、小红书、抖音脚本和客服回复，适合电商上新与日常内容发布。</p>
      </div>
      <button class="btn btn-primary" :disabled="loading" @click="generate">{{ loading ? '生成中...' : '一键生成文案' }}</button>
    </div>

    <div class="marketing-grid">
      <section class="panel-card">
        <h3>产品信息</h3>
        <label>品牌名</label>
        <input v-model="form.brandName" />
        <label>产品名称</label>
        <input v-model="form.productName" />
        <label>产品类型</label>
        <input v-model="form.productType" placeholder="如：冰箱贴/徽章/帆布袋/明信片" />
        <label>目标人群</label>
        <textarea v-model="form.audience" rows="2" />
        <label>核心卖点</label>
        <textarea v-model="form.sellingPoints" rows="3" placeholder="用逗号分隔，比如：原创设计、亚克力材质、适合送礼" />
        <div class="form-row">
          <div><label>价格带</label><input v-model="form.priceBand" /></div>
          <div><label>投放渠道</label><input v-model="form.channel" /></div>
        </div>
        <label>使用/购买场景</label>
        <textarea v-model="form.scenario" rows="2" />
        <label>语气风格</label>
        <input v-model="form.tone" />
        <label class="check-row"><input v-model="form.useAi" type="checkbox" /> 调用硅基流动大模型增强（建议开启；会同时保留本地模板兜底）</label>
        <button class="btn btn-primary full" :disabled="loading" @click="generate">{{ loading ? '生成中...' : '生成营销文案' }}</button>
      </section>

      <section class="panel-card guide-card">
        <h3>第一步落地建议</h3>
        <div class="step"><b>1. 每个产品先填一遍</b><span>把产品名、卖点、场景、价格带统一起来。</span></div>
        <div class="step"><b>2. 选出可用标题</b><span>标题用于商品详情页、小红书、朋友圈和短视频封面。</span></div>
        <div class="step"><b>3. 人工审核再发布</b><span>涉及价格、承诺、IP风格和版权表达，一定要人工确认。</span></div>
        <div class="tip">如果配置了大模型 Key，系统会额外返回 AI 版本；没配置也能用本地模板先跑业务流程。</div>
      </section>
    </div>

    <section v-if="result" class="panel-card result-card">
      <div class="panel-head">
        <div>
          <h3>生成结果</h3>
          <p>来源：{{ result.source }} · {{ result.aiEnabled ? '已调用大模型' : '本地模板' }}</p>
        </div>
        <button class="btn btn-secondary btn-sm" @click="copyText([joinList(result.titles), joinList(result.sellingBullets), result.detailCopy, result.xiaohongshuNote, result.douyinScript, result.customerReply, result.aiDraft].filter(Boolean).join('\n\n---\n\n'))">复制全部</button>
      </div>

      <div class="result-grid">
        <div class="copy-block">
          <div class="copy-head"><h4>商品标题</h4><button @click="copyText(joinList(result.titles))">复制</button></div>
          <ol><li v-for="t in result.titles" :key="t">{{ t }}</li></ol>
        </div>
        <div class="copy-block">
          <div class="copy-head"><h4>卖点 bullet</h4><button @click="copyText(joinList(result.sellingBullets))">复制</button></div>
          <ul><li v-for="b in result.sellingBullets" :key="b">{{ b }}</li></ul>
        </div>
        <div class="copy-block wide">
          <div class="copy-head"><h4>详情页文案</h4><button @click="copyText(result.detailCopy)">复制</button></div>
          <p>{{ result.detailCopy }}</p>
        </div>
        <div class="copy-block">
          <div class="copy-head"><h4>小红书笔记</h4><button @click="copyText(result.xiaohongshuNote)">复制</button></div>
          <pre>{{ result.xiaohongshuNote }}</pre>
        </div>
        <div class="copy-block">
          <div class="copy-head"><h4>抖音脚本</h4><button @click="copyText(result.douyinScript)">复制</button></div>
          <pre>{{ result.douyinScript }}</pre>
        </div>
        <div class="copy-block wide">
          <div class="copy-head"><h4>客服回复</h4><button @click="copyText(result.customerReply)">复制</button></div>
          <p>{{ result.customerReply }}</p>
        </div>
        <div v-if="result.aiDraft" class="copy-block wide ai-block">
          <div class="copy-head"><h4>大模型增强版本</h4><button @click="copyText(result.aiDraft)">复制</button></div>
          <pre>{{ result.aiDraft }}</pre>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.marketing-page{background:#f8fafc;min-height:calc(100vh - var(--header-h))}.marketing-hero{padding:28px;border-radius:20px;color:#fff;background:radial-gradient(circle at 20% 20%,rgba(255,255,255,.2),transparent 24%),linear-gradient(135deg,#111827,#be123c 48%,#7c2d12);box-shadow:var(--shadow-md)}.marketing-hero .page-title{color:#fff;font-size:28px}.marketing-hero .page-desc{color:rgba(255,255,255,.82)}.eyebrow{margin:0 0 8px;font-size:12px;letter-spacing:2px;color:#fecdd3;font-weight:800}.marketing-grid{display:grid;grid-template-columns:1.2fr .8fr;gap:18px;margin-bottom:18px}.panel-card{background:#fff;border:1px solid var(--c-border);border-radius:18px;padding:20px;box-shadow:var(--shadow-sm)}.panel-card h3{margin:0 0 16px}.panel-card label{display:block;font-size:12px;font-weight:700;color:var(--c-text-2);margin:10px 0 6px}.check-row{display:flex!important;align-items:center;gap:8px;margin-top:12px!important;color:#334155!important}.check-row input{width:auto;height:auto}input,textarea{width:100%;border:1px solid var(--c-border);border-radius:10px;padding:10px;background:#fff;font-family:inherit}input{height:38px}.form-row{display:grid;grid-template-columns:1fr 1fr;gap:10px}.full{width:100%;margin-top:16px}.guide-card{display:flex;flex-direction:column;gap:12px}.step{padding:14px;border:1px solid #ffe4e6;background:#fff1f2;border-radius:14px}.step b{display:block;color:#9f1239;margin-bottom:5px}.step span,.tip{color:#64748b;font-size:13px;line-height:1.7}.tip{padding:14px;border-radius:14px;background:#f8fafc;border:1px dashed #cbd5e1}.panel-head{display:flex;justify-content:space-between;gap:12px;align-items:flex-start;margin-bottom:16px}.panel-head h3{margin:0 0 4px}.panel-head p{margin:0;color:var(--c-text-2);font-size:13px}.result-grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.copy-block{border:1px solid #eef2f7;border-radius:14px;padding:14px;background:#fff}.copy-block.wide{grid-column:1/-1}.copy-head{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:10px}.copy-head h4{margin:0}.copy-head button{border:0;background:#f1f5f9;color:#334155;border-radius:999px;padding:5px 10px;cursor:pointer}.copy-block li{margin-bottom:7px}.copy-block p{line-height:1.8;margin:0;color:#334155}.copy-block pre{white-space:pre-wrap;word-break:break-word;line-height:1.7;margin:0;color:#334155;font-family:inherit}.ai-block{border-color:#c4b5fd;background:#faf5ff}@media(max-width:1000px){.marketing-grid,.result-grid{grid-template-columns:1fr}.form-row{grid-template-columns:1fr}}
</style>
