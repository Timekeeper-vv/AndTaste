package com.example.shixun.service;

import java.util.Locale;

/**
 * Product-specific prompt constraints shared by every image and 3D provider.
 * The policy describes the physical product, not just its visual theme, so an
 * edible SKU cannot silently become a metal souvenir or a decorative object.
 */
public final class ProductPromptPolicy {
    /**
     * Bump this value whenever the deterministic product locks or negative
     * prompt contract changes. It is persisted with generation requests so a
     * later review can explain which policy produced an image.
     */
    public static final String VERSION = "product-prompt-policy-v3";
    /** Descriptive alias for callers that use the longer constant name. */
    public static final String POLICY_VERSION = VERSION;

    private ProductPromptPolicy() {}

    public record Profile(String key, String label, String positiveLock, String negativePrompt, boolean edible) {}

    public static String policyVersion() {
        return VERSION;
    }

    public static Profile resolve(String productCategory, String material) {
        String context = normalize(productCategory) + " " + normalize(material);
        if (containsAny(context, "曲奇", "饼干", "糕点", "cookie", "biscuit", "pastry")) {
            return new Profile("cookie", "曲奇/饼干",
                    "真实可食用曲奇或饼干食品，不是摆件；使用食品级面团、可食用糖霜、食用色粉或巧克力装饰；保留清晰可识别的烘焙轮廓、统一厚度、金黄烘烤边缘、自然饼干孔隙和可食用礼盒/密封食品包装展示；适合打样生产。",
                    "metal object, alloy, badge, brooch, enamel pin, keychain, jewelry, plastic, PVC, resin, ceramic ornament, stone, wood, toy, non-edible decoration, inedible material, food-shaped souvenir",
                    true);
        }
        if (containsAny(context, "冰淇淋", "冰激凌", "ice cream", "gelato", "frozen dessert")) {
            return new Profile("ice_cream", "冰淇淋",
                    "真实可食用的 2.5D 浮雕冰淇淋/冰激凌冷冻乳制品食品成品，不是摆件；固定为冰棒式扁平浮雕轮廓和等距视角，主体表面有清晰的压花/浅浮雕、细腻纹理、哑光霜冻质感和食品级冷冻甜品工艺；底部必须插入约 100-120mm 的天然实木冰棒支撑棒，连接整齐、可生产、可食用本体与木棒分明；使用纯白背景、3:4 竖构图、单个完整产品，适合冷链打样与商品摄影。",
                    "ice cream cone, ice cream cup, scoop, sundae, metal body, alloy body, plastic figurine, PVC, resin, ceramic ornament, stone, wooden body, toy, non-edible sculpture, inedible material, flat poster, packaging mockup, missing stick, detached stick, extra products, text, logo",
                    true);
        }
        if (containsAny(context, "巧克力", "糖果", "月饼", "茶叶", "饮品", "咖啡", "食品", "食用", "food", "chocolate", "candy", "mooncake", "beverage", "coffee", "tea")) {
            return new Profile("food", "食品",
                    "真实可食用食品商品，不是装饰摆件；使用明确的食品级原料、可食用色彩和食品工艺，呈现真实可食用的质地、厚度、边缘与安全包装；文化纹样只能作为食品本体上的可食用印花、压纹、糖霜、巧克力或包装图形；适合食品打样，但配料、过敏原、保质期和执行标准必须由生产方确认。",
                    "metal object, alloy, badge, brooch, keychain, jewelry, plastic ornament, PVC, resin, ceramic ornament, stone, wood, toy, non-edible material, inedible decoration, food-shaped souvenir",
                    true);
        }
        if (containsAny(context, "抱枕", "靠垫", "cushion", "pillow")) {
            return new Profile("cushion", "异形抱枕",
                    "可拥抱的异形抱枕；把参考主体外轮廓裁成柔软填充形体，使用布艺裁片、包边、缝线、合理填充厚度和热转印/刺绣/数码彩喷表面；主体必须占据抱枕主要面积，适合家居日用和打样生产。",
                    "hard statue, metal body, ceramic object, flat poster, flat fabric swatch, missing pillow volume, sharp thin parts, tiny motif only",
                    false);
        }
        if (containsAny(context, "雨伞", "伞面", "umbrella")) {
            return new Profile("umbrella", "折叠雨伞",
                    "可使用的折叠雨伞；展示展开伞面、伞骨、伞杆、伞柄和安全圆角，参考主体转为伞面连续印花，使用真实防水涤纶和可收合结构。",
                    "flat poster, missing canopy, missing ribs, missing handle, impossible floating fabric, statue, tiny motif only",
                    false);
        }
        if (containsAny(context, "毛巾", "浴巾", "towel")) {
            return new Profile("towel", "文化主题毛巾",
                    "可使用的织物毛巾；展示完整织物轮廓、吸水绒面、织造纹理、包边和自然垂坠，参考主体转为提花、印花或刺绣，保持亲肤软体结构。",
                    "paper card, plastic sheet, metal object, statue, flat poster, missing textile pile, tiny motif only",
                    false);
        }
        if (containsAny(context, "杯垫", "coaster")) {
            return new Profile("coaster", "主题杯垫",
                    "可使用的杯垫；保持掌心尺寸的平面轮廓、真实厚度、圆角和防滑底面，参考主体成为正面印花、浅浮雕或激光雕刻主视觉，材质必须适合日用接触。",
                    "oversized sculpture, paper poster, missing thickness, missing anti-slip base, floating parts, tiny motif only",
                    false);
        }
        if (containsAny(context, "保温杯", "随行杯", "tumbler", "thermos")) {
            return new Profile("tumbler", "保温杯/随行杯",
                    "可使用的保温杯或随行杯；展示杯盖、杯口、圆柱杯身、底部和真实防漏结构，参考主体作为杯身环绕印花、激光雕刻或贴花，保持选定金属/塑胶材质。",
                    "flat label sheet, missing lid, missing opening, paper card, sculpture, tiny motif only",
                    false);
        }
        if (containsAny(context, "文件夹", "folder")) {
            return new Profile("folder", "文件夹",
                    "可使用的文件夹；展示前后封片、折痕、插袋开口、真实板材厚度和边缘，参考主体落在正面印刷或透明夹层中，保持可装文件结构。",
                    "flat poster only, loose card, missing fold, missing pocket, impossible thin structure, tiny motif only",
                    false);
        }
        if (containsAny(context, "本册", "笔记本", "打卡本", "notebook", "journal")) {
            return new Profile("notebook", "主题本册/笔记本",
                    "可翻阅的主题本册或笔记本；展示封面、书脊、内页厚度、装订或活页结构和真实纸张表面，参考主体重构为封面主视觉、烫金或压凹凸工艺。",
                    "flat poster only, loose paper pile, missing spine, missing page block, phone screenshot, tiny motif only",
                    false);
        }
        if (containsAny(context, "中性笔", "签字笔", "笔夹", "pen", "ballpoint")) {
            return new Profile("pen", "中性笔/签字笔",
                    "可书写的中性笔或签字笔；展示笔杆、笔夹、笔尖、按动或笔帽结构和真实塑胶/金属表面，参考主体转为笔杆印花、立体笔夹或局部软胶装饰。",
                    "flat poster, missing tip, missing clip, broken pen body, oversized sculpture, tiny motif only",
                    false);
        }
        if (containsAny(context, "贴纸", "sticker")) {
            return new Profile("sticker", "主题贴纸包",
                    "可生产的贴纸包；展示多枚有清晰裁切边、出血、背胶和离型纸的贴纸套装，参考主体拆解为主要贴纸图形并保持可辨识。",
                    "phone screenshot, poster mockup, missing die-cut edges, loose logo only, blank sheet, tiny unreadable motif",
                    false);
        }
        if (containsAny(context, "叶雕灯", "夜灯", "灯具", "lamp", "light")) {
            return new Profile("lamp", "文创灯具",
                    "可点亮的文创灯具或叶雕灯；展示灯体、透明/半透明雕刻板、底座、光源和稳定装配关系，参考主体转为板面镂空或雕刻图形，保持真实发光结构。",
                    "flat poster, missing light source, missing base, impossible floating panel, random lamp shape, tiny motif only",
                    false);
        }
        if (containsAny(context, "邮票", "stamp")) {
            return new Profile("stamp", "邮票/封装收藏品",
                    "可收藏的邮票成品或封装；展示纸张、齿孔、完整边框、面值区域和清晰印刷图案，参考主体重构为邮票主图，保持平面纸品结构。",
                    "phone screenshot, poster without perforation, missing stamp border, oversized 3D object, tiny motif only",
                    false);
        }
        if (containsAny(context, "T恤", "短袖", "t-shirt", "tshirt", "tee")) {
            return new Profile("tshirt", "文化主题T恤",
                    "可穿着的文化主题T恤；展示衣身、领口、袖口、下摆、真实棉/混纺布料和自然褶皱，参考主体转为大面积印花、刺绣或发泡工艺，保持完整服饰结构。",
                    "flat garment graphic, missing sleeves, missing neckline, floating artwork, hard statue, tiny motif only",
                    false);
        }
        if (containsAny(context, "冰箱贴", "magnet", "fridge magnet")) {
            return new Profile("magnet", "冰箱贴",
                    "可生产的冰箱贴；主体应为掌心尺寸的清晰轮廓，前面是文化图形或浅浮雕，背面必须预留完整平整的磁铁粘贴位和稳定吸附面；保持合理厚度、圆角和不易断裂的边缘，适合压铸、PVC、树脂或陶瓷打样。",
                    "edible food, biscuit, ice cream, jewelry, brooch pin, keychain ring, hanging hole, oversized sculpture, paper-only artwork, razor-thin fragile edge, floating parts",
                    false);
        }
        if (containsAny(context, "徽章", "胸针", "badge", "brooch", "pin")) {
            return new Profile("badge", "徽章/胸针",
                    "可生产的徽章或胸针；使用清晰金属外轮廓、合理厚度、压铸/冲压浅浮雕、烤漆或冷珐琅分色区，背面必须有真实背针、别针或蝴蝶帽固定结构；避免极细镂空和会刺伤的尖锐边缘。",
                    "edible food, biscuit, ice cream, magnet backing, keychain ring, plush fabric, paper-only flat artwork, impossible thin metal, sharp blade, floating parts",
                    false);
        }
        if (containsAny(context, "钥匙扣", "keychain", "key chain")) {
            return new Profile("keychain", "钥匙扣",
                    "可生产的钥匙扣；主体需有清晰轮廓、耐用厚度、圆角和真实的挂孔、连接环或链条连接位，挂孔必须与主体保持足够边距；适合金属、亚克力、PVC或木质工艺，展示为可随身使用的成品而不是独立摆件。",
                    "edible food, biscuit, ice cream, brooch pin, magnet backing, fragile paper-only sheet, no hanging hole, floating parts, sharp unsafe edge, oversized sculpture",
                    false);
        }
        if (containsAny(context, "书签", "bookmark") && containsAny(context, "金属", "合金", "metal", "alloy")) {
            return new Profile("metal_bookmark", "金属书签",
                    "可生产的金属书签；使用薄而有韧性的金属片、圆润裁切边、可读的压纹/腐蚀/烤漆图形和真实夹持端或挂穗孔，整体保持书页间可放置的平面比例；避免生成徽章背针、冰箱贴磁铁或厚重摆件结构。",
                    "edible food, biscuit, ice cream, magnet backing, brooch pin, bulky sculpture, thick toy, plush fabric, paper-only sheet, sharp blade, floating parts",
                    false);
        }
        boolean explicitlyPlush = containsAny(context, "毛绒", "布偶", "plush", "stuffed", "softtoy", "soft_toy");
        boolean hardFigure = containsAny(context, "pvc", "搪胶", "软胶", "硬塑", "abs", "ppc", "树脂",
                "vinyl", "hardplastic", "hard_plastic", "resin")
                || (!explicitlyPlush && containsAny(context, "公仔", "摆件", "潮玩", "figure", "figurine", "collectible"));
        if (hardFigure) {
            return new Profile("plastic_figure", "PVC/搪胶/硬塑公仔",
                    "可生产的PVC、搪胶、ABS/PPC硬塑或树脂公仔/摆件；使用明确的立体头身比例、稳定底座或站立结构、合理壁厚、圆角、分件线和可喷涂区域；表面应符合选定硬质或软胶材质，参考图元素重构为公仔轮廓、涂装和浅浮雕细节，避免深倒扣、悬空细杆和无法开模的结构。",
                    "plush fabric, fur, stuffed soft body, textile seams, edible food, ceramic statue, metal-only body, paper-only artwork, flat poster, floating parts, razor-thin limbs, impossible undercut",
                    false);
        }
        if (containsAny(context, "毛绒", "玩偶", "公仔", "plush", "stuffed", "soft toy")) {
            return new Profile("plush", "毛绒玩具",
                    "可生产的填充毛绒玩具；使用布料裁片、填充体积、合理缝线、短毛或超柔绒面、刺绣五官和安全的软体结构；明确头身比例、四肢、耳朵/尾巴等分件，避免把图案错误地做成硬塑或金属实体。",
                    "metal body, alloy, ceramic, glass, hard plastic shell, PVC figurine, resin statue, sharp wire, exposed sharp parts, glossy hard surface",
                    false);
        }
        if (containsAny(context, "马克杯", "咖啡杯", "茶杯", "餐盘", "碟", "碗", "陶瓷", "骨瓷", "搪瓷", "mug", "ceramic", "tableware")) {
            return new Profile("tableware", "杯具/餐具",
                    "可真实使用的杯具或餐具；保持合理器型、容积、开口、底部稳定性和可生产壁厚；食品接触面使用食品安全的陶瓷釉面、骨瓷或搪瓷，纹样位于可印刷/釉上彩区域；展示为真实器物而不是雕塑摆件。",
                    "metal sculpture, alloy ornament, sharp thin edge, unstable floating form, porous unsafe surface, non-food-safe coating, toy, abstract sculpture",
                    false);
        }
        if (containsAny(context, "帆布", "笔袋", "袋", "包", "canvas", "pouch", "bag", "textile")) {
            return new Profile("canvas", "帆布/纺织品",
                    "可生产的帆布或纺织品；使用布料平面、裁片、缝线、包边、提手/拉链等真实结构；图案必须落在布面印刷、刺绣、植绒、热转印或织唛区域；展示真实可用的包袋或文具，不把主视觉变成独立硬质雕塑。",
                    "metal body, alloy object, ceramic, glass, hard plastic shell, resin statue, floating decoration, impossible seamless structure, sharp exposed parts",
                    false);
        }
        if (containsAny(context, "合金", "金属", "徽章", "胸针", "钥匙扣", "五金", "metal", "alloy", "badge", "pin", "keychain")) {
            return new Profile("metal", "金属/五金",
                    "可生产的金属或合金文创；使用压铸/冲压后的真实金属厚度、圆角、安全边缘、分型线、浅浮雕、烤漆/珐琅/电镀表面；徽章需有背针或别针结构，冰箱贴需有平整背面磁铁位，钥匙扣需有真实挂孔和连接环；保持工程结构可打样。",
                    "edible food, biscuit, ice cream, plush fabric, fur, paper-only flat artwork, soft cloth body, impossible paper-thin metal, sharp exposed blade, random toy plastic",
                    false);
        }
        if (containsAny(context, "亚克力", "透明挂件", "acrylic")) {
            return new Profile("acrylic", "亚克力制品",
                    "可生产的平面或分层亚克力制品；使用透明/半透明或不透明亚克力板、激光切割轮廓、圆角和可见厚度，图案为正面印刷或夹层印刷，钥匙扣需有真实挂孔与连接环；保持平板结构，不生成金属实体或食品。",
                    "metal body, alloy sculpture, ceramic, fabric, fur, edible food, melted plastic, impossible soft volume, sharp unsafe edge, random 3D statue",
                    false);
        }
        if (containsAny(context, "书签", "明信片", "卡片", "贴纸", "纸品", "纸", "bookmark", "postcard", "sticker", "paper")) {
            return new Profile("paper", "纸品/文具",
                    "可生产的纸质平面文创；使用纸张或卡纸的真实厚度、裁切边、出血和折叠/装订结构，图案适合印刷、烫金、压凹凸或覆膜；保持平面构图和可读的文化图形，不把书签、明信片或贴纸变成厚重立体摆件。",
                    "metal sculpture, alloy body, ceramic, glass, plush, fur, oversized 3D volume, impossible thin floating parts, unreadable clutter, food object",
                    false);
        }
        if (containsAny(context, "木质", "木", "wood", "wooden")) {
            return new Profile("wood", "木质制品",
                    "可生产的木质文创；使用真实木纹、可加工厚度、圆角和激光雕刻/丝印/木蜡油表面，纹样应适合雕刻深度和刀路；避免悬空薄片、过细连接和无法加工的复杂倒扣。",
                    "edible food, ice cream, metal body, plush, fabric, glass, impossible floating parts, razor-thin fragile wood, sharp splinters",
                    false);
        }
        return new Profile("general", productCategory == null || productCategory.isBlank() ? "文创产品" : productCategory.trim(),
                "把它作为真实可打样的文创产品设计；明确主体轮廓、合理尺寸、可制造厚度、圆角、安全边缘、分件和适合实际材质的表面工艺；不要只生成海报或抽象概念图。",
                "unrelated object, impossible structure, floating parts, razor-thin fragile details, unsafe sharp edges, random material substitution",
                false);
    }

    public static String enforce(String prompt, String productCategory, String material) {
        // Prompt candidates can come from an older client or a persisted job.
        // Internal policy markers are server-owned and must be rebuilt for the
        // current product/material instead of allowing a stale marker to bypass
        // the current policy.
        String base = stripInternalLocks(prompt);
        Profile profile = resolve(productCategory, material);
        String materialLine = material == null || material.isBlank() ? "" : "选定制造材质必须保持为：" + material.trim() + "。"
                + "不得把主材质替换成其他材质。";
        String marker = "<<PRODUCT_MANUFACTURING_LOCK:" + profile.key() + ">>";
        String lock = marker + "产品类型锁定：" + profile.label() + "。" + materialLine
                + profile.positiveLock() + "这是生成约束，不是可忽略的装饰建议。<</PRODUCT_MANUFACTURING_LOCK>>";
        if (profile.edible()) {
            if ("ice_cream".equals(profile.key())) return enforceIceCream(base, lock, material);
            return enforceFood(base, lock);
        }
        return base.isBlank() ? lock : lock + "\n" + base;
    }

    public static String negative(String productCategory, String material) {
        return resolve(productCategory, material).negativePrompt();
    }

    public static String optimizerRules(String productCategory, String material) {
        Profile profile = resolve(productCategory, material);
        return "Product type: " + profile.label() + ". Product-specific production rules: " + profile.positiveLock()
                + " Never include these conflicts: " + profile.negativePrompt();
    }

    private static String enforceFood(String base, String lock) {
        String safeBase = boundPreservingEnds(base, 1800);
        return "<<EDIBLE_FOOD_LOCK>>必须一眼看出是可以吃的真实食品，而不是食品外观的纪念品或摆件。禁止不可食用材质和非食品结构。<</EDIBLE_FOOD_LOCK>>\n" + lock + (safeBase.isBlank() ? "" : "\n" + safeBase);
    }

    /**
     * Seedream is more consistent when the 2.5D ice-cream carrier is a fixed
     * English product template and the user's subject is inserted into one
     * bounded field. Midjourney-only flags such as --ar/--style are expressed
     * as plain instructions because this route uses Seedream's API.
     */
    private static String enforceIceCream(String base, String lock, String material) {
        String subject = boundPreservingEnds(base, 1200);
        if (subject.isBlank()) subject = "the user's cultural subject and motif";
        String finish = material == null || material.isBlank()
                ? "food-safe matte frosted frozen-dessert material"
                : "food-safe " + material.trim() + " with a matte frosted finish";
        String template = "Isometric view of a 2.5D cultural creative ice cream, main subject and cultural elements: ["
                + subject + "], intricate relief embossed details, subtle texture, " + finish
                + ", main color: preserve every user-specified color and otherwise derive a coherent palette from the subject, "
                + "soft diffused studio lighting, subtle ambient occlusion to highlight embossed patterns, "
                + "clean pure white background, minimalist product photography, C4D render, clay-render lighting style only (the product remains edible, never clay material), "
                + "high detail, sharp form, one complete popsicle-shaped product, portrait 3:4 composition, "
                + "the bottom of the object is inserted with a 100-120mm natural solid wood stick support pole, "
                + "neat food-safe connection structure, no extra debris, no text or logo, 8k.";
        return "<<EDIBLE_FOOD_LOCK>>必须一眼看出是可以吃的真实食品，而不是食品外观的纪念品或摆件。"
                + "禁止不可食用材质和非食品结构；只允许天然实木作为底部支撑棒。<</EDIBLE_FOOD_LOCK>>\n"
                + lock + "\n<<ICE_CREAM_2_5D_TEMPLATE>>" + template + "<</ICE_CREAM_2_5D_TEMPLATE>>";
    }

    private static String boundPreservingEnds(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value == null ? "" : value;
        String separator = "\n...[中间描述已压缩]...\n";
        int available = maxLength - separator.length();
        int headLength = (int) Math.ceil(available * 0.58d);
        int tailLength = available - headLength;
        return value.substring(0, headLength) + separator + value.substring(value.length() - tailLength);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String stripInternalLocks(String value) {
        if (value == null || value.isBlank()) return "";
        return value
                .replaceAll("(?s)<<PRODUCT_MANUFACTURING_LOCK:[^>]+>>.*?<</PRODUCT_MANUFACTURING_LOCK>>", "")
                .replaceAll("(?s)<<EDIBLE_FOOD_LOCK>>.*?<</EDIBLE_FOOD_LOCK>>", "")
                .replaceAll("(?s)<<ICE_CREAM_2_5D_TEMPLATE>>.*?<</ICE_CREAM_2_5D_TEMPLATE>>", "")
                .trim();
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(normalize(term))) return true;
        return false;
    }
}
