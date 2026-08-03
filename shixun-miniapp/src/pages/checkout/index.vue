<template>
  <view class="page">
    <template v-if="orderCreated">
      <view class="success-hero">
        <view class="seal">待</view>
        <text>订单已创建</text>
        <text>支付即将接入</text>
      </view>
      <view class="created-card">
        <view><text>订单编号</text><text>{{ orderCreated.orderNo }}</text></view>
        <view><text>待支付金额</text><text>¥{{ money(orderCreated.payAmount) }}</text></view>
        <view><text>当前状态</text><text>待支付</text></view>
      </view>
      <view class="honesty-card"><text>请注意</text><text>这不是支付成功页。当前订单只是已创建，支付、收货地址确认、发货、物流和售后能力仍在接入；平台不会把它标记为已付款、已完成或已发货。</text></view>
      <view class="success-actions"><button class="primary" @tap="goOrders">查看我的订单</button><button class="secondary" @tap="continueShopping">继续逛商城</button></view>
    </template>

    <template v-else>
      <view class="head"><text class="eyebrow">ORDER REVIEW</text><text class="title">确认待支付订单</text><text class="sub">创建订单前，我们会再次以实际商品 SKU、库存与服务端价格为准。</text></view>

      <view v-if="loading" class="loading"><text>正在核对商品信息…</text></view>
      <view v-else-if="sku" class="order-card">
        <image v-if="coverUrl" :src="coverUrl" class="cover" mode="aspectFill" />
        <view v-else class="cover fallback">{{ sku.productName.slice(0, 1) || '物' }}</view>
        <view class="product-info"><text>{{ sku.productName }}</text><text>{{ sku.artworkTitle || '文化创意作品' }}</text><text>{{ sku.material || '材质待确认' }} · {{ sku.size || '规格待确认' }}</text><text>SKU：{{ sku.skuCode || sku.id }}</text></view>
      </view>
      <view v-else-if="!loading" class="error-card"><text>{{ errorMessage || '该商品已下架或不可购买' }}</text><button size="mini" @tap="goMarket">返回商城</button></view>

      <template v-if="sku">
        <view class="summary-card">
          <view class="summary-line"><text>单价</text><text>¥{{ money(sku.price) }}</text></view>
          <view class="summary-line"><text>购买数量</text><view class="stepper"><text :class="{ disabled: quantity <= 1 }" @tap="changeQuantity(-1)">−</text><text>{{ quantity }}</text><text :class="{ disabled: quantity >= stock }" @tap="changeQuantity(1)">＋</text></view></view>
          <view class="summary-line total"><text>订单金额</text><text>¥{{ totalPrice }}</text></view>
        </view>

        <view class="remark-card"><text>给商家的备注（可选）</text><textarea v-model.trim="remark" maxlength="200" placeholder="例如：希望确认材质、尺寸或发货时间" /><text>{{ remark.length }}/200</text></view>

        <view class="payment-card"><view class="payment-mark">未</view><view><text>支付功能尚未接入</text><text>本次仅创建待支付订单。不会唤起支付、不会扣款，也不能视为支付完成。</text></view></view>
        <view class="fulfillment-card"><text>交易闭环进度</text><view><text>✓ 商品与库存校验</text><text>✓ 待支付订单创建</text><text>○ 微信支付接入中</text><text>○ 收货地址、发货、物流、退款/售后接入中</text></view></view>

        <button class="create-button" :loading="creating" @tap="confirmCreate">创建待支付订单</button>
        <text class="footnote">订单创建后，系统会保留“待支付”状态；只有未来真实支付回调确认后，才可进入生产或发货流程。</text>
      </template>
    </template>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  createMarketplaceOrder,
  getMarketplaceSkus,
  type CreateMarketplaceOrderResult,
  type MarketplaceSku,
} from '../../api/marketplace'
import { imageUrl, moneyText } from '../../utils/format'
import { requireSession } from '../../utils/session'

const sku = ref<MarketplaceSku | null>(null)
const artworkId = ref<number | null>(null)
const quantity = ref(1)
const remark = ref('')
const loading = ref(false)
const creating = ref(false)
const errorMessage = ref('')
const orderCreated = ref<CreateMarketplaceOrderResult | null>(null)

const stock = computed(() => Math.max(0, Number(sku.value?.stock || 0)))
const coverUrl = computed(() => imageUrl(sku.value?.coverUrl || ''))
const totalPrice = computed(() => moneyText(Number(sku.value?.price || 0) * quantity.value))
const money = (value: unknown) => moneyText(value)

function parsePositiveInteger(value: unknown) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

async function loadSku(skuId: number, parentArtworkId: number | null) {
  loading.value = true
  errorMessage.value = ''
  try {
    const rows = await getMarketplaceSkus(parentArtworkId || undefined)
    const match = Array.isArray(rows) ? rows.find(item => item.id === skuId) : null
    if (!match || match.status !== 'on_sale' || Number(match.stock || 0) <= 0) {
      errorMessage.value = '该商品已下架、库存不足或暂不可购买'
      return
    }
    sku.value = match
    artworkId.value = match.artworkId || parentArtworkId
    quantity.value = Math.min(quantity.value, Math.max(1, Number(match.stock)))
  } catch (error: any) {
    errorMessage.value = error?.message || '商品信息加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function changeQuantity(delta: number) {
  quantity.value = Math.min(stock.value, Math.max(1, quantity.value + delta)) || 1
}

function askForCreate() {
  return new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '确认创建待支付订单？',
      content: '支付尚未接入。创建后订单仍为待支付，不能当作付款成功，也不会安排发货。',
      confirmText: '确认创建',
      confirmColor: '#557769',
      success: result => resolve(Boolean(result.confirm)),
      fail: () => resolve(false),
    })
  })
}

async function confirmCreate() {
  if (!sku.value || creating.value || !requireSession()) return
  if (quantity.value < 1 || quantity.value > stock.value) {
    uni.showToast({ title: '商品库存已变化，请重新选择数量', icon: 'none' })
    return
  }
  if (!(await askForCreate())) return

  creating.value = true
  try {
    const created = await createMarketplaceOrder({
      items: [{ skuId: sku.value.id, quantity: quantity.value }],
      // 后端允许该真实支付方式标识，但此页面绝不尝试、也不伪造支付。
      paymentMethod: 'wechat',
      remark: remark.value || undefined,
    })
    orderCreated.value = created
  } catch (error: any) {
    uni.showToast({ title: error?.message || '订单创建失败，请刷新商品后重试', icon: 'none' })
    // 库存等服务端状态可能已变化，重新读取，但不自动重复创建订单。
    if (sku.value) await loadSku(sku.value.id, artworkId.value)
  } finally {
    creating.value = false
  }
}

function goOrders() {
  uni.redirectTo({ url: '/pages/orders/index' })
}

function continueShopping() {
  uni.redirectTo({ url: '/pages/market/index' })
}

function goMarket() {
  uni.redirectTo({ url: '/pages/market/index' })
}

onLoad((query: any) => {
  if (!requireSession()) return
  const skuId = parsePositiveInteger(query?.skuId)
  const requestedArtworkId = parsePositiveInteger(query?.artworkId)
  const requestedQuantity = parsePositiveInteger(query?.quantity)
  if (!skuId) {
    errorMessage.value = '缺少商品编号，请从商品详情重新进入'
    return
  }
  artworkId.value = requestedArtworkId
  quantity.value = requestedQuantity || 1
  void loadSku(skuId, requestedArtworkId)
})
</script>

<style scoped lang="scss">
.page{min-height:100vh;box-sizing:border-box;padding:38rpx 30rpx calc(72rpx + env(safe-area-inset-bottom));background:radial-gradient(ellipse at 6% 0%,rgba(151,177,163,.18),transparent 31%),linear-gradient(180deg,#faf8f3,#f0e9df);color:#342f29}.head{padding:6rpx 7rpx 29rpx}.eyebrow{display:block;color:#668276;font-size:18rpx;font-weight:900;letter-spacing:2.5rpx}.title{display:block;margin-top:12rpx;color:#302b26;font-family:"Songti SC","STSong",serif;font-size:45rpx;font-weight:800}.sub{display:block;margin-top:12rpx;color:#867b70;font-size:22rpx;line-height:1.65}.loading,.error-card{display:flex;align-items:center;justify-content:center;min-height:190rpx;padding:25rpx;border:1rpx dashed #d9cab9;border-radius:24rpx;background:rgba(255,253,249,.78);color:#8f8174;font-size:23rpx;text-align:center}.error-card{flex-direction:column;gap:15rpx}.error-card button{margin:0;background:#607f71;color:#fff;font-size:21rpx}.order-card{display:grid;grid-template-columns:144rpx minmax(0,1fr);gap:17rpx;padding:15rpx;border:1rpx solid rgba(127,109,89,.14);border-radius:24rpx;background:rgba(255,253,249,.9);box-shadow:0 12rpx 28rpx rgba(67,53,37,.06)}.cover{width:144rpx;height:144rpx;border-radius:18rpx;background:#e1dad1}.cover.fallback{display:grid;place-items:center;color:#648074;background:linear-gradient(145deg,#dfe9df,#ead6c6);font-family:"Songti SC","STSong",serif;font-size:55rpx}.product-info{display:flex;min-width:0;flex-direction:column;gap:6rpx}.product-info text:first-child{overflow:hidden;margin-top:2rpx;color:#3c3730;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.product-info text:nth-child(2){overflow:hidden;color:#698375;font-size:19rpx;text-overflow:ellipsis;white-space:nowrap}.product-info text:nth-child(3){overflow:hidden;color:#8e8277;font-size:18rpx;text-overflow:ellipsis;white-space:nowrap}.product-info text:last-child{margin-top:auto;color:#aaa095;font-size:16rpx}.summary-card,.remark-card,.payment-card,.fulfillment-card,.created-card,.honesty-card{margin-top:20rpx;border:1rpx solid rgba(128,110,90,.13);border-radius:23rpx;background:rgba(255,253,249,.86);box-shadow:0 8rpx 22rpx rgba(67,53,37,.045)}.summary-card{padding:6rpx 21rpx}.summary-line{display:flex;align-items:center;justify-content:space-between;min-height:81rpx;border-bottom:1rpx solid #eee5da;color:#746a61;font-size:23rpx}.summary-line:last-child{border-bottom:0}.summary-line>text:last-child{color:#464039;font-weight:800}.summary-line.total>text:first-child{color:#443d36;font-family:"Songti SC","STSong",serif;font-size:27rpx;font-weight:800}.summary-line.total>text:last-child{color:#a65138;font-size:31rpx;font-weight:900}.stepper{display:flex;align-items:center;overflow:hidden;border:1rpx solid #e2d8cd;border-radius:13rpx}.stepper text{display:grid;place-items:center;width:53rpx;height:49rpx;color:#5c796d;font-size:28rpx}.stepper text:nth-child(2){width:57rpx;border-right:1rpx solid #e2d8cd;border-left:1rpx solid #e2d8cd;color:#49423a;font-size:21rpx;font-weight:800}.stepper text.disabled{color:#c7bcb0}.remark-card{display:flex;flex-direction:column;padding:20rpx}.remark-card>text:first-child{color:#514a43;font-size:24rpx;font-weight:800}.remark-card textarea{box-sizing:border-box;width:100%;height:126rpx;margin-top:13rpx;padding:14rpx;border:1rpx solid #e6dcd1;border-radius:15rpx;background:#fbf9f4;color:#443f38;font-size:21rpx;line-height:1.5}.remark-card>text:last-child{align-self:flex-end;margin-top:6rpx;color:#aaa095;font-size:16rpx}.payment-card{display:flex;gap:15rpx;padding:19rpx;background:#fbf1eb;border-color:#ecd7ca}.payment-mark{display:grid;place-items:center;flex:none;width:51rpx;height:51rpx;border:2rpx solid #ad5e44;border-radius:8rpx;color:#aa5d43;font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:800;transform:rotate(-7deg)}.payment-card>view:last-child{display:flex;flex-direction:column;gap:5rpx}.payment-card>view:last-child text:first-child{color:#a45e44;font-size:22rpx;font-weight:900}.payment-card>view:last-child text:last-child{color:#947b6d;font-size:18rpx;line-height:1.55}.fulfillment-card{padding:20rpx}.fulfillment-card>text{display:block;color:#4e473f;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:800}.fulfillment-card>view{display:flex;flex-direction:column;gap:10rpx;margin-top:16rpx}.fulfillment-card>view text{font-size:20rpx}.fulfillment-card>view text:nth-child(-n+2){color:#5d7c6d}.fulfillment-card>view text:nth-child(n+3){color:#a18878}.create-button{height:92rpx;line-height:92rpx;margin-top:28rpx;border-radius:18rpx;background:linear-gradient(135deg,#3f3934,#617f72);color:#fff;font-size:28rpx;font-weight:800;box-shadow:0 13rpx 23rpx rgba(53,60,52,.16)}.footnote{display:block;margin:18rpx 11rpx 0;color:#998c80;font-size:18rpx;line-height:1.65;text-align:center}.success-hero{display:flex;align-items:center;flex-direction:column;padding:55rpx 25rpx 33rpx;text-align:center}.seal{display:grid;place-items:center;width:84rpx;height:84rpx;border:3rpx solid #5b7b6d;border-radius:11rpx;color:#5d7c6e;font-family:"Songti SC","STSong",serif;font-size:44rpx;font-weight:800;transform:rotate(-8deg)}.success-hero text:nth-child(2){margin-top:24rpx;color:#353029;font-family:"Songti SC","STSong",serif;font-size:44rpx;font-weight:800}.success-hero text:last-child{margin-top:10rpx;color:#6a8779;font-size:25rpx;font-weight:800}.created-card{overflow:hidden;margin-top:0}.created-card view{display:flex;align-items:center;justify-content:space-between;min-height:84rpx;padding:0 22rpx;border-bottom:1rpx solid #eee5db}.created-card view:last-child{border-bottom:0}.created-card text:first-child{color:#897d72;font-size:22rpx}.created-card text:last-child{color:#474038;font-size:23rpx;font-weight:800}.created-card view:nth-child(2) text:last-child{color:#a65138;font-size:29rpx}.honesty-card{display:flex;flex-direction:column;gap:7rpx;padding:20rpx;background:#fbf1eb;border-color:#ead4c7}.honesty-card text:first-child{color:#a15d44;font-size:22rpx;font-weight:900}.honesty-card text:last-child{color:#947b6d;font-size:19rpx;line-height:1.7}.success-actions{display:flex;flex-direction:column;gap:13rpx;margin-top:28rpx}.success-actions button{height:86rpx;line-height:86rpx;border-radius:18rpx;font-size:26rpx;font-weight:800}.success-actions .primary{background:linear-gradient(135deg,#3f3934,#617f72);color:#fff}.success-actions .secondary{border:1rpx solid #d9c9bb;background:#fffaf5;color:#8e654f}@media(max-width:360px){.product-info text:first-child{font-size:25rpx}.order-card{grid-template-columns:125rpx minmax(0,1fr)}.cover{width:125rpx;height:125rpx}}
</style>
