package com.seedcrm.crm.order.enums;

public enum OrderType {
    DEPOSIT(1, "deposit"),
    COUPON(2, "coupon");

    private final int code;
    private final String apiValue;

    OrderType(int code, String apiValue) {
        this.code = code;
        this.apiValue = apiValue;
    }

    public int getCode() {
        return code;
    }

    public String getApiValue() {
        return apiValue;
    }

    public static boolean isValid(Integer code) {
        return normalizeCode(code) != null;
    }

    public static Integer normalizeCode(Integer code) {
        if (code == null) {
            return null;
        }
        if (code == 1) {
            return DEPOSIT.code;
        }
        if (code == 2 || code == 3) {
            return COUPON.code;
        }
        return null;
    }

    public static String toApiValue(Integer code) {
        Integer normalizedCode = normalizeCode(code);
        if (normalizedCode == null) {
            return null;
        }
        for (OrderType value : values()) {
            if (value.code == normalizedCode) {
                return value.apiValue;
            }
        }
        return null;
    }
}
