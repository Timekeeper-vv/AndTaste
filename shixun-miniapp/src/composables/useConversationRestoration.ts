import type { Ref } from 'vue'
import type { ConversationEvent, ConversationQuickReply, SeedreamProductionSimulationImage } from '../api/creative'
import { withProductCategoryBackReply } from './useConversationChat'

type ReadonlyValue<T> = { readonly value: T }
type AnyRef<T = any> = Ref<T>
type MessageRole = 'assistant' | 'user'

interface ProductLike {
  name?: string
}

export interface ConversationRestorationOptions {
  phase: AnyRef<string>
  mode: AnyRef<string>
  selectedProduct: AnyRef<ProductLike | null>
  material: AnyRef<string>
  materialChoice: AnyRef<string>
  productSize: AnyRef<string>
  productSizeRecommended: AnyRef<boolean>
  inspirationText: AnyRef<string>
  referenceAssetId: AnyRef<number | null>
  generatedAssetId: AnyRef<number | null>
  pendingImageJobId: AnyRef<number | null>
  pendingGenerationPrompt: AnyRef<string>
  pendingMultiViewJobId: AnyRef<number | null>
  pendingMultiViewInputAssetId: AnyRef<number | null>
  pendingMultiViewPrompt: AnyRef<string>
  previewUrl: AnyRef<string>
  referenceAnalysis: AnyRef<string>
  multiviewImages: AnyRef<any[]>
  simulationAssetId: AnyRef<number | null>
  simulationImage: AnyRef<SeedreamProductionSimulationImage | null>
  multiviewBundleId: AnyRef<number | null>
  multiviewBundleNo: AnyRef<string>
  multiviewBundleStatus: AnyRef<string>
  multiviewBundleComment: AnyRef<string>
  campaignAttached: AnyRef<boolean>
  modelInputMode: AnyRef<string>
  refinementNote: AnyRef<string>
  replacementImagePending: AnyRef<boolean>
  replacementPrompt: AnyRef<string>
  chatQuickReplies: AnyRef<ConversationQuickReply[]>
  chatStage: AnyRef<string>
  awaitingGenerationConfirmation: AnyRef<boolean>
  modeOptions: ReadonlyArray<{ key: string; title: string }>
  productByValue: (productType?: string, productKey?: string) => ProductLike | null
  imageUrl: (item: any) => string
  setModelTask: (payload: any) => void
  applyChatBrief: (payload: Record<string, any>) => void
  clearGeneratedOutputForNewDirection: () => void
  editableTarget: (value: unknown) => unknown
  resetTranscript: () => void
  addInitialMessage: () => void
  addRestoredMessage: (role: MessageRole, text: string) => unknown
  addRestoredImageMessage: (assetId: number, text?: string) => unknown
  setGenerationConfirmationReplies: () => void
}

function eventType(event: ConversationEvent) {
  return String(event?.eventType || '')
}

function payloadOf(event: ConversationEvent): Record<string, any> {
  return event?.payload && typeof event.payload === 'object' ? event.payload : {}
}

/** Rehydrates page state and transcript from the persisted event stream. */
export function useConversationRestoration(options: ConversationRestorationOptions) {
  function restoreEvent(event: ConversationEvent) {
    const payload = payloadOf(event)
    switch (eventType(event)) {
      case 'mode_selected':
        options.mode.value = payload.mode || options.mode.value
        break
      case 'product_selected':
        options.selectedProduct.value = options.productByValue(payload.productType, payload.productKey) || options.selectedProduct.value
        options.material.value = ''
        options.materialChoice.value = 'recommend'
        options.productSize.value = ''
        options.productSizeRecommended.value = false
        break
      case 'text_inspiration_submitted':
        options.inspirationText.value = String(payload.inspirationText || '')
        break
      case 'image_inspiration_uploaded':
      case 'image_inspiration_confirmed':
      case 'image_reference_replaced':
        options.mode.value = 'image'
        // An uploaded image is the primary source, but any text brief entered
        // before the upload is still a valid supplement. Clearing it here made
        // a restored session produce a different prompt from the live session.
        const restoredInspiration = String(payload.inspirationText || '').trim()
        if (restoredInspiration && !/^(?:没有(?:具体)?灵感(?:（?看看示例）?)?|无(?:具体)?灵感|没有补充|无补充|不用补充|我已上传(?:一张)?(?:灵感)?图片|已上传(?:一张)?灵感图片|上传灵感图片)[。.!！?？\s]*$/i.test(restoredInspiration)) {
          options.inspirationText.value = restoredInspiration
        }
        options.referenceAssetId.value = Number(payload.inputAssetId || payload.referenceAssetId) || options.referenceAssetId.value || null
        if (eventType(event) === 'image_reference_replaced') {
          options.replacementImagePending.value = true
          options.replacementPrompt.value = ''
          options.chatStage.value = 'need_additional_detail'
        }
        break
      case 'image_reference_replacement_prompt':
        options.replacementImagePending.value = true
        options.replacementPrompt.value = String(payload.prompt || '')
        options.chatStage.value = 'need_additional_detail'
        break
      case 'material_selected':
        options.material.value = String(payload.material || payload.materialName || options.material.value)
        options.materialChoice.value = payload.recommended ? 'recommend' : options.material.value
        break
      case 'size_selected':
        options.productSize.value = String(payload.productSize || payload.size || payload.dimensions || options.productSize.value)
        options.productSizeRecommended.value = Boolean(payload.recommended && options.productSize.value)
        break
      case 'campaign_selected':
        options.campaignAttached.value = true
        break
      case 'style_selected':
      case 'purpose_selected':
      case 'creative_direction_confirmed':
      case 'creative_direction_auto_confirmed':
        if (payload.inspirationText) options.inspirationText.value = String(payload.inspirationText)
        break
      case 'image_generation_queued':
        options.replacementImagePending.value = false
        options.replacementPrompt.value = ''
        options.pendingImageJobId.value = Number(payload.jobId) || options.pendingImageJobId.value
        options.pendingGenerationPrompt.value = String(payload.prompt || options.pendingGenerationPrompt.value)
        break
      case 'image_generation_failed':
        options.replacementImagePending.value = false
        options.replacementPrompt.value = ''
        options.pendingImageJobId.value = null
        options.pendingGenerationPrompt.value = ''
        break
      case 'image_generated':
        options.pendingImageJobId.value = null
        options.pendingGenerationPrompt.value = ''
        options.replacementImagePending.value = false
        options.replacementPrompt.value = ''
        options.generatedAssetId.value = Number(payload.generatedAssetId) || options.generatedAssetId.value
        options.previewUrl.value = options.imageUrl({ previewUrl: payload.previewUrl })
        options.referenceAnalysis.value = String(payload.referenceAnalysis || options.referenceAnalysis.value || '')
        break
      case 'image_refined':
        options.replacementImagePending.value = false
        options.replacementPrompt.value = ''
        options.generatedAssetId.value = Number(payload.generatedAssetId) || options.generatedAssetId.value
        options.previewUrl.value = options.imageUrl({ previewUrl: payload.previewUrl })
        options.referenceAnalysis.value = String(payload.referenceAnalysis || options.referenceAnalysis.value || '')
        options.refinementNote.value = ''
        break
      case 'multiview_queued':
        options.pendingMultiViewJobId.value = Number(payload.jobId) || options.pendingMultiViewJobId.value
        options.pendingMultiViewInputAssetId.value = Number(payload.inputAssetId) || options.pendingMultiViewInputAssetId.value
        options.pendingMultiViewPrompt.value = String(payload.prompt || options.pendingMultiViewPrompt.value)
        break
      case 'multiview_failed':
        options.pendingMultiViewJobId.value = null
        options.pendingMultiViewInputAssetId.value = null
        options.pendingMultiViewPrompt.value = ''
        break
      case 'multiview_generated':
        options.pendingMultiViewJobId.value = null
        options.pendingMultiViewInputAssetId.value = Number(payload.inputAssetId) || null
        options.pendingMultiViewPrompt.value = ''
        options.multiviewImages.value = Array.isArray(payload.images) ? payload.images : []
        options.simulationAssetId.value = Number(payload.simulationAssetId) > 0 ? Number(payload.simulationAssetId) : null
        options.simulationImage.value = payload.simulationImage && typeof payload.simulationImage === 'object'
          ? payload.simulationImage
          : (options.simulationAssetId.value ? { assetId: options.simulationAssetId.value, label: '生产模拟图' } : null)
        // Older events did not restore the generated source asset. Keep the
        // input asset as a fallback so the restored package remains usable.
        if (!options.generatedAssetId.value && Number(payload.inputAssetId) > 0) options.generatedAssetId.value = Number(payload.inputAssetId)
        options.multiviewBundleId.value = Number(payload.bundleId) || options.multiviewBundleId.value
        options.multiviewBundleNo.value = String(payload.bundleNo || options.multiviewBundleNo.value || '')
        options.multiviewBundleStatus.value = String(payload.bundleStatus || options.multiviewBundleStatus.value || '')
        options.multiviewBundleComment.value = String(payload.bundleComment || options.multiviewBundleComment.value || '')
        break
      case 'model_submitted':
        options.modelInputMode.value = payload.multiview ? 'multiview' : 'single'
        options.setModelTask(payload)
        break
      case 'model_completed':
        options.setModelTask({ ...payload, status: 'succeeded', progress: 100 })
        break
      case 'model_failed':
        options.setModelTask({ ...payload, status: 'failed' })
        break
      case 'chat_state':
        options.applyChatBrief(payload)
        break
      case 'chat_user_message':
        if (payload.action?.type === 'edit' && options.editableTarget(payload.action?.value)) options.clearGeneratedOutputForNewDirection()
        break
      case 'previous_step':
        if (options.editableTarget(payload.to)) options.clearGeneratedOutputForNewDirection()
        break
      default:
        break
    }
  }

  function restoreMessages(events: ConversationEvent[]) {
    options.resetTranscript()
    options.addInitialMessage()
    const hasChatTranscript = events.some(event => ['chat_user_message', 'chat_assistant_message'].includes(eventType(event)))
    const legacyConversationEvents = new Set([
      'mode_selected', 'product_selected', 'text_inspiration_submitted',
      'image_inspiration_uploaded', 'image_inspiration_confirmed',
      'material_selected', 'size_selected', 'creative_direction_confirmed', 'creative_direction_auto_confirmed',
    ])
    for (const event of events) {
      if (hasChatTranscript && legacyConversationEvents.has(eventType(event))) continue
      const payload = payloadOf(event)
      switch (eventType(event)) {
        case 'mode_selected':
          options.addRestoredMessage('user', options.modeOptions.find(item => item.key === payload.mode)?.title || String(payload.modeName || '已选择创作方式'))
          options.addRestoredMessage('assistant', '好，我们先确定产品方向。你想把它做成什么？')
          break
        case 'product_selected': {
          const product = options.productByValue(payload.productType, payload.productKey)
          if (product) options.addRestoredMessage('user', String(product.name || ''))
          if (options.mode.value === 'template') options.addRestoredMessage('assistant', `${product?.name || '这个产品'}很适合先做一版。现在选材质，我会把工艺约束一起带进提示词。`)
          else if (options.mode.value === 'text') options.addRestoredMessage('assistant', '收到。把你已有的文字灵感告诉我，不用写成复杂提示词。')
          else options.addRestoredMessage('assistant', '收到。请上传一张你有权使用的灵感图片，我会保留主体并优化成产品视觉。')
          break
        }
        case 'text_inspiration_submitted':
          if (payload.inspirationText) options.addRestoredMessage('user', String(payload.inspirationText))
          options.addRestoredMessage('assistant', '我记下了这段灵感。接下来选择材质，我会把材质、结构和生产限制一起考虑。')
          break
        case 'image_inspiration_uploaded':
          options.addRestoredImageMessage(Number(payload.inputAssetId), '已上传灵感图片')
          options.addRestoredMessage('assistant', '图片已收到。你希望它用什么材质？')
          break
        case 'image_reference_replaced':
          options.addRestoredImageMessage(Number(payload.inputAssetId), '已上传新的参考图片')
          options.addRestoredMessage('assistant', '新参考图已收到，产品方向保持不变。请补充这次生成要求，我再调用图生图。')
          break
        case 'image_reference_replacement_prompt':
          if (payload.prompt) options.addRestoredMessage('user', `新参考图生成要求：${String(payload.prompt)}`)
          options.addRestoredMessage('assistant', '好的，我会保持当前产品方向，根据新参考图重新生成。')
          break
        case 'material_selected':
          options.addRestoredMessage('user', String(payload.material || payload.materialName || options.material.value))
          options.addRestoredMessage('assistant', '材质已确认。接下来确认成品尺寸后，我会生成产品图。')
          break
        case 'size_selected':
          options.addRestoredMessage('user', String(payload.productSize || payload.size || payload.dimensions || options.productSize.value))
          options.addRestoredMessage('assistant', '尺寸已确认。我会按这个比例和可生产结构准备产品图。')
          break
        case 'creative_direction_auto_confirmed':
          options.addRestoredMessage('assistant', `我会根据你的灵感自动匹配${payload.material || options.material.value}，现在直接生成产品图。`)
          break
        case 'image_generation_queued':
          options.addRestoredMessage('assistant', '产品图已进入生成队列。离开当前页面也会继续生成，完成后会自动保存到作品库。')
          break
        case 'image_generation_failed':
          options.addRestoredMessage('assistant', `产品图本次没有生成成功。${payload.errorMessage || '可以调整描述后重新提交。'}`)
          break
        case 'image_generated':
          options.addRestoredMessage('assistant', '产品视觉已经生成并保存。下一步可以生成生产模拟图、生成 3D，或直接提交商品化申请。')
          break
        case 'image_refined':
          options.addRestoredMessage('user', `补充修改：${payload.refinementNote || '基于当前图重新生成'}`)
          options.addRestoredMessage('assistant', '新的产品视觉已经生成，旧版本仍保留在作品库。你可以继续修改，或生成生产模拟图和 3D。')
          break
        case 'multiview_queued':
          options.addRestoredMessage('assistant', '生产模拟图已进入生成队列。离开当前页面也会继续生成，完成后会自动保存到作品库。')
          break
        case 'multiview_failed':
          options.addRestoredMessage('assistant', `生产模拟图本次没有生成成功。${payload.errorMessage || '可以稍后重新提交。'}`)
          break
        case 'multiview_generated':
          options.addRestoredMessage('assistant', '生产模拟图已经保存。一张图包含正面、侧面和背面，系统也保留三张视角切片用于 3D 建模。')
          break
        case 'model_submitted':
          options.addRestoredMessage('assistant', '3D 建模任务已提交，完成后会出现在作品库。你可以在那里预览、评审并申请打样。')
          break
        case 'model_completed':
          options.addRestoredMessage('assistant', '3D 模型已经生成并保存到作品库，可以继续评审、申请打样或提交商品化报价。')
          break
        case 'model_failed':
          options.addRestoredMessage('assistant', '3D 建模没有完成，失败原因已保存。可以检查产品图后重新提交。')
          break
        case 'chat_user_message':
          if (payload.action?.type === 'image' && Number(payload.action?.value) > 0) options.addRestoredImageMessage(Number(payload.action.value), '已上传灵感图片')
          else if (payload.message) options.addRestoredMessage('user', String(payload.message))
          else if (payload.action?.label) options.addRestoredMessage('user', String(payload.action.label))
          break
        case 'chat_assistant_message':
          if (payload.text) options.addRestoredMessage('assistant', String(payload.text))
          if (Array.isArray(payload.quickReplies)) options.chatQuickReplies.value = withProductCategoryBackReply(payload.quickReplies)
          if (payload.stage) options.chatStage.value = String(payload.stage)
          if (!options.generatedAssetId.value && (payload.generationConfirmationRequired || (payload.readyToGenerate && payload.generationConfirmed !== true))) {
            options.awaitingGenerationConfirmation.value = true
            options.chatStage.value = 'confirm_before_image'
            if (!options.chatQuickReplies.value.length) options.setGenerationConfirmationReplies()
          }
          break
        default:
          break
      }
    }
  }

  function restorePhase(events: ConversationEvent[]) {
    options.phase.value = 'mode'
    for (const event of events) {
      const payload = payloadOf(event)
      switch (eventType(event)) {
        case 'mode_selected': options.phase.value = 'product'; break
        case 'product_selected': options.phase.value = options.mode.value === 'template' ? 'material' : options.mode.value === 'text' ? 'inspiration' : 'image'; break
        case 'text_inspiration_submitted': options.phase.value = 'material'; break
        case 'image_inspiration_uploaded':
          options.phase.value = 'image'; break
        case 'image_reference_replaced':
        case 'image_reference_replacement_prompt': options.phase.value = 'result'; break
        case 'image_inspiration_confirmed': options.phase.value = 'material'; break
        case 'material_selected': options.phase.value = 'material'; break
        case 'size_selected': options.phase.value = 'size'; break
        case 'creative_direction_confirmed':
        case 'creative_direction_auto_confirmed': options.phase.value = 'material'; break
        case 'image_generation_queued': options.phase.value = 'material'; break
        case 'image_generation_failed': options.phase.value = 'material'; break
        case 'image_generated': options.phase.value = 'result'; break
        case 'image_refined': options.phase.value = 'result'; break
        case 'multiview_queued': options.phase.value = 'result'; break
        case 'multiview_failed': options.phase.value = 'result'; break
        case 'multiview_generated': options.phase.value = 'multiview'; break
        case 'model_submitted':
        case 'model_completed':
        case 'model_failed': options.phase.value = 'model'; break
        case 'chat_user_message':
          if (payload.action?.type === 'edit' && options.editableTarget(payload.action?.value)) options.phase.value = 'mode'
          break
        case 'previous_step': {
          const destination = String(payload.to || '')
          if (options.editableTarget(destination)) options.phase.value = 'mode'
          else if (destination === 'result' || destination === 'multiview' || destination === 'model') options.phase.value = destination
          break
        }
        case 'chat_assistant_message':
          if (payload.stage) options.chatStage.value = String(payload.stage)
          if (!options.generatedAssetId.value && (payload.generationConfirmationRequired || (payload.readyToGenerate && payload.generationConfirmed !== true))) {
            options.chatStage.value = 'confirm_before_image'
            options.awaitingGenerationConfirmation.value = true
          } else if (payload.readyToGenerate) {
            options.chatStage.value = 'ready_for_image'
          }
          break
        default:
          break
      }
    }
  }

  return { restoreEvent, restoreMessages, restorePhase }
}
