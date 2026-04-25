package com.backend.user.service.system;

import com.backend.user.entity.system.ProjectEntity;
import com.backend.user.entity.system.ProjectMemberEntity;
import com.backend.user.mapper.system.ProjectMapper;
import com.backend.user.mapper.system.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 项目服务类
 */
@Slf4j
@Service
@Transactional
public class ProjectService {

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ProjectMemberService projectMemberService;

    @Autowired
    private UserMapper userMapper;

    @Transactional(readOnly = true)
    public List<ProjectEntity> getAllProjects() {
        return projectMapper.findAll();
    }

    @Transactional(readOnly = true)
    public ProjectEntity getProjectById(Long id) {
        return Optional.ofNullable(projectMapper.findById(id)).orElse(null);
    }

    public ProjectEntity createProject(ProjectEntity project) {
        if (projectMapper.existsByName(project.getName()) > 0) {
            throw new RuntimeException("项目名称已存在: " + project.getName());
        }

        var now = LocalDateTime.now();
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        project.setActive(true);

        if (project.getStatus() == null) project.setStatus("active");
        if (project.getProgress() == null) project.setProgress(0);

        projectMapper.insert(project);
        log.info("项目创建成功: " + project.getName());

        // 自动将 leader 添加为项目成员
        autoAddLeaderAsMember(project);

        return project;
    }

    public ProjectEntity updateProject(Long id, ProjectEntity data) {
        ProjectEntity project = Optional.ofNullable(projectMapper.findById(id)).orElse(null);
        if (project == null) {
            log.warn("项目不存在: " + id);
            return null;
        }

        if (data.getName() != null && !data.getName().equals(project.getName())
                && projectMapper.existsByName(data.getName()) > 0) {
            throw new RuntimeException("项目名称已存在: " + data.getName());
        }

        if (data.getName() != null) project.setName(data.getName());
        if (data.getType() != null) project.setType(data.getType());
        if (data.getStatus() != null) project.setStatus(data.getStatus());
        if (data.getProgress() != null) project.setProgress(data.getProgress());
        if (data.getLeader() != null) project.setLeader(data.getLeader());
        if (data.getDepartmentId() != null) project.setDepartmentId(data.getDepartmentId());
        if (data.getDescription() != null) project.setDescription(data.getDescription());
        if (data.getTechStack() != null) project.setTechStack(data.getTechStack());
        if (data.getObjectives() != null) project.setObjectives(data.getObjectives());
        project.setUpdatedBy(data.getUpdatedBy());
        project.setUpdatedAt(LocalDateTime.now());

        projectMapper.update(project);
        log.info("项目更新成功: " + id);

        // leader 变更时自动更新成员
        if (data.getLeader() != null && !data.getLeader().equals(project.getLeader())) {
            autoAddLeaderAsMember(project);
        }

        return project;
    }

    public boolean deleteProject(Long id) {
        if (projectMapper.existsById(id) == 0) {
            log.warn("项目不存在: " + id);
            return false;
        }
        projectMapper.deleteById(id);
        log.info("项目删除成功: " + id);
        return true;
    }

    private void autoAddLeaderAsMember(ProjectEntity project) {
        if (project.getLeader() == null || project.getLeader().isEmpty()) return;
        try {
            var user = userMapper.findByUsername(project.getLeader());
            if (user == null) {
                log.warn("Leader 用户不存在: {}", project.getLeader());
                return;
            }
            if (projectMemberService.getMembersByProjectId(project.getId()).stream()
                    .noneMatch(m -> m.getUserId().equals(user.getId()))) {
                var member = new ProjectMemberEntity();
                member.setProjectId(project.getId());
                member.setUserId(user.getId());
                member.setUsername(user.getUsername());
                member.setFullName(user.getFullName());
                member.setRole(user.getRole());
                member.setPosition(user.getPosition());
                member.setStatus("active");
                projectMemberService.addMember(member);
                log.info("Leader 自动添加为项目成员: {} -> projectId={}", user.getUsername(), project.getId());
            }
        } catch (Exception e) {
            log.warn("自动添加 leader 为成员失败: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ProjectEntity> searchProjects(String query) {
        return projectMapper.search(query);
    }
}
