package com.backend.manage.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;

/**
 * 项目成员实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectMemberEntity {
    private Long id;
    private Long projectId;
    private Long userId;
    private String projectRole;
    private String position;
    private String status;
    @org.springframework.data.annotation.Transient
    private String username;
    @org.springframework.data.annotation.Transient
    private String fullName;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private boolean active;

    // System info from users table (not persisted)
    private String systemRole;
    private String email;
    private String phone;
    private String tgUsername;
    private String departmentName;
    private String userPosition;
}
