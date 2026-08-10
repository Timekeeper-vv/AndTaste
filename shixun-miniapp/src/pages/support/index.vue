<template>
  <view class="page">
    <view class="wash wash-top" />
    <view class="wash wash-bottom" />

    <view class="hero">
      <view class="hero-mark"><text>间</text></view>
      <view class="hero-copy">
        <text class="hero-eyebrow">CARE · RIGHTS · CREATION</text>
        <text class="hero-title">创作路上，<text>我们一直在。</text></text>
        <text class="hero-desc">客服会话与版权咨询均会同步到平台服务团队。</text>
      </view>
    </view>

    <view class="tabs" role="tablist">
      <view class="tab-item" :class="{ active: activeTab === 'chat' }" role="tab" @tap="switchTab('chat')">
        <text class="tab-symbol">问</text>
        <view><text>在线客服</text><text>消息与进度</text></view>
      </view>
      <view class="tab-item" :class="{ active: activeTab === 'rights' }" role="tab" @tap="switchTab('rights')">
        <text class="tab-symbol">权</text>
        <view><text>版权咨询</text><text>登记与合规</text></view>
      </view>
    </view>

    <view v-if="activeTab === 'chat'" class="content-panel chat-content">
      <view class="panel-intro">
        <view>
          <text class="panel-kicker">SERVICE DESK</text>
          <text class="panel-title">和客服聊聊你的创作</text>
          <text class="panel-desc">可咨询创作、积分、审核、生产、博物馆合作及版权服务。</text>
        </view>
        <view class="online-chip" :class="{ human: hasHumanTakeover }">
          <text class="status-dot" />
          <text>{{ hasHumanTakeover ? '人工已接入' : 'AI 首响在线' }}</text>
        </view>
      </view>
      <AiGeneratedNotice class="ai-disclosure" compact description="标有“AI生成”的客服回复由人工智能生成，仅供参考。付款、版权、授权、生产交期和合作结论以人工复核及正式协议为准。" />

      <view class="conversation-meta" v-if="conversation">
        <text>{{ hasHumanTakeover ? humanTakeoverText : '你的问题会保留在本次服务会话中' }}</text>
        <text v-if="conversation.updatedAt">更新于 {{ formatDate(conversation.updatedAt) }}</text>
      </view>

      <view v-if="chatError" class="error-card">
        <view><text class="error-title">服务连接遇到一点问题</text><text>{{ chatError }}</text></view>
        <button class="error-action" @tap="handleChatErrorAction">{{ isChatAuthError ? '重新登录' : '重试' }}</button>
      </view>

      <view class="chat-shell">
        <scroll-view class="messages" scroll-y :scroll-into-view="scrollTarget" :scroll-with-animation="true">
          <view v-if="chatLoading && !messages.length" class="loading-state">
            <view class="seal-loader"><text>印</text></view>
            <text>正在连接服务台…</text>
          </view>
          <view v-else-if="!messages.length" class="empty-state">
            <view class="empty-seal">问</view>
            <text>还没有服务记录</text>
            <text>点击下方主题或直接留言，即可建立真实服务会话。</text>
          </view>
          <view
            v-for="message in messages"
            :id="`message-${message.id}`"
            :key="message.id"
            class="message-row"
            :class="message.senderType === 'user' ? 'mine' : 'service'"
          >
            <view v-if="message.senderType !== 'user'" class="message-avatar" :class="message.senderType">
              {{ message.senderType === 'staff' ? '人' : 'AI' }}
            </view>
            <view class="bubble-wrap">
              <view class="message-bubble"><text>{{ message.content }}</text></view>
              <view class="message-detail">
                <text>{{ senderLabel(message.senderType, message.senderName) }}</text>
                <text v-if="message.senderType === 'assistant'" class="ai-message-label">AI生成</text>
                <text v-if="message.createdAt">{{ formatMessageTime(message.createdAt) }}</text>
              </view>
            </view>
          </view>
        </scroll-view>

        <view v-if="!hasHumanTakeover" class="quick-topics">
          <text class="quick-label">常见问题</text>
          <scroll-view class="quick-scroll" scroll-x :show-scrollbar="false">
            <view class="quick-list">
              <button v-for="topic in quickTopics" :key="topic" class="quick-item" :disabled="sending" @tap="sendQuick(topic)">{{ topic }}</button>
            </view>
          </scroll-view>
        </view>

        <view class="composer">
          <textarea
            v-model="messageInput"
            class="message-input"
            :maxlength="500"
            :auto-height="true"
            confirm-type="send"
            placeholder="描述你的问题，AI 与人工客服都会看到"
            @confirm="submitInput"
          />
          <button class="send-button" :disabled="sending || !messageInput.trim()" @tap="submitInput">
            {{ sending ? '发送中' : '发送' }}
          </button>
        </view>
      </view>

      <view class="service-note">
        <text class="note-symbol">※</text>
        <text>涉及付款、版权、授权、生产交期或博物馆合作的结论，请以人工复核及正式协议为准。</text>
      </view>
    </view>

    <view v-else class="content-panel rights-content">
      <view class="panel-intro rights-intro">
        <view>
          <text class="panel-kicker">RIGHTS &amp; COMPLIANCE</text>
          <text class="panel-title">为原创留下清晰凭据</text>
          <text class="panel-desc">选择服务与关联作品后提交，平台人员会按协议核对材料。</text>
        </view>
        <view class="rights-seal">权</view>
      </view>

      <view v-if="rightsSuccess" class="success-card">
        <text class="success-mark">✓</text>
        <view><text>咨询已提交</text><text>{{ rightsSuccess }}</text></view>
      </view>
      <view v-if="rightsError" class="error-card rights-error">
        <view><text class="error-title">暂时无法提交咨询</text><text>{{ rightsError }}</text></view>
        <button class="error-action" @tap="handleRightsErrorAction">{{ isRightsAuthError ? '重新登录' : '重试' }}</button>
      </view>

      <view class="form-section">
        <view class="form-heading"><text class="step-no">01</text><view><text>选择咨询方向</text><text>请选择你希望平台协助确认的事项</text></view></view>
        <view class="service-grid">
          <view
            v-for="item in copyrightServices"
            :key="item.value"
            class="service-option"
            :class="{ selected: selectedService === item.value }"
            @tap="selectedService = item.value"
          >
            <view class="service-option-icon">{{ item.mark }}</view>
            <view><text>{{ item.value }}</text><text>{{ item.desc }}</text></view>
            <text class="selection-mark">{{ selectedService === item.value ? '✓' : '' }}</text>
          </view>
        </view>
      </view>

      <view class="form-section work-section">
        <view class="form-heading"><text class="step-no">02</text><view><text>关联一件作品 <text class="optional">可选</text></text><text>仅展示并允许选择你本人作品库中的真实作品</text></view></view>
        <view v-if="assetsLoading" class="asset-state"><text>正在读取你的作品库…</text></view>
        <view v-else-if="assetsError" class="asset-state asset-error"><text>{{ assetsError }}</text><button @tap="loadAssets">重新读取</button></view>
        <scroll-view v-else class="asset-scroll" scroll-x :show-scrollbar="false">
          <view class="asset-list">
            <view class="asset-option no-asset" :class="{ selected: selectedAssetId === null }" @tap="selectedAssetId = null">
              <view class="asset-mark">无</view>
              <view><text>暂不关联作品</text><text>先完成服务咨询</text></view>
            </view>
            <view
              v-for="asset in rightsAssets"
              :key="asset.id"
              class="asset-option"
              :class="{ selected: selectedAssetId === asset.id }"
              @tap="selectedAssetId = asset.id"
            >
              <view class="asset-mark" :class="asset.assetType === 'model' ? 'model' : 'image'">{{ asset.assetType === 'model' ? '3D' : '图' }}</view>
              <view><text>{{ assetTitle(asset) }}</text><text>{{ asset.assetType === 'model' ? '三维作品' : '二维作品' }}</text></view>
              <text v-if="selectedAssetId === asset.id" class="asset-selected">✓</text>
            </view>
            <view v-if="!rightsAssets.length" class="asset-empty"><text>作品库暂无可关联的作品</text><text>你仍可先提交通用版权服务咨询。</text></view>
          </view>
        </scroll-view>
      </view>

      <view class="form-section note-section">
        <view class="form-heading"><text class="step-no">03</text><view><text>补充说明 <text class="optional">可选</text></text><text>可写明作品来源、授权材料或希望咨询的问题</text></view></view>
        <textarea v-model="rightsNote" class="rights-note-input" :maxlength="1000" :auto-height="true" placeholder="例如：作品使用了自有品牌元素，想了解著作权登记需要准备哪些材料。" />
        <text class="char-count">{{ rightsNote.length }} / 1000</text>
      </view>

      <button class="rights-submit" :disabled="rightsSubmitting || !selectedService" @tap="submitRights">
        {{ rightsSubmitting ? '正在提交咨询…' : '提交版权咨询' }}
      </button>
      <view class="legal-note">
        <text class="note-symbol">※</text>
        <text>此入口为咨询登记，不等同于已完成著作权登记、专利申请、商标注册或获得第三方 IP 授权。涉及人物肖像、商标、品牌及第三方 IP 时，请先准备有效授权材料。</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { onHide, onLoad, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import AiGeneratedNotice from '../../components/AiGeneratedNotice.vue'
import {
  createCopyrightConsultation,
  getAssets,
  getMyCustomerServiceConversation,
  openCustomerServiceConversation,
  sendCustomerServiceMessage,
  type CustomerServiceConversation,
  type CustomerServiceMessage,
} from '../../api/creative'
import { clearSession, requireSession } from '../../utils/session'

type SupportTab = 'chat' | 'rights'
type AssetRecord = { id: number | string; assetType?: string; title?: string; name?: string; fileName?: string; prompt?: string }

const activeTab = ref<SupportTab>('chat')
const conversation = ref<CustomerServiceConversation | null>(null)
const messages = ref<CustomerServiceMessage[]>([])
const messageInput = ref('')
const scrollTarget = ref('')
const chatLoading = ref(false)
const sending = ref(false)
const chatError = ref('')
const assetsLoading = ref(false)
const assetsError = ref('')
const rightsAssets = ref<AssetRecord[]>([])
const selectedService = ref('')
const selectedAssetId = ref<number | string | null>(null)
const rightsNote = ref('')
const rightsSubmitting = ref(false)
const rightsError = ref('')
const rightsSuccess = ref('')
let refreshTimer: ReturnType<typeof setInterval> | null = null
let hasBootstrapped = false

const quickTopics = ['我的积分 / 充值问题', '审核 / 打样进度', '3D 材质与模型问题', '博物馆合作与售卖', '版权与确权咨询']
const copyrightServices = [
  { mark: '存', value: '暂不申请，仅保存创作证据', desc: '了解创作留痕与保存建议' },
  { mark: '著', value: '著作权登记咨询', desc: '咨询登记材料与办理流程' },
  { mark: '专', value: '外观设计专利咨询', desc: '咨询产品外观的申请方向' },
  { mark: '商', value: '商标 / IP 运营咨询', desc: '咨询商标与合作授权边界' },
]

const hasHumanTakeover = computed(() => Boolean(conversation.value?.humanTakeover))
const humanTakeoverText = computed(() => {
  const name = conversation.value?.takenByName
  return name ? `人工客服 ${name} 已接入，本次会话将由人工继续跟进。` : '人工客服已接入，本次会话将由人工继续跟进。'
})
const isChatAuthError = computed(() => isAuthOrPermissionError(chatError.value))
const isRightsAuthError = computed(() => isAuthOrPermissionError(rightsError.value))

function switchTab(tab: SupportTab) {
  activeTab.value = tab
  if (tab === 'rights') void loadAssets()
  else if (!conversation.value) void loadConversation(true)
}

function errorText(error: unknown, fallback: string) {
  const raw = error instanceof Error ? error.message : String(error || '')
  if (/登录已过期|请先登录|401/i.test(raw)) return '登录状态已失效，请重新登录后继续使用服务。'
  if (/C端用户|C 端用户|当前账号/i.test(raw)) return '当前账号不是小程序创作用户，请使用创作用户账号登录后再试。'
  if (/无权|权限/i.test(raw)) return '当前账号暂无访问此服务的权限。如切换过账号，请重新登录后再试。'
  if (/网络|request failed|network/i.test(raw)) return '网络连接不稳定，请确认网络正常后重新尝试。'
  return raw || fallback
}

function isAuthOrPermissionError(message: string) {
  return /登录|账号|权限|无权/.test(message)
}

function formatDate(value?: string) {
  if (!value) return ''
  const parts = String(value).replace('T', ' ').split(' ')[0]?.split('-') || []
  return parts.length === 3 ? `${Number(parts[1])}月${Number(parts[2])}日` : String(value)
}

function formatMessageTime(value?: string) {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  const time = text.match(/(\d{1,2}:\d{2})(?::\d{2})?/)?.[1]
  return time || formatDate(text)
}

function senderLabel(type: string, name?: string | null) {
  if (type === 'user') return '我'
  if (type === 'staff') return name || '人工客服'
  return name || 'AI 客服助手'
}

function assetTitle(asset: AssetRecord) {
  const title = asset.title || asset.name || asset.fileName || asset.prompt
  return title ? String(title).slice(0, 19) : `作品 #${asset.id}`
}

async function scrollToLatest() {
  await nextTick()
  const last = messages.value[messages.value.length - 1]
  scrollTarget.value = last ? `message-${last.id}` : ''
}

async function loadConversation(open = false) {
  if (chatLoading.value) return
  chatLoading.value = true
  chatError.value = ''
  try {
    const detail = open ? await openCustomerServiceConversation() : await getMyCustomerServiceConversation()
    conversation.value = detail.conversation || null
    messages.value = Array.isArray(detail.messages) ? detail.messages : []
    await scrollToLatest()
  } catch (error) {
    chatError.value = errorText(error, '暂时无法读取服务记录，请稍后重试。')
  } finally {
    chatLoading.value = false
  }
}

async function ensureConversation() {
  if (conversation.value) return true
  await loadConversation(true)
  return Boolean(conversation.value)
}

async function sendMessage(content: string) {
  const text = content.trim()
  if (!text || sending.value) return
  if (!(await ensureConversation()) || !conversation.value) {
    if (!chatError.value) chatError.value = '服务会话尚未建立，请稍后重试。'
    return
  }
  sending.value = true
  chatError.value = ''
  try {
    const detail = await sendCustomerServiceMessage(conversation.value.id, text)
    conversation.value = detail.conversation || conversation.value
    messages.value = Array.isArray(detail.messages) ? detail.messages : messages.value
    messageInput.value = ''
    await scrollToLatest()
  } catch (error) {
    chatError.value = errorText(error, '消息未能发送，请重试。')
  } finally {
    sending.value = false
  }
}

function submitInput() {
  void sendMessage(messageInput.value)
}

function sendQuick(topic: string) {
  void sendMessage(topic)
}

async function loadAssets() {
  if (assetsLoading.value) return
  assetsLoading.value = true
  assetsError.value = ''
  try {
    const assets = await getAssets()
    rightsAssets.value = Array.isArray(assets)
      ? assets.filter(item => item?.id && (item.assetType === 'image' || item.assetType === 'model'))
      : []
  } catch (error) {
    assetsError.value = errorText(error, '作品库暂时无法读取，请稍后重试。')
  } finally {
    assetsLoading.value = false
  }
}

async function submitRights() {
  if (!selectedService.value || rightsSubmitting.value) return
  rightsSubmitting.value = true
  rightsError.value = ''
  rightsSuccess.value = ''
  try {
    const result = await createCopyrightConsultation({
      service: selectedService.value,
      note: rightsNote.value.trim(),
      ...(selectedAssetId.value !== null ? { assetId: selectedAssetId.value } : {}),
    })
    rightsSuccess.value = result.message || '版权服务咨询已登记，平台人员将按协议与您核对材料。'
    rightsNote.value = ''
  } catch (error) {
    rightsError.value = errorText(error, '版权服务咨询登记失败，请稍后重试。')
  } finally {
    rightsSubmitting.value = false
  }
}

function goLogin() {
  clearSession()
  uni.reLaunch({ url: '/pages/login/index' })
}

function handleChatErrorAction() {
  if (isChatAuthError.value) goLogin()
  else void loadConversation(true)
}

function handleRightsErrorAction() {
  if (isRightsAuthError.value) goLogin()
  else void submitRights()
}

function startRefreshing() {
  if (refreshTimer) return
  refreshTimer = setInterval(() => {
    if (activeTab.value === 'chat' && !chatLoading.value && !sending.value) void loadConversation(false)
  }, 12000)
}

function stopRefreshing() {
  if (!refreshTimer) return
  clearInterval(refreshTimer)
  refreshTimer = null
}

async function bootstrap() {
  if (!requireSession()) return
  if (activeTab.value === 'rights') await loadAssets()
  else await loadConversation(true)
  hasBootstrapped = true
  startRefreshing()
}

onLoad(options => {
  const tab = String(options?.tab || '')
  if (tab === 'rights') activeTab.value = 'rights'
  void bootstrap()
})

onShow(() => {
  if (!hasBootstrapped) return
  startRefreshing()
  if (activeTab.value === 'chat') void loadConversation(false)
})

onHide(stopRefreshing)
onUnload(stopRefreshing)
onBeforeUnmount(stopRefreshing)

onPullDownRefresh(async () => {
  if (activeTab.value === 'chat') await loadConversation(false)
  else await loadAssets()
  uni.stopPullDownRefresh()
})
</script>

<style scoped lang="scss">
.ai-disclosure{margin:0 20rpx 12rpx}.ai-message-label{padding:3rpx 6rpx;border-radius:5rpx;background:#f4e6dc;color:#9a6048;font-size:14rpx;font-weight:900;line-height:1.2}
.page { position: relative; box-sizing: border-box; min-height: 100vh; overflow: hidden; padding: 38rpx 28rpx calc(54rpx + env(safe-area-inset-bottom)); background: linear-gradient(165deg, #fbfaf5 0%, #f4f0e7 64%, #edf1ea 100%); }
.wash { position: absolute; z-index: 0; border-radius: 999rpx; pointer-events: none; }
.wash-top { top: -145rpx; right: -124rpx; width: 530rpx; height: 370rpx; opacity: .75; background: radial-gradient(ellipse, rgba(109, 143, 125, .19), rgba(109, 143, 125, .04) 53%, transparent 71%); transform: rotate(-20deg); }
.wash-bottom { bottom: -194rpx; left: -150rpx; width: 570rpx; height: 390rpx; opacity: .65; background: radial-gradient(ellipse, rgba(178, 108, 79, .13), rgba(178, 108, 79, .025) 55%, transparent 72%); transform: rotate(18deg); }
.hero, .tabs, .content-panel { position: relative; z-index: 1; }
.hero { display: flex; align-items: center; gap: 20rpx; padding: 10rpx 8rpx 36rpx; }
.hero-mark { display: flex; align-items: center; justify-content: center; flex: none; width: 88rpx; height: 88rpx; border: 4rpx solid rgba(255, 252, 247, .9); border-radius: 27rpx 25rpx 32rpx 24rpx; color: #fffaf4; background: linear-gradient(145deg, #5f7d6e, #8ba596); box-shadow: 0 14rpx 25rpx rgba(54, 79, 64, .2), inset 0 0 0 1rpx rgba(255, 255, 255, .25); transform: rotate(-6deg); }
.hero-mark text { font-family: "Songti SC", "STSong", serif; font-size: 39rpx; font-weight: 800; }
.hero-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.hero-eyebrow, .panel-kicker { color: #779184; font-size: 18rpx; font-weight: 900; letter-spacing: 1.8rpx; }
.hero-title { margin-top: 6rpx; color: #2d322d; font-family: "Songti SC", "STSong", serif; font-size: 36rpx; font-weight: 800; line-height: 1.28; }
.hero-title text { color: #98634d; }
.hero-desc { margin-top: 8rpx; color: #80877f; font-size: 20rpx; }
.tabs { display: flex; gap: 12rpx; padding: 8rpx; border: 1rpx solid rgba(117, 111, 95, .11); border-radius: 26rpx; background: rgba(255, 254, 250, .72); box-shadow: 0 13rpx 28rpx rgba(68, 58, 44, .045); }
.tab-item { display: flex; align-items: center; flex: 1; gap: 12rpx; min-width: 0; padding: 16rpx 14rpx; border-radius: 19rpx; }
.tab-item:active { opacity: .76; }.tab-item.active { color: #fff; background: linear-gradient(135deg, #5d7e70, #759888); box-shadow: 0 10rpx 19rpx rgba(63, 105, 85, .2); }
.tab-symbol { display: flex; align-items: center; justify-content: center; flex: none; width: 43rpx; height: 43rpx; border-radius: 14rpx; color: #668171; background: #edf3ed; font-family: "Songti SC", "STSong", serif; font-size: 23rpx; font-weight: 800; }
.tab-item.active .tab-symbol { color: #fdfaf4; background: rgba(255, 255, 255, .18); }
.tab-item view { display: flex; min-width: 0; flex-direction: column; }.tab-item view text:first-child { font-size: 25rpx; font-weight: 800; }.tab-item view text:last-child { overflow: hidden; margin-top: 4rpx; color: #8d978e; font-size: 18rpx; text-overflow: ellipsis; white-space: nowrap; }.tab-item.active view text:last-child { color: rgba(255, 255, 255, .75); }
.content-panel { margin-top: 22rpx; border: 1rpx solid rgba(117, 105, 85, .13); border-radius: 31rpx; background: rgba(255, 254, 250, .88); box-shadow: 0 20rpx 43rpx rgba(64, 56, 43, .075); overflow: hidden; }
.panel-intro { display: flex; align-items: flex-start; justify-content: space-between; gap: 20rpx; padding: 30rpx 27rpx 22rpx; background: linear-gradient(125deg, rgba(249, 252, 247, .92), rgba(239, 244, 236, .82)); }
.panel-intro>view:first-child { display: flex; min-width: 0; flex: 1; flex-direction: column; }.panel-title { margin-top: 8rpx; color: #343832; font-family: "Songti SC", "STSong", serif; font-size: 33rpx; font-weight: 800; }.panel-desc { margin-top: 8rpx; color: #7a827a; font-size: 20rpx; line-height: 1.5; }
.online-chip { display: flex; align-items: center; flex: none; gap: 7rpx; margin-top: 8rpx; padding: 8rpx 11rpx; border-radius: 99rpx; color: #55816f; background: #e4f2eb; font-size: 18rpx; font-weight: 800; }.online-chip.human { color: #a25e45; background: #f8e9e1; }.status-dot { width: 9rpx; height: 9rpx; border-radius: 50%; background: #56a47e; }.online-chip.human .status-dot { background: #c7775a; }
.conversation-meta { display: flex; justify-content: space-between; gap: 18rpx; padding: 13rpx 27rpx; border-bottom: 1rpx solid #eee8df; color: #9a9d96; font-size: 18rpx; }.conversation-meta text:first-child { flex: 1; }.conversation-meta text:last-child { flex: none; }
.chat-shell { padding: 18rpx 18rpx 17rpx; }.messages { box-sizing: border-box; height: 570rpx; padding: 4rpx 8rpx 20rpx; }.loading-state, .empty-state { display: flex; align-items: center; justify-content: center; min-height: 470rpx; flex-direction: column; color: #8e968e; font-size: 23rpx; text-align: center; }.empty-state text:last-child { max-width: 460rpx; margin-top: 11rpx; color: #a0a39e; font-size: 19rpx; line-height: 1.65; }
.seal-loader, .empty-seal { display: flex; align-items: center; justify-content: center; width: 67rpx; height: 67rpx; margin-bottom: 18rpx; border: 2rpx solid rgba(102, 135, 117, .55); border-radius: 13rpx; color: #668574; font-family: "Songti SC", "STSong", serif; font-size: 31rpx; font-weight: 800; }.seal-loader { animation: seal-pulse 1.35s ease-in-out infinite; }
@keyframes seal-pulse { 0%, 100% { opacity: .52; transform: rotate(-7deg) scale(.93); } 50% { opacity: 1; transform: rotate(-7deg) scale(1); } }
.message-row { display: flex; align-items: flex-start; gap: 10rpx; margin-bottom: 19rpx; }.message-row.mine { justify-content: flex-end; }.message-avatar { display: flex; align-items: center; justify-content: center; flex: none; width: 44rpx; height: 44rpx; border-radius: 14rpx; color: #fff; background: #668878; font-size: 16rpx; font-weight: 900; }.message-avatar.staff { color: #fff9f3; background: #bd7256; }.bubble-wrap { max-width: 82%; }.message-bubble { box-sizing: border-box; padding: 15rpx 17rpx; border: 1rpx solid #e5ece5; border-radius: 7rpx 20rpx 20rpx 20rpx; color: #455049; background: #fdfefb; box-shadow: 0 6rpx 14rpx rgba(69, 82, 69, .045); }.message-bubble text { white-space: pre-wrap; font-size: 24rpx; line-height: 1.62; }.mine .message-bubble { border: 0; border-radius: 20rpx 7rpx 20rpx 20rpx; color: #fffdf8; background: linear-gradient(135deg, #587a6b, #719383); box-shadow: 0 8rpx 17rpx rgba(61, 103, 83, .16); }.message-detail { display: flex; justify-content: space-between; gap: 14rpx; margin: 7rpx 5rpx 0; color: #a6aaa5; font-size: 17rpx; }.mine .message-detail { justify-content: flex-end; }
.quick-topics { margin-top: 4rpx; padding-top: 16rpx; border-top: 1rpx solid #eee8df; }.quick-label { display: block; margin: 0 8rpx 9rpx; color: #89978d; font-size: 18rpx; font-weight: 800; letter-spacing: 1rpx; }.quick-scroll { width: 100%; white-space: nowrap; }.quick-list { display: inline-flex; gap: 10rpx; padding: 0 8rpx 2rpx; }.quick-item { flex: none; height: 52rpx; margin: 0; padding: 0 15rpx; border: 1rpx solid #d9e8de; border-radius: 99rpx; color: #5d8272; background: #f7fbf7; font-size: 19rpx; line-height: 50rpx; }.quick-item::after, .send-button::after, .error-action::after, .rights-submit::after, .asset-state button::after { border: 0; }.quick-item[disabled] { opacity: .52; }
.composer { display: flex; align-items: flex-end; gap: 12rpx; margin-top: 17rpx; padding: 12rpx; border: 1rpx solid #e4e9e1; border-radius: 21rpx; background: #f7f8f3; }.message-input { box-sizing: border-box; flex: 1; max-height: 146rpx; min-height: 46rpx; padding: 8rpx 3rpx; color: #3d443f; background: transparent; font-size: 23rpx; line-height: 1.55; }.send-button { flex: none; width: 104rpx; height: 59rpx; margin: 0; border-radius: 16rpx; color: #fffdf8; background: #668574; font-size: 21rpx; font-weight: 800; line-height: 59rpx; }.send-button[disabled] { opacity: .42; }
.service-note, .legal-note { display: flex; align-items: flex-start; gap: 9rpx; margin: 0 25rpx 24rpx; padding: 16rpx 0 0; border-top: 1rpx solid #eee8df; color: #979990; font-size: 18rpx; line-height: 1.6; }.note-symbol { color: #a86a52; font-size: 22rpx; font-weight: 900; line-height: 1.4; }
.error-card, .success-card { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin: 17rpx 20rpx 0; padding: 16rpx; border: 1rpx solid #f0d7ca; border-radius: 18rpx; color: #9b5e48; background: #fff5ef; }.error-card view, .success-card view { display: flex; min-width: 0; flex: 1; flex-direction: column; gap: 4rpx; }.error-card view text:last-child, .success-card view text:last-child { font-size: 19rpx; line-height: 1.45; }.error-title { font-size: 21rpx; font-weight: 800; }.error-action { flex: none; height: 51rpx; margin: 0; padding: 0 14rpx; border-radius: 13rpx; color: #9c6048; background: #f9e5d9; font-size: 19rpx; line-height: 51rpx; }.success-card { border-color: #d2e6d8; color: #537d68; background: #f0f8f1; }.success-mark { display: flex; align-items: center; justify-content: center; flex: none; width: 43rpx; height: 43rpx; border-radius: 50%; color: #fff; background: #69957a; font-weight: 900; }
.rights-intro { padding-bottom: 28rpx; }.rights-seal { display: flex; align-items: center; justify-content: center; flex: none; width: 60rpx; height: 60rpx; margin-top: 3rpx; border: 2rpx solid rgba(166, 95, 70, .68); border-radius: 8rpx; color: #a25f47; font-family: "Songti SC", "STSong", serif; font-size: 30rpx; font-weight: 800; transform: rotate(-7deg); }.form-section { padding: 27rpx 25rpx 0; }.form-heading { display: flex; align-items: flex-start; gap: 13rpx; }.step-no { display: flex; align-items: center; justify-content: center; flex: none; width: 37rpx; height: 37rpx; margin-top: 2rpx; border-radius: 50%; color: #fffdf8; background: #6e8e7d; font-size: 17rpx; font-weight: 900; }.form-heading view { display: flex; flex-direction: column; }.form-heading view>text:first-child { color: #44473f; font-family: "Songti SC", "STSong", serif; font-size: 27rpx; font-weight: 800; }.form-heading view>text:last-child { margin-top: 5rpx; color: #91958d; font-size: 19rpx; line-height: 1.45; }.optional { margin-left: 4rpx; color: #9b9e98; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; font-size: 17rpx; font-weight: 500; }
.service-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx; margin-top: 18rpx; }.service-option { position: relative; display: flex; min-height: 130rpx; gap: 11rpx; padding: 15rpx 12rpx; border: 1rpx solid #e6e4db; border-radius: 19rpx; background: #fffefa; }.service-option:active { background: #f6f8f3; }.service-option.selected { border-color: #719584; background: linear-gradient(145deg, #f3faf4, #edf5ee); box-shadow: 0 7rpx 15rpx rgba(79, 122, 96, .1); }.service-option-icon { display: flex; align-items: center; justify-content: center; flex: none; width: 37rpx; height: 37rpx; border-radius: 12rpx; color: #6d8a79; background: #e9f1eb; font-family: "Songti SC", "STSong", serif; font-size: 20rpx; font-weight: 800; }.service-option>view:nth-child(2) { display: flex; min-width: 0; flex-direction: column; }.service-option>view:nth-child(2) text:first-child { color: #41473f; font-size: 21rpx; font-weight: 800; line-height: 1.35; }.service-option>view:nth-child(2) text:last-child { margin-top: 7rpx; color: #92968f; font-size: 17rpx; line-height: 1.4; }.selection-mark { position: absolute; top: 10rpx; right: 10rpx; display: flex; align-items: center; justify-content: center; width: 27rpx; height: 27rpx; border-radius: 50%; color: #fff; background: #638c75; font-size: 17rpx; font-weight: 900; }
.work-section { padding-bottom: 0; }.asset-state { display: flex; align-items: center; justify-content: center; min-height: 126rpx; margin-top: 18rpx; border: 1rpx dashed #d8ded6; border-radius: 18rpx; color: #8c968e; font-size: 20rpx; }.asset-error { flex-direction: column; gap: 10rpx; color: #a86b55; border-color: #eddbd0; background: #fffaf7; }.asset-state button { height: 45rpx; margin: 0; padding: 0 15rpx; border-radius: 12rpx; color: #a0664e; background: #f8e9df; font-size: 19rpx; line-height: 45rpx; }.asset-scroll { width: 100%; margin-top: 18rpx; white-space: nowrap; }.asset-list { display: inline-flex; gap: 11rpx; padding-bottom: 3rpx; }.asset-option { position: relative; display: flex; align-items: center; gap: 10rpx; box-sizing: border-box; width: 274rpx; min-height: 93rpx; padding: 13rpx; border: 1rpx solid #e6e4dc; border-radius: 18rpx; background: #fffefa; white-space: normal; }.asset-option.selected { border-color: #709481; background: #f0f7f1; box-shadow: 0 7rpx 15rpx rgba(76, 122, 95, .1); }.asset-mark { display: flex; align-items: center; justify-content: center; flex: none; width: 41rpx; height: 41rpx; border-radius: 13rpx; color: #8b846f; background: #f1ede3; font-family: "Songti SC", "STSong", serif; font-size: 18rpx; font-weight: 800; }.asset-mark.image { color: #a16851; background: #f7e9e1; }.asset-mark.model { color: #56816f; background: #e2f0e9; font-family: -apple-system, BlinkMacSystemFont, "PingFang SC", sans-serif; font-size: 15rpx; }.asset-option>view:nth-child(2) { display: flex; min-width: 0; flex: 1; flex-direction: column; }.asset-option>view:nth-child(2) text:first-child { overflow: hidden; color: #4c5048; font-size: 20rpx; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }.asset-option>view:nth-child(2) text:last-child { margin-top: 5rpx; color: #92968f; font-size: 17rpx; }.asset-selected { position: absolute; top: 9rpx; right: 9rpx; color: #5f8d75; font-size: 20rpx; font-weight: 900; }.asset-empty { display: flex; flex-direction: column; justify-content: center; box-sizing: border-box; width: 360rpx; min-height: 93rpx; padding: 13rpx 16rpx; border: 1rpx dashed #dcded7; border-radius: 18rpx; color: #929890; white-space: normal; }.asset-empty text:first-child { color: #788379; font-size: 20rpx; font-weight: 700; }.asset-empty text:last-child { margin-top: 5rpx; font-size: 17rpx; line-height: 1.4; }
.note-section { padding-bottom: 0; }.rights-note-input { box-sizing: border-box; width: 100%; min-height: 146rpx; margin-top: 18rpx; padding: 17rpx; border: 1rpx solid #e3e4dd; border-radius: 18rpx; color: #4a5049; background: #fafaf6; font-size: 22rpx; line-height: 1.58; }.char-count { display: block; margin: 7rpx 4rpx 0; color: #a5a7a1; font-size: 17rpx; text-align: right; }.rights-submit { width: calc(100% - 50rpx); height: 92rpx; margin: 30rpx 25rpx 0; border-radius: 21rpx; color: #fffdf8; background: linear-gradient(135deg, #5c7f70, #78988a); box-shadow: 0 13rpx 24rpx rgba(68, 112, 87, .19); font-size: 27rpx; font-weight: 800; line-height: 92rpx; }.rights-submit[disabled] { opacity: .45; box-shadow: none; }.legal-note { margin-top: 22rpx; padding-top: 18rpx; }
@media (max-width: 360px) { .hero-title { font-size: 32rpx; }.tab-item { gap: 8rpx; padding: 14rpx 9rpx; }.tab-item view text:first-child { font-size: 23rpx; }.tab-item view text:last-child { font-size: 16rpx; }.service-grid { gap: 9rpx; }.service-option { padding: 12rpx 9rpx; gap: 8rpx; }.service-option>view:nth-child(2) text:first-child { font-size: 19rpx; }.service-option>view:nth-child(2) text:last-child { font-size: 16rpx; }.messages { height: 520rpx; } }
</style>
