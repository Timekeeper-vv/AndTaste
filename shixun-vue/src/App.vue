<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, watchEffect } from 'vue'
import type { User, PageName, AlertType, Role, AuthSession } from './types'
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
import AiAssistantPage from './components/AiAssistantPage.vue'
import CustomerServiceDesk from './components/CustomerServiceDesk.vue'
import ConsumerConversationPage from './components/ConsumerConversationPage.vue'
import ConsumerWorksReview from './components/ConsumerWorksReview.vue'
import ConsumerAssetInventory from './components/ConsumerAssetInventory.vue'
import ConsumerCreditManagement from './components/ConsumerCreditManagement.vue'
import PaymentOperations from './components/PaymentOperations.vue'
import HistoricalSalesManagement from './components/HistoricalSalesManagement.vue'
import ConsumerProductionReview from './components/ConsumerProductionReview.vue'
import OrderManagement from './components/OrderManagement.vue'
import CommercialProductization from './components/CommercialProductization.vue'
import { isEmbeddedMiniapp, notifyMiniapp } from './utils/miniappBridge'

// 角色兼容说明：
// admin      = 超级管理员：拥有全部功能，包括账号权限、审批和系统配置
// technician = 审批主管：可查看业务模块并处理审批，但不能管理账号角色
// feeder     = 员工：可制作内容、发起/提交申请，不能审批和管理账号
// designer   = 设计师：仅可使用创意设计下的 2D、3D、智能评估三个功能
// user       = C端用户：仅进入手机端轻量创作界面
const ALL_ROLES: Role[] = ['admin', 'technician', 'feeder']
const MANAGER_ROLES: Role[] = ['admin', 'technician']
const STAFF_WORKFLOW_ROLES: Role[] = ['admin', 'technician', 'feeder']
const SUPER_ADMIN_ROLES: Role[] = ['admin']
const CREATIVE_DESIGN_ROLES: Role[] = ['admin', 'technician', 'feeder', 'designer']
const CONSUMER_ROLES: Role[] = ['user']

const PAGE_ROLES: Record<string, Role[]> = {
  consumerMobile:CONSUMER_ROLES,
  dashboard:    ALL_ROLES,
  approvalCenter:MANAGER_ROLES,
  consumerWorksReview:SUPER_ADMIN_ROLES,
  professionalWorksReview:SUPER_ADMIN_ROLES,
  multiviewReview:SUPER_ADMIN_ROLES,
  consumerAssetInventory:SUPER_ADMIN_ROLES,
  consumerCreditManagement:SUPER_ADMIN_ROLES,
  paymentOperations:SUPER_ADMIN_ROLES,
  historicalSales:SUPER_ADMIN_ROLES,
  consumerProductionReview:SUPER_ADMIN_ROLES,
  orderManagement:SUPER_ADMIN_ROLES,
  commercialProductization:MANAGER_ROLES,
  professionalGuidance:MANAGER_ROLES,
  aiAssistant:  ALL_ROLES,
  customerService: ALL_ROLES,
  studio:       STAFF_WORKFLOW_ROLES,
  creative2d:   CREATIVE_DESIGN_ROLES,
  creative3d:   CREATIVE_DESIGN_ROLES,
  creativeReview:CREATIVE_DESIGN_ROLES,
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
  return (Object.keys(PAGE_ROLES).find(p => hasAccess(p, role)) || 'dashboard') as PageName
}

const currentUser = ref<User | null>(null)
const currentPage = ref<PageName>('dashboard')
type ConsumerDevice = 'mobile' | 'desktop'
const CONSUMER_DEVICE_STORAGE_KEY = 'smart_pig_consumer_device'
const consumerDevice = ref<ConsumerDevice | null>(null)
const consumerDevicePickerOpen = ref(false)
const sidebarCollapsed = ref<boolean>(false)
const alertMsg = ref<string>('')
const alertType = ref<AlertType>('success')
const alertVisible = ref<boolean>(false)
let alertTimer: ReturnType<typeof setTimeout> | null = null

const shouldChooseConsumerDevice = computed(() =>
  Boolean(currentUser.value?.role === 'user' && consumerDevicePickerOpen.value),
)

function getSavedConsumerDevice(): ConsumerDevice | null {
  try {
    const value = localStorage.getItem(CONSUMER_DEVICE_STORAGE_KEY)
    return value === 'mobile' || value === 'desktop' ? value : null
  } catch {
    return null
  }
}

function prepareConsumerDeviceSelection(user: User, options: { askAgain: boolean }): void {
  if (user.role !== 'user') {
    consumerDevice.value = null
    consumerDevicePickerOpen.value = false
    return
  }

  // A mini-program web-view is always a touch-first surface.  It must never
  // restore a desktop preference left by the same account in a browser.
  if (isEmbeddedMiniapp()) {
    consumerDevice.value = 'mobile'
    consumerDevicePickerOpen.value = false
    return
  }

  const savedDevice = options.askAgain ? null : getSavedConsumerDevice()
  consumerDevice.value = savedDevice
  consumerDevicePickerOpen.value = !savedDevice
}

function chooseConsumerDevice(device: ConsumerDevice): void {
  consumerDevice.value = device
  consumerDevicePickerOpen.value = false
  try {
    localStorage.setItem(CONSUMER_DEVICE_STORAGE_KEY, device)
  } catch {
    // Storage can be disabled by a browser privacy setting; the selected
    // layout still applies to the current session.
  }
}

async function restoreSession(): Promise<void> {
  const token = sessionStorage.getItem('accessToken')
  if (!token) return
  try {
    const response = await fetch('/api/auth/me', { cache: 'no-store' })
    if (!response.ok) throw new Error('session invalid')
    const data = await response.json()
    if (isEmbeddedMiniapp() && data.user?.role !== 'user') {
      // The embedded entry is intentionally a consumer-only surface.  Do not
      // let a cached staff/admin JWT turn the mini-program into a back-office
      // console just because the same H5 bundle serves both audiences.
      sessionStorage.removeItem('accessToken')
      sessionStorage.removeItem('currentUser')
      localStorage.removeItem('accessToken')
      localStorage.removeItem('currentUser')
      showAlert('微信小程序仅支持用户端账号，请使用 user 账号登录', 'error')
      return
    }
    currentUser.value = data.user as User
    sessionStorage.setItem('currentUser', JSON.stringify(data.user))
    currentPage.value = firstAllowedPage(data.user.role || 'admin')
    prepareConsumerDeviceSelection(data.user as User, { askAgain: false })
  } catch {
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('currentUser')
    if (isEmbeddedMiniapp()) {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('currentUser')
    }
  }
}

onMounted(() => {
  restoreSession()
  window.addEventListener('auth-expired', onLogout)
})

onBeforeUnmount(() => window.removeEventListener('auth-expired', onLogout))

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

function onLogin(session: AuthSession): void {
  if (isEmbeddedMiniapp() && session.user?.role !== 'user') {
    sessionStorage.removeItem('accessToken')
    sessionStorage.removeItem('currentUser')
    localStorage.removeItem('accessToken')
    localStorage.removeItem('currentUser')
    notifyMiniapp('AUTH_LOGOUT')
    showAlert('微信小程序仅支持用户端账号，请使用 user 账号登录', 'error')
    return
  }
  currentUser.value = session.user
  sessionStorage.setItem('accessToken', session.token)
  sessionStorage.setItem('currentUser', JSON.stringify(session.user))
  if (isEmbeddedMiniapp()) {
    localStorage.setItem('accessToken', session.token)
    localStorage.setItem('currentUser', JSON.stringify(session.user))
  }
  currentPage.value = firstAllowedPage(session.user.role || 'admin')
  prepareConsumerDeviceSelection(session.user, { askAgain: true })
}

function onLogout(): void {
  // If this H5 surface is embedded by the mini-program, let the native shell
  // clear its own session as well.  Browser sessionStorage is isolated from
  // `uni` storage, so clearing only one side would otherwise re-inject a stale
  // token the next time the web-view is opened.
  notifyMiniapp('AUTH_LOGOUT')
  currentUser.value = null
  consumerDevice.value = null
  consumerDevicePickerOpen.value = false
  sessionStorage.removeItem('accessToken')
  sessionStorage.removeItem('currentUser')
  localStorage.removeItem('accessToken')
  localStorage.removeItem('currentUser')
}

const pageLabels: Record<string, string> = {
  consumerMobile:'文创灵感工坊',
  dashboard:    '经营看板',
  approvalCenter:'审批中心',
  consumerWorksReview:'C端作品审核',
  professionalWorksReview:'专业作品审核',
  multiviewReview:'多视图审核',
  consumerAssetInventory:'C端用户端库存',
  consumerCreditManagement:'C端额度管理',
  paymentOperations:'支付运营',
  historicalSales:'历史销售数据',
  consumerProductionReview:'3D建模审核',
  orderManagement:'订单管理',
  commercialProductization:'商品化与代销审核',
  professionalGuidance:'专业指导',
  aiAssistant:  '之间味道AI助手',
  customerService:'C端客服会话',
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

  <section v-else-if="shouldChooseConsumerDevice" class="consumer-device-picker" role="dialog" aria-modal="true" aria-labelledby="consumer-device-picker-title">
    <div class="consumer-device-picker-card">
      <span class="consumer-device-picker-kicker">WELCOME TO BETWEEN TASTE</span>
      <h1 id="consumer-device-picker-title">这次，想在哪个设备上创作？</h1>
      <p>选择适合当前设备的工作方式。手机端为触控与单列阅读优化，电脑端保留完整的大屏创作工作台。</p>
      <div class="consumer-device-options">
        <button type="button" class="consumer-device-option mobile" @click="chooseConsumerDevice('mobile')">
          <span class="consumer-device-icon" aria-hidden="true">⌁</span>
          <strong>手机端</strong>
          <small>适合随时记录灵感、触控创作和单手浏览。</small>
          <em>移动优先 <b>→</b></em>
        </button>
        <button type="button" class="consumer-device-option desktop" @click="chooseConsumerDevice('desktop')">
          <span class="consumer-device-icon" aria-hidden="true">▣</span>
          <strong>电脑端</strong>
          <small>适合大屏查看作品、精细编辑和键鼠操作。</small>
          <em>完整工作台 <b>→</b></em>
        </button>
      </div>
      <small class="consumer-device-picker-note">下次登录会再次询问；刷新页面会保留本次选择。</small>
    </div>
  </section>

  <ConsumerConversationPage
    v-else-if="currentUser.role === 'user' && consumerDevice"
    :class="`consumer-device-${consumerDevice}`"
    :current-user="currentUser"
    @alert="showAlert"
    @logout="onLogout"
  />

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
        <ConsumerWorksReview v-if="currentPage === 'consumerWorksReview'" :current-user="currentUser" @alert="showAlert" />
        <ConsumerWorksReview v-if="currentPage === 'professionalWorksReview'" :current-user="currentUser" mode="professional" @alert="showAlert" />
        <ConsumerWorksReview v-if="currentPage === 'multiviewReview'" :current-user="currentUser" mode="multiview" @alert="showAlert" />
        <ConsumerAssetInventory v-if="currentPage === 'consumerAssetInventory'" :current-user="currentUser" @alert="showAlert" />
        <ConsumerCreditManagement v-if="currentPage === 'consumerCreditManagement'" :current-user="currentUser" @alert="showAlert" />
        <PaymentOperations v-if="currentPage === 'paymentOperations'" :current-user="currentUser" @alert="showAlert" />
        <HistoricalSalesManagement v-if="currentPage === 'historicalSales'" :current-user="currentUser" @alert="showAlert" />
        <ConsumerWorksReview v-if="currentPage === 'consumerProductionReview'" :current-user="currentUser" mode="standard" @alert="showAlert" />
        <OrderManagement v-if="currentPage === 'orderManagement'" />
        <CommercialProductization v-if="currentPage === 'commercialProductization'" :current-user="currentUser" @alert="showAlert" />
        <CommercialProductization v-if="currentPage === 'professionalGuidance'" :current-user="currentUser" initial-tab="guidance" guidance-only @alert="showAlert" />
        <AiAssistantPage v-if="currentPage === 'aiAssistant'" :current-user="currentUser" />
        <CustomerServiceDesk v-if="currentPage === 'customerService'" :current-user="currentUser" @alert="showAlert" />
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
  <AiChat v-if="currentUser && currentUser.role !== 'user' && currentPage !== 'aiAssistant'" :current-user="currentUser" />
</template>

<style>
/* Consumer device choice stays outside the component so it can be shown before
   the consumer workspace is mounted. */
.consumer-device-picker {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px;
  overflow: auto;
  background:
    radial-gradient(circle at 13% 10%, rgba(154, 185, 167, .34), transparent 28%),
    radial-gradient(circle at 88% 88%, rgba(190, 105, 76, .22), transparent 29%),
    linear-gradient(145deg, #f5f0e8, #e7ddd1);
}

.consumer-device-picker-card {
  width: min(700px, 100%);
  padding: clamp(24px, 5vw, 42px);
  border: 1px solid rgba(255, 255, 255, .85);
  border-radius: 30px;
  background: rgba(255, 253, 249, .88);
  box-shadow: 0 28px 76px rgba(61, 45, 34, .16);
  color: #3c352e;
  backdrop-filter: blur(18px);
}

.consumer-device-picker-kicker {
  display: block;
  color: #638071;
  font-size: 10px;
  font-weight: 900;
  letter-spacing: .16em;
}

.consumer-device-picker h1 {
  max-width: 14ch;
  margin: 10px 0 9px;
  font-family: "Songti SC", STSong, serif;
  font-size: clamp(27px, 4vw, 40px);
  line-height: 1.2;
  letter-spacing: -.045em;
}

.consumer-device-picker-card > p {
  max-width: 53ch;
  margin: 0;
  color: #81766b;
  font-size: 14px;
  line-height: 1.75;
}

.consumer-device-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 26px;
}

.consumer-device-option {
  display: grid;
  min-height: 226px;
  padding: 21px;
  border: 1px solid #e5ddd2;
  border-radius: 21px;
  background: #fffdfa;
  color: #443c34;
  cursor: pointer;
  text-align: left;
  transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
}

.consumer-device-option:hover {
  transform: translateY(-4px);
  border-color: #a8beaf;
  box-shadow: 0 18px 30px rgba(62, 78, 66, .12);
}

.consumer-device-option.desktop {
  border-color: #ded4c8;
  background: linear-gradient(145deg, #fffcf7, #f5ebe1);
}

.consumer-device-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 13px;
  background: #eaf2eb;
  color: #597665;
  font-size: 22px;
  font-weight: 800;
}

.consumer-device-option.desktop .consumer-device-icon {
  background: #f4e8dd;
  color: #9c604a;
}

.consumer-device-option strong {
  margin-top: 20px;
  font-family: "Songti SC", STSong, serif;
  font-size: 24px;
  font-weight: 650;
}

.consumer-device-option small {
  margin-top: 8px;
  color: #867a6d;
  font-size: 12px;
  line-height: 1.65;
}

.consumer-device-option em {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-top: auto;
  padding-top: 18px;
  color: #587261;
  font-size: 11px;
  font-style: normal;
  font-weight: 900;
}

.consumer-device-option.desktop em { color: #9c604a; }
.consumer-device-option em b { font-size: 18px; font-weight: 500; }

.consumer-device-picker-note {
  display: block;
  margin-top: 17px;
  color: #9a8d80;
  font-size: 11px;
}

@media (max-width: 640px) {
  .consumer-device-picker { align-items: end; padding: 12px; }
  .consumer-device-picker-card { padding: 25px 20px calc(25px + env(safe-area-inset-bottom, 0px)); border-radius: 26px; }
  .consumer-device-picker h1 { max-width: 12ch; font-size: 29px; }
  .consumer-device-picker-card > p { font-size: 13px; }
  .consumer-device-options { grid-template-columns: 1fr; gap: 10px; margin-top: 19px; }
  .consumer-device-option { grid-template-columns: 42px 1fr; column-gap: 13px; min-height: 0; padding: 15px; }
  .consumer-device-option strong { align-self: center; margin: 0; font-size: 21px; }
  .consumer-device-option small, .consumer-device-option em { grid-column: 1 / -1; }
  .consumer-device-option small { margin-top: 8px; }
  .consumer-device-option em { margin-top: 0; padding-top: 8px; }
}

/* Layout shell */
.app-shell {
  display: flex;
  min-height: 100vh;
  background:
    radial-gradient(circle at 20% 0%, rgba(20,184,166,.13), transparent 26%),
    radial-gradient(circle at 88% 8%, rgba(124,58,237,.10), transparent 30%),
    linear-gradient(180deg, #f8fbff 0%, #eef4ff 100%);
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
  background: rgba(255,255,255,.72);
  border-bottom: 1px solid rgba(148,163,184,.20);
  box-shadow: 0 10px 34px rgba(15,23,42,.06);
  backdrop-filter: blur(22px) saturate(160%);
  -webkit-backdrop-filter: blur(22px) saturate(160%);
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
  width: 36px;
  height: 36px;
  border: 1px solid var(--c-border);
  border-radius: 12px;
  background: rgba(255,255,255,.76);
  color: var(--c-text-2);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all .15s;
  flex-shrink: 0;
}
.toggle-btn:hover {
  transform: translateY(-1px);
  background: #fff;
  color: var(--c-primary-dark);
  border-color: rgba(20,184,166,.26);
  box-shadow: 0 12px 24px rgba(15,23,42,.08);
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}
.bc-root  { color: var(--c-text-3); }
.bc-root svg { color: var(--c-text-3); }
.bc-current {
  color: var(--c-text);
  font-weight: 800;
  letter-spacing: -.01em;
}


.header-center-title {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  max-width: min(520px, 42vw);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: transparent;
  font-size: 15px;
  font-weight: 900;
  letter-spacing: .06em;
  background: linear-gradient(90deg, #0f172a, #0f766e 52%, #4f46e5);
  -webkit-background-clip: text;
  background-clip: text;
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
  height: 36px;
  padding: 0 12px 0 5px;
  border: 1px solid rgba(148,163,184,.20);
  border-radius: 999px;
  background: rgba(255,255,255,.72);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #14b8a6, #7c3aed);
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
  position: relative;
  padding: 26px;
  overflow-y: auto;
}

.app-main::before {
  content: '';
  position: fixed;
  inset: var(--header-h) 0 0 var(--sidebar-w);
  pointer-events: none;
  background:
    linear-gradient(90deg, rgba(255,255,255,.35), transparent 38%),
    radial-gradient(circle at 96% 0%, rgba(56,189,248,.12), transparent 28%);
  transition: inset .2s ease;
}

.app-shell.collapsed .app-main::before {
  left: var(--sidebar-collapsed-w);
}

@media (max-width: 920px) {
  .header-center-title { display: none; }
  .app-header { padding: 0 14px; }
  .app-main { padding: 16px; }
  .user-name { display: none; }
}
</style>
