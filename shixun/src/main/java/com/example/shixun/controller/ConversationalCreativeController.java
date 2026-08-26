package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.CreativeProjectService;
import com.example.shixun.service.SiliconFlowChatService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persists the C-end conversational creation journey. AI execution remains in
 * CreativeAiController; this controller only owns the user's workflow state.
 */
@RestController
@RequestMapping("/api/creative/ai/conversations")
public class ConversationalCreativeController {
    private static final Set<String> MODES = Set.of("template", "text", "image");
    private static final Set<String> CHAT_ACTIONS = Set.of("product", "category", "material", "size", "template", "image", "text", "confirm_generate", "add_detail", "edit", "adopt_direction");
    private static final Set<String> EDIT_TARGETS = Set.of("product", "inspiration", "material", "size");
    private static final Set<String> STEPS = Set.of("welcome", "chat", "mode", "product", "inspiration", "material", "size", "style", "summary", "image", "multiview", "model", "commercial", "compliance", "navigation");
    private static final int MAX_PAYLOAD_LENGTH = 12000;
    private static final Pattern DIMENSION_PATTERN = Pattern.compile(
            "(?i)(?:\\d{1,4}(?:\\.\\d+)?\\s*(?:(?:[x×*]|乘)\\s*\\d{1,4}(?:\\.\\d+)?\\s*){1,3}(?:mm|毫米|cm|厘米|m|米|in|英寸)|"
                    + "\\d{1,4}(?:\\.\\d+)?\\s*(?:mm|毫米|cm|厘米|m|米|in|英寸)(?:\\s*(?:[x×*]|乘|长|宽|高|厚)\\s*\\d{1,4}(?:\\.\\d+)?\\s*(?:mm|毫米|cm|厘米|m|米|in|英寸)?){0,3})");
    private static final Pattern STANDARD_SIZE_PATTERN = Pattern.compile("(?i)\\bA[3-6]\\b");
    private static final Pattern NAMED_DIMENSION_PATTERN = Pattern.compile(
            "(?i)(?:长|宽|高|厚|直径|口径)[^。；，,]{0,48}\\d{1,4}(?:\\.\\d+)?\\s*(?:mm|毫米|cm|厘米|m|米|in|英寸)");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SiliconFlowChatService siliconFlow;
    private final CreativeProjectService projects;

    @Autowired
    public ConversationalCreativeController(JdbcTemplate jdbc, ObjectMapper mapper, SiliconFlowChatService siliconFlow,
                                            CreativeProjectService projects) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.siliconFlow = siliconFlow;
        this.projects = projects;
    }

    /** Kept for focused unit tests and older embedders that construct the controller directly. */
    public ConversationalCreativeController(JdbcTemplate jdbc, ObjectMapper mapper, SiliconFlowChatService siliconFlow) {
        this(jdbc, mapper, siliconFlow, new CreativeProjectService(jdbc, mapper));
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody(required = false) Map<String, Object> body,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        long userId = requireConsumer(principal);
        String mode = text(body == null ? null : body.get("mode"));
        if (mode != null && !MODES.contains(mode)) throw new IllegalArgumentException("创作方式无效");
        CreativeProjectService.ProjectRef project = projects.createProject(userId, mode, text(body == null ? null : body.get("title")));
        String sessionNo = no("CCS");
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO creative_conversation_session (session_no,user_id,mode,status,project_id,version_id) VALUES (?,?,?,'draft',?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sessionNo);
            ps.setLong(2, userId);
            ps.setString(3, mode);
            ps.setLong(4, project.projectId());
            ps.setLong(5, project.versionId());
            return ps;
        }, keyHolder);
        Number key = generatedId(keyHolder);
        if (key == null) throw new IllegalStateException("创作会话创建失败");
        long sessionId = key.longValue();
        saveEvent(sessionId, userId, "welcome", "session_started", Map.of("mode", mode == null ? "" : mode));
        return getOwnedSession(sessionId, userId);
    }

    @GetMapping
    public List<Map<String, Object>> mine(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        long userId = requireConsumer(principal);
        return jdbc.queryForList("SELECT id,session_no sessionNo,mode,product_type productType,material,product_size productSize,status,project_id projectId,version_id versionId,created_at createdAt,updated_at updatedAt FROM creative_conversation_session WHERE user_id=? ORDER BY updated_at DESC,id DESC LIMIT 30", userId);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable long id,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return getOwnedSession(id, requireConsumer(principal));
    }

    /**
     * Removes only the conversation transcript. The creative project and all
     * generated assets remain available in the works and production flows.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable long id,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        long userId = requireConsumer(principal);
        Integer owned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM creative_conversation_session WHERE id=? AND user_id=?",
                Integer.class, id, userId);
        if (owned == null || owned == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "创作会话不存在");
        // Do not delete the linked creative_project: generated assets, models,
        // reviews and production records may still reference that project.
        jdbc.update("DELETE FROM creative_conversation_event WHERE session_id=? AND user_id=?", id, userId);
        jdbc.update("DELETE FROM creative_conversation_session WHERE id=? AND user_id=?", id, userId);
        return Map.of("deleted", true, "id", id);
    }

    @PostMapping("/{id}/events")
    public Map<String, Object> event(@PathVariable long id, @RequestBody Map<String, Object> body,
                                     @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        long userId = requireConsumer(principal);
        Map<String, Object> session = getOwnedSession(id, userId);
        if (body == null) throw new IllegalArgumentException("创作步骤不能为空");
        String step = text(body.get("step"));
        String eventType = text(body.get("eventType"));
        if (step == null || !STEPS.contains(step)) throw new IllegalArgumentException("创作步骤无效");
        if (eventType == null || eventType.length() > 60) throw new IllegalArgumentException("创作事件无效");
        Object payload = body.get("payload");
        if (payload == null) payload = Map.of();
        String payloadJson = json(payload);
        if (payloadJson.length() > MAX_PAYLOAD_LENGTH) throw new IllegalArgumentException("本次创作内容过长，请精简后重试");
        CreativeProjectService.ProjectRef project = projects.ensureForSession(id, userId, text(session.get("mode")));
        jdbc.update("INSERT INTO creative_conversation_event (session_id,user_id,project_id,version_id,step,event_type,payload_json) VALUES (?,?,?,?,?,?,?)",
                id, userId, project.projectId(), project.versionId(), step, eventType, payloadJson);
        updateSummary(id, userId, step, payload);
        projects.appendEvent(project.projectId(), userId, project.versionId(), eventType, null,
                projectStage(step, eventType), "user", userId, text(body.get("idempotencyKey")), payload);
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
        boolean materialKnownBeforeTurn = !blank(text(brief.get("material")));
        boolean confirmationText = isGenerationConfirmationMessage(message);
        applyAction(brief, action, message, userId);

        List<Map<String, Object>> catalog = catalogOptions(text(brief.get("categoryKey")), message, 120);
        Map<String, Object> naturalLanguageProductMatch = isFreeTextTurn(message, action)
                ? matchProduct(message, catalog, userId) : null;
        String editTarget = "edit".equals(text(action.get("type"))) ? text(action.get("value")) : null;
        String actionType = text(action.get("type"));
        boolean structuredAction = !action.isEmpty() && !"text".equals(actionType);
        boolean directQuestion = !structuredAction && !confirmationText && isLikelyQuestion(message, brief);
        PlannerDecision decision = (isGenerationConfirmationAction(action) || confirmationText || editTarget != null || structuredAction)
                ? new PlannerDecision("", Map.of(), "", "")
                : directQuestion ? answerQuestionTurn(message, brief, catalog) : planTurn(message, action, brief, catalog);
        boolean creativeInput = structuredAction || shouldApplyCreativeInput(decision.intent, message, brief);
        if (creativeInput || confirmationText || isGenerationConfirmationAction(action)) {
            applyGenerationConfirmationState(brief, action, message);
        }
        if (creativeInput) applyDecision(brief, decision, userId);
        // The model may improve the wording, but it must never erase a
        // material, size, or inspiration already captured by local rules.
        if (!confirmationText && creativeInput) applyLocalHints(brief, message, action, catalog, userId);
        // A structured button is authoritative. The language model may phrase
        // the next question as text mode, but it must not undo an explicit
        // product, material, template, or uploaded-image choice.
        applyAction(brief, action, null, userId);
        normalizeBrief(brief, userId);

        if (creativeInput || confirmationText || isGenerationConfirmationAction(action)) {
            applyGenerationConfirmationState(brief, action, message);
        }
        boolean templateUnavailable = "template".equals(text(brief.get("mode")));
        boolean complete = !templateUnavailable && hasRequiredBrief(brief);
        boolean ordinaryQuestion = !creativeInput && !structuredAction && !blank(message) && !confirmationText;
        boolean ready = complete && isTrue(brief.get("generationConfirmed")) && !ordinaryQuestion;
        boolean addingDetail = complete && "add_detail".equals(text(action.get("type")));
        boolean confirmationRequired = complete && !ready && !addingDetail && !ordinaryQuestion;
        List<Map<String, Object>> quickReplies = templateUnavailable
                ? templateQuickReplies()
                : addingDetail ? List.of() : quickReplies(brief, catalog, complete, ready);
        if (ordinaryQuestion && blank(decision.suggestedDirection)) {
            quickReplies = List.of();
        }
        if (("answer_question".equals(decision.intent) || "recommend_direction".equals(decision.intent))
                && !blank(decision.suggestedDirection)) {
            quickReplies = List.of(reply("把这个方向带入创作", "adopt_direction", decision.suggestedDirection));
        }
        String stage = templateUnavailable ? "template_unavailable"
                : ready ? "ready_for_image"
                : addingDetail ? "need_additional_detail"
                : confirmationRequired ? "confirm_before_image"
                : missingStage(brief);
        String reply = decision.reply;
        if (templateUnavailable) reply = "没有灵感示例功能正在开发中，你可以先用文字描述或上传一张灵感图片开始创作。";
        if (blank(reply)) reply = fallbackReply(brief, catalog, ready);
        if (addingDetail) {
            reply = "好的，请在下方补充想保留、想加强或需要避免的内容；我更新方案后会再请你确认。";
        } else if ("product".equals(editTarget)) {
            reply = "好的，之前的灵感会继续保留。请重新选择想落地的产品。";
        } else if ("inspiration".equals(editTarget)) {
            reply = "好的，产品和材质会继续保留。请重新描述灵感，或上传一张新的参考图。";
        } else if ("material".equals(editTarget)) {
            reply = "好的，请重新选择材质；不确定时可以继续让我推荐。";
        } else if ("size".equals(editTarget)) {
            reply = "好的，请重新告诉我成品尺寸；例如 60×60×3mm，也可以让我按常用规格推荐。";
        } else if (confirmationRequired && creativeInput) {
            reply = "我已经整理好产品、灵感、材质和尺寸。生成图片前，还有需要补充的吗？没有的话点击“没有补充，开始生成”。";
        } else if (ready && (isGenerationConfirmationAction(action) || confirmationText)) {
            reply = "好的，我按当前方案开始生成产品图。";
        } else if (creativeInput && naturalLanguageProductMatch != null && !templateUnavailable) {
            // A concrete product phrase such as “兵马俑冰箱贴” is already
            // enough to bind the catalog product family. Keep the cultural
            // phrase as inspiration and move straight to the next missing
            // field instead of asking the user to choose the product again.
            reply = "已识别并绑定为「" + value(naturalLanguageProductMatch, "name") + "」。" + fallbackReply(brief, catalog, false);
        } else if (creativeInput && "need_size".equals(stage)) {
            // The planner may phrase the reply differently, but it must not
            // move the workflow backwards by repeating a stale question.
            reply = fallbackReply(brief, catalog, false);
        }
        if (creativeInput && !addingDetail && editTarget == null && !templateUnavailable && !ready
                && isStaleProgressReply(reply, brief)) {
            reply = fallbackReply(brief, catalog, false);
        }
        boolean recommendationTurn = creativeInput && isRecommendationMessage(message);
        boolean materialRecommendationTurn = "material".equals(actionType)
                && "recommend".equalsIgnoreCase(text(action.get("value")));
        boolean sizeRecommendationTurn = "size".equals(actionType)
                && "recommend".equalsIgnoreCase(text(action.get("value")));
        if (materialRecommendationTurn || (recommendationTurn && !materialKnownBeforeTurn)) {
            String selected = text(brief.get("material"));
            if (!blank(selected)) {
                String selectedSize = text(brief.get("productSize"));
                reply = "我已推荐材质「" + selected + "」"
                        + (blank(selectedSize) ? "" : "和成品规格「" + selectedSize + "」")
                        + "，并直接写入图片生成提示词。生成前还有需要补充的吗？";
            }
        } else if (sizeRecommendationTurn || recommendationTurn) {
            String selected = text(brief.get("productSize"));
            if (!blank(selected)) {
                reply = "根据「" + text(brief.get("productName")) + "」的常用打样规格，我推荐「" + selected
                        + "」，已为你设置并写入图片生成提示词。生成前还有需要补充的吗？";
            }
        }
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
                "generationConfirmationRequired", confirmationRequired,
                "generationConfirmed", isTrue(brief.get("generationConfirmed")),
                "stage", stage,
                "brief", new LinkedHashMap<>(brief),
                "chatModel", siliconFlow.modelName()));
        saveEvent(id, userId, "chat", "chat_state", brief);
        updateSummary(id, userId, "chat", brief);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assistantText", reply);
        result.put("quickReplies", quickReplies);
        result.put("readyToGenerate", ready);
        result.put("generationConfirmationRequired", confirmationRequired);
        result.put("brief", brief);
        result.put("stage", stage);
        result.put("chatModel", siliconFlow.modelName());
        result.put("session", getOwnedSession(id, userId));
        return result;
    }

    private Map<String, Object> currentBrief(long id, long userId) {
        Map<String, Object> brief = new LinkedHashMap<>();
        List<Map<String, Object>> sessionRows = jdbc.queryForList(
                "SELECT mode,product_type productType,material,product_size productSize FROM creative_conversation_session WHERE id=? AND user_id=?",
                id, userId);
        if (!sessionRows.isEmpty()) {
            Map<String, Object> session = sessionRows.get(0);
            copyNonBlank(brief, session, "mode", "productType", "material", "productSize");
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
            if ("chat_state".equals(eventType)) {
                // chat_state is a complete snapshot. Replace older legacy
                // fields here, while still allowing newer legacy events to be
                // applied in their actual event order.
                brief.clear();
                mergeMap(brief, parseObject(String.valueOf(row.get("payload_json"))));
                continue;
            }
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
            if ("size_selected".equals(eventType)) {
                Map<String, Object> payload = parseObject(String.valueOf(row.get("payload_json")));
                String productSize = normalizeProductSize(firstText(payload, "productSize", "size", "dimensions"));
                if (productSize != null) brief.put("productSize", productSize);
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
                    if (blank(text(brief.get("productSize")))) {
                        String recommendedSize = recommendedProductSize(brief);
                        if (recommendedSize != null) {
                            brief.put("productSize", recommendedSize);
                            brief.put("sizeRecommended", true);
                        }
                    }
                }
            } else if (value != null) {
                String canonical = canonicalMaterial(text(brief.get("materialOptions")), value);
                if (canonical != null) {
                    brief.put("material", canonical);
                    brief.put("materialRecommended", false);
                }
            }
        } else if ("size".equals(type)) {
            if ("recommend".equalsIgnoreCase(value)) {
                String recommended = recommendedProductSize(brief);
                if (recommended != null) {
                    brief.put("productSize", recommended);
                    brief.put("sizeRecommended", true);
                }
            } else {
                String productSize = normalizeProductSize(value);
                if (productSize == null) {
                    throw new IllegalArgumentException("请填写可识别的成品尺寸，例如 60×60×3mm");
                }
                brief.put("productSize", productSize);
                brief.put("sizeRecommended", false);
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
        } else if ("adopt_direction".equals(type)) {
            String direction = limit(value, 240);
            if (!blank(direction)) {
                brief.put("inspiration", direction);
                brief.put("inspirationSource", "adopted_direction");
                if (blank(text(brief.get("mode")))) brief.put("mode", "text");
                applyNaturalLanguageHints(brief, direction, catalogOptions(text(brief.get("categoryKey")), direction, 120), userId);
                brief.put("inspiration", direction);
                brief.put("inspirationSource", "adopted_direction");
            }
        } else if ("edit".equals(type)) {
            if ("product".equals(value)) {
                clearProductSelection(brief);
                brief.remove("categoryKey");
            } else if ("inspiration".equals(value)) {
                brief.remove("mode");
                brief.remove("inspiration");
                brief.remove("inspirationSource");
                brief.remove("referenceAssetId");
            } else if ("material".equals(value)) {
                brief.remove("material");
                brief.remove("materialRecommended");
            } else if ("size".equals(value)) {
                brief.remove("productSize");
                brief.remove("sizeRecommended");
            }
        }
        if ("text".equals(type)) brief.put("mode", "text");
    }

    private Map<String, Object> normalizeAction(Object raw) {
        if (!(raw instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> action = toStringMap(source);
        String type = text(action.get("type"));
        if (type == null || !CHAT_ACTIONS.contains(type.toLowerCase(Locale.ROOT))) return Map.of();
        String normalizedType = type.toLowerCase(Locale.ROOT);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", normalizedType);
        String value = limit(text(action.get("value")), 240);
        if ("edit".equals(normalizedType)) {
            String normalizedTarget = value == null ? null : value.toLowerCase(Locale.ROOT);
            if (normalizedTarget == null || !EDIT_TARGETS.contains(normalizedTarget)) {
                throw new IllegalArgumentException("修改目标无效");
            }
            value = normalizedTarget;
        }
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
        // Keep the catalog as the first choice, but do not discard a clear
        // user-entered material merely because an older product row does not
        // list that variant yet (for example 毛线 or 毛绒).
        if (material == null && !blank(text(brief.get("productKey")))) {
            material = extractExplicitMaterial(input);
        }
        if (material != null) {
            brief.put("material", material);
            brief.put("materialRecommended", false);
        } else if (blank(text(brief.get("material"))) && brief.get("productKey") != null
                && input.matches(".*(推荐|帮我选|你来选).*")) {
            String recommended = recommendedMaterialValue(text(brief.get("materialOptions")));
            if (recommended != null) {
                brief.put("material", recommended);
                brief.put("materialRecommended", true);
            }
        }

        String productSize = extractProductSize(input);
        if (productSize != null) {
            brief.put("productSize", productSize);
            brief.put("sizeRecommended", false);
        } else if (blank(text(brief.get("productSize"))) && hasRequiredFieldsBeforeSize(brief)) {
            // A common answer to the size question is just "60". Interpret a
            // bare number as millimetres only in the size stage; elsewhere it
            // may be part of the user's creative description.
            String bareNumericSize = extractBareNumericSize(input);
            if (bareNumericSize != null) {
                brief.put("productSize", bareNumericSize);
                brief.put("sizeRecommended", false);
            } else if (input.length() <= 24 && input.matches(".*(推荐|帮我选|你来选).*")) {
                String recommended = recommendedProductSize(brief);
                if (recommended != null) {
                    brief.put("productSize", recommended);
                    brief.put("sizeRecommended", true);
                }
            }
        }

        boolean selectionOnly = matchedProduct != null && input.length() <= 18
                && !input.matches(".*(想做|做成|设计|主题|灵感|结合|希望|需要|请|我要).*?");
        boolean recommendOnly = input.length() <= 24 && input.matches(".*(推荐|帮我选|你来选).*" );
        boolean materialOnly = (material != null || recommendOnly) && input.length() <= 24
                && !input.matches(".*(想做|做成|设计|主题|灵感|结合|希望|需要|请|我要).*?");
        boolean sizeOnly = productSize != null && input.length() <= 96
                && !input.matches(".*(想做|做成|设计|主题|灵感|结合|希望|需要|请|我要).*?");
        if (!selectionOnly && !materialOnly && !sizeOnly) {
            brief.put("inspiration", input);
            brief.put("inspirationSource", "text");
            if (blank(text(brief.get("referenceAssetId")))) brief.put("mode", "text");
        } else if (blank(text(brief.get("mode"))) && matchedProduct != null) {
            brief.put("mode", "text");
        }
    }

    private String extractExplicitMaterial(String input) {
        if (blank(input)) return null;
        String normalized = input.trim().replaceAll("\\s+", "");
        String[] materialTokens = {
                "水晶超柔", "超柔", "毛线", "毛绒", "毛毡", "布艺", "棉布", "帆布",
                "亚克力", "合金", "锌合金", "陶瓷", "硅胶", "木质", "木材", "纸质",
                "不锈钢", "玻璃", "树脂", "PVC", "皮革", "牛皮纸", "磁性材料"
        };
        for (String token : materialTokens) {
            if (normalized.contains(token)) return token;
        }
        return null;
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
            String family = productFamilyToken(name);
            if (!blank(family) && normalized.contains(family.toLowerCase(Locale.ROOT))) score += 70;
            for (String token : tags.split("[,，/、]")) {
                if (token.trim().length() >= 2 && normalized.contains(token.trim().toLowerCase(Locale.ROOT))) score += 8;
            }
            if (score > bestScore) { best = row; bestScore = score; }
        }
        if (best != null) return best;
        if (input.length() <= 30) return findCatalogOptionByName(input, userId);
        return null;
    }

    /**
     * Maps a user-facing product family to the canonical catalog option. The
     * catalog stores manufacturable variants such as “合金冰箱贴”, while users
     * naturally say “兵马俑冰箱贴” or “城市地标冰箱贴”.
     */
    private String productFamilyToken(String name) {
        if (blank(name)) return "";
        String[] families = {
                "冰箱贴", "钥匙扣", "徽章", "胸针", "书签", "明信片", "贴纸",
                "笔记本", "本册", "抱枕", "毛巾", "公仔", "潮玩", "毛绒",
                "杯垫", "马克杯", "保温杯", "随行杯", "帆布包", "手提袋",
                "T恤", "吊坠", "耳钉", "耳坠", "项链", "手链", "手镯", "摆件"
        };
        for (String family : families) if (name.contains(family)) return family;
        return "";
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
        String explicit = extractExplicitMaterial(requested);
        return explicit != null ? explicit : (blank(options) ? requested.trim() : null);
    }

    private String extractProductSize(String input) {
        if (blank(input)) return null;
        Matcher dimensions = DIMENSION_PATTERN.matcher(input);
        if (dimensions.find()) return normalizeProductSize(dimensions.group());
        Matcher standardSize = STANDARD_SIZE_PATTERN.matcher(input);
        if (standardSize.find()) return normalizeProductSize(standardSize.group());
        Matcher namedDimensions = NAMED_DIMENSION_PATTERN.matcher(input);
        if (namedDimensions.find()) return normalizeProductSize(namedDimensions.group());
        String normalized = input.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 80 && normalized.matches(".*(掌心|手掌|口袋|随身|桌面|迷你|小号|中号|大号|常规|标准).*(尺寸|大小|规格)?.*")) {
            return normalized;
        }
        return null;
    }

    private String extractBareNumericSize(String input) {
        if (blank(input)) return null;
        String normalized = input.trim().replaceAll("\\s+", "");
        if (!normalized.matches("\\d{1,4}(?:\\.\\d+)?")) return null;
        return "约 " + normalized + "mm";
    }

    private String normalizeProductSize(String requested) {
        if (blank(requested)) return null;
        String normalized = requested.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 120) return null;
        if (DIMENSION_PATTERN.matcher(normalized).find() || STANDARD_SIZE_PATTERN.matcher(normalized).find()
                || NAMED_DIMENSION_PATTERN.matcher(normalized).find()
                || normalized.matches(".*(掌心|手掌|口袋|随身|桌面|迷你|小号|中号|大号|常规|标准).*(尺寸|大小|规格)?.*")) {
            return normalized;
        }
        return null;
    }

    private String recommendedProductSize(Map<String, Object> brief) {
        String specification = text(brief.get("sizeRecommendation"));
        String product = text(brief.get("productName"));
        String concreteSpecification = concreteSizeFromSpecification(specification);
        if (concreteSpecification != null) return concreteSpecification;
        String productDefault = defaultSizeForProduct(product);
        if (productDefault != null) return productDefault;
        String categoryDefault = defaultSizeForCategory(text(brief.get("categoryKey")));
        return categoryDefault == null ? "80×80×8mm" : categoryDefault;
    }

    /**
     * Catalog specifications may be ranges ("4-8cm") or non-dimensional
     * manufacturing notes ("随型", "定制"). Only reuse a specification when
     * it already identifies one concrete finished size.
     */
    private String concreteSizeFromSpecification(String specification) {
        if (blank(specification)) return null;
        String normalized = specification.trim().replaceAll("\\s+", " ");

        Matcher named = NAMED_DIMENSION_PATTERN.matcher(normalized);
        while (named.find()) {
            String candidate = named.group().trim();
            if (!containsDimensionRange(candidate)) return candidate;
        }

        Matcher dimensions = DIMENSION_PATTERN.matcher(normalized);
        while (dimensions.find()) {
            String candidate = dimensions.group().trim();
            boolean multipleDimensions = candidate.matches("(?i).*(?:[x×*]|乘).*");
            boolean entireSpecification = candidate.equalsIgnoreCase(normalized);
            boolean startsInsideRange = dimensions.start() > 0
                    && "-~～至到".indexOf(normalized.charAt(dimensions.start() - 1)) >= 0;
            if (!startsInsideRange && !containsDimensionRange(candidate)
                    && (multipleDimensions || entireSpecification)) {
                return candidate;
            }
        }

        Matcher standardSize = STANDARD_SIZE_PATTERN.matcher(normalized);
        if (standardSize.find()) {
            String value = standardSize.group().toUpperCase(Locale.ROOT);
            if ("A3".equals(value)) return "A3（297×420mm）";
            if ("A4".equals(value)) return "A4（210×297mm）";
            if ("A5".equals(value)) return "A5（148×210mm）";
            if ("A6".equals(value)) return "A6（105×148mm）";
        }
        return null;
    }

    private boolean containsDimensionRange(String value) {
        return value != null && value.matches(".*\\d(?:\\.\\d+)?\\s*[-~～至到]\\s*\\d.*");
    }

    private String defaultSizeForProduct(String product) {
        if (blank(product)) return null;
        if (product.contains("冰箱贴")) return "60×60×4mm";
        if (product.contains("钥匙扣")) return "50×50×4mm";
        if (product.contains("徽章") || product.contains("胸针") || product.contains("纪念章") || product.endsWith("币")) return "58×58×3mm";
        if (product.contains("书签")) return "40×120×1.2mm";
        if (product.contains("明信片")) return "A6（105×148mm）";
        if (product.contains("贴纸")) return "50×50mm";
        if (product.contains("本册") || product.contains("笔记本") || product.contains("打卡本")) return "A5（148×210mm）";
        if (product.contains("抱枕")) return "400×400×120mm";
        if (product.contains("毛巾")) return "200×700mm";
        if (product.contains("冰淇淋") || product.contains("冰激凌")) return "成品约 80×45×12mm，天然实木棒 100-120mm";
        if (product.contains("公仔") || product.contains("潮玩") || product.contains("毛绒")) return "高 130mm";
        if (product.contains("杯垫")) return "100×100×5mm";
        if (product.contains("马克杯")) return "直径 80mm、高 95mm";
        if (product.contains("保温杯") || product.contains("随行杯")) return "直径 70mm、高 200mm";
        if (product.contains("帆布") && product.contains("包") || product.contains("手提袋")) return "350×300×100mm";
        if (product.contains("T 恤") || product.contains("T恤")) return "衣长 680mm、胸宽 500mm";
        if (product.contains("吊坠")) return "30×30×3mm";
        if (product.contains("耳钉")) return "12×12×3mm";
        if (product.contains("耳坠")) return "15×30×3mm";
        if (product.contains("项链") || product.contains("颈链")) return "链长 450mm";
        if (product.contains("手镯") || product.contains("手链")) return "周长 170mm";
        if (product.contains("摆件") || product.contains("工艺品")) return "150×150×200mm";
        return null;
    }

    private String defaultSizeForCategory(String category) {
        if (blank(category)) return null;
        if ("food".equals(category)) return "180×120×50mm";
        if ("stationery".equals(category)) return "A5（148×210mm）";
        if ("daily".equals(category)) return "300×300×80mm";
        if ("toy".equals(category)) return "高 130mm";
        if ("tableware".equals(category)) return "100×100×100mm";
        if ("souvenir".equals(category)) return "60×60×4mm";
        if ("accessory".equals(category)) return "35×35×3mm";
        if ("apparel".equals(category)) return "350×300×100mm";
        if ("craft".equals(category)) return "150×150×200mm";
        if ("precious".equals(category)) return "40×40×3mm";
        return null;
    }

    private boolean hasRequiredFieldsBeforeSize(Map<String, Object> brief) {
        return !blank(text(brief.get("productKey")))
                && !blank(text(brief.get("material")))
                && !blank(text(brief.get("inspiration")))
                && !blank(text(brief.get("mode")))
                && (!"image".equals(text(brief.get("mode"))) || !blank(text(brief.get("referenceAssetId"))));
    }

    private void clearProductSelection(Map<String, Object> brief) {
        brief.remove("productKey");
        brief.remove("productName");
        brief.remove("categoryName");
        brief.remove("material");
        brief.remove("materialOptions");
        brief.remove("materialRecommended");
        brief.remove("productSize");
        brief.remove("sizeRecommended");
        brief.remove("sizeRecommendation");
    }

    private PlannerDecision planTurn(String message, Map<String, Object> action, Map<String, Object> brief,
                                     List<Map<String, Object>> catalog) {
        if (blank(message) && action.isEmpty()) return new PlannerDecision("", Map.of(), "", "");
        String catalogText = catalog.stream().limit(120)
                .map(row -> String.join("|", value(row, "optionKey"), value(row, "name"), value(row, "categoryKey"), value(row, "material"), value(row, "process")))
                .reduce((left, right) -> left + "\n" + right).orElse("");
        String system = "你是‘之间智造’的对话式文创产品设计师。你要像豆包一样自然聊天，但每次只推进一个最必要的问题。先判断用户意图：answer_question=普通咨询/知识问答/市场建议，只回答不修改创作档案；recommend_direction=给出产品方向建议；choose_product/provide_inspiration/provide_material/provide_size=用户明确在填写创作信息；confirm_generate=确认生成；edit_brief=修改已有档案。普通问句如‘什么卖得最好、哪个好、有什么推荐、怎么选’默认是answer_question。只有用户明确说‘我要做/设计/生成/做成/主题/灵感/希望’等创作表达时，才写入创作档案。\n"
                + "必须只输出JSON，不要Markdown，格式：{\"intent\":\"answer_question|recommend_direction|choose_product|provide_inspiration|provide_material|provide_size|confirm_generate|edit_brief\",\"reply\":\"给用户看的简短中文回复\",\"productKey\":\"选品表中的optionKey或空\",\"productName\":\"或空\",\"categoryKey\":\"或空\",\"material\":\"或空\",\"productSize\":\"用户明确的成品尺寸/规格或空\",\"inspiration\":\"用户明确提供的创作灵感或空\",\"mode\":\"text/image/template或空\",\"suggestedDirection\":\"咨询后可选的创作方向，无法建议则空\",\"ready\":false}\n"
                + "产品只能从提供的选品目录中选择，不能创造不存在的产品；不要强制询问颜色和视觉风格，按产品自动处理；材质不确定时允许推荐。生成前必须已明确产品、灵感、材质和成品尺寸；尺寸优先记录用户明确的长宽高/厚度、直径或 A 系列规格，用户说“你帮我推荐”时不要伪造尺寸，由系统按选品规格推荐。信息足够时ready=true；回复不要重复询问已经有的字段，最多两句话。\n"
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
            fields.put("productSize", node.path("productSize").asText(""));
            fields.put("inspiration", node.path("inspiration").asText(""));
            fields.put("mode", node.path("mode").asText(""));
            fields.put("ready", node.path("ready").asBoolean(false));
            return new PlannerDecision(node.path("reply").asText(""), fields,
                    node.path("intent").asText(""), node.path("suggestedDirection").asText(""));
        } catch (Exception ignored) {
            return new PlannerDecision("", Map.of(), "", "");
        }
    }

    /**
     * Ordinary questions use a separate SiliconFlow prompt from the creative
     * planner. This prevents market advice from being mistaken for a product
     * brief and makes the model answer the user's question directly.
     */
    private PlannerDecision answerQuestionTurn(String message, Map<String, Object> brief,
                                               List<Map<String, Object>> catalog) {
        String catalogText = catalog.stream().limit(80)
                .map(row -> value(row, "name"))
                .filter(name -> !blank(name))
                .distinct()
                .reduce((left, right) -> left + "、" + right).orElse("");
        String system = "你是‘之间智造’的市场咨询助手，不是创作流程引导器。直接回答用户的问题，不要反问用户想做什么产品，不要要求用户选择品类，不要输出JSON，不要把用户原话改写成创作提示词，也不要修改任何创作档案。\n"
                + "用户咨询文创、景区销售或产品方向时，请给出具体、可执行的中文回答，说明推荐品类和原因；信息不足时可以明确说明是趋势判断，但仍要先给出答案。回答控制在2到4句话。可参考当前系统支持的产品目录："
                + (blank(catalogText) ? "（目录暂不可用）" : catalogText);
        try {
            String raw = siliconFlow.chat(system, message, 0.35, 700, 30);
            String reply = raw == null ? "" : raw.trim();
            String suggestedDirection = "";
            try {
                JsonNode node = parseJsonNode(raw);
                if (node.isObject()) {
                    reply = firstNonBlank(node.path("answer").asText(""), node.path("reply").asText(""), reply);
                    suggestedDirection = node.path("suggestedDirection").asText("");
                }
            } catch (Exception ignored) {
                // SiliconFlow is allowed to return normal text for Q&A.
            }
            return new PlannerDecision(reply, Map.of(), "answer_question", suggestedDirection);
        } catch (Exception ignored) {
            return new PlannerDecision("我暂时无法连接市场咨询模型，请稍后再试。", Map.of(), "answer_question", "");
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (!blank(value)) return value.trim();
        return "";
    }

    private boolean isLikelyQuestion(String message, Map<String, Object> brief) {
        if (blank(message)) return false;
        String value = message.trim();
        if (!blank(text(brief.get("productKey")))
                && value.matches(".*(你帮我推荐|帮我选|你来选|按推荐规格|推荐材质|推荐尺寸).*") ) {
            return false;
        }
        if (value.matches(".*(我想做|我要做|想做一个|想做一款|帮我做|请帮我设计|帮我设计|请生成|帮我生成|设计一个|设计一款|生成一个|生成一款|做成|主题是|灵感是|结合|希望做|请做|做一个|做一款).*") ) {
            return false;
        }
        return value.matches(".*(什么|哪种|哪个|哪些|怎么|如何|为什么|卖得|卖的|好卖|推荐|适合|有没有|能不能|可以吗|值得|价格|多少钱|成本|利润).*")
                || value.matches(".*[？?]$");
    }

    private boolean shouldApplyCreativeInput(String intent, String message, Map<String, Object> brief) {
        if ("answer_question".equals(intent)) return false;
        if (Set.of("choose_product", "provide_inspiration", "provide_material", "provide_size", "edit_brief").contains(intent)) return true;
        if (blank(message)) return false;
        String value = message.trim();
        if (isRecommendationMessage(value)) return !blank(text(brief.get("productKey")));
        if (!blank(text(brief.get("productKey")))
                && (extractExplicitMaterial(value) != null || extractProductSize(value) != null
                || extractBareNumericSize(value) != null)) return true;
        if (!blank(text(brief.get("inspiration")))
                && value.matches(".*(再?补充|增加|添加|改成|调整|修改|去掉|删掉|保留|加强|弱化|避免).*") ) return true;
        boolean explicitCreation = value.matches(".*(我想做|我要做|想做一个|想做一款|帮我做|请帮我设计|帮我设计|请生成|帮我生成|设计一个|设计一款|生成一个|生成一款|做成|主题是|灵感是|结合|希望做|请做|做一个|做一款).*");
        if (explicitCreation) return true;
        return !value.matches(".*(什么|怎么|如何|为什么|哪个好|哪些|是否|能不能|可以吗|推荐吗|吗[？?]?$|[？?]$).*")
                && value.matches(".*(设计|生成|创作|制作).*");
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
        String productSize = normalizeProductSize(text(fields.get("productSize")));
        if (productSize != null) {
            brief.put("productSize", productSize);
            brief.put("sizeRecommended", false);
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
        String productSize = normalizeProductSize(text(brief.get("productSize")));
        if (productSize != null) {
            brief.put("productSize", productSize);
        } else {
            brief.remove("productSize");
            brief.remove("sizeRecommended");
        }
    }

    private boolean hasRequiredBrief(Map<String, Object> brief) {
        boolean base = !blank(text(brief.get("productKey")))
                && !blank(text(brief.get("material")))
                && !blank(text(brief.get("productSize")))
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
        if (blank(text(brief.get("productSize")))) return "need_size";
        return "understanding";
    }

    private String fallbackReply(Map<String, Object> brief, List<Map<String, Object>> catalog, boolean ready) {
        if (ready) return "我已经理解你的产品方向、灵感和材质，现在直接生成可生产方向的产品图。";
        if (blank(text(brief.get("productKey")))) return "你想把这个灵感做成什么产品？可以直接输入，也可以从下面选择。";
        if ("image".equals(text(brief.get("mode"))) && blank(text(brief.get("referenceAssetId")))) return "请先上传一张你有权使用的灵感图片，我会保留主体和可识别细节。";
        if (blank(text(brief.get("inspiration"))) && !"template".equals(brief.get("mode"))) return "说说你的灵感即可，不需要写专业提示词；也可以上传一张参考图。";
        if (blank(text(brief.get("material")))) return "材质不确定也没关系，我会根据产品结构和量产工艺帮你推荐。";
        if (blank(text(brief.get("productSize")))) return "这件产品想做多大？例如 60×60×3mm、直径 80mm 或 A5；不确定时可以让我按常用规格推荐。";
        return "材质不确定也没关系，我会根据产品结构和量产工艺帮你推荐。";
    }

    private List<Map<String, Object>> quickReplies(Map<String, Object> brief, List<Map<String, Object>> catalog,
                                                   boolean complete, boolean ready) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (ready) return result;
        if (complete) {
            result.add(reply("没有补充，开始生成", "confirm_generate", "confirm"));
            result.add(reply("我还要补充", "add_detail", ""));
            return result;
        }
        if (blank(text(brief.get("productKey")))) {
            if (!blank(text(brief.get("categoryKey")))) {
                result.add(reply("返回选择大品类", "edit", "product"));
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
            return result;
        }
        if (blank(text(brief.get("productSize")))) {
            result.add(reply("按推荐规格", "size", "recommend"));
        }
        return result;
    }

    private boolean isGenerationConfirmationAction(Map<String, Object> action) {
        return "confirm_generate".equals(text(action.get("type")));
    }

    private boolean isGenerationConfirmationMessage(String message) {
        if (blank(message)) return false;
        String value = message.trim();
        return value.matches(".*(没有|无|不需要|不用).*(补充|修改|添加|意见).*|.*(直接|开始|确认).*(生成|出图).*|^(没有|没有了|无|无了|就这样|不用补充)$");
    }

    private void applyGenerationConfirmationState(Map<String, Object> brief, Map<String, Object> action, String message) {
        String type = text(action.get("type"));
        if (isGenerationConfirmationAction(action) || isGenerationConfirmationMessage(message)) {
            brief.put("generationConfirmed", true);
            return;
        }
        if ("add_detail".equals(type) || !blank(message)
                || (type != null && Set.of("product", "category", "material", "size", "template", "image", "text", "edit", "adopt_direction").contains(type))) {
            brief.put("generationConfirmed", false);
        }
    }

    private boolean isTrue(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }

    private boolean isRecommendationMessage(String message) {
        if (blank(message)) return false;
        return message.trim().length() <= 32 && message.matches(".*(推荐|帮我选|你来选|按推荐规格).*" );
    }

    private boolean isStaleProgressReply(String reply, Map<String, Object> brief) {
        if (blank(reply)) return true;
        String value = reply.trim();
        if (!blank(text(brief.get("productKey")))) {
            if (value.matches(".*(做成什么产品|选择.*产品|产品方向).*")) return true;
        }
        if (!blank(text(brief.get("inspiration")))) {
            if (value.matches(".*(说说你的灵感|描述.*灵感|上传.*参考图).*")) return true;
        }
        if (!blank(text(brief.get("material")))) {
            if (value.matches(".*(材质不确定|确认材质|选择材质|推荐材质).*")) return true;
        }
        return !blank(text(brief.get("productSize")))
                && value.matches(".*(想做多大|成品尺寸|确认.*尺寸|推荐规格).*");
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
            brief.remove("productSize");
            brief.remove("sizeRecommended");
        }
        brief.put("productKey", product.get("optionKey"));
        brief.put("productName", product.get("name"));
        brief.put("categoryKey", product.get("categoryKey"));
        brief.put("categoryName", product.get("categoryName"));
        brief.put("materialOptions", product.get("material"));
        String specification = value(product, "specification");
        if (!blank(specification)) brief.put("sizeRecommendation", specification);
        else brief.remove("sizeRecommendation");
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
        StringBuilder sql = new StringBuilder("SELECT o.option_key optionKey,o.category_key categoryKey,c.name categoryName,o.name,o.material,o.process,o.specification,o.description,o.tags FROM selection_option o JOIN selection_category c ON c.category_key=o.category_key WHERE o.enabled=1 AND o.review_status='approved' AND c.enabled=1 AND c.review_status='approved'");
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
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.option_key optionKey,o.category_key categoryKey,c.name categoryName,o.name,o.material,o.process,o.specification,o.description,o.tags FROM selection_option o JOIN selection_category c ON c.category_key=o.category_key WHERE o.option_key=? AND o.enabled=1 AND o.review_status='approved' AND c.enabled=1 AND c.review_status='approved' LIMIT 1", optionKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findCatalogOptionByName(String name, long userId) {
        if (blank(name)) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT o.option_key optionKey,o.category_key categoryKey,c.name categoryName,o.name,o.material,o.process,o.specification,o.description,o.tags FROM selection_option o JOIN selection_category c ON c.category_key=o.category_key WHERE o.enabled=1 AND o.review_status='approved' AND c.enabled=1 AND c.review_status='approved' AND (o.name=? OR o.name LIKE ?) ORDER BY CHAR_LENGTH(o.name) DESC,o.sort_order LIMIT 1", name.trim(), "%" + name.trim() + "%");
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
        private final String intent;
        private final String suggestedDirection;
        private PlannerDecision(String reply, Map<String, Object> fields, String intent, String suggestedDirection) {
            this.reply = reply; this.fields = fields; this.intent = intent; this.suggestedDirection = suggestedDirection;
        }
    }

    private void updateSummary(long id, long userId, String step, Object payload) {
        Map<String, Object> values = payload instanceof Map<?, ?> raw ? toStringMap(raw) : Map.of();
        String mode = text(values.get("mode"));
        String productType = firstText(values, "productType", "product", "productName");
        String material = firstText(values, "material", "materialName");
        String productSize = firstText(values, "productSize", "dimensions", "size");
        if ("chat".equals(step)) {
            jdbc.update("UPDATE creative_conversation_session SET mode=?,product_type=?,material=?,product_size=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",
                    mode, productType, material, productSize, id, userId);
            return;
        }
        String status = "image".equals(step) || "model".equals(step) ? "completed" : null;
        jdbc.update("UPDATE creative_conversation_session SET mode=COALESCE(?,mode),product_type=COALESCE(?,product_type),material=COALESCE(?,material),product_size=COALESCE(?,product_size),status=COALESCE(?,status),updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",
                mode, productType, material, productSize, status, id, userId);
    }

    private void saveEvent(long sessionId, long userId, String step, String eventType, Object payload) {
        String payloadJson = json(payload);
        if (payloadJson.length() > MAX_PAYLOAD_LENGTH) throw new IllegalArgumentException("本次创作内容过长，请精简后重试");
        CreativeProjectService.ProjectRef project = projects.ensureForSession(sessionId, userId, null);
        jdbc.update("INSERT INTO creative_conversation_event (session_id,user_id,project_id,version_id,step,event_type,payload_json) VALUES (?,?,?,?,?,?,?)",
                sessionId, userId, project.projectId(), project.versionId(), step, eventType, payloadJson);
        projects.appendEvent(project.projectId(), userId, project.versionId(), eventType, null,
                projectStage(step, eventType), "user", userId, null, payload);
    }

    private Map<String, Object> getOwnedSession(long id, long userId) {
        List<Map<String, Object>> sessions = jdbc.queryForList("SELECT id,session_no sessionNo,user_id userId,mode,product_type productType,material,product_size productSize,status,project_id projectId,version_id versionId,created_at createdAt,updated_at updatedAt FROM creative_conversation_session WHERE id=? AND user_id=?", id, userId);
        if (sessions.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "创作会话不存在");
        Map<String, Object> result = new LinkedHashMap<>(sessions.get(0));
        CreativeProjectService.ProjectRef project = projects.ensureForSession(id, userId, text(result.get("mode")));
        result.put("projectId", project.projectId());
        result.put("versionId", project.versionId());
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

    private String projectStage(String step, String eventType) {
        if ("image".equals(step) || "model".equals(step) || "generation_started".equals(eventType)) return "generating";
        if ("commercial".equals(step) || "compliance".equals(step)) return "engineering_check";
        if ("multiview".equals(step)) return "candidate_selected";
        if ("navigation".equals(step) && (eventType != null && eventType.contains("review"))) return "human_review";
        if ("summary".equals(step) || "size".equals(step) || "material".equals(step) || "product".equals(step) || "inspiration".equals(step)) return "brief_ready";
        return "brief";
    }

    private Number generatedId(KeyHolder holder) {
        try {
            Number key = holder.getKey();
            if (key != null) return key;
        } catch (org.springframework.dao.InvalidDataAccessApiUsageException ignored) {
            // H2 may include generated timestamp columns in the key row.
        }
        for (Map<String, Object> row : holder.getKeyList()) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if ("ID".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number number) return number;
            }
        }
        return null;
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
