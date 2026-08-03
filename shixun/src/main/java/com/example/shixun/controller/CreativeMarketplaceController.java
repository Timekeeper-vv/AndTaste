package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/creative")
public class CreativeMarketplaceController {
    private final JdbcTemplate jdbc;

    public CreativeMarketplaceController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        requireMarketplaceAdmin(principal);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("artworkCount", count("artwork"));
        data.put("skuCount", count("product_sku"));
        data.put("designerCount", count("designer_profile"));
        data.put("orderCount", count("`order`"));
        data.put("revenue", jdbc.queryForObject("SELECT COALESCE(SUM(pay_amount),0) FROM `order` WHERE order_status IN ('paid','producing','shipped','completed')", BigDecimal.class));
        data.put("hotArtworks", jdbc.queryForList(
                "SELECT a.id, a.title, a.subtitle, a.image_url imageUrl, a.view_count viewCount, a.favorite_count favoriteCount, " +
                "c.name categoryName, d.brand_name designerName " +
                "FROM artwork a LEFT JOIN category c ON a.category_id=c.id LEFT JOIN designer_profile d ON a.designer_id=d.id " +
                "WHERE a.audit_status='approved' ORDER BY a.favorite_count DESC, a.view_count DESC LIMIT 5"));
        data.put("latestOrders", jdbc.queryForList(
                "SELECT id, order_no orderNo, total_amount totalAmount, pay_amount payAmount, order_status orderStatus, created_at createdAt " +
                "FROM `order` ORDER BY id DESC LIMIT 5"));
        return data;
    }

    @GetMapping("/categories")
    public List<Map<String, Object>> categories() {
        return jdbc.queryForList("SELECT id, name, description, sort_order sortOrder FROM category WHERE enabled=1 ORDER BY sort_order, id");
    }

    @GetMapping("/tags")
    public List<Map<String, Object>> tags() {
        return jdbc.queryForList("SELECT id, name FROM tag ORDER BY id");
    }

    @GetMapping("/artworks")
    public List<Map<String, Object>> artworks(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Long categoryId) {
        StringBuilder sql = new StringBuilder(
                "SELECT a.id, a.title, a.subtitle, a.image_url imageUrl, a.thumbnail_url thumbnailUrl, a.story, " +
                "a.license_type licenseType, a.sale_status saleStatus, a.view_count viewCount, a.favorite_count favoriteCount, " +
                "c.id categoryId, c.name categoryName, d.id designerId, d.brand_name designerName, " +
                "(SELECT MIN(price) FROM product_sku s WHERE s.artwork_id=a.id AND s.status='on_sale') minPrice, " +
                "(SELECT COUNT(*) FROM product_sku s WHERE s.artwork_id=a.id) skuCount " +
                "FROM artwork a LEFT JOIN category c ON a.category_id=c.id LEFT JOIN designer_profile d ON a.designer_id=d.id " +
                "WHERE a.audit_status='approved'");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (a.title LIKE ? OR a.subtitle LIKE ? OR a.story LIKE ?)");
            String k = "%" + keyword.trim() + "%";
            args.add(k); args.add(k); args.add(k);
        }
        if (categoryId != null) {
            sql.append(" AND a.category_id=?");
            args.add(categoryId);
        }
        sql.append(" ORDER BY a.id DESC");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    @GetMapping("/artworks/{id}")
    public Map<String, Object> artworkDetail(@PathVariable Long id) {
        // 详情页同列表页一样只暴露审核通过的作品。此前只要猜中 ID 就可能读到
        // 尚未审核的后台草稿；浏览量也只能在成功读取公开作品后才增加。
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.id, a.title, a.subtitle, a.image_url imageUrl, a.thumbnail_url thumbnailUrl, a.story, " +
                "a.license_type licenseType, a.sale_status saleStatus, a.view_count viewCount, a.favorite_count favoriteCount, " +
                "c.id categoryId, c.name categoryName, d.id designerId, d.brand_name designerName, d.bio designerBio " +
                "FROM artwork a LEFT JOIN category c ON a.category_id=c.id LEFT JOIN designer_profile d ON a.designer_id=d.id " +
                "WHERE a.id=? AND a.audit_status='approved'", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "作品不存在或尚未上架");
        jdbc.update("UPDATE artwork SET view_count=view_count+1 WHERE id=?", id);
        Map<String, Object> artwork = rows.get(0);
        artwork.put("tags", jdbc.queryForList(
                "SELECT t.id, t.name FROM tag t JOIN artwork_tag at ON t.id=at.tag_id WHERE at.artwork_id=? ORDER BY t.id", id));
        artwork.put("skus", skus(id));
        return artwork;
    }

    @GetMapping("/skus")
    public List<Map<String, Object>> skus(@RequestParam(required = false) Long artworkId) {
        if (artworkId != null) {
            return jdbc.queryForList(
                    "SELECT s.id, s.artwork_id artworkId, s.sku_code skuCode, s.product_name productName, s.product_type productType, " +
                    "s.cover_url coverUrl, s.price, s.original_price originalPrice, s.stock, s.material, s.size, s.status, " +
                    "a.title artworkTitle, d.brand_name designerName " +
                    "FROM product_sku s JOIN artwork a ON s.artwork_id=a.id LEFT JOIN designer_profile d ON a.designer_id=d.id " +
                    "WHERE s.artwork_id=? ORDER BY s.id", artworkId);
        }
        return jdbc.queryForList(
                "SELECT s.id, s.artwork_id artworkId, s.sku_code skuCode, s.product_name productName, s.product_type productType, " +
                "s.cover_url coverUrl, s.price, s.original_price originalPrice, s.stock, s.material, s.size, s.status, " +
                "a.title artworkTitle, d.brand_name designerName " +
                "FROM product_sku s JOIN artwork a ON s.artwork_id=a.id LEFT JOIN designer_profile d ON a.designer_id=d.id ORDER BY s.id DESC");
    }

    @GetMapping("/orders")
    public List<Map<String, Object>> orders(@RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal) {
        boolean staff = isMarketplaceStaff(principal);
        Long ownPlatformUserId = staff ? null : requireMarketplaceConsumer(principal);
        String sql = "SELECT o.id, o.order_no orderNo, o.user_id userId, u.display_name buyerName, o.total_amount totalAmount, " +
                "o.pay_amount payAmount, o.payment_method paymentMethod, o.order_status orderStatus, o.remark, o.created_at createdAt " +
                "FROM `order` o LEFT JOIN platform_user u ON o.user_id=u.id " +
                (staff ? "ORDER BY o.id DESC" : "WHERE o.user_id=? ORDER BY o.id DESC");
        List<Map<String, Object>> orders = staff ? jdbc.queryForList(sql) : jdbc.queryForList(sql, ownPlatformUserId);
        for (Map<String, Object> order : orders) {
            order.put("items", jdbc.queryForList(
                    "SELECT id, sku_id skuId, artwork_id artworkId, product_name productName, artwork_title artworkTitle, " +
                    "cover_url coverUrl, unit_price unitPrice, quantity, subtotal FROM order_item WHERE order_id=?", order.get("id")));
        }
        return orders;
    }

    @PostMapping("/orders")
    @Transactional
    public Map<String, Object> createOrder(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @RequestBody CreateOrderRequest request) {
        Long userId = requireMarketplaceConsumer(principal);
        if (request == null || request.items == null || request.items.isEmpty()) {
            throw new IllegalArgumentException("订单至少需要一个商品");
        }
        if (request.userId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单用户必须使用当前登录身份，不能通过请求体指定 userId");
        }
        String paymentMethod = request.paymentMethod == null ? "" : request.paymentMethod.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("wechat", "manual_wechat_qr").contains(paymentMethod)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "暂不支持该支付方式；禁止使用模拟支付");
        }
        String orderNo = "AT" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + (int)(Math.random() * 900 + 100);

        List<OrderLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderItem item : request.items) {
            if (item == null || item.skuId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "订单商品缺少 SKU");
            }
            int qty = item.quantity == null ? 1 : item.quantity;
            if (qty <= 0 || qty > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "单个商品数量必须在1到100之间");
            }
            List<Map<String, Object>> skuRows = jdbc.queryForList(
                    "SELECT s.*, a.title artwork_title, a.id artwork_id, a.designer_id, d.revenue_share " +
                    "FROM product_sku s JOIN artwork a ON s.artwork_id=a.id LEFT JOIN designer_profile d ON a.designer_id=d.id " +
                    "WHERE s.id=? AND s.status='on_sale' AND s.stock>=0 AND a.audit_status='approved' AND a.sale_status='on_sale' FOR UPDATE", item.skuId);
            if (skuRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在、已下架或暂不可售");
            Map<String, Object> sku = skuRows.get(0);
            Number stock = (Number) sku.get("stock");
            if (stock == null || stock.intValue() < qty) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "商品库存不足");
            }
            BigDecimal price = decimalValue(sku.get("price"));
            if (price == null || price.signum() < 0) throw new IllegalStateException("商品价格配置无效");
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
            total = total.add(subtotal);
            lines.add(new OrderLine(sku, qty, subtotal));
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        BigDecimal finalTotal = total;
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO `order` (order_no, user_id, total_amount, pay_amount, payment_method, order_status, remark) VALUES (?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, orderNo);
            ps.setLong(2, userId);
            ps.setBigDecimal(3, finalTotal);
            ps.setBigDecimal(4, finalTotal);
            ps.setString(5, paymentMethod);
            ps.setString(6, "pending_pay");
            ps.setString(7, request.remark);
            return ps;
        }, keyHolder);
        Long orderId = Objects.requireNonNull(keyHolder.getKey()).longValue();

        for (OrderLine line : lines) {
            Map<String, Object> sku = line.sku;
            BigDecimal share = decimalValue(sku.get("revenue_share"));
            if (share == null || share.signum() < 0 || share.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalStateException("设计师分成配置无效");
            }
            BigDecimal designerRevenue = line.subtotal.multiply(share).divide(BigDecimal.valueOf(100));
            jdbc.update("INSERT INTO order_item (order_id, sku_id, artwork_id, product_name, artwork_title, cover_url, unit_price, quantity, subtotal, designer_id, designer_revenue) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    orderId, sku.get("id"), sku.get("artwork_id"), sku.get("product_name"), sku.get("artwork_title"), sku.get("cover_url"), sku.get("price"), line.quantity, line.subtotal, sku.get("designer_id"), designerRevenue);
            int changed = jdbc.update("UPDATE product_sku SET stock = stock - ? WHERE id=? AND status='on_sale' AND stock>=?", line.quantity, sku.get("id"), line.quantity);
            if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "商品库存不足，请刷新后重试");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", orderId);
        result.put("orderNo", orderNo);
        result.put("payAmount", total);
        result.put("orderStatus", "pending_pay");
        result.put("paymentRequired", true);
        return result;
    }

    @GetMapping("/designers")
    public List<Map<String, Object>> designers() {
        return jdbc.queryForList(
                "SELECT d.id, d.brand_name brandName, d.bio, d.revenue_share revenueShare, d.audit_status auditStatus, " +
                "u.display_name displayName, u.avatar_url avatarUrl, " +
                "(SELECT COUNT(*) FROM artwork a WHERE a.designer_id=d.id) artworkCount " +
                "FROM designer_profile d JOIN platform_user u ON d.user_id=u.id ORDER BY d.id DESC");
    }

    private Long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException e) { return null; }
    }

    /** Returns true only for a currently persisted back-office operator. */
    private boolean isMarketplaceStaff(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!Set.of("admin", "technician", "feeder").contains(principal.role())) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role=?", Integer.class,
                principal.userId(), principal.role());
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        }
        return true;
    }

    private void requireMarketplaceAdmin(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!"admin".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可访问商城运营数据");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='admin'", Integer.class, principal.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
    }

    private Long requireMarketplaceConsumer(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!"user".equals(principal.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅C端用户可创建商城订单");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='user'", Integer.class, principal.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
        try {
            List<Long> mappings = jdbc.query(
                    "SELECT p.id FROM user_platform_identity i JOIN platform_user p ON p.id=i.platform_user_id " +
                            "WHERE i.user_id=? AND p.role='consumer' AND p.status='active'",
                    (rs, rowNum) -> rs.getLong(1), principal.userId());
            if (mappings.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "商城用户身份尚未同步，请重新登录后重试");
            return mappings.get(0);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "商城用户身份尚未同步，请重新登录后重试");
        }
    }

    public static class CreateOrderRequest {
        public Long userId;
        public String paymentMethod;
        public String remark;
        public List<CreateOrderItem> items;
    }

    public static class CreateOrderItem {
        public Long skuId;
        public Integer quantity;
    }

    private static class OrderLine {
        final Map<String, Object> sku;
        final int quantity;
        final BigDecimal subtotal;
        OrderLine(Map<String, Object> sku, int quantity, BigDecimal subtotal) {
            this.sku = sku;
            this.quantity = quantity;
            this.subtotal = subtotal;
        }
    }
}
