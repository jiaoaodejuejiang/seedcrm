package com.seedcrm.crm.wecom.support;

import com.seedcrm.crm.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

public final class WecomBindingStateCodec {

    private static final String PREFIX = "sc";
    private static final String V2_PREFIX = "SC2";
    private static final String DEFAULT_SECRET = "seedcrm-wecom-binding-state-v1";
    private static final int ID_WIDTH = 13;
    private static final int EXPIRES_WIDTH = 8;
    private static final int PHONE_HASH_WIDTH = 12;
    private static final int SIGNATURE_WIDTH = 32;

    private WecomBindingStateCodec() {
    }

    public static String encode(Long orderId, Long userId, Long customerId) {
        return encode(orderId, userId, customerId, null);
    }

    public static String encode(Long orderId, Long userId, Long customerId, String userPhone) {
        if (orderId == null || userId == null || customerId == null) {
            throw new BusinessException("生成企微绑定 state 需要订单、员工和客户信息");
        }
        String expiresAt = toBase36(Instant.now().plus(48, ChronoUnit.HOURS).getEpochSecond());
        String payload = toFixedBase36(orderId, ID_WIDTH)
                + toFixedBase36(userId, ID_WIDTH)
                + toFixedBase36(customerId, ID_WIDTH)
                + leftPad(expiresAt, EXPIRES_WIDTH)
                + hashPhone(userPhone);
        return V2_PREFIX + payload + signature(payload);
    }

    public static BindingState decode(String state) {
        if (!StringUtils.hasText(state)) {
            return null;
        }
        if (state.startsWith(V2_PREFIX)) {
            return decodeV2(state);
        }
        if (!state.startsWith(PREFIX + ".")) {
            return null;
        }
        return decodeLegacy(state);
    }

    public static boolean matchesPhoneHash(String phone, String expectedHash) {
        if (!StringUtils.hasText(expectedHash)) {
            return true;
        }
        if ("x".equals(expectedHash)) {
            return !StringUtils.hasText(phone);
        }
        return StringUtils.hasText(phone) && expectedHash.equals(hashPhone(phone));
    }

    private static BindingState decodeLegacy(String state) {
        String[] parts = state.split("\\.");
        if (parts.length != 6 && parts.length != 7) {
            return null;
        }
        boolean legacyState = parts.length == 6;
        String payload = legacyState
                ? parts[1] + "." + parts[2] + "." + parts[3] + "." + parts[4]
                : parts[1] + "." + parts[2] + "." + parts[3] + "." + parts[4] + "." + parts[5];
        String expectedSignature = legacySignature(payload);
        String actualSignature = legacyState ? parts[5] : parts[6];
        if (!expectedSignature.equals(actualSignature) && !expectedSignature.startsWith(actualSignature)) {
            throw new BusinessException("企微回调 state 校验失败");
        }
        try {
            Long expiresAt = fromBase36(parts[4]);
            if (expiresAt < Instant.now().getEpochSecond()) {
                throw new BusinessException("企微回调 state 已过期");
            }
            return new BindingState(fromBase36(parts[1]), fromBase36(parts[2]), fromBase36(parts[3]), legacyState ? null : parts[5]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static BindingState decodeV2(String state) {
        int payloadStart = V2_PREFIX.length();
        int payloadLength = ID_WIDTH * 3 + EXPIRES_WIDTH + PHONE_HASH_WIDTH;
        int expectedLength = payloadStart + payloadLength + SIGNATURE_WIDTH;
        if (state.length() != expectedLength) {
            return null;
        }
        String payload = state.substring(payloadStart, payloadStart + payloadLength);
        String actualSignature = state.substring(payloadStart + payloadLength);
        String expectedSignature = signature(payload);
        if (!expectedSignature.equals(actualSignature)) {
            throw new BusinessException("企微回调 state 校验失败");
        }
        try {
            Long orderId = fromBase36(payload.substring(0, ID_WIDTH));
            Long userId = fromBase36(payload.substring(ID_WIDTH, ID_WIDTH * 2));
            Long customerId = fromBase36(payload.substring(ID_WIDTH * 2, ID_WIDTH * 3));
            Long expiresAt = fromBase36(payload.substring(ID_WIDTH * 3, ID_WIDTH * 3 + EXPIRES_WIDTH));
            if (expiresAt < Instant.now().getEpochSecond()) {
                throw new BusinessException("企微回调 state 已过期");
            }
            String userPhoneHash = payload.substring(ID_WIDTH * 3 + EXPIRES_WIDTH);
            return new BindingState(orderId, userId, customerId, userPhoneHash);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String signature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(resolveSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return toHex(digest).substring(0, SIGNATURE_WIDTH);
        } catch (Exception exception) {
            throw new BusinessException("生成企微绑定 state 失败");
        }
    }

    private static String legacySignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(resolveSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (Exception exception) {
            throw new BusinessException("生成企微绑定 state 失败");
        }
    }

    private static String hashPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return "x";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(phone.trim().getBytes(StandardCharsets.UTF_8));
            return toHex(hashed).substring(0, PHONE_HASH_WIDTH);
        } catch (Exception exception) {
            throw new BusinessException("生成企微绑定手机号摘要失败");
        }
    }

    private static String resolveSecret() {
        String configured = System.getenv("SEEDCRM_WECOM_STATE_SECRET");
        return StringUtils.hasText(configured) ? configured.trim() : DEFAULT_SECRET;
    }

    private static String toBase36(Long value) {
        return Long.toString(value, 36);
    }

    private static String toFixedBase36(Long value, int width) {
        return leftPad(toBase36(value), width);
    }

    private static String leftPad(String value, int width) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > width) {
            throw new BusinessException("企微绑定 state 字段长度超限");
        }
        return "0".repeat(width - normalized.length()) + normalized;
    }

    private static Long fromBase36(String value) {
        return Long.parseLong(value, 36);
    }

    private static String toHex(byte[] values) {
        StringBuilder builder = new StringBuilder(values.length * 2);
        for (byte value : values) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    public record BindingState(Long orderId, Long userId, Long customerId, String userPhoneHash) {
    }
}
