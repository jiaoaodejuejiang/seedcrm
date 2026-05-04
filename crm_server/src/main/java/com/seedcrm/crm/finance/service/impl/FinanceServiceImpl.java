package com.seedcrm.crm.finance.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.customer.entity.Customer;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.distributor.entity.DistributorIncomeDetail;
import com.seedcrm.crm.distributor.entity.DistributorWithdraw;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.distributor.enums.DistributorWithdrawStatus;
import com.seedcrm.crm.finance.dto.FinanceBalanceResponse;
import com.seedcrm.crm.finance.dto.FinanceCheckItemResponse;
import com.seedcrm.crm.finance.dto.FinanceCheckResponse;
import com.seedcrm.crm.finance.dto.FinanceRefundRecordListResponse;
import com.seedcrm.crm.finance.dto.FinanceRefundRecordResponse;
import com.seedcrm.crm.finance.entity.Account;
import com.seedcrm.crm.finance.entity.FinanceCheckRecord;
import com.seedcrm.crm.finance.entity.Ledger;
import com.seedcrm.crm.finance.enums.AccountOwnerType;
import com.seedcrm.crm.finance.enums.FinanceCheckStatus;
import com.seedcrm.crm.finance.enums.LedgerBizType;
import com.seedcrm.crm.finance.enums.LedgerDirection;
import com.seedcrm.crm.finance.mapper.FinanceCheckRecordMapper;
import com.seedcrm.crm.finance.service.AccountService;
import com.seedcrm.crm.finance.service.FinanceService;
import com.seedcrm.crm.finance.service.LedgerService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.entity.OrderRefundRecord;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.order.mapper.OrderRefundRecordMapper;
import com.seedcrm.crm.salary.entity.SalaryDetail;
import com.seedcrm.crm.salary.entity.WithdrawRecord;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.salary.enums.WithdrawStatus;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FinanceServiceImpl implements FinanceService {

    private static final int DEFAULT_REFUND_RECORD_PAGE_SIZE = 30;
    private static final int MAX_REFUND_RECORD_PAGE_SIZE = 200;
    private static final int MAX_REFUND_REASON_LENGTH = 48;

    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final FinanceCheckRecordMapper financeCheckRecordMapper;
    private final OrderMapper orderMapper;
    private final OrderRefundRecordMapper orderRefundRecordMapper;
    private final CustomerMapper customerMapper;
    private final SalaryDetailMapper salaryDetailMapper;
    private final DistributorIncomeDetailMapper distributorIncomeDetailMapper;
    private final WithdrawRecordMapper withdrawRecordMapper;
    private final DistributorWithdrawMapper distributorWithdrawMapper;
    private final StaffDirectoryService staffDirectoryService;

    public FinanceServiceImpl(AccountService accountService,
                              LedgerService ledgerService,
                              FinanceCheckRecordMapper financeCheckRecordMapper,
                              OrderMapper orderMapper,
                              OrderRefundRecordMapper orderRefundRecordMapper,
                              CustomerMapper customerMapper,
                              SalaryDetailMapper salaryDetailMapper,
                              DistributorIncomeDetailMapper distributorIncomeDetailMapper,
                              WithdrawRecordMapper withdrawRecordMapper,
                              DistributorWithdrawMapper distributorWithdrawMapper,
                              StaffDirectoryService staffDirectoryService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
        this.financeCheckRecordMapper = financeCheckRecordMapper;
        this.orderMapper = orderMapper;
        this.orderRefundRecordMapper = orderRefundRecordMapper;
        this.customerMapper = customerMapper;
        this.salaryDetailMapper = salaryDetailMapper;
        this.distributorIncomeDetailMapper = distributorIncomeDetailMapper;
        this.withdrawRecordMapper = withdrawRecordMapper;
        this.distributorWithdrawMapper = distributorWithdrawMapper;
        this.staffDirectoryService = staffDirectoryService;
    }

    @Override
    @Transactional
    public Ledger recordOrderIncome(Order order) {
        if (order == null || order.getId() == null) {
            throw new BusinessException("completed order is required");
        }
        if (order.getAmount() == null || order.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("order amount must be greater than 0");
        }
        return ledgerService.record(AccountOwnerType.PLATFORM, AccountService.PLATFORM_OWNER_ID, order.getAmount(),
                LedgerBizType.ORDER, order.getId(), LedgerDirection.IN);
    }

    @Override
    @Transactional
    public Ledger recordSalaryIncome(SalaryDetail salaryDetail) {
        if (salaryDetail == null || salaryDetail.getId() == null) {
            throw new BusinessException("salary detail is required");
        }
        if (salaryDetail.getUserId() == null || salaryDetail.getUserId() <= 0) {
            throw new BusinessException("salary detail userId is required");
        }
        BigDecimal amount = salaryDetail.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("salary detail amount is required");
        }
        LedgerDirection direction = amount.compareTo(BigDecimal.ZERO) < 0
                ? LedgerDirection.OUT
                : LedgerDirection.IN;
        return ledgerService.record(AccountOwnerType.USER, salaryDetail.getUserId(), amount.abs(),
                LedgerBizType.SALARY, salaryDetail.getId(), direction);
    }

    @Override
    @Transactional
    public Ledger recordDistributorIncome(DistributorIncomeDetail incomeDetail) {
        if (incomeDetail == null || incomeDetail.getId() == null) {
            throw new BusinessException("distributor income detail is required");
        }
        if (incomeDetail.getDistributorId() == null || incomeDetail.getDistributorId() <= 0) {
            throw new BusinessException("distributorId is required");
        }
        return ledgerService.record(AccountOwnerType.DISTRIBUTOR, incomeDetail.getDistributorId(),
                incomeDetail.getIncomeAmount(), LedgerBizType.DISTRIBUTOR, incomeDetail.getId(), LedgerDirection.IN);
    }

    @Override
    @Transactional
    public Ledger recordUserWithdraw(WithdrawRecord withdrawRecord) {
        if (withdrawRecord == null || withdrawRecord.getId() == null) {
            throw new BusinessException("withdraw record is required");
        }
        if (withdrawRecord.getUserId() == null || withdrawRecord.getUserId() <= 0) {
            throw new BusinessException("withdraw record userId is required");
        }
        return ledgerService.record(AccountOwnerType.USER, withdrawRecord.getUserId(), withdrawRecord.getAmount(),
                LedgerBizType.WITHDRAW, withdrawRecord.getId(), LedgerDirection.OUT);
    }

    @Override
    @Transactional
    public Ledger recordDistributorWithdraw(DistributorWithdraw withdrawRecord) {
        if (withdrawRecord == null || withdrawRecord.getId() == null) {
            throw new BusinessException("distributor withdraw record is required");
        }
        if (withdrawRecord.getDistributorId() == null || withdrawRecord.getDistributorId() <= 0) {
            throw new BusinessException("distributor withdraw distributorId is required");
        }
        return ledgerService.record(AccountOwnerType.DISTRIBUTOR, withdrawRecord.getDistributorId(),
                withdrawRecord.getAmount(), LedgerBizType.WITHDRAW, withdrawRecord.getId(), LedgerDirection.OUT);
    }

    @Override
    public FinanceBalanceResponse getBalance(AccountOwnerType ownerType, Long ownerId) {
        Account account = accountService.getOrCreateAccount(ownerType, ownerId);
        return new FinanceBalanceResponse(account.getId(), account.getOwnerType(), account.getOwnerId(),
                ledgerService.getBalance(account.getId()));
    }

    @Override
    @Transactional
    public FinanceCheckResponse check() {
        synchronizeLedger();

        List<FinanceCheckItemResponse> records = new ArrayList<>();

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .isNotNull(Order::getCompleteTime)
                .orderByAsc(Order::getCompleteTime, Order::getId));
        for (Order order : orders) {
            records.add(checkAndSave(LedgerBizType.ORDER, order.getId(), order.getAmount(),
                    AccountOwnerType.PLATFORM, AccountService.PLATFORM_OWNER_ID));
        }

        List<SalaryDetail> salaryDetails = salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                .orderByAsc(SalaryDetail::getCreateTime, SalaryDetail::getId));
        for (SalaryDetail salaryDetail : salaryDetails) {
            records.add(checkAndSave(LedgerBizType.SALARY, salaryDetail.getId(), salaryDetail.getAmount(),
                    AccountOwnerType.USER, salaryDetail.getUserId()));
        }

        List<DistributorIncomeDetail> distributorIncomeDetails = distributorIncomeDetailMapper.selectList(
                new LambdaQueryWrapper<DistributorIncomeDetail>()
                        .orderByAsc(DistributorIncomeDetail::getCreateTime, DistributorIncomeDetail::getId));
        for (DistributorIncomeDetail incomeDetail : distributorIncomeDetails) {
            records.add(checkAndSave(LedgerBizType.DISTRIBUTOR, incomeDetail.getId(), incomeDetail.getIncomeAmount(),
                    AccountOwnerType.DISTRIBUTOR, incomeDetail.getDistributorId()));
        }

        int matchCount = (int) records.stream()
                .filter(record -> FinanceCheckStatus.MATCH.name().equals(record.getStatus()))
                .count();
        return new FinanceCheckResponse(records.size(), matchCount, records.size() - matchCount, records);
    }

    @Override
    public FinanceRefundRecordListResponse listRefundRecords(String refundScene,
                                                            Long orderId,
                                                            String status,
                                                            String orderNo,
                                                            Integer page,
                                                            Integer pageSize) {
        int currentPage = page == null || page < 1 ? 1 : page;
        int currentPageSize = pageSize == null || pageSize < 1
                ? DEFAULT_REFUND_RECORD_PAGE_SIZE
                : Math.min(pageSize, MAX_REFUND_RECORD_PAGE_SIZE);
        Set<Long> orderIdsByOrderNo = resolveOrderIdsByOrderNo(orderNo);
        if (StringUtils.hasText(orderNo) && orderIdsByOrderNo.isEmpty()) {
            return new FinanceRefundRecordListResponse(List.of(), 0L, currentPage, currentPageSize);
        }

        LambdaQueryWrapper<OrderRefundRecord> wrapper = new LambdaQueryWrapper<OrderRefundRecord>()
                .eq(refundScene != null && !refundScene.isBlank(), OrderRefundRecord::getRefundScene, refundScene)
                .eq(orderId != null, OrderRefundRecord::getOrderId, orderId)
                .eq(StringUtils.hasText(status), OrderRefundRecord::getStatus, status)
                .in(!orderIdsByOrderNo.isEmpty(), OrderRefundRecord::getOrderId, orderIdsByOrderNo)
                .orderByDesc(OrderRefundRecord::getCreateTime, OrderRefundRecord::getId);
        IPage<OrderRefundRecord> pageResult = orderRefundRecordMapper.selectPage(new Page<>(currentPage, currentPageSize), wrapper);
        List<OrderRefundRecord> records = pageResult.getRecords();
        if (records.isEmpty()) {
            return new FinanceRefundRecordListResponse(List.of(), pageResult.getTotal(), currentPage, currentPageSize);
        }

        Set<Long> refundRecordIds = records.stream()
                .map(OrderRefundRecord::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> orderIds = records.stream()
                .map(OrderRefundRecord::getOrderId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, Order> orderMap = orderIds.isEmpty()
                ? Map.of()
                : orderMapper.selectBatchIds(orderIds).stream()
                        .collect(Collectors.toMap(Order::getId, Function.identity(), (left, right) -> left));
        Map<Long, Customer> customerMap = loadCustomerMap(orderMap);
        Map<Long, SalaryReversalStat> salaryReversalStats = loadSalaryReversalStats(refundRecordIds);

        List<FinanceRefundRecordResponse> responseRecords = records.stream()
                .map(record -> {
                    Order order = orderMap.get(record.getOrderId());
                    Customer customer = order == null ? null : customerMap.get(order.getCustomerId());
                    return toRefundRecordResponse(record, order, customer, salaryReversalStats.get(record.getId()));
                })
                .toList();
        return new FinanceRefundRecordListResponse(responseRecords, pageResult.getTotal(), currentPage, currentPageSize);
    }

    @Override
    public boolean hasLedgerRecord(LedgerBizType bizType, Long bizId) {
        return ledgerService.hasRecord(bizType, bizId);
    }

    private void synchronizeLedger() {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .isNotNull(Order::getCompleteTime)
                .orderByAsc(Order::getCompleteTime, Order::getId));
        orders.forEach(this::recordOrderIncome);

        List<SalaryDetail> salaryDetails = salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                .orderByAsc(SalaryDetail::getCreateTime, SalaryDetail::getId));
        salaryDetails.forEach(this::recordSalaryIncome);

        List<DistributorIncomeDetail> distributorIncomeDetails = distributorIncomeDetailMapper.selectList(
                new LambdaQueryWrapper<DistributorIncomeDetail>()
                        .orderByAsc(DistributorIncomeDetail::getCreateTime, DistributorIncomeDetail::getId));
        distributorIncomeDetails.forEach(this::recordDistributorIncome);

        List<WithdrawRecord> withdrawRecords = withdrawRecordMapper.selectList(new LambdaQueryWrapper<WithdrawRecord>()
                .in(WithdrawRecord::getStatus, WithdrawStatus.APPROVED.name(), WithdrawStatus.PAID.name())
                .orderByAsc(WithdrawRecord::getCreateTime, WithdrawRecord::getId));
        withdrawRecords.forEach(this::recordUserWithdraw);

        List<DistributorWithdraw> distributorWithdraws = distributorWithdrawMapper.selectList(
                new LambdaQueryWrapper<DistributorWithdraw>()
                        .in(DistributorWithdraw::getStatus,
                                DistributorWithdrawStatus.APPROVED.name(),
                                DistributorWithdrawStatus.PAID.name())
                        .orderByAsc(DistributorWithdraw::getCreateTime, DistributorWithdraw::getId));
        distributorWithdraws.forEach(this::recordDistributorWithdraw);
    }

    private FinanceCheckItemResponse checkAndSave(LedgerBizType bizType,
                                                  Long bizId,
                                                  BigDecimal expectedAmount,
                                                  AccountOwnerType ownerType,
                                                  Long ownerId) {
        BigDecimal normalizedExpectedAmount = scale(expectedAmount);
        Account account = accountService.getOrCreateAccount(ownerType, ownerId);
        BigDecimal actualAmount = scale(ledgerService.getBizAmount(account.getId(), bizType, bizId));
        FinanceCheckStatus status = normalizedExpectedAmount.compareTo(actualAmount) == 0
                ? FinanceCheckStatus.MATCH
                : FinanceCheckStatus.MISMATCH;

        FinanceCheckRecord record = new FinanceCheckRecord();
        record.setBizType(bizType.name());
        record.setBizId(bizId);
        record.setExpectedAmount(normalizedExpectedAmount);
        record.setActualAmount(actualAmount);
        record.setStatus(status.name());
        record.setCreateTime(LocalDateTime.now());
        if (financeCheckRecordMapper.insert(record) <= 0) {
            throw new BusinessException("failed to create finance check record");
        }

        return new FinanceCheckItemResponse(bizType.name(), bizId, normalizedExpectedAmount, actualAmount, status.name());
    }

    private Set<Long> resolveOrderIdsByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return Set.of();
        }
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .like(Order::getOrderNo, orderNo.trim()))
                .stream()
                .map(Order::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<Long, Customer> loadCustomerMap(Map<Long, Order> orderMap) {
        Set<Long> customerIds = orderMap.values().stream()
                .map(Order::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        return customerMapper.selectBatchIds(customerIds).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, SalaryReversalStat> loadSalaryReversalStats(Set<Long> refundRecordIds) {
        if (refundRecordIds == null || refundRecordIds.isEmpty()) {
            return Map.of();
        }
        return salaryDetailMapper.selectList(new LambdaQueryWrapper<SalaryDetail>()
                        .in(SalaryDetail::getRefundRecordId, refundRecordIds)
                        .eq(SalaryDetail::getAdjustmentType, "REFUND_REVERSAL"))
                .stream()
                .filter(detail -> detail.getRefundRecordId() != null)
                .collect(Collectors.groupingBy(
                        SalaryDetail::getRefundRecordId,
                        Collectors.collectingAndThen(Collectors.toList(), this::toSalaryReversalStat)));
    }

    private SalaryReversalStat toSalaryReversalStat(List<SalaryDetail> details) {
        BigDecimal amount = details.stream()
                .map(SalaryDetail::getAmount)
                .filter(Objects::nonNull)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SalaryReversalStat(details.size(), scale(amount));
    }

    private FinanceRefundRecordResponse toRefundRecordResponse(OrderRefundRecord record,
                                                               Order order,
                                                               Customer customer,
                                                               SalaryReversalStat salaryReversalStat) {
        FinanceRefundRecordResponse response = new FinanceRefundRecordResponse();
        response.setRefundRecordId(record.getId());
        response.setOrderId(record.getOrderId());
        response.setOrderNo(order == null ? null : order.getOrderNo());
        response.setCustomerId(order == null ? null : order.getCustomerId());
        response.setCustomerName(customer == null ? null : customer.getName());
        response.setCustomerPhoneMasked(customer == null ? null : maskPhone(customer.getPhone()));
        response.setStoreName(order == null ? null : order.getAppointmentStoreName());
        response.setPlanOrderId(record.getPlanOrderId());
        response.setRefundScene(record.getRefundScene());
        response.setRefundObject(record.getRefundObject());
        response.setRefundAmount(record.getRefundAmount());
        response.setRefundReasonType(record.getRefundReasonType());
        response.setRefundReasonMasked(maskReason(record.getRefundReason()));
        response.setStatus(record.getStatus());
        response.setPlatformChannel(record.getPlatformChannel());
        response.setOperatorUserId(record.getOperatorUserId());
        response.setOperatorName(staffDirectoryService.getUserName(record.getOperatorUserId()));
        response.setReverseStorePerformance(isEnabled(record.getReverseStorePerformance()));
        response.setReverseCustomerService(isEnabled(record.getReverseCustomerService()));
        response.setReverseDistributor(isEnabled(record.getReverseDistributor()));
        response.setSalaryReversalCount(salaryReversalStat == null ? 0 : salaryReversalStat.count());
        response.setSalaryReversalAmount(salaryReversalStat == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : salaryReversalStat.amount());
        response.setFundsTransferred(false);
        response.setLedgerOnly(true);
        response.setCreateTime(record.getCreateTime());
        response.setUpdateTime(record.getUpdateTime());
        return response;
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String value = phone.trim();
        if (value.length() <= 7) {
            return value.charAt(0) + "***" + value.charAt(value.length() - 1);
        }
        return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
    }

    private String maskReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return null;
        }
        String value = reason.trim().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
        if (value.length() <= MAX_REFUND_REASON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_REFUND_REASON_LENGTH) + "...";
    }

    private boolean isEnabled(Integer value) {
        return value != null && value == 1;
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record SalaryReversalStat(int count, BigDecimal amount) {
    }
}
