<template>
  <view class="page">
    <view class="ink ink-top" />
    <view class="ink ink-bottom" />

    <view class="hero">
      <view class="hero-top">
        <view>
          <text class="eyebrow">CULTURAL OBJECT MARKET</text>
          <text class="title">把好故事，带回日常。</text>
        </view>
        <view class="order-link" @tap="goOrders"><text>订单</text><text>›</text></view>
      </view>
      <text class="hero-copy">甄选已审核的文化创意作品与实体衍生品。每一件商品的材质、尺寸与库存，都以实际 SKU 为准。</text>
      <view class="search-box">
        <input v-model.trim="keyword" confirm-type="search" maxlength="50" placeholder="搜索作品、故事或灵感关键词" @confirm="search" />
        <text @tap="search">搜索</text>
      </view>
    </view>

    <view class="section-head category-head">
      <view><text class="section-kicker">CURATED COLLECTION</text><text>按灵感慢慢逛</text></view>
      <text>{{ artworks.length }} 件已审核作品</text>
    </view>
    <scroll-view scroll-x class="category-scroll" :show-scrollbar="false">
      <view class="category-row">
        <view class="category-chip" :class="{ active: !selectedCategoryId }" @tap="chooseCategory()">全部</view>
        <view v-for="category in categories" :key="category.id" class="category-chip" :class="{ active: selectedCategoryId === category.id }" @tap="chooseCategory(category.id)">{{ category.name }}</view>
      </view>
    </scroll-view>

    <view v-if="tags.length" class="tag-rail"><text>灵感标签</text><scroll-view scroll-x :show-scrollbar="false"><view><text v-for="tag in tags.slice(0, 8)" :key="tag.id"># {{ tag.name }}</text></view></scroll-view></view>

    <view v-if="loading" class="loading-card"><text class="seal">印</text><text>正在整理馆藏灵感与可售作品…</text></view>
    <view v-else-if="errorMessage" class="state-card"><text>{{ errorMessage }}</text><button size="mini" @tap="loadMarket">重新加载</button></view>
    <view v-else-if="!artworks.length" class="state-card"><text>没有找到匹配的文化作品</text><text>换一个关键词或分类再看看。</text></view>

    <scroll-view v-else scroll-x class="artwork-scroll" :show-scrollbar="false">
      <view class="artwork-row">
        <view v-for="artwork in featuredArtworks" :key="artwork.id" class="artwork-card" @tap="openArtwork(artwork.id)">
          <image v-if="coverOfArtwork(artwork)" :src="coverOfArtwork(artwork)" class="artwork-cover" mode="aspectFill" />
          <view v-else class="artwork-placeholder"><text>{{ artwork.title?.slice(0, 1) || '艺' }}</text></view>
          <view class="artwork-shade" />
          <view class="artwork-content">
            <text>{{ artwork.categoryName || '文化创意' }}</text>
            <text>{{ artwork.title }}</text>
            <text>{{ artwork.designerName || '平台甄选设计师' }}</text>
            <view><text>¥{{ money(artwork.minPrice) }} 起</text><text>{{ artwork.skuCount || 0 }} 个衍生品</text></view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="section-head product-head">
      <view><text class="section-kicker">READY TO TAKE HOME</text><text>可带回家的文化小物</text></view>
      <text>{{ onSaleSkus.length }} 件可售</text>
    </view>

    <view v-if="!loading && !onSaleSkus.length" class="state-card compact"><text>当前分类暂无可售商品</text></view>
    <view class="product-grid">
      <view v-for="sku in onSaleSkus" :key="sku.id" class="product-card" @tap="openSku(sku)">
        <image v-if="coverOfSku(sku)" :src="coverOfSku(sku)" class="product-cover" mode="aspectFill" />
        <view v-else class="product-placeholder"><text>{{ sku.productName?.slice(0, 1) || '物' }}</text></view>
        <view class="product-body">
          <text class="product-type">{{ sku.productType || '文创衍生品' }}</text>
          <text class="product-name">{{ sku.productName }}</text>
          <text class="product-meta">{{ sku.material || '材质以详情为准' }} · {{ sku.size || '规格待确认' }}</text>
          <view class="product-foot"><view><text>¥{{ money(sku.price) }}</text><text v-if="hasOriginalPrice(sku)">¥{{ money(sku.originalPrice) }}</text></view><text>{{ stockText(sku) }}</text></view>
        </view>
      </view>
    </view>

    <view v-if="designers.length" class="designer-section">
      <view class="section-head designer-head"><view><text class="section-kicker">CREATOR NOTES</text><text>来自设计师的文化视角</text></view></view>
      <scroll-view scroll-x :show-scrollbar="false"><view class="designer-row"><view v-for="designer in designers.slice(0, 6)" :key="designer.id" class="designer-card"><image v-if="designer.avatarUrl" :src="imageUrl(designer.avatarUrl)" class="designer-avatar" mode="aspectFill" /><view v-else class="designer-avatar fallback">{{ (designer.brandName || designer.displayName || '创').slice(0, 1) }}</view><view><text>{{ designer.brandName || designer.displayName || '文化创作者' }}</text><text>{{ designer.artworkCount || 0 }} 件作品 · 审核通过后上架</text></view></view></view></scroll-view>
    </view>

    <view class="market-note"><text>真实交易说明</text><text>下单只会创建“待支付”订单；支付、收货地址与履约能力正在接入，未支付订单不会被标记为已完成或发货。</text></view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onPullDownRefresh } from '@dcloudio/uni-app'
import {
  getMarketplaceArtworks,
  getMarketplaceCategories,
  getMarketplaceDesigners,
  getMarketplaceSkus,
  getMarketplaceTags,
  type MarketplaceArtwork,
  type MarketplaceCategory,
  type MarketplaceDesigner,
  type MarketplaceSku,
  type MarketplaceTag,
} from '../../api/marketplace'
import { imageUrl, moneyText } from '../../utils/format'
import { requireSession } from '../../utils/session'

const categories = ref<MarketplaceCategory[]>([])
const tags = ref<MarketplaceTag[]>([])
const artworks = ref<MarketplaceArtwork[]>([])
const skus = ref<MarketplaceSku[]>([])
const designers = ref<MarketplaceDesigner[]>([])
const keyword = ref('')
const selectedCategoryId = ref<number | null>(null)
const loading = ref(false)
const errorMessage = ref('')

const featuredArtworks = computed(() => artworks.value.slice(0, 8))
const onSaleSkus = computed(() => {
  const matchingArtworkIds = new Set(artworks.value.map(item => item.id))
  return skus.value.filter(item => (
    matchingArtworkIds.has(item.artworkId)
    && item.status === 'on_sale'
    && Number(item.stock ?? 0) > 0
  ))
})

const toRows = <T,>(value: unknown): T[] => Array.isArray(value) ? value as T[] : []
const money = (value: unknown) => moneyText(value)
const coverOfArtwork = (artwork: MarketplaceArtwork) => imageUrl(artwork.thumbnailUrl || artwork.imageUrl || '')
const coverOfSku = (sku: MarketplaceSku) => imageUrl(sku.coverUrl || '')
const hasOriginalPrice = (sku: MarketplaceSku) => Number(sku.originalPrice) > Number(sku.price)
const stockText = (sku: MarketplaceSku) => Number(sku.stock || 0) <= 8 ? `仅余 ${sku.stock || 0} 件` : '有货'

async function loadMarket() {
  loading.value = true
  errorMessage.value = ''
  try {
    const filters = { keyword: keyword.value, categoryId: selectedCategoryId.value || undefined }
    const [categoryData, tagData, artworkData, skuData, designerData] = await Promise.all([
      getMarketplaceCategories(),
      getMarketplaceTags(),
      getMarketplaceArtworks(filters),
      getMarketplaceSkus(),
      getMarketplaceDesigners(),
    ])
    categories.value = toRows<MarketplaceCategory>(categoryData)
    tags.value = toRows<MarketplaceTag>(tagData)
    artworks.value = toRows<MarketplaceArtwork>(artworkData)
    skus.value = toRows<MarketplaceSku>(skuData)
    designers.value = toRows<MarketplaceDesigner>(designerData)
  } catch (error: any) {
    errorMessage.value = error?.message || '商城内容暂时无法加载，请稍后重试'
  } finally {
    loading.value = false
  }
}

function chooseCategory(id?: number) {
  selectedCategoryId.value = id || null
  void loadMarket()
}

function search() {
  void loadMarket()
}

function openArtwork(id: number) {
  uni.navigateTo({ url: `/pages/product/index?artworkId=${encodeURIComponent(String(id))}` })
}

function openSku(sku: MarketplaceSku) {
  uni.navigateTo({ url: `/pages/product/index?artworkId=${encodeURIComponent(String(sku.artworkId))}&skuId=${encodeURIComponent(String(sku.id))}` })
}

function goOrders() {
  uni.navigateTo({ url: '/pages/orders/index' })
}

onLoad(() => {
  if (!requireSession()) return
  void loadMarket()
})

onPullDownRefresh(async () => {
  if (requireSession()) await loadMarket()
  uni.stopPullDownRefresh()
})
</script>

<style scoped lang="scss">
.page{position:relative;min-height:100vh;box-sizing:border-box;overflow:hidden;padding:30rpx 26rpx calc(52rpx + env(safe-area-inset-bottom));background:radial-gradient(ellipse at 3% 3%,rgba(146,172,156,.17),transparent 31%),linear-gradient(180deg,#faf8f3,#f1ebe2);color:#312d27}.ink{position:absolute;border-radius:999rpx;pointer-events:none;filter:blur(4rpx)}.ink-top{top:-112rpx;right:-95rpx;width:360rpx;height:196rpx;background:rgba(118,151,133,.13);transform:rotate(-19deg)}.ink-bottom{bottom:170rpx;left:-120rpx;width:300rpx;height:180rpx;background:rgba(187,112,82,.08);transform:rotate(18deg)}.hero,.section-head,.category-scroll,.tag-rail,.artwork-scroll,.product-grid,.designer-section,.market-note,.state-card,.loading-card{position:relative;z-index:1}.hero{padding:18rpx 8rpx 29rpx}.hero-top{display:flex;align-items:flex-start;justify-content:space-between;gap:20rpx}.eyebrow,.section-kicker{display:block;color:#688478;font-size:17rpx;font-weight:900;letter-spacing:2.2rpx}.title{display:block;margin-top:11rpx;color:#302b25;font-family:"Songti SC","STSong",serif;font-size:48rpx;font-weight:800;letter-spacing:-1rpx}.order-link{display:flex;align-items:center;gap:9rpx;margin-top:8rpx;padding:11rpx 15rpx;border:1rpx solid rgba(104,132,120,.2);border-radius:99rpx;background:rgba(251,253,250,.75);color:#547467;font-size:22rpx;font-weight:800}.order-link text:last-child{font-size:32rpx;font-weight:300;line-height:.7}.hero-copy{display:block;margin-top:17rpx;color:#82776c;font-size:23rpx;line-height:1.65}.search-box{display:flex;align-items:center;gap:12rpx;box-sizing:border-box;height:86rpx;margin-top:25rpx;padding:0 10rpx 0 23rpx;border:1rpx solid rgba(128,111,91,.15);border-radius:20rpx;background:rgba(255,254,251,.87);box-shadow:0 10rpx 25rpx rgba(67,53,37,.05)}.search-box input{flex:1;min-width:0;color:#3d3730;font-size:24rpx}.search-box>text{padding:13rpx 17rpx;border-radius:14rpx;background:#5f7e71;color:#fff;font-size:21rpx;font-weight:800}.section-head{display:flex;align-items:flex-end;justify-content:space-between;gap:18rpx;margin:23rpx 7rpx 14rpx}.section-head view text:last-child{display:block;margin-top:5rpx;color:#3b3933;font-family:"Songti SC","STSong",serif;font-size:31rpx;font-weight:800}.section-head>text{padding-bottom:3rpx;color:#988d82;font-size:18rpx}.category-head{margin-top:3rpx}.category-scroll{margin:0 -26rpx;padding:0 26rpx;white-space:nowrap}.category-row{display:flex;gap:11rpx;padding:4rpx 0 10rpx}.category-chip{flex:0 0 auto;padding:13rpx 20rpx;border:1rpx solid #e7ddd1;border-radius:99rpx;background:rgba(255,253,249,.84);color:#84796e;font-size:22rpx}.category-chip.active{border-color:#6e8c7e;background:#e7f0e9;color:#45685a;font-weight:800}.tag-rail{display:flex;align-items:center;gap:12rpx;margin:14rpx 2rpx 0;color:#9b8e80;font-size:18rpx}.tag-rail>text{flex:none;color:#a26149;font-weight:800}.tag-rail scroll-view{min-width:0;white-space:nowrap}.tag-rail scroll-view view{display:flex;gap:10rpx}.tag-rail scroll-view text{flex:0 0 auto;padding:5rpx 11rpx;border-radius:99rpx;background:rgba(242,234,222,.82);color:#95705d}.loading-card,.state-card{display:flex;align-items:center;justify-content:center;gap:14rpx;min-height:137rpx;margin-top:21rpx;padding:20rpx;border:1rpx dashed #d8cab9;border-radius:22rpx;background:rgba(255,253,249,.74);color:#8f8173;font-size:22rpx;text-align:center}.loading-card .seal{display:grid;place-items:center;width:46rpx;height:46rpx;border:2rpx solid #ad5d43;border-radius:7rpx;color:#ab5b42;font-family:"Songti SC","STSong",serif;font-size:25rpx;font-weight:800;animation:seal-pulse 1.4s ease-in-out infinite}.state-card{flex-direction:column}.state-card button{margin:2rpx 0 0;background:#e7f0e9;color:#4d7061;font-size:20rpx}.state-card.compact{min-height:70rpx;margin-top:0}.artwork-scroll{margin:0 -26rpx;padding:22rpx 26rpx 5rpx;white-space:nowrap}.artwork-row{display:flex;gap:16rpx}.artwork-card{position:relative;flex:0 0 500rpx;height:338rpx;overflow:hidden;border:1rpx solid rgba(119,102,81,.15);border-radius:27rpx;background:#d5ded7;box-shadow:0 16rpx 33rpx rgba(61,52,40,.12)}.artwork-cover{width:100%;height:100%;transition:transform .25s}.artwork-card:active .artwork-cover{transform:scale(1.03)}.artwork-placeholder,.product-placeholder{display:grid;place-items:center;width:100%;height:100%;background:linear-gradient(140deg,#718b7e,#d1a88e);color:rgba(255,255,255,.88);font-family:"Songti SC","STSong",serif;font-size:85rpx}.artwork-shade{position:absolute;inset:0;background:linear-gradient(180deg,rgba(19,27,23,.04) 23%,rgba(19,27,23,.76) 100%)}.artwork-content{position:absolute;right:23rpx;bottom:21rpx;left:23rpx;display:flex;flex-direction:column;gap:5rpx;color:#fff}.artwork-content>text:first-child{align-self:flex-start;padding:5rpx 10rpx;border-radius:99rpx;background:rgba(255,255,255,.2);font-size:17rpx}.artwork-content>text:nth-child(2){overflow:hidden;font-family:"Songti SC","STSong",serif;font-size:34rpx;font-weight:800;line-height:1.25;text-overflow:ellipsis;white-space:nowrap}.artwork-content>text:nth-child(3){overflow:hidden;color:rgba(255,255,255,.76);font-size:19rpx;text-overflow:ellipsis;white-space:nowrap}.artwork-content>view{display:flex;align-items:baseline;justify-content:space-between;margin-top:5rpx}.artwork-content>view text:first-child{font-size:25rpx;font-weight:800}.artwork-content>view text:last-child{color:rgba(255,255,255,.73);font-size:17rpx}.product-head{margin-top:34rpx}.product-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16rpx}.product-card{overflow:hidden;border:1rpx solid rgba(123,105,85,.14);border-radius:23rpx;background:rgba(255,253,249,.89);box-shadow:0 11rpx 25rpx rgba(67,53,37,.055)}.product-cover,.product-placeholder{display:block;width:100%;height:210rpx}.product-placeholder{font-size:58rpx}.product-body{padding:16rpx}.product-type{display:block;overflow:hidden;color:#738b7e;font-size:17rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.product-name{display:block;overflow:hidden;margin-top:7rpx;color:#38332d;font-family:"Songti SC","STSong",serif;font-size:26rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.product-meta{display:block;overflow:hidden;margin-top:6rpx;color:#94887c;font-size:17rpx;text-overflow:ellipsis;white-space:nowrap}.product-foot{display:flex;align-items:flex-end;justify-content:space-between;gap:7rpx;margin-top:14rpx}.product-foot>view{display:flex;flex-direction:column}.product-foot>view text:first-child{color:#a95137;font-size:26rpx;font-weight:900}.product-foot>view text:last-child{margin-top:2rpx;color:#b7aba0;font-size:16rpx;text-decoration:line-through}.product-foot>text{color:#778b7e;font-size:16rpx}.designer-section{margin-top:36rpx}.designer-head{margin-left:7rpx}.designer-row{display:flex;gap:13rpx;padding:1rpx 2rpx 7rpx}.designer-card{display:flex;flex:0 0 310rpx;align-items:center;gap:13rpx;padding:15rpx;border:1rpx solid rgba(117,101,83,.13);border-radius:20rpx;background:rgba(255,253,249,.78)}.designer-avatar{flex:none;width:66rpx;height:66rpx;border-radius:22rpx;background:#dbe8de}.designer-avatar.fallback{display:grid;place-items:center;color:#5d7c6e;font-family:"Songti SC","STSong",serif;font-size:29rpx;font-weight:800}.designer-card>view:last-child{display:flex;min-width:0;flex:1;flex-direction:column;gap:5rpx}.designer-card>view:last-child text:first-child{overflow:hidden;color:#474039;font-size:22rpx;font-weight:800;text-overflow:ellipsis;white-space:nowrap}.designer-card>view:last-child text:last-child{overflow:hidden;color:#9a8f84;font-size:16rpx;text-overflow:ellipsis;white-space:nowrap}.market-note{display:flex;flex-direction:column;gap:6rpx;margin:35rpx 4rpx 0;padding:19rpx;border:1rpx solid #eadacc;border-radius:20rpx;background:rgba(249,241,234,.86)}.market-note text:first-child{color:#a45e45;font-size:21rpx;font-weight:900}.market-note text:last-child{color:#947b6d;font-size:18rpx;line-height:1.65}@keyframes seal-pulse{50%{transform:rotate(-7deg) scale(1.06);opacity:.65}}
</style>
