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
.page{position:relative;min-height:100vh;overflow:hidden;padding:100rpx 34rpx 80rpx;box-sizing:border-box;background:radial-gradient(circle at 10% 5%,rgba(145,163,151,.27),transparent 26%),radial-gradient(circle at 91% 89%,rgba(170,98,76,.15),transparent 29%),linear-gradient(145deg,#f8f4ed,#e9e1d6)}.page::before{content:"";position:absolute;right:-100rpx;top:150rpx;width:390rpx;height:116rpx;border-radius:50%;background:rgba(104,132,118,.1);filter:blur(19rpx);transform:rotate(-13deg)}.top,.choice,.museum,.enter{position:relative;z-index:1}.top{display:flex;flex-direction:column}.tag{color:#5e7d70;font-size:17rpx;font-weight:800;letter-spacing:3rpx}.title{margin-top:21rpx;color:#2d2924;font-family:"Songti SC","STSong",serif;font-size:58rpx;font-weight:700;line-height:1.3}.title text{color:#b9664f}.desc{margin:18rpx 0 33rpx;color:#766d63;font-size:24rpx;line-height:1.7}.choice{display:flex;align-items:center;margin-bottom:14rpx;padding:25rpx;border:1rpx solid rgba(114,96,78,.14);border-radius:21rpx;background:rgba(255,253,249,.77);color:#37312b;box-shadow:0 9rpx 20rpx rgba(73,55,37,.06)}.choice.active{border-color:#9caf9f;background:#eef4ee;color:#405f53}.icon{display:grid;place-items:center;width:58rpx;height:58rpx;border-radius:15rpx;background:#f2eee7;color:#a0604b;font-family:"Songti SC","STSong",serif;font-size:30rpx}.choice.active .icon{background:#607e71;color:#fff}.name,.intro{display:block}.name{font-size:28rpx;font-weight:800}.intro{margin-top:7rpx;color:#7d7369;font-size:20rpx;line-height:1.5}.check{margin-left:auto;color:#5b7a6d;font-size:31rpx}.museum{margin-top:21rpx;padding:25rpx;border:1rpx solid rgba(114,96,78,.14);border-radius:24rpx;background:rgba(255,253,249,.9);box-shadow:0 12rpx 28rpx rgba(73,55,37,.065)}.museum-title{display:block;margin-bottom:13rpx;color:#38322c;font-family:"Songti SC","STSong",serif;font-size:30rpx;font-weight:700}.picker{display:flex;justify-content:space-between;align-items:center;height:82rpx;margin:12rpx 0;padding:0 19rpx;border:1rpx solid #e5ddd2;border-radius:14rpx;background:#faf7f1;color:#554d45;font-size:24rpx}.picker text{color:#ad674f;font-size:33rpx}.recommendation{margin-top:17rpx;padding:17rpx;border:1rpx solid #dce8dd;border-radius:16rpx;background:#eff5ef}.recommendation-head{display:flex;justify-content:space-between;gap:12rpx;color:#58776a;font-size:20rpx;font-weight:800}.recommendation-head text:last-child{padding:4rpx 10rpx;border-radius:99rpx;background:#dceade;font-size:16rpx}.metrics{display:flex;flex-wrap:wrap;gap:8rpx;margin-top:13rpx}.metrics text{padding:6rpx 8rpx;border-radius:8rpx;background:#fffdfa;color:#6c776e;font-size:17rpx}.advice,.disclaimer{display:block;line-height:1.6}.advice{margin-top:12rpx;color:#52675d;font-size:19rpx;font-weight:600}.disclaimer{margin-top:7rpx;color:#8e887f;font-size:16rpx}.source{display:block;margin-top:13rpx;color:#9a6653;font-size:19rpx;line-height:1.6}.enter{height:92rpx;line-height:92rpx;margin-top:30rpx;border-radius:17rpx;background:linear-gradient(135deg,#3d3933,#627f72);color:#fff;font-size:28rpx;font-weight:800;box-shadow:0 12rpx 22rpx rgba(52,58,52,.16)}.enter[disabled]{opacity:.45}
</style>
