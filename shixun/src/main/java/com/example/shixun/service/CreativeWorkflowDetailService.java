package com.example.shixun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Read model for the end-to-end consumer production workflow.
 *
 * <p>The write side intentionally remains split by ownership (consumer,
 * reviewer, factory and logistics). This service is the single read boundary
 * those clients use to render the current step and the next safe action.</p>
 */
@Service
public class CreativeWorkflowDetailService {
    private static final Set<String> STAFF_ROLES = Set.of("admin", "technician", "feeder");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CreativeWorkflowDetailService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Map<String, Object> forConsumer(Long requestId, Long userId) {
        Map<String, Object> detail = load(requestId, userId);
        Object requestObject = detail.get("request");
        Long ownerId = requestObject instanceof Map<?, ?> requestMap ? number(requestMap.get("userId")) : null;
        if (!Objects.equals(ownerId, userId)) {
            throw new NoSuchElementException("生产申请不存在或无权访问");
        }
        return detail;
    }

    public Map<String, Object> forStaff(Long requestId, String role) {
        if (!STAFF_ROLES.contains(role)) throw new IllegalArgumentException("当前后台账号没有流程查看权限");
        return load(requestId, null);
    }

    private Map<String, Object> load(Long requestId, Long expectedOwnerId) {
        if (requestId == null || requestId <= 0) throw new IllegalArgumentException("申请编号无效");
        Map<String, Object> request = queryRequest(requestId);
        if (request == null) throw new NoSuchElementException("生产申请不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("request", request);

        Long projectId = number(request.get("projectId"));
        Long versionId = number(request.get("versionId"));
        Map<String, Object> project = projectId == null ? null : queryProject(projectId);
        if (expectedOwnerId != null && project != null
                && !Objects.equals(number(project.get("userId")), expectedOwnerId)) {
            throw new NoSuchElementException("生产申请不存在或无权访问");
        }
        Map<String, Object> version = projectId == null || versionId == null ? null : queryVersion(projectId, versionId);
        result.put("project", project);
        result.put("version", version);
        result.put("snapshot", parseJson(request.get("versionSnapshotJson"), (Object) Map.of()));
        result.put("preflight", projectId == null || versionId == null ? null : queryPreflight(projectId, versionId));
        result.put("review", queryReview(request, projectId, versionId));
        result.put("payment", queryPayment(request));

        String requestType = text(request.get("requestType"));
        if ("sample".equalsIgnoreCase(requestType)) {
            result.put("sample", querySampleLifecycle(requestId));
            result.put("logistics", queryLogistics(requestId));
        } else {
            result.put("sample", null);
            result.put("logistics", null);
        }
        result.put("timeline", projectId == null ? List.of() : queryTimeline(projectId));
        result.put("flow", flow(request, project, version, result));
        return result;
    }

    private Map<String, Object> flow(Map<String, Object> request, Map<String, Object> project,
                                     Map<String, Object> version, Map<String, Object> detail) {
        String code = deriveCode(request, project, version);
        Map<String, Object> flow = new LinkedHashMap<>();
        flow.put("code", code);
        flow.put("label", label(code));
        flow.put("phase", text(version == null ? null : version.get("phase"), code));
        flow.put("phaseLabel", label(text(version == null ? null : version.get("phase"), code)));
        flow.put("nextAction", nextAction(code));
        flow.put("nextActionCode", nextActionCode(code));
        List<Map<String, Object>> blockers = blockers(request, detail, code);
        flow.put("blocked", !blockers.isEmpty());
        flow.put("blockers", blockers);
        flow.put("availableActions", actions(request, detail, code, blockers));
        flow.put("progressPercent", progress(code));
        flow.put("updatedAt", request.get("updatedAt"));
        return flow;
    }

    private String deriveCode(Map<String, Object> request, Map<String, Object> project, Map<String, Object> version) {
        String sample = text(request.get("sampleWorkflowStatus"));
        String status = text(request.get("status"));
        String payment = text(request.get("samplePaymentStatus"));
        String type = text(request.get("requestType"));
        if ("sample".equalsIgnoreCase(type)) {
            if (Set.of("bulk_unlocked").contains(sample)) return "bulk_unlocked";
            if ("accepted".equals(sample)) return "sample_accepted";
            if ("rejected".equals(sample)) return "sample_rejected";
            if (Set.of("revision_required", "revision_in_progress", "revision_completed").contains(sample)) return sample;
            if ("received".equals(sample)) return "sample_feedback";
            if ("shipped".equals(sample)) return "sample_shipped";
            if ("ready_to_ship".equals(sample)) return "sample_ready";
            if ("in_production".equals(sample)) return "sampling";
            if ("review".equals(status)) return "human_review";
            if ("approved".equals(status) && Set.of("unpaid", "pending", "manual_review").contains(payment)) return "payment_pending";
            if (Set.of("processing", "shipped", "completed").contains(status)) return "sampling";
        }
        if ("rejected".equals(status)) return "rejected";
        if ("review".equals(status)) return "human_review";
        if ("approved".equals(status)) return "approved";
        if ("processing".equals(status)) return "in_production";
        if ("shipped".equals(status)) return "shipped";
        if ("completed".equals(status)) return "completed";
        return text(version == null ? null : version.get("phase"), text(project == null ? null : project.get("currentPhase"), "brief"));
    }

    private List<Map<String, Object>> blockers(Map<String, Object> request, Map<String, Object> detail, String code) {
        List<Map<String, Object>> blockers = new ArrayList<>();
        String payment = text(request.get("samplePaymentStatus"));
        String status = text(request.get("status"));
        Object preflightObject = detail.get("preflight");
        if ("payment_pending".equals(code)) blockers.add(issue("payment", "待支付打样费", "完成打样费支付后，工厂才能开始制作", "pay_sample"));
        if (preflightObject instanceof Map<?, ?> preflight && "blocked".equals(text(preflight.get("status")))) {
            blockers.add(issue("preflight", "生产预检未通过", firstItem(preflight.get("issues"), "请先处理生产预检阻断项"), "fix_preflight"));
        }
        if ("review".equals(status)) blockers.add(issue("review", "等待平台审核", "审核通过后才能进入支付或生产", "wait_review"));
        if ("sample".equalsIgnoreCase(text(request.get("requestType"))) && "approved".equals(status)
                && Set.of("unpaid", "pending", "manual_review").contains(payment)
                && !"payment_pending".equals(code)) {
            blockers.add(issue("payment", "打样费待处理", "请完成或核对打样费支付", "pay_sample"));
        }
        return blockers;
    }

    private List<String> actions(Map<String, Object> request, Map<String, Object> detail, String code,
                                 List<Map<String, Object>> blockers) {
        List<String> actions = new ArrayList<>();
        boolean blockedByReview = blockers.stream().anyMatch(item -> "review".equals(item.get("code")));
        boolean blockedByPayment = blockers.stream().anyMatch(item -> "payment".equals(item.get("code")));
        if ("human_review".equals(code)) actions.add("wait_review");
        if (blockedByPayment) actions.add("pay_sample");
        if (Set.of("sampling", "in_production").contains(code)) actions.add("track_sample");
        if (Set.of("sample_shipped", "sample_feedback").contains(code)) actions.add("submit_feedback");
        if (Set.of("revision_required", "revision_completed").contains(code)) actions.add("track_revision");
        if ("sample_accepted".equals(code)) actions.add("unlock_bulk");
        if ("bulk_unlocked".equals(code)) actions.add("submit_bulk");
        if (actions.isEmpty() && !blockedByReview && !blockedByPayment) actions.add("view_timeline");
        return actions;
    }

    private Map<String, Object> issue(String code, String label, String reason, String action) {
        return Map.of("code", code, "label", label, "reason", reason, "action", action);
    }

    private Map<String, Object> queryRequest(Long requestId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.id,r.request_no requestNo,r.user_id userId,r.asset_id assetId,r.multiview_bundle_id bundleId,r.request_type requestType,r.title,r.quantity,r.status,r.review_comment reviewComment,r.reviewed_by reviewedBy,r.reviewed_at reviewedAt,r.sample_product_name sampleProductName,r.sample_fee_yuan sampleFeeYuan,r.sample_payment_status samplePaymentStatus,r.sample_payment_order_no samplePaymentOrderNo,r.sample_paid_at samplePaidAt,r.sample_workflow_status sampleWorkflowStatus,r.sample_received_at sampleReceivedAt,r.sample_accepted_at sampleAcceptedAt,r.sample_revision_count sampleRevisionCount,r.bulk_unlocked_at bulkUnlockedAt,r.bulk_unlocked_by bulkUnlockedBy,r.project_id projectId,r.version_id versionId,r.version_snapshot_json versionSnapshotJson,r.version_snapshot_hash versionSnapshotHash,r.version_frozen_at versionFrozenAt,r.note,r.created_at createdAt,r.updated_at updatedAt FROM consumer_production_request r WHERE r.id=?", requestId);
            return rows.isEmpty() ? null : canonicalize(rows.get(0));
        } catch (Exception ignored) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,request_no requestNo,user_id userId,asset_id assetId,request_type requestType,title,quantity,status,review_comment reviewComment,reviewed_by reviewedBy,reviewed_at reviewedAt,sample_product_name sampleProductName,sample_fee_yuan sampleFeeYuan,sample_payment_status samplePaymentStatus,sample_payment_order_no samplePaymentOrderNo,sample_paid_at samplePaidAt,created_at createdAt,updated_at updatedAt FROM consumer_production_request WHERE id=?", requestId);
            return rows.isEmpty() ? null : canonicalize(rows.get(0));
        }
    }

    private Map<String, Object> queryProject(Long projectId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,project_no projectNo,user_id userId,name,theme,status,current_phase currentPhase,current_version_id currentVersionId,next_action nextAction,metadata_json metadataJson,created_at createdAt,updated_at updatedAt FROM creative_project WHERE id=?", projectId);
        return rows.isEmpty() ? null : canonicalize(rows.get(0));
    }

    private Map<String, Object> queryVersion(Long projectId, Long versionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,project_id projectId,version_no versionNo,version_number versionNumber,version_label versionLabel,phase,status,frozen_at frozenAt,frozen_by frozenBy,freeze_reason freezeReason,freeze_hash freezeHash,brief_json briefJson,metadata_json metadataJson,created_at createdAt,updated_at updatedAt FROM creative_project_version WHERE id=? AND project_id=?", versionId, projectId);
        return rows.isEmpty() ? null : canonicalize(rows.get(0));
    }

    private Map<String, Object> queryPreflight(Long projectId, Long versionId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id reportId,project_id projectId,version_id versionId,status,score,version_freeze_hash versionFreezeHash,checks_json checksJson,issues_json issuesJson,suggestions_json suggestionsJson,context_json contextJson,created_at createdAt,updated_at updatedAt FROM creative_preflight_report WHERE project_id=? AND version_id=? ORDER BY id DESC LIMIT 1", projectId, versionId);
            if (rows.isEmpty()) return null;
            Map<String, Object> row = canonicalize(rows.get(0));
            row.put("checks", parseJson(row.remove("checksJson"), (Object) List.of()));
            row.put("issues", parseJson(row.remove("issuesJson"), (Object) List.of()));
            row.put("suggestions", parseJson(row.remove("suggestionsJson"), (Object) List.of()));
            row.put("context", parseJson(row.remove("contextJson"), (Object) Map.of()));
            return row;
        } catch (Exception ignored) { return null; }
    }

    private Map<String, Object> queryReview(Map<String, Object> request, Long projectId, Long versionId) {
        Long assetId = number(request.get("assetId"));
        if (assetId == null) return null;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT r.id,r.review_no reviewNo,r.asset_id assetId,r.overall_score overallScore,r.summary,r.recommendation,r.created_at createdAt FROM design_review r WHERE r.asset_id=? ORDER BY r.id DESC LIMIT 1", assetId);
            if (rows.isEmpty()) return null;
            return canonicalize(rows.get(0));
        } catch (Exception ignored) { return null; }
    }

    private Map<String, Object> queryPayment(Map<String, Object> request) {
        String orderNo = text(request.get("samplePaymentOrderNo"));
        if (orderNo.isBlank()) return null;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT order_no orderNo,status,channel,amount_fen amountFen,created_at createdAt,paid_at paidAt,updated_at updatedAt FROM payment_order WHERE order_no=? LIMIT 1", orderNo);
            return rows.isEmpty() ? null : canonicalize(rows.get(0));
        } catch (Exception ignored) { return null; }
    }

    private Map<String, Object> querySampleLifecycle(Long requestId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,request_id requestId,event_type eventType,decision,rating,comment,issue_tags_json issueTagsJson,evidence_asset_ids_json evidenceAssetIdsJson,payload_json payloadJson,created_by createdBy,created_at createdAt FROM creative_sample_lifecycle_event WHERE request_id=? ORDER BY id ASC", requestId);
            List<Map<String, Object>> events = rows.stream().map(this::canonicalize).toList();
            return Map.of("events", events, "count", events.size());
        } catch (Exception ignored) { return Map.of("events", List.of(), "count", 0); }
    }

    private Map<String, Object> queryLogistics(Long requestId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id logisticsId,request_id requestId,carrier_code carrierCode,carrier_name carrierName,tracking_no trackingNo,status,latest_trace latestTrace,alert_level alertLevel,alert_status alertStatus,exception_note exceptionNote,shipped_at shippedAt,signed_at signedAt,estimated_arrival estimatedArrival,last_synced_at lastSyncedAt,updated_at updatedAt FROM creative_sample_logistics WHERE request_id=?", requestId);
            if (rows.isEmpty()) return null;
            Map<String, Object> item = canonicalize(rows.get(0));
            Long logisticsId = number(item.get("logisticsId"));
            if (logisticsId != null) {
                List<Map<String, Object>> traces = jdbc.queryForList("SELECT id,logistics_id logisticsId,request_id requestId,event_type eventType,status,alert_level alertLevel,location,content,payload_json payloadJson,created_by createdBy,created_at createdAt FROM creative_sample_logistics_event WHERE logistics_id=? ORDER BY id DESC", logisticsId);
                item.put("traces", traces.stream().map(this::canonicalize).toList());
            }
            return item;
        } catch (Exception ignored) { return null; }
    }

    private List<Map<String, Object>> queryTimeline(Long projectId) {
        try {
            return jdbc.queryForList("SELECT id,version_id versionId,event_type eventType,from_phase fromPhase,to_phase toPhase,next_action nextAction,actor_type actorType,actor_id actorId,payload_json payloadJson,created_at createdAt FROM creative_project_event WHERE project_id=? ORDER BY id ASC", projectId).stream().map(this::canonicalize).toList();
        } catch (Exception ignored) { return List.of(); }
    }

    private String label(String code) {
        return switch (String.valueOf(code)) {
            case "brief" -> "确认创作需求";
            case "generation" -> "生成产品方案";
            case "multiview" -> "补齐三视图";
            case "preflight" -> "完成生产预检";
            case "ai_review" -> "完成AI评审";
            case "human_review" -> "等待平台审核";
            case "approved" -> "审核通过";
            case "payment_pending" -> "等待支付打样费";
            case "sampling" -> "工厂打样中";
            case "sample_ready" -> "样品已出样";
            case "sample_shipped" -> "样品运输中";
            case "sample_feedback" -> "等待样品反馈";
            case "revision_required" -> "等待返修";
            case "revision_in_progress" -> "返修制作中";
            case "revision_completed" -> "返修完成，待重新出样";
            case "sample_accepted" -> "样品验收通过";
            case "bulk_unlocked" -> "已解锁量产";
            case "in_production" -> "量产制作中";
            case "shipped" -> "成品已发货";
            case "completed" -> "流程已完成";
            case "rejected" -> "申请已驳回";
            case "sample_rejected" -> "样品未通过";
            default -> String.valueOf(code);
        };
    }

    private String nextAction(String code) {
        return switch (code) {
            case "human_review" -> "平台审核完成后继续";
            case "payment_pending" -> "完成打样费支付";
            case "sampling", "in_production" -> "等待工厂更新制作状态";
            case "sample_ready" -> "等待工厂填写运单号";
            case "sample_shipped" -> "收到样品后提交反馈";
            case "sample_feedback" -> "选择返修或确认验收";
            case "revision_required" -> "等待工厂开始返修";
            case "revision_in_progress" -> "等待返修完成并重新出样";
            case "revision_completed" -> "等待新样品寄出";
            case "sample_accepted" -> "解锁批量生产";
            case "bulk_unlocked" -> "提交批量生产申请";
            default -> label(code);
        };
    }

    private String nextActionCode(String code) {
        return switch (code) {
            case "payment_pending" -> "pay_sample";
            case "sample_shipped", "sample_feedback" -> "submit_feedback";
            case "sample_accepted" -> "unlock_bulk";
            case "bulk_unlocked" -> "submit_bulk";
            default -> "wait";
        };
    }

    private int progress(String code) {
        return switch (code) {
            case "brief" -> 8; case "generation" -> 18; case "multiview" -> 28;
            case "preflight" -> 36; case "ai_review" -> 44; case "human_review" -> 52;
            case "approved", "payment_pending" -> 60; case "sampling", "in_production" -> 70;
            case "sample_ready", "sample_shipped", "sample_feedback" -> 78;
            case "revision_required", "revision_in_progress", "revision_completed" -> 74;
            case "sample_accepted" -> 88; case "bulk_unlocked" -> 92;
            case "completed" -> 100; default -> 20;
        };
    }

    private String firstItem(Object value, String fallback) {
        if (value instanceof Collection<?> values && !values.isEmpty()) return String.valueOf(values.iterator().next());
        return fallback;
    }

    private Object parseJson(Object value, Object fallback) {
        if (value == null) return fallback;
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) return value;
        try { return mapper.readValue(String.valueOf(value), Object.class); }
        catch (Exception ignored) { return fallback; }
    }

    private Map<String, Object> canonicalize(Map<String, Object> source) {
        Map<String, String> aliases = new HashMap<>();
        for (String key : List.of(
                "id", "requestNo", "userId", "assetId", "bundleId", "requestType", "title", "quantity", "status",
                "reviewComment", "reviewedBy", "reviewedAt", "sampleProductName", "sampleFeeYuan", "samplePaymentStatus",
                "samplePaymentOrderNo", "samplePaidAt", "sampleWorkflowStatus", "sampleReceivedAt", "sampleAcceptedAt",
                "sampleRevisionCount", "bulkUnlockedAt", "bulkUnlockedBy", "projectId", "versionId", "versionSnapshotJson",
                "versionSnapshotHash", "versionFrozenAt", "note", "createdAt", "updatedAt", "projectNo", "name", "theme",
                "currentPhase", "currentVersionId", "nextAction", "metadataJson", "versionNo", "versionNumber", "versionLabel",
                "phase", "frozenAt", "frozenBy", "freezeReason", "freezeHash", "briefJson", "reportId", "score",
                "versionFreezeHash", "checksJson", "issuesJson", "suggestionsJson", "contextJson", "reviewNo", "overallScore",
                "summary", "recommendation", "orderNo", "channel", "amountFen", "paidAt", "eventType", "decision", "rating",
                "comment", "issueTagsJson", "evidenceAssetIdsJson", "payloadJson", "createdBy", "logisticsId", "carrierCode",
                "carrierName", "trackingNo", "latestTrace", "alertLevel", "alertStatus", "exceptionNote", "shippedAt",
                "signedAt", "estimatedArrival", "lastSyncedAt", "location", "content")) aliases.put(key.toLowerCase(Locale.ROOT), key);
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (key == null) continue;
            out.put(aliases.getOrDefault(key.toLowerCase(Locale.ROOT), key), entry.getValue());
        }
        return out;
    }

    private Long number(Object value) {
        if (value instanceof Number n) return n.longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (Exception ignored) { return null; }
    }

    private String text(Object value) { return text(value, ""); }
    private String text(Object value, String fallback) { return value == null ? fallback : String.valueOf(value).trim(); }
}
