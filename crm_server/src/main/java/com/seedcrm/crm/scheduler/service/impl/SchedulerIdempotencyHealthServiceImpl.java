package com.seedcrm.crm.scheduler.service.impl;

import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse.DuplicateGroup;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse.DuplicateLogSample;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse.IndexHealth;
import com.seedcrm.crm.scheduler.service.SchedulerIdempotencyHealthService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SchedulerIdempotencyHealthServiceImpl implements SchedulerIdempotencyHealthService {

    private static final String TABLE_NAME = "integration_callback_event_log";
    private static final String IDEMPOTENCY_INDEX = "uk_callback_provider_idempotency";
    private static final String EVENT_INDEX = "uk_callback_provider_event";

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public SchedulerIdempotencyHealthServiceImpl(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    SchedulerIdempotencyHealthServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SchedulerIdempotencyHealthResponse inspect(String providerCode) {
        String normalizedProviderCode = normalizeProvider(providerCode);
        SchedulerIdempotencyHealthResponse response = new SchedulerIdempotencyHealthResponse();
        response.setGeneratedAt(LocalDateTime.now());
        response.setTableName(TABLE_NAME);
        response.setProviderCode(normalizedProviderCode);

        if (!tableExists(TABLE_NAME)) {
            response.setHealthy(false);
            response.setStatus("MISSING_TABLE");
            response.setDuplicateGroups(List.of());
            response.setIndexes(List.of(missingTableIndexHealth(IDEMPOTENCY_INDEX, "provider_code, idempotency_key"),
                    missingTableIndexHealth(EVENT_INDEX, "provider_code, event_id")));
            response.setRecommendedActions(List.of("回调日志表不存在，请先完成调度中心数据库初始化。"));
            return response;
        }

        List<DuplicateGroup> duplicateGroups = new ArrayList<>();
        duplicateGroups.addAll(listDuplicateGroups("IDEMPOTENCY_KEY", "idempotency_key", normalizedProviderCode));
        duplicateGroups.addAll(listDuplicateGroups("EVENT_ID", "event_id", normalizedProviderCode));

        long idempotencyDuplicateGroups = countDuplicateGroups("idempotency_key", normalizedProviderCode);
        long eventDuplicateGroups = countDuplicateGroups("event_id", normalizedProviderCode);
        long idempotencyAffectedLogs = countAffectedLogs("idempotency_key", normalizedProviderCode);
        long eventAffectedLogs = countAffectedLogs("event_id", normalizedProviderCode);
        long duplicateGroupCount = idempotencyDuplicateGroups + eventDuplicateGroups;
        long affectedLogCount = idempotencyAffectedLogs + eventAffectedLogs;

        boolean idempotencyIndexExists = indexExists(TABLE_NAME, IDEMPOTENCY_INDEX);
        boolean eventIndexExists = indexExists(TABLE_NAME, EVENT_INDEX);
        response.setIndexes(List.of(
                indexHealth(IDEMPOTENCY_INDEX, "provider_code, idempotency_key", idempotencyIndexExists, idempotencyDuplicateGroups),
                indexHealth(EVENT_INDEX, "provider_code, event_id", eventIndexExists, eventDuplicateGroups)));
        response.setDuplicateGroups(duplicateGroups);
        response.setDuplicateGroupCount(duplicateGroupCount);
        response.setAffectedLogCount(affectedLogCount);

        boolean healthy = idempotencyIndexExists && eventIndexExists && duplicateGroupCount == 0;
        response.setHealthy(healthy);
        response.setStatus(healthy ? "HEALTHY" : duplicateGroupCount > 0 ? "DUPLICATE_DATA" : "INDEX_NOT_READY");
        response.setRecommendedActions(recommendations(response));
        return response;
    }

    private IndexHealth missingTableIndexHealth(String indexName, String columns) {
        IndexHealth item = new IndexHealth();
        item.setIndexName(indexName);
        item.setColumns(columns);
        item.setUnique(true);
        item.setExists(false);
        item.setStatus("MISSING_TABLE");
        item.setMessage("回调日志表不存在，无法检查唯一索引");
        return item;
    }

    private IndexHealth indexHealth(String indexName, String columns, boolean exists, long duplicateGroups) {
        IndexHealth item = new IndexHealth();
        item.setIndexName(indexName);
        item.setColumns(columns);
        item.setUnique(true);
        item.setExists(exists);
        if (exists) {
            item.setStatus("ACTIVE");
            item.setMessage("唯一索引已生效");
        } else if (duplicateGroups > 0) {
            item.setStatus("BLOCKED_BY_DUPLICATES");
            item.setMessage("存在历史重复回调日志，唯一索引暂未创建");
        } else {
            item.setStatus("READY_TO_CREATE");
            item.setMessage("当前未发现重复数据，重启服务或手工执行索引创建后可生效");
        }
        return item;
    }

    private List<String> recommendations(SchedulerIdempotencyHealthResponse response) {
        if (response.isHealthy()) {
            return List.of("幂等唯一索引已生效，当前未发现重复接口接收日志。");
        }
        List<String> actions = new ArrayList<>();
        if (response.getDuplicateGroupCount() > 0) {
            actions.add("存在历史重复接口接收日志，唯一索引暂未生效；上线前需完成数据治理或确认风险接受。");
            actions.add("先按下方重复分组治理历史接口接收日志，建议保留业务处理成功或最新一条记录。");
            actions.add("治理后重启服务，让初始化器自动创建唯一索引；或由 DBA 手工创建唯一索引。");
            actions.add("禁止直接删除相关业务 Customer / Order / PlanOrder 数据，避免破坏方案 B 主链路。");
        }
        boolean missingIndex = response.getIndexes().stream().anyMatch(index -> !index.isExists());
        if (missingIndex && response.getDuplicateGroupCount() == 0) {
            actions.add("当前没有重复数据但唯一索引未生效，建议重启服务或手工创建唯一索引。");
        }
        actions.add("在索引生效前，系统仍通过应用幂等、外部订单唯一键和异常队列兜底。");
        return actions;
    }

    private List<DuplicateGroup> listDuplicateGroups(String duplicateType, String keyColumn, String providerCode) {
        String sql = """
                SELECT provider_code,
                       %s AS duplicate_key,
                       COUNT(1) AS duplicate_count,
                       MIN(id) AS first_log_id,
                       MAX(id) AS latest_log_id,
                       MIN(received_at) AS first_received_at,
                       MAX(received_at) AS latest_received_at,
                       GROUP_CONCAT(id ORDER BY id DESC SEPARATOR ',') AS sample_log_ids,
                       GROUP_CONCAT(CONCAT_WS('|',
                           id,
                           COALESCE(trace_id, ''),
                           COALESCE(process_status, ''),
                           COALESCE(idempotency_status, ''),
                           COALESCE(body_hash, ''),
                           COALESCE(related_order_id, ''),
                           COALESCE(DATE_FORMAT(received_at, '%%Y-%%m-%%d %%H:%%i:%%s'), '')
                       ) ORDER BY id DESC SEPARATOR '\n') AS sample_log_rows,
                       GROUP_CONCAT(trace_id ORDER BY id DESC SEPARATOR ',') AS sample_trace_ids
                FROM %s
                WHERE %s IS NOT NULL AND %s <> ''
                %s
                GROUP BY provider_code, %s
                HAVING COUNT(1) > 1
                ORDER BY duplicate_count DESC, latest_log_id DESC
                LIMIT 20
                """.formatted(keyColumn, TABLE_NAME, keyColumn, keyColumn, providerFilterSql(providerCode), keyColumn);
        return jdbcTemplate.query(sql, ps -> setProvider(ps, providerCode), (rs, rowNum) -> duplicateGroup(rs, duplicateType));
    }

    private DuplicateGroup duplicateGroup(ResultSet rs, String duplicateType) throws SQLException {
        DuplicateGroup item = new DuplicateGroup();
        item.setDuplicateType(duplicateType);
        item.setProviderCode(rs.getString("provider_code"));
        item.setDuplicateKey(rs.getString("duplicate_key"));
        item.setDuplicateCount(rs.getLong("duplicate_count"));
        item.setFirstLogId(rs.getObject("first_log_id", Long.class));
        item.setLatestLogId(rs.getObject("latest_log_id", Long.class));
        item.setRetainLogId(item.getLatestLogId());
        item.setReviewLogIds(reviewLogIds(rs.getString("sample_log_ids"), item.getRetainLogId()));
        item.setLogSamples(duplicateLogSamples(rs.getString("sample_log_rows")));
        item.setFirstReceivedAt(toLocalDateTime(rs.getTimestamp("first_received_at")));
        item.setLatestReceivedAt(toLocalDateTime(rs.getTimestamp("latest_received_at")));
        item.setSampleTraceIds(sampleTraceIds(rs.getString("sample_trace_ids")));
        item.setCleanupStrategy("建议先人工核对重复记录，默认保留最新接收记录；确认业务处理结果一致后，再治理其余历史重复接收日志。");
        item.setRecommendedAction("保留建议记录 " + item.getRetainLogId()
                + "，核对其余重复接收记录；治理完成后重启服务或由 DBA 重建唯一索引。");
        return item;
    }

    private long countDuplicateGroups(String keyColumn, String providerCode) {
        String sql = """
                SELECT COUNT(1)
                FROM (
                    SELECT provider_code, %s, COUNT(1) AS duplicate_count
                    FROM %s
                    WHERE %s IS NOT NULL AND %s <> ''
                    %s
                    GROUP BY provider_code, %s
                    HAVING COUNT(1) > 1
                ) duplicate_rows
                """.formatted(keyColumn, TABLE_NAME, keyColumn, keyColumn, providerFilterSql(providerCode), keyColumn);
        Long count = jdbcTemplate.query(sql, ps -> setProvider(ps, providerCode), rs -> rs.next() ? rs.getLong(1) : 0L);
        return count == null ? 0L : count;
    }

    private long countAffectedLogs(String keyColumn, String providerCode) {
        String sql = """
                SELECT COALESCE(SUM(duplicate_count), 0)
                FROM (
                    SELECT COUNT(1) AS duplicate_count
                    FROM %s
                    WHERE %s IS NOT NULL AND %s <> ''
                    %s
                    GROUP BY provider_code, %s
                    HAVING COUNT(1) > 1
                ) duplicate_rows
                """.formatted(TABLE_NAME, keyColumn, keyColumn, providerFilterSql(providerCode), keyColumn);
        Long count = jdbcTemplate.query(sql, ps -> setProvider(ps, providerCode), rs -> rs.next() ? rs.getLong(1) : 0L);
        return count == null ? 0L : count;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """, Integer.class, tableName);
        return count != null && count > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """, Integer.class, tableName, indexName);
        return count != null && count > 0;
    }

    private String providerFilterSql(String providerCode) {
        return StringUtils.hasText(providerCode) ? "AND provider_code = ?" : "";
    }

    private void setProvider(java.sql.PreparedStatement ps, String providerCode) throws SQLException {
        if (StringUtils.hasText(providerCode)) {
            ps.setString(1, providerCode);
        }
    }

    private List<String> sampleTraceIds(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(5)
                .toList();
    }

    private List<Long> reviewLogIds(String value, Long retainLogId) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::parseLong)
                .filter(id -> id != null && !id.equals(retainLogId))
                .distinct()
                .limit(10)
                .toList();
    }

    private List<DuplicateLogSample> duplicateLogSamples(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::duplicateLogSample)
                .filter(sample -> sample.getId() != null)
                .limit(10)
                .toList();
    }

    private DuplicateLogSample duplicateLogSample(String row) {
        String[] parts = row.split("\\|", -1);
        DuplicateLogSample sample = new DuplicateLogSample();
        sample.setId(parts.length > 0 ? parseLong(parts[0]) : null);
        sample.setTraceId(parts.length > 1 ? emptyToNull(parts[1]) : null);
        sample.setProcessStatus(parts.length > 2 ? emptyToNull(parts[2]) : null);
        sample.setIdempotencyStatus(parts.length > 3 ? emptyToNull(parts[3]) : null);
        sample.setBodyHash(parts.length > 4 ? emptyToNull(parts[4]) : null);
        sample.setRelatedOrderId(parts.length > 5 ? parseLong(parts[5]) : null);
        sample.setReceivedAt(parts.length > 6 ? emptyToNull(parts[6]) : null);
        return sample;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String normalizeProvider(String providerCode) {
        return StringUtils.hasText(providerCode) ? providerCode.trim().toUpperCase(Locale.ROOT) : null;
    }
}
