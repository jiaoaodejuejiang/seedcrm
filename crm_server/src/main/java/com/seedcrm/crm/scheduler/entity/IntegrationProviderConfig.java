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

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("access_token")
    private String accessToken;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("refresh_token")
    private String refreshToken;

    @TableField("account_id")
    private String accountId;

    @TableField("life_account_ids")
    private String lifeAccountIds;

    @TableField("open_id")
    private String openId;

    @TableField("page_size")
    private Integer pageSize;

    @TableField("request_timeout_ms")
    private Integer requestTimeoutMs;

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
