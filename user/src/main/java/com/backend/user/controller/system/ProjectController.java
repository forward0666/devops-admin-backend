package com.backend.user.controller.system;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.entity.system.ProjectEntity;
import com.backend.user.service.system.ProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/project")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @GetMapping
    public ApiResponseDto<List<ProjectEntity>> getAllProjects() {
        try {
            List<ProjectEntity> projects = projectService.getAllProjects();
            return ApiResponseDto.success("Projects retrieved successfully", projects);
        } catch (Exception e) {
            log.error("Failed to retrieve projects", e);
            return ApiResponseDto.error("Failed to retrieve projects");
        }
    }

    @GetMapping("/{id}")
    public ApiResponseDto<ProjectEntity> getProjectById(@PathVariable Long id) {
        try {
            ProjectEntity project = projectService.getProjectById(id);
            if (project != null) {
                return ApiResponseDto.success("Project retrieved successfully", project);
            } else {
                return ApiResponseDto.error("Project not found");
            }
        } catch (Exception e) {
            log.error("Failed to retrieve project: " + id, e);
            return ApiResponseDto.error("Failed to retrieve project");
        }
    }

    @PostMapping
    public ApiResponseDto<ProjectEntity> createProject(@RequestBody ProjectEntity project) {
        try {
            if (project.getName() == null || project.getName().isEmpty()) {
                return ApiResponseDto.error("Project name is required");
            }
            ProjectEntity created = projectService.createProject(project);
            return ApiResponseDto.success("Project created successfully", created);
        } catch (RuntimeException e) {
            log.warn("Failed to create project: " + e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create project", e);
            return ApiResponseDto.error("Failed to create project");
        }
    }

    @PutMapping("/{id}")
    public ApiResponseDto<ProjectEntity> updateProject(@PathVariable Long id, @RequestBody ProjectEntity project) {
        try {
            ProjectEntity updated = projectService.updateProject(id, project);
            if (updated != null) {
                return ApiResponseDto.success("Project updated successfully", updated);
            } else {
                return ApiResponseDto.error("Project not found");
            }
        } catch (RuntimeException e) {
            log.warn("Failed to update project: " + e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update project: " + id, e);
            return ApiResponseDto.error("Failed to update project");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponseDto<Void> deleteProject(@PathVariable Long id) {
        try {
            boolean deleted = projectService.deleteProject(id);
            if (deleted) {
                return ApiResponseDto.success("Project deleted successfully", null);
            } else {
                return ApiResponseDto.error("Project not found");
            }
        } catch (Exception e) {
            log.error("Failed to delete project: " + id, e);
            return ApiResponseDto.error("Failed to delete project");
        }
    }
}
