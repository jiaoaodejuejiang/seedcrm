package com.seedcrm.crm.order.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class OrderAmountMaskingSupport {

    public static final String MASK_MARKER = "_amountsMasked";
    private static final Set<String> AMOUNT_TOKENS = Set.of(
            "amount",
            "money",
            "price",
            "deposit",
            "refund",
            "fee",
            "charge",
            "cost",
            "commission",
            "income",
            "settlement");
    private static final Set<String> COMBINED_AMOUNT_KEYS = Set.of(
            "unitprice",
            "totalprice",
            "totalamount",
            "totalmoney",
            "totalfee",
            "totalcost",
            "grandtotal",
            "subtotal",
            "actualpayamount",
            "paidamount",
            "paymentamount",
            "verificationamount",
            "serviceconfirmamount");

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
        String normalized = rawKey.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return splitAsciiTokens(rawKey).stream().anyMatch(AMOUNT_TOKENS::contains)
                || COMBINED_AMOUNT_KEYS.stream().anyMatch(normalized::contains)
                || rawKey.contains("\u91d1\u989d")
                || rawKey.contains("\u4ef7\u683c")
                || rawKey.contains("\u5b9a\u91d1")
                || rawKey.contains("\u9000\u6b3e")
                || rawKey.contains("\u8d39\u7528")
                || rawKey.contains("\u6536\u8d39")
                || rawKey.contains("\u4ef7\u6b3e")
                || rawKey.contains("\u603b\u4ef7")
                || rawKey.contains("\u5355\u4ef7")
                || rawKey.contains("\u6838\u9500\u91d1\u989d")
                || rawKey.contains("\u652f\u4ed8\u91d1\u989d")
                || rawKey.contains("\u5b9e\u4ed8")
                || rawKey.contains("\u5e94\u4ed8")
                || rawKey.contains("\u4ed8\u6b3e\u91d1\u989d")
                || rawKey.contains("\u5206\u4f63")
                || rawKey.contains("\u4f63\u91d1")
                || rawKey.contains("\u7ed3\u7b97\u91d1\u989d");
    }

    private static Set<String> splitAsciiTokens(String rawKey) {
        String spaced = rawKey
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("[^A-Za-z0-9]+", " ");
        return Arrays.stream(spaced.split("\\s+"))
                .map(token -> token.trim().toLowerCase(Locale.ROOT))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
    }
}
