import { reportActiveUsage } from '../api/activities'
import { lastActiveAt } from './activity'
import { getSession } from './session'

// 与需求一致：每分钟 5 积分、每日上限 1000 分；离开 3 分钟视为中断。
// 客户端每 30 秒上报一次，服务端按整分钟结算并做单次/每日封顶。
const HEARTBEAT_INTERVAL = 30000
const HEARTBEAT_SECONDS = 30
const IDLE_TIMEOUT = 180000

let timer: ReturnType<typeof setInterval> | null = null
let reporting = false

async function reportIfActive() {
  if (!getSession()?.token) return
  if (Date.now() - lastActiveAt() > IDLE_TIMEOUT) return
  if (reporting) return
  reporting = true
  try {
    await reportActiveUsage(HEARTBEAT_SECONDS)
  } catch {
    // 奖励上报失败不能影响正常使用，下一次心跳会自然补上。
  } finally {
    reporting = false
  }
}

export function startUsageTracking() {
  if (timer) return
  timer = setInterval(() => { void reportIfActive() }, HEARTBEAT_INTERVAL)
}

export function stopUsageTracking() {
  if (!timer) return
  clearInterval(timer)
  timer = null
}
