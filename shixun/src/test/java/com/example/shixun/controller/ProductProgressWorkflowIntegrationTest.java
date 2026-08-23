package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the consumer path used by the mini-program product-progress page.
 * The test calls the authenticated HTTP endpoints and checks the durable rows
 * rather than relying only on client-side optimistic state.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductProgressWorkflowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;
    @Autowired PaymentController paymentController;

    @DynamicPropertySource
    static void workflowProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:product_progress_workflow;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
    }

    @BeforeEach
    void resetData() {
        createWorkflowSchema();
        jdbc.update("DELETE FROM consumer_production_request");
        jdbc.update("DELETE FROM consumer_sample_fee_catalog");
        jdbc.update("DELETE FROM commercial_application_revision");
        jdbc.update("DELETE FROM commercial_professional_guidance_request");
        jdbc.update("DELETE FROM payment_order");
        jdbc.update("DELETE FROM creative_multiview_bundle_item");
        jdbc.update("DELETE FROM creative_multiview_bundle");
        jdbc.update("DELETE FROM commercial_application_audit_log");
        jdbc.update("DELETE FROM creative_consignment_application");
        jdbc.update("DELETE FROM creative_quote_request");
        jdbc.update("DELETE FROM selection_demand_request");
        jdbc.update("DELETE FROM creative_product_template");
        jdbc.update("DELETE FROM user_selection_favorite");
        jdbc.update("DELETE FROM selection_option");
        jdbc.update("DELETE FROM selection_category");
        jdbc.update("DELETE FROM channel_directory");
        jdbc.update("DELETE FROM digital_asset");
        jdbc.update("DELETE FROM user");
    }

    @Test
    void submissionsCreateDurableProgressRecordsForOnlyTheSubmittingAccount() throws Exception {
        TestUser creator = createUser("workflow-creator", "user");
        TestUser otherConsumer = createUser("workflow-other", "user");
        TestUser reviewer = createUser("workflow-reviewer", "admin");
        seedProductCatalog();

        long imageAssetId = createAsset(creator, "AST-WORKFLOW-IMAGE", "image", "draft");

        mvc.perform(put("/api/creative/ai/consumer-assets/{id}/submit-review", imageAssetId)
                        .header("Authorization", "Bearer " + creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("purpose", "personal"))))
                .andExpect(status().isBadRequest());
        assertThat(assetStatus(imageAssetId)).isEqualTo("draft");

        JsonNode creatorAssets = request(get("/api/creative/ai/assets"), creator.token(), null);
        assertThat(containsId(creatorAssets, imageAssetId)).isTrue();
        assertThat(field(assetById(creatorAssets, imageAssetId), "status").asText()).isEqualTo("draft");
        JsonNode otherAssets = request(get("/api/creative/ai/assets"), otherConsumer.token(), null);
        assertThat(containsId(otherAssets, imageAssetId)).isFalse();

        JsonNode demand = request(post("/api/selection/demands"), creator.token(), Map.of(
                "optionKey", "souvenir-alloy-magnet",
                "assetId", imageAssetId,
                "theme", "青铜云纹",
                "audience", "文博游客",
                "occasion", "旅行伴手礼"));
        String demandNo = demand.path("requestNo").asText();
        assertThat(demandNo).startsWith("SDR");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM selection_demand_request WHERE request_no=? AND user_id=?", Integer.class, demandNo, creator.id()))
                .isEqualTo(1);

        JsonNode quote = request(post("/api/commercial/consumer/quote-requests"), creator.token(), Map.of(
                "templateCode", "alloy-magnet",
                "assetId", imageAssetId,
                "requestType", "sample",
                "quantity", 1,
                "purpose", "personal",
                "copyrightBasis", "original",
                "copyrightConfirmed", true));
        String quoteNo = quote.path("requestNo").asText();
        assertThat(quote.path("requestKind").asText()).isEqualTo("quote");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_quote_request WHERE request_no=? AND user_id=?", Integer.class, quoteNo, creator.id()))
                .isEqualTo(1);

        JsonNode consignment = request(post("/api/commercial/consumer/consignment-applications"), creator.token(), Map.of(
                "templateCode", "alloy-magnet",
                "assetId", imageAssetId,
                "channelId", 1,
                "copyrightBasis", "original",
                "copyrightConfirmed", true));
        String consignmentNo = consignment.path("requestNo").asText();
        assertThat(consignment.path("requestKind").asText()).isEqualTo("consignment");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_consignment_application WHERE application_no=? AND user_id=?", Integer.class, consignmentNo, creator.id()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commercial_application_audit_log WHERE operator=?", Integer.class, "workflow-creator"))
                .isEqualTo(2);

        long modelAssetId = createAsset(creator, "AST-WORKFLOW-MODEL", "model", "draft");
        request(put("/api/creative/ai/consumer-assets/{id}/submit-review", modelAssetId), creator.token(), Map.of("purpose", "personal"));
        reviewAsset(reviewer, modelAssetId, "approved");

        // The selected product is bound in the generated asset metadata. The
        // mini-program deliberately does not ask the user to select it again.
        JsonNode production = request(post("/api/creative/ai/consumer-production/submit"), creator.token(), Map.of(
                "assetId", modelAssetId,
                "requestType", "sample",
                "idempotencyKey", "workflow-submit-1",
                "quantity", 1,
                "purpose", "personal",
                "recipientName", "测试用户",
                "recipientPhone", "13800138000",
                "recipientAddress", "北京市东城区测试路 1 号"));
        String productionNo = production.path("requestNo").asText();
        assertThat(production.path("success").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM consumer_production_request WHERE request_no=?", String.class, productionNo))
                .isEqualTo("review");
        assertThat(jdbc.queryForObject("SELECT sample_product_name FROM consumer_production_request WHERE request_no=?", String.class, productionNo))
                .isEqualTo("合金冰箱贴");
        assertThat(jdbc.queryForObject("SELECT sample_fee_yuan FROM consumer_production_request WHERE request_no=?", BigDecimal.class, productionNo))
                .isEqualByComparingTo("2000.00");

        JsonNode replayedProduction = request(post("/api/creative/ai/consumer-production/submit"), creator.token(), Map.of(
                "assetId", modelAssetId,
                "requestType", "sample",
                "idempotencyKey", "workflow-submit-1",
                "quantity", 1,
                "purpose", "personal",
                "recipientName", "测试用户",
                "recipientPhone", "13800138000",
                "recipientAddress", "北京市东城区测试路 1 号"));
        assertThat(replayedProduction.path("id").asLong()).isEqualTo(production.path("id").asLong());
        assertThat(replayedProduction.path("idempotent").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM consumer_production_request WHERE user_id=?", Integer.class, creator.id()))
                .isEqualTo(1);

        JsonNode progress = request(get("/api/commercial/consumer/requests"), creator.token(), null);
        assertThat(progress.path("quoteRequests").size()).isEqualTo(1);
        assertThat(progress.path("consignmentApplications").size()).isEqualTo(1);
        assertThat(progress.path("selectionDemands").size()).isEqualTo(1);
        assertThat(field(progress.path("quoteRequests").get(0), "requestNo").asText()).isEqualTo(quoteNo);
        assertThat(field(progress.path("consignmentApplications").get(0), "applicationNo").asText()).isEqualTo(consignmentNo);
        assertThat(field(progress.path("selectionDemands").get(0), "requestNo").asText()).isEqualTo(demandNo);

        JsonNode productionRequests = request(get("/api/creative/ai/consumer-production/my"), creator.token(), null);
        assertThat(productionRequests.size()).isEqualTo(1);
        assertThat(field(productionRequests.get(0), "requestNo").asText()).isEqualTo(productionNo);
        assertThat(field(productionRequests.get(0), "sampleProductName").asText()).isEqualTo("合金冰箱贴");

        JsonNode otherProgress = request(get("/api/commercial/consumer/requests"), otherConsumer.token(), null);
        assertThat(otherProgress.path("quoteRequests").size()).isZero();
        assertThat(otherProgress.path("consignmentApplications").size()).isZero();
        assertThat(otherProgress.path("selectionDemands").size()).isZero();
        JsonNode otherProductionRequests = request(get("/api/creative/ai/consumer-production/my"), otherConsumer.token(), null);
        assertThat(otherProductionRequests.size()).isZero();
    }

    @Test
    void rejectedApplicationCanBeResubmittedWithOwnedLocalImageAndUseProfessionalGuidance() throws Exception {
        TestUser creator = createUser("revision-creator", "user");
        TestUser otherConsumer = createUser("revision-other", "user");
        TestUser reviewer = createUser("revision-reviewer", "admin");
        seedProductCatalog();

        long originalAssetId = createAsset(creator, "AST-REVISION-ORIGINAL", "image", "draft");
        JsonNode quote = request(post("/api/commercial/consumer/quote-requests"), creator.token(), Map.of(
                "templateCode", "alloy-magnet",
                "assetId", originalAssetId,
                "requestType", "sample",
                "quantity", 1,
                "purpose", "personal",
                "copyrightBasis", "original",
                "copyrightConfirmed", true));
        long quoteId = quote.path("id").asLong();

        request(put("/api/commercial/admin/quote-requests/{id}", quoteId), reviewer.token(), Map.of(
                "status", "rejected", "operatorComment", "请调整主体比例并补充可生产细节"));

        // A revision must be a newly uploaded local image, not another existing
        // generated asset from the user's work library.
        mvc.perform(post("/api/commercial/consumer/application-revisions")
                        .header("Authorization", "Bearer " + creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "applicationType", "quote", "applicationId", quoteId, "assetId", originalAssetId))))
                .andExpect(status().isConflict());

        long otherUsersUpload = createAsset(otherConsumer, "AST-REVISION-OTHER", "image", "draft", "upload");
        mvc.perform(post("/api/commercial/consumer/application-revisions")
                        .header("Authorization", "Bearer " + creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "applicationType", "quote", "applicationId", quoteId, "assetId", otherUsersUpload))))
                .andExpect(status().isForbidden());

        long localRevisionAsset = createAsset(creator, "AST-REVISION-LOCAL", "image", "draft", "upload");
        JsonNode resubmission = request(post("/api/commercial/consumer/application-revisions"), creator.token(), Map.of(
                "applicationType", "quote", "applicationId", quoteId, "assetId", localRevisionAsset));
        assertThat(resubmission.path("status").asText()).isEqualTo("new");
        assertThat(jdbc.queryForObject("SELECT asset_id FROM creative_quote_request WHERE id=?", Long.class, quoteId)).isEqualTo(localRevisionAsset);
        assertThat(jdbc.queryForObject("SELECT status FROM creative_quote_request WHERE id=?", String.class, quoteId)).isEqualTo("new");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM commercial_application_revision WHERE application_type='quote' AND application_id=? AND asset_id=?", Integer.class, quoteId, localRevisionAsset)).isEqualTo(1);

        request(put("/api/commercial/admin/quote-requests/{id}", quoteId), reviewer.token(), Map.of(
                "status", "rejected", "operatorComment", "建议先接受专业指导后修改"));
        JsonNode guidance = request(post("/api/commercial/consumer/professional-guidance"), creator.token(), Map.of(
                "applicationType", "quote", "applicationId", quoteId));
        long guidanceId = guidance.path("guidanceId").asLong();
        assertThat(guidance.path("status").asText()).isEqualTo("requested");
        JsonNode adminGuidance = request(get("/api/commercial/admin/professional-guidance?status=requested"), reviewer.token(), null);
        assertThat(adminGuidance.size()).isEqualTo(1);
        assertThat(field(adminGuidance.get(0), "id").asLong()).isEqualTo(guidanceId);
        assertThat(field(adminGuidance.get(0), "applicationNo").asText()).isEqualTo(quote.path("requestNo").asText());
        // Older cached admin bundles sent the generic commercial status `new`.
        // It must resolve to the first professional-guidance state rather than
        // fail the whole page with a server error.
        JsonNode legacyAdminGuidance = request(get("/api/commercial/admin/professional-guidance?status=new"), reviewer.token(), null);
        assertThat(legacyAdminGuidance.size()).isEqualTo(1);
        assertThat(field(legacyAdminGuidance.get(0), "id").asLong()).isEqualTo(guidanceId);
        mvc.perform(post("/api/commercial/consumer/professional-guidance")
                        .header("Authorization", "Bearer " + creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("applicationType", "quote", "applicationId", quoteId))))
                .andExpect(status().isConflict());

        request(put("/api/commercial/admin/professional-guidance/{id}", guidanceId), reviewer.token(), Map.of(
                "status", "quoted", "quotedFeeYuan", new BigDecimal("199.00"),
                "quotedLeadTime", "2 个工作日", "operatorComment", "包含视觉、工艺与修改路径建议"));
        JsonNode feed = request(get("/api/commercial/consumer/requests"), creator.token(), null);
        assertThat(feed.path("guidanceRequests").size()).isEqualTo(1);
        JsonNode guidanceRow = feed.path("guidanceRequests").get(0);
        assertThat(field(guidanceRow, "id").asLong()).isEqualTo(guidanceId);
        assertThat(field(guidanceRow, "status").asText()).isEqualTo("quoted");
        assertThat(field(guidanceRow, "paymentStatus").asText()).isEqualTo("unpaid");
        assertThat(field(feed.path("summary"), "professionalGuidanceCount").asInt()).isEqualTo(1);

        // Payment confirmation is normally executed by the verified WeChat
        // callback. Invoke the same server-side business handler here so this
        // test covers the state transition without calling an external gateway.
        String guidancePaymentNo = "PAY-GUIDANCE-" + guidanceId;
        createPendingPayment(creator.id(), guidancePaymentNo, "commercial_guidance_" + guidanceId);
        jdbc.update("UPDATE commercial_professional_guidance_request SET payment_status='pending',payment_order_no=? WHERE id=?",
                guidancePaymentNo, guidanceId);
        confirmPayment(creator.id(), guidancePaymentNo, "commercial_guidance_" + guidanceId, "WX-GUIDANCE-1");
        assertThat(jdbc.queryForObject("SELECT status FROM commercial_professional_guidance_request WHERE id=?", String.class, guidanceId))
                .isEqualTo("in_progress");
        assertThat(jdbc.queryForObject("SELECT payment_status FROM commercial_professional_guidance_request WHERE id=?", String.class, guidanceId))
                .isEqualTo("paid");

        request(put("/api/commercial/admin/professional-guidance/{id}", guidanceId), reviewer.token(), Map.of(
                "status", "completed",
                "operatorComment", "已完成专业指导",
                "guidanceResult", "保留云纹主体，压缩边缘层次，并将厚度控制为 4mm 以内。"));
        assertThat(jdbc.queryForObject("SELECT status FROM commercial_professional_guidance_request WHERE id=?", String.class, guidanceId))
                .isEqualTo("completed");

        long guidedLocalRevisionAsset = createAsset(creator, "AST-REVISION-GUIDED-LOCAL", "image", "draft", "upload");
        JsonNode guidedResubmission = request(post("/api/commercial/consumer/application-revisions"), creator.token(), Map.of(
                "applicationType", "quote", "applicationId", quoteId, "assetId", guidedLocalRevisionAsset,
                "note", "已根据专业指导完成本地修改"));
        assertThat(guidedResubmission.path("status").asText()).isEqualTo("new");
        assertThat(jdbc.queryForObject("SELECT asset_id FROM creative_quote_request WHERE id=?", Long.class, quoteId))
                .isEqualTo(guidedLocalRevisionAsset);

        request(put("/api/commercial/admin/quote-requests/{id}", quoteId), reviewer.token(), Map.of(
                "status", "quoted", "quotedUnitPrice", new BigDecimal("36.00"),
                "quotedTotalPrice", new BigDecimal("36.00"), "quotedLeadTime", "7 个工作日",
                "operatorComment", "修改作品已通过工艺评估，可进入打样"));
        JsonNode accepted = request(post("/api/commercial/consumer/quote-requests/{id}/accept", quoteId), creator.token(), null);
        assertThat(accepted.path("status").asText()).isEqualTo("accepted");
        assertThat(jdbc.queryForObject("SELECT sample_payment_status FROM creative_quote_request WHERE id=?", String.class, quoteId))
                .isEqualTo("unpaid");

        String samplePaymentNo = "PAY-SAMPLE-" + quoteId;
        createPendingPayment(creator.id(), samplePaymentNo, "commercial_quote_sample_" + quoteId);
        jdbc.update("UPDATE creative_quote_request SET sample_payment_status='pending',sample_payment_order_no=? WHERE id=?",
                samplePaymentNo, quoteId);
        confirmPayment(creator.id(), samplePaymentNo, "commercial_quote_sample_" + quoteId, "WX-SAMPLE-1");
        assertThat(jdbc.queryForObject("SELECT sample_payment_status FROM creative_quote_request WHERE id=?", String.class, quoteId))
                .isEqualTo("paid");
    }

    @Test
    void threeViewsAreReviewedAsOneBundleBeforeSampleRequest() throws Exception {
        TestUser creator = createUser("multiview-creator", "user");
        TestUser otherConsumer = createUser("multiview-other", "user");
        TestUser reviewer = createUser("multiview-reviewer", "admin");
        seedProductCatalog();

        long inputAssetId = createAsset(creator, "AST-MV-INPUT", "image", "draft");
        long frontAssetId = createAsset(creator, "AST-MV-FRONT", "image", "draft");
        long leftAssetId = createAsset(creator, "AST-MV-LEFT", "image", "draft");
        long backAssetId = createAsset(creator, "AST-MV-BACK", "image", "draft");

        Map<String, Object> incompleteBundle = Map.of(
                "inputAssetId", inputAssetId,
                "productKey", "souvenir-alloy-magnet",
                "productName", "合金冰箱贴",
                "material", "锌合金",
                "productSize", "60mm",
                "viewCount", 3,
                "images", List.of(
                        Map.of("view", "front", "assetId", frontAssetId),
                        Map.of("view", "left", "assetId", leftAssetId)));
        mvc.perform(post("/api/creative/ai/consumer-multiview-bundles")
                        .header("Authorization", "Bearer " + creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(incompleteBundle)))
                .andExpect(status().isBadRequest());

        Map<String, Object> completeBundle = Map.of(
                "inputAssetId", inputAssetId,
                "productKey", "souvenir-alloy-magnet",
                "productName", "合金冰箱贴",
                "material", "锌合金",
                "productSize", "60mm",
                "viewCount", 3,
                "images", List.of(
                        Map.of("view", "front", "assetId", frontAssetId),
                        Map.of("view", "left", "assetId", leftAssetId),
                        Map.of("view", "back", "assetId", backAssetId)));

        mvc.perform(post("/api/creative/ai/consumer-multiview-bundles")
                        .header("Authorization", "Bearer " + otherConsumer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(completeBundle)))
                .andExpect(status().isForbidden());

        JsonNode created = request(post("/api/creative/ai/consumer-multiview-bundles"), creator.token(), completeBundle);
        long bundleId = field(created, "id").asLong();
        assertThat(field(created, "status").asText()).isEqualTo("draft");
        assertThat(field(created, "images").size()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_multiview_bundle", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_multiview_bundle_item WHERE bundle_id=?", Integer.class, bundleId)).isEqualTo(3);
        JsonNode creatorAssetsAfterBundle = request(get("/api/creative/ai/assets"), creator.token(), null);
        assertThat(containsId(creatorAssetsAfterBundle, frontAssetId)).isFalse();
        assertThat(containsId(creatorAssetsAfterBundle, leftAssetId)).isFalse();
        assertThat(containsId(creatorAssetsAfterBundle, backAssetId)).isFalse();

        // A draft cannot be approved directly; the user must explicitly
        // submit the complete package first.
        mvc.perform(put("/api/creative/ai/consumer-multiview-bundles/{id}/review", bundleId)
                        .header("Authorization", "Bearer " + reviewer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("status", "approved"))))
                .andExpect(status().isBadRequest());

        // The three child assets are represented by the bundle endpoint, not
        // repeated in the administrator's legacy single-asset review list.
        JsonNode ordinaryReviewRows = request(get("/api/creative/ai/consumer-assets/review"), reviewer.token(), null);
        assertThat(containsId(ordinaryReviewRows, frontAssetId)).isFalse();
        assertThat(containsId(ordinaryReviewRows, leftAssetId)).isFalse();
        assertThat(containsId(ordinaryReviewRows, backAssetId)).isFalse();

        // Replaying the generation callback must reuse the same bundle.
        JsonNode duplicate = request(post("/api/creative/ai/consumer-multiview-bundles"), creator.token(), completeBundle);
        assertThat(field(duplicate, "id").asLong()).isEqualTo(bundleId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM creative_multiview_bundle", Integer.class)).isEqualTo(1);

        JsonNode submitted = request(put("/api/creative/ai/consumer-multiview-bundles/{id}/submit-review", bundleId), creator.token(), Map.of(
                "purpose", "personal", "note", "整组三视图提交人工审核"));
        assertThat(field(submitted, "status").asText()).isEqualTo("review");
        assertThat(jdbc.queryForObject("SELECT status FROM creative_multiview_bundle WHERE id=?", String.class, bundleId)).isEqualTo("review");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM digital_asset WHERE id IN (?,?,?) AND status='review'", Integer.class, frontAssetId, leftAssetId, backAssetId)).isEqualTo(3);

        JsonNode adminReviewRows = request(get("/api/creative/ai/consumer-multiview-bundles/review?status=review"), reviewer.token(), null);
        assertThat(adminReviewRows.size()).isEqualTo(1);
        assertThat(field(adminReviewRows.get(0), "id").asLong()).isEqualTo(bundleId);
        assertThat(field(adminReviewRows.get(0), "images").size()).isEqualTo(3);

        mvc.perform(put("/api/creative/ai/consumer-multiview-bundles/{id}/submit-review", bundleId)
                        .header("Authorization", "Bearer " + creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("purpose", "personal"))))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/creative/ai/consumer-multiview-bundles/{id}/review", bundleId)
                        .header("Authorization", "Bearer " + reviewer.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("status", "rejected"))))
                .andExpect(status().isBadRequest());

        JsonNode rejected = request(put("/api/creative/ai/consumer-multiview-bundles/{id}/review", bundleId), reviewer.token(), Map.of(
                "status", "rejected", "comment", "请补充侧面结构并确认尺寸比例"));
        assertThat(field(rejected, "status").asText()).isEqualTo("rejected");
        assertThat(field(rejected, "reviewComment").asText()).isEqualTo("请补充侧面结构并确认尺寸比例");
        assertThat(jdbc.queryForObject("SELECT status FROM digital_asset WHERE id=?", String.class, frontAssetId)).isEqualTo("rejected");

        mvc.perform(post("/api/creative/ai/consumer-production/submit")
                        .header("Authorization", "Bearer " + creator.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("bundleId", bundleId, "requestType", "sample"))))
                .andExpect(status().isBadRequest());

        JsonNode resubmitted = request(put("/api/creative/ai/consumer-multiview-bundles/{id}/submit-review", bundleId), creator.token(), Map.of(
                "purpose", "personal", "note", "已按审核意见补充结构"));
        assertThat(field(resubmitted, "status").asText()).isEqualTo("review");
        JsonNode approved = request(put("/api/creative/ai/consumer-multiview-bundles/{id}/review", bundleId), reviewer.token(), Map.of(
                "status", "approved", "comment", "三视图结构和产品信息已确认"));
        assertThat(field(approved, "status").asText()).isEqualTo("approved");
        assertThat(jdbc.queryForObject("SELECT status FROM creative_multiview_bundle WHERE id=?", String.class, bundleId)).isEqualTo("approved");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM digital_asset WHERE id IN (?,?,?) AND status='approved'", Integer.class, frontAssetId, leftAssetId, backAssetId)).isEqualTo(3);

        JsonNode production = request(post("/api/creative/ai/consumer-production/submit"), creator.token(), Map.of(
                "bundleId", bundleId,
                "requestType", "sample",
                "quantity", 1,
                "purpose", "personal",
                "recipientName", "三视图测试用户",
                "recipientPhone", "13800138000",
                "recipientAddress", "北京市东城区三视图测试路 1 号"));
        long productionId = field(production, "id").asLong();
        String productionNo = field(production, "requestNo").asText();
        assertThat(field(production, "multiviewBundleId").asLong()).isEqualTo(bundleId);
        assertThat(jdbc.queryForObject("SELECT multiview_bundle_id FROM consumer_production_request WHERE id=?", Long.class, productionId)).isEqualTo(bundleId);
        assertThat(jdbc.queryForObject("SELECT asset_id FROM consumer_production_request WHERE id=?", Long.class, productionId)).isEqualTo(frontAssetId);
        assertThat(jdbc.queryForObject("SELECT status FROM consumer_production_request WHERE id=?", String.class, productionId)).isEqualTo("review");
        assertThat(jdbc.queryForObject("SELECT sample_fee_yuan FROM consumer_production_request WHERE id=?", BigDecimal.class, productionId)).isEqualByComparingTo("2000.00");

        JsonNode myProduction = request(get("/api/creative/ai/consumer-production/my"), creator.token(), null);
        assertThat(myProduction.size()).isEqualTo(1);
        assertThat(field(myProduction.get(0), "requestNo").asText()).isEqualTo(productionNo);
        assertThat(field(myProduction.get(0), "multiviewBundleStatus").asText()).isEqualTo("approved");
        assertThat(field(myProduction.get(0), "multiviewImages").size()).isEqualTo(3);

        JsonNode adminProduction = request(get("/api/creative/ai/consumer-production/admin/review?status=review"), reviewer.token(), null);
        assertThat(adminProduction.size()).isEqualTo(1);
        assertThat(field(adminProduction.get(0), "multiviewBundleId").asLong()).isEqualTo(bundleId);
        assertThat(field(adminProduction.get(0), "multiviewImages").size()).isEqualTo(3);

        JsonNode productionApproval = request(put("/api/creative/ai/consumer-production/admin/{id}/review", productionId), reviewer.token(), Map.of(
                "status", "approved", "comment", "已确认三视图打样申请"));
        assertThat(field(productionApproval, "status").asText()).isEqualTo("approved");
        assertThat(field(productionApproval, "samplePaymentStatus").asText()).isEqualTo("unpaid");
        assertThat(jdbc.queryForObject("SELECT status FROM consumer_production_request WHERE id=?", String.class, productionId)).isEqualTo("approved");
        assertThat(jdbc.queryForObject("SELECT sample_payment_status FROM consumer_production_request WHERE id=?", String.class, productionId)).isEqualTo("unpaid");
    }

    private void createPendingPayment(long userId, String orderNo, String productCode) {
        jdbc.update("INSERT INTO payment_order (order_no,user_id,product_code,amount_fen,credit_amount,channel,status) VALUES (?,?,?,?,?,?,?)",
                orderNo, userId, productCode, 100L, BigDecimal.ZERO, "wechat_jsapi", "pending");
    }

    private void confirmPayment(long userId, String orderNo, String productCode, String providerOrderNo) {
        ReflectionTestUtils.invokeMethod(paymentController, "creditConfirmedOrder",
                Map.of("order_no", orderNo, "user_id", userId, "product_code", productCode), providerOrderNo, "test callback");
        assertThat(jdbc.queryForObject("SELECT status FROM payment_order WHERE order_no=?", String.class, orderNo))
                .isEqualTo("paid");
    }

    private JsonNode request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                             String token, Map<String, Object> body) throws Exception {
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        String response = mvc.perform(builder.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(response);
    }

    private void reviewAsset(TestUser reviewer, long assetId, String statusValue) throws Exception {
        JsonNode response = request(put("/api/creative/ai/consumer-assets/{id}/review", assetId), reviewer.token(),
                Map.of("status", statusValue));
        assertThat(response.path("status").asText()).isEqualTo(statusValue);
    }

    private TestUser createUser(String username, String role) {
        jdbc.update("INSERT INTO user (username,password,role,status) VALUES (?,?,?,?)", username, "test-password", role, "active");
        long id = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
        User user = new User(id, username, 20, username + "@test.local", null);
        user.setRole(role);
        return new TestUser(id, jwtService.issue(user));
    }

    private void seedProductCatalog() {
        jdbc.update("INSERT INTO selection_category (category_key,name,description,review_status,enabled,sort_order) VALUES (?,?,?,?,?,?)",
                "souvenir", "文旅纪念", "测试用公开选品分类", "approved", 1, 1);
        jdbc.update("INSERT INTO selection_option (option_key,category_key,name,subtitle,description,material,process,specification,sample_lead_time,bulk_lead_time,retail_display,tags,audience_tags,occasion_tags,budget_band,review_status,enabled,sort_order) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "souvenir-alloy-magnet", "souvenir", "合金冰箱贴", "测试商品", "用于流程测试的合金冰箱贴", "锌合金", "压铸", "60mm", "7天", "15天", "待运营确认", "云纹", "游客", "伴手礼", "50-100", "approved", 1, 1);
        long optionId = jdbc.queryForObject("SELECT id FROM selection_option WHERE option_key='souvenir-alloy-magnet'", Long.class);
        jdbc.update("INSERT INTO creative_product_template (template_code,selection_option_id,product_name,published,supply_status) VALUES (?,?,?,?,?)",
                "alloy-magnet", optionId, "合金冰箱贴", 1, "confirmed");
        jdbc.update("INSERT INTO channel_directory (id,channel_code,name,province,city,district,channel_type,source_type,cooperation_status,enabled) VALUES (?,?,?,?,?,?,?,?,?,?)",
                1L, "museum-test", "测试博物馆", "北京市", "北京市", "东城区", "museum", "test", "directory_only", 1);
        jdbc.update("INSERT INTO consumer_sample_fee_catalog (product_name,fee_yuan,active) VALUES (?,?,?)", "合金冰箱贴", new BigDecimal("2000.00"), 1);
    }

    private long createAsset(TestUser owner, String assetNo, String assetType, String statusValue) {
        return createAsset(owner, assetNo, assetType, statusValue, "ai_generated");
    }

    private long createAsset(TestUser owner, String assetNo, String assetType, String statusValue, String sourceType) {
        jdbc.update("INSERT INTO digital_asset (asset_no,title,asset_type,source_type,file_url,preview_url,prompt,metadata_json,format,status,created_by,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                assetNo, "之间智造效果图", assetType, sourceType, "/generated/test.png", "/generated/test.png", "青铜云纹合金冰箱贴",
                "{\"productKey\":\"souvenir-alloy-magnet\",\"productName\":\"合金冰箱贴\"}", assetType.equals("model") ? "glb" : "png", statusValue, owner.id());
        return jdbc.queryForObject("SELECT id FROM digital_asset WHERE asset_no=?", Long.class, assetNo);
    }

    private String assetStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM digital_asset WHERE id=?", String.class, id);
    }

    private boolean containsId(JsonNode nodes, long id) {
        return assetById(nodes, id) != null;
    }

    private JsonNode assetById(JsonNode nodes, long id) {
        for (JsonNode node : nodes) if (field(node, "id").asLong() == id) return node;
        return null;
    }

    private JsonNode field(JsonNode row, String name) {
        JsonNode exact = row.get(name);
        if (exact != null) return exact;
        java.util.Iterator<Map.Entry<String, JsonNode>> fields = row.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return mapper.nullNode();
    }

    private void createWorkflowSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS selection_category (id BIGINT AUTO_INCREMENT PRIMARY KEY,category_key VARCHAR(60) NOT NULL UNIQUE,name VARCHAR(80) NOT NULL,description VARCHAR(300) NOT NULL,review_status VARCHAR(30) NOT NULL,effective_from DATE,enabled TINYINT NOT NULL DEFAULT 1,sort_order INT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS selection_option (id BIGINT AUTO_INCREMENT PRIMARY KEY,option_key VARCHAR(80) NOT NULL UNIQUE,category_key VARCHAR(60) NOT NULL,name VARCHAR(120) NOT NULL,subtitle VARCHAR(200) NOT NULL,description VARCHAR(500) NOT NULL,material VARCHAR(500) NOT NULL,process VARCHAR(1000) NOT NULL,specification VARCHAR(500) NOT NULL,sample_lead_time VARCHAR(60) NOT NULL,bulk_lead_time VARCHAR(60) NOT NULL,retail_min DECIMAL(12,2),retail_max DECIMAL(12,2),retail_display VARCHAR(80) NOT NULL,tags VARCHAR(1000) NOT NULL,audience_tags VARCHAR(500) NOT NULL,occasion_tags VARCHAR(500) NOT NULL,budget_band VARCHAR(30) NOT NULL,cover_image_url VARCHAR(500),image_source VARCHAR(500),image_rights_status VARCHAR(40),source_version VARCHAR(40),source_name VARCHAR(200),source_page INT,review_status VARCHAR(30) NOT NULL,effective_from DATE,enabled TINYINT NOT NULL DEFAULT 1,sort_order INT NOT NULL DEFAULT 0)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_selection_favorite (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,option_id BIGINT NOT NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS selection_demand_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,request_no VARCHAR(80) NOT NULL UNIQUE,user_id BIGINT NOT NULL,option_id BIGINT NOT NULL,asset_id BIGINT,theme VARCHAR(300),budget_max DECIMAL(12,2),audience VARCHAR(200),occasion VARCHAR(100),note VARCHAR(1000),status VARCHAR(30) NOT NULL DEFAULT 'new',created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_product_template (id BIGINT AUTO_INCREMENT PRIMARY KEY,template_code VARCHAR(80) NOT NULL UNIQUE,selection_option_id BIGINT,product_name VARCHAR(160) NOT NULL,published TINYINT NOT NULL DEFAULT 1,supply_status VARCHAR(30) NOT NULL DEFAULT 'pending_confirmation')");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_quote_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,request_no VARCHAR(80) NOT NULL UNIQUE,user_id BIGINT NOT NULL,asset_id BIGINT,product_template_id BIGINT NOT NULL,request_type VARCHAR(30) NOT NULL,quantity INT NOT NULL,purpose VARCHAR(30) NOT NULL,note VARCHAR(1200),copyright_basis VARCHAR(30) NOT NULL,copyright_confirmed TINYINT NOT NULL,copyright_statement_version VARCHAR(30) NOT NULL,status VARCHAR(30) NOT NULL DEFAULT 'new',quoted_unit_price DECIMAL(10,2),quoted_total_price DECIMAL(12,2),quoted_lead_time VARCHAR(120),operator_comment VARCHAR(1200),reviewed_by VARCHAR(80),reviewed_at TIMESTAMP,sample_payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required',sample_payment_order_no VARCHAR(64),sample_paid_at TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_consignment_application (id BIGINT AUTO_INCREMENT PRIMARY KEY,application_no VARCHAR(80) NOT NULL UNIQUE,user_id BIGINT NOT NULL,asset_id BIGINT NOT NULL,product_template_id BIGINT NOT NULL,channel_id BIGINT,channel_name_snapshot VARCHAR(200),sales_mode VARCHAR(30) NOT NULL,creator_share_percent DECIMAL(5,2) NOT NULL,platform_service_percent DECIMAL(5,2) NOT NULL,note VARCHAR(1200),copyright_basis VARCHAR(30) NOT NULL,copyright_confirmed TINYINT NOT NULL,copyright_statement_version VARCHAR(30) NOT NULL,authorization_note VARCHAR(1000),status VARCHAR(30) NOT NULL DEFAULT 'pending_review',operator_comment VARCHAR(1200),reviewed_by VARCHAR(80),reviewed_at TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS commercial_application_audit_log (id BIGINT AUTO_INCREMENT PRIMARY KEY,application_type VARCHAR(30) NOT NULL,application_id BIGINT NOT NULL,action VARCHAR(40) NOT NULL,operator VARCHAR(80) NOT NULL,comment VARCHAR(1200),created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS commercial_application_revision (id BIGINT AUTO_INCREMENT PRIMARY KEY,application_type VARCHAR(30) NOT NULL,application_id BIGINT NOT NULL,user_id BIGINT NOT NULL,previous_asset_id BIGINT,asset_id BIGINT NOT NULL,note VARCHAR(1200),created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS commercial_professional_guidance_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,guidance_no VARCHAR(80) NOT NULL UNIQUE,application_type VARCHAR(30) NOT NULL,application_id BIGINT NOT NULL,user_id BIGINT NOT NULL,asset_id BIGINT,product_template_id BIGINT,request_note VARCHAR(1200),status VARCHAR(30) NOT NULL DEFAULT 'requested',quoted_fee_yuan DECIMAL(12,2),quoted_lead_time VARCHAR(120),operator_comment VARCHAR(1200),guidance_result VARCHAR(3000),payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required',payment_order_no VARCHAR(64),paid_at TIMESTAMP,quoted_by VARCHAR(80),quoted_at TIMESTAMP,completed_at TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS payment_order (id BIGINT AUTO_INCREMENT PRIMARY KEY,order_no VARCHAR(64) NOT NULL UNIQUE,user_id BIGINT NOT NULL,product_code VARCHAR(100) NOT NULL,amount_fen BIGINT NOT NULL DEFAULT 0,credit_amount DECIMAL(12,2) NOT NULL DEFAULT 0,channel VARCHAR(40) NOT NULL,status VARCHAR(30) NOT NULL,provider_order_no VARCHAR(128),provider_response CLOB,paid_at TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS consumer_sample_fee_catalog (id BIGINT AUTO_INCREMENT PRIMARY KEY,product_name VARCHAR(120) NOT NULL UNIQUE,fee_yuan DECIMAL(10,2) NOT NULL,active TINYINT NOT NULL DEFAULT 1)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_multiview_bundle (id BIGINT AUTO_INCREMENT PRIMARY KEY,bundle_no VARCHAR(80) NOT NULL UNIQUE,user_id BIGINT NOT NULL,project_id BIGINT,version_id BIGINT,input_asset_id BIGINT,product_key VARCHAR(120),product_name VARCHAR(180),material VARCHAR(180),product_size VARCHAR(120),view_count INT NOT NULL DEFAULT 3,status VARCHAR(30) NOT NULL DEFAULT 'draft',purpose VARCHAR(30),museum_id VARCHAR(80),museum_name VARCHAR(200),campaign_key VARCHAR(100),note VARCHAR(1200),review_comment VARCHAR(1200),reviewed_by VARCHAR(80),reviewed_at TIMESTAMP,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("ALTER TABLE creative_multiview_bundle ADD COLUMN IF NOT EXISTS project_id BIGINT");
        jdbc.execute("ALTER TABLE creative_multiview_bundle ADD COLUMN IF NOT EXISTS version_id BIGINT");
        jdbc.execute("CREATE TABLE IF NOT EXISTS creative_multiview_bundle_item (id BIGINT AUTO_INCREMENT PRIMARY KEY,bundle_id BIGINT NOT NULL,view_key VARCHAR(20) NOT NULL,asset_id BIGINT NOT NULL,label VARCHAR(40) NOT NULL,created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS consumer_production_request (id BIGINT AUTO_INCREMENT PRIMARY KEY,request_no VARCHAR(80) NOT NULL UNIQUE,user_id BIGINT NOT NULL,asset_id BIGINT NOT NULL,project_id BIGINT,version_id BIGINT,multiview_bundle_id BIGINT,request_type VARCHAR(20) NOT NULL,title VARCHAR(200),quantity INT NOT NULL,self_ship_quantity INT NOT NULL,museum_distribution_json CLOB,recipient_name VARCHAR(80),recipient_phone VARCHAR(80),recipient_address VARCHAR(500),note VARCHAR(1000),client_request_key VARCHAR(120),status VARCHAR(30) NOT NULL DEFAULT 'review',review_comment VARCHAR(1000),reviewed_by VARCHAR(80),reviewed_at TIMESTAMP,sample_product_name VARCHAR(120),sample_fee_yuan DECIMAL(10,2),sample_payment_status VARCHAR(24) NOT NULL DEFAULT 'not_required',sample_payment_order_no VARCHAR(64),sample_paid_at TIMESTAMP,sample_workflow_status VARCHAR(32) NOT NULL DEFAULT 'not_started',sample_received_at TIMESTAMP,sample_accepted_at TIMESTAMP,sample_revision_count INT NOT NULL DEFAULT 0,bulk_unlocked_at TIMESTAMP,bulk_unlocked_by BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(user_id,client_request_key))");
    }

    private record TestUser(long id, String token) { }
}
