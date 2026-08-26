<template>
  <view class="page">
    <view class="head">
      <text class="eyebrow">MAKE IT REAL</text>
      <text class="title">打样 / 生产申请</text>
      <text class="sub">审核通过的三视图作品包或 3D 作品可以提交。平台确认申请后再进入打样与后续生产安排。</text>
    </view>

    <view class="work-card"><text class="work-label">{{ bundleId ? '三视图作品包' : '申请作品' }}</text><text class="work-title">{{ assetTitle || (bundleId ? '三视图作品' : '3D 作品') }}</text><text class="work-id">{{ bundleId ? `作品包编号：${bundleNo || bundleId}` : `作品编号：${assetId || '-'}` }}</text></view>
    <view v-if="sourceLoading" class="source-loading">正在读取已审核的作品包，请稍候…</view>
    <view v-else-if="sourceError" class="source-error"><text>{{ sourceError }}</text><button type="button" @tap="reloadSource">重新读取</button></view>

    <view v-if="projectId && versionId" class="preflight-card" :class="`preflight-${preflight?.status || 'not_run'}`">
      <view class="preflight-head"><view><text>PRODUCTION PREFLIGHT</text><text>生产预检</text></view><button :loading="preflightLoading" :disabled="preflightLoading || submitting" @tap="runPreflight">{{ preflightLoading ? '检查中' : '运行预检' }}</button></view>
      <text class="preflight-status">{{ preflight ? `${preflightStatusLabel} · ${preflight.score ?? '-'} 分` : '提交前会检查版本、模型、三视图、规格书和审核记录' }}</text>
      <view v-if="preflight?.issues?.length" class="preflight-list"><text v-for="issue in preflight.issues.slice(0, 3)" :key="issue">{{ issue }}</text></view>
      <text v-if="preflight?.status === 'needs_review'" class="preflight-tip">存在需要人工确认的项目，平台审核时会继续核对。</text>
    </view>

    <view v-if="bundleImages.length" class="bundle-preview">
      <view v-for="item in bundleImages" :key="item.assetId"><image :src="bundleImageUrl(item)" mode="aspectFit" /><text>{{ item.label }}</text></view>
    </view>

    <view class="card">
      <text class="label">申请类型</text>
    <view v-if="bundleId && requestType === 'sample'" class="bundle-sample-only"><text>先做打样</text><text>三视图审核已通过，先确认材质、工艺和实物效果</text></view>
      <view v-else-if="bundleId" class="bundle-sample-only"><text>提交批量生产</text><text>该三视图作品已完成样品验收，平台会按冻结版本审核量产申请</text></view>
      <radio-group v-else class="options" @change="changeRequestType">
        <label class="option" :class="{ active: requestType === 'sample' }"><radio value="sample" :checked="requestType === 'sample'" color="#9b4328" /><view><text>先做打样</text><text>建议先确认工艺、材质与实物效果</text></view></label>
        <label class="option" :class="{ active: requestType === 'bulk' }"><radio value="bulk" :checked="requestType === 'bulk'" color="#9b4328" /><view><text>批量生产</text><text>适合已确认方案的正式量产</text></view></label>
      </radio-group>

      <text class="label">申请数量</text>
      <input v-model.trim="quantity" class="input" type="number" :placeholder="requestType === 'sample' ? '默认 1 件' : '请输入生产数量'" />

      <text class="label">用途与收货方式</text>
      <radio-group class="purpose-options" @change="changePurpose">
        <label :class="{ active: purpose === 'personal' }"><radio value="personal" :checked="purpose === 'personal'" color="#9b4328" />个人收藏 / 送礼</label>
        <label :class="{ active: purpose === 'museum_sale' }"><radio value="museum_sale" :checked="purpose === 'museum_sale'" color="#9b4328" />景区 / 博物馆售卖</label>
      </radio-group>

      <template v-if="purpose === 'museum_sale'">
        <text class="label">投放渠道</text>
        <picker :range="provinces" :value="provinceIndex" @change="changeProvince">
          <view class="picker">{{ province || '选择省 / 直辖市' }}<text>›</text></view>
        </picker>
        <picker :range="museumNames" :value="museumIndex" @change="changeMuseum" :disabled="!province || !museumNames.length">
          <view class="picker">{{ selectedMuseum?.name || '选择该省博物馆或景区' }}<text>›</text></view>
        </picker>
        <text v-if="selectedMuseum" class="museum-location">{{ selectedMuseum.city }} · {{ selectedMuseum.district }} · {{ selectedMuseum.scene }}</text>
        <text class="tip">售卖会将全部数量投放至选定渠道，提交后等待平台与授权方审核。</text>
      </template>

      <template v-else>
        <text class="label">收货信息</text>
        <input v-model.trim="recipientName" class="input" placeholder="收货人姓名" />
        <input v-model.trim="recipientPhone" class="input" type="number" placeholder="联系电话" />
        <textarea v-model.trim="recipientAddress" class="textarea" maxlength="300" placeholder="详细收货地址" />
      </template>

      <text class="label">补充说明（可选）</text>
      <textarea v-model.trim="note" class="textarea" maxlength="500" placeholder="例如材质偏好、包装要求、预计使用场景等" />
    </view>

    <button class="submit" :loading="submitting" :disabled="submitting || preflightLoading || sourceLoading || Boolean(sourceError)" @tap="submit">{{ submitting ? '提交中…' : '提交申请' }}</button>
    <text class="footer">提交即代表你确认该作品为原创或已取得相应授权；实际价格、生产周期和版权事项以人工审核结果为准。</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMuseums, getMyMultiViewBundles, runCreativePreflight as runCreativePreflightApi, submitProductionRequest } from '../../api/creative'
import { requireSession } from '../../utils/session'
import { imageUrl } from '../../utils/format'

const assetId = ref<number | null>(null)
const bundleId = ref<number | null>(null)
const projectId = ref<number | null>(null)
const versionId = ref<number | null>(null)
const bundleNo = ref('')
const bundleImages = ref<any[]>([])
const assetTitle = ref('')
const requestType = ref<'sample' | 'bulk'>('sample')
const quantity = ref('1')
const purpose = ref<'personal' | 'museum_sale'>('personal')
const museums = ref<any[]>([])
const province = ref('')
const provinceIndex = ref(0)
const museumIndex = ref(0)
const selectedMuseumId = ref('')
const recipientName = ref('')
const recipientPhone = ref('')
const recipientAddress = ref('')
const note = ref('')
const submitting = ref(false)
const preflight = ref<any>(null)
const preflightLoading = ref(false)
const sourceLoading = ref(false)
const sourceError = ref('')

const provinces = computed(() => [...new Set(museums.value.map((museum) => museum.province))])
const filteredMuseums = computed(() => museums.value.filter((museum) => museum.province === province.value))
const museumNames = computed(() => filteredMuseums.value.map((museum) => `${museum.name} · ${museum.channelType === 'scenic_spot' ? '景区' : '博物馆'}`))
const selectedMuseum = computed(() => filteredMuseums.value.find((museum) => String(museum.id) === selectedMuseumId.value) || null)
const bundleImageUrl = (item: any) => imageUrl(item?.previewUrl || item?.imageUrl || item?.fileUrl || '')
const preflightStatusLabel = computed(() => {
  const labels: Record<string, string> = { passed: '预检通过', needs_review: '需要人工复核', blocked: '存在阻断问题', not_run: '尚未运行' }
  return labels[String(preflight.value?.status || 'not_run')] || '尚未运行'
})

function safelyDecode(value: unknown) {
  try { return decodeURIComponent(String(value || '')) } catch { return String(value || '') }
}

function changeRequestType(event: any) {
  requestType.value = event.detail.value === 'bulk' ? 'bulk' : 'sample'
  if (requestType.value === 'sample' && (!quantity.value || Number(quantity.value) < 1)) quantity.value = '1'
}

function changePurpose(event: any) {
  purpose.value = event.detail.value === 'museum_sale' ? 'museum_sale' : 'personal'
}

function changeProvince(event: any) {
  provinceIndex.value = Number(event.detail.value) || 0
  province.value = provinces.value[provinceIndex.value] || ''
  museumIndex.value = 0
  selectedMuseumId.value = ''
}

function changeMuseum(event: any) {
  museumIndex.value = Number(event.detail.value) || 0
  selectedMuseumId.value = String(filteredMuseums.value[museumIndex.value]?.id || '')
}

async function loadMuseums(context: any) {
  try {
    const rows = await getMuseums()
    museums.value = Array.isArray(rows) ? rows : []
    const contextMuseumId = context?.museum?.id
    const contextMuseum = museums.value.find((museum) => String(museum.id) === String(contextMuseumId || ''))
    if (contextMuseum) {
      province.value = contextMuseum.province || ''
      provinceIndex.value = Math.max(0, provinces.value.indexOf(province.value))
      const index = filteredMuseums.value.findIndex((museum) => String(museum.id) === String(contextMuseumId))
      museumIndex.value = index >= 0 ? index : 0
      selectedMuseumId.value = String(contextMuseum.id)
    }
  } catch (error: any) {
    uni.showToast({ title: error.message || '博物馆目录加载失败', icon: 'none' })
  }
}

function validQuantity() {
  const parsed = Number(quantity.value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : 0
}

function requestSignature(amount: number) {
  // Keep retries idempotent while allowing an intentional quantity/address
  // change to create a new request key on the same page.
  const raw = [projectId.value || 0, versionId.value || 0, assetId.value || 0, bundleId.value || 0,
    requestType.value, amount, purpose.value, selectedMuseumId.value, recipientName.value,
    recipientPhone.value, recipientAddress.value, note.value].join('|')
  let hash = 0
  for (let index = 0; index < raw.length; index += 1) hash = ((hash * 31) + raw.charCodeAt(index)) >>> 0
  return hash.toString(36)
}

function requestKey(amount: number) {
  const scope = [projectId.value || 0, versionId.value || 0, assetId.value || 0, bundleId.value || 0, requestType.value, requestSignature(amount)].join('-')
  const storageKey = `production-request-key:${scope}`
  let key = String(uni.getStorageSync(storageKey) || '')
  if (!key) {
    key = `miniapp-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
    uni.setStorageSync(storageKey, key)
  }
  return { key, storageKey }
}

async function runPreflight() {
  if (preflightLoading.value) return false
  if (!projectId.value || !versionId.value) return true
  preflightLoading.value = true
  try {
    preflight.value = await runCreativePreflightApi(projectId.value, versionId.value, {
      assetId: assetId.value || undefined,
      bundleId: bundleId.value || undefined,
    })
    if (preflight.value?.status === 'blocked') {
      uni.showModal({ title: '暂不能提交打样', content: preflight.value?.issues?.[0] || '当前版本未通过生产预检', showCancel: false })
      return false
    }
    return true
  } catch (error: any) {
    uni.showToast({ title: error.message || '生产预检失败', icon: 'none' })
    return false
  } finally {
    preflightLoading.value = false
  }
}

async function submit() {
  if (submitting.value || preflightLoading.value || sourceLoading.value) return
  if (sourceError.value) return uni.showToast({ title: sourceError.value, icon: 'none' })
  if (!assetId.value && !bundleId.value) return uni.showToast({ title: '缺少作品编号，请返回作品页重新进入', icon: 'none' })
  const amount = validQuantity()
  if (!amount) return uni.showToast({ title: '申请数量必须是大于 0 的整数', icon: 'none' })
  const museum = selectedMuseum.value
  if (purpose.value === 'museum_sale' && !museum) return uni.showToast({ title: '请选择投放博物馆', icon: 'none' })
  if (purpose.value === 'personal' && (!recipientName.value || !recipientPhone.value || !recipientAddress.value)) {
    return uni.showToast({ title: '请填写完整收货信息', icon: 'none' })
  }
  submitting.value = true
  try {
    if (!(await runPreflight())) return
    const idempotency = requestKey(amount)
    const response = await submitProductionRequest({
      assetId: assetId.value || undefined,
      bundleId: bundleId.value || undefined,
      projectId: projectId.value || undefined,
      versionId: versionId.value || undefined,
      idempotencyKey: idempotency.key,
      requestType: requestType.value,
      title: `${requestType.value === 'sample' ? '打样申请' : '批量生产申请'}-${assetTitle.value || (bundleId.value ? '三视图作品' : '3D作品')}`,
      quantity: amount,
      purpose: purpose.value,
      selfShipQuantity: purpose.value === 'personal' ? amount : 0,
      museumDistribution: purpose.value === 'museum_sale' ? [{ museumId: String(museum.id), museumName: museum.name, quantity: amount }] : [],
      recipientName: recipientName.value,
      recipientPhone: recipientPhone.value,
      recipientAddress: recipientAddress.value,
      note: note.value,
    })
    const lifecycleProjectId = response?.projectId || projectId.value
    const lifecycleVersionId = response?.versionId || versionId.value
    const lifecycleUrl = requestType.value === 'sample' && response?.id && lifecycleProjectId && lifecycleVersionId
      ? `/pages/sample-lifecycle/index?projectId=${encodeURIComponent(String(lifecycleProjectId))}&versionId=${encodeURIComponent(String(lifecycleVersionId))}&requestId=${encodeURIComponent(String(response.id))}&title=${encodeURIComponent(assetTitle.value || '样品申请')}`
      : ''
    uni.showModal({
      title: '申请已提交',
      content: response?.message || '平台审核完成后会更新申请状态。',
      showCancel: false,
      confirmText: lifecycleUrl ? '查看样品进度' : '查看申请',
      success: result => {
        if (lifecycleUrl && result.confirm) {
          uni.redirectTo({ url: lifecycleUrl })
        } else {
          uni.redirectTo({ url: '/pages/production-requests/index' })
        }
      },
    })
    // Remove only after the server accepted the request. A lost response can
    // then safely retry with the same key instead of creating a duplicate.
    uni.removeStorageSync(idempotency.storageKey)
  } catch (error: any) {
    // Keep the validation/server reason visible long enough for the user to
    // act on it. A short toast made failed submissions look like a dead
    // button, especially when production preflight rejected the request.
    uni.showModal({
      title: '申请未提交',
      content: error?.message || '提交申请失败，请稍后重试',
      showCancel: false,
      confirmText: '知道了',
    })
  } finally {
    submitting.value = false
  }
}

async function loadBundle(bundleKey: number) {
  sourceLoading.value = true
  sourceError.value = ''
  try {
    const rows = await getMyMultiViewBundles()
    const bundle = (Array.isArray(rows) ? rows : []).find(item => Number(item.id || item.bundleId) === bundleKey)
    if (!bundle) throw new Error('三视图作品包不存在或已被移除')
    if (bundle.status !== 'approved') {
      throw new Error('该三视图作品包尚未审核通过，暂不能申请打样')
    }
    bundleNo.value = String(bundle.bundleNo || '')
    projectId.value = Number(bundle.projectId) > 0 ? Number(bundle.projectId) : null
    versionId.value = Number(bundle.versionId) > 0 ? Number(bundle.versionId) : null
    bundleImages.value = Array.isArray(bundle.images) ? bundle.images : []
    assetTitle.value = bundle.productName || assetTitle.value || '三视图作品'
    if (!projectId.value || !versionId.value) {
      sourceError.value = '该作品包缺少项目版本信息，请返回作品库刷新后重试'
    }
  } catch (error: any) {
    sourceError.value = error?.message || '三视图作品包加载失败'
  } finally {
    sourceLoading.value = false
  }
}

function reloadSource() {
  if (bundleId.value) void loadBundle(bundleId.value)
}

onLoad((query: any) => {
  if (!requireSession()) return
  const parsedId = Number(query?.assetId)
  if (Number.isFinite(parsedId) && parsedId > 0) assetId.value = parsedId
  const parsedProjectId = Number(query?.projectId)
  const parsedVersionId = Number(query?.versionId)
  if (Number.isFinite(parsedProjectId) && parsedProjectId > 0) projectId.value = parsedProjectId
  if (Number.isFinite(parsedVersionId) && parsedVersionId > 0) versionId.value = parsedVersionId
  const parsedBundleId = Number(query?.bundleId)
  const requestedTitle = safelyDecode(query?.title)
  requestType.value = String(query?.requestType || '') === 'bulk' ? 'bulk' : 'sample'
  if (Number.isFinite(parsedBundleId) && parsedBundleId > 0) {
    bundleId.value = parsedBundleId
    assetTitle.value = requestedTitle || '三视图作品'
    void loadBundle(parsedBundleId)
  } else {
    assetTitle.value = requestedTitle
  }
  const context = uni.getStorageSync('creation_context') || {}
  purpose.value = context.purpose === 'museum_sale' ? 'museum_sale' : 'personal'
  void loadMuseums(context)
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:38rpx 34rpx 80rpx;box-sizing:border-box}.head{padding:14rpx 4rpx 30rpx}.eyebrow{display:block;font-size:20rpx;color:#a64b2b;letter-spacing:3rpx}.title{display:block;font-size:48rpx;font-weight:800;margin-top:14rpx}.sub{display:block;font-size:23rpx;line-height:1.65;color:#8c7063;margin-top:12rpx}.work-card{padding:26rpx 30rpx;background:linear-gradient(135deg,#482116,#9c4529);border-radius:24rpx;color:#fff}.work-label,.work-title,.work-id{display:block}.work-label{font-size:21rpx;color:#f3cdb8}.work-title{font-size:33rpx;font-weight:750;margin-top:9rpx}.work-id{font-size:20rpx;color:#eec8b2;margin-top:10rpx}.source-loading,.source-error{display:flex;align-items:center;justify-content:space-between;gap:12rpx;margin-top:16rpx;padding:15rpx 18rpx;border-radius:14rpx;background:#f3f7f2;color:#668273;font-size:19rpx;line-height:1.45}.source-error{background:#fff5f1;color:#a05d4b}.source-error button{flex:none;height:52rpx;margin:0;padding:0 14rpx;border:1rpx solid #d8b5a8;border-radius:10rpx;background:#fff;color:#9b5e4c;font-size:17rpx;line-height:52rpx}.card{margin-top:26rpx;background:#fff;border-radius:25rpx;padding:30rpx;box-sizing:border-box}.label{display:block;font-size:28rpx;font-weight:750;margin:8rpx 0 17rpx}.options{display:flex;gap:14rpx;margin-bottom:28rpx}.option{flex:1;display:flex;gap:8rpx;align-items:flex-start;background:#faf4ee;border:2rpx solid transparent;border-radius:16rpx;padding:18rpx 12rpx;box-sizing:border-box}.option.active{background:#fff5eb;border-color:#d9936e}.option text{display:block;font-size:24rpx;font-weight:700}.option text:last-child{font-size:19rpx;line-height:1.45;font-weight:400;color:#8d7366;margin-top:8rpx}.input,.textarea,.picker{box-sizing:border-box;width:100%;background:#faf5f1;border-radius:16rpx;padding:0 22rpx;font-size:26rpx;margin-bottom:17rpx}.input,.picker{height:86rpx;line-height:86rpx}.textarea{height:150rpx;padding-top:20rpx;line-height:1.5}.picker{display:flex;justify-content:space-between;align-items:center}.picker text{font-size:38rpx;color:#a34a2a}.purpose-options{display:flex;flex-direction:column;gap:12rpx;margin-bottom:28rpx}.purpose-options label{display:block;padding:18rpx;background:#faf4ee;border:2rpx solid transparent;border-radius:14rpx;font-size:25rpx}.purpose-options label.active{border-color:#d9936e;background:#fff8f1}.tip{display:block;font-size:21rpx;color:#936d5c;line-height:1.55;margin:-4rpx 0 25rpx}.submit{height:96rpx;line-height:96rpx;margin-top:34rpx;background:#963c23;color:#fff;border-radius:48rpx;font-size:30rpx}.footer{display:block;margin:26rpx 12rpx 0;font-size:20rpx;color:#a0877b;line-height:1.65;text-align:center}
.museum-location{display:block;margin:-4rpx 4rpx 14rpx;color:#88796c;font-size:20rpx;line-height:1.5}
.bundle-preview{display:grid;grid-template-columns:repeat(3,1fr);gap:10rpx;margin-top:18rpx}.bundle-preview>view{overflow:hidden;border:1rpx solid #dce4dc;border-radius:10rpx;background:#fff}.bundle-preview image{display:block;width:100%;height:150rpx;background:#edf1ed}.bundle-preview text{display:block;padding:8rpx;color:#617668;font-size:19rpx;font-weight:800;text-align:center}.bundle-sample-only{display:flex;flex-direction:column;gap:7rpx;margin-bottom:28rpx;padding:20rpx;border:2rpx solid #9caf9f;border-radius:14rpx;background:#eef4ee}.bundle-sample-only text:first-child{color:#486856;font-size:27rpx;font-weight:800}.bundle-sample-only text:last-child{color:#75877b;font-size:20rpx;line-height:1.5}
.preflight-card{margin-top:18rpx;padding:18rpx;border:1rpx solid #d8e2d8;border-radius:18rpx;background:#f7faf6}.preflight-card.preflight-blocked{border-color:#e3c5bb;background:#fff7f3}.preflight-card.preflight-needs_review{border-color:#e7d7b8;background:#fffaf1}.preflight-head{display:flex;align-items:flex-start;justify-content:space-between;gap:10rpx}.preflight-head>view{display:flex;flex-direction:column;gap:4rpx}.preflight-head>view text:first-child{color:#6c8777;font-size:13rpx;font-weight:900;letter-spacing:1.4rpx}.preflight-head>view text:last-child{color:#443c35;font-family:"Songti SC","STSong",serif;font-size:24rpx;font-weight:800}.preflight-head button{height:52rpx;margin:0;padding:0 13rpx;border:1rpx solid #9cb6a3;border-radius:10rpx;background:#fff;color:#557565;font-size:14rpx}.preflight-head button::after{border:0}.preflight-status{display:block;margin-top:10rpx;color:#5c7565;font-size:17rpx;font-weight:800}.preflight-blocked .preflight-status{color:#9c5f4d}.preflight-needs_review .preflight-status{color:#997748}.preflight-list{display:flex;flex-direction:column;gap:4rpx;margin-top:9rpx;padding-top:9rpx;border-top:1rpx solid rgba(130,111,91,.14)}.preflight-list text{color:#7c6c5f;font-size:14rpx;line-height:1.45}.preflight-tip{display:block;margin-top:9rpx;color:#94784c;font-size:13rpx;line-height:1.45}
</style>

<style scoped lang="scss">
.page{background:radial-gradient(ellipse at 6% 0%,rgba(151,177,163,.17),transparent 28%),linear-gradient(180deg,#faf8f3,#f0e9df)}.title{font-family:"Songti SC","STSong",serif;color:#302b26}.eyebrow{color:#5f7d70}.sub{color:#82786d}.work-card{border:1rpx solid rgba(114,96,78,.12);background:linear-gradient(145deg,#edf3ed,#dce9df);color:#385043;box-shadow:0 10rpx 23rpx rgba(63,82,69,.08)}.work-label,.work-id{color:#668075}.card{border:1rpx solid rgba(129,112,93,.13);box-shadow:0 9rpx 22rpx rgba(67,53,37,.055)}.option{background:#faf8f3}.option.active{border-color:#9caf9f;background:#eef4ee}.input,.textarea,.picker{border:1rpx solid #e4dcd1;background:#fbf9f4}.picker text{color:#a5664f}.purpose-options label{background:#faf8f3}.purpose-options label.active{border-color:#9caf9f;background:#eef4ee}.submit{border-radius:17rpx;background:linear-gradient(135deg,#3e3933,#617e71);box-shadow:0 12rpx 22rpx rgba(52,58,52,.16)}.footer{color:#93877c}
</style>
