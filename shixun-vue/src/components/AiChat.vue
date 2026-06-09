<script setup lang="ts">
import { ref, nextTick } from 'vue'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

const open = ref(false)
const loading = ref(false)
const inputText = ref('')
const messages = ref<Message[]>([
  { role: 'assistant', content: '你好！我是智慧养殖 AI 助手，可以回答关于畜牧养殖、疫病防治、饲养管理等专业问题，请问有什么可以帮你？' }
])
const messagesEnd = ref<HTMLElement | null>(null)

function toggle() {
  open.value = !open.value
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true
  scrollToBottom()

  // 发送给后端时携带最近 10 条历史（去掉当前这条，因为已在 message 字段）
  const history = messages.value.slice(-11, -1).map(m => ({
    role: m.role,
    content: m.content
  }))

  try {
    const res = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text, history })
    })
    const data = await res.json()
    if (data.reply) {
      messages.value.push({ role: 'assistant', content: data.reply })
    } else {
      messages.value.push({ role: 'assistant', content: `抱歉，出现了错误：${data.error || '未知错误'}` })
    }
  } catch {
    messages.value.push({ role: 'assistant', content: '网络错误，请检查后端服务是否正常运行。' })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    messagesEnd.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function clearHistory() {
  messages.value = [messages.value[0]]
}
</script>

<template>
  <!-- 浮动按钮 -->
  <button class="ai-fab" @click="toggle" :title="open ? '关闭 AI 助手' : '打开 AI 助手'">
    <svg v-if="!open" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      <circle cx="9" cy="10" r="1" fill="currentColor" stroke="none"/>
      <circle cx="12" cy="10" r="1" fill="currentColor" stroke="none"/>
      <circle cx="15" cy="10" r="1" fill="currentColor" stroke="none"/>
    </svg>
    <svg v-else width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
      <line x1="18" y1="6" x2="6" y2="18"/>
      <line x1="6" y1="6" x2="18" y2="18"/>
    </svg>
  </button>

  <!-- 聊天面板 -->
  <transition name="chat-slide">
    <div v-if="open" class="ai-panel">
      <!-- 面板头部 -->
      <div class="ai-panel-header">
        <div class="ai-header-info">
          <div class="ai-avatar">AI</div>
          <div>
            <div class="ai-title">养殖 AI 助手</div>
            <div class="ai-subtitle">畜牧养殖专业问答</div>
          </div>
        </div>
        <button class="ai-clear-btn" @click="clearHistory" title="清空对话">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/>
            <path d="M10 11v6"/><path d="M14 11v6"/>
          </svg>
        </button>
      </div>

      <!-- 消息区域 -->
      <div class="ai-messages">
        <div
          v-for="(msg, i) in messages"
          :key="i"
          :class="['ai-msg', msg.role === 'user' ? 'ai-msg-user' : 'ai-msg-bot']"
        >
          <div v-if="msg.role === 'assistant'" class="ai-msg-avatar">AI</div>
          <div class="ai-msg-bubble">{{ msg.content }}</div>
        </div>

        <!-- 加载中动画 -->
        <div v-if="loading" class="ai-msg ai-msg-bot">
          <div class="ai-msg-avatar">AI</div>
          <div class="ai-msg-bubble ai-typing">
            <span></span><span></span><span></span>
          </div>
        </div>

        <div ref="messagesEnd" />
      </div>

      <!-- 输入区域 -->
      <div class="ai-input-area">
        <textarea
          v-model="inputText"
          class="ai-input"
          placeholder="输入问题，按 Enter 发送..."
          rows="2"
          @keydown="onKeydown"
          :disabled="loading"
        />
        <button class="ai-send-btn" @click="sendMessage" :disabled="loading || !inputText.trim()">
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"/>
            <polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </button>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/* 浮动按钮 */
.ai-fab {
  position: fixed;
  bottom: 28px;
  right: 28px;
  z-index: 1000;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--c-primary, #0d9488);
  color: #fff;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px rgba(13, 148, 136, 0.45);
  transition: transform .2s, box-shadow .2s;
}
.ai-fab:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(13, 148, 136, 0.55);
}

/* 聊天面板 */
.ai-panel {
  position: fixed;
  bottom: 92px;
  right: 28px;
  z-index: 999;
  width: 360px;
  height: 520px;
  background: var(--c-surface, #fff);
  border: 1px solid var(--c-border, #e5e7eb);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 过渡动画 */
.chat-slide-enter-active,
.chat-slide-leave-active {
  transition: opacity .25s, transform .25s;
}
.chat-slide-enter-from,
.chat-slide-leave-to {
  opacity: 0;
  transform: translateY(16px) scale(0.97);
}

/* 头部 */
.ai-panel-header {
  padding: 14px 16px;
  border-bottom: 1px solid var(--c-border, #e5e7eb);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--c-surface, #fff);
  flex-shrink: 0;
}

.ai-header-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ai-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--c-primary, #0d9488);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ai-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--c-text, #111827);
}

.ai-subtitle {
  font-size: 11px;
  color: var(--c-text-3, #9ca3af);
  margin-top: 1px;
}

.ai-clear-btn {
  width: 28px;
  height: 28px;
  border: 1px solid var(--c-border, #e5e7eb);
  border-radius: 6px;
  background: transparent;
  color: var(--c-text-2, #6b7280);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all .15s;
}
.ai-clear-btn:hover {
  background: #fee2e2;
  border-color: #fca5a5;
  color: #ef4444;
}

/* 消息区域 */
.ai-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ai-msg {
  display: flex;
  gap: 8px;
  align-items: flex-start;
}

.ai-msg-user {
  flex-direction: row-reverse;
}

.ai-msg-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--c-primary, #0d9488);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
}

.ai-msg-bubble {
  max-width: 78%;
  padding: 10px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-msg-bot .ai-msg-bubble {
  background: var(--c-bg, #f9fafb);
  color: var(--c-text, #111827);
  border: 1px solid var(--c-border, #e5e7eb);
  border-top-left-radius: 4px;
}

.ai-msg-user .ai-msg-bubble {
  background: var(--c-primary, #0d9488);
  color: #fff;
  border-top-right-radius: 4px;
}

/* 打字动画 */
.ai-typing {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 16px;
}
.ai-typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--c-text-3, #9ca3af);
  animation: typing-bounce 1.2s ease-in-out infinite;
}
.ai-typing span:nth-child(2) { animation-delay: .2s; }
.ai-typing span:nth-child(3) { animation-delay: .4s; }

@keyframes typing-bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
}

/* 输入区域 */
.ai-input-area {
  padding: 12px;
  border-top: 1px solid var(--c-border, #e5e7eb);
  display: flex;
  gap: 8px;
  align-items: flex-end;
  flex-shrink: 0;
}

.ai-input {
  flex: 1;
  resize: none;
  border: 1px solid var(--c-border, #e5e7eb);
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 13px;
  font-family: inherit;
  color: var(--c-text, #111827);
  background: var(--c-bg, #f9fafb);
  outline: none;
  transition: border-color .15s;
  line-height: 1.5;
}
.ai-input:focus {
  border-color: var(--c-primary, #0d9488);
  background: var(--c-surface, #fff);
}
.ai-input:disabled { opacity: .6; }

.ai-send-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: var(--c-primary, #0d9488);
  color: #fff;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: opacity .15s;
}
.ai-send-btn:disabled { opacity: .4; cursor: not-allowed; }
.ai-send-btn:not(:disabled):hover { opacity: .85; }
</style>
