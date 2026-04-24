package com.backend.manage.controller.system;

import com.backend.manage.annotation.OperationLog;
import com.backend.manage.dto.ApiResponseDto;
import com.backend.manage.entity.system.ProjectMemberEntity;
import com.backend.manage.service.system.ProjectMemberService;
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
    @OperationLog(
        operationType = "CREATE",
        operationName = "添加项目成员",
        resourceType = "PROJECT_MEMBER",
        description = "添加项目成员"
    )
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
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新项目成员",
        resourceType = "PROJECT_MEMBER",
        resourceIdIndex = 0,
        description = "更新项目成员信息"
    )
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
    @OperationLog(
        operationType = "DELETE",
        operationName = "移除项目成员",
        resourceType = "PROJECT_MEMBER",
        resourceIdIndex = 0,
        description = "移除项目成员"
    )
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
