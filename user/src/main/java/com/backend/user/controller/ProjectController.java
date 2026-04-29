package com.backend.user.controller;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.entity.ProjectEntity;
import com.backend.user.service.ProjectService;
import com.backend.user.service.ProjectMemberService;
import com.backend.user.util.JwtUtil;
import com.backend.user.vo.ProjectVo;
import jakarta.servlet.http.HttpServletRequest;
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
    @Autowired
    private ProjectMemberService projectMemberService;
    @Autowired
    private JwtUtil jwtUtil;

    private Long getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return jwtUtil.getUserIdFromToken(authHeader.substring(7));
        }
        return null;
    }

    @GetMapping
    public ApiResponseDto<List<ProjectVo>> getAllProjects(HttpServletRequest request) {
        try {
            Long userId = getCurrentUserId(request);
            List<ProjectEntity> projects = projectService.getAllProjects();
            if (userId != null) {
                var memberProjects = projectMemberService.findProjectIdsByUserId(userId);
                projects = projects.stream().filter(p -> memberProjects.contains(p.getId())).toList();
            }
            return ApiResponseDto.success("Projects retrieved successfully", projects.stream().map(ProjectVo::fromEntity).toList());
        } catch (Exception e) {
            log.error("Failed to retrieve projects", e);
            return ApiResponseDto.error("Failed to retrieve projects");
        }
    }

    @GetMapping("/{id}")
    public ApiResponseDto<ProjectVo> getProjectById(@PathVariable Long id) {
        try {
            ProjectEntity project = projectService.getProjectById(id);
            if (project != null) {
                return ApiResponseDto.success("Project retrieved successfully", ProjectVo.fromEntity(project));
            } else {
                return ApiResponseDto.error("Project not found");
            }
        } catch (Exception e) {
            log.error("Failed to retrieve project: " + id, e);
            return ApiResponseDto.error("Failed to retrieve project");
        }
    }

    @PostMapping
    public ApiResponseDto<ProjectVo> createProject(@RequestBody ProjectEntity project) {
        try {
            if (project.getName() == null || project.getName().isEmpty()) {
                return ApiResponseDto.error("Project name is required");
            }
            ProjectEntity created = projectService.createProject(project);
            return ApiResponseDto.success("Project created successfully", ProjectVo.fromEntity(created));
        } catch (RuntimeException e) {
            log.warn("Failed to create project: " + e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to create project", e);
            return ApiResponseDto.error("Failed to create project");
        }
    }

    @PutMapping("/{id}")
    public ApiResponseDto<ProjectVo> updateProject(@PathVariable Long id, @RequestBody ProjectEntity project) {
        try {
            ProjectEntity updated = projectService.updateProject(id, project);
            if (updated != null) {
                return ApiResponseDto.success("Project updated successfully", ProjectVo.fromEntity(updated));
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
