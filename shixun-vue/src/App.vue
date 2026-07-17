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
import MarketingDemandPage from './components/MarketingDemandPage.vue'
import MarketingAssistant from './components/MarketingAssistant.vue'
import ProjectDemandPage from './components/ProjectDemandPage.vue'
import HumanResourcePage from './components/HumanResourcePage.vue'
import AttendanceManagementPage from './components/AttendanceManagementPage.vue'
import SupplierList from './components/SupplierList.vue'
import SampleWorkOrderPage from './components/SampleWorkOrderPage.vue'
import SampleApplicationPage from './components/SampleApplicationPage.vue'
import BulkProductionWorkOrderPage from './components/BulkProductionWorkOrderPage.vue'
import BulkProductionApplicationPage from './components/BulkProductionApplicationPage.vue'
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
  marketDemand:STAFF_WORKFLOW_ROLES,
  marketPromotion:STAFF_WORKFLOW_ROLES,
  marketEcommerceNewProduct:STAFF_WORKFLOW_ROLES,
  marketShooting:STAFF_WORKFLOW_ROLES,
  marketProductCopy:STAFF_WORKFLOW_ROLES,
  projectDemand:STAFF_WORKFLOW_ROLES,
  projectInitiation:STAFF_WORKFLOW_ROLES,
  projectInquiry:STAFF_WORKFLOW_ROLES,
  hrManagement:STAFF_WORKFLOW_ROLES,
  hrNewProductIncentive:STAFF_WORKFLOW_ROLES,
  hrResignation:STAFF_WORKFLOW_ROLES,
  hrTraining:STAFF_WORKFLOW_ROLES,
  hrHolidayOvertime:STAFF_WORKFLOW_ROLES,
  hrTransfer:STAFF_WORKFLOW_ROLES,
  hrPolicyApproval:STAFF_WORKFLOW_ROLES,
  hrRegularization:STAFF_WORKFLOW_ROLES,
  hrRecruitment:STAFF_WORKFLOW_ROLES,
  attendanceManagement:STAFF_WORKFLOW_ROLES,
  attendanceCardRepair:STAFF_WORKFLOW_ROLES,
  attendanceLeave:STAFF_WORKFLOW_ROLES,
  attendanceBusinessTrip:STAFF_WORKFLOW_ROLES,
  attendanceOutgoing:STAFF_WORKFLOW_ROLES,
  supplierList:STAFF_WORKFLOW_ROLES,
  sampleWorkOrders:STAFF_WORKFLOW_ROLES,
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
  createProductionProject:STAFF_WORKFLOW_ROLES,
  production:   STAFF_WORKFLOW_ROLES,
  sampleApplication:STAFF_WORKFLOW_ROLES,
  sampleProduction:STAFF_WORKFLOW_ROLES,
  bulkProductionApplication:STAFF_WORKFLOW_ROLES,
  bulkProductionWorkOrders:STAFF_WORKFLOW_ROLES,
  bulkProduction:STAFF_WORKFLOW_ROLES,
  logistics:    MANAGER_ROLES,
  warehouseLogistics:MANAGER_ROLES,
  warehouseProducts:MANAGER_ROLES,
  warehouseInventory:MANAGER_ROLES,
  warehouseInbound:MANAGER_ROLES,
  warehouseOutbound:MANAGER_ROLES,
  warehousePick:MANAGER_ROLES,
  warehouseAlerts:MANAGER_ROLES,
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
  marketDemand:'市场部需求管理',
  marketPromotion:'营销宣传申请',
  marketEcommerceNewProduct:'电商新品上架申请',
  marketShooting:'拍摄需求申请',
  marketProductCopy:'产品宣传文案',
  projectDemand:'项目部需求管理',
  projectInitiation:'项目立项申请',
  projectInquiry:'项目询价申请',
  hrManagement:'人力资源管理',
  hrNewProductIncentive:'新产品开发激励',
  hrResignation:'离职申请',
  hrTraining:'培训申请',
  hrHolidayOvertime:'加班申请【法定节假日】',
  hrTransfer:'调岗申请',
  hrPolicyApproval:'制度&方案审批',
  hrRegularization:'转正申请',
  hrRecruitment:'招聘申请',
  attendanceManagement:'考勤管理',
  attendanceCardRepair:'补卡申请',
  attendanceLeave:'请假申请',
  attendanceBusinessTrip:'出差申请',
  attendanceOutgoing:'外出申请',
  supplierList:'供应商列表',
  sampleWorkOrders:'供应链打样工单明细',
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
  createProductionProject:'创建项目',
  production:   '智能成本核算引擎',
  sampleApplication:'打样申请',
  sampleProduction:'产品打样管理',
  bulkProductionApplication:'大货生产申请',
  bulkProductionWorkOrders:'大货工单明细',
  bulkProduction:'大货生产管理',
  logistics:    '物流跟踪',
  warehouseLogistics:'产品库存与物流管理',
  warehouseProducts:'产品主数据库',
  warehouseInventory:'库存台账',
  warehouseInbound:'入库管理',
  warehouseOutbound:'出库管理',
  warehousePick:'拣货任务',
  warehouseAlerts:'库存预警',
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
        <MarketingDemandPage v-if="currentPage === 'marketDemand'" type="home" :current-user="currentUser" @alert="showAlert" />
        <MarketingDemandPage v-if="currentPage === 'marketPromotion'" type="promotion" :current-user="currentUser" @alert="showAlert" />
        <MarketingDemandPage v-if="currentPage === 'marketEcommerceNewProduct'" type="ecommerceNewProduct" :current-user="currentUser" @alert="showAlert" />
        <MarketingDemandPage v-if="currentPage === 'marketShooting'" type="shooting" :current-user="currentUser" @alert="showAlert" />
        <MarketingAssistant v-if="currentPage === 'marketProductCopy'" @alert="showAlert" />
        <ProjectDemandPage v-if="currentPage === 'projectDemand'" type="home" :current-user="currentUser" @alert="showAlert" />
        <ProjectDemandPage v-if="currentPage === 'projectInitiation'" type="initiation" :current-user="currentUser" @alert="showAlert" />
        <ProjectDemandPage v-if="currentPage === 'projectInquiry'" type="inquiry" :current-user="currentUser" @alert="showAlert" />
        <HumanResourcePage v-if="currentPage === 'hrManagement'" type="home" :current-user="currentUser" @alert="showAlert" />
        <HumanResourcePage v-if="currentPage === 'hrNewProductIncentive'" type="newProductIncentive" :current-user="currentUser" @alert="showAlert" />
        <HumanResourcePage v-if="currentPage === 'hrResignation'" type="resignation" :current-user="currentUser" @alert="showAlert" />
        <HumanResourcePage v-if="currentPage === 'hrTraining'" type="training" :current-user="currentUser" @alert="showAlert" />
        <HumanResourcePage v-if="currentPage === 'hrHolidayOvertime'" type="holidayOvertime" :current-user="currentUser" @alert="showAlert" />
        <HumanResourcePage v-if="currentPage === 'hrTransfer'" type="transfer" :current-user="currentUser" @alert="showAlert" />
        <HumanResourcePage v-if="currentPage === 'hrPolicyApproval'" type="policyApproval" :current-user="currentUser" @alert="showAlert" />
        <HumanResourcePage v-if="currentPage === 'hrRegularization'" type="regularization" :current-user="currentUser" @alert="showAlert" />
        <HumanResourcePage v-if="currentPage === 'hrRecruitment'" type="recruitment" :current-user="currentUser" @alert="showAlert" />
        <AttendanceManagementPage v-if="currentPage === 'attendanceManagement'" type="home" :current-user="currentUser" @alert="showAlert" />
        <AttendanceManagementPage v-if="currentPage === 'attendanceCardRepair'" type="cardRepair" :current-user="currentUser" @alert="showAlert" />
        <AttendanceManagementPage v-if="currentPage === 'attendanceLeave'" type="leave" :current-user="currentUser" @alert="showAlert" />
        <AttendanceManagementPage v-if="currentPage === 'attendanceBusinessTrip'" type="businessTrip" :current-user="currentUser" @alert="showAlert" />
        <AttendanceManagementPage v-if="currentPage === 'attendanceOutgoing'" type="outgoing" :current-user="currentUser" @alert="showAlert" />
        <SupplierList v-if="currentPage === 'supplierList'" />
        <SampleWorkOrderPage v-if="currentPage === 'sampleWorkOrders'" />
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
        <ProductionManagement v-if="currentPage === 'createProductionProject'" initial-view="project" :current-user="currentUser" @alert="showAlert" @switch-page="p => { if (hasAccess(p, currentUser?.role)) currentPage = p as PageName }" />
        <ProductionManagement v-if="currentPage === 'production'" initial-view="cost" :current-user="currentUser" @alert="showAlert" />
        <SampleApplicationPage v-if="currentPage === 'sampleApplication'" :current-user="currentUser" @alert="showAlert" />
        <ProductionManagement v-if="currentPage === 'sampleProduction'" initial-view="sample" :current-user="currentUser" @alert="showAlert" />
        <BulkProductionApplicationPage v-if="currentPage === 'bulkProductionApplication'" :current-user="currentUser" @alert="showAlert" />
        <BulkProductionWorkOrderPage v-if="currentPage === 'bulkProductionWorkOrders'" />
        <ProductionManagement v-if="currentPage === 'bulkProduction'" initial-view="bulk" :current-user="currentUser" @alert="showAlert" />
        <LogisticsTracking    v-if="currentPage === 'logistics'"  @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehouseLogistics'" initial-view="products" :show-tabs="false" @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehouseProducts'" initial-view="products" :show-tabs="false" @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehouseInventory'" initial-view="inventory" :show-tabs="false" @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehouseInbound'" initial-view="inbound" :show-tabs="false" @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehouseOutbound'" initial-view="outbound" :show-tabs="false" @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehousePick'" initial-view="pick" :show-tabs="false" @alert="showAlert" />
        <WarehouseManagement v-if="currentPage === 'warehouseAlerts'" initial-view="alerts" :show-tabs="false" @alert="showAlert" />
        <DesignerCenter       v-if="currentPage === 'designers'"   @alert="showAlert" />
        <UserManagement       v-if="currentPage === 'users'"       :current-user="currentUser" @alert="showAlert" />
      </main>
    </div>
  </div>

  <GlobalAlert :msg="alertMsg" :type="alertType" :visible="alertVisible" />
  <AiChat v-if="currentUser" :current-user="currentUser" />
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
