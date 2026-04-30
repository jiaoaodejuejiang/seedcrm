package com.seedcrm.crm.scheduler.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse;
import com.seedcrm.crm.scheduler.dto.SchedulerIdempotencyHealthResponse.DuplicateGroup;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

class SchedulerIdempotencyHealthServiceImplTest {

    @Test
    void shouldReportHealthyWhenUniqueIndexesExistAndNoDuplicates() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.activeIndexes.add("uk_callback_provider_idempotency");
        jdbcTemplate.activeIndexes.add("uk_callback_provider_event");
        SchedulerIdempotencyHealthServiceImpl service = new SchedulerIdempotencyHealthServiceImpl(jdbcTemplate);

        SchedulerIdempotencyHealthResponse response = service.inspect("distribution");

        assertThat(response.isHealthy()).isTrue();
        assertThat(response.getProviderCode()).isEqualTo("DISTRIBUTION");
        assertThat(response.getStatus()).isEqualTo("HEALTHY");
        assertThat(response.getDuplicateGroupCount()).isZero();
        assertThat(response.getAffectedLogCount()).isZero();
        assertThat(response.getIndexes())
                .extracting(SchedulerIdempotencyHealthResponse.IndexHealth::getStatus)
                .containsExactly("ACTIVE", "ACTIVE");
        assertThat(response.getRecommendedActions()).contains("幂等唯一索引已生效，当前未发现重复接口接收日志。");
        assertThat(jdbcTemplate.sqls).anyMatch(sql -> sql.contains("AND provider_code = ?"));
    }

    @Test
    void shouldReportDuplicateDataWhenHistoricalCallbackLogsConflict() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        DuplicateGroup group = duplicateGroup("IDEMPOTENCY_KEY", "idem-001", 3);
        jdbcTemplate.idempotencyGroups = List.of(group);
        jdbcTemplate.idempotencyDuplicateGroups = 1;
        jdbcTemplate.idempotencyAffectedLogs = 3;
        SchedulerIdempotencyHealthServiceImpl service = new SchedulerIdempotencyHealthServiceImpl(jdbcTemplate);

        SchedulerIdempotencyHealthResponse response = service.inspect("DISTRIBUTION");

        assertThat(response.isHealthy()).isFalse();
        assertThat(response.getStatus()).isEqualTo("DUPLICATE_DATA");
        assertThat(response.getDuplicateGroupCount()).isEqualTo(1);
        assertThat(response.getAffectedLogCount()).isEqualTo(3);
        assertThat(response.getDuplicateGroups()).containsExactly(group);
        assertThat(response.getIndexes())
                .extracting(SchedulerIdempotencyHealthResponse.IndexHealth::getStatus)
                .containsExactly("BLOCKED_BY_DUPLICATES", "READY_TO_CREATE");
        assertThat(response.getRecommendedActions())
                .anyMatch(action -> action.contains("清理历史接口接收日志"))
                .anyMatch(action -> action.contains("不要删除相关业务 Customer / Order / PlanOrder 数据"));
    }

    @Test
    void shouldReportIndexNotReadyWhenDataIsCleanButUniqueIndexesAreMissing() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        SchedulerIdempotencyHealthServiceImpl service = new SchedulerIdempotencyHealthServiceImpl(jdbcTemplate);

        SchedulerIdempotencyHealthResponse response = service.inspect(null);

        assertThat(response.isHealthy()).isFalse();
        assertThat(response.getProviderCode()).isNull();
        assertThat(response.getStatus()).isEqualTo("INDEX_NOT_READY");
        assertThat(response.getDuplicateGroupCount()).isZero();
        assertThat(response.getIndexes())
                .extracting(SchedulerIdempotencyHealthResponse.IndexHealth::getStatus)
                .containsExactly("READY_TO_CREATE", "READY_TO_CREATE");
        assertThat(response.getRecommendedActions())
                .anyMatch(action -> action.contains("当前没有重复数据但唯一索引未生效"));
        assertThat(jdbcTemplate.sqls).noneMatch(sql -> sql.contains("AND provider_code = ?"));
    }

    @Test
    void shouldReportMissingTableWithoutRunningDuplicateQueries() {
        StubJdbcTemplate jdbcTemplate = new StubJdbcTemplate();
        jdbcTemplate.tableExists = false;
        SchedulerIdempotencyHealthServiceImpl service = new SchedulerIdempotencyHealthServiceImpl(jdbcTemplate);

        SchedulerIdempotencyHealthResponse response = service.inspect("DISTRIBUTION");

        assertThat(response.isHealthy()).isFalse();
        assertThat(response.getStatus()).isEqualTo("MISSING_TABLE");
        assertThat(response.getDuplicateGroups()).isEmpty();
        assertThat(response.getIndexes())
                .extracting(SchedulerIdempotencyHealthResponse.IndexHealth::getStatus)
                .containsExactly("MISSING_TABLE", "MISSING_TABLE");
        assertThat(response.getRecommendedActions()).containsExactly("回调日志表不存在，请先完成调度中心数据库初始化。");
        assertThat(jdbcTemplate.sqls).hasSize(1);
    }

    private static DuplicateGroup duplicateGroup(String type, String key, long count) {
        DuplicateGroup group = new DuplicateGroup();
        group.setDuplicateType(type);
        group.setProviderCode("DISTRIBUTION");
        group.setDuplicateKey(key);
        group.setDuplicateCount(count);
        group.setSampleTraceIds(List.of("trace-003", "trace-002"));
        group.setRecommendedAction("保留最新或业务成功记录，清理其他重复接口接收日志后重建唯一索引。");
        return group;
    }

    private static class StubJdbcTemplate extends JdbcTemplate {
        private boolean tableExists = true;
        private final Set<String> activeIndexes = new HashSet<>();
        private List<DuplicateGroup> idempotencyGroups = List.of();
        private List<DuplicateGroup> eventGroups = List.of();
        private long idempotencyDuplicateGroups;
        private long eventDuplicateGroups;
        private long idempotencyAffectedLogs;
        private long eventAffectedLogs;
        private final List<String> sqls = new ArrayList<>();

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            sqls.add(sql);
            if (sql.contains("information_schema.TABLES")) {
                return requiredType.cast(tableExists ? 1 : 0);
            }
            if (sql.contains("information_schema.STATISTICS")) {
                String indexName = String.valueOf(args[1]);
                return requiredType.cast(activeIndexes.contains(indexName) ? 1 : 0);
            }
            return requiredType.cast(0);
        }

        @Override
        public <T> List<T> query(String sql, PreparedStatementSetter pss, RowMapper<T> rowMapper) {
            sqls.add(sql);
            applySetter(pss);
            List<DuplicateGroup> groups = sql.contains("idempotency_key AS duplicate_key")
                    ? idempotencyGroups
                    : eventGroups;
            return groups.stream().map(row -> (T) row).toList();
        }

        @Override
        public <T> T query(String sql, PreparedStatementSetter pss, ResultSetExtractor<T> rse) {
            sqls.add(sql);
            applySetter(pss);
            boolean affectedQuery = sql.contains("COALESCE(SUM(duplicate_count), 0)");
            boolean idempotencyQuery = sql.contains("idempotency_key");
            Long value;
            if (affectedQuery && idempotencyQuery) {
                value = idempotencyAffectedLogs;
            } else if (affectedQuery) {
                value = eventAffectedLogs;
            } else if (idempotencyQuery) {
                value = idempotencyDuplicateGroups;
            } else {
                value = eventDuplicateGroups;
            }
            return (T) value;
        }

        private void applySetter(PreparedStatementSetter pss) {
            if (pss == null) {
                return;
            }
            try {
                pss.setValues(mock(PreparedStatement.class));
            } catch (SQLException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
