package com.backend.manage.vo;

import com.backend.manage.entity.ProjectEntity;
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
    public static ProjectVo fromEntity(ProjectEntity entity) {
        return new ProjectVo(
                entity.getId(),
                entity.getName(),
                entity.getType(),
                entity.getStatus(),
                entity.getProgress(),
                entity.getDepartmentId(),
                entity.getDescription(),
                entity.getTechStack(),
                entity.getObjectives(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
