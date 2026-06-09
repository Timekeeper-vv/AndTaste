package com.example.shixun.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiChatController {

    @Value("${qwen.api.key}")
    private String apiKey;

    private static final String QWEN_URL =
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private static final String SYSTEM_PROMPT =
            "你是一位智慧畜牧养殖管理平台的专业助手，专注于畜牧养殖领域的知识问答。" +
            "你擅长回答以下方面的问题：\n" +
            "1. 动物疾病防治与用药建议（猪、牛、羊、鸡等常见畜禽）\n" +
            "2. 疫苗免疫程序与防疫管理\n" +
            "3. 圈舍环境管理与消毒方案\n" +
            "4. 饲料营养与饲养管理技术\n" +
            "5. 养殖批次管理与出栏决策\n" +
            "6. 兽药规范使用与休药期\n" +
            "7. 养殖政策法规与食品安全溯源\n\n" +
            "回答时请专业、简洁，如涉及用药请提醒遵守兽医处方规定。";

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String userMessage = (String) body.get("message");
        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "消息不能为空"));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        // 构建消息列表：system + 历史 + 当前用户消息
        List<Map<String, String>> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userMessage));

        Map<String, Object> requestBody = Map.of(
                "model", "qwen-turbo",
                "messages", messages,
                "max_tokens", 1024
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(QWEN_URL, request, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) messageObj.get("content");
                    return ResponseEntity.ok(Map.of("reply", content));
                }
            }
            return ResponseEntity.status(500).body(Map.of("error", "AI 返回格式异常"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "调用 AI 接口失败：" + e.getMessage()));
        }
    }
}
