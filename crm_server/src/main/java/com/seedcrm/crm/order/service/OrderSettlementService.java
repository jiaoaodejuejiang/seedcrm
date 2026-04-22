package com.seedcrm.crm.order.service;

import com.seedcrm.crm.order.entity.Order;

public interface OrderSettlementService {

    Order settleCompletedOrder(Long orderId);
}
