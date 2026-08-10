<template>
  <view class="page">
    <view class="ink ink-one" />
    <view class="ink ink-two" />

    <view class="hero">
      <view class="hero-copy">
        <text class="eyebrow">CREATIVE INTELLIGENCE</text>
        <text class="title">AI 深度评审</text>
        <text class="subtitle">用设计、市场、成本与消费者四个真实视角，帮你把作品推进到下一步。</text>
      </view>
      <text class="seal">审</text>
    </view>
    <AiGeneratedNotice class="ai-disclosure" description="本页评审结论、评分、建议和路线图均为人工智能生成内容，仅供创作决策参考，不替代人工审核、版权判断、正式报价、质检或生产工艺确认。" />

    <view class="asset-card">
      <text class="asset-label">正在评审的作品</text>
      <text class="asset-title">{{ assetTitle }}</text>
      <text class="asset-no">作品编号 #{{ assetId }}</text>
    </view>

    <view v-if="loading" class="loading-card"><view class="seal-loader">印</view><text>正在读取你的专属评审报告…</text></view>

    <template v-else>
      <view v-if="latestReview" class="report">
        <view class="report-head">
          <view>
            <text class="section-kicker">LATEST REPORT</text>
            <text class="section-title">{{ latestReview.reviewNo || '创作评审报告' }}</text>
          </view>
          <view class="recommendation" :class="recommendationClass(latestReview.recommendation)">
            <text>{{ recommendationLabel(latestReview.recommendation) }}</text>
          </view>
        </view>

        <view class="score-row">
          <view class="score-ring"><text>{{ displayScore(latestReview.overallScore) }}</text><text>综合分</text></view>
          <view class="score-copy"><text>{{ scoreHeadline(latestReview.overallScore) }}</text><text>{{ latestReview.summary || '四位专业角色已从不同角度完成本次作品评估。' }}</text><text v-if="latestReview.createdAt" class="date">评审于 {{ formatDate(latestReview.createdAt) }}</text></view>
        </view>

        <view class="section-head"><text>四方视角</text><text>4 PERSPECTIVES</text></view>
        <view class="agent-grid">
          <view v-for="agent in latestReview.agents || []" :key="agent.agentKey" class="agent-card">
            <view class="agent-top"><text>{{ agentIcon(agent.agentKey) }}</text><view><text>{{ agent.agentName }}</text><text>{{ agent.verdict || '专业意见' }}</text></view><text>{{ displayScore(agent.score) }}</text></view>
            <view class="score-track"><view :style="{ width: `${Math.max(0, Math.min(100, Number(agent.score) || 0))}%` }" /></view>
            <text class="agent-comment">{{ agent.comments || '已完成本维度评估。' }}</text>
            <view v-if="suggestionList(agent).length" class="suggestion-list"><text v-for="suggestion in suggestionList(agent).slice(0, 2)" :key="suggestion">{{ suggestion }}</text></view>
          </view>
        </view>

        <view v-if="roadmapLines(latestReview.roadmap).length" class="roadmap">
          <view class="section-head"><text>下一步升级路径</text><text>ROADMAP</text></view>
          <view v-for="(line, index) in roadmapLines(latestReview.roadmap).slice(0, 4)" :key="`${index}-${line}`" class="roadmap-line"><text>{{ index + 1 }}</text><text>{{ line }}</text></view>
        </view>
      </view>

      <view v-else class="empty-report">
        <text class="empty-mark">鉴</text>
        <text>还没有深度评审报告</text>
        <text>先让四位专业角色读懂你的作品，再决定是继续打样、调整卖点，还是优化量产细节。</text>
      </view>

      <view class="context-card">
        <view class="context-head"><text>补充评审目标（可选）</text><text>会帮助报告更贴近你的场景</text></view>
        <textarea v-model="reviewContext" maxlength="240" class="context-input" placeholder="例如：面向亲子客群的博物馆礼物，预算 59 元，希望评估爆款潜力…" />
        <text class="context-count">{{ reviewContext.length }}/240</text>
      </view>

      <button class="primary" :loading="submitting" :disabled="submitting || !assetId" @tap="confirmCreateReview">
        <text>{{ latestReview ? '重新生成专业评审' : '开始专业评审' }}</text><text>✦</text>
      </button>
      <text class="notice">AI 深度评审用于辅助创作决策，不替代正式审核、报价、质检或生产工艺确认。</text>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import AiGeneratedNotice from '../../components/AiGeneratedNotice.vue'
import { createDesignReview, getDesignReviews, type DesignReviewAgent, type DesignReviewRecommendation, type DesignReviewReport } from '../../api/creative'
import { requireSession } from '../../utils/session'

const assetId = ref('')
const assetTitle = ref('作品')
const loading = ref(true)
const submitting = ref(false)
const reviewContext = ref('')
const reports = ref<DesignReviewReport[]>([])
const latestReview = computed(() => reports.value[0] || null)

function displayScore(score?: number) {
  const value = Number(score)
  return Number.isFinite(value) ? String(Math.round(value)) : '—'
}

function recommendationLabel(value?: DesignReviewRecommendation) {
  if (value === 'go') return '建议推进'
  if (value === 'adjust') return '优化后推进'
  if (value === 'reject') return '建议重构'
  return '专业评审'
}

function recommendationClass(value?: DesignReviewRecommendation) {
  if (value === 'go') return 'go'
  if (value === 'adjust') return 'adjust'
  if (value === 'reject') return 'reject'
  return ''
}

function scoreHeadline(score?: number) {
  const value = Number(score) || 0
  if (value >= 85) return '这件作品已经具备明确的推进价值。'
  if (value >= 70) return '方向成立，再打磨几个关键细节。'
  return '先完成一次针对性调整，会更值得进入下一步。'
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value.replace('T', ' ').slice(0, 16)
  return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`
}

function agentIcon(key?: string) {
  return ({ senior_designer: '设', market_analyst: '市', cost_controller: '造', target_consumer: '客' } as Record<string, string>)[String(key || '')] || '评'
}

function suggestionList(agent: DesignReviewAgent) {
  const source = agent.suggestions ?? agent.suggestionsJson
  if (Array.isArray(source)) return source.map(item => String(item).trim()).filter(Boolean)
  if (!source) return []
  try {
    const parsed = JSON.parse(String(source))
    if (Array.isArray(parsed)) return parsed.map(item => String(item).trim()).filter(Boolean)
  } catch { /* plain text is supported below */ }
  return String(source).split(/[\n；;]+/).map(item => item.trim()).filter(Boolean)
}

function roadmapLines(value: unknown) {
  if (Array.isArray(value)) return value.map(item => typeof item === 'string' ? item : JSON.stringify(item)).filter(Boolean)
  if (value && typeof value === 'object') return Object.values(value as Record<string, unknown>).flatMap(item => Array.isArray(item) ? item : [item]).map(item => typeof item === 'string' ? item : JSON.stringify(item)).filter(Boolean)
  return value ? [String(value)] : []
}

async function loadReports() {
  if (!assetId.value) { loading.value = false; return }
  loading.value = true
  try {
    const response = await getDesignReviews(assetId.value)
    reports.value = Array.isArray(response) ? response : []
  } catch (error: any) {
    uni.showToast({ title: error?.message || '评审报告加载失败', icon: 'none' })
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function confirmCreateReview() {
  if (!assetId.value || submitting.value) return
  uni.showModal({
    title: latestReview.value ? '重新生成评审？' : '开始专业评审？',
    content: latestReview.value ? '会生成一份新的四角色评审报告，旧报告仍会保留在本作品历史中。' : '系统将从设计、市场、成本和消费者视角生成本作品的专业评审报告。',
    confirmText: '开始评审',
    success: result => { if (result.confirm) void createReview() },
  })
}

async function createReview() {
  submitting.value = true
  try {
    const report = await createDesignReview({ assetId: assetId.value, context: reviewContext.value.trim() || undefined })
    reports.value = [report, ...reports.value.filter(item => String(item.reviewId || item.id || '') !== String(report.reviewId || report.id || ''))]
    uni.showToast({ title: '评审报告已生成', icon: 'success' })
  } catch (error: any) {
    uni.showToast({ title: error?.message || '生成评审失败，请稍后重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad((query) => {
  assetId.value = String(query?.assetId || '')
  // uni-app 已经将页面参数解码；再次 decode 会在作品名包含孤立 % 时抛异常。
  assetTitle.value = String(query?.title || '作品')
  if (requireSession()) void loadReports()
})

onPullDownRefresh(() => { if (requireSession()) void loadReports(); else uni.stopPullDownRefresh() })
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;box-sizing:border-box;overflow:hidden;padding:42rpx 30rpx calc(60rpx + env(safe-area-inset-bottom));background:linear-gradient(155deg,#fbfaf7 0%,#f1eee8 52%,#edf3ee 100%)}.ink{position:absolute;pointer-events:none;border-radius:50%;filter:blur(1rpx)}.ink-one{top:-150rpx;right:-155rpx;width:460rpx;height:420rpx;background:radial-gradient(ellipse,rgba(111,146,126,.19),transparent 66%)}.ink-two{left:-180rpx;bottom:160rpx;width:440rpx;height:320rpx;background:radial-gradient(ellipse,rgba(182,103,78,.11),transparent 68%)}.hero,.asset-card,.report,.loading-card,.empty-report,.context-card,.primary,.notice{position:relative;z-index:1}.hero{display:flex;align-items:flex-start;justify-content:space-between;gap:25rpx;padding:16rpx 6rpx 34rpx}.hero-copy{display:flex;flex:1;flex-direction:column}.eyebrow,.section-kicker{color:#82988d;font-size:18rpx;font-weight:900;letter-spacing:2rpx}.title{margin-top:8rpx;color:#30362f;font-family:"Songti SC","STSong",serif;font-size:50rpx;font-weight:800;line-height:1.15}.subtitle{max-width:560rpx;margin-top:15rpx;color:#7d8178;font-size:22rpx;line-height:1.65}.seal{display:grid;place-items:center;flex:none;width:72rpx;height:72rpx;border:2rpx solid rgba(157,79,59,.72);border-radius:12rpx;color:#9e543f;background:rgba(255,249,241,.67);font-family:"Songti SC","STSong",serif;font-size:37rpx;font-weight:800;transform:rotate(-7deg)}.asset-card{display:flex;flex-direction:column;padding:24rpx 26rpx;border:1rpx solid rgba(106,118,97,.15);border-radius:24rpx;background:rgba(255,254,250,.84);box-shadow:0 14rpx 30rpx rgba(55,54,43,.055)}.asset-label{color:#739080;font-size:18rpx;font-weight:850;letter-spacing:1.4rpx}.asset-title{overflow:hidden;margin-top:10rpx;color:#3c4039;font-family:"Songti SC","STSong",serif;font-size:31rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.asset-no{margin-top:8rpx;color:#9a9187;font-size:19rpx}.loading-card,.empty-report{display:grid;justify-items:center;gap:15rpx;margin-top:24rpx;padding:62rpx 34rpx;border:1rpx solid rgba(107,119,100,.13);border-radius:25rpx;background:rgba(255,254,250,.79);color:#7c8179;text-align:center;font-size:23rpx;line-height:1.7}.seal-loader{display:grid;place-items:center;width:62rpx;height:62rpx;border:2rpx solid #829b8c;border-radius:11rpx;color:#638071;font-family:"Songti SC","STSong",serif;font-size:29rpx;animation:stamp 1.4s ease-in-out infinite}.empty-report{color:#858178}.empty-report .empty-mark{display:grid;place-items:center;width:86rpx;height:86rpx;border-radius:50%;color:#9f6653;background:#f1e5da;font-family:"Songti SC","STSong",serif;font-size:45rpx}.empty-report text:last-child{max-width:530rpx;font-size:21rpx}.report{margin-top:24rpx;padding:26rpx;border:1rpx solid rgba(106,118,97,.15);border-radius:25rpx;background:rgba(255,254,250,.9);box-shadow:0 14rpx 30rpx rgba(55,54,43,.055)}.report-head,.section-head{display:flex;align-items:center;justify-content:space-between;gap:15rpx}.section-title{display:block;margin-top:7rpx;color:#41463e;font-family:"Songti SC","STSong",serif;font-size:28rpx;font-weight:800}.recommendation{padding:9rpx 13rpx;border-radius:99rpx;color:#617f6d;background:#e9f2eb;font-size:19rpx;font-weight:850;white-space:nowrap}.recommendation.adjust{color:#9b7542;background:#f5eedc}.recommendation.reject{color:#a75b4b;background:#f7e5df}.score-row{display:flex;align-items:center;gap:21rpx;margin-top:25rpx;padding:20rpx 0 25rpx;border-bottom:1rpx solid #eee8de}.score-ring{display:grid;place-content:center;flex:none;width:122rpx;height:122rpx;border:10rpx solid #dce9df;border-radius:50%;box-sizing:border-box;background:radial-gradient(circle at 50% 35%,#fff,#f4f3ed);text-align:center}.score-ring text:first-child{color:#456b5a;font-family:"Songti SC","STSong",serif;font-size:43rpx;font-weight:900;line-height:1}.score-ring text:last-child{margin-top:4rpx;color:#8a9288;font-size:17rpx}.score-copy{display:flex;min-width:0;flex:1;flex-direction:column}.score-copy text:first-child{color:#41483e;font-size:25rpx;font-weight:850;line-height:1.5}.score-copy text:nth-child(2){margin-top:8rpx;color:#767b73;font-size:20rpx;line-height:1.65}.date{margin-top:9rpx;color:#9c968d;font-size:17rpx}.section-head{margin-top:25rpx}.section-head text:first-child{color:#494e46;font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:800}.section-head text:last-child{color:#9aa99e;font-size:16rpx;font-weight:850;letter-spacing:1.5rpx}.agent-grid{display:grid;grid-template-columns:1fr 1fr;gap:12rpx;margin-top:15rpx}.agent-card{padding:17rpx;border:1rpx solid #e8e3da;border-radius:17rpx;background:linear-gradient(145deg,#fffefa,#f8f6f0)}.agent-top{display:grid;grid-template-columns:39rpx 1fr auto;align-items:center;gap:8rpx}.agent-top>text:first-child{display:grid;place-items:center;width:38rpx;height:38rpx;border-radius:12rpx;color:#577966;background:#e4eee5;font-family:"Songti SC","STSong",serif;font-size:21rpx;font-weight:800}.agent-top view{display:flex;min-width:0;flex-direction:column}.agent-top view text:first-child{overflow:hidden;color:#4b4e48;font-size:20rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.agent-top view text:last-child{overflow:hidden;margin-top:3rpx;color:#a19a90;font-size:15rpx;text-overflow:ellipsis;white-space:nowrap}.agent-top>text:last-child{color:#577866;font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:900}.score-track{height:7rpx;margin-top:14rpx;overflow:hidden;border-radius:8rpx;background:#e9e5dd}.score-track view{height:100%;border-radius:inherit;background:linear-gradient(90deg,#a46d57,#6f8b7c)}.agent-comment{display:-webkit-box;overflow:hidden;margin-top:11rpx;color:#77786f;font-size:18rpx;line-height:1.55;-webkit-box-orient:vertical;-webkit-line-clamp:3}.suggestion-list{display:grid;gap:5rpx;margin-top:10rpx}.suggestion-list text{color:#668276;font-size:16rpx;line-height:1.45}.suggestion-list text::before{margin-right:5rpx;content:'·';color:#b06d55}.roadmap{margin-top:22rpx;padding-top:2rpx}.roadmap-line{display:flex;gap:12rpx;align-items:flex-start;margin-top:13rpx;color:#69706a;font-size:20rpx;line-height:1.55}.roadmap-line text:first-child{display:grid;place-items:center;flex:none;width:29rpx;height:29rpx;border-radius:9rpx;color:#fff;background:#7c9887;font-size:16rpx;font-weight:850}.context-card{margin-top:24rpx;padding:21rpx;border:1rpx solid rgba(112,121,104,.15);border-radius:21rpx;background:rgba(255,254,250,.8)}.context-head{display:flex;align-items:baseline;justify-content:space-between;gap:10rpx}.context-head text:first-child{color:#4c514a;font-size:23rpx;font-weight:850}.context-head text:last-child{color:#9b9288;font-size:16rpx;text-align:right}.context-input{display:block;box-sizing:border-box;width:100%;height:118rpx;margin-top:15rpx;padding:14rpx;border:1rpx solid #e6ded3;border-radius:14rpx;color:#4b4e48;background:#fcfbf7;font-size:20rpx;line-height:1.55}.context-count{display:block;margin-top:7rpx;color:#a0968a;font-size:16rpx;text-align:right}.primary{display:flex;align-items:center;justify-content:center;gap:12rpx;width:100%;height:92rpx;margin-top:17rpx;border-radius:19rpx;color:#fff;background:linear-gradient(135deg,#3e3933,#627f71);font-size:26rpx;font-weight:850}.primary text:last-child{color:#e5c09c}.notice{display:block;margin:14rpx 14rpx 0;color:#948d84;font-size:17rpx;line-height:1.55;text-align:center}@keyframes stamp{0%,100%{transform:scale(1) rotate(-4deg);opacity:.8}50%{transform:scale(.88) rotate(3deg);opacity:1}}
</style>
