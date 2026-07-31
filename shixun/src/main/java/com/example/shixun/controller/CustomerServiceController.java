package com.example.shixun.controller;

import com.example.shixun.service.SiliconFlowChatService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/** C端客服：AI 首响 + 后台人工接管。 */
@RestController
@RequestMapping("/api/customer-service")
@CrossOrigin(origins = "*")
public class CustomerServiceController {
    private final JdbcTemplate jdbc;
    private final SiliconFlowChatService siliconFlow;

    public CustomerServiceController(JdbcTemplate jdbc, SiliconFlowChatService siliconFlow) {
        this.jdbc = jdbc;
        this.siliconFlow = siliconFlow;
    }

    @PostMapping("/conversations/open")
    public Map<String, Object> open(@RequestBody Map<String, Object> body) {
        ensureTables();
        Long userId = longValue(body.get("currentUserId"));
        if (userId == null) throw new IllegalArgumentException("缺少用户信息");
        String username = userName(userId);
        List<Map<String, Object>> existing = jdbc.queryForList("SELECT id, status FROM customer_service_conversation WHERE user_id=? ORDER BY updated_at DESC LIMIT 1", userId);
        Long conversationId;
        if (existing.isEmpty()) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("INSERT INTO customer_service_conversation (user_id,user_name,status) VALUES (?,?,'open')", Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, userId); ps.setString(2, username); return ps;
            }, keyHolder);
            conversationId = Objects.requireNonNull(keyHolder.getKey()).longValue();
            addMessage(conversationId, "assistant", null, "AI 客服助手", "你好，我是之间智造客服助手。你可以咨询创作、积分、材料、审核、博物馆合作、生产或版权服务；复杂问题会转交人工客服处理。", false);
        } else {
            conversationId = ((Number) existing.get(0).get("id")).longValue();
            jdbc.update("UPDATE customer_service_conversation SET status='open', updated_at=CURRENT_TIMESTAMP WHERE id=?", conversationId);
        }
        return conversationDetail(conversationId, "user");
    }

    @GetMapping("/conversations/mine")
    public Map<String, Object> mine(@RequestParam Long currentUserId) {
        ensureTables();
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM customer_service_conversation WHERE user_id=? ORDER BY updated_at DESC LIMIT 1", currentUserId);
        if (rows.isEmpty()) return Map.of("conversation", null, "messages", List.of());
        return conversationDetail(((Number) rows.get(0).get("id")).longValue(), "user");
    }

    @GetMapping("/admin/conversations")
    public List<Map<String, Object>> adminConversations(@RequestHeader(value = "X-Current-Role", required = false) String role) {
        requireStaff(role); ensureTables();
        return jdbc.queryForList("SELECT c.id,u.id userId,u.username userName,COALESCE(c.status,'new') status,COALESCE(c.human_takeover,0) humanTakeover,c.taken_by_name takenByName,c.updated_at updatedAt, " +
                "(SELECT content FROM customer_service_message m WHERE m.conversation_id=c.id ORDER BY m.id DESC LIMIT 1) lastMessage, " +
                "(SELECT COUNT(*) FROM customer_service_message m WHERE m.conversation_id=c.id AND m.sender_type='user' AND m.read_by_staff=0) unreadCount " +
                "FROM user u LEFT JOIN customer_service_conversation c ON c.id=(SELECT c2.id FROM customer_service_conversation c2 WHERE c2.user_id=u.id ORDER BY c2.updated_at DESC LIMIT 1) " +
                "WHERE u.role='user' ORDER BY c.updated_at IS NULL DESC,c.updated_at DESC,u.id DESC");
    }

    @PostMapping("/admin/conversations/open")
    public Map<String, Object> adminOpen(@RequestBody Map<String, Object> body, @RequestHeader(value = "X-Current-Role", required = false) String role) {
        requireStaff(role); ensureTables();
        Long userId = longValue(body.get("userId"));
        if (userId == null) throw new IllegalArgumentException("请选择C端用户");
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT id FROM customer_service_conversation WHERE user_id=? ORDER BY updated_at DESC LIMIT 1", userId);
        if (rows.isEmpty()) {
            KeyHolder keyHolder = new GeneratedKeyHolder(); String username = userName(userId);
            jdbc.update(connection -> { PreparedStatement ps=connection.prepareStatement("INSERT INTO customer_service_conversation (user_id,user_name,status) VALUES (?,?,'open')", Statement.RETURN_GENERATED_KEYS); ps.setLong(1,userId); ps.setString(2,username); return ps; }, keyHolder);
            Long id=Objects.requireNonNull(keyHolder.getKey()).longValue(); addMessage(id,"assistant",null,"AI 客服助手","客服会话已建立。你可以直接留言；如需人工处理，后台客服会接入本次会话。",false); return conversationDetail(id,"staff");
        }
        return conversationDetail(((Number)rows.get(0).get("id")).longValue(),"staff");
    }

    @GetMapping("/conversations/{id}")
    public Map<String, Object> detail(@PathVariable Long id, @RequestParam String viewer, @RequestParam(required = false) Long currentUserId,
                                      @RequestHeader(value = "X-Current-Role", required = false) String role) {
        ensureTables();
        if ("staff".equals(viewer)) requireStaff(role);
        else requireOwner(id, currentUserId);
        return conversationDetail(id, viewer);
    }

    @PostMapping("/conversations/{id}/messages")
    public Map<String, Object> send(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                    @RequestHeader(value = "X-Current-Role", required = false) String role) {
        ensureTables();
        Long senderId = longValue(body.get("currentUserId"));
        String senderType = string(body.get("senderType"));
        String content = string(body.get("content"));
        if (senderId == null || content == null || content.isBlank()) throw new IllegalArgumentException("消息内容不能为空");
        boolean staff = "staff".equals(senderType);
        if (staff) requireStaff(role); else requireOwner(id, senderId);
        addMessage(id, staff ? "staff" : "user", senderId, staff ? staffName(senderId) : userName(senderId), content.trim(), false);
        jdbc.update("UPDATE customer_service_conversation SET status='open', updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        if (!staff && !humanTakeover(id)) {
            String reply = assistantReply(content.trim());
            addMessage(id, "assistant", null, "AI 客服助手", reply, false);
        }
        return conversationDetail(id, staff ? "staff" : "user");
    }

    @PostMapping("/conversations/{id}/human-takeover")
    public Map<String, Object> humanTakeover(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body,
                                             @RequestHeader(value = "X-Current-Role", required = false) String role) {
        requireStaff(role); ensureTables();
        boolean enabled = body == null || !Boolean.FALSE.equals(body.get("enabled"));
        Long operatorId = body == null ? null : longValue(body.get("currentUserId"));
        String operatorName = operatorId == null ? "人工客服" : staffName(operatorId);
        jdbc.update("UPDATE customer_service_conversation SET human_takeover=?, taken_by=?, taken_by_name=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", enabled ? 1 : 0, enabled ? operatorId : null, enabled ? operatorName : null, id);
        return conversationDetail(id, "staff");
    }

    @PostMapping("/conversations/{id}/close")
    public Map<String, Object> close(@PathVariable Long id, @RequestHeader(value = "X-Current-Role", required = false) String role) {
        requireStaff(role); ensureTables(); jdbc.update("UPDATE customer_service_conversation SET status='closed',updated_at=CURRENT_TIMESTAMP WHERE id=?", id);
        return Map.of("message", "会话已关闭");
    }

    private Map<String, Object> conversationDetail(Long id, String viewer) {
        Map<String,Object> conversation = jdbc.queryForMap("SELECT id,user_id userId,user_name userName,status,human_takeover humanTakeover,taken_by_name takenByName,created_at createdAt,updated_at updatedAt FROM customer_service_conversation WHERE id=?", id);
        List<Map<String,Object>> messages = jdbc.queryForList("SELECT id,sender_type senderType,sender_name senderName,content,created_at createdAt FROM customer_service_message WHERE conversation_id=? ORDER BY id", id);
        if ("staff".equals(viewer)) jdbc.update("UPDATE customer_service_message SET read_by_staff=1 WHERE conversation_id=? AND sender_type='user'", id);
        else jdbc.update("UPDATE customer_service_message SET read_by_user=1 WHERE conversation_id=? AND sender_type IN ('staff','assistant')", id);
        return Map.of("conversation", conversation, "messages", messages);
    }

    private void addMessage(Long conversationId, String type, Long senderId, String senderName, String content, boolean ignored) {
        jdbc.update("INSERT INTO customer_service_message (conversation_id,sender_type,sender_id,sender_name,content) VALUES (?,?,?,?,?)", conversationId, type, senderId, senderName, content);
    }

    private String assistantReply(String message) {
        String local = "我已收到你的问题。涉及订单、支付、版权、生产审批或博物馆合作时，建议保留作品编号和截图；人工客服也可以在后台继续跟进。";
        try {
            String prompt = "你是之间智造C端客服助手。只回答文创创作、积分、3D材质、审核、生产、博物馆合作和版权服务。中文，120字内，务实；涉及付款、版权、授权和生产必须提示人工复核。用户问题：" + message;
            String reply = siliconFlow.chat("你是专业且友好的平台客服。", prompt, 0.35, 400, 8);
            return reply == null || reply.isBlank() ? local : reply.trim();
        } catch (Exception ignored) { return local; }
    }

    private boolean humanTakeover(Long id) {
        Integer value = jdbc.queryForObject("SELECT human_takeover FROM customer_service_conversation WHERE id=?", Integer.class, id);
        return value != null && value == 1;
    }

    private void requireOwner(Long conversationId, Long userId) {
        if (userId == null) throw new IllegalArgumentException("缺少用户信息");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM customer_service_conversation WHERE id=? AND user_id=?", Integer.class, conversationId, userId);
        if (count == null || count == 0) throw new IllegalStateException("无权访问该客服会话");
    }
    private void requireStaff(String role) { if (!"admin".equals(role) && !"technician".equals(role) && !"feeder".equals(role)) throw new IllegalStateException("仅管理端账号可处理客服消息"); }
    private String userName(Long id) { return jdbc.queryForObject("SELECT username FROM user WHERE id=?", String.class, id); }
    private String staffName(Long id) { try { return userName(id); } catch(Exception ignored) { return "人工客服"; } }
    private Long longValue(Object o) { return o instanceof Number ? ((Number)o).longValue() : o == null ? null : Long.valueOf(String.valueOf(o)); }
    private String string(Object o) { return o == null ? null : String.valueOf(o); }
    private void ensureTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS customer_service_conversation (id BIGINT AUTO_INCREMENT PRIMARY KEY,user_id BIGINT NOT NULL,user_name VARCHAR(100) NOT NULL,status VARCHAR(20) NOT NULL DEFAULT 'open',human_takeover TINYINT NOT NULL DEFAULT 0,taken_by BIGINT NULL,taken_by_name VARCHAR(100),created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,INDEX idx_customer_service_user (user_id),INDEX idx_customer_service_updated (updated_at))");
        try { jdbc.execute("ALTER TABLE customer_service_conversation ADD COLUMN human_takeover TINYINT NOT NULL DEFAULT 0"); } catch (Exception ignored) { }
        try { jdbc.execute("ALTER TABLE customer_service_conversation ADD COLUMN taken_by BIGINT NULL"); } catch (Exception ignored) { }
        try { jdbc.execute("ALTER TABLE customer_service_conversation ADD COLUMN taken_by_name VARCHAR(100)"); } catch (Exception ignored) { }
        jdbc.execute("CREATE TABLE IF NOT EXISTS customer_service_message (id BIGINT AUTO_INCREMENT PRIMARY KEY,conversation_id BIGINT NOT NULL,sender_type VARCHAR(20) NOT NULL,sender_id BIGINT NULL,sender_name VARCHAR(100),content TEXT NOT NULL,read_by_user TINYINT NOT NULL DEFAULT 0,read_by_staff TINYINT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,INDEX idx_customer_service_message_conversation (conversation_id))");
    }
}
