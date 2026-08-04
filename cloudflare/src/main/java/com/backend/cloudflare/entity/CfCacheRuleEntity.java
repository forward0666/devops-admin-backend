package com.backend.cloudflare.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CfCacheRuleEntity {
    private Long id;
    private Long projectId;
    private String env;
    private String name;
    private String url;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}