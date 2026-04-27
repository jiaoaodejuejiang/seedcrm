package com.seedcrm.crm.permission.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.entity.PlanOrder;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OrderPermissionResourceResolver {

    private static final Set<String> SERVICE_ROLE_CODES = Set.of(
            "STORE_SERVICE",
            "STORE_MANAGER",
            "PHOTOGRAPHER",
            "MAKEUP_ARTIST",
            "PHOTO_SELECTOR",
            "CONSULTANT",
            "DOCTOR",
            "ASSISTANT");

    private final ClueMapper clueMapper;
    private final OrderMapper orderMapper;
    private final PlanOrderMapper planOrderMapper;
    private final OrderRoleRecordMapper orderRoleRecordMapper;

    public OrderPermissionResourceResolver(ClueMapper clueMapper,
                                           OrderMapper orderMapper,
                                           PlanOrderMapper planOrderMapper,
                                           OrderRoleRecordMapper orderRoleRecordMapper) {
        this.clueMapper = clueMapper;
        this.orderMapper = orderMapper;
        this.planOrderMapper = planOrderMapper;
        this.orderRoleRecordMapper = orderRoleRecordMapper;
    }

    public Long resolveClueOwnerId(Long clueId) {
        if (clueId == null || clueId <= 0) {
            return null;
        }
        Clue clue = clueMapper.selectById(clueId);
        if (clue == null) {
            throw new BusinessException("clue not found");
        }
        return clue.getCurrentOwnerId();
    }

    public Long resolveOrderOwnerId(Long orderId) {
        Order order = getOrderOrThrow(orderId);
        return resolveClueOwnerId(order.getClueId());
    }

    public Long resolveOrderStoreScopeOwnerId(Long orderId) {
        Order order = getOrderOrThrow(orderId);
        PlanOrder planOrder = planOrderMapper.selectOne(Wrappers.<PlanOrder>lambdaQuery()
                .eq(PlanOrder::getOrderId, orderId)
                .last("LIMIT 1"));
        if (planOrder != null) {
            Long serviceRoleUserId = resolveServiceRoleUserId(planOrder.getId());
            if (serviceRoleUserId != null) {
                return serviceRoleUserId;
            }
        }
        return resolveClueOwnerId(order.getClueId());
    }

    public Long resolvePlanOrderOwnerId(Long planOrderId) {
        if (planOrderId == null || planOrderId <= 0) {
            throw new BusinessException("planOrderId is required");
        }
        PlanOrder planOrder = planOrderMapper.selectById(planOrderId);
        if (planOrder == null) {
            throw new BusinessException("plan order not found");
        }
        return resolveOrderOwnerId(planOrder.getOrderId());
    }

    public Long resolvePlanOrderStoreScopeOwnerId(Long planOrderId) {
        if (planOrderId == null || planOrderId <= 0) {
            throw new BusinessException("planOrderId is required");
        }
        PlanOrder planOrder = planOrderMapper.selectById(planOrderId);
        if (planOrder == null) {
            throw new BusinessException("plan order not found");
        }
        Order order = getOrderOrThrow(planOrder.getOrderId());
        Long serviceRoleUserId = resolveServiceRoleUserId(planOrderId);
        if (serviceRoleUserId != null) {
            return serviceRoleUserId;
        }
        return resolveClueOwnerId(order.getClueId());
    }

    private Order getOrderOrThrow(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new BusinessException("orderId is required");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("order not found");
        }
        return order;
    }

    private Long resolveServiceRoleUserId(Long planOrderId) {
        List<OrderRoleRecord> currentRecords = orderRoleRecordMapper.selectList(Wrappers.<OrderRoleRecord>lambdaQuery()
                .eq(OrderRoleRecord::getPlanOrderId, planOrderId)
                .eq(OrderRoleRecord::getIsCurrent, 1)
                .orderByAsc(OrderRoleRecord::getCreateTime)
                .orderByAsc(OrderRoleRecord::getId));
        Long currentUserId = pickServiceRoleUserId(currentRecords);
        if (currentUserId != null) {
            return currentUserId;
        }
        List<OrderRoleRecord> historicalRecords = orderRoleRecordMapper.selectList(Wrappers.<OrderRoleRecord>lambdaQuery()
                .eq(OrderRoleRecord::getPlanOrderId, planOrderId)
                .orderByDesc(OrderRoleRecord::getCreateTime)
                .orderByDesc(OrderRoleRecord::getId));
        return pickServiceRoleUserId(historicalRecords);
    }

    private Long pickServiceRoleUserId(List<OrderRoleRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        for (OrderRoleRecord record : records) {
            if (record == null || record.getUserId() == null || record.getUserId() <= 0) {
                continue;
            }
            String roleCode = normalize(record.getRoleCode());
            if (SERVICE_ROLE_CODES.contains(roleCode)) {
                return record.getUserId();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
