package com.seedcrm.crm.wecom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
@TableName("wecom_live_code_config")
public class WecomLiveCodeConfig {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("code_name")
    private String codeName;

    @TableField("scene")
    private String scene;

    @TableField("strategy")
    private String strategy;

    @JsonIgnore
    @TableField("employee_names_json")
    private String employeeNamesJson;

    @JsonIgnore
    @TableField("employee_accounts_json")
    private String employeeAccountsJson;

    @JsonIgnore
    @TableField("store_names_json")
    private String storeNamesJson;

    @TableField("remark")
    private String remark;

    @TableField("is_enabled")
    private Integer isEnabled;

    @TableField("contact_way_id")
    private String contactWayId;

    @TableField("qr_code_url")
    private String qrCodeUrl;

    @TableField("short_link")
    private String shortLink;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("published_at")
    private LocalDateTime publishedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("generated_at")
    private LocalDateTime generatedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private List<String> employeeNames = new ArrayList<>();

    @TableField(exist = false)
    private List<String> employeeAccounts = new ArrayList<>();

    @TableField(exist = false)
    private List<String> storeNames = new ArrayList<>();
}
