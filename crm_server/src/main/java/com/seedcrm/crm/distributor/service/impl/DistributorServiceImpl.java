package com.seedcrm.crm.distributor.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.distributor.dto.DistributorCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorStatsResponse;
import com.seedcrm.crm.distributor.entity.Distributor;
import com.seedcrm.crm.distributor.mapper.DistributorMapper;
import com.seedcrm.crm.distributor.service.DistributorService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DistributorServiceImpl extends ServiceImpl<DistributorMapper, Distributor> implements DistributorService {

    private final DistributorMapper distributorMapper;
    private final ClueMapper clueMapper;
    private final OrderMapper orderMapper;

    public DistributorServiceImpl(DistributorMapper distributorMapper,
                                  ClueMapper clueMapper,
                                  OrderMapper orderMapper) {
        this.distributorMapper = distributorMapper;
        this.clueMapper = clueMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public Distributor createDistributor(DistributorCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessException("distributor name is required");
        }

        Distributor distributor = new Distributor();
        distributor.setName(request.getName().trim());
        distributor.setContactInfo(StringUtils.hasText(request.getContactInfo()) ? request.getContactInfo().trim() : null);
        distributor.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : "ACTIVE");
        distributor.setCreateTime(LocalDateTime.now());
        if (distributorMapper.insert(distributor) <= 0) {
            throw new BusinessException("failed to create distributor");
        }
        return distributor;
    }

    @Override
    public Distributor getByIdOrThrow(Long distributorId) {
        if (distributorId == null || distributorId <= 0) {
            throw new BusinessException("distributorId is required");
        }
        Distributor distributor = distributorMapper.selectById(distributorId);
        if (distributor == null) {
            throw new BusinessException("distributor not found");
        }
        return distributor;
    }

    @Override
    public DistributorStatsResponse getStats(Long distributorId) {
        getByIdOrThrow(distributorId);

        Long clueCount = clueMapper.selectCount(Wrappers.<Clue>lambdaQuery()
                .eq(Clue::getSourceChannel, SourceChannel.DISTRIBUTOR.name())
                .eq(Clue::getSourceId, distributorId));

        List<Order> distributorOrders = orderMapper.selectList(Wrappers.<Order>lambdaQuery()
                .eq(Order::getSourceChannel, SourceChannel.DISTRIBUTOR.name())
                .eq(Order::getSourceId, distributorId));

        long orderCount = distributorOrders.size();
        long dealCustomerCount = distributorOrders.stream()
                .filter(order -> order.getCustomerId() != null)
                .filter(this::isDealOrder)
                .map(Order::getCustomerId)
                .distinct()
                .count();

        return new DistributorStatsResponse(distributorId, safeLong(clueCount), dealCustomerCount, orderCount);
    }

    private boolean isDealOrder(Order order) {
        if (order == null || !StringUtils.hasText(order.getStatus())) {
            return false;
        }
        return OrderStatus.PAID_DEPOSIT.name().equals(order.getStatus())
                || OrderStatus.APPOINTMENT.name().equals(order.getStatus())
                || OrderStatus.ARRIVED.name().equals(order.getStatus())
                || OrderStatus.SERVING.name().equals(order.getStatus())
                || OrderStatus.COMPLETED.name().equals(order.getStatus());
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
