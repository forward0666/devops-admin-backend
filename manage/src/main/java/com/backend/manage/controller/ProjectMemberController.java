package com.backend.manage.controller;

import com.backend.manage.annotation.OperationLog;
import com.backend.utils.dto.ApiResponseDto;
import com.backend.manage.entity.ProjectMemberEntity;
import com.backend.manage.service.ProjectMemberService;
import com.backend.manage.vo.ProjectMemberVo;
import com.backend.utils.exception.BizException;
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

    @GetMapping
    public ResponseEntity<ApiResponseDto<List<ProjectMemberVo>>> getMembers(@RequestParam Long projectId) {
        List<ProjectMemberEntity> members = projectMemberService.getMembersByProjectId(projectId);
        List<ProjectMemberVo> result = members.stream().map(ProjectMemberVo::fromEntity).toList();
        return ResponseEntity.ok(ApiResponseDto.success("Members retrieved successfully", result));
    }

    @PostMapping
    @OperationLog(
        operationType = "CREATE",
        operationName = "添加项目成员",
        resourceType = "PROJECT_MEMBER",
        description = "添加项目成员"
    )
    public ResponseEntity<ApiResponseDto<ProjectMemberVo>> addMember(@RequestBody ProjectMemberEntity member) {
        ProjectMemberEntity created = projectMemberService.addMember(member);
        return ResponseEntity.ok(ApiResponseDto.success("Member added successfully", ProjectMemberVo.fromEntity(created)));
    }

    @PutMapping("/{id}")
    @OperationLog(
        operationType = "UPDATE",
        operationName = "更新项目成员",
        resourceType = "PROJECT_MEMBER",
        resourceIdIndex = 0,
        description = "更新项目成员信息"
    )
    public ResponseEntity<ApiResponseDto<ProjectMemberVo>> updateMember(@PathVariable Long id, @RequestBody ProjectMemberEntity member) {
        ProjectMemberEntity updated = projectMemberService.updateMember(id, member);
        if (updated == null) throw new BizException(404, "Member not found");
        return ResponseEntity.ok(ApiResponseDto.success("Member updated successfully", ProjectMemberVo.fromEntity(updated)));
    }

    @DeleteMapping("/{id}")
    @OperationLog(
        operationType = "DELETE",
        operationName = "移除项目成员",
        resourceType = "PROJECT_MEMBER",
        resourceIdIndex = 0,
        description = "移除项目成员"
    )
    public ResponseEntity<ApiResponseDto<Void>> removeMember(@PathVariable Long id) {
        boolean deleted = projectMemberService.removeMemberById(id);
        if (!deleted) throw new BizException(404, "Member not found");
        return ResponseEntity.ok(ApiResponseDto.success("Member removed successfully", null));
    }
}