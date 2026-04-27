package com.backend.user.service.mongo;

import com.backend.user.entity.mongo.DomainEntity;
import com.backend.user.repository.mongo.DomainRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DomainService {

    @Autowired
    private DomainRepository domainRepository;

    public List<DomainEntity> findByProjectId(Long projectId) {
        return domainRepository.findByProjectId(projectId);
    }

    public DomainEntity create(Long projectId, DomainEntity entity) {
        entity.setId(null);
        entity.setProjectId(projectId);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return domainRepository.save(entity);
    }

    public DomainEntity update(String id, Long projectId, DomainEntity entity) {
        DomainEntity existing = domainRepository.findById(id).orElse(null);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            return null;
        }
        if (entity.getDomain() != null) existing.setDomain(entity.getDomain());
        if (entity.getType() != null) existing.setType(entity.getType());
        if (entity.getRemark() != null) existing.setRemark(entity.getRemark());
        existing.setUpdatedAt(LocalDateTime.now());
        return domainRepository.save(existing);
    }

    public boolean delete(String id, Long projectId) {
        DomainEntity existing = domainRepository.findById(id).orElse(null);
        if (existing == null || !existing.getProjectId().equals(projectId)) {
            return false;
        }
        domainRepository.deleteById(id);
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
        domainRepository.saveAll(domains);
    }
}
