package com.seedcrm.crm.salary.service.impl;

import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import com.seedcrm.crm.salary.dto.SalaryStatResponse;
import com.seedcrm.crm.salary.service.SalaryService;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SalaryServiceImpl implements SalaryService {

    private final OrderRoleRecordMapper orderRoleRecordMapper;

    public SalaryServiceImpl(OrderRoleRecordMapper orderRoleRecordMapper) {
        this.orderRoleRecordMapper = orderRoleRecordMapper;
    }

    @Override
    public SalaryStatResponse stat(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId is required");
        }
        Long orderCount = defaultCount(orderRoleRecordMapper.countDistinctPlanOrdersByUserId(userId));
        Long serviceCount = defaultCount(orderRoleRecordMapper.countFinishedServicesByUserId(userId));
        Map<String, Long> roleDistribution = buildRoleDistribution(
                orderRoleRecordMapper.selectRoleDistributionByUserId(userId));
        return new SalaryStatResponse(userId, orderCount, roleDistribution, serviceCount);
    }

    private Long defaultCount(Long value) {
        return value == null ? 0L : value;
    }

    private Map<String, Long> buildRoleDistribution(List<Map<String, Object>> rawRows) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        if (rawRows == null) {
            return distribution;
        }
        for (Map<String, Object> row : rawRows) {
            Object roleCode = row.get("roleCode");
            Object roleCount = row.get("roleCount");
            if (roleCode != null) {
                distribution.put(String.valueOf(roleCode), toLong(roleCount));
            }
        }
        return distribution;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Integer integerValue) {
            return integerValue.longValue();
        }
        if (value instanceof BigInteger bigIntegerValue) {
            return bigIntegerValue.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }
}
