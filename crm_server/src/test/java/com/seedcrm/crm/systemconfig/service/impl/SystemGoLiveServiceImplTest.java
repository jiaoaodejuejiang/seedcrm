package com.seedcrm.crm.systemconfig.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.permission.support.PermissionRequestContext;
import com.seedcrm.crm.systemconfig.dto.SystemConfigDtos;
import com.seedcrm.crm.systemconfig.dto.SystemGoLiveDtos;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class SystemGoLiveServiceImplTest {

    private JdbcTemplate jdbcTemplate;
    private SystemConfigService systemConfigService;
    private SystemGoLiveServiceImpl service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource());
        systemConfigService = Mockito.mock(SystemConfigService.class);
        service = new SystemGoLiveServiceImpl(jdbcTemplate, systemConfigService);
        createMinimalTables();
        when(systemConfigService.getString("system.environment.mode", "TEST")).thenReturn("TEST");
        when(systemConfigService.getDomainSettings()).thenReturn(domainSettings());
    }

    @Test
    void clearTestDataShouldPreviewWithoutDeletingWhenDryRun() {
        jdbcTemplate.update("INSERT INTO order_info(id, order_no) VALUES (1, 'ORD-1')");
        SystemGoLiveDtos.ClearTestDataRequest request = new SystemGoLiveDtos.ClearTestDataRequest();
        request.setConfirmText("CLEAR_TEST_DATA");
        request.setDryRun(true);

        SystemGoLiveDtos.OperationResponse response = service.clearTestData(request, context());

        assertThat(response.getStatus()).isEqualTo("DRY_RUN");
        assertThat(response.getAffectedRows()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM order_info", Long.class)).isEqualTo(1L);
        assertThat(response.getTables())
                .anySatisfy(item -> {
                    assertThat(item.getTableName()).isEqualTo("order_info");
                    assertThat(item.getRowCountBefore()).isEqualTo(1L);
                    assertThat(item.getSkipped()).isTrue();
                });
    }

    @Test
    void clearTestDataShouldDeleteBusinessAndQueueTablesInNonProd() {
        jdbcTemplate.update("INSERT INTO order_info(id, order_no) VALUES (1, 'ORD-1')");
        jdbcTemplate.update("INSERT INTO scheduler_outbox_event(id, event_type) VALUES (1, 'crm.order.used')");
        SystemGoLiveDtos.ClearTestDataRequest request = new SystemGoLiveDtos.ClearTestDataRequest();
        request.setConfirmText("CLEAR_TEST_DATA");

        SystemGoLiveDtos.OperationResponse response = service.clearTestData(request, context());

        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getAffectedRows()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM order_info", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM scheduler_outbox_event", Long.class)).isZero();
    }

    @Test
    void clearTestDataShouldRejectProdEnvironment() {
        when(systemConfigService.getString("system.environment.mode", "TEST")).thenReturn("PROD");
        SystemGoLiveDtos.ClearTestDataRequest request = new SystemGoLiveDtos.ClearTestDataRequest();
        request.setConfirmText("CLEAR_TEST_DATA");

        assertThatThrownBy(() -> service.clearTestData(request, context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("blocked");
    }

    @Test
    void initializeShouldRequireProdConfirmText() {
        SystemGoLiveDtos.InitializeRequest request = new SystemGoLiveDtos.InitializeRequest();
        request.setTargetEnvironment("PROD");
        request.setConfirmText("INIT_SYSTEM");

        assertThatThrownBy(() -> service.initialize(request, context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("INIT_PROD_SYSTEM");
    }

    private DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:go-live-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void createMinimalTables() {
        jdbcTemplate.execute("""
                CREATE TABLE system_config (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    config_key VARCHAR(128),
                    config_value TEXT,
                    value_type VARCHAR(32),
                    scope_type VARCHAR(32),
                    scope_id VARCHAR(64),
                    enabled TINYINT,
                    description VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE system_config_change_log (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    config_key VARCHAR(128),
                    scope_type VARCHAR(32),
                    scope_id VARCHAR(64),
                    before_value TEXT,
                    after_value TEXT,
                    actor_role_code VARCHAR(64),
                    actor_user_id BIGINT,
                    summary VARCHAR(500),
                    create_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("CREATE TABLE order_info (id BIGINT PRIMARY KEY, order_no VARCHAR(64))");
        jdbcTemplate.execute("CREATE TABLE scheduler_outbox_event (id BIGINT PRIMARY KEY, event_type VARCHAR(64))");
    }

    private SystemConfigDtos.DomainSettingsResponse domainSettings() {
        SystemConfigDtos.DomainSettingsResponse response = new SystemConfigDtos.DomainSettingsResponse();
        response.setSystemBaseUrl("http://127.0.0.1:4173");
        response.setApiBaseUrl("http://127.0.0.1:8080");
        response.setEventIngestUrl("http://127.0.0.1:8080/open/distribution/events");
        response.setSwaggerUiUrl("http://127.0.0.1:8080/swagger-ui.html");
        response.setOpenApiDocsUrl("http://127.0.0.1:8080/v3/api-docs/distribution-open-api");
        return response;
    }

    private PermissionRequestContext context() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("ADMIN");
        context.setCurrentUserId(1L);
        return context;
    }
}
