package com.seedcrm.crm.order.config;

import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderRefundRecordTableInitializer {

    private static final String TABLE_NAME = "order_refund_record";

    private final JdbcTemplate jdbcTemplate;

    public OrderRefundRecordTableInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        if (!tableExists()) {
            jdbcTemplate.execute(createTableSql());
            log.info("created table {}", TABLE_NAME);
            return;
        }
        ensureMissingColumns();
        ensureIndex("idx_refund_order_scene", "CREATE INDEX idx_refund_order_scene ON order_refund_record(order_id, refund_scene)");
        ensureIndex("uk_refund_idempotency", "CREATE UNIQUE INDEX uk_refund_idempotency ON order_refund_record(idempotency_key)");
    }

    private boolean tableExists() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, TABLE_NAME);
        return count != null && count > 0;
    }

    private void ensureMissingColumns() {
        for (Map.Entry<String, String> entry : expectedColumns().entrySet()) {
            if (!columnExists(entry.getKey())) {
                jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + entry.getValue());
                log.info("added missing column {}.{}", TABLE_NAME, entry.getKey());
            }
        }
    }

    private boolean columnExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, TABLE_NAME, columnName);
        return count != null && count > 0;
    }

    private void ensureIndex(String indexName, String createSql) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(createSql);
            log.info("created index {} on {}", indexName, TABLE_NAME);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE order_refund_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    order_id BIGINT NOT NULL,
                    plan_order_id BIGINT,
                    refund_scene VARCHAR(48) NOT NULL,
                    refund_object VARCHAR(48) NOT NULL,
                    refund_amount DECIMAL(12,2) NOT NULL,
                    refund_reason_type VARCHAR(64),
                    refund_reason VARCHAR(255),
                    status VARCHAR(32) NOT NULL,
                    idempotency_key VARCHAR(128) NOT NULL,
                    out_order_no VARCHAR(128),
                    out_refund_no VARCHAR(128),
                    external_refund_id VARCHAR(128),
                    item_order_id VARCHAR(128),
                    notify_url VARCHAR(255),
                    platform_channel VARCHAR(64),
                    operator_user_id BIGINT,
                    reverse_store_performance TINYINT DEFAULT 0,
                    reverse_customer_service TINYINT DEFAULT 0,
                    reverse_distributor TINYINT DEFAULT 0,
                    raw_request TEXT,
                    raw_response TEXT,
                    raw_notify TEXT,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_refund_idempotency (idempotency_key),
                    KEY idx_refund_order_scene (order_id, refund_scene)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("order_id", "order_id BIGINT NOT NULL");
        columns.put("plan_order_id", "plan_order_id BIGINT");
        columns.put("refund_scene", "refund_scene VARCHAR(48) NOT NULL");
        columns.put("refund_object", "refund_object VARCHAR(48) NOT NULL");
        columns.put("refund_amount", "refund_amount DECIMAL(12,2) NOT NULL");
        columns.put("refund_reason_type", "refund_reason_type VARCHAR(64)");
        columns.put("refund_reason", "refund_reason VARCHAR(255)");
        columns.put("status", "status VARCHAR(32) NOT NULL");
        columns.put("idempotency_key", "idempotency_key VARCHAR(128) NOT NULL");
        columns.put("out_order_no", "out_order_no VARCHAR(128)");
        columns.put("out_refund_no", "out_refund_no VARCHAR(128)");
        columns.put("external_refund_id", "external_refund_id VARCHAR(128)");
        columns.put("item_order_id", "item_order_id VARCHAR(128)");
        columns.put("notify_url", "notify_url VARCHAR(255)");
        columns.put("platform_channel", "platform_channel VARCHAR(64)");
        columns.put("operator_user_id", "operator_user_id BIGINT");
        columns.put("reverse_store_performance", "reverse_store_performance TINYINT DEFAULT 0");
        columns.put("reverse_customer_service", "reverse_customer_service TINYINT DEFAULT 0");
        columns.put("reverse_distributor", "reverse_distributor TINYINT DEFAULT 0");
        columns.put("raw_request", "raw_request TEXT");
        columns.put("raw_response", "raw_response TEXT");
        columns.put("raw_notify", "raw_notify TEXT");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }
}
