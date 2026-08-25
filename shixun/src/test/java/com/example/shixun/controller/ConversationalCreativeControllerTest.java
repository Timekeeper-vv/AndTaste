package com.example.shixun.controller;

import com.example.shixun.security.JwtService;
import com.example.shixun.service.SiliconFlowChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationalCreativeControllerTest {
    private JdbcTemplate jdbc;
    private SiliconFlowChatService siliconFlow;
    private ConversationalCreativeController controller;
    private JwtService.Claims claims;

    @BeforeEach
    void setUp() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:conversation_" + System.nanoTime() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        createSchema();
        seedUserAndSession();

        siliconFlow = mock(SiliconFlowChatService.class);
        when(siliconFlow.modelName()).thenReturn("Qwen/Qwen3-32B");
        when(siliconFlow.chat(anyString(), anyString(), anyDouble(), anyInt(), anyInt()))
                .thenReturn("{\"reply\":\"我已记下\",\"productKey\":\"\",\"productName\":\"\",\"categoryKey\":\"\",\"material\":\"\",\"inspiration\":\"\",\"mode\":\"\",\"ready\":false}");
        controller = new ConversationalCreativeController(jdbc, new ObjectMapper(), siliconFlow);
        claims = new JwtService.Claims(1L, "consumer", "user", Instant.now().getEpochSecond() + 900);
    }

    @Test
    void deletingConversationKeepsGeneratedAssets() {
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'image','image_generated',?)", "{\"assetId\":7}");
        jdbc.update("INSERT INTO digital_asset(id,created_by) VALUES (7,1)");

        Map<String, Object> result = controller.delete(1L, claims);

        assertThat(result.get("deleted")).isEqualTo(true);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_conversation_session WHERE id=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_conversation_event WHERE session_id=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM digital_asset WHERE id=7", Integer.class)).isEqualTo(1);
    }

    @Test
    void naturalLanguageRequiresFinishedProductSizeBeforeGenerationConfirmation() {
        Map<String, Object> result = controller.chat(1L, Map.of(
                "message", "我想做一个合金冰箱贴，主题是祥云和古城墙"
        ), claims);

        assertThat(result.get("readyToGenerate")).isEqualTo(false);
        assertThat(result.get("generationConfirmationRequired")).isEqualTo(false);
        assertThat(result.get("stage")).isEqualTo("need_size");
        assertThat(result.get("quickReplies").toString()).contains("按推荐规格");
        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("productKey")).isEqualTo("souvenir-alloy-magnet");
        assertThat(brief.get("material")).isEqualTo("合金");
        assertThat(brief.get("mode")).isEqualTo("text");
        assertThat(String.valueOf(brief.get("inspiration"))).contains("祥云");
        assertMissingKeys(brief, "productSize");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM creative_conversation_event WHERE session_id=1 AND event_type='chat_state'",
                Integer.class)).isEqualTo(1);

        Map<String, Object> size = new LinkedHashMap<>();
        size.put("type", "size");
        size.put("value", "recommend");
        Map<String, Object> withSize = controller.chat(1L, Map.of("action", size), claims);
        assertThat(withSize.get("readyToGenerate")).isEqualTo(false);
        assertThat(withSize.get("generationConfirmationRequired")).isEqualTo(true);
        assertThat(withSize.get("stage")).isEqualTo("confirm_before_image");
        assertThat(((Map<?, ?>) withSize.get("brief")).get("productSize")).isEqualTo("60×60×4mm");
        assertThat(withSize.get("quickReplies").toString())
                .contains("没有补充，开始生成")
                .contains("我还要补充")
                .doesNotContain("按推荐规格");
        assertThat(jdbc.queryForObject("SELECT product_size FROM creative_conversation_session WHERE id=1", String.class))
                .isEqualTo("60×60×4mm");

        Map<String, Object> confirmation = new LinkedHashMap<>();
        confirmation.put("type", "confirm_generate");
        confirmation.put("value", "confirm");
        Map<String, Object> confirmed = controller.chat(1L, Map.of("action", confirmation), claims);
        assertThat(confirmed.get("readyToGenerate")).isEqualTo(true);
        assertThat(confirmed.get("generationConfirmationRequired")).isEqualTo(false);
    }

    @Test
    void bareNumericSizeAnswerAdvancesPastTheSizeQuestion() {
        controller.chat(1L, Map.of(
                "message", "我想做一个合金冰箱贴，主题是祥云和古城墙"
        ), claims);

        Map<String, Object> sized = controller.chat(1L, Map.of("message", "60"), claims);

        Map<?, ?> brief = (Map<?, ?>) sized.get("brief");
        assertThat(brief.get("productSize")).isEqualTo("约 60mm");
        assertThat(sized.get("stage")).isEqualTo("confirm_before_image");
        assertThat(String.valueOf(sized.get("assistantText"))).doesNotContain("这件产品想做多大");
    }

    @Test
    void recommendedSizeActionIsLocalAndRemainsAdvancedWhenSubmittedAgain() throws Exception {
        controller.chat(1L, Map.of(
                "message", "我想做一个合金冰箱贴，主题是祥云和古城墙"
        ), claims);

        Map<String, Object> size = new LinkedHashMap<>();
        size.put("type", "size");
        size.put("value", "recommend");
        Map<String, Object> first = controller.chat(1L, Map.of("action", size), claims);
        Map<String, Object> second = controller.chat(1L, Map.of("action", size), claims);

        assertThat(((Map<?, ?>) first.get("brief")).get("productSize")).isEqualTo("60×60×4mm");
        assertThat(((Map<?, ?>) second.get("brief")).get("productSize")).isEqualTo("60×60×4mm");
        assertThat(first.get("stage")).isEqualTo("confirm_before_image");
        assertThat(second.get("stage")).isEqualTo("confirm_before_image");
        assertThat(String.valueOf(second.get("assistantText"))).doesNotContain("这件产品想做多大");
        verify(siliconFlow, times(1)).chat(anyString(), anyString(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void explicitMaterialOutsideLegacyCatalogIsKeptAndAdvancesToSize() {
        controller.chat(1L, Map.of(
                "message", "我想做一个合金冰箱贴，主题是白色的比熊"
        ), claims);

        Map<String, Object> result = controller.chat(1L, Map.of("message", "我要毛线"), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("material")).isEqualTo("毛线");
        assertThat(result.get("stage")).isEqualTo("need_size");
        assertThat(String.valueOf(result.get("assistantText"))).doesNotContain("材质不确定");
    }

    @Test
    void recommendationMessageWritesMaterialAndSizeIntoGenerationBrief() {
        controller.chat(1L, Map.of(
                "message", "我想做一个合金冰箱贴，主题是祥云和古城墙"
        ), claims);

        Map<String, Object> result = controller.chat(1L, Map.of("message", "你帮我推荐"), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("material")).isEqualTo("合金");
        assertThat(brief.get("productSize")).isEqualTo("60×60×4mm");
        assertThat(result.get("stage")).isEqualTo("confirm_before_image");
        assertThat(String.valueOf(result.get("assistantText"))).contains("写入图片生成提示词");
    }

    @Test
    void sizeRecommendationDoesNotReplaceAnExplicitMaterial() {
        controller.chat(1L, Map.of(
                "message", "我想做一个合金冰箱贴，主题是白色的比熊"
        ), claims);
        controller.chat(1L, Map.of("message", "我要毛线"), claims);

        Map<String, Object> result = controller.chat(1L, Map.of("message", "你帮我推荐尺寸"), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("material")).isEqualTo("毛线");
        assertThat(brief.get("materialRecommended")).isEqualTo(false);
        assertThat(brief.get("productSize")).isEqualTo("60×60×4mm");
        assertThat(result.get("stage")).isEqualTo("confirm_before_image");
        assertThat(String.valueOf(result.get("assistantText")))
                .contains("60×60×4mm")
                .doesNotContain("推荐材质");
    }

    @Test
    void nonDimensionalCatalogSpecificationFallsBackToConcreteProductSize() {
        jdbc.update("INSERT INTO selection_option(option_key,category_key,name,subtitle,description,material,process,specification,tags,enabled,review_status,sort_order) VALUES ('souvenir-custom-display','souvenir','文化摆件','定制摆件','文化陈列产品','树脂','翻模','随型','摆件,陈列',1,'approved',2)");

        Map<String, Object> first = controller.chat(1L, Map.of(
                "message", "我想做一个树脂文化摆件，主题是金翅鸟"
        ), claims);
        assertThat(first.get("stage")).isEqualTo("need_size");

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "size");
        action.put("value", "recommend");
        Map<String, Object> result = controller.chat(1L, Map.of("action", action), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("productSize")).isEqualTo("150×150×200mm");
        assertThat(result.get("stage")).isEqualTo("confirm_before_image");
        assertThat(String.valueOf(result.get("assistantText"))).contains("150×150×200mm");
    }

    @Test
    void noMoreDetailConfirmationDoesNotCallChatModelAgain() throws Exception {
        controller.chat(1L, Map.of(
                "message", "我想做一个合金冰箱贴，主题是祥云和古城墙"
        ), claims);
        chooseRecommendedSize();

        Map<String, Object> confirmed = controller.chat(1L, Map.of("message", "没有了"), claims);

        assertThat(confirmed.get("readyToGenerate")).isEqualTo(true);
        assertThat(confirmed.get("stage")).isEqualTo("ready_for_image");
        verify(siliconFlow, times(1)).chat(anyString(), anyString(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void structuredProductAndMaterialChoicesDoNotBecomeInspiration() {
        Map<String, Object> productAction = new LinkedHashMap<>();
        productAction.put("type", "product");
        productAction.put("value", "souvenir-alloy-magnet");
        Map<String, Object> first = controller.chat(1L, Map.of("action", productAction), claims);
        assertThat(((Map<?, ?>) first.get("brief")).get("inspiration")).isNull();

        Map<String, Object> materialAction = new LinkedHashMap<>();
        materialAction.put("type", "material");
        materialAction.put("value", "合金");
        Map<String, Object> second = controller.chat(1L, Map.of("action", materialAction), claims);
        Map<?, ?> secondBrief = (Map<?, ?>) second.get("brief");
        assertThat(secondBrief.get("material")).isEqualTo("合金");
        assertThat(secondBrief.get("inspiration")).isNull();
        assertThat(second.get("readyToGenerate")).isEqualTo(false);
    }

    @Test
    void ordinaryQuestionOnlyAnswersAndCanBeExplicitlyAdopted() throws Exception {
        when(siliconFlow.chat(anyString(), anyString(), anyDouble(), anyInt(), anyInt()))
                .thenReturn("{\"intent\":\"answer_question\",\"reply\":\"西湖景区更适合轻量便携的地标文创。\",\"suggestedDirection\":\"西湖断桥主题的合金冰箱贴\",\"ready\":false}");

        Map<String, Object> question = controller.chat(1L, Map.of("message", "西湖景区什么卖得最好"), claims);
        Map<?, ?> questionBrief = (Map<?, ?>) question.get("brief");
        assertMissingKeys(questionBrief, "productKey", "inspiration", "material", "productSize");
        assertThat(question.get("stage")).isEqualTo("need_product");
        assertThat(String.valueOf(question.get("quickReplies"))).contains("把这个方向带入创作");

        Map<String, Object> adopt = new LinkedHashMap<>();
        adopt.put("type", "adopt_direction");
        adopt.put("value", "西湖断桥主题的合金冰箱贴");
        Map<String, Object> adopted = controller.chat(1L, Map.of("action", adopt), claims);
        Map<?, ?> adoptedBrief = (Map<?, ?>) adopted.get("brief");
        assertThat(adoptedBrief.get("inspiration")).isEqualTo("西湖断桥主题的合金冰箱贴");
        assertThat(adoptedBrief.get("inspirationSource")).isEqualTo("adopted_direction");
        assertThat(adoptedBrief.get("productKey")).isEqualTo("souvenir-alloy-magnet");
    }

    @Test
    void questionFallbackDoesNotBecomeInspirationWhenPlannerOmitsIntent() {
        Map<String, Object> result = controller.chat(1L, Map.of("message", "冰箱贴应该怎么设计？"), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertMissingKeys(brief, "productKey", "inspiration", "material", "productSize");
        assertThat(result.get("readyToGenerate")).isEqualTo(false);
    }

    @Test
    void ordinaryQuestionDisplaysPlainSiliconFlowAnswerWithoutCreativePrompting() throws Exception {
        when(siliconFlow.chat(anyString(), anyString(), anyDouble(), anyInt(), anyInt()))
                .thenReturn("广西景区更适合销售冰箱贴、钥匙扣和明信片，原因是轻便、易携带，也方便做地标主题。");

        Map<String, Object> result = controller.chat(1L, Map.of("message", "广西什么东西卖得好"), claims);

        assertThat(result.get("assistantText"))
                .isEqualTo("广西景区更适合销售冰箱贴、钥匙扣和明信片，原因是轻便、易携带，也方便做地标主题。");
        assertThat(result.get("stage")).isEqualTo("need_product");
        assertMissingKeys((Map<?, ?>) result.get("brief"), "productKey", "inspiration", "material", "productSize");
        verify(siliconFlow).chat(org.mockito.ArgumentMatchers.contains("不是创作流程引导器"),
                org.mockito.ArgumentMatchers.eq("广西什么东西卖得好"), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void ordinaryQuestionPreservesAConfirmedBriefWithoutTriggeringGeneration() throws Exception {
        controller.chat(1L, Map.of("message", "我想做一个合金冰箱贴，主题是祥云和古城墙"), claims);
        chooseRecommendedSize();
        Map<String, Object> confirmation = new LinkedHashMap<>();
        confirmation.put("type", "confirm_generate");
        confirmation.put("value", "confirm");
        controller.chat(1L, Map.of("action", confirmation), claims);
        when(siliconFlow.chat(anyString(), anyString(), anyDouble(), anyInt(), anyInt()))
                .thenReturn("{\"intent\":\"answer_question\",\"reply\":\"合金适合压铸和烤漆，结构稳定且便于量产。\",\"ready\":false}");

        Map<String, Object> result = controller.chat(1L, Map.of("message", "为什么冰箱贴适合用合金？"), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("generationConfirmed")).isEqualTo(true);
        assertThat(String.valueOf(brief.get("inspiration"))).contains("祥云");
        assertThat(result.get("assistantText")).isEqualTo("合金适合压铸和烤漆，结构稳定且便于量产。");
        assertThat(result.get("readyToGenerate")).isEqualTo(false);
        assertThat(result.get("generationConfirmationRequired")).isEqualTo(false);
        assertThat((List<?>) result.get("quickReplies")).isEmpty();
    }

    @Test
    void categoryChoicesCanReturnToTheTopLevelCatalog() {
        Map<String, Object> categoryAction = new LinkedHashMap<>();
        categoryAction.put("type", "category");
        categoryAction.put("value", "souvenir");

        Map<String, Object> categoryResult = controller.chat(1L, Map.of("action", categoryAction), claims);

        assertThat(categoryResult.get("stage")).isEqualTo("need_product");
        assertThat(String.valueOf(categoryResult.get("quickReplies")))
                .contains("返回选择大品类")
                .contains("type=edit")
                .contains("value=product")
                .contains("合金冰箱贴");

        Map<String, Object> backAction = new LinkedHashMap<>();
        backAction.put("type", "edit");
        backAction.put("value", "product");
        Map<String, Object> topLevelResult = controller.chat(1L, Map.of("action", backAction), claims);

        Map<?, ?> brief = (Map<?, ?>) topLevelResult.get("brief");
        assertMissingKeys(brief, "productKey", "productName", "categoryKey");
        assertThat(String.valueOf(topLevelResult.get("quickReplies")))
                .contains("纪念品")
                .doesNotContain("返回选择大品类");
    }

    @Test
    void additionalInputClearsPreviousGenerationConfirmation() {
        controller.chat(1L, Map.of("message", "我想做一个合金冰箱贴，主题是祥云和古城墙"), claims);
        chooseRecommendedSize();
        Map<String, Object> confirmation = new LinkedHashMap<>();
        confirmation.put("type", "confirm_generate");
        confirmation.put("value", "confirm");
        Map<String, Object> confirmed = controller.chat(1L, Map.of("action", confirmation), claims);
        assertThat(confirmed.get("readyToGenerate")).isEqualTo(true);

        Map<String, Object> requestMoreDetail = new LinkedHashMap<>();
        requestMoreDetail.put("type", "add_detail");
        Map<String, Object> awaitingDetail = controller.chat(1L, Map.of("action", requestMoreDetail), claims);
        assertThat(awaitingDetail.get("readyToGenerate")).isEqualTo(false);
        assertThat(awaitingDetail.get("stage")).isEqualTo("need_additional_detail");
        assertThat((List<?>) awaitingDetail.get("quickReplies")).isEmpty();

        Map<String, Object> changed = controller.chat(1L, Map.of("message", "再补充一只飞鸟，整体更简洁"), claims);
        assertThat(changed.get("readyToGenerate")).isEqualTo(false);
        assertThat(changed.get("generationConfirmationRequired")).isEqualTo(true);
        assertThat(((Map<?, ?>) changed.get("brief")).get("generationConfirmed")).isEqualTo(false);
    }

    @Test
    void editingProductClearsProductAndMaterialWithoutLosingInspiration() {
        controller.chat(1L, Map.of("message", "我想做一个合金冰箱贴，主题是祥云和古城墙"), claims);

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("type", "edit");
        edit.put("value", "product");
        Map<String, Object> result = controller.chat(1L, Map.of("action", edit), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertMissingKeys(brief, "productKey", "productName", "material");
        assertThat(brief.get("generationConfirmed")).isEqualTo(false);
        assertThat(String.valueOf(brief.get("inspiration"))).contains("祥云");
        assertThat(result.get("stage")).isEqualTo("need_product");

        Map<?, ?> restoredBrief = (Map<?, ?>) controller.chat(1L, Map.of(), claims).get("brief");
        assertMissingKeys(restoredBrief, "productKey", "productName", "material");
        assertThat(String.valueOf(restoredBrief.get("inspiration"))).contains("祥云");
        assertThat(jdbc.queryForObject("SELECT product_type FROM creative_conversation_session WHERE id=1", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT material FROM creative_conversation_session WHERE id=1", String.class)).isNull();
    }

    @Test
    void editingInspirationKeepsProductAndMaterialButRemovesPreviousSource() {
        controller.chat(1L, Map.of("message", "我想做一个合金冰箱贴，主题是祥云和古城墙"), claims);

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("type", "edit");
        edit.put("value", "inspiration");
        Map<String, Object> result = controller.chat(1L, Map.of("action", edit), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("productKey")).isEqualTo("souvenir-alloy-magnet");
        assertThat(brief.get("material")).isEqualTo("合金");
        assertMissingKeys(brief, "mode", "inspiration", "inspirationSource", "referenceAssetId");
        assertThat(brief.get("generationConfirmed")).isEqualTo(false);
        assertThat(result.get("stage")).isEqualTo("need_inspiration");
    }

    @Test
    void editingMaterialKeepsProductAndInspiration() {
        controller.chat(1L, Map.of("message", "我想做一个合金冰箱贴，主题是祥云和古城墙"), claims);

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("type", "edit");
        edit.put("value", "material");
        Map<String, Object> result = controller.chat(1L, Map.of("action", edit), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("productKey")).isEqualTo("souvenir-alloy-magnet");
        assertThat(String.valueOf(brief.get("inspiration"))).contains("祥云");
        assertMissingKeys(brief, "material", "materialRecommended");
        assertThat(brief.get("generationConfirmed")).isEqualTo(false);
        assertThat(result.get("stage")).isEqualTo("need_material");
    }

    @Test
    void customSizeIsPersistedAndCanBeEditedWithoutLosingTheRestOfTheBrief() {
        controller.chat(1L, Map.of("message", "我想做一个合金冰箱贴，主题是祥云和古城墙"), claims);

        Map<String, Object> size = new LinkedHashMap<>();
        size.put("type", "size");
        size.put("value", "65×55×4mm");
        Map<String, Object> sized = controller.chat(1L, Map.of("action", size), claims);
        assertThat(((Map<?, ?>) sized.get("brief")).get("productSize")).isEqualTo("65×55×4mm");
        assertThat(sized.get("stage")).isEqualTo("confirm_before_image");

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("type", "edit");
        edit.put("value", "size");
        Map<String, Object> edited = controller.chat(1L, Map.of("action", edit), claims);
        Map<?, ?> brief = (Map<?, ?>) edited.get("brief");
        assertThat(brief.get("productKey")).isEqualTo("souvenir-alloy-magnet");
        assertThat(brief.get("material")).isEqualTo("合金");
        assertThat(String.valueOf(brief.get("inspiration"))).contains("祥云");
        assertMissingKeys(brief, "productSize");
        assertThat(edited.get("stage")).isEqualTo("need_size");
        assertThat(jdbc.queryForObject("SELECT product_size FROM creative_conversation_session WHERE id=1", String.class)).isNull();
    }

    @Test
    void editingRejectsUnknownTargetWithoutChangingBrief() {
        controller.chat(1L, Map.of("message", "我想做一个合金冰箱贴，主题是祥云和古城墙"), claims);
        Map<?, ?> before = (Map<?, ?>) controller.chat(1L, Map.of(), claims).get("brief");

        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("type", "edit");
        edit.put("value", "unknown");

        assertThatThrownBy(() -> controller.chat(1L, Map.of("action", edit), claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("修改目标无效");
        Map<?, ?> after = (Map<?, ?>) controller.chat(1L, Map.of(), claims).get("brief");
        assertThat(after).isEqualTo(before);
    }

    @Test
    void imageActionRejectsAnAssetOwnedByAnotherUser() {
        Map<String, Object> imageAction = new LinkedHashMap<>();
        imageAction.put("type", "image");
        imageAction.put("value", "99");

        assertThatThrownBy(() -> controller.chat(1L, Map.of("action", imageAction), claims))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatus())
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void templateActionNeverStartsGeneration() {
        Map<String, Object> templateAction = new LinkedHashMap<>();
        templateAction.put("type", "template");

        Map<String, Object> result = controller.chat(1L, Map.of("action", templateAction), claims);

        assertThat(result.get("readyToGenerate")).isEqualTo(false);
        assertThat(result.get("stage")).isEqualTo("template_unavailable");
        assertThat(result.get("assistantText")).asString().contains("开发中");
    }

    @Test
    void legacyEventAliasesAreRestoredIntoChatBrief() {
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'mode','mode_selected',?)",
                "{\"mode\":\"image\"}");
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'product','product_selected',?)",
                "{\"productKey\":\"souvenir-alloy-magnet\",\"productType\":\"合金冰箱贴\"}");
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'inspiration','text_inspiration_submitted',?)",
                "{\"inspirationText\":\"祥云和古城墙\"}");
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'material','material_selected',?)",
                "{\"materialName\":\"合金\"}");
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'size','size_selected',?)",
                "{\"productSize\":\"60×60×4mm\"}");

        Map<String, Object> result = controller.chat(1L, Map.of(), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("productName")).isEqualTo("合金冰箱贴");
        assertThat(brief.get("inspiration")).isEqualTo("祥云和古城墙");
        assertThat(brief.get("material")).isEqualTo("合金");
        assertThat(brief.get("productSize")).isEqualTo("60×60×4mm");
        assertThat(brief.get("mode")).isEqualTo("text");
        assertThat(result.get("readyToGenerate")).isEqualTo(false);
        assertThat(result.get("generationConfirmationRequired")).isEqualTo(true);
    }

    @Test
    void legacyImageAssetAliasIsRestoredWithoutAskingForAnotherUpload() {
        jdbc.update("INSERT INTO digital_asset(id,created_by) VALUES (7,1)");
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'mode','mode_selected',?)",
                "{\"mode\":\"image\"}");
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'product','product_selected',?)",
                "{\"productKey\":\"souvenir-alloy-magnet\",\"productType\":\"合金冰箱贴\"}");
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'inspiration','image_inspiration_uploaded',?)",
                "{\"inputAssetId\":7}");
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'material','material_selected',?)",
                "{\"materialName\":\"合金\"}");
        jdbc.update("INSERT INTO creative_conversation_event(session_id,user_id,step,event_type,payload_json) VALUES (1,1,'size','size_selected',?)",
                "{\"productSize\":\"60×60×4mm\"}");

        Map<String, Object> result = controller.chat(1L, Map.of(), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("referenceAssetId")).isEqualTo(7L);
        assertThat(brief.get("inspirationSource")).isEqualTo("image");
        assertThat(brief.get("productSize")).isEqualTo("60×60×4mm");
        assertThat(result.get("readyToGenerate")).isEqualTo(false);
        assertThat(result.get("generationConfirmationRequired")).isEqualTo(true);
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100), role VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE creative_project (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_no VARCHAR(120), user_id BIGINT, tenant_id BIGINT, name VARCHAR(180), theme VARCHAR(300), status VARCHAR(40), current_phase VARCHAR(40), current_version_id BIGINT, next_action VARCHAR(160), metadata_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_project_version (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT, version_no VARCHAR(100), version_number INT, version_label VARCHAR(160), phase VARCHAR(40), status VARCHAR(30), frozen_at TIMESTAMP NULL, frozen_by BIGINT NULL, freeze_reason VARCHAR(500), freeze_hash VARCHAR(128), brief_json CLOB, metadata_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_project_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, project_id BIGINT, version_id BIGINT, user_id BIGINT, event_type VARCHAR(60), from_phase VARCHAR(40), to_phase VARCHAR(40), next_action VARCHAR(160), actor_type VARCHAR(30), actor_id BIGINT, idempotency_key VARCHAR(120), payload_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_conversation_session (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_no VARCHAR(80), user_id BIGINT, project_id BIGINT, version_id BIGINT, mode VARCHAR(24), product_type VARCHAR(120), material VARCHAR(120), product_size VARCHAR(120), status VARCHAR(24), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_conversation_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT, user_id BIGINT, project_id BIGINT, version_id BIGINT, step VARCHAR(40), event_type VARCHAR(60), payload_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE selection_category (id BIGINT AUTO_INCREMENT PRIMARY KEY, category_key VARCHAR(60), name VARCHAR(80), enabled INT, review_status VARCHAR(30))");
        jdbc.execute("CREATE TABLE selection_option (id BIGINT AUTO_INCREMENT PRIMARY KEY, option_key VARCHAR(80), category_key VARCHAR(60), name VARCHAR(120), subtitle VARCHAR(200), description VARCHAR(500), material VARCHAR(500), process VARCHAR(1000), specification VARCHAR(500), tags VARCHAR(1000), enabled INT, review_status VARCHAR(30), sort_order INT)");
        jdbc.execute("CREATE TABLE digital_asset (id BIGINT AUTO_INCREMENT PRIMARY KEY, created_by BIGINT)");
    }

    private void assertMissingKeys(Map<?, ?> values, String... keys) {
        for (String key : keys) assertThat(values.containsKey(key)).as("missing key %s", key).isFalse();
    }

    private void seedUserAndSession() {
        jdbc.update("INSERT INTO user(id,username,role,status) VALUES (1,'consumer','user','active')");
        jdbc.update("INSERT INTO creative_conversation_session(id,session_no,user_id,status) VALUES (1,'CCS-test',1,'draft')");
        jdbc.update("INSERT INTO selection_category(category_key,name,enabled,review_status) VALUES ('souvenir','纪念品',1,'approved')");
        jdbc.update("INSERT INTO selection_option(option_key,category_key,name,subtitle,description,material,process,specification,tags,enabled,review_status,sort_order) VALUES ('souvenir-alloy-magnet','souvenir','合金冰箱贴','景区纪念','文化纪念品','合金','压铸/烤漆','4-8cm','博物馆,景区,纪念',1,'approved',1)");
    }

    private void chooseRecommendedSize() {
        Map<String, Object> size = new LinkedHashMap<>();
        size.put("type", "size");
        size.put("value", "recommend");
        controller.chat(1L, Map.of("action", size), claims);
    }
}
