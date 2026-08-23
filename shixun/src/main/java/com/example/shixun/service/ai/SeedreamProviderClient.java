package com.example.shixun.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin client for the Volcengine Ark Seedream Images API.
 *
 * The controller owns application workflow and asset persistence; this client
 * owns the provider request shape, transport timeout, response parsing, and
 * provider-specific error messages.
 */
@Service
public class SeedreamProviderClient {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration GENERATION_TIMEOUT = Duration.ofSeconds(150);

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${volcengine.ark.images.base-url:https://ark.cn-beijing.volces.com/api/v3/images/generations}")
    private String imagesUrl;

    @Value("${volcengine.ark.seedream.image.model:${VOLCENGINE_ARK_SEEDREAM_IMAGE_MODEL:doubao-seedream-5-0-pro-260628}}")
    private String model;

    public SeedreamProviderClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Submit a text-to-image request using the exact payload expected by the
     * existing Ark queue worker.
     */
    public JsonNode createTextImage(String apiKey, String prompt, String size,
                                    String negativePrompt) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("response_format", "url");
        payload.put("size", size);
        payload.put("stream", false);
        payload.put("watermark", true);
        if (!blank(negativePrompt)) payload.put("negative_prompt", negativePrompt);
        return send(payload, apiKey, "火山方舟生图请求超时，请稍后重试", "无法连接火山方舟生图服务：");
    }

    /**
     * Submit an image-to-image request. The reference image is already
     * authorized and encoded by the application layer before reaching this
     * provider client.
     */
    public JsonNode createImageToImage(String apiKey, String prompt, String referenceImage,
                                       String size, boolean watermark, String negativePrompt,
                                       Long seed) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("prompt", prompt);
        payload.put("image", referenceImage);
        payload.put("response_format", "url");
        payload.put("size", normalizeSize(size));
        payload.put("stream", false);
        payload.put("watermark", watermark);
        if (!blank(negativePrompt)) payload.put("negative_prompt", negativePrompt);
        if (seed != null && seed >= 0) payload.put("seed", seed);
        return send(payload, apiKey, "火山方舟图生图请求超时，请稍后重试", "无法连接火山方舟图生图服务：");
    }

    /**
     * Exposes provider error mapping for the legacy synchronous multiview
     * path while its queue migration is in progress.
     */
    public IllegalStateException imageHttpError(int status, String raw) {
        try {
            JsonNode root = mapper.readTree(raw);
            String errorCode = root.path("error").path("code").asText("");
            String detail = firstNonBlank(
                    root.path("error").path("message").asText(""),
                    root.path("message").asText(""),
                    root.path("error").asText("")
            );
            if (status == 401 || status == 403) {
                return new IllegalStateException("火山方舟 API Key 无效、模型未开通或无调用权限：" + detail);
            }
            if ("SetLimitExceeded".equalsIgnoreCase(errorCode)
                    || detail.contains("Safe Experience Mode")) {
                return new IllegalStateException("火山方舟模型已因安全体验模式额度用尽而暂停。请在方舟控制台的模型开通页面提高额度或关闭安全体验模式后重试。");
            }
            if (status == 429) {
                return new ArkRateLimitException("火山方舟模型触发调用频率限制：" + detail);
            }
            return new IllegalStateException("火山方舟生图接口失败 HTTP " + status + "：" + detail);
        } catch (Exception ignored) {
            return new IllegalStateException("火山方舟生图接口失败 HTTP " + status + "：" + raw);
        }
    }

    public String modelName() {
        return model;
    }

    private JsonNode send(Map<String, Object> payload, String apiKey,
                          String timeoutMessage, String connectionMessage) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imagesUrl))
                .timeout(GENERATION_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw imageHttpError(response.statusCode(), response.body());
            }
            return mapper.readTree(response.body());
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException(timeoutMessage, e);
        } catch (IOException e) {
            throw new IllegalStateException(connectionMessage + safeMessage(e), e);
        }
    }

    private String normalizeSize(String value) {
        return "1K".equals(value) || "2K".equals(value) ? value : "2K";
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!blank(value)) return value.trim();
        }
        return "";
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    public static class ArkRateLimitException extends IllegalStateException {
        public ArkRateLimitException(String message) {
            super(message);
        }
    }
}
