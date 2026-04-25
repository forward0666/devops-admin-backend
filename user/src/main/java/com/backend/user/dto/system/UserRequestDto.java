package com.backend.user.dto.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRequestDto {
    @NotBlank(message = "Username is required")
    @Size(max = 50, message = "Username cannot exceed 50 characters")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    private String password;

    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Size(max = 20, message = "Phone cannot exceed 20 characters")
    private String phone;

    @Size(max = 50, message = "Telegram username cannot exceed 50 characters")
    private String tgUsername;

    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    @Size(max = 255, message = "Avatar URL cannot exceed 255 characters")
    private String avatarUrl;

    @Size(max = 100, message = "Position cannot exceed 100 characters")
    private String position;

    @Size(max = 50, message = "Employee ID cannot exceed 50 characters")
    private String employeeId;

    @NotBlank(message = "Role is required")
    private String role;

    private Long departmentId;

    private Boolean active;
    private Long createdBy;
    private Long updatedBy;
}
