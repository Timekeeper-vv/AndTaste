package com.example.shixun.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterMaterialLabTest {
    private final JwtService jwtService = new JwtService(
            new ObjectMapper(),
            "test-only-jwt-secret-that-is-long-enough-2026",
            900
    );

    @Test
    void materialLabTokenIsAcceptedOnlyForItsReadAndVariantUploadRoutes() throws Exception {
        String labToken = jwtService.issueMaterialLabAccessToken(7L, "creative-user", "user", 42L);
        JwtAuthenticationFilter filter = filterForCreativeUser();

        assertThat(run(filter, request("GET", "/api/creative/ai/assets/42/model-content", labToken))).isEqualTo(200);
        assertThat(run(filter, request("GET", "/api/creative/ai/assets/42/preview-content", labToken))).isEqualTo(200);
        assertThat(run(filter, request("POST", "/api/creative/ai/assets/42/material-variants", labToken))).isEqualTo(200);

        assertThat(run(filter, request("GET", "/api/creative/ai/assets/43/model-content", labToken))).isEqualTo(401);
        assertThat(run(filter, bearerRequest("GET", "/api/creative/ai/jobs", labToken))).isEqualTo(401);
        assertThat(run(filter, request("GET", "/api/creative/ai/jobs", labToken))).isEqualTo(401);
    }

    @Test
    void ordinaryReadTokenCannotUploadAMaterialVariant() throws Exception {
        String readToken = jwtService.issueMediaAccessToken(7L, "creative-user", "user", 42L);

        assertThat(run(filterForCreativeUser(), request("POST", "/api/creative/ai/assets/42/material-variants", readToken)))
                .isEqualTo(401);
    }

    @Test
    void professionalSubmissionTokenIsAcceptedOnlyForItsDownloadRoute() throws Exception {
        String downloadToken = jwtService.issueProfessionalSubmissionAccessToken(7L, "creative-user", "user", 48L);
        JwtAuthenticationFilter filter = filterForCreativeUser();

        assertThat(run(filter, request("GET", "/api/creative/ai/consumer-professional-submissions/48/download", downloadToken)))
                .isEqualTo(200);
        assertThat(run(filter, request("GET", "/api/creative/ai/consumer-professional-submissions/49/download", downloadToken)))
                .isEqualTo(401);
        assertThat(run(filter, request("GET", "/api/creative/ai/assets/48/model-content", downloadToken)))
                .isEqualTo(401);
    }

    private JwtAuthenticationFilter filterForCreativeUser() {
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                return List.of(Map.of("username", "creative-user", "role", "user"));
            }
        };
        return new JwtAuthenticationFilter(jwtService, new ObjectMapper(), jdbc);
    }

    private MockHttpServletRequest request(String method, String path, String accessToken) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setParameter("access_token", accessToken);
        return request;
    }

    private MockHttpServletRequest bearerRequest(String method, String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private int run(JwtAuthenticationFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> chainCalled.set(true);
        filter.doFilterInternal(request, response, chain);
        if (response.getStatus() == 200) assertThat(chainCalled).isTrue();
        else assertThat(chainCalled).isFalse();
        return response.getStatus();
    }
}
