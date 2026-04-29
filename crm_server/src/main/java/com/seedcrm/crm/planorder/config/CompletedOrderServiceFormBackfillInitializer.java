package com.seedcrm.crm.planorder.config;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DependsOn({"orderTableInitializer", "planOrderTableInitializer"})
public class CompletedOrderServiceFormBackfillInitializer {

    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public CompletedOrderServiceFormBackfillInitializer(DataSource dataSource,
                                                        @Value("${seedcrm.backfill.completed-service-form.enabled:false}") boolean enabled) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.enabled = enabled;
    }

    @PostConstruct
    public void initialize() {
        if (!enabled) {
            log.info("completed order service form backfill skipped, set seedcrm.backfill.completed-service-form.enabled=true to run it");
            return;
        }
        backfillMissingPlanOrders();
        normalizeCompletedPlanOrders();
        backfillMissingServiceForms();
    }

    private void backfillMissingPlanOrders() {
        int inserted = jdbcTemplate.update("""
                INSERT INTO plan_order (order_id, status, arrive_time, start_time, finish_time, create_time)
                SELECT o.id,
                       'FINISHED',
                       COALESCE(o.arrive_time, o.appointment_time, o.complete_time, o.update_time, o.create_time, NOW()),
                       COALESCE(o.arrive_time, o.appointment_time, o.complete_time, o.update_time, o.create_time, NOW()),
                       COALESCE(o.complete_time, o.update_time, o.create_time, NOW()),
                       COALESCE(o.create_time, NOW())
                FROM order_info o
                LEFT JOIN plan_order p ON p.order_id = o.id
                WHERE o.status = 'COMPLETED'
                  AND p.id IS NULL
                """);
        if (inserted > 0) {
            log.info("backfilled {} missing finished service forms for completed orders", inserted);
        }
    }

    private void normalizeCompletedPlanOrders() {
        int updated = jdbcTemplate.update("""
                UPDATE plan_order p
                JOIN order_info o ON o.id = p.order_id
                SET p.status = 'FINISHED',
                    p.arrive_time = COALESCE(p.arrive_time, o.arrive_time, o.appointment_time, o.complete_time, o.update_time, o.create_time, NOW()),
                    p.start_time = COALESCE(p.start_time, p.arrive_time, o.arrive_time, o.appointment_time, o.complete_time, o.update_time, o.create_time, NOW()),
                    p.finish_time = COALESCE(p.finish_time, o.complete_time, o.update_time, o.create_time, NOW())
                WHERE o.status = 'COMPLETED'
                  AND (
                      p.status <> 'FINISHED'
                      OR p.arrive_time IS NULL
                      OR p.start_time IS NULL
                      OR p.finish_time IS NULL
                  )
                """);
        if (updated > 0) {
            log.info("normalized {} completed order service-form rows", updated);
        }
    }

    private void backfillMissingServiceForms() {
        int updated = jdbcTemplate.update("""
                UPDATE order_info o
                JOIN plan_order p ON p.order_id = o.id
                SET o.service_detail_json = JSON_OBJECT(
                        'currentRoleCode', 'STORE_SERVICE',
                        'profession', '',
                        'preferredStyles', JSON_ARRAY(),
                        'preferredScenes', JSON_ARRAY(),
                        'serviceRequirement', COALESCE(NULLIF(o.remark, ''), '历史已完成订单，系统补齐服务确认单'),
                        'styleConfirmation', '历史已完成订单已完成门店服务确认',
                        'serviceItems', JSON_ARRAY('门店服务'),
                        'serviceConfirmAmount', COALESCE(NULLIF(o.amount, 0), NULLIF(o.deposit, 0), 0),
                        'serviceDuration', '',
                        'addOnCount', 0,
                        'addOnContent', '',
                        'signature', '系统补齐',
                        'customerSignature', 'data:image/png;base64,legacy-service-confirmed',
                        'customerSignatureSignedAt', DATE_FORMAT(COALESCE(p.finish_time, o.complete_time, o.update_time, o.create_time, NOW()), '%Y-%m-%dT%H:%i:%s'),
                        'internalRemark', '历史脏数据补齐：已完成订单必须有服务确认单'
                    ),
                    o.verification_status = 'VERIFIED',
                    o.verification_method = COALESCE(NULLIF(o.verification_method, ''), 'LEGACY_BACKFILL'),
                    o.verification_code = COALESCE(NULLIF(o.verification_code, ''), CONCAT('LEGACY-', o.id)),
                    o.verification_time = COALESCE(o.verification_time, p.arrive_time, p.start_time, p.finish_time, o.update_time, o.create_time, NOW()),
                    o.complete_time = COALESCE(o.complete_time, p.finish_time, o.update_time, o.create_time, NOW())
                WHERE o.status = 'COMPLETED'
                  AND p.status = 'FINISHED'
                  AND (o.service_detail_json IS NULL OR o.service_detail_json = '')
                """);
        if (updated > 0) {
            log.info("backfilled {} missing service confirmation forms for completed orders", updated);
        }
    }
}
