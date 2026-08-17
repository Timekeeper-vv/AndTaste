package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * V1 consumer productization workflow.
 *
 * Product templates are deliberately separate from sellable product_sku rows:
 * a template is a requestable manufacturing direction, while a SKU requires
 * an approved artwork, confirmed supplier parameters and a real price.
 */
@RestController
@RequestMapping("/api/commercial")
public class CommercialProductizationController {
    private static final String COPYRIGHT_VERSION = "commercial-v1";
    private static final String COPYRIGHT_TEXT = "我确认提交的作品为本人原创、已取得有效商业授权，或属于可依法商业使用的公有领域内容；我不会在未获授权的情况下使用博物馆、景区、品牌、字体、人物肖像或他人作品。平台审核不等于权利授予，正式上架前仍需补充权利证明并签署相关协议。";
    private final JdbcTemplate jdbc;

    public CommercialProductizationController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/consumer/products")
    public List<Map<String, Object>> consumerProducts(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireConsumer(principal);
        List<Map<String, Object>> products = jdbc.queryForList("SELECT p.id,p.template_code templateCode,p.product_name productName,p.product_type productType,p.material,p.process,p.specification,p.sample_moq sampleMoq,p.bulk_moq bulkMoq,p.sample_fee_yuan sampleFeeYuan,p.indicative_retail_display indicativeRetailDisplay,p.sample_lead_time sampleLeadTime,p.bulk_lead_time bulkLeadTime,p.supply_status supplyStatus,p.fulfillment_mode fulfillmentMode,p.copyright_requirement copyrightRequirement,o.option_key optionKey,COALESCE(o.category_key,p.product_type) categoryKey,COALESCE(c.name,'其他') categoryName,CASE WHEN o.image_rights_status='approved' THEN o.cover_image_url ELSE NULL END coverImageUrl FROM creative_product_template p LEFT JOIN selection_option o ON o.id=p.selection_option_id LEFT JOIN selection_category c ON c.category_key=o.category_key WHERE p.published=1 AND p.supply_status <> 'suspended' ORDER BY c.sort_order,p.sort_order,p.id");
        products.forEach(product -> { product.put("copyrightStatementVersion", COPYRIGHT_VERSION); product.put("copyrightStatement", COPYRIGHT_TEXT); });
        return products;
    }

    @GetMapping("/consumer/channels")
    public List<Map<String, Object>> consumerChannels(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireConsumer(principal);
        StringBuilder sql = new StringBuilder("SELECT id,channel_code channelCode,name,province,city,district,channel_type channelType,source_type sourceType,cooperation_status cooperationStatus,official_url officialUrl,notes FROM channel_directory WHERE enabled=1");
        List<Object> args = new ArrayList<>();
        if (!blank(keyword)) {
            sql.append(" AND (name LIKE ? OR city LIKE ? OR province LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value); args.add(value); args.add(value);
        }
        if (!blank(province)) { sql.append(" AND province=?"); args.add(province.trim()); }
        sql.append(" ORDER BY province,city,name LIMIT 5000");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        rows.forEach(row -> row.put("cooperationNotice", "目录记录不代表平台已与该机构合作；提交后仍需人工联系、授权和渠道审核。"));
        return rows;
    }

    /**
     * Searchable channel directory for the mini-program selector. The legacy
     * channels endpoint remains available for existing clients.
     */
    @GetMapping("/consumer/channel-directory")
    public Map<String, Object> consumerChannelDirectory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String region,
            @RequestParam(name = "type", required = false) String channelType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireConsumer(principal);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(10, size));
        StringBuilder where = new StringBuilder(" WHERE enabled=1");
        List<Object> args = new ArrayList<>();
        appendChannelFilters(where, args, keyword, province, region, channelType);

        int total = Optional.ofNullable(jdbc.queryForObject("SELECT COUNT(*) FROM channel_directory" + where, Integer.class, args.toArray())).orElse(0);
        List<Object> itemArgs = new ArrayList<>(args);
        itemArgs.add(safeSize);
        itemArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> items = jdbc.queryForList(
                "SELECT id,channel_code channelCode,name,province,city,district,channel_type channelType,source_type sourceType,cooperation_status cooperationStatus,official_url officialUrl,notes "
                        + "FROM channel_directory" + where + " ORDER BY province,city,name LIMIT ? OFFSET ?",
                itemArgs.toArray());
        items.forEach(row -> row.put("cooperationNotice", channelNotice(text(row.get("cooperationStatus")))));

        StringBuilder provinceWhere = new StringBuilder(" WHERE enabled=1 AND province IS NOT NULL AND province <> ''");
        List<Object> provinceArgs = new ArrayList<>();
        appendChannelFilters(provinceWhere, provinceArgs, null, null, region, channelType);
        List<Map<String, Object>> provinces = jdbc.queryForList(
                "SELECT province,COUNT(*) count FROM channel_directory" + provinceWhere + " GROUP BY province ORDER BY province",
                provinceArgs.toArray());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("provinces", provinces);
        return result;
    }

    private void appendChannelFilters(StringBuilder where, List<Object> args, String keyword, String province, String region, String channelType) {
        if (!blank(keyword)) {
            where.append(" AND (name LIKE ? OR city LIKE ? OR province LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            args.add(value); args.add(value); args.add(value);
        }
        if (!blank(channelType) && Set.of("museum", "scenic_spot").contains(channelType.trim())) {
            where.append(" AND channel_type=?");
            args.add(channelType.trim());
        } else {
            // The consumer consignment selector only exposes museums and scenic spots.
            where.append(" AND channel_type IN ('museum','scenic_spot')");
        }
        if (!blank(region)) {
            List<String> provinces = provincesForRegion(region.trim());
            if (!provinces.isEmpty()) {
                where.append(" AND province IN (").append(String.join(",", Collections.nCopies(provinces.size(), "?"))).append(")");
                args.addAll(provinces);
            }
        }
        if (!blank(province)) {
            where.append(" AND province=?");
            args.add(province.trim());
        }
    }

    private List<String> provincesForRegion(String region) {
        return switch (region) {
            case "north" -> List.of("北京市", "天津市", "河北省", "山西省", "内蒙古自治区");
            case "northeast" -> List.of("辽宁省", "吉林省", "黑龙江省");
            case "east" -> List.of("上海市", "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省");
            case "central" -> List.of("河南省", "湖北省", "湖南省");
            case "south" -> List.of("广东省", "广西壮族自治区", "海南省");
            case "southwest" -> List.of("重庆市", "四川省", "贵州省", "云南省", "西藏自治区");
            case "northwest" -> List.of("陕西省", "甘肃省", "青海省", "宁夏回族自治区", "新疆维吾尔自治区");
            default -> List.of();
        };
    }

    private String channelNotice(String status) {
        if ("cooperating".equals(status)) return "已标记为合作渠道，仍需以运营确认的品类、门店和授权范围为准。";
        if ("pending_verification".equals(status)) return "运营提供的候选渠道，合作关系、具体点位和授权状态待核验。";
        return "目录记录不代表平台已与该机构合作；提交后仍需人工联系、授权和渠道审核。";
    }

    @PostMapping("/consumer/quote-requests")
    @Transactional
    public Map<String, Object> createQuoteRequest(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        if (body == null) throw new IllegalArgumentException("报价申请内容不能为空");
        Map<String, Object> product = product(body.get("templateCode"));
        Long assetId = longValue(body.get("assetId"));
        if (assetId != null) requireOwnedAsset(assetId, userId);
        if (assetId != null) requireAssetProductMatch(assetId, product);
        int quantity = positiveInt(body.get("quantity"), 1, 100000, "数量必须在1到100000之间");
        String requestType = enumValue(body.get("requestType"), Set.of("sample", "bulk", "personal"), "sample");
        String purpose = enumValue(body.get("purpose"), Set.of("personal", "channel_sale", "museum_sale"), "personal");
        String basis = copyrightBasis(body.get("copyrightBasis"));
        requireCopyrightConfirmed(body.get("copyrightConfirmed"));
        String requestNo = no("CQR");
        jdbc.update("INSERT INTO creative_quote_request (request_no,user_id,asset_id,product_template_id,request_type,quantity,purpose,note,copyright_basis,copyright_confirmed,copyright_statement_version,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,'new')",
                requestNo, userId, assetId, product.get("id"), requestType, quantity, purpose, limit(text(body.get("note")), 1200), basis, true, COPYRIGHT_VERSION);
        Long applicationId = jdbc.queryForObject("SELECT id FROM creative_quote_request WHERE request_no=?", Long.class, requestNo);
        audit("quote", String.valueOf(applicationId), "created", principal.username(), "用户提交报价/打样申请");
        Map<String, Object> out = result(requestNo, "报价申请已提交，运营会根据作品、数量和工艺条件人工确认");
        out.put("id", applicationId);
        out.put("requestId", applicationId);
        out.put("requestKind", "quote");
        out.put("request", createdQuoteRequest(applicationId, requestNo, assetId, requestType, quantity, purpose, product));
        return out;
    }

    @PostMapping("/consumer/consignment-applications")
    @Transactional
    public Map<String, Object> createConsignmentApplication(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        if (body == null) throw new IllegalArgumentException("代销申请内容不能为空");
        Long assetId = longValue(body.get("assetId"));
        if (assetId == null) throw new IllegalArgumentException("代销申请必须关联一件自己的作品");
        requireOwnedAsset(assetId, userId);
        Map<String, Object> product = product(body.get("templateCode"));
        requireAssetProductMatch(assetId, product);
        Long channelId = longValue(body.get("channelId"));
        String channelName = null;
        if (channelId != null) {
            List<Map<String, Object>> channels = jdbc.queryForList("SELECT id,name FROM channel_directory WHERE id=? AND enabled=1", channelId);
            if (channels.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "渠道目录不存在或已停用");
            channelName = text(channels.get(0).get("name"));
        }
        String basis = copyrightBasis(body.get("copyrightBasis"));
        requireCopyrightConfirmed(body.get("copyrightConfirmed"));
        if ("authorized".equals(basis) && blank(body.get("authorizationNote"))) {
            throw new IllegalArgumentException("已授权作品请填写授权来源和使用范围");
        }
        String applicationNo = no("CCA");
        jdbc.update("INSERT INTO creative_consignment_application (application_no,user_id,asset_id,product_template_id,channel_id,channel_name_snapshot,sales_mode,creator_share_percent,platform_service_percent,note,copyright_basis,copyright_confirmed,copyright_statement_version,authorization_note,status) VALUES (?,?,?,?,?,?, 'preorder',70.00,30.00,?,?,?,?,?,'pending_review')",
                applicationNo, userId, assetId, product.get("id"), channelId, channelName, limit(text(body.get("note")), 1200), basis, true, COPYRIGHT_VERSION, limit(text(body.get("authorizationNote")), 1000));
        Long applicationId = jdbc.queryForObject("SELECT id FROM creative_consignment_application WHERE application_no=?", Long.class, applicationNo);
        audit("consignment", String.valueOf(applicationId), "created", principal.username(), "用户提交代销申请");
        Map<String, Object> out = result(applicationNo, "代销申请已提交，平台会先进行版权、作品质量和渠道匹配审核");
        out.put("id", applicationId);
        out.put("applicationId", applicationId);
        out.put("requestKind", "consignment");
        out.put("application", createdConsignmentApplication(applicationId, applicationNo, assetId, channelId, channelName, product));
        out.put("request", out.get("application"));
        return out;
    }

    @GetMapping("/consumer/requests")
    public Map<String, Object> consumerRequests(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            HttpServletResponse response) {
        noStore(response);
        return consumerRequests(principal);
    }

    /**
     * Direct overload retained for controller-level tests and internal calls.
     * The mapped method above adds no-store headers for real HTTP responses.
     */
    public Map<String, Object> consumerRequests(JwtService.Claims principal) {
        return consumerRequestPayload(requireConsumer(principal));
    }

    @Deprecated
    @GetMapping("/consumer/product-progress")
    public Map<String, Object> consumerProductProgress(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            HttpServletResponse response) {
        noStore(response);
        return consumerProductProgress(principal);
    }

    public Map<String, Object> consumerProductProgress(JwtService.Claims principal) {
        return consumerRequests(principal);
    }

    private Map<String, Object> consumerRequestPayload(Long userId) {
        List<Map<String, Object>> quoteRequests = jdbc.queryForList(
                "SELECT r.id AS `id`,r.request_no AS `requestNo`,r.asset_id AS `assetId`,r.request_type AS `requestType`,"
                        + "r.quantity AS `quantity`,r.purpose AS `purpose`,r.status AS `status`,"
                        + "r.quoted_unit_price AS `quotedUnitPrice`,r.quoted_total_price AS `quotedTotalPrice`,r.quoted_lead_time AS `quotedLeadTime`,"
                        + "r.operator_comment AS `operatorComment`,CASE WHEN r.request_type='sample' AND r.status='accepted' "
                        + "AND r.sample_payment_status='not_required' THEN 'unpaid' ELSE r.sample_payment_status END AS `samplePaymentStatus`,"
                        + "r.sample_payment_order_no AS `samplePaymentOrderNo`,r.sample_paid_at AS `samplePaidAt`,"
                        + "COALESCE(p.template_code,CONCAT('archived-product-',r.product_template_id)) AS `templateCode`,"
                        + "COALESCE(p.product_name,'历史商品化申请') AS `productName`,r.created_at AS `createdAt`,r.updated_at AS `updatedAt` "
                        + "FROM creative_quote_request r LEFT JOIN creative_product_template p ON p.id=r.product_template_id "
                        + "WHERE r.user_id=? ORDER BY r.id DESC LIMIT 100", userId);
        List<Map<String, Object>> consignmentApplications = jdbc.queryForList(
                "SELECT a.id AS `id`,a.application_no AS `applicationNo`,a.asset_id AS `assetId`,a.channel_id AS `channelId`,"
                        + "a.channel_name_snapshot AS `channelName`,a.sales_mode AS `salesMode`,"
                        + "a.creator_share_percent AS `creatorSharePercent`,a.platform_service_percent AS `platformServicePercent`,"
                        + "a.status AS `status`,a.operator_comment AS `operatorComment`,"
                        + "COALESCE(p.template_code,CONCAT('archived-product-',a.product_template_id)) AS `templateCode`,"
                        + "COALESCE(p.product_name,'历史商品化申请') AS `productName`,a.created_at AS `createdAt`,a.updated_at AS `updatedAt` "
                        + "FROM creative_consignment_application a LEFT JOIN creative_product_template p ON p.id=a.product_template_id "
                        + "WHERE a.user_id=? ORDER BY a.id DESC LIMIT 100", userId);
        List<Map<String, Object>> selectionDemands = selectionDemandRequests(userId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("quoteRequests", quoteRequests);
        out.put("consignmentApplications", consignmentApplications);
        out.put("selectionDemands", selectionDemands);
        out.put("summary", Map.of(
                "quoteRequestCount", quoteRequests.size(),
                "consignmentApplicationCount", consignmentApplications.size(),
                "selectionDemandCount", selectionDemands.size()));
        out.put("syncedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return out;
    }

    /**
     * The selection page has its own lightweight demand form. It is still a
     * productization request from the user's point of view, so expose it from
     * the same account-scoped feed as quote and consignment applications.
     *
     * The table check keeps the endpoint compatible with databases that were
     * upgraded from a release before the selection knowledge-base migration.
     */
    private List<Map<String, Object>> selectionDemandRequests(Long userId) {
        if (!tableExists("selection_demand_request") || !tableExists("selection_option")) {
            return Collections.emptyList();
        }
        return jdbc.queryForList(
                "SELECT d.id AS `id`,d.request_no AS `requestNo`,d.option_id AS `optionId`,d.asset_id AS `assetId`,"
                        + "o.option_key AS `optionKey`,COALESCE(o.name,'历史选品需求') AS `productName`,"
                        + "COALESCE(o.name,'历史选品需求') AS `optionName`,d.theme AS `theme`,d.budget_max AS `budgetMax`,"
                        + "d.audience AS `audience`,d.occasion AS `occasion`,d.note AS `note`,d.status AS `status`,"
                        + "d.created_at AS `createdAt`,d.updated_at AS `updatedAt` "
                        + "FROM selection_demand_request d LEFT JOIN selection_option o ON o.id=d.option_id "
                        + "WHERE d.user_id=? ORDER BY d.id DESC LIMIT 100", userId);
    }

    private boolean tableExists(String tableName) {
        Number count = jdbc.queryForObject(
                // MySQL reports the active database, while H2's test schema is PUBLIC.
                // Restricting the lookup still avoids accidentally finding a same-named
                // table in another MySQL schema without making the endpoint test-only.
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE LOWER(table_name)=LOWER(?) "
                        + "AND (table_schema=DATABASE() OR UPPER(table_schema)='PUBLIC')",
                new Object[]{tableName}, Number.class);
        return count != null && count.intValue() > 0;
    }

    @PostMapping("/consumer/quote-requests/{id}/accept")
    @Transactional
    public Map<String, Object> acceptQuoteRequest(
            @PathVariable Long id,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        int changed = jdbc.update("UPDATE creative_quote_request SET status='accepted',sample_payment_status=CASE WHEN request_type='sample' THEN 'unpaid' ELSE 'not_required' END,sample_payment_order_no=NULL,sample_paid_at=NULL,reviewed_at=NOW() WHERE id=? AND user_id=? AND status='quoted' AND quoted_unit_price IS NOT NULL AND quoted_total_price IS NOT NULL AND quoted_lead_time IS NOT NULL AND quoted_lead_time <> ''", id, userId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "报价尚未完整确认，或该申请已处理");
        audit("quote", String.valueOf(id), "accepted", principal.username(), "用户接受报价，等待运营确认打样/生产");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true); result.put("id", id); result.put("status", "accepted");
        result.put("paymentRequired", isSampleQuote(id));
        result.put("message", isSampleQuote(id) ? "报价已接受，请支付打样费，支付成功后进入生产安排" : "报价已接受，运营会联系你确认生产细节");
        return result;
    }

    private boolean isSampleQuote(Long id) {
        String type = jdbc.queryForObject("SELECT request_type FROM creative_quote_request WHERE id=?", String.class, id);
        return "sample".equals(type);
    }

    @GetMapping("/admin/quote-requests")
    public List<Map<String, Object>> adminQuoteRequests(
            @RequestParam(required = false, defaultValue = "new") String status,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireStaff(principal);
        String sql = "SELECT r.id,r.request_no requestNo,r.user_id userId,u.username,r.asset_id assetId,r.request_type requestType,r.quantity,r.purpose,r.note,r.copyright_basis copyrightBasis,r.copyright_confirmed copyrightConfirmed,r.status,r.quoted_unit_price quotedUnitPrice,r.quoted_total_price quotedTotalPrice,r.quoted_lead_time quotedLeadTime,r.operator_comment operatorComment,p.template_code templateCode,p.product_name productName,r.created_at createdAt,r.updated_at updatedAt FROM creative_quote_request r JOIN user u ON u.id=r.user_id JOIN creative_product_template p ON p.id=r.product_template_id";
        if ("all".equals(status)) return jdbc.queryForList(sql + " ORDER BY r.id DESC LIMIT 300");
        return jdbc.queryForList(sql + " WHERE r.status=? ORDER BY r.id DESC LIMIT 300", status);
    }

    @PutMapping("/admin/quote-requests/{id}")
    @Transactional
    public Map<String, Object> reviewQuoteRequest(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Object> body,
                                                  @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireStaff(principal);
        if (body == null) throw new IllegalArgumentException("审核内容不能为空");
        String status = enumValue(body.get("status"), Set.of("new", "processing", "quoted", "accepted", "rejected", "closed"), "processing");
        BigDecimal unit = decimal(body.get("quotedUnitPrice"));
        BigDecimal total = decimal(body.get("quotedTotalPrice"));
        String lead = limit(text(body.get("quotedLeadTime")), 120);
        String comment = limit(text(body.get("operatorComment")), 1200);
        if (Set.of("quoted", "accepted").contains(status)
                && (unit == null || total == null || unit.signum() < 0 || total.signum() < 0 || blank(lead))) {
            throw new IllegalArgumentException("保存报价前请填写单价、总价和交期");
        }
        int changed = jdbc.update("UPDATE creative_quote_request SET status=?,quoted_unit_price=?,quoted_total_price=?,quoted_lead_time=?,operator_comment=?,reviewed_by=?,reviewed_at=NOW() WHERE id=?", status, unit, total, lead, comment, principal.username(), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "报价申请不存在");
        audit("quote", String.valueOf(id), status, principal.username(), comment);
        return Map.of("success", true, "id", id, "status", status);
    }

    @GetMapping("/admin/consignment-applications")
    public List<Map<String, Object>> adminConsignmentApplications(
            @RequestParam(required = false, defaultValue = "pending_review") String status,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireStaff(principal);
        String sql = "SELECT a.id,a.application_no applicationNo,a.user_id userId,u.username,a.asset_id assetId,a.channel_id channelId,a.channel_name_snapshot channelName,a.sales_mode salesMode,a.creator_share_percent creatorSharePercent,a.platform_service_percent platformServicePercent,a.note,a.copyright_basis copyrightBasis,a.copyright_confirmed copyrightConfirmed,a.authorization_note authorizationNote,a.status,a.operator_comment operatorComment,p.template_code templateCode,p.product_name productName,a.created_at createdAt,a.updated_at updatedAt FROM creative_consignment_application a JOIN user u ON u.id=a.user_id JOIN creative_product_template p ON p.id=a.product_template_id";
        if ("all".equals(status)) return jdbc.queryForList(sql + " ORDER BY a.id DESC LIMIT 300");
        return jdbc.queryForList(sql + " WHERE a.status=? ORDER BY a.id DESC LIMIT 300", status);
    }

    @PutMapping("/admin/consignment-applications/{id}")
    @Transactional
    public Map<String, Object> reviewConsignmentApplication(@PathVariable Long id,
                                                            @RequestBody(required = false) Map<String, Object> body,
                                                            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireStaff(principal);
        if (body == null) throw new IllegalArgumentException("审核内容不能为空");
        String status = enumValue(body.get("status"), Set.of("pending_review", "need_materials", "approved", "rejected", "withdrawn"), "need_materials");
        String comment = limit(text(body.get("operatorComment")), 1200);
        int changed = jdbc.update("UPDATE creative_consignment_application SET status=?,operator_comment=?,reviewed_by=?,reviewed_at=NOW() WHERE id=?", status, comment, principal.username(), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "代销申请不存在");
        audit("consignment", String.valueOf(id), status, principal.username(), comment);
        return Map.of("success", true, "id", id, "status", status);
    }

    private Map<String, Object> product(Object code) {
        if (blank(code)) throw new IllegalArgumentException("请选择商品方向");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.id,p.template_code templateCode,p.product_name productName,o.option_key optionKey FROM creative_product_template p LEFT JOIN selection_option o ON o.id=p.selection_option_id WHERE p.template_code=? AND p.published=1 AND p.supply_status <> 'suspended'", String.valueOf(code).trim());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品方向不存在或暂未开放");
        return rows.get(0);
    }

    @SuppressWarnings("unchecked")
    private void requireAssetProductMatch(Long assetId, Map<String, Object> product) {
        String optionKey = text(product.get("optionKey"));
        if (blank(optionKey)) return;
        List<String> rows = jdbc.queryForList("SELECT metadata_json FROM digital_asset WHERE id=?", String.class, assetId);
        if (rows.isEmpty() || blank(rows.get(0))) return;
        try {
            Map<String, Object> metadata = new com.fasterxml.jackson.databind.ObjectMapper().readValue(rows.get(0), Map.class);
            String assetProductKey = text(metadata.get("productKey"));
            if (!blank(assetProductKey) && !optionKey.equals(assetProductKey)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该作品已绑定“" + text(metadata.get("productName")) + "”，不能改为其他产品申请商品化");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // Historical assets may not have metadata in the current format.
        }
    }

    private Long requireConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"user".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅C端用户可使用商品化服务");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user' AND COALESCE(status,'active')='active'", Integer.class, principal.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        return principal.userId();
    }

    private void requireStaff(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!Set.of("admin", "technician").contains(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有运营审核人员可以处理商品化申请");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role=? AND COALESCE(status,'active')='active'", Integer.class, principal.userId(), principal.role());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前审核身份已失效");
    }

    private void requireOwnedAsset(Long assetId, Long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM digital_asset WHERE id=? AND created_by=?", Integer.class, assetId, userId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能关联自己的创作作品");
    }

    private void requireCopyrightConfirmed(Object value) {
        boolean confirmed = value instanceof Boolean b ? b : "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
        if (!confirmed) throw new IllegalArgumentException("请先阅读并确认版权声明");
    }

    private String copyrightBasis(Object value) {
        String basis = text(value);
        if (!Set.of("original", "authorized", "public_domain").contains(basis)) throw new IllegalArgumentException("请选择作品权利依据");
        return basis;
    }

    private void audit(String type, String applicationId, String action, String operator, String comment) {
        jdbc.update("INSERT INTO commercial_application_audit_log (application_type,application_id,action,operator,comment) VALUES (?,?,?,?,?)", type, numericId(applicationId), action, operator == null ? "system" : operator, limit(comment, 1200));
    }

    private Map<String, Object> createdQuoteRequest(Long id, String requestNo, Long assetId,
                                                     String requestType, int quantity, String purpose,
                                                     Map<String, Object> product) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("requestNo", requestNo);
        row.put("assetId", assetId);
        row.put("requestType", requestType);
        row.put("quantity", quantity);
        row.put("purpose", purpose);
        row.put("status", "new");
        row.put("samplePaymentStatus", "not_required");
        row.put("templateCode", product.get("templateCode"));
        row.put("productName", product.get("productName"));
        row.put("createdAt", now);
        row.put("updatedAt", now);
        return row;
    }

    private Map<String, Object> createdConsignmentApplication(Long id, String applicationNo, Long assetId,
                                                               Long channelId, String channelName,
                                                               Map<String, Object> product) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("applicationNo", applicationNo);
        row.put("assetId", assetId);
        row.put("channelId", channelId);
        row.put("channelName", channelName);
        row.put("status", "pending_review");
        row.put("templateCode", product.get("templateCode"));
        row.put("productName", product.get("productName"));
        row.put("createdAt", now);
        row.put("updatedAt", now);
        return row;
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    private long numericId(String value) {
        try { return Long.parseLong(value); } catch (Exception ignored) {
            return Math.abs((long) value.hashCode());
        }
    }

    private Map<String, Object> result(String requestNo, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true); out.put("requestNo", requestNo); out.put("copyrightStatementVersion", COPYRIGHT_VERSION); out.put("message", message);
        return out;
    }

    private String no(String prefix) { return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int) (Math.random() * 900 + 100); }
    private String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private String limit(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
    private boolean blank(Object value) { return value == null || String.valueOf(value).trim().isEmpty(); }
    private Long longValue(Object value) { try { return blank(value) ? null : Long.valueOf(String.valueOf(value)); } catch (Exception ignored) { throw new IllegalArgumentException("编号格式不正确"); } }
    private BigDecimal decimal(Object value) { try { return blank(value) ? null : new BigDecimal(String.valueOf(value)); } catch (Exception ignored) { throw new IllegalArgumentException("报价金额格式不正确"); } }
    private int positiveInt(Object value, int min, int max, String message) { try { int n = blank(value) ? min : Integer.parseInt(String.valueOf(value)); if (n < min || n > max) throw new IllegalArgumentException(message); return n; } catch (NumberFormatException e) { throw new IllegalArgumentException(message); } }
    private String enumValue(Object value, Set<String> values, String fallback) { String v = blank(value) ? fallback : String.valueOf(value).trim(); if (!values.contains(v)) throw new IllegalArgumentException("请求状态或类型不正确"); return v; }
}
