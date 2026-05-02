package com.backend.user.service;

import com.backend.user.entity.DomainEntity;
import com.backend.user.mapper.DomainMapper;
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
public class DomainService {

    @Autowired
    private DomainMapper domainMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public List<DomainEntity> findByProjectId(Long projectId) {
        return domainMapper.findByProjectId(projectId);
    }

    public DomainEntity create(Long projectId, DomainEntity entity) {
        DomainEntity existing = domainMapper.findByProjectIdAndDomain(projectId, entity.getDomain());
        if (existing != null) {
            throw new RuntimeException("域名已存在: " + entity.getDomain());
        }
        entity.setId(null);
        entity.setProjectId(projectId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        domainMapper.insert(entity);
        evictDomainCache(projectId);
        return entity;
    }

    public DomainEntity update(String id, Long projectId, DomainEntity entity) {
        if (entity.getDomain() != null) {
            DomainEntity existing = domainMapper.findByProjectIdAndDomain(projectId, entity.getDomain());
            if (existing != null && !existing.getId().equals(id)) {
                throw new RuntimeException("域名已存在: " + entity.getDomain());
            }
        }
        Map<String, Object> fields = new LinkedHashMap<>();
        if (entity.getDomain() != null) fields.put("domain", entity.getDomain());
        if (entity.getType() != null) fields.put("type", entity.getType());
        if (entity.getEnv() != null) fields.put("env", entity.getEnv());
        if (entity.getRemark() != null) fields.put("remark", entity.getRemark());
        if (fields.isEmpty()) return domainMapper.findByIdAndProjectId(id, projectId);
        domainMapper.update(id, projectId, fields);
        evictDomainCache(projectId);
        return domainMapper.findByIdAndProjectId(id, projectId);
    }

    public boolean delete(String id, Long projectId) {
        DomainEntity existing = domainMapper.findByIdAndProjectId(id, projectId);
        if (existing == null) return false;
        domainMapper.deleteById(id);
        evictDomainCache(projectId);
        return true;
    }

    public void importDomains(Long projectId, List<DomainEntity> domains) {
        List<String> domainNames = domains.stream().map(DomainEntity::getDomain).toList();
        List<DomainEntity> existing = domainMapper.findByProjectIdAndDomains(projectId, domainNames);
        if (!existing.isEmpty()) {
            String duplicates = existing.stream().map(DomainEntity::getDomain).collect(java.util.stream.Collectors.joining(", "));
            throw new RuntimeException("域名已存在: " + duplicates);
        }
        LocalDateTime now = LocalDateTime.now();
        for (DomainEntity d : domains) {
            d.setId(null);
            d.setProjectId(projectId);
            d.setCreatedAt(now);
            d.setUpdatedAt(now);
        }
        domainMapper.insertAll(domains);
        evictDomainCache(projectId);
    }

    private void evictDomainCache(Long projectId) {
        try {
            redisTemplate.delete(redisTemplate.keys("bot:domains:" + projectId + ":*"));
        } catch (Exception e) {
            log.warn("清除域名缓存失败: projectId={}", projectId, e);
        }
    }
}
