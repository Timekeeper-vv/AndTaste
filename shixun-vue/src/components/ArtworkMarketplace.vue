<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

const emit = defineEmits<{ alert: [msg: string, type?: 'success' | 'error'] }>()
const artworks = ref<any[]>([])
const categories = ref<any[]>([])
const skus = ref<any[]>([])
const selectedCategory = ref('')
const keyword = ref('')
const loading = ref(false)

const filteredSkus = computed(() => skus.value)

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams()
    if (keyword.value.trim()) params.set('keyword', keyword.value.trim())
    if (selectedCategory.value) params.set('categoryId', selectedCategory.value)
    const [catRes, artRes, skuRes] = await Promise.all([
      fetch('/api/creative/categories'),
      fetch(`/api/creative/artworks?${params.toString()}`),
      fetch('/api/creative/skus')
    ])
    categories.value = await catRes.json()
    artworks.value = await artRes.json()
    skus.value = await skuRes.json()
  } catch (e: any) {
    emit('alert', `加载商品失败：${e.message || e}`, 'error')
  } finally { loading.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">图片IP与文创商品</h2>
        <p class="page-desc">一张图片IP可衍生多个实体SKU，适配明信片、装饰画、帆布袋、贴纸等文创品类。</p>
      </div>
      <button class="btn btn-primary" @click="load">刷新</button>
    </div>

    <div class="toolbar creative-toolbar">
      <input v-model="keyword" class="search-input plain" placeholder="搜索作品标题、故事、关键词" @keyup.enter="load" />
      <select v-model="selectedCategory" class="select" @change="load">
        <option value="">全部分类</option>
        <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
      </select>
      <button class="btn btn-secondary" @click="load">查询</button>
    </div>

    <section class="artwork-grid">
      <article v-for="art in artworks" :key="art.id" class="art-card">
        <img :src="art.imageUrl" class="art-img" />
        <div class="art-body">
          <div class="art-meta"><span>{{ art.categoryName }}</span><span>{{ art.designerName }}</span></div>
          <h3>{{ art.title }}</h3>
          <p>{{ art.subtitle || art.story }}</p>
          <div class="art-foot"><b>¥{{ Number(art.minPrice || 0).toFixed(2) }} 起</b><small>{{ art.skuCount }} 个SKU</small></div>
        </div>
      </article>
    </section>

    <div class="table-card sku-card">
      <div class="panel-title">可售文创SKU</div>
      <table>
        <thead><tr><th>商品</th><th>关联IP</th><th>材质/尺寸</th><th>价格</th><th>库存</th><th>状态</th><th>操作</th></tr></thead>
        <tbody>
          <tr v-for="sku in filteredSkus" :key="sku.id">
            <td><div class="sku-name"><img :src="sku.coverUrl"/><div><strong>{{ sku.productName }}</strong><span>{{ sku.skuCode }} · {{ sku.productType }}</span></div></div></td>
            <td>{{ sku.artworkTitle }}</td>
            <td>{{ sku.material }} / {{ sku.size }}</td>
            <td>¥{{ Number(sku.price).toFixed(2) }}</td>
            <td>{{ sku.stock }}</td>
            <td><span class="badge ok">{{ sku.status }}</span></td>
            <td><button class="btn btn-secondary btn-sm" disabled title="商品支付通道尚未配置">商品支付待接入</button></td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.creative-toolbar { padding: 0; margin-bottom: 18px; }
.search-input.plain { padding-left: 12px; }
.select { height:36px; border:1px solid var(--c-border); border-radius:var(--r); background:#fff; padding:0 10px; }
.artwork-grid { display:grid; grid-template-columns: repeat(auto-fill, minmax(260px,1fr)); gap:18px; margin-bottom:24px; }
.art-card { background:#fff; border:1px solid var(--c-border); border-radius:18px; overflow:hidden; box-shadow:var(--shadow-sm); }
.art-img { width:100%; height:175px; object-fit:cover; display:block; }
.art-body { padding:16px; }
.art-meta { display:flex; justify-content:space-between; color:#f97316; font-size:12px; font-weight:700; margin-bottom:8px; }
.art-body h3 { margin:0 0 8px; font-size:18px; }
.art-body p { min-height:38px; margin:0 0 14px; color:var(--c-text-2); font-size:13px; line-height:1.5; }
.art-foot { display:flex; justify-content:space-between; align-items:center; }
.art-foot b { color:#dc2626; font-size:18px; }
.art-foot small { color:var(--c-text-2); }
.sku-card { padding: 0 0 8px; }
.panel-title { padding:16px; font-weight:700; border-bottom:1px solid var(--c-border); }
table { width:100%; border-collapse:collapse; }
th,td { padding:12px 14px; border-bottom:1px solid #eef2f7; text-align:left; font-size:13px; }
th { color:var(--c-text-2); background:#f8fafc; }
.sku-name { display:flex; gap:10px; align-items:center; }
.sku-name img { width:54px; height:40px; object-fit:cover; border-radius:8px; }
.sku-name span { display:block; color:var(--c-text-2); font-size:12px; margin-top:3px; }
.badge { padding:3px 8px; border-radius:999px; font-size:12px; }
.badge.ok { background:#dcfce7; color:#166534; }
</style>
