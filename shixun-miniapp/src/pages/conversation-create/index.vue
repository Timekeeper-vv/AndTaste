<template>
  <view class="page chat-experience">
    <scroll-view class="chat" scroll-y :scroll-into-view="scrollIntoView" scroll-with-animation>
      <view class="workspace-intro">
        <view class="workspace-intro-top"><view class="online-mark"><view class="online-dot" /><text>AI 工作台</text></view><text class="workspace-ref">{{ sessionId ? `项目 ${sessionId}` : '新项目' }}</text></view>
        <text class="workspace-title">把灵感说出来，剩下的交给我</text>
        <text class="workspace-subtitle">我会帮你整理产品方向、生成视觉，并继续推进三视图、3D 和打样。</text>
        <view v-if="selectedProduct || material" class="brief-strip">
          <view v-if="selectedProduct" class="brief-chip"><text>产品</text><text>{{ selectedProduct.name }}</text></view>
          <view v-if="material" class="brief-chip"><text>材质</text><text>{{ material }}</text></view>
          <view v-if="productSize" class="brief-chip"><text>尺寸</text><text>{{ productSize }}</text></view>
          <view v-if="mode" class="brief-chip muted"><text>{{ mode === 'image' ? '参考图' : '文字灵感' }}</text></view>
        </view>
        <view v-if="campaignContext" class="campaign-strip">
          <view><text>优先征集</text><text>{{ campaignContext.title }}</text><text>面向 {{ campaignContext.targetName }} · {{ campaignContext.collectionStyle }}</text></view>
          <text>通过 +{{ campaignContext.rewardAmount }} 积分</text>
        </view>
      </view>

      <AiGeneratedNotice class="ai-disclosure" compact description="对话建议、提示词和后续生成的图片、三视图、3D 原型均可能由人工智能生成，仅供创作参考，商业使用前请人工复核。" />

      <view v-for="item in messages" :id="`message-${item.id}`" :key="item.id" class="message-row" :class="item.role">
        <view v-if="item.role === 'assistant'" class="message-avatar assistant-avatar">之</view>
        <view class="message-content">
          <view class="message-meta"><text>{{ item.role === 'assistant' ? '之间智造' : '我' }}</text><text v-if="item.role === 'assistant'">AI 助手</text></view>
          <view class="bubble" :class="{ 'image-bubble': item.imageUrl || item.imageAssetId }">
            <image
              v-if="item.imageUrl"
              class="message-image"
              :src="item.imageUrl"
              mode="aspectFit"
              @tap="previewMessageImage(item)"
            />
            <view v-else-if="item.imageAssetId" class="message-image-loading" @tap="previewMessageImage(item)">
              <text>图片加载中</text>
            </view>
            <!-- selectable uses WeChat's native text-selection menu, which
                 remains available even when the JS clipboard scope is not
                 declared in the mini-program privacy guide. -->
            <text v-if="item.text" class="message-text" selectable>{{ item.text }}</text>
            <view v-if="item.text && !item.imageUrl && !item.imageAssetId" class="message-actions">
              <text class="message-copy" aria-label="复制这段文字" @tap.stop="copyMessageText(item)">复制</text>
            </view>
            <view v-if="item.imageUrl || item.imageAssetId" class="message-image-footer">
              <text :class="{ failed: item.imageState === 'failed' }">{{ item.imageState === 'uploading' ? '正在上传灵感图片…' : item.imageState === 'failed' ? '上传失败，请重新选择' : '已上传灵感图片 · 点击查看大图' }}</text>
              <text v-if="item.role === 'user'" class="message-image-reselect" @tap.stop="pickInspirationImage">重新选择</text>
            </view>
          </view>
        </view>
        <view v-if="item.role === 'user'" class="message-avatar user-avatar">我</view>
      </view>

      <view v-if="chatThinking" id="chat-thinking" class="thinking-row" aria-label="之间正在思考">
        <view class="message-avatar assistant-avatar thinking-avatar">之</view>
        <view class="thinking-content">
          <view class="thinking-bubble">
            <view class="thinking-title-row"><text class="thinking-title">之间正在思考</text><view class="thinking-dots" aria-hidden="true"><view class="thinking-dot" /><view class="thinking-dot" /><view class="thinking-dot" /></view></view>
            <text class="thinking-detail">{{ thinkingLabel }}</text>
          </view>
        </view>
      </view>

      <view v-if="phase === 'result'" id="result-output" class="output-surface">
        <view class="output-header"><view><text class="surface-kicker">IMAGE OUTPUT</text><text class="surface-title">产品视觉已完成</text></view><view class="output-status"><view class="status-check">✓</view><text>已保存</text></view></view>
        <view class="visual-frame"><image v-if="previewUrl" class="result-image" :src="previewUrl" mode="aspectFit" @tap="previewImage" /><view v-else class="result-placeholder"><text>{{ selectedProduct?.mark || '作' }}</text><text>作品已保存到作品库</text></view><view class="visual-badge">AI 生成</view></view>
        <view class="output-info"><view><text>{{ selectedProduct?.name || '文创产品' }}</text><text>{{ material || '材质待定' }} · {{ productSize || '尺寸待定' }} · {{ mode === 'image' ? '参考图改造' : '文字生图' }}</text></view><text class="output-open" @tap="previewImage">查看大图 ›</text></view>
        <view v-if="refiningImage" class="refinement-panel"><view class="refinement-heading"><view><text class="surface-kicker">REFINE THIS IMAGE</text><text>告诉我哪里不满意</text></view><text class="refinement-close" @tap="cancelRefinement">×</text></view><textarea v-model="refinementNote" maxlength="500" auto-height class="text-input refinement-input" placeholder="例如：保留主体和构图，把边缘改得更简洁，去掉文字。" /><view class="input-foot"><text>{{ refinementNote.length }}/500</text><button class="dark-button" :disabled="!refinementNote.trim() || busy" :loading="busy" @tap="regenerateWithRefinement">基于当前图重新生成</button></view></view>
        <view v-else class="output-actions"><view class="output-action primary" @tap="generateMultiView"><view class="action-icon">观</view><view><text>生成三视图</text><text>补全结构视角</text></view><text class="action-arrow">›</text></view><view class="output-action" @tap="startRefinement"><view class="action-icon warm">改</view><view><text>不满意，继续修改</text><text>基于当前图再生成</text></view><text class="action-arrow">›</text></view><view class="output-action" @tap="generateModel"><view class="action-icon dark">3D</view><view><text>单图生成 3D</text><text>直接创建产品原型</text></view><text class="action-arrow">›</text></view><view class="output-action disabled"><view class="action-icon gold">样</view><view><text>完成三视图或 3D 原型后打样</text><text>当前产品图仅用于继续创作</text></view></view></view>
      </view>

      <view v-if="phase === 'multiview'" id="multiview-output" class="output-surface">
        <view class="output-header"><view><text class="surface-kicker">MULTI-VIEW OUTPUT</text><text class="surface-title">三视图已完成</text></view><view class="output-status"><view class="status-check">✓</view><text>3 张已保存</text></view></view>
        <text class="surface-note">正面、侧面和背面已作为一个作品包保存，审核时会整包查看，不会拆成三条作品。</text>
        <view class="view-grid"><view v-for="item in multiviewImages" :key="item.assetId" class="view-card" @tap="previewMultiViewImage(item)"><image v-if="imageUrl(item)" :src="imageUrl(item)" mode="aspectFit" /><view v-else class="view-placeholder"><text>{{ item.label }}</text><text>已保存</text></view><view class="view-label"><text>{{ item.label }}</text><text>查看大图 ›</text></view></view></view>
        <view class="bundle-review-state" :class="`bundle-${multiviewBundleStatus || 'draft'}`">
          <view><text class="bundle-review-label">作品包状态</text><text class="bundle-review-title">{{ multiviewBundleStatusText }}</text></view>
          <text v-if="multiviewBundleNo" class="bundle-review-no">{{ multiviewBundleNo }}</text>
        </view>
        <text v-if="multiviewBundleStatus === 'rejected' && multiviewBundleComment" class="bundle-review-comment">未通过原因：{{ multiviewBundleComment }}</text>
        <button v-if="canSubmitMultiViewReview" class="dark-button full-button" :loading="multiviewBundleSubmitting" @tap="submitMultiViewReview">提交三视图审核 <text>›</text></button>
        <button v-else-if="multiviewBundleStatus === 'review'" class="outline-button full-button" disabled>审核中，请等待平台反馈</button>
        <template v-else-if="multiviewBundleStatus === 'approved'">
          <button class="dark-button full-button" @tap="applyMultiViewProduction">申请打样 <text>›</text></button>
          <button class="outline-button full-button" :loading="busy" @tap="generateModel">继续生成 3D 原型</button>
        </template>
      </view>

      <view v-if="phase === 'model'" id="model-output" class="output-surface">
        <view class="output-header"><view><text class="surface-kicker">3D PROTOTYPE</text><text class="surface-title">{{ modelTaskTitle }}</text></view><view class="model-state" :class="{ done: isModelTaskSucceeded, failed: isModelTaskFailed }">{{ isModelTaskSucceeded ? '完成' : isModelTaskFailed ? '失败' : '处理中' }}</view></view>
        <view class="model-summary"><view class="model-mark">3D</view><view><text>{{ modelTaskDescription }}</text><text>{{ modelTaskDetail }}</text></view></view>
        <view v-if="modelTask" class="model-progress"><view class="progress-row"><text>建模进度</text><text>{{ normalizedModelProgress }}%</text></view><view class="model-progress-track"><view class="model-progress-value" :style="{ width: `${normalizedModelProgress}%` }" /></view></view>
        <text v-if="modelTask?.errorMessage" class="model-error">{{ modelTask.errorMessage }}</text>
        <button v-if="modelTask && !isModelTaskTerminal" class="outline-button full-button" :loading="modelRefreshing" @tap="refreshModelTask">刷新进度</button>
        <button v-if="isModelTaskFailed" class="dark-button full-button" :loading="busy" @tap="generateModel">重新提交 3D 建模</button>
        <button class="dark-button full-button" @tap="goWorks">{{ isModelTaskSucceeded ? '查看已完成的 3D 作品' : '查看我的作品' }}</button>
        <button v-if="isModelTaskSucceeded" class="outline-button full-button" @tap="openCommercial">申请打样 / 商品化</button>
      </view>

      <view id="bottom-anchor" class="bottom-anchor" />
    </scroll-view>

    <view v-if="busy" class="loading-bar"><view class="loading-spinner" aria-hidden="true" /><view><text class="loading-title">之间正在处理</text><text>{{ busyMessage }}</text></view></view>

    <view class="composer-dock">
      <view class="composer-context"><view class="context-live" /><text>{{ chatStageLabel }}</text><text v-if="selectedProduct" class="context-product">· {{ selectedProduct.name }}</text><text v-if="chatSending" class="context-working">处理中</text></view>
      <scroll-view v-if="chatQuickReplies.length" scroll-x class="quick-reply-list" :show-scrollbar="false"><view class="quick-reply-track"><view v-for="item in chatQuickReplies" :key="`${item.type}-${item.value}-${item.label}`" class="quick-reply" :class="{ confirm: item.type === 'confirm_generate', secondary: item.type === 'add_detail', disabled: busy || chatSending || quickReplySubmitting }" :aria-label="item.label" @tap="handleQuickReply(item)"><text class="quick-reply-mark">{{ quickReplyMark(item.type) }}</text><text>{{ item.label }}</text></view></view></scroll-view>
      <view class="chat-input-row"><button class="chat-upload-button" :disabled="busy || chatSending || quickReplySubmitting" aria-label="上传灵感图片" @tap="pickInspirationImage">＋</button><input v-model="chatInput" class="chat-input" maxlength="1200" confirm-type="send" placeholder="描述你的灵感，或直接回答上面的问题" @confirm="submitChatInput" /><button class="chat-send-button" :class="{ ready: chatInput.trim() }" :disabled="!chatInput.trim() || busy || chatSending || quickReplySubmitting" aria-label="发送" @tap="submitChatInput">↑</button></view>
      <view class="composer-footer"><text>AI 生成内容 · 请在商业使用前人工复核</text><text>{{ chatInput.length }}/1200</text></view>
    </view>

    <view class="bottom-actions"><button v-if="canGoPrevious" :disabled="busy || saving || chatSending" @tap="goPreviousStep"><text>‹</text>{{ previousActionLabel }}</button><button @tap="goWorks"><text>▣</text>作品库</button><button class="restart-action" @tap="restart"><text>＋</text>重新开始</button></view>

    <view v-if="policyDialog" class="policy-mask" @tap="resolvePolicyDialog(false)">
      <view class="policy-dialog" @tap.stop>
        <view class="policy-dialog-head"><view><text class="surface-kicker">BEFORE YOU CREATE</text><text>AI生成提示</text></view><text>提交前确认</text></view>
        <text class="policy-dialog-title">{{ activePolicy.title }}</text>
        <scroll-view class="policy-dialog-copy" scroll-y><text>{{ activePolicy.content }}</text></scroll-view>
        <view class="policy-dialog-actions"><button class="policy-cancel" @tap="resolvePolicyDialog(false)">暂不继续</button><button class="policy-confirm" @tap="resolvePolicyDialog(true)">我已阅读并继续</button></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { onHide, onLoad, onUnload } from '@dcloudio/uni-app'
import AiGeneratedNotice from '../../components/AiGeneratedNotice.vue'
import { getSelectionOptions, type SelectionOption } from '../../api/selection'
import {
  createConversation,
  createMultiViewBundle,
  createModel,
  createSeedreamMultiView,
  getAssetPreviewAccess,
  getConversation,
  getConversations,
  getMyMultiViewBundles,
  getTripoModelTask,
  optimizeImageEditPrompt,
  optimizeImagePrompt,
  saveConversationEvent,
  sendConversationChat,
  submitMultiViewBundleReview,
  uploadReference,
  type ConversationSession,
  type ConversationQuickReply,
  type CreatorCampaign,
  type MultiViewBundle,
  type SeedreamMultiViewImage,
} from '../../api/creative'
import { DEFAULT_SEEDREAM_IMAGE_SIZE, apiUrl, createReferenceToImage, createTextToImage, getArkImageJob, readableErrorMessage, waitForArkImageJob } from '../../api/client'
import { CREATIVE_POLICY_VERSION, getCreativePolicy, type CreativePolicyKey } from '../../utils/compliance'
import { requireSession } from '../../utils/session'

type Phase = 'mode' | 'product' | 'inspiration' | 'image' | 'material' | 'size' | 'result' | 'multiview' | 'model'
type Mode = 'template' | 'text' | 'image'
type EditableBriefField = 'product' | 'inspiration' | 'material' | 'size'
interface Message {
  id: number
  role: 'assistant' | 'user'
  text: string
  imageUrl?: string
  imageAssetId?: number
  imageState?: 'uploading' | 'ready' | 'failed'
}
interface ProductOption {
  key: string
  name: string
  mark: string
  desc: string
  process: string
  categoryKey: string
  categoryName: string
  materials: MaterialOption[]
  /** Catalog specification used by the local "recommend size" fallback. */
  specification?: string
  recommendedSize?: string
}
interface MaterialOption { name: string; note: string; color: string }
interface ModelTask { jobId: number; status: string; progress: number; assetId?: number | null; previewUrl?: string; errorMessage?: string }
type CampaignContext = CreatorCampaign & { sessionId?: number }

const SEEDREAM_IMAGE_SIZE = DEFAULT_SEEDREAM_IMAGE_SIZE
const REFERENCE_PROMPT_GUARD = 'The attached reference image is the primary visual source of truth. Preserve the same recognizable subject identity, silhouette, proportions, markings, dominant colors and distinctive motifs; transform it into the requested finished product instead of replacing it with an unrelated subject or copying the raw photo unchanged.'
const PRODUCT_ROLE_PROMPT = '【角色】你是专业产品设计师 + AI 图像工程师，正在为电商平台制作真实、可量产、可打样的文创产品主图。'
const PRODUCT_PRESENTATION_GUARD = '【参考图转化原则】上传参考图只提供主体、轮廓、颜色、纹样和文化识别点；必须改变原始载体、原始场景和原始画面用途，把这些视觉元素重构到目标产品上。主体要成为产品的主要视觉或结构，不得只贴一个小 logo，也不得原图不变。'
const PRODUCT_FRAME_GUARD = '【构图规则】一件完整成品居中，占画面约 75%（允许 70%-80%），边缘留白不超过 10%；使用正方形或 4:5 电商商品摄影构图。背景只能是纯白或浅灰，不保留天空、山、云、建筑、原始场景或手机截图比例。'
const PRODUCT_OUTPUT_NEGATIVE = 'phone screenshot, smartphone, mobile screen, app interface, status bar, media player, playback controls, progress bar, interface buttons, black UI frame, screen frame, phone frame, raw screenshot, unchanged reference image, near duplicate, collage, split screen, flat poster, flat design board, label sheet, label-only artwork, tiny isolated motif, floating logo, cropped product, incomplete product, excessive empty background, narrow portrait strip, yellow cast, sepia, monochrome wash, sky, cloud, mountain, landscape scenery, unrelated object, external watermark'
const PRODUCT_SELF_CHECK = '【交付前自检】确认目标产品形态、材质、完整轮廓、主体占比、白/浅灰背景和成品规格全部成立；不满足时优先重新构建设计，不要输出原图、截图、海报或平面标签稿。'
const CLIENT_PROMPT_BUDGET = 1400

interface ProductFormProfile {
  key: string
  prompt: string
  negative: string
  recommendedSize?: string
}

const DEFAULT_PRODUCT_FORM_PROFILE: ProductFormProfile = {
  key: 'general',
  prompt: '把它做成完整、可识别、可量产的真实实体文创产品，明确产品轮廓、功能结构、合理厚度、圆角和实际材质表面；参考图元素必须作为产品的主要视觉或结构细节，而不是孤立的小图案。',
  negative: 'abstract pattern only, tiny isolated motif, flat poster, unfinished concept board, unclear product form, random material substitution',
}

const PRODUCT_FORM_PROFILES: Array<{ match: RegExp; profile: ProductFormProfile }> = [
  {
    // Keep this focused on the product identity.  The old `饮品` term also
    // appeared in the food category label and made tea/chocolate products
    // inherit a bottle form and bottle dimensions.
    match: /矿泉水|瓶装水|饮用水|水瓶|果汁|饮料|汽水|water\s*bottle|beverage|juice/i,
    profile: {
      key: 'bottle',
      recommendedSize: '500mL 圆柱瓶（直径约65mm，高约210mm）',
      prompt: '完整、直立、可识别的圆柱瓶实体（cylindrical bottle）；瓶底、瓶身、瓶肩、瓶口和瓶盖完整可见，瓶身有真实液体与环绕瓶标，参考图中的主体/核心视觉元素要作为瓶标主视觉或瓶身大面积印花，不能缩成角落小 logo；展示 3/4 角度的真实商品摄影，保持可灌装、可量产结构。',
      negative: 'flat label sheet, flat poster, label-only artwork, tiny logo on a blank bottle, incomplete bottle, cropped bottle, box-only packaging, pouch, carton, wide flat package, yellow cast, sepia',
    },
  },
  {
    match: /毛绒钥匙扣|毛绒挂件/i,
    profile: {
      key: 'plush_keychain',
      recommendedSize: '高约100mm（含挂环）',
      prompt: '完整立体填充毛绒挂件/钥匙扣；使用布料裁片、填充体积、短绒面、缝线、刺绣或印花五官，并带真实挂环和连接位；把参考主体转成玩具轮廓与表面图案，不能只生成平面插画。',
      negative: 'flat illustration, flat poster, hard plastic statue, metal badge, label sheet, missing ring, tiny motif only',
    },
  },
  {
    // “毛绒” can describe a pen or pencil case in the catalog. Require the
    // product carrier (toy/doll/plush) so a material adjective cannot replace
    // the selected product form.
    match: /毛绒玩具|毛绒公仔|毛绒娃娃|毛绒玩偶|布偶|plush\s*(toy|doll)|stuffed\s*(toy|animal)|soft\s*toy/i,
    profile: {
      key: 'plush',
      recommendedSize: '高约130mm',
      prompt: '完整立体填充毛绒玩具；使用布料裁片、柔软填充体积、合理缝线、短绒或超柔绒面、刺绣五官和安全软体结构，明确头身、四肢、耳朵/尾巴等分件；参考主体必须成为玩具的轮廓、刺绣或印花主视觉，不能只做平面海报。',
      negative: 'flat illustration, flat poster, hard plastic shell, metal body, ceramic statue, glossy hard surface, tiny motif only',
    },
  },
  {
    match: /马克杯|咖啡杯|茶杯|水杯|mug|cup/i,
    profile: {
      key: 'mug',
      recommendedSize: '直径约80mm，高约95mm',
      prompt: '完整可使用的马克杯/饮品杯；杯体、开口、杯腔、杯底和真实把手完整可见，参考元素以环绕杯身的弧面印刷、釉上彩或浮雕呈现，展示真实器物的厚度、容积和稳定底部，不是平面海报或单独标签。',
      negative: 'flat poster, flat label sheet, handle missing, incomplete cup, floating object, tiny motif only',
    },
  },
  {
    match: /明信片|贺卡|卡片|postcard|greeting card/i,
    profile: {
      key: 'postcard',
      recommendedSize: 'A6（105×148mm）',
      prompt: '一张真实可生产的明信片/卡片成品；展示完整卡纸轮廓、真实纸张厚度、裁切边和正面印刷构图，可有轻微立体透视或桌面商品摄影；把参考图元素重新编排为卡片正面设计，不直接复刻手机截图或原图载体。',
      negative: 'phone screenshot, unchanged photo, smartphone frame, app controls, label sheet, poster mockup, missing card edges, cropped card',
    },
  },
  {
    match: /钥匙扣|挂件|keychain|key ring/i,
    profile: {
      key: 'keychain',
      recommendedSize: '50×50×4mm（主体，含挂环另计）',
      prompt: '完整可随身使用的钥匙扣/挂件；主体有清晰轮廓、合理耐用厚度、圆角、真实挂孔和连接环/链条，参考主体作为大面积图案或立体轮廓落在成品上，展示完整主体和挂环，不是独立小图标。',
      negative: 'flat label sheet, poster, missing hanging hole, missing ring, tiny isolated motif, fragile paper-only sheet',
    },
  },
  {
    // `magnetic` is a material/property (for example a magnetic ruler), not
    // a fridge magnet. Match the carrier word instead of that substring.
    match: /冰箱贴|磁贴|fridge\s*magnet/i,
    profile: {
      key: 'magnet',
      recommendedSize: '60×60×4mm',
      prompt: '完整掌心尺寸的冰箱贴成品；正面是清晰的文化图形或浅浮雕，边缘有合理厚度和圆角，背面应有平整稳定的磁铁粘贴位；参考主体要占据正面主要面积，不是平面海报或孤立 logo。',
      negative: 'flat poster, paper-only card, missing magnetic backing, tiny isolated motif, oversized sculpture',
    },
  },
  {
    match: /徽章|胸针|纪念章|贵金属章|贵金属币|徽章|badge|brooch|medal|coin|pin/i,
    profile: {
      key: 'badge',
      recommendedSize: '直径约58mm、厚约3mm',
      prompt: '完整可生产的徽章/纪念章/胸针；有明确金属外轮廓、合理厚度、浅浮雕或珐琅分色，背面有真实别针/固定结构（硬币则为稳定平面边缘）；参考主体应成为正面主要图案而不是小角落装饰。',
      negative: 'flat poster, paper card, missing pin or edge, tiny isolated motif, soft plush body, food object',
    },
  },
  {
    match: /书签|bookmark/i,
    profile: {
      key: 'bookmark',
      recommendedSize: '40×120×1.2mm',
      prompt: '完整可使用的书签；保持细长平面比例、真实纸张/金属/亚克力厚度、圆润裁切边和可选挂穗孔，参考元素沿书签正面完整展开，不能变成厚重摆件或手机截图。',
      negative: 'phone screenshot, thick statue, oversized 3D volume, missing bookmark silhouette, tiny isolated motif',
    },
  },
  {
    // Do not use bare “包/袋”: names such as “袋泡茶” and “贴纸包” are
    // food/stationery products, not bags. Keep explicit carrier terms and
    // the catalog's known pencil/lunch pouch names instead.
    match: /帆布|手提袋|单肩包|腰包|背包|笔袋|餐包|毛毡包|杜邦纸包|收纳包|零钱包|托特包|购物袋|canvas\s*bag|bag|pouch/i,
    profile: {
      key: 'bag',
      recommendedSize: '350×300×100mm',
      prompt: '完整可使用的布包/手提袋/帆布包；展示真实布面、裁片、缝线、包边、提手、开口和容量，参考元素以大面积印花、刺绣或织唛落在包面，不能只生成一张平面图案稿。',
      negative: 'flat poster, floating artwork, missing handles, impossible seamless structure, hard statue, tiny motif only',
    },
  },
  {
    match: /项链|颈链|手镯|手链|耳钉|耳坠|吊坠|首饰|jewelry|necklace|bracelet|earring/i,
    profile: {
      key: 'jewelry',
      prompt: '完整可佩戴的首饰成品；展示真实金属/宝石/连接件、合理厚度、圆角和佩戴结构（链条、耳针或扣件），参考元素转为主要吊坠/纹样，不是孤立平面 logo 或海报。',
      negative: 'flat poster, missing clasp or chain, oversized sculpture, tiny isolated motif, unrelated object',
    },
  },
  {
    match: /food|食品|巧克力|糖果|曲奇|饼干|月饼|糕点|甜品|冰淇淋|茶叶|咖啡/i,
    profile: {
      key: 'food',
      prompt: '真实可食用的文创食品成品；使用食品级原料和可食用印花、压纹、糖霜或巧克力装饰，呈现可食用的形状、厚度、边缘与合理食品包装，不是金属/塑料摆件或单独平面标签。',
      negative: 'metal ornament, plastic statue, badge, keychain, jewelry, inedible decoration, flat label sheet, tiny motif only',
    },
  },
]

const CATEGORY_PRODUCT_FORM_PROFILES: Record<string, ProductFormProfile> = {
  stationery: {
    key: 'paper_stationery',
    recommendedSize: 'A5（148×210mm）',
    prompt: '完整可使用的纸品/文具成品；明确纸张或板材厚度、裁切边、折叠/装订/夹持等功能结构，参考元素适合印刷、烫金、压凹凸或覆膜，不能只生成一张海报稿。',
    negative: 'phone screenshot, flat poster only, missing paper edges, impossible 3D structure, tiny isolated motif',
  },
  toy: {
    key: 'collectible_toy',
    recommendedSize: '高约130mm',
    prompt: '完整立体可量产的潮玩/玩具/手办；明确头身比例、稳定底部、分件、连接位和涂装表面，参考主体成为玩具轮廓与主要装饰，不是平面图案或随机物体。',
    negative: 'flat illustration, flat poster, missing body or base, melted geometry, tiny isolated motif only',
  },
  tableware: {
    key: 'tableware',
    recommendedSize: '直径约80mm，高约95mm',
    prompt: '完整可使用的餐饮器物；明确开口、容积、底部稳定性、合理壁厚和食品接触面，参考元素落在釉面、印花或浮雕区域，展示真实器物而不是雕塑。',
    negative: 'flat poster, missing opening, unstable base, abstract sculpture, tiny isolated motif only',
  },
  apparel: {
    key: 'apparel',
    recommendedSize: '按常用成人尺码，图案安全边距清晰',
    prompt: '完整可穿戴的服饰/配件；展示真实布料、裁片、缝线、领口/袖口/扣件或佩戴结构，参考元素以印花、刺绣、织唛或提花落在服饰表面，不能只生成平面图案稿。',
    negative: 'flat poster, floating garment graphic, missing garment structure, hard statue, tiny isolated motif only',
  },
  daily: {
    key: 'daily_use',
    prompt: '完整可日常使用的生活用品；明确容器/握持/开合/支撑等功能结构、真实材质和可生产厚度，参考元素作为产品表面主视觉或结构细节，而不是孤立小图案。',
    negative: 'flat poster, abstract pattern only, missing functional structure, random object, tiny isolated motif only',
  },
  craft: {
    key: 'craft_object',
    recommendedSize: '高约150mm，底部稳定',
    prompt: '完整可陈列、可打样的工艺收藏品；明确主体轮廓、底座/支撑、材质工艺、厚度和安全边缘，参考元素要成为器物的主要造型或表面工艺，不是海报。',
    negative: 'flat poster, floating parts, unstable base, impossible thin details, tiny isolated motif only',
  },
  precious: {
    key: 'precious_collectible',
    recommendedSize: '直径约40mm、厚约3mm',
    prompt: '完整可生产的贵金属纪念收藏品；明确金属厚度、边缘、浮雕/压印和稳定轮廓，参考主体成为正面主要图案，展示真实金属成品而不是平面海报。',
    negative: 'flat poster, paper card, soft plush, missing metal edge, tiny isolated motif only',
  },
}

function productFormContext(product: ProductOption | null) {
  if (!product) return ''
  // Match the physical carrier from stable catalog identity fields. Free-form
  // descriptions often contain words such as “包装” or “袋”, which can make a
  // food product look like a bag if they are used as the primary classifier.
  // Use stable identity fields only.  Display category labels are localized
  // UI text (for example “食品饮品” or “饰品挂件”) and must never drive
  // physical-form matching.
  return `${product.key} ${product.name} ${product.categoryKey} ${product.materials.map(item => item.name).join(' ')}`
}

function matchedProductFormProfile(product: ProductOption | null) {
  const context = productFormContext(product)
  return PRODUCT_FORM_PROFILES.find(item => item.match.test(context))?.profile
}

function productFormProfile(product: ProductOption | null): ProductFormProfile {
  const matched = matchedProductFormProfile(product)
  if (matched) return matched
  if (product?.categoryKey && CATEGORY_PRODUCT_FORM_PROFILES[product.categoryKey]) return CATEGORY_PRODUCT_FORM_PROFILES[product.categoryKey]
  return DEFAULT_PRODUCT_FORM_PROFILE
}

function productAdaptationInstruction(profile: ProductFormProfile) {
  const instructions: Record<string, string> = {
    bottle: '【参考图使用方式】把主体转为瓶身大面积主视觉/环绕瓶标，不保留原雕像、原照片或原场景。',
    mug: '【参考图使用方式】把主体转为杯身环绕印花、釉上彩或浮雕，优先呈现完整杯体结构。',
    bag: '【参考图使用方式】把主体转为包面大面积印花、刺绣或织唛，优先呈现完整包体和提手。',
    apparel: '【参考图使用方式】把主体转为服饰大面积印花、刺绣或提花，优先呈现完整可穿戴结构。',
    jewelry: '【参考图使用方式】把主体重构为可佩戴的吊坠/纹样和连接结构，不输出原始雕像或照片。',
    plush: '【参考图使用方式】保留主体可辨识轮廓并重构为柔软填充体、裁片和缝线，不输出硬质雕塑。',
    plush_keychain: '【参考图使用方式】保留主体可辨识轮廓并重构为带挂环的柔软填充挂件，不输出平面插画。',
    collectible_toy: '【参考图使用方式】保留主体可辨识轮廓并重构为有分件、底座和涂装的立体玩具。',
    keychain: '【参考图使用方式】保留主体轮廓并重构为有厚度、挂孔和连接环的钥匙扣成品。',
    postcard: '【参考图使用方式】把主体重新编排到完整卡纸正面，保留卡片边缘和纸张厚度，不输出原手机画面。',
    bookmark: '【参考图使用方式】把主体沿完整书签正面重新编排，保持书签细长轮廓和真实厚度。',
    paper_stationery: '【参考图使用方式】把主体编排到完整纸品/文具表面，保留裁切边、厚度和装订/夹持结构。',
    badge: '【参考图使用方式】把主体转为金属徽章正面浅浮雕/珐琅图案，保留完整金属边缘和背部固定结构。',
    magnet: '【参考图使用方式】把主体转为冰箱贴正面图形或浅浮雕，保留完整厚度、圆角和磁吸背面。',
    precious_collectible: '【参考图使用方式】把主体转为贵金属成品正面浮雕/压印，保留金属边缘和稳定轮廓。',
    food: '【参考图使用方式】把主体转为真实可食用的形状、印花、压纹或装饰，不输出金属/塑料摆件。',
    tableware: '【参考图使用方式】把主体转为器物表面的印花、釉彩或浮雕，同时呈现完整可用结构。',
    daily_use: '【参考图使用方式】把主体转为生活用品表面主视觉或结构细节，同时呈现完整功能结构。',
    craft_object: '【参考图使用方式】保留主体文化识别点并重构为有支撑、厚度和安全边缘的工艺品。',
    general: '【参考图使用方式】保留主体文化识别点并适配目标产品的轮廓、材质和功能结构。',
  }
  return instructions[profile.key] || instructions.general
}

function productFormConstraint(product: ProductOption | null = selectedProduct.value) {
  const profile = productFormProfile(product)
  const productName = product?.name || '文创产品'
  const selectedMaterial = material.value || '适合该产品的制造材质'
  const selectedSize = productSize.value || profile.recommendedSize || '按产品实际规格'
  const referenceSource = isReferenceImageMode()
    ? '上传参考图中的主体和核心视觉元素'
    : '用户提供的灵感和核心视觉元素'
  return [
    PRODUCT_ROLE_PROMPT,
    `【任务】将${referenceSource}完全重构为一件真实的「${productName}」成品，用于电商主图展示；不是对原图做轻微滤镜或简单贴图。`,
    '【强制规则】',
    `1. ${PRODUCT_PRESENTATION_GUARD}`,
    `2. ${productAdaptationInstruction(profile)}`,
    `3. 【目标产品形态】${profile.prompt}`,
    `4. ${PRODUCT_FRAME_GUARD}`,
    `5. 【制造参数】材质为「${selectedMaterial}」；成品规格为「${selectedSize}」。规格用于约束实体比例和结构，不是图片分辨率。`,
    '6. 【原图处理】删除手机、播放器、状态栏、截图边框、天空、山、云和原始场景；保留主体识别特征、文化元素和主要配色。',
    '7. 【禁止输出】原图不变、手机截图、海报、平面标签稿、孤立小图案、黄褐滤镜、窄长手机构图或无关物体。',
    PRODUCT_SELF_CHECK,
  ].join('\n')
}

function productFormNegative(product: ProductOption | null = selectedProduct.value) {
  return productFormProfile(product).negative
}

const modeOptions = [
  { key: 'template' as Mode, mark: '例', title: '没有灵感（看看示例）', desc: '浏览示例并了解创作方式' },
  { key: 'text' as Mode, mark: '字', title: '已有灵感（文字）', desc: '把你的想法、故事或需求告诉我' },
  { key: 'image' as Mode, mark: '图', title: '已有灵感（图片）', desc: '上传草图、照片或有权使用的参考图' },
]
const productOptions = ref<ProductOption[]>([])
const productKeyword = ref('')
const productCategory = ref('')
const catalogLoading = ref(false)

const phase = ref<Phase>('mode')
const mode = ref<Mode | ''>('')
const selectedProduct = ref<ProductOption | null>(null)
const material = ref('')
const materialChoice = ref<'recommend' | string>('recommend')
const productSize = ref('')
const productSizeRecommended = ref(false)
const inspirationText = ref('')
const referencePath = ref('')
const referenceAssetId = ref<number | null>(null)
const sessionId = ref<number | null>(null)
const generatedAssetId = ref<number | null>(null)
const pendingImageJobId = ref<number | null>(null)
const pendingGenerationPrompt = ref('')
const pendingMultiViewJobId = ref<number | null>(null)
const pendingMultiViewInputAssetId = ref<number | null>(null)
const pendingMultiViewPrompt = ref('')
const previewUrl = ref('')
const referenceAnalysis = ref('')
const multiviewImages = ref<SeedreamMultiViewImage[]>([])
const multiviewBundleId = ref<number | null>(null)
const multiviewBundleNo = ref('')
const multiviewBundleStatus = ref('')
const multiviewBundleComment = ref('')
const multiviewBundleSubmitting = ref(false)
const modelInputMode = ref<'single' | 'multiview'>('single')
const refiningImage = ref(false)
const refinementNote = ref('')
const modelTask = ref<ModelTask | null>(null)
const messages = ref<Message[]>([])
const busy = ref(false)
const busyMessage = ref('正在保存创作过程并调用 AI，请稍候…')
const saving = ref(false)
const sessionReady = ref(false)
const scrollIntoView = ref('bottom-anchor')
let messageId = 0
let sessionPromise: Promise<boolean> | null = null
const forceNewSession = ref(false)
const chatExperience = true
const chatInput = ref('')
const chatQuickReplies = ref<ConversationQuickReply[]>([])
const chatSending = ref(false)
const quickReplySubmitting = ref(false)
const chatThinking = ref(false)
const imageGenerationStage = ref<'adapting_product' | ''>('')
const thinkingLabel = ref('正在理解你的想法')
const awaitingGenerationConfirmation = ref(false)
const chatStage = ref('need_product')
const autoGenerationInFlight = ref(false)
const modelRefreshing = ref(false)
const referencePolicyConfirmed = ref(false)
const aiPolicyConfirmed = ref(false)
const threeDimensionalPolicyConfirmed = ref(false)
const policyDialog = ref<{ key: CreativePolicyKey; resolve: (confirmed: boolean) => void } | null>(null)
const campaignContext = ref<CampaignContext | null>(null)
const campaignAttached = ref(false)
let modelPollTimer: ReturnType<typeof setTimeout> | null = null
let modelPollVersion = 0
let draftSaveTimer: ReturnType<typeof setTimeout> | null = null

const previousEditTarget = computed<EditableBriefField | null>(() => {
  if (phase.value === 'result') return selectedProduct.value ? 'size' : null
  if (phase.value === 'multiview' || phase.value === 'model') return null
  if (!selectedProduct.value || chatStage.value === 'need_product') return null
  if (chatStage.value === 'need_inspiration') return 'product'
  if (chatStage.value === 'need_material') return 'inspiration'
  if (chatStage.value === 'need_size') return 'material'
  if (['confirm_before_image', 'need_additional_detail', 'ready_for_image', 'image_ready'].includes(chatStage.value)) return 'size'
  if (productSize.value) return 'size'
  if (material.value) return 'material'
  if (inspirationText.value || referenceAssetId.value) return 'inspiration'
  return 'product'
})
const previousActionLabel = computed(() => {
  if (phase.value === 'model') return multiviewImages.value.length >= 3 ? '返回三视图' : '返回产品图'
  if (phase.value === 'multiview') return '返回产品图'
  return ({ product: '修改产品', inspiration: '修改灵感', material: '修改材质', size: '修改尺寸' } as Record<EditableBriefField, string>)[previousEditTarget.value || 'product']
})
const canGoPrevious = computed(() => !busy.value && !saving.value && !chatSending.value
  && (phase.value === 'multiview' || phase.value === 'model' || Boolean(previousEditTarget.value)))

const currentMaterials = computed(() => selectedProduct.value?.materials || [])
const categoryLabels: Record<string, string> = { food: '食品饮品', stationery: '文具纸品', souvenir: '景区文创', accessory: '饰品挂件', craft: '工艺收藏', daily: '日用生活', tableware: '餐饮器物', toy: '潮玩玩具', apparel: '服饰配件', precious: '贵金属' }
const categoryOrder = ['food', 'stationery', 'souvenir', 'accessory', 'craft', 'daily', 'tableware', 'toy', 'apparel', 'precious']
const productCatalogCategories = computed(() => {
  const names = new Map<string, string>()
  productOptions.value.forEach(item => names.set(item.categoryKey, categoryLabels[item.categoryKey] || item.categoryName || '其他'))
  return Array.from(names.entries())
    .map(([key, name]) => ({ key, name }))
    .sort((left, right) => {
      const leftIndex = categoryOrder.indexOf(left.key)
      const rightIndex = categoryOrder.indexOf(right.key)
      return (leftIndex < 0 ? 999 : leftIndex) - (rightIndex < 0 ? 999 : rightIndex) || left.name.localeCompare(right.name, 'zh-CN')
    })
})
const filteredProductOptions = computed(() => {
  const keyword = productKeyword.value.trim().toLowerCase()
  return productOptions.value.filter(item => {
    if (productCategory.value && item.categoryKey !== productCategory.value) return false
    if (!keyword) return true
    return `${item.name} ${item.desc} ${item.process} ${item.materials.map(material => material.name).join(' ')}`.toLowerCase().includes(keyword)
  })
})
const productCategoryName = computed(() => productCatalogCategories.value.find(item => item.key === productCategory.value)?.name || '')
const isFoodProduct = computed(() => selectedProduct.value?.categoryKey === 'food'
  || /食品|食用|曲奇|饼干|糕点|月饼|咖啡|饮品|茶|巧克力|糖果/.test(`${selectedProduct.value?.name || ''} ${material.value}`))
const prompt = computed(() => {
  const product = selectedProduct.value?.name || '文创产品'
  const source = inspirationText.value.trim() || `为${product}设计一套具有文化辨识度、适合量产打样的产品视觉`
  const campaignDirection = campaignContext.value
    ? `本作品参加平台优先征集「${campaignContext.value.title}」，面向${campaignContext.value.targetName}候选渠道；请重点遵循：${campaignContext.value.promptHint}。`
    : ''
  const size = productSize.value || '待确认'
  const sizeSource = productSizeRecommended.value ? '（已按推荐规格确定）' : ''
  const catalogHint = productSizeRecommended.value
    ? catalogSpecificationHint(selectedProduct.value, productSize.value)
    : ''
  const catalogSpecification = catalogHint
    ? `目录推荐规格参考（仅保留容量/件数信息，不覆盖已选成品规格）：${catalogHint}。`
    : ''
  // Image mode receives the structured role/task/rules block from
  // productTransformationGuard. Avoid sending the same long block twice.
  const formConstraint = isReferenceImageMode() ? '' : productFormConstraint(selectedProduct.value)
  return `${source}。产品：${product}；材质：${material.value}；成品尺寸：${size}${sizeSource}。${catalogSpecification}视觉气质与配色只依据用户灵感和产品形态协调，不强行套用固定风格或用途。${formConstraint}${campaignDirection}`
})
const normalizedModelProgress = computed(() => Math.max(0, Math.min(100, Number(modelTask.value?.progress) || 0)))
const isModelTaskSucceeded = computed(() => modelTask.value?.status === 'succeeded')
const isModelTaskFailed = computed(() => modelTask.value?.status === 'failed')
const isModelTaskTerminal = computed(() => isModelTaskSucceeded.value || isModelTaskFailed.value)
const modelTaskTitle = computed(() => isModelTaskSucceeded.value ? '3D 模型已经生成' : isModelTaskFailed.value ? '3D 建模未完成' : '3D 建模正在生成')
const hasCompleteThreeViews = computed(() => {
  const available = new Set(multiviewImages.value.map(item => String(item?.view || '').toLowerCase()))
  return ['front', 'left', 'back'].every(view => available.has(view))
})
const multiviewBundleStatusText = computed(() => ({
  draft: '待提交审核',
  review: '三视图审核中',
  approved: '审核已通过',
  rejected: '审核未通过',
}[multiviewBundleStatus.value] || '待创建审核包'))
const canSubmitMultiViewReview = computed(() => ['draft', 'rejected'].includes(multiviewBundleStatus.value))
const modelInputLabel = computed(() => modelInputMode.value === 'multiview' ? '三视图建模' : '单图建模')
const modelTaskDescription = computed(() => isModelTaskSucceeded.value ? `${modelInputLabel.value}的 3D 原型已保存到作品库` : isModelTaskFailed.value ? `本次${modelInputLabel.value}失败，可回到产品图重新提交` : `正在进行${modelInputLabel.value}`)
const modelTaskDetail = computed(() => isModelTaskSucceeded.value ? '可以在作品库查看模型、评审并申请打样。' : isModelTaskFailed.value ? '失败原因已保留。检查产品图或三视图后可以再次发起建模。' : '本页面会自动刷新进度，离开后也会继续在作品库保存。')
const activePolicy = computed(() => getCreativePolicy(policyDialog.value?.key || 'ai-output'))
const chatStageLabel = computed(() => ({
  need_product: '先告诉我想做什么产品',
  need_inspiration: '再说说你的灵感，或上传参考图',
  need_material: '最后确认材质，不确定可以让我推荐',
  need_size: '再确认成品尺寸，不确定可以按推荐规格',
  understanding: '我正在整理你的创作方向',
  confirm_before_image: '生成前确认一下，还有需要补充的吗？',
  need_additional_detail: '请补充你想保留、加强或避免的内容',
  ready_for_image: '信息已足够，准备生成产品图',
  template_unavailable: '没有灵感示例功能正在开发中',
  image_ready: '产品图已完成，可以继续落地',
  multiview_ready: '三视图已完成，请先整包提交审核',
  multiview_review: '三视图正在人工审核',
  multiview_approved: '三视图审核已通过，可以申请打样',
  multiview_rejected: '三视图未通过，可根据原因修改后重提',
  model_running: '3D 原型正在生成',
  model_ready: '3D 原型已完成，可以申请打样',
}[chatStage.value] || '告诉我你的创作想法'))
function quickReplyMark(type: string) {
  return ({
    category: '类',
    product: '选',
    material: '材',
    size: '尺',
    upload: '图',
    text: '写',
    template: '例',
    confirm_generate: '出',
    add_detail: '改',
    multiview: '观',
    bundle_review: '审',
    bundle_production: '样',
    model: '3D',
    refine: '改',
    commercial: '样',
    works: '作',
  } as Record<string, string>)[type] || '→'
}

function productTransformationGuard() {
  const product = selectedProduct.value?.name || '用户选择的文创产品'
  const selectedMaterial = material.value || '适合该产品的制造材质'
  const size = productSize.value || '已确认的成品规格'
  const profile = productFormProfile(selectedProduct.value)
  return [
    PRODUCT_ROLE_PROMPT,
    `【任务】将上传参考图中的非UI主体和核心视觉元素重构为真实、可量产的「${product}」电商主图；禁止原图复刻或只做滤镜。`,
    '【强制规则】',
    '1. 参考图只提供主体身份、关键纹样、文化识别点和主要配色；目标产品载体优先。',
    `2. ${productAdaptationInstruction(profile)}`,
    `3. 【产品形态】${profile.prompt}`,
    `4. 【材质/规格】材质「${selectedMaterial}」；成品规格「${size}」，这是实体规格，不是图片分辨率。`,
    `5. ${PRODUCT_FRAME_GUARD}`,
    '6. 删除原始载体、原始场景、天空山云、手机/UI/播放器和截图边框；不得输出原图、海报、标签稿或孤立小图案。',
    PRODUCT_SELF_CHECK,
  ].join('\n')
}

function addMessage(role: Message['role'], text: string) {
  const id = ++messageId
  messages.value.push({ id, role, text })
  void nextTick(() => { scrollIntoView.value = `message-${id}` })
  return id
}
function addImageMessage(imageUrl: string, text = '已上传灵感图片', imageState: Message['imageState'] = 'ready', imageAssetId?: number) {
  const id = ++messageId
  messages.value.push({ id, role: 'user', text, imageUrl, imageAssetId, imageState })
  void nextTick(() => { scrollIntoView.value = `message-${id}` })
  return id
}
function updateImageMessage(id: number, values: Partial<Pick<Message, 'text' | 'imageUrl' | 'imageAssetId' | 'imageState'>>) {
  const message = messages.value.find(item => item.id === id)
  if (message) Object.assign(message, values)
}
function addAssistantMessage(text: string) {
  const value = text.trim()
  if (!value) return null
  const recentAssistant = [...messages.value].reverse().find(item => item.role === 'assistant')
  // Keep a broken planner from filling the transcript with the same template
  // while the persisted brief still advances locally.
  if (recentAssistant?.text === value) return null
  return addMessage('assistant', value)
}
function addRestoredMessage(role: Message['role'], text: string) {
  const value = text.trim()
  if (!value) return null
  const isSizeQuestion = role === 'assistant' && /这件产品想做多大[？?]/.test(value)
  const isRecommendedSizeReply = role === 'user' && value === '按推荐规格'
  // Old sessions can contain several copies of the same size turn from the
  // previous client. Once a size is already in the brief, those questions are
  // stale and should not be shown again when the transcript is restored.
  if (isSizeQuestion && productSize.value) return null
  if (isRecommendedSizeReply && messages.value.some(item => item.role === 'user' && item.text === value)) return null
  if (messages.value.some(item => item.role === role && item.text === value)) return null
  return addMessage(role, value)
}
async function scrollToSection(id: 'result-output' | 'multiview-output' | 'model-output' | 'bottom-anchor') {
  scrollIntoView.value = ''
  await nextTick()
  scrollIntoView.value = id
}
function setChatThinking(active: boolean, label = '正在理解你的想法') {
  chatThinking.value = active
  if (active) {
    thinkingLabel.value = label
    void nextTick(() => { if (chatThinking.value) scrollIntoView.value = 'chat-thinking' })
  }
}
function thinkingLabelFor(action?: { type: string; value?: string; label?: string }, message = '') {
  const type = String(action?.type || '')
  if (type === 'category' || type === 'product') return '正在整理产品方向'
  if (type === 'material' || type === 'recommend_material') return '正在匹配材质与生产工艺'
  if (type === 'size') return '正在核对成品尺寸与比例'
  if (type === 'upload' || /图片|照片|草图|参考图/.test(message)) return '正在读取参考图片和主体特征'
  return '正在理解你的想法'
}
function setGenerationConfirmationReplies() {
  chatQuickReplies.value = [
    { label: '没有补充，开始生成', type: 'confirm_generate', value: 'confirm' },
    { label: '我还要补充', type: 'add_detail', value: '' },
  ]
}
function isGenerationConfirmationText(message: string) {
  const value = message.trim()
  return Boolean(value) && (/.*(没有|无|不需要|不用).*(补充|修改|添加|意见).*/.test(value)
    || /.*(直接|开始|确认).*(生成|出图).*/.test(value)
    || /^(没有|没有了|无|无了|就这样|不用补充)$/.test(value))
}
function isRecommendedSizeTurn(stageBeforeRequest: string, message: string, action?: { type: string; value?: string }) {
  if (action?.type === 'size' && String(action.value || '').toLowerCase() === 'recommend') return true
  const value = message.trim()
  return stageBeforeRequest === 'need_size'
    && value.length <= 32
    && /(推荐|帮我选|你来选|按推荐规格)/.test(value)
}
function shouldPreserveRecommendedSizeAfterChat(action: { type: string; value?: string } | undefined, message: string) {
  if (!productSizeRecommended.value || !productSize.value) return false
  const type = String(action?.type || '')
  const value = String(action?.value || '').toLowerCase()
  // A new product or an explicit size edit invalidates the previous
  // recommendation. Other turns (especially “我还要补充”) should retain it
  // even if an older backend response omits productSize from its brief.
  if (type === 'product' || type === 'category' || type === 'size' || (type === 'edit' && value === 'size')) return false
  if (/(尺寸|规格|大小|做多大|改成|换成).*(?:\d|a[3-6]|推荐)/i.test(message)) return false
  return true
}
function normalizeRecommendedSpecification(value: unknown) {
  const normalized = String(value || '').trim().replace(/\s+/g, ' ')
  if (!normalized || /^(随型|定制|按规格|参考产品册|短袖常规尺码)$/i.test(normalized)) return ''
  const hasStandardPaperSize = /\bA[3-6]\b/i.test(normalized)
  const hasNumericSpec = /\d(?:\.\d+)?/.test(normalized)
  const hasPhysicalOrCapacityUnit = /(?:mm|毫米|cm|厘米|mL|毫升|ml|g|克|kg|公斤|个|支|块|袋|盒|套|片|粒|根|英寸|in)/i.test(normalized)
  // Selection specifications are often ranges or capacities (300-500ml,
  // 4-8cm/个, 50g*8块). Keep them as recommendation data instead of silently
  // falling back to a flat category size. The product-form profile below adds
  // the missing geometry when the catalog only provides capacity/weight.
  if (!hasStandardPaperSize && !(hasNumericSpec && hasPhysicalOrCapacityUnit)) return ''
  return normalized.length > 120 ? normalized.slice(0, 120) : normalized
}
function defaultLocalSizeForProduct(product: ProductOption | null, includeCategoryDefault = true) {
  const name = product?.name || ''
  if (name.includes('冰箱贴')) return '60×60×4mm'
  if (name.includes('钥匙扣')) return '50×50×4mm'
  if (name.includes('徽章') || name.includes('胸针') || name.includes('纪念章') || name.endsWith('币')) return '58×58×3mm'
  if (name.includes('书签')) return '40×120×1.2mm'
  if (name.includes('明信片')) return 'A6（105×148mm）'
  if (name.includes('贴纸')) return '50×50mm'
  if (name.includes('本册') || name.includes('笔记本') || name.includes('打卡本')) return 'A5（148×210mm）'
  if (name.includes('抱枕')) return '400×400×120mm'
  if (name.includes('毛巾')) return '200×700mm'
  if (name.includes('公仔') || name.includes('潮玩') || /毛绒玩具|毛绒公仔|毛绒娃娃|毛绒玩偶|布偶/.test(name)) return '高 130mm'
  if (name.includes('杯垫')) return '100×100×5mm'
  if (name.includes('马克杯')) return '直径 80mm、高 95mm'
  if (name.includes('保温杯') || name.includes('随行杯')) return '直径 70mm、高 200mm'
  if (name.includes('帆布') && name.includes('包') || name.includes('手提袋')) return '350×300×100mm'
  if (name.includes('吊坠')) return '30×30×3mm'
  if (name.includes('耳钉')) return '12×12×3mm'
  if (name.includes('耳坠')) return '15×30×3mm'
  if (name.includes('项链') || name.includes('颈链')) return '链长 450mm'
  if (name.includes('手镯') || name.includes('手链')) return '周长 170mm'
  if (name.includes('摆件') || name.includes('工艺品')) return '150×150×200mm'
  if (!includeCategoryDefault) return ''
  const categoryDefaults: Record<string, string> = {
    food: '500g级食品包装或食品本体（按实际包装/模具定制）',
    stationery: 'A5（148×210mm）',
    daily: '300×300×80mm',
    toy: '高 130mm',
    tableware: '100×100×100mm',
    souvenir: '60×60×4mm',
    accessory: '35×35×3mm',
    apparel: '350×300×100mm',
    craft: '150×150×200mm',
    precious: '40×40×3mm',
  }
  return categoryDefaults[product?.categoryKey || ''] || '80×80×8mm'
}
function localRecommendedProductSize(product: ProductOption | null) {
  // A carrier-specific profile is authoritative (for example a 500mL bottle
  // or A6 postcard). Category defaults are deliberately applied later so a
  // catalog specification such as 15-20cm ruler or 3g*10 tea bags is not
  // overwritten by the broad stationery/tableware fallback.
  const formRecommendation = matchedProductFormProfile(product)?.recommendedSize
  if (formRecommendation) return formRecommendation
  const catalogSpecification = normalizeRecommendedSpecification(product?.recommendedSize || product?.specification)
  // Keep catalog capacities, pack counts and ranges as explicit recommendation
  // data. Product-form constraints supply the geometry where needed.
  if (catalogSpecification) return catalogSpecification
  const categoryRecommendation = product?.categoryKey
    ? CATEGORY_PRODUCT_FORM_PROFILES[product.categoryKey]?.recommendedSize
    : ''
  // Prefer a product-name geometry (for example a tumbler) over a broad
  // category recommendation (tableware's mug-sized fallback).
  return defaultLocalSizeForProduct(product, false) || categoryRecommendation || defaultLocalSizeForProduct(product)
}
function catalogSpecificationHint(product: ProductOption | null, selectedSize: string) {
  const catalog = normalizeRecommendedSpecification(product?.specification || product?.recommendedSize)
  if (!catalog || catalog === selectedSize) return ''
  // A physical-form profile is authoritative for geometry. Keep capacity,
  // weight and pack-count data, but hide conflicting legacy dimensions.
  if (productFormProfile(product).recommendedSize && /(?:mm|毫米|cm|厘米|英寸|in|×|x|直径|高度|宽|高|厚)/i.test(catalog)) return ''
  return catalog
}
function hasCompleteLocalGenerationBrief() {
  if (!selectedProduct.value || !material.value || !productSize.value) return false
  if (isReferenceImageMode()) return Boolean(referenceAssetId.value)
  if (!mode.value) return false
  return mode.value === 'text' && Boolean(inspirationText.value.trim())
}

function hasReferenceImage() {
  const assetId = Number(referenceAssetId.value)
  return Number.isFinite(assetId) && assetId > 0
}

/**
 * A saved reference image is the authoritative source for image generation.
 * Chat planning can return a stale text mode, so every generation decision
 * uses this derived state instead of trusting the planner's mode field alone.
 */
function isReferenceImageMode() {
  return mode.value === 'image' || hasReferenceImage()
}

function activateReferenceImageMode() {
  mode.value = 'image'
  // Text entered before choosing an image is stale for the image-only path.
  // Users can still add deliberate details after the upload completes.
  inspirationText.value = ''
}

function preserveReferenceImageMode() {
  if (!hasReferenceImage()) return false
  mode.value = 'image'
  return true
}
function setInitialChatReplies() {
  if (productOptions.value.length) {
    const seen = new Set<string>()
    const categories = productOptions.value
      .filter(item => seen.has(item.categoryKey) ? false : (seen.add(item.categoryKey), true))
      .slice(0, 7)
      .map(item => ({ label: item.categoryName, type: 'category', value: item.categoryKey }))
    chatQuickReplies.value = [
      ...categories,
      { label: '没有灵感（看看示例）', type: 'template', value: '' },
    ]
  } else {
    chatQuickReplies.value = [
      { label: '我有一个想法', type: 'text', value: '' },
      { label: '上传灵感图片', type: 'upload', value: '' },
      { label: '没有灵感（看看示例）', type: 'template', value: '' },
    ]
  }
}

function applyChatBrief(brief: Record<string, any> | undefined, preserveExisting = false, preserveRecommendedSize = false) {
  if (!brief) return
  const previousSize = productSize.value
  const previousSizeWasRecommended = productSizeRecommended.value
  const localReferenceAssetId = hasReferenceImage() ? Number(referenceAssetId.value) : null
  const product = productByValue(brief.productName, brief.productKey)
  if (product || !preserveExisting) selectedProduct.value = product
  const resolvedMode = ['template', 'text', 'image'].includes(String(brief.mode || '')) ? String(brief.mode) as Mode : ''
  if (!localReferenceAssetId && (resolvedMode || !preserveExisting)) mode.value = resolvedMode
  if (brief.inspiration && brief.inspirationSource !== 'image') inspirationText.value = String(brief.inspiration)
  else if (!preserveExisting) inspirationText.value = ''
  const resolvedReferenceAssetId = Number(brief.referenceAssetId) > 0 ? Number(brief.referenceAssetId) : null
  if (localReferenceAssetId) referenceAssetId.value = localReferenceAssetId
  else if (resolvedReferenceAssetId || !preserveExisting) referenceAssetId.value = resolvedReferenceAssetId
  // Never let a planner response downgrade an uploaded reference into text
  // generation or discard the asset ID that was just uploaded locally.
  preserveReferenceImageMode()
  if (!referenceAssetId.value) referencePath.value = ''
  if (brief.material) {
    material.value = String(brief.material)
    materialChoice.value = brief.materialRecommended ? 'recommend' : material.value
  } else if (!preserveExisting) {
    material.value = ''
    materialChoice.value = 'recommend'
  }
  if (brief.productSize) {
    const resolvedSize = String(brief.productSize)
    productSize.value = resolvedSize
    productSizeRecommended.value = Boolean(brief.sizeRecommended)
      || (preserveRecommendedSize && previousSizeWasRecommended && resolvedSize === previousSize)
  } else if (!preserveExisting && !preserveRecommendedSize) {
    productSize.value = ''
    productSizeRecommended.value = false
  }
}

async function handleQuickReply(item: ConversationQuickReply) {
  if (busy.value || chatSending.value || quickReplySubmitting.value) return
  quickReplySubmitting.value = true
  const type = String(item.type || '')
  try {
    if (type === 'upload') {
      await pickInspirationImage()
      return
    }
    if (type === 'multiview') {
      await generateMultiView()
      return
    }
    if (type === 'bundle_review') {
      await submitMultiViewReview()
      return
    }
    if (type === 'bundle_production') {
      applyMultiViewProduction()
      return
    }
    if (type === 'model') {
      await generateModel()
      return
    }
    if (type === 'commercial') {
      openCommercial()
      return
    }
    if (type === 'works') {
      goWorks()
      return
    }
    if (type === 'refine') {
      startRefinement()
      return
    }
    if (type === 'size' && String(item.value || '').toLowerCase() === 'recommend') {
      await chooseRecommendedSizeLocally(String(item.label || '按推荐规格'))
      return
    }
    if (type === 'template') {
      showTemplateDeveloping()
      return
    }
    if (type === 'confirm_generate' && productSizeRecommended.value && hasCompleteLocalGenerationBrief() && !generatedAssetId.value) {
      // A locally resolved recommendation already contains the complete brief;
      // start generation directly instead of asking the planner to resolve the
      // same size a second time.
      addMessage('user', String(item.label || '没有补充，开始生成'))
      awaitingGenerationConfirmation.value = false
      chatStage.value = 'ready_for_image'
      chatQuickReplies.value = []
      addAssistantMessage('好的，我按当前推荐规格开始生成产品图。')
      await saveCreativeEventBestEffort('chat', 'chat_user_message', {
        message: '',
        action: { type, value: String(item.value || ''), label: String(item.label || '') },
        localGeneration: true,
      })
      await generateProductImage()
      return
    }
    if (type === 'confirm_generate' || type === 'add_detail') {
      await sendChatTurn('', { type, value: String(item.value || ''), label: String(item.label || '') })
      return
    }
    if (type === 'text' && !String(item.value || '').trim()) {
      uni.showToast({ title: '请在下方输入框告诉我你的想法', icon: 'none' })
      return
    }
    const label = String(item.label || item.value || '').trim()
    // Keep structured selections out of the free-text slot. Otherwise a
    // product/material button can be misread as the user's inspiration.
    const message = type === 'text' ? label : ''
    await sendChatTurn(message, { type, value: String(item.value || ''), label })
  } finally {
    quickReplySubmitting.value = false
  }
}

async function submitChatInput() {
  const value = chatInput.value.trim()
  if (!value || busy.value || chatSending.value) return
  chatInput.value = ''
  const sent = await sendChatTurn(value)
  if (sent && !chatInput.value.trim()) clearChatDraft()
  else if (!chatInput.value.trim()) chatInput.value = value
}

async function chooseRecommendedSizeLocally(label = '按推荐规格') {
  if (!selectedProduct.value) {
    uni.showToast({ title: '请先选择产品，再推荐成品规格', icon: 'none' })
    return
  }
  const recommended = localRecommendedProductSize(selectedProduct.value)
  productSize.value = recommended
  productSizeRecommended.value = true
  addMessage('user', label)
  await saveCreativeEventBestEffort('size', 'size_selected', {
    productKey: selectedProduct.value.key,
    productType: selectedProduct.value.name,
    productSize: recommended,
    recommended: true,
    source: 'miniapp_catalog',
  })
  chatStage.value = 'confirm_before_image'
  awaitingGenerationConfirmation.value = true
  setGenerationConfirmationReplies()
  addAssistantMessage(`根据${selectedProduct.value.name}的常用打样规格，我推荐 ${recommended}，已为你设置并写入生成提示词。生成前还有需要补充的吗？`)
  phase.value = 'size'
}

async function sendChatTurn(message: string, action?: { type: string; value?: string; label?: string }, options: { skipUserMessage?: boolean } = {}) {
  if (!(await ensureSession()) || !sessionId.value || chatSending.value) return false
  const visibleMessage = message.trim()
  const displayMessage = visibleMessage || String(action?.label || '').trim()
  const stageBeforeRequest = chatStage.value
  const recommendedSizeTurn = isRecommendedSizeTurn(stageBeforeRequest, visibleMessage, action)
  const preserveRecommendedSize = shouldPreserveRecommendedSizeAfterChat(action, visibleMessage)
  const actionType = String(action?.type || '')
  const actionValue = String(action?.value || '')
  const productSizeBeforeChat = productSize.value
  const productSizeWasRecommendedBeforeChat = productSizeRecommended.value
  // An explicit source edit means the old image is no longer the brief being
  // edited. Conversely, an image action must lock the source before the chat
  // planner gets a chance to return a stale text mode.
  if (actionType === 'edit' && actionValue === 'inspiration') {
    referencePath.value = ''
    referenceAssetId.value = null
    inspirationText.value = ''
  } else if (actionType === 'image' && Number(actionValue) > 0) {
    activateReferenceImageMode()
  }
  // Size recommendation is a deterministic catalog operation. Resolve it on
  // the miniapp even when the conversational planner is unavailable.
  if (recommendedSizeTurn && selectedProduct.value) {
    await chooseRecommendedSizeLocally(displayMessage || '按推荐规格')
    return true
  }
  const productBeforeChat = selectedProduct.value
  const optimisticMessageId = displayMessage && !options.skipUserMessage ? addMessage('user', displayMessage) : null
  let succeeded = false
  chatSending.value = true
  setChatThinking(true, thinkingLabelFor(action, visibleMessage))
  try {
    const result = await sendConversationChat(sessionId.value, { message: visibleMessage, action })
    applyChatBrief(result.brief, recommendedSizeTurn, preserveRecommendedSize)
    if (actionType === 'image' || hasReferenceImage()) {
      // The uploaded pixels remain the source of truth even if the planner
      // echoes a stale `mode: text` or a generated inspiration sentence.
      preserveReferenceImageMode()
      const explicitReferenceSupplement = actionType === 'text'
        || actionType === 'add_detail'
        || (!actionType && Boolean(visibleMessage))
      if (!explicitReferenceSupplement) inspirationText.value = ''
    }
    const materialRecommendationTurn = actionType === 'material' && actionValue.toLowerCase() === 'recommend'
    let materialRecommendationResolved = false
    if (materialRecommendationTurn && selectedProduct.value
      && (!productSizeBeforeChat || productSizeWasRecommendedBeforeChat)) {
      const localSize = localRecommendedProductSize(selectedProduct.value)
      if (localSize) {
        productSize.value = localSize
        productSizeRecommended.value = true
        materialRecommendationResolved = true
        await saveCreativeEventBestEffort('size', 'size_selected', {
          productKey: selectedProduct.value.key,
          productType: selectedProduct.value.name,
          productSize: localSize,
          recommended: true,
          source: 'miniapp_catalog_material_recommend',
        })
      }
    }
    // The chat endpoint is allowed to return an incomplete planner response.
    // Resolve the recommendation from the catalog already loaded by the
    // miniapp so this interaction never falls into the interruption modal.
    if (recommendedSizeTurn) {
      if (!selectedProduct.value && productBeforeChat) selectedProduct.value = productBeforeChat
      if (!productSize.value) productSize.value = localRecommendedProductSize(selectedProduct.value)
      if (productSize.value) {
        productSizeRecommended.value = true
        await saveCreativeEventBestEffort('size', 'size_selected', {
          productKey: selectedProduct.value?.key,
          productType: selectedProduct.value?.name,
          productSize: productSize.value,
          recommended: true,
          source: 'miniapp_catalog_fallback',
        })
      }
    }
    chatStage.value = String(result.stage || 'understanding')
    chatQuickReplies.value = Array.isArray(result.quickReplies) ? result.quickReplies : []
    const recommendedSizeResolved = (recommendedSizeTurn || preserveRecommendedSize || materialRecommendationResolved)
      && result.stage !== 'need_additional_detail'
      && hasCompleteLocalGenerationBrief()
    if (recommendedSizeResolved) {
      // The returned concrete size is authoritative. Do not let a stale stage
      // or stale quick replies from an older service keep asking for size.
      chatStage.value = 'confirm_before_image'
      setGenerationConfirmationReplies()
    }
    const assistantText = recommendedSizeResolved
      ? `根据${selectedProduct.value?.name || '当前产品'}的常用打样规格，我推荐 ${productSize.value}，已为你设置并写入生成提示词。生成前还有需要补充的吗？`
      : String(result.assistantText || '')
    if (assistantText) addAssistantMessage(assistantText)
    succeeded = true
    // Let the assistant reply settle in the transcript before showing the
    // separate, longer-running image-generation status.
    setChatThinking(false)
    const explicitlyConfirmed = action?.type === 'confirm_generate' || isGenerationConfirmationText(visibleMessage)
    const additionalDetailRequired = result.stage === 'need_additional_detail'
    const confirmationRequired = !additionalDetailRequired && (recommendedSizeResolved || Boolean(result.generationConfirmationRequired) || result.stage === 'confirm_before_image')
    awaitingGenerationConfirmation.value = confirmationRequired
    if (confirmationRequired && !chatQuickReplies.value.length) setGenerationConfirmationReplies()
    if (result.readyToGenerate && explicitlyConfirmed && !generatedAssetId.value && phase.value !== 'result' && !autoGenerationInFlight.value) {
      awaitingGenerationConfirmation.value = false
      autoGenerationInFlight.value = true
      try {
        await generateProductImage()
      } finally {
        autoGenerationInFlight.value = false
      }
    } else if (result.readyToGenerate && !explicitlyConfirmed && !generatedAssetId.value && phase.value !== 'result') {
      // Keep a hard client-side guard when an older server response still
      // reports ready=true without a user confirmation action.
      awaitingGenerationConfirmation.value = true
      chatStage.value = 'confirm_before_image'
      setGenerationConfirmationReplies()
      if (!recommendedSizeResolved) addAssistantMessage('生成前确认一下，还有需要补充的吗？没有的话点击“没有补充，开始生成”。')
    }
  } catch (error: any) {
    setChatThinking(false)
    if (optimisticMessageId) messages.value = messages.value.filter(item => item.id !== optimisticMessageId)
    const message = readableErrorMessage(error, '创作服务暂时不可用，当前已输入内容会保留，请稍后重试。')
    console.warn('[conversation-create] chat failed', { message, statusCode: error?.statusCode || 0 })
    uni.showModal({ title: '对话暂时中断', content: message, showCancel: false })
  } finally {
    setChatThinking(false)
    chatSending.value = false
  }
  return succeeded
}

async function goPreviousStep() {
  if (!canGoPrevious.value) return
  const from = phase.value
  if (from === 'multiview' || from === 'model') {
    const to = previousPhase(from)
    if (!to) return
    phase.value = to
    addMessage('assistant', '已返回上一步，现有作品和生成记录不会删除。')
    await saveEvent('navigation', 'previous_step', { from, to })
    await scrollToSection(to === 'multiview' ? 'multiview-output' : 'result-output')
    return
  }
  const target = previousEditTarget.value
  if (!target) return
  const label = ({ product: '修改产品', inspiration: '修改灵感', material: '修改材质', size: '修改尺寸' } as Record<EditableBriefField, string>)[target]
  const edited = await sendChatTurn('', { type: 'edit', value: target, label })
  if (!edited) return
  if (generatedAssetId.value) clearGeneratedOutputForNewDirection()
  phase.value = 'mode'
  await saveEvent('navigation', 'previous_step', { from, to: target })
}

function previousPhase(current: Phase): Phase | null {
  const transitions: Partial<Record<Phase, Phase>> = {
    product: 'mode',
    inspiration: 'product',
    image: 'product',
    material: mode.value === 'image' ? 'image' : 'inspiration',
    result: 'material',
    multiview: 'result',
    model: multiviewImages.value.length >= 3 ? 'multiview' : 'result',
  }
  return transitions[current] || null
}
function goWorks() { uni.navigateTo({ url: '/pages/works/index' }) }
function openCommercial() {
  if (phase.value === 'result') {
    uni.showToast({ title: '请先生成三视图或 3D 原型', icon: 'none' })
    return
  }
  if (phase.value === 'model' && !isModelTaskSucceeded.value) {
    uni.showToast({ title: '请等待 3D 原型生成完成', icon: 'none' })
    return
  }
  const params: string[] = []
  const commercialAssetId = phase.value === 'model' && isModelTaskSucceeded.value ? modelTask.value?.assetId : generatedAssetId.value
  if (!commercialAssetId) {
    uni.showToast({ title: '3D 原型尚未保存完成，请稍后再试', icon: 'none' })
    return
  }
  if (commercialAssetId) params.push('assetId=' + encodeURIComponent(String(commercialAssetId)))
  if (selectedProduct.value?.key) params.push(`productKey=${encodeURIComponent(selectedProduct.value.key)}`)
  if (selectedProduct.value?.name) params.push(`productName=${encodeURIComponent(selectedProduct.value.name)}`)
  if (material.value) params.push(`material=${encodeURIComponent(material.value)}`)
  if (productSize.value) params.push(`productSize=${encodeURIComponent(productSize.value)}`)
  const query = params.join('&')
  uni.navigateTo({ url: `/pages/commercial/index${query ? `?${query}` : ''}` })
}
function selectedModeTitle() { return modeOptions.find(item => item.key === mode.value)?.title || '' }
function showTemplateDeveloping() {
  uni.showModal({
    title: '功能开发中',
    content: '没有灵感示例功能正在开发，敬请期待。你也可以先使用文字或图片灵感开始创作。',
    showCancel: false,
  })
}

function confirmCreativePolicyInPage(key: CreativePolicyKey): Promise<boolean> {
  // Some iOS/DevTools combinations do not render uni.showModal after a long
  // scroll interaction. Use a page-owned layer for the creation flow so the
  // user always sees the required consent action.
  if (policyDialog.value) return Promise.resolve(false)
  return new Promise(resolve => { policyDialog.value = { key, resolve } })
}
function resolvePolicyDialog(confirmed: boolean) {
  const dialog = policyDialog.value
  policyDialog.value = null
  dialog?.resolve(confirmed)
}

function productMark(name: string, category: string) {
  if (name.includes('冰箱贴')) return '贴'
  if (name.includes('徽章')) return '章'
  if (name.includes('钥匙扣')) return '扣'
  if (name.includes('书签')) return '签'
  if (name.includes('杯')) return '杯'
  if (name.includes('包') || name.includes('袋')) return '包'
  if (name.includes('公仔')) return '偶'
  if (name.includes('首饰') || name.includes('项链') || name.includes('耳')) return '饰'
  return ({ food: '食', stationery: '文', daily: '用', toy: '玩', tableware: '器', souvenir: '礼', accessory: '饰', apparel: '衣', craft: '艺', precious: '金' } as Record<string, string>)[category] || '作'
}

function materialColor(material: string) {
  if (/金属|合金|贵金属|马口铁|金箔|溅射金/.test(material)) return 'linear-gradient(145deg,#ead29d,#8a6a45)'
  if (/陶瓷|骨瓷|琉璃|玻璃|搪瓷/.test(material)) return 'linear-gradient(145deg,#fffdf3,#a7c8ba)'
  if (/亚克力|PC|PVC|ABS|硅胶|塑胶|树脂|搪胶/.test(material)) return 'linear-gradient(145deg,#f4fbfc,#97c2c7)'
  if (/毛绒|布艺|帆布|棉|毛毡|纤维|涤纶/.test(material)) return 'linear-gradient(145deg,#f4e7d5,#bc9776)'
  if (/木|竹|纸|杜邦/.test(material)) return 'linear-gradient(145deg,#f1e2c8,#a9835b)'
  return 'linear-gradient(145deg,#e7ece4,#91aa9a)'
}

function productFromSelection(option: SelectionOption): ProductOption {
  return {
    key: option.optionKey,
    name: option.name,
    mark: productMark(option.name, option.categoryKey),
    desc: option.subtitle || option.description,
    process: option.process,
    categoryKey: option.categoryKey,
    categoryName: categoryLabels[option.categoryKey] || option.categoryName || '其他',
    materials: [{ name: option.material, note: `${option.process} · ${option.specification}`, color: materialColor(option.material) }],
    specification: option.specification,
    recommendedSize: normalizeRecommendedSpecification(option.specification) || undefined,
  }
}

async function loadProductCatalog() {
  if (catalogLoading.value) return
  catalogLoading.value = true
  try {
    const options = await getSelectionOptions({ size: 300 })
    productOptions.value = (Array.isArray(options) ? options : []).map(productFromSelection)
    if (!messages.value.length) setInitialChatReplies()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '选品目录暂不可用，请稍后重试', icon: 'none' })
  } finally {
    catalogLoading.value = false
  }
}

function updateProductKeyword(event: any) { productKeyword.value = String(event?.detail?.value || '') }
function productCountForCategory(categoryKey: string) { return productOptions.value.filter(item => item.categoryKey === categoryKey).length }
function categoryMark(categoryKey: string) {
  return ({ food: '食', stationery: '文', souvenir: '礼', accessory: '饰', craft: '艺', daily: '用', tableware: '器', toy: '玩', apparel: '衣', precious: '金' } as Record<string, string>)[categoryKey] || '作'
}

function isNotFound(error: any) { return Number(error?.statusCode) === 404 || /not found|不存在|找不到/i.test(String(error?.message || '')) }

function campaignFromStorage(): CampaignContext | null {
  const context = uni.getStorageSync('creation_context') || {}
  const value = context?.campaign
  if (!value || typeof value !== 'object' || typeof value.key !== 'string' || typeof value.channelCode !== 'string') return null
  return value as CampaignContext
}

function bindCampaignSession() {
  if (!campaignContext.value || !sessionId.value || campaignContext.value.sessionId === sessionId.value) return
  const context = uni.getStorageSync('creation_context') || {}
  campaignContext.value = { ...campaignContext.value, sessionId: sessionId.value }
  uni.setStorageSync('creation_context', { ...context, campaign: campaignContext.value })
}

async function attachCampaignToConversation() {
  const campaign = campaignContext.value
  if (!campaign || campaignAttached.value) return
  campaignAttached.value = true
  bindCampaignSession()
  addMessage('assistant', `已带入「${campaign.title}」。我会把${campaign.collectionStyle}和${campaign.recommendedProducts.join('、')}方向带进后续生成；作品提交审核通过后，${campaign.rewardAmount} 积分会自动到账。`)
  await saveEvent('campaign', 'campaign_selected', {
    campaignKey: campaign.key,
    campaignTitle: campaign.title,
    channelCode: campaign.channelCode,
    targetName: campaign.targetName,
    rewardAmount: campaign.rewardAmount,
  })
}

function productByValue(productType?: string, productKey?: string) {
  return productOptions.value.find(item => item.key === productKey || item.name === productType) || null
}

function chatDraftStorageKey() {
  return sessionId.value ? `conversation-create:draft:${sessionId.value}` : ''
}

function persistChatDraft() {
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  draftSaveTimer = null
  const key = chatDraftStorageKey()
  if (!key) return
  try {
    if (chatInput.value) uni.setStorageSync(key, { value: chatInput.value, updatedAt: Date.now() })
    else uni.removeStorageSync(key)
  } catch {
    // A storage quota issue must not block the conversation itself.
  }
}

function scheduleChatDraftSave() {
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  draftSaveTimer = setTimeout(persistChatDraft, 300)
}

function restoreChatDraft() {
  const key = chatDraftStorageKey()
  if (!key || chatInput.value) return
  try {
    const saved = uni.getStorageSync(key)
    const value = typeof saved === 'string' ? saved : String(saved?.value || '')
    if (value) chatInput.value = value
  } catch {
    // Ignore a malformed or unavailable local draft.
  }
}

function clearChatDraft() {
  if (draftSaveTimer) clearTimeout(draftSaveTimer)
  draftSaveTimer = null
  const key = chatDraftStorageKey()
  if (!key) return
  try { uni.removeStorageSync(key) } catch { /* local storage is best-effort */ }
}

function clearGeneratedOutputForNewDirection() {
  stopModelPolling()
  generatedAssetId.value = null
  pendingImageJobId.value = null
  pendingGenerationPrompt.value = ''
  pendingMultiViewJobId.value = null
  pendingMultiViewInputAssetId.value = null
  pendingMultiViewPrompt.value = ''
  previewUrl.value = ''
  referenceAnalysis.value = ''
  multiviewImages.value = []
  multiviewBundleId.value = null
  multiviewBundleNo.value = ''
  multiviewBundleStatus.value = ''
  multiviewBundleComment.value = ''
  modelInputMode.value = 'single'
  modelTask.value = null
  refiningImage.value = false
  refinementNote.value = ''
}

function editableTarget(value: unknown): EditableBriefField | null {
  const target = String(value || '')
  return ['product', 'inspiration', 'material', 'size'].includes(target) ? target as EditableBriefField : null
}

async function freshAssetPreview(assetId: number) {
  if (!Number.isFinite(assetId) || assetId <= 0) return ''
  try { return imageUrl(await getAssetPreviewAccess(assetId)) } catch { return '' }
}

async function refreshRestoredPreviews() {
  if (generatedAssetId.value) {
    const fresh = await freshAssetPreview(generatedAssetId.value)
    if (fresh) previewUrl.value = fresh
  }
  if (multiviewImages.value.length) {
    multiviewImages.value = await Promise.all(multiviewImages.value.map(async item => {
      const fresh = await freshAssetPreview(Number(item.assetId))
      return fresh ? { ...item, previewUrl: fresh } : item
    }))
  }
  const imageMessages = messages.value.filter(item => item.imageAssetId && !item.imageUrl)
  await Promise.all(imageMessages.map(async item => {
    const fresh = await freshAssetPreview(Number(item.imageAssetId))
    if (fresh) updateImageMessage(item.id, { imageUrl: fresh, imageState: 'ready' })
  }))
}

async function restoreCurrentMultiViewBundle() {
  const inputAssetId = generatedAssetId.value || pendingMultiViewInputAssetId.value
  if (!hasCompleteThreeViews.value || !inputAssetId) return
  try {
    let bundle: MultiViewBundle | undefined
    if (multiviewBundleId.value) {
      const rows = await getMyMultiViewBundles()
      bundle = rows.find(item => Number(item.id || item.bundleId) === Number(multiviewBundleId.value))
    }
    if (!bundle) {
      bundle = await createMultiViewBundle({
        inputAssetId,
        productKey: selectedProduct.value?.key,
        productName: selectedProduct.value?.name,
        material: material.value,
        productSize: productSize.value,
        viewCount: 3,
        images: multiviewImages.value.map(item => ({ view: item.view, assetId: item.assetId, label: item.label })),
      })
    }
    applyMultiViewBundle(bundle)
    await refreshRestoredPreviews()
    updateMultiViewChatState()
  } catch (error: any) {
    uni.showToast({ title: error?.message || '三视图审核状态暂时无法读取', icon: 'none' })
  }
}

function resetViewState() {
  stopModelPolling()
  phase.value = 'mode'
  mode.value = ''
  selectedProduct.value = null
  material.value = ''
  materialChoice.value = 'recommend'
  productSize.value = ''
  productSizeRecommended.value = false
  inspirationText.value = ''
  referencePath.value = ''
  referenceAssetId.value = null
  generatedAssetId.value = null
  pendingImageJobId.value = null
  pendingGenerationPrompt.value = ''
  pendingMultiViewJobId.value = null
  pendingMultiViewInputAssetId.value = null
  pendingMultiViewPrompt.value = ''
  previewUrl.value = ''
  referenceAnalysis.value = ''
  multiviewImages.value = []
  multiviewBundleId.value = null
  multiviewBundleNo.value = ''
  multiviewBundleStatus.value = ''
  multiviewBundleComment.value = ''
  modelInputMode.value = 'single'
  refiningImage.value = false
  refinementNote.value = ''
  modelTask.value = null
  referencePolicyConfirmed.value = false
  aiPolicyConfirmed.value = false
  threeDimensionalPolicyConfirmed.value = false
  messages.value = []
  messageId = 0
  chatQuickReplies.value = []
  chatStage.value = 'need_product'
  chatInput.value = ''
  awaitingGenerationConfirmation.value = false
  campaignAttached.value = false
  setChatThinking(false)
  autoGenerationInFlight.value = false
}

function restoreEvent(event: any) {
  const payload = event?.payload || {}
  switch (String(event?.eventType || '')) {
    case 'mode_selected':
      mode.value = payload.mode || mode.value
      break
    case 'product_selected':
      selectedProduct.value = productByValue(payload.productType, payload.productKey) || selectedProduct.value
      material.value = ''
      materialChoice.value = 'recommend'
      productSize.value = ''
      productSizeRecommended.value = false
      break
    case 'text_inspiration_submitted':
      inspirationText.value = String(payload.inspirationText || '')
      break
    case 'image_inspiration_uploaded':
      mode.value = 'image'
      inspirationText.value = ''
      referenceAssetId.value = Number(payload.inputAssetId) || null
      break
    case 'material_selected':
      material.value = String(payload.material || payload.materialName || material.value)
      materialChoice.value = payload.recommended ? 'recommend' : material.value
      break
    case 'size_selected':
      productSize.value = String(payload.productSize || payload.size || payload.dimensions || productSize.value)
      productSizeRecommended.value = Boolean(payload.recommended && productSize.value)
      break
    case 'campaign_selected':
      campaignAttached.value = true
      break
    case 'style_selected':
    case 'purpose_selected':
    case 'creative_direction_confirmed':
    case 'creative_direction_auto_confirmed':
      if (payload.inspirationText) inspirationText.value = String(payload.inspirationText)
      break
    case 'image_generation_queued':
      pendingImageJobId.value = Number(payload.jobId) || pendingImageJobId.value
      pendingGenerationPrompt.value = String(payload.prompt || pendingGenerationPrompt.value)
      break
    case 'image_generation_failed':
      pendingImageJobId.value = null
      pendingGenerationPrompt.value = ''
      break
    case 'image_generated':
      pendingImageJobId.value = null
      pendingGenerationPrompt.value = ''
      generatedAssetId.value = Number(payload.generatedAssetId) || generatedAssetId.value
      previewUrl.value = imageUrl({ previewUrl: payload.previewUrl })
      referenceAnalysis.value = String(payload.referenceAnalysis || referenceAnalysis.value || '')
      break
    case 'image_refined':
      generatedAssetId.value = Number(payload.generatedAssetId) || generatedAssetId.value
      previewUrl.value = imageUrl({ previewUrl: payload.previewUrl })
      referenceAnalysis.value = String(payload.referenceAnalysis || referenceAnalysis.value || '')
      refinementNote.value = ''
      break
    case 'multiview_queued':
      pendingMultiViewJobId.value = Number(payload.jobId) || pendingMultiViewJobId.value
      pendingMultiViewInputAssetId.value = Number(payload.inputAssetId) || pendingMultiViewInputAssetId.value
      pendingMultiViewPrompt.value = String(payload.prompt || pendingMultiViewPrompt.value)
      break
    case 'multiview_failed':
      pendingMultiViewJobId.value = null
      pendingMultiViewInputAssetId.value = null
      pendingMultiViewPrompt.value = ''
      break
    case 'multiview_generated':
      pendingMultiViewJobId.value = null
      pendingMultiViewInputAssetId.value = Number(payload.inputAssetId) || null
      pendingMultiViewPrompt.value = ''
      multiviewImages.value = Array.isArray(payload.images) ? payload.images : []
      // The generated product image is the source asset for the view package.
      // Older events did not restore it, which left the bundle without an input
      // asset and made the page appear empty after reopening the session.
      if (!generatedAssetId.value && Number(payload.inputAssetId) > 0) generatedAssetId.value = Number(payload.inputAssetId)
      multiviewBundleId.value = Number(payload.bundleId) || multiviewBundleId.value
      multiviewBundleNo.value = String(payload.bundleNo || multiviewBundleNo.value || '')
      multiviewBundleStatus.value = String(payload.bundleStatus || multiviewBundleStatus.value || '')
      multiviewBundleComment.value = String(payload.bundleComment || multiviewBundleComment.value || '')
      break
    case 'model_submitted':
      modelInputMode.value = payload.multiview ? 'multiview' : 'single'
      setModelTask(payload)
      break
    case 'model_completed':
      setModelTask({ ...payload, status: 'succeeded', progress: 100 })
      break
    case 'model_failed':
      setModelTask({ ...payload, status: 'failed' })
      break
    case 'chat_state':
      applyChatBrief(payload)
      break
    case 'chat_user_message':
      if (payload.action?.type === 'edit' && editableTarget(payload.action?.value)) clearGeneratedOutputForNewDirection()
      break
    case 'previous_step':
      if (editableTarget(payload.to)) clearGeneratedOutputForNewDirection()
      break
    default:
      break
  }
}

function restoreMessages(events: any[]) {
  messages.value = []
  messageId = 0
  addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  const hasChatTranscript = events.some(event => ['chat_user_message', 'chat_assistant_message'].includes(String(event?.eventType || '')))
  const legacyConversationEvents = new Set([
    'mode_selected', 'product_selected', 'text_inspiration_submitted',
    'image_inspiration_uploaded', 'image_inspiration_confirmed',
    'material_selected', 'size_selected', 'creative_direction_confirmed', 'creative_direction_auto_confirmed',
  ])
  for (const event of events) {
    if (hasChatTranscript && legacyConversationEvents.has(String(event?.eventType || ''))) continue
    const payload = event?.payload || {}
    switch (String(event?.eventType || '')) {
      case 'mode_selected':
        addRestoredMessage('user', modeOptions.find(item => item.key === payload.mode)?.title || String(payload.modeName || '已选择创作方式'))
        addRestoredMessage('assistant', '好，我们先确定产品方向。你想把它做成什么？')
        break
      case 'product_selected': {
        const product = productByValue(payload.productType, payload.productKey)
        if (product) addRestoredMessage('user', product.name)
        if (mode.value === 'template') addRestoredMessage('assistant', `${product?.name || '这个产品'}很适合先做一版。现在选材质，我会把工艺约束一起带进提示词。`)
        else if (mode.value === 'text') addRestoredMessage('assistant', '收到。把你已有的文字灵感告诉我，不用写成复杂提示词。')
        else addRestoredMessage('assistant', '收到。请上传一张你有权使用的灵感图片，我会保留主体并优化成产品视觉。')
        break
      }
      case 'text_inspiration_submitted':
        if (payload.inspirationText) addRestoredMessage('user', String(payload.inspirationText))
        addRestoredMessage('assistant', '我记下了这段灵感。接下来选择材质，我会把材质、结构和生产限制一起考虑。')
        break
      case 'image_inspiration_uploaded':
        addRestoredImageMessage(Number(payload.inputAssetId), '已上传灵感图片')
        addRestoredMessage('assistant', '图片已收到。你希望它用什么材质？')
        break
      case 'material_selected':
        addRestoredMessage('user', String(payload.material || payload.materialName || material.value))
        addRestoredMessage('assistant', '材质已确认。接下来确认成品尺寸后，我会生成产品图。')
        break
      case 'size_selected':
        addRestoredMessage('user', String(payload.productSize || payload.size || payload.dimensions || productSize.value))
        addRestoredMessage('assistant', '尺寸已确认。我会按这个比例和可生产结构准备产品图。')
        break
      case 'creative_direction_auto_confirmed':
        addRestoredMessage('assistant', `我会根据你的灵感自动匹配${payload.material || material.value}，现在直接生成产品图。`)
        break
      case 'image_generation_queued':
        addRestoredMessage('assistant', '产品图已进入生成队列。离开当前页面也会继续生成，完成后会自动保存到作品库。')
        break
      case 'image_generation_failed':
        addRestoredMessage('assistant', `产品图本次没有生成成功。${payload.errorMessage || '可以调整描述后重新提交。'}`)
        break
      case 'image_generated':
        addRestoredMessage('assistant', '产品视觉已经生成并保存。下一步可以补全四视图、生成 3D，或直接提交商品化申请。')
        break
      case 'image_refined':
        addRestoredMessage('user', `补充修改：${payload.refinementNote || '基于当前图重新生成'}`)
        addRestoredMessage('assistant', '新的产品视觉已经生成，旧版本仍保留在作品库。你可以继续修改，或进入四视图和 3D。')
        break
      case 'multiview_queued':
        addRestoredMessage('assistant', '三视图已进入生成队列。离开当前页面也会继续生成，完成后会自动保存到作品库。')
        break
      case 'multiview_failed':
        addRestoredMessage('assistant', `三视图本次没有生成成功。${payload.errorMessage || '可以稍后重新提交。'}`)
        break
      case 'multiview_generated':
        addRestoredMessage('assistant', '三视图已经保存。现在可以把它们一起交给 3D 建模，结构会比单张图更完整。')
        break
      case 'model_submitted':
        addRestoredMessage('assistant', '3D 建模任务已提交，完成后会出现在作品库。你可以在那里预览、评审并申请打样。')
        break
      case 'model_completed':
        addRestoredMessage('assistant', '3D 模型已经生成并保存到作品库，可以继续评审、申请打样或提交商品化报价。')
        break
      case 'model_failed':
        addRestoredMessage('assistant', '3D 建模没有完成，失败原因已保存。可以检查产品图后重新提交。')
        break
      case 'chat_user_message':
        if (payload.action?.type === 'image' && Number(payload.action?.value) > 0) {
          addRestoredImageMessage(Number(payload.action.value), '已上传灵感图片')
        } else if (payload.message) addRestoredMessage('user', String(payload.message))
        else if (payload.action?.label) addRestoredMessage('user', String(payload.action.label))
        break
      case 'chat_assistant_message':
        if (payload.text) addRestoredMessage('assistant', String(payload.text))
        if (Array.isArray(payload.quickReplies)) chatQuickReplies.value = payload.quickReplies
        if (payload.stage) chatStage.value = String(payload.stage)
        if (!generatedAssetId.value && (payload.generationConfirmationRequired || (payload.readyToGenerate && payload.generationConfirmed !== true))) {
          awaitingGenerationConfirmation.value = true
          chatStage.value = 'confirm_before_image'
          if (!chatQuickReplies.value.length) setGenerationConfirmationReplies()
        }
        break
      default:
        break
    }
  }
}

function addRestoredImageMessage(assetId: number, text = '已上传灵感图片') {
  if (!Number.isFinite(assetId) || assetId <= 0) return null
  if (messages.value.some(item => item.imageAssetId === assetId)) return null
  const id = ++messageId
  messages.value.push({ id, role: 'user', text, imageAssetId: assetId, imageState: 'ready' })
  return id
}

function restorePhase(events: any[]) {
  phase.value = 'mode'
  for (const event of events) {
    switch (String(event?.eventType || '')) {
      case 'mode_selected': phase.value = 'product'; break
      case 'product_selected': phase.value = mode.value === 'template' ? 'material' : mode.value === 'text' ? 'inspiration' : 'image'; break
      case 'text_inspiration_submitted': phase.value = 'material'; break
      case 'image_inspiration_uploaded': phase.value = 'image'; break
      case 'image_inspiration_confirmed': phase.value = 'material'; break
      case 'material_selected': phase.value = 'material'; break
      case 'size_selected': phase.value = 'size'; break
      case 'creative_direction_confirmed':
      case 'creative_direction_auto_confirmed': phase.value = 'material'; break
      case 'image_generation_queued': phase.value = 'material'; break
      case 'image_generation_failed': phase.value = 'material'; break
      case 'image_generated': phase.value = 'result'; break
      case 'image_refined': phase.value = 'result'; break
      case 'multiview_queued': phase.value = 'result'; break
      case 'multiview_failed': phase.value = 'result'; break
      case 'multiview_generated': phase.value = 'multiview'; break
      case 'model_submitted': phase.value = 'model'; break
      case 'model_completed': phase.value = 'model'; break
      case 'model_failed': phase.value = 'model'; break
      case 'chat_user_message':
        if (event?.payload?.action?.type === 'edit' && editableTarget(event.payload.action?.value)) phase.value = 'mode'
        break
      case 'previous_step': {
        const destination = String(event?.payload?.to || '')
        if (editableTarget(destination)) phase.value = 'mode'
        else if (destination === 'result' || destination === 'multiview' || destination === 'model') phase.value = destination
        break
      }
      case 'chat_assistant_message':
        if (event?.payload?.stage) chatStage.value = String(event.payload.stage)
        if (!generatedAssetId.value && (event?.payload?.generationConfirmationRequired || (event?.payload?.readyToGenerate && event?.payload?.generationConfirmed !== true))) {
          chatStage.value = 'confirm_before_image'
          awaitingGenerationConfirmation.value = true
        } else if (event?.payload?.readyToGenerate) {
          chatStage.value = 'ready_for_image'
        }
        break
      default: break
    }
  }
}

async function restoreSession(sessionToRestore: number | string) {
  try {
    const detail = await getConversation(sessionToRestore)
    const events = Array.isArray(detail.events) ? detail.events : []
    resetViewState()
    sessionId.value = Number(detail.id)
    for (const event of events) restoreEvent(event)
    restoreMessages(events)
    restorePhase(events)
    await refreshRestoredPreviews()
    return Boolean(sessionId.value)
  } catch (error) {
    // A session may belong to an old deployment or have been removed. Do not
    // expose a raw 404 toast; start a fresh draft instead.
    if (isNotFound(error)) return false
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
    if (!requireSession()) return false
    if (sessionId.value) return true
    try {
      if (!forceNewSession.value) {
        try {
          const campaignSessionId = Number(campaignContext.value?.sessionId) || 0
          if (campaignSessionId > 0 && await restoreSession(campaignSessionId)) return true
          if (await restoreLatestSession()) return true
        } catch (error: any) { if (!isNotFound(error)) throw error }
      }
      const session = await createConversation()
      sessionId.value = Number(session.id)
      resetViewState()
      return Boolean(sessionId.value)
    } catch (error: any) {
      uni.showToast({ title: isNotFound(error) ? '创作服务暂时不可用，请稍后再试' : (error?.message || '无法建立创作会话'), icon: 'none' })
      return false
    }
  })()
  const result = await sessionPromise
  sessionReady.value = result
  return result
}
async function saveEvent(step: string, eventType: string, payload: Record<string, any>) {
  if (!(await ensureSession()) || !sessionId.value) return
  saving.value = true
  try { await saveConversationEvent(sessionId.value, { step, eventType, payload }) }
  catch {
    // Conversation history improves continuity, but must never interrupt a
    // user's product selection or AI generation when a background save fails.
  }
  finally { saving.value = false }
}
async function chooseMode(value: Mode) {
  if (busy.value) return
  if (value === 'template') {
    showTemplateDeveloping()
    return
  }
  mode.value = value
  if (value === 'image') {
    inspirationText.value = ''
  } else {
    // Choosing a new non-image source starts a new direction. Do not let an
    // earlier reference image silently force this turn back to image-to-image.
    referencePath.value = ''
    referenceAssetId.value = null
  }
  addMessage('user', selectedModeTitle())
  addMessage('assistant', '好，我们先确定产品方向。你想把它做成什么？')
  await saveEvent('mode', 'mode_selected', { mode: value, modeName: selectedModeTitle() })
  phase.value = 'product'
}
async function chooseProduct(value: ProductOption) {
  selectedProduct.value = value
  material.value = ''
  materialChoice.value = 'recommend'
  productSize.value = ''
  productSizeRecommended.value = false
  addMessage('user', value.name)
  await saveEvent('product', 'product_selected', { productKey: value.key, productType: value.name, process: value.process })
  if (mode.value === 'template') {
    addMessage('assistant', `${value.name}很适合先做一版。现在选材质，我会把工艺约束一起带进提示词。`)
    phase.value = 'material'
  } else if (mode.value === 'text') {
    addMessage('assistant', '收到。把你已有的文字灵感告诉我，不用写成复杂提示词。')
    phase.value = 'inspiration'
  } else {
    addMessage('assistant', '收到。请上传一张你有权使用的灵感图片，我会保留主体并优化成产品视觉。')
    phase.value = 'image'
  }
}
async function submitTextInspiration() {
  if (!inspirationText.value.trim()) return
  addMessage('user', inspirationText.value.trim())
  await saveEvent('inspiration', 'text_inspiration_submitted', { productType: selectedProduct.value?.name, inspirationText: inspirationText.value.trim() })
  addMessage('assistant', '灵感已记录。请再确认材质，随后我会询问成品尺寸。')
  phase.value = 'material'
}
async function pickInspirationImage() {
  if (busy.value) {
    uni.showToast({ title: '图片正在上传或生成中，请稍候', icon: 'none' })
    return
  }
  if (!referencePolicyConfirmed.value) {
    const confirmed = await confirmCreativePolicyInPage('reference-materials')
    if (!confirmed) return
    referencePolicyConfirmed.value = true
    await saveEvent('compliance', 'policy_notice_confirmed', { policyKey: 'reference-materials', policyVersion: CREATIVE_POLICY_VERSION })
  }
  // chooseImage is the protected API itself. Calling it directly lets WeChat
  // invoke the app-level privacy resolver and then resume this exact action.
  // A separate requirePrivacyAuthorize call can consume the tap without
  // opening the album on some base-library versions.
  uni.chooseImage({ count: 1, sizeType: ['compressed'], sourceType: ['album'], success: (result) => {
    const path = result.tempFilePaths?.[0]
    if (!path) {
      uni.showToast({ title: '没有读取到图片，请重新选择', icon: 'none' })
      return
    }
    activateReferenceImageMode()
    referencePath.value = path
    referenceAssetId.value = null
    void uploadInspirationImage(path)
  }, fail: (error: any) => {
    const message = String(error?.errMsg || '')
    if (/cancel/i.test(message)) return
    const hint = /privacy/i.test(message)
      ? '请先同意小程序隐私保护指引后重试'
      : /auth|permission|deny/i.test(message)
        ? '微信没有读取相册的权限，请在系统设置中检查微信的照片权限'
        : '微信未能选取这张图片'
    uni.showModal({ title: '选择图片失败', content: `${hint}\n\n微信返回：${message || '未提供错误信息'}`, showCancel: false })
  } })
}
async function uploadInspirationImage(path: string) {
  const imageMessageId = addImageMessage(path, '正在上传灵感图片…', 'uploading')
  busy.value = true
  try {
    const result = await uploadReference(path)
    const id = Number(result?.assetId)
    if (!Number.isFinite(id) || id <= 0) throw new Error('图片上传成功但没有返回作品编号')
    activateReferenceImageMode()
    referenceAssetId.value = id
    // Show the local image immediately. Replace it with the server-controlled
    // preview in the background so a slow media-token request cannot stall the
    // conversation turn.
    updateImageMessage(imageMessageId, { text: '已上传灵感图片', imageUrl: path, imageAssetId: id, imageState: 'ready' })
    void freshAssetPreview(id).then(storedPreview => {
      if (storedPreview) updateImageMessage(imageMessageId, { imageUrl: storedPreview, imageState: 'ready' })
    })
    await saveCreativeEventBestEffort('inspiration', 'image_inspiration_uploaded', { productType: selectedProduct.value?.name, inputAssetId: id, fileType: 'image' })
    uni.showToast({ title: '图片已留存', icon: 'success' })
    // The upload is complete before the chat turn starts. Release the upload
    // lock so a ready image conversation can enter the normal generation path.
    busy.value = false
    await sendChatTurn('我已上传灵感图片', { type: 'image', value: String(id), label: '已上传灵感图片' }, { skipUserMessage: true })
  } catch (error: any) {
    updateImageMessage(imageMessageId, { imageState: 'failed', text: '这张灵感图片上传失败' })
    const message = error?.message || '图片上传失败'
    // Toast 文案长度有限，网络上传错误改用弹窗，避免关键的微信错误被截断。
    uni.showModal({ title: '图片上传失败', content: message, showCancel: false })
  }
  finally { busy.value = false }
}
async function submitImageInspiration() {
  if (!referenceAssetId.value) return
  addMessage('user', '已上传一张灵感图片')
  await saveEvent('inspiration', 'image_inspiration_confirmed', { productType: selectedProduct.value?.name, inputAssetId: referenceAssetId.value })
  addMessage('assistant', '图片已收到。你希望它用什么材质？')
  phase.value = 'material'
}
async function chooseMaterial(value: MaterialOption) {
  material.value = value.name
  materialChoice.value = value.name
  addMessage('user', value.name)
  await saveEvent('material', 'material_selected', { productType: selectedProduct.value?.name, material: value.name, materialNote: value.note })
  await generateImageAfterMaterialSelection()
}
function recommendedMaterial() {
  return currentMaterials.value[0] || null
}
async function chooseRecommendedMaterial() {
  const recommendation = recommendedMaterial()
  if (!recommendation) {
    uni.showToast({ title: '暂时无法推荐材质，请手动选择', icon: 'none' })
    return
  }
  material.value = recommendation.name
  materialChoice.value = 'recommend'
  addMessage('user', '你帮我推荐材质')
  await saveEvent('material', 'material_selected', { productType: selectedProduct.value?.name, material: recommendation.name, materialNote: recommendation.note, recommended: true })
  addMessage('assistant', `根据${selectedProduct.value?.name || '当前产品'}的结构和工艺，我推荐${recommendation.name}。${recommendation.note}`)
  await generateImageAfterMaterialSelection()
}

async function generateImageAfterMaterialSelection() {
  if (!productSize.value) {
    addMessage('assistant', '材质已确认。这件产品想做多大？例如 60×60×3mm、直径 80mm 或 A5；不确定时可以按推荐规格。')
    phase.value = 'size'
    return
  }
  addMessage('assistant', '材质和尺寸已确认，现在生成产品图。')
  await generateProductImage()
}

function updateImageQueueMessage(job: { status?: string; jobType?: string; queuePosition?: number }) {
  if (job.status === 'queued') {
    const stageMessage = imageGenerationStage.value === 'adapting_product'
      ? '产品化生成任务已排队，正在准备最终成品…'
      : ''
    busyMessage.value = stageMessage || (job.queuePosition && job.queuePosition > 0
      ? `已进入生成队列，前面还有 ${job.queuePosition - 1} 项任务…`
      : '已进入生成队列，马上开始…')
  } else if (job.status === 'running') {
    busyMessage.value = imageGenerationStage.value === 'adapting_product'
      ? '正在把参考图元素转译为目标文创产品，请稍候…'
      : job.jobType === 'multi_view'
      ? '正在生成一致的产品多视图，请稍候…'
      : job.jobType === 'image_to_image'
        ? '正在依据参考图生成产品视觉，请稍候…'
        : 'Seedream 5.0 正在生成产品视觉，请稍候…'
  }
}

/**
 * Keep the image-to-image prompt deterministic. A generic text optimizer can
 * rewrite a visual reference into a flat poster brief, so the reference path
 * sends the user's product brief plus immutable carrier/framing locks directly
 * to Seedream. Text and multi-view prompts may still use Qwen as an enhancer,
 * but the original requirements are retained alongside its candidate.
 */
async function resolveSeedreamPrompt(sourcePrompt: string, purpose: 'initial' | 'multiview' = 'initial') {
  const original = sourcePrompt.trim()
  if (!original || !selectedProduct.value) return original
  const multiviewReferenceMode = purpose === 'multiview'
  const productAdaptationMode = isReferenceImageMode() && !multiviewReferenceMode
  const referenceConstraint = multiviewReferenceMode
    ? REFERENCE_PROMPT_GUARD
    : productAdaptationMode
      ? productTransformationGuard()
      : ''
  const optimizerInput = referenceConstraint ? `${original}\nReference-image constraint: ${referenceConstraint}` : original
  let optimized = original
  if (!productAdaptationMode) {
    busyMessage.value = '正在整理产品提示词…'
    try {
      const result = await optimizeImagePrompt({
        prompt: optimizerInput,
        provider: 'ark',
        productCategory: selectedProduct.value.name,
        material: material.value,
        productSize: productSize.value,
      })
      const candidate = String(result?.prompt || '').trim()
      if (candidate) {
        // Do not let a candidate silently delete the user's product or
        // cultural requirements. The immutable locks are appended below too.
        const merged = `${candidate}\nCore user requirements that must remain: ${original}`
        optimized = merged.length > 1600 ? `${candidate.slice(0, 1050)}\nCore user requirements that must remain: ${original.slice(0, 500)}` : merged
      }
      try {
        await saveEvent('summary', 'prompt_optimized', {
          purpose,
          productType: selectedProduct.value.name,
          material: material.value,
          productSize: productSize.value,
          sourcePrompt: original,
          optimizerInput,
          optimizedPrompt: optimized,
          optimizer: 'siliconflow_qwen',
          imageProvider: 'volcengine_ark_seedream_5',
        })
      } catch {
        // Prompt telemetry must never block an otherwise valid image request.
      }
    } catch {
      try {
        await saveEvent('summary', 'prompt_optimization_fallback', {
          purpose,
          productType: selectedProduct.value.name,
          material: material.value,
          productSize: productSize.value,
          sourcePrompt: original,
          reason: 'optimization_unavailable',
          imageProvider: 'volcengine_ark_seedream_5',
        })
      } catch {
        // Keep the original prompt even when the event endpoint is unavailable.
      }
    }
  } else {
    // Initial image conversion is intentionally single-pass: the uploaded
    // pixels remain the source of truth and are not replaced by a lossy
    // intermediate image or a generic text-to-image rewrite.
    await saveCreativeEventBestEffort('summary', 'prompt_optimization_skipped', {
      purpose,
      productType: selectedProduct.value.name,
      material: material.value,
      productSize: productSize.value,
      reason: 'direct_reference_product_adaptation',
    })
  }
  // Keep the selected finished-product specification in the actual image
  // prompt even when the optimizer rewrites or shortens the user's wording.
  // The same value is sent as structured request metadata below.
  const productSizeGuard = productSize.value
    ? `成品尺寸/规格必须严格按「${productSize.value}」执行；这是成品规格，不是图片分辨率。`
    : ''
  // Keep the carrier, framing and reference constraints in the final client
  // payload as immutable guards. The server adds its own identity lock too.
  const multiviewCarrierGuard = purpose === 'multiview'
    ? [`【产品形态】${productFormProfile(selectedProduct.value).prompt}`, PRODUCT_FRAME_GUARD].join(' ')
    : ''
  const immutableGuard = productAdaptationMode
    ? productTransformationGuard()
    : [referenceConstraint, multiviewCarrierGuard].filter(Boolean).join(' ')
  const immutableTail = [productSizeGuard, immutableGuard].filter(Boolean).join(' ')
  return composeBoundedPrompt(optimized, immutableTail)
}

/**
 * Keep the client payload conservative because the server adds its own
 * product-identity lock before calling Seedream. The task/rules tail wins;
 * optional user prose is the first part shortened. If a future profile grows
 * beyond the budget, preserve both its opening task and closing size/check.
 */
function composeBoundedPrompt(core: string, immutableTail: string, budget = CLIENT_PROMPT_BUDGET) {
  const normalizedCore = core.trim()
  const normalizedTail = immutableTail.trim()
  if (!normalizedTail) return normalizedCore.slice(0, budget)
  if (normalizedTail.length >= budget) {
    const headLength = Math.floor(budget * 0.68)
    const tailLength = Math.max(1, budget - headLength - 20)
    return `${normalizedTail.slice(0, headLength)}\n...\n${normalizedTail.slice(-tailLength)}`
  }
  const coreLength = Math.max(0, budget - normalizedTail.length - 1)
  const boundedCore = normalizedCore.slice(0, coreLength)
  return [boundedCore, normalizedTail].filter(Boolean).join(' ')
}

async function saveCreativeEventBestEffort(step: string, eventType: string, payload: Record<string, any> = {}) {
  if (!sessionId.value) return
  try {
    await saveEvent(step, eventType, payload)
  } catch (error) {
    console.warn('[conversation-create] event persistence skipped', { step, eventType, error })
  }
}

async function completeGeneratedProductImage(result: any, generationPrompt: string) {
  if (!selectedProduct.value) throw new Error('当前产品信息已失效，请重新选择产品')
  const assetId = Number(result?.assetId || result?.id)
  if (!Number.isFinite(assetId) || assetId <= 0) throw new Error('产品图没有保存成功，请重新生成')
  pendingImageJobId.value = null
  pendingGenerationPrompt.value = ''
  generatedAssetId.value = assetId
  previewUrl.value = imageUrl(result) || await freshAssetPreview(assetId)
  referenceAnalysis.value = String(result?.referenceAnalysis || '')
  await saveCreativeEventBestEffort('image', 'image_generated', { jobId: result?.jobId, productType: selectedProduct.value.name, material: material.value, productSize: productSize.value, prompt: generationPrompt, sourcePrompt: prompt.value, generatedAssetId: generatedAssetId.value, previewUrl: previewUrl.value, mode: mode.value, referenceAssetId: referenceAssetId.value, inspirationText: inspirationText.value, referenceStrategy: isReferenceImageMode() ? 'direct_single_pass' : 'text_to_image', productForm: productFormProfile(selectedProduct.value).key, referenceAnalysis: result?.referenceAnalysis || '', referenceAnalysisSource: result?.referenceAnalysisSource || '' })
  addMessage('assistant', '产品视觉已经生成并保存。下一步请生成三视图或 3D 原型，完成后才能提交审核和申请打样。')
  chatStage.value = 'image_ready'
  chatQuickReplies.value = [
    { label: '满意，生成三视图', type: 'multiview', value: '' },
    { label: '不满意，告诉我怎么改', type: 'refine', value: '' },
    { label: '生成 3D 原型', type: 'model', value: '' },
  ]
  phase.value = 'result'
  await scrollToSection('result-output')
}

async function generateProductImage() {
  if (busy.value) {
    uni.showToast({ title: '图片正在生成，请不要重复提交', icon: 'none' })
    return
  }
  if (!selectedProduct.value || !material.value || !productSize.value) {
    uni.showModal({ title: '暂时不能生成', content: '请先完成产品、材质和成品尺寸的确认，再生成产品图。', showCancel: false })
    return
  }
  if (!aiPolicyConfirmed.value) {
    const confirmed = await confirmCreativePolicyInPage('ai-output')
    if (!confirmed) {
      uni.showToast({ title: '已取消本次 AI 生成', icon: 'none' })
      return
    }
    aiPolicyConfirmed.value = true
    await saveEvent('compliance', 'policy_notice_confirmed', { policyKey: 'ai-output', policyVersion: CREATIVE_POLICY_VERSION })
  }
  busy.value = true
  busyMessage.value = '正在保存创作参数…'
  let queueEventPromise: Promise<void> | null = null
  try {
    await saveCreativeEventBestEffort('summary', 'generation_started', {
      productType: selectedProduct.value.name,
      material: material.value,
      productSize: productSize.value,
      prompt: prompt.value,
      mode: mode.value,
      referenceAssetId: referenceAssetId.value,
      inspirationText: inspirationText.value,
      imageElementTranslation: isReferenceImageMode(),
      referenceStrategy: isReferenceImageMode() ? 'direct_single_pass' : 'text_to_image',
      productForm: productFormProfile(selectedProduct.value).key,
    })
    const generationPrompt = await resolveSeedreamPrompt(prompt.value)
    let result: any
    if (isReferenceImageMode()) {
      if (!referenceAssetId.value) throw new Error('参考图片还没有保存完成，请重新上传后再生成')
      // The API accepts one reference asset. Keep the original upload as that
      // asset: a generated cleanup image can shrink, recolor or flatten the
      // subject, and the second i2i pass cannot recover those lost pixels.
      const referenceInputAssetId = referenceAssetId.value
      const adaptationGuard = productTransformationGuard()
      imageGenerationStage.value = 'adapting_product'
      busyMessage.value = `正在依据参考图生成${selectedProduct.value.name}产品，预计需要 1-3 分钟…`
      result = await createReferenceToImage({
        title: `${selectedProduct.value.name} · 对话创作`,
        prompt: generationPrompt,
        rawPrompt: prompt.value,
        negativePrompt: `${PRODUCT_OUTPUT_NEGATIVE}, ${productFormNegative(selectedProduct.value)}`,
        imageSize: SEEDREAM_IMAGE_SIZE,
        inputAssetId: referenceInputAssetId,
        productKey: selectedProduct.value.key,
        productCategory: selectedProduct.value.name,
        material: material.value,
        productSize: productSize.value,
        // This is an initial product conversion, not a revision of an already
        // generated product. The normal reference-preserving path gives the
        // target carrier priority without locking the intermediate composition.
        refinement: false,
        refinementNote: adaptationGuard,
      }, (job) => {
        updateImageQueueMessage(job)
        const jobId = Number(job.jobId)
        if (Number.isFinite(jobId) && jobId > 0 && pendingImageJobId.value !== jobId) {
          pendingImageJobId.value = jobId
          pendingGenerationPrompt.value = generationPrompt
          queueEventPromise = saveCreativeEventBestEffort('image', 'image_generation_queued', {
            jobId,
            productType: selectedProduct.value?.name,
            material: material.value,
            productSize: productSize.value,
            sourceReferenceAssetId: referenceAssetId.value,
            designSourceAssetId: referenceInputAssetId,
            mode: mode.value,
            inspirationText: inspirationText.value,
            referenceStrategy: 'direct_single_pass',
            prompt: generationPrompt,
          })
        }
      })
    } else {
      busyMessage.value = '正在提交 Seedream 5.0 生图任务…'
      result = await createTextToImage({ title: `${selectedProduct.value.name} · 对话创作`, prompt: generationPrompt, rawPrompt: inspirationText.value || prompt.value, negativePrompt: `${PRODUCT_OUTPUT_NEGATIVE}, ${productFormNegative(selectedProduct.value)}`, imageSize: SEEDREAM_IMAGE_SIZE, productType: selectedProduct.value.name, productKey: selectedProduct.value.key, productCategory: selectedProduct.value.name, material: material.value, productSize: productSize.value }, (job) => {
        updateImageQueueMessage(job)
        const jobId = Number(job.jobId)
        if (Number.isFinite(jobId) && jobId > 0 && pendingImageJobId.value !== jobId) {
          pendingImageJobId.value = jobId
          pendingGenerationPrompt.value = generationPrompt
          queueEventPromise = saveCreativeEventBestEffort('image', 'image_generation_queued', {
            jobId,
            productType: selectedProduct.value?.name,
            material: material.value,
            productSize: productSize.value,
            prompt: generationPrompt,
          })
        }
      })
    }
    if (queueEventPromise) await queueEventPromise
    await completeGeneratedProductImage(result, generationPrompt)
  } catch (error: any) {
    const message = generationFailureMessage(error)
    uni.showModal({ title: '产品图未生成', content: message, showCancel: false })
  }
  finally {
    busy.value = false
    imageGenerationStage.value = ''
    busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
  }
}

async function resumePendingImageGeneration() {
  const jobId = pendingImageJobId.value
  if (!jobId || generatedAssetId.value || busy.value) return
  busy.value = true
  busyMessage.value = '正在恢复上次的图片生成进度…'
  try {
    let job = await getArkImageJob(jobId)
    updateImageQueueMessage(job)
    if (job.status === 'queued' || job.status === 'running') {
      job = await waitForArkImageJob(job, updateImageQueueMessage)
    }
    await completeGeneratedProductImage(job, pendingGenerationPrompt.value || prompt.value)
  } catch (error: any) {
    let failedJob: any = null
    try {
      const latest = await getArkImageJob(jobId)
      if (latest.status === 'failed') failedJob = latest
    } catch {
      // Keep the pending job attached when the network itself is unavailable.
    }
    if (failedJob) {
      pendingImageJobId.value = null
      pendingGenerationPrompt.value = ''
      await saveEvent('image', 'image_generation_failed', { jobId, errorMessage: failedJob.errorMessage || failedJob.message || '图片生成失败' })
    }
    uni.showModal({ title: failedJob ? '产品图未生成' : '生成进度暂时无法读取', content: generationFailureMessage(failedJob || error), showCancel: false })
  } finally {
    busy.value = false
    busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
  }
}

function generationFailureMessage(error: any) {
  const raw = String(error?.message || error?.errMsg || '').trim()
  if (/timeout|timed out|超时/i.test(raw)) return '生成请求等待超时。之间大模型生成通常需要 1-3 分钟，请检查网络后重新提交；本次失败不会扣除未成功生成的积分。'
  if (/登录已过期|请先登录|401/i.test(raw)) return '登录状态已失效，请重新登录后再生成。'
  if (/安全体验模式|SetLimitExceeded|模型.*暂停/i.test(raw)) return '方舟模型的安全体验额度已用尽，服务已暂停。请联系平台管理员在火山方舟控制台提高额度或关闭安全体验模式后重试。'
  if (/ark api key|火山方舟|服务尚未配置|未配置/i.test(raw)) return 'AI 生图服务没有完成配置。请检查服务器上的 VOLCENGINE_ARK_API_KEY 和模型开通状态，配置后重启 smart-pig 服务。'
  if (/网络|network|fail|connect|refused|域名/i.test(raw)) return '无法连接 AI 生图服务。请检查微信公众平台 request 合法域名、网络连接和服务器运行状态。'
  return raw || '生成服务暂时不可用，请稍后重试。'
}
function imageUrl(item: any) {
  const raw = String(item?.previewUrl || item?.imageUrl || item?.fileUrl || item?.url || item?.accessUrl || '')
  if (/^https?:\/\//i.test(raw)) return raw
  return raw.startsWith('/') ? apiUrl(raw) : ''
}
async function previewMessageImage(item: Message) {
  let url = item.imageUrl || ''
  if (!url && item.imageAssetId) url = await freshAssetPreview(item.imageAssetId)
  if (!url) {
    uni.showToast({ title: item.imageState === 'failed' ? '图片上传失败，请重新选择' : '图片还在加载，请稍候', icon: 'none' })
    return
  }
  if (!item.imageUrl) updateImageMessage(item.id, { imageUrl: url, imageState: 'ready' })
  uni.previewImage({ current: url, urls: [url] })
}
let copyingMessageText = false

function clipboardText(value: unknown) {
  // Keep line breaks/tabs, but remove control characters that can make the
  // native clipboard reject an otherwise valid string.
  return String(value ?? '').replace(/[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/g, '').trim()
}

function invokeClipboardApi(api: { setClipboardData: (options: WechatClipboardOptions) => void }, data: string) {
  return new Promise<void>((resolve, reject) => {
    try {
      const options: WechatClipboardOptions = {
        data,
        success: () => resolve(),
        fail: (error) => reject(error),
      }
      api.setClipboardData(options)
    } catch (error) {
      reject(error)
    }
  })
}

async function writeClipboardText(data: string) {
  let firstError: unknown
  // In the WeChat mini program, call the host API directly first. This avoids
  // an adapter mismatch in older uni runtimes while retaining a uni fallback
  // for H5/App and newer runtimes.
  try {
    const nativeWx = typeof wx !== 'undefined' ? wx : undefined
    if (nativeWx && typeof nativeWx.setClipboardData === 'function') {
      await invokeClipboardApi({ setClipboardData: nativeWx.setClipboardData.bind(nativeWx) }, data)
      return true
    }
  } catch (error) {
    firstError = error
    // wx and uni both reach the same host API in a mini program. Retrying a
    // privacy-scope rejection only duplicates the warning and cannot change
    // the platform-side declaration.
    if (isClipboardPrivacyError(error)) throw error
  }

  try {
    await new Promise<void>((resolve, reject) => {
      try {
        uni.setClipboardData({
          data,
          showToast: false,
          success: () => resolve(),
          fail: (error) => reject(error),
        })
      } catch (error) {
        reject(error)
      }
    })
    return false
  } catch (error) {
    throw firstError || error
  }
}

function isClipboardPrivacyError(error: unknown) {
  // This scope is controlled by the WeChat admin-side privacy guide, not the
  // location-only `requiredPrivateInfos` app.json setting.
  const raw = String((error as any)?.errMsg || (error as any)?.message || '')
  return /privacy agreement|privacy policy|scope is not declared|隐私协议|隐私指引/i.test(raw)
}

function clipboardFailureMessage(error: any) {
  const raw = String(error?.errMsg || error?.message || '').trim()
  console.warn('[clipboard] setClipboardData failed', { errMsg: raw, errCode: error?.errCode })
  if (isClipboardPrivacyError(error)) {
    return '小程序隐私指引尚未声明剪贴板，请联系管理员配置；也可长按文字复制'
  }
  if (/not support|not available|undefined|not a function|navigator\.clipboard/i.test(raw)) {
    return '当前环境不支持复制，请在微信小程序中操作'
  }
  if (/auth|permission|denied|forbidden|拒绝/i.test(raw)) {
    return '剪贴板权限被系统拒绝，请允许后重试'
  }
  return '复制失败，可长按文字复制'
}

async function copyMessageText(item: Message) {
  const value = clipboardText(item.text)
  if (!value || copyingMessageText) return
  copyingMessageText = true
  try {
    const nativeToastShown = await writeClipboardText(value)
    if (!nativeToastShown) uni.showToast({ title: '已复制', icon: 'success' })
  } catch (error) {
    uni.showToast({ title: clipboardFailureMessage(error), icon: 'none' })
  } finally {
    copyingMessageText = false
  }
}
async function previewMultiViewImage(item: SeedreamMultiViewImage) {
  let current = imageUrl(item)
  if (!current && Number(item.assetId) > 0) current = await freshAssetPreview(Number(item.assetId))
  if (!current) {
    uni.showToast({ title: '视图还在加载，请稍候', icon: 'none' })
    return
  }
  const urls = multiviewImages.value.map(imageUrl).filter(Boolean)
  uni.previewImage({ current, urls: urls.length ? urls : [current] })
}
function previewImage() { if (previewUrl.value) uni.previewImage({ current: previewUrl.value, urls: [previewUrl.value] }) }
function startRefinement() {
  if (!generatedAssetId.value) {
    uni.showToast({ title: '当前产品图未保存成功，请先重新生成', icon: 'none' })
    return
  }
  refinementNote.value = ''
  refiningImage.value = true
}
function cancelRefinement() {
  refiningImage.value = false
  refinementNote.value = ''
}
async function regenerateWithRefinement() {
  const sourceAssetId = generatedAssetId.value
  const note = refinementNote.value.trim()
  if (busy.value || !sourceAssetId || !note || !selectedProduct.value) return
  busy.value = true
  busyMessage.value = '正在理解修改要求并生成新方案，请稍候…'
  try {
    let refinementPrompt = note
    try {
      busyMessage.value = '正在由之间大模型优化修改要求…'
      const optimized = await optimizeImageEditPrompt({
        prompt: prompt.value,
        refinementNote: note,
        productCategory: selectedProduct.value.name,
        material: material.value,
        productSize: productSize.value,
      })
      if (String(optimized?.prompt || '').trim()) refinementPrompt = String(optimized.prompt).trim()
    } catch {
      // The edit request remains usable when prompt optimization is temporarily unavailable.
    }
    const refinementGuard = productTransformationGuard()
    refinementPrompt = composeBoundedPrompt(refinementPrompt, refinementGuard)
    busyMessage.value = '正在基于当前产品图生成新方案，请稍候…'
    await saveCreativeEventBestEffort('image', 'image_refinement_started', { inputAssetId: sourceAssetId, refinementNote: note, optimizedPrompt: refinementPrompt, productType: selectedProduct.value.name, material: material.value, productSize: productSize.value })
    const result = await createReferenceToImage({ title: `${selectedProduct.value.name} · 修改方案`, prompt: refinementPrompt, rawPrompt: note, negativePrompt: `${PRODUCT_OUTPUT_NEGATIVE}, ${productFormNegative(selectedProduct.value)}`, imageSize: SEEDREAM_IMAGE_SIZE, inputAssetId: sourceAssetId, productKey: selectedProduct.value.key, productCategory: selectedProduct.value.name, material: material.value, productSize: productSize.value, refinement: true, refinementNote: `${note}\n${refinementGuard}` }, updateImageQueueMessage)
    const newAssetId = Number(result?.assetId || result?.id)
    if (!Number.isFinite(newAssetId) || newAssetId <= 0) throw new Error('修改后的产品图没有保存成功，请重试')
    generatedAssetId.value = newAssetId
    previewUrl.value = imageUrl(result) || await freshAssetPreview(newAssetId)
    referenceAnalysis.value = String(result?.referenceAnalysis || referenceAnalysis.value || '')
    multiviewImages.value = []
    multiviewBundleId.value = null
    multiviewBundleNo.value = ''
    multiviewBundleStatus.value = ''
    multiviewBundleComment.value = ''
    stopModelPolling()
    modelTask.value = null
    modelInputMode.value = 'single'
    await saveCreativeEventBestEffort('image', 'image_refined', { previousAssetId: sourceAssetId, generatedAssetId: newAssetId, previewUrl: previewUrl.value, refinementNote: note, optimizedPrompt: refinementPrompt, productType: selectedProduct.value.name, material: material.value, productSize: productSize.value, referenceAnalysis: result?.referenceAnalysis || '', referenceAnalysisSource: result?.referenceAnalysisSource || '' })
    addMessage('user', `补充修改：${note}`)
    addMessage('assistant', '新的产品视觉已经生成，旧版本仍保留在作品库。你可以继续修改，或进入四视图和 3D。')
    cancelRefinement()
    await scrollToSection('result-output')
  } catch (error: any) {
    uni.showToast({ title: error?.message || '重新生成失败，请稍后重试', icon: 'none' })
  } finally {
    busy.value = false
    busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
  }
}

function setModelTask(payload: any) {
  const jobId = Number(payload?.jobId || payload?.modelJobId)
  if (!Number.isFinite(jobId) || jobId <= 0) return
  modelTask.value = {
    jobId,
    status: String(payload?.status || 'running').toLowerCase(),
    progress: Number(payload?.progress) || 0,
    assetId: Number(payload?.assetId || payload?.modelAssetId) || null,
    previewUrl: imageUrl(payload),
    errorMessage: String(payload?.errorMessage || payload?.error || ''),
  }
}

function stopModelPolling() {
  modelPollVersion += 1
  if (modelPollTimer) clearTimeout(modelPollTimer)
  modelPollTimer = null
}

async function refreshModelTask() {
  if (!modelTask.value || modelRefreshing.value) return
  modelRefreshing.value = true
  try {
    const result = await getTripoModelTask(modelTask.value.jobId)
    const previousStatus = modelTask.value.status
    setModelTask(result)
    if (!modelTask.value) return
    if (modelTask.value.status === 'succeeded' && previousStatus !== 'succeeded') {
      await saveEvent('model', 'model_completed', { modelJobId: modelTask.value.jobId, assetId: modelTask.value.assetId, status: 'succeeded', progress: 100, previewUrl: modelTask.value.previewUrl })
      addMessage('assistant', '3D 模型已经生成并保存到作品库，可以继续评审、申请打样或提交商品化报价。')
      chatStage.value = 'model_ready'
      chatQuickReplies.value = [
        { label: '申请打样 / 商品化', type: 'commercial', value: '' },
        { label: '查看我的作品', type: 'works', value: '' },
      ]
      if (phase.value === 'model') await scrollToSection('model-output')
    } else if (modelTask.value.status === 'failed' && previousStatus !== 'failed') {
      await saveEvent('model', 'model_failed', { modelJobId: modelTask.value.jobId, status: 'failed', progress: modelTask.value.progress, errorMessage: modelTask.value.errorMessage })
      addMessage('assistant', '3D 建模没有完成，失败原因已保存。可以检查产品图后重新提交。')
    }
  } catch (error: any) {
    if (modelTask.value && !isModelTaskTerminal.value) modelTask.value.errorMessage = error?.message || '暂时无法读取建模进度，系统会自动重试'
  } finally {
    modelRefreshing.value = false
  }
}

async function scheduleModelPolling(immediate = false) {
  stopModelPolling()
  const version = modelPollVersion
  const poll = async () => {
    if (version !== modelPollVersion || !modelTask.value || isModelTaskTerminal.value) return
    await refreshModelTask()
    if (version !== modelPollVersion || !modelTask.value || isModelTaskTerminal.value) return
    modelPollTimer = setTimeout(poll, 5000)
  }
  if (immediate) await poll()
  else modelPollTimer = setTimeout(poll, 5000)
}

async function generateMultiView() {
  if (busy.value) return
  if (!generatedAssetId.value) {
    uni.showToast({ title: '当前产品图未保存成功，请先重新生成产品图', icon: 'none' })
    return
  }
  if (!aiPolicyConfirmed.value) {
    const confirmed = await confirmCreativePolicyInPage('ai-output')
    if (!confirmed) return
    aiPolicyConfirmed.value = true
    await saveEvent('compliance', 'policy_notice_confirmed', { policyKey: 'ai-output', policyVersion: CREATIVE_POLICY_VERSION })
  }
  busy.value = true
  busyMessage.value = '正在基于当前产品图生成正面、侧面和背面，请稍候…'
  const inputAssetId = generatedAssetId.value
  let queueEventPromise: Promise<void> | null = null
  try {
    await saveCreativeEventBestEffort('multiview', 'multiview_started', { inputAssetId, productType: selectedProduct.value?.name, material: material.value, productSize: productSize.value })
    const multiviewPrompt = await resolveSeedreamPrompt(prompt.value, 'multiview')
    const result = await createSeedreamMultiView({ inputAssetId, prompt: multiviewPrompt, productKey: selectedProduct.value?.key, productCategory: selectedProduct.value?.name, material: material.value, productSize: productSize.value, viewCount: 3, size: SEEDREAM_IMAGE_SIZE, watermark: true }, (job) => {
      updateImageQueueMessage(job)
      const jobId = Number(job.jobId)
      if (Number.isFinite(jobId) && jobId > 0 && pendingMultiViewJobId.value !== jobId) {
        pendingMultiViewJobId.value = jobId
        pendingMultiViewInputAssetId.value = inputAssetId
        pendingMultiViewPrompt.value = multiviewPrompt
        queueEventPromise = saveCreativeEventBestEffort('multiview', 'multiview_queued', {
          jobId,
          inputAssetId,
          prompt: multiviewPrompt,
          productType: selectedProduct.value?.name,
          material: material.value,
          productSize: productSize.value,
        })
      }
    })
    if (queueEventPromise) await queueEventPromise
    await completeGeneratedMultiView(result, inputAssetId)
  } catch (error: any) { uni.showToast({ title: error?.message || '三视图生成失败', icon: 'none' }) }
  finally { busy.value = false; busyMessage.value = '正在保存创作过程并调用 AI，请稍候…' }
}

async function completeGeneratedMultiView(result: any, inputAssetId: number) {
  // The conversational route is a three-view product package. If an older
  // provider response also contains a right view, keep only the contractual
  // front/left/back set so bundle creation cannot fail on an unexpected extra
  // image.
  const viewOrder = ['front', 'left', 'back'] as const
  const labels: Record<string, string> = { front: '正面', left: '侧面', back: '背面' }
  const images = ((Array.isArray(result?.images) ? result.images : []) as SeedreamMultiViewImage[])
    .filter(item => viewOrder.includes(String(item?.view || '').toLowerCase() as typeof viewOrder[number]) && Number(item?.assetId) > 0)
    .map(item => ({
      ...item,
      view: String(item.view).toLowerCase() as SeedreamMultiViewImage['view'],
      label: item.label || labels[String(item.view).toLowerCase()] || '视图',
      assetId: Number(item.assetId),
    }))
    .sort((left, right) => viewOrder.indexOf(left.view as typeof viewOrder[number]) - viewOrder.indexOf(right.view as typeof viewOrder[number]))
  const returnedViews = new Set(images.map(item => item.view))
  if (!viewOrder.every(view => returnedViews.has(view))) throw new Error('三视图没有完整返回正面、侧面和背面，请稍后重试')
  const hydratedImages = await Promise.all(images.map(async item => {
    const fresh = await freshAssetPreview(item.assetId)
    return fresh ? { ...item, previewUrl: fresh } : item
  }))
  multiviewImages.value = hydratedImages
  const bundle = await createMultiViewBundle({
    inputAssetId,
    productKey: selectedProduct.value?.key,
    productName: selectedProduct.value?.name,
    material: material.value,
    productSize: productSize.value,
    viewCount: 3,
    images: hydratedImages.map(item => ({ view: item.view, assetId: item.assetId, label: item.label })),
  })
  applyMultiViewBundle(bundle)
  pendingMultiViewJobId.value = null
  pendingMultiViewInputAssetId.value = null
  pendingMultiViewPrompt.value = ''
  await saveCreativeEventBestEffort('multiview', 'multiview_generated', {
    jobId: result?.jobId,
    inputAssetId,
    productSize: productSize.value,
    bundleId: bundle.id,
    bundleNo: bundle.bundleNo,
    bundleStatus: bundle.status,
    bundleComment: bundle.reviewComment,
    images: hydratedImages.map(item => ({ view: item.view, assetId: item.assetId, label: item.label })),
  })
  addMessage('assistant', '正面、侧面和背面已保存为一个作品包。先提交整包审核，审核通过后就可以申请打样；如果审核未通过，我会把原因保留在这里。')
  updateMultiViewChatState()
  phase.value = 'multiview'
  await scrollToSection('multiview-output')
}

function applyMultiViewBundle(bundle: MultiViewBundle | null | undefined) {
  if (!bundle) return
  multiviewBundleId.value = Number(bundle.id || bundle.bundleId) || multiviewBundleId.value
  if (!generatedAssetId.value && Number(bundle.inputAssetId) > 0) generatedAssetId.value = Number(bundle.inputAssetId)
  multiviewBundleNo.value = String(bundle.bundleNo || multiviewBundleNo.value || '')
  multiviewBundleStatus.value = String(bundle.status || multiviewBundleStatus.value || 'draft')
  multiviewBundleComment.value = String(bundle.reviewComment || '')
  if (Array.isArray(bundle.images) && bundle.images.length) {
    multiviewImages.value = bundle.images
      .filter(item => Number(item?.assetId) > 0)
      .map(item => ({ ...item, assetId: Number(item.assetId) })) as SeedreamMultiViewImage[]
  }
}

function updateMultiViewChatState() {
  if (multiviewBundleStatus.value === 'approved') {
    chatStage.value = 'multiview_approved'
    chatQuickReplies.value = [
      { label: '申请打样', type: 'bundle_production', value: '' },
      { label: '继续生成 3D', type: 'model', value: '' },
    ]
  } else if (multiviewBundleStatus.value === 'review') {
    chatStage.value = 'multiview_review'
    chatQuickReplies.value = [{ label: '查看我的作品', type: 'works', value: '' }]
  } else if (multiviewBundleStatus.value === 'rejected') {
    chatStage.value = 'multiview_rejected'
    chatQuickReplies.value = [{ label: '重新提交审核', type: 'bundle_review', value: '' }]
  } else {
    chatStage.value = 'multiview_ready'
    chatQuickReplies.value = [{ label: '提交三视图审核', type: 'bundle_review', value: '' }]
  }
}

async function submitMultiViewReview() {
  if (multiviewBundleSubmitting.value || !multiviewBundleId.value) return
  const context = uni.getStorageSync('creation_context') || {}
  const purpose = context.purpose === 'museum_sale' ? 'museum_sale' : 'personal'
  const museumId = purpose === 'museum_sale' ? String(context.museum?.id || '') : undefined
  const campaign = context.campaign && typeof context.campaign === 'object' ? context.campaign : null
  if (purpose === 'museum_sale' && !museumId) {
    uni.showToast({ title: '请先选择服务博物馆，再提交三视图审核', icon: 'none' })
    return
  }
  if (campaign?.key && (purpose !== 'museum_sale' || campaign.channelCode !== context.museum?.channelCode)) {
    uni.showToast({ title: '优先征集任务与当前渠道不一致，请重新选择方向', icon: 'none' })
    return
  }
  uni.showModal({
    title: '提交三视图审核',
    content: purpose === 'museum_sale' ? `将把正面、侧面和背面作为一个作品包提交给${context.museum?.name || '目标渠道'}审核。` : '三张图会作为一个完整作品包提交审核，审核通过后才能申请打样。',
    confirmText: '提交审核',
    success: async result => {
      if (!result.confirm) return
      multiviewBundleSubmitting.value = true
      try {
        const response = await submitMultiViewBundleReview(multiviewBundleId.value as number, {
          purpose,
          museumId,
          note: '由对话式创作提交的三视图作品包',
          ...(campaign?.key ? { campaignKey: campaign.key } : {}),
        })
        applyMultiViewBundle(response)
        updateMultiViewChatState()
        await saveEvent('multiview', 'multiview_review_submitted', { bundleId: multiviewBundleId.value, status: response.status, purpose })
        uni.showToast({ title: response.message || '三视图已提交审核', icon: 'success' })
      } catch (error: any) {
        uni.showToast({ title: error?.message || '提交三视图审核失败', icon: 'none' })
      } finally {
        multiviewBundleSubmitting.value = false
      }
    },
  })
}

function applyMultiViewProduction() {
  if (!multiviewBundleId.value || multiviewBundleStatus.value !== 'approved') return
  const title = selectedProduct.value?.name || '三视图作品'
  uni.navigateTo({ url: `/pages/production/index?bundleId=${encodeURIComponent(String(multiviewBundleId.value))}&title=${encodeURIComponent(title)}` })
}

async function resumePendingMultiViewGeneration() {
  const jobId = pendingMultiViewJobId.value
  const inputAssetId = pendingMultiViewInputAssetId.value || generatedAssetId.value
  if (!jobId || !inputAssetId || hasCompleteThreeViews.value || busy.value) return
  busy.value = true
  busyMessage.value = '正在恢复上次的三视图生成进度…'
  try {
    let job = await getArkImageJob(jobId)
    updateImageQueueMessage(job)
    if (job.status === 'queued' || job.status === 'running') job = await waitForArkImageJob(job, updateImageQueueMessage)
    if (job.status === 'failed') throw new Error(job.errorMessage || job.message || '三视图生成失败')
    await completeGeneratedMultiView(job, inputAssetId)
  } catch (error: any) {
    let failedJob: any = null
    try {
      const latest = await getArkImageJob(jobId)
      if (latest.status === 'failed') failedJob = latest
    } catch {
      // Keep the pending job attached when the network itself is unavailable.
    }
    if (failedJob) {
      pendingMultiViewJobId.value = null
      pendingMultiViewInputAssetId.value = null
      pendingMultiViewPrompt.value = ''
      await saveEvent('multiview', 'multiview_failed', { jobId, inputAssetId, errorMessage: failedJob.errorMessage || failedJob.message || '三视图生成失败' })
    }
    uni.showModal({ title: failedJob ? '三视图未生成' : '三视图进度暂时无法读取', content: generationFailureMessage(failedJob || error), showCancel: false })
  } finally {
    busy.value = false
    busyMessage.value = '正在保存创作过程并调用 AI，请稍候…'
  }
}
async function generateModel() {
  if (busy.value) return
  if (!generatedAssetId.value) {
    uni.showToast({ title: '当前产品图未保存成功，请先重新生成产品图', icon: 'none' })
    return
  }
  if (!threeDimensionalPolicyConfirmed.value) {
    const confirmed = await confirmCreativePolicyInPage('three-dimensional')
    if (!confirmed) return
    threeDimensionalPolicyConfirmed.value = true
    await saveEvent('compliance', 'policy_notice_confirmed', { policyKey: 'three-dimensional', policyVersion: CREATIVE_POLICY_VERSION })
  }
  busy.value = true
  busyMessage.value = '正在提交 3D 建模任务，请稍候…'
  try {
    const useMultiview = phase.value === 'multiview'
    if (useMultiview && !hasCompleteThreeViews.value) throw new Error('请先生成完整的正面、侧面和背面，再提交多视图建模')
    if (useMultiview && multiviewBundleStatus.value !== 'approved') throw new Error('三视图作品包需先通过人工审核，再继续建模或申请打样')
    modelInputMode.value = useMultiview ? 'multiview' : 'single'
    busyMessage.value = useMultiview ? '正在提交三视图 3D 建模任务，请稍候…' : '正在提交单图 3D 建模任务，请稍候…'
    const payload: any = { title: `${selectedProduct.value?.name || '文创产品'} · ${useMultiview ? '三视图' : '单图'} 3D 原型`, prompt: prompt.value, rawPrompt: prompt.value, mode: useMultiview ? 'multiview_to_model' : 'image_to_model', inputAssetId: generatedAssetId.value, productKey: selectedProduct.value?.key, productCategory: selectedProduct.value?.name, material: material.value, productSize: productSize.value, materialLabel: material.value, materialPrompt: `manufacturing material: ${material.value}`, multiviewAssetIds: useMultiview ? Object.fromEntries(multiviewImages.value.map(item => [item.view, Number(item.assetId)])) : undefined, exportFormats: 'GLB', texture: true, pbr: true, textureQuality: 'extreme', geometryQuality: 'detailed', textureAlignment: 'original_image', orientation: 'align_image', autoSize: true, imageAutofix: true, exportUv: true, faceLimit: 2000000 }
    await saveEvent('model', 'model_started', { inputAssetId: generatedAssetId.value, multiview: useMultiview, inputMode: modelInputMode.value, productType: selectedProduct.value?.name, material: material.value, productSize: productSize.value })
    const result = await createModel(payload)
    const jobId = Number(result?.jobId)
    if (!Number.isFinite(jobId) || jobId <= 0) throw new Error('3D 服务没有返回任务编号，请稍后重试')
    setModelTask({ jobId, status: result?.status, progress: result?.progress, assetId: result?.assetId })
    await saveEvent('model', 'model_submitted', { inputAssetId: generatedAssetId.value, multiview: useMultiview, inputMode: modelInputMode.value, modelJobId: jobId, modelAssetId: result?.assetId, status: modelTask.value?.status || 'running', progress: modelTask.value?.progress || 0, productType: selectedProduct.value?.name, material: material.value, productSize: productSize.value })
    addMessage('assistant', `${modelInputLabel.value}任务已提交，完成后会出现在作品库。你可以在那里预览、评审并申请打样。`)
    chatStage.value = 'model_running'
    chatQuickReplies.value = [{ label: '查看我的作品', type: 'works', value: '' }]
    phase.value = 'model'
    await scrollToSection('model-output')
    void scheduleModelPolling(true)
  } catch (error: any) { uni.showToast({ title: error?.message || '3D 任务提交失败', icon: 'none' }) }
  finally { busy.value = false; busyMessage.value = '正在保存创作过程并调用 AI，请稍候…' }
}
function restart() {
  if (busy.value || saving.value) {
    uni.showToast({ title: '当前正在保存或生成，请稍候', icon: 'none' })
    return
  }
  uni.showModal({
    title: '重新开始创作',
    content: '当前进度会保留在创作记录中，并为你新建一份空白创作。',
    confirmText: '重新开始',
    success: result => {
      if (!result.confirm) return
      persistChatDraft()
      uni.redirectTo({ url: '/pages/conversation-create/index?new=1' })
    },
  })
}
watch(chatInput, scheduleChatDraftSave)
onLoad(options => {
  campaignContext.value = campaignFromStorage()
  const campaignNeedsSession = Boolean(campaignContext.value && !Number(campaignContext.value.sessionId))
  forceNewSession.value = String(options?.new || '') === '1' || campaignNeedsSession
})
onMounted(async () => {
  if (!requireSession()) return
  if (!messages.value.length) addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  if (!chatQuickReplies.value.length) setInitialChatReplies()
  await loadProductCatalog()
  if (!(await ensureSession())) return
  restoreChatDraft()
  if (!messages.value.length) addMessage('assistant', '你好，我会像一位产品设计师一样，一步一步把你的想法整理成可生成、可建模、可打样的文创产品。')
  await attachCampaignToConversation()
  if (awaitingGenerationConfirmation.value && !chatQuickReplies.value.length) setGenerationConfirmationReplies()
  if (!chatQuickReplies.value.length && chatStage.value !== 'need_additional_detail') setInitialChatReplies()
  if (pendingImageJobId.value && !generatedAssetId.value) void resumePendingImageGeneration()
  else if (pendingMultiViewJobId.value && !hasCompleteThreeViews.value) void resumePendingMultiViewGeneration()
  if (phase.value === 'multiview' && hasCompleteThreeViews.value) await restoreCurrentMultiViewBundle()
  if (phase.value === 'model' && modelTask.value && !isModelTaskTerminal.value) void scheduleModelPolling(true)
  if (phase.value === 'result') await scrollToSection('result-output')
  else if (phase.value === 'multiview') await scrollToSection('multiview-output')
  else if (phase.value === 'model') await scrollToSection('model-output')
})
onHide(persistChatDraft)
onUnload(persistChatDraft)
onUnmounted(() => { persistChatDraft(); resolvePolicyDialog(false); stopModelPolling() })
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding-bottom:116rpx;background:linear-gradient(180deg,#f7f3ed 0%,#f1ece4 100%);color:#332d28}.topbar{position:fixed;z-index:5;top:0;left:0;right:0;display:flex;align-items:center;gap:12rpx;padding:18rpx 26rpx calc(16rpx + env(safe-area-inset-top));border-bottom:1rpx solid rgba(116,96,75,.12);background:rgba(247,243,237,.96);backdrop-filter:blur(14rpx)}.back{width:48rpx;height:48rpx;color:#6d5f52;font-size:58rpx;line-height:38rpx;text-align:center}.topbar>view:nth-child(2){display:flex;flex:1;flex-direction:column;gap:4rpx}.eyebrow{color:#668071;font-size:14rpx;font-weight:900;letter-spacing:2rpx}.top-title{font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.save-state{color:#88988b;font-size:15rpx}.chat{height:calc(100vh - 132rpx);box-sizing:border-box;padding:126rpx 24rpx 26rpx}.intro-line{margin:0 2rpx 20rpx;padding:12rpx 14rpx;border-left:3rpx solid #b58b69;background:#f3eee6;color:#84786c;font-size:15rpx;line-height:1.5}.message-row{display:flex;align-items:flex-start;gap:9rpx;margin:17rpx 0}.message-row.user{justify-content:flex-end}.avatar{display:grid;place-items:center;flex:0 0 48rpx;width:48rpx;height:48rpx;border-radius:15rpx;background:#5e7c6d;color:#fff;font-family:"Songti SC","STSong",serif;font-size:25rpx}.bubble{max-width:78%;padding:14rpx 16rpx;border:1rpx solid #e2d8cb;border-radius:17rpx;background:#fffdfa;box-shadow:0 6rpx 15rpx rgba(80,61,42,.045)}.bubble text{color:#534940;font-size:20rpx;line-height:1.55}.user .bubble{border-color:#a9bdae;background:#e5efe7}.user .bubble text{color:#4f685b}.choice-panel,.input-panel,.summary-panel,.result-panel{margin:22rpx 0 26rpx;padding:19rpx;border:1rpx solid #e2d9ce;border-radius:22rpx;background:rgba(255,253,249,.9);box-shadow:0 10rpx 25rpx rgba(79,60,41,.06)}.choice-title{display:block;color:#403831;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.choice-note{display:block;margin-top:7rpx;color:#8c8075;font-size:16rpx;line-height:1.5}.choice-grid,.product-grid,.material-grid{display:grid;gap:10rpx;margin-top:15rpx}.choice-card{display:grid;grid-template-columns:50rpx minmax(0,1fr) 20rpx;align-items:center;gap:10rpx;padding:13rpx;border:1rpx solid #e4dbd0;border-radius:16rpx;background:#fffefa}.choice-mark,.product-mark{display:grid;place-items:center;width:47rpx;height:47rpx;border-radius:14rpx;background:#e8f0e8;color:#5e806e;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:800}.choice-card view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.choice-card view text:first-child{color:#463d35;font-size:21rpx;font-weight:800}.choice-card view text:last-child{color:#92867a;font-size:15rpx;line-height:1.4}.choice-arrow{color:#a16f59;font-size:33rpx}.product-grid{grid-template-columns:1fr 1fr}.product-card{display:flex;min-height:177rpx;flex-direction:column;padding:14rpx;border:1rpx solid #e5dbce;border-radius:17rpx;background:#fffefa}.product-card:active,.choice-card:active,.next-card:active{background:#f4efe7}.product-card:nth-child(2n) .product-mark{background:#f7e8df;color:#a96750}.product-card:nth-child(3n) .product-mark{background:#f5edd9;color:#947144}.product-name{margin-top:10rpx;color:#443a32;font-size:20rpx;font-weight:850}.product-desc{margin-top:5rpx;color:#8b7f73;font-size:14rpx;line-height:1.4}.product-process{margin-top:auto;color:#8c6e59;font-size:14rpx;font-weight:800}.text-input{width:100%;min-height:190rpx;box-sizing:border-box;margin-top:16rpx;padding:14rpx;border:1rpx solid #ddd2c5;border-radius:15rpx;background:#fbf9f5;color:#443b33;font-size:20rpx;line-height:1.6}.input-foot{display:flex;align-items:center;justify-content:space-between;margin-top:12rpx;color:#a09387;font-size:14rpx}.dark-button,.outline-button{height:76rpx;margin-top:15rpx;border-radius:14rpx;font-size:21rpx;font-weight:800}.dark-button{background:#3f3933;color:#fff}.dark-button::after,.outline-button::after,.link-button::after{border:0}.dark-button[disabled]{opacity:.48}.full-button{width:100%}.image-picker{display:flex;align-items:center;justify-content:center;height:300rpx;margin-top:16rpx;overflow:hidden;border:1rpx dashed #b5a796;border-radius:17rpx;background:#faf7f1}.image-picker>view{display:flex;align-items:center;flex-direction:column;gap:8rpx;color:#96897b}.image-picker>view text:first-child{font-size:62rpx;line-height:1}.image-picker image{width:100%;height:100%}.material-grid{grid-template-columns:1fr 1fr}.material-card{display:grid;grid-template-columns:36rpx minmax(0,1fr) 22rpx;align-items:center;gap:9rpx;min-height:74rpx;padding:11rpx;border:1rpx solid #e2d8cc;border-radius:15rpx;background:#fffefa}.material-card.active{border-color:#80a28f;background:#eef5ee}.swatch{width:32rpx;height:32rpx;border:1rpx solid rgba(100,80,58,.16);border-radius:10rpx}.material-card view:nth-child(2){display:flex;min-width:0;flex-direction:column;gap:4rpx}.material-card view text:first-child{color:#493f36;font-size:18rpx;font-weight:800}.material-card view text:last-child{color:#94877b;font-size:13rpx;line-height:1.3}.check{color:#56816c;font-size:21rpx;font-weight:900}.style-section{margin-top:17rpx}.style-section>text{color:#72675c;font-size:16rpx;font-weight:800}.pill-row{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:9rpx}.pill{padding:9rpx 12rpx;border:1rpx solid #e1d7cb;border-radius:999rpx;background:#fffefa;color:#897d71;font-size:15rpx}.pill.active{border-color:#6e907e;background:#e7f0e8;color:#4d715f;font-weight:800}.summary-card{display:grid;gap:0;margin-top:15rpx;border-top:1rpx solid #e6ddd2}.summary-card>view{display:grid;grid-template-columns:110rpx 1fr;gap:10rpx;padding:12rpx 0;border-bottom:1rpx solid #eee7df}.summary-card text:first-child{color:#9c8b7d;font-size:15rpx}.summary-card text:last-child{color:#4c4239;font-size:17rpx;line-height:1.45}.summary-note,.result-tip{display:block;margin-top:14rpx;color:#82766a;font-size:16rpx;line-height:1.55}.link-button{display:block;margin:13rpx auto 0;padding:0;background:transparent;color:#93705d;font-size:16rpx}.result-kicker{display:block;color:#9d7a5e;font-size:14rpx;font-weight:900;letter-spacing:2rpx}.result-image{width:100%;height:430rpx;margin-top:15rpx;border-radius:17rpx;background:#eee7dc}.result-placeholder{display:flex;align-items:center;justify-content:center;height:260rpx;margin-top:15rpx;flex-direction:column;gap:9rpx;border-radius:17rpx;background:linear-gradient(145deg,#d9e7dc,#ead9cc);color:#557365}.result-placeholder text:first-child{font-family:"Songti SC","STSong",serif;font-size:62rpx}.result-placeholder text:last-child{font-size:16rpx}.next-grid{display:grid;gap:10rpx;margin-top:17rpx}.next-card{display:grid;grid-template-columns:48rpx minmax(0,1fr) 18rpx;align-items:center;gap:10rpx;padding:13rpx;border:1rpx solid #e2d8cd;border-radius:15rpx;background:#fffefa}.next-card>text:first-child{display:grid;place-items:center;width:44rpx;height:44rpx;border-radius:13rpx;background:#edf3eb;color:#5e806e;font-family:"Songti SC","STSong",serif;font-size:24rpx;font-weight:800}.next-card view{display:flex;min-width:0;flex-direction:column;gap:4rpx}.next-card view text:first-child{color:#473d35;font-size:19rpx;font-weight:800}.next-card view text:last-child{color:#92867a;font-size:14rpx}.next-card>text:last-child{color:#a16f59;font-size:31rpx}.view-grid{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:15rpx}.view-card{overflow:hidden;border:1rpx solid #e2d8cd;border-radius:14rpx;background:#fffefa}.view-card image,.view-placeholder{display:block;width:100%;height:190rpx;background:#eee8df}.view-placeholder{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:5rpx;color:#817367;font-size:15rpx}.view-card>text:last-child{display:block;padding:8rpx 10rpx;color:#6f6257;font-size:15rpx;font-weight:800}.model-success{display:flex;align-items:center;gap:14rpx;margin-top:18rpx;padding:16rpx;border-radius:16rpx;background:#e8f0e9}.model-success>text{display:grid;place-items:center;width:74rpx;height:74rpx;border-radius:22rpx;background:#5f7d6e;color:#fff;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.model-success view{display:flex;flex:1;flex-direction:column;gap:6rpx}.model-success view text:first-child{color:#4c6e5c;font-size:20rpx;font-weight:800}.model-success view text:last-child{color:#789082;font-size:14rpx;line-height:1.4}.model-progress{margin-top:15rpx;padding:13rpx;border:1rpx solid #dbe7dc;border-radius:14rpx;background:#f7fbf6}.model-progress>view:first-child{display:flex;justify-content:space-between;color:#54715f;font-size:15rpx;font-weight:800}.model-progress-track{height:12rpx;margin-top:10rpx;overflow:hidden;border-radius:999rpx;background:#dbe8dd}.model-progress-value{height:100%;border-radius:inherit;background:#648875;transition:width .35s ease}.model-error{display:block;margin-top:12rpx;padding:11rpx;border-radius:12rpx;background:#fff0ec;color:#a05543;font-size:14rpx;line-height:1.45}.outline-button{border:1rpx solid #9ab4a2;background:#f7fbf6;color:#557564}.loading-bar{position:fixed;z-index:7;right:20rpx;bottom:115rpx;left:20rpx;padding:12rpx 14rpx;border:1rpx solid #d9c8b5;border-radius:13rpx;background:#fff7eb;color:#96704f;font-size:15rpx;text-align:center;box-shadow:0 8rpx 20rpx rgba(81,58,35,.12)}.bottom-actions{position:fixed;z-index:6;right:0;bottom:0;left:0;display:flex;justify-content:space-around;padding:13rpx 20rpx calc(13rpx + env(safe-area-inset-bottom));border-top:1rpx solid rgba(110,91,70,.14);background:rgba(247,243,237,.96);backdrop-filter:blur(13rpx)}.bottom-actions button{margin:0;background:transparent;color:#6f6256;font-size:16rpx}.bottom-actions button::after{border:0}
.catalog-tools{margin-top:15rpx;padding:12rpx;border:1rpx solid #e6ddd2;border-radius:15rpx;background:#f8f4ed}.catalog-search{box-sizing:border-box;width:100%;height:66rpx;padding:0 13rpx;border:1rpx solid #ded4c7;border-radius:11rpx;background:#fffefa;color:#4c433a;font-size:18rpx}.catalog-categories{margin-top:10rpx;white-space:nowrap}.catalog-categories>view{display:flex;gap:7rpx}.catalog-category{display:inline-block;padding:7rpx 10rpx;border:1rpx solid #ded5c9;border-radius:9rpx;background:#fffefa;color:#897d72;font-size:14rpx}.catalog-category.active{border-color:#72917f;background:#e7f0e7;color:#4e705e;font-weight:800}.catalog-count{display:block;margin-top:9rpx;color:#907d6f;font-size:13rpx}.catalog-empty{margin-top:15rpx;padding:34rpx 16rpx;border:1rpx dashed #d8cbbd;border-radius:15rpx;background:#faf7f1;color:#8f8276;font-size:17rpx;text-align:center}.product-card{min-height:187rpx}.product-category-name{margin-top:9rpx;color:#9c8879;font-size:12rpx}.product-name{margin-top:4rpx;line-height:1.35}.product-desc,.product-process{display:-webkit-box;overflow:hidden;-webkit-box-orient:vertical}.product-desc{-webkit-line-clamp:2}.product-process{font-size:13rpx;line-height:1.35;-webkit-line-clamp:2}
.category-entry-grid{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:15rpx}.category-entry{display:grid;grid-template-columns:44rpx minmax(0,1fr) 15rpx;align-items:center;gap:8rpx;min-height:112rpx;padding:12rpx;border:1rpx solid #dfd6ca;border-radius:15rpx;background:#fffefa}.category-entry:active{background:#f1f5ef}.category-entry-mark{display:grid;place-items:center;width:42rpx;height:42rpx;border-radius:12rpx;background:#e7f0e8;color:#567665;font-family:"Songti SC","STSong",serif;font-size:23rpx;font-weight:800}.category-entry view{display:flex;min-width:0;flex-direction:column;gap:5rpx}.category-entry view text:first-child{overflow:hidden;color:#4d433b;font-size:17rpx;font-weight:850;text-overflow:ellipsis;white-space:nowrap}.category-entry view text:last-child{color:#97887b;font-size:12rpx}.category-entry>text:last-child{color:#aa7a61;font-size:27rpx}.catalog-result-title{display:block;margin:16rpx 2rpx 0;color:#6a5c4e;font-size:18rpx;font-weight:850}
.recommendation-card{border-color:#a8beab;background:#f0f7ef}.recommendation-mark{display:grid;place-items:center;width:32rpx;height:32rpx;border-radius:10rpx;background:#5d806b;color:#fff;font-size:18rpx;font-weight:850}.recommendation-pill{border-color:#8cad98;background:#edf5ed;color:#4f715d;font-weight:850}
.refinement-panel{margin-top:16rpx;padding:15rpx;border:1rpx solid #d8c9b7;border-radius:15rpx;background:#f8f3eb}.refinement-panel>text:first-child{display:block;color:#5c5044;font-size:19rpx;font-weight:850}.refinement-input{min-height:130rpx;margin-top:10rpx;font-size:18rpx}.refinement-panel .dark-button{height:64rpx;margin:0;font-size:17rpx}
.food-direction-note{display:block;margin-top:14rpx;padding:12rpx;border-left:4rpx solid #b37b4d;border-radius:0 10rpx 10rpx 0;background:#fbf2e5;color:#795b42;font-size:16rpx;line-height:1.55}
.policy-mask{position:fixed;z-index:20;inset:0;display:flex;align-items:center;justify-content:center;padding:38rpx;background:rgba(24,29,26,.58);box-sizing:border-box}.policy-dialog{width:100%;max-height:80vh;overflow:hidden;border-radius:18rpx;background:#fffdfa;box-shadow:0 20rpx 50rpx rgba(25,31,27,.3)}.policy-dialog-head{display:flex;align-items:center;justify-content:space-between;padding:22rpx 22rpx 13rpx;border-bottom:1rpx solid #ece4d9}.policy-dialog-head text:first-child{color:#3d3831;font-size:24rpx;font-weight:850}.policy-dialog-head text:last-child{color:#a36e57;font-size:14rpx}.policy-dialog-title{display:block;padding:18rpx 22rpx 7rpx;color:#332e29;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:850}.policy-dialog-copy{box-sizing:border-box;width:100%;height:270rpx;padding:0 22rpx 18rpx}.policy-dialog-copy text{color:#6f665c;font-size:17rpx;line-height:1.7}.policy-dialog-actions{display:flex;gap:10rpx;padding:14rpx 22rpx calc(18rpx + env(safe-area-inset-bottom));border-top:1rpx solid #eee7de;background:#fffdfa}.policy-dialog-actions button{flex:1;height:78rpx;margin:0;border-radius:10rpx;font-size:18rpx;font-weight:850}.policy-dialog-actions button::after{border:0}.policy-cancel{border:1rpx solid #ded5c9;background:#f7f3ed;color:#827568}.policy-confirm{background:#3f3933;color:#fff}
.topbar-actions{display:flex;align-items:center;gap:10rpx}.previous-button{height:46rpx;margin:0;padding:0 12rpx;border:1rpx solid #bfd0c1;border-radius:9rpx;background:#f3f8f3;color:#527463;font-size:14rpx;line-height:46rpx}.previous-button::after{border:0}.previous-button[disabled],.bottom-actions button[disabled]{opacity:.55}
.chat-experience .choice-panel,.chat-experience .input-panel{display:none}.chat-command-panel{margin:18rpx 0 24rpx;padding:15rpx;border:1rpx solid #dfd5c9;border-radius:18rpx;background:rgba(255,253,249,.94);box-shadow:0 8rpx 20rpx rgba(79,60,41,.05)}.chat-stage-label{display:block;color:#837568;font-size:15rpx;line-height:1.4}.quick-reply-list{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:12rpx}.quick-reply{height:62rpx;margin:0;padding:0 14rpx;border:1rpx solid #a9c1ad;border-radius:13rpx;background:#eff6ef;color:#4f705e;font-size:16rpx;line-height:62rpx}.quick-reply::after{border:0}.quick-reply[disabled]{opacity:.5}.chat-input-row{display:flex;align-items:center;gap:8rpx;margin-top:12rpx}.chat-upload-button,.chat-send-button{flex:0 0 auto;height:66rpx;margin:0;border-radius:12rpx;font-size:17rpx;line-height:66rpx}.chat-upload-button{width:66rpx;padding:0;border:1rpx solid #d5c9bc;background:#faf6ef;color:#806f61;font-size:30rpx}.chat-send-button{padding:0 15rpx;background:#3f3933;color:#fff}.chat-input{flex:1;box-sizing:border-box;height:66rpx;padding:0 13rpx;border:1rpx solid #d9cec1;border-radius:12rpx;background:#fbf9f5;color:#443b33;font-size:18rpx}.chat-send-button::after,.chat-upload-button::after{border:0}.chat-send-button[disabled]{opacity:.45}
.thinking-row{display:flex;align-items:flex-start;gap:9rpx;margin:17rpx 0 18rpx;animation:thinking-enter .24s ease-out}.thinking-avatar{animation:thinking-breathe 1.8s ease-in-out infinite;box-shadow:0 0 0 6rpx rgba(94,124,109,.08)}.thinking-bubble{max-width:78%;padding:13rpx 16rpx;border:1rpx solid #d4e0d5;border-radius:17rpx 17rpx 17rpx 7rpx;background:#f8fcf8;box-shadow:0 7rpx 17rpx rgba(73,102,81,.07)}.thinking-title-row{display:flex;align-items:center;gap:9rpx}.thinking-title{color:#4d705c;font-size:19rpx;font-weight:850}.thinking-detail{display:block;margin-top:5rpx;color:#8a9b8d;font-size:15rpx;line-height:1.4}.thinking-dots{display:flex;align-items:center;gap:4rpx;height:22rpx}.thinking-dot{width:7rpx;height:7rpx;border-radius:50%;background:#6e967c;animation:thinking-dot-bounce 1.25s ease-in-out infinite}.thinking-dot:nth-child(2){animation-delay:.16s}.thinking-dot:nth-child(3){animation-delay:.32s}.quick-reply[disabled],.chat-upload-button[disabled],.chat-send-button[disabled]{opacity:.72;filter:none}.quick-reply[disabled]{border-color:#c4d5c6;background:#f1f7f1;color:#779180}.chat-upload-button[disabled]{border-color:#d9d5cc;background:#f6f4ef;color:#998f83}.chat-send-button[disabled]{background:#7b877f;color:#fff}.dark-button[disabled]{opacity:.72;background:#68746d;color:#fff}.loading-bar{display:flex;align-items:center;justify-content:center;gap:9rpx}.loading-spinner{width:22rpx;height:22rpx;border:3rpx solid #e8d8c7;border-top-color:#ad7e5d;border-radius:50%;animation:loading-spin .8s linear infinite}@keyframes thinking-enter{from{opacity:0;transform:translateY(8rpx)}to{opacity:1;transform:translateY(0)}}@keyframes thinking-breathe{0%,100%{transform:translateY(0);box-shadow:0 0 0 6rpx rgba(94,124,109,.08)}50%{transform:translateY(-2rpx);box-shadow:0 0 0 10rpx rgba(94,124,109,.03)}}@keyframes thinking-dot-bounce{0%,60%,100%{opacity:.35;transform:translateY(0) scale(.85)}30%{opacity:1;transform:translateY(-4rpx) scale(1)}}@keyframes loading-spin{to{transform:rotate(360deg)}}
</style>

<style scoped lang="scss">
/* The conversation page is a focused workspace: the transcript stays clear,
 * while the composer and project state remain available at the edges. */
.page.chat-experience {
  --ink: #26332d;
  --ink-soft: #738079;
  --line: #e2e8e3;
  --paper: #f5f7f5;
  --surface: #ffffff;
  --green: #3f6958;
  --green-soft: #e9f2ec;
  --orange: #c76f53;
  --orange-soft: #fff0e9;
  min-height: 100vh;
  box-sizing: border-box;
  padding-bottom: calc(118rpx + env(safe-area-inset-bottom));
  background: var(--paper);
  color: var(--ink);
}

.workspace-intro-top,
.output-header,
.output-info,
.composer-context,
.composer-footer,
.refinement-heading,
.progress-row {
  display: flex;
  align-items: center;
}

.surface-kicker {
  display: block;
  color: #84938b;
  font-size: 11rpx;
  font-weight: 900;
  letter-spacing: 1.8rpx;
}

.chat {
  height: 100vh;
  box-sizing: border-box;
  padding: 24rpx 28rpx calc(332rpx + env(safe-area-inset-bottom));
}
.workspace-intro { margin: 6rpx 0 18rpx; }
.workspace-intro-top { justify-content: space-between; gap: 10rpx; }
.online-mark { display: flex; align-items: center; gap: 7rpx; color: var(--green); font-size: 13rpx; font-weight: 850; }
.online-dot { width: 10rpx; height: 10rpx; border-radius: 50%; background: #6faa82; box-shadow: 0 0 0 5rpx rgba(111, 170, 130, .12); }
.workspace-ref { overflow: hidden; color: #a0aaa4; font-size: 12rpx; text-overflow: ellipsis; white-space: nowrap; }
.workspace-title { display: block; margin-top: 13rpx; color: var(--ink); font-family: "Songti SC", "STSong", serif; font-size: 35rpx; font-weight: 800; line-height: 1.25; }
.workspace-subtitle { display: block; max-width: 630rpx; margin-top: 7rpx; color: var(--ink-soft); font-size: 15rpx; line-height: 1.5; }
.brief-strip { display: flex; flex-wrap: wrap; gap: 7rpx; margin-top: 13rpx; }
.brief-chip { display: inline-flex; align-items: center; gap: 6rpx; padding: 6rpx 9rpx; border: 1rpx solid #cdded2; border-radius: 8rpx; background: #edf5ef; color: var(--green); font-size: 12rpx; }
.brief-chip text:first-child { color: #8ca296; }
.brief-chip.muted { border-color: #e2e7e3; background: #fff; color: #84918a; }
.campaign-strip { display: flex; align-items: flex-start; justify-content: space-between; gap: 10rpx; margin-top: 13rpx; padding: 10rpx 11rpx; border: 1rpx solid #c7dccb; border-radius: 11rpx; background: #f2f8f2; }
.campaign-strip>view { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 3rpx; }
.campaign-strip>view text:first-child { color: #66806e; font-size: 11rpx; font-weight: 900; letter-spacing: 1.2rpx; }
.campaign-strip>view text:nth-child(2) { overflow: hidden; color: #3e5949; font-size: 15rpx; font-weight: 850; text-overflow: ellipsis; white-space: nowrap; }
.campaign-strip>view text:last-child { overflow: hidden; color: #789081; font-size: 11rpx; text-overflow: ellipsis; white-space: nowrap; }
.campaign-strip>text { flex: 0 0 auto; padding: 5rpx 6rpx; border-radius: 7rpx; background: #dcecdf; color: #4e745c; font-size: 11rpx; font-weight: 850; white-space: nowrap; }
.ai-disclosure { margin: 0 0 22rpx; }

.message-row { display: flex; align-items: flex-start; gap: 10rpx; margin: 20rpx 0; }
.message-row.user { justify-content: flex-end; }
.message-avatar { display: grid; place-items: center; flex: 0 0 46rpx; width: 46rpx; height: 46rpx; border-radius: 14rpx; font-family: "Songti SC", "STSong", serif; font-size: 22rpx; font-weight: 850; }
.assistant-avatar { background: var(--green); color: #fff; box-shadow: 0 5rpx 12rpx rgba(54, 93, 74, .18); }
.user-avatar { background: #f7e5dc; color: #a45d48; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; font-size: 16rpx; }
.message-content { display: flex; min-width: 0; max-width: 82%; flex-direction: column; align-items: flex-start; }
.user .message-content { align-items: flex-end; }
.message-meta { display: flex; align-items: center; gap: 8rpx; margin: 0 4rpx 8rpx; color: #849089; font-size: 19rpx; }
.message-meta text:last-child { color: #a5afa9; }
.bubble { max-width: 100%; box-sizing: border-box; padding: 18rpx 20rpx; border: 1rpx solid #e1e8e2; border-radius: 7rpx 16rpx 16rpx 16rpx; background: var(--surface); box-shadow: 0 6rpx 17rpx rgba(51, 72, 60, .045); }
.bubble text { color: #3f4d45; font-size: 28rpx; line-height: 1.65; }
.user .bubble { border-color: #c3d5c8; border-radius: 16rpx 7rpx 16rpx 16rpx; background: #e8f2eb; }
.user .bubble text { color: #436052; }
.image-bubble { width: 100%; max-width: 540rpx; padding: 10rpx; }
.message-image, .message-image-loading { display: block; width: 100%; height: 360rpx; overflow: hidden; border-radius: 10rpx; background: #dfe9e1; }
.message-image-loading { display: flex; align-items: center; justify-content: center; color: #759080; font-size: 20rpx; }
.message-image-footer { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; padding: 9rpx 3rpx 1rpx; }
.message-image-footer text:first-child { overflow: hidden; color: #6f8176; font-size: 18rpx; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.message-image-footer text:first-child.failed { color: #b46350; }
.message-image-reselect { flex: 0 0 auto; color: #a16f59 !important; font-size: 18rpx !important; font-weight: 800; }
.message-actions { display: flex; justify-content: flex-end; margin-top: 8rpx; }
.message-copy { padding: 3rpx 2rpx; color: #789184 !important; font-size: 18rpx !important; line-height: 1.3 !important; }
.message-copy:active { color: #4f7561 !important; opacity: .72; }

.thinking-row { display: flex; align-items: flex-start; gap: 10rpx; margin: 20rpx 0; animation: thinking-enter .24s ease-out; }
.thinking-content { display: flex; min-width: 0; max-width: 82%; flex-direction: column; }
.thinking-bubble { padding: 13rpx 16rpx; border: 1rpx solid #d7e5da; border-radius: 7rpx 16rpx 16rpx 16rpx; background: #f9fcf9; box-shadow: 0 7rpx 17rpx rgba(62, 103, 76, .07); }
.thinking-title-row { display: flex; align-items: center; gap: 10rpx; }
.thinking-title { color: var(--green); font-size: 25rpx; font-weight: 850; }
.thinking-detail { display: block; margin-top: 7rpx; color: #84958b; font-size: 22rpx; line-height: 1.5; }
.thinking-dots { display: flex; align-items: center; gap: 4rpx; height: 22rpx; }
.thinking-dot { width: 7rpx; height: 7rpx; border-radius: 50%; background: #78a58a; animation: thinking-dot-bounce 1.25s ease-in-out infinite; }
.thinking-dot:nth-child(2) { animation-delay: .16s; }
.thinking-dot:nth-child(3) { animation-delay: .32s; }

.output-surface { margin: 24rpx 0 20rpx; padding: 18rpx; border: 1rpx solid #dce6df; border-radius: 18rpx; background: #fff; box-shadow: 0 12rpx 28rpx rgba(42, 67, 53, .07); }
.output-header { justify-content: space-between; gap: 12rpx; }
.surface-title { display: block; margin-top: 5rpx; color: var(--ink); font-size: 25rpx; font-weight: 850; }
.output-status { display: flex; align-items: center; gap: 5rpx; color: #6f8d7b; font-size: 12rpx; }
.status-check { display: grid; place-items: center; width: 25rpx; height: 25rpx; border-radius: 50%; background: #e6f1e9; color: var(--green); font-size: 15rpx; font-weight: 900; }
.visual-frame { position: relative; overflow: hidden; margin-top: 16rpx; border-radius: 13rpx; background: #edf0ed; }
.result-image { display: block; width: 100%; height: 420rpx; background: #edf0ed; }
.visual-badge { position: absolute; top: 12rpx; left: 12rpx; padding: 5rpx 8rpx; border: 1rpx solid rgba(255, 255, 255, .7); border-radius: 6rpx; background: rgba(35, 53, 43, .7); color: #fff; font-size: 11rpx; font-weight: 800; }
.result-placeholder { display: flex; align-items: center; justify-content: center; height: 420rpx; flex-direction: column; gap: 8rpx; background: #e9f0eb; color: #5c7a68; }
.result-placeholder text:first-child { font-family: "Songti SC", "STSong", serif; font-size: 58rpx; }
.result-placeholder text:last-child { font-size: 14rpx; }
.output-info { justify-content: space-between; gap: 10rpx; padding: 13rpx 2rpx 3rpx; }
.output-info view { display: flex; min-width: 0; flex-direction: column; gap: 4rpx; }
.output-info view text:first-child { overflow: hidden; color: var(--ink); font-size: 18rpx; font-weight: 850; text-overflow: ellipsis; white-space: nowrap; }
.output-info view text:last-child { color: #8b9890; font-size: 13rpx; }
.output-open { flex: 0 0 auto; color: var(--orange); font-size: 13rpx; font-weight: 800; }
.output-actions { display: grid; gap: 8rpx; margin-top: 13rpx; }
.output-action { display: grid; grid-template-columns: 44rpx minmax(0, 1fr) 18rpx; align-items: center; gap: 10rpx; min-height: 66rpx; padding: 10rpx 11rpx; border: 1rpx solid #e2e9e3; border-radius: 12rpx; background: #fbfcfb; }
.output-action.primary { border-color: #b7d0be; background: #f0f7f1; }
.output-action:active { background: #edf3ee; }
.action-icon { display: grid; place-items: center; width: 42rpx; height: 42rpx; border-radius: 12rpx; background: #dcebe0; color: var(--green); font-family: "Songti SC", "STSong", serif; font-size: 21rpx; font-weight: 850; }
.action-icon.warm { background: var(--orange-soft); color: var(--orange); }
.action-icon.dark { background: #e9ecea; color: #44534b; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; font-size: 14rpx; }
.action-icon.gold { background: #f9f0dd; color: #a17a3e; }
.output-action view:nth-child(2) { display: flex; min-width: 0; flex-direction: column; gap: 3rpx; }
.output-action view:nth-child(2) text:first-child { color: #3d4c43; font-size: 17rpx; font-weight: 850; }
.output-action view:nth-child(2) text:last-child { color: #8b9890; font-size: 13rpx; }
.action-arrow { color: #a8b3ac; font-size: 28rpx; }
.surface-note { display: block; margin-top: 10rpx; color: #7f8d84; font-size: 14rpx; line-height: 1.5; }

.view-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 9rpx; margin-top: 15rpx; }
.view-card { overflow: hidden; border: 1rpx solid #e0e8e1; border-radius: 12rpx; background: #fbfcfb; }
.view-card image, .view-placeholder { display: block; width: 100%; height: 184rpx; background: #edf1ed; }
.view-placeholder { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 5rpx; color: #829189; font-size: 13rpx; }
.view-label { display: flex; align-items: center; justify-content: space-between; gap: 5rpx; padding: 8rpx 9rpx; }
.view-label text:first-child { color: #4d5e53; font-size: 14rpx; font-weight: 850; }
.view-label text:last-child { color: #8aa493; font-size: 11rpx; }
.full-button { width: 100%; }
.dark-button, .outline-button { height: 68rpx; margin-top: 12rpx; border-radius: 11rpx; font-size: 17rpx; font-weight: 850; line-height: 68rpx; }
.dark-button { background: #354b40; color: #fff; }
.outline-button { border: 1rpx solid #b9cec0; background: #f8fbf8; color: #557464; }
.dark-button::after, .outline-button::after, .link-button::after { border: 0; }
.dark-button[disabled] { opacity: .55; }
.dark-button text { margin-left: 5rpx; font-size: 25rpx; line-height: 1; }

.model-summary { display: flex; align-items: center; gap: 13rpx; margin-top: 17rpx; padding: 14rpx; border-radius: 13rpx; background: #edf5ef; }
.model-mark { display: grid; place-items: center; flex: 0 0 62rpx; width: 62rpx; height: 62rpx; border-radius: 17rpx; background: var(--green); color: #fff; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; font-size: 18rpx; font-weight: 900; }
.model-summary view:last-child { display: flex; min-width: 0; flex-direction: column; gap: 5rpx; }
.model-summary view:last-child text:first-child { color: #4d6b59; font-size: 16rpx; font-weight: 850; }
.model-summary view:last-child text:last-child { color: #83958a; font-size: 13rpx; line-height: 1.4; }
.model-state { padding: 5rpx 8rpx; border-radius: 7rpx; background: #edf2ed; color: #6a8273; font-size: 12rpx; font-weight: 850; }
.model-state.done { background: #e5f2e8; color: #4f8463; }
.model-state.failed { background: #fff0ec; color: #ad5d4a; }
.model-progress { margin-top: 13rpx; padding: 13rpx; border: 1rpx solid #e0e9e1; border-radius: 12rpx; background: #fbfdfb; }
.progress-row { justify-content: space-between; color: #587161; font-size: 13rpx; font-weight: 850; }
.model-progress-track { height: 9rpx; margin-top: 10rpx; overflow: hidden; border-radius: 99rpx; background: #e1ebe3; }
.model-progress-value { height: 100%; border-radius: inherit; background: #67947a; transition: width .35s ease; }
.model-error { display: block; margin-top: 11rpx; padding: 10rpx; border-radius: 10rpx; background: #fff0ec; color: #a75948; font-size: 13rpx; line-height: 1.45; }

.bundle-review-state { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; margin-top: 14rpx; padding: 12rpx 13rpx; border: 1rpx solid #dfe7e1; border-radius: 11rpx; background: #f8faf8; }
.bundle-review-state>view { display: flex; min-width: 0; flex-direction: column; gap: 4rpx; }
.bundle-review-label { color: #929e97; font-size: 11rpx; }
.bundle-review-title { color: #55665c; font-size: 16rpx; font-weight: 850; }
.bundle-review-no { overflow: hidden; max-width: 190rpx; color: #9ba59f; font-size: 10rpx; text-overflow: ellipsis; white-space: nowrap; }
.bundle-review-state.bundle-review { border-color: #ead9ad; background: #fffaf0; }
.bundle-review-state.bundle-approved { border-color: #bbd8c3; background: #eef7f0; }
.bundle-review-state.bundle-approved .bundle-review-title { color: #47745a; }
.bundle-review-state.bundle-rejected { border-color: #edc8bd; background: #fff3ef; }
.bundle-review-state.bundle-rejected .bundle-review-title { color: #a65d49; }
.bundle-review-comment { display: block; margin-top: 9rpx; padding: 10rpx 11rpx; border-left: 4rpx solid #bd6d55; border-radius: 0 9rpx 9rpx 0; background: #fff4f0; color: #965542; font-size: 13rpx; line-height: 1.55; }

.refinement-panel { margin-top: 14rpx; padding: 14rpx; border: 1rpx solid #ead7ce; border-radius: 13rpx; background: #fff9f6; }
.refinement-heading { justify-content: space-between; gap: 10rpx; }
.refinement-heading view { display: flex; flex-direction: column; gap: 5rpx; }
.refinement-heading view text:last-child { color: #684e45; font-size: 17rpx; font-weight: 850; }
.refinement-close { color: #aa806e; font-size: 28rpx; }
.text-input { width: 100%; min-height: 132rpx; box-sizing: border-box; margin-top: 12rpx; padding: 12rpx; border: 1rpx solid #e5d4cc; border-radius: 10rpx; background: #fff; color: #493f3b; font-size: 17rpx; line-height: 1.55; }
.input-foot { display: flex; align-items: center; justify-content: space-between; margin-top: 9rpx; color: #a08f86; font-size: 12rpx; }
.refinement-panel .dark-button { height: 58rpx; margin: 0; padding: 0 13rpx; font-size: 15rpx; line-height: 58rpx; }

.composer-dock { position: fixed; z-index: 25; right: 0; bottom: calc(88rpx + env(safe-area-inset-bottom)); left: 0; box-sizing: border-box; padding: 13rpx 22rpx 9rpx; border-top: 1rpx solid #dfe7e1; background: rgba(255, 255, 255, .97); box-shadow: 0 -10rpx 24rpx rgba(44, 62, 51, .07); backdrop-filter: blur(18rpx); }
.composer-context { min-width: 0; gap: 7rpx; color: #64776c; font-size: 20rpx; }
.context-live { width: 9rpx; height: 9rpx; border-radius: 50%; background: #70a481; }
.context-product { overflow: hidden; max-width: 260rpx; color: #96a29b; text-overflow: ellipsis; white-space: nowrap; }
.context-working { margin-left: auto; color: var(--orange); font-size: 19rpx; }
.quick-reply-list { width: 100%; margin-top: 10rpx; white-space: nowrap; }
.quick-reply-track { display: flex; gap: 8rpx; }
.quick-reply { display: inline-flex; align-items: center; gap: 7rpx; flex: 0 0 auto; min-height: 64rpx; padding: 0 15rpx; border: 1rpx solid #d8e5db; border-radius: 10rpx; background: #f6faf7; color: #456655; font-size: 24rpx; line-height: 1.35; }
.quick-reply.confirm { border-color: #9fc3a9; background: #eaf5ed; color: #3f7052; font-weight: 850; }
.quick-reply.secondary { border-color: #e6d5ca; background: #fff9f5; color: #9b6b57; }
.quick-reply.disabled { opacity: .55; }
.quick-reply:active { opacity: .75; }
.quick-reply-mark { display: inline-grid; place-items: center; flex: 0 0 32rpx; width: 32rpx; height: 32rpx; border-radius: 7rpx; background: #dcebe0; color: #4e7860; font-size: 18rpx; font-weight: 900; line-height: 32rpx; }
.quick-reply.confirm .quick-reply-mark { background: #4f8563; color: #fff; }
.quick-reply.secondary .quick-reply-mark { background: #f3dfd3; color: #a66751; }
.chat-input-row { display: flex; align-items: center; gap: 8rpx; margin-top: 10rpx; }
.chat-upload-button, .chat-send-button { flex: 0 0 auto; height: 76rpx; margin: 0; border-radius: 12rpx; line-height: 76rpx; }
.chat-upload-button { width: 76rpx; padding: 0; border: 1rpx solid #d9e2db; background: #f8faf8; color: #658073; font-size: 34rpx; }
.chat-send-button { width: 76rpx; padding: 0; background: #dfe7e1; color: #91a099; font-size: 32rpx; font-weight: 900; }
.chat-send-button.ready { background: var(--green); color: #fff; }
.chat-input { flex: 1; min-width: 0; height: 76rpx; box-sizing: border-box; padding: 0 17rpx; border: 1rpx solid #d9e2db; border-radius: 12rpx; background: #f8faf8; color: #33463b; font-size: 26rpx; }
.chat-input:focus { border-color: #9cbea7; background: #fff; }
.chat-send-button::after, .chat-upload-button::after { border: 0; }
.chat-send-button[disabled], .chat-upload-button[disabled] { opacity: .65; }
.composer-footer { justify-content: space-between; gap: 8rpx; margin-top: 7rpx; color: #a2ada6; font-size: 10rpx; }
.composer-footer text:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.loading-bar { position: fixed; z-index: 27; right: 22rpx; bottom: calc(238rpx + env(safe-area-inset-bottom)); left: 22rpx; display: flex; align-items: center; justify-content: center; gap: 9rpx; box-sizing: border-box; min-height: 52rpx; padding: 8rpx 13rpx; border: 1rpx solid #efd6c8; border-radius: 11rpx; background: #fff8f4; color: #9e6b58; font-size: 12rpx; box-shadow: 0 8rpx 19rpx rgba(111, 71, 54, .1); }
.loading-bar view:last-child { display: flex; min-width: 0; flex-direction: column; gap: 2rpx; }
.loading-title { color: #875541; font-size: 13rpx; font-weight: 850; }
.loading-spinner { width: 19rpx; height: 19rpx; border: 3rpx solid #f1dcd2; border-top-color: var(--orange); border-radius: 50%; animation: loading-spin .8s linear infinite; }

.bottom-actions { position: fixed; z-index: 28; right: 0; bottom: 0; left: 0; display: flex; align-items: center; justify-content: space-around; box-sizing: border-box; min-height: 88rpx; padding: 9rpx 24rpx calc(9rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #dfe7e1; background: rgba(248, 250, 248, .98); backdrop-filter: blur(18rpx); }
.bottom-actions button { display: flex; align-items: center; justify-content: center; gap: 5rpx; min-width: 132rpx; height: 52rpx; margin: 0; padding: 0 11rpx; border: 1rpx solid transparent; border-radius: 10rpx; background: transparent; color: #74827a; font-size: 14rpx; line-height: 52rpx; }
.bottom-actions button text { font-size: 21rpx; line-height: 1; }
.bottom-actions button::after { border: 0; }
.bottom-actions button:active { background: #edf3ee; }
.bottom-actions button[disabled] { opacity: .45; }
.bottom-actions .restart-action { border-color: #ead8ce; color: #a16b56; background: #fffaf7; }

.policy-mask { position: fixed; z-index: 50; inset: 0; display: flex; align-items: center; justify-content: center; padding: 32rpx; box-sizing: border-box; background: rgba(31, 44, 36, .62); }
.policy-dialog { width: 100%; max-height: 80vh; overflow: hidden; border: 1rpx solid #dce6df; border-radius: 17rpx; background: #fff; box-shadow: 0 22rpx 55rpx rgba(22, 38, 29, .3); }
.policy-dialog-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10rpx; padding: 18rpx 20rpx 14rpx; border-bottom: 1rpx solid #e8eee9; }
.policy-dialog-head view { display: flex; flex-direction: column; gap: 5rpx; }
.policy-dialog-head view text:last-child { color: var(--ink); font-size: 22rpx; font-weight: 850; }
.policy-dialog-head>text { color: var(--orange); font-size: 12rpx; }
.policy-dialog-title { display: block; padding: 16rpx 20rpx 7rpx; color: var(--ink); font-size: 25rpx; font-weight: 850; }
.policy-dialog-copy { width: 100%; height: 270rpx; box-sizing: border-box; padding: 0 20rpx 16rpx; }
.policy-dialog-copy text { color: #69776f; font-size: 15rpx; line-height: 1.7; }
.policy-dialog-actions { display: flex; gap: 9rpx; padding: 13rpx 20rpx calc(16rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #e8eee9; background: #fbfcfb; }
.policy-dialog-actions button { flex: 1; height: 66rpx; margin: 0; border-radius: 10rpx; font-size: 16rpx; font-weight: 850; line-height: 66rpx; }
.policy-dialog-actions button::after { border: 0; }
.policy-cancel { border: 1rpx solid #dce5de; background: #fff; color: #7c8982; }
.policy-confirm { background: #354b40; color: #fff; }

@keyframes thinking-enter { from { opacity: 0; transform: translateY(8rpx); } to { opacity: 1; transform: translateY(0); } }
@keyframes thinking-dot-bounce { 0%, 60%, 100% { opacity: .35; transform: translateY(0) scale(.85); } 30% { opacity: 1; transform: translateY(-4rpx) scale(1); } }
@keyframes loading-spin { to { transform: rotate(360deg); } }
</style>
