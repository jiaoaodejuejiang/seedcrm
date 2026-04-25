package com.seedcrm.crm.wecom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("wecom_app_config")
public class WecomAppConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("app_code")
    private String appCode;

    @TableField("app_id")
    private String appId;

    @TableField("suite_id")
    private String suiteId;

    @TableField("auth_mode")
    private String authMode;

    @TableField("corp_id")
    private String corpId;

    @TableField("auth_corp_id")
    private String authCorpId;

    @TableField("agent_id")
    private String agentId;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("app_secret")
    private String appSecret;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("suite_secret")
    private String suiteSecret;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("auth_code")
    private String authCode;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("suite_ticket")
    private String suiteTicket;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("permanent_code")
    private String permanentCode;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("access_token")
    private String accessToken;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("refresh_token")
    private String refreshToken;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("suite_access_token")
    private String suiteAccessToken;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("corp_access_token")
    private String corpAccessToken;

    @TableField("execution_mode")
    private String executionMode;

    @TableField("callback_url")
    private String callbackUrl;

    @TableField("redirect_uri")
    private String redirectUri;

    @TableField("trusted_domain")
    private String trustedDomain;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("callback_token")
    private String callbackToken;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("encoding_aes_key")
    private String encodingAesKey;

    @TableField("live_code_type")
    private Integer liveCodeType;

    @TableField("live_code_scene")
    private Integer liveCodeScene;

    @TableField("live_code_style")
    private Integer liveCodeStyle;

    @TableField("skip_verify")
    private Integer skipVerify;

    @TableField("state_template")
    private String stateTemplate;

    @TableField("mark_source")
    private String markSource;

    @TableField("enabled")
    private Integer enabled;

    @TableField("last_token_status")
    private String lastTokenStatus;

    @TableField("last_token_message")
    private String lastTokenMessage;

    @TableField("auth_status")
    private String authStatus;

    @TableField("last_callback_status")
    private String lastCallbackStatus;

    @TableField("last_callback_message")
    private String lastCallbackMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_token_checked_at")
    private LocalDateTime lastTokenCheckedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("suite_ticket_at")
    private LocalDateTime suiteTicketAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_auth_code_at")
    private LocalDateTime lastAuthCodeAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("suite_access_token_expires_at")
    private LocalDateTime suiteAccessTokenExpiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("corp_access_token_expires_at")
    private LocalDateTime corpAccessTokenExpiresAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_callback_at")
    private LocalDateTime lastCallbackAt;

    @TableField("last_callback_payload")
    private String lastCallbackPayload;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String appSecretMasked;

    @TableField(exist = false)
    private String suiteSecretMasked;

    @TableField(exist = false)
    private String authCodeMasked;

    @TableField(exist = false)
    private String suiteTicketMasked;

    @TableField(exist = false)
    private String permanentCodeMasked;

    @TableField(exist = false)
    private String accessTokenMasked;

    @TableField(exist = false)
    private String refreshTokenMasked;

    @TableField(exist = false)
    private String suiteAccessTokenMasked;

    @TableField(exist = false)
    private String corpAccessTokenMasked;

    @TableField(exist = false)
    private String callbackTokenMasked;

    @TableField(exist = false)
    private String encodingAesKeyMasked;
}
