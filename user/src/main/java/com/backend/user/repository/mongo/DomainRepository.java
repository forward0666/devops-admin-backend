package com.backend.user.repository.mongo;

import com.backend.user.entity.mongo.DomainEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DomainRepository extends MongoRepository<DomainEntity, String> {

    List<DomainEntity> findByProjectId(Long projectId);

    void deleteByIdAndProjectId(String id, Long projectId);
}
