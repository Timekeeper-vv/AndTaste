export const imageUrl = (value?: string) => {
  if (!value) return ''
  if (/^https?:\/\//.test(value)) return value
  return (import.meta.env.VITE_API_BASE_URL || 'https://api.example.com').replace(/\/$/, '') + value
}
export const statusText = (status?: string) => ({ pending: '审核中', approved: '已通过', rejected: '未通过', succeeded: '已完成', manual_review: '待人工核验', paid: '已到账' }[status || ''] || status || '处理中')
