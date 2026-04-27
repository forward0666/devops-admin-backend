package com.backend.user.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "project_domains")
public class DomainEntity {

    @Id
    private String id;

    private Long projectId;
    private String domain;
    private String env;
    private String type;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
