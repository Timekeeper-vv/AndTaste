<script setup>
import { computed } from 'vue'

const props = defineProps({
  currentUser: Object,
  currentPage: String,
  collapsed: Boolean,
})

const emit = defineEmits(['switch-page', 'logout', 'toggle'])

const roleLabels = { admin: '管理员', technician: '技术员', feeder: '饲养员' }
const roleColors = { admin: '#0d9488', technician: '#2563eb', feeder: '#d97706' }

const allMenus = [
  {
    group: '概览',
    items: [
      { key: 'dashboard', label: '仪表盘', roles: ['admin', 'technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>` },
    ]
  },
  {
    group: '基础数据',
    items: [
      { key: 'pens',  label: '圈舍管理',  roles: ['admin', 'feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>` },
      { key: 'drugs', label: '兽药疫苗库', roles: ['admin', 'technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 3H5a2 2 0 0 0-2 2v4m6-6h10a2 2 0 0 1 2 2v4M9 3v18m0 0h10a2 2 0 0 0 2-2V9M9 21H5a2 2 0 0 1-2-2V9m0 0h18"/></svg>` },
    ]
  },
  {
    group: '养殖资产',
    items: [
      { key: 'batches', label: '养殖批次', roles: ['admin', 'feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/></svg>` },
      { key: 'animals', label: '个体档案', roles: ['admin', 'technician', 'feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>` },
    ]
  },
  {
    group: '生产记录',
    items: [
      { key: 'immunization', label: '免疫记录', roles: ['admin', 'technician', 'feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>` },
      { key: 'medication',   label: '用药记录', roles: ['admin', 'technician', 'feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.5 20H4a2 2 0 0 1-2-2V5c0-1.1.9-2 2-2h3.93a2 2 0 0 1 1.66.9l.82 1.2a2 2 0 0 0 1.66.9H20a2 2 0 0 1 2 2v3"/><circle cx="18" cy="18" r="3"/><path d="m22 22-1.5-1.5"/></svg>` },
      { key: 'transfer',     label: '转舍管理', roles: ['admin', 'technician', 'feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 1l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>` },
      { key: 'slaughter',    label: '出栏管理', roles: ['admin', 'feeder'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>` },
    ]
  },
  {
    group: '溯源 & 系统',
    items: [
      { key: 'traceability', label: '全链路溯源', roles: ['admin', 'technician'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>` },
      { key: 'users',        label: '用户管理',  roles: ['admin'], icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>` },
    ]
  },
]

const menus = computed(() => {
  const role = props.currentUser?.role || 'admin'
  return allMenus
    .map(g => ({ ...g, items: g.items.filter(item => item.roles.includes(role)) }))
    .filter(g => g.items.length > 0)
})

const currentRoleLabel = computed(() => roleLabels[props.currentUser?.role] || '管理员')
const currentRoleColor = computed(() => roleColors[props.currentUser?.role] || '#0d9488')
</script>

<template>
  <aside class="sidebar" :class="{ collapsed }">
    <!-- Logo -->
    <div class="sidebar-logo">
      <div class="logo-icon">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          <polyline points="9 22 9 12 15 12 15 22"/>
        </svg>
      </div>
      <transition name="fade">
        <div v-if="!collapsed" class="logo-text">
          <span class="logo-title">智慧养殖平台</span>
          <span class="logo-sub">Farm Management</span>
        </div>
      </transition>
    </div>

    <!-- Navigation -->
    <nav class="sidebar-nav">
      <template v-for="menu in menus" :key="menu.group">
        <div class="nav-group-label" v-if="!collapsed">{{ menu.group }}</div>
        <div v-else class="nav-group-divider"></div>
        <button
          v-for="item in menu.items"
          :key="item.key"
          class="nav-item"
          :class="{ active: currentPage === item.key }"
          :title="collapsed ? item.label : ''"
          @click="emit('switch-page', item.key)"
        >
          <span class="nav-icon" v-html="item.icon"></span>
          <transition name="fade">
            <span v-if="!collapsed" class="nav-label">{{ item.label }}</span>
          </transition>
          <transition name="fade">
            <span v-if="!collapsed && currentPage === item.key" class="nav-active-bar"></span>
          </transition>
        </button>
      </template>
    </nav>

    <!-- Footer -->
    <div class="sidebar-footer">
      <div v-if="!collapsed" class="footer-user">
        <div class="footer-avatar">{{ currentUser.username?.[0]?.toUpperCase() }}</div>
        <div class="footer-info">
          <span class="footer-name">{{ currentUser.username }}</span>
          <span class="footer-role" :style="{ color: currentRoleColor }">{{ currentRoleLabel }}</span>
        </div>
      </div>
      <div v-else class="footer-avatar-only">
        <div class="footer-avatar">{{ currentUser.username?.[0]?.toUpperCase() }}</div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  top: 0;
  left: 0;
  width: var(--sidebar-w);
  height: 100vh;
  background: #0F172A;
  display: flex;
  flex-direction: column;
  z-index: 50;
  transition: width .2s ease;
  overflow: hidden;
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-w);
}

/* Logo area */
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  height: var(--header-h);
  border-bottom: 1px solid rgba(255,255,255,.06);
  flex-shrink: 0;
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: var(--c-primary);
  border-radius: var(--r);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.logo-text {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  white-space: nowrap;
}

.logo-title {
  font-size: 14px;
  font-weight: 700;
  color: #fff;
  line-height: 1.2;
}

.logo-sub {
  font-size: 11px;
  color: rgba(255,255,255,.35);
  letter-spacing: 0.5px;
}

/* Navigation */
.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 0;
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,.08) transparent;
}

.sidebar-nav::-webkit-scrollbar { width: 3px; }
.sidebar-nav::-webkit-scrollbar-thumb { background: rgba(255,255,255,.08); }

.nav-group-label {
  padding: 12px 16px 4px;
  font-size: 10px;
  font-weight: 600;
  color: rgba(255,255,255,.25);
  text-transform: uppercase;
  letter-spacing: 0.8px;
  white-space: nowrap;
}

.nav-group-divider {
  margin: 8px 12px;
  border-top: 1px solid rgba(255,255,255,.06);
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  height: 40px;
  width: 100%;
  border: none;
  background: transparent;
  color: rgba(255,255,255,.55);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font);
  cursor: pointer;
  transition: color .15s, background .15s;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
}

.nav-item:hover {
  color: rgba(255,255,255,.9);
  background: rgba(255,255,255,.05);
}

.nav-item.active {
  color: #fff;
  background: rgba(13,148,136,.2);
}

.nav-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
}

.nav-active-bar {
  position: absolute;
  left: 0;
  top: 8px;
  bottom: 8px;
  width: 3px;
  background: var(--c-primary);
  border-radius: 0 2px 2px 0;
}

.nav-label {
  flex: 1;
  white-space: nowrap;
}

/* Footer */
.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid rgba(255,255,255,.06);
  flex-shrink: 0;
}

.footer-user {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}

.footer-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--c-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.footer-avatar-only {
  display: flex;
  justify-content: center;
}

.footer-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.footer-name {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255,255,255,.85);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.footer-role {
  font-size: 11px;
  color: rgba(255,255,255,.3);
}

/* Transitions */
.fade-enter-active, .fade-leave-active { transition: opacity .15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
