package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
        List<Map<String, Object>> products = jdbc.queryForList("SELECT p.id,p.template_code templateCode,p.product_name productName,p.product_type productType,p.material,p.process,p.specification,p.sample_moq sampleMoq,p.bulk_moq bulkMoq,p.sample_fee_yuan sampleFeeYuan,p.indicative_retail_display indicativeRetailDisplay,p.sample_lead_time sampleLeadTime,p.bulk_lead_time bulkLeadTime,p.supply_status supplyStatus,p.fulfillment_mode fulfillmentMode,p.copyright_requirement copyrightRequirement,o.option_key optionKey,CASE WHEN o.image_rights_status='approved' THEN o.cover_image_url ELSE NULL END coverImageUrl FROM creative_product_template p LEFT JOIN selection_option o ON o.id=p.selection_option_id WHERE p.published=1 AND p.supply_status <> 'suspended' ORDER BY p.sort_order,p.id");
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
        return result(requestNo, "报价申请已提交，运营会根据作品、数量和工艺条件人工确认");
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
        return result(applicationNo, "代销申请已提交，平台会先进行版权、作品质量和渠道匹配审核");
    }

    @GetMapping("/consumer/requests")
    public Map<String, Object> consumerRequests(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("quoteRequests", jdbc.queryForList("SELECT r.id,r.request_no requestNo,r.asset_id assetId,r.request_type requestType,r.quantity,r.purpose,r.status,r.quoted_unit_price quotedUnitPrice,r.quoted_total_price quotedTotalPrice,r.quoted_lead_time quotedLeadTime,r.operator_comment operatorComment,p.template_code templateCode,p.product_name productName,r.created_at createdAt,r.updated_at updatedAt FROM creative_quote_request r JOIN creative_product_template p ON p.id=r.product_template_id WHERE r.user_id=? ORDER BY r.id DESC LIMIT 100", userId));
        out.put("consignmentApplications", jdbc.queryForList("SELECT a.id,a.application_no applicationNo,a.asset_id assetId,a.channel_id channelId,a.channel_name_snapshot channelName,a.sales_mode salesMode,a.creator_share_percent creatorSharePercent,a.platform_service_percent platformServicePercent,a.status,a.operator_comment operatorComment,p.template_code templateCode,p.product_name productName,a.created_at createdAt,a.updated_at updatedAt FROM creative_consignment_application a JOIN creative_product_template p ON p.id=a.product_template_id WHERE a.user_id=? ORDER BY a.id DESC LIMIT 100", userId));
        return out;
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
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,template_code templateCode FROM creative_product_template WHERE template_code=? AND published=1 AND supply_status <> 'suspended'", String.valueOf(code).trim());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品方向不存在或暂未开放");
        return rows.get(0);
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
