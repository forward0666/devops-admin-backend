package com.backend.user.service.system;

import com.backend.user.entity.system.ProjectMemberEntity;
import com.backend.user.mapper.system.ProjectMemberMapper;
import com.backend.user.mapper.system.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
public class ProjectMemberService {

    @Autowired
    private ProjectMemberMapper projectMemberMapper;

    @Autowired
    private UserMapper userMapper;

    @Transactional(readOnly = true)
    public List<ProjectMemberEntity> getMembersByProjectId(Long projectId) {
        return projectMemberMapper.findByProjectId(projectId);
    }

    public ProjectMemberEntity addMember(ProjectMemberEntity data) {
        if (projectMemberMapper.existsByProjectIdAndUserId(data.getProjectId(), data.getUserId()) > 0) {
            throw new RuntimeException("该成员已在此项目中");
        }

        // Fetch user info to fill in details
        if (data.getUserId() != null) {
            var user = userMapper.findById(data.getUserId());
            if (user != null) {
                if (data.getUsername() == null || data.getUsername().isEmpty()) data.setUsername(user.getUsername());
                if (data.getFullName() == null || data.getFullName().isEmpty()) data.setFullName(user.getFullName());
                if (data.getPosition() == null || data.getPosition().isEmpty()) data.setPosition(user.getPosition());
            }
        }

        var now = LocalDateTime.now();
        data.setCreatedAt(now);
        data.setUpdatedAt(now);
        data.setJoinedAt(now);
        data.setActive(true);
        if (data.getStatus() == null) data.setStatus("active");
        if (data.getRole() == null) data.setRole("Member");

        projectMemberMapper.insert(data);
        log.info("项目成员添加成功: projectId={}, userId={}", data.getProjectId(), data.getUserId());
        return data;
    }

    public ProjectMemberEntity updateMember(Long id, ProjectMemberEntity data) {
        ProjectMemberEntity member = projectMemberMapper.findById(id);
        if (member == null) {
            return null;
        }

        if (data.getRole() != null) member.setRole(data.getRole());
        if (data.getPosition() != null) member.setPosition(data.getPosition());
        if (data.getStatus() != null) member.setStatus(data.getStatus());
        if (data.getFullName() != null) member.setFullName(data.getFullName());
        if (data.getUsername() != null) member.setUsername(data.getUsername());
        member.setUpdatedBy(data.getUpdatedBy());

        projectMemberMapper.update(member);
        log.info("项目成员更新成功: id={}", id);
        return member;
    }

    public boolean removeMember(Long projectId, Long userId) {
        int rows = projectMemberMapper.deleteByProjectIdAndUserId(projectId, userId);
        if (rows > 0) {
            log.info("项目成员移除成功: projectId={}, userId={}", projectId, userId);
            return true;
        }
        return false;
    }

    public boolean removeMemberById(Long id) {
        ProjectMemberEntity member = projectMemberMapper.findById(id);
        if (member == null) {
            return false;
        }
        projectMemberMapper.deleteById(id);
        log.info("项目成员移除成功: id={}", id);
        return true;
    }
}
