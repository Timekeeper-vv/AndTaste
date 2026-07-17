package com.example.shixun.controller;

import com.example.shixun.service.SiliconFlowChatService;
import com.example.shixun.service.SupplierTextToApiService;
import com.example.shixun.service.WebSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiChatController {
    private final SiliconFlowChatService siliconFlow;
    private final JdbcTemplate jdbc;
    private final WebSearchService webSearch;
    private final SupplierTextToApiService supplierTextToApi;

    private static final String SYSTEM_PROMPT =
            "你是“之间味道-文创产品智能体平台”的AI业务助手，专注于文创产品业务。" +
            "你可以帮助用户做：文创选品、设计企划、营销文案、客户询盘回复、报价思路、打样流程、生产BOM、物流履约、经营分析。" +
            "你还熟悉本系统全部左侧菜单、申请表单、审批流和供应商列表。" +
            "回答要务实、简洁、可执行。涉及付款、报价、合同、版权、承诺交期时必须提醒人工复核；避免建议使用未授权IP或明显模仿知名品牌/角色。" +
            "如果用户问某个申请怎么做，必须回答：入口菜单、需要填写的关键字段、提交后去哪里审批、注意事项。" +
            "由于前端聊天窗口较小，默认使用纯文字短段落回答，避免Markdown表格、竖线表格、复杂符号和过长列表。";

    private static final List<FormDoc> FORM_DOCS = List.of(
            new FormDoc("创意设计 / 2D创意生图", "创意设计 > 2D创意生图", "填写创意描述、风格、图片参数后生成图片资产。", "创意描述、风格、尺寸、参考图、负向提示词", "适合先做产品视觉、IP图、包装概念图。生成内容需人工确认版权和商业合规。"),
            new FormDoc("创意设计 / 3D辅助建模", "创意设计 > 3D辅助建模", "选择 Tripo 模型，支持单图生3D、多视图生3D、文生3D。", "模型系列、生成模式、参考图或Prompt、纹理、面数、UV、种子", "P系列适合低面数资产，H系列适合高精度展示。生成后模型保存到资产库。"),
            new FormDoc("智能评估", "创意设计 > 智能评估", "上传产品图或方案，填写销售、材质、场景信息后做商业可行性评估。", "目标人群、价格、材质、销售场景、生产要求", "评估结果用于方案修改，不能替代最终商业决策。"),

            new FormDoc("创建项目", "生产管理 > 创建项目", "先创建产品项目并生成基础BOM，再进入智能成本核算。", "项目/产品名称、产品类型、计划数量、目标售价、客户、联系人、地址、项目要求", "创建后到“智能成本核算引擎”调整物料工艺、生成报价和订单。"),
            new FormDoc("智能成本核算引擎", "生产管理 > 智能成本核算引擎", "选择项目BOM，调整物料、工艺、数量、毛利率，核算打样/大货预算并生成订单。", "项目BOM、数量、目标毛利、成交单价、客户、联系人、收货地址、生产要求", "先从“创建项目”进入；订单确认和下达生产需要审批中心审批。"),
            new FormDoc("打样申请", "生产管理 > 打样申请", "新增、编辑、删除打样单，并提交审批中心。审批通过后打样工单状态会自动变为待打样，后续可更新为进行中、已完成、延期完成或项目暂停。", "申请人、申请部门、项目名称、产品名称、订单类型、产品类型、二级类型、打样数量、规格/口味、打样费、负责人、工厂、预计完成时间、备注", "草稿可修改；提交后进入审批中心；审批通过/驳回会自动回写到打样工单明细。"),
            new FormDoc("产品打样管理", "生产管理 > 产品打样管理", "查看打样订单状态，提交确认/下达审批，跟踪生产到待发货。", "订单号、产品、数量、报价、审批状态", "确认订单、下达生产需先提交审批申请。"),
            new FormDoc("大货生产管理", "生产管理 > 大货生产管理", "查看大货订单、生产任务、采购准备和完工待发货状态。", "订单号、产品、数量、报价、审批状态", "生产完工后到物流跟踪绑定运单。"),

            new FormDoc("供应商列表", "供应商列表", "查询供应商对公账户、银行账号、开户行和所在地。", "收方编号、供应商、户名、银行账号、银行、开户行、所在地", "支持模糊查询、银行筛选、地区筛选、导出CSV。付款前务必核验户名和账号一致性。天津华明乳业信息需二次核实。"),
            new FormDoc("供应链打样工单明细", "供应链打样工单明细", "查看已导入的2026打样申请工作表明细。原表155行含表头，系统保存154条业务明细，并保留34列原始数据、SourceID和行校验和。", "申请编号、项目名称、产品名称、订单类型、产品类型、打样数量、工单状态、开始时间、预计完成时间、实际完成时间、负责人、工厂、打样费用、SourceID", "支持按申请编号、项目、产品、负责人、产品类型、SourceID查询筛选；点击查看原始列可核对Excel 34列。"),

            new FormDoc("营销宣传申请", "市场部需求管理 > 营销宣传申请", "申请活动推广、节日营销、渠道投放、物料制作。", "需求名称、宣传主题、投放渠道、目标人群、上线时间、预算金额、所需物料、预期效果", "提交后进入审批中心。"),
            new FormDoc("电商新品上架申请", "市场部需求管理 > 电商新品上架申请", "申请电商平台新品上架资料、价格、库存、详情页和资源位。", "商品名称、SKU、上架平台、商品类目、建议售价、首批库存、卖点摘要、计划上架时间", "价格库存需和供应链确认。"),
            new FormDoc("拍摄需求申请", "市场部需求管理 > 拍摄需求申请", "申请产品图、场景图、短视频、直播素材等拍摄排期。", "拍摄项目、产品/主题、拍摄类型、使用渠道、交付时间、地点、参与人员、参考风格", "尽量提供参考图或脚本方向。"),
            new FormDoc("产品宣传文案", "市场部需求管理 > 产品宣传文案", "生成标题、卖点、详情页、小红书、抖音脚本和客服回复。", "品牌名、产品名称、产品类型、目标人群、核心卖点、价格带、投放渠道、语气风格", "生成后需人工审核价格、承诺、版权/IP表达。"),

            new FormDoc("项目立项申请", "项目部需求管理 > 项目立项申请", "申请新项目启动、目标范围、预算周期和资源需求。", "项目名称、项目编号、项目负责人、客户/部门、项目背景、项目目标、计划周期、预算金额、资源需求", "写清项目目标、范围和交付物。"),
            new FormDoc("项目询价申请", "项目部需求管理 > 项目询价申请", "申请项目物料、工艺、服务、供应商报价。", "询价项目、需求品类、规格参数、预计数量、期望交期、目标价格、候选供应商、报价用途、联系人", "规格、数量、交期越明确，报价越准确。"),

            new FormDoc("新产品开发激励", "人力资源管理 > 新产品开发激励", "申请新产品开发贡献激励。", "激励项目/产品、申请部门、参与人员、贡献说明、成果产出、建议激励金额、完成时间、证明材料", "金额需说明依据。"),
            new FormDoc("离职申请", "人力资源管理 > 离职申请", "申请员工离职和工作交接。", "离职人、部门、岗位、入职日期、拟离职日期、离职原因、交接人、交接事项", "写清最后工作日和资产归还。"),
            new FormDoc("培训申请", "人力资源管理 > 培训申请", "申请内部/外部培训。", "培训名称、类型、参训人员、机构/讲师、时间、地点、费用、目标", "外部培训需说明费用明细。"),
            new FormDoc("加班申请【法定节假日】", "人力资源管理 > 加班申请【法定节假日】", "申请法定节假日加班。", "加班人员、部门、日期、时段、地点、原因、工作内容、补偿方式", "仅用于法定节假日加班。"),
            new FormDoc("调岗申请", "人力资源管理 > 调岗申请", "申请员工岗位调整。", "员工姓名、当前部门、当前岗位、调入部门、调入岗位、调岗原因、生效日期、交接安排", "明确调岗前后岗位。"),
            new FormDoc("制度&方案审批", "人力资源管理 > 制度&方案审批", "审批人事制度、管理方案、激励方案等文件。", "制度/方案名称、发起部门、适用范围、生效时间、核心内容、修订原因、影响说明、附件清单", "写清适用范围和生效时间。"),
            new FormDoc("转正申请", "人力资源管理 > 转正申请", "申请试用期员工转正。", "员工姓名、部门、岗位、入职日期、拟转正日期、试用期表现、直属上级评价、转正建议", "如涉及薪酬调整需备注。"),
            new FormDoc("招聘申请", "人力资源管理 > 招聘申请", "申请新增/补缺岗位招聘。", "招聘岗位、部门、人数、原因、岗位职责、任职要求、期望到岗时间、薪资范围", "明确新增或补缺原因。"),

            new FormDoc("补卡申请", "考勤管理 > 补卡申请", "申请漏打卡、忘打卡、设备异常补卡。", "申请人、部门、补卡日期、补卡时间、补卡类型、异常原因、证明人、备注", "补卡原因需真实可核验。"),
            new FormDoc("请假申请", "考勤管理 > 请假申请", "申请事假、病假、年假、调休等。", "请假人、部门、请假类型、开始时间、结束时间、请假天数/小时、请假原因、工作交接人", "写清工作交接，病假按制度补证明。"),
            new FormDoc("出差申请", "考勤管理 > 出差申请", "申请因公出差行程、目的和预算。", "出差人、部门、地点、事由、出发时间、返回时间、预计费用、同行人员", "预计费用需提前测算。"),
            new FormDoc("外出申请", "考勤管理 > 外出申请", "申请工作时间临时外出、客户拜访、办事等。", "外出人、部门、事由、地点、开始时间、结束时间、联系人/客户、交通方式", "填写预计返回时间。"),

            new FormDoc("门店营销方案申请【连锁】", "之间连锁 > 门店营销方案申请【连锁】", "申请连锁门店活动、节日促销、社群推广、陈列物料。", "申请门店、活动名称、活动时间、预算金额、营销方案、预期效果", "提交后进入审批中心。"),
            new FormDoc("新商品上架申请【连锁】", "之间连锁 > 新商品上架申请【连锁】", "申请连锁门店新品上架。", "商品名称、商品类别、建议售价、适用门店、上架原因、铺货计划", "提交后进入审批中心。"),
            new FormDoc("商品售价调整申请【连锁】", "之间连锁 > 商品售价调整申请【连锁】", "申请连锁商品售价调整。", "商品名称、当前售价、调整后售价、生效时间、影响门店、调价原因", "提交后进入审批中心。"),

            new FormDoc("财务管理", "财务管理", "包含固定资产报废、对公付款、备用金、报销、促销审批、用章、开票等申请。", "按具体财务子菜单填写", "财务类提交后进入审批中心。")
    );

    private static final List<SupplierDoc> SUPPLIERS = List.of(
            new SupplierDoc("2025051500575","深圳市星米三维科技有限公司","对公账户","深圳市星米三维科技有限公司","755958121410901","招商银行","招商银行有限公司深圳布吉支行","深圳市",""),
            new SupplierDoc("2024082100473","秦皇岛轩阳贸易有限公司","对公账户","秦皇岛轩阳贸易有限公司","50813001040028701","中国农业银行秦皇岛港城支行","秦皇岛港城支行","河北省秦皇岛",""),
            new SupplierDoc("2024081500471","山东珂芮慕斯食品有限公司","对公账户","山东珂芮慕斯食品有限公司","531906954310908","招商银行股份有限公司","济南工业南路支行","山东省济南市",""),
            new SupplierDoc("2024080200468","武汉扬子江普啦啦食品有限公司","对公账户","武汉扬子江普啦啦食品有限公司","8111501013401163157","中信银行股份有限公司武汉江夏支行","中信银行股份有限公司","武汉",""),
            new SupplierDoc("2024080100465","天津华明乳业有限公司","对公账户","天津华明乳业有限公司","817980001421010192","威海银行股份有限公司","威海银行股份有限公司","天津","原始数据中“银行”字段为“w”，已按上下文推断为威海银行股份有限公司；付款前务必二次核实。"),
            new SupplierDoc("2024062500444","海城市金城果糖厂","对公账户","海城市金城果糖厂","241311950010251030380","交通银行鞍山海城支行","交通银行","辽宁省海城市",""),
            new SupplierDoc("2024062500441","浙江冰富生物科技有限公司","对公账户","浙江冰富生物科技有限公司","584715957600015","浙江民泰商业银行股份有限公司湖州练市小微综合支行","浙江民泰商业银行股份有限公司","浙江省湖州市",""),
            new SupplierDoc("2024061300434","佛山市恒邦达新材料科技有限公司","对公账户","佛山市恒邦达新材料科技有限公司","2013016809200067848","中国工商银行","中国工商银行股份有限公司佛山南海中海万锦支行","广东佛山",""),
            new SupplierDoc("2024061100428","上海方棱轻工机械厂","对公账户","上海方棱轻工机械厂","31001972000055657058","中国建设银行","上海星火支行","上海市",""),
            new SupplierDoc("2024060600427","英唯奕(上海)餐饮管理有限公司","对公账户","英唯奕(上海)餐饮管理有限公司","31050136360000001091","中国建设银行股份有限公司","上海市分行","上海市",""),
            new SupplierDoc("2024060600426","厚得（广东）生物科技有限公司","对公账户","厚得（广东）生物科技有限公司","3602886609100274693","中国工商银行股份有限公司","广州增城开发区支行","广东省广州市",""),
            new SupplierDoc("2024053100420","福州中商贸易有限公司","对公账户","福州中商贸易有限公司","631890227","民生银行","中国民生银行股份有限公司福州广达支行","福建省福州市",""),
            new SupplierDoc("2024052800419","秦皇岛鹏泽糖业有限公司","对公账户","秦皇岛鹏泽糖业有限公司","50825001040002892","中国农业银行","昌黎龙家店分理处","河北秦皇岛",""),
            new SupplierDoc("2024052000413","东莞市宸宇包装有限公司","对公账户","东莞市宸宇包装有限公司","44050177623800000656","中国建设银行","中国建设银行股份有限公司东莞东宝路支行","广东省东莞市",""),
            new SupplierDoc("2024052000412","天津市佳越商贸有限公司","对公账户","天津市佳越商贸有限公司","271360075117","中国银行","中国银行股份有限公司天津北宁支行","天津市",""),
            new SupplierDoc("2024052000411","广州恒生包装制品有限公司","对公账户","广州恒生包装制品有限公司","3602864309100122837","中国工商银行","中国工商银行股份有限公司广州晓港支行","广东省广州市",""),
            new SupplierDoc("2024052000410","佛山市展智鸿货架有限公司","对公账户","佛山市展智鸿货架有限公司","2013026709200043239","中国工商银行","中国工商银行股份有限公司佛山分行","广东省佛山市",""),
            new SupplierDoc("2024052000409","华测检测认证集团北京有限公司","对公账户","华测检测认证集团北京有限公司","999012825710506","招商银行","招商银行北京亦庄支行","北京市朝阳区",""),
            new SupplierDoc("2024052000408","广东元宇火光印刷有限公司","对公账户","广东元宇火光印刷有限公司","2004023009200126778","中国工商银行","中国工商银行股份有限公司潮安支行","广东省潮州市",""),
            new SupplierDoc("2024052000407","青岛益美鑫包装科技有限公司","对公账户","青岛益美鑫包装科技有限公司","37150198691000004147","中国建设银行","中国建设银行青岛中山路支行","山东省青岛市","")
    );

    public AiChatController(SiliconFlowChatService siliconFlow, JdbcTemplate jdbc, WebSearchService webSearch, SupplierTextToApiService supplierTextToApi) {
        this.siliconFlow = siliconFlow;
        this.jdbc = jdbc;
        this.webSearch = webSearch;
        this.supplierTextToApi = supplierTextToApi;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String userMessage = (String) body.get("message");
        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "消息不能为空"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());
        String historyText = history.stream()
                .limit(12)
                .map(m -> ("assistant".equals(m.get("role")) ? "助手" : "用户") + "：" + m.getOrDefault("content", ""))
                .collect(Collectors.joining("\n"));

        var supplierToolAnswer = supplierTextToApi.tryAnswer(userMessage);
        if (supplierToolAnswer.isPresent()) {
            var answer = supplierToolAnswer.get();
            return ResponseEntity.ok(Map.of(
                    "reply", answer.reply(),
                    "source", answer.source(),
                    "tool", answer.toolName(),
                    "toolArguments", answer.toolArguments(),
                    "toolResult", answer.toolResult()
            ));
        }

        String context = retrieveKnowledge(userMessage);
        if (context.startsWith("【供应商实时查询结果】") || context.startsWith("【供应商实时汇总】")) {
            if (needsSupplierExternalAnalysis(userMessage)) {
                try {
                    String reply = supplierHybridAnalysis(userMessage, context, historyText);
                    return ResponseEntity.ok(Map.of("reply", reply, "source", "mysql+web+siliconflow:" + siliconFlow.modelName(), "webSearch", true));
                } catch (Exception e) {
                    return ResponseEntity.ok(Map.of("reply", supplierHybridFallback(userMessage, context, e.getMessage()), "source", "mysql+web-fallback", "webSearch", true, "warning", e.getMessage()));
                }
            }
            // 供应商/银行账户类问题必须用数据库实时结果确定性回答，避免大模型编造或漏报。
            return ResponseEntity.ok(Map.of("reply", supplierDirectAnswer(context), "source", "mysql:supplier_bank_accounts"));
        }
        Object currentUser = body.get("currentUser");
        String webContext = "";
        boolean usedWeb = shouldWebSearch(userMessage);
        if (usedWeb) {
            try {
                webContext = webSearch.formatResults(webSearch.search(cleanSearchQuery(userMessage), 5));
            } catch (Exception e) {
                webContext = "【联网搜索结果】搜索失败：" + e.getMessage();
            }
        }
        String prompt = (historyText.isBlank() ? "" : "历史对话：\n" + historyText + "\n\n")
                + "当前登录用户：" + (currentUser == null ? "未知" : currentUser) + "\n\n"
                + "以下是本系统可用菜单、表单和流程知识库片段，请优先根据这些内容回答：\n"
                + context + "\n\n"
                + (webContext.isBlank() ? "" : "以下是刚刚联网搜索得到的结果。回答涉及外部事实、新闻、价格、政策、官网信息时，必须优先依据这些结果，并在末尾列出参考链接：\n" + webContext + "\n\n")
                + "当前问题：\n" + userMessage;
        try {
            String reply = siliconFlow.chat(SYSTEM_PROMPT + (usedWeb ? "你具备联网搜索工具。使用搜索结果回答时要说明来源，不能把未搜索到的信息当事实。" : ""), prompt, 0.45, usedWeb ? 1800 : 1200, usedWeb ? 30 : 20);
            return ResponseEntity.ok(Map.of("reply", reply, "source", (usedWeb ? "siliconflow+web:" : "siliconflow:") + siliconFlow.modelName(), "webSearch", usedWeb));
        } catch (Exception e) {
            String fallback = usedWeb && !webContext.isBlank()
                    ? fallbackWebAnswer(userMessage, context, webContext)
                    : fallbackAnswer(userMessage, context);
            return ResponseEntity.ok(Map.of("reply", fallback, "source", usedWeb ? "web-search-fallback" : "local-form-kb", "warning", "大模型不可用，已使用本地能力回答：" + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(@RequestParam String q) {
        try {
            var results = webSearch.search(q, 8);
            return ResponseEntity.ok(Map.of("query", q, "results", results, "text", webSearch.formatResults(results)));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("query", q, "results", List.of(), "error", e.getMessage()));
        }
    }

    private boolean shouldWebSearch(String question) {
        String q = question == null ? "" : question.trim().toLowerCase();
        return q.contains("联网") || q.contains("搜索") || q.contains("搜一下") || q.contains("查一下") || q.contains("百度") || q.contains("网上") ||
                q.contains("新闻") || q.contains("最新") || q.contains("今天") || q.contains("现在") || q.contains("实时") || q.contains("官网") ||
                q.contains("价格") || q.contains("行情") || q.contains("政策") || q.contains("法规") || q.contains("竞品") || q.contains("趋势") ||
                q.contains("2026") || q.contains("2025");
    }

    private String cleanSearchQuery(String question) {
        String q = question == null ? "" : question.trim();
        return q.replace("帮我", "").replace("请", "")
                .replace("联网搜索", "").replace("联网查", "")
                .replace("搜索一下", "").replace("搜一下", "")
                .replace("查一下", "").trim();
    }

    private String fallbackWebAnswer(String question, String context, String webContext) {
        return "我已执行联网搜索，但大模型暂时不可用。下面是可用的搜索结果，你可以点链接查看：\n\n" + webContext
                + (context == null || context.isBlank() ? "" : "\n\n系统内知识库匹配：\n" + context);
    }

    private boolean needsSupplierExternalAnalysis(String question) {
        String q = question == null ? "" : question.toLowerCase();
        return q.contains("优劣") || q.contains("优势") || q.contains("劣势") || q.contains("风险")
                || q.contains("评价") || q.contains("评估") || q.contains("分析") || q.contains("对比")
                || q.contains("建议") || q.contains("推荐") || q.contains("怎么样") || q.contains("好不好")
                || q.contains("靠不靠谱") || q.contains("舆情") || q.contains("工商") || q.contains("诉讼")
                || q.contains("经营异常");
    }

    private String supplierHybridAnalysis(String question, String dbContext, String historyText) throws Exception {
        List<SupplierDoc> suppliers = matchedSuppliersForQuestion(question);
        String internalFacts = formatSupplierInternalFacts(suppliers);
        String webFacts = buildSupplierWebFacts(suppliers);
        String prompt = (historyText == null || historyText.isBlank() ? "" : "历史对话：\n" + historyText + "\n\n")
                + "当前日期：" + LocalDate.now() + "\n"
                + "用户问题：" + question + "\n\n"
                + "【内部事实：来自本系统 MySQL supplier_bank_accounts 实时台账，是确定事实】\n"
                + internalFacts + "\n\n"
                + "【外部情报：刚刚按供应商全称逐个联网搜索得到，可能存在同名/过期/噪声，必须谨慎使用】\n"
                + webFacts + "\n\n"
                + "请按以下四段式输出，禁止自由发挥。重要：聊天窗口很小，禁止使用Markdown表格、竖线表格、竖线分隔符、星号加粗、复杂符号；不要使用“|”或“｜”；必须用纯文字、短段落、编号列表输出：\n"
                + "一、核心结论：直接说明当前系统内匹配供应商数量、区域分布、总体判断；只能说“系统台账存在这些供应商记录”，不得据此推断“合法注册/资质良好”。没有明确外部负面证据时要说“暂未从本次搜索结果中发现明确公开负面信息”，不能编造。\n"
                + "二、内部数据事实：只展示关键字段；银行账号必须脱敏；按城市/地区归类；注明“截至当前实时查询”。每家供应商用一到两行自然文字，不要表格，不要竖线分隔符。\n"
                + "三、外部情报增强：逐家用“供应商名：优势...；风险...；来源...”格式输出，不要表格。"
                + "优势和风险必须来源于搜索结果标题/摘要/URL；信息源/时效列必须给出实际URL或搜索结果标题。若没有明确URL/摘要证据，必须写“未检索到明确公开来源”。如果搜索结果没有明确提到注册资本、诉讼、行政处罚、纳税等级、成立年限等，不得编造，写“未检索到明确公开信息”。不得凭常识写“产业带优势/政策扶持/合法注册/经营稳定”。\n"
                + "四、行动建议与免责声明：给采购/财务可执行动作，并声明内部台账实时准确，外部公开信息仅辅助参考，重大决策需人工复核/验厂/法务审查。若提到系统入口，只能写已有菜单“供应商列表”，不得编造“供应商管理”等不存在菜单。\n"
                + "特别要求：先内后外；严禁把外部同名企业信息直接当成内部供应商事实；每条外部判断必须带来源链接或来源名称。";
        return siliconFlow.chat(SYSTEM_PROMPT + "当问题同时涉及内部供应商数据和优劣/风险分析时，必须采用“结论-内部事实-外部情报-行动建议”四段式结构，严格区分内部事实与外部公开信息。禁止使用Markdown表格、竖线分隔符和复杂格式，输出适配小聊天窗口。", prompt, 0.25, 2200, 90);
    }

    private List<SupplierDoc> matchedSuppliersForQuestion(String question) {
        String q = question == null ? "" : question.toLowerCase();
        List<SupplierDoc> all;
        try {
            all = loadSuppliersFromDb();
        } catch (Exception e) {
            all = SUPPLIERS;
        }
        List<SupplierDoc> matched = all.stream().filter(s -> supplierMatches(q, s)).toList();
        if (!matched.isEmpty()) return matched;
        if (q.contains("供应商") && (q.contains("全部") || q.contains("所有") || q.contains("列表"))) return all;
        return matched;
    }

    private String formatSupplierInternalFacts(List<SupplierDoc> suppliers) {
        if (suppliers == null || suppliers.isEmpty()) return "未匹配到供应商。";
        StringBuilder sb = new StringBuilder("当前实时匹配供应商共 " + suppliers.size() + " 家：\n");
        for (int i = 0; i < suppliers.size(); i++) {
            SupplierDoc s = suppliers.get(i);
            sb.append(i + 1).append("、").append(s.supplier())
                    .append("，所在地：").append(cityLabel(s.location()))
                    .append("，收方编号：").append(s.receiverNo())
                    .append("，户名：").append(s.accountName())
                    .append("，银行账号：").append(maskAccount(s.bankAccount()))
                    .append("，银行：").append(s.bank())
                    .append("，开户行：").append(s.branch());
            if (s.note() != null && !s.note().isBlank()) {
                sb.append("，备注：").append(s.note());
            }
            if (i < suppliers.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private String buildSupplierWebFacts(List<SupplierDoc> suppliers) {
        if (suppliers == null || suppliers.isEmpty()) return "无供应商，未执行联网搜索。";
        StringBuilder sb = new StringBuilder();
        for (SupplierDoc s : suppliers.stream().limit(8).toList()) {
            sb.append("【").append(s.supplier()).append("】\n");
            try {
                List<WebSearchService.SearchResult> results = webSearch.search(s.supplier() + " 经营异常 诉讼 行政处罚 官网 企业信用", 2);
                if (results.isEmpty()) {
                    sb.append("未检索到明确公开结果。\n");
                } else {
                    for (int i = 0; i < results.size(); i++) {
                        WebSearchService.SearchResult r = results.get(i);
                        sb.append(i + 1).append(". ").append(r.title()).append("\n")
                                .append("   链接：").append(r.url()).append("\n");
                        if (r.snippet() != null && !r.snippet().isBlank()) {
                            sb.append("   摘要：").append(r.snippet()).append("\n");
                        }
                    }
                }
            } catch (Exception e) {
                sb.append("搜索失败：").append(e.getMessage()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private String supplierHybridFallback(String question, String dbContext, String error) {
        return "一、核心结论\n"
                + "我已先实时查询内部供应商台账，但大模型或联网综合分析暂时失败：" + error + "\n\n"
                + "二、内部数据事实\n"
                + dbContext.replaceAll("账号：([0-9A-Za-z]{4})[0-9A-Za-z]+([0-9A-Za-z]{4})", "账号：$1 **** **** $2") + "\n\n"
                + "三、外部情报增强\n"
                + "本次未能完成可靠的大模型综合解读，不能编造供应商优劣。请稍后重试，或逐个供应商执行公开工商/诉讼/行政处罚查询。\n\n"
                + "四、行动建议与免责声明\n"
                + "建议采购/财务对匹配供应商进行人工复核：工商状态、经营异常、司法诉讼、行政处罚、开户行一致性、历史履约记录。内部台账为实时数据；外部公开信息仅作辅助，不构成最终准入/淘汰依据。";
    }

    private String maskAccount(String account) {
        if (account == null || account.length() <= 8) return account == null ? "" : account;
        return account.substring(0, 4) + " **** **** " + account.substring(account.length() - 4);
    }

    private String cityLabel(String location) {
        if (location == null || location.isBlank()) return "未知地区";
        if (location.contains("广州")) return "广州";
        if (location.contains("佛山")) return "佛山";
        if (location.contains("东莞")) return "东莞";
        if (location.contains("潮州")) return "潮州";
        if (location.contains("深圳")) return "深圳";
        if (location.contains("广东")) return "广东";
        return location;
    }

    private String retrieveKnowledge(String query) {
        String q = query == null ? "" : query.toLowerCase();
        List<FormDoc> matched = new ArrayList<>();
        for (FormDoc doc : FORM_DOCS) {
            String text = (doc.name + " " + doc.menu + " " + doc.summary + " " + doc.fields + " " + doc.tips).toLowerCase();
            if (q.isBlank() || text.contains(q) || containsAny(q, doc.name, doc.menu, doc.summary, doc.fields)) matched.add(doc);
        }
        String formKnowledge = "";
        if (matched.isEmpty()) matched = FORM_DOCS.stream().limit(8).toList();
        formKnowledge = matched.stream().limit(10).map(FormDoc::format).collect(Collectors.joining("\n\n"));
        return formKnowledge.trim();
    }

    private String retrieveSupplierKnowledge(String q) {
        List<SupplierDoc> all;
        try {
            all = loadSuppliersFromDb();
        } catch (Exception e) {
            all = SUPPLIERS;
        }
        boolean likelySupplierQuestion = q.contains("供应商") || q.contains("收方") || q.contains("银行") || q.contains("账号") || q.contains("账户") || q.contains("开户行") || q.contains("付款") || q.contains("信息");
        List<SupplierDoc> matched = all.stream().filter(s -> supplierMatches(q, s)).toList();

        boolean askAllSuppliers = likelySupplierQuestion && matched.isEmpty()
                && (q.contains("供应商") && (q.contains("几个") || q.contains("多少") || q.contains("全部") || q.contains("列表") || q.contains("所有")));
        if (askAllSuppliers) {
            return "【供应商实时汇总】当前数据库 supplier_bank_accounts 表共 " + all.size() + " 个供应商。\n"
                    + all.stream().limit(50).map(SupplierDoc::format).collect(Collectors.joining("\n"));
        }
        if (matched.isEmpty()) {
            return likelySupplierQuestion ? "【供应商实时查询结果】未在当前数据库 supplier_bank_accounts 表中匹配到相关记录。当前供应商列表共 " + all.size() + " 条，可按供应商名称、地区、银行、账号、收方编号查询。" : "";
        }
        return "【供应商实时查询结果】从数据库 supplier_bank_accounts 表实时查询，共匹配 " + matched.size() + " 个供应商：\n"
                + matched.stream().limit(20).map(SupplierDoc::format).collect(Collectors.joining("\n"));
    }

    private List<SupplierDoc> loadSuppliersFromDb() {
        String sql = "SELECT receiver_no, supplier, account_type, account_name, bank_account, bank, branch, location, note " +
                "FROM supplier_bank_accounts ORDER BY id DESC LIMIT 500";
        return jdbc.query(sql, (rs, i) -> new SupplierDoc(
                rs.getString("receiver_no"),
                rs.getString("supplier"),
                rs.getString("account_type"),
                rs.getString("account_name"),
                rs.getString("bank_account"),
                rs.getString("bank"),
                rs.getString("branch"),
                rs.getString("location"),
                rs.getString("note")
        ));
    }

    private String supplierDirectAnswer(String context) {
        return context + "\n\n说明：以上是我刚刚从数据库 supplier_bank_accounts 实时查询到的真实表单数据；之后你在供应商列表新增、删除的数据，AI 助手都会按当前数据库实时结果回答。付款前仍建议人工核验户名、账号和开户行。";
    }

    private boolean supplierMatches(String q, SupplierDoc s) {
        if (q == null || q.isBlank()) return false;
        String text = s.searchText().toLowerCase();
        if (text.contains(q) || q.contains(s.receiverNo()) || q.contains(s.supplier().toLowerCase()) || q.contains(s.bankAccount())) return true;
        for (String token : supplierTokens(s)) {
            if (token.length() >= 2 && q.contains(token.toLowerCase())) return true;
        }
        return false;
    }

    private List<String> supplierTokens(SupplierDoc s) {
        List<String> tokens = new ArrayList<>();
        tokens.add(s.receiverNo());
        tokens.add(s.supplier());
        tokens.add(s.accountName());
        tokens.add(s.bankAccount());
        tokens.add(s.bank());
        tokens.add(s.branch());
        tokens.add(s.location());
        tokens.add(s.location().replace("省", "").replace("市", ""));
        if (s.location().contains("秦皇岛")) tokens.add("秦皇岛");
        if (s.location().contains("广东")) tokens.add("广东");
        if (s.location().contains("上海")) tokens.add("上海");
        if (s.location().contains("天津")) tokens.add("天津");
        if (s.location().contains("山东")) tokens.add("山东");
        if (s.location().contains("河北")) tokens.add("河北");
        if (s.location().contains("浙江")) tokens.add("浙江");
        if (s.location().contains("福建")) tokens.add("福建");
        if (s.location().contains("辽宁")) tokens.add("辽宁");
        if (s.bank().contains("工商")) tokens.add("工商银行");
        if (s.bank().contains("建设")) tokens.add("建设银行");
        if (s.bank().contains("招商")) tokens.add("招商银行");
        if (s.bank().contains("农业")) tokens.add("农业银行");
        if (s.bank().contains("中信")) tokens.add("中信银行");
        if (s.bank().contains("民生")) tokens.add("民生银行");
        if (s.bank().contains("交通")) tokens.add("交通银行");
        return tokens;
    }

    private boolean containsAny(String q, String... values) {
        for (String v : values) {
            if (v == null) continue;
            for (String part : v.split("[ >/、，,：:【】()（）&]+")) {
                String p = part.trim().toLowerCase();
                if (p.length() >= 2 && q.contains(p)) return true;
            }
        }
        return false;
    }

    private String fallbackAnswer(String question, String context) {
        return "我先按系统当前知识库给你回答：\n\n" + context
                + "\n\n操作原则：在左侧菜单进入对应表单，按字段填写后提交；申请类表单会进入“审批中心”，由超级管理员或审批主管处理。"
                + "\n如果你要问更具体的流程，可以直接问：例如“请假申请怎么填”“项目询价在哪”“供应商账号怎么查”。";
    }

    private record FormDoc(String name, String menu, String summary, String fields, String tips) {
        String format() {
            return "【" + name + "】\n入口：" + menu + "\n用途：" + summary + "\n关键字段：" + fields + "\n注意事项：" + tips;
        }
    }

    private record SupplierDoc(String receiverNo, String supplier, String accountType, String accountName, String bankAccount, String bank, String branch, String location, String note) {
        String searchText() {
            return String.join(" ", receiverNo, supplier, accountType, accountName, bankAccount, bank, branch, location, note == null ? "" : note);
        }
        String format() {
            return "- 收方编号：" + receiverNo + "\n  供应商：" + supplier + "\n  户名：" + accountName + "\n  账号：" + bankAccount + "\n  银行：" + bank + "\n  开户行：" + branch + "\n  所在地：" + location + (note == null || note.isBlank() ? "" : "\n  注意：" + note);
        }
    }
}
