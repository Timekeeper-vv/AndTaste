<template>
  <view class="page">
    <view class="hero"><text class="eyebrow">POINTS ACTIVITIES</text><text class="title">积分活动</text><text class="balance">当前积分 {{ balance }}</text></view>
    <view class="tabs"><text v-for="item in tabs" :key="item.key" :class="['tab', activeTab === item.key && 'selected']" @tap="activeTab = item.key">{{ item.label }}</text></view>
    <view v-if="activeTab === 'checkin'" class="section">
      <view class="section-head"><text>每日签到</text><text>连续第 {{ checkin?.streakDays || 0 }} 天</text></view>
      <text class="description">每日签到 +240 积分，连续满 7 天额外奖励 500 积分。</text>
      <view class="week-grid"><view v-for="day in 7" :key="day" :class="['day', day <= streakWeekProgress && 'done']"><text>第{{ day }}天</text><text>{{ day === 7 ? '+740' : '+240' }}</text></view></view>
      <button class="primary" :disabled="checkin?.checkedIn || busy" @tap="doCheckin">{{ checkin?.checkedIn ? '今日已签到' : '签到领取 240 积分' }}</button>
    </view>
    <view v-else-if="activeTab === 'growth'" class="section">
      <view class="section-head"><text>使用时长</text><text>{{ usage?.minutes || 0 }} 分钟 · 今日 +{{ usage?.points || 0 }} 分</text></view>
      <text class="description">创作、提交、生产、渠道页面每分钟 +{{ usage?.pointsPerMinute || 5 }} 积分，每日上限 {{ usage?.dailyPointCap || 1000 }} 分；离开 3 分钟视为中断。</text>
      <view class="growth-bar"><view class="growth-fill" :style="{ width: usageProgress + '%' }" /></view>
      <view class="section-head sub-head"><text>分享朋友圈</text><text>每日一次 +{{ share?.points || 80 }} 分</text></view>
      <text class="description">点击右上角“…”分享到朋友圈，然后回到本页领取积分。</text>
      <button class="primary" :disabled="busy || share?.claimedToday" @tap="claimShare">{{ share?.claimedToday ? '今日分享奖励已领取' : `我已分享，领取 ${share?.points || 80} 积分` }}</button>
      <view class="section-head sub-head"><text>邀请好友</text><text>好友注册双方都有积分</text></view>
      <text class="description">好友注册得 {{ invite?.inviteePoints || 200 }} 积分，你得 {{ invite?.inviterPoints || 80 }} 积分。</text>
      <view class="invite-card"><view><text class="invite-code">{{ invite?.code || '—' }}</text><text class="invite-meta">已邀请 {{ invite?.invitedCount || 0 }} 人 · 累计 +{{ invite?.pointsEarned || 0 }} 分</text></view><button class="claim" @tap="copyInviteCode">复制邀请码</button></view>
      <button class="primary" open-type="share">邀请好友一起赚积分</button>
    </view>
    <view v-else-if="activeTab === 'redpackets'" class="section">
      <view class="section-head"><text>红包雨</text><text>每场限量 100 份</text></view>
      <text class="description">每日 9:00、14:00、21:00 开场，每次随机领取 2–10 积分，每人每场限一次。</text>
      <view v-for="event in redPackets" :key="event.slot" class="event-row"><view><text class="event-title">{{ Number(event.slot) }}:00 场</text><text class="event-meta">已领取 {{ event.claimedCount }}/{{ event.packetLimit }}</text></view><button class="claim" :disabled="event.status !== 'open' || busy" @tap="claim(event.slot)">{{ packetLabel(event) }}</button></view>
    </view>
    <view v-else-if="activeTab === 'quiz'" class="section">
      <view class="section-head"><text>答题赚积分</text><text>{{ quiz?.sessionsUsed || 0 }}/3 轮</text></view>
      <template v-if="question">
        <text class="question-count">第 {{ (Number(session?.currentPosition) || 0) + 1 }} / 7 题</text>
        <text class="question">{{ question.question }}</text>
        <view v-for="(label, key) in options" :key="key" :class="['option', selected === key && 'option-selected', locked && 'option-locked']" @tap="choose(String(key))"><text class="option-letter">{{ key }}</text><text>{{ label }}</text></view>
        <button class="primary" :disabled="!selected || locked || busy" @tap="submitAnswer">{{ locked ? `${countdown} 秒后可重选` : '确认答案' }}</button>
      </template>
      <template v-else>
        <text class="description">每轮 7 道四选一，全部答对奖励 100 积分。答错等待 3 秒后可重选，每天最多 3 轮、每轮间隔 2 小时。</text>
        <button class="primary" :disabled="busy || Number(quiz?.sessionsUsed || 0) >= 3" @tap="beginQuiz">{{ Number(quiz?.sessionsUsed || 0) >= 3 ? '今日答题次数已用完' : '开始答题' }}</button>
      </template>
    </view>
    <view v-else class="section">
      <view class="section-head"><text>悬赏任务</text><text>{{ selectedTask ? taskSubmissionCount(selectedTask.id) : submissions.length }}/5 件</text></view>
      <view v-if="!tasks.length" class="empty">当前没有开放投稿的任务</view>
      <view v-for="task in tasks" :key="task.id" class="task-row" @tap="selectedTask = task"><text class="task-title">{{ task.title }}</text><text class="description">{{ task.brief }}</text><text class="task-meta">审核通过可得 {{ task.rewardAmount }} 积分 · 截稿 {{ task.deadline || '待公布' }}</text></view>
      <view v-if="selectedTask" class="task-form">
        <text class="form-title">提交作品 · {{ selectedTask.title }}</text>
        <input v-model="form.productName" class="field" placeholder="产品名称（必填）" />
        <input v-model="form.productCategory" class="field" placeholder="作品品类（必填）" />
        <textarea v-model="form.productFeatures" class="field area" placeholder="特色功能（必填）" />
        <input v-model="form.artifactPrototype" class="field" placeholder="文物原型 / 灵感来源（必填）" />
        <textarea v-model="form.productDescription" class="field area" placeholder="产品介绍（必填）" />
        <input v-model.trim="form.idCard" class="field" maxlength="18" placeholder="身份证号（必填，18 位，用于奖励发放实名核验）" />
        <input v-model.trim="form.contactPhone" class="field" maxlength="11" type="number" placeholder="手机号（必填，11 位，用于打样快递联络）" />
        <view class="upload-guide">全色三视图必交：正面、侧面、背面。每张仅支持 JPG，1–3 MB。</view>
        <view v-for="(label, index) in viewLabels" :key="label" class="upload" @tap="pickArtwork(index)"><text>{{ label }} · {{ uploadedAssets[index]?.title || '选择 JPG 图片' }}</text><text>{{ uploadedAssets[index] ? '已添加' : '选择文件' }}</text></view>
        <button class="primary" :disabled="busy || uploadedAssets.length !== 3 || uploadedAssets.some(item => !item) || taskSubmissionCount(selectedTask.id) >= 5" @tap="sendSubmission">提交投稿</button>
      </view>
      <view v-for="item in submissions" :key="item.id" class="submission-status">{{ item.productName }} · {{ statusLabel(item.status) }}</view>
      <view v-for="voucher in vouchers" :key="voucher.id" class="voucher">免费树脂打样 1 件，含 1 次快递 · {{ voucher.productName }} · 有效至 {{ String(voucher.expiresAt || '').slice(0, 10) }} · {{ voucher.status === 'available' ? '待使用' : voucher.status }}</view>
      <view v-for="rev in revenues" :key="rev.revenueId" class="voucher">销售分成 {{ rev.shareRate }}% · {{ rev.productName }} · 待结算 ¥{{ rev.pendingAmount }} · 已结算 ¥{{ rev.settledAmount }}</view>
    </view>
    <text class="footnote">活动积分按服务器记录为准。使用时长、分享与邀请奖励每日额度于次日 0 点刷新。</text>
  </view>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { onLoad, onShareAppMessage, onShareTimeline, onShow } from '@dcloudio/uni-app'
import { answerQuiz, checkinToday, claimRedPacket, claimShareReward, getActivityOverview, getBountyTasks, getCurrentRedPackets, getMyBountyRevenue, getMyBountySubmissions, getMySampleVouchers, startQuiz, submitBountyTask } from '../../api/activities'
import { createMultiViewBundle, uploadReference } from '../../api/creative'

const tabs = [{ key: 'checkin', label: '签到' }, { key: 'growth', label: '成长' }, { key: 'redpackets', label: '红包雨' }, { key: 'quiz', label: '答题' }, { key: 'tasks', label: '悬赏令' }]
const activeTab = ref('checkin')
const balance = ref(0)
const checkin = ref<any>({})
const streakWeekProgress = computed(() => Number(checkin.value?.streakDays || 0) === 0 ? 0 : ((Number(checkin.value.streakDays) - 1) % 7) + 1)
const usage = ref<any>({})
const share = ref<any>({})
const invite = ref<any>({})
const usageProgress = computed(() => {
  const cap = Number(usage.value?.dailyPointCap || 1000)
  return Math.max(0, Math.min(100, Math.round(Number(usage.value?.points || 0) / cap * 100)))
})
const redPackets = ref<any[]>([])
const quiz = ref<any>({})
const session = ref<any>(null)
const question = computed(() => session.value?.question || null)
const options = computed(() => ({ A: question.value?.optionA, B: question.value?.optionB, C: question.value?.optionC, D: question.value?.optionD }))
const tasks = ref<any[]>([])
const submissions = ref<any[]>([])
const vouchers = ref<any[]>([])
const revenues = ref<any[]>([])
const selectedTask = ref<any>(null)
const uploadedAssets = ref<any[]>([])
const viewLabels = ['正面', '侧面', '背面']
const form = ref({ productName: '', productCategory: '', productFeatures: '', artifactPrototype: '', productDescription: '', idCard: '', contactPhone: '' })
const selected = ref('')
const busy = ref(false)
const locked = ref(false)
const countdown = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

function toast(title: string) { uni.showToast({ title, icon: 'none' }) }
function packetLabel(event: any) { return ({ upcoming: '即将开始', open: '抢红包', claimed: '已领取', sold_out: '已抢完', closed: '已结束' } as Record<string, string>)[event.status] || '抢红包' }
function statusLabel(status: string) { return ({ pending_review: '审核中', approved: '已通过', rejected: '未通过' } as Record<string, string>)[status] || status }
function taskSubmissionCount(taskId: number) { return submissions.value.filter(item => Number(item.taskId) === Number(taskId)).length }

async function load() {
  try {
    const result = await getActivityOverview()
    balance.value = Number(result?.creditAccount?.balance ?? result?.balance ?? balance.value) || balance.value
    checkin.value = result?.checkin || {}
    usage.value = result?.usage || {}
    share.value = result?.share || {}
    invite.value = result?.invite || {}
    redPackets.value = Array.isArray(result?.redPackets) ? result.redPackets : []
    quiz.value = result?.quiz || {}
    session.value = result?.quiz?.session || null
  } catch (error: any) { toast(error?.message || '活动信息加载失败') }
  try { redPackets.value = await getCurrentRedPackets() } catch { /* overview still carries the event state */ }
  try { tasks.value = await getBountyTasks(); submissions.value = await getMyBountySubmissions(); vouchers.value = await getMySampleVouchers(); revenues.value = await getMyBountyRevenue() } catch { tasks.value = []; submissions.value = []; vouchers.value = []; revenues.value = [] }
}

async function run<T>(action: () => Promise<T>) {
  if (busy.value) return
  busy.value = true
  try { return await action() } catch (error: any) { toast(error?.message || '操作失败，请稍后重试') }
  finally { busy.value = false; await load() }
}

async function doCheckin() { await run(async () => { const result = await checkinToday(); toast(`签到成功，获得 ${result.points} 积分`) }) }
async function claimShare() {
  await run(async () => {
    const result = await claimShareReward('timeline')
    toast(`分享奖励 +${result.points} 积分`)
  })
}
function copyInviteCode() {
  const code = String(invite.value?.code || '')
  if (!code) return toast('邀请码加载中，请稍后再试')
  uni.setClipboardData({ data: code, success: () => toast('邀请码已复制') })
}
function inviteSharePath() {
  const code = String(invite.value?.code || '')
  return `/pages/register/index${code ? `?inviteCode=${encodeURIComponent(code)}` : ''}`
}
onShareAppMessage(() => ({ title: '来之间智造创作你的文创，注册双方都得积分', path: inviteSharePath() }))
onShareTimeline(() => ({ title: '来之间智造创作你的文创，注册双方都得积分', query: invite.value?.code ? `inviteCode=${encodeURIComponent(String(invite.value.code))}` : '' }))
async function claim(slot: string) { await run(async () => { const result = await claimRedPacket(slot); toast(`抢到 ${result.points} 积分`) }) }
async function beginQuiz() { await run(async () => { session.value = await startQuiz(); selected.value = '' }) }
async function submitAnswer() {
  if (!session.value?.id || !selected.value) return
  await run(async () => {
    const result = await answerQuiz(Number(session.value.id), selected.value)
    selected.value = ''
    if (!result.correct) {
      locked.value = true; countdown.value = 3
      if (timer) clearInterval(timer)
      timer = setInterval(() => { countdown.value -= 1; if (countdown.value <= 0) { locked.value = false; if (timer) clearInterval(timer); timer = null } }, 1000)
      toast('答错了，3秒后可以重新选择')
    } else if (result.completed) { session.value = null; toast('答题完成，获得 100 积分') }
    else { session.value = { ...session.value, currentPosition: result.position - 1, question: result.question }; toast('回答正确') }
  })
}

function choose(option: string) { if (!locked.value) selected.value = option }
function pickArtwork(index: number) {
  uni.chooseImage({ count: 1, sizeType: ['original'], success: async (result) => {
    const localPath = result.tempFilePaths?.[0]
    if (!localPath) return
    await run(async () => {
      const file = (result as any).tempFiles?.[0]
      const name = String(file?.name || localPath).toLowerCase()
      const size = Number(file?.size || 0)
      if (!name.endsWith('.jpg') && !name.endsWith('.jpeg')) throw new Error('三视图仅支持 JPG 格式')
      if (size < 1_048_576 || size > 3_145_728) throw new Error('每张图片大小必须为 1–3 MB')
      const uploaded = await uploadReference(localPath)
      uploadedAssets.value[index] = { ...uploaded, localName: name }
      toast(`${viewLabels[index]}图片已上传`)
    })
  } })
}

async function sendSubmission() {
  if (!selectedTask.value || uploadedAssets.value.length !== 3 || uploadedAssets.value.some(item => !item?.assetId)) return toast('请先上传正面、侧面和背面三张图片')
  const required = Object.values(form.value).every(value => String(value).trim())
  if (!required) return toast('请填写全部作品信息')
  if (!/^\d{17}[\dXx]$/.test(form.value.idCard)) return toast('身份证号必须为 18 位，请核对后再提交')
  if (!/^1\d{10}$/.test(form.value.contactPhone)) return toast('手机号必须为 11 位，请核对后再提交')
  await run(async () => {
    const ids = uploadedAssets.value.map(item => Number(item.assetId))
    const bundle = await createMultiViewBundle({ inputAssetId: ids[0], viewCount: 3, productName: form.value.productName, material: '树脂', images: [{ view: 'front', assetId: ids[0] }, { view: 'left', assetId: ids[1] }, { view: 'back', assetId: ids[2] }] })
    await submitBountyTask(Number(selectedTask.value.id), { ...form.value, assetId: ids[0], bundleId: Number(bundle.id || bundle.bundleId), viewAssetIds: ids })
    toast('投稿已提交')
    selectedTask.value = null; uploadedAssets.value = []
    form.value = { productName: '', productCategory: '', productFeatures: '', artifactPrototype: '', productDescription: '', idCard: '', contactPhone: '' }
  })
}

onShow(load)
onLoad((query) => {
  // 朋友圈分享只能落在当前页，先把邀请码留在本地，好友注册时自动带上。
  const inviteCode = String(query?.inviteCode || '').trim()
  if (inviteCode) uni.setStorageSync('pending_invite_code', inviteCode.toUpperCase())
  if (query?.tab && tabs.some(tab => tab.key === query.tab)) activeTab.value = String(query.tab)
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:36rpx 28rpx calc(54rpx + env(safe-area-inset-bottom));box-sizing:border-box;background:#f5f7f4;color:#242a27}.hero{display:flex;flex-direction:column;padding:20rpx 4rpx 28rpx}.eyebrow{color:#498b77;font-size:18rpx;font-weight:800}.title{margin-top:8rpx;font-size:42rpx;font-weight:800}.balance{margin-top:10rpx;color:#5f7f70;font-size:23rpx}.tabs{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:8rpx;margin-bottom:20rpx}.tab{padding:16rpx 2rpx;border-radius:12rpx;background:#e8eeea;color:#65736c;font-size:20rpx;text-align:center}.selected{background:#177b68;color:#fff;font-weight:750}.section{padding:26rpx 22rpx;border:1rpx solid #e4eae5;border-radius:18rpx;background:#fff;box-shadow:0 8rpx 24rpx rgba(32,56,46,.05)}.section-head{display:flex;align-items:center;justify-content:space-between;gap:10rpx;font-size:28rpx;font-weight:800}.section-head text:last-child{color:#648477;font-size:19rpx;font-weight:600}.description{display:block;margin-top:14rpx;color:#7d8982;font-size:21rpx;line-height:1.65}.week-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8rpx;margin-top:22rpx}.day{display:flex;min-height:92rpx;flex-direction:column;align-items:center;justify-content:center;gap:7rpx;border-radius:12rpx;background:#f2f5f2;color:#7a8780;font-size:18rpx}.done{background:#dcf1e8;color:#167760}.primary{height:82rpx;margin-top:24rpx;border:0;border-radius:14rpx;background:#087f6c;color:white;font-size:24rpx;font-weight:750;line-height:82rpx}.primary[disabled]{opacity:.55}.event-row{display:flex;align-items:center;justify-content:space-between;gap:14rpx;padding:18rpx 0;border-top:1rpx solid #edf0ed}.event-row:first-of-type{margin-top:18rpx}.event-row view{display:flex;flex-direction:column;gap:7rpx}.event-title{font-size:24rpx;font-weight:750}.event-meta{color:#87928c;font-size:18rpx}.claim{min-width:142rpx;height:64rpx;border-radius:32rpx;background:#e3f4ed;color:#177b68;font-size:20rpx;line-height:64rpx}.claim[disabled]{color:#8c9690;background:#eff1ef}.question-count{display:block;margin:24rpx 0 8rpx;color:#4e8b78;font-size:19rpx}.question{display:block;margin:16rpx 0 22rpx;font-size:27rpx;font-weight:750;line-height:1.55}.option{display:flex;align-items:center;gap:13rpx;margin-top:11rpx;padding:18rpx 14rpx;border:1rpx solid #e3e9e4;border-radius:13rpx;color:#48524c;font-size:21rpx}.option-selected{border-color:#258b74;background:#eef8f3}.option-locked{opacity:.62}.option-letter{display:grid;width:38rpx;height:38rpx;place-items:center;border-radius:50%;background:#e9f0eb;color:#347964;font-weight:800}.task-row{display:flex;flex-direction:column;margin-top:18rpx;padding:18rpx 0;border-top:1rpx solid #edf0ed}.task-title,.form-title{font-size:24rpx;font-weight:800}.task-meta{margin-top:11rpx;color:#4d8874;font-size:19rpx}.empty{padding:44rpx 0;color:#8a948e;font-size:21rpx;text-align:center}.task-form{margin-top:22rpx;padding-top:18rpx;border-top:1rpx solid #e8ede9}.field{box-sizing:border-box;width:100%;height:76rpx;margin-top:12rpx;padding:0 16rpx;border:1rpx solid #e0e7e1;border-radius:11rpx;background:#fbfcfa;font-size:21rpx}.area{height:112rpx;padding-top:15rpx}.upload{display:flex;align-items:center;justify-content:space-between;gap:10rpx;margin-top:13rpx;padding:17rpx 14rpx;border:1rpx dashed #79a995;border-radius:12rpx;color:#4b806b;font-size:18rpx}.submission-status,.voucher{padding:13rpx 0;border-top:1rpx solid #edf0ed;color:#68766f;font-size:19rpx}.voucher{color:#3f7c65}.footnote{display:block;margin:20rpx 5rpx;color:#8b958f;font-size:17rpx;line-height:1.6}.primary::after,.claim::after{border:0}
.growth-bar{height:14rpx;margin-top:22rpx;border-radius:7rpx;background:#edf2ee;overflow:hidden}.growth-fill{height:100%;border-radius:7rpx;background:linear-gradient(90deg,#2f9b7d,#79c7a8)}
.sub-head{margin-top:32rpx;padding-top:24rpx;border-top:1rpx solid #edf0ed}
.invite-card{display:flex;align-items:center;justify-content:space-between;gap:14rpx;margin-top:18rpx;padding:20rpx;border:1rpx dashed #a8c6b6;border-radius:14rpx;background:#f2faf6}
.invite-card>view{display:flex;flex-direction:column;gap:8rpx}
.invite-code{color:#177b68;font-size:34rpx;font-weight:800;letter-spacing:3rpx}
.invite-meta{color:#718b7e;font-size:19rpx}
</style>
