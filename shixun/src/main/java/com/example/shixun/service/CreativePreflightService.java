package com.example.shixun.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/**
 * Produces a durable, version-scoped manufacturing preflight report.
 *
 * The checks are intentionally deterministic and explainable.  They are an
 * early production gate, not a replacement for factory engineering review.
 */
@Service
public class CreativePreflightService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CreativeProjectService projects;

    public CreativePreflightService(JdbcTemplate jdbc, ObjectMapper mapper,
                                    CreativeProjectService projects) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.projects = projects;
    }

    @Transactional
    public Map<String, Object> run(Long projectId, Long versionId, Long userId,
                                   Map<String, Object> request) {
        if (projectId == null || versionId == null) {
            throw new IllegalArgumentException("生产预检必须指定项目和版本");
        }
        Map<String, Object> version = projects.getVersion(projectId, versionId, userId);
        Map<String, Object> body = request == null ? Map.of() : request;
        Long requestedAssetId = number(body.get("assetId"));
        Long requestedBundleId = number(body.get("bundleId"));

        List<Map<String, Object>> assets = queryAssets(projectId, versionId, userId, requestedAssetId);
        Map<String, Object> bundle = queryBundle(requestedBundleId, userId);
        if (bundle != null && (!Objects.equals(number(bundle.get("projectId")), projectId)
                || !Objects.equals(number(bundle.get("versionId")), versionId))) {
            bundle = null;
            requestedBundleId = null;
        }
        List<Map<String, Object>> reviews = queryReviews(projectId, versionId);
        List<Map<String, Object>> checks = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        String phase = text(version.get("phase"));
        String versionStatus = text(version.get("status"));
        boolean archived = "archived".equalsIgnoreCase(versionStatus);
        addCheck(checks, "version", "版本状态", archived ? "blocked" : "passed",
                archived ? "当前版本已归档，不能进入打样" : "版本可用于生产预检", archived);
        if (archived) issues.add("当前版本已归档，请创建新版本后重新提交");

        boolean hasPrimary = !assets.isEmpty();
        addCheck(checks, "primary_asset", "主资产", hasPrimary ? "passed" : "blocked",
                hasPrimary ? "已找到当前版本的作品资产" : "当前版本没有绑定图片、3D模型或规格书", !hasPrimary);
        if (!hasPrimary) issues.add("当前版本没有绑定任何生产资料");

        boolean hasModel = hasAssetType(assets, "model");
        boolean modelApproved = hasApprovedAsset(assets, "model");
        boolean hasSpec = hasSpecification(assets);
        boolean bundleExists = bundle != null;
        boolean bundleComplete = bundleExists && bundleViewCount(bundle) >= 3;
        boolean bundleApproved = bundleComplete && "approved".equalsIgnoreCase(text(bundle.get("status")));
        addCheck(checks, "product_form", "产品形态", (modelApproved || bundleApproved) ? "passed" : "blocked",
                modelApproved ? "已找到审核通过的3D模型" : bundleApproved ? "已找到审核通过的三视图作品包" : "需要审核通过的3D模型或三视图作品包", !(modelApproved || bundleApproved));
        if (!modelApproved && !bundleApproved) issues.add("打样前必须有审核通过的3D模型或三视图作品包");

        addCheck(checks, "multiview", "三视图完整性", !bundleExists || bundleComplete ? "passed" : "blocked",
                !bundleExists ? "当前申请未指定三视图作品包" : bundleComplete ? "三视图视角数量完整" : "三视图作品包缺少必要视角", bundleExists && !bundleComplete);
        if (bundleExists && !bundleComplete) issues.add("三视图作品包至少需要正面、侧面和背面");

        addCheck(checks, "specification", "规格资料", hasSpec ? "passed" : "needs_review",
                hasSpec ? "已绑定3D建模规格书" : "未找到结构化3D建模规格书，建议补充尺寸、材质和工艺说明", false);
        if (!hasSpec) suggestions.add("补充3D建模规格书，明确成品尺寸、材质、壁厚、分件和打样工艺");

        boolean hasReview = !reviews.isEmpty();
        boolean hasGoReview = reviews.stream().anyMatch(row -> "go".equalsIgnoreCase(text(row.get("recommendation"))));
        addCheck(checks, "ai_review", "AI评审", hasGoReview ? "passed" : hasReview ? "needs_review" : "needs_review",
                hasGoReview ? "当前版本已有建议通过的AI评审" : hasReview ? "已有AI评审，但建议不是直接通过" : "尚未找到当前版本的AI评审记录", false);
        if (!hasGoReview) suggestions.add("完成AI评审并处理设计、成本、市场和消费者风险后再提交人工审核");

        String content = collectContent(assets, bundle, version);
        List<String> riskTerms = findTerms(content, List.of("细线", "极细", "发丝", "悬空", "漂浮", "倒扣", "thin line", "floating", "undercut"));
        List<String> rightsTerms = findTerms(content, List.of("未经授权", "明星脸", "肖像复刻", "迪士尼", "漫威", "宝可梦", "皮卡丘", "hello kitty"));
        boolean rightsBlocked = !rightsTerms.isEmpty();
        addCheck(checks, "risk", "结构与版权风险", rightsBlocked ? "blocked" : riskTerms.isEmpty() ? "passed" : "needs_review",
                rightsBlocked ? "发现可能涉及未授权IP或肖像的描述" : riskTerms.isEmpty() ? "未发现明显结构风险词" : "发现需要工艺复核的结构描述", rightsBlocked);
        if (!riskTerms.isEmpty()) issues.add("结构风险词：" + String.join("、", riskTerms));
        if (rightsBlocked) issues.add("版权/肖像风险词：" + String.join("、", rightsTerms));
        if (!riskTerms.isEmpty()) suggestions.add("请由工艺人员确认最小线宽、支撑、拔模和分件结构");
        if (rightsBlocked) suggestions.add("提供版权/肖像授权证明，或改用原创文化元素描述");

        boolean hasMaterial = !blank(firstNonBlank(text(bundle == null ? null : bundle.get("material")), metadataValue(assets, "productMaterial")));
        boolean hasSize = !blank(firstNonBlank(text(bundle == null ? null : bundle.get("productSize")), metadataValue(assets, "productSize")));
        addCheck(checks, "spec_lock", "材质与尺寸", hasMaterial && hasSize ? "passed" : "needs_review",
                hasMaterial && hasSize ? "已记录材质和成品尺寸" : "材质或成品尺寸仍需确认", false);
        if (!hasMaterial) suggestions.add("确认最终材质、表面处理和食品接触要求");
        if (!hasSize) suggestions.add("确认成品长宽高、厚度和关键结构尺寸");

        boolean blocked = checks.stream().anyMatch(check -> "blocked".equals(check.get("status")));
        boolean needsReview = checks.stream().anyMatch(check -> "needs_review".equals(check.get("status")));
        int score = Math.max(0, 100 - (int) checks.stream().filter(check -> "blocked".equals(check.get("status"))).count() * 30
                - (int) checks.stream().filter(check -> "needs_review".equals(check.get("status"))).count() * 8);
        String status = blocked ? "blocked" : needsReview ? "needs_review" : "passed";
        if (issues.isEmpty()) issues.add("未发现阻断性问题，仍需由人工和工厂完成最终确认");
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("phase", phase);
        context.put("versionStatus", versionStatus);
        context.put("assetIds", assets.stream().map(row -> row.get("id")).toList());
        context.put("bundleId", requestedBundleId);
        context.put("reviewIds", reviews.stream().map(row -> row.get("id")).toList());
        context.put("hasModel", hasModel);
        context.put("hasSpecification", hasSpec);
        context.put("generatedAt", new Date().toInstant().toString());

        Long reportId = insertReport(projectId, versionId, userId, status, score,
                text(version.get("freezeHash")), checks, issues, suggestions, context);
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("reportId", reportId);
        eventPayload.put("status", status);
        eventPayload.put("score", score);
        eventPayload.put("blockingIssueCount", checks.stream().filter(check -> "blocked".equals(check.get("status"))).count());
        if (Set.of("brief", "generation", "multiview", "preflight").contains(phase)) {
            try {
                projects.transitionProject(projectId, versionId, userId, "preflight", "preflight_completed",
                        "system", null, eventPayload);
            } catch (IllegalStateException ignored) {
                projects.recordWorkflowEvent(projectId, versionId, userId, "preflight_completed",
                        "system", null, eventPayload);
            }
        } else {
            projects.recordWorkflowEvent(projectId, versionId, userId, "preflight_completed",
                    "system", null, eventPayload);
        }
        return report(reportId, projectId, versionId, userId, status, score, version, checks, issues, suggestions, context);
    }

    public Map<String, Object> latest(Long projectId, Long versionId, Long userId) {
        projects.getVersion(projectId, versionId, userId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,project_id projectId,version_id versionId,user_id userId,status,score,version_freeze_hash versionFreezeHash,checks_json checksJson,issues_json issuesJson,suggestions_json suggestionsJson,context_json contextJson,created_at createdAt,updated_at updatedAt FROM creative_preflight_report WHERE project_id=? AND version_id=? ORDER BY id DESC LIMIT 1",
                projectId, versionId);
        if (rows.isEmpty()) return Map.of("projectId", projectId, "versionId", versionId, "status", "not_run");
        Map<String, Object> row = rows.get(0);
        return parseStoredReport(row);
    }

    private Map<String, Object> report(Long reportId, Long projectId, Long versionId, Long userId,
                                       String status, int score, Map<String, Object> version,
                                       List<Map<String, Object>> checks, List<String> issues,
                                       List<String> suggestions, Map<String, Object> context) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("projectId", projectId);
        result.put("versionId", versionId);
        result.put("userId", userId);
        result.put("status", status);
        result.put("score", score);
        result.put("versionFreezeHash", version.get("freezeHash"));
        result.put("checks", checks);
        result.put("issues", issues);
        result.put("suggestions", suggestions);
        result.put("context", context);
        return result;
    }

    private Long insertReport(Long projectId, Long versionId, Long userId, String status, int score,
                              String freezeHash, List<Map<String, Object>> checks, List<String> issues,
                              List<String> suggestions, Map<String, Object> context) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO creative_preflight_report (project_id,version_id,user_id,status,score,version_freeze_hash,checks_json,issues_json,suggestions_json,context_json) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            ps.setLong(2, versionId);
            ps.setLong(3, userId);
            ps.setString(4, status);
            ps.setInt(5, score);
            ps.setString(6, blank(freezeHash) ? null : freezeHash);
            ps.setString(7, json(checks));
            ps.setString(8, json(issues));
            ps.setString(9, json(suggestions));
            ps.setString(10, json(context));
            return ps;
        }, keyHolder);
        Number key = generatedId(keyHolder);
        if (key == null) throw new IllegalStateException("生产预检报告保存失败");
        return key.longValue();
    }

    /** H2 and some JDBC drivers return the id together with audit timestamps. */
    private Number generatedId(KeyHolder holder) {
        try {
            Number key = holder.getKey();
            if (key != null) return key;
        } catch (org.springframework.dao.InvalidDataAccessApiUsageException ignored) {
            // Fall through to the generated-key map below.
        }
        for (Map<String, Object> row : holder.getKeyList()) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if ("ID".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number number) {
                    return number;
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> queryAssets(Long projectId, Long versionId, Long userId, Long requestedAssetId) {
        try {
            if (requestedAssetId != null) {
                return jdbc.queryForList(
                        "SELECT id,asset_type assetType,status,format,tags,metadata_json metadataJson,prompt,negative_prompt negativePrompt,created_by createdBy FROM digital_asset WHERE id=? AND created_by=? AND project_id=? AND version_id=?",
                        requestedAssetId, userId, projectId, versionId);
            }
            return jdbc.queryForList(
                    "SELECT id,asset_type assetType,status,format,tags,metadata_json metadataJson,prompt,negative_prompt negativePrompt,created_by createdBy FROM digital_asset WHERE created_by=? AND project_id=? AND version_id=? ORDER BY id DESC",
                    userId, projectId, versionId);
        } catch (DataAccessException ignored) {
            // The project columns are additive. A pre-migration node can still
            // run the old text-only endpoint, but cannot produce a scoped gate.
            return List.of();
        }
    }

    private Map<String, Object> queryBundle(Long bundleId, Long userId) {
        if (bundleId == null) return null;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT id,status,view_count viewCount,project_id projectId,version_id versionId,input_asset_id inputAssetId,product_name productName,material,product_size productSize FROM creative_multiview_bundle WHERE id=? AND user_id=?",
                    bundleId, userId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException ignored) {
            return null;
        }
    }

    private List<Map<String, Object>> queryReviews(Long projectId, Long versionId) {
        try {
            return jdbc.queryForList(
                    "SELECT r.id,r.overall_score overallScore,r.recommendation,a.id assetId FROM design_review r JOIN digital_asset a ON a.id=r.asset_id WHERE a.project_id=? AND a.version_id=? ORDER BY r.id DESC",
                    projectId, versionId);
        } catch (DataAccessException ignored) {
            return List.of();
        }
    }

    private Map<String, Object> parseStoredReport(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", row.get("id"));
        result.put("projectId", row.get("projectId"));
        result.put("versionId", row.get("versionId"));
        result.put("userId", row.get("userId"));
        result.put("status", row.get("status"));
        result.put("score", row.get("score"));
        result.put("versionFreezeHash", row.get("versionFreezeHash"));
        result.put("checks", readJson(row.get("checksJson"), List.of()));
        result.put("issues", readJson(row.get("issuesJson"), List.of()));
        result.put("suggestions", readJson(row.get("suggestionsJson"), List.of()));
        result.put("context", readJson(row.get("contextJson"), Map.of()));
        result.put("createdAt", row.get("createdAt"));
        result.put("updatedAt", row.get("updatedAt"));
        return result;
    }

    private void addCheck(List<Map<String, Object>> checks, String key, String label,
                          String status, String detail, boolean blocking) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("key", key);
        check.put("label", label);
        check.put("status", status);
        check.put("detail", detail);
        check.put("blocking", blocking);
        checks.add(check);
    }

    private boolean hasAssetType(List<Map<String, Object>> assets, String type) {
        return assets.stream().anyMatch(asset -> type.equalsIgnoreCase(text(asset.get("assetType"))));
    }

    private boolean hasApprovedAsset(List<Map<String, Object>> assets, String type) {
        return assets.stream().anyMatch(asset -> type.equalsIgnoreCase(text(asset.get("assetType")))
                && "approved".equalsIgnoreCase(text(asset.get("status"))));
    }

    private boolean hasSpecification(List<Map<String, Object>> assets) {
        return assets.stream().anyMatch(asset -> "prompt".equalsIgnoreCase(text(asset.get("assetType")))
                && (text(asset.get("tags")).contains("3D") || text(asset.get("tags")).contains("建模")
                || text(asset.get("prompt")).contains("建模规格")));
    }

    private int bundleViewCount(Map<String, Object> bundle) {
        Number count = bundle == null ? null : number(bundle.get("viewCount"));
        return count == null ? 0 : count.intValue();
    }

    private String metadataValue(List<Map<String, Object>> assets, String key) {
        for (Map<String, Object> asset : assets) {
            try {
                JsonNode node = mapper.readTree(text(asset.get("metadataJson")));
                if (node != null && node.isObject() && !blank(node.path(key).asText(""))) return node.path(key).asText();
            } catch (Exception ignored) { }
        }
        return "";
    }

    private String collectContent(List<Map<String, Object>> assets, Map<String, Object> bundle,
                                  Map<String, Object> version) {
        StringBuilder content = new StringBuilder();
        for (Map<String, Object> asset : assets) {
            content.append(' ').append(text(asset.get("prompt"))).append(' ')
                    .append(text(asset.get("tags"))).append(' ')
                    .append(text(asset.get("metadataJson")));
        }
        if (bundle != null) content.append(' ').append(text(bundle.get("productName"))).append(' ')
                .append(text(bundle.get("material"))).append(' ')
                .append(text(bundle.get("productSize")));
        content.append(' ').append(text(version.get("briefJson"))).append(' ')
                .append(text(version.get("metadataJson")));
        return content.toString().toLowerCase(Locale.ROOT);
    }

    private List<String> findTerms(String content, List<String> candidates) {
        List<String> found = new ArrayList<>();
        for (String candidate : candidates) if (content.contains(candidate.toLowerCase(Locale.ROOT))) found.add(candidate);
        return found;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("预检报告数据格式无效", e); }
    }

    private Object readJson(Object value, Object fallback) {
        if (value == null || blank(String.valueOf(value))) return fallback;
        try { return mapper.readValue(String.valueOf(value), Object.class); }
        catch (Exception ignored) { return fallback; }
    }

    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null || blank(String.valueOf(value)) ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException e) { return null; }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (!blank(value)) return value;
        return "";
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
