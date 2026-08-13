package com.example.shixun.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * Keeps the bundled historical-sales data available in every runtime.
 *
 * <p>Production deployment already imports these SQL files, but local Spring
 * Boot starts do not execute the deployment shell script.  Initializing here
 * prevents analytics and the consumer "爆款方向" panel from failing on a fresh
 * or developer database.  The data script is an idempotent upsert, so an
 * interrupted first import can safely be repaired on the next start.</p>
 */
@Component
public class HistoricalSalesDataInitializer implements InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(HistoricalSalesDataInitializer.class);
    private static final String IMPORT_BATCH = "2026-sales-20260804";
    private static final int EXPECTED_SOURCE_ROWS = 1696;
    private static final String SCHEMA_SCRIPT = "db/migration/V20260804_01__historical_sales_insights.sql";
    private static final String DATA_SCRIPT = "db/migration/V20260804_01__historical_sales_data.sql";

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public HistoricalSalesDataInitializer(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        executeScript(SCHEMA_SCRIPT);

        Integer importedRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM historical_sales_fact WHERE import_batch=?",
                Integer.class,
                IMPORT_BATCH
        );
        int currentRows = importedRows == null ? 0 : importedRows;
        if (currentRows < EXPECTED_SOURCE_ROWS) {
            log.info("Historical sales seed is incomplete ({} / {}); restoring bundled data", currentRows, EXPECTED_SOURCE_ROWS);
            executeScript(DATA_SCRIPT);
            Integer restoredRows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM historical_sales_fact WHERE import_batch=?",
                    Integer.class,
                    IMPORT_BATCH
            );
            if (restoredRows == null || restoredRows < EXPECTED_SOURCE_ROWS) {
                throw new IllegalStateException("历史销售初始化数据导入不完整，应用未启动");
            }
            log.info("Historical sales seed is ready: {} records", restoredRows);
        }
    }

    private void executeScript(String resourcePath) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.addScript(new ClassPathResource(resourcePath));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }
}
