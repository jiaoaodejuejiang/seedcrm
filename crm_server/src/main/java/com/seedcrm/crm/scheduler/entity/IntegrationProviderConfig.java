package com.seedcrm.crm.scheduler.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("integration_provider_config")
public class IntegrationProviderConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("provider_code")
    private String providerCode;

    @TableField("provider_name")
    private String providerName;

    @TableField("module_code")
    private String moduleCode;

    @TableField("execution_mode")
    private String executionMode;

    @TableField("auth_type")
    private String authType;

    @TableField("app_id")
    private String appId;

    @TableField("base_url")
    private String baseUrl;

    @TableField("token_url")
    private String tokenUrl;

    @TableField("endpoint_path")
    private String endpointPath;

    @TableField("status_query_path")
    private String statusQueryPath;

    @TableField("reconciliation_pull_path")
    private String reconciliationPullPath;

    @TableField("voucher_prepare_path")
    private String voucherPreparePath;

    @TableField("voucher_verify_path")
    private String voucherVerifyPath;

    @TableField("voucher_cancel_path")
    private String voucherCancelPath;

    @TableField("refund_apply_path")
    private String refundApplyPath;

    @TableField("refund_query_path")
    private String refundQueryPath;

    @TableField("refund_list_path")
    private String refundListPath;

    @TableField("refund_notify_path")
    private String refundNotifyPath;

    @TableField("refund_audit_callback_path")
    private String refundAuditCallbackPath;

    @TableField("refund_order_id_field")
    private String refundOrderIdField;

    @TableField("refund_amount_field")
    private String refundAmountField;

    @TableField("refund_reason_field")
    private String refundReasonField;

    @TableField("refund_out_order_no_field")
    private String refundOutOrderNoField;

    @TableField("refund_out_refund_no_field")
    private String refundOutRefundNoField;

    @TableField("refund_external_refund_id_field")
    private String refundExternalRefundIdField;

    @TableField("refund_item_order_id_field")
    private String refundItemOrderIdField;

    @TableField("refund_notify_url_field")
    private String refundNotifyUrlField;

    @TableField("refund_amount_unit")
    private String refundAmountUnit;

    @TableField("refund_status_mapping")
    private String refundStatusMapping;

    @TableField("status_mapping")
    private String statusMapping;

    @TableField("client_key")
    private String clientKey;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("client_secret")
    private String clientSecret;

    @TableField("redirect_uri")
    private String redirectUri;

    @TableField("scope")
    private String scope;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("auth_code")
    private String authCode;

    @TableField("auth_code_status")
    private String authCodeStatus;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("access_token")
    private String accessToken;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("refresh_token")
    private String refreshToken;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_refresh_at")
    private LocalDateTime lastRefreshAt;

    @TableField("account_id")
    private String accountId;

    @TableField("life_account_ids")
    private String lifeAccountIds;

    @TableField("local_account_ids")
    private String localAccountIds;

    @TableField("open_id")
    private String openId;

    @TableField("poi_id")
    private String poiId;

    @TableField("verify_code_field")
    private String verifyCodeField;

    @TableField("page_size")
    private Integer pageSize;

    @TableField("pull_window_minutes")
    private Integer pullWindowMinutes;

    @TableField("overlap_minutes")
    private Integer overlapMinutes;

    @TableField("request_timeout_ms")
    private Integer requestTimeoutMs;

    @TableField("rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    @TableField("cache_ttl_seconds")
    private Integer cacheTtlSeconds;

    @TableField("callback_url")
    private String callbackUrl;

    @TableField("enabled")
    private Integer enabled;

    @TableField("remark")
    private String remark;

    @TableField("last_test_status")
    private String lastTestStatus;

    @TableField("last_test_message")
    private String lastTestMessage;

    @TableField("auth_status")
    private String authStatus;

    @TableField("last_callback_status")
    private String lastCallbackStatus;

    @TableField("last_callback_message")
    private String lastCallbackMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_test_at")
    private LocalDateTime lastTestAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_auth_code_at")
    private LocalDateTime lastAuthCodeAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_callback_at")
    private LocalDateTime lastCallbackAt;

    @TableField("last_callback_payload")
    private String lastCallbackPayload;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_sync_time")
    private LocalDateTime lastSyncTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String clientSecretMasked;

    @TableField(exist = false)
    private Boolean clientSecretConfigured;

    @TableField(exist = false)
    private String authCodeMasked;

    @TableField(exist = false)
    private String accessTokenMasked;

    @TableField(exist = false)
    private String refreshTokenMasked;
}
