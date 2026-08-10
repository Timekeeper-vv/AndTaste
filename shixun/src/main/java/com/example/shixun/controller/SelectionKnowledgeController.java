package com.example.shixun.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumer-facing selection knowledge base. Only reviewed public fields leave
 * this controller; supplier cost, MOQ, margin and internal quotes stay out.
 */
@RestController
@RequestMapping("/api/selection")
public class SelectionKnowledgeController {
    private static final String VERSION = "2023";
    private static final String SOURCE = "选品手册-之间味道.pdf";
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public SelectionKnowledgeController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireConsumer(principal);
        return jdbc.queryForList("SELECT category_key categoryKey,name,description,source_version sourceVersion,review_status reviewStatus FROM selection_category WHERE enabled=1 AND review_status='approved' AND (effective_from IS NULL OR effective_from<=CURRENT_DATE) ORDER BY sort_order,id");
    }

    @GetMapping("/options")
    public List<Map<String, Object>> options(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "24") int size,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        // Conversational creation reads the complete handbook catalog. Keep a
        // bounded limit so a malformed client cannot request an unbounded set.
        return findOptions(userId, category, keyword, Math.max(1, Math.min(size, 300)));
    }

    @GetMapping("/options/{optionKey}")
    public Map<String, Object> option(@PathVariable String optionKey,
                                      @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        return findOptionByKey(optionKey, userId);
    }

    @PostMapping("/recommendations")
    public Map<String, Object> recommendations(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        Map<String, Object> input = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        String category = text(input.get("category"));
        String keyword = text(input.get("theme"));
        String audience = text(input.get("audience"));
        String occasion = text(input.get("occasion"));
        BigDecimal budgetMax = decimal(input.get("budgetMax"));
        int size = integer(input.get("size"), 6);
        Long assetId = longValue(input.get("assetId"));
        if (assetId != null) requireOwnedAsset(assetId, userId);

        // Theme is a ranking signal rather than a hard SQL filter. A user who
        // types a new cultural subject should still receive practical starter
        // SKUs instead of an empty result set.
        List<Map<String, Object>> candidates = findOptions(userId, category, null, 80);
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (Map<String, Object> row : candidates) {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            String haystack = (String.valueOf(row.get("name")) + " " + String.valueOf(row.get("subtitle")) + " "
                    + String.valueOf(row.get("description")) + " " + String.valueOf(row.get("tags"))).toLowerCase();
            if (!blank(keyword) && contains(haystack, keyword)) {
                score += 5;
                reasons.add("与你输入的文化主题相近");
            }
            if (!blank(audience) && contains(String.valueOf(row.get("audienceTags")), audience)) {
                score += 4;
                reasons.add("适合当前受众");
            }
            if (!blank(occasion) && contains(String.valueOf(row.get("occasionTags")), occasion)) {
                score += 4;
                reasons.add("符合当前使用场景");
            }
            BigDecimal retailMin = decimal(row.get("retailMin"));
            if (budgetMax != null && retailMin != null && retailMin.compareTo(budgetMax) <= 0) {
                score += 5;
                reasons.add("落在你的预算范围内");
            } else if (budgetMax != null && retailMin != null) {
                score -= 3;
            }
            if (retailMin != null && retailMin.compareTo(BigDecimal.valueOf(50)) <= 0) score += 1;
            row.put("matchScore", score);
            row.put("reason", reasons.isEmpty() ? defaultReason(row) : String.join("；", reasons));
            row.put("planningNote", planningNote(row, budgetMax));
            ranked.add(row);
        }
        ranked.sort(Comparator.comparingInt(row -> -((Number) row.getOrDefault("matchScore", 0)).intValue()));
        List<Map<String, Object>> selected = ranked.subList(0, Math.min(Math.max(3, Math.min(size, 6)), ranked.size()));
        List<Long> optionIds = selected.stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        String recommendationNo = no("SEL");
        jdbc.update("INSERT INTO creative_selection_recommendation (recommendation_no,user_id,asset_id,input_json,option_ids_json) VALUES (?,?,?,?,?)",
                recommendationNo, userId, assetId, json(input), json(optionIds));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recommendationNo", recommendationNo);
        result.put("version", VERSION);
        result.put("source", SOURCE);
        result.put("filters", Map.of("category", nullToEmpty(category), "theme", nullToEmpty(keyword), "audience", nullToEmpty(audience), "occasion", nullToEmpty(occasion), "budgetMax", budgetMax == null ? "" : budgetMax));
        result.put("options", selected);
        result.put("disclaimer", "资料来源于 2023 年选品手册，仅用于方向筛选。价格、工期、资质和可生产性需在正式打样前重新确认。");
        return result;
    }

    @GetMapping("/favorites")
    public List<Map<String, Object>> favorites(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        return findOptions(userId, null, null, 300).stream()
                .filter(row -> booleanValue(row.get("favorited")))
                .toList();
    }

    @PostMapping("/favorites/{optionKey}")
    public Map<String, Object> addFavorite(@PathVariable String optionKey,
                                           @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        Map<String, Object> row = findOptionByKey(optionKey, userId);
        jdbc.update("INSERT IGNORE INTO user_selection_favorite (user_id,option_id) VALUES (?,?)", userId, row.get("id"));
        return Map.of("optionKey", optionKey, "favorited", true, "message", "已收藏这个选品方向");
    }

    @DeleteMapping("/favorites/{optionKey}")
    public Map<String, Object> removeFavorite(@PathVariable String optionKey,
                                              @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        Map<String, Object> row = findOptionByKey(optionKey, userId);
        jdbc.update("DELETE FROM user_selection_favorite WHERE user_id=? AND option_id=?", userId, row.get("id"));
        return Map.of("optionKey", optionKey, "favorited", false, "message", "已取消收藏");
    }

    @PostMapping("/demands")
    public Map<String, Object> createDemand(@RequestBody Map<String, Object> body,
                                             @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        if (body == null) throw new IllegalArgumentException("需求内容不能为空");
        String optionKey = text(body == null ? null : body.get("optionKey"));
        if (blank(optionKey)) throw new IllegalArgumentException("请选择一个选品方向");
        Map<String, Object> option = findOptionByKey(optionKey, userId);
        Long assetId = longValue(body.get("assetId"));
        if (assetId != null) requireOwnedAsset(assetId, userId);
        String requestNo = no("SDR");
        jdbc.update("INSERT INTO selection_demand_request (request_no,user_id,option_id,asset_id,theme,budget_max,audience,occasion,note) VALUES (?,?,?,?,?,?,?,?,?)",
                requestNo, userId, option.get("id"), assetId, text(body.get("theme")), decimal(body.get("budgetMax")), text(body.get("audience")), text(body.get("occasion")), limit(text(body.get("note")), 1000));
        return Map.of("requestNo", requestNo, "optionKey", optionKey, "optionName", option.get("name"), "status", "new", "message", "商品化需求已提交，后续会由运营或设计师跟进");
    }

    @GetMapping("/demands/mine")
    public List<Map<String, Object>> myDemands(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        Long userId = requireConsumer(principal);
        return jdbc.queryForList("SELECT d.request_no requestNo,d.option_id optionId,o.option_key optionKey,o.name optionName,d.asset_id assetId,d.theme,d.budget_max budgetMax,d.audience,d.occasion,d.note,d.status,d.created_at createdAt FROM selection_demand_request d JOIN selection_option o ON o.id=d.option_id WHERE d.user_id=? ORDER BY d.id DESC LIMIT 100", userId);
    }

    private List<Map<String, Object>> findOptions(Long userId, String category, String keyword, int size) {
        StringBuilder sql = new StringBuilder("SELECT o.id,o.option_key optionKey,o.category_key categoryKey,c.name categoryName,o.name,o.subtitle,o.description,o.material,o.process,o.specification,o.sample_lead_time sampleLeadTime,o.bulk_lead_time bulkLeadTime,o.retail_min retailMin,o.retail_max retailMax,o.retail_display retailDisplay,o.tags,o.audience_tags audienceTags,o.occasion_tags occasionTags,o.budget_band budgetBand,o.cover_image_url coverImageUrl,o.image_source imageSource,o.image_rights_status imageRightsStatus,o.source_version sourceVersion,o.source_name sourceName,o.source_page sourcePage,o.review_status reviewStatus,CASE WHEN f.id IS NULL THEN FALSE ELSE TRUE END favorited FROM selection_option o JOIN selection_category c ON c.category_key=o.category_key LEFT JOIN user_selection_favorite f ON f.option_id=o.id AND f.user_id=? WHERE o.enabled=1 AND o.review_status='approved' AND c.enabled=1 AND c.review_status='approved' AND (o.effective_from IS NULL OR o.effective_from<=CURRENT_DATE) AND (c.effective_from IS NULL OR c.effective_from<=CURRENT_DATE)");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (!blank(category)) { sql.append(" AND o.category_key=?"); args.add(category.trim()); }
        if (!blank(keyword)) { sql.append(" AND (o.name LIKE ? OR o.subtitle LIKE ? OR o.description LIKE ? OR o.tags LIKE ? OR c.name LIKE ?)"); String k = "%" + keyword.trim() + "%"; args.add(k); args.add(k); args.add(k); args.add(k); args.add(k); }
        sql.append(" ORDER BY o.sort_order,o.id LIMIT ?");
        args.add(size);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    private Map<String, Object> findOptionByKey(String optionKey, Long userId) {
        return findOptions(userId, null, null, 300).stream()
                .filter(row -> optionKey.equals(String.valueOf(row.get("optionKey"))))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "选品方向不存在或尚未发布"));
    }

    private void requireOwnedAsset(Long assetId, Long userId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM digital_asset WHERE id=? AND created_by=?", Integer.class, assetId, userId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能关联自己的创作作品");
    }

    private Long requireConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"user".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅C端用户可使用选品服务");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user' AND COALESCE(status,'active')='active'", Integer.class, principal.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        return principal.userId();
    }

    private String defaultReason(Map<String, Object> row) {
        String category = String.valueOf(row.get("categoryName"));
        return "适合从" + category + "方向开始做小批量商品化验证";
    }

    private String planningNote(Map<String, Object> row, BigDecimal budgetMax) {
        if (budgetMax != null && row.get("retailMin") == null) return "该方向需按实时规格、材质和克重正式报价";
        if (budgetMax != null && decimal(row.get("retailMin")).compareTo(budgetMax) > 0) return "超出当前预算，建议先从轻量化单品试水";
        return "先确认授权、尺寸和视觉稿，再进入打样确认";
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception ignored) { return "{}"; }
    }

    private String no(String prefix) {
        return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int) (Math.random() * 900 + 100);
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private String limit(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private boolean contains(String haystack, String needle) { return haystack.contains(needle.trim().toLowerCase()); }
    private boolean booleanValue(Object value) { return value instanceof Boolean b ? b : value instanceof Number n ? n.intValue() != 0 : "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value)); }
    private Integer integer(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (Exception ignored) { return fallback; } }
    private Long longValue(Object value) { try { return value == null || blank(String.valueOf(value)) ? null : Long.valueOf(String.valueOf(value)); } catch (Exception ignored) { throw new IllegalArgumentException("作品编号不正确"); } }
    private BigDecimal decimal(Object value) { try { return value == null || blank(String.valueOf(value)) ? null : new BigDecimal(String.valueOf(value)); } catch (Exception ignored) { throw new IllegalArgumentException("预算金额不正确"); } }
}
