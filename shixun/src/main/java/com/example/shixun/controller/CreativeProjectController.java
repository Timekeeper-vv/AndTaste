package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.CreativeProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

/**
 * C端统一创作项目入口。旧的生成、对话和打样接口继续兼容；本资源只
 * 提供项目/版本/时间线的公共数据边界，后续流程可以逐步接入。
 */
@RestController
@RequestMapping("/api/creative/projects")
public class CreativeProjectController {
    /** Phases a consumer may advance directly; review/production phases are
     * written only by the dedicated staff, payment, factory and lifecycle
     * boundaries. */
    private static final Set<String> CONSUMER_PHASES = Set.of(
            "brief", "generation", "multiview", "preflight", "ai_review",
            "human_review", "needs_revision", "cancelled");
    private final CreativeProjectService projects;
    private final JdbcTemplate jdbc;

    public CreativeProjectController(CreativeProjectService projects, JdbcTemplate jdbc) {
        this.projects = projects;
        this.jdbc = jdbc;
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody(required = false) Map<String, Object> body,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return projects.createProject(requireConsumer(principal), body);
    }

    @GetMapping({"", "/mine"})
    public Map<String, Object> mine(@RequestParam(defaultValue = "50") int limit,
                                    @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return Map.of("projects", projects.listProjects(requireConsumer(principal), limit));
    }

    @GetMapping("/{projectId}")
    public Map<String, Object> detail(@PathVariable Long projectId,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return notFound(() -> projects.getProject(projectId, requireConsumer(principal)));
    }

    @GetMapping("/{projectId}/timeline")
    public Map<String, Object> timeline(@PathVariable Long projectId,
                                        @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return notFound(() -> Map.of("projectId", projectId, "events", projects.timeline(projectId, requireConsumer(principal))));
    }

    @GetMapping("/{projectId}/versions/{versionId}")
    public Map<String, Object> version(@PathVariable Long projectId, @PathVariable Long versionId,
                                       @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return notFound(() -> projects.getVersion(projectId, versionId, requireConsumer(principal)));
    }

    @PostMapping("/{projectId}/versions")
    public Map<String, Object> createVersion(@PathVariable Long projectId,
                                             @RequestBody(required = false) Map<String, Object> body,
                                             @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Map<String, Object> request = body == null ? Map.of() : body;
        assertConsumerPhase(text(request.get("phase")));
        return notFound(() -> projects.createVersion(projectId, requireConsumer(principal), request));
    }

    @PostMapping("/{projectId}/events")
    public Map<String, Object> appendEvent(@PathVariable Long projectId,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        Map<String, Object> request = body == null ? Map.of() : body;
        String toPhase = text(request.get("toPhase"));
        assertConsumerPhase(toPhase);
        return notFound(() -> projects.appendEvent(projectId, longValue(request.get("versionId")), userId,
                text(request.get("eventType")), toPhase, text(request.get("nextAction")),
                "user", objectMap(request.get("payload"))));
    }

    @PostMapping("/{projectId}/transition")
    public Map<String, Object> transition(@PathVariable Long projectId,
                                          @RequestBody(required = false) Map<String, Object> body,
                                          @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        Map<String, Object> request = body == null ? Map.of() : body;
        Long versionId = longValue(request.get("versionId"));
        if (versionId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "阶段流转必须指定版本");
        assertConsumerPhase(text(request.get("toPhase")));
        if (Boolean.TRUE.equals(request.get("freeze"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "C端不能直接冻结生产版本");
        }
        return notFound(() -> projects.transition(projectId, versionId, userId,
                text(request.get("toPhase")), text(request.get("eventType")), "user",
                objectMap(request.get("payload")), text(request.get("idempotencyKey")),
                false, null));
    }

    @PostMapping("/{projectId}/versions/{versionId}/freeze")
    public Map<String, Object> freeze(@PathVariable Long projectId, @PathVariable Long versionId,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireConsumer(principal);
        // Freezing creates the immutable snapshot consumed by review, payment,
        // sampling and production. It is therefore a server-owned workflow
        // boundary; C端 clients can trigger it indirectly through their
        // submission endpoints, but cannot freeze an arbitrary draft version.
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "设计版本只能在提交审核或生产申请时由系统冻结");
    }

    @PostMapping("/{projectId}/links")
    public Map<String, Object> link(@PathVariable Long projectId,
                                    @RequestBody(required = false) Map<String, Object> body,
                                    @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        Map<String, Object> request = body == null ? Map.of() : body;
        String entityType = text(request.get("entityType"));
        if (entityType == null || entityType.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "关联类型不能为空");
        return notFound(() -> projects.link(projectId, longValue(request.get("versionId")), longValue(request.get("entityId")), entityType, userId));
    }

    private Long requireConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"user".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是C端用户");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user' AND COALESCE(status,'active')='active'", Integer.class, principal.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        return principal.userId();
    }

    private <T> T notFound(SupplierWithException<T> call) {
        try { return call.get(); }
        catch (NoSuchElementException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()); }
    }

    private Long longValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编号格式无效"); }
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }

    private void assertConsumerPhase(String phase) {
        if (phase == null || phase.isBlank()) return;
        String normalized = switch (phase.trim().toLowerCase(Locale.ROOT)) {
            case "brief_ready" -> "brief";
            case "generating" -> "generation";
            case "candidate_selected" -> "multiview";
            case "engineering_check" -> "preflight";
            case "design_review" -> "ai_review";
            case "review" -> "human_review";
            default -> phase.trim().toLowerCase(Locale.ROOT);
        };
        if (!CONSUMER_PHASES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前阶段只能由平台审核、支付或工厂流程推进");
        }
    }

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    @FunctionalInterface
    private interface SupplierWithException<T> { T get(); }
}
