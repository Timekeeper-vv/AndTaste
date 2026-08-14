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
    void naturalLanguageCompletesProductInspirationAndMaterialWithoutModelFields() {
        Map<String, Object> result = controller.chat(1L, Map.of(
                "message", "我想做一个合金冰箱贴，主题是祥云和古城墙"
        ), claims);

        assertThat(result.get("readyToGenerate")).isEqualTo(false);
        assertThat(result.get("generationConfirmationRequired")).isEqualTo(true);
        assertThat(result.get("quickReplies").toString()).contains("没有补充，开始生成");
        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("productKey")).isEqualTo("souvenir-alloy-magnet");
        assertThat(brief.get("material")).isEqualTo("合金");
        assertThat(brief.get("mode")).isEqualTo("text");
        assertThat(String.valueOf(brief.get("inspiration"))).contains("祥云");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM creative_conversation_event WHERE session_id=1 AND event_type='chat_state'",
                Integer.class)).isEqualTo(1);

        Map<String, Object> confirmation = new LinkedHashMap<>();
        confirmation.put("type", "confirm_generate");
        confirmation.put("value", "confirm");
        Map<String, Object> confirmed = controller.chat(1L, Map.of("action", confirmation), claims);
        assertThat(confirmed.get("readyToGenerate")).isEqualTo(true);
        assertThat(confirmed.get("generationConfirmationRequired")).isEqualTo(false);
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
    void additionalInputClearsPreviousGenerationConfirmation() {
        controller.chat(1L, Map.of("message", "我想做一个合金冰箱贴，主题是祥云和古城墙"), claims);
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

        Map<String, Object> result = controller.chat(1L, Map.of(), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("productName")).isEqualTo("合金冰箱贴");
        assertThat(brief.get("inspiration")).isEqualTo("祥云和古城墙");
        assertThat(brief.get("material")).isEqualTo("合金");
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

        Map<String, Object> result = controller.chat(1L, Map.of(), claims);

        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("referenceAssetId")).isEqualTo(7L);
        assertThat(brief.get("inspirationSource")).isEqualTo("image");
        assertThat(result.get("readyToGenerate")).isEqualTo(false);
        assertThat(result.get("generationConfirmationRequired")).isEqualTo(true);
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100), role VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE creative_conversation_session (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_no VARCHAR(80), user_id BIGINT, mode VARCHAR(24), product_type VARCHAR(120), material VARCHAR(120), status VARCHAR(24), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_conversation_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT, user_id BIGINT, step VARCHAR(40), event_type VARCHAR(60), payload_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE selection_category (id BIGINT AUTO_INCREMENT PRIMARY KEY, category_key VARCHAR(60), name VARCHAR(80), enabled INT, review_status VARCHAR(30))");
        jdbc.execute("CREATE TABLE selection_option (id BIGINT AUTO_INCREMENT PRIMARY KEY, option_key VARCHAR(80), category_key VARCHAR(60), name VARCHAR(120), subtitle VARCHAR(200), description VARCHAR(500), material VARCHAR(500), process VARCHAR(1000), tags VARCHAR(1000), enabled INT, review_status VARCHAR(30), sort_order INT)");
        jdbc.execute("CREATE TABLE digital_asset (id BIGINT AUTO_INCREMENT PRIMARY KEY, created_by BIGINT)");
    }

    private void assertMissingKeys(Map<?, ?> values, String... keys) {
        for (String key : keys) assertThat(values.containsKey(key)).as("missing key %s", key).isFalse();
    }

    private void seedUserAndSession() {
        jdbc.update("INSERT INTO user(id,username,role,status) VALUES (1,'consumer','user','active')");
        jdbc.update("INSERT INTO creative_conversation_session(id,session_no,user_id,status) VALUES (1,'CCS-test',1,'draft')");
        jdbc.update("INSERT INTO selection_category(category_key,name,enabled,review_status) VALUES ('souvenir','纪念品',1,'approved')");
        jdbc.update("INSERT INTO selection_option(option_key,category_key,name,subtitle,description,material,process,tags,enabled,review_status,sort_order) VALUES ('souvenir-alloy-magnet','souvenir','合金冰箱贴','景区纪念','文化纪念品','合金','压铸/烤漆','博物馆,景区,纪念',1,'approved',1)");
    }
}
