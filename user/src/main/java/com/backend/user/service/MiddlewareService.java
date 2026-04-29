package com.backend.user.service;

import com.backend.user.entity.MiddlewareEntity;
import com.backend.user.mapper.MiddlewareMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MiddlewareService {

    @Autowired
    private MiddlewareMapper middlewareMapper;

    public List<MiddlewareEntity> findByProjectId(Long projectId) {
        return middlewareMapper.findByProjectId(projectId);
    }

    public MiddlewareEntity create(Long projectId, MiddlewareEntity entity) {
        entity.setId(null);
        entity.setProjectId(projectId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return middlewareMapper.insert(entity);
    }

    public MiddlewareEntity update(String id, Long projectId, MiddlewareEntity entity) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (entity.getName() != null) fields.put("name", entity.getName());
        if (entity.getProtocol() != null) fields.put("protocol", entity.getProtocol());
        if (entity.getExternalAddr() != null) fields.put("externalAddr", entity.getExternalAddr());
        if (entity.getInternalAddr() != null) fields.put("internalAddr", entity.getInternalAddr());
        if (entity.getSvcAddr() != null) fields.put("svcAddr", entity.getSvcAddr());
        if (entity.getEnv() != null) fields.put("env", entity.getEnv());
        if (entity.getRemark() != null) fields.put("remark", entity.getRemark());
        if (fields.isEmpty()) return middlewareMapper.findByIdAndProjectId(id, projectId);
        return middlewareMapper.update(id, projectId, fields);
    }

    public boolean delete(String id, Long projectId) {
        MiddlewareEntity existing = middlewareMapper.findByIdAndProjectId(id, projectId);
        if (existing == null) return false;
        middlewareMapper.deleteById(id);
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
        middlewareMapper.insertAll(items);
    }
}
