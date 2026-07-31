<template>
  <view class="page">
    <view class="top"><text class="tag">CREATE WITH PURPOSE</text><text class="title">这次创作，<text>为了什么？</text></text><text class="desc">选择用途后才能进入创作工作台。博物馆创作将记录审批出处。</text></view>
    <view class="choice" :class="{active: purpose==='personal'}" @tap="purpose='personal'"><text class="icon">✦</text><view><text class="name">个人创作</text><text class="intro">为自己的灵感、作品与生活方式而创作</text></view><text class="check">{{ purpose==='personal' ? '✓' : '' }}</text></view>
    <view class="choice" :class="{active: purpose==='museum_sale'}" @tap="purpose='museum_sale'"><text class="icon">⌘</text><view><text class="name">售卖（景区、博物馆）</text><text class="intro">面向景区文创店、博物馆文创店与文旅渠道售卖</text></view><text class="check">{{ purpose==='museum_sale' ? '✓' : '' }}</text></view>
    <view v-if="purpose==='museum_sale'" class="museum">
      <text class="museum-title">选择服务博物馆</text>
      <picker :range="provinces" :value="provinceIndex" @change="chooseProvince"><view class="picker">{{ province || '选择省 / 直辖市' }}<text>›</text></view></picker>
      <picker :range="cities" :value="cityIndex" @change="chooseCity" :disabled="!province"><view class="picker">{{ city || '选择城市' }}<text>›</text></view></picker>
      <picker :range="districts" :value="districtIndex" @change="chooseDistrict" :disabled="!city"><view class="picker">{{ district || '选择区 / 县' }}<text>›</text></view></picker>
      <picker :range="museumNames" :value="museumIndex" @change="chooseMuseum" :disabled="!district"><view class="picker">{{ museum?.name || '选择具体博物馆' }}<text>›</text></view></picker>
      <view v-if="museum?.recommendation" class="recommendation">
        <view class="recommendation-head"><text>选址策略建议（测试）</text><text>{{ museum.recommendation.badge }}</text></view>
        <view class="metrics"><text>客流潜力：{{ museum.recommendation.trafficLevel }}</text><text>竞争强度：{{ museum.recommendation.competitionLevel }}</text><text>爆款潜力：{{ museum.recommendation.breakoutPotential }}</text></view>
        <text class="advice">优点：{{ museum.recommendation.advantages }}</text>
        <text class="advice">注意：{{ museum.recommendation.risks }}</text>
        <text class="disclaimer">{{ museum.recommendation.disclaimer }}</text>
      </view>
      <text v-if="museum" class="source">审批出处将标注：{{ province }} · {{ city }} · {{ district }} · {{ museum.name }}</text>
    </view>
    <button class="enter" :disabled="!canEnter" @tap="enter">确认并进入创作</button>
  </view>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getMuseums } from '../../api/creative'
import { requireSession } from '../../utils/session'
const purpose = ref<'personal'|'museum_sale'|''>('')
const museums = ref<any[]>([]); const province=ref(''); const city=ref(''); const district=ref(''); const museum=ref<any>(null)
const provinceIndex=ref(0); const cityIndex=ref(0); const districtIndex=ref(0); const museumIndex=ref(0)
const provinces=computed(()=>[...new Set(museums.value.map(x=>x.province))]); const cities=computed(()=>[...new Set(museums.value.filter(x=>x.province===province.value).map(x=>x.city))]); const districts=computed(()=>[...new Set(museums.value.filter(x=>x.province===province.value&&x.city===city.value).map(x=>x.district))]); const filtered=computed(()=>museums.value.filter(x=>x.province===province.value&&x.city===city.value&&x.district===district.value)); const museumNames=computed(()=>filtered.value.map(x=>x.name)); const canEnter=computed(()=>purpose.value==='personal'||!!museum.value)
function reset(level: 'province'|'city'|'district') { if(level==='province'){city.value='';district.value='';museum.value=null} if(level==='city'){district.value='';museum.value=null} if(level==='district')museum.value=null }
function chooseProvince(e:any){province.value=provinces.value[e.detail.value];provinceIndex.value=e.detail.value;reset('province')}; function chooseCity(e:any){city.value=cities.value[e.detail.value];cityIndex.value=e.detail.value;reset('city')}; function chooseDistrict(e:any){district.value=districts.value[e.detail.value];districtIndex.value=e.detail.value;reset('district')}; function chooseMuseum(e:any){museum.value=filtered.value[e.detail.value];museumIndex.value=e.detail.value}
function enter(){if(!canEnter.value)return;uni.setStorageSync('creation_context',{purpose:purpose.value,museum:museum.value});uni.reLaunch({url:'/pages/home/index'})}
onMounted(async()=>{if(!requireSession())return;try{museums.value=await getMuseums()}catch(e:any){uni.showToast({title:e.message||'博物馆目录加载失败',icon:'none'})}})
</script>
<style scoped lang="scss">
.page{min-height:100vh;padding:116rpx 42rpx 80rpx;box-sizing:border-box;background:linear-gradient(155deg,#25140f,#74301d 52%,#d17743)}.top{display:flex;flex-direction:column}.tag{font-size:20rpx;letter-spacing:4rpx;color:#f6be8c}.title{color:#fff;font-size:62rpx;line-height:1.3;font-weight:800;margin-top:24rpx}.title text{color:#ffd4a8}.desc{color:#f5dfd0;font-size:26rpx;line-height:1.75;margin:22rpx 0 40rpx}.choice{display:flex;align-items:center;background:rgba(255,255,255,.11);border:2rpx solid rgba(255,255,255,.22);padding:30rpx;border-radius:25rpx;margin-bottom:20rpx;color:#fff}.choice.active{background:#fff6eb;color:#542115;border-color:#ffd5a5}.icon{font-size:46rpx;width:72rpx}.name,.intro{display:block}.name{font-size:32rpx;font-weight:700}.intro{font-size:23rpx;opacity:.75;margin-top:8rpx;line-height:1.45}.check{font-size:36rpx;margin-left:auto}.museum{margin-top:28rpx;background:#fff;border-radius:28rpx;padding:30rpx}.museum-title{font-weight:700;font-size:32rpx;display:block;margin-bottom:16rpx}.picker{display:flex;justify-content:space-between;align-items:center;background:#faf5f0;border-radius:16rpx;padding:0 22rpx;height:86rpx;font-size:27rpx;margin:14rpx 0}.picker text{font-size:38rpx;color:#a34a2a}.recommendation{margin-top:20rpx;padding:20rpx;border-radius:16rpx;background:#fff4e8}.recommendation-head{display:flex;justify-content:space-between;gap:12rpx;font-size:22rpx;font-weight:700;color:#9c4325}.recommendation-head text:last-child{padding:4rpx 12rpx;border-radius:99rpx;background:#f4d4ba;font-size:19rpx}.metrics{display:flex;flex-wrap:wrap;gap:10rpx;margin-top:16rpx}.metrics text{padding:6rpx 10rpx;background:#fff;border-radius:8rpx;color:#785a4d;font-size:20rpx}.advice,.disclaimer{display:block;line-height:1.6}.advice{font-size:21rpx;font-weight:600;color:#573025;margin-top:14rpx}.disclaimer{font-size:18rpx;color:#9a7b6d;margin-top:8rpx}.source{font-size:21rpx;line-height:1.6;color:#9b5239;display:block;margin-top:16rpx}.enter{margin-top:36rpx;border-radius:48rpx;height:96rpx;line-height:96rpx;font-size:30rpx;font-weight:700;color:#5a2414;background:#ffdab0}.enter[disabled]{opacity:.45}
</style>
