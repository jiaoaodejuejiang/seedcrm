package com.seedcrm.crm.workbench.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seedcrm.crm.clue.entity.Clue;
import com.seedcrm.crm.clue.entity.ClueRecord;
import com.seedcrm.crm.clue.mapper.ClueMapper;
import com.seedcrm.crm.clue.service.ClueRecordService;
import com.seedcrm.crm.customer.mapper.CustomerEcomUserMapper;
import com.seedcrm.crm.customer.mapper.CustomerMapper;
import com.seedcrm.crm.customer.mapper.CustomerTagDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorIncomeDetailMapper;
import com.seedcrm.crm.distributor.mapper.DistributorMapper;
import com.seedcrm.crm.distributor.mapper.DistributorWithdrawMapper;
import com.seedcrm.crm.distributor.service.DistributorService;
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

        List<ClueItemResponse> responses = workbenchService.listClues("DOUYIN", null, null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(1L);
        assertThat(responses.get(0).getClueRecords()).hasSize(1);
        assertThat(responses.get(0).getClueRecords().get(0).getSourceChannel()).isEqualTo("DOUYIN");
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
