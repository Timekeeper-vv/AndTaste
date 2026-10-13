package com.example.shixun.controller;

import com.example.shixun.service.CreativeProjectService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PaymentPackageCatalogTest {
    @Test
    void exposesTheCurrentPricingAndBonusCreditBreakdown() {
        PaymentController controller = new PaymentController(
                mock(JdbcTemplate.class), new ObjectMapper(),
                mock(PlatformTransactionManager.class), mock(CreativeProjectService.class));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> packages = (List<Map<String, Object>>) controller.packages().get("items");

        assertThat(packages).hasSize(5);
        assertThat(packages).extracting(item -> item.get("code"))
                .containsExactly("credit_1000", "credit_4000", "credit_10200", "credit_20700", "credit_52400");
        assertThat(packages).extracting(item -> item.get("amountFen"))
                .containsExactly(990L, 3900L, 9900L, 19900L, 49900L);
        assertThat(packages).extracting(item -> ((BigDecimal) item.get("credits")).intValueExact())
                .containsExactly(1000, 4000, 10200, 20700, 52400);
        assertThat(packages).extracting(item -> ((BigDecimal) item.get("baseCredits")).intValueExact())
                .containsExactly(990, 3900, 9900, 19900, 49900);
        assertThat(packages).extracting(item -> ((BigDecimal) item.get("bonusCredits")).intValueExact())
                .containsExactly(10, 100, 300, 800, 2500);
        assertThat(packages).extracting(item -> item.get("estimatedFlatImages"))
                .containsExactly(8, 33, 85, 172, 436);
    }
}
