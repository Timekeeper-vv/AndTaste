package com.example.shixun.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApiRateLimitFilterTest {
    @Test
    void blocksTheEleventhPasswordLoginFromOneAddress() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(new ObjectMapper());
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = invoke(filter, "/api/users/login");
            assertEquals(200, response.getStatus());
        }

        MockHttpServletResponse blocked = invoke(filter, "/api/users/login");
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
    }

    @Test
    void doesNotLimitReadOnlyRequests() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(new ObjectMapper());
        MockHttpServletRequest request = request("GET", "/api/payments/packages");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertEquals(200, response.getStatus());
    }

    @Test
    void isolatesCreativeChatBudgetByBearerToken() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(new ObjectMapper());
        String path = "/api/creative/ai/conversations/12/chat";
        for (int i = 0; i < 60; i++) {
            MockHttpServletResponse response = invoke(filter, path, "token-a");
            assertEquals(200, response.getStatus());
        }
        assertEquals(429, invoke(filter, path, "token-a").getStatus());
        assertEquals(200, invoke(filter, path, "token-b").getStatus());
    }

    @Test
    void unauthenticatedCreativeChatStillUsesTheProxyAddressBudget() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(new ObjectMapper());
        String path = "/api/creative/ai/conversations/12/chat";
        for (int i = 0; i < 60; i++) {
            MockHttpServletResponse response = invoke(filter, path);
            assertEquals(200, response.getStatus());
        }
        assertEquals(429, invoke(filter, path).getStatus());
    }

    private MockHttpServletResponse invoke(ApiRateLimitFilter filter, String path) throws Exception {
        return invoke(filter, path, null);
    }

    private MockHttpServletResponse invoke(ApiRateLimitFilter filter, String path, String token) throws Exception {
        MockHttpServletRequest request = request("POST", path);
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("198.51.100.7");
        return request;
    }
}
