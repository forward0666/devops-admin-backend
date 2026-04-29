package com.backend.user.vo;

import com.backend.user.entity.ProjectEntity;
import java.time.LocalDateTime;

public record ProjectVo(
    Long id,
    String name,
    String type,
    String status,
    Integer progress,
    Long departmentId,
    String description,
    String techStack,
    String objectives,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static ProjectVo fromEntity(ProjectEntity project) {
        return new ProjectVo(
            project.getId(), project.getName(), project.getType(), project.getStatus(),
            project.getProgress(), project.getDepartmentId(), project.getDescription(),
            project.getTechStack(), project.getObjectives(), project.isActive(),
            project.getCreatedAt(), project.getUpdatedAt()
        );
    }
}
