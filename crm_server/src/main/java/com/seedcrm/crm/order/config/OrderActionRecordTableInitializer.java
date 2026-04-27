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
public class OrderActionRecordTableInitializer {

    private static final String TABLE_NAME = "order_action_record";
    private static final String INDEX_ORDER = "idx_order_action_record_order_id";
    private static final String INDEX_ACTION = "idx_order_action_record_action_type";

    private final JdbcTemplate jdbcTemplate;

    public OrderActionRecordTableInitializer(DataSource dataSource) {
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
        ensureIndexes();
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

    private void ensureIndexes() {
        ensureIndex(INDEX_ORDER, "ALTER TABLE " + TABLE_NAME
                + " ADD INDEX " + INDEX_ORDER + " (order_id)");
        ensureIndex(INDEX_ACTION, "ALTER TABLE " + TABLE_NAME
                + " ADD INDEX " + INDEX_ACTION + " (action_type)");
    }

    private void ensureIndex(String indexName, String sql) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, TABLE_NAME, indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(sql);
            log.info("added index {} on {}", indexName, TABLE_NAME);
        }
    }

    private String createTableSql() {
        return """
                CREATE TABLE order_action_record (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    order_id BIGINT NOT NULL,
                    action_type VARCHAR(64) NOT NULL,
                    from_status VARCHAR(32),
                    to_status VARCHAR(32),
                    operator_user_id BIGINT,
                    remark VARCHAR(500),
                    extra_json TEXT,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_order_action_record_order_id (order_id),
                    KEY idx_order_action_record_action_type (action_type)
                )
                """;
    }

    private Map<String, String> expectedColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id BIGINT PRIMARY KEY AUTO_INCREMENT");
        columns.put("order_id", "order_id BIGINT NOT NULL");
        columns.put("action_type", "action_type VARCHAR(64) NOT NULL");
        columns.put("from_status", "from_status VARCHAR(32)");
        columns.put("to_status", "to_status VARCHAR(32)");
        columns.put("operator_user_id", "operator_user_id BIGINT");
        columns.put("remark", "remark VARCHAR(500)");
        columns.put("extra_json", "extra_json TEXT");
        columns.put("create_time", "create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return columns;
    }
}
