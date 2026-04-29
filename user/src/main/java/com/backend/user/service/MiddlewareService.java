package com.backend.user.service;

import com.backend.user.entity.MiddlewareEntity;
import com.backend.user.repository.MiddlewareRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MiddlewareService {

    @Autowired
    private MiddlewareRepository middlewareRepository;

    public List<MiddlewareEntity> findByProjectId(Long projectId) {
        return middlewareRepository.findByProjectId(projectId);
    }

    public MiddlewareEntity create(Long projectId, MiddlewareEntity entity) {
        entity.setId(null);
        entity.setProjectId(projectId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return middlewareRepository.save(entity);
    }

    public MiddlewareEntity update(String id, Long projectId, MiddlewareEntity entity) {
        MiddlewareEntity existing = middlewareRepository.findById(id).orElse(null);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            return null;
        }
        if (entity.getName() != null) existing.setName(entity.getName());
        if (entity.getProtocol() != null) existing.setProtocol(entity.getProtocol());
        if (entity.getExternalAddr() != null) existing.setExternalAddr(entity.getExternalAddr());
        if (entity.getInternalAddr() != null) existing.setInternalAddr(entity.getInternalAddr());
        if (entity.getSvcAddr() != null) existing.setSvcAddr(entity.getSvcAddr());
        if (entity.getRemark() != null) existing.setRemark(entity.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        return middlewareRepository.save(existing);
    }

    public boolean delete(String id, Long projectId) {
        MiddlewareEntity existing = middlewareRepository.findById(id).orElse(null);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            return false;
        }
        middlewareRepository.deleteById(id);
        return true;
    }

    public void importMiddlewares(Long projectId, List<MiddlewareEntity> items) {
        LocalDateTime now = LocalDateTime.now();
        for (MiddlewareEntity m : items) {
            m.setId(null);
            m.setProjectId(projectId);
            m.setCreatedAt(now);
            m.setUpdatedAt(now);
        }
        middlewareRepository.saveAll(items);
    }
}
