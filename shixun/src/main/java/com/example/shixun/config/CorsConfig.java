package com.example.shixun.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Keep browser API access limited to explicitly configured front-end origins.
 *
 * The mini-program does not use browser CORS, while the web client normally
 * runs on the same origin or through the Vite proxy.  A short local allowlist
 * keeps development convenient without shipping an "allow any origin" policy
 * to production.  Set CORS_ALLOWED_ORIGINS (comma separated) in deployment.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:5176,http://127.0.0.1:5176,http://localhost:5173,http://127.0.0.1:5173}") String configuredOrigins) {
        this.allowedOrigins = Arrays.stream(configuredOrigins == null ? new String[0] : configuredOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableList());
        if (this.allowedOrigins.isEmpty()) {
            throw new IllegalStateException("app.cors.allowed-origins 至少需要配置一个前端来源");
        }
        if (this.allowedOrigins.stream().anyMatch(value -> "*".equals(value) || value.toLowerCase(Locale.ROOT).contains("null"))) {
            throw new IllegalStateException("app.cors.allowed-origins 不允许使用 * 或 null 来源");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
