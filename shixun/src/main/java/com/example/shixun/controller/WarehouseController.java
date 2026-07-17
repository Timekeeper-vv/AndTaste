package com.example.shixun.controller;

import com.example.shixun.service.SiliconFlowChatService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/warehouse")
@CrossOrigin(origins = "*")
public class WarehouseController {
    private final JdbcTemplate jdbc;
    private final SiliconFlowChatService ai;

    public WarehouseController(JdbcTemplate jdbc, SiliconFlowChatService ai) {
        this.jdbc = jdbc;
        this.ai = ai;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "productCount", qLong("SELECT COUNT(*) FROM warehouse_product_catalog WHERE enabled=1"),
                "itemCount", qLong("SELECT COUNT(*) FROM warehouse_inventory"),
                "totalStock", qDecimal("SELECT COALESCE(SUM(stock_qty),0) FROM warehouse_inventory"),
                "availableStock", qDecimal("SELECT COALESCE(SUM(available_qty),0) FROM warehouse_inventory"),
                "catalogInitialQty", qDecimal("SELECT COALESCE(SUM(initial_qty),0) FROM warehouse_product_catalog WHERE enabled=1"),
                "inboundToday", qLong("SELECT COUNT(*) FROM warehouse_inbound WHERE DATE(created_at)=CURRENT_DATE"),
                "outboundToday", qLong("SELECT COUNT(*) FROM warehouse_outbound WHERE DATE(created_at)=CURRENT_DATE"),
                "pendingPick", qLong("SELECT COUNT(*) FROM warehouse_pick_task WHERE status IN ('pending','picking')"),
                "alertCount", qLong("SELECT COUNT(*) FROM warehouse_alert WHERE status='open'")
        );
    }

    @GetMapping("/locations")
    public List<Map<String, Object>> locations() {
        return jdbc.queryForList("SELECT id, location_code locationCode, name, zone, capacity, enabled FROM warehouse_location WHERE enabled=1 ORDER BY location_code");
    }

    @GetMapping("/inventory")
    public List<Map<String, Object>> inventory() {
        return jdbc.queryForList("SELECT i.id, i.item_type itemType, i.item_id itemId, i.item_code itemCode, i.item_name itemName, i.spec, i.unit, i.location_id locationId, l.location_code locationCode, l.name locationName, i.stock_qty stockQty, i.locked_qty lockedQty, i.available_qty availableQty, i.safety_stock safetyStock, i.max_stock maxStock, i.last_in_at lastInAt, i.last_out_at lastOutAt, i.updated_at updatedAt, p.primary_category primaryCategory, p.secondary_category secondaryCategory, p.box_code boxCode, p.settlement_unit_price settlementUnitPrice, p.product_cost_unit_price productCostUnitPrice, p.company_cost_price companyCostPrice, p.cold_category coldCategory FROM warehouse_inventory i LEFT JOIN warehouse_location l ON i.location_id=l.id LEFT JOIN warehouse_product_catalog p ON p.product_code=i.item_code ORDER BY i.updated_at DESC, i.id DESC");
    }

    @GetMapping("/products")
    public Map<String, Object> products(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String primaryCategory,
                                        @RequestParam(required = false) String secondaryCategory,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "100") int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(10, Math.min(pageSize, 500));
        StringBuilder where = new StringBuilder(" WHERE enabled=1 ");
        List<Object> params = new ArrayList<>();
        if (!blank(keyword)) {
            where.append(" AND (product_name LIKE ? OR product_code LIKE ? OR box_code LIKE ? OR cold_category LIKE ?) ");
            String kw = "%" + keyword.trim() + "%";
            params.add(kw); params.add(kw); params.add(kw); params.add(kw);
        }
        if (!blank(primaryCategory)) { where.append(" AND primary_category=? "); params.add(primaryCategory.trim()); }
        if (!blank(secondaryCategory)) { where.append(" AND secondary_category=? "); params.add(secondaryCategory.trim()); }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_product_catalog" + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(safeSize);
        pageParams.add((safePage - 1) * safeSize);
        List<Map<String,Object>> items = jdbc.queryForList(
                "SELECT id, product_name productName, product_code productCode, product_ref_code productRefCode, box_code boxCode, primary_category primaryCategory, secondary_category secondaryCategory, mold_type moldType, style_count styleCount, initial_qty initialQty, location_name locationName, sample_fee sampleFee, bulk_mold_fee bulkMoldFee, settlement_unit_price settlementUnitPrice, product_cost_unit_price productCostUnitPrice, company_cost_price companyCostPrice, spec_description specDescription, cold_category coldCategory, source_created_at sourceCreatedAt, updated_at updatedAt FROM warehouse_product_catalog" + where + " ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?",
                pageParams.toArray());
        return Map.of(
                "total", total == null ? 0 : total,
                "page", safePage,
                "pageSize", safeSize,
                "items", items,
                "primaryCategories", jdbc.queryForList("SELECT primary_category value, COUNT(*) count FROM warehouse_product_catalog WHERE enabled=1 AND primary_category IS NOT NULL AND primary_category<>'' GROUP BY primary_category ORDER BY count DESC, primary_category"),
                "secondaryCategories", jdbc.queryForList("SELECT secondary_category value, COUNT(*) count FROM warehouse_product_catalog WHERE enabled=1 AND secondary_category IS NOT NULL AND secondary_category<>'' GROUP BY secondary_category ORDER BY count DESC, secondary_category LIMIT 120")
        );
    }

    @GetMapping("/inbound")
    public List<Map<String, Object>> inboundList() {
        List<Map<String, Object>> list = jdbc.queryForList("SELECT id, inbound_no inboundNo, source_type sourceType, supplier, operator, remark, status, created_at createdAt FROM warehouse_inbound ORDER BY id DESC LIMIT 50");
        for (Map<String, Object> r : list) r.put("items", jdbc.queryForList("SELECT id, inbound_id inboundId, inventory_id inventoryId, item_name itemName, qty, unit_cost unitCost, location_code locationCode FROM warehouse_inbound_item WHERE inbound_id=?", r.get("id")));
        return list;
    }

    @GetMapping("/outbound")
    public List<Map<String, Object>> outboundList() {
        List<Map<String, Object>> list = jdbc.queryForList("SELECT id, outbound_no outboundNo, order_no orderNo, purpose, receiver, operator, status, created_at createdAt FROM warehouse_outbound ORDER BY id DESC LIMIT 50");
        for (Map<String, Object> r : list) r.put("items", jdbc.queryForList("SELECT id, outbound_id outboundId, inventory_id inventoryId, item_name itemName, qty, location_code locationCode FROM warehouse_outbound_item WHERE outbound_id=?", r.get("id")));
        return list;
    }

    @GetMapping("/pick-tasks")
    public List<Map<String, Object>> pickTasks() {
        return jdbc.queryForList("SELECT p.id, p.pick_no pickNo, p.outbound_id outboundId, o.outbound_no outboundNo, o.order_no orderNo, p.inventory_id inventoryId, p.item_name itemName, p.qty, p.location_code locationCode, p.status, p.operator, p.created_at createdAt, p.completed_at completedAt FROM warehouse_pick_task p JOIN warehouse_outbound o ON p.outbound_id=o.id ORDER BY p.id DESC LIMIT 100");
    }

    @GetMapping("/alerts")
    public List<Map<String, Object>> alerts() {
        return jdbc.queryForList("SELECT a.id, a.alert_no alertNo, a.inventory_id inventoryId, i.item_name itemName, i.item_code itemCode, a.alert_type alertType, a.level, a.message, a.suggestion, a.status, a.created_at createdAt FROM warehouse_alert a LEFT JOIN warehouse_inventory i ON a.inventory_id=i.id WHERE a.status='open' ORDER BY FIELD(a.level,'critical','warning','info'), a.id DESC");
    }

    @PostMapping("/inbound")
    public Map<String, Object> inbound(@RequestBody InboundRequest req) {
        enrichInboundFromCatalog(req);
        if (blank(req.itemName) || req.qty == null || req.qty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("入库商品和数量不能为空");
        Long locationId = ensureLocation(req.locationCode);
        Long inventoryId = ensureInventory(req, locationId);
        String inboundNo = no("IN");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO warehouse_inbound (inbound_no,source_type,supplier,operator,remark,status) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, inboundNo); ps.setString(2, nvl(req.sourceType,"purchase")); ps.setString(3, req.supplier); ps.setString(4, nvl(req.operator,"仓库员")); ps.setString(5, req.remark); ps.setString(6, "done"); return ps;
        }, kh);
        Long inboundId = Objects.requireNonNull(kh.getKey()).longValue();
        jdbc.update("INSERT INTO warehouse_inbound_item (inbound_id,inventory_id,item_name,qty,unit_cost,location_code) VALUES (?,?,?,?,?,?)", inboundId, inventoryId, req.itemName, req.qty, req.unitCost == null ? BigDecimal.ZERO : req.unitCost, req.locationCode);
        jdbc.update("UPDATE warehouse_inventory SET stock_qty=stock_qty+?, available_qty=available_qty+?, last_in_at=NOW() WHERE id=?", req.qty, req.qty, inventoryId);
        refreshAlerts();
        return Map.of("inboundId", inboundId, "inboundNo", inboundNo, "inventoryId", inventoryId, "message", "入库完成，库存已增加");
    }

    @PostMapping("/outbound")
    public Map<String, Object> outbound(@RequestBody OutboundRequest req) {
        if (req.inventoryId == null || req.qty == null || req.qty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("请选择出库库存并填写数量");
        Map<String, Object> inv = jdbc.queryForMap("SELECT id, item_name itemName, available_qty availableQty, location_id locationId FROM warehouse_inventory WHERE id=?", req.inventoryId);
        BigDecimal available = bd(inv.get("availableQty"));
        if (available.compareTo(req.qty) < 0) throw new IllegalArgumentException("可用库存不足，当前可用 " + available);
        String locationCode = jdbc.queryForObject("SELECT location_code FROM warehouse_location WHERE id=?", String.class, inv.get("locationId"));
        String outboundNo = no("OUT");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO warehouse_outbound (outbound_no,order_no,purpose,receiver,operator,status) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, outboundNo); ps.setString(2, req.orderNo); ps.setString(3, nvl(req.purpose,"订单发货")); ps.setString(4, req.receiver); ps.setString(5, nvl(req.operator,"仓库员")); ps.setString(6, "picking"); return ps;
        }, kh);
        Long outboundId = Objects.requireNonNull(kh.getKey()).longValue();
        jdbc.update("INSERT INTO warehouse_outbound_item (outbound_id,inventory_id,item_name,qty,location_code) VALUES (?,?,?,?,?)", outboundId, req.inventoryId, inv.get("itemName"), req.qty, locationCode);
        jdbc.update("UPDATE warehouse_inventory SET locked_qty=locked_qty+?, available_qty=available_qty-? WHERE id=?", req.qty, req.qty, req.inventoryId);
        String pickNo = no("PICK");
        jdbc.update("INSERT INTO warehouse_pick_task (pick_no,outbound_id,inventory_id,item_name,qty,location_code,status,operator) VALUES (?,?,?,?,?,?,?,?)", pickNo, outboundId, req.inventoryId, inv.get("itemName"), req.qty, locationCode, "pending", nvl(req.operator,"仓库员"));
        refreshAlerts();
        return Map.of("outboundId", outboundId, "outboundNo", outboundNo, "pickNo", pickNo, "message", "出库单已创建，已生成拣货任务");
    }

    @PostMapping("/pick-tasks/{id}/complete")
    public Map<String, Object> completePick(@PathVariable Long id) {
        Map<String, Object> p = jdbc.queryForMap("SELECT id, outbound_id outboundId, inventory_id inventoryId, qty, status FROM warehouse_pick_task WHERE id=?", id);
        if ("done".equals(p.get("status"))) return Map.of("message", "该拣货任务已完成");
        BigDecimal qty = bd(p.get("qty"));
        Long inventoryId = ((Number)p.get("inventoryId")).longValue();
        jdbc.update("UPDATE warehouse_inventory SET stock_qty=stock_qty-?, locked_qty=GREATEST(locked_qty-?,0), last_out_at=NOW() WHERE id=?", qty, qty, inventoryId);
        jdbc.update("UPDATE warehouse_pick_task SET status='done', completed_at=NOW() WHERE id=?", id);
        jdbc.update("UPDATE warehouse_outbound SET status='shipped' WHERE id=?", p.get("outboundId"));
        refreshAlerts();
        return Map.of("message", "拣货完成，库存已扣减，出库单已发货");
    }

    @PostMapping("/alerts/refresh")
    public Map<String, Object> refreshAlertsApi() {
        int count = refreshAlerts();
        return Map.of("message", "智能预警已刷新", "alertCount", count);
    }

    @PostMapping("/alerts/ai-report")
    public Map<String, Object> aiReport() {
        List<Map<String,Object>> inv = inventory();
        List<Map<String,Object>> alerts = alerts();
        String prompt = "库存数据：" + inv.subList(0, Math.min(inv.size(), 20)) + "\n当前预警：" + alerts + "\n请输出文创仓储补货、拣货、出库风险建议，要求简洁可执行。";
        String report;
        try { report = ai.chat("你是文创产品智能仓储经理，关注库存周转、缺货、超储、拣货效率和发货风险。", prompt, 0.45, 900, 60); }
        catch (Exception e) { report = "AI报告生成失败：" + e.getMessage() + "\n可先处理低库存、缺货和待拣货任务。"; }
        return Map.of("reportNo", no("WAR"), "source", "siliconflow:" + ai.modelName(), "report", report);
    }

    private int refreshAlerts() {
        jdbc.update("UPDATE warehouse_alert SET status='closed' WHERE status='open'");
        List<Map<String,Object>> invs = jdbc.queryForList("SELECT id,item_name itemName,item_code itemCode,stock_qty stockQty,available_qty availableQty,safety_stock safetyStock,max_stock maxStock FROM warehouse_inventory");
        int count = 0;
        for (Map<String,Object> i : invs) {
            Long id = ((Number)i.get("id")).longValue();
            BigDecimal stock = bd(i.get("stockQty")); BigDecimal available = bd(i.get("availableQty")); BigDecimal safety = bd(i.get("safetyStock")); BigDecimal max = bd(i.get("maxStock"));
            if (available.compareTo(BigDecimal.ZERO) <= 0) { addAlert(id,"out_of_stock","critical","库存缺货","可用库存为0，请立即补货或暂停接单"); count++; }
            else if (available.compareTo(safety) <= 0) { addAlert(id,"low_stock","warning","低库存预警","可用库存低于安全库存，建议发起采购/生产补货"); count++; }
            if (max.compareTo(BigDecimal.ZERO) > 0 && stock.compareTo(max) > 0) { addAlert(id,"over_stock","info","库存偏高","库存超过上限，建议促销或控制入库节奏"); count++; }
        }
        return count;
    }

    private void addAlert(Long inventoryId, String type, String level, String msg, String suggestion) {
        jdbc.update("INSERT INTO warehouse_alert (alert_no,inventory_id,alert_type,level,message,suggestion,status) VALUES (?,?,?,?,?,?,?)", no("ALT"), inventoryId, type, level, msg, suggestion, "open");
    }

    private Long ensureLocation(String code) {
        String c = blank(code) ? "A-01-01" : code.trim();
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id FROM warehouse_location WHERE location_code=?", c);
        if (!rows.isEmpty()) return ((Number)rows.get(0).get("id")).longValue();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> { PreparedStatement ps = con.prepareStatement("INSERT INTO warehouse_location (location_code,name,zone,capacity) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1,c); ps.setString(2,"库位"+c); ps.setString(3,c.substring(0,1)); ps.setBigDecimal(4,new BigDecimal("9999")); return ps; }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private Long ensureInventory(InboundRequest req, Long locationId) {
        String code = blank(req.itemCode) ? req.itemName : req.itemCode.trim();
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id FROM warehouse_inventory WHERE item_code=? AND location_id=?", code, locationId);
        Long productId = productCatalogId(code);
        if (!rows.isEmpty()) {
            Long id = ((Number)rows.get(0).get("id")).longValue();
            if (productId != null) jdbc.update("UPDATE warehouse_inventory SET item_id=? WHERE id=? AND item_id IS NULL", productId, id);
            return id;
        }
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> { PreparedStatement ps = con.prepareStatement("INSERT INTO warehouse_inventory (item_type,item_id,item_code,item_name,spec,unit,location_id,stock_qty,locked_qty,available_qty,safety_stock,max_stock) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1,nvl(req.itemType,"SKU")); if(productId==null) ps.setObject(2,null); else ps.setLong(2,productId); ps.setString(3,code); ps.setString(4,req.itemName); ps.setString(5,req.spec); ps.setString(6,nvl(req.unit,"件")); ps.setLong(7,locationId); ps.setBigDecimal(8,BigDecimal.ZERO); ps.setBigDecimal(9,BigDecimal.ZERO); ps.setBigDecimal(10,BigDecimal.ZERO); ps.setBigDecimal(11,req.safetyStock==null?new BigDecimal("20"):req.safetyStock); ps.setBigDecimal(12,req.maxStock==null?new BigDecimal("9999"):req.maxStock); return ps; }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private void enrichInboundFromCatalog(InboundRequest req) {
        if (blank(req.itemCode)) return;
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id, product_name productName, product_code productCode, primary_category primaryCategory, secondary_category secondaryCategory, spec_description specDescription, cold_category coldCategory, product_cost_unit_price productCostUnitPrice, company_cost_price companyCostPrice, location_name locationName FROM warehouse_product_catalog WHERE product_code=? AND enabled=1", req.itemCode.trim());
        if (rows.isEmpty()) return;
        Map<String,Object> p = rows.get(0);
        if (blank(req.itemName)) req.itemName = String.valueOf(p.get("productName"));
        if (blank(req.spec)) req.spec = nvl((String)p.get("specDescription"), nvl((String)p.get("coldCategory"), (String)p.get("secondaryCategory")));
        if (blank(req.itemType)) req.itemType = "SKU";
        if (blank(req.unit)) req.unit = "件";
        if (req.unitCost == null) req.unitCost = bd(p.get("productCostUnitPrice")).compareTo(BigDecimal.ZERO) > 0 ? bd(p.get("productCostUnitPrice")) : bd(p.get("companyCostPrice"));
        if (blank(req.locationCode) && !blank((String)p.get("locationName"))) req.locationCode = (String)p.get("locationName");
    }

    private Long productCatalogId(String code) {
        if (blank(code)) return null;
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id FROM warehouse_product_catalog WHERE product_code=? AND enabled=1", code.trim());
        return rows.isEmpty() ? null : ((Number)rows.get(0).get("id")).longValue();
    }

    private long qLong(String sql) { Long v = jdbc.queryForObject(sql, Long.class); return v==null?0L:v; }
    private BigDecimal qDecimal(String sql) { BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class); return v==null?BigDecimal.ZERO:v; }
    private BigDecimal bd(Object o) { if (o == null) return BigDecimal.ZERO; if (o instanceof BigDecimal b) return b; return new BigDecimal(String.valueOf(o)); }
    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private String nvl(String s, String d) { return blank(s) ? d : s; }
    private String no(String p) { return p + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int)(Math.random()*900+100); }

    public static class InboundRequest { public String itemType; public String itemCode; public String itemName; public String spec; public String unit; public BigDecimal qty; public BigDecimal unitCost; public String locationCode; public BigDecimal safetyStock; public BigDecimal maxStock; public String sourceType; public String supplier; public String operator; public String remark; }
    public static class OutboundRequest { public Long inventoryId; public BigDecimal qty; public String orderNo; public String purpose; public String receiver; public String operator; }
}
