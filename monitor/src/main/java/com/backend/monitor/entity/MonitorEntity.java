package com.backend.monitor.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("monitor_rule")
public class MonitorEntity {
    @TableId
    private Long id;
    private String name;
    private String type;
    private String target;
    private Long interval;
    private Integer timeout;
    private Boolean enabled;
    private String status;
    private String lastCheck;
    private String description;
    private Date createdAt;
    private Date updatedAt;
}