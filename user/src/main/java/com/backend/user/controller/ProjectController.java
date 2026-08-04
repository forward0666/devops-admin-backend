package com.backend.user.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.user.entity.ProjectEntity;
import com.backend.user.service.ProjectService;
import com.backend.user.service.ProjectMemberService;
import com.backend.utils.JwtUtil;
import com.backend.utils.exception.BizException;
import com.backend.user.vo.ProjectVo;
import jakarta.servlet.http.HttpServletRequest;
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
    private final ProjectMemberService projectMemberService;
    private final JwtUtil jwtUtil;

    private Long getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return jwtUtil.getUserIdFromToken(authHeader.substring(7));
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ProjectVo>>> getAllProjects(HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        List<ProjectEntity> projects = projectService.getAllProjects();
        if (userId != null) {
            var memberProjects = projectMemberService.findProjectIdsByUserId(userId);
            projects = projects.stream().filter(p -> memberProjects.contains(p.getId())).toList();
        }
        return ResponseEntity.ok(ApiResponseDto.success("Projects retrieved successfully",
            projects.stream().map(ProjectVo::fromEntity).toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseDto<ProjectVo>> getProjectById(@PathVariable Long id) {
        ProjectEntity project = projectService.getProjectById(id);
        if (project == null) throw new BizException(404, "Project not found");
        return ResponseEntity.ok(ApiResponseDto.success("Project retrieved successfully", ProjectVo.fromEntity(project)));
    }
}