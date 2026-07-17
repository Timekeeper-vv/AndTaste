package com.example.shixun.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0"));
        result.put("pendingCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND status='pending'"));
        result.put("approvedCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND status='approved'"));
        result.put("rejectedCount", count("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND status='rejected'"));
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
            int pageSize = Math.max(1, Math.min(size, 100));
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
        return loadApplication(id);
    }

    @PostMapping("/applications")
    public Map<String, Object> submit(@RequestBody WorkflowSubmitRequest req) {
        validateSubmitRequest(req);
        String role = normalizeRole(req.applicantRole, "feeder");
        validateRole(role, true);
        String title = blank(req.title) ? defaultTitle(req.category, req.typeKey) : req.title.trim();
        String appNo = no("WF");
        String formJson;
        try {
            formJson = mapper.writeValueAsString(req.fields == null ? Map.of() : req.fields);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请表单格式错误");
        }

        jdbc.update(
                "INSERT INTO workflow_application (app_no,category,type_key,title,applicant,applicant_role,form_data_json,status) VALUES (?,?,?,?,?,?,?,?)",
                appNo, req.category.trim(), req.typeKey.trim(), title, req.applicant.trim(), role, formJson, "pending"
        );
        Long id = jdbc.queryForObject("SELECT id FROM workflow_application WHERE app_no=?", Long.class, appNo);
        insertLog(id, "submit", req.applicant.trim(), role, "提交申请");
        return loadApplication(id);
    }

    @PostMapping("/applications/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id, @RequestBody WorkflowActionRequest req) {
        return act(id, req, "approved");
    }

    @PostMapping("/applications/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody WorkflowActionRequest req) {
        return act(id, req, "rejected");
    }

    private Map<String, Object> act(Long id, WorkflowActionRequest req, String targetStatus) {
        if (req == null || blank(req.operator) || blank(req.operatorRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "审批人信息不能为空");
        }
        validateRole(req.operatorRole.trim(), false);
        Map<String, Object> current = loadApplication(id);
        if (!"pending".equals(String.valueOf(current.get("status")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该申请已处理");
        }
        String comment = blank(req.comment) ? "" : req.comment.trim();
        jdbc.update(
                "UPDATE workflow_application SET status=?, approver=?, approval_comment=?, approved_at=CASE WHEN ?='approved' THEN CURRENT_TIMESTAMP ELSE approved_at END, rejected_at=CASE WHEN ?='rejected' THEN CURRENT_TIMESTAMP ELSE rejected_at END WHERE id=?",
                targetStatus, req.operator.trim(), comment, targetStatus, targetStatus, id
        );
        syncSampleRequestStatus(id, targetStatus, req.operator.trim());
        syncBulkProductionStatus(id, targetStatus, req.operator.trim());
        insertLog(id, targetStatus.equals("approved") ? "approve" : "reject", req.operator.trim(), req.operatorRole.trim(), comment);
        return loadApplication(id);
    }

    private void syncSampleRequestStatus(Long workflowId, String targetStatus, String operator) {
        String approvalStatus = "approved".equals(targetStatus) ? "已通过" : "已驳回";
        String workStatus = "approved".equals(targetStatus) ? "待打样" : "审批驳回";
        jdbc.update(
                "UPDATE supply_chain_sample_work_order SET approval_status=?, current_handler=NULL, work_order_status=?, updated_by=? WHERE workflow_application_id=? AND deleted=0",
                approvalStatus, workStatus, operator, workflowId
        );
    }

    private void syncBulkProductionStatus(Long workflowId, String targetStatus, String operator) {
        String approvalStatus = "approved".equals(targetStatus) ? "已通过" : "已驳回";
        String workStatus = "approved".equals(targetStatus) ? "待生产" : "审批驳回";
        jdbc.update(
                "UPDATE supply_chain_bulk_production_order SET approval_status=?, current_handler=NULL, work_order_status=?, updated_by=? WHERE workflow_application_id=? AND deleted=0",
                approvalStatus, workStatus, operator, workflowId
        );
    }

    private List<Map<String, Object>> listApplications(String status, String category, String applicant, Integer limit, Integer offset) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, app_no appNo, category, type_key typeKey, title, applicant, applicant_role applicantRole, form_data_json formDataJson, status, approver, approval_comment approvalComment, submitted_at submittedAt, updated_at updatedAt, approved_at approvedAt, rejected_at rejectedAt " +
                "FROM workflow_application WHERE deleted=0"
        );
        List<Object> params = new ArrayList<>();
        if (!blank(status)) {
            sql.append(" AND status=?");
            params.add(status.trim());
        }
        if (!blank(category)) {
            sql.append(" AND category=?");
            params.add(category.trim());
        }
        if (!blank(applicant)) {
            sql.append(" AND applicant=?");
            params.add(applicant.trim());
        }
        sql.append(" ORDER BY id DESC");
        if (limit != null) {
            sql.append(" LIMIT ?");
            params.add(limit);
            if (offset != null) {
                sql.append(" OFFSET ?");
                params.add(offset);
            }
        }
        return jdbc.query(sql.toString(), (rs, rowNum) -> rowToApplication(rs.getLong("id"),
                rs.getString("appNo"),
                rs.getString("category"),
                rs.getString("typeKey"),
                rs.getString("title"),
                rs.getString("applicant"),
                rs.getString("applicantRole"),
                rs.getString("formDataJson"),
                rs.getString("status"),
                rs.getString("approver"),
                rs.getString("approvalComment"),
                rs.getTimestamp("submittedAt"),
                rs.getTimestamp("updatedAt"),
                rs.getTimestamp("approvedAt"),
                rs.getTimestamp("rejectedAt")), params.toArray());
    }

    private Map<String, Object> loadApplication(Long id) {
        List<Map<String, Object>> list = jdbc.query(
                "SELECT id, app_no appNo, category, type_key typeKey, title, applicant, applicant_role applicantRole, form_data_json formDataJson, status, approver, approval_comment approvalComment, submitted_at submittedAt, updated_at updatedAt, approved_at approvedAt, rejected_at rejectedAt FROM workflow_application WHERE id=? AND deleted=0",
                (rs, rowNum) -> rowToApplication(rs.getLong("id"),
                        rs.getString("appNo"),
                        rs.getString("category"),
                        rs.getString("typeKey"),
                        rs.getString("title"),
                        rs.getString("applicant"),
                        rs.getString("applicantRole"),
                        rs.getString("formDataJson"),
                        rs.getString("status"),
                        rs.getString("approver"),
                        rs.getString("approvalComment"),
                        rs.getTimestamp("submittedAt"),
                        rs.getTimestamp("updatedAt"),
                        rs.getTimestamp("approvedAt"),
                        rs.getTimestamp("rejectedAt")),
                id
        );
        if (list.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "申请单不存在");
        return list.get(0);
    }

    private Map<String, Object> rowToApplication(Long id, String appNo, String category, String typeKey, String title, String applicant, String applicantRole,
                                                 String formJson, String status, String approver, String approvalComment,
                                                 java.sql.Timestamp submittedAt, java.sql.Timestamp updatedAt,
                                                 java.sql.Timestamp approvedAt, java.sql.Timestamp rejectedAt) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("appNo", appNo);
        item.put("category", category);
        item.put("type", typeKey);
        item.put("title", title);
        item.put("applicant", applicant);
        item.put("applicantRole", applicantRole);
        item.put("fields", parseFields(formJson));
        item.put("status", status);
        item.put("approver", approver);
        item.put("comment", approvalComment);
        item.put("createdAt", submittedAt == null ? "" : submittedAt.toLocalDateTime().toString().replace('T', ' '));
        item.put("updatedAt", updatedAt == null ? "" : updatedAt.toLocalDateTime().toString().replace('T', ' '));
        item.put("approvedAt", approvedAt == null ? null : approvedAt.toLocalDateTime().toString().replace('T', ' '));
        item.put("rejectedAt", rejectedAt == null ? null : rejectedAt.toLocalDateTime().toString().replace('T', ' '));
        return item;
    }

    private Map<String, String> parseFields(String json) {
        try {
            if (blank(json)) return Map.of();
            Map<String, String> raw = mapper.readValue(json, FORM_TYPE);
            return new LinkedHashMap<>(raw);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void insertLog(Long applicationId, String action, String operator, String operatorRole, String comment) {
        jdbc.update(
                "INSERT INTO workflow_approval_log (application_id, action, operator, operator_role, comment) VALUES (?,?,?,?,?)",
                applicationId, action, operator, operatorRole, blank(comment) ? null : comment
        );
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private long countApplications(String status, String category, String applicant, String applicantRole) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM workflow_application WHERE deleted=0");
        List<Object> params = new ArrayList<>();
        if (!blank(status)) {
            sql.append(" AND status=?");
            params.add(status.trim());
        }
        if (!blank(category)) {
            sql.append(" AND category=?");
            params.add(category.trim());
        }
        if (!blank(applicant)) {
            sql.append(" AND applicant=?");
            params.add(applicant.trim());
        }
        if (!blank(applicantRole)) {
            sql.append(" AND applicant_role=?");
            params.add(applicantRole.trim());
        }
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
        return blank(typeKey) ? "申请单" : typeKey;
    }

    private void validateSubmitRequest(WorkflowSubmitRequest req) {
        if (req == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        if (blank(req.category)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请分类不能为空");
        if (blank(req.typeKey)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请类型不能为空");
        if (blank(req.applicant)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "申请人不能为空");
    }

    private void validateRole(String role, boolean submit) {
        String r = normalizeRole(role, "");
        if (submit) {
            if (!List.of("admin", "technician", "feeder").contains(r)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限提交申请");
            }
        } else {
            if (!List.of("admin", "technician").contains(r)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权限审批");
            }
        }
    }

    private String normalizeRole(String role, String fallback) {
        String r = blank(role) ? fallback : role.trim();
        return blank(r) ? fallback : r;
    }

    private boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String no(String prefix) {
        return prefix + java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int) (Math.random() * 900 + 100);
    }

    public static class WorkflowSubmitRequest {
        public String category;
        public String typeKey;
        public String title;
        public String applicant;
        public String applicantRole;
        public Map<String, String> fields;
    }

    public static class WorkflowActionRequest {
        public String operator;
        public String operatorRole;
        public String comment;
    }
}
