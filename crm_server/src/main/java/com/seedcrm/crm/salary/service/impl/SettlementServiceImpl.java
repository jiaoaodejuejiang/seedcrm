package com.seedcrm.crm.salary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.salary.dto.SalarySettlementCreateRequest;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.SalarySettlement;
import com.seedcrm.crm.salary.enums.SalarySettlementStatus;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.salary.mapper.SalarySettlementMapper;
import com.seedcrm.crm.risk.service.DbLockService;
import com.seedcrm.crm.salary.service.SettlementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementServiceImpl implements SettlementService {

    private final SalaryDetailMapper salaryDetailMapper;
    private final SalarySettlementMapper salarySettlementMapper;
    private final DbLockService dbLockService;

    public SettlementServiceImpl(SalaryDetailMapper salaryDetailMapper,
                                 SalarySettlementMapper salarySettlementMapper,
                                 DbLockService dbLockService) {
        this.salaryDetailMapper = salaryDetailMapper;
        this.salarySettlementMapper = salarySettlementMapper;
        this.dbLockService = dbLockService;
    }

    @Override
    @Transactional
    public SalarySettlement createSettlement(SalarySettlementCreateRequest request) {
        validateCreateRequest(request);
        List<SalaryDetail> details = dbLockService.lockUnsettledSalaryDetails(
                request.getUserId(), request.getStartTime(), request.getEndTime());
        if (details.isEmpty()) {
            throw new BusinessException("no unsettled salary details found for settlement");
        }

        LocalDateTime now = LocalDateTime.now();
        SalarySettlement settlement = new SalarySettlement();
        settlement.setUserId(request.getUserId());
        settlement.setTotalAmount(sumAmounts(details));
        settlement.setStatus(SalarySettlementStatus.INIT.name());
        settlement.setStartTime(request.getStartTime());
        settlement.setEndTime(request.getEndTime());
        settlement.setCreateTime(now);
        if (salarySettlementMapper.insert(settlement) <= 0) {
            throw new BusinessException("failed to create salary settlement");
        }

        for (SalaryDetail detail : details) {
            detail.setSettlementId(settlement.getId());
            detail.setSettlementTime(now);
            if (salaryDetailMapper.updateById(detail) <= 0) {
                throw new BusinessException("failed to bind salary detail to settlement");
            }
        }
        return settlement;
    }

    @Override
    @Transactional
    public SalarySettlement updateStatus(Long settlementId, SalarySettlementStatus targetStatus) {
        if (settlementId == null || settlementId <= 0) {
            throw new BusinessException("settlementId is required");
        }
        if (targetStatus == null) {
            throw new BusinessException("target settlement status is required");
        }
        SalarySettlement settlement = dbLockService.lockSalarySettlement(settlementId);
        SalarySettlementStatus currentStatus = SalarySettlementStatus.fromCode(settlement.getStatus());
        ensureStatusTransition(currentStatus, targetStatus);
        settlement.setStatus(targetStatus.name());
        if (salarySettlementMapper.updateById(settlement) <= 0) {
            throw new BusinessException("failed to update settlement status");
        }
        return settlement;
    }

    @Override
    public List<SalarySettlement> listSettlements(Long userId) {
        LambdaQueryWrapper<SalarySettlement> wrapper = new LambdaQueryWrapper<SalarySettlement>()
                .orderByDesc(SalarySettlement::getCreateTime)
                .orderByDesc(SalarySettlement::getId);
        if (userId != null && userId > 0) {
            wrapper.eq(SalarySettlement::getUserId, userId);
        }
        return salarySettlementMapper.selectList(wrapper);
    }

    private void validateCreateRequest(SalarySettlementCreateRequest request) {
        if (request == null) {
            throw new BusinessException("request body is required");
        }
        if (request.getUserId() == null || request.getUserId() <= 0) {
            throw new BusinessException("userId is required");
        }
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException("startTime and endTime are required");
        }
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new BusinessException("startTime cannot be after endTime");
        }
    }

    private BigDecimal sumAmounts(List<SalaryDetail> details) {
        return details.stream()
                .map(SalaryDetail::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void ensureStatusTransition(SalarySettlementStatus currentStatus, SalarySettlementStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return;
        }
        if (currentStatus == SalarySettlementStatus.INIT && targetStatus == SalarySettlementStatus.CONFIRMED) {
            return;
        }
        if ((currentStatus == SalarySettlementStatus.INIT || currentStatus == SalarySettlementStatus.CONFIRMED)
                && targetStatus == SalarySettlementStatus.PAID) {
            return;
        }
        throw new BusinessException("invalid settlement status transition: "
                + currentStatus.name() + " -> " + targetStatus.name());
    }
}
