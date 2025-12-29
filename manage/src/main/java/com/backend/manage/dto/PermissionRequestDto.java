package com.backend.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 权限映射请求数据传输对象
 * 用于更新角色的菜单权限
 *
 * 设计特点：
 * 1. 使用 Lombok @Data 注解自动生成 getter/setter
 * 2. 使用验证注解确保输入数据有效性
 * 3. 支持批量更新角色的菜单权限
 *
 * @author Backend Team
 * @version 2.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDto {

    /**
     * 角色 ID
     *
     * 验证规则：不能为空
     */
    @NotNull(message = "Role ID cannot be null")
    private Long roleId;

    /**
     * 菜单 ID 列表
     *
     * 验证规则：不能为空
     * 该角色拥有的所有菜单权限
     */
    @NotNull(message = "Menu IDs cannot be null")
    private List<Long> menuIds;
}
