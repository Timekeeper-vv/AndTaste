import { computed, ref, type ComputedRef, type Ref } from 'vue'
import {
  CREATIVE_POLICY_VERSION,
  getCreativePolicy,
  type CreativePolicyKey,
} from '../utils/compliance'

type PolicyDialog = {
  key: CreativePolicyKey
  resolve: (confirmed: boolean) => void
}

export interface CreativePolicyOptions {
  saveEvent: (step: string, eventType: string, payload: Record<string, any>) => Promise<void>
}

/**
 * Owns creation-flow policy confirmations and the page-owned confirmation
 * layer. The confirmation flags are intentionally local to this page/session;
 * resetting a draft must require the notices again for the next draft.
 */
export function useCreativePolicy(options: CreativePolicyOptions) {
  const referencePolicyConfirmed = ref(false)
  const aiPolicyConfirmed = ref(false)
  const threeDimensionalPolicyConfirmed = ref(false)
  const policyDialog = ref<PolicyDialog | null>(null)
  const activePolicy = computed(() => getCreativePolicy(policyDialog.value?.key || 'ai-output'))

  function confirmCreativePolicyInPage(key: CreativePolicyKey): Promise<boolean> {
    // Some iOS/DevTools combinations do not render uni.showModal after a
    // long scroll interaction. The page-owned layer keeps the required
    // consent action visible in the creation flow.
    if (policyDialog.value) return Promise.resolve(false)
    return new Promise(resolve => { policyDialog.value = { key, resolve } })
  }

  function resolvePolicyDialog(confirmed: boolean) {
    const dialog = policyDialog.value
    policyDialog.value = null
    dialog?.resolve(confirmed)
  }

  async function confirmOnce(
    key: CreativePolicyKey,
    confirmed: Ref<boolean>,
    cancelledMessage = '',
  ) {
    if (confirmed.value) return true
    const accepted = await confirmCreativePolicyInPage(key)
    if (!accepted) {
      if (cancelledMessage) uni.showToast({ title: cancelledMessage, icon: 'none' })
      return false
    }
    confirmed.value = true
    await options.saveEvent('compliance', 'policy_notice_confirmed', {
      policyKey: key,
      policyVersion: CREATIVE_POLICY_VERSION,
    })
    return true
  }

  function ensureReferencePolicy() {
    return confirmOnce('reference-materials', referencePolicyConfirmed)
  }

  function ensureAiPolicyForImage() {
    return confirmOnce('ai-output', aiPolicyConfirmed, '已取消本次 AI 生成')
  }

  function ensureThreeDimensionalPolicy() {
    return confirmOnce('three-dimensional', threeDimensionalPolicyConfirmed)
  }

  function reset() {
    // Resolve any in-flight action before clearing the dialog ref. Without
    // this, a session restore/restart could leave a pending Promise forever.
    if (policyDialog.value) resolvePolicyDialog(false)
    referencePolicyConfirmed.value = false
    aiPolicyConfirmed.value = false
    threeDimensionalPolicyConfirmed.value = false
  }

  return {
    referencePolicyConfirmed,
    aiPolicyConfirmed,
    threeDimensionalPolicyConfirmed,
    policyDialog,
    activePolicy: activePolicy as ComputedRef<{ title: string; content: string }>,
    confirmCreativePolicyInPage,
    resolvePolicyDialog,
    ensureReferencePolicy,
    ensureAiPolicyForImage,
    ensureThreeDimensionalPolicy,
    reset,
  }
}
