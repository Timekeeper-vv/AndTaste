package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.SiliconFlowChatService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Persists the C-end conversational creation journey. AI execution remains in
 * CreativeAiController; this controller only owns the user's workflow state.
 */
@RestController
@RequestMapping("/api/creative/ai/conversations")
public class ConversationalCreativeController {
    private static final Set<String> MODES = Set.of("template", "text", "image");
    private static final Set<String> CHAT_ACTIONS = Set.of("product", "category", "material", "template", "image", "text");
    private static final Set<String> STEPS = Set.of("welcome", "chat", "mode", "product", "inspiration", "material", "style", "summary", "image", "multiview", "model", "commercial", "compliance", "navigation");
    private static final int MAX_PAYLOAD_LENGTH = 12000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SiliconFlowChatService siliconFlow;

    public ConversationalCreativeController(JdbcTemplate jdbc, ObjectMapper mapper, SiliconFlowChatService siliconFlow) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.siliconFlow = siliconFlow;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody(required = false) Map<String, Object> body,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        long userId = requireConsumer(principal);
        String mode = text(body == null ? null : body.get("mode"));
        if (mode != null && !MODES.contains(mode)) throw new IllegalArgumentException("创作方式无效");
        String sessionNo = no("CCS");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO creative_conversation_session (session_no,user_id,mode,status) VALUES (?,?,?,'draft')",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionNo);
            ps.setLong(2, userId);
            ps.setString(3, mode);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) throw new IllegalStateException("创作会话创建失败");
        long sessionId = key.longValue();
        saveEvent(sessionId, userId, "welcome", "session_started", Map.of("mode", mode == null ? "" : mode));
        return getOwnedSession(sessionId, userId);
    }

    @GetMapping
    public List<Map<String, Object>> mine(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        long userId = requireConsumer(principal);
        return jdbc.queryForList("SELECT id,session_no sessionNo,mode,product_type productType,material,status,created_at createdAt,updated_at updatedAt FROM creative_conversation_session WHERE user_id=? ORDER BY updated_at DESC LIMIT 30", userId);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return getOwnedSession(id, requireConsumer(principal));
    }

    @PostMapping("/{id}/events")
    public Map<String, Object> event(@PathVariable long id, @RequestBody Map<String, Object> body,
                                     @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        long userId = requireConsumer(principal);
        getOwnedSession(id, userId);
        if (body == null) throw new IllegalArgumentException("创作步骤不能为空");
        String step = text(body.get("step"));
        String eventType = text(body.get("eventType"));
        if (step == null || !STEPS.contains(step)) throw new IllegalArgumentException("创作步骤无效");
        if (eventType == null || eventType.length() > 60) throw new IllegalArgumentException("创作事件无效");
        Object payload = body.get("payload");
        if (payload == null) payload = Map.of();
        String payloadJson = json(payload);
        if (payloadJson.length() > MAX_PAYLOAD_LENGTH) throw new IllegalArgumentException("本次创作内容过长，请精简后重试");
        jdbc.update("INSERT INTO creative_conversation_event (session_id,user_id,step,event_type,payload_json) VALUES (?,?,?,?,?)", id, userId, step, eventType, payloadJson);
        updateSummary(id, userId, step, payload);
        return getOwnedSession(id, userId);
    }

    /**
     * Chat-oriented orchestration endpoint. The model extracts slots from a
     * natural-language turn, while the server validates every product and
     * material against the published catalog before returning a next action.
     */
    @PostMapping("/{id}/chat")
    public Map<String, Object> chat(@PathVariable long id,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        long userId = requireConsumer(principal);
        getOwnedSession(id, userId);
        Map<String, Object> input = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        String message = limit(text(input.get("message")), 1200);
        Map<String, Object> action = normalizeAction(input.get("action"));
        Map<String, Object> brief = currentBrief(id, userId);
        applyAction(brief, action, message, userId);

        List<Map<String, Object>> catalog = catalogOptions(text(brief.get("categoryKey")), message, 120);
        applyLocalHints(brief, message, action, catalog, userId);
        PlannerDecision decision = planTurn(message, action, brief, catalog);
        applyDecision(brief, decision, userId);
        // A structured button is authoritative. The language model may phrase
        // the next question as text mode, but it must not undo an explicit
        // product, material, template, or uploaded-image choice.
        applyAction(brief, action, null, userId);
        normalizeBrief(brief, userId);

        boolean templateUnavailable = "template".equals(text(brief.get("mode")));
        boolean ready = !templateUnavailable && hasRequiredBrief(brief);
        List<Map<String, Object>> quickReplies = templateUnavailable
                ? templateQuickReplies()
                : quickReplies(brief, catalog, ready);
        String reply = decision.reply;
        if (templateUnavailable) reply = "没有灵感示例功能正在开发中，你可以先用文字描述或上传一张灵感图片开始创作。";
        if (blank(reply)) reply = fallbackReply(brief, catalog, ready);
        if (message != null || !action.isEmpty()) {
            saveEvent(id, userId, "chat", "chat_user_message", Map.of(
                    "message", message == null ? "" : message,
                    "action", action,
                    "brief", new LinkedHashMap<>(brief)));
        }
        saveEvent(id, userId, "chat", "chat_assistant_message", Map.of(
                "text", reply,
                "quickReplies", quickReplies,
                "readyToGenerate", ready,
                "brief", new LinkedHashMap<>(brief),
                "chatModel", siliconFlow.modelName()));
        saveEvent(id, userId, "chat", "chat_state", brief);
        updateSummary(id, userId, "chat", brief);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assistantText", reply);
        result.put("quickReplies", quickReplies);
        result.put("readyToGenerate", ready);
        result.put("brief", brief);
        result.put("stage", templateUnavailable ? "template_unavailable" : ready ? "ready_for_image" : missingStage(brief));
        result.put("chatModel", siliconFlow.modelName());
        result.put("session", getOwnedSession(id, userId));
        return result;
    }

    private Map<String, Object> currentBrief(long id, long userId) {
        Map<String, Object> brief = new LinkedHashMap<>();
        List<Map<String, Object>> sessionRows = jdbc.queryForList(
                "SELECT mode,product_type productType,material FROM creative_conversation_session WHERE id=? AND user_id=?",
                id, userId);
        if (!sessionRows.isEmpty()) {
            Map<String, Object> session = sessionRows.get(0);
            copyNonBlank(brief, session, "mode", "productType", "material");
            if (session.get("productType") != null) brief.put("productName", session.get("productType"));
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT event_type,payload_json FROM creative_conversation_event WHERE session_id=? AND user_id=? ORDER BY id ASC", id, userId);
        for (Map<String, Object> row : rows) {
            String eventType = String.valueOf(row.get("event_type"));
            if ("session_started".equals(eventType) || "mode_selected".equals(eventType)) {
                Map<String, Object> payload = parseObject(String.valueOf(row.get("payload_json")));
                String mode = text(payload.get("mode"));
                if (mode != null && MODES.contains(mode)) brief.put("mode", mode);
            }
            if ("chat_state".equals(eventType)) mergeMap(brief, parseObject(String.valueOf(row.get("payload_json"))));
            if ("product_selected".equals(eventType)) {
                Map<String, Object> payload = parseObject(String.valueOf(row.get("payload_json")));
                copyNonBlank(brief, payload, "productKey", "categoryKey", "categoryName");
                String productName = firstText(payload, "productName", "productType", "product");
                if (productName != null) brief.put("productName", productName);
            }
            if ("material_selected".equals(eventType)) {
                Map<String, Object> payload = parseObject(String.valueOf(row.get("payload_json")));
                String material = firstText(payload, "material", "materialName");
                if (material != null) brief.put("material", material);
            }
            if ("text_inspiration_submitted".equals(eventType)) {
                Map<String, Object> payload = parseObject(String.valueOf(row.get("payload_json")));
                String inspiration = firstText(payload, "inspiration", "inspirationText");
                if (inspiration != null) brief.put("inspiration", inspiration);
                brief.put("inspirationSource", "text");
                brief.put("mode", "text");
            }
            if ("image_inspiration_uploaded".equals(eventType) || "image_inspiration_confirmed".equals(eventType)) {
                Map<String, Object> payload = parseObject(String.valueOf(row.get("payload_json")));
                Long assetId = longValue(payload.get("referenceAssetId"));
                if (assetId == null) assetId = longValue(payload.get("inputAssetId"));
                if (assetId != null) {
                    brief.put("referenceAssetId", assetId);
                    brief.put("inspiration", "以用户上传的参考图片主体、构图和可识别细节为创作依据。");
                    brief.put("inspirationSource", "image");
                }
                brief.put("mode", "image");
            }
        }
        return brief;
    }

    private void applyAction(Map<String, Object> brief, Map<String, Object> action, String message, long userId) {
        String type = text(action.get("type"));
        String value = text(action.get("value"));
        if ("product".equals(type)) {
            Map<String, Object> product = findCatalogOption(value, userId);
            if (product != null) applyProduct(brief, product);
        } else if ("category".equals(type)) {
            String category = value == null ? "" : value;
            if (!category.equals(text(brief.get("categoryKey")))) {
                clearProductSelection(brief);
                brief.put("categoryKey", category);
            }
        } else if ("material".equals(type)) {
            if ("recommend".equals(value)) {
                String recommended = recommendedMaterialValue(text(brief.get("materialOptions")));
                if (recommended != null) {
                    brief.put("material", recommended);
                    brief.put("materialRecommended", true);
                }
            } else if (value != null) {
                String canonical = canonicalMaterial(text(brief.get("materialOptions")), value);
                if (canonical != null) {
                    brief.put("material", canonical);
                    brief.put("materialRecommended", false);
                }
            }
        } else if ("template".equals(type)) {
            brief.put("mode", "template");
            brief.put("inspiration", "无具体灵感，由系统根据产品类别和生产工艺推荐创意方向。");
            brief.put("inspirationSource", "template");
        } else if ("image".equals(type)) {
            Long assetId = longValue(action.get("value"));
            if (assetId != null) {
                requireOwnedAsset(assetId, userId);
                brief.put("mode", "image");
                brief.put("referenceAssetId", assetId);
                brief.put("inspiration", "以用户上传的参考图片主体、构图和可识别细节为创作依据。");
                brief.put("inspirationSource", "image");
            }
        }
        if ("text".equals(type)) brief.put("mode", "text");
    }

    private Map<String, Object> normalizeAction(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> action = toStringMap(source);
        String type = text(action.get("type"));
        if (type == null || !CHAT_ACTIONS.contains(type.toLowerCase(Locale.ROOT))) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type.toLowerCase(Locale.ROOT));
        String value = limit(text(action.get("value")), 240);
        String label = limit(text(action.get("label")), 120);
        if (value != null) result.put("value", value);
        if (label != null) result.put("label", label);
        return result;
    }

    private boolean isFreeTextTurn(String message, Map<String, Object> action) {
        if (blank(message)) return false;
        String type = text(action.get("type"));
        return action.isEmpty() || "text".equals(type);
    }

    private void applyLocalHints(Map<String, Object> brief, String message, Map<String, Object> action,
                                 List<Map<String, Object>> catalog, long userId) {
        if (!isFreeTextTurn(message, action)) return;
        applyNaturalLanguageHints(brief, message, catalog, userId);
    }

    private void applyNaturalLanguageHints(Map<String, Object> brief, String message,
                                           List<Map<String, Object>> catalog, long userId) {
        if (blank(message)) return;
        String input = message.trim();
        Map<String, Object> matchedProduct = matchProduct(input, catalog, userId);
        if (matchedProduct != null) applyProduct(brief, matchedProduct);

        String material = matchMaterial(text(brief.get("materialOptions")), input);
        if (material != null) {
            brief.put("material", material);
            brief.put("materialRecommended", false);
        } else if (brief.get("productKey") != null && input.matches(".*(推荐|帮我选|你来选).*")) {
            String recommended = recommendedMaterialValue(text(brief.get("materialOptions")));
            if (recommended != null) {
                brief.put("material", recommended);
                brief.put("materialRecommended", true);
            }
        }

        boolean selectionOnly = matchedProduct != null && input.length() <= 18
                && !input.matches(".*(想做|做成|设计|主题|灵感|结合|希望|需要|请|我要).*?");
        boolean recommendOnly = input.length() <= 24 && input.matches(".*(推荐|帮我选|你来选).*" );
        boolean materialOnly = (material != null || recommendOnly) && input.length() <= 24
                && !input.matches(".*(想做|做成|设计|主题|灵感|结合|希望|需要|请|我要).*?");
        if (!selectionOnly && !materialOnly) {
            brief.put("inspiration", input);
            brief.put("inspirationSource", "text");
            if (blank(text(brief.get("referenceAssetId")))) brief.put("mode", "text");
        } else if (blank(text(brief.get("mode"))) && matchedProduct != null) {
            brief.put("mode", "text");
        }
    }

    private Map<String, Object> matchProduct(String input, List<Map<String, Object>> catalog, long userId) {
        String normalized = input.toLowerCase(Locale.ROOT);
        Map<String, Object> best = null;
        int bestScore = 0;
        for (Map<String, Object> row : catalog) {
            int score = 0;
            String name = value(row, "name");
            String key = value(row, "optionKey");
            String tags = value(row, "tags");
            if (!blank(name) && normalized.contains(name.toLowerCase(Locale.ROOT))) score += 100;
            if (!blank(key) && normalized.contains(key.toLowerCase(Locale.ROOT))) score += 90;
            for (String token : tags.split("[,，/、]")) {
                if (token.trim().length() >= 2 && normalized.contains(token.trim().toLowerCase(Locale.ROOT))) score += 8;
            }
            if (score > bestScore) { best = row; bestScore = score; }
        }
        if (best != null) return best;
        if (input.length() <= 30) return findCatalogOptionByName(input, userId);
        return null;
    }

    private String matchMaterial(String options, String input) {
        for (String token : materialTokens(options)) {
            if (token.length() >= 2 && input.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) return token;
        }
        return null;
    }

    private List<String> materialTokens(String options) {
        if (blank(options)) return List.of();
        List<String> result = new ArrayList<>();
        for (String token : options.split("[,，/、;；|]")) if (!blank(token)) result.add(token.trim());
        return result;
    }

    private String recommendedMaterialValue(String options) {
        List<String> tokens = materialTokens(options);
        return tokens.isEmpty() ? (blank(options) ? null : options.trim()) : tokens.get(0);
    }

    private String canonicalMaterial(String options, String requested) {
        if (blank(requested)) return null;
        String normalized = requested.trim().toLowerCase(Locale.ROOT);
        for (String token : materialTokens(options)) {
            String candidate = token.toLowerCase(Locale.ROOT);
            if (candidate.equals(normalized) || candidate.contains(normalized) || normalized.contains(candidate)) return token;
        }
        return blank(options) ? requested.trim() : null;
    }

    private void clearProductSelection(Map<String, Object> brief) {
        brief.remove("productKey");
        brief.remove("productName");
        brief.remove("categoryName");
        brief.remove("material");
        brief.remove("materialOptions");
        brief.remove("materialRecommended");
    }

    private PlannerDecision planTurn(String message, Map<String, Object> action, Map<String, Object> brief,
                                     List<Map<String, Object>> catalog) {
        if (blank(message) && action.isEmpty()) return new PlannerDecision("", Map.of());
        String catalogText = catalog.stream().limit(120)
                .map(row -> String.join("|", value(row, "optionKey"), value(row, "name"), value(row, "categoryKey"), value(row, "material"), value(row, "process")))
                .reduce((left, right) -> left + "\n" + right).orElse("");
        String system = "你是‘之间智造’的对话式文创产品设计师。你要像豆包一样自然聊天，但每次只推进一个最必要的问题。\n"
                + "必须只输出JSON，不要Markdown，格式：{\"reply\":\"给用户看的简短中文回复\",\"productKey\":\"选品表中的optionKey或空\",\"productName\":\"或空\",\"categoryKey\":\"或空\",\"material\":\"或空\",\"inspiration\":\"用户灵感或空\",\"mode\":\"text/image/template或空\",\"ready\":false}\n"
                + "产品只能从提供的选品目录中选择，不能创造不存在的产品；不要强制询问颜色和视觉风格，按产品自动处理；材质不确定时允许推荐；信息足够时ready=true；回复不要重复询问已经有的字段，最多两句话。\n"
                + "选品目录：\n" + catalogText;
        String user = "当前创作档案：" + brief + "\n用户本轮输入：" + (blank(message) ? "（点击了快捷选项）" : message)
                + "\n快捷动作：" + action;
        try {
            String raw = siliconFlow.chat(system, user, 0.2, 900, 30);
            JsonNode node = parseJsonNode(raw);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("productKey", node.path("productKey").asText(""));
            fields.put("productName", node.path("productName").asText(""));
            fields.put("categoryKey", node.path("categoryKey").asText(""));
            fields.put("material", node.path("material").asText(""));
            fields.put("inspiration", node.path("inspiration").asText(""));
            fields.put("mode", node.path("mode").asText(""));
            fields.put("ready", node.path("ready").asBoolean(false));
            return new PlannerDecision(node.path("reply").asText(""), fields);
        } catch (Exception ignored) {
            return new PlannerDecision("", Map.of());
        }
    }

    private void applyDecision(Map<String, Object> brief, PlannerDecision decision, long userId) {
        Map<String, Object> fields = decision.fields;
        String productKey = text(fields.get("productKey"));
        String productName = text(fields.get("productName"));
        Map<String, Object> product = findCatalogOption(productKey, userId);
        if (product == null && productName != null) product = findCatalogOptionByName(productName, userId);
        if (product != null) applyProduct(brief, product);
        String categoryKey = text(fields.get("categoryKey"));
        if (blank(text(brief.get("productKey"))) && isKnownCategory(categoryKey)) brief.put("categoryKey", categoryKey);
        String canonical = canonicalMaterial(text(brief.get("materialOptions")), text(fields.get("material")));
        if (canonical != null) {
            brief.put("material", canonical);
            brief.put("materialRecommended", false);
        }
        String inspiration = text(fields.get("inspiration"));
        if (!blank(inspiration) && !isSystemPlaceholder(inspiration)) {
            brief.put("inspiration", inspiration);
            brief.put("inspirationSource", "text");
        }
        String mode = text(fields.get("mode"));
        if (mode != null && MODES.contains(mode) && blank(text(brief.get("mode")))) brief.put("mode", mode);
        if ("template".equals(brief.get("mode")) && blank(text(brief.get("inspiration")))) {
            brief.put("inspiration", "无具体灵感，由系统根据产品类别和生产工艺推荐创意方向。");
        }
    }

    private void normalizeBrief(Map<String, Object> brief, long userId) {
        Map<String, Object> product = findCatalogOption(text(brief.get("productKey")), userId);
        if (product == null) product = findCatalogOptionByName(text(brief.get("productName")), userId);
        if (product != null) applyProduct(brief, product);
        String canonical = canonicalMaterial(text(brief.get("materialOptions")), text(brief.get("material")));
        if (canonical != null) {
            brief.put("material", canonical);
        } else if (!blank(text(brief.get("material")))) {
            brief.remove("material");
            brief.remove("materialRecommended");
        }
        if ("recommend".equalsIgnoreCase(text(brief.get("material"))) && product != null) {
            brief.put("material", recommendedMaterialValue(value(product, "material")));
            brief.put("materialRecommended", true);
        }
    }

    private boolean hasRequiredBrief(Map<String, Object> brief) {
        boolean base = !blank(text(brief.get("productKey")))
                && !blank(text(brief.get("material")))
                && !blank(text(brief.get("inspiration")))
                && !blank(text(brief.get("mode")));
        if (!base) return false;
        return !"image".equals(text(brief.get("mode"))) || !blank(text(brief.get("referenceAssetId")));
    }

    private String missingStage(Map<String, Object> brief) {
        if (blank(text(brief.get("productKey")))) return "need_product";
        if ("image".equals(text(brief.get("mode"))) && blank(text(brief.get("referenceAssetId")))) return "need_inspiration";
        if (blank(text(brief.get("inspiration"))) && !"template".equals(brief.get("mode"))) return "need_inspiration";
        if (blank(text(brief.get("material"))) ) return "need_material";
        return "understanding";
    }

    private String fallbackReply(Map<String, Object> brief, List<Map<String, Object>> catalog, boolean ready) {
        if (ready) return "我已经理解你的产品方向、灵感和材质，现在直接生成可生产方向的产品图。";
        if (blank(text(brief.get("productKey")))) return "你想把这个灵感做成什么产品？可以直接输入，也可以从下面选择。";
        if ("image".equals(text(brief.get("mode"))) && blank(text(brief.get("referenceAssetId")))) return "请先上传一张你有权使用的灵感图片，我会保留主体和可识别细节。";
        if (blank(text(brief.get("inspiration"))) && !"template".equals(brief.get("mode"))) return "说说你的灵感即可，不需要写专业提示词；也可以上传一张参考图。";
        return "材质不确定也没关系，我会根据产品结构和量产工艺帮你推荐。";
    }

    private List<Map<String, Object>> quickReplies(Map<String, Object> brief, List<Map<String, Object>> catalog, boolean ready) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (ready) return result;
        if (blank(text(brief.get("productKey")))) {
            if (!blank(text(brief.get("categoryKey")))) {
                for (Map<String, Object> row : catalog) {
                    result.add(reply(value(row, "name"), "product", value(row, "optionKey")));
                    if (result.size() >= 8) break;
                }
                if (!result.isEmpty()) return result;
            }
            Set<String> seen = new HashSet<>();
            for (Map<String, Object> row : catalog) {
                String category = value(row, "categoryKey");
                if (seen.add(category)) result.add(reply(categoryName(row), "category", category));
                if (result.size() >= 8) break;
            }
            if (result.isEmpty()) result.add(reply("查看产品目录", "text", "我想看看可以做什么产品"));
            return result;
        }
        if (blank(text(brief.get("inspiration"))) && !"template".equals(brief.get("mode"))) {
            result.add(reply("上传灵感图片", "upload", ""));
            result.add(reply("没有灵感（看看示例）", "template", ""));
            return result;
        }
        if ("image".equals(text(brief.get("mode"))) && blank(text(brief.get("referenceAssetId")))) {
            result.add(reply("上传灵感图片", "upload", ""));
            return result;
        }
        if (blank(text(brief.get("material")))) {
            result.add(reply("你帮我推荐", "material", "recommend"));
            String material = text(brief.get("materialOptions"));
            if (material != null) for (String item : material.split("[,，/]")) if (!blank(item)) result.add(reply(item.trim(), "material", item.trim()));
        }
        return result;
    }

    private Map<String, Object> reply(String label, String type, String value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("label", label); result.put("type", type); result.put("value", value); return result;
    }

    private void applyProduct(Map<String, Object> brief, Map<String, Object> product) {
        String previousKey = text(brief.get("productKey"));
        String nextKey = value(product, "optionKey");
        if (previousKey != null && !previousKey.equals(nextKey)) {
            brief.remove("material");
            brief.remove("materialRecommended");
        }
        brief.put("productKey", product.get("optionKey"));
        brief.put("productName", product.get("name"));
        brief.put("categoryKey", product.get("categoryKey"));
        brief.put("categoryName", product.get("categoryName"));
        brief.put("materialOptions", product.get("material"));
        String existingMaterial = text(brief.get("material"));
        String canonical = canonicalMaterial(value(product, "material"), existingMaterial);
        if (canonical != null) brief.put("material", canonical);
        else if (existingMaterial != null) brief.remove("material");
    }

    private boolean isKnownCategory(String categoryKey) {
        if (blank(categoryKey)) return false;
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM selection_category WHERE category_key=? AND enabled=1 AND review_status='approved'",
                Integer.class, categoryKey);
        return count != null && count > 0;
    }

    private boolean isSystemPlaceholder(String value) {
        return "无具体灵感，由系统根据产品类别和生产工艺推荐创意方向。".equals(value)
                || "以用户上传的参考图片主体、构图和可识别细节为创作依据。".equals(value);
    }

    private List<Map<String, Object>> templateQuickReplies() {
        return List.of(
                reply("已有文字想法", "text", ""),
                reply("上传灵感图片", "upload", "")
        );
    }

    private List<Map<String, Object>> catalogOptions(String categoryKey, String keyword, int size) {
        StringBuilder sql = new StringBuilder("SELECT o.option_key optionKey,o.category_key categoryKey,c.name categoryName,o.name,o.material,o.process,o.description,o.tags FROM selection_option o JOIN selection_category c ON c.category_key=o.category_key WHERE o.enabled=1 AND o.review_status='approved' AND c.enabled=1 AND c.review_status='approved'");
        List<Object> args = new ArrayList<>();
        if (!blank(categoryKey)) { sql.append(" AND o.category_key=?"); args.add(categoryKey); }
        if (!blank(keyword) && keyword.length() > 2) { sql.append(" AND (o.name LIKE ? OR o.subtitle LIKE ? OR o.tags LIKE ? OR o.description LIKE ?)"); String k = "%" + keyword + "%"; args.add(k); args.add(k); args.add(k); args.add(k); }
        sql.append(" ORDER BY o.sort_order,o.id LIMIT ?"); args.add(Math.max(1, Math.min(size, 180)));
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        // A natural-language sentence rarely matches a complete SQL LIKE
        // phrase. Fall back to the category catalog so the chat model can
        // still resolve a product from the full approved list.
        if (rows.isEmpty() && !blank(keyword)) return catalogOptions(categoryKey, null, size);
        return rows;
    }

    private Map<String, Object> findCatalogOption(String optionKey, long userId) {
        if (blank(optionKey)) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.option_key optionKey,o.category_key categoryKey,c.name categoryName,o.name,o.material,o.process,o.description,o.tags FROM selection_option o JOIN selection_category c ON c.category_key=o.category_key WHERE o.option_key=? AND o.enabled=1 AND o.review_status='approved' AND c.enabled=1 AND c.review_status='approved' LIMIT 1", optionKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findCatalogOptionByName(String name, long userId) {
        if (blank(name)) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.option_key optionKey,o.category_key categoryKey,c.name categoryName,o.name,o.material,o.process,o.description,o.tags FROM selection_option o JOIN selection_category c ON c.category_key=o.category_key WHERE o.enabled=1 AND o.review_status='approved' AND c.enabled=1 AND c.review_status='approved' AND (o.name=? OR o.name LIKE ?) ORDER BY CHAR_LENGTH(o.name) DESC,o.sort_order LIMIT 1", name.trim(), "%" + name.trim() + "%");
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String categoryName(Map<String, Object> row) { return blank(value(row, "categoryName")) ? "其他产品" : value(row, "categoryName"); }

    private String value(Map<String, Object> row, String key) { return row == null || row.get(key) == null ? "" : String.valueOf(row.get(key)); }

    private void mergeMap(Map<String, Object> target, Map<String, Object> source) { if (source != null) source.forEach((key, value) -> { if (value != null && !String.valueOf(value).isBlank()) target.put(key, value); }); }

    private void copyNonBlank(Map<String, Object> target, Map<String, Object> source, String... keys) { for (String key : keys) if (!blank(text(source.get(key)))) target.put(key, source.get(key)); }

    private JsonNode parseJsonNode(String raw) throws Exception {
        String value = raw == null ? "" : raw.trim(); int start = value.indexOf('{'); int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) value = value.substring(start, end + 1); return mapper.readTree(value);
    }

    private Map<String, Object> parseObject(String raw) {
        try { JsonNode node = parseJsonNode(raw); return mapper.convertValue(node, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception ignored) { return new LinkedHashMap<>(); }
    }

    private Long longValue(Object value) { try { return value == null || String.valueOf(value).isBlank() ? null : Long.valueOf(String.valueOf(value)); } catch (Exception ignored) { return null; } }

    private String limit(String value, int max) { return value == null ? null : value.substring(0, Math.min(max, value.length())); }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private void requireOwnedAsset(Long assetId, long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM digital_asset WHERE id=? AND created_by=?", Integer.class, assetId, userId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能关联自己的创作作品");
    }

    private static final class PlannerDecision {
        private final String reply;
        private final Map<String, Object> fields;
        private PlannerDecision(String reply, Map<String, Object> fields) { this.reply = reply; this.fields = fields; }
    }

    private void updateSummary(long id, long userId, String step, Object payload) {
        Map<String, Object> values = payload instanceof Map<?, ?> raw ? toStringMap(raw) : Map.of();
        String mode = text(values.get("mode"));
        String productType = firstText(values, "productType", "product", "productName");
        String material = firstText(values, "material", "materialName");
        String status = "image".equals(step) || "model".equals(step) ? "completed" : null;
        jdbc.update("UPDATE creative_conversation_session SET mode=COALESCE(?,mode),product_type=COALESCE(?,product_type),material=COALESCE(?,material),status=COALESCE(?,status),updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",
                mode, productType, material, status, id, userId);
    }

    private void saveEvent(long sessionId, long userId, String step, String eventType, Object payload) {
        String payloadJson = json(payload);
        if (payloadJson.length() > MAX_PAYLOAD_LENGTH) throw new IllegalArgumentException("本次创作内容过长，请精简后重试");
        jdbc.update("INSERT INTO creative_conversation_event (session_id,user_id,step,event_type,payload_json) VALUES (?,?,?,?,?)",
                sessionId, userId, step, eventType, payloadJson);
    }

    private Map<String, Object> getOwnedSession(long id, long userId) {
        List<Map<String, Object>> sessions = jdbc.queryForList("SELECT id,session_no sessionNo,user_id userId,mode,product_type productType,material,status,created_at createdAt,updated_at updatedAt FROM creative_conversation_session WHERE id=? AND user_id=?", id, userId);
        if (sessions.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "创作会话不存在");
        Map<String, Object> result = new LinkedHashMap<>(sessions.get(0));
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,step,event_type eventType,payload_json payloadJson,created_at createdAt FROM creative_conversation_event WHERE session_id=? AND user_id=? ORDER BY id ASC", id, userId);
        List<Map<String, Object>> events = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> event = new LinkedHashMap<>(row);
            Object raw = row.get("payloadJson");
            try { event.put("payload", mapper.readValue(String.valueOf(raw), new TypeReference<Object>() {})); }
            catch (Exception ignored) { event.put("payload", Map.of()); }
            event.remove("payloadJson");
            events.add(event);
        }
        result.put("events", events);
        return result;
    }

    private long requireConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"user".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅C端用户可使用对话式创作");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user' AND COALESCE(status,'active')='active'", Integer.class, principal.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        return principal.userId();
    }

    private Map<String, Object> toStringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> { if (key != null) result.put(String.valueOf(key), value); });
        return result;
    }

    private String firstText(Map<String, Object> values, String... keys) {
        for (String key : keys) { String value = text(values.get(key)); if (value != null) return value; }
        return null;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("创作内容格式无效"); }
    }

    private String text(Object value) {
        if (value == null) return null;
        String valueText = String.valueOf(value).trim();
        return valueText.isEmpty() ? null : valueText.substring(0, Math.min(valueText.length(), 1200));
    }

    private String no(String prefix) {
        return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int) (Math.random() * 900 + 100);
    }
}
