package com.seedcrm.crm.order.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class OrderAmountMaskingSupport {

    public static final String MASK_MARKER = "_amountsMasked";

    private OrderAmountMaskingSupport() {
    }

    public static String maskServiceDetailJson(String serviceDetailJson, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(serviceDetailJson)) {
            return serviceDetailJson;
        }
        try {
            JsonNode root = objectMapper.readTree(serviceDetailJson.trim());
            if (!root.isObject()) {
                return null;
            }
            ObjectNode copy = root.deepCopy();
            maskAmountFields(copy);
            copy.put(MASK_MARKER, true);
            return objectMapper.writeValueAsString(copy);
        } catch (Exception exception) {
            return null;
        }
    }

    public static void restoreMaskedAmountFields(ObjectNode targetRoot, String originalServiceDetailJson, ObjectMapper objectMapper) {
        if (targetRoot == null || !targetRoot.path(MASK_MARKER).asBoolean(false)) {
            return;
        }
        targetRoot.remove(MASK_MARKER);
        if (!StringUtils.hasText(originalServiceDetailJson)) {
            return;
        }
        try {
            JsonNode originalRoot = objectMapper.readTree(originalServiceDetailJson.trim());
            restoreAmountFields(targetRoot, originalRoot);
        } catch (Exception ignored) {
            // If the historical payload is invalid, keep the incoming sanitized payload.
        }
    }

    private static void maskAmountFields(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveAmountKey(field.getKey())) {
                    objectNode.set(field.getKey(), NullNode.getInstance());
                } else {
                    maskAmountFields(field.getValue());
                }
            }
            return;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            arrayNode.forEach(OrderAmountMaskingSupport::maskAmountFields);
        }
    }

    private static void restoreAmountFields(JsonNode target, JsonNode original) {
        if (target == null || original == null || target.isNull() || original.isNull()) {
            return;
        }
        if (target.isObject() && original.isObject()) {
            ObjectNode targetObject = (ObjectNode) target;
            Iterator<Map.Entry<String, JsonNode>> fields = original.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> originalField = fields.next();
                String fieldName = originalField.getKey();
                if (isSensitiveAmountKey(fieldName)) {
                    targetObject.set(fieldName, originalField.getValue().deepCopy());
                    continue;
                }
                JsonNode targetChild = targetObject.get(fieldName);
                if (targetChild != null) {
                    restoreAmountFields(targetChild, originalField.getValue());
                }
            }
            return;
        }
        if (target.isArray() && original.isArray()) {
            ArrayNode targetArray = (ArrayNode) target;
            int size = Math.min(targetArray.size(), original.size());
            for (int index = 0; index < size; index += 1) {
                restoreAmountFields(targetArray.get(index), original.get(index));
            }
        }
    }

    private static boolean isSensitiveAmountKey(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String rawKey = key.trim();
        String normalized = key.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.contains("amount")
                || normalized.contains("money")
                || normalized.contains("price")
                || normalized.contains("deposit")
                || normalized.contains("refund")
                || normalized.contains("fee")
                || rawKey.contains("金额")
                || rawKey.contains("价格")
                || rawKey.contains("定金")
                || rawKey.contains("退款")
                || rawKey.contains("费用")
                || rawKey.contains("收费")
                || rawKey.contains("价款")
                || rawKey.contains("总价")
                || rawKey.contains("单价");
    }
}
