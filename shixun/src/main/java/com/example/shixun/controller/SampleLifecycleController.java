package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.SampleLogisticsService;
import com.example.shixun.service.SampleLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** C端样品反馈、返修、验收与量产解锁。 */
@RestController
@RequestMapping("/api/creative/projects")
public class SampleLifecycleController {
    private final SampleLifecycleService lifecycle;
    private final SampleLogisticsService logistics;
    private final JdbcTemplate jdbc;

    public SampleLifecycleController(SampleLifecycleService lifecycle, SampleLogisticsService logistics, JdbcTemplate jdbc) {
        this.lifecycle = lifecycle;
        this.logistics = logistics;
        this.jdbc = jdbc;
    }

    @GetMapping("/{projectId}/versions/{versionId}/sample-lifecycle/{requestId}")
    public Map<String, Object> detail(@PathVariable Long projectId, @PathVariable Long versionId, @PathVariable Long requestId,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        lifecycle.assertRoute(requestId, projectId, versionId, userId);
        return lifecycle.lifecycle(requestId, userId);
    }

    @PostMapping("/{projectId}/versions/{versionId}/sample-lifecycle/{requestId}/feedback")
    public Map<String, Object> feedback(@PathVariable Long projectId, @PathVariable Long versionId, @PathVariable Long requestId,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal); lifecycle.assertRoute(requestId, projectId, versionId, userId);
        return lifecycle.submitFeedback(requestId, userId, body);
    }

    @PostMapping("/{projectId}/versions/{versionId}/sample-lifecycle/{requestId}/revision")
    public Map<String, Object> revision(@PathVariable Long projectId, @PathVariable Long versionId, @PathVariable Long requestId,
                                        @RequestBody(required = false) Map<String, Object> body,
                                        @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal); lifecycle.assertRoute(requestId, projectId, versionId, userId);
        return lifecycle.requestRevision(requestId, userId, body);
    }

    @PostMapping("/{projectId}/versions/{versionId}/sample-lifecycle/{requestId}/accept")
    public Map<String, Object> accept(@PathVariable Long projectId, @PathVariable Long versionId, @PathVariable Long requestId,
                                      @RequestBody(required = false) Map<String, Object> body,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal); lifecycle.assertRoute(requestId, projectId, versionId, userId);
        return lifecycle.acceptSample(requestId, userId, body);
    }

    @PostMapping("/{projectId}/versions/{versionId}/sample-lifecycle/{requestId}/bulk-unlock")
    public Map<String, Object> bulkUnlock(@PathVariable Long projectId, @PathVariable Long versionId, @PathVariable Long requestId,
                                          @RequestBody(required = false) Map<String, Object> body,
                                          @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal); lifecycle.assertRoute(requestId, projectId, versionId, userId);
        return lifecycle.unlockBulk(requestId, userId, body);
    }

    /** C端只能读取自己项目版本下的样品物流和轨迹。 */
    @GetMapping("/{projectId}/versions/{versionId}/sample-lifecycle/{requestId}/logistics")
    public Map<String, Object> logistics(@PathVariable Long projectId, @PathVariable Long versionId, @PathVariable Long requestId,
                                         @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        lifecycle.assertRoute(requestId, projectId, versionId, userId);
        return logistics.forConsumer(requestId, userId);
    }

    private Long requireConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"user".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是C端用户");
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user' AND COALESCE(status,'active')='active'", Integer.class, principal.userId());
        if (active == null || active == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        return principal.userId();
    }
}
