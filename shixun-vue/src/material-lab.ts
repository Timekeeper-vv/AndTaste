import { createApp, defineComponent, h, ref } from 'vue'
import MaterialModelStudio from './components/MaterialModelStudio.vue'
import './material-lab.css'

const params = new URLSearchParams(window.location.search)

function textParam(name: string, fallback = ''): string {
  return (params.get(name) || fallback).trim()
}

function readLabSession(): { assetId: string; title: string; modelUrl: string; accessToken: string } {
  const assetId = textParam('assetId')
  const title = textParam('title', '3D 材质实验室')
  const accessToken = textParam('labToken')
  const rawModelUrl = textParam('modelUrl')
  if (!/^\d+$/.test(assetId)) throw new Error('缺少有效的作品编号，请返回作品库重新打开。')
  if (!accessToken) throw new Error('材质编辑权限已失效，请返回作品库重新打开。')
  let modelUrl: URL
  try {
    modelUrl = new URL(rawModelUrl, window.location.origin)
  } catch {
    throw new Error('模型访问地址格式不正确，请重新获取权限。')
  }
  const match = modelUrl.pathname.match(/^\/api\/creative\/ai\/assets\/(\d+)\/model-content$/)
  if (modelUrl.protocol !== 'https:' || !match || match[1] !== assetId) {
    throw new Error('模型访问地址与当前作品不匹配。')
  }
  if (modelUrl.searchParams.get('access_token') !== accessToken) {
    throw new Error('材质编辑权限无效，请返回作品库重新打开。')
  }
  return { assetId, title, modelUrl: modelUrl.toString(), accessToken }
}

function removeSensitiveQuery(): void {
  // Only the in-memory renderer keeps the five-minute asset-bound token.
  // Removing it from the visible URL reduces accidental screenshots/history leaks.
  const clean = new URL(window.location.href)
  clean.searchParams.delete('modelUrl')
  clean.searchParams.delete('labToken')
  clean.searchParams.delete('accessToken')
  window.history.replaceState({}, document.title, `${clean.pathname}${clean.search}${clean.hash}`)
}

const failure = ref('')
let session: ReturnType<typeof readLabSession> | null = null
try {
  session = readLabSession()
  removeSensitiveQuery()
} catch (error) {
  failure.value = error instanceof Error ? error.message : '材质实验室暂时不可用，请返回作品库后重试。'
}

const App = defineComponent({
  name: 'MaterialLabApp',
  setup() {
    const saveState = ref<'idle' | 'saving' | 'success' | 'error'>('idle')
    const notice = ref('选择材质后可实时旋转查看，保存会生成一件独立的材质版本，不会覆盖原模型。')

    async function saveVariant(payload: { blob: Blob; materialLabel: string }) {
      if (!session) return
      saveState.value = 'saving'
      notice.value = '正在导出并保存真实 GLB 材质版本…'
      try {
        const body = new FormData()
        body.append('file', payload.blob, `${session.assetId}-${payload.materialLabel}.glb`)
        body.append('materialLabel', payload.materialLabel)
        const endpoint = new URL(`/api/creative/ai/assets/${encodeURIComponent(session.assetId)}/material-variants`, window.location.origin)
        endpoint.searchParams.set('access_token', session.accessToken)
        const response = await fetch(endpoint.toString(), { method: 'POST', body, credentials: 'omit' })
        let result: any = {}
        try { result = await response.json() } catch { /* retain a safe generic error below */ }
        if (!response.ok) throw new Error(result?.message || '材质版本保存失败，请返回小程序后重试。')
        saveState.value = 'success'
        notice.value = result?.message || `“${payload.materialLabel}”材质版已保存到作品库。`
      } catch (error) {
        saveState.value = 'error'
        notice.value = error instanceof Error ? error.message : '材质版本保存失败，请稍后重试。'
      }
    }

    function studioError(message: string) {
      if (saveState.value !== 'saving') {
        saveState.value = 'error'
        notice.value = message || '材质编辑器暂时不可用，请稍后重试。'
      }
    }

    return () => {
      const activeSession = session
      if (failure.value || !activeSession) {
        return h('main', { class: 'lab-shell' }, h('section', { class: 'lab-failure' }, [
          h('span', { class: 'lab-mark' }, '材'),
          h('h1', '暂时无法打开材质实验室'),
          h('p', failure.value || '请返回小程序作品库后重新打开。'),
        ]))
      }
      return h('main', { class: 'lab-shell' }, [
        h('header', { class: 'lab-header' }, [
            h('div', [h('span', { class: 'lab-eyebrow' }, 'BETWEEN · MATERIAL LAB'), h('h1', activeSession.title), h('p', '拖动旋转模型，选择真实 PBR 材质；保存后会生成可审核、可打样的独立版本。')]),
            h('span', { class: 'lab-security' }, '5 分钟安全编辑会话'),
        ]),
        h('section', { class: 'lab-stage', 'aria-label': '3D 材质编辑器' }, [
            h(MaterialModelStudio, {
              modelUrl: activeSession.modelUrl,
              modelName: activeSession.title,
              externalSaving: saveState.value === 'saving',
              onSaveVariant: saveVariant,
              onError: studioError,
            }),
        ]),
        h('p', { class: ['lab-notice', saveState.value] }, notice.value),
      ])
    }
  },
})

createApp(App).mount('#app')
