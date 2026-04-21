package com.seedcrm.crm.order.enums;

public enum OrderType {
    DEPOSIT(1),
    GROUP_BUY(2),
    SUPPLEMENTARY_PAYMENT(3);

    private final int code;

    OrderType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static boolean isValid(Integer code) {
        if (code == null) {
            return false;
        }
        for (OrderType value : values()) {
            if (value.code == code) {
                return true;
            }
        }
        return false;
    }
}
