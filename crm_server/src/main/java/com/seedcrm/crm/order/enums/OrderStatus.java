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
}
