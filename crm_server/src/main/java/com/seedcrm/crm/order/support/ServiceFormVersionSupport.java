package com.seedcrm.crm.order.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.seedcrm.crm.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class ServiceFormVersionSupport {

    public static final String PROJECTION_VERSION = "SERVICE_FORM_PRINTABLE_V1";
    public static final String PRINT_STATUS_PRINTED = "PRINTED";
    public static final String PRINT_STATUS_STALE = "STALE";
    public static final String CONFIRM_STATUS = "PRINT_CONFIRMED";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ServiceFormVersionSupport() {
    }

    public static ObjectNode parseRoot(String serviceDetailJson, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(serviceDetailJson)) {
            throw new BusinessException("service form must be saved before this action");
        }
        try {
            JsonNode parsed = objectMapper.readTree(serviceDetailJson.trim());
            if (parsed == null || !parsed.isObject()) {
                throw new BusinessException("service form json is invalid");
            }
            return (ObjectNode) parsed;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("service form json is invalid");
        }
    }

    public static String printableHash(String serviceDetailJson, ObjectMapper objectMapper) {
        return printableHash(parseRoot(serviceDetailJson, objectMapper), objectMapper);
    }

    public static String printableHash(ObjectNode root, ObjectMapper objectMapper) {
        try {
            JsonNode projection = canonicalize(printableProjection(root, objectMapper), objectMapper);
            String payload = PROJECTION_VERSION + ":" + objectMapper.writeValueAsString(projection);
            return sha256Hex(payload);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("failed to calculate service form version");
        }
    }

    public static void reconcileStateAfterSave(ObjectNode currentRoot, String originalServiceDetailJson, ObjectMapper objectMapper) {
        if (currentRoot == null || !StringUtils.hasText(originalServiceDetailJson)) {
            return;
        }
        ObjectNode originalRoot;
        try {
            JsonNode parsed = objectMapper.readTree(originalServiceDetailJson.trim());
            if (parsed == null || !parsed.isObject()) {
                return;
            }
            originalRoot = (ObjectNode) parsed;
        } catch (Exception ignored) {
            return;
        }

        String originalHash = printableHash(originalRoot, objectMapper);
        String currentHash = printableHash(currentRoot, objectMapper);
        JsonNode originalPrintAudit = originalRoot.get("printAudit");
        JsonNode originalConfirmation = originalRoot.get("confirmation");
        boolean originalContentUnchanged = originalHash.equals(currentHash);

        if (originalContentUnchanged) {
            if (originalPrintAudit != null && !currentRoot.has("printAudit")) {
                currentRoot.set("printAudit", originalPrintAudit.deepCopy());
            }
            if (originalConfirmation != null && !currentRoot.has("confirmation")) {
                currentRoot.set("confirmation", originalConfirmation.deepCopy());
            }
            if (originalRoot.has("serviceFormStatus") && !currentRoot.has("serviceFormStatus")) {
                currentRoot.set("serviceFormStatus", originalRoot.get("serviceFormStatus").deepCopy());
            }
            return;
        }

        if (hasCurrentPrintAudit(currentRoot, currentHash)) {
            return;
        }
        if (hasCurrentPrintAudit(originalRoot, currentHash)) {
            currentRoot.set("printAudit", originalRoot.get("printAudit").deepCopy());
            preserveCurrentConfirmation(currentRoot, originalRoot, currentHash);
            return;
        }
        if (originalPrintAudit != null || currentRoot.has("printAudit")) {
            ObjectNode staleAudit = currentRoot.has("printAudit") && currentRoot.get("printAudit").isObject()
                    ? (ObjectNode) currentRoot.get("printAudit").deepCopy()
                    : originalPrintAudit != null && originalPrintAudit.isObject()
                    ? (ObjectNode) originalPrintAudit.deepCopy()
                    : objectMapper.createObjectNode();
            staleAudit.put("status", PRINT_STATUS_STALE);
            staleAudit.put("staleReason", "CONTENT_CHANGED");
            staleAudit.put("staleAt", DATE_TIME_FORMATTER.format(LocalDateTime.now()));
            staleAudit.put("currentServiceDetailHash", currentHash);
            currentRoot.set("printAudit", staleAudit);
        }
        currentRoot.remove("confirmation");
        currentRoot.remove("serviceFormStatus");
    }

    public static boolean hasCurrentPrintAudit(ObjectNode root, String currentHash) {
        if (root == null || !StringUtils.hasText(currentHash)) {
            return false;
        }
        JsonNode printAudit = root.path("printAudit");
        return PRINT_STATUS_PRINTED.equalsIgnoreCase(printAudit.path("status").asText(""))
                && currentHash.equals(printAudit.path("serviceDetailHash").asText(""));
    }

    public static boolean hasCurrentConfirmation(ObjectNode root, String currentHash) {
        if (root == null) {
            return false;
        }
        JsonNode confirmation = root.path("confirmation");
        String nestedStatus = confirmation.path("status").asText("");
        String flatStatus = root.path("serviceFormStatus").asText("");
        boolean confirmed = CONFIRM_STATUS.equalsIgnoreCase(nestedStatus)
                || CONFIRM_STATUS.equalsIgnoreCase(flatStatus);
        if (!confirmed) {
            return false;
        }
        String confirmedHash = confirmation.path("serviceDetailHash").asText("");
        if (!StringUtils.hasText(confirmedHash)) {
            return true;
        }
        return StringUtils.hasText(currentHash) && currentHash.equals(confirmedHash);
    }

    public static boolean hasVersionedConfirmation(ObjectNode root) {
        return root != null && StringUtils.hasText(root.path("confirmation").path("serviceDetailHash").asText(""));
    }

    public static void clearConfirmationIfHashMismatch(ObjectNode root, String currentHash) {
        if (root == null) {
            return;
        }
        JsonNode confirmation = root.path("confirmation");
        String confirmedHash = confirmation.path("serviceDetailHash").asText("");
        if (StringUtils.hasText(confirmedHash) && !confirmedHash.equals(currentHash)) {
            root.remove("confirmation");
            root.remove("serviceFormStatus");
        }
    }

    private static void preserveCurrentConfirmation(ObjectNode currentRoot, ObjectNode originalRoot, String currentHash) {
        JsonNode originalConfirmation = originalRoot.get("confirmation");
        if (originalConfirmation == null || !originalConfirmation.isObject()) {
            return;
        }
        String originalConfirmationHash = originalConfirmation.path("serviceDetailHash").asText("");
        if (currentHash.equals(originalConfirmationHash)) {
            currentRoot.set("confirmation", originalConfirmation.deepCopy());
            if (originalRoot.has("serviceFormStatus")) {
                currentRoot.set("serviceFormStatus", originalRoot.get("serviceFormStatus").deepCopy());
            }
        }
    }

    private static ObjectNode printableProjection(ObjectNode root, ObjectMapper objectMapper) {
        ObjectNode copy = root == null ? objectMapper.createObjectNode() : root.deepCopy();
        removeVolatileFields(copy);
        return copy;
    }

    private static void removeVolatileFields(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            List<String> removeKeys = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isVolatileField(field.getKey())) {
                    removeKeys.add(field.getKey());
                } else {
                    removeVolatileFields(field.getValue());
                }
            }
            removeKeys.forEach(objectNode::remove);
            return;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            arrayNode.forEach(ServiceFormVersionSupport::removeVolatileFields);
        }
    }

    private static boolean isVolatileField(String key) {
        return "confirmation".equals(key)
                || "printAudit".equals(key)
                || "serviceFormStatus".equals(key)
                || "internalRemark".equals(key)
                || OrderAmountMaskingSupport.MASK_MARKER.equals(key)
                || "snapshotAt".equals(key);
    }

    private static JsonNode canonicalize(JsonNode node, ObjectMapper objectMapper) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            node.forEach(child -> arrayNode.add(canonicalize(child, objectMapper)));
            return arrayNode;
        }
        ObjectNode objectNode = objectMapper.createObjectNode();
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        Collections.sort(fieldNames);
        for (String fieldName : fieldNames) {
            objectNode.set(fieldName, canonicalize(node.get(fieldName), objectMapper));
        }
        return objectNode;
    }

    private static String sha256Hex(String payload) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte item : hash) {
            hex.append(String.format("%02x", item));
        }
        return hex.toString();
    }
}
