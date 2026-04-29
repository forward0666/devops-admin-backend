package com.backend.user.repository;

import com.backend.user.entity.DomainEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DomainRepository extends MongoRepository<DomainEntity, String> {

    List<DomainEntity> findByProjectId(Long projectId);

    void deleteByIdAndProjectId(String id, Long projectId);
}
