/**
 * Shared client-side creative contract.
 *
 * Pages collect user input; this module turns that input into the same
 * product-aware Seedream request. The server remains the final source of
 * truth, but both miniapp entry points now send one deterministic brief.
 */

export const CREATIVE_ENGINE_VERSION = 'miniapp-creative-engine-v1'
export const CREATIVE_IMAGE_SIZE = '2K' as const

export const PRODUCT_ROLE_PROMPT = '【角色】你是专业产品设计师 + AI 图像工程师，正在为电商平台制作真实、可量产、可打样的文创产品主图。'
export const PRODUCT_REFERENCE_GUARD = '【参考图转化原则】上传参考图只提供主体、轮廓、颜色、纹样和文化识别点；必须改变原始载体、原始场景和原始画面用途，把这些视觉元素重构到目标产品上。主体要成为产品的主要视觉或结构，不得只贴一个小 logo，也不得原图不变。'
export const PRODUCT_FRAME_GUARD = '【构图规则】一件完整成品居中，占画面约 75%（允许 70%-80%），边缘留白不超过 10%；使用正方形或 4:5 电商商品摄影构图。背景只能是纯白或浅灰，不保留天空、山、云、建筑、原始场景或手机截图比例。'
export const PRODUCT_OUTPUT_NEGATIVE = 'phone screenshot, smartphone, mobile screen, app interface, status bar, media player, playback controls, progress bar, interface buttons, black UI frame, screen frame, phone frame, raw screenshot, unchanged reference image, near duplicate, collage, split screen, flat poster, flat design board, label sheet, label-only artwork, tiny isolated motif, floating logo, cropped product, incomplete product, excessive empty background, narrow portrait strip, yellow cast, sepia, monochrome wash, sky, cloud, mountain, landscape scenery, unrelated object, external watermark'
export const PRODUCT_SELF_CHECK = '【交付前自检】确认目标产品形态、材质、完整轮廓、主体占比、白/浅灰背景和成品规格全部成立；不满足时优先重新构建设计，不要输出原图、截图、海报或平面标签稿。'

/**
 * Stable raw brief for every reference-image entry point. The uploaded pixels
 * carry the subject identity; text is only a supplement. Keeping this brief
 * independent from page-specific product prose prevents a fixed carrier
 * template (for example the 2.5D ice-cream template) from nesting itself.
 */
export function buildReferenceRawPrompt(supplement?: string) {
  const candidate = String(supplement || '').trim()
  // Chat orchestration can echo workflow acknowledgements into the brief
  // (for example "没有灵感" or "已上传灵感图片"). They are not creative
  // direction and must not make an otherwise identical upload request differ
  // merely because the user reached the upload step through another reply.
  const detail = /^(?:没有(?:具体)?灵感(?:（?看看示例）?)?|无(?:具体)?灵感|没有补充|无补充|不用补充|我已上传(?:一张)?(?:灵感)?图片|已上传(?:一张)?灵感图片|上传灵感图片|以用户上传的参考图片主体、构图和可识别细节为创作依据)[。.!！?？\s]*$/i.test(candidate)
    ? ''
    : candidate
  return detail
    ? `上传参考图中的主体、轮廓、颜色和文化识别元素。用户补充方向：${detail}`
    : '上传参考图中的主体、轮廓、颜色和文化识别元素。'
}

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
  /** Optional Seedream seed for reproducible comparison runs. */
  seed?: number | string | null
  purpose?: CreativePromptPurpose
  /** Optional output from Qwen. It is treated as a candidate, never as a policy. */
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

const DEFAULT_PROFILE: CreativeProductProfile = {
  key: 'general',
  prompt: '把主体重构为完整、可识别、可量产的真实实体文创产品，明确产品轮廓、功能结构、合理厚度、圆角和实际材质表面；参考图元素必须成为产品的主要视觉或结构细节，而不是孤立的小图案。',
  negative: 'abstract pattern only, tiny isolated motif, flat poster, unfinished concept board, unclear product form, random material substitution',
}

const FORM_PROFILES: Array<{ match: RegExp; profile: CreativeProductProfile }> = [
  {
    match: /异形抱枕|抱枕|靠垫|cushion|pillow/i,
    profile: {
      key: 'cushion',
      recommendedSize: '40×40cm 或按主体异形轮廓定制',
      prompt: '完整可拥抱的异形抱枕；把参考主体的外轮廓裁成柔软填充形体，展示布料裁片、包边/滚边、缝线、填充厚度和真实印花、刺绣或热转印；主体必须占据抱枕主要面积，不能只是平面图案或硬质雕像。',
      negative: 'hard statue, metal body, ceramic object, flat poster, flat fabric swatch, missing pillow volume, sharp thin parts, tiny motif only',
    },
  },
  {
    match: /折叠雨伞|雨伞|伞面|umbrella/i,
    profile: {
      key: 'umbrella',
      recommendedSize: '伞面展开直径约960mm',
      prompt: '完整可使用的折叠雨伞；展示展开伞面、伞骨、伞杆、伞柄和安全圆角，参考主体转为伞面连续印花或局部图形，呈现真实防水涤纶材质和商品摄影，不输出一张平面海报。',
      negative: 'flat poster, missing canopy, missing ribs, missing handle, impossible floating fabric, statue, tiny motif only',
    },
  },
  {
    match: /文化主题毛巾|毛巾|浴巾|towel/i,
    profile: {
      key: 'towel',
      recommendedSize: '20×70cm 或 30×60cm',
      prompt: '完整可使用的织物毛巾；展示矩形/异形织物轮廓、吸水绒面、织造纹理、包边和真实垂坠，参考主体转为大面积提花、印花或刺绣，不输出纸张、塑料或雕塑。',
      negative: 'paper card, plastic sheet, metal object, statue, flat poster, missing textile pile, tiny motif only',
    },
  },
  {
    match: /主题杯垫|杯垫|coaster/i,
    profile: {
      key: 'coaster',
      recommendedSize: '直径80-120mm、厚约5mm',
      prompt: '完整可使用的杯垫；保持掌心尺寸的平面轮廓、真实厚度、圆角和防滑底面，参考主体成为正面印花、浅浮雕或激光雕刻主视觉，呈现木质、硅胶、皮革、石材或亚克力的选定材质。',
      negative: 'oversized sculpture, paper poster, missing thickness, missing anti-slip base, floating parts, tiny motif only',
    },
  },
  {
    match: /文化主题本册|打卡本|笔记本|本册|notebook|journal/i,
    profile: {
      key: 'notebook',
      recommendedSize: 'A5 或 A6',
      prompt: '完整可翻阅的主题本册/笔记本；展示封面、书脊、内页厚度、装订或活页结构和真实纸张表面，参考主体重构为封面主视觉、烫金、压凹凸或局部纹样，不输出一张孤立海报。',
      negative: 'flat poster only, loose paper pile, missing spine, missing page block, phone screenshot, tiny motif only',
    },
  },
  {
    match: /文件夹|folder/i,
    profile: {
      key: 'folder',
      recommendedSize: '22×15cm',
      prompt: '完整可使用的文件夹；展示前后封片、折痕、插袋开口、真实板材厚度和边缘，参考主体落在文件夹正面印刷或透明夹层中，不输出平面海报或无结构图案。',
      negative: 'flat poster only, loose card, missing fold, missing pocket, impossible thin structure, tiny motif only',
    },
  },
  {
    match: /中性笔|签字笔|笔夹|pen|ballpoint/i,
    profile: {
      key: 'pen',
      recommendedSize: '单支约140mm',
      prompt: '完整可书写的中性笔/签字笔；展示笔杆、笔夹、笔尖、按动或笔帽结构和真实塑胶/金属表面，参考主体转为笔杆印花、立体笔夹或局部软胶装饰，不输出独立平面图案。',
      negative: 'flat poster, missing tip, missing clip, broken pen body, oversized sculpture, tiny motif only',
    },
  },
  {
    match: /主题贴纸包|贴纸|sticker/i,
    profile: {
      key: 'sticker',
      recommendedSize: '50×50mm 单枚或按套装排版',
      prompt: '完整可生产的贴纸包；展示多枚有清晰裁切边、出血、背胶和离型纸的贴纸套装，参考主体拆解为主要贴纸图形并保持可辨识，不输出手机截图、平面海报或孤立小图标。',
      negative: 'phone screenshot, poster mockup, missing die-cut edges, loose logo only, blank sheet, tiny unreadable motif',
    },
  },
  {
    match: /叶雕灯|夜灯|灯具|lamp|light/i,
    profile: {
      key: 'lamp',
      recommendedSize: '约127×178mm',
      prompt: '完整可点亮的文创灯具/叶雕灯；展示灯体、透明或半透明雕刻板、底座、光源和稳定装配关系，参考主体转为板面镂空/雕刻图形，呈现真实发光效果和商品结构，不输出单张海报。',
      negative: 'flat poster, missing light source, missing base, impossible floating panel, random lamp shape, tiny motif only',
    },
  },
  {
    match: /邮票|stamp/i,
    profile: {
      key: 'stamp',
      recommendedSize: '约40×60mm',
      prompt: '完整可收藏的邮票成品或邮票封装；展示纸张、齿孔、完整边框、面值区域和清晰印刷图案，参考主体重构为邮票主图，不输出手机界面或无边界海报。',
      negative: 'phone screenshot, poster without perforation, missing stamp border, oversized 3D object, tiny motif only',
    },
  },
  {
    match: /保温杯|随行杯|tumbler|thermos/i,
    profile: {
      key: 'tumbler',
      recommendedSize: '直径约70mm、高约200mm',
      prompt: '完整可使用的保温杯/随行杯；展示杯盖、杯口、圆柱杯身、底部和真实防漏结构，参考主体作为杯身环绕印花、激光雕刻或贴花，呈现选定金属/塑胶材质，不输出平面海报。',
      negative: 'flat label sheet, missing lid, missing opening, paper card, sculpture, tiny motif only',
    },
  },
  {
    match: /文化主题\s*t?恤|T恤|短袖|t-shirt|tshirt|tee/i,
    profile: {
      key: 'tshirt',
      recommendedSize: '按常用成人尺码，图案保留安全边距',
      prompt: '完整可穿着的文化主题 T 恤；展示衣身、领口、袖口、下摆、真实棉/混纺布料和自然褶皱，参考主体转为大面积印花、刺绣或发泡工艺，保持可穿戴结构，不输出悬浮图案稿。',
      negative: 'flat garment graphic, missing sleeves, missing neckline, floating artwork, hard statue, tiny motif only',
    },
  },
  {
    match: /矿泉水|瓶装水|饮用水|水瓶|果汁|饮料|汽水|water\s*bottle|beverage|juice/i,
    profile: {
      key: 'bottle',
      recommendedSize: '500mL 圆柱瓶（直径约65mm，高约210mm）',
      prompt: '完整、直立、可识别的圆柱瓶实体；瓶底、瓶身、瓶肩、瓶口和瓶盖完整可见，瓶身有真实液体与环绕瓶标，参考图中的主体/核心视觉元素作为瓶标主视觉或瓶身大面积印花，不能缩成角落小 logo；展示 3/4 角度的真实商品摄影，保持可灌装、可量产结构。',
      negative: 'flat label sheet, flat poster, label-only artwork, tiny logo on a blank bottle, incomplete bottle, cropped bottle, box-only packaging, pouch, carton, wide flat package, yellow cast, sepia',
    },
  },
  {
    match: /毛绒钥匙扣|毛绒挂件/i,
    profile: {
      key: 'plush_keychain',
      recommendedSize: '高约100mm（含挂环）',
      prompt: '完整立体填充毛绒挂件/钥匙扣；使用布料裁片、填充体积、短绒面、缝线、刺绣或印花五官，并带真实挂环和连接位；把参考主体转成玩具轮廓与表面图案，不能只生成平面插画。',
      negative: 'flat illustration, flat poster, hard plastic statue, metal badge, label sheet, missing ring, tiny motif only',
    },
  },
  {
    match: /毛绒玩具|毛绒公仔|毛绒娃娃|毛绒玩偶|布偶|plush\s*(toy|doll)|stuffed\s*(toy|animal)|soft\s*toy/i,
    profile: {
      key: 'plush',
      recommendedSize: '高约130mm',
      prompt: '完整立体填充毛绒玩具；使用布料裁片、柔软填充体积、合理缝线、短绒或超柔绒面、刺绣五官和安全软体结构，明确头身、四肢、耳朵/尾巴等分件；参考主体必须成为玩具的轮廓、刺绣或印花主视觉，不能只做平面海报。',
      negative: 'flat illustration, flat poster, hard plastic shell, metal body, ceramic statue, glossy hard surface, tiny motif only',
    },
  },
  {
    match: /PVC\s*[/／]?\s*搪胶公仔|PVC公仔|搪胶公仔|软胶公仔|潮玩公仔|手办|vinyl\s*figure|collectible\s*figure/i,
    profile: {
      key: 'collectible_toy',
      recommendedSize: '高约130mm',
      prompt: '完整立体可量产的 PVC、搪胶或树脂公仔；明确头身比例、稳定底部、分件、连接位、圆角和真实涂装区域，参考主体成为公仔轮廓与主要装饰，不是平面图案、毛绒玩具或原始雕像。',
      negative: 'flat illustration, flat poster, plush fabric, stuffed toy, missing body or base, melted geometry, tiny isolated motif only',
    },
  },
  {
    match: /硬塑摆件|硬塑公仔|PPC|ABS|注塑摆件|engineering\s*plastic/i,
    profile: {
      key: 'hard_plastic',
      recommendedSize: '150×150×200mm',
      prompt: '完整可量产的硬塑摆件；明确注塑分件、合理壁厚、圆角、凹槽、浅浮雕、紧密装配和稳定底座，参考主体成为产品主要结构或视觉细节，不得生成毛绒、软胶或平面海报。',
      negative: 'plush fabric, soft stuffed toy, flat poster, missing base, impossible thin walls, floating parts, tiny isolated motif only',
    },
  },
  {
    match: /礼盒|包装盒|gift\s*box|packaging\s*box/i,
    profile: {
      key: 'gift_box',
      recommendedSize: '200×150×80mm',
      prompt: '完整可生产的文创礼盒；展示盒体、开合结构、纸张或木材厚度、裁切折线、内衬和真实装配关系，参考元素作为盒面大面积主视觉或压印、烫金工艺，不是单张平面海报。',
      negative: 'flat poster only, missing box structure, impossible opening, no thickness, tiny isolated motif, unrelated package',
    },
  },
  {
    match: /马克杯|咖啡杯|茶杯|水杯|mug|cup/i,
    profile: {
      key: 'mug',
      recommendedSize: '直径约80mm，高约95mm',
      prompt: '完整可使用的马克杯/饮品杯；杯体、开口、杯腔、杯底和真实把手完整可见，参考元素以环绕杯身的弧面印刷、釉上彩或浮雕呈现，展示真实器物的厚度、容积和稳定底部，不是平面海报或单独标签。',
      negative: 'flat poster, flat label sheet, handle missing, incomplete cup, floating object, tiny motif only',
    },
  },
  {
    match: /明信片|贺卡|卡片|postcard|greeting card/i,
    profile: {
      key: 'postcard',
      recommendedSize: 'A6（105×148mm）',
      prompt: '一张真实可生产的明信片/卡片成品；展示完整卡纸轮廓、真实纸张厚度、裁切边和正面印刷构图，可有轻微立体透视或桌面商品摄影；把参考图元素重新编排为卡片正面设计，不直接复刻手机截图或原图载体。',
      negative: 'phone screenshot, unchanged photo, smartphone frame, app controls, label sheet, poster mockup, missing card edges, cropped card',
    },
  },
  {
    match: /钥匙扣|挂件|keychain|key ring/i,
    profile: {
      key: 'keychain',
      recommendedSize: '50×50×4mm（主体，含挂环另计）',
      prompt: '完整可随身使用的钥匙扣/挂件；主体有清晰轮廓、合理耐用厚度、圆角、真实挂孔和连接环/链条，参考主体作为大面积图案或立体轮廓落在成品上，展示完整主体和挂环，不是独立小图标。',
      negative: 'flat label sheet, poster, missing hanging hole, missing ring, tiny isolated motif, fragile paper-only sheet',
    },
  },
  {
    match: /冰箱贴|磁贴|fridge\s*magnet/i,
    profile: {
      key: 'magnet',
      recommendedSize: '60×60×4mm',
      prompt: '完整掌心尺寸的冰箱贴成品；正面是清晰的文化图形或浅浮雕，边缘有合理厚度和圆角，背面应有平整稳定的磁铁粘贴位；参考主体要占据正面主要面积，不是平面海报或孤立 logo。',
      negative: 'flat poster, paper-only card, missing magnetic backing, tiny isolated motif, oversized sculpture',
    },
  },
  {
    match: /徽章|胸针|纪念章|贵金属章|贵金属币|badge|brooch|medal|coin|pin/i,
    profile: {
      key: 'badge',
      recommendedSize: '直径约58mm、厚约3mm',
      prompt: '完整可生产的徽章/纪念章/胸针；有明确金属外轮廓、合理厚度、浅浮雕或珐琅分色，背面有真实别针/固定结构（硬币则为稳定平面边缘）；参考主体应成为正面主要图案而不是小角落装饰。',
      negative: 'flat poster, paper card, missing pin or edge, tiny isolated motif, soft plush body, food object',
    },
  },
  {
    match: /书签|bookmark/i,
    profile: {
      key: 'bookmark',
      recommendedSize: '40×120×1.2mm',
      prompt: '完整可使用的书签；保持细长平面比例、真实纸张/金属/亚克力厚度、圆润裁切边和可选挂穗孔，参考元素沿书签正面完整展开，不能变成厚重摆件或手机截图。',
      negative: 'phone screenshot, thick statue, oversized 3D volume, missing bookmark silhouette, tiny isolated motif',
    },
  },
  {
    match: /帆布|手提袋|单肩包|腰包|背包|笔袋|餐包|毛毡包|杜邦纸包|收纳包|零钱包|托特包|购物袋|canvas\s*bag|bag|pouch/i,
    profile: {
      key: 'bag',
      recommendedSize: '350×300×100mm',
      prompt: '完整可使用的布包/手提袋/帆布包；展示真实布面、裁片、缝线、包边、提手、开口和容量，参考元素以大面积印花、刺绣或织唛落在包面，不能只生成一张平面图案稿。',
      negative: 'flat poster, floating artwork, missing handles, impossible seamless structure, hard statue, tiny motif only',
    },
  },
  {
    match: /项链|颈链|手镯|手链|耳钉|耳坠|吊坠|首饰|jewelry|necklace|bracelet|earring/i,
    profile: {
      key: 'jewelry',
      prompt: '完整可佩戴的首饰成品；展示真实金属/宝石/连接件、合理厚度、圆角和佩戴结构（链条、耳针或扣件），参考元素转为主要吊坠/纹样，不是孤立平面 logo 或海报。',
      negative: 'flat poster, missing clasp or chain, oversized sculpture, tiny isolated motif, unrelated object',
    },
  },
  {
    match: /冰淇淋|冰激凌|ice\s*cream|gelato|frozen\s*dessert/i,
    profile: {
      key: 'ice_cream',
      recommendedSize: '成品约 80×45×12mm，天然实木棒 100-120mm',
      prompt: '固定 2.5D 浮雕冰淇淋/冰激凌成品模板：等距视角（isometric view）、冰棒式扁平完整轮廓、主体表面有清晰压花和浅浮雕、细腻纹理、食品级哑光霜冻质感、纯白背景、柔和摄影棚光线、轻微环境遮蔽、C4D/clay render 商品摄影、3:4 竖构图；底部必须插入天然实木冰棒支撑棒，连接整齐且可生产。用户主体、纹样和文化元素必须成为冰淇淋正面的主要浮雕内容，不得变成包装、杯装冰淇淋或普通冰淇淋球。',
      negative: 'ice cream cone, ice cream cup, scoop, sundae, metal body, plastic figurine, resin statue, ceramic ornament, toy, flat poster, packaging mockup, missing stick, detached stick, extra products, text, logo, blurry embossed details',
    },
  },
  {
    match: /food|食品|巧克力|糖果|曲奇|饼干|月饼|糕点|甜品|冰淇淋|茶叶|咖啡/i,
    profile: {
      key: 'food',
      prompt: '真实可食用的文创食品成品；使用食品级原料和可食用印花、压纹、糖霜或巧克力装饰，呈现可食用的形状、厚度、边缘与合理食品包装，不是金属/塑料摆件或单独平面标签。',
      negative: 'metal ornament, plastic statue, badge, keychain, jewelry, inedible decoration, flat label sheet, tiny motif only',
    },
  },
]

const CATEGORY_PROFILES: Record<string, CreativeProductProfile> = {
  magnet: { key: 'magnet', recommendedSize: '60×60×4mm', prompt: '完整掌心尺寸的冰箱贴成品；正面是清晰的文化图形或浅浮雕，边缘有合理厚度和圆角，背面应有平整稳定的磁铁粘贴位；参考主体占据正面主要面积，不是平面海报或孤立 logo。', negative: 'flat poster, paper-only card, missing magnetic backing, tiny isolated motif, oversized sculpture' },
  stationery: { key: 'paper_stationery', recommendedSize: 'A5（148×210mm）', prompt: '完整可使用的纸品/文具成品；明确纸张或板材厚度、裁切边、折叠/装订/夹持等功能结构，参考元素适合印刷、烫金、压凹凸或覆膜，不能只生成一张海报稿。', negative: 'phone screenshot, flat poster only, missing paper edges, impossible 3D structure, tiny isolated motif' },
  plush: { key: 'plush', recommendedSize: '高约130mm', prompt: '完整立体填充毛绒玩具；使用布料裁片、柔软填充体积、合理缝线、短绒或超柔绒面、刺绣五官和安全软体结构，明确头身和必要分件；参考主体成为玩具轮廓和表面主视觉。', negative: 'flat illustration, flat poster, hard plastic shell, glossy hard surface, tiny motif only' },
  pvc_figure: { key: 'collectible_toy', recommendedSize: '高约130mm', prompt: '完整立体可量产的 PVC、搪胶或树脂公仔；明确头身比例、稳定底部、分件、连接位、圆角和真实涂装区域，参考主体成为公仔轮廓与主要装饰。', negative: 'flat illustration, plush fabric, missing body or base, melted geometry, tiny isolated motif only' },
  hard_plastic: { key: 'hard_plastic', recommendedSize: '150×150×200mm', prompt: '完整可量产的硬塑摆件；明确注塑分件、合理壁厚、圆角、凹槽、浅浮雕、紧密装配和稳定底座。', negative: 'plush fabric, soft stuffed toy, flat poster, missing base, impossible thin walls, floating parts' },
  keychain: { key: 'keychain', recommendedSize: '50×50×4mm（主体，含挂环另计）', prompt: '完整可随身使用的钥匙扣；主体有清晰轮廓、耐用厚度、圆角、挂孔和连接环或链条，参考主体占据成品主要面积。', negative: 'flat label sheet, poster, missing hanging hole, missing ring, tiny isolated motif' },
  gift_box: { key: 'gift_box', recommendedSize: '200×150×80mm', prompt: '完整可生产的文创礼盒；展示盒体、开合结构、材料厚度、裁切折线、内衬和真实装配关系，参考元素作为盒面主要视觉或表面工艺。', negative: 'flat poster only, missing box structure, impossible opening, no thickness, tiny isolated motif' },
  toy: { key: 'collectible_toy', recommendedSize: '高约130mm', prompt: '完整立体可量产的潮玩/玩具/手办；明确头身比例、稳定底部、分件、连接位和涂装表面，参考主体成为玩具轮廓与主要装饰，不是平面图案或随机物体。', negative: 'flat illustration, flat poster, missing body or base, melted geometry, tiny isolated motif only' },
  tableware: { key: 'tableware', recommendedSize: '直径约80mm，高约95mm', prompt: '完整可使用的餐饮器物；明确开口、容积、底部稳定性、合理壁厚和食品接触面，参考元素落在釉面、印花或浮雕区域，展示真实器物而不是雕塑。', negative: 'flat poster, missing opening, unstable base, abstract sculpture, tiny isolated motif only' },
  apparel: { key: 'apparel', recommendedSize: '按常用成人尺码，图案安全边距清晰', prompt: '完整可穿戴的服饰/配件；展示真实布料、裁片、缝线、领口/袖口/扣件或佩戴结构，参考元素以印花、刺绣、织唛或提花落在服饰表面，不能只生成平面图案稿。', negative: 'flat poster, floating garment graphic, missing garment structure, hard statue, tiny isolated motif only' },
  daily: { key: 'daily_use', prompt: '完整可日常使用的生活用品；明确容器/握持/开合/支撑等功能结构、真实材质和可生产厚度，参考元素作为产品表面主视觉或结构细节，而不是孤立小图案。', negative: 'flat poster, abstract pattern only, missing functional structure, random object, tiny isolated motif only' },
  craft: { key: 'craft_object', recommendedSize: '高约150mm，底部稳定', prompt: '完整可陈列、可打样的工艺收藏品；明确主体轮廓、底座/支撑、材质工艺、厚度和安全边缘，参考元素要成为器物的主要造型或表面工艺，不是海报。', negative: 'flat poster, floating parts, unstable base, impossible thin details, tiny isolated motif only' },
  precious: { key: 'precious_collectible', recommendedSize: '直径约40mm、厚约3mm', prompt: '完整可生产的贵金属纪念收藏品；明确金属厚度、边缘、浮雕/压印和稳定轮廓，参考主体成为正面主要图案，展示真实金属成品而不是平面海报。', negative: 'flat poster, paper card, soft plush, missing metal edge, tiny isolated motif only' },
}

const ADAPTATION_INSTRUCTIONS: Record<string, string> = {
  ice_cream: '【参考图使用方式】提取参考图主体的轮廓、颜色和文化识别点，压缩成冰棒正面的 2.5D 浅浮雕/压花；保留主体辨识度，但必须改成可食用冰淇淋本体和天然实木棒结构。',
  cushion: '【参考图使用方式】保留主体外轮廓并裁成柔软填充抱枕，图案/刺绣覆盖主要可见面，优先展示完整抱枕体积、缝线和边缘，不输出原始雕像。',
  umbrella: '【参考图使用方式】把主体拆解为伞面连续印花或局部图形，优先呈现展开伞面、伞骨、伞杆和伞柄等完整结构。',
  towel: '【参考图使用方式】把主体转为毛巾的大面积提花、印花或刺绣，保留织物绒面、包边和自然垂坠。',
  coaster: '【参考图使用方式】把主体转为杯垫正面印花、浅浮雕或激光雕刻，保留平面轮廓、厚度和防滑底面。',
  notebook: '【参考图使用方式】把主体重构为本册封面主视觉和压印/烫金工艺，保留书脊、内页和装订结构。',
  folder: '【参考图使用方式】把主体落到文件夹前后封片或透明夹层，优先呈现折痕、插袋和真实板材厚度。',
  pen: '【参考图使用方式】把主体转为笔杆印花、立体笔夹或局部软胶装饰，保留完整笔尖、笔夹和可书写结构。',
  sticker: '【参考图使用方式】把主体拆解为贴纸包中主要贴纸图形，保留清晰裁切边、出血和离型纸，不生成手机截图。',
  lamp: '【参考图使用方式】把主体转为灯体雕刻板或镂空图形，优先呈现底座、光源、装配关系和真实发光效果。',
  stamp: '【参考图使用方式】把主体重构为邮票主图，保留完整边框、齿孔、纸张和收藏封装结构。',
  tumbler: '【参考图使用方式】把主体转为杯身环绕印花、贴花或激光雕刻，优先呈现杯盖、杯口、圆柱杯身和防漏结构。',
  tshirt: '【参考图使用方式】把主体转为衣身大面积印花、刺绣或发泡工艺，优先呈现完整衣身、领口、袖口和自然布料褶皱。',
  bottle: '【参考图使用方式】把主体转为瓶身大面积主视觉/环绕瓶标，不保留原雕像、原照片或原场景。',
  mug: '【参考图使用方式】把主体转为杯身环绕印花、釉上彩或浮雕，优先呈现完整杯体结构。',
  bag: '【参考图使用方式】把主体转为包面大面积印花、刺绣或织唛，优先呈现完整包体和提手。',
  apparel: '【参考图使用方式】把主体转为服饰大面积印花、刺绣或提花，优先呈现完整可穿戴结构。',
  jewelry: '【参考图使用方式】把主体重构为可佩戴的吊坠/纹样和连接结构，不输出原始雕像或照片。',
  plush: '【参考图使用方式】保留主体可辨识轮廓并重构为柔软填充体、裁片和缝线，不输出硬质雕塑。',
  plush_keychain: '【参考图使用方式】保留主体可辨识轮廓并重构为带挂环的柔软填充挂件，不输出平面插画。',
  collectible_toy: '【参考图使用方式】保留主体可辨识轮廓并重构为有分件、底座和涂装的立体玩具。',
  keychain: '【参考图使用方式】保留主体轮廓并重构为有厚度、挂孔和连接环的钥匙扣成品。',
  postcard: '【参考图使用方式】把主体重新编排到完整卡纸正面，保留卡片边缘和纸张厚度，不输出原手机画面。',
  bookmark: '【参考图使用方式】把主体沿完整书签正面重新编排，保持书签细长轮廓和真实厚度。',
  paper_stationery: '【参考图使用方式】把主体编排到完整纸品/文具表面，保留裁切边、厚度和装订/夹持结构。',
  badge: '【参考图使用方式】把主体转为金属徽章正面浅浮雕/珐琅图案，保留完整金属边缘和背部固定结构。',
  magnet: '【参考图使用方式】把主体转为冰箱贴正面图形或浅浮雕，保留完整厚度、圆角和磁吸背面。',
  precious_collectible: '【参考图使用方式】把主体转为贵金属成品正面浮雕/压印，保留金属边缘和稳定轮廓。',
  food: '【参考图使用方式】把主体转为真实可食用的形状、印花、压纹或装饰，不输出金属/塑料摆件。',
  tableware: '【参考图使用方式】把主体转为器物表面的印花、釉彩或浮雕，同时呈现完整可用结构。',
  daily_use: '【参考图使用方式】把主体转为生活用品表面主视觉或结构细节，同时呈现完整功能结构。',
  craft_object: '【参考图使用方式】保留主体文化识别点并重构为有支撑、厚度和安全边缘的工艺品。',
  general: '【参考图使用方式】保留主体文化识别点并适配目标产品的轮廓、材质和功能结构。',
}

function clean(value: unknown, maxLength = 2400) {
  return String(value ?? '').trim().replace(/\s+/g, ' ').slice(0, maxLength)
}

function cleanPrompt(value: unknown, maxLength = 6000) {
  return String(value ?? '').replace(/\r\n?/g, '\n').split('\n').map(line => line.trim().replace(/[ \t]+/g, ' ')).join('\n').trim().slice(0, maxLength)
}

function productContext(input: CreativeEngineInput) {
  const product = input.product
  // Display categories such as "食品饮品" and "饰品挂件" are broad UI labels,
  // not physical product identities. Matching them here makes a tea bag or a
  // stationery pack inherit the wrong carrier profile. Use stable identity
  // fields for form detection; category fields remain available as fallbacks.
  return [input.productKey, input.productType, product?.key, product?.name, product?.label, product?.categoryKey, ...(product?.materials || []).map(item => item.name)].filter(Boolean).join(' ')
}

export function resolveCreativeProductProfile(input: CreativeEngineInput): CreativeProductProfile {
  const context = productContext(input)
  const match = FORM_PROFILES.find(item => item.match.test(context))?.profile
  if (match) return match
  const keys = [input.product?.categoryKey, input.product?.key, input.productKey, input.productCategory]
    .map(value => clean(value || ''))
    .filter(Boolean)
  return keys.map(key => CATEGORY_PROFILES[key]).find(Boolean) || DEFAULT_PROFILE
}

export function productProfileFor(product: CreativeProductLike | null | undefined): CreativeProductProfile {
  return resolveCreativeProductProfile({ product })
}

export function productNameFor(product: CreativeProductLike | null | undefined): string {
  return creativeProductName({ product })
}

export function creativeProductName(input: CreativeEngineInput) {
  return clean(input.product?.name || input.product?.label || input.productType || input.productCategory || '文创产品', 160)
}

export function creativeProductKey(input: CreativeEngineInput) {
  return clean(input.product?.key || input.productKey || '', 120)
}

export function creativeProductSize(input: CreativeEngineInput, profile = resolveCreativeProductProfile(input)) {
  return clean(input.productSize || profile.recommendedSize || '按产品实际规格', 120)
}

export function creativeProductFormConstraint(input: CreativeEngineInput, purpose: CreativePromptPurpose = 'text') {
  const profile = resolveCreativeProductProfile(input)
  const product = creativeProductName(input)
  const material = clean(input.material || '适合该产品的制造材质', 160)
  const size = creativeProductSize(input, profile)
  const referenceMode = purpose === 'reference' || purpose === 'refinement'
  const source = referenceMode ? '上传参考图中的主体和核心视觉元素' : '用户提供的灵感和核心视觉元素'
  const sourcePrinciple = referenceMode
    ? PRODUCT_REFERENCE_GUARD
    : '【灵感转化原则】用户的文字灵感提供主题、故事、配色和文化识别点；必须把这些内容落实到目标产品的完整结构、材质和真实使用方式上，不得只输出海报或孤立图案。'
  const sourceCleanup = referenceMode
    ? '6. 【原图处理】删除手机、播放器、状态栏、截图边框、天空、山、云和原始场景；保留主体识别特征、文化元素和主要配色。'
    : '6. 【成品要求】产品功能结构、材料厚度、连接关系和表面工艺必须合理，并以真实商品摄影方式呈现。'
  const adaptation = ADAPTATION_INSTRUCTIONS[profile.key] || ADAPTATION_INSTRUCTIONS.general
  if (profile.key === 'ice_cream') {
    // The fixed carrier is the optimizer for this product. Only place the
    // user's original subject in its slot; a Qwen/legacy candidate may already
    // contain a full product template and would otherwise be nested here.
    const subject = cleanPrompt(input.rawPrompt || input.prompt || input.optimizedPrompt || '用户指定的文化主体和纹样', 1200)
    return [
      PRODUCT_ROLE_PROMPT,
      `【任务】把${referenceMode ? '上传参考图中的主体和文化元素' : '用户描述的主体和文化元素'}重构为一支标准化、可食用、可打样的 2.5D 文创冰淇淋；不是包装图、杯装冰淇淋或普通冰淇淋球。`,
      '【固定模板，必须完整保留】',
      `Isometric view of a 2.5D cultural creative ice cream, main subject and cultural elements: [${subject}], intricate relief embossed details, subtle texture, food-safe matte frosted frozen-dessert material, main color: preserve every user-specified color and otherwise derive a coherent palette from the subject, soft diffused studio lighting, subtle ambient occlusion to highlight embossed patterns, clean pure white background, minimalist product photography, C4D render, clay-render lighting style only (the product remains edible, never clay material), high detail, sharp form, one complete popsicle-shaped product, portrait 3:4 composition, the bottom of the object is inserted with a 100-120mm natural solid wood stick support pole, neat food-safe connection structure, no extra debris, no text or logo, 8k.`,
      '【强制规则】用户主体必须成为冰淇淋正面的主要浮雕轮廓和文化图案；保持单个完整产品、垂直 3:4 构图、纯白背景、清晰边缘和可见木棒，不得改变成蛋筒、杯装、冰淇淋球、包装盒或金属/塑料摆件。',
      `【制造参数】材质为「${material}」；成品规格为「${size}」，其中木棒长度固定为 100-120mm，规格用于约束实体比例和结构，不是图片分辨率。`,
      sourceCleanup,
      PRODUCT_SELF_CHECK,
      `【用户补充方向】${subject}`,
    ].join('\n')
  }
  return [
    PRODUCT_ROLE_PROMPT,
    `【任务】将${source}完全重构为一件真实的「${product}」成品，用于电商主图展示；不是对原图做轻微滤镜或简单贴图。`,
    '【强制规则】',
    `1. ${sourcePrinciple}`,
    `2. ${adaptation}`,
    `3. 【目标产品形态】${profile.prompt}`,
    `4. ${PRODUCT_FRAME_GUARD}`,
    `5. 【制造参数】材质为「${material}」；成品规格为「${size}」。规格用于约束实体比例和结构，不是图片分辨率。`,
    sourceCleanup,
    '7. 【禁止输出】原图不变、手机截图、海报、平面标签稿、孤立小图案、黄褐滤镜、窄长手机构图或无关物体。',
    PRODUCT_SELF_CHECK,
  ].join('\n')
}

export function creativeNegativePrompt(input: CreativeEngineInput) {
  const profile = resolveCreativeProductProfile(input)
  return [PRODUCT_OUTPUT_NEGATIVE, profile.negative].filter(Boolean).join(', ')
}

export function creativeProductNegative(input: CreativeEngineInput) {
  return resolveCreativeProductProfile(input).negative
}

export function buildCreativePrompt(input: CreativeEngineInput): string {
  const purpose = input.purpose || (input.refinement ? 'refinement' : Number(input.inputAssetId) > 0 ? 'reference' : 'text')
  const source = cleanPrompt(input.optimizedPrompt || input.prompt || input.rawPrompt || `为${creativeProductName(input)}设计一套适合量产打样的产品视觉`, 6000)
  if (purpose === 'multiview') {
    return [
      `【任务】基于输入的${creativeProductName(input)}产品图，生成同一产品的正面、侧面和背面视图。`,
      '【一致性规则】保持同一产品身份、轮廓、比例、材质、颜色、纹样、结构和尺寸，不新增、不删减、不改造产品形态。',
      `【产品形态】${resolveCreativeProductProfile(input).prompt}`,
      `【制造参数】材质为「${clean(input.material || '已确认材质', 160)}」；成品规格为「${creativeProductSize(input)}」。`,
      '【输出规则】只生成产品设计视图，不保留原图场景、手机/UI、播放器、截图边框或无关物体。',
      `【用户方向】${source}`,
    ].join('\n')
  }
  return [creativeProductFormConstraint({ ...input, purpose }), `【用户补充方向】${source}`].join('\n')
}

export function compileCreativeImageRequest(input: CreativeEngineInput): CreativeImageRequest {
  const purpose = input.purpose || (input.refinement ? 'refinement' : Number(input.inputAssetId) > 0 ? 'reference' : 'text')
  const rawPrompt = cleanPrompt(input.rawPrompt || input.prompt || '', 6000)
  const product = resolveCreativeProductProfile(input)
  const category = clean(input.product?.categoryName || input.productCategory || input.product?.categoryKey || '文创产品', 160)
  const type = creativeProductName(input)
  const refinementNote = cleanPrompt(
    input.refinementNote || (purpose === 'reference' ? creativeProductFormConstraint({ ...input, purpose }, purpose) : ''),
    2400,
  )
  return {
    prompt: buildCreativePrompt({ ...input, purpose }),
    rawPrompt,
    negativePrompt: creativeNegativePrompt(input),
    productKey: creativeProductKey(input),
    productCategory: category,
    productType: type,
    material: clean(input.material, 160),
    productSize: creativeProductSize(input, product),
    inputAssetId: Number.isFinite(Number(input.inputAssetId)) && Number(input.inputAssetId) > 0 ? Number(input.inputAssetId) : null,
    refinement: input.refinement === true || purpose === 'refinement',
    refinementNote,
    seed: input.seed ?? null,
    productForm: product.key,
    creativeEngineVersion: CREATIVE_ENGINE_VERSION,
  }
}
