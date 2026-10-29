package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/creative/ai/consumer-activities")
public class ConsumerPointsActivityController {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> PACKET_SLOTS = List.of("09", "14", "21");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public ConsumerPointsActivityController(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @GetMapping("/overview")
    public Map<String, Object> overview() {
        long userId = requireConsumerUser();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("date", today.toString());
        out.put("checkin", checkinState(userId, today));
        out.put("redPackets", packetState(userId, today));
        out.put("quiz", quizState(userId, today));
        out.put("creditAccount", account(userId));
        out.put("tasks", jdbc.queryForList("SELECT campaign_key campaignKey,title,brief,deadline,reward_amount rewardAmount,status FROM consumer_bounty_task WHERE status='active' AND (deadline IS NULL OR deadline>=?) ORDER BY id DESC", today));
        return out;
    }

    @PostMapping("/checkin")
    public Map<String, Object> checkin() {
        long userId = requireConsumerUser();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return Objects.requireNonNull(transactions.execute(status -> {
            jdbc.update("INSERT IGNORE INTO consumer_points_checkin (user_id,last_checkin_date,streak_days) VALUES (?,NULL,0)", userId);
            Map<String, Object> row = jdbc.queryForMap("SELECT last_checkin_date lastDate,streak_days streakDays FROM consumer_points_checkin WHERE user_id=? FOR UPDATE", userId);
            if (today.toString().equals(String.valueOf(row.get("lastDate")))) {
                throw new IllegalStateException("今天已经签到过了");
            }
            int prior = ((Number) row.get("streakDays")).intValue();
            String last = row.get("lastDate") == null ? "" : String.valueOf(row.get("lastDate"));
            int streak = today.minusDays(1).toString().equals(last) ? prior + 1 : 1;
            int reward = 120 + (streak % 7 == 0 ? 300 : 0);
            jdbc.update("UPDATE consumer_points_checkin SET last_checkin_date=?,streak_days=? WHERE user_id=?", today, streak, userId);
            grantPoints(userId, reward, "每日签到" + (reward > 120 ? "，连续7天奖励" : ""));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("date", today.toString()); out.put("streakDays", streak); out.put("points", reward); out.put("creditAccount", account(userId));
            return out;
        }));
    }

    @GetMapping("/red-packets/current")
    public List<Map<String, Object>> currentPacket() {
        long userId = requireConsumerUser();
        return packetState(userId, LocalDate.now(BUSINESS_ZONE));
    }

    @GetMapping("/tasks")
    public List<Map<String, Object>> tasks() {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return jdbc.queryForList("SELECT id,campaign_key campaignKey,title,brief,deadline,reward_amount rewardAmount FROM consumer_bounty_task WHERE status='active' AND (deadline IS NULL OR deadline>=?) ORDER BY id DESC", today);
    }

    @PostMapping("/tasks/{taskId}/submissions")
    public Map<String, Object> submitTask(@PathVariable long taskId, @RequestBody Map<String, Object> body) {
        long userId = requireConsumerUser();
        Map<String, Object> task = jdbc.queryForMap("SELECT id,reward_amount rewardAmount,status,deadline FROM consumer_bounty_task WHERE id=?", taskId);
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (!"active".equals(String.valueOf(task.get("status")))) throw new IllegalStateException("该悬赏任务暂未开放投稿");
        Object deadline = task.get("deadline");
        if (deadline != null && LocalDate.parse(String.valueOf(deadline)).isBefore(today)) throw new IllegalStateException("该悬赏任务已截稿");
        long bundleId = requiredLong(body, "bundleId");
        Object rawIds = body.get("viewAssetIds");
        if (!(rawIds instanceof List<?> ids) || ids.size() != 3) throw new IllegalArgumentException("必须提交正面、侧面、背面三张视图");
        List<Long> viewAssetIds = new ArrayList<>();
        for (Object id : ids) viewAssetIds.add(Long.parseLong(String.valueOf(id)));
        long assetId = viewAssetIds.get(0);
        Map<String, Object> bundle = jdbc.queryForMap("SELECT id,user_id userId,view_count viewCount FROM creative_multiview_bundle WHERE id=?", bundleId);
        if (((Number) bundle.get("userId")).longValue() != userId || ((Number) bundle.get("viewCount")).intValue() != 3) throw new IllegalArgumentException("请选择本人完整的三视图作品包");
        List<Map<String, Object>> bundleItems = jdbc.queryForList("SELECT view_key viewKey,asset_id assetId FROM creative_multiview_bundle_item WHERE bundle_id=? ORDER BY CASE view_key WHEN 'front' THEN 1 WHEN 'left' THEN 2 WHEN 'back' THEN 3 ELSE 4 END", bundleId);
        if (bundleItems.size() != 3) throw new IllegalArgumentException("三视图作品包必须包含正面、侧面和背面");
        List<Long> bundledIds = bundleItems.stream().map(item -> ((Number) item.get("assetId")).longValue()).toList();
        if (!bundledIds.equals(viewAssetIds)) throw new IllegalArgumentException("投稿图片必须与所选三视图作品包一致");
        for (Long viewAssetId : viewAssetIds) {
            List<Map<String, Object>> assets = jdbc.queryForList("SELECT id,asset_type assetType,format,JSON_UNQUOTE(JSON_EXTRACT(metadata_json,'$.size')) fileSize,created_by createdBy FROM digital_asset WHERE id=?", viewAssetId);
            if (assets.isEmpty() || ((Number) assets.get(0).get("createdBy")).longValue() != userId || !"image".equals(String.valueOf(assets.get(0).get("assetType")))) throw new IllegalArgumentException("三视图必须是本人上传的图片");
            Map<String, Object> image = assets.get(0);
            String format = String.valueOf(image.get("format")).toLowerCase(Locale.ROOT);
            long bytes = Long.parseLong(String.valueOf(image.get("fileSize")));
            if (!Set.of("jpg", "jpeg").contains(format)) throw new IllegalArgumentException("三视图仅支持 JPG 格式");
            if (bytes < 1_048_576L || bytes > 3_145_728L) throw new IllegalArgumentException("每张三视图大小必须为 1–3 MB");
        }
        String name = requiredText(body, "productName", 200);
        String category = requiredText(body, "productCategory", 100);
        String features = requiredText(body, "productFeatures", 1000);
        String prototype = requiredText(body, "artifactPrototype", 500);
        String description = requiredText(body, "productDescription", 4000);
        return Objects.requireNonNull(transactions.execute(status -> {
            jdbc.queryForMap("SELECT id FROM consumer_bounty_task WHERE id=? FOR UPDATE", taskId);
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM consumer_bounty_submission WHERE task_id=? AND user_id=?", Integer.class, taskId, userId);
            if (count != null && count >= 5) throw new IllegalStateException("每个账号最多可提交5件作品");
            String viewIdsJson = "[" + String.join(",", viewAssetIds.stream().map(String::valueOf).toList()) + "]";
            jdbc.update("INSERT INTO consumer_bounty_submission (task_id,user_id,asset_id,bundle_id,view_asset_ids_json,product_name,product_category,product_features,artifact_prototype,product_description) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    taskId, userId, assetId, bundleId, viewIdsJson, name, category, features, prototype, description);
            Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            Map<String, Object> result = new LinkedHashMap<>(); result.put("id", id); result.put("status", "pending_review"); result.put("message", "投稿已提交，审核通过后发放积分");
            return result;
        }));
    }

    @GetMapping("/tasks/mine")
    public List<Map<String, Object>> myTaskSubmissions() {
        long userId = requireConsumerUser();
        return jdbc.queryForList("SELECT s.id,s.task_id taskId,t.title,s.asset_id assetId,s.product_name productName,s.product_category productCategory,s.status,s.selected_for_sample selectedForSample,s.created_at createdAt FROM consumer_bounty_submission s JOIN consumer_bounty_task t ON t.id=s.task_id WHERE s.user_id=? ORDER BY s.id DESC", userId);
    }

    @GetMapping("/sample-vouchers/mine")
    public List<Map<String, Object>> mySampleVouchers() {
        long userId = requireConsumerUser();
        return jdbc.queryForList("SELECT v.id,v.bundle_id bundleId,v.status,v.created_at createdAt,v.expires_at expiresAt,v.material,v.sample_quantity sampleQuantity,v.shipping_included shippingIncluded,t.title taskTitle,s.product_name productName FROM consumer_bounty_sample_voucher v JOIN consumer_bounty_task t ON t.id=v.task_id JOIN consumer_bounty_submission s ON s.id=v.submission_id WHERE v.user_id=? ORDER BY v.id DESC", userId);
    }

    @GetMapping("/admin/tasks")
    public List<Map<String, Object>> adminTasks() {
        requireAdmin();
        return jdbc.queryForList("SELECT id,campaign_key campaignKey,title,brief,deadline,reward_amount rewardAmount,status,created_at createdAt FROM consumer_bounty_task ORDER BY id DESC");
    }

    @PostMapping("/admin/tasks")
    public Map<String, Object> createTask(@RequestBody Map<String, Object> body) {
        requireAdmin();
        String key = requiredText(body, "campaignKey", 80);
        String title = requiredText(body, "title", 200);
        String brief = requiredText(body, "brief", 8000);
        int reward = body.get("rewardAmount") == null ? 2000 : Integer.parseInt(String.valueOf(body.get("rewardAmount")));
        if (reward <= 0) throw new IllegalArgumentException("积分奖励必须大于0");
        LocalDate deadline = body.get("deadline") == null || String.valueOf(body.get("deadline")).isBlank() ? null : LocalDate.parse(String.valueOf(body.get("deadline")));
        String state = String.valueOf(body.getOrDefault("status", "draft"));
        if (!List.of("draft", "active", "closed").contains(state)) throw new IllegalArgumentException("任务状态无效");
        if ("active".equals(state) && deadline == null) throw new IllegalArgumentException("开放投稿前必须设置有效截稿日期");
        jdbc.update("INSERT INTO consumer_bounty_task (campaign_key,title,brief,deadline,reward_amount,status) VALUES (?,?,?,?,?,?)", key, title, brief, deadline, reward, state);
        return Map.of("id", jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class), "status", state);
    }

    @PutMapping("/admin/tasks/{taskId}")
    public Map<String, Object> updateTask(@PathVariable long taskId, @RequestBody Map<String, Object> body) {
        requireAdmin();
        String title = requiredText(body, "title", 200);
        String brief = requiredText(body, "brief", 8000);
        int reward = Integer.parseInt(String.valueOf(body.getOrDefault("rewardAmount", 2000)));
        LocalDate deadline = body.get("deadline") == null || String.valueOf(body.get("deadline")).isBlank() ? null : LocalDate.parse(String.valueOf(body.get("deadline")));
        String state = String.valueOf(body.getOrDefault("status", "draft"));
        if (!List.of("draft", "active", "closed").contains(state) || reward <= 0) throw new IllegalArgumentException("任务配置无效");
        if ("active".equals(state) && deadline == null) throw new IllegalArgumentException("开放投稿前必须设置有效截稿日期");
        int changed = jdbc.update("UPDATE consumer_bounty_task SET title=?,brief=?,deadline=?,reward_amount=?,status=? WHERE id=?", title, brief, deadline, reward, state, taskId);
        if (changed != 1) throw new IllegalArgumentException("悬赏任务不存在");
        return Map.of("id", taskId, "status", state);
    }

    @GetMapping("/admin/tasks/submissions")
    public List<Map<String, Object>> adminSubmissions(@RequestParam(required=false) String statusFilter) {
        requireAdmin();
        if (statusFilter == null || statusFilter.isBlank()) return jdbc.queryForList("SELECT s.id,s.task_id taskId,t.title,s.user_id userId,u.username,s.asset_id assetId,s.bundle_id bundleId,s.view_asset_ids_json viewAssetIdsJson,a.title assetTitle,s.product_name productName,s.product_category productCategory,s.product_features productFeatures,s.artifact_prototype artifactPrototype,s.product_description productDescription,s.status,s.created_at createdAt FROM consumer_bounty_submission s JOIN consumer_bounty_task t ON t.id=s.task_id JOIN user u ON u.id=s.user_id JOIN digital_asset a ON a.id=s.asset_id ORDER BY s.id DESC");
        return jdbc.queryForList("SELECT s.id,s.task_id taskId,t.title,s.user_id userId,u.username,s.asset_id assetId,s.bundle_id bundleId,s.view_asset_ids_json viewAssetIdsJson,a.title assetTitle,s.product_name productName,s.product_category productCategory,s.product_features productFeatures,s.artifact_prototype artifactPrototype,s.product_description productDescription,s.status,s.created_at createdAt FROM consumer_bounty_submission s JOIN consumer_bounty_task t ON t.id=s.task_id JOIN user u ON u.id=s.user_id JOIN digital_asset a ON a.id=s.asset_id WHERE s.status=? ORDER BY s.id DESC", statusFilter);
    }

    @PostMapping("/admin/tasks/submissions/{submissionId}/review")
    public Map<String, Object> reviewTaskSubmission(@PathVariable long submissionId, @RequestBody Map<String, Object> body) {
        long adminId = requireAdmin();
        String decision = String.valueOf(body.getOrDefault("decision", ""));
        boolean selected = Boolean.parseBoolean(String.valueOf(body.getOrDefault("selectedForSample", false)));
        if (!List.of("approved", "rejected").contains(decision)) throw new IllegalArgumentException("审核结果无效");
        return Objects.requireNonNull(transactions.execute(status -> {
            Map<String, Object> row = jdbc.queryForMap("SELECT s.id,s.task_id taskId,s.user_id userId,s.bundle_id bundleId,s.status,t.reward_amount rewardAmount FROM consumer_bounty_submission s JOIN consumer_bounty_task t ON t.id=s.task_id WHERE s.id=? FOR UPDATE", submissionId);
            if (!"pending_review".equals(String.valueOf(row.get("status")))) throw new IllegalStateException("该投稿已经审核");
            long userId = ((Number) row.get("userId")).longValue();
            jdbc.update("UPDATE consumer_bounty_submission SET status=?,selected_for_sample=?,reviewed_by=?,reviewed_at=NOW() WHERE id=?", decision, selected && "approved".equals(decision) ? 1 : 0, String.valueOf(adminId), submissionId);
            if ("approved".equals(decision)) {
                int reward = ((Number) row.get("rewardAmount")).intValue();
                grantPoints(userId, reward, "悬赏任务入选奖励");
                if (selected) {
                    long bundleId = ((Number) row.get("bundleId")).longValue();
                    jdbc.update("UPDATE creative_multiview_bundle SET status='approved',review_comment='悬赏投稿审核通过',reviewed_by=?,reviewed_at=NOW(),updated_at=NOW() WHERE id=? AND user_id=? AND status IN ('draft','review')", String.valueOf(adminId), bundleId, userId);
                    jdbc.update("INSERT INTO consumer_bounty_sample_voucher (task_id,submission_id,user_id,bundle_id,material,sample_quantity,shipping_included,expires_at) VALUES (?,?,?,?,'树脂',1,1,DATE_ADD(NOW(),INTERVAL 60 DAY))", row.get("taskId"), submissionId, userId, bundleId);
                }
            }
            return Map.of("id", submissionId, "status", decision, "points", "approved".equals(decision) ? row.get("rewardAmount") : 0, "sampleVoucher", selected && "approved".equals(decision));
        }));
    }

    @GetMapping("/admin/quiz/questions")
    public List<Map<String, Object>> adminQuestions() {
        requireAdmin();
        return jdbc.queryForList("SELECT id,category,question_text question,option_a optionA,option_b optionB,option_c optionC,option_d optionD,correct_option correctOption,explanation,active FROM consumer_points_quiz_question ORDER BY id DESC");
    }

    @PostMapping("/admin/quiz/questions")
    public Map<String, Object> createQuestion(@RequestBody Map<String, Object> body) {
        requireAdmin();
        String question = requiredText(body, "question", 1000);
        String a = requiredText(body, "optionA", 500), b = requiredText(body, "optionB", 500), c = requiredText(body, "optionC", 500), d = requiredText(body, "optionD", 500);
        String correct = requiredText(body, "correctOption", 1).toUpperCase(Locale.ROOT);
        if (!List.of("A", "B", "C", "D").contains(correct)) throw new IllegalArgumentException("正确选项只能是A、B、C或D");
        jdbc.update("INSERT INTO consumer_points_quiz_question (category,question_text,option_a,option_b,option_c,option_d,correct_option,explanation,active) VALUES (?,?,?,?,?,?,?,?,?)",
                body.get("category"), question, a, b, c, d, correct, body.get("explanation"), Boolean.parseBoolean(String.valueOf(body.getOrDefault("active", true))) ? 1 : 0);
        return Map.of("id", jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class));
    }

    @PostMapping("/red-packets/{slot}/claim")
    public Map<String, Object> claimPacket(@PathVariable String slot) {
        long userId = requireConsumerUser();
        if (!PACKET_SLOTS.contains(slot)) throw new IllegalArgumentException("红包雨场次无效");
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalTime now = LocalTime.now(BUSINESS_ZONE);
        int hour = Integer.parseInt(slot);
        if (now.isBefore(LocalTime.of(hour, 0)) || now.isAfter(LocalTime.of(hour, 30))) throw new IllegalStateException("本场红包雨尚未开始或已经结束");
        Map<String, Object> result = Objects.requireNonNull(transactions.execute(status -> {
            List<Map<String, Object>> existing = jdbc.queryForList("SELECT id FROM consumer_points_red_packet_event WHERE event_date=? AND event_slot=? FOR UPDATE", today, slot);
            if (existing.isEmpty()) {
                try {
                    jdbc.update("INSERT INTO consumer_points_red_packet_event (event_date,event_slot) VALUES (?,?)", today, slot);
                } catch (DataIntegrityViolationException ignored) { }
            }
            Map<String, Object> event = jdbc.queryForMap("SELECT id,packet_limit packetLimit,points_min pointsMin,points_max pointsMax,claimed_count claimedCount,status FROM consumer_points_red_packet_event WHERE event_date=? AND event_slot=? FOR UPDATE", today, slot);
            long eventId = ((Number) event.get("id")).longValue();
            if (!"open".equals(String.valueOf(event.get("status")))) throw new IllegalStateException("本场红包雨已结束");
            Integer priorClaim = jdbc.queryForObject("SELECT COUNT(*) FROM consumer_points_red_packet_claim WHERE event_id=? AND user_id=?", Integer.class, eventId, userId);
            if (priorClaim != null && priorClaim > 0) throw new IllegalStateException("你已经领取过本场红包");
            if (((Number) event.get("claimedCount")).intValue() >= ((Number) event.get("packetLimit")).intValue()) throw new IllegalStateException("本场红包已领完");
            int points = ThreadLocalRandom.current().nextInt(((Number) event.get("pointsMin")).intValue(), ((Number) event.get("pointsMax")).intValue() + 1);
            jdbc.update("INSERT INTO consumer_points_red_packet_claim (event_id,user_id,points) VALUES (?,?,?)", eventId, userId, points);
            jdbc.update("UPDATE consumer_points_red_packet_event SET claimed_count=claimed_count+1 WHERE id=?", eventId);
            grantPoints(userId, points, "红包雨 " + slot + ":00");
            Map<String, Object> out = new LinkedHashMap<>(); out.put("points", points); out.put("creditAccount", account(userId));
            return out;
        }));
        return result;
    }

    @PostMapping("/quiz/start")
    public Map<String, Object> startQuiz() {
        long userId = requireConsumerUser();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        return Objects.requireNonNull(transactions.execute(status -> {
            jdbc.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE", userId);
            Map<String, Object> current = currentQuizSession(userId, today);
            if (current != null) return quizSessionPayload(((Number) current.get("id")).longValue(), userId);
            Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM consumer_points_quiz_session WHERE user_id=? AND session_date=?", Integer.class, userId, today);
            int sequence = (count == null ? 0 : count) + 1;
            if (sequence > 3) throw new IllegalStateException("今天的答题次数已用完");
            if (sequence > 1) {
                LocalDateTime last = jdbc.queryForObject("SELECT created_at FROM consumer_points_quiz_session WHERE user_id=? AND session_date=? ORDER BY sequence_no DESC LIMIT 1", LocalDateTime.class, userId, today);
                if (last == null || last.plusHours(2).isAfter(LocalDateTime.now(BUSINESS_ZONE))) throw new IllegalStateException("距离下一轮答题还不到2小时");
            }
            List<Map<String, Object>> questions = jdbc.queryForList("SELECT id FROM consumer_points_quiz_question WHERE active=1 ORDER BY RAND() LIMIT 7");
            if (questions.size() < 7) throw new IllegalStateException("题库正在准备中，请稍后再来");
            jdbc.update("INSERT INTO consumer_points_quiz_session (user_id,session_date,sequence_no) VALUES (?,?,?)", userId, today, sequence);
            long sessionId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            for (int i = 0; i < questions.size(); i++) jdbc.update("INSERT INTO consumer_points_quiz_session_question (session_id,position_no,question_id) VALUES (?,?,?)", sessionId, i, questions.get(i).get("id"));
            return quizSessionPayload(sessionId, userId);
        }));
    }

    @PostMapping("/quiz/{sessionId}/answer")
    public Map<String, Object> answerQuiz(@PathVariable long sessionId, @RequestBody Map<String, Object> body) {
        long userId = requireConsumerUser();
        String selected = String.valueOf(body.getOrDefault("option", "")).trim().toUpperCase(Locale.ROOT);
        if (!List.of("A", "B", "C", "D").contains(selected)) throw new IllegalArgumentException("请选择一个选项");
        return Objects.requireNonNull(transactions.execute(status -> {
            Map<String, Object> session = jdbc.queryForMap("SELECT id,current_position currentPosition,status,IF(wrong_retry_after>NOW(),1,0) retryPending FROM consumer_points_quiz_session WHERE id=? AND user_id=? FOR UPDATE", sessionId, userId);
            if (!"in_progress".equals(String.valueOf(session.get("status")))) throw new IllegalStateException("本轮答题已结束");
            if (((Number) session.get("retryPending")).intValue() > 0) {
                throw new IllegalStateException("答错后请等待3秒再作答");
            }
            int position = ((Number) session.get("currentPosition")).intValue();
            Map<String, Object> q = jdbc.queryForMap("SELECT q.correct_option correctOption FROM consumer_points_quiz_session_question sq JOIN consumer_points_quiz_question q ON q.id=sq.question_id WHERE sq.session_id=? AND sq.position_no=? AND sq.solved=0", sessionId, position);
            boolean correct = selected.equalsIgnoreCase(String.valueOf(q.get("correctOption")));
            jdbc.update("INSERT INTO consumer_points_quiz_attempt (session_id,position_no,selected_option,is_correct) VALUES (?,?,?,?)", sessionId, position, selected, correct ? 1 : 0);
            Map<String, Object> out = new LinkedHashMap<>(); out.put("correct", correct);
            if (!correct) {
                jdbc.update("UPDATE consumer_points_quiz_session SET wrong_retry_after=DATE_ADD(NOW(),INTERVAL 3 SECOND) WHERE id=?", sessionId);
                out.put("retryAfterSeconds", 3); out.put("position", position + 1); return out;
            }
            jdbc.update("UPDATE consumer_points_quiz_session_question SET solved=1 WHERE session_id=? AND position_no=?", sessionId, position);
            int next = position + 1;
            jdbc.update("UPDATE consumer_points_quiz_session SET current_position=?,wrong_retry_after=NULL WHERE id=?", next, sessionId);
            if (next >= 7) {
                grantPoints(userId, 100, "完成积分答题");
                jdbc.update("UPDATE consumer_points_quiz_session SET status='completed',completed_at=NOW() WHERE id=?", sessionId);
                out.put("completed", true); out.put("points", 100); out.put("creditAccount", account(userId));
            } else {
                out.put("completed", false); out.put("position", next + 1); out.put("question", questionFor(sessionId, next));
            }
            return out;
        }));
    }

    private Map<String, Object> checkinState(long userId, LocalDate today) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT last_checkin_date lastDate,streak_days streakDays FROM consumer_points_checkin WHERE user_id=?", userId);
        Map<String, Object> state = rows.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(rows.get(0));
        state.put("checkedIn", !rows.isEmpty() && today.toString().equals(String.valueOf(rows.get(0).get("lastDate"))));
        state.put("streakDays", rows.isEmpty() ? 0 : rows.get(0).get("streakDays"));
        state.put("dailyPoints", 120); state.put("seventhDayBonus", 300);
        return state;
    }

    private List<Map<String, Object>> packetState(long userId, LocalDate today) {
        List<Map<String, Object>> out = new ArrayList<>();
        LocalTime now = LocalTime.now(BUSINESS_ZONE);
        for (String slot : PACKET_SLOTS) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,packet_limit packetLimit,claimed_count claimedCount FROM consumer_points_red_packet_event WHERE event_date=? AND event_slot=?", today, slot);
            int claimed = rows.isEmpty() ? 0 : ((Number) rows.get(0).get("claimedCount")).intValue();
            int limit = rows.isEmpty() ? 100 : ((Number) rows.get(0).get("packetLimit")).intValue();
            int hour = Integer.parseInt(slot);
            boolean claimedByUser = !rows.isEmpty() && jdbc.queryForObject("SELECT COUNT(*) FROM consumer_points_red_packet_claim WHERE event_id=? AND user_id=?", Integer.class, rows.get(0).get("id"), userId) > 0;
            Map<String, Object> item = new LinkedHashMap<>(); item.put("slot", slot); item.put("claimedCount", claimed); item.put("packetLimit", limit); item.put("claimed", claimedByUser);
            item.put("status", now.isBefore(LocalTime.of(hour, 0)) ? "upcoming" : now.isAfter(LocalTime.of(hour, 30)) ? "closed" : claimed >= limit ? "sold_out" : claimedByUser ? "claimed" : "open");
            out.add(item);
        }
        return out;
    }

    private Map<String, Object> quizState(long userId, LocalDate today) {
        Map<String, Object> current = currentQuizSession(userId, today);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM consumer_points_quiz_session WHERE user_id=? AND session_date=?", Integer.class, userId, today);
        Map<String, Object> out = new LinkedHashMap<>(); out.put("sessionsUsed", count == null ? 0 : count); out.put("dailyLimit", 3);
        if (current != null) out.put("session", quizSessionPayload(((Number) current.get("id")).longValue(), userId));
        return out;
    }

    private Map<String, Object> currentQuizSession(long userId, LocalDate today) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,current_position currentPosition,status FROM consumer_points_quiz_session WHERE user_id=? AND session_date=? AND status='in_progress' ORDER BY id DESC LIMIT 1", userId, today);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> quizSessionPayload(long sessionId, long userId) {
        Map<String, Object> session = jdbc.queryForMap("SELECT id,current_position currentPosition,status FROM consumer_points_quiz_session WHERE id=? AND user_id=?", sessionId, userId);
        Map<String, Object> out = new LinkedHashMap<>(session);
        if ("in_progress".equals(String.valueOf(session.get("status")))) out.put("question", questionFor(sessionId, ((Number) session.get("currentPosition")).intValue()));
        return out;
    }

    private Map<String, Object> questionFor(long sessionId, int position) {
        return jdbc.queryForMap("SELECT q.id,q.category,q.question_text question,q.option_a optionA,q.option_b optionB,q.option_c optionC,q.option_d optionD FROM consumer_points_quiz_session_question sq JOIN consumer_points_quiz_question q ON q.id=sq.question_id WHERE sq.session_id=? AND sq.position_no=?", sessionId, position);
    }

    private void grantPoints(long userId, int points, String remark) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT balance FROM consumer_credit_account WHERE user_id=? FOR UPDATE", userId);
        if (rows.isEmpty()) throw new IllegalStateException("积分账户不存在");
        BigDecimal before = new BigDecimal(String.valueOf(rows.get(0).get("balance")));
        BigDecimal amount = BigDecimal.valueOf(points), after = before.add(amount);
        jdbc.update("UPDATE consumer_credit_account SET balance=? WHERE user_id=?", after, userId);
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO consumer_credit_transaction (transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "ACT-" + UUID.randomUUID()); ps.setLong(2, userId); ps.setString(3, "reward"); ps.setBigDecimal(4, amount); ps.setString(5, "income"); ps.setString(6, "completed"); ps.setBigDecimal(7, before); ps.setBigDecimal(8, after); ps.setString(9, remark); ps.setString(10, "system"); return ps;
        }, key);
    }

    private Map<String, Object> account(long userId) {
        return jdbc.queryForMap("SELECT balance FROM consumer_credit_account WHERE user_id=?", userId);
    }

    private long requireAdmin() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        Object value = attrs == null ? null : attrs.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof JwtService.Claims claims)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"admin".equals(claims.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可操作");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='admin'", Integer.class, claims.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员身份已失效");
        return claims.userId();
    }

    private static String requiredText(Map<String, Object> body, String key, int maxLength) {
        String value = String.valueOf(body.getOrDefault(key, "")).trim();
        if (value.isBlank()) throw new IllegalArgumentException("请填写" + key);
        if (value.length() > maxLength) throw new IllegalArgumentException(key + "内容过长");
        return value;
    }

    private static long requiredLong(Map<String, Object> body, String key) {
        try {
            long value = Long.parseLong(String.valueOf(body.get(key)));
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("缺少有效的作品文件");
        }
    }

    private long requireConsumerUser() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        Object value = attrs == null ? null : attrs.getAttribute(JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (!(value instanceof JwtService.Claims claims)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"user".equals(claims.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅C端用户可使用积分活动");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM user WHERE id=? AND role='user' LIMIT 1", claims.userId());
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        return claims.userId();
    }
}
