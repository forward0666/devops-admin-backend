package com.backend.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model")
public class ModelEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String provider;
    private String model;
    private String baseUrl;
    private String apiKey;
    private String description;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}