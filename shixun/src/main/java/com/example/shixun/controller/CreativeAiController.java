package com.example.shixun.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
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
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/creative/ai")
@CrossOrigin(origins = "*")
public class CreativeAiController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).followRedirects(HttpClient.Redirect.NORMAL).build();

    @Value("${siliconflow.api.key:}")
    private String siliconflowApiKey;

    @Value("${siliconflow.image.model:Kwai-Kolors/Kolors}")
    private String imageModel;

    @Value("${siliconflow.image.edit.model:Qwen/Qwen-Image-Edit-2509}")
    private String imageEditModel;

    @Value("${siliconflow.chat.model:Qwen/Qwen3-32B}")
    private String chatModel;

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

    public CreativeAiController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.jdbc.execute("CREATE TABLE IF NOT EXISTS design_review_report (id BIGINT AUTO_INCREMENT PRIMARY KEY, review_id BIGINT NOT NULL UNIQUE, report_json JSON NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP) COMMENT='智能评估完整报告留存'");
        try { this.jdbc.execute("ALTER TABLE digital_asset ADD COLUMN created_by BIGINT NULL"); } catch (Exception ignored) {}
        try { this.jdbc.execute("ALTER TABLE ai_generation_job ADD COLUMN created_by BIGINT NULL"); } catch (Exception ignored) {}
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String,Object> businessError(RuntimeException e) {
        return Map.of("success", false, "message", e.getMessage() == null ? "请求处理失败" : e.getMessage());
    }

    @GetMapping("/styles")
    public List<Map<String, Object>> styles() {
        return jdbc.queryForList("SELECT id, name, description, base_prompt basePrompt, negative_prompt negativePrompt, palette, cultural_guardrails culturalGuardrails FROM brand_style_profile WHERE enabled=1 ORDER BY id");
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
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写基础3D模型描述");
        String template = normalizeTripo3dTemplate(req.promptTemplate);
        String system = "You are a senior Tripo text-to-3D prompt engineer. Rewrite the user's rough idea into a high-detail English prompt for Tripo text-to-model. "
                + "Output JSON only with keys: prompt, negativePrompt, usageTips. No Markdown. "
                + "The prompt value must be English only; translate all Chinese product names, place names, materials, patterns and style words into natural English. Do not include Chinese characters in prompt unless the user explicitly requests visible Chinese label text on the model. "
                + "The usageTips value must be Chinese, short and practical for the operator. "
                + "The prompt must preserve the user's subject and practical use, avoid abstract adjectives alone, and describe concrete geometry, silhouette, materials, surface details, topology and production-ready 3D asset qualities. "
                + "Always include clean topology, watertight mesh, no floating parts, ultra-detailed 3D asset, sharp geometry, 8k PBR textures, professional product visualization. "
                + "Negative prompt should include low poly, blurry, flat texture, deformed, asymmetric, noisy mesh, broken topology, floating parts. "
                + "Selected template: " + tripo3dTemplateName(template) + ". Template rules: " + tripo3dTemplateInstruction(template);
        String content = callChat(system, req.prompt.trim()).trim();
        String optimized;
        String negative = "low poly, blurry, flat texture, deformed, asymmetric, noisy mesh, broken topology, floating parts, melted details, plastic look";
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
        return Set.of("fantasy", "hard_surface", "oriental", "collectible", "universal").contains(t) ? t : "universal";
    }

    private String tripo3dTemplateName(String template) {
        return switch (normalizeTripo3dTemplate(template)) {
            case "fantasy" -> "史诗级奇幻/角色（高细节雕刻感）";
            case "hard_surface" -> "硬核科幻/机械（高精度硬表面）";
            case "oriental" -> "东方美学/国风（纹样与釉色）";
            case "collectible" -> "潮玩/IP 手办（精致涂装与微缩感）";
            default -> "万能产品模板（填空即用）";
        };
    }

    private String tripo3dTemplateInstruction(String template) {
        return switch (normalizeTripo3dTemplate(template)) {
            case "fantasy" -> "Use ancient relic, creature, statue or armor language. Emphasize intricate carvings, rune engravings, weathered stone, gold filigree, ornamentation, volumetric lighting, museum quality artifact, photorealistic PBR materials.";
            case "hard_surface" -> "Use hard-surface industrial design language. Emphasize beveled panels, seams, exposed hydraulic pistons, wiring, greeble details, brushed titanium, carbon fiber, ultra-sharp edges, studio lighting, 4k/8k texture fidelity.";
            case "oriental" -> "Use Chinese/Eastern craft language. Emphasize cloisonné enamel, filigree wirework, glossy ceramic glaze, crackle finish, jade finial, carved relief patterns, traditional craftsmanship, cultural heritage artifact.";
            case "collectible" -> "Use collectible toy / GK figurine language. Emphasize cute stylized proportions, miniature accessories, hand-painted resin texture, matte finish, metallic accents, tilt-shift product photography, softbox lighting, extremely fine surface details.";
            default -> "Use the structure: A [adjective] [subject] made of [primary material] and [secondary material], featuring [specific surface detail/pattern], [art style] aesthetic, [lighting type] lighting, ultra-detailed 3D asset, 8k PBR textures, sharp geometry, professional product visualization.";
        };
    }

    private String tripo3dTemplateTips(String template) {
        return switch (normalizeTripo3dTemplate(template)) {
            case "fantasy" -> "适合怪物、雕像、复杂盔甲、文物感摆件。建议描述具体雕刻、镶嵌、风化、符文和凹凸纹理，避免只写“酷/漂亮”。";
            case "hard_surface" -> "适合机甲、武器、设备和工业产品。建议强调倒角、接缝、螺丝、液压、线缆、拉丝金属和硬表面分件。";
            case "oriental" -> "适合国风器物、瓷器、景泰蓝、文博衍生品。材质要写具体：釉面、开片、掐丝、玉石、金属包边，避免塑料感。";
            case "collectible" -> "适合盲盒、IP手办、钥匙扣和微缩摆件。建议写清比例、姿态、涂装、配件、底座和哑光/金属局部材质。";
            default -> "适合普通产品快速转3D。先生成基础形态，再追加材质、纹样、PBR、clean topology、watertight mesh 等细节词进行二次优化。";
        };
    }

    @PostMapping("/prompt/tripo-optimize")
    public Map<String,Object> optimizeTripoImagePrompt(@RequestBody GenerateImageRequest req) throws Exception {
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写基础创意描述");
        String provider = nullToEmpty(req.provider).toLowerCase(Locale.ROOT);
        String system = "You are a senior English prompt writer for premium AI image generation, specializing in cinematic commercial product photography, official brand visuals, cultural creative products, packaging concepts, and realistic lifestyle scenes. "
                + "Rewrite the user's Chinese or rough idea into ONE polished English image-generation prompt. Output the final English prompt only: no title, no explanation, no negative prompt, no Markdown, no Chinese characters unless the user explicitly asks for visible Chinese text printed on the product. "
                + "Use this reference template and tone: A photo of a computer screen displaying a Spotify playlist during golden-hour evening light in a living room with many green plants in the background. The playlist says GPT-image-2. The caption is \"this new image model from OpenAI is dope.\" The artists are Replicate. The songs are themed around open-source AI and machine learning. The account name is Replicate. Use the Replicate logo as the profile picture and artist image. "
                + "Follow the same structure: clear photographic subject, specific environment, warm cinematic lighting, exact visible text when provided, brand/profile/logo placement when relevant, detailed product or interface contents, realistic background objects, premium composition, shallow depth of field, tactile materials, sharp focal details, official and trustworthy visual tone. "
                + "Preserve the user's actual product, place, cultural theme, brand elements, materials, colors, label text, audience, and use case. If the user provides Chinese product/region names, translate them naturally into English unless they are meant to appear as printed text. "
                + "For packaging or product concepts, describe the package shape, paper/plastic/metal/ceramic texture, typography, illustration style, net weight or label copy if supplied, countertop/tabletop/studio setting, lens, depth of field, and commercial product-shot quality. "
                + "Keep it concise but rich, within 900 English words. Target provider: " + (blank(provider) ? "general" : provider) + ".";
        String optimized=callChat(system,req.prompt.trim()).trim();
        int maxPromptLength = "imagen".equalsIgnoreCase(nullToEmpty(req.provider)) ? 1800 : 1024;
        if(optimized.length()>maxPromptLength)optimized=optimized.substring(0,maxPromptLength);
        String usageGuide = buildProductUsageGuide(req, optimized);
        return Map.of(
                "prompt", optimized,
                "usageGuide", usageGuide,
                "source", "siliconflow:" + chatModel,
                "target", switch (provider) { case "jimeng" -> "jimeng-seedream-4.6:text-to-image"; case "imagen" -> "google-imagen-4:text-to-image"; case "modao" -> "modao:text-to-image"; default -> "tripo:text-to-image"; }
        );
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
        Map<String, Object> style = style(req.styleId);
        String finalPrompt = buildPrompt(req.prompt, style, req.scene, req.productType);
        String negative = mergeNegative(req.negativePrompt, (String) style.get("negativePrompt"));
        String jobNo = no("AIG");
        Long jobId = createJob(jobNo, "text_to_image", "siliconflow", imageModel, req.styleId, null, finalPrompt, negative, "running", null, null);
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
            Long assetId = createAsset(req.title == null || req.title.isBlank() ? "AI生成图片" : req.title, "image", "ai_generated", localUrl, localUrl, finalPrompt, negative, req.styleId, null, "png", req.tags, Map.of("provider", "siliconflow", "model", imageModel, "remoteUrl", remoteUrl));
            jdbc.update("UPDATE ai_generation_job SET status='succeeded', output_asset_id=? WHERE id=?", assetId, jobId);
            return Map.of("jobNo", jobNo, "assetId", assetId, "imageUrl", localUrl, "previewUrl", localUrl, "fileUrl", localUrl, "prompt", finalPrompt, "negativePrompt", negative, "status", "succeeded", "source", "siliconflow:" + imageModel, "model", imageModel);
        } catch (Exception e) {
            jdbc.update("UPDATE ai_generation_job SET status='failed', error_message=? WHERE id=?", e.getMessage(), jobId);
            throw e;
        }
    }

    @PostMapping("/image-to-image")
    public Map<String, Object> imageToImage(@RequestBody GenerateImageRequest req) throws Exception {
        if (req.inputAssetId == null) throw new IllegalArgumentException("请先选择一张参考图");
        Map<String, Object> style = style(req.styleId);
        String finalPrompt = buildPrompt(req.prompt, style, req.scene, req.productType);
        String negative = mergeNegative(req.negativePrompt, (String) style.get("negativePrompt"));
        String jobNo = no("I2I");
        Long jobId = createJob(jobNo, "image_to_image", "siliconflow", imageEditModel, req.styleId, req.inputAssetId, finalPrompt, negative, "running", null, null);
        try {
            if (siliconflowApiKey == null || siliconflowApiKey.trim().isEmpty() || siliconflowApiKey.contains("YOUR_")) {
                throw new IllegalStateException("未配置 siliconflow.api.key，请在 shixun/application-local.properties 配置");
            }
            String inputImage = buildInputImageForSiliconFlow(req.inputAssetId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", imageEditModel);
            payload.put("prompt", finalPrompt);
            payload.put("image", inputImage);
            if (negative != null && !negative.isBlank()) payload.put("negative_prompt", negative);
            payload.put("batch_size", 1);
            if (req.seed != null) payload.put("seed", req.seed);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.siliconflow.cn/v1/images/generations"))
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
                    Map.of("provider", "siliconflow", "model", imageEditModel, "remoteUrl", remoteUrl, "inputAssetId", req.inputAssetId)
            );
            jdbc.update("UPDATE ai_generation_job SET status='succeeded', output_asset_id=? WHERE id=?", assetId, jobId);
            return Map.of("jobId", jobId, "jobNo", jobNo, "assetId", assetId, "imageUrl", localUrl, "prompt", finalPrompt, "negativePrompt", negative, "status", "succeeded");
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
        if(blank(jimengAccessKeyId) || blank(jimengSecretAccessKey)) throw new IllegalStateException("即梦视觉接口需要火山引擎 AccessKeyId + SecretAccessKey 签名鉴权，不支持直接使用 Vx 开头的 API Key。请在 shixun/application-local.properties 配置 jimeng.access-key-id 和 jimeng.secret-access-key");
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写或生成生图提示词");
        String prompt = req.prompt.trim();
        if(prompt.length() > 2000) prompt = prompt.substring(0, 2000);
        String aspect = Set.of("1:1","16:9","9:16","4:3","3:4").contains(nullToEmpty(req.imagenAspectRatio)) ? req.imagenAspectRatio : "1:1";
        String size = Set.of("1K","2K").contains(nullToEmpty(req.imagenImageSize)) ? req.imagenImageSize : "1K";
        String format = Set.of("png","jpg").contains(nullToEmpty(req.imagenOutputFormat).toLowerCase(Locale.ROOT)) ? req.imagenOutputFormat.toLowerCase(Locale.ROOT) : "png";
        int[] wh = jimengDimensions(aspect, size);
        String finalPrompt = buildJimengPrompt(prompt);
        String jobNo = no("JMG");
        Long jobId = createJob(jobNo, "text_to_image", "jimeng", jimengReqKey, req.styleId, null, prompt, req.negativePrompt, "running", null, size + " " + aspect);
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
        if(blank(replicateApiKey) || replicateApiKey.contains("YOUR_")) throw new IllegalStateException("未配置 Replicate API Key：请在 shixun/application-local.properties 配置 replicate.api.key");
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写或生成生图提示词");
        String prompt = req.prompt.trim();
        if(prompt.length() > 2000) prompt = prompt.substring(0, 2000);
        String aspect = Set.of("1:1","16:9","9:16","4:3","3:4").contains(nullToEmpty(req.imagenAspectRatio)) ? req.imagenAspectRatio : "1:1";
        String size = Set.of("1K","2K").contains(nullToEmpty(req.imagenImageSize)) ? req.imagenImageSize : "1K";
        String format = Set.of("png","jpg").contains(nullToEmpty(req.imagenOutputFormat).toLowerCase(Locale.ROOT)) ? req.imagenOutputFormat.toLowerCase(Locale.ROOT) : "png";
        String imagenPrompt = buildImagenPrompt(prompt);
        String jobNo = no("IMG");
        Long jobId = createJob(jobNo, "text_to_image", "replicate", replicateImagenModel, req.styleId, null, prompt, req.negativePrompt, "running", null, size + " " + aspect);
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
            if(req.currentUserId!=null){meta.put("createdByUserId",req.currentUserId);meta.put("consumerWork",true);}
            Long assetId = createAsset("Google Imagen 4 2D创意图", "image", "ai_generated", localImage, localImage, prompt, req.negativePrompt, req.styleId, null, format, "Google Imagen 4,Replicate,2D创意生图,AI生成", meta);
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
            out.put("imageUrl", localImage);
            out.put("previewUrl", localImage);
            out.put("fileUrl", localImage);
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
        if(blank(modaoApiKey) || !modaoApiKey.startsWith("modao_")) throw new IllegalStateException("未配置墨刀令牌 modao.api.key，请在 shixun/application-local.properties 配置");
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写或生成设计提示词");
        String prompt = req.prompt.trim();
        if(prompt.length() > 2000) prompt = prompt.substring(0, 2000);
        String jobNo = no("MDA");
        Long jobId = createJob(jobNo, "text_to_image", "modao", "modao-generate-image", req.styleId, null, prompt, req.negativePrompt, "running", null, req.imageSize);
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
            Long assetId = createAsset("墨刀AI 2D设计图", "image", "ai_generated", localImage, localImage, prompt, req.negativePrompt, req.styleId, null, "png", "墨刀,2D创意生图,AI生成", meta);
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,external_task_id=?,status='succeeded',progress=100,error_message=NULL WHERE id=?", assetId, blank(key)?"modao-generate-image":key, jobId);
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("jobId", jobId);
            out.put("jobNo", jobNo);
            out.put("provider", "modao");
            out.put("status", "succeeded");
            out.put("progress", 100);
            out.put("assetId", assetId);
            out.put("imageUrl", localImage);
            out.put("previewUrl", localImage);
            out.put("fileUrl", localImage);
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
        result.put("qualityPreset", "ultra");
        result.put("geometryQuality", "detailed");
        result.put("textureQuality", "extreme");
        result.put("maxFaceLimit", 2_000_000);
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
        if(blank(tripoApiKey) || tripoApiKey.contains("YOUR_")) throw new IllegalStateException("未配置Tripo API Key");
        if(blank(req.prompt)) throw new IllegalArgumentException("请先填写或生成生图提示词");
        if(req.prompt.trim().length()>1024) throw new IllegalArgumentException("Tripo生图提示词不能超过1024个字符");
        String model=Set.of("seedream_v5","seedream_v4","banana","banana_pro","banana2","chat_image_1","chat_image_1.5","chat_image_2").contains(req.tripoImageModel)?req.tripoImageModel:"seedream_v5";
        Map<String,Object> body=new LinkedHashMap<>(); body.put("prompt",req.prompt.trim()); body.put("model",model);
        if(!blank(req.tripoTemplate)) body.put("template",req.tripoTemplate.trim());
        if(Boolean.TRUE.equals(req.tPose)) body.put("t_pose",true);
        if(Boolean.TRUE.equals(req.sketchToRender)) body.put("sketch_to_render",true);
        String raw=tripoJson("POST","/generation/text-to-image",mapper.writeValueAsString(body)); JsonNode root=mapper.readTree(raw); ensureTripoOk(root,raw);
        String taskId=root.path("data").path("task_id").asText(""); if(blank(taskId))throw new IllegalStateException("Tripo文本生图未返回task_id："+raw);
        String jobNo=no("T2D"); Long jobId=createJob(jobNo,"text_to_image","tripo",model,req.styleId,null,req.prompt,req.negativePrompt,"running",null,req.imageSize);
        jdbc.update("UPDATE ai_generation_job SET external_task_id=?,progress=0 WHERE id=?",taskId,jobId);
        return Map.of("jobId",jobId,"jobNo",jobNo,"taskId",taskId,"status","running","progress",0,"provider","tripo","model",model,"message","Tripo文本生图任务已提交");
    }

    @GetMapping("/tripo/image-tasks/{jobId}")
    public synchronized Map<String,Object> tripoImageTask(@PathVariable Long jobId) throws Exception {
        Map<String,Object> job=jdbc.queryForMap("SELECT id,job_no jobNo,external_task_id externalTaskId,output_asset_id outputAssetId,status,progress,error_message errorMessage,prompt,negative_prompt negativePrompt,style_id styleId,model_name modelName FROM ai_generation_job WHERE id=? AND provider='tripo'",jobId);
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
            Long assetId=createAsset("Tripo 2D创意图","image","ai_generated",localImage,localImage,str(job.get("prompt")),str(job.get("negativePrompt")),styleId,null,suffixFromUrl(imageUrl,".png").replace(".",""),"Tripo,2D创意生图,AI生成",meta);
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,status='succeeded',progress=100,error_message=NULL WHERE id=?",assetId,jobId);
            job=jdbc.queryForMap("SELECT id,job_no jobNo,external_task_id externalTaskId,output_asset_id outputAssetId,status,progress,error_message errorMessage,model_name modelName FROM ai_generation_job WHERE id=?",jobId);
            return completedTripoImageJob(jobId,job);
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("jobId",jobId);out.put("jobNo",job.get("jobNo"));out.put("taskId",taskId);out.put("status",localStatus);out.put("remoteStatus",remoteStatus);out.put("progress",progress);out.put("errorMessage",error);out.put("model",job.get("modelName"));return out;
    }

    @PostMapping({"/tripo/generate", "/tripo/image-to-3d"})
    public Map<String,Object> tripoGenerate(@RequestBody Generate3dRequest req) throws Exception {
        if(blank(tripoApiKey) || tripoApiKey.contains("YOUR_"))
            throw new IllegalStateException("未配置 tripo.api.key，请在服务器.env中填写TRIPO_API_KEY后重新部署");

        String mode = blank(req.mode) ? "image_to_model" : req.mode.trim();
        if(!Set.of("image_to_model", "multiview_to_model", "text_to_model").contains(mode))
            throw new IllegalArgumentException("不支持的Tripo生成模式：" + mode);

        String selectedModel=blank(req.modelVersion)?tripoModelVersion:req.modelVersion.trim();
        Set<String> supportedModels=Set.of("P1-20260311","tripo-p1","tripo-v3.1","v3.1-20260211","tripo-v3.0","v3.0-20250812","tripo-v2.5","v2.5-20250123");
        if(!supportedModels.contains(selectedModel))throw new IllegalArgumentException("不支持的Tripo 3D模型："+selectedModel);
        Map<String,Object> taskBody = new LinkedHashMap<>();
        taskBody.put("model", selectedModel);
        Long primaryInputAssetId = req.inputAssetId;

        if("text_to_model".equals(mode)) {
            if(blank(req.prompt)) throw new IllegalArgumentException("文生3D模式必须填写模型描述");
            if(req.prompt.trim().length() > 1024) throw new IllegalArgumentException("模型描述不能超过1024个字符");
            if(!blank(req.negativePrompt) && req.negativePrompt.trim().length() > 255) throw new IllegalArgumentException("反向提示词不能超过255个字符");
            taskBody.put("prompt", req.prompt.trim());
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
                Path image = resolveAssetImage(assetId);
                inputs.add(Map.of(view, uploadToTripo(image)));
                if(primaryInputAssetId == null) primaryInputAssetId = assetId;
            }
            taskBody.put("inputs", inputs);
        } else {
            if(req.inputAssetId == null) throw new IllegalArgumentException("请先上传2D参考图");
            Path image = resolveAssetImage(req.inputAssetId);
            taskBody.put("input", uploadToTripo(image));
        }

        applyTripoQualityOptions(taskBody, req, mode, selectedModel);
        String generationPath = "text_to_model".equals(mode) ? "/generation/text-to-model" :
                "multiview_to_model".equals(mode) ? "/generation/multiview-to-model" : "/generation/image-to-model";
        String taskResponse = tripoJson("POST", generationPath, mapper.writeValueAsString(taskBody));
        JsonNode root = mapper.readTree(taskResponse);
        ensureTripoOk(root, taskResponse);
        String taskId = root.path("data").path("task_id").asText(root.path("data").path("taskId").asText(""));
        if(blank(taskId)) throw new IllegalStateException("Tripo未返回task_id：" + taskResponse);

        String jobNo = no("T3D");
        Long jobId = createJob(jobNo, mode, "tripo", selectedModel, null,
                primaryInputAssetId, req.prompt, req.negativePrompt, "running", null,
                Boolean.TRUE.equals(req.quad) ? "FBX" : (blank(req.exportFormats) ? "GLB" : req.exportFormats));
        assignJobOwner(jobId, req.currentUserId);
        jdbc.update("UPDATE ai_generation_job SET external_task_id=?,progress=0 WHERE id=?", taskId, jobId);
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("jobId", jobId); response.put("jobNo", jobNo); response.put("taskId", taskId);
        response.put("status", "running"); response.put("progress", 0); response.put("provider", "tripo");
        response.put("modelVersion", selectedModel); response.put("qualityPreset", isPSeriesModel(selectedModel)?"p-series":"standard");
        response.put("message", "Tripo "+selectedModel+"任务已提交");
        return response;
    }

    private Path resolveAssetImage(Long assetId) throws IOException {
        Map<String,Object> asset = jdbc.queryForMap("SELECT file_url fileUrl,preview_url previewUrl FROM digital_asset WHERE id=?", assetId);
        Object url = asset.get("fileUrl") == null ? asset.get("previewUrl") : asset.get("fileUrl");
        return resolvePublicAsset(String.valueOf(url));
    }

    private void applyTripoQualityOptions(Map<String,Object> body, Generate3dRequest req, String mode, String model) {
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
        Map<String,Object> job=jdbc.queryForMap("SELECT id,job_no jobNo,external_task_id externalTaskId,input_asset_id inputAssetId,output_asset_id outputAssetId,status,progress,error_message errorMessage,created_by createdBy FROM ai_generation_job WHERE id=?",jobId);
        String taskId=str(job.get("externalTaskId")); if(blank(taskId))throw new IllegalStateException("任务没有Tripo task_id");
        if(job.get("outputAssetId")!=null) return completedTripoJob(jobId,job);
        String response=tripoJson("GET","/tasks/"+URLEncoder.encode(taskId,StandardCharsets.UTF_8),null);
        JsonNode root=mapper.readTree(response); ensureTripoOk(root,response); JsonNode data=root.path("data");
        String remoteStatus=data.path("status").asText("unknown"); int progress=data.path("progress").asInt(0);
        String localStatus=mapTripoStatus(remoteStatus); String error=data.path("error").asText(data.path("message").asText(""));
        if(!"succeeded".equals(localStatus)) jdbc.update("UPDATE ai_generation_job SET status=?,progress=?,error_message=? WHERE id=?",localStatus,progress,blank(error)?null:error,jobId);
        if("succeeded".equals(localStatus)) {
            JsonNode output=data.path("output"); String modelUrl=firstUrl(output,"model_url","pbr_model","model","base_model","glb_model","model_urls"); String previewUrl=firstUrl(output,"rendered_image_url","rendered_image","image","preview_image");
            if(blank(modelUrl)) throw new IllegalStateException("Tripo任务成功但没有返回模型地址："+response);
            String localModel=saveRemoteFile(modelUrl,"tripo-model-",suffixFromUrl(modelUrl,".glb"),"models");
            String localPreview=blank(previewUrl)?null:saveRemoteFile(previewUrl,"tripo-preview-",suffixFromUrl(previewUrl,".webp"),"models");
            Long inputId=job.get("inputAssetId") instanceof Number ? ((Number)job.get("inputAssetId")).longValue() : null;
            String modelName=jdbc.queryForObject("SELECT model_name FROM ai_generation_job WHERE id=?",String.class,jobId);
            Map<String,Object> metadata=new LinkedHashMap<>(); metadata.put("provider","tripo"); metadata.put("taskId",taskId); metadata.put("remoteModel",modelUrl); metadata.put("modelVersion",modelName);
            if(job.get("createdBy") instanceof Number){metadata.put("createdByUserId",((Number)job.get("createdBy")).longValue());metadata.put("consumerWork",true);}
            Long assetId=createAsset("Tripo "+modelName+" 3D模型","model","ai_generated",localModel,localPreview,String.valueOf(jdbc.queryForObject("SELECT prompt FROM ai_generation_job WHERE id=?",String.class,jobId)),null,null,inputId,suffixFromUrl(modelUrl,".glb").replace(".",""),"Tripo,3D模型,"+modelName,metadata);
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,status='succeeded',progress=100 WHERE id=?",assetId,jobId);
            job=jdbc.queryForMap("SELECT id,job_no jobNo,external_task_id externalTaskId,input_asset_id inputAssetId,output_asset_id outputAssetId,status,progress,error_message errorMessage,created_by createdBy FROM ai_generation_job WHERE id=?",jobId);
            return completedTripoJob(jobId,job);
        }
        Map<String,Object> out=new LinkedHashMap<>();out.put("jobId",jobId);out.put("jobNo",job.get("jobNo"));out.put("taskId",taskId);out.put("status",localStatus);out.put("remoteStatus",remoteStatus);out.put("progress",progress);out.put("errorMessage",error);return out;
    }

    @PostMapping("/text-to-3d")
    public Map<String, Object> textTo3d(@RequestBody Generate3dRequest req) throws Exception {
        String prompt = "3D cultural creative product model, " + nullToEmpty(req.prompt) + ", export-ready mesh, clean topology, product prototype";
        String jobNo = no("T3D");
        Long jobId = createJob(jobNo, "text_to_3d", "siliconflow", chatModel, null, req.inputAssetId, prompt, null, "running", null, req.exportFormats == null ? "OBJ,STL,GLB" : req.exportFormats);
        try {
            String spec = callChat(
                    "你是文创产品3D建模指导专家。硅基流动当前在本系统用于生成3D建模规格书，不直接产出OBJ/STL文件。请输出可交给建模师或后续3D工具的结构化建模方案。",
                    "产品/创意：" + prompt + "\n" +
                    "参考资产ID：" + req.inputAssetId + "\n" +
                    "导出格式：" + (req.exportFormats == null ? "OBJ,STL,GLB" : req.exportFormats) + "\n" +
                    "请包含：造型拆解、尺寸建议、材质、工艺、建模步骤、打印/开模风险。"
            );
            Long assetId = createAsset("AI 3D建模规格书", "prompt", "ai_generated", null, null, spec, null, null, req.inputAssetId, "txt", "3D建模,硅基流动,文创打样", Map.of("provider", "siliconflow", "model", chatModel));
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
                                           @RequestParam(required = false) String tags,
                                           @RequestParam(required = false) Long currentUserId) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要上传的图片");
        String original = file.getOriginalFilename() == null ? "upload.png" : file.getOriginalFilename();
        String lower = original.toLowerCase(Locale.ROOT);
        String ext = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? ".jpg" : lower.endsWith(".webp") ? ".webp" : ".png";
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp"))) {
            throw new IllegalArgumentException("当前仅支持 PNG/JPG/WEBP 图片");
        }
        Path dir = Path.of(System.getProperty("user.dir"), "..", "shixun-vue", "public", "uploads").normalize().toAbsolutePath();
        Files.createDirectories(dir);
        String fileName = "ref-" + System.currentTimeMillis() + ext;
        Path target = dir.resolve(fileName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        String url = "/uploads/" + fileName;
        Map<String,Object> meta=new LinkedHashMap<>();
        meta.put("uploadName", original); meta.put("size", file.getSize()); meta.put("contentType", file.getContentType() == null ? "" : file.getContentType());
        if(currentUserId!=null){meta.put("createdByUserId",currentUserId);meta.put("consumerReference",true);}
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
        return Map.of("assetId", assetId, "url", url, "title", title == null || title.isBlank() ? original : title);
    }

    @GetMapping("/assets/{id}/content")
    public ResponseEntity<byte[]> assetContent(@PathVariable Long id) throws Exception {
        Map<String,Object> asset=jdbc.queryForMap("SELECT file_url fileUrl,preview_url previewUrl,format FROM digital_asset WHERE id=?",id);
        String url=String.valueOf(asset.get("fileUrl")==null?asset.get("previewUrl"):asset.get("fileUrl"));
        if(url.startsWith("http://")||url.startsWith("https://")) {
            HttpResponse<byte[]> response=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()<200||response.statusCode()>=300) throw new IOException("读取图片失败 HTTP "+response.statusCode());
            String ct=response.headers().firstValue("content-type").orElse("image/png");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(ct)).body(response.body());
        }
        Path publicDir=vuePublicDir();
        String relative=url.startsWith("/")?url.substring(1):url; Path file=publicDir.resolve(relative).normalize();
        if(!file.startsWith(publicDir)||!Files.exists(file)) throw new IOException("图片文件不存在："+url);
        String lower=file.getFileName().toString().toLowerCase(Locale.ROOT);
        MediaType type=lower.endsWith(".jpg")||lower.endsWith(".jpeg")?MediaType.IMAGE_JPEG:lower.endsWith(".webp")?MediaType.parseMediaType("image/webp"):MediaType.IMAGE_PNG;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).body(Files.readAllBytes(file));
    }

    @GetMapping("/assets/{id}/model-content")
    public ResponseEntity<byte[]> assetModelContent(@PathVariable Long id) throws Exception {
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
        Path publicDir=vuePublicDir();
        String relative=url.startsWith("/")?url.substring(1):url; Path file=publicDir.resolve(relative).normalize();
        if(!file.startsWith(publicDir)||!Files.exists(file)) throw new IOException("模型文件不存在："+url);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(glbType).body(Files.readAllBytes(file));
    }

    @GetMapping("/assets/{id}/download-model")
    public ResponseEntity<byte[]> downloadModel(@PathVariable Long id,@RequestParam(defaultValue="GLB") String format) throws Exception {
        String fmt=normalizeModelFormat(format);
        Map<String,Object> asset=resolveDownloadableModelAsset(id,fmt);
        return modelDownloadResponse(asset,fmt);
    }

    @GetMapping("/assets")
    public List<Map<String, Object>> assets(@RequestParam(required = false) String type,
                                            @RequestParam(required = false) Long currentUserId,
                                            @RequestParam(required = false) String role,
                                            @RequestHeader(value = "X-Current-Role", required = false) String headerRole,
                                            @RequestHeader(value = "X-Current-User-Id", required = false) Long headerUserId) {
        String actualRole=blank(role)?headerRole:role; Long actualUserId=currentUserId==null?headerUserId:currentUserId;
        String cols="id, asset_no assetNo, title, asset_type assetType, source_type sourceType, file_url fileUrl, preview_url previewUrl, prompt, style_id styleId, parent_asset_id parentAssetId, version_no versionNo, status, format, tags, created_by createdBy, created_at createdAt";
        if("user".equals(actualRole)){
            if(actualUserId==null) return List.of();
            if (type != null && !type.isBlank()) return jdbc.queryForList("SELECT "+cols+" FROM digital_asset WHERE asset_type=? AND created_by=? ORDER BY id DESC", type, actualUserId);
            return jdbc.queryForList("SELECT "+cols+" FROM digital_asset WHERE created_by=? ORDER BY id DESC LIMIT 100", actualUserId);
        }
        if (type != null && !type.isBlank()) return jdbc.queryForList("SELECT "+cols+" FROM digital_asset WHERE asset_type=? ORDER BY id DESC", type);
        return jdbc.queryForList("SELECT "+cols+" FROM digital_asset ORDER BY id DESC LIMIT 100");
    }

    @GetMapping("/consumer-assets/review")
    public List<Map<String,Object>> consumerAssetsReview(@RequestHeader(value="X-Current-Role",required=false) String role,
                                                         @RequestParam(required=false) Long userId,
                                                         @RequestParam(required=false) String status,
                                                         @RequestParam(required=false,defaultValue="100") int size) {
        requireCreativeAdmin(role);
        StringBuilder sql=new StringBuilder("SELECT a.id,a.asset_no assetNo,a.title,a.asset_type assetType,a.source_type sourceType,a.file_url fileUrl,a.preview_url previewUrl,a.prompt,a.status,a.format,a.tags,a.created_by createdBy,u.username createdByName,a.created_at createdAt FROM digital_asset a JOIN user u ON a.created_by=u.id WHERE u.role='user' AND a.asset_type IN ('image','model') AND COALESCE(a.source_type,'ai_generated')<>'upload'");
        List<Object> args=new ArrayList<>();
        if(userId!=null){sql.append(" AND a.created_by=?");args.add(userId);}
        if(!blank(status)){sql.append(" AND a.status=?");args.add(status);}
        sql.append(" ORDER BY a.id DESC LIMIT ?");args.add(Math.max(1,Math.min(size,500)));
        return jdbc.queryForList(sql.toString(),args.toArray());
    }

    @GetMapping("/consumer-assets/inventory")
    public List<Map<String,Object>> consumerAssetsInventory(@RequestHeader(value="X-Current-Role",required=false) String role,
                                                            @RequestParam(required=false) Long userId,
                                                            @RequestParam(required=false) String type,
                                                            @RequestParam(required=false) String keyword,
                                                            @RequestParam(required=false,defaultValue="200") int size) {
        requireCreativeAdmin(role);
        StringBuilder sql=new StringBuilder("SELECT a.id,a.asset_no assetNo,a.title,a.asset_type assetType,a.source_type sourceType,a.file_url fileUrl,a.preview_url previewUrl,a.prompt,a.status,a.format,a.tags,a.created_by createdBy,u.username createdByName,a.created_at createdAt,a.updated_at updatedAt FROM digital_asset a JOIN user u ON a.created_by=u.id WHERE u.role='user' AND a.status='approved' AND a.asset_type IN ('image','model') AND COALESCE(a.source_type,'ai_generated')<>'upload'");
        List<Object> args=new ArrayList<>();
        if(userId!=null){sql.append(" AND a.created_by=?");args.add(userId);}
        if(!blank(type) && Set.of("image","model").contains(type)){sql.append(" AND a.asset_type=?");args.add(type);}
        if(!blank(keyword)){sql.append(" AND (a.title LIKE ? OR a.prompt LIKE ? OR a.asset_no LIKE ? OR u.username LIKE ?)");String kw="%"+keyword.trim()+"%";args.add(kw);args.add(kw);args.add(kw);args.add(kw);}
        sql.append(" ORDER BY a.updated_at DESC,a.id DESC LIMIT ?");args.add(Math.max(1,Math.min(size,1000)));
        return jdbc.queryForList(sql.toString(),args.toArray());
    }

    @PutMapping("/consumer-assets/{id}/submit-review")
    public Map<String,Object> submitConsumerAssetReview(@PathVariable Long id,
                                                        @RequestHeader(value="X-Current-Role",required=false) String role,
                                                        @RequestHeader(value="X-Current-User-Id",required=false) Long headerUserId,
                                                        @RequestHeader(value="X-Current-User",required=false) String headerUsername,
                                                        @RequestParam(required=false) Long currentUserId,
                                                        @RequestParam(required=false) String currentUsername,
                                                        @RequestBody(required=false) Map<String,String> body) {
        Long userId=currentUserId==null?headerUserId:currentUserId;
        if(userId==null && body!=null && !blank(body.get("currentUserId"))) {
            try { userId=Long.parseLong(body.get("currentUserId").trim()); } catch(Exception ignored) {}
        }
        String username=blank(currentUsername)?headerUsername:currentUsername;
        if(userId==null && !blank(username)) {
            List<Map<String,Object>> users=jdbc.queryForList("SELECT id FROM user WHERE username=? AND role='user' LIMIT 1", username.trim());
            if(!users.isEmpty() && users.get(0).get("id") instanceof Number) userId=((Number)users.get(0).get("id")).longValue();
        }
        if(userId==null) throw new IllegalArgumentException("缺少当前用户ID，无法提交审核");
        List<Map<String,Object>> userRows=jdbc.queryForList("SELECT id,role FROM user WHERE id=? LIMIT 1", userId);
        if(userRows.isEmpty() || !"user".equals(String.valueOf(userRows.get(0).get("role")))) throw new IllegalStateException("仅C端用户可提交自己的作品审核");
        String note=body==null?"":nullToEmpty(body.get("note"));
        int n=jdbc.update("UPDATE digital_asset SET status='review', tags=CONCAT(COALESCE(tags,''), ?) WHERE id=? AND created_by=? AND asset_type IN ('image','model') AND COALESCE(source_type,'ai_generated')<>'upload' AND COALESCE(status,'draft')<>'approved'", blank(note)?";用户提交审核":";用户提交审核-"+note, id, userId);
        if(n==0) throw new IllegalArgumentException("作品不存在、无权提交，或作品已审核通过");
        return Map.of("success",true,"id",id,"status","review","message","作品已提交给审核员");
    }

    @PutMapping("/consumer-assets/{id}/review")
    public Map<String,Object> reviewConsumerAsset(@PathVariable Long id,
                                                  @RequestHeader(value="X-Current-Role",required=false) String role,
                                                  @RequestHeader(value="X-Current-User",required=false) String operatorHeader,
                                                  @RequestBody Map<String,String> body) {
        requireCreativeAdmin(role);
        String status=body==null?"":nullToEmpty(body.get("status")).trim();
        if(!Set.of("approved","rejected","review").contains(status)) throw new IllegalArgumentException("审核状态只能是 approved / rejected / review");
        String operator=blank(body==null?null:body.get("operator"))?operatorHeader:body.get("operator");
        String comment=body==null?"":nullToEmpty(body.get("comment"));
        int n=jdbc.update("UPDATE digital_asset a SET a.status=?, a.tags=CONCAT(COALESCE(a.tags,''), ?) WHERE a.id=? AND EXISTS (SELECT 1 FROM user u WHERE u.id=a.created_by AND u.role='user')",status,";审核:"+status+(blank(comment)?"":"-"+comment),id);
        if(n==0) throw new IllegalArgumentException("作品不存在或不是C端用户作品");
        return Map.of("success",true,"id",id,"status",status,"operator",blank(operator)?"admin":operator,"message","approved".equals(status)?"审核已通过，作品已进入C端用户端库存":"审核状态已更新");
    }

    @PostMapping("/reviews")
    public Map<String, Object> createReview(@RequestBody ReviewRequest req) throws Exception {
        if (req.assetId == null) throw new IllegalArgumentException("assetId不能为空");
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
        Map<String,Object> fullReport = new LinkedHashMap<>(Map.of("reviewId", reviewId, "reviewNo", reviewNo, "asset", asset, "overallScore", avg, "recommendation", recommendation, "summary", summary, "agents", results, "matrix", buildReviewMatrix(results), "roadmap", buildUpgradeRoadmap(avg, recommendation, results)));
        jdbc.update("INSERT INTO design_review_report (review_id, report_json) VALUES (?, CAST(? AS JSON)) ON DUPLICATE KEY UPDATE report_json=VALUES(report_json)", reviewId, mapper.writeValueAsString(fullReport));
        return fullReport;
    }

    @Scheduled(fixedDelayString = "${tripo.poll.delay-ms:5000}", initialDelayString = "${tripo.poll.initial-delay-ms:8000}")
    public void autoDownloadTripoModels() {
        if(blank(tripoApiKey) || tripoApiKey.contains("YOUR_")) return;
        List<Long> jobIds=jdbc.queryForList("SELECT id FROM ai_generation_job WHERE provider='tripo' AND status IN ('running','queued','succeeded') AND external_task_id IS NOT NULL AND output_asset_id IS NULL ORDER BY id LIMIT 20",Long.class);
        for(Long jobId:jobIds) {
            try {
                String type=jdbc.queryForObject("SELECT job_type FROM ai_generation_job WHERE id=?",String.class,jobId);
                if("text_to_image".equals(type)) tripoImageTask(jobId); else tripoTask(jobId);
            } catch(Exception e) { jdbc.update("UPDATE ai_generation_job SET error_message=? WHERE id=?", "后台轮询："+safeMessage(e), jobId); }
        }
    }

    @GetMapping("/reviews")
    public List<Map<String, Object>> reviews(@RequestParam(required = false) Long assetId) {
        String sql = "SELECT r.id, r.review_no reviewNo, r.asset_id assetId, a.title assetTitle, a.preview_url previewUrl, r.overall_score overallScore, r.summary, r.recommendation, r.created_at createdAt FROM design_review r JOIN digital_asset a ON r.asset_id=a.id";
        List<Map<String, Object>> list;
        if (assetId != null) list = jdbc.queryForList(sql + " WHERE r.asset_id=? ORDER BY r.id DESC", assetId);
        else list = jdbc.queryForList(sql + " ORDER BY r.id DESC LIMIT 50");
        for (Map<String, Object> r : list) {
            r.put("agents", jdbc.queryForList("SELECT agent_key agentKey, agent_name agentName, score, verdict, comments, suggestions_json suggestionsJson FROM design_review_agent WHERE review_id=? ORDER BY id", r.get("id")));
            List<String> reports = jdbc.queryForList("SELECT report_json FROM design_review_report WHERE review_id=?", String.class, r.get("id"));
            if(!reports.isEmpty()) {
                try {
                    Map<String,Object> full = mapper.readValue(reports.get(0), Map.class);
                    r.putAll(full);
                    r.put("id", full.getOrDefault("reviewId", r.get("id")));
                    r.put("createdAt", r.get("createdAt"));
                } catch(Exception ignored) {}
            }
        }
        return list;
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> jobs() {
        return jdbc.queryForList("SELECT id, job_no jobNo, job_type jobType, provider, model_name modelName, input_asset_id inputAssetId, output_asset_id outputAssetId, external_task_id externalTaskId, status, progress, error_message errorMessage, export_formats exportFormats, created_at createdAt FROM ai_generation_job ORDER BY id DESC LIMIT 100");
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
        if(p.toLowerCase(Locale.ROOT).contains("product") || p.contains("产品") || p.contains("文创")) return p;
        return p + "\nCommercial cultural creative product design, official brand quality, clean product photography, detailed material, premium packaging and manufacturable prototype.";
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
            Path dir = vuePublicDir().resolve("generated").resolve("images").normalize();
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
        if(req.currentUserId!=null){meta.put("createdByUserId",req.currentUserId);meta.put("consumerWork",true);}
        Long assetId = createAsset("即梦AI 4.6 2D创意图", "image", "ai_generated", localImage, localImage, prompt, req.negativePrompt, req.styleId, null, format, "即梦AI,火山引擎,2D创意生图,AI生成", meta);
        jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,external_task_id=?,status='succeeded',progress=100,error_message=NULL WHERE id=?", assetId, taskId, jobId);
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
        out.put("imageUrl", localImage);
        out.put("previewUrl", localImage);
        out.put("fileUrl", localImage);
        out.put("remoteImage", remoteImage);
        out.put("taskId", taskId);
        out.put("model", jimengReqKey);
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
            Path dir = vuePublicDir().resolve("generated").resolve("images").normalize();
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

    private String saveGeneratedText(String text, String prefix, String suffix, String folder) throws Exception {
        Path dir = vuePublicDir().resolve("generated").resolve(folder).normalize();
        Files.createDirectories(dir);
        String file = prefix + System.currentTimeMillis() + suffix;
        Files.writeString(dir.resolve(file), text, StandardCharsets.UTF_8);
        return "/generated/" + folder + "/" + file;
    }

    private String renderHtmlToPng(String htmlUrl, String prefix) throws Exception {
        Path publicDir = vuePublicDir();
        String rel = htmlUrl.startsWith("/") ? htmlUrl.substring(1) : htmlUrl;
        Path htmlFile = publicDir.resolve(rel).normalize();
        if(!htmlFile.startsWith(publicDir) || !Files.exists(htmlFile)) throw new IOException("墨刀HTML文件不存在：" + htmlUrl);
        Path outDir = publicDir.resolve("generated/images");
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
    private Path resolvePublicAsset(String url)throws IOException{Path dir=vuePublicDir();String rel=url.startsWith("/")?url.substring(1):url;Path file=dir.resolve(rel).normalize();if(!file.startsWith(dir)||!Files.exists(file))throw new IOException("参考图文件不存在："+url);return file;}
    private String imageExtension(Path p){String n=p.getFileName().toString().toLowerCase(Locale.ROOT);return n.endsWith(".jpeg")?"jpg":n.substring(n.lastIndexOf('.')+1);}
    private String mapTripoStatus(String s){s=s.toLowerCase(Locale.ROOT);if(s.contains("success"))return "succeeded";if(s.contains("fail")||s.contains("cancel")||s.contains("banned")||s.contains("expired"))return "failed";return "running";}
    private String firstText(JsonNode n,String...keys){for(String k:keys){String v=n.path(k).asText("");if(!blank(v))return v;}return "";}
    private String firstUrl(JsonNode n,String...keys){for(String k:keys){JsonNode v=n.path(k);if(v.isTextual()&&!blank(v.asText()))return v.asText();if(v.isArray()&&v.size()>0&&v.get(0).isTextual())return v.get(0).asText();}return "";}
    private String safeMessage(Throwable e){String m=e.getMessage();return blank(m)?e.getClass().getSimpleName():m;}
    private String suffixFromUrl(String url,String fallback){try{String p=URI.create(url).getPath();int i=p.lastIndexOf('.');if(i>=0&&p.length()-i<=6)return p.substring(i).toLowerCase(Locale.ROOT);}catch(Exception ignored){}return fallback;}
    private String saveRemoteFile(String url,String prefix,String suffix,String folder)throws Exception{HttpResponse<byte[]> r=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());if(r.statusCode()<200||r.statusCode()>=300)throw new IOException("下载远程文件失败 HTTP "+r.statusCode());Path dir=vuePublicDir().resolve("generated").resolve(folder).normalize();Files.createDirectories(dir);String file=prefix+System.currentTimeMillis()+suffix;Files.write(dir.resolve(file),r.body());return "/generated/"+folder+"/"+file;}
    private Map<String,Object> completedTripoImageJob(Long jobId,Map<String,Object> job){Map<String,Object>a=jdbc.queryForMap("SELECT id,title,file_url fileUrl,preview_url previewUrl,format,created_at createdAt FROM digital_asset WHERE id=?",job.get("outputAssetId"));Map<String,Object>r=new LinkedHashMap<>();r.put("jobId",jobId);r.put("jobNo",job.get("jobNo"));r.put("taskId",job.get("externalTaskId"));r.put("status","succeeded");r.put("progress",100);r.put("assetId",a.get("id"));r.put("imageUrl",a.get("fileUrl"));r.put("previewUrl",a.get("previewUrl"));r.put("format",a.get("format"));r.put("model",job.get("modelName"));r.put("source","Tripo "+str(job.get("modelName")));return r;}
    private Map<String,Object> completedTripoJob(Long jobId,Map<String,Object> job){Map<String,Object>a=jdbc.queryForMap("SELECT id,title,asset_type assetType,source_type sourceType,status assetStatus,file_url fileUrl,preview_url previewUrl,format,created_at createdAt FROM digital_asset WHERE id=?",job.get("outputAssetId"));Map<String,Object>r=new LinkedHashMap<>();r.put("jobId",jobId);r.put("jobNo",job.get("jobNo"));r.put("taskId",job.get("externalTaskId"));r.put("status","succeeded");r.put("progress",100);r.put("id",a.get("id"));r.put("assetId",a.get("id"));r.put("assetType",a.get("assetType"));r.put("sourceType",a.get("sourceType"));r.put("assetStatus",a.get("assetStatus"));r.put("modelUrl",a.get("fileUrl"));r.put("fileUrl",a.get("fileUrl"));r.put("previewUrl",a.get("previewUrl"));r.put("format",a.get("format"));return r;}
    private boolean blank(String s){return s==null||s.trim().isEmpty();}
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
        String user = "设计资产标题：" + asset.get("title") + "\n资产类型：" + asset.get("assetType") + "\n标签：" + asset.get("tags") + "\n生成/设计Prompt：" + asset.get("prompt") + "\n图片地址：" + asset.get("fileUrl") + "\n补充业务背景：" + (context == null ? "用于图片IP文创产品开发，可衍生明信片、装饰画、手机壳、帆布袋等SKU。" : context);
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

    private Map<String, Object> style(Long id) { return jdbc.queryForMap("SELECT id, name, base_prompt basePrompt, negative_prompt negativePrompt, cultural_guardrails culturalGuardrails FROM brand_style_profile WHERE id=?", id == null ? 1L : id); }

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
        Map<String, Object> asset = jdbc.queryForMap("SELECT file_url fileUrl, preview_url previewUrl, format FROM digital_asset WHERE id=?", assetId);
        String url = String.valueOf(asset.get("fileUrl") == null ? asset.get("previewUrl") : asset.get("fileUrl"));
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        Path publicDir = Path.of(System.getProperty("user.dir"), "..", "shixun-vue", "public").normalize().toAbsolutePath();
        String relative = url.startsWith("/") ? url.substring(1) : url;
        Path file = publicDir.resolve(relative).normalize();
        if (!file.startsWith(publicDir) || !Files.exists(file)) throw new IOException("参考图文件不存在：" + url);
        String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String mime = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg" : lower.endsWith(".webp") ? "image/webp" : "image/png";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(file));
    }

    private String saveRemoteImage(String url, String prefix, String suffix) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("下载生成图片失败 HTTP " + response.statusCode());
        Path dir = Path.of("..", "shixun-vue", "public", "generated").normalize();
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

    private void assignAssetOwner(Long assetId, Long userId) {
        if(assetId==null||userId==null) return;
        try { jdbc.update("UPDATE digital_asset SET created_by=? WHERE id=?", userId, assetId); } catch(Exception ignored) {}
    }

    private void assignJobOwner(Long jobId, Long userId) {
        if(jobId==null||userId==null) return;
        try { jdbc.update("UPDATE ai_generation_job SET created_by=? WHERE id=?", userId, jobId); } catch(Exception ignored) {}
    }

    private void requireCreativeAdmin(String role) {
        if(!"admin".equals(role)) throw new IllegalStateException("仅超级管理员可审核C端用户作品");
    }

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
        Map<String,Object> asset=jdbc.queryForMap("SELECT id,asset_no assetNo,title,asset_type assetType,source_type sourceType,file_url fileUrl,preview_url previewUrl,prompt,parent_asset_id parentAssetId,format,tags,metadata_json metadataJson FROM digital_asset WHERE id=?",id);
        if(!"model".equals(String.valueOf(asset.get("assetType")))) throw new IOException("该资产不是3D模型："+id);
        String currentFormat=str(asset.get("format")).toUpperCase(Locale.ROOT);
        if(asset.get("parentAssetId") instanceof Number&&"converted".equals(str(asset.get("sourceType")))){
            Map<String,Object> parent=jdbc.queryForMap("SELECT id,asset_no assetNo,title,asset_type assetType,source_type sourceType,file_url fileUrl,preview_url previewUrl,prompt,parent_asset_id parentAssetId,format,tags,metadata_json metadataJson FROM digital_asset WHERE id=?",((Number)asset.get("parentAssetId")).longValue());
            if("GLB".equals(fmt)) return parent;
            asset=parent; id=((Number)parent.get("id")).longValue(); currentFormat=str(parent.get("format")).toUpperCase(Locale.ROOT);
        }
        if("GLB".equals(fmt)||fmt.equals(currentFormat)) return asset;
        List<Map<String,Object>> cached=jdbc.queryForList("SELECT id,asset_no assetNo,title,asset_type assetType,file_url fileUrl,preview_url previewUrl,prompt,parent_asset_id parentAssetId,format,tags,metadata_json metadataJson FROM digital_asset WHERE asset_type='model' AND parent_asset_id=? AND UPPER(format)=? ORDER BY id DESC LIMIT 1",id,fmt);
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
        Path publicDir=vuePublicDir();
        Path modelsDir=publicDir.resolve("generated").resolve("models").normalize();
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
        Path publicDir=vuePublicDir();
        String rel=url.startsWith("/")?url.substring(1):url;
        Path file=publicDir.resolve(rel).normalize();
        if(!file.startsWith(publicDir)||!Files.exists(file)) throw new IOException("模型源文件不存在："+url);
        return file;
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
        List<String> cmd;
        if(commandAvailable(modelConvertBlenderCommand)) {
            Path script=modelConvertScriptPath();
            cmd=List.of(modelConvertBlenderCommand,"-b","--python",script.toString(),"--",input.toString(),output.toString(),fmt);
        } else if(commandAvailable(modelConvertAssimpCommand)) {
            cmd=List.of(modelConvertAssimpCommand,"export",input.toString(),output.toString());
        } else if(commandAvailable(modelConvertNodeCommand)) {
            Path script=modelConvertNodeScriptPath();
            cmd=List.of(modelConvertNodeCommand,script.toString(),input.toString(),output.toString(),fmt);
        } else {
            throw new IllegalStateException("服务器未安装模型转换器。请安装 Blender（推荐）、assimp，或确认 Node 可执行并已安装前端依赖");
        }
        ProcessBuilder pb=new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log.toFile());
        Process p=pb.start();
        boolean finished=p.waitFor(Math.max(60,modelConvertTimeoutSeconds),java.util.concurrent.TimeUnit.SECONDS);
        if(!finished) { p.destroyForcibly(); throw new IllegalStateException("模型本地转换超时，请稍后重试或检查模型大小"); }
        String out=Files.exists(log)?Files.readString(log,StandardCharsets.UTF_8):"";
        if(p.exitValue()!=0) throw new IllegalStateException("模型本地转换失败："+out);
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

    private ResponseEntity<byte[]> modelDownloadResponse(Map<String,Object> asset,String fmt) throws Exception {
        String url=str(asset.get("fileUrl")); if(blank(url)) throw new IOException("模型文件地址不存在");
        byte[] bytes; String lower=url.toLowerCase(Locale.ROOT);
        if(url.startsWith("http://")||url.startsWith("https://")){
            HttpResponse<byte[]> response=http.send(HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()<200||response.statusCode()>=300) throw new IOException("读取模型失败 HTTP "+response.statusCode());
            bytes=response.body();
        } else {
            Path publicDir=vuePublicDir(); String relative=url.startsWith("/")?url.substring(1):url; Path file=publicDir.resolve(relative).normalize();
            if(!file.startsWith(publicDir)||!Files.exists(file)) throw new IOException("模型文件不存在："+url);
            bytes=Files.readAllBytes(file); lower=file.getFileName().toString().toLowerCase(Locale.ROOT);
        }
        MediaType type=lower.endsWith(".zip")?MediaType.parseMediaType("application/zip"):"STL".equals(fmt)?MediaType.parseMediaType("model/stl"):"OBJ".equals(fmt)?MediaType.parseMediaType("model/obj"):MediaType.parseMediaType("model/gltf-binary");
        String suffix=lower.endsWith(".zip")?".zip":"."+fmt.toLowerCase(Locale.ROOT);
        String filename="and-taste-3d-"+asset.get("id")+"-"+fmt.toLowerCase(Locale.ROOT)+suffix;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(type).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+filename+"\"").body(bytes);
    }

    public static class ReviewRequest {
        public Long assetId;
        public String context;
    }

    public static class GenerateImageRequest {
        public String title;
        public String provider;
        public String prompt;
        public String negativePrompt;
        public Long styleId;
        public String scene;
        public String productType;
        public String imageSize;
        public Long seed;
        public String tags;
        public Long inputAssetId;
        public String tripoImageModel;
        public String tripoTemplate;
        public Boolean tPose;
        public Boolean sketchToRender;
        public String imagenAspectRatio;
        public String imagenImageSize;
        public String imagenOutputFormat;
        public Long currentUserId;
    }
    public static class Generate3dRequest {
        public String mode;
        public String modelVersion;
        public String promptTemplate;
        public String prompt;
        public String negativePrompt;
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
