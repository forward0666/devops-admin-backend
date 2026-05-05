package com.backend.user.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "middleware")
public class MiddlewareEntity {

    @Id
    private String id;

    private Long projectId;
    private String name;
    private String env;
    private String protocol;
    private String externalAddr;
    private String internalAddr;
    private String svcAddr;
    private String remark;
    private String type;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
