package com.seedcrm.crm.systemflow.config;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SystemFlowSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public SystemFlowSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void initialize() {
        createTables();
        seedStandardOrderFlow();
        ensureStandardOrderFlowCompliant();
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_flow_definition (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    flow_code VARCHAR(64) NOT NULL,
                    flow_name VARCHAR(100) NOT NULL,
                    module_code VARCHAR(64) NOT NULL,
                    business_object VARCHAR(64) NOT NULL,
                    description VARCHAR(500),
                    enabled TINYINT DEFAULT 1,
                    current_version_id BIGINT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_system_flow_definition_code (flow_code)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_flow_version (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    definition_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    change_summary VARCHAR(500),
                    published_at DATETIME,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_system_flow_version_no (definition_id, version_no),
                    KEY idx_system_flow_version_definition (definition_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_flow_node (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    version_id BIGINT NOT NULL,
                    domain_code VARCHAR(64),
                    node_code VARCHAR(64) NOT NULL,
                    node_name VARCHAR(100) NOT NULL,
                    node_type VARCHAR(32) NOT NULL,
                    business_state VARCHAR(64),
                    role_code VARCHAR(64),
                    sort_order INT DEFAULT 0,
                    description VARCHAR(500),
                    KEY idx_system_flow_node_version (version_id),
                    UNIQUE KEY uk_system_flow_node_code (version_id, node_code)
                )
                """);
        ensureColumn("system_flow_node", "domain_code", "domain_code VARCHAR(64) AFTER version_id");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_flow_transition (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    version_id BIGINT NOT NULL,
                    from_node_code VARCHAR(64) NOT NULL,
                    to_node_code VARCHAR(64) NOT NULL,
                    action_code VARCHAR(64) NOT NULL,
                    action_name VARCHAR(100) NOT NULL,
                    guard_rule VARCHAR(500),
                    sort_order INT DEFAULT 0,
                    KEY idx_system_flow_transition_version (version_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_flow_trigger (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    version_id BIGINT NOT NULL,
                    node_code VARCHAR(64) NOT NULL,
                    trigger_type VARCHAR(64) NOT NULL,
                    trigger_name VARCHAR(100) NOT NULL,
                    target_code VARCHAR(128),
                    execution_mode VARCHAR(32) NOT NULL,
                    enabled TINYINT DEFAULT 1,
                    sort_order INT DEFAULT 0,
                    config_json TEXT,
                    KEY idx_system_flow_trigger_version (version_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_flow_audit_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    flow_code VARCHAR(64) NOT NULL,
                    version_no INT,
                    action_type VARCHAR(64) NOT NULL,
                    actor_role_code VARCHAR(64),
                    actor_user_id BIGINT,
                    summary VARCHAR(500),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_system_flow_audit_flow (flow_code, created_at)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_flow_instance (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    flow_code VARCHAR(64) NOT NULL,
                    version_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    business_object VARCHAR(64) NOT NULL,
                    business_id BIGINT NOT NULL,
                    current_node_code VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    title VARCHAR(200),
                    created_by_role_code VARCHAR(64),
                    created_by_user_id BIGINT,
                    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_system_flow_instance_biz (flow_code, business_object, business_id),
                    KEY idx_system_flow_instance_status (flow_code, status, update_time)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_flow_task (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    instance_id BIGINT NOT NULL,
                    flow_code VARCHAR(64) NOT NULL,
                    node_code VARCHAR(64) NOT NULL,
                    node_name VARCHAR(100),
                    task_name VARCHAR(100) NOT NULL,
                    role_code VARCHAR(64),
                    assignee_user_id BIGINT,
                    status VARCHAR(32) NOT NULL,
                    opened_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    completed_at DATETIME,
                    remark VARCHAR(500),
                    KEY idx_system_flow_task_instance (instance_id),
                    KEY idx_system_flow_task_status (flow_code, status, role_code)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS system_flow_event_log (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    instance_id BIGINT NOT NULL,
                    flow_code VARCHAR(64) NOT NULL,
                    version_no INT,
                    action_code VARCHAR(64) NOT NULL,
                    from_node_code VARCHAR(64),
                    to_node_code VARCHAR(64),
                    actor_role_code VARCHAR(64),
                    actor_user_id BIGINT,
                    summary VARCHAR(500),
                    event_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_system_flow_event_instance (instance_id, event_time),
                    KEY idx_system_flow_event_action (flow_code, action_code, event_time)
                )
                """);
    }

    private void seedStandardOrderFlow() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM system_flow_definition WHERE flow_code = 'ORDER_MAIN_FLOW'",
                Integer.class);
        if (count != null && count > 0) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                INSERT INTO system_flow_definition(flow_code, flow_name, module_code, business_object, description, enabled, created_at, updated_at)
                VALUES ('ORDER_MAIN_FLOW', '订单主流程', 'SETTING', 'ORDER', 'Clue -> Customer -> Order -> PlanOrder 标准主链路', 1, ?, ?)
                """, now, now);
        Long definitionId = jdbcTemplate.queryForObject(
                "SELECT id FROM system_flow_definition WHERE flow_code = 'ORDER_MAIN_FLOW'",
                Long.class);
        jdbcTemplate.update("""
                INSERT INTO system_flow_version(definition_id, version_no, status, change_summary, published_at, created_at, updated_at)
                VALUES (?, 1, 'PUBLISHED', '系统内置标准订单流程', ?, ?, ?)
                """, definitionId, now, now, now);
        Long versionId = jdbcTemplate.queryForObject(
                "SELECT id FROM system_flow_version WHERE definition_id = ? AND version_no = 1",
                Long.class, definitionId);
        jdbcTemplate.update("UPDATE system_flow_definition SET current_version_id = ?, updated_at = ? WHERE id = ?",
                versionId, now, definitionId);

        seedStandardNodesAndEdges(versionId);
        log.info("seeded system flow ORDER_MAIN_FLOW v1");
    }

    private void ensureStandardOrderFlowCompliant() {
        Long definitionId = jdbcTemplate.query("""
                SELECT id
                FROM system_flow_definition
                WHERE flow_code = 'ORDER_MAIN_FLOW'
                LIMIT 1
                """, rs -> rs.next() ? rs.getLong("id") : null);
        if (definitionId == null) {
            return;
        }
        Long currentVersionId = jdbcTemplate.query("""
                SELECT current_version_id
                FROM system_flow_definition
                WHERE id = ?
                """, rs -> rs.next() ? rs.getLong("current_version_id") : null, definitionId);
        if (currentVersionId == null || isCurrentStandardFlowCompliant(currentVersionId)) {
            return;
        }
        if (!isSystemManagedVersion(currentVersionId)) {
            log.warn("system flow ORDER_MAIN_FLOW current version {} is not compliant, but it is user-managed; skip automatic publish", currentVersionId);
            return;
        }
        Integer maxVersionNo = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version_no), 0)
                FROM system_flow_version
                WHERE definition_id = ?
                """, Integer.class, definitionId);
        int nextVersionNo = (maxVersionNo == null ? 0 : maxVersionNo) + 1;
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("""
                UPDATE system_flow_version
                SET status = 'ARCHIVED', updated_at = ?
                WHERE definition_id = ? AND status = 'PUBLISHED'
                """, now, definitionId);
        jdbcTemplate.update("""
                INSERT INTO system_flow_version(definition_id, version_no, status, change_summary, published_at, created_at, updated_at)
                VALUES (?, ?, 'PUBLISHED', '系统内置主链路约束升级：补齐纸质确认单节点，主链域限定为 Clue/Customer/Order/PlanOrder，触发器仅保留元数据', ?, ?, ?)
                """, definitionId, nextVersionNo, now, now, now);
        Long versionId = jdbcTemplate.queryForObject("""
                SELECT id
                FROM system_flow_version
                WHERE definition_id = ? AND version_no = ?
                """, Long.class, definitionId, nextVersionNo);
        seedStandardNodesAndEdges(versionId);
        jdbcTemplate.update("UPDATE system_flow_definition SET current_version_id = ?, enabled = 1, updated_at = ? WHERE id = ?",
                versionId, now, definitionId);
        jdbcTemplate.update("""
                INSERT INTO system_flow_audit_log(flow_code, version_no, action_type, actor_role_code, actor_user_id, summary, created_at)
                VALUES ('ORDER_MAIN_FLOW', ?, 'SYSTEM_UPGRADE', 'SYSTEM', NULL, '补齐 Clue -> Customer -> Order -> PlanOrder 标准主链路配置，并将调度/三方能力限定为触发器元数据', ?)
                """, nextVersionNo, now);
        log.info("upgraded system flow ORDER_MAIN_FLOW to v{}", nextVersionNo);
    }

    private boolean isCurrentStandardFlowCompliant(Long versionId) {
        Integer customerNodeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_flow_node
                WHERE version_id = ?
                  AND domain_code = 'CUSTOMER'
                """, Integer.class, versionId);
        Integer orderCoreStateCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_flow_node
                WHERE version_id = ?
                  AND domain_code = 'ORDER'
                  AND LOWER(business_state) IN ('paid', 'used')
                """, Integer.class, versionId);
        Integer planOrderCoreStateCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_flow_node
                WHERE version_id = ?
                  AND domain_code = 'PLANORDER'
                  AND LOWER(business_state) IN ('arrived', 'service_form_confirmed', 'servicing', 'finished')
                """, Integer.class, versionId);
        Integer schedulerNodeCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_flow_node
                WHERE version_id = ?
                  AND domain_code = 'SCHEDULER'
                """, Integer.class, versionId);
        Integer unsafeTriggerCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_flow_trigger
                WHERE version_id = ?
                  AND (
                    UPPER(execution_mode) NOT IN ('MANUAL', 'METADATA_ONLY')
                    OR UPPER(target_code) NOT IN ('DOUYIN_CLUE_INCREMENTAL', 'DOUYIN_VOUCHER_VERIFY', 'ORDER_SETTLEMENT_METADATA', 'SALARY_SETTLEMENT_METADATA')
                  )
                """, Integer.class, versionId);
        Integer appointmentCancelCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_flow_transition
                WHERE version_id = ?
                  AND action_code = 'ORDER_APPOINTMENT_CANCEL'
                """, Integer.class, versionId);
        Integer serviceFormConfirmCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_flow_transition
                WHERE version_id = ?
                  AND action_code = 'SERVICE_FORM_CONFIRM'
                """, Integer.class, versionId);
        return customerNodeCount != null && customerNodeCount > 0
                && orderCoreStateCount != null && orderCoreStateCount >= 2
                && planOrderCoreStateCount != null && planOrderCoreStateCount >= 4
                && schedulerNodeCount != null && schedulerNodeCount == 0
                && unsafeTriggerCount != null && unsafeTriggerCount == 0
                && appointmentCancelCount != null && appointmentCancelCount > 0
                && serviceFormConfirmCount != null && serviceFormConfirmCount > 0;
    }

    private boolean isSystemManagedVersion(Long versionId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM system_flow_version
                WHERE id = ?
                  AND change_summary LIKE '系统内置%'
                """, Integer.class, versionId);
        return count != null && count > 0;
    }

    private void seedStandardNodesAndEdges(Long versionId) {
        seedNode(versionId, "CLUE", "CLUE_INTAKE", "客资入库", "START", "intake", "SCHEDULER", 10, "外部客资统一进入 Clue，必须保留 raw_data");
        seedNode(versionId, "CLUE", "CLUE_ASSIGN", "分配/公海", "TASK", "assigned", "CLUE_MANAGER", 20, "自动或人工分配给客服");
        seedNode(versionId, "CLUE", "CLUE_FOLLOW", "跟进", "TASK", "following", "ONLINE_CUSTOMER_SERVICE", 30, "客服记录跟进和标签");
        seedNode(versionId, "CUSTOMER", "CUSTOMER_CREATED", "生成客户", "TASK", "created", "SYSTEM", 40, "Customer 只能在 Order 创建时生成，禁止外部直接创建");
        seedNode(versionId, "ORDER", "ORDER_PAID", "订单已付款", "STATE", "paid", "ONLINE_CUSTOMER_SERVICE", 50, "Order 必须绑定 Customer，类型 deposit/coupon");
        seedNode(versionId, "ORDER", "APPOINTMENT", "预约排档", "TASK", "paid", "CLUE_MANAGER", 60, "客服按门店档期预约，不新增核心状态");
        seedNode(versionId, "ORDER", "VERIFY", "门店核销", "TASK", "paid", "STORE_SERVICE", 70, "核销团购券或定金，Order 仍处于 paid 到 used 的受控流程");
        seedNode(versionId, "PLANORDER", "PLAN_CREATED", "创建计划单", "TASK", "arrived", "SYSTEM", 80, "PlanOrder 必须绑定 Order，数量 1:1");
        seedNode(versionId, "PLANORDER", "PLAN_ARRIVED", "到店", "STATE", "arrived", "STORE_SERVICE", 90, "PlanOrder arrived");
        seedNode(versionId, "PLANORDER", "SERVICE_FORM_CONFIRMED", "纸质确认单已确认", "STATE", "service_form_confirmed", "STORE_SERVICE", 100, "打印确认单，客户手写签名后在系统确认");
        seedNode(versionId, "PLANORDER", "PLAN_SERVICING", "服务中", "STATE", "servicing", "STORE_SERVICE", 110, "PlanOrder servicing");
        seedNode(versionId, "PLANORDER", "PLAN_FINISHED", "服务完成", "STATE", "finished", "STORE_SERVICE", 120, "PlanOrder finished");
        seedNode(versionId, "ORDER", "ORDER_USED", "订单已使用", "END", "used", "STORE_SERVICE", 130, "Order used，主链路终点");

        seedTransition(versionId, "CLUE_INTAKE", "CLUE_ASSIGN", "CLUE_AUTO_ASSIGN", "自动分配", "Clue raw_data 已保存", 10);
        seedTransition(versionId, "CLUE_ASSIGN", "CLUE_FOLLOW", "CLUE_FOLLOW", "客服跟进", "客服拥有客资数据权限", 20);
        seedTransition(versionId, "CLUE_FOLLOW", "CUSTOMER_CREATED", "PAYMENT_SYNC", "付款同步生成客户", "外部平台付款成功，Customer 在 Order 创建时生成", 30);
        seedTransition(versionId, "CUSTOMER_CREATED", "ORDER_PAID", "ORDER_MARK_PAID", "订单进入已付款", "Order 绑定 Customer", 40);
        seedTransition(versionId, "ORDER_PAID", "APPOINTMENT", "ORDER_APPOINTMENT", "预约门店档期", "订单处于 paid 阶段", 50);
        seedTransition(versionId, "APPOINTMENT", "ORDER_PAID", "ORDER_APPOINTMENT_CANCEL", "取消预约排档", "订单未到店且未核销", 55);
        seedTransition(versionId, "APPOINTMENT", "VERIFY", "ORDER_VERIFY", "门店核销", "门店人员有订单权限", 60);
        seedTransition(versionId, "VERIFY", "PLAN_CREATED", "PLAN_CREATE", "创建计划单", "订单已核销，PlanOrder 1:1 绑定 Order", 70);
        seedTransition(versionId, "PLAN_CREATED", "PLAN_ARRIVED", "PLAN_ARRIVE", "确认到店", "PlanOrder 已创建", 80);
        seedTransition(versionId, "PLAN_ARRIVED", "SERVICE_FORM_CONFIRMED", "SERVICE_FORM_CONFIRM", "确认纸质服务单", "服务确认单已打印，客户已手写签名", 90);
        seedTransition(versionId, "SERVICE_FORM_CONFIRMED", "PLAN_SERVICING", "PLAN_START", "开始服务", "纸质确认单已确认", 100);
        seedTransition(versionId, "PLAN_SERVICING", "PLAN_FINISHED", "PLAN_FINISH", "完成服务", "确认单已确认并完成服务", 110);
        seedTransition(versionId, "PLAN_FINISHED", "ORDER_USED", "ORDER_COMPLETE", "订单完成", "PlanOrder 已完成", 120);

        seedTrigger(versionId, "CLUE_INTAKE", "SCHEDULER_JOB", "调度拉取客资", "DOUYIN_CLUE_INCREMENTAL", "METADATA_ONLY", 10,
                "{\"jobCode\":\"DOUYIN_CLUE_INCREMENTAL\"}");
        seedTrigger(versionId, "VERIFY", "THIRD_PARTY_API", "抖音券码核销", "DOUYIN_VOUCHER_VERIFY", "METADATA_ONLY", 20,
                "{\"provider\":\"DOUYIN_LAIKE\",\"mode\":\"MOCK_OR_LIVE\"}");
        seedTrigger(versionId, "PLAN_FINISHED", "INTERNAL_SERVICE", "服务完成后续配置", "ORDER_SETTLEMENT_METADATA", "METADATA_ONLY", 30,
                "{\"note\":\"V1 仅保存触发器配置，不执行结算服务\"}");
        seedTrigger(versionId, "ORDER_USED", "INTERNAL_SERVICE", "薪酬/分销入账配置", "SALARY_SETTLEMENT_METADATA", "METADATA_ONLY", 40,
                "{\"source\":\"order_role_record\",\"note\":\"V1 仅作为下游触发器元数据\"}");
    }

    private void seedNode(Long versionId, String domainCode, String code, String name, String type, String state, String role, int sort, String description) {
        jdbcTemplate.update("""
                INSERT INTO system_flow_node(version_id, domain_code, node_code, node_name, node_type, business_state, role_code, sort_order, description)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, versionId, domainCode, code, name, type, state, role, sort, description);
    }

    private void seedTransition(Long versionId, String from, String to, String action, String name, String guard, int sort) {
        jdbcTemplate.update("""
                INSERT INTO system_flow_transition(version_id, from_node_code, to_node_code, action_code, action_name, guard_rule, sort_order)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, versionId, from, to, action, name, guard, sort);
    }

    private void seedTrigger(Long versionId, String node, String type, String name, String target, String mode, int sort, String configJson) {
        jdbcTemplate.update("""
                INSERT INTO system_flow_trigger(version_id, node_code, trigger_type, trigger_name, target_code, execution_mode, enabled, sort_order, config_json)
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?)
                """, versionId, node, type, name, target, mode, sort, configJson);
    }

    private void ensureColumn(String tableName, String columnName, String columnDefinition) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """, Integer.class, tableName, columnName);
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnDefinition);
            log.info("added missing column {}.{}", tableName, columnName);
        }
    }
}
