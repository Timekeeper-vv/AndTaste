package com.example.shixun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ApprovalWorkflowAiService {
    private final JdbcTemplate jdbc;
    private final SiliconFlowChatService siliconFlow;
    private final ObjectMapper mapper;

    private static final String ANSWER_PROMPT = """
你是“之间味道AI助手”的审批中心业务分析助手。
重要产品规则：AI助手不是纯查询工具。你必须基于后端真实数据库结果进行业务化总结，不能只机械复读字段。
数据约束：所有审批数量、申请编号、申请人、状态、业务类型都必须来自工具返回 JSON，禁止编造。
回答风格：纯文字短段落，先给结论，再说明你按什么条件查了审批中心，然后做一两句业务判断，最后给相关追问建议。
审批规则：当前系统采用审批员1-4四人会签，四人全部通过后才算最终通过。
不要使用Markdown表格、竖线表格或复杂符号。
""";

    public ApprovalWorkflowAiService(JdbcTemplate jdbc, SiliconFlowChatService siliconFlow, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.siliconFlow = siliconFlow;
        this.mapper = mapper;
    }

    public Optional<ToolAnswer> tryAnswer(String question) {
        String q = safe(question);
        if (!isApprovalDataQuestion(q)) return Optional.empty();
        Map<String, Object> args = buildArgs(q);
        Map<String, Object> result = query(args);
        String reply = llmAnswer(question, args, result);
        return Optional.of(new ToolAnswer(reply, "text-to-api+llm:approval-workflow", "search_approval_workflows", args, result));
    }

    private boolean isApprovalDataQuestion(String q) {
        if (q.isBlank()) return false;
        boolean approvalScope = q.contains("审批中心") || q.contains("审批") || q.contains("待审批") || q.contains("待处理") || q.contains("待办");
        if (!approvalScope) return false;
        return q.contains("多少") || q.contains("几个") || q.contains("几条") || q.contains("数量")
                || q.contains("有哪些") || q.contains("列表") || q.contains("统计") || q.contains("待办")
                || q.contains("待处理") || q.contains("积压") || q.contains("谁提交") || q.contains("最近");
    }

    private Map<String, Object> buildArgs(String q) {
        Map<String, Object> args = new LinkedHashMap<>();
        String status = "";
        if (q.contains("待审批") || q.contains("待处理") || q.contains("待办") || q.contains("审批中") || q.contains("积压")) status = "pending";
        else if (q.contains("已通过") || q.contains("通过了") || q.contains("批准")) status = "approved";
        else if (q.contains("已驳回") || q.contains("驳回")) status = "rejected";
        else if (q.contains("已撤回") || q.contains("撤回")) status = "withdrawn";
        if (!status.isBlank()) args.put("status", status);

        String category = "";
        if (q.contains("财务")) category = "finance";
        else if (q.contains("生产") || q.contains("打样") || q.contains("大货")) category = "production";
        else if (q.contains("连锁") || q.contains("门店")) category = "chain";
        else if (q.contains("市场")) category = "marketDepartment";
        else if (q.contains("项目")) category = "projectDepartment";
        else if (q.contains("人力") || q.contains("人事") || q.contains("转正") || q.contains("离职") || q.contains("招聘")) category = "humanResource";
        else if (q.contains("考勤") || q.contains("请假") || q.contains("补卡") || q.contains("出差")) category = "attendance";
        if (!category.isBlank()) args.put("category", category);

        boolean listMode = q.contains("有哪些") || q.contains("列表") || q.contains("最近") || q.contains("明细");
        args.put("list", listMode);
        args.put("limit", listMode ? 10 : 5);
        return args;
    }


    private String llmAnswer(String question, Map<String, Object> args, Map<String, Object> result) {
        try {
            String prompt = "用户原始问题：\n" + safe(question)
                    + "\n\n工具名称：\nsearch_approval_workflows"
                    + "\n\n工具参数：\n" + mapper.writeValueAsString(args)
                    + "\n\n工具返回：\n" + mapper.writeValueAsString(result)
                    + "\n\n请基于上述真实工具返回生成最终中文回答。";
            return siliconFlow.chat(ANSWER_PROMPT, prompt, 0.2, 1200, 45);
        } catch (Exception e) {
            return "我已经查询到审批中心数据库，但大模型总结服务暂时不可用。按当前规则，AI助手不能绕过大模型直接输出纯数据结果，请稍后重试。";
        }
    }

    private Map<String, Object> query(Map<String, Object> args) {
        String status = safe(args.get("status"));
        String category = safe(args.get("category"));
        int limit = number(args.get("limit"), 5);

        StringBuilder where = new StringBuilder(" WHERE deleted=0");
        List<Object> params = new ArrayList<>();
        if (!status.isBlank()) { where.append(" AND status=?"); params.add(status); }
        if (!category.isBlank()) { where.append(" AND category=?"); params.add(category); }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM workflow_application" + where, Long.class, params.toArray());
        List<Map<String, Object>> categorySummary = jdbc.queryForList(
                "SELECT category value, COUNT(*) count FROM workflow_application" + where + " GROUP BY category ORDER BY count DESC, value ASC",
                params.toArray());
        List<Map<String, Object>> statusSummary = jdbc.queryForList(
                "SELECT status value, COUNT(*) count FROM workflow_application" + where + " GROUP BY status ORDER BY count DESC, value ASC",
                params.toArray());
        List<Map<String, Object>> applicantSummary = jdbc.queryForList(
                "SELECT COALESCE(NULLIF(TRIM(applicant), ''), '未填写') value, COUNT(*) count FROM workflow_application" + where + " GROUP BY COALESCE(NULLIF(TRIM(applicant), ''), '未填写') ORDER BY count DESC, value ASC LIMIT 5",
                params.toArray());

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(limit);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT id, app_no appNo, category, type_key typeKey, title, applicant, status, flow_name flowName, current_step_name currentStepName, current_handler currentHandler, current_approval_count currentApprovalCount, DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') createdAt, DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') updatedAt " +
                        "FROM workflow_application" + where + " ORDER BY submitted_at ASC, id ASC LIMIT ?",
                listParams.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("query_params", args);
        result.put("category_summary", categorySummary);
        result.put("status_summary", statusSummary);
        result.put("applicant_summary", applicantSummary);
        result.put("items", items);
        return result;
    }

    private String answer(Map<String, Object> result) {
        long total = Long.parseLong(String.valueOf(result.getOrDefault("total", 0)));
        Map<?, ?> query = result.get("query_params") instanceof Map<?, ?> m ? m : Map.of();
        String status = safe(query.get("status"));
        String category = safe(query.get("category"));
        String scope = scopeText(status, category);
        StringBuilder sb = new StringBuilder();
        sb.append("我查了审批中心数据库，").append(scope).append("当前共有 ").append(total).append(" 条。");

        if (total == 0) {
            sb.append("目前这个范围内没有待处理记录，审批队列比较干净。");
            sb.append("你还可以继续问：“全部待审批有多少条？”、“最近已通过的审批有哪些？”、“财务待审批有多少条？”。");
            return sb.toString();
        }

        sb.append(formatSummary(result.get("category_summary"), "按业务类型看", true));
        sb.append(formatSummary(result.get("applicant_summary"), "按申请人看", false));

        List<?> items = result.get("items") instanceof List<?> l ? l : List.of();
        if (!items.isEmpty()) {
            sb.append("最需要先看的几条：");
            sb.append(items.stream().limit(5).map(x -> {
                Map<?, ?> item = (Map<?, ?>) x;
                return display(item.get("appNo")) + "，" + display(item.get("title")) + "，申请人：" + display(item.get("applicant")) + "，类型：" + categoryLabel(display(item.get("category"))) + "，当前：" + display(item.get("currentStepName"));
            }).collect(Collectors.joining("；")));
            sb.append("。");
        }

        if ("pending".equals(status)) {
            sb.append("建议优先处理提交时间最早、财务/生产类或已经积压的申请；现在审批规则是审批员1-4全部通过后才算最终通过。");
        } else {
            sb.append("如果你想追原因，可以继续按申请人、业务类型或时间范围拆开看。");
        }
        sb.append("你还可以继续问：“财务待审批有多少条？”、“最近待审批有哪些？”、“这些待审批是谁提交的？”、“按类型统计审批数量”。");
        return sb.toString();
    }

    private String scopeText(String status, String category) {
        List<String> parts = new ArrayList<>();
        if (!category.isBlank()) parts.add(categoryLabel(category));
        if (!status.isBlank()) parts.add(statusLabel(status));
        if (parts.isEmpty()) return "全部审批记录";
        return String.join("、", parts);
    }

    private String formatSummary(Object obj, String title, boolean category) {
        if (!(obj instanceof List<?> list) || list.isEmpty()) return "";
        String text = list.stream().limit(6).map(x -> {
            Map<?, ?> item = (Map<?, ?>) x;
            String value = display(item.get("value"));
            return (category ? categoryLabel(value) : value) + " " + display(item.get("count")) + "条";
        }).collect(Collectors.joining("，"));
        return title + "：" + text + "。";
    }

    private String statusLabel(String status) {
        return switch (status) {
            case "pending" -> "待审批";
            case "approved" -> "已通过";
            case "rejected" -> "已驳回";
            case "withdrawn" -> "已撤回";
            default -> status;
        };
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "finance" -> "财务";
            case "production" -> "生产";
            case "chain" -> "连锁";
            case "marketDepartment" -> "市场";
            case "projectDepartment" -> "项目";
            case "humanResource" -> "人力资源";
            case "attendance" -> "考勤";
            default -> category == null || category.isBlank() ? "未分类" : category;
        };
    }

    private String safe(Object value) {
        if (value == null) return "";
        return String.valueOf(value).trim().replaceAll("[\\p{Cntrl}]", "");
    }

    private String display(Object value) {
        String s = safe(value);
        return s.isBlank() ? "未填写" : s;
    }

    private int number(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return fallback; }
    }

    public record ToolAnswer(String reply, String source, String toolName, Map<String, Object> toolArguments, Object toolResult) {}
}
