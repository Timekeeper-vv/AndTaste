package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.example.shixun.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consumer self-service account cancellation. This is deliberately separate
 * from the administrator user-delete endpoint so a client can never choose a
 * different account id.
 */
@RestController
@RequestMapping("/api/users/me")
public class AccountCancellationController {
    private static final String CONFIRMATION = "注销账号";
    private static final List<String> ACTIVE_ORDER_STATUSES = List.of("pending_pay", "paid", "producing", "shipped");
    private static final List<String> ACTIVE_PRODUCTION_STATUSES = List.of("review", "approved", "processing", "producing", "shipped");

    private final JdbcTemplate jdbc;
    private final UserService userService;

    public AccountCancellationController(JdbcTemplate jdbc, UserService userService) {
        this.jdbc = jdbc;
        this.userService = userService;
    }

    @PostMapping("/cancellation")
    @Transactional
    public Map<String, Object> cancel(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE) JwtService.Claims principal,
            @RequestBody(required = false) Map<String, String> body) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!"user".equalsIgnoreCase(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理端账号不能从用户端注销，请联系系统管理员");
        }
        String password = body == null ? null : body.get("password");
        String confirmation = body == null ? null : body.get("confirmation");
        if (!CONFIRMATION.equals(confirmation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入“注销账号”确认操作");
        }

        Long userId = principal.userId();
        Map<String, Object> user = one("SELECT id,username,password,role FROM user WHERE id=? LIMIT 1", userId);
        if (user == null || !"user".equalsIgnoreCase(String.valueOf(user.get("role")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录身份已失效，请重新登录");
        }
        boolean socialAccount = tableExists("wechat_user_binding")
                && count("SELECT COUNT(*) FROM wechat_user_binding WHERE user_id=?", userId) > 0;
        if (!socialAccount) {
            if (password == null || password.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入当前登录密码");
            }
            if (!userService.matchesPassword(password, String.valueOf(user.get("password")))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前密码不正确");
            }
        }

        Long platformUserId = platformUserId(userId);
        if (platformUserId != null && tableExists("order") && count("SELECT COUNT(*) FROM `order` WHERE user_id=? AND order_status IN (?,?,?,?)", platformUserId, ACTIVE_ORDER_STATUSES.toArray()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "还有进行中的订单，请完成收货或退款后再注销");
        }
        if (tableExists("consumer_production_request") && count("SELECT COUNT(*) FROM consumer_production_request WHERE user_id=? AND status IN (?,?,?,?,?)", userId, ACTIVE_PRODUCTION_STATUSES.toArray()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "还有进行中的打样或生产申请，请等待处理完成后再注销");
        }
        if (tableExists("consumer_credit_account")) {
            Map<String, Object> credit = one("SELECT balance,frozen_balance FROM consumer_credit_account WHERE user_id=? LIMIT 1", userId);
            if (credit != null && (decimal(credit.get("balance")) > 0 || decimal(credit.get("frozen_balance")) > 0)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "账户仍有可用或冻结额度，请先使用完或申请处理后再注销");
            }
        }
        if (tableExists("payment_refund") && count("SELECT COUNT(*) FROM payment_refund WHERE user_id=? AND status IN ('pending','review','processing')", userId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "还有未完成的退款申请，请处理完成后再注销");
        }

        // Delete data that has no fulfilment or audit value, while retaining
        // order/payment/credit ledgers in de-identified form.
        if (tableExists("shopping_cart_item") && platformUserId != null) jdbc.update("DELETE FROM shopping_cart_item WHERE user_id=?", platformUserId);
        if (tableExists("favorite_artwork") && platformUserId != null) jdbc.update("DELETE FROM favorite_artwork WHERE user_id=?", platformUserId);
        if (tableExists("product_review") && platformUserId != null) jdbc.update("DELETE FROM product_review WHERE user_id=?", platformUserId);
        if (tableExists("address") && platformUserId != null) jdbc.update("UPDATE address SET receiver_name='已注销用户', phone='已注销', province=NULL, city=NULL, district=NULL, detail='已注销用户地址' WHERE user_id=?", platformUserId);
        if (tableExists("customer_service_message") && tableExists("customer_service_conversation")) {
            jdbc.update("DELETE m FROM customer_service_message m JOIN customer_service_conversation c ON c.id=m.conversation_id WHERE c.user_id=?", userId);
            jdbc.update("DELETE FROM customer_service_conversation WHERE user_id=?", userId);
        }
        if (tableExists("wechat_user_binding")) jdbc.update("DELETE FROM wechat_user_binding WHERE user_id=?", userId);
        if (tableExists("consumer_reward_mission_claim")) jdbc.update("DELETE FROM consumer_reward_mission_claim WHERE user_id=?", userId);
        if (tableExists("consumer_production_request")) jdbc.update("UPDATE consumer_production_request SET recipient_name=NULL,recipient_phone=NULL,recipient_address=NULL,note=NULL,review_comment=NULL WHERE user_id=?", userId);
        if (tableExists("consumer_professional_submission")) removeProfessionalSubmissions(userId);
        if (tableExists("ai_generation_job")) jdbc.update("UPDATE ai_generation_job SET created_by=NULL,prompt=NULL,negative_prompt=NULL,error_message=NULL WHERE created_by=?", userId);
        if (tableExists("digital_asset")) jdbc.update("UPDATE digital_asset SET created_by=NULL,title='已注销用户作品',prompt=NULL,negative_prompt=NULL,metadata_json=NULL,tags=NULL,file_url=NULL,preview_url=NULL,status='archived' WHERE created_by=?", userId);

        if (platformUserId != null && tableExists("payment_wechat_order") && tableExists("order")) {
            jdbc.update("UPDATE payment_wechat_order p JOIN `order` o ON o.order_no=p.order_no SET p.payer_openid=NULL WHERE o.user_id=?", platformUserId);
        }
        if (platformUserId != null && tableExists("platform_user")) {
            jdbc.update("UPDATE platform_user SET username=?,password=?,display_name='已注销用户',email=NULL,phone=NULL,avatar_url=NULL,status='deleted' WHERE id=?", anonymousName(userId), userService.hashPassword(UUID.randomUUID().toString()), platformUserId);
        }
        if (tableExists("user_platform_identity")) jdbc.update("DELETE FROM user_platform_identity WHERE user_id=?", userId);
        jdbc.update("UPDATE user SET username=?,age=NULL,email=NULL,phone=NULL,password=?,role='user',status='deleted' WHERE id=?", anonymousName(userId), userService.hashPassword(UUID.randomUUID().toString()), userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "账号已注销，个人登录身份和可删除资料已清理");
        result.put("retainedData", "订单、支付、退款和额度流水会以匿名标识保留，用于履约、售后、财务和法定审计");
        return result;
    }

    private void removeProfessionalSubmissions(Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT storage_name storageName FROM consumer_professional_submission WHERE user_id=?", userId);
        Path root = creativeAssetRoot().resolve("professional-submissions").normalize();
        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.get("storageName"));
            try {
                Path file = root.resolve(name).normalize();
                if (file.startsWith(root)) Files.deleteIfExists(file);
            } catch (Exception ignored) { }
        }
        jdbc.update("DELETE FROM consumer_professional_submission WHERE user_id=?", userId);
    }

    private Path creativeAssetRoot() {
        String configured = System.getProperty("creative.asset.private-root");
        if (configured == null || configured.isBlank()) configured = System.getenv("CREATIVE_ASSET_PRIVATE_ROOT");
        if (configured == null || configured.isBlank()) configured = "/opt/smart_pig/shixun/src/main/resources/static";
        return Paths.get(configured).toAbsolutePath().normalize();
    }

    private Long platformUserId(Long userId) {
        if (!tableExists("user_platform_identity")) return null;
        Map<String, Object> row = one("SELECT platform_user_id platformUserId FROM user_platform_identity WHERE user_id=? LIMIT 1", userId);
        return row == null || row.get("platformUserId") == null ? null : ((Number) row.get("platformUserId")).longValue();
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private long count(String sql, Object id, Object... args) {
        Object[] values = new Object[args.length + 1];
        values[0] = id;
        System.arraycopy(args, 0, values, 1, args.length);
        Number value = jdbc.queryForObject(sql, values, Number.class);
        return value == null ? 0 : value.longValue();
    }

    private boolean tableExists(String table) {
        Number value = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", new Object[]{table}, Number.class);
        return value != null && value.intValue() > 0;
    }

    private double decimal(Object value) {
        return value == null ? 0 : Double.parseDouble(String.valueOf(value));
    }

    private String anonymousName(Long userId) {
        return "deleted-user-" + userId + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
