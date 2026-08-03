package com.example.shixun.controller;

import com.example.shixun.service.SiliconFlowChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/mvp")
public class CommercialMvpController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SiliconFlowChatService siliconFlow;

    public CommercialMvpController(JdbcTemplate jdbc, ObjectMapper mapper, SiliconFlowChatService siliconFlow) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.siliconFlow = siliconFlow;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "customerCount", count("creative_customer"),
                "inquiryCount", count("customer_inquiry"),
                "quoteCount", count("inquiry_quote"),
                "bomCount", count("product_bom"),
                "sampleCount", count("sample_order"),
                "quoteAmount", decimal("SELECT COALESCE(SUM(total_quote),0) FROM inquiry_quote WHERE status <> 'rejected'")
        );
    }

    @GetMapping("/customers")
    public List<Map<String, Object>> customers(@RequestParam(required = false) String keyword) {
        if (keyword != null && !keyword.isBlank()) {
            String k = "%" + keyword.trim() + "%";
            return jdbc.queryForList("SELECT id, customer_no customerNo, name, contact_name contactName, phone, wechat, company, industry, source, notes, status, created_at createdAt FROM creative_customer WHERE name LIKE ? OR contact_name LIKE ? OR company LIKE ? ORDER BY id DESC", k, k, k);
        }
        return jdbc.queryForList("SELECT id, customer_no customerNo, name, contact_name contactName, phone, wechat, company, industry, source, notes, status, created_at createdAt FROM creative_customer ORDER BY id DESC LIMIT 100");
    }

    @PostMapping("/customers")
    public Map<String, Object> createCustomer(@RequestBody CustomerRequest req) {
        if (blank(req.name)) throw new IllegalArgumentException("客户名称不能为空");
        String no = no("CUS");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO creative_customer (customer_no,name,contact_name,phone,wechat,company,industry,source,notes) VALUES (?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, no); ps.setString(2, req.name); ps.setString(3, req.contactName); ps.setString(4, req.phone); ps.setString(5, req.wechat); ps.setString(6, req.company); ps.setString(7, req.industry); ps.setString(8, req.source); ps.setString(9, req.notes);
            return ps;
        }, kh);
        return customer(Objects.requireNonNull(kh.getKey()).longValue());
    }

    @GetMapping("/inquiries")
    public List<Map<String, Object>> inquiries(@RequestParam(required = false) String status, @RequestParam(required = false) String keyword) {
        StringBuilder sql = new StringBuilder("SELECT i.id, i.inquiry_no inquiryNo, i.customer_id customerId, c.name customerName, c.contact_name contactName, i.title, i.product_name productName, i.product_type productType, i.quantity, i.size, i.material, i.packaging, i.destination, i.deadline, i.budget, i.usage_scenario usageScenario, i.raw_requirement rawRequirement, i.status, i.created_at createdAt FROM customer_inquiry i LEFT JOIN creative_customer c ON i.customer_id=c.id WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (!blank(status)) { sql.append(" AND i.status=?"); args.add(status); }
        if (!blank(keyword)) { sql.append(" AND (i.title LIKE ? OR i.product_name LIKE ? OR c.name LIKE ? OR i.raw_requirement LIKE ?)"); String k="%"+keyword.trim()+"%"; args.add(k); args.add(k); args.add(k); args.add(k); }
        sql.append(" ORDER BY i.id DESC LIMIT 200");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @GetMapping("/inquiries/{id}")
    public Map<String, Object> inquiry(@PathVariable Long id) {
        Map<String, Object> it = jdbc.queryForMap("SELECT i.id, i.inquiry_no inquiryNo, i.customer_id customerId, c.name customerName, c.contact_name contactName, c.phone, c.wechat, i.title, i.product_name productName, i.product_type productType, i.quantity, i.size, i.material, i.packaging, i.destination, i.deadline, i.budget, i.usage_scenario usageScenario, i.raw_requirement rawRequirement, i.status, i.ai_parse_json aiParseJson, i.created_at createdAt FROM customer_inquiry i LEFT JOIN creative_customer c ON i.customer_id=c.id WHERE i.id=?", id);
        it.put("quotes", quotesByInquiry(id));
        it.put("links", inquiryLinks("l.inquiry_id=?", id));
        return it;
    }

    @PostMapping("/inquiries")
    public Map<String, Object> createInquiry(@RequestBody InquiryRequest req) throws Exception {
        Long customerId = req.customerId;
        if (customerId == null && !blank(req.customerName)) {
            CustomerRequest c = new CustomerRequest(); c.name = req.customerName; c.contactName = req.contactName; c.phone = req.phone; c.wechat = req.wechat; c.company = req.company; c.source = "询盘创建";
            customerId = ((Number) createCustomer(c).get("id")).longValue();
        }
        if (blank(req.title)) req.title = blank(req.productName) ? "新客户询盘" : req.productName + "询盘";
        if (blank(req.productName)) req.productName = req.title;
        if (blank(req.productType)) req.productType = "文创定制品";
        if (req.quantity == null || req.quantity <= 0) req.quantity = 100;
        String no = no("INQ");
        String aiParse = tryAiParse(req);
        KeyHolder kh = new GeneratedKeyHolder();
        Long finalCustomerId = customerId;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO customer_inquiry (inquiry_no,customer_id,title,product_name,product_type,quantity,size,material,packaging,destination,deadline,budget,usage_scenario,raw_requirement,ai_parse_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, no); if (finalCustomerId == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, finalCustomerId); ps.setString(3, req.title); ps.setString(4, req.productName); ps.setString(5, req.productType); ps.setInt(6, req.quantity); ps.setString(7, req.size); ps.setString(8, req.material); ps.setString(9, req.packaging); ps.setString(10, req.destination); ps.setObject(11, req.deadline); ps.setString(12, req.budget); ps.setString(13, req.usageScenario); ps.setString(14, req.rawRequirement); ps.setString(15, aiParse);
            return ps;
        }, kh);
        return inquiry(Objects.requireNonNull(kh.getKey()).longValue());
    }

    @PostMapping("/inquiries/{id}/quote")
    public Map<String, Object> quoteInquiry(@PathVariable Long id, @RequestBody(required = false) QuoteOptions opt) throws Exception {
        Map<String, Object> inq = inquiry(id);
        int qty = number(inq.get("quantity"), 100);
        BigDecimal targetMargin = ratio(opt == null ? null : opt.targetMarginRate, new BigDecimal("0.45"));
        BigDecimal overhead = ratio(opt == null ? null : opt.overheadRate, new BigDecimal("0.12"));
        ProductCost cost = costByType(str(inq.get("productType")));
        BigDecimal logistics = logisticsEstimate(str(inq.get("destination")), qty, str(inq.get("productType")));
        BigDecimal designFee = money(opt == null || opt.designFee == null ? new BigDecimal("300") : opt.designFee);
        BigDecimal setupFee = setupFee(str(inq.get("productType")));
        BigDecimal directUnit = cost.material.add(cost.process).add(cost.packageCost).add(logistics.divide(BigDecimal.valueOf(qty), 4, RoundingMode.HALF_UP)).add(designFee.add(setupFee).divide(BigDecimal.valueOf(qty), 4, RoundingMode.HALF_UP));
        BigDecimal unitCost = directUnit.multiply(BigDecimal.ONE.add(overhead)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal suggested = unitCost.divide(BigDecimal.ONE.subtract(targetMargin), 2, RoundingMode.HALF_UP);
        BigDecimal floor = unitCost.divide(new BigDecimal("0.75"), 2, RoundingMode.HALF_UP);
        BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalQuote = suggested.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        String lead = leadTime(str(inq.get("productType")), qty);
        String customerReply = buildQuoteReply(inq, suggested, totalQuote, lead);
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("materialUnit", cost.material); breakdown.put("processUnit", cost.process); breakdown.put("packageUnit", cost.packageCost); breakdown.put("logisticsTotal", logistics); breakdown.put("designFee", designFee); breakdown.put("setupFee", setupFee); breakdown.put("overheadRate", overhead); breakdown.put("targetMarginRate", targetMargin);
        String aiDraft = tryAi("你是文创定制SaaS报价顾问。基于系统成本测算，输出正式报价建议、客户沟通话术、风险提醒和下一步确认事项。不要修改系统给出的金额。", Map.of("inquiry", inq, "quote", Map.of("unitCost", unitCost, "suggestedUnitPrice", suggested, "totalQuote", totalQuote, "leadTime", lead, "costBreakdown", breakdown)).toString());
        String no = no("QUO");
        String breakdownJson = mapper.writeValueAsString(breakdown);
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO inquiry_quote (quote_no,inquiry_id,customer_id,product_name,product_type,quantity,unit_cost,suggested_unit_price,floor_unit_price,total_cost,total_quote,gross_margin_rate,lead_time,customer_reply,ai_draft,cost_breakdown_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, no); ps.setLong(2, id); if (inq.get("customerId") == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, ((Number) inq.get("customerId")).longValue()); ps.setString(4, str(inq.get("productName"))); ps.setString(5, str(inq.get("productType"))); ps.setInt(6, qty); ps.setBigDecimal(7, unitCost); ps.setBigDecimal(8, suggested); ps.setBigDecimal(9, floor); ps.setBigDecimal(10, totalCost); ps.setBigDecimal(11, totalQuote); ps.setBigDecimal(12, targetMargin); ps.setString(13, lead); ps.setString(14, customerReply); ps.setString(15, aiDraft); ps.setString(16, breakdownJson);
            return ps;
        }, kh);
        jdbc.update("UPDATE customer_inquiry SET status='quoted' WHERE id=?", id);
        return quote(Objects.requireNonNull(kh.getKey()).longValue());
    }

    @GetMapping("/quotes")
    public List<Map<String, Object>> quotes() { return jdbc.queryForList("SELECT q.id, q.quote_no quoteNo, q.inquiry_id inquiryId, i.inquiry_no inquiryNo, c.name customerName, q.product_name productName, q.product_type productType, q.quantity, q.unit_cost unitCost, q.suggested_unit_price suggestedUnitPrice, q.floor_unit_price floorUnitPrice, q.total_cost totalCost, q.total_quote totalQuote, q.gross_margin_rate grossMarginRate, q.lead_time leadTime, q.status, q.created_at createdAt FROM inquiry_quote q JOIN customer_inquiry i ON q.inquiry_id=i.id LEFT JOIN creative_customer c ON q.customer_id=c.id ORDER BY q.id DESC LIMIT 200"); }

    @GetMapping("/quotes/{id}")
    public Map<String, Object> quote(@PathVariable Long id) {
        Map<String, Object> q = jdbc.queryForMap("SELECT q.id, q.quote_no quoteNo, q.inquiry_id inquiryId, i.inquiry_no inquiryNo, q.customer_id customerId, c.name customerName, c.contact_name contactName, c.phone, c.wechat, c.company, q.product_name productName, q.product_type productType, q.quantity, q.unit_cost unitCost, q.suggested_unit_price suggestedUnitPrice, q.floor_unit_price floorUnitPrice, q.total_cost totalCost, q.total_quote totalQuote, q.gross_margin_rate grossMarginRate, q.lead_time leadTime, q.customer_reply customerReply, q.ai_draft aiDraft, q.cost_breakdown_json costBreakdownJson, q.status, q.created_at createdAt FROM inquiry_quote q JOIN customer_inquiry i ON q.inquiry_id=i.id LEFT JOIN creative_customer c ON q.customer_id=c.id WHERE q.id=?", id);
        q.put("items", List.of(Map.of("name", q.get("productName"), "type", q.get("productType"), "quantity", q.get("quantity"), "unitPrice", q.get("suggestedUnitPrice"), "subtotal", q.get("totalQuote"))));
        q.put("terms", List.of("报价有效期7天，以最终设计稿、尺寸、材质、包装和收货地址确认为准。", "批量订单建议预付30%-50%定金，余款发货前结清。", "AI报价为系统测算，正式对外发送前建议人工复核。"));
        q.put("links", inquiryLinks("l.quote_id=?", id));
        return q;
    }

    @PostMapping("/quotes/{id}/status")
    public Map<String, Object> updateQuoteStatus(@PathVariable Long id, @RequestBody Map<String, String> body) { jdbc.update("UPDATE inquiry_quote SET status=? WHERE id=?", body.getOrDefault("status", "sent"), id); return quote(id); }

    @PostMapping("/quotes/{id}/bom-sample")
    public Map<String, Object> createBomAndSample(@PathVariable Long id) {
        Map<String, Object> q = quote(id);
        Long bomId = createBom(str(q.get("productName")), number(q.get("quantity"), 100), bd(q.get("suggestedUnitPrice")), str(q.get("productType")));
        BigDecimal sampleCost = bd(q.get("unitCost")).multiply(new BigDecimal("3")).setScale(2, RoundingMode.HALF_UP);
        String sampleNo = no("SMP");
        KeyHolder skh = new GeneratedKeyHolder();
        jdbc.update(con -> { PreparedStatement ps = con.prepareStatement("INSERT INTO sample_order (sample_no,bom_id,sample_qty,status,estimated_cost,due_date,feedback) VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1, sampleNo); ps.setLong(2, bomId); ps.setInt(3, 3); ps.setString(4, "pending"); ps.setBigDecimal(5, sampleCost); ps.setObject(6, LocalDate.now().plusDays(5)); ps.setString(7, "由商业MVP询盘报价自动创建打样单"); return ps; }, skh);
        Long sampleId = Objects.requireNonNull(skh.getKey()).longValue();
        jdbc.update("INSERT INTO inquiry_bom_link (inquiry_id,quote_id,bom_id,sample_id) VALUES (?,?,?,?)", q.get("inquiryId"), id, bomId, sampleId);
        jdbc.update("UPDATE customer_inquiry SET status='sample' WHERE id=?", q.get("inquiryId"));
        return Map.of("bomId", bomId, "sampleId", sampleId, "sampleNo", sampleNo, "message", "已生成BOM和打样单，下一步可创建生产单");
    }

    @PostMapping("/quotes/{id}/production")
    public Map<String, Object> createProductionFromQuote(@PathVariable Long id) {
        Map<String, Object> q = quote(id);
        Map<String, Object> link = latestLink(id);
        if (link == null || link.get("bomId") == null) {
            createBomAndSample(id);
            link = latestLink(id);
        }
        Long bomId = ((Number) link.get("bomId")).longValue();
        int qty = number(q.get("quantity"), 100);
        BigDecimal estimated = bd(q.get("unitCost")).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        String productionNo = no("MFG");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO production_order (production_no,bom_id,planned_qty,status,estimated_cost,start_date,due_date) VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, productionNo); ps.setLong(2, bomId); ps.setInt(3, qty); ps.setString(4, "planned"); ps.setBigDecimal(5, estimated); ps.setObject(6, LocalDate.now()); ps.setObject(7, LocalDate.now().plusDays(qty > 1000 ? 15 : 10)); return ps;
        }, kh);
        Long productionId = Objects.requireNonNull(kh.getKey()).longValue();
        generatePurchaseSuggestions(productionId, bomId, qty);
        jdbc.update("UPDATE inquiry_bom_link SET production_order_id=? WHERE id=?", productionId, link.get("id"));
        jdbc.update("UPDATE customer_inquiry SET status='production' WHERE id=?", q.get("inquiryId"));
        return Map.of("productionId", productionId, "productionNo", productionNo, "estimatedCost", estimated, "message", "已创建生产单，下一步生产完成后入库");
    }

    @PostMapping("/quotes/{id}/warehouse-inbound")
    public Map<String, Object> inboundFromQuote(@PathVariable Long id) {
        Map<String, Object> q = quote(id);
        Map<String, Object> link = latestLink(id);
        if (link == null || link.get("productionOrderId") == null) throw new IllegalStateException("请先创建生产单");
        Long productionId = ((Number) link.get("productionOrderId")).longValue();
        int qty = number(q.get("quantity"), 100);
        Long locationId = ensureWarehouseLocation("A-01-01");
        String itemCode = "Q-" + q.get("quoteNo");
        Long inventoryId = ensureWarehouseInventory(itemCode, str(q.get("productName")), str(q.get("productType")), "件", locationId);
        String inboundNo = no("IN");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO warehouse_inbound (inbound_no,source_type,supplier,operator,remark,status) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, inboundNo); ps.setString(2, "production"); ps.setString(3, "生产完工"); ps.setString(4, "系统自动"); ps.setString(5, "由询盘报价闭环自动入库"); ps.setString(6, "done"); return ps;
        }, kh);
        Long inboundId = Objects.requireNonNull(kh.getKey()).longValue();
        jdbc.update("INSERT INTO warehouse_inbound_item (inbound_id,inventory_id,item_name,qty,unit_cost,location_code) VALUES (?,?,?,?,?,?)", inboundId, inventoryId, q.get("productName"), BigDecimal.valueOf(qty), bd(q.get("unitCost")), "A-01-01");
        jdbc.update("UPDATE warehouse_inventory SET stock_qty=stock_qty+?, available_qty=available_qty+?, last_in_at=NOW() WHERE id=?", BigDecimal.valueOf(qty), BigDecimal.valueOf(qty), inventoryId);
        jdbc.update("UPDATE production_order SET status='completed', completed_qty=planned_qty WHERE id=?", productionId);
        jdbc.update("UPDATE inquiry_bom_link SET warehouse_inbound_id=? WHERE id=?", inboundId, link.get("id"));
        jdbc.update("UPDATE customer_inquiry SET status='won' WHERE id=?", q.get("inquiryId"));
        refreshWarehouseAlerts();
        return Map.of("inboundId", inboundId, "inboundNo", inboundNo, "inventoryId", inventoryId, "message", "生产完成并已入库，下一步可创建出库拣货");
    }

    @PostMapping("/quotes/{id}/warehouse-outbound")
    public Map<String, Object> outboundFromQuote(@PathVariable Long id) {
        Map<String, Object> q = quote(id);
        Map<String, Object> link = latestLink(id);
        if (link == null || link.get("warehouseInboundId") == null) throw new IllegalStateException("请先生产完工入库");
        String itemCode = "Q-" + q.get("quoteNo");
        Map<String,Object> inv = jdbc.queryForMap("SELECT id, item_name itemName, available_qty availableQty, location_id locationId FROM warehouse_inventory WHERE item_code=? ORDER BY id DESC LIMIT 1", itemCode);
        int qty = number(q.get("quantity"), 100);
        BigDecimal qtyBd = BigDecimal.valueOf(qty);
        if (bd(inv.get("availableQty")).compareTo(qtyBd) < 0) throw new IllegalStateException("库存不足，无法整单出库");
        String locationCode = jdbc.queryForObject("SELECT location_code FROM warehouse_location WHERE id=?", String.class, inv.get("locationId"));
        String outboundNo = no("OUT");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO warehouse_outbound (outbound_no,order_no,purpose,receiver,operator,status) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, outboundNo); ps.setString(2, str(q.get("quoteNo"))); ps.setString(3, "客户定制单发货"); ps.setString(4, str(q.get("customerName"))); ps.setString(5, "系统自动"); ps.setString(6, "picking"); return ps;
        }, kh);
        Long outboundId = Objects.requireNonNull(kh.getKey()).longValue();
        Long inventoryId = ((Number) inv.get("id")).longValue();
        jdbc.update("INSERT INTO warehouse_outbound_item (outbound_id,inventory_id,item_name,qty,location_code) VALUES (?,?,?,?,?)", outboundId, inventoryId, inv.get("itemName"), qtyBd, locationCode);
        jdbc.update("UPDATE warehouse_inventory SET locked_qty=locked_qty+?, available_qty=available_qty-? WHERE id=?", qtyBd, qtyBd, inventoryId);
        String pickNo = no("PICK");
        jdbc.update("INSERT INTO warehouse_pick_task (pick_no,outbound_id,inventory_id,item_name,qty,location_code,status,operator) VALUES (?,?,?,?,?,?,?,?)", pickNo, outboundId, inventoryId, inv.get("itemName"), qtyBd, locationCode, "pending", "仓库员");
        jdbc.update("UPDATE inquiry_bom_link SET warehouse_outbound_id=? WHERE id=?", outboundId, link.get("id"));
        refreshWarehouseAlerts();
        return Map.of("outboundId", outboundId, "outboundNo", outboundNo, "pickNo", pickNo, "message", "已创建出库单和拣货任务，拣货完成后请到物流跟踪录入真实快递单号");
    }

    @GetMapping("/cost-library")
    public Map<String, Object> costLibrary() { return Map.of("materials", jdbc.queryForList("SELECT id, material_code materialCode, name, category, unit, standard_cost standardCost, stock_qty stockQty, safety_stock safetyStock, supplier, lead_time_days leadTimeDays FROM material WHERE enabled=1 ORDER BY id"), "processes", jdbc.queryForList("SELECT id, process_code processCode, name, category, unit, standard_cost standardCost, standard_hours standardHours, loss_rate lossRate, description FROM craft_process WHERE enabled=1 ORDER BY id"), "productTypes", List.of("亚克力钥匙扣", "冰箱贴", "徽章", "贴纸包", "明信片套装", "帆布袋", "礼盒")); }

    @PostMapping("/design-plan")
    public Map<String, Object> designPlan(@RequestBody DesignPlanRequest req) { if (blank(req.theme)) req.theme = "城市味道文创"; if (blank(req.audience)) req.audience = "年轻游客、企业礼品客户、文创爱好者"; String prompt = "主题：" + req.theme + "\n目标人群：" + req.audience + "\n产品类型：" + req.productTypes + "\n预算/价格带：" + req.budget + "\n请输出：产品定位、3个系列方向、SKU建议、视觉风格、打样建议、报价注意事项、版权风险。"; String ai = tryAi("你是文创新品企划总监，帮助商家把客户需求转成可报价、可设计、可打样的方案。", prompt); return Map.of("planNo", no("PLN"), "source", "siliconflow:" + siliconFlow.modelName(), "aiDraft", ai, "theme", req.theme); }

    @PostMapping("/design-review")
    public Map<String, Object> designReview(@RequestBody DesignReviewRequest req) { String prompt = "设计方案/Prompt：" + nullToEmpty(req.concept) + "\n目标SKU：" + nullToEmpty(req.productType) + "\n目标人群：" + nullToEmpty(req.audience) + "\n请按设计完成度、市场卖点、成本可行性、版权风险、打样风险评分，并给出是否进入打样。"; String ai = tryAi("你是文创AI评审团，必须严格指出风险并给出可执行优化建议。", prompt); return Map.of("reviewNo", no("REV"), "source", "siliconflow:" + siliconFlow.modelName(), "aiDraft", ai); }


    private List<Map<String, Object>> inquiryLinks(String where, Object arg) {
        return jdbc.queryForList("SELECT l.id, l.quote_id quoteId, l.bom_id bomId, b.bom_no bomNo, b.product_name productName, l.sample_id sampleId, s.sample_no sampleNo, s.status sampleStatus, l.production_order_id productionOrderId, p.production_no productionNo, p.status productionStatus, l.warehouse_inbound_id warehouseInboundId, wi.inbound_no warehouseInboundNo, l.warehouse_outbound_id warehouseOutboundId, wo.outbound_no warehouseOutboundNo, wo.status warehouseOutboundStatus, l.created_at createdAt FROM inquiry_bom_link l LEFT JOIN product_bom b ON l.bom_id=b.id LEFT JOIN sample_order s ON l.sample_id=s.id LEFT JOIN production_order p ON l.production_order_id=p.id LEFT JOIN warehouse_inbound wi ON l.warehouse_inbound_id=wi.id LEFT JOIN warehouse_outbound wo ON l.warehouse_outbound_id=wo.id WHERE " + where + " ORDER BY l.id DESC", arg);
    }
    private Map<String, Object> latestLink(Long quoteId) {
        List<Map<String,Object>> rows = inquiryLinks("l.quote_id=?", quoteId);
        return rows.isEmpty() ? null : rows.get(0);
    }
    private List<Map<String, Object>> materialItems(Long bomId) {
        return jdbc.queryForList("SELECT i.material_id materialId, m.stock_qty stockQty, i.qty, i.loss_rate lossRate FROM bom_material_item i JOIN material m ON i.material_id=m.id WHERE i.bom_id=?", bomId);
    }
    private void generatePurchaseSuggestions(Long poId, Long bomId, int qty) {
        for (Map<String,Object> it : materialItems(bomId)) {
            BigDecimal required = bd(it.get("qty")).multiply(BigDecimal.ONE.add(bd(it.get("lossRate")))).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal stock = bd(it.get("stockQty"));
            BigDecimal suggest = required.subtract(stock).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (suggest.compareTo(BigDecimal.ZERO) > 0) jdbc.update("INSERT INTO purchase_suggestion (production_order_id,material_id,required_qty,stock_qty,suggest_purchase_qty) VALUES (?,?,?,?,?)", poId, it.get("materialId"), required, stock, suggest);
        }
    }
    private Long ensureWarehouseLocation(String code) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id FROM warehouse_location WHERE location_code=?", code);
        if (!rows.isEmpty()) return ((Number)rows.get(0).get("id")).longValue();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> { PreparedStatement ps = con.prepareStatement("INSERT INTO warehouse_location (location_code,name,zone,capacity) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1, code); ps.setString(2, "默认发货库位"); ps.setString(3, "A"); ps.setBigDecimal(4, new BigDecimal("9999")); return ps; }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }
    private Long ensureWarehouseInventory(String code, String name, String spec, String unit, Long locationId) {
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id FROM warehouse_inventory WHERE item_code=? AND location_id=?", code, locationId);
        if (!rows.isEmpty()) return ((Number)rows.get(0).get("id")).longValue();
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> { PreparedStatement ps = con.prepareStatement("INSERT INTO warehouse_inventory (item_type,item_code,item_name,spec,unit,location_id,stock_qty,locked_qty,available_qty,safety_stock,max_stock) VALUES (?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1,"SKU"); ps.setString(2,code); ps.setString(3,name); ps.setString(4,spec); ps.setString(5,unit); ps.setLong(6,locationId); ps.setBigDecimal(7,BigDecimal.ZERO); ps.setBigDecimal(8,BigDecimal.ZERO); ps.setBigDecimal(9,BigDecimal.ZERO); ps.setBigDecimal(10,new BigDecimal("10")); ps.setBigDecimal(11,new BigDecimal("99999")); return ps; }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }
    private void refreshWarehouseAlerts() {
        jdbc.update("UPDATE warehouse_alert SET status='closed' WHERE status='open'");
        List<Map<String,Object>> invs = jdbc.queryForList("SELECT id,stock_qty stockQty,available_qty availableQty,safety_stock safetyStock,max_stock maxStock FROM warehouse_inventory");
        for (Map<String,Object> i : invs) {
            Long id = ((Number)i.get("id")).longValue(); BigDecimal stock=bd(i.get("stockQty")); BigDecimal available=bd(i.get("availableQty")); BigDecimal safety=bd(i.get("safetyStock")); BigDecimal max=bd(i.get("maxStock"));
            if (available.compareTo(BigDecimal.ZERO)<=0) jdbc.update("INSERT INTO warehouse_alert (alert_no,inventory_id,alert_type,level,message,suggestion,status) VALUES (?,?,?,?,?,?,?)", no("ALT"), id, "out_of_stock", "critical", "库存缺货", "可用库存为0，请立即补货或暂停接单", "open");
            else if (available.compareTo(safety)<=0) jdbc.update("INSERT INTO warehouse_alert (alert_no,inventory_id,alert_type,level,message,suggestion,status) VALUES (?,?,?,?,?,?,?)", no("ALT"), id, "low_stock", "warning", "低库存预警", "可用库存低于安全库存，建议发起采购/生产补货", "open");
            if (max.compareTo(BigDecimal.ZERO)>0 && stock.compareTo(max)>0) jdbc.update("INSERT INTO warehouse_alert (alert_no,inventory_id,alert_type,level,message,suggestion,status) VALUES (?,?,?,?,?,?,?)", no("ALT"), id, "over_stock", "info", "库存偏高", "库存超过上限，建议促销或控制入库节奏", "open");
        }
    }

    private Map<String, Object> customer(Long id) { return jdbc.queryForMap("SELECT id, customer_no customerNo, name, contact_name contactName, phone, wechat, company, industry, source, notes, status, created_at createdAt FROM creative_customer WHERE id=?", id); }
    private List<Map<String, Object>> quotesByInquiry(Long id) { return jdbc.queryForList("SELECT id, quote_no quoteNo, product_name productName, quantity, suggested_unit_price suggestedUnitPrice, total_quote totalQuote, status, created_at createdAt FROM inquiry_quote WHERE inquiry_id=? ORDER BY id DESC", id); }
    private String tryAiParse(InquiryRequest req) { try { String raw = "客户原始需求：" + nullToEmpty(req.rawRequirement) + "\n已填字段：产品=" + req.productName + ", 类型=" + req.productType + ", 数量=" + req.quantity + ", 尺寸=" + req.size + ", 材质=" + req.material + ", 包装=" + req.packaging + ", 地址=" + req.destination; return mapper.writeValueAsString(Map.of("source", "siliconflow:" + siliconFlow.modelName(), "content", tryAi("你是文创询盘分析助手，请提取客户需求、缺失信息、报价风险。", raw))); } catch (Exception e) { return null; } }
    private String tryAi(String system, String user) { try { return siliconFlow.chat(system, user, 0.55, 1200, 90); } catch (Exception e) { return "AI调用失败，已使用系统规则完成基础流程。原因：" + e.getMessage(); } }

    private Long createBom(String name, int qty, BigDecimal targetPrice, String productType) { String bomNo = no("BOM"); KeyHolder kh = new GeneratedKeyHolder(); jdbc.update(con -> { PreparedStatement ps = con.prepareStatement("INSERT INTO product_bom (bom_no,product_name,planned_qty,target_price,status,remark) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1, bomNo); ps.setString(2, name); ps.setInt(3, qty); ps.setBigDecimal(4, targetPrice); ps.setString(5, "draft"); ps.setString(6, "商业MVP自动生成BOM"); return ps; }, kh); Long bomId = Objects.requireNonNull(kh.getKey()).longValue(); String t = productType == null ? "" : productType; if (t.contains("帆布")) { addMaterial(bomId, 3L, BigDecimal.ONE, new BigDecimal("0.03")); addMaterial(bomId, 4L, BigDecimal.ONE, new BigDecimal("0.01")); addProcess(bomId, 4L, BigDecimal.ONE); addProcess(bomId, 5L, BigDecimal.ONE); } else if (t.contains("贴纸")) { addMaterial(bomId, 5L, new BigDecimal("2"), new BigDecimal("0.03")); addMaterial(bomId, 2L, new BigDecimal("2"), new BigDecimal("0.02")); addProcess(bomId, 1L, new BigDecimal("2")); addProcess(bomId, 3L, new BigDecimal("2")); addProcess(bomId, 5L, BigDecimal.ONE); } else { addMaterial(bomId, 1L, new BigDecimal("8"), new BigDecimal("0.03")); addMaterial(bomId, 2L, new BigDecimal("8"), new BigDecimal("0.02")); addMaterial(bomId, 4L, BigDecimal.ONE, new BigDecimal("0.01")); addProcess(bomId, 1L, new BigDecimal("8")); addProcess(bomId, 2L, new BigDecimal("8")); addProcess(bomId, 5L, BigDecimal.ONE); } return bomId; }
    private void addMaterial(Long bomId, Long materialId, BigDecimal qty, BigDecimal loss) { BigDecimal unit = jdbc.queryForObject("SELECT standard_cost FROM material WHERE id=?", BigDecimal.class, materialId); jdbc.update("INSERT INTO bom_material_item (bom_id,material_id,qty,loss_rate,unit_cost) VALUES (?,?,?,?,?)", bomId, materialId, qty, loss, unit); }
    private void addProcess(Long bomId, Long processId, BigDecimal qty) { Map<String,Object> p = jdbc.queryForMap("SELECT standard_cost, standard_hours FROM craft_process WHERE id=?", processId); jdbc.update("INSERT INTO bom_process_item (bom_id,process_id,qty,unit_cost,standard_hours) VALUES (?,?,?,?,?)", bomId, processId, qty, p.get("standard_cost"), p.get("standard_hours")); }
    private ProductCost costByType(String type) { String t = type == null ? "" : type.toLowerCase(Locale.ROOT); if (t.contains("canvas") || t.contains("帆布")) return new ProductCost(new BigDecimal("18.50"), new BigDecimal("7.10"), new BigDecimal("3.20")); if (t.contains("sticker") || t.contains("贴纸")) return new ProductCost(new BigDecimal("1.48"), new BigDecimal("1.80"), new BigDecimal("0.60")); if (t.contains("acrylic") || t.contains("亚克力") || t.contains("钥匙扣")) return new ProductCost(new BigDecimal("3.80"), new BigDecimal("2.40"), new BigDecimal("0.85")); if (t.contains("magnet") || t.contains("冰箱贴")) return new ProductCost(new BigDecimal("2.60"), new BigDecimal("1.80"), new BigDecimal("0.75")); if (t.contains("badge") || t.contains("徽章")) return new ProductCost(new BigDecimal("2.10"), new BigDecimal("1.50"), new BigDecimal("0.60")); return new ProductCost(new BigDecimal("8.32"), new BigDecimal("11.40"), new BigDecimal("3.23")); }
    private BigDecimal logisticsEstimate(String destination, int qty, String productType) { BigDecimal base = new BigDecimal("8"); String d = destination == null ? "" : destination; if (d.contains("新疆") || d.contains("西藏") || d.contains("海南")) base = new BigDecimal("18"); else if (d.contains("江浙沪") || d.contains("同城") || d.contains("杭州") || d.contains("上海") || d.contains("苏州")) base = new BigDecimal("6"); BigDecimal factor = productType != null && (productType.contains("帆布") || productType.toLowerCase(Locale.ROOT).contains("canvas")) ? new BigDecimal("0.11") : new BigDecimal("0.035"); return base.add(BigDecimal.valueOf(qty).multiply(factor)).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal setupFee(String type) { String t = type == null ? "" : type.toLowerCase(Locale.ROOT); if (t.contains("acrylic") || t.contains("亚克力") || t.contains("钥匙扣") || t.contains("徽章")) return new BigDecimal("200"); if (t.contains("gift") || t.contains("礼盒")) return new BigDecimal("300"); return new BigDecimal("80"); }
    private String leadTime(String type, int qty) { int days = qty > 1000 ? 12 : qty > 300 ? 8 : 5; String t = type == null ? "" : type; if (t.contains("亚克力") || t.contains("钥匙扣") || t.contains("徽章")) days += 2; return days + "-" + (days + 3) + "天，含确认稿、打样和批量生产；急单需单独确认。"; }
    private String buildQuoteReply(Map<String,Object> inq, BigDecimal price, BigDecimal total, String lead) { return "您好，按当前需求：" + str(inq.get("productName")) + "，数量" + inq.get("quantity") + "件，预估单价约¥" + price + "/件，总价约¥" + total + "。交期参考：" + lead + "。正式报价需最终确认设计稿、尺寸、材质、包装和收货地址。"; }
    private Long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private BigDecimal decimal(String sql) { BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class); return v == null ? BigDecimal.ZERO : v; }
    private BigDecimal ratio(BigDecimal value, BigDecimal fallback) { if (value == null) return fallback; if (value.compareTo(BigDecimal.ONE) > 0) return value.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP); return value; }
    private BigDecimal money(BigDecimal v) { return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal bd(Object o) { if (o == null) return BigDecimal.ZERO; if (o instanceof BigDecimal) return (BigDecimal)o; if (o instanceof Number) return BigDecimal.valueOf(((Number)o).doubleValue()); return new BigDecimal(String.valueOf(o)); }
    private int number(Object o, int fallback) { if (o instanceof Number) return ((Number)o).intValue(); try { return Integer.parseInt(String.valueOf(o)); } catch(Exception e) { return fallback; } }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private String nullToEmpty(String s) { return s == null ? "" : s; }
    private String no(String prefix) { return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int)(Math.random()*900+100); }
    private record ProductCost(BigDecimal material, BigDecimal process, BigDecimal packageCost) {}
    public static class CustomerRequest { public String name; public String contactName; public String phone; public String wechat; public String company; public String industry; public String source; public String notes; }
    public static class InquiryRequest { public Long customerId; public String customerName; public String contactName; public String phone; public String wechat; public String company; public String title; public String productName; public String productType; public Integer quantity; public String size; public String material; public String packaging; public String destination; public LocalDate deadline; public String budget; public String usageScenario; public String rawRequirement; }
    public static class QuoteOptions { public BigDecimal designFee; public BigDecimal overheadRate; public BigDecimal targetMarginRate; }
    public static class DesignPlanRequest { public String theme; public String audience; public String productTypes; public String budget; }
    public static class DesignReviewRequest { public String concept; public String productType; public String audience; }
}
