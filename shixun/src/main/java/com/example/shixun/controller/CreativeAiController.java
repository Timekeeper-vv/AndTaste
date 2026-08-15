package com.example.shixun.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.ProductPromptPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/creative/ai")
public class CreativeAiController {
    /**
     * Curated priority calls shown before login. They are platform briefs for
     * target channels, not assertions that a named institution has purchased,
     * authorized, or commissioned the work.
     */
    private static final List<CampaignDefinition> CREATOR_CAMPAIGNS = List.of(
            new CampaignDefinition(
                    "museum_summer_gift_2026", "器物新生 · 城市礼赠", "museum-national", "中国国家博物馆",
                    "现代东方器物风", List.of("冰箱贴", "金属书签", "礼盒"), "magnet",
                    "围绕器物轮廓、传统纹样与城市记忆，创作轻量、易携带的当代礼赠文创。",
                    "以原创器物纹样和城市文化记忆为灵感，采用现代东方的简洁构图，设计一款适合游客带走的轻量礼赠文创；突出清晰轮廓、真实材质、易携带结构和商品陈列感。",
                    BigDecimal.valueOf(80), "2026-09-30", true),
            new CampaignDefinition(
                    "suzhou_garden_stationery_2026", "园林雅物 · 江南文具", "museum-suzhou", "苏州博物馆",
                    "江南留白与园林几何", List.of("书签", "明信片", "帆布袋"), "stationery",
                    "用园林窗格、水色、瓦当和留白节奏，做轻盈克制的日常文具与随身文创。",
                    "以原创江南园林窗格、屋檐线条、水色和留白节奏为灵感，形成克制、清爽、可量产的现代文创视觉；避免直接复制任何馆藏图像、标识或受保护 IP。",
                    BigDecimal.valueOf(70), "2026-10-15", false),
            new CampaignDefinition(
                    "hunan_lacquer_gift_2026", "汉风漆彩 · 旅行伴手礼", "museum-hunan", "湖南博物院",
                    "朱砂漆彩与云纹新表达", List.of("冰箱贴", "钥匙扣", "礼盒"), "magnet",
                    "从汉代漆器的色彩、云纹和器形节奏获得启发，做有辨识度的旅行伴手礼。",
                    "以原创朱砂漆彩、流动云纹和简化器形比例为灵感，设计一款色彩有记忆点、轮廓清晰、适合文旅零售陈列的现代伴手礼；不得复刻具体文物纹样或馆方标识。",
                    BigDecimal.valueOf(70), "2026-10-31", false),
            new CampaignDefinition(
                    "sanxingdui_bronze_collectible_2026", "青铜想象 · 年轻潮玩", "museum-sanxingdui", "三星堆博物馆",
                    "青铜绿金与几何潮玩", List.of("PVC / 搪胶公仔", "硬塑摆件", "徽章"), "pvc_figure",
                    "以原创青铜色、几何轮廓和抽象面具感，做适合年轻客群的收藏型文创。",
                    "以原创青铜绿、金色氧化质感、抽象几何轮廓和未来感陈列结构为灵感，设计一件可量产的年轻潮玩文创；避免复刻具体文物造型、面具图像或馆方标识。",
                    BigDecimal.valueOf(90), "2026-11-15", false)
    );

    private final JdbcTemplate jdbc;
    private final TransactionTemplate creditTransactions;
    private final ObjectMapper mapper;
    private final JwtService jwtService;
    private final ThreadPoolTaskExecutor arkImageGenerationExecutor;
    private final ThreadPoolTaskExecutor siliconflowImageGenerationExecutor;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Object arkQueueSubmissionLock = new Object();
    private final Object siliconflowQueueSubmissionLock = new Object();
    private final Set<Long> activeArkImageJobs = ConcurrentHashMap.newKeySet();
    private final Set<Long> activeSiliconflowImageJobs = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean arkQueueRecovered = new AtomicBoolean(false);
    private final AtomicBoolean siliconflowQueueRecovered = new AtomicBoolean(false);
    private final AtomicBoolean arkQueueTableVerified = new AtomicBoolean(false);

    @Value("${siliconflow.api.key:}")
    private String siliconflowApiKey;

    @Value("${siliconflow.image.model:Kwai-Kolors/Kolors}")
    private String imageModel;

    @Value("${siliconflow.image.edit.model:Qwen/Qwen-Image-Edit-2509}")
    private String imageEditModel;

    @Value("${siliconflow.images.base-url:https://api.siliconflow.cn/v1/images/generations}")
    private String siliconflowImagesUrl;

    @Value("${siliconflow.vision.enabled:true}")
    private boolean siliconflowVisionEnabled;

    @Value("${siliconflow.image.queue.concurrency:2}")
    private int siliconflowImageQueueConcurrency;

    @Value("${siliconflow.image.queue.retry-attempts:2}")
    private int siliconflowImageQueueRetryAttempts;

    @Value("${siliconflow.image.queue.retry-delay-seconds:3}")
    private long siliconflowImageQueueRetryDelaySeconds;

    @Value("${siliconflow.chat.model:Qwen/Qwen3-32B}")
    private String chatModel;

    @Value("${siliconflow.vision.model:Qwen/Qwen2.5-VL-72B-Instruct}")
    private String visionModel;

    @Value("${tripo.api.key:}")
    private String tripoApiKey;

    @Value("${tripo.api.base-url:https://openapi.tripo3d.com/v3}")
    private String tripoBaseUrl;

    @Value("${tripo.convert.base-url:https://api.tripo3d.ai/v2/openapi}")
    private String tripoConvertBaseUrl;

    @Value("${model.convert.prefer-local:true}")
    private boolean modelConvertPreferLocal;

    @Value("${model.convert.fallback-tripo:false}")
    private boolean modelConvertFallbackTripo;

    @Value("${model.convert.blender-command:blender}")
    private String modelConvertBlenderCommand;

    @Value("${model.convert.assimp-command:assimp}")
    private String modelConvertAssimpCommand;

    @Value("${model.convert.node-command:node}")
    private String modelConvertNodeCommand;

    @Value("${model.convert.timeout-seconds:300}")
    private long modelConvertTimeoutSeconds;

    @Value("${tripo.model.version:v3.1-20260211}")
    private String tripoModelVersion;

    @Value("${consumer.credit.initial-balance:100}")
    private BigDecimal consumerCreditInitialBalance;

    @Value("${replicate.api.key:}")
    private String replicateApiKey;

    @Value("${replicate.api.base-url:https://api.replicate.com/v1}")
    private String replicateBaseUrl;

    @Value("${replicate.imagen.model:google/imagen-4}")
    private String replicateImagenModel;

    @Value("${jimeng.api.key:}")
    private String jimengApiKey;

    @Value("${jimeng.access-key-id:}")
    private String jimengAccessKeyId;

    @Value("${jimeng.secret-access-key:}")
    private String jimengSecretAccessKey;

    @Value("${jimeng.region:cn-north-1}")
    private String jimengRegion;

    @Value("${jimeng.service:cv}")
    private String jimengService;

    @Value("${jimeng.api.base-url:https://visual.volcengineapi.com}")
    private String jimengBaseUrl;

    @Value("${jimeng.req-key:jimeng_seedream46_cvtob}")
    private String jimengReqKey;

    // 火山引擎 Ark 密钥仅从本地密钥库 / 环境变量读取，绝不写入代码仓库。
    @Value("${volcengine.ark.api.key:${VOLCENGINE_ARK_API_KEY:}}")
    private String volcengineArkApiKey;

    @Value("${volcengine.ark.images.base-url:https://ark.cn-beijing.volces.com/api/v3/images/generations}")
    private String volcengineArkImagesUrl;

    @Value("${volcengine.ark.seedream.image.model:${VOLCENGINE_ARK_SEEDREAM_IMAGE_MODEL:doubao-seedream-5-0-pro-260628}}")
    private String volcengineArkSeedreamImageModel;

    @Value("${volcengine.ark.queue.concurrency:1}")
    private int arkQueueConcurrency;

    @Value("${volcengine.ark.queue.retry-attempts:3}")
    private int arkQueueRetryAttempts;

    @Value("${volcengine.ark.queue.retry-delay-seconds:4}")
    private long arkQueueRetryDelaySeconds;

    // 使用已在 Ark 控制台开通的 Seedream 接入点名称；可通过环境变量覆盖。
    @Value("${volcengine.ark.seedream.multiview.model:${VOLCENGINE_ARK_SEEDREAM_MULTIVIEW_MODEL:doubao-seedream-5-0-260128}}")
    private String volcengineArkSeedreamMultiviewModel;

    @Value("${jimeng.poll.max-seconds:180}")
    private long jimengPollMaxSeconds;

    @Value("${modao.api.key:}")
    private String modaoApiKey;

    @Value("${modao.design.url:https://modao.cc/ai/design/spmrsxjgcyi6g0h1/6a5dd48151e5a21110c1697a}")
    private String modaoDesignUrl;

    @Value("${modao.mcp.url:https://modao.cc/agent-py/ai/mcp}")
    private String modaoMcpUrl;

    @Value("${modao.chrome.path:/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}")
    private String modaoChromePath;

    /**
     * Deliberately outside shixun-vue/public.  Creative files are user-owned
     * and must only be read through the authenticated asset endpoints.
     */
    @Value("${creative.asset.private-root:${CREATIVE_ASSET_PRIVATE_ROOT:}}")
    private String creativePrivateAssetRoot;

    public CreativeAiController(JdbcTemplate jdbc, ObjectMapper mapper, JwtService jwtService,
                                PlatformTransactionManager transactionManager,
                                @Qualifier("arkImageGenerationExecutor") ThreadPoolTaskExecutor arkImageGenerationExecutor,
                                @Qualifier("siliconflowImageGenerationExecutor") ThreadPoolTaskExecutor siliconflowImageGenerationExecutor) {
        this.jdbc = jdbc;
        this.creditTransactions = new TransactionTemplate(transactionManager);
        this.mapper = mapper;
        this.jwtService = jwtService;
        this.arkImageGenerationExecutor = arkImageGenerationExecutor;
        this.siliconflowImageGenerationExecutor = siliconflowImageGenerationExecutor;
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> businessError(RuntimeException e) {
        return Map.of("success", false, "message", e.getMessage() == null ? "请求处理失败" : e.getMessage());
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> ioBusinessError(IOException e) {
        return Map.of("success", false, "message", e.getMessage() == null ? "文件读取失败" : e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Map<String,Object> uploadTooLarge(MaxUploadSizeExceededException e) {
        return Map.of("success", false, "message", "材质版模型及贴图超过 100MB，请降低贴图分辨率后再保存");
    }

    /**
     * The JWT filter installs this principal after verifying the bearer token.
     * Never derive an identity from query parameters, a JSON body, or a caller
     * supplied X-Current-* header: those values are all forgeable before the
     * filter has run.
     */
    private JwtService.Claims authenticatedPrincipal() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null : attributes.getAttribute(
                JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof JwtService.Claims)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return (JwtService.Claims) value;
    }

    private Long authenticatedUserId() {
        Long userId = authenticatedPrincipal().userId();
        if (userId == null || userId <= 0) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录身份无效");
        return userId;
    }

    private Long requirePersistedAuthenticatedUser() {
        JwtService.Claims principal = authenticatedPrincipal();
        if (!hasPersistedRole(principal.userId(), principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        }
        return principal.userId();
    }

    private boolean hasPersistedRole(Long userId, String expectedRole) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT role FROM user WHERE id=? LIMIT 1", userId);
        return !rows.isEmpty() && expectedRole.equals(String.valueOf(rows.get(0).get("role")));
    }

    private boolean isCreativeAdmin(JwtService.Claims principal) {
        return principal != null && "admin".equals(principal.role()) && hasPersistedRole(principal.userId(), "admin");
    }

    private void requireCreativeAdmin() {
        if (!isCreativeAdmin(authenticatedPrincipal())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可访问该资源");
        }
    }

    private Long requireCurrentConsumerUser() {
        JwtService.Claims principal = authenticatedPrincipal();
        Long userId = principal.userId();
        if (!"user".equals(principal.role()) || !hasPersistedRole(userId, "user")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅C端用户可使用该资源");
        }
        return userId;
    }

    private Long currentConsumerUserIdOrNull() {
        JwtService.Claims principal = authenticatedPrincipal();
        return "user".equals(principal.role()) && hasPersistedRole(principal.userId(), "user")
                ? principal.userId() : null;
    }

    private void requireAssetAccess(Long assetId) {
        if (assetId == null) throw new IllegalArgumentException("缺少作品ID");
        JwtService.Claims principal = authenticatedPrincipal();
        if (isCreativeAdmin(principal)) return;
        Long userId = requirePersistedAuthenticatedUser();
        Long ownerId = assetOwnerId(assetId);
        if (ownerId == null || !ownerId.equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问其他用户的作品");
        }
    }

    /**
     * Converted/material-variant assets may inherit the owner through their
     * parent.  Following the parent chain keeps old rows without a copied
     * created_by value private as well.
     */
    private Long assetOwnerId(Long assetId) {
        Long cursor = assetId;
        Set<Long> visited = new HashSet<>();
        while (cursor != null && visited.add(cursor)) {
            List<Map<String,Object>> rows = jdbc.queryForList(
                    "SELECT created_by createdBy,parent_asset_id parentAssetId FROM digital_asset WHERE id=? LIMIT 1", cursor);
            if (rows.isEmpty()) throw new IllegalArgumentException("作品不存在");
            Map<String,Object> row = rows.get(0);
            if (row.get("createdBy") instanceof Number) return ((Number) row.get("createdBy")).longValue();
            cursor = row.get("parentAssetId") instanceof Number ? ((Number) row.get("parentAssetId")).longValue() : null;
        }
        return null;
    }

    private void requireJobAccess(Long jobId) {
        if (jobId == null) throw new IllegalArgumentException("缺少任务ID");
        JwtService.Claims principal = authenticatedPrincipal();
        if (isCreativeAdmin(principal)) return;
        Long userId = requirePersistedAuthenticatedUser();
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT created_by createdBy FROM ai_generation_job WHERE id=? LIMIT 1", jobId);
        if (rows.isEmpty()) throw new IllegalArgumentException("生成任务不存在");
        Object owner = rows.get(0).get("createdBy");
        if (!(owner instanceof Number) || ((Number) owner).longValue() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问其他用户的生成任务");
        }
    }

    private Long jobOwnerId(Long jobId) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT created_by createdBy FROM ai_generation_job WHERE id=? LIMIT 1", jobId);
        if (rows.isEmpty() || !(rows.get(0).get("createdBy") instanceof Number)) return null;
        return ((Number) rows.get(0).get("createdBy")).longValue();
    }

    private Map<String,Object> withAssetOwner(Map<String,Object> metadata, Long ownerUserId) {
        Map<String,Object> result = new LinkedHashMap<>();
        if (metadata != null) result.putAll(metadata);
        if (ownerUserId != null) {
            result.put("createdByUserId", ownerUserId);
        }
        return result;
    }

    private String signedMediaUrl(Long assetId, String endpoint, JwtService.Claims principal) {
        String token = jwtService.issueMediaAccessToken(principal.userId(), principal.username(), principal.role(), assetId);
        return "/api/creative/ai/assets/" + assetId + "/" + endpoint + "?access_token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /**
     * Put short-lived, same-origin media URLs in a freshly-created asset
     * response.  The database deliberately keeps its private /generated or
     * /uploads path, but clients must never be asked to render that path
     * directly (it is intentionally not a public static resource anymore).
     */
    private void addSignedAssetFields(Map<String, Object> result, Long assetId, String assetType) {
        if (result == null || assetId == null || assetId <= 0 || blank(assetType)) return;
        // Scheduled Tripo polling also reuses the completion helpers outside a
        // servlet request.  There is no caller to receive a URL in that case;
        // simply leave the fields absent and let the next authenticated poll
        // obtain fresh access URLs.
        JwtService.Claims principal = currentPrincipalOrNull();
        if (principal == null) return;
        String preview = signedMediaUrl(assetId, "preview-content", principal);
        String content = signedMediaUrl(assetId,
                "model".equals(assetType) ? "model-content" : "content", principal);
        result.put("previewUrl", preview);
        result.put("fileUrl", content);
        if ("model".equals(assetType)) result.put("modelUrl", content);
        if ("image".equals(assetType)) result.put("imageUrl", content);
    }

    private JwtService.Claims currentPrincipalOrNull() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null : attributes.getAttribute(
                JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return value instanceof JwtService.Claims ? (JwtService.Claims) value : null;
    }

    /** Replace legacy public paths in list responses with short-lived URLs. */
    private void addSignedAssetUrls(List<Map<String,Object>> rows) {
        JwtService.Claims principal = authenticatedPrincipal();
        for (Map<String,Object> row : rows) {
            // Asset-list rows use `id` for the digital asset itself, while
            // production-request rows contain both a request `id` and the
            // referenced digital asset `assetId`.  Prefer the explicit asset
            // id whenever it is present; signing the request id produces a
            // valid-looking URL for the wrong record and leaves the client
            // unable to load its image/model.
            Object idValue = row.get("assetId") instanceof Number ? row.get("assetId") : row.get("id");
            if (!(idValue instanceof Number)) continue;
            Long assetId = ((Number) idValue).longValue();
            boolean model = "model".equals(String.valueOf(row.get("assetType")));
            String preview = signedMediaUrl(assetId, "preview-content", principal);
            String content = signedMediaUrl(assetId, model ? "model-content" : "content", principal);
            row.put("signedPreviewUrl", preview);
            row.put("signedFileUrl", content);
            row.put("previewUrl", preview);
            row.put("fileUrl", content);
        }
    }


    @GetMapping("/styles")
    public List<Map<String, Object>> styles() {
        return jdbc.queryForList("SELECT id, name, description, base_prompt basePrompt, negative_prompt negativePrompt, palette, cultural_guardrails culturalGuardrails FROM brand_style_profile WHERE enabled=1 ORDER BY id");
    }

    @GetMapping("/consumer-credits/rules")
    public Map<String,Object> consumerCreditRules() {
        return Map.of(
                "image2d", consumerCreditCost("image2d"),
                "imageTo3d", consumerCreditCost("image_to_3d"),
                "textTo3d", consumerCreditCost("text_to_3d"),
                "modelConvert", consumerCreditCost("model_convert"),
                "unit", "点"
        );
    }

    @GetMapping("/consumer-credits/account")
    public Map<String,Object> consumerCreditAccount() {
        Long userId = requireCurrentConsumerUser();
        ensureConsumerCreditAccount(userId);
        return creditAccountMap(userId);
    }

    @GetMapping("/consumer-rewards/overview")
    public Map<String,Object> consumerRewardOverview() {
        Long userId = requireCurrentConsumerUser();
        ensureConsumerCreditAccount(userId);
        List<Map<String,Object>> missions = new ArrayList<>();
        for (String missionKey : rewardMissionKeys()) missions.add(rewardMissionOverview(userId, missionKey));
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("missions", missions);
        out.put("campaign", campaignOverview(userId, defaultCampaignKey()));
        out.put("campaigns", CREATOR_CAMPAIGNS.stream().map(campaign -> campaignOverview(userId, campaign.key())).toList());
        out.put("creditAccount", creditAccountMap(userId));
        return out;
    }

    /**
     * Login is intentionally allowed to read only the public campaign briefs.
     * User participation status and point balances remain behind authentication.
     */
    @GetMapping("/consumer-rewards/campaigns/public")
    public List<Map<String,Object>> publicCreatorCampaigns() {
        return CREATOR_CAMPAIGNS.stream().map(this::publicCampaignMap).toList();
    }

    @GetMapping("/consumer-rewards/history")
    public Map<String,Object> consumerRewardHistory() {
        Long userId = requireCurrentConsumerUser();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("missions", jdbc.queryForList("SELECT mission_key missionKey,asset_id assetId,status,claimed_at claimedAt FROM consumer_reward_mission_claim WHERE user_id=? ORDER BY id DESC", userId));
        out.put("campaigns", jdbc.queryForList("SELECT c.participation_no participationNo,c.campaign_key campaignKey,c.asset_id assetId,a.title assetTitle,c.status,c.reward_amount rewardAmount,c.reviewed_at reviewedAt,c.created_at createdAt FROM consumer_campaign_reward c LEFT JOIN digital_asset a ON a.id=c.asset_id WHERE c.user_id=? ORDER BY c.id DESC", userId));
        out.put("transactions", jdbc.queryForList("SELECT transaction_no transactionNo,asset_id assetId,amount,status,remark,created_at createdAt FROM consumer_credit_transaction WHERE user_id=? AND biz_type='reward' ORDER BY id DESC LIMIT 100", userId));
        return out;
    }

    @PostMapping("/consumer-rewards/missions/{missionKey}/claim")
    public Map<String,Object> claimConsumerRewardMission(@PathVariable String missionKey) {
        Long userId = requireCurrentConsumerUser();
        if (!rewardMissionKeys().contains(missionKey)) throw new IllegalArgumentException("不支持的创作任务");
        creditTransactions.execute(status -> {
            Map<String,Object> mission = rewardMissionOverview(userId, missionKey);
            if (!"claimable".equals(String.valueOf(mission.get("status")))) {
                throw new IllegalStateException("claimed".equals(String.valueOf(mission.get("status"))) ? "该任务积分已领取" : "请先完成对应创作任务");
            }
            Long assetId = mission.get("assetId") instanceof Number ? ((Number) mission.get("assetId")).longValue() : null;
            int inserted = jdbc.update("INSERT INTO consumer_reward_mission_claim (claim_no,user_id,mission_key,asset_id,status) VALUES (?,?,?,?, 'claimed') ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)", no("RMC"), userId, missionKey, assetId);
            if (inserted != 1) throw new IllegalStateException("该任务积分已领取");
            Long txId = grantRewardCreditInTransaction(userId, assetId, rewardMissionAmount(missionKey), "完成创作任务：" + rewardMissionTitle(missionKey));
            jdbc.update("UPDATE consumer_reward_mission_claim SET credit_transaction_id=? WHERE user_id=? AND mission_key=?", txId, userId, missionKey);
            return null;
        });
        Map<String,Object> out = new LinkedHashMap<>(rewardMissionOverview(userId, missionKey));
        out.put("creditAccount", creditAccountMap(userId));
        out.put("message", "任务积分已到账");
        return out;
    }

    @PostMapping("/consumer-rewards/campaigns/{campaignKey}/participations")
    public Map<String,Object> joinConsumerCampaign(@PathVariable String campaignKey, @RequestBody Map<String,Object> body) {
        Long userId = requireCurrentConsumerUser();
        CampaignDefinition campaign = campaignDefinition(campaignKey);
        Long assetId = body != null && body.get("assetId") instanceof Number ? ((Number) body.get("assetId")).longValue() : null;
        if (assetId == null) throw new IllegalArgumentException("请选择要投稿的作品");
        requireAssetAccess(assetId);
        List<Map<String,Object>> assets = jdbc.queryForList("SELECT id,status,asset_type assetType,created_by createdBy,tags FROM digital_asset WHERE id=? AND created_by=? AND asset_type IN ('image','model') LIMIT 1", assetId, userId);
        if (assets.isEmpty()) throw new IllegalArgumentException("仅可投稿本人生成的图片或3D作品");
        if (!"review".equals(String.valueOf(assets.get(0).get("status")))) throw new IllegalStateException("请先把作品提交审核，再参加本期活动");
        if (!campaign.legacyManualParticipation() && !String.valueOf(assets.get(0).get("tags")).contains(";激励任务=" + campaign.key())) {
            throw new IllegalStateException("请从登录页选择该优先征集任务后，再创作并提交作品审核");
        }
        createCampaignParticipation(userId, campaign, assetId);
        Map<String,Object> out = campaignOverview(userId, campaignKey);
        out.put("message", "活动投稿已进入审核，通过后系统自动发放积分");
        return out;
    }

    @GetMapping("/consumer-rewards/admin/campaigns")
    public List<Map<String,Object>> adminCampaignRewards(@RequestParam(required=false) String status,
                                                         @RequestParam(required=false,defaultValue="200") int size) {
        requireCreativeAdmin();
        StringBuilder sql = new StringBuilder("SELECT c.id,c.participation_no participationNo,c.campaign_key campaignKey,c.asset_id assetId,a.title assetTitle,a.asset_type assetType,a.status assetStatus,u.username,c.status,c.reward_amount rewardAmount,c.reviewed_by reviewedBy,c.reviewed_at reviewedAt,c.created_at createdAt FROM consumer_campaign_reward c JOIN user u ON u.id=c.user_id JOIN digital_asset a ON a.id=c.asset_id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!blank(status)) { sql.append(" AND c.status=?"); args.add(status.trim()); }
        sql.append(" ORDER BY c.id DESC LIMIT ?"); args.add(Math.max(1, Math.min(size, 500)));
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @GetMapping("/consumer-credits/admin/accounts")
    public List<Map<String,Object>> consumerCreditAccounts(@RequestParam(required=false) String search,
                                                           @RequestParam(required=false,defaultValue="200") int size) {
        requireCreativeAdmin();
        StringBuilder sql=new StringBuilder("SELECT u.id userId,u.username,u.phone,u.email,COALESCE(a.balance,0) balance,COALESCE(a.frozen_balance,0) frozenBalance,COALESCE(a.total_recharged,0) totalRecharged,COALESCE(a.total_consumed,0) totalConsumed,a.updated_at updatedAt FROM user u LEFT JOIN consumer_credit_account a ON a.user_id=u.id WHERE u.role='user'");
        List<Object> args=new ArrayList<>();
        if(!blank(search)){sql.append(" AND (u.username LIKE ? OR u.phone LIKE ? OR CAST(u.id AS CHAR) LIKE ?)");String kw="%"+search.trim()+"%";args.add(kw);args.add(kw);args.add(kw);}
        sql.append(" ORDER BY u.id DESC LIMIT ?");args.add(Math.max(1,Math.min(size,1000)));
        List<Map<String,Object>> rows=jdbc.queryForList(sql.toString(),args.toArray());
        rows.forEach(r -> ensureConsumerCreditAccount(((Number)r.get("userId")).longValue()));
        return jdbc.queryForList(sql.toString(),args.toArray());
    }

    @GetMapping("/consumer-credits/admin/transactions")
    public List<Map<String,Object>> consumerCreditTransactions(@RequestParam(required=false) Long userId,
                                                               @RequestParam(required=false) String status,
                                                               @RequestParam(required=false,defaultValue="300") int size) {
        requireCreativeAdmin();
        StringBuilder sql=new StringBuilder("SELECT t.id,t.transaction_no transactionNo,t.user_id userId,u.username,t.asset_id assetId,t.job_id jobId,t.biz_type bizType,t.amount,t.direction,t.status,t.balance_before balanceBefore,t.balance_after balanceAfter,t.remark,t.operator,t.created_at createdAt,t.updated_at updatedAt FROM consumer_credit_transaction t LEFT JOIN user u ON u.id=t.user_id WHERE 1=1");
        List<Object> args=new ArrayList<>();
        if(userId!=null){sql.append(" AND t.user_id=?");args.add(userId);}
        if(!blank(status)){sql.append(" AND t.status=?");args.add(status.trim());}
        sql.append(" ORDER BY t.id DESC LIMIT ?");args.add(Math.max(1,Math.min(size,1000)));
        return jdbc.queryForList(sql.toString(),args.toArray());
    }

    @PostMapping("/consumer-credits/admin/recharge")
    public Map<String,Object> rechargeConsumerCredit(@RequestBody Map<String,String> body) {
        requireCreativeAdmin();
        Long userId=body==null||blank(body.get("userId"))?null:Long.parseLong(body.get("userId").trim());
        if(userId==null) throw new IllegalArgumentException("请选择C端用户");
        if(body==null||blank(body.get("amount"))) throw new IllegalArgumentException("请填写充值额度");
        BigDecimal amount=new BigDecimal(body.get("amount").trim());
        if(amount.compareTo(BigDecimal.ZERO)<=0) throw new IllegalArgumentException("充值额度必须大于0");
        String remark=body==null?"":nullToEmpty(body.get("remark"));
        Long txId=rechargeCredit(userId,amount,authenticatedPrincipal().username(),remark);
        Map<String,Object> out=new LinkedHashMap<>(creditAccountMap(userId));
        out.put("transactionId",txId);
        out.put("message","充值成功");
        return out;
    }

    @PostMapping("/consumer-credits/admin/set-balance")
    public Map<String,Object> setConsumerCreditBalance(@RequestBody Map<String,String> body) {
        requireCreativeAdmin();
        Long userId=body==null||blank(body.get("userId"))?null:Long.parseLong(body.get("userId").trim());
        if(userId==null) throw new IllegalArgumentException("请选择C端用户");
        if(body==null||blank(body.get("balance"))) throw new IllegalArgumentException("请填写目标余额");
        BigDecimal balance=new BigDecimal(body.get("balance").trim());
        if(balance.compareTo(BigDecimal.ZERO)<0) throw new IllegalArgumentException("目标余额不能小于0");
        String remark=body==null?"":nullToEmpty(body.get("remark"));
        Long txId=setCreditBalance(userId,balance,authenticatedPrincipal().username(),remark);
        Map<String,Object> out=new LinkedHashMap<>(creditAccountMap(userId));
        out.put("transactionId",txId);
        out.put("message","额度设置成功");
        return out;
    }

    @PostMapping("/prompt/compose")
    public Map<String, Object> composePrompt(@RequestBody GenerateImageRequest req) {
        Map<String, Object> style = style(req.styleId);
        String finalPrompt = buildPrompt(req.prompt, style, req.scene, req.productType);
        String negative = mergeNegative(req.negativePrompt, (String) style.get("negativePrompt"));
        return Map.of("prompt", finalPrompt, "negativePrompt", negative, "styleName", style.get("name"), "guardrails", style.get("culturalGuardrails") == null ? "" : style.get("culturalGuardrails"));
    }



    @PostMapping("/prompt/ai")
    public Map<String, Object> aiProductPrompt(@RequestBody GenerateImageRequest req) throws Exception {
        Map<String, Object> style = style(req.styleId);
        String system = "You are a cultural creative product image prompt expert. Convert the user's requirements into a high-quality ENGLISH prompt for AI image generation. The prompt must be clear, executable, commercial, photorealistic or premium product-visual oriented. Output Chinese section markers only if required by the parser, but the positive prompt content itself must be English.";
        String user = "请根据以下信息生成一段用于AI生成文创产品原型图的中文提示词，并补充一段反向提示词。\n" +
                "作品/产品名：" + nullToEmpty(req.title) + "\n" +
                "产品类型：" + nullToEmpty(req.productType) + "\n" +
                "使用场景：" + nullToEmpty(req.scene) + "\n" +
                "用户想法：" + nullToEmpty(req.prompt) + "\n" +
                "品牌风格：" + style.get("name") + "；基础风格：" + style.get("basePrompt") + "\n" +
                "文化/版权要求：" + style.get("culturalGuardrails") + "\n\n" +
                "输出格式必须如下：\n" +
                "【正向提示词】\n" +
                "一段完整提示词，包含：产品主体、材质工艺、图案元素、构图、光线、背景、商业产品渲染、可打样细节。\n" +
                "【反向提示词】\n" +
                "一段反向提示词，包含：避免低清晰、变形、文字错误、廉价感、版权风险、杂乱背景等。";
        String content = callChat(system, user);
        String positive = content;
        String negative = mergeNegative(req.negativePrompt, (String) style.get("negativePrompt"));
        String posMark = "【正向提示词】";
        String negMark = "【反向提示词】";
        int pos = content.indexOf(posMark);
        int neg = content.indexOf(negMark);
        if (pos >= 0 && neg > pos) {
            positive = content.substring(pos + posMark.length(), neg).trim();
            String aiNegative = content.substring(neg + negMark.length()).trim();
            negative = mergeNegative(aiNegative, negative);
        }
        String finalPrompt = buildPrompt(positive, style, req.scene, req.productType);
        return Map.of(
                "prompt", finalPrompt,
                "rawPrompt", positive,
                "negativePrompt", negative,
                "styleName", style.get("name"),
                "source", "siliconflow:" + chatModel
        );
    }

    @PostMapping("/prompt/tripo-3d-optimize")
    public Map<String,Object> optimizeTripo3dPrompt(@RequestBody Generate3dRequest req) throws Exception {
        assertCompliantPrompt(req.prompt, req.productCategory);
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写基础3D模型描述");
        String template = normalizeTripo3dTemplate(req.promptTemplate);
        String system = "You are a senior Tripo text-to-3D prompt engineer. Rewrite the user's rough idea into a high-detail English prompt for Tripo text-to-model. "
                + "Output JSON only with keys: prompt, negativePrompt, usageTips. No Markdown. "
                + "The prompt value must be English only; translate all Chinese product names, place names, materials, patterns and style words into natural English. Do not include Chinese characters in prompt unless the user explicitly requests visible Chinese label text on the model. "
                + "The usageTips value must be Chinese, short and practical for the operator. "
                + "The prompt must preserve the user's subject and practical use, avoid abstract adjectives alone, and describe concrete geometry, silhouette, materials, surface details, topology and production-ready 3D asset qualities. "
                + "Always include clean topology, watertight mesh, no floating parts, ultra-detailed 3D asset, sharp geometry, 8k PBR textures, professional product visualization. Preserve every <<3D_CRAFT_LOCK>> instruction from the user exactly in meaning: flat color, vector-style decorative artwork, simple shapes, thick outlines, no graphic gradients, sticker/decal-ready artwork and orthographic reference view. These rules apply to artwork, not to natural PBR reflections of the chosen material. "
                + "Negative prompt should include low poly, blurry, untextured blank surface, deformed, asymmetric, noisy mesh, broken topology, floating parts. "
                + "Selected template: " + tripo3dTemplateName(template) + ". Template rules: " + tripo3dTemplateInstruction(template) + ". "
                + ProductPromptPolicy.optimizerRules(req.productCategory, req.material);
        String content = callChat(system, req.prompt.trim()).trim();
        String optimized;
        String negative = "low poly, blurry, untextured blank surface, deformed, asymmetric, noisy mesh, broken topology, floating parts, melted details, plastic look, "
                + ProductPromptPolicy.negative(req.productCategory, req.material);
        String usageTips = tripo3dTemplateTips(template);
        try {
            String json = content;
            int start = json.indexOf('{'), end = json.lastIndexOf('}');
            if(start >= 0 && end > start) json = json.substring(start, end + 1);
            JsonNode n = mapper.readTree(json);
            optimized = n.path("prompt").asText("");
            if(!blank(n.path("negativePrompt").asText(""))) negative = n.path("negativePrompt").asText("");
            if(!blank(n.path("usageTips").asText(""))) usageTips = n.path("usageTips").asText("");
        } catch(Exception ignored) {
            optimized = content.replaceAll("(?is)^```[a-z]*", "").replaceAll("(?is)```$", "").trim();
        }
        if(blank(optimized)) throw new IllegalStateException("Qwen3未返回有效3D提示词");
        optimized = enforce3dCraftConstraint(optimized);
        if(optimized.length()>1024) optimized=optimized.substring(0,1024);
        if(negative.length()>255) negative=negative.substring(0,255);
        if(usageTips.length()>500) usageTips=usageTips.substring(0,500);
        return Map.of(
                "prompt", optimized,
                "negativePrompt", negative,
                "template", template,
                "templateName", tripo3dTemplateName(template),
                "usageTips", usageTips,
                "source", "siliconflow:"+chatModel,
                "target", "tripo:text-to-model"
        );
    }

    private String normalizeTripo3dTemplate(String template) {
        String t = blank(template) ? "universal" : template.trim();
        return Set.of("fantasy", "hard_surface", "oriental", "collectible", "plush_toy", "ppc_precision", "universal").contains(t) ? t : "universal";
    }

    private String tripo3dTemplateName(String template) {
        return switch (normalizeTripo3dTemplate(template)) {
            case "fantasy" -> "史诗级奇幻/角色（高细节雕刻感）";
            case "hard_surface" -> "硬核科幻/机械（高精度硬表面）";
            case "oriental" -> "东方美学/国风（纹样与釉色）";
            case "collectible" -> "潮玩/IP 手办（精致涂装与微缩感）";
            case "plush_toy" -> "毛绒玩具（软体填充、短密绒毛、刺绣细节）";
            case "ppc_precision" -> "PPC 精密硬塑（高精度注塑、分件与微细表面）";
            default -> "万能产品模板（填空即用）";
        };
    }

    private String tripo3dTemplateInstruction(String template) {
        return switch (normalizeTripo3dTemplate(template)) {
            case "fantasy" -> "Use ancient relic, creature, statue or armor language. Emphasize intricate carvings, rune engravings, weathered stone, gold filigree, ornamentation, volumetric lighting, museum quality artifact, photorealistic PBR materials.";
            case "hard_surface" -> "Use hard-surface industrial design language. Emphasize beveled panels, seams, exposed hydraulic pistons, wiring, greeble details, brushed titanium, carbon fiber, ultra-sharp edges, studio lighting, 4k/8k texture fidelity.";
            case "oriental" -> "Use Chinese/Eastern craft language. Emphasize cloisonné enamel, filigree wirework, glossy ceramic glaze, crackle finish, jade finial, carved relief patterns, traditional craftsmanship, cultural heritage artifact.";
            case "collectible" -> "Use collectible toy / GK figurine language. Emphasize cute stylized proportions, miniature accessories, hand-painted resin texture, matte finish, metallic accents, tilt-shift product photography, softbox lighting, extremely fine surface details.";
            case "plush_toy" -> "Use premium stuffed plush toy design language. Emphasize soft rounded padded silhouette, dense short-pile faux fur, velvety microfiber fibers, subtle panel seams, embroidered eyes and nose, fabric ears and limbs, gentle squashy volume, plush product photography, no hard plastic or glossy vinyl, clean UV-ready watertight mesh, production-friendly closed geometry.";
            case "ppc_precision" -> "Use high-precision injection-molded PPC polymer product design language. Emphasize clean manufactured part separation, crisp but softly filleted edges, tight panel gaps, visible but subtle parting lines, fine engraved relief, tiny recessed grooves, accurate symmetrical details, high-density satin engineering-plastic surface, micro orange-peel texture, 8k PBR textures, UV-ready watertight mesh, production-friendly closed geometry. Avoid fabric, fur, glass, metal, glossy vinyl and melted shapes.";
            default -> "Use the structure: A [adjective] [subject] made of [primary material] and [secondary material], featuring [specific surface detail/pattern], [art style] aesthetic, [lighting type] lighting, ultra-detailed 3D asset, 8k PBR textures, sharp geometry, professional product visualization.";
        };
    }

    private String tripo3dTemplateTips(String template) {
        return switch (normalizeTripo3dTemplate(template)) {
            case "fantasy" -> "适合怪物、雕像、复杂盔甲、文物感摆件。建议描述具体雕刻、镶嵌、风化、符文和凹凸纹理，避免只写“酷/漂亮”。";
            case "hard_surface" -> "适合机甲、武器、设备和工业产品。建议强调倒角、接缝、螺丝、液压、线缆、拉丝金属和硬表面分件。";
            case "oriental" -> "适合国风器物、瓷器、景泰蓝、文博衍生品。材质要写具体：釉面、开片、掐丝、玉石、金属包边，避免塑料感。";
            case "collectible" -> "适合盲盒、IP手办、钥匙扣和微缩摆件。建议写清比例、姿态、涂装、配件、底座和哑光/金属局部材质。";
            case "plush_toy" -> "适合毛绒公仔、玩偶、吉祥物。建议写清动物/IP主体、圆润比例、短毛或长毛、主辅色、刺绣五官、耳朵尾巴、缝线和填充感；避免同时写树脂、金属、透明等冲突材质。";
            case "ppc_precision" -> "适合硬塑摆件、精密结构玩具和量产概念。建议写清分件位置、拼缝、浅浮雕、凹槽、倒角、底座与功能结构；避免把毛绒、织物、金属、透明件和高光搪胶同时混入。";
            default -> "适合普通产品快速转3D。先生成基础形态，再追加材质、纹样、PBR、clean topology、watertight mesh 等细节词进行二次优化。";
        };
    }

    @PostMapping("/prompt/tripo-optimize")
    public Map<String,Object> optimizeTripoImagePrompt(@RequestBody GenerateImageRequest req) throws Exception {
        assertCompliantPrompt(req.prompt, req.productCategory);
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写基础创意描述");
        String sourcePrompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        String provider = nullToEmpty(req.provider).toLowerCase(Locale.ROOT);
        String system = "You are a senior English prompt writer for premium AI image generation, specializing in cinematic commercial product photography, official brand visuals, cultural creative products, packaging concepts, and realistic lifestyle scenes. "
                + "Rewrite the user's Chinese or rough idea into ONE polished English image-generation prompt. Output the final English prompt only: no title, no explanation, no negative prompt, no Markdown, no Chinese characters unless the user explicitly asks for visible Chinese text printed on the product. "
                + "Use this reference template and tone: A photo of a computer screen displaying a Spotify playlist during golden-hour evening light in a living room with many green plants in the background. The playlist says GPT-image-2. The caption is \"this new image model from OpenAI is dope.\" The artists are Replicate. The songs are themed around open-source AI and machine learning. The account name is Replicate. Use the Replicate logo as the profile picture and artist image. "
                + "Follow the same structure: clear photographic subject, specific environment, warm cinematic lighting, exact visible text when provided, brand/profile/logo placement when relevant, detailed product or interface contents, realistic background objects, premium composition, shallow depth of field, tactile materials, sharp focal details, official and trustworthy visual tone. "
                + "Preserve the user's actual product, place, cultural theme, brand elements, materials, colors, label text, audience, and use case. If the user provides Chinese product/region names, translate them naturally into English unless they are meant to appear as printed text. "
                + "For packaging or product concepts, describe the package shape, paper/plastic/metal/ceramic texture, typography, illustration style, net weight or label copy if supplied, countertop/tabletop/studio setting, lens, depth of field, and commercial product-shot quality. "
                + "Keep it concise but rich, within 900 English words. Target provider: " + (blank(provider) ? "general" : provider) + ". "
                + ProductPromptPolicy.optimizerRules(req.productCategory, req.material);
        String optimized=callChat(system,sourcePrompt).trim();
        int maxPromptLength = "imagen".equalsIgnoreCase(nullToEmpty(req.provider)) ? 1800 : ("jimeng".equalsIgnoreCase(nullToEmpty(req.provider)) ? 760 : 1024);
        if(optimized.length()>maxPromptLength)optimized=optimized.substring(0,maxPromptLength);
        String usageGuide = buildProductUsageGuide(req, optimized);
        return Map.of(
                "prompt", optimized,
                "usageGuide", usageGuide,
                "source", "siliconflow:" + chatModel,
                "target", switch (provider) { case "jimeng" -> "jimeng-seedream-4.6:text-to-image"; case "imagen" -> "google-imagen-4:text-to-image"; case "modao" -> "modao:text-to-image"; default -> "tripo:text-to-image"; }
        );
    }

    /**
     * Refine a user's change request into an image-to-image instruction. This
     * is deliberately separate from text-to-image prompt optimization: the
     * current image remains the visual source, while the requested edit must
     * be concrete enough for the edit model to carry out.
     */
    @PostMapping("/prompt/image-edit-optimize")
    public Map<String,Object> optimizeImageEditPrompt(@RequestBody GenerateImageRequest req) throws Exception {
        assertCompliantPrompt(req.refinementNote, req.productCategory);
        if (blank(req.refinementNote)) throw new IllegalArgumentException("请先填写希望修改的内容");
        String system = "You are a precise image-to-image editing prompt writer for commercial cultural creative products. "
                + "Return ONE concise ENGLISH edit prompt only, with no title, explanation, Markdown, JSON, or Chinese. "
                + "The current image is the reference source. Preserve its recognizable main subject identity, cultural theme, dominant color family, and key decorative motifs unless the user's edit explicitly changes one of them. "
                + "The user's requested edit is mandatory and has higher priority than preserving shape, carrier, composition, or product form. "
                + "If they request a new shape, carrier, product category, pose, composition, or structure, state that change explicitly and make it visibly clear. "
                + "Do not return a near-duplicate of the current image, but do not invent an unrelated subject, theme, color world, or random extra objects. "
                + "Describe one coherent finished commercial product image, centered and clearly readable. Keep under 700 characters. "
                + ProductPromptPolicy.optimizerRules(req.productCategory, req.material);
        String user = "Original creation direction: " + nullToEmpty(req.prompt) + "\n"
                + "Product category: " + nullToEmpty(req.productCategory) + "\n"
                + "Material: " + nullToEmpty(req.material) + "\n"
                + "MANDATORY USER EDIT: " + req.refinementNote.trim();
        String optimized = callChat(system, user).trim()
                .replaceAll("(?is)^```[a-z]*", "").replaceAll("(?is)```$", "").trim();
        if (blank(optimized)) throw new IllegalStateException("提示词优化服务未返回有效修改方案");
        if (optimized.length() > 900) optimized = optimized.substring(0, 900);
        return Map.of("prompt", optimized, "source", "siliconflow:" + chatModel, "target", "siliconflow:image-to-image");
    }

    private String buildProductUsageGuide(GenerateImageRequest req, String optimizedPrompt) {
        try {
            String provider = "imagen".equalsIgnoreCase(nullToEmpty(req.provider)) ? "Google Imagen 4" : "Tripo";
            String system = "你是文创产品说明书文案专家。请根据用户原始产品需求和已优化的画面Prompt，生成“产品本身”的中文使用说明，而不是AI提示词使用说明。只输出中文，不要Markdown代码块。要求像正式商品说明/包装背标，结构清晰、可直接给客户或领导看。必须包含：1）产品定位；2）适用场景/适用人群；3）使用方法；4）保养或储存方式；5）安全/注意事项；6）一句官方感温馨提示。若产品是食品/包装概念，要写食用/储存/过敏或生产信息核验提示；若是钥匙扣、摆件、冰箱贴等非食品，要写佩戴/摆放/清洁/儿童误吞等注意事项。控制在350字以内。";
            String user = "服务商：" + provider + "\n原始产品需求：" + nullToEmpty(req.prompt) + "\n画面Prompt：" + nullToEmpty(optimizedPrompt);
            String guide = callChat(system, user).trim();
            guide = guide.replace("**", "").replace("__", "").replaceAll("(?m)^#+\\s*", "");
            if(guide.length() > 700) guide = guide.substring(0, 700);
            return guide;
        } catch(Exception e) {
            return "产品使用说明：本产品适合作为文创礼品、陈列展示或日常使用场景使用。使用前请确认外观、尺寸、材质和包装标识是否符合实际打样版本；如为食品或食品包装类产品，请以最终生产标签、配料、净含量、保质期和执行标准为准。日常保存应避免高温、潮湿、暴晒和重压。儿童使用需成人陪同，避免误食小部件或包装材料。最终上市前请完成版权、商标、质检和包装合规核验。";
        }
    }

    @PostMapping("/text-to-image")
    public Map<String, Object> textToImage(@RequestBody GenerateImageRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        assertCompliantPrompt(req.prompt, req.productCategory);
        Map<String, Object> style = style(req.styleId);
        String finalPrompt = buildPrompt(enforceMaterialConstraint(req.prompt, req.productCategory, req.material), style, req.scene, req.productType);
        String negative = mergeNegative(mergeNegative(req.negativePrompt, (String) style.get("negativePrompt")), ProductPromptPolicy.negative(req.productCategory, req.material));
        String jobNo = no("AIG");
        Long jobId = createJob(jobNo, "text_to_image", "siliconflow", imageModel, req.styleId, null, finalPrompt, negative, "running", null, null);
        assignJobOwner(jobId, ownerUserId);
        try {
            if (siliconflowApiKey == null || siliconflowApiKey.trim().isEmpty() || siliconflowApiKey.contains("YOUR_")) {
                throw new IllegalStateException("未配置 siliconflow.api.key，请在 shixun/application-local.properties 配置");
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", imageModel);
            payload.put("prompt", finalPrompt);
            payload.put("negative_prompt", negative);
            payload.put("image_size", req.imageSize == null ? "1024x1024" : req.imageSize);
            payload.put("batch_size", 1);
            if (req.seed != null) payload.put("seed", req.seed);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.siliconflow.cn/v1/images/generations"))
                    .header("Authorization", "Bearer " + siliconflowApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("SiliconFlow HTTP " + response.statusCode() + ": " + response.body());
            JsonNode root = mapper.readTree(response.body());
            String remoteUrl = extractImageUrl(root);
            String localUrl = saveRemoteImage(remoteUrl, "ai-2d-", ".png");
            Long assetId = createAsset(req.title == null || req.title.isBlank() ? "AI生成图片" : req.title, "image", "ai_generated", localUrl, localUrl, finalPrompt, negative, req.styleId, null, "png", req.tags,
                    withAssetOwner(Map.of("provider", "siliconflow", "model", imageModel, "remoteUrl", remoteUrl), ownerUserId));
            jdbc.update("UPDATE ai_generation_job SET status='succeeded', output_asset_id=? WHERE id=?", assetId, jobId);
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("jobNo", jobNo); result.put("assetId", assetId); result.put("prompt", finalPrompt);
            result.put("negativePrompt", negative); result.put("status", "succeeded");
            result.put("source", "siliconflow:" + imageModel); result.put("model", imageModel);
            addSignedAssetFields(result, assetId, "image");
            return result;
        } catch (Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed', error_message=? WHERE id=?", e.getMessage(), jobId);
            throw e;
        }
    }

    @PostMapping("/volcengine/seedream/multiview")
    public Map<String,Object> volcengineSeedreamMultiview(@RequestBody MultiViewImageRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        assertCompliantPrompt(req.prompt, null);
        if (blank(req.prompt)) throw new IllegalArgumentException("请先填写要生成的产品或角色描述");
        if (req.inputAssetId == null) throw new IllegalArgumentException("请先上传一张产品参考图，再生成多视图");
        requireAssetAccess(req.inputAssetId);
        if (blank(siliconflowApiKey) || siliconflowApiKey.contains("YOUR_")) {
            throw new IllegalStateException("多视图服务暂不可用，请联系平台管理员检查已配置的图改图服务");
        }
        String size = blank(req.size) ? "2K" : req.size.trim();
        if (!Set.of("1K", "2K").contains(size)) throw new IllegalArgumentException("多视图仅支持 1K 或 2K 尺寸");
        if (Boolean.TRUE.equals(req.queue)) return queueSiliconflowMultiView(req, ownerUserId);
        List<String> views = req.viewCount != null && req.viewCount == 3
                ? List.of("front", "left", "back")
                : List.of("front", "left", "back", "right");
        Map<String,String> labels = Map.of("front", "正面", "left", "左侧", "back", "背面", "right", "右侧");
        List<Map<String,Object>> images = new ArrayList<>();
        String basePrompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        String referenceImage = buildInputImageForSiliconFlow(req.inputAssetId);
        for (String view : views) {
            String viewPrompt = "PRODUCT TURNAROUND IMAGE EDIT. Use the supplied reference image as the only source of truth. "
                    + "Generate exactly one " + view + " view of the SAME product, not a redesign. "
                    + "Strictly preserve its recognizable identity, silhouette, proportions, colors, material finish, motifs, accessories and all distinctive details. "
                    + "For unseen surfaces, infer only the minimal structure needed to keep the same product consistent. "
                    + "Show the full centered product at the same scale on a clean light-neutral studio background. "
                    + "No collage, no split screen, no extra object, no human, no packaging mockup, no text, no logo, no watermark. "
                    + "Product direction: " + basePrompt;
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("model", imageEditModel);
            payload.put("prompt", viewPrompt);
            payload.put("image", referenceImage);
            payload.put("negative_prompt", "different product, changed silhouette, changed color palette, collage, split screen, multiple objects, duplicate product, person, hand, text, logo, watermark, cropped object, blurry, distorted product structure");
            payload.put("num_inference_steps", 28);
            payload.put("guidance_scale", 6);
            payload.put("batch_size", 1);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(siliconflowImagesUrl))
                    .header("Authorization", "Bearer " + siliconflowApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("多视图生成失败（" + labels.get(view) + "）HTTP " + response.statusCode());
            }
            String remoteUrl = extractImageUrl(mapper.readTree(response.body()));
            if (blank(remoteUrl)) throw new IllegalStateException("多视图服务未返回" + labels.get(view) + "图地址");
            String localUrl = saveRemoteImage(remoteUrl, "siliconflow-multiview-" + view + "-", ".png");
            Map<String,Object> metadata = new LinkedHashMap<>();
            metadata.put("provider", "siliconflow"); metadata.put("model", imageEditModel);
            metadata.put("view", view); metadata.put("remoteUrl", remoteUrl); metadata.put("multiView", true);
            addProductIdentity(metadata, req.productKey, req.productCategory, req.material);
            metadata.put("createdByUserId", ownerUserId);
            if (currentConsumerUserIdOrNull() != null) metadata.put("consumerWork", true);
            Long assetId = createAsset("AI 多视图参考 · " + labels.get(view), "image", "ai_generated", localUrl, localUrl,
                    viewPrompt, null, null, req.inputAssetId, "png", "AI生成,多视图,3D参考," + labels.get(view), metadata);
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("view", view); item.put("label", labels.get(view)); item.put("assetId", assetId);
            addSignedAssetFields(item, assetId, "image");
            images.add(item);
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("provider", "siliconflow"); out.put("model", imageEditModel); out.put("images", images);
        out.put("viewCount", views.size());
        out.put("message", "AI 图改图已生成 " + views.size() + " 个一致视角，可一键带入 Tripo 多视图建模");
        return out;
    }

    /**
     * Ark 图片接口使用 Bearer API Key；即梦视觉接口使用 AK/SK 签名。
     * 只有在旧配置明确放入 Vx 开头的 Ark Key 时才允许复用，避免把 AK/SK
     * 误当成 Bearer Key 发给 Ark。
     */
    private String resolvedArkApiKey() {
        if (!blank(volcengineArkApiKey) && !volcengineArkApiKey.contains("YOUR_")) return volcengineArkApiKey;
        if (!blank(jimengApiKey) && jimengApiKey.trim().startsWith("Vx")) return jimengApiKey;
        return "";
    }

    @GetMapping("/ark/config")
    public Map<String, Object> arkImageConfig() {
        boolean configured = !blank(resolvedArkApiKey());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", configured);
        result.put("provider", "Volcengine Ark");
        result.put("displayName", "Doubao-Seedream-5.0-pro");
        result.put("model", volcengineArkSeedreamImageModel);
        result.put("apiVersion", "OpenAI-compatible Images API v3");
        result.put("imageSizes", List.of("1K", "2K"));
        result.put("watermarkEnabled", true);
        result.put("message", configured
                ? "用户端文生图使用火山方舟 Doubao-Seedream-5.0-pro，生成内容会保留 AI 标识并保存到作品库。"
                : "未配置火山方舟 Ark API Key。请在服务器 .env 配置 VOLCENGINE_ARK_API_KEY。");
        return result;
    }

    @PostMapping("/ark/text-to-image")
    public Map<String, Object> arkTextToImage(@RequestBody GenerateImageRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        Long consumerUserId = currentConsumerUserIdOrNull();
        assertCompliantPrompt(req.prompt, req.productCategory);
        if (blank(resolvedArkApiKey())) throw new IllegalStateException("未配置火山方舟 Ark API Key，请联系平台管理员完成 VOLCENGINE_ARK_API_KEY 配置");
        if (blank(req.prompt)) throw new IllegalArgumentException("请先填写或生成生图提示词");

        String prompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        Map<String, Object> style = style(req.styleId);
        String finalPrompt = buildPrompt(prompt, style, req.scene, req.productType);
        if (finalPrompt.length() > 3500) finalPrompt = finalPrompt.substring(0, 3500);
        String size = Set.of("1K", "2K").contains(nullToEmpty(req.imagenImageSize)) ? req.imagenImageSize : "2K";
        String format = "jpg".equalsIgnoreCase(nullToEmpty(req.imagenOutputFormat)) ? "jpg" : "png";
        String negative = mergeNegative(req.negativePrompt, (String) style.get("negativePrompt"));
        synchronized (arkQueueSubmissionLock) {
            List<Map<String, Object>> active = jdbc.queryForList(
                    "SELECT id,prompt,product_key productKey FROM ai_generation_job " +
                            "WHERE created_by=? AND provider='volcengine_ark' AND job_type='text_to_image' " +
                            "AND status IN ('queued','running') AND output_asset_id IS NULL ORDER BY id LIMIT 1",
                    ownerUserId);
            if (!active.isEmpty()) {
                Map<String, Object> existing = active.get(0);
                boolean sameRequest = finalPrompt.equals(str(existing.get("prompt")))
                        && Objects.equals(nullToEmpty(req.productKey), nullToEmpty(str(existing.get("productKey"))));
                if (!sameRequest) {
                    throw new IllegalStateException("你已有一项图片正在排队或生成，请等待完成后再提交新作品");
                }
                Map<String, Object> response = arkImageJobResponse(((Number) existing.get("id")).longValue());
                response.put("reused", true);
                return response;
            }

            Long creditTxId = consumerUserId == null ? null : reserveConsumerCredit(
                    consumerUserId, "image2d", consumerCreditCost("image2d"), "C端火山方舟生图预扣");
            String jobNo = no("ARK");
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            requestPayload.put("title", req.title);
            requestPayload.put("requestedFormat", format);
            Long jobId;
            try {
                jobId = createArkQueuedJob(jobNo, req.styleId, finalPrompt, negative, size,
                        req.productKey, req.productCategory, req.material, ownerUserId, creditTxId, requestPayload);
                linkCreditTransaction(creditTxId, jobId, null);
            } catch (Exception e) {
                refundConsumerCredit(creditTxId, safeMessage(e));
                throw e;
            }
            return arkImageJobResponse(jobId);
        }
    }

    @GetMapping("/ark/image-jobs/{jobId}")
    public Map<String, Object> arkImageJob(@PathVariable Long jobId) {
        requireJobAccess(jobId);
        return arkImageJobResponse(jobId);
    }

    @GetMapping("/image-jobs/{jobId}")
    public Map<String, Object> imageJob(@PathVariable Long jobId) {
        requireJobAccess(jobId);
        return imageGenerationJobResponse(jobId);
    }

    @Scheduled(fixedDelayString = "${volcengine.ark.queue.dispatch-interval-ms:1000}")
    public void dispatchArkImageQueue() {
        if (!arkQueueTableVerified.get()) {
            if (!arkQueueTableAvailable()) return;
            arkQueueTableVerified.set(true);
        }
        if (arkQueueRecovered.compareAndSet(false, true)) {
            jdbc.update("UPDATE ai_generation_job SET status='queued',progress=0,started_at=NULL," +
                            "error_message='服务重启后已自动恢复排队' WHERE provider='volcengine_ark' " +
                            "AND job_type='text_to_image' AND status='running' AND output_asset_id IS NULL");
        }
        int concurrency = normalizedArkQueueConcurrency();
        int available = concurrency - activeArkImageJobs.size();
        if (available <= 0) return;
        List<Long> queued = jdbc.queryForList(
                "SELECT id FROM ai_generation_job WHERE provider='volcengine_ark' " +
                        "AND job_type='text_to_image' AND status='queued' AND output_asset_id IS NULL " +
                        "ORDER BY id LIMIT " + available,
                Long.class);
        for (Long jobId : queued) {
            int claimed = jdbc.update("UPDATE ai_generation_job SET status='running',progress=10," +
                            "started_at=COALESCE(started_at,NOW()),error_message=NULL " +
                            "WHERE id=? AND status='queued'", jobId);
            if (claimed != 1 || !activeArkImageJobs.add(jobId)) continue;
            try {
                arkImageGenerationExecutor.execute(() -> {
                    try {
                        processArkImageJob(jobId);
                    } finally {
                        activeArkImageJobs.remove(jobId);
                    }
                });
            } catch (RuntimeException e) {
                activeArkImageJobs.remove(jobId);
                jdbc.update("UPDATE ai_generation_job SET status='queued',progress=0,started_at=NULL," +
                        "error_message='生成执行器繁忙，已自动重新排队' WHERE id=? AND status='running'", jobId);
            }
        }
    }

    private boolean arkQueueTableAvailable() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)=LOWER(?)",
                    Integer.class, "ai_generation_job");
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void processArkImageJob(Long jobId) {
        Map<String, Object> job;
        try {
            job = jdbc.queryForMap("SELECT id,job_no jobNo,model_name modelName,style_id styleId," +
                            "prompt,negative_prompt negativePrompt,export_formats exportFormats," +
                            "product_key productKey,product_name productName,product_material productMaterial," +
                            "created_by createdBy,credit_transaction_id creditTransactionId," +
                            "request_payload_json requestPayloadJson,attempt_count attemptCount,status " +
                            "FROM ai_generation_job WHERE id=?", jobId);
        } catch (Exception e) {
            return;
        }
        if (!"running".equals(str(job.get("status")))) return;
        Long creditTxId = numberAsLong(job.get("creditTransactionId"));
        Long ownerUserId = numberAsLong(job.get("createdBy"));
        try {
            if (blank(resolvedArkApiKey())) throw new IllegalStateException("未配置火山方舟 Ark API Key");
            int attempts = job.get("attemptCount") instanceof Number ? ((Number) job.get("attemptCount")).intValue() : 0;
            int maxAttempts = Math.max(1, Math.min(arkQueueRetryAttempts, 6));
            JsonNode generated = null;
            while (attempts < maxAttempts) {
                attempts++;
                jdbc.update("UPDATE ai_generation_job SET attempt_count=?,progress=20,error_message=NULL WHERE id=?", attempts, jobId);
                try {
                    generated = createArkTextImage(str(job.get("prompt")), normalizedArkImageSize(job.get("exportFormats")));
                    break;
                } catch (ArkRateLimitException e) {
                    if (attempts >= maxAttempts) throw e;
                    long delaySeconds = Math.max(1, Math.min(arkQueueRetryDelaySeconds, 30)) * attempts;
                    jdbc.update("UPDATE ai_generation_job SET progress=10,error_message=? WHERE id=?",
                            "模型限流，" + delaySeconds + " 秒后自动重试（" + attempts + "/" + maxAttempts + "）", jobId);
                    try {
                        TimeUnit.SECONDS.sleep(delaySeconds);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        jdbc.update("UPDATE ai_generation_job SET status='queued',progress=0,started_at=NULL," +
                                "error_message='服务停止，任务已自动重新排队' WHERE id=? AND status='running'", jobId);
                        return;
                    }
                }
            }
            if (generated == null) throw new IllegalStateException("火山方舟未返回图片结果");

            jdbc.update("UPDATE ai_generation_job SET progress=70,error_message=NULL WHERE id=?", jobId);
            String remoteImage = extractImageUrl(generated);
            String requestedFormat = requestPayloadText(job.get("requestPayloadJson"), "requestedFormat");
            String format = normalizedArkOutputFormat(generated, requestedFormat);
            String localImage = saveRemoteImage(remoteImage, "ark-seedream-", "." + format);
            jdbc.update("UPDATE ai_generation_job SET progress=85 WHERE id=?", jobId);

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("provider", "volcengine_ark");
            metadata.put("model", str(job.get("modelName")));
            metadata.put("remoteImage", remoteImage);
            metadata.put("imageSize", normalizedArkImageSize(job.get("exportFormats")));
            metadata.put("watermark", true);
            metadata.put("aiGenerated", true);
            addProductIdentity(metadata, str(job.get("productKey")), str(job.get("productName")), str(job.get("productMaterial")));
            if (ownerUserId != null) {
                metadata.put("createdByUserId", ownerUserId);
                if (hasPersistedRole(ownerUserId, "user")) metadata.put("consumerWork", true);
            }
            String requestedTitle = requestPayloadText(job.get("requestPayloadJson"), "title");
            String title = blank(requestedTitle) ? "之间智造AI效果图-" + str(job.get("jobNo")) : requestedTitle.trim();
            Long styleId = numberAsLong(job.get("styleId"));
            Long assetId = createAsset(title, "image", "ai_generated", localImage, localImage,
                    str(job.get("prompt")), str(job.get("negativePrompt")), styleId, null, format,
                    "AI生成,火山方舟,豆包Seedream,文创生图", metadata);
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,status='succeeded',progress=100," +
                    "error_message=NULL,finished_at=NOW() WHERE id=?", assetId, jobId);
            try {
                completeConsumerCredit(creditTxId, jobId, assetId);
            } catch (Exception creditError) {
                jdbc.update("UPDATE ai_generation_job SET error_message=? WHERE id=?",
                        "作品已生成，积分结算待核对：" + safeMessage(creditError), jobId);
            }
        } catch (Exception e) {
            String error = safeMessage(e);
            try {
                refundConsumerCredit(creditTxId, error);
                // Do not expose a terminal failure before its reserved credit
                // is settled. Otherwise a polling client can show a failed
                // generation while the user's balance is still frozen.
                jdbc.update("UPDATE ai_generation_job SET status='failed',progress=0,error_message=?,finished_at=NOW() " +
                        "WHERE id=? AND status<>'succeeded'", error, jobId);
            } catch (Exception refundError) {
                jdbc.update("UPDATE ai_generation_job SET status='failed',progress=0,error_message=?,finished_at=NOW() " +
                                "WHERE id=? AND status<>'succeeded'",
                        error + "；积分退回待核对：" + safeMessage(refundError), jobId);
            }
        }
    }

    private Long createArkQueuedJob(String jobNo, Long styleId, String prompt, String negative, String size,
                                    String productKey, String productName, String material, Long ownerUserId,
                                    Long creditTxId, Map<String, Object> requestPayload) throws Exception {
        KeyHolder kh = new GeneratedKeyHolder();
        String payloadJson = mapper.writeValueAsString(requestPayload == null ? Map.of() : requestPayload);
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ai_generation_job (job_no,job_type,provider,model_name,style_id," +
                            "product_key,product_name,product_material,prompt,negative_prompt,status,progress," +
                            "error_message,export_formats,created_by,credit_transaction_id,request_payload_json,attempt_count) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, jobNo); ps.setString(2, "text_to_image"); ps.setString(3, "volcengine_ark");
            ps.setString(4, volcengineArkSeedreamImageModel);
            if (styleId == null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, styleId);
            ps.setString(6, blank(productKey) ? null : productKey.trim());
            ps.setString(7, blank(productName) ? null : productName.trim());
            ps.setString(8, blank(material) ? null : material.trim());
            ps.setString(9, prompt); ps.setString(10, negative); ps.setString(11, "queued"); ps.setInt(12, 0);
            ps.setNull(13, java.sql.Types.LONGVARCHAR); ps.setString(14, size);
            if (ownerUserId == null) ps.setNull(15, java.sql.Types.BIGINT); else ps.setLong(15, ownerUserId);
            if (creditTxId == null) ps.setNull(16, java.sql.Types.BIGINT); else ps.setLong(16, creditTxId);
            ps.setString(17, payloadJson); ps.setInt(18, 0);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private Map<String, Object> arkImageJobResponse(Long jobId) {
        Map<String, Object> result = imageGenerationJobResponse(jobId);
        if (!"volcengine_ark".equals(str(result.get("provider")))) {
            throw new IllegalArgumentException("该任务不是火山方舟图片任务");
        }
        return result;
    }

    private Map<String, Object> imageGenerationJobResponse(Long jobId) {
        Map<String, Object> job = jdbc.queryForMap(
                "SELECT id,job_no jobNo,provider,model_name modelName,output_asset_id outputAssetId," +
                        "product_key productKey,product_name productName,product_material productMaterial," +
                        "status,progress,attempt_count attemptCount,error_message errorMessage," +
                        "job_type jobType,result_payload_json resultPayloadJson,created_by createdBy," +
                        "credit_transaction_id creditTransactionId," +
                        "created_at createdAt,started_at startedAt,finished_at finishedAt " +
                        "FROM ai_generation_job WHERE id=?",
                jobId);
        Map<String, Object> out = new LinkedHashMap<>();
        String status = str(job.get("status"));
        String provider = str(job.get("provider"));
        String jobType = str(job.get("jobType"));
        out.put("jobId", jobId);
        out.put("jobNo", job.get("jobNo"));
        out.put("provider", provider);
        out.put("model", job.get("modelName"));
        out.put("jobType", jobType);
        out.put("productKey", job.get("productKey"));
        out.put("productName", job.get("productName"));
        out.put("productMaterial", job.get("productMaterial"));
        out.put("status", status);
        out.put("progress", job.get("progress"));
        out.put("attemptCount", job.get("attemptCount"));
        out.put("errorMessage", job.get("errorMessage"));
        out.put("createdAt", job.get("createdAt"));
        out.put("startedAt", job.get("startedAt"));
        out.put("finishedAt", job.get("finishedAt"));
        out.put("source", imageJobSource(provider, jobType));
        out.put("queueConcurrency", imageQueueConcurrency(provider, jobType));
        if ("queued".equals(status)) {
            Integer position = queuedImageJobPosition(provider, jobType, jobId);
            out.put("queuePosition", position == null ? 1 : position);
            out.put("message", "已进入图片任务队列，轮到后会自动开始");
        } else if ("running".equals(status)) {
            out.put("queuePosition", 0);
            out.put("message", runningImageJobMessage(provider, jobType));
        } else if ("failed".equals(status)) {
            out.put("queuePosition", 0);
            out.put("message", blank(str(job.get("errorMessage"))) ? "图片生成失败" : str(job.get("errorMessage")));
        } else if ("succeeded".equals(status)) {
            Map<String, Object> resultPayload = jobJsonMap(job.get("resultPayloadJson"));
            if ("multi_view".equals(jobType)) {
                List<Map<String, Object>> images = signedMultiViewResultImages(resultPayload);
                out.put("images", images);
                out.put("viewCount", images.size());
                out.put("message", blank(str(resultPayload.get("message")))
                        ? "AI 多视图已生成并保存到作品库。"
                        : str(resultPayload.get("message")));
            } else {
                Long assetId = numberAsLong(job.get("outputAssetId"));
                if (assetId != null) {
                    out.put("assetId", assetId);
                    out.put("id", assetId);
                    out.put("assetType", "image");
                    out.put("sourceType", "ai_generated");
                    out.put("assetStatus", "draft");
                    addSignedAssetFields(out, assetId, "image");
                }
                copyJobResultValue(resultPayload, out, "referenceAnalysis", "referenceAnalysisSource");
                out.put("message", blank(str(resultPayload.get("message")))
                        ? "AI 产品图已生成并保存到作品库。"
                        : str(resultPayload.get("message")));
            }
        }
        Long ownerUserId = numberAsLong(job.get("createdBy"));
        if (ownerUserId != null && numberAsLong(job.get("creditTransactionId")) != null && hasPersistedRole(ownerUserId, "user")) {
            out.put("creditAccount", creditAccountMap(ownerUserId));
        }
        return out;
    }

    private String imageJobSource(String provider, String jobType) {
        if ("volcengine_ark".equals(provider)) return "火山方舟 · Doubao-Seedream-5.0-pro";
        if ("siliconflow".equals(provider)) {
            return "multi_view".equals(jobType) ? "硅基流动 · 多视图生成" : "硅基流动 · 图改图";
        }
        return blank(provider) ? "AI 图片任务" : provider;
    }

    private int imageQueueConcurrency(String provider, String jobType) {
        if ("volcengine_ark".equals(provider)) return normalizedArkQueueConcurrency();
        if (isQueuedSiliconflowImageJob(provider, jobType)) return normalizedSiliconflowImageQueueConcurrency();
        return 0;
    }

    private String runningImageJobMessage(String provider, String jobType) {
        if ("multi_view".equals(jobType)) return "正在生成一致的产品多视图";
        if ("image_to_image".equals(jobType)) return "正在依据参考图生成产品视觉";
        if ("volcengine_ark".equals(provider)) return "之间大模型正在生成产品图";
        return "正在生成图片";
    }

    private Integer queuedImageJobPosition(String provider, String jobType, Long jobId) {
        if ("volcengine_ark".equals(provider)) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ai_generation_job WHERE provider='volcengine_ark' " +
                    "AND job_type='text_to_image' AND status='queued' AND id<=?", Integer.class, jobId);
        }
        if (isQueuedSiliconflowImageJob(provider, jobType)) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM ai_generation_job WHERE provider='siliconflow' " +
                    "AND job_type IN ('image_to_image','multi_view') AND status='queued' AND id<=?", Integer.class, jobId);
        }
        return 0;
    }

    private boolean isQueuedSiliconflowImageJob(String provider, String jobType) {
        return "siliconflow".equals(provider) && Set.of("image_to_image", "multi_view").contains(jobType);
    }

    private int normalizedSiliconflowImageQueueConcurrency() {
        return Math.max(1, Math.min(siliconflowImageQueueConcurrency, 8));
    }

    private Map<String, Object> jobJsonMap(Object raw) {
        try {
            JsonNode parsed = storedJsonNode(raw);
            if (parsed == null || !parsed.isObject()) return new LinkedHashMap<>();
            Map<String, Object> result = new LinkedHashMap<>();
            parsed.fields().forEachRemaining(entry -> result.put(entry.getKey(), mapper.convertValue(entry.getValue(), Object.class)));
            return result;
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * MySQL JSON columns normally return an object string. Some JDBC/H2 paths return that JSON as a
     * JSON string literal instead, so unwrap textual roots before binding a queued request or result.
     */
    private JsonNode storedJsonNode(Object raw) throws IOException {
        if (raw == null) return null;
        JsonNode node;
        if (raw instanceof JsonNode jsonNode) {
            node = jsonNode;
        } else if (raw instanceof Map<?, ?> || raw instanceof Collection<?>) {
            node = mapper.valueToTree(raw);
        } else {
            String json = raw instanceof byte[] ? new String((byte[]) raw, StandardCharsets.UTF_8) : String.valueOf(raw);
            if (blank(json) || "null".equalsIgnoreCase(json.trim())) return null;
            node = mapper.readTree(json);
        }
        for (int level = 0; node != null && node.isTextual() && level < 2; level++) {
            String embeddedJson = node.asText();
            if (blank(embeddedJson) || "null".equalsIgnoreCase(embeddedJson.trim())) return null;
            node = mapper.readTree(embeddedJson);
        }
        return node;
    }

    private void copyJobResultValue(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !blank(String.valueOf(value))) target.put(key, value);
        }
    }

    private List<Map<String, Object>> signedMultiViewResultImages(Map<String, Object> resultPayload) {
        Object rawImages = resultPayload.get("images");
        if (!(rawImages instanceof List<?> rows)) return List.of();
        List<Map<String, Object>> images = new ArrayList<>();
        for (Object raw : rows) {
            if (!(raw instanceof Map<?, ?> source)) continue;
            Map<String, Object> image = new LinkedHashMap<>();
            source.forEach((key, value) -> image.put(String.valueOf(key), value));
            Long assetId = numberAsLong(image.get("assetId"));
            if (assetId == null || assetId <= 0) continue;
            image.put("assetId", assetId);
            addSignedAssetFields(image, assetId, "image");
            images.add(image);
        }
        return images;
    }

    private int normalizedArkQueueConcurrency() {
        return Math.max(1, Math.min(arkQueueConcurrency, 16));
    }

    private String normalizedArkImageSize(Object value) {
        String size = str(value);
        return Set.of("1K", "2K").contains(size) ? size : "2K";
    }

    private String normalizedArkOutputFormat(JsonNode generated, String requestedFormat) {
        String actual = generated.path("data").path(0).path("output_format").asText("").toLowerCase(Locale.ROOT);
        if ("jpeg".equals(actual)) actual = "jpg";
        if (Set.of("jpg", "png", "webp").contains(actual)) return actual;
        return "jpg".equalsIgnoreCase(requestedFormat) ? "jpg" : "png";
    }

    private String requestPayloadText(Object rawPayload, String field) {
        if (rawPayload == null || blank(field)) return "";
        try {
            JsonNode payload = storedJsonNode(rawPayload);
            return payload == null ? "" : payload.path(field).asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private Long numberAsLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private JsonNode createArkTextImage(String prompt, String size) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", volcengineArkSeedreamImageModel);
        payload.put("prompt", prompt);
        payload.put("response_format", "url");
        payload.put("size", size);
        payload.put("stream", false);
        payload.put("watermark", true);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(volcengineArkImagesUrl))
                .timeout(Duration.ofSeconds(150))
                .header("Authorization", "Bearer " + resolvedArkApiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw arkImageHttpError(response.statusCode(), response.body());
            return mapper.readTree(response.body());
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException("火山方舟生图请求超时，请稍后重试", e);
        } catch (IOException e) {
            throw new IllegalStateException("无法连接火山方舟生图服务：" + safeMessage(e), e);
        }
    }

    private IllegalStateException arkImageHttpError(int status, String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String errorCode = root.path("error").path("code").asText("");
            String detail = firstNonBlank(root.path("error").path("message").asText(""), root.path("message").asText(""), root.path("error").asText(""));
            if (status == 401 || status == 403) return new IllegalStateException("火山方舟 API Key 无效、模型未开通或无调用权限：" + detail);
            if ("SetLimitExceeded".equalsIgnoreCase(errorCode) || detail.contains("Safe Experience Mode")) {
                return new IllegalStateException("火山方舟模型已因安全体验模式额度用尽而暂停。请在方舟控制台的模型开通页面提高额度或关闭安全体验模式后重试。");
            }
            if (status == 429) return new ArkRateLimitException("火山方舟模型触发调用频率限制：" + detail);
            return new IllegalStateException("火山方舟生图接口失败 HTTP " + status + "：" + detail);
        } catch (Exception ignored) {
            return new IllegalStateException("火山方舟生图接口失败 HTTP " + status + "：" + raw);
        }
    }

    private static final class ArkRateLimitException extends IllegalStateException {
        private ArkRateLimitException(String message) {
            super(message);
        }
    }

    private Map<String, Object> queueSiliconflowImageToImage(GenerateImageRequest req, Long ownerUserId) throws Exception {
        requireSiliconflowImageConfiguration();
        return enqueueSiliconflowImageJob("image_to_image", "I2Q", req.styleId, req.inputAssetId,
                req.prompt, req.negativePrompt, req.productKey, req.productCategory, req.material,
                null, req, ownerUserId);
    }

    private Map<String, Object> queueSiliconflowMultiView(MultiViewImageRequest req, Long ownerUserId) throws Exception {
        requireSiliconflowImageConfiguration();
        String size = blank(req.size) ? "2K" : req.size.trim();
        return enqueueSiliconflowImageJob("multi_view", "MVQ", null, req.inputAssetId,
                req.prompt, null, req.productKey, req.productCategory, req.material,
                size, req, ownerUserId);
    }

    private void requireSiliconflowImageConfiguration() {
        if (blank(siliconflowApiKey) || siliconflowApiKey.contains("YOUR_")) {
            throw new IllegalStateException("未配置 siliconflow.api.key，请联系平台管理员检查图改图服务");
        }
    }

    private Map<String, Object> enqueueSiliconflowImageJob(String jobType, String jobPrefix, Long styleId,
                                                             Long inputAssetId, String prompt, String negative,
                                                             String productKey, String productName, String material,
                                                             String exportFormats, Object requestPayload,
                                                             Long ownerUserId) throws Exception {
        synchronized (siliconflowQueueSubmissionLock) {
            List<Map<String, Object>> active = jdbc.queryForList(
                    "SELECT id,job_type jobType,input_asset_id inputAssetId,prompt FROM ai_generation_job " +
                            "WHERE created_by=? AND provider='siliconflow' " +
                            "AND job_type IN ('image_to_image','multi_view') " +
                            "AND status IN ('queued','running') AND output_asset_id IS NULL ORDER BY id LIMIT 1",
                    ownerUserId);
            if (!active.isEmpty()) {
                Map<String, Object> existing = active.get(0);
                boolean sameRequest = jobType.equals(str(existing.get("jobType")))
                        && Objects.equals(inputAssetId, numberAsLong(existing.get("inputAssetId")))
                        && nullToEmpty(prompt).equals(nullToEmpty(str(existing.get("prompt"))));
                if (!sameRequest) {
                    throw new IllegalStateException("你已有一项图改图或多视图任务正在排队或生成，请等待完成后再提交新任务");
                }
                Map<String, Object> response = imageGenerationJobResponse(numberAsLong(existing.get("id")));
                response.put("reused", true);
                return response;
            }
            Long jobId = createQueuedSiliconflowImageJob(no(jobPrefix), jobType, styleId, inputAssetId,
                    prompt, negative, productKey, productName, material, exportFormats, requestPayload, ownerUserId);
            return imageGenerationJobResponse(jobId);
        }
    }

    private Long createQueuedSiliconflowImageJob(String jobNo, String jobType, Long styleId, Long inputAssetId,
                                                  String prompt, String negative, String productKey, String productName,
                                                  String material, String exportFormats, Object requestPayload,
                                                  Long ownerUserId) throws Exception {
        KeyHolder kh = new GeneratedKeyHolder();
        String payloadJson = mapper.writeValueAsString(requestPayload == null ? Map.of() : requestPayload);
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO ai_generation_job (job_no,job_type,provider,model_name,style_id,input_asset_id," +
                            "product_key,product_name,product_material,prompt,negative_prompt,status,progress," +
                            "error_message,export_formats,created_by,request_payload_json,attempt_count) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, jobNo); ps.setString(2, jobType); ps.setString(3, "siliconflow");
            ps.setString(4, imageEditModel);
            if (styleId == null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, styleId);
            if (inputAssetId == null) ps.setNull(6, java.sql.Types.BIGINT); else ps.setLong(6, inputAssetId);
            ps.setString(7, blank(productKey) ? null : productKey.trim());
            ps.setString(8, blank(productName) ? null : productName.trim());
            ps.setString(9, blank(material) ? null : material.trim());
            ps.setString(10, prompt); ps.setString(11, negative); ps.setString(12, "queued"); ps.setInt(13, 0);
            ps.setNull(14, java.sql.Types.LONGVARCHAR); ps.setString(15, exportFormats);
            if (ownerUserId == null) ps.setNull(16, java.sql.Types.BIGINT); else ps.setLong(16, ownerUserId);
            ps.setString(17, payloadJson); ps.setInt(18, 0);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    @Scheduled(fixedDelayString = "${siliconflow.image.queue.dispatch-interval-ms:1000}")
    public void dispatchSiliconflowImageQueue() {
        if (!arkQueueTableVerified.get()) {
            if (!arkQueueTableAvailable()) return;
            arkQueueTableVerified.set(true);
        }
        if (siliconflowQueueRecovered.compareAndSet(false, true)) {
            jdbc.update("UPDATE ai_generation_job SET status='queued',progress=0,started_at=NULL," +
                            "error_message='服务重启后已自动恢复排队' WHERE provider='siliconflow' " +
                            "AND job_type IN ('image_to_image','multi_view') AND status='running' AND output_asset_id IS NULL");
        }
        int available = normalizedSiliconflowImageQueueConcurrency() - activeSiliconflowImageJobs.size();
        if (available <= 0) return;
        List<Long> queued = jdbc.queryForList(
                "SELECT id FROM ai_generation_job WHERE provider='siliconflow' " +
                        "AND job_type IN ('image_to_image','multi_view') AND status='queued' " +
                        "AND output_asset_id IS NULL ORDER BY id LIMIT " + available,
                Long.class);
        for (Long jobId : queued) {
            int claimed = jdbc.update("UPDATE ai_generation_job SET status='running',progress=10," +
                            "started_at=COALESCE(started_at,NOW()),error_message=NULL WHERE id=? AND status='queued'", jobId);
            if (claimed != 1 || !activeSiliconflowImageJobs.add(jobId)) continue;
            try {
                siliconflowImageGenerationExecutor.execute(() -> {
                    try {
                        processSiliconflowImageJob(jobId);
                    } finally {
                        activeSiliconflowImageJobs.remove(jobId);
                    }
                });
            } catch (RuntimeException e) {
                activeSiliconflowImageJobs.remove(jobId);
                jdbc.update("UPDATE ai_generation_job SET status='queued',progress=0,started_at=NULL," +
                        "error_message='生成执行器繁忙，已自动重新排队' WHERE id=? AND status='running'", jobId);
            }
        }
    }

    private void processSiliconflowImageJob(Long jobId) {
        Map<String, Object> job;
        try {
            job = jdbc.queryForMap("SELECT id,job_type jobType,input_asset_id inputAssetId,style_id styleId," +
                    "prompt,negative_prompt negativePrompt,request_payload_json requestPayloadJson," +
                    "result_payload_json resultPayloadJson,created_by createdBy,attempt_count attemptCount,status " +
                    "FROM ai_generation_job WHERE id=?", jobId);
        } catch (Exception ignored) {
            return;
        }
        if (!"running".equals(str(job.get("status")))) return;
        try {
            String jobType = str(job.get("jobType"));
            int attempts = job.get("attemptCount") instanceof Number ? ((Number) job.get("attemptCount")).intValue() : 0;
            int maxAttempts = Math.max(1, Math.min(siliconflowImageQueueRetryAttempts, 4));
            Map<String, Object> result = null;
            while (attempts < maxAttempts) {
                attempts++;
                jdbc.update("UPDATE ai_generation_job SET attempt_count=?,progress=20,error_message=NULL WHERE id=?", attempts, jobId);
                try {
                    if ("image_to_image".equals(jobType)) {
                        GenerateImageRequest request = readQueuedImageRequest(job.get("requestPayloadJson"), GenerateImageRequest.class);
                        result = generateQueuedImageToImage(job, request);
                    } else if ("multi_view".equals(jobType)) {
                        MultiViewImageRequest request = readQueuedImageRequest(job.get("requestPayloadJson"), MultiViewImageRequest.class);
                        result = generateQueuedMultiView(job, request);
                    } else {
                        throw new IllegalStateException("不支持的图片队列任务：" + jobType);
                    }
                    break;
                } catch (SiliconflowRetryableException e) {
                    if (attempts >= maxAttempts) throw e;
                    long delaySeconds = Math.max(1, Math.min(siliconflowImageQueueRetryDelaySeconds, 30)) * attempts;
                    jdbc.update("UPDATE ai_generation_job SET progress=10,error_message=? WHERE id=?",
                            "服务暂时限流，" + delaySeconds + " 秒后自动重试（" + attempts + "/" + maxAttempts + "）", jobId);
                    try {
                        TimeUnit.SECONDS.sleep(delaySeconds);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        jdbc.update("UPDATE ai_generation_job SET status='queued',progress=0,started_at=NULL," +
                                "error_message='服务停止，任务已自动重新排队' WHERE id=? AND status='running'", jobId);
                        return;
                    }
                }
            }
            if (result == null) throw new IllegalStateException("图像服务未返回生成结果");
            Long assetId = numberAsLong(result.get("assetId"));
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,result_payload_json=?,status='succeeded'," +
                            "progress=100,error_message=NULL,finished_at=NOW() WHERE id=?",
                    assetId, mapper.writeValueAsString(result), jobId);
        } catch (Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed',progress=0,error_message=?,finished_at=NOW() " +
                    "WHERE id=? AND status<>'succeeded'", safeMessage(e), jobId);
        }
    }

    private <T> T readQueuedImageRequest(Object rawPayload, Class<T> type) throws Exception {
        JsonNode payload = storedJsonNode(rawPayload);
        if (payload == null || payload.isNull()) throw new IllegalStateException("图片任务参数丢失，请重新提交");
        if (!payload.isObject()) throw new IllegalStateException("图片任务参数格式异常，请重新提交");
        return mapper.treeToValue(payload, type);
    }

    private Map<String, Object> generateQueuedImageToImage(Map<String, Object> job, GenerateImageRequest req) throws Exception {
        Long inputAssetId = numberAsLong(job.get("inputAssetId"));
        if (inputAssetId == null) throw new IllegalStateException("图改图任务缺少参考图");
        Long ownerUserId = numberAsLong(job.get("createdBy"));
        Map<String, Object> style = style(req.styleId);
        String requestedPrompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        ReferenceImageAnalysis referenceAnalysis = analyzeReferenceImage(inputAssetId, true);
        boolean refinement = Boolean.TRUE.equals(req.refinement);
        String finalPrompt = refinement
                ? buildBalancedRefinementPrompt(requestedPrompt, req.productCategory, req.material, referenceAnalysis.visualBrief, req.refinementNote)
                : buildReferencePreservingPrompt(buildPrompt(requestedPrompt, style, req.scene, req.productType), req.productCategory, req.material, referenceAnalysis.visualBrief);
        String negative = mergeNegative(mergeNegative(req.negativePrompt, (String) style.get("negativePrompt")), refinement
                ? "unrelated subject, unrelated theme, random extra object, duplicate product, unreadable form, low detail, distorted anatomy, broken product structure, text, watermark"
                : "different subject, unrelated object, replacement design, changed silhouette, changed main composition, changed color palette, lost distinctive details, generic product, random decoration, extra main subject");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", imageEditModel);
        payload.put("prompt", finalPrompt);
        payload.put("image", readInputImageForSiliconFlow(inputAssetId));
        payload.put("num_inference_steps", refinement ? 28 : 20);
        payload.put("guidance_scale", refinement ? 6 : 4);
        if (!blank(negative)) payload.put("negative_prompt", negative);
        payload.put("batch_size", 1);
        if (req.seed != null) payload.put("seed", req.seed);
        String remoteUrl = extractImageUrl(createSiliconflowImage(payload));
        String localUrl = saveRemoteImage(remoteUrl, "ai-i2i-", ".png");
        Long assetId = createAsset(
                blank(req.title) ? "AI图改图作品" : req.title + "-图改图",
                "image", "ai_generated", localUrl, localUrl, finalPrompt, negative, req.styleId, inputAssetId, "png",
                blank(req.tags) ? "图改图,AI生成,之间味道" : req.tags + ",图改图",
                withProductIdentity(withAssetOwner(referenceImageMetadata(remoteUrl, inputAssetId, referenceAnalysis, refinement), ownerUserId),
                        req.productKey, req.productCategory, req.material));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", assetId);
        result.put("referenceAnalysis", referenceAnalysis.visualBrief);
        result.put("referenceAnalysisSource", referenceAnalysis.source);
        result.put("message", "AI 产品图已生成并保存到作品库。");
        return result;
    }

    private Map<String, Object> generateQueuedMultiView(Map<String, Object> job, MultiViewImageRequest req) throws Exception {
        Long jobId = numberAsLong(job.get("id"));
        Long inputAssetId = numberAsLong(job.get("inputAssetId"));
        Long ownerUserId = numberAsLong(job.get("createdBy"));
        if (jobId == null || inputAssetId == null) throw new IllegalStateException("多视图任务缺少参考图");
        String size = blank(req.size) ? "2K" : req.size.trim();
        if (!Set.of("1K", "2K").contains(size)) throw new IllegalArgumentException("多视图仅支持 1K 或 2K 尺寸");
        List<String> views = req.viewCount != null && req.viewCount == 3
                ? List.of("front", "left", "back")
                : List.of("front", "left", "back", "right");
        Map<String, String> labels = Map.of("front", "正面", "left", "左侧", "back", "背面", "right", "右侧");
        List<Map<String, Object>> images = storedMultiViewImages(jobId);
        Set<String> completedViews = new HashSet<>();
        for (Map<String, Object> image : images) completedViews.add(str(image.get("view")));
        String basePrompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        String referenceImage = readInputImageForSiliconFlow(inputAssetId);
        for (String view : views) {
            if (completedViews.contains(view)) continue;
            String viewPrompt = "PRODUCT TURNAROUND IMAGE EDIT. Use the supplied reference image as the only source of truth. "
                    + "Generate exactly one " + view + " view of the SAME product, not a redesign. "
                    + "Strictly preserve its recognizable identity, silhouette, proportions, colors, material finish, motifs, accessories and all distinctive details. "
                    + "For unseen surfaces, infer only the minimal structure needed to keep the same product consistent. "
                    + "Show the full centered product at the same scale on a clean light-neutral studio background. "
                    + "No collage, no split screen, no extra object, no human, no packaging mockup, no text, no logo, no watermark. "
                    + "Product direction: " + basePrompt;
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", imageEditModel);
            payload.put("prompt", viewPrompt);
            payload.put("image", referenceImage);
            payload.put("negative_prompt", "different product, changed silhouette, changed color palette, collage, split screen, multiple objects, duplicate product, person, hand, text, logo, watermark, cropped object, blurry, distorted product structure");
            payload.put("num_inference_steps", 28);
            payload.put("guidance_scale", 6);
            payload.put("batch_size", 1);
            String remoteUrl = extractImageUrl(createSiliconflowImage(payload));
            String localUrl = saveRemoteImage(remoteUrl, "siliconflow-multiview-" + view + "-", ".png");
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("provider", "siliconflow");
            metadata.put("model", imageEditModel);
            metadata.put("view", view);
            metadata.put("remoteUrl", remoteUrl);
            metadata.put("multiView", true);
            addProductIdentity(metadata, req.productKey, req.productCategory, req.material);
            if (ownerUserId != null) {
                metadata.put("createdByUserId", ownerUserId);
                if (hasPersistedRole(ownerUserId, "user")) metadata.put("consumerWork", true);
            }
            Long assetId = createAsset("AI 多视图参考 · " + labels.get(view), "image", "ai_generated", localUrl, localUrl,
                    viewPrompt, null, null, inputAssetId, "png", "AI生成,多视图,3D参考," + labels.get(view), metadata);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("view", view);
            item.put("label", labels.get(view));
            item.put("assetId", assetId);
            images.add(item);
            completedViews.add(view);
            persistImageJobResult(jobId, multiViewResultPayload(images, true));
        }
        return multiViewResultPayload(images, false);
    }

    private List<Map<String, Object>> storedMultiViewImages(Long jobId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT result_payload_json resultPayloadJson FROM ai_generation_job WHERE id=?", jobId);
        if (rows.isEmpty()) return new ArrayList<>();
        Object rawImages = jobJsonMap(rows.get(0).get("resultPayloadJson")).get("images");
        List<Map<String, Object>> images = new ArrayList<>();
        if (!(rawImages instanceof List<?> source)) return images;
        for (Object raw : source) {
            if (!(raw instanceof Map<?, ?> row)) continue;
            Map<String, Object> image = new LinkedHashMap<>();
            row.forEach((key, value) -> image.put(String.valueOf(key), value));
            if (numberAsLong(image.get("assetId")) != null && !blank(str(image.get("view")))) images.add(image);
        }
        return images;
    }

    private Map<String, Object> multiViewResultPayload(List<Map<String, Object>> images, boolean partial) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("images", images);
        result.put("provider", "siliconflow");
        result.put("model", imageEditModel);
        result.put("partial", partial);
        result.put("message", partial ? "多视图正在生成，已保存完成的视角。" : "AI 图改图已生成 " + images.size() + " 个一致视角，可一键带入 Tripo 多视图建模");
        return result;
    }

    private void persistImageJobResult(Long jobId, Map<String, Object> result) throws Exception {
        jdbc.update("UPDATE ai_generation_job SET result_payload_json=? WHERE id=?", mapper.writeValueAsString(result), jobId);
    }

    private JsonNode createSiliconflowImage(Map<String, Object> payload) throws Exception {
        requireSiliconflowImageConfiguration();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(siliconflowImagesUrl))
                .timeout(Duration.ofSeconds(150))
                .header("Authorization", "Bearer " + siliconflowApiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String message = "硅基流动图片服务 HTTP " + response.statusCode() + "：" + response.body();
                if (response.statusCode() == 408 || response.statusCode() == 425 || response.statusCode() == 429 || response.statusCode() >= 500) {
                    throw new SiliconflowRetryableException(message);
                }
                throw new IllegalStateException(message);
            }
            return mapper.readTree(response.body());
        } catch (HttpTimeoutException e) {
            throw new SiliconflowRetryableException("硅基流动图片服务请求超时");
        } catch (IOException e) {
            throw new SiliconflowRetryableException("无法连接硅基流动图片服务：" + safeMessage(e));
        }
    }

    private static final class SiliconflowRetryableException extends IllegalStateException {
        private SiliconflowRetryableException(String message) {
            super(message);
        }
    }

    private boolean hasJimengSignatureCredentials() {
        return !blank(jimengAccessKeyId) && !blank(jimengSecretAccessKey)
                && !jimengAccessKeyId.contains("YOUR_") && !jimengSecretAccessKey.contains("YOUR_");
    }

    @PostMapping("/image-to-image")
    public Map<String, Object> imageToImage(@RequestBody GenerateImageRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        assertCompliantPrompt(req.prompt, req.productCategory);
        if (req.inputAssetId == null) throw new IllegalArgumentException("请先选择一张参考图");
        requireAssetAccess(req.inputAssetId);
        if (Boolean.TRUE.equals(req.queue)) return queueSiliconflowImageToImage(req, ownerUserId);
        Map<String, Object> style = style(req.styleId);
        String requestedPrompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        ReferenceImageAnalysis referenceAnalysis = analyzeReferenceImage(req.inputAssetId);
        boolean refinement = Boolean.TRUE.equals(req.refinement);
        String finalPrompt = refinement
                ? buildBalancedRefinementPrompt(requestedPrompt, req.productCategory, req.material, referenceAnalysis.visualBrief, req.refinementNote)
                : buildReferencePreservingPrompt(buildPrompt(requestedPrompt, style, req.scene, req.productType), req.productCategory, req.material, referenceAnalysis.visualBrief);
        String negative = mergeNegative(mergeNegative(req.negativePrompt, (String) style.get("negativePrompt")), refinement
                ? "unrelated subject, unrelated theme, random extra object, duplicate product, unreadable form, low detail, distorted anatomy, broken product structure, text, watermark"
                : "different subject, unrelated object, replacement design, changed silhouette, changed main composition, changed color palette, lost distinctive details, generic product, random decoration, extra main subject");
        String jobNo = no("I2I");
        Long jobId = createJob(jobNo, "image_to_image", "siliconflow", imageEditModel, req.styleId, req.inputAssetId, finalPrompt, negative, "running", null, null);
        storeJobProductIdentity(jobId, req.productKey, req.productCategory, req.material);
        assignJobOwner(jobId, ownerUserId);
        try {
            if (siliconflowApiKey == null || siliconflowApiKey.trim().isEmpty() || siliconflowApiKey.contains("YOUR_")) {
                throw new IllegalStateException("未配置 siliconflow.api.key，请在 shixun/application-local.properties 配置");
            }
            String inputImage = buildInputImageForSiliconFlow(req.inputAssetId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", imageEditModel);
            payload.put("prompt", finalPrompt);
            payload.put("image", inputImage);
            // Refinements use moderate prompt guidance: strong enough to make
            // the requested edit visible, but not so high that the image loses
            // its established subject, theme, and product identity.
            payload.put("num_inference_steps", refinement ? 28 : 20);
            payload.put("guidance_scale", refinement ? 6 : 4);
            if (negative != null && !negative.isBlank()) payload.put("negative_prompt", negative);
            payload.put("batch_size", 1);
            if (req.seed != null) payload.put("seed", req.seed);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(siliconflowImagesUrl))
                    .header("Authorization", "Bearer " + siliconflowApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("SiliconFlow图改图 HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            String remoteUrl = extractImageUrl(root);
            String localUrl = saveRemoteImage(remoteUrl, "ai-i2i-", ".png");
            Long assetId = createAsset(
                    req.title == null || req.title.isBlank() ? "AI图改图作品" : req.title + "-图改图",
                    "image",
                    "ai_generated",
                    localUrl,
                    localUrl,
                    finalPrompt,
                    negative,
                    req.styleId,
                    req.inputAssetId,
                    "png",
                    req.tags == null || req.tags.isBlank() ? "图改图,AI生成,之间味道" : req.tags + ",图改图",
                    withProductIdentity(withAssetOwner(referenceImageMetadata(remoteUrl, req.inputAssetId, referenceAnalysis, refinement), ownerUserId), req.productKey, req.productCategory, req.material)
            );
            jdbc.update("UPDATE ai_generation_job SET status='succeeded', output_asset_id=? WHERE id=?", assetId, jobId);
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("jobId", jobId); result.put("jobNo", jobNo); result.put("assetId", assetId);
            result.put("prompt", finalPrompt); result.put("negativePrompt", negative); result.put("status", "succeeded");
            result.put("referenceAnalysis", referenceAnalysis.visualBrief);
            result.put("referenceAnalysisSource", referenceAnalysis.source);
            addSignedAssetFields(result, assetId, "image");
            return result;
        } catch (Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed', error_message=? WHERE id=?", e.getMessage(), jobId);
            throw e;
        }
    }

    @GetMapping("/jimeng/config")
    public Map<String,Object> jimengConfig() {
        boolean signatureConfigured = !blank(jimengAccessKeyId) && !blank(jimengSecretAccessKey) && !jimengAccessKeyId.contains("YOUR_") && !jimengSecretAccessKey.contains("YOUR_");
        boolean bearerOnly = !signatureConfigured && !blank(jimengApiKey) && !jimengApiKey.contains("YOUR_");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("configured", signatureConfigured);
        result.put("provider", "Volcengine");
        result.put("authMode", signatureConfigured ? "volcengine-signature" : (bearerOnly ? "api-key-not-supported-by-this-endpoint" : "missing"));
        result.put("displayName", "即梦AI-图片生成4.6");
        result.put("model", jimengReqKey);
        result.put("apiVersion", "CVSync2AsyncSubmitTask 2022-08-31");
        result.put("serviceReachable", signatureConfigured);
        result.put("imageSizes", List.of("1K", "2K"));
        result.put("aspectRatios", List.of("1:1", "16:9", "9:16", "4:3", "3:4"));
        result.put("outputFormats", List.of("png", "jpg"));
        result.put("message", signatureConfigured ? "当前首选接入火山引擎即梦AI-图片生成4.6，使用火山公共签名鉴权，生成结果会自动保存到系统资产库。" : (bearerOnly ? "检测到 jimeng.api.key，但该视觉接口需要 AccessKeyId + SecretAccessKey 签名鉴权；请配置 jimeng.access-key-id 和 jimeng.secret-access-key。" : "未配置火山引擎 AccessKeyId / SecretAccessKey。"));
        return result;
    }

    @PostMapping("/jimeng/text-to-image")
    public Map<String,Object> jimengTextToImage(@RequestBody GenerateImageRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        Long consumerUserId = currentConsumerUserIdOrNull();
        assertCompliantPrompt(req.prompt, req.productCategory);
        if(blank(jimengAccessKeyId) || blank(jimengSecretAccessKey)) throw new IllegalStateException("即梦视觉接口需要火山引擎 AccessKeyId + SecretAccessKey 签名鉴权，不支持直接使用 Vx 开头的 API Key。请在 shixun/application-local.properties 配置 jimeng.access-key-id 和 jimeng.secret-access-key");
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写或生成生图提示词");
        String prompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        if(prompt.length() > 2000) prompt = prompt.substring(0, 2000);
        String aspect = Set.of("1:1","16:9","9:16","4:3","3:4").contains(nullToEmpty(req.imagenAspectRatio)) ? req.imagenAspectRatio : "1:1";
        String size = Set.of("1K","2K").contains(nullToEmpty(req.imagenImageSize)) ? req.imagenImageSize : "1K";
        String format = Set.of("png","jpg").contains(nullToEmpty(req.imagenOutputFormat).toLowerCase(Locale.ROOT)) ? req.imagenOutputFormat.toLowerCase(Locale.ROOT) : "png";
        int[] wh = jimengDimensions(aspect, size);
        String finalPrompt = buildJimengPrompt(prompt);
        Long creditTxId = consumerUserId == null ? null : reserveConsumerCredit(consumerUserId,"image2d",consumerCreditCost("image2d"),"C端2D图片生成预扣");
        String jobNo = no("JMG");
        Long jobId = createJob(jobNo, "text_to_image", "jimeng", jimengReqKey, req.styleId, null, prompt, req.negativePrompt, "running", null, size + " " + aspect);
        storeJobProductIdentity(jobId, req.productKey, req.productCategory, req.material);
        assignJobOwner(jobId, ownerUserId);
        linkCreditTransaction(creditTxId,jobId,null);
        try {
            JsonNode submit = submitJimengTask(finalPrompt, wh[0], wh[1], req.seed, format);
            String taskId = firstNonBlank(submit.path("data").path("task_id").asText(""), submit.path("data").path("taskId").asText(""), submit.path("task_id").asText(""), submit.path("taskId").asText(""));
            if(blank(taskId)) {
                String immediate = extractJimengImageUrl(submit);
                if(!blank(immediate)) return finishJimengImage(jobId, jobNo, "", immediate, prompt, finalPrompt, req, aspect, size, format, wh);
                throw new IllegalStateException("即梦提交成功但未返回 task_id：" + submit.toString());
            }
            jdbc.update("UPDATE ai_generation_job SET external_task_id=?,progress=10 WHERE id=?", taskId, jobId);
            JsonNode result = waitJimengTask(taskId);
            String remoteImage = extractJimengImageUrl(result);
            if(blank(remoteImage)) throw new IllegalStateException("即梦任务完成但未返回图片地址：" + result.toString());
            return finishJimengImage(jobId, jobNo, taskId, remoteImage, prompt, finalPrompt, req, aspect, size, format, wh);
        } catch(Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed',error_message=? WHERE id=?", safeMessage(e), jobId);
            refundConsumerCredit(creditTxId,safeMessage(e));
            throw new IllegalStateException("即梦AI-图片生成4.6 失败：" + safeMessage(e), e);
        }
    }

    @GetMapping("/imagen/config")
    public Map<String,Object> imagenConfig() {
        boolean configured = !blank(replicateApiKey) && !replicateApiKey.contains("YOUR_");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("configured", configured);
        result.put("provider", "Replicate");
        result.put("displayName", "Google Imagen 4");
        result.put("model", replicateImagenModel);
        result.put("apiVersion", "v1");
        result.put("serviceReachable", configured);
        result.put("imageSizes", List.of("1K", "2K"));
        result.put("aspectRatios", List.of("1:1", "16:9", "9:16", "4:3", "3:4"));
        result.put("outputFormats", List.of("png", "jpg"));
        result.put("message", "当前接入 Replicate google/imagen-4，生成结果会自动保存到系统资产库。");
        return result;
    }

    @PostMapping("/imagen/text-to-image")
    public Map<String,Object> imagenTextToImage(@RequestBody GenerateImageRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        if(blank(replicateApiKey) || replicateApiKey.contains("YOUR_")) throw new IllegalStateException("未配置 Replicate API Key：请在 shixun/application-local.properties 配置 replicate.api.key");
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写或生成生图提示词");
        String prompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        if(prompt.length() > 2000) prompt = prompt.substring(0, 2000);
        String aspect = Set.of("1:1","16:9","9:16","4:3","3:4").contains(nullToEmpty(req.imagenAspectRatio)) ? req.imagenAspectRatio : "1:1";
        String size = Set.of("1K","2K").contains(nullToEmpty(req.imagenImageSize)) ? req.imagenImageSize : "1K";
        String format = Set.of("png","jpg").contains(nullToEmpty(req.imagenOutputFormat).toLowerCase(Locale.ROOT)) ? req.imagenOutputFormat.toLowerCase(Locale.ROOT) : "png";
        String imagenPrompt = buildImagenPrompt(prompt + "\n" + ProductPromptPolicy.optimizerRules(req.productCategory, req.material));
        String jobNo = no("IMG");
        String imagenNegative = ProductPromptPolicy.negative(req.productCategory, req.material);
        Long jobId = createJob(jobNo, "text_to_image", "replicate", replicateImagenModel, req.styleId, null, prompt, imagenNegative, "running", null, size + " " + aspect);
        assignJobOwner(jobId, ownerUserId);
        try {
            JsonNode prediction = createImagenPrediction(imagenPrompt, aspect, size, format);
            prediction = waitReplicatePrediction(prediction);
            String status = prediction.path("status").asText("");
            if("failed".equals(status) || "canceled".equals(status)) throw new IllegalStateException("Imagen 4 任务失败：" + prediction.path("error").asText(prediction.toString()));
            if(!"succeeded".equals(status)) throw new IllegalStateException("Imagen 4 任务未完成，当前状态：" + status);
            String remoteImage = replicateOutputUrl(prediction);
            if(blank(remoteImage)) throw new IllegalStateException("Imagen 4 任务成功但未返回图片地址：" + prediction.toString());
            String localImage = saveRemoteFile(remoteImage, "imagen4-image-", "." + format, "images");
            Map<String,Object> meta = new LinkedHashMap<>();
            meta.put("provider", "replicate");
            meta.put("model", replicateImagenModel);
            meta.put("predictionId", prediction.path("id").asText(""));
            meta.put("remoteImage", remoteImage);
            meta.put("aspectRatio", aspect);
            meta.put("imageSize", size);
            meta.put("outputFormat", format);
            meta.put("promptForImagen", imagenPrompt);
            meta.put("createdByUserId", ownerUserId);
            if (currentConsumerUserIdOrNull() != null) meta.put("consumerWork", true);
            Long assetId = createAsset("Google Imagen 4 2D创意图", "image", "ai_generated", localImage, localImage, prompt, imagenNegative, req.styleId, null, format, "Google Imagen 4,Replicate,2D创意生图,AI生成", meta);
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,external_task_id=?,status='succeeded',progress=100,error_message=NULL WHERE id=?", assetId, prediction.path("id").asText(""), jobId);
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("jobId", jobId);
            out.put("jobNo", jobNo);
            out.put("provider", "imagen");
            out.put("status", "succeeded");
            out.put("progress", 100);
            out.put("id", assetId);
            out.put("assetId", assetId);
            out.put("assetType", "image");
            out.put("sourceType", "ai_generated");
            out.put("assetStatus", "draft");
            addSignedAssetFields(out, assetId, "image");
            out.put("remoteImage", remoteImage);
            out.put("predictionId", prediction.path("id").asText(""));
            out.put("model", replicateImagenModel);
            out.put("source", "Google Imagen 4 · Replicate");
            out.put("message", "Google Imagen 4 图片已生成，并已回传保存到系统资产库。");
            return out;
        } catch(Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed',error_message=? WHERE id=?", safeMessage(e), jobId);
            throw new IllegalStateException("Google Imagen 4 生成失败：" + safeMessage(e), e);
        }
    }

    @GetMapping("/modao/config")
    public Map<String,Object> modaoConfig() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("provider", "Modao");
        result.put("displayName", "墨刀 AI 设计");
        result.put("configured", !blank(modaoApiKey) && modaoApiKey.startsWith("modao_"));
        result.put("workspaceUrl", modaoDesignUrl);
        result.put("mcpUrl", modaoMcpUrl);
        result.put("mode", "streamable_http_mcp");
        result.put("serviceReachable", !blank(modaoMcpUrl));
        result.put("message", "当前接入墨刀 Streamable HTTP MCP：后端使用 modao-token 调用 generate_image，图片结果自动回传平台资产库。");
        return result;
    }

    @PostMapping("/modao/launch")
    public Map<String,Object> modaoLaunch(@RequestBody GenerateImageRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        if(blank(modaoApiKey) || !modaoApiKey.startsWith("modao_")) throw new IllegalStateException("未配置墨刀令牌 modao.api.key，请在 shixun/application-local.properties 配置");
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写或生成设计提示词");
        String prompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        if(prompt.length() > 2000) prompt = prompt.substring(0, 2000);
        String jobNo = no("MDA");
        String modaoNegative = ProductPromptPolicy.negative(req.productCategory, req.material);
        Long jobId = createJob(jobNo, "text_to_image", "modao", "modao-generate-image", req.styleId, null, prompt, modaoNegative, "running", null, req.imageSize);
        assignJobOwner(jobId, ownerUserId);
        try {
            Map<String,Object> generated = modaoGenerateImage(prompt, "生成1024x1024文创产品视觉图，适合电商主图/产品海报截图。画面必须有清晰主体、商业级构图、丰富质感，不要生成后台界面。");
            String imageUrl = str(generated.get("imageUrl"));
            if(blank(imageUrl)) throw new IllegalStateException("墨刀 MCP 已连接，但 generate_image 未返回可下载图片链接；墨刀当前只返回了任务/预览链接，平台无法直接保存为图片。");
            String key = str(generated.get("taskId"));
            String localImage = saveModaoImage(imageUrl);
            Map<String,Object> meta = new LinkedHashMap<>();
            meta.put("provider", "modao");
            meta.put("key", key);
            meta.put("remoteImage", imageUrl);
            meta.put("previewUrl", generated.get("previewUrl"));
            meta.put("taskUrl", generated.get("taskUrl"));
            meta.put("workspaceUrl", modaoDesignUrl);
            meta.put("mcpUrl", modaoMcpUrl);
            meta.put("tool", "generate_image");
            meta.put("createdByUserId", ownerUserId);
            if (currentConsumerUserIdOrNull() != null) meta.put("consumerWork", true);
            Long assetId = createAsset("墨刀AI 2D设计图", "image", "ai_generated", localImage, localImage, prompt, modaoNegative, req.styleId, null, "png", "墨刀,2D创意生图,AI生成", meta);
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,external_task_id=?,status='succeeded',progress=100,error_message=NULL WHERE id=?", assetId, blank(key)?"modao-generate-image":key, jobId);
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("jobId", jobId);
            out.put("jobNo", jobNo);
            out.put("provider", "modao");
            out.put("status", "succeeded");
            out.put("progress", 100);
            out.put("assetId", assetId);
            addSignedAssetFields(out, assetId, "image");
            out.put("remoteImage", imageUrl);
            out.put("taskUrl", generated.get("taskUrl"));
            out.put("prompt", prompt);
            out.put("source", "墨刀AI设计 · MCP图片生成");
            out.put("message", "墨刀AI设计已生成，并已回传保存到系统资产库。");
            return out;
        } catch(Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed',error_message=? WHERE id=?", safeMessage(e), jobId);
            throw new IllegalStateException("墨刀生成失败：" + safeMessage(e), e);
        }
    }

    @GetMapping("/tripo/config")
    public Map<String,Object> tripoConfig() {
        Map<String,Object> result = new LinkedHashMap<>();
        boolean configured = !blank(tripoApiKey) && !tripoApiKey.contains("YOUR_");
        result.put("configured", configured);
        result.put("provider", "Tripo");
        result.put("apiVersion", "v3");
        result.put("modelVersion", tripoModelVersion);
        result.put("qualityPreset", "fast-preview");
        result.put("geometryQuality", "standard");
        result.put("textureQuality", "standard");
        result.put("maxFaceLimit", 20_000);
        result.put("modelOptions", List.of(
                Map.of("value","P1-20260311","label","P1.0 · P系列低面数旗舰","series","P"),
                Map.of("value","v3.1-20260211","label","H3.1 · 最新高精度","series","H"),
                Map.of("value","v3.0-20250812","label","H3.0 · 稳定版","series","H"),
                Map.of("value","v2.5-20250123","label","H2.5 · 兼容版","series","H")
        ));
        result.put("modes", List.of("image_to_model", "multiview_to_model", "text_to_model", "text_to_image"));
        result.put("imageModels", List.of("seedream_v5", "seedream_v4", "banana", "banana_pro", "banana2", "chat_image_1", "chat_image_1.5", "chat_image_2"));
        if(configured) {
            try {
                JsonNode balanceRoot = mapper.readTree(tripoJson("GET", "/account/balance", null));
                ensureTripoOk(balanceRoot, balanceRoot.toString());
                result.put("serviceReachable", true);
                result.put("balance", balanceRoot.path("data").path("balance").asDouble(0));
                result.put("frozenBalance", balanceRoot.path("data").path("frozen").asDouble(0));
            } catch(Exception e) {
                result.put("serviceReachable", false);
                result.put("connectionError", safeMessage(e));
            }
        }
        return result;
    }

    @PostMapping("/tripo/text-to-image")
    public Map<String,Object> tripoTextToImage(@RequestBody GenerateImageRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        if(blank(tripoApiKey) || tripoApiKey.contains("YOUR_")) throw new IllegalStateException("未配置Tripo API Key");
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写或生成生图提示词");
        if(req.prompt.trim().length()>1024) throw new IllegalArgumentException("Tripo生图提示词不能超过1024个字符");
        String tripoPrompt = enforceMaterialConstraint(req.prompt, req.productCategory, req.material);
        String model=Set.of("seedream_v5","seedream_v4","banana","banana_pro","banana2","chat_image_1","chat_image_1.5","chat_image_2").contains(req.tripoImageModel)?req.tripoImageModel:"seedream_v5";
        Map<String,Object> body=new LinkedHashMap<>(); body.put("prompt",tripoPrompt.length() > 1024 ? tripoPrompt.substring(0, 1024) : tripoPrompt); body.put("model",model);
        if(!blank(req.tripoTemplate)) body.put("template",req.tripoTemplate.trim());
        if(Boolean.TRUE.equals(req.tPose)) body.put("t_pose",true);
        if(Boolean.TRUE.equals(req.sketchToRender)) body.put("sketch_to_render",true);
        String raw=tripoJson("POST","/generation/text-to-image",mapper.writeValueAsString(body)); JsonNode root=mapper.readTree(raw); ensureTripoOk(root,raw);
        String taskId=root.path("data").path("task_id").asText(""); if(blank(taskId))throw new IllegalStateException("Tripo文本生图未返回task_id："+raw);
        String tripoNegative = mergeNegative(req.negativePrompt, ProductPromptPolicy.negative(req.productCategory, req.material));
        String jobNo=no("T2D"); Long jobId=createJob(jobNo,"text_to_image","tripo",model,req.styleId,null,tripoPrompt,tripoNegative,"running",null,req.imageSize);
        assignJobOwner(jobId, ownerUserId);
        jdbc.update("UPDATE ai_generation_job SET external_task_id=?,progress=0 WHERE id=?",taskId,jobId);
        return Map.of("jobId",jobId,"jobNo",jobNo,"taskId",taskId,"status","running","progress",0,"provider","tripo","model",model,"message","Tripo文本生图任务已提交");
    }

    @GetMapping("/tripo/image-tasks/{jobId}")
    public synchronized Map<String,Object> tripoImageTask(@PathVariable Long jobId) throws Exception {
        requireJobAccess(jobId);
        return pollTripoImageTask(jobId);
    }

    private synchronized Map<String,Object> pollTripoImageTask(Long jobId) throws Exception {
        Map<String,Object> job=jdbc.queryForMap("SELECT id,job_no jobNo,external_task_id externalTaskId,output_asset_id outputAssetId,status,progress,error_message errorMessage,prompt,negative_prompt negativePrompt,style_id styleId,model_name modelName,created_by createdBy FROM ai_generation_job WHERE id=? AND provider='tripo'",jobId);
        String taskId=str(job.get("externalTaskId")); if(blank(taskId))throw new IllegalStateException("任务没有Tripo task_id");
        if(job.get("outputAssetId")!=null)return completedTripoImageJob(jobId,job);
        String raw=tripoJson("GET","/tasks/"+URLEncoder.encode(taskId,StandardCharsets.UTF_8),null); JsonNode root=mapper.readTree(raw); ensureTripoOk(root,raw); JsonNode data=root.path("data");
        String remoteStatus=data.path("status").asText("unknown"); int progress=data.path("progress").asInt(0); String localStatus=mapTripoStatus(remoteStatus);
        String error=data.path("error").path("message").asText(data.path("message").asText(""));
        if(!"succeeded".equals(localStatus)) jdbc.update("UPDATE ai_generation_job SET status=?,progress=?,error_message=? WHERE id=?",localStatus,progress,blank(error)?null:error,jobId);
        if("succeeded".equals(localStatus)) {
            JsonNode output=data.path("output"); String imageUrl=firstUrl(output,"generated_image_url","generated_image","image_url","image","images");
            if(blank(imageUrl))throw new IllegalStateException("Tripo生图任务成功但没有返回图片地址："+raw);
            String localImage=saveRemoteFile(imageUrl,"tripo-image-",suffixFromUrl(imageUrl,".png"),"images");
            Long styleId=job.get("styleId") instanceof Number?((Number)job.get("styleId")).longValue():null;
            Map<String,Object> meta=new LinkedHashMap<>();meta.put("provider","tripo");meta.put("taskId",taskId);meta.put("model",job.get("modelName"));meta.put("remoteImage",imageUrl);meta.put("size",output.path("size").asText(""));
            if (job.get("createdBy") instanceof Number) {
                Long ownerUserId = ((Number) job.get("createdBy")).longValue();
                meta.put("createdByUserId", ownerUserId);
                if (hasPersistedRole(ownerUserId, "user")) meta.put("consumerWork", true);
            }
            Long assetId=createAsset("Tripo 2D创意图","image","ai_generated",localImage,localImage,str(job.get("prompt")),str(job.get("negativePrompt")),styleId,null,suffixFromUrl(imageUrl,".png").replace(".",""),"Tripo,2D创意生图,AI生成",meta);
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,status='succeeded',progress=100,error_message=NULL WHERE id=?",assetId,jobId);
            job=jdbc.queryForMap("SELECT id,job_no jobNo,external_task_id externalTaskId,output_asset_id outputAssetId,status,progress,error_message errorMessage,model_name modelName FROM ai_generation_job WHERE id=?",jobId);
            return completedTripoImageJob(jobId,job);
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("jobId",jobId);out.put("jobNo",job.get("jobNo"));out.put("taskId",taskId);out.put("status",localStatus);out.put("remoteStatus",remoteStatus);out.put("progress",progress);out.put("errorMessage",error);out.put("model",job.get("modelName"));return out;
    }

    @PostMapping({"/tripo/generate", "/tripo/image-to-3d"})
    public Map<String,Object> tripoGenerate(@RequestBody Generate3dRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        Long consumerUserId = currentConsumerUserIdOrNull();
        assertCompliantPrompt(req.prompt, req.productCategory);
        if(blank(tripoApiKey) || tripoApiKey.contains("YOUR_"))
            throw new IllegalStateException("未配置 tripo.api.key，请在服务器.env中填写TRIPO_API_KEY后重新部署");

        String mode = blank(req.mode) ? "image_to_model" : req.mode.trim();
        if(!Set.of("image_to_model", "multiview_to_model", "text_to_model").contains(mode))
            throw new IllegalArgumentException("不支持的Tripo生成模式：" + mode);

        boolean consumerRequest = consumerUserId != null;
        String requestedModel = blank(req.modelVersion) ? tripoModelVersion : req.modelVersion.trim();
        // Consumer users may choose the light P1 preview model or the H3.1
        // production model. Older clients keep the server default safely.
        String selectedModel = consumerRequest && !Set.of("P1-20260311", "v3.1-20260211").contains(requestedModel)
                ? tripoModelVersion : requestedModel;
        Set<String> supportedModels=Set.of("P1-20260311","tripo-p1","tripo-v3.1","v3.1-20260211","tripo-v3.0","v3.0-20250812","tripo-v2.5","v2.5-20250123");
        if(!supportedModels.contains(selectedModel))throw new IllegalArgumentException("不支持的Tripo 3D模型："+selectedModel);
        Map<String,Object> taskBody = new LinkedHashMap<>();
        taskBody.put("model", selectedModel);
        Long primaryInputAssetId = req.inputAssetId;
        String finalTextPrompt = null;
        Long creditTxId = consumerRequest ? reserveConsumerCredit(consumerUserId,"text_to_model".equals(mode)?"text_to_3d":"image_to_3d",consumerCreditCost("text_to_model".equals(mode)?"text_to_3d":"image_to_3d"),"C端3D生成预扣") : null;

        try {
            if("text_to_model".equals(mode)) {
                if(blank(req.prompt)) throw new IllegalArgumentException("文生3D模式必须填写模型描述");
                if(req.prompt.trim().length() > 1024) throw new IllegalArgumentException("模型描述不能超过1024个字符");
                if(!blank(req.negativePrompt) && req.negativePrompt.trim().length() > 255) throw new IllegalArgumentException("反向提示词不能超过255个字符");
                String textPrompt = enforce3dCraftConstraint(enforceMaterialConstraint(req.prompt, req.productCategory, req.material));
                if (!blank(req.materialPrompt)) textPrompt = textPrompt + ", material and surface finish: " + req.materialPrompt.trim();
                if(textPrompt.length() > 1024) textPrompt = textPrompt.substring(0, 1024);
                finalTextPrompt = textPrompt;
                taskBody.put("prompt", textPrompt);
                if(!blank(req.negativePrompt)) taskBody.put("negative_prompt", req.negativePrompt.trim());
            } else if("multiview_to_model".equals(mode)) {
                if(req.multiviewAssetIds == null || req.multiviewAssetIds.get("front") == null)
                    throw new IllegalArgumentException("多视图建模必须上传正面图");
                long viewCount = List.of("front", "left", "back", "right").stream().filter(v -> req.multiviewAssetIds.get(v) != null).count();
                if(viewCount < 2) throw new IllegalArgumentException("多视图建模至少需要正面图和另一个视角，共2张图片");
                List<Map<String,String>> inputs = new ArrayList<>();
                for(String view : List.of("front", "left", "back", "right")) {
                    Long assetId = req.multiviewAssetIds.get(view);
                    if(assetId == null) continue;
                    requireAssetAccess(assetId);
                    Path image = resolveAssetImage(assetId);
                    inputs.add(Map.of(view, uploadToTripo(image)));
                    if(primaryInputAssetId == null) primaryInputAssetId = assetId;
                }
                taskBody.put("inputs", inputs);
            } else {
                if(req.inputAssetId == null) throw new IllegalArgumentException("请先上传2D参考图");
                requireAssetAccess(req.inputAssetId);
                Path image = resolveAssetImage(req.inputAssetId);
                taskBody.put("input", uploadToTripo(image));
                req.prompt = null;
                req.negativePrompt = null;
            }

            applyTripoQualityOptions(taskBody, req, mode, selectedModel, consumerRequest);
            String generationPath = "text_to_model".equals(mode) ? "/generation/text-to-model" :
                    "multiview_to_model".equals(mode) ? "/generation/multiview-to-model" : "/generation/image-to-model";
            String taskResponse = tripoJson("POST", generationPath, mapper.writeValueAsString(taskBody));
            JsonNode root = mapper.readTree(taskResponse);
            ensureTripoOk(root, taskResponse);
            String taskId = root.path("data").path("task_id").asText(root.path("data").path("taskId").asText(""));
            if(blank(taskId)) throw new IllegalStateException("Tripo未返回task_id：" + taskResponse);

            String jobNo = no("T3D");
            String materialNote = blank(req.materialLabel) ? "" : "期望材质/表面质感：" + req.materialLabel;
            String storedPrompt = "text_to_model".equals(mode) ? finalTextPrompt : materialNote;
            if ("text_to_model".equals(mode) && !blank(materialNote)) storedPrompt = (storedPrompt == null ? "" : storedPrompt) + "；" + materialNote;
            String storedNegativePrompt = "text_to_model".equals(mode) ? req.negativePrompt : "";
            Long jobId = createJob(jobNo, mode, "tripo", selectedModel, null,
                    primaryInputAssetId, storedPrompt, storedNegativePrompt, "running", null,
                    Boolean.TRUE.equals(req.quad) ? "FBX" : (blank(req.exportFormats) ? "GLB" : req.exportFormats));
            storeJobProductIdentity(jobId, req.productKey, req.productCategory, req.material);
            assignJobOwner(jobId, ownerUserId);
            linkCreditTransaction(creditTxId,jobId,null);
            jdbc.update("UPDATE ai_generation_job SET external_task_id=?,progress=0 WHERE id=?", taskId, jobId);
            Map<String,Object> response = new LinkedHashMap<>();
            response.put("jobId", jobId); response.put("jobNo", jobNo); response.put("taskId", taskId);
            response.put("status", "running"); response.put("progress", 0); response.put("provider", "tripo");
            response.put("modelVersion", selectedModel); response.put("qualityPreset", isPSeriesModel(selectedModel)?"fast-preview":"production");
            if(consumerUserId != null) response.put("creditAccount", creditAccountMap(consumerUserId));
            response.put("message", "Tripo "+selectedModel+"任务已提交");
            return response;
        } catch(Exception e) {
            refundConsumerCredit(creditTxId,safeMessage(e));
            throw e;
        }
    }

    private Path resolveAssetImage(Long assetId) throws IOException {
        requireAssetAccess(assetId);
        Map<String,Object> asset = jdbc.queryForMap("SELECT file_url fileUrl,preview_url previewUrl FROM digital_asset WHERE id=?", assetId);
        Object url = asset.get("fileUrl") == null ? asset.get("previewUrl") : asset.get("fileUrl");
        return resolvePublicAsset(String.valueOf(url));
    }

    private void applyTripoQualityOptions(Map<String,Object> body, Generate3dRequest req, String mode, String model, boolean consumerRequest) {
        boolean pSeries=isPSeriesModel(model);
        boolean legacy25=model.contains("v2.5");
        boolean supportsAdvanced=!pSeries&&!legacy25;
        boolean texture=req.texture==null||req.texture;
        boolean pbr=texture&&(req.pbr==null||req.pbr);
        boolean parts=supportsAdvanced&&Boolean.TRUE.equals(req.generateParts);
        boolean quad=supportsAdvanced&&!parts&&Boolean.TRUE.equals(req.quad);
        boolean smartLowPoly=supportsAdvanced&&!parts&&!quad&&Boolean.TRUE.equals(req.smartLowPoly);

        body.put("texture",texture); body.put("pbr",pbr); body.put("export_uv",req.exportUv==null||req.exportUv);
        if(!legacy25) {
            body.put("auto_size",req.autoSize==null||req.autoSize);
            String textureQuality = blank(req.textureQuality) ? "extreme" : req.textureQuality.trim();
            if(texture)body.put("texture_quality",Set.of("standard","detailed","extreme").contains(textureQuality)?textureQuality:"extreme");
            if(Boolean.TRUE.equals(req.compress))body.put("compress","geometry");
        }
        if(supportsAdvanced) {
            body.put("generate_parts",parts); body.put("quad",quad); body.put("smart_low_poly",smartLowPoly);
            if(!quad&&!smartLowPoly&&!parts)body.put("geometry_quality","standard".equals(req.geometryQuality)?"standard":"detailed");
        }
        if("image_to_model".equals(mode)) body.put("enable_image_autofix",req.imageAutofix==null||req.imageAutofix);
        if("image_to_model".equals(mode)||(pSeries&&"multiview_to_model".equals(mode))) {
            String orientation = blank(req.orientation) ? "align_image" : req.orientation.trim();
            body.put("orientation",Set.of("default","align_image").contains(orientation)?orientation:"align_image");
            String textureAlignment = blank(req.textureAlignment) ? "original_image" : req.textureAlignment.trim();
            if(texture)body.put("texture_alignment","original_image".equals(textureAlignment)?"original_image":"geometry");
        }
        int maxFaces=pSeries?20_000:legacy25?500_000:(quad?150_000:smartLowPoly?20_000:2_000_000);
        int minFaces=pSeries?48:1_000; int requested=req.faceLimit==null?maxFaces:req.faceLimit;
        body.put("face_limit",Math.max(minFaces,Math.min(requested,maxFaces)));
        if("text_to_model".equals(mode)||pSeries){if(req.modelSeed!=null)body.put("model_seed",req.modelSeed);}
        if("text_to_model".equals(mode)&&req.imageSeed!=null)body.put("image_seed",req.imageSeed);
        if(texture&&req.textureSeed!=null)body.put("texture_seed",req.textureSeed);
    }

    @GetMapping("/tripo/tasks/{jobId}")
    public synchronized Map<String,Object> tripoTask(@PathVariable Long jobId) throws Exception {
        requireJobAccess(jobId);
        return pollTripoTask(jobId);
    }

    private synchronized Map<String,Object> pollTripoTask(Long jobId) throws Exception {
        Map<String,Object> job=jdbc.queryForMap("SELECT id,job_no jobNo,external_task_id externalTaskId,input_asset_id inputAssetId,output_asset_id outputAssetId,product_key productKey,product_name productName,product_material productMaterial,status,progress,error_message errorMessage,created_by createdBy,credit_transaction_id creditTransactionId FROM ai_generation_job WHERE id=?",jobId);
        String taskId=str(job.get("externalTaskId")); if(blank(taskId))throw new IllegalStateException("任务没有Tripo task_id");
        if(job.get("outputAssetId")!=null) return completedTripoJob(jobId,job);
        String response=tripoJson("GET","/tasks/"+URLEncoder.encode(taskId,StandardCharsets.UTF_8),null);
        JsonNode root=mapper.readTree(response); ensureTripoOk(root,response); JsonNode data=root.path("data");
        String remoteStatus=data.path("status").asText("unknown"); int progress=data.path("progress").asInt(0);
        String localStatus=mapTripoStatus(remoteStatus); String error=data.path("error").asText(data.path("message").asText(""));
        if(!"succeeded".equals(localStatus)) jdbc.update("UPDATE ai_generation_job SET status=?,progress=?,error_message=? WHERE id=?",localStatus,progress,blank(error)?null:error,jobId);
        if("failed".equals(localStatus)) refundConsumerCredit(job.get("creditTransactionId") instanceof Number?((Number)job.get("creditTransactionId")).longValue():null, blank(error)?"3D生成失败":error);
        if("succeeded".equals(localStatus)) {
            JsonNode output=data.path("output"); String modelUrl=firstUrl(output,"model_url","pbr_model","model","base_model","glb_model","model_urls"); String previewUrl=firstUrl(output,"rendered_image_url","rendered_image","image","preview_image");
            if(blank(modelUrl)) throw new IllegalStateException("Tripo任务成功但没有返回模型地址："+response);
            String localModel=saveRemoteFile(modelUrl,"tripo-model-",suffixFromUrl(modelUrl,".glb"),"models");
            String localPreview=blank(previewUrl)?null:saveRemoteFile(previewUrl,"tripo-preview-",suffixFromUrl(previewUrl,".webp"),"models");
            Long inputId=job.get("inputAssetId") instanceof Number ? ((Number)job.get("inputAssetId")).longValue() : null;
            String modelName=jdbc.queryForObject("SELECT model_name FROM ai_generation_job WHERE id=?",String.class,jobId);
            Map<String,Object> metadata=new LinkedHashMap<>(); metadata.put("provider","tripo"); metadata.put("taskId",taskId); metadata.put("remoteModel",modelUrl); metadata.put("modelVersion",modelName);
            addProductIdentity(metadata, str(job.get("productKey")), str(job.get("productName")), str(job.get("productMaterial")));
            if(job.get("createdBy") instanceof Number){
                Long ownerUserId=((Number)job.get("createdBy")).longValue();
                metadata.put("createdByUserId", ownerUserId);
                if (hasPersistedRole(ownerUserId, "user")) metadata.put("consumerWork",true);
            }
            String productName = str(job.get("productName"));
            Long assetId=createAsset(blank(productName) ? "Tripo "+modelName+" 3D模型" : productName+" · 3D 原型","model","ai_generated",localModel,localPreview,String.valueOf(jdbc.queryForObject("SELECT prompt FROM ai_generation_job WHERE id=?",String.class,jobId)),null,null,inputId,suffixFromUrl(modelUrl,".glb").replace(".",""),"Tripo,3D模型,"+modelName,metadata);
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,status='succeeded',progress=100 WHERE id=?",assetId,jobId);
            completeConsumerCredit(job.get("creditTransactionId") instanceof Number?((Number)job.get("creditTransactionId")).longValue():null,jobId,assetId);
            job=jdbc.queryForMap("SELECT id,job_no jobNo,external_task_id externalTaskId,input_asset_id inputAssetId,output_asset_id outputAssetId,product_key productKey,product_name productName,product_material productMaterial,status,progress,error_message errorMessage,created_by createdBy,credit_transaction_id creditTransactionId FROM ai_generation_job WHERE id=?",jobId);
            return completedTripoJob(jobId,job);
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("jobId",jobId);out.put("jobNo",job.get("jobNo"));out.put("taskId",taskId);out.put("status",localStatus);out.put("remoteStatus",remoteStatus);out.put("progress",progress);out.put("errorMessage",error);return out;
    }

    @PostMapping("/text-to-3d")
    public Map<String, Object> textTo3d(@RequestBody Generate3dRequest req) throws Exception {
        Long ownerUserId = authenticatedUserId();
        if (req.inputAssetId != null) requireAssetAccess(req.inputAssetId);
        String prompt = "3D cultural creative product model, " + nullToEmpty(req.prompt) + ", export-ready mesh, clean topology, product prototype";
        String jobNo = no("T3D");
        Long jobId = createJob(jobNo, "text_to_3d", "siliconflow", chatModel, null, req.inputAssetId, prompt, null, "running", null, req.exportFormats == null ? "OBJ,STL,GLB" : req.exportFormats);
        assignJobOwner(jobId, ownerUserId);
        try {
            String spec = callChat(
                    "你是文创产品3D建模指导专家。硅基流动当前在本系统用于生成3D建模规格书，不直接产出OBJ/STL文件。请输出可交给建模师或后续3D工具的结构化建模方案。",
                    "产品/创意：" + prompt + "\n" +
                    "参考资产ID：" + req.inputAssetId + "\n" +
                    "导出格式：" + (req.exportFormats == null ? "OBJ,STL,GLB" : req.exportFormats) + "\n" +
                    "请包含：造型拆解、尺寸建议、材质、工艺、建模步骤、打印/开模风险。"
            );
            Long assetId = createAsset("AI 3D建模规格书", "prompt", "ai_generated", null, null, spec, null, null, req.inputAssetId, "txt", "3D建模,硅基流动,文创打样",
                    withAssetOwner(Map.of("provider", "siliconflow", "model", chatModel), ownerUserId));
            jdbc.update("UPDATE ai_generation_job SET status='succeeded', output_asset_id=? WHERE id=?", assetId, jobId);
            return Map.of("jobId", jobId, "jobNo", jobNo, "status", "succeeded", "assetId", assetId, "prompt", prompt, "aiDraft", spec, "source", "siliconflow:" + chatModel, "exportFormats", req.exportFormats == null ? "OBJ,STL,GLB" : req.exportFormats, "message", "已通过硅基流动生成3D建模规格书；如需真实OBJ/STL，后续仍需接入专业3D生成/建模工具。 ");
        } catch (Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed', error_message=? WHERE id=?", e.getMessage(), jobId);
            throw e;
        }
    }

    @PostMapping(value = "/assets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadAsset(@RequestParam("file") MultipartFile file,
                                           @RequestParam(required = false) String title,
                                           @RequestParam(required = false) String tags) throws Exception {
        Long ownerUserId = authenticatedUserId();
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的图片");
        String original = file.getOriginalFilename() == null ? "upload.png" : file.getOriginalFilename();
        String lower = original.toLowerCase(Locale.ROOT);
        String ext = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? ".jpg" : lower.endsWith(".webp") ? ".webp" : ".png";
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp"))) {
            throw new IllegalArgumentException("当前仅支持 PNG/JPG/WEBP 图片");
        }
        Path dir = creativeAssetRoot().resolve("uploads").normalize();
        Files.createDirectories(dir);
        String fileName = "ref-" + System.currentTimeMillis() + ext;
        Path target = dir.resolve(fileName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        String url = "/uploads/" + fileName;
        Map<String,Object> meta=new LinkedHashMap<>();
        meta.put("uploadName", original); meta.put("size", file.getSize()); meta.put("contentType", file.getContentType() == null ? "" : file.getContentType());
        meta.put("createdByUserId", ownerUserId);
        if (currentConsumerUserIdOrNull() != null) meta.put("consumerReference",true);
        Long assetId = createAsset(
                title == null || title.isBlank() ? original : title,
                "image",
                "upload",
                url,
                url,
                "用户上传参考图，可用于图生图或3D建模参考。",
                null,
                null,
                null,
                ext.replace(".", ""),
                tags == null || tags.isBlank() ? "参考图,上传" : tags,
                meta
        );
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("assetId", assetId);
        result.put("title", title == null || title.isBlank() ? original : title);
        addSignedAssetFields(result, assetId, "image");
        // `url` is retained as a backwards-compatible alias, but it is now a
        // short-lived signed URL rather than the private /uploads path.
        result.put("url", result.get("imageUrl"));
        return result;
    }

    @PostMapping(value = "/consumer-professional-submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> uploadProfessionalSubmission(@RequestParam("file") MultipartFile file,
                                                           @RequestParam(required=false) String title,
                                                           @RequestParam(required=false) String note,
                                                           @RequestParam(required=false) String purpose,
                                                           @RequestParam(required=false) String museumId,
                                                           @RequestParam(required=false) String museumName) throws Exception {
        Long userId = requireCurrentConsumerUser();
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要提交的 ZIP 作品包");
        if (file.getSize() > 100L * 1024 * 1024) throw new IllegalArgumentException("ZIP 作品包不能超过 100MB");
        String original = nullToEmpty(file.getOriginalFilename()).replaceAll("[\\r\\n]", "").trim();
        if (!original.toLowerCase(Locale.ROOT).endsWith(".zip")) throw new IllegalArgumentException("专业审核仅支持 ZIP 作品包");
        if (!hasZipSignature(file)) throw new IllegalArgumentException("上传文件不是有效的 ZIP 作品包");
        String normalizedPurpose = Set.of("personal", "museum_sale").contains(purpose) ? purpose : "personal";
        if ("museum_sale".equals(normalizedPurpose) && blank(museumId)) throw new IllegalArgumentException("售卖作品包必须先选择合作博物馆");
        Path directory = creativeAssetRoot().resolve("professional-submissions").normalize();
        Files.createDirectories(directory);
        String stored = "professional-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ".zip";
        Files.copy(file.getInputStream(), directory.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
        String submissionNo = no("CPS");
        String safeTitle = blank(title) ? original.replaceFirst("(?i)\\.zip$", "") : title.trim();
        jdbc.update("INSERT INTO consumer_professional_submission (submission_no,user_id,title,original_name,storage_name,file_size,purpose,museum_id,museum_name,note) VALUES (?,?,?,?,?,?,?,?,?,?)",
                submissionNo, userId, safeTitle, original, stored, file.getSize(), normalizedPurpose, nullToEmpty(museumId), nullToEmpty(museumName), nullToEmpty(note));
        return Map.of("success", true, "submissionNo", submissionNo, "status", "review", "message", "专业作品包已提交，审核员可在后台下载审核");
    }

    private boolean hasZipSignature(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4);
            return head.length == 4 && head[0] == 'P' && head[1] == 'K'
                    && ((head[2] == 3 && head[3] == 4) || (head[2] == 5 && head[3] == 6) || (head[2] == 7 && head[3] == 8));
        }
    }

    @GetMapping("/consumer-professional-submissions/my")
    public List<Map<String,Object>> myProfessionalSubmissions() {
        Long userId = requireCurrentConsumerUser();
        return jdbc.queryForList("SELECT id,submission_no submissionNo,title,original_name originalName,file_size fileSize,purpose,museum_name museumName,note,status,review_comment reviewComment,reviewed_by reviewedBy,reviewed_at reviewedAt,created_at createdAt FROM consumer_professional_submission WHERE user_id=? ORDER BY id DESC", userId);
    }

    @GetMapping("/consumer-professional-submissions/review")
    public List<Map<String,Object>> professionalSubmissionsForReview() {
        requireCreativeAdmin();
        return jdbc.queryForList("SELECT s.id,s.submission_no submissionNo,s.title,s.original_name originalName,s.file_size fileSize,s.purpose,s.museum_name museumName,s.note,s.status,s.review_comment reviewComment,s.reviewed_by reviewedBy,s.reviewed_at reviewedAt,s.created_at createdAt,u.username createdByName,s.user_id userId FROM consumer_professional_submission s JOIN user u ON u.id=s.user_id ORDER BY s.id DESC");
    }

    @GetMapping("/consumer-professional-submissions/{id}/download")
    public ResponseEntity<Resource> downloadProfessionalSubmission(@PathVariable Long id) throws Exception {
        Map<String,Object> row = jdbc.queryForMap("SELECT user_id userId,original_name originalName,storage_name storageName FROM consumer_professional_submission WHERE id=?", id);
        JwtService.Claims principal = authenticatedPrincipal();
        Long owner = ((Number) row.get("userId")).longValue();
        if (!isCreativeAdmin(principal) && !owner.equals(principal.userId())) throw new IllegalArgumentException("无权下载该作品包");
        Path file = creativeAssetRoot().resolve("professional-submissions").resolve(String.valueOf(row.get("storageName"))).normalize();
        if (!file.startsWith(creativeAssetRoot().resolve("professional-submissions").normalize()) || !Files.isRegularFile(file)) throw new IOException("作品包文件不存在");
        String filename = URLEncoder.encode(String.valueOf(row.get("originalName")), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentLength(Files.size(file)).body(new FileSystemResource(file));
    }

    @PutMapping("/consumer-professional-submissions/{id}/review")
    public Map<String,Object> reviewProfessionalSubmission(@PathVariable Long id, @RequestBody Map<String,String> body) {
        requireCreativeAdmin();
        String status = body == null ? "" : nullToEmpty(body.get("status")).trim();
        if (!Set.of("review", "approved", "rejected").contains(status)) throw new IllegalArgumentException("审核状态只能是 review / approved / rejected");
        String comment = body == null ? "" : nullToEmpty(body.get("comment"));
        int updated = jdbc.update("UPDATE consumer_professional_submission SET status=?,review_comment=?,reviewed_by=?,reviewed_at=NOW() WHERE id=?", status, comment, authenticatedPrincipal().username(), id);
        if (updated == 0) throw new IllegalArgumentException("专业作品包不存在");
        return Map.of("success", true, "status", status, "message", "专业作品包审核状态已更新");
    }

    @PostMapping(value = "/assets/{id}/material-variants", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> saveMaterialVariant(@PathVariable Long id,
                                                  @RequestParam("file") MultipartFile file,
                                                  @RequestParam(required=false) String materialLabel) throws Exception {
        Long userId = requireCurrentConsumerUser();
        requireAssetAccess(id);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请先导出材质版 GLB 模型");
        if (file.getSize() > 100L * 1024 * 1024) throw new IllegalArgumentException("材质版 GLB 不能超过 100MB");
        String original = nullToEmpty(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!original.endsWith(".glb")) throw new IllegalArgumentException("当前仅支持保存 GLB 材质版模型");
        Map<String,Object> source = jdbc.queryForMap("SELECT id,title,asset_type assetType,preview_url previewUrl,prompt,tags FROM digital_asset WHERE id=?", id);
        if (!"model".equals(String.valueOf(source.get("assetType")))) throw new IllegalArgumentException("仅支持为 3D 模型创建材质版本");
        Path dir = creativeAssetRoot().resolve("generated").normalize();
        Files.createDirectories(dir);
        String fileName = "material-" + id + "-" + System.currentTimeMillis() + ".glb";
        Files.copy(file.getInputStream(), dir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        String label = blank(materialLabel) ? "自定义材质" : materialLabel.trim();
        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("createdByUserId", userId); meta.put("materialVariant", true); meta.put("sourceAssetId", id); meta.put("materialLabel", label); meta.put("editor", "threejs-material-lab");
        Long assetId = createAsset(String.valueOf(source.get("title")) + " · " + label, "model", "material_variant", "/generated/" + fileName,
                source.get("previewUrl") == null ? null : String.valueOf(source.get("previewUrl")), String.valueOf(source.get("prompt")), null, null, id,
                "glb", String.valueOf(source.get("tags")) + ",材质转换," + label, meta);
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("assetId", assetId);
        result.put("title", String.valueOf(source.get("title")) + " · " + label);
        result.put("materialLabel", label);
        result.put("message", "材质版模型已保存到作品库，可继续提交审核");
        addSignedAssetFields(result, assetId, "model");
        return result;
    }

    @GetMapping("/assets/{id}/content")
    public ResponseEntity<byte[]> assetContent(@PathVariable Long id) throws Exception {
        requireAssetAccess(id);
        Map<String,Object> asset=jdbc.queryForMap("SELECT file_url fileUrl,preview_url previewUrl,format FROM digital_asset WHERE id=?",id);
        String url=String.valueOf(asset.get("fileUrl")==null?asset.get("previewUrl"):asset.get("fileUrl"));
        if(url.startsWith("http://")||url.startsWith("https://")) {
            HttpResponse<byte[]> response=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()<200||response.statusCode()>=300) throw new IOException("读取图片失败 HTTP "+response.statusCode());
            String ct=response.headers().firstValue("content-type").orElse("image/png");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(ct)).body(response.body());
        }
        Path file=resolvePublicAssetFile(url,"图片文件不存在：");
        String lower=file.getFileName().toString().toLowerCase(Locale.ROOT);
        MediaType type=lower.endsWith(".jpg")||lower.endsWith(".jpeg")?MediaType.IMAGE_JPEG:lower.endsWith(".webp")?MediaType.parseMediaType("image/webp"):MediaType.IMAGE_PNG;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(Files.readAllBytes(file));
    }

    @GetMapping("/assets/{id}/model-content")
    public ResponseEntity<byte[]> assetModelContent(@PathVariable Long id) throws Exception {
        requireAssetAccess(id);
        Map<String,Object> asset=jdbc.queryForMap("SELECT asset_type assetType,file_url fileUrl,format FROM digital_asset WHERE id=?",id);
        if(!"model".equals(String.valueOf(asset.get("assetType")))) throw new IOException("该资产不是3D模型："+id);
        String url=String.valueOf(asset.get("fileUrl"));
        if(blank(url)) throw new IOException("模型文件地址不存在："+id);
        MediaType glbType=MediaType.parseMediaType("model/gltf-binary");
        if(url.startsWith("http://")||url.startsWith("https://")) {
            HttpResponse<byte[]> response=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()<200||response.statusCode()>=300) throw new IOException("读取模型失败 HTTP "+response.statusCode());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(glbType).body(response.body());
        }
        Path file=resolvePublicAssetFile(url,"模型文件不存在：");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(glbType).body(Files.readAllBytes(file));
    }

    @GetMapping("/assets/{id}/preview-content")
    public ResponseEntity<byte[]> assetPreviewContent(@PathVariable Long id) throws Exception {
        requireAssetAccess(id);
        Map<String,Object> asset=jdbc.queryForMap("SELECT file_url fileUrl,preview_url previewUrl FROM digital_asset WHERE id=?",id);
        String url = !blank(str(asset.get("previewUrl"))) ? str(asset.get("previewUrl")) : str(asset.get("fileUrl"));
        if (blank(url)) throw new IOException("预览图片地址不存在：" + id);
        if(url.startsWith("http://")||url.startsWith("https://")) {
            HttpResponse<byte[]> response=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()<200||response.statusCode()>=300) throw new IOException("读取预览图失败 HTTP "+response.statusCode());
            String ct=response.headers().firstValue("content-type").orElse("image/png");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(ct)).body(response.body());
        }
        Path file=resolvePublicAssetFile(url,"预览图片文件不存在：");
        String lower=file.getFileName().toString().toLowerCase(Locale.ROOT);
        MediaType type=lower.endsWith(".jpg")||lower.endsWith(".jpeg")?MediaType.IMAGE_JPEG:lower.endsWith(".webp")?MediaType.parseMediaType("image/webp"):MediaType.IMAGE_PNG;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(Files.readAllBytes(file));
    }

    /**
     * Browser 3D viewers cannot attach an Authorization header to a model URL.
     * Return a five-minute, asset-bound token instead of exposing the regular
     * login JWT in a query string.  The JWT filter verifies both its scope and
     * its asset id before it reaches the media endpoints above.
     */
    @PostMapping("/assets/{id}/preview-access")
    public Map<String,Object> createPreviewAccess(@PathVariable Long id) {
        requireAssetAccess(id);
        JwtService.Claims principal = authenticatedPrincipal();
        Map<String,Object> asset = jdbc.queryForMap("SELECT asset_type assetType FROM digital_asset WHERE id=?", id);
        String endpoint = "model".equals(String.valueOf(asset.get("assetType"))) ? "model-content" : "content";
        String token = jwtService.issueMediaAccessToken(principal.userId(), principal.username(), principal.role(), id);
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String url = "/api/creative/ai/assets/" + id + "/" + endpoint + "?access_token=" + encodedToken;
        String previewUrl = "/api/creative/ai/assets/" + id + "/preview-content?access_token=" + encodedToken;
        return Map.of("assetId", id, "accessToken", token, "url", url, "previewUrl", previewUrl, "expiresIn", 300, "message", "预览链接将在5分钟后失效");
    }

    /**
     * Gives the C-end material laboratory only the minimum authority it needs:
     * read this one model and save a derived GLB material variant.  The token
     * cannot be used as a normal login token or for another asset.
     */
    @PostMapping("/assets/{id}/material-lab-access")
    public Map<String,Object> createMaterialLabAccess(@PathVariable Long id) {
        Long userId = requireCurrentConsumerUser();
        requireAssetAccess(id);
        Map<String,Object> asset = jdbc.queryForMap("SELECT asset_type assetType FROM digital_asset WHERE id=?", id);
        if (!"model".equals(String.valueOf(asset.get("assetType")))) {
            throw new IllegalArgumentException("仅支持为 3D 模型开启材质实验室");
        }
        JwtService.Claims principal = authenticatedPrincipal();
        String token = jwtService.issueMaterialLabAccessToken(userId, principal.username(), principal.role(), id);
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String modelUrl = "/api/creative/ai/assets/" + id + "/model-content?access_token=" + encodedToken;
        return Map.of(
                "assetId", id,
                "modelUrl", modelUrl,
                "accessToken", token,
                "expiresIn", 300
        );
    }

    @GetMapping("/assets/{id}/download-model")
    public ResponseEntity<Resource> downloadModel(@PathVariable Long id,
                                                @RequestParam(defaultValue="GLB") String format) throws Exception {
        String fmt=normalizeModelFormat(format);
        requireAssetAccess(id);
        Long consumerUserId = currentConsumerUserIdOrNull();
        Long creditTxId=!"GLB".equals(fmt) && consumerUserId != null
                ? reserveConsumerCredit(consumerUserId,"model_convert",consumerCreditCost("model_convert"),"C端3D模型"+fmt+"格式下载/转换预扣") : null;
        try {
            Map<String,Object> asset=resolveDownloadableModelAsset(id,fmt);
            requireAssetAccess(((Number) asset.get("id")).longValue());
            ResponseEntity<Resource> response=modelDownloadResponse(asset,fmt);
            completeConsumerCredit(creditTxId,null,asset.get("id") instanceof Number?((Number)asset.get("id")).longValue():id);
            return response;
        } catch(Exception e) {
            refundConsumerCredit(creditTxId,safeMessage(e));
            throw e;
        }
    }

    @GetMapping("/assets")
    public List<Map<String, Object>> assets(@RequestParam(required = false) String type,
                                            @RequestParam(required = false, defaultValue = "100") int size) {
        String cols="id, asset_no assetNo, title, asset_type assetType, source_type sourceType, file_url fileUrl, preview_url previewUrl, prompt, style_id styleId, parent_asset_id parentAssetId, version_no versionNo, status, format, tags, created_by createdBy, created_at createdAt";
        JwtService.Claims principal = authenticatedPrincipal();
        int limit = Math.max(1, Math.min(size, 500));
        if(!isCreativeAdmin(principal)){
            Long userId = requirePersistedAuthenticatedUser();
            List<Map<String,Object>> rows = type != null && !type.isBlank()
                    ? jdbc.queryForList("SELECT "+cols+" FROM digital_asset WHERE asset_type=? AND created_by=? ORDER BY id DESC LIMIT ?", type, userId, limit)
                    : jdbc.queryForList("SELECT "+cols+" FROM digital_asset WHERE created_by=? ORDER BY id DESC LIMIT ?", userId, limit);
            addSignedAssetUrls(rows);
            return rows;
        }
        List<Map<String,Object>> rows = type != null && !type.isBlank()
                ? jdbc.queryForList("SELECT "+cols+" FROM digital_asset WHERE asset_type=? ORDER BY id DESC LIMIT ?", type, limit)
                : jdbc.queryForList("SELECT "+cols+" FROM digital_asset ORDER BY id DESC LIMIT ?", limit);
        addSignedAssetUrls(rows);
        return rows;
    }

    @GetMapping("/consumer-assets/review")
    public List<Map<String,Object>> consumerAssetsReview(@RequestParam(required=false) Long userId,
                                                         @RequestParam(required=false) String status,
                                                         @RequestParam(required=false,defaultValue="100") int size) {
        requireCreativeAdmin();
        StringBuilder sql=new StringBuilder("SELECT a.id,a.asset_no assetNo,a.title,a.asset_type assetType,a.source_type sourceType,a.file_url fileUrl,a.preview_url previewUrl,a.prompt,a.status,a.format,a.tags,a.created_by createdBy,u.username createdByName,a.created_at createdAt FROM digital_asset a JOIN user u ON a.created_by=u.id WHERE u.role='user' AND a.asset_type IN ('image','model') AND (a.asset_type='model' OR COALESCE(a.source_type,'ai_generated')<>'upload')");
        List<Object> args=new ArrayList<>();
        if(userId!=null){sql.append(" AND a.created_by=?");args.add(userId);}
        if(!blank(status)){sql.append(" AND a.status=?");args.add(status);}
        sql.append(" ORDER BY a.id DESC LIMIT ?");args.add(Math.max(1,Math.min(size,500)));
        List<Map<String,Object>> rows = jdbc.queryForList(sql.toString(),args.toArray());
        addSignedAssetUrls(rows);
        return rows;
    }

    @GetMapping("/consumer-assets/inventory")
    public List<Map<String,Object>> consumerAssetsInventory(@RequestParam(required=false) Long userId,
                                                            @RequestParam(required=false) String type,
                                                            @RequestParam(required=false) String keyword,
                                                            @RequestParam(required=false,defaultValue="200") int size) {
        requireCreativeAdmin();
        StringBuilder sql=new StringBuilder("SELECT a.id,a.asset_no assetNo,a.title,a.asset_type assetType,a.source_type sourceType,a.file_url fileUrl,a.preview_url previewUrl,a.prompt,a.status,a.format,a.tags,a.created_by createdBy,u.username createdByName,a.created_at createdAt,a.updated_at updatedAt FROM digital_asset a JOIN user u ON a.created_by=u.id WHERE u.role='user' AND a.status='approved' AND a.asset_type IN ('image','model') AND (a.asset_type='model' OR COALESCE(a.source_type,'ai_generated')<>'upload')");
        List<Object> args=new ArrayList<>();
        if(userId!=null){sql.append(" AND a.created_by=?");args.add(userId);}
        if(!blank(type) && Set.of("image","model").contains(type)){sql.append(" AND a.asset_type=?");args.add(type);}
        if(!blank(keyword)){sql.append(" AND (a.title LIKE ? OR a.prompt LIKE ? OR a.asset_no LIKE ? OR u.username LIKE ?)");String kw="%"+keyword.trim()+"%";args.add(kw);args.add(kw);args.add(kw);args.add(kw);}
        sql.append(" ORDER BY a.updated_at DESC,a.id DESC LIMIT ?");args.add(Math.max(1,Math.min(size,1000)));
        List<Map<String,Object>> rows = jdbc.queryForList(sql.toString(),args.toArray());
        addSignedAssetUrls(rows);
        return rows;
    }

    @PutMapping("/consumer-assets/{id}/submit-review")
    public Map<String,Object> submitConsumerAssetReview(@PathVariable Long id,
                                                        @RequestBody(required=false) Map<String,String> body) {
        Long userId = requireCurrentConsumerUser();
        String purpose=body==null?"":nullToEmpty(body.get("purpose")).trim();
        if(!Set.of("personal","museum_sale").contains(purpose)) purpose="";
        String note=body==null?"":nullToEmpty(body.get("note"));
        String campaignKey=body==null?"":nullToEmpty(body.get("campaignKey")).trim();
        CampaignDefinition campaign=blank(campaignKey)?null:campaignDefinition(campaignKey);
        String museumSource="";
        Map<String,Object> selectedMuseum=null;
        if("museum_sale".equals(purpose)) {
            String museumId=body==null?"":nullToEmpty(body.get("museumId"));
            selectedMuseum=consumerProductionMuseums().stream().filter(x -> museumId.equals(String.valueOf(x.get("id")))).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("博物馆售卖作品必须标明审批博物馆"));
            museumSource=String.valueOf(selectedMuseum.get("province")) + String.valueOf(selectedMuseum.get("city")) + String.valueOf(selectedMuseum.get("district")) + " · " + String.valueOf(selectedMuseum.get("name"));
        }
        if(campaign!=null) {
            if(!"museum_sale".equals(purpose)) throw new IllegalArgumentException("优先征集作品须按博物馆售卖方向提交审核");
            if(selectedMuseum==null || !campaign.channelCode().equals(String.valueOf(selectedMuseum.get("channelCode")))) {
                throw new IllegalArgumentException("该优先征集任务须选择对应的目标博物馆或景区");
            }
        }
        String auditTag = ";用户提交审核" + (blank(purpose) ? "" : ";用途=" + purpose) + (blank(museumSource) ? "" : ";审批出处=" + museumSource) + (campaign==null ? "" : ";激励任务=" + campaign.key()) + (blank(note) ? "" : "-" + note);
        CampaignDefinition selectedCampaign=campaign;
        Map<String,Object> response=creditTransactions.execute(status -> {
            int n=jdbc.update("UPDATE digital_asset SET status='review', tags=CONCAT(COALESCE(tags,''), ?) WHERE id=? AND created_by=? AND asset_type IN ('image','model') AND (asset_type='model' OR COALESCE(source_type,'ai_generated')<>'upload') AND COALESCE(status,'draft')<>'approved'", auditTag, id, userId);
            if(n==0) throw new IllegalArgumentException("作品不存在、无权提交，或作品已审核通过");
            if(selectedCampaign!=null) createCampaignParticipation(userId, selectedCampaign, id);
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("success",true); out.put("id",id); out.put("status","review");
            if(selectedCampaign!=null) {
                out.put("campaignKey", selectedCampaign.key());
                out.put("rewardAmount", selectedCampaign.rewardAmount());
                out.put("message","作品已提交优先征集审核，通过后积分将自动到账");
            } else {
                out.put("message","作品已提交给审核员");
            }
            return out;
        });
        return response==null?Map.of("success",true,"id",id,"status","review"):response;
    }

    @PutMapping("/consumer-assets/{id}/review")
    public Map<String,Object> reviewConsumerAsset(@PathVariable Long id,
                                                  @RequestBody Map<String,String> body) {
        requireCreativeAdmin();
        String status=body==null?"":nullToEmpty(body.get("status")).trim();
        if(!Set.of("approved","rejected","review").contains(status)) throw new IllegalArgumentException("审核状态只能是 approved / rejected / review");
        String operator = authenticatedPrincipal().username();
        String comment=body==null?"":nullToEmpty(body.get("comment"));
        int n=jdbc.update("UPDATE digital_asset a SET a.status=?, a.tags=CONCAT(COALESCE(a.tags,''), ?) WHERE a.id=? AND EXISTS (SELECT 1 FROM user u WHERE u.id=a.created_by AND u.role='user')",status,";审核:"+status+(blank(comment)?"":"-"+comment),id);
        if(n==0) throw new IllegalArgumentException("作品不存在或不是C端用户作品");
        BigDecimal campaignReward = settleCampaignRewardForReview(id, status, blank(operator) ? "admin" : operator);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("success", true); out.put("id", id); out.put("status", status); out.put("operator", blank(operator) ? "admin" : operator);
        if (campaignReward.compareTo(BigDecimal.ZERO) > 0) {
            out.put("campaignReward", campaignReward);
            out.put("message", "审核已通过，活动积分已自动发放");
        } else {
            out.put("message", "approved".equals(status) ? "审核已通过，作品已进入C端用户端库存" : "审核状态已更新");
        }
        return out;
    }

    @GetMapping("/consumer-production/museums")
    public List<Map<String,Object>> consumerProductionMuseums() {
        List<Map<String,Object>> directory = jdbc.queryForList(
                "SELECT id,channel_code channelCode,name,province,city,district,"
                        + "channel_type channelType,source_type sourceType,"
                        + "cooperation_status cooperationStatus "
                        + "FROM channel_directory "
                        + "WHERE enabled=1 AND channel_type IN ('museum','scenic_spot') "
                        + "ORDER BY province,city,name");
        return directory.stream().map(this::normalizeChannelDirectoryRow).map(this::withChannelRecommendation).toList();
    }

    /** JDBC drivers do not agree on the case of unquoted SQL aliases. */
    private Map<String,Object> normalizeChannelDirectoryRow(Map<String,Object> row) {
        Map<String,Object> item = new LinkedHashMap<>();
        item.put("id", mapValueIgnoreCase(row, "id"));
        item.put("channelCode", mapValueIgnoreCase(row, "channelCode"));
        item.put("name", mapValueIgnoreCase(row, "name"));
        item.put("province", mapValueIgnoreCase(row, "province"));
        item.put("city", mapValueIgnoreCase(row, "city"));
        item.put("district", mapValueIgnoreCase(row, "district"));
        item.put("channelType", mapValueIgnoreCase(row, "channelType"));
        item.put("sourceType", mapValueIgnoreCase(row, "sourceType"));
        item.put("cooperationStatus", mapValueIgnoreCase(row, "cooperationStatus"));
        return item;
    }

    private Object mapValueIgnoreCase(Map<String,Object> row, String key) {
        if (row.containsKey(key)) return row.get(key);
        return row.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private Map<String,Object> withChannelRecommendation(Map<String,Object> channel) {
        Map<String,Object> item = new LinkedHashMap<>(channel);
        String channelCode = str(item.get("channelCode"));
        String channelType = str(item.get("channelType"));
        item.put("scene", "scenic_spot".equals(channelType) ? "景区文创候选渠道" : "博物馆文创候选渠道");
        item.put("cooperationNotice", "目录记录不代表平台已合作；提交后仍需完成版权、授权与运营审核。");
        Map<String,Object> recommendation = new LinkedHashMap<>();
        boolean flagship = Set.of("museum-national", "museum-shanghai", "museum-nanjing", "museum-shaanxi-history", "museum-qinshihuang", "museum-hunan", "museum-sanxingdui", "museum-suzhou").contains(channelCode);
        boolean emerging = Set.of("catalog-ningbo-museum", "catalog-shenzhen-museum", "museum-capital", "catalog-china-art-museum").contains(channelCode);
        if (flagship) {
            recommendation.put("strategy", "流量优先");
            recommendation.put("trafficLevel", "高");
            recommendation.put("competitionLevel", "高");
            recommendation.put("breakoutPotential", "中高");
            recommendation.put("badge", "高客流 · 高竞争");
            recommendation.put("advantages", "曝光机会大、品牌背书强、游客消费场景成熟。");
            recommendation.put("risks", "同类竞争强，通常需要更成熟的设计、供应链和差异化 IP 才容易突围。");
            recommendation.put("advice", "适合已有成熟设计、供应链和差异化 IP 的作品。");
        } else if (emerging) {
            recommendation.put("strategy", "爆款试水");
            recommendation.put("trafficLevel", "中低");
            recommendation.put("competitionLevel", "较低");
            recommendation.put("breakoutPotential", "高");
            recommendation.put("badge", "竞争较低 · 更易试爆款");
            recommendation.put("advantages", "同质竞争较低，地域题材和新颖产品更容易被看见，适合小批量试爆款。");
            recommendation.put("risks", "自然客流相对有限，需要更精准的定价、陈列和线上传播配合。");
            recommendation.put("advice", "适合题材鲜明、价格友好、适合游客即时购买的创意产品。");
        } else {
            recommendation.put("strategy", "平衡增长");
            recommendation.put("trafficLevel", "中高");
            recommendation.put("competitionLevel", "中");
            recommendation.put("breakoutPotential", "中高");
            recommendation.put("badge", "客流与竞争较均衡");
            recommendation.put("advantages", "客流与竞争相对均衡，既适合展示品牌，也适合稳定测试转化。");
            recommendation.put("risks", "需要围绕当地文化符号、礼品属性和价格带做清晰差异化，避免产品定位普通。");
            recommendation.put("advice", "适合兼顾品牌展示和转化的文创产品。");
        }
        recommendation.put("disclaimer", "策略标签为系统测试建议，请以实际授权、客流、渠道规则和市场调研为准。");
        item.put("recommendation", recommendation);
        return item;
    }

    /**
     * 历史项目销量只用于创作方向洞察，不写入用户订单或仓库库存。
     * 返回产品形态的可复制信号，避免用单个大客户的绝对销量冒充普遍爆款概率。
     */
    @GetMapping("/consumer-insights/opportunities")
    public Map<String,Object> consumerInsightOpportunities(@RequestParam(required=false) String museumName) {
        requireCurrentConsumerUser();
        String requestedMuseum = blank(museumName) ? "" : museumName.trim();
        String scope = blank(requestedMuseum) ? "全部历史项目" : requestedMuseum;
        String where = blank(requestedMuseum) ? "" : " WHERE project_name=?";
        List<Object> args = blank(requestedMuseum) ? List.of() : List.of(requestedMuseum);
        String aggregateSql = "SELECT COALESCE(NULLIF(TRIM(product_type),''),'其他') productType, "
                + "COALESCE(NULLIF(TRIM(secondary_type),''),'综合文创') secondaryType, "
                + "COALESCE(SUM(sales_ytd),0) sales, COALESCE(SUM(loss_ytd),0) loss, "
                + "COUNT(*) sampleCount, COUNT(DISTINCT NULLIF(TRIM(project_name),'')) projectCount, "
                + "COUNT(DISTINCT NULLIF(TRIM(product_code),'')) productCount, "
                + "COALESCE(SUM(sales_jan),0) janSales, COALESCE(SUM(sales_feb),0) febSales, "
                + "COALESCE(SUM(sales_mar),0) marSales, COALESCE(SUM(sales_apr),0) aprSales, "
                + "COALESCE(SUM(sales_may),0) maySales, COALESCE(SUM(sales_jun),0) junSales, "
                + "COALESCE(SUM(sales_jul),0) julSales FROM historical_sales_fact"
                + where + " GROUP BY product_type, secondary_type ORDER BY sales DESC LIMIT 12";
        List<Map<String,Object>> groups = jdbc.queryForList(aggregateSql, args.toArray());
        boolean matchedMuseum = !blank(requestedMuseum) && !groups.isEmpty();
        if (!matchedMuseum && !blank(requestedMuseum)) {
            scope = "全部历史项目";
            groups = jdbc.queryForList(aggregateSql.replace(where, ""), new Object[0]);
        }
        long maxSales = groups.stream().mapToLong(row -> asLong(row.get("sales"))).max().orElse(1L);
        List<Map<String,Object>> opportunities = groups.stream().map(row -> buildOpportunity(row, maxSales)).toList();
        String topSql = "SELECT product_name productName, project_name projectName, product_type productType, "
                + "secondary_type secondaryType, sales_ytd sales, loss_ytd loss FROM historical_sales_fact WHERE sales_ytd>0"
                + (matchedMuseum ? " AND project_name=?" : "") + " ORDER BY sales_ytd DESC LIMIT 6";
        List<Map<String,Object>> topProducts = jdbc.queryForList(topSql, matchedMuseum ? new Object[]{requestedMuseum} : new Object[0]);
        String summarySql = "SELECT COALESCE(SUM(sales_ytd),0) sales, COALESCE(SUM(loss_ytd),0) loss, "
                + "COUNT(DISTINCT NULLIF(TRIM(project_name),'')) projects, COUNT(DISTINCT NULLIF(TRIM(product_code),'')) products "
                + "FROM historical_sales_fact" + (matchedMuseum ? " WHERE project_name=?" : "");
        Map<String,Object> summary = jdbc.queryForMap(summarySql, matchedMuseum ? new Object[]{requestedMuseum} : new Object[0]);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("scope", scope);
        out.put("requestedMuseum", requestedMuseum);
        out.put("matchedMuseum", matchedMuseum);
        out.put("period", "2026年1-7月历史项目样本");
        out.put("summary", summary);
        out.put("opportunities", opportunities);
        out.put("topProducts", topProducts);
        out.put("disclaimer", "历史项目销量样本仅用于创作方向参考，不代表销售承诺；实际合作、授权、定价和渠道以审核及协议为准。");
        return out;
    }

    private Map<String,Object> buildOpportunity(Map<String,Object> row, long maxSales) {
        String type = str(row.get("productType"));
        String secondary = str(row.get("secondaryType"));
        long sales = asLong(row.get("sales"));
        long loss = asLong(row.get("loss"));
        long samples = asLong(row.get("sampleCount"));
        long projects = asLong(row.get("projectCount"));
        double lossRate = sales + loss == 0 ? 0 : (double) loss / (sales + loss);
        double recent = asLong(row.get("maySales")) + asLong(row.get("junSales")) + asLong(row.get("julSales"));
        double earlier = asLong(row.get("janSales")) + asLong(row.get("febSales")) + asLong(row.get("marSales")) + asLong(row.get("aprSales"));
        double trend = earlier <= 0 ? (recent > 0 ? 1 : 0) : ((recent / 3.0) / Math.max(1.0, earlier / 4.0)) - 1.0;
        double volumeScore = Math.min(100, 100 * Math.log1p(sales) / Math.log1p(Math.max(1, maxSales)));
        double replicationScore = Math.min(100, projects * 20.0);
        double lowLossScore = Math.max(0, 100 - lossRate * 100);
        double confidenceScore = Math.min(100, samples * 10.0);
        int score = (int)Math.round(volumeScore * .45 + replicationScore * .25 + lowLossScore * .20 + confidenceScore * .10);
        String level = score >= 78 ? "高潜力" : score >= 58 ? "可复制" : "待验证";
        String key = opportunityProductKey(type, secondary);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", key + "-" + secondary);
        out.put("productType", type);
        out.put("secondaryType", secondary);
        out.put("productKey", key);
        out.put("title", opportunityTitle(type, secondary));
        out.put("score", score);
        out.put("level", level);
        out.put("sales", sales);
        out.put("lossRate", Math.round(lossRate * 1000) / 10.0);
        out.put("sampleCount", samples);
        out.put("projectCount", projects);
        out.put("trend", Math.round(trend * 1000) / 10.0);
        out.put("reason", opportunityReason(type, secondary, projects, lossRate));
        out.put("promptSuffix", opportunityPrompt(type, secondary));
        return out;
    }

    private String opportunityProductKey(String type, String secondary) {
        if (secondary.contains("冰淇淋")) return "icecream";
        if (secondary.contains("棒棒糖")) return "candy";
        if (secondary.contains("巧克力")) return "blindbox";
        if (type.contains("冰箱贴")) return "magnet";
        if (type.contains("文具")) return "giftbox";
        return "magnet";
    }

    private String opportunityTitle(String type, String secondary) {
        if (!"综合文创".equals(secondary)) return "文化符号 × " + secondary;
        return "文化符号 × " + type;
    }

    private String opportunityReason(String type, String secondary, long projects, double lossRate) {
        String projectHint = projects > 1 ? "已有 " + projects + " 个项目出现过该方向" : "当前样本较少，建议先小批量验证";
        String lossHint = lossRate < .02 ? "历史耗损较低" : lossRate < .08 ? "耗损处于可控区间" : "耗损偏高，建议先做小批量试销";
        return projectHint + "，" + lossHint + "。适合围绕具体馆藏符号做差异化设计。";
    }

    private String opportunityPrompt(String type, String secondary) {
        if (secondary.contains("冰淇淋")) return "以博物馆或景区核心地标为视觉主角，开发适合游客即时消费和拍照分享的文创冰淇淋，包装与口味形成地域记忆点";
        if (secondary.contains("棒棒糖") || secondary.contains("巧克力")) return "以馆藏文物或城市符号做年轻化图形转译，开发适合随手购买、送礼和社交分享的文创糖果礼品";
        if (type.contains("冰箱贴")) return "把馆藏纹样或地标轮廓压缩成一眼可识别的轻量伴手礼，控制尺寸和成本，适合小批量快速试销";
        return "围绕一个明确的馆藏符号做系列化文创产品，优先选择便携、易陈列、适合游客即时购买的产品形态";
    }

    private long asLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return blank(str(value)) ? 0L : Math.round(Double.parseDouble(str(value))); }
        catch (Exception ignored) { return 0L; }
    }

    @GetMapping("/consumer-production/sample-fees")
    public List<Map<String,Object>> consumerSampleFees() {
        requireCurrentConsumerUser();
        return jdbc.queryForList("SELECT id,product_name productName,fee_yuan feeYuan,source_file sourceFile FROM consumer_sample_fee_catalog WHERE active=1 ORDER BY id");
    }

    @GetMapping("/consumer-production/my")
    public List<Map<String,Object>> myConsumerProductionRequests(@RequestParam(required=false) String type,
                                                                 @RequestParam(required=false,defaultValue="100") int size) {
        Long userId = requireCurrentConsumerUser();
        StringBuilder sql=new StringBuilder("SELECT r.id,r.request_no requestNo,r.user_id userId,u.username,r.asset_id assetId,a.title assetTitle,a.asset_type assetType,a.preview_url previewUrl,a.file_url fileUrl,a.format,r.request_type requestType,r.title,r.quantity,r.self_ship_quantity selfShipQuantity,r.museum_distribution_json museumDistributionJson,r.recipient_name recipientName,r.recipient_phone recipientPhone,r.recipient_address recipientAddress,r.note,r.status,r.review_comment reviewComment,r.reviewed_by reviewedBy,r.reviewed_at reviewedAt,r.sample_product_name sampleProductName,r.sample_fee_yuan sampleFeeYuan,r.sample_payment_status samplePaymentStatus,r.sample_payment_order_no samplePaymentOrderNo,r.sample_paid_at samplePaidAt,r.created_at createdAt,r.updated_at updatedAt FROM consumer_production_request r JOIN user u ON u.id=r.user_id JOIN digital_asset a ON a.id=r.asset_id WHERE r.user_id=?");
        List<Object> args=new ArrayList<>();args.add(userId);
        if(!blank(type)&&Set.of("sample","bulk").contains(type)){sql.append(" AND r.request_type=?");args.add(type);}
        sql.append(" ORDER BY r.id DESC LIMIT ?");args.add(Math.max(1,Math.min(size,300)));
        return enrichProductionRows(jdbc.queryForList(sql.toString(),args.toArray()));
    }

    @PostMapping("/consumer-production/submit")
    public Map<String,Object> submitConsumerProductionRequest(@RequestBody Map<String,Object> body) throws Exception {
        Long userId = requireCurrentConsumerUser();
        Long assetId=body==null||body.get("assetId")==null?null:Long.parseLong(String.valueOf(body.get("assetId")));
        if(assetId==null) throw new IllegalArgumentException("请选择审核通过的3D作品");
        requireAssetAccess(assetId);
        String requestType=body==null||body.get("requestType")==null?"":String.valueOf(body.get("requestType")).trim();
        if(!Set.of("sample","bulk").contains(requestType)) throw new IllegalArgumentException("申请类型只能是打样或批量生产");
        Map<String,Object> asset=jdbc.queryForMap("SELECT id,title,asset_type assetType,status,created_by createdBy FROM digital_asset WHERE id=?",assetId);
        if(!"model".equals(String.valueOf(asset.get("assetType")))) throw new IllegalStateException("第一版仅支持3D模型作品提交打样/生产申请");
        if(!"approved".equals(String.valueOf(asset.get("status")))) throw new IllegalStateException("作品需先通过审核，才能提交打样或生产申请");
        int quantity=parsePositiveInt(body==null?null:body.get("quantity"), "sample".equals(requestType)?1:0);
        if(quantity<=0) throw new IllegalArgumentException("申请数量必须大于0");
        String sampleProductName = body==null || body.get("sampleProductName")==null ? "" : String.valueOf(body.get("sampleProductName")).trim();
        BigDecimal sampleFeeYuan = null;
        if ("sample".equals(requestType)) {
            if (blank(sampleProductName)) throw new IllegalArgumentException("请选择打样产品");
            List<BigDecimal> feeRows = jdbc.query("SELECT fee_yuan FROM consumer_sample_fee_catalog WHERE product_name=? AND active=1", (rs, rowNum) -> rs.getBigDecimal(1), sampleProductName);
            if (feeRows.isEmpty()) throw new IllegalArgumentException("打样产品不存在或已下架，请刷新后重试");
            sampleFeeYuan = feeRows.get(0);
        }
        String purpose=body==null||body.get("purpose")==null?"personal":String.valueOf(body.get("purpose")).trim();
        if(blank(purpose)) purpose="personal";
        if(!Set.of("personal","museum_sale").contains(purpose)) throw new IllegalArgumentException("创作目的只能是个人收藏/送礼或博物馆售卖");
        int selfQty=parseNonNegativeInt(body==null?null:body.get("selfShipQuantity"));
        Object museumObj=body==null?null:body.get("museumDistribution");
        List<Map<String,Object>> museumDistribution=normalizeMuseumDistribution(museumObj);
        if("museum_sale".equals(purpose)) {
            if(museumDistribution.isEmpty()) throw new IllegalArgumentException("博物馆售卖用途必须选择一个博物馆");
            if(museumDistribution.size()!=1) throw new IllegalArgumentException("博物馆售卖用途不支持拆分多个博物馆");
            selfQty=0;
            museumDistribution.get(0).put("quantity",quantity);
        } else {
            selfQty=quantity;
            museumDistribution=new ArrayList<>();
        }
        int museumQty=museumDistribution.stream().mapToInt(m -> parseNonNegativeInt(m.get("quantity"))).sum();
        if(selfQty>0 && museumQty>0) throw new IllegalArgumentException("同一申请不能同时分配给个人和博物馆，请按创作目的单一路径提交");
        if("museum_sale".equals(purpose) && museumQty!=quantity) throw new IllegalArgumentException("博物馆售卖用途必须将全部数量投放到所选博物馆");
        if("personal".equals(purpose) && selfQty!=quantity) throw new IllegalArgumentException("个人收藏/送礼用途必须将全部数量寄送给个人，不支持拆分");
        String title=body==null||body.get("title")==null?"":String.valueOf(body.get("title"));
        if(blank(title)) title=("sample".equals(requestType)?"C端打样申请-":"C端批量生产申请-")+asset.get("title");
        String requestNo=no("sample".equals(requestType)?"CYP":"CPR");
        KeyHolder kh=new GeneratedKeyHolder();
        Long finalUserId=userId; Long finalAssetId=assetId; String finalRequestType=requestType; int finalQuantity=quantity;
        int finalSelfQty=selfQty; String finalTitle=title; String distributionJson=mapper.writeValueAsString(museumDistribution); BigDecimal finalSampleFeeYuan=sampleFeeYuan;
        String recipientName=body.get("recipientName")==null?"":String.valueOf(body.get("recipientName"));
        String recipientPhone=body.get("recipientPhone")==null?"":String.valueOf(body.get("recipientPhone"));
        String recipientAddress=body.get("recipientAddress")==null?"":String.valueOf(body.get("recipientAddress"));
        String note=body.get("note")==null?"":String.valueOf(body.get("note"));
        jdbc.update(con -> {
            PreparedStatement ps=con.prepareStatement("INSERT INTO consumer_production_request (request_no,user_id,asset_id,request_type,title,quantity,self_ship_quantity,museum_distribution_json,recipient_name,recipient_phone,recipient_address,note,status,sample_product_name,sample_fee_yuan,sample_payment_status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'review',?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,requestNo);ps.setLong(2,finalUserId);ps.setLong(3,finalAssetId);ps.setString(4,finalRequestType);ps.setString(5,finalTitle);ps.setInt(6,finalQuantity);ps.setInt(7,finalSelfQty);ps.setString(8,distributionJson);ps.setString(9,recipientName);ps.setString(10,recipientPhone);ps.setString(11,recipientAddress);ps.setString(12,note);ps.setString(13,sampleProductName); if(finalSampleFeeYuan==null) ps.setNull(14, java.sql.Types.DECIMAL); else ps.setBigDecimal(14, finalSampleFeeYuan); ps.setString(15, "not_required");
            return ps;
        },kh);
        Long id=Objects.requireNonNull(kh.getKey()).longValue();
        return Map.of("success",true,"id",id,"requestNo",requestNo,"status","review","message","sample".equals(requestType)?"打样申请已提交，请等待审核":"批量生产申请已提交，请等待审核");
    }

    @GetMapping("/consumer-production/admin/review")
    public List<Map<String,Object>> consumerProductionReview(@RequestParam(required=false) String type,
                                                             @RequestParam(required=false) String status,
                                                             @RequestParam(required=false) Long userId,
                                                             @RequestParam(required=false,defaultValue="200") int size) {
        requireCreativeAdmin();
        StringBuilder sql=new StringBuilder("SELECT r.id,r.request_no requestNo,r.user_id userId,u.username,r.asset_id assetId,a.title assetTitle,a.asset_type assetType,a.preview_url previewUrl,a.file_url fileUrl,a.format,r.request_type requestType,r.title,r.quantity,r.self_ship_quantity selfShipQuantity,r.museum_distribution_json museumDistributionJson,r.recipient_name recipientName,r.recipient_phone recipientPhone,r.recipient_address recipientAddress,r.note,r.status,r.review_comment reviewComment,r.reviewed_by reviewedBy,r.reviewed_at reviewedAt,r.sample_product_name sampleProductName,r.sample_fee_yuan sampleFeeYuan,r.sample_payment_status samplePaymentStatus,r.sample_payment_order_no samplePaymentOrderNo,r.sample_paid_at samplePaidAt,r.created_at createdAt,r.updated_at updatedAt FROM consumer_production_request r JOIN user u ON u.id=r.user_id JOIN digital_asset a ON a.id=r.asset_id WHERE 1=1");
        List<Object> args=new ArrayList<>();
        if(!blank(type)&&Set.of("sample","bulk").contains(type)){sql.append(" AND r.request_type=?");args.add(type);}
        if(!blank(status)&&Set.of("review","approved","rejected").contains(status)){sql.append(" AND r.status=?");args.add(status);}
        if(userId!=null){sql.append(" AND r.user_id=?");args.add(userId);}
        sql.append(" ORDER BY r.id DESC LIMIT ?");args.add(Math.max(1,Math.min(size,500)));
        return enrichProductionRows(jdbc.queryForList(sql.toString(),args.toArray()));
    }

    @PutMapping("/consumer-production/admin/{id}/review")
    public Map<String,Object> reviewConsumerProduction(@PathVariable Long id,
                                                       @RequestBody Map<String,String> body) {
        requireCreativeAdmin();
        String status=body==null?"":nullToEmpty(body.get("status")).trim();
        if(!Set.of("approved","rejected","review").contains(status)) throw new IllegalArgumentException("审核状态只能是 approved / rejected / review");
        String comment=body==null?"":nullToEmpty(body.get("comment"));
        String operator = authenticatedPrincipal().username();
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT request_type,sample_payment_status FROM consumer_production_request WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw new IllegalArgumentException("生产申请不存在");
        Map<String,Object> current = rows.get(0);
        String requestType = String.valueOf(current.get("request_type"));
        String paymentStatus = String.valueOf(current.get("sample_payment_status"));
        if ("sample".equals(requestType) && "paid".equals(paymentStatus) && !"approved".equals(status)) {
            throw new IllegalStateException("打样费已支付，不能驳回或退回该申请");
        }
        if ("sample".equals(requestType) && Set.of("pending", "manual_review").contains(paymentStatus) && !"approved".equals(status)) {
            throw new IllegalStateException("打样费支付流程进行中，请先处理支付订单后再变更审核状态");
        }
        if ("sample".equals(requestType) && "approved".equals(status)) {
            if ("paid".equals(paymentStatus)) status = "approved";
            else if (!Set.of("pending", "manual_review").contains(paymentStatus)) paymentStatus = "unpaid";
        } else if ("sample".equals(requestType) && !"paid".equals(paymentStatus)) {
            paymentStatus = "not_required";
        }
        int n=jdbc.update("UPDATE consumer_production_request SET status=?,sample_payment_status=?,review_comment=?,reviewed_by=?,reviewed_at=? WHERE id=?",status,paymentStatus,comment,blank(operator)?"admin":operator,"review".equals(status)?null:LocalDateTime.now(),id);
        if(n==0) throw new IllegalArgumentException("生产申请不存在");
        boolean paymentRequired = "sample".equals(requestType) && "approved".equals(status) && Set.of("unpaid", "pending", "manual_review").contains(paymentStatus);
        return Map.of("success",true,"id",id,"status",status,"samplePaymentStatus",paymentStatus,"paymentRequired",paymentRequired,"message","approved".equals(status)?(paymentRequired?"生产申请已通过，请通知用户支付打样费":"生产申请已通过"):"rejected".equals(status)?"生产申请已驳回":"已退回待审核");
    }

    @PostMapping("/reviews")
    public Map<String, Object> createReview(@RequestBody ReviewRequest req) throws Exception {
        if (req.assetId == null) throw new IllegalArgumentException("assetId不能为空");
        requireAssetAccess(req.assetId);
        Map<String, Object> asset = jdbc.queryForMap("SELECT id, asset_no assetNo, title, asset_type assetType, file_url fileUrl, prompt, tags, metadata_json metadataJson FROM digital_asset WHERE id=?", req.assetId);
        String reviewNo = no("REV");
        Long reviewId = insertReview(reviewNo, req.assetId);

        List<Map<String, String>> agents = List.of(
                Map.of("key", "senior_designer", "name", "资深设计师", "focus", "视觉构图、品牌调性、文化符号准确性、可延展为文创IP的设计完成度"),
                Map.of("key", "market_analyst", "name", "市场分析师", "focus", "目标人群、卖点清晰度、差异化、上架转化潜力和传播话题性"),
                Map.of("key", "cost_controller", "name", "成本控制专家", "focus", "打样难度、印刷/制造成本、SKU适配性、量产风险和库存压力"),
                Map.of("key", "target_consumer", "name", "目标消费者", "focus", "第一眼吸引力、情绪价值、购买理由、送礼/自用场景和价格接受度")
        );
        List<Map<String, Object>> results = new ArrayList<>();
        int total = 0;
        for (Map<String, String> agent : agents) {
            Map<String, Object> one = reviewByAgent(agent, asset, req.context);
            total += ((Number) one.get("score")).intValue();
            insertAgentReview(reviewId, agent, one);
            results.add(new LinkedHashMap<>(Map.of(
                    "agentKey", agent.get("key"),
                    "agentName", agent.get("name"),
                    "score", one.get("score"),
                    "verdict", one.get("verdict"),
                    "comments", one.get("comments"),
                    "suggestions", one.get("suggestions")
            )));
        }
        BigDecimal avg = BigDecimal.valueOf(total).divide(BigDecimal.valueOf(results.size()), 2, java.math.RoundingMode.HALF_UP);
        String recommendation = avg.intValue() >= 85 ? "go" : avg.intValue() >= 70 ? "adjust" : "reject";
        String summary = buildReviewSummary(avg, recommendation, results);
        jdbc.update("UPDATE design_review SET overall_score=?, summary=?, recommendation=? WHERE id=?", avg, summary, recommendation, reviewId);
        // Do not persist the private database media path in the report JSON.
        // Reports are later returned verbatim by /reviews, so retaining
        // `fileUrl` here would bypass the signed-asset URL layer even though
        // the normal asset list is sanitized.
        Map<String,Object> reportAsset = new LinkedHashMap<>(asset);
        reportAsset.remove("fileUrl");
        reportAsset.remove("previewUrl");
        reportAsset.remove("metadataJson");
        Map<String,Object> fullReport = new LinkedHashMap<>(Map.of("reviewId", reviewId, "reviewNo", reviewNo, "asset", reportAsset, "overallScore", avg, "recommendation", recommendation, "summary", summary, "agents", results, "matrix", buildReviewMatrix(results), "roadmap", buildUpgradeRoadmap(avg, recommendation, results)));
        jdbc.update("INSERT INTO design_review_report (review_id, report_json) VALUES (?, CAST(? AS JSON)) ON DUPLICATE KEY UPDATE report_json=VALUES(report_json)", reviewId, mapper.writeValueAsString(fullReport));
        // The just-created response can still show a preview, but only via a
        // short-lived token bound to this asset.  The persisted report above
        // intentionally remains free of expiring URLs.
        Map<String,Object> responseAsset = new LinkedHashMap<>(reportAsset);
        responseAsset.put("previewUrl", signedMediaUrl(req.assetId, "preview-content", authenticatedPrincipal()));
        fullReport.put("asset", responseAsset);
        return fullReport;
    }

    @Scheduled(fixedDelayString = "${tripo.poll.delay-ms:5000}", initialDelayString = "${tripo.poll.initial-delay-ms:8000}")
    public void autoDownloadTripoModels() {
        if(blank(tripoApiKey) || tripoApiKey.contains("YOUR_")) return;
        List<Long> jobIds=jdbc.queryForList("SELECT id FROM ai_generation_job WHERE provider='tripo' AND status IN ('running','queued','succeeded') AND external_task_id IS NOT NULL AND output_asset_id IS NULL ORDER BY id LIMIT 20",Long.class);
        for(Long jobId:jobIds) {
            try {
                String type=jdbc.queryForObject("SELECT job_type FROM ai_generation_job WHERE id=?",String.class,jobId);
                if("text_to_image".equals(type)) pollTripoImageTask(jobId); else pollTripoTask(jobId);
            } catch(Exception e) { jdbc.update("UPDATE ai_generation_job SET error_message=? WHERE id=?", "后台轮询："+safeMessage(e), jobId); }
        }
    }

    @GetMapping("/reviews")
    public List<Map<String, Object>> reviews(@RequestParam(required = false) Long assetId) {
        String sql = "SELECT r.id, r.review_no reviewNo, r.asset_id assetId, a.title assetTitle, a.preview_url previewUrl, r.overall_score overallScore, r.summary, r.recommendation, r.created_at createdAt FROM design_review r JOIN digital_asset a ON r.asset_id=a.id";
        JwtService.Claims principal = authenticatedPrincipal();
        List<Map<String, Object>> list;
        if (isCreativeAdmin(principal)) {
            if (assetId != null) list = jdbc.queryForList(sql + " WHERE r.asset_id=? ORDER BY r.id DESC", assetId);
            else list = jdbc.queryForList(sql + " ORDER BY r.id DESC LIMIT 50");
        } else {
            Long userId = requirePersistedAuthenticatedUser();
            if (assetId != null) {
                requireAssetAccess(assetId);
                list = jdbc.queryForList(sql + " WHERE r.asset_id=? AND a.created_by=? ORDER BY r.id DESC", assetId, userId);
            } else {
                list = jdbc.queryForList(sql + " WHERE a.created_by=? ORDER BY r.id DESC LIMIT 50", userId);
            }
        }
        for (Map<String, Object> r : list) {
            if (r.get("assetId") instanceof Number) {
                r.put("previewUrl", signedMediaUrl(((Number) r.get("assetId")).longValue(), "preview-content", principal));
            }
            r.put("agents", jdbc.queryForList("SELECT agent_key agentKey, agent_name agentName, score, verdict, comments, suggestions_json suggestionsJson FROM design_review_agent WHERE review_id=? ORDER BY id", r.get("id")));
            List<String> reports = jdbc.queryForList("SELECT report_json FROM design_review_report WHERE review_id=?", String.class, r.get("id"));
            if(!reports.isEmpty()) {
                try {
                    Map<String,Object> full = mapper.readValue(reports.get(0), Map.class);
                    r.putAll(full);
                    r.put("id", full.getOrDefault("reviewId", r.get("id")));
                    r.put("createdAt", r.get("createdAt"));
                    Object nestedAsset = r.get("asset");
                    if (nestedAsset instanceof Map<?,?> rawAsset && r.get("assetId") instanceof Number) {
                        Map<String,Object> refreshedAsset = new LinkedHashMap<>();
                        rawAsset.forEach((key, value) -> refreshedAsset.put(String.valueOf(key), value));
                        // Reports created by older deployments may still carry
                        // the database's /generated or /uploads path.  Never
                        // replay those private fields in a response; the
                        // short-lived preview URL below is the only media link
                        // a browser should receive.
                        refreshedAsset.remove("fileUrl");
                        refreshedAsset.remove("previewUrl");
                        refreshedAsset.remove("modelUrl");
                        refreshedAsset.remove("signedFileUrl");
                        refreshedAsset.remove("metadataJson");
                        Long reviewedAssetId = ((Number) r.get("assetId")).longValue();
                        String assetEndpoint = "model".equals(String.valueOf(refreshedAsset.get("assetType")))
                                ? "model-content" : "content";
                        refreshedAsset.put("previewUrl", signedMediaUrl(reviewedAssetId, "preview-content", principal));
                        refreshedAsset.put("fileUrl", signedMediaUrl(reviewedAssetId, assetEndpoint, principal));
                        r.put("asset", refreshedAsset);
                    }
                } catch(Exception ignored) {}
            }
        }
        return list;
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> jobs() {
        String cols = "id, job_no jobNo, job_type jobType, provider, model_name modelName, " +
                "input_asset_id inputAssetId, output_asset_id outputAssetId, external_task_id externalTaskId, " +
                "product_key productKey, product_name productName, product_material productMaterial, " +
                "status, progress, attempt_count attemptCount, error_message errorMessage, export_formats exportFormats, " +
                "created_by createdBy, created_at createdAt, started_at startedAt, finished_at finishedAt";
        JwtService.Claims principal = authenticatedPrincipal();
        if (isCreativeAdmin(principal)) {
            return jdbc.queryForList("SELECT " + cols + " FROM ai_generation_job ORDER BY id DESC LIMIT 100");
        }
        Long userId = requirePersistedAuthenticatedUser();
        return jdbc.queryForList("SELECT " + cols + " FROM ai_generation_job WHERE created_by=? ORDER BY id DESC LIMIT 100", userId);
    }

    private String uploadToTripo(Path file) throws Exception {
        String boundary="----AndTaste"+System.nanoTime(); byte[] bytes=Files.readAllBytes(file);
        String head="--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+file.getFileName()+"\"\r\nContent-Type: application/octet-stream\r\n\r\n";
        byte[] tail=("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8); byte[] hb=head.getBytes(StandardCharsets.UTF_8); byte[] body=new byte[hb.length+bytes.length+tail.length];
        System.arraycopy(hb,0,body,0,hb.length);System.arraycopy(bytes,0,body,hb.length,bytes.length);System.arraycopy(tail,0,body,hb.length+bytes.length,tail.length);
        HttpRequest request=HttpRequest.newBuilder().uri(URI.create(tripoBaseUrl+"/files")).timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+tripoApiKey.trim()).header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        try {
            HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300) throw tripoHttpError("上传",response.statusCode(),response.body());
            JsonNode root=mapper.readTree(response.body()); ensureTripoOk(root,response.body());
            String token=root.path("data").path("file_token").asText("");
            if(blank(token))throw new IllegalStateException("Tripo上传未返回file_token："+response.body());
            return token;
        } catch(HttpTimeoutException e) { throw new IllegalStateException("连接Tripo上传接口超时，请检查服务器外网",e); }
          catch(IOException e) { throw new IllegalStateException("无法连接Tripo上传接口，请检查服务器DNS和HTTPS外网："+safeMessage(e),e); }
    }
    private String tripoJson(String method,String path,String body)throws Exception {
        HttpRequest.Builder b=HttpRequest.newBuilder().uri(URI.create(tripoBaseUrl+path)).timeout(Duration.ofSeconds(45)).header("Authorization","Bearer "+tripoApiKey.trim()).header("Content-Type","application/json");
        if("POST".equals(method)) b.POST(HttpRequest.BodyPublishers.ofString(body==null?"{}":body)); else b.GET();
        try {
            HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()<200||r.statusCode()>=300) throw tripoHttpError("请求",r.statusCode(),r.body());
            return r.body();
        } catch(HttpTimeoutException e) { throw new IllegalStateException("连接Tripo接口超时，任务没有提交，请检查服务器外网",e); }
          catch(IOException e) { throw new IllegalStateException("无法连接Tripo接口，任务没有提交："+safeMessage(e),e); }
    }

    private String tripoConvertJson(String method,String path,String body)throws Exception {
        String base=tripoConvertBaseUrl.replaceAll("/$","");
        HttpRequest.Builder b=HttpRequest.newBuilder().uri(URI.create(base+path)).timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+tripoApiKey.trim()).header("Content-Type","application/json");
        if("POST".equals(method)) b.POST(HttpRequest.BodyPublishers.ofString(body==null?"{}":body)); else b.GET();
        try {
            HttpResponse<String> r=http.send(b.build(),HttpResponse.BodyHandlers.ofString());
            if(r.statusCode()<200||r.statusCode()>=300) throw tripoHttpError("模型格式转换",r.statusCode(),r.body());
            return r.body();
        } catch(HttpTimeoutException e) { throw new IllegalStateException("Tripo模型格式转换超时，请稍后重试",e); }
          catch(IOException e) { throw new IllegalStateException("无法连接Tripo模型格式转换接口："+safeMessage(e),e); }
    }

    private String buildImagenPrompt(String prompt) {
        String base = prompt == null ? "" : prompt.trim();
        if(blank(base)) return base;
        try {
            String system = "你是 Google Imagen 4 商业产品图提示词专家。把用户中文需求改写成英文生图 Prompt。只输出英文 Prompt，不要解释、标题或 Markdown。要求：主体清晰、商业产品摄影/海报质感、背景干净、高级审美、适合文创产品打样和电商展示；保留地名、文化元素、材质、颜色和产品类型。";
            String optimized = callChat(system, base).trim();
            if(!blank(optimized)) {
                optimized = optimized.replaceAll("(?is)^```[a-z]*", "").replaceAll("(?is)```$", "").trim();
                return optimized.length() > 1800 ? optimized.substring(0, 1800) : optimized;
            }
        } catch(Exception ignored) {
            // SiliconFlow 不可用时不阻断 Imagen，直接使用用户已确认的提示词。
        }
        return base.length() > 1800 ? base.substring(0, 1800) : base;
    }

    private String buildJimengPrompt(String prompt) {
        String p = nullToEmpty(prompt).trim();
        String finalPrompt = (p.toLowerCase(Locale.ROOT).contains("product") || p.contains("产品") || p.contains("文创"))
                ? p
                : p + "\nCommercial cultural creative product design, official brand quality, clean product photography, detailed material, premium packaging and manufacturable prototype.";
        // 火山即梦 seedream 4.6 当前接口限制 prompt 不超过 800 字符。
        // 管理端和C端统一从后端兜底裁剪，避免前端优化提示词较长时任务提交后查询失败。
        return finalPrompt.length() > 800 ? finalPrompt.substring(0, 800) : finalPrompt;
    }

    // The API boundary enforces the chosen physical material too, so a future
    // client cannot accidentally turn material selection back into display-only UI.
    private String enforceMaterialConstraint(String prompt, String productCategory, String material) {
        return ProductPromptPolicy.enforce(prompt, productCategory, material);
    }

    /**
     * Food selections need a stronger guard than a generic material lock. Without
     * it, image models often interpret a cultural motif as an enamel badge or a
     * metal ornament merely placed in a gift box, which is unsafe for a food SKU.
     */
    private boolean isEdibleFoodProduct(String productCategory, String material) {
        String context = (nullToEmpty(productCategory) + " " + nullToEmpty(material)).toLowerCase(Locale.ROOT);
        return context.contains("food") || context.contains("食品") || context.contains("食用")
                || context.contains("曲奇") || context.contains("饼干") || context.contains("糕点")
                || context.contains("月饼") || context.contains("咖啡") || context.contains("饮品")
                || context.contains("茶") || context.contains("巧克力") || context.contains("糖果");
    }

    private String enforceEdibleFoodConstraint(String prompt, String productCategory, String material) {
        String base = nullToEmpty(prompt).trim();
        if (base.contains("<<EDIBLE_FOOD_LOCK>>")) return base;
        String category = blank(productCategory) ? "edible cultural creative food" : productCategory.trim();
        String ingredient = blank(material) ? "food-grade edible ingredients" : material.trim();
        // Keep room for the immutable food lock under the provider's 800-character prompt limit.
        if (base.length() > 360) base = base.substring(0, 360);
        return base + "\n<<EDIBLE_FOOD_LOCK>>This is a real, clearly edible " + category + ", made from " + ingredient + ". "
                + "Render the cultural motif as a baked cookie or pastry shape, food-safe icing, embossed dough pattern, edible color printing, or chocolate decoration. "
                + "Show convincing baked golden edges, porous biscuit crumb, realistic cookie thickness, slight oven browning, and an edible serving or sealed food-gift-box presentation. "
                + "It must be instantly recognizable as food that can be eaten, not a souvenir. Never render metal, gold plating, enamel, jewelry, badge, keychain, toy, plastic, resin, ceramic, stone, lacquer, or a decorative object placed in a box.<</EDIBLE_FOOD_LOCK>>";
    }

    // The design constraint is kept at the API boundary as well as in the UI. This
    // prevents a direct client call from silently dropping the production-art rules.
    private String enforce3dCraftConstraint(String prompt) {
        String base = nullToEmpty(prompt).trim();
        if (base.contains("<<3D_CRAFT_LOCK>>")) return base;
        String lock = "<<3D_CRAFT_LOCK>>Artwork only: flat color, vector art style, simple shapes, thick outlines, no gradient, sticker design, orthographic front view. Preserve the selected physical material and PBR reflections. Use watertight production geometry with no floating parts.<</3D_CRAFT_LOCK>>";
        int remaining = Math.max(0, 1024 - lock.length() - (blank(base) ? 0 : 1));
        return lock + (blank(base) ? "" : "\n" + base.substring(0, Math.min(base.length(), remaining)));
    }

    private int[] jimengDimensions(String aspect, String size) {
        boolean high = "2K".equals(size);
        return switch (aspect) {
            case "16:9" -> high ? new int[]{2048, 1152} : new int[]{1664, 936};
            case "9:16" -> high ? new int[]{1152, 2048} : new int[]{936, 1664};
            case "4:3" -> high ? new int[]{2048, 1536} : new int[]{1472, 1104};
            case "3:4" -> high ? new int[]{1536, 2048} : new int[]{1104, 1472};
            default -> high ? new int[]{2048, 2048} : new int[]{1328, 1328};
        };
    }

    private JsonNode submitJimengTask(String prompt, int width, int height, Long seed, String format) throws Exception {
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("req_key", jimengReqKey);
        payload.put("prompt", prompt);
        payload.put("width", width);
        payload.put("height", height);
        payload.put("seed", seed == null ? -1 : seed);
        payload.put("return_url", true);
        payload.put("use_pre_llm", true);
        payload.put("use_sr", true);
        payload.put("output_format", "jpg".equals(format) ? "jpeg" : "png");
        String url = jimengBaseUrl.replaceAll("/$", "") + "?Action=CVSync2AsyncSubmitTask&Version=2022-08-31";
        return jimengPost(url, payload, "提交任务");
    }

    private JsonNode waitJimengTask(String taskId) throws Exception {
        long deadline = System.currentTimeMillis() + Math.max(30, jimengPollMaxSeconds) * 1000L;
        JsonNode last = mapper.createObjectNode();
        while(System.currentTimeMillis() < deadline) {
            Thread.sleep(3000);
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("req_key", jimengReqKey);
            payload.put("task_id", taskId);
            payload.put("req_json", "{\"return_url\":true}");
            String url = jimengBaseUrl.replaceAll("/$", "") + "?Action=CVSync2AsyncGetResult&Version=2022-08-31";
            last = jimengPost(url, payload, "查询结果");
            String status = firstNonBlank(last.path("data").path("status").asText(""), last.path("status").asText(""));
            if("done".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status) || "success".equalsIgnoreCase(status)) return last;
            if("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) throw new IllegalStateException("即梦任务失败：" + last.toString());
            if(!blank(extractJimengImageUrl(last))) return last;
        }
        throw new IllegalStateException("即梦任务超时，请稍后重试；最后状态：" + last.toString());
    }

    private JsonNode jimengPost(String url, Map<String,Object> payload, String actionName) throws Exception {
        String body = mapper.writeValueAsString(payload);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(75))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        signVolcengineRequest(builder, URI.create(url), body);
        HttpRequest request = builder.build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200 || response.statusCode()>=300) throw jimengHttpError(response.statusCode(), response.body(), actionName);
            JsonNode root = mapper.readTree(response.body());
            int code = root.path("code").asInt(root.path("ResponseMetadata").path("Error").isMissingNode() ? 10000 : -1);
            if(code != 0 && code != 10000) throw new IllegalStateException("即梦" + actionName + "返回错误：" + response.body());
            JsonNode error = root.path("ResponseMetadata").path("Error");
            if(!error.isMissingNode() && !blank(error.path("Message").asText(""))) throw new IllegalStateException("即梦" + actionName + "返回错误：" + error.path("Message").asText(root.toString()));
            return root;
        } catch(HttpTimeoutException e) { throw new IllegalStateException("连接火山引擎即梦接口超时", e); }
          catch(IOException e) { throw new IllegalStateException("无法连接火山引擎即梦接口：" + safeMessage(e), e); }
    }

    private String extractJimengImageUrl(JsonNode root) {
        String direct = firstUrl(root.path("data"), "image_urls", "image_url", "url", "result_url");
        if(!blank(direct)) return direct;
        direct = firstUrl(root, "image_urls", "image_url", "url", "result_url");
        if(!blank(direct)) return direct;
        JsonNode arr = root.path("data").path("binary_data_base64");
        if(arr.isArray() && arr.size() > 0 && arr.get(0).isTextual()) return saveBase64JimengImage(arr.get(0).asText());
        if(arr.isTextual() && !blank(arr.asText())) return saveBase64JimengImage(arr.asText());
        return "";
    }

    private String saveBase64JimengImage(String b64) {
        try {
            String clean = b64.contains(",") ? b64.substring(b64.indexOf(',') + 1) : b64;
            byte[] bytes = Base64.getDecoder().decode(clean);
            Path dir = creativeAssetRoot().resolve("generated").resolve("images").normalize();
            Files.createDirectories(dir);
            String file = "jimeng-image-" + System.currentTimeMillis() + ".png";
            Files.write(dir.resolve(file), bytes);
            return "/generated/images/" + file;
        } catch(Exception ignored) { return ""; }
    }

    private Map<String,Object> finishJimengImage(Long jobId, String jobNo, String taskId, String remoteImage, String prompt, String finalPrompt, GenerateImageRequest req, String aspect, String size, String format, int[] wh) throws Exception {
        String localImage = looksLikeUrl(remoteImage) ? saveRemoteFile(remoteImage, "jimeng-image-", "." + format, "images") : remoteImage;
        Map<String,Object> meta = new LinkedHashMap<>();
        meta.put("provider", "jimeng");
        meta.put("model", jimengReqKey);
        meta.put("taskId", taskId);
        meta.put("remoteImage", remoteImage);
        meta.put("aspectRatio", aspect);
        meta.put("imageSize", size);
        meta.put("width", wh[0]);
        meta.put("height", wh[1]);
        meta.put("outputFormat", format);
        meta.put("promptForJimeng", finalPrompt);
        addProductIdentity(meta, req.productKey, req.productCategory, req.material);
        Long ownerUserId = jobOwnerId(jobId);
        if (ownerUserId != null) {
            meta.put("createdByUserId", ownerUserId);
            if (hasPersistedRole(ownerUserId, "user")) meta.put("consumerWork", true);
        }
        Long assetId = createAsset("之间智造AI效果图-" + jobNo, "image", "ai_generated", localImage, localImage, prompt, req.negativePrompt, req.styleId, null, format, "即梦AI,火山引擎,2D创意生图,AI生成", meta);
        jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,external_task_id=?,status='succeeded',progress=100,error_message=NULL WHERE id=?", assetId, taskId, jobId);
        completeConsumerCredit(creditTransactionIdForJob(jobId),jobId,assetId);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("jobId", jobId);
        out.put("jobNo", jobNo);
        out.put("provider", "jimeng");
        out.put("status", "succeeded");
        out.put("progress", 100);
        out.put("id", assetId);
        out.put("assetId", assetId);
        out.put("assetType", "image");
        out.put("sourceType", "ai_generated");
        out.put("assetStatus", "draft");
        addSignedAssetFields(out, assetId, "image");
        // Base64 responses are materialized under the private legacy path by
        // saveBase64JimengImage().  Never send that database/storage path back
        // to the browser; expose the same short-lived asset URL used by the
        // other generated-image responses instead.
        out.put("remoteImage", isPrivateAssetPath(remoteImage)
                ? signedMediaUrl(assetId, "content", authenticatedPrincipal())
                : remoteImage);
        out.put("taskId", taskId);
        out.put("model", jimengReqKey);
        if(ownerUserId != null && hasPersistedRole(ownerUserId, "user")) out.put("creditAccount", creditAccountMap(ownerUserId));
        out.put("source", "火山引擎 · 即梦AI-图片生成4.6");
        out.put("message", "即梦AI 4.6 图片已生成，并已回传保存到系统资产库。用户端可继续提交审核。");
        return out;
    }

    private void signVolcengineRequest(HttpRequest.Builder builder, URI uri, String body) throws Exception {
        String host = uri.getHost();
        String xDate = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
        String shortDate = xDate.substring(0, 8);
        String payloadHash = sha256Hex(body);
        String canonicalQuery = canonicalQuery(uri.getRawQuery());
        String signedHeaders = "content-type;host;x-content-sha256;x-date";
        String canonicalHeaders = "content-type:application/json\n" + "host:" + host + "\n" + "x-content-sha256:" + payloadHash + "\n" + "x-date:" + xDate + "\n";
        String canonicalRequest = "POST\n/\n" + canonicalQuery + "\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadHash;
        String credentialScope = shortDate + "/" + jimengRegion + "/" + jimengService + "/request";
        String stringToSign = "HMAC-SHA256\n" + xDate + "\n" + credentialScope + "\n" + sha256Hex(canonicalRequest);
        byte[] signingKey = hmac(hmac(hmac(hmac(jimengSecretAccessKey.getBytes(StandardCharsets.UTF_8), shortDate), jimengRegion), jimengService), "request");
        String signature = hex(hmac(signingKey, stringToSign));
        String authorization = "HMAC-SHA256 Credential=" + jimengAccessKeyId.trim() + "/" + credentialScope + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
        // Java HttpClient 会自动设置 Host；Host 属于 restricted header，不能手动 header("Host", ...)。
        // 签名 canonicalHeaders 中仍需要包含 host 值，否则火山签名校验会失败。
        builder.header("X-Date", xDate)
                .header("X-Content-Sha256", payloadHash)
                .header("Authorization", authorization);
    }

    private String canonicalQuery(String rawQuery) {
        if(blank(rawQuery)) return "";
        List<String> parts = new ArrayList<>(Arrays.asList(rawQuery.split("&")));
        parts.sort(String::compareTo);
        return String.join("&", parts);
    }

    private String sha256Hex(String s) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
    private byte[] hmac(byte[] key, String data) throws Exception { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256")); return mac.doFinal(data.getBytes(StandardCharsets.UTF_8)); }
    private String hex(byte[] bytes) { StringBuilder sb=new StringBuilder(bytes.length*2); for(byte b:bytes) sb.append(String.format("%02x", b & 0xff)); return sb.toString(); }

    private IllegalStateException jimengHttpError(int status, String raw, String actionName) {
        try {
            JsonNode root = mapper.readTree(raw);
            String detail = root.path("message").asText(root.path("error").asText(root.path("ResponseMetadata").path("Error").path("Message").asText(raw)));
            if(status == 401 || status == 403) return new IllegalStateException("火山引擎即梦 API Key 无效或无权限：" + detail);
            return new IllegalStateException("即梦" + actionName + "接口失败 HTTP " + status + "：" + detail);
        } catch(Exception ignored) { return new IllegalStateException("即梦" + actionName + "接口失败 HTTP " + status + "：" + raw); }
    }

    private JsonNode createImagenPrediction(String prompt, String aspect, String size, String format) throws Exception {
        Map<String,Object> input = new LinkedHashMap<>();
        input.put("prompt", prompt);
        input.put("aspect_ratio", aspect);
        input.put("image_size", size);
        input.put("output_format", format);
        input.put("safety_filter_level", "block_only_high");
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("input", input);
        String pathModel = replicateImagenModel.startsWith("/") ? replicateImagenModel.substring(1) : replicateImagenModel;
        String url = replicateBaseUrl.replaceAll("/$", "") + "/models/" + pathModel + "/predictions";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(75))
                .header("Authorization", "Bearer " + replicateApiKey.trim())
                .header("Content-Type", "application/json")
                .header("Prefer", "wait=60")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200 || response.statusCode()>=300) throw replicateHttpError(response.statusCode(), response.body());
            return mapper.readTree(response.body());
        } catch(HttpTimeoutException e) { throw new IllegalStateException("连接 Replicate Imagen 4 接口超时", e); }
          catch(IOException e) { throw new IllegalStateException("无法连接 Replicate Imagen 4 接口：" + safeMessage(e), e); }
    }

    private JsonNode waitReplicatePrediction(JsonNode prediction) throws Exception {
        String status = prediction.path("status").asText("");
        if("succeeded".equals(status) || "failed".equals(status) || "canceled".equals(status)) return prediction;
        String getUrl = prediction.path("urls").path("get").asText("");
        String id = prediction.path("id").asText("");
        if(blank(getUrl) && !blank(id)) getUrl = replicateBaseUrl.replaceAll("/$", "") + "/predictions/" + URLEncoder.encode(id, StandardCharsets.UTF_8);
        if(blank(getUrl)) return prediction;
        JsonNode current = prediction;
        for(int i=0;i<24;i++) {
            Thread.sleep(3000);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + replicateApiKey.trim())
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200 || response.statusCode()>=300) throw replicateHttpError(response.statusCode(), response.body());
            current = mapper.readTree(response.body());
            status = current.path("status").asText("");
            if("succeeded".equals(status) || "failed".equals(status) || "canceled".equals(status)) return current;
        }
        return current;
    }

    private String replicateOutputUrl(JsonNode prediction) {
        JsonNode output = prediction.path("output");
        if(output.isTextual()) return output.asText("");
        if(output.isArray() && output.size() > 0) {
            for(JsonNode item : output) {
                if(item.isTextual() && looksLikeUrl(item.asText())) return item.asText();
                String nested = findPreferredImageUrl(item);
                if(!blank(nested)) return nested;
            }
        }
        if(output.isObject()) return findPreferredImageUrl(output);
        return "";
    }

    private IllegalStateException replicateHttpError(int status, String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String detail = root.path("detail").asText(root.path("error").asText(root.path("message").asText(raw)));
            if(status == 401 || status == 403) return new IllegalStateException("Replicate API Key 无效或无权限：" + detail);
            if(status == 402) return new IllegalStateException("Replicate 账户余额不足或未开通计费：" + detail);
            return new IllegalStateException("Replicate Imagen 4 接口失败 HTTP " + status + "：" + detail);
        } catch(Exception ignored) { return new IllegalStateException("Replicate Imagen 4 接口失败 HTTP " + status + "：" + raw); }
    }

    private Map<String,Object> modaoGenerateImage(String prompt, String reference) throws Exception {
        Map<String,Object> args = new LinkedHashMap<>();
        args.put("user_input", prompt);
        args.put("query", prompt);
        args.put("client", "smart_pig");
        if(!blank(reference)) args.put("reference", reference);
        JsonNode result = modaoCallTool("generate_image", args);
        ensureModaoToolSuccess(result, "generate_image");
        JsonNode structured = result.path("structuredContent");
        String foundImage = findPreferredImageUrl(structured);
        String imageUrl = isLikelyImageUrl(foundImage) ? foundImage : "";
        String previewUrl = firstExistingUrl(structured, "preview_url", "previewUrl", "preview", "share_url", "shareUrl", "url");
        String taskUrl = firstExistingUrl(structured, "task_url", "taskUrl", "task_link", "link", "workspace_url");
        if(blank(taskUrl) && !blank(foundImage) && !isLikelyImageUrl(foundImage)) taskUrl = foundImage;
        String taskId = firstText(structured, "task_id", "taskId", "id", "key");
        if(blank(imageUrl)) {
            JsonNode content = result.path("content");
            if(content.isArray()) {
                for(JsonNode item : content) {
                    String text = item.path("text").asText("");
                    if(blank(text)) continue;
                    try {
                        JsonNode parsed = mapper.readTree(text);
                        String nestedImage = findPreferredImageUrl(parsed);
                        if(blank(imageUrl) && isLikelyImageUrl(nestedImage)) imageUrl = nestedImage;
                        if(blank(previewUrl)) previewUrl = firstExistingUrl(parsed, "preview_url", "previewUrl", "preview", "share_url", "shareUrl", "url");
                        if(blank(taskUrl)) taskUrl = firstExistingUrl(parsed, "task_url", "taskUrl", "task_link", "link", "workspace_url");
                        if(blank(taskUrl) && !blank(nestedImage) && !isLikelyImageUrl(nestedImage)) taskUrl = nestedImage;
                        if(blank(taskId)) taskId = firstText(parsed, "task_id", "taskId", "id", "key");
                    } catch(Exception ignored) {
                        String textImage = findImageUrlInText(text);
                        if(blank(imageUrl) && isLikelyImageUrl(textImage)) imageUrl = textImage;
                        if(blank(previewUrl)) previewUrl = findAnyUrlInText(text);
                    }
                }
            }
        }
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("imageUrl", imageUrl);
        out.put("previewUrl", previewUrl);
        out.put("taskUrl", taskUrl);
        out.put("taskId", taskId);
        out.put("raw", result.toString());
        return out;
    }

    private void ensureModaoToolSuccess(JsonNode result, String tool) {
        JsonNode structured = result.path("structuredContent");
        boolean failed = structured.has("success") && !structured.path("success").asBoolean(false);
        String error = firstText(structured, "error", "error_type", "code");
        String message = firstText(structured, "message", "status");
        if(failed || !blank(error)) {
            String detail = !blank(error) ? error : message;
            if("insufficient_points".equalsIgnoreCase(detail) || "insufficient_points".equalsIgnoreCase(message))
                throw new IllegalStateException("墨刀 MCP 已连接，但账号积分不足，请到墨刀充值/领取积分后再生成。");
            throw new IllegalStateException("墨刀 MCP 工具 " + tool + " 返回失败：" + (!blank(detail) ? detail : structured.toString()));
        }
    }

    private JsonNode modaoCallTool(String tool, Map<String,Object> args) throws Exception {
        Map<String,Object> params = new LinkedHashMap<>();
        params.put("name", tool);
        params.put("arguments", args);
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", System.currentTimeMillis());
        payload.put("method", "tools/call");
        payload.put("params", params);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(modaoMcpUrl))
                .timeout(Duration.ofSeconds(90))
                .header("modao-token", modaoApiKey.trim())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if(response.statusCode()<200 || response.statusCode()>=300) throw modaoHttpError(response.statusCode(), response.body());
            JsonNode root = parseMcpResponse(response.body());
            if(root.path("error").isObject()) throw new IllegalStateException("墨刀 MCP 调用失败：" + root.path("error").path("message").asText(root.path("error").toString()));
            JsonNode result = root.path("result");
            if(result.path("isError").asBoolean(false)) throw new IllegalStateException("墨刀 MCP 工具失败：" + mcpContentText(result));
            return result;
        } catch(HttpTimeoutException e) { throw new IllegalStateException("连接墨刀接口超时", e); }
          catch(IOException e) { throw new IllegalStateException("无法连接墨刀接口：" + safeMessage(e), e); }
    }

    private JsonNode parseMcpResponse(String raw) throws Exception {
        String trimmed = raw == null ? "" : raw.trim();
        if(trimmed.startsWith("{")) return mapper.readTree(trimmed);
        JsonNode last = null;
        for(String line : trimmed.split("\\R")) {
            String s = line.trim();
            if(!s.startsWith("data:")) continue;
            String data = s.substring(5).trim();
            if(data.isEmpty() || "[DONE]".equals(data)) continue;
            last = mapper.readTree(data);
        }
        if(last == null) throw new IllegalStateException("墨刀 MCP 返回格式无法解析：" + trimmed);
        return last;
    }

    private String mcpContentText(JsonNode result) {
        StringBuilder sb = new StringBuilder();
        JsonNode content = result.path("content");
        if(content.isArray()) for(JsonNode item : content) {
            String text = item.path("text").asText("");
            if(!blank(text)) sb.append(text).append(' ');
        }
        return sb.length() == 0 ? result.toString() : sb.toString().trim();
    }

    private IllegalStateException modaoHttpError(int status, String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String type = root.path("error_type").asText("");
            String message = root.path("message").asText(root.path("error").asText(raw));
            if("INVALID_TOKEN".equalsIgnoreCase(type)) return new IllegalStateException("墨刀连接失败：无效token。请在墨刀头像 → 令牌设置中重新创建 MCP/API 令牌");
            return new IllegalStateException("墨刀接口失败 HTTP " + status + "：" + message);
        } catch(Exception ignored) { return new IllegalStateException("墨刀接口失败 HTTP " + status + "：" + raw); }
    }

    private String extractHtml(String text) {
        if(text == null) return "";
        int start = text.indexOf("<!DOCTYPE html>");
        if(start < 0) start = text.indexOf("<html");
        int end = text.lastIndexOf("</html>");
        if(start >= 0 && end >= start) return text.substring(start, end + 7);
        return "";
    }

    private String findPreferredImageUrl(JsonNode node) {
        String direct = firstExistingUrl(node,
                "image_url", "imageUrl", "image", "img", "url",
                "download_url", "downloadUrl", "file_url", "fileUrl",
                "generated_image_url", "generatedImageUrl", "images");
        if(isLikelyImageUrl(direct)) return direct;
        List<String> urls = new ArrayList<>();
        collectUrls(node, urls);
        for(String u : urls) if(isLikelyImageUrl(u)) return u;
        return urls.isEmpty() ? "" : urls.get(0);
    }

    private String firstExistingUrl(JsonNode node, String... keys) {
        if(node == null || node.isMissingNode() || node.isNull()) return "";
        for(String key : keys) {
            JsonNode v = node.path(key);
            if(v.isTextual() && looksLikeUrl(v.asText())) return v.asText();
            if(v.isArray()) {
                for(JsonNode item : v) {
                    if(item.isTextual() && looksLikeUrl(item.asText())) return item.asText();
                    String nested = findPreferredImageUrl(item);
                    if(!blank(nested)) return nested;
                }
            }
            if(v.isObject()) {
                String nested = findPreferredImageUrl(v);
                if(!blank(nested)) return nested;
            }
        }
        return "";
    }

    private void collectUrls(JsonNode node, List<String> urls) {
        if(node == null || node.isMissingNode() || node.isNull()) return;
        if(node.isTextual()) {
            String text = node.asText();
            if(looksLikeUrl(text)) urls.add(text);
            else {
                String found = findAnyUrlInText(text);
                if(!blank(found)) urls.add(found);
            }
            return;
        }
        if(node.isArray()) for(JsonNode item : node) collectUrls(item, urls);
        if(node.isObject()) node.fields().forEachRemaining(e -> collectUrls(e.getValue(), urls));
    }

    private String findImageUrlInText(String text) {
        if(blank(text)) return "";
        List<String> urls = urlsInText(text);
        for(String u : urls) if(isLikelyImageUrl(u)) return u;
        return urls.isEmpty() ? "" : urls.get(0);
    }

    private String findAnyUrlInText(String text) {
        List<String> urls = urlsInText(text);
        return urls.isEmpty() ? "" : urls.get(0);
    }

    private List<String> urlsInText(String text) {
        List<String> urls = new ArrayList<>();
        if(blank(text)) return urls;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s\\\"'<>，。)）]+").matcher(text);
        while(m.find()) urls.add(m.group());
        return urls;
    }

    private boolean looksLikeUrl(String s) {
        if(blank(s)) return false;
        String v = s.trim();
        return v.startsWith("http://") || v.startsWith("https://") || v.startsWith("data:image/");
    }

    private boolean isLikelyImageUrl(String s) {
        if(blank(s)) return false;
        String v = s.toLowerCase(Locale.ROOT);
        return v.startsWith("data:image/") || v.contains(".png") || v.contains(".jpg") || v.contains(".jpeg") || v.contains(".webp") || v.contains(".gif");
    }

    private String saveModaoImage(String imageUrl) throws Exception {
        if(imageUrl.startsWith("data:image/")) {
            int comma = imageUrl.indexOf(',');
            if(comma < 0) throw new IOException("墨刀返回的data图片格式不正确");
            String meta = imageUrl.substring(0, comma).toLowerCase(Locale.ROOT);
            String suffix = meta.contains("jpeg") ? ".jpg" : meta.contains("webp") ? ".webp" : ".png";
            byte[] bytes = Base64.getDecoder().decode(imageUrl.substring(comma + 1));
            Path dir = creativeAssetRoot().resolve("generated").resolve("images").normalize();
            Files.createDirectories(dir);
            String file = "modao-image-" + System.currentTimeMillis() + suffix;
            Files.write(dir.resolve(file), bytes);
            return "/generated/images/" + file;
        }
        return saveRemoteFile(imageUrl, "modao-image-", suffixFromUrl(imageUrl, ".png"), "images");
    }

    private Path vuePublicDir() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                cwd.resolve("shixun-vue/public").normalize(),
                cwd.resolve("../shixun-vue/public").normalize(),
                cwd.resolve("public").normalize()
        );
        for(Path candidate : candidates) {
            if(Files.exists(candidate)) return candidate;
            Path parent = candidate.getParent();
            if(parent != null && Files.exists(parent)) return candidate;
        }
        return cwd.resolve("../shixun-vue/public").normalize();
    }

    private Path creativeAssetRoot() {
        if (!blank(creativePrivateAssetRoot)) {
            return Path.of(creativePrivateAssetRoot.trim()).toAbsolutePath().normalize();
        }
        // The backend working directory is never copied into the Vite bundle.
        // Keeping this outside shixun-vue/public prevents filename guessing from
        // becoming a public download URL in both development and deployment.
        return Path.of(System.getProperty("user.dir"), "data", "creative-assets").toAbsolutePath().normalize();
    }

    private Path resolvePublicAssetFile(String url, String errorPrefix) throws IOException {
        if(blank(url)) throw new IOException(errorPrefix + url);
        String relative = url.startsWith("/") ? url.substring(1) : url;
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> assetRoots = new ArrayList<>();
        assetRoots.add(creativeAssetRoot());
        // Existing records may still point to the historic public directory.
        // This is read-only compatibility through an authenticated controller;
        // newly written files never go into these locations.
        assetRoots.add(vuePublicDir());
        assetRoots.add(cwd.resolve("shixun-vue/public").normalize());
        assetRoots.add(cwd.resolve("../shixun-vue/public").normalize());
        assetRoots.add(cwd.resolve("public").normalize());
        for (Path assetRoot : assetRoots) {
            Path file = assetRoot.resolve(relative).normalize();
            if (file.startsWith(assetRoot) && Files.exists(file)) return file;
        }
        throw new IOException(errorPrefix + url);
    }

    private String saveGeneratedText(String text, String prefix, String suffix, String folder) throws Exception {
        Path dir = creativeAssetRoot().resolve("generated").resolve(folder).normalize();
        Files.createDirectories(dir);
        String file = prefix + System.currentTimeMillis() + suffix;
        Files.writeString(dir.resolve(file), text, StandardCharsets.UTF_8);
        return "/generated/" + folder + "/" + file;
    }

    private String renderHtmlToPng(String htmlUrl, String prefix) throws Exception {
        Path htmlFile = resolvePublicAssetFile(htmlUrl, "墨刀HTML文件不存在：");
        Path outDir = creativeAssetRoot().resolve("generated/images").normalize();
        Files.createDirectories(outDir);
        Path png = outDir.resolve(prefix + System.currentTimeMillis() + ".png");
        Path chrome = Path.of(modaoChromePath);
        if(!Files.exists(chrome)) throw new IllegalStateException("找不到Chrome，无法把墨刀HTML渲染成图片：" + modaoChromePath);
        List<String> cmd = List.of(
                modaoChromePath,
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--hide-scrollbars",
                "--window-size=1024,1024",
                "--screenshot=" + png.toAbsolutePath(),
                htmlFile.toUri().toString()
        );
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = p.waitFor(45, java.util.concurrent.TimeUnit.SECONDS);
        if(!finished) { p.destroyForcibly(); throw new IllegalStateException("Chrome渲染墨刀图片超时"); }
        if(p.exitValue()!=0 || !Files.exists(png) || Files.size(png)==0) throw new IllegalStateException("Chrome渲染墨刀图片失败：" + output);
        return "/generated/images/" + png.getFileName();
    }

    private IllegalStateException tripoHttpError(String action,int status,String raw) {
        try {
            JsonNode root=mapper.readTree(raw); int code=root.path("code").asInt(-1); String message=root.path("message").asText(root.path("status").asText(raw));
            if(status==403 && code==2010) return new IllegalStateException("Tripo账户积分不足，请先在Tripo工作台充值后再提交（错误码2010）");
            return new IllegalStateException("Tripo"+action+"失败 HTTP "+status+" / "+code+"："+message);
        } catch(Exception ignored) { return new IllegalStateException("Tripo"+action+"失败 HTTP "+status+"："+raw); }
    }
    private void ensureTripoOk(JsonNode root,String raw){int code=root.path("code").asInt(0);if(code!=0)throw new IllegalStateException("Tripo错误 "+code+": "+root.path("message").asText(raw));}
    private boolean isPSeriesModel(String model){return "P1-20260311".equals(model)||"tripo-p1".equals(model);}
    private Path resolvePublicAsset(String url)throws IOException{return resolvePublicAssetFile(url,"参考图文件不存在：");}
    private String imageExtension(Path p){String n=p.getFileName().toString().toLowerCase(Locale.ROOT);return n.endsWith(".jpeg")?"jpg":n.substring(n.lastIndexOf('.')+1);}
    private String mapTripoStatus(String s){s=s.toLowerCase(Locale.ROOT);if(s.contains("success"))return "succeeded";if(s.contains("fail")||s.contains("cancel")||s.contains("banned")||s.contains("expired"))return "failed";return "running";}
    private String firstText(JsonNode n,String...keys){for(String k:keys){String v=n.path(k).asText("");if(!blank(v))return v;}return "";}
    private String firstUrl(JsonNode n,String...keys){for(String k:keys){JsonNode v=n.path(k);if(v.isTextual()&&!blank(v.asText()))return v.asText();if(v.isArray()&&v.size()>0&&v.get(0).isTextual())return v.get(0).asText();}return "";}
    private String safeMessage(Throwable e){String m=e.getMessage();return blank(m)?e.getClass().getSimpleName():m;}
    private String suffixFromUrl(String url,String fallback){try{String p=URI.create(url).getPath();int i=p.lastIndexOf('.');if(i>=0&&p.length()-i<=6)return p.substring(i).toLowerCase(Locale.ROOT);}catch(Exception ignored){}return fallback;}
    private String saveRemoteFile(String url,String prefix,String suffix,String folder)throws Exception{HttpResponse<byte[]> r=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());if(r.statusCode()<200||r.statusCode()>=300)throw new IOException("下载远程文件失败 HTTP "+r.statusCode());Path dir=creativeAssetRoot().resolve("generated").resolve(folder).normalize();Files.createDirectories(dir);String file=prefix+System.currentTimeMillis()+suffix;Files.write(dir.resolve(file),r.body());return "/generated/"+folder+"/"+file;}
    private Map<String,Object> completedTripoImageJob(Long jobId,Map<String,Object> job){
        Map<String,Object>a=jdbc.queryForMap("SELECT id,title,file_url fileUrl,preview_url previewUrl,format,created_at createdAt FROM digital_asset WHERE id=?",job.get("outputAssetId"));
        Map<String,Object>r=new LinkedHashMap<>();
        r.put("jobId",jobId); r.put("jobNo",job.get("jobNo")); r.put("taskId",job.get("externalTaskId"));
        r.put("status","succeeded"); r.put("progress",100); r.put("assetId",a.get("id"));
        r.put("format",a.get("format")); r.put("model",job.get("modelName"));
        r.put("source","Tripo "+str(job.get("modelName")));
        addSignedAssetFields(r, ((Number)a.get("id")).longValue(), "image");
        return r;
    }
    private Map<String,Object> completedTripoJob(Long jobId,Map<String,Object> job){
        Map<String,Object>a=jdbc.queryForMap("SELECT id,title,asset_type assetType,source_type sourceType,status assetStatus,file_url fileUrl,preview_url previewUrl,format,created_at createdAt FROM digital_asset WHERE id=?",job.get("outputAssetId"));
        Map<String,Object>r=new LinkedHashMap<>();
        r.put("jobId",jobId); r.put("jobNo",job.get("jobNo")); r.put("taskId",job.get("externalTaskId"));
        r.put("status","succeeded"); r.put("progress",100); r.put("id",a.get("id")); r.put("assetId",a.get("id"));
        r.put("assetType",a.get("assetType")); r.put("sourceType",a.get("sourceType")); r.put("assetStatus",a.get("assetStatus")); r.put("format",a.get("format"));
        addSignedAssetFields(r, ((Number)a.get("id")).longValue(), String.valueOf(a.get("assetType")));
        if(job.get("createdBy") instanceof Number&&hasPersistedRole(((Number)job.get("createdBy")).longValue(),"user"))r.put("creditAccount",creditAccountMap(((Number)job.get("createdBy")).longValue()));
        return r;
    }
    private boolean blank(String s){return s==null||s.trim().isEmpty();}
    private boolean isPrivateAssetPath(String s) {
        return s != null && (s.startsWith("/generated/") || s.startsWith("/uploads/"));
    }
    private String str(Object o){return o==null?"":String.valueOf(o);}

    private Long insertReview(String reviewNo, Long assetId) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO design_review (review_no, asset_id) VALUES (?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, reviewNo); ps.setLong(2, assetId); return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private void insertAgentReview(Long reviewId, Map<String, String> agent, Map<String, Object> result) throws Exception {
        jdbc.update("INSERT INTO design_review_agent (review_id, agent_key, agent_name, score, verdict, comments, suggestions_json) VALUES (?,?,?,?,?,?,?)",
                reviewId, agent.get("key"), agent.get("name"), result.get("score"), result.get("verdict"), result.get("comments"), mapper.writeValueAsString(result.get("suggestions")));
    }

    private Map<String, Object> reviewByAgent(Map<String, String> agent, Map<String, Object> asset, String context) {
        String instruction = "你是“之间味道”文创设计售卖平台AI评审团成员：" + agent.get("name") + "。你的评审重点：" + agent.get("focus") + "。请评审一个图片类文创产品方案，可结合用户提供的爆款/竞品信息做对标。必须只返回JSON，不要markdown。格式：{\"score\":0-100整数,\"verdict\":\"一句话结论\",\"comments\":\"具体评语\",\"suggestions\":[\"建议1\",\"建议2\",\"建议3\"],\"subScores\":{\"设计表现\":0-100,\"市场潜力\":0-100,\"成本生产\":0-100,\"消费转化\":0-100,\"爆款对标\":0-100},\"risks\":[{\"level\":\"高/中/低\",\"name\":\"风险名\",\"advice\":\"处理建议\"}],\"opportunities\":[\"机会1\",\"机会2\"],\"nextActions\":[\"下一步1\",\"下一步2\"],\"benchmark\":\"与爆款/竞品相比的差距和可借鉴点\"}";
        // The model receives text-only review context.  Never forward the
        // private /generated or /uploads path to a third-party provider.
        String user = "设计资产标题：" + asset.get("title") + "\n资产ID：" + asset.get("id") + "\n资产类型：" + asset.get("assetType") + "\n标签：" + asset.get("tags") + "\n生成/设计Prompt：" + asset.get("prompt") + "\n补充业务背景：" + (context == null ? "用于图片IP文创产品开发，可衍生明信片、装饰画、手机壳、帆布袋等SKU。" : context);
        try {
            String content = callChat(instruction, user);
            return parseAgentJson(content);
        } catch (Exception e) {
            return fallbackReview(agent, asset, e.getMessage());
        }
    }

    private String callChat(String system, String user) throws Exception {
        if (siliconflowApiKey == null || siliconflowApiKey.trim().isEmpty()) throw new IllegalStateException("未配置siliconflow.api.key");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", chatModel);
        payload.put("temperature", 0.35);
        payload.put("max_tokens", 700);
        payload.put("enable_thinking", false);
        payload.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.siliconflow.cn/v1/chat/completions"))
                .header("Authorization", "Bearer " + siliconflowApiKey.trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("SiliconFlow Chat HTTP " + response.statusCode() + ": " + response.body());
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").path(0).path("message").path("content").asText();
    }

    private Map<String, Object> parseAgentJson(String content) throws Exception {
        String c = content.trim();
        int start = c.indexOf('{'); int end = c.lastIndexOf('}');
        if (start >= 0 && end > start) c = c.substring(start, end + 1);
        JsonNode n = mapper.readTree(c);
        int score = Math.max(0, Math.min(100, n.path("score").asInt(75)));
        List<String> suggestions = new ArrayList<>();
        if (n.path("suggestions").isArray()) n.path("suggestions").forEach(x -> suggestions.add(x.asText()));
        return new LinkedHashMap<>(Map.of(
                "score", score,
                "verdict", n.path("verdict").asText("建议进一步优化"),
                "comments", n.path("comments").asText("该方案具备一定文创开发潜力。"),
                "suggestions", suggestions,
                "subScores", jsonMap(n.path("subScores")),
                "risks", jsonList(n.path("risks")),
                "opportunities", stringList(n.path("opportunities")),
                "nextActions", stringList(n.path("nextActions")),
                "benchmark", n.path("benchmark").asText("")
        ));
    }

    private Map<String, Object> fallbackReview(Map<String, String> agent, Map<String, Object> asset, String err) {
        return new LinkedHashMap<>(Map.of(
                "score", 72,
                "verdict", "已完成基础评审，建议人工复核",
                "comments", agent.get("name") + "认为该方案可进入初步讨论；AI评审调用异常：" + err,
                "suggestions", List.of("明确目标SKU与使用场景", "补充视觉主次层级", "进行小样打样与用户反馈"),
                "subScores", Map.of("设计表现",72,"市场潜力",70,"成本生产",74,"消费转化",72,"爆款对标",68),
                "risks", List.of(Map.of("level","中","name","信息不足","advice","补充目标渠道、竞品价格、预计销量和工艺参数后复评")),
                "opportunities", List.of("可先做小批量打样验证", "可围绕地域文化故事强化传播点"),
                "nextActions", List.of("完善竞品/爆款参考", "进入BOM与成本测算", "生成改版视觉方案"),
                "benchmark", "暂未获得完整爆款对标数据，建议补充竞品链接、价格、销量、卖点。"
        ));
    }

    private Map<String,Object> buildReviewMatrix(List<Map<String,Object>> results) {
        List<String> keys=List.of("设计表现","市场潜力","成本生产","消费转化","爆款对标");
        Map<String,Object> matrix=new LinkedHashMap<>();
        for(String k:keys){int sum=0,count=0;for(Map<String,Object> r:results){Object ss=r.get("subScores");if(ss instanceof Map<?,?> m&&m.get(k) instanceof Number n){sum+=n.intValue();count++;}}matrix.put(k,count==0?0:BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count),1,java.math.RoundingMode.HALF_UP));}
        return matrix;
    }

    private Map<String,Object> buildUpgradeRoadmap(BigDecimal avg,String recommendation,List<Map<String,Object>> results) {
        List<Object> risks=new ArrayList<>(), opportunities=new ArrayList<>(), actions=new ArrayList<>();
        for(Map<String,Object> r:results){ if(r.get("risks") instanceof List<?> l)risks.addAll(l); if(r.get("opportunities") instanceof List<?> l)opportunities.addAll(l); if(r.get("nextActions") instanceof List<?> l)actions.addAll(l); }
        return Map.of(
                "phase1", List.of("结构化评分：补全设计/市场/成本/转化/爆款对标五维雷达图", "沉淀风险标签和改版建议", "形成原方案与改版方案对比记录"),
                "phase2", List.of("爆款拆解：录入竞品价格、销量、材质、卖点、渠道，输出差距表", "生成适配不同渠道的卖点与价格带", "识别IP、文化表达、生产和库存风险"),
                "phase3", List.of("通过后进入BOM、工艺路线和成本核算", "自动生成打样任务和小批量试销计划", "依据试销反馈回流更新评分模型"),
                "risks", risks.stream().limit(8).toList(),
                "opportunities", opportunities.stream().limit(8).toList(),
                "nextActions", actions.stream().limit(10).toList(),
                "decision", avg.intValue()>=85?"可以进入打样与成本核算":avg.intValue()>=70?"建议先改版，再进入打样评审":"建议暂缓，先重做定位/设计/成本方案"
        );
    }

    private Map<String,Object> jsonMap(JsonNode n){Map<String,Object> m=new LinkedHashMap<>();if(n!=null&&n.isObject())n.fields().forEachRemaining(e->m.put(e.getKey(),e.getValue().isNumber()?e.getValue().numberValue():e.getValue().asText()));return m;}
    private List<Object> jsonList(JsonNode n){List<Object> l=new ArrayList<>();if(n!=null&&n.isArray())n.forEach(x->{if(x.isObject())l.add(jsonMap(x));else l.add(x.asText());});return l;}
    private List<String> stringList(JsonNode n){List<String> l=new ArrayList<>();if(n!=null&&n.isArray())n.forEach(x->l.add(x.asText()));return l;}

    private String buildReviewSummary(BigDecimal avg, String recommendation, List<Map<String, Object>> results) {
        String rec = "go".equals(recommendation) ? "建议进入商品化打样" : "adjust".equals(recommendation) ? "建议优化后再打样" : "暂不建议进入生产";
        return "AI评审团平均分 " + avg + "，结论：" + rec + "。重点关注设计表达、市场卖点、成本可行性与消费者购买理由四个维度。";
    }

    private Map<String, Object> style(Long id) {
        // Both C 端的“风格工作台”和图片生成都只能使用已启用的风格。这样用户
        // 即使篡改 styleId 也无法读取或套用后台停用中的内部风格草稿。
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, name, base_prompt basePrompt, negative_prompt negativePrompt, cultural_guardrails culturalGuardrails " +
                        "FROM brand_style_profile WHERE id=? AND enabled=1", id == null ? 1L : id);
        if (rows.isEmpty()) throw new IllegalArgumentException("所选风格不存在或已停用，请重新选择");
        return rows.get(0);
    }

    private String buildPrompt(String userPrompt, Map<String, Object> style, String scene, String productType) {
        StringBuilder sb = new StringBuilder();
        sb.append(style.get("basePrompt"));
        if (scene != null && !scene.isBlank()) sb.append(", scene: ").append(scene.trim());
        if (productType != null && !productType.isBlank()) sb.append(", designed for ").append(productType.trim());
        sb.append(", brand name: Between Taste, premium cultural creative product visual, high detail, commercial-ready");
        if (userPrompt != null && !userPrompt.isBlank()) sb.append(", user concept: ").append(userPrompt.trim());
        Object guard = style.get("culturalGuardrails");
        if (guard != null) sb.append(", cultural guardrails: ").append(guard);
        return sb.toString();
    }

    /**
     * Image-to-image is a product adaptation of the supplied asset, not a new
     * text-to-image concept. Keep this rule server-side so every client uses
     * the same preservation contract even when it sends a weak prompt.
     */
    private String buildReferencePreservingPrompt(String requestedPrompt, String productCategory, String material, String visualBrief) {
        String product = blank(productCategory) ? "the requested cultural creative product" : productCategory.trim();
        String surface = blank(material) ? "the requested production material and finish" : material.trim();
        return "IMPORTANT IMAGE-TO-IMAGE IDENTITY LOCK: The supplied reference image is the single primary source of truth. "
                + "Create a product adaptation of THAT SAME SUBJECT, not a newly invented design. "
                + "Preserve the reference image's main subject identity, recognizable silhouette and proportions, overall composition, main color palette, "
                + "and all distinctive visual details, motifs, markings and structural features that make it immediately recognizable. "
                + "A viewer must immediately understand that the result was made from the supplied reference image. "
                + "Only adapt the preserved subject for " + product + " using " + surface + "; do not replace, redesign, swap, crop away, or invent a different main subject. "
                + "First preserve these objectively recognized reference-image facts: " + visualBrief + " "
                + "If the reference is a screenshot, remove only phone/app frames, status bars, buttons, text controls and other UI overlays; retain the actual artwork, people, objects, scenery and color atmosphere underneath. "
                + "When any stylistic instruction conflicts with the reference identity, the reference identity always wins. "
                + "Requested product presentation: " + requestedPrompt;
    }

    private String buildBalancedRefinementPrompt(String optimizedEdit, String productCategory, String material, String visualBrief, String refinementNote) {
        String product = blank(productCategory) ? "the current cultural creative product" : productCategory.trim();
        String surface = blank(material) ? "its current production material and finish" : material.trim();
        return "BALANCED IMAGE EDIT: Use the supplied image as the visual source. Keep the recognizable subject identity, cultural theme, dominant color family and key decorative motifs from the current image. "
                + "Do not return a near-duplicate. The mandatory requested change below must be clearly visible in the finished image, including any requested change of shape, carrier, product form, structure or composition. "
                + "Retain continuity with these reference facts where they do not conflict with the requested change: " + visualBrief + " "
                + "Create one coherent, production-oriented " + product + " using " + surface + ". "
                + "Mandatory user change: " + nullToEmpty(refinementNote) + " "
                + "Optimized edit direction: " + optimizedEdit;
    }

    private ReferenceImageAnalysis analyzeReferenceImage(Long assetId) {
        return analyzeReferenceImage(assetId, false);
    }

    private ReferenceImageAnalysis analyzeReferenceImage(Long assetId, boolean trustedJob) {
        String fallback = "Preserve every visible non-UI subject, person or object, its recognizable silhouette and proportions, the original scene layout, dominant colors, mood, decorative motifs and all distinguishing details.";
        if (!siliconflowVisionEnabled || blank(siliconflowApiKey) || siliconflowApiKey.contains("YOUR_")) {
            return new ReferenceImageAnalysis(fallback, "fallback_no_vision_key");
        }
        try {
            String image = trustedJob ? readInputImageForSiliconFlow(assetId) : buildInputImageForSiliconFlow(assetId);
            String system = "You are a precise visual analyst for image-to-image generation. Inspect the supplied reference image. "
                    + "Return ONE concise ENGLISH visual preservation brief, no markdown and no introduction. "
                    + "Describe only what is visibly present: main subject/person/object and distinctive appearance, secondary subjects, setting, dominant colors, composition, atmosphere, motifs, and any UI/screenshot overlays to remove. "
                    + "Do not invent details. Prioritize facts needed to make a generated image clearly recognizable as derived from this reference.";
            Map<String,Object> imagePart = new LinkedHashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", Map.of("url", image, "detail", "high"));
            Map<String,Object> textPart = Map.of(
                    "type", "text",
                    "text", "Analyze this reference for faithful product adaptation. Include any phone/app UI, buttons, captions or status bars that must be removed from the final artwork.");
            Map<String,Object> payload = new LinkedHashMap<>();
            payload.put("model", visionModel);
            payload.put("temperature", 0.1);
            payload.put("max_tokens", 450);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", system),
                    Map.of("role", "user", "content", List.of(imagePart, textPart))));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.siliconflow.cn/v1/chat/completions"))
                    .header("Authorization", "Bearer " + siliconflowApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new ReferenceImageAnalysis(fallback, "fallback_vision_http_" + response.statusCode());
            }
            String brief = mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("").trim();
            if (brief.length() > 900) brief = brief.substring(0, 900);
            return new ReferenceImageAnalysis(blank(brief) ? fallback : brief, blank(brief) ? "fallback_empty_vision_response" : "siliconflow:" + visionModel);
        } catch (Exception ignored) {
            return new ReferenceImageAnalysis(fallback, "fallback_vision_unavailable");
        }
    }

    private Map<String,Object> referenceImageMetadata(String remoteUrl, Long inputAssetId, ReferenceImageAnalysis analysis, boolean refinement) {
        Map<String,Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", "siliconflow");
        metadata.put("model", imageEditModel);
        metadata.put("remoteUrl", remoteUrl);
        metadata.put("inputAssetId", inputAssetId);
        metadata.put("referencePreservation", "subject,silhouette,composition,main_colors,distinctive_details");
        metadata.put("refinement", refinement);
        metadata.put("referenceAnalysis", analysis.visualBrief);
        metadata.put("referenceAnalysisSource", analysis.source);
        return metadata;
    }

    private static class ReferenceImageAnalysis {
        private final String visualBrief;
        private final String source;

        private ReferenceImageAnalysis(String visualBrief, String source) {
            this.visualBrief = visualBrief;
            this.source = source;
        }
    }

    private String mergeNegative(String userNegative, String styleNegative) {
        if (userNegative == null || userNegative.isBlank()) return styleNegative == null ? "" : styleNegative;
        if (styleNegative == null || styleNegative.isBlank()) return userNegative;
        return styleNegative + ", " + userNegative;
    }

    private String extractImageUrl(JsonNode root) {
        JsonNode data = root.get("data");
        if (data != null && data.isArray() && data.size() > 0 && data.get(0).hasNonNull("url")) return data.get(0).get("url").asText();
        JsonNode images = root.get("images");
        if (images != null && images.isArray() && images.size() > 0 && images.get(0).hasNonNull("url")) return images.get(0).get("url").asText();
        throw new IllegalStateException("无法从SiliconFlow响应中解析图片URL: " + root);
    }

    private String buildInputImageForSiliconFlow(Long assetId) throws IOException {
        requireAssetAccess(assetId);
        return readInputImageForSiliconFlow(assetId);
    }

    private String readInputImageForSiliconFlow(Long assetId) throws IOException {
        Map<String, Object> asset = jdbc.queryForMap("SELECT file_url fileUrl, preview_url previewUrl, format FROM digital_asset WHERE id=?", assetId);
        String url = String.valueOf(asset.get("fileUrl") == null ? asset.get("previewUrl") : asset.get("fileUrl"));
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        Path file = resolvePublicAssetFile(url, "参考图文件不存在：");
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String mime = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg" : lower.endsWith(".webp") ? "image/webp" : "image/png";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
    }

    private String buildArkReferenceImage(Long assetId) throws Exception {
        Path image = resolveAssetImage(assetId);
        String name = image.getFileName().toString().toLowerCase(Locale.ROOT);
        String contentType = name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg" : name.endsWith(".webp") ? "image/webp" : "image/png";
        byte[] bytes = Files.readAllBytes(image);
        if (bytes.length > 15L * 1024 * 1024) throw new IllegalArgumentException("参考图不能超过 15MB，请压缩后重试");
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private String saveRemoteImage(String url, String prefix, String suffix) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("下载生成图片失败 HTTP " + response.statusCode());
        Path dir = creativeAssetRoot().resolve("generated").normalize();
        Files.createDirectories(dir);
        String file = prefix + System.currentTimeMillis() + suffix;
        Files.write(dir.resolve(file), response.body());
        return "/generated/" + file;
    }

    private Long createAsset(String title, String type, String sourceType, String fileUrl, String previewUrl, String prompt, String negative, Long styleId, Long parentAssetId, String format, String tags, Map<String, Object> meta) throws Exception {
        KeyHolder kh = new GeneratedKeyHolder();
        String assetNo = no("AST");
        String metaJson = mapper.writeValueAsString(meta == null ? Map.of() : meta);
        String initialStatus = "draft";
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO digital_asset (asset_no,title,asset_type,source_type,file_url,preview_url,prompt,negative_prompt,style_id,parent_asset_id,format,tags,metadata_json,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, assetNo); ps.setString(2, title); ps.setString(3, type); ps.setString(4, sourceType == null ? "ai_generated" : sourceType); ps.setString(5, fileUrl); ps.setString(6, previewUrl); ps.setString(7, prompt); ps.setString(8, negative); if (styleId == null) ps.setNull(9, java.sql.Types.BIGINT); else ps.setLong(9, styleId); if (parentAssetId == null) ps.setNull(10, java.sql.Types.BIGINT); else ps.setLong(10, parentAssetId); ps.setString(11, format); ps.setString(12, tags); ps.setString(13, metaJson); ps.setString(14, initialStatus);
            return ps;
        }, kh);
        Long assetId=Objects.requireNonNull(kh.getKey()).longValue();
        Object owner=meta==null?null:meta.get("createdByUserId");
        if(owner instanceof Number) assignAssetOwner(assetId,((Number)owner).longValue());
        return assetId;
    }

    private void addProductIdentity(Map<String, Object> metadata, String productKey, String productName, String material) {
        if (!blank(productKey)) metadata.put("productKey", productKey.trim());
        if (!blank(productName)) metadata.put("productName", productName.trim());
        if (!blank(material)) metadata.put("productMaterial", material.trim());
    }

    private Map<String, Object> withProductIdentity(Map<String, Object> metadata, String productKey, String productName, String material) {
        addProductIdentity(metadata, productKey, productName, material);
        return metadata;
    }

    private void storeJobProductIdentity(Long jobId, String productKey, String productName, String material) {
        if (jobId == null) return;
        jdbc.update("UPDATE ai_generation_job SET product_key=?,product_name=?,product_material=? WHERE id=?",
                blank(productKey) ? null : productKey.trim(), blank(productName) ? null : productName.trim(),
                blank(material) ? null : material.trim(), jobId);
    }

    private void assignAssetOwner(Long assetId, Long userId) {
        if(assetId==null||userId==null) return;
        try { jdbc.update("UPDATE digital_asset SET created_by=? WHERE id=?", userId, assetId); } catch(Exception ignored) {}
    }

    private void assignJobOwner(Long jobId, Long userId) {
        if(jobId==null||userId==null) return;
        try { jdbc.update("UPDATE ai_generation_job SET created_by=? WHERE id=?", userId, jobId); } catch(Exception ignored) {}
    }

    private List<Map<String,Object>> enrichProductionRows(List<Map<String,Object>> rows) {
        for(Map<String,Object> r:rows) {
            Object json=r.get("museumDistributionJson");
            try {
                if(json==null||blank(String.valueOf(json))) r.put("museumDistribution",List.of());
                else r.put("museumDistribution",mapper.readValue(String.valueOf(json),List.class));
            } catch(Exception ignored) { r.put("museumDistribution",List.of()); }
        }
        addSignedAssetUrls(rows);
        return rows;
    }

    private int parsePositiveInt(Object value,int fallback) {
        if(value==null||blank(String.valueOf(value))) return fallback;
        try { return Integer.parseInt(String.valueOf(value).trim()); } catch(Exception e) { throw new IllegalArgumentException("数量必须是整数"); }
    }

    private int parseNonNegativeInt(Object value) {
        if(value==null||blank(String.valueOf(value))) return 0;
        int n=parsePositiveInt(value,0);
        if(n<0) throw new IllegalArgumentException("数量不能小于0");
        return n;
    }

    private List<Map<String,Object>> normalizeMuseumDistribution(Object raw) {
        if(!(raw instanceof List<?> list)) return new ArrayList<>();
        Map<String,Map<String,Object>> museumLookup=new LinkedHashMap<>();
        consumerProductionMuseums().forEach(m -> museumLookup.put(String.valueOf(m.get("id")),m));
        List<Map<String,Object>> out=new ArrayList<>();
        for(Object item:list) {
            if(!(item instanceof Map<?,?> m)) continue;
            String museumId=m.get("museumId")==null?"":String.valueOf(m.get("museumId"));
            String museumName=m.get("museumName")==null?"":String.valueOf(m.get("museumName"));
            int qty=parseNonNegativeInt(m.get("quantity"));
            if(qty<=0) continue;
            Map<String,Object> known=museumLookup.get(museumId);
            if(known!=null) { museumName=String.valueOf(known.get("name")); }
            if(blank(museumName)) throw new IllegalArgumentException("博物馆投放项缺少名称");
            Map<String,Object> row=new LinkedHashMap<>();row.put("museumId",museumId);row.put("museumName",museumName);row.put("quantity",qty);if(known!=null){row.put("province",known.get("province"));row.put("city",known.get("city"));row.put("district",known.get("district"));row.put("scene",known.get("scene"));row.put("approvalSource",known.get("province") + "" + known.get("city") + "" + known.get("district") + " · " + known.get("name"));}
            out.add(row);
        }
        return out;
    }

    private BigDecimal consumerCreditCost(String bizType) {
        return switch (nullToEmpty(bizType)) {
            case "image2d" -> BigDecimal.valueOf(16);
            case "image_to_3d" -> BigDecimal.valueOf(70);
            case "text_to_3d" -> BigDecimal.valueOf(60);
            case "model_convert" -> BigDecimal.valueOf(1);
            default -> BigDecimal.ZERO;
        };
    }

    private void requireConsumerUser(Long userId) {
        if(userId==null) throw new IllegalArgumentException("缺少C端用户ID");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,role FROM user WHERE id=? LIMIT 1",userId);
        if(rows.isEmpty()||!"user".equals(String.valueOf(rows.get(0).get("role")))) throw new IllegalStateException("仅C端用户可使用额度账户");
    }

    private Set<String> rewardMissionKeys() {
        return new LinkedHashSet<>(List.of(
                "first_image_success", "first_model_success", "first_review_submit",
                "first_approved_work", "first_sample_request"));
    }

    private String rewardMissionTitle(String missionKey) {
        return switch (missionKey) {
            case "first_image_success" -> "完成第一张 AI 产品图";
            case "first_model_success" -> "完成第一个 3D 原型";
            case "first_review_submit" -> "完成第一次作品提交审核";
            case "first_approved_work" -> "让第一件作品通过审核";
            case "first_sample_request" -> "提交第一次打样申请";
            default -> throw new IllegalArgumentException("不支持的创作任务");
        };
    }

    private BigDecimal rewardMissionAmount(String missionKey) {
        return switch (missionKey) {
            case "first_image_success" -> BigDecimal.valueOf(10);
            case "first_model_success" -> BigDecimal.valueOf(15);
            case "first_review_submit" -> BigDecimal.valueOf(10);
            case "first_approved_work" -> BigDecimal.valueOf(20);
            case "first_sample_request" -> BigDecimal.valueOf(20);
            default -> throw new IllegalArgumentException("不支持的创作任务");
        };
    }

    private String rewardMissionDescription(String missionKey) {
        return switch (missionKey) {
            case "first_image_success" -> "首次成功生成并保存一张 AI 文创产品图。";
            case "first_model_success" -> "首次成功生成并保存一个可预览的 3D 原型。";
            case "first_review_submit" -> "首次将自己的图片或 3D 作品提交给审核员。";
            case "first_approved_work" -> "第一件作品获得审核通过，进入可继续生产的状态。";
            case "first_sample_request" -> "为已通过的 3D 作品提交一次真实打样申请。";
            default -> "";
        };
    }

    private Map<String,Object> rewardMissionOverview(Long userId, String missionKey) {
        List<Map<String,Object>> claimedRows = jdbc.queryForList("SELECT asset_id assetId,status,claimed_at claimedAt FROM consumer_reward_mission_claim WHERE user_id=? AND mission_key=? LIMIT 1", userId, missionKey);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("key", missionKey); out.put("title", rewardMissionTitle(missionKey)); out.put("description", rewardMissionDescription(missionKey)); out.put("rewardAmount", rewardMissionAmount(missionKey));
        if (!claimedRows.isEmpty()) {
            out.put("status", "claimed"); out.put("assetId", claimedRows.get(0).get("assetId")); out.put("claimedAt", claimedRows.get(0).get("claimedAt"));
            return out;
        }
        String predicate = switch (missionKey) {
            case "first_image_success" -> "asset_type='image' AND created_by=? AND COALESCE(source_type,'ai_generated')<>'upload'";
            case "first_model_success" -> "asset_type='model' AND created_by=?";
            case "first_review_submit" -> "created_by=? AND asset_type IN ('image','model') AND tags LIKE '%用户提交审核%'";
            case "first_approved_work" -> "created_by=? AND asset_type IN ('image','model') AND status='approved'";
            case "first_sample_request" -> "id IN (SELECT asset_id FROM consumer_production_request WHERE user_id=? AND request_type='sample')";
            default -> throw new IllegalArgumentException("不支持的创作任务");
        };
        List<Map<String,Object>> assetRows = jdbc.queryForList("SELECT id FROM digital_asset WHERE " + predicate + " ORDER BY id ASC LIMIT 1", userId);
        if (assetRows.isEmpty()) {
            out.put("status", "in_progress");
        } else {
            out.put("status", "claimable"); out.put("assetId", assetRows.get(0).get("id"));
        }
        return out;
    }

    private String defaultCampaignKey() { return "museum_summer_gift_2026"; }

    private CampaignDefinition campaignDefinition(String campaignKey) {
        String key = nullToEmpty(campaignKey).trim();
        return CREATOR_CAMPAIGNS.stream()
                .filter(campaign -> campaign.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("活动不存在或已结束"));
    }

    private BigDecimal campaignRewardAmount(String campaignKey) {
        return campaignDefinition(campaignKey).rewardAmount();
    }

    private Map<String,Object> publicCampaignMap(CampaignDefinition campaign) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("key", campaign.key());
        out.put("title", campaign.title());
        out.put("targetName", campaign.targetName());
        out.put("channelCode", campaign.channelCode());
        out.put("collectionStyle", campaign.collectionStyle());
        out.put("recommendedProducts", campaign.recommendedProducts());
        out.put("recommendedProductKey", campaign.recommendedProductKey());
        out.put("brief", campaign.brief());
        out.put("promptHint", campaign.promptHint());
        out.put("rewardAmount", campaign.rewardAmount());
        out.put("deadline", campaign.deadline());
        out.put("reviewNotice", "投稿作品须先进入人工审核；审核通过后系统自动发放积分，积分不构成现金或销售收益承诺。");
        out.put("cooperationNotice", "该内容为平台优先征集方向，不代表目标机构已采购、合作、授权或认可具体作品。");
        return out;
    }

    private Map<String,Object> campaignOverview(Long userId, String campaignKey) {
        CampaignDefinition campaign = campaignDefinition(campaignKey);
        Map<String,Object> out = publicCampaignMap(campaign);
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT c.asset_id assetId,a.title assetTitle,c.status,c.reward_amount rewardAmount,c.reviewed_at reviewedAt,c.created_at createdAt FROM consumer_campaign_reward c LEFT JOIN digital_asset a ON a.id=c.asset_id WHERE c.user_id=? AND c.campaign_key=? LIMIT 1", userId, campaign.key());
        if (rows.isEmpty()) {
            out.put("status", "not_joined");
        } else {
            out.putAll(rows.get(0));
        }
        return out;
    }

    private void createCampaignParticipation(Long userId, CampaignDefinition campaign, Long assetId) {
        try {
            int inserted = jdbc.update("INSERT INTO consumer_campaign_reward (participation_no,user_id,campaign_key,asset_id,status,reward_amount) VALUES (?,?,?,?, 'pending_review',?)", no("CRW"), userId, campaign.key(), assetId, campaign.rewardAmount());
            if (inserted != 1) throw new IllegalStateException("活动投稿创建失败");
            jdbc.update("UPDATE digital_asset SET tags=CONCAT(COALESCE(tags,''), ?) WHERE id=? AND created_by=?", ";活动投稿=" + campaign.key(), assetId, userId);
        } catch (DataIntegrityViolationException error) {
            List<Map<String,Object>> existing = jdbc.queryForList("SELECT campaign_key campaignKey,asset_id assetId,status FROM consumer_campaign_reward WHERE user_id=? AND (campaign_key=? OR asset_id=?) LIMIT 1", userId, campaign.key(), assetId);
            if (!existing.isEmpty()) {
                Map<String,Object> row = existing.get(0);
                if (campaign.key().equals(String.valueOf(row.get("campaignKey")))) {
                    throw new IllegalStateException("该优先征集任务已投稿，请勿重复提交");
                }
                throw new IllegalStateException("该作品已参加其他优先征集任务，请选择另一件作品");
            }
            throw error;
        }
    }

    private record CampaignDefinition(
            String key,
            String title,
            String channelCode,
            String targetName,
            String collectionStyle,
            List<String> recommendedProducts,
            String recommendedProductKey,
            String brief,
            String promptHint,
            BigDecimal rewardAmount,
            String deadline,
            boolean legacyManualParticipation) {
    }

    private BigDecimal settleCampaignRewardForReview(Long assetId, String assetStatus, String operator) {
        if (!"approved".equals(assetStatus) && !"rejected".equals(assetStatus)) return BigDecimal.ZERO;
        BigDecimal result = creditTransactions.execute(status -> {
            List<Map<String,Object>> rows = jdbc.queryForList("SELECT id,user_id userId,campaign_key campaignKey,asset_id assetId,status,reward_amount rewardAmount FROM consumer_campaign_reward WHERE asset_id=? FOR UPDATE", assetId);
            if (rows.isEmpty()) return BigDecimal.ZERO;
            Map<String,Object> row = rows.get(0);
            if (!"pending_review".equals(String.valueOf(row.get("status")))) return BigDecimal.ZERO;
            Long userId = ((Number) row.get("userId")).longValue();
            if ("rejected".equals(assetStatus)) {
                jdbc.update("UPDATE consumer_campaign_reward SET status='rejected',reviewed_by=?,reviewed_at=NOW() WHERE id=? AND status='pending_review'", operator, row.get("id"));
                return BigDecimal.ZERO;
            }
            BigDecimal amount = toDecimal(row.get("rewardAmount"));
            Long txId = grantRewardCreditInTransaction(userId, assetId, amount, "活动审核通过：" + String.valueOf(row.get("campaignKey")));
            int updated = jdbc.update("UPDATE consumer_campaign_reward SET status='rewarded',credit_transaction_id=?,reviewed_by=?,reviewed_at=NOW() WHERE id=? AND status='pending_review'", txId, operator, row.get("id"));
            if (updated != 1) throw new IllegalStateException("活动奖励状态并发变化，已回滚");
            return amount;
        });
        return result == null ? BigDecimal.ZERO : result;
    }

    private Long grantRewardCreditInTransaction(Long userId, Long assetId, BigDecimal amount, String remark) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("奖励积分必须大于0");
        ensureConsumerCreditAccount(userId);
        List<Map<String,Object>> accounts = jdbc.queryForList("SELECT balance FROM consumer_credit_account WHERE user_id=? FOR UPDATE", userId);
        if (accounts.isEmpty()) throw new IllegalStateException("积分账户不存在");
        BigDecimal before = toDecimal(accounts.get(0).get("balance"));
        BigDecimal after = before.add(amount);
        int changed = jdbc.update("UPDATE consumer_credit_account SET balance=? WHERE user_id=?", after, userId);
        if (changed != 1) throw new IllegalStateException("积分账户更新失败");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO consumer_credit_transaction (transaction_no,user_id,asset_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, no("CRT")); ps.setLong(2, userId); if (assetId == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, assetId);
            ps.setString(4, "reward"); ps.setBigDecimal(5, amount); ps.setString(6, "income"); ps.setString(7, "completed"); ps.setBigDecimal(8, before); ps.setBigDecimal(9, after); ps.setString(10, remark); ps.setString(11, "system");
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private synchronized void ensureConsumerCreditAccount(Long userId) {
        requireConsumerUser(userId);
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM consumer_credit_account WHERE user_id=?",Integer.class,userId);
        if(count==null||count==0) {
            BigDecimal initial=consumerCreditInitialBalance==null?BigDecimal.ZERO:consumerCreditInitialBalance;
            jdbc.update("INSERT INTO consumer_credit_account (user_id,balance,total_recharged) VALUES (?,?,?)",userId,initial.max(BigDecimal.ZERO),initial.max(BigDecimal.ZERO));
            if(initial.compareTo(BigDecimal.ZERO)>0) {
                jdbc.update("INSERT INTO consumer_credit_transaction (transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        no("CRT"),userId,"initial",initial,"recharge","completed",BigDecimal.ZERO,initial,"系统初始化赠送额度","system");
            }
        }
    }

    private Map<String,Object> creditAccountMap(Long userId) {
        Map<String,Object> row=jdbc.queryForMap("SELECT a.user_id userId,u.username,a.balance,a.frozen_balance frozenBalance,a.total_recharged totalRecharged,a.total_consumed totalConsumed,a.updated_at updatedAt FROM consumer_credit_account a JOIN user u ON u.id=a.user_id WHERE a.user_id=?",userId);
        Map<String,Object> out=new LinkedHashMap<>(row);
        out.put("rules",consumerCreditRules());
        return out;
    }

    private synchronized Long reserveConsumerCredit(Long userId,String bizType,BigDecimal amount,String remark) {
        if(userId==null||amount==null||amount.compareTo(BigDecimal.ZERO)<=0) return null;
        return creditTransactions.execute(status -> reserveConsumerCreditInTransaction(userId,bizType,amount,remark));
    }

    private Long reserveConsumerCreditInTransaction(Long userId,String bizType,BigDecimal amount,String remark) {
        ensureConsumerCreditAccount(userId);
        // Conditional debit is shared with payment-refund's row lock. This is
        // deliberately an atomic SQL predicate rather than a read-then-write,
        // so a concurrent refund or another application node cannot overdraw
        // the same credit balance.
        int reserved=jdbc.update("UPDATE consumer_credit_account SET balance=balance-?,frozen_balance=frozen_balance+? WHERE user_id=? AND balance>=?",amount,amount,userId,amount);
        if(reserved!=1) {
            BigDecimal balance=toDecimal(jdbc.queryForObject("SELECT balance FROM consumer_credit_account WHERE user_id=?",Object.class,userId));
            throw new IllegalStateException("额度不足：当前剩余 "+plain(balance)+" 点，本次需要 "+plain(amount)+" 点，请联系管理员充值");
        }
        Map<String,Object> account=jdbc.queryForMap("SELECT balance FROM consumer_credit_account WHERE user_id=?",userId);
        BigDecimal after=toDecimal(account.get("balance"));
        BigDecimal balance=after.add(amount);
        KeyHolder kh=new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps=con.prepareStatement("INSERT INTO consumer_credit_transaction (transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,no("CRT"));ps.setLong(2,userId);ps.setString(3,bizType);ps.setBigDecimal(4,amount);ps.setString(5,"consume");ps.setString(6,"pending");ps.setBigDecimal(7,balance);ps.setBigDecimal(8,after);ps.setString(9,remark);ps.setString(10,"system");
            return ps;
        },kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private synchronized void linkCreditTransaction(Long txId,Long jobId,Long assetId) {
        if(txId==null) return;
        jdbc.update("UPDATE consumer_credit_transaction SET job_id=COALESCE(?,job_id),asset_id=COALESCE(?,asset_id) WHERE id=?",jobId,assetId,txId);
        if(jobId!=null) try{jdbc.update("UPDATE ai_generation_job SET credit_transaction_id=? WHERE id=?",txId,jobId);}catch(Exception ignored){}
    }

    private Long creditTransactionIdForJob(Long jobId) {
        if(jobId==null) return null;
        try {
            Object v=jdbc.queryForObject("SELECT credit_transaction_id FROM ai_generation_job WHERE id=?",Object.class,jobId);
            return v instanceof Number ? ((Number)v).longValue() : null;
        } catch(Exception ignored) { return null; }
    }

    private synchronized void completeConsumerCredit(Long txId,Long jobId,Long assetId) {
        if(txId==null) return;
        creditTransactions.execute(status -> { completeConsumerCreditInTransaction(txId,jobId,assetId); return null; });
    }

    private void completeConsumerCreditInTransaction(Long txId,Long jobId,Long assetId) {
        // Lock the ledger row across application nodes. The in-process
        // synchronized guard is not sufficient when the scheduler and an
        // API request run on different instances.
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,user_id userId,amount,status FROM consumer_credit_transaction WHERE id=? FOR UPDATE",txId);
        if(rows.isEmpty()||!"pending".equals(String.valueOf(rows.get(0).get("status")))) return;
        Long userId=((Number)rows.get(0).get("userId")).longValue();
        BigDecimal amount=toDecimal(rows.get(0).get("amount"));
        int accountChanged=jdbc.update("UPDATE consumer_credit_account SET frozen_balance=frozen_balance-?,total_consumed=total_consumed+? WHERE user_id=? AND frozen_balance>=?",amount,amount,userId,amount);
        if(accountChanged!=1) throw new IllegalStateException("额度冻结流水与账户余额不一致，需人工核对");
        int txChanged=jdbc.update("UPDATE consumer_credit_transaction SET status='completed',job_id=COALESCE(?,job_id),asset_id=COALESCE(?,asset_id),remark=CONCAT(COALESCE(remark,''),';已完成扣费') WHERE id=? AND status='pending'",jobId,assetId,txId);
        if(txChanged!=1) throw new IllegalStateException("额度流水状态并发变化，事务已回滚");
    }

    private synchronized void refundConsumerCredit(Long txId,String reason) {
        if(txId==null) return;
        creditTransactions.execute(status -> { refundConsumerCreditInTransaction(txId,reason); return null; });
    }

    private void refundConsumerCreditInTransaction(Long txId,String reason) {
        // Lock the ledger row so a refund and a completion cannot both apply
        // the same pending reservation when requests hit different nodes.
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,user_id userId,amount,status FROM consumer_credit_transaction WHERE id=? FOR UPDATE",txId);
        if(rows.isEmpty()||!"pending".equals(String.valueOf(rows.get(0).get("status")))) return;
        Long userId=((Number)rows.get(0).get("userId")).longValue();
        BigDecimal amount=toDecimal(rows.get(0).get("amount"));
        int accountChanged=jdbc.update("UPDATE consumer_credit_account SET balance=balance+?,frozen_balance=frozen_balance-? WHERE user_id=? AND frozen_balance>=?",amount,amount, userId,amount);
        if(accountChanged!=1) throw new IllegalStateException("额度冻结流水与账户余额不一致，需人工核对");
        Map<String,Object> account=jdbc.queryForMap("SELECT balance FROM consumer_credit_account WHERE user_id=?",userId);
        int txChanged=jdbc.update("UPDATE consumer_credit_transaction SET status='refunded',balance_after=?,remark=CONCAT(COALESCE(remark,''),?) WHERE id=? AND status='pending'",toDecimal(account.get("balance")),";失败退回-"+nullToEmpty(reason),txId);
        if(txChanged!=1) throw new IllegalStateException("额度流水状态并发变化，事务已回滚");
    }

    private synchronized Long rechargeCredit(Long userId,BigDecimal amount,String operator,String remark) {
        ensureConsumerCreditAccount(userId);
        Map<String,Object> account=jdbc.queryForMap("SELECT balance FROM consumer_credit_account WHERE user_id=?",userId);
        BigDecimal before=toDecimal(account.get("balance"));
        BigDecimal after=before.add(amount);
        jdbc.update("UPDATE consumer_credit_account SET balance=?,total_recharged=total_recharged+? WHERE user_id=?",after,amount,userId);
        KeyHolder kh=new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps=con.prepareStatement("INSERT INTO consumer_credit_transaction (transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,no("CRT"));ps.setLong(2,userId);ps.setString(3,"admin_recharge");ps.setBigDecimal(4,amount);ps.setString(5,"recharge");ps.setString(6,"completed");ps.setBigDecimal(7,before);ps.setBigDecimal(8,after);ps.setString(9,blank(remark)?"管理员充值":remark);ps.setString(10,operator);
            return ps;
        },kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private synchronized Long setCreditBalance(Long userId,BigDecimal targetBalance,String operator,String remark) {
        ensureConsumerCreditAccount(userId);
        Map<String,Object> account=jdbc.queryForMap("SELECT balance FROM consumer_credit_account WHERE user_id=?",userId);
        BigDecimal before=toDecimal(account.get("balance"));
        BigDecimal after=targetBalance;
        BigDecimal delta=after.subtract(before);
        jdbc.update("UPDATE consumer_credit_account SET balance=? WHERE user_id=?",after,userId);
        KeyHolder kh=new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps=con.prepareStatement("INSERT INTO consumer_credit_transaction (transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,no("CRT"));ps.setLong(2,userId);ps.setString(3,"admin_set_balance");ps.setBigDecimal(4,delta.abs());ps.setString(5,"adjust");ps.setString(6,"completed");ps.setBigDecimal(7,before);ps.setBigDecimal(8,after);ps.setString(9,blank(remark)?"管理员直接设置额度余额":remark);ps.setString(10,operator);
            return ps;
        },kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private BigDecimal toDecimal(Object v) {
        if(v instanceof BigDecimal b) return b;
        if(v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if(v==null||blank(String.valueOf(v))) return BigDecimal.ZERO;
        return new BigDecimal(String.valueOf(v));
    }

    private String plain(BigDecimal v) { return v.stripTrailingZeros().toPlainString(); }

    private Long createJob(String jobNo, String type, String provider, String model, Long styleId, Long inputAssetId, String prompt, String negative, String status, String error, String exportFormats) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO ai_generation_job (job_no,job_type,provider,model_name,style_id,input_asset_id,prompt,negative_prompt,status,error_message,export_formats) VALUES (?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, jobNo); ps.setString(2, type); ps.setString(3, provider); ps.setString(4, model); if(styleId==null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, styleId); if(inputAssetId==null) ps.setNull(6, java.sql.Types.BIGINT); else ps.setLong(6, inputAssetId); ps.setString(7, prompt); ps.setString(8, negative); ps.setString(9, status); ps.setString(10, error); ps.setString(11, exportFormats);
            return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private String no(String prefix) { return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int)(Math.random()*900+100); }
    private String nullToEmpty(String s) { return s == null ? "" : s; }

    private String normalizeModelFormat(String format) {
        String f=blank(format)?"GLB":format.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("GLB","OBJ","STL").contains(f)) throw new IllegalArgumentException("暂只支持下载 GLB / OBJ / STL 格式");
        return f;
    }

    private Map<String,Object> resolveDownloadableModelAsset(Long id,String fmt) throws Exception {
        Map<String,Object> asset=jdbc.queryForMap("SELECT id,asset_no assetNo,title,asset_type assetType,source_type sourceType,file_url fileUrl,preview_url previewUrl,prompt,parent_asset_id parentAssetId,format,tags,metadata_json metadataJson,created_by createdBy FROM digital_asset WHERE id=?",id);
        if(!"model".equals(String.valueOf(asset.get("assetType")))) throw new IOException("该资产不是3D模型："+id);
        String currentFormat=str(asset.get("format")).toUpperCase(Locale.ROOT);
        if(asset.get("parentAssetId") instanceof Number&&"converted".equals(str(asset.get("sourceType")))){
            Map<String,Object> parent=jdbc.queryForMap("SELECT id,asset_no assetNo,title,asset_type assetType,source_type sourceType,file_url fileUrl,preview_url previewUrl,prompt,parent_asset_id parentAssetId,format,tags,metadata_json metadataJson,created_by createdBy FROM digital_asset WHERE id=?",((Number)asset.get("parentAssetId")).longValue());
            if("GLB".equals(fmt)) return parent;
            asset=parent; id=((Number)parent.get("id")).longValue(); currentFormat=str(parent.get("format")).toUpperCase(Locale.ROOT);
        }
        if("GLB".equals(fmt)||fmt.equals(currentFormat)) return asset;
        List<Map<String,Object>> cached=jdbc.queryForList("SELECT id,asset_no assetNo,title,asset_type assetType,file_url fileUrl,preview_url previewUrl,prompt,parent_asset_id parentAssetId,format,tags,metadata_json metadataJson,created_by createdBy FROM digital_asset WHERE asset_type='model' AND parent_asset_id=? AND UPPER(format)=? ORDER BY id DESC LIMIT 1",id,fmt);
        if(!cached.isEmpty()) return cached.get(0);
        if(modelConvertPreferLocal) {
            try { return convertModelFormatLocally(asset,fmt); }
            catch(Exception localError) {
                if(!modelConvertFallbackTripo) throw localError;
                if(blank(tripoApiKey)||tripoApiKey.contains("YOUR_")) throw localError;
                try { return convertTripoModelFormat(asset,fmt); }
                catch(Exception tripoError) { throw new IllegalStateException("本地模型转换失败："+safeMessage(localError)+"；Tripo在线转换也失败："+safeMessage(tripoError),tripoError); }
            }
        }
        return convertTripoModelFormat(asset,fmt);
    }

    private Map<String,Object> convertModelFormatLocally(Map<String,Object> source,String fmt) throws Exception {
        Long sourceId=((Number)source.get("id")).longValue();
        String preview=str(source.get("previewUrl"));
        Path assetDir=creativeAssetRoot();
        Path modelsDir=assetDir.resolve("generated").resolve("models").normalize();
        Files.createDirectories(modelsDir);
        Path sourceFile=null; boolean deleteSourceFile=false; Path workDir=null;
        try {
            sourceFile=resolveModelSourceFile(source);
            if(sourceFile==null) { sourceFile=downloadModelToTemp(source); deleteSourceFile=true; }
            String stamp=String.valueOf(System.currentTimeMillis());
            String localModel;
            if("OBJ".equals(fmt)) {
                workDir=modelsDir.resolve("convert-"+sourceId+"-"+stamp).normalize();
                Files.createDirectories(workDir);
                Path obj=workDir.resolve("model.obj");
                runLocalModelConverter(sourceFile,obj,fmt);
                if(!Files.exists(obj)||Files.size(obj)==0) throw new IllegalStateException("OBJ转换完成但没有生成有效文件");
                Path zip=modelsDir.resolve("and-taste-3d-"+sourceId+"-obj-"+stamp+".zip").normalize();
                zipDirectory(workDir,zip);
                deleteDirectoryQuietly(workDir); workDir=null;
                localModel="/generated/models/"+zip.getFileName();
            } else {
                Path stl=modelsDir.resolve("and-taste-3d-"+sourceId+"-stl-"+stamp+".stl").normalize();
                runLocalModelConverter(sourceFile,stl,fmt);
                if(!Files.exists(stl)||Files.size(stl)==0) throw new IllegalStateException("STL转换完成但没有生成有效文件");
                localModel="/generated/models/"+stl.getFileName();
            }
            Map<String,Object> meta=new LinkedHashMap<>();
            meta.put("provider",commandAvailable(modelConvertBlenderCommand)?"local-blender":commandAvailable(modelConvertAssimpCommand)?"local-assimp":"local-three");
            meta.put("convertedFromAssetId",sourceId);
            meta.put("sourceFile",str(source.get("fileUrl")));
            meta.put("format",fmt);
            if (source.get("createdBy") instanceof Number) meta.put("createdByUserId", ((Number) source.get("createdBy")).longValue());
            Long assetId=createAsset("3D模型 "+fmt+"格式","model","converted",localModel,blank(preview)?null:preview,str(source.get("prompt")),null,null,sourceId,fmt.toLowerCase(Locale.ROOT),"3D模型,本地格式转换,"+fmt,meta);
            return jdbc.queryForMap("SELECT id,asset_no assetNo,title,asset_type assetType,file_url fileUrl,preview_url previewUrl,prompt,parent_asset_id parentAssetId,format,tags,metadata_json metadataJson FROM digital_asset WHERE id=?",assetId);
        } finally {
            if(deleteSourceFile&&sourceFile!=null) try{Files.deleteIfExists(sourceFile);}catch(Exception ignored){}
            if(workDir!=null) deleteDirectoryQuietly(workDir);
        }
    }

    private Path resolveModelSourceFile(Map<String,Object> source) throws IOException {
        String url=str(source.get("fileUrl"));
        if(blank(url)||url.startsWith("http://")||url.startsWith("https://")) return null;
        return resolvePublicAssetFile(url,"模型源文件不存在：");
    }

    private Path downloadModelToTemp(Map<String,Object> source) throws Exception {
        String url=str(source.get("fileUrl"));
        if(blank(url)||!(url.startsWith("http://")||url.startsWith("https://"))) throw new IOException("模型源文件地址无效："+url);
        HttpResponse<byte[]> r=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        if(r.statusCode()<200||r.statusCode()>=300) throw new IOException("下载模型源文件失败 HTTP "+r.statusCode());
        Path tmp=Files.createTempFile("and-taste-model-source-",".glb");
        Files.write(tmp,r.body());
        return tmp;
    }

    private void runLocalModelConverter(Path input,Path output,String fmt) throws Exception {
        Path log=output.getParent().resolve("convert-"+fmt.toLowerCase(Locale.ROOT)+".log");
        List<List<String>> commands=new ArrayList<>();
        if(commandAvailable(modelConvertBlenderCommand)) {
            Path script=modelConvertScriptPath();
            commands.add(List.of(modelConvertBlenderCommand,"-b","--python",script.toString(),"--",input.toString(),output.toString(),fmt));
        }
        if(commandAvailable(modelConvertAssimpCommand)) {
            commands.add(List.of(modelConvertAssimpCommand,"export",input.toString(),output.toString()));
        }
        if(commandAvailable(modelConvertNodeCommand)) {
            Path script=modelConvertNodeScriptPath();
            commands.add(List.of(modelConvertNodeCommand,script.toString(),input.toString(),output.toString(),fmt));
        }
        if(commands.isEmpty()) {
            throw new IllegalStateException("服务器未安装模型转换器。请安装 Blender（推荐）、assimp，或确认 Node 可执行并已安装前端依赖");
        }
        List<String> errors=new ArrayList<>();
        for(List<String> cmd:commands) {
            Files.deleteIfExists(output);
            ProcessBuilder pb=new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile());
            Process p=pb.start();
            boolean finished=p.waitFor(Math.max(60,modelConvertTimeoutSeconds),java.util.concurrent.TimeUnit.SECONDS);
            String out=Files.exists(log)?Files.readString(log,StandardCharsets.UTF_8):"";
            String name=cmd.isEmpty()?"converter":cmd.get(0);
            if(!finished) {
                p.destroyForcibly();
                errors.add(name+"转换超时");
                continue;
            }
            if(p.exitValue()==0&&Files.exists(output)&&Files.size(output)>0) return;
            errors.add(name+"转换失败："+out);
        }
        throw new IllegalStateException("模型本地转换失败，已尝试可用转换器："+String.join("；",errors));
    }

    private boolean commandAvailable(String command) {
        if(blank(command)) return false;
        try {
            Process p=new ProcessBuilder(command,"--version").redirectErrorStream(true).start();
            boolean ok=p.waitFor(8,java.util.concurrent.TimeUnit.SECONDS);
            if(!ok) p.destroyForcibly();
            return ok&&p.exitValue()==0;
        } catch(Exception ignored) { return false; }
    }

    private Path modelConvertScriptPath() throws IOException {
        Path cwd=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates=List.of(cwd.resolve("scripts/model-convert-blender.py"),cwd.resolve("../scripts/model-convert-blender.py"),cwd.resolve("model-convert-blender.py"));
        for(Path p:candidates) if(Files.exists(p)) return p;
        throw new IOException("找不到 Blender 模型转换脚本 scripts/model-convert-blender.py");
    }

    private Path modelConvertNodeScriptPath() throws IOException {
        Path cwd=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates=List.of(cwd.resolve("shixun-vue/scripts/model-convert-three.mjs"),cwd.resolve("../shixun-vue/scripts/model-convert-three.mjs"),cwd.resolve("scripts/model-convert-three.mjs"));
        for(Path p:candidates) if(Files.exists(p)) return p;
        throw new IOException("找不到 Node 模型转换脚本 shixun-vue/scripts/model-convert-three.mjs");
    }

    private void zipDirectory(Path dir,Path zipFile) throws IOException {
        try(ZipOutputStream zos=new ZipOutputStream(Files.newOutputStream(zipFile)); Stream<Path> paths=Files.walk(dir)) {
            Iterator<Path> it=paths.filter(Files::isRegularFile).iterator();
            while(it.hasNext()) {
                Path file=it.next();
                String name=dir.relativize(file).toString().replace('\\','/');
                zos.putNextEntry(new ZipEntry(name));
                Files.copy(file,zos);
                zos.closeEntry();
            }
        }
    }

    private void deleteDirectoryQuietly(Path dir) {
        try(Stream<Path> paths=Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});
        } catch(Exception ignored) {}
    }

    private Map<String,Object> convertTripoModelFormat(Map<String,Object> source,String fmt) throws Exception {
        if(blank(tripoApiKey)||tripoApiKey.contains("YOUR_")) throw new IllegalStateException("未配置 tripo.api.key，无法转换模型格式");
        String taskId=extractTaskId(source.get("metadataJson"));
        if(blank(taskId)) throw new IllegalStateException("旧模型缺少Tripo任务ID，暂不能在线转换为 "+fmt+"；请重新生成模型后下载该格式");
        Map<String,Object> body=new LinkedHashMap<>(); body.put("type","convert_model"); body.put("format",fmt); body.put("original_model_task_id",taskId);
        String raw=tripoConvertJson("POST","/task",mapper.writeValueAsString(body)); JsonNode root=mapper.readTree(raw); ensureTripoOk(root,raw);
        String convertTaskId=root.path("data").path("task_id").asText(root.path("data").path("taskId").asText(""));
        if(blank(convertTaskId)) throw new IllegalStateException("Tripo未返回格式转换任务ID："+raw);
        String remoteModel=""; String preview=str(source.get("previewUrl"));
        for(int i=0;i<60;i++){
            Thread.sleep(2000);
            String check=tripoConvertJson("GET","/task/"+URLEncoder.encode(convertTaskId,StandardCharsets.UTF_8),null);
            JsonNode checkRoot=mapper.readTree(check); ensureTripoOk(checkRoot,check); JsonNode data=checkRoot.path("data");
            String status=mapTripoStatus(data.path("status").asText("running"));
            if("failed".equals(status)) throw new IllegalStateException("Tripo模型格式转换失败："+data.path("error").asText(data.path("message").asText(check)));
            if("succeeded".equals(status)){
                JsonNode output=data.path("output");
                remoteModel=firstUrl(output,"model","model_url","download_url","url","result","base_model","pbr_model",fmt.toLowerCase(Locale.ROOT)+"_model","model_urls");
                if(blank(remoteModel)) remoteModel=firstUrl(data,"model","model_url","download_url","url","result");
                break;
            }
        }
        if(blank(remoteModel)) throw new IllegalStateException("Tripo模型格式转换超时，请稍后重试下载 "+fmt);
        String defaultSuffix="OBJ".equals(fmt)?".zip":"."+fmt.toLowerCase(Locale.ROOT);
        String localModel=saveRemoteFile(remoteModel,"tripo-"+fmt.toLowerCase(Locale.ROOT)+"-",suffixFromUrl(remoteModel,defaultSuffix),"models");
        Long sourceId=((Number)source.get("id")).longValue();
        Map<String,Object> meta=new LinkedHashMap<>(); meta.put("provider","tripo"); meta.put("convertedFromAssetId",sourceId); meta.put("sourceTaskId",taskId); meta.put("conversionTaskId",convertTaskId); meta.put("remoteModel",remoteModel); meta.put("format",fmt);
        if (source.get("createdBy") instanceof Number) meta.put("createdByUserId", ((Number) source.get("createdBy")).longValue());
        Long assetId=createAsset("3D模型 "+fmt+"格式","model","converted",localModel,blank(preview)?null:preview,str(source.get("prompt")),null,null,sourceId,fmt.toLowerCase(Locale.ROOT),"3D模型,格式转换,"+fmt,meta);
        return jdbc.queryForMap("SELECT id,asset_no assetNo,title,asset_type assetType,file_url fileUrl,preview_url previewUrl,prompt,parent_asset_id parentAssetId,format,tags,metadata_json metadataJson FROM digital_asset WHERE id=?",assetId);
    }

    private String extractTaskId(Object metadataJson) {
        try {
            if(metadataJson==null||blank(String.valueOf(metadataJson))) return "";
            JsonNode n=mapper.readTree(String.valueOf(metadataJson));
            return firstNonBlank(n.path("taskId").asText(""),n.path("sourceTaskId").asText(""),n.path("externalTaskId").asText(""));
        } catch(Exception ignored) { return ""; }
    }

    private String firstNonBlank(String... values) { for(String v:values) if(!blank(v)) return v; return ""; }

    private ResponseEntity<Resource> modelDownloadResponse(Map<String,Object> asset,String fmt) throws Exception {
        String url=str(asset.get("fileUrl")); if(blank(url)) throw new IOException("模型文件地址不存在");
        Resource body; long contentLength=-1; String lower=url.toLowerCase(Locale.ROOT);
        if(url.startsWith("http://")||url.startsWith("https://")){
            HttpResponse<byte[]> response=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()<200||response.statusCode()>=300) throw new IOException("读取模型失败 HTTP "+response.statusCode());
            byte[] bytes=response.body();
            body=new ByteArrayResource(bytes);
            contentLength=bytes.length;
        } else {
            Path file=resolvePublicAssetFile(url,"模型文件不存在：");
            body=new FileSystemResource(file);
            contentLength=Files.size(file);
            lower=file.getFileName().toString().toLowerCase(Locale.ROOT);
        }
        MediaType type=lower.endsWith(".zip")?MediaType.parseMediaType("application/zip"):"STL".equals(fmt)?MediaType.parseMediaType("model/stl"):"OBJ".equals(fmt)?MediaType.parseMediaType("model/obj"):MediaType.parseMediaType("model/gltf-binary");
        String suffix=lower.endsWith(".zip")?".zip":"."+fmt.toLowerCase(Locale.ROOT);
        String filename="and-taste-3d-"+asset.get("id")+"-"+fmt.toLowerCase(Locale.ROOT)+suffix;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentLength(contentLength).contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"").body(body);
    }

    public static class ReviewRequest {
        public Long assetId;
        public String context;
    }

    @PostMapping("/production-feasibility")
    public Map<String, Object> productionFeasibility(@RequestBody Map<String, Object> body) {
        String category = nullToEmpty(String.valueOf(body.getOrDefault("productCategory", "")));
        String material = nullToEmpty(String.valueOf(body.getOrDefault("material", "")));
        String prompt = nullToEmpty(String.valueOf(body.getOrDefault("prompt", "")));
        ProductPromptPolicy.Profile productPolicy = ProductPromptPolicy.resolve(category, material);
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        int score = 92;
        String all = (category + " " + material + " " + prompt).toLowerCase(Locale.ROOT);
        if (productPolicy.edible()) {
            suggestions.add("食品类必须由生产方确认食品级原料、配料/过敏原、净含量、保质期、包装和冷链条件；AI提示词不能替代食品合规审核。 ");
            if (all.contains("金属") || all.contains("合金") || all.contains("塑料") || all.contains("树脂") || all.contains("徽章")) {
                score -= 25;
                issues.add("食品产品描述混入了非食品材质或饰品结构，可能导致生成结果不可食用。 ");
            }
        }
        if (all.contains("细线") || all.contains("极细") || all.contains("发丝") || all.contains("thin line")) { score -= 14; issues.add("存在过细线条风险，量产时易断裂或丢失细节。"); suggestions.add("将关键线条加粗，并在打样图上标注最小线宽。 "); }
        if (all.contains("悬空") || all.contains("floating") || all.contains("漂浮")) { score -= 14; issues.add("存在悬空/细连接结构，运输和开模风险较高。"); suggestions.add("增加底座、支撑或改成独立分件。 "); }
        if (all.contains("倒扣") || all.contains("undercut")) { score -= 12; issues.add("描述包含倒扣结构，可能需要滑块或调整分件。"); suggestions.add("在打样前由工艺人员确认拔模方向与分型线。 "); }
        if (material.contains("毛绒")) { suggestions.add("毛绒建议使用刺绣五官和独立布料裁片，避免把复杂图案直接做成长毛印花。 "); }
        if (material.contains("PVC") || material.contains("搪胶") || material.contains("PPC") || material.contains("硬塑")) { suggestions.add("注塑/搪胶件建议预留合理壁厚、圆角与分件位；该结果仅为系统初筛。 "); }
        if (category.contains("冰箱贴")) suggestions.add("冰箱贴请在背面预留平整磁铁位，并避免过薄边缘。 ");
        if (productPolicy.key().equals("metal")) suggestions.add("金属类请在生产图中明确厚度、圆角、分型线和背面连接结构，再由工厂确认模具与表面处理。 ");
        if (productPolicy.key().equals("paper")) suggestions.add("纸品请确认成品尺寸、出血、纸张克重、折线、覆膜和印刷色差，避免把平面稿直接当成结构模型。 ");
        if (productPolicy.key().equals("canvas")) suggestions.add("帆布/纺织品请确认裁片、缝线、印花安全边距、提手或拉链结构，最终以打样实物确认。 ");
        if (productPolicy.key().equals("tableware")) suggestions.add("杯具/餐具请确认食品接触面、釉面/搪瓷工艺、容积、壁厚和耐热要求。 ");
        if (issues.isEmpty()) issues.add("未识别到明显的文字描述风险，仍需以3D结构、尺寸和真实打样为准。 ");
        String level = score >= 85 ? "可进入打样准备" : score >= 65 ? "建议工艺复核" : "建议先修改方案";
        return Map.of("score", Math.max(35, score), "level", level, "issues", issues, "suggestions", suggestions,
                "productPolicy", productPolicy.key(), "productRules", productPolicy.positiveLock(),
                "disclaimer", "这是基于文字描述的生产可行性初筛，不构成报价、质检承诺或正式工艺结论；量产前须由工厂/人工专业人员确认。");
    }

    @PostMapping("/consumer/copyright-consultations")
    public Map<String, Object> createCopyrightConsultation(@RequestBody Map<String, Object> body) {
        Long userId = requireCurrentConsumerUser();
        String service = nullToEmpty(String.valueOf(body.getOrDefault("service", "")));
        if (blank(service)) throw new IllegalArgumentException("请选择确权服务");
        Object assetValue = body.get("assetId"); Long assetId = assetValue instanceof Number ? ((Number) assetValue).longValue() : null;
        if (assetId != null) requireAssetAccess(assetId);
        jdbc.update("INSERT INTO creative_rights_consultation (user_id,asset_id,service_type,note) VALUES (?,?,?,?)", userId, assetId, service, nullToEmpty(String.valueOf(body.getOrDefault("note", ""))));
        return Map.of("message", "版权服务咨询已登记，平台人员将按协议与您核对材料。该入口不等同于已完成登记或授权。", "status", "pending");
    }

    private void assertCompliantPrompt(String prompt, String category) {
        String normalized = (nullToEmpty(prompt) + " " + nullToEmpty(category)).toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        List<String> prohibited = List.of("裸照", "色情", "色情网", "强奸", "毒品", "爆炸物", "制枪", "恐怖袭击", "未成年性", "deepfake porn");
        List<String> rightsRisk = List.of("明星同款", "明星脸", "肖像复刻", "未经授权", "迪士尼", "漫威", "宝可梦", "皮卡丘", "哆啦a梦", "奥特曼", "hello kitty");
        for (String keyword : prohibited) if (normalized.contains(keyword)) throw new IllegalArgumentException("当前描述包含平台禁止内容，请修改后重试。");
        for (String keyword : rightsRisk) if (normalized.contains(keyword)) throw new IllegalArgumentException("当前描述可能涉及未授权人物肖像、商标或IP，请提供授权证明或改用原创描述。");
    }

    public static class GenerateImageRequest {
        public String title;
        public String provider;
        public String prompt;
        public String negativePrompt;
        public Long styleId;
        public String scene;
        public String productType;
        public String productCategory;
        public String productKey;
        public String material;
        public String imageSize;
        public Long seed;
        public String tags;
        public Long inputAssetId;
        public Boolean refinement;
        public String refinementNote;
        public String tripoImageModel;
        public String tripoTemplate;
        public Boolean tPose;
        public Boolean sketchToRender;
        public String imagenAspectRatio;
        public String imagenImageSize;
        public String imagenOutputFormat;
        /** Opt in to the durable image task queue; retained as an opt-in for older clients. */
        public Boolean queue;
        public Long currentUserId;
    }
    public static class MultiViewImageRequest {
        public String prompt;
        public String productCategory;
        public String productKey;
        public String material;
        public Long inputAssetId;
        public String size;
        /** Conversational creation uses 3 views; the professional page keeps 4. */
        public Integer viewCount;
        public Boolean watermark;
        /** Opt in to the durable image task queue; retained as an opt-in for older clients. */
        public Boolean queue;
        public Long currentUserId;
    }

    public static class Generate3dRequest {
        public String mode;
        public String modelVersion;
        public String promptTemplate;
        public String prompt;
        public String negativePrompt;
        // 材质为“视觉/PBR 表面质感”偏好；文本建模会注入提示词，图/多视图建模会随任务记录保存。
        public String materialLabel;
        public String materialPrompt;
        public String material;
        public String productCategory;
        public String productKey;
        public Long inputAssetId;
        public Map<String,Long> multiviewAssetIds;
        public String exportFormats;
        public Boolean texture;
        public Boolean pbr;
        public String textureQuality;
        public String geometryQuality;
        public String textureAlignment;
        public String orientation;
        public Boolean autoSize;
        public Boolean imageAutofix;
        public Boolean quad;
        public Boolean smartLowPoly;
        public Boolean generateParts;
        public Boolean exportUv;
        public Boolean compress;
        public Integer faceLimit;
        public Long modelSeed;
        public Long imageSeed;
        public Long textureSeed;
        public Long currentUserId;
    }
}
