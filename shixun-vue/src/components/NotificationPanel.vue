<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'


type SystemNotification = { type?: 'warning' | 'info'; category?: string; title: string; message: string; readFlag?: number; createdAt?: string }
const notifications = ref<SystemNotification[]>([])
const open = ref<boolean>(false)
const unread = ref<number>(0)
let pollTimer: ReturnType<typeof setInterval> | null = null

async function fetchNotifications() {
  try {
    const res = await fetch('/api/workflows/notifications')
    if (res.ok) {
      const rows = await res.json()
      notifications.value = (Array.isArray(rows) ? rows : []).map((n: SystemNotification) => ({ ...n, type: n.type || 'info' }))
      if (!open.value) unread.value = notifications.value.filter(n => !n.readFlag).length
    }
  } catch { /* silent */ }
}

function togglePanel() {
  open.value = !open.value
  if (open.value) unread.value = 0
}

function closePanel() {
  open.value = false
}

onMounted(() => {
  fetchNotifications()
  pollTimer = setInterval(fetchNotifications, 30000)
  document.addEventListener('click', onDocClick)
})

onUnmounted(() => {
  clearInterval(pollTimer)
  document.removeEventListener('click', onDocClick)
})

function onDocClick(e: MouseEvent): void {
  if (!(e.target as Element).closest('.notif-wrap')) closePanel()
}
</script>

<template>
  <div class="notif-wrap">
    <button class="notif-btn" :class="{ active: open }" @click.stop="togglePanel" title="系统预警">
      <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
        <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
      </svg>
      <span v-if="unread > 0" class="notif-badge">{{ unread > 9 ? '9+' : unread }}</span>
    </button>

    <transition name="panel">
      <div v-if="open" class="notif-panel" @click.stop>
        <div class="panel-header">
          <span class="panel-title">系统预警</span>
          <span class="panel-count">{{ notifications.length }} 条</span>
        </div>

        <div class="panel-body">
          <div v-if="notifications.length === 0" class="notif-empty">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><polyline points="12 8 12 12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            <p>暂无仓储、物流、履约预警</p>
          </div>

          <div
            v-for="(n, i) in notifications"
            :key="i"
            class="notif-item"
            :class="n.type"
          >
            <div class="notif-icon">
              <!-- warning -->
              <svg v-if="n.type === 'warning'" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
              <!-- info -->
              <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="16" x2="12" y2="12"/><line x1="12" y1="8" x2="12.01" y2="8"/></svg>
            </div>
            <div class="notif-content">
              <div class="notif-title"><span>{{ n.title }}</span><em v-if="n.category">{{ n.category }}</em></div>
              <div class="notif-msg">{{ n.message }}</div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.notif-wrap {
  position: relative;
}

.notif-btn {
  position: relative;
  width: 34px;
  height: 34px;
  border: 1px solid var(--c-border);
  border-radius: var(--r);
  background: var(--c-surface);
  color: var(--c-text-2);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all .15s;
}
.notif-btn:hover, .notif-btn.active {
  background: var(--c-bg);
  color: var(--c-text);
  border-color: var(--c-primary);
}

.notif-badge {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 3px;
  background: var(--c-error);
  color: #fff;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
  border: 1.5px solid var(--c-surface);
}

.notif-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 320px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-lg);
  z-index: 200;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--c-border);
}

.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
}

.panel-count {
  font-size: 12px;
  color: var(--c-text-3);
}

.panel-body {
  max-height: 340px;
  overflow-y: auto;
}

.notif-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 16px;
  color: var(--c-text-3);
  font-size: 13px;
}

.notif-item {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--c-border);
  transition: background .1s;
}
.notif-item:last-child { border-bottom: none; }
.notif-item:hover { background: var(--c-bg); }

.notif-item.warning .notif-icon { color: var(--c-warning); background: var(--c-warning-bg); }
.notif-item.info    .notif-icon { color: var(--c-info);    background: var(--c-info-bg); }

.notif-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 1px;
}

.notif-content {
  flex: 1;
  min-width: 0;
}

.notif-title {
  display:flex;
  align-items:center;
  justify-content:space-between;
  gap:8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--c-text);
  margin-bottom: 2px;
}
.notif-title em {
  font-style:normal;
  font-size:10px;
  color:var(--c-text-3);
  background:var(--c-bg);
  border-radius:999px;
  padding:2px 6px;
  flex-shrink:0;
}

.notif-msg {
  font-size: 12px;
  color: var(--c-text-2);
  line-height: 1.4;
}

/* Panel slide animation */
.panel-enter-active, .panel-leave-active {
  transition: opacity .15s, transform .15s;
}
.panel-enter-from, .panel-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
