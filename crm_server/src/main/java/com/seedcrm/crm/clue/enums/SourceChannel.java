package com.seedcrm.crm.clue.enums;

import org.springframework.util.StringUtils;

public enum SourceChannel {
    DOUYIN,
    DISTRIBUTOR,
    OTHER;

    public static String resolveCode(String sourceChannel, String legacySource) {
        if (StringUtils.hasText(sourceChannel)) {
            String normalized = sourceChannel.trim().toUpperCase();
            for (SourceChannel value : values()) {
                if (value.name().equals(normalized)) {
                    return value.name();
                }
            }
        }

        if (!StringUtils.hasText(legacySource)) {
            return DOUYIN.name();
        }

        String normalizedSource = legacySource.trim().toUpperCase();
        if ("抖音".equals(legacySource.trim()) || normalizedSource.contains("DOUYIN")) {
            return DOUYIN.name();
        }
        if ("分销".equals(legacySource.trim()) || normalizedSource.contains("DISTRIBUTOR")) {
            return DISTRIBUTOR.name();
        }
        return OTHER.name();
    }

    public static String resolveLegacySource(String sourceChannel, String legacySource) {
        if (StringUtils.hasText(legacySource)) {
            return legacySource.trim();
        }

        return switch (resolveCode(sourceChannel, legacySource)) {
            case "DISTRIBUTOR" -> "distributor";
            case "OTHER" -> "other";
            default -> "douyin";
        };
    }
}
