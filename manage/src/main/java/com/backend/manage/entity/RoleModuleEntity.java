package com.backend.manage.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色-模块关联实体
 * 角色拥有的模块访问权限
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleModuleEntity {
    private Long id;
    private Long roleId;
    private Long moduleId;
    private Boolean canRead;
    private Boolean canWrite;
    private LocalDateTime createdAt;
}