package com.example.shixun.service;

import java.util.Locale;

/**
 * Product-specific prompt constraints shared by every image and 3D provider.
 * The policy describes the physical product, not just its visual theme, so an
 * edible SKU cannot silently become a metal souvenir or a decorative object.
 */
public final class ProductPromptPolicy {
    private ProductPromptPolicy() {}

    public record Profile(String key, String label, String positiveLock, String negativePrompt, boolean edible) {}

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
                    "真实可食用冰淇淋或冷冻乳制品食品，不是摆件；使用食品级冰淇淋/雪糕质感，呈现可落地的模具成型轮廓、奶油冰晶细节、食品级可食用装饰、蛋筒或食品包装；主体必须看起来可以直接食用，并考虑冷冻食品生产与冷链展示。",
                    "metal object, alloy, badge, brooch, keychain, jewelry, plastic figurine, PVC, resin, ceramic ornament, stone, wood, toy, non-edible sculpture, inedible material, food-shaped souvenir",
                    true);
        }
        if (containsAny(context, "巧克力", "糖果", "月饼", "茶叶", "饮品", "咖啡", "食品", "食用", "food", "chocolate", "candy", "mooncake", "beverage", "coffee", "tea")) {
            return new Profile("food", "食品",
                    "真实可食用食品商品，不是装饰摆件；使用明确的食品级原料、可食用色彩和食品工艺，呈现真实可食用的质地、厚度、边缘与安全包装；文化纹样只能作为食品本体上的可食用印花、压纹、糖霜、巧克力或包装图形；适合食品打样，但配料、过敏原、保质期和执行标准必须由生产方确认。",
                    "metal object, alloy, badge, brooch, keychain, jewelry, plastic ornament, PVC, resin, ceramic ornament, stone, wood, toy, non-edible material, inedible decoration, food-shaped souvenir",
                    true);
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
        String base = prompt == null ? "" : prompt.trim();
        Profile profile = resolve(productCategory, material);
        String materialLine = material == null || material.isBlank() ? "" : "选定制造材质必须保持为：" + material.trim() + "。"
                + "不得把主材质替换成其他材质。";
        String marker = "<<PRODUCT_MANUFACTURING_LOCK:" + profile.key() + ">>";
        if (base.contains(marker)) return base;
        String lock = marker + "产品类型锁定：" + profile.label() + "。" + materialLine
                + profile.positiveLock() + "这是生成约束，不是可忽略的装饰建议。<</PRODUCT_MANUFACTURING_LOCK>>";
        if (profile.edible()) return enforceFood(base, lock);
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
        if (base.contains("<<EDIBLE_FOOD_LOCK>>")) return base + "\n" + lock;
        String safeBase = base.length() > 360 ? base.substring(0, 360) : base;
        return "<<EDIBLE_FOOD_LOCK>>必须一眼看出是可以吃的真实食品，而不是食品外观的纪念品或摆件。禁止不可食用材质和非食品结构。<</EDIBLE_FOOD_LOCK>>\n" + lock + (safeBase.isBlank() ? "" : "\n" + safeBase);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) if (value.contains(normalize(term))) return true;
        return false;
    }
}
