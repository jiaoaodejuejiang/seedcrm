package com.seedcrm.crm.workbench.service;

import com.seedcrm.crm.workbench.dto.WorkbenchResponses.ClueItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.CustomerProfileResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.DistributorBoardItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.FinanceOverviewResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.OrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderItemResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.PlanOrderWorkbenchResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StaffRoleOptionResponse;
import com.seedcrm.crm.workbench.dto.WorkbenchResponses.StoreLiveCodePreviewResponse;
import java.util.List;

public interface WorkbenchService {

    List<ClueItemResponse> listClues(String sourceChannel, String productSourceType, String status);

    List<OrderItemResponse> listOrders(String status, String customerName, String customerPhone);

    StoreLiveCodePreviewResponse getOrderLiveCodePreview(Long orderId);

    List<PlanOrderItemResponse> listPlanOrders(String status);

    PlanOrderWorkbenchResponse getPlanOrderWorkbench(Long planOrderId);

    CustomerProfileResponse getCustomerProfile(Long customerId);

    List<DistributorBoardItemResponse> listDistributors();

    FinanceOverviewResponse getFinanceOverview();

    List<StaffRoleOptionResponse> listStaffOptions();
}
