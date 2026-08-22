package com.example.shixun.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreativePromptCompilerTest {

    private final CreativePromptCompiler compiler = new CreativePromptCompiler();

    @Test
    void equivalentStructuredBriefsCompileToTheSameDeterministicContract() {
        GenerationCommand first = compiler.compile(
                new CreativeBrief("plush_toy", "毛绒玩具", "超柔短毛绒", "20 x 15 x 12 cm",
                        "把上传图片中的金凤凰做成可拥抱的毛绒玩具", 42L, false));
        GenerationCommand second = compiler.compile(
                new CreativeBrief(" plush_toy ", " 毛绒玩具 ", "超柔短毛绒", "20   x 15 x 12 cm",
                        "  把上传图片中的金凤凰做成可拥抱的毛绒玩具  ", 42L, false));

        assertThat(first.compiledPrompt()).isEqualTo(second.compiledPrompt());
        assertThat(first.negativePrompt()).isEqualTo(second.negativePrompt());
        assertThat(first.policyVersion()).isEqualTo(ProductPromptPolicy.VERSION);
        assertThat(first.compiledPrompt()).contains("产品选项标识：plush_toy");
        assertThat(first.compiledPrompt()).contains("成品物理尺寸为 20 x 15 x 12 cm");
        assertThat(first.compiledPrompt()).contains("REFERENCE_IMAGE_CONTRACT");
    }

    @Test
    void promptWhitespaceAndLineEndingsAreNormalizedAcrossAdapters() {
        GenerationCommand first = compiler.compile(
                new CreativeBrief("paper", "纸品", "特种纸", "A5",
                        "第一行  多余空格\r\n第二行", null, false));
        GenerationCommand second = compiler.compile(
                new CreativeBrief("paper", "纸品", "特种纸", "A5",
                        "第一行 多余空格\n第二行", null, false));

        assertThat(first.brief().rawPrompt()).isEqualTo(second.brief().rawPrompt());
        assertThat(first.compiledPrompt()).isEqualTo(second.compiledPrompt());
    }

    @Test
    void referenceAndRefinementModesKeepDifferentMandatoryContracts() {
        GenerationCommand conversion = compiler.compile(
                "postcard", "明信片", "特种纸", "10 x 15 cm",
                "将参考图主体转成可印刷的明信片", 9L, false);
        GenerationCommand refinement = compiler.compile(
                "postcard", "明信片", "特种纸", "10 x 15 cm",
                "将参考图主体转成可印刷的明信片", 9L, true);

        assertThat(conversion.compiledPrompt()).contains("图生图文创转化任务");
        assertThat(refinement.compiledPrompt()).contains("图生图修改任务");
        assertThat(conversion.compiledPrompt()).doesNotContain("图生图修改任务");
        assertThat(refinement.compiledPrompt()).doesNotContain("图生图文创转化任务");
        assertThat(conversion.productSize()).isEqualTo("10 x 15 cm");
        assertThat(refinement.referenceAssetId()).isEqualTo(9L);
    }

    @Test
    void productPolicyIsSelectedFromCategoryAndMaterialAndIsNeverDropped() {
        GenerationCommand command = compiler.compile(
                "cookie", "曲奇饼干", "食品级面团", "直径 8 cm",
                "保留上传图案的凤凰轮廓", null, false);

        assertThat(command.compiledPrompt()).contains("真实可食用曲奇或饼干食品");
        assertThat(command.compiledPrompt()).contains("制造材质：食品级面团");
        assertThat(command.negativePrompt()).contains("metal object");
        assertThat(command.negativePrompt()).contains("plastic");
    }

    @Test
    void hardPlasticFigureIsNotMisclassifiedAsPlush() {
        GenerationCommand hardFigure = compiler.compile(
                "pvc_figure", "PVC / 搪胶公仔", "搪胶", "高约130mm",
                "把参考图主体重构成立体潮玩公仔", 12L, false);
        GenerationCommand plush = compiler.compile(
                "plush", "毛绒公仔", "超柔绒", "高约130mm",
                "把参考图主体重构成毛绒公仔", 13L, false);

        assertThat(hardFigure.compiledPrompt()).contains("PRODUCT_MANUFACTURING_LOCK:plastic_figure");
        assertThat(hardFigure.compiledPrompt()).contains("合理壁厚、圆角、分件线");
        assertThat(hardFigure.compiledPrompt()).doesNotContain("PRODUCT_MANUFACTURING_LOCK:plush");
        assertThat(plush.compiledPrompt()).contains("PRODUCT_MANUFACTURING_LOCK:plush");
    }

    @Test
    void concreteDailySkuKeepsItsCarrierInsteadOfFallingBackToGenericProduct() {
        GenerationCommand cushion = compiler.compile(
                "daily-cushion", "日用生活", "布艺/填充物", "40×40cm",
                "把上传图片中的守护兽做成异形抱枕", 31L, false);
        GenerationCommand tumbler = compiler.compile(
                "tableware-tumbler", "餐饮器物", "不锈钢/塑胶", "直径70mm、高200mm",
                "把上传图片中的纹样做成保温杯", 32L, false);

        assertThat(cushion.compiledPrompt()).contains("PRODUCT_MANUFACTURING_LOCK:cushion");
        assertThat(cushion.compiledPrompt()).contains("柔软填充形体");
        assertThat(cushion.compiledPrompt()).contains("REFERENCE_IMAGE_CONTRACT");
        assertThat(tumbler.compiledPrompt()).contains("PRODUCT_MANUFACTURING_LOCK:tumbler");
        assertThat(tumbler.compiledPrompt()).contains("杯盖、杯口、圆柱杯身");
    }

    @Test
    void blankCategoryFallsBackToStableGenericProductPolicy() {
        GenerationCommand command = compiler.compile(
                "custom_product", "", "", "", "一个可量产的文创产品", null, false);

        assertThat(command.category()).isEqualTo("文创产品");
        assertThat(command.compiledPrompt()).contains("产品类别：文创产品");
        assertThat(command.policyVersion()).isNotBlank();
    }

    @Test
    void optimizedCandidateDoesNotReplaceTheOriginalBrief() {
        CreativeBrief brief = new CreativeBrief(
                "plush_toy", "毛绒玩具", "短毛绒", "20 cm",
                "把上传图片里的金凤凰做成毛绒玩具", 17L, false);

        GenerationCommand command = compiler.compileWithCandidate(
                brief, "premium golden decorative product on a studio background");

        assertThat(command.brief().rawPrompt()).isEqualTo("把上传图片里的金凤凰做成毛绒玩具");
        assertThat(command.compiledPrompt()).contains("premium golden decorative product");
        assertThat(command.compiledPrompt()).contains("用户原始要求（必须保留）：把上传图片里的金凤凰做成毛绒玩具");
    }

    @Test
    void staleInternalLocksAreRebuiltFromTheCurrentStructuredBrief() {
        String staleCandidate = "<<PRODUCT_MANUFACTURING_LOCK:paper>>旧纸品规则<</PRODUCT_MANUFACTURING_LOCK>>\n"
                + "<<CREATIVE_BRIEF_CONTEXT>>旧产品字段<</CREATIVE_BRIEF_CONTEXT>>\n"
                + "<<PRODUCT_SIZE_LOCK>>成品物理尺寸为 A5<</PRODUCT_SIZE_LOCK>>\n"
                + "<<REFERENCE_IMAGE_CONTRACT>>旧图生图规则<</REFERENCE_IMAGE_CONTRACT>>\n"
                + "把凤凰做成新的毛绒产品";

        GenerationCommand command = compiler.compileWithCandidate(
                new CreativeBrief("plush", "毛绒玩具", "超柔绒", "高约130mm",
                        "把凤凰做成毛绒玩具", 21L, false), staleCandidate);

        assertThat(command.compiledPrompt()).contains("PRODUCT_MANUFACTURING_LOCK:plush");
        assertThat(command.compiledPrompt()).contains("成品物理尺寸为 高约130mm");
        assertThat(command.compiledPrompt()).contains("图生图文创转化任务");
        assertThat(command.compiledPrompt()).doesNotContain("PRODUCT_MANUFACTURING_LOCK:paper");
        assertThat(command.compiledPrompt()).doesNotContain("成品物理尺寸为 A5");
        assertThat(command.compiledPrompt()).doesNotContain("旧图生图规则");
    }

    @Test
    void longUserBriefIsBoundedWithoutDroppingItsEnding() {
        String rawPrompt = "开头" + "主体元素".repeat(2000) + "结尾必须保留";

        GenerationCommand command = compiler.compileWithCandidate(
                new CreativeBrief("giftbox", "礼盒", "纸质", "200×150×80mm",
                        rawPrompt, null, false), "optimized candidate");

        assertThat(command.brief().rawPrompt()).hasSizeLessThanOrEqualTo(6000);
        assertThat(command.compiledPrompt()).contains("结尾必须保留");
        assertThat(command.compiledPrompt()).contains("成品物理尺寸为 200×150×80mm");
    }

    @Test
    void iceCreamTemplateIsRebuiltOnceAroundTheOriginalSubject() {
        CreativeBrief brief = new CreativeBrief(
                "ice_cream_2_5d", "食品饮品 / 文创冰淇淋", "冰淇淋", "80×45×12mm",
                "上传图中的金凤凰轮廓、金红配色和祥云浮雕", 88L, false);
        String expandedClientCandidate = "<<ICE_CREAM_2_5D_TEMPLATE>> old candidate subject: a generic cone dessert "
                + "<</ICE_CREAM_2_5D_TEMPLATE>>";

        GenerationCommand command = compiler.compileWithCandidate(brief, expandedClientCandidate);
        String prompt = command.compiledPrompt();
        String marker = "<<ICE_CREAM_2_5D_TEMPLATE>>";

        assertThat(prompt).contains(marker);
        assertThat(prompt.indexOf(marker)).isEqualTo(prompt.lastIndexOf(marker));
        assertThat(prompt).contains("上传图中的金凤凰轮廓、金红配色和祥云浮雕");
        assertThat(prompt).doesNotContain("old candidate subject");
        assertThat(prompt).contains("成品物理尺寸为 80×45×12mm");
        assertThat(prompt).contains("图生图文创转化任务");
    }
}
