package com.seedcrm.crm.order.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

public final class OrderNoGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private OrderNoGenerator() {
    }

    public static String generate() {
        int randomNumber = ThreadLocalRandom.current().nextInt(1000, 10000);
        return "ORD" + LocalDateTime.now().format(FORMATTER) + randomNumber;
    }
}
