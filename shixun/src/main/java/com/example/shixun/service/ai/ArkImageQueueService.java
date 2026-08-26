package com.example.shixun.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Callable;

/**
 * Durable queue boundary for Ark/Seedream image jobs.
 *
 * The service owns queue concurrency, claiming, migration and insertion. The
 * application workflow that processes a claimed job remains injectable as a
 * callback for now; this keeps asset and credit transactions in their current
 * boundary while the controller is being decomposed incrementally.
 */
@Service
public class ArkImageQueueService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final ThreadPoolTaskExecutor executor;
    private final Object submissionLock = new Object();
    private final Set<Long> activeJobs = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean queueTableVerified = new AtomicBoolean(false);
    private final AtomicBoolean legacyJobsMigrated = new AtomicBoolean(false);
    private final AtomicBoolean queueRecovered = new AtomicBoolean(false);

    @Value("${volcengine.ark.queue.concurrency:1}")
    private int configuredConcurrency;

    public ArkImageQueueService(JdbcTemplate jdbc, ObjectMapper mapper,
                                @Qualifier("arkImageGenerationExecutor") ThreadPoolTaskExecutor executor) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.executor = executor;
    }

    /**
     * Dispatch queued jobs and let the caller process the claimed job. The
     * callback is executed off the scheduler thread and is always followed by
     * removal from the in-memory active set.
     */
    public void dispatch(String model, Consumer<Long> processor) {
        if (processor == null) throw new IllegalArgumentException("图片任务处理器不能为空");
        if (!queueTableVerified.get()) {
            if (!queueTableAvailable()) return;
            queueTableVerified.set(true);
        }
        if (legacyJobsMigrated.compareAndSet(false, true)) {
            jdbc.update("UPDATE ai_generation_job SET provider='volcengine_ark',model_name=?,status='queued'," +
                            "progress=0,started_at=NULL,error_message='已切换到火山方舟 Seedream 5.0 队列' " +
                            "WHERE provider='siliconflow' AND job_type IN ('text_to_image','image_to_image','multi_view') " +
                            "AND status IN ('queued','running') AND output_asset_id IS NULL",
                    model);
        }
        if (queueRecovered.compareAndSet(false, true)) {
            jdbc.update("UPDATE ai_generation_job SET status='queued',progress=0,started_at=NULL," +
                            "error_message='服务重启后已自动恢复排队' WHERE provider='volcengine_ark' " +
                            "AND job_type IN ('text_to_image','image_to_image','multi_view') " +
                            "AND status='running' AND output_asset_id IS NULL");
        }
        int available = normalizedConcurrency() - activeJobs.size();
        if (available <= 0) return;
        java.util.List<Long> queued = jdbc.queryForList(
                "SELECT id FROM ai_generation_job WHERE provider='volcengine_ark' " +
                        "AND job_type IN ('text_to_image','image_to_image','multi_view') " +
                        "AND status='queued' AND output_asset_id IS NULL " +
                        "ORDER BY id LIMIT " + available,
                Long.class);
        for (Long jobId : queued) {
            int claimed = jdbc.update("UPDATE ai_generation_job SET status='running',progress=10," +
                            "started_at=COALESCE(started_at,NOW()),error_message=NULL " +
                            "WHERE id=? AND status='queued'", jobId);
            if (claimed != 1 || !activeJobs.add(jobId)) continue;
            try {
                executor.execute(() -> {
                    try {
                        processor.accept(jobId);
                    } finally {
                        activeJobs.remove(jobId);
                    }
                });
            } catch (RuntimeException error) {
                activeJobs.remove(jobId);
                jdbc.update("UPDATE ai_generation_job SET status='queued',progress=0,started_at=NULL," +
                        "error_message='生成执行器繁忙，已自动重新排队' WHERE id=? AND status='running'", jobId);
            }
        }
    }

    public boolean queueTableAvailable() {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name)=LOWER(?)",
                    Integer.class, "ai_generation_job");
            if (count != null && count > 0) return true;
            // H2 and a few managed MySQL-compatible proxies expose table
            // metadata through a different information_schema shape. The
            // business-table probe is authoritative and keeps the scheduler
            // from silently leaving durable jobs in queued forever.
            jdbc.queryForObject("SELECT COUNT(*) FROM ai_generation_job", Integer.class);
            return true;
        } catch (Exception ignored) {
            try {
                jdbc.queryForObject("SELECT COUNT(*) FROM ai_generation_job", Integer.class);
                return true;
            } catch (Exception unavailable) {
                return false;
            }
        }
    }

    public int normalizedConcurrency() {
        return Math.max(1, Math.min(configuredConcurrency, 16));
    }

    /**
     * Allows a legacy submission flow that must reserve credits only after its
     * duplicate check to share the same queue lock during the migration.
     */
    public <T> T withSubmissionLock(Callable<T> operation) throws Exception {
        if (operation == null) throw new IllegalArgumentException("队列操作不能为空");
        synchronized (submissionLock) {
            return operation.call();
        }
    }

    /**
     * Insert a durable queued job. The request JSON is kept untouched so an
     * old worker or a later restart can reconstruct the exact generation
     * brief. No schema change is required by this extraction.
     */
    public long createQueuedJob(String jobNo, String model, String jobType, Long styleId,
                                Long inputAssetId, String prompt, String negative, String size,
                                String productKey, String productName, String material,
                                Long ownerUserId, Long creditTxId, Object requestPayload) throws Exception {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        String payloadJson = mapper.writeValueAsString(requestPayload == null ? Map.of() : requestPayload);
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO ai_generation_job (job_no,job_type,provider,model_name,style_id,input_asset_id," +
                            "product_key,product_name,product_material,prompt,negative_prompt,status,progress," +
                            "error_message,export_formats,created_by,credit_transaction_id,request_payload_json,attempt_count) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, jobNo);
            ps.setString(2, jobType);
            ps.setString(3, "volcengine_ark");
            ps.setString(4, model);
            if (styleId == null) ps.setNull(5, java.sql.Types.BIGINT); else ps.setLong(5, styleId);
            if (inputAssetId == null) ps.setNull(6, java.sql.Types.BIGINT); else ps.setLong(6, inputAssetId);
            ps.setString(7, blank(productKey) ? null : productKey.trim());
            ps.setString(8, blank(productName) ? null : productName.trim());
            ps.setString(9, blank(material) ? null : material.trim());
            ps.setString(10, prompt);
            ps.setString(11, negative);
            ps.setString(12, "queued");
            ps.setInt(13, 0);
            ps.setNull(14, java.sql.Types.LONGVARCHAR);
            ps.setString(15, size);
            if (ownerUserId == null) ps.setNull(16, java.sql.Types.BIGINT); else ps.setLong(16, ownerUserId);
            if (creditTxId == null) ps.setNull(17, java.sql.Types.BIGINT); else ps.setLong(17, creditTxId);
            ps.setString(18, payloadJson);
            ps.setInt(19, 0);
            return ps;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    /** Result of an idempotent queue submission. */
    public record QueueSubmission(long jobId, boolean reused) {}

    /**
     * Serialize the account-level duplicate check and insertion for image
     * editing/multiview jobs. The controller still formats the public response.
     */
    public QueueSubmission enqueue(String jobNo, String model, String jobType, Long styleId,
                                   Long inputAssetId, String prompt, String negative, String size,
                                   String productKey, String productName, String material,
                                   Long ownerUserId, Object requestPayload) throws Exception {
        return enqueue(jobNo, model, jobType, styleId, inputAssetId, prompt, negative, size,
                productKey, productName, material, ownerUserId, requestPayload, null, null);
    }

    /**
     * Queue a job with an optional credit reservation that runs only after the
     * duplicate check. This keeps repeated taps idempotent and prevents a
     * failed database insert from leaving a frozen credit balance.
     */
    public QueueSubmission enqueue(String jobNo, String model, String jobType, Long styleId,
                                   Long inputAssetId, String prompt, String negative, String size,
                                   String productKey, String productName, String material,
                                   Long ownerUserId, Object requestPayload,
                                   Supplier<Long> reserveCredit, Consumer<Long> refundCredit) throws Exception {
        synchronized (submissionLock) {
            java.util.List<Map<String, Object>> active = jdbc.queryForList(
                    "SELECT id,job_type jobType,input_asset_id inputAssetId,prompt FROM ai_generation_job " +
                            "WHERE created_by=? AND provider='volcengine_ark' " +
                            "AND job_type IN ('text_to_image','image_to_image','multi_view') " +
                            "AND status IN ('queued','running') AND output_asset_id IS NULL ORDER BY id LIMIT 1",
                    ownerUserId);
            if (!active.isEmpty()) {
                Map<String, Object> existing = active.get(0);
                boolean sameRequest = jobType.equals(String.valueOf(existing.get("jobType")))
                        && Objects.equals(inputAssetId, numberAsLong(existing.get("inputAssetId")))
                        && nullToEmpty(prompt).equals(nullToEmpty(String.valueOf(existing.get("prompt"))));
                if (!sameRequest) {
                    throw new IllegalStateException("你已有一项 Seedream 图片任务正在排队或生成，请等待完成后再提交新任务");
                }
                return new QueueSubmission(numberAsLong(existing.get("id")), true);
            }
            Long creditTxId = reserveCredit == null ? null : reserveCredit.get();
            try {
                long jobId = createQueuedJob(jobNo, model, jobType, styleId, inputAssetId,
                        prompt, negative, size, productKey, productName, material,
                        ownerUserId, creditTxId, requestPayload);
                return new QueueSubmission(jobId, false);
            } catch (Exception error) {
                if (creditTxId != null && refundCredit != null) {
                    try { refundCredit.accept(creditTxId); } catch (Exception ignored) { }
                }
                throw error;
            }
        }
    }

    private Long numberAsLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
