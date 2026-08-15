/**
 * C 端产品与材质的唯一注册表。
 *
 * 这里的字段不仅用于界面展示，还会写入图片/3D 生成提示词、生产初筛和作品
 * 元数据。新增材质时应只改本文件，避免首页和创作页出现不同的材质名称或工艺语义。
 */
export type Tripo3dPromptTemplate = 'universal' | 'collectible' | 'oriental' | 'plush_toy' | 'ppc_precision'

export type MaterialKey =
  | 'pvc'
  | 'tangjiao'
  | 'soft_vinyl'
  | 'resin'
  | 'metal'
  | 'ceramic'
  | 'plush'
  | 'short_plush'
  | 'ultra_plush'
  | 'ppc'
  | 'abs'
  | 'acrylic'
  | 'paper'
  | 'wood'

export type ProductCategoryKey = 'magnet' | 'stationery' | 'plush' | 'pvc_figure' | 'hard_plastic' | 'keychain' | 'gift_box'

export interface MaterialDefinition {
  key: MaterialKey
  name: string
  /** 常见俗称，仅用于界面解释与旧草稿兼容；提交后仍保存规范材质名。 */
  aliases?: string[]
  /** H5 PBR 实验室和 Tripo 提示词使用的视觉材质名。 */
  modelLabel: string
  /** 面向用户的短说明。 */
  short: string
  /** Tripo 可识别的 PBR 表面说明。 */
  modelPrompt: string
  swatch: string
}

export interface ProductCategoryDefinition {
  key: ProductCategoryKey
  label: string
  short: string
  mark: string
  description: string
  materialKeys: MaterialKey[]
  defaultTemplate: Tripo3dPromptTemplate
}

export const materialCatalog: Record<MaterialKey, MaterialDefinition> = {
  pvc: {
    key: 'pvc', name: 'PVC', short: '量产塑胶', modelLabel: 'PVC 潮玩',
    modelPrompt: 'premium PVC vinyl, smooth satin molded surface, rounded safe edges, clear parting lines, durable painted details, 8k PBR, no fabric',
    swatch: 'linear-gradient(145deg,#f4e9dc,#d08c70 48%,#f7f1e7 52%,#b85e4e)',
  },
  tangjiao: {
    key: 'tangjiao', name: '搪胶', aliases: ['糖胶', '糖膠'], short: '软触潮玩（糖胶）', modelLabel: '搪胶软胶',
    modelPrompt: 'soft vinyl tangjiao collectible, warm matte rubberized touch, rounded hollow form, subtle mold seams, soft-touch painted finish, 8k PBR, no hard metal',
    swatch: 'linear-gradient(145deg,#f4d9c4,#ce7c62 54%,#90554d)',
  },
  soft_vinyl: {
    key: 'soft_vinyl', name: '软胶', short: '柔韧包胶', modelLabel: '柔韧软胶',
    modelPrompt: 'flexible soft vinyl, gentle rubberized matte surface, smooth rounded contours, durable soft-touch coating, clean molded details, 8k PBR',
    swatch: 'linear-gradient(145deg,#e9ddd1,#9a8575 48%,#c5ad98)',
  },
  resin: {
    key: 'resin', name: '树脂', short: '潮玩摆件', modelLabel: '树脂潮玩',
    modelPrompt: 'premium cast resin collectible, velvety low-gloss finish, crisp painted details, gentle rounded surfaces, fine sculpted relief, 8k PBR material',
    swatch: 'radial-gradient(circle at 30% 25%,#fff 0 13%,transparent 14%),linear-gradient(145deg,#dce5db,#789488)',
  },
  metal: {
    key: 'metal', name: '金属', short: '徽章/五金', modelLabel: '金属质感',
    modelPrompt: 'brushed bronze metal, restrained antique metallic luster, refined engraved relief, rounded safe edges, premium museum souvenir finish, 8k PBR',
    swatch: 'linear-gradient(145deg,#e2c88f,#8b7655 47%,#d0b373 51%,#514336)',
  },
  ceramic: {
    key: 'ceramic', name: '陶瓷釉面', short: '温润器物', modelLabel: '陶瓷釉面',
    modelPrompt: 'glazed ceramic, smooth glossy glaze, subtle kiln texture, gentle translucent highlights, refined relief details, 8k PBR',
    swatch: 'linear-gradient(145deg,#f5f1df,#a8c5b8 48%,#e5d4c4)',
  },
  plush: {
    key: 'plush', name: '全毛绒', short: '填充玩偶', modelLabel: '全毛绒',
    modelPrompt: 'soft premium plush toy fabric, dense short-pile faux fur, padded stuffed volume, subtle seams, embroidered details, no glossy plastic, 8k PBR',
    swatch: 'repeating-linear-gradient(50deg,#f1e7d9 0 3rpx,#d7c1a7 3rpx 6rpx,#f6ede2 6rpx 9rpx)',
  },
  short_plush: {
    key: 'short_plush', name: '短毛绒', short: '短密绒面', modelLabel: '短毛绒',
    modelPrompt: 'short-pile plush fabric, dense velvety fibers, soft padded toy volume, fine embroidery and stitching, warm matte textile finish, no plastic',
    swatch: 'repeating-linear-gradient(135deg,#ead8c2 0 2rpx,#cfac86 2rpx 4rpx,#f4e6d2 4rpx 6rpx)',
  },
  ultra_plush: {
    key: 'ultra_plush', name: '超柔绒', short: '亲肤柔软', modelLabel: '超柔绒',
    modelPrompt: 'ultra-soft minky plush fabric, silky fine pile, pillowy stuffed volume, gentle soft highlights, embroidered facial details, no rigid plastic',
    swatch: 'linear-gradient(145deg,#fff1e7,#d8b8a0 52%,#f4d9c7)',
  },
  ppc: {
    key: 'ppc', name: 'PPC', short: '精密硬塑', modelLabel: 'PPC 高精硬塑',
    modelPrompt: 'precision injection-molded PPC polymer, high-density satin engineering plastic, crisp parting lines, subtle micro orange-peel texture, tight seams, accurate small details, premium non-glossy polymer, 8k PBR, no fabric or fur',
    swatch: 'linear-gradient(145deg,#dce3e1,#89a1a0 48%,#eef1ed 52%,#58757a)',
  },
  abs: {
    key: 'abs', name: 'ABS', short: '工程硬塑', modelLabel: 'ABS 工程硬塑',
    modelPrompt: 'precision injection-molded ABS engineering plastic, durable satin finish, crisp chamfers, clean parting lines, tight assembly seams, fine molded relief, 8k PBR',
    swatch: 'linear-gradient(145deg,#f2e8d8,#b6a997 48%,#796e64)',
  },
  acrylic: {
    key: 'acrylic', name: '亚克力', short: '透明挂件', modelLabel: '透明亚克力',
    modelPrompt: 'transparent polished acrylic, clear glossy surface, bright beveled edges, clean laser-cut silhouette, subtle internal reflection, 8k PBR',
    swatch: 'linear-gradient(135deg,rgba(255,255,255,.95),rgba(155,198,203,.7) 47%,rgba(255,225,202,.75) 52%,rgba(255,255,255,.9))',
  },
  paper: {
    key: 'paper', name: '纸质', short: '礼赠包装', modelLabel: '纸质礼盒',
    modelPrompt: 'premium textured art paper packaging, refined matte coating, crisp folded edges, subtle foil-stamped details, tactile museum gift-box finish, 8k PBR',
    swatch: 'repeating-linear-gradient(0deg,#efe3ce 0 2rpx,#e2d0b7 2rpx 3rpx,#f6eddf 3rpx 5rpx)',
  },
  wood: {
    key: 'wood', name: '木质', short: '自然木作', modelLabel: '木质温润',
    modelPrompt: 'natural warm wood, fine visible grain, matte handcrafted finish, gentle rounded edges, engraved cultural details, 8k PBR',
    swatch: 'repeating-linear-gradient(65deg,#be8f61 0 2rpx,#e5c59e 2rpx 5rpx,#8f663f 5rpx 7rpx)',
  },
}

export const productCategories: ProductCategoryDefinition[] = [
  { key: 'magnet', label: '冰箱贴', short: '轻量纪念品', mark: '贴', description: '背面预留平整磁铁位，适合用清晰轮廓与浅浮雕讲出馆藏故事。', materialKeys: ['pvc', 'tangjiao', 'resin', 'metal', 'ceramic'], defaultTemplate: 'oriental' },
  { key: 'stationery', label: '文具纸品', short: '书签/明信片', mark: '签', description: '适合书签、明信片与纸质礼赠；图案会按真实裁切边、印刷出血与平面工艺组织。', materialKeys: ['paper', 'metal', 'acrylic', 'wood'], defaultTemplate: 'oriental' },
  { key: 'plush', label: '毛绒玩具', short: '亲子潮玩', mark: '绒', description: '优先保证圆润填充、刺绣五官和可落地的裁片与缝线设计。', materialKeys: ['plush', 'short_plush', 'ultra_plush'], defaultTemplate: 'plush_toy' },
  { key: 'pvc_figure', label: 'PVC / 搪胶公仔', short: '收藏潮玩', mark: '偶', description: '适合角色化设计；生成时会优先考虑圆角、分件和量产涂装区域。', materialKeys: ['pvc', 'tangjiao', 'soft_vinyl', 'resin'], defaultTemplate: 'collectible' },
  { key: 'hard_plastic', label: '硬塑摆件', short: '精密陈列', mark: '塑', description: '针对注塑分件、壁厚、凹槽和浅浮雕细节做更稳妥的建模方向。', materialKeys: ['ppc', 'abs', 'resin'], defaultTemplate: 'ppc_precision' },
  { key: 'keychain', label: '钥匙扣', short: '随身配饰', mark: '扣', description: '挂孔、边缘和连接位会作为工艺重点，适合做轻量高辨识文创。', materialKeys: ['acrylic', 'pvc', 'metal', 'wood'], defaultTemplate: 'oriental' },
  { key: 'gift_box', label: '礼盒', short: '礼赠陈列', mark: '盒', description: '强调开合结构、纹样布局与材质细节，适合形成完整送礼体验。', materialKeys: ['paper', 'wood', 'metal', 'ceramic'], defaultTemplate: 'oriental' },
]

/**
 * 固定顺序的完整材质目录。页面默认只展示当前产品的量产推荐，但用户可以随时
 * 展开此目录选择任一材质，用于概念验证或与工艺团队进一步确认。
 */
export const materialList: MaterialDefinition[] = [
  materialCatalog.pvc,
  materialCatalog.tangjiao,
  materialCatalog.soft_vinyl,
  materialCatalog.resin,
  materialCatalog.metal,
  materialCatalog.ceramic,
  materialCatalog.plush,
  materialCatalog.short_plush,
  materialCatalog.ultra_plush,
  materialCatalog.ppc,
  materialCatalog.abs,
  materialCatalog.acrylic,
  materialCatalog.paper,
  materialCatalog.wood,
]

/** 兼容旧草稿、旧模型标签和“糖胶”等用户常用称呼。 */
export function findMaterialDefinition(materialName?: string, modelMaterial?: string): MaterialDefinition | null {
  const values = [materialName, modelMaterial].filter(Boolean).map(value => String(value).trim())
  if (!values.length) return null
  return materialList.find((material) => values.some(value => (
    material.name === value
    || material.modelLabel === value
    || material.aliases?.includes(value)
  ))) || null
}

export function isRecommendedMaterial(product: ProductCategoryDefinition, material: MaterialDefinition): boolean {
  return product.materialKeys.includes(material.key)
}
