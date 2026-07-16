package com.example.shixun.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "系统预警", description = "当前文创经营系统的仓储、物流、履约、报价等待处理预警")
public class NotificationController {

    private final JdbcTemplate jdbc;

    @Value("${kuaidi100.customer:}")
    private String kuaidi100Customer;

    @Value("${kuaidi100.key:}")
    private String kuaidi100Key;

    public NotificationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Operation(summary = "获取当前系统待处理预警")
    public List<Map<String, Object>> getNotifications() {
        List<Map<String, Object>> list = new ArrayList<>();
        addWorkflowAlerts(list);
        addWarehouseAlerts(list);
        addLogisticsAlerts(list);
        addPickTaskAlerts(list);
        addInquiryAlerts(list);
        addQuoteAlerts(list);
        addConfigAlerts(list);
        return list.size() > 20 ? list.subList(0, 20) : list;
    }

    private void addWorkflowAlerts(List<Map<String, Object>> list) {
        try {
            Long pending = jdbc.queryForObject("SELECT COUNT(*) FROM workflow_application WHERE deleted=0 AND status='pending'", Long.class);
            if (pending != null && pending > 0) {
                list.add(Map.of(
                        "type", "warning",
                        "category", "workflow",
                        "title", "审批待办",
                        "message", "当前有 " + pending + " 条待审批申请，请进入“审批中心”处理"
                ));
            }
        } catch (Exception ignored) { }
    }

    private void addWarehouseAlerts(List<Map<String, Object>> list) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT a.level, a.message, a.suggestion, i.item_name itemName, i.available_qty availableQty, i.safety_stock safetyStock " +
                    "FROM warehouse_alert a LEFT JOIN warehouse_inventory i ON a.inventory_id=i.id " +
                    "WHERE a.status='open' ORDER BY FIELD(a.level,'critical','warning','info'), a.id DESC LIMIT 8");
            for (Map<String, Object> r : rows) {
                String level = str(r.get("level"));
                String title = "critical".equals(level) ? "仓储严重预警" : "仓储库存预警";
                list.add(Map.of(
                        "type", "critical".equals(level) || "warning".equals(level) ? "warning" : "info",
                        "category", "warehouse",
                        "title", title,
                        "message", str(r.get("itemName")) + "：" + str(r.get("message")) + "；" + str(r.get("suggestion"))
                ));
            }
        } catch (Exception ignored) { }
    }

    private void addLogisticsAlerts(List<Map<String, Object>> list) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT order_no orderNo, carrier_name carrierName, tracking_no trackingNo, latest_trace latestTrace, status, alert_level alertLevel " +
                    "FROM logistics_shipment WHERE status='exception' OR alert_level IN ('warning','exception') ORDER BY updated_at DESC LIMIT 6");
            for (Map<String, Object> r : rows) {
                list.add(Map.of(
                        "type", "warning",
                        "category", "logistics",
                        "title", "物流异常待处理",
                        "message", str(r.get("carrierName")) + " " + str(r.get("trackingNo")) + "：" + str(r.get("latestTrace"))
                ));
            }
        } catch (Exception ignored) { }
    }

    private void addPickTaskAlerts(List<Map<String, Object>> list) {
        try {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_pick_task WHERE status IN ('pending','picking')", Long.class);
            if (count != null && count > 0) {
                Map<String, Object> first = jdbc.queryForMap("SELECT item_name itemName, qty, location_code locationCode FROM warehouse_pick_task WHERE status IN ('pending','picking') ORDER BY id DESC LIMIT 1");
                list.add(Map.of(
                        "type", "info",
                        "category", "pick_task",
                        "title", "待拣货任务",
                        "message", "当前有 " + count + " 个待拣货任务，最近一项：" + str(first.get("itemName")) + " × " + str(first.get("qty")) + "，库位 " + str(first.get("locationCode"))
                ));
            }
        } catch (Exception ignored) { }
    }

    private void addInquiryAlerts(List<Map<String, Object>> list) {
        try {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM customer_inquiry WHERE status='new'", Long.class);
            if (count != null && count > 0) {
                list.add(Map.of(
                        "type", "info",
                        "category", "inquiry",
                        "title", "新询盘待报价",
                        "message", "当前有 " + count + " 个新客户询盘待分析/报价，请进入“询盘到报价”处理"
                ));
            }
        } catch (Exception ignored) { }
    }

    private void addQuoteAlerts(List<Map<String, Object>> list) {
        try {
            Long draft = jdbc.queryForObject("SELECT COUNT(*) FROM inquiry_quote WHERE status='draft'", Long.class);
            Long sent = jdbc.queryForObject("SELECT COUNT(*) FROM inquiry_quote WHERE status='sent'", Long.class);
            if ((draft != null && draft > 0) || (sent != null && sent > 0)) {
                list.add(Map.of(
                        "type", "info",
                        "category", "quote",
                        "title", "报价单待跟进",
                        "message", "草稿报价 " + (draft == null ? 0 : draft) + " 张，已发送待确认 " + (sent == null ? 0 : sent) + " 张"
                ));
            }
        } catch (Exception ignored) { }
    }

    private void addConfigAlerts(List<Map<String, Object>> list) {
        if (blank(kuaidi100Customer) || blank(kuaidi100Key)) {
            list.add(Map.of(
                    "type", "warning",
                    "category", "config",
                    "title", "真实物流API未配置",
                    "message", "快递100 customer/key 未配置，物流模块不能查询真实轨迹"
            ));
        }
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
}
