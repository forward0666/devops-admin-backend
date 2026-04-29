package com.backend.user.service;

import com.backend.user.entity.ProjectEntity;
import com.backend.user.mapper.ProjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ProjectService {

    @Autowired
    private ProjectMapper projectMapper;

    public List<ProjectEntity> getAllProjects() {
        return projectMapper.findAll();
    }

    public ProjectEntity getProjectById(Long id) {
        return Optional.ofNullable(projectMapper.findById(id)).orElse(null);
    }
}
