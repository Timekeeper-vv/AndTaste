package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.CreativeWorkflowDetailService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;

/** Single read boundary for the C端 and factory workflow detail views. */
@RestController
public class CreativeWorkflowDetailController {
    private final CreativeWorkflowDetailService workflows;
    private final JdbcTemplate jdbc;

    public CreativeWorkflowDetailController(CreativeWorkflowDetailService workflows, JdbcTemplate jdbc) {
        this.workflows = workflows;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/creative/workflow/requests/{requestId}")
    public Map<String, Object> consumer(@PathVariable Long requestId,
                                        @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return notFound(() -> workflows.forConsumer(requestId, requireConsumer(principal)));
    }

    @GetMapping("/api/production/workflow/{requestId}")
    public Map<String, Object> staff(@PathVariable Long requestId) {
        JwtService.Claims principal = requireStaff();
        return notFound(() -> workflows.forStaff(requestId, principal.role()));
    }

    private Long requireConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"user".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是C端用户");
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user' AND COALESCE(status,'active')='active'", Integer.class, principal.userId());
        if (active == null || active == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        return principal.userId();
    }

    private JwtService.Claims requireStaff() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        Object value = attributes == null ? null : attributes.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof JwtService.Claims claims) || claims.userId() == null || !java.util.Set.of("admin", "technician", "feeder").contains(claims.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有后台生产人员可以查看流程详情");
        }
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role=? AND COALESCE(status,'active')='active'", Integer.class, claims.userId(), claims.role());
        if (active == null || active == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前后台账号已失效");
        return claims;
    }

    private <T> T notFound(java.util.function.Supplier<T> supplier) {
        try { return supplier.get(); }
        catch (NoSuchElementException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage()); }
    }
}
