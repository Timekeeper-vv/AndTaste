package com.example.shixun.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Owns the sample-to-bulk handoff after a production request is approved.
 * Feedback is append-only; the request row is only the current projection.
 */
@Service
public class SampleLifecycleService {
    private static final Set<String> DECISIONS = Set.of("accept", "revision_required", "reject");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CreativeProjectService projects;

    public SampleLifecycleService(JdbcTemplate jdbc, ObjectMapper mapper, CreativeProjectService projects) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.projects = projects;
    }

    @Transactional
    public Map<String, Object> submitFeedback(Long requestId, Long userId, Map<String, Object> body) {
        RequestContext request = ownedSampleRequest(requestId, userId);
        rejectClosedLifecycle(request);
        requireSampleInProduction(request);
        String decision = text(body == null ? null : body.get("decision"));
        if (!DECISIONS.contains(decision)) throw new IllegalArgumentException("反馈结论只能是 accept / revision_required / reject");
        int rating = integer(body == null ? null : body.get("rating"), 0);
        if (rating < 0 || rating > 5) throw new IllegalArgumentException("评分必须是0到5分");
        String comment = limit(text(body == null ? null : body.get("comment")), 2000);
        List<Object> issueTags = list(body == null ? null : body.get("issueTags"));
        List<Object> evidence = list(body == null ? null : body.get("evidenceAssetIds"));
        if ("revision_required".equals(decision) && blank(comment) && issueTags.isEmpty()) {
            throw new IllegalArgumentException("需要返修时请填写问题说明或问题标签");
        }
        String eventType = "reject".equals(decision) ? "rejected" : "feedback";
        Map<String, Object> event = appendEvent(request, userId, eventType, decision, rating, comment, issueTags, evidence, body);
        String nextStatus = "accept".equals(decision) ? "accepted" : "revision_required".equals(decision) ? "revision_required" : "rejected";
        // An "accept" feedback is also a formal acceptance when callers use
        // the generic feedback endpoint. Keep its projection identical to the
        // dedicated /accept endpoint so accepted_at and the bulk gate cannot
        // diverge between clients.
        updateProjection(request, userId, nextStatus, "accept".equals(decision), "revision_required".equals(decision));
        transitionProject(request, userId, "accept".equals(decision) ? "sample_accepted" : "needs_revision",
                "sample_feedback_" + decision, Map.of("requestId", requestId, "decision", decision, "feedbackId", event.get("id")));
        return lifecycle(requestId, userId);
    }

    @Transactional
    public Map<String, Object> requestRevision(Long requestId, Long userId, Map<String, Object> body) {
        RequestContext request = ownedSampleRequest(requestId, userId);
        rejectClosedLifecycle(request);
        requireSampleInProduction(request);
        String comment = limit(text(body == null ? null : body.get("comment")), 2000);
        List<Object> issueTags = list(body == null ? null : body.get("issueTags"));
        if (blank(comment) && issueTags.isEmpty()) throw new IllegalArgumentException("返修说明不能为空");
        Map<String, Object> event = appendEvent(request, userId, "revision_requested", "revision_required", 0,
                comment, issueTags, list(body == null ? null : body.get("evidenceAssetIds")), body);
        int updated = jdbc.update("UPDATE consumer_production_request SET sample_workflow_status='revision_required',sample_revision_count=COALESCE(sample_revision_count,0)+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND sample_workflow_status=?", requestId, userId, request.workflowStatus);
        if (updated == 0) throw new IllegalStateException("样品状态已发生变化，请刷新后重试");
        transitionProject(request, userId, "needs_revision", "sample_revision_requested", Map.of("requestId", requestId, "revisionId", event.get("id")));
        return lifecycle(requestId, userId);
    }

    @Transactional
    public Map<String, Object> acceptSample(Long requestId, Long userId, Map<String, Object> body) {
        RequestContext request = ownedSampleRequest(requestId, userId);
        if (!"sample".equalsIgnoreCase(request.requestType)) throw new IllegalArgumentException("只有打样申请可以验收样品");
        rejectClosedLifecycle(request);
        requireSampleInProduction(request);
        String comment = limit(text(body == null ? null : body.get("comment")), 2000);
        List<Object> evidence = list(body == null ? null : body.get("evidenceAssetIds"));
        Map<String, Object> event = appendEvent(request, userId, "accepted", "accept", integer(body == null ? null : body.get("rating"), 0), comment,
                list(body == null ? null : body.get("issueTags")), evidence, body);
        updateProjection(request, userId, "accepted", true, false);
        transitionProject(request, userId, "sample_accepted", "sample_accepted", Map.of("requestId", requestId, "acceptanceId", event.get("id")));
        return lifecycle(requestId, userId);
    }

    @Transactional
    public Map<String, Object> unlockBulk(Long requestId, Long userId, Map<String, Object> body) {
        RequestContext request = ownedSampleRequest(requestId, userId);
        if (!"sample".equalsIgnoreCase(request.requestType)) throw new IllegalArgumentException("只有打样申请可以解锁量产");
        requireSampleExecutionGate(request);
        if (!"accepted".equalsIgnoreCase(request.workflowStatus)) throw new IllegalStateException("样品尚未验收通过，不能解锁量产");
        Map<String, Object> event = appendEvent(request, userId, "bulk_unlocked", "accept", 0,
                limit(text(body == null ? null : body.get("comment")), 2000), List.of(), list(body == null ? null : body.get("evidenceAssetIds")), body);
        int updated = jdbc.update("UPDATE consumer_production_request SET sample_workflow_status='bulk_unlocked',bulk_unlocked_at=CURRENT_TIMESTAMP,bulk_unlocked_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND sample_workflow_status=?", userId, requestId, userId, request.workflowStatus);
        if (updated == 0) throw new IllegalStateException("样品状态已发生变化，请刷新后重试");
        transitionProject(request, userId, "bulk_unlocked", "bulk_unlocked", Map.of("requestId", requestId, "unlockId", event.get("id")));
        return lifecycle(requestId, userId);
    }

    public Map<String, Object> lifecycle(Long requestId, Long userId) {
        ownedSampleRequest(requestId, userId);
        Map<String, Object> result;
        try {
            result = jdbc.queryForMap("SELECT id,request_no requestNo,request_type requestType,status,sample_payment_status samplePaymentStatus,project_id projectId,version_id versionId,sample_workflow_status sampleWorkflowStatus,sample_received_at sampleReceivedAt,sample_accepted_at sampleAcceptedAt,sample_revision_count sampleRevisionCount,bulk_unlocked_at bulkUnlockedAt,bulk_unlocked_by bulkUnlockedBy FROM consumer_production_request WHERE id=? AND user_id=?", requestId, userId);
        } catch (DataAccessException oldSchema) {
            result = jdbc.queryForMap("SELECT id,request_no requestNo,request_type requestType,status,sample_payment_status samplePaymentStatus FROM consumer_production_request WHERE id=? AND user_id=?", requestId, userId);
            result.put("projectId", null);
            result.put("versionId", null);
            String status = text(result.get("status"));
            result.put("sampleWorkflowStatus", "shipped".equalsIgnoreCase(status) ? "shipped"
                    : "processing".equalsIgnoreCase(status) ? "in_production" : "not_started");
            result.put("sampleReceivedAt", null);
            result.put("sampleAcceptedAt", null);
            result.put("sampleRevisionCount", 0);
            result.put("bulkUnlockedAt", null);
            result.put("bulkUnlockedBy", null);
        }
        try {
            result.put("events", jdbc.queryForList("SELECT id,request_id requestId,event_type eventType,decision,rating,comment,issue_tags_json issueTagsJson,evidence_asset_ids_json evidenceAssetIdsJson,payload_json payloadJson,created_by createdBy,created_at createdAt FROM creative_sample_lifecycle_event WHERE request_id=? ORDER BY id ASC", requestId));
        } catch (DataAccessException missingLifecycleTable) {
            result.put("events", List.of());
        }
        return result;
    }

    /** Validates the URL project/version scope before a lifecycle mutation. */
    public void assertRoute(Long requestId, Long projectId, Long versionId, Long userId) {
        RequestContext request = ownedSampleRequest(requestId, userId);
        if (!Objects.equals(request.projectId, projectId) || !Objects.equals(request.versionId, versionId)) {
            throw new NoSuchElementException("样品申请不属于当前项目版本");
        }
    }

    private Map<String, Object> appendEvent(RequestContext request, Long userId, String type, String decision, int rating,
                                            String comment, List<Object> issueTags, List<Object> evidence, Map<String, Object> body) {
        String issues = json(issueTags), proof = json(evidence), payload = json(body == null ? Map.of() : body);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO creative_sample_lifecycle_event (request_id,project_id,version_id,user_id,event_type,decision,rating,comment,issue_tags_json,evidence_asset_ids_json,payload_json,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, request.id);
            if (request.projectId == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, request.projectId);
            if (request.versionId == null) ps.setNull(3, java.sql.Types.BIGINT); else ps.setLong(3, request.versionId);
            ps.setLong(4, userId);
            ps.setString(5, type);
            ps.setString(6, decision);
            if (rating <= 0) ps.setNull(7, java.sql.Types.INTEGER); else ps.setInt(7, rating);
            ps.setString(8, comment);
            ps.setString(9, issues);
            ps.setString(10, proof);
            ps.setString(11, payload);
            ps.setLong(12, userId);
            return ps;
        }, keyHolder);
        Number key = generatedId(keyHolder);
        if (key == null) throw new IllegalStateException("样品反馈记录保存失败");
        return Map.of("id", key.longValue());
    }

    private void updateProjection(RequestContext request, Long userId, String status, boolean received, boolean revision) {
        String revisionPart = revision ? ",sample_revision_count=COALESCE(sample_revision_count,0)+1" : "";
        String sql = received
                ? "UPDATE consumer_production_request SET sample_workflow_status=?,sample_received_at=COALESCE(sample_received_at,CURRENT_TIMESTAMP),sample_accepted_at=CURRENT_TIMESTAMP" + revisionPart + ",updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?"
                : "UPDATE consumer_production_request SET sample_workflow_status=?,sample_received_at=COALESCE(sample_received_at,CURRENT_TIMESTAMP)" + revisionPart + ",updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?";
        int updated = jdbc.update(sql + " AND sample_workflow_status=?", status, request.id, userId, request.workflowStatus);
        if (updated == 0) throw new IllegalStateException("样品状态已发生变化，请刷新后重试");
    }

    private void transitionProject(RequestContext request, Long userId, String phase, String eventType, Map<String, Object> payload) {
        if (request.projectId == null || request.versionId == null) return;
        try {
            projects.transitionProject(request.projectId, request.versionId, userId, phase, eventType, "user", userId, payload);
        } catch (IllegalStateException ignored) {
            projects.recordWorkflowEvent(request.projectId, request.versionId, userId, eventType, "user", userId, payload);
        }
    }

    private RequestContext ownedSampleRequest(Long requestId, Long userId) {
        if (requestId == null || requestId <= 0) throw new IllegalArgumentException("申请编号无效");
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT id,request_type requestType,status,sample_workflow_status workflowStatus,sample_payment_status paymentStatus,project_id projectId,version_id versionId FROM consumer_production_request WHERE id=? AND user_id=? FOR UPDATE", requestId, userId);
        } catch (DataAccessException missingProjectColumns) {
            // project_id/version_id were added after the original request
            // table. Lifecycle operations still need ownership and payment
            // gates while an older node finishes its additive migration.
            rows = jdbc.queryForList("SELECT id,request_type requestType,status,sample_workflow_status workflowStatus,sample_payment_status paymentStatus,NULL projectId,NULL versionId FROM consumer_production_request WHERE id=? AND user_id=? FOR UPDATE", requestId, userId);
        }
        if (rows.isEmpty()) throw new NoSuchElementException("打样申请不存在或无权访问");
        Map<String, Object> row = rows.get(0);
        Number p = number(row.get("projectId")), v = number(row.get("versionId"));
        if (p != null && v != null) projects.getVersion(p.longValue(), v.longValue(), userId);
        return new RequestContext(requestId, text(row.get("requestType")), text(row.get("workflowStatus")), text(row.get("status")),
                text(row.get("paymentStatus")), p == null ? null : p.longValue(), v == null ? null : v.longValue());
    }

    /** The payment/review gate and the physical-delivery gate are separate. */
    private void requireSampleInProduction(RequestContext request) {
        if (!"sample".equalsIgnoreCase(request.requestType)) return;
        requireSampleExecutionGate(request);
        String workflowStatus = request.workflowStatus == null ? "" : request.workflowStatus.toLowerCase(Locale.ROOT);
        if (!Set.of("shipped", "received").contains(workflowStatus)) {
            throw new IllegalStateException("样品尚未寄出或进入待反馈状态，暂不能提交收货反馈或验收");
        }
    }

    private void requireSampleExecutionGate(RequestContext request) {
        if (!"sample".equalsIgnoreCase(request.requestType)) return;
        boolean paidOrFreeAndRunning = Set.of("processing", "shipped", "completed").contains(request.status)
                && Set.of("paid", "not_required").contains(request.paymentStatus.toLowerCase(Locale.ROOT));
        boolean freeAndApproved = "not_required".equalsIgnoreCase(request.paymentStatus)
                && Set.of("approved", "processing", "shipped", "completed").contains(request.status.toLowerCase(Locale.ROOT));
        if (!paidOrFreeAndRunning && !freeAndApproved) {
            throw new IllegalStateException("样品尚未进入生产流程，暂不能提交收货反馈或验收");
        }
    }

    private void rejectClosedLifecycle(RequestContext request) {
        if (Set.of("accepted", "bulk_unlocked", "rejected").contains(request.workflowStatus.toLowerCase(Locale.ROOT))) {
            throw new IllegalStateException("当前样品生命周期已结束，不能重复反馈或返修");
        }
    }

    private record RequestContext(Long id, String requestType, String workflowStatus, String status, String paymentStatus, Long projectId, Long versionId) {}
    private Number number(Object value) { return value instanceof Number n ? n : value == null ? null : parseNumber(value); }
    private Number parseNumber(Object value) { try { return Long.valueOf(String.valueOf(value)); } catch (Exception e) { return null; } }
    private int integer(Object value, int fallback) { try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); } catch (Exception e) { throw new IllegalArgumentException("数字格式无效"); } }
    private List<Object> list(Object value) { if (!(value instanceof Collection<?> c)) return new ArrayList<>(); return new ArrayList<>(c); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String limit(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(0, max); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String json(Object value) { try { return mapper.writeValueAsString(value == null ? Map.of() : value); } catch (JsonProcessingException e) { throw new IllegalArgumentException("反馈数据格式无效"); } }

    private Number generatedId(KeyHolder holder) {
        try {
            Number key = holder.getKey();
            if (key != null) return key;
        } catch (org.springframework.dao.InvalidDataAccessApiUsageException ignored) {
            // Some drivers expose generated columns through the key map.
        }
        for (Map<String, Object> row : holder.getKeyList()) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if ("ID".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number number) return number;
            }
        }
        return null;
    }
}
