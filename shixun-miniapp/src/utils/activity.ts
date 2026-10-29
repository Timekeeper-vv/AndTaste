// 有效使用时长需要区分"真的在用"和"切到后台挂着"。这里只记录最后一次
// 真实交互时间，真正的心跳上报在 utils/usage.ts 中完成。
const LAST_ACTIVE_KEY = 'smart_pig_last_active_at'

export function markActive() {
  try { uni.setStorageSync(LAST_ACTIVE_KEY, Date.now()) } catch { /* Storage 仅用于优化统计，失败可忽略。 */ }
}

export function lastActiveAt() {
  try { return Number(uni.getStorageSync(LAST_ACTIVE_KEY)) || 0 } catch { return 0 }
}
