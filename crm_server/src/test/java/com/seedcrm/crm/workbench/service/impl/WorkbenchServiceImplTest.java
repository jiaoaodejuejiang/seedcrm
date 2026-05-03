package com.seedcrm.crm.workbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.entity.ClueRecord;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.clue.service.ClueProfileService;
import com.seedcrm.crm.clue.service.ClueRecordService;
import com.seedcrm.crm.customer.mapper.CustomerEcomUserMapper;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.customer.mapper.CustomerTagDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorMapper;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.distributor.service.DistributorService;
import com.seedcrm.crm.order.entity.Order;
import com.seedcrm.crm.order.entity.OrderActionRecord;
import com.seedcrm.crm.order.mapper.OrderActionRecordMapper;
import com.seedcrm.crm.order.mapper.OrderMapper;
import com.seedcrm.crm.planorder.mapper.OrderRoleRecordMapper;
import com.seedcrm.crm.planorder.mapper.PlanOrderMapper;
import com.seedcrm.crm.salary.mapper.SalaryDetailMapper;
import com.seedcrm.crm.salary.mapper.WithdrawRecordMapper;
import com.seedcrm.crm.wecom.mapper.CustomerWecomRelationMapper;
import com.seedcrm.crm.wecom.mapper.WecomTouchLogMapper;
import com.seedcrm.crm.wecom.service.WecomConsoleService;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.ClueItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CluePageResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.OrderItemResponse;
import com.seedcrm.crm.workbench.service.StaffDirectoryService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkbenchServiceImplTest {

    @Mock
    private ClueMapper clueMapper;

    @Mock
    private ClueProfileService clueProfileService;

    @Mock
    private ClueRecordService clueRecordService;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private OrderActionRecordMapper orderActionRecordMapper;

    @Mock
    private PlanOrderMapper planOrderMapper;

    @Mock
    private OrderRoleRecordMapper orderRoleRecordMapper;

    @Mock
    private CustomerMapper customerMapper;

    @Mock
    private CustomerTagDetailMapper customerTagDetailMapper;

    @Mock
    private CustomerEcomUserMapper customerEcomUserMapper;

    @Mock
    private CustomerWecomRelationMapper customerWecomRelationMapper;

    @Mock
    private WecomTouchLogMapper wecomTouchLogMapper;

    @Mock
    private DistributorMapper distributorMapper;

    @Mock
    private DistributorIncomeDetailMapper distributorIncomeDetailMapper;

    @Mock
    private DistributorWithdrawMapper distributorWithdrawMapper;

    @Mock
    private SalaryDetailMapper salaryDetailMapper;

    @Mock
    private WithdrawRecordMapper withdrawRecordMapper;

    @Mock
    private DistributorService distributorService;

    @Mock
    private StaffDirectoryService staffDirectoryService;

    @Mock
    private WecomConsoleService wecomConsoleService;

    @Mock
    private DataSource dataSource;

    private WorkbenchServiceImpl workbenchService;

    @BeforeEach
    void setUp() {
        workbenchService = new WorkbenchServiceImpl(
                clueMapper,
                clueProfileService,
                clueRecordService,
                orderMapper,
                orderActionRecordMapper,
                planOrderMapper,
                orderRoleRecordMapper,
                customerMapper,
                customerTagDetailMapper,
                customerEcomUserMapper,
                customerWecomRelationMapper,
                wecomTouchLogMapper,
                distributorMapper,
                distributorIncomeDetailMapper,
                distributorWithdrawMapper,
                salaryDetailMapper,
                withdrawRecordMapper,
                distributorService,
                staffDirectoryService,
                wecomConsoleService,
                dataSource);
    }

    @Test
    void listCluesShouldReturnMoreThanLegacyTwentyRows() {
        List<Clue> clues = new ArrayList<>();
        for (long index = 1; index <= 35; index++) {
            clues.add(clue(index, "DOUYIN"));
        }
        mockCluePage(clues);
        when(customerMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(clueRecordService.listByClueIds(any())).thenReturn(List.of());
        when(clueProfileService.listByClueIds(any())).thenReturn(List.of());

        List<ClueItemResponse> responses = workbenchService.listClues(null, null, null);

        assertThat(responses).hasSize(35);
    }

    @Test
    void listCluesShouldMatchSourceChannelFromClueRecord() {
        Clue manualClue = clue(1L, "MANUAL");
        ClueRecord douyinRecord = new ClueRecord();
        douyinRecord.setId(10L);
        douyinRecord.setClueId(1L);
        douyinRecord.setRecordType("ORDER");
        douyinRecord.setSourceChannel("DOUYIN");
        douyinRecord.setTitle("订单同步");
        douyinRecord.setOccurredAt(LocalDateTime.now());
        mockCluePage(List.of(manualClue));
        when(customerMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(clueRecordService.listByClueIds(any())).thenReturn(List.of(douyinRecord));
        when(clueProfileService.listByClueIds(any())).thenReturn(List.of());

        List<ClueItemResponse> responses = workbenchService.listClues("DOUYIN", null, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(1L);
        assertThat(responses.get(0).getClueRecords()).hasSize(1);
        assertThat(responses.get(0).getClueRecords().get(0).getSourceChannel()).isEqualTo("DOUYIN");
    }

    @Test
    void pageCluesShouldReturnRequestedSliceAndTotal() {
        List<Clue> clues = new ArrayList<>();
        for (long index = 1; index <= 65; index++) {
            clues.add(clue(index, "DOUYIN"));
        }
        mockCluePage(clues);
        when(customerMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(clueRecordService.listByClueIds(any())).thenReturn(List.of());
        when(clueProfileService.listByClueIds(any())).thenReturn(List.of());

        CluePageResponse response = workbenchService.pageClues(
                null, null, null, null, null, null, null, 2, 30, clueId -> true);

        assertThat(response.getTotal()).isEqualTo(65L);
        assertThat(response.getRows()).hasSize(30);
        assertThat(response.getRows().get(0).getId()).isEqualTo(31L);
        assertThat(response.getProductSourceCounts().get("ALL")).isEqualTo(65L);
    }

    @Test
    void pageCluesShouldMatchSourceChannelFromRelatedOrder() {
        Clue manualClue = clue(1L, "MANUAL");
        Order douyinOrder = new Order();
        douyinOrder.setId(20L);
        douyinOrder.setClueId(1L);
        douyinOrder.setSourceChannel("DOUYIN");
        douyinOrder.setStatus("PAID_DEPOSIT");
        douyinOrder.setCreateTime(LocalDateTime.now());
        mockCluePage(List.of(manualClue));
        when(customerMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of(douyinOrder));
        when(clueRecordService.listByClueIds(any())).thenReturn(List.of());
        when(clueProfileService.listByClueIds(any())).thenReturn(List.of());

        CluePageResponse response = workbenchService.pageClues(
                "DOUYIN", null, null, null, null, null, null, 1, 30, clueId -> true);

        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getRows()).hasSize(1);
        assertThat(response.getRows().get(0).getLatestOrderId()).isEqualTo(20L);
    }

    @Test
    void pageCluesShouldFilterQueueStatusBeforePaging() {
        Clue waitAssign = clue(1L, "DOUYIN");
        Clue waitFollowUp = clue(2L, "DOUYIN");
        waitFollowUp.setCurrentOwnerId(100L);
        mockCluePage(List.of(waitAssign, waitFollowUp));
        when(customerMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(clueRecordService.listByClueIds(any())).thenReturn(List.of());
        when(clueProfileService.listByClueIds(any())).thenReturn(List.of());

        CluePageResponse response = workbenchService.pageClues(
                null, null, null, null, null, null, "WAIT_ASSIGN", 1, 30, clueId -> true);

        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getRows()).extracting(ClueItemResponse::getId).containsExactly(1L);
        assertThat(response.getQueueStatusCounts().get("ALL")).isEqualTo(2L);
        assertThat(response.getQueueStatusCounts().get("WAIT_ASSIGN")).isEqualTo(1L);
        assertThat(response.getQueueStatusCounts().get("WAIT_FOLLOW_UP")).isEqualTo(1L);
    }

    @Test
    void listOrdersShouldAttachFulfillmentRecordsAndKeepMoreThanTwentyRows() {
        List<Order> orders = new ArrayList<>();
        for (long index = 1; index <= 25; index++) {
            Order order = new Order();
            order.setId(index);
            order.setClueId(index);
            order.setCustomerId(1000L + index);
            order.setOrderNo("SO" + index);
            order.setType(1);
            order.setStatus("PAID_DEPOSIT");
            order.setVerificationStatus("UNVERIFIED");
            order.setCreateTime(LocalDateTime.now().minusMinutes(index));
            orders.add(order);
        }
        OrderActionRecord record = new OrderActionRecord();
        record.setId(101L);
        record.setOrderId(3L);
        record.setActionType("DIRECT_DEPOSIT_VERIFY");
        record.setFromStatus("PAID_DEPOSIT");
        record.setToStatus("PAID_DEPOSIT");
        record.setOperatorUserId(9001L);
        record.setRemark("DIRECT-DEPOSIT-3");
        record.setExtraJson("{\"verificationMethod\":\"DIRECT_DEPOSIT\",\"responsePayload\":\"secret payload\"}");
        record.setCreateTime(LocalDateTime.now());

        when(orderMapper.selectList(any())).thenReturn(orders);
        when(customerMapper.selectList(any())).thenReturn(List.of());
        when(clueProfileService.listByClueIds(any())).thenReturn(List.of());
        when(planOrderMapper.selectList(any())).thenReturn(List.of());
        when(orderActionRecordMapper.selectList(any())).thenReturn(List.of(), List.of(record));
        when(staffDirectoryService.getUserName(9001L)).thenReturn("Store Service A");

        List<OrderItemResponse> responses = workbenchService.listOrders(null, null, null);

        assertThat(responses).hasSize(25);
        OrderItemResponse target = responses.stream()
                .filter(item -> item.getId().equals(3L))
                .findFirst()
                .orElseThrow();
        assertThat(target.getFulfillmentRecords()).hasSize(1);
        assertThat(target.getFulfillmentRecords().get(0).getStage()).isEqualTo("VERIFY");
        assertThat(target.getFulfillmentRecords().get(0).getOperatorUserName()).isEqualTo("Store Service A");
        assertThat(target.getFulfillmentRecords().get(0).getSummary()).isEqualTo("定金免码确认");
        assertThat(target.getFulfillmentRecords().get(0).getDetailItems()).contains("核销方式：DIRECT_DEPOSIT");
        assertThat(target.getFulfillmentRecords().get(0).getRemark()).isNull();
        assertThat(target.getFulfillmentRecords().get(0).getExtraJson()).isNull();
    }

    private void mockCluePage(List<Clue> clues) {
        when(clueMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<Clue> page = invocation.getArgument(0);
            page.setRecords(clues);
            return page;
        });
    }

    private Clue clue(Long id, String sourceChannel) {
        Clue clue = new Clue();
        clue.setId(id);
        clue.setName("客资" + id);
        clue.setPhone("1380013" + String.format("%04d", id));
        clue.setSourceChannel(sourceChannel);
        clue.setSource("douyin");
        clue.setStatus("new");
        clue.setIsPublic(1);
        clue.setCreatedAt(LocalDateTime.now().minusMinutes(id));
        return clue;
    }
}
