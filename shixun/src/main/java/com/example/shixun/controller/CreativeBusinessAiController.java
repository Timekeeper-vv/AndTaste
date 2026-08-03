package com.example.shixun.controller;

import com.example.shixun.service.SiliconFlowChatService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/creative/assistant")
public class CreativeBusinessAiController {
    private final JdbcTemplate jdbc;
    private final SiliconFlowChatService siliconFlow;

    public CreativeBusinessAiController(JdbcTemplate jdbc, SiliconFlowChatService siliconFlow) {
        this.jdbc = jdbc;
        this.siliconFlow = siliconFlow;
    }

    @PostMapping("/quote")
    public Map<String, Object> quote(@RequestBody QuoteAssistantRequest req) {
        normalizeQuote(req);
        ProductCost cost = costByType(req.productType);
        int qty = Math.max(req.quantity == null ? 100 : req.quantity, 1);
        BigDecimal logisticsTotal = logisticsEstimate(req.destination, qty, req.productType);
        BigDecimal designTotal = money(req.designFee == null ? new BigDecimal("300") : req.designFee);
        BigDecimal setupTotal = setupFee(req.productType);
        BigDecimal directUnit = cost.material.add(cost.process).add(cost.packageCost)
                .add(logisticsTotal.divide(BigDecimal.valueOf(qty), 4, RoundingMode.HALF_UP))
                .add(designTotal.add(setupTotal).divide(BigDecimal.valueOf(qty), 4, RoundingMode.HALF_UP));
        BigDecimal overheadRate = ratio(req.overheadRate, new BigDecimal("0.12"));
        BigDecimal marginRate = ratio(req.targetMarginRate, new BigDecimal("0.45"));
        BigDecimal unitCost = directUnit.multiply(BigDecimal.ONE.add(overheadRate)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal suggestedPrice = unitCost.divide(BigDecimal.ONE.subtract(marginRate), 2, RoundingMode.HALF_UP);
        BigDecimal floorPrice = unitCost.divide(new BigDecimal("0.75"), 2, RoundingMode.HALF_UP);
        BigDecimal totalCost = unitCost.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalQuote = suggestedPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        List<String> questions = missingQuoteQuestions(req);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("quoteNo", no("AIQ"));
        result.put("productName", req.productName);
        result.put("productType", req.productType);
        result.put("quantity", qty);
        result.put("unitCost", unitCost);
        result.put("suggestedUnitPrice", suggestedPrice);
        result.put("floorUnitPrice", floorPrice);
        result.put("totalCost", totalCost);
        result.put("totalQuote", totalQuote);
        result.put("grossMarginRate", marginRate);
        result.put("leadTime", leadTime(req.productType, qty));
        result.put("costBreakdown", Map.of(
                "materialUnit", cost.material,
                "processUnit", cost.process,
                "packageUnit", cost.packageCost,
                "logisticsTotal", logisticsTotal,
                "designFee", designTotal,
                "setupFee", setupTotal,
                "overheadRate", overheadRate
        ));
        result.put("questions", questions);
        result.put("risks", quoteRisks(req, qty));
        result.put("customerReply", buildQuoteReply(req, qty, suggestedPrice, totalQuote, questions));
        result.put("internalAdvice", List.of(
                "先按建议单价对外沟通，底价仅内部可见。",
                "打样前确认尺寸、材质、包装、是否异形、是否需要授权图案。",
                "批量订单建议收取30%-50%定金，并把交期与改稿次数写入报价单。"
        ));
        attachAi(result, "你是文创定制业务报价专家。请基于系统已计算出的成本、建议价、交期和风险，生成更自然的客户报价话术、内部复核建议和可追问问题。不要改变系统计算出的数值。", result.toString());
        return result;
    }

    @PostMapping("/planning")
    public Map<String, Object> planning(@RequestBody PlanningRequest req) {
        if (blank(req.theme)) req.theme = "城市味道与地域文化";
        if (blank(req.audience)) req.audience = "年轻游客、文创爱好者、送礼人群";
        if (blank(req.channel)) req.channel = "小红书、抖音、线下门店";
        if (blank(req.budget)) req.budget = "小批量试产，单品29-99元";
        List<Map<String, Object>> concepts = List.of(
                concept("城市记忆套装", "把地标、街巷、食物和方言符号做成系列视觉", List.of("明信片8张", "冰箱贴3枚", "贴纸包1套"), "适合旅游纪念和伴手礼组合销售"),
                concept("节气限定礼物", "围绕节气、地方食味和温暖祝福做限定包装", List.of("礼盒包装", "挂件", "贺卡"), "适合节日营销和企业团购"),
                concept("治愈办公小物", "把地域文化降低严肃感，转成可日常使用的小物", List.of("杯垫", "帆布袋", "亚克力立牌"), "适合年轻用户自用和社交分享")
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planNo", no("PLN"));
        result.put("positioning", "面向" + req.audience + "，用“" + req.theme + "”建立可系列化、可复购、可拍照传播的文创SKU。渠道优先投放" + req.channel + "。");
        result.put("concepts", concepts);
        result.put("launchRoadmap", List.of(
                Map.of("week", "第1周", "task", "确定主题、视觉风格、核心SKU和目标价格带"),
                Map.of("week", "第2周", "task", "AI出图/人工精修/版权检查/内部评审"),
                Map.of("week", "第3周", "task", "生成BOM、打样、拍摄产品图、准备详情页文案"),
                Map.of("week", "第4周", "task", "小批量上架，跑小红书/抖音内容测试，根据收藏和转化决定补货")
        ));
        result.put("kpi", List.of("首批3-5个SKU", "单品毛利率目标40%以上", "7天内容互动率>5%", "首批售罄周期≤30天"));
        result.put("dataToCollect", List.of("客户询盘高频关键词", "不同价格带转化率", "SKU库存周转天数", "小红书/抖音互动数据", "打样失败原因"));
        result.put("riskControl", List.of("避免直接使用未授权IP或明显模仿风格", "控制首批库存，先验证再放量", "高客单礼盒需要提前验证包装损耗和物流破损率"));
        result.put("nextAction", "建议先选择1个概念进入AI创意工坊生成主视觉，再到生产管理创建BOM并核算报价。");
        attachAi(result, "你是文创新品产品经理。请基于企划输入和系统方案，扩展成可执行的新品开发方案，包含SKU优先级、视觉方向、打样建议、营销测试和风险提醒。", result.toString());
        return result;
    }

    @PostMapping("/logistics")
    public Map<String, Object> logistics(@RequestBody LogisticsRequest req) {
        List<Map<String, Object>> orders = recentOrders(req.orderNo);
        int qty = req.quantity == null || req.quantity <= 0 ? inferQty(orders) : req.quantity;
        String destination = blank(req.destination) ? "同城/江浙沪" : req.destination;
        BigDecimal freight = logisticsEstimate(destination, qty, blank(req.productType) ? "postcard" : req.productType);
        int productionDays = qty > 1000 ? 10 : qty > 300 ? 7 : 4;
        int shippingDays = destination.contains("新疆") || destination.contains("西藏") || destination.contains("海南") ? 5 : destination.contains("江浙沪") || destination.contains("同城") ? 2 : 3;
        LocalDate shipDate = LocalDate.now().plusDays(productionDays);
        LocalDate eta = shipDate.plusDays(shippingDays);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logisticsNo", no("LOG"));
        result.put("orders", orders);
        result.put("quantity", qty);
        result.put("destination", destination);
        result.put("estimatedFreight", freight);
        result.put("productionDays", productionDays);
        result.put("shippingDays", shippingDays);
        result.put("suggestedShipDate", shipDate.toString());
        result.put("eta", eta.toString());
        result.put("carrierOptions", List.of(
                Map.of("name", "顺丰/京东", "cost", freight.multiply(new BigDecimal("1.35")).setScale(2, RoundingMode.HALF_UP), "advantage", "时效稳定，适合急单和礼盒"),
                Map.of("name", "中通/圆通", "cost", freight, "advantage", "性价比高，适合常规订单"),
                Map.of("name", "德邦/跨越", "cost", freight.multiply(new BigDecimal("1.15")).setScale(2, RoundingMode.HALF_UP), "advantage", "适合大件、批量箱货")
        ));
        result.put("alerts", logisticsAlerts(qty, destination));
        result.put("customerReply", "您好，当前订单预计" + shipDate.format(DateTimeFormatter.ISO_DATE) + "前完成生产并发货，预计" + eta.format(DateTimeFormatter.ISO_DATE) + "左右送达。若您有活动截止日期，建议提前告诉我们，我们会优先安排打样、生产和物流。 ");
        result.put("internalChecklist", List.of("发货前拍照留档", "礼盒类加护角/气泡袋", "批量订单拆箱编号", "同步客户物流单号", "异常件24小时内跟进"));
        attachAi(result, "你是文创订单履约和物流客服专家。请基于系统预测的发货日、到达日、运费和风险，生成客户通知话术、内部履约清单和异常预案。不要改变系统日期和金额。", result.toString());
        return result;
    }

    @GetMapping("/finance")
    public Map<String, Object> finance() {
        BigDecimal revenue = queryDecimal("SELECT COALESCE(SUM(pay_amount),0) FROM `order` WHERE order_status <> 'cancelled'");
        Long orderCount = queryLong("SELECT COUNT(*) FROM `order`");
        Long skuCount = queryLong("SELECT COUNT(*) FROM product_sku");
        Long artworkCount = queryLong("SELECT COUNT(*) FROM artwork");
        BigDecimal avgOrder = orderCount == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);
        List<Map<String, Object>> topSkus = jdbc.queryForList(
                "SELECT oi.product_name productName, SUM(oi.quantity) qty, SUM(oi.subtotal) amount " +
                "FROM order_item oi GROUP BY oi.product_name ORDER BY amount DESC LIMIT 5");
        List<Map<String, Object>> stockAlerts = jdbc.queryForList(
                "SELECT product_name productName, stock, price FROM product_sku WHERE stock < 100 ORDER BY stock ASC LIMIT 10");
        List<Map<String, Object>> quoteMargins = jdbc.queryForList(
                "SELECT q.quote_no quoteNo, b.product_name productName, q.quantity, q.unit_cost unitCost, q.suggested_price suggestedPrice, q.gross_margin_rate grossMarginRate, q.created_at createdAt " +
                "FROM cost_quote q JOIN product_bom b ON q.bom_id=b.id ORDER BY q.id DESC LIMIT 8");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", Map.of(
                "revenue", revenue,
                "orderCount", orderCount,
                "avgOrder", avgOrder,
                "skuCount", skuCount,
                "artworkCount", artworkCount
        ));
        result.put("topSkus", topSkus);
        result.put("stockAlerts", stockAlerts);
        result.put("recentQuoteMargins", quoteMargins);
        result.put("analysis", List.of(
                revenue.compareTo(BigDecimal.ZERO) == 0 ? "当前模拟订单收入较少，建议先通过商城生成订单样本，再观察SKU表现。" : "当前累计成交额为¥" + revenue + "，客单价约¥" + avgOrder + "。",
                "优先关注库存低于100的SKU，结合内容热度决定补货或下架。",
                "报价记录要持续沉淀，后续可用历史毛利率训练更准确的报价规则。",
                "建议把营销文案、设计资产、BOM、报价、订单串起来，形成从创意到回款的闭环数据。"
        ));
        result.put("nextActions", List.of("每周导出经营简报", "为爆款SKU建立安全库存", "把物流异常率纳入供应商评分", "把客户询盘接入报价助手"));
        attachAi(result, "你是文创电商经营分析师。请基于订单、SKU、库存和报价记录，生成一份简短经营分析，指出机会、风险和下周动作。必须说明数据来自当前系统样本，避免过度推断。", result.toString());
        return result;
    }


    private void attachAi(Map<String, Object> result, String system, String user) {
        try {
            String content = siliconFlow.chat(system, user, 0.55, 1200, 45);
            result.put("aiEnabled", true);
            result.put("source", "siliconflow:" + siliconFlow.modelName());
            result.put("aiDraft", content);
        } catch (Exception e) {
            result.put("aiEnabled", false);
            result.put("source", "local-rule-fallback");
            result.put("aiError", e.getMessage());
        }
    }

    private ProductCost costByType(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.contains("canvas") || t.contains("帆布")) return new ProductCost(new BigDecimal("18.50"), new BigDecimal("7.10"), new BigDecimal("3.20"));
        if (t.contains("sticker") || t.contains("贴纸")) return new ProductCost(new BigDecimal("1.48"), new BigDecimal("1.80"), new BigDecimal("0.60"));
        if (t.contains("acrylic") || t.contains("亚克力") || t.contains("钥匙扣")) return new ProductCost(new BigDecimal("3.80"), new BigDecimal("2.40"), new BigDecimal("0.85"));
        if (t.contains("magnet") || t.contains("冰箱贴")) return new ProductCost(new BigDecimal("2.60"), new BigDecimal("1.80"), new BigDecimal("0.75"));
        if (t.contains("badge") || t.contains("徽章")) return new ProductCost(new BigDecimal("2.10"), new BigDecimal("1.50"), new BigDecimal("0.60"));
        return new ProductCost(new BigDecimal("8.32"), new BigDecimal("11.40"), new BigDecimal("3.23"));
    }

    private BigDecimal logisticsEstimate(String destination, int qty, String productType) {
        BigDecimal base = new BigDecimal("8");
        String d = destination == null ? "" : destination;
        if (d.contains("新疆") || d.contains("西藏") || d.contains("海南")) base = new BigDecimal("18");
        else if (d.contains("江浙沪") || d.contains("同城") || d.contains("杭州") || d.contains("上海") || d.contains("苏州")) base = new BigDecimal("6");
        BigDecimal factor = productType != null && (productType.contains("帆布") || productType.toLowerCase(Locale.ROOT).contains("canvas")) ? new BigDecimal("0.11") : new BigDecimal("0.035");
        return base.add(BigDecimal.valueOf(qty).multiply(factor)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal setupFee(String type) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.contains("acrylic") || t.contains("亚克力") || t.contains("钥匙扣") || t.contains("徽章")) return new BigDecimal("200");
        if (t.contains("gift") || t.contains("礼盒")) return new BigDecimal("300");
        return new BigDecimal("80");
    }

    private String leadTime(String type, int qty) {
        int days = qty > 1000 ? 12 : qty > 300 ? 8 : 5;
        String t = type == null ? "" : type;
        if (t.contains("亚克力") || t.contains("钥匙扣") || t.contains("徽章")) days += 2;
        return days + "-" + (days + 3) + "天，含确认稿、打样和批量生产；急单需单独确认。";
    }

    private List<String> missingQuoteQuestions(QuoteAssistantRequest req) {
        List<String> q = new ArrayList<>();
        if (blank(req.size)) q.add("请确认尺寸/厚度/是否异形。 ");
        if (blank(req.material)) q.add("请确认材质和表面工艺，如亚克力厚度、纸张克重、是否覆膜。 ");
        if (blank(req.packaging)) q.add("请确认包装方式：裸装、OPP袋、独立纸盒或礼盒。 ");
        if (blank(req.destination)) q.add("请确认收货城市，方便核算物流费用。 ");
        if (req.quantity == null || req.quantity <= 0) q.add("请确认采购数量，不同数量单价差异较大。 ");
        return q;
    }

    private List<String> quoteRisks(QuoteAssistantRequest req, int qty) {
        List<String> r = new ArrayList<>();
        if (qty < 100) r.add("数量偏少，单件分摊设计费/开机费较高。 ");
        if (req.productType != null && (req.productType.contains("礼盒") || req.productType.contains("gift"))) r.add("礼盒类要重点测试物流抗压和包装损耗。 ");
        r.add("涉及客户提供图案时，需确认版权和商用授权。 ");
        r.add("AI报价为预估价，正式报价前应由业务/生产复核。 ");
        return r;
    }

    private String buildQuoteReply(QuoteAssistantRequest req, int qty, BigDecimal price, BigDecimal total, List<String> questions) {
        StringBuilder sb = new StringBuilder();
        sb.append("您好，按您目前需求：").append(req.productName).append("，数量").append(qty).append("件，预估单价约¥").append(price).append("/件，总价约¥").append(total).append("。交期参考：").append(leadTime(req.productType, qty));
        if (!questions.isEmpty()) sb.append("\n为给您准确报价，还需要确认：").append(String.join("", questions));
        sb.append("\n确认设计稿、尺寸、材质、包装和收货地址后，我可以给您正式报价单。 ");
        return sb.toString();
    }

    private List<Map<String, Object>> recentOrders(String orderNo) {
        if (!blank(orderNo)) {
            return jdbc.queryForList("SELECT o.id, o.order_no orderNo, o.pay_amount payAmount, o.order_status orderStatus, o.created_at createdAt FROM `order` o WHERE o.order_no LIKE ? ORDER BY o.id DESC", "%" + orderNo.trim() + "%");
        }
        return jdbc.queryForList("SELECT o.id, o.order_no orderNo, o.pay_amount payAmount, o.order_status orderStatus, o.created_at createdAt FROM `order` o ORDER BY o.id DESC LIMIT 5");
    }

    private int inferQty(List<Map<String, Object>> orders) {
        if (orders.isEmpty()) return 100;
        Object id = orders.get(0).get("id");
        Integer qty = jdbc.queryForObject("SELECT COALESCE(SUM(quantity),0) FROM order_item WHERE order_id=?", Integer.class, id);
        return qty == null || qty <= 0 ? 100 : qty;
    }

    private List<String> logisticsAlerts(int qty, String destination) {
        List<String> list = new ArrayList<>();
        if (qty > 1000) list.add("数量较大，建议分箱发货并做箱号清单。 ");
        if (destination.contains("新疆") || destination.contains("西藏") || destination.contains("海南")) list.add("偏远地区时效波动大，建议提前2-3天发货。 ");
        list.add("易压损产品建议使用加厚纸箱、护角和缓冲材料。 ");
        return list;
    }

    private Map<String, Object> concept(String name, String insight, List<String> skus, String reason) {
        return Map.of("name", name, "insight", insight, "recommendedSkus", skus, "reason", reason);
    }

    private void normalizeQuote(QuoteAssistantRequest req) {
        if (blank(req.productName)) req.productName = "定制文创产品";
        if (blank(req.productType)) req.productType = "明信片/贴纸/亚克力周边";
        if (blank(req.destination)) req.destination = "江浙沪";
    }

    private BigDecimal ratio(BigDecimal value, BigDecimal fallback) {
        if (value == null) return fallback;
        if (value.compareTo(BigDecimal.ONE) > 0) return value.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return value;
    }
    private BigDecimal money(BigDecimal v) { return v == null ? BigDecimal.ZERO : v.setScale(2, RoundingMode.HALF_UP); }
    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private String no(String prefix) { return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(java.time.LocalDateTime.now()) + (int)(Math.random()*900+100); }
    private Long queryLong(String sql) { Long v = jdbc.queryForObject(sql, Long.class); return v == null ? 0L : v; }
    private BigDecimal queryDecimal(String sql) { BigDecimal v = jdbc.queryForObject(sql, BigDecimal.class); return v == null ? BigDecimal.ZERO : v; }

    private record ProductCost(BigDecimal material, BigDecimal process, BigDecimal packageCost) {}

    public static class QuoteAssistantRequest {
        public String productName;
        public String productType;
        public Integer quantity;
        public String size;
        public String material;
        public String packaging;
        public String destination;
        public BigDecimal designFee;
        public BigDecimal overheadRate;
        public BigDecimal targetMarginRate;
    }
    public static class PlanningRequest { public String theme; public String audience; public String channel; public String budget; }
    public static class LogisticsRequest { public String orderNo; public String productType; public Integer quantity; public String destination; }
}
