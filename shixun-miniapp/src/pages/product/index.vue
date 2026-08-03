<template>
  <view class="page">
    <view v-if="loading" class="loading"><text>印</text><text>正在展开作品故事…</text></view>

    <template v-else-if="artwork">
      <view class="cover-stage">
        <image v-if="coverUrl" :src="coverUrl" class="cover" mode="aspectFill" />
        <view v-else class="cover-placeholder"><text>{{ artwork.title.slice(0, 1) || '艺' }}</text></view>
        <view class="cover-shade" />
        <view class="cover-caption"><text>{{ artwork.categoryName || '文化创意' }}</text><text>{{ artwork.designerName || '平台甄选设计师' }}</text></view>
      </view>

      <view class="content">
        <view class="title-block">
          <text class="eyebrow">CULTURAL WORK</text>
          <text class="title">{{ artwork.title }}</text>
          <text v-if="artwork.subtitle" class="subtitle">{{ artwork.subtitle }}</text>
        </view>

        <view v-if="artwork.tags?.length" class="tags"><text v-for="tag in artwork.tags" :key="tag.id"># {{ tag.name }}</text></view>

        <view class="story-card">
          <text>作品故事</text>
          <text>{{ artwork.story || '这件作品正在等待它的完整文化故事。' }}</text>
          <view class="maker"><text>设计师</text><text>{{ artwork.designerName || '平台甄选设计师' }}</text><text>{{ artwork.designerBio || '以当代设计语言重新讲述文化记忆。' }}</text></view>
        </view>

        <view class="section-title"><view><text>选择实体衍生品</text><text>实际价格、材质、规格与库存以此处为准</text></view><text>{{ onSaleSkus.length }} 款</text></view>
        <view v-if="!onSaleSkus.length" class="empty-sku">该作品暂时没有可售的实体衍生品，请稍后再来看看。</view>
        <view v-else class="sku-list">
          <view v-for="sku in onSaleSkus" :key="sku.id" class="sku-card" :class="{ selected: selectedSkuId === sku.id }" @tap="selectSku(sku.id)">
            <image v-if="skuCover(sku)" :src="skuCover(sku)" class="sku-cover" mode="aspectFill" />
            <view v-else class="sku-cover fallback"><text>{{ sku.productName.slice(0, 1) || '物' }}</text></view>
            <view class="sku-info"><text>{{ sku.productName }}</text><text>{{ sku.material || '材质待确认' }} · {{ sku.size || '规格待确认' }}</text><text>剩余 {{ sku.stock }} 件</text></view>
            <view class="sku-price"><text>¥{{ money(sku.price) }}</text><text v-if="hasOriginalPrice(sku)">¥{{ money(sku.originalPrice) }}</text><text class="check">{{ selectedSkuId === sku.id ? '✓' : '' }}</text></view>
          </view>
        </view>

        <view v-if="selectedSku" class="quantity-card">
          <view><text>购买数量</text><text>库存 {{ selectedSku.stock }} 件</text></view>
          <view class="stepper"><text :class="{ disabled: quantity <= 1 }" @tap="changeQuantity(-1)">−</text><text>{{ quantity }}</text><text :class="{ disabled: quantity >= availableStock }" @tap="changeQuantity(1)">＋</text></view>
        </view>

        <view class="settlement-note"><text>支付能力正在接入</text><text>点击下方仅创建“待支付”订单。不会显示支付成功，也不会触发发货；支付、地址与售后能力上线后再完成交易闭环。</text></view>
      </view>

      <view class="bottom-bar"><view v-if="selectedSku"><text>合计</text><text>¥{{ totalPrice }}</text></view><button :disabled="!selectedSku" @tap="goCheckout">创建待支付订单</button></view>
    </template>

    <view v-else class="error-state"><text>{{ errorMessage || '未找到这件作品' }}</text><button @tap="backToMarket">返回商城</button></view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getMarketplaceArtwork, type MarketplaceArtwork, type MarketplaceSku } from '../../api/marketplace'
import { imageUrl, moneyText } from '../../utils/format'
import { requireSession } from '../../utils/session'

const artwork = ref<MarketplaceArtwork | null>(null)
const selectedSkuId = ref<number | null>(null)
const quantity = ref(1)
const loading = ref(false)
const errorMessage = ref('')

const onSaleSkus = computed(() => (artwork.value?.skus || []).filter(sku => sku.status === 'on_sale' && Number(sku.stock || 0) > 0))
const selectedSku = computed(() => onSaleSkus.value.find(sku => sku.id === selectedSkuId.value) || null)
const availableStock = computed(() => Math.max(0, Number(selectedSku.value?.stock || 0)))
const coverUrl = computed(() => imageUrl(artwork.value?.imageUrl || artwork.value?.thumbnailUrl || ''))
const totalPrice = computed(() => moneyText(Number(selectedSku.value?.price || 0) * quantity.value))
const money = (value: unknown) => moneyText(value)
const skuCover = (sku: MarketplaceSku) => imageUrl(sku.coverUrl || artwork.value?.thumbnailUrl || artwork.value?.imageUrl || '')
const hasOriginalPrice = (sku: MarketplaceSku) => Number(sku.originalPrice) > Number(sku.price)

function parseId(value: unknown) {
  const number = Number(value)
  return Number.isInteger(number) && number > 0 ? number : null
}

async function loadArtwork(id: number, preferredSkuId: number | null) {
  loading.value = true
  errorMessage.value = ''
  try {
    const detail = await getMarketplaceArtwork(id)
    artwork.value = detail || null
    const firstSku = (detail?.skus || []).find(sku => sku.status === 'on_sale' && Number(sku.stock || 0) > 0)
    const preferredSku = (detail?.skus || []).find(sku => sku.id === preferredSkuId && sku.status === 'on_sale' && Number(sku.stock || 0) > 0)
    selectedSkuId.value = (preferredSku || firstSku)?.id || null
    quantity.value = 1
  } catch (error: any) {
    errorMessage.value = error?.message || '作品详情加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function selectSku(id: number) {
  selectedSkuId.value = id
  quantity.value = 1
}

function changeQuantity(delta: number) {
  const next = Math.min(availableStock.value, Math.max(1, quantity.value + delta))
  quantity.value = next || 1
}

function goCheckout() {
  if (!selectedSku.value || !artwork.value) return
  uni.navigateTo({
    url: `/pages/checkout/index?artworkId=${encodeURIComponent(String(artwork.value.id))}&skuId=${encodeURIComponent(String(selectedSku.value.id))}&quantity=${encodeURIComponent(String(quantity.value))}`,
  })
}

function backToMarket() {
  uni.redirectTo({ url: '/pages/market/index' })
}

onLoad((query: any) => {
  if (!requireSession()) return
  const artworkId = parseId(query?.artworkId)
  if (!artworkId) {
    errorMessage.value = '缺少作品编号，请从商城重新进入'
    return
  }
  void loadArtwork(artworkId, parseId(query?.skuId))
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;padding-bottom:142rpx;background:linear-gradient(180deg,#f4efe6,#faf8f3 30%,#f2ece3);color:#332d27}.loading,.error-state{display:flex;align-items:center;justify-content:center;flex-direction:column;gap:18rpx;min-height:100vh;padding:40rpx;color:#87796b;font-size:25rpx;text-align:center}.loading text:first-child{display:grid;place-items:center;width:58rpx;height:58rpx;border:2rpx solid #a45c43;border-radius:7rpx;color:#a45c43;font-family:"Songti SC","STSong",serif;font-size:33rpx;font-weight:800;animation:pulse 1.4s ease-in-out infinite}.error-state button{margin:0;background:#607f71;color:#fff;font-size:23rpx}.cover-stage{position:relative;height:525rpx;overflow:hidden;background:linear-gradient(145deg,#657e72,#d4ad92)}.cover,.cover-placeholder{width:100%;height:100%}.cover-placeholder{display:grid;place-items:center;color:rgba(255,255,255,.88);font-family:"Songti SC","STSong",serif;font-size:145rpx}.cover-shade{position:absolute;inset:0;background:linear-gradient(180deg,rgba(24,31,27,.02),rgba(24,31,27,.55))}.cover-caption{position:absolute;right:28rpx;bottom:24rpx;left:28rpx;display:flex;justify-content:space-between;gap:15rpx;color:rgba(255,255,255,.86);font-size:19rpx}.cover-caption text:first-child{padding:6rpx 12rpx;border-radius:99rpx;background:rgba(255,255,255,.19)}.cover-caption text:last-child{overflow:hidden;padding-top:6rpx;text-overflow:ellipsis;white-space:nowrap}.content{padding:0 28rpx}.title-block{position:relative;margin-top:-31rpx;padding:25rpx 24rpx 23rpx;border:1rpx solid rgba(129,111,90,.14);border-radius:26rpx;background:rgba(255,254,251,.94);box-shadow:0 12rpx 30rpx rgba(67,53,37,.09)}.eyebrow{display:block;color:#6a887a;font-size:17rpx;font-weight:900;letter-spacing:2rpx}.title{display:block;margin-top:9rpx;color:#332d26;font-family:"Songti SC","STSong",serif;font-size:43rpx;font-weight:800;line-height:1.25}.subtitle{display:block;margin-top:9rpx;color:#87796d;font-size:22rpx;line-height:1.55}.tags{display:flex;gap:9rpx;overflow:auto;margin:16rpx 2rpx 0;white-space:nowrap}.tags text{flex:none;padding:7rpx 12rpx;border-radius:99rpx;background:#edf2eb;color:#638174;font-size:18rpx}.story-card{display:flex;flex-direction:column;gap:10rpx;margin-top:20rpx;padding:22rpx;border:1rpx solid rgba(128,110,89,.12);border-radius:23rpx;background:rgba(255,253,249,.78)}.story-card>text:first-child{color:#4e473f;font-family:"Songti SC","STSong",serif;font-size:28rpx;font-weight:800}.story-card>text:nth-child(2){color:#84786d;font-size:22rpx;line-height:1.75}.maker{display:grid;grid-template-columns:auto 1fr;gap:5rpx 12rpx;margin-top:5rpx;padding-top:15rpx;border-top:1rpx solid #eee5da}.maker text:first-child{grid-row:span 2;color:#a2624a;font-size:18rpx;font-weight:800}.maker text:nth-child(2){color:#5c625a;font-size:20rpx;font-weight:800}.maker text:last-child{color:#9a8e82;font-size:18rpx;line-height:1.45}.section-title{display:flex;align-items:flex-end;justify-content:space-between;gap:15rpx;margin:30rpx 4rpx 14rpx}.section-title view{display:flex;flex-direction:column;gap:5rpx}.section-title view text:first-child{color:#3c3731;font-family:"Songti SC","STSong",serif;font-size:30rpx;font-weight:800}.section-title view text:last-child{color:#998d81;font-size:18rpx}.section-title>text{color:#718a7d;font-size:19rpx}.sku-list{display:flex;flex-direction:column;gap:13rpx}.sku-card{display:grid;grid-template-columns:105rpx minmax(0,1fr) auto;align-items:center;gap:14rpx;padding:13rpx;border:1rpx solid #e7ddd1;border-radius:20rpx;background:rgba(255,253,249,.88)}.sku-card.selected{border-color:#88a496;background:#eef5ef;box-shadow:0 9rpx 19rpx rgba(64,92,78,.08)}.sku-cover{width:105rpx;height:105rpx;border-radius:15rpx;background:#e4ddd4}.sku-cover.fallback{display:grid;place-items:center;color:#668176;font-family:"Songti SC","STSong",serif;font-size:40rpx}.sku-info{display:flex;min-width:0;flex-direction:column;gap:6rpx}.sku-info text:first-child{overflow:hidden;color:#403a33;font-size:25rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.sku-info text:nth-child(2){overflow:hidden;color:#8e8277;font-size:18rpx;text-overflow:ellipsis;white-space:nowrap}.sku-info text:last-child{color:#70877a;font-size:17rpx}.sku-price{display:flex;align-items:flex-end;flex-direction:column;gap:3rpx}.sku-price text:first-child{color:#a55138;font-size:25rpx;font-weight:900}.sku-price text:nth-child(2){color:#b9ada2;font-size:16rpx;text-decoration:line-through}.sku-price .check{display:grid;place-items:center;width:27rpx;height:27rpx;margin-top:5rpx;border-radius:50%;background:#5e7d70;color:#fff;font-size:17rpx}.empty-sku{padding:28rpx;border:1rpx dashed #dbcabb;border-radius:20rpx;background:rgba(255,253,249,.7);color:#958779;font-size:21rpx;line-height:1.6;text-align:center}.quantity-card{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin-top:18rpx;padding:20rpx;border:1rpx solid rgba(126,108,87,.13);border-radius:21rpx;background:#fffdf9}.quantity-card>view:first-child{display:flex;flex-direction:column;gap:6rpx}.quantity-card>view:first-child text:first-child{color:#504941;font-size:25rpx;font-weight:800}.quantity-card>view:first-child text:last-child{color:#968a7e;font-size:18rpx}.stepper{display:flex;align-items:center;overflow:hidden;border:1rpx solid #e5dbcf;border-radius:14rpx}.stepper text{display:grid;place-items:center;width:55rpx;height:53rpx;color:#5c796c;font-size:29rpx}.stepper text:nth-child(2){width:60rpx;border-right:1rpx solid #e5dbcf;border-left:1rpx solid #e5dbcf;color:#454039;font-size:23rpx;font-weight:800}.stepper text.disabled{color:#c7bcb0}.settlement-note{display:flex;flex-direction:column;gap:6rpx;margin-top:19rpx;padding:18rpx;border:1rpx solid #ead7ca;border-radius:20rpx;background:#fbf1eb}.settlement-note text:first-child{color:#a45e44;font-size:21rpx;font-weight:900}.settlement-note text:last-child{color:#967b6c;font-size:18rpx;line-height:1.65}.bottom-bar{position:fixed;right:0;bottom:0;left:0;z-index:10;display:flex;align-items:center;justify-content:space-between;gap:18rpx;box-sizing:border-box;padding:17rpx 28rpx calc(17rpx + env(safe-area-inset-bottom));border-top:1rpx solid rgba(123,105,84,.13);background:rgba(255,254,251,.96);box-shadow:0 -7rpx 22rpx rgba(67,53,37,.07)}.bottom-bar>view{display:flex;flex-direction:column;gap:2rpx}.bottom-bar>view text:first-child{color:#968a7d;font-size:18rpx}.bottom-bar>view text:last-child{color:#a55039;font-size:31rpx;font-weight:900}.bottom-bar button{flex:none;height:78rpx;margin:0;padding:0 25rpx;border-radius:17rpx;background:linear-gradient(135deg,#3e3933,#617f72);color:#fff;font-size:22rpx;font-weight:800}.bottom-bar button[disabled]{background:#bfb7ae;color:#f5f2ee}@keyframes pulse{50%{transform:rotate(-8deg) scale(1.08);opacity:.65}}
</style>
