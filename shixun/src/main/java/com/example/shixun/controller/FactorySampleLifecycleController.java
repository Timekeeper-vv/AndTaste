package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.FactorySampleLifecycleService;
import com.example.shixun.service.SampleLogisticsService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** Factory/back-office milestones for C端 sample requests. */
@RestController
@RequestMapping("/api/production/sample-lifecycle")
public class FactorySampleLifecycleController {
    private static final Set<String> STAFF_ROLES = Set.of("admin", "technician", "feeder");
    private final FactorySampleLifecycleService lifecycle;
    private final SampleLogisticsService logistics;
    private final JdbcTemplate jdbc;

    public FactorySampleLifecycleController(FactorySampleLifecycleService lifecycle, SampleLogisticsService logistics, JdbcTemplate jdbc) {
        this.lifecycle = lifecycle;
        this.logistics = logistics;
        this.jdbc = jdbc;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "200") int size) {
        requireStaff();
        return lifecycle.list(status, keyword, size);
    }

    @GetMapping("/{requestId}")
    public Map<String, Object> detail(@PathVariable Long requestId) {
        requireStaff();
        return lifecycle.detail(requestId);
    }

    @PutMapping("/{requestId}/status")
    public Map<String, Object> updateStatus(@PathVariable Long requestId,
                                            @RequestBody(required = false) Map<String, Object> body) {
        JwtService.Claims principal = requireStaff();
        String status = body == null ? "" : text(body.get("status"));
        String comment = body == null ? "" : text(body.get("comment"));
        List<?> evidence = body == null || !(body.get("evidenceAssetIds") instanceof List<?> list) ? List.of() : list;
        return lifecycle.updateStatus(requestId, status, principal.userId(), principal.username(), comment, evidence);
    }

    @GetMapping("/{requestId}/logistics")
    public Map<String, Object> logistics(@PathVariable Long requestId) {
        requireStaff();
        return logistics.forStaff(requestId);
    }

    @PutMapping("/{requestId}/logistics")
    public Map<String, Object> updateLogistics(@PathVariable Long requestId,
                                               @RequestBody(required = false) Map<String, Object> body) {
        JwtService.Claims principal = requireStaff();
        return logistics.update(requestId, principal.userId(), body == null ? Map.of() : body);
    }

    @PostMapping("/{requestId}/logistics/exception")
    public Map<String, Object> markLogisticsException(@PathVariable Long requestId,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        JwtService.Claims principal = requireStaff();
        return logistics.markException(requestId, principal.userId(), body == null ? Map.of() : body);
    }

    @PostMapping("/{requestId}/logistics/resolve")
    public Map<String, Object> resolveLogisticsException(@PathVariable Long requestId,
                                                         @RequestBody(required = false) Map<String, Object> body) {
        JwtService.Claims principal = requireStaff();
        return logistics.resolveException(requestId, principal.userId(), body == null ? Map.of() : body);
    }

    private JwtService.Claims requireStaff() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null : attributes.getAttribute(
                JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof JwtService.Claims claims) || claims.userId() == null || claims.username() == null
                || claims.username().isBlank() || !STAFF_ROLES.contains(claims.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有后台生产人员可以处理样品生命周期");
        }
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role=? AND COALESCE(status,'active')='active'",
                Integer.class, claims.userId(), claims.role());
        if (active == null || active == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前后台账号已失效");
        return claims;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
