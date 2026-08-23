package com.example.shixun.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Factory-side projection for sample production.  C端 feedback remains owned by
 * SampleLifecycleService; this service only records operational milestones and
 * never changes the customer's feedback or acceptance events.
 */
@Service
public class FactorySampleLifecycleService {
    private static final Set<String> FACTORY_STATUSES = Set.of(
            "in_production", "ready_to_ship", "shipped", "revision_in_progress", "revision_completed");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CreativeProjectService projects;

    @Autowired
    public FactorySampleLifecycleService(JdbcTemplate jdbc, ObjectMapper mapper, CreativeProjectService projects) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.projects = projects;
    }

    /** Compatibility constructor for focused tests and older embedders. */
    public FactorySampleLifecycleService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this(jdbc, mapper, new CreativeProjectService(jdbc, mapper));
    }

    public List<Map<String, Object>> list(String workflowStatus, String keyword, int size) {
        int limit = Math.max(1, Math.min(size, 500));
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT r.id, r.request_no requestNo, r.user_id userId, u.username, "
                + "r.request_type requestType, r.title, r.quantity, r.status, "
                + "r.sample_product_name sampleProductName, r.sample_fee_yuan sampleFeeYuan, "
                + "r.sample_payment_status samplePaymentStatus, r.sample_workflow_status sampleWorkflowStatus, "
                + "r.sample_received_at sampleReceivedAt, r.sample_accepted_at sampleAcceptedAt, "
                + "r.sample_revision_count sampleRevisionCount, r.bulk_unlocked_at bulkUnlockedAt, "
                + "r.project_id projectId, r.version_id versionId, r.asset_id assetId, a.title assetTitle, "
                + "r.recipient_name recipientName, r.recipient_phone recipientPhone, r.recipient_address recipientAddress, "
                + "r.created_at createdAt, r.updated_at updatedAt "
                + "FROM consumer_production_request r JOIN user u ON u.id=r.user_id "
                + "LEFT JOIN digital_asset a ON a.id=r.asset_id WHERE r.request_type='sample'");
        if (workflowStatus != null && !workflowStatus.isBlank()) {
            if (!FACTORY_STATUSES.contains(workflowStatus) && !Set.of("not_started", "revision_required", "received", "accepted", "rejected", "bulk_unlocked").contains(workflowStatus)) {
                throw new IllegalArgumentException("不支持的样品生命周期状态");
            }
            sql.append(" AND r.sample_workflow_status=?");
            args.add(workflowStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim() + "%";
            sql.append(" AND (r.request_no LIKE ? OR r.title LIKE ? OR r.sample_product_name LIKE ? OR u.username LIKE ?)");
            args.add(like); args.add(like); args.add(like); args.add(like);
        }
        sql.append(" ORDER BY r.updated_at DESC, r.id DESC LIMIT ?");
        args.add(limit);
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = canonicalize(rows.get(i));
            addLatestEvent(row);
            addLogisticsSummary(row);
            rows.set(i, row);
        }
        return rows;
    }

    public Map<String, Object> detail(Long requestId) {
        Map<String, Object> row = find(requestId);
        List<Map<String, Object>> events = jdbc.queryForList("SELECT id, request_id requestId, project_id projectId, version_id versionId, "
                + "event_type eventType, decision, rating, comment, issue_tags_json issueTagsJson, "
                + "evidence_asset_ids_json evidenceAssetIdsJson, payload_json payloadJson, created_by createdBy, created_at createdAt "
                + "FROM creative_sample_lifecycle_event WHERE request_id=? ORDER BY id ASC", requestId);
        for (int i = 0; i < events.size(); i++) events.set(i, canonicalize(events.get(i)));
        row.put("events", events);
        addLogisticsSummary(row);
        return row;
    }

    /** Optional enrichment keeps the lifecycle list useful during rolling upgrades. */
    private void addLogisticsSummary(Map<String, Object> row) {
        try {
            List<Map<String, Object>> values = jdbc.queryForList(
                    "SELECT id logisticsId,carrier_code carrierCode,carrier_name carrierName,tracking_no trackingNo,status logisticsStatus,latest_trace latestTrace,alert_level alertLevel,alert_status alertStatus,exception_note exceptionNote,shipped_at shippedAt,signed_at signedAt,estimated_arrival estimatedArrival FROM creative_sample_logistics WHERE request_id=?",
                    value(row, "id"));
            row.put("logistics", values.isEmpty() ? null : canonicalize(values.get(0)));
        } catch (Exception ignored) {
            row.put("logistics", null);
        }
    }

    @Transactional
    public Map<String, Object> updateStatus(Long requestId, String nextStatus, Long operatorId,
                                             String operatorName, String comment, List<?> evidenceAssetIds) {
        if (requestId == null || requestId <= 0) throw new IllegalArgumentException("申请编号无效");
        if (!FACTORY_STATUSES.contains(nextStatus)) throw new IllegalArgumentException("不支持的工厂处理状态");
        Map<String, Object> current = findForUpdate(requestId);
        String previous = text(current.get("sampleWorkflowStatus"), "not_started");
        if (Objects.equals(previous, nextStatus)) throw new IllegalStateException("样品已经处于该状态");
        requireProductionReady(current, nextStatus);
        if (Set.of("accepted", "bulk_unlocked", "rejected").contains(previous)) {
            throw new IllegalStateException("样品验收流程已结束，不能继续更新工厂状态");
        }
        if (!allowed(previous, nextStatus)) {
            throw new IllegalStateException("不能从“" + previous + "”直接变更为“" + nextStatus + "”");
        }
        String requestStatus = switch (nextStatus) {
            case "in_production", "ready_to_ship", "revision_in_progress", "revision_completed" -> "processing";
            case "shipped" -> "shipped";
            default -> text(current.get("status"), "processing");
        };
        int updated = jdbc.update("UPDATE consumer_production_request SET sample_workflow_status=?, status=?, updated_at=CURRENT_TIMESTAMP WHERE id=? AND sample_workflow_status=?",
                nextStatus, requestStatus, requestId, previous);
        if (updated == 0) throw new IllegalStateException("样品状态已发生变化，请刷新后重试");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousStatus", previous);
        payload.put("nextStatus", nextStatus);
        payload.put("operator", operatorName == null ? "" : operatorName);
        if (comment != null && !comment.isBlank()) payload.put("comment", comment.trim());
        String evidence = json(evidenceAssetIds == null ? List.of() : evidenceAssetIds);
        jdbc.update("INSERT INTO creative_sample_lifecycle_event (request_id, project_id, version_id, user_id, event_type, decision, comment, evidence_asset_ids_json, payload_json, created_by) VALUES (?,?,?,?,?,?,?,?,?,?)",
                requestId, current.get("projectId"), current.get("versionId"), current.get("userId"),
                eventType(nextStatus), null, limit(comment, 2000), evidence, json(payload), operatorId);
        advanceProject(current, nextStatus, requestId, operatorId);
        return detail(requestId);
    }

    private void advanceProject(Map<String, Object> request, String nextStatus, Long requestId, Long operatorId) {
        Number projectId = number(request.get("projectId"));
        Number versionId = number(request.get("versionId"));
        Number ownerId = number(request.get("userId"));
        if (projectId == null || versionId == null || ownerId == null) return;
        String phase = switch (nextStatus) {
            case "shipped" -> "sample_review";
            case "revision_in_progress", "revision_completed" -> "needs_revision";
            default -> "sampling";
        };
        Map<String, Object> payload = Map.of("requestId", requestId, "sampleStatus", nextStatus);
        try {
            projects.transitionProject(projectId.longValue(), versionId.longValue(), ownerId.longValue(),
                    phase, "factory_sample_" + nextStatus, "staff", operatorId, payload);
        } catch (IllegalStateException ignored) {
            // The request/lifecycle projection remains authoritative if an old
            // project version cannot accept a duplicate operational event.
            try {
                projects.recordWorkflowEvent(projectId.longValue(), versionId.longValue(), ownerId.longValue(),
                        "factory_sample_" + nextStatus, "staff", operatorId, payload);
            } catch (RuntimeException ignoredTimeline) {
                // Keep factory status updates usable during a rolling upgrade.
            }
        }
    }

    private boolean allowed(String previous, String next) {
        return switch (next) {
            case "in_production" -> "not_started".equals(previous);
            case "ready_to_ship" -> Set.of("in_production", "revision_completed").contains(previous);
            // A repaired sample must be marked ready_to_ship before a carrier
            // can be attached; otherwise the out-of-sample checkpoint and its
            // audit event are silently skipped.
            case "shipped" -> "ready_to_ship".equals(previous);
            case "revision_in_progress" -> "revision_required".equals(previous);
            case "revision_completed" -> "revision_in_progress".equals(previous);
            default -> false;
        };
    }

    /** A factory may not bypass the C端 approval and sample-fee gates. */
    private void requireProductionReady(Map<String, Object> current, String nextStatus) {
        if (!"in_production".equals(nextStatus) && !"revision_in_progress".equals(nextStatus)) return;
        String requestStatus = text(current.get("status"), "");
        String paymentStatus = text(current.get("samplePaymentStatus"), "");
        boolean approved = Set.of("approved", "processing", "shipped", "completed").contains(requestStatus);
        boolean paidOrFree = Set.of("paid", "not_required").contains(paymentStatus);
        if (!approved || !paidOrFree) {
            throw new IllegalStateException("样品尚未完成审核或付款，不能进入工厂制作");
        }
    }

    private String eventType(String status) {
        return switch (status) {
            case "in_production" -> "production_started";
            case "ready_to_ship" -> "sample_ready";
            case "shipped" -> "sample_shipped";
            case "revision_in_progress" -> "revision_started";
            case "revision_completed" -> "revision_completed";
            default -> status;
        };
    }

    private Map<String, Object> find(Long requestId) {
        return find(requestId, false);
    }

    private Map<String, Object> findForUpdate(Long requestId) {
        return find(requestId, true);
    }

    private Map<String, Object> find(Long requestId, boolean lock) {
        if (requestId == null || requestId <= 0) throw new IllegalArgumentException("申请编号无效");
        String sql = "SELECT r.id, r.request_no requestNo, r.user_id userId, u.username, "
                + "r.request_type requestType, r.title, r.quantity, r.status, r.sample_product_name sampleProductName, "
                + "r.sample_fee_yuan sampleFeeYuan, r.sample_payment_status samplePaymentStatus, r.sample_workflow_status sampleWorkflowStatus, "
                + "r.sample_received_at sampleReceivedAt, r.sample_accepted_at sampleAcceptedAt, r.sample_revision_count sampleRevisionCount, "
                + "r.bulk_unlocked_at bulkUnlockedAt, r.project_id projectId, r.version_id versionId, r.asset_id assetId, a.title assetTitle, "
                + "r.recipient_name recipientName, r.recipient_phone recipientPhone, r.recipient_address recipientAddress, r.created_at createdAt, r.updated_at updatedAt "
                + "FROM consumer_production_request r JOIN user u ON u.id=r.user_id LEFT JOIN digital_asset a ON a.id=r.asset_id "
                + "WHERE r.id=? AND r.request_type='sample'" + (lock ? " FOR UPDATE" : "");
        List<Map<String, Object>> rows = jdbc.queryForList(sql, requestId);
        if (rows.isEmpty()) throw new NoSuchElementException("样品申请不存在");
        Map<String, Object> row = canonicalize(rows.get(0));
        addLatestEvent(row);
        return row;
    }

    private void addLatestEvent(Map<String, Object> row) {
        List<Map<String, Object>> latest = jdbc.queryForList("SELECT id, event_type eventType, comment, created_at createdAt FROM creative_sample_lifecycle_event WHERE request_id=? ORDER BY id DESC LIMIT 1", value(row, "id"));
        row.put("latestEvent", latest.isEmpty() ? null : canonicalize(latest.get(0)));
    }

    /** H2 uppercases unquoted aliases while MySQL preserves their spelling. */
    private Map<String, Object> canonicalize(Map<String, Object> source) {
        Map<String, String> aliases = new HashMap<>();
        for (String key : List.of("id", "requestNo", "userId", "username", "requestType", "title", "quantity", "status",
                "sampleProductName", "sampleFeeYuan", "samplePaymentStatus", "sampleWorkflowStatus", "sampleReceivedAt",
                "sampleAcceptedAt", "sampleRevisionCount", "bulkUnlockedAt", "projectId", "versionId", "assetId", "assetTitle",
                "recipientName", "recipientPhone", "recipientAddress", "createdAt", "updatedAt", "requestId", "eventType", "decision",
                "rating", "issueTagsJson", "evidenceAssetIdsJson", "payloadJson", "createdBy", "comment", "logisticsId",
                "carrierCode", "carrierName", "trackingNo", "logisticsStatus", "latestTrace", "alertLevel", "alertStatus",
                "exceptionNote", "shippedAt", "signedAt", "estimatedArrival")) {
            aliases.put(key.toLowerCase(Locale.ROOT), key);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = aliases.getOrDefault(entry.getKey().toLowerCase(Locale.ROOT), entry.getKey());
            out.put(key, entry.getValue());
        }
        return out;
    }

    private Object value(Map<String, Object> row, String key) {
        if (row.containsKey(key)) return row.get(key);
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }

    private String text(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private Number number(Object value) {
        if (value instanceof Number n) return n;
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (Exception ignored) { return null; }
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value == null ? List.of() : value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("生命周期记录格式无效"); }
    }
}
