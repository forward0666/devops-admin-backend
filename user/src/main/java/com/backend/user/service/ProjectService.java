package com.backend.user.service;

import com.backend.user.entity.ProjectEntity;
import com.backend.user.mapper.ProjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
public class ProjectService {

    @Autowired
    private ProjectMapper projectMapper;

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
        if (data.getDepartmentId() != null) project.setDepartmentId(data.getDepartmentId());
        if (data.getDescription() != null) project.setDescription(data.getDescription());
        if (data.getTechStack() != null) project.setTechStack(data.getTechStack());
        if (data.getObjectives() != null) project.setObjectives(data.getObjectives());
        project.setUpdatedBy(data.getUpdatedBy());
        project.setUpdatedAt(LocalDateTime.now());

        projectMapper.update(project);
        log.info("项目更新成功: " + id);

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

    @Transactional(readOnly = true)
    public List<ProjectEntity> searchProjects(String query) {
        return projectMapper.search(query);
    }
}
