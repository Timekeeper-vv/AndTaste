package com.example.shixun.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The common data boundary for the first-stage creative workflow.
 *
 * Controllers and asynchronous workers can attach their existing records to
 * one project/version without having to know the details of the timeline
 * tables.  All consumer reads are scoped by the canonical user id.
 */
@Service
public class CreativeProjectService {
    private static final DateTimeFormatter PROJECT_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> LINK_TYPES = Set.of(
            "conversation_session", "conversation_event", "generation_job", "asset",
            "design_review", "multiview_bundle", "production_request");

    /**
     * The project timeline deliberately has a small canonical state machine.
     * Older clients used descriptive labels (for example "generating") for
     * the same states, so those labels are normalized at this boundary.
     */
    private static final Set<String> PHASES = Set.of(
            "brief", "generation", "multiview", "preflight", "ai_review",
            "human_review", "approved", "needs_revision", "sampling",
            "sample_review", "sample_accepted", "bulk_unlocked", "in_production",
            "shipped", "completed", "rejected", "cancelled", "failed");
    private static final Map<String, String> PHASE_ALIASES = Map.ofEntries(
            Map.entry("brief_ready", "brief"),
            Map.entry("generating", "generation"),
            Map.entry("candidate_selected", "multiview"),
            Map.entry("engineering_check", "preflight"),
            Map.entry("design_review", "ai_review"),
            Map.entry("review", "human_review"),
            Map.entry("sample", "sampling"),
            Map.entry("production", "in_production"),
            Map.entry("sample_approved", "sample_accepted"));
    private static final Map<String, Set<String>> PHASE_TRANSITIONS = Map.ofEntries(
            // A generated image bundle or 3D model can be submitted for review
            // while its version is still recorded as "brief" (the generation
            // phase write may have advanced a different version, or the model
            // asset was bound after the fact). Every other generative phase
            // already allows human_review, so let a brief enter it directly
            // instead of failing the user's submit-for-review action. An
            // already-approved 3D asset may also enter sampling directly from
            // brief when the production request is submitted.
            Map.entry("brief", Set.of("brief", "generation", "human_review", "sampling")),
            Map.entry("generation", Set.of("generation", "multiview", "preflight", "ai_review", "human_review", "sampling", "brief", "failed", "cancelled")),
            Map.entry("multiview", Set.of("multiview", "preflight", "ai_review", "human_review", "generation", "failed", "cancelled")),
            // A production preflight can be run before the admin review is
            // recorded. Once the underlying asset/request is approved, the
            // production boundary may move directly into sampling while
            // preserving the preflight event in the timeline.
            Map.entry("preflight", Set.of("preflight", "ai_review", "human_review", "sampling", "multiview", "generation", "failed", "cancelled")),
            Map.entry("ai_review", Set.of("ai_review", "human_review", "approved", "needs_revision", "preflight", "generation", "failed")),
            Map.entry("human_review", Set.of("human_review", "approved", "needs_revision", "sampling", "preflight", "generation", "failed")),
            Map.entry("approved", Set.of("approved", "sampling", "bulk_unlocked", "needs_revision", "cancelled")),
            // A revision can return to production without creating a new
            // design version. The physical sample is still tied to the same
            // frozen snapshot, so allow the factory/user feedback loop to
            // re-enter sampling and finish at sample acceptance.
            Map.entry("needs_revision", Set.of("needs_revision", "generation", "brief", "preflight", "human_review", "sampling", "sample_review", "sample_accepted", "cancelled")),
            Map.entry("sampling", Set.of("sampling", "sample_review", "sample_accepted", "bulk_unlocked", "completed", "human_review", "needs_revision", "preflight", "failed")),
            Map.entry("sample_review", Set.of("sample_review", "sample_accepted", "sampling", "needs_revision", "failed")),
            Map.entry("sample_accepted", Set.of("sample_accepted", "bulk_unlocked", "sampling", "needs_revision")),
            Map.entry("bulk_unlocked", Set.of("bulk_unlocked", "in_production", "completed", "cancelled")),
            Map.entry("in_production", Set.of("in_production", "shipped", "completed", "failed")),
            Map.entry("shipped", Set.of("shipped", "completed")),
            Map.entry("completed", Set.of("completed")),
            Map.entry("rejected", Set.of("rejected", "needs_revision")),
            Map.entry("cancelled", Set.of("cancelled")),
            Map.entry("failed", Set.of("failed", "generation", "preflight")));
    private static final Set<String> FREEZE_PHASES = Set.of(
            "human_review", "approved", "sampling", "sample_review", "sample_accepted",
            "bulk_unlocked", "in_production", "shipped", "completed");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CreativeProjectService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** Lightweight reference used by existing conversation/generation flows. */
    public record ProjectRef(long projectId, long versionId, String projectNo) {}

    /**
     * Resolves and validates a project reference supplied by a consumer.
     * Legacy clients may send only projectId; in that case the current
     * version is selected so every new record still has an immutable version
     * anchor. A null projectId intentionally means "legacy unscoped request".
     */
    public ProjectRef resolveOwnedRef(Long projectId, Long versionId, Long userId) {
        if (projectId == null) return null;
        Map<String, Object> project = ownedProject(projectId, userId);
        Number currentVersion = number(project.get("currentVersionId"));
        Long resolvedVersion = versionId == null
                ? (currentVersion == null ? null : currentVersion.longValue())
                : versionId;
        if (resolvedVersion == null) throw new NoSuchElementException("项目尚未创建可用版本");
        requireVersion(projectId, resolvedVersion);
        return new ProjectRef(projectId, resolvedVersion, text(project.get("projectNo")));
    }

    /** Compatibility boundary for the conversation controller. */
    @Transactional
    public ProjectRef createProject(long userId, String mode, String title) {
        String name = blank(title) ? "对话创作" : title;
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (!blank(mode)) metadata.put("mode", mode);
        Map<String, Object> project = createProject(userId, name, null, metadata);
        Number projectId = (Number) project.get("id");
        Number versionId = (Number) project.get("currentVersionId");
        if (projectId == null || versionId == null) throw new IllegalStateException("创作项目引用创建失败");
        return new ProjectRef(projectId.longValue(), versionId.longValue(), text(project.get("projectNo")));
    }

    /** Lazily repairs links for a historical conversation session. */
    @Transactional
    public ProjectRef ensureForSession(long sessionId, long userId, String mode) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT project_id projectId,version_id versionId FROM creative_conversation_session WHERE id=? AND user_id=?",
                sessionId, userId);
        if (rows.isEmpty()) throw new NoSuchElementException("创作会话不存在或无权访问");
        Map<String, Object> row = rows.get(0);
        Number existingProject = number(row.get("projectId"));
        Number existingVersion = number(row.get("versionId"));
        if (existingProject != null && existingVersion != null) {
            Map<String, Object> project = ownedProject(existingProject.longValue(), userId);
            return new ProjectRef(existingProject.longValue(), existingVersion.longValue(), text(project.get("projectNo")));
        }
        ProjectRef created = createProject(userId, mode, "对话创作");
        jdbc.update("UPDATE creative_conversation_session SET project_id=?,version_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=?",
                created.projectId(), created.versionId(), sessionId, userId);
        jdbc.update("UPDATE creative_conversation_event SET project_id=?,version_id=? WHERE session_id=? AND user_id=? AND project_id IS NULL",
                created.projectId(), created.versionId(), sessionId, userId);
        return created;
    }

    /** Binds a generated asset when the caller has a project context. */
    @Transactional
    public void bindAsset(Long assetId, Long projectId, Long versionId, Long sourceAssetId) {
        ProjectLink link = effectiveLink(projectId, versionId, sourceAssetId);
        if (assetId == null || link.projectId() == null) return;
        if (link.versionId() != null) requireVersion(link.projectId(), link.versionId());
        try {
            jdbc.update("UPDATE digital_asset SET project_id=?,version_id=? WHERE id=?", link.projectId(), link.versionId(), assetId);
        } catch (Exception ignored) {
            // Optional project columns may not exist during a rolling upgrade.
        }
    }

    /** Binds a queued generation job when the caller has a project context. */
    @Transactional
    public void bindJob(Long jobId, Long projectId, Long versionId, Long inputAssetId) {
        ProjectLink link = effectiveLink(projectId, versionId, inputAssetId);
        if (jobId == null || link.projectId() == null) return;
        if (link.versionId() != null) requireVersion(link.projectId(), link.versionId());
        try {
            jdbc.update("UPDATE ai_generation_job SET project_id=?,version_id=? WHERE id=?", link.projectId(), link.versionId(), jobId);
        } catch (Exception ignored) {
            // Optional project columns may not exist during a rolling upgrade.
        }
    }

    /** Binds a complete multi-view bundle, inheriting the input asset project when available. */
    @Transactional
    public void bindBundle(Long bundleId, Long projectId, Long versionId, Long inputAssetId) {
        ProjectLink link = effectiveLink(projectId, versionId, inputAssetId);
        if (bundleId == null || link.projectId() == null) return;
        if (link.versionId() != null) requireVersion(link.projectId(), link.versionId());
        try {
            jdbc.update("UPDATE creative_multiview_bundle SET project_id=?,version_id=? WHERE id=?", link.projectId(), link.versionId(), bundleId);
        } catch (Exception ignored) {
            // Rolling deployments may create the bundle before the optional
            // project columns are applied; the bundle itself remains valid.
        }
    }

    /** Binds an AI or human review to the exact asset version under review. */
    @Transactional
    public void bindReview(Long reviewId, Long projectId, Long versionId, Long assetId) {
        ProjectLink link = effectiveLink(projectId, versionId, assetId);
        if (reviewId == null || link.projectId() == null) return;
        if (link.versionId() != null) requireVersion(link.projectId(), link.versionId());
        try {
            jdbc.update("UPDATE design_review SET project_id=?,version_id=? WHERE id=?", link.projectId(), link.versionId(), reviewId);
        } catch (Exception ignored) {
            // Preserve review behavior on an older read-only schema.
        }
    }

    /** Binds a production request so payment and sampling cannot lose version identity. */
    @Transactional
    public void bindProductionRequest(Long requestId, Long projectId, Long versionId, Long assetId) {
        ProjectLink link = effectiveLink(projectId, versionId, assetId);
        if (requestId == null || link.projectId() == null) return;
        if (link.versionId() != null) requireVersion(link.projectId(), link.versionId());
        try {
            jdbc.update("UPDATE consumer_production_request SET project_id=?,version_id=? WHERE id=?", link.projectId(), link.versionId(), requestId);
        } catch (Exception ignored) {
            // The request remains usable while an older node is still serving.
        }
    }

    private ProjectLink effectiveLink(Long projectId, Long versionId, Long assetId) {
        if (projectId != null) return new ProjectLink(projectId, versionId);
        if (assetId == null) return new ProjectLink(null, null);
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT project_id projectId,version_id versionId FROM digital_asset WHERE id=?", assetId);
            if (!rows.isEmpty()) {
                Number inheritedProject = number(rows.get(0).get("projectId"));
                Number inheritedVersion = number(rows.get(0).get("versionId"));
                if (inheritedProject != null) return new ProjectLink(inheritedProject.longValue(), inheritedVersion == null ? null : inheritedVersion.longValue());
            }
        } catch (Exception ignored) {
            // Optional project columns may not exist during a rolling upgrade.
        }
        return new ProjectLink(null, null);
    }

    private record ProjectLink(Long projectId, Long versionId) {}

    /** Creates a project, its initial v1 snapshot and a project_created event. */
    @Transactional
    public Map<String, Object> createProject(Long userId, String name, String brief, Map<String, Object> metadata) {
        requireUser(userId);
        String projectName = limit(blank(name) ? "未命名创作项目" : name.trim(), 180);
        String projectNo = uniqueNo("CP");
        Map<String, Object> safeMetadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        if (!blank(brief)) safeMetadata.putIfAbsent("brief", brief.trim());

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO creative_project (project_no,user_id,name,theme,status,current_phase,next_action,metadata_json) " +
                            "VALUES (?,?,?,?,'planning','brief','确认创作需求',?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, projectNo);
            ps.setLong(2, userId);
            ps.setString(3, projectName);
            ps.setString(4, blank(brief) ? null : limit(brief.trim(), 300));
            ps.setString(5, jsonOrNull(safeMetadata));
            return ps;
        }, keyHolder);
        Number key = generatedId(keyHolder);
        if (key == null) throw new IllegalStateException("创作项目创建失败");
        long projectId = key.longValue();
        Map<String, Object> version = createVersionInternal(projectId, userId, "初始版本", "brief", brief, safeMetadata, false);
        appendEventInternal(projectId, ((Number) version.get("id")).longValue(), userId,
                "project_created", null, "brief", "确认创作需求", "user", userId, null,
                Map.of("projectNo", projectNo, "name", projectName));
        return getProject(projectId, userId);
    }

    /** Convenience overload for request-body based callers. */
    public Map<String, Object> createProject(Long userId, Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        String brief = text(body.get("brief"));
        if (blank(brief)) brief = text(body.get("description"));
        Map<String, Object> metadata = objectMap(body.get("metadata"));
        if (metadata.isEmpty()) {
            for (String key : List.of("productType", "productName", "material", "sourceAssetId", "inspiration")) {
                if (body.containsKey(key) && body.get(key) != null) metadata.put(key, body.get(key));
            }
        }
        return createProject(userId, text(body.get("name")), brief, metadata);
    }

    public List<Map<String, Object>> listProjects(Long userId, int limit) {
        requireUser(userId);
        int pageSize = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        return jdbc.queryForList(
                "SELECT id,project_no projectNo,user_id userId,name,theme,status,current_phase currentPhase," +
                        "current_version_id currentVersionId,next_action nextAction,metadata_json metadataJson,created_at createdAt,updated_at updatedAt " +
                        "FROM creative_project WHERE user_id=? ORDER BY updated_at DESC,id DESC LIMIT " + pageSize, userId);
    }

    public Map<String, Object> getProject(Long projectId, Long userId) {
        Map<String, Object> project = ownedProject(projectId, userId);
        List<Map<String, Object>> versions = jdbc.queryForList(
                "SELECT id,project_id projectId,version_no versionNo,version_number versionNumber,version_label versionLabel," +
                        "phase,status,brief_json briefJson,metadata_json metadataJson,created_by createdBy," +
                        "frozen_at frozenAt,frozen_by frozenBy,freeze_reason freezeReason,freeze_hash freezeHash," +
                        "created_at createdAt,updated_at updatedAt " +
                        "FROM creative_project_version WHERE project_id=? ORDER BY version_number DESC,id DESC", projectId);
        project.put("versions", versions);
        return project;
    }

    public List<Map<String, Object>> timeline(Long projectId, Long userId) {
        ownedProject(projectId, userId);
        return jdbc.queryForList(
                "SELECT id,project_id projectId,version_id versionId,user_id userId,event_type eventType," +
                        "from_phase fromPhase,to_phase toPhase,next_action nextAction,actor_type actorType,actor_id actorId," +
                        "payload_json payloadJson,created_at createdAt FROM creative_project_event " +
                        "WHERE project_id=? ORDER BY created_at ASC,id ASC", projectId);
    }

    public Map<String, Object> getVersion(Long projectId, Long versionId, Long userId) {
        ownedProject(projectId, userId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,project_id projectId,version_no versionNo,version_number versionNumber,version_label versionLabel," +
                        "phase,status,brief_json briefJson,metadata_json metadataJson,created_by createdBy," +
                        "frozen_at frozenAt,frozen_by frozenBy,freeze_reason freezeReason,freeze_hash freezeHash," +
                        "created_at createdAt,updated_at updatedAt " +
                        "FROM creative_project_version WHERE id=? AND project_id=?", versionId, projectId);
        if (rows.isEmpty()) throw new NoSuchElementException("项目版本不存在");
        return rows.get(0);
    }

    /** Locks a version while a workflow transition is evaluated and recorded. */
    private Map<String, Object> getVersionForUpdate(Long projectId, Long versionId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,project_id projectId,version_no versionNo,version_number versionNumber,version_label versionLabel," +
                        "phase,status,brief_json briefJson,metadata_json metadataJson,created_by createdBy," +
                        "frozen_at frozenAt,frozen_by frozenBy,freeze_reason freezeReason,freeze_hash freezeHash," +
                        "created_at createdAt,updated_at updatedAt " +
                        "FROM creative_project_version WHERE id=? AND project_id=? FOR UPDATE", versionId, projectId);
        if (rows.isEmpty()) throw new NoSuchElementException("项目版本不存在");
        return rows.get(0);
    }

    @Transactional
    public Map<String, Object> createVersion(Long projectId, Long userId, String label, String phase,
                                             Object brief, Map<String, Object> metadata) {
        ownedProject(projectId, userId);
        String versionPhase = canonicalPhase(blank(phase) ? "brief" : phase);
        Map<String, Object> version = createVersionInternal(projectId, userId,
                blank(label) ? null : label.trim(), versionPhase, brief, metadata, true);
        appendEventInternal(projectId, ((Number) version.get("id")).longValue(), userId,
                "version_created", null, versionPhase, nextAction(versionPhase), "user", userId, null,
                Map.of("versionId", version.get("id"), "versionNumber", version.get("versionNumber")));
        return version;
    }

    public Map<String, Object> createVersion(Long projectId, Long userId, Map<String, Object> request) {
        Map<String, Object> body = request == null ? Map.of() : request;
        return createVersion(projectId, userId, text(body.get("versionLabel")), text(body.get("phase")),
                body.get("brief"), objectMap(body.get("metadata")));
    }

    /**
     * Moves a project/version through the canonical workflow.  The transition
     * and optional freeze happen in one transaction, so review and sampling
     * cannot observe a half-updated version.
     */
    @Transactional
    public Map<String, Object> transition(Long projectId, Long versionId, Long userId,
                                          String toPhase, String eventType, String actorType,
                                          Map<String, Object> payload, String idempotencyKey,
                                          boolean freeze, String freezeReason) {
        ownedProject(projectId, userId);
        if (versionId == null) throw new IllegalArgumentException("阶段流转必须指定版本");
        String target = canonicalPhase(toPhase);
        Map<String, Object> version = getVersionForUpdate(projectId, versionId);
        String current = canonicalPhase(text(version.get("phase")));
        assertFrozenPhaseTransitionAllowed(version, target);
        validateTransition(current, target);
        Map<String, Object> event = appendEventInternal(projectId, versionId, userId,
                blank(eventType) ? "phase_changed" : eventType, current, target,
                nextAction(target), blank(actorType) ? "user" : actorType, userId,
                blank(idempotencyKey) ? null : idempotencyKey, objectMap(payload));
        if (freeze || FREEZE_PHASES.contains(target)) {
            freezeVersionInternal(projectId, versionId, userId,
                    blank(freezeReason) ? "进入审核/打样阶段" : freezeReason);
        }
        return event;
    }

    /** Public name used by downstream workflow controllers in stage two. */
    @Transactional
    public Map<String, Object> transitionProject(Long projectId, Long versionId, Long userId,
                                                 String toPhase, String eventType, String actorType,
                                                 Long actorId, Map<String, Object> payload) {
        ownedProject(projectId, userId);
        if (versionId == null) throw new IllegalArgumentException("阶段流转必须指定版本");
        String target = canonicalPhase(toPhase);
        Map<String, Object> version = getVersionForUpdate(projectId, versionId);
        String current = canonicalPhase(text(version.get("phase")));
        assertFrozenPhaseTransitionAllowed(version, target);
        validateTransition(current, target);
        Map<String, Object> event = appendEventInternal(projectId, versionId, userId,
                blank(eventType) ? "phase_changed" : eventType, current, target,
                nextAction(target), blank(actorType) ? "user" : actorType,
                actorId == null ? userId : actorId, null, objectMap(payload));
        if (FREEZE_PHASES.contains(target)) {
            freezeVersionInternal(projectId, versionId, userId, "进入" + target + "阶段");
        }
        return event;
    }

    /** Same transition contract with an idempotency key for retried clients. */
    @Transactional
    public Map<String, Object> transitionProject(Long projectId, Long versionId, Long userId,
                                                 String toPhase, String eventType, String actorType,
                                                 Long actorId, Map<String, Object> payload,
                                                 String idempotencyKey) {
        ownedProject(projectId, userId);
        if (versionId == null) throw new IllegalArgumentException("阶段流转必须指定版本");
        Map<String, Object> version = getVersionForUpdate(projectId, versionId);
        String key = blank(idempotencyKey) ? null : limit(idempotencyKey.trim(), 120);
        if (key != null) {
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT id,project_id projectId,version_id versionId,user_id userId,event_type eventType,from_phase fromPhase,to_phase toPhase,next_action nextAction,actor_type actorType,actor_id actorId,idempotency_key idempotencyKey,payload_json payloadJson,created_at createdAt FROM creative_project_event WHERE project_id=? AND idempotency_key=? LIMIT 1",
                    projectId, key);
            if (!existing.isEmpty()) return existing.get(0);
        }
        String target = canonicalPhase(toPhase);
        String current = canonicalPhase(text(version.get("phase")));
        assertFrozenPhaseTransitionAllowed(version, target);
        validateTransition(current, target);
        Map<String, Object> event = appendEventInternal(projectId, versionId, userId,
                blank(eventType) ? "phase_changed" : eventType, current, target,
                nextAction(target), blank(actorType) ? "user" : actorType,
                actorId == null ? userId : actorId, key, objectMap(payload));
        if (FREEZE_PHASES.contains(target)) {
            freezeVersionInternal(projectId, versionId, userId, "进入" + target + "阶段");
        }
        return event;
    }

    /** Locks a version as the immutable design snapshot used by review/sample. */
    @Transactional
    public Map<String, Object> freezeVersion(Long projectId, Long versionId, Long userId, String reason) {
        ownedProject(projectId, userId);
        requireVersion(projectId, versionId);
        Map<String, Object> frozen = freezeVersionInternal(projectId, versionId, userId,
                blank(reason) ? "设计版本已提交审核" : reason);
        appendEventInternal(projectId, versionId, userId, "version_frozen", null, null,
                nextAction(text(frozen.get("phase"))), "user", userId, null,
                Map.of("versionId", versionId, "freezeHash", frozen.getOrDefault("freezeHash", "")));
        return frozen;
    }

    /** Actor-aware overload for staff/system workflow integrations. */
    @Transactional
    public Map<String, Object> freezeVersion(Long projectId, Long versionId, Long userId,
                                             String reason, String actorType, Long actorId) {
        ownedProject(projectId, userId);
        requireVersion(projectId, versionId);
        Map<String, Object> frozen = freezeVersionInternal(projectId, versionId, userId,
                blank(reason) ? "设计版本已提交审核" : reason);
        appendEventInternal(projectId, versionId, userId, "version_frozen", null, null,
                nextAction(text(frozen.get("phase"))), blank(actorType) ? "user" : actorType,
                actorId == null ? userId : actorId, null,
                Map.of("versionId", versionId, "freezeHash", frozen.getOrDefault("freezeHash", "")));
        return frozen;
    }

    /** Fails fast when a caller tries to mutate an immutable design version. */
    public Map<String, Object> assertVersionMutable(Long projectId, Long versionId, Long userId) {
        Map<String, Object> version = getVersion(projectId, versionId, userId);
        String status = text(version.get("status"));
        if (version.get("frozenAt") != null || Set.of("frozen", "approved", "archived").contains(status)) {
            throw new IllegalStateException("该设计版本已冻结，修改请新建版本");
        }
        return version;
    }

    /**
     * Returns the immutable snapshot consumed by production.  Existing
     * production requests can call this without changing their schema; the
     * version row and its hash are the durable snapshot identity.
     */
    @Transactional
    public Map<String, Object> createProductionSnapshot(Long projectId, Long versionId, Long userId) {
        ownedProject(projectId, userId);
        requireVersion(projectId, versionId);
        Map<String, Object> version = getVersion(projectId, versionId, userId);
        if (version.get("frozenAt") == null || !Set.of("frozen", "approved").contains(text(version.get("status")))) {
            version = freezeVersion(projectId, versionId, userId, "提交打样/生产申请");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("projectId", projectId);
        snapshot.put("versionId", versionId);
        snapshot.put("versionNo", version.get("versionNo"));
        snapshot.put("versionNumber", version.get("versionNumber"));
        snapshot.put("freezeHash", version.get("freezeHash"));
        snapshot.put("frozenAt", version.get("frozenAt"));
        snapshot.put("phase", version.get("phase"));
        snapshot.put("status", version.get("status"));
        snapshot.put("briefJson", version.get("briefJson"));
        snapshot.put("metadataJson", version.get("metadataJson"));
        return snapshot;
    }

    /** Returns an owned version only when it has been frozen for downstream use. */
    public Map<String, Object> requireFrozenVersion(Long projectId, Long versionId, Long userId) {
        Map<String, Object> version = getVersion(projectId, versionId, userId);
        if (version.get("frozenAt") == null || !Set.of("frozen", "approved").contains(text(version.get("status")))) {
            throw new IllegalStateException("审核或打样必须使用已冻结的设计版本");
        }
        return version;
    }

    public boolean isVersionFrozen(Long projectId, Long versionId) {
        if (projectId == null || versionId == null) return false;
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM creative_project_version WHERE id=? AND project_id=? AND status IN ('frozen','approved') AND frozen_at IS NOT NULL",
                    Integer.class, versionId, projectId);
            return count != null && count > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Transactional
    public Map<String, Object> appendEvent(Long projectId, Long versionId, Long userId, String eventType,
                                           String toPhase, String nextAction, String actorType,
                                           Map<String, Object> payload) {
        ownedProject(projectId, userId);
        if (versionId != null) getVersionForUpdate(projectId, versionId);
        String phase = blank(toPhase) ? null : canonicalPhase(toPhase);
        String action = blank(nextAction) ? null : limit(nextAction.trim(), 160);
        String type = blank(eventType) ? "note" : limit(eventType.trim(), 60);
        String actor = blank(actorType) ? "user" : limit(actorType.trim(), 30);
        if (phase != null && versionId != null) {
            Map<String, Object> version = getVersionForUpdate(projectId, versionId);
            assertFrozenPhaseTransitionAllowed(version, phase);
            validateTransition(canonicalPhase(text(version.get("phase"))), phase);
        }
        Map<String, Object> event = appendEventInternal(projectId, versionId, userId, type, null, phase, action, actor, userId, null, objectMap(payload));
        if (phase != null && FREEZE_PHASES.contains(phase)) {
            freezeVersionInternal(projectId, versionId, userId, "进入" + phase + "阶段");
        }
        return event;
    }

    /** Event signature used by the conversation workflow, including a client idempotency key. */
    @Transactional
    public Map<String, Object> appendEvent(long projectId, long userId, long versionId, String eventType,
                                           String fromPhase, String toPhase, String actorType, long actorId,
                                           String idempotencyKey, Object payload) {
        ownedProject(projectId, userId);
        Map<String, Object> lockedVersion = getVersionForUpdate(projectId, versionId);
        String type = blank(eventType) ? "note" : limit(eventType.trim(), 60);
        String actor = blank(actorType) ? "user" : limit(actorType.trim(), 30);
        String key = blank(idempotencyKey) ? null : limit(idempotencyKey.trim(), 120);
        if (key != null) {
            List<Map<String, Object>> existing = jdbc.queryForList(
                    "SELECT id,project_id projectId,version_id versionId,user_id userId,event_type eventType,from_phase fromPhase,to_phase toPhase,next_action nextAction,actor_type actorType,actor_id actorId,idempotency_key idempotencyKey,payload_json payloadJson,created_at createdAt FROM creative_project_event WHERE project_id=? AND idempotency_key=? LIMIT 1",
                    projectId, key);
            if (!existing.isEmpty()) return existing.get(0);
        }
        String canonicalFrom = blank(fromPhase) ? null : canonicalPhase(fromPhase);
        String canonicalTo = blank(toPhase) ? null : canonicalPhase(toPhase);
        if (canonicalTo != null) {
            Map<String, Object> version = lockedVersion;
            assertFrozenPhaseTransitionAllowed(version, canonicalTo);
            validateTransition(canonicalFrom == null ? canonicalPhase(text(version.get("phase"))) : canonicalFrom, canonicalTo);
        }
        Map<String, Object> event = appendEventInternal(projectId, versionId, userId, type, canonicalFrom, canonicalTo,
                nextAction(canonicalTo), actor, actorId, key, objectMap(payload));
        if (canonicalTo != null && FREEZE_PHASES.contains(canonicalTo)) {
            freezeVersionInternal(projectId, versionId, userId, "进入" + canonicalTo + "阶段");
        }
        return event;
    }

    /** Records an audit event without changing the workflow phase. */
    @Transactional
    public Map<String, Object> recordWorkflowEvent(Long projectId, Long versionId, Long userId,
                                                   String eventType, String actorType, Long actorId,
                                                   Map<String, Object> payload) {
        ownedProject(projectId, userId);
        Map<String, Object> version = getVersionForUpdate(projectId, versionId);
        String phase = canonicalPhase(text(version.get("phase")));
        return appendEventInternal(projectId, versionId, userId,
                blank(eventType) ? "workflow_event" : eventType,
                phase, phase, nextAction(phase), blank(actorType) ? "system" : actorType,
                actorId == null ? userId : actorId, null, objectMap(payload));
    }

    /**
     * Links an existing record to a project/version.  This is intentionally
     * explicit instead of using a generic table name in SQL, keeping the
     * allowed association surface auditable.
     */
    @Transactional
    public Map<String, Object> link(Long projectId, Long versionId, Long entityId, String entityType, Long userId) {
        ownedProject(projectId, userId);
        if (versionId != null) requireVersion(projectId, versionId);
        String type = entityType == null ? "" : entityType.trim().toLowerCase(Locale.ROOT);
        if (!LINK_TYPES.contains(type)) throw new IllegalArgumentException("不支持的项目关联类型");
        if (entityId == null || entityId <= 0) throw new IllegalArgumentException("关联记录编号无效");

        String sql;
        Object[] args;
        switch (type) {
            case "conversation_session" -> {
                sql = "UPDATE creative_conversation_session SET project_id=?,version_id=? WHERE id=? AND user_id=?";
                args = new Object[]{projectId, versionId, entityId, userId};
            }
            case "conversation_event" -> {
                sql = "UPDATE creative_conversation_event SET project_id=?,version_id=? WHERE id=? AND user_id=?";
                args = new Object[]{projectId, versionId, entityId, userId};
            }
            case "generation_job" -> {
                sql = "UPDATE ai_generation_job SET project_id=?,version_id=? WHERE id=? AND created_by=?";
                args = new Object[]{projectId, versionId, entityId, userId};
            }
            case "asset" -> {
                sql = "UPDATE digital_asset SET project_id=?,version_id=? WHERE id=? AND created_by=?";
                args = new Object[]{projectId, versionId, entityId, userId};
            }
            case "design_review" -> {
                sql = "UPDATE design_review r JOIN digital_asset a ON a.id=r.asset_id SET r.project_id=?,r.version_id=? " +
                        "WHERE r.id=? AND a.created_by=?";
                args = new Object[]{projectId, versionId, entityId, userId};
            }
            case "multiview_bundle" -> {
                sql = "UPDATE creative_multiview_bundle SET project_id=?,version_id=? WHERE id=? AND user_id=?";
                args = new Object[]{projectId, versionId, entityId, userId};
            }
            case "production_request" -> {
                sql = "UPDATE consumer_production_request SET project_id=?,version_id=? WHERE id=? AND user_id=?";
                args = new Object[]{projectId, versionId, entityId, userId};
            }
            default -> throw new IllegalArgumentException("不支持的项目关联类型");
        }
        if (jdbc.update(sql, args) == 0) throw new NoSuchElementException("关联记录不存在或不属于当前用户");
        appendEventInternal(projectId, versionId, userId, "entity_linked", null, null, null,
                "system", userId, null, Map.of("entityType", type, "entityId", entityId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectId", projectId);
        result.put("versionId", versionId);
        result.put("entityType", type);
        result.put("entityId", entityId);
        return result;
    }

    private Map<String, Object> createVersionInternal(Long projectId, Long userId, String label, String phase,
                                                        Object brief, Map<String, Object> metadata, boolean updateProject) {
        // Serialize version number allocation per project. MAX()+1 is only
        // deterministic while the project row is locked for this transaction.
        lockProjectRow(projectId);
        Integer max = jdbc.queryForObject("SELECT COALESCE(MAX(version_number),0) FROM creative_project_version WHERE project_id=?", Integer.class, projectId);
        int number = (max == null ? 0 : max) + 1;
        String projectNo = jdbc.queryForObject("SELECT project_no FROM creative_project WHERE id=?", String.class, projectId);
        String versionNo = limit(projectNo + "-V" + number, 100);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        Map<String, Object> safeMetadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO creative_project_version (project_id,version_no,version_number,version_label,phase,status,brief_json,metadata_json,created_by) VALUES (?,?,?,?,?,'draft',?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            ps.setString(2, versionNo);
            ps.setInt(3, number);
            ps.setString(4, label);
            ps.setString(5, phase);
            ps.setString(6, jsonOrNull(brief));
            ps.setString(7, jsonOrNull(safeMetadata));
            ps.setLong(8, userId);
            return ps;
        }, keyHolder);
        Number key = generatedId(keyHolder);
        if (key == null) throw new IllegalStateException("项目版本创建失败");
        long versionId = key.longValue();
        if (updateProject) {
            jdbc.update("UPDATE creative_project SET current_version_id=?,current_phase=?,next_action=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    versionId, phase, nextAction(phase), projectId);
        } else {
            jdbc.update("UPDATE creative_project SET current_version_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", versionId, projectId);
        }
        return getVersion(projectId, versionId, userId);
    }

    private void lockProjectRow(Long projectId) {
        List<Long> rows = jdbc.queryForList("SELECT id FROM creative_project WHERE id=? FOR UPDATE", Long.class, projectId);
        if (rows.isEmpty()) throw new NoSuchElementException("项目不存在");
    }

    private Map<String, Object> appendEventInternal(Long projectId, Long versionId, Long userId, String eventType,
                                                     String fromPhase, String toPhase, String nextAction,
                                                     String actorType, Long actorId, String idempotencyKey,
                                                     Map<String, Object> payload) {
        String current = fromPhase;
        if (current == null) {
            List<String> values = jdbc.queryForList("SELECT current_phase FROM creative_project WHERE id=?", String.class, projectId);
            current = values.isEmpty() ? null : values.get(0);
        }
        KeyHolder keyHolder = new GeneratedKeyHolder();
        final String oldPhase = current;
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO creative_project_event (project_id,version_id,user_id,event_type,from_phase,to_phase,next_action,actor_type,actor_id,idempotency_key,payload_json) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, projectId);
            if (versionId == null) ps.setObject(2, null); else ps.setLong(2, versionId);
            if (userId == null) ps.setObject(3, null); else ps.setLong(3, userId);
            ps.setString(4, eventType);
            ps.setString(5, oldPhase);
            ps.setString(6, toPhase);
            ps.setString(7, nextAction);
            ps.setString(8, actorType);
            if (actorId == null) ps.setObject(9, null); else ps.setLong(9, actorId);
            ps.setString(10, idempotencyKey);
            ps.setString(11, jsonOrNull(payload));
            return ps;
        }, keyHolder);
        if (toPhase != null || nextAction != null) {
            boolean writeVersionPhase = true;
            if (versionId != null && toPhase != null) {
                writeVersionPhase = allowVersionPhaseWrite(projectId, versionId, current, toPhase);
            }
            jdbc.update("UPDATE creative_project SET current_phase=COALESCE(?,current_phase),next_action=COALESCE(?,next_action),updated_at=CURRENT_TIMESTAMP " +
                            "WHERE id=? AND (current_version_id=? OR ? IS NULL)",
                    toPhase, nextAction, projectId, versionId, versionId);
            if (versionId != null && toPhase != null && writeVersionPhase) {
                jdbc.update("UPDATE creative_project_version SET phase=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND project_id=?", toPhase, versionId, projectId);
            }
        }
        Number key = generatedId(keyHolder);
        return key == null ? Map.of("projectId", projectId) : jdbc.queryForMap(
                "SELECT id,project_id projectId,version_id versionId,user_id userId,event_type eventType,from_phase fromPhase,to_phase toPhase,next_action nextAction,actor_type actorType,actor_id actorId,idempotency_key idempotencyKey,payload_json payloadJson,created_at createdAt FROM creative_project_event WHERE id=?", key.longValue());
    }

    private Map<String, Object> freezeVersionInternal(Long projectId, Long versionId, Long userId, String reason) {
        Map<String, Object> version = getVersionForUpdate(projectId, versionId);
        String status = text(version.get("status"));
        if ("archived".equalsIgnoreCase(status)) throw new IllegalStateException("归档版本不能冻结");
        if ("frozen".equalsIgnoreCase(status) && version.get("frozenAt") != null) return version;
        String freezeHash = snapshotHash(version, reason);
        if ("approved".equalsIgnoreCase(status)) {
            jdbc.update("UPDATE creative_project_version SET frozen_at=COALESCE(frozen_at,CURRENT_TIMESTAMP),frozen_by=COALESCE(frozen_by,?),freeze_reason=COALESCE(freeze_reason,?),freeze_hash=COALESCE(freeze_hash,?),updated_at=CURRENT_TIMESTAMP WHERE id=? AND project_id=?",
                    userId, limit(reason, 500), freezeHash, versionId, projectId);
            return getVersion(projectId, versionId, userId);
        }
        int updated = jdbc.update("UPDATE creative_project_version SET status='frozen',frozen_at=CURRENT_TIMESTAMP,frozen_by=?,freeze_reason=?,freeze_hash=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND project_id=? AND status NOT IN ('approved','archived')",
                userId, limit(reason, 500), freezeHash, versionId, projectId);
        if (updated == 0) throw new IllegalStateException("设计版本当前状态不允许冻结");
        jdbc.update("UPDATE creative_project SET current_version_id=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", versionId, projectId);
        return getVersion(projectId, versionId, userId);
    }

    private String snapshotHash(Map<String, Object> version, String reason) {
        try {
            String canonical = String.valueOf(version.getOrDefault("versionNo", "")) + "|"
                    + String.valueOf(version.getOrDefault("phase", "")) + "|"
                    + String.valueOf(version.getOrDefault("briefJson", "")) + "|"
                    + String.valueOf(version.getOrDefault("metadataJson", "")) + "|"
                    + String.valueOf(reason == null ? "" : reason);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(64);
            for (byte value : digest) out.append(String.format("%02x", value));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("设计版本快照计算失败", e);
        }
    }

    private String canonicalPhase(String phase) {
        String value = phase == null ? "" : phase.trim().toLowerCase(Locale.ROOT);
        value = PHASE_ALIASES.getOrDefault(value, value);
        if (!PHASES.contains(value)) throw new IllegalArgumentException("不支持的创作阶段: " + phase);
        return value;
    }

    private void validateTransition(String from, String to) {
        String source = canonicalPhase(blank(from) ? "brief" : from);
        String target = canonicalPhase(to);
        Set<String> allowed = PHASE_TRANSITIONS.getOrDefault(source, Set.of());
        if (!allowed.contains(target)) throw new IllegalStateException("创作阶段不能从 " + source + " 流转到 " + target);
    }

    private boolean allowVersionPhaseWrite(Long projectId, Long versionId, String current, String target) {
        Map<String, Object> rows = jdbc.queryForMap(
                "SELECT status,frozen_at frozenAt FROM creative_project_version WHERE id=? AND project_id=?",
                versionId, projectId);
        String status = text(rows.get("status"));
        if (rows.get("frozenAt") != null || Set.of("frozen", "approved", "archived").contains(status)) {
            // Workflow phase changes are audit metadata, not design edits. The
            // immutable brief/metadata snapshot remains protected by the
            // frozen status while review outcomes may still be recorded.
            return !Objects.equals(canonicalPhase(current), canonicalPhase(target));
        }
        return true;
    }

    private void assertFrozenPhaseTransitionAllowed(Map<String, Object> version, String target) {
        String status = text(version.get("status"));
        if (version.get("frozenAt") == null && !Set.of("frozen", "approved", "archived").contains(status)) return;
        if (!Set.of("human_review", "approved", "needs_revision", "sampling", "sample_review",
                "sample_accepted", "bulk_unlocked", "in_production", "shipped", "completed",
                "rejected", "cancelled").contains(target)) {
            throw new IllegalStateException("该设计版本已冻结，修改请新建版本");
        }
    }

    private Map<String, Object> ownedProject(Long projectId, Long userId) {
        requireUser(userId);
        if (projectId == null || projectId <= 0) throw new IllegalArgumentException("项目编号无效");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,project_no projectNo,user_id userId,name,theme,status,current_phase currentPhase,current_version_id currentVersionId,next_action nextAction,metadata_json metadataJson,created_at createdAt,updated_at updatedAt FROM creative_project WHERE id=? AND user_id=?",
                projectId, userId);
        if (rows.isEmpty()) throw new NoSuchElementException("项目不存在或无权访问");
        return rows.get(0);
    }

    private void requireVersion(Long projectId, Long versionId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM creative_project_version WHERE id=? AND project_id=?", Integer.class, versionId, projectId);
        if (count == null || count == 0) throw new NoSuchElementException("项目版本不存在");
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("请先登录");
    }

    private String uniqueNo(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(PROJECT_TIME) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    /** H2 may return auto-generated timestamp columns alongside the id. */
    private Number generatedId(KeyHolder holder) {
        try {
            Number key = holder.getKey();
            if (key != null) return key;
        } catch (org.springframework.dao.InvalidDataAccessApiUsageException ignored) {
            // Fall through to the first generated id entry.
        }
        for (Map<String, Object> row : holder.getKeyList()) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if ("ID".equalsIgnoreCase(entry.getKey()) && entry.getValue() instanceof Number number) return number;
            }
        }
        return null;
    }

    private String jsonOrNull(Object value) {
        if (value == null) return null;
        if (value instanceof String string) {
            String trimmed = string.trim();
            if (trimmed.isEmpty()) return null;
            try {
                mapper.readTree(trimmed);
                return trimmed;
            } catch (Exception ignored) {
                return quoteJson(trimmed);
            }
        }
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("项目数据格式无效", e); }
    }

    private String quoteJson(String value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("项目数据格式无效", e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, item) -> out.put(String.valueOf(key), item));
            return out;
        }
        return new LinkedHashMap<>();
    }

    private String nextAction(String phase) {
        return switch (phase == null ? "brief" : phase) {
            case "generation" -> "生成产品图并确认方案";
            case "multiview" -> "生成三视图或3D文件";
            case "preflight" -> "完成可生产性预检";
            case "ai_review" -> "提交AI评审";
            case "human_review" -> "等待人工审核";
            case "approved" -> "提交打样申请";
            case "needs_revision", "rejected" -> "创建新版本并重新提交";
            case "sampling" -> "等待打样进度";
            case "sample_review" -> "确认样品或提交返修意见";
            case "sample_accepted" -> "确认量产解锁";
            case "bulk_unlocked" -> "提交批量生产";
            case "in_production" -> "等待生产完成";
            case "shipped" -> "等待收货并验收";
            case "failed" -> "修复问题后重新生成";
            case "cancelled" -> "项目已取消";
            case "completed" -> "项目已完成";
            default -> "确认创作需求";
        };
    }

    private String text(Object value) { return value == null ? null : String.valueOf(value); }
    private Number number(Object value) { return value instanceof Number number ? number : null; }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private String limit(String value, int max) { return value == null ? null : value.substring(0, Math.min(value.length(), max)); }
}
