package com.backend.manage.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleRequestDto {
    @NotBlank(message = "Role name is required")
    @Size(max = 100, message = "Role name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Role code is required")
    @Size(max = 50, message = "Role code cannot exceed 50 characters")
    private String code;

    @Size(max = 500, message = "Role description cannot exceed 500 characters")
    private String description;

    private String status;
}
