package com.backend.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent")
public class AgentEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String type;
    private String description;
    private Integer modelId;
    private String systemPrompt;
    private String provider;
    private Double temperature;
    private Integer maxTokens;
    private String mcpUrl;
    private String config;
    private Boolean enabled;
    private String status;
    private LocalDateTime lastActiveAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}