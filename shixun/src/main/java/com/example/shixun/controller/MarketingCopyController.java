package com.example.shixun.controller;

import com.example.shixun.service.SiliconFlowChatService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/creative/marketing")
public class MarketingCopyController {
    private final SiliconFlowChatService siliconFlow;

    public MarketingCopyController(SiliconFlowChatService siliconFlow) {
        this.siliconFlow = siliconFlow;
    }

    @PostMapping("/copy")
    public Map<String, Object> generateCopy(@RequestBody MarketingCopyRequest req) {
        normalize(req);
        Map<String, Object> fallback = localCopy(req);
        String aiText = tryAi(req);
        Map<String, Object> result = new LinkedHashMap<>(fallback);
        if (aiText != null && !aiText.isBlank()) {
            result.put("aiEnabled", true);
            result.put("aiDraft", aiText.trim());
            result.put("source", "siliconflow:" + siliconFlow.modelName());
        } else {
            result.put("aiEnabled", false);
            result.put("aiDraft", "");
            result.put("source", "local-template");
        }
        result.put("inputs", req);
        return result;
    }

    private void normalize(MarketingCopyRequest req) {
        if (blank(req.brandName)) req.brandName = "之间味道";
        if (blank(req.productName)) req.productName = "城市记忆文创礼盒";
        if (blank(req.productType)) req.productType = "文创产品";
        if (blank(req.audience)) req.audience = "年轻游客、城市礼物购买者、文创爱好者";
        if (blank(req.sellingPoints)) req.sellingPoints = "地域文化、原创设计、适合送礼、质感包装";
        if (blank(req.scenario)) req.scenario = "旅行纪念、节日赠礼、办公桌装饰、伴手礼";
        if (blank(req.channel)) req.channel = "小红书/抖音/朋友圈/详情页";
        if (blank(req.tone)) req.tone = "温暖、有记忆点、有东方生活美学";
        if (blank(req.priceBand)) req.priceBand = "29-99元";
    }

    private Map<String, Object> localCopy(MarketingCopyRequest req) {
        List<String> points = splitPoints(req.sellingPoints);
        String firstPoint = points.isEmpty() ? "原创设计" : points.get(0);
        String secondPoint = points.size() > 1 ? points.get(1) : "适合送礼";
        String thirdPoint = points.size() > 2 ? points.get(2) : "质感包装";

        List<String> titles = List.of(
                req.productName + "｜把" + firstPoint + "带回家",
                "不是普通纪念品，是一份有故事的" + req.productType,
                req.brandName + "新品：适合送人的" + req.productName
        );

        List<String> sellingBullets = List.of(
                "以「" + firstPoint + "」为核心视觉，降低同质化，适合做系列化SKU。",
                "围绕" + req.audience + "的购买动机，突出“好看、好送、好分享”。",
                "适配" + req.scenario + "等场景，可用于礼盒、套装和节日营销。",
                "价格带建议控制在" + req.priceBand + "，便于冲动消费和批量采购。"
        );

        String detailCopy = "这款「" + req.productName + "」来自" + req.brandName + "的原创文创系列。"
                + "设计灵感取自" + firstPoint + "，通过" + secondPoint + "与" + thirdPoint + "形成清晰记忆点。"
                + "它不只是一件" + req.productType + "，更像是一份可以被带走、被收藏、被分享的城市情绪。"
                + "适合" + req.scenario + "，推荐给" + req.audience + "。";

        String xhs = "标题：我找到了一份很会讲故事的" + req.productType + "\n\n"
                + "正文：\n最近看到「" + req.productName + "」，第一眼就被它的" + firstPoint + "吸引。"
                + "它不是那种千篇一律的旅游纪念品，而是把" + req.scenario + "做成了可以日常使用的小物。\n\n"
                + "喜欢它的几个点：\n"
                + "1. " + firstPoint + "，有辨识度\n"
                + "2. " + secondPoint + "，送人不尴尬\n"
                + "3. " + thirdPoint + "，拍照也好看\n\n"
                + "如果你也在找" + req.priceBand + "左右、有质感的文创礼物，可以看看这一款。\n"
                + "#文创礼物 #城市伴手礼 #原创设计 #" + req.brandName;

        String douyin = "镜头1（0-3秒）：快速展示产品细节，字幕：这不是普通纪念品。\n"
                + "镜头2（3-8秒）：切换到使用/送礼场景，旁白：它把" + firstPoint + "做成了一件能带走的" + req.productType + "。\n"
                + "镜头3（8-15秒）：展示包装和材质，字幕：" + secondPoint + " / " + thirdPoint + " / 适合送礼。\n"
                + "镜头4（15-20秒）：产品合集陈列，旁白：送朋友、做伴手礼、自己收藏都刚好。\n"
                + "结尾：评论区告诉我，你想把哪座城市做成文创？";

        String customerReply = "您好，这款「" + req.productName + "」主打" + req.sellingPoints + "，适合" + req.scenario + "。"
                + "目前建议价格带是" + req.priceBand + "，如果您需要定制LOGO、包装或批量采购，我可以继续帮您确认数量、工艺和交期后给到准确报价。";

        List<String> hashtags = List.of("#文创产品", "#原创设计", "#城市礼物", "#伴手礼", "#" + req.brandName);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("titles", titles);
        map.put("sellingBullets", sellingBullets);
        map.put("detailCopy", detailCopy);
        map.put("xiaohongshuNote", xhs);
        map.put("douyinScript", douyin);
        map.put("customerReply", customerReply);
        map.put("hashtags", hashtags);
        map.put("nextActions", List.of("补充产品图片后生成详情页配图文案", "进入生产管理生成BOM和成本报价", "将表现好的标题沉淀为品牌话术库"));
        return map;
    }

    private String tryAi(MarketingCopyRequest req) {
        try {
            return siliconFlow.chat(
                    "你是资深文创电商营销策划，擅长把原创设计、城市文化和产品卖点转化为可直接发布的中文营销文案。请避免夸大承诺和侵权IP表达。",
                    buildPrompt(req),
                    0.7,
                    1200,
                    45
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildPrompt(MarketingCopyRequest req) {
        return "请为文创产品生成营销文案，要求结构清晰、可直接复制使用。\n"
                + "品牌：" + req.brandName + "\n"
                + "产品名：" + req.productName + "\n"
                + "产品类型：" + req.productType + "\n"
                + "目标人群：" + req.audience + "\n"
                + "核心卖点：" + req.sellingPoints + "\n"
                + "使用/购买场景：" + req.scenario + "\n"
                + "价格带：" + req.priceBand + "\n"
                + "投放渠道：" + req.channel + "\n"
                + "语气：" + req.tone + "\n\n"
                + "请输出：1）商品标题5个；2）一句话卖点；3）详情页文案；4）小红书笔记；5）抖音短视频脚本；6）客服回复话术；7）风险提醒。";
    }

    private List<String> splitPoints(String text) {
        if (text == null) return List.of();
        return Arrays.stream(text.split("[,，、/；;\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(5)
                .toList();
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }

    public static class MarketingCopyRequest {
        public String brandName;
        public String productName;
        public String productType;
        public String audience;
        public String sellingPoints;
        public String scenario;
        public String priceBand;
        public String channel;
        public String tone;
        public Boolean useAi;

        public String getBrandName() { return brandName; }
        public String getProductName() { return productName; }
        public String getProductType() { return productType; }
        public String getAudience() { return audience; }
        public String getSellingPoints() { return sellingPoints; }
        public String getScenario() { return scenario; }
        public String getPriceBand() { return priceBand; }
        public String getChannel() { return channel; }
        public String getTone() { return tone; }
        public Boolean getUseAi() { return useAi; }
    }
}
