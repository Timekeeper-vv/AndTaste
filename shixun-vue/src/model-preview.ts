import '@google/model-viewer'
import './model-preview.css'

const params = new URLSearchParams(window.location.search)
const titleElement = document.querySelector<HTMLElement>('#model-title')
const statusElement = document.querySelector<HTMLElement>('#model-status')
const stateElement = document.querySelector<HTMLElement>('#viewer-state')
const viewer = document.querySelector<HTMLElement>('#model-viewer')

function textParam(name: string, fallback = ''): string {
  return (params.get(name) || fallback).trim()
}

function setFailure(message: string): void {
  if (statusElement) statusElement.textContent = '预览暂时不可用'
  if (stateElement) {
    stateElement.textContent = message
    stateElement.classList.add('error')
  }
  viewer?.setAttribute('hidden', 'true')
}

function sanitizeModelUrl(raw: string, expectedAssetId: string): string {
  if (!raw) throw new Error('缺少模型访问地址，请返回小程序重新打开。')
  let parsed: URL
  try {
    parsed = new URL(raw, window.location.origin)
  } catch {
    throw new Error('模型访问地址格式不正确，请重新获取预览权限。')
  }
  if (parsed.protocol !== 'https:') throw new Error('模型预览仅支持 HTTPS 地址。')
  const pathMatch = parsed.pathname.match(/^\/api\/creative\/ai\/assets\/(\d+)\/model-content$/)
  if (!pathMatch || pathMatch[1] !== expectedAssetId) throw new Error('模型访问地址与当前作品不匹配。')
  if (!parsed.searchParams.get('access_token')) throw new Error('模型预览权限已失效，请返回小程序重新打开。')
  return parsed.toString()
}

function removeSensitiveQuery(): void {
  // URL 中的短期 token 只用于初始化 model-viewer，之后从地址栏和历史记录移除。
  const clean = new URL(window.location.href)
  clean.searchParams.delete('modelUrl')
  clean.searchParams.delete('accessToken')
  window.history.replaceState({}, document.title, `${clean.pathname}${clean.search}${clean.hash}`)
}

const assetId = textParam('assetId')
const title = textParam('title', '3D 模型')
if (titleElement) titleElement.textContent = title

if (!/^\d+$/.test(assetId)) {
  setFailure('缺少有效的作品编号，请返回小程序重新打开。')
} else {
  try {
    const modelUrl = sanitizeModelUrl(textParam('modelUrl'), assetId)
    removeSensitiveQuery()
    if (statusElement) statusElement.textContent = '可拖动旋转 · 双指缩放查看细节'
    viewer?.addEventListener('load', () => {
      if (stateElement) stateElement.textContent = '模型已加载'
    }, { once: true })
    viewer?.addEventListener('error', () => {
      setFailure('模型文件加载失败或预览权限已过期，请返回小程序重新打开。')
    }, { once: true })
    viewer?.setAttribute('src', modelUrl)
  } catch (error) {
    setFailure(error instanceof Error ? error.message : '模型预览失败，请返回小程序重新打开。')
  }
}
