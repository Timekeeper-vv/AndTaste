package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;

/**
 * C 端额度充值支付。
 *
 * <p>微信支付的最终到账只能由微信支付 API v3 的已验签异步通知确认；小程序
 * {@code wx.requestPayment} 的成功回调仅代表客户端展示成功，绝不能用于入账。</p>
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private static final int DEFAULT_ORDER_LIST_LIMIT = 50;
    private static final int MAX_ORDER_LIST_LIMIT = 100;
    private static final int CALLBACK_BODY_MAX_BYTES = 64 * 1024;
    private static final int MAX_BILL_DOWNLOAD_BYTES = 100 * 1024 * 1024;
    private static final int MAX_BILL_UNCOMPRESSED_BYTES = 200 * 1024 * 1024;
    private static final int REFERENCE_INSERT_ATTEMPTS = 5;
    private static final Duration WECHAT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration WECHAT_SESSION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration WECHAT_TRANSACTION_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration WECHAT_BILL_TIMEOUT = Duration.ofSeconds(60);
    private static final Set<String> WECHAT_CHANNELS = Set.of("wechat", "wechat_jsapi");
    private static final Set<String> WECHAT_BILL_DOWNLOAD_HOSTS = Set.of("api.mch.weixin.qq.com", "apihk.mch.weixin.qq.com");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final TransactionTemplate transactions;
    // One thread-safe client is shared by all payment requests so TCP/TLS and
    // HTTP/2 connections are reused under concurrent checkout traffic.
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(WECHAT_CONNECT_TIMEOUT)
            .version(HttpClient.Version.HTTP_2)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Value("${payment.wechat.enabled:false}") private boolean wechatEnabled;
    @Value("${payment.wechat.app-id:}") private String wechatAppId;
    @Value("${payment.wechat.mini-app-secret:}") private String wechatMiniAppSecret;
    @Value("${payment.wechat.mch-id:}") private String wechatMchId;
    @Value("${payment.wechat.serial-no:}") private String wechatSerialNo;
    @Value("${payment.wechat.private-key-path:}") private String wechatPrivateKeyPath;
    @Value("${payment.wechat.api-v3-key:}") private String wechatApiV3Key;
    @Value("${payment.wechat.notify-url:}") private String wechatNotifyUrl;
    @Value("${payment.wechat.refund-notify-url:}") private String wechatRefundNotifyUrl;
    @Value("${payment.wechat.platform-public-key-path:}") private String wechatPlatformPublicKeyPath;
    @Value("${payment.wechat.platform-serial-no:}") private String wechatPlatformSerialNo;
    @Value("${payment.wechat.callback-max-age-seconds:300}") private long wechatCallbackMaxAgeSeconds;
    @Value("${payment.wechat.reconcile-enabled:true}") private boolean wechatReconcileEnabled;
    @Value("${payment.wechat.reconcile-limit:40}") private int wechatReconcileLimit;
    @Value("${payment.manual-qr-url:/payment-collection-qr.jpg}") private String manualWechatQrUrl;
    @Value("${payment.manual-qr-enabled:false}") private boolean manualWechatQrEnabled;

    private static final List<CreditPackage> PACKAGES = List.of(
            new CreditPackage("credit_100", "体验包", "适合少量图片生成和一次3D尝试", 990, new BigDecimal("100")),
            new CreditPackage("credit_500", "创作包", "适合连续做系列文创方案", 3990, new BigDecimal("500")),
            new CreditPackage("credit_1000", "生产预备包", "适合博物馆售卖方向的批量创作", 6990, new BigDecimal("1000"))
    );

    public PaymentController(JdbcTemplate jdbc, ObjectMapper mapper, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
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
        // Keep WeChat-specific data in extension tables. This lets a deployed
        // instance upgrade without risky ALTERs on a financial order table.
        jdbc.execute("CREATE TABLE IF NOT EXISTS wechat_user_binding (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, app_id VARCHAR(64) NOT NULL, " +
                "openid VARCHAR(128) NOT NULL, bound_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_wechat_user_app(user_id, app_id), UNIQUE KEY uk_wechat_app_openid(app_id, openid), " +
                "INDEX idx_wechat_binding_user(user_id)) COMMENT='小程序微信OpenID绑定（不得向客户端返回）'");
        jdbc.execute("CREATE TABLE IF NOT EXISTS payment_wechat_order (" +
                "order_no VARCHAR(64) NOT NULL PRIMARY KEY, app_id VARCHAR(64) NOT NULL, mch_id VARCHAR(64) NOT NULL, " +
                "payer_openid VARCHAR(128) NULL, prepay_id VARCHAR(128) NULL, transaction_id VARCHAR(128) NULL, " +
                "provider_trade_state VARCHAR(32) NULL, last_reconciled_at DATETIME NULL, " +
                "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_wechat_transaction(transaction_id)) COMMENT='微信支付订单安全校验元数据'");
        jdbc.execute("CREATE TABLE IF NOT EXISTS payment_refund (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_no VARCHAR(64) NOT NULL UNIQUE, order_no VARCHAR(64) NOT NULL UNIQUE, " +
                "user_id BIGINT NOT NULL, amount_fen BIGINT NOT NULL, credit_amount DECIMAL(12,2) NOT NULL, " +
                "status VARCHAR(32) NOT NULL, reason VARCHAR(240) NULL, provider_refund_id VARCHAR(128) NULL, " +
                "provider_response TEXT NULL, requested_by BIGINT NOT NULL, requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "completed_at DATETIME NULL, updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "UNIQUE KEY uk_provider_refund(provider_refund_id), INDEX idx_refund_status(status)) COMMENT='微信充值退款及额度冻结流水'");
        jdbc.execute("CREATE TABLE IF NOT EXISTS payment_daily_reconciliation (" +
                "bill_date DATE NOT NULL,bill_type VARCHAR(24) NOT NULL,status VARCHAR(32) NOT NULL,download_sha256 CHAR(64) NULL,download_bytes BIGINT NULL," +
                "local_record_count INT NOT NULL DEFAULT 0,provider_record_count INT NULL,matched_record_count INT NULL,discrepancy_count INT NULL," +
                "result_summary TEXT NULL,verified_by BIGINT NULL,verified_at DATETIME NULL,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "PRIMARY KEY(bill_date,bill_type)) COMMENT='微信支付日账单下载与本地比对审计'");
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
                Map.of("code", "wechat", "name", "微信扫码支付", "enabled", wechatPaymentReady(), "mode", "native"),
                Map.of("code", "wechat_jsapi", "name", "微信小程序支付", "enabled", wechatJsapiReady(), "mode", "jsapi")
        ));
    }

    /**
     * Bind a temporary {@code uni.login()} code to the authenticated consumer.
     * OpenID is deliberately never returned to the browser or mini-program.
     */
    @PostMapping("/wechat/bind")
    public Map<String, Object> bindWechatOpenId(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @RequestBody(required = false) Map<String, String> body) throws Exception {
        Long userId = requireConsumer(principal);
        requireWechatJsapiReady();
        String code = body == null ? "" : nullToEmpty(body.get("code"));
        if (code.length() < 6 || code.length() > 512 || code.contains(" ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信登录凭证无效，请重新进入小程序后重试");
        }
        String openId = exchangeMiniProgramCode(code);
        try {
            transactions.execute(status -> {
                bindOpenIdInTransaction(userId, openId);
                return null;
            });
        } catch (DataIntegrityViolationException e) {
            // A concurrent request may have won the unique OpenID binding. Do
            // not ever reassign an OpenID from one platform account to another.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该微信账号已绑定其他平台账号，请使用原账号登录");
        }
        return Map.of("bound", true, "openIdBound", true);
    }

    @PostMapping("/orders")
    public Map<String, Object> createOrder(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @RequestBody(required = false) Map<String, String> body) throws Exception {
        Long userId = requireConsumer(principal);
        String packageCode = body == null ? "" : nullToEmpty(body.get("packageCode"));
        String channel = body == null ? "" : nullToEmpty(body.get("channel"));
        CreditPackage pkg = packageFor(packageCode);
        validateRequestedChannel(channel);

        OrderCreation creation;
        try {
            creation = Objects.requireNonNull(transactions.execute(status -> createOrReusePendingOrder(userId, pkg, channel)));
        } catch (IllegalStateException e) {
            // An unresolved financial order is a deliberate business lock, not
            // a server failure. Returning 409 prevents the client from hiding
            // the reason behind a misleading 500 error.
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
        if (creation.reused) return createdOrderView(creation.orderNo, userId, channel, true);
        if ("manual_wechat_qr".equals(channel)) {
            transactions.execute(status -> {
                jdbc.update("UPDATE payment_order SET code_url=?,provider_response=? WHERE order_no=? AND user_id=?",
                        manualWechatQrUrl.trim(), "manual receipt QR", creation.orderNo, userId);
                return null;
            });
            return createdOrderView(creation.orderNo, userId, channel, false);
        }

        boolean providerSubmissionAttempted = false;
        try {
            if ("wechat_jsapi".equals(channel)) {
                String openId = boundOpenId(userId);
                transactions.execute(status -> {
                    prepareWechatOrderMetadata(creation.orderNo, openId);
                    return null;
                });
                providerSubmissionAttempted = true;
                Map<String, Object> result = createWechatJsapiOrder(creation.orderNo, pkg, openId);
                persistWechatPrepay(creation.orderNo, result);
            } else {
                transactions.execute(status -> {
                    prepareWechatOrderMetadata(creation.orderNo, null);
                    return null;
                });
                providerSubmissionAttempted = true;
                Map<String, Object> result = createWechatNativeOrder(creation.orderNo, pkg);
                persistWechatPrepay(creation.orderNo, result);
            }
            return createdOrderView(creation.orderNo, userId, channel, false);
        } catch (Exception e) {
            String nextStatus = providerSubmissionAttempted && providerResultUnknown(e) ? "payment_exception" : "failed";
            transactions.execute(status -> {
                jdbc.update("UPDATE payment_order SET status=?,provider_response=? WHERE order_no=? AND status='pending'",
                        nextStatus, safeError(e), creation.orderNo);
                return null;
            });
            throw userSafeWechatError(e, providerSubmissionAttempted && providerResultUnknown(e)
                    ? "微信下单结果待核对，请勿重复付款或创建新订单"
                    : "微信支付下单未成功，请稍后重试");
        }
    }

    /**
     * Create a payment order for an approved sample request. Sample fees are
     * priced from the server-side catalog and never from browser input; unlike
     * recharge orders, a successful sample payment must not add user credits.
     */
    @PostMapping("/sample-orders")
    public Map<String, Object> createSampleOrder(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @RequestBody(required = false) Map<String, String> body) throws Exception {
        Long userId = requireConsumer(principal);
        String requestIdText = body == null ? "" : nullToEmpty(body.get("requestId"));
        String channel = body == null ? "" : nullToEmpty(body.get("channel"));
        long requestId;
        try { requestId = Long.parseLong(requestIdText); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "打样申请编号无效"); }
        if (requestId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "打样申请编号无效");
        validateRequestedChannel(channel);

        SampleOrderCreation creation;
        try {
            creation = Objects.requireNonNull(transactions.execute(status -> {
                List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT r.id,r.request_type,r.status,r.sample_product_name,r.sample_fee_yuan,r.sample_payment_status,r.sample_payment_order_no " +
                            "FROM consumer_production_request r WHERE r.id=? AND r.user_id=? FOR UPDATE", requestId, userId);
            if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "打样申请不存在");
            Map<String, Object> request = rows.get(0);
            if (!"sample".equals(String.valueOf(request.get("request_type")))) throw new IllegalStateException("当前申请不是打样申请");
            if (!"approved".equals(String.valueOf(request.get("status")))) throw new IllegalStateException("管理员审核通过后才能支付打样费");
            BigDecimal feeYuan = decimal(request.get("sample_fee_yuan"));
            if (feeYuan.signum() <= 0) throw new IllegalStateException("打样费用尚未配置，请联系管理员");
            String existingOrderNo = nullableText(request.get("sample_payment_order_no"));
            if (!blank(existingOrderNo)) {
                List<Map<String, Object>> existing = jdbc.queryForList("SELECT status,channel FROM payment_order WHERE order_no=? AND user_id=? FOR UPDATE", existingOrderNo, userId);
                if (!existing.isEmpty()) {
                    String existingStatus = String.valueOf(existing.get(0).get("status"));
                    if (Set.of("pending", "manual_review", "paid").contains(existingStatus)) {
                        return new SampleOrderCreation(existingOrderNo, true, feeYuan, String.valueOf(request.get("sample_product_name")), String.valueOf(existing.get(0).get("channel")));
                    }
                }
            }
            long amountFen = feeYuan.movePointRight(2).longValueExact();
            CreditPackage pkg = new CreditPackage("sample_fee_" + requestId,
                    "打样费 · " + nullableText(request.get("sample_product_name")),
                    "审核通过后的作品打样费用", amountFen, BigDecimal.ZERO);
            OrderCreation order = createOrReusePendingOrder(userId, pkg, channel);
            jdbc.update("UPDATE consumer_production_request SET sample_payment_status='pending',sample_payment_order_no=? WHERE id=? AND user_id=?",
                    order.orderNo, requestId, userId);
                return new SampleOrderCreation(order.orderNo, order.reused, feeYuan, nullableText(request.get("sample_product_name")), channel);
            }));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }

        String actualChannel = creation.channel;
        if (creation.reused) return createdOrderView(creation.orderNo, userId, actualChannel, true);
        if ("manual_wechat_qr".equals(actualChannel)) {
            transactions.execute(status -> {
                jdbc.update("UPDATE payment_order SET code_url=?,provider_response=? WHERE order_no=? AND user_id=?",
                        manualWechatQrUrl.trim(), "manual receipt QR for sample fee", creation.orderNo, userId);
                return null;
            });
            return createdOrderView(creation.orderNo, userId, actualChannel, false);
        }

        CreditPackage pkg = new CreditPackage("sample_fee_" + requestId,
                "打样费 · " + creation.productName, "审核通过后的作品打样费用",
                creation.feeYuan.movePointRight(2).longValueExact(), BigDecimal.ZERO);
        boolean providerSubmissionAttempted = false;
        try {
            if ("wechat_jsapi".equals(actualChannel)) {
                String openId = boundOpenId(userId);
                transactions.execute(status -> { prepareWechatOrderMetadata(creation.orderNo, openId); return null; });
                providerSubmissionAttempted = true;
                persistWechatPrepay(creation.orderNo, createWechatJsapiOrder(creation.orderNo, pkg, openId));
            } else {
                transactions.execute(status -> { prepareWechatOrderMetadata(creation.orderNo, null); return null; });
                providerSubmissionAttempted = true;
                persistWechatPrepay(creation.orderNo, createWechatNativeOrder(creation.orderNo, pkg));
            }
            return createdOrderView(creation.orderNo, userId, actualChannel, false);
        } catch (Exception e) {
            String nextStatus = providerSubmissionAttempted && providerResultUnknown(e) ? "payment_exception" : "failed";
            transactions.execute(status -> {
                jdbc.update("UPDATE payment_order SET status=?,provider_response=? WHERE order_no=? AND status='pending'", nextStatus, safeError(e), creation.orderNo);
                if ("failed".equals(nextStatus)) {
                    jdbc.update("UPDATE consumer_production_request SET sample_payment_status='unpaid',sample_payment_order_no=NULL WHERE sample_payment_order_no=? AND sample_payment_status='pending'", creation.orderNo);
                }
                return null;
            });
            throw userSafeWechatError(e, providerSubmissionAttempted && providerResultUnknown(e)
                    ? "微信下单结果待核对，请勿重复付款或创建新订单" : "微信支付下单未成功，请稍后重试");
        }
    }

    @GetMapping("/orders/{orderNo}")
    public Map<String, Object> order(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @PathVariable String orderNo) {
        Long userId = requireConsumer(principal);
        return orderView(orderNo, userId);
    }

    @PostMapping("/orders/{orderNo}/payment-params")
    public Map<String, Object> paymentParams(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @PathVariable String orderNo) throws Exception {
        Long userId = requireConsumer(principal);
        Map<String, Object> result = orderView(orderNo, userId);
        // A pending JSAPI order may be resumed after the user cancels or the
        // client is interrupted. Generate a fresh client signature only for
        // the authenticated owner; never create a second provider trade.
        if (!"wechat_jsapi".equals(String.valueOf(result.get("channel")))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该订单不支持小程序支付");
        }
        if (!"pending".equals(String.valueOf(result.get("status")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单当前不可继续支付");
        }
        addJsapiPaymentParams(result, orderNo, userId);
        return result;
    }

    /**
     * Spring Boot normally serializes a {@link ResponseStatusException} as a
     * generic HTTP label such as "Conflict". Payment actions need the safe,
     * actionable reason so the mini program can guide the user to resume the
     * existing order instead of creating another one.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> paymentRequestError(ResponseStatusException error) {
        String message = blank(error.getReason()) ? "支付请求暂时无法完成，请刷新订单后重试" : error.getReason();
        return ResponseEntity.status(error.getStatus()).body(Map.of(
                "code", "PAYMENT_REQUEST_REJECTED",
                "message", message));
    }

    /** 当前登录用户的充值订单历史。 */
    @GetMapping("/orders")
    public Map<String, Object> orders(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @RequestParam(value = "limit", required = false) Integer limit) {
        Long userId = requireConsumer(principal);
        expireOverdueOrders(userId);
        int safeLimit = limit == null ? DEFAULT_ORDER_LIST_LIMIT : Math.max(1, Math.min(limit, MAX_ORDER_LIST_LIMIT));
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT p.order_no orderNo,p.product_code packageCode,p.product_name packageName,p.amount_fen amountFen," +
                        "p.credit_amount credits,p.channel,p.status,p.code_url codeUrl,p.provider_order_no providerOrderNo," +
                        "p.paid_at paidAt,p.expired_at expiredAt,p.created_at createdAt,r.status refundStatus " +
                        "FROM payment_order p LEFT JOIN payment_refund r ON r.order_no=p.order_no " +
                        "WHERE p.user_id=? ORDER BY p.id DESC LIMIT ?", userId, safeLimit);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : rows) items.add(toOrderView(row));
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM payment_order WHERE user_id=?", Integer.class, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        result.put("limit", safeLimit);
        return result;
    }

    /** 微信支付 API v3 支付结果通知。此接口完全不信任客户端。 */
    @PostMapping("/wechat/notify")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> wechatNotify(
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestBody String rawBody) throws Exception {
        VerifiedWechatNotification notification = verifyAndDecryptNotification(timestamp, nonce, signature, serial, rawBody);
        boolean processed = Objects.requireNonNull(transactions.execute(status -> {
            // The audit row, credit mutation and processed marker are one
            // transaction. A failed handler therefore remains retryable; a
            // duplicate is ignored only after processed=1 is committed.
            if (!claimCallback("wechat_payment", notification.eventId, rawBody)) return false;
            if ("TRANSACTION.SUCCESS".equals(notification.eventType)) processWechatPaymentNotification(notification, rawBody);
            markCallbackProcessed("wechat_payment", notification.eventId);
            return true;
        }));
        return success(processed ? "成功" : "duplicate");
    }

    /** 微信支付 API v3 退款结果通知。 */
    @PostMapping("/wechat/refund-notify")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> wechatRefundNotify(
            @RequestHeader("Wechatpay-Timestamp") String timestamp,
            @RequestHeader("Wechatpay-Nonce") String nonce,
            @RequestHeader("Wechatpay-Signature") String signature,
            @RequestHeader("Wechatpay-Serial") String serial,
            @RequestBody String rawBody) throws Exception {
        VerifiedWechatNotification notification = verifyAndDecryptNotification(timestamp, nonce, signature, serial, rawBody);
        boolean processed = Objects.requireNonNull(transactions.execute(status -> {
            if (!claimCallback("wechat_refund", notification.eventId, rawBody)) return false;
            processWechatRefundNotification(notification, rawBody);
            markCallbackProcessed("wechat_refund", notification.eventId);
            return true;
        }));
        return success(processed ? "成功" : "duplicate");
    }

    /** 用户扫码付款后提交人工核验；此操作不会自动增加额度。 */
    @PostMapping("/orders/{orderNo}/manual-complete")
    public Map<String, Object> markManualPaymentComplete(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @PathVariable String orderNo) {
        Long userId = requireConsumer(principal);
        return Objects.requireNonNull(transactions.execute(status -> {
            expireOverdueOrder(orderNo, userId);
            List<Map<String, Object>> orderRows = jdbc.queryForList("SELECT product_code FROM payment_order WHERE order_no=? AND user_id=? FOR UPDATE", orderNo, userId);
            if (orderRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付订单不存在");
            int changed = jdbc.update("UPDATE payment_order SET status='manual_review' WHERE order_no=? AND user_id=? " +
                            "AND channel='manual_wechat_qr' AND status='pending' " +
                            "AND (expired_at IS NULL OR expired_at>CURRENT_TIMESTAMP)", orderNo, userId);
            if (changed == 0) throwUnavailableOrderState(orderNo, userId, "该订单当前无法提交核验");
            String productCode = String.valueOf(orderRows.get(0).get("product_code"));
            if (productCode.startsWith("sample_fee_")) {
                jdbc.update("UPDATE consumer_production_request SET sample_payment_status='manual_review' WHERE sample_payment_order_no=? AND sample_payment_status='pending'", orderNo);
            }
            return orderView(orderNo, userId);
        }));
    }

    /** 管理员人工核实收款后确认到账。 */
    @PostMapping("/admin/orders/{orderNo}/confirm")
    public Map<String, Object> confirmManualPayment(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @PathVariable String orderNo) {
        requireAdmin(principal);
        return Objects.requireNonNull(transactions.execute(status -> {
            expireOverdueOrder(orderNo);
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM payment_order WHERE order_no=? FOR UPDATE", orderNo);
            if (rows.isEmpty()) throw new IllegalArgumentException("支付订单不存在");
            Map<String, Object> order = rows.get(0);
            if ("paid".equals(order.get("status"))) return orderViewForAdmin(orderNo);
            if (Set.of("expired", "closed", "payment_exception").contains(String.valueOf(order.get("status")))) {
                throw new IllegalStateException("订单当前状态不能人工确认到账");
            }
            if (!"manual_review".equals(order.get("status"))) throw new IllegalStateException("订单尚未提交人工核验");
            creditConfirmedOrder(order, "manual_qr:" + orderNo, "人工核验收款 " + orderNo);
            return orderViewForAdmin(orderNo);
        }));
    }

    /** 管理员请求原路退款。只允许未被消费的充值额度进入退款流程。 */
    @PostMapping("/admin/orders/{orderNo}/refund")
    public Map<String, Object> requestRefund(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @PathVariable String orderNo,
            @RequestBody(required = false) Map<String, String> body) {
        requireAdmin(principal);
        requireWechatRefundReady();
        String reason = body == null ? "" : limit(nullToEmpty(body.get("reason")), 240);
        RefundPreparation preparation = Objects.requireNonNull(transactions.execute(status -> prepareRefund(orderNo, principal.userId(), reason)));
        return submitRefund(preparation);
    }

    /**
     * Settles the only safe exit for a verified late/closed payment: it first
     * re-queries WeChat, then refunds the money without touching credits that
     * were never granted to the user.
     */
    @PostMapping("/admin/orders/{orderNo}/exception-refund")
    public Map<String, Object> refundUncreditedException(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @PathVariable String orderNo,
            @RequestBody(required = false) Map<String, String> body) {
        requireAdmin(principal);
        requireWechatRefundReady();
        String reason = body == null ? "" : limit(nullToEmpty(body.get("reason")), 240);
        try {
            reconcileWechatPayment(orderNo);
        } catch (Exception e) {
            throw userSafeWechatError(e, "无法确认异常订单的微信实付状态，不能发起退款");
        }
        RefundPreparation preparation = Objects.requireNonNull(transactions.execute(status -> prepareUncreditedExceptionRefund(orderNo, principal.userId(), reason)));
        return submitRefund(preparation);
    }

    /** 管理员按微信官方订单查询接口主动对账单笔充值/退款。 */
    @PostMapping("/admin/orders/{orderNo}/reconcile")
    public Map<String, Object> reconcileOrder(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @PathVariable String orderNo) {
        requireAdmin(principal);
        requireSafeOrderNo(orderNo);
        try {
            reconcileWechatPayment(orderNo);
            reconcileRefundForOrder(orderNo);
        } catch (Exception e) {
            throw userSafeWechatError(e, "微信对账暂不可用，请勿重复支付或退款");
        }
        return orderViewForAdmin(orderNo);
    }

    /**
     * Downloads the signed daily trade/refund/fund-flow bill URLs issued by
     * WeChat, records a SHA-256 evidence hash, and compares local references.
     * It intentionally never auto-adjusts money or credits from a CSV result;
     * any mismatch remains an administrator-visible exception for investigation.
     */
    @PostMapping("/admin/reconciliation/daily")
    public List<Map<String, Object>> reconcileDailyBills(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @RequestParam String billDate) {
        requireAdmin(principal);
        requireWechatPaymentReady();
        LocalDate date;
        try { date = LocalDate.parse(billDate); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账单日期必须是 yyyy-MM-dd"); }
        if (date.isAfter(LocalDate.now()) || date.isBefore(LocalDate.now().minusDays(365))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账单日期必须在过去365天内");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (String billType : List.of("trade", "refund", "fundflow")) {
            try { result.add(downloadAndReconcileDailyBill(date, billType, principal.userId())); }
            catch (Exception e) { result.add(recordDailyBillFailure(date, billType, principal.userId(), safeError(e))); }
        }
        return result;
    }

    @GetMapping("/admin/reconciliation/daily")
    public List<Map<String, Object>> dailyReconciliationHistory(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @RequestParam(required = false) String billDate) {
        requireAdmin(principal);
        if (blank(billDate)) return jdbc.queryForList("SELECT bill_date billDate,bill_type billType,status,download_sha256 downloadSha256,download_bytes downloadBytes," +
                "local_record_count localRecordCount,provider_record_count providerRecordCount,matched_record_count matchedRecordCount,discrepancy_count discrepancyCount," +
                "result_summary resultSummary,verified_at verifiedAt FROM payment_daily_reconciliation ORDER BY bill_date DESC,bill_type LIMIT 90");
        try { LocalDate.parse(billDate); } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "账单日期必须是 yyyy-MM-dd"); }
        return jdbc.queryForList("SELECT bill_date billDate,bill_type billType,status,download_sha256 downloadSha256,download_bytes downloadBytes," +
                "local_record_count localRecordCount,provider_record_count providerRecordCount,matched_record_count matchedRecordCount,discrepancy_count discrepancyCount," +
                "result_summary resultSummary,verified_at verifiedAt FROM payment_daily_reconciliation WHERE bill_date=? ORDER BY bill_type", LocalDate.parse(billDate));
    }

    @GetMapping("/admin/exceptions")
    public List<Map<String, Object>> paymentExceptions(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireAdmin(principal);
        return jdbc.queryForList("SELECT p.order_no orderNo,p.user_id userId,p.channel,p.status,p.amount_fen amountFen,p.credit_amount credits," +
                "p.provider_order_no providerOrderNo,p.updated_at updatedAt,r.refund_no refundNo,r.status refundStatus,r.provider_refund_id providerRefundId " +
                "FROM payment_order p LEFT JOIN payment_refund r ON r.order_no=p.order_no " +
                "WHERE p.status IN ('payment_exception','refund_unknown','refund_exception') " +
                "OR r.status IN ('refund_unknown','refund_exception') ORDER BY p.updated_at DESC LIMIT 300");
    }

    @GetMapping("/admin/orders")
    public List<Map<String, Object>> adminOrders(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireAdmin(principal);
        expireOverdueOrders();
        return jdbc.queryForList("SELECT p.order_no orderNo,u.username,p.product_name packageName,p.amount_fen amountFen,p.credit_amount credits,p.channel,p.status," +
                "p.provider_order_no providerOrderNo,p.created_at createdAt,p.paid_at paidAt,p.expired_at expiredAt,r.refund_no refundNo,r.status refundStatus " +
                "FROM payment_order p LEFT JOIN user u ON u.id=p.user_id LEFT JOIN payment_refund r ON r.order_no=p.order_no ORDER BY p.id DESC LIMIT 300");
    }

    /**
     * Safe operational readiness check. It intentionally exposes only booleans
     * and missing configuration labels, never credentials, certificate data or
     * payment provider responses.
     */
    @GetMapping("/admin/configuration")
    public Map<String, Object> adminPaymentConfiguration(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireAdmin(principal);
        List<String> missing = new ArrayList<>();
        if (!wechatEnabled) missing.add("PAYMENT_WECHAT_ENABLED=true");
        if (blank(wechatAppId)) missing.add("小程序 AppID");
        if (blank(wechatMiniAppSecret)) missing.add("小程序 AppSecret");
        if (blank(wechatMchId)) missing.add("微信支付商户号");
        if (blank(wechatSerialNo)) missing.add("商户 API 证书序列号");
        if (blank(wechatPrivateKeyPath)) missing.add("商户私钥路径");
        else if (!readableFile(wechatPrivateKeyPath)) missing.add("可读取的商户私钥文件");
        else if (!validMerchantPrivateKey()) missing.add("有效的商户私钥文件");
        if (wechatApiV3Key == null || wechatApiV3Key.getBytes(StandardCharsets.UTF_8).length != 32) missing.add("32 字节 API v3 密钥");
        if (blank(wechatNotifyUrl) || !wechatNotifyUrl.startsWith("https://")) missing.add("HTTPS 支付回调地址");
        if (blank(wechatRefundNotifyUrl) || !wechatRefundNotifyUrl.startsWith("https://")) missing.add("HTTPS 退款回调地址");
        if (blank(wechatPlatformPublicKeyPath)) missing.add("微信支付公钥路径");
        else if (!readableFile(wechatPlatformPublicKeyPath)) missing.add("可读取的微信支付公钥文件");
        else if (!validWechatPlatformPublicKey()) missing.add("有效的微信支付公钥文件");
        if (blank(wechatPlatformSerialNo)) missing.add("微信支付公钥 ID");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("officialPaymentReady", wechatPaymentReady());
        result.put("miniappPaymentReady", wechatJsapiReady());
        result.put("manualQrEnabled", manualWechatQrReady());
        result.put("privateKeyReadable", !blank(wechatPrivateKeyPath) && readableFile(wechatPrivateKeyPath));
        result.put("privateKeyValid", validMerchantPrivateKey());
        result.put("platformPublicKeyReadable", !blank(wechatPlatformPublicKeyPath) && readableFile(wechatPlatformPublicKeyPath));
        result.put("platformPublicKeyValid", validWechatPlatformPublicKey());
        result.put("notifyUrlConfigured", !blank(wechatNotifyUrl) && wechatNotifyUrl.startsWith("https://"));
        result.put("refundNotifyUrlConfigured", !blank(wechatRefundNotifyUrl) && wechatRefundNotifyUrl.startsWith("https://"));
        result.put("missing", missing);
        return result;
    }

    @PostMapping("/orders/{orderNo}/close")
    public Map<String, Object> closeOrder(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @PathVariable String orderNo) {
        Long userId = requireConsumer(principal);
        Map<String, Object> current = findOrderForUser(orderNo, userId);
        String channel = String.valueOf(current.get("channel"));
        if (WECHAT_CHANNELS.contains(channel) && "pending".equals(String.valueOf(current.get("status")))) {
            try {
                closeWechatTrade(orderNo);
            } catch (WechatApiException e) {
                // ORDERPAID is a normal race: query the official state instead of
                // closing locally and accidentally hiding a successful payment.
                try {
                    reconcileWechatPayment(orderNo);
                } catch (Exception reconcileError) {
                    throw userSafeWechatError(reconcileError, "微信关单结果未知，请稍后刷新订单状态");
                }
                Map<String, Object> afterReconcile = findOrderForUser(orderNo, userId);
                if ("pending".equals(String.valueOf(afterReconcile.get("status")))) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信关单结果未知，请稍后刷新订单状态");
                }
            } catch (Exception e) {
                throw userSafeWechatError(e, "微信关单暂不可用，请稍后刷新订单状态");
            }
        }
        return Objects.requireNonNull(transactions.execute(status -> {
            expireOverdueOrder(orderNo, userId);
            int changed = jdbc.update("UPDATE payment_order SET status='closed' WHERE order_no=? AND user_id=? AND status='pending'", orderNo, userId);
            if (changed == 0) throwUnavailableOrderState(orderNo, userId, "订单无法关闭");
            return orderView(orderNo, userId);
        }));
    }

    /**
     * Periodic reconciliation is intentionally fail-closed. It never trusts a
     * mini-program result and never releases a refund reservation on a network
     * failure. Set PAYMENT_WECHAT_RECONCILE_ENABLED=false only during planned
     * maintenance, then manually reconcile all exceptions before reopening.
     */
    @Scheduled(fixedDelayString = "${payment.wechat.reconcile-delay-ms:300000}", initialDelayString = "${payment.wechat.reconcile-initial-delay-ms:45000}")
    public void reconcileWechatOrdersOnSchedule() {
        if (!wechatReconcileEnabled || !wechatPaymentReady()) return;
        int limit = Math.max(1, Math.min(wechatReconcileLimit, 100));
        List<String> paymentOrders = jdbc.query("SELECT order_no FROM payment_order WHERE channel IN ('wechat','wechat_jsapi') " +
                        "AND status IN ('pending','payment_exception') ORDER BY updated_at ASC LIMIT ?",
                (rs, rowNum) -> rs.getString(1), limit);
        for (String orderNo : paymentOrders) {
            try {
                reconcileWechatPayment(orderNo);
                expireWechatTradeIfDue(orderNo);
            } catch (Exception error) { log.warn("微信支付订单对账失败 orderNo={}", orderNo, error); /* retry next run; never silently expire */ }
        }
        List<String> refundOrders = jdbc.query("SELECT order_no FROM payment_refund WHERE status IN ('refund_requested','refund_processing','refund_unknown') " +
                        "ORDER BY updated_at ASC LIMIT ?", (rs, rowNum) -> rs.getString(1), limit);
        for (String orderNo : refundOrders) {
            try { reconcileRefundForOrder(orderNo); } catch (Exception error) { log.warn("微信退款对账失败 orderNo={}", orderNo, error); /* retry next run */ }
        }
    }

    @Scheduled(cron = "${payment.wechat.daily-reconcile-cron:0 30 10 * * *}", zone = "Asia/Shanghai")
    public void reconcilePreviousDayBillsOnSchedule() {
        if (!wechatReconcileEnabled || !wechatPaymentReady()) return;
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        for (String billType : List.of("trade", "refund", "fundflow")) {
            try { downloadAndReconcileDailyBill(date, billType, null); }
            catch (Exception error) {
                log.warn("微信日账单下载失败 date={} type={}", date, billType, error);
                try { recordDailyBillFailure(date, billType, null, safeError(error)); } catch (Exception ignored) { log.warn("微信日账单失败记录写入失败 date={} type={}", date, billType); }
            }
        }
    }

    /** Retry recent failed bill downloads. A delayed generation after the
     * morning task must not remain failed forever just because the next
     * primary run has moved on to a newer billing date. */
    @Scheduled(cron = "${payment.wechat.daily-reconcile-retry-cron:0 30 14 * * *}", zone = "Asia/Shanghai")
    public void retryFailedPreviousDayBillsOnSchedule() {
        if (!wechatReconcileEnabled || !wechatPaymentReady()) return;
        LocalDate newestDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1);
        LocalDate oldestDate = newestDate.minusDays(6);
        List<DailyBillRetry> failedBills = jdbc.query(
                "SELECT bill_date,bill_type FROM payment_daily_reconciliation WHERE bill_date BETWEEN ? AND ? AND status='download_failed' ORDER BY bill_date,bill_type",
                (rs, rowNum) -> new DailyBillRetry(rs.getDate(1).toLocalDate(), rs.getString(2)), oldestDate, newestDate);
        for (DailyBillRetry failed : failedBills) {
            Integer stillFailed = jdbc.queryForObject("SELECT COUNT(*) FROM payment_daily_reconciliation WHERE bill_date=? AND bill_type=? AND status='download_failed'",
                    Integer.class, failed.billDate(), failed.billType());
            if (stillFailed == null || stillFailed == 0) continue;
            try { downloadAndReconcileDailyBill(failed.billDate(), failed.billType(), null); }
            catch (Exception error) {
                log.warn("微信失败账单重试仍失败 date={} type={}", failed.billDate(), failed.billType(), error);
                try { recordDailyBillFailure(failed.billDate(), failed.billType(), null, safeError(error)); } catch (Exception ignored) { }
            }
        }
    }

    private OrderCreation createOrReusePendingOrder(Long userId, CreditPackage pkg, String channel) {
        expireOverdueOrders(userId);
        // Lock the canonical account even when it has no prior order. A query
        // against an empty order set cannot serialize two first-click requests.
        jdbc.queryForList("SELECT id FROM user WHERE id=? FOR UPDATE", userId);
        List<Map<String, Object>> activeOrders = jdbc.queryForList(
                "SELECT order_no,product_code,channel,status FROM payment_order " +
                        "WHERE user_id=? AND status IN ('pending','manual_review','payment_exception','refund_requested','refund_processing','refund_unknown','refund_exception') " +
                        "ORDER BY id DESC LIMIT 1 FOR UPDATE", userId);
        if (!activeOrders.isEmpty()) {
            Map<String, Object> active = activeOrders.get(0);
            String activeStatus = String.valueOf(active.get("status"));
            String activeOrderNo = String.valueOf(active.get("order_no"));
            if ("pending".equals(activeStatus) && pkg.code.equals(String.valueOf(active.get("product_code")))
                    && channel.equals(String.valueOf(active.get("channel")))) {
                return new OrderCreation(activeOrderNo, true);
            }
            if ("manual_review".equals(activeStatus)) {
                throw new IllegalStateException("已有待人工核验的充值订单，请等待管理员确认后再创建新订单");
            }
            if (Set.of("payment_exception", "refund_requested", "refund_processing", "refund_unknown", "refund_exception").contains(activeStatus)) {
                throw new IllegalStateException("已有待微信核对或退款处理的订单 " + activeOrderNo + "，请勿重复付款或创建新订单");
            }
            throw new IllegalStateException("已有待支付订单 " + activeOrderNo + "，请先完成支付或关闭该订单");
        }
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(15);
        for (int attempt = 1; attempt <= REFERENCE_INSERT_ATTEMPTS; attempt++) {
            String orderNo = newOrderNo();
            try {
                jdbc.update("INSERT INTO payment_order(order_no,user_id,product_code,product_name,amount_fen,credit_amount,channel,status,expired_at) VALUES (?,?,?,?,?,?,?,?,?)",
                        orderNo, userId, pkg.code, pkg.name, pkg.amountFen, pkg.credits, channel, "pending", expiresAt);
                return new OrderCreation(orderNo, false);
            } catch (DuplicateKeyException collision) {
                if (attempt == REFERENCE_INSERT_ATTEMPTS) {
                    throw new IllegalStateException("支付订单创建繁忙，请稍后重试", collision);
                }
                log.warn("支付订单号冲突，正在重试 attempt={}", attempt);
            }
        }
        throw new IllegalStateException("支付订单创建繁忙，请稍后重试");
    }

    private Map<String, Object> createdOrderView(String orderNo, Long userId, String channel, boolean reused) throws Exception {
        Map<String, Object> result = orderView(orderNo, userId);
        if ("wechat_jsapi".equals(channel)) addJsapiPaymentParams(result, orderNo, userId);
        if (reused) result.put("reused", true);
        return result;
    }

    private void validateRequestedChannel(String channel) {
        if (!Set.of("wechat", "wechat_jsapi", "manual_wechat_qr").contains(channel)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持该支付方式");
        }
        if ("manual_wechat_qr".equals(channel) && !manualWechatQrReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "平台尚未配置有效收款码，请联系管理员");
        }
        if ("wechat".equals(channel)) requireWechatPaymentReady();
        if ("wechat_jsapi".equals(channel)) requireWechatJsapiReady();
    }

    private void bindOpenIdInTransaction(Long userId, String openId) {
        List<Map<String, Object>> ownerRows = jdbc.queryForList(
                "SELECT user_id FROM wechat_user_binding WHERE app_id=? AND openid=? FOR UPDATE", wechatAppId, openId);
        if (!ownerRows.isEmpty() && ((Number) ownerRows.get(0).get("user_id")).longValue() != userId) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该微信账号已绑定其他平台账号，请使用原账号登录");
        }
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM payment_order p JOIN payment_wechat_order w ON w.order_no=p.order_no " +
                "WHERE p.user_id=? AND p.channel='wechat_jsapi' AND p.status='pending' AND w.payer_openid<>?", Integer.class, userId, openId);
        if (pending != null && pending > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "请先完成或关闭当前待支付订单后再更换微信账号");
        int changed = jdbc.update("UPDATE wechat_user_binding SET openid=?,bound_at=NOW() WHERE user_id=? AND app_id=?", openId, userId, wechatAppId);
        if (changed == 0) jdbc.update("INSERT INTO wechat_user_binding(user_id,app_id,openid) VALUES (?,?,?)", userId, wechatAppId, openId);
    }

    private String exchangeMiniProgramCode(String code) throws Exception {
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + encode(wechatAppId)
                + "&secret=" + encode(wechatMiniAppSecret) + "&js_code=" + encode(code) + "&grant_type=authorization_code";
        HttpResponse<String> response = http.send(httpRequest(URI.create(url), WECHAT_SESSION_TIMEOUT).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IllegalStateException("微信登录凭证校验服务不可用");
        JsonNode root = mapper.readTree(response.body());
        if (root.path("errcode").asInt(0) != 0 || blank(root.path("openid").asText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信登录凭证已失效，请重新进入小程序后重试");
        }
        String openId = root.path("openid").asText().trim();
        if (openId.length() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信账号标识无效");
        return openId;
    }

    private String boundOpenId(Long userId) {
        List<String> rows = jdbc.query("SELECT openid FROM wechat_user_binding WHERE user_id=? AND app_id=? LIMIT 1", (rs, rowNum) -> rs.getString(1), userId, wechatAppId);
        if (rows.isEmpty() || blank(rows.get(0))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先调用微信登录绑定，再发起小程序支付");
        }
        return rows.get(0);
    }

    private void prepareWechatOrderMetadata(String orderNo, String openId) {
        jdbc.update("INSERT INTO payment_wechat_order(order_no,app_id,mch_id,payer_openid,provider_trade_state) VALUES (?,?,?,?,?)",
                orderNo, wechatAppId, wechatMchId, blank(openId) ? null : openId, "CREATED");
    }

    private void persistWechatPrepay(String orderNo, Map<String, Object> result) {
        String prepayId = nullableText(result.get("prepay_id"));
        String codeUrl = nullableText(result.get("code_url"));
        if (blank(prepayId) && blank(codeUrl)) throw new IllegalStateException("微信支付未返回有效预支付凭证");
        transactions.execute(status -> {
            int metadataChanged = jdbc.update("UPDATE payment_wechat_order SET prepay_id=?,provider_trade_state='NOTPAY' WHERE order_no=? AND app_id=? AND mch_id=?",
                    blank(prepayId) ? null : prepayId, orderNo, wechatAppId, wechatMchId);
            if (metadataChanged != 1) throw new IllegalStateException("微信支付订单元数据不存在或配置不匹配");
            jdbc.update("UPDATE payment_order SET code_url=?,provider_response=? WHERE order_no=? AND status='pending'",
                    blank(codeUrl) ? null : codeUrl, compactJson(result), orderNo);
            return null;
        });
    }

    private void addJsapiPaymentParams(Map<String, Object> order, String orderNo, Long userId) throws Exception {
        if (!"pending".equals(String.valueOf(order.get("status")))) return;
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT app_id,payer_openid,prepay_id FROM payment_wechat_order WHERE order_no=?", orderNo);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "支付凭证正在生成，请稍后重试");
        Map<String, Object> row = rows.get(0);
        if (!wechatAppId.equals(nullableText(row.get("app_id"))) || blank(nullableText(row.get("payer_openid")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前微信支付配置已变更，请关闭订单后重新创建");
        }
        String prepayId = nullableText(row.get("prepay_id"));
        if (blank(prepayId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "支付凭证正在生成，请稍后重试");
        order.put("paymentParams", buildJsapiPaymentParams(prepayId));
    }

    private Map<String, Object> buildJsapiPaymentParams(String prepayId) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonceStr = randomNonce();
        String packageValue = "prepay_id=" + prepayId;
        String message = wechatAppId + "\n" + timestamp + "\n" + nonceStr + "\n" + packageValue + "\n";
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("timeStamp", timestamp);
        params.put("nonceStr", nonceStr);
        params.put("package", packageValue);
        params.put("signType", "RSA");
        params.put("paySign", sign(message, merchantPrivateKey()));
        return params;
    }

    private Map<String, Object> createWechatNativeOrder(String orderNo, CreditPackage pkg) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appid", wechatAppId);
        payload.put("mchid", wechatMchId);
        payload.put("description", "之间味道 - " + pkg.name);
        payload.put("out_trade_no", orderNo);
        payload.put("notify_url", wechatNotifyUrl);
        payload.put("time_expire", wechatTimeExpire(orderNo));
        payload.put("amount", Map.of("total", pkg.amountFen, "currency", "CNY"));
        return wechatJsonRequest("POST", "/v3/pay/transactions/native", payload);
    }

    private Map<String, Object> createWechatJsapiOrder(String orderNo, CreditPackage pkg, String openId) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("appid", wechatAppId);
        payload.put("mchid", wechatMchId);
        payload.put("description", "之间味道 - " + pkg.name);
        payload.put("out_trade_no", orderNo);
        payload.put("notify_url", wechatNotifyUrl);
        payload.put("time_expire", wechatTimeExpire(orderNo));
        payload.put("amount", Map.of("total", pkg.amountFen, "currency", "CNY"));
        payload.put("payer", Map.of("openid", openId));
        Map<String, Object> result = wechatJsonRequest("POST", "/v3/pay/transactions/jsapi", payload);
        if (blank(nullableText(result.get("prepay_id")))) throw new IllegalStateException("微信支付未返回预支付凭证");
        return result;
    }

    private void processWechatPaymentNotification(VerifiedWechatNotification notification, String rawBody) {
        Map<String, Object> payment = notification.resource;
        String orderNo = requiredText(payment, "out_trade_no");
        String transactionId = requiredText(payment, "transaction_id");
        String tradeState = requiredText(payment, "trade_state");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM payment_order WHERE order_no=? FOR UPDATE", orderNo);
        if (rows.isEmpty()) return; // verified but not created by this platform: audit only
        Map<String, Object> order = rows.get(0);
        if (!WECHAT_CHANNELS.contains(String.valueOf(order.get("channel")))) {
            markPaymentException(orderNo, "微信回调对应了非微信订单");
            return;
        }
        List<Map<String, Object>> metaRows = jdbc.queryForList("SELECT * FROM payment_wechat_order WHERE order_no=? FOR UPDATE", orderNo);
        if (metaRows.isEmpty()) {
            markPaymentException(orderNo, "微信订单缺少安全元数据，需人工核对");
            return;
        }
        Map<String, Object> meta = metaRows.get(0);
        if (!wechatAppId.equals(requiredText(payment, "appid")) || !wechatMchId.equals(requiredText(payment, "mchid"))
                || !wechatAppId.equals(String.valueOf(meta.get("app_id"))) || !wechatMchId.equals(String.valueOf(meta.get("mch_id")))) {
            markPaymentException(orderNo, "微信回调的商户或应用标识不匹配");
            return;
        }
        if (!"SUCCESS".equals(tradeState)) {
            markPaymentException(orderNo, "微信支付成功事件中的交易状态异常：" + limit(tradeState, 32));
            return;
        }
        Map<String, Object> amount = asMap(payment.get("amount"));
        if (!"CNY".equals(requiredText(amount, "currency")) || toLong(required(amount, "total")) != toLong(order.get("amount_fen"))) {
            markPaymentException(orderNo, "微信回调金额或币种不匹配");
            return;
        }
        String expectedOpenId = nullableText(meta.get("payer_openid"));
        String payerOpenId = nullableText(asMap(payment.get("payer")).get("openid"));
        if (!blank(expectedOpenId) && !expectedOpenId.equals(payerOpenId)) {
            markPaymentException(orderNo, "微信付款人不匹配");
            return;
        }
        String currentProviderOrderNo = nullableText(order.get("provider_order_no"));
        if (!blank(currentProviderOrderNo) && !currentProviderOrderNo.equals(transactionId)) {
            markPaymentException(orderNo, "同一订单收到不一致的微信交易号");
            return;
        }
        List<Map<String, Object>> transactionConflicts = jdbc.queryForList(
                "SELECT order_no FROM payment_wechat_order WHERE transaction_id=? AND order_no<>? FOR UPDATE", transactionId, orderNo);
        if (!transactionConflicts.isEmpty()) {
            markPaymentException(orderNo, "微信交易号已绑定其他本地订单，需人工核对");
            return;
        }
        List<Map<String, Object>> paymentConflicts = jdbc.queryForList(
                "SELECT order_no FROM payment_order WHERE provider_order_no=? AND order_no<>? FOR UPDATE", transactionId, orderNo);
        if (!paymentConflicts.isEmpty()) {
            markPaymentException(orderNo, "微信交易号已用于其他本地到账记录，需人工核对");
            return;
        }
        String orderStatus = String.valueOf(order.get("status"));
        try {
            jdbc.update("UPDATE payment_wechat_order SET transaction_id=?,provider_trade_state='SUCCESS',last_reconciled_at=NOW() WHERE order_no=?",
                    transactionId, orderNo);
        } catch (DataIntegrityViolationException conflict) {
            // Two callbacks for different orders can race before either row
            // holds the unique transaction id. Preserve the order as an
            // exception instead of throwing forever and hiding the conflict.
            markPaymentException(orderNo, "微信交易号并发绑定冲突，需人工核对");
            return;
        }
        if ("paid".equals(orderStatus) || orderStatus.startsWith("refund_")) return;
        if (!"pending".equals(orderStatus)) {
            markPaymentException(orderNo, "微信付款已成功但本地订单状态为 " + orderStatus + "，不得自动入账");
            return;
        }
        creditConfirmedOrder(order, transactionId, "微信支付订单 " + orderNo);
    }

    private RefundPreparation prepareRefund(String orderNo, Long adminUserId, String reason) {
        requireSafeOrderNo(orderNo);
        List<Map<String, Object>> orderRows = jdbc.queryForList("SELECT * FROM payment_order WHERE order_no=? FOR UPDATE", orderNo);
        if (orderRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付订单不存在");
        Map<String, Object> order = orderRows.get(0);
        if (!"paid".equals(String.valueOf(order.get("status")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅已到账且未进入退款流程的订单可申请退款");
        }
        if (!WECHAT_CHANNELS.contains(String.valueOf(order.get("channel")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "人工收款订单须线下原路退款，不能调用微信自动退款");
        }
        List<Map<String, Object>> metaRows = jdbc.queryForList("SELECT * FROM payment_wechat_order WHERE order_no=? FOR UPDATE", orderNo);
        if (metaRows.isEmpty() || blank(nullableText(metaRows.get(0).get("transaction_id")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订单缺少已核验的微信交易号，不能自动退款");
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM payment_refund WHERE order_no=?", Integer.class, orderNo);
        if (existing != null && existing > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单已有退款记录，不能重复发起");
        Long userId = toLong(order.get("user_id"));
        BigDecimal credits = decimal(order.get("credit_amount"));
        ensureCreditAccount(userId);
        Map<String, Object> account = jdbc.queryForMap("SELECT balance,frozen_balance FROM consumer_credit_account WHERE user_id=? FOR UPDATE", userId);
        BigDecimal balance = decimal(account.get("balance"));
        if (balance.compareTo(credits) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该笔充值额度已被使用或余额不足，不能自动退款；请人工核对售后");
        }
        // Conservative anti-double-spend rule: any generation reservation or completed consumption after this top-up blocks automatic refund.
        Integer consumedAfterPayment = jdbc.queryForObject("SELECT COUNT(*) FROM consumer_credit_transaction " +
                "WHERE user_id=? AND direction='consume' AND status IN ('pending','completed') AND created_at>=?", Integer.class, userId, order.get("paid_at"));
        if (consumedAfterPayment != null && consumedAfterPayment > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "充值后已有创作额度消费或预留，不能自动退款；请人工处理售后");
        }
        BigDecimal after = balance.subtract(credits);
        String refundNo = insertRefundRequest(orderNo, userId, toLong(order.get("amount_fen")), credits,
                blank(reason) ? null : reason, adminUserId);
        jdbc.update("UPDATE consumer_credit_account SET balance=?,frozen_balance=frozen_balance+? WHERE user_id=?", after, credits, userId);
        jdbc.update("INSERT INTO consumer_credit_transaction(transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)",
                "RFD-" + refundNo, userId, "payment_refund", credits, "out", "pending", balance, after,
                "微信退款额度冻结 " + orderNo, String.valueOf(adminUserId));
        jdbc.update("UPDATE payment_order SET status='refund_requested' WHERE order_no=?", orderNo);
        return new RefundPreparation(refundNo, orderNo, userId, toLong(order.get("amount_fen")), credits,
                nullableText(metaRows.get(0).get("transaction_id")), reason);
    }

    private RefundPreparation prepareUncreditedExceptionRefund(String orderNo, Long adminUserId, String reason) {
        requireSafeOrderNo(orderNo);
        List<Map<String, Object>> orderRows = jdbc.queryForList("SELECT * FROM payment_order WHERE order_no=? FOR UPDATE", orderNo);
        if (orderRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付订单不存在");
        Map<String, Object> order = orderRows.get(0);
        if (!"payment_exception".equals(String.valueOf(order.get("status")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单不是待处理的未入账支付异常");
        }
        if (!WECHAT_CHANNELS.contains(String.valueOf(order.get("channel")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "非微信订单不能发起微信异常退款");
        }
        Integer credited = jdbc.queryForObject("SELECT COUNT(*) FROM consumer_credit_transaction WHERE transaction_no=?", Integer.class, "PAY-" + orderNo);
        if (credited != null && credited > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单已产生额度入账流水，请使用常规退款流程");
        }
        List<Map<String, Object>> metaRows = jdbc.queryForList("SELECT * FROM payment_wechat_order WHERE order_no=? FOR UPDATE", orderNo);
        if (metaRows.isEmpty() || blank(nullableText(metaRows.get(0).get("transaction_id")))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "异常订单尚未从微信对账获得交易号，不能退款");
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM payment_refund WHERE order_no=?", Integer.class, orderNo);
        if (existing != null && existing > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "该订单已有退款记录，不能重复发起");
        Long userId = toLong(order.get("user_id"));
        String refundNo = insertRefundRequest(orderNo, userId, toLong(order.get("amount_fen")), BigDecimal.ZERO,
                blank(reason) ? "未入账支付异常退款" : reason, adminUserId);
        jdbc.update("UPDATE payment_order SET status='refund_requested' WHERE order_no=? AND status='payment_exception'", orderNo);
        return new RefundPreparation(refundNo, orderNo, userId, toLong(order.get("amount_fen")), BigDecimal.ZERO,
                nullableText(metaRows.get(0).get("transaction_id")), reason);
    }

    /**
     * The order row is locked by the caller, so concurrent refunds for the
     * same payment remain serialized. This retry only covers the extremely
     * unlikely global refund-number collision between different orders.
     */
    private String insertRefundRequest(String orderNo, Long userId, long amountFen, BigDecimal credits,
                                       String reason, Long adminUserId) {
        for (int attempt = 1; attempt <= REFERENCE_INSERT_ATTEMPTS; attempt++) {
            String refundNo = newRefundNo();
            try {
                jdbc.update("INSERT INTO payment_refund(refund_no,order_no,user_id,amount_fen,credit_amount,status,reason,requested_by) VALUES (?,?,?,?,?,?,?,?)",
                        refundNo, orderNo, userId, amountFen, credits, "refund_requested", reason, adminUserId);
                return refundNo;
            } catch (DuplicateKeyException collision) {
                if (attempt == REFERENCE_INSERT_ATTEMPTS) {
                    throw new IllegalStateException("退款单创建繁忙，请稍后重试", collision);
                }
                log.warn("退款单号冲突，正在重试 attempt={}", attempt);
            }
        }
        throw new IllegalStateException("退款单创建繁忙，请稍后重试");
    }

    private Map<String, Object> submitRefund(RefundPreparation preparation) {
        try {
            Map<String, Object> provider = createWechatRefund(preparation);
            transactions.execute(status -> {
                recordRefundProviderAccepted(preparation, provider);
                return null;
            });
            return refundView(preparation.refundNo);
        } catch (WechatApiException rejected) {
            if (rejected.definitivelyRejected()) {
                transactions.execute(status -> {
                    releaseRefundReservation(preparation.refundNo, "微信明确拒绝退款请求（HTTP " + rejected.statusCode + "）");
                    return null;
                });
                Map<String, Object> result = refundView(preparation.refundNo);
                result.put("message", "微信明确拒绝退款，额度冻结已解除；请人工核对原因后再处理");
                return result;
            }
            return markRefundSubmissionUnknown(preparation, rejected);
        } catch (Exception e) {
            // A timeout is deliberately treated as unknown, not failed: WeChat
            // may already have accepted the refund. Credits remain reserved until
            // a signed callback or an authenticated reconciliation proves state.
            return markRefundSubmissionUnknown(preparation, e);
        }
    }

    private Map<String, Object> markRefundSubmissionUnknown(RefundPreparation preparation, Exception error) {
        transactions.execute(status -> {
            markRefundUnknown(preparation.refundNo, safeError(error));
            return null;
        });
        Map<String, Object> result = refundView(preparation.refundNo);
        result.put("message", "退款请求状态未知，资金和额度已冻结；请通过对账确认后再处理，勿重复发起退款");
        return result;
    }

    private Map<String, Object> createWechatRefund(RefundPreparation refund) throws Exception {
        if (blank(wechatRefundNotifyUrl)) throw new IllegalStateException("未配置微信退款回调地址");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transaction_id", refund.transactionId);
        payload.put("out_refund_no", refund.refundNo);
        // The API limits this field to 80 characters.  Keep the longer local
        // audit reason in payment_refund, but never let an overlong reason
        // turn an otherwise valid refund into a provider parameter error.
        payload.put("reason", limit(blank(refund.reason) ? "用户申请退款" : refund.reason, 80));
        payload.put("notify_url", wechatRefundNotifyUrl);
        payload.put("amount", Map.of("refund", refund.amountFen, "total", refund.amountFen, "currency", "CNY"));
        return wechatJsonRequest("POST", "/v3/refund/domestic/refunds", payload);
    }

    private void recordRefundProviderAccepted(RefundPreparation refund, Map<String, Object> provider) {
        validateRefundProviderResponse(provider, refund, true);
        String providerRefundId = nullableText(provider.get("refund_id"));
        String state = nullableText(provider.get("status"));
        int changed = jdbc.update("UPDATE payment_refund SET status='refund_processing',provider_refund_id=?,provider_response=? WHERE refund_no=? AND status='refund_requested'",
                providerRefundId, compactJson(provider), refund.refundNo);
        if (changed == 0) {
            // A very fast callback/reconciliation may have finalized the row
            // while the create-refund response was in flight.  Do not regress
            // that terminal state or report a false failure to the caller.
            List<Map<String, Object>> current = jdbc.queryForList("SELECT status,provider_refund_id FROM payment_refund WHERE refund_no=? FOR UPDATE", refund.refundNo);
            if (current.isEmpty() || Set.of("refunded", "refund_failed", "refund_exception").contains(String.valueOf(current.get(0).get("status")))) return;
            throw new IllegalStateException("微信退款本地状态更新失败");
        }
        jdbc.update("UPDATE payment_order SET status='refund_processing' WHERE order_no=? AND status='refund_requested'", refund.orderNo);
        switch (state) {
            case "SUCCESS" -> finalizeRefundSuccess(refund.refundNo, providerRefundId, "微信退款接口直接返回成功");
            case "CLOSED" -> releaseRefundReservation(refund.refundNo, "微信退款接口返回已关闭");
            case "ABNORMAL" -> markRefundException(refund.refundNo, "微信退款接口返回异常");
            case "PROCESSING" -> { /* wait for signed callback or query */ }
            default -> markRefundUnknown(refund.refundNo, "微信退款接口返回未知状态：" + limit(state, 32));
        }
    }

    /** Validate the signed refund response before changing any local money state. */
    private void validateRefundProviderResponse(Map<String, Object> provider, RefundPreparation refund, boolean requireRefundId) {
        String outRefundNo = requiredText(provider, "out_refund_no");
        String outTradeNo = requiredText(provider, "out_trade_no");
        String transactionId = requiredText(provider, "transaction_id");
        String state = requiredText(provider, "status");
        if (!refund.refundNo.equals(outRefundNo) || !refund.orderNo.equals(outTradeNo)
                || !refund.transactionId.equals(transactionId)) {
            throw new IllegalStateException("微信退款响应订单或交易号不匹配");
        }
        if (!Set.of("SUCCESS", "CLOSED", "PROCESSING", "ABNORMAL").contains(state)) {
            throw new IllegalStateException("微信退款响应状态无效");
        }
        if (requireRefundId && blank(nullableText(provider.get("refund_id")))) {
            throw new IllegalStateException("微信退款未返回退款单号");
        }
        Map<String, Object> amount = asMap(provider.get("amount"));
        if (!"CNY".equals(requiredText(amount, "currency"))
                || toLong(required(amount, "total")) != refund.amountFen
                || toLong(required(amount, "refund")) != refund.amountFen) {
            throw new IllegalStateException("微信退款响应金额或币种不匹配");
        }
        validateOptionalPayerAmounts(amount, refund.amountFen);
    }

    private void validateOptionalPayerAmounts(Map<String, Object> amount, long refundFen) {
        if (amount.containsKey("payer_refund") && toLong(required(amount, "payer_refund")) < 0) {
            throw new IllegalStateException("微信退款用户退款金额无效");
        }
        if (amount.containsKey("payer_total") && toLong(required(amount, "payer_total")) < 0) {
            throw new IllegalStateException("微信退款用户支付金额无效");
        }
        if (amount.containsKey("payer_refund") && toLong(required(amount, "payer_refund")) > refundFen) {
            throw new IllegalStateException("微信退款用户退款金额超过申请金额");
        }
    }

    private void markRefundUnknown(String refundNo, String detail) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT order_no,status FROM payment_refund WHERE refund_no=? FOR UPDATE", refundNo);
        if (rows.isEmpty()) return;
        String orderNo = String.valueOf(rows.get(0).get("order_no"));
        jdbc.update("UPDATE payment_refund SET status='refund_unknown',provider_response=? WHERE refund_no=? AND status IN ('refund_requested','refund_processing')", detail, refundNo);
        jdbc.update("UPDATE payment_order SET status='refund_unknown' WHERE order_no=? AND status IN ('refund_requested','refund_processing')", orderNo);
    }

    private void processWechatRefundNotification(VerifiedWechatNotification notification, String rawBody) {
        if (!notification.eventType.startsWith("REFUND.")) return;
        Map<String, Object> refundData = notification.resource;
        String refundNo;
        try { refundNo = requiredText(refundData, "out_refund_no"); }
        catch (IllegalArgumentException e) { return; }
        List<Map<String, Object>> refundRows = jdbc.queryForList("SELECT * FROM payment_refund WHERE refund_no=? FOR UPDATE", refundNo);
        if (refundRows.isEmpty()) return; // audit only; no local credit mutation for an unknown refund
        Map<String, Object> refund = refundRows.get(0);
        List<Map<String, Object>> orderRows = jdbc.queryForList("SELECT * FROM payment_order WHERE order_no=? FOR UPDATE", refund.get("order_no"));
        List<Map<String, Object>> metaRows = orderRows.isEmpty() ? List.of() : jdbc.queryForList("SELECT * FROM payment_wechat_order WHERE order_no=? FOR UPDATE", refund.get("order_no"));
        if (orderRows.isEmpty() || metaRows.isEmpty() || !refundPayloadMatches(refundData, refund, orderRows.get(0), metaRows.get(0), true)) {
            markRefundException(refundNo, "微信退款回调字段校验不匹配");
            return;
        }
        String status = nullableText(refundData.get("refund_status"));
        if (blank(status)) status = nullableText(refundData.get("status"));
        if ("SUCCESS".equals(status)) {
            finalizeRefundSuccess(refundNo, nullableText(refundData.get("refund_id")), "微信退款回调成功");
        } else if ("CLOSED".equals(status)) {
            releaseRefundReservation(refundNo, "微信退款已关闭");
        } else if ("PROCESSING".equals(status)) {
            jdbc.update("UPDATE payment_refund SET status='refund_processing',provider_response=? WHERE refund_no=? AND status IN ('refund_requested','refund_processing','refund_unknown')",
                    compactJson(refundData), refundNo);
            jdbc.update("UPDATE payment_order SET status='refund_processing' WHERE order_no=? AND status IN ('refund_requested','refund_unknown')", refund.get("order_no"));
        } else if ("ABNORMAL".equals(status)) {
            markRefundException(refundNo, "微信退款回调报告退款异常");
        } else {
            markRefundException(refundNo, "微信退款状态：" + limit(status, 32));
        }
    }

    private boolean refundPayloadMatches(Map<String, Object> data, Map<String, Object> refund, Map<String, Object> order, Map<String, Object> meta, boolean callbackPayload) {
        try {
            String mchId = nullableText(data.get("mchid"));
            if ((!blank(mchId) && !wechatMchId.equals(mchId)) || !wechatMchId.equals(nullableText(meta.get("mch_id")))) return false;
            if (callbackPayload && blank(mchId)) return false;
            String appId = nullableText(data.get("appid"));
            if (!blank(appId) && (!wechatAppId.equals(appId) || !wechatAppId.equals(nullableText(meta.get("app_id"))))) return false;
            if (!String.valueOf(refund.get("order_no")).equals(requiredText(data, "out_trade_no"))) return false;
            if (!nullableText(meta.get("transaction_id")).equals(requiredText(data, "transaction_id"))) return false;
            String refundId = nullableText(data.get("refund_id"));
            String storedRefundId = nullableText(refund.get("provider_refund_id"));
            if (blank(refundId)) return false;
            if (!blank(storedRefundId) && !blank(refundId) && !storedRefundId.equals(refundId)) return false;
            Map<String, Object> amount = asMap(data.get("amount"));
            boolean amountsMatch = "CNY".equals(requiredText(amount, "currency"))
                    && toLong(required(amount, "refund")) == toLong(refund.get("amount_fen"))
                    && toLong(required(amount, "total")) == toLong(order.get("amount_fen"));
            if (!amountsMatch) return false;
            validateOptionalPayerAmounts(amount, toLong(refund.get("amount_fen")));
            return true;
        } catch (RuntimeException e) { return false; }
    }

    private void finalizeRefundSuccess(String refundNo, String providerRefundId, String remark) {
        List<Map<String, Object>> refundRows = jdbc.queryForList("SELECT * FROM payment_refund WHERE refund_no=? FOR UPDATE", refundNo);
        if (refundRows.isEmpty()) return;
        Map<String, Object> refund = refundRows.get(0);
        String callbackRefundId = nullableText(providerRefundId);
        String storedRefundId = nullableText(refund.get("provider_refund_id"));
        if (!blank(storedRefundId) && !blank(callbackRefundId) && !storedRefundId.equals(callbackRefundId)) {
            markRefundException(refundNo, "微信退款单号与本地记录不一致");
            return;
        }
        if ("refunded".equals(String.valueOf(refund.get("status")))) return;
        if (!Set.of("refund_requested", "refund_processing", "refund_unknown").contains(String.valueOf(refund.get("status")))) {
            markRefundException(refundNo, "退款成功回调到达时状态非法");
            return;
        }
        BigDecimal credits = decimal(refund.get("credit_amount"));
        if (credits.compareTo(BigDecimal.ZERO) > 0) {
            Long userId = toLong(refund.get("user_id"));
            Map<String, Object> account = jdbc.queryForMap("SELECT frozen_balance FROM consumer_credit_account WHERE user_id=? FOR UPDATE", userId);
            if (decimal(account.get("frozen_balance")).compareTo(credits) < 0) {
                markRefundException(refundNo, "退款额度冻结余额不一致，需人工处理");
                return;
            }
            jdbc.update("UPDATE consumer_credit_account SET frozen_balance=frozen_balance-? WHERE user_id=?", credits, userId);
            int txChanged = jdbc.update("UPDATE consumer_credit_transaction SET status='completed',remark=CONCAT(COALESCE(remark,''),?) WHERE transaction_no=? AND status='pending'",
                    ";微信退款已完成", "RFD-" + refundNo);
            if (txChanged != 1) throw new IllegalStateException("退款额度流水缺失或状态异常，需人工核对");
        }
        jdbc.update("UPDATE payment_refund SET status='refunded',provider_refund_id=COALESCE(?,provider_refund_id),completed_at=NOW(),provider_response=? WHERE refund_no=?",
                blank(callbackRefundId) ? null : callbackRefundId, remark, refundNo);
        jdbc.update("UPDATE payment_order SET status='refunded' WHERE order_no=? AND status IN ('refund_requested','refund_processing','refund_unknown')", refund.get("order_no"));
    }

    private void releaseRefundReservation(String refundNo, String reason) {
        List<Map<String, Object>> refundRows = jdbc.queryForList("SELECT * FROM payment_refund WHERE refund_no=? FOR UPDATE", refundNo);
        if (refundRows.isEmpty()) return;
        Map<String, Object> refund = refundRows.get(0);
        if (!Set.of("refund_requested", "refund_processing", "refund_unknown").contains(String.valueOf(refund.get("status")))) return;
        BigDecimal credits = decimal(refund.get("credit_amount"));
        if (credits.compareTo(BigDecimal.ZERO) > 0) {
            Long userId = toLong(refund.get("user_id"));
            Map<String, Object> account = jdbc.queryForMap("SELECT balance,frozen_balance FROM consumer_credit_account WHERE user_id=? FOR UPDATE", userId);
            if (decimal(account.get("frozen_balance")).compareTo(credits) < 0) {
                markRefundException(refundNo, "退款关闭时额度冻结余额不一致，需人工处理");
                return;
            }
            BigDecimal after = decimal(account.get("balance")).add(credits);
            jdbc.update("UPDATE consumer_credit_account SET balance=?,frozen_balance=frozen_balance-? WHERE user_id=?", after, credits, userId);
            int txChanged = jdbc.update("UPDATE consumer_credit_transaction SET status='cancelled',balance_after=?,remark=CONCAT(COALESCE(remark,''),?) WHERE transaction_no=? AND status='pending'",
                    after, ";" + reason, "RFD-" + refundNo);
            if (txChanged != 1) throw new IllegalStateException("退款额度流水缺失或状态异常，需人工核对");
        }
        jdbc.update("UPDATE payment_refund SET status='refund_failed',provider_response=? WHERE refund_no=?", reason, refundNo);
        jdbc.update("UPDATE payment_order SET status=? WHERE order_no=? AND status IN ('refund_requested','refund_processing','refund_unknown')",
                credits.compareTo(BigDecimal.ZERO) > 0 ? "paid" : "payment_exception", refund.get("order_no"));
    }

    private void markRefundException(String refundNo, String detail) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT order_no FROM payment_refund WHERE refund_no=? FOR UPDATE", refundNo);
        if (rows.isEmpty()) return;
        String orderNo = String.valueOf(rows.get(0).get("order_no"));
        jdbc.update("UPDATE payment_refund SET status='refund_exception',provider_response=? WHERE refund_no=? AND status<>'refunded'", limit(detail, 3500), refundNo);
        jdbc.update("UPDATE payment_order SET status='refund_exception' WHERE order_no=? AND status<>'refunded'", orderNo);
    }

    private void reconcileWechatPayment(String orderNo) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.status,p.channel,w.app_id,w.mch_id FROM payment_order p JOIN payment_wechat_order w ON w.order_no=p.order_no WHERE p.order_no=?", orderNo);
        if (rows.isEmpty() || !WECHAT_CHANNELS.contains(String.valueOf(rows.get(0).get("channel")))) return;
        Map<String, Object> response;
        try {
            response = wechatJsonRequest("GET", "/v3/pay/transactions/out-trade-no/" + orderNo + "?mchid=" + encode(wechatMchId), null);
        } catch (WechatApiException error) {
            if (!error.resourceNotExists()) throw error;
            // The authenticated merchant query conclusively found no trade.
            // This resolves an uncertain submit without keeping the consumer
            // permanently blocked in payment_exception.
            transactions.execute(status -> {
                jdbc.update("UPDATE payment_order SET status='failed',provider_response=? WHERE order_no=? AND status IN ('pending','payment_exception')",
                        "微信主动对账：交易不存在", orderNo);
                jdbc.update("UPDATE payment_wechat_order SET provider_trade_state='NOT_FOUND',last_reconciled_at=NOW() WHERE order_no=?", orderNo);
                return null;
            });
            return;
        }
        transactions.execute(status -> {
            String tradeState = nullableText(response.get("trade_state"));
            jdbc.update("UPDATE payment_wechat_order SET provider_trade_state=?,last_reconciled_at=NOW() WHERE order_no=?", limit(tradeState, 32), orderNo);
            if ("SUCCESS".equals(tradeState)) {
                VerifiedWechatNotification synthetic = new VerifiedWechatNotification("reconcile-" + orderNo + "-" + Instant.now().getEpochSecond(), "TRANSACTION.SUCCESS", response);
                processWechatPaymentNotification(synthetic, compactJson(response));
            } else if (Set.of("CLOSED", "REVOKED", "PAYERROR").contains(tradeState)) {
                String next = "CLOSED".equals(tradeState) ? "closed" : "failed";
                // A prior network timeout may have left the local order in
                // payment_exception. Once the signed official query proves
                // that no payment exists, it is safe to converge that state
                // to a reusable terminal status.
                jdbc.update("UPDATE payment_order SET status=?,provider_response=? WHERE order_no=? AND status IN ('pending','payment_exception')",
                        next, "微信主动对账：" + tradeState, orderNo);
            }
            return null;
        });
    }

    private Map<String, Object> downloadAndReconcileDailyBill(LocalDate date, String billType, Long adminUserId) throws Exception {
        String path = switch (billType) {
            case "trade" -> "/v3/bill/tradebill?bill_date=" + date + "&bill_type=ALL";
            case "refund" -> "/v3/bill/refundbill?bill_date=" + date;
            case "fundflow" -> "/v3/bill/fundflowbill?bill_date=" + date + "&account_type=BASIC";
            default -> throw new IllegalArgumentException("未知账单类型");
        };
        Map<String, Object> bill = wechatJsonRequest("GET", path, null);
        String downloadUrl = requiredText(bill, "download_url");
        byte[] bytes = downloadWechatBill(downloadUrl);
        String hashType = requiredText(bill, "hash_type");
        String expectedHash = requiredText(bill, "hash_value");
        // WeChat's hash_value is calculated over the original CSV/TXT
        // contents. If a caller requests a gzip stream, hash the decompressed
        // payload; an uncompressed response simply passes through unchanged.
        byte[] plainBytes = unzipIfNeeded(bytes);
        String officialHash = digestHex(plainBytes, hashType);
        if (!officialHash.equalsIgnoreCase(expectedHash)) throw new SecurityException("微信" + billType + "账单哈希校验失败");
        // Persist our own fixed SHA-256 evidence hash even if the official
        // response currently uses SHA-1, so the column semantics stay true
        // and an auditor can identify the exact raw bill contents later.
        String evidenceSha256 = digestHex(plainBytes, "SHA-256");
        String text = decodeBillText(plainBytes);
        Map<String, Long> localRecords = localBillAmounts(date, billType);
        ParsedBill parsed = parseBill(text, billType);
        Map<String, List<Long>> providerRecordsByKey = new LinkedHashMap<>();
        for (BillRecord record : parsed.records()) {
            providerRecordsByKey.computeIfAbsent(record.key(), ignored -> new ArrayList<>()).add(record.amountFen());
        }

        int matched = 0;
        int missingFromProvider = 0;
        int extraInProvider = 0;
        int amountMismatch = 0;
        Set<String> allKeys = new LinkedHashSet<>(localRecords.keySet());
        allKeys.addAll(providerRecordsByKey.keySet());
        boolean compareAmounts = !"fundflow".equals(billType);
        List<String> differenceSamples = new ArrayList<>();
        for (String key : allKeys) {
            Long localAmount = localRecords.get(key);
            List<Long> providerAmounts = providerRecordsByKey.get(key);
            if (providerAmounts == null) {
                missingFromProvider++;
                addDifferenceSample(differenceSamples, "微信账单缺少本地记录 " + key);
                continue;
            }
            if (localAmount == null) {
                extraInProvider++;
                addDifferenceSample(differenceSamples, "本地缺少微信账单记录 " + key);
                continue;
            }
            if (providerAmounts.size() != 1) {
                amountMismatch++;
                addDifferenceSample(differenceSamples, "微信账单重复记录 " + key + "（" + providerAmounts.size() + "行）");
                continue;
            }
            Long providerAmount = providerAmounts.get(0);
            if (compareAmounts && (providerAmount == null || !providerAmount.equals(localAmount))) {
                amountMismatch++;
                addDifferenceSample(differenceSamples, "金额不一致 " + key + "（本地" + localAmount + "分/微信" + providerAmount + "分）");
                continue;
            }
            matched++;
        }
        int discrepancy = missingFromProvider + extraInProvider + amountMismatch + parsed.issues().size();
        String state = discrepancy == 0 ? "downloaded_matched_local_refs" : "downloaded_needs_review";
        String comparisonLabel = compareAmounts ? "金额/重复不符" : "重复/业务单号不符";
        String comparisonConclusion = differenceSamples.isEmpty()
                ? (compareAmounts
                    ? "交易/退款记录与金额一致。"
                    : "资金账单业务单号已完成初步交叉核对；金额字段不作为自动对账结论，需用真实商户账单样本确认映射后再启用。")
                : "差异样例：" + String.join("；", differenceSamples);
        String summary = "已下载官方" + billType + "账单；微信" + hashType + "=" + officialHash + "；本地SHA-256=" + evidenceSha256
                + "；本地记录=" + localRecords.size() + "，微信有效记录=" + parsed.records().size()
                + "，匹配=" + matched + "，本地缺失=" + missingFromProvider + "，微信多出=" + extraInProvider
                + "，" + comparisonLabel + "=" + amountMismatch + "，解析问题=" + parsed.issues().size()
                + "。" + comparisonConclusion
                + "资金不会因账单结果自动调整。";
        upsertDailyBill(date, billType, state, evidenceSha256, bytes.length, localRecords.size(), parsed.records().size(), matched, discrepancy, summary, adminUserId);
        return dailyBillView(date, billType);
    }

    private byte[] downloadWechatBill(String downloadUrl) throws Exception {
        URI uri = URI.create(downloadUrl);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || blank(host)
                || !WECHAT_BILL_DOWNLOAD_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new SecurityException("微信账单下载地址域名不受信任");
        }
        String rawPath = uri.getRawPath() + (blank(uri.getRawQuery()) ? "" : "?" + uri.getRawQuery());
        String nonce = randomNonce();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String message = "GET\n" + rawPath + "\n" + timestamp + "\n" + nonce + "\n\n";
        String authorization = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + wechatMchId + "\",nonce_str=\"" + nonce + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + wechatSerialNo + "\",signature=\"" + sign(message, merchantPrivateKey()) + "\"";
        HttpRequest request = httpRequest(uri, WECHAT_BILL_TIMEOUT)
                .header("Accept", "text/csv,application/octet-stream").header("Authorization", authorization).GET().build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length == 0) throw new IllegalStateException("微信账单下载失败");
        if (response.body().length > MAX_BILL_DOWNLOAD_BYTES) throw new IllegalStateException("微信账单文件超过100MB限制");
        return response.body();
    }

    private byte[] unzipIfNeeded(byte[] bytes) throws Exception {
        if (bytes.length < 2 || (bytes[0] & 0xff) != 0x1f || (bytes[1] & 0xff) != 0x8b) return bytes;
        try (GZIPInputStream input = new GZIPInputStream(new java.io.ByteArrayInputStream(bytes));
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (output.size() > MAX_BILL_UNCOMPRESSED_BYTES - read) {
                    throw new IllegalStateException("微信账单解压后超过200MB限制");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private Map<String, Long> localBillAmounts(LocalDate date, String billType) {
        String start = date + " 00:00:00";
        String end = date.plusDays(1) + " 00:00:00";
        String sql;
        if ("refund".equals(billType)) {
            sql = "SELECT refund_no,amount_fen FROM payment_refund WHERE completed_at>=? AND completed_at<? AND refund_no IS NOT NULL";
        } else if ("fundflow".equals(billType)) {
            sql = "SELECT provider_order_no,amount_fen FROM payment_order WHERE paid_at>=? AND paid_at<? AND provider_order_no IS NOT NULL";
        } else {
            sql = "SELECT order_no,amount_fen FROM payment_order WHERE paid_at>=? AND paid_at<? AND provider_order_no IS NOT NULL";
        }
        Map<String, Long> records = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            String key = cleanBillValue(rs.getString(1));
            if (!blank(key)) records.put(key, rs.getLong(2));
        }, start, end);
        return records;
    }

    private String decodeBillText(byte[] bytes) {
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        // WeChat currently emits UTF-8.  Keep a GB18030 fallback for older
        // merchant bill exports that otherwise contain replacement characters.
        if (utf8.indexOf('\uFFFD') >= 0) {
            try {
                String legacy = new String(bytes, java.nio.charset.Charset.forName("GB18030"));
                if (legacy.indexOf('\uFFFD') < utf8.indexOf('\uFFFD')) return legacy;
            } catch (Exception ignored) { }
        }
        return utf8;
    }

    private ParsedBill parseBill(String text, String billType) {
        List<List<String>> rows = parseCsvRows(text);
        if (rows.isEmpty()) return new ParsedBill(List.of(), List.of("账单为空"));
        List<String> keyAliases = switch (billType) {
            case "refund" -> List.of("商户退款单号");
            case "fundflow" -> List.of("微信支付业务单号", "商户订单号", "业务单号");
            default -> List.of("商户订单号");
        };
        List<String> amountAliases = switch (billType) {
            case "refund" -> List.of("退款金额", "申请退款金额");
            // Fund-flow CSVs contain multiple business types and debit/credit
            // conventions. Until a real merchant sample fixes that mapping,
            // only reconcile its signed download and business references.
            case "fundflow" -> List.of();
            // Compare the original order total held locally. “应结订单金额”
            // can differ when coupons or settlement adjustments exist, so use
            // it only as a backward-compatible fallback.
            default -> List.of("订单金额", "总金额", "应结订单金额");
        };
        int headerIndex = -1;
        int keyIndex = -1;
        int amountIndex = -1;
        for (int i = 0; i < rows.size(); i++) {
            List<String> candidate = rows.get(i);
            keyIndex = findBillColumn(candidate, keyAliases);
            if (keyIndex >= 0) {
                headerIndex = i;
                amountIndex = findBillColumn(candidate, amountAliases);
                break;
            }
        }
        if (headerIndex < 0) return new ParsedBill(List.of(), List.of("未找到" + keyAliases.get(0) + "表头"));
        List<BillRecord> records = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.isEmpty()) continue;
            String key = keyIndex < row.size() ? cleanBillValue(row.get(keyIndex)) : "";
            if (blank(key) || isBillSummaryRow(key)) continue;
            Long amountFen = null;
            if (amountIndex >= 0 && amountIndex < row.size() && !blank(cleanBillValue(row.get(amountIndex)))) {
                try { amountFen = yuanToFen(row.get(amountIndex)); }
                catch (Exception e) {
                    addIssue(issues, "金额无法解析（" + key + "）");
                }
            } else if (!"fundflow".equals(billType)) {
                addIssue(issues, "缺少金额（" + key + "）");
            }
            records.add(new BillRecord(key, amountFen));
        }
        return new ParsedBill(records, issues);
    }

    private List<List<String>> parseCsvRows(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((ch == '\n' || ch == '\r') && !quoted) {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && blank(row.get(0)))) rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(ch);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            if (!(row.size() == 1 && blank(row.get(0)))) rows.add(row);
        }
        return rows;
    }

    private int findBillColumn(List<String> headers, List<String> aliases) {
        // Prefer exact names in the caller-provided priority order. In
        // particular, “应结订单金额” contains “订单金额” but is not the same
        // figure once discounts or settlement adjustments are present.
        for (String alias : aliases) {
            for (int i = 0; i < headers.size(); i++) {
                String header = normalizedBillHeader(headers.get(i));
                if (header.equals(normalizedBillHeader(alias))) return i;
            }
        }
        for (String alias : aliases) {
            for (int i = 0; i < headers.size(); i++) {
                String header = normalizedBillHeader(headers.get(i));
                if (header.contains(normalizedBillHeader(alias))) return i;
            }
        }
        return -1;
    }

    /** Removes display-only units such as “（元）” before matching an
     * official CSV header, without conflating “订单金额” and “应结订单金额”. */
    private String normalizedBillHeader(String value) {
        return cleanBillValue(value).replaceAll("\\s+", "").replaceAll("[（(][^）)]*[）)]", "");
    }

    private String cleanBillValue(String value) {
        if (value == null) return "";
        return value.replace("\uFEFF", "").trim().replaceAll("^'+", "");
    }

    private boolean isBillSummaryRow(String value) {
        return value.startsWith("总交易") || value.startsWith("总退款") || value.startsWith("合计") || value.startsWith("汇总");
    }

    private Long yuanToFen(String value) {
        String cleaned = cleanBillValue(value).replace(",", "").replace("¥", "").replace("￥", "");
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) cleaned = "-" + cleaned.substring(1, cleaned.length() - 1);
        return new BigDecimal(cleaned).movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact();
    }

    private void addIssue(List<String> issues, String issue) {
        if (issues.size() < 50) issues.add(issue);
    }

    private void addDifferenceSample(List<String> samples, String sample) {
        if (samples.size() < 12) samples.add(sample);
    }

    private String digestHex(byte[] bytes, String hashType) throws Exception {
        String algorithm = switch (hashType.toUpperCase(Locale.ROOT)) {
            case "SHA1", "SHA-1" -> "SHA-1";
            case "SHA256", "SHA-256" -> "SHA-256";
            default -> throw new SecurityException("微信账单哈希算法不受支持");
        };
        byte[] digest = MessageDigest.getInstance(algorithm).digest(bytes);
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest) out.append(String.format("%02x", value));
        return out.toString();
    }

    private void upsertDailyBill(LocalDate date, String type, String state, String hash, long bytes, int local, int provider, int matched, int discrepancy, String summary, Long adminUserId) {
        int updated = jdbc.update("UPDATE payment_daily_reconciliation SET status=?,download_sha256=?,download_bytes=?,local_record_count=?,provider_record_count=?,matched_record_count=?,discrepancy_count=?,result_summary=?,verified_by=?,verified_at=NOW() WHERE bill_date=? AND bill_type=?",
                state, hash, bytes, local, provider, matched, discrepancy, summary, adminUserId, date, type);
        if (updated != 0) return;
        try {
            jdbc.update("INSERT INTO payment_daily_reconciliation(bill_date,bill_type,status,download_sha256,download_bytes,local_record_count,provider_record_count,matched_record_count,discrepancy_count,result_summary,verified_by,verified_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,NOW())",
                    date, type, state, hash, bytes, local, provider, matched, discrepancy, summary, adminUserId);
        } catch (DataIntegrityViolationException concurrentInsert) {
            // Manual reconciliation and the scheduler can start together.
            // Another writer created the key after our UPDATE; replace its
            // result with this verified run rather than surfacing a spurious
            // duplicate-key error or recording the bill as failed.
            jdbc.update("UPDATE payment_daily_reconciliation SET status=?,download_sha256=?,download_bytes=?,local_record_count=?,provider_record_count=?,matched_record_count=?,discrepancy_count=?,result_summary=?,verified_by=?,verified_at=NOW() WHERE bill_date=? AND bill_type=?",
                    state, hash, bytes, local, provider, matched, discrepancy, summary, adminUserId, date, type);
        }
    }

    private Map<String, Object> recordDailyBillFailure(LocalDate date, String type, Long adminUserId, String error) {
        String summary = "账单下载或解析失败：" + limit(error, 1000) + "；不得据此自动调整账务。";
        // Never replace a completed download (including one with legitimate
        // discrepancies) with a concurrent task's transient failure.
        int updated = jdbc.update("UPDATE payment_daily_reconciliation SET status='download_failed',download_sha256=NULL,download_bytes=0,local_record_count=0,provider_record_count=0,matched_record_count=0,discrepancy_count=-1,result_summary=?,verified_by=?,verified_at=NOW() WHERE bill_date=? AND bill_type=? AND status NOT LIKE 'downloaded_%'",
                summary, adminUserId, date, type);
        if (updated == 0) {
            Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM payment_daily_reconciliation WHERE bill_date=? AND bill_type=?", Integer.class, date, type);
            if (existing == null || existing == 0) {
                try {
                    jdbc.update("INSERT INTO payment_daily_reconciliation(bill_date,bill_type,status,download_sha256,download_bytes,local_record_count,provider_record_count,matched_record_count,discrepancy_count,result_summary,verified_by,verified_at) VALUES (?,?,'download_failed',NULL,0,0,0,0,-1,?,?,NOW())",
                            date, type, summary, adminUserId);
                } catch (DataIntegrityViolationException concurrentInsert) {
                    // A concurrent verified run created the row; preserve it.
                }
            }
        }
        return dailyBillView(date, type);
    }

    private Map<String, Object> dailyBillView(LocalDate date, String type) {
        return jdbc.queryForMap("SELECT bill_date billDate,bill_type billType,status,download_sha256 downloadSha256,download_bytes downloadBytes,local_record_count localRecordCount,provider_record_count providerRecordCount,matched_record_count matchedRecordCount,discrepancy_count discrepancyCount,result_summary resultSummary,verified_at verifiedAt FROM payment_daily_reconciliation WHERE bill_date=? AND bill_type=?", date, type);
    }

    /**
     * A local expiry never closes a WeChat trade by itself. The official trade
     * is first queried/closed, then and only then is this order marked expired.
     */
    private void expireWechatTradeIfDue(String orderNo) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status,expired_at FROM payment_order WHERE order_no=? AND channel IN ('wechat','wechat_jsapi')", orderNo);
        if (rows.isEmpty() || !"pending".equals(String.valueOf(rows.get(0).get("status")))) return;
        LocalDateTime expiresAt = localDateTime(rows.get(0).get("expired_at"));
        if (expiresAt == null || expiresAt.isAfter(LocalDateTime.now())) return;
        try {
            closeWechatTrade(orderNo);
        } catch (WechatApiException closeError) {
            reconcileWechatPayment(orderNo);
            List<Map<String, Object>> after = jdbc.queryForList("SELECT status FROM payment_order WHERE order_no=?", orderNo);
            if (!after.isEmpty() && "pending".equals(String.valueOf(after.get(0).get("status")))) {
                transactions.execute(status -> {
                    markPaymentException(orderNo, "订单到期后微信关单结果未知，等待主动对账");
                    return null;
                });
            }
            return;
        }
        transactions.execute(status -> {
            jdbc.update("UPDATE payment_order SET status='expired',provider_response=? WHERE order_no=? AND status='pending' AND expired_at<=CURRENT_TIMESTAMP",
                    "微信支付订单已官方关单并过期", orderNo);
            return null;
        });
    }

    private void reconcileRefundForOrder(String orderNo) throws Exception {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT refund_no FROM payment_refund WHERE order_no=? AND status IN ('refund_requested','refund_processing','refund_unknown')", orderNo);
        if (rows.isEmpty()) return;
        String refundNo = String.valueOf(rows.get(0).get("refund_no"));
        Map<String, Object> response;
        try {
            response = wechatJsonRequest("GET", "/v3/refund/domestic/refunds/" + refundNo, null);
        } catch (WechatApiException error) {
            if (!error.resourceNotExists()) throw error;
            transactions.execute(status -> {
                List<Map<String, Object>> localRows = jdbc.queryForList("SELECT provider_refund_id FROM payment_refund WHERE refund_no=? FOR UPDATE", refundNo);
                if (localRows.isEmpty()) return null;
                if (blank(nullableText(localRows.get(0).get("provider_refund_id")))) {
                    // No provider refund id was ever recorded and the official
                    // query confirms it does not exist, so release the local
                    // credit reservation rather than leaving it frozen.
                    releaseRefundReservation(refundNo, "微信主动对账确认退款单不存在");
                } else {
                    // A recorded provider refund id combined with a 404 is an
                    // inconsistency, not proof that money was not refunded.
                    markRefundException(refundNo, "微信退款单号已受理但主动查询不存在，需人工核对");
                }
                return null;
            });
            return;
        }
        transactions.execute(status -> {
            List<Map<String, Object>> refundRows = jdbc.queryForList("SELECT * FROM payment_refund WHERE refund_no=? FOR UPDATE", refundNo);
            if (refundRows.isEmpty()) return null;
            List<Map<String, Object>> orderRows = jdbc.queryForList("SELECT * FROM payment_order WHERE order_no=? FOR UPDATE", orderNo);
            List<Map<String, Object>> metaRows = jdbc.queryForList("SELECT * FROM payment_wechat_order WHERE order_no=? FOR UPDATE", orderNo);
            if (orderRows.isEmpty() || metaRows.isEmpty()
                    || !refundPayloadMatches(response, refundRows.get(0), orderRows.get(0), metaRows.get(0), false)) {
                markRefundException(refundNo, "微信退款查询字段校验不匹配");
                return null;
            }
            String providerStatus = nullableText(response.get("status"));
            String providerRefundId = nullableText(response.get("refund_id"));
            if ("SUCCESS".equals(providerStatus)) {
                finalizeRefundSuccess(refundNo, providerRefundId, "微信主动对账确认退款成功");
            } else if ("CLOSED".equals(providerStatus)) {
                releaseRefundReservation(refundNo, "微信主动对账确认退款关闭");
            } else if ("ABNORMAL".equals(providerStatus)) {
                markRefundException(refundNo, "微信主动对账发现退款异常");
            } else if ("PROCESSING".equals(providerStatus)) {
                jdbc.update("UPDATE payment_refund SET status='refund_processing',provider_response=? WHERE refund_no=?", compactJson(response), refundNo);
                jdbc.update("UPDATE payment_order SET status='refund_processing' WHERE order_no=? AND status IN ('refund_requested','refund_unknown')", orderNo);
            } else {
                markRefundException(refundNo, "微信主动对账返回未知退款状态：" + limit(providerStatus, 32));
            }
            return null;
        });
    }

    private void closeWechatTrade(String orderNo) throws Exception {
        // wechatJsonRequest rejects every non-2xx response. The caller then
        // queries the official order status instead of closing only locally.
        wechatJsonRequest("POST", "/v3/pay/transactions/out-trade-no/" + orderNo + "/close", Map.of("mchid", wechatMchId));
    }

    private Map<String, Object> wechatJsonRequest(String method, String path, Map<String, Object> payload) throws Exception {
        String body = payload == null ? "" : mapper.writeValueAsString(payload);
        HttpResponse<String> response = wechatRequest(method, path, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String providerCode = "";
            try { providerCode = mapper.readTree(response.body()).path("code").asText(""); } catch (Exception ignored) { }
            throw new WechatApiException(response.statusCode(), providerCode, "微信支付服务返回 HTTP " + response.statusCode() + (blank(providerCode) ? "" : "（" + providerCode + "）"));
        }
        if (blank(response.body())) return Map.of();
        return mapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private HttpResponse<String> wechatRequest(String method, String path, String body) throws Exception {
        requireWechatPaymentReady();
        String nonce = randomNonce();
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String safeBody = body == null ? "" : body;
        String message = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + safeBody + "\n";
        String authorization = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + wechatMchId + "\",nonce_str=\"" + nonce + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + wechatSerialNo + "\",signature=\"" + sign(message, merchantPrivateKey()) + "\"";
        HttpRequest.Builder builder = httpRequest(URI.create("https://api.mch.weixin.qq.com" + path), WECHAT_TRANSACTION_TIMEOUT)
                .header("Accept", "application/json")
                .header("Authorization", authorization);
        if ("GET".equals(method)) builder.GET();
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(safeBody, StandardCharsets.UTF_8));
        HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        // API v3 returns a signed response. TLS alone is not accepted as the
        // business confirmation for a money-moving request.
        if (response.statusCode() >= 200 && response.statusCode() < 300) verifyWechatApiResponse(response);
        return response;
    }

    private void verifyWechatApiResponse(HttpResponse<String> response) throws Exception {
        String timestamp = response.headers().firstValue("Wechatpay-Timestamp").orElse("");
        String nonce = response.headers().firstValue("Wechatpay-Nonce").orElse("");
        String signature = response.headers().firstValue("Wechatpay-Signature").orElse("");
        String serial = response.headers().firstValue("Wechatpay-Serial").orElse("");
        if (!verifyWechatSignature(timestamp, nonce, signature, serial, response.body())) {
            throw new SecurityException("微信支付响应验签失败");
        }
    }

    private VerifiedWechatNotification verifyAndDecryptNotification(String timestamp, String nonce, String signature, String serial, String rawBody) throws Exception {
        if (rawBody == null || rawBody.getBytes(StandardCharsets.UTF_8).length > CALLBACK_BODY_MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信支付回调体无效");
        }
        if (!verifyWechatSignature(timestamp, nonce, signature, serial, rawBody)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "微信支付回调验签失败");
        }
        JsonNode root = mapper.readTree(rawBody);
        String eventId = root.path("id").asText("").trim();
        String eventType = root.path("event_type").asText("").trim();
        JsonNode resource = root.path("resource");
        String resourceType = root.path("resource_type").asText("").trim();
        String originalType = resource.path("original_type").asText("").trim();
        String algorithm = resource.path("algorithm").asText("").trim();
        // `original_type` is part of the authenticated envelope.  Treat a
        // missing value as malformed rather than accepting a legacy/ambiguous
        // resource and routing it to the wrong money-moving handler.
        boolean expectedOriginalType = (eventType.startsWith("TRANSACTION.") && "transaction".equals(originalType))
                || (eventType.startsWith("REFUND.") && "refund".equals(originalType));
        if (blank(eventId) || eventId.length() > 128 || blank(eventType) || !resource.isObject()
                || !"encrypt-resource".equals(resourceType) || !expectedOriginalType
                || !"AEAD_AES_256_GCM".equals(algorithm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "微信支付回调格式无效");
        }
        String plain = decryptWechatResource(resource.path("associated_data").asText(), resource.path("nonce").asText(), resource.path("ciphertext").asText());
        Map<String, Object> data = mapper.readValue(plain, new TypeReference<Map<String, Object>>() {});
        return new VerifiedWechatNotification(eventId, eventType, data);
    }

    private boolean verifyWechatSignature(String timestamp, String nonce, String signature, String serial, String body) throws Exception {
        if (!wechatPaymentReady() || blank(timestamp) || blank(nonce) || blank(signature) || blank(serial)
                || !wechatPlatformSerialNo.equals(serial) || !freshTimestamp(timestamp)) return false;
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(wechatPlatformPublicKey());
            verifier.update((timestamp + "\n" + nonce + "\n" + (body == null ? "" : body) + "\n").getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (IllegalArgumentException e) { return false; }
    }

    private boolean freshTimestamp(String timestamp) {
        try {
            long seconds = Long.parseLong(timestamp);
            long maxAge = Math.max(30, Math.min(wechatCallbackMaxAgeSeconds, 900));
            return Math.abs(Instant.now().getEpochSecond() - seconds) <= maxAge;
        } catch (NumberFormatException e) { return false; }
    }

    private String decryptWechatResource(String associatedData, String nonce, String ciphertext) throws Exception {
        byte[] key = wechatApiV3Key.getBytes(StandardCharsets.UTF_8);
        if (key.length != 32) throw new IllegalStateException("微信支付 API v3 Key 必须为32字节");
        if (blank(nonce) || blank(ciphertext)) throw new IllegalArgumentException("微信支付回调加密资源无效");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        cipher.updateAAD((associatedData == null ? "" : associatedData).getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
    }

    private PrivateKey merchantPrivateKey() throws Exception {
        String pem = Files.readString(Path.of(wechatPrivateKeyPath), StandardCharsets.UTF_8)
                .replaceAll("-----BEGIN [A-Z ]+-----|-----END [A-Z ]+-----|\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    private PublicKey wechatPlatformPublicKey() throws Exception {
        String pem = Files.readString(Path.of(wechatPlatformPublicKeyPath), StandardCharsets.UTF_8);
        // WeChat Pay now issues a standalone platform public key (pub_key.pem)
        // in addition to the older platform certificate format. Accept both
        // formats so callback/API response verification does not silently fail
        // after a merchant switches to the public-key mode.
        if (pem.contains("-----BEGIN PUBLIC KEY-----")) {
            String encoded = pem.replaceAll("-----BEGIN [A-Z ]+-----|-----END [A-Z ]+-----|\\s", "");
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        }
        try (var input = Files.newInputStream(Path.of(wechatPlatformPublicKeyPath))) {
            X509Certificate certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
            return certificate.getPublicKey();
        }
    }

    private boolean validMerchantPrivateKey() {
        if (blank(wechatPrivateKeyPath) || !readableFile(wechatPrivateKeyPath)) return false;
        try { merchantPrivateKey(); return true; } catch (Exception ignored) { return false; }
    }

    private boolean validWechatPlatformPublicKey() {
        if (blank(wechatPlatformPublicKeyPath) || !readableFile(wechatPlatformPublicKeyPath)) return false;
        try { wechatPlatformPublicKey(); return true; } catch (Exception ignored) { return false; }
    }

    private String sign(String message, PrivateKey privateKey) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(message.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signer.sign());
    }

    /** Must be called inside the same transaction as handler processing. */
    private boolean claimCallback(String channel, String eventId, String rawBody) {
        for (int attempts = 0; attempts < 3; attempts++) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT processed FROM payment_callback_log WHERE channel=? AND provider_event_id=? FOR UPDATE", channel, eventId);
            if (!rows.isEmpty()) return !truthy(rows.get(0).get("processed"));
            try {
                jdbc.update("INSERT INTO payment_callback_log(channel,provider_event_id,payload_json,verified,processed) VALUES (?,?,?,1,0)",
                        channel, eventId, limit(rawBody, CALLBACK_BODY_MAX_BYTES));
                return true;
            } catch (DataIntegrityViolationException ignored) {
                // Concurrent callback delivery won the insert; acquire its row
                // on the next loop and process only if it was not committed.
            }
        }
        throw new IllegalStateException("支付回调并发处理状态未知，请让微信重试");
    }

    private void markCallbackProcessed(String channel, String eventId) {
        jdbc.update("UPDATE payment_callback_log SET processed=1 WHERE channel=? AND provider_event_id=?", channel, eventId);
    }

    private void creditConfirmedOrder(Map<String, Object> order, String providerOrderNo, String remark) {
        String orderNo = String.valueOf(order.get("order_no"));
        Long userId = toLong(order.get("user_id"));
        String productCode = String.valueOf(order.get("product_code"));
        int changed = jdbc.update("UPDATE payment_order SET status='paid',provider_order_no=?,paid_at=NOW() WHERE order_no=? AND status IN ('pending','manual_review')", providerOrderNo, orderNo);
        if (changed == 0) return;
        if (productCode.startsWith("sample_fee_")) {
            try {
                long requestId = Long.parseLong(productCode.substring("sample_fee_".length()));
                int linked = jdbc.update("UPDATE consumer_production_request SET sample_payment_status='paid',sample_paid_at=NOW(),status='processing' WHERE id=? AND sample_payment_order_no=? AND sample_payment_status IN ('pending','manual_review') AND status='approved'", requestId, orderNo);
                if (linked != 1) throw new IllegalStateException("打样支付订单未关联到可生产申请");
            } catch (NumberFormatException ignored) {
                throw new IllegalStateException("打样支付订单关联申请无效");
            }
            return;
        }
        BigDecimal credits = decimal(order.get("credit_amount"));
        ensureCreditAccount(userId);
        Map<String, Object> account = jdbc.queryForMap("SELECT balance FROM consumer_credit_account WHERE user_id=? FOR UPDATE", userId);
        BigDecimal before = decimal(account.get("balance"));
        BigDecimal after = before.add(credits);
        jdbc.update("UPDATE consumer_credit_account SET balance=?,total_recharged=total_recharged+? WHERE user_id=?", after, credits, userId);
        jdbc.update("INSERT INTO consumer_credit_transaction(transaction_no,user_id,biz_type,amount,direction,status,balance_before,balance_after,remark,operator) VALUES (?,?,?,?,?,?,?,?,?,?)",
                "PAY-" + orderNo, userId, "payment_recharge", credits, "in", "success", before, after, remark, providerOrderNo);
    }

    private Map<String, Object> orderViewForAdmin(String orderNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.order_no orderNo,p.user_id userId,p.product_name packageName,p.amount_fen amountFen,p.credit_amount credits,p.channel,p.status," +
                "p.provider_order_no providerOrderNo,p.paid_at paidAt,p.expired_at expiredAt,p.created_at createdAt,r.refund_no refundNo,r.status refundStatus,r.provider_refund_id providerRefundId " +
                "FROM payment_order p LEFT JOIN payment_refund r ON r.order_no=p.order_no WHERE p.order_no=?", orderNo);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付订单不存在");
        return toOrderView(rows.get(0));
    }

    private Map<String, Object> refundView(String refundNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT refund_no refundNo,order_no orderNo,user_id userId,amount_fen amountFen,credit_amount credits,status,reason," +
                "provider_refund_id providerRefundId,requested_at requestedAt,completed_at completedAt,updated_at updatedAt FROM payment_refund WHERE refund_no=?", refundNo);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "退款记录不存在");
        Map<String, Object> result = new LinkedHashMap<>(rows.get(0));
        result.put("amountYuan", fenToYuan(toLong(result.get("amountFen"))));
        return result;
    }

    private Map<String, Object> findOrderForUser(String orderNo, Long userId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT order_no,channel,status FROM payment_order WHERE order_no=? AND user_id=?", orderNo, userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付订单不存在");
        return rows.get(0);
    }

    private Map<String, Object> orderView(String orderNo, Long userId) {
        expireOverdueOrder(orderNo, userId);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT p.order_no orderNo,p.product_code packageCode,p.product_name packageName,p.amount_fen amountFen," +
                "p.credit_amount credits,p.channel,p.status,p.code_url codeUrl,p.provider_order_no providerOrderNo,p.paid_at paidAt,p.expired_at expiredAt,p.created_at createdAt," +
                "r.refund_no refundNo,r.status refundStatus FROM payment_order p LEFT JOIN payment_refund r ON r.order_no=p.order_no WHERE p.order_no=? AND p.user_id=?", orderNo, userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付订单不存在");
        // GET remains a read-only status endpoint: clients need the terminal
        // `expired` state in order to stop polling. Mutating endpoints enforce
        // the state transition separately.
        return toOrderView(rows.get(0));
    }

    private void expireOverdueOrders() {
        jdbc.update("UPDATE payment_order SET status='expired' WHERE channel='manual_wechat_qr' AND status IN ('pending','manual_review') AND expired_at IS NOT NULL AND expired_at<=CURRENT_TIMESTAMP");
    }

    private void expireOverdueOrders(Long userId) {
        jdbc.update("UPDATE payment_order SET status='expired' WHERE user_id=? AND channel='manual_wechat_qr' AND status IN ('pending','manual_review') AND expired_at IS NOT NULL AND expired_at<=CURRENT_TIMESTAMP", userId);
    }

    private void expireOverdueOrder(String orderNo) {
        jdbc.update("UPDATE payment_order SET status='expired' WHERE order_no=? AND channel='manual_wechat_qr' AND status IN ('pending','manual_review') AND expired_at IS NOT NULL AND expired_at<=CURRENT_TIMESTAMP", orderNo);
    }

    private void expireOverdueOrder(String orderNo, Long userId) {
        jdbc.update("UPDATE payment_order SET status='expired' WHERE order_no=? AND user_id=? AND channel='manual_wechat_qr' AND status IN ('pending','manual_review') AND expired_at IS NOT NULL AND expired_at<=CURRENT_TIMESTAMP", orderNo, userId);
    }

    private void throwUnavailableOrderState(String orderNo, Long userId, String fallback) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT status FROM payment_order WHERE order_no=? AND user_id=?", orderNo, userId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "支付订单不存在");
        String status = String.valueOf(rows.get(0).get("status"));
        if ("expired".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已过期，请重新创建充值订单");
        if ("closed".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已关闭，不能继续操作");
        if ("paid".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "订单已支付，无需重复操作");
        throw new ResponseStatusException(HttpStatus.CONFLICT, fallback);
    }

    private Map<String, Object> toOrderView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>(row);
        Object amountFen = result.get("amountFen");
        if (amountFen != null) result.put("amountYuan", fenToYuan(toLong(amountFen)));
        String status = String.valueOf(result.get("status"));
        result.put("expired", "expired".equals(status));
        result.put("canManualComplete", "pending".equals(status) && "manual_wechat_qr".equals(result.get("channel")));
        result.put("canClose", "pending".equals(status));
        return result;
    }

    private void markPaymentException(String orderNo, String detail) {
        jdbc.update("UPDATE payment_order SET status='payment_exception',provider_response=? WHERE order_no=? AND status<>'paid' AND status NOT LIKE 'refund%'", limit(detail, 3500), orderNo);
    }

    private Long requireConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"user".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅C端用户可发起充值");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user'", Integer.class, principal.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        return principal.userId();
    }

    private void requireAdmin(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"admin".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可执行此操作");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='admin'", Integer.class, principal.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
    }

    private void ensureCreditAccount(Long userId) {
        jdbc.update("INSERT IGNORE INTO consumer_credit_account(user_id,balance,frozen_balance,total_recharged,total_consumed) VALUES (?,0,0,0,0)", userId);
    }

    private CreditPackage packageFor(String code) {
        return PACKAGES.stream().filter(p -> p.code.equals(code)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "充值套餐不存在"));
    }

    private boolean wechatMerchantReady() {
        return wechatEnabled && !blank(wechatAppId) && !blank(wechatMchId) && !blank(wechatSerialNo)
                && !blank(wechatPrivateKeyPath) && !blank(wechatApiV3Key)
                && !blank(wechatNotifyUrl) && wechatNotifyUrl.startsWith("https://");
    }

    private boolean wechatPaymentReady() {
        return wechatMerchantReady() && !blank(wechatPlatformPublicKeyPath) && !blank(wechatPlatformSerialNo)
                && validMerchantPrivateKey() && validWechatPlatformPublicKey()
                && wechatApiV3Key != null && wechatApiV3Key.getBytes(StandardCharsets.UTF_8).length == 32;
    }

    private boolean wechatJsapiReady() {
        return wechatPaymentReady() && !blank(wechatMiniAppSecret);
    }

    private boolean wechatRefundReady() {
        return wechatPaymentReady() && !blank(wechatRefundNotifyUrl) && wechatRefundNotifyUrl.startsWith("https://");
    }

    private void requireWechatPaymentReady() {
        if (!wechatPaymentReady()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信支付商户或回调验签配置尚未完成");
    }

    private void requireWechatJsapiReady() {
        if (!wechatJsapiReady()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信小程序支付配置尚未完成");
    }

    private void requireWechatRefundReady() {
        if (!wechatRefundReady()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "微信退款商户或 HTTPS 回调配置尚未完成");
    }

    private boolean manualWechatQrReady() {
        if (!manualWechatQrEnabled) return false;
        if (blank(manualWechatQrUrl)) return false;
        String value = manualWechatQrUrl.trim();
        if (value.startsWith("https://")) return true;
        if (value.startsWith("http://")) return false;
        String classpathPath = value.startsWith("/") ? value.substring(1) : value;
        return new ClassPathResource("static/" + classpathPath).exists();
    }

    private boolean readableFile(String value) {
        try { return !blank(value) && Files.isRegularFile(Path.of(value)) && Files.isReadable(Path.of(value)); }
        catch (Exception ignored) { return false; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) { return value instanceof Map ? (Map<String, Object>) value : Map.of(); }
    private Object required(Map<String, Object> values, String key) { Object value = values.get(key); if (value == null) throw new IllegalArgumentException("缺少微信字段 " + key); return value; }
    private String requiredText(Map<String, Object> values, String key) { String value = nullableText(required(values, key)); if (blank(value)) throw new IllegalArgumentException("缺少微信字段 " + key); return value; }
    private String nullableText(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private long toLong(Object value) { try { return new BigDecimal(String.valueOf(value)).longValueExact(); } catch (Exception e) { throw new IllegalArgumentException("金额字段无效"); } }
    private BigDecimal decimal(Object value) { try { return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value)); } catch (Exception e) { throw new IllegalArgumentException("金额字段无效"); } }
    private HttpRequest.Builder httpRequest(URI uri, Duration timeout) {
        return HttpRequest.newBuilder(uri).timeout(timeout);
    }
    private String newOrderNo() { return newPaymentReference("PAY"); }
    private String newRefundNo() { return newPaymentReference("RFD"); }
    private String newPaymentReference(String prefix) {
        // 3-char prefix + base36 millisecond time + unsigned 64-bit random
        // suffix stays below WeChat's 32-character out_trade_no limit while
        // providing process-safe, cross-node collision resistance.
        String time = Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT);
        String random = Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36).toUpperCase(Locale.ROOT);
        return prefix + time + random;
    }
    private String randomNonce() { return UUID.randomUUID().toString().replace("-", ""); }
    private String wechatTimeExpire(String orderNo) {
        List<LocalDateTime> rows = jdbc.query("SELECT expired_at FROM payment_order WHERE order_no=?", (rs, rowNum) -> {
            Timestamp timestamp = rs.getTimestamp(1);
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }, orderNo);
        if (rows.isEmpty() || rows.get(0) == null) throw new IllegalStateException("支付订单缺少过期时间");
        return rows.get(0).atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    private LocalDateTime localDateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof java.util.Date date) return new Timestamp(date.getTime()).toLocalDateTime();
        if (value instanceof LocalDateTime dateTime) return dateTime;
        try { return value == null ? null : LocalDateTime.parse(String.valueOf(value).replace(' ', 'T')); }
        catch (Exception ignored) { return null; }
    }
    private String fenToYuan(long fen) { return BigDecimal.valueOf(fen, 2).toPlainString(); }
    private String compactJson(Object value) { try { return limit(mapper.writeValueAsString(value), 3500); } catch (Exception e) { return "微信支付响应已记录"; } }
    private String safeError(Exception e) { String value = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); return limit(value, 3500); }
    private String nullToEmpty(String value) { return value == null ? "" : value.trim(); }
    private String limit(String value, int max) { if (value == null) return ""; return value.length() <= max ? value : value.substring(0, max); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private boolean truthy(Object value) { return value instanceof Boolean ? (Boolean) value : value instanceof Number ? ((Number) value).intValue() != 0 : "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value)); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private void requireSafeOrderNo(String orderNo) { if (blank(orderNo) || !orderNo.matches("^[A-Za-z0-9_-]{1,64}$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单号格式无效"); }
    private Map<String, String> success(String message) { return Map.of("code", "SUCCESS", "message", message); }
    private ResponseStatusException userSafeWechatError(Exception error, String fallback) {
        if (error instanceof ResponseStatusException response) return response;
        if (error instanceof WechatApiException apiError
                && apiError.statusCode == 403
                && "NO_AUTH".equals(apiError.providerCode)) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "当前微信支付商户尚未开通该支付产品，请在微信支付商户平台开通对应权限后重试");
        }
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, fallback);
    }

    private boolean providerResultUnknown(Exception error) {
        if (error instanceof WechatApiException apiError) return !apiError.definitivelyRejected();
        // Signature failures, timeouts and local persistence errors after the
        // outbound request all require official reconciliation before reuse.
        return true;
    }

    private record CreditPackage(String code, String name, String description, long amountFen, BigDecimal credits) { }
    private record SampleOrderCreation(String orderNo, boolean reused, BigDecimal feeYuan, String productName, String channel) { }
    private record OrderCreation(String orderNo, boolean reused) { }
    private record RefundPreparation(String refundNo, String orderNo, Long userId, long amountFen, BigDecimal credits, String transactionId, String reason) { }
    private record DailyBillRetry(LocalDate billDate, String billType) { }
    private record BillRecord(String key, Long amountFen) { }
    private record ParsedBill(List<BillRecord> records, List<String> issues) { }
    private record VerifiedWechatNotification(String eventId, String eventType, Map<String, Object> resource) { }
    private static final class WechatApiException extends RuntimeException {
        final int statusCode;
        final String providerCode;
        WechatApiException(int statusCode, String providerCode, String message) { super(message); this.statusCode = statusCode; this.providerCode = providerCode == null ? "" : providerCode; }
        boolean resourceNotExists() {
            // Payment order queries use ORDER_NOT_EXIST, while some other
            // WeChat Pay v3 resources use RESOURCE_NOT_EXISTS. Both mean the
            // authenticated merchant conclusively has no provider-side trade.
            return statusCode == 404 && Set.of("RESOURCE_NOT_EXISTS", "ORDER_NOT_EXIST").contains(providerCode);
        }
        boolean definitivelyRejected() {
            // A generic 4xx/409 can mean the idempotent request was accepted
            // before the response was lost. Release a reservation only for a
            // documented, unambiguous parameter/resource rejection.
            return (statusCode == 400 && Set.of("PARAM_ERROR", "INVALID_REQUEST", "SIGN_ERROR", "NOAUTH", "APPID_MCHID_NOT_MATCH").contains(providerCode))
                    || resourceNotExists();
        }
    }
}
