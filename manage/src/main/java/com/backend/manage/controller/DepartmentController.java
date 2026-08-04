package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.manage.entity.DepartmentEntity;
import com.backend.manage.service.DepartmentService;
import com.backend.manage.vo.DepartmentVo;
import com.backend.utils.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/department")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<DepartmentVo>>> getAllDepartments() {
        List<DepartmentEntity> departments = departmentService.getAllDepartments();
        List<DepartmentVo> result = departments.stream().map(DepartmentVo::fromEntity).toList();
        return ResponseEntity.ok(ApiResponseDto.success("Departments retrieved successfully", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<DepartmentVo>> getDepartmentById(@PathVariable Long id) {
        DepartmentEntity department = departmentService.getDepartmentById(id);
        if (department == null) throw new BizException(404, "Department not found");
        return ResponseEntity.ok(ApiResponseDto.success("Department retrieved successfully", DepartmentVo.fromEntity(department)));
    }

    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建部门",
        resourceType = "DEPARTMENT",
        description = "创建新部门"
    )
    public ResponseEntity<ApiResponseDto<DepartmentVo>> createDepartment(@Valid @RequestBody DepartmentEntity department) {
        DepartmentEntity created = departmentService.createDepartment(department);
        return ResponseEntity.ok(ApiResponseDto.success("Department created successfully", DepartmentVo.fromEntity(created)));
    }

    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新部门",
        resourceType = "DEPARTMENT",
        resourceIdIndex = 0,
        description = "更新部门信息"
    )
    public ResponseEntity<ApiResponseDto<DepartmentVo>> updateDepartment(@PathVariable Long id, @Valid @RequestBody DepartmentEntity department) {
        department.setId(id);
        DepartmentEntity updated = departmentService.updateDepartment(department);
        if (updated == null) throw new BizException(404, "Department not found");
        return ResponseEntity.ok(ApiResponseDto.success("Department updated successfully", DepartmentVo.fromEntity(updated)));
    }

    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除部门",
        resourceType = "DEPARTMENT",
        resourceIdIndex = 0,
        description = "删除部门"
    )
    public ResponseEntity<ApiResponseDto<Void>> deleteDepartment(@PathVariable Long id) {
        boolean deleted = departmentService.deleteDepartment(id);
        if (!deleted) throw new BizException(404, "Department not found");
        return ResponseEntity.ok(ApiResponseDto.success("Department deleted successfully", null));
    }

    @GetMapping("/{id}/users")
    public ResponseEntity<ApiResponseDto<List<Object>>> getDepartmentUsers(@PathVariable Long id) {
        List<Object> users = departmentService.getDepartmentUsers(id);
        return ResponseEntity.ok(ApiResponseDto.success("Department users retrieved successfully", users));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponseDto<List<DepartmentVo>>> searchDepartments(@RequestParam String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BizException(400, "Search name cannot be empty");
        }
        List<DepartmentEntity> allDepartments = departmentService.getAllDepartments();
        List<DepartmentVo> filtered = allDepartments.stream()
                .filter(dept -> dept.getName().toLowerCase().contains(name.toLowerCase()))
                .map(DepartmentVo::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponseDto.success("Departments found", filtered));
    }
}