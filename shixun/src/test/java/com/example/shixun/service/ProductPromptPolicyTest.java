package com.example.shixun.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductPromptPolicyTest {

    @Test
    void cookiePromptIsClearlyEdibleAndRejectsNonFoodMaterials() {
        String prompt = ProductPromptPolicy.enforce("青铜纹样和祥云", "曲奇饼干", "食品级原料");
        String negative = ProductPromptPolicy.negative("曲奇饼干", "食品级原料");

        assertThat(prompt).contains("真实可食用曲奇或饼干食品");
        assertThat(prompt).contains("食品级面团");
        assertThat(prompt).contains("不是摆件");
        assertThat(prompt).contains("EDIBLE_FOOD_LOCK");
        assertThat(negative).contains("metal object");
        assertThat(negative).contains("plastic");
    }

    @Test
    void iceCreamPromptKeepsFoodAndColdChainMeaning() {
        ProductPromptPolicy.Profile profile = ProductPromptPolicy.resolve("2.5D 文创冰淇淋", "冰淇淋");

        assertThat(profile.key()).isEqualTo("ice_cream");
        assertThat(profile.edible()).isTrue();
        assertThat(profile.positiveLock()).contains("冷冻乳制品食品");
        assertThat(profile.positiveLock()).contains("冷链");
    }

    @Test
    void metalProductsKeepManufacturableHardware() {
        String prompt = ProductPromptPolicy.enforce("青铜器守护兽", "锌合金徽章", "锌合金");

        assertThat(prompt).contains("金属外轮廓");
        assertThat(prompt).contains("背针、别针或蝴蝶帽固定结构");
        assertThat(prompt).contains("PRODUCT_MANUFACTURING_LOCK");
    }

    @Test
    void magnetBadgeAndKeychainUseDifferentBacksideStructures() {
        ProductPromptPolicy.Profile magnet = ProductPromptPolicy.resolve("合金冰箱贴", "合金");
        ProductPromptPolicy.Profile badge = ProductPromptPolicy.resolve("锌合金徽章", "锌合金");
        ProductPromptPolicy.Profile keychain = ProductPromptPolicy.resolve("亚克力钥匙扣", "亚克力");

        assertThat(magnet.key()).isEqualTo("magnet");
        assertThat(magnet.positiveLock()).contains("磁铁粘贴位");
        assertThat(badge.key()).isEqualTo("badge");
        assertThat(badge.positiveLock()).contains("背针");
        assertThat(keychain.key()).isEqualTo("keychain");
        assertThat(keychain.positiveLock()).contains("挂孔");
    }

    @Test
    void textileProductsDoNotBecomeHardSculptures() {
        ProductPromptPolicy.Profile profile = ProductPromptPolicy.resolve("帆布单肩包", "帆布");

        assertThat(profile.key()).isEqualTo("canvas");
        assertThat(profile.positiveLock()).contains("裁片");
        assertThat(profile.positiveLock()).contains("缝线");
        assertThat(profile.negativePrompt()).contains("ceramic");
    }

    @Test
    void paperProductsStayFlatAndPrintable() {
        ProductPromptPolicy.Profile profile = ProductPromptPolicy.resolve("书签", "特种纸");

        assertThat(profile.key()).isEqualTo("paper");
        assertThat(profile.positiveLock()).contains("纸质平面文创");
        assertThat(profile.negativePrompt()).contains("oversized 3D volume");
    }
}
