export type Role = 'admin' | 'technician' | 'feeder'
export type AlertType = 'success' | 'error'
export type PageName =
  | 'dashboard' | 'statistics' | 'pens' | 'drugs' | 'batches'
  | 'animals' | 'immunization' | 'medication' | 'transfer'
  | 'slaughter' | 'death' | 'traceability' | 'users' | 'products'

export interface User {
  id: number
  username: string
  role: Role
}

export interface Pen {
  id: number
  penCode: string
  penName: string
  capacity: number
  currentCount: number
  responsiblePerson: string
  status: number
}

export interface Batch {
  id: number
  batchCode: string
  entryDate: string
  breed: string
  source: string | null
  initialPenId: number | null
  initialPenName: string | null
  animalCount: number | null
  notes: string | null
}

export interface Animal {
  id: number
  earTag: string
  gender: 'MALE' | 'FEMALE'
  entryDate: string
  breed: string
  batchId: number | null
  batchCode: string
  currentPenId: number | null
  currentPenName: string | null
  birthWeight: number | null
  status: 'ACTIVE' | 'SOLD' | 'DEAD'
}

export interface DrugVaccine {
  id: number
  category: 'DRUG' | 'VACCINE'
  genericName: string
  specification: string
  manufacturer: string
  description: string | null
  imageUrl: string | null
}

export interface UserRecord {
  id: number
  username: string
  age: number | null
  email: string | null
  phone: string | null
  role: Role
}

export interface Product {
  id: number
  name: string
  price: number
  stock: number
  category: string
  description: string
}

export interface ImmunizationRecord {
  id: number
  earTag: string
  vaccineName: string
  eventTime: string
  dosage: string
  operator: string
  notes: string
}

export interface MedicationRecord {
  id: number
  earTag: string
  drugName: string
  reason: string
  eventTime: string
  dosage: string
  operator: string
}

export interface TransferRecord {
  id: number
  earTag: string
  toPenId: number
  toPenName: string
  fromPenName: string
  eventTime: string
  reason: string
}

export interface SlaughterRecord {
  id: number
  earTag: string
  eventTime: string
  type: 'SALE' | 'SLAUGHTER' | 'TRANSFER'
  destination: string
  weight: number | null
  price: number | null
}

export interface Notification {
  type: 'warning' | 'info'
  title: string
  message: string
}

export interface MonthlyData {
  month: string
  count: string
}

export interface AnimalStatusData {
  status: 'ACTIVE' | 'SOLD' | 'DEAD'
  count: string
}

export interface DeathRecord {
  id: number
  earTag: string
  eventTime: string
  cause: string | null
  operator: string | null
  notes: string | null
}

export interface PenUsageData {
  penName: string
  capacity: string
  currentCount: string
}

export interface AnimalTraceability {
  animal: {
    earTag: string
    gender: string
    breed: string
    batchCode: string
    currentPenName: string
    entryDate: string
    status: string
  }
  timeline: Array<{
    eventType: string
    eventTime: string
    description: string
    operator?: string
    itemName?: string
    detail?: string
    dosage?: string
  }>
}

export interface BatchTraceability {
  batch: {
    batch_code: string
    breed: string
    entry_date: string
    total_count: number
    sold_count: number
    immunization_count: number
    medication_count: number
    transfer_count: number
  }
  animals: Animal[]
}

export interface DashboardStats {
  animals: number
  activeAnimals: number
  soldAnimals: number
  deadAnimals: number
  pens: number
  activePens: number
  batches: number
  drugs: number
}
