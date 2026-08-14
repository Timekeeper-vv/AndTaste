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

        assertThat(result.get("readyToGenerate")).isEqualTo(true);
        Map<?, ?> brief = (Map<?, ?>) result.get("brief");
        assertThat(brief.get("productKey")).isEqualTo("souvenir-alloy-magnet");
        assertThat(brief.get("material")).isEqualTo("合金");
        assertThat(brief.get("mode")).isEqualTo("text");
        assertThat(String.valueOf(brief.get("inspiration"))).contains("祥云");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM creative_conversation_event WHERE session_id=1 AND event_type='chat_state'",
                Integer.class)).isEqualTo(1);
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
        assertThat(result.get("readyToGenerate")).isEqualTo(true);
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
        assertThat(result.get("readyToGenerate")).isEqualTo(true);
    }

    private void createSchema() {
        jdbc.execute("CREATE TABLE user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100), role VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE creative_conversation_session (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_no VARCHAR(80), user_id BIGINT, mode VARCHAR(24), product_type VARCHAR(120), material VARCHAR(120), status VARCHAR(24), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_conversation_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id BIGINT, user_id BIGINT, step VARCHAR(40), event_type VARCHAR(60), payload_json CLOB, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE selection_category (id BIGINT AUTO_INCREMENT PRIMARY KEY, category_key VARCHAR(60), name VARCHAR(80), enabled INT, review_status VARCHAR(30))");
        jdbc.execute("CREATE TABLE selection_option (id BIGINT AUTO_INCREMENT PRIMARY KEY, option_key VARCHAR(80), category_key VARCHAR(60), name VARCHAR(120), subtitle VARCHAR(200), description VARCHAR(500), material VARCHAR(500), process VARCHAR(1000), tags VARCHAR(1000), enabled INT, review_status VARCHAR(30), sort_order INT)");
        jdbc.execute("CREATE TABLE digital_asset (id BIGINT AUTO_INCREMENT PRIMARY KEY, created_by BIGINT)");
    }

    private void seedUserAndSession() {
        jdbc.update("INSERT INTO user(id,username,role,status) VALUES (1,'consumer','user','active')");
        jdbc.update("INSERT INTO creative_conversation_session(id,session_no,user_id,status) VALUES (1,'CCS-test',1,'draft')");
        jdbc.update("INSERT INTO selection_category(category_key,name,enabled,review_status) VALUES ('souvenir','纪念品',1,'approved')");
        jdbc.update("INSERT INTO selection_option(option_key,category_key,name,subtitle,description,material,process,tags,enabled,review_status,sort_order) VALUES ('souvenir-alloy-magnet','souvenir','合金冰箱贴','景区纪念','文化纪念品','合金','压铸/烤漆','博物馆,景区,纪念',1,'approved',1)");
    }
}
