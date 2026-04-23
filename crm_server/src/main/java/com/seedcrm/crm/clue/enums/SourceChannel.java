package com.seedcrm.crm.clue.enums;

import org.springframework.util.StringUtils;

public enum SourceChannel {
    DOUYIN,
    DISTRIBUTOR,
    OTHER;

    public static String resolveCode(String sourceChannel, String legacySource) {
        if (StringUtils.hasText(sourceChannel)) {
            String normalized = sourceChannel.trim().toUpperCase();
            if ("DISTRIBUTION".equals(normalized)) {
                return DISTRIBUTOR.name();
            }
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
        if (normalizedSource.contains("DOUYIN")) {
            return DOUYIN.name();
        }
        if (normalizedSource.contains("DISTRIBUTOR") || normalizedSource.contains("DISTRIBUTION")) {
            return DISTRIBUTOR.name();
        }
        return OTHER.name();
    }

    public static String resolveLegacySource(String sourceChannel, String legacySource) {
        if (StringUtils.hasText(legacySource)) {
            String normalized = legacySource.trim();
            if ("distributor".equalsIgnoreCase(normalized)) {
                return "distribution";
            }
            return normalized;
        }

        return switch (resolveCode(sourceChannel, legacySource)) {
            case "DISTRIBUTOR" -> "distribution";
            case "OTHER" -> "other";
            default -> "douyin";
        };
    }
}
