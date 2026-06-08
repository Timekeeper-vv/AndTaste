<script setup>
import { ref, onMounted, watchEffect } from 'vue'
import LoginPage from './components/LoginPage.vue'
import Sidebar from './components/Sidebar.vue'
import Dashboard from './components/Dashboard.vue'
import UserManagement from './components/UserManagement.vue'
import PenManagement from './components/PenManagement.vue'
import DrugVaccineManagement from './components/DrugVaccineManagement.vue'
import BatchManagement from './components/BatchManagement.vue'
import AnimalManagement from './components/AnimalManagement.vue'
import ImmunizationManagement from './components/ImmunizationManagement.vue'
import MedicationManagement from './components/MedicationManagement.vue'
import PenTransferManagement from './components/PenTransferManagement.vue'
import SlaughterManagement from './components/SlaughterManagement.vue'
import TraceabilityView from './components/TraceabilityView.vue'
import GlobalAlert from './components/GlobalAlert.vue'

const PAGE_ROLES = {
  dashboard:    ['admin', 'technician'],
  pens:         ['admin', 'feeder'],
  drugs:        ['admin', 'technician'],
  batches:      ['admin', 'feeder'],
  animals:      ['admin', 'technician', 'feeder'],
  immunization: ['admin', 'technician', 'feeder'],
  medication:   ['admin', 'technician', 'feeder'],
  transfer:     ['admin', 'technician', 'feeder'],
  slaughter:    ['admin', 'feeder'],
  traceability: ['admin', 'technician'],
  users:        ['admin'],
}

function hasAccess(page, role) {
  return (PAGE_ROLES[page] ?? ['admin']).includes(role || 'admin')
}

function firstAllowedPage(role) {
  return Object.keys(PAGE_ROLES).find(p => hasAccess(p, role)) || 'animals'
}

const currentUser = ref(null)
const currentPage = ref('dashboard')
const sidebarCollapsed = ref(false)
const alertMsg = ref('')
const alertType = ref('success')
const alertVisible = ref(false)
let alertTimer = null

onMounted(() => {
  const saved = sessionStorage.getItem('currentUser')
  if (saved) currentUser.value = JSON.parse(saved)
})

watchEffect(() => {
  if (!currentUser.value) return
  const role = currentUser.value.role || 'admin'
  if (!hasAccess(currentPage.value, role)) {
    currentPage.value = firstAllowedPage(role)
  }
})

function showAlert(msg, type = 'success') {
  alertMsg.value = msg
  alertType.value = type
  alertVisible.value = true
  if (alertTimer) clearTimeout(alertTimer)
  alertTimer = setTimeout(() => { alertVisible.value = false }, 3000)
}

function onLogin(user) {
  currentUser.value = user
  sessionStorage.setItem('currentUser', JSON.stringify(user))
  currentPage.value = 'dashboard'
}

function onLogout() {
  currentUser.value = null
  sessionStorage.removeItem('currentUser')
}

const pageLabels = {
  dashboard:    '概览仪表盘',
  pens:         '圈舍管理',
  drugs:        '兽药疫苗库',
  batches:      '养殖批次',
  animals:      '个体档案',
  immunization: '免疫记录',
  medication:   '用药记录',
  transfer:     '转舍管理',
  slaughter:    '出栏管理',
  traceability: '全链路溯源',
  users:        '用户管理',
}
</script>

<template>
  <LoginPage v-if="!currentUser" @login="onLogin" />

  <div v-else class="app-shell" :class="{ collapsed: sidebarCollapsed }">
    <!-- Sidebar -->
    <Sidebar
      :current-user="currentUser"
      :current-page="currentPage"
      :collapsed="sidebarCollapsed"
      @switch-page="p => { if (hasAccess(p, currentUser?.role)) currentPage = p }"
      @logout="onLogout"
      @toggle="sidebarCollapsed = !sidebarCollapsed"
    />

    <!-- Right area -->
    <div class="app-body">
      <!-- Top header -->
      <header class="app-header">
        <div class="header-left">
          <button class="toggle-btn" @click="sidebarCollapsed = !sidebarCollapsed" title="Toggle sidebar">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="3" y1="6" x2="21" y2="6"/>
              <line x1="3" y1="12" x2="21" y2="12"/>
              <line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
          </button>
          <nav class="breadcrumb">
            <span class="bc-root">智慧养殖平台</span>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="9 18 15 12 9 6"/></svg>
            <span class="bc-current">{{ pageLabels[currentPage] }}</span>
          </nav>
        </div>
        <div class="header-right">
          <div class="user-chip">
            <div class="user-avatar">{{ currentUser.username?.[0]?.toUpperCase() }}</div>
            <span class="user-name">{{ currentUser.username }}</span>
          </div>
          <button class="btn btn-secondary btn-sm" @click="onLogout">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
            退出
          </button>
        </div>
      </header>

      <!-- Main content -->
      <main class="app-main">
        <Dashboard          v-if="currentPage === 'dashboard'"    @switch-page="p => { if (hasAccess(p, currentUser?.role)) currentPage = p }" @alert="showAlert" />
        <PenManagement          v-if="currentPage === 'pens'"          @alert="showAlert" />
        <DrugVaccineManagement  v-if="currentPage === 'drugs'"         @alert="showAlert" />
        <BatchManagement        v-if="currentPage === 'batches'"       @alert="showAlert" />
        <AnimalManagement       v-if="currentPage === 'animals'"       @alert="showAlert" />
        <ImmunizationManagement v-if="currentPage === 'immunization'"  @alert="showAlert" />
        <MedicationManagement   v-if="currentPage === 'medication'"    @alert="showAlert" />
        <PenTransferManagement  v-if="currentPage === 'transfer'"      @alert="showAlert" />
        <SlaughterManagement    v-if="currentPage === 'slaughter'"     @alert="showAlert" />
        <TraceabilityView       v-if="currentPage === 'traceability'"  @alert="showAlert" />
        <UserManagement         v-if="currentPage === 'users'"         @alert="showAlert" />
      </main>
    </div>
  </div>

  <GlobalAlert :msg="alertMsg" :type="alertType" :visible="alertVisible" />
</template>

<style>
/* Layout shell */
.app-shell {
  display: flex;
  min-height: 100vh;
}

.app-body {
  flex: 1;
  margin-left: var(--sidebar-w);
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  transition: margin-left .2s ease;
  min-width: 0;
}

.app-shell.collapsed .app-body {
  margin-left: var(--sidebar-collapsed-w);
}

/* Header */
.app-header {
  position: sticky;
  top: 0;
  z-index: 40;
  height: var(--header-h);
  background: var(--c-surface);
  border-bottom: 1px solid var(--c-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toggle-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--c-border);
  border-radius: var(--r);
  background: var(--c-surface);
  color: var(--c-text-2);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all .15s;
  flex-shrink: 0;
}
.toggle-btn:hover { background: var(--c-bg); color: var(--c-text); }

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.bc-root  { color: var(--c-text-3); }
.bc-root svg { color: var(--c-text-3); }
.bc-current { color: var(--c-text); font-weight: 500; }

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--c-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--c-text);
}

/* Main content */
.app-main {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
}
</style>
