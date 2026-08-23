package com.example.shixun.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Logistics projection for a sample request.
 *
 * <p>The older {@code logistics_shipment} table belongs to commercial orders.
 * Samples are created before a commercial order exists, so their tracking
 * state is kept in a request-scoped projection and an append-only event log.
 * This keeps legacy order tracking untouched while allowing the C端 timeline
 * and the factory console to share one source of truth.</p>
 */
@Service
public class SampleLogisticsService {
    private static final Set<String> STATUSES = Set.of(
            "pending", "shipped", "in_transit", "delivering", "signed", "exception", "returned");
    private static final Set<String> ALERT_LEVELS = Set.of("normal", "warning", "exception");
    private static final Set<String> ALERT_STATUSES = Set.of("open", "acknowledged", "resolved");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CreativeProjectService projects;

    public SampleLogisticsService(JdbcTemplate jdbc, ObjectMapper mapper, CreativeProjectService projects) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.projects = projects;
    }

    /** Returns a safe empty projection when a factory has not entered tracking yet. */
    public Map<String, Object> forConsumer(Long requestId, Long userId) {
        RequestContext request = ownedRequest(requestId, userId);
        return projection(request);
    }

    /** Factory detail is scoped by the request, not by a client-supplied user id. */
    public Map<String, Object> forStaff(Long requestId) {
        RequestContext request = request(requestId);
        return projection(request);
    }

    /**
     * Adds or updates the sample's carrier/tracking information.  A tracking
     * update is idempotent at the projection level but still leaves an audit
     * event so operators can see who changed it and why.
     */
    @Transactional
    public Map<String, Object> update(Long requestId, Long operatorId, Map<String, Object> body) {
        RequestContext request = requestForUpdate(requestId);
        Map<String, Object> previous = existing(requestId);

        String oldTracking = text(value(previous, "trackingNo"));
        String carrierCode = firstNonBlank(text(body == null ? null : body.get("carrierCode")), text(value(previous, "carrierCode")));
        String carrierName = firstNonBlank(text(body == null ? null : body.get("carrierName")), text(value(previous, "carrierName")));
        String trackingNo = firstNonBlank(text(body == null ? null : body.get("trackingNo")), oldTracking);
        carrierCode = limit(carrierCode, 50);
        carrierName = limit(carrierName, 80);
        if (!blank(trackingNo)) validateTrackingNo(trackingNo);
        if (!blank(carrierCode) && blank(carrierName)) carrierName = carrierName(carrierCode);
        if (!blank(trackingNo) && blank(carrierCode)) throw new IllegalArgumentException("填写快递单号时必须选择承运商");

        String oldStatus = firstNonBlank(text(value(previous, "status")), "pending");
        String requestedStatus = text(body == null ? null : body.get("status"));
        String status = blank(requestedStatus) ? (!blank(trackingNo) && "pending".equals(oldStatus) ? "shipped" : oldStatus) : requestedStatus;
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("不支持的样品物流状态");
        if (previous.isEmpty() && !Set.of("pending", "shipped", "exception").contains(status)) {
            throw new IllegalStateException("新建物流记录只能处于待发货、已发货或异常状态");
        }
        requireSampleReady(request, status);
        if (!previous.isEmpty() && !Objects.equals(oldStatus, status) && !allowedStatus(oldStatus, status)) {
            throw new IllegalStateException("不能从“" + oldStatus + "”直接变更为“" + status + "”");
        }
        if (!blank(trackingNo)) {
            List<Map<String, Object>> duplicate = jdbc.queryForList("SELECT request_id requestId FROM creative_sample_logistics WHERE tracking_no=? AND request_id<>? LIMIT 1", trackingNo, request.id);
            if (!duplicate.isEmpty()) throw new IllegalArgumentException("该快递单号已绑定其他样品申请");
        }

        String oldAlertLevel = firstNonBlank(text(value(previous, "alertLevel")), "normal");
        String requestedAlertLevel = text(body == null ? null : body.get("alertLevel"));
        String exceptionNote = limit(text(body == null ? null : body.get("exceptionNote")), 2000);
        String alertLevel = blank(requestedAlertLevel)
                ? (blank(exceptionNote) ? oldAlertLevel : "exception") : requestedAlertLevel;
        if (!ALERT_LEVELS.contains(alertLevel)) throw new IllegalArgumentException("不支持的异常级别");
        if ("exception".equals(status) && blank(exceptionNote)) {
            exceptionNote = firstNonBlank(text(value(previous, "exceptionNote")), "样品物流异常，需要人工核实");
        }
        if ("exception".equals(alertLevel) && blank(exceptionNote)) {
            throw new IllegalArgumentException("标记物流异常时请填写异常说明");
        }

        String requestedAlertStatus = text(body == null ? null : body.get("alertStatus"));
        String previousAlertStatus = firstNonBlank(text(value(previous, "alertStatus")), "resolved");
        String alertStatus = blank(requestedAlertStatus)
                ? ("normal".equals(alertLevel) ? "resolved" : Set.of("open", "acknowledged").contains(previousAlertStatus) ? previousAlertStatus : "open")
                : requestedAlertStatus;
        if (!ALERT_STATUSES.contains(alertStatus)) throw new IllegalArgumentException("不支持的异常处理状态");
        if ("normal".equals(alertLevel)) alertStatus = "resolved";

        String latestTrace = firstNonBlank(limit(text(body == null ? null : body.get("latestTrace")), 1000), exceptionNote,
                text(value(previous, "latestTrace")), statusText(status));
        Timestamp estimated = timestamp(body == null ? null : body.get("estimatedArrival"));
        if (estimated == null) estimated = timestamp(value(previous, "estimatedArrival"));
        final String insertCarrierCode = carrierCode;
        final String insertCarrierName = carrierName;
        final String insertTrackingNo = trackingNo;
        final String insertLatestTrace = latestTrace;
        final String insertStatus = status;
        final String insertAlertLevel = alertLevel;
        final String insertAlertStatus = alertStatus;
        final String insertExceptionNote = exceptionNote;
        final Timestamp insertEstimated = estimated;

        final LocalDateTime now = LocalDateTime.now();
        final long requestDbId = request.id;
        final long requestUserId = request.userId;
        final Long operatorDbId = operatorId;
        Long logisticsId;
        if (previous.isEmpty()) {
            final Timestamp shippedAt = shipmentTime(insertStatus, now, null);
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO creative_sample_logistics (request_id,user_id,carrier_code,carrier_name,tracking_no,status,latest_trace,alert_level,alert_status,exception_note,shipped_at,signed_at,estimated_arrival,last_synced_at,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
                ps.setLong(1, requestDbId);
                ps.setLong(2, requestUserId);
                ps.setString(3, blank(insertCarrierCode) ? null : insertCarrierCode);
                ps.setString(4, blank(insertCarrierName) ? null : insertCarrierName);
                ps.setString(5, blank(insertTrackingNo) ? null : insertTrackingNo);
                ps.setString(6, insertStatus);
                ps.setString(7, insertLatestTrace);
                ps.setString(8, insertAlertLevel);
                ps.setString(9, insertAlertStatus);
                ps.setString(10, blank(insertExceptionNote) ? null : insertExceptionNote);
                ps.setTimestamp(11, shippedAt);
                ps.setTimestamp(12, "signed".equals(insertStatus) ? Timestamp.valueOf(now) : null);
                ps.setTimestamp(13, insertEstimated);
                ps.setTimestamp(14, Timestamp.valueOf(now));
                if (operatorDbId == null) ps.setNull(15, java.sql.Types.BIGINT); else ps.setLong(15, operatorDbId);
                return ps;
            });
            logisticsId = queryId(request.id);
        } else {
            logisticsId = number(value(previous, "id")) == null ? queryId(request.id) : number(value(previous, "id")).longValue();
            Timestamp shippedAt = shipmentTime(status, now, timestamp(value(previous, "shippedAt")));
            Timestamp signedAt = "signed".equals(status) ? Timestamp.valueOf(now) : timestamp(value(previous, "signedAt"));
            jdbc.update("UPDATE creative_sample_logistics SET carrier_code=?,carrier_name=?,tracking_no=?,status=?,latest_trace=?,alert_level=?,alert_status=?,exception_note=?,shipped_at=?,signed_at=?,estimated_arrival=?,last_synced_at=CURRENT_TIMESTAMP,created_by=?,updated_at=CURRENT_TIMESTAMP WHERE request_id=?",
                    blank(carrierCode) ? null : carrierCode, blank(carrierName) ? null : carrierName,
                    blank(trackingNo) ? null : trackingNo, status, latestTrace, alertLevel, alertStatus,
                    blank(exceptionNote) ? null : exceptionNote, shippedAt, signedAt, estimated, operatorId, request.id);
        }

        boolean exception = "exception".equals(status) || "exception".equals(alertLevel);
        boolean resolved = "exception".equals(text(value(previous, "alertLevel")))
                && !exception && "resolved".equals(alertStatus);
        String eventType = exception ? "exception_marked" : resolved ? "exception_resolved"
                : previous.isEmpty() || !Objects.equals(oldTracking, trackingNo) ? "tracking_updated" : "status_changed";
        String comment = firstNonBlank(limit(text(body == null ? null : body.get("comment")), 1000),
                exception ? exceptionNote : "样品物流状态更新为" + statusText(status));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousStatus", oldStatus);
        payload.put("status", status);
        payload.put("previousAlertLevel", oldAlertLevel);
        payload.put("alertLevel", alertLevel);
        payload.put("alertStatus", alertStatus);
        if (!blank(oldTracking)) payload.put("previousTrackingNo", oldTracking);
        if (!blank(trackingNo)) payload.put("trackingNo", trackingNo);
        if (!blank(comment)) payload.put("comment", comment);
        if (logisticsId == null) throw new IllegalStateException("样品物流记录创建失败");
        appendEvent(logisticsId, request, eventType, status, alertLevel, null, comment, payload, operatorId);

        // Keep the existing sample progress projection coherent when the first
        // tracking number is entered from the factory console.
        if ("shipped".equals(status) && "ready_to_ship".equals(request.workflowStatus)) {
            jdbc.update("UPDATE consumer_production_request SET sample_workflow_status='shipped',status='shipped',updated_at=CURRENT_TIMESTAMP WHERE id=? AND sample_workflow_status='ready_to_ship'", request.id);
            try {
                appendLifecycleEvent(request, operatorId, "sample_shipped", comment, Map.of("trackingNo", trackingNo));
            } catch (Exception ignored) {
                // The logistics projection remains valid during a rolling upgrade
                // where the lifecycle table may not exist yet.
            }
        }
        advanceProject(request, status, operatorId);
        return projection(request(requestId));
    }

    private void advanceProject(RequestContext request, String logisticsStatus, Long operatorId) {
        if (request.projectId == null || request.versionId == null) return;
        // Logistics changes do not grant acceptance. They only move the
        // project into the sample-review/readiness phase until the customer
        // submits feedback through SampleLifecycleService.
        if (!Set.of("shipped", "in_transit", "delivering", "signed").contains(logisticsStatus)) return;
        Map<String, Object> payload = Map.of("requestId", request.id, "logisticsStatus", logisticsStatus);
        try {
            projects.transitionProject(request.projectId.longValue(), request.versionId.longValue(), request.userId,
                    "sample_review", "sample_logistics_" + logisticsStatus, "staff", operatorId, payload);
        } catch (IllegalStateException ignored) {
            try {
                projects.recordWorkflowEvent(request.projectId.longValue(), request.versionId.longValue(), request.userId,
                        "sample_logistics_" + logisticsStatus, "staff", operatorId, payload);
            } catch (RuntimeException ignoredTimeline) {
                // Keep logistics usable while an older project schema is still
                // serving requests.
            }
        }
    }

    @Transactional
    public Map<String, Object> markException(Long requestId, Long operatorId, Map<String, Object> body) {
        Map<String, Object> next = new LinkedHashMap<>();
        if (body != null) next.putAll(body);
        next.put("status", "exception");
        next.put("alertLevel", "exception");
        if (blank(text(next.get("exceptionNote")))) next.put("exceptionNote", "样品物流异常，需要人工核实");
        if (blank(text(next.get("comment")))) next.put("comment", text(next.get("exceptionNote")));
        return update(requestId, operatorId, next);
    }

    @Transactional
    public Map<String, Object> resolveException(Long requestId, Long operatorId, Map<String, Object> body) {
        RequestContext request = requestForUpdate(requestId);
        Map<String, Object> current = existing(requestId);
        if (current.isEmpty()) throw new IllegalStateException("该样品尚未建立物流记录");
        Map<String, Object> next = new LinkedHashMap<>();
        String currentStatus = text(value(current, "status"));
        String requestedStatus = text(body == null ? null : body.get("status"));
        next.put("status", firstNonBlank(requestedStatus, "exception".equals(currentStatus) ? "in_transit" : currentStatus, "in_transit"));
        next.put("alertLevel", "normal");
        next.put("alertStatus", "resolved");
        next.put("comment", firstNonBlank(text(body == null ? null : body.get("comment")), "物流异常已处理"));
        next.put("trackingNo", value(current, "trackingNo"));
        next.put("carrierCode", value(current, "carrierCode"));
        next.put("carrierName", value(current, "carrierName"));
        next.put("latestTrace", firstNonBlank(text(body == null ? null : body.get("latestTrace")), text(value(current, "latestTrace"))));
        return update(requestId, operatorId, next);
    }

    public List<Map<String, Object>> alerts(int size) {
        int limit = Math.max(1, Math.min(size, 200));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT l.id,l.request_id requestId,r.request_no requestNo,l.user_id userId,u.username,r.title,r.sample_product_name sampleProductName,r.sample_workflow_status sampleWorkflowStatus,l.carrier_code carrierCode,l.carrier_name carrierName,l.tracking_no trackingNo,l.status,l.latest_trace latestTrace,l.alert_level alertLevel,l.alert_status alertStatus,l.exception_note exceptionNote,l.updated_at updatedAt "
                        + "FROM creative_sample_logistics l JOIN consumer_production_request r ON r.id=l.request_id LEFT JOIN user u ON u.id=l.user_id "
                        + "WHERE l.alert_status IN ('open','acknowledged') AND l.alert_level IN ('warning','exception') ORDER BY CASE l.alert_level WHEN 'exception' THEN 0 WHEN 'warning' THEN 1 ELSE 2 END,l.updated_at DESC,l.id DESC LIMIT ?", limit);
        return rows.stream().map(this::canonicalize).toList();
    }

    private Map<String, Object> projection(RequestContext request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", request.id);
        result.put("requestNo", request.requestNo);
        result.put("userId", request.userId);
        result.put("status", "pending");
        result.put("alertLevel", "normal");
        result.put("alertStatus", "resolved");
        result.put("traces", List.of());
        Map<String, Object> current = existing(request.id);
        if (!current.isEmpty()) {
            result.putAll(canonicalize(current));
            Number id = number(value(current, "id"));
            if (id != null) result.put("traces", jdbc.queryForList("SELECT id,logistics_id logisticsId,request_id requestId,event_type eventType,status,alert_level alertLevel,location,content,payload_json payloadJson,created_by createdBy,created_at createdAt FROM creative_sample_logistics_event WHERE logistics_id=? ORDER BY created_at DESC,id DESC", id.longValue()).stream().map(this::canonicalize).toList());
        }
        return result;
    }

    private RequestContext ownedRequest(Long requestId, Long userId) {
        RequestContext request = request(requestId);
        if (!Objects.equals(request.userId, userId)) throw new NoSuchElementException("样品申请不存在或无权访问");
        if (request.projectId != null && request.versionId != null) projects.getVersion(request.projectId.longValue(), request.versionId.longValue(), userId);
        return request;
    }

    private RequestContext request(Long requestId) {
        return request(requestId, false);
    }

    private RequestContext requestForUpdate(Long requestId) {
        return request(requestId, true);
    }

    private RequestContext request(Long requestId, boolean lock) {
        if (requestId == null || requestId <= 0) throw new IllegalArgumentException("申请编号无效");
        String sql = "SELECT id,request_no requestNo,user_id userId,request_type requestType,status requestStatus,sample_payment_status paymentStatus,sample_workflow_status workflowStatus,project_id projectId,version_id versionId FROM consumer_production_request WHERE id=? AND request_type='sample'" + (lock ? " FOR UPDATE" : "");
        List<Map<String, Object>> rows = jdbc.queryForList(sql, requestId);
        if (rows.isEmpty()) throw new NoSuchElementException("打样申请不存在");
        Map<String, Object> row = canonicalize(rows.get(0));
        return new RequestContext(requestId, number(value(row, "userId")).longValue(), text(value(row, "requestNo")),
                text(value(row, "requestType")), text(value(row, "requestStatus")), text(value(row, "paymentStatus")),
                text(value(row, "workflowStatus")), number(value(row, "projectId")), number(value(row, "versionId")));
    }

    private Map<String, Object> existing(Long requestId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,request_id requestId,user_id userId,carrier_code carrierCode,carrier_name carrierName,tracking_no trackingNo,status,latest_trace latestTrace,alert_level alertLevel,alert_status alertStatus,exception_note exceptionNote,shipped_at shippedAt,signed_at signedAt,estimated_arrival estimatedArrival,last_synced_at lastSyncedAt,created_by createdBy,created_at createdAt,updated_at updatedAt FROM creative_sample_logistics WHERE request_id=?", requestId);
        return rows.isEmpty() ? Map.of() : canonicalize(rows.get(0));
    }

    private Long queryId(Long requestId) {
        Number id = jdbc.queryForObject("SELECT id FROM creative_sample_logistics WHERE request_id=?", Number.class, requestId);
        return id == null ? null : id.longValue();
    }

    private void appendEvent(Long logisticsId, RequestContext request, String type, String status, String level,
                             String location, String content, Map<String, Object> payload, Long operatorId) {
        jdbc.update("INSERT INTO creative_sample_logistics_event (logistics_id,request_id,event_type,status,alert_level,location,content,payload_json,created_by) VALUES (?,?,?,?,?,?,?,CAST(? AS JSON),?)",
                logisticsId, request.id, type, status, level, location, limit(content, 1000), json(payload), operatorId);
    }

    private void appendLifecycleEvent(RequestContext request, Long operatorId, String type, String comment, Map<String, Object> payload) {
        jdbc.update("INSERT INTO creative_sample_lifecycle_event (request_id,project_id,version_id,user_id,event_type,comment,payload_json,created_by) VALUES (?,?,?,?,?,?,CAST(? AS JSON),?)",
                request.id, request.projectId, request.versionId, request.userId, type, limit(comment, 2000), json(payload), operatorId);
    }

    private String carrierName(String code) {
        try {
            List<String> values = jdbc.query("SELECT name FROM logistics_carrier WHERE code=? AND enabled=1", (rs, rowNum) -> rs.getString(1), code);
            return values.isEmpty() ? code : values.get(0);
        } catch (Exception ignored) { return code; }
    }

    private Timestamp shipmentTime(String status, LocalDateTime now, Timestamp current) {
        if (current != null) return current;
        return Set.of("shipped", "in_transit", "delivering", "signed", "exception", "returned").contains(status)
                ? Timestamp.valueOf(now) : null;
    }

    private Timestamp timestamp(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp;
        if (value instanceof java.util.Date date) return new Timestamp(date.getTime());
        String input = String.valueOf(value).trim();
        if (input.isEmpty()) return null;
        try { return Timestamp.valueOf(input.length() == 16 ? input + ":00" : input.replace('T', ' ')); }
        catch (Exception e) { throw new IllegalArgumentException("预计到达时间格式应为 yyyy-MM-dd HH:mm:ss"); }
    }

    private void validateTrackingNo(String value) {
        if (value.length() < 4 || value.length() > 120 || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("快递单号长度或格式无效");
        }
    }

    private String statusText(String status) {
        return switch (status) {
            case "shipped" -> "样品已发货，等待承运商揽收";
            case "in_transit" -> "样品运输中";
            case "delivering" -> "样品派送中";
            case "signed" -> "样品已签收";
            case "exception" -> "样品物流异常";
            case "returned" -> "样品已退回";
            default -> "等待物流信息";
        };
    }

    /** Do not expose a shipped/exception state before review and payment gates pass. */
    private void requireSampleReady(RequestContext request, String logisticsStatus) {
        if ("pending".equals(logisticsStatus)) return;
        boolean approved = Set.of("approved", "processing", "shipped", "completed")
                .contains(request.requestStatus.toLowerCase(Locale.ROOT));
        boolean paid = Set.of("paid", "not_required")
                .contains(request.paymentStatus.toLowerCase(Locale.ROOT));
        if (!approved || !paid) throw new IllegalStateException("样品尚未完成审核或付款，不能录入发货/物流状态");
    }

    private boolean allowedStatus(String previous, String next) {
        return switch (previous) {
            case "pending" -> "shipped".equals(next) || "exception".equals(next);
            case "shipped" -> Set.of("shipped", "in_transit", "exception", "returned").contains(next);
            case "in_transit" -> Set.of("in_transit", "delivering", "signed", "exception", "returned").contains(next);
            case "delivering" -> Set.of("delivering", "signed", "exception", "returned").contains(next);
            case "exception" -> Set.of("exception", "in_transit", "delivering", "signed", "returned").contains(next);
            case "signed" -> "signed".equals(next);
            case "returned" -> "returned".equals(next);
            default -> false;
        };
    }

    private Map<String, Object> canonicalize(Map<String, Object> source) {
        Map<String, String> aliases = new HashMap<>();
        for (String key : List.of("id", "requestId", "requestNo", "userId", "username", "title", "sampleProductName",
                "sampleWorkflowStatus", "logisticsId", "carrierCode", "carrierName", "trackingNo", "status", "latestTrace",
                "alertLevel", "alertStatus", "exceptionNote", "shippedAt", "signedAt", "estimatedArrival", "lastSyncedAt",
                "createdBy", "createdAt", "updatedAt", "eventType", "location", "content", "payloadJson")) {
            aliases.put(key.toLowerCase(Locale.ROOT), key);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        source.forEach((key, value) -> out.put(aliases.getOrDefault(key.toLowerCase(Locale.ROOT), key), value));
        return out;
    }

    private Object value(Map<String, Object> row, String key) {
        if (row == null) return null;
        if (row.containsKey(key)) return row.get(key);
        for (Map.Entry<String, Object> entry : row.entrySet()) if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        return null;
    }

    private Number number(Object value) {
        if (value instanceof Number n) return n;
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Long.valueOf(String.valueOf(value)); } catch (Exception e) { return null; }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String firstNonBlank(String... values) { for (String value : values) if (!blank(value)) return value.trim(); return ""; }
    private String limit(String value, int max) { if (value == null) return ""; String trimmed = value.trim(); return trimmed.length() <= max ? trimmed : trimmed.substring(0, max); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String json(Object value) { try { return mapper.writeValueAsString(value == null ? Map.of() : value); } catch (JsonProcessingException e) { throw new IllegalArgumentException("物流记录格式无效"); } }

    private record RequestContext(Long id, Long userId, String requestNo, String requestType, String requestStatus,
                                  String paymentStatus, String workflowStatus, Number projectId, Number versionId) {}
}
