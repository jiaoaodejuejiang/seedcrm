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
public class OrderTableInitializer {

    private static final String TABLE_NAME = "order_info";

    private final JdbcTemplate jdbcTemplate;

    public OrderTableInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        if (!tableExists()) {
            jdbcTemplate.execute(createTableSql());
            log.info("created table {}", TABLE_NAME);
        } else {
            ensureMissingColumns();
        }
        normalizeLegacyVerificationStates();
        normalizeLegacyPlanOrderStates();
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
        Map<String, String> columns = expectedColumns();
        for (Map.Entry<String, String> entry : columns.entrySet()) {
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

    private String createTableSql() {
        return """
                CREATE TABLE order_info (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    order_no VARCHAR(64) NOT NULL,
                    clue_id BIGINT,
                    customer_id BIGINT,
                    source_channel VARCHAR(32),
                    source_id BIGINT,
                    type INT NOT NULL,
                    amount DECIMAL(10,2),
                    deposit DECIMAL(10,2),
                    status VARCHAR(32) NOT NULL,
                    appointment_time DATETIME,
                    arrive_time DATETIME,
                    complete_time DATETIME,
                    remark VARCHAR(255),
                    service_detail_json TEXT,
                    verification_status VARCHAR(32),
                    verification_method VARCHAR(32),
                    verification_code VARCHAR(64),
                    verification_time DATETIME,
                    verification_operator_id BIGINT,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_order_no (order_no)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("order_no", "order_no VARCHAR(64) NOT NULL");
        columns.put("clue_id", "clue_id BIGINT");
        columns.put("customer_id", "customer_id BIGINT");
        columns.put("source_channel", "source_channel VARCHAR(32)");
        columns.put("source_id", "source_id BIGINT");
        columns.put("type", "type INT NOT NULL");
        columns.put("amount", "amount DECIMAL(10,2)");
        columns.put("deposit", "deposit DECIMAL(10,2)");
        columns.put("status", "status VARCHAR(32) NOT NULL");
        columns.put("appointment_time", "appointment_time DATETIME");
        columns.put("arrive_time", "arrive_time DATETIME");
        columns.put("complete_time", "complete_time DATETIME");
        columns.put("remark", "remark VARCHAR(255)");
        columns.put("service_detail_json", "service_detail_json TEXT");
        columns.put("verification_status", "verification_status VARCHAR(32)");
        columns.put("verification_method", "verification_method VARCHAR(32)");
        columns.put("verification_code", "verification_code VARCHAR(64)");
        columns.put("verification_time", "verification_time DATETIME");
        columns.put("verification_operator_id", "verification_operator_id BIGINT");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        columns.put("update_time", "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return columns;
    }

    private void normalizeLegacyVerificationStates() {
        int updated = jdbcTemplate.update("""
                UPDATE order_info
                SET verification_status = 'VERIFIED',
                    verification_method = COALESCE(NULLIF(verification_method, ''), 'LEGACY_BACKFILL'),
                    verification_code = COALESCE(NULLIF(verification_code, ''), CONCAT('LEGACY-', id)),
                    verification_time = COALESCE(verification_time, arrive_time, complete_time, update_time, create_time)
                WHERE (verification_status IS NULL OR verification_status = '' OR UPPER(verification_status) <> 'VERIFIED')
                  AND status IN ('ARRIVED', 'SERVING', 'COMPLETED')
                """);
        if (updated > 0) {
            log.info("normalized {} legacy service orders without verification state", updated);
        }
    }

    private void normalizeLegacyPlanOrderStates() {
        int updated = jdbcTemplate.update("""
                UPDATE order_info o
                JOIN plan_order p ON p.order_id = o.id
                SET o.status = CASE
                        WHEN p.finish_time IS NOT NULL OR p.status = 'FINISHED' THEN 'COMPLETED'
                        WHEN p.start_time IS NOT NULL OR p.status = 'SERVICING' THEN 'SERVING'
                        WHEN p.arrive_time IS NOT NULL OR p.status = 'ARRIVED' THEN 'ARRIVED'
                        ELSE o.status
                    END,
                    o.arrive_time = COALESCE(o.arrive_time, p.arrive_time),
                    o.complete_time = CASE
                        WHEN p.finish_time IS NOT NULL THEN COALESCE(o.complete_time, p.finish_time)
                        ELSE o.complete_time
                    END,
                    o.verification_status = CASE
                        WHEN p.arrive_time IS NOT NULL OR p.start_time IS NOT NULL OR p.finish_time IS NOT NULL
                            THEN 'VERIFIED'
                        ELSE o.verification_status
                    END,
                    o.verification_method = CASE
                        WHEN (p.arrive_time IS NOT NULL OR p.start_time IS NOT NULL OR p.finish_time IS NOT NULL)
                             AND (o.verification_method IS NULL OR o.verification_method = '')
                            THEN 'LEGACY_BACKFILL'
                        ELSE o.verification_method
                    END,
                    o.verification_code = CASE
                        WHEN (p.arrive_time IS NOT NULL OR p.start_time IS NOT NULL OR p.finish_time IS NOT NULL)
                             AND (o.verification_code IS NULL OR o.verification_code = '')
                            THEN CONCAT('LEGACY-', o.id)
                        ELSE o.verification_code
                    END,
                    o.verification_time = CASE
                        WHEN (p.arrive_time IS NOT NULL OR p.start_time IS NOT NULL OR p.finish_time IS NOT NULL)
                            THEN COALESCE(o.verification_time, p.arrive_time, p.start_time, p.finish_time, o.update_time, o.create_time)
                        ELSE o.verification_time
                    END
                WHERE (p.arrive_time IS NOT NULL OR p.start_time IS NOT NULL OR p.finish_time IS NOT NULL)
                  AND (
                      o.status NOT IN ('ARRIVED', 'SERVING', 'COMPLETED')
                      OR o.verification_status IS NULL
                      OR o.verification_status = ''
                      OR UPPER(o.verification_status) <> 'VERIFIED'
                      OR o.arrive_time IS NULL
                      OR (p.finish_time IS NOT NULL AND o.complete_time IS NULL)
                  )
                """);
        if (updated > 0) {
            log.info("normalized {} legacy plan-order rows with inconsistent order status", updated);
        }
    }
}
