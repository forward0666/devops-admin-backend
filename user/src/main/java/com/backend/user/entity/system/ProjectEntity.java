package com.backend.user.entity.system;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目实体类
 * 表示系统中的项目信息，对应数据库中的 projects 表
 *
 * @author Backend Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEntity {
    private Long id;
    private String name;
    private String type;
    private String status;
    private Integer progress;
    private Long departmentId;
    private String description;
    private String techStack;
    private String objectives;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdBy;
    private Long updatedBy;
    private boolean active;
}
