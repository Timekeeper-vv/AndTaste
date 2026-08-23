/**
 * Runtime-safe creative engine used by the mini-program.
 *
 * Keep this module self-contained. The WeChat developer tool rebuilds a
 * watched output directory one file at a time; a dynamic require of another
 * engine module can then resolve to an empty CommonJS record. This module is
 * the complete mini-program contract and must not depend on a second runtime
 * file being present or current.
 */

export interface CreativeProductLike {
  key?: string
  name?: string
  label?: string
  categoryKey?: string
  categoryName?: string
  description?: string
  materials?: Array<{ name?: string }>
}

export interface CreativeProductProfile {
  key: string
  prompt: string
  negative: string
  recommendedSize?: string
}

export type CreativePromptPurpose = 'text' | 'reference' | 'multiview' | 'refinement'

export interface CreativeEngineInput {
  product?: CreativeProductLike | null
  productKey?: string
  productCategory?: string
  productType?: string
  material?: string
  productSize?: string
  prompt?: string
  rawPrompt?: string
  inputAssetId?: number | string | null
  refinement?: boolean
  refinementNote?: string
  seed?: number | string | null
  purpose?: CreativePromptPurpose
  optimizedPrompt?: string
}

export interface CreativeImageRequest extends Record<string, unknown> {
  prompt: string
  rawPrompt: string
  negativePrompt: string
  productKey: string
  productCategory: string
  productType: string
  material: string
  productSize: string
  inputAssetId: number | null
  refinement: boolean
  refinementNote: string
  seed?: number | string | null
  productForm: string
  creativeEngineVersion: string
}

const FALLBACK_ENGINE_VERSION = 'miniapp-creative-engine-runtime-fallback-v1'
const ROLE = '【角色】你是专业产品设计师 + AI 图像工程师，正在为电商平台制作真实、可量产、可打样的文创产品主图。'
const REFERENCE_RULE = '【参考图转化原则】上传参考图只提供主体、轮廓、颜色、纹样和文化识别点；必须改变原始载体、原始场景和原始画面用途，把这些视觉元素重构到目标产品上，不得原图不变。'
const NEGATIVE = 'phone screenshot, smartphone, mobile screen, app interface, status bar, media player, raw screenshot, unchanged reference image, near duplicate, flat poster, label-only artwork, tiny isolated motif, cropped product, incomplete product, excessive empty background, unrelated object, external watermark'

const FALLBACK_PROFILES: Array<{ match: RegExp; profile: CreativeProductProfile }> = [
  {
    match: /冰淇淋|冰激凌|ice\s*cream|gelato|frozen\s*dessert/i,
    profile: {
      key: 'ice_cream',
      recommendedSize: '成品约 80×45×12mm，天然实木棒 100-120mm',
      prompt: '标准化、可食用、可打样的 2.5D 浮雕冰淇淋冰棒；单个完整扁平轮廓，正面有清晰浅浮雕和细腻食品级霜冻纹理，底部有天然实木棒。',
      negative: 'ice cream cone, ice cream cup, scoop, sundae, metal body, plastic figurine, resin statue, packaging mockup, missing stick, detached stick, extra products, text, logo',
    },
  },
  {
    match: /抱枕|靠垫|cushion|pillow/i,
    profile: {
      key: 'cushion',
      recommendedSize: '40×40cm 或按主体异形轮廓定制',
      prompt: '完整可拥抱的异形抱枕；把参考主体外轮廓裁成柔软填充形体，展示布料裁片、包边、缝线、填充厚度和印花/刺绣。',
      negative: 'hard statue, metal body, ceramic object, flat poster, missing pillow volume, tiny motif only',
    },
  },
  {
    match: /毛绒|布偶|plush|stuffed|soft\s*toy/i,
    profile: {
      key: 'plush',
      recommendedSize: '高约 130mm',
      prompt: '完整立体填充毛绒玩具；使用布料裁片、柔软填充体积、缝线、短绒或超柔绒面、刺绣五官和安全软体结构。',
      negative: 'flat illustration, flat poster, hard plastic shell, glossy hard surface, metal body, tiny motif only',
    },
  },
  {
    match: /钥匙扣|key\s*chain|keychain/i,
    profile: {
      key: 'keychain',
      recommendedSize: '50×50×4mm（主体，含挂环另计）',
      prompt: '完整可随身使用的钥匙扣；主体有清晰轮廓、耐用厚度、圆角、挂孔和连接环/链条，参考主体占据成品主要面积。',
      negative: 'flat label sheet, poster, missing hanging hole, missing ring, fragile thin edge, tiny motif only',
    },
  },
  {
    match: /冰箱贴|磁贴|magnet/i,
    profile: {
      key: 'magnet',
      recommendedSize: '60×60×4mm',
      prompt: '完整掌心尺寸的冰箱贴；正面是清晰文化图形或浅浮雕，边缘有合理厚度和圆角，背面有平整稳定的磁铁粘贴位。',
      negative: 'flat poster, paper-only card, missing magnetic backing, oversized sculpture, tiny motif only',
    },
  },
  {
    match: /明信片|书签|贴纸|笔记本|本册|postcard|bookmark|sticker|notebook/i,
    profile: {
      key: 'paper_stationery',
      recommendedSize: 'A6（105×148mm）',
      prompt: '完整可生产的纸品/文具成品；明确纸张厚度、裁切边、折叠或装订结构，参考主体落实为可印刷、烫金、压凹凸或覆膜的主要视觉。',
      negative: 'phone screenshot, flat poster only, missing paper edges, oversized 3D sculpture, tiny motif only',
    },
  },
  {
    match: /潮玩|公仔|手办|pvc|搪胶|树脂摆件|collectible|figure/i,
    profile: {
      key: 'collectible_toy',
      recommendedSize: '高约 130mm',
      prompt: '完整立体可量产的潮玩/公仔；明确头身比例、稳定底部、分件、连接位、圆角和真实涂装区域，参考主体成为玩具轮廓与主要装饰。',
      negative: 'flat illustration, flat poster, plush fabric, missing body or base, melted geometry, tiny isolated motif only',
    },
  },
  {
    match: /礼盒|包装盒|gift\s*box|box/i,
    profile: {
      key: 'gift_box',
      recommendedSize: '200×150×80mm',
      prompt: '完整可生产的文创礼盒；展示盒体、开合结构、材料厚度、裁切折线、内衬和真实装配关系，参考元素作为盒面主要视觉或表面工艺。',
      negative: 'flat poster only, missing box structure, impossible opening, no thickness, tiny motif only',
    },
  },
]

const CATEGORY_PROFILES: Record<string, CreativeProductProfile> = {
  magnet: { key: 'magnet', recommendedSize: '60×60×4mm', prompt: '完整掌心尺寸的冰箱贴成品；正面是清晰文化图形或浅浮雕，边缘有合理厚度和圆角，背面预留平整稳定的磁铁位。', negative: 'flat poster, paper-only card, missing magnetic backing, oversized sculpture, tiny motif only' },
  stationery: { key: 'paper_stationery', recommendedSize: 'A5（148×210mm）', prompt: '完整可生产的纸品/文具成品；明确纸张厚度、裁切边、折叠/装订/夹持结构，参考元素落实为印刷、烫金、压凹凸或覆膜工艺。', negative: 'phone screenshot, flat poster only, missing paper edges, oversized 3D sculpture, tiny motif only' },
  plush: { key: 'plush', recommendedSize: '高约 130mm', prompt: '完整立体填充毛绒玩具；使用布料裁片、柔软填充体积、缝线、短绒或超柔绒面、刺绣细节和安全软体结构。', negative: 'flat illustration, flat poster, hard plastic shell, glossy hard surface, metal body, tiny motif only' },
  pvc_figure: { key: 'collectible_toy', recommendedSize: '高约 130mm', prompt: '完整立体可量产的 PVC、搪胶或树脂公仔；明确头身比例、稳定底部、分件、连接位、圆角和真实涂装区域。', negative: 'flat illustration, plush fabric, missing body or base, melted geometry, tiny motif only' },
  hard_plastic: { key: 'hard_plastic', recommendedSize: '150×150×200mm', prompt: '完整可量产的硬塑摆件；明确注塑分件、合理壁厚、圆角、凹槽、浅浮雕、紧密装配和稳定底座。', negative: 'plush fabric, soft stuffed toy, flat poster, missing base, impossible thin walls, floating parts' },
  keychain: { key: 'keychain', recommendedSize: '50×50×4mm（主体，含挂环另计）', prompt: '完整可随身使用的钥匙扣；主体有清晰轮廓、耐用厚度、圆角、挂孔和连接环或链条，参考主体占据成品主要面积。', negative: 'flat label sheet, poster, missing hanging hole, missing ring, fragile thin edge, tiny motif only' },
  gift_box: { key: 'gift_box', recommendedSize: '200×150×80mm', prompt: '完整可生产的文创礼盒；展示盒体、开合结构、材料厚度、裁切折线、内衬和真实装配关系，参考元素作为盒面主要视觉或表面工艺。', negative: 'flat poster only, missing box structure, impossible opening, no thickness, tiny motif only' },
  toy: { key: 'collectible_toy', recommendedSize: '高约 130mm', prompt: '完整立体可量产的潮玩/玩具；明确头身比例、稳定底部、分件、连接位和涂装表面。', negative: 'flat illustration, flat poster, missing body or base, melted geometry' },
  apparel: { key: 'apparel', recommendedSize: '按常用成人尺码', prompt: '完整可穿戴的服饰/配件；展示真实布料、裁片、缝线和佩戴结构，参考元素以印花、刺绣或提花落在表面。', negative: 'flat poster, floating garment graphic, missing garment structure, hard statue' },
  tableware: { key: 'tableware', recommendedSize: '直径约 80mm、高约 95mm', prompt: '完整可使用的餐饮器物；明确开口、容积、底部稳定性、合理壁厚和食品接触面。', negative: 'flat poster, missing opening, unstable base, abstract sculpture' },
  food: { key: 'food', recommendedSize: '按实际食品规格定制', prompt: '真实可食用的文创食品成品；使用食品级原料和可食用印花、压纹、糖霜或巧克力装饰。', negative: 'metal ornament, plastic statue, inedible decoration, flat label sheet' },
}

function clean(value: unknown, maxLength = 2400) {
  return String(value ?? '').trim().replace(/\s+/g, ' ').slice(0, maxLength)
}

function cleanPrompt(value: unknown, maxLength = 6000) {
  return String(value ?? '').replace(/\r\n?/g, '\n').split('\n').map(line => line.trim().replace(/[ \t]+/g, ' ')).join('\n').trim().slice(0, maxLength)
}

function productName(input: CreativeEngineInput) {
  return clean(input.product?.name || input.product?.label || input.productType || input.productCategory || '文创产品', 160)
}

function productKey(input: CreativeEngineInput) {
  return clean(input.product?.key || input.productKey || '', 120)
}

function context(input: CreativeEngineInput) {
  return [productKey(input), input.productType, input.product?.name, input.product?.label, input.product?.categoryKey, input.productCategory, ...(input.product?.materials || []).map(item => item.name)].filter(Boolean).join(' ')
}

function fallbackProfile(input: CreativeEngineInput): CreativeProductProfile {
  const match = FALLBACK_PROFILES.find(item => item.match.test(context(input)))?.profile
  if (match) return match
  const category = String(input.product?.categoryKey || input.productKey || input.productCategory || '').trim()
  return CATEGORY_PROFILES[category] || {
    key: 'general',
    prompt: '把主体重构为完整、可识别、可量产的真实实体文创产品，明确轮廓、功能结构、合理厚度、圆角和实际材质表面；参考图元素必须成为产品的主要视觉或结构细节。',
    negative: 'abstract pattern only, tiny isolated motif, flat poster, unclear product form, random material substitution',
  }
}

function fallbackSize(input: CreativeEngineInput, profile = fallbackProfile(input)) {
  if (input.productSize) return clean(input.productSize, 120)
  const name = context(input)
  if (/明信片|postcard/i.test(name)) return 'A6（105×148mm）'
  if (/书签|bookmark/i.test(name)) return '40×120×1.2mm'
  if (/贴纸|sticker/i.test(name)) return '50×50mm'
  if (/笔记本|本册|notebook|journal/i.test(name)) return 'A5（148×210mm）'
  if (/马克杯|咖啡杯|茶杯|mug/i.test(name)) return '直径 80mm、高 95mm'
  if (/保温杯|随行杯|tumbler|thermos/i.test(name)) return '直径 70mm、高 200mm'
  if (/帆布|手提袋|背包|bag|pouch/i.test(name)) return '350×300×100mm'
  return clean(profile.recommendedSize || '按产品实际规格', 120)
}

function fallbackReferencePrompt(supplement?: string) {
  const detail = clean(supplement, 1200)
  const ignored = /^(?:没有(?:具体)?灵感|无(?:具体)?灵感|没有补充|无补充|不用补充|我已上传(?:一张)?(?:灵感)?图片|已上传(?:一张)?灵感图片|上传灵感图片)[。.!！?？\s]*$/i.test(detail)
  return ignored || !detail ? '上传参考图中的主体、轮廓、颜色和文化识别元素。' : '上传参考图中的主体、轮廓、颜色和文化识别元素。用户补充方向：' + detail
}

function fallbackPrompt(input: CreativeEngineInput, purpose: 'text' | 'reference' | 'multiview' | 'refinement', profile: CreativeProductProfile) {
  const product = productName(input)
  const material = clean(input.material || '适合该产品的制造材质', 160)
  const size = fallbackSize(input, profile)
  const source = cleanPrompt(input.optimizedPrompt || input.prompt || input.rawPrompt || ('为' + product + '设计一套适合量产打样的产品视觉'), 6000)
  if (purpose === 'multiview') {
    return ['【任务】基于输入的' + product + '产品图，生成同一产品的正面、侧面和背面视图。', '【一致性规则】保持同一产品身份、轮廓、比例、材质、颜色、纹样、结构和尺寸，不新增、不删减、不改造产品形态。', '【产品形态】' + profile.prompt, '【制造参数】材质为「' + material + '」；成品规格为「' + size + '」。', '【输出规则】只生成产品设计视图，不保留原图场景、手机/UI、截图边框或无关物体。', '【用户方向】' + source].join('\n')
  }
  if (profile.key === 'ice_cream') {
    return [ROLE, '【任务】把' + (purpose === 'reference' || purpose === 'refinement' ? '上传参考图中的主体和文化元素' : '用户描述的主体和文化元素') + '重构为一支标准化、可食用、可打样的 2.5D 文创冰淇淋。', 'Isometric view of a 2.5D cultural creative ice cream, main subject and cultural elements: [' + source + '], intricate relief embossed details, subtle texture, food-safe matte frosted frozen-dessert material, preserve user colors, clean pure white background, minimalist product photography, one complete popsicle-shaped product, portrait 3:4 composition, bottom inserted with a 100-120mm natural solid wood stick, neat food-safe connection, no text or logo, 8k.', '【强制规则】主体必须成为冰淇淋正面的主要浮雕轮廓和文化图案；不得变成蛋筒、杯装、冰淇淋球、包装盒或摆件。', '【制造参数】材质为「' + material + '」；成品规格为「' + size + '」。', REFERENCE_RULE, '【交付前自检】确认单个完整产品、清晰边缘、纯白背景、可见木棒和可生产结构全部成立。'].join('\n')
  }
  return [ROLE, '【任务】将' + (purpose === 'reference' || purpose === 'refinement' ? '上传参考图中的主体和核心视觉元素' : '用户提供的灵感') + '完全重构为一件真实的「' + product + '」成品，用于电商主图展示；不是对原图做轻微滤镜或简单贴图。', '【强制规则】', purpose === 'reference' || purpose === 'refinement' ? '1. ' + REFERENCE_RULE : '1. 用户灵感必须落实到目标产品的完整结构、材质和真实使用方式。', '2. 【产品形态】' + profile.prompt, '3. 一件完整成品居中，占画面约 75%，背景只能是纯白或浅灰，边缘清晰且留白克制。', '4. 【制造参数】材质为「' + material + '」；成品规格为「' + size + '」，用于约束实体比例和结构，不是图片分辨率。', '5. 禁止输出原图不变、手机截图、海报、平面标签稿、孤立小图案、无关物体。', '【交付前自检】确认目标产品形态、材质、完整轮廓、主体占比和成品规格全部成立。', '【用户补充方向】' + source].join('\n')
}

function fallbackRequest(input: CreativeEngineInput): CreativeImageRequest {
  const purpose = input.purpose || (input.refinement ? 'refinement' : Number(input.inputAssetId) > 0 ? 'reference' : 'text')
  const profile = fallbackProfile(input)
  const rawPrompt = cleanPrompt(input.rawPrompt || input.prompt || '', 6000)
  const inputAssetId = Number(input.inputAssetId)
  return {
    prompt: fallbackPrompt(input, purpose, profile),
    rawPrompt,
    negativePrompt: [NEGATIVE, profile.negative].filter(Boolean).join(', '),
    productKey: productKey(input),
    productCategory: clean(input.product?.categoryName || input.productCategory || input.product?.categoryKey || '文创产品', 160),
    productType: productName(input),
    material: clean(input.material, 160),
    productSize: fallbackSize(input, profile),
    inputAssetId: Number.isFinite(inputAssetId) && inputAssetId > 0 ? inputAssetId : null,
    refinement: input.refinement === true || purpose === 'refinement',
    refinementNote: cleanPrompt(input.refinementNote || (purpose === 'reference' ? fallbackPrompt(input, purpose, profile) : ''), 2400),
    seed: input.seed ?? null,
    productForm: profile.key,
    creativeEngineVersion: FALLBACK_ENGINE_VERSION,
  }
}

export function resolveCreativeProductProfile(input: CreativeEngineInput): CreativeProductProfile {
  return fallbackProfile(input)
}

export function creativeProductSize(input: CreativeEngineInput, profile = resolveCreativeProductProfile(input)) {
  return fallbackSize(input, profile)
}

export function buildReferenceRawPrompt(supplement?: string) {
  return fallbackReferencePrompt(supplement)
}

export function compileCreativeImageRequest(input: CreativeEngineInput): CreativeImageRequest {
  return fallbackRequest(input)
}
