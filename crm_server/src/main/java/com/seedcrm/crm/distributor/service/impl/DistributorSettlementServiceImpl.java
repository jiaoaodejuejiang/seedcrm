package com.seedcrm.crm.distributor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.distributor.dto.DistributorSettlementCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorWithdrawCreateRequest;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorSettlement;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.distributor.enums.DistributorSettlementStatus;
import com.seedcrm.crm.distributor.enums.DistributorWithdrawStatus;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorSettlementMapper;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.distributor.service.DistributorService;
import com.seedcrm.crm.distributor.service.DistributorSettlementService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributorSettlementServiceImpl implements DistributorSettlementService {

    private final DistributorService distributorService;
    private final DistributorIncomeDetailMapper distributorIncomeDetailMapper;
    private final DistributorSettlementMapper distributorSettlementMapper;
    private final DistributorWithdrawMapper distributorWithdrawMapper;

    public DistributorSettlementServiceImpl(DistributorService distributorService,
                                            DistributorIncomeDetailMapper distributorIncomeDetailMapper,
                                            DistributorSettlementMapper distributorSettlementMapper,
                                            DistributorWithdrawMapper distributorWithdrawMapper) {
        this.distributorService = distributorService;
        this.distributorIncomeDetailMapper = distributorIncomeDetailMapper;
        this.distributorSettlementMapper = distributorSettlementMapper;
        this.distributorWithdrawMapper = distributorWithdrawMapper;
    }

    @Override
    @Transactional
    public DistributorSettlement createSettlement(DistributorSettlementCreateRequest request) {
        validateSettlementRequest(request);
        distributorService.getByIdOrThrow(request.getDistributorId());

        List<DistributorIncomeDetail> details = distributorIncomeDetailMapper.selectList(
                new LambdaQueryWrapper<DistributorIncomeDetail>()
                        .eq(DistributorIncomeDetail::getDistributorId, request.getDistributorId())
                        .isNull(DistributorIncomeDetail::getSettlementId)
                        .ge(DistributorIncomeDetail::getCreateTime, request.getStartTime())
                        .le(DistributorIncomeDetail::getCreateTime, request.getEndTime())
                        .orderByAsc(DistributorIncomeDetail::getCreateTime, DistributorIncomeDetail::getId));
        if (details.isEmpty()) {
            throw new BusinessException("no unsettled distributor income details found for settlement");
        }

        LocalDateTime now = LocalDateTime.now();
        DistributorSettlement settlement = new DistributorSettlement();
        settlement.setDistributorId(request.getDistributorId());
        settlement.setTotalAmount(sumIncome(details));
        settlement.setStatus(DistributorSettlementStatus.INIT.name());
        settlement.setStartTime(request.getStartTime());
        settlement.setEndTime(request.getEndTime());
        settlement.setCreateTime(now);
        if (distributorSettlementMapper.insert(settlement) <= 0) {
            throw new BusinessException("failed to create distributor settlement");
        }

        for (DistributorIncomeDetail detail : details) {
            detail.setSettlementId(settlement.getId());
            detail.setSettlementTime(now);
            if (distributorIncomeDetailMapper.updateById(detail) <= 0) {
                throw new BusinessException("failed to bind income detail to settlement");
            }
        }
        return settlement;
    }

    @Override
    @Transactional
    public DistributorSettlement updateSettlementStatus(Long settlementId, DistributorSettlementStatus targetStatus) {
        if (settlementId == null || settlementId <= 0) {
            throw new BusinessException("settlementId is required");
        }
        if (targetStatus == null) {
            throw new BusinessException("target settlement status is required");
        }
        DistributorSettlement settlement = distributorSettlementMapper.selectById(settlementId);
        if (settlement == null) {
            throw new BusinessException("distributor settlement not found");
        }
        ensureSettlementStatusTransition(
                DistributorSettlementStatus.fromCode(settlement.getStatus()), targetStatus);
        settlement.setStatus(targetStatus.name());
        if (distributorSettlementMapper.updateById(settlement) <= 0) {
            throw new BusinessException("failed to update distributor settlement status");
        }
        return settlement;
    }

    @Override
    public BigDecimal getWithdrawableAmount(Long distributorId) {
        validateDistributorId(distributorId);
        distributorService.getByIdOrThrow(distributorId);
        BigDecimal settledAmount = distributorSettlementMapper.selectList(new LambdaQueryWrapper<DistributorSettlement>()
                        .eq(DistributorSettlement::getDistributorId, distributorId))
                .stream()
                .map(DistributorSettlement::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withdrawnAmount = distributorWithdrawMapper.selectList(new LambdaQueryWrapper<DistributorWithdraw>()
                        .eq(DistributorWithdraw::getDistributorId, distributorId))
                .stream()
                .map(DistributorWithdraw::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal withdrawableAmount = settledAmount.subtract(withdrawnAmount);
        if (withdrawableAmount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return withdrawableAmount.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional
    public DistributorWithdraw createWithdraw(DistributorWithdrawCreateRequest request) {
        if (request == null) {
            throw new BusinessException("request body is required");
        }
        validateDistributorId(request.getDistributorId());
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("withdraw amount must be greater than 0");
        }

        BigDecimal withdrawableAmount = getWithdrawableAmount(request.getDistributorId());
        BigDecimal requestAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (requestAmount.compareTo(withdrawableAmount) > 0) {
            throw new BusinessException("withdraw amount exceeds withdrawable balance");
        }

        DistributorWithdraw withdraw = new DistributorWithdraw();
        withdraw.setDistributorId(request.getDistributorId());
        withdraw.setAmount(requestAmount);
        withdraw.setStatus(DistributorWithdrawStatus.PENDING.name());
        withdraw.setCreateTime(LocalDateTime.now());
        if (distributorWithdrawMapper.insert(withdraw) <= 0) {
            throw new BusinessException("failed to create distributor withdraw record");
        }
        return withdraw;
    }

    @Override
    @Transactional
    public DistributorWithdraw approveWithdraw(Long withdrawId, DistributorWithdrawStatus targetStatus) {
        if (withdrawId == null || withdrawId <= 0) {
            throw new BusinessException("withdrawId is required");
        }
        if (targetStatus == null) {
            throw new BusinessException("target withdraw status is required");
        }
        if (targetStatus == DistributorWithdrawStatus.PENDING) {
            throw new BusinessException("withdraw status cannot be set back to PENDING");
        }
        DistributorWithdraw withdraw = distributorWithdrawMapper.selectById(withdrawId);
        if (withdraw == null) {
            throw new BusinessException("distributor withdraw record not found");
        }

        ensureWithdrawStatusTransition(DistributorWithdrawStatus.fromCode(withdraw.getStatus()), targetStatus);
        withdraw.setStatus(targetStatus.name());
        if (distributorWithdrawMapper.updateById(withdraw) <= 0) {
            throw new BusinessException("failed to update distributor withdraw status");
        }
        return withdraw;
    }

    private void validateSettlementRequest(DistributorSettlementCreateRequest request) {
        if (request == null) {
            throw new BusinessException("request body is required");
        }
        validateDistributorId(request.getDistributorId());
        if (request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException("startTime and endTime are required");
        }
        if (request.getStartTime().isAfter(request.getEndTime())) {
            throw new BusinessException("startTime cannot be after endTime");
        }
    }

    private void validateDistributorId(Long distributorId) {
        if (distributorId == null || distributorId <= 0) {
            throw new BusinessException("distributorId is required");
        }
    }

    private void ensureSettlementStatusTransition(DistributorSettlementStatus currentStatus,
                                                  DistributorSettlementStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return;
        }
        if (currentStatus == DistributorSettlementStatus.INIT && targetStatus == DistributorSettlementStatus.CONFIRMED) {
            return;
        }
        if ((currentStatus == DistributorSettlementStatus.INIT
                || currentStatus == DistributorSettlementStatus.CONFIRMED)
                && targetStatus == DistributorSettlementStatus.PAID) {
            return;
        }
        throw new BusinessException("invalid distributor settlement status transition: "
                + currentStatus.name() + " -> " + targetStatus.name());
    }

    private void ensureWithdrawStatusTransition(DistributorWithdrawStatus currentStatus,
                                                DistributorWithdrawStatus targetStatus) {
        if (currentStatus == targetStatus) {
            return;
        }
        if (currentStatus == DistributorWithdrawStatus.PENDING
                && (targetStatus == DistributorWithdrawStatus.APPROVED
                || targetStatus == DistributorWithdrawStatus.PAID)) {
            return;
        }
        if (currentStatus == DistributorWithdrawStatus.APPROVED
                && targetStatus == DistributorWithdrawStatus.PAID) {
            return;
        }
        throw new BusinessException("invalid distributor withdraw status transition: "
                + currentStatus.name() + " -> " + targetStatus.name());
    }

    private BigDecimal sumIncome(List<DistributorIncomeDetail> details) {
        return details.stream()
                .map(DistributorIncomeDetail::getIncomeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
