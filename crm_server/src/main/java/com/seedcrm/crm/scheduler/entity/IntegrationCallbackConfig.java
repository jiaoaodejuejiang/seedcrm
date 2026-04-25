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
@TableName("integration_callback_config")
public class IntegrationCallbackConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("provider_code")
    private String providerCode;

    @TableField("callback_name")
    private String callbackName;

    @TableField("callback_url")
    private String callbackUrl;

    @TableField("signature_mode")
    private String signatureMode;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("token_value")
    private String tokenValue;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @TableField("aes_key")
    private String aesKey;

    @TableField("enabled")
    private Integer enabled;

    @TableField("remark")
    private String remark;

    @TableField("last_check_status")
    private String lastCheckStatus;

    @TableField("last_check_message")
    private String lastCheckMessage;

    @TableField("last_callback_status")
    private String lastCallbackStatus;

    @TableField("last_callback_message")
    private String lastCallbackMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("last_callback_at")
    private LocalDateTime lastCallbackAt;

    @TableField("last_trace_id")
    private String lastTraceId;

    @TableField("last_auth_code")
    private String lastAuthCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String tokenMasked;

    @TableField(exist = false)
    private String aesKeyMasked;

    @TableField(exist = false)
    private String lastAuthCodeMasked;
}
