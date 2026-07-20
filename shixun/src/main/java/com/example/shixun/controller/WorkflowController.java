package com.example.shixun.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workflows")
@CrossOrigin(origins = "*")
public class WorkflowController {

    private static final TypeReference<Map<String, String>> FORM_TYPE = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public WorkflowController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        ensureWorkflowSchema();
    }

    @GetMapping("/definitions")
    public Map<String, Object> definitions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flows", List.of(
                Map.of("key", "standard", "name", "标准审批", "desc", "一级主管审批，适合普通业务申请", "steps", flowToMap(stepsFor("standard"))),
                Map.of("key", "twoLevel", "name", "两级审批", "desc", "主管审批 + 终审确认，适合财务、生产等关键事项", "steps", flowToMap(stepsFor("twoLevel"))),
                Map.of("key", "countersign", "name", "会签审批", "desc", "多人会签后进入终审，适合重大或跨部门事项", "steps", flowToMap(stepsFor("countersign")))
        ));
        result.put("categoryDefaults", Map.of(
                "finance", "twoLevel",
                "production", "twoLevel",
                "chain", "standard",
                "marketDepartment", "standard",
                "projectDepartment", "standard",
                "humanResource", "standard",
                "attendance", "standard"
        ));
        return result;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0"));
        result.put("pendingCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND status='pending'"));
        result.put("approvedCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND status='approved'"));
        result.put("rejectedCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND status='rejected'"));
        result.put("withdrawnCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND status='withdrawn'"));
        result.put("financePendingCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND category='finance' AND status='pending'"));
        result.put("chainPendingCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND category='chain' AND status='pending'"));
        result.put("marketDepartmentPendingCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND category='marketDepartment' AND status='pending'"));
        result.put("projectDepartmentPendingCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND category='projectDepartment' AND status='pending'"));
        result.put("humanResourcePendingCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND category='humanResource' AND status='pending'"));
        result.put("attendancePendingCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND category='attendance' AND status='pending'"));
        result.put("productionPendingCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND category='production' AND status='pending'"));
        result.put("recentPending", listApplications("pending", null, null, 5, 0));
        result.put("recentApproved", listApplications("approved", null, null, 5, 0));
        return result;
    }

    @GetMapping("/applications")
    public Object list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String applicant,
            @RequestParam(required = false) String applicantRole,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        if (page != null) {
            int pageIndex = Math.max(1, page);
            int pageSize = Math.max(1, Math.min(size, 300));
            int offset = (pageIndex - 1) * pageSize;
            List<Map<String, Object>> content = listApplications(status, category, applicant, pageSize, offset);
            long total = countApplications(status, category, applicant, applicantRole);
            Map<String, Object> pageResult = new LinkedHashMap<>();
            pageResult.put("content", content);
            pageResult.put("total", total);
            pageResult.put("page", pageIndex);
            pageResult.put("size", pageSize);
            return pageResult;
        }
        return listApplications(status, category, applicant, null, null);
    }

    @GetMapping("/applications/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        return loadApplication(id, true);
    }

    @PostMapping("/applications")
    public Map<String, Object> submit(@RequestBody WorkflowSubmitRequest req) {
        validateSubmitRequest(req);
        String role = normalizeRole(req.applicantRole, "feeder");
        validateRole(role, true);
        String title = blank(req.title) ? defaultTitle(req.category, req.typeKey) : req.title.trim();
        String appNo = no("WF");
        String flowType = normalizeFlowType(req.flowType, req.category, req.typeKey);
        List<FlowStep> steps = stepsFor(flowType);
        String formJson = toJson(req.fields == null ? Map.of() : req.fields);
        String flowJson = toJson(flowToMap(steps));

        jdbc.update(
                "INSERT INTO workflow_application (app_no,category,type_key,title,applicant,applicant_role,form_data_json,status,flow_type,flow_name,flow_config_json,current_step,current_step_name,current_handler,current_approval_count) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                appNo, req.category.trim(), req.typeKey.trim(), title, req.applicant.trim(), role, formJson, "pending", flowType, flowName(flowType), flowJson, 0, steps.get(0).name, handlerLabel(steps.get(0)), 0
        );
        Long id = jdbc.queryForObject("SELECT id FROM workflow_application WHERE app_no=?", Long.class, appNo);
        insertLog(id, "submit", req.applicant.trim(), role, "提交申请");
        notifyRoles(id, steps.get(0), "审批待办", title + " 等待处理");
        return loadApplication(id, true);
    }

    @PostMapping("/applications/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id, @RequestBody WorkflowActionRequest req) {
        return approveStep(id, req);
    }

    @PostMapping("/applications/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody WorkflowActionRequest req) {
        return rejectApplication(id, req);
    }

    @PostMapping("/applications/{id}/transfer")
    public Map<String, Object> transfer(@PathVariable Long id, @RequestBody WorkflowActionRequest req) {
        validateOperator(req, false);
        if (blank(req.target)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "转交对象不能为空");
        Map<String, Object> current = loadApplication(id, false);
        ensurePending(current);
        validateCurrentHandler(current, req);
        jdbc.update("UPDATE workflow_application SET current_handler=? WHERE id=?", req.target.trim(), id);
        insertLog(id, "transfer", req.operator.trim(), req.operatorRole.trim(), "转交给 " + req.target.trim() + (blank(req.comment) ? "" : "；" + req.comment.trim()));
        insertNotice(id, req.target.trim(), "审批转交", String.valueOf(current.get("title")) + " 已转交给你处理");
        return loadApplication(id, true);
    }

    @PostMapping("/applications/{id}/withdraw")
    public Map<String, Object> withdraw(@PathVariable Long id, @RequestBody WorkflowActionRequest req) {
        validateOperator(req, true);
        Map<String, Object> current = loadApplication(id, false);
        ensurePending(current);
        String applicant = String.valueOf(current.get("applicant"));
        String role = normalizeRole(req.operatorRole, "");
        if (!"admin".equals(role) && !applicant.equals(req.operator.trim())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能撤回自己提交的申请");
        }
        jdbc.update("UPDATE workflow_application SET status='withdrawn', current_handler=NULL, withdrawn_at=CURRENT_TIMESTAMP WHERE id=?", id);
        insertLog(id, "withdraw", req.operator.trim(), req.operatorRole.trim(), blank(req.comment) ? "撤回申请" : req.comment.trim());
        return loadApplication(id, true);
    }

    @PostMapping("/applications/{id}/resubmit")
    public Map<String, Object> resubmit(@PathVariable Long id, @RequestBody WorkflowResubmitRequest req) {
        if (req == null || blank(req.operator) || blank(req.operatorRole)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "提交人信息不能为空");
        Map<String, Object> current = loadApplication(id, false);
        String status = String.valueOf(current.get("status"));
        if (!List.of("rejected", "withdrawn").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只有已驳回或已撤回的申请可以重新提交");
        String applicant = String.valueOf(current.get("applicant"));
        String role = normalizeRole(req.operatorRole, "");
        if (!"admin".equals(role) && !applicant.equals(req.operator.trim())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能重新提交自己的申请");
        String flowType = normalizeFlowType(String.valueOf(current.get("flowType")), String.valueOf(current.get("category")), String.valueOf(current.get("type")));
        List<FlowStep> steps = stepsFor(flowType);
        String title = blank(req.title) ? String.valueOf(current.get("title")) : req.title.trim();
        String formJson = req.fields == null ? toJson((Map<?, ?>) current.get("fields")) : toJson(req.fields);
        jdbc.update("UPDATE workflow_application SET title=?, form_data_json=?, status='pending', current_step=0, current_step_name=?, current_handler=?, current_approval_count=0, approver=NULL, approval_comment=NULL, approved_at=NULL, rejected_at=NULL, withdrawn_at=NULL, finished_at=NULL, resubmit_count=resubmit_count+1 WHERE id=?",
                title, formJson, steps.get(0).name, handlerLabel(steps.get(0)), id);
        insertLog(id, "resubmit", req.operator.trim(), req.operatorRole.trim(), blank(req.comment) ? "重新提交申请" : req.comment.trim());
        notifyRoles(id, steps.get(0), "重新提交待审批", title + " 已重新提交");
        return loadApplication(id, true);
    }

    @GetMapping("/notifications")
    public List<Map<String, Object>> notifications(@RequestParam String receiver) {
        return jdbc.queryForList("SELECT id, application_id applicationId, receiver, title, message, read_flag readFlag, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') createdAt FROM workflow_notification WHERE receiver=? OR receiver IN ('admin','technician') ORDER BY id DESC LIMIT 50", receiver.trim());
    }

    private Map<String, Object> approveStep(Long id, WorkflowActionRequest req) {
        validateOperator(req, false);
        Map<String, Object> current = loadApplication(id, false);
        ensurePending(current);
        validateCurrentHandler(current, req);
        String flowType = normalizeFlowType(String.valueOf(current.get("flowType")), String.valueOf(current.get("category")), String.valueOf(current.get("type")));
        List<FlowStep> steps = stepsFor(flowType);
        int stepIndex = Math.max(0, intValue(current.get("currentStep")));
        if (stepIndex >= steps.size()) stepIndex = steps.size() - 1;
        FlowStep step = steps.get(stepIndex);
        String operatorRole = normalizeRole(req.operatorRole, "");
        if (!step.roles.contains(operatorRole) && !"admin".equals(operatorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色不能处理该审批节点");
        }
        int count = intValue(current.get("currentApprovalCount")) + 1;
        String comment = blank(req.comment) ? "同意" : req.comment.trim();
        insertLog(id, "approve", req.operator.trim(), operatorRole, step.name + "：" + comment);
        if (count < step.requiredCount) {
            jdbc.update("UPDATE workflow_application SET current_approval_count=?, approver=?, approval_comment=? WHERE id=?", count, req.operator.trim(), comment, id);
            return loadApplication(id, true);
        }
        if (stepIndex + 1 < steps.size()) {
            FlowStep next = steps.get(stepIndex + 1);
            jdbc.update("UPDATE workflow_application SET current_step=?, current_step_name=?, current_handler=?, current_approval_count=0, approver=?, approval_comment=? WHERE id=?",
                    stepIndex + 1, next.name, handlerLabel(next), req.operator.trim(), comment, id);
            notifyRoles(id, next, "审批待办", String.valueOf(current.get("title")) + " 已进入 " + next.name);
        } else {
            jdbc.update("UPDATE workflow_application SET status='approved', approver=?, approval_comment=?, approved_at=CURRENT_TIMESTAMP, finished_at=CURRENT_TIMESTAMP, current_handler=NULL WHERE id=?",
                    req.operator.trim(), comment, id);
            syncSampleRequestStatus(id, "approved", req.operator.trim());
            syncBulkProductionStatus(id, "approved", req.operator.trim());
            insertNotice(id, String.valueOf(current.get("applicant")), "申请已通过", String.valueOf(current.get("title")) + " 已审批通过");
        }
        return loadApplication(id, true);
    }

    private Map<String, Object> rejectApplication(Long id, WorkflowActionRequest req) {
        validateOperator(req, false);
        Map<String, Object> current = loadApplication(id, false);
        ensurePending(current);
        validateCurrentHandler(current, req);
        String comment = blank(req.comment) ? "驳回" : req.comment.trim();
        jdbc.update("UPDATE workflow_application SET status='rejected', approver=?, approval_comment=?, rejected_at=CURRENT_TIMESTAMP, finished_at=CURRENT_TIMESTAMP, current_handler=NULL WHERE id=?",
                req.operator.trim(), comment, id);
        syncSampleRequestStatus(id, "rejected", req.operator.trim());
        syncBulkProductionStatus(id, "rejected", req.operator.trim());
        insertLog(id, "reject", req.operator.trim(), req.operatorRole.trim(), comment);
        insertNotice(id, String.valueOf(current.get("applicant")), "申请已驳回", String.valueOf(current.get("title")) + " 已驳回，可修改后重新提交");
        return loadApplication(id, true);
    }

    private void syncSampleRequestStatus(Long workflowId, String targetStatus, String operator) {
        String approvalStatus = "approved".equals(targetStatus) ? "已通过" : "已驳回";
        String workStatus = "approved".equals(targetStatus) ? "待打样" : "审批驳回";
        jdbc.update("UPDATE supply_chain_sample_work_order SET approval_status=?, current_handler=NULL, work_order_status=?, updated_by=? WHERE workflow_application_id=? AND deleted=0", approvalStatus, workStatus, operator, workflowId);
    }

    private void syncBulkProductionStatus(Long workflowId, String targetStatus, String operator) {
        String approvalStatus = "approved".equals(targetStatus) ? "已通过" : "已驳回";
        String workStatus = "approved".equals(targetStatus) ? "待生产" : "审批驳回";
        jdbc.update("UPDATE supply_chain_bulk_production_order SET approval_status=?, current_handler=NULL, work_order_status=?, updated_by=? WHERE workflow_application_id=? AND deleted=0", approvalStatus, workStatus, operator, workflowId);
    }

    private List<Map<String, Object>> listApplications(String status, String category, String applicant, Integer limit, Integer offset) {
        StringBuilder sql = new StringBuilder(selectSql() + " FROM workflow_application WHERE deleted=0");
        List<Object> params = new ArrayList<>();
        if (!blank(status)) { sql.append(" AND status=?"); params.add(status.trim()); }
        if (!blank(category)) { sql.append(" AND category=?"); params.add(category.trim()); }
        if (!blank(applicant)) { sql.append(" AND applicant=?"); params.add(applicant.trim()); }
        sql.append(" ORDER BY id DESC");
        if (limit != null) { sql.append(" LIMIT ?"); params.add(limit); if (offset != null) { sql.append(" OFFSET ?"); params.add(offset); } }
        return jdbc.query(sql.toString(), (rs, rowNum) -> rowToApplication(rs.getLong("id"), false), params.toArray());
    }

    private Map<String, Object> loadApplication(Long id) { return loadApplication(id, true); }

    private Map<String, Object> loadApplication(Long id, boolean withLogs) {
        List<Map<String, Object>> list = jdbc.query(selectSql() + " FROM workflow_application WHERE id=? AND deleted=0", (rs, rowNum) -> rowToApplication(rs.getLong("id"), withLogs), id);
        if (list.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "申请单不存在");
        return list.get(0);
    }

    private String selectSql() {
        return "SELECT id, app_no appNo, category, type_key typeKey, title, applicant, applicant_role applicantRole, form_data_json formDataJson, status, approver, approval_comment approvalComment, submitted_at submittedAt, updated_at updatedAt, approved_at approvedAt, rejected_at rejectedAt, flow_type flowType, flow_name flowName, flow_config_json flowConfigJson, current_step currentStep, current_step_name currentStepName, current_handler currentHandler, current_approval_count currentApprovalCount, resubmit_count resubmitCount, withdrawn_at withdrawnAt, finished_at finishedAt";
    }

    private Map<String, Object> rowToApplication(Long id, boolean withLogs) {
        Map<String, Object> row = jdbc.queryForMap(selectSql() + " FROM workflow_application WHERE id=?", id);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("appNo", row.get("appNo"));
        item.put("category", row.get("category"));
        item.put("type", row.get("typeKey"));
        item.put("title", row.get("title"));
        item.put("applicant", row.get("applicant"));
        item.put("applicantRole", row.get("applicantRole"));
        item.put("fields", parseFields(str(row.get("formDataJson"))));
        item.put("status", row.get("status"));
        item.put("approver", row.get("approver"));
        item.put("comment", row.get("approvalComment"));
        item.put("createdAt", ts(row.get("submittedAt")));
        item.put("updatedAt", ts(row.get("updatedAt")));
        item.put("approvedAt", ts(row.get("approvedAt")));
        item.put("rejectedAt", ts(row.get("rejectedAt")));
        item.put("withdrawnAt", ts(row.get("withdrawnAt")));
        item.put("finishedAt", ts(row.get("finishedAt")));
        item.put("flowType", row.get("flowType"));
        item.put("flowName", row.get("flowName"));
        item.put("flowConfig", parseJsonList(str(row.get("flowConfigJson"))));
        item.put("currentStep", intValue(row.get("currentStep")));
        item.put("currentStepName", row.get("currentStepName"));
        item.put("currentHandler", row.get("currentHandler"));
        item.put("currentApprovalCount", intValue(row.get("currentApprovalCount")));
        item.put("resubmitCount", intValue(row.get("resubmitCount")));
        if (withLogs) {
            item.put("logs", logs(id));
            item.put("timeline", buildTimeline(item, logs(id)));
        }
        return item;
    }

    private List<Map<String, Object>> logs(Long id) {
        return jdbc.queryForList("SELECT id, action, operator, operator_role operatorRole, comment, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') createdAt FROM workflow_approval_log WHERE application_id=? ORDER BY id ASC", id);
    }

    private List<Map<String, Object>> buildTimeline(Map<String, Object> app, List<Map<String, Object>> logs) {
        List<Map<String, Object>> timeline = new ArrayList<>();
        timeline.add(Map.of("name", "提交申请", "status", "done", "time", String.valueOf(app.get("createdAt")), "operator", String.valueOf(app.get("applicant"))));
        List<?> cfg = app.get("flowConfig") instanceof List<?> l ? l : flowToMap(stepsFor(String.valueOf(app.get("flowType"))));
        int current = intValue(app.get("currentStep"));
        String status = String.valueOf(app.get("status"));
        for (int i = 0; i < cfg.size(); i++) {
            Object obj = cfg.get(i);
            String name = obj instanceof Map<?, ?> m ? String.valueOf(m.get("name")) : "审批节点";
            String nodeStatus = "pending".equals(status) ? (i < current ? "done" : i == current ? "active" : "waiting") : ("approved".equals(status) ? "done" : (i <= current ? status : "waiting"));
            timeline.add(Map.of("name", name, "status", nodeStatus, "time", "", "operator", ""));
        }
        if (!"pending".equals(status)) timeline.add(Map.of("name", "流程结束", "status", status, "time", String.valueOf(app.get("finishedAt")), "operator", str(app.get("approver"))));
        return timeline;
    }

    private Map<String, String> parseFields(String json) {
        try { if (blank(json)) return Map.of(); return new LinkedHashMap<>(mapper.readValue(json, FORM_TYPE)); } catch (Exception e) { return Map.of(); }
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        try { if (blank(json)) return List.of(); return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {}); } catch (Exception e) { return List.of(); }
    }

    private void insertLog(Long applicationId, String action, String operator, String operatorRole, String comment) {
        jdbc.update("INSERT INTO workflow_approval_log (application_id, action, operator, operator_role, comment) VALUES (?,?,?,?,?)", applicationId, action, operator, operatorRole, blank(comment) ? null : comment);
    }

    private void insertNotice(Long applicationId, String receiver, String title, String message) {
        try { jdbc.update("INSERT INTO workflow_notification (application_id, receiver, title, message) VALUES (?,?,?,?)", applicationId, receiver, title, message); } catch (Exception ignored) { }
    }

    private void notifyRoles(Long applicationId, FlowStep step, String title, String message) {
        for (String role : step.roles) insertNotice(applicationId, role, title, message);
    }

    private long count(String sql) { Long value = jdbc.queryForObject(sql, Long.class); return value == null ? 0L : value; }

    private long countApplications(String status, String category, String applicant, String applicantRole) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM workflow_application WHERE deleted=0");
        List<Object> params = new ArrayList<>();
        if (!blank(status)) { sql.append(" AND status=?"); params.add(status.trim()); }
        if (!blank(category)) { sql.append(" AND category=?"); params.add(category.trim()); }
        if (!blank(applicant)) { sql.append(" AND applicant=?"); params.add(applicant.trim()); }
        if (!blank(applicantRole)) { sql.append(" AND applicant_role=?"); params.add(applicantRole.trim()); }
        Long value = jdbc.queryForObject(sql.toString(), Long.class, params.toArray());
        return value == null ? 0L : value;
    }

    private String defaultTitle(String category, String typeKey) {
        if ("finance".equals(category)) return "财务申请";
        if ("chain".equals(category)) return "连锁申请";
        if ("marketDepartment".equals(category)) return "市场部需求";
        if ("projectDepartment".equals(category)) return "项目部需求";
        if ("humanResource".equals(category)) return "人力资源申请";
        if ("attendance".equals(category)) return "考勤申请";
        if ("production".equals(category)) return "生产申请";
        return blank(typeKey) ? "申请单" : typeKey;
    }

    private void validateSubmitRequest(WorkflowSubmitRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        if (blank(req.category)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请分类不能为空");
        if (blank(req.typeKey)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请类型不能为空");
        if (blank(req.applicant)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请人不能为空");
    }

    private void validateOperator(WorkflowActionRequest req, boolean allowSubmitRole) {
        if (req == null || blank(req.operator) || blank(req.operatorRole)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "操作人信息不能为空");
        validateRole(req.operatorRole.trim(), allowSubmitRole);
    }

    private void validateRole(String role, boolean submit) {
        String r = normalizeRole(role, "");
        if (submit) {
            if (!List.of("admin", "technician", "feeder").contains(r)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限提交申请");
        } else {
            if (!List.of("admin", "technician").contains(r)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限审批");
        }
    }

    private void ensurePending(Map<String, Object> current) {
        if (!"pending".equals(String.valueOf(current.get("status")))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该申请当前不可审批");
    }

    private void validateCurrentHandler(Map<String, Object> current, WorkflowActionRequest req) {
        String handler = str(current.get("currentHandler"));
        String role = normalizeRole(req.operatorRole, "");
        if (!blank(handler) && !handler.contains("/") && !handler.equals(req.operator.trim()) && !"admin".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该申请已转交给 " + handler + " 处理");
        }
    }

    private String normalizeRole(String role, String fallback) { String r = blank(role) ? fallback : role.trim(); return blank(r) ? fallback : r; }
    private boolean blank(String s) { return s == null || s.trim().isEmpty() || "null".equalsIgnoreCase(s.trim()); }
    private String no(String prefix) { return prefix + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int) (Math.random() * 900 + 100); }
    private String ts(Object o) { if (o == null) return null; if (o instanceof Timestamp t) return t.toLocalDateTime().toString().replace('T', ' '); return String.valueOf(o).replace('T', ' '); }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private int intValue(Object o) { try { return o == null ? 0 : Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; } }
    private String toJson(Object value) { try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JSON序列化失败"); } }

    private String normalizeFlowType(String flowType, String category, String typeKey) {
        String f = blank(flowType) ? defaultFlowType(category, typeKey) : flowType.trim();
        return List.of("standard", "twoLevel", "countersign").contains(f) ? f : defaultFlowType(category, typeKey);
    }

    private String defaultFlowType(String category, String typeKey) {
        if ("finance".equals(category) || "production".equals(category)) return "twoLevel";
        return "standard";
    }

    private String flowName(String flowType) { return "twoLevel".equals(flowType) ? "两级审批" : "countersign".equals(flowType) ? "会签审批" : "标准审批"; }

    private List<FlowStep> stepsFor(String flowType) {
        if ("countersign".equals(flowType)) return List.of(new FlowStep("会签审批", List.of("technician", "admin"), "all", 2), new FlowStep("终审确认", List.of("admin"), "or", 1));
        if ("twoLevel".equals(flowType)) return List.of(new FlowStep("主管审批", List.of("technician", "admin"), "or", 1), new FlowStep("终审确认", List.of("admin"), "or", 1));
        return List.of(new FlowStep("主管审批", List.of("technician", "admin"), "or", 1));
    }

    private List<Map<String, Object>> flowToMap(List<FlowStep> steps) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            FlowStep s = steps.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("index", i);
            m.put("name", s.name);
            m.put("roles", s.roles);
            m.put("mode", s.mode);
            m.put("requiredCount", s.requiredCount);
            list.add(m);
        }
        return list;
    }

    private String handlerLabel(FlowStep step) { return String.join("/", step.roles); }

    private void ensureWorkflowSchema() {
        String[] sqls = new String[] {
                "ALTER TABLE workflow_application ADD COLUMN flow_type VARCHAR(50) NOT NULL DEFAULT 'standard'",
                "ALTER TABLE workflow_application ADD COLUMN flow_name VARCHAR(100) NULL",
                "ALTER TABLE workflow_application ADD COLUMN flow_config_json JSON NULL",
                "ALTER TABLE workflow_application ADD COLUMN current_step INT NOT NULL DEFAULT 0",
                "ALTER TABLE workflow_application ADD COLUMN current_step_name VARCHAR(100) NULL",
                "ALTER TABLE workflow_application ADD COLUMN current_handler VARCHAR(100) NULL",
                "ALTER TABLE workflow_application ADD COLUMN current_approval_count INT NOT NULL DEFAULT 0",
                "ALTER TABLE workflow_application ADD COLUMN resubmit_count INT NOT NULL DEFAULT 0",
                "ALTER TABLE workflow_application ADD COLUMN withdrawn_at DATETIME DEFAULT NULL",
                "ALTER TABLE workflow_application ADD COLUMN finished_at DATETIME DEFAULT NULL",
                "CREATE TABLE IF NOT EXISTS workflow_notification (id BIGINT AUTO_INCREMENT PRIMARY KEY, application_id BIGINT NOT NULL, receiver VARCHAR(100) NOT NULL, title VARCHAR(200) NOT NULL, message VARCHAR(1000) NOT NULL, read_flag TINYINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, INDEX idx_workflow_notice_receiver (receiver, read_flag), INDEX idx_workflow_notice_app (application_id))"
        };
        for (String sql : sqls) { try { jdbc.execute(sql); } catch (Exception ignored) { } }
        try { jdbc.update("UPDATE workflow_application SET flow_type=COALESCE(flow_type,'standard'), flow_name=COALESCE(flow_name,'标准审批'), current_step_name=COALESCE(current_step_name,'主管审批'), current_handler=COALESCE(current_handler,'technician/admin') WHERE deleted=0"); } catch (Exception ignored) { }
    }

    private record FlowStep(String name, List<String> roles, String mode, int requiredCount) {}

    public static class WorkflowSubmitRequest {
        public String category;
        public String typeKey;
        public String title;
        public String applicant;
        public String applicantRole;
        public String flowType;
        public Map<String, String> fields;
    }

    public static class WorkflowActionRequest {
        public String operator;
        public String operatorRole;
        public String comment;
        public String target;
    }

    public static class WorkflowResubmitRequest {
        public String operator;
        public String operatorRole;
        public String title;
        public String comment;
        public Map<String, String> fields;
    }
}
