<template>
  <view class="page">
    <view class="head">
      <text class="eyebrow">MAKE IT REAL</text>
      <text class="title">打样 / 生产申请</text>
      <text class="sub">仅审核通过的 3D 作品可以提交。平台审核通过后才会进入后续生产安排。</text>
    </view>

    <view class="work-card"><text class="work-label">申请作品</text><text class="work-title">{{ assetTitle || '3D 作品' }}</text><text class="work-id">作品编号：{{ assetId || '-' }}</text></view>

    <view class="card">
      <text class="label">申请类型</text>
      <radio-group class="options" @change="changeRequestType">
        <label class="option" :class="{ active: requestType === 'sample' }"><radio value="sample" :checked="requestType === 'sample'" color="#9b4328" /><view><text>先做打样</text><text>建议先确认工艺、材质与实物效果</text></view></label>
        <label class="option" :class="{ active: requestType === 'bulk' }"><radio value="bulk" :checked="requestType === 'bulk'" color="#9b4328" /><view><text>批量生产</text><text>适合已确认方案的正式量产</text></view></label>
      </radio-group>

      <text class="label">申请数量</text>
      <input v-model.trim="quantity" class="input" type="number" :placeholder="requestType === 'sample' ? '默认 1 件' : '请输入生产数量'" />

      <text class="label">用途与收货方式</text>
      <radio-group class="purpose-options" @change="changePurpose">
        <label :class="{ active: purpose === 'personal' }"><radio value="personal" :checked="purpose === 'personal'" color="#9b4328" />个人收藏 / 送礼</label>
        <label :class="{ active: purpose === 'museum_sale' }"><radio value="museum_sale" :checked="purpose === 'museum_sale'" color="#9b4328" />博物馆售卖</label>
      </radio-group>

      <template v-if="purpose === 'museum_sale'">
        <text class="label">投放博物馆</text>
        <picker :range="provinces" :value="provinceIndex" @change="changeProvince">
          <view class="picker">{{ province || '选择省 / 直辖市' }}<text>›</text></view>
        </picker>
        <picker :range="museumNames" :value="museumIndex" @change="changeMuseum" :disabled="!province || !museumNames.length">
          <view class="picker">{{ selectedMuseum?.name || '选择该省博物馆' }}<text>›</text></view>
        </picker>
        <text v-if="selectedMuseum" class="museum-location">{{ selectedMuseum.city }} · {{ selectedMuseum.district }} · {{ selectedMuseum.scene }}</text>
        <text class="tip">博物馆售卖会将全部数量投放至选定机构，提交后等待平台与授权方审核。</text>
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

    <button class="submit" :loading="submitting" @tap="submit">提交申请</button>
    <text class="footer">提交即代表你确认该作品为原创或已取得相应授权；实际价格、生产周期和版权事项以人工审核结果为准。</text>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMuseums, submitProductionRequest } from '../../api/creative'
import { requireSession } from '../../utils/session'

const assetId = ref<number | null>(null)
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

const provinces = computed(() => [...new Set(museums.value.map((museum) => museum.province))])
const filteredMuseums = computed(() => museums.value.filter((museum) => museum.province === province.value))
const museumNames = computed(() => filteredMuseums.value.map((museum) => museum.name))
const selectedMuseum = computed(() => filteredMuseums.value.find((museum) => String(museum.id) === selectedMuseumId.value) || null)

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

async function submit() {
  if (!assetId.value) return uni.showToast({ title: '缺少作品编号，请返回作品页重新进入', icon: 'none' })
  const amount = validQuantity()
  if (!amount) return uni.showToast({ title: '申请数量必须是大于 0 的整数', icon: 'none' })
  const museum = selectedMuseum.value
  if (purpose.value === 'museum_sale' && !museum) return uni.showToast({ title: '请选择投放博物馆', icon: 'none' })
  if (purpose.value === 'personal' && (!recipientName.value || !recipientPhone.value || !recipientAddress.value)) {
    return uni.showToast({ title: '请填写完整收货信息', icon: 'none' })
  }

  submitting.value = true
  try {
    const response = await submitProductionRequest({
      assetId: assetId.value,
      requestType: requestType.value,
      title: `${requestType.value === 'sample' ? '打样申请' : '批量生产申请'}-${assetTitle.value || '3D作品'}`,
      quantity: amount,
      purpose: purpose.value,
      selfShipQuantity: purpose.value === 'personal' ? amount : 0,
      museumDistribution: purpose.value === 'museum_sale' ? [{ museumId: String(museum.id), museumName: museum.name, quantity: amount }] : [],
      recipientName: recipientName.value,
      recipientPhone: recipientPhone.value,
      recipientAddress: recipientAddress.value,
      note: note.value,
    })
    uni.showModal({
      title: '申请已提交',
      content: response?.message || '平台审核完成后会更新申请状态。',
      showCancel: false,
      success: () => uni.navigateBack(),
    })
  } catch (error: any) {
    uni.showToast({ title: error.message || '提交申请失败', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

onLoad((query: any) => {
  if (!requireSession()) return
  const parsedId = Number(query?.assetId)
  if (Number.isFinite(parsedId) && parsedId > 0) assetId.value = parsedId
  assetTitle.value = safelyDecode(query?.title)
  const context = uni.getStorageSync('creation_context') || {}
  purpose.value = context.purpose === 'museum_sale' ? 'museum_sale' : 'personal'
  void loadMuseums(context)
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding:38rpx 34rpx 80rpx;box-sizing:border-box}.head{padding:14rpx 4rpx 30rpx}.eyebrow{display:block;font-size:20rpx;color:#a64b2b;letter-spacing:3rpx}.title{display:block;font-size:48rpx;font-weight:800;margin-top:14rpx}.sub{display:block;font-size:23rpx;line-height:1.65;color:#8c7063;margin-top:12rpx}.work-card{padding:26rpx 30rpx;background:linear-gradient(135deg,#482116,#9c4529);border-radius:24rpx;color:#fff}.work-label,.work-title,.work-id{display:block}.work-label{font-size:21rpx;color:#f3cdb8}.work-title{font-size:33rpx;font-weight:750;margin-top:9rpx}.work-id{font-size:20rpx;color:#eec8b2;margin-top:10rpx}.card{margin-top:26rpx;background:#fff;border-radius:25rpx;padding:30rpx;box-sizing:border-box}.label{display:block;font-size:28rpx;font-weight:750;margin:8rpx 0 17rpx}.options{display:flex;gap:14rpx;margin-bottom:28rpx}.option{flex:1;display:flex;gap:8rpx;align-items:flex-start;background:#faf4ee;border:2rpx solid transparent;border-radius:16rpx;padding:18rpx 12rpx;box-sizing:border-box}.option.active{background:#fff5eb;border-color:#d9936e}.option text{display:block;font-size:24rpx;font-weight:700}.option text:last-child{font-size:19rpx;line-height:1.45;font-weight:400;color:#8d7366;margin-top:8rpx}.input,.textarea,.picker{box-sizing:border-box;width:100%;background:#faf5f1;border-radius:16rpx;padding:0 22rpx;font-size:26rpx;margin-bottom:17rpx}.input,.picker{height:86rpx;line-height:86rpx}.textarea{height:150rpx;padding-top:20rpx;line-height:1.5}.picker{display:flex;justify-content:space-between;align-items:center}.picker text{font-size:38rpx;color:#a34a2a}.purpose-options{display:flex;flex-direction:column;gap:12rpx;margin-bottom:28rpx}.purpose-options label{display:block;padding:18rpx;background:#faf4ee;border:2rpx solid transparent;border-radius:14rpx;font-size:25rpx}.purpose-options label.active{border-color:#d9936e;background:#fff8f1}.tip{display:block;font-size:21rpx;color:#936d5c;line-height:1.55;margin:-4rpx 0 25rpx}.submit{height:96rpx;line-height:96rpx;margin-top:34rpx;background:#963c23;color:#fff;border-radius:48rpx;font-size:30rpx}.footer{display:block;margin:26rpx 12rpx 0;font-size:20rpx;color:#a0877b;line-height:1.65;text-align:center}
.museum-location{display:block;margin:-4rpx 4rpx 14rpx;color:#88796c;font-size:20rpx;line-height:1.5}
</style>

<style scoped lang="scss">
.page{background:radial-gradient(ellipse at 6% 0%,rgba(151,177,163,.17),transparent 28%),linear-gradient(180deg,#faf8f3,#f0e9df)}.title{font-family:"Songti SC","STSong",serif;color:#302b26}.eyebrow{color:#5f7d70}.sub{color:#82786d}.work-card{border:1rpx solid rgba(114,96,78,.12);background:linear-gradient(145deg,#edf3ed,#dce9df);color:#385043;box-shadow:0 10rpx 23rpx rgba(63,82,69,.08)}.work-label,.work-id{color:#668075}.card{border:1rpx solid rgba(129,112,93,.13);box-shadow:0 9rpx 22rpx rgba(67,53,37,.055)}.option{background:#faf8f3}.option.active{border-color:#9caf9f;background:#eef4ee}.input,.textarea,.picker{border:1rpx solid #e4dcd1;background:#fbf9f4}.picker text{color:#a5664f}.purpose-options label{background:#faf8f3}.purpose-options label.active{border-color:#9caf9f;background:#eef4ee}.submit{border-radius:17rpx;background:linear-gradient(135deg,#3e3933,#617e71);box-shadow:0 12rpx 22rpx rgba(52,58,52,.16)}.footer{color:#93877c}
</style>
