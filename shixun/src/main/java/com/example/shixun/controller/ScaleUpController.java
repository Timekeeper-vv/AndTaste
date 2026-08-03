package com.example.shixun.controller;

import com.example.shixun.service.SiliconFlowChatService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/scale")
public class ScaleUpController {
    private final JdbcTemplate jdbc;
    private final SiliconFlowChatService ai;

    public ScaleUpController(JdbcTemplate jdbc, SiliconFlowChatService ai) {
        this.jdbc = jdbc;
        this.ai = ai;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "tenantCount", count("saas_tenant"),
                "activeSubscriptionCount", jdbc.queryForObject("SELECT COUNT(*) FROM saas_subscription WHERE status IN ('active','trial')", Long.class),
                "projectCount", count("creative_project"),
                "projectSkuCount", count("creative_project_sku"),
                "templateCount", count("template_market_item"),
                "mrr", decimal("SELECT COALESCE(SUM(p.monthly_price),0) FROM saas_subscription s JOIN saas_plan p ON s.plan_id=p.id WHERE s.status IN ('active','trial')")
        );
    }

    @GetMapping("/tenants")
    public List<Map<String, Object>> tenants() {
        return jdbc.queryForList("SELECT t.id, t.tenant_no tenantNo, t.name, t.industry, t.contact_name contactName, t.phone, t.status, p.name planName, p.monthly_price monthlyPrice, s.status subscriptionStatus, s.end_date endDate, t.created_at createdAt FROM saas_tenant t LEFT JOIN saas_subscription s ON t.id=s.tenant_id LEFT JOIN saas_plan p ON s.plan_id=p.id ORDER BY t.id DESC");
    }

    @PostMapping("/tenants")
    public Map<String, Object> createTenant(@RequestBody TenantRequest req) {
        if (blank(req.name)) throw new IllegalArgumentException("租户名称不能为空");
        String no = no("TNT");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO saas_tenant (tenant_no,name,industry,contact_name,phone) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, no); ps.setString(2, req.name); ps.setString(3, req.industry); ps.setString(4, req.contactName); ps.setString(5, req.phone); return ps;
        }, kh);
        Long id = Objects.requireNonNull(kh.getKey()).longValue();
        Long planId = req.planId == null ? 1L : req.planId;
        jdbc.update("INSERT INTO saas_subscription (tenant_id,plan_id,status,start_date,end_date) VALUES (?,?,?,?,?)", id, planId, "trial", LocalDate.now(), LocalDate.now().plusDays(14));
        return tenant(id);
    }

    @GetMapping("/plans")
    public List<Map<String, Object>> plans() {
        return jdbc.queryForList("SELECT id, plan_code planCode, name, monthly_price monthlyPrice, user_limit userLimit, inquiry_limit inquiryLimit, ai_quota aiQuota, project_limit projectLimit, features_json featuresJson FROM saas_plan WHERE enabled=1 ORDER BY monthly_price");
    }

    @PostMapping("/subscriptions")
    public Map<String, Object> subscribe(@RequestBody SubscribeRequest req) {
        if (req.tenantId == null || req.planId == null) throw new IllegalArgumentException("tenantId/planId不能为空");
        jdbc.update("UPDATE saas_subscription SET status='cancelled' WHERE tenant_id=? AND status IN ('trial','active')", req.tenantId);
        jdbc.update("INSERT INTO saas_subscription (tenant_id,plan_id,status,start_date,end_date) VALUES (?,?,?,?,?)", req.tenantId, req.planId, "active", LocalDate.now(), LocalDate.now().plusMonths(req.months == null ? 1 : req.months));
        return tenant(req.tenantId);
    }

    @GetMapping("/usage")
    public List<Map<String, Object>> usage(@RequestParam(required = false) Long tenantId) {
        if (tenantId != null) return jdbc.queryForList("SELECT u.id, u.tenant_id tenantId, t.name tenantName, u.usage_month usageMonth, u.metric, u.used_count usedCount, u.updated_at updatedAt FROM saas_usage_meter u JOIN saas_tenant t ON u.tenant_id=t.id WHERE u.tenant_id=? ORDER BY u.usage_month DESC,u.metric", tenantId);
        return jdbc.queryForList("SELECT u.id, u.tenant_id tenantId, t.name tenantName, u.usage_month usageMonth, u.metric, u.used_count usedCount, u.updated_at updatedAt FROM saas_usage_meter u JOIN saas_tenant t ON u.tenant_id=t.id ORDER BY u.usage_month DESC,u.metric LIMIT 200");
    }

    @GetMapping("/templates")
    public List<Map<String, Object>> templates(@RequestParam(required = false) String category) {
        if (!blank(category)) return jdbc.queryForList("SELECT id, template_no templateNo, title, category, product_type productType, description, template_json templateJson, price, usage_count usageCount, status FROM template_market_item WHERE status='published' AND category=? ORDER BY usage_count DESC,id DESC", category);
        return jdbc.queryForList("SELECT id, template_no templateNo, title, category, product_type productType, description, template_json templateJson, price, usage_count usageCount, status FROM template_market_item WHERE status='published' ORDER BY usage_count DESC,id DESC");
    }

    @PostMapping("/templates/{id}/use")
    public Map<String, Object> useTemplate(@PathVariable Long id, @RequestBody(required = false) UseTemplateRequest req) {
        jdbc.update("UPDATE template_market_item SET usage_count=usage_count+1 WHERE id=?", id);
        if (req != null && req.tenantId != null) incUsage(req.tenantId, "template_use", 1);
        return jdbc.queryForMap("SELECT id, template_no templateNo, title, category, product_type productType, description, template_json templateJson, usage_count usageCount FROM template_market_item WHERE id=?", id);
    }

    @GetMapping("/projects")
    public List<Map<String, Object>> projects(@RequestParam(required = false) Long tenantId) {
        String sql = "SELECT p.id, p.project_no projectNo, p.tenant_id tenantId, t.name tenantName, p.inquiry_id inquiryId, p.quote_id quoteId, p.name, p.theme, p.target_audience targetAudience, p.product_types productTypes, p.budget, p.status, p.created_at createdAt FROM creative_project p LEFT JOIN saas_tenant t ON p.tenant_id=t.id";
        if (tenantId != null) return jdbc.queryForList(sql + " WHERE p.tenant_id=? ORDER BY p.id DESC", tenantId);
        return jdbc.queryForList(sql + " ORDER BY p.id DESC LIMIT 100");
    }

    @GetMapping("/projects/{id}")
    public Map<String, Object> project(@PathVariable Long id) {
        Map<String, Object> p = jdbc.queryForMap("SELECT p.id, p.project_no projectNo, p.tenant_id tenantId, t.name tenantName, p.inquiry_id inquiryId, p.quote_id quoteId, p.name, p.theme, p.target_audience targetAudience, p.product_types productTypes, p.budget, p.status, p.ai_plan aiPlan, p.ai_review aiReview, p.created_at createdAt FROM creative_project p LEFT JOIN saas_tenant t ON p.tenant_id=t.id WHERE p.id=?", id);
        p.put("skus", projectSkus(id));
        return p;
    }

    @PostMapping("/projects")
    public Map<String, Object> createProject(@RequestBody ProjectRequest req) {
        if (blank(req.name)) req.name = blank(req.theme) ? "新文创开发项目" : req.theme + "项目";
        if (req.tenantId == null) req.tenantId = 1L;
        String no = no("PRJ");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO creative_project (project_no,tenant_id,inquiry_id,quote_id,name,theme,target_audience,product_types,budget,status) VALUES (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, no); ps.setLong(2, req.tenantId); setLongOrNull(ps,3,req.inquiryId); setLongOrNull(ps,4,req.quoteId); ps.setString(5, req.name); ps.setString(6, req.theme); ps.setString(7, req.targetAudience); ps.setString(8, req.productTypes); ps.setString(9, req.budget); ps.setString(10, "planning"); return ps;
        }, kh);
        incUsage(req.tenantId, "project", 1);
        return project(Objects.requireNonNull(kh.getKey()).longValue());
    }

    @PostMapping("/projects/{id}/ai-plan")
    public Map<String, Object> aiPlan(@PathVariable Long id) {
        Map<String, Object> p = project(id);
        String prompt = "项目：" + p.get("name") + "\n主题：" + p.get("theme") + "\n人群：" + p.get("targetAudience") + "\n产品类型：" + p.get("productTypes") + "\n预算：" + p.get("budget") + "\n请输出从设计到生产的项目方案：视觉方向、SKU矩阵、打样顺序、BOM注意事项、供应商要求、风险。";
        String content = callAi("你是AI文创智造项目经理，输出可执行的设计到生产计划。", prompt);
        jdbc.update("UPDATE creative_project SET ai_plan=?, status='design' WHERE id=?", content, id);
        incUsage(longVal(p.get("tenantId"), 1L), "ai_call", 1);
        return project(id);
    }

    @PostMapping("/projects/{id}/generate-skus")
    public Map<String, Object> generateSkus(@PathVariable Long id) {
        Map<String, Object> p = project(id);
        if (!projectSkus(id).isEmpty()) return project(id);
        List<Map<String, Object>> skuDefs = List.of(
                Map.of("name", p.get("theme") + "钥匙扣", "type", "亚克力钥匙扣", "price", new BigDecimal("29.90"), "qty", 500, "material", "3mm透明亚克力", "point", "轻量伴手礼，适合景区和活动赠品"),
                Map.of("name", p.get("theme") + "冰箱贴", "type", "冰箱贴", "price", new BigDecimal("19.90"), "qty", 500, "material", "软磁+滴胶", "point", "高频购买，小体积易陈列"),
                Map.of("name", p.get("theme") + "明信片套装", "type", "明信片套装", "price", new BigDecimal("39.90"), "qty", 300, "material", "300g艺术纸", "point", "系列化讲故事，适合套装销售")
        );
        for (Map<String, Object> s : skuDefs) {
            jdbc.update("INSERT INTO creative_project_sku (project_id,sku_name,product_type,target_price,planned_qty,material,selling_point) VALUES (?,?,?,?,?,?,?)", id, s.get("name"), s.get("type"), s.get("price"), s.get("qty"), s.get("material"), s.get("point"));
        }
        jdbc.update("UPDATE creative_project SET status='review' WHERE id=?", id);
        return project(id);
    }

    @PostMapping("/projects/{id}/ai-review")
    public Map<String, Object> aiReview(@PathVariable Long id) {
        Map<String, Object> p = project(id);
        String content = callAi("你是文创AI评审团，请从设计、市场、成本、生产、版权五个角度评审，并给出是否进入打样。", p.toString());
        jdbc.update("UPDATE creative_project SET ai_review=?, status='bom' WHERE id=?", content, id);
        incUsage(longVal(p.get("tenantId"), 1L), "ai_call", 1);
        return project(id);
    }

    @PostMapping("/project-skus/{skuId}/bom-sample")
    public Map<String, Object> skuBomSample(@PathVariable Long skuId) {
        Map<String, Object> sku = jdbc.queryForMap("SELECT * FROM creative_project_sku WHERE id=?", skuId);
        Long bomId = createBom(String.valueOf(sku.get("sku_name")), intVal(sku.get("planned_qty"), 100), bd(sku.get("target_price")), String.valueOf(sku.get("product_type")));
        String sampleNo = no("SMP");
        KeyHolder skh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO sample_order (sample_no,bom_id,sample_qty,status,estimated_cost,due_date,feedback) VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, sampleNo); ps.setLong(2, bomId); ps.setInt(3, 3); ps.setString(4, "pending"); ps.setBigDecimal(5, new BigDecimal("99.00")); ps.setObject(6, LocalDate.now().plusDays(5)); ps.setString(7, "由二阶段项目SKU自动生成打样单"); return ps;
        }, skh);
        Long sampleId = Objects.requireNonNull(skh.getKey()).longValue();
        jdbc.update("UPDATE creative_project_sku SET bom_id=?, sample_id=?, status='sample' WHERE id=?", bomId, sampleId, skuId);
        jdbc.update("UPDATE creative_project SET status='sample' WHERE id=?", sku.get("project_id"));
        return Map.of("bomId", bomId, "sampleId", sampleId, "sampleNo", sampleNo);
    }

    @PostMapping("/project-skus/{skuId}/production")
    public Map<String, Object> skuProduction(@PathVariable Long skuId) {
        Map<String, Object> sku = jdbc.queryForMap("SELECT * FROM creative_project_sku WHERE id=?", skuId);
        Object bom = sku.get("bom_id");
        if (bom == null) throw new IllegalArgumentException("请先生成BOM和打样单");
        String productionNo = no("MFG");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO production_order (production_no,bom_id,planned_qty,status,estimated_cost,start_date,due_date) VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, productionNo); ps.setLong(2, ((Number) bom).longValue()); ps.setInt(3, intVal(sku.get("planned_qty"), 100)); ps.setString(4, "planned"); ps.setBigDecimal(5, bd(sku.get("target_price")).multiply(BigDecimal.valueOf(intVal(sku.get("planned_qty"), 100))).multiply(new BigDecimal("0.55"))); ps.setObject(6, LocalDate.now()); ps.setObject(7, LocalDate.now().plusDays(12)); return ps;
        }, kh);
        Long poId = Objects.requireNonNull(kh.getKey()).longValue();
        jdbc.update("UPDATE creative_project_sku SET production_order_id=?, status='production' WHERE id=?", poId, skuId);
        jdbc.update("UPDATE creative_project SET status='production' WHERE id=?", sku.get("project_id"));
        return Map.of("productionOrderId", poId, "productionNo", productionNo);
    }

    private Map<String, Object> tenant(Long id) { return jdbc.queryForMap("SELECT id, tenant_no tenantNo, name, industry, contact_name contactName, phone, status FROM saas_tenant WHERE id=?", id); }
    private List<Map<String, Object>> projectSkus(Long projectId) { return jdbc.queryForList("SELECT id, project_id projectId, sku_name skuName, product_type productType, target_price targetPrice, planned_qty plannedQty, material, selling_point sellingPoint, bom_id bomId, sample_id sampleId, production_order_id productionOrderId, status, created_at createdAt FROM creative_project_sku WHERE project_id=? ORDER BY id", projectId); }
    private String callAi(String system, String prompt) { try { return ai.chat(system, prompt, 0.55, 1200, 90); } catch (Exception e) { return "AI调用失败，已保留规则流程。原因：" + e.getMessage(); } }
    private void incUsage(Long tenantId, String metric, int amount) { String month = DateTimeFormatter.ofPattern("yyyy-MM").format(LocalDate.now()); jdbc.update("INSERT INTO saas_usage_meter (tenant_id,usage_month,metric,used_count) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE used_count=used_count+VALUES(used_count)", tenantId, month, metric, amount); }
    private Long createBom(String name, int qty, BigDecimal targetPrice, String productType) { String bomNo = no("BOM"); KeyHolder kh = new GeneratedKeyHolder(); jdbc.update(con -> { PreparedStatement ps = con.prepareStatement("INSERT INTO product_bom (bom_no,product_name,planned_qty,target_price,status,remark) VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS); ps.setString(1, bomNo); ps.setString(2, name); ps.setInt(3, qty); ps.setBigDecimal(4, targetPrice); ps.setString(5, "draft"); ps.setString(6, "项目工作流自动生成BOM"); return ps; }, kh); return Objects.requireNonNull(kh.getKey()).longValue(); }
    private void setLongOrNull(PreparedStatement ps, int idx, Long v) throws java.sql.SQLException { if (v == null) ps.setNull(idx, java.sql.Types.BIGINT); else ps.setLong(idx, v); }
    private Long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
    private BigDecimal decimal(String sql) { BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class); return v == null ? BigDecimal.ZERO : v; }
    private BigDecimal bd(Object o) { if (o == null) return BigDecimal.ZERO; if (o instanceof BigDecimal) return (BigDecimal)o; if (o instanceof Number) return BigDecimal.valueOf(((Number)o).doubleValue()); return new BigDecimal(String.valueOf(o)); }
    private int intVal(Object o, int fallback) { if (o instanceof Number) return ((Number)o).intValue(); try { return Integer.parseInt(String.valueOf(o)); } catch(Exception e) { return fallback; } }
    private long longVal(Object o, long fallback) { if (o instanceof Number) return ((Number)o).longValue(); try { return Long.parseLong(String.valueOf(o)); } catch(Exception e) { return fallback; } }
    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private String no(String prefix) { return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int)(Math.random()*900+100); }

    public static class TenantRequest { public String name; public String industry; public String contactName; public String phone; public Long planId; }
    public static class SubscribeRequest { public Long tenantId; public Long planId; public Integer months; }
    public static class UseTemplateRequest { public Long tenantId; }
    public static class ProjectRequest { public Long tenantId; public Long inquiryId; public Long quoteId; public String name; public String theme; public String targetAudience; public String productTypes; public String budget; }
}
