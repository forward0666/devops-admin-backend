package com.backend.user.controller.system;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.entity.system.ProjectMemberEntity;
import com.backend.user.service.system.ProjectMemberService;
import com.backend.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/projectMember")
public class ProjectMemberController {

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

    private void checkPermission(HttpServletRequest request, Long projectId, List<String> allowedRoles) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            throw new RuntimeException("Unauthorized");
        }
        ProjectMemberEntity member = projectMemberService.findByProjectIdAndUserId(projectId, userId);
        if (member == null || !allowedRoles.contains(member.getProjectRole())) {
            throw new RuntimeException("Permission denied");
        }
    }

    private boolean isLeaderAndTargetIsPrivileged(HttpServletRequest request, Long projectId, String targetRole) {
        if (!"Administrator".equals(targetRole) && !"DevOps".equals(targetRole)) return false;
        ProjectMemberEntity current = projectMemberService.findByProjectIdAndUserId(projectId, getCurrentUserId(request));
        return current != null && "Leader".equals(current.getProjectRole());
    }

    @GetMapping
    public ApiResponseDto<List<ProjectMemberEntity>> getMembers(@RequestParam Long projectId) {
        try {
            List<ProjectMemberEntity> members = projectMemberService.getMembersByProjectId(projectId);
            return ApiResponseDto.success("Members retrieved successfully", members);
        } catch (Exception e) {
            log.error("Failed to retrieve members", e);
            return ApiResponseDto.error("Failed to retrieve members");
        }
    }

    @PostMapping
    public ApiResponseDto<ProjectMemberEntity> addMember(HttpServletRequest request, @RequestBody ProjectMemberEntity member) {
        try {
            checkPermission(request, member.getProjectId(), List.of("Administrator", "DevOps", "Leader"));
            ProjectMemberEntity created = projectMemberService.addMember(member);
            return ApiResponseDto.success("Member added successfully", created);
        } catch (RuntimeException e) {
            log.warn("Failed to add member: " + e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to add member", e);
            return ApiResponseDto.error("Failed to add member");
        }
    }

    @PutMapping("/{id}")
    public ApiResponseDto<ProjectMemberEntity> updateMember(HttpServletRequest request, @PathVariable Long id, @RequestBody ProjectMemberEntity member) {
        try {
            ProjectMemberEntity existing = projectMemberService.findById(id);
            if (existing == null) return ApiResponseDto.error("Member not found");
            if (isLeaderAndTargetIsPrivileged(request, existing.getProjectId(), existing.getProjectRole())) {
                return ApiResponseDto.error("Leaders cannot edit admin/devops members");
            }
            if (isLeaderAndTargetIsPrivileged(request, existing.getProjectId(), existing.getProjectRole())) {
                return ApiResponseDto.error("Leaders cannot remove admin/devops members");
            }
            checkPermission(request, existing.getProjectId(), List.of("Administrator", "DevOps", "Leader"));
            ProjectMemberEntity updated = projectMemberService.updateMember(id, member);
            return ApiResponseDto.success("Member updated successfully", updated);
        } catch (RuntimeException e) {
            log.warn("Failed to update member: " + e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to update member: " + id, e);
            return ApiResponseDto.error("Failed to update member");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponseDto<Void> removeMember(HttpServletRequest request, @PathVariable Long id) {
        try {
            ProjectMemberEntity existing = projectMemberService.findById(id);
            if (existing == null) return ApiResponseDto.error("Member not found");
            if (isLeaderAndTargetIsPrivileged(request, existing.getProjectId(), existing.getProjectRole())) {
                return ApiResponseDto.error("Leaders cannot remove admin/devops members");
            }
            checkPermission(request, existing.getProjectId(), List.of("Administrator", "DevOps", "Leader"));
            Long currentUserId = getCurrentUserId(request);
            if (existing.getUserId().equals(currentUserId)) {
                return ApiResponseDto.error("Cannot remove yourself");
            }
            boolean deleted = projectMemberService.removeMemberById(id);
            return ApiResponseDto.success("Member removed successfully", null);
        } catch (RuntimeException e) {
            log.warn("Failed to remove member: " + e.getMessage());
            return ApiResponseDto.error(e.getMessage());
        } catch (Exception e) {
            log.error("Failed to remove member: " + id, e);
            return ApiResponseDto.error("Failed to remove member");
        }
    }
}
