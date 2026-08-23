package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.SampleLogisticsService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Backend list of open sample-production and logistics exceptions. */
@RestController
@RequestMapping("/api/production/sample-logistics")
public class FactorySampleLogisticsController {
    private static final Set<String> STAFF_ROLES = Set.of("admin", "technician", "feeder");
    private final SampleLogisticsService logistics;
    private final JdbcTemplate jdbc;

    public FactorySampleLogisticsController(SampleLogisticsService logistics, JdbcTemplate jdbc) {
        this.logistics = logistics;
        this.jdbc = jdbc;
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts(@RequestParam(defaultValue = "100") int size) {
        requireStaff();
        return logistics.alerts(size);
    }

    @GetMapping("/{requestId}")
    public Map<String, Object> detail(@PathVariable Long requestId) {
        requireStaff();
        return logistics.forStaff(requestId);
    }

    @PutMapping("/{requestId}")
    public Map<String, Object> update(@PathVariable Long requestId,
                                      @RequestBody(required = false) Map<String, Object> body) {
        JwtService.Claims claims = requireStaff();
        return logistics.update(requestId, claims.userId(), body == null ? Map.of() : body);
    }

    @PostMapping("/{requestId}/exception")
    public Map<String, Object> markException(@PathVariable Long requestId,
                                              @RequestBody(required = false) Map<String, Object> body) {
        JwtService.Claims claims = requireStaff();
        return logistics.markException(requestId, claims.userId(), body == null ? Map.of() : body);
    }

    @PostMapping("/{requestId}/exception/resolve")
    public Map<String, Object> resolveException(@PathVariable Long requestId,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        JwtService.Claims claims = requireStaff();
        return logistics.resolveException(requestId, claims.userId(), body == null ? Map.of() : body);
    }

    private JwtService.Claims requireStaff() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null : attributes.getAttribute(
                JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof JwtService.Claims claims) || claims.userId() == null || claims.username() == null
                || claims.username().isBlank() || !STAFF_ROLES.contains(claims.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有后台生产人员可以查看样品物流预警");
        }
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role=? AND COALESCE(status,'active')='active'",
                Integer.class, claims.userId(), claims.role());
        if (active == null || active == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前后台账号已失效");
        return claims;
    }
}
