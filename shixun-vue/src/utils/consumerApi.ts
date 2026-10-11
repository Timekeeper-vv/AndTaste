/** Consumer pages share the login header injected by main.ts. */
export async function consumerApi<T = any>(url: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(url, { cache: 'no-store', ...init })
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new Error(data?.message || data?.error || `请求失败（HTTP ${response.status}）`)
  return data as T
}

export function consumerJson(method: string, body?: unknown): RequestInit {
  return { method, headers: { 'Content-Type': 'application/json' }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) }
}

export function rows<T = any>(value: unknown): T[] { return Array.isArray(value) ? value : [] }
export function money(value: unknown) { return Number(value || 0).toFixed(2) }
export function dateText(value: unknown) { return String(value || '').replace('T', ' ').slice(0, 16) }

export function validateZip(file: File | null): asserts file is File {
  if (!file || !/\.zip$/i.test(file.name)) throw new Error('请选择 ZIP 格式的作品包')
  if (!file.size) throw new Error('作品包不能为空')
  if (file.size > 100 * 1024 * 1024) throw new Error('ZIP 作品包不能超过 100MB')
}

export async function downloadConsumerFile(url: string, filename: string) {
  const response = await fetch(url, { cache: 'no-store' })
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.message || '下载失败，请重试')
  }
  const objectUrl = URL.createObjectURL(await response.blob())
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename.replace(/[\\/:*?"<>|]/g, '_')
  link.click()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1500)
}
