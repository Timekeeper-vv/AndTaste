package com.example.shixun.service;

import com.example.shixun.model.SupplierSearchRequest;
import com.example.shixun.model.SupplierSearchResult;
import com.example.shixun.model.SupplierBankAccount;
import com.example.shixun.model.SupplierStatisticsRequest;
import com.example.shixun.model.SupplierStatisticsResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;
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
            // 规则：供应商问答必须先让大模型参与语义理解；后端兜底只用于修正明显口语化参数，不能直接生成最终答案。
            plan = planToolCall(userQuestion);
        } catch (Exception e) {
            plan = heuristicPlan(userQuestion);
            if ("none".equals(plan.tool())) {
                return supplierGuardrail(userQuestion, "连接不上大模型，供应商语义理解暂时不可用。按AI助手规则，我不能绕过大模型直接输出纯供应商查询结果，请稍后重试。");
            }
        }

        plan = normalizePlan(userQuestion, plan);
        if (!"search_suppliers".equals(plan.tool()) && !"get_supplier_statistics".equals(plan.tool())) {
            plan = heuristicPlan(userQuestion);
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
            String factsSummary = deterministicToolAnswer(userQuestion, plan.tool(), answerResult);
            String answerPrompt = SupplierAiPromptTemplates.buildAnswerPrompt(userQuestion, plan.tool(), argsJson, resultJson, factsSummary);

            String reply;
            try {
                // 最终用户可见回答必须由大模型基于真实工具结果组织，保证“AI助手”体验而不是纯查询工具。
                reply = cleanLlmReply(siliconFlow.chat(SupplierAiPromptTemplates.ANSWER_SYSTEM_PROMPT, answerPrompt, 0.15, 900, 90));
            } catch (Exception answerError) {
                reply = "我已经查询到供应商数据库，但连接不上大模型。按当前规则，AI助手不能绕过大模型直接输出纯数据结果，请稍后重试。";
            }
            return Optional.of(new ToolAnswer(reply, "text-to-api+llm:" + plan.tool(), plan.tool(), mapper.convertValue(request, Map.class), answerResult));
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

    private ToolPlan normalizePlan(String userQuestion, ToolPlan plan) {
        if (!isLikelySupplierDataQuestion(userQuestion)) return plan;
        if (plan == null || plan.arguments() == null || plan.arguments().isMissingNode() || plan.arguments().isNull()) {
            return heuristicPlan(userQuestion);
        }
        String q = safe(userQuestion);
        String tool = plan.tool() == null ? "none" : plan.tool();
        boolean hasMeaningfulArg = false;
        JsonNode a = plan.arguments();
        if ("search_suppliers".equals(tool)) {
            hasMeaningfulArg = !a.path("region").asText("").isBlank()
                    || !a.path("keyword").asText("").isBlank()
                    || !a.path("bank_name").asText("").isBlank()
                    || !a.path("bankName").asText("").isBlank();
            if (!hasMeaningfulArg && !asksAllSuppliers(q)) return heuristicPlan(userQuestion);
        }
        if ("none".equals(tool)) return heuristicPlan(userQuestion);
        return plan;
    }

    private ToolPlan heuristicPlan(String userQuestion) {
        String q = safe(userQuestion);
        if (!isLikelySupplierDataQuestion(q)) return new ToolPlan("none", mapper.createObjectNode());
        ObjectNode args = mapper.createObjectNode();

        if (containsAny(q, "供应商分为哪些地区", "供应商有哪些地区", "地区分布", "各地区", "按地区", "地区统计")) {
            args.put("group_by_field", "region");
            args.put("include_count", true);
            return new ToolPlan("get_supplier_statistics", args);
        }
        if (containsAny(q, "银行分布", "按银行", "哪些银行", "各银行", "银行统计", "开户银行统计")) {
            args.put("group_by_field", "bank_name");
            args.put("include_count", true);
            return new ToolPlan("get_supplier_statistics", args);
        }
        if (containsAny(q, "供应商类型", "供应商分类", "按类型", "按分类", "什么标准分类")) {
            args.put("group_by_field", "supplier_type");
            args.put("include_count", true);
            return new ToolPlan("get_supplier_statistics", args);
        }

        boolean countOnly = asksCount(q) && !shouldExposeBankAccount(q) && !asksList(q);
        String region = extractRegion(q);
        String bank = extractBank(q);
        String keyword = extractSupplierKeyword(q, region, bank);
        if (!region.isBlank()) args.put("region", region);
        if (!bank.isBlank()) args.put("bank_name", bank);
        if (!keyword.isBlank()) args.put("keyword", keyword);
        if (args.size() == 0 && asksAllSuppliers(q)) {
            // 全部供应商数量/列表。
        } else if (args.size() == 0) {
            return new ToolPlan("none", mapper.createObjectNode());
        }
        args.put("is_count_only", countOnly);
        args.put("limit", countOnly ? 20 : 10);
        return new ToolPlan("search_suppliers", args);
    }

    private boolean asksCount(String q) {
        return containsAny(q, "几个", "几家", "多少", "数量", "总数", "一共", "共有", "有几");
    }

    private boolean asksList(String q) {
        return containsAny(q, "哪些", "哪几个", "列表", "明细", "详情", "信息", "账号", "账户", "开户行", "付款", "打款", "收款");
    }

    private boolean asksAllSuppliers(String q) {
        return q.contains("供应商") && containsAny(q, "全部", "所有", "一共", "总共", "总数", "多少", "几个", "几家", "列表");
    }

    private String extractRegion(String q) {
        for (String r : List.of("秦皇岛", "广州", "深圳", "佛山", "东莞", "潮州", "上海", "天津", "山东", "河北", "浙江", "福建", "辽宁", "广东", "武汉", "北京", "青岛", "济南", "福州", "湖州", "海城")) {
            if (q.contains(r)) return r;
        }
        return "";
    }

    private String extractBank(String q) {
        if (q.contains("工商") || q.contains("工行")) return "工商银行";
        if (q.contains("建设") || q.contains("建行")) return "建设银行";
        if (q.contains("招商") || q.contains("招行")) return "招商银行";
        if (q.contains("农业") || q.contains("农行")) return "农业银行";
        if (q.contains("中信")) return "中信银行";
        if (q.contains("民生")) return "民生银行";
        if (q.contains("交通") || q.contains("交行")) return "交通银行";
        if (q.contains("中国银行") || q.contains("中行")) return "中国银行";
        if (q.contains("威海")) return "威海银行";
        if (q.contains("民泰")) return "民泰银行";
        return "";
    }

    private String extractSupplierKeyword(String q, String region, String bank) {
        String s = q;
        for (String token : List.of(region, bank, "供应商", "银行账号", "有多少个", "有多少家", "有几个", "有几家", "多少个", "多少家", "是多少", "是什么", "几个", "几家", "多少", "数量", "一共", "总共", "共有", "哪些", "哪几个", "列表", "明细", "详情", "信息", "账号", "账户", "付款", "打款", "收款", "开户行", "查一下", "查询", "帮我", "请", "的")) {
            if (token != null && !token.isBlank()) s = s.replace(token, "");
        }
        s = s.replaceAll("[？?。！!，,：:\\s]", "").replaceAll("是$", "").trim();
        if (s.length() < 2) return "";
        if (List.of("地区", "银行", "类型", "分类", "对公").contains(s)) return "";
        return s.length() > 30 ? s.substring(0, 30) : s;
    }

    private boolean containsAny(String text, String... values) {
        if (text == null) return false;
        for (String value : values) {
            if (value != null && !value.isBlank() && text.contains(value)) return true;
        }
        return false;
    }

    private String safe(String value) {
        if (value == null) return "";
        String s = value.trim().replaceAll("[\\p{Cntrl}]", "");
        return s.length() > 120 ? s.substring(0, 120) : s;
    }


    private String cleanLlmReply(String reply) {
        if (reply == null) return "";
        return reply.replace("**", "").replace("｜", " ").replace("|", " ").trim();
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
     * 语义路由优先走大模型；后端启发式只做容错补参，最终回答仍必须由大模型基于工具结果总结。
     */
    private boolean isLikelySupplierDataQuestion(String userQuestion) {
        String q = userQuestion == null ? "" : userQuestion.trim();
        if (q.isBlank()) return false;
        if (q.contains("密码") || q.contains("登录") || q.contains("用户管理") || q.contains("审批员账号")) return false;
        boolean accountIntent = q.contains("账号") || q.contains("账户") || q.contains("付款") || q.contains("打款") || q.contains("收款") || q.contains("开户行");
        boolean subject = q.contains("供应商") || q.contains("收方") || q.contains("开户行") || q.contains("银行账号") || q.contains("对公账户") || accountIntent;
        boolean dataIntent = q.contains("几个") || q.contains("多少") || q.contains("哪些") || q.contains("哪几个")
                || q.contains("列表") || q.contains("查询") || q.contains("查") || q.contains("账号") || q.contains("账户")
                || q.contains("银行") || q.contains("地区") || q.contains("分类") || q.contains("分布") || q.contains("信息")
                || q.contains("有没有") || q.contains("是否有") || q.contains("是多少") || q.contains("是什么");
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
