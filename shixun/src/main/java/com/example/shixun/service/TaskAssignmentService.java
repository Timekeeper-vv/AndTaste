package com.example.shixun.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TaskAssignmentService {

    private final JdbcTemplate jdbc;

    public TaskAssignmentService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 根据申请类型和阶段自动分配给对应角色
     * 正确流程: 用户提交 → 设计师审核 → 项目经理审核 → 设计师制作生产文件 → 生产报价 → 财务收款 → 物流发货
     */
    public String assignTaskByStage(String requestType, String stage) {
        if ("sample".equals(requestType) || "打样".equals(requestType)) {
            switch (stage) {
                case "designer_initial_review":
                case "设计师初审":
                    return "designer";
                case "project_manager_review":
                case "项目经理审核":
                    return "project_manager";
                case "designer_production_files":
                case "设计师生产文件":
                    return "designer";
                case "production_quotation":
                case "生产报价":
                    return "production";
                case "finance_payment":
                case "财务收款":
                    return "finance";
                case "logistics_delivery":
                case "物流发货":
                    return "logistics";
                default:
                    return "designer";
            }
        }

        if ("bulk".equals(requestType) || "大货".equals(requestType)) {
            switch (stage) {
                case "designer_review":
                case "设计师审核":
                    return "designer";
                case "project_manager_review":
                case "项目经理审核":
                    return "project_manager";
                case "designer_production_files":
                case "设计师生产文件":
                    return "designer";
                case "production_planning":
                case "生产规划":
                    return "production";
                case "payment_management":
                case "财务管理":
                    return "finance";
                case "warehouse_management":
                case "仓储管理":
                    return "logistics";
                default:
                    return "designer";
            }
        }

        return "designer";
    }

    /**
     * 获取某角色的待处理任务列表
     */
    public List<Map<String, Object>> getPendingTasksByRole(String role, String username) {
        if ("admin".equals(role)) {
            return jdbc.queryForList(
                "SELECT id, app_no appNo, category, type_key typeKey, title, applicant, " +
                "applicant_role applicantRole, status, current_step currentStep, " +
                "current_step_name currentStepName, current_handler currentHandler, " +
                "DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') submittedAt, " +
                "DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') updatedAt " +
                "FROM workflow_application " +
                "WHERE status='pending' AND deleted=0 " +
                "ORDER BY submitted_at DESC"
            );
        }

        if ("project_manager".equals(role)) {
            return jdbc.queryForList(
                "SELECT id, app_no appNo, category, type_key typeKey, title, applicant, " +
                "applicant_role applicantRole, status, current_step currentStep, " +
                "current_step_name currentStepName, current_handler currentHandler, " +
                "DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') submittedAt, " +
                "DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') updatedAt " +
                "FROM workflow_application " +
                "WHERE status='pending' AND deleted=0 " +
                "ORDER BY submitted_at DESC"
            );
        }

        return jdbc.queryForList(
            "SELECT id, app_no appNo, category, type_key typeKey, title, applicant, " +
            "applicant_role applicantRole, status, current_step currentStep, " +
            "current_step_name currentStepName, current_handler currentHandler, " +
            "DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') submittedAt, " +
            "DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') updatedAt " +
            "FROM workflow_application " +
            "WHERE status='pending' AND current_handler=? AND deleted=0 " +
            "ORDER BY submitted_at DESC",
            role
        );
    }

    /**
     * 获取我提交的申请
     */
    public List<Map<String, Object>> getMySubmissions(String username) {
        return jdbc.queryForList(
            "SELECT id, app_no appNo, category, type_key typeKey, title, applicant, " +
            "applicant_role applicantRole, status, current_step currentStep, " +
            "current_step_name currentStepName, current_handler currentHandler, " +
            "DATE_FORMAT(submitted_at, '%Y-%m-%d %H:%i:%s') submittedAt, " +
            "DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') updatedAt " +
            "FROM workflow_application " +
            "WHERE applicant=? AND deleted=0 " +
            "ORDER BY submitted_at DESC",
            username
        );
    }

    /**
     * 获取申请单类型
     */
    public String getApplicationType(Long applicationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT type_key FROM workflow_application WHERE id=?", applicationId
        );
        if (rows.isEmpty()) return null;
        String typeKey = (String) rows.get(0).get("type_key");

        if (typeKey != null && (typeKey.contains("sample") || typeKey.contains("打样"))) {
            return "sample";
        }
        if (typeKey != null && (typeKey.contains("bulk") || typeKey.contains("大货"))) {
            return "bulk";
        }
        return "other";
    }
}
