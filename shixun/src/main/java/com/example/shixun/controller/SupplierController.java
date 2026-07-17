package com.example.shixun.controller;

import com.example.shixun.model.SupplierSearchRequest;
import com.example.shixun.model.SupplierSearchResult;
import com.example.shixun.model.SupplierStatisticsRequest;
import com.example.shixun.model.SupplierStatisticsResult;
import com.example.shixun.service.SupplierSearchToolService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suppliers")
@CrossOrigin(origins = "*")
public class SupplierController {
    private final JdbcTemplate jdbc;
    private final SupplierSearchToolService supplierSearch;

    public SupplierController(JdbcTemplate jdbc, SupplierSearchToolService supplierSearch) {
        this.jdbc = jdbc;
        this.supplierSearch = supplierSearch;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@RequestParam(required = false) String search) {
        String kw = search == null ? "" : search.trim();
        String sql = "SELECT id, receiver_no AS receiverNo, supplier, account_type AS accountType, " +
                "account_name AS accountName, bank_account AS bankAccount, bank, branch, location, note, " +
                "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt, DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt " +
                "FROM supplier_bank_accounts ";
        if (!kw.isBlank()) {
            String like = "%" + kw + "%";
            return ResponseEntity.ok(jdbc.queryForList(sql + "WHERE receiver_no LIKE ? OR supplier LIKE ? OR account_name LIKE ? OR bank_account LIKE ? OR bank LIKE ? OR branch LIKE ? OR location LIKE ? OR IFNULL(note,'') LIKE ? ORDER BY id DESC LIMIT 300",
                    like, like, like, like, like, like, like, like));
        }
        return ResponseEntity.ok(jdbc.queryForList(sql + "ORDER BY id DESC LIMIT 300"));
    }

    @PostMapping("/search")
    public ResponseEntity<SupplierSearchResult> search(@RequestBody(required = false) SupplierSearchRequest request) {
        return ResponseEntity.ok(supplierSearch.searchSuppliers(request));
    }

    @PostMapping("/statistics")
    public ResponseEntity<SupplierStatisticsResult> statistics(@RequestBody(required = false) SupplierStatisticsRequest request) {
        return ResponseEntity.ok(supplierSearch.getSupplierStatistics(request));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String receiverNo = str(body.get("receiverNo"));
        String supplier = str(body.get("supplier"));
        String accountType = defaultStr(body.get("accountType"), "对公账户");
        String accountName = str(body.get("accountName"));
        String bankAccount = str(body.get("bankAccount")).replace(" ", "");
        String bank = str(body.get("bank"));
        String branch = str(body.get("branch"));
        String location = str(body.get("location"));
        String note = str(body.get("note"));

        if (receiverNo.isBlank()) receiverNo = "SUP" + System.currentTimeMillis();
        if (supplier.isBlank() || accountName.isBlank() || bankAccount.isBlank() || bank.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "供应商、户名、银行账号、银行为必填项"));
        }
        try {
            jdbc.update("INSERT INTO supplier_bank_accounts (receiver_no, supplier, account_type, account_name, bank_account, bank, branch, location, note) VALUES (?,?,?,?,?,?,?,?,?)",
                    receiverNo, supplier, accountType, accountName, bankAccount, bank, branch, location, note.isBlank() ? null : note);
            Map<String, Object> row = jdbc.queryForMap("SELECT id, receiver_no AS receiverNo, supplier, account_type AS accountType, account_name AS accountName, bank_account AS bankAccount, bank, branch, location, note, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt, DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt FROM supplier_bank_accounts WHERE receiver_no=?", receiverNo);
            return ResponseEntity.ok(row);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "收方编号已存在，请换一个编号"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        int rows = jdbc.update("DELETE FROM supplier_bank_accounts WHERE id=?", id);
        return ResponseEntity.ok(Map.of("deleted", rows));
    }

    private String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private String defaultStr(Object v, String fallback) {
        String s = str(v);
        return s.isBlank() ? fallback : s;
    }
}
