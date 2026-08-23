package com.example.shixun.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SampleLogisticsServiceTest {
    private JdbcTemplate jdbc;
    private SampleLogisticsService logistics;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:sample_logistics_" + System.nanoTime() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE user (id BIGINT PRIMARY KEY, username VARCHAR(80), role VARCHAR(20), status VARCHAR(20))");
        jdbc.execute("CREATE TABLE consumer_production_request (id BIGINT AUTO_INCREMENT PRIMARY KEY, request_no VARCHAR(80), user_id BIGINT, request_type VARCHAR(20), title VARCHAR(200), sample_product_name VARCHAR(120), status VARCHAR(30), sample_payment_status VARCHAR(24), sample_workflow_status VARCHAR(32), project_id BIGINT NULL, version_id BIGINT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_sample_logistics (id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT UNIQUE, user_id BIGINT, carrier_code VARCHAR(50), carrier_name VARCHAR(80), tracking_no VARCHAR(120) UNIQUE, status VARCHAR(30), latest_trace VARCHAR(1000), alert_level VARCHAR(20), alert_status VARCHAR(20), exception_note VARCHAR(2000), shipped_at TIMESTAMP NULL, signed_at TIMESTAMP NULL, estimated_arrival TIMESTAMP NULL, last_synced_at TIMESTAMP NULL, created_by BIGINT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_sample_logistics_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, logistics_id BIGINT, request_id BIGINT, event_type VARCHAR(40), status VARCHAR(30), alert_level VARCHAR(20), location VARCHAR(160), content VARCHAR(1000), payload_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE creative_sample_lifecycle_event (id BIGINT AUTO_INCREMENT PRIMARY KEY, request_id BIGINT, project_id BIGINT NULL, version_id BIGINT NULL, user_id BIGINT, event_type VARCHAR(32), comment VARCHAR(2000), payload_json CLOB, created_by BIGINT, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE logistics_carrier (code VARCHAR(50) PRIMARY KEY, name VARCHAR(80), enabled INT DEFAULT 1)");
        jdbc.update("INSERT INTO user (id,username,role,status) VALUES (7,'customer','user','active'),(9,'factory','feeder','active')");
        jdbc.update("INSERT INTO logistics_carrier (code,name,enabled) VALUES ('shunfeng','顺丰速运',1)");
        jdbc.update("INSERT INTO consumer_production_request (request_no,user_id,request_type,title,status,sample_payment_status,sample_workflow_status) VALUES ('CYP-1',7,'sample','凤凰抱枕','processing','not_required','ready_to_ship')");
        logistics = new SampleLogisticsService(jdbc, new ObjectMapper(), new CreativeProjectService(jdbc, new ObjectMapper()));
    }

    @Test
    void trackingUpdateCreatesConsumerProjectionAndTrace() {
        Map<String, Object> result = logistics.update(1L, 9L, Map.of("carrierCode", "shunfeng", "trackingNo", "SF12345678", "comment", "出样已寄出"));
        assertThat(result.get("status")).isEqualTo("shipped");
        assertThat(result.get("carrierName")).isEqualTo("顺丰速运");
        assertThat(result.get("trackingNo")).isEqualTo("SF12345678");
        assertThat((List<?>) result.get("traces")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT sample_workflow_status FROM consumer_production_request WHERE id=1", String.class)).isEqualTo("shipped");
    }

    @Test
    void exceptionIsVisibleToStaffAndCanBeResolved() {
        logistics.update(1L, 9L, Map.of("carrierCode", "shunfeng", "trackingNo", "SF22345678"));
        logistics.markException(1L, 9L, Map.of("exceptionNote", "地址无法识别"));
        assertThat(logistics.alerts(20)).hasSize(1);
        assertThat(logistics.forConsumer(1L, 7L).get("alertLevel")).isEqualTo("exception");

        Map<String, Object> resolved = logistics.resolveException(1L, 9L, Map.of("comment", "已联系快递修正地址"));
        assertThat(resolved.get("status")).isEqualTo("in_transit");
        assertThat(resolved.get("alertLevel")).isEqualTo("normal");
        assertThat(logistics.alerts(20)).isEmpty();
        assertThat((List<?>) resolved.get("traces")).hasSize(3);
    }

    @Test
    void consumerCannotReadAnotherUsersSample() {
        assertThatThrownBy(() -> logistics.forConsumer(1L, 99L))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void reviewOrUnpaidRequestCannotEnterShippingState() {
        jdbc.update("INSERT INTO consumer_production_request (request_no,user_id,request_type,title,status,sample_payment_status,sample_workflow_status) VALUES ('CYP-2',7,'sample','未付款样品','review','unpaid','ready_to_ship')");
        assertThatThrownBy(() -> logistics.update(2L, 9L, Map.of("carrierCode", "shunfeng", "trackingNo", "SF99999999")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("审核或付款");
    }

    @Test
    void newLogisticsRecordCannotStartAsSigned() {
        jdbc.update("INSERT INTO consumer_production_request (request_no,user_id,request_type,title,status,sample_payment_status,sample_workflow_status) VALUES ('CYP-2',7,'sample','已出样','processing','not_required','ready_to_ship')");
        assertThatThrownBy(() -> logistics.update(2L, 9L, Map.of(
                "carrierCode", "shunfeng", "trackingNo", "SF88888888", "status", "signed")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("新建物流记录");
    }
}
