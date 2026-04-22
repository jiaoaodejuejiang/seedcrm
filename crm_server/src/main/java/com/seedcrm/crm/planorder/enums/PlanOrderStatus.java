package com.seedcrm.crm.planorder.enums;

public enum PlanOrderStatus {
    ARRIVED,
    SERVICING,
    FINISHED;

    public PlanOrderStatus nextNormalStatus() {
        return switch (this) {
            case ARRIVED -> SERVICING;
            case SERVICING -> FINISHED;
            case FINISHED -> null;
        };
    }
}
