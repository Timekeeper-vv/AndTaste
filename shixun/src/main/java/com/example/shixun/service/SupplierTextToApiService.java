package com.example.shixun.service;

import com.example.shixun.model.SupplierSearchRequest;
import com.example.shixun.model.SupplierSearchResult;
import com.example.shixun.model.SupplierBankAccount;
import com.example.shixun.model.SupplierStatisticsRequest;
import com.example.shixun.model.SupplierStatisticsResult;
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
        ToolPlan plan;
        try {
            plan = planToolCall(userQuestion);
        } catch (Exception e) {
            return supplierGuardrail(userQuestion, "供应商语义解析暂时失败，我不会编造供应商数据。请稍后重试，或换成“广州供应商有几个”“供应商分为哪些地区”这类问法。");
        }

        if (!"search_suppliers".equals(plan.tool()) && !"get_supplier_statistics".equals(plan.tool())) {
            return supplierGuardrail(userQuestion, "抱歉，这个供应商问题当前没有匹配到可执行的数据查询工具，我不会编造地区、分类、数量或供应商名称。");
        }

        try {
            Object request;
            Object answerResult;
            if ("get_supplier_statistics".equals(plan.tool())) {
                SupplierStatisticsRequest statisticsRequest = mapper.treeToValue(plan.arguments(), SupplierStatisticsRequest.class);
                SupplierStatisticsResult statisticsResult = supplierSearchTool.getSupplierStatistics(statisticsRequest);
                request = statisticsRequest;
                answerResult = statisticsResult;
            } else {
                SupplierSearchRequest searchRequest = mapper.treeToValue(plan.arguments(), SupplierSearchRequest.class);
                SupplierSearchResult searchResult = supplierSearchTool.searchSuppliers(searchRequest);
                request = searchRequest;
                answerResult = sanitizeForAnswer(userQuestion, searchResult);
            }
            String argsJson = mapper.writeValueAsString(request);
            String resultJson = mapper.writeValueAsString(answerResult);
            String answerPrompt = SupplierAiPromptTemplates.buildAnswerPrompt(userQuestion, plan.tool(), argsJson, resultJson);

            String reply;
            try {
                reply = siliconFlow.chat(SupplierAiPromptTemplates.ANSWER_SYSTEM_PROMPT, answerPrompt, 0.2, 1200, 45);
            } catch (Exception answerError) {
                reply = deterministicToolAnswer(userQuestion, plan.tool(), answerResult);
            }
            return Optional.of(new ToolAnswer(reply, "text-to-api:" + plan.tool(), plan.tool(), mapper.convertValue(request, Map.class), answerResult));
        } catch (Exception e) {
            return supplierGuardrail(userQuestion, "供应商数据库查询暂时失败，我不会编造供应商数据。请稍后重试。");
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

    private Optional<ToolAnswer> supplierGuardrail(String userQuestion, String reply) {
        if (!isLikelySupplierDataQuestion(userQuestion)) return Optional.empty();
        return Optional.of(new ToolAnswer(
                reply,
                "text-to-api:supplier-guardrail",
                "none",
                Map.of(),
                Map.of("message", reply)
        ));
    }

    /**
     * 这里只做安全兜底分类，避免供应商数据问题在工具不可用时落入通用大模型自由回答。
     * 不在这里解析“广州/包装/多少个”等业务条件，真实查询仍必须走 LLM Text-to-API + 后端工具。
     */
    private boolean isLikelySupplierDataQuestion(String userQuestion) {
        String q = userQuestion == null ? "" : userQuestion.trim();
        if (q.isBlank()) return false;
        boolean subject = q.contains("供应商") || q.contains("收方") || q.contains("开户行") || q.contains("银行账号") || q.contains("对公账户");
        boolean dataIntent = q.contains("几个") || q.contains("多少") || q.contains("哪些") || q.contains("哪几个")
                || q.contains("列表") || q.contains("查询") || q.contains("查") || q.contains("账号") || q.contains("账户")
                || q.contains("银行") || q.contains("地区") || q.contains("分类") || q.contains("分布") || q.contains("信息")
                || q.contains("有没有") || q.contains("是否有");
        return subject && dataIntent;
    }

    private String deterministicToolAnswer(String userQuestion, String toolName, Object answerResult) {
        if (answerResult instanceof SupplierStatisticsResult stats) {
            if (stats.getMessage() != null && !stats.getMessage().isBlank()) {
                return stats.getMessage();
            }
            if (stats.getGroups() == null || stats.getGroups().isEmpty()) {
                return "未找到可统计的供应商数据。";
            }
            String fieldLabel = "region".equals(stats.getField()) ? "地区" : ("bank_name".equals(stats.getField()) ? "银行" : stats.getField());
            String groups = stats.getGroups().stream()
                    .limit(30)
                    .map(g -> g.getCount() == null ? g.getValue() : g.getValue() + " " + g.getCount() + "家")
                    .collect(java.util.stream.Collectors.joining("；"));
            return "我按" + fieldLabel + "统计了当前供应商台账，实际存在的" + fieldLabel + "为：" + groups + "。";
        }
        if (answerResult instanceof SupplierSearchResult search) {
            if (search.getCount() != null) {
                String names = search.getNames() == null || search.getNames().isEmpty() ? "" : "，分别是：" + String.join("、", search.getNames());
                return "按当前筛选条件查询，共找到 " + search.getCount() + " 个供应商" + names + "。";
            }
            if (search.getItems() == null || search.getItems().isEmpty()) {
                return "按当前筛选条件查询，未找到匹配供应商。";
            }
            String items = search.getItems().stream().limit(20).map(item -> {
                String line = item.getSupplier() + "，所在地：" + item.getLocation() + "，银行：" + item.getBank();
                if (shouldExposeBankAccount(userQuestion)) {
                    line += "，账号：" + item.getBankAccount() + "，开户行：" + item.getBranch();
                }
                return line;
            }).collect(java.util.stream.Collectors.joining("；"));
            String safety = shouldExposeBankAccount(userQuestion) ? " 付款前请务必人工二次核验户名、账号及开户行信息。" : "";
            long total = search.getTotal() == null ? search.getItems().size() : search.getTotal();
            return "按当前筛选条件查询，共找到 " + total + " 个供应商：" + items + "。" + safety;
        }
        return "已调用 " + toolName + " 工具，但大模型整理答案暂时失败；为避免编造，请查看工具返回的真实数据。";
    }

    public record ToolPlan(String tool, JsonNode arguments) {}
    public record ToolAnswer(String reply, String source, String toolName, Map<String, Object> toolArguments, Object toolResult) {}
}
