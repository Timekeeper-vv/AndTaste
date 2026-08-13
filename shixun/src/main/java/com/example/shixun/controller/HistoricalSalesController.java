package com.example.shixun.controller;

import com.example.shixun.security.JwtAuthenticationFilter;
import com.example.shixun.security.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 管理端历史销售事实数据查询；该表只用于经营分析，不修改订单和库存。 */
@RestController
@RequestMapping("/api/analytics/historical-sales")
public class HistoricalSalesController {
    private final JdbcTemplate jdbc;

    public HistoricalSalesController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public Map<String, Object> list(
            @RequestAttribute(name = JwtAuthenticationFilter.AUTHENTICATED_CLAIMS_ATTRIBUTE, required = false) JwtService.Claims principal,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String productType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        requireAdmin(principal);
        int safePage = Math.max(1, page == null ? 1 : page);
        int safeSize = Math.max(1, Math.min(size == null ? 50 : size, 200));
        Filters filters = filters(year, projectName, productType, keyword);
        String where = filters.where();
        List<Object> args = filters.args();

        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM historical_sales_fact" + where, Integer.class, args.toArray());
        Map<String, Object> summary = jdbc.queryForMap("SELECT COALESCE(SUM(sales_ytd),0) sales, COALESCE(SUM(loss_ytd),0) loss, "
                + "COUNT(DISTINCT project_name) projects, COUNT(DISTINCT product_code) products FROM historical_sales_fact" + where,
                args.toArray());
        List<Object> itemArgs = new ArrayList<>(args);
        itemArgs.add(safeSize);
        itemArgs.add((safePage - 1) * safeSize);
        List<Map<String, Object>> items = jdbc.queryForList("SELECT id,report_year reportYear,project_name projectName,product_code productCode,"
                + "product_name productName,product_type productType,secondary_type secondaryType,sales_jan janSales,sales_feb febSales,"
                + "sales_mar marSales,sales_apr aprSales,sales_may maySales,sales_jun junSales,sales_jul julSales,sales_ytd sales,loss_ytd loss,"
                + "source_file sourceFile,source_sheet sourceSheet,source_row_no sourceRowNo,imported_at importedAt FROM historical_sales_fact"
                + where + " ORDER BY sales_ytd DESC,id ASC LIMIT ? OFFSET ?", itemArgs.toArray());
        List<Map<String, Object>> topProducts = jdbc.queryForList("SELECT product_name productName,project_name projectName,product_type productType,"
                + "secondary_type secondaryType,sales_ytd sales,loss_ytd loss FROM historical_sales_fact" + where
                + " ORDER BY sales_ytd DESC LIMIT 10", args.toArray());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("topProducts", topProducts);
        result.put("summary", summary);
        result.put("total", total == null ? 0 : total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("filters", Map.of("year", year == null ? "" : year, "projectName", filters.projectName(), "productType", filters.productType(), "keyword", filters.keyword()));
        result.put("source", "2026年销售数量.xlsx");
        return result;
    }

    private Filters filters(Integer year, String projectName, String productType, String keyword) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (year != null) { if (year < 2000 || year > 2100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "年份范围无效"); clauses.add("report_year=?"); args.add(year); }
        String project = clean(projectName), type = clean(productType), query = clean(keyword);
        if (!project.isEmpty()) { clauses.add("project_name=?"); args.add(project); }
        if (!type.isEmpty()) { clauses.add("product_type=?"); args.add(type); }
        if (!query.isEmpty()) { clauses.add("(product_name LIKE ? OR product_code LIKE ? OR project_name LIKE ?)"); String like = "%" + query + "%"; args.add(like); args.add(like); args.add(like); }
        return new Filters(clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses), args, project, type, query);
    }

    private String clean(String value) { return value == null ? "" : value.trim().substring(0, Math.min(value.trim().length(), 120)); }

    private void requireAdmin(JwtService.Claims principal) {
        if (principal == null || principal.userId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        if (!"admin".equals(principal.role())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅超级管理员可查看销售数据");
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM user WHERE id=? AND role='admin'", Integer.class, principal.userId());
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前登录身份已失效");
    }

    private record Filters(String where, List<Object> args, String projectName, String productType, String keyword) { }
}
