package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.LinkedHashMap;
import java.util.List;
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
    private static final Set<String> STEPS = Set.of("welcome", "mode", "product", "inspiration", "material", "style", "summary", "image", "multiview", "model", "commercial", "compliance", "navigation");
    private static final int MAX_PAYLOAD_LENGTH = 12000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ConversationalCreativeController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
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
