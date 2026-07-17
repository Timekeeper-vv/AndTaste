package com.example.shixun.service;

import com.example.shixun.model.SupplierSearchRequest;
import com.example.shixun.model.SupplierSearchResult;
import com.example.shixun.model.SupplierBankAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
public class SupplierTextToApiService {
    private final SiliconFlowChatService siliconFlow;
    private final SupplierSearchToolService supplierSearchTool;
    private final ObjectMapper mapper;

    public SupplierTextToApiService(SiliconFlowChatService siliconFlow, SupplierSearchToolService supplierSearchTool, ObjectMapper mapper) {
        this.siliconFlow = siliconFlow;
        this.supplierSearchTool = supplierSearchTool;
        this.mapper = mapper;
    }

    public Optional<ToolAnswer> tryAnswer(String userQuestion) {
        try {
            ToolPlan plan = planToolCall(userQuestion);
            if (!"search_suppliers".equals(plan.tool())) return Optional.empty();

            SupplierSearchRequest request = mapper.treeToValue(plan.arguments(), SupplierSearchRequest.class);
            SupplierSearchResult result = supplierSearchTool.searchSuppliers(request);
            SupplierSearchResult answerResult = sanitizeForAnswer(userQuestion, result);
            String argsJson = mapper.writeValueAsString(request);
            String resultJson = mapper.writeValueAsString(answerResult);
            String answerPrompt = SupplierAiPromptTemplates.buildAnswerPrompt(userQuestion, argsJson, resultJson);

            String reply = siliconFlow.chat(SupplierAiPromptTemplates.ANSWER_SYSTEM_PROMPT, answerPrompt, 0.2, 1200, 45);
            return Optional.of(new ToolAnswer(reply, "text-to-api:search_suppliers", mapper.convertValue(request, Map.class), answerResult));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public ToolPlan planToolCall(String userQuestion) throws Exception {
        String raw = siliconFlow.chat(
                SupplierAiPromptTemplates.TOOL_ROUTER_SYSTEM_PROMPT,
                SupplierAiPromptTemplates.buildToolRouterUserPrompt(userQuestion),
                0.0,
                500,
                20
        );
        JsonNode node = mapper.readTree(extractJson(raw));
        String tool = node.path("tool").asText("none");
        JsonNode arguments = node.path("arguments");
        if (arguments.isMissingNode() || arguments.isNull()) arguments = mapper.createObjectNode();
        return new ToolPlan(tool, arguments);
    }

    private String extractJson(String raw) {
        if (raw == null) return "{\"tool\":\"none\",\"arguments\":{}}";
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end >= start) return s.substring(start, end + 1);
        return "{\"tool\":\"none\",\"arguments\":{}}";
    }

    private SupplierSearchResult sanitizeForAnswer(String userQuestion, SupplierSearchResult result) {
        if (result == null || result.getItems() == null || shouldExposeBankAccount(userQuestion)) return result;
        var sanitizedItems = result.getItems().stream().map(item -> new SupplierBankAccount(
                item.getId(),
                item.getReceiverNo(),
                item.getSupplier(),
                item.getAccountType(),
                item.getAccountName(),
                null,
                item.getBank(),
                item.getBranch(),
                item.getLocation(),
                item.getNote()
        )).toList();
        return SupplierSearchResult.list(result.getTotal() == null ? sanitizedItems.size() : result.getTotal(), sanitizedItems, result.getQueryParams());
    }

    private boolean shouldExposeBankAccount(String userQuestion) {
        String q = userQuestion == null ? "" : userQuestion;
        return q.contains("账号") || q.contains("账户") || q.contains("付款") || q.contains("打款") || q.contains("收款") || q.contains("银行账号");
    }

    public record ToolPlan(String tool, JsonNode arguments) {}
    public record ToolAnswer(String reply, String source, Map<String, Object> toolArguments, SupplierSearchResult toolResult) {}
}
