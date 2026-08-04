package com.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("domain")
public class DomainEntity {
    @TableId
    private Long id;
    private Long projectId;
    private String domain;
    private String env;
    private String type;
    private String remark;
    private String cdn;
}

// Statistic entity for MongoDB, not MySQL
class StatisticMongoEntity {
    private String domain;
    private String zoneId;
    private long total;
    private long cached;
    private long uncached;
    private long bandwidth;
    private long threats;
    private long pageViews;
    private long uniqueVisitor;
}