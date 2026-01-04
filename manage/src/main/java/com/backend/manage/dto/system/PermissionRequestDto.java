package com.backend.manage.dto.system;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDto {
    @NotNull(message = "Role ID is required")
    private Long roleId;

    private List<Long> menuIds;

    private List<MenuPermissionItem> permissions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuPermissionItem {
        private Long menuId;
        private String permissionType;
    }
}
