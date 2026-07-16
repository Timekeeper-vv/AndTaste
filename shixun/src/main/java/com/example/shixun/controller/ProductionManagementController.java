package com.example.shixun.controller;

import com.example.shixun.service.SiliconFlowChatService;
import com.fasterxml.jackson.databind.JsonNode;
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
@RequestMapping("/api/production")
@CrossOrigin(origins = "*")
public class ProductionManagementController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SiliconFlowChatService ai;

    public ProductionManagementController(JdbcTemplate jdbc, ObjectMapper mapper, SiliconFlowChatService ai) {
        this.jdbc = jdbc; this.mapper = mapper; this.ai = ai;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "bomCount", count("product_bom"),
                "materialCount", count("material"),
                "processCount", count("craft_process"),
                "sampleCount", count("sample_order"),
                "productionCount", count("production_order"),
                "pendingPurchaseCount", jdbc.queryForObject("SELECT COUNT(*) FROM purchase_suggestion WHERE status='pending'", Long.class)
        );
    }

    @GetMapping("/materials")
    public List<Map<String, Object>> materials() {
        return jdbc.queryForList("SELECT id, material_code materialCode, name, category, unit, standard_cost standardCost, stock_qty stockQty, safety_stock safetyStock, supplier, lead_time_days leadTimeDays FROM material WHERE enabled=1 ORDER BY id");
    }

    @GetMapping("/processes")
    public List<Map<String, Object>> processes() {
        return jdbc.queryForList("SELECT id, process_code processCode, name, category, unit, standard_cost standardCost, standard_hours standardHours, loss_rate lossRate, description FROM craft_process WHERE enabled=1 ORDER BY id");
    }

    @GetMapping("/boms")
    public List<Map<String, Object>> boms() {
        List<Map<String, Object>> list = jdbc.queryForList(
                "SELECT b.id, b.bom_no bomNo, b.sku_id skuId, b.asset_id assetId, b.product_name productName, b.version_no versionNo, b.planned_qty plannedQty, b.target_price targetPrice, b.status, b.remark, b.created_at createdAt " +
                "FROM product_bom b ORDER BY b.id DESC");
        for (Map<String, Object> b : list) enrichBom(b);
        return list;
    }

    @GetMapping("/boms/{id}")
    public Map<String, Object> bom(@PathVariable Long id) {
        Map<String, Object> b = jdbc.queryForMap("SELECT id, bom_no bomNo, sku_id skuId, asset_id assetId, product_name productName, version_no versionNo, planned_qty plannedQty, target_price targetPrice, status, remark, created_at createdAt FROM product_bom WHERE id=?", id);
        enrichBom(b);
        return b;
    }

    @PostMapping("/boms/auto")
    public Map<String, Object> autoBom(@RequestBody AutoBomRequest req) {
        String productType = req.productType == null ? "postcard" : req.productType;
        Long bomId = createBom(req.productName == null ? "AI自动BOM" : req.productName, req.skuId, req.assetId, req.plannedQty == null ? 100 : req.plannedQty, req.targetPrice == null ? BigDecimal.ZERO : req.targetPrice);
        if (productType.contains("canvas")) {
            addMaterial(bomId, 3L, BigDecimal.ONE, new BigDecimal("0.03"));
            addMaterial(bomId, 4L, BigDecimal.ONE, new BigDecimal("0.01"));
            addProcess(bomId, 4L, BigDecimal.ONE);
            addProcess(bomId, 5L, BigDecimal.ONE);
        } else if (productType.contains("sticker")) {
            addMaterial(bomId, 5L, new BigDecimal("2"), new BigDecimal("0.03"));
            addMaterial(bomId, 2L, new BigDecimal("2"), new BigDecimal("0.02"));
            addProcess(bomId, 1L, new BigDecimal("2"));
            addProcess(bomId, 3L, new BigDecimal("2"));
            addProcess(bomId, 5L, BigDecimal.ONE);
        } else {
            addMaterial(bomId, 1L, new BigDecimal("8"), new BigDecimal("0.03"));
            addMaterial(bomId, 2L, new BigDecimal("8"), new BigDecimal("0.02"));
            addMaterial(bomId, 4L, BigDecimal.ONE, new BigDecimal("0.01"));
            addProcess(bomId, 1L, new BigDecimal("8"));
            addProcess(bomId, 2L, new BigDecimal("8"));
            addProcess(bomId, 5L, BigDecimal.ONE);
        }
        return bom(bomId);
    }

    @PostMapping("/quotes/simulate")
    public Map<String, Object> simulate(@RequestBody QuoteRequest req) throws Exception {
        if (req.bomId == null) throw new IllegalArgumentException("bomId不能为空");
        int qty = req.quantity == null || req.quantity <= 0 ? 100 : req.quantity;
        BigDecimal overheadRate = req.overheadRate == null ? new BigDecimal("0.12") : req.overheadRate;
        BigDecimal targetMargin = req.targetMarginRate == null ? new BigDecimal("0.45") : req.targetMarginRate;

        List<Map<String, Object>> materialItems = materialItems(req.bomId);
        List<Map<String, Object>> processItems = processItems(req.bomId);
        BigDecimal materialUnit = BigDecimal.ZERO;
        BigDecimal packageUnit = BigDecimal.ZERO;
        for (Map<String, Object> it : materialItems) {
            BigDecimal qtyPer = bd(it.get("qty"));
            BigDecimal loss = bd(it.get("lossRate"));
            BigDecimal unit = bd(it.get("unitCost"));
            BigDecimal cost = qtyPer.multiply(BigDecimal.ONE.add(loss)).multiply(unit);
            if ("package".equals(String.valueOf(it.get("category")))) packageUnit = packageUnit.add(cost);
            else materialUnit = materialUnit.add(cost);
        }
        BigDecimal processUnit = BigDecimal.ZERO;
        for (Map<String, Object> it : processItems) {
            processUnit = processUnit.add(bd(it.get("qty")).multiply(bd(it.get("unitCost"))));
        }
        BigDecimal directUnit = materialUnit.add(processUnit).add(packageUnit);
        BigDecimal overheadUnit = directUnit.multiply(overheadRate);
        BigDecimal unitCost = directUnit.add(overheadUnit).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal suggestedPrice = unitCost.divide(BigDecimal.ONE.subtract(targetMargin), 2, RoundingMode.HALF_UP);
        BigDecimal margin = suggestedPrice.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : suggestedPrice.subtract(unitCost).divide(suggestedPrice, 4, RoundingMode.HALF_UP);

        String quoteNo = no("QUO");
        String simJson = mapper.writeValueAsString(Map.of("overheadRate", overheadRate, "targetMarginRate", targetMargin, "materialItems", materialItems, "processItems", processItems));
        jdbc.update("INSERT INTO cost_quote (quote_no,bom_id,quantity,material_cost,process_cost,packaging_cost,overhead_cost,total_cost,unit_cost,suggested_price,gross_margin_rate,simulation_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                quoteNo, req.bomId, qty,
                materialUnit.multiply(BigDecimal.valueOf(qty)), processUnit.multiply(BigDecimal.valueOf(qty)), packageUnit.multiply(BigDecimal.valueOf(qty)), overheadUnit.multiply(BigDecimal.valueOf(qty)), totalCost, unitCost, suggestedPrice, margin, simJson);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("quoteNo", quoteNo);
        result.put("bomId", req.bomId);
        result.put("quantity", qty);
        result.put("materialUnitCost", materialUnit.setScale(2, RoundingMode.HALF_UP));
        result.put("processUnitCost", processUnit.setScale(2, RoundingMode.HALF_UP));
        result.put("packagingUnitCost", packageUnit.setScale(2, RoundingMode.HALF_UP));
        result.put("overheadUnitCost", overheadUnit.setScale(2, RoundingMode.HALF_UP));
        result.put("unitCost", unitCost);
        result.put("totalCost", totalCost);
        result.put("suggestedPrice", suggestedPrice);
        result.put("grossMarginRate", margin);
        result.put("materialItems", materialItems);
        result.put("processItems", processItems);
        return result;
    }

    @GetMapping("/quotes")
    public List<Map<String, Object>> quotes() {
        return jdbc.queryForList("SELECT q.id, q.quote_no quoteNo, q.bom_id bomId, b.product_name productName, q.quantity, q.total_cost totalCost, q.unit_cost unitCost, q.suggested_price suggestedPrice, q.gross_margin_rate grossMarginRate, q.created_at createdAt FROM cost_quote q JOIN product_bom b ON q.bom_id=b.id ORDER BY q.id DESC LIMIT 50");
    }

    @PostMapping("/samples")
    public Map<String, Object> createSample(@RequestBody SampleRequest req) throws Exception {
        if (req.bomId == null) throw new IllegalArgumentException("bomId不能为空");
        Map<String, Object> quote = simulate(new QuoteRequest(req.bomId, req.sampleQty == null ? 1 : req.sampleQty, new BigDecimal("0.18"), new BigDecimal("0.35")));
        String sampleNo = no("SMP");
        jdbc.update("INSERT INTO sample_order (sample_no,bom_id,asset_id,sample_qty,status,estimated_cost,due_date,feedback) VALUES (?,?,?,?,?,?,?,?)",
                sampleNo, req.bomId, req.assetId, req.sampleQty == null ? 1 : req.sampleQty, "pending", quote.get("totalCost"), req.dueDate, req.feedback);
        return Map.of("sampleNo", sampleNo, "estimatedCost", quote.get("totalCost"), "status", "pending");
    }

    @GetMapping("/samples")
    public List<Map<String, Object>> samples() {
        return jdbc.queryForList("SELECT s.id, s.sample_no sampleNo, s.bom_id bomId, b.product_name productName, s.sample_qty sampleQty, s.status, s.estimated_cost estimatedCost, s.actual_cost actualCost, s.feedback, s.due_date dueDate, s.created_at createdAt FROM sample_order s JOIN product_bom b ON s.bom_id=b.id ORDER BY s.id DESC");
    }

    @PostMapping("/orders")
    public Map<String, Object> createProduction(@RequestBody ProductionRequest req) throws Exception {
        if (req.bomId == null) throw new IllegalArgumentException("bomId不能为空");
        int qty = req.plannedQty == null || req.plannedQty <= 0 ? 100 : req.plannedQty;
        Map<String, Object> quote = simulate(new QuoteRequest(req.bomId, qty, new BigDecimal("0.12"), new BigDecimal("0.45")));
        String productionNo = no("MFG");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO production_order (production_no,bom_id,planned_qty,status,estimated_cost,start_date,due_date) VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, productionNo); ps.setLong(2, req.bomId); ps.setInt(3, qty); ps.setString(4, "planned"); ps.setBigDecimal(5, bd(quote.get("totalCost"))); ps.setObject(6, req.startDate); ps.setObject(7, req.dueDate); return ps;
        }, kh);
        Long poId = Objects.requireNonNull(kh.getKey()).longValue();
        generatePurchaseSuggestions(poId, req.bomId, qty);
        return Map.of("productionNo", productionNo, "productionId", poId, "estimatedCost", quote.get("totalCost"), "status", "planned");
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> productionOrders() {
        return jdbc.queryForList("SELECT p.id, p.production_no productionNo, p.bom_id bomId, b.product_name productName, p.planned_qty plannedQty, p.completed_qty completedQty, p.status, p.estimated_cost estimatedCost, p.actual_material_cost actualMaterialCost, p.actual_labor_hours actualLaborHours, p.actual_loss_qty actualLossQty, p.start_date startDate, p.due_date dueDate, p.created_at createdAt FROM production_order p JOIN product_bom b ON p.bom_id=b.id ORDER BY p.id DESC");
    }

    @GetMapping("/workbench")
    public Map<String, Object> workbench() {
        List<Map<String, Object>> sources = jdbc.queryForList(
            "SELECT b.id bomId,b.bom_no bomNo,b.product_name productName,b.planned_qty plannedQty,b.target_price targetPrice,b.status bomStatus," +
            "ps.id projectSkuId,ps.sku_name skuName,ps.product_type productType,ps.status skuStatus,p.id projectId,p.project_no projectNo,p.name projectName," +
            "c.name customerName,c.contact_name contactName,c.phone contactPhone,i.destination receiverAddress " +
            "FROM product_bom b LEFT JOIN creative_project_sku ps ON ps.bom_id=b.id LEFT JOIN creative_project p ON ps.project_id=p.id " +
            "LEFT JOIN customer_inquiry i ON p.inquiry_id=i.id LEFT JOIN creative_customer c ON i.customer_id=c.id ORDER BY b.id DESC");
        for (Map<String,Object> source : sources) { Long bomId=((Number)source.get("bomId")).longValue(); source.put("materials",materialItems(bomId)); source.put("processes",processItems(bomId)); }
        List<Map<String,Object>> orderList = jdbc.queryForList(
            "SELECT o.id,o.order_no orderNo,o.order_type orderType,o.project_id projectId,p.project_no projectNo,p.name projectName," +
            "o.project_sku_id projectSkuId,o.bom_id bomId,b.bom_no bomNo,b.product_name productName,o.quote_id quoteId,q.quote_no quoteNo," +
            "o.sample_id sampleId,s.sample_no sampleNo,o.production_order_id productionOrderId,po.production_no productionNo," +
            "o.customer_name customerName,o.contact_name contactName,o.contact_phone contactPhone,o.receiver_address receiverAddress," +
            "o.quantity,o.unit_price unitPrice,o.total_amount totalAmount,o.status,o.confirmed_at confirmedAt,o.created_at createdAt," +
            "(SELECT COUNT(*) FROM logistics_shipment ls WHERE ls.order_no=o.order_no) shipmentCount " +
            "FROM commercial_order o JOIN product_bom b ON o.bom_id=b.id LEFT JOIN creative_project p ON o.project_id=p.id " +
            "LEFT JOIN cost_quote q ON o.quote_id=q.id LEFT JOIN sample_order s ON o.sample_id=s.id LEFT JOIN production_order po ON o.production_order_id=po.id ORDER BY o.id DESC");
        for (Map<String,Object> order : orderList) {
            Long orderId = ((Number) order.get("id")).longValue();
            order.put("confirmApproval", orderApproval(orderId, "confirm"));
            order.put("startApproval", orderApproval(orderId, "start"));
        }
        return Map.of("sources", sources, "orders", orderList);
    }

    @PostMapping("/boms/{id}/ai-plan")
    public Map<String,Object> aiPlan(@PathVariable Long id, @RequestBody Map<String,Object> body) throws Exception {
        Map<String,Object> bom=bom(id); int qty=body.get("quantity")==null?((Number)bom.get("plannedQty")).intValue():Integer.parseInt(String.valueOf(body.get("quantity")));
        String mode=String.valueOf(body.getOrDefault("mode","bulk")); String requirement=String.valueOf(body.getOrDefault("requirement",""));
        List<Map<String,Object>> ms=materials(), ps=processes();
        Map<String,Object> result=new LinkedHashMap<>();
        if(ai.configured()) {
            String prompt="产品："+bom.get("productName")+"；数量："+qty+"；类型："+mode+"；要求："+requirement+"。可选物料："+mapper.writeValueAsString(ms)+"；可选工艺："+mapper.writeValueAsString(ps)+"。只返回JSON：{materials:[{materialId,qty,lossRate,remark}],processes:[{processId,qty,remark}],budgetAdvice:string,riskAdvice:string}。qty为单件用量，lossRate小数。";
            String raw=ai.chat("你是文创产品生产工程师和成本会计。制定可落地BOM、工艺路线和预算建议，只能使用给定ID。",prompt,0.2,1800,35);
            int a=raw.indexOf('{'),z=raw.lastIndexOf('}'); if(a<0||z<a)throw new IllegalStateException("AI未返回有效JSON");
            result=mapper.convertValue(mapper.readTree(raw.substring(a,z+1)),Map.class);
        } else {
            result.put("materials", bom.get("materials")); result.put("processes",bom.get("processes"));
            result.put("budgetAdvice","当前未配置AI密钥，已按现有BOM形成预算草案，可人工调整后核算。"); result.put("riskAdvice","重点检查起订量、损耗率、打样费和交期。 ");
        }
        result.put("model",ai.configured()?ai.modelName():"rule-fallback"); result.put("quantity",qty); return result;
    }

    @PutMapping("/boms/{id}")
    public Map<String,Object> saveBom(@PathVariable Long id,@RequestBody BomEditRequest req) {
        if(req.productName!=null) jdbc.update("UPDATE product_bom SET product_name=?,planned_qty=?,target_price=?,status='confirmed',remark=? WHERE id=?",req.productName,req.plannedQty==null?100:req.plannedQty,req.targetPrice==null?BigDecimal.ZERO:req.targetPrice,req.remark,id);
        jdbc.update("DELETE FROM bom_material_item WHERE bom_id=?",id); if(req.materials!=null) for(BomMaterialEdit x:req.materials){BigDecimal cost=jdbc.queryForObject("SELECT standard_cost FROM material WHERE id=?",BigDecimal.class,x.materialId);jdbc.update("INSERT INTO bom_material_item(bom_id,material_id,qty,loss_rate,unit_cost,remark) VALUES(?,?,?,?,?,?)",id,x.materialId,x.qty,x.lossRate,cost,x.remark);}
        jdbc.update("DELETE FROM bom_process_item WHERE bom_id=?",id); if(req.processes!=null) for(BomProcessEdit x:req.processes){Map<String,Object> p=jdbc.queryForMap("SELECT standard_cost,standard_hours FROM craft_process WHERE id=?",x.processId);jdbc.update("INSERT INTO bom_process_item(bom_id,process_id,qty,unit_cost,standard_hours,remark) VALUES(?,?,?,?,?,?)",id,x.processId,x.qty,p.get("standard_cost"),p.get("standard_hours"),x.remark);}
        return bom(id);
    }

    @PostMapping("/commercial-orders")
    public Map<String,Object> createCommercialOrder(@RequestBody CommercialOrderRequest req) throws Exception {
        if (req.bomId == null) throw new IllegalArgumentException("请选择项目BOM");
        if (!"sample".equals(req.orderType) && !"bulk".equals(req.orderType)) throw new IllegalArgumentException("订单类型必须为sample或bulk");
        int qty = req.quantity == null || req.quantity <= 0 ? ("sample".equals(req.orderType) ? 3 : 100) : req.quantity;
        BigDecimal overhead = "sample".equals(req.orderType) ? new BigDecimal("0.18") : new BigDecimal("0.12");
        BigDecimal margin = req.targetMarginRate == null ? new BigDecimal("0.45") : req.targetMarginRate;
        Map<String,Object> quote = simulate(new QuoteRequest(req.bomId, qty, overhead, margin));
        Long quoteId = jdbc.queryForObject("SELECT id FROM cost_quote WHERE quote_no=?", Long.class, quote.get("quoteNo"));
        BigDecimal unitPrice = req.unitPrice == null || req.unitPrice.compareTo(BigDecimal.ZERO)<=0 ? bd(quote.get("suggestedPrice")) : req.unitPrice;
        String orderNo = no("ORD");
        Map<String,Object> bomMaster=bom(req.bomId);
        String bomSnapshot=mapper.writeValueAsString(Map.of("bomNo",bomMaster.get("bomNo"),"productName",bomMaster.get("productName"),"versionNo",bomMaster.get("versionNo"),"plannedQty",bomMaster.get("plannedQty"),"targetPrice",bomMaster.get("targetPrice"),"remark",String.valueOf(bomMaster.getOrDefault("remark",""))));
        String materialSnapshot=mapper.writeValueAsString(materialItems(req.bomId));
        String processSnapshot=mapper.writeValueAsString(processItems(req.bomId));
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> { PreparedStatement ps=con.prepareStatement("INSERT INTO commercial_order(order_no,order_type,project_id,project_sku_id,bom_id,quote_id,customer_name,contact_name,contact_phone,receiver_address,quantity,unit_price,total_amount,status,production_requirement,bom_snapshot_json,material_snapshot_json,process_snapshot_json) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CAST(? AS JSON),CAST(? AS JSON),CAST(? AS JSON))",Statement.RETURN_GENERATED_KEYS);
            ps.setString(1,orderNo);            ps.setString(2,req.orderType); if(req.projectId==null)ps.setNull(3,java.sql.Types.BIGINT);else ps.setLong(3,req.projectId); if(req.projectSkuId==null)ps.setNull(4,java.sql.Types.BIGINT);else ps.setLong(4,req.projectSkuId);
            ps.setLong(5,req.bomId);ps.setLong(6,quoteId);ps.setString(7,req.customerName);ps.setString(8,req.contactName);ps.setString(9,req.contactPhone);ps.setString(10,req.receiverAddress);ps.setInt(11,qty);ps.setBigDecimal(12,unitPrice);ps.setBigDecimal(13,unitPrice.multiply(BigDecimal.valueOf(qty)));ps.setString(14,"pending_confirm");ps.setString(15,req.productionRequirement);ps.setString(16,bomSnapshot);ps.setString(17,materialSnapshot);ps.setString(18,processSnapshot);return ps;},kh);
        return commercialOrder(Objects.requireNonNull(kh.getKey()).longValue());
    }

    @GetMapping("/commercial-orders/{id}")
    public Map<String,Object> commercialOrderDetail(@PathVariable Long id) {
        Map<String,Object> o=commercialOrder(id); Long bomId=((Number)o.get("bomId")).longValue();
        Map<String,Object> snapshots=jdbc.queryForMap("SELECT production_requirement productionRequirement,bom_snapshot_json bomSnapshot,material_snapshot_json materialSnapshot,process_snapshot_json processSnapshot FROM commercial_order WHERE id=?",id);
        o.put("productionRequirement",snapshots.get("productionRequirement")); o.put("bomSnapshot",jsonValue(snapshots.get("bomSnapshot"),Map.class));
        Object materialSnapshot=snapshots.get("materialSnapshot"), processSnapshot=snapshots.get("processSnapshot");
        o.put("materials",materialSnapshot==null?materialItems(bomId):jsonValue(materialSnapshot,List.class));
        o.put("processes",processSnapshot==null?processItems(bomId):jsonValue(processSnapshot,List.class));
        Object quoteId=o.get("quoteId");
        if(quoteId!=null) o.put("quote",jdbc.queryForMap("SELECT id,quote_no quoteNo,quantity,material_cost materialCost,process_cost processCost,packaging_cost packagingCost,overhead_cost overheadCost,total_cost totalCost,unit_cost unitCost,suggested_price suggestedPrice,gross_margin_rate grossMarginRate,created_at createdAt FROM cost_quote WHERE id=?",quoteId));
        return o;
    }

    @PostMapping("/commercial-orders/{id}/confirm")
    public Map<String,Object> confirmCommercialOrder(@PathVariable Long id) {
        if (!hasApprovedOrderApproval(id, "confirm")) {
            throw new IllegalStateException("请先提交订单确认申请，审批通过后才能确认订单");
        }
        jdbc.update("UPDATE commercial_order SET status='confirmed',confirmed_at=NOW() WHERE id=? AND status IN ('quoted','pending_confirm')",id);
        return commercialOrder(id);
    }

    @PostMapping("/commercial-orders/{id}/start")
    public Map<String,Object> startCommercialOrder(@PathVariable Long id) throws Exception {
        Map<String,Object> o=commercialOrder(id); String type=String.valueOf(o.get("orderType")); Long bomId=((Number)o.get("bomId")).longValue(); int qty=((Number)o.get("quantity")).intValue();
        if (!"confirmed".equals(String.valueOf(o.get("status")))) throw new IllegalStateException("订单须先经客户确认才能下达生产");
        if (!hasApprovedOrderApproval(id, "start")) {
            throw new IllegalStateException("请先提交下达生产申请，审批通过后才能进入下一步");
        }
        if ("sample".equals(type)) { createSample(new SampleRequest(bomId,null,qty,null,"来源统一订单 "+o.get("orderNo"))); Long sid=jdbc.queryForObject("SELECT id FROM sample_order WHERE bom_id=? ORDER BY id DESC LIMIT 1",Long.class,bomId); jdbc.update("UPDATE commercial_order SET sample_id=?,status='producing' WHERE id=?",sid,id); }
        else { Map<String,Object> po=createProduction(new ProductionRequest(bomId,qty,null,null)); jdbc.update("UPDATE commercial_order SET production_order_id=?,status='producing' WHERE id=?",po.get("productionId"),id); }
        return commercialOrder(id);
    }

    @PostMapping("/commercial-orders/{id}/approval-request")
    public Map<String,Object> requestCommercialOrderApproval(@PathVariable Long id, @RequestBody OrderApprovalRequest req) throws Exception {
        Map<String,Object> o = commercialOrder(id);
        String action = req == null || req.action == null ? "" : req.action.trim();
        if (!List.of("confirm", "start").contains(action)) throw new IllegalArgumentException("审批动作必须为 confirm 或 start");
        String status = String.valueOf(o.get("status"));
        if ("confirm".equals(action) && !List.of("quoted", "pending_confirm").contains(status)) {
            throw new IllegalStateException("当前订单状态不允许提交确认申请");
        }
        if ("start".equals(action) && !"confirmed".equals(status)) {
            throw new IllegalStateException("订单须先确认后才能提交下达生产申请");
        }
        Map<String,Object> existing = orderApproval(id, action);
        if (existing != null && List.of("pending", "approved").contains(String.valueOf(existing.get("status")))) {
            return existing;
        }

        String type = String.valueOf(o.get("orderType"));
        String typeName = "sample".equals(type) ? "打样" : "大货";
        String actionName = "confirm".equals(action) ? "订单确认" : "下达生产";
        String applicant = blank(req == null ? null : req.applicant) ? "当前用户" : req.applicant.trim();
        String applicantRole = blank(req == null ? null : req.applicantRole) ? "feeder" : req.applicantRole.trim();
        String appNo = no("WF");
        String title = typeName + actionName + "申请 - " + o.get("orderNo");
        Map<String,String> fields = new LinkedHashMap<>();
        fields.put("orderId", String.valueOf(id));
        fields.put("action", action);
        fields.put("动作", actionName);
        fields.put("业务类型", typeName);
        fields.put("订单号", String.valueOf(o.get("orderNo")));
        fields.put("产品名称", String.valueOf(o.get("productName")));
        fields.put("数量", String.valueOf(o.get("quantity")));
        fields.put("成交金额", String.valueOf(o.get("totalAmount")));
        fields.put("客户/公司", String.valueOf(o.getOrDefault("customerName", "")));
        fields.put("申请说明", blank(req == null ? null : req.comment) ? "申请进入下一步：" + actionName : req.comment.trim());
        String formJson = mapper.writeValueAsString(fields);
        jdbc.update(
                "INSERT INTO workflow_application (app_no,category,type_key,title,applicant,applicant_role,form_data_json,status) VALUES (?,?,?,?,?,?,?,?)",
                appNo, "production", type + "_" + action, title, applicant, applicantRole, formJson, "pending"
        );
        Long appId = jdbc.queryForObject("SELECT id FROM workflow_application WHERE app_no=?", Long.class, appNo);
        jdbc.update("INSERT INTO workflow_approval_log (application_id, action, operator, operator_role, comment) VALUES (?,?,?,?,?)",
                appId, "submit", applicant, applicantRole, fields.get("申请说明"));
        return orderApproval(id, action);
    }

    @PostMapping("/commercial-orders/{id}/ready")
    public Map<String,Object> readyCommercialOrder(@PathVariable Long id) { jdbc.update("UPDATE commercial_order SET status='ready_to_ship' WHERE id=? AND status='producing'",id); return commercialOrder(id); }

    private Map<String,Object> commercialOrder(Long id) { return jdbc.queryForMap("SELECT o.id,o.order_no orderNo,o.order_type orderType,o.project_id projectId,o.project_sku_id projectSkuId,o.bom_id bomId,b.product_name productName,o.quote_id quoteId,o.sample_id sampleId,o.production_order_id productionOrderId,o.customer_name customerName,o.contact_name contactName,o.contact_phone contactPhone,o.receiver_address receiverAddress,o.quantity,o.unit_price unitPrice,o.total_amount totalAmount,o.status,o.confirmed_at confirmedAt,o.created_at createdAt FROM commercial_order o JOIN product_bom b ON o.bom_id=b.id WHERE o.id=?",id); }

    private Map<String,Object> orderApproval(Long orderId, String action) {
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT id, app_no appNo, type_key typeKey, title, status, applicant, approver, approval_comment approvalComment, submitted_at submittedAt, approved_at approvedAt, rejected_at rejectedAt, updated_at updatedAt " +
                "FROM workflow_application WHERE deleted=0 AND category='production' " +
                "AND JSON_UNQUOTE(JSON_EXTRACT(form_data_json,'$.orderId'))=? AND JSON_UNQUOTE(JSON_EXTRACT(form_data_json,'$.action'))=? " +
                "ORDER BY id DESC LIMIT 1",
                String.valueOf(orderId), action
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean hasApprovedOrderApproval(Long orderId, String action) {
        Map<String,Object> approval = orderApproval(orderId, action);
        return approval != null && "approved".equals(String.valueOf(approval.get("status")));
    }

    @GetMapping("/purchase-suggestions")
    public List<Map<String, Object>> purchaseSuggestions() {
        return jdbc.queryForList("SELECT ps.id, ps.production_order_id productionOrderId, ps.material_id materialId, m.name materialName, m.unit, ps.required_qty requiredQty, ps.stock_qty stockQty, ps.suggest_purchase_qty suggestPurchaseQty, ps.status, ps.created_at createdAt FROM purchase_suggestion ps JOIN material m ON ps.material_id=m.id ORDER BY ps.id DESC");
    }

    private void enrichBom(Map<String, Object> b) {
        Long id = ((Number) b.get("id")).longValue();
        b.put("materials", materialItems(id));
        b.put("processes", processItems(id));
    }

    private List<Map<String, Object>> materialItems(Long bomId) {
        return jdbc.queryForList("SELECT i.id, i.material_id materialId, m.material_code materialCode, m.name, m.category, m.unit, i.qty, i.loss_rate lossRate, i.unit_cost unitCost, i.remark, m.stock_qty stockQty FROM bom_material_item i JOIN material m ON i.material_id=m.id WHERE i.bom_id=? ORDER BY i.id", bomId);
    }

    private List<Map<String, Object>> processItems(Long bomId) {
        return jdbc.queryForList("SELECT i.id, i.process_id processId, p.process_code processCode, p.name, p.category, p.unit, i.qty, i.unit_cost unitCost, i.standard_hours standardHours, i.remark FROM bom_process_item i JOIN craft_process p ON i.process_id=p.id WHERE i.bom_id=? ORDER BY i.id", bomId);
    }

    private Long createBom(String name, Long skuId, Long assetId, Integer plannedQty, BigDecimal targetPrice) {
        KeyHolder kh = new GeneratedKeyHolder();
        String bomNo = no("BOM");
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO product_bom (bom_no,sku_id,asset_id,product_name,planned_qty,target_price,status,remark) VALUES (?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, bomNo); if(skuId==null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, skuId); if(assetId==null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, assetId); ps.setString(4, name); ps.setInt(5, plannedQty); ps.setBigDecimal(6, targetPrice); ps.setString(7, "draft"); ps.setString(8, "AI半自动生成BOM"); return ps;
        }, kh);
        return Objects.requireNonNull(kh.getKey()).longValue();
    }

    private void addMaterial(Long bomId, Long materialId, BigDecimal qty, BigDecimal loss) {
        BigDecimal unit = jdbc.queryForObject("SELECT standard_cost FROM material WHERE id=?", BigDecimal.class, materialId);
        jdbc.update("INSERT INTO bom_material_item (bom_id,material_id,qty,loss_rate,unit_cost) VALUES (?,?,?,?,?)", bomId, materialId, qty, loss, unit);
    }

    private void addProcess(Long bomId, Long processId, BigDecimal qty) {
        Map<String, Object> p = jdbc.queryForMap("SELECT standard_cost, standard_hours FROM craft_process WHERE id=?", processId);
        jdbc.update("INSERT INTO bom_process_item (bom_id,process_id,qty,unit_cost,standard_hours) VALUES (?,?,?,?,?)", bomId, processId, qty, p.get("standard_cost"), p.get("standard_hours"));
    }

    private void generatePurchaseSuggestions(Long poId, Long bomId, int qty) {
        for (Map<String, Object> it : materialItems(bomId)) {
            BigDecimal required = bd(it.get("qty")).multiply(BigDecimal.ONE.add(bd(it.get("lossRate")))).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal stock = bd(it.get("stockQty"));
            BigDecimal suggest = required.subtract(stock).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            if (suggest.compareTo(BigDecimal.ZERO) > 0) {
                jdbc.update("INSERT INTO purchase_suggestion (production_order_id,material_id,required_qty,stock_qty,suggest_purchase_qty) VALUES (?,?,?,?,?)", poId, it.get("materialId"), required, stock, suggest);
            }
        }
    }

    private <T> T jsonValue(Object value, Class<T> type) {
        if(value==null)return null;
        try { if(value instanceof byte[]) return mapper.readValue((byte[])value,type); return mapper.readValue(String.valueOf(value),type); }
        catch(Exception e){ throw new IllegalStateException("订单生产快照解析失败",e); }
    }

    private Long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private BigDecimal bd(Object o) { if (o == null) return BigDecimal.ZERO; if (o instanceof BigDecimal) return (BigDecimal) o; if (o instanceof Number) return BigDecimal.valueOf(((Number)o).doubleValue()); return new BigDecimal(String.valueOf(o)); }
    private String no(String prefix) { return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int)(Math.random()*900+100); }
    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    public static class AutoBomRequest { public String productName; public String productType; public Long skuId; public Long assetId; public Integer plannedQty; public BigDecimal targetPrice; }
    public static class QuoteRequest { public Long bomId; public Integer quantity; public BigDecimal overheadRate; public BigDecimal targetMarginRate; public QuoteRequest(){} public QuoteRequest(Long bomId, Integer quantity, BigDecimal overheadRate, BigDecimal targetMarginRate){this.bomId=bomId;this.quantity=quantity;this.overheadRate=overheadRate;this.targetMarginRate=targetMarginRate;} }
    public static class SampleRequest { public Long bomId; public Long assetId; public Integer sampleQty; public LocalDate dueDate; public String feedback; public SampleRequest(){} public SampleRequest(Long bomId,Long assetId,Integer sampleQty,LocalDate dueDate,String feedback){this.bomId=bomId;this.assetId=assetId;this.sampleQty=sampleQty;this.dueDate=dueDate;this.feedback=feedback;} }
    public static class ProductionRequest { public Long bomId; public Integer plannedQty; public LocalDate startDate; public LocalDate dueDate; public ProductionRequest(){} public ProductionRequest(Long bomId,Integer plannedQty,LocalDate startDate,LocalDate dueDate){this.bomId=bomId;this.plannedQty=plannedQty;this.startDate=startDate;this.dueDate=dueDate;} }
    public static class BomEditRequest { public String productName; public Integer plannedQty; public BigDecimal targetPrice; public String remark; public List<BomMaterialEdit> materials; public List<BomProcessEdit> processes; }
    public static class BomMaterialEdit { public Long materialId; public BigDecimal qty; public BigDecimal lossRate; public String remark; }
    public static class BomProcessEdit { public Long processId; public BigDecimal qty; public String remark; }
    public static class CommercialOrderRequest { public String orderType; public Long projectId; public Long projectSkuId; public Long bomId; public Integer quantity; public BigDecimal unitPrice; public BigDecimal targetMarginRate; public String customerName; public String contactName; public String contactPhone; public String receiverAddress; public String productionRequirement; }
    public static class OrderApprovalRequest { public String action; public String applicant; public String applicantRole; public String comment; }
}
