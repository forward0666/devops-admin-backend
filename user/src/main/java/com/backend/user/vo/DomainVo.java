package com.backend.user.vo;

import com.backend.user.entity.DomainEntity;
import java.time.LocalDateTime;

public record DomainVo(
    String id,
    Long projectId,
    String domain,
    String env,
    String type,
    String remark,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static DomainVo fromEntity(DomainEntity entity) {
        return new DomainVo(
            entity.getId(), entity.getProjectId(), entity.getDomain(),
            entity.getEnv(), entity.getType(), entity.getRemark(),
            entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
