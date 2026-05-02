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
import org.springframework.core.env.Environment;
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
    void clearTestDataShouldRequireExplicitAllowFlagForRealDelete() {
        Environment environment = Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        when(environment.getProperty("seedcrm.go-live.allow-destructive-clear-test-data", "false")).thenReturn("false");
        SystemGoLiveServiceImpl guardedService = new SystemGoLiveServiceImpl(jdbcTemplate, systemConfigService, environment);
        SystemGoLiveDtos.ClearTestDataRequest request = new SystemGoLiveDtos.ClearTestDataRequest();
        request.setConfirmText("CLEAR_TEST_DATA");
        request.setDryRun(false);

        assertThatThrownBy(() -> guardedService.clearTestData(request, context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("destructive clear is disabled");
    }

    @Test
    void clearTestDataShouldRejectProdProfileEvenWhenAllowFlagIsEnabled() {
        Environment environment = Mockito.mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"prod"});
        when(environment.getProperty("seedcrm.go-live.allow-destructive-clear-test-data", "false")).thenReturn("true");
        SystemGoLiveServiceImpl guardedService = new SystemGoLiveServiceImpl(jdbcTemplate, systemConfigService, environment);
        SystemGoLiveDtos.ClearTestDataRequest request = new SystemGoLiveDtos.ClearTestDataRequest();
        request.setConfirmText("CLEAR_TEST_DATA");
        request.setDryRun(false);

        assertThatThrownBy(() -> guardedService.clearTestData(request, context()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("destructive clear is disabled");
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

    @Test
    void summaryShouldWarnWhenProviderIsOnlyMockSeedConfig() {
        jdbcTemplate.update("""
                INSERT INTO integration_provider_config(
                    provider_code, enabled, execution_mode, base_url, voucher_verify_path, verify_code_field,
                    auth_status, access_token, token_expires_at, last_test_status
                )
                VALUES ('DOUYIN_LAIKE', 1, 'MOCK', 'https://api.oceanengine.com', '/verify', 'encrypted_codes',
                        'MOCK', NULL, NULL, 'SUCCESS')
                """);

        SystemGoLiveDtos.SummaryResponse response = service.summary();

        assertThat(response.getReadinessItems())
                .anySatisfy(item -> {
                    assertThat(item.getKey()).isEqualTo("DOUYIN_PROVIDER");
                    assertThat(item.getStatus()).isEqualTo("WARN");
                    assertThat(item.getMessage()).contains("运行模式不是 LIVE");
                });
    }

    @Test
    void summaryShouldPassWhenDouyinVoucherProviderIsLiveAndAuthorized() {
        jdbcTemplate.update("""
                INSERT INTO integration_provider_config(
                    provider_code, enabled, execution_mode, base_url, voucher_verify_path, verify_code_field,
                    auth_status, access_token, token_expires_at, last_test_status
                )
                VALUES ('DOUYIN_LAIKE', 1, 'LIVE', 'https://api.oceanengine.com',
                        '/goodlife/v1/fulfilment/certificate/verify/', 'encrypted_codes',
                        'AUTHORIZED', 'token', DATEADD('DAY', 1, CURRENT_TIMESTAMP), 'SUCCESS')
                """);

        SystemGoLiveDtos.SummaryResponse response = service.summary();

        assertThat(response.getReadinessItems())
                .anySatisfy(item -> {
                    assertThat(item.getKey()).isEqualTo("DOUYIN_PROVIDER");
                    assertThat(item.getStatus()).isEqualTo("PASS");
                });
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
        jdbcTemplate.execute("CREATE TABLE scheduler_job (id BIGINT PRIMARY KEY, job_code VARCHAR(64))");
        jdbcTemplate.execute("""
                CREATE TABLE integration_provider_config (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    provider_code VARCHAR(64),
                    enabled TINYINT,
                    execution_mode VARCHAR(32),
                    base_url VARCHAR(500),
                    voucher_verify_path VARCHAR(500),
                    verify_code_field VARCHAR(128),
                    auth_status VARCHAR(64),
                    access_token VARCHAR(500),
                    refresh_token VARCHAR(500),
                    token_expires_at TIMESTAMP,
                    refresh_token_expires_at TIMESTAMP,
                    last_test_status VARCHAR(64)
                )
                """);
    }

    private SystemConfigDtos.DomainSettingsResponse domainSettings() {
        SystemConfigDtos.DomainSettingsResponse response = new SystemConfigDtos.DomainSettingsResponse();
        response.setSystemBaseUrl("http://127.0.0.1:8003");
        response.setApiBaseUrl("http://127.0.0.1:8004");
        response.setEventIngestUrl("http://127.0.0.1:8004/open/distribution/events");
        response.setSwaggerUiUrl("http://127.0.0.1:8004/swagger-ui.html");
        response.setOpenApiDocsUrl("http://127.0.0.1:8004/v3/api-docs/distribution-open-api");
        return response;
    }

    private PermissionRequestContext context() {
        PermissionRequestContext context = new PermissionRequestContext();
        context.setRoleCode("ADMIN");
        context.setCurrentUserId(1L);
        return context;
    }
}
