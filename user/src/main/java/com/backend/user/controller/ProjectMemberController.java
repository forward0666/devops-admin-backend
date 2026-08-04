package com.backend.user.controller;

import com.backend.utils.dto.ApiResponseDto;
import com.backend.user.entity.ProjectMemberEntity;
import com.backend.user.service.ProjectMemberService;
import com.backend.utils.JwtUtil;
import com.backend.utils.exception.BizException;
import com.backend.user.vo.ProjectMemberVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/projectMember")
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;
    private final JwtUtil jwtUtil;

    private Long getCurrentUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return jwtUtil.getUserIdFromToken(authHeader.substring(7));
        }
        return null;
    }

    private void checkPermission(HttpServletRequest request, Long projectId, List<String> allowedRoles) {
        Long userId = getCurrentUserId(request);
        if (userId == null) throw new BizException(401, "Unauthorized");
        ProjectMemberEntity member = projectMemberService.findByProjectIdAndUserId(projectId, userId);
        if (member == null || !allowedRoles.contains(member.getProjectRole())) {
            throw new BizException(403, "Permission denied");
        }
    }

    private boolean isLeaderAndTargetIsPrivileged(HttpServletRequest request, Long projectId, String targetRole) {
        if (!"Administrator".equals(targetRole) && !"DevOps".equals(targetRole)) return false;
        ProjectMemberEntity current = projectMemberService.findByProjectIdAndUserId(projectId, getCurrentUserId(request));
        return current != null && "Leader".equals(current.getProjectRole());
    }

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ProjectMemberVo>>> getMembers(@RequestParam Long projectId) {
        List<ProjectMemberEntity> members = projectMemberService.getMembersByProjectId(projectId);
        return ResponseEntity.ok(ApiResponseDto.success("Members retrieved successfully",
            members.stream().map(ProjectMemberVo::fromEntity).toList()));
    }

    @PostMapping
    public ResponseEntity<ApiResponseDto<ProjectMemberVo>> addMember(HttpServletRequest request, @RequestBody ProjectMemberEntity member) {
        checkPermission(request, member.getProjectId(), List.of("Administrator", "DevOps", "Leader"));
        ProjectMemberEntity created = projectMemberService.addMember(member);
        return ResponseEntity.ok(ApiResponseDto.success("Member added successfully", ProjectMemberVo.fromEntity(created)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseDto<ProjectMemberVo>> updateMember(HttpServletRequest request, @PathVariable Long id, @RequestBody ProjectMemberEntity member) {
        ProjectMemberEntity existing = projectMemberService.findById(id);
        if (existing == null) throw new BizException(404, "Member not found");
        if (isLeaderAndTargetIsPrivileged(request, existing.getProjectId(), existing.getProjectRole())) {
            throw new BizException(403, "Leaders cannot edit admin/devops members");
        }
        checkPermission(request, existing.getProjectId(), List.of("Administrator", "DevOps", "Leader"));
        ProjectMemberEntity updated = projectMemberService.updateMember(id, member);
        return ResponseEntity.ok(ApiResponseDto.success("Member updated successfully", ProjectMemberVo.fromEntity(updated)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseDto<Void>> removeMember(HttpServletRequest request, @PathVariable Long id) {
        ProjectMemberEntity existing = projectMemberService.findById(id);
        if (existing == null) throw new BizException(404, "Member not found");
        if (isLeaderAndTargetIsPrivileged(request, existing.getProjectId(), existing.getProjectRole())) {
            throw new BizException(403, "Leaders cannot remove admin/devops members");
        }
        checkPermission(request, existing.getProjectId(), List.of("Administrator", "DevOps", "Leader"));
        Long currentUserId = getCurrentUserId(request);
        if (existing.getUserId().equals(currentUserId)) {
            throw new BizException(400, "Cannot remove yourself");
        }
        projectMemberService.removeMemberById(id);
        return ResponseEntity.ok(ApiResponseDto.success("Member removed successfully", null));
    }
}