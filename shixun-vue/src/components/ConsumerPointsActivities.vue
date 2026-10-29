<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

const emit = defineEmits<{ alert: [message: string, type?: 'success' | 'error'] }>()
const view = ref<'tasks' | 'questions' | 'submissions'>('tasks')
const tasks = ref<any[]>([])
const questions = ref<any[]>([])
const submissions = ref<any[]>([])
const taskForm = reactive({ campaignKey: '', title: '', brief: '', deadline: '', rewardAmount: 2000, status: 'draft' })
const questionForm = reactive({ category: '文创知识', question: '', optionA: '', optionB: '', optionC: '', optionD: '', correctOption: 'A', explanation: '', active: true })
const busy = ref(false)

async function call(path: string, method = 'GET', body?: unknown) {
  const response = await fetch(path, { method, ...(body === undefined ? {} : { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }) })
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new Error(payload?.message || `HTTP ${response.status}`)
  return payload
}

async function load() {
  try {
    const [taskRows, questionRows, submissionRows] = await Promise.all([
      call('/api/creative/ai/consumer-activities/admin/tasks'),
      call('/api/creative/ai/consumer-activities/admin/quiz/questions'),
      call('/api/creative/ai/consumer-activities/admin/tasks/submissions'),
    ])
    tasks.value = Array.isArray(taskRows) ? taskRows : []
    questions.value = Array.isArray(questionRows) ? questionRows : []
    submissions.value = Array.isArray(submissionRows) ? submissionRows : []
  } catch (error: any) { emit('alert', `积分活动数据加载失败：${error?.message || error}`, 'error') }
}

async function saveTask(task?: any) {
  busy.value = true
  try {
    const data = task || taskForm
    const body = { ...data, deadline: data.deadline || null }
    if (task?.id) await call(`/api/creative/ai/consumer-activities/admin/tasks/${task.id}`, 'PUT', body)
    else await call('/api/creative/ai/consumer-activities/admin/tasks', 'POST', body)
    emit('alert', '悬赏任务已保存')
    if (!task) Object.assign(taskForm, { campaignKey: '', title: '', brief: '', deadline: '', rewardAmount: 2000, status: 'draft' })
    await load()
  } catch (error: any) { emit('alert', `保存失败：${error?.message || error}`, 'error') }
  finally { busy.value = false }
}

async function saveQuestion() {
  busy.value = true
  try {
    await call('/api/creative/ai/consumer-activities/admin/quiz/questions', 'POST', questionForm)
    emit('alert', '题目已加入题库')
    Object.assign(questionForm, { question: '', optionA: '', optionB: '', optionC: '', optionD: '', correctOption: 'A', explanation: '', active: true })
    await load()
  } catch (error: any) { emit('alert', `保存题目失败：${error?.message || error}`, 'error') }
  finally { busy.value = false }
}

async function review(item: any, decision: 'approved' | 'rejected') {
    const selectedForSample = decision === 'approved' && window.confirm('该作品是否入选，并发放免费权益：1件树脂打样，含1次快递运费，有效60天？')
  busy.value = true
  try {
    await call(`/api/creative/ai/consumer-activities/admin/tasks/submissions/${item.id}/review`, 'POST', { decision, selectedForSample })
    emit('alert', decision === 'approved' ? '审核通过，积分已发放' : '投稿已退回')
    await load()
  } catch (error: any) { emit('alert', `审核失败：${error?.message || error}`, 'error') }
  finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <div class="activities-admin">
    <header><div><span class="eyebrow">CONSUMER REWARDS</span><h1>积分活动运营</h1><p>发布悬赏任务、维护题库并审核投稿；签到、红包和答题奖励由服务端按活动规则记入积分流水。</p></div><button @click="load">刷新</button></header>
    <nav><button :class="{ active: view === 'tasks' }" @click="view = 'tasks'">悬赏任务</button><button :class="{ active: view === 'submissions' }" @click="view = 'submissions'">投稿审核 <b>{{ submissions.filter(x => x.status === 'pending_review').length }}</b></button><button :class="{ active: view === 'questions' }" @click="view = 'questions'">题库 <b>{{ questions.filter(x => x.active).length }}</b></button></nav>
    <section v-if="view === 'tasks'" class="panel">
      <h2>新建悬赏任务</h2>
      <div class="form-grid"><label>任务标识<input v-model.trim="taskForm.campaignKey" placeholder="例如 yunnan_museum_2026" /></label><label>标题<input v-model.trim="taskForm.title" placeholder="云南省博物馆文创产品征集令" /></label><label>奖励积分<input v-model.number="taskForm.rewardAmount" type="number" min="1" /></label><label>截稿日期<input v-model="taskForm.deadline" type="date" /></label><label>状态<select v-model="taskForm.status"><option value="draft">草稿</option><option value="active">开放投稿</option><option value="closed">已截止</option></select></label><label class="wide">任务说明<textarea v-model.trim="taskForm.brief" rows="4" /></label></div>
      <button class="primary" :disabled="busy" @click="saveTask()">保存任务</button>
      <h2 class="subhead">已发布任务</h2>
      <div v-for="task in tasks" :key="task.id" class="task-editor"><div class="task-line"><strong>{{ task.title }}</strong><span>{{ task.campaignKey }}</span></div><div class="form-grid"><label>任务标题<input v-model.trim="task.title" /></label><label>奖励积分<input v-model.number="task.rewardAmount" type="number" min="1" /></label><label>截稿日期<input v-model="task.deadline" type="date" /></label><label>状态<select v-model="task.status"><option value="draft">草稿</option><option value="active">开放投稿</option><option value="closed">已截止</option></select></label><label class="wide">任务说明<textarea v-model.trim="task.brief" rows="3" /></label></div><button :disabled="busy" @click="saveTask(task)">保存修改</button></div>
    </section>
    <section v-else-if="view === 'questions'" class="panel">
      <h2>添加四选一题目</h2><div class="form-grid"><label>分类<input v-model.trim="questionForm.category" /></label><label>正确选项<select v-model="questionForm.correctOption"><option>A</option><option>B</option><option>C</option><option>D</option></select></label><label class="wide">题目<textarea v-model.trim="questionForm.question" rows="2" /></label><label>选项 A<input v-model.trim="questionForm.optionA" /></label><label>选项 B<input v-model.trim="questionForm.optionB" /></label><label>选项 C<input v-model.trim="questionForm.optionC" /></label><label>选项 D<input v-model.trim="questionForm.optionD" /></label><label class="wide">答案解析<input v-model.trim="questionForm.explanation" /></label></div><button class="primary" :disabled="busy" @click="saveQuestion">加入题库</button>
      <p class="hint">题库至少需要 7 道启用题目才能开放一轮答题。题目将按题目表逐条录入，收到云珊姐的题库后可在此维护。</p>
      <div v-for="question in questions" :key="question.id" class="question-line"><strong>{{ question.question }}</strong><span>A. {{ question.optionA }}　B. {{ question.optionB }}　C. {{ question.optionC }}　D. {{ question.optionD }}</span><em>正确答案：{{ question.correctOption }}</em></div>
    </section>
    <section v-else class="panel"><h2>悬赏投稿审核</h2><div v-for="item in submissions" :key="item.id" class="submission"><div><strong>{{ item.productName }} · {{ item.title }}</strong><span>{{ item.username }} · {{ item.productCategory }} · 作品文件 #{{ item.assetId }}</span><p>{{ item.productFeatures }}</p><p>文物原型：{{ item.artifactPrototype }}</p><p>{{ item.productDescription }}</p></div><div class="actions"><span>{{ item.status }}</span><template v-if="item.status === 'pending_review'"><button @click="review(item, 'approved')">通过并发 2000 积分</button><button class="reject" @click="review(item, 'rejected')">退回</button></template></div></div><p v-if="!submissions.length" class="hint">暂无投稿</p></section>
  </div>
</template>

<style scoped>
.activities-admin{display:flex;flex-direction:column;gap:18px;padding:24px;color:#26342e}.activities-admin header{display:flex;align-items:center;justify-content:space-between;padding:20px 22px;border:1px solid #e1e8e2;border-radius:12px;background:#fff}.eyebrow{color:#46856f;font-size:11px;font-weight:800}.activities-admin h1{margin:6px 0;font-size:26px}.activities-admin header p,.hint{max-width:760px;color:#77837c;font-size:13px;line-height:1.6}.activities-admin button,.activities-admin select{min-height:36px;padding:7px 12px;border:1px solid #dce5de;border-radius:8px;background:#fff;color:#3c5549;cursor:pointer}.activities-admin nav{display:flex;gap:8px;border-bottom:1px solid #dfe8e1}.activities-admin nav button{border:0;border-radius:8px 8px 0 0;background:transparent}.activities-admin nav button.active{background:#e8f4ed;color:#1b775e;font-weight:700}.activities-admin b{margin-left:5px}.panel{padding:20px 22px;border:1px solid #e4eae5;border-radius:12px;background:#fff}.panel h2{margin:0 0 16px;font-size:17px}.subhead{margin-top:28px!important}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.form-grid label{display:flex;flex-direction:column;gap:6px;color:#617168;font-size:12px}.form-grid input,.form-grid select,.form-grid textarea{box-sizing:border-box;width:100%;min-height:38px;padding:8px 10px;border:1px solid #dce5de;border-radius:7px;color:#27372f;font:inherit}.form-grid textarea{resize:vertical}.wide{grid-column:1/-1}.primary{margin-top:14px;background:#187c63!important;color:white!important}.task-line,.submission,.question-line{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:14px 0;border-top:1px solid #edf0ed}.task-line p,.submission span,.submission p,.question-line span,.question-line em{display:block;margin:5px 0;color:#7b8780;font-size:12px;font-style:normal}.question-line{align-items:flex-start;flex-direction:column;gap:2px}.submission{align-items:flex-start}.submission>div:first-child{max-width:75%;word-break:break-word}.actions{display:flex;align-items:center;gap:8px;flex-wrap:wrap}.reject{color:#a4473c!important}@media(max-width:700px){.activities-admin{padding:12px}.form-grid{grid-template-columns:1fr}.wide{grid-column:auto}.submission{flex-direction:column}.submission>div:first-child{max-width:100%}}
</style>

<style scoped>
.task-editor{padding:14px 0;border-top:1px solid #edf0ed}.task-editor .task-line{border:0;padding:0 0 10px}.task-editor>button{margin-top:10px;background:#e8f4ed;color:#1b775e;font-weight:700}
</style>
