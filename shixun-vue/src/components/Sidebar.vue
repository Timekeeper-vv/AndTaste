<script setup lang="ts">
import { computed } from 'vue'
import type { User, PageName, Role } from '../types'
import andTasteLogo from '../assets/and_taste.png'

interface MenuItem { key: PageName; label: string; roles: Role[]; icon: string; parentKey?: PageName }
interface MenuGroup { group: string; items: MenuItem[] }

const props = defineProps<{ currentUser: User; currentPage: PageName; collapsed: boolean }>()
const emit = defineEmits<{ 'switch-page': [page: PageName]; 'logout': []; 'toggle': [] }>()

const roleLabels: Record<Role, string> = { admin: '超级管理员', technician: '审批主管', feeder: '员工' }
const roleColors: Record<Role, string> = { admin: '#ef4444', technician: '#7c3aed', feeder: '#0d9488' }

const ALL_ROLES: Role[] = ['admin', 'technician', 'feeder']
const MANAGER_ROLES: Role[] = ['admin', 'technician']
const STAFF_WORKFLOW_ROLES: Role[] = ['admin', 'technician', 'feeder']
const SUPER_ADMIN_ROLES: Role[] = ['admin']

const allMenus: MenuGroup[] = [
  { group: '总览', items: [
    { key: 'dashboard', label: '经营看板', roles: ALL_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>` },
    { key: 'approvalCenter', label: '审批中心', roles: MANAGER_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>` },
  ]},
  { group: '', items: [
  ]},
  { group: '', items: [
    { key: 'studio', label: '创意设计', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2l1.8 5.4L19 9l-5.2 1.6L12 16l-1.8-5.4L5 9l5.2-1.6L12 2z"/><path d="M19 15l.9 2.7L22 19l-2.1.7L19 22l-.9-2.3L16 19l2.1-1.3L19 15z"/></svg>` },
    { key: 'creative2d', label: '2D创意生图', parentKey: 'studio', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'creative3d', label: '3D辅助建模', parentKey: 'studio', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'creativeReview', label: '智能评估', parentKey: 'studio', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'scaleUp', label: '生产管理', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 19h16"/><path d="M7 16V8"/><path d="M12 16V5"/><path d="M17 16v-3"/></svg>` },
    { key: 'createProductionProject', label: '创建项目', parentKey: 'scaleUp', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'production', label: '智能成本核算引擎', parentKey: 'scaleUp', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 7h16"/><path d="M4 12h16"/><path d="M4 17h10"/><path d="M6 3v18"/><path d="M18 3v10"/></svg>` },
    { key: 'sampleApplication', label: '打样申请', parentKey: 'scaleUp', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'sampleWorkOrders', label: '打样工单明细', parentKey: 'scaleUp', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'sampleProduction', label: '产品打样管理', parentKey: 'scaleUp', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'bulkProductionApplication', label: '大货生产申请', parentKey: 'scaleUp', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'bulkProductionWorkOrders', label: '大货工单明细', parentKey: 'scaleUp', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'bulkProduction', label: '大货生产管理', parentKey: 'scaleUp', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '', items: [
    { key: 'warehouseLogistics', label: '产品库存与物流管理', roles: MANAGER_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 10l9-6 9 6v10H3z"/><path d="M7 20v-6h10v6"/></svg>` },
    { key: 'warehouse', label: '产品主数据 / 库存出入库', parentKey: 'warehouseLogistics', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'logistics', label: '物流跟踪', parentKey: 'warehouseLogistics', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'designers', label: '设计师/创作者', roles: MANAGER_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>` },
  ]},
  { group: '', items: [
    { key: 'chain', label: '之间连锁', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 21V7l8-4 8 4v14"/><path d="M9 21v-8h6v8"/><path d="M4 11h16"/></svg>` },
    { key: 'chainMarketing', label: '门店营销方案申请【连锁】', parentKey: 'chain', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'chainNewProduct', label: '新商品上架申请【连锁】', parentKey: 'chain', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'chainPriceAdjust', label: '商品售价调整申请【连锁】', parentKey: 'chain', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '', items: [
    { key: 'marketDemand', label: '市场部需求管理', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 11l18-5v12L3 13v-2z"/><path d="M11.6 16.8a3 3 0 0 1-5.8-1.6"/><path d="M21 8v8"/></svg>` },
    { key: 'marketPromotion', label: '营销宣传申请', parentKey: 'marketDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'marketEcommerceNewProduct', label: '电商新品上架申请', parentKey: 'marketDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'marketShooting', label: '拍摄需求申请', parentKey: 'marketDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'marketProductCopy', label: '产品宣传文案', parentKey: 'marketDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '', items: [
    { key: 'projectDemand', label: '项目部需求管理', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 5h16v14H4z"/><path d="M8 9h8"/><path d="M8 13h5"/><path d="M6 3v4"/><path d="M18 3v4"/></svg>` },
    { key: 'projectInitiation', label: '项目立项申请', parentKey: 'projectDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'projectInquiry', label: '项目询价申请', parentKey: 'projectDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '', items: [
    { key: 'hrManagement', label: '人力资源管理', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>` },
    { key: 'hrNewProductIncentive', label: '新产品开发激励', parentKey: 'hrManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'hrResignation', label: '离职申请', parentKey: 'hrManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'hrTraining', label: '培训申请', parentKey: 'hrManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'hrHolidayOvertime', label: '加班申请【法定节假日】', parentKey: 'hrManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'hrTransfer', label: '调岗申请', parentKey: 'hrManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'hrPolicyApproval', label: '制度&方案审批', parentKey: 'hrManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'hrRegularization', label: '转正申请', parentKey: 'hrManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'hrRecruitment', label: '招聘申请', parentKey: 'hrManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '', items: [
    { key: 'attendanceManagement', label: '考勤管理', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/><path d="M12 14v3l2 1"/></svg>` },
    { key: 'attendanceCardRepair', label: '补卡申请', parentKey: 'attendanceManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'attendanceLeave', label: '请假申请', parentKey: 'attendanceManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'attendanceBusinessTrip', label: '出差申请', parentKey: 'attendanceManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'attendanceOutgoing', label: '外出申请', parentKey: 'attendanceManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '', items: [
    { key: 'supplierList', label: '供应商列表', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h18"/><path d="M5 7V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v2"/><rect x="4" y="7" width="16" height="14" rx="2"/><path d="M8 12h8"/><path d="M8 16h5"/></svg>` },
  ]},
  { group: '', items: [
    { key: 'finance', label: '财务管理', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 21h18"/><path d="M5 21V7l7-4 7 4v14"/><path d="M9 21v-8h6v8"/><path d="M8 9h8"/></svg>` },
    { key: 'financeAssetScrap', label: '固定资产报废申请', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'financePublicPayment', label: '对公付款申请(供应链)', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'financePettyCash', label: '备用金申请', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'financePersonalExpense', label: '个人费用报销', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'financePromotionApproval', label: '促销活动审批', parentKey: 'finance', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'financeSeal', label: '用章用印申请', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'financePettyCashRepay', label: '备用金还款', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'financeTravel', label: '差旅报销', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'financeInvoice', label: '开票申请', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'financeSpecialExpense', label: '费用报销(特殊事项)', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'financePettyCashWriteoff', label: '备用金核销', parentKey: 'finance', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '系统', items: [
    { key: 'users', label: '账号权限', roles: SUPER_ADMIN_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/></svg>` },
  ]},
]

const menus = computed<MenuGroup[]>(() => {
  const role: Role = props.currentUser?.role || 'admin'
  return allMenus.map(g => ({ ...g, items: g.items.filter(item => item.roles.includes(role)) })).filter(g => g.items.length > 0)
})
const currentRoleLabel = computed<string>(() => roleLabels[props.currentUser?.role] || '超级管理员')
const currentRoleColor = computed<string>(() => roleColors[props.currentUser?.role] || '#ef4444')
</script>

<template>
  <aside class="sidebar" :class="{ collapsed }">
    <!-- Logo -->
    <div class="sidebar-logo">
      <div class="logo-icon">
        <img :src="andTasteLogo" alt="之间味道 logo" />
      </div>
      <transition name="fade">
        <div v-if="!collapsed" class="logo-text">
          <span class="logo-title">之间智造</span>
          <span class="logo-sub">文创产品智能体平台</span>
        </div>
      </transition>
    </div>

    <!-- Navigation -->
    <nav class="sidebar-nav">
      <template v-for="menu in menus" :key="menu.group">
        <div class="nav-group-label" v-if="!collapsed && menu.group">{{ menu.group }}</div>
        <div v-else-if="collapsed && menu.group" class="nav-group-divider"></div>
        <button
          v-for="item in menu.items"
          :key="item.key"
          class="nav-item"
          :class="{ active: currentPage === item.key, parent: ['studio','scaleUp','warehouseLogistics','chain','marketDemand','projectDemand','hrManagement','attendanceManagement','finance'].includes(item.key), child: !!item.parentKey }"
          :title="collapsed ? item.label : ''"
          @click="emit('switch-page', item.key)"
        >
          <span class="nav-icon" v-html="item.icon"></span>
          <transition name="fade">
            <span v-if="!collapsed" class="nav-content">
              <span v-if="item.parentKey" class="child-line"></span>
              <span class="nav-label">{{ item.label }}</span>
              <span v-if="['studio','scaleUp','warehouseLogistics','chain','marketDemand','projectDemand','hrManagement','attendanceManagement','finance'].includes(item.key)" class="parent-arrow">⌄</span>
            </span>
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
  background: #fff;
  border-radius: var(--r);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.logo-icon img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
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

.nav-item.child { height: 36px; padding-left: 37px; font-size: 12px; color: rgba(255,255,255,.48); background: rgba(255,255,255,.018); }
.nav-item.child .nav-icon { display:none; }
.nav-content { display:flex; align-items:center; gap:10px; min-width:0; flex:1; }
.child-line { width: 8px; height: 12px; border-left: 1px solid rgba(255,255,255,.18); border-bottom: 1px solid rgba(255,255,255,.18); border-radius: 0 0 0 5px; flex-shrink:0; }
.parent-arrow { margin-left:auto; color:rgba(255,255,255,.4); font-size:14px; }
.nav-item.parent { font-weight:700; color:rgba(255,255,255,.75); }
.nav-item.child.active { color:#fff; background:rgba(124,58,237,.25); }

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
