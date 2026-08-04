package com.backend.task.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Task entity mapped to MySQL `task` table.
 */
@Data
@TableName("task")
public class TaskEntity {

    @TableId
    private Long id;
    private String name;
    private String type;
    private String cron;
    private Boolean enabled;
    private String description;
    private String config;         // JSON string stored in MySQL
    private String lastStatus;
    private Date lastRunAt;
    private Date createdAt;
    private Date updatedAt;
}