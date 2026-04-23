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

    public String getApiValue() {
        return name().toLowerCase();
    }

    public static String toApiValue(String status) {
        if (status == null) {
            return null;
        }
        try {
            return PlanOrderStatus.valueOf(status).getApiValue();
        } catch (IllegalArgumentException exception) {
            return status.toLowerCase();
        }
    }
}
