package com.seedcrm.crm.distributor.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.seedcrm.crm.clue.enums.SourceChannel;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.distributor.dto.DistributorCreateRequest;
import com.seedcrm.crm.distributor.dto.DistributorStatsResponse;
import com.seedcrm.crm.distributor.entity.Distributor;
import com.seedcrm.crm.distributor.mapper.DistributorMapper;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.enums.OrderStatus;
import com.seedcrm.crm.order.mapper.OrderMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DistributorServiceImplTest {

    @Mock
    private DistributorMapper distributorMapper;

    @Mock
    private ClueMapper clueMapper;

    @Mock
    private OrderMapper orderMapper;

    private DistributorServiceImpl distributorService;

    @BeforeEach
    void setUp() {
        distributorService = new DistributorServiceImpl(distributorMapper, clueMapper, orderMapper);
    }

    @Test
    void createDistributorShouldPersistDefaultStatus() {
        when(distributorMapper.insert(any(Distributor.class))).thenAnswer(invocation -> {
            Distributor distributor = invocation.getArgument(0);
            distributor.setId(5L);
            return 1;
        });

        DistributorCreateRequest request = new DistributorCreateRequest();
        request.setName("east-distributor");
        request.setContactInfo("wechat:hd001");

        Distributor distributor = distributorService.createDistributor(request);

        assertThat(distributor.getId()).isEqualTo(5L);
        assertThat(distributor.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getStatsShouldCountDistributorCluesDealsAndOrders() {
        Distributor distributor = new Distributor();
        distributor.setId(9L);
        when(distributorMapper.selectById(9L)).thenReturn(distributor);
        when(clueMapper.selectCount(any())).thenReturn(3L);

        Order createdOrder = new Order();
        createdOrder.setCustomerId(101L);
        createdOrder.setStatus(OrderStatus.CREATED.name());
        createdOrder.setSourceChannel(SourceChannel.DISTRIBUTOR.name());
        createdOrder.setSourceId(9L);

        Order paidOrder = new Order();
        paidOrder.setCustomerId(102L);
        paidOrder.setStatus(OrderStatus.PAID_DEPOSIT.name());
        paidOrder.setSourceChannel(SourceChannel.DISTRIBUTOR.name());
        paidOrder.setSourceId(9L);

        Order completedOrder = new Order();
        completedOrder.setCustomerId(102L);
        completedOrder.setStatus(OrderStatus.COMPLETED.name());
        completedOrder.setSourceChannel(SourceChannel.DISTRIBUTOR.name());
        completedOrder.setSourceId(9L);

        when(orderMapper.selectList(any())).thenReturn(List.of(createdOrder, paidOrder, completedOrder));

        DistributorStatsResponse stats = distributorService.getStats(9L);

        assertThat(stats.getDistributorId()).isEqualTo(9L);
        assertThat(stats.getClueCount()).isEqualTo(3L);
        assertThat(stats.getOrderCount()).isEqualTo(3L);
        assertThat(stats.getDealCustomerCount()).isEqualTo(1L);
    }
}
