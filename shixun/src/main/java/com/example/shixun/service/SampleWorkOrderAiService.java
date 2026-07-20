package com.example.shixun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SampleWorkOrderAiService {
    private final SiliconFlowChatService siliconFlow;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private static final String ROUTER_PROMPT = """
你是打样工单数据查询助手，负责把用户自然语言转换为后端工具调用参数。
你拥有两个工具：search_sample_work_orders 和 get_sample_work_order_statistics。

工具1 search_sample_work_orders：查询打样工单明细和数量。
参数：
{
  "keyword": "项目名/产品名/申请编号/规格关键词，如 北京天文馆、冰淇淋、瓦子发圈",
  "work_order_status": "工单状态，如 进行中、已完成、待打样、待审批、延期完成、项目暂停、草稿、审批驳回",
  "owner": "负责人，如 李楷、王园凯、许丹、张绪、武璐瑶",
  "project_name": "项目名称关键词",
  "product_type": "产品类型，如 百货、冷冻食品、常温食品、文具、冰箱贴",
  "order_type": "订单类型，如 采购、分账",
  "is_count_only": true,
  "limit": 20
}

工具2 get_sample_work_order_statistics：聚合统计。
参数：
{
  "group_by_field": "work_order_status|owner|product_type|order_type|project_name",
  "include_count": true
}

选择规则：
1. 用户问打样工单、打样明细、打样申请、样品进度、进行中/已完成/延期/负责人/项目/产品数量时，必须调用工具。
2. 问“进行中多少个”“已完成多少个”“谁负责的最多”等聚合类问题，优先调用 get_sample_work_order_statistics 或 search_sample_work_orders count。
3. 问某个项目/产品/申请编号的详情，调用 search_sample_work_orders。
4. 不涉及打样工单数据，输出 {"tool":"none","arguments":{}}。
5. 禁止输出 Markdown，禁止解释，禁止生成 SQL。只输出 JSON。

输出示例：
{"tool":"search_sample_work_orders","arguments":{"keyword":"","work_order_status":"进行中","is_count_only":true,"limit":20}}
{"tool":"get_sample_work_order_statistics","arguments":{"group_by_field":"owner","include_count":true}}
""";

    private static final String ANSWER_PROMPT = """
你是打样工单数据查询助手。你会收到用户原始问题、工具名称、工具参数和工具返回结果。
重要产品规则：AI助手不是纯查询工具，最终回复必须由大模型基于真实数据库结果进行业务化总结，不能只机械复读字段。
你必须只基于工具返回的真实数据库结果回答，禁止凭记忆编造项目、产品、数量、负责人、状态或日期。

回答规则：
1. 使用纯文字短段落，适配右下角小聊天窗口。
2. 禁止 Markdown 表格、竖线表格和长篇解释。
3. 先直接给结论，比如“当前进行中的打样工单有 4 个”。
4. 数量类问题可以简要列出项目/产品/负责人，不要刷太多字段。
5. 详情类问题最多列出申请编号、项目、产品、状态、负责人、预计完成、实际完成、备注。
6. 工具返回空结果时，只能说未找到匹配打样工单，并说明筛选条件。
7. 不要提 Excel、导入、SourceID、raw_json、校验这些技术词。
8. 输出前自检：回答里的每个项目名、产品名、负责人、数量和状态都必须来自工具结果。
""";

    public SampleWorkOrderAiService(SiliconFlowChatService siliconFlow, JdbcTemplate jdbc, ObjectMapper mapper) {
        this.siliconFlow = siliconFlow;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<ToolAnswer> tryAnswer(String userQuestion) {
        String initialQuestion = safe(userQuestion);
        if (!isLikelySampleQuestion(initialQuestion)) return Optional.empty();

        ToolPlan plan;
        try {
            plan = planToolCall(userQuestion);
        } catch (Exception e) {
            plan = heuristicPlan(userQuestion);
            if ("none".equals(plan.tool())) {
                return guardrail(userQuestion, "打样工单语义解析暂时失败，我不会编造打样数据。请稍后重试，或换成“进行中的打样有多少个”“北京天文馆打样进度”这类问法。");
            }
        }

        plan = normalizePlan(userQuestion, plan);

        if (!"search_sample_work_orders".equals(plan.tool()) && !"get_sample_work_order_statistics".equals(plan.tool())) {
            plan = heuristicPlan(userQuestion);
        }

        if (!"search_sample_work_orders".equals(plan.tool()) && !"get_sample_work_order_statistics".equals(plan.tool())) {
            return guardrail(userQuestion, "这个问题没有匹配到可执行的打样工单查询工具，我不会编造打样数据。");
        }

        try {
            Map<String, Object> args = mapper.convertValue(plan.arguments(), Map.class);
            Object result = "get_sample_work_order_statistics".equals(plan.tool())
                    ? statistics(args)
                    : search(args);
            String answerPrompt = "用户原始问题：\n" + safe(userQuestion)
                    + "\n\n工具名称：\n" + plan.tool()
                    + "\n\n工具参数：\n" + mapper.writeValueAsString(args)
                    + "\n\n工具返回：\n" + mapper.writeValueAsString(result)
                    + "\n\n请基于上述真实工具返回生成最终中文回答。";

            String reply;
            try {
                // 最终用户可见回答必须由大模型基于真实工具结果组织，保证“AI助手”体验而不是纯查询工具。
                reply = siliconFlow.chat(ANSWER_PROMPT, answerPrompt, 0.2, 1200, 45);
            } catch (Exception e) {
                reply = "我已经查询到打样工单数据库，但大模型总结服务暂时不可用。按当前规则，AI助手不能绕过大模型直接输出纯数据结果，请稍后重试。";
            }
            return Optional.of(new ToolAnswer(reply, "text-to-api+llm:" + plan.tool(), plan.tool(), args, result));
        } catch (Exception e) {
            return guardrail(userQuestion, "打样工单数据库查询暂时失败，我不会编造数据。请稍后重试。");
        }
    }

    private ToolPlan normalizePlan(String userQuestion, ToolPlan plan) {
        String q = safe(userQuestion);
        if (isOverallCountQuestion(q)) {
            ObjectNode countArgs = mapper.createObjectNode();
            countArgs.put("is_count_only", true);
            countArgs.put("limit", 20);
            return new ToolPlan("search_sample_work_orders", countArgs);
        }
        ObjectNode args = mapper.createObjectNode();
        String countKeyword = extractCountKeyword(q);
        if (!countKeyword.isBlank()) {
            args.put("keyword", countKeyword);
            args.put("is_count_only", true);
            args.put("limit", 20);
            return new ToolPlan("search_sample_work_orders", args);
        }
        boolean ownerAggregate = q.contains("负责人") && (
                q.contains("几个") || q.contains("多少") || q.contains("几位") || q.contains("有哪些")
                        || q.contains("哪几个") || q.contains("分别") || q.contains("各") || q.contains("统计") || q.contains("分布")
        );
        if (isLikelySampleQuestion(q) && ownerAggregate) {
            args.put("group_by_field", "owner");
            args.put("include_count", true);
            return new ToolPlan("get_sample_work_order_statistics", args);
        }
        return plan;
    }

    ToolPlan planToolCall(String userQuestion) throws Exception {
        String raw = siliconFlow.chat(ROUTER_PROMPT, "用户问题：\n" + safe(userQuestion), 0.0, 500, 20);
        JsonNode node = mapper.readTree(extractJson(raw));
        String tool = node.path("tool").asText("none");
        JsonNode arguments = node.path("arguments");
        if (arguments.isMissingNode() || arguments.isNull()) arguments = mapper.createObjectNode();
        return new ToolPlan(tool, arguments);
    }

    private ToolPlan heuristicPlan(String userQuestion) {
        String q = safe(userQuestion);
        if (!isLikelySampleQuestion(q)) return new ToolPlan("none", mapper.createObjectNode());

        ObjectNode args = mapper.createObjectNode();
        boolean asksCount = q.contains("多少") || q.contains("几个") || q.contains("数量") || q.contains("有几");

        if ((q.contains("各") || q.contains("分布") || q.contains("统计") || q.contains("分别")
                || q.contains("几个") || q.contains("多少") || q.contains("几位") || q.contains("有哪些") || q.contains("哪几个"))
                && q.contains("负责人")) {
            args.put("group_by_field", "owner");
            args.put("include_count", true);
            return new ToolPlan("get_sample_work_order_statistics", args);
        }
        if ((q.contains("各") || q.contains("分布") || q.contains("统计") || q.contains("分别")) && q.contains("状态")) {
            args.put("group_by_field", "work_order_status");
            args.put("include_count", true);
            return new ToolPlan("get_sample_work_order_statistics", args);
        }
        if (q.contains("谁负责") || q.contains("负责人有哪些")) {
            args.put("group_by_field", "owner");
            args.put("include_count", true);
            return new ToolPlan("get_sample_work_order_statistics", args);
        }

        String status = extractStatus(q);
        if (!status.isBlank()) args.put("work_order_status", status);
        String owner = extractBefore(q, "负责");
        if (!owner.isBlank()) args.put("owner", owner);
        String project = extractBefore(q, "打样");
        if (project.isBlank()) project = extractBefore(q, "进度");
        if (!project.isBlank() && owner.isBlank() && status.isBlank()) args.put("keyword", project);
        if (args.size() == 0) {
            String keyword = cleanupKeyword(q);
            if (!keyword.isBlank()) args.put("keyword", keyword);
        }
        args.put("is_count_only", asksCount);
        args.put("limit", asksCount ? 20 : 10);
        return new ToolPlan("search_sample_work_orders", args);
    }

    private String extractStatus(String q) {
        for (String s : List.of("进行中", "已完成", "待打样", "待审批", "延期完成", "项目暂停", "草稿", "审批驳回")) {
            if (q.contains(s)) return s;
        }
        return "";
    }

    private String extractBefore(String q, String marker) {
        int idx = q.indexOf(marker);
        if (idx <= 0) return "";
        String left = q.substring(0, idx);
        Matcher m = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9（）()·]{2,20})$").matcher(left);
        if (!m.find()) return "";
        String v = cleanupKeyword(m.group(1));
        if (v.contains("哪些") || v.contains("多少") || v.contains("几个")) return "";
        return v;
    }

    private String cleanupKeyword(String q) {
        return q.replace("打样工单", "").replace("打样明细", "").replace("打样申请", "")
                .replace("打样", "").replace("工单", "").replace("样品", "")
                .replace("明细", "").replace("一共", "").replace("总共", "").replace("总数", "").replace("全部", "").replace("当前", "")
                .replace("有多少个", "").replace("有几个", "").replace("多少个", "").replace("几个", "")
                .replace("数量", "").replace("多少", "").replace("共有", "").replace("共", "")
                .replace("哪些", "").replace("查询", "").replace("查一下", "").replace("进度", "")
                .replace("怎么样", "").replace("是什么", "").trim();
    }

    private boolean isOverallCountQuestion(String q) {
        if (!isLikelySampleQuestion(q)) return false;
        boolean asksCount = q.contains("一共") || q.contains("总共") || q.contains("总数") || q.contains("全部")
                || q.contains("多少个") || q.contains("有几个") || q.contains("数量") || q.contains("共有") || q.contains("共多少");
        if (!asksCount) return false;
        if (!extractStatus(q).isBlank()) return false;
        if (q.contains("负责人") || q.contains("负责的") || q.contains("谁负责")) return false;
        if (q.contains("项目") && !(q.contains("项目一共") || q.contains("项目总数"))) return false;
        if (q.contains("产品") && !(q.contains("产品一共") || q.contains("产品总数"))) return false;
        if (!extractCountKeyword(q).isBlank()) return false;
        return true;
    }

    private String extractCountKeyword(String q) {
        boolean asksCount = q.contains("多少个") || q.contains("有几个") || q.contains("数量") || q.contains("几个");
        if (!asksCount) return "";
        if (!extractStatus(q).isBlank()) return "";
        if (q.contains("负责人") || q.contains("谁负责")) return "";
        String keyword = cleanupKeyword(q)
                .replace("的", "")
                .replace("单", "")
                .replace("项目", "")
                .replace("产品", "")
                .trim();
        if (keyword.length() < 2) return "";
        if (List.of("明细", "全部", "所有", "总").contains(keyword)) return "";
        return keyword;
    }

    private Map<String, Object> search(Map<String, Object> raw) {
        String keyword = safe(raw.get("keyword"));
        String status = safe(raw.get("work_order_status"));
        String owner = safe(raw.get("owner"));
        String projectName = safe(raw.get("project_name"));
        String productType = safe(raw.get("product_type"));
        String orderType = safe(raw.get("order_type"));
        boolean countOnly = Boolean.TRUE.equals(raw.get("is_count_only"));
        int limit = number(raw.get("limit"), 20);
        if (limit < 1) limit = 20;
        if (limit > 100) limit = 100;

        StringBuilder where = new StringBuilder(" WHERE deleted=0");
        List<Object> params = new ArrayList<>();
        if (!keyword.isBlank()) {
            List<String> variants = keywordVariants(keyword);
            where.append(" AND (");
            for (int k = 0; k < variants.size(); k++) {
                if (k > 0) where.append(" OR ");
                where.append("(application_no LIKE ? OR project_name LIKE ? OR detail_project_name LIKE ? OR product_name LIKE ? OR product_type LIKE ? OR product_sub_type LIKE ? OR spec_flavor LIKE ? OR owner LIKE ? OR factory LIKE ? OR IFNULL(detail_remark,'') LIKE ?)");
                String like = like(variants.get(k));
                for (int i = 0; i < 10; i++) params.add(like);
            }
            where.append(")");
        }
        if (!status.isBlank()) { where.append(" AND work_order_status=?"); params.add(status); }
        if (!owner.isBlank()) { where.append(" AND owner LIKE ?"); params.add(like(owner)); }
        if (!projectName.isBlank()) { where.append(" AND (project_name LIKE ? OR detail_project_name LIKE ?)"); params.add(like(projectName)); params.add(like(projectName)); }
        if (!productType.isBlank()) { where.append(" AND product_type LIKE ?"); params.add(like(productType)); }
        if (!orderType.isBlank()) { where.append(" AND order_type LIKE ?"); params.add(like(orderType)); }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM supply_chain_sample_work_order" + where, Long.class, params.toArray());
        Map<String, Object> query = new LinkedHashMap<>();
        if (!keyword.isBlank()) query.put("keyword", keyword);
        if (!status.isBlank()) query.put("work_order_status", status);
        if (!owner.isBlank()) query.put("owner", owner);
        if (!projectName.isBlank()) query.put("project_name", projectName);
        if (!productType.isBlank()) query.put("product_type", productType);
        if (!orderType.isBlank()) query.put("order_type", orderType);
        query.put("is_count_only", countOnly);
        query.put("limit", limit);

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(limit);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT id, application_no applicationNo, approval_status approvalStatus, DATE_FORMAT(initiated_at, '%Y-%m-%d %H:%i:%s') AS initiatedAt, applicant, application_department applicationDepartment, " +
                        "COALESCE(NULLIF(project_name,''), detail_project_name) projectName, product_name productName, order_type orderType, product_type productType, product_sub_type productSubType, " +
                        "sample_quantity_text sampleQuantityText, spec_flavor specFlavor, sample_fee_yuan sampleFeeYuan, detail_remark detailRemark, work_order_status workOrderStatus, " +
                        "DATE_FORMAT(start_date, '%Y-%m-%d') AS startDate, DATE_FORMAT(estimated_complete_date, '%Y-%m-%d') AS estimatedCompleteDate, DATE_FORMAT(actual_complete_date, '%Y-%m-%d') AS actualCompleteDate, " +
                        "owner, factory, sample_cost_yuan sampleCostYuan, DATE_FORMAT(sample_file_provided_date, '%Y-%m-%d') AS sampleFileProvidedDate " +
                        "FROM supply_chain_sample_work_order" + where + " ORDER BY initiated_at DESC, id DESC LIMIT ?",
                listParams.toArray());
        List<Map<String, Object>> statusSummary = jdbc.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(work_order_status), ''), '未填写') AS value, COUNT(*) AS count " +
                        "FROM supply_chain_sample_work_order" + where + " GROUP BY COALESCE(NULLIF(TRIM(work_order_status), ''), '未填写') ORDER BY count DESC, value ASC",
                params.toArray());
        List<Map<String, Object>> ownerSummary = jdbc.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(owner), ''), '未填写') AS value, COUNT(*) AS count " +
                        "FROM supply_chain_sample_work_order" + where + " GROUP BY COALESCE(NULLIF(TRIM(owner), ''), '未填写') ORDER BY count DESC, value ASC LIMIT 5",
                params.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(countOnly ? "count" : "total", total == null ? 0 : total);
        result.put("items", items);
        result.put("status_summary", statusSummary);
        result.put("owner_summary", ownerSummary);
        result.put("query_params", query);
        return result;
    }

    private Map<String, Object> statistics(Map<String, Object> raw) {
        String field = safe(raw.get("group_by_field"));
        if (field.isBlank()) field = "work_order_status";
        boolean includeCount = !Boolean.FALSE.equals(raw.get("include_count"));
        String column = switch (field) {
            case "work_order_status" -> "work_order_status";
            case "owner" -> "owner";
            case "product_type" -> "product_type";
            case "order_type" -> "order_type";
            case "project_name" -> "COALESCE(NULLIF(project_name,''), detail_project_name)";
            default -> null;
        };
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("group_by_field", field);
        query.put("include_count", includeCount);
        if (column == null) {
            return Map.of("field", field, "groups", List.of(), "message", "当前数据库暂不支持该维度的打样工单统计。", "query_params", query);
        }
        String countExpr = includeCount ? "COUNT(*)" : "NULL";
        List<Map<String, Object>> groups = jdbc.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(" + column + "), ''), '未填写') AS value, " + countExpr + " AS count " +
                        "FROM supply_chain_sample_work_order WHERE deleted=0 GROUP BY COALESCE(NULLIF(TRIM(" + column + "), ''), '未填写') ORDER BY count DESC, value ASC LIMIT 50");
        long blankCount = groups.stream()
                .filter(g -> "未填写".equals(String.valueOf(g.get("value"))))
                .mapToLong(g -> Long.parseLong(String.valueOf(g.getOrDefault("count", 0))))
                .sum();
        long nonEmptyDistinctCount = groups.stream()
                .filter(g -> !"未填写".equals(String.valueOf(g.get("value"))))
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("group_count", groups.size());
        result.put("non_empty_distinct_count", nonEmptyDistinctCount);
        result.put("blank_count", blankCount);
        result.put("groups", groups);
        result.put("query_params", query);
        return result;
    }

    private String deterministicAnswer(String toolName, Object result) {
        if (result instanceof Map<?, ?> map) {
            Object message = map.get("message");
            if (message != null) return String.valueOf(message);
            Object count = map.get("count");
            Object total = map.get("total");
            Object itemsObj = map.get("items");
            if (count != null || total != null) {
                long n = Long.parseLong(String.valueOf(count != null ? count : total));
                String prefix = "当前共找到 " + n + " 个打样工单";
                Object queryParams = map.get("query_params");
                if (queryParams instanceof Map<?, ?> query && Boolean.TRUE.equals(query.get("is_count_only"))) {
                    String scope = queryScope(query);
                    String statusText = formatGroupSummary(map.get("status_summary"), "状态");
                    String ownerText = formatGroupSummary(map.get("owner_summary"), "负责人");
                    return scope + prefix + "。" + statusText + ownerText + relatedQuestions(query);
                }
                if (itemsObj instanceof List<?> items && !items.isEmpty()) {
                    String names = items.stream().limit(8).map(x -> {
                        Map<?, ?> item = (Map<?, ?>) x;
                        return display(item.get("projectName")) + " / " + display(item.get("productName")) + "，状态：" + display(item.get("workOrderStatus")) + "，负责人：" + display(item.get("owner"));
                    }).collect(Collectors.joining("；"));
                    return prefix + "：" + names + "。";
                }
                return prefix + "。";
            }
            Object groupsObj = map.get("groups");
            if (groupsObj instanceof List<?> groups) {
                if (groups.isEmpty()) return "未找到可统计的打样工单数据。";
                String field = String.valueOf(map.containsKey("field") ? map.get("field") : "");
                if ("owner".equals(field)) {
                    long personCount = Long.parseLong(String.valueOf(map.containsKey("non_empty_distinct_count") ? map.get("non_empty_distinct_count") : 0));
                    long blankCount = Long.parseLong(String.valueOf(map.containsKey("blank_count") ? map.get("blank_count") : 0));
                    String names = groups.stream()
                            .filter(g -> {
                                Map<?, ?> item = (Map<?, ?>) g;
                                return !"未填写".equals(String.valueOf(item.get("value")));
                            })
                            .limit(20)
                            .map(g -> {
                                Map<?, ?> item = (Map<?, ?>) g;
                                return item.get("value") + " " + item.get("count") + "条";
                            }).collect(Collectors.joining("；"));
                    String suffix = blankCount > 0 ? "；另外有 " + blankCount + " 条工单未填写负责人。" : "。";
                    return "当前打样工单中已填写的负责人有 " + personCount + " 位：" + names + suffix;
                }
                String text = groups.stream().limit(20).map(g -> {
                    Map<?, ?> item = (Map<?, ?>) g;
                    return item.get("value") + " " + item.get("count") + "个";
                }).collect(Collectors.joining("；"));
                return "当前打样工单统计结果：" + text + "。";
            }
        }
        return "已查询打样工单数据库，但大模型整理答案暂时失败；请查看工具返回结果。";
    }

    private Optional<ToolAnswer> guardrail(String question, String reply) {
        if (!isLikelySampleQuestion(question)) return Optional.empty();
        return Optional.of(new ToolAnswer(reply, "text-to-api:sample-work-order-guardrail", "none", Map.of(), Map.of("message", reply)));
    }

    private boolean isLikelySampleQuestion(String question) {
        String q = question == null ? "" : question.trim();
        if (q.isBlank()) return false;
        return q.contains("打样") || q.contains("样品") || q.contains("工单")
                || q.contains("待打样") || q.contains("延期完成")
                || (q.contains("进行中") && (q.contains("多少") || q.contains("几个") || q.contains("哪些")))
                || (q.contains("已完成") && (q.contains("多少") || q.contains("几个") || q.contains("哪些")));
    }

    private String extractJson(String raw) {
        if (raw == null) return "{\"tool\":\"none\",\"arguments\":{}}";
        String s = raw.trim();
        if (s.startsWith("```")) s = s.replaceFirst("^```[a-zA-Z]*", "").replaceFirst("```$", "").trim();
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end >= start) return s.substring(start, end + 1);
        return "{\"tool\":\"none\",\"arguments\":{}}";
    }

    private String safe(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).trim().replaceAll("[\\p{Cntrl}]", "");
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private List<String> keywordVariants(String keyword) {
        String base = safe(keyword).replace("的", "").trim();
        if (base.isBlank()) return List.of();
        List<String> list = new ArrayList<>();
        list.add(base);
        if (base.contains("博物馆") && !base.contains("省博物馆")) {
            list.add(base.replace("博物馆", "省博物馆"));
        }
        if (base.contains("省博物馆")) {
            list.add(base.replace("省博物馆", "博物馆"));
        }
        return list.stream().filter(s -> !s.isBlank()).distinct().collect(Collectors.toList());
    }

    private String queryScope(Map<?, ?> query) {
        List<String> filters = new ArrayList<>();
        if (query.containsKey("keyword")) filters.add("关键词“" + display(query.get("keyword")) + "”");
        if (query.containsKey("project_name")) filters.add("项目“" + display(query.get("project_name")) + "”");
        if (query.containsKey("work_order_status")) filters.add("状态“" + display(query.get("work_order_status")) + "”");
        if (query.containsKey("owner")) filters.add("负责人“" + display(query.get("owner")) + "”");
        if (query.containsKey("product_type")) filters.add("产品类型“" + display(query.get("product_type")) + "”");
        if (query.containsKey("order_type")) filters.add("订单类型“" + display(query.get("order_type")) + "”");
        if (filters.isEmpty()) return "我查了打样工单明细库的全部有效记录，";
        return "我按" + String.join("、", filters) + "查询了打样工单明细库，";
    }

    private String formatGroupSummary(Object groupObj, String label) {
        if (!(groupObj instanceof List<?> groups) || groups.isEmpty()) return "";
        String text = groups.stream().limit(5).map(g -> {
            Map<?, ?> item = (Map<?, ?>) g;
            return display(item.get("value")) + " " + display(item.get("count")) + "个";
        }).collect(Collectors.joining("，"));
        return "按" + label + "看：" + text + "。";
    }

    private String relatedQuestions(Map<?, ?> query) {
        if (query.containsKey("keyword") || query.containsKey("project_name")) {
            String project = display(query.containsKey("project_name") ? query.get("project_name") : query.get("keyword"));
            return "你还可以继续问：“" + project + "进行中的打样有哪些？”、“" + project + "是谁负责？”、“" + project + "预计什么时候完成？”。";
        }
        if (query.containsKey("work_order_status")) {
            return "你还可以继续问：“这些工单分别是谁负责？”、“按项目统计一下”、“列出最近的几条明细”。";
        }
        return "你还可以继续问：“江西省博物馆的打样单多少个？”、“进行中的打样有哪些？”、“按负责人统计一下”、“延期完成的有哪些？”。";
    }

    private String like(String v) { return "%" + v + "%"; }
    private String display(Object v) {
        if (v == null || String.valueOf(v).isBlank()) return "未填写";
        return String.valueOf(v);
    }
    private int number(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return fallback; }
    }

    public record ToolPlan(String tool, JsonNode arguments) {}
    public record ToolAnswer(String reply, String source, String toolName, Map<String, Object> toolArguments, Object toolResult) {}
}
