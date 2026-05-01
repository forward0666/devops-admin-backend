package com.backend.user.service;

import com.backend.user.entity.MiddlewareEntity;
import com.backend.user.mapper.MiddlewareMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class MiddlewareService {

    @Autowired
    private MiddlewareMapper middlewareMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public List<MiddlewareEntity> findByProjectId(Long projectId) {
        return middlewareMapper.findByProjectId(projectId);
    }

    public MiddlewareEntity create(Long projectId, MiddlewareEntity entity) {
        entity.setId(null);
        entity.setProjectId(projectId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        middlewareMapper.insert(entity);
        evictMiddlewareCache(projectId);
        return entity;
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
        middlewareMapper.update(id, projectId, fields);
        evictMiddlewareCache(projectId);
        return middlewareMapper.findByIdAndProjectId(id, projectId);
    }

    public boolean delete(String id, Long projectId) {
        MiddlewareEntity existing = middlewareMapper.findByIdAndProjectId(id, projectId);
        if (existing == null) return false;
        middlewareMapper.deleteById(id);
        evictMiddlewareCache(projectId);
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
        evictMiddlewareCache(projectId);
    }

    private void evictMiddlewareCache(Long projectId) {
        try {
            redisTemplate.delete("bot:middlewares:" + projectId);
        } catch (Exception e) {
            log.warn("清除中间件缓存失败: projectId={}", projectId, e);
        }
    }
}
