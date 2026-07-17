package com.example.shixun.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/supply-chain/sample-work-orders")
@CrossOrigin(origins = "*")
public class SupplyChainSampleWorkOrderController {
    private static final int EXPECTED_DATA_ROWS = 154;
    private static final int EXPECTED_WORKSHEET_ROWS = 155;
    private static final int EXPECTED_SOURCE_COLUMNS = 34;
    private static final String SOURCE_FILE = "供应链工单明细_2026打样申请_打样申请 (1).xlsx";
    private static final String SOURCE_SHEET = "2026打样申请";

    private final JdbcTemplate jdbc;

    public SupplyChainSampleWorkOrderController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/verify")
    public Map<String, Object> verify() {
        Long total = longValue("SELECT COUNT(*) FROM supply_chain_sample_work_order");
        Long distinctSourceId = longValue("SELECT COUNT(DISTINCT source_id) FROM supply_chain_sample_work_order");
        Long distinctChecksum = longValue("SELECT COUNT(DISTINCT row_checksum) FROM supply_chain_sample_work_order");
        Long duplicateSourceId = longValue("SELECT COUNT(*) FROM (SELECT source_id FROM supply_chain_sample_work_order GROUP BY source_id HAVING COUNT(*) > 1) x");
        Long duplicateExcelRow = longValue("SELECT COUNT(*) FROM (SELECT import_batch, excel_row_no FROM supply_chain_sample_work_order GROUP BY import_batch, excel_row_no HAVING COUNT(*) > 1) x");
        return Map.ofEntries(
                Map.entry("sourceFile", SOURCE_FILE),
                Map.entry("sourceSheet", SOURCE_SHEET),
                Map.entry("expectedRows", EXPECTED_DATA_ROWS),
                Map.entry("expectedDataRows", EXPECTED_DATA_ROWS),
                Map.entry("expectedWorksheetRows", EXPECTED_WORKSHEET_ROWS),
                Map.entry("headerRows", 1),
                Map.entry("expectedColumns", EXPECTED_SOURCE_COLUMNS),
                Map.entry("importedRows", total),
                Map.entry("distinctSourceId", distinctSourceId),
                Map.entry("distinctChecksum", distinctChecksum),
                Map.entry("duplicateSourceIdGroups", duplicateSourceId),
                Map.entry("duplicateExcelRowGroups", duplicateExcelRow),
                Map.entry("missingRows", Math.max(0, EXPECTED_DATA_ROWS - total.intValue())),
                Map.entry("complete", total == EXPECTED_DATA_ROWS && distinctSourceId == EXPECTED_DATA_ROWS && duplicateSourceId == 0 && duplicateExcelRow == 0)
        );
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> verify = verify();
        return Map.of(
                "verify", verify,
                "status", group("work_order_status", 20),
                "orderType", group("order_type", 20),
                "productType", group("product_type", 20),
                "owner", group("owner", 20),
                "department", group("application_department", 20),
                "month", jdbc.queryForList("SELECT DATE_FORMAT(initiated_at, '%Y-%m') AS name, COUNT(*) AS count FROM supply_chain_sample_work_order GROUP BY name ORDER BY name DESC")
        );
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of(
                "statuses", distinct("work_order_status"),
                "owners", distinct("owner"),
                "orderTypes", distinct("order_type"),
                "productTypes", distinct("product_type"),
                "departments", distinct("application_department")
        );
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String owner,
                                    @RequestParam(required = false) String orderType,
                                    @RequestParam(required = false) String productType,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        page = Math.max(1, page);
        size = Math.min(Math.max(1, size), 500);
        Where where = where(keyword, status, owner, orderType, productType);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM supply_chain_sample_work_order" + where.sql, Long.class, where.args.toArray());
        List<Object> args = new ArrayList<>(where.args);
        args.add(size);
        args.add((page - 1) * size);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT id, import_batch importBatch, source_file sourceFile, source_sheet sourceSheet, excel_row_no excelRowNo, " +
                        "application_no applicationNo, approval_status approvalStatus, approval_flow approvalFlow, initiated_at initiatedAt, completed_at completedAt, " +
                        "initiator, initiator_department initiatorDepartment, application_department applicationDepartment, applicant, project_name projectName, " +
                        "product_name productName, order_type orderType, product_type productType, product_sub_type productSubType, product_estimate productEstimate, " +
                        "product_estimate_currency productEstimateCurrency, sample_quantity_text sampleQuantityText, spec_flavor specFlavor, sample_fee_yuan sampleFeeYuan, " +
                        "detail_remark detailRemark, detail_project_name detailProjectName, attachment_summary attachmentSummary, source_id sourceId, " +
                        "work_order_status workOrderStatus, start_date startDate, estimated_complete_date estimatedCompleteDate, actual_complete_date actualCompleteDate, " +
                        "owner, factory, sample_cost_yuan sampleCostYuan, sample_file_provided_date sampleFileProvidedDate, row_checksum rowChecksum " +
                        "FROM supply_chain_sample_work_order" + where.sql + " ORDER BY initiated_at DESC, excel_row_no ASC LIMIT ? OFFSET ?",
                args.toArray());
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
        return jdbc.queryForMap(
                "SELECT id, import_batch importBatch, source_file sourceFile, source_sheet sourceSheet, excel_row_no excelRowNo, " +
                        "application_no applicationNo, approval_status approvalStatus, approval_flow approvalFlow, initiated_at initiatedAt, completed_at completedAt, " +
                        "initiator, initiator_department initiatorDepartment, current_handler currentHandler, approval_node approvalNode, application_department applicationDepartment, applicant, " +
                        "project_name projectName, product_name productName, order_type orderType, product_type productType, product_sub_type productSubType, product_estimate productEstimate, " +
                        "product_estimate_currency productEstimateCurrency, sample_quantity_text sampleQuantityText, spec_flavor specFlavor, sample_fee_yuan sampleFeeYuan, " +
                        "detail_remark detailRemark, detail_project_name detailProjectName, linked_project_flow linkedProjectFlow, attachment_summary attachmentSummary, source_id sourceId, " +
                        "work_order_status workOrderStatus, start_date startDate, estimated_complete_date estimatedCompleteDate, actual_complete_date actualCompleteDate, " +
                        "owner, factory, sample_cost_yuan sampleCostYuan, sample_file_provided_date sampleFileProvidedDate, row_checksum rowChecksum, raw_json rawJson, created_at createdAt, updated_at updatedAt " +
                        "FROM supply_chain_sample_work_order WHERE id=?", id);
    }

    private Long longValue(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private List<Map<String, Object>> group(String column, int limit) {
        String safeColumn = switch (column) {
            case "work_order_status", "order_type", "product_type", "owner", "application_department" -> column;
            default -> throw new IllegalArgumentException("Unsupported group column");
        };
        return jdbc.queryForList("SELECT COALESCE(NULLIF(TRIM(" + safeColumn + "), ''), '未填写') AS name, COUNT(*) AS count " +
                "FROM supply_chain_sample_work_order GROUP BY name ORDER BY count DESC, name ASC LIMIT ?", limit);
    }

    private List<String> distinct(String column) {
        String safeColumn = switch (column) {
            case "work_order_status", "owner", "order_type", "product_type", "application_department" -> column;
            default -> throw new IllegalArgumentException("Unsupported distinct column");
        };
        return jdbc.queryForList("SELECT DISTINCT " + safeColumn + " FROM supply_chain_sample_work_order WHERE " + safeColumn + " IS NOT NULL AND TRIM(" + safeColumn + ") <> '' ORDER BY " + safeColumn, String.class);
    }

    private Where where(String keyword, String status, String owner, String orderType, String productType) {
        StringBuilder sql = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!blank(keyword)) {
            String k = "%" + keyword.trim() + "%";
            sql.append(" AND (application_no LIKE ? OR project_name LIKE ? OR product_name LIKE ? OR product_type LIKE ? OR product_sub_type LIKE ? OR applicant LIKE ? OR initiator LIKE ? OR owner LIKE ? OR source_id LIKE ?)");
            for (int i = 0; i < 9; i++) args.add(k);
        }
        if (!blank(status)) { sql.append(" AND work_order_status=?"); args.add(status.trim()); }
        if (!blank(owner)) { sql.append(" AND owner=?"); args.add(owner.trim()); }
        if (!blank(orderType)) { sql.append(" AND order_type=?"); args.add(orderType.trim()); }
        if (!blank(productType)) { sql.append(" AND product_type=?"); args.add(productType.trim()); }
        return new Where(sql.toString(), args);
    }

    private boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private record Where(String sql, List<Object> args) {}
}
