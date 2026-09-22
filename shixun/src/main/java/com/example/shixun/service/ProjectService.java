package com.example.shixun.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Project-centric collaboration engine: project -> stage -> task -> owner.
 *
 * <p>A project moves through a fixed stage sequence. At any time exactly one
 * {@code project_task} is active (status=pending); completing it advances the
 * project to the next stage and opens the next task. This replaces
 * per-request menu hunting with a single place every role checks for work.</p>
 */
@Service
public class ProjectService {

    public record Stage(String key, String name, String role) {}

    private static final List<Stage> SAMPLE_STAGES = List.of(
            new Stage("designer_review", "设计师初审", "designer"),
            new Stage("project_manager_review", "项目经理审核", "project_manager"),
            new Stage("designer_production_files", "设计师制作生产文件", "designer"),
            new Stage("production_quotation", "生产报价", "production"),
            new Stage("finance_payment", "财务收款", "finance"),
            new Stage("logistics_delivery", "物流发货", "logistics")
    );

    private static final List<Stage> MULTIVIEW_REVIEW_STAGES = List.of(
            new Stage("designer_review", "设计师初审", "designer"),
            new Stage("project_manager_review", "项目经理复审", "project_manager"),
            new Stage("designer_production_files", "设计师制作生产文件", "designer"),
            new Stage("production_review", "生产文件审核", "production")
    );

    private final JdbcTemplate jdbc;

    public ProjectService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcTemplate getJdbc() {
        return jdbc;
    }

    private List<Stage> stagesFor(String category) {
        if ("multiview".equals(category)) return MULTIVIEW_REVIEW_STAGES;
        return SAMPLE_STAGES;
    }

    public Map<String, Object> createProject(String title, String category, String ownerUsername,
                                              String productNo, Long workflowApplicationId) {
        return createProject(title, category, ownerUsername, productNo, workflowApplicationId, null);
    }

    public Map<String, Object> createProject(String title, String category, String ownerUsername,
                                              String productNo, Long workflowApplicationId, Long assetId) {
        List<Stage> stages = stagesFor(category);
        Stage first = stages.get(0);
        String projectNo = "PJ" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + (int) (Math.random() * 900 + 100);

        jdbc.update(
                "INSERT INTO project (project_no,title,category,product_no,asset_id,workflow_application_id,owner_username," +
                "current_stage_index,current_stage_key,current_stage_name,current_assignee_role,status) " +
                "VALUES (?,?,?,?,?,?,?,0,?,?,?,'active')",
                projectNo, title, category, productNo, assetId, workflowApplicationId, ownerUsername,
                first.key(), first.name(), first.role()
        );
        Long projectId = jdbc.queryForObject("SELECT id FROM project WHERE project_no=?", Long.class, projectNo);

        openTask(projectId, 0, first);
        addMember(projectId, ownerUsername, "owner");
        insertLog(projectId, null, "create", ownerUsername, "owner", title + " 项目已创建");

        return getProject(projectId);
    }

    private void openTask(Long projectId, int stageIndex, Stage stage) {
        jdbc.update(
                "INSERT INTO project_task (project_id,stage_index,stage_key,stage_name,assignee_role,status) " +
                "VALUES (?,?,?,?,?,'pending')",
                projectId, stageIndex, stage.key(), stage.name(), stage.role()
        );
    }

    private void addMember(Long projectId, String username, String role) {
        try {
            jdbc.update("INSERT INTO project_member (project_id,username,role) VALUES (?,?,?)", projectId, username, role);
        } catch (DuplicateKeyException ignored) {
            // Already a member; role on first join is kept.
        }
    }

    private void insertLog(Long projectId, Long taskId, String action, String operator, String operatorRole, String content) {
        jdbc.update("INSERT INTO project_log (project_id,task_id,action,operator,operator_role,content) VALUES (?,?,?,?,?,?)",
                projectId, taskId, action, operator, operatorRole, content);
    }

    public List<Map<String, Object>> getMyTasks(String role, String username) {
        List<Map<String, Object>> tasks;
        if ("admin".equals(role)) {
            tasks = jdbc.queryForList(
                    "SELECT t.id, t.project_id projectId, p.project_no projectNo, p.title, p.category, " +
                    "p.owner_username ownerUsername, t.stage_index stageIndex, t.stage_key stageKey, t.stage_name stageName, " +
                    "t.assignee_role assigneeRole, t.status, DATE_FORMAT(t.created_at,'%Y-%m-%d %H:%i:%s') createdAt, " +
                    "p.asset_id assetId, a.asset_type assetType, a.title assetTitle " +
                    "FROM project_task t JOIN project p ON p.id=t.project_id " +
                    "LEFT JOIN digital_asset a ON a.id=p.asset_id " +
                    "WHERE t.status='pending' ORDER BY t.created_at DESC"
            );
        } else {
            tasks = jdbc.queryForList(
                    "SELECT t.id, t.project_id projectId, p.project_no projectNo, p.title, p.category, " +
                    "p.owner_username ownerUsername, t.stage_index stageIndex, t.stage_key stageKey, t.stage_name stageName, " +
                    "t.assignee_role assigneeRole, t.status, DATE_FORMAT(t.created_at,'%Y-%m-%d %H:%i:%s') createdAt, " +
                    "p.asset_id assetId, a.asset_type assetType, a.title assetTitle " +
                    "FROM project_task t JOIN project p ON p.id=t.project_id " +
                    "LEFT JOIN digital_asset a ON a.id=p.asset_id " +
                    "WHERE t.status='pending' AND t.assignee_role=? ORDER BY t.created_at DESC",
                    role
            );
        }
        for (Map<String, Object> task : tasks) {
            if ("multiview".equals(task.get("category"))) {
                Long projectId = ((Number) task.get("projectId")).longValue();
                Long bundleId = findMultiViewBundleId(projectId);
                task.put("bundleId", bundleId);
                if (bundleId != null) task.putAll(multiViewBundleSummary(bundleId));
            }
        }
        return tasks;
    }

    public List<Map<String, Object>> getMyProjects(String username) {
        return jdbc.queryForList(
                "SELECT p.id, p.project_no projectNo, p.title, p.category, p.status, " +
                "p.current_stage_name currentStageName, p.current_assignee_role currentAssigneeRole, " +
                "DATE_FORMAT(p.created_at,'%Y-%m-%d %H:%i:%s') createdAt, " +
                "DATE_FORMAT(p.updated_at,'%Y-%m-%d %H:%i:%s') updatedAt, " +
                "p.asset_id assetId, a.asset_type assetType, a.title assetTitle " +
                "FROM project p JOIN project_member m ON m.project_id=p.id " +
                "LEFT JOIN digital_asset a ON a.id=p.asset_id " +
                "WHERE m.username=? ORDER BY p.updated_at DESC",
                username
        );
    }

    public Map<String, Object> getProject(Long projectId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.id, p.project_no projectNo, p.title, p.category, p.product_no productNo, " +
                "p.workflow_application_id workflowApplicationId, p.owner_username ownerUsername, " +
                "p.current_stage_index currentStageIndex, p.current_stage_key currentStageKey, " +
                "p.current_stage_name currentStageName, p.current_assignee_role currentAssigneeRole, p.status, " +
                "DATE_FORMAT(p.created_at,'%Y-%m-%d %H:%i:%s') createdAt, " +
                "DATE_FORMAT(p.updated_at,'%Y-%m-%d %H:%i:%s') updatedAt, " +
                "DATE_FORMAT(p.completed_at,'%Y-%m-%d %H:%i:%s') completedAt, " +
                "p.asset_id assetId, a.asset_type assetType, a.title assetTitle " +
                "FROM project p LEFT JOIN digital_asset a ON a.id=p.asset_id WHERE p.id=?", projectId
        );
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        Map<String, Object> project = new LinkedHashMap<>(rows.get(0));

        project.put("tasks", jdbc.queryForList(
                "SELECT id, stage_index stageIndex, stage_key stageKey, stage_name stageName, " +
                "assignee_role assigneeRole, assignee_username assigneeUsername, status, comment, " +
                "DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') createdAt, " +
                "DATE_FORMAT(finished_at,'%Y-%m-%d %H:%i:%s') finishedAt " +
                "FROM project_task WHERE project_id=? ORDER BY stage_index ASC", projectId
        ));
        project.put("members", jdbc.queryForList(
                "SELECT username, role, DATE_FORMAT(joined_at,'%Y-%m-%d %H:%i:%s') joinedAt " +
                "FROM project_member WHERE project_id=? ORDER BY joined_at ASC", projectId
        ));
        project.put("logs", jdbc.queryForList(
                "SELECT id, task_id taskId, action, operator, operator_role operatorRole, content, " +
                "DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') createdAt " +
                "FROM project_log WHERE project_id=? ORDER BY id ASC", projectId
        ));
        return project;
    }

    public Map<String, Object> completeTask(Long taskId, String operator, String operatorRole, String comment) {
        return completeTask(taskId, operator, operatorRole, comment, false);
    }

    public Map<String, Object> completeMultiViewReviewTask(Long taskId, Long bundleId, String operator,
                                                            String operatorRole, String comment) {
        validateMultiViewReviewTask(taskId, bundleId, operatorRole);
        return completeTask(taskId, operator, operatorRole, comment, true);
    }

    private Map<String, Object> completeTask(Long taskId, String operator, String operatorRole, String comment,
                                              boolean synchronizedReview) {
        Map<String, Object> task = requireTask(taskId);
        Long projectId = ((Number) task.get("projectId")).longValue();
        Map<String, Object> project = requireProject(projectId);
        validateAssignee(task, operatorRole);
        if ("multiview".equals(project.get("category")) && !synchronizedReview) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "三视图审核请使用工作台审核操作");
        }

        String note = blank(comment) ? "同意" : comment.trim();
        jdbc.update("UPDATE project_task SET status='done', assignee_username=?, comment=?, finished_at=CURRENT_TIMESTAMP WHERE id=?",
                operator, note, taskId);
        addMember(projectId, operator, operatorRole);
        insertLog(projectId, taskId, "complete", operator, operatorRole,
                String.valueOf(task.get("stageName")) + "：" + note);

        List<Stage> stages = stagesFor(String.valueOf(project.get("category")));
        int nextIndex = ((Number) task.get("stageIndex")).intValue() + 1;

        if (nextIndex < stages.size()) {
            Stage next = stages.get(nextIndex);
            jdbc.update("UPDATE project SET current_stage_index=?, current_stage_key=?, current_stage_name=?, current_assignee_role=? WHERE id=?",
                    nextIndex, next.key(), next.name(), next.role(), projectId);
            openTask(projectId, nextIndex, next);
            insertLog(projectId, null, "advance", operator, operatorRole, "项目进入阶段：" + next.name());
        } else {
            jdbc.update("UPDATE project SET status='completed', completed_at=CURRENT_TIMESTAMP WHERE id=?", projectId);
            insertLog(projectId, null, "complete_project", operator, operatorRole, "项目全部阶段已完成");
        }
        return getProject(projectId);
    }

    public Map<String, Object> rejectTask(Long taskId, String operator, String operatorRole, String comment) {
        return rejectTask(taskId, operator, operatorRole, comment, false);
    }

    public Map<String, Object> rejectMultiViewReviewTask(Long taskId, Long bundleId, String operator,
                                                          String operatorRole, String comment) {
        validateMultiViewReviewTask(taskId, bundleId, operatorRole);
        return rejectTask(taskId, operator, operatorRole, comment, true);
    }

    /** Attach the designer's ZIP and immediately route the project to production review. */
    public Map<String, Object> submitProductionFile(Long projectId, Long assetId, String fileName,
                                                     long fileSize, String mimeType, String operator,
                                                     String operatorRole) {
        if (projectId == null || assetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "项目和生产文件不能为空");
        }
        Map<String, Object> task = requirePendingTask(projectId, "designer_production_files");
        validateAssignee(task, operatorRole);
        jdbc.update("INSERT INTO project_asset (project_id,asset_id,asset_type,file_name,file_url,file_size,mime_type,created_by,created_at) " +
                        "SELECT ?,id,'production_file',?,file_url,?,?,?,CURRENT_TIMESTAMP FROM digital_asset WHERE id=?",
                projectId, fileName, fileSize,
                blank(mimeType) ? "application/zip" : mimeType, operator, assetId);
        return completeTask(((Number) task.get("id")).longValue(), operator, operatorRole, "生产文件已上传", true);
    }

    public Map<String, Object> reviewProductionFile(Long taskId, String status, String operator,
                                                     String operatorRole, String comment) {
        Map<String, Object> task = requireTask(taskId);
        if (!"production_review".equals(task.get("stageKey"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前任务不是生产文件审核任务");
        }
        validateAssignee(task, operatorRole);
        Long projectId = ((Number) task.get("projectId")).longValue();
        Map<String, Object> project = requireProject(projectId);
        if (!"multiview".equals(project.get("category"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前项目不是三视图生产文件项目");
        }
        if ("approved".equals(status)) {
            jdbc.update("UPDATE digital_asset a JOIN project_asset pa ON pa.asset_id=a.id " +
                            "SET a.status='approved',a.tags=CONCAT(COALESCE(a.tags,''),';生产文件审核=approved') " +
                            "WHERE pa.project_id=? AND pa.asset_type='production_file'", projectId);
            return completeTask(taskId, operator, operatorRole, blank(comment) ? "生产文件审核通过" : comment, true);
        }
        if ("rejected".equals(status)) {
            jdbc.update("UPDATE digital_asset a JOIN project_asset pa ON pa.asset_id=a.id " +
                            "SET a.status='rejected',a.tags=CONCAT(COALESCE(a.tags,''),';生产文件审核=rejected') " +
                            "WHERE pa.project_id=? AND pa.asset_type='production_file'", projectId);
            String note = blank(comment) ? "生产文件需修改" : comment.trim();
            jdbc.update("UPDATE project_task SET status='rejected',assignee_username=?,comment=?,finished_at=CURRENT_TIMESTAMP WHERE id=?",
                    operator, note, taskId);
            Stage designerStage = MULTIVIEW_REVIEW_STAGES.get(2);
            jdbc.update("UPDATE project SET status='active',current_stage_index=?,current_stage_key=?,current_stage_name=?,current_assignee_role=? WHERE id=?",
                    2, designerStage.key(), designerStage.name(), designerStage.role(), projectId);
            openTask(projectId, 2, designerStage);
            addMember(projectId, operator, operatorRole);
            insertLog(projectId, taskId, "reject", operator, operatorRole, "生产文件审核驳回：" + note);
            insertLog(projectId, null, "return", operator, operatorRole, "项目退回设计师重新制作生产文件");
            return getProject(projectId);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "生产文件审核状态只能是 approved / rejected");
    }

    private Map<String, Object> rejectTask(Long taskId, String operator, String operatorRole, String comment,
                                            boolean synchronizedReview) {
        Map<String, Object> task = requireTask(taskId);
        Long projectId = ((Number) task.get("projectId")).longValue();
        Map<String, Object> project = requireProject(projectId);
        validateAssignee(task, operatorRole);
        if ("multiview".equals(project.get("category")) && !synchronizedReview) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "三视图审核请使用工作台审核操作");
        }

        String note = blank(comment) ? "驳回" : comment.trim();
        jdbc.update("UPDATE project_task SET status='rejected', assignee_username=?, comment=?, finished_at=CURRENT_TIMESTAMP WHERE id=?",
                operator, note, taskId);
        jdbc.update("UPDATE project SET status='rejected' WHERE id=?", projectId);
        insertLog(projectId, taskId, "reject", operator, operatorRole,
                String.valueOf(task.get("stageName")) + "：" + note);
        return getProject(projectId);
    }

    private void validateAssignee(Map<String, Object> task, String operatorRole) {
        if (!"pending".equals(task.get("status"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该任务已被处理");
        }
        if (!"admin".equals(operatorRole) && !operatorRole.equals(task.get("assigneeRole"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前角色无权处理该任务");
        }
    }

    public Map<String, Object> validateMultiViewReviewTask(Long taskId, Long bundleId, String operatorRole) {
        Map<String, Object> task = requireTask(taskId);
        validateAssignee(task, operatorRole);
        if (!List.of("designer_review", "project_manager_review").contains(String.valueOf(task.get("stageKey")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前任务不是三视图审核任务");
        }
        Long projectId = ((Number) task.get("projectId")).longValue();
        Map<String, Object> project = requireProject(projectId);
        if (!"multiview".equals(project.get("category")) || !bundleId.equals(findMultiViewBundleId(projectId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "三视图作品包与当前审核任务不匹配");
        }
        return task;
    }

    private Long findMultiViewBundleId(Long projectId) {
        List<Long> ids = jdbc.query(
                "SELECT i.bundle_id FROM project_asset pa " +
                "JOIN creative_multiview_bundle_item i ON i.asset_id=pa.asset_id " +
                "WHERE pa.project_id=? GROUP BY i.bundle_id ORDER BY COUNT(*) DESC LIMIT 1",
                (rs, rowNum) -> rs.getLong(1), projectId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Map<String, Object> multiViewBundleSummary(Long bundleId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT bundle_no bundleNo,product_no productNo,product_name productName,material," +
                "product_size productSize,view_count viewCount,purpose,museum_name museumName,note," +
                "review_comment reviewComment,status bundleStatus FROM creative_multiview_bundle WHERE id=?",
                bundleId);
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    private Map<String, Object> requireTask(Long taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_id projectId, stage_index stageIndex, stage_key stageKey, stage_name stageName, " +
                "assignee_role assigneeRole, status FROM project_task WHERE id=?", taskId
        );
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        return rows.get(0);
    }

    private Map<String, Object> requirePendingTask(Long projectId, String stageKey) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, project_id projectId, stage_index stageIndex, stage_key stageKey, stage_name stageName, " +
                        "assignee_role assigneeRole, status FROM project_task WHERE project_id=? AND stage_key=? AND status='pending' LIMIT 1",
                projectId, stageKey);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前项目不在生产文件制作阶段");
        return rows.get(0);
    }

    private Map<String, Object> requireProject(Long projectId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, category FROM project WHERE id=?", projectId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在");
        return rows.get(0);
    }

    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
}
