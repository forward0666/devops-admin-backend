package com.backend.user.controller.system;

import com.backend.user.dto.ApiResponseDto;
import com.backend.user.entity.system.ProjectMemberEntity;
import com.backend.user.service.system.ProjectMemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/projectMember")
public class ProjectMemberController {

    @Autowired
    private ProjectMemberService projectMemberService;

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
    public ApiResponseDto<ProjectMemberEntity> addMember(@RequestBody ProjectMemberEntity member) {
        try {
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
    public ApiResponseDto<ProjectMemberEntity> updateMember(@PathVariable Long id, @RequestBody ProjectMemberEntity member) {
        try {
            ProjectMemberEntity updated = projectMemberService.updateMember(id, member);
            if (updated != null) {
                return ApiResponseDto.success("Member updated successfully", updated);
            } else {
                return ApiResponseDto.error("Member not found");
            }
        } catch (Exception e) {
            log.error("Failed to update member: " + id, e);
            return ApiResponseDto.error("Failed to update member");
        }
    }

    @DeleteMapping("/{id}")
    public ApiResponseDto<Void> removeMember(@PathVariable Long id) {
        try {
            boolean deleted = projectMemberService.removeMemberById(id);
            if (deleted) {
                return ApiResponseDto.success("Member removed successfully", null);
            } else {
                return ApiResponseDto.error("Member not found");
            }
        } catch (Exception e) {
            log.error("Failed to remove member: " + id, e);
            return ApiResponseDto.error("Failed to remove member");
        }
    }
}
