/**
 * 后台角色：超级管理员、财务、项目经理、设计师、生产、物流；user 为 C 端用户。
 * technician/feeder 保留用于历史账号兼容，后续可由管理员迁移。
 */
export type Role = 'admin' | 'finance' | 'project_manager' | 'designer' | 'production' | 'logistics' | 'technician' | 'feeder' | 'user'
export type AlertType = 'success' | 'error'

export type PageName =
  | 'dashboard'
  | 'approvalCenter'
  | 'consumerWorksReview'
  | 'professionalWorksReview'
  | 'multiviewReview'
  | 'consumerAssetInventory'
  | 'consumerCreditManagement'
  | 'paymentOperations'
  | 'historicalSales'
  | 'consumerProductionReview'
  | 'orderManagement'
  | 'commercialProductization'
  | 'professionalGuidance'
  | 'aiAssistant'
  | 'customerService'
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
  | 'myWorkspace'

export interface User {
  id: number
  username: string
  role: Role
}

export interface AuthSession {
  token: string
  tokenType: 'Bearer'
  expiresIn: number
  user: User
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
