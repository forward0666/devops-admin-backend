package com.backend.cloudflare.service;

import com.backend.cloudflare.entity.DomainGroupEntity;
import com.backend.cloudflare.mapper.DomainGroupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DomainGroupService {

    private final DomainGroupMapper domainGroupMapper;

    /**
     * List all domain groups.
     */
    public List<DomainGroupEntity> listAll() {
        return domainGroupMapper.selectList(null);
    }

    /**
     * List domain groups for a specific account.
     */
    public List<DomainGroupEntity> listByAccount(Long accountId) {
        return domainGroupMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DomainGroupEntity>()
                        .eq(DomainGroupEntity::getAccountId, accountId)
        );
    }

    /**
     * Get a domain group by ID.
     */
    public DomainGroupEntity getById(Long id) {
        return domainGroupMapper.selectById(id);
    }

    /**
     * Create a new domain group.
     */
    public DomainGroupEntity create(DomainGroupEntity entity) {
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        domainGroupMapper.insert(entity);
        log.info("Created domain group: id={}, name={}", entity.getId(), entity.getName());
        return entity;
    }

    /**
     * Update an existing domain group.
     */
    public DomainGroupEntity update(DomainGroupEntity entity) {
        DomainGroupEntity existing = domainGroupMapper.selectById(entity.getId());
        if (existing == null) {
            throw new RuntimeException("Domain group not found: " + entity.getId());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        domainGroupMapper.updateById(entity);
        log.info("Updated domain group: id={}", entity.getId());
        return domainGroupMapper.selectById(entity.getId());
    }

    /**
     * Delete a domain group by ID.
     */
    public void delete(Long id) {
        DomainGroupEntity existing = domainGroupMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Domain group not found: " + id);
        }
        domainGroupMapper.deleteById(id);
        log.info("Deleted domain group: id={}", id);
    }
}