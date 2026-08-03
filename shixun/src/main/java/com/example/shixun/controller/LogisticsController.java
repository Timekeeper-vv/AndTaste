package com.example.shixun.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/logistics")
public class LogisticsController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Value("${kuaidi100.customer:}")
    private String kuaidi100Customer;
    @Value("${kuaidi100.key:}")
    private String kuaidi100Key;
    @Value("${kuaidi100.callback-url:}")
    private String kuaidi100CallbackUrl;
    @Value("${kuaidi100.salt:}")
    private String kuaidi100Salt;

    public LogisticsController(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping("/provider-status")
    public Map<String, Object> providerStatus() {
        return Map.of(
                "provider", "kuaidi100",
                "queryConfigured", kuaidi100Configured(),
                "subscribeConfigured", kuaidi100Configured() && !blank(kuaidi100CallbackUrl) && !blank(kuaidi100Salt),
                "callbackSignatureConfigured", !blank(kuaidi100Salt),
                "callbackUrl", blank(kuaidi100CallbackUrl) ? "" : kuaidi100CallbackUrl,
                "message", !kuaidi100Configured() ? "未配置快递100 customer/key，不能查询真实物流"
                        : blank(kuaidi100Salt) ? "已配置查询，但未配置回调签名盐值，不能订阅物流推送" : "已配置快递100真实API"
        );
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return Map.of(
                "shipmentCount", count("logistics_shipment"),
                "inTransitCount", queryLong("SELECT COUNT(*) FROM logistics_shipment WHERE status IN ('shipped','in_transit','delivering')"),
                "signedCount", queryLong("SELECT COUNT(*) FROM logistics_shipment WHERE status='signed'"),
                "exceptionCount", queryLong("SELECT COUNT(*) FROM logistics_shipment WHERE status='exception' OR alert_level IN ('warning','exception')")
        );
    }

    @GetMapping("/carriers")
    public List<Map<String, Object>> carriers() {
        return jdbc.queryForList("SELECT code, name, provider, enabled FROM logistics_carrier WHERE enabled=1 ORDER BY sort_order, id");
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders() {
        return jdbc.queryForList("SELECT o.id,o.order_no orderNo,o.customer_name buyerName,o.contact_phone buyerPhone,o.total_amount payAmount,o.status orderStatus,o.order_type orderType,o.receiver_address receiverAddress,o.created_at createdAt FROM commercial_order o WHERE o.status IN ('ready_to_ship','shipped','completed') ORDER BY o.id DESC LIMIT 100");
    }

    @GetMapping("/shipments")
    public List<Map<String, Object>> shipments() {
        return jdbc.queryForList("SELECT s.id, s.shipment_no shipmentNo, s.order_id orderId, s.order_no orderNo, s.receiver_name receiverName, s.receiver_phone receiverPhone, s.receiver_address receiverAddress, s.carrier_code carrierCode, s.carrier_name carrierName, s.tracking_no trackingNo, s.status, s.latest_trace latestTrace, s.alert_level alertLevel, s.shipped_at shippedAt, s.signed_at signedAt, s.estimated_arrival estimatedArrival, s.created_at createdAt, s.updated_at updatedAt FROM logistics_shipment s ORDER BY s.id DESC");
    }

    @GetMapping("/shipments/{id}")
    public Map<String, Object> shipment(@PathVariable Long id) {
        Map<String, Object> s = jdbc.queryForMap("SELECT id, shipment_no shipmentNo, order_id orderId, order_no orderNo, receiver_name receiverName, receiver_phone receiverPhone, receiver_address receiverAddress, carrier_code carrierCode, carrier_name carrierName, tracking_no trackingNo, status, latest_trace latestTrace, alert_level alertLevel, shipped_at shippedAt, signed_at signedAt, estimated_arrival estimatedArrival, created_at createdAt, updated_at updatedAt FROM logistics_shipment WHERE id=?", id);
        s.put("traces", traces(id));
        s.put("suggestion", suggestion(str(s.get("status")), str(s.get("alertLevel"))));
        s.put("provider", "kuaidi100");
        return s;
    }

    @PostMapping("/shipments")
    public Map<String, Object> createShipment(@RequestBody ShipmentRequest req) throws Exception {
        if (blank(req.carrierCode) || blank(req.trackingNo)) throw new IllegalArgumentException("快递公司和快递单号不能为空");
        Map<String, Object> carrier = carrier(req.carrierCode);
        if (blank(req.orderNo) && req.orderId != null) {
            try { req.orderNo = jdbc.queryForObject("SELECT order_no FROM commercial_order WHERE id=?", String.class, req.orderId); } catch (Exception ignored) {}
        }
        if (!blank(req.orderNo)) {
            List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,contact_name,contact_phone,receiver_address,status FROM commercial_order WHERE order_no=?",req.orderNo);
            if (rows.isEmpty()) throw new IllegalArgumentException("订单号不存在，请从统一商业订单选择");
            Map<String,Object> o=rows.get(0); req.orderId=((Number)o.get("id")).longValue();
            if (blank(req.receiverName)) req.receiverName=str(o.get("contact_name")); if(blank(req.receiverPhone)) req.receiverPhone=str(o.get("contact_phone")); if(blank(req.receiverAddress)) req.receiverAddress=str(o.get("receiver_address"));
        }
        String shipmentNo = no("SHP");
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("INSERT INTO logistics_shipment (shipment_no,order_id,order_no,receiver_name,receiver_phone,receiver_address,carrier_code,carrier_name,tracking_no,status,latest_trace,alert_level,shipped_at,estimated_arrival) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, shipmentNo);
            if (req.orderId == null) ps.setNull(2, java.sql.Types.BIGINT); else ps.setLong(2, req.orderId);
            ps.setString(3, req.orderNo);
            ps.setString(4, blank(req.receiverName) ? "客户" : req.receiverName);
            ps.setString(5, req.receiverPhone);
            ps.setString(6, req.receiverAddress);
            ps.setString(7, req.carrierCode);
            ps.setString(8, str(carrier.get("name")));
            ps.setString(9, req.trackingNo.trim());
            ps.setString(10, "shipped");
            ps.setString(11, "已录入真实快递单号，待调用快递100查询/订阅");
            ps.setString(12, "normal");
            ps.setObject(13, LocalDateTime.now());
            ps.setObject(14, LocalDateTime.now().plusDays(3));
            return ps;
        }, kh);
        Long id = Objects.requireNonNull(kh.getKey()).longValue();
        insertTrace(id, "shipped", "发货录入", "已录入真实快递单号，等待承运商揽收或快递100返回轨迹");
        if (req.orderId != null) jdbc.update("UPDATE commercial_order SET status='shipped' WHERE id=?", req.orderId);
        if (kuaidi100Configured() && !blank(kuaidi100CallbackUrl)) {
            try { subscribeKuaidi100(id); } catch (Exception e) { insertTrace(id, "exception", "快递100订阅", "订阅失败：" + e.getMessage()); }
        }
        return shipment(id);
    }

    @PostMapping("/shipments/{id}/sync")
    public Map<String, Object> sync(@PathVariable Long id) throws Exception {
        Map<String, Object> s = jdbc.queryForMap("SELECT id, carrier_code carrierCode, tracking_no trackingNo, receiver_phone receiverPhone FROM logistics_shipment WHERE id=?", id);
        queryKuaidi100AndPersist(id, str(s.get("carrierCode")), str(s.get("trackingNo")), str(s.get("receiverPhone")));
        return shipment(id);
    }

    @PostMapping("/shipments/{id}/subscribe")
    public Map<String, Object> subscribe(@PathVariable Long id) throws Exception {
        subscribeKuaidi100(id);
        return shipment(id);
    }

    @PostMapping("/shipments/{id}/exception")
    public Map<String, Object> markException(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String reason = str(body.get("reason"));
        if (blank(reason)) reason = "物流长时间未更新，需要人工联系快递核实";
        insertTrace(id, "exception", "人工异常", reason);
        jdbc.update("UPDATE logistics_shipment SET status='exception', latest_trace=?, alert_level='exception' WHERE id=?", reason, id);
        return shipment(id);
    }

    @PostMapping("/callback/kuaidi100")
    public Map<String, Object> kuaidi100Callback(@RequestParam Map<String, String> form) {
        try {
            String param = form.get("param");
            String sign = form.get("sign");
            jdbc.update("INSERT INTO logistics_callback_log (provider, tracking_no, payload_json, handled) VALUES (?,?,?,0)", "kuaidi100", "", param == null ? "{}" : param);
            if (blank(param)) throw new IllegalArgumentException("回调param为空");
            // A callback is unauthenticated by design, therefore signing is mandatory.
            // Never accept a missing `sign` merely because a deployment forgot to configure it.
            if (blank(kuaidi100Salt)) throw new IllegalStateException("未配置快递100回调签名盐值，拒绝处理未验证回调");
            if (blank(sign)) throw new IllegalArgumentException("快递100回调缺少签名");
            String expected = md5(param + kuaidi100Salt).toUpperCase(Locale.ROOT);
            if (!expected.equalsIgnoreCase(sign)) throw new IllegalArgumentException("快递100回调签名验证失败");
            JsonNode root = mapper.readTree(param);
            JsonNode last = root.path("lastResult").isMissingNode() ? root : root.path("lastResult");
            String com = text(last, "com", text(root, "company", ""));
            String num = text(last, "nu", text(root, "number", ""));
            Long shipmentId = findShipmentId(com, num);
            if (shipmentId == null) throw new IllegalArgumentException("系统内未找到对应发货单：" + com + " / " + num);
            persistKuaidi100Result(shipmentId, last);
            jdbc.update("UPDATE logistics_callback_log SET tracking_no=?, handled=1 WHERE id=(SELECT id FROM (SELECT MAX(id) id FROM logistics_callback_log WHERE provider='kuaidi100') x)", num);
            return Map.of("result", true, "returnCode", "200", "message", "成功");
        } catch (Exception e) {
            try { jdbc.update("UPDATE logistics_callback_log SET error_message=? WHERE id=(SELECT id FROM (SELECT MAX(id) id FROM logistics_callback_log WHERE provider='kuaidi100') x)", e.getMessage()); } catch (Exception ignored) {}
            return Map.of("result", false, "returnCode", "500", "message", e.getMessage());
        }
    }

    private void queryKuaidi100AndPersist(Long shipmentId, String carrierCode, String trackingNo, String phone) throws Exception {
        if (!kuaidi100Configured()) throw new IllegalStateException("未配置快递100真实API。请在 shixun/application-local.properties 配置 kuaidi100.customer 和 kuaidi100.key");
        Map<String, Object> paramMap = new LinkedHashMap<>();
        paramMap.put("com", carrierCode);
        paramMap.put("num", trackingNo);
        if (!blank(phone) && ("shunfeng".equals(carrierCode) || "zhongtong".equals(carrierCode))) paramMap.put("phone", phone);
        paramMap.put("resultv2", "1");
        paramMap.put("show", "0");
        paramMap.put("order", "desc");
        String param = mapper.writeValueAsString(paramMap);
        String sign = md5(param + kuaidi100Key.trim() + kuaidi100Customer.trim()).toUpperCase(Locale.ROOT);
        String body = form(Map.of("customer", kuaidi100Customer.trim(), "sign", sign, "param", param));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://poll.kuaidi100.com/poll/query.do"))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("快递100 HTTP " + response.statusCode() + ": " + response.body());
        JsonNode root = mapper.readTree(response.body());
        String status = text(root, "status", "");
        if (!"200".equals(status)) throw new IllegalStateException("快递100查询失败：" + response.body());
        persistKuaidi100Result(shipmentId, root);
    }

    private void subscribeKuaidi100(Long shipmentId) throws Exception {
        if (!kuaidi100Configured()) throw new IllegalStateException("未配置快递100真实API customer/key");
        if (blank(kuaidi100CallbackUrl)) throw new IllegalStateException("未配置 kuaidi100.callback-url，无法订阅真实物流推送；该地址必须公网可访问");
        if (blank(kuaidi100Salt)) throw new IllegalStateException("未配置 kuaidi100.salt，出于安全原因不能订阅未签名的物流回调");
        Map<String, Object> s = jdbc.queryForMap("SELECT carrier_code carrierCode, tracking_no trackingNo, receiver_address receiverAddress FROM logistics_shipment WHERE id=?", shipmentId);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("callbackurl", kuaidi100CallbackUrl.trim());
        params.put("resultv2", "1");
        params.put("salt", kuaidi100Salt.trim());
        Map<String, Object> paramMap = new LinkedHashMap<>();
        paramMap.put("company", str(s.get("carrierCode")));
        paramMap.put("number", str(s.get("trackingNo")));
        paramMap.put("key", kuaidi100Key.trim());
        if (!blank(str(s.get("receiverAddress")))) paramMap.put("to", str(s.get("receiverAddress")));
        paramMap.put("parameters", params);
        String param = mapper.writeValueAsString(paramMap);
        String body = form(Map.of("schema", "json", "param", param));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://poll.kuaidi100.com/poll"))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IllegalStateException("快递100订阅 HTTP " + response.statusCode() + ": " + response.body());
        JsonNode root = mapper.readTree(response.body());
        if (!root.path("result").asBoolean(false)) throw new IllegalStateException("快递100订阅失败：" + response.body());
        insertTrace(shipmentId, "shipped", "快递100订阅", "已提交真实物流订阅，后续轨迹将由快递100回调推送");
        jdbc.update("UPDATE logistics_shipment SET latest_trace=? WHERE id=?", "已提交快递100真实物流订阅", shipmentId);
    }

    private void persistKuaidi100Result(Long shipmentId, JsonNode root) throws Exception {
        String status = mapKuaidi100State(text(root, "state", "0"));
        JsonNode data = root.path("data");
        if (data.isArray() && data.size() > 0) {
            for (JsonNode item : data) {
                String content = text(item, "context", text(item, "status", "物流轨迹更新"));
                String time = text(item, "ftime", text(item, "time", ""));
                String location = text(item, "areaName", text(item, "location", ""));
                if (!traceExists(shipmentId, content, time)) {
                    jdbc.update("INSERT INTO logistics_trace (shipment_id, trace_time, status, location, content, raw_json) VALUES (?,?,?,?,?,CAST(? AS JSON))",
                            shipmentId, parseKuaidiTime(time), status, location, content, mapper.writeValueAsString(item));
                }
            }
            JsonNode latest = data.get(0);
            String latestContent = text(latest, "context", text(latest, "status", "物流轨迹更新"));
            String alert = "exception".equals(status) ? "exception" : "normal";
            if ("signed".equals(status)) {
                jdbc.update("UPDATE logistics_shipment SET status=?, latest_trace=?, alert_level=?, signed_at=COALESCE(signed_at,NOW()) WHERE id=?", status, latestContent, alert, shipmentId);
                Object orderId = jdbc.queryForObject("SELECT order_id FROM logistics_shipment WHERE id=?", Object.class, shipmentId);
                if (orderId != null) jdbc.update("UPDATE commercial_order SET status='completed' WHERE id=?", orderId);
            } else {
                jdbc.update("UPDATE logistics_shipment SET status=?, latest_trace=?, alert_level=? WHERE id=?", status, latestContent, alert, shipmentId);
            }
        } else {
            String msg = text(root, "message", "快递100暂无轨迹，可能承运商还未揽收或单号暂未同步");
            insertTrace(shipmentId, "shipped", "快递100查询", msg);
            jdbc.update("UPDATE logistics_shipment SET latest_trace=? WHERE id=?", msg, shipmentId);
        }
    }

    private Long findShipmentId(String carrierCode, String trackingNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id FROM logistics_shipment WHERE tracking_no=? AND (carrier_code=? OR ?='') ORDER BY id DESC LIMIT 1", trackingNo, carrierCode, carrierCode);
        if (rows.isEmpty()) return null;
        return ((Number) rows.get(0).get("id")).longValue();
    }

    private boolean traceExists(Long shipmentId, String content, String time) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM logistics_trace WHERE shipment_id=? AND content=? AND DATE_FORMAT(trace_time,'%Y-%m-%d %H:%i:%s')=?", Integer.class, shipmentId, content, time);
        return c != null && c > 0;
    }

    private LocalDateTime parseKuaidiTime(String time) {
        if (blank(time)) return LocalDateTime.now();
        try { return LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception e) { return LocalDateTime.now(); }
    }

    private String mapKuaidi100State(String state) {
        return switch (state) {
            case "3" -> "signed";
            case "5" -> "delivering";
            case "2", "4", "6", "13", "14" -> "exception";
            default -> "in_transit";
        };
    }

    private List<Map<String, Object>> traces(Long shipmentId) {
        return jdbc.queryForList("SELECT id, shipment_id shipmentId, trace_time traceTime, status, location, content, created_at createdAt FROM logistics_trace WHERE shipment_id=? ORDER BY trace_time DESC, id DESC", shipmentId);
    }

    private Map<String, Object> carrier(String code) {
        List<Map<String, Object>> list = jdbc.queryForList("SELECT code, name, provider FROM logistics_carrier WHERE code=? AND enabled=1", code);
        if (!list.isEmpty()) return list.get(0);
        throw new IllegalArgumentException("不支持的快递公司编码：" + code + "。请使用快递100真实编码，例如 shunfeng、zhongtong、yuantong");
    }

    private void insertTrace(Long shipmentId, String status, String location, String content) {
        jdbc.update("INSERT INTO logistics_trace (shipment_id, trace_time, status, location, content) VALUES (?,?,?,?,?)", shipmentId, LocalDateTime.now(), status, location, content);
    }

    private String suggestion(String status, String alert) {
        if (!kuaidi100Configured()) return "真实物流API未配置：请先配置快递100 customer/key，否则不能同步真实轨迹。";
        if ("signed".equals(status)) return "已签收：可以自动完成订单，并引导客户评价或复购。";
        if ("exception".equals(status) || "exception".equals(alert)) return "异常件：建议客服联系快递核实，并主动告知客户处理进度。";
        if ("delivering".equals(status)) return "派送中：提醒客户保持电话畅通。";
        return "运输中：轨迹来自快递100真实API；同一单号不要频繁查询，建议间隔30分钟以上。";
    }

    private boolean kuaidi100Configured() { return !blank(kuaidi100Customer) && !blank(kuaidi100Key); }
    private String form(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
    private String md5(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] bytes = md.digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format("%02x", b));
        return hex.toString();
    }
    private String text(JsonNode n, String field, String def) { JsonNode v = n.path(field); return v.isMissingNode() || v.isNull() ? def : v.asText(def); }
    private long count(String table) { return queryLong("SELECT COUNT(*) FROM " + table); }
    private long queryLong(String sql) { Long v = jdbc.queryForObject(sql, Long.class); return v == null ? 0L : v; }
    private boolean blank(String s) { return s == null || s.trim().isEmpty(); }
    private String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private String no(String prefix) { return prefix + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int)(Math.random()*900+100); }

    public static class ShipmentRequest {
        public Long orderId;
        public String orderNo;
        public String receiverName;
        public String receiverPhone;
        public String receiverAddress;
        public String carrierCode;
        public String trackingNo;
    }
}
