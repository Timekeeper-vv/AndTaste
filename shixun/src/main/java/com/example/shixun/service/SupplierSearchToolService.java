package com.example.shixun.service;

import com.example.shixun.model.SupplierBankAccount;
import com.example.shixun.model.SupplierSearchRequest;
import com.example.shixun.model.SupplierSearchResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupplierSearchToolService {
    private final JdbcTemplate jdbc;

    public SupplierSearchToolService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * search_suppliers 工具函数。
     * 设计原则：LLM 只能传结构化参数；后端只做安全参数化查询，严禁拼接用户值为 SQL 片段。
     */
    public SupplierSearchResult searchSuppliers(SupplierSearchRequest rawRequest) {
        SupplierSearchRequest req = normalize(rawRequest);
        QueryParts query = buildQuery(req);
        boolean countOnly = Boolean.TRUE.equals(req.getCountOnly());

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM supplier_bank_accounts" + query.whereSql(), Long.class, query.args().toArray());
        if (total == null) total = 0L;

        Map<String, Object> queryParams = queryParams(req);
        if (countOnly) {
            // 为了让小窗口答案能“只给数量并简列名称”，这里额外返回名称，不返回账号等敏感明细。
            List<Object> nameArgs = new ArrayList<>(query.args());
            nameArgs.add(Math.min(req.getLimit(), 20));
            List<String> names = jdbc.queryForList("SELECT supplier FROM supplier_bank_accounts" + query.whereSql() + " ORDER BY id DESC LIMIT ?", String.class, nameArgs.toArray());
            return SupplierSearchResult.count(total, names, queryParams);
        }

        List<Object> listArgs = new ArrayList<>(query.args());
        listArgs.add(req.getLimit());
        List<SupplierBankAccount> items = jdbc.query("SELECT id, receiver_no, supplier, account_type, account_name, bank_account, bank, branch, location, note " +
                        "FROM supplier_bank_accounts" + query.whereSql() + " ORDER BY id DESC LIMIT ?",
                (rs, i) -> new SupplierBankAccount(
                        rs.getLong("id"),
                        rs.getString("receiver_no"),
                        rs.getString("supplier"),
                        rs.getString("account_type"),
                        rs.getString("account_name"),
                        rs.getString("bank_account"),
                        rs.getString("bank"),
                        rs.getString("branch"),
                        rs.getString("location"),
                        rs.getString("note")
                ), listArgs.toArray());
        return SupplierSearchResult.list(total, items, queryParams);
    }

    SupplierSearchRequest normalize(SupplierSearchRequest raw) {
        SupplierSearchRequest req = raw == null ? new SupplierSearchRequest() : raw;
        String region = safeText(req.getRegion());
        String keyword = safeText(req.getKeyword());
        String bankName = safeText(req.getBankName());
        boolean countOnly = Boolean.TRUE.equals(req.getCountOnly());
        int limit = req.getLimit() == null ? 20 : req.getLimit();
        if (limit < 1) limit = 20;
        if (limit > 100) limit = 100;
        return new SupplierSearchRequest(region, keyword, bankName, countOnly, limit);
    }

    QueryParts buildQuery(SupplierSearchRequest req) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (!req.getRegion().isBlank()) {
            where.append(" AND (location LIKE ? OR branch LIKE ?)");
            String like = like(req.getRegion());
            args.add(like);
            args.add(like);
        }

        if (!req.getKeyword().isBlank()) {
            where.append(" AND (supplier LIKE ? OR account_name LIKE ? OR branch LIKE ? OR location LIKE ? OR IFNULL(note,'') LIKE ?)");
            String like = like(req.getKeyword());
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }

        if (!req.getBankName().isBlank()) {
            where.append(" AND (bank LIKE ? OR branch LIKE ? OR bank LIKE ? OR branch LIKE ?)");
            String direct = like(req.getBankName());
            String subsequence = subsequenceLike(req.getBankName());
            args.add(direct);
            args.add(direct);
            args.add(subsequence);
            args.add(subsequence);
        }

        return new QueryParts(where.toString(), args);
    }

    private Map<String, Object> queryParams(SupplierSearchRequest req) {
        Map<String, Object> params = new LinkedHashMap<>();
        if (!req.getRegion().isBlank()) params.put("region", req.getRegion());
        if (!req.getKeyword().isBlank()) params.put("keyword", req.getKeyword());
        if (!req.getBankName().isBlank()) params.put("bank_name", req.getBankName());
        params.put("is_count_only", Boolean.TRUE.equals(req.getCountOnly()));
        params.put("limit", req.getLimit());
        return params;
    }

    private String safeText(String value) {
        if (value == null) return "";
        String s = value.trim();
        if (s.length() > 80) s = s.substring(0, 80);
        // 控制字符不参与查询；SQL 注入由 PreparedStatement 占位符防护。
        return s.replaceAll("[\\p{Cntrl}]", "");
    }

    private String like(String value) {
        return "%" + value + "%";
    }

    /**
     * 通用简称匹配：如“建行”生成“%建%行%”，可匹配“中国建设银行”。
     * 该逻辑不依赖具体银行白名单，避免硬编码别名字典。
     */
    private String subsequenceLike(String value) {
        StringBuilder sb = new StringBuilder("%");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c).append('%');
        }
        return sb.toString();
    }

    record QueryParts(String whereSql, List<Object> args) {}
}
