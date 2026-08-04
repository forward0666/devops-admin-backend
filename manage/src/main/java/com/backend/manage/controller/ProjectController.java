package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.manage.entity.ProjectEntity;
import com.backend.manage.service.ProjectService;
import com.backend.manage.vo.ProjectVo;
import com.backend.utils.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/project")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ProjectVo>>> getAllProjects() {
        List<ProjectEntity> projects = projectService.getAllProjects();
        List<ProjectVo> result = projects.stream().map(ProjectVo::fromEntity).toList();
        return ResponseEntity.ok(ApiResponseDto.success("Projects retrieved successfully", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<ProjectVo>> getProjectById(@PathVariable Long id) {
        ProjectEntity project = projectService.getProjectById(id);
        if (project == null) throw new BizException(404, "Project not found");
        return ResponseEntity.ok(ApiResponseDto.success("Project retrieved successfully", ProjectVo.fromEntity(project)));
    }

    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "创建项目",
        resourceType = "PROJECT",
        description = "创建新项目"
    )
    public ResponseEntity<ApiResponseDto<ProjectVo>> createProject(@RequestBody ProjectEntity project) {
        if (project.getName() == null || project.getName().isEmpty()) {
            throw new BizException(400, "Project name is required");
        }
        ProjectEntity created = projectService.createProject(project);
        return ResponseEntity.ok(ApiResponseDto.success("Project created successfully", ProjectVo.fromEntity(created)));
    }

    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新项目",
        resourceType = "PROJECT",
        resourceIdIndex = 0,
        description = "更新项目信息"
    )
    public ResponseEntity<ApiResponseDto<ProjectVo>> updateProject(@PathVariable Long id, @RequestBody ProjectEntity project) {
        ProjectEntity updated = projectService.updateProject(id, project);
        if (updated == null) throw new BizException(404, "Project not found");
        return ResponseEntity.ok(ApiResponseDto.success("Project updated successfully", ProjectVo.fromEntity(updated)));
    }

    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "删除项目",
        resourceType = "PROJECT",
        resourceIdIndex = 0,
        description = "删除项目"
    )
    public ResponseEntity<ApiResponseDto<Void>> deleteProject(@PathVariable Long id) {
        boolean deleted = projectService.deleteProject(id);
        if (!deleted) throw new BizException(404, "Project not found");
        return ResponseEntity.ok(ApiResponseDto.success("Project deleted successfully", null));
    }
}