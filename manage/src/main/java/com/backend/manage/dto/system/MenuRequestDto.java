package com.backend.manage.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuRequestDto {
    @NotNull(message = "Menu ID is required")
    private Long menuId;

    @NotBlank(message = "Menu name is required")
    @Size(max = 100, message = "Menu name cannot exceed 100 characters")
    private String name;

    private Long parentId;

    @Size(max = 200, message = "Menu path cannot exceed 200 characters")
    private String path;

    @Size(max = 50, message = "Menu icon cannot exceed 50 characters")
    private String icon;

    @NotBlank(message = "Menu type is required")
    private String type;

    @NotNull(message = "Menu sort order is required")
    private Integer sort;

    private String status;
}
