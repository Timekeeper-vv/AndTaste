package com.example.shixun.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/supply-chain/bulk-production-orders")
@CrossOrigin(origins = "*")
public class SupplyChainBulkProductionController {
    private static final int EXPECTED_DATA_ROWS = 536;
    private static final int EXPECTED_WORKSHEET_ROWS = 537;
    private static final int EXPECTED_SOURCE_COLUMNS = 35;
    private static final String SOURCE_FILE = "供应链工单明细_2026年大货生产审批.xlsx";
    private static final String SOURCE_SHEET = "2026年大货生产审批";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SupplyChainBulkProductionController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping("/verify")
    public Map<String, Object> verify() {
        Long excelTotal = longValue("SELECT COUNT(*) FROM supply_chain_bulk_production_order WHERE source_kind='excel'");
        Long activeTotal = longValue("SELECT COUNT(*) FROM supply_chain_bulk_production_order WHERE deleted=0");
        Long manualTotal = longValue("SELECT COUNT(*) FROM supply_chain_bulk_production_order WHERE deleted=0 AND source_kind='manual'");
        Long distinctSourceId = longValue("SELECT COUNT(DISTINCT source_id) FROM supply_chain_bulk_production_order WHERE source_kind='excel'");
        Long duplicateSourceId = longValue("SELECT COUNT(*) FROM (SELECT source_id FROM supply_chain_bulk_production_order WHERE source_kind='excel' GROUP BY source_id HAVING COUNT(*) > 1) x");
        Long duplicateExcelRow = longValue("SELECT COUNT(*) FROM (SELECT import_batch, excel_row_no FROM supply_chain_bulk_production_order WHERE source_kind='excel' GROUP BY import_batch, excel_row_no HAVING COUNT(*) > 1) x");
        return Map.ofEntries(
                Map.entry("sourceFile", SOURCE_FILE),
                Map.entry("sourceSheet", SOURCE_SHEET),
                Map.entry("expectedRows", EXPECTED_DATA_ROWS),
                Map.entry("expectedDataRows", EXPECTED_DATA_ROWS),
                Map.entry("expectedWorksheetRows", EXPECTED_WORKSHEET_ROWS),
                Map.entry("headerRows", 1),
                Map.entry("expectedColumns", EXPECTED_SOURCE_COLUMNS),
                Map.entry("importedRows", excelTotal),
                Map.entry("activeRows", activeTotal),
                Map.entry("manualRows", manualTotal),
                Map.entry("distinctSourceId", distinctSourceId),
                Map.entry("duplicateSourceIdGroups", duplicateSourceId),
                Map.entry("duplicateExcelRowGroups", duplicateExcelRow),
                Map.entry("missingRows", Math.max(0, EXPECTED_DATA_ROWS - excelTotal.intValue())),
                Map.entry("complete", excelTotal == EXPECTED_DATA_ROWS && distinctSourceId == EXPECTED_DATA_ROWS && duplicateSourceId == 0 && duplicateExcelRow == 0)
        );
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "verify", verify(),
                "status", group("work_order_status", 20),
                "approvalStatus", group("approval_status", 20),
                "projectType", group("project_type", 20),
                "projectLevel", group("project_level", 20),
                "productionType", group("production_type", 20),
                "primaryCategory", group("primary_category", 20),
                "owner", group("owner", 20),
                "department", group("application_department", 20),
                "month", jdbc.queryForList("SELECT DATE_FORMAT(initiated_at, '%Y-%m') AS name, COUNT(*) AS count FROM supply_chain_bulk_production_order WHERE deleted=0 GROUP BY name ORDER BY name DESC")
        );
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of(
                "statuses", distinct("work_order_status"),
                "owners", distinct("owner"),
                "projectTypes", distinct("project_type"),
                "projectLevels", distinct("project_level"),
                "productionTypes", distinct("production_type"),
                "primaryCategories", distinct("primary_category"),
                "departments", distinct("application_department")
        );
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String owner,
                                    @RequestParam(required = false) String projectType,
                                    @RequestParam(required = false) String productionType,
                                    @RequestParam(required = false) String primaryCategory,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), 500);
        Where where = where(keyword, status, owner, projectType, productionType, primaryCategory);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM supply_chain_bulk_production_order" + where.sql, Long.class, where.args.toArray());
        List<Object> args = new ArrayList<>(where.args);
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> items = jdbc.queryForList(selectSql(false) + where.sql + " ORDER BY initiated_at DESC, excel_row_no ASC LIMIT ? OFFSET ?", args.toArray());
        return Map.of(
                "items", items,
                "total", total == null ? 0 : total,
                "page", page,
                "size", size,
                "expectedRows", EXPECTED_DATA_ROWS,
                "expectedDataRows", EXPECTED_DATA_ROWS,
                "expectedWorksheetRows", EXPECTED_WORKSHEET_ROWS,
                "expectedColumns", EXPECTED_SOURCE_COLUMNS
        );
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable Long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(selectSql(true) + " WHERE id=? AND deleted=0", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "大货生产单不存在");
        return rows.get(0);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        String productName = text(body.get("productName"));
        if (blank(productName)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "产品名称不能为空");
        String applicant = firstNonBlank(text(body.get("applicant")), text(body.get("initiator")), "当前用户");
        String sourceId = "MANUAL-BULK-" + UUID.randomUUID();
        String appNo = firstNonBlank(text(body.get("applicationNo")), no("DH"));
        int excelRowNo = nextManualRowNo();
        String rawJson = rawJson(body, appNo, sourceId, excelRowNo);
        String checksum = sha256(sourceId + "|" + rawJson);

        jdbc.update("INSERT INTO supply_chain_bulk_production_order (" +
                        "import_batch, source_file, source_sheet, excel_row_no, application_no, approval_status, approval_flow, initiated_at, completed_at, " +
                        "initiator, initiator_department, current_handler, approval_node, application_department, applicant, project_name, project_type, project_level, project_detail, " +
                        "product_name, product_code, primary_category, secondary_category, production_type, production_quantity, spec_flavor, unit_price, unit_price_currency, product_remark, " +
                        "design_attachment_summary, linked_approval, contract_attachment_summary, source_id, work_order_status, start_date, estimated_complete_date, actual_complete_date, owner, factory, " +
                        "source_kind, created_by, updated_by, row_checksum, raw_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "系统大货生产申请", "系统新增", "2026年大货生产审批", excelRowNo, appNo, "草稿", "大货生产审批2026年", now(), null,
                applicant, text(body.get("initiatorDepartment")), null, null, text(body.get("applicationDepartment")), applicant, text(body.get("projectName")), text(body.get("projectType")), text(body.get("projectLevel")), text(body.get("projectDetail")),
                productName, text(body.get("productCode")), text(body.get("primaryCategory")), text(body.get("secondaryCategory")), text(body.get("productionType")), intText(body.get("productionQuantity")), text(body.get("specFlavor")), decimalText(body.get("unitPrice")), firstNonBlank(text(body.get("unitPriceCurrency")), "CNY"), text(body.get("productRemark")),
                text(body.get("designAttachmentSummary")), text(body.get("linkedApproval")), text(body.get("contractAttachmentSummary")), sourceId, "草稿", dateText(body.get("startDate")), dateText(body.get("estimatedCompleteDate")), dateText(body.get("actualCompleteDate")), text(body.get("owner")), text(body.get("factory")),
                "manual", applicant, applicant, checksum, rawJson);
        Long id = jdbc.queryForObject("SELECT id FROM supply_chain_bulk_production_order WHERE source_id=?", Long.class, sourceId);
        return detail(id);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ensureExists(id);
        String productName = text(body.get("productName"));
        if (blank(productName)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "产品名称不能为空");
        String updater = firstNonBlank(text(body.get("updatedBy")), text(body.get("applicant")), "当前用户");
        jdbc.update("UPDATE supply_chain_bulk_production_order SET " +
                        "application_department=?, applicant=?, project_name=?, project_type=?, project_level=?, project_detail=?, product_name=?, product_code=?, primary_category=?, secondary_category=?, " +
                        "production_type=?, production_quantity=?, spec_flavor=?, unit_price=?, unit_price_currency=?, product_remark=?, design_attachment_summary=?, linked_approval=?, contract_attachment_summary=?, " +
                        "start_date=?, estimated_complete_date=?, actual_complete_date=?, owner=?, factory=?, updated_by=? WHERE id=? AND deleted=0",
                text(body.get("applicationDepartment")), text(body.get("applicant")), text(body.get("projectName")), text(body.get("projectType")), text(body.get("projectLevel")), text(body.get("projectDetail")), productName, text(body.get("productCode")), text(body.get("primaryCategory")), text(body.get("secondaryCategory")),
                text(body.get("productionType")), intText(body.get("productionQuantity")), text(body.get("specFlavor")), decimalText(body.get("unitPrice")), firstNonBlank(text(body.get("unitPriceCurrency")), "CNY"), text(body.get("productRemark")), text(body.get("designAttachmentSummary")), text(body.get("linkedApproval")), text(body.get("contractAttachmentSummary")),
                dateText(body.get("startDate")), dateText(body.get("estimatedCompleteDate")), dateText(body.get("actualCompleteDate")), text(body.get("owner")), text(body.get("factory")), updater, id);
        return detail(id);
    }

    @PutMapping("/{id}/work-status")
    public Map<String, Object> updateWorkStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        ensureExists(id);
        String status = text(body.get("workOrderStatus"));
        if (blank(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工单状态不能为空");
        jdbc.update("UPDATE supply_chain_bulk_production_order SET work_order_status=?, start_date=?, estimated_complete_date=?, actual_complete_date=?, owner=?, factory=?, updated_by=? WHERE id=? AND deleted=0",
                status, dateText(body.get("startDate")), dateText(body.get("estimatedCompleteDate")), dateText(body.get("actualCompleteDate")), text(body.get("owner")), text(body.get("factory")), firstNonBlank(text(body.get("updatedBy")), "当前用户"), id);
        return detail(id);
    }

    @PostMapping("/{id}/submit-approval")
    public Map<String, Object> submitApproval(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> row = detail(id);
        String applicant = firstNonBlank(text(body.get("applicant")), text(row.get("applicant")), "当前用户");
        String role = firstNonBlank(text(body.get("applicantRole")), "feeder");
        String title = "大货生产申请：" + firstNonBlank(text(row.get("projectName")), "未命名项目") + " / " + text(row.get("productName"));
        String appNo = no("WF");
        String formJson = workflowFormJson(row);
        jdbc.update("INSERT INTO workflow_application (app_no,category,type_key,title,applicant,applicant_role,form_data_json,status) VALUES (?,?,?,?,?,?,?,?)",
                appNo, "production", "bulkProductionRequest", title, applicant, role, formJson, "pending");
        Long workflowId = jdbc.queryForObject("SELECT id FROM workflow_application WHERE app_no=?", Long.class, appNo);
        jdbc.update("INSERT INTO workflow_approval_log (application_id, action, operator, operator_role, comment) VALUES (?,?,?,?,?)",
                workflowId, "submit", applicant, role, "提交大货生产申请");
        jdbc.update("UPDATE supply_chain_bulk_production_order SET workflow_application_id=?, approval_status='审批中', current_handler='审批中心', work_order_status='待审批', updated_by=? WHERE id=? AND deleted=0",
                workflowId, applicant, id);
        return detail(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        ensureExists(id);
        String operator = body == null ? "当前用户" : firstNonBlank(text(body.get("operator")), "当前用户");
        jdbc.update("UPDATE supply_chain_bulk_production_order SET deleted=1, updated_by=? WHERE id=?", operator, id);
        return Map.of("success", true, "id", id);
    }

    private String selectSql(boolean detail) {
        String extra = detail ? ", current_handler currentHandler, approval_node approvalNode, raw_json rawJson, created_by createdBy, updated_by updatedBy, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt, DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt " : " ";
        return "SELECT id, import_batch importBatch, source_file sourceFile, source_sheet sourceSheet, excel_row_no excelRowNo, " +
                "application_no applicationNo, approval_status approvalStatus, approval_flow approvalFlow, " +
                "DATE_FORMAT(initiated_at, '%Y-%m-%d %H:%i:%s') AS initiatedAt, DATE_FORMAT(completed_at, '%Y-%m-%d %H:%i:%s') AS completedAt, " +
                "initiator, initiator_department initiatorDepartment, application_department applicationDepartment, applicant, project_name projectName, project_type projectType, project_level projectLevel, project_detail projectDetail, " +
                "product_name productName, product_code productCode, primary_category primaryCategory, secondary_category secondaryCategory, production_type productionType, production_quantity productionQuantity, spec_flavor specFlavor, unit_price unitPrice, unit_price_currency unitPriceCurrency, product_remark productRemark, " +
                "design_attachment_summary designAttachmentSummary, linked_approval linkedApproval, contract_attachment_summary contractAttachmentSummary, source_id sourceId, work_order_status workOrderStatus, " +
                "DATE_FORMAT(start_date, '%Y-%m-%d') AS startDate, DATE_FORMAT(estimated_complete_date, '%Y-%m-%d') AS estimatedCompleteDate, DATE_FORMAT(actual_complete_date, '%Y-%m-%d') AS actualCompleteDate, " +
                "owner, factory, source_kind sourceKind, workflow_application_id workflowApplicationId" + extra +
                "FROM supply_chain_bulk_production_order";
    }

    private Long longValue(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private List<Map<String, Object>> group(String column, int limit) {
        String safeColumn = switch (column) {
            case "work_order_status", "approval_status", "project_type", "project_level", "production_type", "primary_category", "owner", "application_department" -> column;
            default -> throw new IllegalArgumentException("Unsupported group column");
        };
        return jdbc.queryForList("SELECT COALESCE(NULLIF(TRIM(" + safeColumn + "), ''), '未填写') AS name, COUNT(*) AS count " +
                "FROM supply_chain_bulk_production_order WHERE deleted=0 GROUP BY name ORDER BY count DESC, name ASC LIMIT ?", limit);
    }

    private List<String> distinct(String column) {
        String safeColumn = switch (column) {
            case "work_order_status", "owner", "project_type", "project_level", "production_type", "primary_category", "application_department" -> column;
            default -> throw new IllegalArgumentException("Unsupported distinct column");
        };
        return jdbc.queryForList("SELECT DISTINCT " + safeColumn + " FROM supply_chain_bulk_production_order WHERE deleted=0 AND " + safeColumn + " IS NOT NULL AND TRIM(" + safeColumn + ") <> '' ORDER BY " + safeColumn, String.class);
    }

    private Where where(String keyword, String status, String owner, String projectType, String productionType, String primaryCategory) {
        StringBuilder sql = new StringBuilder(" WHERE deleted=0");
        List<Object> args = new ArrayList<>();
        if (!blank(keyword)) {
            String k = "%" + keyword.trim() + "%";
            sql.append(" AND (application_no LIKE ? OR project_name LIKE ? OR product_name LIKE ? OR product_code LIKE ? OR primary_category LIKE ? OR secondary_category LIKE ? OR applicant LIKE ? OR initiator LIKE ? OR owner LIKE ? OR source_id LIKE ?)");
            for (int i = 0; i < 10; i++) args.add(k);
        }
        if (!blank(status)) { sql.append(" AND work_order_status=?"); args.add(status.trim()); }
        if (!blank(owner)) { sql.append(" AND owner=?"); args.add(owner.trim()); }
        if (!blank(projectType)) { sql.append(" AND project_type=?"); args.add(projectType.trim()); }
        if (!blank(productionType)) { sql.append(" AND production_type=?"); args.add(productionType.trim()); }
        if (!blank(primaryCategory)) { sql.append(" AND primary_category=?"); args.add(primaryCategory.trim()); }
        return new Where(sql.toString(), args);
    }

    private void ensureExists(Long id) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM supply_chain_bulk_production_order WHERE id=? AND deleted=0", Integer.class, id);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "大货生产单不存在");
    }

    private int nextManualRowNo() {
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(excel_row_no), 0) FROM supply_chain_bulk_production_order WHERE import_batch='系统大货生产申请'", Integer.class);
        return (max == null ? 0 : max) + 1;
    }

    private String workflowFormJson(Map<String, Object> row) {
        try {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("关联大货生产单ID", String.valueOf(row.get("id")));
            form.put("申请编号", text(row.get("applicationNo")));
            form.put("项目名称", text(row.get("projectName")));
            form.put("项目类型", text(row.get("projectType")));
            form.put("项目等级", text(row.get("projectLevel")));
            form.put("产品名称", text(row.get("productName")));
            form.put("产品编码", text(row.get("productCode")));
            form.put("产品分类", text(row.get("primaryCategory")) + " / " + text(row.get("secondaryCategory")));
            form.put("生产类型", text(row.get("productionType")));
            form.put("生产数量", text(row.get("productionQuantity")));
            form.put("规格/口味", text(row.get("specFlavor")));
            form.put("单价", text(row.get("unitPrice")) + " " + text(row.get("unitPriceCurrency")));
            form.put("负责人", text(row.get("owner")));
            form.put("预计完成时间", text(row.get("estimatedCompleteDate")));
            form.put("项目详情", text(row.get("projectDetail")));
            form.put("备注", text(row.get("productRemark")));
            return mapper.writeValueAsString(form);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String rawJson(Map<String, Object> body, String appNo, String sourceId, int rowNo) {
        try {
            Map<String, Object> cells = new LinkedHashMap<>(body);
            cells.put("申请编号", appNo);
            cells.put("SourceID", sourceId);
            return mapper.writeValueAsString(Map.of("sourceFile", "系统新增", "sheet", SOURCE_SHEET, "excelRow", rowNo, "cells", cells));
        } catch (Exception e) {
            return "{}";
        }
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    private String text(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).trim();
        return s.length() > 1000 ? s.substring(0, 1000) : s;
    }

    private String dateText(Object value) {
        String s = text(value);
        return s.isBlank() ? null : s.substring(0, Math.min(10, s.length()));
    }

    private String decimalText(Object value) {
        String s = text(value).replace(",", "");
        return s.matches("[-+]?\\d+(\\.\\d+)?") ? s : null;
    }

    private String intText(Object value) {
        String s = text(value).replace(",", "");
        return s.matches("[-+]?\\d+") ? s : null;
    }

    private String now() { return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now()); }

    private String no(String prefix) { return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int) (Math.random() * 900 + 100); }

    private String firstNonBlank(String... values) {
        for (String v : values) if (!blank(v)) return v;
        return "";
    }

    private String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        }
    }

    private record Where(String sql, List<Object> args) {}
}
