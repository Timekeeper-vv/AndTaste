package com.example.shixun.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 发送通知给指定角色的所有用户
     */
    public void notifyRole(String role, String title, String message, Long applicationId) {
        List<String> users = jdbc.queryForList(
            "SELECT username FROM user WHERE role=? AND status='active'",
            String.class, role
        );

        for (String username : users) {
            jdbc.update(
                "INSERT INTO workflow_notification (application_id, receiver, title, message, read_flag) VALUES (?,?,?,?,?)",
                applicationId, username, title, message, 0
            );
        }
    }

    /**
     * 发送通知给指定用户
     */
    public void notifyUser(String username, String title, String message, Long applicationId) {
        jdbc.update(
            "INSERT INTO workflow_notification (application_id, receiver, title, message, read_flag) VALUES (?,?,?,?,?)",
            applicationId, username, title, message, 0
        );
    }

    /**
     * 获取用户未读通知
     */
    public List<Map<String, Object>> getUnreadNotifications(String username) {
        return jdbc.queryForList(
            "SELECT id, application_id applicationId, title, message, " +
            "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') createdAt " +
            "FROM workflow_notification " +
            "WHERE receiver=? AND read_flag=0 " +
            "ORDER BY created_at DESC",
            username
        );
    }

    /**
     * 标记通知为已读
     */
    public void markAsRead(Long notificationId, String username) {
        jdbc.update(
            "UPDATE workflow_notification SET read_flag=1 WHERE id=? AND receiver=?",
            notificationId, username
        );
    }

    /**
     * 标记所有通知为已读
     */
    public void markAllAsRead(String username) {
        jdbc.update(
            "UPDATE workflow_notification SET read_flag=1 WHERE receiver=? AND read_flag=0",
            username
        );
    }

    /**
     * 获取未读通知数量
     */
    public int getUnreadCount(String username) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM workflow_notification WHERE receiver=? AND read_flag=0",
            Integer.class, username
        );
        return count != null ? count : 0;
    }
}
