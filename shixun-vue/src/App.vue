<script setup lang="ts">
import { ref, onMounted, watchEffect } from 'vue'
import type { User, PageName, AlertType, Role } from './types'
import LoginPage from './components/LoginPage.vue'
import Sidebar from './components/Sidebar.vue'
import CreativeDashboard from './components/CreativeDashboard.vue'
import CreativeStudio from './components/CreativeStudio.vue'
import ScaleUpPlatform from './components/ScaleUpPlatform.vue'
import ProductionManagement from './components/ProductionManagement.vue'
import LogisticsTracking from './components/LogisticsTracking.vue'
import WarehouseManagement from './components/WarehouseManagement.vue'
import DesignerCenter from './components/DesignerCenter.vue'
import UserManagement from './components/UserManagement.vue'
import ChainApplicationPage from './components/ChainApplicationPage.vue'
import FinanceApplicationPage from './components/FinanceApplicationPage.vue'
import ApprovalCenter from './components/ApprovalCenter.vue'
import NotificationPanel from './components/NotificationPanel.vue'
import GlobalAlert from './components/GlobalAlert.vue'
import AiChat from './components/AiChat.vue'

// 角色兼容说明：
// admin      = 超级管理员：拥有全部功能，包括账号权限、审批和系统配置
// technician = 审批主管：可查看业务模块并处理审批，但不能管理账号角色
// feeder     = 员工：可制作内容、发起/提交申请，不能审批和管理账号
const ALL_ROLES: Role[] = ['admin', 'technician', 'feeder']
const MANAGER_ROLES: Role[] = ['admin', 'technician']
const STAFF_WORKFLOW_ROLES: Role[] = ['admin', 'technician', 'feeder']
const SUPER_ADMIN_ROLES: Role[] = ['admin']

const PAGE_ROLES: Record<string, Role[]> = {
  dashboard:    ALL_ROLES,
  approvalCenter:MANAGER_ROLES,
  studio:       STAFF_WORKFLOW_ROLES,
  creative2d:   STAFF_WORKFLOW_ROLES,
  creative3d:   STAFF_WORKFLOW_ROLES,
  creativeReview:MANAGER_ROLES,
  chain:        STAFF_WORKFLOW_ROLES,
  chainMarketing:STAFF_WORKFLOW_ROLES,
  chainNewProduct:STAFF_WORKFLOW_ROLES,
  chainPriceAdjust:STAFF_WORKFLOW_ROLES,
  finance:      STAFF_WORKFLOW_ROLES,
  financeAssetScrap:STAFF_WORKFLOW_ROLES,
  financePublicPayment:STAFF_WORKFLOW_ROLES,
  financePettyCash:STAFF_WORKFLOW_ROLES,
  financePersonalExpense:STAFF_WORKFLOW_ROLES,
  financePromotionApproval:MANAGER_ROLES,
  financeSeal:STAFF_WORKFLOW_ROLES,
  financePettyCashRepay:STAFF_WORKFLOW_ROLES,
  financeTravel:STAFF_WORKFLOW_ROLES,
  financeInvoice:STAFF_WORKFLOW_ROLES,
  financeSpecialExpense:STAFF_WORKFLOW_ROLES,
  financePettyCashWriteoff:STAFF_WORKFLOW_ROLES,
  scaleUp:      STAFF_WORKFLOW_ROLES,
  production:   MANAGER_ROLES,
  sampleProduction:STAFF_WORKFLOW_ROLES,
  bulkProduction:STAFF_WORKFLOW_ROLES,
  logistics:    MANAGER_ROLES,
  warehouseLogistics:MANAGER_ROLES,
  warehouse:    MANAGER_ROLES,
  designers:    MANAGER_ROLES,
  users:        SUPER_ADMIN_ROLES,
}

function hasAccess(page: string, role?: Role): boolean {
  return (PAGE_ROLES[page] ?? ['admin']).includes(role || 'admin')
}

function firstAllowedPage(role: Role): PageName {
  return (Object.keys(PAGE_ROLES).find(p => hasAccess(p, role)) || 'marketplace') as PageName
}

const currentUser = ref<User | null>(null)
const currentPage = ref<PageName>('dashboard')
const sidebarCollapsed = ref<boolean>(false)
const alertMsg = ref<string>('')
const alertType = ref<AlertType>('success')
const alertVisible = ref<boolean>(false)
let alertTimer: ReturnType<typeof setTimeout> | null = null

onMounted(() => {
  const saved = sessionStorage.getItem('currentUser')
  if (saved) currentUser.value = JSON.parse(saved) as User
})

watchEffect(() => {
  if (!currentUser.value) return
  const role = currentUser.value.role || 'admin'
  if (!hasAccess(currentPage.value, role)) currentPage.value = firstAllowedPage(role)
})

function showAlert(msg: string, type: AlertType = 'success'): void {
  alertMsg.value = msg
  alertType.value = type
  alertVisible.value = true
  if (alertTimer) clearTimeout(alertTimer)
  alertTimer = setTimeout(() => { alertVisible.value = false }, 3000)
}

function onLogin(user: User): void {
  currentUser.value = user
  sessionStorage.setItem('currentUser', JSON.stringify(user))
  currentPage.value = 'dashboard'
}

function onLogout(): void {
  currentUser.value = null
  sessionStorage.removeItem('currentUser')
}

const pageLabels: Record<string, string> = {
  dashboard:    '经营看板',
  approvalCenter:'审批中心',
  studio:       '创意设计',
  creative2d:   '2D创意生图',
  creative3d:   '3D辅助建模',
  creativeReview:'智能评估',
  chain:        '之间连锁',
  chainMarketing:'门店营销方案申请【连锁】',
  chainNewProduct:'新商品上架申请【连锁】',
  chainPriceAdjust:'商品售价调整申请【连锁】',
  finance:      '财务管理',
  financeAssetScrap:'固定资产报废申请',
  financePublicPayment:'对公付款申请(供应链)',
  financePettyCash:'备用金申请',
  financePersonalExpense:'个人费用报销',
  financePromotionApproval:'促销活动审批',
  financeSeal:'用章用印申请',
  financePettyCashRepay:'备用金还款',
  financeTravel:'差旅报销',
  financeInvoice:'开票申请',
  financeSpecialExpense:'费用报销(特殊事项)',
  financePettyCashWriteoff:'备用金核销',
  scaleUp:      '生产管理',
  production:   '智能成本核算引擎',
  sampleProduction:'产品打样管理',
  bulkProduction:'大货生产管理',
  logistics:    '物流跟踪',
  warehouseLogistics:'仓储与物流管理',
  warehouse:    '智能库存预警与出入库',
  designers:    '设计师/创作者',
  users:        '账号权限',
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
            <span class="bc-current">{{ pageLabels[currentPage] }}</span>
          </nav>
        </div>
        <div class="header-center-title">之间味道-文创产品智能体平台</div>
        <div class="header-right">
          <NotificationPanel />
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
        <CreativeDashboard    v-if="currentPage === 'dashboard'"   @switch-page="p => { if (hasAccess(p, currentUser?.role)) currentPage = p as PageName }" @alert="showAlert" />
        <ApprovalCenter v-if="currentPage === 'approvalCenter'" :current-user="currentUser" @alert="showAlert" />
        <CreativeStudio v-if="currentPage === 'studio'" initial-view="image2d" @alert="showAlert" />
        <CreativeStudio v-if="currentPage === 'creative2d'" initial-view="image2d" @alert="showAlert" />
        <CreativeStudio v-if="currentPage === 'creative3d'" initial-view="model3d" @alert="showAlert" />
        <CreativeStudio v-if="currentPage === 'creativeReview'" initial-view="review" @alert="showAlert" />
        <ChainApplicationPage v-if="currentPage === 'chain'" type="home" :current-user="currentUser" @alert="showAlert" />
        <ChainApplicationPage v-if="currentPage === 'chainMarketing'" type="marketing" :current-user="currentUser" @alert="showAlert" />
        <ChainApplicationPage v-if="currentPage === 'chainNewProduct'" type="newProduct" :current-user="currentUser" @alert="showAlert" />
        <ChainApplicationPage v-if="currentPage === 'chainPriceAdjust'" type="priceAdjust" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'finance'" type="home" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financeAssetScrap'" type="assetScrap" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financePublicPayment'" type="publicPayment" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financePettyCash'" type="pettyCash" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financePersonalExpense'" type="personalExpense" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financePromotionApproval'" type="promotionApproval" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financeSeal'" type="seal" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financePettyCashRepay'" type="pettyCashRepay" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financeTravel'" type="travel" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financeInvoice'" type="invoice" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financeSpecialExpense'" type="specialExpense" :current-user="currentUser" @alert="showAlert" />
        <FinanceApplicationPage v-if="currentPage === 'financePettyCashWriteoff'" type="pettyCashWriteoff" :current-user="currentUser" @alert="showAlert" />
        <ScaleUpPlatform     v-if="currentPage === 'scaleUp'" @alert="showAlert" />
        <ProductionManagement v-if="currentPage === 'production'" initial-view="cost" @alert="showAlert" />
        <ProductionManagement v-if="currentPage === 'sampleProduction'" initial-view="sample" @alert="showAlert" />
        <ProductionManagement v-if="currentPage === 'bulkProduction'" initial-view="bulk" @alert="showAlert" />
        <LogisticsTracking    v-if="currentPage === 'logistics'"  @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehouseLogistics'" initial-view="inventory" @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehouse'" initial-view="alerts" @alert="showAlert" />
        <DesignerCenter       v-if="currentPage === 'designers'"   @alert="showAlert" />
        <UserManagement       v-if="currentPage === 'users'"       :current-user="currentUser" @alert="showAlert" />
      </main>
    </div>
  </div>

  <GlobalAlert :msg="alertMsg" :type="alertType" :visible="alertVisible" />
  <AiChat v-if="currentUser" />
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


.header-center-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  max-width: min(520px, 42vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--c-text);
  font-size: 15px;
  font-weight: 800;
  letter-spacing: .04em;
}

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
