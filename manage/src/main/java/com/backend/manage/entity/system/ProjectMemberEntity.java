package com.backend.manage.entity.system;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目成员实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberEntity {
    private Long id;
    private Long projectId;
    private Long userId;
    private String username;
    private String fullName;
    private String projectRole;
    private String position;
    private String status;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private boolean active;
}
