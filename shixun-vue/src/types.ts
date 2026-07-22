/**
 * 角色编码保持兼容历史数据：
 * admin=超级管理员，technician=审批主管，feeder=员工，designer=设计师，user=C端用户。
 */
export type Role = 'admin' | 'technician' | 'feeder' | 'designer' | 'user'
export type AlertType = 'success' | 'error'

export type PageName =
  | 'dashboard'
  | 'approvalCenter'
  | 'consumerWorksReview'
  | 'consumerAssetInventory'
  | 'consumerCreditManagement'
  | 'aiAssistant'
  | 'consumerMobile'
  | 'studio'
  | 'creative2d'
  | 'creative3d'
  | 'creativeReview'
  | 'chain'
  | 'chainMarketing'
  | 'chainNewProduct'
  | 'chainPriceAdjust'
  | 'marketDemand'
  | 'marketPromotion'
  | 'marketEcommerceNewProduct'
  | 'marketShooting'
  | 'marketProductCopy'
  | 'projectDemand'
  | 'projectInitiation'
  | 'projectInquiry'
  | 'hrManagement'
  | 'hrNewProductIncentive'
  | 'hrResignation'
  | 'hrTraining'
  | 'hrHolidayOvertime'
  | 'hrTransfer'
  | 'hrPolicyApproval'
  | 'hrRegularization'
  | 'hrRecruitment'
  | 'attendanceManagement'
  | 'attendanceCardRepair'
  | 'attendanceLeave'
  | 'attendanceBusinessTrip'
  | 'attendanceOutgoing'
  | 'supplierList'
  | 'sampleWorkOrders'
  | 'finance'
  | 'financeAssetScrap'
  | 'financePublicPayment'
  | 'financePettyCash'
  | 'financePersonalExpense'
  | 'financePromotionApproval'
  | 'financeSeal'
  | 'financePettyCashRepay'
  | 'financeTravel'
  | 'financeInvoice'
  | 'financeSpecialExpense'
  | 'financePettyCashWriteoff'
  | 'warehouseLogistics'
  | 'warehouseProducts'
  | 'warehouseInventory'
  | 'warehouseInbound'
  | 'warehouseOutbound'
  | 'warehousePick'
  | 'warehouseAlerts'
  | 'scaleUp'
  | 'createProductionProject'
  | 'production'
  | 'sampleApplication'
  | 'sampleProduction'
  | 'bulkProductionApplication'
  | 'bulkProductionWorkOrders'
  | 'bulkProduction'
  | 'logistics'
  | 'designers'
  | 'users'

export interface User {
  id: number
  username: string
  role: Role
}

export interface UserRecord {
  id: number
  username: string
  age: number | null
  email: string | null
  phone: string | null
  role: Role
}

export interface Notification {
  type: 'warning' | 'info'
  title: string
  message: string
}
