package com.example.shixun.controller;

import com.example.shixun.model.User;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.ProductPromptPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ArkImageQueueIntegrationTest {
    private static final ObjectMapper providerMapper = new ObjectMapper();
    private static final AtomicInteger activeProviderRequests = new AtomicInteger();
    private static final AtomicInteger maxProviderRequests = new AtomicInteger();
    private static final AtomicInteger rateLimitedResponses = new AtomicInteger();
    private static final AtomicInteger failedResponses = new AtomicInteger();
    private static final AtomicBoolean sawSeedreamModel = new AtomicBoolean();
    private static final AtomicBoolean sawReferenceImagePayload = new AtomicBoolean();
    private static final AtomicBoolean sawArkImageFields = new AtomicBoolean();
    private static final AtomicBoolean sawSiliconFlowAuthorization = new AtomicBoolean();
    private static final ExecutorService providerExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "ark-provider-test");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpServer provider = startProvider();
    private static final Path assetRoot = Path.of("target", "ark-queue-test-assets").toAbsolutePath();

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;

    @DynamicPropertySource
    static void queueProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:ark_queue_test;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        registry.add("volcengine.ark.api.key", () -> "test-ark-api-key");
        registry.add("volcengine.ark.images.base-url", () -> providerUrl("/api/v3/images/generations"));
        registry.add("volcengine.ark.queue.concurrency", () -> 1);
        registry.add("volcengine.ark.queue.retry-attempts", () -> 3);
        registry.add("volcengine.ark.queue.retry-delay-seconds", () -> 1);
        registry.add("volcengine.ark.queue.dispatch-interval-ms", () -> 50);
        registry.add("siliconflow.api.key", () -> "test-siliconflow-api-key");
        registry.add("creative.asset.private-root", assetRoot::toString);
        registry.add("tripo.poll.initial-delay-ms", () -> 600000);
    }

    @BeforeEach
    void resetQueue() throws Exception {
        waitUntil(() -> count("SELECT COUNT(*) FROM ai_generation_job WHERE status IN ('queued','running')") == 0,
                Duration.ofSeconds(8));
        jdbc.update("DELETE FROM consumer_credit_transaction");
        jdbc.update("DELETE FROM consumer_credit_account");
        jdbc.update("DELETE FROM ai_generation_job");
        jdbc.update("DELETE FROM digital_asset");
        jdbc.update("DELETE FROM user");
        if (Files.exists(assetRoot)) {
            try (var paths = Files.walk(assetRoot)) {
                paths.sorted(Comparator.reverseOrder()).filter(path -> !path.equals(assetRoot)).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                });
            }
        }
        activeProviderRequests.set(0);
        maxProviderRequests.set(0);
        rateLimitedResponses.set(1);
        failedResponses.set(0);
        sawSeedreamModel.set(false);
        sawReferenceImagePayload.set(false);
        sawArkImageFields.set(false);
        sawSiliconFlowAuthorization.set(false);
    }

    @AfterAll
    static void stopProvider() {
        provider.stop(0);
        providerExecutor.shutdownNow();
    }

    @Test
    void serializesRequestsRetriesRateLimitAndDeduplicatesClicks() throws Exception {
        TestUser first = createUser("queue-user-one");
        TestUser second = createUser("queue-user-two");
        String payload = "{\"title\":\"云纹冰箱贴\",\"prompt\":\"原创青绿色云纹冰箱贴，适合量产\"," +
                "\"productKey\":\"magnet\",\"productCategory\":\"冰箱贴\",\"productType\":\"冰箱贴\"," +
                "\"material\":\"PVC\",\"imagenImageSize\":\"1K\",\"imagenOutputFormat\":\"png\"}";

        JsonNode firstSubmission = postJob(first.token(), payload);
        JsonNode duplicateSubmission = postJob(first.token(), payload);
        JsonNode secondSubmission = postJob(second.token(), payload.replace("云纹冰箱贴", "山水冰箱贴"));

        assertThat(duplicateSubmission.path("jobId").asLong()).isEqualTo(firstSubmission.path("jobId").asLong());
        assertThat(duplicateSubmission.path("reused").asBoolean()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM ai_generation_job")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM consumer_credit_transaction WHERE biz_type='image2d' AND user_id=" + first.id())).isEqualTo(1);

        waitUntil(() -> count("SELECT COUNT(*) FROM ai_generation_job WHERE status='succeeded'") == 2,
                Duration.ofSeconds(15));

        assertThat(maxProviderRequests.get()).isEqualTo(1);
        assertThat(sawSeedreamModel).as("all image requests use Seedream 5.0").isTrue();
        assertThat(sawSiliconFlowAuthorization).as("image requests never use SiliconFlow credentials").isFalse();
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM ai_generation_job WHERE id=?", Integer.class,
                firstSubmission.path("jobId").asLong())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT format FROM digital_asset WHERE id=(SELECT output_asset_id FROM ai_generation_job WHERE id=?)",
                String.class, firstSubmission.path("jobId").asLong())).isEqualTo("jpg");
        assertThat(jdbc.queryForObject("SELECT title FROM digital_asset WHERE id=(SELECT output_asset_id FROM ai_generation_job WHERE id=?)",
                String.class, firstSubmission.path("jobId").asLong())).isEqualTo("之间智造效果图");
        assertCreditSettled(first.id());
        assertCreditSettled(second.id());

        JsonNode completed = getJob(first.token(), firstSubmission.path("jobId").asLong());
        assertThat(completed.path("status").asText()).isEqualTo("succeeded");
        assertThat(completed.path("assetId").asLong()).isPositive();
        assertThat(completed.path("previewUrl").asText()).contains("access_token=");
        assertThat(secondSubmission.path("jobId").asLong()).isNotEqualTo(firstSubmission.path("jobId").asLong());

        TestUser failedUser = createUser("queue-user-failed");
        failedResponses.set(1);
        JsonNode failedSubmission = postJob(failedUser.token(), payload.replace("云纹冰箱贴", "失败测试冰箱贴"));
        waitUntil(() -> "failed".equals(jdbc.queryForObject(
                "SELECT status FROM ai_generation_job WHERE id=?", String.class, failedSubmission.path("jobId").asLong())),
                Duration.ofSeconds(8));
        var failedAccount = jdbc.queryForMap("SELECT balance,frozen_balance frozenBalance,total_consumed totalConsumed " +
                "FROM consumer_credit_account WHERE user_id=?", failedUser.id());
        assertThat(failedAccount.get("balance").toString()).isEqualTo("100.00");
        assertThat(failedAccount.get("frozenBalance").toString()).isEqualTo("0.00");
        assertThat(failedAccount.get("totalConsumed").toString()).isEqualTo("0.00");
        assertThat(jdbc.queryForObject("SELECT status FROM consumer_credit_transaction WHERE user_id=? AND biz_type='image2d'",
                String.class, failedUser.id())).isEqualTo("refunded");
    }

    @Test
    void queuesImageEditsAndMultiViewTasksWithDurableResults() throws Exception {
        rateLimitedResponses.set(0);
        TestUser editor = createUser("queue-editor");
        TestUser multiViewUser = createUser("queue-multiview");
        long editorAsset = createReferenceAsset(editor, "editor-reference.png");
        long multiViewAsset = createReferenceAsset(multiViewUser, "multiview-reference.png");

        JsonNode edit = postQueuedImageEdit(editor.token(), editorAsset);
        JsonNode multiView = postQueuedMultiView(multiViewUser.token(), multiViewAsset);
        assertThat(edit.path("jobType").asText()).isEqualTo("image_to_image");
        assertThat(multiView.path("jobType").asText()).isEqualTo("multi_view");
        assertThat(edit.path("provider").asText()).isEqualTo("volcengine_ark");
        assertThat(multiView.path("provider").asText()).isEqualTo("volcengine_ark");
        assertThat(edit.path("assetId").asLong()).isZero();

        try {
            waitUntil(() -> count("SELECT COUNT(*) FROM ai_generation_job WHERE provider='volcengine_ark' AND status='succeeded'") == 2,
                    Duration.ofSeconds(20));
        } catch (AssertionError error) {
            throw new AssertionError("Seedream queue did not finish: " + jdbc.queryForList(
                    "SELECT id,provider,job_type,status,error_message,attempt_count FROM ai_generation_job ORDER BY id"), error);
        }

        JsonNode completedEdit = getImageJob(editor.token(), edit.path("jobId").asLong());
        JsonNode completedViews = getImageJob(multiViewUser.token(), multiView.path("jobId").asLong());
        assertThat(completedEdit.path("status").asText()).isEqualTo("succeeded");
        assertThat(completedEdit.path("assetId").asLong()).isPositive();
        assertThat(completedEdit.path("referenceAnalysis").asText()).isNotBlank();
        assertThat(completedEdit.path("compiledPrompt").asText()).isNotBlank();
        assertThat(completedEdit.path("policyVersion").asText()).isEqualTo(ProductPromptPolicy.VERSION);
        assertThat(completedEdit.path("creativeBrief").path("productKey").asText()).isEqualTo("magnet");
        assertThat(jdbc.queryForObject("SELECT title FROM digital_asset WHERE id=?", String.class,
                completedEdit.path("assetId").asLong())).isEqualTo("之间智造效果图");
        assertThat(completedViews.path("status").asText()).isEqualTo("succeeded");
        assertThat(completedViews.path("images")).hasSize(3);
        assertThat(completedViews.path("simulationAssetId").asLong()).isPositive();
        assertThat(completedViews.path("simulationImage").path("previewUrl").asText()).contains("access_token=");
        assertThat(completedViews.path("compiledPrompt").asText()).isNotBlank();
        assertThat(completedViews.path("policyVersion").asText()).isEqualTo(ProductPromptPolicy.VERSION);
        assertThat(completedViews.path("images").get(0).path("previewUrl").asText()).contains("access_token=");
        assertThat(count("SELECT COUNT(*) FROM digital_asset WHERE parent_asset_id=" + multiViewAsset +
                " AND source_type='ai_generated' AND title='之间智造效果图'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM digital_asset WHERE parent_asset_id="
                + completedViews.path("simulationAssetId").asLong()
                + " AND source_type='ai_generated' AND title='之间智造效果图'")).isEqualTo(3);
        assertThat(sawSeedreamModel).isTrue();
        assertThat(sawReferenceImagePayload).as("image-to-image and multiview send the source image to Seedream").isTrue();
        assertThat(sawArkImageFields).as("Seedream payload includes the Ark image fields").isTrue();
        assertThat(sawSiliconFlowAuthorization).isFalse();
        assertThat(maxProviderRequests.get()).isEqualTo(1);
    }

    @Test
    void presentsLegacyGeneratedImagesWithTheCurrentProductName() throws Exception {
        TestUser user = createUser("legacy-image-owner");
        jdbc.update("INSERT INTO digital_asset (asset_no,title,asset_type,source_type,file_url,preview_url,format,status,created_by,created_at,updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "AST-LEGACY-IMAGE", "AI 多视图参考 · 背面", "image", "ai_generated", "/generated/legacy.png",
                "/generated/legacy.png", "png", "draft", user.id());

        String body = mvc.perform(get("/api/creative/ai/assets")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(mapper.readTree(body).get(0).path("title").asText()).isEqualTo("之间智造效果图");
    }

    private JsonNode postJob(String token, String payload) throws Exception {
        String body = mvc.perform(post("/api/creative/ai/ark/text-to-image")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    private JsonNode getJob(String token, long jobId) throws Exception {
        String body = mvc.perform(get("/api/creative/ai/ark/image-jobs/{jobId}", jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    private JsonNode getImageJob(String token, long jobId) throws Exception {
        String body = mvc.perform(get("/api/creative/ai/image-jobs/{jobId}", jobId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    private JsonNode postQueuedImageEdit(String token, long inputAssetId) throws Exception {
        String payload = "{\"title\":\"参考图冰箱贴\",\"prompt\":\"保留参考图主体，做成冰箱贴\",\"inputAssetId\":" + inputAssetId +
                ",\"productKey\":\"magnet\",\"productCategory\":\"冰箱贴\",\"material\":\"PVC\",\"queue\":true}";
        String body = mvc.perform(post("/api/creative/ai/image-to-image")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    private JsonNode postQueuedMultiView(String token, long inputAssetId) throws Exception {
        String payload = "{\"prompt\":\"原创云纹冰箱贴\",\"inputAssetId\":" + inputAssetId +
                ",\"productKey\":\"magnet\",\"productCategory\":\"冰箱贴\",\"material\":\"PVC\",\"viewCount\":3,\"size\":\"1K\",\"queue\":true}";
        String body = mvc.perform(post("/api/creative/ai/volcengine/seedream/multiview")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return mapper.readTree(body);
    }

    private TestUser createUser(String username) {
        jdbc.update("INSERT INTO user (username,password,role,status) VALUES (?,?,?,?)", username, "test-password", "user", "active");
        Long id = jdbc.queryForObject("SELECT id FROM user WHERE username=?", Long.class, username);
        User user = new User(id, username, 20, username + "@test.local", null);
        user.setRole("user");
        return new TestUser(id, jwtService.issue(user));
    }

    private long createReferenceAsset(TestUser user, String filename) throws IOException {
        Path generated = assetRoot.resolve("generated");
        Files.createDirectories(generated);
        Files.writeString(generated.resolve(filename), "reference-image", StandardCharsets.UTF_8);
        jdbc.update("INSERT INTO digital_asset (asset_no,title,asset_type,source_type,file_url,preview_url,format,status,created_by,created_at,updated_at) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                "AST-" + filename, "参考图", "image", "uploaded", "/generated/" + filename,
                "/generated/" + filename, "png", "draft", user.id());
        Long assetId = jdbc.queryForObject("SELECT id FROM digital_asset WHERE asset_no=?", Long.class, "AST-" + filename);
        return assetId == null ? 0 : assetId;
    }

    private void assertCreditSettled(long userId) {
        var account = jdbc.queryForMap("SELECT balance,frozen_balance frozenBalance,total_consumed totalConsumed " +
                "FROM consumer_credit_account WHERE user_id=?", userId);
        assertThat(account.get("balance").toString()).isEqualTo("84.00");
        assertThat(account.get("frozenBalance").toString()).isEqualTo("0.00");
        assertThat(account.get("totalConsumed").toString()).isEqualTo("16.00");
        assertThat(jdbc.queryForObject("SELECT status FROM consumer_credit_transaction WHERE user_id=? AND biz_type='image2d'", String.class, userId))
                .isEqualTo("completed");
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private static void waitUntil(CheckedCondition condition, Duration timeout) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.test()) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for Ark queue state");
    }

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/v3/images/generations", ArkImageQueueIntegrationTest::handleGeneration);
            byte[] generatedJpeg = createTestJpeg();
            server.createContext("/generated.jpg", exchange -> respond(exchange, 200, "image/jpeg", generatedJpeg));
            server.setExecutor(providerExecutor);
            server.start();
            return server;
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static void handleGeneration(HttpExchange exchange) throws IOException {
        int active = activeProviderRequests.incrementAndGet();
        maxProviderRequests.accumulateAndGet(active, Math::max);
        try {
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            JsonNode request = providerMapper.readTree(requestBytes);
            if ("doubao-seedream-5-0-pro-260628".equals(request.path("model").asText())) {
                sawSeedreamModel.set(true);
            }
            if (request.hasNonNull("image")) sawReferenceImagePayload.set(true);
            if (request.has("response_format") && request.has("size") && request.has("stream") && request.has("watermark")) {
                sawArkImageFields.set(true);
            }
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (authorization != null && authorization.contains("test-siliconflow-api-key")) {
                sawSiliconFlowAuthorization.set(true);
            }
            if (rateLimitedResponses.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                respond(exchange, 429, "application/json",
                        "{\"error\":{\"code\":\"RateLimitExceeded\",\"message\":\"concurrency limit\"}}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (failedResponses.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                respond(exchange, 500, "application/json",
                        "{\"error\":{\"code\":\"InternalError\",\"message\":\"provider unavailable\"}}".getBytes(StandardCharsets.UTF_8));
                return;
            }
            try { Thread.sleep(350); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            String body = "{\"data\":[{\"url\":\"" + providerUrl("/generated.jpg") + "\",\"output_format\":\"jpeg\"}]}";
            respond(exchange, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
        } finally {
            activeProviderRequests.decrementAndGet();
        }
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static byte[] createTestJpeg() throws IOException {
        BufferedImage image = new BufferedImage(12, 6, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            Color color = x < 4 ? Color.RED : x < 8 ? Color.GREEN : Color.BLUE;
            for (int y = 0; y < image.getHeight(); y++) image.setRGB(x, y, color.getRGB());
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", output)) throw new IOException("无法生成测试 JPEG");
        return output.toByteArray();
    }

    private static String providerUrl(String path) {
        return "http://127.0.0.1:" + provider.getAddress().getPort() + path;
    }

    private record TestUser(long id, String token) {}
    private interface CheckedCondition { boolean test() throws Exception; }
}
