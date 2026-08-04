package com.backend.cloudflare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cf_sync_rule")
public class CfSyncRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;

    private String name;

    private String description;

    private String sourceZoneId;

    @TableField("target_zone_ids")
    private String targetZoneIds;

    @TableField("rule_types")
    private String ruleTypes;

    private LocalDateTime lastSyncedAt;

    private Boolean enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}