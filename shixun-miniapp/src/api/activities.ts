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
export const reportActiveUsage = (activeSeconds: number) => request<any>(
  '/api/creative/ai/consumer-activities/usage/heartbeat',
  { method: 'POST', data: { activeSeconds }, header: { 'content-type': 'application/json' }, timeout: 15000 },
)
export const claimShareReward = (channel = 'timeline') => request<any>(
  '/api/creative/ai/consumer-activities/share/claim',
  { method: 'POST', data: { channel }, header: { 'content-type': 'application/json' } },
)
export const getInviteCode = () => request<any>('/api/creative/ai/consumer-activities/invite/code')
export const getMyBountyRevenue = () => request<any[]>('/api/creative/ai/consumer-activities/revenue/mine')
