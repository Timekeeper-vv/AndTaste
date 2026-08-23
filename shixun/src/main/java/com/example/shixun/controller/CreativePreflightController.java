package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.CreativePreflightService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** Project/version-scoped manufacturing gate for the consumer workflow. */
@RestController
@RequestMapping("/api/creative/projects")
public class CreativePreflightController {
    private final CreativePreflightService preflight;
    private final JdbcTemplate jdbc;

    public CreativePreflightController(CreativePreflightService preflight, JdbcTemplate jdbc) {
        this.preflight = preflight;
        this.jdbc = jdbc;
    }

    @PostMapping("/{projectId}/versions/{versionId}/preflight")
    public Map<String, Object> run(@PathVariable Long projectId, @PathVariable Long versionId,
                                   @RequestBody(required = false) Map<String, Object> body,
                                   @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return preflight.run(projectId, versionId, requireConsumer(principal), body);
    }

    @GetMapping("/{projectId}/versions/{versionId}/preflight/latest")
    public Map<String, Object> latest(@PathVariable Long projectId, @PathVariable Long versionId,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        return preflight.latest(projectId, versionId, requireConsumer(principal));
    }

    private Long requireConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"user".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是C端用户");
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user' AND COALESCE(status,'active')='active'", Integer.class, principal.userId());
        if (active == null || active == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        return principal.userId();
    }
}
