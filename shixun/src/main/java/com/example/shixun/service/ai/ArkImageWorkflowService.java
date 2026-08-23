package com.example.shixun.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Application workflow for durable Ark/Seedream image jobs.
 *
 * The controller supplies the domain-specific operations that still depend on
 * the existing asset and prompt helpers. This service owns the job lifecycle:
 * claiming is handled by {@link ArkImageQueueService}, while this class owns
 * provider retries, terminal state transitions and credit settlement.
 */
@Service
public class ArkImageWorkflowService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SeedreamProviderClient seedreamProviderClient;

    public ArkImageWorkflowService(JdbcTemplate jdbc, ObjectMapper mapper,
                                   SeedreamProviderClient seedreamProviderClient) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.seedreamProviderClient = seedreamProviderClient;
    }

    /**
     * Execute one already-claimed job. The operation callbacks are deliberately
     * narrow so this workflow does not depend on a web controller or on HTTP
     * request state; they can be moved to dedicated asset/credit services in a
     * later extraction without changing queue semantics.
     */
    public void process(Long jobId, String apiKey, int configuredRetryAttempts,
                        long configuredRetryDelaySeconds, Operations operations) {
        if (jobId == null || operations == null) return;
        Map<String, Object> job;
        try {
            job = jdbc.queryForMap("SELECT id,job_no jobNo,model_name modelName,style_id styleId," +
                            "job_type jobType,input_asset_id inputAssetId,prompt,negative_prompt negativePrompt,export_formats exportFormats," +
                    "product_key productKey,product_name productName,product_material productMaterial," +
                            "created_by createdBy,credit_transaction_id creditTransactionId," +
                            "request_payload_json requestPayloadJson,attempt_count attemptCount,status " +
                            "FROM ai_generation_job WHERE id=?", jobId);
            // Project/version columns are introduced by the first-stage
            // migration. Keep workers able to finish jobs created during a
            // rolling deployment where the old table is still in use.
            try {
                Map<String, Object> identity = jdbc.queryForMap(
                        "SELECT project_id projectId,version_id versionId FROM ai_generation_job WHERE id=?", jobId);
                job.putAll(identity);
            } catch (Exception ignored) {
                // The job remains processable; it simply has no project link.
            }
        } catch (Exception ignored) {
            return;
        }
        if (!"running".equals(text(job.get("status")))) return;

        Long creditTransactionId = number(job.get("creditTransactionId"));
        try {
            if (blank(apiKey)) throw new IllegalStateException("未配置火山方舟 Ark API Key");
            int attempts = job.get("attemptCount") instanceof Number
                    ? ((Number) job.get("attemptCount")).intValue() : 0;
            int maxAttempts = Math.max(1, Math.min(configuredRetryAttempts, 6));
            JsonNode generated = null;
            while (attempts < maxAttempts) {
                attempts++;
                jdbc.update("UPDATE ai_generation_job SET attempt_count=?,progress=20,error_message=NULL WHERE id=?",
                        attempts, jobId);
                try {
                    String jobType = text(job.get("jobType"));
                    if ("text_to_image".equals(jobType)) {
                        generated = seedreamProviderClient.createTextImage(
                                apiKey, text(job.get("prompt")),
                                normalizeImageSize(job.get("exportFormats")),
                                text(job.get("negativePrompt")));
                    } else if ("image_to_image".equals(jobType)) {
                        Map<String, Object> result = operations.generateImageToImage(job);
                        Long assetId = number(result == null ? null : result.get("assetId"));
                        jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,result_payload_json=?,status='succeeded'," +
                                        "progress=100,error_message=NULL,finished_at=NOW() WHERE id=?",
                                assetId, mapper.writeValueAsString(result), jobId);
                        operations.completeCredit(creditTransactionId, jobId, assetId);
                        return;
                    } else if ("multi_view".equals(jobType)) {
                        Map<String, Object> result = operations.generateMultiView(job);
                        jdbc.update("UPDATE ai_generation_job SET output_asset_id=NULL,result_payload_json=?,status='succeeded'," +
                                        "progress=100,error_message=NULL,finished_at=NOW() WHERE id=?",
                                mapper.writeValueAsString(result), jobId);
                        return;
                    } else {
                        throw new IllegalStateException("不支持的 Seedream 图片任务：" + jobType);
                    }
                    break;
                } catch (SeedreamProviderClient.ArkRateLimitException e) {
                    if (attempts >= maxAttempts) throw e;
                    long delaySeconds = Math.max(1, Math.min(configuredRetryDelaySeconds, 30)) * attempts;
                    jdbc.update("UPDATE ai_generation_job SET progress=10,error_message=? WHERE id=?",
                            "模型限流，" + delaySeconds + " 秒后自动重试（" + attempts + "/" + maxAttempts + "）", jobId);
                    try {
                        TimeUnit.SECONDS.sleep(delaySeconds);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        jdbc.update("UPDATE ai_generation_job SET status='queued',progress=0,started_at=NULL," +
                                "error_message='服务停止，任务已自动重新排队' WHERE id=? AND status='running'", jobId);
                        return;
                    }
                }
            }
            if (generated == null) throw new IllegalStateException("火山方舟未返回图片结果");

            jdbc.update("UPDATE ai_generation_job SET progress=70,error_message=NULL WHERE id=?", jobId);
            Long assetId = operations.persistTextImage(job, generated);
            jdbc.update("UPDATE ai_generation_job SET output_asset_id=?,status='succeeded',progress=100," +
                    "error_message=NULL,finished_at=NOW() WHERE id=?", assetId, jobId);
            try {
                operations.completeCredit(creditTransactionId, jobId, assetId);
            } catch (Exception creditError) {
                jdbc.update("UPDATE ai_generation_job SET error_message=? WHERE id=?",
                        "作品已生成，积分结算待核对：" + safeMessage(creditError), jobId);
            }
        } catch (Exception error) {
            String message = safeMessage(error);
            try {
                operations.refundCredit(creditTransactionId, message);
                // Do not publish a terminal failure before its reservation has
                // been settled; clients may poll this row while it is running.
                jdbc.update("UPDATE ai_generation_job SET status='failed',progress=0,error_message=?,finished_at=NOW() " +
                        "WHERE id=? AND status<>'succeeded'", message, jobId);
            } catch (Exception refundError) {
                jdbc.update("UPDATE ai_generation_job SET status='failed',progress=0,error_message=?,finished_at=NOW() " +
                                "WHERE id=? AND status<>'succeeded'",
                        message + "；积分退回待核对：" + safeMessage(refundError), jobId);
            }
        }
    }

    public interface Operations {
        Map<String, Object> generateImageToImage(Map<String, Object> job) throws Exception;

        Map<String, Object> generateMultiView(Map<String, Object> job) throws Exception;

        Long persistTextImage(Map<String, Object> job, JsonNode generated) throws Exception;

        void completeCredit(Long creditTransactionId, Long jobId, Long assetId);

        void refundCredit(Long creditTransactionId, String reason);
    }

    private String normalizeImageSize(Object value) {
        String size = text(value);
        return "1K".equals(size) || "2K".equals(size) ? size : "2K";
    }

    private Long number(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return blank(message) ? (error == null ? "未知错误" : error.getClass().getSimpleName()) : message;
    }
}
