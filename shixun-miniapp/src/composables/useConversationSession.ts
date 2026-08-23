import type { Ref } from 'vue'
import {
  createConversation,
  getConversation,
  getConversations,
  saveConversationEvent,
  type ConversationEvent,
  type ConversationSession,
} from '../api/creative'

type ReadonlyValue<T> = { readonly value: T }

export interface ConversationSessionOptions {
  sessionId: Ref<number | null>
  sessionReady: Ref<boolean>
  saving: Ref<boolean>
  forceNewSession: ReadonlyValue<boolean>
  campaignSessionId?: () => number
  requireSession: () => boolean
  isNotFound: (error: unknown) => boolean
  resetViewState: () => void
  restoreEvent: (event: ConversationEvent) => void
  restoreMessages: (events: ConversationEvent[]) => void
  restorePhase: (events: ConversationEvent[]) => void
  refreshRestoredPreviews: () => Promise<void>
  onSessionLoaded?: (session: ConversationSession) => void
  onCreateError?: (error: any) => void
}

/**
 * Owns conversation-session lifecycle and persistence while leaving the
 * product-specific state restoration to the page callbacks.
 */
export function useConversationSession(options: ConversationSessionOptions) {
  let sessionPromise: Promise<boolean> | null = null

  async function restoreSession(sessionToRestore: number | string) {
    try {
      const detail = await getConversation(sessionToRestore)
      const events = Array.isArray(detail.events) ? detail.events : []
      options.resetViewState()
      options.sessionId.value = Number(detail.id)
      options.onSessionLoaded?.(detail)
      for (const event of events) options.restoreEvent(event)
      options.restoreMessages(events)
      options.restorePhase(events)
      await options.refreshRestoredPreviews()
      return Boolean(options.sessionId.value)
    } catch (error) {
      // A session may belong to an old deployment or have been removed. The
      // caller can then create a fresh draft without showing a raw 404.
      if (options.isNotFound(error)) return false
      throw error
    }
  }

  async function restoreLatestSession() {
    const sessions = await getConversations()
    const latest = sessions.find(item => String(item.status || 'draft') !== 'archived')
    if (!latest?.id) return false
    return restoreSession(latest.id)
  }

  async function ensureSession() {
    if (sessionPromise) return sessionPromise
    sessionPromise = (async () => {
      if (!options.requireSession()) return false
      if (options.sessionId.value) return true
      try {
        if (!options.forceNewSession.value) {
          try {
            const campaignSessionId = Number(options.campaignSessionId?.() || 0)
            if (campaignSessionId > 0 && await restoreSession(campaignSessionId)) return true
            if (await restoreLatestSession()) return true
          } catch (error) {
            if (!options.isNotFound(error)) throw error
          }
        }
        const session = await createConversation()
        options.sessionId.value = Number(session.id)
        options.onSessionLoaded?.(session)
        options.resetViewState()
        return Boolean(options.sessionId.value)
      } catch (error: any) {
        options.onCreateError?.(error)
        return false
      }
    })()
    const result = await sessionPromise
    options.sessionReady.value = result
    return result
  }

  async function saveEvent(step: string, eventType: string, payload: Record<string, any>) {
    if (!(await ensureSession()) || !options.sessionId.value) return
    options.saving.value = true
    try {
      await saveConversationEvent(options.sessionId.value, { step, eventType, payload })
    } catch {
      // Persistence improves continuity but must not interrupt product
      // selection or an in-flight generation request.
    } finally {
      options.saving.value = false
    }
  }

  return {
    ensureSession,
    restoreSession,
    restoreLatestSession,
    saveEvent,
  }
}
