import { computed, ref, type ComputedRef, type Ref } from 'vue'
import { getSelectionOptions, type SelectionOption } from '../api/selection'
import {
  resolveCreativeProductProfile,
  type CreativeProductLike,
} from '../utils/creativeEngine'

/**
 * Product data consumed by the conversation page.
 *
 * The API exposes a selection option, while the page needs a display mark,
 * material swatch and a normalized description. Keeping that adapter here
 * means the page does not need to know the catalog response shape.
 */
export interface CatalogMaterialOption {
  name: string
  note: string
  color: string
}

export interface CatalogProductOption extends CreativeProductLike {
  key: string
  name: string
  mark: string
  desc: string
  process: string
  categoryKey: string
  categoryName: string
  materials: CatalogMaterialOption[]
  /** Catalog specification used by the local recommend-size fallback. */
  specification?: string
  recommendedSize?: string
}

// Short aliases keep the migration of existing page code mechanical while the
// catalog-specific names remain available to other entry points.
export type ProductOption = CatalogProductOption
export type MaterialOption = CatalogMaterialOption

export interface ProductCatalogCategory {
  key: string
  name: string
}

export interface ProductCatalogOptions {
  /** Number of rows requested from the selection endpoint. */
  fetchSize?: number
  initialProducts?: CatalogProductOption[]
  onError?: (error: unknown) => void
}

export const PRODUCT_CATEGORY_LABELS: Record<string, string> = {
  food: '食品饮品',
  stationery: '文具纸品',
  souvenir: '景区文创',
  accessory: '饰品挂件',
  craft: '工艺收藏',
  daily: '日用生活',
  tableware: '餐饮器物',
  toy: '潮玩玩具',
  apparel: '服饰配件',
  precious: '贵金属',
}

export const PRODUCT_CATEGORY_ORDER = [
  'food',
  'stationery',
  'souvenir',
  'accessory',
  'craft',
  'daily',
  'tableware',
  'toy',
  'apparel',
  'precious',
]

function productMark(name: string, category: string) {
  if (name.includes('冰箱贴')) return '贴'
  if (name.includes('徽章')) return '章'
  if (name.includes('钥匙扣')) return '扣'
  if (name.includes('书签')) return '签'
  if (name.includes('杯')) return '杯'
  if (name.includes('包') || name.includes('袋')) return '包'
  if (name.includes('公仔')) return '偶'
  if (name.includes('首饰') || name.includes('项链') || name.includes('耳')) return '饰'
  return ({
    food: '食',
    stationery: '文',
    daily: '用',
    toy: '玩',
    tableware: '器',
    souvenir: '礼',
    accessory: '饰',
    apparel: '衣',
    craft: '艺',
    precious: '金',
  } as Record<string, string>)[category] || '作'
}

function materialColor(material: string) {
  if (/金属|合金|贵金属|马口铁|金箔|溅射金/.test(material)) return 'linear-gradient(145deg,#ead29d,#8a6a45)'
  if (/陶瓷|骨瓷|琉璃|玻璃|搪瓷/.test(material)) return 'linear-gradient(145deg,#fffdf3,#a7c8ba)'
  if (/亚克力|PC|PVC|ABS|硅胶|塑胶|树脂|搪胶/.test(material)) return 'linear-gradient(145deg,#f4fbfc,#97c2c7)'
  if (/毛绒|布艺|帆布|棉|毛毡|纤维|涤纶/.test(material)) return 'linear-gradient(145deg,#f4e7d5,#bc9776)'
  if (/木|竹|纸|杜邦/.test(material)) return 'linear-gradient(145deg,#f1e2c8,#a9835b)'
  return 'linear-gradient(145deg,#e7ece4,#91aa9a)'
}

/** Converts one API selection row into the stable shape used by the UI. */
export function productFromSelection(option: SelectionOption): CatalogProductOption {
  const categoryName = PRODUCT_CATEGORY_LABELS[option.categoryKey] || option.categoryName || '其他'
  return {
    key: option.optionKey,
    name: option.name,
    mark: productMark(option.name, option.categoryKey),
    desc: option.subtitle || option.description,
    process: option.process,
    categoryKey: option.categoryKey,
    categoryName,
    materials: [{
      name: option.material,
      note: `${option.process} · ${option.specification}`,
      color: materialColor(option.material),
    }],
    specification: option.specification,
    recommendedSize: normalizeRecommendedSpecification(option.specification) || undefined,
  }
}

/**
 * Catalog specs can be capacities, ranges or pack counts, not only physical
 * dimensions. Keep those values as recommendation data when they are useful.
 */
export function normalizeRecommendedSpecification(value: unknown) {
  const normalized = String(value || '').trim().replace(/\s+/g, ' ')
  if (!normalized || /^(随型|定制|按规格|参考产品册|短袖常规尺码)$/i.test(normalized)) return ''
  const hasStandardPaperSize = /\bA[3-6]\b/i.test(normalized)
  const hasNumericSpec = /\d(?:\.\d+)?/.test(normalized)
  const hasPhysicalOrCapacityUnit = /(?:mm|毫米|cm|厘米|mL|毫升|ml|g|克|kg|公斤|个|支|块|袋|盒|套|片|粒|根|英寸|in)/i.test(normalized)
  if (!hasStandardPaperSize && !(hasNumericSpec && hasPhysicalOrCapacityUnit)) return ''
  return normalized.length > 120 ? normalized.slice(0, 120) : normalized
}

function productFormRecommendation(product: CatalogProductOption | null | undefined) {
  if (!product) return ''
  return resolveCreativeProductProfile({
    product: product as CreativeProductLike,
    productKey: product.key,
    productCategory: product.categoryName || product.categoryKey,
    productType: product.name,
  }).recommendedSize || ''
}

/** Product-name and category fallbacks used when the catalog has no spec. */
export function defaultLocalSizeForProduct(product: CatalogProductOption | null | undefined, includeCategoryDefault = true) {
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
  if (name.includes('冰淇淋') || name.includes('冰激凌')) return '成品约 80×45×12mm，天然实木棒 100-120mm'
  if (name.includes('公仔') || name.includes('潮玩') || /毛绒玩具|毛绒公仔|毛绒娃娃|毛绒玩偶|布偶/.test(name)) return '高 130mm'
  if (name.includes('杯垫')) return '100×100×5mm'
  if (name.includes('马克杯')) return '直径 80mm、高 95mm'
  if (name.includes('保温杯') || name.includes('随行杯')) return '直径 70mm、高 200mm'
  if ((name.includes('帆布') && name.includes('包')) || name.includes('手提袋')) return '350×300×100mm'
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

/** Returns the same local recommendation used by the conversation flow. */
export function localRecommendedProductSize(product: CatalogProductOption | null | undefined) {
  if (!product) return defaultLocalSizeForProduct(null)
  // Product-form profiles are authoritative for known carriers (for example a
  // bottle or postcard). Catalog capacities and pack counts remain useful when
  // no specific form profile is available.
  const formRecommendation = productFormRecommendation(product)
  if (formRecommendation) return formRecommendation
  const catalogSpecification = normalizeRecommendedSpecification(product.recommendedSize || product.specification)
  if (catalogSpecification) return catalogSpecification
  return defaultLocalSizeForProduct(product, false) || productFormRecommendation(product) || defaultLocalSizeForProduct(product)
}

export function catalogSpecificationHint(product: CatalogProductOption | null | undefined, selectedSize: string) {
  const catalog = normalizeRecommendedSpecification(product?.specification || product?.recommendedSize)
  if (!catalog || catalog === selectedSize) return ''
  // A physical-form profile owns geometry. Preserve only capacity, weight and
  // pack-count data so a legacy catalog dimension cannot conflict with it.
  if (productFormRecommendation(product) && /(?:mm|毫米|cm|厘米|英寸|in|×|x|直径|高度|宽|高|厚)/i.test(catalog)) return ''
  return catalog
}

function categoryList(products: readonly CatalogProductOption[]): ProductCatalogCategory[] {
  const names = new Map<string, string>()
  products.forEach(item => names.set(item.categoryKey, PRODUCT_CATEGORY_LABELS[item.categoryKey] || item.categoryName || '其他'))
  return Array.from(names.entries())
    .map(([key, name]) => ({ key, name }))
    .sort((left, right) => {
      const leftIndex = PRODUCT_CATEGORY_ORDER.indexOf(left.key)
      const rightIndex = PRODUCT_CATEGORY_ORDER.indexOf(right.key)
      return (leftIndex < 0 ? 999 : leftIndex) - (rightIndex < 0 ? 999 : rightIndex)
        || left.name.localeCompare(right.name, 'zh-CN')
    })
}

/** Owns only catalog state; selection side effects remain with the page/chat flow. */
export function useProductCatalog(options: ProductCatalogOptions = {}) {
  const productOptions = ref<CatalogProductOption[]>(options.initialProducts ? [...options.initialProducts] : [])
  const productKeyword = ref('')
  const productCategory = ref('')
  const catalogLoading = ref(false)
  const catalogError = ref<unknown | null>(null)

  const productCatalogCategories = computed(() => categoryList(productOptions.value))
  const filteredProductOptions = computed(() => {
    const keyword = productKeyword.value.trim().toLowerCase()
    return productOptions.value.filter(item => {
      if (productCategory.value && item.categoryKey !== productCategory.value) return false
      if (!keyword) return true
      return `${item.name} ${item.desc} ${item.process} ${item.materials.map(material => material.name).join(' ')}`
        .toLowerCase()
        .includes(keyword)
    })
  })

  async function loadProductCatalog() {
    if (catalogLoading.value) return productOptions.value
    catalogLoading.value = true
    catalogError.value = null
    try {
      const optionsFromApi = await getSelectionOptions({ size: options.fetchSize || 300 })
      productOptions.value = (Array.isArray(optionsFromApi) ? optionsFromApi : []).map(productFromSelection)
      return productOptions.value
    } catch (error) {
      catalogError.value = error
      options.onError?.(error)
      return []
    } finally {
      catalogLoading.value = false
    }
  }

  function updateProductKeyword(value: string | { detail?: { value?: unknown } } | unknown) {
    if (typeof value === 'string') {
      productKeyword.value = value
      return
    }
    const eventValue = (value as { detail?: { value?: unknown } } | null)?.detail?.value
    productKeyword.value = String(eventValue || '')
  }

  function setProductCategory(category: string) {
    productCategory.value = String(category || '')
  }

  function clearProductFilters() {
    productKeyword.value = ''
    productCategory.value = ''
  }

  function productCountForCategory(categoryKey: string) {
    return productOptions.value.filter(item => item.categoryKey === categoryKey).length
  }

  function productByValue(productType?: string, productKey?: string) {
    return productOptions.value.find(item => item.key === productKey || item.name === productType) || null
  }

  return {
    productOptions: productOptions as Ref<CatalogProductOption[]>,
    productKeyword,
    productCategory,
    catalogLoading,
    catalogError,
    productCatalogCategories: productCatalogCategories as ComputedRef<ProductCatalogCategory[]>,
    filteredProductOptions: filteredProductOptions as ComputedRef<CatalogProductOption[]>,
    loadProductCatalog,
    updateProductKeyword,
    setProductCategory,
    clearProductFilters,
    productCountForCategory,
    productByValue,
  }
}
