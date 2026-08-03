export const imageUrl = (value?: string) => {
  if (!value) return ''
  if (/^https?:\/\//.test(value)) return value
  const base = (import.meta.env.VITE_API_BASE_URL || 'https://api.example.com').replace(/\/$/, '')
  return `${base}${value.startsWith('/') ? value : `/${value}`}`
}

export const statusText = (status?: string) => ({
  draft: '待提交审核',
  pending: '处理中',
  queued: '排队中',
  running: '生成中',
  processing: '生成中',
  review: '审核中',
  approved: '已通过',
  rejected: '未通过',
  succeeded: '已完成',
  failed: '生成失败',
  cancelled: '已取消',
  canceled: '已取消',
  manual_review: '待人工核验',
  paid: '已到账',
  closed: '已关闭',
  expired: '已过期',
}[status || ''] || status || '处理中')

export const moneyText = (value: unknown, fallbackFen?: unknown) => {
  const yuan = Number(value)
  if (Number.isFinite(yuan)) return yuan.toFixed(2)
  const fen = Number(fallbackFen)
  return Number.isFinite(fen) ? (fen / 100).toFixed(2) : '0.00'
}
