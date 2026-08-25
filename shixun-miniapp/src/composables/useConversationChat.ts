import type { Ref } from 'vue'
import {
  sendConversationChat,
  type ConversationChatResult,
  type ConversationQuickReply,
} from '../api/creative'
import { readableErrorMessage } from '../api/client'

type ReadonlyValue<T> = { readonly value: T }
export interface ChatAction { type: string; value?: string; label?: string }

const CATEGORY_BACK_REPLY: ConversationQuickReply = {
  label: '返回选择大品类',
  type: 'edit',
  value: 'product',
}

/** Keeps older saved category turns navigable after this control was added. */
export function withProductCategoryBackReply(replies: ConversationQuickReply[]) {
  const hasProductChoices = replies.some(item => item.type === 'product')
  const hasBackReply = replies.some(item => item.type === 'edit' && item.value === 'product')
  return hasProductChoices && !hasBackReply ? [CATEGORY_BACK_REPLY, ...replies] : replies
}

interface ProductLike { key?: string; name?: string }

export interface ConversationChatOptions {
  sessionId: ReadonlyValue<number | null>
  busy: ReadonlyValue<boolean>
  chatSending: Ref<boolean>
  quickReplySubmitting: Ref<boolean>
  chatInput: Ref<string>
  chatQuickReplies: Ref<ConversationQuickReply[]>
  awaitingGenerationConfirmation: Ref<boolean>
  chatStage: Ref<string>
  autoGenerationInFlight: Ref<boolean>
  productSize: Ref<string>
  productSizeRecommended: Ref<boolean>
  generatedAssetId: ReadonlyValue<number | null>
  phase: ReadonlyValue<string>
  selectedProduct: Ref<ProductLike | null>
  inspirationText: Ref<string>
  referencePath: Ref<string>
  referenceAssetId: Ref<number | null>
  ensureSession: () => Promise<boolean>
  applyChatBrief: (brief: Record<string, any> | undefined, preserveExisting?: boolean, preserveRecommendedSize?: boolean) => void
  addMessage: (role: 'assistant' | 'user', text: string) => number
  addAssistantMessage: (text: string) => unknown
  setChatThinking: (active: boolean, label?: string) => void
  setGenerationConfirmationReplies: () => void
  hasReferenceImage: () => boolean
  activateReferenceImageMode: () => void
  preserveReferenceImageMode: () => boolean
  hasCompleteLocalGenerationBrief: () => boolean
  chooseRecommendedSizeLocally: (label?: string) => Promise<void>
  recommendedProductSize: () => string
  saveCreativeEventBestEffort: (step: string, eventType: string, payload?: Record<string, any>) => Promise<void>
  generateProductImage: () => Promise<void>
  pickInspirationImage: () => Promise<void>
  generateMultiView: () => Promise<void>
  submitMultiViewReview: () => Promise<void>
  applyMultiViewProduction: () => void
  generateModel: () => Promise<void>
  openCommercial: () => void
  goWorks: () => void
  startRefinement: () => void
  showTemplateDeveloping: () => void
  removeOptimisticMessage?: (id: number) => void
  clearChatDraft?: () => void
  onMissingText?: () => void
  onChatError?: (message: string, error: any) => void
  sendChat?: typeof sendConversationChat
}

export function useConversationChat(options: ConversationChatOptions) {
  const sendChat = options.sendChat || sendConversationChat

  function thinkingLabelFor(action?: ChatAction, message = '') {
    const type = String(action?.type || '')
    if (type === 'category' || type === 'product') return '正在整理产品方向'
    if (type === 'material' || type === 'recommend_material') return '正在匹配材质与生产工艺'
    if (type === 'size') return '正在核对成品尺寸与比例'
    if (type === 'adopt_direction') return '正在把建议整理为创作方案'
    if (type === 'upload' || /图片|照片|草图|参考图/.test(message)) return '正在读取参考图片和主体特征'
    return '正在理解你的想法'
  }

  function isGenerationConfirmationText(message: string) {
    const value = message.trim()
    return Boolean(value) && (/.*(没有|无|不需要|不用).*(补充|修改|添加|意见).*/.test(value)
      || /.*(直接|开始|确认).*(生成|出图).*/.test(value)
      || /^(没有|没有了|无|无了|就这样|不用补充)$/.test(value))
  }

  function isRecommendedSizeTurn(stageBeforeRequest: string, message: string, action?: ChatAction) {
    if (action?.type === 'size' && String(action.value || '').toLowerCase() === 'recommend') return true
    const value = message.trim()
    return stageBeforeRequest === 'need_size'
      && value.length <= 32
      && /(推荐|帮我选|你来选|按推荐规格)/.test(value)
  }

  function shouldPreserveRecommendedSize(action: ChatAction | undefined, message: string) {
    if (!options.productSizeRecommended.value || !options.productSize.value) return false
    const type = String(action?.type || '')
    const value = String(action?.value || '').toLowerCase()
    if (type === 'product' || type === 'category' || type === 'size' || (type === 'edit' && value === 'size')) return false
    if (/(尺寸|规格|大小|做多大|改成|换成).*(?:\d|a[3-6]|推荐)/i.test(message)) return false
    return true
  }

  async function sendChatTurn(message: string, action?: ChatAction, turnOptions: { skipUserMessage?: boolean } = {}) {
    if (!(await options.ensureSession()) || !options.sessionId.value || options.chatSending.value) return false
    const visibleMessage = message.trim()
    const displayMessage = visibleMessage || String(action?.label || '').trim()
    const stageBeforeRequest = options.chatStage.value
    const recommendedSizeTurn = isRecommendedSizeTurn(stageBeforeRequest, visibleMessage, action)
    const preserveRecommendedSize = shouldPreserveRecommendedSize(action, visibleMessage)
    const actionType = String(action?.type || '')
    const actionValue = String(action?.value || '')
    const productSizeBeforeChat = options.productSize.value
    const productSizeWasRecommendedBeforeChat = options.productSizeRecommended.value

    if (actionType === 'edit' && actionValue === 'inspiration') {
      options.referencePath.value = ''
      options.referenceAssetId.value = null
      options.inspirationText.value = ''
    } else if (actionType === 'image' && Number(actionValue) > 0) {
      options.activateReferenceImageMode()
    }
    if (recommendedSizeTurn && options.selectedProduct.value) {
      await options.chooseRecommendedSizeLocally(displayMessage || '按推荐规格')
      return true
    }

    const productBeforeChat = options.selectedProduct.value
    const optimisticMessageId = displayMessage && !turnOptions.skipUserMessage
      ? options.addMessage('user', displayMessage)
      : null
    let succeeded = false
    options.chatSending.value = true
    options.setChatThinking(true, thinkingLabelFor(action, visibleMessage))
    try {
      const result: ConversationChatResult = await sendChat(options.sessionId.value, { message: visibleMessage, action })
      options.applyChatBrief(result.brief, recommendedSizeTurn, preserveRecommendedSize)
      if (actionType === 'image' || options.hasReferenceImage()) {
        options.preserveReferenceImageMode()
        // The image is the primary source, but a user's existing text remains
        // a valid supplement. Clearing it after the upload made the same
        // reference image produce a different prompt depending on upload step.
      }

      const materialRecommendationTurn = actionType === 'material' && actionValue.toLowerCase() === 'recommend'
      let materialRecommendationResolved = false
      if (materialRecommendationTurn && options.selectedProduct.value
        && (!productSizeBeforeChat || productSizeWasRecommendedBeforeChat)) {
        const localSize = options.recommendedProductSize()
        if (localSize) {
          options.productSize.value = localSize
          options.productSizeRecommended.value = true
          materialRecommendationResolved = true
          await options.saveCreativeEventBestEffort('size', 'size_selected', {
            productKey: options.selectedProduct.value?.key,
            productType: options.selectedProduct.value?.name,
            productSize: localSize,
            recommended: true,
            source: 'miniapp_catalog_material_recommend',
          })
        }
      }
      if (recommendedSizeTurn) {
        if (!options.selectedProduct.value && productBeforeChat) options.selectedProduct.value = productBeforeChat
        if (!options.productSize.value) options.productSize.value = options.recommendedProductSize()
        if (options.productSize.value) {
          options.productSizeRecommended.value = true
          await options.saveCreativeEventBestEffort('size', 'size_selected', {
            productKey: options.selectedProduct.value?.key,
            productType: options.selectedProduct.value?.name,
            productSize: options.productSize.value,
            recommended: true,
            source: 'miniapp_catalog_fallback',
          })
        }
      }

      options.chatStage.value = String(result.stage || 'understanding')
      options.chatQuickReplies.value = withProductCategoryBackReply(Array.isArray(result.quickReplies) ? result.quickReplies : [])
      const recommendedSizeResolved = (recommendedSizeTurn || preserveRecommendedSize || materialRecommendationResolved)
        && result.stage !== 'need_additional_detail'
        && options.hasCompleteLocalGenerationBrief()
      if (recommendedSizeResolved) {
        options.chatStage.value = 'confirm_before_image'
        options.setGenerationConfirmationReplies()
      }
      const assistantText = recommendedSizeResolved
        ? `根据${options.selectedProduct.value?.name || '当前产品'}的常用打样规格，我推荐 ${options.productSize.value}，已为你设置并写入生成提示词。生成前还有需要补充的吗？`
        : String(result.assistantText || '')
      if (assistantText) options.addAssistantMessage(assistantText)
      succeeded = true
      options.setChatThinking(false)

      const explicitlyConfirmed = action?.type === 'confirm_generate' || isGenerationConfirmationText(visibleMessage)
      const additionalDetailRequired = result.stage === 'need_additional_detail'
      const confirmationRequired = !additionalDetailRequired && (recommendedSizeResolved || Boolean(result.generationConfirmationRequired) || result.stage === 'confirm_before_image')
      options.awaitingGenerationConfirmation.value = confirmationRequired
      if (confirmationRequired && !options.chatQuickReplies.value.length) options.setGenerationConfirmationReplies()
      if (result.readyToGenerate && explicitlyConfirmed && !options.generatedAssetId.value && options.phase.value !== 'result' && !options.autoGenerationInFlight.value) {
        options.awaitingGenerationConfirmation.value = false
        options.autoGenerationInFlight.value = true
        try { await options.generateProductImage() } finally { options.autoGenerationInFlight.value = false }
      } else if (result.readyToGenerate && !explicitlyConfirmed && !options.generatedAssetId.value && options.phase.value !== 'result') {
        options.awaitingGenerationConfirmation.value = true
        options.chatStage.value = 'confirm_before_image'
        options.setGenerationConfirmationReplies()
        if (!recommendedSizeResolved) options.addAssistantMessage('生成前确认一下，还有需要补充的吗？没有的话点击“没有补充，开始生成”。')
      }
    } catch (error: any) {
      options.setChatThinking(false)
      if (optimisticMessageId) {
        // The page owns the transcript array, so the callback can remove the
        // optimistic item without exposing its storage shape here.
        options.removeOptimisticMessage?.(optimisticMessageId)
      }
      const errorMessage = readableErrorMessage(error, '创作服务暂时不可用，当前已输入内容会保留，请稍后重试。')
      console.warn('[conversation-create] chat failed', { message: errorMessage, statusCode: error?.statusCode || 0 })
      options.onChatError?.(errorMessage, error)
    } finally {
      options.setChatThinking(false)
      options.chatSending.value = false
    }
    return succeeded
  }

  async function handleQuickReply(item: ConversationQuickReply) {
    if (options.busy.value || options.chatSending.value || options.quickReplySubmitting.value) return
    options.quickReplySubmitting.value = true
    const type = String(item.type || '')
    try {
      if (type === 'upload') return await options.pickInspirationImage()
      if (type === 'multiview') return await options.generateMultiView()
      if (type === 'bundle_review') return await options.submitMultiViewReview()
      if (type === 'bundle_production') return options.applyMultiViewProduction()
      if (type === 'model') return await options.generateModel()
      if (type === 'commercial') return options.openCommercial()
      if (type === 'works') return options.goWorks()
      if (type === 'refine') return options.startRefinement()
      if (type === 'size' && String(item.value || '').toLowerCase() === 'recommend') {
        return await options.chooseRecommendedSizeLocally(String(item.label || '按推荐规格'))
      }
      if (type === 'template') return options.showTemplateDeveloping()
      if (type === 'confirm_generate' && options.productSizeRecommended.value && options.hasCompleteLocalGenerationBrief() && !options.generatedAssetId.value) {
        options.addMessage('user', String(item.label || '没有补充，开始生成'))
        options.awaitingGenerationConfirmation.value = false
        options.chatStage.value = 'ready_for_image'
        options.chatQuickReplies.value = []
        options.addAssistantMessage('好的，我按当前推荐规格开始生成产品图。')
        await options.saveCreativeEventBestEffort('chat', 'chat_user_message', {
          message: '',
          action: { type, value: String(item.value || ''), label: String(item.label || '') },
          localGeneration: true,
        })
        return await options.generateProductImage()
      }
      if (type === 'confirm_generate' || type === 'add_detail') {
        return await sendChatTurn('', { type, value: String(item.value || ''), label: String(item.label || '') })
      }
      if (type === 'text' && !String(item.value || '').trim()) return options.onMissingText?.()
      const label = String(item.label || item.value || '').trim()
      const message = type === 'text' ? label : ''
      return await sendChatTurn(message, { type, value: String(item.value || ''), label })
    } finally {
      options.quickReplySubmitting.value = false
    }
  }

  async function submitChatInput() {
    const value = options.chatInput.value.trim()
    if (!value || options.busy.value || options.chatSending.value || options.quickReplySubmitting.value) return
    options.chatInput.value = ''
    const sent = await sendChatTurn(value)
    if (sent && !options.chatInput.value.trim()) options.clearChatDraft?.()
    else if (!options.chatInput.value.trim()) options.chatInput.value = value
    return sent
  }

  return { sendChatTurn, handleQuickReply, submitChatInput }
}
