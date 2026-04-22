package com.seedcrm.crm.planorder.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.planorder.entity.OrderRoleRecord;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import com.seedcrm.crm.planorder.service.OrderRoleRecordService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrderRoleRecordServiceImpl extends ServiceImpl<OrderRoleRecordMapper, OrderRoleRecord>
        implements OrderRoleRecordService {

    private final OrderRoleRecordMapper orderRoleRecordMapper;

    public OrderRoleRecordServiceImpl(OrderRoleRecordMapper orderRoleRecordMapper) {
        this.orderRoleRecordMapper = orderRoleRecordMapper;
    }

    @Override
    @Transactional
    public OrderRoleRecord assignRole(Long planOrderId, String roleCode, Long userId) {
        validateAssignParams(planOrderId, roleCode, userId);
        String normalizedRoleCode = roleCode.trim().toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        OrderRoleRecord currentRecord = orderRoleRecordMapper.selectOne(new LambdaQueryWrapper<OrderRoleRecord>()
                .eq(OrderRoleRecord::getPlanOrderId, planOrderId)
                .eq(OrderRoleRecord::getRoleCode, normalizedRoleCode)
                .eq(OrderRoleRecord::getIsCurrent, 1)
                .last("LIMIT 1"));
        if (currentRecord != null) {
            if (userId.equals(currentRecord.getUserId())) {
                throw new BusinessException("role already assigned to current user");
            }
            currentRecord.setIsCurrent(0);
            currentRecord.setEndTime(now);
            if (orderRoleRecordMapper.updateById(currentRecord) <= 0) {
                throw new BusinessException("failed to close current role record");
            }
        }

        OrderRoleRecord newRecord = new OrderRoleRecord();
        newRecord.setPlanOrderId(planOrderId);
        newRecord.setRoleCode(normalizedRoleCode);
        newRecord.setUserId(userId);
        newRecord.setStartTime(now);
        newRecord.setIsCurrent(1);
        newRecord.setCreateTime(now);
        if (orderRoleRecordMapper.insert(newRecord) <= 0) {
            throw new BusinessException("failed to create role record");
        }
        return newRecord;
    }

    @Override
    public List<OrderRoleRecord> listByPlanOrderId(Long planOrderId) {
        if (planOrderId == null || planOrderId <= 0) {
            throw new BusinessException("planOrderId is required");
        }
        return orderRoleRecordMapper.selectList(new LambdaQueryWrapper<OrderRoleRecord>()
                .eq(OrderRoleRecord::getPlanOrderId, planOrderId)
                .orderByAsc(OrderRoleRecord::getCreateTime, OrderRoleRecord::getId));
    }

    private void validateAssignParams(Long planOrderId, String roleCode, Long userId) {
        if (planOrderId == null || planOrderId <= 0) {
            throw new BusinessException("planOrderId is required");
        }
        if (!StringUtils.hasText(roleCode)) {
            throw new BusinessException("roleCode is required");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException("userId is required");
        }
    }
}
