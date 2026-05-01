package com.seedcrm.crm.scheduler.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seedcrm.crm.common.exception.BusinessException;
import com.seedcrm.crm.order.enums.OrderType;
import com.seedcrm.crm.scheduler.dto.DistributionEventDtos.DistributionOrderPayload;
import com.seedcrm.crm.systemconfig.service.SystemConfigService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DistributionOrderTypeMappingResolver {

    public static final String CONFIG_KEY = "distribution.order.type.mapping";
    public static final String DEFAULT_MAPPING_JSON = """
            {
              "default": "coupon",
              "strictProductMapping": false,
              "aliases": {
                "coupon": "coupon",
                "groupbuy": "coupon",
                "voucher": "coupon",
                "团购": "coupon",
                "团购券": "coupon",
                "distribution_product": "distribution_product",
                "distribution": "distribution_product",
                "product": "distribution_product",
                "分销商品": "distribution_product",
                "deposit": "deposit",
                "prepay": "deposit",
                "prepaid": "deposit",
                "定金": "deposit",
                "预付定金": "deposit"
              },
              "rules": []
            }
            """;

    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;

    public DistributionOrderTypeMappingResolver(ObjectMapper objectMapper,
                                                SystemConfigService systemConfigService) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.systemConfigService = systemConfigService;
    }

    public ResolvedOrderType resolve(String providerCode,
                                     String sourceChannel,
                                     DistributionOrderPayload orderPayload) {
        MatchContext context = MatchContext.from(providerCode, sourceChannel, orderPayload);
        return resolve(context);
    }

    public ResolvedOrderType resolve(String providerCode,
                                     String sourceChannel,
                                     JsonNode orderNode) {
        MatchContext context = MatchContext.from(providerCode, sourceChannel, orderNode);
        return resolve(context);
    }

    private ResolvedOrderType resolve(MatchContext context) {
        MappingConfig config = loadConfig();
        RuleMatch match = config.rules().stream()
                .filter(rule -> rule.matches(context))
                .max(Comparator.comparingInt(RuleMatch::priority)
                        .thenComparingInt(RuleMatch::specificity))
                .orElse(null);
        if (match != null) {
            String apiValue = normalizeOrderType(firstNonBlank(match.internalOrderType(), config.defaultType(), "coupon"));
            return new ResolvedOrderType(
                    orderTypeCode(apiValue),
                    apiValue,
                    "RULE",
                    firstNonBlank(match.ruleId(), match.name()),
                    config.strictProductMapping(),
                    context.hasProductIdentity(),
                    false,
                    trimToNull(match.verificationPolicy()),
                    trimToNull(match.serviceCategory()),
                    match.snapshot());
        }

        if (config.strictProductMapping() && context.hasProductIdentity()) {
            return new ResolvedOrderType(
                    orderTypeCode(config.defaultType()),
                    config.defaultType(),
                    "MISSING_PRODUCT_RULE",
                    null,
                    true,
                    true,
                    true,
                    null,
                    null,
                    Map.of());
        }

        String incomingType = normalizeKey(context.externalType());
        String mappedType = StringUtils.hasText(incomingType) ? config.aliases().get(incomingType) : null;
        String apiValue = normalizeOrderType(firstNonBlank(mappedType, config.defaultType(), "coupon"));
        return new ResolvedOrderType(
                orderTypeCode(apiValue),
                apiValue,
                StringUtils.hasText(mappedType) ? "ALIAS" : "DEFAULT",
                null,
                config.strictProductMapping(),
                context.hasProductIdentity(),
                false,
                null,
                null,
                Map.of());
    }

    private MappingConfig loadConfig() {
        String configured = systemConfigService == null
                ? DEFAULT_MAPPING_JSON
                : systemConfigService.getString(CONFIG_KEY, DEFAULT_MAPPING_JSON);
        try {
            return parseConfig(configured);
        } catch (RuntimeException exception) {
            if (systemConfigService == null) {
                return parseConfig(DEFAULT_MAPPING_JSON);
            }
            throw new BusinessException("distribution order type mapping config invalid");
        }
    }

    private MappingConfig parseConfig(String value) {
        try {
            JsonNode root = objectMapper.readTree(StringUtils.hasText(value) ? value : DEFAULT_MAPPING_JSON);
            String defaultType = configuredOrderType(firstNonBlank(text(root, "default"), "coupon"), "default");
            boolean strictProductMapping = root.path("strictProductMapping").asBoolean(false)
                    || root.path("requireProductMapping").asBoolean(false);
            Map<String, String> aliases = parseAliases(root.path("aliases"));
            List<RuleMatch> rules = parseRules(root.path("rules"));
            return new MappingConfig(defaultType, strictProductMapping, aliases, rules);
        } catch (Exception exception) {
            throw new IllegalArgumentException("distribution order type mapping config invalid", exception);
        }
    }

    private Map<String, String> parseAliases(JsonNode aliasesNode) {
        Map<String, String> aliases = new LinkedHashMap<>();
        if (aliasesNode != null && aliasesNode.isObject()) {
            aliasesNode.fields().forEachRemaining(entry -> {
                String key = normalizeKey(entry.getKey());
                String value = configuredOrderType(entry.getValue().asText(null), "aliases." + entry.getKey());
                if (StringUtils.hasText(key)) {
                    aliases.put(key, value);
                }
            });
        }
        if (aliases.isEmpty()) {
            aliases.put("deposit", "deposit");
            aliases.put("prepay", "deposit");
            aliases.put("coupon", "coupon");
            aliases.put("groupbuy", "coupon");
            aliases.put("voucher", "coupon");
            aliases.put("distribution_product", "distribution_product");
            aliases.put("distribution", "distribution_product");
            aliases.put("product", "distribution_product");
        }
        return aliases;
    }

    private List<RuleMatch> parseRules(JsonNode rulesNode) {
        List<RuleMatch> rules = new ArrayList<>();
        if (rulesNode == null || !rulesNode.isArray()) {
            return rules;
        }
        for (JsonNode node : rulesNode) {
            if (node == null || !node.isObject() || !node.path("enabled").asBoolean(true)) {
                continue;
            }
            String internalOrderType = firstNonBlank(
                    text(node, "internalOrderType"),
                    text(node, "orderType"),
                    text(node, "targetOrderType"));
            if (!StringUtils.hasText(internalOrderType)) {
                continue;
            }
            rules.add(new RuleMatch(
                    firstNonBlank(text(node, "ruleId"), text(node, "id")),
                    text(node, "name"),
                    text(node, "providerCode"),
                    firstNonBlank(text(node, "sourceChannel"), text(node, "channelCode")),
                    firstNonBlank(text(node, "externalProductId"), text(node, "productId")),
                    firstNonBlank(text(node, "externalSkuId"), text(node, "skuId")),
                    firstNonBlank(text(node, "externalStoreCode"), text(node, "storeCode")),
                    firstNonBlank(text(node, "externalOrderType"), text(node, "externalType")),
                    configuredOrderType(internalOrderType, "rules.internalOrderType"),
                    text(node, "verificationPolicy"),
                    text(node, "serviceCategory"),
                    node.path("priority").asInt(0),
                    snapshot(node)));
        }
        return rules;
    }

    private Map<String, Object> snapshot(JsonNode node) {
        try {
            return objectMapper.convertValue(node, Map.class);
        } catch (IllegalArgumentException ignored) {
            return Map.of();
        }
    }

    private int orderTypeCode(String apiValue) {
        return switch (normalizeOrderType(apiValue)) {
            case "deposit" -> OrderType.DEPOSIT.getCode();
            case "distribution_product" -> OrderType.DISTRIBUTION_PRODUCT.getCode();
            default -> OrderType.COUPON.getCode();
        };
    }

    private String normalizeOrderType(String value) {
        String normalized = normalizeKey(value);
        if (!StringUtils.hasText(normalized)) {
            return "coupon";
        }
        return switch (normalized) {
            case "deposit", "prepay", "prepaid", "定金", "预付定金" -> "deposit";
            case "distribution_product", "distribution", "product", "分销商品" -> "distribution_product";
            case "coupon", "groupbuy", "voucher", "团购", "团购券" -> "coupon";
            default -> "coupon";
        };
    }

    private String configuredOrderType(String value, String fieldName) {
        String normalized = normalizeKey(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return switch (normalized) {
            case "deposit", "prepay", "prepaid", "定金", "预付定金" -> "deposit";
            case "distribution_product", "distribution", "product", "分销商品" -> "distribution_product";
            case "coupon", "groupbuy", "voucher", "团购", "团购券" -> "coupon";
            default -> throw new IllegalArgumentException(fieldName + " only supports coupon, deposit or distribution_product");
        };
    }

    private static String text(JsonNode node, String fieldName) {
        if (node == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.isTextual() ? value.asText() : value.toString();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String normalizeKey(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    private record MappingConfig(String defaultType,
                                 boolean strictProductMapping,
                                 Map<String, String> aliases,
                                 List<RuleMatch> rules) {
    }

    private record MatchContext(String providerCode,
                                String sourceChannel,
                                String externalProductId,
                                String externalSkuId,
                                String externalStoreCode,
                                String externalType) {

        static MatchContext from(String providerCode, String sourceChannel, DistributionOrderPayload payload) {
            return new MatchContext(
                    providerCode,
                    sourceChannel,
                    payload == null ? null : payload.getExternalProductId(),
                    payload == null ? null : payload.getExternalSkuId(),
                    payload == null ? null : payload.getStoreCode(),
                    payload == null ? null : payload.getType());
        }

        static MatchContext from(String providerCode, String sourceChannel, JsonNode orderNode) {
            return new MatchContext(
                    providerCode,
                    sourceChannel,
                    text(orderNode, "externalProductId"),
                    text(orderNode, "externalSkuId"),
                    text(orderNode, "storeCode"),
                    text(orderNode, "type"));
        }

        boolean hasProductIdentity() {
            return StringUtils.hasText(externalProductId) || StringUtils.hasText(externalSkuId);
        }
    }

    private record RuleMatch(String ruleId,
                             String name,
                             String providerCode,
                             String sourceChannel,
                             String externalProductId,
                             String externalSkuId,
                             String externalStoreCode,
                             String externalType,
                             String internalOrderType,
                             String verificationPolicy,
                             String serviceCategory,
                             int priority,
                             Map<String, Object> snapshot) {

        boolean matches(MatchContext context) {
            return matchesField(providerCode, context.providerCode())
                    && matchesField(sourceChannel, context.sourceChannel())
                    && matchesField(externalProductId, context.externalProductId())
                    && matchesField(externalSkuId, context.externalSkuId())
                    && matchesField(externalStoreCode, context.externalStoreCode())
                    && matchesField(externalType, context.externalType());
        }

        int specificity() {
            int score = 0;
            score += StringUtils.hasText(providerCode) ? 1 : 0;
            score += StringUtils.hasText(sourceChannel) ? 1 : 0;
            score += StringUtils.hasText(externalProductId) ? 1 : 0;
            score += StringUtils.hasText(externalSkuId) ? 1 : 0;
            score += StringUtils.hasText(externalStoreCode) ? 1 : 0;
            score += StringUtils.hasText(externalType) ? 1 : 0;
            return score;
        }

        private static boolean matchesField(String expected, String actual) {
            if (!StringUtils.hasText(expected)) {
                return true;
            }
            return normalizeKey(expected).equals(normalizeKey(actual));
        }
    }

    public record ResolvedOrderType(int code,
                                    String apiValue,
                                    String resolution,
                                    String ruleId,
                                    boolean strictProductMapping,
                                    boolean productIdentityPresent,
                                    boolean missingProductRule,
                                    String verificationPolicy,
                                    String serviceCategory,
                                    Map<String, Object> ruleSnapshot) {

        public Map<String, Object> toPreviewMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("internalOrderType", apiValue);
            result.put("internalOrderTypeCode", code);
            result.put("resolution", resolution);
            result.put("ruleId", ruleId);
            result.put("strictProductMapping", strictProductMapping);
            result.put("productIdentityPresent", productIdentityPresent);
            result.put("missingProductRule", missingProductRule);
            result.put("verificationPolicy", verificationPolicy);
            result.put("serviceCategory", serviceCategory);
            result.put("ruleSnapshot", ruleSnapshot == null ? Map.of() : ruleSnapshot);
            return result;
        }
    }
}
