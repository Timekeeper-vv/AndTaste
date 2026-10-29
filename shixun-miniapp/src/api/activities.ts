import { request } from './client'

export const getActivityOverview = () => request<any>('/api/creative/ai/consumer-activities/overview')
export const checkinToday = () => request<any>('/api/creative/ai/consumer-activities/checkin', { method: 'POST' })
export const getCurrentRedPackets = () => request<any[]>('/api/creative/ai/consumer-activities/red-packets/current')
export const claimRedPacket = (slot: string) => request<any>(`/api/creative/ai/consumer-activities/red-packets/${encodeURIComponent(slot)}/claim`, { method: 'POST' })
export const startQuiz = () => request<any>('/api/creative/ai/consumer-activities/quiz/start', { method: 'POST' })
export const answerQuiz = (sessionId: number, option: string) => request<any>(
  `/api/creative/ai/consumer-activities/quiz/${sessionId}/answer`,
  { method: 'POST', data: { option }, header: { 'content-type': 'application/json' } },
)
export const getBountyTasks = () => request<any[]>('/api/creative/ai/consumer-activities/tasks')
export const getMyBountySubmissions = () => request<any[]>('/api/creative/ai/consumer-activities/tasks/mine')
export const getMySampleVouchers = () => request<any[]>('/api/creative/ai/consumer-activities/sample-vouchers/mine')
export const submitBountyTask = (taskId: number, body: Record<string, unknown>) => request<any>(
  `/api/creative/ai/consumer-activities/tasks/${taskId}/submissions`,
  { method: 'POST', data: body, header: { 'content-type': 'application/json' } },
)
