<script setup lang="ts">
import { nextTick, ref } from 'vue'
import type { User } from '../types'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

const props = defineProps<{ currentUser?: User | null }>()
const loading = ref(false)
const inputText = ref('')
const messagesEnd = ref<HTMLElement | null>(null)
const messages = ref<Message[]>([
  {
    role: 'assistant',
    content: '你好，我是之间味道AI助手。我会优先结合系统数据库回答，比如审批、供应商、打样工单、业务表单；也可以帮你梳理下一步该查什么。你可以直接问：打样工单明细一共多少个、江西省博物馆的打样单多少个、审批中心还有多少待处理、供应商付款账号怎么查。'
  }
])

const suggestions = [
  '打样工单明细一共多少个',
  '江西省博物馆的打样单多少个',
  '进行中的打样工单有哪些',
  '按负责人统计打样工单',
  '审批中心待审批有多少条',
  '秦皇岛供应商有几个',
]

async function sendMessage(textFromSuggestion = '') {
  const text = (textFromSuggestion || inputText.value).trim()
  if (!text || loading.value) return
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  const history = messages.value.slice(-11, -1).map(m => ({ role: m.role, content: m.content }))
  const assistantIndex = messages.value.push({ role: 'assistant', content: '' }) - 1
  try {
    const res = await fetch('/api/ai/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text, history, currentUser: props.currentUser })
    })
    if (!res.ok) throw new Error(await res.text())
    await readTextStream(res, chunk => {
      messages.value[assistantIndex].content += chunk
      scrollToBottom()
    })
    if (!messages.value[assistantIndex].content.trim()) {
      messages.value[assistantIndex].content = '连接不上大模型：返回内容为空。'
    }
  } catch (e: any) {
    messages.value[assistantIndex].content = '连接不上大模型或后端服务：' + (e?.message || '未知错误')
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

async function readTextStream(res: Response, onChunk: (chunk: string) => void) {
  if (!res.body) {
    onChunk(await res.text())
    return
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder('utf-8')
  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    onChunk(decoder.decode(value, { stream: true }))
  }
  const tail = decoder.decode()
  if (tail) onChunk(tail)
}


function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function scrollToBottom() {
  nextTick(() => messagesEnd.value?.scrollIntoView({ behavior: 'smooth' }))
}

function clearHistory() {
  messages.value = [messages.value[0]]
}
</script>

<template>
  <div class="page ai-assistant-page">
    <section class="ai-hero">
      <div>
        <span>AND TASTE AI ASSISTANT</span>
        <h2>之间味道AI助手</h2>
        <p>不是简单聊天窗口，而是接入系统数据库的业务助手：先查真实数据，再给出结论、概况和下一步可继续追问的方向。</p>
      </div>
      <button @click="clearHistory">清空对话</button>
    </section>

    <section class="ai-workbench">
      <aside class="ai-side">
        <div class="side-card">
          <h3>可以这样问</h3>
          <button v-for="s in suggestions" :key="s" @click="sendMessage(s)">{{ s }}</button>
        </div>
        <div class="side-card muted">
          <h3>能力范围</h3>
          <p>打样工单、供应商、审批流、业务表单说明、经营分析建议。涉及数据库的问题会以系统真实查询结果为准。</p>
        </div>
      </aside>

      <main class="chat-card">
        <header>
          <div class="ai-avatar">AI</div>
          <div>
            <b>业务数据问答</b>
            <small>数据库查询 / 表单解释 / 业务建议</small>
          </div>
        </header>

        <div class="messages">
          <div v-for="(msg, i) in messages" :key="i" :class="['msg', msg.role]">
            <i v-if="msg.role === 'assistant'">AI</i>
            <p>{{ msg.content }}</p>
          </div>
          <div v-if="loading" class="msg assistant">
            <i>AI</i>
            <p class="typing"><span></span><span></span><span></span></p>
          </div>
          <div ref="messagesEnd"></div>
        </div>

        <footer>
          <textarea v-model="inputText" rows="3" placeholder="输入问题，按 Enter 发送，Shift + Enter 换行…" :disabled="loading" @keydown="onKeydown"></textarea>
          <button :disabled="loading || !inputText.trim()" @click="sendMessage()">
            {{ loading ? '输出中…' : '发送' }}
          </button>
        </footer>
      </main>
    </section>
  </div>
</template>

<style scoped>
.ai-assistant-page{min-height:100vh}.ai-hero{display:flex;justify-content:space-between;gap:24px;align-items:flex-start;padding:34px;border:1px solid rgba(15,23,42,.08);border-radius:30px;background:linear-gradient(125deg,rgba(255,255,255,.98),rgba(255,255,255,.74)),radial-gradient(circle at 86% 0,rgba(20,184,166,.18),transparent 34%);box-shadow:0 24px 68px rgba(15,23,42,.10)}.ai-hero span{font-size:9px;letter-spacing:.18em;color:#0f766e;font-weight:950}.ai-hero h2{margin:8px 0;font-size:36px;font-weight:950;letter-spacing:-.05em;color:#0f172a}.ai-hero p{max-width:760px;margin:0;color:#64748b;line-height:1.75}.ai-hero button{height:36px;padding:0 14px;border:1px solid rgba(15,23,42,.08);border-radius:999px;background:#fff;color:#0f172a;font-weight:900;cursor:pointer}.ai-workbench{display:grid;grid-template-columns:280px minmax(0,1fr);gap:18px;margin-top:18px}.ai-side{display:grid;gap:14px;align-content:start}.side-card,.chat-card{border:1px solid rgba(15,23,42,.08);border-radius:24px;background:linear-gradient(145deg,rgba(255,255,255,.96),rgba(255,255,255,.78));box-shadow:0 20px 56px rgba(15,23,42,.08)}.side-card{padding:18px}.side-card h3{margin:0 0 14px;color:#0f172a;font-size:16px;font-weight:950}.side-card button{width:100%;margin-top:8px;padding:10px 12px;text-align:left;border:1px solid rgba(15,23,42,.08);border-radius:14px;background:#fff;color:#334155;font-weight:850;cursor:pointer}.side-card button:hover{background:#ecfdf5;border-color:rgba(20,184,166,.28);color:#0f766e}.side-card p{margin:0;color:#64748b;line-height:1.7}.chat-card{height:calc(100vh - 190px);min-height:620px;display:flex;flex-direction:column;overflow:hidden}.chat-card header{height:72px;padding:0 20px;border-bottom:1px solid rgba(15,23,42,.08);display:flex;align-items:center;gap:12px}.ai-avatar,.msg i{display:grid;place-items:center;border-radius:50%;background:linear-gradient(135deg,#0f766e,#14b8a6);color:#fff;font-weight:950}.ai-avatar{width:42px;height:42px}.chat-card header b{display:block;color:#0f172a}.chat-card header small{display:block;margin-top:3px;color:#94a3b8}.messages{flex:1;overflow:auto;padding:22px;display:flex;flex-direction:column;gap:14px;background:radial-gradient(circle at 95% 8%,rgba(20,184,166,.08),transparent 28%)}.msg{display:flex;gap:10px;align-items:flex-start}.msg.user{justify-content:flex-end}.msg i{width:30px;height:30px;flex:0 0 30px;font-size:10px}.msg p{max-width:78%;margin:0;padding:12px 14px;border-radius:16px;background:#f8fafc;border:1px solid rgba(15,23,42,.08);color:#0f172a;line-height:1.7;white-space:pre-wrap;word-break:break-word}.msg.user p{background:linear-gradient(135deg,#0f766e,#14b8a6);color:#fff;border-color:transparent;border-top-right-radius:5px}.msg.assistant p{border-top-left-radius:5px}.typing{display:flex!important;gap:5px;width:max-content}.typing span{width:6px;height:6px;border-radius:50%;background:#94a3b8;animation:bounce 1.1s infinite}.typing span:nth-child(2){animation-delay:.15s}.typing span:nth-child(3){animation-delay:.3s}@keyframes bounce{0%,70%,100%{transform:translateY(0)}35%{transform:translateY(-5px)}}.chat-card footer{padding:16px;border-top:1px solid rgba(15,23,42,.08);display:grid;grid-template-columns:1fr auto;gap:12px;background:#fff}.chat-card textarea{resize:none;border:1px solid rgba(15,23,42,.10);border-radius:16px;padding:12px;font-family:inherit;line-height:1.55;outline:none}.chat-card textarea:focus{border-color:#14b8a6;box-shadow:0 0 0 4px rgba(20,184,166,.10)}.chat-card footer button{width:88px;border:0;border-radius:16px;background:linear-gradient(135deg,#0f766e,#14b8a6);color:#fff;font-weight:950;cursor:pointer}.chat-card footer button:disabled{opacity:.45;cursor:not-allowed}@media(max-width:1000px){.ai-workbench{grid-template-columns:1fr}.chat-card{height:70vh;min-height:560px}}@media(max-width:700px){.ai-hero{display:block}.chat-card footer{grid-template-columns:1fr}.chat-card footer button{width:100%;height:40px}.msg p{max-width:86%}}
</style>
