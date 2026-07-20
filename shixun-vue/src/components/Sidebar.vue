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
  { group: '创意与生产', items: [
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
  { group: '仓储与履约', items: [
    { key: 'warehouseLogistics', label: '产品库存与物流管理', roles: MANAGER_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 10l9-6 9 6v10H3z"/><path d="M7 20v-6h10v6"/></svg>` },
    { key: 'warehouseProducts', label: '产品主数据库', parentKey: 'warehouseLogistics', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'warehouseInventory', label: '库存台账', parentKey: 'warehouseLogistics', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'warehouseInbound', label: '入库管理', parentKey: 'warehouseLogistics', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'warehouseOutbound', label: '出库管理', parentKey: 'warehouseLogistics', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'warehousePick', label: '拣货任务', parentKey: 'warehouseLogistics', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'warehouseAlerts', label: '库存预警', parentKey: 'warehouseLogistics', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'logistics', label: '物流跟踪', parentKey: 'warehouseLogistics', roles: MANAGER_ROLES, icon: `<svg></svg>` },
    { key: 'designers', label: '设计师/创作者', roles: MANAGER_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>` },
  ]},
  { group: '连锁业务', items: [
    { key: 'chain', label: '之间连锁', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 21V7l8-4 8 4v14"/><path d="M9 21v-8h6v8"/><path d="M4 11h16"/></svg>` },
    { key: 'chainMarketing', label: '门店营销方案申请【连锁】', parentKey: 'chain', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'chainNewProduct', label: '新商品上架申请【连锁】', parentKey: 'chain', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'chainPriceAdjust', label: '商品售价调整申请【连锁】', parentKey: 'chain', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '市场项目', items: [
    { key: 'marketDemand', label: '市场部需求管理', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 11l18-5v12L3 13v-2z"/><path d="M11.6 16.8a3 3 0 0 1-5.8-1.6"/><path d="M21 8v8"/></svg>` },
    { key: 'marketPromotion', label: '营销宣传申请', parentKey: 'marketDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'marketEcommerceNewProduct', label: '电商新品上架申请', parentKey: 'marketDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'marketShooting', label: '拍摄需求申请', parentKey: 'marketDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'marketProductCopy', label: '产品宣传文案', parentKey: 'marketDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '项目协同', items: [
    { key: 'projectDemand', label: '项目部需求管理', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 5h16v14H4z"/><path d="M8 9h8"/><path d="M8 13h5"/><path d="M6 3v4"/><path d="M18 3v4"/></svg>` },
    { key: 'projectInitiation', label: '项目立项申请', parentKey: 'projectDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'projectInquiry', label: '项目询价申请', parentKey: 'projectDemand', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '人力行政', items: [
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
  { group: '考勤管理', items: [
    { key: 'attendanceManagement', label: '考勤管理', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4"/><path d="M8 2v4"/><path d="M3 10h18"/><path d="M12 14v3l2 1"/></svg>` },
    { key: 'attendanceCardRepair', label: '补卡申请', parentKey: 'attendanceManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'attendanceLeave', label: '请假申请', parentKey: 'attendanceManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'attendanceBusinessTrip', label: '出差申请', parentKey: 'attendanceManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
    { key: 'attendanceOutgoing', label: '外出申请', parentKey: 'attendanceManagement', roles: STAFF_WORKFLOW_ROLES, icon: `<svg></svg>` },
  ]},
  { group: '供应商', items: [
    { key: 'supplierList', label: '供应商列表', roles: STAFF_WORKFLOW_ROLES, icon: `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 7h18"/><path d="M5 7V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v2"/><rect x="4" y="7" width="16" height="14" rx="2"/><path d="M8 12h8"/><path d="M8 16h5"/></svg>` },
  ]},
  { group: '财务审批', items: [
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
const firstLetter = computed(() => props.currentUser.username?.[0]?.toUpperCase() || 'U')
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
        <div class="footer-avatar">{{ firstLetter }}</div>
        <div class="footer-info">
          <span class="footer-name">{{ currentUser.username }}</span>
          <span class="footer-role" :style="{ color: currentRoleColor }">{{ currentRoleLabel }}</span>
        </div>
        <button class="footer-logout" title="退出登录" @click="emit('logout')">
          <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
        </button>
      </div>
      <div v-else class="footer-avatar-only">
        <div class="footer-avatar">{{ firstLetter }}</div>
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
  isolation: isolate;
  background:
    radial-gradient(circle at 18% 0%, rgba(20,184,166,.26), transparent 28%),
    radial-gradient(circle at 96% 18%, rgba(124,58,237,.26), transparent 34%),
    linear-gradient(180deg, #050816 0%, #0b1220 46%, #08111f 100%);
  display: flex;
  flex-direction: column;
  z-index: 50;
  transition: width .24s ease;
  overflow: hidden;
  box-shadow: 18px 0 56px rgba(2,6,23,.28);
}

.sidebar::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -2;
  background-image:
    linear-gradient(rgba(255,255,255,.055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.045) 1px, transparent 1px);
  background-size: 34px 34px;
  mask-image: linear-gradient(180deg, rgba(0,0,0,.8), transparent 92%);
}

.sidebar::after {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,.06) 48%, transparent 49%);
  background-size: 220px 100%;
  opacity: .5;
  animation: sidebarScan 7s linear infinite;
}

@keyframes sidebarScan {
  from { transform: translateX(-230px); }
  to { transform: translateX(230px); }
}

.sidebar.collapsed {
  width: var(--sidebar-collapsed-w);
}

/* Logo area */
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  height: 72px;
  border-bottom: 1px solid rgba(255,255,255,.08);
  flex-shrink: 0;
  background: linear-gradient(180deg, rgba(255,255,255,.08), rgba(255,255,255,.02));
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: rgba(255,255,255,.95);
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
  box-shadow: 0 0 0 1px rgba(255,255,255,.30), 0 16px 34px rgba(20,184,166,.22), inset 0 -8px 18px rgba(20,184,166,.08);
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
  font-size: 16px;
  font-weight: 900;
  color: #fff;
  line-height: 1.2;
  letter-spacing: .02em;
}

.logo-sub {
  margin-top: 3px;
  font-size: 10px;
  color: rgba(203,213,225,.62);
  letter-spacing: 0.5px;
}

/* Navigation */
.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: 12px 8px;
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,.08) transparent;
}

.sidebar-nav::-webkit-scrollbar { width: 3px; }
.sidebar-nav::-webkit-scrollbar-thumb { background: rgba(255,255,255,.08); }

.nav-group-label {
  padding: 15px 11px 7px;
  font-size: 10px;
  font-weight: 800;
  color: rgba(148,163,184,.54);
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
  gap: 11px;
  padding: 0 12px;
  height: 42px;
  width: 100%;
  border: none;
  border-radius: 14px;
  background: transparent;
  color: rgba(226,232,240,.62);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font);
  cursor: pointer;
  transition: transform .18s ease, color .18s, background .18s, box-shadow .18s;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
}

.nav-item.child { height: 36px; padding-left: 33px; font-size: 12px; color: rgba(203,213,225,.54); background: rgba(255,255,255,.025); }
.nav-item.child .nav-icon { display:none; }
.nav-content { display:flex; align-items:center; gap:10px; min-width:0; flex:1; }
.child-line { width: 8px; height: 12px; border-left: 1px solid rgba(148,163,184,.32); border-bottom: 1px solid rgba(148,163,184,.32); border-radius: 0 0 0 5px; flex-shrink:0; }
.parent-arrow { margin-left:auto; color:rgba(125,211,252,.48); font-size:14px; }
.nav-item.parent { font-weight:800; color:rgba(248,250,252,.82); }
.nav-item.child.active { color:#fff; background:linear-gradient(90deg, rgba(124,58,237,.32), rgba(20,184,166,.12)); }

.nav-item:hover {
  transform: translateX(3px);
  color: #fff;
  background: rgba(255,255,255,.075);
}

.nav-item.active {
  color: #fff;
  background: linear-gradient(135deg, rgba(20,184,166,.28), rgba(124,58,237,.24));
  box-shadow: inset 0 0 0 1px rgba(255,255,255,.10), 0 14px 32px rgba(20,184,166,.16);
}

.nav-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  color: #7dd3fc;
}

.nav-active-bar {
  position: absolute;
  left: 7px;
  top: 9px;
  bottom: 9px;
  width: 4px;
  background: linear-gradient(180deg, #5eead4, #a78bfa);
  border-radius: 999px;
  box-shadow: 0 0 18px rgba(94,234,212,.75);
}

.nav-label {
  flex: 1;
  white-space: nowrap;
}

/* Footer */
.sidebar-footer {
  padding: 14px;
  border-top: 1px solid rgba(255,255,255,.08);
  flex-shrink: 0;
  background: linear-gradient(0deg, rgba(255,255,255,.08), rgba(255,255,255,.02));
}

.footer-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border: 1px solid rgba(255,255,255,.10);
  border-radius: 16px;
  background: rgba(255,255,255,.06);
  box-shadow: inset 0 1px 0 rgba(255,255,255,.08);
  overflow: hidden;
}

.footer-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #14b8a6, #7c3aed);
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
  flex: 1;
}

.footer-name {
  font-size: 13px;
  font-weight: 500;
  color: rgba(255,255,255,.92);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.footer-role {
  font-size: 11px;
  font-weight: 700;
  color: rgba(125,211,252,.72);
}

.footer-logout {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255,255,255,.12);
  border-radius: 10px;
  color: rgba(226,232,240,.70);
  background: rgba(255,255,255,.055);
  cursor: pointer;
  transition: all .18s ease;
}
.footer-logout:hover {
  color: #fff;
  background: rgba(20,184,166,.18);
  border-color: rgba(94,234,212,.28);
}

/* Transitions */
.fade-enter-active, .fade-leave-active { transition: opacity .15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* Official launch sidebar skin — authoritative, less sci-fi */
.sidebar {
  background:
    radial-gradient(circle at 0 0, rgba(20,184,166,.16), transparent 26%),
    linear-gradient(180deg, #10233f 0%, #0b1b33 48%, #09223a 100%);
  box-shadow: 18px 0 48px rgba(15,23,42,.22);
}
.sidebar::before {
  opacity: .12;
  background-image:
    linear-gradient(rgba(255,255,255,.22) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.20) 1px, transparent 1px);
  background-size: 56px 56px;
}
.sidebar::after {
  display: none;
}
.sidebar-logo {
  height: 70px;
  background: rgba(255,255,255,.045);
  border-bottom-color: rgba(255,255,255,.10);
}
.logo-icon {
  border-radius: 12px;
  box-shadow: 0 12px 28px rgba(15,23,42,.18);
}
.logo-title {
  letter-spacing: .06em;
}
.logo-sub {
  color: rgba(226,232,240,.58);
}
.nav-group-label {
  color: rgba(203,213,225,.48);
}
.nav-item {
  color: rgba(226,232,240,.68);
  border-radius: 12px;
}
.nav-icon {
  color: rgba(125,211,252,.86);
}
.nav-item.parent {
  color: rgba(248,250,252,.86);
}
.nav-item:hover {
  transform: translateX(2px);
  background: rgba(255,255,255,.07);
}
.nav-item.active {
  background:
    linear-gradient(90deg, rgba(20,184,166,.30), rgba(20,184,166,.10)),
    rgba(255,255,255,.04);
  box-shadow: inset 0 0 0 1px rgba(94,234,212,.20), 0 10px 24px rgba(2,6,23,.14);
}
.nav-item.child.active {
  background: rgba(20,184,166,.18);
}
.nav-active-bar {
  background: #5eead4;
  box-shadow: 0 0 14px rgba(94,234,212,.48);
}
.sidebar-footer {
  background: rgba(255,255,255,.04);
  border-top-color: rgba(255,255,255,.10);
}
.footer-user {
  background: rgba(255,255,255,.07);
  border-color: rgba(255,255,255,.12);
}
</style>
