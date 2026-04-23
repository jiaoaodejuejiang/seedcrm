package com.seedcrm.crm.order.enums;

public enum OrderStatus {
    CREATED,
    PAID_DEPOSIT,
    APPOINTMENT,
    ARRIVED,
    SERVING,
    COMPLETED,
    CANCELLED,
    REFUNDED;

    public String getApiValue() {
        return this == COMPLETED ? "used" : "paid";
    }

    public OrderStatus nextNormalStatus() {
        return switch (this) {
            case CREATED -> PAID_DEPOSIT;
            case PAID_DEPOSIT -> APPOINTMENT;
            case APPOINTMENT -> ARRIVED;
            case ARRIVED -> SERVING;
            case SERVING -> COMPLETED;
            default -> null;
        };
    }

    public boolean canCancel() {
        return this == CREATED
                || this == PAID_DEPOSIT
                || this == APPOINTMENT
                || this == ARRIVED
                || this == SERVING;
    }

    public boolean canRefund() {
        return this == PAID_DEPOSIT
                || this == APPOINTMENT
                || this == ARRIVED
                || this == SERVING
                || this == COMPLETED;
    }

    public boolean isPaidStage() {
        return this == PAID_DEPOSIT
                || this == APPOINTMENT
                || this == ARRIVED
                || this == SERVING
                || this == COMPLETED;
    }

    public static String toApiValue(String status) {
        if (status == null) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status).getApiValue();
        } catch (IllegalArgumentException exception) {
            return status.toLowerCase();
        }
    }
}
