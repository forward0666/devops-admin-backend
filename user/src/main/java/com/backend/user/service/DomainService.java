package com.backend.user.service;

import com.backend.user.entity.DomainEntity;
import com.backend.user.mapper.DomainMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DomainService {

    @Autowired
    private DomainMapper domainMapper;

    public List<DomainEntity> findByProjectId(Long projectId) {
        return domainMapper.findByProjectId(projectId);
    }

    public DomainEntity create(Long projectId, DomainEntity entity) {
        entity.setId(null);
        entity.setProjectId(projectId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return domainMapper.insert(entity);
    }

    public DomainEntity update(String id, Long projectId, DomainEntity entity) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (entity.getDomain() != null) fields.put("domain", entity.getDomain());
        if (entity.getType() != null) fields.put("type", entity.getType());
        if (entity.getEnv() != null) fields.put("env", entity.getEnv());
        if (entity.getRemark() != null) fields.put("remark", entity.getRemark());
        if (fields.isEmpty()) return domainMapper.findByIdAndProjectId(id, projectId);
        return domainMapper.update(id, projectId, fields);
    }

    public boolean delete(String id, Long projectId) {
        DomainEntity existing = domainMapper.findByIdAndProjectId(id, projectId);
        if (existing == null) return false;
        domainMapper.deleteById(id);
        return true;
    }

    public void importDomains(Long projectId, List<DomainEntity> domains) {
        LocalDateTime now = LocalDateTime.now();
        for (DomainEntity d : domains) {
            d.setId(null);
            d.setProjectId(projectId);
            d.setCreatedAt(now);
            d.setUpdatedAt(now);
        }
        domainMapper.insertAll(domains);
    }
}
