package com.backend.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Statistical data entity - stored in MongoDB statistic_* or statistic_chart_* collections.
 */
@Data
@Document
public class StatisticEntity {

    @Id
    private String id;
    private String domain;
    private String zoneId;
    private Long total;
    private Long cached;
    private Long uncached;
    private Long bandwidth;
    private Long threats;
    private Long pageViews;
    private Long uniqueVisitor;
}