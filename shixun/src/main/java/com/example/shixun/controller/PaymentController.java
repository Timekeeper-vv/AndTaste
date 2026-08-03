package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.ClassPathResource;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * C端额度支付：第一期接入微信支付 Native（二维码支付）。
 * 所有价格、积分均在服务端固定，支付成功只能由微信异步通知确认。
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private static final int DEFAULT_ORDER_LIST_LIMIT = 50;
    private static final int MAX_ORDER_LIST_LIMIT = 100;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

    @Value("${payment.wechat.enabled:false}") private boolean wechatEnabled;
    @Value("${payment.wechat.app-id:}") private String wechatAppId;
    @Value("${payment.wechat.mch-id:}") private String wechatMchId;
    @Value("${payment.wechat.serial-no:}") private String wechatSerialNo;
    @Value("${payment.wechat.private-key-path:}") private String wechatPrivateKeyPath;
    @Value("${payment.wechat.api-v3-key:}") private String wechatApiV3Key;
    @Value("${payment.wechat.notify-url:}") private String wechatNotifyUrl;
    @Value("${payment.wechat.platform-public-key-path:}") private String wechatPlatformPublicKeyPath;
    @Value("${payment.wechat.platform-serial-no:}") private String wechatPlatformSerialNo;
    @Value("${payment.manual-qr-url:/payment-collection-qr.jpg}") private String manualWechatQrUrl;

    private static final List<CreditPackage> PACKAGES = List.of(
            new CreditPackage("credit_100", "体验包", "适合少量图片生成和一次3D尝试", 990, new BigDecimal("100")),
            new CreditPackage("credit_500", "创作包", "适合连续做系列文创方案", 3990, new BigDecimal("500")),
            new CreditPackage("credit_1000", "生产预备包", "适合博物馆售卖方向的批量创作", 6990, new BigDecimal("1000"))
    );

    public PaymentController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @PostConstruct
    void initTables() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS payment_order (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(64) NOT NULL UNIQUE, user_id BIGINT NOT NULL, " +
                "product_code VARCHAR(64) NOT NULL, product_name VARCHAR(100) NOT NULL, amount_fen BIGINT NOT NULL, " +
                "credit_amount DECIMAL(12,2) NOT NULL, channel VARCHAR(32) NOT NULL, provider_order_no VARCHAR(128) NULL, " +
                "status VARCHAR(32) NOT NULL, code_url TEXT NULL, provider_response TEXT NULL, paid_at DATETIME NULL, " +
                "expired_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "INDEX idx_payment_user(user_id), INDEX idx_payment_status(status), " +
                "UNIQUE KEY uk_provider_order(channel, provider_order_no)) COMMENT='C端额度支付订单'");
        jdbc.execute("CREATE TABLE IF NOT EXISTS payment_callback_log (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, channel VARCHAR(32) NOT NULL, provider_event_id VARCHAR(128) NULL, " +
                "payload_json LONGTEXT NOT NULL, verified TINYINT NOT NULL DEFAULT 0, processed TINYINT NOT NULL DEFAULT 0, " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY uk_payment_event(channel, provider_event_id)) " +
                "COMMENT='支付回调审计日志'");
    }

    @GetMapping("/packages")
    public Map<String, Object> packages() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (CreditPackage pkg : PACKAGES) {
            items.add(Map.of("code", pkg.code, "name", pkg.name, "description", pkg.description,
                    "amountFen", pkg.amountFen, "amountYuan", fenToYuan(pkg.amountFen), "credits", pkg.credits));
        }
        return Map.of("items", items, "channels", List.of(
                Map.of("code", "manual_wechat_qr", "name", "微信收款码", "enabled", manualWechatQrReady(), "mode", "manual_qr"),
                Map.of("code", "wechat", "name", "微信支付", "enabled", wechatReady(), "mode", "native")
        ));
    }

    @PostMapping("/orders")
    @Transactional
    public Map<String, Object> createOrder(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
                                            @RequestBody Map<String, String> body) throws Exception {
        Long userId = requireConsumer(principal);
        String packageCode = body == null ? "" : nullToEmpty(body.get("packageCode"));
        String channel = body == null ? "" : nullToEmpty(body.get("channel"));
        CreditPackage pkg = PACKAGES.stream().filter(p -> p.code.equals(packageCode)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("充值套餐不存在"));
        if (!"wechat".equals(channel) && !"manual_wechat_qr".equals(channel)) throw new IllegalArgumentException("暂不支持该支付方式");
        if ("wechat".equals(channel) && !wechatReady()) throw new IllegalStateException("微信支付尚未配置，请联系平台管理员完成商户配置");
        if ("manual_wechat_qr".equals(channel) && !manualWechatQrReady()) throw new IllegalStateException("平台尚未配置有效收款码，请联系管理员");

        // 同一用户同时只能保留一笔尚未完成的充值，避免重复扫码、重复人工核验。
        // 同套餐、同通道的重复点击返回原订单，作为创建接口的幂等行为。
        expireOverdueOrders(userId);
        List<Map<String, Object>> activeOrders = jdbc.queryForList(
                "SELECT order_no,product_code,channel,status FROM payment_order " +
                        "WHERE user_id=? AND status IN ('pending','manual_review') " +
                        "AND (expired_at IS NULL OR expired_at>CURRENT_TIMESTAMP) ORDER BY id DESC LIMIT 1 FOR UPDATE",
                userId);
        if (!activeOrders.isEmpty()) {
            Map<String, Object> active = activeOrders.get(0);
            String activeStatus = String.valueOf(active.get("status"));
            String activeOrderNo = String.valueOf(active.get("order_no"));
            if ("pending".equals(activeStatus)
                    && pkg.code.equals(String.valueOf(active.get("product_code")))
                    && channel.equals(String.valueOf(active.get("channel")))) {
                Map<String, Object> reused = orderView(activeOrderNo, userId);
                reused.put("reused", true);
                return reused;
            }
            if ("manual_review".equals(activeStatus)) {
                throw new IllegalStateException("已有待人工核验的充值订单，请等待管理员确认后再创建新订单");
            }
            throw new IllegalStateException("已有待支付订单 " + activeOrderNo + "，请先完成支付或关闭该订单");
        }

        String orderNo = newOrderNo();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        jdbc.update("INSERT INTO payment_order(order_no,user_id,product_code,product_name,amount_fen,credit_amount,channel,status,expired_at) VALUES (?,?,?,?,?,?,?,?,?)",
                orderNo, userId, pkg.code, pkg.name, pkg.amountFen, pkg.credits, channel, "pending", expiresAt);
        if ("manual_wechat_qr".equals(channel)) {
            jdbc.update("UPDATE payment_order SET code_url=?,provider_response=? WHERE order_no=?", manualWechatQrUrl.trim(), "manual receipt QR", orderNo);
            return orderView(orderNo, userId);
        }
        try {
            Map<String, Object> result = createWechatNativeOrder(orderNo, pkg);
            String codeUrl = String.valueOf(result.get("code_url"));
            jdbc.update("UPDATE payment_order SET code_url=?,provider_response=? WHERE order_no=?", codeUrl, mapper.writeValueAsString(result), orderNo);
            return orderView(orderNo, userId);
        } catch (Exception e) {
            jdbc.update("UPDATE payment_order SET status='failed',provider_response=? WHERE order_no=?", safeError(e), orderNo);
            throw e;
        }
    }

    @GetMapping("/orders/{orderNo}")
    public Map<String, Object> order(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
                                     @PathVariable String orderNo) {
        Long userId = requireConsumer(principal);
        return orderView(orderNo, userId);
    }

    /** 当前登录用户的充值订单历史。过期订单会保留在历史中，并以 expired 状态返回。 */
    @GetMapping("/orders")
    public Map<String, Object> orders(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
                                      @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireConsumer(principal);
        expireOverdueOrders(userId);
        int safeLimit = limit == null ? DEFAULT_ORDER_LIST_LIMIT : Math.max(1, Math.min(limit, MAX_ORDER_LIST_LIMIT));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT order_no orderNo,product_code packageCode,product_name packageName,amount_fen amountFen," +
                        "credit_amount credits,channel,status,code_url codeUrl,provider_order_no providerOrderNo," +
                        "paid_at paidAt,expired_at expiredAt,created_at createdAt " +
                        "FROM payment_order WHERE user_id=? ORDER BY id DESC LIMIT ?",
                userId, safeLimit);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) items.add(toOrderView(row));
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM payment_order WHERE user_id=?", Integer.class, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        result.put("limit", safeLimit);
        return result;
    }

    /** 微信支付 APIv3 支付结果通知。 */
    @PostMapping("/wechat/notify")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    public Map<String, String> wechatNotify(
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader(value = "Wechatpay-Serial", required = false) String serial,
            @RequestBody String rawBody) throws Exception {
        if (!verifyWechatNotification(timestamp, nonce, signature, serial, rawBody)) {
            throw new IllegalArgumentException("微信支付回调验签失败");
        }
        JsonNode root = mapper.readTree(rawBody);
        JsonNode resource = root.path("resource");
        String eventId = root.path("id").asText("");
        String plain = decryptWechatResource(resource.path("associated_data").asText(), resource.path("nonce").asText(), resource.path("ciphertext").asText());
        Map<String, Object> payment = mapper.readValue(plain, new TypeReference<Map<String, Object>>() {});
        jdbc.update("INSERT IGNORE INTO payment_callback_log(channel,provider_event_id,payload_json,verified,processed) VALUES ('wechat',?,?,1,0)", eventId, rawBody);
        if (!"SUCCESS".equals(String.valueOf(payment.get("trade_state")))) return Map.of("code", "SUCCESS", "message", "ignored");
        String orderNo = String.valueOf(payment.get("out_trade_no"));
        String transactionId = String.valueOf(payment.get("transaction_id"));
        Map<String, Object> amount = asMap(payment.get("amount"));
        long total = Long.parseLong(String.valueOf(amount.get("total")));
        confirmWechatPayment(orderNo, transactionId, total, eventId);
        return Map.of("code", "SUCCESS", "message", "成功");
    }

    /** 用户扫码付款后提交人工核验；此操作不会自动增加额度。 */
    @PostMapping("/orders/{orderNo}/manual-complete")
    @Transactional
    public Map<String, Object> markManualPaymentComplete(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
                                                          @PathVariable String orderNo) {
        Long userId = requireConsumer(principal);
        expireOverdueOrder(orderNo, userId);
        int changed = jdbc.update("UPDATE payment_order SET status='manual_review' WHERE order_no=? AND user_id=? " +
                        "AND channel='manual_wechat_qr' AND status='pending' " +
                        "AND (expired_at IS NULL OR expired_at>CURRENT_TIMESTAMP)",
                orderNo, userId);
        if (changed == 0) throwUnavailableOrderState(orderNo, userId, "该订单当前无法提交核验");
        return orderView(orderNo, userId);
    }

    /** 管理员人工核实收款后确认到账。 */
    @PostMapping("/admin/orders/{orderNo}/confirm")
    @Transactional
    public Map<String, Object> confirmManualPayment(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
                                                     @PathVariable String orderNo) {
        requireAdmin(principal);
        expireOverdueOrder(orderNo);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM payment_order WHERE order_no=? FOR UPDATE", orderNo);
        if (rows.isEmpty()) throw new IllegalArgumentException("支付订单不存在");
        Map<String, Object> order = rows.get(0);
        if ("paid".equals(order.get("status"))) return orderViewForAdmin(orderNo);
        if ("expired".equals(order.get("status"))) throw new IllegalStateException("订单已过期，不能再确认到账");
        if ("closed".equals(order.get("status"))) throw new IllegalStateException("订单已关闭，不能再确认到账");
        if (!"manual_review".equals(order.get("status"))) throw new IllegalStateException("订单尚未提交人工核验");
        creditConfirmedOrder(order, "manual_qr", "人工核验收款 " + orderNo);
        return orderViewForAdmin(orderNo);
    }

    @GetMapping("/admin/orders")
    public List<Map<String, Object>> adminOrders(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireAdmin(principal);
        expireOverdueOrders();
        return jdbc.queryForList("SELECT p.order_no orderNo,u.username,p.product_name packageName,p.amount_fen amountFen,p.credit_amount credits,p.channel,p.status,p.created_at createdAt,p.paid_at paidAt,p.expired_at expiredAt FROM payment_order p LEFT JOIN user u ON u.id=p.user_id ORDER BY p.id DESC LIMIT 300");
    }

    @PostMapping("/orders/{orderNo}/close")
    @Transactional
    public Map<String, Object> closeOrder(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
                                           @PathVariable String orderNo) {
        Long userId = requireConsumer(principal);
        expireOverdueOrder(orderNo, userId);
        int changed = jdbc.update("UPDATE payment_order SET status='closed' WHERE order_no=? AND user_id=? AND status='pending' " +
                        "AND (expired_at IS NULL OR expired_at>CURRENT_TIMESTAMP)",
                orderNo, userId);
        if (changed == 0) throwUnavailableOrderState(orderNo, userId, "订单无法关闭");
        return orderView(orderNo, userId);
    }

    @Transactional
    public void confirmWechatPayment(String orderNo, String providerOrderNo, long paidAmountFen, String eventId) {
        expireOverdueOrder(orderNo);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM payment_order WHERE order_no=? FOR UPDATE", orderNo);
        if (rows.isEmpty()) throw new IllegalArgumentException("支付订单不存在");
        Map<String, Object> order = rows.get(0);
        if ("paid".equals(order.get("status"))) {
            jdbc.update("UPDATE payment_callback_log SET processed=1 WHERE channel='wechat' AND provider_event_id=?", eventId);
            return;
        }
        long expected = ((Number) order.get("amount_fen")).longValue();
        if (expected != paidAmountFen) throw new IllegalArgumentException("支付金额校验失败");
        // 本地已关单或已过期时，支付回调只做审计，不再自动发放积分，避免过期订单被重复入账。
        if ("expired".equals(order.get("status")) || "closed".equals(order.get("status"))) {
            jdbc.update("UPDATE payment_order SET provider_order_no=?,provider_response=? WHERE order_no=?",
                    providerOrderNo, "支付回调到达时订单已" + ("expired".equals(order.get("status")) ? "过期" : "关闭") + "，需人工核对退款", orderNo);
            jdbc.update("UPDATE payment_callback_log SET processed=1 WHERE channel='wechat' AND provider_event_id=?", eventId);
            return;
        }
        if (!"pending".equals(order.get("status"))) throw new IllegalStateException("订单状态不允许入账");
        creditConfirmedOrder(order, providerOrderNo, "微信支付订单 " + orderNo);
        jdbc.update("UPDATE payment_callback_log SET processed=1 WHERE channel='wechat' AND provider_event_id=?", eventId);
    }

    private void creditConfirmedOrder(Map<String, Object> order, String providerOrderNo, String remark) {
        String orderNo = String.valueOf(order.get("order_no"));
        Long userId = ((Number) order.get("user_id")).longValue();
        BigDecimal credits = new BigDecimal(String.valueOf(order.get("credit_amount")));
        ensureCreditAccount(userId);
        Map<String, Object> account = jdbc.queryForMap("SELECT balance FROM consumer_credit_account WHERE user_id=? FOR UPDATE", userId);
        BigDecimal before = new BigDecimal(String.valueOf(account.get("balance")));
        BigDecimal after = before.add(credits);
        jdbc.update("UPDATE payment_order SET status='paid',provider_order_no=?,paid_at=NOW() WHERE order_no=?", providerOrderNo, orderNo);
        jdbc.update("UPDATE consumer_credit_account SET balance=?,total_recharged=total_recharged+? WHERE user_id=?", after, credits, userId);
        jdbc.update("INSERT INTO consumer_credit_transaction(transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)",
                "PAY-" + orderNo, userId, "payment_recharge", credits, "in", "success", before, after, remark, providerOrderNo);
    }

    private Map<String, Object> orderViewForAdmin(String orderNo) {
        return jdbc.queryForMap("SELECT order_no orderNo,user_id userId,product_name packageName,amount_fen amountFen,credit_amount credits,channel,status,provider_order_no providerOrderNo,paid_at paidAt,expired_at expiredAt,created_at createdAt FROM payment_order WHERE order_no=?", orderNo);
    }

    private Map<String, Object> createWechatNativeOrder(String orderNo, CreditPackage pkg) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appid", wechatAppId);
        payload.put("mchid", wechatMchId);
        payload.put("description", "之间味道 - " + pkg.name);
        payload.put("out_trade_no", orderNo);
        payload.put("notify_url", wechatNotifyUrl);
        payload.put("amount", Map.of("total", pkg.amountFen, "currency", "CNY"));
        String body = mapper.writeValueAsString(payload);
        String path = "/v3/pay/transactions/native";
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String message = "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        String authorization = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + wechatMchId + "\",nonce_str=\"" + nonce + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + wechatSerialNo + "\",signature=\"" + sign(message, merchantPrivateKey()) + "\"";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mch.weixin.qq.com" + path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", authorization)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("微信支付下单失败：HTTP " + response.statusCode() + " " + response.body());
        }
        Map<String, Object> out = mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
        if (out.get("code_url") == null) throw new IllegalStateException("微信支付未返回二维码链接");
        return out;
    }

    private boolean verifyWechatNotification(String timestamp, String nonce, String signature, String serial, String body) throws Exception {
        if (!wechatReady() || blank(wechatPlatformPublicKeyPath)) return false;
        if (!blank(wechatPlatformSerialNo) && !wechatPlatformSerialNo.equals(serial)) return false;
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(wechatPlatformPublicKey());
        verifier.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(signature));
    }

    private String decryptWechatResource(String associatedData, String nonce, String ciphertext) throws Exception {
        byte[] key = wechatApiV3Key.getBytes(StandardCharsets.UTF_8);
        if (key.length != 32) throw new IllegalStateException("微信支付 API v3 Key 必须为32字节");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
    }

    private PrivateKey merchantPrivateKey() throws Exception {
        String pem = Files.readString(Path.of(wechatPrivateKeyPath), StandardCharsets.UTF_8).replaceAll("-----BEGIN [A-Z ]+-----|-----END [A-Z ]+-----|\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    private PublicKey wechatPlatformPublicKey() throws Exception {
        try (var input = Files.newInputStream(Path.of(wechatPlatformPublicKeyPath))) {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            return certificate.getPublicKey();
        }
    }

    private String sign(String message, PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    private Map<String, Object> orderView(String orderNo, Long userId) {
        expireOverdueOrder(orderNo, userId);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT order_no orderNo,product_code packageCode,product_name packageName,amount_fen amountFen,credit_amount credits,channel,status,code_url codeUrl,provider_order_no providerOrderNo,paid_at paidAt,expired_at expiredAt,created_at createdAt FROM payment_order WHERE order_no=? AND user_id=?", orderNo, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("支付订单不存在");
        Map<String, Object> result = toOrderView(rows.get(0));
        if ("expired".equals(result.get("status"))) throw new IllegalStateException("订单已过期，请重新创建充值订单");
        return result;
    }

    /** 懒惰过期：每次涉及订单状态的请求都会先把过期中的订单收敛到 expired。 */
    private void expireOverdueOrders() {
        jdbc.update("UPDATE payment_order SET status='expired' WHERE status IN ('pending','manual_review') " +
                "AND expired_at IS NOT NULL AND expired_at<=CURRENT_TIMESTAMP");
    }

    private void expireOverdueOrders(Long userId) {
        jdbc.update("UPDATE payment_order SET status='expired' WHERE user_id=? AND status IN ('pending','manual_review') " +
                "AND expired_at IS NOT NULL AND expired_at<=CURRENT_TIMESTAMP", userId);
    }

    private void expireOverdueOrder(String orderNo) {
        jdbc.update("UPDATE payment_order SET status='expired' WHERE order_no=? AND status IN ('pending','manual_review') " +
                "AND expired_at IS NOT NULL AND expired_at<=CURRENT_TIMESTAMP", orderNo);
    }

    private void expireOverdueOrder(String orderNo, Long userId) {
        jdbc.update("UPDATE payment_order SET status='expired' WHERE order_no=? AND user_id=? " +
                        "AND status IN ('pending','manual_review') AND expired_at IS NOT NULL " +
                        "AND expired_at<=CURRENT_TIMESTAMP",
                orderNo, userId);
    }

    private void throwUnavailableOrderState(String orderNo, Long userId, String fallback) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status FROM payment_order WHERE order_no=? AND user_id=?", orderNo, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("支付订单不存在");
        String status = String.valueOf(rows.get(0).get("status"));
        if ("expired".equals(status)) throw new IllegalStateException("订单已过期，请重新创建充值订单");
        if ("closed".equals(status)) throw new IllegalStateException("订单已关闭，不能继续操作");
        if ("paid".equals(status)) throw new IllegalStateException("订单已支付，无需重复操作");
        throw new IllegalStateException(fallback);
    }

    private Map<String, Object> toOrderView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        Object amountFen = result.get("amountFen");
        if (amountFen instanceof Number) result.put("amountYuan", fenToYuan(((Number) amountFen).longValue()));
        String status = String.valueOf(result.get("status"));
        result.put("expired", "expired".equals(status));
        result.put("canManualComplete", "pending".equals(status) && "manual_wechat_qr".equals(result.get("channel")));
        result.put("canClose", "pending".equals(status));
        return result;
    }

    private Long requireConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!"user".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅C端用户可发起充值");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user'", Integer.class, principal.userId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        }
        return principal.userId();
    }

    private void requireAdmin(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!"admin".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可执行此操作");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='admin'", Integer.class, principal.userId());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        }
    }

    private void ensureCreditAccount(Long userId) {
        jdbc.update("INSERT IGNORE INTO consumer_credit_account(user_id,balance,frozen_balance,total_recharged,total_consumed) VALUES (?,0,0,0,0)", userId);
    }

    private boolean wechatReady() {
        return wechatEnabled && !blank(wechatAppId) && !blank(wechatMchId) && !blank(wechatSerialNo)
                && !blank(wechatPrivateKeyPath) && !blank(wechatApiV3Key) && !blank(wechatNotifyUrl);
    }

    private boolean manualWechatQrReady() {
        if (blank(manualWechatQrUrl)) return false;
        String value = manualWechatQrUrl.trim();
        if (value.startsWith("https://") || value.startsWith("http://")) return true;
        String classpathPath = value.startsWith("/") ? value.substring(1) : value;
        return new ClassPathResource("static/" + classpathPath).exists();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) { return value instanceof Map ? (Map<String, Object>) value : Map.of(); }
    private String newOrderNo() { return "PAY" + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(java.time.LocalDateTime.now()) + String.format("%04d", new Random().nextInt(10000)); }
    private String fenToYuan(long fen) { return BigDecimal.valueOf(fen, 2).toPlainString(); }
    private String safeError(Exception e) { String value = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); return value.length() > 3500 ? value.substring(0, 3500) : value; }
    private String nullToEmpty(String value) { return value == null ? "" : value.trim(); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static final class CreditPackage {
        final String code, name, description; final long amountFen; final BigDecimal credits;
        CreditPackage(String code, String name, String description, long amountFen, BigDecimal credits) { this.code = code; this.name = name; this.description = description; this.amountFen = amountFen; this.credits = credits; }
    }
}
