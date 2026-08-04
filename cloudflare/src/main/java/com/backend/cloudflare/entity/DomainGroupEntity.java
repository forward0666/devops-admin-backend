package com.backend.cloudflare.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("domain_groups")
public class DomainGroupEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Long accountId;

    @TableField("domains")
    private String domains;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}