package com.backend.user.repository;

import com.backend.user.entity.MiddlewareEntity;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MiddlewareRepository extends MongoRepository<MiddlewareEntity, String> {

    List<MiddlewareEntity> findByProjectId(Long projectId);
}
