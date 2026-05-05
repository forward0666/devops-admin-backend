package com.backend.user.vo;

import com.backend.user.entity.MiddlewareEntity;
import java.time.LocalDateTime;

public record MiddlewareVo(
    String id,
    Long projectId,
    String name,
    String env,
    String protocol,
    String externalAddr,
    String internalAddr,
    String svcAddr,
    String remark,
    String type,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static MiddlewareVo fromEntity(MiddlewareEntity entity) {
        return new MiddlewareVo(
            entity.getId(), entity.getProjectId(), entity.getName(),
            entity.getEnv(), entity.getProtocol(), entity.getExternalAddr(),
            entity.getInternalAddr(), entity.getSvcAddr(), entity.getRemark(), entity.getType(),
            entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
