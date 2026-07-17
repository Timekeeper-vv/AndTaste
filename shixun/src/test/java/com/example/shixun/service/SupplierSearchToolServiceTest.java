package com.example.shixun.service;

import com.example.shixun.model.SupplierBankAccount;
import com.example.shixun.model.SupplierSearchRequest;
import com.example.shixun.model.SupplierSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierSearchToolServiceTest {
    private SupplierSearchToolService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:supplier_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP TABLE IF EXISTS supplier_bank_accounts");
        jdbc.execute("""
                CREATE TABLE supplier_bank_accounts (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    receiver_no VARCHAR(40),
                    supplier VARCHAR(200),
                    account_type VARCHAR(50),
                    account_name VARCHAR(200),
                    bank_account VARCHAR(80),
                    bank VARCHAR(200),
                    branch VARCHAR(200),
                    location VARCHAR(100),
                    note VARCHAR(1000)
                )
                """);
        insert(jdbc, "2025051500575", "深圳市星米三维科技有限公司", "深圳市星米三维科技有限公司", "755958121410901", "招商银行", "招商银行有限公司深圳布吉支行", "深圳市");
        insert(jdbc, "2024052000411", "广州恒生包装制品有限公司", "广州恒生包装制品有限公司", "3602864309100122837", "中国工商银行", "中国工商银行股份有限公司广州晓港支行", "广东省广州市");
        insert(jdbc, "2024060600426", "厚得（广东）生物科技有限公司", "厚得（广东）生物科技有限公司", "3602886609100274693", "中国工商银行股份有限公司", "广州增城开发区支行", "广东省广州市");
        insert(jdbc, "2024052000413", "东莞市宸宇包装有限公司", "东莞市宸宇包装有限公司", "44050177623800000656", "中国建设银行", "中国建设银行股份有限公司东莞东宝路支行", "广东省东莞市");
        insert(jdbc, "2024052000407", "青岛益美鑫包装科技有限公司", "青岛益美鑫包装科技有限公司", "37150198691000004147", "中国建设银行", "中国建设银行青岛中山路支行", "山东省青岛市");
        service = new SupplierSearchToolService(jdbc);
    }

    @Test
    void searchSuppliers_countByRegionGuangzhou() {
        SupplierSearchResult result = service.searchSuppliers(new SupplierSearchRequest("广州", null, null, true, 20));

        assertThat(result.getCount()).isEqualTo(2);
        assertThat(result.getNames()).containsExactlyInAnyOrder("广州恒生包装制品有限公司", "厚得（广东）生物科技有限公司");
        assertThat(result.getQueryParams()).containsEntry("region", "广州");
        assertThat(result.getQueryParams()).containsEntry("is_count_only", true);
    }

    @Test
    void searchSuppliers_filterByRegionAndKeyword() {
        SupplierSearchResult result = service.searchSuppliers(new SupplierSearchRequest("广东", "包装", null, false, 20));

        assertThat(result.getTotal()).isEqualTo(2);
        List<String> names = result.getItems().stream().map(SupplierBankAccount::getSupplier).toList();
        assertThat(names).containsExactlyInAnyOrder("广州恒生包装制品有限公司", "东莞市宸宇包装有限公司");
    }

    @Test
    void searchSuppliers_countByBankAbbreviation() {
        SupplierSearchResult result = service.searchSuppliers(new SupplierSearchRequest(null, null, "建行", true, 20));

        assertThat(result.getCount()).isEqualTo(2);
        assertThat(result.getNames()).containsExactlyInAnyOrder("东莞市宸宇包装有限公司", "青岛益美鑫包装科技有限公司");
    }

    @Test
    void searchSuppliers_findAccountByKeyword() {
        SupplierSearchResult result = service.searchSuppliers(new SupplierSearchRequest(null, "星米三维", null, false, 1));

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getSupplier()).isEqualTo("深圳市星米三维科技有限公司");
        assertThat(result.getItems().get(0).getBankAccount()).isEqualTo("755958121410901");
    }

    @Test
    void normalize_clampsLimitAndRemovesControlCharacters() {
        SupplierSearchRequest normalized = service.normalize(new SupplierSearchRequest("广州\n", "包装\t", "建行", false, 500));

        assertThat(normalized.getRegion()).isEqualTo("广州");
        assertThat(normalized.getKeyword()).isEqualTo("包装");
        assertThat(normalized.getLimit()).isEqualTo(100);
    }

    private void insert(JdbcTemplate jdbc, String receiverNo, String supplier, String accountName, String account,
                        String bank, String branch, String location) {
        jdbc.update("INSERT INTO supplier_bank_accounts (receiver_no, supplier, account_type, account_name, bank_account, bank, branch, location, note) VALUES (?,?,?,?,?,?,?,?,?)",
                receiverNo, supplier, "对公账户", accountName, account, bank, branch, location, null);
    }
}
