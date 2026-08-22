import type { Ref } from 'vue'

type ReadonlyValue<T> = { readonly value: T }

export interface ChatDraftOptions {
  sessionId: ReadonlyValue<number | null>
  chatInput: Ref<string>
  storagePrefix?: string
  delay?: number
}

/** Persists the unfinished composer text without coupling it to chat state. */
export function useChatDraft(options: ChatDraftOptions) {
  let saveTimer: ReturnType<typeof setTimeout> | null = null
  const prefix = options.storagePrefix || 'conversation-create:draft'
  const delay = options.delay ?? 300

  function storageKey() {
    return options.sessionId.value ? `${prefix}:${options.sessionId.value}` : ''
  }

  function persist() {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = null
    const key = storageKey()
    if (!key) return
    try {
      if (options.chatInput.value) uni.setStorageSync(key, { value: options.chatInput.value, updatedAt: Date.now() })
      else uni.removeStorageSync(key)
    } catch {
      // Local draft persistence is best-effort and must never interrupt chat.
    }
  }

  function schedule() {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(persist, delay)
  }

  function restore() {
    const key = storageKey()
    if (!key || options.chatInput.value) return
    try {
      const saved = uni.getStorageSync(key)
      const value = typeof saved === 'string' ? saved : String(saved?.value || '')
      if (value) options.chatInput.value = value
    } catch {
      // Ignore malformed or unavailable local storage.
    }
  }

  function clear() {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = null
    const key = storageKey()
    if (!key) return
    try { uni.removeStorageSync(key) } catch { /* local storage is best-effort */ }
  }

  return { storageKey, persist, schedule, restore, clear }
}
