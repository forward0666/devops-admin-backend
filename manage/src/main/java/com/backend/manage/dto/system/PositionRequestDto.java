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
public class PositionRequestDto {
    @NotBlank(message = "Position name is required")
    @Size(max = 100, message = "Position name cannot exceed 100 characters")
    private String name;

    @NotBlank(message = "Position code is required")
    @Size(max = 50, message = "Position code cannot exceed 50 characters")
    private String code;

    private Long departmentId;

    @NotNull(message = "Position level is required")
    private Integer level;

    @Size(max = 500, message = "Position description cannot exceed 500 characters")
    private String description;

    private String status;
}
